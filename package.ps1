# package.ps1 - build distributable servd bundles into dist/.
#
# Desktop: a self-contained app image (bundled JRE, no Java needed on the target),
# zipped for distribution; optionally the OS-native installer too. Android: the
# debug APK always, plus a signed release APK when androidApp/keystore.properties
# exists (see README > Packaging).
#
# Uses the same TMP/TEMP workaround as run.ps1 (JDK 21 NIO needs its socket in a
# dir that accepts socket files).
#
# Usage:
#   .\package.ps1                 # desktop app image (zipped) + Android APK(s)
#   .\package.ps1 -Installer      # also build the OS-native installer (.msi/.dmg/.deb)
#   .\package.ps1 -Desktop        # desktop only
#   .\package.ps1 -Android        # Android only
#   .\package.ps1 -Clean          # wipe dist/ first

param(
    [switch]$Desktop,
    [switch]$Android,
    [switch]$Installer,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

# Version from the single source of truth (gradle.properties: servdVersion).
$versionLine = Get-Content (Join-Path $root "gradle.properties") | Where-Object { $_ -match '^\s*servdVersion\s*=' } | Select-Object -First 1
$version = if ($versionLine) { ($versionLine -replace '^\s*servdVersion\s*=\s*', '').Trim() } else { "1.0.0" }

# TMP/TEMP workaround (see run.ps1).
$sockDir = Join-Path $root ".sock-tmp"
New-Item -ItemType Directory -Force -Path $sockDir | Out-Null
$env:TMP = $sockDir
$env:TEMP = $sockDir

# Cross-platform gradlew + OS label for artifact names.
$onWindows = -not (Test-Path variable:IsWindows) -or $IsWindows
$gradlew = if ($onWindows) { Join-Path $root "gradlew.bat" } else { Join-Path $root "gradlew" }
$osLabel = if ($onWindows) { "windows" } elseif ((Test-Path variable:IsMacOS) -and $IsMacOS) { "macos" } else { "linux" }

# Default to building both when neither is named.
if (-not $Desktop -and -not $Android) { $Desktop = $true; $Android = $true }

$dist = Join-Path $root "dist"
if ($Clean -and (Test-Path $dist)) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

# Color scheme (kept minimal and meaningful): Cyan = step, Green = produced artifact,
# Yellow = skipped/warning, Red = failure.
function Invoke-Gradle {
    param([string[]]$GradleArgs)
    & $gradlew @GradleArgs "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "gradle failed: $($GradleArgs -join ' ')" -ForegroundColor Red
        throw "gradle failed: $($GradleArgs -join ' ')"
    }
}

if ($Desktop) {
    Write-Host "==> Desktop: building app image (bundled JRE)..." -ForegroundColor Cyan
    Invoke-Gradle @(":desktopHost:jpackageImage")

    $imageDir = Join-Path $root "desktopHost\build\jpackage\servd"
    if (-not (Test-Path $imageDir)) { throw "app image not found at $imageDir" }
    $zip = Join-Path $dist "servd-$version-$osLabel.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path $imageDir -DestinationPath $zip
    Write-Host "    app image  -> $zip" -ForegroundColor Green

    if ($Installer) {
        Write-Host "==> Desktop: building native installer..." -ForegroundColor Cyan
        # On Windows this needs the WiX Toolset on PATH; jpackage is per-OS.
        Invoke-Gradle @(":desktopHost:jpackageInstaller")
        $jpDir = Join-Path $root "desktopHost\build\jpackage"
        Get-ChildItem $jpDir -File -Include *.msi, *.exe, *.dmg, *.pkg, *.deb, *.rpm -Recurse |
            ForEach-Object {
                Copy-Item $_.FullName -Destination $dist -Force
                Write-Host "    installer  -> $(Join-Path $dist $_.Name)" -ForegroundColor Green
            }
    }
}

if ($Android) {
    Write-Host "==> Android: building debug APK..." -ForegroundColor Cyan
    Invoke-Gradle @(":androidApp:assembleDebug")
    $debugApk = Join-Path $root "androidApp\build\outputs\apk\debug\androidApp-debug.apk"
    Copy-Item $debugApk -Destination (Join-Path $dist "servd-$version-debug.apk") -Force
    Write-Host "    debug APK  -> $(Join-Path $dist "servd-$version-debug.apk")" -ForegroundColor Green

    $keystoreProps = Join-Path $root "androidApp\keystore.properties"
    if (Test-Path $keystoreProps) {
        Write-Host "==> Android: building signed release APK..." -ForegroundColor Cyan
        Invoke-Gradle @(":androidApp:assembleRelease")
        $releaseApk = Join-Path $root "androidApp\build\outputs\apk\release\androidApp-release.apk"
        Copy-Item $releaseApk -Destination (Join-Path $dist "servd-$version-release.apk") -Force
        Write-Host "    release APK-> $(Join-Path $dist "servd-$version-release.apk")" -ForegroundColor Green
    } else {
        Write-Host "    (no androidApp/keystore.properties - skipping signed release; see README > Packaging)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Done. Bundles are in: $dist" -ForegroundColor Green
Get-ChildItem $dist -File | Select-Object Name, @{n = "SizeMB"; e = { "{0:N1}" -f ($_.Length / 1MB) } } | Format-Table -AutoSize

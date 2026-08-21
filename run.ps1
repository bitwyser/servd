# run.ps1 - build/run servd, with the TMP/TEMP workaround this machine needs.
#
# JDK 21's NIO uses an AF_UNIX socket for its internal pipe, auto-bound in TMP/TEMP.
# The default Windows temp rejects socket files here, so we point TMP/TEMP at a
# repo-local dir before invoking anything JVM.
#
# Running the server (no args, or args starting with --) launches the JVM directly
# so Ctrl+C stops it cleanly - no gradle wrapper in the middle, so no
# "Terminate batch job (Y/N)?" prompt.
#
# Usage:
#   .\run.ps1                      # run the server
#   .\run.ps1 --no-open --port 9443  # run the server with flags
#   .\run.ps1 discover             # find hubs on the network
#   .\run.ps1 :core:desktopTest    # any Gradle task(s)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$sockDir = Join-Path $root ".sock-tmp"
New-Item -ItemType Directory -Force -Path $sockDir | Out-Null
$env:TMP = $sockDir
$env:TEMP = $sockDir
$gradlew = Join-Path $root "gradlew.bat"

# Minimal, meaningful color: cyan for a status note, red for a failure.
function Write-Info($m) { Write-Host $m -ForegroundColor Cyan }
function Write-Err($m) { Write-Host $m -ForegroundColor Red }

# Server run = no args, or the first arg is a servd flag / the discover verb.
$serverRun = ($args.Count -eq 0) -or ($args[0] -like '--*') -or ($args[0] -eq 'discover')

if ($serverRun) {
    Write-Info "building servd..."   # the build below is quiet (-q), so say what's happening
    & $gradlew ":desktopHost:installDist" "-q" "--console=plain"
    if ($LASTEXITCODE -ne 0) { Write-Err "build failed (exit $LASTEXITCODE)"; exit $LASTEXITCODE }
    $jars = (Get-ChildItem (Join-Path $root "desktopHost\build\install\servd\lib\*.jar")).FullName -join ';'
    & java "-cp" $jars "dev.servd.host.MainKt" @args
    exit $LASTEXITCODE
}

& $gradlew @args
if ($LASTEXITCODE -ne 0) { Write-Err "gradle failed (exit $LASTEXITCODE)" }
exit $LASTEXITCODE

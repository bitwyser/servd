# run.ps1 — build/run servd via Gradle, with the TMP/TEMP workaround this machine needs.
#
# JDK 21's NIO uses an AF_UNIX socket for its internal pipe, auto-bound in TMP/TEMP.
# The default Windows temp (AppData\Local\Temp) rejects socket files here, so we point
# TMP/TEMP at a repo-local dir before invoking Gradle.
#
# Usage:
#   .\run.ps1                       # default: run the desktop host
#   .\run.ps1 :core:desktopTest     # any Gradle task(s)
#   .\run.ps1 build

$ErrorActionPreference = "Stop"
$sockDir = Join-Path $PSScriptRoot ".sock-tmp"
New-Item -ItemType Directory -Force -Path $sockDir | Out-Null
$env:TMP = $sockDir
$env:TEMP = $sockDir

$gradleArgs = if ($args.Count -gt 0) { $args } else { @(":desktopHost:run") }
& (Join-Path $PSScriptRoot "gradlew.bat") @gradleArgs
exit $LASTEXITCODE

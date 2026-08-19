@echo off
REM run.bat - build/run servd via Gradle, with the TMP/TEMP workaround this machine needs.
REM JDK 21 NIO uses an AF_UNIX socket in TMP/TEMP; the default Windows temp rejects socket
REM files here, so point TMP/TEMP at a repo-local dir before invoking Gradle.
REM
REM   run.bat                    -> run the desktop host
REM   run.bat :core:desktopTest  -> any Gradle task(s)
setlocal
set "SOCKDIR=%~dp0.sock-tmp"
if not exist "%SOCKDIR%" mkdir "%SOCKDIR%"
set "TMP=%SOCKDIR%"
set "TEMP=%SOCKDIR%"
if "%~1"=="" (
  call "%~dp0gradlew.bat" :desktopHost:run
) else (
  call "%~dp0gradlew.bat" %*
)
endlocal

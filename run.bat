@echo off
REM run.bat - build/run servd, with the TMP/TEMP workaround this machine needs.
REM JDK 21 NIO uses an AF_UNIX socket in TMP/TEMP; the default Windows temp rejects
REM socket files here, so point TMP/TEMP at a repo-local dir first.
REM
REM Running the server launches the JVM directly (not via Gradle) so Ctrl+C stops
REM it cleanly. NOTE: cmd.exe still shows "Terminate batch job (Y/N)?" after Ctrl+C
REM - the server has already stopped either way. For a prompt-free stop, use run.ps1.
REM
REM   run.bat                     -> run the server
REM   run.bat --no-open           -> run the server with flags
REM   run.bat discover            -> find hubs on the network
REM   run.bat :core:desktopTest   -> any Gradle task(s)
setlocal

REM Minimal, meaningful color (cyan status, red failure) via ANSI - modern Windows conhost/Terminal.
for /f "tokens=1,2 delims=#" %%a in ('"prompt #$H#$E# & echo on & for %%b in (1) do rem"') do set "ESC=%%b"
set "C_INFO=%ESC%[36m"
set "C_ERR=%ESC%[91m"
set "C_OFF=%ESC%[0m"

set "SOCKDIR=%~dp0.sock-tmp"
if not exist "%SOCKDIR%" mkdir "%SOCKDIR%"
set "TMP=%SOCKDIR%"
set "TEMP=%SOCKDIR%"

set "FIRST=%~1"
if "%FIRST%"=="" goto serverrun
if "%FIRST%"=="discover" goto serverrun
echo(%FIRST%| findstr /b /c:"--" >nul && goto serverrun

REM Gradle task mode
call "%~dp0gradlew.bat" %*
if errorlevel 1 echo %C_ERR%gradle failed%C_OFF%
goto :eof

:serverrun
echo %C_INFO%building servd...%C_OFF%
call "%~dp0gradlew.bat" :desktopHost:installDist -q --console=plain
if errorlevel 1 ( echo %C_ERR%build failed%C_OFF% & goto :eof )
java -cp "%~dp0desktopHost\build\install\servd\lib\*" dev.servd.host.MainKt %*
goto :eof

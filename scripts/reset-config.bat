@echo off
rem Resets the dashboard's settings and tile layout (~\.emberfx\config.json).
rem Keeps the logs, module data (like shooter tuning points) and team modules.
setlocal
set "CFG=%USERPROFILE%\.emberfx\config.json"
if not exist "%CFG%" (
    echo Nothing to reset: %CFG% does not exist.
    pause
    exit /b 0
)
tasklist /fi "imagename eq EmberFX.exe" | find /i "EmberFX.exe" >nul
if not errorlevel 1 (
    echo Close the dashboard first, or it can save its old settings again.
    pause
    exit /b 1
)
copy /y "%CFG%" "%CFG%.bak" >nul
del "%CFG%"
echo Reset. The old settings are in %CFG%.bak
pause

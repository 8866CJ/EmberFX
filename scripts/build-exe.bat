@echo off
rem Builds the dashboard for sharing:
rem   build\installer\FRCDashboard-Setup-<version>.exe  one-file installer (needs Inno Setup 6)
rem   build\dist\FRCDashboard.zip                       portable folder, no install needed
rem Both carry their own Java runtime. Only the person building needs WPILib installed.
setlocal
cd /d "%~dp0.."
rem Use the newest WPILib JDK installed (it includes jpackage).
for /d %%D in ("C:\Users\Public\wpilib\*") do if exist "%%D\jdk\bin\jpackage.exe" set "JAVA_HOME=%%D\jdk"

rem --offline works without internet at events. Falls back to online the first time.
call "%CD%\gradlew.bat" packageExe --offline -q || call "%CD%\gradlew.bat" packageExe -q
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
call "%CD%\gradlew.bat" packageInstaller --offline -q
if errorlevel 1 (
    echo.
    echo The portable zip is built, but not the installer. See the message above.
    echo Portable zip: %CD%\build\dist\FRCDashboard.zip
    pause
    exit /b 1
)
echo.
echo Installer:    in %CD%\build\installer
echo Portable zip: %CD%\build\dist\FRCDashboard.zip
pause

@echo off
rem Makes FRCDashboard.exe the Driver Station's "Default" dashboard.
rem Close the Driver Station first: it rewrites its settings file when it exits.
rem Usage: register-ds.bat [path\to\FRCDashboard.exe]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0register-ds.ps1" %*
pause

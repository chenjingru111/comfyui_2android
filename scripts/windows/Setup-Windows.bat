@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Setup-Windows.ps1" %*
pause

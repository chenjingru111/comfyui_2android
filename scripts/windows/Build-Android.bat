@echo off
setlocal
cd /d "%~dp0\..\.."
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo Build failed. Open the project in Android Studio once to install the required SDK.
  pause
  exit /b 1
)
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
pause

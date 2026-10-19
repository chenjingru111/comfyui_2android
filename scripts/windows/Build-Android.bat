@echo off
setlocal
cd /d "%~dp0\..\.."
if not exist "local.properties" call "%~dp0Setup-Windows.bat"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo Build failed. Open the project in Android Studio once to install the required SDK.
  pause
  exit /b 1
)
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
pause

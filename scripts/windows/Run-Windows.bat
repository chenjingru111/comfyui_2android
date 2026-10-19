@echo off
setlocal
cd /d "%~dp0\..\.."
if not exist "local.properties" call "%~dp0Setup-Windows.bat"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
call gradlew.bat installDebug
if errorlevel 1 (
  echo Run failed. Start an Android emulator or connect a USB-debugging phone first.
  pause
  exit /b 1
)
set "ADB=adb"
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
"%ADB%" shell am start -n com.local.comfymobile/.view.LoginActivity
if errorlevel 1 echo The app is installed. Open Comfy Mobile manually on the device.
pause

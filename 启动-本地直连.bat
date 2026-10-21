@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "GUI=server\comfy_local_gui.py"

where python >nul 2>nul
if errorlevel 1 goto no_python

where pythonw >nul 2>nul
if errorlevel 1 goto run_console

start "" pythonw "%GUI%"
goto done

:run_console
python "%GUI%"
goto done

:no_python
echo.
echo 未找到 Python。请安装 Python 3.9+，安装时勾选 "Add python.exe to PATH"。
echo 若已安装但不在 PATH，可直接运行: D:\PY\python.exe server\comfy_local_gui.py
echo.
pause

:done

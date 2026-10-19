$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Python = Join-Path $ProjectRoot ".venv\Scripts\python.exe"
$Config = Join-Path $ProjectRoot "server\config.json"

if (-not (Test-Path $Python) -or -not (Test-Path $Config)) {
    throw "Run scripts\windows\Setup-Windows.bat first."
}
Set-Location $ProjectRoot
& $Python (Join-Path $ProjectRoot "server\comfy_mobile_server.py")

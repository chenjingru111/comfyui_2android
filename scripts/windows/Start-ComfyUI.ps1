$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ConfigPath = Join-Path $ProjectRoot "server\config.json"
if (-not (Test-Path $ConfigPath)) { throw "Run scripts\windows\Setup-Windows.bat first." }

$Config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$ComfyUIPath = $Config.comfyUIPath
if (-not (Test-Path (Join-Path $ComfyUIPath "main.py"))) { throw "ComfyUI path is invalid: $ComfyUIPath" }

$pythonCandidates = @(
    (Join-Path $ComfyUIPath "venv\Scripts\python.exe"),
    (Join-Path $ComfyUIPath ".venv\Scripts\python.exe"),
    (Join-Path $ComfyUIPath "python_embeded\python.exe")
)
$Python = $pythonCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Python) { throw "No ComfyUI Python environment was found under $ComfyUIPath." }

Set-Location $ComfyUIPath
& $Python -u main.py --preview-method auto --listen 0.0.0.0 --port 8188

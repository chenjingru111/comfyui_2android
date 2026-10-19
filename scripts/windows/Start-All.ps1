$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not (Test-Path (Join-Path $ProjectRoot "server\config.json"))) {
    & (Join-Path $PSScriptRoot "Setup-Windows.ps1")
}
if (Test-NetConnection -ComputerName 127.0.0.1 -Port 8188 -InformationLevel Quiet) {
    Write-Host "ComfyUI is already running on port 8188." -ForegroundColor Green
} else {
    $ComfyScript = Join-Path $PSScriptRoot "Start-ComfyUI.ps1"
    Start-Process powershell -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$ComfyScript`"")
    Start-Sleep -Seconds 3
}
& (Join-Path $PSScriptRoot "Start-Gateway.ps1")

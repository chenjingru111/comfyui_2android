param(
    [string]$ComfyUIPath = "",
    [int]$GatewayPort = 8765,
    [string]$Username = "admin"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$VenvPython = Join-Path $ProjectRoot ".venv\Scripts\python.exe"

function Find-ComfyUI {
    $candidates = @()
    if ($env:STABILITY_MATRIX_HOME) { $candidates += Join-Path $env:STABILITY_MATRIX_HOME "Data\Packages\ComfyUI" }
    if ($env:APPDATA) { $candidates += Join-Path $env:APPDATA "StabilityMatrix\Data\Packages\ComfyUI" }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA "StabilityMatrix\Data\Packages\ComfyUI" }
    foreach ($drive in @("C:", "D:", "E:", "F:", "G:")) {
        $candidates += Join-Path $drive "StabilityMatrix\Data\Packages\ComfyUI"
        $candidates += Join-Path $drive "gpu_move\launch\StabilityMatrix\Data\Packages\ComfyUI"
    }
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "main.py")) { return (Resolve-Path $candidate).Path }
    }
    return ""
}

if (-not $ComfyUIPath) { $ComfyUIPath = Find-ComfyUI }
if (-not $ComfyUIPath) { $ComfyUIPath = Read-Host "Enter the full ComfyUI folder (the folder containing main.py)" }
if (-not (Test-Path (Join-Path $ComfyUIPath "main.py"))) { throw "Invalid ComfyUI folder: $ComfyUIPath" }

if (-not (Test-Path $VenvPython)) {
    if (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3 -m venv (Join-Path $ProjectRoot ".venv")
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        & python -m venv (Join-Path $ProjectRoot ".venv")
    } else {
        throw "Python 3 was not found. Install Python 3.11 or newer and enable Add Python to PATH."
    }
}

& $VenvPython -m pip install --disable-pip-version-check -r (Join-Path $ProjectRoot "server\requirements.txt")
if ($LASTEXITCODE -ne 0) { throw "Failed to install the gateway Python dependencies." }
$SecurePassword = Read-Host "Create the mobile gateway password" -AsSecureString
$PasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
try {
    $env:COMFY_MOBILE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($PasswordPointer)
    & $VenvPython (Join-Path $ProjectRoot "server\configure.py") --comfyui-path $ComfyUIPath --username $Username --port $GatewayPort
    if ($LASTEXITCODE -ne 0) { throw "Failed to create server\config.json." }
} finally {
    Remove-Item Env:COMFY_MOBILE_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($PasswordPointer)
}

$ruleName = "Comfy Mobile Gateway $GatewayPort"
try {
    if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort $GatewayPort | Out-Null
    }
    Write-Host "Windows Firewall port $GatewayPort is open." -ForegroundColor Green
} catch {
    Write-Warning "Run this setup once as Administrator to automatically open TCP port $GatewayPort."
}

$addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    Select-Object -ExpandProperty IPAddress -Unique
Write-Host "Setup complete." -ForegroundColor Green
foreach ($address in $addresses) { Write-Host "Mobile login address: http://${address}:$GatewayPort" }

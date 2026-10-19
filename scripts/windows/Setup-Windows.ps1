param([string]$PersonKeyPath = "")

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RawDirectory = Join-Path $ProjectRoot "app\src\main\res\raw"
$PersonKeyTarget = Join-Path $RawDirectory "person_key.json"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $SdkPath)) {
    Write-Warning "Android SDK was not found. Open this project in Android Studio and install Android SDK 35."
} else {
    $SdkValue = $SdkPath.Replace("\", "/")
    [System.IO.File]::WriteAllText((Join-Path $ProjectRoot "local.properties"), "sdk.dir=$SdkValue`n", $Utf8NoBom)
    Write-Host "Android SDK: $SdkPath" -ForegroundColor Green
}

if (-not $PersonKeyPath) {
    $PersonKeyPath = Read-Host "WireGuard person_key.json or .conf path (leave blank to configure later)"
}
if ($PersonKeyPath) {
    $PersonKeyPath = (Resolve-Path $PersonKeyPath).Path
    if ([IO.Path]::GetExtension($PersonKeyPath) -ieq ".json") {
        $Profile = Get-Content $PersonKeyPath -Raw | ConvertFrom-Json
        if ($Profile.type -ne "wireguard" -or -not $Profile.config) { throw "Invalid person_key JSON file." }
        $Json = Get-Content $PersonKeyPath -Raw
    } else {
        $WireGuardConfig = Get-Content $PersonKeyPath -Raw
        if ($WireGuardConfig -notmatch "\[Interface\]" -or $WireGuardConfig -notmatch "PrivateKey\s*=") {
            throw "Invalid WireGuard configuration file."
        }
        $Json = [ordered]@{
            version = 1
            type = "wireguard"
            name = "person_key"
            config = $WireGuardConfig
        } | ConvertTo-Json
    }
    [System.IO.Directory]::CreateDirectory($RawDirectory) | Out-Null
    [System.IO.File]::WriteAllText($PersonKeyTarget, $Json, $Utf8NoBom)
    Write-Host "VPN configuration installed locally. It remains excluded from Git." -ForegroundColor Green
} elseif (-not (Test-Path $PersonKeyTarget)) {
    Write-Warning "Add person_key.json before testing outside the server LAN."
}

Write-Host "Setup complete. Open the project in Android Studio and click Run, or run Run-Windows.bat." -ForegroundColor Green
Write-Host "The app will connect to http://192.168.5.160:8765 through person_key VPN."

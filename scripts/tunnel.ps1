# 用 Cloudflare 临时隧道把本机网关暴露到公网（免安装、免管理员）
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1
#
# 运行后会打印一个 https://xxxx.trycloudflare.com 地址，手机在任何网络下
# 把它填到登录页即可。注意：
#   * 这是临时地址，关闭窗口/进程后失效，每次启动都会变
#   * 公网可访问，务必先改掉默认密码: python server\set_local_password.py

param(
    [int]$Port = 8765,
    [string]$Cloudflared = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Cloudflared) {
    $Cloudflared = Join-Path $root "tools\cloudflared.exe"
}
if (-not (Test-Path $Cloudflared)) {
    Write-Host "找不到 cloudflared.exe: $Cloudflared" -ForegroundColor Red
    Write-Host "下载地址: https://github.com/cloudflare/cloudflared/releases/latest"
    exit 1
}

Write-Host "检查本机网关 http://127.0.0.1:$Port ..." -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod "http://127.0.0.1:$Port/api/health" -TimeoutSec 5
    Write-Host "网关正常: $($health.service)" -ForegroundColor Green
} catch {
    Write-Host "网关未运行，请先在“ComfyMobile 本地直连”窗口里一键启动。" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "启动隧道，稍等几秒会出现 https://xxx.trycloudflare.com 地址 ..." -ForegroundColor Cyan
Write-Host "把那个地址整行填到手机登录页；按 Ctrl+C 结束隧道。" -ForegroundColor Yellow
Write-Host ""

& $Cloudflared tunnel --url "http://127.0.0.1:$Port" --no-autoupdate

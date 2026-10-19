# Windows 使用说明

这个分支可直接在 Windows 10/11 上运行 Android 项目、本地网关和
Stability Matrix 中的 ComfyUI。

## 需要安装

1. Android Studio（安装 Android SDK 35）。
2. Python 3.11 或更新版本，安装时勾选 `Add Python to PATH`。
3. Stability Matrix 以及一个可正常启动的 ComfyUI 包。
4. Git for Windows。

## 第一次运行

在项目目录中双击：

```text
scripts\windows\Setup-Windows.bat
```

脚本会：

- 自动寻找 Stability Matrix 的 ComfyUI；找不到时要求手动选择包含
  `main.py` 的目录；
- 在项目的 `.venv` 中安装网关依赖；
- 要求设置手机登录网关使用的账号密码；
- 生成不会提交到 Git 的 `server\config.json`；
- 尝试为网关开放 Windows 防火墙 TCP 8765 端口；
- 显示手机登录页应填写的局域网地址。

如防火墙步骤提示权限不足，右键 `Setup-Windows.bat`，选择“以管理员身份运行”。

## 日常启动

双击：

```text
scripts\windows\Run-Windows.bat
```

它会分别启动 ComfyUI 和手机网关。窗口不要关闭。已经通过 Stability Matrix
启动 ComfyUI 时，也可以只运行 `Start-Gateway.ps1`。

## 构建 Android APK

使用 Android Studio 打开项目根目录，等待 Gradle Sync 完成后运行 `app`；
也可以双击：

```text
scripts\windows\Build-Android.bat
```

APK 输出到 `app\build\outputs\apk\debug\app-debug.apk`。

真实 VPN 配置应放在
`app\src\main\res\raw\person_key.json`。它已被 `.gitignore` 排除，不会上传。

## 手机连接

手机和 Windows 处于同一局域网时，在登录页填写初始化脚本显示的地址，例如：

```text
http://192.168.1.20:8765
```

账号默认为 `admin`，密码是初始化时设置的密码。外网真机测试时再打开
`person_key` VPN 开关。

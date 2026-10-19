# Windows 客户端运行说明

`dev` 分支用于在 Windows 的 Android Studio 中启动手机 App，并通过
`person_key` VPN 连接当前服务器上的网关和 ComfyUI。Windows 本机不需要再安装
ComfyUI、Stability Matrix 或 Python 网关。

## 准备环境

1. 安装 Git for Windows。
2. 安装 Android Studio，并安装 Android SDK 35。
3. 准备服务器 VPN 配置：原始 WireGuard `.conf` 或项目使用的
   `person_key.json` 均可。

## 拉取与初始化

```text
git clone -b dev git@github.com:chenjingru111/comfyui_2android.git
cd comfyui_2android
scripts\windows\Setup-Windows.bat
```

初始化脚本会自动填写 `local.properties`，并把选择的 `.conf` 安全转换成：

```text
app\src\main\res\raw\person_key.json
```

该文件包含私钥，已被 Git 忽略。

## 运行

推荐用 Android Studio 打开项目根目录，选择模拟器或 USB 真机，然后点击
Run。也可以先启动模拟器，再双击：

```text
scripts\windows\Run-Windows.bat
```

脚本会构建、安装并打开 App。登录页默认配置为：

- 服务器：`http://192.168.5.160:8765`
- 账号：`admin`
- VPN：默认开启

输入服务器网关密码并登录；首次连接时允许 Android 的 VPN 权限即可。

## 说明

- Windows 只负责运行 Android Studio、模拟器或连接真机。
- ComfyUI、模型、工作流和网关仍然运行在当前 GPU 服务器上。
- 不要把 `person_key.json`、生成的 APK 或 VPN 私钥公开上传。
- 模拟器和真机同时使用同一份 WireGuard 私钥可能互相抢占连接，建议一次只测试一个设备。

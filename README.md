# Comfy Mobile

Native Android client and local authenticated gateway for ComfyUI.

## Architecture

- `app/.../model`: HTTP client, session state, DTOs and repository.
- `app/.../view`: Android Activities and XML UI.
- `app/.../controller`: page controllers and view contracts.
- `app/.../adapter`: ListView adapters, including dynamic ComfyUI input mapping.
- `server`: local authentication, ComfyUI discovery, workflow compilation, uploads, queueing and artifacts.

## Start locally

```bash
/gpu_move/android-dev/projects/ComfyMobile/scripts/start-comfyui.sh
/gpu_move/android-dev/projects/ComfyMobile/scripts/start-gateway.sh
```

Gateway: `http://192.168.5.160:8765`

## Windows 本地直连（用本机的 ComfyUI 出图）

网关和 ComfyUI 都在同一台 Windows 主机上时，不需要服务器，也不需要 VPN：

1. 双击项目根目录的 `启动-本地直连.bat`（等价于 `python server\comfy_local_gui.py`）。
2. 点“自动检测”确认 ComfyUI 目录（例如 `E:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI`），
   填好账号密码，点“保存配置”。
3. 点“一键启动”，等 ComfyUI 与网关都变绿；窗口给出手机登录地址，例如 `http://192.168.5.23:8765`。
4. 手机装同一个 APK，登录页的“本地域名”填这个地址，账号密码与窗口一致。

窗口负责：

- 生成 `server/config.json`（`comfyCandidates` 指向 `127.0.0.1:8188`，工作流根目录指向本机
  `user/default/workflows`，上传目录指向本机 `input`）；
- 首次运行创建 `server/.venv` 并安装 `server/requirements.txt`；
- 启停 ComfyUI（自动使用 Aki/便携版的 `python`）与网关，实时显示状态与日志；
- “放行防火墙”为本机 8765 端口添加入站规则（会弹 UAC）；
- “USB 转发”执行 `adb reverse`，手机端可改填 `http://127.0.0.1:8765`；
- “自检连接”验证登录、工作流枚举与输入解析，“生成一张测试图”验证完整出图与产物回传。

注意：

- 手机与电脑需在同一局域网；首次运行请允许 Windows 防火墙的“专用网络”放行 8765。
- **手机不用线、也不在同一网络**：窗口里点「启动外网隧道」，会用 `tools/cloudflared.exe`
  生成一个 `https://xxx.trycloudflare.com` 地址（免安装、免管理员），手机在任何网络下都能连。
  地址每次启动都会变，关窗口即失效；公网可访问，**先改密码**：
  `python server\set_local_password.py 你的密码`。
- **手机和电脑不在同一网络时**，见 [docs/remote-access.md](docs/remote-access.md)：
  USB 数据线（`adb reverse`）、Cloudflare 临时隧道、把本机接入现有 WireGuard、
  Tailscale 四种方式都有对照表和步骤。
- `workflows/ComfyMobile_本地快速测试.json` 是 7 节点 SD1.5 小工作流，约 2 秒出图，用来确认链路；
  换模型时把它里面的 `ckpt_name` 改成自己有的 checkpoint 即可。
- 只用命令行时：`python server\configure_local.py` 生成配置，
  再执行 `server\.venv\Scripts\python.exe server\comfy_mobile_server.py` 启动网关。
- 网关重启才会读取新的 `config.json`；窗口里保存配置会自动重启网关。

Open this directory in Android Studio or build with:

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Customize the Android UI

The complete interface is defined in XML under `app/src/main/res`:

- `layout/activity_*.xml`: full application screens.
- `layout/row_*.xml`: workflow, job and dynamic input rows.
- `values/colors.xml`: the application color palette.
- `values/dimens.xml`: shared spacing and sizing.
- `values/styles.xml`: typography, buttons and input styles.
- `values/strings.xml`: all user-facing labels.

`WorkflowInputAdapter` only binds workflow values to the controls declared in
`layout/row_workflow_input.xml`; it no longer creates UI widgets in Java.

工作流详情页（`activity_workflow_detail.xml` + `row_workflow_input.xml`）：

- 每个输入一张卡片，右上角有类型徽标（图片 / 开关 / 下拉 / 数字 / 长文本 …）；
- 布尔参数用 Switch 显示「已开启 / 已关闭」，不再是一排分不清的“启用”；
- 图片/视频/音频会显示当前值（`当前：xxx.jpg`）并提供「更换」按钮；
- 长文本限制为 6 行且可内部滚动，避免一个提示词卡片撑满整屏；
- 顶部「筛选参数」框可按节点名或参数名过滤，参数名命中的结果排在最前，
  也可以直接输入「图片」「开关」这类类型词；
- **按 ComfyUI 画布分区（`groups`）分组**：网关用节点坐标判断它属于哪个分区，
  详情页在每个分区第一行显示分区标题，顺序与画布从上到下一致；
- 画布上被旁路（`mode != 0`）的节点也会返回，标记为「已旁路」并置灰只读；
  默认只显示会执行的节点，打开顶部「显示已旁路的节点」才列出全部
  （例如 Work-Fisher 双音色版共 180 个输入，其中 152 个是旁路状态）。
- **分区开关**：每个分区标题右侧有 Switch，提交时会作为 `groupModes` 发给网关，
  网关改完节点 mode 再编译，等于在手机上做“取消旁路 / 旁路整组”；
  图片/音频行右侧还有单项开关，对应 `nodeModes`，可以只启用其中几张参考图或某段音色。
  这些改动只影响这一次提交，不会回写磁盘上的工作流文件。

「生成记录」页右上角有 **本地文件** 按钮，进入 `OutputFilesActivity`：

- 浏览 ComfyUI 的 `output/` 目录（含子文件夹），按文件夹/图片/视频/音频分类显示大小与时间；
- 点图片/视频直接用现有产物查看器打开，可“保存到手机”；
- 对应网关接口 `GET /api/outputs?path=`（列目录）和 `GET /api/outputs/file?path=`
  （读文件），路径做了越界校验，只能访问 `output/` 之下。

### 登录态

- 登录成功后会保存 token（7 天有效），之后**启动 App 直接进工作流列表**，不再要求重新输入密码；
- 网关返回 401（token 过期 / 换了 `tokenSecret`）时，App 自动清掉本地会话并跳回登录页；
- 列表页右上角有 **账号** 按钮：重新登录、修改服务器地址、退出登录都在这里；
- App 不保存密码，token 过期后需要重新输一次。

## Local configuration

Private configuration is intentionally excluded from Git. Before running a new
clone, copy `server/config.example.json` to `server/config.json`, replace its
placeholder credentials and paths, and run `server/set_password.py`.

To enable the login-page VPN switch, copy
`app/src/main/res/raw/person_key_example.json` to
`app/src/main/res/raw/person_key.json` and replace the WireGuard placeholders.
The real `person_key.json` is packaged only in local builds and must never be
committed because it contains a private key.

The linked user services are `comfy-mobile-comfyui.service` and
`comfy-mobile-gateway.service`. Check them with `systemctl --user status`.

Change the gateway password with:

```bash
/gpu_move/lly/telemac/conda_envs/android-dev/bin/python server/set_password.py
systemctl --user restart comfy-mobile-gateway.service
```

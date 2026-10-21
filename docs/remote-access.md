# 手机和电脑不在同一个网络时怎么连

## 先试最省事的一条：数据线（USB 直连）

手机用数据线插在电脑上、已开 USB 调试时，**不用管网络**：

```powershell
adb reverse tcp:8765 tcp:8765
```

手机登录页填 `http://127.0.0.1:8765` 即可。窗口里点「USB 转发（adb reverse）」是同一件事，
成功后会**自动把地址切到 127.0.0.1**。

限制：临时的，拔线 / 重启 adb 后要再执行一次；适合调试和临时用。

## 最常见的三种情况

| 情况 | 手机登录页填 |
|---|---|
| 数据线连着（adb reverse） | `http://127.0.0.1:8765` |
| 同一 Wi-Fi | `http://<电脑局域网 IP>:8765`，例如 `192.168.2.194` |
| 手机纯流量 / 人在外面 | `https://xxx.trycloudflare.com`（点窗口「启动外网隧道」） |

## 纯流量 / 人在外面：Cloudflare 临时隧道（已验证可用）

窗口里点 **「启动外网隧道」**，10~20 秒后「外网临时地址」栏会出现一个
`https://xxxx.trycloudflare.com`（自动复制到剪贴板）。把它整行填到手机登录页即可，
手机用 4G/5G、在任何网络下都能连。

要点：

- 用的是 `tools\cloudflared.exe`（单文件、免安装、免管理员），前提是网关已启动。
- **地址每次启动都会变**，关掉窗口/隧道就失效；适合临时在外面用。
- 它是**公网可访问**的，务必先改掉默认密码：
  ```powershell
  python server\set_local_password.py 你的新密码
  ```
  然后重启网关。
- 想长期稳定：用 Cloudflare 账号 + 自己的域名建 "named tunnel"，地址就固定了；
  或者走下面的方案 A / B（私网，更安全）。
- 命令行等价：`powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1`

## 一句话结论

网关（8765）只在电脑本机的局域网里，手机在别的网络时**必须有一条虚拟通道**把两端接起来。
你这台电脑没有公网 IPv4，所以别做端口映射，推荐：

| 方案 | 需要动服务器 | 手机要装东西 | 稳定性 | 推荐度 |
|---|---|---|---|---|
| E. Cloudflare 临时隧道 | 不用 | 不用 | 中（地址会变，走境外节点） | ★★★★ |
| **A. 把电脑接进现有 WireGuard** | 要（加一个 peer） | 不用，App 自带 VPN 开关 | 高（就是你现在用的隧道） | ★★★★★ |
| B. Tailscale / ZeroTier | 不用 | 要装 App | 一般（国内打洞不稳） | ★★★ |
| C. 手机开热点，电脑连热点 | 不用 | 不用 | 高（但要求人在旁边） | ★★★ |
| D. 公网端口映射 / IPv6 直连 | 要改路由器 | 不用 | 低且不安全 | ★ |

---

## 方案 A：把电脑接进你已经有的 WireGuard（推荐）

现状：手机里的 `person_key.json` 指向 `125.43.231.224:51820`，隧道网段 `192.168.4.0/24`
（中枢 `192.168.4.1`，手机 `192.168.4.5`），并且能经它访问 `192.168.5.160`。
所以只要给电脑分配 `192.168.4.6`，手机就能像访问 `192.168.5.160` 一样访问 `192.168.4.6:8765`。

密钥已经帮你生成好了（**`wireguard/` 目录含私钥，已 gitignore，别提交、别外发**）：

- `wireguard/pc-comfymobile.conf` — 导入到电脑的 WireGuard 客户端
- `wireguard/server-peer.txt` — 粘贴到中枢服务器的 WireGuard 配置里

### 1. 中枢服务器：加一个 peer

```bash
# 看接口名，通常是 wg0
sudo wg show

# 立即生效（把 <PUB> 换成 wireguard/server-peer.txt 里的 PublicKey）
sudo wg set wg0 peer <PUB> allowed-ips 192.168.4.6/32

# 持久化：把 wireguard/server-peer.txt 的 [Peer] 块手动粘贴到 /etc/wireguard/wg0.conf 末尾
sudo nano /etc/wireguard/wg0.conf
```

> 如果中枢是 OpenWrt / 路由器而不是 Linux 主机，接口名和持久化方式不同（`/etc/config/network`），
> 用 LuCI 的「WireGuard 接口 → 对端 → 添加」更直观，删掉 `[Peer]` 语法、填同样的 PublicKey 与 AllowedIPs 即可。
> **如果你不是这个中枢的管理员**（比如它是公司网关），方案 A 走不通，直接用方案 B 或 C。

### 2. 中枢服务器：允许 peer 之间互相转发

桌面版 WireGuard 默认只做「客户端 → 服务器」，peer 到 peer 需要转发：

```bash
sudo sysctl -w net.ipv4.ip_forward=1                 # 建议写进 /etc/sysctl.conf
sudo iptables -A FORWARD -i wg0 -o wg0 -j ACCEPT     # 重启后要恢复（iptables-persistent / ufw route）
```

确认：

```bash
sudo iptables -S FORWARD | grep wg0
# 应能看到 -A FORWARD -i wg0 -o wg0 -j ACCEPT
```

### 3. 电脑：导入并启用

1. 安装 WireGuard for Windows：<https://www.wireguard.com/install/>
2. 「新建隧道」→ 粘贴 `wireguard/pc-comfymobile.conf` 内容 → 保存 → 启用
3. 验证：`ping 192.168.4.1`（中枢）、`ping 192.168.4.5`（手机，手机需在线）

> 配置里 `AllowedIPs = 192.168.4.0/24` 是**分流**模式，电脑自己的上网不受影响。

### 4. 窗口里换成虚拟网卡地址

1. 双击 `启动-本地直连.bat`
2. 「刷新地址」→ 下拉框选 `192.168.4.6`（会记住，下次自动选中）
3. 点「放行防火墙」（会弹 UAC；规则对 8765 入站、所有网络配置放行）
4. 「一键启动」，确认 ComfyUI 与网关都变绿

### 5. 手机

登录页地址填 `http://192.168.4.6:8765`，账号密码同窗口。
如果手机此时没连 VPN，就勾上登录页的 **VPN 开关**（用的就是 `res/raw/person_key.json`）。

### 6. 验证顺序（失败时按这个查）

| 检查 | 命令 / 位置 | 期望 |
|---|---|---|
| 电脑到中枢 | 电脑 `ping 192.168.4.1` | 通 |
| 中枢转发已开 | 服务器 `sysctl net.ipv4.ip_forward` | = 1 |
| peer 已加 | 服务器 `sudo wg show` | 有 `192.168.4.6/32` 且 `latest handshake` 有值 |
| 网关在听 | 电脑 `netstat -ano \| findstr :8765` | `0.0.0.0:8765` |
| 手机到电脑 | 手机浏览器开 `http://192.168.4.6:8765/api/health` | 返回 `{"ok":true,...}` |
| 手机到网关业务 | 手机登录 App | 能看到工作流列表 |

---

## 方案 B：Tailscale / ZeroTier（不想动服务器时）

电脑和手机各装一个、登同一个账号，厂商会自动打洞并给出稳定虚拟 IP：

1. 电脑装 Tailscale 并登录
2. 窗口「刷新地址」→ 选 `100.x.y.z`
3. 「放行防火墙」
4. 手机装 Tailscale、登录同账号，登录页填 `http://100.x.y.z:8765`

注意：国内直连 Tailscale 官方协调服务器/中继可能不稳，经常退化成慢速中继；
能稳定用的前提是打洞成功。ZeroTier 同理。要长期稳定，还是方案 A。

---

## 方案 C：手机开热点，电脑连热点

人和设备在一起时最省事：

1. 手机开「个人热点」
2. 电脑连这个热点（有线网卡继续上网，热点网卡只用于互访）
3. 窗口「刷新地址」→ 选热点网段的地址（常见 `192.168.43.x`、`192.168.137.x`）
4. 「放行防火墙」，手机填这个地址

---

## 方案 D：公网直连（不推荐）

- 你这条线只有 IPv6 公网段（`2408:...`），没有公网 IPv4，端口映射做不了；
- IPv6 直连要路由器放行入站 + 前缀会变（需要 DDNS），而且把 8765 明文 HTTP 暴露在公网；
- 真要走公网，至少套一层 HTTPS 反向代理（Caddy + 域名证书），并先改掉默认密码。

---

## 安全提醒

- 网关默认账号 `admin` / `admin1234`，在任何「不只自己用」的虚拟网络里请先改密码（窗口里改，保存后自动重启）。
- 不要把 8765 映射到公网；隧道内跑明文 HTTP 是安全的，因为外层已经加密。
- `wireguard/` 目录、`server/config.json`、`res/raw/person_key.json` 都含敏感信息，仓库里已排除，别手动提交。

# Codex Mobile

Codex Mobile 是独立安卓客户端，通过 `person_key` VPN 访问服务器上的认证网关，
并由网关连接本机 Codex app-server。它与 VS Code Codex 使用相同的本地会话库。

## 功能

- 使用固定服务器地址和账号密码登录；
- 列出、打开和新建 VS Code/Codex 对话；
- 查看用户消息、Codex 回复和截断后的工具输出；
- 发送新任务并实时显示回复；
- 停止正在执行的 turn；
- 在手机上批准或拒绝命令及文件修改请求。

## 构建 APK

```bash
./gradlew :codexapp:assembleDebug
```

输出文件：

```text
codexapp/build/outputs/apk/debug/codexapp-debug.apk
```

真实 VPN 配置应放在：

```text
codexapp/src/main/res/raw/person_key.json
```

该文件和服务器的 `codex-server/config.json` 均已被 Git 忽略。

## 服务器

网关默认只监听 VPN 可达的 `192.168.5.160:8770`。用户服务：

```bash
systemctl --user status codex-mobile-gateway.service
systemctl --user restart codex-mobile-gateway.service
```

网关通过 stdin/stdout RPC 启动本机 `codex app-server`，不会向网络直接公开
Codex 进程、ChatGPT 登录令牌或任意 shell 接口。

不要让 VS Code 和手机同时向同一条会话发送任务；可以同时查看，但并发执行可能
造成两个 Codex app-server 竞争同一会话文件。

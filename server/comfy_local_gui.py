"""ComfyMobile · 本地直连窗口

把本机的 ComfyUI 接到 ComfyMobile 安卓端：
  * 自动定位本机 ComfyUI（含便携版 python），生成 server/config.json
  * 一键启动 / 停止 ComfyUI 与网关，实时显示状态
  * 给出手机端应填写的局域网地址，支持复制与 USB(adb reverse) 转发
  * 内置自检：登录 → 读工作流 → 解析输入 → 可选出测试图

只依赖标准库（tkinter + urllib），不需要额外安装。
"""

from __future__ import annotations

import json
import os
import queue
import re
import secrets
import shutil
import string
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

sys.path.insert(0, str(Path(__file__).resolve().parent))
import local_env  # noqa: E402

# 与 App 的 colors.xml 保持一致
BG = "#F4F6FA"
SURFACE = "#FFFFFF"
MUTED = "#EEF2F7"
PRIMARY = "#2563EB"
TEXT = "#111827"
SECONDARY = "#6B7280"
DIVIDER = "#E5E7EB"
SUCCESS = "#15803D"
ERROR = "#B91C1C"
FONT = ("Microsoft YaHei UI", 9)
FONT_BOLD = ("Microsoft YaHei UI", 9, "bold")
FONT_TITLE = ("Microsoft YaHei UI", 14, "bold")
FONT_MONO = ("Consolas", 9)


def adb_executable() -> str | None:
    found = shutil.which("adb")
    if found:
        return found
    for candidate in (
        Path(r"E:\Android\Sdk\platform-tools\adb.exe"),
        Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe",
    ):
        if candidate.is_file():
            return str(candidate)
    return None


class LocalWindow:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("ComfyMobile · 本地直连")
        self.root.geometry("1020x820")
        self.root.minsize(900, 700)
        self.root.configure(bg=BG)

        self.config_data = local_env.read_config()
        self.comfy_service: local_env.Service | None = None
        self.gateway_service: local_env.Service | None = None
        self.tunnel_service: local_env.Service | None = None
        self.log_queue: queue.Queue[str] = queue.Queue()
        self.actions: queue.Queue = queue.Queue()
        self.comfy_port = local_env.DEFAULT_COMFY_PORT
        self.gateway_port = local_env.DEFAULT_GATEWAY_PORT
        self.lan_address = "127.0.0.1"
        self.comfy_up = False
        self.gateway_up = False
        self.deps_ok = False
        self.closing = False
        self.busy = False

        self._init_vars()
        self._build_ui()
        self._drain_log()
        self._tick()
        threading.Thread(target=self._status_loop, daemon=True).start()
        threading.Thread(target=self._check_deps, daemon=True).start()
        self.root.after(150, self._autodetect)
        self.root.after(400, lambda: self._generate_password(automatic=True))
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    # ------------------------------------------------------------------ 变量
    def _init_vars(self) -> None:
        config = self.config_data or {}
        comfy_url = (config.get("comfyCandidates") or ["http://127.0.0.1:8188"])[0]
        comfy_port = local_env.DEFAULT_COMFY_PORT
        if ":" in comfy_url:
            try:
                comfy_port = int(comfy_url.rsplit(":", 1)[1].strip("/"))
            except ValueError:
                pass
        self.comfy_port = comfy_port
        self.gateway_port = int(config.get("port", local_env.DEFAULT_GATEWAY_PORT))

        roots = config.get("workflowRoots") or []
        self.comfy_root_var = tk.StringVar()
        self.workflows_var = tk.StringVar(value=roots[0] if roots else "")
        self.uploads_var = tk.StringVar(value=config.get("comfyInputDir", ""))
        self.port_var = tk.StringVar(value=str(self.gateway_port))
        self.comfy_port_var = tk.StringVar(value=str(comfy_port))
        self.user_var = tk.StringVar(value=config.get("username", local_env.DEFAULT_USERNAME))
        self.password_var = tk.StringVar(value="" if config else local_env.DEFAULT_PASSWORD)
        self.include_bundled_var = tk.BooleanVar(value=True)
        self.tunnel_var = tk.StringVar(value="")
        self.tunnel_url = ""
        self.host_options = local_env.ipv4_addresses()
        saved_host = str(local_env.read_ui().get("phoneHost", "") or "")
        default_host = saved_host or (self.host_options[0] if self.host_options else self.lan_address)
        self.host_var = tk.StringVar(value=default_host)
        self.address_hint_var = tk.StringVar(value="")
        self.comfy_state_var = tk.StringVar(value="未运行")
        self.gateway_state_var = tk.StringVar(value="未运行")
        self.config_state_var = tk.StringVar(value="")
        self._refresh_config_state()

    def _refresh_config_state(self) -> None:
        if local_env.CONFIG_PATH.is_file():
            self.config_state_var.set(f"配置已生成:{local_env.CONFIG_PATH}")
        else:
            self.config_state_var.set("配置未生成: 填写账号密码后点“保存配置”")

    # -------------------------------------------------------------------- UI
    def _build_ui(self) -> None:
        header = tk.Frame(self.root, bg=BG)
        header.pack(fill="x", padx=18, pady=(16, 10))
        tk.Label(header, text="ComfyMobile 本地直连", bg=BG, fg=TEXT, font=FONT_TITLE).pack(anchor="w")
        tk.Label(
            header,
            text="让手机上的 ComfyMobile 连到这台电脑的 ComfyUI: 先保存配置, 再一键启动, 最后在手机登录页填入下面的地址。",
            bg=BG, fg=SECONDARY, font=FONT, justify="left",
        ).pack(anchor="w", pady=(2, 0))

        outer, body = self._card("① 路径与端口")
        outer.pack(fill="x", padx=18, pady=(0, 10))
        self._path_row(body, 0, "ComfyUI 目录", self.comfy_root_var, self._browse_comfy)
        self._path_row(body, 1, "工作流目录", self.workflows_var, self._browse_workflows)
        self._path_row(body, 2, "上传目录", self.uploads_var, self._browse_uploads)

        bundled_row = tk.Frame(body, bg=SURFACE)
        bundled_row.grid(row=3, column=0, columnspan=3, sticky="w", pady=(4, 0))
        tk.Checkbutton(
            bundled_row, text="同时挂载项目内置测试工作流（workflows/ComfyMobile_本地快速测试.json）",
            variable=self.include_bundled_var, bg=SURFACE, fg=TEXT, font=FONT,
            activebackground=SURFACE, selectcolor=SURFACE, bd=0, highlightthickness=0,
        ).pack(side="left")

        line = tk.Frame(body, bg=SURFACE)
        line.grid(row=4, column=0, columnspan=3, sticky="we", pady=(8, 0))
        tk.Label(line, text="网关端口", bg=SURFACE, fg=TEXT, font=FONT).pack(side="left")
        tk.Entry(line, textvariable=self.port_var, width=7, font=FONT, relief="solid", bd=1,
                 highlightthickness=0).pack(side="left", padx=(6, 16))
        tk.Label(line, text="ComfyUI 端口", bg=SURFACE, fg=TEXT, font=FONT).pack(side="left")
        tk.Entry(line, textvariable=self.comfy_port_var, width=7, font=FONT, relief="solid", bd=1,
                 highlightthickness=0).pack(side="left", padx=(6, 16))
        tk.Label(line, text="账号", bg=SURFACE, fg=TEXT, font=FONT).pack(side="left")
        tk.Entry(line, textvariable=self.user_var, width=10, font=FONT, relief="solid", bd=1,
                 highlightthickness=0).pack(side="left", padx=(6, 16))
        tk.Label(line, text="密码", bg=SURFACE, fg=TEXT, font=FONT).pack(side="left")
        tk.Entry(line, textvariable=self.password_var, width=18, font=("Consolas", 11), relief="solid",
                 bd=1, highlightthickness=0).pack(side="left", padx=(6, 8))
        self._button(line, "复制", self._copy_password).pack(side="left", padx=(0, 6))
        self._button(line, "换一个", self._generate_password).pack(side="left")

        line2 = tk.Frame(body, bg=SURFACE)
        line2.grid(row=5, column=0, columnspan=3, sticky="w", pady=(8, 0))
        tk.Button(line2, text="自动检测", command=self._autodetect, font=FONT, relief="solid", bd=1,
                  bg=MUTED, activebackground=DIVIDER, cursor="hand2").pack(side="left", padx=(0, 6))
        tk.Button(line2, text="保存配置", command=self._save_config, font=FONT, relief="solid", bd=1,
                  bg=MUTED, activebackground=DIVIDER, cursor="hand2").pack(side="left")

        tk.Label(body, textvariable=self.config_state_var, bg=SURFACE, fg=SECONDARY, font=FONT).grid(
            row=6, column=0, columnspan=3, sticky="w", pady=(8, 0))
        tk.Label(body, text="密码每次启动都会随机重新生成、明码显示在这里；点“复制”带到手机，点“换一个”立刻换。",
                 bg=SURFACE, fg=SECONDARY, font=FONT).grid(row=7, column=0, columnspan=3, sticky="w", pady=(2, 0))
        tk.Label(body, text="密码只以哈希形式保存在 server/config.json（.gitignore 已排除）；改了密码网关会自动重启生效。",
                 bg=SURFACE, fg=SECONDARY, font=FONT).grid(row=8, column=0, columnspan=3, sticky="w", pady=(2, 0))

        outer, body = self._card("② 服务")
        outer.pack(fill="x", padx=18, pady=(0, 10))
        self.comfy_dot = self._service_row(body, 0, "ComfyUI", self.comfy_state_var,
                                           self._start_comfy, self._stop_comfy)
        self.gateway_dot = self._service_row(body, 1, "网关 Gateway", self.gateway_state_var,
                                             self._start_gateway, self._stop_gateway)

        actions = tk.Frame(body, bg=SURFACE)
        actions.grid(row=2, column=0, columnspan=4, sticky="we", pady=(10, 0))
        self._button(actions, "一键启动（ComfyUI + 网关）", self._start_all, primary=True).pack(side="left")
        self._button(actions, "全部停止", self._stop_all).pack(side="left", padx=8)
        self._button(actions, "安装/修复依赖", self._install_deps).pack(side="left")
        self._button(actions, "打开 ComfyUI 网页", self._open_comfy).pack(side="left", padx=8)
        self._button(actions, "放行防火墙", self._allow_firewall).pack(side="left")

        outer, body = self._card("③ 手机连接")
        outer.pack(fill="x", padx=18, pady=(0, 10))
        row = tk.Frame(body, bg=SURFACE)
        row.pack(fill="x")
        tk.Label(row, text="手机登录地址", bg=SURFACE, fg=TEXT, font=FONT_BOLD).pack(side="left")
        tk.Label(row, text="http://", bg=SURFACE, fg=PRIMARY,
                 font=("Consolas", 13, "bold")).pack(side="left", padx=(12, 0))
        self.host_box = ttk.Combobox(row, textvariable=self.host_var, values=self.host_options,
                                     width=17, font=("Consolas", 12))
        self.host_box.pack(side="left")
        self.host_box.bind("<<ComboboxSelected>>", lambda _event: self._remember_host())
        self.port_label = tk.Label(row, text=f":{self.gateway_port}", bg=SURFACE, fg=PRIMARY,
                                   font=("Consolas", 13, "bold"))
        self.port_label.pack(side="left", padx=(0, 12))
        self._button(row, "复制", self._copy_address).pack(side="left")
        self._button(row, "刷新地址", self._refresh_addresses).pack(side="left", padx=8)
        self._button(row, "USB 转发（adb reverse）", self._adb_reverse).pack(side="left")

        tunnel_row = tk.Frame(body, bg=SURFACE)
        tunnel_row.pack(fill="x", pady=(8, 0))
        tk.Label(tunnel_row, text="外网临时地址", bg=SURFACE, fg=TEXT, font=FONT_BOLD).pack(side="left")
        tk.Entry(tunnel_row, textvariable=self.tunnel_var, font=("Consolas", 11), relief="solid", bd=1,
                 highlightthickness=0, state="readonly", width=12).pack(
            side="left", padx=(10, 8), fill="x", expand=True)
        self._button(tunnel_row, "启动外网隧道", self._start_tunnel).pack(side="left")
        self._button(tunnel_row, "复制", self._copy_tunnel).pack(side="left", padx=8)
        self._button(tunnel_row, "停止隧道", self._stop_tunnel).pack(side="left")

        tk.Label(body, textvariable=self.address_hint_var, bg=SURFACE, fg=SECONDARY,
                 font=FONT).pack(anchor="w", pady=(6, 0))
        tk.Label(
            body,
            text=("① 数据线连着手机：点「USB 转发（adb reverse）」，地址自动切到 127.0.0.1，不用管网络。\n"
                  "② 手机和电脑同一 Wi-Fi：用上面的局域网地址（首次点「放行防火墙」）。\n"
                  "③ 手机纯流量 / 人在外面：点「启动外网隧道」，把右边那个 https 地址复制到手机登录页。\n"
                  "    隧道地址每次启动都会变，关掉窗口就失效；临时使用够用。\n"
                  "④ 长期用：让两端进同一个虚拟专网（本机接入 WireGuard / 装 Tailscale），地址更稳定也更安全。\n"
                  "手机登录页的“本地域名”一栏填上面任意一种地址，账号密码与这里一致。"),
            bg=SURFACE, fg=SECONDARY, font=FONT, justify="left",
        ).pack(anchor="w", pady=(4, 0))
        self._button(body, "远程访问说明（不同网络怎么连）", self._open_remote_doc).pack(anchor="w", pady=(6, 0))

        outer, body = self._card("④ 自检与日志")
        outer.pack(fill="both", expand=True, padx=18, pady=(0, 16))
        row = tk.Frame(body, bg=SURFACE)
        row.pack(fill="x")
        self._button(row, "自检连接（登录 + 读工作流 + 解析输入）", self._self_test).pack(side="left")
        self._button(row, "生成一张测试图", self._run_test_image).pack(side="left", padx=8)
        self._button(row, "打开产物目录", self._open_output).pack(side="left")

        log_frame = tk.Frame(body, bg="#111827")
        log_frame.pack(fill="both", expand=True, pady=(10, 0))
        self.log = tk.Text(log_frame, height=11, bg="#111827", fg="#D1D5DB", insertbackground="#D1D5DB",
                           font=FONT_MONO, relief="flat", wrap="word", padx=10, pady=8)
        scroll = tk.Scrollbar(log_frame, command=self.log.yview, width=12)
        self.log.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.log.pack(side="left", fill="both", expand=True)
        self.log.configure(state="disabled")

        self._log("窗口已就绪。建议顺序：自动检测 → 保存配置 → 一键启动 → 自检连接。")
        self._fit_to_screen()

    def _fit_to_screen(self) -> None:
        """按实际屏幕自适应，避免高 DPI / 小屏下窗口被裁切。"""
        self.root.update_idletasks()
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        width = min(max(940, self.root.winfo_reqwidth()), int(screen_w * 0.92))
        height = min(max(700, self.root.winfo_reqheight() + 20), int(screen_h * 0.92))
        left = max(0, (screen_w - width) // 2)
        top = max(0, (screen_h - height) // 2)
        self.root.geometry(f"{width}x{height}+{left}+{top}")

    def _card(self, title: str):
        outer = tk.Frame(self.root, bg=SURFACE, highlightbackground=DIVIDER, highlightthickness=1)
        tk.Label(outer, text=title, bg=SURFACE, fg=TEXT, font=("Microsoft YaHei UI", 10, "bold"),
                 anchor="w").pack(fill="x", padx=14, pady=(10, 6))
        body = tk.Frame(outer, bg=SURFACE)
        body.pack(fill="both", expand=True, padx=14, pady=(0, 12))
        return outer, body

    def _path_row(self, parent: tk.Frame, row: int, label: str, variable: tk.StringVar, browse) -> None:
        tk.Label(parent, text=label, bg=SURFACE, fg=TEXT, font=FONT).grid(
            row=row, column=0, sticky="e", padx=(0, 10), pady=3)
        entry = tk.Entry(parent, textvariable=variable, font=FONT, relief="solid", bd=1, highlightthickness=0)
        entry.grid(row=row, column=1, sticky="we", pady=3)
        tk.Button(parent, text="浏览…", command=browse, font=FONT, relief="solid", bd=1, bg=MUTED,
                  activebackground=DIVIDER, cursor="hand2").grid(row=row, column=2, padx=(8, 0), pady=3)
        parent.grid_columnconfigure(1, weight=1)

    def _button(self, parent, text: str, command, primary: bool = False) -> tk.Button:
        return tk.Button(
            parent, text=text, command=command, font=FONT,
            bg=PRIMARY if primary else MUTED, fg="#FFFFFF" if primary else TEXT,
            activebackground="#1D4ED8" if primary else DIVIDER,
            activeforeground="#FFFFFF" if primary else TEXT,
            relief="flat" if primary else "solid", bd=0 if primary else 1,
            padx=12, pady=4, cursor="hand2",
        )

    def _service_row(self, parent: tk.Frame, row: int, name: str, state_var: tk.StringVar,
                     start, stop) -> tk.Label:
        dot = tk.Label(parent, text="●", bg=SURFACE, fg=SECONDARY, font=("Microsoft YaHei UI", 11))
        dot.grid(row=row, column=0, sticky="w", pady=4)
        tk.Label(parent, text=name, bg=SURFACE, fg=TEXT, font=FONT_BOLD, width=14, anchor="w").grid(
            row=row, column=1, sticky="w", pady=4)
        tk.Label(parent, textvariable=state_var, bg=SURFACE, fg=SECONDARY, font=FONT, anchor="w").grid(
            row=row, column=2, sticky="w", pady=4)
        buttons = tk.Frame(parent, bg=SURFACE)
        buttons.grid(row=row, column=3, sticky="e", pady=4)
        self._button(buttons, "启动", start).pack(side="left")
        self._button(buttons, "停止", stop).pack(side="left", padx=6)
        parent.grid_columnconfigure(2, weight=1)
        return dot

    # ------------------------------------------------------------------ 日志
    def _log(self, message: str) -> None:
        self.log_queue.put(message)

    def _post(self, callback) -> None:
        """从后台线程安全地安排一个主线程动作（tkinter 不能在子线程里操作）。"""
        self.actions.put(callback)

    def _run_actions(self) -> None:
        while True:
            try:
                callback = self.actions.get_nowait()
            except queue.Empty:
                return
            try:
                callback()
            except Exception as error:  # noqa: BLE001
                self._log(f"操作失败：{error}")

    def _drain_log(self) -> None:
        lines: list[str] = []
        while True:
            try:
                lines.append(self.log_queue.get_nowait())
            except queue.Empty:
                break
        if lines:
            self.log.configure(state="normal")
            for line in lines:
                self.log.insert("end", line + "\n")
            self.log.see("end")
            self.log.configure(state="disabled")
        self.root.after(150, self._drain_log)

    # -------------------------------------------------------------- 状态轮询
    def _status_loop(self) -> None:
        last_ip = 0.0
        while not self.closing:
            try:
                self.comfy_up = local_env.port_open(self.comfy_port, timeout=0.4)
                self.gateway_up = local_env.port_open(self.gateway_port, timeout=0.4)
                if time.time() - last_ip > 30:
                    self.lan_address = local_env.lan_ip()
                    last_ip = time.time()
            except Exception:
                pass
            time.sleep(1.5)

    def _tick(self) -> None:
        self._run_actions()
        self.comfy_dot.configure(fg=SUCCESS if self.comfy_up else SECONDARY)
        self.gateway_dot.configure(fg=SUCCESS if self.gateway_up else SECONDARY)
        self.comfy_state_var.set(
            f"运行中 · http://127.0.0.1:{self.comfy_port}" if self.comfy_up
            else "未运行（点“启动”或“一键启动”）")
        self.gateway_state_var.set(
            f"运行中 · 监听 {self.gateway_port}" if self.gateway_up
            else "未运行（需要已生成的 config.json）")
        self.port_label.configure(text=f":{self.gateway_port}")
        self.address_hint_var.set(
            "网关运行中，把 http://%s:%d 填到手机登录页" % (self.host_var.get().strip() or "?", self.gateway_port)
            if self.gateway_up else "网关未启动：先点“一键启动”，否则手机连不上")
        self.root.after(1200, self._tick)

    # ------------------------------------------------------------ 配置 / 路径
    def _autodetect(self) -> None:
        explicit = self.comfy_root_var.get().strip() or None

        def work() -> None:
            root = local_env.detect_comfy_root(explicit)
            if root is None:
                self._log("未自动找到 ComfyUI，请点“浏览…”手动选择 ComfyUI 目录（含 main.py 的那一层）。")
                return
            self._post(lambda: self._apply_detected(root))

        threading.Thread(target=work, daemon=True).start()

    def _apply_detected(self, root: Path) -> None:
        self.comfy_root_var.set(str(root))
        if not self.workflows_var.get().strip():
            self.workflows_var.set(str(local_env.workflows_dir(root)))
        if not self.uploads_var.get().strip():
            self.uploads_var.set(str(local_env.input_dir(root)))
        python = local_env.comfy_python(root)
        self._log(f"已定位 ComfyUI：{root}")
        self._log(f"  解释器：{python or '未找到（需自己先启动 ComfyUI）'}")
        self._log(f"  工作流：{self.workflows_var.get()}")
        self._log(f"  上传目录：{self.uploads_var.get()}")

    def _browse_comfy(self) -> None:
        selected = filedialog.askdirectory(title="选择 ComfyUI 目录（包含 main.py 的那一层）")
        if not selected:
            return
        root = local_env.resolve_comfy_root(selected)
        if root is None:
            messagebox.showerror("不像是 ComfyUI 目录",
                                 "该目录下没有 main.py 与 comfy 子目录，请选择 ComfyUI 本体的目录。")
            return
        self.comfy_root_var.set(str(root))
        if not self.workflows_var.get().strip():
            self.workflows_var.set(str(local_env.workflows_dir(root)))
        if not self.uploads_var.get().strip():
            self.uploads_var.set(str(local_env.input_dir(root)))

    def _browse_workflows(self) -> None:
        selected = filedialog.askdirectory(title="选择工作流 JSON 根目录")
        if selected:
            self.workflows_var.set(selected)

    def _browse_uploads(self) -> None:
        selected = filedialog.askdirectory(title="选择 ComfyUI 的 input 目录")
        if selected:
            self.uploads_var.set(selected)

    def _ports(self) -> tuple[int, int] | None:
        try:
            gateway_port = int(self.port_var.get().strip())
            comfy_port = int(self.comfy_port_var.get().strip())
        except ValueError:
            messagebox.showerror("端口无效", "端口必须是数字。")
            return None
        self.gateway_port = gateway_port
        self.comfy_port = comfy_port
        return gateway_port, comfy_port

    def _workflow_roots(self, comfy_root: Path) -> list[Path]:
        primary = Path(self.workflows_var.get().strip()) if self.workflows_var.get().strip() \
            else local_env.workflows_dir(comfy_root)
        roots = [primary]
        bundled = local_env.PROJECT_ROOT / "workflows"
        if self.include_bundled_var.get() and bundled.is_dir() and bundled not in roots:
            roots.append(bundled)
        return roots

    def _random_password(self, length: int = 14) -> str:
        alphabet = string.ascii_letters + string.digits
        return "".join(secrets.choice(alphabet) for _ in range(length))

    def _generate_password(self, automatic: bool = False) -> None:
        """生成随机密码：写进 config.json、显示在界面上、复制到剪贴板，并重启网关生效。"""
        config = local_env.read_config()
        if not config:
            self._log("还没有 server/config.json：先确认路径和账号，点“保存配置”。")
            return
        password = self._random_password()
        self.password_var.set(password)
        salt_hex, digest = local_env.hash_password(password)
        config["passwordSalt"] = salt_hex
        config["passwordHash"] = digest
        local_env.write_config(config)
        self.config_data = config
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append(password)
            copied = "，已复制到剪贴板"
        except tk.TclError:
            copied = ""
        self._log(f"{'启动时自动生成' if automatic else '已生成'}新密码：{password}{copied}")
        if local_env.port_open(self.gateway_port) or (self.gateway_service and self.gateway_service.running):
            self._log("网关正在重启以应用新密码…")
            self.root.after(300, self._restart_gateway)

    def _copy_password(self) -> None:
        password = self.password_var.get()
        if not password:
            self._log("当前没有密码，点“换一个”生成。")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(password)
        self._log("已复制密码到剪贴板")

    def _save_config(self, quiet: bool = False) -> bool:
        ports = self._ports()
        if ports is None:
            return False
        gateway_port, comfy_port = ports
        comfy_root = local_env.detect_comfy_root(self.comfy_root_var.get().strip() or None)
        if comfy_root is None:
            messagebox.showerror("找不到 ComfyUI", "请先指定 ComfyUI 目录（含 main.py 的那一层）。")
            return False

        roots = self._workflow_roots(comfy_root)
        uploads = Path(self.uploads_var.get().strip()) if self.uploads_var.get().strip() \
            else local_env.input_dir(comfy_root)
        existing = local_env.read_config() or {}
        password = self.password_var.get()

        if existing and not password:
            # 密码留空 = 沿用旧密码，只更新路径与端口
            config = dict(existing)
            config.update({
                "bindHost": "0.0.0.0",
                "port": gateway_port,
                "username": self.user_var.get().strip() or local_env.DEFAULT_USERNAME,
                "comfyCandidates": [f"http://127.0.0.1:{comfy_port}"],
                "workflowRoots": [str(root) for root in roots],
                "comfyInputDir": str(uploads),
                "database": existing.get("database", "server/data/comfy_mobile.db"),
            })
        else:
            if not password:
                password = self._random_password()
                self.password_var.set(password)
                self._log("未填密码，已自动生成：" + password)
            config = local_env.build_config(
                comfy_root,
                workflow_roots=roots,
                uploads=uploads,
                port=gateway_port,
                username=self.user_var.get().strip() or local_env.DEFAULT_USERNAME,
                password=password,
                comfy_port=comfy_port,
            )
            if existing.get("tokenSecret"):
                config["tokenSecret"] = existing["tokenSecret"]

        local_env.write_config(config)
        self.config_data = config
        self._refresh_config_state()
        self._log(f"配置已保存：{local_env.CONFIG_PATH}")
        for root in config["workflowRoots"]:
            exists = Path(root).is_dir()
            self._log(f"  工作流根目录 {'OK ' if exists else 'MISSING '} {root}")

        if (self.gateway_service and self.gateway_service.running) or self.gateway_up:
            self._log("网关正在重启以加载新配置…")
            self.root.after(200, self._restart_gateway)
        elif not quiet:
            self._log("下一步：点“一键启动”拉起服务。")
        return True

    def _port_pid(self, port: int) -> int | None:
        """查出占用指定端口的进程 PID（不依赖第三方库）。"""
        if os.name != "nt":
            return None
        try:
            output = subprocess.run(["netstat", "-ano", "-p", "tcp"], capture_output=True, text=True,
                                    timeout=20, encoding="mbcs", errors="replace").stdout or ""
        except (OSError, subprocess.SubprocessError):
            return None
        for line in output.splitlines():
            parts = line.split()
            if len(parts) < 5 or parts[0].upper() != "TCP":
                continue
            if not parts[1].endswith(f":{port}"):
                continue
            if parts[3].upper() != "LISTENING":
                continue
            if parts[4].isdigit():
                return int(parts[4])
        return None

    def _restart_gateway(self) -> None:
        """让网关进程重新加载 config.json（即使它不是本窗口启动的）。"""
        if self.gateway_service and self.gateway_service.running:
            self.gateway_service.stop()
            self.gateway_up = False
            self.root.after(500, self._start_gateway)
            return
        self._kill_port(self.gateway_port, "网关")
        self.gateway_up = False
        self._start_gateway()

    # ----------------------------------------------------------------- 服务
    def _ensure_config(self) -> bool:
        if local_env.CONFIG_PATH.is_file():
            return True
        self._log("还没有 config.json，正在按当前界面生成…")
        return self._save_config(quiet=True)

    def _start_comfy(self) -> None:
        if local_env.port_open(self.comfy_port) or (self.comfy_service and self.comfy_service.running):
            self._log("ComfyUI 已在运行。")
            return
        comfy_root = local_env.detect_comfy_root(self.comfy_root_var.get().strip() or None)
        if comfy_root is None:
            messagebox.showerror("找不到 ComfyUI", "请先指定 ComfyUI 目录。")
            return
        try:
            command = local_env.comfy_command(comfy_root, self.comfy_port)
        except FileNotFoundError as error:
            messagebox.showerror("无法启动 ComfyUI", str(error))
            return
        self.comfy_service = local_env.Service(
            "ComfyUI", command, comfy_root,
            on_line=lambda line: self._log("[ComfyUI] " + line) if line.strip() else None,
        )
        self.comfy_service.start()
        self._log(f"启动 ComfyUI：{command[0]}（端口 {self.comfy_port}）")

        def wait() -> None:
            ready = local_env.wait_for(lambda: local_env.port_open(self.comfy_port), timeout=300, interval=2)
            self._log("ComfyUI 已就绪。" if ready else "ComfyUI 启动超时，请查看日志。")

        threading.Thread(target=wait, daemon=True).start()

    def _stop_comfy(self) -> None:
        if self.comfy_service and self.comfy_service.running:
            self.comfy_service.stop()
            self._log("已停止 ComfyUI。")
            return
        if self._kill_port(self.comfy_port, "ComfyUI"):
            self._log("已结束占用 " + str(self.comfy_port) + " 端口的 ComfyUI 进程。")
        else:
            self._log("ComfyUI 没有在运行（" + str(self.comfy_port) + " 端口没有监听）。")

    def _start_gateway(self) -> None:
        # 用实时探测而不是缓存状态，避免“刚杀完旧进程又以为它还在跑”
        if local_env.port_open(self.gateway_port) or (self.gateway_service and self.gateway_service.running):
            self._log("网关已在运行。")
            return
        if not self._ensure_config():
            return
        python = local_env.gateway_python()
        if not local_env.has_gateway_deps(python):
            self._log("网关依赖未安装，点“安装/修复依赖”。")
            messagebox.showwarning("缺少依赖", "请先点“安装/修复依赖”。")
            return
        self.gateway_service = local_env.Service(
            "Gateway", local_env.gateway_command(python), local_env.SERVER_DIR,
            on_line=lambda line: self._log("[Gateway] " + line) if line.strip() else None,
        )
        self.gateway_service.start()
        self._log(f"启动网关：{local_env.SERVER_DIR / 'comfy_mobile_server.py'}（端口 {self.gateway_port}）")

    def _stop_gateway(self) -> None:
        if self.gateway_service and self.gateway_service.running:
            self.gateway_service.stop()
            self._log("已停止网关。")
            return
        if self._kill_port(self.gateway_port, "网关"):
            self._log("已结束占用 " + str(self.gateway_port) + " 端口的网关进程。")
        else:
            self._log("网关没有在运行（" + str(self.gateway_port) + " 端口没有监听）。")

    def _kill_port(self, port: int, label: str) -> bool:
        """结束占用指定端口的进程（即使它不是本窗口启动的）。"""
        pid = self._port_pid(port)
        if not pid:
            return False
        if pid == os.getpid():
            self._log(f"{label} 的端口被本窗口自己占用，跳过。")
            return False
        self._log(f"检测到 {port} 端口被 PID {pid} 占用，正在结束…")
        try:
            subprocess.run(["taskkill", "/PID", str(pid), "/F", "/T"], capture_output=True, timeout=20)
        except (OSError, subprocess.SubprocessError) as error:
            self._log(f"结束进程失败：{error}")
            return False
        return local_env.wait_for(lambda: not local_env.port_open(port), timeout=10, interval=0.4)

    def _start_all(self) -> None:
        if self.busy:
            return
        self.busy = True
        self._log("========== 一键启动 ==========")
        if not self._ensure_config():
            self.busy = False
            return

        def sequence() -> None:
            try:
                if not local_env.port_open(self.comfy_port):
                    self._post(self._start_comfy)
                    if not local_env.wait_for(lambda: local_env.port_open(self.comfy_port), timeout=300, interval=2):
                        self._log("ComfyUI 未能在超时内就绪，仍尝试启动网关。")
                else:
                    self._log(f"ComfyUI 已在 {self.comfy_port} 端口运行，跳过启动。")
                python = local_env.gateway_python()
                if not local_env.has_gateway_deps(python):
                    self._log("网关依赖缺失，请先点“安装/修复依赖”。")
                    return
                self._post(self._start_gateway)
                if local_env.wait_for(lambda: local_env.port_open(self.gateway_port), timeout=40, interval=1):
                    self._log("网关已就绪。")
                    self._log(f"手机登录地址：http://{local_env.lan_ip()}:{self.gateway_port}")
                    self._log("接着可点“自检连接”确认工作流能解析。")
                else:
                    self._log("网关启动超时，请查看上面的日志。")
            finally:
                self.busy = False

        threading.Thread(target=sequence, daemon=True).start()

    def _stop_all(self) -> None:
        self._stop_tunnel()
        self._stop_gateway()
        self._stop_comfy()

    def _check_deps(self) -> None:
        python = local_env.gateway_python()
        self.deps_ok = local_env.has_gateway_deps(python)
        if self.deps_ok:
            self._log(f"网关依赖已就绪（{python}）。")
        else:
            self._log("网关依赖尚未安装：点“安装/修复依赖”即可自动处理。")

    def _install_deps(self) -> None:
        def work() -> None:
            self._log("开始安装网关依赖（首次需要联网，约 1-2 分钟）…")
            ok = local_env.install_requirements(log=self._log)
            self.deps_ok = ok
            if ok:
                self._log("依赖已就位，现在可以“一键启动”。")
            else:
                self._log("依赖安装失败，请检查网络，或手动执行：")
                self._log(r"  server\.venv\Scripts\python.exe -m pip install -r server\requirements.txt")

        threading.Thread(target=work, daemon=True).start()

    def _open_comfy(self) -> None:
        webbrowser.open(f"http://127.0.0.1:{self.comfy_port}")

    def _firewall_rule_state(self, rule: str) -> tuple[str, str]:
        """返回 (missing|exists, 说明)。netsh 不需要管理员权限，输出也是英文/中文固定的。"""
        try:
            probe = subprocess.run(["netsh", "advfirewall", "firewall", "show", "rule", f"name={rule}"],
                                   capture_output=True, text=True, timeout=20,
                                   encoding="mbcs", errors="replace")
        except (OSError, subprocess.SubprocessError) as error:
            return "missing", f"查询失败：{error}"
        output = probe.stdout or ""
        if not any(marker in output for marker in ("Rule Name:", "规则名称:")):
            return "missing", "未找到规则"
        profiles = ""
        for line in output.splitlines():
            if line.strip().lower().startswith(("profiles:", "配置文件:")):
                profiles = line.split(":", 1)[1].strip()
                break
        return "exists", f"已存在（Profiles={profiles or '未知'}）"

    def _allow_firewall(self) -> None:
        """放行网关端口的入站连接（手机走局域网/虚拟网卡时经常被 Windows 防火墙拦住）。"""
        if os.name != "nt":
            self._log("非 Windows 系统，忽略防火墙设置。")
            return
        rule = f"ComfyMobile Gateway {self.gateway_port}"
        state, detail = self._firewall_rule_state(rule)
        self._log(f"防火墙规则检查：{rule} → {detail}")

        if state == "exists" and ("any" in detail.lower() or "所有" in detail or "全部" in detail):
            self._log("该规则已覆盖所有网络配置，无需重复操作。")
            return

        if state == "exists":
            inner = f'netsh advfirewall firewall set rule name="{rule}" new profile=any enable=yes'
            action = "放宽为全部网络配置"
        else:
            inner = (f'netsh advfirewall firewall add rule name="{rule}" dir=in action=allow '
                     f'protocol=TCP localport={self.gateway_port} profile=any')
            action = "新增入站放行"

        try:
            import ctypes
            code = ctypes.windll.shell32.ShellExecuteW(  # type: ignore[attr-defined]
                None, "runas", "cmd.exe", f'/c {inner} & timeout /t 2 >nul', None, 1)
        except Exception as error:  # noqa: BLE001
            self._log(f"请求管理员权限失败：{error}")
            code = 0
        if code > 32:
            self._log(f"已请求管理员权限（{action}），请在 UAC 弹窗中确认。")
            self._log(f"  规则：TCP {self.gateway_port} 入站放行，网络范围=全部")
        else:
            self._log("未能提升权限（可能点了“否”）。可手动以管理员身份执行：")
            self._log("  " + inner)

    def _open_output(self) -> None:
        comfy_root = local_env.detect_comfy_root(self.comfy_root_var.get().strip() or None)
        if comfy_root and (comfy_root / "output").is_dir():
            os.startfile(str(comfy_root / "output"))  # type: ignore[attr-defined]
        else:
            self._log("没找到 ComfyUI 的 output 目录。")

    # ----------------------------------------------------------------- 连接
    def _copy_address(self) -> None:
        address = f"http://{self.host_var.get().strip()}:{self.gateway_port}"
        self.root.clipboard_clear()
        self.root.clipboard_append(address)
        self._remember_host()
        self._log(f"已复制：{address}")

    def _remember_host(self) -> None:
        host = self.host_var.get().strip()
        if host:
            data = local_env.read_ui()
            data["phoneHost"] = host
            local_env.write_ui(data)

    def _refresh_addresses(self) -> None:
        hosts = local_env.ipv4_addresses()
        self.host_options = hosts
        self.host_box.configure(values=hosts)
        self._log("本机 IPv4：" + ("、".join(hosts) if hosts else "未检测到"))
        if hosts and self.host_var.get().strip() not in hosts:
            self.host_var.set(hosts[0])
            self._log(f"地址已切换为 {hosts[0]}")
        else:
            self._log("提示：接入 WireGuard / Tailscale 后，对应的虚拟网卡地址也会出现在下拉框里。")

    def _open_remote_doc(self) -> None:
        doc = local_env.PROJECT_ROOT / "docs" / "remote-access.md"
        if doc.is_file():
            os.startfile(str(doc))  # type: ignore[attr-defined]
        else:
            self._log(f"说明文档不存在：{doc}")

    def _adb_reverse(self) -> None:
        adb = adb_executable()
        if not adb:
            self._log("未找到 adb。可以先在 Android Studio 里跑一次应用，或把 platform-tools 加入 PATH。")
            return
        try:
            result = subprocess.run(
                [adb, "reverse", f"tcp:{self.gateway_port}", f"tcp:{self.gateway_port}"],
                capture_output=True, text=True, timeout=20)
        except (OSError, subprocess.SubprocessError) as error:
            self._log(f"adb reverse 失败：{error}")
            return
        if result.returncode == 0:
            # USB 转发成功后，手机端应当用 127.0.0.1 访问本机网关
            if "127.0.0.1" not in self.host_options:
                self.host_options.insert(0, "127.0.0.1")
                self.host_box.configure(values=self.host_options)
            self.host_var.set("127.0.0.1")
            self._remember_host()
            self._log(f"adb reverse 已建立，地址已切换为 127.0.0.1")
            self._log(f"  手机登录页填：http://127.0.0.1:{self.gateway_port}")
            self._log("  注意：拔掉数据线或重启 adb 后需要再点一次本按钮。")
        else:
            self._log("adb reverse 失败：" + (result.stderr or result.stdout).strip())
            self._log("请确认手机已用数据线连接并开启 USB 调试。")

    # ------------------------------------------------------- 外网隧道（Cloudflare）
    def _cloudflared(self) -> Path:
        return local_env.PROJECT_ROOT / "tools" / "cloudflared.exe"

    def _start_tunnel(self) -> None:
        if self.tunnel_service and self.tunnel_service.running:
            self._log("外网隧道已经在运行。")
            return
        if not self.gateway_up:
            self._log("先启动网关，再开外网隧道。")
            return
        exe = self._cloudflared()
        if not exe.is_file():
            self._log(f"找不到 {exe}")
            self._log("下载：https://github.com/cloudflare/cloudflared/releases/latest → 重命名为 cloudflared.exe 放进 tools 目录")
            messagebox.showwarning("缺少 cloudflared", f"没有找到：\n{exe}\n\n请先下载 cloudflared.exe 放进 tools 目录。")
            return
        self.tunnel_var.set("正在申请地址…")
        self.tunnel_url = ""
        self.tunnel_service = local_env.Service(
            "Cloudflared",
            [str(exe), "tunnel", "--url", f"http://127.0.0.1:{self.gateway_port}",
             "--no-autoupdate", "--protocol", "http2"],
            local_env.PROJECT_ROOT,
            on_line=self._on_tunnel_line,
        )
        self.tunnel_service.start()
        self._log("正在向 Cloudflare 申请临时隧道（约 10-20 秒）…")

    def _on_tunnel_line(self, line: str) -> None:
        # 注意：本方法在 cloudflared 的读取线程里执行，只能做纯字符串处理 + 入队，
        # 不能直接碰 tkinter 控件。
        if not line.strip():
            return
        self._log("[Tunnel] " + line)
        match = re.search(r"https://[A-Za-z0-9-]+\.trycloudflare\.com", line)
        if match and not self.tunnel_url:
            self.tunnel_url = match.group(0)
            self._post(lambda: self._tunnel_ready(self.tunnel_url))

    def _tunnel_ready(self, url: str) -> None:
        self.tunnel_var.set(url)
        self.root.clipboard_clear()
        self.root.clipboard_append(url)
        self._log("========== 外网地址已就绪（已复制到剪贴板） ==========")
        self._log("  手机登录页填：" + url)
        self._log("  账号密码与本窗口一致；这个地址每次启动都会变，关窗口即失效。")

    def _copy_tunnel(self) -> None:
        url = self.tunnel_var.get().strip()
        if not url.startswith("https://"):
            self._log("还没有可用的外网地址，先点「启动外网隧道」。")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(url)
        self._log("已复制外网地址：" + url)

    def _stop_tunnel(self) -> None:
        if self.tunnel_service and self.tunnel_service.running:
            self.tunnel_service.stop()
            self._log("已停止外网隧道。")
        else:
            self._log("外网隧道没有在运行。")
        self.tunnel_url = ""
        self.tunnel_var.set("")

    def _http(self, method: str, path: str, body: dict | None = None, token: str = "") -> dict:
        url = f"http://127.0.0.1:{self.gateway_port}{path}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json; charset=utf-8")
        if token:
            request.add_header("Authorization", f"Bearer {token}")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))

    def _login(self, username: str, password: str) -> str:
        if not password:
            raise RuntimeError("请在“密码”里填入网关登录密码后再自检"
                               "（密码留空表示沿用 config.json 中的旧密码，但自检需要明文）。")
        session = self._http("POST", "/api/login", {"username": username, "password": password})
        return session["token"]

    def _self_test(self) -> None:
        username = self.user_var.get().strip()
        password = self.password_var.get()

        def work() -> None:
            self._log("========== 自检 ==========")
            try:
                if not self.gateway_up:
                    self._log("网关没在运行，先点“一键启动”。")
                    return
                token = self._login(username, password)
                self._log("登录成功。")
                workflows = self._http("GET", "/api/workflows", token=token).get("workflows", [])
                self._log(f"工作流 {len(workflows)} 个。")
                if not workflows:
                    self._log("没有可用工作流：请检查“工作流目录”下是否有 ComfyUI 保存的 *.json。")
                    return
                target = next((item for item in workflows if "ComfyMobile" in item.get("relativePath", "")),
                              workflows[0])
                query = urllib.parse.urlencode({"comfyUrl": f"http://127.0.0.1:{self.comfy_port}"})
                detail = self._http("GET", f"/api/workflows/{target['id']}?{query}", token=token)
                inputs = detail.get("inputs", [])
                self._log(f"解析工作流“{target['name']}”：{len(inputs)} 个可控输入。")
                for item in inputs[:8]:
                    self._log(f"  · {item['nodeTitle']} / {item['name']} = {str(item.get('value'))[:40]}")
                if len(inputs) > 8:
                    self._log(f"  · …… 其余 {len(inputs) - 8} 项")
                if inputs:
                    self._log("自检通过：手机端应能看到同样的输入项。")
                else:
                    self._log("输入为 0：通常是工作流里的节点类型在本机 ComfyUI 中不存在（缺自定义节点）。")
            except urllib.error.HTTPError as error:
                self._log(f"自检失败（HTTP {error.code}）：{error.read().decode('utf-8', 'replace')}")
            except Exception as error:  # noqa: BLE001
                self._log(f"自检失败：{error}")

        threading.Thread(target=work, daemon=True).start()

    def _run_test_image(self) -> None:
        username = self.user_var.get().strip()
        password = self.password_var.get()

        def work() -> None:
            self._log("========== 生成测试图 ==========")
            try:
                if not self.gateway_up:
                    self._log("网关没在运行，先点“一键启动”。")
                    return
                token = self._login(username, password)
                workflows = self._http("GET", "/api/workflows", token=token).get("workflows", [])
                target = next((item for item in workflows if "ComfyMobile" in item.get("relativePath", "")), None)
                if target is None:
                    self._log("没找到 ComfyMobile_本地快速测试.json，可勾选“同时挂载项目内置测试工作流”后重新保存配置。")
                    return
                body = {"comfyUrl": f"http://127.0.0.1:{self.comfy_port}", "overrides": {}}
                job = self._http("POST", f"/api/workflows/{target['id']}/run", body, token)["job"]
                self._log(f"已提交任务 {job['id']}，等待出图…")
                current = job
                deadline = time.time() + 180
                while time.time() < deadline:
                    time.sleep(2)
                    current = self._http("GET", f"/api/jobs/{job['id']}", token=token)["job"]
                    if current["status"] in {"COMPLETED", "FAILED"}:
                        break
                if current["status"] != "COMPLETED":
                    self._log(f"任务状态 {current['status']}：{current.get('error') or '超时'}")
                    return
                artifacts = current.get("artifacts", [])
                self._log(f"完成，产物 {len(artifacts)} 个。")
                if artifacts:
                    url = f"http://127.0.0.1:{self.gateway_port}{artifacts[0]['url']}"
                    request = urllib.request.Request(url)
                    request.add_header("Authorization", f"Bearer {token}")
                    local_file = local_env.SERVER_DIR / "data" / f"test_{job['id'][:8]}.png"
                    local_file.parent.mkdir(parents=True, exist_ok=True)
                    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                    with opener.open(request, timeout=120) as response, local_file.open("wb") as output:
                        output.write(response.read())
                    self._log(f"已保存：{local_file}")
                    os.startfile(str(local_file))  # type: ignore[attr-defined]
            except urllib.error.HTTPError as error:
                self._log(f"测试失败（HTTP {error.code}）：{error.read().decode('utf-8', 'replace')}")
            except Exception as error:  # noqa: BLE001
                self._log(f"测试失败：{error}")

        threading.Thread(target=work, daemon=True).start()

    # ----------------------------------------------------------------- 退出
    def _on_close(self) -> None:
        self._remember_host()
        running = [name for name, service in (("ComfyUI", self.comfy_service), ("网关", self.gateway_service),
                                             ("外网隧道", self.tunnel_service))
                   if service and service.running]
        if running:
            stop = messagebox.askyesno(
                "退出", f"{'、'.join(running)} 正在运行。\n是否停止它们后退出？\n选择“否”则保持后台运行。")
            if stop:
                self._stop_all()
                time.sleep(0.5)
        self.closing = True
        self.root.destroy()


def main() -> None:
    # 不调用 SetProcessDpiAwareness：保持 Tk 的 DPI 虚拟化，
    # 缩放屏下字体与几何才一致，窗口尺寸由 _fit_to_screen 自适应。
    root = tk.Tk()
    try:
        ttk.Style().theme_use("vista")
    except tk.TclError:
        pass
    LocalWindow(root)
    root.mainloop()


if __name__ == "__main__":
    main()

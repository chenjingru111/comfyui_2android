"""Local (Windows) helpers for running the Comfy Mobile gateway against a
desktop ComfyUI install.

Only the standard library is used here so this module can be imported by the
configuration CLI and by the GUI without installing the gateway requirements.
"""

from __future__ import annotations

import hashlib
import json
import os
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Iterable

SERVER_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SERVER_DIR.parent
CONFIG_PATH = SERVER_DIR / "config.json"
EXAMPLE_CONFIG_PATH = SERVER_DIR / "config.example.json"
VENV_DIR = SERVER_DIR / ".venv"
REQUIREMENTS_PATH = SERVER_DIR / "requirements.txt"
DB_DIR = SERVER_DIR / "data"

DEFAULT_GATEWAY_PORT = 8765
DEFAULT_COMFY_PORT = 8188
DEFAULT_USERNAME = "admin"
DEFAULT_PASSWORD = "admin1234"

PIP_INDEXES = (
    "",
    "https://pypi.tuna.tsinghua.edu.cn/simple",
    "https://mirrors.aliyun.com/pypi/simple",
)


# --------------------------------------------------------------------------- #
# ComfyUI discovery
# --------------------------------------------------------------------------- #

def _looks_like_comfyui(path: Path) -> bool:
    return (path / "main.py").is_file() and (path / "comfy").is_dir()


def resolve_comfy_root(candidate: Path | str) -> Path | None:
    """Accept either a ComfyUI directory or a parent that contains one."""
    path = Path(candidate).expanduser()
    if not path.exists():
        return None
    for option in (path, path / "ComfyUI"):
        try:
            resolved = option.resolve()
        except OSError:
            continue
        if _looks_like_comfyui(resolved):
            return resolved
    return None


def candidate_roots() -> Iterable[Path]:
    env_value = os.environ.get("COMFYUI_ROOT") or os.environ.get("COMFYUI_PATH")
    if env_value:
        yield Path(env_value)
    yield Path(r"E:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2")
    yield Path(r"E:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI")
    yield PROJECT_ROOT / "ComfyUI"
    yield Path.home() / "ComfyUI"
    for drive in "CDEFG":
        base = Path(f"{drive}:\\")
        if not base.exists():
            continue
        try:
            for child in base.iterdir():
                if not child.is_dir():
                    continue
                lowered = child.name.lower()
                if "comfyui" in lowered or lowered in {"comfy", "sdxl"}:
                    yield child
                    yield child / "ComfyUI"
        except OSError:
            continue


def detect_comfy_root(explicit: str | Path | None = None) -> Path | None:
    if explicit:
        return resolve_comfy_root(explicit)
    for candidate in candidate_roots():
        resolved = resolve_comfy_root(candidate)
        if resolved is not None:
            return resolved
    return None


def workflows_dir(comfy_root: Path) -> Path:
    return comfy_root / "user" / "default" / "workflows"


def input_dir(comfy_root: Path) -> Path:
    return comfy_root / "input"


def output_dir(comfy_root: Path) -> Path:
    return comfy_root / "output"


def comfy_python(comfy_root: Path) -> Path | None:
    """Locate the interpreter that can run this ComfyUI install."""
    options = (
        comfy_root.parent / "python" / "python.exe",
        comfy_root.parent / "python_embeded" / "python.exe",
        comfy_root / "python_embeded" / "python.exe",
        comfy_root / "venv" / "Scripts" / "python.exe",
        comfy_root / ".venv" / "Scripts" / "python.exe",
    )
    for option in options:
        if option.is_file():
            return option
    return None


# --------------------------------------------------------------------------- #
# Configuration
# --------------------------------------------------------------------------- #

def hash_password(password: str, salt_hex: str | None = None) -> tuple[str, str]:
    salt = bytes.fromhex(salt_hex) if salt_hex else secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 300_000).hex()
    return salt.hex(), digest


def build_config(
    comfy_root: Path,
    *,
    workflows: Path | None = None,
    workflow_roots: list[Path] | None = None,
    uploads: Path | None = None,
    port: int = DEFAULT_GATEWAY_PORT,
    bind_host: str = "0.0.0.0",
    username: str = DEFAULT_USERNAME,
    password: str = DEFAULT_PASSWORD,
    comfy_port: int = DEFAULT_COMFY_PORT,
) -> dict:
    salt_hex, password_hash = hash_password(password)
    roots = list(workflow_roots or [])
    if workflows and workflows not in roots:
        roots.insert(0, workflows)
    if not roots:
        roots = [workflows_dir(comfy_root)]
    return {
        "bindHost": bind_host,
        "port": int(port),
        "username": username,
        "passwordSalt": salt_hex,
        "passwordHash": password_hash,
        "tokenSecret": secrets.token_hex(32),
        "comfyCandidates": [f"http://127.0.0.1:{int(comfy_port)}"],
        "workflowRoots": [str(root) for root in roots],
        "comfyInputDir": str(uploads or input_dir(comfy_root)),
        "comfyOutputDir": str(output_dir(comfy_root)),
        "database": "server/data/comfy_mobile.db",
    }


def write_config(config: dict) -> Path:
    CONFIG_PATH.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return CONFIG_PATH


def read_config() -> dict | None:
    if not CONFIG_PATH.is_file():
        return None
    try:
        return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


# --------------------------------------------------------------------------- #
# Python environment for the gateway
# --------------------------------------------------------------------------- #

def gateway_python() -> Path:
    """Prefer the dedicated virtualenv, fall back to the running interpreter."""
    candidate = VENV_DIR / "Scripts" / "python.exe"
    if candidate.is_file():
        return candidate
    return Path(sys.executable)


def has_gateway_deps(python: Path) -> bool:
    code = "import fastapi, httpx, uvicorn, multipart"
    try:
        result = subprocess.run(
            [str(python), "-c", code],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=60,
        )
        return result.returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def create_venv(log=print) -> Path:
    if not (VENV_DIR / "Scripts" / "python.exe").is_file():
        log(f"创建虚拟环境：{VENV_DIR}")
        subprocess.run([sys.executable, "-m", "venv", str(VENV_DIR)], check=True)
    return VENV_DIR / "Scripts" / "python.exe"


def install_requirements(log=print) -> bool:
    python = create_venv(log)
    for index in PIP_INDEXES:
        command = [str(python), "-m", "pip", "install", "--disable-pip-version-check", "-r", str(REQUIREMENTS_PATH)]
        if index:
            command += ["-i", index]
        log(f"安装网关依赖：{'镜像 ' + index if index else '默认 PyPI'}")
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace")
        lines: list[str] = []
        assert process.stdout is not None
        for line in process.stdout:
            text = line.rstrip()
            if not text:
                continue
            lines.append(text)
            if len(lines) % 15 == 0:
                log("  " + text)
        if process.wait() == 0:
            log("依赖安装完成。")
            return True
        log("本次安装失败，输出末尾：")
        for text in lines[-12:]:
            log("  " + text)
    return False


# --------------------------------------------------------------------------- #
# Networking
# --------------------------------------------------------------------------- #

def lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.settimeout(0.4)
            probe.connect(("10.255.255.255", 1))
            address = probe.getsockname()[0]
            if address and not address.startswith("127."):
                return address
    except OSError:
        pass
    try:
        for address in socket.gethostbyname_ex(socket.gethostname())[2]:
            if not address.startswith("127."):
                return address
    except OSError:
        pass
    return "127.0.0.1"


def ipv4_addresses() -> list[str]:
    """所有可能的本机 IPv4（局域网 / WireGuard / Tailscale 等），首选地址排最前。"""
    result: list[str] = []
    primary = lan_ip()
    if primary != "127.0.0.1":
        result.append(primary)
    try:
        for address in socket.gethostbyname_ex(socket.gethostname())[2]:
            if address not in result and not address.startswith("127."):
                result.append(address)
    except OSError:
        pass
    for address in _ipconfig_addresses():
        if address not in result:
            result.append(address)
    return result


def _ipconfig_addresses() -> list[str]:
    """用 ipconfig 兜底枚举（不依赖第三方库），只取 IPv4 字面量。"""
    if os.name != "nt":
        return []
    try:
        output = subprocess.run(["ipconfig"], capture_output=True, timeout=10,
                                encoding="mbcs", errors="replace").stdout or ""
    except (OSError, subprocess.SubprocessError):
        return []
    found: list[str] = []
    for line in output.splitlines():
        marker = line.find("IPv4")
        if marker < 0:
            continue
        for token in line.replace(":", " ").split():
            if token.count(".") == 3 and all(part.isdigit() and int(part) < 256 for part in token.split(".")):
                if not token.startswith("127.") and token not in found:
                    found.append(token)
    return found


def read_ui() -> dict:
    path = DB_DIR / "ui.json"
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def write_ui(data: dict) -> None:
    path = DB_DIR / "ui.json"
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except OSError:
        pass


def http_json(url: str, timeout: float = 3.0) -> dict | None:
    try:
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, ValueError, json.JSONDecodeError):
        return None


def port_open(port: int, host: str = "127.0.0.1", timeout: float = 0.6) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def comfy_stats(port: int = DEFAULT_COMFY_PORT) -> dict | None:
    return http_json(f"http://127.0.0.1:{port}/system_stats", timeout=3.0)


def gateway_health(port: int = DEFAULT_GATEWAY_PORT) -> dict | None:
    return http_json(f"http://127.0.0.1:{port}/api/health", timeout=3.0)


def wait_for(predicate, timeout: float = 90.0, interval: float = 1.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


# --------------------------------------------------------------------------- #
# Child processes
# --------------------------------------------------------------------------- #

class Service:
    """A long-lived child process whose output is streamed to a callback."""

    def __init__(self, name: str, command: list[str], cwd: Path, on_line=None):
        self.name = name
        self.command = command
        self.cwd = cwd
        self.on_line = on_line
        self.process: subprocess.Popen | None = None
        self._reader: threading.Thread | None = None

    @property
    def running(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def start(self) -> None:
        if self.running:
            return
        creation = 0
        if os.name == "nt":
            creation = subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
        self.process = subprocess.Popen(
            self.command,
            cwd=str(self.cwd),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            creationflags=creation,
        )
        self._reader = threading.Thread(target=self._pump, daemon=True)
        self._reader.start()

    def _pump(self) -> None:
        process = self.process
        if process is None or process.stdout is None:
            return
        for line in process.stdout:
            if self.on_line:
                self.on_line(line.rstrip())
        if self.on_line:
            self.on_line(f"[{self.name}] 进程已退出（{process.poll()}）")

    def stop(self, timeout: float = 8.0) -> None:
        process = self.process
        if process is None or process.poll() is not None:
            self.process = None
            return
        process.terminate()
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            try:
                process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                pass
        self.process = None


def comfy_command(comfy_root: Path, port: int = DEFAULT_COMFY_PORT, extra: list[str] | None = None) -> list[str]:
    python = comfy_python(comfy_root)
    if python is None:
        raise FileNotFoundError("未找到 ComfyUI 的 Python 解释器，请手动启动 ComfyUI")
    command = [
        str(python),
        "-s",
        "main.py",
        "--windows-standalone-build",
        "--listen",
        "127.0.0.1",
        "--port",
        str(int(port)),
    ]
    return command + list(extra or [])


def gateway_command(python: Path | None = None) -> list[str]:
    return [str(python or gateway_python()), str(SERVER_DIR / "comfy_mobile_server.py")]

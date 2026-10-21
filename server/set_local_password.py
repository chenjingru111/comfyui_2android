"""修改本机网关的登录密码（不交互，便于脚本/我这边直接调用）。

用法:
    python server/set_local_password.py            # 打印当前账号 + 生成一个随机密码
    python server/set_local_password.py 我的密码     # 指定新密码
"""

from __future__ import annotations

import hashlib
import json
import secrets
import string
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = BASE_DIR / "config.json"


def random_password(length: int = 14) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def main() -> int:
    if not CONFIG_PATH.is_file():
        print("缺少 server/config.json，请先运行 python server/configure_local.py")
        return 1
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    password = sys.argv[1].strip() if len(sys.argv) > 1 else random_password()

    salt = secrets.token_bytes(16)
    config["passwordSalt"] = salt.hex()
    config["passwordHash"] = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 300_000).hex()
    CONFIG_PATH.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print("账号:", config["username"])
    print("密码:", password)
    print("端口:", config["port"])
    print("已写入:", CONFIG_PATH)
    print("记得重启网关（窗口里点“停止”再“启动”，或重新“一键启动”）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

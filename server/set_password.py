import getpass
import hashlib
import json
import secrets
from pathlib import Path


config_path = Path(__file__).with_name("config.json")
config = json.loads(config_path.read_text(encoding="utf-8"))
password = getpass.getpass("新密码: ")
confirm = getpass.getpass("再次输入: ")
if not password or password != confirm:
    raise SystemExit("两次密码不一致")
salt = secrets.token_bytes(16)
config["passwordSalt"] = salt.hex()
config["passwordHash"] = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 300_000).hex()
config["tokenSecret"] = secrets.token_hex(32)
config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
config_path.chmod(0o600)
print("密码已更新，请重启 Comfy Mobile Gateway")

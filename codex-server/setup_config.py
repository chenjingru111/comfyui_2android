import getpass
import hashlib
import json
import secrets
import shutil
from pathlib import Path


config_path = Path(__file__).with_name("config.json")
codex_binary = shutil.which("codex")
if not codex_binary:
    raise SystemExit("codex executable was not found")
password = getpass.getpass("Codex Mobile password: ")
confirm = getpass.getpass("Repeat password: ")
if not password or password != confirm:
    raise SystemExit("Passwords do not match")
salt = secrets.token_bytes(16)
config = {
    "bindHost": "192.168.5.160",
    "port": 8770,
    "username": "admin",
    "passwordSalt": salt.hex(),
    "passwordHash": hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 300_000).hex(),
    "tokenSecret": secrets.token_hex(32),
    "codexBinary": codex_binary,
}
config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
config_path.chmod(0o600)
print(f"Created {config_path}")

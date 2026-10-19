import argparse
import getpass
import hashlib
import json
import os
import secrets
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local Comfy Mobile gateway configuration")
    parser.add_argument("--comfyui-path", required=True)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("config.json"))
    args = parser.parse_args()

    comfyui = Path(args.comfyui_path).expanduser().resolve()
    if not (comfyui / "main.py").is_file():
        raise SystemExit(f"ComfyUI path is invalid (main.py not found): {comfyui}")

    password = os.environ.get("COMFY_MOBILE_PASSWORD") or getpass.getpass("Gateway password: ")
    if not password:
        raise SystemExit("Gateway password cannot be empty")

    salt = secrets.token_bytes(16)
    data_root = comfyui.parent.parent if comfyui.parent.name.lower() == "packages" else comfyui.parent
    workflow_roots = [data_root / "Workflows", comfyui / "user" / "default" / "workflows"]
    config = {
        "bindHost": "0.0.0.0",
        "port": args.port,
        "username": args.username,
        "passwordSalt": salt.hex(),
        "passwordHash": hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 300_000).hex(),
        "tokenSecret": secrets.token_hex(32),
        "comfyCandidates": [f"http://127.0.0.1:{port}" for port in range(8188, 8192)],
        "workflowRoots": [str(path) for path in workflow_roots],
        "comfyInputDir": str(comfyui / "input"),
        "database": str(Path(__file__).with_name("data") / "comfy_mobile.db"),
        "comfyUIPath": str(comfyui),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Configuration created: {args.output.resolve()}")


if __name__ == "__main__":
    main()

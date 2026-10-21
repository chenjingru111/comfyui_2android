"""Create server/config.json for a local desktop ComfyUI install.

Examples:
    python server/configure_local.py
    python server/configure_local.py --comfy-root "E:\\ComfyUI-aki-v3.2\\ComfyUI-aki-v3.2"
    python server/configure_local.py --password 123456 --port 8765 --force
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import local_env  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="为本地 ComfyUI 生成 Comfy Mobile 网关配置")
    parser.add_argument("--comfy-root", default=None, help="ComfyUI 目录（或包含 ComfyUI 目录的上级目录）")
    parser.add_argument("--workflows", action="append", default=None,
                        help="工作流 JSON 根目录（可多次指定），默认 ComfyUI/user/default/workflows")
    parser.add_argument("--uploads", default=None, help="ComfyUI input 目录，默认 ComfyUI/input")
    parser.add_argument("--port", type=int, default=local_env.DEFAULT_GATEWAY_PORT, help="网关端口")
    parser.add_argument("--bind", default="0.0.0.0", help="网关监听地址")
    parser.add_argument("--username", default=local_env.DEFAULT_USERNAME)
    parser.add_argument("--password", default=local_env.DEFAULT_PASSWORD)
    parser.add_argument("--comfy-port", type=int, default=local_env.DEFAULT_COMFY_PORT)
    parser.add_argument("--force", action="store_true", help="覆盖已存在的 config.json")
    args = parser.parse_args(argv)

    if local_env.CONFIG_PATH.is_file() and not args.force:
        print(f"配置已存在：{local_env.CONFIG_PATH}\n如需覆盖请加 --force")
        return 1

    comfy_root = local_env.detect_comfy_root(args.comfy_root)
    if comfy_root is None:
        print("未找到 ComfyUI 目录，请用 --comfy-root 指定，例如：")
        print(r'  --comfy-root "E:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2"')
        return 2

    roots: list[Path] = []
    for value in (args.workflows or []):
        roots.append(Path(value).expanduser())
    if not roots:
        roots.append(local_env.workflows_dir(comfy_root))
    bundled = local_env.PROJECT_ROOT / "workflows"
    if bundled.is_dir() and bundled not in roots:
        roots.append(bundled)

    uploads = Path(args.uploads).expanduser() if args.uploads else local_env.input_dir(comfy_root)
    for root in roots:
        if not root.is_dir():
            print(f"警告：工作流目录不存在：{root}")
    if not uploads.is_dir():
        print(f"警告：ComfyUI input 目录不存在：{uploads}")

    config = local_env.build_config(
        comfy_root,
        workflow_roots=roots,
        uploads=uploads,
        port=args.port,
        bind_host=args.bind,
        username=args.username,
        password=args.password,
        comfy_port=args.comfy_port,
    )
    path = local_env.write_config(config)

    print("已写入：" + str(path))
    print(json.dumps({key: config[key] for key in ("port", "bindHost", "username", "comfyCandidates", "workflowRoots", "comfyInputDir")},
                     ensure_ascii=False, indent=2))
    print(f"\n手机端登录：http://{local_env.lan_ip()}:{config['port']}  账号 {config['username']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

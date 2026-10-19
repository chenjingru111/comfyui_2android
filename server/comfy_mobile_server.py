from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import mimetypes
import os
import secrets
import sqlite3
import time
import urllib.parse
import uuid
from pathlib import Path
from typing import Any

import httpx
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field


BASE_DIR = Path(__file__).resolve().parent
CONFIG = json.loads((BASE_DIR / "config.json").read_text(encoding="utf-8"))
DB_PATH = Path(CONFIG["database"])
DB_PATH.parent.mkdir(parents=True, exist_ok=True)
UPLOAD_DIR = Path(CONFIG["comfyInputDir"]) / "mobile"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Comfy Mobile Gateway", version="0.1.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
object_info_cache: dict[str, tuple[float, dict[str, Any]]] = {}


class LoginRequest(BaseModel):
    username: str
    password: str


class RunRequest(BaseModel):
    comfyUrl: str = ""
    overrides: dict[str, Any] = Field(default_factory=dict)


def connect_db() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def init_db() -> None:
    with connect_db() as db:
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS jobs (
                id TEXT PRIMARY KEY,
                prompt_id TEXT NOT NULL,
                workflow_id TEXT NOT NULL,
                workflow_name TEXT NOT NULL,
                comfy_url TEXT NOT NULL,
                status TEXT NOT NULL,
                error TEXT NOT NULL DEFAULT '',
                artifacts_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """
        )


def password_valid(password: str) -> bool:
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode(), bytes.fromhex(CONFIG["passwordSalt"]), 300_000
    ).hex()
    return hmac.compare_digest(digest, CONFIG["passwordHash"])


def issue_token(username: str) -> str:
    expires = int(time.time()) + 7 * 24 * 3600
    payload = f"{username}:{expires}:{secrets.token_hex(12)}".encode()
    encoded = base64.urlsafe_b64encode(payload).decode().rstrip("=")
    signature = hmac.new(bytes.fromhex(CONFIG["tokenSecret"]), encoded.encode(), hashlib.sha256).hexdigest()
    return f"{encoded}.{signature}"


def require_auth(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="请先登录")
    token = authorization[7:]
    try:
        encoded, signature = token.split(".", 1)
        expected = hmac.new(bytes.fromhex(CONFIG["tokenSecret"]), encoded.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            raise ValueError
        padding = "=" * (-len(encoded) % 4)
        username, expires, _ = base64.urlsafe_b64decode(encoded + padding).decode().split(":", 2)
        if username != CONFIG["username"] or int(expires) < time.time():
            raise ValueError
        return username
    except ValueError:
        raise HTTPException(status_code=401, detail="登录已失效，请重新登录")


def normalize_comfy_url(url: str) -> str:
    return url.rstrip("/")


async def discover_comfy() -> list[dict[str, Any]]:
    async def probe(url: str) -> dict[str, Any]:
        url = normalize_comfy_url(url)
        try:
            async with httpx.AsyncClient(timeout=2.0, trust_env=False) as client:
                response = await client.get(f"{url}/system_stats")
                response.raise_for_status()
                data = response.json()
                device = (data.get("devices") or [{}])[0]
                label = device.get("name") or data.get("system", {}).get("os") or "ComfyUI"
                return {"url": url, "label": label, "online": True}
        except Exception:
            return {"url": url, "label": "ComfyUI", "online": False}

    return await asyncio.gather(*(probe(url) for url in CONFIG["comfyCandidates"]))


async def active_comfy(requested: str = "") -> str:
    if requested:
        url = normalize_comfy_url(requested)
        try:
            async with httpx.AsyncClient(timeout=3.0, trust_env=False) as client:
                response = await client.get(f"{url}/system_stats")
                response.raise_for_status()
            return url
        except Exception as error:
            raise HTTPException(status_code=503, detail=f"无法连接 ComfyUI：{error}")
    instances = await discover_comfy()
    for instance in instances:
        if instance["online"]:
            return instance["url"]
    raise HTTPException(status_code=503, detail="没有发现已启动的 ComfyUI")


def workflow_files() -> list[tuple[str, Path, Path]]:
    result: list[tuple[str, Path, Path]] = []
    seen: set[Path] = set()
    for root_value in CONFIG["workflowRoots"]:
        root = Path(root_value)
        if not root.is_dir():
            continue
        for path in root.rglob("*.json"):
            resolved = path.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            workflow_id = hashlib.sha256(str(resolved).encode()).hexdigest()[:20]
            result.append((workflow_id, resolved, root.resolve()))
    return sorted(result, key=lambda item: item[1].name.lower())


def workflow_entry(workflow_id: str) -> tuple[Path, Path]:
    for current_id, path, root in workflow_files():
        if current_id == workflow_id:
            return path, root
    raise HTTPException(status_code=404, detail="工作流不存在")


def load_workflow(workflow_id: str) -> tuple[dict[str, Any], Path, Path]:
    path, root = workflow_entry(workflow_id)
    try:
        return json.loads(path.read_text(encoding="utf-8")), path, root
    except Exception as error:
        raise HTTPException(status_code=422, detail=f"工作流 JSON 无法读取：{error}")


def workflow_summary(workflow_id: str, path: Path, root: Path, workflow: dict[str, Any] | None = None) -> dict[str, Any]:
    if workflow is None:
        try:
            workflow = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            workflow = {}
    return {
        "id": workflow_id,
        "name": path.stem,
        "relativePath": str(path.relative_to(root)),
        "nodeCount": len(workflow.get("nodes", [])),
        "modifiedAt": int(path.stat().st_mtime),
    }


async def get_object_info(comfy_url: str) -> dict[str, Any]:
    cached = object_info_cache.get(comfy_url)
    if cached and time.time() - cached[0] < 30:
        return cached[1]
    async with httpx.AsyncClient(timeout=30.0, trust_env=False) as client:
        response = await client.get(f"{comfy_url}/object_info")
        response.raise_for_status()
        data = response.json()
        object_info_cache[comfy_url] = (time.time(), data)
        return data


def node_definitions(node_info: dict[str, Any]) -> list[tuple[str, Any]]:
    inputs = node_info.get("input", {})
    return list(inputs.get("required", {}).items()) + list(inputs.get("optional", {}).items())


def is_widget(spec: Any) -> bool:
    if not isinstance(spec, (list, tuple)) or not spec:
        return False
    value_type = spec[0]
    return isinstance(value_type, list) or value_type in {"STRING", "INT", "FLOAT", "BOOLEAN", "COMBO", DYNAMIC_COMBO}


def workflow_input_map(node: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item.get("name", ""): item for item in node.get("inputs", [])}


CONTROL_VALUES = {"fixed", "increment", "decrement", "randomize"}
DYNAMIC_COMBO = "COMFY_DYNAMICCOMBO_V3"


def node_widget_values(node: dict[str, Any], node_info: dict[str, Any]) -> dict[str, Any]:
    values = node.get("widgets_values") or []
    if isinstance(values, dict):
        return dict(values)
    result: dict[str, Any] = {}
    cursor = 0
    sockets = workflow_input_map(node)
    for name, spec in node_definitions(node_info):
        if not is_widget(spec):
            continue
        if cursor >= len(values):
            break
        result[name] = values[cursor]
        cursor += 1
        if "seed" in name.lower() and cursor < len(values) and values[cursor] in CONTROL_VALUES:
            cursor += 1
        socket = sockets.get(name)
        if socket and socket.get("widget") and cursor < len(values) and values[cursor] in CONTROL_VALUES:
            cursor += 1
    return result


def media_kind(node_type: str, name: str) -> tuple[str, str] | None:
    node = node_type.lower()
    field = name.lower()
    if "load" not in node and "upload" not in node:
        return None
    if "video" in node or "video" in field:
        return "VIDEO", "video/*"
    if "audio" in node or "audio" in field:
        return "AUDIO", "audio/*"
    if "image" in node or "image" in field:
        return "IMAGE", "image/*"
    return None


def describe_input(node: dict[str, Any], name: str, spec: Any, value: Any) -> dict[str, Any] | None:
    if not is_widget(spec):
        return None
    node_type = node.get("type", "")
    option = spec[1] if len(spec) > 1 and isinstance(spec[1], dict) else {}
    kind = "TEXT"
    mime = "*/*"
    choices: list[Any] = []
    media = media_kind(node_type, name)
    value_type = spec[0]
    if media:
        kind, mime = media
    elif isinstance(value_type, list):
        kind = "SELECT"
        choices = value_type
    elif value_type in {"COMBO", DYNAMIC_COMBO}:
        kind = "SELECT"
        choices = [item.get("key") if isinstance(item, dict) else item for item in option.get("options", [])]
    elif value_type in {"INT", "FLOAT"}:
        kind = "NUMBER"
    elif value_type == "BOOLEAN":
        kind = "BOOLEAN"
    elif value_type == "STRING" and (option.get("multiline") or any(term in name.lower() for term in ("prompt", "text", "description"))):
        kind = "TEXTAREA"
    title = node.get("title") or node_type
    result = {
        "key": f"{node['id']}.{name}",
        "nodeId": str(node["id"]),
        "nodeTitle": title,
        "nodeType": node_type,
        "name": name,
        "label": option.get("label") or name.replace("_", " "),
        "kind": kind,
        "value": value,
        "choices": choices,
        "mime": mime,
    }
    for key in ("min", "max", "step"):
        if key in option:
            result[key] = option[key]
    return result


def workflow_schema(workflow: dict[str, Any], object_info: dict[str, Any]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for node in workflow.get("nodes", []):
        if node.get("mode", 0) != 0:
            continue
        info = object_info.get(node.get("type"))
        if not info:
            continue
        sockets = workflow_input_map(node)
        values = node_widget_values(node, info)
        for name, spec in node_definitions(info):
            socket = sockets.get(name)
            if socket and socket.get("link") is not None:
                continue
            description = describe_input(node, name, spec, values.get(name))
            if description:
                result.append(description)
    return result


def resolve_link(link_id: int, nodes: dict[int, dict[str, Any]], links: dict[int, list[Any]], expected_type: str = "") -> list[Any]:
    link = links.get(link_id)
    if not link:
        raise ValueError(f"缺少连接 {link_id}")
    source_id, source_slot, link_type = int(link[1]), int(link[2]), str(link[5])
    source = nodes.get(source_id)
    if source and source.get("mode", 0) == 4:
        candidates = [item for item in source.get("inputs", []) if item.get("link") is not None]
        typed = [item for item in candidates if item.get("type") in {expected_type, link_type}]
        chosen = (typed or candidates)[0] if candidates else None
        if not chosen:
            raise ValueError(f"无法绕过节点 {source_id}")
        return resolve_link(int(chosen["link"]), nodes, links, chosen.get("type", ""))
    return [str(source_id), source_slot]


def compile_prompt(workflow: dict[str, Any], object_info: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    nodes = {int(node["id"]): node for node in workflow.get("nodes", [])}
    links = {int(link[0]): link for link in workflow.get("links", [])}
    prompt: dict[str, Any] = {}
    for node_id, node in nodes.items():
        if node.get("mode", 0) != 0:
            continue
        node_type = node.get("type")
        info = object_info.get(node_type)
        if not info:
            continue
        inputs: dict[str, Any] = {}
        for socket in node.get("inputs", []):
            if socket.get("link") is not None:
                inputs[socket["name"]] = resolve_link(int(socket["link"]), nodes, links, socket.get("type", ""))
        values = node_widget_values(node, info)
        for name, spec in node_definitions(info):
            key = f"{node_id}.{name}"
            if key in overrides:
                inputs[name] = overrides[key]
            elif name not in inputs and name in values:
                inputs[name] = values[name]
        prompt[str(node_id)] = {
            "class_type": node_type,
            "inputs": inputs,
            "_meta": {"title": node.get("title") or node_type},
        }
    return prompt


def job_json(row: sqlite3.Row) -> dict[str, Any]:
    artifacts = json.loads(row["artifacts_json"])
    for index, artifact in enumerate(artifacts):
        artifact["jobId"] = row["id"]
        artifact["index"] = index
        artifact["url"] = f"/api/artifacts/{row['id']}/{index}"
    return {
        "id": row["id"],
        "promptId": row["prompt_id"],
        "workflowId": row["workflow_id"],
        "workflowName": row["workflow_name"],
        "status": row["status"],
        "error": row["error"],
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
        "artifacts": artifacts,
    }


def extract_artifacts(history: dict[str, Any]) -> list[dict[str, Any]]:
    artifacts: list[dict[str, Any]] = []
    for output in history.get("outputs", {}).values():
        if not isinstance(output, dict):
            continue
        for category, values in output.items():
            if not isinstance(values, list):
                continue
            for value in values:
                if not isinstance(value, dict) or not value.get("filename"):
                    continue
                filename = value["filename"]
                mime = mimetypes.guess_type(filename)[0] or {
                    "images": "image/png", "audio": "audio/wav", "videos": "video/mp4", "gifs": "image/gif"
                }.get(category, "application/octet-stream")
                artifacts.append({
                    "filename": filename,
                    "subfolder": value.get("subfolder", ""),
                    "type": value.get("type", "output"),
                    "mimeType": mime,
                })
    return artifacts


async def track_job(job_id: str) -> None:
    for _ in range(24 * 60 * 4):
        with connect_db() as db:
            row = db.execute("SELECT * FROM jobs WHERE id = ?", (job_id,)).fetchone()
        if not row or row["status"] in {"COMPLETED", "FAILED"}:
            return
        try:
            async with httpx.AsyncClient(timeout=10.0, trust_env=False) as client:
                response = await client.get(f"{row['comfy_url']}/history/{row['prompt_id']}")
                response.raise_for_status()
                payload = response.json()
            history = payload.get(row["prompt_id"])
            if history:
                status_data = history.get("status", {})
                status_text = status_data.get("status_str", "")
                messages = status_data.get("messages", [])
                failed = status_text == "error" or any(item and item[0] == "execution_error" for item in messages)
                artifacts = extract_artifacts(history)
                status = "FAILED" if failed else "COMPLETED"
                error = "ComfyUI 执行失败" if failed else ""
                with connect_db() as db:
                    db.execute(
                        "UPDATE jobs SET status=?, error=?, artifacts_json=?, updated_at=? WHERE id=?",
                        (status, error, json.dumps(artifacts, ensure_ascii=False), int(time.time()), job_id),
                    )
                return
        except Exception:
            pass
        await asyncio.sleep(15)


@app.on_event("startup")
async def startup() -> None:
    init_db()
    with connect_db() as db:
        pending = db.execute("SELECT id FROM jobs WHERE status IN ('QUEUED','RUNNING')").fetchall()
    for row in pending:
        asyncio.create_task(track_job(row["id"]))


@app.get("/api/health")
async def health() -> dict[str, Any]:
    return {"ok": True, "service": "Comfy Mobile Gateway", "version": "0.1.0"}


@app.post("/api/login")
async def login(request: LoginRequest) -> dict[str, Any]:
    if request.username != CONFIG["username"] or not password_valid(request.password):
        raise HTTPException(status_code=401, detail="账号或密码错误")
    return {
        "token": issue_token(request.username),
        "username": request.username,
        "comfyInstances": await discover_comfy(),
    }


@app.get("/api/comfy/discover")
async def comfy_discover(_: str = Depends(require_auth)) -> dict[str, Any]:
    return {"instances": await discover_comfy()}


@app.get("/api/workflows")
async def workflows(_: str = Depends(require_auth)) -> dict[str, Any]:
    items = []
    for workflow_id, path, root in workflow_files():
        try:
            items.append(workflow_summary(workflow_id, path, root))
        except OSError:
            continue
    return {"workflows": items}


@app.get("/api/workflows/{workflow_id}")
async def workflow_detail(
    workflow_id: str,
    comfy_url: str = Query(default="", alias="comfyUrl"),
    _: str = Depends(require_auth),
) -> dict[str, Any]:
    workflow, path, root = load_workflow(workflow_id)
    url = await active_comfy(comfy_url)
    try:
        info = await get_object_info(url)
    except Exception as error:
        raise HTTPException(status_code=503, detail=f"无法读取 ComfyUI 节点信息：{error}")
    return {
        "workflow": workflow_summary(workflow_id, path, root, workflow),
        "comfyUrl": url,
        "inputs": workflow_schema(workflow, info),
    }


@app.post("/api/uploads")
async def upload(
    file: UploadFile = File(...),
    kind: str = Form(default="file"),
    _: str = Depends(require_auth),
) -> dict[str, Any]:
    suffix = Path(file.filename or "upload.bin").suffix.lower()
    safe_stem = "".join(character if character.isalnum() or character in "-_" else "_" for character in Path(file.filename or "upload").stem)[:80]
    filename = f"{int(time.time())}_{uuid.uuid4().hex[:8]}_{safe_stem}{suffix}"
    target = (UPLOAD_DIR / filename).resolve()
    if UPLOAD_DIR.resolve() not in target.parents:
        raise HTTPException(status_code=400, detail="非法文件名")
    with target.open("wb") as output:
        while chunk := await file.read(1024 * 1024):
            output.write(chunk)
    return {"storedName": f"mobile/{filename}", "filename": filename, "kind": kind, "size": target.stat().st_size}


@app.post("/api/workflows/{workflow_id}/run")
async def run_workflow(workflow_id: str, request: RunRequest, _: str = Depends(require_auth)) -> dict[str, Any]:
    workflow, path, root = load_workflow(workflow_id)
    url = await active_comfy(request.comfyUrl)
    try:
        info = await get_object_info(url)
        prompt = compile_prompt(workflow, info, request.overrides)
        async with httpx.AsyncClient(timeout=30.0, trust_env=False) as client:
            response = await client.post(f"{url}/prompt", json={"prompt": prompt, "client_id": f"comfy-mobile-{uuid.uuid4().hex}"})
            if response.status_code >= 400:
                raise RuntimeError(response.text)
            prompt_id = response.json()["prompt_id"]
    except Exception as error:
        raise HTTPException(status_code=422, detail=f"提交工作流失败：{error}")
    now = int(time.time())
    job_id = uuid.uuid4().hex
    with connect_db() as db:
        db.execute(
            "INSERT INTO jobs(id,prompt_id,workflow_id,workflow_name,comfy_url,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
            (job_id, prompt_id, workflow_id, path.stem, url, "QUEUED", now, now),
        )
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    asyncio.create_task(track_job(job_id))
    return {"job": job_json(row)}


@app.get("/api/jobs")
async def jobs(_: str = Depends(require_auth)) -> dict[str, Any]:
    with connect_db() as db:
        rows = db.execute("SELECT * FROM jobs ORDER BY created_at DESC LIMIT 200").fetchall()
    return {"jobs": [job_json(row) for row in rows]}


@app.get("/api/jobs/{job_id}")
async def job(job_id: str, _: str = Depends(require_auth)) -> dict[str, Any]:
    with connect_db() as db:
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"job": job_json(row)}


@app.get("/api/artifacts/{job_id}/{artifact_index}")
async def artifact(job_id: str, artifact_index: int, _: str = Depends(require_auth)) -> StreamingResponse:
    with connect_db() as db:
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="任务不存在")
    artifacts = json.loads(row["artifacts_json"])
    if artifact_index < 0 or artifact_index >= len(artifacts):
        raise HTTPException(status_code=404, detail="产物不存在")
    item = artifacts[artifact_index]
    params = urllib.parse.urlencode({
        "filename": item["filename"], "subfolder": item.get("subfolder", ""), "type": item.get("type", "output")
    })

    async def stream():
        async with httpx.AsyncClient(timeout=None, trust_env=False) as client:
            async with client.stream("GET", f"{row['comfy_url']}/view?{params}") as response:
                response.raise_for_status()
                async for chunk in response.aiter_bytes(1024 * 1024):
                    yield chunk

    return StreamingResponse(stream(), media_type=item.get("mimeType", "application/octet-stream"), headers={"Content-Disposition": f"inline; filename*=UTF-8''{urllib.parse.quote(item['filename'])}"})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=CONFIG["bindHost"], port=int(CONFIG["port"]))

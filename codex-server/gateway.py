from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import secrets
import time
from collections import deque
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field


BASE_DIR = Path(__file__).resolve().parent
CONFIG = json.loads((BASE_DIR / "config.json").read_text(encoding="utf-8"))


def codex_binary() -> str:
    configured = Path(CONFIG["codexBinary"])
    if configured.is_file():
        return str(configured)
    candidates = list(Path.home().glob(".vscode/extensions/openai.chatgpt-*-linux-x64/bin/linux-x86_64/codex"))
    if not candidates:
        raise RuntimeError("Codex executable was not found")
    return str(max(candidates, key=lambda path: path.stat().st_mtime))


class CodexRpc:
    def __init__(self) -> None:
        self.process: asyncio.subprocess.Process | None = None
        self.pending: dict[int, asyncio.Future] = {}
        self.approvals: dict[str, dict[str, Any]] = {}
        self.loaded_threads: set[str] = set()
        self.events: deque[dict[str, Any]] = deque(maxlen=4000)
        self.condition = asyncio.Condition()
        self.next_id = 1
        self.next_event = 1
        self.write_lock = asyncio.Lock()

    async def start(self) -> None:
        self.process = await asyncio.create_subprocess_exec(
            codex_binary(), "app-server", "--listen", "stdio://",
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            limit=32 * 1024 * 1024,
        )
        asyncio.create_task(self._read_stdout())
        asyncio.create_task(self._read_stderr())
        await self.call("initialize", {
            "clientInfo": {"name": "codex_mobile", "title": "Codex Mobile", "version": "0.1.0"},
            "capabilities": {"experimentalApi": True},
        })
        await self.notify("initialized", {})

    async def stop(self) -> None:
        if self.process and self.process.returncode is None:
            self.process.terminate()
            try:
                await asyncio.wait_for(self.process.wait(), 3)
            except asyncio.TimeoutError:
                self.process.kill()

    async def call(self, method: str, params: dict[str, Any] | None = None) -> Any:
        if not self.process or self.process.returncode is not None:
            raise RuntimeError("Codex app-server is not running")
        request_id = self.next_id
        self.next_id += 1
        future = asyncio.get_running_loop().create_future()
        self.pending[request_id] = future
        await self._write({"jsonrpc": "2.0", "id": request_id, "method": method, "params": params or {}})
        try:
            return await asyncio.wait_for(future, 60)
        finally:
            self.pending.pop(request_id, None)

    async def notify(self, method: str, params: dict[str, Any]) -> None:
        await self._write({"jsonrpc": "2.0", "method": method, "params": params})

    async def answer(self, request_id: str, decision: str) -> None:
        request = self.approvals.pop(request_id, None)
        if not request:
            raise KeyError("Approval request no longer exists")
        method = request["method"]
        if method in {"item/commandExecution/requestApproval", "item/fileChange/requestApproval"}:
            result = {"decision": "accept" if decision == "accept" else "decline"}
        elif method in {"execCommandApproval", "applyPatchApproval"}:
            result = {"decision": "approved" if decision == "accept" else "denied"}
        else:
            raise ValueError(f"Unsupported approval method: {method}")
        await self._write({"jsonrpc": "2.0", "id": request["rpcId"], "result": result})

    async def wait_events(self, after: int, timeout: float) -> list[dict[str, Any]]:
        events = [event for event in self.events if event["seq"] > after]
        if events:
            return events
        async with self.condition:
            try:
                await asyncio.wait_for(self.condition.wait(), timeout)
            except asyncio.TimeoutError:
                return []
        return [event for event in self.events if event["seq"] > after]

    async def _write(self, message: dict[str, Any]) -> None:
        if not self.process or not self.process.stdin:
            raise RuntimeError("Codex app-server stdin is unavailable")
        data = (json.dumps(message, ensure_ascii=False) + "\n").encode()
        async with self.write_lock:
            self.process.stdin.write(data)
            await self.process.stdin.drain()

    async def _read_stdout(self) -> None:
        assert self.process and self.process.stdout
        while line := await self.process.stdout.readline():
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                continue
            request_id = message.get("id")
            if request_id is not None and ("result" in message or "error" in message):
                future = self.pending.get(request_id)
                if future and not future.done():
                    if "error" in message:
                        future.set_exception(RuntimeError(str(message["error"])))
                    else:
                        future.set_result(message.get("result"))
                continue
            if request_id is not None and "method" in message:
                approval_id = str(request_id)
                request = {"rpcId": request_id, "requestId": approval_id, "method": message["method"], "params": message.get("params", {})}
                self.approvals[approval_id] = request
                await self._emit("approval/requested", request)
                continue
            if "method" in message:
                await self._emit(message["method"], message.get("params", {}))

    async def _read_stderr(self) -> None:
        assert self.process and self.process.stderr
        while line := await self.process.stderr.readline():
            text = line.decode(errors="replace").strip()
            if text:
                await self._emit("gateway/codexLog", {"message": text[-1000:]})

    async def _emit(self, method: str, params: dict[str, Any]) -> None:
        event = {"seq": self.next_event, "method": method, "params": params, "createdAt": int(time.time())}
        self.next_event += 1
        self.events.append(event)
        async with self.condition:
            self.condition.notify_all()


rpc = CodexRpc()


@asynccontextmanager
async def lifespan(_: FastAPI):
    await rpc.start()
    yield
    await rpc.stop()


app = FastAPI(title="Codex Mobile Gateway", version="0.1.0", lifespan=lifespan)


class LoginRequest(BaseModel):
    username: str
    password: str


class ThreadCreateRequest(BaseModel):
    cwd: str = "/gpu_move"
    model: str | None = None


class TurnRequest(BaseModel):
    text: str


class ApprovalRequest(BaseModel):
    decision: str


def password_valid(password: str) -> bool:
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(CONFIG["passwordSalt"]), 300_000).hex()
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
    try:
        encoded, signature = authorization[7:].split(".", 1)
        expected = hmac.new(bytes.fromhex(CONFIG["tokenSecret"]), encoded.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            raise ValueError
        padding = "=" * (-len(encoded) % 4)
        username, expires, _ = base64.urlsafe_b64decode(encoded + padding).decode().split(":", 2)
        if username != CONFIG["username"] or int(expires) < time.time():
            raise ValueError
        return username
    except (ValueError, TypeError):
        raise HTTPException(status_code=401, detail="登录已失效")


async def codex_call(method: str, params: dict[str, Any]) -> Any:
    try:
        return await rpc.call(method, params)
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"Codex 调用失败：{error}")


@app.get("/api/health")
async def health() -> dict[str, Any]:
    return {"ok": True, "codexRunning": bool(rpc.process and rpc.process.returncode is None)}


@app.post("/api/login")
async def login(request: LoginRequest) -> dict[str, str]:
    if request.username != CONFIG["username"] or not password_valid(request.password):
        raise HTTPException(status_code=401, detail="账号或密码错误")
    return {"token": issue_token(request.username), "username": request.username}


@app.get("/api/threads")
async def threads(search: str = "", _: str = Depends(require_auth)) -> Any:
    params: dict[str, Any] = {"limit": 100, "sortKey": "updated_at", "sortDirection": "desc"}
    if search:
        params["searchTerm"] = search
    return await codex_call("thread/list", params)


@app.post("/api/threads")
async def create_thread(request: ThreadCreateRequest, _: str = Depends(require_auth)) -> Any:
    path = Path(request.cwd).expanduser().resolve()
    if not path.is_dir():
        raise HTTPException(status_code=400, detail="工作目录不存在")
    params: dict[str, Any] = {"cwd": str(path), "approvalPolicy": "on-request"}
    if request.model:
        params["model"] = request.model
    result = await codex_call("thread/start", params)
    thread = result.get("thread", {})
    if thread.get("id"):
        rpc.loaded_threads.add(thread["id"])
    return result


@app.get("/api/threads/{thread_id}")
async def read_thread(thread_id: str, _: str = Depends(require_auth)) -> Any:
    return await codex_call("thread/read", {"threadId": thread_id, "includeTurns": True})


@app.post("/api/threads/{thread_id}/turns")
async def start_turn(thread_id: str, request: TurnRequest, _: str = Depends(require_auth)) -> Any:
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="消息不能为空")
    if thread_id not in rpc.loaded_threads:
        await codex_call("thread/resume", {"threadId": thread_id, "excludeTurns": True, "approvalPolicy": "on-request"})
        rpc.loaded_threads.add(thread_id)
    return await codex_call("turn/start", {
        "threadId": thread_id,
        "input": [{"type": "text", "text": request.text}],
        "approvalPolicy": "on-request",
    })


@app.post("/api/threads/{thread_id}/turns/{turn_id}/interrupt")
async def interrupt_turn(thread_id: str, turn_id: str, _: str = Depends(require_auth)) -> Any:
    return await codex_call("turn/interrupt", {"threadId": thread_id, "turnId": turn_id})


@app.get("/api/events")
async def events(after: int = 0, timeout: float = Query(default=25, ge=1, le=30), _: str = Depends(require_auth)) -> dict[str, Any]:
    data = await rpc.wait_events(after, timeout)
    return {"events": data, "lastSeq": data[-1]["seq"] if data else after}


@app.post("/api/approvals/{request_id}")
async def answer_approval(request_id: str, request: ApprovalRequest, _: str = Depends(require_auth)) -> dict[str, bool]:
    if request.decision not in {"accept", "decline"}:
        raise HTTPException(status_code=400, detail="无效审批结果")
    try:
        await rpc.answer(request_id, request.decision)
    except (KeyError, ValueError) as error:
        raise HTTPException(status_code=404, detail=str(error))
    return {"ok": True}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=CONFIG["bindHost"], port=int(CONFIG["port"]))

#!/usr/bin/env python3
"""Local agent bridge for OpenClaude Mobile.

Why this exists
---------------
The Android UI must never hold the reasoning loop itself. If it did, a thermal
throttle or an ANR would look like a model failure, and every prompt change would
need an app release. This process owns the loop instead, and speaks one narrow
protocol to the app: newline-delimited JSON over a unix socket or TCP.

Two deployment shapes:

  * edge   -- runs inside the proot userland on the phone, spawned by the app
  * cloud  -- runs on a small server, reached over an authenticated websocket

Both use the same handler, so behaviour cannot drift between them.

Run:
    python3 agent_server.py --transport tcp --port 8765 --provider openrouter
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import pathlib
import secrets
import sys
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, AsyncIterator, Protocol

LOG = logging.getLogger("ocm.bridge")

MAX_TURN_SECONDS = 600
MAX_PROMPT_CHARS = 60_000
MAX_SUBAGENTS = 3


# --------------------------------------------------------------------------- events


def event(kind: str, **payload: Any) -> str:
    """One wire line. Compact separators: this crosses a socket per token."""
    body = {"type": kind}
    body.update(payload)
    return json.dumps(body, separators=(",", ":"), ensure_ascii=False)


@dataclass
class Turn:
    session_id: str
    text: str
    agent_count: int = 1
    started_at: float = field(default_factory=time.monotonic)

    def expired(self) -> bool:
        return (time.monotonic() - self.started_at) > MAX_TURN_SECONDS


# --------------------------------------------------------------------------- providers


class Provider(Protocol):
    name: str

    async def stream(self, messages: list[dict[str, str]], tools: list[dict[str, Any]]) -> AsyncIterator[dict[str, Any]]:
        ...


class OpenAiCompatibleProvider:
    """Any /chat/completions endpoint: OpenRouter, vLLM, Ollama, GitHub Models."""

    def __init__(self, base_url: str, api_key: str, model: str, max_tokens: int = 4096) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.max_tokens = max_tokens
        self.name = model

    async def stream(self, messages, tools):
        import urllib.request

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": True,
            "max_tokens": self.max_tokens,
        }
        if tools:
            payload["tools"] = tools

        request = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )

        # urllib is blocking; push the read loop to a thread so the event loop stays free.
        loop = asyncio.get_running_loop()

        def read_all() -> list[str]:
            lines: list[str] = []
            with urllib.request.urlopen(request, timeout=120) as response:
                for raw in response:
                    line = raw.decode("utf-8", "replace").strip()
                    if line.startswith("data:"):
                        lines.append(line[5:].strip())
            return lines

        for chunk in await loop.run_in_executor(None, read_all):
            if chunk == "[DONE]":
                break
            try:
                parsed = json.loads(chunk)
            except json.JSONDecodeError:
                LOG.warning("skipping unparsable chunk")
                continue
            delta = parsed.get("choices", [{}])[0].get("delta", {})
            if text := delta.get("content"):
                yield {"kind": "text", "text": text}
            for call in delta.get("tool_calls", []) or []:
                yield {"kind": "tool_call", "name": call.get("function", {}).get("name", "tool"),
                       "input": call.get("function", {}).get("arguments")}


def build_provider(kind: str) -> Provider:
    if kind == "openrouter":
        return OpenAiCompatibleProvider(
            base_url=os.environ.get("OPENAI_BASE_URL", "https://openrouter.ai/api/v1"),
            api_key=os.environ.get("OPENAI_API_KEY", ""),
            model=os.environ.get("OPENAI_MODEL", "anthropic/claude-sonnet-4"),
        )
    if kind == "ollama":
        return OpenAiCompatibleProvider(
            base_url=os.environ.get("OPENAI_BASE_URL", "http://127.0.0.1:11434/v1"),
            api_key="ollama",
            model=os.environ.get("OPENAI_MODEL", "qwen2.5-coder:7b"),
        )
    raise SystemExit(f"unknown provider kind: {kind}")


# --------------------------------------------------------------------------- tools


TOOL_SPECS = [
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "Read a UTF-8 text file from the agent workspace.",
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string"}},
                "required": ["path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "write_file",
            "description": "Write a text file into the agent workspace.",
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string"}, "content": {"type": "string"}},
                "required": ["path", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "run_command",
            "description": "Run a shell command inside the sandbox workspace.",
            "parameters": {
                "type": "object",
                "properties": {"command": {"type": "string"}},
                "required": ["command"],
            },
        },
    },
]


# --------------------------------------------------------------------------- session


class Session:
    """One conversation, kept in memory. Persistence belongs to the app's database."""

    def __init__(self, session_id: str, provider: Provider) -> None:
        self.id = session_id
        self.provider = provider
        self.history: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "You are a coding agent running on a phone. Prefer small, verifiable "
                    "steps. Show what you changed. Never claim a command succeeded unless "
                    "you saw its output."
                ),
            }
        ]
        self.lock = asyncio.Lock()

    async def run(self, turn: Turn) -> AsyncIterator[str]:
        if self.lock.locked():
            yield event("error", message="a turn is already running", retryable=True)
            return

        async with self.lock:
            if len(turn.text) > MAX_PROMPT_CHARS:
                yield event("error", message="prompt too long")
                return

            self.history.append({"role": "user", "content": turn.text})

            subagent_ids = [
                f"{self.id[:8]}-{i}" for i in range(min(turn.agent_count, MAX_SUBAGENTS) - 1)
            ]
            for sid in subagent_ids:
                yield event("agent_spawn", id=sid, role="worker", task=turn.text[:120])

            try:
                async for chunk in self.provider.stream(self.history, TOOL_SPECS):
                    if turn.expired():
                        yield event("error", message="turn timed out", retryable=True)
                        break
                    if chunk["kind"] == "text":
                        yield event("assistant_delta", text=chunk["text"])
                    elif chunk["kind"] == "tool_call":
                        yield event("tool_call", id=uuid.uuid4().hex[:12],
                                    name=chunk["name"], input=chunk.get("input"))
            except Exception as exc:  # noqa: BLE001 - surfaced to the UI verbatim
                LOG.exception("provider failure")
                yield event("error", message=str(exc), retryable=True)
                return

            for sid in subagent_ids:
                yield event("agent_done", id=sid, summary="completed")

            yield event("usage", input_tokens=0, output_tokens=0)
            yield event("turn_done", stop_reason="end_turn")


# --------------------------------------------------------------------------- server


class Bridge:
    def __init__(self, provider: Provider, token: str) -> None:
        self.provider = provider
        self.token = token
        self.sessions: dict[str, Session] = {}

    def session(self, session_id: str) -> Session:
        if session_id not in self.sessions:
            self.sessions[session_id] = Session(session_id, self.provider)
        return self.sessions[session_id]

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        peer = writer.get_extra_info("peername")
        LOG.info("client connected: %s", peer)
        try:
            # First line must be the handshake; anything else is dropped.
            hello = await asyncio.wait_for(reader.readline(), timeout=10)
            try:
                auth = json.loads(hello or b"{}")
            except json.JSONDecodeError:
                auth = {}
            if not secrets.compare_digest(str(auth.get("token", "")), self.token):
                writer.write(b'{"type":"error","message":"unauthorized"}\n')
                await writer.drain()
                return

            while True:
                line = await reader.readline()
                if not line:
                    break
                try:
                    message = json.loads(line)
                except json.JSONDecodeError:
                    writer.write(b'{"type":"error","message":"bad json"}\n')
                    await writer.drain()
                    continue

                kind = message.get("type")
                if kind == "user_turn":
                    turn = Turn(
                        session_id=message.get("sessionId", "default"),
                        text=message.get("text", ""),
                        agent_count=int(message.get("agentCount", 1)),
                    )
                    session = self.session(turn.session_id)
                    async for chunk in session.run(turn):
                        writer.write(chunk.encode() + b"\n")
                        await writer.drain()
                elif kind == "interrupt":
                    LOG.info("interrupt requested")
                    writer.write(b'{"type":"turn_done","stop_reason":"interrupted"}\n')
                    await writer.drain()
                elif kind == "ping":
                    writer.write(b'{"type":"pong"}\n')
                    await writer.drain()
                else:
                    writer.write(b'{"type":"error","message":"unknown message"}\n')
                    await writer.drain()
        except asyncio.TimeoutError:
            LOG.warning("handshake timeout")
        finally:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:  # noqa: BLE001
                pass
            LOG.info("client disconnected")


async def main() -> None:
    parser = argparse.ArgumentParser(description="OpenClaude Mobile agent bridge")
    parser.add_argument("--transport", choices=["tcp", "unix"], default="tcp")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--socket", default="/tmp/ocm-agent.sock")
    parser.add_argument("--provider", default="openrouter")
    parser.add_argument("--token", default=os.environ.get("OCM_BRIDGE_TOKEN", ""))
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    token = args.token or secrets.token_urlsafe(24)
    bridge = Bridge(build_provider(args.provider), token)

    if args.transport == "unix":
        socket_path = pathlib.Path(args.socket)
        if socket_path.exists():
            socket_path.unlink()
        server = await asyncio.start_unix_server(bridge.handle, path=args.socket)
        LOG.info("listening on unix:%s", args.socket)
    else:
        server = await asyncio.start_server(bridge.handle, args.host, args.port)
        LOG.info("listening on tcp://%s:%d", args.host, args.port)

    LOG.info("handshake token: %s", token)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)

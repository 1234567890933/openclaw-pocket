#!/usr/bin/env node
/**
 * Node host for the agent, used for the desktop and LAN deployment shapes.
 *
 * The phone talks to this over the same newline-delimited JSON protocol as the
 * in-guest bridge, so the Android side has exactly one parser to maintain. The
 * point of having a Node host at all is that the OpenClaude CLI is a Node program:
 * on a desktop or a server we can spawn the real CLI instead of reimplementing
 * the loop.
 */

import { createServer } from "node:net";
import { spawn } from "node:child_process";
import { randomBytes, timingSafeEqual } from "node:crypto";
import process from "node:process";

const PORT = Number(process.env.OCM_PORT ?? 8765);
const HOST = process.env.OCM_HOST ?? "127.0.0.1";
const TOKEN = process.env.OCM_BRIDGE_TOKEN ?? randomBytes(24).toString("base64url");
const CLI = process.env.OCM_CLI ?? "openclaude";
const MAX_LINE = 1 << 20; // 1 MiB per inbound line; a prompt is never this big.

const log = (...args) => console.error("[ocm.bridge]", ...args);

function safeEqual(a, b) {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

function send(socket, payload) {
  if (socket.destroyed) return;
  socket.write(JSON.stringify(payload) + "\n");
}

/**
 * Spawns the CLI once per session and pipes prompts into it. Keeping the process
 * alive between turns is what makes the second question in a conversation fast.
 */
class Session {
  constructor(id) {
    this.id = id;
    this.child = null;
    this.buffer = "";
  }

  ensureChild(socket) {
    if (this.child && !this.child.killed) return this.child;

    this.child = spawn(CLI, ["--headless", "--json-stream"], {
      stdio: ["pipe", "pipe", "pipe"],
      env: process.env,
    });

    this.child.stdout.setEncoding("utf8");
    this.child.stdout.on("data", (chunk) => {
      this.buffer += chunk;
      let index;
      while ((index = this.buffer.indexOf("\n")) >= 0) {
        const line = this.buffer.slice(0, index).trim();
        this.buffer = this.buffer.slice(index + 1);
        if (!line) continue;
        try {
          send(socket, JSON.parse(line));
        } catch {
          send(socket, { type: "assistant_delta", text: line });
        }
      }
    });

    this.child.stderr.setEncoding("utf8");
    this.child.stderr.on("data", (chunk) => {
      for (const line of chunk.split("\n")) {
        if (line.trim()) log("stderr:", line.trim());
      }
    });

    this.child.on("exit", (code) => {
      log(`session ${this.id} exited with ${code}`);
      send(socket, { type: "turn_done", stop_reason: code === 0 ? "end_turn" : "error" });
      this.child = null;
    });

    return this.child;
  }

  turn(socket, message) {
    const child = this.ensureChild(socket);
    child.stdin.write(JSON.stringify(message) + "\n");
  }

  interrupt(socket) {
    if (!this.child) return;
    this.child.stdin.write(JSON.stringify({ type: "interrupt" }) + "\n");
    send(socket, { type: "turn_done", stop_reason: "interrupted" });
  }

  dispose() {
    if (this.child) {
      this.child.kill("SIGTERM");
      setTimeout(() => this.child?.kill("SIGKILL"), 2000).unref();
      this.child = null;
    }
  }
}

const sessions = new Map();

const server = createServer((socket) => {
  socket.setEncoding("utf8");
  let authed = false;
  let buffer = "";
  let sessionId = null;

  socket.on("data", (chunk) => {
    buffer += chunk;
    if (buffer.length > MAX_LINE) {
      send(socket, { type: "error", message: "line too long" });
      socket.destroy();
      return;
    }

    let index;
    while ((index = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, index).trim();
      buffer = buffer.slice(index + 1);
      if (!line) continue;

      let message;
      try {
        message = JSON.parse(line);
      } catch {
        send(socket, { type: "error", message: "bad json" });
        continue;
      }

      if (!authed) {
        if (!safeEqual(message.token ?? "", TOKEN)) {
          send(socket, { type: "error", message: "unauthorized" });
          socket.destroy();
          return;
        }
        authed = true;
        send(socket, { type: "ready" });
        continue;
      }

      switch (message.type) {
        case "user_turn": {
          sessionId = message.sessionId ?? "default";
          if (!sessions.has(sessionId)) sessions.set(sessionId, new Session(sessionId));
          sessions.get(sessionId).turn(socket, message);
          break;
        }
        case "interrupt": {
          if (sessionId) sessions.get(sessionId)?.interrupt(socket);
          break;
        }
        case "ping":
          send(socket, { type: "pong" });
          break;
        default:
          send(socket, { type: "error", message: `unknown message: ${message.type}` });
      }
    }
  });

  socket.on("close", () => {
    log("client closed");
    if (sessionId) sessions.get(sessionId)?.dispose();
  });

  socket.on("error", (error) => log("socket error:", error.message));
});

server.listen(PORT, HOST, () => {
  log(`listening on ${HOST}:${PORT}`);
  log(`handshake token: ${TOKEN}`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    for (const session of sessions.values()) session.dispose();
    server.close(() => process.exit(0));
  });
}

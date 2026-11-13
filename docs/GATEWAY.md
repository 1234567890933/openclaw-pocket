# Running the gateway on a phone

## Why this is the hard part

The gateway assumes a desktop: a writable home directory, a Node runtime it can
upgrade, and a loopback port nobody else is using. On Android it gets none of those
by default — the app sandbox is read-only outside its own data directory, there is no
system Node, and ports are shared with every other app.

`GatewayHost` deals with each:

| Assumption | What the app does |
|---|---|
| writable home | proot rootfs inside `filesDir`, `OPENCLAW_HOME=/root/openclaw` |
| Node runtime | bundled Node 24 in the runtime bundle, executed through proot |
| free port | picks a candidate, confirms with a real TCP connect, retries 12 times |
| long-lived process | foreground service + supervisor with capped backoff |
| upgradeable runtime | version pinned in the bundle; upgrade = new bundle, not a fetch |

## Lifecycle

```
user taps "Start on this phone"
  → ToolBridgeServer.start()        (127.0.0.1:18790, bearer token)
  → GatewayHost.start()
      → preflight: proot, rootfs, node, openclaw.mjs
      → pick port, build proot argv, spawn
      → poll TCP accept for up to 45 s
      → Running(port, pid)
  → GatewayClient.connect(endpoint)
      → Hello frame
      → Ready → UI shows the pill as connected
```

Stopping reverses it, and the foreground service is what keeps the process alive
with the screen off.

## Protocol

Frames are JSON objects over one WebSocket, `{id, type, …}`. Requests carry an id;
events do not. Unknown `type` values are dropped with a log line, so a newer gateway
cannot crash an older client.

Outbound: `hello`, `user_message`, `interrupt`, `approval_reply`, `ping`.
Inbound: `ready`, `delta`, `tool_call`, `tool_result`, `approval_request`,
`bot_event`, `done`, `error`, `pong`.

The typed copy lives in `GatewayProtocol.kt`. It is intentionally *not* generated
from upstream's types: the app has to keep working against the previous gateway
release, and a shared type would drag both forward together.

## Remote mode

Point the app at a computer instead:

1. On the computer, run the gateway and approve this phone with the pairing flow.
2. In the app: Gateway sheet → enter `192.168.1.20:18789` and the token.

Same client, same protocol. The tool bridge still runs on the phone, so tools
execute on the device even when the model loop is remote — which is usually what you
want, because the files you care about are on the phone.

## Failure modes worth knowing

* **Port taken.** Handled: candidate is probed before use.
* **Node killed by the OS.** The foreground service plus supervisor restarts it; the
  client's outbox holds anything typed meanwhile.
* **Rootfs wiped by the user.** Preflight fails fast with "rootfs missing" rather
  than a ptrace error nobody can read.
* **Gateway starts but never accepts.** 45s timeout, then a retry with backoff, then
  a permanent `Failed` state the sheet shows verbatim.

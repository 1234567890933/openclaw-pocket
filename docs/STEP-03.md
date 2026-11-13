# Step 3 — the gateway moves onto the phone

Upstream, OpenClaw's Gateway is a desktop control plane: it runs on a laptop or a
server, and every other surface — Control UI, CLI, TUI, the native apps — is a
client of it. A phone is a node, not a host.

This step closes that gap. The app now runs the gateway itself, inside the same
proot userland the agent already used, and the phone stops needing a computer on
the network to have an assistant.

## What was added

**A gateway host.** `GatewayHost` starts `openclaw gateway run` bound to
`127.0.0.1` on a port picked at runtime, waits for a real TCP accept before
reporting ready, and supervises the process with capped backoff. Loopback-only is
not a default to be changed later: a gateway listening on the LAN is remote code
execution with a battery.

**A client for it.** `GatewayClient` speaks the gateway's frame protocol over a
WebSocket, with a heartbeat, reconnect backoff and an outbox — a message typed while
the socket is down is sent when it comes back rather than dropped.

**A tool bridge.** The interesting inversion. Rather than letting the agent run
shell commands and hoping, the gateway POSTs to a loopback HTTP server in the app
(`ToolBridgeServer`), which runs the tool inside the app process. So the sandbox is
Android's permission model, not a shell that happens to exist on the device. The
bridge is bearer-authenticated with the gateway token, compared in constant time,
capped at 512 KB per body, one request per connection.

**Six tools** — files, code, search, http, browser, and the bridge that exposes them.
See `docs/TOOLS.md`.

**Five bots and an orchestrator** — Scout, Forge, Ledger, Critic, Runner, plus a
plan → dispatch → synthesise loop. See `docs/BOTS.md`.

**Glass, in one place only.** The composer is frosted glass: a translucent gradient,
a specular border that is bright at the top-left, an inner highlight and a drop
shadow. Nothing else on the screen is glass. Two glass surfaces make a screen look
like a 2010 dashboard; one reads as a decision.

## Three transports, one transcript

| Transport | When | Where the loop runs |
|---|---|---|
| On device | a model is installed and the turn needs no tools | phone CPU, via llama.cpp |
| Gateway | gateway connected | phone, Node in proot |
| Remote gateway | pointed at a computer | that computer |

`GeminiViewModel` picks per turn and writes into the same buffer regardless. The
transcript does not know or care which one answered; the pill under the title bar
does.

## What this does not do

* It does not ship OpenClaw. The package is installed at build time by
  `scripts/fetch-openclaw.sh` from a pinned version and packed into the runtime
  bundle — see `third_party/openclaw.lock.json` for why vendoring 14 GB was the
  wrong call.
* It does not open the gateway to the network. If you want your laptop's channels
  to reach this phone, that is a deliberate future feature with a pairing flow, not
  a flag.
* Channels (WhatsApp, Telegram and the rest) are upstream's plugins and are not
  wired here. The phone is a host for the gateway, not yet a channel endpoint.

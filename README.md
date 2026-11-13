# OpenClaw Pocket

An Android host for the open-source [OpenClaw](https://github.com/openclaw/openclaw) gateway
coding agent. The agent runs inside a bundled Linux userland on the phone — no
Termux, no root, no PC. The app is a normal chat interface, not a terminal.

This repository is the Android application. It does **not** contain OpenClaude's
own source: the CLI is installed at first boot from a runtime bundle you publish,
and the licence and attribution for it stay with its authors.

## Status

Step 3. The app now **hosts** the OpenClaw gateway on the phone instead of only
connecting to one, adds a tool belt the agent calls back into, a five-bot team with
an orchestrator, and a glass composer as the single visual flourish.

| Area | State |
|---|---|
| Conversation UI, glass composer, bot strip, gateway pill | complete |
| Gateway host on the phone (proot + Node, loopback only) | complete, needs the runtime bundle |
| Gateway client (frames, heartbeat, outbox, reconnect) | complete |
| Tool bridge + six tools (files, code, search, http, browser) | complete |
| Five bots + orchestrator + supervisor | complete |
| On-device inference (vendored llama.cpp) | complete, needs a GGUF |
| One-tap installer | complete |
| Chat storage, vault, gate, native core | complete |
| OpenClaw package | not vendored — pinned install, see `third_party/openclaw.lock.json` |
| Channels (WhatsApp, Telegram, …) | upstream plugins, not wired |
| Attachments, voice, provider editor, approvals UI | stubs that say so |

`docs/STEP-03.md` for what changed, `PROJECT-STATE.md` for where it stands,
`docs/architecture-poster.png` for the whole thing on one page.

## Quick start

```bash
git clone <your fork>
cd OpenClaudeMobile
cp local.properties.example local.properties   # point sdk.dir at your SDK
./gradlew :app:assembleDebug
```

Then follow `docs/BUILD.md` to set up the OAuth client id and the runtime bundle.
Without the bundle the app installs, shows the bootstrap screen, and stops there —
which is the honest failure mode rather than a fake "ready".

## How it fits together

```
┌─────────────────────────────────────────────┐
│ Compose UI  (chat, history, settings)       │
├─────────────────────────────────────────────┤
│ AgentRepository  ── JSON lines ──┐          │
├──────────────────────────────────┼──────────┤
│ SessionRunner (pipes, timeouts)  │          │
├──────────────────────────────────┼──────────┤
│ proot  ──►  Debian userland  ──► node cli   │
│            (rootfs in filesDir)             │
└─────────────────────────────────────────────┘
```

Full detail in `docs/ARCHITECTURE.md`.

## Why the app is small and the runtime is large

Hand-written source for an app like this is a few megabytes. The 500–700 MB that
comparable projects quote is the embedded Linux userland, the Node and Python
runtimes, and the offline toolset — binaries, not source. Padding a repository to
a size target would only make it slower to clone and harder to review. What
actually matters is that the payload is verified, resumable and optional per
component, which is what `BootstrapManager` does.

## Licence

Application code: MIT. See `docs/LICENSING.md` for what that does and does not
cover — in particular, OpenClaude's own licensing and Anthropic's trademarks.

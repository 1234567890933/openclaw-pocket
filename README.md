# OpenClaw Pocket

An Android client for the open-source [OpenClaw](https://github.com/openclaw/openclaw) agent — the gateway runs *on the phone*, not on a laptop the phone connects to.

## What this is

Upstream OpenClaw is a Node process that acts as a local control plane: sessions, tools, events, channels. Its desktop and mobile clients connect to that gateway over the network. On Android the gateway had no home — you ran it on a computer and paired the phone to it.

This project puts the gateway in the phone:

```
Compose UI
   |
   +-- GatewayClient --> GatewayHost (proot + Debian + Node) --> openclaw gateway
   |                        loopback, supervised, restarts on crash
   |
   +-- DirectModelClient --> provider HTTPS (OpenAI-compatible / Gemini)
   |                          no gateway, no userland, no download
   |
   +-- LocalEngine --> llama.cpp via JNI (stub in the shipped APK)
```

Three transports on purpose. The gateway is the full experience; the direct
provider path is the one that always works; the on-device engine is the one that
needs no network at all.

## Modules

| Module | Contains | Compose? |
|---|---|---|
| `:core` | gateway client, tools, bots, storage, security, providers | no |
| `:app` | Compose UI, activity, services | yes |

The split is not decoration: `:core` compiles in a fraction of the time and can be
tested on a plain JVM, which is what made verification possible on a 2 GB machine.

## What works

- **Chat** with streaming, history, per-message actions
- **Direct provider path** — add a key, talk to OpenAI-compatible or Gemini endpoints
- **Gateway path** — Node, proot and the agent bundle ship in the APK; the Debian
  userland is fetched at build time
- **Tool belt** — files, code execution, web search, HTTP, browser (WebView)
- **Bot team** — five personas with tool allow-lists and an orchestrator
- **Guard** — prompt-injection and jailbreak screening, fail-closed, code-locked
- **Device gates** — root detection, RAM/storage floors, arm64 only
- **24 fps cap**, thermal governor, picture-in-picture

## Build

```bash
export ANDROID_HOME=/path/to/sdk
./scripts/fetch-runtime-payloads.sh   # ~250 MB of binaries, not in git
gradle :app:assembleRelease
```

The payloads are deliberately not committed: they are binaries, they change
independently of the source, and a 250 MB blob in git is a blob nobody can review.
`scripts/fetch-runtime-payloads.sh` resolves them, and the CI workflow runs it.

## Source

The full tree (193 source files across Kotlin, Java, C++, Python, Node, TypeScript,
Rust, Go, SQL and shell) is attached to every release as `source.zip`, because the
build environment that produced it could not push a tree of that size through the
API. Unzip it and run the two commands above.

## Layout

```
app/            Compose UI, activity, services
core/           gateway, tools, bots, storage, security (no Compose)
bridge/         Python and Node bridges for LAN/desktop mode
third_party/    vendored llama.cpp (pinned, CPU-only)
tools/          model selection, bundle verifier, policy engine
devtools/       TypeScript status dashboard
docs/           architecture, build, providers, security, roadmap
```

## Licence

MIT for this client. OpenClaw itself is MIT, (c) the OpenClaw Foundation. This is an
independent client and is not affiliated with or endorsed by the upstream project.

# Architecture

## Shape of the system

Three layers, each with one job.

**UI (Compose).** Renders conversation state and forwards intent. It never parses
agent output, never touches the filesystem, and never builds a shell command.

**Agent plumbing (Kotlin, `data/agent`, `data/runtime`).** Owns the process
lifecycle, the wire protocol, timeouts and backpressure. This is where the
interesting failure modes live, so it is kept free of UI concerns and testable
without a device.

**Guest runtime (proot + Debian + Node).** Runs the actual OpenClaude CLI. It is
a real Linux environment inside the app's private data directory, so the agent's
tools — bash, file writes, git — behave the way they do on a desktop.

## First boot

`BootstrapManager` reads `res/raw/bootstrap_manifest.json`, which lists seven
components with a size and a SHA-256 each. Components are ordered by `stage`:

1. proot binary — without it nothing else can run
2. Debian minirootfs — the filesystem
3. Node and Python — the interpreters
4. OpenClaude bundle — the agent itself
5. Offline tools and model index — optional, skippable

Each download writes to `<name>.part` and resumes with an HTTP `Range` header.
Verification happens before the file is renamed into place, so a truncated
download can never be mistaken for an installed component. Extraction is a
purpose-built tar reader: Android ships no tar, and shelling out to one would
require the very runtime we are trying to install.

The manifest is data, not code. Moving to a new runtime version is a release
asset plus an edited JSON file, not an app update.

## Running the agent

`ProotLauncher` builds the argv for proot with the app's rootfs as `/`, binds
`/dev`, `/proc`, `/sys`, and the work directory, and execs node directly on the
CLI. No shell is involved unless the agent asks for one.

`SessionRunner` owns the process: one coroutine reads stdout, a daemon thread
drains stderr. Both pipes must be drained — a chatty child that fills the 64 KB
pipe buffer blocks forever, and that looks exactly like a hang.

## Protocol

Newline-delimited JSON, both directions. Chosen over scraping the CLI's ANSI
output because terminal rendering changes and a GUI should not break when a
spinner does.

Inbound (app to agent):

```json
{"type":"user_turn","sessionId":"...","text":"...","agentCount":1}
{"type":"interrupt","sessionId":"..."}
```

Outbound (agent to app), one object per line:

```json
{"type":"assistant_delta","text":"..."}
{"type":"tool_call","id":"...","name":"read_file","input":{...}}
{"type":"tool_result","id":"...","ok":true,"output":"..."}
{"type":"agent_spawn","id":"...","role":"worker","task":"..."}
{"type":"usage","input_tokens":0,"output_tokens":0}
{"type":"turn_done","stop_reason":"end_turn"}
{"type":"error","message":"...","retryable":true}
```

Unknown event types are logged and dropped. The CLI may add kinds; a new kind
must not crash the stream.

## Streaming and storage

Assistant text arrives as deltas. `ChatViewModel` accumulates into a
`StringBuilder` and flushes to Room at most every 40 ms. Writing each token would
mean several hundred disk writes per reply and visible jank on a mid-range phone.

Retention is a product decision, not a storage limit: four sessions, four hundred
messages each, pruned on write. Old agent transcripts are not something people
return to.

## Hybrid split

The UI is thin on purpose. Two deployment shapes are supported by the same
protocol:

* **edge** — proot on the phone, spawned by the app. Works offline with a local
  model, costs battery and heat.
* **bridge** — `bridge/node/server.mjs` or `bridge/python/agent_server.py` on a
  desktop, a LAN box or a small server. The phone stays cool and the heavy loop
  runs elsewhere.

Both speak identical JSON, so switching is a settings change rather than a code
path.

## Deliberate omissions

* No DI framework. The graph is a dozen objects; `ServiceLocator` is more
  readable than generated code.
* No navigation library in the hot path. Four steps walked in order are a state
  machine, not a graph.
* No WebView. The agent's output is text, and a WebView would add an entire
  renderer for markdown.

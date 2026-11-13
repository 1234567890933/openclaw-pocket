# The tool belt

Six tools, all executed inside the app process, exposed to the gateway over a
loopback HTTP bridge.

| Tool | Risk | What it does |
|---|---|---|
| `files` | write | read, write, list, stat, delete inside the workspace |
| `code` | exec | run a python, node or shell snippet with a timeout |
| `search` | net | web search, Brave API if a key exists, else the HTML endpoint |
| `http` | net | fetch a URL, refuse private addresses, reduce HTML to text |
| `browser` | net | load a page in a real WebView, including JavaScript |
| bridge | — | `POST /tool/{name}`, `GET /tools`, `GET /health` |

## Why tools run in the app, not in the shell

The obvious design is to let the agent run commands in the proot shell. It is also
the design that makes every tool a shell injection waiting to happen, and it gives
the agent the whole filesystem the app can see.

Running them here means:

* the tool decides what it touches, not a command string
* Android's permission model is the outer boundary
* a tool can be audited, rate-limited and shown to the user with a risk badge

## Containment, stated honestly

`code` is a timeout and a working directory. Android gives an unprivileged app no
cgroups, so there is no CPU or memory cap. The denylist in `CodeTool` catches
obvious mistakes and nothing more. Do not describe it as a sandbox.

`files` is genuinely confined: paths are resolved against the workspace and then
canonicalised again, which catches both `../` and a symlink pointing out.

`http` refuses loopback and private ranges on purpose — an agent that can reach
`127.0.0.1` can reach the gateway's own API and the tool bridge itself.

## Adding a tool

1. Implement `Tool` in `tools/<name>/`.
2. Register it in `ToolRegistry`.
3. Give it a risk level. Default to `WRITE` if unsure.
4. Add it to a bot's allow-list in `BotRoster` — a tool nobody can call is dead code.
5. Add a test for the refusal paths first; those are the ones that matter.

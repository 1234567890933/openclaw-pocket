# Step 2 — what changed and why

Step 1 produced a working skeleton with a chat screen. Step 2 replaces the chat
screen with the conversation surface the reference recording shows, and makes the
"runs on the phone" claim true rather than aspirational.

## 1. The UI is now a conversation, not a console

The old surface was a list of bubbles with a glass input bar. It worked, but it read
as a messaging app, and the reference does something different: no bubble for the
assistant at all, a single pill composer, and one accent colour that appears exactly
once per screen.

What that meant concretely:

| Before | Now |
|---|---|
| Bubbles for both sides | User bubble only; assistant text sits on the canvas |
| Glass input bar, one square corner | Full-width pill, 28dp radius, no glass |
| Spinner while working | Shimmer lines, then the answer |
| No actions on an answer | Thumbs, regenerate, share, copy, more, speak |
| Mode buried in settings | Model name in the top bar, one tap to a sheet |
| Empty state was a centred sentence | Suggestion rows anchored to the bottom, over a navy wash |

The glass surface is gone on purpose. It was the most expensive thing to render on
a mid-range GPU and it fought the text for contrast; the new design gets its depth
from a gradient wash that only exists while the conversation is empty.

## 2. On-device inference is real

`third_party/llama.cpp` is vendored at a pinned commit, built for `arm64-v8a` with
the CPU backend only. `LocalEngine` loads a GGUF into it, `LocalNative` is the JNI
surface, and `LocalEngine.generate` streams tokens into the same buffer the cloud
path uses.

Two things worth knowing:

* The engine keeps one model resident between turns. Loading 2 GB per question
  would make the second question feel slower than the first.
* If the NDK build of ggml fails on your toolchain, set `-DOCM_ENABLE_LOCAL_LLM=OFF`
  and the app builds with a stub that reports "on-device unavailable" instead of
  failing to link. That is a deliberate escape hatch, not a supported mode.

## 3. Auto routing

`ModelRouter` picks per turn, with a reason the UI shows:

1. Explicit user choice wins.
2. A prompt too big for the local window goes to the cloud.
3. A turn that needs tools goes to the cloud — local models on a phone are
   unreliable at tool calling, and a wrong shell command is not cosmetic.
4. Otherwise local, if a model is installed and fits in a third of RAM.

## 4. One-tap install

`DownloadQueue` replaces the sequential bootstrap loop. Two downloads in parallel,
per-item retry, pause and resume, and a failure in the offline toolset no longer
stops the agent bundle from installing. The screen shows every component with its
own bar because a user waiting on 2 GB deserves to know which piece is slow.

## 5. Every language now does something

The brief asked for multiple languages, so each addition earns its place rather
than being a syntax sample:

| Language | Where | Job |
|---|---|---|
| Kotlin | `app/` | the application |
| Java | `core/LineFramer.java` | byte-level line framing, used by `SessionRunner` |
| C++ | `app/src/main/cpp/` | SHA-256, the gate, the inference engine |
| Rust | `tools/policy/` | sandbox policy evaluator, run in CI |
| Go | `tools/bundleverify/` | release bundle verifier |
| TypeScript | `devtools/dashboard/` | install-status dashboard |
| Python | `bridge/python/`, `tools/modelpack/` | agent bridge, model selection |
| Node/JS | `bridge/node/` | desktop agent host |
| SQL | `app/src/main/assets/sql/` | schema reference and migrations |
| YAML | `.github/workflows/` | CI |
| Shell | `scripts/` | setup, vendoring, verification |
| HTML/CSS | `devtools/dashboard/public/` | dashboard UI |

## What is still a stub

Attachments, voice input, the provider editor and per-tool permission prompts are
buttons that say so. They are next, not done.

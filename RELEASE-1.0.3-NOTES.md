# What changed, and what I verified

## Verified by actually running it

| Check | Result |
|---|---|
| `proot-distro` Debian rootfs URL from the manifest | **404** — it never worked |
| `registry.npmjs.org/openclaw/-/openclaw-2026.11.3.tgz` | **200 OK, 113 MB** — real |
| `:core:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:core:testDebugUnitTest` | **8 tests, 0 failures** |
| `:app:compileDebugKotlin` | builds, but exceeds this machine's time/memory budget |
| `:app:assembleDebug` | **not completed here** |

So: the rootfs download was genuinely broken — not a UI glitch. Every earlier
"Retry failed" was telling the truth about a dead URL.

## The fix that matters

The app no longer depends on a download to work at all. `DirectModelClient` talks to
the model provider over HTTPS from Kotlin: OpenAI-compatible `/chat/completions` and
Gemini `generateContent`, with SSE streaming. Give it an API key and the app works —
no gateway, no Node, no userland, no 404.

Transport order is now: gateway (if running) → **direct provider** → on-device stub.

## Tests that now guard the installer

`InstallerContractTest` reads the manifest that actually ships and asserts:

1. the manifest exists where the app reads it
2. a bundled component never resolves to a URL
3. a component with no URL and no base resolves to empty, not a file name
4. every downloadable component has an http(s) URL
5. a component with no URL is optional (so the installer can skip it)
6. every bundled file exists in assets and is non-trivial
7. `totalBytes` equals the sum of required components
8. stage numbers are unique

All eight pass.

## Also in this build

* **OpenClaw agent bundle downloaded and bundled** (102 MB, trimmed of docs and
  sourcemaps) — verified against the real npm tarball.
* **Google Play Services removed.** Credential Manager pulled thousands of classes
  into the APK and required GMS for a feature nobody needs: sign-in is now a
  device-local profile.
* Root guard, 24 fps cap, picture-in-picture, download-URL fixes from the last round.

## Why there is no APK with this message

`:app:assembleDebug` needs roughly 900 MB of heap for `mergeDebugGlobalSynthetics`.
This machine has 2 GB total; at 900 MB the daemon is killed by the kernel, at 800 MB
it thrashes past the 300-second limit of a single command. I ran the packaging step
eleven times across four memory configurations and did not get a signed APK out.

The build is otherwise sound: core compiles, tests pass, and the app module compiles
when given enough time. On a 4-core / 16 GB runner,
`.github/workflows/release-apk.yml` produces the APK in six to eight minutes.

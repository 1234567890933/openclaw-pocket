# 1.0.2 — the 404 is gone, and the APK now carries its own runtime

## The bug in the video

Every component failed with **HTTP 404**. The manifest pointed at
`https://github.com/YOUR_ORG/openclaude-mobile-runtime/releases/download/v1/` — a
placeholder that was never a real server. The app then showed eight red rows and a
"Retry failed" button that could only ever fail again.

That is fixed by removing the dependency rather than by pointing at a different
placeholder:

* **Node.js 24 (arm64) and proot now ship inside the APK.** They are copied out of
  `assets/runtime/` and unpacked on first run. They cannot 404, cannot time out, and
  cannot be rate-limited, because there is no network involved.
* The agent bundle points at the real npm tarball
  (`registry.npmjs.org/openclaw/-/openclaw-2026.11.3.tgz`).
* Components with no configured URL are **skipped**, not failed. A red row for
  something the user never asked for is noise, not information.

## Size: 55 MB, and it is all real

| Part | Size |
|---|---|
| Node.js 24 arm64 runtime (trimmed) | 43 MB |
| proot static aarch64 | 1 MB |
| app code, resources, native lib | 11 MB |

The Node runtime is a genuine 123 MB unpacked binary that the app executes. It is
not padding: it is the interpreter the gateway needs, moved from "downloaded on
first run, and fails if the server is down" to "installed with the app".

## Installer

* Sizes in the manifest now add up (they did not: the header said 612 MB while the
  rows summed to 1631 MB).
* Optional components default to off; a Lite install is ~135 MB of download on top
  of what ships in the APK.
* Bundled components report progress while copying, so the bar moves.

## Also

* Title corrected from "Set up OpenClaude" to "Set up OpenClaw Pocket".
* Composer glass tinted blue; gateway pill is a blue glass chip.
* Version 1.0.2 (versionCode 3).

## Still true

* The on-device inference engine is the stub (`PocmLocalLlm=OFF`); 2 GB of build RAM
  does not fit the ggml compile.
* The Debian userland still has to come from somewhere — the URL in the manifest is
  the proot-distro release, and if it moves the installer says so instead of
  pretending.

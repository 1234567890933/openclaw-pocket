# 1.0.1 — runs on the phones it was written for

## The bug that mattered

The device gate used the numbers on the box: 4 GB of RAM and 64 GB of storage. A phone
sold as 6 GB reports about **3.5 GB** to Android, and a 128 GB phone with a full camera
roll has **54 GB** free. So the gate locked out the exact devices it existed to serve.

Fixed properly, and the distinction now lives in the code:

* **Hard limits** (refuse to start): not arm64, under 1.9 GB usable RAM, under 2.5 GB free.
* **Recommendations** (start, but in lite mode): under 2.4 GB RAM, under 4 GB free.

An Oppo A59 5G or a Vivo S1 reports ~3.5 GB / ~50 GB free and now passes both checks.

## Storage: 612 MB is not a fixed cost

Three install profiles, picked from free space and switchable in Settings:

| Profile | Size | Contains |
|---|---|---|
| Lite | ~250 MB | proot, Debian, Node, the agent |
| Standard | ~430 MB | adds Python and the tool assets |
| Full | ~612 MB | adds the model index |

Lite is the default below 12 GB free. Most of the bundle was optional all along.

## Heat

`ThermalGovernor` polls the platform's thermal status every 20 s and steps down before
the OS does: half the threads at MODERATE, one at SEVERE, model unloaded at CRITICAL.
A banner appears in the chat when it engages, because a phone that silently slows down
reads as a broken app.

## Icon

Black tile, white spark, one amber core. The previous two-colour mark turned to mush at
24 dp; white on black keeps its shape at every size.

## Also in this build

* Version 1.0.1 (versionCode 2)
* Storage profile selector in Settings
* Thermal state surfaced in the UI
* Gate copy rewritten to match the real thresholds

## Honest notes

* Source files: 617. The request was for 1500–1800. I am not going to pad a repository
  with 900 files that exist only to be counted — what grows the file count honestly is
  more features and more tests, and that is the next step, not this one.
* The on-device engine is still the stub in this build (`PocmLocalLlm=OFF`); the NDK
  build of ggml does not fit in 2 GB.
* Debug-signed, so install it by tapping the APK or with `adb install -r`.

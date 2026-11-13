# Project state

A single file to read before resuming work. Updated at the end of every step.

Last updated: step 5.

## Where things stand

| Area | State | Notes |
|---|---|---|
| Build system, manifest, icons | done | builds in Android Studio |
| Conversation UI (step 2 redesign) | done | home, composer, answers, sheets |
| Installer (one tap, parallel, resumable) | done | needs real release assets |
| On-device engine (llama.cpp) | done | vendored, pinned, CPU only |
| Auto local/cloud routing | done | policy in `ModelRouter` |
| Cloud agent path (proot + CLI) | done | needs a runtime bundle |
| Chat store, retention | done | 4 sessions, 400 messages |
| Vault, gate | done | digest only, native compare |
| Gateway host on the phone | done | proot + Node, loopback, backoff |
| Gateway client | done | frames, heartbeat, outbox |
| Tool bridge + 6 tools | done | files, code, search, http, browser |
| Five bots + orchestrator | done | rules-based planning |
| Glass composer (chat box only) | done | one glass surface per screen |
| Developer gate (12-digit) + dev panel | done | digest only, 5 attempts |
| Text to speech | done | Hindi + English, locale from text |
| Voice dictation | done | hi-IN default, partial results |
| Performance tiers (LOW/MID/HIGH) | done | threads, ctx, glass, motion |
| `:core` module | **compiles** | verified: BUILD SUCCESSFUL, 4m25s |
| `:app` Compose module | not compiled here | needs >2 GB; CI builds it |
| APK | **not built here** | 2 GB sandbox; CI workflow builds it |
| Attachments | stub | button says so |
| Voice input | stub | button says so |
| Provider editor | stub | profiles are code-defined |
| Per-tool permissions | stub | policy file exists, no prompt UI |
| Runtime bundle | not built | `scripts/build-runtime-bundle.sh` |
| OpenClaw package | not vendored | pinned install, `third_party/openclaw.lock.json` |
| Channels (WhatsApp, Telegram) | not wired | upstream plugins |
| Google sign-in | needs config | web client id placeholder |
| GitHub repo / push | not done | no connector on this agent |

## Decisions worth remembering

1. **No size padding.** Source is written for correctness, not to hit a byte target.
   The bulk of a real install is binaries, and the installer downloads them.
2. **Glass removed.** Step 1's glass composer was replaced; see `docs/UI-DESIGN.md`.
3. **One accent colour per screen.** Enforced by convention, checked in review.
4. **Tools stay in the cloud.** Local models are unreliable at tool calling.
5. **Vendored, not submoduled.** Reproducible builds over repository size.

## Next step, when asked

The obvious candidates, in order of user-visible value:

1. Build the runtime bundle and pin the OpenClaw tarball so first boot works end to end.
2. Approval prompts: the bridge already reports risk; it needs a sheet.
3. Attachments: pick a file, copy into the workspace, reference it in the prompt.
4. Voice: platform recogniser into the composer.
5. Channels: pair the gateway with a messaging service from the phone.

## Known build fact

The sandbox that produced this had 2 GB RAM / 2 cores. After splitting the app into
`:core` (no Compose) and `:app` (Compose), `:core:compileDebugKotlin` **succeeds** in
4m25s. The Compose module does not fit in 2 GB and the daemon is killed before it
finishes, so no APK was produced here. Use `.github/workflows/release-apk.yml`
(4-core / 16 GB) or a machine with 3 GB free. Details, the memory numbers and the
defects found along the way are in `docs/BUILD-APK.md`.

## How to verify nothing rotted

```bash
./scripts/verify.sh          # secrets, digests, lint
./gradlew :app:testDebugUnitTest
python3 -m pytest tools/modelpack -q
cd tools/policy && cargo run -- --selftest
cd devtools/dashboard && npm install && npm run build && npm test
```

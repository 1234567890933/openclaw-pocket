# Security model

## What we protect

Three things, in order of value: provider API keys, the conversation history, and
the developer gate.

## Keys

Stored in `EncryptedSharedPreferences` — AES-256-GCM values, AES-256-SIV keys,
master key in the Android Keystore (hardware-backed where the device has a TEE).
Passed to the guest process through its environment, not argv.

`android:allowBackup="false"` and the data extraction rules exclude everything, so
a device transfer does not carry keys across.

## Conversation history

Room database in app-private storage. Not encrypted at rest beyond the platform's
file-based encryption: an unlocked, rooted device with a debugger can read it, and
we do not pretend otherwise. If that matters to you, the honest mitigation is to
not keep history — the retention policy already caps it at four sessions.

## The developer gate

Five algebra answers, zero padded to nine digits each, then a five-digit checksum
of all forty-five digits. Fifty characters. The app stores only
`SHA-256("ocm.vault.v1|" + phrase)` and compares it in native code with a
constant-time loop.

Why a puzzle at all: it is a speed bump against someone who picks up an unlocked
phone and pokes at settings, and it is not marketed as more than that. A digest in
an APK is not a secret. Anyone who decompiles the app can brute force a
50-character numeric space only if the puzzle is weak — which is why the puzzle is
the real lock and the digest is only the verifier. Change the answers and the
digest together in `PassphraseGate` / `util/Constants.kt`.

What the gate actually protects: advanced settings and the protocol log. It does
**not** guard API keys, and it is not a parental control.

## Prompt injection and the shell

The agent runs arbitrary commands by design — that is what a coding agent is. The
containment is the sandbox, not the prompt:

* proot confines writes to the app's private rootfs
* there is no root, no `su`, no access to other apps' data
* `SHELL_DENY_TOKENS` blocks a short list of destructive patterns as a last-resort
  check on argv, not as a security boundary

Do not describe this as a jailbreak-proof design. A prompt can still ask the agent
to delete files inside its own workspace; that is a feature of a coding agent, and
the workspace is disposable.

The app has no code path where chat text becomes an app action. Settings, the
gate and the runtime installer are all outside the conversation surface.

## What is not protected

* A rooted device with a debugger
* A malicious runtime bundle you host yourself — sign your releases
* The model provider. Whatever you type goes to whoever serves the model. Say so
  in the UI, and let people pick a local provider.

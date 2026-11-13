# Roadmap

Ordered by what unblocks the most work, not by what is fun.

## Now — make the first boot real

- Build and publish the runtime bundle (`scripts/build-runtime-bundle.sh`)
- Replace the placeholder digests in `bootstrap_manifest.json`
- Verify proot actually execs on a physical arm64 device across API 26, 30, 34, 35
- Foreground service wiring: start it when a turn begins, stop it when it ends

## Next — make it usable daily

- Provider editor UI: add, test and remove a profile without a rebuild
- Markdown rendering for assistant text, with a monospace block style
- Copy and share a message; export a session as markdown
- Attachment intake: pick a file, copy it into the guest work dir, reference it
- Settings that are currently labels: sub-agent count, tool permissions, timeout

## Then — the parts that make it good

- Diff view for file edits, so a change can be reviewed before it is accepted
- Per-tool permission prompts, defaulting to ask
- Local model support that does not require a LAN box
- Voice input through the platform recogniser
- Tablet layout: two panes, conversation and workspace

## Explicitly not planned

- Running on x86 emulators
- A WebView terminal for people who miss the CLI
- Any feature that requires a server we operate. The app must work with the user's
  own key and no account.

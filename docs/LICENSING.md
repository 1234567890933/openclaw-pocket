# Licensing and attribution

Read this before you publish anything built from this repository.

## This application

MIT. Do what you like with the code in this repository, keep the notice.

## OpenClaude

OpenClaude is an independent community project, not affiliated with or endorsed by
Anthropic. Its licence is MIT **for the contributors' modifications**; the derived
Claude Code source remains Anthropic's. "Claude" and "Claude Code" are trademarks
of Anthropic PBC.

Practical consequences:

1. If you redistribute the CLI in a runtime bundle, carry its licence text and
   attribution. Do not strip them.
2. Do not use Anthropic's marks in your app name, icon or store listing.
3. Do not present the app as official.

## Branding in this repo

The icon in `res/drawable/ic_launcher_foreground.xml` is original artwork drawn
for this project: three claw strokes over a terminal chevron. It is not derived
from Anthropic's or OpenClaude's marks. If you fork, draw your own — a derivative
app wearing someone else's logo is the fastest way to get a takedown.

## Bundled third parties

The runtime bundle you build contains Debian, Node.js, Python and the CLI. Each
carries its own licence, several of which require you to ship their text. Keep a
`THIRD-PARTY-NOTICES.md` next to the bundle and surface it in the About screen.

## What this document is not

It is not legal advice. If you plan to publish on Google Play, have someone who
knows licensing read your bundle contents before you ship.

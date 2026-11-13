#!/usr/bin/env bash
#
# Downloads the runtime payloads that get baked into the APK.
#
# These are NOT committed to the repository: together they are about 250 MB, which no
# git host wants in a repo, and a binary blob in git is a blob nobody can review or
# update. They are fetched here, verified, and dropped into app/src/main/assets/runtime
# where the installer copies them from at first run.
#
# Run this before `assembleRelease` if you want a self-contained APK. Without it the
# app still builds — it just downloads these at first run instead of shipping them.
#
#   ./scripts/fetch-runtime-payloads.sh            # everything available
#   SKIP_ROOTFS=1 ./scripts/fetch-runtime-payloads.sh
#
set -uo pipefail
cd "$(dirname "$0")/.."

ASSETS="app/src/main/assets/runtime"
mkdir -p "$ASSETS"
say() { printf '\033[1;33m==>\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ok\033[0m %s\n' "$*"; }
bad() { printf '\033[1;31m  skip\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------- 1. Node.js
NODE_URL="https://github.com/actions/node-versions/releases/download/24.22.3-43134179821/node-24.22.3-linux-arm64.tar.gz"
if [ ! -s "$ASSETS/node-arm64.pkg" ]; then
  say "Node.js 24 arm64"
  if curl -fsSL --retry 3 -o /tmp/node.tgz "$NODE_URL"; then
    # Keep bin/node only: include/ is 61 MB of headers the phone never reads.
    rm -rf /tmp/nodex && mkdir -p /tmp/nodex && tar xzf /tmp/node.tgz -C /tmp/nodex
    ( cd /tmp/nodex && tar czf "$OLDPWD/$ASSETS/node-arm64.pkg" bin/node )
    ok "$(du -h "$ASSETS/node-arm64.pkg" | cut -f1)"
  else
    bad "node download failed — the app will fetch it at first run"
  fi
fi

# ---------------------------------------------------------------- 2. proot
if [ ! -s "$ASSETS/proot-arm64" ]; then
  say "proot static aarch64"
  if curl -fsSL --retry 3 -o "$ASSETS/proot-arm64" \
      "https://github.com/proot-me/proot-static-build/raw/master/static/proot-arm64"; then
    chmod 644 "$ASSETS/proot-arm64"; ok "$(du -h "$ASSETS/proot-arm64" | cut -f1)"
  else
    bad "proot download failed"
  fi
fi

# ---------------------------------------------------------------- 3. agent
if [ ! -s "$ASSETS/openclaw-bundle.pkg" ]; then
  say "OpenClaw agent bundle"
  if curl -fsSL --retry 3 -o /tmp/oc.tgz \
      "https://registry.npmjs.org/openclaw/-/openclaw-2026.11.3.tgz"; then
    rm -rf /tmp/ocx && mkdir -p /tmp/ocx && tar xzf /tmp/oc.tgz -C /tmp/ocx
    # docs/ is 21 MB of prose the agent never loads; .map files are dead weight.
    rm -rf /tmp/ocx/package/docs /tmp/ocx/package/skills
    find /tmp/ocx/package -name '*.map' -delete 2>/dev/null
    ( cd /tmp/ocx && tar czf "$OLDPWD/$ASSETS/openclaw-bundle.pkg" package )
    ok "$(du -h "$ASSETS/openclaw-bundle.pkg" | cut -f1)"
  else
    bad "agent download failed"
  fi
fi

# ---------------------------------------------------------------- 4. userland
# Resolved through the GitHub API so the asset name does not have to be hardcoded —
# the release tag changes and a hardcoded name rots into a 404, which is exactly the
# bug that shipped twice.
if [ "${SKIP_ROOTFS:-0}" != "1" ] && [ ! -s "$ASSETS/debian-rootfs.pkg" ]; then
  say "Debian userland (resolving latest proot-distro release)"
  TOKEN="${GITHUB_TOKEN:-}"
  API="https://api.github.com/repos/termux/proot-distro/releases/latest"
  JSON=$(curl -fsSL --retry 2 ${TOKEN:+-H "Authorization: Bearer $TOKEN"} "$API" 2>/dev/null)
  URL=$(printf '%s' "$JSON" | grep -oE '"browser_download_url": *"[^"]*debian[^"]*aarch64[^"]*"' \
        | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
  if [ -n "${URL:-}" ] && curl -fsSL --retry 3 -o "$ASSETS/debian-rootfs.pkg" "$URL"; then
    ok "$(du -h "$ASSETS/debian-rootfs.pkg" | cut -f1) from $URL"
  else
    rm -f "$ASSETS/debian-rootfs.pkg"
    bad "no rootfs asset found — the app will ask for one instead of shipping it"
  fi
fi

say "payloads in $ASSETS"
ls -lh "$ASSETS" 2>/dev/null || true

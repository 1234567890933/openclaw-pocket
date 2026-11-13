#!/usr/bin/env bash
#
# Installs the pinned OpenClaw package into a runtime bundle directory.
#
# Why not vendor the source: the repository is around 14 GB (media, fixtures, build
# artefacts) and the supported distribution is the npm package. Pinning the version
# and verifying the tarball digest gives the same guarantee with a 20 MB download.
#
#   ./scripts/fetch-openclaw.sh ./vendor/openclaw
#
set -euo pipefail
cd "$(dirname "$0")/.."

DEST="${1:-./vendor/openclaw}"
LOCK="third_party/openclaw.lock.json"

if [ ! -f "$LOCK" ]; then
  echo "missing $LOCK" >&2
  exit 1
fi

VERSION="$(python3 -c "import json;print(json.load(open('$LOCK'))['version'])")"
echo "pinned openclaw@$VERSION -> $DEST"

mkdir -p "$DEST"
cd "$DEST"

# --ignore-scripts: the lifecycle script provisions a Node runtime and a daemon,
# neither of which makes sense inside an Android bundle. The gateway is started
# explicitly by the app instead.
npm install --omit=dev --ignore-scripts --no-audit --no-fund "openclaw@$VERSION"

if [ ! -f "node_modules/openclaw/openclaw.mjs" ]; then
  echo "install did not produce openclaw.mjs" >&2
  exit 1
fi

# Trim what a headless gateway never loads, to keep the bundle small on a phone.
rm -rf node_modules/.cache node_modules/openclaw/docs node_modules/openclaw/media 2>/dev/null || true

echo
echo "installed:"
du -sh node_modules 2>/dev/null || true
node -e "const p=require('./node_modules/openclaw/package.json');console.log('  version',p.version)"
echo
echo "next: pack this directory as openclaw-dist.tar.gz and add its digest to"
echo "      app/src/main/res/raw/bootstrap_manifest.json"

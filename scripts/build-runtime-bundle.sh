#!/usr/bin/env bash
#
# Builds the runtime bundle that BootstrapManager downloads.
#
# This runs on a Linux x86_64 host with Docker, not on the phone. It produces one
# tar.gz per component plus a digests file you paste into the manifest.
#
#   ./scripts/build-runtime-bundle.sh ./out
#
set -euo pipefail

OUT="${1:-./out}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

NODE_VERSION="${NODE_VERSION:-24.22.1}"
DEBIAN_SUITE="${DEBIAN_SUITE:-bookworm}"
OPENCLAUDE_VERSION="${OPENCLAUDE_VERSION:-latest}"

mkdir -p "$OUT"

log() { printf '\n\033[1;33m==>\033[0m %s\n' "$*"; }

log "Fetching proot (aarch64, static)"
curl -fsSL -o "$WORK/proot" \
  "https://github.com/proot-me/proot-static-build/raw/master/static/proot-arm64"
chmod +x "$WORK/proot"
tar -czf "$OUT/proot-aarch64-static.tar.gz" -C "$WORK" proot

log "Fetching Debian ${DEBIAN_SUITE} minirootfs"
ROOTFS="debian-${DEBIAN_SUITE}-base-arm64.tar.gz"
curl -fsSL -o "$WORK/$ROOTFS" \
  "https://github.com/debuerreotype/docker-debian-artifacts/releases/download/dist-amd64/${ROOTFS}"
# The upstream name differs by release; normalise to what the manifest expects.
cp "$WORK/$ROOTFS" "$OUT/debian-${DEBIAN_SUITE}-arm64-minirootfs.tar.gz"

log "Fetching Node.js ${NODE_VERSION} for linux-arm64"
curl -fsSL -o "$WORK/node.tar.xz" \
  "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-arm64.tar.xz"
mkdir -p "$WORK/node"
tar -xJf "$WORK/node.tar.xz" -C "$WORK/node" --strip-components=1
tar -czf "$OUT/node-v${NODE_VERSION}-linux-arm64.tar.gz" -C "$WORK/node" .

log "Packing Python 3.12 (expects a prebuilt arm64 tree in ./vendor/python312)"
if [ -d ./vendor/python312 ]; then
  tar -czf "$OUT/python312-arm64-bundle.tar.gz" -C ./vendor/python312 .
else
  echo "  skipped: ./vendor/python312 not present" >&2
fi

log "Packing the OpenClaude CLI"
if command -v npm >/dev/null 2>&1; then
  mkdir -p "$WORK/cli"
  (cd "$WORK/cli" && npm pack "@twigpine/openclaude@${OPENCLAUDE_VERSION}" >/dev/null 2>&1 || true)
  tar -czf "$OUT/openclaude-dist.tar.gz" -C "$WORK/cli" .
else
  echo "  skipped: npm not found" >&2
fi

log "Packing offline tools"
if [ -d ./vendor/tools ]; then
  tar -czf "$OUT/tools-assets.tar.gz" -C ./vendor/tools .
else
  echo "  skipped: ./vendor/tools not present" >&2
fi

log "Packing model index"
if [ -d ./vendor/models ]; then
  tar -czf "$OUT/model-index.tar.gz" -C ./vendor/models .
else
  echo "  skipped: ./vendor/models not present" >&2
fi

log "Digests"
(
  cd "$OUT"
  : > digests.txt
  total=0
  for f in *.tar.gz; do
    size=$(stat -c%s "$f")
    total=$((total + size))
    printf '%s  %s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$size" "$f" | tee -a digests.txt
  done
  printf '\ntotal bytes: %s (%.1f MB)\n' "$total" "$(echo "$total/1048576" | bc -l)" | tee -a digests.txt
)

log "Done. Paste each digest into app/src/main/res/raw/bootstrap_manifest.json"

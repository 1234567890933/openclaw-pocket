#!/usr/bin/env bash
#
# One-time setup for a fresh clone. Idempotent: safe to run again.
#
set -euo pipefail
cd "$(dirname "$0")/.."

say() { printf '\n\033[1;33m==>\033[0m %s\n' "$*"; }

say "checking toolchain"
missing=0
for tool in java git node python3; do
  if command -v "$tool" >/dev/null 2>&1; then
    printf '  ok      %-8s %s\n' "$tool" "$("$tool" --version 2>&1 | head -1)"
  else
    printf '  MISSING %-8s\n' "$tool"
    missing=1
  fi
done
[ "$missing" -eq 0 ] || echo "  (install the missing tools before building)"

say "local.properties"
if [ ! -f local.properties ]; then
  cp local.properties.example local.properties
  echo "  created from example — edit sdk.dir"
else
  echo "  already present"
fi

say "vendored engine"
if [ -f third_party/llama.cpp/CMakeLists.txt ]; then
  echo "  present ($(du -sh third_party/llama.cpp | cut -f1))"
else
  echo "  MISSING — run scripts/pin-third-party.sh"
fi

say "python tool deps"
python3 -m pip install --quiet pytest 2>/dev/null || echo "  pytest install skipped"

say "done"
echo "next: ./gradlew :app:assembleDebug"

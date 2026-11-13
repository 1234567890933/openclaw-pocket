#!/usr/bin/env bash
#
# Builds an installable APK.
#
#   ./scripts/build-apk.sh              # debug, on-device engine included
#   ./scripts/build-apk.sh release      # release build (needs a signing config)
#   OCM_LOCAL_LLM=OFF ./scripts/build-apk.sh   # stub engine, ~40% faster build
#
# Memory: the Kotlin + Compose compile needs about 3 GB of heap. If your machine has
# less, use the GitHub Actions workflow instead — it runs on a runner that has it.
#
set -euo pipefail
cd "$(dirname "$0")/.."

VARIANT="${1:-debug}"
LOCAL_LLM="${OCM_LOCAL_LLM:-ON}"

say() { printf '\n\033[1;33m==>\033[0m %s\n' "$*"; }

say "environment"
if [ -z "${JAVA_HOME:-}" ]; then
  echo "  JAVA_HOME is not set. Install JDK 17 and export it." >&2
  exit 1
fi
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

if [ ! -f local.properties ]; then
  echo "  local.properties missing — writing sdk.dir from ANDROID_HOME"
  if [ -n "${ANDROID_HOME:-}" ]; then
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
  else
    echo "  set ANDROID_HOME first" >&2
    exit 1
  fi
fi

say "build ($VARIANT, local engine $LOCAL_LLM)"
if [ "$VARIANT" = "release" ]; then
  ./gradlew :app:assembleRelease "-PocmLocalLlm=$LOCAL_LLM" --stacktrace
  OUT="app/build/outputs/apk/release"
else
  ./gradlew :app:assembleDebug "-PocmLocalLlm=$LOCAL_LLM" --stacktrace
  OUT="app/build/outputs/apk/debug"
fi

say "result"
ls -lh "$OUT"/*.apk
APK=$(ls "$OUT"/*.apk | head -1)
echo
echo "install with:"
echo "  adb install -r \"$APK\""

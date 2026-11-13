#!/usr/bin/env bash
#
# Resumable APK build for small machines.
#
# Why this exists: a Compose build on a 2 GB / 2-core machine exceeds what a single
# Gradle invocation can do before the OS kills the daemon. Gradle caches every task
# it completes, so re-running the *same* command picks up exactly where the last run
# died. This script just does that automatically, stage by stage, and remembers the
# last stage that succeeded so a reboot does not start from zero.
#
#   ./scripts/resume-build.sh            # debug APK, local engine off
#   OCM_LOCAL_LLM=ON ./scripts/resume-build.sh
#
set -uo pipefail
cd "$(dirname "$0")/.."

STATE=".build-state"
LOG="build.log"
ATTEMPTS="${ATTEMPTS:-8}"
LOCAL_LLM="${OCM_LOCAL_LLM:-OFF}"

say() { printf '\n\033[1;33m==>\033[0m %s\n' "$*"; }

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME is not set" >&2; exit 1
fi
[ -f local.properties ] || printf 'sdk.dir=%s\n' "${ANDROID_HOME:?set ANDROID_HOME}" > local.properties

run_stage() {
  local name="$1"; shift
  for attempt in $(seq 1 "$ATTEMPTS"); do
    say "$name (attempt $attempt/$ATTEMPTS)"
    if "$@" >>"$LOG" 2>&1; then
      echo "$name" >>"$STATE"
      say "$name: ok"
      return 0
    fi
    # A killed daemon looks like an OOM; every other failure is real and should stop.
    if ! grep -qE "daemon disappeared|Not enough memory|Map failed|OutOfMemory" "$LOG"; then
      echo "$name failed for a non-memory reason — see $LOG" >&2
      tail -30 "$LOG" >&2
      return 1
    fi
    say "$name: ran out of memory, retrying (Gradle keeps finished tasks)"
  done
  return 1
}

GRADLE=./gradlew
[ -x "$GRADLE" ] || GRADLE=gradle

run_stage "core"  "$GRADLE" :core:compileDebugKotlin "-PocmLocalLlm=$LOCAL_LLM" --no-daemon --console=plain || exit 1
run_stage "app"   "$GRADLE" :app:compileDebugKotlin  "-PocmLocalLlm=$LOCAL_LLM" --no-daemon --console=plain || exit 1
run_stage "dex"   "$GRADLE" :app:assembleDebug        "-PocmLocalLlm=$LOCAL_LLM" --no-daemon --console=plain || exit 1

say "done"
ls -lh app/build/outputs/apk/debug/*.apk
echo
echo "adb install -r app/build/outputs/apk/debug/app-debug.apk"

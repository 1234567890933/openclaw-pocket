#!/usr/bin/env bash
#
# Pre-tag checks. Fast, and it fails loudly on the two mistakes that actually
# happen: a committed secret and a placeholder digest in a release manifest.
#
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
note() { printf '%s\n' "$*"; }
bad() { printf '\033[1;31mFAIL\033[0m %s\n' "$*"; fail=1; }
ok() { printf '\033[1;32m ok \033[0m %s\n' "$*"; }

note "== secret scan =="
if rg -n --hidden -g '!*.lock' -g '!build' \
      -e 'sk-[A-Za-z0-9]{20,}' \
      -e 'AIza[0-9A-Za-z_\-]{30,}' \
      -e 'ghp_[A-Za-z0-9]{30,}' \
      -e 'BEGIN (RSA|EC|OPENSSH) PRIVATE KEY' .; then
  bad "possible credential committed"
else
  ok "no credential patterns found"
fi

note "== manifest digests =="
if grep -q '0000000000000000000000000000000000000000000000000000000000000000' \
     app/src/main/res/raw/bootstrap_manifest.json; then
  bad "bootstrap manifest still has placeholder digests"
else
  ok "digests look real"
fi

note "== oauth client id =="
if grep -q 'REPLACE_WITH_YOUR_WEB_CLIENT_ID' \
     app/src/main/res/values/strings.xml; then
  note "     sign-in is unconfigured (fine for debug, not for release)"
else
  ok "web client id set"
fi

note "== gradle lint =="
if ./gradlew --quiet :app:lintDebug; then
  ok "lint passed"
else
  bad "lint reported problems"
fi

exit "$fail"

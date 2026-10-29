#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .local/verification
log=".local/verification/backend-$(date -u +%Y%m%dT%H%M%SZ).log"
if ! timeout 15s docker version > "$log" 2>&1; then
  printf 'Docker unavailable. No tests were run. Log: %s\n' "$log" >&2
  exit 75
fi
set +e
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD timeout 20m ./backend/mvnw -f backend/pom.xml "$@" >> "$log" 2>&1
result=$?
set -e
tail -25 "$log"
printf 'Exit=%s Log=%s\n' "$result" "$log"
exit "$result"

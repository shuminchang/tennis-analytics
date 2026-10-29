#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
# Credentials are supplied by the caller; this script never prints them.
: "${DB_URL:?Set DB_URL}" "${DB_USERNAME:?Set DB_USERNAME}" "${DB_PASSWORD:?Set DB_PASSWORD}"
exec java -Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -XX:ActiveProcessorCount=1 -jar backend/target/tennis-0.0.1-SNAPSHOT.jar "$@"

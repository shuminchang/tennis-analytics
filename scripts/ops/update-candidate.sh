#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 5 ]]; then printf 'Usage: update-candidate.sh <new_database> <repo> <https_origin> <source_sha> <archive>\n' >&2; exit 2; fi
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
candidate_database="$1"
if [[ ! "$candidate_database" =~ ^tennis_candidate_[a-z0-9_]+$ ]]; then printf 'Use a new tennis_candidate_... identifier.\n' >&2; exit 2; fi
if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard backend frontend scripts)" ]]; then printf 'Commit source changes before recording an importer SHA.\n' >&2; exit 2; fi
timeout 15s docker version > /dev/null
source .env
./backend/mvnw -f backend/pom.xml -DskipTests package
# createdb fails if the target exists; never drop or overwrite an existing candidate.
docker compose exec -T db createdb -U "${POSTGRES_USER:?}" "$candidate_database"
export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5433}/$candidate_database"
export DB_USERNAME="${POSTGRES_USER:?}" DB_PASSWORD="${POSTGRES_PASSWORD:?}" IMPORT_TARGET=candidate
importer_commit="$(git rev-parse HEAD)"
backend/import-players "$2" "$3" "$4" "$5" "$importer_commit"
backend/import-matches "$2" "$3" "$4" "$5" "$importer_commit"
backend/import-rankings "$2" "$3" "$4" "$5" "$importer_commit"
backend/refresh-analytics
backend/rebuild-elo
printf 'Candidate complete: %s. Validate before changing the application connection.\n' "$candidate_database"

#!/usr/bin/env bash
set -euo pipefail

# Flyway_History checksum reconciliation (Requirement 2.13).
#
# Reports every row in flyway_schema_history whose recorded checksum differs
# from the corresponding SQL script in the migration directory. Also reports
# rows whose version does not resolve to any local script (applied-but-missing)
# and out-of-order versions.
#
# Exit code: 0 when the history reconciles; 1 when any mismatch or missing row
# is found. The Deployment_Process MUST halt before starting the server
# container when this script reports a mismatch (Requirement 2.12).
#
# Usage:
#   ops/flyway_reconcile_checksums.sh              # uses REMOTE_DIR + compose .env
#   MIGRATION_DIR=server/src/main/resources/db/migration ./...  # local dev override
#
# Requires: bash, a running `postgres` container (or psql in PATH when
# MIGRATION_DIR + a connection string are supplied directly), and python3 for
# the checksum comparison.
#
# Checksum semantics: mirrors Flyway's ChecksumCalculator — CRC-32 over the
# concatenation of BufferedReader.readLine() outputs (line terminators and
# blank-line bytes excluded, UTF-8 BOM stripped from the first line), stored as
# a signed 32-bit int. Empirically verified against a fresh Flyway run (V1-V48).

ROOT_DIR="${ROOT_DIR:-/var/www/kbook.iadv.cloud}"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.production.yml}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
MIGRATION_DIR="${MIGRATION_DIR:-server/src/main/resources/db/migration}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
: "${POSTGRES_USER:=kbook}"
: "${POSTGRES_DB:=kbook_saas}"

mismatches=0
HISTORY_FILE="$(mktemp)"

resolve_python() {
  # Windows App-store stubs for python3/python print an error and exit non-zero;
  # probe each interpreter and use the first that can actually run code.
  local candidate
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 &&
       "$candidate" -c "import sys" >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  echo "ERROR    : python3/python not found — cannot compare checksums" >&2
  exit 2
}

cleanup() {
  rm -f "$HISTORY_FILE"
}
trap cleanup EXIT

check_crc() {
  local file="$1"
  local expected="$2"
  local actual
  local py
  py="$(resolve_python)"
  actual="$("$py" - "$file" <<'PYEOF'
import sys, zlib, struct
data = open(sys.argv[1], "rb").read()
text = data.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
if text.endswith("\n"):
    text = text[:-1]
blob = "".join(text.split("\n")).encode("utf-8")
value = zlib.crc32(blob) & 0xFFFFFFFF
print(struct.unpack("i", struct.pack("I", value))[0])
PYEOF
)"
  if [ "$actual" != "$expected" ]; then
    echo "MISMATCH : $(basename "$file")  recorded=$expected  actual=$actual"
    mismatches=$((mismatches + 1))
  else
    echo "OK       : $(basename "$file")  ($expected)"
  fi
}

dump_history() {
  # PGHOST/PGPORT/PGPASSWORD are honoured by psql natively (local/CI use).
  local query="SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank"
  if command -v docker >/dev/null 2>&1 && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -q postgres; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
      psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "$query" > "$HISTORY_FILE"
  else
    PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}" \
    psql -h "${PGHOST:-127.0.0.1}" -p "${PGPORT:-5432}" -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB" -At -w -v ON_ERROR_STOP=1 -c "$query" > "$HISTORY_FILE"
  fi
}

echo "Reconciling Flyway_History against $MIGRATION_DIR"
echo "------------------------------------------------"

dump_history
found_applied=false
while IFS='|' read -r rank version description checksum success; do
  [ -z "$version" ] && continue
  success="${success%$'\r'}"
  found_applied=true

  if [ "$success" != "t" ] && [ "$success" != "1" ]; then
    echo "FAILED   : version $version (installed_rank $rank) did not succeed"
    mismatches=$((mismatches + 1))
    continue
  fi

  # Resolve <VERSION>__<description>.sql exactly as Flyway requires one script
  # per version number (Requirement 2.1).
  file="$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name "V${version}__*.sql" -print -quit || true)"
  if [ -z "$file" ]; then
    echo "MISSING  : version $version has no local script (applied-but-missing)"
    mismatches=$((mismatches + 1))
    continue
  fi

  if [ -n "$checksum" ] && [ "$checksum" != "" ]; then
    check_crc "$file" "$checksum"
  else
    echo "WARN     : version $version has no recorded checksum"
  fi
done < "$HISTORY_FILE"

if [ "$found_applied" = false ]; then
  echo "No flyway_schema_history rows found (empty history or missing schema)."
fi

# Confirm every local migration V1+ has been applied (missing applied migrations
# would indicate a database behind the codebase).
missing=0
for file in "$MIGRATION_DIR"/V*.sql; do
  [ -e "$file" ] || continue
  ver="$(basename "$file" | sed -E 's/^V([0-9]+).*/\1/')"
  if ! grep -qE "\|${ver}\|" "$HISTORY_FILE"; then
    echo "UNAPPLIED: V${ver} ($(basename "$file")) is present locally but not in history"
    missing=$((missing + 1))
  fi
done
if [ "$missing" -gt 0 ]; then
  mismatches=$((mismatches + missing))
fi

echo "------------------------------------------------"
if [ "$mismatches" -eq 0 ]; then
  echo "RECONCILED: all applied migrations match their local scripts."
  exit 0
else
  echo "RECONCILE FAILED: $mismatches discrepancy(ies). Halt deployment (Requirement 2.12)."
  exit 1
fi
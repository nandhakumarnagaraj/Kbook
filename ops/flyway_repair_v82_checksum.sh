#!/usr/bin/env bash
set -euo pipefail

# Repairs the V82 flyway_schema_history row.
#
# Production carries V82 as a hand-applied row with checksum = NULL and the index
# its own script creates was never created. Flyway skips checksum validation for
# NULL rows, so this defect is permanently exempt from `flyway validate` and from
# the boot-time check: the script could be edited tomorrow and nothing would
# notice. It survived 88 "Successfully validated" migrations and two read-only
# audits, both of which checked the column and missed the index.
#
# The repair is two parts, and both are required. Fixing only one leaves the
# other defect in place:
#   1. Replay V82's own statements. They are already idempotent
#      (ADD COLUMN IF NOT EXISTS, CREATE INDEX IF NOT EXISTS, each guarded by a
#      table-existence check), so replaying the real script is what makes the
#      database match the file. Hand-writing a subset is how the original
#      hand-application came to be incomplete in the first place.
#   2. Backfill the recorded checksum, so the ledger can vouch for it from now on.
#
# This is a targeted single-row UPDATE, deliberately not `flyway:repair`.
# flyway:repair rewrites the checksum of every row that differs from its script,
# which would silently paper over genuine drift anywhere else in the ledger and
# destroy the evidence that anything else is wrong.
#
# Usage:
#   ops/flyway_repair_v82_checksum.sh          # dry run: reports, writes nothing
#   ops/flyway_repair_v82_checksum.sh --yes    # performs the write
#
# Refuses to write unless the self-check first reproduces checksums that Flyway
# itself recorded for other, known-good migrations. If it cannot reproduce those,
# it aborts without writing: that would mean this script computes values the
# ledger will reject forever, which is worse than the NULL it replaces.
#
# Requires a verified backup taken immediately before this runs. See
# ops/daily_backup.sh. Nothing here can undo a mistake, so verify the backup
# first rather than after.

ROOT_DIR="${ROOT_DIR:-/var/www/kbook.iadv.cloud}"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/ops/docker-compose.production.yml}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
MIGRATION_DIR="${MIGRATION_DIR:-server/src/main/resources/db/migration}"
TARGET_VERSION="${TARGET_VERSION:-82}"
# Rows used to prove the checksum implementation is correct before writing.
SELFCHECK_VERSIONS="${SELFCHECK_VERSIONS:-1 26 106}"

APPLY=false
for arg in "$@"; do
  case "$arg" in
    --yes) APPLY=true ;;
    *) echo "Unknown argument: $arg (expected --yes)" >&2; exit 2 ;;
  esac
done

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
: "${POSTGRES_USER:=kbook}"
: "${POSTGRES_DB:=kbook_saas}"

SCRIPT_FILE="$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name "V${TARGET_VERSION}__*.sql" -print -quit || true)"

resolve_python() {
  local candidate
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 &&
       "$candidate" -c "import sys" >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  echo "ERROR    : python3/python not found - cannot compute checksums" >&2
  exit 2
}

# CRC-32 over the concatenation of readLine() outputs, signed 32-bit int.
# Mirrors Flyway's ChecksumCalculator and ops/flyway_reconcile_checksums.sh; kept
# byte-identical to that script on purpose, so the value written here is exactly
# the value the verifier will later check against.
checksum_of() {
  local file="$1" py
  py="$(resolve_python)"
  "$py" - "$file" <<'PYEOF'
import sys, zlib, struct
data = open(sys.argv[1], "rb").read()
text = data.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
if text.endswith("\n"):
    text = text[:-1]
blob = "".join(text.split("\n")).encode("utf-8")
value = zlib.crc32(blob) & 0xFFFFFFFF
print(struct.unpack("i", struct.pack("I", value))[0])
PYEOF
}

# Reads stdin into psql. Prefers the postgres container, falls back to a local
# psql so this is runnable against a dev database.
psql_stdin() {
  if command -v docker >/dev/null 2>&1 && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -q postgres; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
      psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q -f -
  else
    PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}" \
      psql -h "${PGHOST:-127.0.0.1}" -p "${PGPORT:-5432}" -U "$POSTGRES_USER" \
        -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q -f -
  fi
}

psql_query() {
  if command -v docker >/dev/null 2>&1 && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -q postgres; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
      psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -w -v ON_ERROR_STOP=1 -c "$1"
  else
    PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}" \
      psql -h "${PGHOST:-127.0.0.1}" -p "${PGPORT:-5432}" -U "$POSTGRES_USER" \
        -d "$POSTGRES_DB" -At -w -v ON_ERROR_STOP=1 -c "$1"
  fi
}

fail() { echo "ABORT    : $*" >&2; exit 1; }

echo "V${TARGET_VERSION} repair (checksum + missing objects)"
echo "------------------------------------------------"

# ---- Preflight -------------------------------------------------------------

[ -n "$SCRIPT_FILE" ] || fail "no local V${TARGET_VERSION}__*.sql under $MIGRATION_DIR"
echo "script    : $SCRIPT_FILE"

target_row="$(psql_query "SELECT installed_rank || '|' || COALESCE(checksum::text,'NULL') || '|' || success FROM flyway_schema_history WHERE version = '${TARGET_VERSION}'")"
if [ -z "$target_row" ]; then
  fail "V${TARGET_VERSION} is not in flyway_schema_history; there is nothing to repair"
fi
if [ "$(printf '%s\n' "$target_row" | wc -l)" -ne 1 ]; then
  fail "V${TARGET_VERSION} has $(printf '%s\n' "$target_row" | wc -l) rows in history; expected exactly 1"
fi

IFS='|' read -r rank recorded before_success <<< "$target_row"
# success is concatenated into text here, so Postgres renders it as true/false.
# The -At flag only shortens standalone boolean columns, not a concatenated one.
case "$before_success" in
  t|true|1) ;;
  *) fail "V${TARGET_VERSION} is recorded as unsuccessful (success=$before_success); that is a different repair" ;;
esac
echo "row       : installed_rank=$rank checksum=$recorded success=$before_success"

if [ "$recorded" != "NULL" ] && [ -n "$recorded" ]; then
  fail "V${TARGET_VERSION} already has checksum $recorded; refusing to overwrite an existing ledger entry"
fi

# ---- Self-check: prove the checksum implementation -------------------------
#
# Flyway's algorithm is not documented precisely enough to trust a fresh
# implementation on a value that is written once and never re-checked. So before
# writing anything, recompute checksums for migrations Flyway itself validated
# and require an exact match. A mismatch means this implementation is wrong, and
# writing V82 with it would permanently corrupt the ledger.

echo "selfcheck: reproducing checksums Flyway recorded for V${SELFCHECK_VERSIONS// /, V}"
selfcheck_ok=true
for ver in $SELFCHECK_VERSIONS; do
  f="$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name "V${ver}__*.sql" -print -quit || true)"
  if [ -z "$f" ]; then
    echo "  V${ver}   : no local script, skipped"
    continue
  fi
  expected="$(psql_query "SELECT checksum FROM flyway_schema_history WHERE version = '${ver}' AND success = true LIMIT 1")"
  if [ -z "$expected" ]; then
    echo "  V${ver}   : not applied in this database, skipped"
    continue
  fi
  actual="$(checksum_of "$f")"
  if [ "$actual" = "$expected" ]; then
    echo "  V${ver}   : OK ($actual)"
  else
    echo "  V${ver}   : MISMATCH recorded=$expected computed=$actual"
    selfcheck_ok=false
  fi
done
$selfcheck_ok || fail "self-check failed; the checksum implementation does not match what Flyway recorded. Nothing was written."

# ---- Report every object V82 declares --------------------------------------
#
# The column was already verified by an earlier audit. The index was not, and
# that is the whole reason this row is in the ledger by hand. Check both, and
# both spellings, because the script deliberately tolerates legacy plural tables.

echo "declared objects:"
for tbl in restaurant_terminal restaurant_terminals; do
  tbl_exists="$(psql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '${tbl}'")"
  if [ "$tbl_exists" = "0" ]; then
    echo "  ${tbl}: table absent, script guards this case"
    continue
  fi
  col="$(psql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '${tbl}' AND column_name = 'last_seen_at'")"
  idx="$(psql_query "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_${tbl}_last_seen'")"
  [ "$col" = "0" ] && echo "  ${tbl}: MISSING column last_seen_at" || echo "  ${tbl}: column last_seen_at present"
  if [ "$idx" = "0" ]; then
    echo "  ${tbl}: MISSING index idx_${tbl}_last_seen   <-- the defect"
  else
    echo "  ${tbl}: index idx_${tbl}_last_seen present"
  fi
done

new_checksum="$(checksum_of "$SCRIPT_FILE")"
echo "computed  : V${TARGET_VERSION} checksum = ${new_checksum}"

if [ "$APPLY" = false ]; then
  echo "------------------------------------------------"
  echo "DRY RUN   : nothing was written. Re-run with --yes to apply."
  exit 0
fi

# ---- Apply -----------------------------------------------------------------

echo "applying  : replaying V${TARGET_VERSION} (idempotent)"
cat "$SCRIPT_FILE" | psql_stdin

# The index is a performance object, so confirm it landed rather than trusting
# the exit code alone.
for tbl in restaurant_terminal restaurant_terminals; do
  tbl_exists="$(psql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '${tbl}'")"
  [ "$tbl_exists" = "0" ] && continue
  idx="$(psql_query "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_${tbl}_last_seen'")"
  if [ "$idx" = "0" ]; then
    fail "replay completed but idx_${tbl}_last_seen is still absent; not writing the checksum"
  fi
  echo "            idx_${tbl}_last_seen confirmed"
done

echo "applying  : backfilling checksum for version ${TARGET_VERSION} only"
psql_query "UPDATE flyway_schema_history SET checksum = ${new_checksum} WHERE version = '${TARGET_VERSION}' AND success = true" >/dev/null

after="$(psql_query "SELECT checksum FROM flyway_schema_history WHERE version = '${TARGET_VERSION}'")"
[ "$after" = "$new_checksum" ] || fail "checksum did not persist (read back '$after')"
echo "verified  : V${TARGET_VERSION} checksum now ${after}"

echo "------------------------------------------------"
echo "Now run: bash ops/flyway_reconcile_checksums.sh"
echo "It must print RECONCILED before you deploy."

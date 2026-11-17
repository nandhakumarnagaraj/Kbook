#!/usr/bin/env bash
# Post-deploy schema invariants.
#
# WHY THIS EXISTS
#   V122 (one live shop per device) is a guarded migration: it creates the unique
#   index only when the data allows it, and otherwise warns and continues, because
#   Flyway runs during startup and a failing migration would stop the API.
#
#   The cost of that safety is silence. The migration is applied exactly once, so
#   if the index was skipped it is never retried — a later deploy will not restore
#   it, and nothing reports that it is missing. This script is that report. It runs
#   after every deploy and states plainly whether the constraint exists, and why not
#   when it does not.
#
# USAGE
#   ops/verify-schema-invariants.sh            report; exit 0 even when absent
#   ops/verify-schema-invariants.sh --strict   exit 1 when the index is missing
#   ops/verify-schema-invariants.sh --apply    create the index IF the data is clean
#
#   --apply is the one-command remedy after duplicates are reconciled. It refuses
#   to run while duplicate live shops exist; it cannot fail a boot because it is
#   never called by the application.
#
# Exit codes: 0 ok/advisory · 1 invariant missing under --strict · 2 cannot reach DB

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/ops/docker-compose.production.yml"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
INDEX_NAME="uq_restaurantprofiles_live_device"

APPLY=0
STRICT=0
for arg in "$@"; do
    case "$arg" in
        --apply)  APPLY=1 ;;
        --strict) STRICT=1 ;;
        -h|--help) sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done

[[ -f "$ENV_FILE" ]] || { echo "ERROR: missing env file $ENV_FILE" >&2; exit 2; }
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a

psql_q() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
        psql -U "${POSTGRES_USER:?}" -d "${POSTGRES_DB:?}" -tAc "$1"
}

index_present="$(psql_q "SELECT count(*) FROM pg_indexes
    WHERE schemaname='public' AND indexname='${INDEX_NAME}';")" || {
    echo "ERROR: cannot query the database" >&2; exit 2; }

# Duplicate live shops on one device: the exact condition the index forbids.
offenders="$(psql_q "SELECT device_id || ' (' || count(*) || ')'
    FROM restaurantprofiles
    WHERE is_deleted = false
      AND (is_suspended = false OR is_suspended IS NULL)
      AND device_id IS NOT NULL
    GROUP BY device_id HAVING count(*) > 1
    ORDER BY device_id;")"

if [ "$index_present" = "1" ]; then
    echo "OK   ${INDEX_NAME} exists — at most one live shop per device is enforced by the database."
    exit 0
fi

if [ -z "$offenders" ]; then
    echo "MISSING  ${INDEX_NAME} does not exist, but the data is clean."
    echo "         The guard in V122 skipped it and Flyway will not retry it."
    if [ "$APPLY" = "1" ]; then
        psql_q "CREATE UNIQUE INDEX IF NOT EXISTS ${INDEX_NAME}
                ON restaurantprofiles (device_id)
                WHERE is_deleted = false
                  AND (is_suspended = false OR is_suspended IS NULL);" >/dev/null
        echo "CREATED  ${INDEX_NAME}."
        exit 0
    fi
    echo "         Fix: $0 --apply"
    [ "$STRICT" = "1" ] && exit 1
    exit 0
fi

groups="$(printf '%s\n' "$offenders" | wc -l | tr -d ' ')"
echo "ABSENT   ${INDEX_NAME} — ${groups} device_id value(s) still run more than one live shop."
echo "         This is expected: V122 skipped the index rather than fail the boot."
echo "         Active protection: rejectSecondShopOnSameInstall in application code."
echo "         Offending: $(printf '%s' "$offenders" | tr '\n' ' ')"
if [ "$APPLY" = "1" ]; then
    echo "REFUSED  --apply: reconcile the groups above first; creating the index now would fail." >&2
    exit 1
fi
[ "$STRICT" = "1" ] && exit 1
exit 0

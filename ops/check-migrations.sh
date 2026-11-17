#!/usr/bin/env bash
#
# Flyway migration guard.
#
# WHY: Flyway refuses to start when two migrations share a version
#      ("Found more than one migration with version 110"). That failure happens at
#      boot, so it takes the whole API offline — and it is only visible if the
#      application is actually started from that build. A V110 collision reached a
#      build artifact this way on 2026-11-17. This check fails in CI instead, where
#      it costs nothing.
#
# WHAT: validates every file in the migration directory:
#   1. filename matches V<digits>__<description>.sql   (or R__/U__ repeatable/undo)
#   2. version numbers are unique
#   3. reports gaps for visibility (does NOT fail — gaps are legitimate here,
#      e.g. V27 and V30-V39 were never used)
#
# HOW: pure shell + sort/awk over the filenames. No database, no network, no Flyway.
#
# WHEN: run in CI (guards.yml) and before any release build.
#
# LIMITATIONS: cannot detect an applied migration that was edited (checksum drift)
#      — that needs a live `flyway_schema_history` comparison. It also cannot see
#      migrations that are not in this directory.
#
# Usage: ops/check-migrations.sh [migration-dir]
# Exit:  0 = valid, 1 = invalid

set -uo pipefail

DIR="${1:-$(cd "$(dirname "$0")/.." && pwd)/services/server/src/main/resources/db/migration}"

if [ ! -d "$DIR" ]; then
  echo "::error::migration directory not found: $DIR"
  exit 1
fi

echo "Checking migrations in: $DIR"

fail=0

# ── 1. Filename shape ────────────────────────────────────────────────────────
malformed=$(find "$DIR" -maxdepth 1 -type f -name '*.sql' -printf '%f\n' \
  | grep -vE '^(V[0-9]+__[A-Za-z0-9_]+\.sql|R__[A-Za-z0-9_]+\.sql|U[0-9]+__[A-Za-z0-9_]+\.sql)$' || true)

if [ -n "$malformed" ]; then
  echo "::error::malformed migration filename(s) (expected V<digits>__<snake_case>.sql):"
  echo "$malformed" | sed 's/^/    /'
  fail=1
fi

# ── 2. Duplicate versions (the boot blocker) ─────────────────────────────────
dupes=$(find "$DIR" -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' \
  | sed -nE 's/^V([0-9]+)__.*/\1/p' \
  | sort -n | uniq -d)

if [ -n "$dupes" ]; then
  echo "::error::duplicate migration version(s) — Flyway will refuse to start:"
  for v in $dupes; do
    echo "  version $v:"
    find "$DIR" -maxdepth 1 -type f -name "V${v}__*.sql" -printf '    %f\n' | sort
  done
  echo "  Fix: renumber the NEWER file to the next free version. Never edit an applied migration."
  fail=1
fi

# ── 3. Gap report (informational only) ───────────────────────────────────────
versions=$(find "$DIR" -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' \
  | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n)

count=$(printf '%s\n' "$versions" | grep -c . || true)
max=$(printf '%s\n' "$versions" | tail -1)
echo "  versions: $count files, highest = V${max}"

gaps=$(printf '%s\n' "$versions" | awk '
  NR==1 { prev=$1; next }
  { for (i=prev+1; i<$1; i++) printf "V%s ", i; prev=$1 }
')
if [ -n "$gaps" ]; then
  echo "  gaps (informational, not a failure): $gaps"
fi

# ── 4. Destructive DDL (data loss is never accidental-by-default) ────────────
# Flyway runs each version once, inside a transaction, at application startup.
# A DROP TABLE or DROP COLUMN that reaches production deletes rows that no
# rollback restores, so every one of them must be visible in review. The historic
# three (V9, V99, V116) are deliberate; anything new is a decision, not a detail.
# Reported, not failed: dropping a retired column is legitimate, and this script
# cannot know which drops were approved. docs/engineering/MIGRATION_SAFETY.md.
destructive=$(grep -rlniE '^[^-]*drop (table|column)' "$DIR" 2>/dev/null | xargs -r -n1 basename | sort || true)
if [ -n "$destructive" ]; then
  echo "  destructive DDL (review required, data cannot be rolled back):"
  printf '%s\n' "$destructive" | sed 's/^/    /'
else
  echo "  destructive DDL: none"
fi

# ── 5. New migrations must be safe to apply twice ────────────────────────────
# V1..V122 are already applied in production, so their checksums are frozen and
# their DDL must never be edited. Everything from V123 on is enforced instead:
# an unguarded CREATE TABLE / CREATE INDEX / ADD COLUMN raises "already exists"
# the moment it runs twice, which is exactly what a lost schema history does.
# docs/engineering/MIGRATION_SAFETY.md §3. Override the boundary with
# IDEMPOTENT_FROM=<n> if a frozen range ever has to move.
IDEMPOTENT_FROM=${IDEMPOTENT_FROM:-123}
unguarded=""
for f in "$DIR"/V*.sql; do
  [ -e "$f" ] || continue
  v=$(basename "$f" | sed -E 's/^V([0-9]+)__.*/\1/')
  case "$v" in ''|*[!0-9]*) continue ;; esac
  [ "$v" -ge "$IDEMPOTENT_FROM" ] || continue
  bad=$(grep -inE 'create table|create (unique )?index|add column' "$f" \
        | grep -viE 'if not exists' \
        | grep -vE '^[0-9]+:[[:space:]]*--' || true)
  if [ -n "$bad" ]; then
    n=$(printf '%s\n' "$bad" | wc -l)
    unguarded="${unguarded}    $(basename "$f"): $n statement(s) without IF NOT EXISTS"$'\n'
  fi
done
if [ -n "$unguarded" ]; then
  echo "  new migration(s) with unguarded DDL — add IF NOT EXISTS:"
  printf '%s' "$unguarded"
  fail=1
else
  echo "  new-migration DDL guard: OK (enforced from V$IDEMPOTENT_FROM)"
fi

# ── 6. Next free version (the number to use for a new migration) ─────────────
echo "  next free version: V$((max + 1))"

if [ "$fail" -ne 0 ]; then
  echo "::error::migration check FAILED"
  exit 1
fi

echo "migration check OK"

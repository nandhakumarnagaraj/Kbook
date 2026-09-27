#!/usr/bin/env bash
# KhanaBook daily backup: production DB + CDN images.
# Runs via cron at 02:30 IST daily. Logs to backups/cron.log.
set -euo pipefail

ROOT_DIR="/var/www/kbook.iadv.cloud"
COMPOSE_FILE="$ROOT_DIR/ops/docker-compose.production.yml"
ENV_FILE="$ROOT_DIR/.env"
BACKUP_DIR="$ROOT_DIR/backups/daily"
LOG_FILE="$ROOT_DIR/backups/cron.log"
STATUS_FILE="$ROOT_DIR/backups/STATUS"
RETENTION_DAYS=14

mkdir -p "$BACKUP_DIR"

# Covers every exit path. `set -e` aborts on the first failure, and a failure
# previously left no trace outside cron.log, which is why a rejected backup went
# unnoticed for 15 hours: cron sees the job's stdout and stderr, not a log file
# nobody is told to read. So a failure is echoed to stderr (picked up by a MAILTO
# in the crontab or any log forwarder) and the verdict is also written to
# backups/STATUS, which needs no mail transport at all. A health check can assert
# that file is "OK" and no older than ~26 hours without knowing anything else.
on_exit() {
  local code="$?"
  if [ -n "${raw_file:-}" ]; then rm -f "$raw_file" 2>/dev/null || true; fi
  local ts
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  if [ "$code" -eq 0 ]; then
    printf 'OK %s\n' "$ts" > "$STATUS_FILE"
  else
    printf 'FAILED %s exit=%s\n' "$ts" "$code" > "$STATUS_FILE" || true
    echo "[$ts] BACKUP CYCLE FAILED (exit $code) - see $LOG_FILE" >&2
  fi
}
trap on_exit EXIT

log() {
  local line="[$(date '+%Y-%m-%d %H:%M:%S %Z')] $*"
  echo "$line" >> "$LOG_FILE"
  # Also to the terminal when a human is running it. Cron discards stdout, so this
  # costs nothing there - and it stops a manual run from looking like it did nothing,
  # which is how a rejected backup went unnoticed for a whole cycle.
  if [ -t 1 ]; then echo "$line"; fi
  return 0
}


cd "$ROOT_DIR"

# ── 1. PostgreSQL dump (dockerized kbook_saas) ────────────────────────────────
set -a; source "$ENV_FILE"; set +a

ts="$(date -u +%Y%m%dT%H%M%SZ)"
db_file="$BACKUP_DIR/kbook_saas_${ts}.sql.gz"

if docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
     pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges \
     | gzip > "$db_file"; then
  log "DB backup OK: $db_file ($(du -h "$db_file" | cut -f1))"
else
  log "ERROR: DB backup FAILED"
  exit 1
fi

# Integrity check
if ! gzip -t "$db_file"; then
  log "ERROR: DB backup corrupt: $db_file"
  rm -f "$db_file"
  exit 1
fi

# Content check. `gzip -t` only proves the stream is DECOMPRESSABLE, which an empty
# database satisfies perfectly: an empty pg_dump is a valid ~20-byte gzip. Production
# retained two such files and would have restored an empty database while every
# integrity check reported success. Assert the dump actually carries data.
#
# These floors are tripwires for a pathologically tiny file, NOT the real test, and
# they must never exceed the smallest legitimate dump of this schema. An earlier
# revision set MIN_RAW_BYTES=1000000, never measured against this database: every
# valid dump lands in a 599K-641K band, so the gate rejected 100% of good backups
# and then deleted them. A later revision set the floors to 50000/100000, which
# still rejected a genuine 52-table dump in testing. Logical dump size tracks the
# DATA, while pg_database_size reports physical size including bloat and free space
# (14MB here against a ~640KB dump) - a floor derived from the latter will always
# reject the former, and any absolute floor is a bet on how full the database is.
# Set these absurdly low and let the content assertions below do the real work: they
# are size-independent, which is the property that actually matters.
MIN_COMPRESSED_BYTES=${MIN_COMPRESSED_BYTES:-1024}
MIN_RAW_BYTES=${MIN_RAW_BYTES:-2048}
# A genuine dump of this schema contains 52 CREATE TABLE statements. An empty or
# truncated one contains none. That 52-vs-0 gap is a far stronger signal than any
# byte threshold, and unlike a byte threshold it does not need calibrating against a
# database that may grow or shrink.
MIN_CREATE_TABLES=${MIN_CREATE_TABLES:-40}

# A rejected archive is preserved, not deleted. Deleting it destroyed a perfectly
# good 640KB backup and left nothing to inspect, which is what made the failure
# undiagnosable. Renaming keeps the evidence while stopping anyone from restoring
# it by accident.
reject() {
  local reason="$1"
  log "ERROR: $reason"
  log "       Preserved for inspection as ${db_file}.FAILED - do not restore it."
  mv -f "$db_file" "${db_file}.FAILED" 2>/dev/null || rm -f "$db_file"
  [ -n "${raw_file:-}" ] && rm -f "$raw_file"
  exit 1
}

db_size=$(stat -c%s "$db_file")
if [ "$db_size" -lt "$MIN_COMPRESSED_BYTES" ]; then
  reject "DB backup is only ${db_size}B (< ${MIN_COMPRESSED_BYTES}B) - an empty database compresses to a valid gzip and passes 'gzip -t'."
fi

raw_file=$(mktemp)
# raw_file is cleaned by on_exit's EXIT trap above; setting another EXIT trap here
# would silently replace it and the STATUS file would stop being written.
if ! gzip -dc "$db_file" > "$raw_file"; then
  reject "DB backup failed to decompress: $db_file"
fi

raw_size=$(stat -c%s "$raw_file")
if [ "$raw_size" -lt "$MIN_RAW_BYTES" ]; then
  reject "DB backup decompresses to only ${raw_size}B (< ${MIN_RAW_BYTES}B) - truncated or empty"
fi

table_count=$(grep -cE '^CREATE TABLE ' "$raw_file" || true)
if [ "$table_count" -lt "$MIN_CREATE_TABLES" ]; then
  reject "DB backup contains only ${table_count} CREATE TABLE statements (< ${MIN_CREATE_TABLES}) - the schema is not in this dump."
fi

# Size alone can be met by a partial dump, so also require the tables that carry the
# business to be present. Grep the decompressed FILE, never a `gzip | grep -q` pipe:
# `grep -q` exits at the first match, gzip takes SIGPIPE, and under `set -o pipefail`
# that pipeline reports failure - which would make this check reject, and then delete,
# a completely healthy backup.
for table in restaurantprofiles menuitems bills; do
  if ! grep -qE "^COPY public\.${table} " "$raw_file"; then
    reject "DB backup has no COPY block for ${table} - dump is partial or empty"
  fi
done
log "DB backup verified: ${db_size}B gz / ${raw_size}B raw, ${table_count} tables, critical tables present"

# ── 2. CDN images (restaurant menu photos) ───────────────────────────────────
cdn_file="$BACKUP_DIR/cdn_images_${ts}.tar.gz"
if tar -czf "$cdn_file" -C /var/www cdn.kbook.iadv.cloud; then
  log "CDN backup OK: $cdn_file ($(du -h "$cdn_file" | cut -f1))"
else
  log "ERROR: CDN backup FAILED"
  exit 1
fi

# ── 3. Retention: delete backups older than N days ───────────────────────────
deleted=$(find "$BACKUP_DIR" -type f \( -name '*.sql.gz' -o -name '*.tar.gz' \) -mtime +${RETENTION_DAYS} -print -delete | wc -l)
[ "$deleted" -gt 0 ] && log "Retention: removed $deleted old file(s)"

log "Backup cycle complete."

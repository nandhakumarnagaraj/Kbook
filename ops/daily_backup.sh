#!/usr/bin/env bash
# KhanaBook daily backup: production DB + CDN images.
# Runs via cron at 02:30 IST daily. Logs to backups/cron.log.
set -euo pipefail

ROOT_DIR="/var/www/kbook.iadv.cloud"
COMPOSE_FILE="$ROOT_DIR/ops/docker-compose.production.yml"
ENV_FILE="$ROOT_DIR/.env"
BACKUP_DIR="$ROOT_DIR/backups/daily"
LOG_FILE="$ROOT_DIR/backups/cron.log"
RETENTION_DAYS=14

mkdir -p "$BACKUP_DIR"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S %Z')] $*" >> "$LOG_FILE"; }

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

#!/usr/bin/env bash
# Off-site backup for KhanaBook — encrypted, deduplicated, restorable.
#
# WHY THIS EXISTS
#   ops/daily_backup.sh writes a verified dump to backups/daily/ on THIS host.
#   That is a sound backup of a database that lives on the same disk: a disk
#   failure, a bad prune, or ransomware removes the database and its backup
#   together. This script is the copy that leaves the machine.
#
#   It is intentionally INERT until credentials exist. Running it without a
#   configured repository fails loudly rather than pretending to have backed up.
#
# SETUP (one time, human):
#   1. Create an S3-compatible bucket (Backblaze B2 / Wasabi / AWS S3 / MinIO).
#   2. Write the credentials to a root-only file OUTSIDE the repository:
#        install -m 600 /dev/stdin /etc/kbook/restic.env <<'EOF'
#        RESTIC_REPOSITORY=s3:https://s3.eu-central-003.backblazeb2.com/kbook-backups
#        RESTIC_PASSWORD=<a long random passphrase, ALSO stored in a password manager>
#        AWS_ACCESS_KEY_ID=...
#        AWS_SECRET_ACCESS_KEY=...
#        EOF
#      Losing RESTIC_PASSWORD means losing every backup. Store it separately.
#   3. Initialise once:  set -a; . /etc/kbook/restic.env; set +a; restic init
#   4. Prove it:         RESTORE-TO-A-SCRATCH-DB test, see md/docs/runbooks/BACKUP_AND_RESTORE.md
#   5. Schedule, after daily_backup.sh (which runs 21:00):
#         crontab -e  →  30 21 * * * /var/www/kbook.iadv.cloud/ops/offsite_backup.sh
#   6. Alert on failure (see md/docs/runbooks/MONITORING.md) — a silent backup job
#      is worse than none, because it is believed.
#
# RETENTION: 14 daily, 8 weekly, 12 monthly, all encrypted client-side.
#
# EXIT CODES: 0 success · 78 not configured · 1 backup or prune failed

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="${REPO_ROOT}/backups/daily"
ENV_FILE="${KBOOK_RESTIC_ENV:-/etc/kbook/restic.env}"
LOG_FILE="${REPO_ROOT}/backups/offsite.log"
LOCK_FILE="/tmp/kbook-offsite.lock"

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG_FILE"; }
die() { log "OFFSITE BACKUP FAILED: $*"; exit 1; }

# --- preconditions -----------------------------------------------------------
command -v restic >/dev/null 2>&1 || die "restic is not installed"
[[ -f "$ENV_FILE" ]] || { log "not configured: $ENV_FILE is missing — see the header of this script"; exit 78; }

# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a

[[ -n "${RESTIC_REPOSITORY:-}" ]] || { log "not configured: RESTIC_REPOSITORY unset"; exit 78; }
[[ -n "${RESTIC_PASSWORD:-}"   ]] || { log "not configured: RESTIC_PASSWORD unset"; exit 78; }

# A backup tool must never dump secrets into a log.
export RESTIC_PASSWORD

exec 9>"$LOCK_FILE" || die "cannot open lock file"
flock -n 9 || { log "another run is in progress; exiting"; exit 0; }

# --- source data -------------------------------------------------------------
[[ -d "$SOURCE_DIR" ]] || die "source directory missing: $SOURCE_DIR"

NEWEST="$(find "$SOURCE_DIR" -maxdepth 1 -name 'kbook_saas_*.sql.gz' -printf '%T@ %p\n' 2>/dev/null \
          | sort -n | tail -1 | cut -d' ' -f2-)"
[[ -n "$NEWEST" ]] || die "no database dump found in $SOURCE_DIR"
# Refuse to ship a stale "backup" — 36h covers a missed night without hiding two.
AGE_H=$(( ( $(date +%s) - $(stat -c %Y "$NEWEST") ) / 3600 ))
(( AGE_H <= 36 )) || die "newest dump is ${AGE_H}h old (>36h); local backup may be broken: $NEWEST"

# --- backup ------------------------------------------------------------------
log "backing up $SOURCE_DIR (newest dump ${AGE_H}h old) -> $RESTIC_REPOSITORY"
restic backup "$SOURCE_DIR" \
    --tag khanabook --tag "$(date -u +%F)" \
    --exclude 'cron.log' --exclude 'offsite.log' --exclude 'STATUS' \
    2>&1 | tee -a "$LOG_FILE" | tail -3 || die "restic backup returned non-zero"

# --- retention ---------------------------------------------------------------
log "applying retention"
restic forget --prune \
    --tag khanabook \
    --keep-daily 14 --keep-weekly 8 --keep-monthly 12 \
    2>&1 | tee -a "$LOG_FILE" | tail -3 || die "restic forget --prune returned non-zero"

# --- integrity ---------------------------------------------------------------
# Weekly deep check; abort if the repository cannot verify itself.
if [[ "$(date -u +%u)" == "7" ]]; then
    log "weekly repository check"
    restic check 2>&1 | tee -a "$LOG_FILE" | tail -2 || die "restic check reported damage"
fi

SNAP="$(restic snapshots --tag khanabook --json 2>/dev/null | grep -c '"short_id"' || true)"
log "ok — ${SNAP} snapshot(s) in repository"
exit 0

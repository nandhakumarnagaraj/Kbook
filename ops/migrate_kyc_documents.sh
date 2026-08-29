#!/usr/bin/env bash
# =============================================================================
# migrate_kyc_documents.sh
# -----------------------------------------------------------------------------
# Moves ONLY the four EaseBuzz KYC document types out of the Apache-public CDN
# directory into the private documents path. Logos and all other CDN assets are
# left untouched.
#
#   Public (exposed): $CDN_ROOT/{restaurantId}/kyc_{docType}_v{n}.<ext>
#   Private (target): $PRIVATE_ROOT/kyc/{restaurantId}/{same filename}
#
# The DB backfill (ops/sql/kyc_document_reconciliation.sql) derives the same
# private key ("kyc/{restaurantId}/{filename}"), so keys and files agree.
#
# SAFETY:
#   * Dry-run by default. Real move requires --apply AND --i-understand-prod.
#   * Refuses to run without a fresh tar backup of the KYC files (--backup-dir).
#   * Copies then verifies (size match) before deleting the public original.
#   * Never overwrites an existing destination file (fails that item, logs it).
#   * Reports missing source files. Writes a full reconciliation log.
#   * Only ever touches files matching kyc_*  — never logo_* or upi_* etc.
# =============================================================================
set -euo pipefail

CDN_ROOT="${CDN_ROOT:-/var/www/cdn.kbook.iadv.cloud/restaurants}"
PRIVATE_ROOT="${PRIVATE_ROOT:-/var/www/kbook-private/documents}"
BACKUP_DIR="${BACKUP_DIR:-}"
APPLY=0
CONFIRM_PROD=0
LOG="${LOG:-/tmp/kyc_migration_$(date +%Y%m%d_%H%M%S).log}"

# KYC filename prefixes we are allowed to move. Anything else is ignored.
KYC_GLOBS=(kyc_id_proof_* kyc_bank_proof_* kyc_business_proof_1_* kyc_business_proof_2_*)

usage() {
  cat <<EOF
Usage: $0 [--apply] [--i-understand-prod] [--backup-dir DIR]

  (no flags)            Dry-run: report what WOULD move. Default, non-destructive.
  --apply               Perform copy+verify+delete. Requires --i-understand-prod.
  --i-understand-prod   Explicit production confirmation gate.
  --backup-dir DIR      Directory to write the pre-move tar backup (required for --apply).

Env overrides: CDN_ROOT, PRIVATE_ROOT, LOG
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --i-understand-prod) CONFIRM_PROD=1 ;;
    --backup-dir) BACKUP_DIR="${2:-}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
  shift
done

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

log "KYC migration starting. CDN_ROOT=$CDN_ROOT PRIVATE_ROOT=$PRIVATE_ROOT APPLY=$APPLY"

if [[ ! -d "$CDN_ROOT" ]]; then
  log "ERROR: source CDN_ROOT does not exist: $CDN_ROOT"; exit 1
fi

# Gather KYC files (only the four types). NUL-safe.
mapfile -d '' KYC_FILES < <(
  for g in "${KYC_GLOBS[@]}"; do
    find "$CDN_ROOT" -type f -name "$g" -print0
  done
)

log "Found ${#KYC_FILES[@]} KYC file(s) to consider."
if [[ ${#KYC_FILES[@]} -eq 0 ]]; then
  log "Nothing to migrate. Exiting."; exit 0
fi

if [[ $APPLY -eq 1 ]]; then
  if [[ $CONFIRM_PROD -ne 1 ]]; then
    log "ERROR: --apply requires --i-understand-prod. Aborting."; exit 1
  fi
  if [[ -z "$BACKUP_DIR" ]]; then
    log "ERROR: --apply requires --backup-dir. Aborting."; exit 1
  fi
  mkdir -p "$BACKUP_DIR"
  BACKUP_TAR="$BACKUP_DIR/kyc_backup_$(date +%Y%m%d_%H%M%S).tar.gz"
  log "Creating backup of KYC files -> $BACKUP_TAR"
  # Back up ONLY the KYC files (relative to CDN_ROOT) so restore is targeted.
  ( cd "$CDN_ROOT" && printf '%s\0' "${KYC_FILES[@]#$CDN_ROOT/}" | tar --null -czf "$BACKUP_TAR" -T - )
  log "Backup written: $BACKUP_TAR"
fi

MOVED=0; SKIPPED=0; MISSING=0; FAILED=0

for src in "${KYC_FILES[@]}"; do
  [[ -z "$src" ]] && continue
  rel="${src#$CDN_ROOT/}"                      # {restaurantId}/kyc_*.ext
  restaurant_id="${rel%%/*}"
  filename="${rel##*/}"
  dest_dir="$PRIVATE_ROOT/kyc/$restaurant_id"
  dest="$dest_dir/$filename"
  key="kyc/$restaurant_id/$filename"

  if [[ ! -f "$src" ]]; then
    log "MISSING source (skipped): $src"; MISSING=$((MISSING+1)); continue
  fi
  if [[ -e "$dest" ]]; then
    log "SKIP (dest exists, not overwriting): $dest  [key=$key]"; SKIPPED=$((SKIPPED+1)); continue
  fi

  if [[ $APPLY -eq 0 ]]; then
    log "DRY-RUN would move: $src -> $dest  [key=$key]"; MOVED=$((MOVED+1)); continue
  fi

  mkdir -p "$dest_dir"
  # Preserve ownership/permissions/timestamps.
  if ! cp -p "$src" "$dest"; then
    log "FAIL copy: $src -> $dest"; FAILED=$((FAILED+1)); continue
  fi
  src_size=$(stat -c%s "$src"); dest_size=$(stat -c%s "$dest")
  if [[ "$src_size" != "$dest_size" ]]; then
    log "FAIL verify (size $src_size != $dest_size): $dest — leaving source intact"; rm -f "$dest"; FAILED=$((FAILED+1)); continue
  fi
  # Verified: safe to remove the public original.
  rm -f "$src"
  log "MOVED: $src -> $dest  [key=$key]"; MOVED=$((MOVED+1))
done

log "----------------------------------------------------------------"
log "Summary: moved=$MOVED skipped=$SKIPPED missing=$MISSING failed=$FAILED (apply=$APPLY)"
log "Log written to: $LOG"
if [[ $FAILED -gt 0 ]]; then
  log "One or more items FAILED. Review the log before running the DB backfill."; exit 3
fi

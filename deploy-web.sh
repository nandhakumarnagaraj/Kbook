#!/usr/bin/env bash
set -euo pipefail

# Deploy the Angular web-admin to the Apache docroot on the VPS.
# Run on the VPS from the repo root after `git pull`.
#
# Overridable:
#   WEB_ROOT   Apache docroot for kbook.iadv.cloud (default below)
#   SKIP_BUILD set to 1 to reuse an existing dist/ (skip npm ci + ng build)

ROOT_DIR="/var/www/kbook.iadv.cloud"
WEB_ROOT="${WEB_ROOT:-/var/www/kbook.iadv.cloud/web}"
WEB_SRC="$ROOT_DIR/web-admin"
DIST_DIR="$WEB_SRC/dist/khanabook-web-admin/browser"
HEALTH_URL="https://kbook.iadv.cloud/api/v1/actuator/health"

echo "--- KhanaBook web-admin deploy ---"
echo "Repo:    $WEB_SRC"
echo "Docroot: $WEB_ROOT"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "--- Building web-admin (production) ---"
  cd "$WEB_SRC"
  npm ci
  npm run build
fi

if [ ! -f "$DIST_DIR/index.html" ]; then
  echo "ERROR: build output not found at $DIST_DIR/index.html" >&2
  echo "Run without SKIP_BUILD, or check the Angular outputPath." >&2
  exit 1
fi

mkdir -p "$WEB_ROOT"

# Back up current docroot before overwriting.
if [ -d "$WEB_ROOT" ] && [ -n "$(ls -A "$WEB_ROOT" 2>/dev/null)" ]; then
  BACKUP="${WEB_ROOT%/}.bak.$(date +%Y%m%d%H%M%S)"
  echo "--- Backing up current docroot -> $BACKUP ---"
  cp -a "$WEB_ROOT" "$BACKUP"
fi

echo "--- Syncing new build to docroot ---"
if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete "$DIST_DIR/" "$WEB_ROOT/"
else
  rm -rf "${WEB_ROOT:?}/"*
  cp -a "$DIST_DIR/." "$WEB_ROOT/"
fi

echo "--- Verifying ---"
[ -f "$WEB_ROOT/index.html" ] && echo "index.html present in docroot"
curl -fsS "$HEALTH_URL" >/dev/null && echo "API health OK" || echo "WARN: API health check failed (frontend still deployed)"

echo "--- Done. https://kbook.iadv.cloud/ now serves the new build. ---"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/var/www/kbook.iadv.cloud"
COMPOSE_FILE="$ROOT_DIR/ops/docker-compose.production.yml"
ENV_FILE="$ROOT_DIR/.env"

cd "$ROOT_DIR"

# Stamp the build with the identity of the checked-out commit before building. The
# Maven build context is server/, so the builder cannot read the repository itself;
# without these args the jar ships with an empty git.properties and the running
# version becomes unidentifiable.
GIT_COMMIT_ID="$(git rev-parse HEAD)"
GIT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo detached)"
GIT_COMMIT_TIME="$(git show -s --format=%cI HEAD 2>/dev/null || echo unknown)"
export GIT_COMMIT_ID GIT_BRANCH GIT_COMMIT_TIME

# Gate the deploy on migration/ledger integrity. A NULL checksum or a half-applied
# script is a hard stop: it means the schema does not match the code about to run.
# Runs after postgres is up but before the new server starts.
echo "Building server at ${GIT_BRANCH}@${GIT_COMMIT_ID}"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build server
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d postgres
bash "$ROOT_DIR/ops/flyway_reconcile_checksums.sh"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d server

echo "Waiting for backend health..."
# Poll instead of a fixed 15s sleep — Spring Boot + Flyway can take longer,
# and the old fixed wait reported failure on healthy deploys.
for i in $(seq 1 24); do
  sleep 5
  if curl -fsS http://127.0.0.1:8081/api/v1/actuator/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8081/api/v1/actuator/health
    echo ""
    echo "Backend healthy."
    # Contract-drift guard: the dashboard deploys separately (deploy-web.sh).
    # A backend-only deploy leaves it stale — that caused the MANAGER-role
    # incident on 2026-09-16. Warn loudly unless this deploy also shipped it.
    if [ "${WEB_DEPLOYED:-0}" != "1" ]; then
      echo ""
      echo "⚠️  REMINDER: web-admin was NOT deployed by this script."
      echo "   If this deploy changed any API contract, run: bash ops/deploy-web.sh"
      echo "   (or use ops/deploy-all.sh to always ship both)."
    fi
    exit 0
  fi
done
echo "ERROR: backend did not become healthy within 120s" >&2
exit 1

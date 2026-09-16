#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/var/www/kbook.iadv.cloud"
COMPOSE_FILE="$ROOT_DIR/docker-compose.production.yml"
ENV_FILE="$ROOT_DIR/.env"

cd "$ROOT_DIR"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build server
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d postgres
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
      echo "   If this deploy changed any API contract, run: bash deploy-web.sh"
      echo "   (or use deploy-all.sh to always ship both)."
    fi
    exit 0
  fi
done
echo "ERROR: backend did not become healthy within 120s" >&2
exit 1

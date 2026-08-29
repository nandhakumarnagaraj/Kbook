#!/usr/bin/env bash
# Deploy the ISOLATED KhanaBook staging stack. NON-destructive.
# Never touches the production stack, DB, volumes, or Apache.
set -euo pipefail

PROJECT="kbook-staging"
COMPOSE_FILE="docker-compose.staging.yml"
ENV_FILE="${ENV_FILE:-ops/.env.staging}"
STAGING_PORT="${STAGING_PORT:-8091}"
HEALTH="http://127.0.0.1:${STAGING_PORT}/api/v1/actuator/health"

echo "=== KhanaBook STAGING deploy (project=$PROJECT) ==="

# 1. Preconditions
command -v docker >/dev/null 2>&1 || { echo "ERROR: docker not found"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "ERROR: 'docker compose' not available"; exit 1; }
[ -f "$COMPOSE_FILE" ] || { echo "ERROR: $COMPOSE_FILE not found (run from repo root)"; exit 1; }
[ -f "$ENV_FILE" ] || { echo "ERROR: $ENV_FILE not found. Copy ops/.env.staging.example and fill it."; exit 1; }

# 2. Guard: refuse if staging env still points anywhere near production values.
if grep -qE 'kbook_saas|pgdata:|cdn\.kbook\.iadv\.cloud|kbook-private' "$ENV_FILE"; then
  echo "ERROR: $ENV_FILE contains production-looking values (kbook_saas / prod paths). Aborting for safety."
  exit 1
fi

# 3. Validate required staging vars are set and not CHANGE_ME.
required=(POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET PAYMENT_CRYPTO_SECRET)
for v in "${required[@]}"; do
  val=$(grep -E "^$v=" "$ENV_FILE" | head -1 | cut -d= -f2-)
  if [ -z "$val" ] || echo "$val" | grep -q "CHANGE_ME"; then
    echo "ERROR: $v is unset or still CHANGE_ME in $ENV_FILE"; exit 1
  fi
done

# 4. Build + start (postgres first, then server). Migrations run on server boot.
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build server
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d postgres
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d server

# 5. Wait for health.
echo "Waiting for staging backend health at $HEALTH ..."
for i in $(seq 1 30); do
  if curl -fsS "$HEALTH" >/dev/null 2>&1; then echo "Staging health OK."; break; fi
  sleep 5
  [ "$i" -eq 30 ] && { echo "ERROR: staging did not become healthy in time"; }
done

# 6. Status summary.
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
echo "=== Staging reachable on 127.0.0.1:${STAGING_PORT} (public URL via Apache handled separately) ==="

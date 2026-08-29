#!/usr/bin/env bash
# DESTRUCTIVE — resets the STAGING database volume only. QA use to return to a clean baseline.
# Hard-guarded so it can NEVER touch the production stack (kbookiadvcloud / pgdata / kbook_saas).
set -euo pipefail

PROJECT="kbook-staging"
COMPOSE_FILE="docker-compose.staging.yml"
ENV_FILE="${ENV_FILE:-ops/.env.staging}"

# Refuse to run against anything that looks like production.
if [ "$PROJECT" != "kbook-staging" ]; then echo "ERROR: wrong project"; exit 1; fi
if grep -qE 'kbook_saas|pgdata:|cdn\.kbook\.iadv\.cloud' "$ENV_FILE" 2>/dev/null; then
  echo "ERROR: $ENV_FILE looks like production. Aborting."; exit 1
fi

echo "This will DELETE the staging database volume 'pgdata-staging' and staging containers."
echo "Production is NOT affected. Type EXACTLY 'RESET STAGING' to continue:"
read -r confirm
[ "$confirm" = "RESET STAGING" ] || { echo "Aborted."; exit 1; }

docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
docker volume rm pgdata-staging 2>/dev/null || echo "(volume pgdata-staging already gone)"
echo "Staging reset. Re-run ops/deploy-staging.sh then the seed to rebuild the baseline."

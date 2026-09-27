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

# Assert the container memory ceilings are actually in force.
#
# Compose resource limits are applied at container CREATE, not when the config is
# read. A deploy that recreated containers from a config carrying no mem_limit
# leaves them unlimited (HostConfig.Memory=0) and still exits 0 -- exactly the state
# M1 was opened for: a leak with no cgroup ceiling that takes Postgres down with the
# JVM. Nothing else in this script would notice, so it is asserted explicitly.
#
# The expected byte count is read from the resolved compose config rather than
# hardcoded, so editing a limit does not silently disable the check. The set of
# services that MUST carry a limit IS hardcoded, so deleting a limit is still caught.
MEM_LIMIT_REQUIRED="server postgres"

assert_memory_limits() {
  local cfg service expected actual cid failures=0
  cfg="$(mktemp)"
  if ! docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --format json >"$cfg" 2>/dev/null; then
    echo "ERROR: could not render compose config; cannot verify memory limits" >&2
    rm -f "$cfg"
    return 1
  fi
  for service in $MEM_LIMIT_REQUIRED; do
    expected="$(jq -r --arg s "$service" '.services[$s].mem_limit // empty' "$cfg")"
    if [ -z "$expected" ]; then
      echo "ERROR: $service has no mem_limit in the compose config - the cgroup ceiling would be silently absent" >&2
      failures=$((failures + 1))
      continue
    fi
    cid="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q "$service")"
    actual="$(docker inspect --format '{{.HostConfig.Memory}}' "$cid" 2>/dev/null || echo 0)"
    if [ -z "$actual" ] || [ "$actual" = "0" ]; then
      echo "ERROR: $service is running with NO memory limit (HostConfig.Memory=0); compose requires $expected bytes" >&2
      failures=$((failures + 1))
    elif [ "$actual" != "$expected" ]; then
      echo "ERROR: $service memory limit mismatch: compose $expected, container $actual" >&2
      failures=$((failures + 1))
    else
      echo "Memory limit OK: $service = $expected bytes ($((expected / 1024 / 1024)) MiB)"
    fi
  done
  rm -f "$cfg"
  [ "$failures" -eq 0 ]
}

echo "Waiting for backend health..."
# Poll instead of a fixed 15s sleep — Spring Boot + Flyway can take longer,
# and the old fixed wait reported failure on healthy deploys.
for i in $(seq 1 24); do
  sleep 5
  if curl -fsS http://127.0.0.1:8081/api/v1/actuator/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8081/api/v1/actuator/health
    echo ""
    echo "Backend healthy."

    # Memory ceilings must be in force on the containers just recreated. A failure
    # here fails the deploy: an unlimited container is a latent outage, not a note.
    if ! assert_memory_limits; then
      echo "ERROR: container memory limits are not in force as configured. See above." >&2
      exit 1
    fi
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

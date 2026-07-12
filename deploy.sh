#!/bin/bash
# KhanaBook production deploy script.
# Flow: test → backup DB → backup JAR → build → start → health check → expose commit
# On failure: restore previous JAR automatically.
set -e

SERVICE=khanabook
JAR=/var/www/kbook.iadv.cloud/server/target/saas-backend-0.0.1-SNAPSHOT.jar
JAR_PREV="${JAR}.prev"
SERVER_DIR=/var/www/kbook.iadv.cloud/server
HEALTH_URL=http://localhost:8080/actuator/health
OPENAPI_URL=http://localhost:8080/v3/api-docs
MAX_WAIT=60   # seconds to wait for startup before declaring failure

REQUIRE_DOCKER="${REQUIRE_DOCKER:-0}"    # set to 1 to hard-fail when Docker is missing
ALLOW_NO_BACKUP="${ALLOW_NO_BACKUP:-0}"  # set to 1 to deploy without a DB backup

# ── 0. Preflight: Testcontainers tests SKIP silently without Docker ───────────
# PostgresMigrationSmokeTest and MultiDeviceInvoiceSyncIntegrationTest use
# Testcontainers (@Testcontainers(disabledWithoutDocker = true)). With no Docker
# daemon they are skipped, not run — so the migration/multi-device gate below is
# INACTIVE and a broken migration could deploy. Make that impossible to miss.
echo "==> Checking Docker (Testcontainers migration + multi-device tests need it)..."
if docker info >/dev/null 2>&1; then
    echo "==> Docker available — Testcontainers tests will run."
else
    MSG="Docker unavailable: migration + multi-device Testcontainers tests will be SKIPPED, so the migration gate is INACTIVE for this deploy."
    if [ "$REQUIRE_DOCKER" = "1" ]; then
        echo "ERROR: $MSG"
        echo "       Install Docker, or re-run with REQUIRE_DOCKER=0 to proceed anyway."
        exit 1
    fi
    echo "WARNING: $MSG"
fi

# ── 1. Run tests before touching anything in production ───────────────────────
echo "==> Running tests (this gates the build)..."
cd "$SERVER_DIR"
mvn test -q
echo "==> Tests passed."

# ── 2. Backup the database ────────────────────────────────────────────────────
echo "==> Backing up database..."
if [ -f /var/www/kbook.iadv.cloud/ops/backup_postgres.sh ]; then
    bash /var/www/kbook.iadv.cloud/ops/backup_postgres.sh
    echo "==> Database backup complete."
else
    if [ "$ALLOW_NO_BACKUP" = "1" ]; then
        echo "WARNING: backup_postgres.sh not found — proceeding without a DB backup (ALLOW_NO_BACKUP=1)."
    else
        echo "ERROR: backup_postgres.sh not found and ALLOW_NO_BACKUP != 1."
        echo "       A Flyway schema migration with no backup has no safe rollback (JAR rollback"
        echo "       does NOT revert the schema). Refusing to deploy. Override with ALLOW_NO_BACKUP=1."
        exit 1
    fi
fi

# ── 3. Record deployed commit ─────────────────────────────────────────────────
GIT_COMMIT=$(git -C "$SERVER_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git -C "$SERVER_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
echo "==> Deploying commit=$GIT_COMMIT branch=$GIT_BRANCH"

# ── 4. Stop service ───────────────────────────────────────────────────────────
echo "==> Stopping $SERVICE service..."
systemctl stop "$SERVICE"

# ── 5. Rotate JARs: move current → .prev before overwriting ──────────────────
if [ -f "$JAR" ]; then
    echo "==> Rotating JAR: $JAR → $JAR_PREV"
    mv "$JAR" "$JAR_PREV"
fi

# ── 6. Build new JAR (tests already passed in step 1) ────────────────────────
echo "==> Building new JAR..."
mvn package -DskipTests -q   # tests already ran; skip here to avoid double-run
echo "==> Build complete."

# ── 7. Start service ──────────────────────────────────────────────────────────
echo "==> Starting $SERVICE service..."
systemctl start "$SERVICE"

# ── 8. Health check with timeout ─────────────────────────────────────────────
echo "==> Waiting for startup (up to ${MAX_WAIT}s)..."
ELAPSED=0
HEALTHY=false
while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
    if [ "$HTTP_STATUS" = "200" ]; then
        HEALTHY=true
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
done

if [ "$HEALTHY" != "true" ]; then
    echo ""
    echo "ERROR: Health check failed after ${MAX_WAIT}s (last status: $HTTP_STATUS)."
    echo "==> Rolling back to previous JAR..."
    systemctl stop "$SERVICE" || true
    if [ -f "$JAR_PREV" ]; then
        mv "$JAR_PREV" "$JAR"
        systemctl start "$SERVICE"
        echo "==> Rollback complete. Previous JAR restored."
        echo "    NOTE: this restores the JAR only. If a Flyway migration ran this deploy,"
        echo "          the schema is already migrated and the old JAR may fail Hibernate"
        echo "          validate on boot. To revert the schema, restore the DB backup:"
        echo "              bash /var/www/kbook.iadv.cloud/ops/restore_postgres.sh"
        echo "    To investigate: journalctl -u $SERVICE -n 100"
    else
        echo "WARNING: No previous JAR to restore. Service is stopped."
    fi
    exit 1
fi

# ── 9. OpenAPI smoke check ────────────────────────────────────────────────────
OPENAPI_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$OPENAPI_URL" 2>/dev/null || echo "000")
if [ "$OPENAPI_STATUS" != "200" ]; then
    echo "WARNING: OpenAPI smoke check returned $OPENAPI_STATUS (non-fatal)."
fi

# ── 10. Print summary ─────────────────────────────────────────────────────────
echo ""
echo "======================================================"
echo "  Deploy SUCCESS"
echo "  commit : $GIT_COMMIT ($GIT_BRANCH)"
echo "  health : $HEALTH_URL → $HTTP_STATUS"
echo "  openapi: $OPENAPI_URL → $OPENAPI_STATUS"
echo "  rollback: mv $JAR_PREV $JAR && systemctl restart $SERVICE"
echo "  db restore (if a migration ran): bash $SERVER_DIR/../ops/restore_postgres.sh"
echo "======================================================"
echo ""

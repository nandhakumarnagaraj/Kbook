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
    echo "WARNING: backup_postgres.sh not found — skipping DB backup."
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
echo "======================================================"
echo ""

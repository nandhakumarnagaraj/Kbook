#!/usr/bin/env bash
# ============================================================
# KhanaBook v1 Pre-Launch Checklist Script
# Run on VPS: /var/www/kbook.iadv.cloud/ops/pre-launch-checklist.sh
#
# This script performs safe, non-destructive checks and applies
# only the changes you confirm. Run it AFTER `git pull`.
# ============================================================
set -euo pipefail

ROOT_DIR="/var/www/kbook.iadv.cloud"
COMPOSE_FILE="$ROOT_DIR/docker-compose.production.yml"
ENV_FILE="$ROOT_DIR/.env"

cd "$ROOT_DIR"

echo "=========================================="
echo " KhanaBook v1 — Pre-Launch Checklist"
echo "=========================================="
echo ""

# ─── 1. Check .env for production-unsafe settings ───────────────
echo "1. Checking .env for production issues..."
issues=0

if grep -q 'SPRINGDOC_API_DOCS_ENABLED=true' "$ENV_FILE" 2>/dev/null; then
    echo "   ⚠️  SPRINGDOC_API_DOCS_ENABLED=true — API schema publicly exposed!"
    echo "   Fix: Set SPRINGDOC_API_DOCS_ENABLED=false in .env"
    issues=$((issues + 1))
fi

if grep -q 'SPRINGDOC_SWAGGER_UI_ENABLED=true' "$ENV_FILE" 2>/dev/null; then
    echo "   ⚠️  SPRINGDOC_SWAGGER_UI_ENABLED=true — Swagger UI publicly exposed!"
    echo "   Fix: Set SPRINGDOC_SWAGGER_UI_ENABLED=false in .env"
    issues=$((issues + 1))
fi

if grep -q 'localhost:4200' "$ENV_FILE" 2>/dev/null; then
    echo "   ⚠️  CORS_ALLOWED_ORIGINS contains localhost:4200 — remove for production"
    issues=$((issues + 1))
fi

if [ $issues -eq 0 ]; then
    echo "   ✅ .env looks production-ready"
fi
echo ""

# ─── 2. Check backup status ────────────────────────────────────
echo "2. Checking backup setup..."
BACKUP_DIR="$ROOT_DIR/backups/postgres"

if [ -d "$BACKUP_DIR" ] && [ "$(find "$BACKUP_DIR" -name '*.sql.gz' -mtime -1 2>/dev/null | wc -l)" -gt 0 ]; then
    latest=$(ls -t "$BACKUP_DIR"/*.sql.gz 2>/dev/null | head -1)
    echo "   ✅ Recent backup found: $(basename "$latest")"
else
    echo "   ⚠️  No backup from today. Taking one now..."
    ./ops/backup_postgres.sh
    echo "   ✅ Backup created."
fi
echo ""

# ─── 3. Check cron for automated backups ───────────────────────
echo "3. Checking automated backup cron..."
if crontab -l 2>/dev/null | grep -q 'backup_postgres.sh'; then
    echo "   ✅ Automated backup cron exists"
else
    echo "   ⚠️  No automated backup cron found!"
    echo "   Recommended: Add to crontab:"
    echo "   0 3 * * * $ROOT_DIR/ops/backup_postgres.sh >> /var/log/kbook_backup.log 2>&1"
fi
echo ""

# ─── 4. Check containers are running ──────────────────────────
echo "4. Checking Docker containers..."
if docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --format '{{.Service}} {{.Status}}' 2>/dev/null | grep -q 'server.*Up'; then
    echo "   ✅ Server container is running"
else
    echo "   ❌ Server container is NOT running!"
fi

if docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --format '{{.Service}} {{.Status}}' 2>/dev/null | grep -q 'postgres.*Up'; then
    echo "   ✅ Postgres container is running"
else
    echo "   ❌ Postgres container is NOT running!"
fi
echo ""

# ─── 5. Health check ──────────────────────────────────────────
echo "5. Checking server health..."
if curl -fsS http://127.0.0.1:8081/api/v1/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "   ✅ Server health: UP"
else
    echo "   ❌ Server health check FAILED!"
fi
echo ""

# ─── 6. Check SSL cert expiry ─────────────────────────────────
echo "6. Checking SSL certificate..."
if [ -f /etc/letsencrypt/live/kbook.iadv.cloud/fullchain.pem ]; then
    expiry=$(openssl x509 -enddate -noout -in /etc/letsencrypt/live/kbook.iadv.cloud/fullchain.pem | cut -d= -f2)
    expiry_epoch=$(date -d "$expiry" +%s 2>/dev/null || echo 0)
    now_epoch=$(date +%s)
    days_left=$(( (expiry_epoch - now_epoch) / 86400 ))
    if [ "$days_left" -gt 30 ]; then
        echo "   ✅ SSL cert valid for $days_left days (expires: $expiry)"
    elif [ "$days_left" -gt 0 ]; then
        echo "   ⚠️  SSL cert expires in $days_left days! Run: certbot renew"
    else
        echo "   ❌ SSL cert EXPIRED! Run: certbot renew --force-renewal"
    fi
else
    echo "   ⚠️  SSL cert file not found at expected path"
fi
echo ""

# ─── 7. Check disk space ──────────────────────────────────────
echo "7. Checking disk space..."
disk_usage=$(df / | awk 'NR==2 {print $5}' | tr -d '%')
if [ "$disk_usage" -lt 70 ]; then
    echo "   ✅ Disk usage: ${disk_usage}%"
elif [ "$disk_usage" -lt 85 ]; then
    echo "   ⚠️  Disk usage: ${disk_usage}% — getting high"
else
    echo "   ❌ Disk usage: ${disk_usage}% — CRITICAL! Free space immediately"
fi
echo ""

# ─── 8. Check recent errors ───────────────────────────────────
echo "8. Checking recent server errors (last 1 hour)..."
error_count=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
    logs --since 1h server 2>/dev/null | grep -c '"log.level":"ERROR"' || true)
if [ "$error_count" -eq 0 ]; then
    echo "   ✅ No errors in the last hour"
else
    echo "   ⚠️  $error_count errors in the last hour"
    echo "   View with: docker compose --env-file .env -f docker-compose.production.yml logs --since 1h server | grep ERROR"
fi
echo ""

# ─── 9. Check public_token uniqueness ─────────────────────────
echo "9. Checking public_token uniqueness..."
set -a; source "$ENV_FILE"; set +a
dup_count=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
    "SELECT COUNT(*) FROM (SELECT public_token FROM bills WHERE public_token IS NOT NULL GROUP BY public_token HAVING COUNT(*) > 1) sub;" \
    2>/dev/null | tr -d ' ' || echo "?")
if [ "$dup_count" = "0" ]; then
    echo "   ✅ No duplicate public_tokens"
elif [ "$dup_count" = "?" ]; then
    echo "   ⚠️  Could not check (DB access issue)"
else
    echo "   ❌ $dup_count duplicate public_token groups found!"
    echo "   Run: ops/sql/public_token_reconciliation.sql (after backup)"
fi
echo ""

# ─── Summary ──────────────────────────────────────────────────
echo "=========================================="
echo " Checklist complete."
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. Fix any ⚠️/❌ items above"
echo "  2. After .env changes: docker compose --env-file .env -f docker-compose.production.yml up -d server"
echo "  3. Set up UptimeRobot monitor: https://kbook.iadv.cloud/api/v1/actuator/health"
echo "  4. Test on 2 Android devices + printer"
echo ""

#!/bin/bash
# Start KhanaBook SaaS v2 on port 8082 with new database
set -a
source "$(dirname "$0")/.env.v2"
set +a

JAR="$(dirname "$0")/target/saas-backend-0.0.1-SNAPSHOT.jar"
LOG="$(dirname "$0")/server_v2.log"

echo "[$(date)] Starting KhanaBook v2 on port $SERVER_PORT with DB: $DB_URL" | tee -a "$LOG"

exec java \
  -Xms256m -Xmx512m \
  -jar "$JAR" \
  >> "$LOG" 2>&1

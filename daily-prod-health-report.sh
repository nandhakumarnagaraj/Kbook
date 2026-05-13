#!/usr/bin/env bash

###############################################################################
#  Kbook Production Health Audit Script
#  Usage:  sudo ./daily-prod-health-report.sh | tee daily-report.txt
###############################################################################

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
pass() { echo -e "  [${GREEN}PASS${NC}] $1"; }
warn() { echo -e "  [${YELLOW}WARN${NC}] $1"; }
fail() { echo -e "  [${RED}FAIL${NC}] $1"; }
info() { echo -e "  [${CYAN}INFO${NC}] $1"; }
header() { echo -e "\n${CYAN}==== $1 ====${NC}"; }

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}  KBOOK PRODUCTION HEALTH REPORT  $(date '+%Y-%m-%d %H:%M:%S %Z')${NC}"
echo -e "${CYAN}================================================================${NC}"

# ---------------------------------------------------------------
header "1. OS & ENVIRONMENT"
# ---------------------------------------------------------------
echo "  OS: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '"')"
echo "  Kernel: $(uname -r)"
echo "  Hostname: $(hostname)"
echo "  Uptime: $(uptime -p)"
echo "  Last boot: $(who -b 2>/dev/null | awk '{print $3,$4}')"
echo "  Load avg: $(cat /proc/loadavg)"
if [ -f /sys/devices/virtual/dmi/id/product_name ]; then
  echo "  Hypervisor: $(cat /sys/devices/virtual/dmi/id/product_name 2>/dev/null)"
fi
echo "  VPS provider: $(grep -oP '^provider:\s*\K.*' /etc/cloud/cloud.cfg 2>/dev/null || grep -i 'contabo\|hetzner\|aws\|digitalocean\|linode\|vultr\|netcup' /sys/devices/virtual/dmi/id/product_name 2>/dev/null || echo 'unknown')"
echo "  CPUs: $(nproc)"
echo "  Arch: $(uname -m)"

# ---------------------------------------------------------------
header "2. STACK DISCOVERY"
# ---------------------------------------------------------------
# Docker
if command -v docker &>/dev/null; then
  echo "  Docker: $(docker --version 2>/dev/null)"
  dc_ver=$(docker compose version 2>/dev/null || docker-compose --version 2>/dev/null || echo 'not found')
  echo "  Docker Compose: $dc_ver"
else
  echo "  Docker: not installed"
fi
# Java
if command -v java &>/dev/null; then
  echo "  Java: $(java -version 2>&1 | head -1)"
fi
# Node
if command -v node &>/dev/null; then
  echo "  Node.js: $(node -v 2>/dev/null)"
fi
# Apache
if command -v apache2ctl &>/dev/null; then
  echo "  Apache: $(apache2ctl -v 2>/dev/null | head -1)"
fi
# Nginx
if command -v nginx &>/dev/null; then
  echo "  Nginx: $(nginx -v 2>&1 | head -1)"
fi
# MongoDB
if command -v mongod &>/dev/null; then
  echo "  MongoDB: $(mongod --version 2>/dev/null | head -1)"
fi
# PostgreSQL
if command -v psql &>/dev/null; then
  echo "  PostgreSQL client: $(psql --version 2>/dev/null)"
fi
# PM2
if command -v pm2 &>/dev/null; then
  echo "  PM2: $(pm2 --version 2>/dev/null)"
fi
# Angular
if command -v ng &>/dev/null; then
  echo "  Angular CLI: $(ng version 2>/dev/null | grep 'Angular CLI' || echo 'installed')"
fi
# Certbot
if command -v certbot &>/dev/null; then
  echo "  Certbot: $(certbot --version 2>/dev/null)"
fi
# Project config
proj_dir="/var/www/kbook.iadv.cloud"
if [ -f "$proj_dir/docker-compose.yml" ]; then
  echo "  Docker Compose project: kbook.iadv.cloud"
  echo "  Spring Boot app port: $(grep -oP 'SERVER_PORT=\K.*' $proj_dir/.env 2>/dev/null || echo '8081')"
fi

# ---------------------------------------------------------------
header "3. RESOURCE USAGE"
# ---------------------------------------------------------------
echo "  --- CPU ---"
mpstat 1 1 2>/dev/null | tail -1 | awk '{printf "  User: %s%%  System: %s%%  Idle: %s%%  Iowait: %s%%  Steal: %s%%\n", $3, $5, $12, $6, $8}' 2>/dev/null || echo "  (install sysstat for detailed CPU)"
echo "  --- Memory ---"
free -h | awk 'NR==2{printf "  Total: %s  Used: %s  Free: %s  Available: %s  (%.1f%% used)\n", $2, $3, $4, $7, ($3/$2)*100}'
echo "  --- Disk ---"
df -h / | awk 'NR==2{printf "  Total: %s  Used: %s  Avail: %s  Used: %s\n", $2, $3, $4, $5}'
echo "  --- Swap ---"
if swapon --show 2>/dev/null | grep -q .; then
  swapon --show | awk 'NR>1{printf "  Total: %s  Used: %s\n", $3, $4}'
else
  echo "  [NO SWAP CONFIGURED - risk of OOM under load]"
fi
echo "  --- Top CPU consumers ---"
ps aux --sort=-%cpu | head -6 | awk 'NR>1{printf "  %-6s %-5s %-5s %s\n", $11, $2, $3"%", $NF}'
echo "  --- Top RAM consumers ---"
ps aux --sort=-%mem | head -6 | awk 'NR>1{printf "  %-6s %-5s %-5s %s\n", $11, $2, $4"%", $NF}'

# ---------------------------------------------------------------
header "4. DOCKER CONTAINER ANALYSIS"
# ---------------------------------------------------------------
if command -v docker &>/dev/null; then
  echo "  Running containers:"
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null | column -t -s $'\t'
  echo ""
  echo "  Container resource usage:"
  docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}' 2>/dev/null | column -t -s $'\t'
  echo ""
  # Per-container health
  for c in $(docker ps --format '{{.Names}}'); do
    health=$(docker inspect "$c" --format '{{.State.Health.Status}}' 2>/dev/null || echo 'N/A')
    restart_count=$(docker inspect "$c" --format '{{.RestartCount}}' 2>/dev/null || echo '0')
    started=$(docker inspect "$c" --format '{{.State.StartedAt}}' 2>/dev/null | cut -d. -f1 | tr 'T' ' ')
    echo "  $c: health=$health restarts=$restart_count started=$started"
  done
  echo ""
  echo "  Docker daemon recent errors:"
  journalctl -u docker --no-pager -n 10 2>/dev/null | grep -i 'error\|warn\|fail' | tail -5 | while read -r line; do echo "    $line"; done
  echo ""
  echo "  Container logs (last 5 error/warn):"
  for c in $(docker ps --format '{{.Names}}'); do
    errs=$(docker logs "$c" --tail 100 2>/dev/null | grep -i 'error\|exception\|warn.*deprecated\|OOM\|killed' | tail -3)
    if [ -n "$errs" ]; then
      echo "  --- $c ---"
      echo "$errs" | while read -r line; do echo "    $line"; done
    fi
  done
  echo ""
  # Docker network check
  echo "  Docker networks:"
  docker network ls --format 'table {{.Name}}\t{{.Driver}}\t{{.Scope}}' 2>/dev/null | column -t -s $'\t'
else
  echo "  Docker not available"
fi

# ---------------------------------------------------------------
header "5. PM2 PROCESS ANALYSIS"
# ---------------------------------------------------------------
if command -v pm2 &>/dev/null; then
  pm2 list 2>/dev/null | tail -n +3 | head -20
  echo ""
  echo "  PM2 memory usage:"
  pm2 prettylist 2>/dev/null | grep -E '"name"|"memory"|"cpu"|"status"' | paste - - - - | head -10 || true
else
  echo "  PM2 not installed"
fi

# ---------------------------------------------------------------
header "6. SYSTEMD SERVICE ANALYSIS"
# ---------------------------------------------------------------
echo "  Failed units:"
systemctl list-units --failed --no-pager 2>/dev/null | tail -n +2 | head -5
echo ""
echo "  Critical service status:"
for svc in docker apache2 nginx ssh mongod postgresql; do
  if systemctl is-active "$svc" &>/dev/null; then
    echo "  $svc: $(systemctl is-active $svc) (enabled: $(systemctl is-enabled $svc 2>/dev/null || echo 'N/A'))"
  fi
done

# ---------------------------------------------------------------
header "7. TRAFFIC & REQUEST ANALYSIS"
# ---------------------------------------------------------------
echo "  --- Listening ports ---"
ss -tlnp 2>/dev/null | tail -n +2 | while read -r line; do
  proto=$(echo "$line" | awk '{print $1}')
  addr=$(echo "$line" | awk '{print $5}')
  proc=$(echo "$line" | grep -oP 'users:\(\(\K[^)]+' | tr ',' ' ')
  echo "  $addr ($proc)"
done

echo ""
echo "  --- Port 8080 traffic (Java backend) ---"
apache_log="/var/log/apache2/kbook_access.log"
if [ -f "$apache_log" ]; then
  total=$(wc -l < "$apache_log" 2>/dev/null || echo 0)
  success=$(grep -c ' 200 ' "$apache_log" 2>/dev/null || true); success=$(( success + 0 ))
  client_err=$(grep -c ' 40[0-9] ' "$apache_log" 2>/dev/null || true); client_err=$(( client_err + 0 ))
  server_err=$(grep -c ' 50[0-9] ' "$apache_log" 2>/dev/null || true); server_err=$(( server_err + 0 ))
  redirects=$(grep -c ' 30[0-9] ' "$apache_log" 2>/dev/null || true); redirects=$(( redirects + 0 ))
  pending=$(( total - success - client_err - server_err - redirects ))
  echo "  Log file: $apache_log"
  echo "  Total requests: $total"
  echo "  Success (2xx): $success"
  echo "  Redirects (3xx): $redirects"
  echo "  Client errors (4xx): $client_err"
  echo "  Server errors (5xx): $server_err"
  echo "  Other: $pending"
  echo ""
  echo "  Requests per path (top 10):"
  grep -oP '(GET|POST|PUT|DELETE|PATCH)\s+\S+' "$apache_log" 2>/dev/null | awk '{print $2}' | sort | uniq -c | sort -rn | head -10
  echo ""
  echo "  IPs with most requests (top 5):"
  awk '{print $1}' "$apache_log" 2>/dev/null | sort | uniq -c | sort -rn | head -5
  echo ""
  echo "  Response time outliers (slow requests) --- from Spring logs:"
  docker logs kbookiadvcloud-server-1 --tail 500 2>/dev/null | grep -oP 'completed in \K[0-9.]+' | sort -rn | head -3 | while read -r t; do
    warn "Request took ${t}ms"
  done
else
  echo "  Apache log not found at $apache_log"
fi

echo ""
echo "  --- Port 8081 traffic (Docker proxied) ---"
# Requests proxied via Apache to Docker on 8081
for f in "$apache_log" "/var/log/apache2/bhai_access.log"; do
  if [ -f "$f" ]; then
    api_count=$(grep -c '/api/v1' "$f" 2>/dev/null || true); api_count=$(( api_count + 0 ))
    api_2xx=$(grep '/api/v1' "$f" 2>/dev/null | grep -c ' 200 ' || true); api_2xx=$(( api_2xx + 0 ))
    api_5xx=$(grep '/api/v1' "$f" 2>/dev/null | grep -c ' 50[0-9] ' || true); api_5xx=$(( api_5xx + 0 ))
    echo "  File: $f"
    echo "  API requests: $api_count  (2xx: $api_2xx, 5xx: $api_5xx)"
  fi
done

echo ""
echo "  --- Apache proxy health (CLOSE_WAIT check) ---"
close_wait=$(ss -tan 2>/dev/null | grep -c 'CLOSE_WAIT' || true); close_wait=$(( close_wait + 0 ))
time_wait=$(ss -tan 2>/dev/null | grep -c 'TIME_WAIT' || true); time_wait=$(( time_wait + 0 ))
established=$(ss -tan 2>/dev/null | grep -c 'ESTAB' || true); established=$(( established + 0 ))
echo "  ESTABLISHED: $established  CLOSE_WAIT: $close_wait  TIME_WAIT: $time_wait"
if [ "$close_wait" -gt 10 ]; then
  fail "High CLOSE_WAIT ($close_wait) - Apache proxy connections not being cleaned up"
fi

echo ""
echo "  --- Connection state summary ---"
ss -s 2>/dev/null | head -5

# ---------------------------------------------------------------
header "8. ERROR LOG ANALYSIS"
# ---------------------------------------------------------------
echo "  --- Apache error log (last 20 lines with errors) ---"
for errlog in /var/log/apache2/kbook_error.log /var/log/apache2/error.log; do
  if [ -f "$errlog" ]; then
    err_count=$(grep -ci 'error\|warn\|fail' "$errlog" 2>/dev/null || true); err_count=$(( err_count + 0 ))
    echo "  $errlog: $err_count error/warn lines"
    grep -i 'error\|warn\|fail' "$errlog" 2>/dev/null | tail -5 | while read -r line; do echo "    $line"; done
  fi
done

echo ""
echo "  --- Auth log anomalies (last 10 failed logins) ---"
grep 'Failed password\|Connection closed\|authentication failure' /var/log/auth.log 2>/dev/null | tail -5 | while read -r line; do echo "    $line"; done

echo ""
echo "  --- Spring Boot app errors ---"
docker logs kbookiadvcloud-server-1 --tail 200 2>/dev/null | grep -i 'error\|exception\|OOM\|killed\|OutOfMemoryError\|StackOverflow\|NullPointer' | tail -10 | while read -r line; do echo "    $line"; done

# ---------------------------------------------------------------
header "9. DATABASE DIAGNOSTICS"
# ---------------------------------------------------------------
if docker exec kbookiadvcloud-postgres-1 pg_isready -U kbookuser &>/dev/null; then
  echo "  PostgreSQL: CONNECTED"
  echo ""
  echo "  Database size:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT pg_database_size('kbook_saas')/1024/1024 as size_mb;" 2>/dev/null
  echo ""
  echo "  Table count:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null
  echo ""
  echo "  Long-running queries (>1s):"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT pid, now() - pg_stat_activity.query_start AS duration, state, query
    FROM pg_stat_activity
    WHERE state != 'idle' AND now() - pg_stat_activity.query_start > interval '1 second'
    ORDER BY duration DESC;" 2>/dev/null | head -15
  echo ""
  echo "  Connection count per state:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT state, count(*) FROM pg_stat_activity GROUP BY state;" 2>/dev/null
  echo ""
  echo "  Cache hit ratio:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT ROUND(blks_hit::numeric / NULLIF(blks_hit+blks_read, 0) * 100, 2) AS cache_hit_pct
    FROM pg_stat_database WHERE datname='kbook_saas';" 2>/dev/null
  echo ""
  echo "  Transaction stats (since last reset):"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT xact_commit, xact_rollback, deadlocks
    FROM pg_stat_database WHERE datname='kbook_saas';" 2>/dev/null
  echo ""
  echo "  Unused/duplicate indexes:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT schemaname, tablename, indexname, idx_scan
    FROM pg_stat_user_indexes
    WHERE idx_scan = 0 AND indexname NOT LIKE '%_pkey';" 2>/dev/null | head -10
  echo ""
  echo "  Table size ranking:"
  docker exec kbookiadvcloud-postgres-1 psql -U kbookuser -d kbook_saas -c "
    SELECT relname AS table, n_live_tup AS rows, pg_size_pretty(pg_total_relation_size(relid)) AS total_size
    FROM pg_stat_user_tables
    ORDER BY pg_total_relation_size(relid) DESC LIMIT 10;" 2>/dev/null
else
  echo "  PostgreSQL: UNREACHABLE"
fi

# MongoDB
echo ""
if command -v mongod &>/dev/null && systemctl is-active mongod &>/dev/null 2>&1; then
  echo "  MongoDB: RUNNING"
  echo "  DB stats:"
  mongo --quiet --eval "printjson(db.stats())" 2>/dev/null | head -15 || echo "  (mongo shell not accessible)"
else
  echo "  MongoDB: not running or not accessible (process may be idle)"
fi

# ---------------------------------------------------------------
header "10. SECURITY AUDIT"
# ---------------------------------------------------------------
echo "  --- SSL certificate expiry ---"
for domain in kbook.iadv.cloud bhai.iadv.cloud iadv.cloud shop.iadv.cloud; do
  cert_file="/etc/letsencrypt/live/$domain/fullchain.pem"
  if [ -f "$cert_file" ]; then
    expiry=$(openssl x509 -noout -dates -in "$cert_file" 2>/dev/null | grep notAfter | cut -d= -f2)
    expiry_ts=$(date -d "$expiry" +%s 2>/dev/null || true); expiry_ts=$(( expiry_ts + 0 ))
    now_ts=$(date +%s)
    days_left=$(( (expiry_ts - now_ts) / 86400 ))
    if [ "$days_left" -lt 14 ]; then
      fail "$domain: EXPIRING in $days_left days ($expiry)"
    elif [ "$days_left" -lt 30 ]; then
      warn "$domain: $days_left days remaining ($expiry)"
    else
      pass "$domain: $days_left days remaining ($expiry)"
    fi
  fi
done

echo ""
echo "  --- Open ports (public) ---"
ss -tlnp 2>/dev/null | grep -v '127.0.0.1:' | grep -v '127.0.0.53:' | grep -v '::1:' | while read -r line; do
  addr=$(echo "$line" | awk '{print $5}')
  proc=$(echo "$line" | grep -oP 'users:\(\(\K[^)]+' | head -1)
  echo "  LISTEN $addr ($proc)"
done

echo ""
echo "  --- SSH security ---"
if [ -f /etc/ssh/sshd_config ]; then
  echo "  Port: $(grep -oP '^Port\s+\K.*' /etc/ssh/sshd_config 2>/dev/null || echo '22 (default)')"
  echo "  Root login: $(grep -oP '^PermitRootLogin\s+\K\S+' /etc/ssh/sshd_config 2>/dev/null || echo 'prohibit-password (default)')"
  echo "  Password auth: $(grep -oP '^PasswordAuthentication\s+\K\S+' /etc/ssh/sshd_config 2>/dev/null || echo 'yes (default)')"
fi

echo ""
echo "  --- Firewall status ---"
if command -v ufw &>/dev/null; then
  if ufw status | grep -q active; then
    echo "  UFW: ACTIVE"
    ufw status verbose | head -15
  else
    warn "UFW: INACTIVE (firewall not enabled)"
  fi
elif command -v iptables &>/dev/null; then
  rules=$(iptables -L -n --line-numbers 2>/dev/null | grep -c 'ACCEPT\|DROP\|REJECT' || true); rules=$(( rules + 0 ))
  echo "  iptables: $rules rules"
fi

echo ""
echo "  --- Recent auth failures ---"
grep 'Failed password' /var/log/auth.log 2>/dev/null | tail -5 | while read -r line; do echo "    $line"; done
fail_count=$(grep -c 'Failed password' /var/log/auth.log 2>/dev/null || true); fail_count=$(( fail_count + 0 ))
echo "  Total failed logins (this log): $fail_count"

# ---------------------------------------------------------------
header "11. RESTART / CRASH ANALYSIS"
# ---------------------------------------------------------------
echo "  --- System reboot history ---"
last -x 2>/dev/null | grep 'reboot' | head -10 || echo "  (no reboot history)"
echo ""
echo "  --- Docker container restarts ---"
docker ps -a --format '{{.Names}} {{.RestartCount}} restarts' 2>/dev/null
echo ""
echo "  --- OOM killer activity ---"
if dmesg 2>/dev/null | grep -qi 'oom\|killed process\|out of memory'; then
  dmesg 2>/dev/null | grep -i 'oom\|killed process' | tail -5 | while read -r line; do
    fail "$line"
  done
else
  pass "No OOM events detected"
fi
echo ""
echo "  --- Service failures in last 24h ---"
journalctl --since "24 hours ago" --no-pager 2>/dev/null | grep -i 'failed\|crash\|segfault\|panic' | tail -10 | while read -r line; do echo "    $line"; done

# ---------------------------------------------------------------
header "12. PERFORMANCE BOTTLENECKS"
# ---------------------------------------------------------------
echo "  --- CPU pressure ---"
load=$(cut -d' ' -f1 /proc/loadavg)
cpu_count=$(nproc)
load_pct=$(echo "$load $cpu_count" | awk '{printf "%d", ($1/$2)*100}' 2>/dev/null)
if [ "$load_pct" -gt 100 ]; then
  fail "Load avg ($load) > CPUs ($cpu_count) = ${load_pct}% - CPU BOUND"
elif [ "$load_pct" -gt 70 ]; then
  warn "Load avg ($load) approaching CPU capacity ($cpu_count)"
else
  pass "Load avg ($load) within range ($cpu_count CPUs)"
fi

echo ""
echo "  --- Memory pressure ---"
mem_avail_pct=$(free | awk 'NR==2{printf "%d", ($7/$2)*100}')
if [ "$mem_avail_pct" -lt 10 ]; then
  fail "Only ${mem_avail_pct}% memory available - CRITICAL"
elif [ "$mem_avail_pct" -lt 20 ]; then
  warn "Only ${mem_avail_pct}% memory available"
else
  pass "${mem_avail_pct}% memory available"
fi

echo ""
echo "  --- Disk I/O pressure ---"
disk_util=$(iostat -x 1 2 2>/dev/null | grep 'sda ' | tail -1 | awk '{print $NF}')
if command -v iostat &>/dev/null; then
  if [ -n "$disk_util" ] && [ "$(printf "%.0f" "$disk_util" 2>/dev/null || echo 0)" -gt 80 ]; then
    fail "Disk utilization at ${disk_util}%"
  else
    pass "Disk utilization: ${disk_util:-N/A}%"
  fi
else
  echo "  (install sysstat for iostat)"
fi

echo ""
echo "  --- Apache connection saturation ---"
apache_workers=$(apache2ctl status 2>/dev/null | grep 'current' | awk '{print $NF}' || echo 'N/A')
echo "  Apache workers: $apache_workers"

echo ""
echo "  --- Network socket pressure ---"
orphan_sockets=$(ss -s 2>/dev/null | grep 'TCP:' | grep -oP 'orphan\s+\K[0-9]+' || true); orphan_sockets=$(( orphan_sockets + 0 ))
echo "  Orphan sockets: $orphan_sockets"
if [ "$orphan_sockets" -gt 500 ]; then
  fail "High orphan count ($orphan_sockets)"
fi

# ---------------------------------------------------------------
header "13. SSL CERTIFICATE STATUS"
# ---------------------------------------------------------------
echo "  Auto-renewal status:"
for domain in kbook.iadv.cloud bhai.iadv.cloud iadv.cloud shop.iadv.cloud; do
  cert_file="/etc/letsencrypt/live/$domain/fullchain.pem"
  if [ -f "$cert_file" ]; then
    issuer=$(openssl x509 -noout -issuer -in "$cert_file" 2>/dev/null | sed 's/.*CN=//')
    san=$(openssl x509 -noout -ext subjectAltName -in "$cert_file" 2>/dev/null | grep DNS | tr ',' '\n' | head -3)
    echo "  $domain:"
    echo "    Issuer: $issuer"
    echo "    SAN: $san"
  fi
done

# ---------------------------------------------------------------
header "14. BACKUP VISIBILITY"
# ---------------------------------------------------------------
echo "  --- Checking for backup mechanisms ---"
backup_found=0
if crontab -l 2>/dev/null | grep -qi 'backup\|dump\|pg_dump\|rsync\|restic\|borg\|duplicity'; then
  echo "  Cron backup jobs found:"
  crontab -l 2>/dev/null | grep -i 'backup\|dump\|pg_dump\|rsync\|restic\|borg\|duplicity' | while read -r line; do echo "    $line"; done
  backup_found=1
fi
if ls /etc/cron.d/* 2>/dev/null | xargs cat 2>/dev/null | grep -qi 'backup\|dump\|pg_dump'; then
  echo "  System cron.d backup jobs exist"
  backup_found=1
fi
if [ -d /var/backups ] && [ "$(ls -A /var/backups 2>/dev/null)" ]; then
  echo "  Backup directory: /var/backups"
  ls -lh /var/backups/ 2>/dev/null | head -10
  backup_found=1
fi
if [ -f "$proj_dir/deploy-production.sh" ] || [ -f "$proj_dir/deploy.sh" ]; then
  echo "  Deployment scripts found: $proj_dir/deploy*.sh"
  backup_found=1
fi
if [ "$backup_found" -eq 0 ]; then
  warn "NO BACKUP SYSTEM DETECTED - critical risk"
fi

# pg_dump specifically
if docker exec kbookiadvcloud-postgres-1 pg_dump --version &>/dev/null; then
  echo "  pg_dump available inside postgres container"
  last_backup=$(ls -lt /var/backups/*.sql 2>/dev/null | head -1 | awk '{print $6,$7,$8}' || echo 'never')
  echo "  Last PG backup: $last_backup"
fi

# ---------------------------------------------------------------
header "15. OPTIMIZATION RECOMMENDATIONS"
# ---------------------------------------------------------------
echo ""

# Swap check
if ! swapon --show 2>/dev/null | grep -q .; then
  warn "1. Enable swap: fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab"
fi

# Docker memory limits check
for c in $(docker ps --format '{{.Names}}'); do
  mem_limit=$(docker inspect "$c" --format '{{.HostConfig.Memory}}' 2>/dev/null)
  if [ "$mem_limit" = "0" ] || [ -z "$mem_limit" ]; then
    warn "2. Container '$c' has NO memory limit - add mem_limit in docker-compose.yml"
  fi
done

# CLOSE_WAIT check
close_wait_count=$(ss -tan 2>/dev/null | grep -c CLOSE_WAIT || true); close_wait_count=$(( close_wait_count + 0 ))
if [ "$close_wait_count" -gt 10 ]; then
  warn "3. $close_wait_count CLOSE_WAIT sockets detected (Apache proxy leak). Fix: ensure Apache KeepAlive matches app Timeout, or tune:"
  echo "       echo 'net.ipv4.tcp_keepalive_time=60' >> /etc/sysctl.conf"
  echo "       echo 'net.ipv4.tcp_keepalive_intvl=10' >> /etc/sysctl.conf"
  echo "       sysctl -p"
fi

# UFW check
if command -v ufw &>/dev/null && ! ufw status | grep -q active; then
  warn "4. Enable firewall: ufw allow OpenSSH && ufw allow 'Apache Full' && ufw enable"
fi

# Disk usage
disk_pct=$(df / | awk 'NR==2{print $5}' | tr -d '%')
if [ "$disk_pct" -gt 80 ]; then
  warn "5. Disk at ${disk_pct}% - clean old logs: find /var/log -name '*.gz' -mtime +30 -delete"
fi

# Java JVM tuning
echo "  6. JVM: -XX:MaxRAMPercentage=75.0 on 31GB = 23GB heap - verify this is needed vs ~4GB for this workload"
java_maxram=$(docker inspect kbookiadvcloud-server-1 2>/dev/null | grep -oP 'MaxRAMPercentage=\K[0-9.]+' || echo 'N/A')
echo "       Current MaxRAMPercentage: ${java_maxram}%"

# SSL renewal cron
if ! crontab -l 2>/dev/null | grep -q certbot; then
  warn "7. No certbot auto-renewal cron. Add: echo '0 */12 * * * root certbot renew --quiet' | sudo tee /etc/cron.d/certbot-renew"
fi

# APK file leftovers
apk_debug="/var/www/kbook.iadv.cloud/kbook-debug.apk"
if [ -f "$apk_debug" ]; then
  apk_size=$(du -h "$apk_debug" 2>/dev/null | cut -f1)
  warn "8. Debug APK found ($apk_size) at $apk_debug - remove for production security"
fi

# Unused packages / services
echo "  9. Audit installed packages: dpkg -l | grep ^rc | wc -l (unused configs)"
echo "  10. Consider sysstat package: apt install sysstat -y (for sar/iostat/mpstat)"

# ---------------------------------------------------------------
header "16. PROXY CONNECTION HEALTH (8080 vs 8081)"
# ---------------------------------------------------------------
echo "  --- 8080 (Java standalone) connections ---"
ss -tan 2>/dev/null | grep ':8080' | awk '{print $1}' | sort | uniq -c | while read -r count state; do
  echo "  $state: $count"
done

echo ""
echo "  --- 8081 (Docker proxy) connections ---"
ss -tan 2>/dev/null | grep ':8081' | awk '{print $1}' | sort | uniq -c | while read -r count state; do
  echo "  $state: $count"
done

echo ""
echo "  --- Apache to backend stuck connections ---"
echo "  (CLOSE_WAIT means Apache is waiting to close - client already disconnected)"
ss -tan 2>/dev/null | grep -E ':8080|:8081' | grep CLOSE_WAIT | while read -r line; do
  echo "    $line"
done

echo ""
echo "  --- HTTP methods used ---"
if [ -f "$apache_log" ]; then
  grep -oP '"(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)' "$apache_log" 2>/dev/null | sort | uniq -c | sort -rn | while read -r count method; do
    echo "  $method: $count"
  done
fi

# ---------------------------------------------------------------
header "17. RECOMMENDED DOCKER-COMPOSE FIX"
# ---------------------------------------------------------------
echo "  Current docker-compose.yml uses host Postgres; production uses container Postgres."
echo "  Ensure .env has proper POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD."
echo ""
echo "  To pull & restart:"
echo "    docker compose -f docker-compose.production.yml pull"
echo "    docker compose -f docker-compose.production.yml up -d"

# ---------------------------------------------------------------
header "SUMMARY"
# ---------------------------------------------------------------
issues=0
warnings=0

echo ""
echo "  Scoring each category:"
echo ""

# Resource score
load_ok=$(awk 'BEGIN{print ('"$(cut -d' ' -f1 /proc/loadavg)"' < '"$(nproc)"')?1:0}')
ram_ok=$(awk 'BEGIN{print ('"$(free | awk 'NR==2{print $7/$2*100}')"' > 20)?1:0}')
disk_ok=$(awk 'BEGIN{print ('"$(df / | awk 'NR==2{print $5}' | tr -d '%')"' < 80)?1:0}')
swap_ok=$(swapon --show 2>/dev/null | grep -q . && echo 0 || echo 1)
res_score=$((load_ok + ram_ok + disk_ok + (1-swap_ok)))
echo "  Resources:    $res_score/4  (CPU=$load_ok RAM=$ram_ok Disk=$disk_ok Swap=$([ $swap_ok -eq 1 ] && echo 0 || echo 1))"
[ "$swap_ok" -eq 1 ] && warnings=$((warnings+1))

# Docker score
docker_running=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -c . || true); docker_running=$(( docker_running + 0 ))
docker_healthy=$(docker ps --filter health=healthy --format '{{.Names}}' 2>/dev/null | grep -c . || true); docker_healthy=$(( docker_healthy + 0 ))
docker_score=$(( docker_healthy * 2 / (docker_running > 0 ? docker_running : 1) ))
echo "  Docker:       $docker_score/2  ($docker_healthy/$docker_running healthy)"

# Error score
api_5xx_count=$(grep -c ' 50[0-9] ' "$apache_log" 2>/dev/null || true); api_5xx_count=$(( api_5xx_count + 0 ))
err_score=$([ "$api_5xx_count" -eq 0 ] && echo 1 || echo 0)
[ "$api_5xx_count" -gt 0 ] && issues=$((issues+1))
echo "  Errors:       $err_score/1  (5xx count: $api_5xx_count)"

# SSL score
ssl_min_days=999
for domain in kbook.iadv.cloud bhai.iadv.cloud iadv.cloud shop.iadv.cloud; do
  cert_file="/etc/letsencrypt/live/$domain/fullchain.pem"
  if [ -f "$cert_file" ]; then
    expiry=$(openssl x509 -noout -dates -in "$cert_file" 2>/dev/null | grep notAfter | cut -d= -f2)
    expiry_ts=$(date -d "$expiry" +%s 2>/dev/null || echo 0)
    now_ts=$(date +%s)
    days_left=$(( (expiry_ts - now_ts) / 86400 ))
    [ "$days_left" -lt "$ssl_min_days" ] && ssl_min_days=$days_left
  fi
done
ssl_score=$([ "$ssl_min_days" -gt 30 ] && echo 2 || ([ "$ssl_min_days" -gt 14 ] && echo 1 || echo 0))
echo "  SSL:          $ssl_score/2  (min $ssl_min_days days to expiry)"

# Security score
ufw_active=$(command -v ufw &>/dev/null && ufw status | grep -q active && echo 1 || echo 0)
ssh_hardened=$(grep -q '^PasswordAuthentication no' /etc/ssh/sshd_config 2>/dev/null && echo 1 || echo 0)
sec_score=$((ufw_active + ssh_hardened))
[ "$ufw_active" -eq 0 ] && warnings=$((warnings+1))
[ "$ssh_hardened" -eq 0 ] && warnings=$((warnings+1))
echo "  Security:     $sec_score/2  (UFW=$ufw_active SSH-Hardened=$ssh_hardened)"

# Backup score
backup_score=$([ "$backup_found" -gt 0 ] && echo 1 || echo 0)
[ "$backup_found" -eq 0 ] && issues=$((issues+1))
echo "  Backups:      $backup_score/1"

# Proxy health score
proxy_score=$([ "$close_wait_count" -lt 10 ] && echo 1 || echo 0)
[ "$close_wait_count" -ge 10 ] && issues=$((issues+1))
echo "  Proxy health: $proxy_score/1  (CLOSE_WAIT=$close_wait_count)"

total_score=$((res_score + docker_score + err_score + ssl_score + sec_score + backup_score + proxy_score))
max_score=13

echo ""
echo -e "${CYAN}================================================================${NC}"
if [ "$total_score" -ge 11 ]; then
  echo -e "  HEALTH SCORE: ${GREEN}${total_score}/${max_score}${NC}"
elif [ "$total_score" -ge 7 ]; then
  echo -e "  HEALTH SCORE: ${YELLOW}${total_score}/${max_score}${NC}"
else
  echo -e "  HEALTH SCORE: ${RED}${total_score}/${max_score}${NC}"
fi
echo -e "  Critical issues: ${RED}${issues}${NC}  Warnings: ${YELLOW}${warnings}${NC}  Passed: ${GREEN}$((max_score - issues - warnings))${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo "  Report generated: $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "  Script version: 1.0.0"

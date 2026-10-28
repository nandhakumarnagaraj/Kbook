#!/usr/bin/env bash
set -euo pipefail

# Launch the Easebuzz MCP server for one environment.
# Usage: mcp-easebuzz.sh dashboard|prod|uat

ENV_NAME="${1:-}"
ENV_FILE="${EASEBUZZ_ENV_FILE:-/var/www/kbook.iadv.cloud/.env}"
SERVER="${EASEBUZZ_SERVER:-/var/www/kbook.iadv.cloud/mcp/easebuzz/server.mjs}"

case "$ENV_NAME" in
  dashboard|prod|uat) ;;
  *)
    echo "usage: $0 dashboard|prod|uat" >&2
    exit 2
    ;;
esac

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

export EASEBUZZ_ENV="$ENV_NAME"
export EASEBUZZ_KEY="${EASEBUZZ_MERCHANT_KEY:-}"
export EASEBUZZ_SALT="${EASEBUZZ_SALT:-}"

case "$ENV_NAME" in
  dashboard)
    export EASEBUZZ_API="dashboard"
    export EASEBUZZ_BASE_URL="${EASEBUZZ_DASHBOARD_BASE_URL:-https://dashboard.easebuzz.in}"
    ;;
  prod)
    export EASEBUZZ_API="payment"
    export EASEBUZZ_BASE_URL="${EASEBUZZ_PAYMENT_BASE_URL:-https://pay.easebuzz.in}"
    ;;
  uat)
    export EASEBUZZ_API="payment"
    export EASEBUZZ_BASE_URL="${EASEBUZZ_UAT_BASE_URL:-https://testpay.easebuzz.in}"
    export EASEBUZZ_KEY="${EASEBUZZ_UAT_KEY:-$EASEBUZZ_KEY}"
    export EASEBUZZ_SALT="${EASEBUZZ_UAT_SALT:-$EASEBUZZ_SALT}"
    export EASEBUZZ_DASHBOARD_BASE_URL="${EASEBUZZ_UAT_DASHBOARD_BASE_URL:-https://testdashboard.easebuzz.in}"
    export EASEBUZZ_WIRE_BASE_URL="${EASEBUZZ_UAT_WIRE_BASE_URL:-https://testdashboard.easebuzz.in}"
    ;;
esac

exec node "$SERVER"

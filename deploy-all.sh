#!/usr/bin/env bash
# Deploy BOTH halves of the KhanaBook stack in the correct order:
#   1. Backend  (Spring Boot jar -> docker containers)   via deploy-production.sh
#   2. Web-admin (Angular build -> Apache docroot)       via deploy-web.sh
#
# Why this exists: backend and frontend deploy through two independent scripts.
# Running only deploy-production.sh leaves the dashboard serving a stale bundle
# whose API contract can drift behind the server's (this is exactly how the
# "Invalid role: MANAGER" incident happened on 2026-09-16 — server collapsed
# roles on Sep 7, dashboard kept offering MANAGER until Sep 16).
#
# Use this for any deploy that touches web-admin/ OR server/ — i.e. use this
# by default. The individual scripts remain available for targeted deploys.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ""
echo "========================================================"
echo " KhanaBook deploy: backend + web-admin"
echo "========================================================"

echo ""
echo "======== STEP 1/2: BACKEND (server jar + containers) ========"
# Tell deploy-production.sh the web will ship in this same run, so its
# "web-admin not deployed" reminder stays silent. If the web deploy below
# fails, set -e aborts this script non-zero — the failure is loud either way.
export WEB_DEPLOYED=1
bash "$ROOT_DIR/deploy-production.sh"

echo ""
echo "======== STEP 2/2: WEB-ADMIN (Angular dashboard) ========"
bash "$ROOT_DIR/deploy-web.sh"

echo ""
echo "========================================================"
echo " Deploy complete: server + web-admin both current."
echo "========================================================"

# Kbook Production Stack

This stack runs Kbook backend and PostgreSQL in one private Docker Compose network.

## Services

- `server`
  - Spring Boot backend
  - exposed only on `127.0.0.1:8081`
- `postgres`
  - PostgreSQL 16
  - not exposed on a public host port
  - persistent volume: `pgdata`

The edge web server (nginx in production) proxies `/api/` to `127.0.0.1:8081`. The API context path is `/api/v1` — there is no `/api/v2`.

## Required `.env` values

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_CLIENT_ID`
- `WHATSAPP_META_ACCESS_TOKEN`
- `WHATSAPP_META_PHONE_NUMBER_ID`
- `WHATSAPP_META_OTP_TEMPLATE_NAME`
- `PAYMENT_CRYPTO_SECRET`
- `APP_BASE_URL`
- `CORS_ALLOWED_ORIGINS`
- `EASEBUZZ_MERCHANT_KEY` — Easebuzz master merchant key (production)
- `EASEBUZZ_SALT` — Easebuzz hash salt (production)
- `EASEBUZZ_PAYMENT_BASE_URL` — `https://pay.easebuzz.in` (production) or `https://testpay.easebuzz.in` (sandbox)
- `EASEBUZZ_DASHBOARD_BASE_URL` — `https://dashboard.easebuzz.in` (production) or `https://testdashboard.easebuzz.in` (sandbox)

## First deployment

1. Update `.env` with production values.
2. Build and start the new stack:
   - `./ops/deploy-production.sh`
3. Restore production data into the compose Postgres:
   - `./ops/restore_postgres.sh /path/to/backup.sql.gz`
4. Verify:
   - `docker compose --env-file .env -f ops/docker-compose.production.yml ps`
   - `curl http://127.0.0.1:8081/api/v1/actuator/health`

## Backup

Run:

- `./ops/backup_postgres.sh`

This writes compressed dumps to `backups/postgres/` and removes backups older than 14 days.

## Cutover notes

- Stop the current host-run `kbook-server.service` before binding the compose backend to `127.0.0.1:8081`.
- The edge proxy is nginx, not Apache (`apache2` is stopped). The canonical vhost is
  tracked at `ops/nginx-kbook.iadv.cloud.conf` and installed at
  `/etc/nginx/sites-enabled/kbook.iadv.cloud`. It proxies `/api/` to `127.0.0.1:8081`
  and serves the web-admin SPA.
- API and CDN locations must stay `^~` (e.g. `location ^~ /api/`). Otherwise the
  static-asset regex location (`~* \.(js|css|...)$`) shadows them and returns 404 for
  proxied assets like the Swagger UI bundle.
- Do not expose PostgreSQL directly on `5432` unless there is a specific operational requirement.

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

Apache should continue proxying `/api/v1/` to `127.0.0.1:8081`.

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

## First deployment

1. Update `.env` with production values.
2. Build and start the new stack:
   - `./deploy-production.sh`
3. Restore production data into the compose Postgres:
   - `./ops/restore_postgres.sh /path/to/backup.sql.gz`
4. Verify:
   - `docker compose --env-file .env -f docker-compose.production.yml ps`
   - `curl http://127.0.0.1:8081/api/v1/actuator/health`

## Backup

Run:

- `./ops/backup_postgres.sh`

This writes compressed dumps to `backups/postgres/` and removes backups older than 14 days.

## Cutover notes

- Stop the current host-run `kbook-server.service` before binding the compose backend to `127.0.0.1:8081`.
- Keep Apache unchanged if it already proxies to `127.0.0.1:8081`.
- Do not expose PostgreSQL directly on `5432` unless there is a specific operational requirement.

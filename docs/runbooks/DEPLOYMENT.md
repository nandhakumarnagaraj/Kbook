# KhanaBook — Deployment Runbook

> One place for shipping server, web-admin and Android. Consolidates the deploy
> instructions that previously lived in both `docs/meta/AGENTS.md` and `ops/`.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Authoritative stack detail:
> `../../ops/PRODUCTION_STACK.md`.

## 1. Production topology

| Piece | Value |
|---|---|
| Host | live VPS |
| Repo root on VPS | `/var/www/kbook.iadv.cloud` |
| Orchestration | Docker Compose (`ops/docker-compose.production.yml`), project name **`kbookiadvcloud`** (pinned — the name matters for scripts) |
| Services | `postgres` (16-alpine, `mem_limit: 4g`), `server` (Spring Boot, `mem_limit: 6g`) |
| Backend port | `127.0.0.1:8081` — **not publicly exposed** |
| Public entry | Apache → `DocumentRoot /var/www/kbook.iadv.cloud/web-admin/dist/khanabook-web-admin/browser`, proxying `/api/v1/` to `127.0.0.1:8081` |
| Health | `https://kbook.iadv.cloud/api/v1/actuator/health` (open; `{"status":"UP"}`) |
| Secrets | `.env` at the repo root (never committed) |
| Server profile | `SPRING_PROFILES_ACTIVE=prod` |

Legacy note: a systemd `khanabook.service` path exists in `ops/`. It is **superseded**
by Compose — do not use it.

## 2. Before any deploy

1. **Backup if a migration may run:** `./ops/backup_postgres.sh`
   (daily job: `ops/daily_backup.sh`).
2. Confirm `.env` is present and current on the VPS.
3. `git pull` on the VPS — the deploy stamps the build with the checked-out commit.

## 3. Backend deploy

```bash
cd /var/www/kbook.iadv.cloud
git pull
./ops/deploy-production.sh
```

What the script actually does:

1. Exports `GIT_COMMIT_ID`, `GIT_BRANCH`, `GIT_COMMIT_TIME` (Maven's context is
   `services/server/`, so the builder cannot read the repo; without these the jar ships with an
   empty `git.properties`).
2. `docker compose build server`.
3. `up -d postgres`, then runs `ops/flyway_reconcile_checksums.sh` — a NULL checksum or
   half-applied script is a **hard stop**, because the schema would not match the code.
4. `up -d server`.
5. Polls `http://127.0.0.1:8081/api/v1/actuator/health` for up to 120 s.
6. **Asserts container memory limits are in force** (`server`, `postgres`). Compose
   limits apply at container create, so a deploy that recreates containers without them
   exits 0 while leaving an unbounded JVM — the script fails the deploy instead.
7. Warns if web-admin was not shipped (`WEB_DEPLOYED=1` suppresses it; `ops/deploy-all.sh`
   always ships both).

Manual equivalent (only if the script is unavailable):

```bash
docker compose --env-file .env -f ops/docker-compose.production.yml build server
docker compose --env-file .env -f ops/docker-compose.production.yml up -d postgres server
curl -fsS http://127.0.0.1:8081/api/v1/actuator/health
```

## 4. Web-admin deploy

Runs **separately** from the backend (it is not part of `deploy-production.sh`).

```bash
cd /var/www/kbook.iadv.cloud
git pull
./ops/deploy-web.sh            # npm ci + ng build + backup docroot + rsync
WEB_ROOT=/path/to/docroot ./ops/deploy-web.sh   # override docroot
SKIP_BUILD=1 ./ops/deploy-web.sh                # reuse existing dist/
```

- Build output: `apps/web-admin/dist/khanabook-web-admin/browser/` (`base href="/"`).
- Default `WEB_ROOT` **is that build directory** — the docroot is the build output, so
  the script backs up the current docroot to `<docroot>.bak.<timestamp>` before syncing.
- Production API base is hardcoded to `https://kbook.iadv.cloud/api/v1` in
  `environment.prod.ts`.
- Always redeploy the web app when a deploy changes an API contract. A backend-only
  deploy previously left the dashboard stale and caused the MANAGER-role incident on
  2026-09-16 — the script now warns about it.

## 5. Android release

```bash
cd apps/android
./gradlew.bat bundleRelease     # Play Store
./gradlew.bat assembleRelease   # APK
```

Release builds hard-fail at task-graph time unless all of these are set correctly:

| Requirement | Where |
|---|---|
| Signing: `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | `local.properties` |
| Explicit version: `RELEASE_VERSION_CODE`, `RELEASE_VERSION_NAME` | `local.properties`, Gradle property, or env |
| Real `GOOGLE_WEB_CLIENT_ID` (not a placeholder) | config |
| `BACKEND_URL` — must be **HTTPS** and host `kbook.iadv.cloud` | config |

Network security: `network_security_config.xml` pins three SPKI SHA-256 keys for
`kbook.iadv.cloud` with `expiration="2028-06-04"` and cleartext disabled. **Read the
pin-rollover runbook comment in that file before rotating the server certificate** — if
the server cert changes without a matching pin, every production app hard-fails TLS
until it updates. Release checklist: `../android/PLAYSTORE_RELEASE_CHECKLIST.md`.

## 6. Verify after deploy

```bash
curl -fsS https://kbook.iadv.cloud/api/v1/actuator/health
docker compose --env-file .env -f ops/docker-compose.production.yml ps
docker compose --env-file .env -f ops/docker-compose.production.yml logs -n 100 server
```

`/actuator/info` shows the git commit/branch/time of the running jar — use it to confirm
the deploy actually shipped the commit you think it did.

## 7. Rollback

| Scenario | Action |
|---|---|
| Bad server build, no migration ran | `git checkout <previous>` → `./ops/deploy-production.sh` (rebuilds and restarts) |
| **A Flyway migration ran** | A JAR rollback is **not enough** — restore the DB: `./ops/restore_postgres.sh <backup.sql.gz>`, then deploy the previous commit |
| Bad web build | `deploy-web.sh` keeps `<docroot>.bak.<timestamp>` — restore that directory |
| Container unbounded/wedged | `docker compose ... up -d --force-recreate server` (then confirm the memory-limit assertion) |

Migration safety helpers in `ops/`: `flyway_reconcile_checksums.sh`,
`flyway_repair_v82_checksum.sh`.

## 8. Staging

```bash
./ops/deploy-staging.sh          # docker-compose.staging.yml
./ops/reset-staging.sh           # wipe + reseed
```

Setup detail: `../qa/STAGING_SETUP_RUNBOOK.md`.

## 9. Troubleshooting

| Symptom | First move |
|---|---|
| 500s from the app | Client shows `errorId`; grep it: `docker compose ... logs server \| grep <errorId>` |
| Backend not healthy | Check `logs server`; if Flyway aborted, verify checksums and restore if needed |
| Web shows stale data | A backend-only deploy changed a contract — run `deploy-web.sh` |
| Deploy exits with the memory-limit error | Compose recreated containers without limits; re-run `up -d` and confirm `config --format json` shows them |
| Sync 409 storms after deploy | Check for a schema push/contract change; see `../engineering/SYNC_CONTRACT.md` §4 |

## 10. Do not

- Run `ops/deploy.sh` (legacy) or the systemd unit.
- Deploy with a dirty tree on the VPS — the commit stamp will not match the running code.
- Edit `.env` values without recording them in the credential rotation plan.
- Skip the web deploy when an API contract changed.

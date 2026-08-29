# KhanaBook — Staging Setup Runbook

> ⚠️ DO NOT RUN ANY OF THIS AGAINST PRODUCTION.
> Staging is a fully isolated stack for the 5-POS physical acceptance tests. Production
> (`kbook.iadv.cloud`, DB `kbook_saas`, volume `pgdata`, `/var/www/cdn.kbook.iadv.cloud`,
> `/var/www/kbook-private`) must never appear in any staging command, env, or mount.

## 1. Architecture
```
5 Android test phones ──HTTPS──> staging.kbook.iadv.cloud ──Apache──> 127.0.0.1:8091
                                                                         │
                                                              kbook-staging-server (Spring Boot)
                                                                         │  (staging-backend network)
                                                              kbook-staging-postgres (DB kbook_staging, vol pgdata-staging)

PRODUCTION (kbook.iadv.cloud / 8081 / kbook_saas / pgdata) — SEPARATE, NEVER IN TEST PATH.
```

## 2. Production vs Staging isolation
| Resource | Production | Staging |
|---|---|---|
| Compose project | kbookiadvcloud | kbook-staging |
| Server container | kbookiadvcloud-server-1 | kbook-staging-server |
| Postgres container | kbookiadvcloud-postgres-1 | kbook-staging-postgres |
| DB name | kbook_saas | kbook_staging |
| DB volume | pgdata | pgdata-staging |
| App host port | 127.0.0.1:8081 | 127.0.0.1:8091 |
| CDN dir | /var/www/cdn.kbook.iadv.cloud | /var/www/staging-cdn |
| Private docs | /var/www/kbook-private | /var/www/staging-private |
| Easebuzz | production keys | SANDBOX only |
| Secrets | prod | new staging-only |
| Public URL | kbook.iadv.cloud | staging.kbook.iadv.cloud (later) |

## 3. Production dependency audit
| Dependency | Classification in staging |
|---|---|
| Database | ISOLATED — own DB `kbook_staging` + volume `pgdata-staging`; migrations create schema fresh |
| CDN storage | ISOLATED — `/var/www/staging-cdn` (own dir, own mount) |
| Private documents | ISOLATED — `/var/www/staging-private` |
| JWT / payment-crypto secrets | ISOLATED — new staging-only values (never copy prod) |
| Easebuzz | REQUIRES SANDBOX CREDENTIALS — testpay/testdashboard endpoints; sandbox key/salt |
| Email (SMTP) | DISABLED (`EMAIL_ENABLED=false`) |
| FCM push | DISABLED (`FIREBASE_CREDENTIALS_PATH` empty) |
| Google OAuth | UNAVAILABLE unless a staging client id is added (optional) |
| WhatsApp OTP | DISABLED (blank creds) — use phone/password test accounts |
| Marketplace (Zomato/Swiggy) | NOT CONFIGURED in staging (no webhooks) |
| Webhook/payment callbacks | Point at staging URL only — never prod processing paths |

## 4. Prerequisites (on VPS, later — not now)
- Docker + `docker compose`.
- `ops/.env.staging` created from `ops/.env.staging.example`, all CHANGE_ME filled with NEW values.
- Host dirs: `mkdir -p /var/www/staging-cdn/{restaurants,tmp} /var/www/staging-private/documents`.
- Enough RAM for a second Postgres + JVM (server uses MaxRAMPercentage=50 in staging).

## 5. First-time deployment (VPS)
```
cd /var/www/kbook.iadv.cloud     # repo root
cp ops/.env.staging.example ops/.env.staging   # then edit, fill CHANGE_ME
mkdir -p /var/www/staging-cdn/{restaurants,tmp} /var/www/staging-private/documents
bash ops/deploy-staging.sh
```
Migrations run automatically on server boot (Flyway V1..V89 against the empty `kbook_staging`).

## 6. Health verification
```
curl -fsS http://127.0.0.1:8091/api/v1/actuator/health    # expect {"status":"UP"}
docker compose -p kbook-staging --env-file ops/.env.staging -f docker-compose.staging.yml ps
```

## 7. Seed procedure (idempotent — safe to re-run)
Seed via the app's real API flows so invariants are respected (preferred over raw SQL).
Base URL for these calls on the VPS: `http://127.0.0.1:8091/api/v1`.

1. OWNER signup (phone/password) — creates restaurant + OWNER. Re-running returns existing.
2. Configure profile: shop name, GST enabled + gstin + gstPercentage, tax config, payment config.
3. Create SHOP_ADMIN staff user under the restaurant.
4. Create representative menu: 2–3 categories, ~8 items with prices, at least one veg/non-veg + one with a variant.
5. Activate 5 terminals POS-1..POS-5 (each phone's first online activation assigns a series).
   The 5-terminal limit is server-enforced; a 6th activation is expected to be rejected (that's LAB material).
Idempotency: signup/onboarding detect existing records; menu items are unique by name per restaurant.
Record all IDs (restaurant, owner, shop-admin, 5 terminal ids) into the LAB evidence blocks.

## 8. Baseline snapshot ("known state at T0")
After seeding:
```
docker exec kbook-staging-postgres sh -c 'pg_dump -U $POSTGRES_USER kbook_staging' | gzip > staging_baseline_$(date +%Y%m%dT%H%M%SZ).sql.gz
```
Keep this so every LAB run starts from an identical, known state.

## 9. Staging API URL
- Local on VPS: `http://127.0.0.1:8091/api/v1`.
- Public (after Apache/TLS step): `https://staging.kbook.iadv.cloud/api/v1`.

## 10. Reset safely
```
bash ops/reset-staging.sh    # requires typing 'RESET STAGING'; wipes pgdata-staging only
```
Then re-run deploy + seed, or restore the baseline snapshot.

## 11. Stop staging
```
docker compose -p kbook-staging --env-file ops/.env.staging -f docker-compose.staging.yml down
```
(`down` keeps the volume; use reset-staging.sh to also drop the DB volume.)

## 12. Logs
```
docker compose -p kbook-staging --env-file ops/.env.staging -f docker-compose.staging.yml logs -n 200 server
```

## 13. External integration configuration
- Easebuzz: sandbox key/salt in `ops/.env.staging`; endpoints already default to testpay/testdashboard.
- Email/FCM/WhatsApp/Google/marketplace: off by default; enable per-test only with test accounts.

## 14. DNS / Apache / TLS (LATER — separate approved step)
```
staging.kbook.iadv.cloud  --A-->  <VPS IP>            (DNS: you create)
Apache vhost (new file, does NOT touch prod vhost):
  ProxyPass /api/v1/ http://127.0.0.1:8091/api/v1/
  + Let's Encrypt cert for staging.kbook.iadv.cloud
```
I will draft the vhost and you approve/apply it. Do not modify the production vhost.

## 15. Troubleshooting
- Health never UP: `logs server` — usually a missing/CHANGE_ME env var or Flyway failure on empty DB.
- Port in use: something else on 8091 — change `SERVER_PORT` in `ops/.env.staging`.
- Deploy guard aborts: `ops/.env.staging` still contains prod-looking values — fix them.

## 16. DO NOT RUN AGAINST PRODUCTION
No staging command may reference: `kbook_saas`, `pgdata` (prod volume), `kbookiadvcloud`,
`/var/www/cdn.kbook.iadv.cloud`, `/var/www/kbook-private`, port 8081, or production Easebuzz keys.
`deploy-staging.sh` and `reset-staging.sh` both hard-abort if they detect these.

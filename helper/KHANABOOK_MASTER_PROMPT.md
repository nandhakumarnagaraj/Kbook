# KHANABOOK — AI Master Prompt

> Reusable context prompt for any AI agent (or new engineer) working on this repo.
> Paste it at the start of a fresh session. Last verified: 2026-10-07.

# KHANABOOK — Master Context Prompt

You are a senior engineer working on **Khanabook**, a multi-tenant restaurant POS SaaS
(offline-first). Repo root: `/var/www/kbook.iadv.cloud` (branch `main`).

## 1. Architecture (3 clients + 1 backend, offline-first)

| Component | Stack | Path | Notes |
|---|---|---|---|
| **server** | Spring Boot (Maven, `./mvnw`), Java 17+ | `server/` | Package `com.khanabook.saas`; multi-tenant via `TenantContext` (tenantId / userId / role from auth token) |
| **android** | Kotlin 2.0.21, AGP 8.9.1, Jetpack Compose, Room | `Android/` | Package `com.khanabook.lite.pos`; Gradle 8.11.1 wrapper |
| **web-admin** | Angular 18 (Karma/Jasmine) | `web-admin/` | Owner-facing dashboard |
| **ops** | Docker Compose (dev/staging/production) | `ops/` | Prod secrets via `.env` + `server/src/main/resources/application-prod.properties` |

Server features: `auth, billing, business, compliance, inventory, menu, notifications,
onboarding, payments, platform, reports, restaurants, staff, sync`.
Android features: `feature/{auth,billing,menu,settings,staff,sync,reports,...}` with
`core/` (Room DB, theme), `domain/` (SessionManager, PermissionManager, MasterSyncProcessor).

## 2. Domain model & invariants (NEVER violate)

- **Tenancy**: every row/data path is scoped to a restaurant. Android scopes prefs by
  `restaurantId + userId` (`SessionManager.scopedKey`); DAOs are wrapped in `TenantDaos`.
- **Terminals**: each device = `RestaurantTerminal` (terminalSeries, credentialVersion,
  deviceId, lastSeenAt, status). Sync push/pull requires `X-Terminal-Token`.
  `terminal.sync.strict=true` in prod (rollback: env `TERMINAL_SYNC_STRICT=false`,
  no rebuild). `KBOOK_ADMIN` is exempt.
- **Sync** (`server feature/sync/GenericSyncService` + Android `MasterSyncProcessor`):
  server is source of truth; server rejects cross-terminal bill edits
  (`CROSS_TERMINAL_UPDATE`) and validates child records (BillItem/BillPayment) ownership;
  Android mutable bill workflows MUST load via `getOperationalBillById` (enforces
  `isLocallyOwned`) — never raw `getBillById` for writes.
- **Roles & permissions**: `OWNER` vs `SHOP_STAFF`. Grants computed server-side
  (`PermissionService`) and synced; Android enforces via `PermissionManager` +
  `SessionManager`. Owner-only: printer config (`settings.printer`), payment/GST/shop
  profile settings, menu price/item edits, report export, staff management.
- **Device-local only (SQLite/Room, NEVER synced)**: KOT (kitchen tickets), printer
  configuration. Do not add server sync for these.
- **Stock/inventory**: the Android client has NO stock-write paths (removed deliberately —
  S6). Billing does not decrement stock on-device. Keep it that way unless the task says
  otherwise.
- **Web-admin surface**: login sends `context:"web"`; server rejects SHOP_STAFF logins on
  the web surface. Android staff POS login is legitimate — don't break it.

## 3. Conventions

- Server: feature-package layout (`feature/<name>/{controller,service,data,dto}`),
  constructor injection, `GlobalExceptionHandler` + `X-Error-Code` headers, records for DTOs.
- Android: feature modules with `ui/ data/ domain/`, Kotlin, MockK in tests
  (`every { }`), Room entities + DAO per feature, config keys in `SessionManager`.
- Changes must match existing style; smallest diff that solves the task; no speculative
  abstraction. Update `docs/meta/KNOWN_ISSUES.md` when behavior intentionally changes.

## 4. Verification (run before claiming done)

```bash
# Server (JUnit + Mockito + jqwik) — expect ~681 tests, 0 failures
cd server && ./mvnw test

# Android unit tests (SDK at /opt/android-sdk in this env)
cd Android && ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest --console=plain

# Web-admin (Chrome as root needs --no-sandbox wrapper: CHROME_BIN=/tmp/chrome-nosandbox)
cd web-admin && CHROME_BIN=<chrome --no-sandbox wrapper> npx ng test --watch=false \
  --browsers=ChromeHeadless --include='**/auth.service.spec.ts'
```

Preserve exit codes when piping (`PIPESTATUS[0]`); never skip/weaken tests to pass.

## 5. Current state (Oct 2026) — done & green, don't regress

- Menu variant reconciliation (id-aware edits, deleted-variant filtering, robust server
  category/item-id resolution) — RC1–RC3.
- Dead client stock-write API removed (S6).
- Bill ownership enforced server-side + Android (G1); `terminal.sync.strict=true` live.
- Printer config owner-gated on-device (G2); SHOP_STAFF blocked from web-admin login (G3).
- Stale-terminal sweeper auto-deactivates by `lastSeenAt`.
- All suites green: server 681 ✓, Android 387 ✓, web-admin auth spec 5 ✓.

## 6. Working rules

1. Extract acceptance criteria first (paths, interfaces, schemas, formats).
2. Plan with a todo list; keep it updated as you work.
3. Verify through the interface the user actually uses; measure, don't assume.
4. Report failures honestly; never claim success without a green check.
5. Never push/deploy/commit without explicit user request.


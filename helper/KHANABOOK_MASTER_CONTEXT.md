# KHANABOOK — Master Product Context (AI + Human)

> Product & architecture source of truth. Reconciled against the actual codebase on
> **2026-10-07**. Statuses: ✅ verified in code · 🟡 partial · ⚠️ open decision · 🔴 not built.
>
> Companion docs (read with this file, don't duplicate it):
> - `docs/meta/AGENTS.md` — build/test commands, stack, migration rules
> - `docs/meta/KNOWN_ISSUES.md` — open defects with evidence
> - `helper/KHANABOOK_MASTER_PROMPT.md` — session bootstrap prompt for AI agents
> - `/var/www/helper/*.txt` (outside repo) — `Aboutkhanabook.txt` (anchored architecture
>   reference), `roles.txt` (spec R1–R6 vs code), `DesignDecisions.txt` (D1–D4), `audit.txt`

## 1. Product Overview

**KhanaBook** is an offline-first GST billing POS for Indian restaurants (Tier 1/2/3),
plus a web-admin oversight dashboard and a multi-tenant Spring Boot backend.

- **Problem:** Indian restaurants need billing that keeps working when internet drops,
  across a few till devices, with GST invoices and simple payments.
- **Core positioning:** offline billing + multi-terminal support (max **5 terminals** per
  restaurant, owner-approved).
- **Design principle (from AGENTS.md):** *Android is terminal-scoped — each terminal sees
  ONLY its own bills. Web-admin is the restaurant-wide view.* The two front-ends are
  deliberately scoped to different jobs, not redundant.
- **Ecosystem:** `Kbook` repo = core POS (this repo). `Khanabook-Replit` repo = marketing /
  customer-acquisition platform (separate; keep contexts apart).

## 2. Core Modules

| Module | Where | Status |
|---|---|---|
| Auth & sessions (JWT, role whitelist) | server `feature/auth`, Android `feature/auth` | ✅ |
| Terminal management (approval, recovery, 5-limit) | server `feature/restaurants` | ✅ |
| Offline billing (bills, items, payments, GST) | Android `feature/billing` → server `feature/billing` | ✅ |
| Menu master data (+ variants, categories) | Android `feature/menu`, server `feature/menu` | ✅ |
| OCR menu import | Android `feature/menu`: `OcrScannerScreen.kt`, `OcrSpatialParser.kt` (ML Kit) | ✅ |
| Printing (dual printer; USB/BT/Wi-Fi) | Android `feature/printing`: `BluetoothPrinterManager`, `UsbPrinterTransport`, Wi-Fi | ✅ device-local |
| KOT (kitchen tickets) | Android, SQLite only | ✅ device-local |
| Payments — Easebuzz sub-merchant | server `feature/payments`, `easebuzz_sub_merchant` (kyc_status), webhooks | ✅ sandbox |
| Sync (push/pull, conflict arbitration) | Android `MasterSyncProcessor` ↔ server `GenericSyncService` | ✅ |
| Staff management | server `feature/staff` (credentials, grants) | ✅ |
| Reports (till-day on Android; restaurant-wide + terminal-level on web) | both | ✅ |
| Inventory/stock ledger | server-side only; **Android client has NO stock-write paths** (removed 2026-09) | ✅ scoped |

## 3. User Roles

Exactly three (verified: `UserRole.java`, V94 migration collapsed legacy roles, Android
whitelist in `UserRepository.kt`, web-admin routes only these three):

| Role | Android POS | Web-admin |
|---|---|---|
| **OWNER (SO)** | ✅ full: master data, config, billing | ✅ full operational control |
| **SHOP_STAFF (SS)** | ✅ billing + read master data | 🔴 **blocked at login** (server rejects `context="web"` SS logins — shipped 2026-10-07, fixes roles.txt R3 PARTIAL) |
| **KBOOK_ADMIN (KA)** | ❌ no POS access | ✅ `/admin/*` platform routes only |

## 4. Role Permissions (what each role can/cannot do)

Enforcement: server `PermissionService` computes grant sets → synced to device →
Android `PermissionManager` + `SessionManager` enforce on-device. Owner needs no grants.

**SHOP_STAFF — allowed (operational set, auto-granted by role):**
- Billing: create, edit, discount, settle, void, refund (`BILLING_*`) ✅
- Bills: **only bills of their own terminal** — server rejects cross-terminal edits
  (`CROSS_TERMINAL_UPDATE` + child-record checks) and all Android mutable bill workflows
  load via `getOperationalBillById` (`isLocallyOwned` guard). Fixes roles.txt R5 GAP. ✅ (2026-10-07)
- Menu: view only. Reports: read (till-day), **no export**.

**SHOP_STAFF — denied (owner-only):**
- Master data edits (menu add/price/edit full), tax (GST), payment config, shop profile
- Staff management, report export
- **Printer config** (`settings.printer`): ⚠️ **OPEN DECISION** — roles.txt R5 says SS
  *may write* printer/app settings; shipped hardening (G2, 2026-10-07) made it
  **OWNER-only** on device + in synced grant sets. Pick one and align spec + code
  (see DesignDecisions.txt). Owner-only today.

**KBOOK_ADMIN:** platform admin only (`/admin/*`, server-side); exempt from terminal-token
sync checks; never touches restaurant master data.

## 5. Platforms

| Platform | Stack | Role in product |
|---|---|---|
| Android POS | Kotlin 2.0.21, AGP 8.9.1, Compose, Room + SQLCipher, Hilt, Retrofit, WorkManager (minSdk 26, target 36) | Till-floor billing, offline-first, terminal-scoped |
| Web-admin | Angular 18.2 (standalone components; `core/ layout/ pages/ shared/`) | Restaurant-wide ops: bills, payments, reports, inventory, management |
| Server API | Java 17, Spring Boot 3.5.x, Maven, PostgreSQL, Flyway | Tenant isolation, sync arbitration, payments, audit |
| Marketing | `Khanabook-Replit` repo (separate) | Customer acquisition — do not mix with POS context |

> Package: `com.khanabook.lite.pos` (`AGENTS.md:284` — an older note claiming a wrong
> `com/khanabook/pos/` package in AGENTS.md is itself stale; no such line exists today).

## 6. Order / Bill Lifecycle

- **Payment-flow modes (restaurant-profile setting, `order_payment_flow_mode`):**
  `PAY_BEFORE_FOOD` ("Pay Before Food") and `PAY_AFTER_FOOD` ("Pay After Food") —
  verified in `OrderPaymentFlowMode.kt`. ✅
- **Quick Bill / Normal Bill:** 🟡 product terms from the owner spec (roles.txt R5); no
  `quickBill` identifier found in `feature/billing` — confirm the code mapping (UI flow vs
  order type) before an agent "adds" anything.
- Bill statuses flow Created → (Settled/Void/Refund paths) with terminal ownership on every
  mutation; bills are terminal-scoped on Android, restaurant-wide only on web-admin.
- Payment records: `bill_payments` syncs only when exactly one active record exists
  (0 or ≥2 diverge — see KNOWN_ISSUES). 🟡 known limitation

## 7. Billing & Payment

- GST config is owner-only (`SETTINGS_GST`); part-payment fields `partAmount1/2` exist
  (⚠️ known quirk: unconditionally reset to "0.0", see KNOWN_ISSUES).
- **Easebuzz sub-merchant model:** Restaurant → KhanaBook → Easebuzz → payment → webhook →
  bill status. Verified: `easebuzz_sub_merchant` (restaurant_id, sub_merchant_id, status,
  kyc_status). Currently **sandbox/testing**. Webhook suite green (3 pre-existing test
  failures fixed 2026-10-07).
- ⚠️ DesignDecisions **D4**: lock the Easebuzz subtree to OWNER — recommended, not yet
  shipped. Confirm before agents touch payments.
- Invoice numbering: per-restaurant unique series (`ux_bills_restaurant_invoice_series_active`).

## 8. Offline-First Behaviour

- Android Room (SQLite, SQLCipher-encrypted) is the local source of truth; every write
  stamps a `changed_fields` mask + `is_synced=0` → `MasterSyncProcessor.pushAll` → server
  `GenericSyncService` merges using `changed_fields` as authority → pull rebuilds locally.
- **Server is source of truth for merge arbitration; device owns its local rows between syncs.**
- Sync auth: every push/pull needs `X-Terminal-Token`; `terminal.sync.strict=true` in prod
  (rollback without rebuild: `TERMINAL_SYNC_STRICT=false`). KBOOK_ADMIN exempt.
- **Device-local only (NEVER sync to server):** KOT and printer configuration — stored only
  in on-device SQLite (owner's explicit rule).
- **Never reintroduce client stock writes:** billing does not decrement stock on-device;
  the whole Android stock-mutation API was removed (S6, 2026-10-07).

## 9. Technical Architecture (verified)

- Server: feature packages `auth billing business compliance inventory menu notifications
  onboarding payments platform reports restaurants staff sync` + `core/{config,security,exception,util}`.
  Context path `/api/v1`. Multi-tenancy via `TenantContext` (tenantId/userId/role from JWT).
- Android: `core/` (Room DB `AppDatabase`, 114 local Room migrations; server separately
  uses Flyway), `domain/` (SessionManager, PermissionManager, MasterSyncProcessor,
  SyncNormalizer), `feature/*` (ui/data/domain per feature).
- Web-admin: standalone Angular components, route guards by the 3 roles (`app.routes.ts`).
- Terminal model: `RestaurantTerminal` (terminalSeries, credentialVersion, deviceId,
  lastSeenAt, status) + `DeviceRegistrationRequest` with challenge codes (number matching
  on approval). Stale terminals auto-deactivated by scheduled sweeper on `lastSeenAt`.

## 10. Data Model (core entities)

`Restaurant` · `User` (3 roles) · `RestaurantProfile` (incl. `order_payment_flow_mode`) ·
`RestaurantTerminal` · `DeviceRegistrationRequest` · `MenuCategory` · `MenuItem` ·
`ItemVariant` (variants edited id-aware via `VariantDraftReconciler`; deleted variants
filtered before push) · `Bill` → `BillItem`, `BillPayment` · `EasebuzzSubMerchant` ·
permission-grant records · webhook/event tables.

## 11. Business Rules (hard invariants)

1. **5-terminal limit per restaurant** — strictly enforced on approval
   (`TERMINAL_LIMIT_REACHED` at `TerminalManagementService`), atomically via
   `RestaurantProfileRepository` row lock.
2. **First-device → owner approval:** new device activation creates a
   `DeviceRegistrationRequest` (HTTP 202 PENDING_APPROVAL); first-ever terminal of a
   restaurant auto-activates as OWNER; recovery uses challenge codes (number matching).
3. **Terminal ownership on bills:** a terminal edits only its own bills (server
   `CROSS_TERMINAL_UPDATE` + child checks; Android `getOperationalBillById` guard).
4. **Master data edits are role-gated:** config/menu/tax/payment = OWNER only; one
   authorized device class edits master data (owner's architecture decision).
5. **Device-local data:** KOT + printer config never leave the device.
6. **No client inventory writes** (server-side only).
7. **Android = terminal scope, Web-admin = restaurant scope** (by design, not redundancy).

## 12. API Conventions

- Base `/api/v1` (`application.properties`); JWT auth; terminal routes additionally need
  `X-Terminal-Token`; errors via `GlobalExceptionHandler` with `X-Error-Code` headers
  (e.g. `RECOVERY_CHALLENGE_LOCKED`, `TERMINAL_LIMIT_REACHED`); DTOs are Java records;
  validation errors return `{error, fields, path}`.

## 13. Security Model

JWT (role from token, identity overwritten server-side — clients can't spoof tenant/user) ·
RBAC via synced grant sets · terminal credentials with `credentialVersion` rotation ·
challenge-based device approval/recovery · strict terminal-token sync · SS blocked from
web-admin login (2026-10-07) · secrets only via env (`ops/` compose + `application-prod.properties`,
never committed). Secrets live in `secrets/` + `.env` — **never paste into prompts/PRs**.

## 14. Deployment

- VPS `/var/www/kbook.iadv.cloud` (live API `https://kbook.iadv.cloud/api/v1`), Docker
  Compose (`ops/docker-compose.{development,staging,production}.yml`), prod profile props in
  `server/src/main/resources/application-prod.properties`. Rollback switch:
  `TERMINAL_SYNC_STRICT=false`. Server rebuild + web-admin deploy needed for client-facing
  changes; Android ships via release APK/bundle.

## 15. Current State (2026-10-07) — fixed today, all suites green

Server **681 ✓** · Android **387 ✓** · web-admin auth spec **5 ✓**.
Fixed: G1 bill ownership (server+Android) · G2 printer-config owner gate · G3 SS web-admin
login block · S6 client stock-write removal · RC1–RC3 menu variant reconciliation ·
stale-terminal sweeper · 3 Easebuzz webhook tests · `terminal.sync.strict=true` in prod.

**Known-open (do not assume fixed):** multi-restaurant device activation gap
(`KNOWN_ISSUES.md`, server-side, 202 stuck) · `bill_payments` single-record sync ·
`partAmount1/2` reset quirk · D4 Easebuzz owner-lock · Quick/Normal-bill code mapping ·
⚠️ printer-config role decision (§4).

## 16. Development Rules

- Follow existing conventions; smallest diff; no speculative abstraction.
- Verification before claiming done: server `./mvnw test`, Android
  `./gradlew testDebugUnitTest` (`ANDROID_HOME=/opt/android-sdk` in this env), web-admin
  `ng test --watch=false --browsers=ChromeHeadless` (root Chrome needs `--no-sandbox` wrapper).
  Preserve exit codes through pipes (`PIPESTATUS[0]`); never skip/weaken tests.
- Migrations: server Flyway (`V<n>__*.sql`); Android Room migrations mirrored in
  `AppDatabase` + consistency-tested.

## 17. AI Coding Rules

1. Understand before editing: read AGENTS.md + the relevant module first; never modify
   unrelated modules; don't duplicate existing functionality; check dependencies before
   adding new ones; don't break existing APIs.
2. Respect §8 device-local boundaries and §11 invariants — an AI that syncs KOT/printer
   config or re-adds client stock writes is a regression.
3. Keep §11 ownership guards intact (`getOperationalBillById`, strict sync, child checks).
4. Layered context: load this file (product) + AGENTS.md (codebase) + the one module you
   touch + KNOWN_ISSUES.md (what's already broken) — not everything at once.
5. When product spec and code conflict (like §4 printer-config), surface the conflict —
   never silently pick a side.


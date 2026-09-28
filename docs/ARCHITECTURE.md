# KhanaBook POS — Architecture & Safe-Change Playbook

**Status:** Living document · **Last reviewed:** 2026-09-28 (Round 2) · **DB version:** 77
**Companion docs:** `docs/VERTICAL_SLICES_BLUEPRINT.md` (package restructure plan), `CONTEXT.md`

This file answers two questions:
1. *What exists, and what can a change in module X break in module Y?* (blast-radius map)
2. *How do I add a feature or fix a bug without breaking a working one?* (playbook + review findings)

---

## 1. System Overview (3 platforms, 1 backend)

| Platform | Stack | Location |
|---|---|---|
| **POS app** (cashier terminal) | Kotlin, Compose, Room + SQLCipher (offline-first), Hilt, WorkManager | `Android/` |
| **Server** | Spring Boot (Java 17, Maven) — REST API, OTP, payment webhooks, master data | `server/` |
| **Web admin** | Angular — restaurant onboarding, menu editing, reports, terminal approval | `web-admin/` |

The Android app is **offline-first**: every write goes to the encrypted local Room DB first, then syncs. Two sync directions (`feature/sync`):

- **Push:** local unsynced rows (`isSynced = false`) → server. Triggered by `triggerImmediateSync()` after each business save (debounced 5s, see `SyncManager.triggerImmediateSync`) + periodic `MasterSyncWorker`.
- **Pull:** server master data (menu, categories, users, profile, printers) → local DB inside `db.withTransaction` (`MasterSyncProcessor`).

> **Why this matters for your "one fix breaks another" problem:** the pull direction is the *hidden coupling* between otherwise independent features. When the server re-keys or deletes a menu item, every module holding a local foreign key to `menu_items` can break — exactly the FK-violation bug class fixed in `BillingViewModel.appendItemsToDraft` (see §5, Finding F1).

---

## 2. Android Module Map

```
com.khanabook.lite.pos
├── core/                      # shared infra — the "stable" layer everything may depend on
│   ├── database/  AppDatabase (v77, SQLCipher, all @Entity tables), DatabaseProvider, TenantDaos
│   ├── network/   KhanaBookApi (Retrofit, 77 endpoints), AuthInterceptor, pinning to kbook.iadv.cloud
│   ├── navigation/ AppNavGraph (all routes), NavigationItems, gestures
│   ├── designsystem/ + theme/ + components/   Khana* components, spacing, radii, colors
│   ├── di/        Hilt modules
│   └── util/      UserMessageSanitizer, BackendErrorParser, GlobalCrashHandler, AppConstants...
├── domain/model/Enums.kt      # shared enums (OrderStatus, PaymentStatus, ...)
├── ui/screens/MainScreen.kt   # bottom-nav shell
└── feature/                   # vertical slices; each has data/ domain/ ui/ viewmodel/
    ├── auth/         login, signup, session (SessionManager is a GLOBAL dependency), app lock, roles
    ├── billing/      the money path: new bill, active orders, drafts, KOT events, cart (36 files)
    ├── menu/         categories, items, variants, OCR menu scan
    ├── inventory/    stock logs, consumption (StockLogEntity)
    ├── payments/     Easebuzz SDK, UPI QR, payment recovery, PaymentStateManager
    ├── printing/     Bluetooth/network printers, PrintRouter, KOT/KDS, invoice PDF (29 files)
    ├── reports/      dashboard, exports (reads bills heavily)
    ├── settings/     shop/tax/payment/printer config, quick start
    ├── staff/        staff accounts, PermissionManager, permission cache
    ├── sync/         SyncManager, MasterSyncProcessor, MasterSyncWorker, quarantine table
    ├── notifications/ in-app notifications, FCM, FSSAI reminders
    └── onboarding/   Easebuzz KYC, compliance docs
```

### File hotspots (size = risk)
| File | LOC | Why it's risky |
|---|---|---|
| `feature/sync/domain/MasterSyncProcessor.kt` | 1748 | Re-writes the whole local DB from server pulls; touches every entity |
| `feature/billing/viewmodel/BillingViewModel.kt` | 1748 | All money-path writes; injected into by sync (see §4) |
| `feature/billing/data/BillDao.kt` | 1367 | Every query against `bills`/`bill_items` |
| `core/database/AppDatabase.kt` | 1298 | Schema v77 + ~25 hand-written migrations |
| `feature/settings/viewmodel/SettingsViewModel.kt` | 1267 | Writes config consumed by billing/printing/payments |
| `feature/menu/viewmodel/MenuViewModel.kt` | 1228 | Menu writes → FK targets for billing |

### Cross-feature import counts (files that import another feature; repo-wide grep, 2026-09-28)
`auth→sync 5 · auth→billing 4 · billing→auth 10 · billing→payments 12 · billing→printing 7 · billing→menu 8 · billing→reports 4 · billing→settings 5 · billing→sync 4 · printing→billing 8 · printing→auth 10 · reports→billing 7 · reports→payments 4 · settings→auth 9 · settings→payments 4 · settings→menu 3 · staff→auth 3 · sync→auth 6 · sync→billing 4 · sync→menu 4 · sync→inventory 3 · sync→notifications 3 · sync→printing 3 …`

**Reading this table:** `billing` and `settings` are the two biggest "fan-out" modules. A change to anything billing *exports* (entities, `PrintDispatchMode`, KOT event contract, `BillCalculator`) or settings *writes* (GST %, payment config, printer profile) radiates the widest.

---

## 3. Blast Radius Map — what can break what

Legend: `A → B` = code in A **imports** B, so changing B's exports can break A.

### 3.1 Static (compile-time) edges
- **`auth.SessionManager` is the universal dependency** — imported by all 12 features. Changing its API is a fleet-wide change.
- `billing → payments (12 files)`, `printing → billing (8)`, `reports → billing (7)`: the **billing data model** (`BillEntity`, `BillItemEntity`, KOT events) is the most-reshared contract in the app.
- `core/navigation/AppNavGraph` imports **every** feature's screens: adding a screen touches core, but that's the only required core edit.

### 3.2 Runtime (compile-silent) edges — where the real "fixed X, broke Y" bugs live
| Change here | Silently affects | Mechanism |
|---|---|---|
| `menu` rows (edit/delete/re-key via web admin or sync pull) | `billing` cart & drafts | `bill_items.menu_item_id` FK to `menu_items`; drafts from other terminals carry *foreign* local ids |
| `master pull` re-writes `menu_items`, `users`, `restaurant_profile` | `billing`, `printing`, `reports`, `inventory` | `MasterSyncProcessor` replaces rows inside a transaction; local ids can shift |
| `settings` (GST %, tax mode, printer paper size, order_payment_flow_mode) | `billing` totals, `printing` layout, `payments` flow | Config is read live from `restaurant_profile` at save/print time |
| `billing` KOT event contract (`KotEventEntity`, snapshot JSON, `eventToken`) | `printing` (PrintRouter, KitchenTicketFormatter, KDS), `sync` | Event-sourced tickets are parsed from immutable snapshots |
| Printer profile / role mapping | `billing` auto-print, `printing` KDS | `PrintRouter` decides KITCHEN vs CUSTOMER by role + ownership guards |
| Schema migration (v77→78) | everything | Hand-written `Migration` objects in `AppDatabase`; a wrong migration corrupts all features |
| `SessionManager` keys / terminal identity | `auth`, `sync`, `printing` ownership guard, everything tenant-scoped | Tenant/terminal scoping is applied inside every DAO call |

### 3.3 The KOT pipeline (most intricate chain — touch with care)
```
Cart save (BillingViewModel)
  → BillRepository.insertBillItems / updateBillItem / deleteBillItemById   (records KotEvent ADD/VOID with batchToken)
  → kot_event rows (immutable itemSnapshotJson)                            (NOT yet printed)
  → PrintService.startPrintJob(AUTO)
  → PrintRouter: ownership guard → batch resolution → KitchenTicketFormatter.formatCombinedTicket
  → markPrinted(ev) + markItemsSentToKot(...) only after transport success
  → failure → kitchenPrintQueueManager retry (30s flush) — never data loss
```
Invariants worth knowing before touching any part of it:
1. `sentToKot` is a **print-receipt flag, not a business flag** — items are recorded as events first; printing is delivery.
2. AUTO mode renders **event snapshots**; manual reprint renders the **live order**. Never conflate them.
3. KOT is only printed for bills whose `recordScope == "terminal_operational" && recordOrigin == "local_created"` and terminal matches (`PrintRouter` ownership guard) — pulled bills are read-only history.
4. One user save = one `batchToken` → one combined ticket (see `BillingViewModel.appendItemsToDraft`).

---

## 4. Dependency Rules (the constitution)

Adopted from the current codebase (mostly already true, some violations noted in §5):

1. **Allowed:** `feature/* → core/*`, `feature/* → domain/model`, `ui → core`. Navigation always routes through `core/navigation`.
2. **Forbidden:** `feature → feature` **except** through the narrow, whitelisted edges below (these exist today and are intentional):
   - any → `auth.domain.SessionManager` (session/tenant/terminal context)
   - any → `staff.domain.PermissionManager` (permission gates)
   - `sync → {billing,menu,inventory,printing,notifications,payments}.data` (the sync engine is the one module allowed to write everyone's tables)
   - `core.database` → feature `.data` entities (the single schema owner, reversed direction, accepted)
   - `MasterSyncProcessor` currently imports `billing.viewmodel.BillingViewModel` (line 16) — **to be removed** (§5, F6); never add new ones like it.
3. **New feature checklist:** new feature = new `feature/<name>/` slice with `data/domain/ui/viewmodel`. Register route in `AppNavGraph`. If it needs a table: entity goes in the feature's `data/`, gets registered in `AppDatabase` (bump version + write migration), and *only then* decide whether sync must carry it (if yes: `MasterSyncProcessor` + server DTO + `SyncEntityMappers`).
4. **No feature reads another feature's ViewModel or UI.** If two features need the same logic, it moves down into `core/util` or `domain/` — never up.

---

## 5. Code Review Findings (evidence-backed, read-only — nothing was edited)

Verification method: `git grep` / file reads against working tree at commit `main`; compile check was interrupted, so all findings below are from static reading with exact locations. Severity: 🔴 fix before release · 🟡 should fix · 🔵 improvement.

### F1 🔴 Stale menu references: handled in `appendItemsToDraft`, **not** in `saveDraftOrder`
- **Where:** `feature/billing/viewmodel/BillingViewModel.kt`
  - `appendItemsToDraft` (~lines 1243–1262): resolves every cart item — id lookup → name-lookup heal (`getItemByName`, `MenuDao.kt:31` filters `is_deleted = 0`) → dangling-variant fallback to base item → user-friendly error only when the item is truly gone. Comment block documents the sync re-keying scenario. ✅ Good fix.
  - `saveDraftOrder` (~lines 1093–1165): builds `BillItemEntity` rows **directly from `cartManager.currentItems`** (`menuItemId = cartItem.item.id`, `variantId = cartItem.variant?.id`) with **no equivalent resolution**.
- **Evidence chain:** `bill_items` has FKs to `menu_items` and `item_variants` with `onDelete = SET_NULL` (`BillItemEntity.kt:9–28`); `MenuItemEntity` inserts use `OnConflictStrategy.ABORT` and sync pull deletes/re-keys rows (`MasterSyncProcessor`); `CartManager` only re-reads prices (`CartManager.kt:43`), it never re-keys ids. An earlier `git diff` snapshot of this file contained exactly this stale-reference guard for `appendItemsToDraft` — the same window between cart-fill and save exists in `saveDraftOrder` (sync can fire between them via `triggerImmediateSync` debounce).
- **Fix direction (when you're ready):** extract the resolve/heal loop from `appendItemsToDraft` into a small `CartReferenceResolver` and call it in both paths before any `insertFullBill`/`insertBillItems`.

### F2 🔴 `appendItemsToDraft` performs ~10 separate DB writes with no transaction
- **Where:** `BillingViewModel.kt` ~1243–1414 (the resolvedCartItems loop → `insertBillItems` / `updateBillItem` / `deleteBillItemById` per row, then `updateBill`).
- **Evidence:** each `BillRepository` method (`BillRepository.kt:597–639`) is an independent DB operation; the only `@Transaction` annotations in the billing data layer are in `BillDao` for single queries, and `MasterSyncProcessor` uses `db.withTransaction` — the ViewModel path does not.
- **Impact:** process death, OOM kill, or crash mid-loop (this is a POS app running on cheap Android tablets, often RAM-starved) leaves the bill **partially updated** — wrong totals, wrong KOT events — and `_isLoading` can be left `true` (see F3). The bill would then be printed/paid from corrupt state.
- **Fix direction:** wrap the whole mutation in `databaseProvider.withTransaction { ... }` (the pattern `MasterSyncProcessor` already uses). KOT event recording stays inside so the batch token remains atomic.

### F3 🟡 `_isLoading` not reset on early-return paths (spinner forever)
- **Where:** `BillingViewModel.saveBill` path — `saveBill` sets `_isLoading.value = true` then early-returns `false` for permission-blocked (~line 820), empty cart (~line 826), and unavailable items (~line 836) **without resetting `_isLoading`**.
- **Evidence:** those `return@withLock false` blocks sit *before* `_isLoading.value = true`, but at least one caller toggles loading before invoking; also `loadDraftOrder`'s catch sets `_error` then `finally { _isLoading.value = false }` (line ~1046) — the correct pattern, proving the codebase's own convention is not applied uniformly. Any early return added in future between `_isLoading = true` and the `try` risks a stuck spinner.
- **Fix direction:** adopt the `try/finally` reset everywhere; or a `guarded { ... }` helper that always resets.

### F4 🟡 Generic exception swallowing loses money-path diagnostics
- **Where:** 16 `catch (e: Exception)` blocks in `BillingViewModel.kt` alone (grep); 100+ across `feature/` (top: `MenuViewModel` 17, `SettingsViewModel` 13, `BluetoothPrinterManager` 10). Some re-map to friendly text (`UserMessageSanitizer.sanitize` — good, used at lines ~735, ~982), others hardcode strings (lines ~1189, ~1419) — inconsistent.
- **New code introduced by the uncommitted change** added the correct `CancellationException` re-throw at 4 sites (803, 1189, 1419, 1525, 1607, 1633) — **but 10+ other catch-alls in the same file still swallow it**, so coroutines cancelled via those paths (e.g. user leaving mid-save) log a bogus "failed to save" error and keep the UI alive when it should die quietly.
- **Fix direction:** one shared `runCatchingBusiness(...)`/`safeCall` util in `core/util` that: re-throws `CancellationException`, sanitizes user message via `UserMessageSanitizer`, and logs to Crashlytics. Replace catch-alls feature by feature.

### F5 🔵 Dead UI code removed in uncommitted change — good, but confirm no hidden users
- **Where:** uncommitted diff of `ActiveOrderDetailScreen.kt` deletes usage of `ActiveOrderSummaryCard`, `ActiveOrderActionGrid`, `ItemSection`, `activeOrderTitle` (defined in `feature/billing/ui/DetailComponents.kt:298,331`). Repo grep (`git grep` across `feature/`) finds **no remaining callers** outside `DetailComponents.kt`.
- **Impact:** none at runtime; the components are now dead weight (~100+ LOC) that future contributors may wrongly treat as canonical.
- **Fix direction (separate commit):** delete the dead composables from `DetailComponents.kt`, or keep if a planned screen still needs them.

### F6 🔵 Sync → ViewModel reverse dependency
- **Where:** `MasterSyncProcessor.kt:16` imports `com.khanabook.lite.pos.feature.billing.viewmodel.BillingViewModel` (used only in a comment reference near line 1724 per grep; verify at edit time).
- **Impact:** compiles today, but it cements the biggest module into the sync engine; any ViewModel refactor ripples into sync tests. Exactly the kind of edge that produces your "fixed X broke Y" syndrome.
- **Fix direction:** drop the import (it looks comment-only) and add a lint/grep guard in CI (see §6).

### F7 🔵 Duplicate test stacks + two untested core engines
- **Where:** `app/build.gradle.kts` testImplementation block has **mockk + mockk-agent AND mockito-core + mockito-kotlin**; 77 unit test files exist but `find` for `*billing*`/`*sync*` unit tests returns nothing — the money path (`BillingViewModel`, `BillRepository`, `MasterSyncProcessor`) has no unit coverage; 25 instrumented tests exist (Hilt runner configured).
- **Impact:** every change to billing/sync is verified only by hand — which is precisely why regressions surface in production features.
- **Fix direction:** pick **one** mocking stack (team already leans MockK), and add the first tests to `BillRepository.insertBillItems` KOT-event behavior + `CartReferenceResolver` (once extracted, F1).

### F8 🔵 Inconsistent error copy in the new screens
- **Where:** the uncommitted `BillingViewModel` change replaced raw `e.message` with fixed strings ("Could not save the order. Please try again." line ~1191, "Could not update the order." line ~1421), while sibling paths use `UserMessageSanitizer.sanitize(...)` (lines 735, 982) which maps backend errors (e.g. invoice-series conflicts) to precise messages.
- **Impact:** cashiers lose the specific reason on the two most-used save paths; sanitizer users keep it.
- **Fix direction:** route both new catches through `UserMessageSanitizer.sanitize(e, fallback)` for consistency.

### Positive notes (keep doing this)
- ✅ KOT event-sourcing with batch tokens (§3.3) is genuinely robust design.
- ✅ `PrintRouter` terminal-ownership guard prevents cross-terminal duplicate KOTs.
- ✅ Terminal daily counters + `operationId` idempotency protect against duplicate orders.
- ✅ Release build hard-fails on missing signing/version/HTTPS host (build.gradle.kts taskGraph hook).
- ✅ Name-heal fallback in `appendItemsToDraft` (F1) is the right idea — it just needs to be shared.

---

## 5b. Round 2 Review (2026-09-28) — deeper pass: security, DB, sync, payments, money math

Method: second full read of `DatabaseProvider`, `DatabaseModule`, `NetworkModule`, `SyncManager`, `BillCalculator`, `EasebuzzPaymentRepository`, `MasterSyncWorker`, plus re-verification of every Round-1 finding against the current tree (a new uncommitted change to `PrintService.kt` had landed since Round 1 — reviewed below).

### Revision of Round-1 findings
- **F3 DOWNGRADED (my Round-1 overclaim):** re-read of `BillingViewModel.completeOrder` (lines ~816–841) shows the permission/empty-cart/unavailable-items early-returns all happen **before** `_isLoading.value = true` — loading is only set inside the guarded `try`. No stuck-spinner bug exists on those paths today. The actionable residue is only *convention risk*: nothing enforces the set-loading-then-try/finally pattern, so F3 becomes "add a lint/test convention", not a fix. Recorded here so the doc stays honest.
- **F1, F2, F4, F5, F6, F7, F8: re-verified, still hold** at the same locations on the current tree.

### New findings
#### R1 🔵 Log drift: `expectedRoomVersion=70` hardcoded while schema is v77
- **Where:** `core/database/DatabaseProvider.kt` — the literal `expectedRoomVersion=70` appears in the switch-success log (~line 100), switch-failure log (~line 117), and warm-up logs (~line 155), while `AppDatabase` is at `version = 77` (`AppDatabase.kt:44`) and `DatabaseProvider.buildDatabaseWithName` wires migrations up to `MIGRATION_76_77`.
- **Impact:** no functional effect — but anyone debugging a field device from logs will misread the DB version and waste time. Classic documentation rot on the highest-stakes subsystem.
- **Fix direction:** replace the literal with `AppDatabase` version reference (or drop the field from the log line).

#### R2 🔵 Legacy-DB migration swallows failure silently
- **Where:** `DatabaseProvider.migrateLegacyDataIfNecessary` — the whole one-time legacy→per-restaurant data copy (users, profile, categories, menu, bills, payments, printers) runs in one `try/catch(Exception)` that only does `Log.e` and continues; `switchToDatabase` then proceeds to open the (possibly empty) new DB.
- **Evidence chain:** `newDbFile.exists()` short-circuits the retry — if the copy half-failed before the file was created it may retry, but if it half-failed *after* the file exists, the next launch skips migration forever with partially-copied data (e.g. bills migrated but menu missing).
- **Impact:** rare, but when it hits, a restaurant sees "all my data is gone" with zero surfaced error — support nightmare.
- **Fix direction:** on failure, delete the partial target file so the next launch retries, and surface a non-fatal notice (Crashlytics + sync-center banner).

#### R3 🔵 `runBlocking` inside `runInTransaction` (legacy migration only)
- **Where:** same `migrateLegacyDataIfNecessary` — `newDb.runInTransaction { runBlocking { ... } }` performs nested suspend DAO work.
- **Impact:** contained to a one-time cold-start path; deadlock requires a suspended transaction waiting on itself, which the current code avoids (all inner calls are plain DAO calls). Acceptable today, but this pattern is a landmine if anyone copies it into a hot path. Flagged as convention risk only.

### Round 2 positives (verified, not assumed)
- ✅ **Per-restaurant encrypted DBs** (`khanabook_lite_db_<id>`) via `DatabaseProvider.switchToDatabase`, with `TenantDaos` decorators re-resolving the active DB on every DAO call — clean multi-tenant scoping.
- ✅ **SQLCipher key management**: 32-byte `SecureRandom` key stored in `KeystoreBackedPreferences`, with an explicit legacy-key migration path (`DatabaseModule.getOrCreateDbPassphrase`, lines 69–94).
- ✅ **`.env` hygiene**: git-ignored, untracked; OTP/Meta tokens moved server-side (documented in build.gradle.kts comments); release build *refuses* placeholder credentials.
- ✅ **Network hardening**: OkHttp BODY-level logging only when `BuildConfig.DEBUG && !isProductionBackend(...)` (`NetworkModule.kt:35–43`); auth token in Keystore-backed prefs (`SessionManager:33`); WorkManager periodic sync with `NetworkType.CONNECTED` constraint + backoff (`MasterSyncWorker:68–89`).
- ✅ **Sync correctness**: full cycle wrapped in `NonCancellable` with a **checkpoint-before-write** timestamp pattern — push-then-pull failure re-pulls the missed window, no record loss (`SyncManager.runSyncCycle`, lines ~248–260); debounce + re-queue prevents sync storms.
- ✅ **Payment integrity**: `EasebuzzPaymentRepository.verifyPayment` does server-side verification (never trusting client result alone), `cancelStalePendingOnlineDrafts` cleans superseded attempts, and the startup self-heal only *logs* pending drafts instead of auto-cancelling (correct — dine-in drafts are DRAFT+PENDING too).
- ✅ **Money math**: `BillCalculator` is BigDecimal end-to-end, HALF_UP consistently, inclusive-GST and odd-paise splits handled correctly (`splitPartPayment` uses DOWN for the first half + remainder to the second).
- ✅ **No `GlobalScope`** anywhere (only a comment naming it as an anti-pattern); ViewModels use injected scopes; `BillingViewModel` has a `CoroutineExceptionHandler` fallback.
- ✅ **New uncommitted `PrintService` fix reviewed**: `resolveForegroundServiceType()` falls back from `connectedDevice` to `dataSync` when `BLUETOOTH_CONNECT` isn't yet granted — correct Android 14+ fix for the fresh-install `SecurityException` crash; comment block documents why. Ship it.
- ✅ **Permission gates**: `completeOrder` checks `BILLING_CREATE` before any write and surfaces a `BlockedPermission` UI event instead of a generic error.

---

## 6. Safe-Change Playbook — how to add a feature / fix a bug without breaking others

### 6.1 The Blast-Radius Pre-Check (2 minutes, before any change)
1. **Identify the contract you're touching:** entity? DAO query? sync DTO? SessionManager? PrintRouter contract? settings key?
2. **Grep its fan-out:** `git grep -l "<TypeName>" -- Android/app/src/main` — count importer files. Anything ≥5 files = wide blast radius → plan behind an interface or additive change.
3. **Check the runtime table in §3.2** — is your change in a row there? If yes, write down which features could silently regress and *how you'd notice* (which screen to open).

### 6.2 Change classes and their gates
| Change type | Mandatory steps |
|---|---|
| **New feature (new slice)** | Create `feature/<name>/`; route in `AppNavGraph`; new entity → AppDatabase **version bump + migration**; decide sync participation *before* writing UI. Add 1 unit test for the domain layer. |
| **DB schema change** | Bump `AppDatabase` version; write `Migration` (idempotent style used in v49–53: guard with `hasColumn`); export schema; add a Room `MigrationTestHelper` instrumented test if data moves. |
| **Entity/DAO change** | Grep fan-out (step 2 above); prefer **adding nullable columns with defaults** over altering meaning; check `MasterSyncProcessor` + server DTO parity in the same commit. |
| **Fix in billing/payments** | Reproduce first; write the failing unit test (F7 says there is none — write it); fix; keep the test. |
| **Sync-affecting change** | Test offline: airplane-mode flow + `MasterSyncWorker` manual trigger + quarantine path. |
| **Print path change** | Test AUTO (event-sourced) AND manual reprint paths + foreign-terminal bill (ownership guard). |

### 6.3 Feature-by-feature rollout (your "add one by one" ask)
1. **Branch per feature** off `main`; keep each PR single-purpose.
2. **Behind a flag when risky:** add a `settings` toggle (or remote config key) for anything touching §3.2 runtime edges — ship dark, enable for one restaurant, then widen.
3. **One test per feature minimum** (domain layer, JVM-only) + one instrumented happy-path if UI is new.
4. **Verify the "features that were already working"** by walking §3.2 rows affected by your change: open New Bill → save → KOT print → pay → reports. That 5-minute manual pass catches 80% of cross-feature regressions.
5. **Commit hygiene:** never mix a refactor (moves) with behavior changes (see `VERTICAL_SLICES_BLUEPRINT.md` philosophy) — this repo's own blueprint mandates behavior-identical slices with a build gate after each.

### 6.4 Regression guardrails to add (cheap, high value)
1. **CI import-boundary grep** — fail the build when a new `feature → feature` import appears outside the §4 whitelist (one bash script; the scan in this doc is that script, basically).
2. **Compile check on every change** (`gradlew :app:compileDebugKotlin`) — was interrupted during this review; make it the pre-push hook (`.githooks/` exists).
3. **Single mocking stack** + first `BillRepository` KOT tests (F7).
4. **Shared error-handling util** (F4/F8) so future catches can't swallow `CancellationException` or leak raw `e.message` to cashiers.

### 6.5 Suggested order of operations (from findings)
1. F1 — extract shared cart-reference resolver, use in both save paths. *(Highest user-visible risk: order creation.)*
2. F2 — transaction-wrap `appendItemsToDraft` (and audit `saveBill` for the same pattern).
3. F4+F8 — shared safe-call util; convert the 6 new CancellationException sites into it; route through sanitizer.
4. F3 — `finally`-style loading reset sweep in `BillingViewModel`.
5. F6 — delete sync→viewmodel import; add CI import guard.
6. F7 — first unit tests on `BillRepository` KOT behavior.
7. F5 — delete dead composables (separate commit).

---

## 7. Glossary (terms used across the codebase)
- **Bill / Draft:** a `BillEntity` row; `orderStatus = DRAFT` until paid/completed; drafts are editable, history is not.
- **KOT:** Kitchen Order Ticket. Here: event-sourced (`KotEventEntity` ADD/VOID/REPRINT with immutable snapshots), printed via `PrintRouter`.
- **Terminal:** one installed POS device identity (`terminalId`, `terminalSeries`) with server activation + approval flow; scopes DB access and print ownership.
- **Master sync / pull:** server→client refresh of shared data inside `MasterSyncProcessor`.
- **Quarantine:** `sync_quarantine_records` — rows that failed to sync with reason + snapshot, surfaced in Sync Center UI.
- **operationId:** idempotency key (`restaurant:terminal:token:action`) preventing duplicate bill creation across retries.

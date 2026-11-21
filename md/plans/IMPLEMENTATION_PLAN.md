# Plan: Make the in-flight multi-device changeset fully working (Tier 1)

## Context / what I found
Branch `v1` has a **74-file uncommitted changeset** that already implements ~80% of PLAN.md,
but it is **half-finished and compiles on neither side**. Goal of this task (per your choices):
**Tier 1 — both projects compile, server boots on `ddl-auto=validate`, existing test suites pass.**
Migration written **dev-safe** (no production duplicate-reconciliation logic).

Out of scope (deferred): KOT NEW/ADD/VOID event semantics + server sync, cursor-pagination
rewrite, deployment automation beyond what exists. These stay as PLAN.md future work.

## Confirmed blockers to fix

### A. Missing classes — Android (won't compile)
1. **`KotEventEntity`** — referenced in `AppDatabase.kt:25`. Must match the `kot_events` table
   created by `MIGRATION_55_56` (AppDatabase.kt:603): columns `public_token`, `kot_revision`,
   `event_type`, `item_snapshot_json`, `originating_device_id`, `is_printed` (default 0),
   `created_at`; composite PK `(public_token, kot_revision)`; indices on `public_token` and
   `originating_device_id`. Create at `data/local/entity/KotEventEntity.kt`.
2. **`KotEventDao`** — referenced in `AppDatabase.kt:39` and implemented by `TenantKotEventDao`
   (`TenantDaos.kt:661`). Required methods (from consumer): `insert`, `insertAll`,
   `getEventsForBill(publicToken)`, `markPrinted(publicToken, kotRevision): Int`,
   `getEventCountForBill(publicToken): Int`, `getEvent(publicToken, kotRevision): KotEventEntity?`.
   Create at `data/local/dao/KotEventDao.kt` (follow existing DAO style, e.g. BillDao).

### B. Missing classes — Server (won't compile)
3. **`RestaurantTerminal`** entity — table `restaurant_terminal`, used to gate/allocate invoice
   series. Fields inferred from usage: `restaurantId`, `terminalSeries` (unique per restaurant),
   plus id/audit. Create at `entity/RestaurantTerminal.java`.
4. **`RestaurantTerminalRepository`** — imported in `BillServiceImpl.java:6,27` and
   `GenericSyncService.java:33`. Required method: `findAndLockByRestaurantIdAndTerminalSeries(tenantId, terminalSeries)`
   returning `Optional<RestaurantTerminal>` with a pessimistic lock (`@Lock(PESSIMISTIC_WRITE)`),
   matching the "reserve sequence" intent. Create at `repository/RestaurantTerminalRepository.java`.

### C. Missing Flyway migration — Server (won't boot; `ddl-auto=validate`)
5. **`V26__multidevice_invoice_identity.sql`** (dev-safe), doing:
   - `ALTER TABLE bills ADD COLUMN IF NOT EXISTS` for `terminal_series`, `invoice_series`,
     `invoice_number` (VARCHAR), `invoice_sequence` (BIGINT), `financial_year` (VARCHAR) — matching
     `Bill.java` column names/types exactly so `validate` passes.
   - `CREATE TABLE IF NOT EXISTS restaurant_terminal (...)` matching the new entity, with a unique
     constraint on `(restaurant_id, terminal_series)`.
   - Backfill `public_token` nulls (guard) and keep V19 unique index (already unique).
   - `DROP INDEX IF EXISTS ux_bills_restaurant_lifetime_order_active;`
     `DROP INDEX IF EXISTS ux_bills_restaurant_daily_order_active;` (PLAN §4.4 steps 8–9 / §10 gate).
   - Guarded uniqueness on `(restaurant_id, financial_year, invoice_series, invoice_sequence)` WHERE
     `is_deleted = false` — created only if no existing duplicates (dev-safe guard, same pattern as V22).
   - Idempotent throughout (`IF NOT EXISTS` / `IF EXISTS` / `DO $$` guards).
   - Verify column names/types against `Bill.java` before finalizing (validate is strict).

### D. Room exported schemas out of date (migration tests can't run)
6. Room `version = 57` but `schemas/.../` stops at `54.json`. Generate/commit `55.json`, `56.json`,
   `57.json` by building with the Room schema exporter (KSP `room.schemaLocation` is already on).
   These are produced by the Gradle build, not hand-written.

### E. Repo hygiene
7. Delete stray `nul` files at repo root, `Android/nul`, `server/nul` (Windows redirect artifacts).

## Definition of done (verification I will run)
- **Server:** `mvn -q -f server/pom.xml compile` succeeds; `mvn -q -f server/pom.xml test` passes.
  (Flyway migration applies cleanly against a fresh dev DB and Hibernate `validate` boots — verified
  via the existing Spring test context / SystemTest if it starts the context.)
- **Android:** `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest` pass
  (includes Room migration tests once 55–57 schemas exist). `docker` is unavailable here, so any
  container-based test is out; JVM unit tests are the gate.
- No `nul` files remain.

## Execution order
1. E (hygiene) — trivial, unblocks clean status.
2. A + B (missing classes) in parallel — pure additions, make each side compile.
3. C (migration) — align to `Bill.java`; drop V22 indexes; add terminal table.
4. Build server → fix any `validate`/compile mismatches.
5. Build Android → generate schemas 55–57 (D) → run unit + migration tests.
6. Report exactly what passed / what (if anything) could not be verified in this environment.

## Notes / risks
- I will **not** commit or push (no request to). I'll leave the tree building & green for your review.
- If, during the build, a 5th hidden reference to another missing symbol appears, I'll fix it in the
  same spirit (minimal, matching existing consumers) and note it — the two "missing class" clusters
  suggest an interrupted session, so a straggler is possible.
- The dev-safe migration is explicitly **not** certified against a production DB containing duplicate
  invoice numbers; that reconciliation remains your separate task per PLAN §4.4/§10.

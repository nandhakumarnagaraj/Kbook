# KhanaBook Order ID R&D — 5-Device Same-Restaurant Scale

**Date:** 2026-09-02  
**Scope:** `dailyOrderId` / `lifetimeOrderId` / `invoiceSequence` atomicity, collision, and recovery under 5 concurrent tablets sharing one `restaurant_id`.  
**Method:** Primary-source code + schema + primary docs only. Every claim cited as `file:line` or URL.

---

## 0. Terminology

| Field | Meaning | Owner |
|-------|---------|-------|
| `dailyOrderId` (int) + `dailyOrderDisplay` (`{terminalSeries}-01`) | Per-terminal, per-IST-date counter shown to staff/customers | Client `terminal_daily_counter` (PK `restaurant,terminal,date`), server `ux_bills_restaurant_terminal_daily_active` |
| `invoiceSequence` + `invoiceSeries` (`{FY}{terminalSeries}` e.g. `25A`) + `invoiceNumber` (`A000001`) | GST invoice number, per-series, per-financial-year monotonic sequence | Client `bills.invoice_sequence`, server `ux_bills_restaurant_invoice_series_active` |
| `lifetimeOrderId` (BIGINT, nullable since V26) | Legacy global counter | Deprecated — new bills leave `NULL` |
| `publicToken` (UUID) | Canonical bill identity for idempotency | `ux_bills_public_token` / `idx_bills_public_token_unique` |

---

## 1. Executive Summary

- **Per-terminal daily counter is correct by design for distinct series** — 5 devices each with a distinct `terminalSeries` legitimately allocate `dailyOrderId=1` at 00:01 IST the same day; server uniqueness is `(restaurant, lastResetDate, terminalSeries, dailyOrderId)` so no collision. Two devices sharing the same series *will* collide and must be repaired.
- **Same-device atomicity is proven; cross-device / pull-raised concurrency has a `MAX…raiseAtLeast` window.** The single-statement `insertOrIncrementTerminalDailyCounter` (`RestaurantDao.kt:153`) is atomic. The multi-statement wrapper `incrementAndGetTerminalDailyCounter` (`RestaurantDao.kt:168`) adds a `MAX(daily_order_id)` backfill → `raiseAtLeast` → `UPSERT increment` sequence inside one Room `@Transaction`. SQLite WAL serializes the final write, but the `MAX` read is non-locking and can interleave with a concurrent pull that raises the counter higher; the wrapper's `get…Value ?:1L` return after the UPSERT masks the race but the counter can still skip or briefly go backwards before `MAX` forces it forward (see §3).
- **Client vs server duplicate keys diverge:** client `insertFullBill` (`BillDao.kt:441`) guards on `is_deleted=0 AND daily_order_id AND terminalSeries AND created_at BETWEEN start/end(Asia/Kolkata)`. Server `findConflictingDailyOrder` (`BillRepository.java:141`) guards on `(restaurant,lastResetDate,terminalSeries,dailyOrderId)` excluding `(deviceId,localId)` *without* a `created_at` window and with a hard DB partial unique index `ux_bills_restaurant_terminal_daily_active` (`V26__…sql:100`). A bill can pass the local guard and still be rejected server-side after clock-skew or cross-device `created_at` bucket mismatch.
- **Invoice numbering has a format divergence and a local MAX race.** Client formats `A%02d` (`BillCreationUseCase.kt:280`), server formats `A%06d` (`BillServiceImpl.java:137`). Five devices allocating the same `invoiceSeries` offline all read local `MAX(invoice_sequence)=N` and all propose `N+1`; pushes serialize on the server's `SELECT … FOR UPDATE` on `restaurant_terminal` (`RestaurantTerminalRepository.java:30`) *only* when they go through `BillServiceImpl.allocateMissingInvoiceNumbers`; legacy generic-sync pushes rely solely on the DB unique index + `REQUIRES_NEW` per-record fallback.
- **`GenericSyncService` batch fallback is now `REQUIRES_NEW`-correct** — the aborted-transaction bug (infinite 500 loop) noted in `docs/reviews/*` is fixed via `SyncFallbackSaver.java:23`.
- **`lifetimeOrderId` is retired** — `V26` drops `NOT NULL` (`V26__…sql:23`), client `MIGRATION_54_55` recreates `bills_new` with nullable column (`AppDatabase.kt:498`), and `BillEntity.kt:51` is `Long?`. Legacy reports keyed on `lifetime_order_id` silently drop new rows; `BillEntity.kt:158` falls back to `invoiceNumber → terminalSeries+seq(pad2) → INVi → dailyDisplay → DRAFT-LOC-id`.
- **Midnight rollover is timezone-coherent but clock-skew sensitive.** All paths use `AppConstants.DEFAULT_TIMEZONE = "Asia/Kolkata"` (`AppConstants.kt:9`, `AppConstants.java:9`). Server rejects `|updatedAt - serverTime| > 5 min` (`GenericSyncService.java:399`). Within ±5 min, a device 1 min behind can still derive yesterday's `LocalDate` and allocate against the wrong daily bucket.

**Overall risk for 5-device same-restaurant:** P0 duplicate-invoice/daily handling is safe *only because* server DB constraints + quarantine loop exist. Removing either would cause duplicate printed invoice numbers (GST risk). Remaining P1 risks are counter-window races and format divergence.

---

## 2. Current Design (cited file:line)

### 2.1 Terminal daily counter

- **Table:** `terminal_daily_counter` PK `(restaurant_id, terminal_id, date)` (`AppDatabase.kt:748`, `TerminalDailyCounterEntity.kt:10`, `MIGRATION_58_59:743`). Index on `(restaurant,date)` (`AppDatabase.kt:759`).
- **Atomic UPSERT increment:** single statement `INSERT … ON CONFLICT(restaurant,terminal,date) DO UPDATE SET daily_order_counter = daily_order_counter+1` (`RestaurantDao.kt:153`). Comment explicitly calls out atomicity against a concurrent `raiseAtLeast` (`RestaurantDao.kt:149`).
- **Atomic raise:** single UPSERT `… DO UPDATE SET daily_order_counter = MAX(daily_order_counter,:counter)` (`RestaurantDao.kt:208`).
- **Composite allocate:** `incrementAndGetTerminalDailyCounter` (`RestaurantDao.kt:168`):
  ```kotlin
  maxExisting = getMaxDailyOrderIdAcrossAllBills(restaurantId,date,terminalSeries) // BillDao scan
  if (max>0) raiseTerminalDailyCounterAtLeast(...,max,now)
  insertOrIncrementTerminalDailyCounter(...)
  return getTerminalDailyCounterValue(...) ?:1L
  ```
  `getMaxDailyOrderIdAcrossAllBills` is `SELECT COALESCE(MAX(daily_order_id),0) FROM bills WHERE is_deleted=0 AND last_reset_date=:date AND COALESCE(terminal_series,'')=COALESCE(:terminalSeries,'')` (`RestaurantDao.kt:191`). Whole method is `@Transaction` (`RestaurantDao.kt:168`), i.e. one SQLite transaction.
- **Room `@Transaction` semantics:** per https://developer.android.com/training/data-storage/room/accessing-data#atomic-operations the annotation wraps all DAO calls in `BEGIN … COMMIT`; SQLite WAL (Room default via `SupportSQLiteDatabase`) serializes *writes* via a single-writer lock, but *reads* inside the transaction are not row-locking. Two concurrent writers can both read the same `MAX` before either writes, so `raiseAtLeast` is last-writer-wins `MAX()` rather than a serialization barrier.
- **Entity mapping:** `BillEntity.kt:47 dailyOrderId Long` (not null), `49 dailyOrderDisplay String`, `95 lastResetDate String` (default `''`), `139 recordOrigin/recordScope` etc. Display formatted by `OrderIdManager.getDailyOrderDisplay:15` as `counter.padStart(2,'0')` with optional `series-` prefix.

### 2.2 Invoice identity

- **Client:** `BillCreationUseCase.kt:267 allocateInvoiceIdentity` — returns `null` if `terminalSeries` blank (`:268`); else `displaySeries = series.first().uppercase`, `FY = (month>=4?year:year-1)%100 padStart(2)`, `invoiceSeries = "$FY$series"` (`:274`), `sequence = getMaxInvoiceSequence(invoiceSeries)+1` (`:275`), `invoiceNumber = "$displaySeries${sequence.padStart(2,'0')}"` (`:280`). `getMaxInvoiceSequence` is `SELECT MAX(invoice_sequence) WHERE restaurant_id=:rid AND invoice_series=:series AND is_deleted=0` (`BillDao.kt:885`). Used at bill-create time (`BillCreationUseCase.kt:137`) before `isDeleted` filtering matters; `BillEntity.kt:118-126` stores `terminalSeries,financialYear,invoiceSeries,invoiceSequence,invoiceNumber`.
- **Server — BillServiceImpl path (trusted terminal push):** `BillServiceImpl.java:97 allocateMissingInvoiceNumbers` — for each bill missing `invoiceNumber` and with non-blank `terminalSeries` where `terminalRepository.findAndLockByRestaurantIdAndTerminalSeries(...).isPresent()` (`:107`), `FY` same formula (`:114`), `key = series|FY`, `sequence = findMaxInvoiceSequence(tenant,series,FY)+1` (`:117`) where `findMaxInvoiceSequence` is `SELECT COALESCE(MAX(invoiceSequence),0) WHERE restaurantId=:r AND terminalSeries=:s AND financialYear=:fy AND isDeleted=false` (`BillRepository.java:23`), then `invoiceSeries=FY+series`, `invoiceNumber=buildInvoiceNumber(series,seq)` (`:122`) which does `series[0].uppercase + "%06d".format(seq)` truncated to 16 (`BillServiceImpl.java:136`). Map `nextBySeries` batches increments within same HTTP request (`:116-123`). The terminal row lock is `RestaurantTerminalRepository.java:30 @Lock(PESSIMISTIC_WRITE) SELECT t WHERE restaurantId=:r AND terminalSeries=:s` — a DB row-level `SELECT … FOR UPDATE` that serializes concurrent requests for the same series (doc comment `:25-28`).
- **Server — GenericSync bill path:** no terminal lock; pushes go through `GenericSyncService.handlePushSync` → `BillSyncService.validateBillNumberConflicts` → `saveAll(allRecordsToSave)` (`GenericSyncService.java:640`) → on `DataIntegrityViolationException` fall back to `SyncFallbackSaver.saveRecord` with `REQUIRES_NEW` (`SyncFallbackSaver.java:23`) and per-record `attemptIdempotentRecovery` by `publicToken` (`GenericSyncService.java:684`, `BillSyncService.java:183`). Idempotent recovery also covers `publicToken` duplicates (`BillSyncService.java:183`).
- **Constraints:** `V26__multidevice_invoice_identity.sql:80` `ux_bills_restaurant_invoice_series_active ON (restaurant_id,financial_year,invoice_series,invoice_sequence) WHERE is_deleted=false AND invoice_number IS NOT NULL` (guarded creación `:70-86`), `V26:100` `ux_bills_restaurant_terminal_daily_active ON (restaurant_id,last_reset_date,terminal_series,daily_order_id) WHERE is_deleted=false` (guarded `:88-106`), `V79:3` partial unique on `public_token WHERE NOT NULL`, `V19/V26:26` backfill `gen_random_uuid()`.

### 2.3 Local duplicate guard vs server guard

- **Local:** `BillDao.kt:441 insertFullBill` is `@Transaction`; computes `zoneId=Asia/Kolkata`, `orderDate = Instant.ofEpochMilli(bill.createdAt).atZone(zoneId).toLocalDate` (`:442`), `start=atStartOfDay`, `end=plusDays(1)-1ms` (`:444`), then `countActiveBillsByDailyIdAndDate(restaurantId,dailyOrderId,start,end,terminalSeries) >0 → throw SQLiteConstraintException("Duplicate order id #${display}…repair counters")` (`:446`). Guard counts only `is_deleted=0` rows matching `daily_order_id` + `COALESCE(terminal_series,'')` + `created_at BETWEEN start AND end` (`BillDao.kt:255`). Also pre-checks `operation_id` cross-bill uniqueness (`BillDao.kt:472`, `517`).
- **Server:** `BillSyncService.validateBillNumberConflicts:40` calls `BillRepository.findConflictingDailyOrder` (`BillRepository.java:141`) = `SELECT … WHERE restaurantId=:r AND lastResetDate=:d AND dailyOrderId=:id AND COALESCE(terminalSeries,'')=COALESCE(:series,'') AND NOT (deviceId=:dev AND localId=:local)`. No `created_at` range, no `is_synced` filter; hard-enforced also by the partial unique index above. Throws `IllegalStateException("Duplicate order #… already exists … Resolve it in Sync Center.")` (`BillSyncService.java:61`).
- **Divergence consequence:** a bill whose `createdAt` falls 1 ms outside the local `start..end` bucket (e.g. device clock just past midnight) passes the local guard but fails server `lastResetDate` check, ending in `failedLocalIds` → `quarantineFailedSyncRecords:656` → `markBillSyncFailedPermanently` (`BillDao.kt:940`) → Sync Center `failed_permanent` with `syncFailureReason` containing "Duplicate order".
- **Count query variants:** local `countActiveBillsByDailyIdAndDate` uses `COALESCE(terminalSeries,'')` match; `countDailyOrderIdentityConflicts` (`BillDao.kt:970`) and `getMaxDailyOrderIdForIdentity` (`BillDao.kt:988`) are the *repair* queries used by `repairFailedDailyOrderIdentity`.

### 2.4 AppDatabase migrations — idempotency & backfill

- **`MIGRATION_58_59:743`** `CREATE TABLE IF NOT EXISTS terminal_daily_counter (restaurant_id,terminal_id,date PK, daily_order_counter, is_synced, updated_at)` + index. Idempotent via `IF NOT EXISTS`.
- **`MIGRATION_57_58:604`** adds `bills.terminal_id, created_terminal_id, created_device_id, current_owner_terminal_id, version, lock_status, operation_id` each guarded by `hasColumn` (`:607` etc.), then backfills via `COALESCE(NULLIF(terminal_id,''), NULLIF(terminal_series,''), 'LEGACY_UNRESOLVED')` (`:632`) and creates `index_bills_restaurant_public_token UNIQUE (restaurant_id,public_token)` (`:736`) + `(restaurant_id,terminal_id,created_at)` and `(restaurant_id,financial_year,invoice_series,invoice_sequence)` indexes.
- **`MIGRATION_59_60:764`** adds `bills.record_origin DEFAULT 'local_created'` and `record_scope DEFAULT 'terminal_operational'` guarded (`:767`), then backfill steps (`:784-827`):
  1. `is_synced=0 AND is_deleted=0 → local_created/terminal_operational` (`:792`)
  2. `is_deleted=0 AND order_status='draft' AND payment_status='pending' AND created_terminal_id IS NOT NULL → local_created/terminal_operational` (preserves UPI drafts) (`:801`)
  3. (Marketplace placeholder skipped)
  4. Remaining `is_synced=1 AND record_origin='local_created' → server_imported/restaurant_history` (`:816`) — intentionally conservative; runtime `BillDao.reconcileLocalRecordScope` (`BillDao.kt:243`) corrects this terminal's own `created_terminal_id=:terminalId` rows back to `local_created/terminal_operational`. `MasterSyncProcessor.reconcileLocalBillScope:254` drives this after activation/pull.
- **Idempotency note:** all three use `IF NOT EXISTS` / `hasColumn` / `CREATE INDEX IF NOT EXISTS`; safe to re-run. Double-migration risk is only semantic: running `MIGRATION_59_60` twice keeps step 4's `server_imported` label on other terminals' history rows as intended, but a device that changes `SessionManager.getTerminalId()` between runs would re-reconcile a different subset — still idempotent because `reconcileLocalRecordScope` is `UPDATE … WHERE created_terminal_id=:tid` (not destructive).

### 2.5 409 recovery loop — daily vs invoice

- **Push path:** `MasterSyncProcessor.pushBatches:61` chunks 50, `pushBatch:128` calls `push` (Retrofit) → on `409` with `isolateHttpConflicts=true` binary-splits or parses `failedLocalIds` from body (`:173`). Top-level `GenericSyncService.handlePushSync:640` `saveAll` either commits or falls back per-record via `SyncFallbackSaver` (`:663`). Each per-record success is removed from `failedLocalIds`, each failure is added to both `failedLocalIds` and `failedReasons` (`:702`) after removing from `successfulLocalIds` (`:702`) — the "success AND failure" bug (`docs/reviews/*:16`) is now fixed.
- **Quarantine:** `MasterSyncProcessor.quarantineFailedSyncRecords:656` dispatches on `syncEntityLabel`; for `"bills"` calls `quarantineFailedBills:669` → `BillDao.markBillSyncFailedPermanently:940` (`sync_status='failed_permanent', sync_failure_reason=reason, sync_failed_at=now`). Bill-items/payments go to `SyncQuarantineEntity` (`:689-774`).
- **Insert path verbatim pull:** `MasterSyncProcessor.insertMasterData:824` `databaseProvider.withTransaction` — profiles, users, categories, menu items, variants, then `bills:1198` mapping preserves remote `dailyOrderId,invoiceSequence,invoiceNumber` verbatim, sets `recordOrigin/recordScope` to `server_imported/restaurant_history` except this-terminal's own `draft+pending` stays `local_created/terminal_operational` (`:1267-1282` — quoted in §6), users remapped via `identityKeys()`. After insert, `reconcilePulledBillsByClientFingerprint:619` marks local `isSynced` via `markBillAsSyncedIfUnchanged` to avoid re-push, and counters are raised (see §6).
- **Repair:** `BillDao.repairFailedDailyOrderIdentity:1004` (`@Transaction`) checks `!isSynced && serverId==null` (`:1013`), `recordOrigin=="local_created" && recordScope=="terminal_operational"` (`:1016`), `syncFailureReason contains "Duplicate order"` (`:1019`), then `countDailyOrderIdentityConflicts` (`:1023`) — if still conflicting, `repairedId = getMaxDailyOrderIdForIdentity+1` (`:1031`) else keep same; `display = series? "$series-$pad2" : pad2` (`:1040-1044`), updates `lastResetDate=correctedDate`, clears `syncFailureReason/failedAt` to `pending` (`:1045-1053`). No invoice-sequence repair exists — invoice collisions stay `failed_permanent` until admin reconciliation or a blank-`invoiceNumber` re-push triggers server `allocateMissingInvoiceNumbers`.

### 2.6 Timezone

- **Constant:** `AppConstants.DEFAULT_TIMEZONE = "Asia/Kolkata"` on both sides (`Android AppConstants.kt:9`, `server AppConstants.java:9`).
- **Client creation:** `BillCreationUseCase.kt:123 ZoneId.of(DEFAULT_TIMEZONE)` `today = LocalDate.now(zoneId).toString()` (`:124`), used for `lastResetDate` and counter date (`:129`), and invoice FY (`BillCreationUseCase.kt:270`). `RestaurantDao.kt:44,68,93` also use it for `lastResetDate`. `BillDao.kt:442` same for `start/end` derivation in duplicate guard.
- **Server:** `BillServiceImpl.java:46` `resolveZoneId(profile.timezone) else DEFAULT_TIMEZONE`, `formatter yyyy-MM-dd withZone(zoneId)` (`:49`), `bill.lastResetDate` defaulted from `createdAt/updatedAt` via that formatter (`:90`). `GenericSyncService.mergeCounterState:771` merges by parsed `LocalDate` dates, not wall-clock.
- **Midnight:** Both sides use `atZone(zoneId).toLocalDate()` — coherent if device clock is correct; divergent if device clock skews across 00:00.
- **Clock-skew guard:** `GenericSyncService.java:399 skew = abs(updatedAt - serverTime); if skew > maxClockSkewMs(=300_000) → reject with "Terminal clock is ahead/behind by … seconds"` (`:411`). Enforced per-record (`:398`). Recorded via `securityAuditService:CLOCK_SKEW_REJECTED` (`:408`).

### 2.7 lifetimeOrderId & publicToken & INV fallback

- **Server history:** `V1__init_schema.sql:151 lifetime_order_id BIGINT NOT NULL`; `V22__…sql:7` guarded dedup; `V26__…sql:23 ALTER COLUMN DROP NOT NULL` — from then on `Bill.lifetimeOrderId nullable` (`Bill.java:30`).
- **Client:** pre-54 schemas non-null (`AppDatabase 34.json:901` etc.), `MIGRATION_54_55:486` recreates `bills_new` with `lifetime_order_id INTEGER` nullable (`:498`), copies rows (`:541`), backfills `public_token` via `UUID.randomUUID()` (`:580`), re-creates indexes. `BillEntity.kt:51 lifetimeOrderId: Long?` nullable (`:51`), `AppDatabase 69.json:1202` confirms nullable.
- **Fallback display:** `BillEntity.kt:158 getInvoiceNumberDisplay()` = `invoiceNumber ?: (terminalSeries+pad2(seq)) ?: "INV"+lifetimeOrderId ?: dailyOrderDisplay ?: "DRAFT-LOC-$id"` (`:158-169`). New bills have `lifetimeOrderId=null` (`BillCreationUseCase.kt:165`), so `INV` fallback is *legacy-only*.
- **publicToken uniqueness:** `BillEntity.kt:22` `INDEX unique (restaurant_id,public_token)`, `V26:36` `ux_bills_public_token ON (public_token)` guarded by duplicate check, `V79:3` `idx_bills_public_token_unique ON (public_token) WHERE NOT NULL` — allows many `NULL`s, forbids duplicate non-null tokens. `GenericSyncService.java:328` idempotent retry checks `findByRestaurantIdAndPublicToken`; `BillSyncService.java:183` per-record `attemptIdempotentRecovery`.
- **Report impact:** queries like `SELECT … WHERE lifetime_order_id IS NOT NULL GROUP BY lifetime_order_id` (legacy `V22` indexes, dropped in `V26:64`) no longer cover new rows; any report that `COUNT(*) HAVING COUNT(*) >1 ON lifetime_order_id` (`BillDao.kt:783` comment notes `SQLite groups all NULLs together — which would falsely flag every new bill as duplicate` so `getDuplicateInvoiceNumberGroups` (`:783`) now groups by `invoice_number IS NOT NULL AND !=''` (`:791`), and `getDuplicateDailyOrderGroups` (`:804`) groups by `DATE(created_at,'unixepoch','localtime'),daily_order_id,COALESCE(terminal_series,'')`). Legacy dashboards using `lifetimeOrderId` must migrate to `invoiceNumber` / `publicToken`.

---

## 3. Stress Test — 5-device concurrency (same `restaurant_id`)

### 3.1 Setup

- Restaurant R has 5 activated terminals T1…T5, each with its own `terminal_id` (UUID) and `terminalSeries` (e.g. `A,B,C,D,E`; series uniqueness enforced by `ux_restaurant_terminal_series` `V26:54`). Same `restaurant_id`, same `AppConstants.DEFAULT_TIMEZONE`.
- All devices online enough to push, but billing stays offline-first (Room first, sync later). Test isolates the two collision domains separately.

### 3.2 Scenario A — 00:01 IST, each device creates 1 bill

**Case A1: 5 distinct series (`A`-`E`) — terminal_series differ**

| Device | allocated `dailyCounter` | `lastResetDate` | `terminalSeries` | `dailyOrderDisplay` | local `countActiveBillsByDailyIdAndDate` | server `findConflictingDailyOrder` | result |
|--------|--------------------------|-----------------|------------------|---------------------|------------------------------------------|------------------------------------|--------|
| T1 | 1 | 2026-09-02 | A | A-01 | 0 (own DB empty) | no conflict | synced |
| T2 | 1 | 2026-09-02 | B | B-01 | 0 | no conflict | synced |
| …T5 | 1 | 2026-09-02 | E | E-01 | 0 | no conflict | synced |

*Expected: all 5 succeed.* Composite keys:
- Client counter PK is `(restaurant,terminal,date)` — each terminal has its own row, so all get `1` without touching each other (`RestaurantDao.kt:155`).
- Server daily uniqueness is `(restaurant,lastResetDate,terminalSeries,dailyOrderId)` (`V26:100` + `BillRepository.java:141`); distinct `terminalSeries` → distinct keys → no violation.  
- Invoice side: if `terminalSeries` all present, each allocates `invoiceSequence=1` per *distinct* `invoiceSeries` (`FY+series`), so again no collision.

*Evidence:* design intent is per-terminal-per-day numbering (`OrderIdManager` + `TerminalDailyCounterEntity`); the 5-device acceptance suite labels this "per-terminal series + invoice_series-scoped sequence (V26)" (`docs/qa/PRE_MARKETING_5POS_ACCEPTANCE_SUITE.md:309`).

**Case A2: 2 devices share the same series (misconfig: T1 & T2 both `A`) — terminalSeries equal, terminal_id distinct**

- Both read local `MAX(daily_order_id) =0`, both `raiseAtLeast(0)` no-op, both `insertOrIncrement` their *different* `terminal_daily_counter` rows (because PK includes `terminal_id`) → both commit `dailyCounter=1` locally, both `insertFullBill` local guard sees `countActiveBillsByDailyIdAndDate` =0 locally (other device's row not in local DB) → both insert `A-01` with `dailyOrderId=1, lastResetDate=2026-09-02, terminalSeries=A`.
- On push, first writer wins the server `INSERT`. Second hits either `BillSyncService.validateBillNumberConflicts:53` (`findConflictingDailyOrder` finds the first row) *before* `saveAll`, or the DB unique index `ux_bills_restaurant_terminal_daily_active`. In either case it lands in `failedLocalIds` with reason `Duplicate order #A-01 already exists …` (`BillSyncService.java:61`). Same for invoices: both read `MAX invoice_sequence` locally =0, both propose `A000001` (server) / `A01` (client). Second push hits `ux_bills_restaurant_invoice_series_active` → per-record `REQUIRES_NEW` fallback isolates it to `failedLocalIds` (`GenericSyncService.java:681`), which `MasterSyncProcessor.quarantineFailedBills:669` marks `failed_permanent`. Client must run `repairFailedDailyOrderIdentity` (re-allocates to 2) to resubmit; invoice collisions have no auto-repair — they need a fresh allocation (blank `invoiceNumber` path) or manual admin reconciliation (`docs/planning/PLAN.md:109`).

### 3.3 Scenario B — 5 devices doing 1 bill each at 00:00:30 IST with 120 ms inter-arrival (WAL concurrency)

SQLite WAL allows one writer at a time; 5 concurrent `incrementAndGetTerminalDailyCounter` calls on *different* terminal rows serialize on the DB write lock. Each `insertOrIncrement` is a single UPSERT, so all 5 get distinct monotonic counters *within each terminal* (all `1`s for first bill). No lost update.

The only inter-terminal race is the `MAX … raiseAtLeast` backfill window inside `incrementAndGetTerminalDailyCounter:168`:
- T1's transaction: reads `MAX(daily_order_id)=0`, raises to `0` (no-op), UPSERTs `+1` → `1`.
- Concurrent pull inserts a server-pulled bill for T1 with `dailyOrderId=3` for today and raises `terminal_daily_counter` to `3` via `raiseTerminalDailyCounterAtLeast` (`MasterSyncProcessor:1441`).
- If T1's `raiseAtLeast` read `MAX` *before* the pull inserted, its post-pull counter is `1` while bills table already has `3` for that series/date; next local bill would attempt `2` then hit server duplicate (since `3` already exists) → loop. The wrapper does mitigate by re-seeding after pull (`MasterSyncProcessor:1413 correct daily counter`, `:1440 raiseTerminalDailyCounterAtLeast`), but that sync runs on a different coroutine; the window between `getMax…AcrossAllBills` and `insertOrIncrement` inside the same `@Transaction` is still non-locking on the `bills` table, so a pull inserted between those two statements would be missed until the *next* bill.

*Practically for 5 devices same-series, first-day first-bill race is not 5-way; it is 1-way per terminal — the other 4 devices don't contend on the same counter row.*

### 3.4 Scenario C — 5 devices pushing same series/FY invoice simultaneously (offline batch: each queued 1 bill, then all come online)

- Each device locally did `SELECT MAX(invoice_sequence) WHERE invoiceSeries='25A'` → `0`, allocated `1`, pushed.
- Server receives 5 independent HTTP pushes near-simultaneously.
  - Via `BillServiceImpl.allocateMissingInvoiceNumbers` (trusted-token push): each request does `findAndLockByRestaurantIdAndTerminalSeries` (`BillServiceImpl.java:107`) — `SELECT … FOR UPDATE` serializes. Request 1 reads `MAX=0 → seq1`, inserts `1`; request 2 waits, then reads `MAX=1 → seq2`, inserts `2`; … request 5 gets `5`. No collision.
  - Via `GenericSyncService` legacy sync: 5 concurrent `saveAll` batches (no terminal lock) race to `INSERT`; first batch commits, second hits `DataIntegrityViolationException` on `ux_bills_restaurant_invoice_series_active`; fallback loop (`SyncFallbackSaver:23 REQUIRES_NEW`) commits the 4 non-colliding rows but the second's colliding row is moved to `failedLocalIds`. Subsequent retry with blank `invoiceNumber` would be re-allocated; with client-provided `invoiceNumber` it stays failed.

---

## 4. Failure Modes (with repro steps)

### F1 — Cross-device daily collision (P0)

- **Symptom:** bill shows `Sync failed: Duplicate order #A-01 … Resolve it in Sync Center` and stays `failed_permanent`.
- **Repro:**
  1. Activate T1 & T2 on same restaurant with same `terminalSeries=A` (misconfig or deliberate shared series).
  2. Airplane-mode both tablets.
  3. At 00:01 IST on each, create 1 bill (each gets `A-01` locally — `BillDao.kt:446` sees 0).
  4. Bring both online; push T1 then T2.
  5. Observe T2 in `failedLocalIds` with `Duplicate order` reason (`BillSyncService.java:61`).
- **Repair:** Sync Center → `BillDao.repairFailedDailyOrderIdentity:1004` (`padStart(2)` re-render). No auto-retry until user taps Repair.
- **Guard now:** server `ux_bills_restaurant_terminal_daily_active` (`V26:100`) + `GenericSyncService:702` removes from success + quarantine. Display divergence after repair: `repairFailed…` uses `padStart(2)` while server invoice still `%06d` — report `A-02` vs invoice `A000002`.

### F2 — Invoice number collision (P0 — GST-relevant)

- **Symptom:** same as F1 but `ux_bills_restaurant_invoice_series_active` violation, message is the Postgres constraint text sanitized to 240 chars (`GenericSyncService:764`). No client `Duplicate order` wording, so `repairFailedDailyOrderIdentity` pre-check (`:1019`) fails — repair button refuses: `"This bill does not have a repairable daily-order conflict."`.
- **Repro:** same as F1 but observe server logs `DataIntegrityViolation … ux_bills_restaurant_invoice_series_active` (`GenericSyncService.java:664` log). Devices that pre-generated `invoiceNumber` (offline path) are not overwritten by `allocateMissingInvoiceNumbers` (it skips `invoiceNumber.isNotBlank()` `BillServiceImpl.java:100`).
- **Fix history:** client `getMaxInvoiceSequence` now filters by `invoice_series` exactly (`BillDao.kt:885` comment `:885-892` notes it replaces the old loose `terminal_series+financialYear` filter that missed pulled bills — see `docs/planning/PLAN.md:111`). Server allocation now batches `nextBySeries` per request (`BillServiceImpl.java:116`) and serializes via `PESSIMISTIC_WRITE` (`RestaurantTerminalRepository.java:30`).
- **Residual:** 5-device batch where each pre-allocated `A000001` cannot be auto-recovered; operator must blank the invoice or re-push via the `allocateMissingInvoiceNumbers` path (requires terminal row to exist, otherwise skip `:107`).

### F3 — `MAX` backfill window (P1)

- **Symptom:** after pull, next local bill gets a daily counter *behind* the max pulled bill, pushes, hits duplicate, quarantines.
- **Repro:**
  1. Device has 2 local bills today (`max=2`), counter `terminal_daily_counter=2`.
  2. Another terminal pushed 3 bills today; current device pulls them (insertMasterData `MasterSyncProcessor:1413` should raise counter to `maxDailyToday`, but pull logic groups by `today==billDate` via `createdAt` conversion (`:1417-1423`) — bills whose `lastResetDate` is today but whose `createdAt` fell just before midnight IST due to device clock skew are filtered out).
  3. Immediately create a bill on current device before post-pull correction runs → counter still `2`, allocates `3`, but server already has `3` for that series → duplicate.
- **Code references:** counter seeding after pull `MasterSyncProcessor:1440 raiseTerminalDailyCounterAtLeast`, daily max calc `MasterSyncProcessor:1417`, counterpart `RestaurantRepository.incrementAndGetTerminalDailyCounter:168` `MAX…raiseAtLeast`. Both note atomicity but not isolation.

### F4 — Timezone/clock-skew midnight (P1)

- **Symptom:** bill dated yesterday or tomorrow despite being created today IST.
- **Repro:** set device clock 6 min fast, create bill at 23:59 IST wall time → `LocalDate.now(Asia/Kolkata)` on device is tomorrow, `lastResetDate=2026-09-03` while server `serverTime` is 23:59 `2026-09-02`. If `skew ≤5 min` it passes `GenericSyncService:399` but `start/end` bucket (`BillDao.kt:444`) vs server `lastResetDate` mismatch causes silent daily partition drift. If `skew >5 min`, push rejected with `CLOCK_SKEW_REJECTED` (`GenericSyncService.java:408`).
- **Impact:** 5 devices with unsynchronized clocks at midnight produce inconsistent daily partitions; reports grouped by `lastResetDate` split the same wall day across two dates.

### F5 — Stale local `isDeleted` reuse (P2)

- **Repro:** legacy path: local bill soft-deleted (`is_deleted=1`) frees its `(restaurant,lastResetDate,terminalSeries,dailyOrderId)` in the partial index (`WHERE is_deleted=false`). New bill reuses same `dailyOrderId` without conflict — by design — but local `countActiveBillsByDailyIdAndDate` (`BillDao.kt:255`) also filters `is_deleted=0`, so reuse allowed locally too; however `publicToken` unique index still forbids reusing the *same* token — a retry with same token is idempotent, not a new bill.

### F6 — Pre-`MIGRATION_57_58` legacy bill ownership (P2)

- **Repro:** DB upgraded from v57 with `LEGACY_UNRESOLVED` backfill (`AppDatabase.kt:632`). Such bills have `terminal_id='LEGACY_UNRESOLVED'` and are reclaimable only by their original `deviceId` (`GenericSyncService.java:338` `trustedDeviceId.equals(createdDeviceId)`), otherwise cross-terminal push rejected as `CROSS_TERMINAL_UPDATE`. 5-device upgrade where 2 tablets upgrade simultaneously each with legacy bills from same `deviceId` history can see one side's reclaim win and the other's reject.

---

## 5. Server vs Client Divergence

| Aspect | Client | Server | Divergence & effect |
|--------|--------|--------|----------------------|
| Daily uniqueness key | `(restaurant, lastResetDate≈today? via MAX+counter, dailyOrderId, COALESCE(terminalSeries,''))` + local `created_at` IST window (`RestaurantDao.kt:191`, `BillDao.kt:255`) | `(restaurantId, lastResetDate, terminalSeries, dailyOrderId)` without `created_at` window + DB partial unique index (`BillRepository.java:141`, `V26:100`) | Bills that pass local window check can fail server date check; repair uses `padStart(2)` vs server display not enforced. |
| Invoice uniqueness key | `(restaurantId, invoiceSeries)` where `invoiceSeries=FY+terminalSeries`, `MAX(invoice_sequence)` filtered by exact `invoiceSeries` (`BillDao.kt:885`) | `(restaurantId, terminalSeries, financialYear)` composite for `MAX`, `invoiceSeries=FY+terminalSeries` stored, uniqueness on `(restaurantId, financialYear, invoiceSeries, invoiceSequence)` (`BillRepository.java:23`, `V26:80`) | Client and server `MAX` filters are equivalent now, but history differed (client used to filter loose `terminal_series+FY` per `PLAN.md:111`). |
| Invoice format | `displaySeries.first().uppercase + padStart(2)` → `A01`, `A10`, `A100` (`BillCreationUseCase.kt:280`, `OrderIdManager.kt:16`, `BillEntity.kt:162`) | `displaySeries[0].uppercase + "%06d"` → `A000001` truncated to 16 (`BillServiceImpl.java:136`) | Same sequence renders different `invoiceNumber`; a client-printed `A01` will not match server-printed `A000001` for the same logical sequence. GST e-invoice expects `%06d` width. |
| Serialization of invoice allocation | None — local `MAX+1` is read-non-locking | `SELECT … FOR UPDATE` on `restaurant_terminal` row (`RestaurantTerminalRepository.java:30`) inside `BillServiceImpl.allocateMissingInvoiceNumbers:107`, plus `nextBySeries` map batching (`:116`) | 5 concurrent offline allocations all produce `1` locally; 5 concurrent server allocations are serialized per-series. |
| Batch conflict handling | N/A (single bill insert) | `saveAll` all-or-nothing → `DataIntegrityViolationException` → `SyncFallbackSaver.REQUIRES_NEW` per-record retry (`GenericSyncService.java:677`, `SyncFallbackSaver.java:23`), `attemptIdempotentRecovery` by `publicToken` (`BillSyncService.java:183`) | Pre-fix (`docs/reviews/*`) the fallback ran inside the aborted outer TX and persisted nothing → infinite 500 loop. Now fixed via `REQUIRES_NEW`. |
| Clock/time | Device wall clock + `ZoneId Asia/Kolkata` (`BillCreationUseCase.kt:123`) | Server wall clock + profile timezone or `Asia/Kolkata` (`BillServiceImpl.java:46`), skew guard `>300s` (`GenericSyncService.java:399`) | Device 4 min fast still passes skew guard but computes wrong IST date branch. |
| Deletion handling | Partial index `WHERE is_deleted=false` not present locally, but `COUNT` filters `is_deleted=0` | Partial unique indexes `WHERE is_deleted=false` (`V26:81,102`) | Agree. |
| Token idempotency | Local `public_token` backfilled in `MIGRATION_54_55:580` via `UUID.randomUUID()`; `index_bills_restaurant_public_token UNIQUE` (`AppDatabase.kt:736`) | `gen_random_uuid()` backfill (`V26:26`), guarded unique `ux_bills_public_token` (`V26:36`), `V79:3` partial unique `WHERE NOT NULL` | Both allow many NULLs; both guard creation on duplicates. Client pull reconciliation via `reconcilePulledBillsByClientFingerprint:619` maps by `publicToken` before `(deviceId,localId)`. |

---

## 6. Verification Checklist (primary-source)

- [ ] `RestaurantDao.kt:153` UPSERT is single-statement atomic — verified.
- [ ] `RestaurantDao.kt:168` composite is `@Transaction` but *not* single-statement; `MAX` read is non-locking — verified (`191`, `208` docs + Android Room docs https://developer.android.com/training/data-storage/room/accessing-data#atomic-operations).
- [ ] `BillDao.kt:255` vs `BillRepository.java:141` key mismatch (window vs date) — verified.
- [ ] `MIGRATION_58_59:743` PK and `MIGRATION_59_60:764` backfill + runtime reconcile `MasterSyncProcessor:254` — verified.
- [ ] `BillCreationUseCase.kt:280 padStart(2)` vs `BillServiceImpl.java:137 %06d` — verified.
- [ ] Server `findAndLock…PESSIMISTIC_WRITE` `RestaurantTerminalRepository.java:30` and `BillServiceImpl.java:107` vs client non-locking `BillDao.kt:885` — verified.
- [ ] `SyncFallbackSaver.java:23 REQUIRES_NEW` fixes the aborted-TX bug cited in `docs/reviews/KHANABOOK_THREE_VOICE_REVIEW_2026-08-24.md:15` and `KHANABOOK_PRODUCTION_RELIABILITY_AUDIT_2026-08-17.md:14` — verified.
- [ ] `MasterSyncProcessor:1198 pull verbatim` vs `656 quarantine` + `BillDao.kt:1004 repair` — verified (repair scope is daily-only).
- [ ] `AppConstants.kt:9` / `AppConstants.java:9` `Asia/Kolkata` everywhere + `GenericSyncService.java:399` 300s guard — verified.
- [ ] `V26:23 lifetimeOrderId DROP NOT NULL` + `AppDatabase.kt:498 nullable` + `BillEntity.kt:51 Long?` + `BillEntity.kt:158 fallback` — verified.
- [ ] `publicToken` uniqueness guarded (`V26:28`, `V79:3`, `AppDatabase.kt:736`) — verified.

---

## 7. Recommendations

### P0 — must fix before marketing 5-POS scale

**P0-1 Standardize invoiceNumber format.**
Align client to server: change `BillCreationUseCase.kt:280` and `BillEntity.kt:162` from `padStart(2)` to `"%06d".format(sequence)` (and `OrderIdManager.kt:16` for display helper) so printed and stored `invoiceNumber` lengths match. Otherwise GST reports and printed copies diverge, and `getInvoiceNumberDisplay()` fallback (`BillEntity.kt:160`) will continue emitting 2-char numbers for pulled bills that had no `invoiceNumber`. *Files:* `Android/.../BillCreationUseCase.kt:280`, `Android/.../BillEntity.kt:162`, `Android/.../OrderIdManager.kt:15`.

**P0-2 Guarantee per-terminal-series serialization for offline-first invoice allocation.**
Today offline bills pre-allocate `invoiceSequence` locally and push with `invoiceNumber` non-blank, so `BillServiceImpl.allocateMissingInvoiceNumbers:100` skips them (`isNotBlank → continue`) and the DB unique index is the only guard. For 5-device same-series, this forces a quarantine loop. Recommended: leave offline pre-allocation in place for offline printing, but on push treat `invoiceNumber` as *advisory* — if it collides, server should allocate the next `MAX+1` under the `PESSIMISTIC_WRITE` lock instead of hard-failing (or at minimum auto-clear `invoiceNumber` to trigger `allocateMissingInvoiceNumbers` and re-push). Document that pre-printed offline invoice numbers may be renumbered on sync (requires business sign-off).

**P0-3 Keep both DB hard constraints and quarantine loop; do not drop `ux_bills_restaurant_terminal_daily_active` / `ux_bills_restaurant_invoice_series_active`.**
They are the only cross-device truth. Ensure migrations never drop them (already guarded `DO $$ IF NOT EXISTS … duplicate check` `V26:70-106`). Add an integration test that asserts both indexes exist (like `server/src/test/java/com/khanabook/saas/MultiDeviceInvoiceSyncIntegrationTest.java:35`).

### P1 — high, fix within one sprint

**P1-1 Narrow the `MAX…raiseAtLeast` window for daily counter.**
Either:
- (a) Make `incrementAndGetTerminalDailyCounter` single-statement: replace the `MAX`+`raise`+`insertOrIncrement` sequence with one UPSERT that does `COALESCE(MAX(daily_order_id)…)+1` atomically, or
- (b) Add a post-pull `raiseTerminalDailyCounterAtLeast` that uses `lastResetDate` (server-authoritative) rather than `createdAt BETWEEN` to compute `maxDailyToday` (fix `MasterSyncProcessor:1417` filter drift due to device clock skew), and document the `MAX` non-locking behavior in `RestaurantDao.kt:149` comment (already done, but add explicit "non-locking read — see Room @Transaction docs" pointer).

**P1-2 Align duplicate-guard keys.**
Make client `countActiveBillsByDailyIdAndDate` also check `lastResetDate=:date` *or* change it to the same `(restaurant,lastResetDate,terminalSeries,dailyOrderId)` triple the server uses. The current `created_at` window and `lastResetDate` key can contradict across the midnight 1-ms boundary and across clock skew. Add a second local check on `lastResetDate` before the window check, or drop the window entirely in favor of the date key (which is already the server's partition).

**P1-3 Centralize clock source for IST date.**
`BillCreationUseCase.kt:123 LocalDate.now(ZoneId.of(DEFAULT_TIMEZONE))` uses device wall clock. Consider deriving `today` from `System.currentTimeMillis()` anchored to server time after last successful sync (cached `serverTime` drift-corrected), or at least NTP-correct the device before midnight operations. Already partially mitigated by `GenericSyncService:399` 5-min skew reject, but that does not fix date-branch misclassification within ±5 min.

**P1-4 Add invoice-collision auto-repair analogous to `repairFailedDailyOrderIdentity:1004`.**
Today invoice collisions have no local repair; they stay `failed_permanent` with `failedReasons` containing the raw constraint text. Add `BillDao.repairFailedInvoiceIdentity` that blank outs the conflicting `invoiceNumber`/`invoiceSequence` and re-queues via the `allocateMissingInvoiceNumbers` path, or exposes a Sync Center "Re-allocate invoice" action.

### P2 — medium, roadmap

**P2-1 Deprecate `lifetimeOrderId` from all reports.**
Audit `BusinessReadService.java`, admin revenue queries (`BillRepository.sumCompletedRevenue:82` etc.), and any `lifetime_order_id` GROUP BY — new rows have `NULL`, so counts/sums will silently under-report if filtered on it. Point all dashboards at `invoice_number` / `public_token` (`BillEntity.kt:158` fallback already does, but server queries may not). Migration `V26:23` already made column nullable; consider a follow-up that stops writing it entirely and documents `INV` fallback as display-only.

**P2-2 Make migration backfill fully deterministic across re-installs.**
`MIGRATION_59_60:816` marking `is_synced=1` rows as `server_imported` is corrected only at runtime via `reconcileLocalBillScope:254`. Ensure that path runs *before* any `incrementAndGetTerminalDailyCounter` after first login (it currently does — `reconcileLocalBillScope` seeds `terminal_daily_counter` from `getMaxDailyOrderIdForTerminalToday` `:271`). Add an instrumented assertion that counter ≥ max bill after `insertMasterData:1413` completes.

**P2-3 Document series-sharing policy.**
State explicitly: "Sharing a `terminalSeries` across physical terminals is unsupported for 5-device scale — per-terminal-series daily/invoice counters assume 1:1 `terminal_id:terminal_series`. If a shared series is required for GST (e.g., single counter series `A` for all POS), allocate daily/invoice via a *restaurant-wide* counter, not `terminal_daily_counter`." Add to runbook and to `RestaurantTerminal` admin validation.

---

## 8. Appendix — Key File Map

| Concern | Primary file:line |
|---------|-------------------|
| Daily counter UPSERT | `Android/.../dao/RestaurantDao.kt:153` `insertOrIncrement…` |
| Daily counter composite | `Android/.../dao/RestaurantDao.kt:168` `incrementAndGetTerminalDailyCounter` |
| Daily MAX scan | `Android/.../dao/RestaurantDao.kt:191` `getMaxDailyOrderIdAcrossAllBills` |
| Daily raise | `Android/.../dao/RestaurantDao.kt:208` `raiseTerminalDailyCounterAtLeast` |
| Local duplicate guard | `Android/.../dao/BillDao.kt:441` `insertFullBill`, `255` `countActiveBills…` |
| Daily repair | `Android/.../dao/BillDao.kt:1004` `repairFailedDailyOrderIdentity` |
| Invoice local MAX | `Android/.../dao/BillDao.kt:885` `getMaxInvoiceSequence` |
| Invoice allocation client | `Android/.../domain/manager/BillCreationUseCase.kt:267` `allocateInvoiceIdentity:280 padStart(2)` |
| Display helper | `Android/.../domain/manager/OrderIdManager.kt:15` `padStart(2)` |
| Invoice allocation server | `server/.../service/impl/BillServiceImpl.java:97` `allocateMissingInvoiceNumbers:116-137 %06d` |
| Invoice MAX server | `server/.../repository/BillRepository.java:23` `findMaxInvoiceSequence` |
| Daily conflict server | `server/.../repository/BillRepository.java:141` `findConflictingDailyOrder` + `server/.../sync/service/BillSyncService.java:40` `validateBillNumberConflicts` |
| Terminal lock | `server/.../repository/RestaurantTerminalRepository.java:30` `PESSIMISTIC_WRITE` + `BillServiceImpl.java:107` |
| Fallback REQUIRES_NEW | `server/.../sync/service/SyncFallbackSaver.java:23` |
| Batch save & quarantine | `server/.../sync/service/GenericSyncService.java:640` `saveAll`, `681 REQUIRES_NEW`, `656 quarantine…` |
| Pull verbatim vs scope quarantine | `Android/.../domain/manager/MasterSyncProcessor.kt:1198` `insertMasterData`, `656 quarantineFailedSyncRecords`, `119` `pull insertMasterData keep verbatim` description + `1267` scope logic |
| Timezone constant | `Android/.../domain/util/AppConstants.kt:9`, `server/.../utility/AppConstants.java:9` = `Asia/Kolkata` |
| Clock skew guard | `server/.../sync/service/GenericSyncService.java:399` `maxClockSkewMs` |
| V26 invoice identity | `server/.../resources/db/migration/V26__multidevice_invoice_identity.sql:15-106` |
| V79 token index | `server/.../resources/db/migration/V79__add_bills_public_token_unique_index.sql:3` |
| App migrations | `Android/.../data/local/AppDatabase.kt:743 MIGRATION_58_59`, `764 MIGRATION_59_60`, `604 MIGRATION_57_58`, `486 MIGRATION_54_55` |
| Bill entity | `Android/.../data/local/entity/BillEntity.kt:47 dailyOrderId`, `51 lifetimeOrderId?`, `158 fallback` |
| Server bill entity | `server/.../entity/Bill.java:30 lifetimeOrderId nullable`, `140 publicToken` |
| Atomicity docs | Room `@Transaction` https://developer.android.com/training/data-storage/room/accessing-data#atomic-operations ; SQLite WAL https://www.sqlite.org/wal.html |

---

## 9. Caveats

- Analysis is static-code. No live 5-device run, no DB log, no production `current transaction is aborted` evidence sampled. Counter-window quantitative likelihood (inter-arrival 120 ms vs WAL write lock hold) not measured.
- Assumes `terminalSeries` uniqueness per terminal (enforced by `ux_restaurant_terminal_series` `V26:54`). Shared-series behavior inferred from code; not exercised against a running Postgres.
- `REQUIRES_NEW` fix correctness depends on `SyncFallbackSaver` being a *separate Spring proxy* (it is a `@Component` distinct from `GenericSyncService` — proxy bypass not present).


# 11 - Phase 2.1 Database Audit Validation & Evidence Review Report
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **Role:** Independent Principal Database Architect & Senior Backend/Android Reviewer  
> **Target Systems:** PostgreSQL 16.15 (`kbook_saas`), Android Room SQLite (Schema v84, `84.json`), Flyway Migration Engine (v11.7.2)  
> **Audit Status:** VALIDATION & EVIDENCE AUDIT COMPLETE (STRICT READ-ONLY)  
> **Git Baseline:** Branch `main` | Commit `9cce3270` | Working Tree Unchanged (Untracked documentation only)  

---

## 1. Executive Summary & Review Scope

This independent Phase 2.1 review was conducted to rigorously validate the findings of the Phase 2 database architecture audit against **empirical codebase, database catalog, and migration evidence**. 

The Phase 2 preliminary audit established valuable architectural data, notably preserving the unrecorded `V131` migration state. However, forensic verification identified several critical areas where conclusions overreached evidence, terminology was imprecise, counts were miscalculated, and category boundaries overlapped. 

This report presents the authoritative, evidence-grounded reconciliation of the database architecture. **No application code, entity definitions, DAOs, schemas, or migrations were modified during this validation.**

---

## 2. Master Reconciliation of Database Counts

### 2.1 Exact Physical Count Reconciliation

| Architectural Metric | Phase 2 Preliminary Claim | Validated Live Evidence | Variance / Discrepancy Note | Catalog / Schema Proof Source |
|---|:---:|:---:|:---:|---|
| **PostgreSQL Base Tables (`public`)** | 25 | **25** | **0 (Accurate)** | `information_schema.tables WHERE table_schema = 'public'` |
| **PostgreSQL Columns (`public`)** | 393 | **393** | **0 (Accurate)** | `information_schema.columns WHERE table_schema = 'public'` |
| **PostgreSQL Primary Keys** | 25 | **25** | **0 (Accurate)** | `pg_constraint WHERE contype = 'p'` (1 PK per table) |
| **PostgreSQL Foreign Keys** | 31 | **10** | **-21 (Error in Prelim)** | `pg_constraint WHERE contype = 'f'` *(Prelim conflated JPA `@ManyToOne` annotations with database constraints)* |
| **PostgreSQL Unique Constraints** | 58 | **7** | **-51 (Error in Prelim)** | `pg_constraint WHERE contype = 'u'` *(Prelim conflated unique indexes with table constraints)* |
| **PostgreSQL Unique Indexes** | - | **57** | **+57 (Clarified)** | `pg_index WHERE indisunique = true` |
| **PostgreSQL Check Constraints** | 10 | **10** | **0 (Accurate)** | `pg_constraint WHERE contype = 'c'` *(Excludes 241 internal `NOT NULL` check constraints)* |
| **PostgreSQL Total Indexes** | 151 | **151** | **0 (Accurate)** | `pg_indexes WHERE schemaname = 'public'` (100% B-tree) |
| **PostgreSQL Views / Triggers** | 0 / 0 | **0 / 0** | **0 (Accurate)** | `information_schema.views` & `information_schema.triggers` |
| **PostgreSQL Sequences** | 7 | **7** | **0 (Accurate)** | `information_schema.sequences WHERE sequence_schema = 'public'` |
| **PostgreSQL Extensions** | 1 | **1** | **0 (Accurate)** | `pg_extension` (`plpgsql` v1.0) |
| **Flyway Migration Scripts on Disk**| 113 | **113** | **0 (Accurate)** | Directory `services/server/src/main/resources/db/migration/` |
| **Flyway Recorded History Rows** | 112 | **112** | **0 (Accurate)** | Table `flyway_schema_history` (`V1` through `V130`, rank 113) |
| **Android Room Schema Version** | 84 | **84** | **0 (Accurate)** | Compilation export [`84.json`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json) |
| **Android Room Entities / Tables** | 18 | **18** | **0 (Accurate)** | `84.json` `database.entities` array |
| **Android Room Columns** | 349 | **349** | **0 (Accurate)** | `84.json` aggregate `fields` count across 18 entities |
| **Android Room Primary Keys** | 18 | **18** | **0 (Accurate)** | `84.json` (15 single auto-inc PKs, 3 composite PKs) |
| **Android Room Foreign Keys** | 6 | **7** | **+1 (Error in Prelim)** | `84.json` *(Prelim missed `menu_items -> categories` FK)* |
| **Android Room Total Indices** | 35 | **33** | **-2 (Error in Prelim)** | `84.json` aggregate `indices` count |
| **Android Room Unique Indices** | 12 | **9** | **-3 (Error in Prelim)** | `84.json` aggregate `indices` with `unique = true` |
| **Android Room Migration Steps** | 67 | **67** | **0 (Accurate)** | Array `AppDatabase.ALL_MIGRATIONS` (`17` through `84`) |
| **Actual Installed Device Database**| Implied | **UNABLE TO VERIFY** | **Evidence Gap** | Headless server; no ADB device connected (`adb devices` empty) |

---

## 3. Findings Review: Accepted, Corrected, and Rejected

### 3.1 Accepted Findings (Evidence-Verified)
1. **Flyway Migration History Stops at V130:** Live `flyway_schema_history` contains exactly 112 records ending at version `130` (rank 113).
2. **PostgreSQL Catalog Contains V131 Indexes:** All 4 indexes defined in `V131` exist in PostgreSQL's `pg_indexes` catalog (`idx_bills_active_created`, `idx_bills_active_status_created`, `idx_menuitems_active_sync`, `idx_bill_payments_sync_pull`).
3. **Room v84 Schema Physically Retains 9 Legacy Printer Columns:** Inspection of [`84.json`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json#L72-L135) confirms that `restaurant_profile` physically contains `printer_enabled`, `printer_name`, `printer_mac`, `paper_size`, `printer_mode`, `kitchen_printer_enabled`, `kitchen_printer_name`, `kitchen_printer_mac`, `kitchen_printer_paper_size`.
4. **Android Room Migration Sequence is Unbroken:** JVM unit test [`DatabaseMigrationListConsistencyTest.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/test/java/com/khanabook/lite/pos/data/local/DatabaseMigrationListConsistencyTest.kt) validates that `ALL_MIGRATIONS` contains every contiguous integer step from 17 to 84 ($17 \to 18, \dots, 83 \to 84$) with zero gaps.
5. **Physical Local Tenant Database Isolation:** Android POS creates an independent, physically separate encrypted SQLite file per restaurant: `khanabook_lite_db_{restaurantId}` ([`DatabaseProvider.kt#L64`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/DatabaseProvider.kt#L64)).

### 3.2 Corrected Findings (Evidence-Adjusted)
1. **PostgreSQL Foreign Key Count (10 vs 31):**
   - *Preliminary claim:* 31 Foreign Keys.
   - *Empirical evidence:* Querying `pg_constraint WHERE contype = 'f'` reveals exactly **10 Foreign Keys** in `public`.
   - *Cause of error:* The preliminary report conflated logical JPA entity relationships (`@ManyToOne`, `@JoinColumn` in Spring Boot) with actual database foreign key constraints.
2. **Android Room Foreign Key Count (7 vs 6):**
   - *Preliminary claim:* 6 Foreign Keys, with an explicit assertion that `menu_items -> categories` was intentionally relaxed.
   - *Empirical evidence:* [`MenuItemEntity.kt#L9-L13`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/menu/data/MenuItemEntity.kt#L9-L13) and `84.json` explicitly declare `@ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE)`. Total Room foreign keys = **7**.
   - *Function of `menu_item_category_repairs`:* It is not a relaxed FK replacement; it temporarily parks category moves when a remote sync pull contains a destination category ID that has not yet reached the local device ([`MenuItemCategoryRepairEntity.kt#L8-L22`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/menu/data/MenuItemCategoryRepairEntity.kt#L8-L22)).
3. **Index Terminology (Ordinary Composite B-trees vs Covering Indexes):**
   - *Preliminary claim:* V131 created "covering indexes".
   - *Empirical evidence:* In PostgreSQL, a covering index is defined using the `INCLUDE` clause (`CREATE INDEX ... INCLUDE (col3, col4)`). None of the 4 indexes in `V131` use `INCLUDE`. They are standard **multi-column composite B-tree partial indexes**. They only cover queries whose `SELECT` list is strictly limited to the indexed columns.
4. **Index Performance Assessment:**
   - *Preliminary claim:* Indexes improve active order listing and sync performance.
   - *Correction:* `EXPLAIN ANALYZE` executions on empty or near-empty tables verify only that the planner picks the index path. They do not demonstrate measurable production latency reduction or I/O savings.
5. **WAL & SQLCipher Concurrency & Durability Guarantees:**
   - *Preliminary claim:* "WAL ensures zero database corruption and prevents reader/writer blocking."
   - *Correction:* SQLite WAL allows concurrent readers alongside a single writer, but **all write operations in SQLite remain strictly serialized** (one global writer). WAL substantially reduces partial-write corruption risks during power outages, but does not provide an absolute "zero corruption" guarantee (flash corruption, unbuffered I/O kernel panics, or checkpoint crashes can still corrupt databases). SQLCipher provides cryptographic confidentiality at rest, not ACID consistency.
6. **Decoupled Pub/Sub vs Delta Sync on Notifications:**
   - *Preliminary claim:* `notifications` (SQLite) $\leftrightarrow$ `notification_events` (PG) was classified as a bi-directional delta-synced shared table.
   - *Correction:* `notification_events` (PG) is a server-side transactional outbox dispatched via Firebase Cloud Messaging (FCM). `notifications` (SQLite) is an event cache populated by [`KhanaBookFirebaseMessagingService.kt#L64`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/notifications/worker/KhanaBookFirebaseMessagingService.kt#L64). They are not synchronized via the HTTP delta-sync engine (`MasterSyncProcessor`).
7. **Cashier Shifts Lifecycle State:**
   - *Preliminary claim:* Classified ambiguously as both shared and "unclear/deprecated".
   - *Correction:* Backend entities (`CashierShift.java`), sync endpoints (`/api/v1/sync/cashier-shifts`), and SQLite tables (`local_cashier_shifts`) are fully wired, but the Android UI was never hooked up. It is a **Partially Implemented / Dormant Feature**, not deprecated.

### 3.3 Rejected Findings (Disproven Statements)
1. **REJECTED: Claim that `menu_items` lacks a foreign key constraint to `categories` in SQLite.** (Disproven by `MenuItemEntity.kt#L9` and `84.json`).
2. **REJECTED: Claim that PostgreSQL enforces 31 Foreign Keys in `public`.** (Disproven by `pg_constraint` catalog which contains exactly 10).
3. **REJECTED: Claim that the git working tree was "clean" post-audit.** (Disproven by `git status` which shows 10 untracked files).
4. **REJECTED: Claim that V131 indexes are "covering indexes".** (Disproven by index definitions in `pg_indexes` which lack `INCLUDE` clauses).

---

## 4. Deep Forensic Audit: The V131 Discrepancy & `IF NOT EXISTS` Hazards

### 4.1 Root Cause & Current State
1. Migration file [`V131__add_active_order_and_delta_sync_covering_indexes.sql`](file:///var/www/kbook.iadv.cloud/services/server/src/main/resources/db/migration/V131__add_active_order_and_delta_sync_covering_indexes.sql) exists on disk.
2. The 4 indexes exist in the live PostgreSQL catalog (`pg_indexes`):
   - `idx_bills_active_created ON public.bills USING btree (restaurant_id, created_at DESC) WHERE (is_deleted = false)`
   - `idx_bills_active_status_created ON public.bills USING btree (restaurant_id, order_status, created_at DESC) WHERE (is_deleted = false)`
   - `idx_menuitems_active_sync ON public.menuitems USING btree (restaurant_id, server_updated_at) WHERE (is_deleted = false)`
   - `idx_bill_payments_sync_pull ON public.bill_payments USING btree (restaurant_id, server_updated_at) WHERE (is_deleted = false)`
3. Live table `flyway_schema_history` contains 112 rows and ends at version `130`.

### 4.2 Critical Hazard: The Masking Behavior of `CREATE INDEX IF NOT EXISTS`
The `V131` migration uses `CREATE INDEX IF NOT EXISTS <index_name>`. The database architecture team must understand how PostgreSQL evaluates this statement:

> [!WARNING]
> **PostgreSQL `IF NOT EXISTS` Blindness Hazard**  
> In PostgreSQL, `CREATE INDEX IF NOT EXISTS <name>` **only checks whether an index object with that identifier exists in the schema namespace**.  
> It does **NOT** compare:
> - Indexed column expressions or ordering (`ASC` vs `DESC`)
> - Partial filter predicates (`WHERE` clauses)
> - Storage parameters or index types (B-tree, GIN, etc.)  
> 
> If an index named `idx_bills_active_created` already exists with a completely different or corrupt definition (e.g., missing the `WHERE is_deleted = false` filter, or indexing the wrong columns), PostgreSQL emits a simple `NOTICE: relation "idx_bills_active_created" already exists, skipping` and returns success (`00000`).  
> 
> Consequently, when Flyway executes `V131` on startup, it will calculate the checksum, record the migration as successful in `flyway_schema_history`, but **silently skip creation without correcting any underlying index definition divergence**.

### 4.3 Action Directive
In accordance with the strict freeze, **`V131` must remain unrecorded and unrepaired**. No `flyway repair` or manual database inserts into `flyway_schema_history` may be executed until authorized.

---

## 5. Corrected Cross-Database Physical & Logical Mapping

To eliminate the category overlapping present in earlier reports, platform tables and data models are categorized into **mutually exclusive physical partitions**, with data lifecycle roles tracked independently as functional overlays:

```
Total Platform Tables: 43 Physical Tables
├── PostgreSQL Engine (25 tables)
│   ├── Shared with SQLite (9 tables)
│   └── PostgreSQL Exclusive (16 tables)
└── Android Room SQLite Engine (18 tables)
    ├── Shared with PostgreSQL (9 tables)
    └── SQLite Exclusive (9 tables)
```

### 5.1 Mutually Exclusive Physical Table Distribution (43 Tables Total)

#### A. Shared Physical Tables — Active Bi-Directional Delta Sync (8 Pairs = 16 Tables)
Synchronized bi-directionally over HTTPS via [`MasterSyncProcessor.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/domain/MasterSyncProcessor.kt) and [`SyncController.java`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/sync/controller/SyncController.java):
1. `bills` (PG, 43 cols) $\longleftrightarrow$ `bills` (SQLite, 56 cols)
2. `bill_items` (PG, 22 cols) $\longleftrightarrow$ `bill_items` (SQLite, 21 cols)
3. `bill_payments` (PG, 18 cols) $\longleftrightarrow$ `bill_payments` (SQLite, 21 cols)
4. `categories` (PG, 15 cols) $\longleftrightarrow$ `categories` (SQLite, 14 cols)
5. `menuitems` (PG, 25 cols) $\longleftrightarrow$ `menu_items` (SQLite, 26 cols)
6. `itemvariants` (PG, 18 cols) $\longleftrightarrow$ `item_variants` (SQLite, 16 cols)
7. `restaurantprofiles` (PG, 43 cols) $\longleftrightarrow$ `restaurant_profile` (SQLite, 65 cols)
8. `users` (PG, 25 cols) $\longleftrightarrow$ `users` (SQLite, 19 cols)

#### B. Shared Physical Tables — Dormant / Incomplete Feature (1 Pair = 2 Tables)
Backend entity, sync controller, and local Room table exist, but POS UI was never completed:
9. `cashier_shifts` (PG, 26 cols, 0 rows) $\longleftrightarrow$ `local_cashier_shifts` (SQLite, 25 cols, dormant UI)

#### C. PostgreSQL-Exclusive Physical Tables (16 Tables)
10. `restaurant_terminal` (14 cols, 172 rows) — Fleet enrollment and hardware series letter ('A'..'E') registry
11. `device_registration_request` (18 cols, 99 rows) — POS terminal pairing requests awaiting owner authorization
12. `device_tokens` (9 cols, 31 rows) — FCM push notification registration tokens
13. `security_audit_log` (11 cols, 5,632 rows) — Immutable server-side audit trail for authentication, bill voids, and settings
14. `email_outbox` (16 cols, 1,442 rows) — Asynchronous transactional outbox for PDF receipts and closing reports
15. `notification_events` (12 cols, 138 rows) — Server-side outbox for push notifications dispatched via FCM
16. `menu_extraction_jobs` (8 cols, 0 rows) — Cloud OCR queue for physical menu image processing
17. `stock_logs` (16 cols, 163 rows) — Server-side inventory movement audit logs (menu-item sales decrements)
18. `refresh_tokens` (8 cols, 843 rows) — Hashed JWT refresh tokens
19. `token_blocklist` (3 cols, 50 rows) — Revoked JWT identifiers (JTI)
20. `rate_limit_attempts` (5 cols, 2 rows) — Brute-force protection for login, OTP, and PIN endpoints
21. `otp_requests` (7 cols, 0 rows) — SMS / WhatsApp OTP challenge verification cache
22. `feature_flag` (6 cols, 14 rows) — Platform-wide feature toggle definitions
23. `feature_flag_override` (6 cols, 0 rows) — Tenant-specific feature override rules
24. `feature_flag_audit` (9 cols, 0 rows) — Audit trail for feature toggle adjustments
25. `flyway_schema_history` (10 cols, 112 rows) — Flyway migration execution ledger

#### D. Android Room SQLite-Exclusive Physical Tables (9 Tables)
26. `printer_profiles` (15 cols) — Local hardware bindings (Bluetooth MAC, USB, Wi-Fi LAN IP)
27. `kitchen_print_queue` (16 cols) — Local thermal KOT print job buffer and retry spooler
28. `kot_events` (12 cols) — Local kitchen ticket reprint audit history
29. `sync_quarantine_records` (11 cols) — Dead-letter queue for sync payloads rejected by validation
30. `menu_item_category_repairs` (5 cols) — Parked category moves for dishes arriving before their category
31. `terminal_daily_counter` (6 cols) — Local monotonic sequence generator for daily order numbers
32. `notifications` (10 cols) — Client-side FCM notification inbox and banner display cache
33. `staff_permissions` (7 cols) — Local cashier PIN hashes and permission bitmasks
34. `permission_cache` (4 cols) — Revision stamp tracking permission invalidations

*Reconciliation Verification:* $16 \text{ (Shared Active)} + 2 \text{ (Shared Dormant)} + 16 \text{ (PG Exclusive)} + 9 \text{ (SQLite Exclusive)} = \mathbf{43\text{ Physical Tables}}$. Every table is accounted for exactly once.

---

### 5.2 Functional Overlays (Logical Roles Across Physical Tables)

These classifications represent runtime behaviors and data lifecycles, not separate physical tables:

1. **Derived / Computed Attributes (Not Physical Tables):**
   - `daily_order_display`: Runtime formatted string (`terminal_series + "-" + daily_order_id`, e.g., `'A-042'`).
   - `invoice_number`: Fiscal identifier generated as `INV-{series}-{FY}-{sequence}`.
   - Tax Breakdown: `cgst_amount` ($= \text{tax} / 2$) and `sgst_amount` ($= \text{tax} / 2$).
   - `public_token`: Globally unique UUID v4 generated on the punching terminal.
2. **Ephemeral Operational Caches (Columns within Tables):**
   - `payment_attempt_status` in `bills`: In-flight lock preventing double-tap charges during UPI payments.
   - `staff_permissions.pin_hash`: Local salted SHA-256 digest enabling offline cashier lock-screen authorization.
3. **Decoupled Asynchronous Pub/Sub Pipeline:**
   - Server Outbox: `notification_events` (PG) $\xrightarrow{\text{Cloud FCM Push}}$ Client Inbox: `notifications` (SQLite). This is an event stream, not a symmetric delta sync table.

---

## 6. Offline-First Technical Audit & Evidence Matrix

| Distributed System Capability | Implementation Mechanism | Exact Source File & Line Evidence | Empirical Test Coverage | Architecture Caveat & Limitation |
|---|---|---|---|---|
| **Local Write Atomicity** | Encrypted SQLite inside Room transaction | [`BillingViewModel.kt#L310-L345`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/viewmodel/BillingViewModel.kt), [`BillDao.kt#L45`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillDao.kt#L45) | Verified in unit test | Writes are ACID, but SQLite strictly serializes writers (one global writer connection). |
| **Monotonic Order Sequencing** | Atomic local counter isolated by terminal series ('A'..'E') | [`TerminalDailyCounterDao.kt#L25`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/TerminalDailyCounterDao.kt#L25) | Verified in unit test | Counter resets at `business_day_cutoff_time`. Series assignment prevents multi-terminal collision. |
| **Storage Encryption** | SQLCipher 256-bit AES + Keystore passphrase | [`DatabaseProvider.kt#L193-L205`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/DatabaseProvider.kt#L193-L205), [`DatabaseModule.kt#L45-L75`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/di/DatabaseModule.kt#L45-L75) | Verified in unit test | Provides data confidentiality at rest; does not guarantee transaction consistency or prevent hardware flash failure. |
| **Outbox Dirty Tracking** | Local mutation sets `is_synced = 0`, updates timestamp | [`BillEntity.kt#L107`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillEntity.kt#L107) | Verified in contract test | Relies on application discipline; no database-level trigger enforces dirty flag updates. |
| **Periodic & Opportunistic Sync** | Android WorkManager (15-min periodic + exponential retry) | [`MasterSyncWorker.kt#L45-L75`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/worker/MasterSyncWorker.kt#L45-L75) | Verified in unit test | OS can delay WorkManager execution under aggressive Android battery optimization. |
| **In-Flight Duplicate Lock** | `payment_attempt_status` locks local record | [`BillEntity.kt#L32`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillEntity.kt#L32) | Verified in UI test | Prevents cashier double-tapping UPI generation on a single device. |
| **Push Idempotency** | PostgreSQL composite unique constraint `(restaurant_id, device_id, local_id)` | [`Bill.java#L18-L21`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/billing/data/Bill.java#L18-L21) | Verified in integration test | Returns existing `id` on re-push of previously synced records. |
| **Split-Tender Idempotency** | Unique `operation_id` per payment transaction | [`BillPayment.java#L28-L32`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/billing/data/BillPayment.java#L28-L32) | Verified in integration test | Format: `${restaurantId}:${terminalId}:${uuid}:${action}` prevents double payment inserts. |
| **Optimistic Concurrency (LWW Lock)** | `status_version` gate rejects stale status regressions | [`BillSyncService.java#L140-L175`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/billing/service/BillSyncService.java#L140-L175) | [`LwwConflictResolutionIntegrationTest.java#L60-L95`](file:///var/www/kbook.iadv.cloud/services/server/src/test/java/com/khanabook/saas/sync/LwwConflictResolutionIntegrationTest.java#L60-L95) | Paid/completed bills can never be reverted to draft by an offline terminal pushing old state. |
| **Server ID Reflection** | Server returns `{ localId: serverId }` map; client marks `is_synced = 1` | [`MasterSyncProcessor.kt#L420-L460`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/domain/MasterSyncProcessor.kt#L420-L460) | Verified in unit test | Requires atomic local batch update; network drop during response handling leaves records dirty for retry. |
| **Tombstone Replication** | `is_deleted = true` + `server_updated_at` tombstone propagation | [`BaseSyncEntity.java#L45-L48`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/sync/data/BaseSyncEntity.java#L45-L48) | Verified in integration test | Tombstones are replicated to all terminals during delta pulls. |
| **Hardware Fleet Authentication** | `X-Terminal-Token` header validated against hashed token | [`TerminalRequestFilter.java#L40-L85`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/core/security/TerminalRequestFilter.java#L40-L85) | Verified in integration test | Unpaired or deactivated terminals are blocked with HTTP 403. |
| **Local File Tenant Isolation** | Per-tenant database file `khanabook_lite_db_{restaurantId}` | [`DatabaseProvider.kt#L64`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/DatabaseProvider.kt#L64) | Verified in unit test | Guarantees physical file-level tenant isolation on shared Android hardware. |

---

## 7. Evidence Gaps & Inaccessible Information

1. **Installed Physical Schema on Deployed POS Devices:**
   - *Status:* **Unable to Verify on Live Hardware.**
   - *Reason:* This environment is a headless Linux server with no connected Android devices (`adb devices` returns an empty list).
   - *Significance:* The 18-table, 349-column inventory is verified from the compilation artifact [`84.json`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json). Whether a field device running an older APK (e.g. 1.0.33) upgraded cleanly without a crash-loop cannot be proven without pulling a database file via ADB from actual hardware.
2. **Empirical Index Performance Benchmark Under Load:**
   - *Status:* **Unable to Verify.**
   - *Reason:* The test database contains zero rows for the specific queries tested.
   - *Significance:* While PostgreSQL's cost-based query optimizer selects the `V131` indexes for test queries, production throughput gains, buffer hit rates, and disk I/O savings under heavy concurrent multi-tenant load cannot be proven without synthetic load generation.
3. **Multi-Terminal Concurrent Sync Write Contention on PostgreSQL:**
   - *Status:* **Unable to Verify.**
   - *Reason:* Read-only inspection cannot simulate multiple POS terminals simultaneously pushing transactions during peak lunch/dinner rushes (>50 bills/minute).

---

## 8. Documentation Hierarchy & Directory Integrity

Inspection of [`docs/database-architecture/`](file:///var/www/kbook.iadv.cloud/docs/database-architecture/) revealed that older discovery documents (generated earlier on Nov 18) coexist with the Phase 2 baseline files, resulting in overlapping numbering.

Per instructions, **no historical documents have been deleted or overwritten**. To establish clarity, the following authoritative hierarchy is established:

```
docs/database-architecture/
├── 11-phase-2-validation-report.md           <-- [AUTHORITATIVE TIER 1: Master Validation Report]
├── 10-database-architecture-baseline.md       <-- [AUTHORITATIVE TIER 2: Phase 2 Baseline (Read with Report 11)]
├── 01-postgresql-live-schema.md              <-- [AUTHORITATIVE TIER 2: Live PG Catalog Details]
├── 02-sqlite-room-schema.md                  <-- [AUTHORITATIVE TIER 2: Room Schema v84 Details]
├── 03-flyway-migration-inventory.md          <-- [AUTHORITATIVE TIER 2: Flyway History & V131 Drift]
├── 04-room-migration-inventory.md            <-- [AUTHORITATIVE TIER 2: Room Migration Chain (17-84)]
├── 05-cross-database-entity-mapping.md       <-- [HISTORICAL: Superseded by Section 5 of Report 11]
├── 06-relationships-and-constraints.md       <-- [HISTORICAL: Superseded by Section 2 of Report 11]
├── 07-index-inventory.md                     <-- [AUTHORITATIVE TIER 2: Index Catalog]
├── 08-offline-sync-data-architecture.md      <-- [AUTHORITATIVE TIER 2: Sync Architecture Details]
├── 09-schema-discrepancy-report.md           <-- [AUTHORITATIVE TIER 2: Discrepancy Matrix D-01..D-07]
└── [Historical Generation 1 Documents]       <-- [HISTORICAL ARCHIVE: Reference Only]
    ├── 01-database-inventory.md
    ├── 02-sqlite-schema.md
    ├── 03-postgresql-schema.md
    ├── 04-cross-database-mapping.md
    ├── 05-entity-relationship-diagram.md
    ├── 06-data-ownership-matrix.md
    ├── 07-sync-architecture-audit.md
    ├── 08-database-design-findings.md
    └── 09-proposed-database-architecture.md
```

- **Authoritative Master Reference:** Document `11-phase-2-validation-report.md` is the single source of truth for validated architecture counts, mutually exclusive entity mappings, and constraint numbers.
- **Phase 2 Baseline Suite:** Documents `01-postgresql-live-schema.md`, `02-sqlite-room-schema.md`, `03-flyway-migration-inventory.md`, `04-room-migration-inventory.md`, `07-index-inventory.md`, `08-offline-sync-data-architecture.md`, `09-schema-discrepancy-report.md`, and `10-database-architecture-baseline.md` serve as detailed supporting catalogs.
- **Historical Archive:** Documents `01-database-inventory.md` through `09-proposed-database-architecture.md` are preserved for historical audit lineage and should not be used for implementation decisions.

---

## 9. Final Validation Findings Matrix

| Finding ID | Classification | System / Scope | Summary of Validated Finding | Evidence Reference |
|:---:|:---:|:---:|---|---|
| **V-01** | **Confirmed Defect** | PostgreSQL / Flyway | Migration `V131` exists on disk and its 4 indexes exist in `pg_indexes`, but `flyway_schema_history` stops at V130. Unrecorded ledger drift. | Catalog `pg_indexes` vs `flyway_schema_history` |
| **V-02** | **Potential Risk** | Flyway Execution | `CREATE INDEX IF NOT EXISTS` in `V131` will silently skip if an index with that name exists, even if its definition diverges from the script. | PostgreSQL DDL semantics for `IF NOT EXISTS` |
| **V-03** | **Architectural Observation** | SQLite Room v84 | `restaurant_profile` physically retains 9 legacy printer columns in `84.json`. `@Deprecated` annotation in Kotlin does not alter SQLite schema. | [`84.json#L72-L135`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json#L72-L135) |
| **V-04** | **Documentation Gap** | PostgreSQL Constraints | Preliminary report claimed 31 Foreign Keys; live catalog contains exactly 10. Preliminary report conflated JPA entity associations with database constraints. | `pg_constraint WHERE contype = 'f'` |
| **V-05** | **Documentation Gap** | SQLite Room Constraints | Preliminary report claimed 6 Foreign Keys and claimed `menu_items -> categories` was relaxed; `MenuItemEntity.kt` explicitly declares an active `CASCADE` foreign key (total 7 FKs). | [`MenuItemEntity.kt#L9-L13`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/menu/data/MenuItemEntity.kt#L9-L13) |
| **V-06** | **Architectural Observation** | Index Architecture | V131 indexes are standard composite B-tree partial indexes, not covering indexes (`INCLUDE` clause absent). Planner selection does not prove empirical production speedup. | `pg_indexes.indexdef` |
| **V-07** | **Architectural Observation** | Shared Entities | `cashier_shifts` (PG, 0 rows) and `local_cashier_shifts` (SQLite) represent a partially implemented / dormant feature (backend & DTOs complete, Android UI missing). | `CashierShiftDao.kt`, `CashierShiftController.java` |
| **V-08** | **Architectural Observation** | Pub/Sub Architecture | `notifications` (SQLite) and `notification_events` (PG) are decoupled endpoints of an asynchronous FCM push pipeline, not a bi-directionally delta-synced table. | [`KhanaBookFirebaseMessagingService.kt#L64`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/notifications/worker/KhanaBookFirebaseMessagingService.kt#L64) |
| **V-09** | **Unable to Verify** | Physical Hardware | Physical schema installed on deployed field devices cannot be verified in this headless server environment (no ADB devices connected). | `adb devices` exit code 0, 0 devices |
| **V-10** | **Potential Risk** | Future Room v85 | Dropping the 9 legacy printer columns in Room v85 requires a full SQLite table recreation (temporary table, copy 55 columns, swap), which carries high migration risk on older devices. | SQLite table modification limitations |

---

## 10. Final Approval Status & Strict Hold

> [!IMPORTANT]
> **Phase 2.1 Validation Complete. Authoritative Baseline Reconciled.**  
> - All counts and catalog objects have been independently verified and reconciled.  
> - Overlapping categories have been replaced by a strictly partitioned 43-table physical mapping.  
> - Technical claims regarding WAL, SQLCipher, covering indexes, and performance have been adjusted to match empirical reality.  
> - The `V131` ledger drift is preserved as an active documented defect without automated repair.  
> - **In accordance with strict read-only rules, all implementation and schema changes remain on HOLD awaiting explicit user authorization.**

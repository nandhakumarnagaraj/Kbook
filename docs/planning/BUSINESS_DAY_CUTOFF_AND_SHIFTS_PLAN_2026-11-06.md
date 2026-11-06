# Business day cutoff, cashier shifts, and durable closing reports

**Status:** proposal — nothing implemented, no migration written
**Date:** 2026-11-06
**Scope:** `services/server` (Spring Boot + Flyway), `apps/android` POS (Room), `apps/web-admin` (Angular)
**Supersedes:** the "revised architectural RFC v2.0" reviewed on 2026-11-06. Its four schema and numbering assumptions are corrected in §0.2; everything else in it is compatible with this plan.

**Method:** static reading of every path that computes or consumes a business date, a daily order counter, a closing figure, or an outbound email. No server, Android, or web test was run and nothing was migrated. Facts marked **[verified]** are read out of the named file; items marked **recommendation** are choices this document proposes. The open questions reading code cannot settle are listed in §3 instead of being guessed at.

---

## 0. Summary

### 0.1 Verdict

| Piece | Feasible on today's design? | What is actually missing |
|---|---|---|
| **A. 04:00 business-day cutoff** | **Yes — the authority already exists** (`BillSyncService.applyServerBusinessDate` overwrites the device's date on every push). | The rule itself, plus **13** call sites that compute "today" independently and two duplicate-detection keys that must move together. No new column on `bills`. |
| **B. Cashier shifts** | **Yes — needs a new synced entity.** | Table + entity + sync DTO + a terminal-state guard (LWW must not reopen a closed shift). No existing seam beyond the standard sync contract. |
| **C. Durable closing email** | **Yes — the pattern is already in production** (`WebhookRetryJob` / `WebhookRetryService`). | Outbox table, worker, template, and an enqueue inside the shift-commit transaction. |

### 0.2 Four corrections to the reviewed RFC

| RFC said | Reality **[verified]** | Consequence |
|---|---|---|
| "Flyway `V116__shift_lifecycle_and_cutoff_engine.sql`" | `V116`, `V117`, `V118` all exist; head is `V118__users_last_seen.sql`. | A V116 file fails deploy (`spring.flyway.out-of-order=false`, `application.properties:58`). This plan uses **`V119`**. |
| "`ALTER TABLE bills ADD COLUMN business_date`" | The business date is **`bills.last_reset_date`** (`VARCHAR(50) NOT NULL`, `V1__init_schema.sql:168`); `V97__backfill_bill_business_date_ist.sql` exists specifically to correct it. | Adding `business_date` creates a second source of truth for the value one migration just reconciled. **No bills column is added.** |
| "`device_created_at` / `server_received_at` / `clock_skew_ms`, flag if >15 min" | `BaseSyncEntity.createdAt` (device) and `serverUpdatedAt` (server) already exist; skew **>300 000 ms is rejected**, not flagged (`GenericSyncService:646`, `sync.clock-skew.max-ms`, audit `CLOCK_SKEW_REJECTED`). | 15 minutes would be looser than the rule in production. **No new timestamp columns.** |
| "Extend `ux_bills_restaurant_terminal_daily_active` to cover business_date" | That index already covers it — it is `(restaurant_id, last_reset_date, terminal_series, daily_order_id) WHERE is_deleted = false` (`V26:101`). | Rebuilding a partial unique index on the hottest write table buys nothing. **Leave it alone**; it also serves report queries by prefix. |

### 0.3 Build order (differs from the RFC)

```
P0  Shared business-date rule + unit tests            (no behaviour change)
P1  Schema: V119 + Room 84                            (no behaviour change)
P2  Cutoff engine — server rule, all client call sites, report windows   ← atomic release
P3  Cashier shifts (depends on P2 being live, not merely merged)
P4  Transactional email outbox (independent — may ship before P2)
P5  Web admin + POS UI
P6  Docs and decision records
```

The critical ordering constraint: **P2 must ship server and client together, gated per restaurant** (§8). A server-only cutoff renumbers receipts printed by an old client.

---

## 1. Ground truth

### 1.1 The business date already has a name

- `bills.last_reset_date VARCHAR(50) NOT NULL` — the IST calendar day of `created_at` **[verified]** (`V1:168`, `Bill.java:lastResetDate`).
- `restaurantprofiles.last_reset_date` (string) and `last_reset_date_proper DATE NOT NULL DEFAULT CURRENT_DATE` **[verified]** (`V11`), kept in sync by `RestaurantProfile.syncDates()`.
- `BillSyncService.applyServerBusinessDate` derives it from `createdAt` in the restaurant's `timezone` and **overwrites the device's value unconditionally**; called from `GenericSyncService:517` for every pushed bill **[verified]**. The method's javadoc records why one side must decide: the client's local duplicate guard used a `createdAt` window while the server's guard keys on the string, and two bases for one fact drift around midnight.
- `RestaurantProfileRepository` also resets a counter against `last_reset_date_proper` (`:113–119`).

**So the cutoff is a change to one derivation, not a new field.** What makes it large is §5.

### 1.2 There are two counters, and only one is used for billing

| Counter | Key | Written by | Consumed by |
|---|---|---|---|
| **Terminal counter** | `(restaurant_id, terminal_id, date)` — `TerminalDailyCounterEntity` | `RestaurantDao.incrementAndGetTerminalDailyCounter:173` (`MAX` backfill → `raiseAtLeast` → single UPSERT) **[verified]** | `BillingViewModel:664, 896, 1122` — the real billing path **[verified]** |
| **Profile counter** | `restaurantprofiles.daily_order_counter` + `last_reset_date` | `RestaurantDao.incrementAndGetCounters:44`, `incrementAndGetDailyCounter:66`, `updateCounters:91` **[verified]** | `OrderIdManager.isResetNeeded/getNextDailyCounter`, legacy Sync Center repair **[verified]** |

Both compute their own `LocalDate.now(zone)` internally. This duplication is a pre-existing hazard; the cutoff makes it a *date-shaped* divergence instead of an off-by-a-few-orders one, which is worse because it never self-heals (§5.3③).

### 1.3 Reports do not read the business date

`AnalyticsController` builds `[day.atStartOfDay(ZONE), day.plusDays(1)…)` in a hardcoded `Asia/Kolkata` (`:38`, `:99`, `:184`, `:316–318`) and five `BillRepository` queries filter `createdAt BETWEEN :from AND :to` **[verified]**. The same calendar-window construction appears in `BusinessReadService:83–98, 174–175, 445–448`, `AdminReportsController:65`, `BillEditDigestService:77–78`, `PaymentMetricsService:34, 165`, `EasebuzzReconciliationService:345, 389` **[verified]**.

Consequence: if the cutoff changes only the writer, nightly sales between 00:00 and 04:00 are stored under the previous business date but *queried* under the current calendar date. Reports would get **worse**, not better.

### 1.4 Closing is a browser form

`daily-closing-page.component.ts` keeps counted cash in `localStorage` (`khanabook:daily-closing-cash:{restaurantId}:{date}`, `:931–947`) and `closeBusinessDay()` only calls `exportClosing()` (CSV) and sets a signal **[verified]**. The UI states it plainly: *"Saved in this browser only; it has not been submitted as a close record."* Expected cash comes from `GET /analytics/daily-closing` (`expectedCash` = cash-mode totals).

### 1.5 Email has no durability — but the repo has the pattern

`EmailNotificationService` has four `@Async` methods, no named executor, and `try { send } catch (log.error)` **[verified]**. `khanabook.email.enabled` defaults **false** **[verified]**; templates live in `templates/email/*.html`.

The durable pattern already exists: `WebhookRetryJob` + `WebhookRetryService` — `PENDING`, `attempt_count`, `max_attempts`, `next_attempt_at`, `last_error`, `DEAD_LETTER`, exponential backoff (`BACKOFF_MS`), `@Scheduled(fixedDelay = 30000)` (`:102`), and admin replay endpoints **[verified]**. Reuse the shape, not the table.

### 1.6 Sync contract constraints any new entity inherits

- Push records carry `localId`, `deviceId`, `restaurantId`, `updatedAt`, `isSynced`, `serverUpdatedAt`, `isDeleted`, `version` (`BaseSyncEntity`) **[verified]**, and are unique on `(restaurant_id, device_id, local_id)` for bills **[verified]**.
- `SyncDtoContractTest` reflects over every Room entity and **fails the build** if a field is missing from its sync DTO, unless it is registered in `localOnlyFields` with a reason **[verified]**. A new entity therefore forces a DTO, a mapper, and a pull mapping.
- `GenericSyncService` rejects clock skew, protects finalized bill state (`protectBillState`), and merges the profile counter by comparing dates (`mergeCounterState`) **[verified]**.

---

## 2. Domain model

```
BUSINESS DAY D  =  [D 04:00, D+1 04:00)   in the restaurant's timezone
   │
   ├── SHIFT 1  11:00 → 16:00   cashier Suresh   drawer ₹6 500   snapshot v1
   ├── SHIFT 2  19:00 → 01:30   cashier Ramesh   drawer ₹14 200  snapshot v2
   │
   └── at 04:00 (cutoff) the day is sealed; at 04:30 a consolidated report is queued.
```

| Concept | Owner | Identity | Never changes |
|---|---|---|---|
| Business day | Server rule, mirrored on device | `bills.last_reset_date` (`YYYY-MM-DD`) | Once assigned to a bill |
| Cashier shift | Device (offline-first), synced | `(restaurant_id, terminal_id, opened_at)` + `local_id` | A closed shift's `declared_cash`, `expected_cash`, `variance`, `snapshot_json` |
| Order number | Terminal counter | `(restaurant, terminal_series, business_date, daily_order_id)` | The printed number, once printed |
| Invoice number | Terminal invoice series | `(restaurant, financial_year, invoice_series, invoice_sequence)` | Always (tax-legal) |

The printed receipt stays authoritative. Where the existing repair path (`BillSyncService.validateBillNumberConflicts`) must renumber a conflicting daily order, that is a pre-existing, logged behaviour — §8 explains why rollout ordering makes it rare rather than nightly.

---

## 3. Decisions (needed before P1 is written)

| # | Question | Recommendation | Why |
|---|---|---|---|
| **D1** | Retroactive or forward-only cutoff? | **Forward-only.** Store `cutoff_effective_from BIGINT`, but **read only its date**: that date is the first business day under the new rule, and every earlier day keeps the calendar rule. Comparing instants instead is not equivalent — an activation between midnight and the cutoff would put the same four hours into two different days' windows. | Retroactive re-bucketing can move a bill onto an occupied `(restaurant, date, series, order)` slot. `V97` already refused to force that ("choosing a new order number for a bill that already exists on a printed receipt is a business decision, not a migration's call"). |
| **D2** | The switch day is 28 hours long. | **Accept it**, and name the right day: the day **before** the effective date runs 00:00 → 04:00 the next morning (28 h); every day from the effective date runs cutoff → cutoff. That one long day absorbs exactly the four hours the calendar rule would have given to the following date, so no hours are counted twice and none are lost. | Any alternative (splitting the overlap) makes two reports both claim the same four hours. |
| **D3** | Late offline shift after the EOD email | **Fire at cutoff+30 min, then re-send a revision** when a late shift syncs. Outbox key includes a `revision`. | The RFC's `UNIQUE (reference_id, recipient)` would block the correction permanently. |
| **D4** | `email_daily_closing_report` default | **FALSE.** Opt-in per restaurant. | `khanabook.email.enabled` is false today; emailing financials by default on day one is a support-ticket generator. |
| **D5** | Where is a shift closed? | **On the device**, then synced. The email is enqueued when the shift row **commits on the server**. | A cashier at 23:30 with no network must still close. This is the one deliberate deviation from the RFC's "closing transaction inserts the outbox row" — the transaction is the sync-apply one, which is still atomic. |
| **D6** | How is `expected_cash` derived? | **Window-based:** cash-mode `BillPayment` rows for bills whose `created_terminal_id` is this terminal and `created_at ∈ [opened_at, closed_at)`, `is_deleted = false`. | No new `bills` column. Trade-off: a bill opened in shift 1 and settled in shift 2 counts to shift 2 — the operational convention, and it matches how the drawer sees it. Recompute **server-side** at seal; never trust the device. |

---

## 4. Schema

### 4.1 Flyway `V119__business_day_cutoff_and_cashier_shifts.sql`

```sql
-- 1. Business-day configuration. Defaults preserve today's behaviour exactly:
--    a 00:00 cutoff plus a NULL effective instant = the legacy calendar-day rule.
ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS opening_time TIME NOT NULL DEFAULT '08:00:00',
    ADD COLUMN IF NOT EXISTS closing_time TIME NOT NULL DEFAULT '23:00:00',
    ADD COLUMN IF NOT EXISTS business_day_cutoff_time TIME NOT NULL DEFAULT '00:00:00',
    ADD COLUMN IF NOT EXISTS cutoff_effective_from BIGINT,
    ADD COLUMN IF NOT EXISTS email_daily_closing_report BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS daily_closing_email_recipients TEXT;

COMMENT ON COLUMN restaurantprofiles.business_day_cutoff_time IS
    'Local time at which the business day rolls. 00:00 keeps the legacy calendar-day rule.';
COMMENT ON COLUMN restaurantprofiles.cutoff_effective_from IS
    'Epoch ms whose DATE in the profile timezone is the first business day under the new '
    'rule; NULL = never. Forward-only: earlier days keep the calendar rule, so no stored '
    'business date is rewritten. The time of day is not read: keying on the instant would '
    'let an activation between midnight and the cutoff put the same hours in two windows.';

-- 2. Per-terminal capability gate (see section 8).
ALTER TABLE restaurant_terminal
    ADD COLUMN IF NOT EXISTS cutoff_capable BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Cashier shifts: device-authored, server-sealed, append-only after CLOSED.
CREATE TABLE IF NOT EXISTS cashier_shifts (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    local_id            BIGINT       NOT NULL,
    device_id           VARCHAR(255) NOT NULL,
    restaurant_id       BIGINT       NOT NULL,
    terminal_id         VARCHAR(255) NOT NULL,
    terminal_series     VARCHAR(255),
    cashier_user_id     BIGINT,
    cashier_name        VARCHAR(255),
    business_date       DATE         NOT NULL,
    opened_at           BIGINT       NOT NULL,
    closed_at           BIGINT,
    opening_float       NUMERIC(12,2) NOT NULL DEFAULT 0,
    declared_cash       NUMERIC(12,2),
    expected_cash       NUMERIC(12,2),
    cash_variance       NUMERIC(12,2),
    total_revenue       NUMERIC(12,2),
    total_orders        INT,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    close_source        VARCHAR(30),
    snapshot_json       TEXT,
    public_token        UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at          BIGINT,
    updated_at          BIGINT,
    server_updated_at   BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_cashier_shifts_device_local UNIQUE (restaurant_id, device_id, local_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cashier_shifts_public_token
    ON cashier_shifts (public_token);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cashier_shifts_one_active_per_terminal
    ON cashier_shifts (restaurant_id, terminal_id)
    WHERE status = 'ACTIVE' AND is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_cashier_shifts_restaurant_date
    ON cashier_shifts (restaurant_id, business_date);

-- 4. Transactional email outbox (same shape as webhook_retry_job).
CREATE TABLE IF NOT EXISTS email_outbox (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL,
    report_type     VARCHAR(50)  NOT NULL,   -- SHIFT_CLOSE | DAILY_EOD
    reference_key   VARCHAR(255) NOT NULL,   -- shift public_token | business date
    revision        INT          NOT NULL DEFAULT 1,
    recipient       TEXT         NOT NULL,
    subject         TEXT         NOT NULL,
    html_body       TEXT         NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 5,
    next_attempt_at BIGINT,
    last_error      TEXT,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    sent_at         BIGINT,
    CONSTRAINT uq_email_outbox_revision
        UNIQUE (report_type, reference_key, revision, recipient)
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_pending
    ON email_outbox (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');
```

**No `bills` change.** The existing `ux_bills_restaurant_terminal_daily_active` partial index already provides `(restaurant_id, last_reset_date)` as a prefix for report queries on active rows.

### 4.2 Room `83 → 84` (`AppDatabase.kt`)

```kotlin
val MIGRATION_83_84 = object : Migration(83, 84) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Guarded ALTERs: an out-of-band column aborted the v39→40 chain (MIGRATION_39_40).
        if (!db.hasColumn("restaurant_profile", "opening_time")) {
            db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `opening_time` TEXT NOT NULL DEFAULT '08:00'")
        }
        if (!db.hasColumn("restaurant_profile", "closing_time")) {
            db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `closing_time` TEXT NOT NULL DEFAULT '23:00'")
        }
        if (!db.hasColumn("restaurant_profile", "business_day_cutoff_time")) {
            db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `business_day_cutoff_time` TEXT NOT NULL DEFAULT '00:00'")
        }
        if (!db.hasColumn("restaurant_profile", "cutoff_effective_from")) {
            db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `cutoff_effective_from` INTEGER")
        }
        if (!db.hasColumn("restaurant_profile", "email_daily_closing_report")) {
            db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `email_daily_closing_report` INTEGER NOT NULL DEFAULT 0")
        }

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `local_cashier_shifts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `restaurant_id` INTEGER NOT NULL,
                `local_id` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                `terminal_id` TEXT NOT NULL,
                `terminal_series` TEXT,
                `cashier_user_id` INTEGER,
                `cashier_name` TEXT,
                `business_date` TEXT NOT NULL,
                `opened_at` INTEGER NOT NULL,
                `closed_at` INTEGER,
                `opening_float` TEXT NOT NULL DEFAULT '0.00',
                `declared_cash` TEXT,
                `expected_cash` TEXT,
                `cash_variance` TEXT,
                `total_revenue` TEXT,
                `total_orders` INTEGER,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                `close_source` TEXT,
                `snapshot_json` TEXT,
                `public_token` TEXT NOT NULL,
                `server_id` INTEGER,
                `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `updated_at` INTEGER NOT NULL DEFAULT 0,
                `is_deleted` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_cashier_shifts_public_token` ON `local_cashier_shifts` (`public_token`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_cashier_shifts_active` ON `local_cashier_shifts` (`restaurant_id`, `terminal_id`) WHERE `status` = 'ACTIVE'")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_cashier_shifts_date` ON `local_cashier_shifts` (`restaurant_id`, `business_date`)")
    }
}
```

Registration (all three are required, in this order):

1. `version = 84`
2. `CashierShiftEntity::class` in `@Database(entities = [...])` + `abstract fun cashierShiftDao(): CashierShiftDao`
3. `MIGRATION_83_84` appended to `ALL_MIGRATIONS` (`:80–99`). `DatabaseProvider:203` and `DatabaseModule` read only that array.

Money columns are `TEXT` on Android and `NUMERIC(12,2)` on PostgreSQL, matching the existing `bills` convention.

---

## 5. The cutoff engine

### 5.1 One rule, two implementations

```kotlin
// Android: core/util/BusinessDate.kt
fun businessDate(createdAtMillis: Long, profile: RestaurantProfileEntity): String
fun cutoffInstant(date: LocalDate, profile: RestaurantProfileEntity): Long   // window start
```

```java
// Server: core/utility/BusinessDay.java
String businessDate(long createdAtMillis, RestaurantProfile profile);
long windowStart(LocalDate day, RestaurantProfile profile);
```

Rule:

```
if (cutoff == 00:00 && effectiveFrom == null)          -> LocalDate(createdAt in tz)          // legacy, bit-identical
if (effectiveFrom != null && createdAt < effectiveFrom) -> LocalDate(createdAt in tz)          // pre-activation bills
z = createdAt in tz
businessDate = if (z.localTime() < cutoff) z.toLocalDate().minusDays(1) else z.toLocalDate()
```

Both implementations must be tested against the same fixture table (§10) or they will disagree at the boundary — which is exactly the failure `applyServerBusinessDate` was written to end.

### 5.2 The 13 call sites

| # | Site | Today | Required change |
|---|---|---|---|
| 1 | `BillingViewModel:664, 896, 1122` | `LocalDate.now(zoneId)` → counter + `bill.lastResetDate` | All three call `BusinessDate.businessDate(...)` from one profile read. Changing one leaves the other two on calendar dates. |
| 2 | `OrderIdManager:42–43` | Hardcodes IST; ignores its `timezone` parameter | Delegate to `BusinessDate`; keep the old signature for callers that have no profile. |
| 3 | `RestaurantDao:45–47, 70–72` | `LocalDate.now()` inside both counter transactions | Take `today: String` as a parameter; callers pass the business date. |
| 4 | `RestaurantDao:91–100` (`updateCounters`) | Sets `lastResetDate = today` on every server counter pull | Take the date; never derive it locally. |
| 5 | `MasterSyncProcessor:426–434` | Calendar window → `getMaxDailyOrderIdForTerminalToday(...)` → raises `date = today` | Seed **per distinct `last_reset_date`** in local bills (see §5.3①). |
| 6 | `SettingsViewModel:484–487, 731–733` | `.atStartOfDay` / `.plusDays(1)` on calendar today | Same per-business-date query. |
| 7 | `BillDao:460–467` (`insertFullBill`) | Duplicate guard counts `created_at BETWEEN [calendar start, end)` | Count `last_reset_date = :date` (+ terminal series). See §5.3②. |
| 8 | `BillDao:886, 896` | `getMaxDailyOrderIdBetween`, `getMaxDailyOrderIdForTerminalToday` — `created_at` windows | Add date-keyed variants; keep the old ones until callers are gone. |
| 9 | `BillSyncService:41–60` | Calendar day in profile tz, overwrites device | Apply `BusinessDay.businessDate`. Server remains the only authority. |
| 10 | `BillServiceImpl.allocateMissingInvoiceNumbers` | `createdAt` → financial year | **Keep it** — the invoice date is legally the issue date. Log when the invoice FY and the business date disagree (at most one day a year, around 1 April). |
| 11 | `AnalyticsController:99, 184, 316` + the 5 `BillRepository` range queries | `created_at` calendar window | Build the window with `BusinessDay.windowStart`/`+1 day`. Queries stay untouched; only the boundaries move. |
| 12 | `BusinessReadService:83–98, 174–175, 445–448`; `AdminReportsController:65`; `BillEditDigestService:77–78`; `PaymentMetricsService:34, 165`; `EasebuzzReconciliationService:345, 389` | Calendar windows | Convert with the same helper **or** explicitly record them as calendar-day surfaces in §12. Dashboards that disagree with the till are a support burden; convert the ones on the daily-closing path first. |
| 13 | `MasterSyncProcessor:1096–1125`; `SettingsViewModel:600–635` | `correctedDate` from `createdAt`'s calendar day, then `if (correctedDate == today) raise(...)` | Compute the business date; compare **business** dates. See §5.3④. |

`mergeCounterState` (`GenericSyncService:1295–1330`) needs no code change **provided** every client applies the same rule. Two clients on different rules make it silently discard one side's counter (§5.3③) — this is the reason for the rollout gate in §8, not a reason to touch it.

### 5.3 The four bugs the cutoff introduces if it is built naively

**① Counter seeding raises the wrong row.**
A bill created 01:30 has `last_reset_date = D` but `created_at = D+1 01:30`. `reconcileLocalBillScope` builds `[D+1 00:00, D+2 00:00)` and raises `date = D+1`. It seeds the day **after** the bills it counted. The next 02:00 bill allocates from D's stale row, collides, and is renumbered. Fix: `SELECT last_reset_date, MAX(daily_order_id) … GROUP BY last_reset_date` and raise each row.

**② The local guard and the server stop using the same key.**
Client: `created_at` window. Server: `(restaurant_id, last_reset_date, terminal_series, daily_order_id)` with a partial unique index. Today they agree only by coincidence of derivation. After the cutoff they disagree for four hours a night: the local guard looks in the wrong bucket, passes, and the server rejects or renumbers. Fix: key the local guard on `last_reset_date` — the same triple the server uses.

**③ Profile-counter merge loses a day silently.**
`mergeCounterState` compares `last_reset_date` strings: older loses, equal takes max. A mixed fleet has one device saying D and another D+1 for the same instant; the "later" counter wins and the other is discarded with no log. Fix: prevent a mixed fleet (terminal capability gate, §8). Optionally retire the profile-counter path for billing afterwards — it duplicates the terminal counter.

**④ Auto-heal goes silent.**
`quarantineFailedBills` and both repair paths gate the counter raise on `correctedDate == today`. At 02:00 the real business date is yesterday, so the gate is false, the counter is never raised, and the next bill collides again — reopening the renumbering loop `validateBillNumberConflicts` was built to close. Fix: compare business dates.

---

## 6. Cashier shifts

### 6.1 States

```
ACTIVE ──close (cashier declares cash)──► CLOSED
ACTIVE ──cutoff reached with no close──► AUTO_SEALED   (server, declared_cash = NULL)
CLOSED / AUTO_SEALED ──never──► ACTIVE
```

`close_source` records `CASHIER` or `CUTOFF_CRON`. A sealed shift is immutable except for server-computed totals.

### 6.2 Sync contract

- Push/pull through the generic engine: new entity, DTO in `SyncRequestDtos.kt`, mapper in `SyncEntityMappers.kt`, pull mapping in `MasterSyncProcessor`, and an entry in `SyncDtoContractTest`'s `localOnlyFields` only for fields the server must never see.
- Unique `(restaurant_id, device_id, local_id)` plus `public_token` gives idempotent close on retry — same pattern as `BillSyncService.attemptIdempotentRecovery`.
- **Append-only guard:** on push of an existing `CLOSED`/`AUTO_SEALED` row, the server restores its sealed fields and refuses a status regression (mirror of `protectBillState`). Without this, LWW reopens closed drawers.
- A second `ACTIVE` row for the same terminal is rejected per record (`failedReasons`) rather than relying on the partial index, so the client sees an actionable message and closes the previous shift.

### 6.3 Declared vs expected

- `declared_cash` — device truth, entered by the cashier, never recomputed.
- `expected_cash`, `cash_variance`, `total_*` — **server-computed** at seal (§3 D6). A device that can set `expected_cash` can report zero variance forever.
- `snapshot_json` stores the rendered report so a reprint is identical even if bills are edited later.

### 6.4 API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/shifts/active` | Current open shift for the authenticated terminal |
| `POST` | `/api/v1/shifts/{publicToken}/close` | Online close (web admin / connected POS); computes variance, enqueues email |
| `GET` | `/api/v1/shifts?date=YYYY-MM-DD` | Shift list for the closing page |
| `GET` | `/api/v1/reports/business-day?date=YYYY-MM-DD` | Consolidated day (all shifts) |

Offline closes arrive through sync, and `ShiftSyncService` runs the same seal logic in the push transaction.

---

## 7. Email outbox

- Enqueue **inside the transaction that commits the shift row** (online close or sync apply). `@Async` is removed from this path; the worker is already a background thread.
- Worker: `@Scheduled(fixedDelay = 30_000, zone = …)` — copy `WebhookRetryService`: `PENDING`/`FAILED` with `next_attempt_at <= now`, exponential backoff, `DEAD_LETTER`, `last_error`, ALERT log.
- Gate on `khanabook.email.enabled`; when false, do not enqueue (no rows accumulate).
- `EmailNotificationService.sendHtmlEmail` is private — expose a package-visible `sendPreRendered(String to, String subject, String html)`.
- `DAILY_EOD` revision: `revision = 1` at cutoff+30 min; if a late shift lands afterwards, enqueue `revision = 2` with the corrected totals (the unique key permits it).
- Cron must specify `zone = "Asia/Kolkata"`; `AsyncConfig.taskScheduler` has **pool size 2**, so the EOD render must be short or moved to its own scheduler.
- Template: `templates/email/daily-closing-report.html` (Thymeleaf, same conventions as the four existing templates).

---

## 8. Rollout gate (non-negotiable)

A server-only cutoff renumbers receipts printed by an old client: the device prints `01`, the server rewrites the bill into the previous business day, finds a collision, and repairs it to `43`. That is the existing, intentional repair behaviour — it must simply not be reachable at 01:00 on the night of a release.

1. Terminal registration records capability: `restaurant_terminal.cutoff_capable = TRUE` when the client presents an `X-App-Version` at or above the cutoff build (`AppVersionFilter` already receives the header).
2. The owner sets `cutoff_effective_from` in web admin. The UI refuses until **every active terminal** is capable, and shows which ones are not (`restaurant_terminal.last_seen_at` already exists).
3. The server applies the cutoff only when `cutoff_effective_from != null`. Until then the rule is bit-identical to today — `00:00` default, NULL instant.
4. `AppVersionFilter` gains a request attribute so terminal registration can read the version; no business logic depends on the header directly.

---

## 9. File-by-file change list

### P0 — shared rule, no behaviour change

| File | Change |
|---|---|
| `apps/android/app/src/main/java/com/khanabook/lite/pos/core/util/BusinessDate.kt` | **New.** Rule + window helpers, pure functions, no I/O. |
| `apps/android/app/src/test/java/com/khanabook/lite/pos/core/util/BusinessDateTest.kt` | **New.** Boundary table (§10). |
| `services/server/src/main/java/com/khanabook/saas/core/utility/BusinessDay.java` | **New.** Java twin of the same rule. |
| `services/server/src/test/java/com/khanabook/saas/core/utility/BusinessDayTest.java` | **New.** Same fixture table as the Kotlin test. |

### P1 — schema only

| File | Change |
|---|---|
| `services/server/src/main/resources/db/migration/V119__business_day_cutoff_and_cashier_shifts.sql` | **New.** §4.1. |
| `services/server/src/main/java/com/khanabook/saas/feature/restaurants/data/RestaurantProfile.java` | Add `openingTime`, `closingTime`, `businessDayCutoffTime` (`LocalTime`), `cutoffEffectiveFrom` (`Long`), `emailDailyClosingReport` (`Boolean`), `dailyClosingEmailRecipients`. |
| `services/server/src/main/java/com/khanabook/saas/feature/restaurants/data/RestaurantTerminal.java` | Add `cutoffCapable`. |
| `apps/android/.../feature/auth/data/RestaurantProfileEntity.kt` | Mirror the six fields (times as `String` `HH:mm`). |
| `apps/android/.../feature/billing/data/CashierShiftEntity.kt` | **New.** Mirrors §4.2. |
| `apps/android/.../feature/billing/data/CashierShiftDao.kt` | **New.** Active-shift query, close, unsynced list, mark synced. |
| `apps/android/.../core/database/AppDatabase.kt` | `version = 84`, `MIGRATION_83_84`, entity + DAO registration, `ALL_MIGRATIONS`. |
| `apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json` | **Generated** by the build (`exportSchema = true`); must be committed. |
| `apps/android/app/src/androidTest/.../data/local/MigrationChainSweepTest.kt` | Add `81, 82, 83` to `startable`; the sweep stops at 80 today, so 83→84 would never be validated from its own start version. |
| `apps/android/app/src/test/java/.../data/local/DatabaseMigrationListConsistencyTest.kt` | No change — it derives the top version from the chain. Verify it passes. |

### P2 — cutoff engine (one release, gated)

| File | Change |
|---|---|
| `services/server/.../feature/billing/service/BillSyncService.java` | `applyServerBusinessDate` → `BusinessDay.businessDate`. Update the javadoc: the rule now includes the cutoff. |
| `services/server/.../feature/reports/controller/AnalyticsController.java` | `:99`, `:184`, `:316–318` → `BusinessDay` window. |
| `services/server/.../feature/business/service/BusinessReadService.java` | `:83–98`, `:174–175`, `:445–448` → same helper (daily-closing path first). |
| `services/server/.../feature/reports/controller/AdminReportsController.java` | `:65` → same helper, or record as calendar-only in §12. |
| `services/server/.../feature/billing/service/BillEditDigestService.java` | `:77–78` → same helper (the digest describes a business day). |
| `services/server/.../feature/billing/service/BillServiceImpl.java` | `allocateMissingInvoiceNumbers`: keep `createdAt`, add a comment + debug log when invoice FY ≠ business date. |
| `services/server/.../feature/restaurants/data/RestaurantProfileDTO.java` | Carry the new fields for the web-admin write path. |
| `services/server/.../feature/sync/service/GenericSyncService.java` | `preserveServerOwnedState`: restore the new fields when the pushing DTO omits them, so a legacy whole-record push cannot blank the cutoff. Also name them in the profile mask path. `mergeCounterState`: review only (no change under §8). |
| `services/server/.../core/security/AppVersionFilter.java` | Put `appVersion` on the request as an attribute for terminal registration. |
| `apps/android/.../feature/billing/domain/OrderIdManager.kt` | `getTodayString`/`isResetNeeded`/`getNextDailyCounter` delegate to `BusinessDate`. |
| `apps/android/.../feature/billing/viewmodel/BillingViewModel.kt` | `:664`, `:896`, `:1122` — one `BusinessDate` call each; the profile is already loaded in scope. |
| `apps/android/.../feature/auth/data/RestaurantDao.kt` | `:45–47`, `:70–72`, `:91–100` — accept the date as a parameter. |
| `apps/android/.../feature/auth/data/RestaurantRepository.kt` | Thread the date through `incrementAndGetTerminalDailyCounter`, `incrementAndGetDailyCounter`, `updateCounters`. |
| `apps/android/.../feature/billing/data/BillDao.kt` | `insertFullBill:460–467` guard on `last_reset_date`; add date-keyed `getMaxDailyOrderId*` variants. |
| `apps/android/.../feature/sync/domain/MasterSyncProcessor.kt` | `:426–434` per-business-date seeding; `:1096–1125` repair date. |
| `apps/android/.../feature/settings/viewmodel/SettingsViewModel.kt` | `:484–487`, `:600–635`, `:731–733` — business-date windows and comparisons. |
| `services/server/src/test/java/.../sync/service/BillSyncServiceBusinessDateTest.java` | Extend: 01:30 → D, 04:01 → D+1, pre-activation → calendar, `00:00` → unchanged. |
| `apps/android/app/src/test/java/.../OrderIdManagerTest.kt` | Add cutoff cases. |

### P3 — shifts

| File | Change |
|---|---|
| `services/server/.../feature/shifts/data/CashierShift.java` | **New.** `BaseSyncEntity`, fields per §4.1. |
| `services/server/.../feature/shifts/data/CashierShiftRepository.java` | **New.** `findByRestaurantIdAndTerminalIdAndStatus`, `findByIdForUpdate`, window aggregates. |
| `services/server/.../feature/shifts/service/CashierShiftService.java` | **New.** Seal logic, variance from cash rows, append-only guard, snapshot render → outbox enqueue. |
| `services/server/.../feature/shifts/controller/ShiftController.java` | **New.** §6.4 endpoints, `@PreAuthorize` aligned with the daily-closing permission. |
| `services/server/.../feature/sync/service/GenericSyncService.java` | Dispatch `CashierShift` on push/pull; call the seal path inside the push transaction. |
| `apps/android/.../feature/billing/domain/ShiftManager.kt` | **New.** Open/close locally, declare cash, enqueue sync. |
| `apps/android/.../feature/billing/ui/shift/…` | **New.** Close-shift sheet (blind count, variance preview, print). |
| `apps/android/.../feature/sync/data/SyncRequestDtos.kt` | **New** `CashierShiftSyncDto`. |
| `apps/android/.../feature/sync/data/SyncEntityMappers.kt` | Map entity ↔ DTO. |
| `apps/android/.../feature/sync/domain/MasterSyncProcessor.kt` | Pull mapping for shifts. |
| `apps/android/app/src/test/java/.../feature/sync/SyncDtoContractTest.kt` | Satisfied by the DTO; register `serverId`/`isSynced` style bookkeeping only if §1.6 applies. |
| `apps/android/app/src/test/java/.../feature/billing/domain/ShiftManagerTest.kt` | **New.** Close computes variance, idempotent re-close, never reopens. |

### P4 — outbox

| File | Change |
|---|---|
| `services/server/.../feature/notifications/data/EmailOutboxEntry.java` | **New.** §4.1 shape. |
| `services/server/.../feature/notifications/data/EmailOutboxRepository.java` | **New.** `findByStatusAndNextAttemptAtLessThanEqual`, `countByStatus`. |
| `services/server/.../feature/notifications/service/EmailOutboxWorker.java` | **New.** `@Scheduled(fixedDelay = 30_000)`, backoff, dead letter, gate on `emailEnabled`. |
| `services/server/.../feature/notifications/service/DailyClosingReportService.java` | **New.** Build the model from shift + bill data, render Thymeleaf, enqueue. |
| `services/server/.../feature/notifications/service/EmailNotificationService.java` | Expose `sendPreRendered(...)`; leave the four `@Async` senders untouched. |
| `services/server/src/main/resources/templates/email/daily-closing-report.html` | **New.** |
| `services/server/src/main/resources/application.properties` | Document the new keys next to the existing `khanabook.email.enabled`. |
| `services/server/.../feature/notifications/service/EmailOutboxWorkerTest.java` | **New.** Retry, dead letter, disabled flag. |

### P5 — UI

| File | Change |
|---|---|
| `apps/web-admin/.../pages/business-settings/business-day-section.component.ts` | **New.** Opening/closing times, cutoff, effective-from, email toggle + recipients, capability warning list. |
| `apps/web-admin/.../pages/business-settings/business-settings-page.component.ts` | Add the section + nav entry; include fields in `saveProfile()`. |
| `apps/web-admin/.../pages/business-settings/business-settings-page.component.spec.ts` | Update the "lists exactly the sections that exist, in order" assertion. |
| `apps/web-admin/.../core/services/business-api.service.ts` | `updateProfile` payload (`:318`). |
| `apps/web-admin/.../core/models/api.models.ts` | Profile model fields. |
| `apps/web-admin/.../pages/daily-closing/daily-closing-page.component.ts` | Replace the `localStorage` count (`:931–947`) with `POST /shifts/{token}/close`; variance from the response; remove the "this browser only" copy; show the shift list. |
| `apps/web-admin/.../pages/daily-closing/daily-closing-page.component.spec.ts` | Rewrite the cash-count specs against the API. |
| `apps/android/.../feature/settings/…` | Display-only business-day card (owner edits in web admin; the POS shows what the server sent). |

### P6 — docs

| File | Change |
|---|---|
| `docs/README.md` | Add this file to the `planning/` row. |
| `docs/product/COMPETITORS.md:127` | Close the "no cutoff field" gap with a pointer here. |
| `docs/qa/DISTRIBUTED_STATE_PRODUCT_DECISIONS.md` | Decision 4: record the chosen rule (cutoff, forward-only) and where it is enforced. |
| `docs/engineering/SYNC_CONTRACT.md` | New entity + the append-only shift rule. |
| `docs/qa/v97-backfill-dry-run.sql` | Add a guard comment: its predicate assumes the calendar rule and must not be re-run once a cutoff is active. **Do not edit `V97__…sql` itself** — Flyway checksums applied migrations. |
| `docs/api/API_REFERENCE.md` | The four shift endpoints. |

---

## 10. Test plan

Boundary fixture table, run against **both** `BusinessDate.kt` and `BusinessDay.java`:

| Case | Cutoff | Effective from | Input (IST) | Expected |
|---|---|---|---|---|
| legacy | 00:00 | null | 2026-11-06 01:30 | 2026-11-06 |
| pre-activation | 04:00 | 2026-11-10 00:00 | 2026-11-09 01:30 | 2026-11-09 |
| after 04:00 | 04:00 | 2026-11-10 00:00 | 2026-11-10 04:01 | 2026-11-10 |
| before 04:00 | 04:00 | 2026-11-10 00:00 | 2026-11-10 03:59 | 2026-11-09 |
| exact cutoff | 04:00 | 2026-11-10 00:00 | 2026-11-10 04:00 | 2026-11-10 |
| switch day | 04:00 | 2026-11-10 00:00 | 2026-11-10 23:00 | 2026-11-10 |

Additional:

- `BillSyncServiceBusinessDateTest` — server authority over a device that claims the wrong day (existing case) still holds under the cutoff.
- `MigrationChainSweepTest` — every start version incl. 81/82/83 migrates to 84 and validates against `84.json`.
- `DatabaseMigrationListConsistencyTest` — 83→84 present.
- `SyncDtoContractTest` — passes with the shift DTO; fails loudly if the shift entity grows a field without one.
- `ShiftManagerTest` — variance, idempotent close, closed → ACTIVE regression refused.
- `EmailOutboxWorkerTest` — SENT, retry with backoff, DEAD_LETTER, disabled flag enqueues nothing.
- Web: daily-closing specs against the API; settings section-order spec.
- Manual: one restaurant, two terminals, cutoff 04:00, bills at 23:50 / 00:10 / 04:01, verify the three business dates, the two shifts, the consolidated EOD and its revision.

---

## 11. Landmines

1. **Do not edit applied migrations** (`V1`, `V26`, `V97`) — Flyway checksums. Corrections go in `V119` and in docs.
2. **Do not add `bills.business_date`** — `last_reset_date` is the business date, and `V97` already paid for that fact.
3. **Do not rebuild `ux_bills_restaurant_terminal_daily_active`** — it already covers the date and serves report prefixes.
4. **Do not default the cutoff to 04:00.** Default `00:00` + `NULL` effective instant, so deploying V119 changes nothing.
5. **Do not ship the server rule ahead of the client.** §8's capability gate is the only thing standing between a release and renumbered receipts.
6. **Do not let LWW touch shifts.** A closed shift must be append-only on the server.
7. **Do not trust `expected_cash` from the device.**
8. The day **before** the effective date is 28 hours (midnight → next-day cutoff). Say so in the report, or the owner will read it as a revenue spike.
9. **Do not re-date a bill the server already stamped.** `applyServerBusinessDate` runs on every push, not just the first. During the activation night a bill already numbered under the calendar rule would otherwise move to the previous day, collide with an occupied `(restaurant, date, series, order)` slot, and be repaired into a new number — on a receipt that is already printed. Implemented in P1: `GenericSyncService` reads the stored row from the batch's existing-record map and passes its date to `applyServerBusinessDate`, which keeps it — and does so **before** `validateBillNumberConflicts`, which is the ordering that matters.
10. **Restore the cutoff fields on every profile push.** The till's profile DTO does not carry them and the incoming row is bound fresh, so without an explicit restore in `applyProfileChangedFieldsMerge` the next push rewrites a configured cutoff to `00:00` and nulls the effective instant with no error. Already done in P1; do not remove it when adding the web-admin form.

---

## 12. Out of scope

- Multi-outlet business days (one profile per restaurant today).
- Per-user drawer assignment beyond `cashier_user_id`.
- Nightly POS auto-close (a device can be off at 04:00; the server seals shifts it knows about).
- Converting **every** dashboard to business-day windows. P2 converts the daily-closing and orders paths; `BusinessReadService` dashboards and `PaymentMetricsService` are listed so the decision is explicit, not accidental.
- GST invoice-date semantics — invoices stay on the issue date (`createdAt`), which may differ from the business date by one day around 1 April.

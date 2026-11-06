# Business-Day Cutoff and Cashier Shifts — Implemented Requirements

**Status:** living doc, updated in place. Design rationale and the full file-by-file plan live in
`BUSINESS_DAY_CUTOFF_AND_SHIFTS_PLAN_2026-11-06.md`; this file records **what exists in the code today**,
requirement by requirement, so nobody has to reverse-engineer it from commits.

Last verified against the tree: **2026-11-06**.

---

## 1. The problem

A restaurant that trades past midnight had no way to say so. Every day boundary was the calendar day
in the shop's timezone, so:

- a bill taken at 01:00 was numbered into a fresh day while staff still considered it last night's trade,
- the day's total split in half at midnight, and
- a closing report had no honest "end of day" to run against.

The fix is a configurable **cutoff** (typically 04:00) plus **cashier shifts**, which close a drawer,
not the day.

## 2. The rule

One function, implemented twice (`apps/android/.../core/util/BusinessDate.kt`,
`services/server/.../core/utility/BusinessDay.java`), and used everywhere a day is computed:

```
D = calendar date of the instant, in the shop's timezone
if no cutoff configured (cutoff = 00:00 or effective instant is NULL): business date = D
if D < effective date of the cutoff:                                  business date = D
if local time < cutoff:                                               business date = D - 1
otherwise:                                                            business date = D
```

**Only the date of `cutoff_effective_from` is read.** Keying on the instant instead is not equivalent:
an activation between midnight and the cutoff would put the same four hours into two different days'
windows. `effectiveDate` names the first business day under the new rule.

Consequences, all deliberate:

- The 28-hour day is the day **before** the effective date (00:00 → 04:00 next morning). It absorbs
  exactly the four hours the calendar rule would have given to the following date, so no hour is
  counted twice and none is lost.
- `windowStart(D)` = midnight while `D < effectiveDate`, otherwise `D` at the cutoff.
  `windowEnd(D)` = `windowStart(D + 1)`. The two helpers are the exact inverse of the rule; a test on
  each side walks every 15 minutes of every window to prove it.
- Activation is forward-only. No stored business date is rewritten by the migration.

| Case | Cutoff | `cutoff_effective_from` | Instant | Business date |
|---|---|---|---|---|
| no cutoff | 00:00 | NULL | 2026-09-10 01:30 IST | 2026-09-10 |
| before the cutoff | 04:00 | 2026-11-10 | 2026-11-10 03:59 IST | 2026-11-09 |
| exact cutoff | 04:00 | 2026-11-10 | 2026-11-10 04:00 IST | 2026-11-10 |
| late evening | 04:00 | 2026-11-10 | 2026-11-10 23:59 IST | 2026-11-10 |
| day before activation | 04:00 | 2026-11-10 | 2026-11-09 01:30 IST | 2026-11-09 |

## 3. Requirements and status

| # | Requirement | Status |
|---|---|---|
| **R1** | A configurable cutoff decides the business date; `00:00` + no effective instant reproduces the calendar day exactly. | **Done** |
| **R2** | Activation is forward-only and keyed on the effective **date**, not the instant. | **Done** |
| **R3** | Client and server evaluate the same rule, so the local duplicate guard and the server's order key cannot disagree. | **Done** |
| **R4** | The server is the authority for a bill's business date (`bills.last_reset_date`); no `bills.business_date` column is added. | **Done** |
| **R5** | A business date the server already stamped is never moved by a later push. | **Done** |
| **R6** | Cutoff configuration is server-owned: a till push can never reset or blank it. | **Done** |
| **R7** | The cutoff cannot be activated until every active terminal is capable (`cutoff_capable`). | **Gate + writer done — no web-admin editor yet** |
| **R8** | Cashier shifts exist locally and on the server, and a closed shift is append-only. | **Schema + server guard only — nothing creates or closes a shift yet** |
| **R9** | Only `declared_cash` is device truth; `expected_cash`, variance and totals are server-owned. | **Done (guard); computation pending** |
| **R10** | Shifts sync: push/pull endpoints, and a `cashierShifts` section on the master-sync pull. | **Server only — Android neither reads nor pushes shifts** |
| **R11** | Order numbering, the duplicate guard, counter repair and seeding all use the business date. | **Done** |
| **R12** | Reports window by the business day, not midnight-to-midnight. | **Done (client summary + server analytics)** |
| **R13** | A transactional daily-closing email, queued at the cutoff. | **Not started — table only** |
| **R14** | Deploying the schema changes nothing for a shop that never sets a cutoff. | **Done** |

Read the status column strictly: **Done** means the code exists *and something calls it*. A table or an
endpoint with no producer behind it is marked as schema-only, however complete it looks.

Two consequences worth stating plainly. The **cutoff is dormant in production**: nothing sets
`cutoff_effective_from` today — there is no web-admin form — so the rule returns the calendar day for
every shop until item 1 of §10 lands. **No shift can exist yet**: the tables, entities and endpoints are
in place, but no screen opens a drawer, so `cashier_shifts` will stay empty.

## 4. Data model

**Flyway `V119__business_day_cutoff_and_cashier_shifts.sql`** (head before it was `V118`):

- `restaurantprofiles`: `opening_time`, `closing_time`, `business_day_cutoff_time` (default `00:00`),
  `cutoff_effective_from` (NULL), `email_daily_closing_report` (FALSE), `daily_closing_email_recipients`.
- `restaurant_terminal`: `cutoff_capable` (default FALSE) — recorded only; nothing enforces it yet.
- `cashier_shifts`: append-only drawer sessions, `ux_cashier_shifts_public_token`,
  `ux_cashier_shifts_one_active_per_terminal` (partial, `WHERE status = 'ACTIVE'`).
- `email_outbox`: keyed by `(restaurant, reference, revision, recipient)` so a late shift can re-send
  instead of being blocked as a duplicate.

**Room 84** (`ShiftMigrations.MIGRATION_83_84`, registered in `ALL_MIGRATIONS`):

- `local_cashier_shifts` — same shape minus `version`; the id maps to `localId` like bills.
- Three indices matching the exported schema exactly. No partial unique index: Room cannot express it,
  so "one ACTIVE per terminal" is enforced in `CashierShiftDao.openShift` instead.

`84.json` is committed. `MigrationChainSweepTest` starts from 80 and now covers 81 → 84.

## 5. Sync contract

- **Profile cutoff fields are server-owned.** They are listed in `SyncDtoContractTest` as never
  pushed, and `GenericSyncService.applyProfileChangedFieldsMerge` restores them on every push. Without
  that restore, the next till push would rewrite a configured 04:00 to `00:00` and NULL the effective
  instant — no error, the shop silently back on calendar days.
- **`CashierShiftSyncDto`** mirrors the entity field for field (the contract test fails the build
  otherwise), with `version = 0L` like `BillItemSyncDto`.
- **Forward-only stamp.** `GenericSyncService` reads the stored row from the batch's existing-record
  map and passes its date to `applyServerBusinessDate(tenantId, bill, storedBusinessDate)`. This runs
  **before** `validateBillNumberConflicts`, which keys on `last_reset_date` — recomputing first and
  repairing afterwards would renumber an already-printed receipt.
- **Append-only shifts.** `preserveServerOwnedState` restores status, `closed_at`, `declared_cash`,
  `close_source`, `snapshot_json`, `opened_at` and `business_date` on a sealed shift, and always
  restores `expected_cash`, variance and totals from the server row.

## 6. Call sites migrated to the shared rule

| File | What changed |
|---|---|
| `BillingViewModel.kt` (×3) | Bill, draft and online bill numbering |
| `RestaurantDao.kt` (×3) | Counter reset/increment transactions |
| `OrderIdManager.kt` | Today's string; also stopped ignoring its `timezone` argument |
| `BillDao.kt` | Duplicate guard now keys on `last_reset_date`, matching the server's unique key |
| `MasterSyncProcessor.kt` (×3) | Counter seeding after pull, auto-heal, and post-pull correction (which now trusts the server's `lastResetDate`) |
| `SettingsViewModel.kt` (×5) | Duplicate-ID health and the two Sync Center repair paths |
| `RestaurantRepository.kt` (×2) | Default date for terminal counter calls |
| `HomeViewModel.kt` | "Today" summary card |
| `AnalyticsController.java` | All report windows, per restaurant timezone + cutoff |
| `BillSyncService.java` | Server stamp via `BusinessDay`, plus the forward-only guard |

Deliberately still calendar-day: `UserRepository` (creates the profile on first login, when no cutoff
can exist), `TaxConfigSection` (a statutory GST period), `AdminReportsController` (cross-tenant admin
dashboard with no single shop context).

## 7. API surface

| Endpoint | Purpose |
|---|---|
| `POST /sync/shifts/push` | Batch of `CashierShiftDTO`; guarded by the billing permission |
| `GET /sync/shifts/pull` | Incremental pull by `lastSyncTimestamp` |
| `GET /sync/master/pull` | Now includes `cashierShifts` (page 0) |
| `GET /business/business-day` | Cutoff time, activation state, and every terminal's capability |
| `PUT /business/business-day` | Sets the cutoff, or deactivates it with a null effective date; refused with 409 while any active terminal is not capable |

## 8. Rollout safety

Defaults are `business_day_cutoff_time = '00:00'` and `cutoff_effective_from = NULL`, so every
replacement above returns exactly the calendar day it returned before. Deploying the migration and
the code changes client-side is therefore behaviour-neutral; the cutoff only starts to matter when an
owner sets an effective date — which is also why R7 being unimplemented is a deployment-order risk
rather than a data-loss risk today.

## 9. Verification

Run:

```bash
cd apps/android && ./gradlew :app:testDebugUnitTest \
  --tests "*BusinessDateTest*" --tests "*SyncDtoContractTest*" \
  --tests "*DatabaseMigrationListConsistencyTest*" --tests "*OrderIdManagerTest*"

cd services/server && ./mvnw -Dtest='BusinessDayTest,BillSyncServiceBusinessDateTest,CashierShiftAppendOnlyTest,SyncDtoEntityFieldParityTest' test
```

Covered by tests:

- The boundary table in §2, mirrored in Kotlin and Java.
- Window/rule consistency: every 15 minutes of every window maps back to that date.
- A server-stamped bill date is never moved; a cutoff profile moves 01:30 to the previous day and
  leaves 23:59 alone; a cutoff is ignored before its effective date.
- Profile pushes cannot reset the cutoff fields.
- Sealed shifts cannot be reopened or rewritten; declared cash still passes through on the seal.
- Every entity field exists on its sync DTO.

**Not verified here:** `V119` has never been executed against Postgres (no Docker in this
environment, so `PostgresMigrationSmokeTest` self-skips), and `MigrationChainSweepTest` needs a device.
The Room DDL was matched to the exported `84.json` instead, which is what Room validates at runtime.

## 10. What remains

1. **Cutoff editor (the missing half of R7).** The server side is complete: `AppVersionFilter` publishes
   the parsed `X-App-Version`, terminal registration records `cutoff_capable` from it, and
   `PUT /business/business-day` owns the rule (deactivation always allowed, activation gated on every
   active terminal, effective date forward-only, cutoff before noon). What does not exist is the
   settings screen that calls it, so the cutoff can only be set through the API today.
   `app.version.business-day-min` defaults to `0`, which trusts **no** build and therefore blocks
   activation until it is set to the released version code.
2. **Server-side expected cash (R9).** At seal, compute expected cash from the shift's payments and
   store variance, so the variance figure cannot be influenced by a terminal.
3. **Android shift client (R10).** Add `cashierShifts` to `MasterSyncResponse`, persist via
   `MasterSyncProcessor`, and push sealed shifts through the sync manager.
4. **Shift UI.** Open/close drawer screens, float entry, declared count, and the printable close-out.
5. **Closing email (R13).** Templates, the outbox worker, and the cutoff+30-minute schedule with
   revision re-sends for late shifts.

## 11. Commit trail

| Commit | What landed |
|---|---|
| `9743b481` | Design plan (`docs/planning/BUSINESS_DAY_CUTOFF_AND_SHIFTS_PLAN_2026-11-06.md`) |
| `a425239f` | Shared rule, V119, Room 84, shift entity/DAO/DTO, forward-only stamp |
| `8f2fa5ea` | Order numbering, duplicate guard, counter repair and seeding |
| `287ddb9f` | Home summary and Sync Center repair paths |
| `834a5c23` | Server report windows |
| `ac084ad0` | Server shift entity, endpoints, master-pull section, append-only guard |
| `fee1c9ad` | Tests for the sealed-shift guard |

## 12. Trading hours (opening / closing time)

Making the day boundary server-owned exposed that the shop's actual trading hours were neither
editable by an owner nor delivered to a till:

| Layer | Change |
|---|---|
| `RestaurantProfileDTO` | Added `openingTime` / `closingTime` (`LocalTime`). Both existed on the entity and in the schema, but this DTO is what the pull serialises — so every terminal silently received its own defaults and the values were unreachable. |
| `BusinessWriteService.updateProfile` | Copies both fields when present, per-field null-guarded like the rest of the payload, so a partial update cannot blank them. |
| Web admin → Business settings → Basic information | Two `<input type="time">` fields. Load slices `08:00:00` to the `HH:mm` the control expects; validation rejects an identical pair but allows closing earlier than opening, since overnight trading is normal. |
| Android → Restaurant configuration | Read-only "Business hours" line with a pointer to the web admin. The till must not edit them: profile fields are server-owned (`applyProfileChangedFieldsMerge` restores them), and an editable field that silently discards its push is worse than a caption. |

Hours are configuration and display only. The day still rolls on `business_day_cutoff_time`, never on
closing time — a `02:00` close is valid and unrelated to the cutoff.

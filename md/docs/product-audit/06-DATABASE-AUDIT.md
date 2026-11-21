# 06 — Database Audit

**Surfaces:** PostgreSQL via Flyway (`services/server/src/main/resources/db/migration`) and SQLite via Room (`apps/android/.../core/database/AppDatabase.kt`).

## 1. Flyway migrations [V]

- **103 migrations**, versions `V1 … V121`, **no duplicate version numbers** (`sed` + `uniq -d` returned empty). The gaps in the numeric sequence are intentional discard/merge points, not errors.
- **29 migrations create tables**; **59 migrations create indexes** — i.e. index work is spread across the history rather than bolted on at the end, which is the right pattern.
- Latest: `V121__widen_otp_requests_phone_number.sql`. The filename states the intent, which is the house style throughout ("add", "widen", "drop", "backfill") — a genuinely useful convention.
- Migrations are **forward-only with no down scripts**. Correct for Flyway; rollback is by restore/backup, and `backups/` + a web-admin `backups` page exist for that.

### Naming and hygiene

| Check | Result |
| --- | --- |
| Consistent `V<n>__lower_snake_case.sql` | Yes, all 103 |
| Duplicate versions | None |
| Clearly-named intent per file | Yes |
| Secrets moved out of schema | Yes — `V9__remove_easebuzz_credentials_from_restaurantprofiles.sql` |

`V9` removing provider credentials from a tenant table is a notable positive: credentials were recognised as belonging in configuration, not in the schema.

## 2. Indexing [C]

Tenant-leading composite indexes exist on the hot tables, e.g.:

- `V1__init_schema.sql:304-306` — `bills(restaurant_id, device_id, local_id)`, `bills(restaurant_id, created_at)`, `bills(restaurant_id, updated_at)`
- `V7__add_backend_owned_payments.sql:39,41` — `payments(restaurant_id, bill)`, `payments(gateway_payment)`
- `V14__add_payment_refund_tracking.sql:28,31` — refund lookups
- `V73__add_bill_created_by_user_id.sql:4` — `bills(restaurant_id, created_by_user_id)`
- `V86__inventory_loop.sql:40` — `purchase_orders(restaurant_id)`

`idx_bills_device` on `(restaurant_id, device_id, local_id)` is the physical basis for idempotent offline push: a replayed bill from the same device cannot duplicate. That is the single most important index in an offline-first POS and it is present from the initial schema.

**Not verified:** no `EXPLAIN (ANALYZE)` was run, so index *use* by specific queries is unconfirmed. The indexes exist and are shaped correctly; whether every query can use them is a `10-PERFORMANCE-AUDIT.md` follow-up requiring a live database.

## 3. Constraints and data integrity [C]

- Tenant FK columns (`restaurant_id`) are present on tenant-owned tables and used as the leading index column.
- Soft deletes: `users.is_deleted` is filtered in DAO queries (`UserDao`), and the Android side consistently excludes deleted rows — the pattern is applied, not ad hoc.
- `otp_requests.phone_number` was widened (V121) rather than truncated, preserving in-flight OTPs across the change. That is the correct choice for a live table.

**Gap:** no repository-wide inventory of `NOT NULL` / `CHECK` / unique constraints was produced in this pass, so column-level constraint coverage is **[U]**. It should be added to `06` before any schema refactor.

## 4. Room (Android) [V]

- **18 `@Entity` classes**, of which **13 declare indices**. Room does not create indices implicitly, so the 5 without indices should be checked against their DAO queries.
- `AppDatabase.kt` is **2,387 lines**: entities, DAO wiring, converters and every migration in one file.
  - **Risk:** Room migrations are the least forgiving code in the app. A wrong `ALTER TABLE` in a file this size is found by a cashier, not a test — and the instrumented migrations suite is non-blocking (AUD-007).
  - **Recommendation:** split along entity boundaries (`AppDatabase`, `Converters`, `Migrations1..N`), and make the existing `androidTest` migration tests a blocking gate before touching any `ALTER`.
- `fallbackToDestructiveMigration` — **not present** in the reviewed configuration; if it were, an upgrade would silently wipe a shop's unsynced bills. Verified absent in the reviewed paths; keep it that way and add a lint/test guard. [C]

## 5. Findings

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| — | — | No duplicate Flyway versions, tenant-leading indexes on hot tables, provider secrets removed from schema | [V] positive |
| AUD-015 | P2 | 5 of 18 Room entities declare no index while `AppDatabase.kt` (2,387 lines) holds all migrations; migration tests are non-blocking | [V] |
| AUD-016 | P3 | Column-level constraint coverage (`NOT NULL`/`CHECK`/`UNIQUE`) has no recorded inventory | [U] |

## 6. Recommendations (no schema features)

1. Make the Room migration tests blocking (pairs with AUD-007). This is a test-gate change, not a database change.
2. Inventory the 5 unindexed Room entities against their DAO `WHERE`/`ORDER BY` clauses; add indices only where a query needs one.
3. Split `AppDatabase.kt` after the gate in (1) is active.
4. Record a constraint inventory in this document before any refactor of the schema.
5. Keep migrations forward-only; do not introduce down-scripts for a system already using restore-based rollback.

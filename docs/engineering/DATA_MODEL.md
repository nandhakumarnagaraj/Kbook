# KhanaBook — Data Model

> Two stores, one truth each. Read this before adding a table, a column, or a synced
> field.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Companion:
> `SYNC_CONTRACT.md` (how rows move), `ARCHITECTURE.md` (blast radius),
> `../product/PRODUCT.md` (residency summary).

## 1. Two stores

| Store | Owns | Never contains |
|---|---|---|
| **Android Room** (SQLCipher, per-restaurant DB `khanabook_lite_db_<restaurantId>`, **schema v82**) | the cashier's operational truth: bills and their children, menu cache, config cache, print state, sync bookkeeping | other terminals' bills; other tenants at all |
| **PostgreSQL** (Flyway, **91 migration files, latest `V109`**) | the business truth: all terminals' bills, menu, staff, payments, inventory, platform data | device-local secrets, printer bindings, print queues |

Android holds **17 entity tables**; the server has **43 `@Entity` classes** (a subset are
join/index mappings).

## 2. Android Room — v82, 17 tables

| Table | Entity | Purpose | Syncs? |
|---|---|---|---|
| `bills` | `BillEntity` | bills and drafts | ✅ push + pull |
| `bill_items` | `BillItemEntity` | line items; **KOT state rides here** (`kotState`, `kotStatus`) | ✅ push + pull |
| `bill_payments` | `BillPaymentEntity` | payment records (split/part payments) | ✅ push + pull |
| `terminal_daily_counter` | `TerminalDailyCounterEntity` | per-terminal, per-day bill numbering | ❌ local only |
| `menu_items` | `MenuItemEntity` | menu cache, incl. `is_favourite` (server) and `local_favourite` (device) | ✅ pull-led |
| `categories` | `CategoryEntity` | categories, incl. server-owned `is_favourite`, `sort_order` | ✅ both |
| `item_variants` | `ItemVariantEntity` | variants | ✅ both |
| `menu_item_category_repairs` | `MenuItemCategoryRepairEntity` | repair log for category/id healing | ❌ local only |
| `users` | `UserEntity` | staff cache for login/permission display | ✅ pull |
| `staff_permissions` | `StaffPermissionEntity` | permission rows mirror | ✅ pull |
| `permission_cache` | `PermissionCacheEntity` | granted keys + revision for offline authorization | ✅ pull |
| `restaurant_profile` | `RestaurantProfileEntity` | shop/tax/payment config snapshot | ✅ both |

Server `restaurant_profile` also has `field_revisions` (V115), a per-field revision map that gates masked profile pushes. It is server-only and not in the Android schema. See `SYNC_CONTRACT.md` §4.4.
| `printer_profiles` | `PrinterProfileEntity` | device-bound printer configuration | ❌ **never** |
| `kitchen_print_queue` | `KitchenPrintQueueEntity` | pending KOT print jobs | ❌ **never** |
| `kot_events` | `KotEventEntity` | immutable KOT event log (ADD/VOID/REPRINT + snapshot JSON) | ❌ **never** |
| `sync_quarantine_records` | `SyncQuarantineEntity` | rows that failed to sync, with reason + snapshot | ❌ local only |
| `notifications` | `NotificationEntity` | local notification log | ❌ local only |

### 2.1 Android migration policy

- Version bumps are hand-written `Migration` objects in `core/database/AppDatabase.kt`
  (~25 of them, idempotent style: guard with `hasColumn` before altering).
- A wrong migration corrupts every feature — there is no partial safety net.
- Per-restaurant DBs mean a migration runs once **per restaurant**, on first open after
  upgrade.

## 3. PostgreSQL — server tables by family

Table names are the physical `@Table` names.

| Family | Tables |
|---|---|
| **Billing** | `bills`, `bill_items`, `bill_payments` — each with a unique key on `(restaurant_id, device_id, local_id)` and a tenant+updated index |
| **Menu** | `menuitems`, `categories`, `itemvariants`, `menu_extraction_jobs` |
| **Restaurants / identity** | `restaurantprofiles`, `restaurant_terminal`, `device_registration_request`, `users`, `refresh_tokens`, `token_blocklist`, `otp_requests`, `staff_permissions`, `staff_permission_revision`, `permission_requests`, `role_templates` |
| **Payments / Easebuzz** | `easebuzz_sub_merchant`, `easebuzz_sub_merchant_webhook_event`, `easebuzz_webhook_events`, `easebuzz_payouts`, `easebuzz_refund_attempts`, `chargebacks`, `webhook_retry_jobs` |
| **Inventory** | `raw_materials`, `item_recipes`, `purchase_orders`, `purchase_order_items`, `vendors`, `stock_movements`, `stock_logs` (see §5) |
| **Compliance** | `fssai_tracker`, `fssai_renewals` |
| **Notifications** | `notification_events`, `device_tokens` |
| **Platform** | `feature_flag`, `feature_flag_override`, `feature_flag_audit`, `security_audit_log`, `rate_limit_attempts` |
| **Legacy / dead** | `marketplace_orders`, `marketplace_order_items` — no entity or code (see `../product/MARKETPLACE_INTEGRATIONS.md`) |

Payment/Easebuzz specifics (money model, split labels, virtual accounts) are in
`../integrations/easebuzz/EASEBUZZ.md`.

## 4. Residency rule

| Data | Home | Rationale |
|---|---|---|
| Printer bindings, print queue, KOT event log, daily counters | device only | hardware/terminal-bound; meaningless elsewhere |
| Permission cache, quarantine, notification log | device only | derived or local-only state |
| Bills, items, payments, menu, profile, staff, inventory, payments, terminals | server (cached on device) | business truth; powers web reports and reconciliation |
| KOT **state** | server (on `bill_items`) | reports/reconciliation see kitchen progress |
| KOT **printing runtime** | device | immutable snapshots for reprints |

## 5. Legacy and deprecated data

| Item | Status |
|---|---|
| `stock_logs` + `/sync/stocklog` | Android stock ledger removed; server controller/entity/table remain for old APKs. See `../product/PROBLEM.md` item 8 |
| `marketplace_orders`, `marketplace_order_items` | no entity, no code; schema residue only |
| `zomato_*` / `swiggy_*` columns on `restaurantprofiles` | unused; preserved on sync |
| Storefront tables (`V8`) | historical; guarded against new filenames by CI |

## 6. Changing the model — checklist

1. **Decide the owner first.** Device-only, server-only, or synced? An entity with no
   answer here will cause the "fixed X broke Y" class of bug.
2. **Android:** add the entity, register it in `AppDatabase`, bump `version`, write an
   idempotent migration, export the schema.
3. **Server:** add the entity + Flyway migration (`V<N>__...sql`, next free number),
   DTOs, repository.
4. **Synced?** Then also: sync DTO, mapper (`SyncEntityMappers`), push endpoint with
   `SyncPushGuard`, pull inclusion in master sync, `OfflineAuthClass` for its
   operations, and a conflict rule for mutable fields.
5. **Server-owned fields** must be restored on push rather than trusted (see
   `SYNC_CONTRACT.md` §4).
6. **Never rename a column to mean something new** — add a nullable column with a
   default; the Android fleet upgrades slowly.
7. Add a test: migration test for the device schema, and a sync round-trip test for the
   new record type.

## 7. Verification

```bash
# Android tables and schema version
grep -n "version = " apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/AppDatabase.kt
grep -rh -A4 "@Entity" apps/android/app/src/main/java --include="*.kt" | grep -oE 'tableName = "[a-z_]+"' | sort -u

# Server tables and migration count
grep -rh -A3 "@Table" services/server/src/main/java --include="*.java" | grep -oE 'name = "[a-z_]+"' | sort -u
ls services/server/src/main/resources/db/migration | wc -l

# Highest migration version
ls services/server/src/main/resources/db/migration | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1
```

# 04 - Android Room SQLite Migration Inventory & Chain Audit
> **KhanaBook Android POS Local Database Architecture**  
> **Source Definitions:** [`AppDatabase.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/AppDatabase.kt#L91-L110) & [`ShiftMigrations.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/ShiftMigrations.kt)  
> **Automated Test Evidence:** [`DatabaseMigrationListConsistencyTest.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/test/java/com/khanabook/lite/pos/data/local/DatabaseMigrationListConsistencyTest.kt)  
> **Audit Mode:** STRICT READ-ONLY Source & Metadata Audit  

---

## 1. Migration Chain Consistency & Totals

Automated test inspection of `DatabaseMigrationListConsistencyTest.kt` confirms:
- **Total Migrations in `ALL_MIGRATIONS` Array:** Exactly **67 consecutive migrations**.
- **Migration Range:** Unbroken sequence from **Version 17** to **Version 84** ($17 \to 18, 18 \to 19, \dots, 83 \to 84$).
- **Gap Status:** **0 Gaps.** `assertNotNull` asserts every integer step from 17 to 84 exists.
- **Current Target Version:** **84** (`APP_DATABASE_VERSION = 84`).
- **Proposed Version 85:** **NOT IMPLEMENTED.** No `MIGRATION_84_85` exists, and schema export remains at `84.json`.

---

## 2. Key Historical Android Migrations

| Migration Step | Source Location | Key Schema Transformations | Technical Mechanics |
|:---:|---|---|---|
| **`17 -> 18`** | `AppDatabase.kt` | Added initial `sync_quarantine_records` dead-letter queue. | `CREATE TABLE IF NOT EXISTS sync_quarantine_records` |
| **`35 -> 36`** | `AppDatabase.kt` | Added `terminal_daily_counter` for offline monotonic invoice sequence generation. | `CREATE TABLE IF NOT EXISTS terminal_daily_counter` |
| **`45 -> 46`** | `AppDatabase.kt` | Dedicated `printer_profiles` table created for Bluetooth, USB, and LAN hardware bindings. | `CREATE TABLE IF NOT EXISTS printer_profiles` |
| **`51 -> 52`** | `AppDatabase.kt` | Added `bills.source_channel` column for Zomato/Swiggy/Takeaway channel tagging. | `ALTER TABLE bills ADD COLUMN source_channel TEXT` |
| **`53 -> 54`** | `AppDatabase.kt` | Added `bills.refund_amount` column for server-owned refund sync reflection. | `ALTER TABLE bills ADD COLUMN refund_amount TEXT` |
| **`55 -> 56`** | `AppDatabase.kt` | Added `kot_events` table for kitchen ticket reprint auditing. | `CREATE TABLE IF NOT EXISTS kot_events` |
| **`71 -> 72`** | `AppDatabase.kt` | Collapsed legacy staff roles (`WAITER`, `CASHIER`, `MANAGER`) into unified `SHOP_STAFF`. | Data DML: `UPDATE users SET role = 'SHOP_STAFF'` |
| **`77 -> 78`** | `AppDatabase.kt` | Added `menu_item_category_repairs` self-healing table for orphaned dishes. | `CREATE TABLE IF NOT EXISTS menu_item_category_repairs` |
| **`83 -> 84`** | `ShiftMigrations.kt` | Added business hours cutoff (`business_day_cutoff_time`, `opening_time`, `closing_time`) to `restaurant_profile` and created `local_cashier_shifts`. | 5 `ALTER TABLE` statements + `CREATE TABLE local_cashier_shifts` |
| **`84 -> 85`** | `ShiftMigrations.kt` | Purged dormant `local_cashier_shifts` table and indexes. | `DROP TABLE IF EXISTS local_cashier_shifts` |

---

## 3. Deep Dive: `ShiftMigrations.kt` (Version 83 $\to$ 84)

The most recent migration, [`ShiftMigrations.MIGRATION_83_84`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/ShiftMigrations.kt#L34-L115), was externalized into its own file to prevent `AppDatabase.kt` from exceeding 2,400 lines:

### 3.1 Profile Business Day Configuration
Guarded against duplicate-column crashes:
```kotlin
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
```

### 3.2 Creation of `local_cashier_shifts` Table
Defines 25 columns matching `CashierShiftEntity.kt` column-for-column. The migration deliberately omits partial indices (`WHERE status = 'ACTIVE'`) because Android Room's runtime schema validator cannot express SQLite partial index clauses, causing `IllegalStateException: Migration didn't properly handle` on startup.

---

## 4. SQLite DDL Constraints & Why Room v85 Must Not Be Preemptively Implemented

1. **SQLite Column Dropping Constraints:** Unlike PostgreSQL, SQLite historically lacks an atomic `ALTER TABLE DROP COLUMN` (only supported in SQLite $\ge$ 3.35.0, which is not guaranteed across older Android 8/9/10 devices).
2. **Table Rebuild Requirement:** Dropping the 9 legacy printer columns from `restaurant_profile` requires a 12-step table recreation migration:
   - Create `restaurant_profile_new` with remaining 55 columns.
   - Copy 55 columns from `restaurant_profile`.
   - Drop old table `restaurant_profile`.
   - Rename `restaurant_profile_new` to `restaurant_profile`.
   - Recreate indexes and trigger schema export `85.json` via KSP compiler.
3. **Audit Directive:** Because the current phase is strictly READ-ONLY discovery, Room v85 is frozen and will not be created until authorized.

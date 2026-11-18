# 02 - Android Room SQLite Schema Audit (Schema Version 84)
> **KhanaBook Android POS Local Database Architecture**  
> **Target Schema:** Room Schema Version 84 (Exported artifact: `apps/android/app/schemas/.../84.json`)  
> **Database Configuration:** Room Database + SQLCipher SQLite  
> **Entity Declaration:** [`AppDatabase.kt`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/AppDatabase.kt#L38-L64)  
> **Audit Mode:** STRICT READ-ONLY Inspection of Room Compilation Metadata & Entities  

---

## 1. Executive Summary & Inventory Totals

Inspection of Room schema version 84 establishes:
- **Room Database Version:** **84** (`APP_DATABASE_VERSION = 84`).
- **Total Tables / Entities:** Exactly **18 tables**.
- **Total Columns:** Exactly **349 columns**.
- **Foreign Key Constraints:** **6 foreign key definitions** (`menu_items -> categories`, `item_variants -> menu_items`, `bills -> users`, `bill_items -> bills`, `bill_items -> menu_items`, `bill_items -> item_variants`, `bill_payments -> bills`).
- **Indices:** **35 local indices**.
- **Primary Keys:** Every table defines an explicit primary key (composite keys on `kot_events`, `terminal_daily_counter`, `permission_cache`).

---

## 2. Master SQLite Table Catalog (Room v84)

| # | SQLite Table Name | Kotlin Entity Class | Column Count | Primary Key | Indices | Foreign Keys | Purpose & Lifecycle |
|---|---|---|---:|---|:---:|:---:|---|
| 1 | `users` | `UserEntity` | 19 | `id` (AutoInc) | 0 | 0 | Local staff & active session cache (password hash stripped) |
| 2 | `restaurant_profile` | `RestaurantProfileEntity` | 65 | `id` (AutoInc) | 0 | 0 | Singleton store configuration, tax rules, business cutoff |
| 3 | `categories` | `CategoryEntity` | 14 | `id` (AutoInc) | 1 | 0 | Menu category taxonomy and display order |
| 4 | `menu_items` | `MenuItemEntity` | 26 | `id` (AutoInc) | 2 | 1 | Dish catalog, prices, food type, barcode, availability |
| 5 | `item_variants` | `ItemVariantEntity` | 16 | `id` (AutoInc) | 1 | 1 | Portion rates (Half, Full) linked to menu items |
| 6 | `printer_profiles` | `PrinterProfileEntity` | 15 | `id` (AutoInc) | 1 | 0 | Local hardware configuration (Bluetooth MAC, USB, Wi-Fi LAN IP) |
| 7 | `kitchen_print_queue` | `KitchenPrintQueueEntity`| 16 | `id` (AutoInc) | 1 | 0 | Offline-resilient KOT thermal spooler queue |
| 8 | `bills` | `BillEntity` | 56 | `id` (AutoInc) | 11 | 1 | Orders, bill numbers (`daily_order_id`), invoices (`lifetime_order_id`) |
| 9 | `bill_items` | `BillItemEntity` | 21 | `id` (AutoInc) | 3 | 3 | Order lines, portion quantities, prices, sent_to_kot state |
| 10 | `bill_payments` | `BillPaymentEntity` | 21 | `id` (AutoInc) | 2 | 1 | Payment tenders, cash/UPI splits, idempotency keys |
| 11 | `local_cashier_shifts` | `CashierShiftEntity` | 25 | `id` (AutoInc) | 3 | 0 | Drawer float and shift reconciliation (Dormant UI) |
| 12 | `sync_quarantine_records`| `SyncQuarantineEntity` | 11 | `id` (AutoInc) | 3 | 0 | Dead-letter queue for rejected sync payloads |
| 13 | `menu_item_category_repairs`| `MenuItemCategoryRepairEntity` | 5 | `id` (AutoInc) | 1 | 0 | Self-healing map for dishes pulled before their category |
| 14 | `kot_events` | `KotEventEntity` | 12 | Composite (`public_token`, `kot_revision`) | 2 | 0 | Local kitchen ticket reprint audit ledger |
| 15 | `terminal_daily_counter`| `TerminalDailyCounterEntity` | 6 | Composite (`restaurant_id`, `terminal_id`, `date`) | 0 | 0 | Monotonic sequence generator for daily bill numbers |
| 16 | `notifications` | `NotificationEntity` | 10 | `id` (AutoInc) | 0 | 0 | Local broadcast alerts and operational push messages |
| 17 | `staff_permissions` | `StaffPermissionEntity` | 7 | `id` (AutoInc) | 2 | 0 | Offline cashier PIN verification hashes and permissions |
| 18 | `permission_cache` | `PermissionCacheEntity` | 4 | `user_id` | 0 | 0 | Revision marker for fast offline privilege checks |

---

## 3. Deep Investigation: `restaurant_profile` & Printer Architecture Coexistence

A forensic audit of `84.json` reveals that the old and new printer configuration models **COEXIST physically in the SQLite schema**:

### 3.1 Physical Printer Columns in `restaurant_profile` (9 Columns in SQLite)
```json
"tableName": "restaurant_profile"
```
The following 9 columns physically exist in SQLite table `restaurant_profile` at Room v84:
1. `printer_enabled`: INTEGER, NOT NULL, default `0`
2. `printer_name`: TEXT, NULLABLE
3. `printer_mac`: TEXT, NULLABLE
4. `paper_size`: TEXT, NOT NULL, default `'58mm'`
5. `printer_mode`: TEXT, NOT NULL, default `'DUAL'`
6. `kitchen_printer_enabled`: INTEGER, NOT NULL, default `0`
7. `kitchen_printer_name`: TEXT, NULLABLE
8. `kitchen_printer_mac`: TEXT, NULLABLE
9. `kitchen_printer_paper_size`: TEXT, NOT NULL, default `'58mm'`

### 3.2 The Dedicated `printer_profiles` Table (15 Columns in SQLite)
```json
"tableName": "printer_profiles"
```
1. `id`: INTEGER PK, NOT NULL
2. `role`: TEXT, NOT NULL (`CUSTOMER` vs `KITCHEN`)
3. `restaurant_id`: INTEGER, NOT NULL
4. `name`: TEXT, NOT NULL
5. `mac_address`: TEXT, NOT NULL
6. `connection_type`: TEXT, NOT NULL (`BLUETOOTH`, `USB`, `WIFI_LAN`)
7. `host`: TEXT, NULLABLE (IP for TCP port 9100)
8. `port`: INTEGER, NOT NULL (e.g. 9100)
9. `enabled`: INTEGER, NOT NULL
10. `auto_print`: INTEGER, NOT NULL
11. `paper_size`: TEXT, NOT NULL (`58mm` vs `80mm`)
12. `include_logo`: INTEGER, NOT NULL
13. `copies`: INTEGER, NOT NULL
14. `created_at`: INTEGER, NOT NULL
15. `updated_at`: INTEGER, NOT NULL

### 3.3 Coexistence Analysis & Code Reality
- **Kotlin Annotation vs SQLite Schema:** While `@Deprecated` was added to Kotlin fields in `RestaurantProfileEntity.kt`, the SQLite physical table structure still defines all 9 columns. Room validates column-for-column against `84.json`.
- **Application Code References:**
  - [`PrintRouter.kt#L456`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/printing/domain/PrintRouter.kt#L456) maintains fallback method `legacyPrinter(restaurantProfile)` if `printer_profiles` table is empty.
  - [`HomeViewModel.kt#L120`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/reports/viewmodel/HomeViewModel.kt#L120) checks `profile?.printerEnabled == true && !profile.printerMac.isNullOrBlank()`.
  - [`MasterSyncProcessor.kt#L1367`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/domain/MasterSyncProcessor.kt#L1367) preserves local printer MAC on profile sync pulls.
- **Architectural Conclusion:** The system operates in a transitional state: `printer_profiles` is the authoritative source for newly configured devices, while `restaurant_profile` columns are retained as an active fallback for legacy installs to prevent billing disruption.

---

## 4. Key Financial Identifiers in SQLite `bills` (56 Columns)

The Room v84 `bills` schema explicitly preserves all operational billing identifiers:
1. `daily_order_id`: INTEGER, NOT NULL — Bill Number resetting to `1` daily based on cutoff.
2. `daily_order_display`: TEXT, NOT NULL — e.g. `'A01'`, `'B05'`.
3. `lifetime_order_id`: INTEGER, NULLABLE — Monotonic invoice sequence number.
4. `invoice_number`: TEXT, NULLABLE — GST invoice identifier (e.g. `'INV-A-2627-0001'`).
5. `part_amount_1` / `part_amount_2`: TEXT, default `'0.0'` — Split tenders.
6. `status_version`: INTEGER, default `0` — Optimistic lock guard against stale LWW overwrites.
7. `public_token`: TEXT, NULLABLE, Unique Indexed — Global UUID receipt identifier.

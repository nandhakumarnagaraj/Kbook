# 05 - Cross-Database Entity & Field Mapping Specification
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **PostgreSQL 16 (`kbook_saas`) ↔ Android Room SQLite (v84)**  
> **Audit Mode:** STRICT READ-ONLY Inspection of Cross-Database Models  

---

## 1. 6-Tier Entity Residency Classification

| Tier | Category | Table Count | Tables Included | Rationale & Residency Rules |
|:---:|---|:---:|---|---|
| **1** | **PostgreSQL Only** | **15** | `restaurant_terminal`, `device_registration_request`, `security_audit_log`, `email_outbox`, `device_tokens`, `refresh_tokens`, `token_blocklist`, `otp_requests`, `rate_limit_attempts`, `feature_flag`, `feature_flag_override`, `feature_flag_audit`, `menu_extraction_jobs`, `stock_logs`, `flyway_schema_history` | Central SaaS cloud governance, tamper-proof security auditing, fleet enrollment, asynchronous email queues, and credentials. Budget tablets must not carry cloud overhead or credentials. |
| **2** | **SQLite Only** | **8** | `printer_profiles`, `kitchen_print_queue`, `kot_events`, `sync_quarantine_records`, `menu_item_category_repairs`, `terminal_daily_counter`, `staff_permissions`, `permission_cache` | Hardware isolation (Bluetooth MAC, USB), sub-10ms counter latency, local printer spooler queues, self-healing sync maps, and offline PIN verification. |
| **3** | **Both Databases (Synced)** | **9** | `bills` ↔ `bills`, `bill_items` ↔ `bill_items`, `bill_payments` ↔ `bill_payments`, `categories` ↔ `categories`, `menu_items` ↔ `menuitems`, `item_variants` ↔ `itemvariants`, `restaurant_profile` ↔ `restaurantprofiles`, `users` ↔ `users`, `notifications` ↔ `notification_events` | Core restaurant master data and transactional ledgers synchronized bi-directionally via delta sync. |
| **4** | **Derived / Computed Data** | **4** | `daily_order_display` (`terminal_series + daily_order_id`), `invoice_number` (`INV-series-FY-seq`), `cgst_amount` / `sgst_amount` (tax calculation), `public_token` (receipt UUID) | Computed deterministically by business rules at runtime or during order creation. |
| **5** | **Cache / Temp Data** | **3** | `staff_permissions` (cached PIN hash), `payment_attempt_status` (in-flight UPI double-tap lock), `permission_cache` (revision gate) | Ephemeral operational state to protect counter speed and prevent double-charging. |
| **6** | **Unclear / Deprecated** | **1** | `stock_logs` (PG, 163 rows) | Legacy remnants: raw material inventory was dropped in V127; shifts were permanently purged in V132/Room v85. |

---

## 2. Comprehensive Shared Business Entity Mapping Matrix

### 2.1 Entity: Orders & Customer Bills
- **PostgreSQL Table:** `bills` (43 cols) | **Entity:** `Bill.java` | **Repo:** `BillRepository.java`
- **SQLite Table:** `bills` (56 cols) | **Entity:** `BillEntity.kt` | **Repo:** `BillRepository.kt`
- **Sync Channel:** `POST /api/v1/sync/master/push` & `GET /api/v1/sync/master/pull`
- **Primary Identifiers:**
  - Client ID: `bills.id` (SQLite INTEGER) $\to$ `bills.local_id` (PostgreSQL BIGINT)
  - Server ID: `bills.id` (PostgreSQL BIGINT) $\to$ `bills.server_id` (SQLite INTEGER)
  - Global Token: `public_token` (VARCHAR / TEXT UUID, UNIQUE)
- **Tenant Key:** `restaurant_id` (PostgreSQL BIGINT / SQLite INTEGER)
- **Terminal Key:** `device_id` (SSAID), `terminal_id` (UUID), `terminal_series` ('A'..'E')
- **Soft Deletion:** `is_deleted` (BOOLEAN vs INTEGER 0/1) + `server_updated_at` tombstone
- **Optimistic Concurrency:** `status_version` (INTEGER) checked on server to protect paid status
- **Offline Creation:** Created instantly offline with monotonic `daily_order_id` (resets daily to 1)

### 2.2 Entity: Order Line Items
- **PostgreSQL Table:** `bill_items` (22 cols) | **Entity:** `BillItem.java` | **Repo:** `BillItemRepository.java`
- **SQLite Table:** `bill_items` (21 cols) | **Entity:** `BillItemEntity.kt` | **Repo:** `BillItemDao.kt`
- **Sync Channel:** Pushed and pulled in batch as children of bills
- **Primary Identifiers:** Client `id` $\to$ PG `local_id`, PG `id` $\to$ Client `server_id`
- **Foreign Key to Bill:** `bill_id` (local bill PK) + `server_bill_id` (server bill PK)
- **Tenant Key:** `restaurant_id`
- **Denormalization Note:** `item_name` and `variant_name` are snapshotted to preserve historical invoice integrity if dishes are renamed later.

### 2.3 Entity: Payment Tenders & Split Payments
- **PostgreSQL Table:** `bill_payments` (18 cols) | **Entity:** `BillPayment.java` | **Repo:** `BillPaymentRepository.java`
- **SQLite Table:** `bill_payments` (21 cols) | **Entity:** `BillPaymentEntity.kt` | **Repo:** `BillPaymentDao.kt`
- **Sync Channel:** Master sync batch push
- **Idempotency Key:** `operation_id` (`${restaurantId}:${terminalId}:${uuid}:${action}`) with `UNIQUE` index in PostgreSQL
- **Split Tenders:** Supports `CASH`, `UPI`, `POS`, and 2-way splits (`part_amount_1`, `part_amount_2`)
- **Tenant Key:** `restaurant_id`

### 2.4 Entity: Menu Catalog & Categories
- **PostgreSQL Table:** `categories` (15 cols) | **Entity:** `Category.java` | **Repo:** `CategoryRepository.java`
- **SQLite Table:** `categories` (14 cols) | **Entity:** `CategoryEntity.kt` | **Repo:** `CategoryDao.kt`
- **Sync Channel:** Master sync push (owner only) and pull (all terminals)
- **Primary Identifiers:** Client `id` $\to$ PG `local_id`, PG `id` $\to$ Client `server_id`
- **Ordering:** `sort_order` (INTEGER)

### 2.5 Entity: Menu Dishes & Items
- **PostgreSQL Table:** `menuitems` (25 cols) | **Entity:** `MenuItem.java` | **Repo:** `MenuItemRepository.java`
- **SQLite Table:** `menu_items` (26 cols) | **Entity:** `MenuItemEntity.kt` | **Repo:** `MenuItemDao.kt`
- **Sync Channel:** Master sync push (owner only) and pull
- **Naming Discrepancy:** PostgreSQL uses unseparated `menuitems`; SQLite uses snake_case `menu_items`
- **Self-Healing Hook:** If parent category is missing on client, quarantined in `menu_item_category_repairs` until category pull arrives.

### 2.6 Entity: Portion Sizes & Variants
- **PostgreSQL Table:** `itemvariants` (18 cols) | **Entity:** `ItemVariant.java` | **Repo:** `ItemVariantRepository.java`
- **SQLite Table:** `item_variants` (16 cols) | **Entity:** `ItemVariantEntity.kt` | **Repo:** `ItemVariantDao.kt`
- **Parent Relationship:** Linked to `menuitems` via `menu_item_id` and `server_menu_item_id`

### 2.7 Entity: Restaurant Store Profile
- **PostgreSQL Table:** `restaurantprofiles` (43 cols) | **Entity:** `RestaurantProfile.java` | **Repo:** `RestaurantProfileRepository.java`
- **SQLite Table:** `restaurant_profile` (65 cols) | **Entity:** `RestaurantProfileEntity.kt` | **Repo:** `RestaurantProfileDao.kt`
- **Singleton Pattern:** Exactly 1 profile per restaurant tenant.
- **Divergence:** SQLite physical table retains 9 legacy hardware printer columns; PostgreSQL dropped them in V99.

### 2.8 Entity: Users & Staff Accounts
- **PostgreSQL Table:** `users` (25 cols) | **Entity:** `User.java` | **Repo:** `UserRepository.java`
- **SQLite Table:** `users` (19 cols) | **Entity:** `UserEntity.kt` | **Repo:** `UserDao.kt`
- **Security Boundary:** `users.password_hash` is stripped by Jackson serializers and NEVER written to Android SQLite.

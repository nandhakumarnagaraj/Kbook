# KhanaBook — Database Schema & Data Model Specification (DATABASE.md)

**Document Version:** 2.1.0  
**Status:** Approved Source of Truth for Relational Schema & Local Edge Storage  
**Database Engines:** PostgreSQL 16+ (Cloud Master, Flyway 117+ Migrations) & SQLite 3 / Room v82 (Android Edge Terminals with SQLCipher)  

---

## 1. Cloud Relational Entity Relationship (ER) Diagram

```mermaid
erDiagram
    RESTAURANTPROFILES ||--o{ RESTAURANT_TERMINAL : provisions
    RESTAURANTPROFILES ||--o{ USERS : employs
    RESTAURANTPROFILES ||--o{ CATEGORIES : organizes
    RESTAURANTPROFILES ||--o{ BILLS : records
    RESTAURANTPROFILES ||--o{ RAW_MATERIALS : stocks
    RESTAURANTPROFILES ||--o{ EASEBUZZ_SUB_MERCHANT : onboards
    RESTAURANTPROFILES ||--o{ FSSAI_TRACKER : tracks_compliance

    CATEGORIES ||--o{ MENUITEMS : contains
    MENUITEMS ||--o{ ITEMVARIANTS : has_variants
    MENUITEMS ||--o{ ITEM_RECIPES : defines_recipe
    RAW_MATERIALS ||--o{ ITEM_RECIPES : consumed_by

    BILLS ||--o{ BILL_ITEMS : contains
    BILLS ||--o{ BILL_PAYMENTS : settled_by
    RESTAURANT_TERMINAL ||--o{ BILLS : issues_series_for

    RAW_MATERIALS ||--o{ STOCK_MOVEMENTS : logs
    RAW_MATERIALS ||--o{ PURCHASE_ORDER_ITEMS : ordered_in
    VENDORS ||--o{ PURCHASE_ORDERS : supplies
    PURCHASE_ORDERS ||--o{ PURCHASE_ORDER_ITEMS : includes

    RESTAURANTPROFILES {
        bigint id PK
        varchar restaurant_id UQ "Singleton restaurant UUID"
        varchar name "Trading business name"
        text address "Physical shop address"
        varchar phone "Primary contact phone"
        varchar email "Business contact email"
        varchar gstin "15-digit GSTIN registration"
        varchar fssai_number "14-digit FSSAI registration"
        boolean tax_enabled "Composite GST flag"
        varchar whatsapp_number "Receipt delivery WhatsApp"
        varchar logo_url "Header logo URL"
        bigint updated_at "BaseSyncEntity epoch ms"
    }

    USERS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        varchar login_id UQ "Unique login identifier"
        varchar name "User full name"
        varchar email "Unique login email"
        varchar phone_number "Phone number"
        varchar password_hash "BCrypt hash"
        varchar role "OWNER | SHOP_STAFF | KBOOK_ADMIN"
        varchar auth_provider "PHONE | GOOGLE | PASSWORD"
        boolean is_active "Account status flag"
    }

    RESTAURANT_TERMINAL {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        varchar terminal_series "Allocated invoice prefix (e.g. A, B)"
        varchar device_id "Hardware UUID"
        varchar terminal_name "Display counter name"
        varchar status "PENDING | APPROVED | REJECTED | DEACTIVATED"
        bigint last_seen_at "Heartbeat epoch ms"
    }

    CATEGORIES {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        varchar name "Category display name"
        boolean is_veg "Veg preference flag"
        integer sort_order "Display sequence"
        boolean is_active "Active catalog status"
        boolean is_favourite "Server-pinned lead category"
    }

    MENUITEMS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        bigint category_id "Local client category FK"
        bigint server_category_id FK "Cloud categories.id FK"
        varchar name "Dish or beverage name"
        numeric base_price "Standard selling price"
        varchar food_type "VEG | NON_VEG | EGG | BEVERAGE"
        text description "Menu item description"
        boolean is_available "Real-time kitchen toggle"
        boolean is_favourite "Starred item"
        varchar image_url "CDN thumbnail"
    }

    ITEMVARIANTS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        bigint menu_item_id "Local client item FK"
        bigint server_menu_item_id FK "Cloud menuitems.id FK"
        varchar name "Portion (Half, Full, 500ml)"
        numeric price "Portion selling price"
        boolean is_active "Active portion status"
    }

    BILLS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        varchar device_id "Creating POS device UUID"
        bigint local_id "Creating POS local bill ID"
        bigint daily_order_id "Counter token sequence"
        varchar daily_order_display "Formatted token (e.g. #042)"
        varchar terminal_series "Terminal identifier prefix"
        varchar invoice_number "Legal tax invoice number"
        varchar order_type "DINE_IN | TAKEAWAY | DELIVERY"
        numeric subtotal "Tax-exclusive item sum"
        numeric cgst_amount "Central GST component"
        numeric sgst_amount "State GST component"
        numeric total_amount "Gross bill total payable"
        varchar payment_mode "CASH | UPI | SPLIT | CARD"
        varchar payment_status "PAID | PENDING | REFUNDED"
        varchar order_status "COMPLETED | CANCELLED | VOID"
        integer status_version "Monotonic intentional state counter"
    }

    BILL_ITEMS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        bigint bill_id "Local bill ID"
        bigint server_bill_id FK "Cloud bills.id FK"
        bigint menu_item_id "Snapshot item reference"
        varchar item_name "Fixed item name snapshot"
        varchar variant_name "Fixed variant snapshot"
        integer quantity "Quantity billed"
        numeric price "Unit price snapshot"
        numeric subtotal "Line subtotal"
    }

    BILL_PAYMENTS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        bigint bill_id "Local bill ID"
        bigint server_bill_id FK "Cloud bills.id FK"
        varchar payment_mode "CASH | UPI | CARD | CREDIT"
        numeric amount "Settlement component amount"
        varchar payment_status "SUCCESS | FAILED | PENDING"
        varchar transaction_ref "Easebuzz/Bank reference ID"
    }

    RAW_MATERIALS {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        varchar name "Ingredient name (e.g. Basmati Rice)"
        varchar unit "Unit of measure (KG, LTR, PACK)"
        numeric current_stock "Available stock quantity"
        numeric reorder_level "Low-stock trigger threshold"
        numeric cost_per_unit "Latest purchase rate"
    }

    ITEM_RECIPES {
        bigint id PK
        bigint restaurant_id FK "Tenant boundary"
        bigint menu_item_id FK "Cloud menuitems.id FK"
        bigint raw_material_id FK "Cloud raw_materials.id FK"
        numeric quantity_required "Portion consumption quantity"
    }
```

---

## 2. PostgreSQL Physical Table Specifications

### 2.1 `restaurantprofiles` (Tenant Singleton Profile)
* **Table:** `restaurantprofiles`
* **Constraints:**
  * `uq_restaurantprofiles_restaurant_id`: Unique constraint on `restaurant_id` (guarantees exactly one profile row per tenant).
  * `restaurantprofiles_restaurant_id_device_id_local_id_key`: Unique constraint on composite tuple `(restaurant_id, device_id, local_id)`.
* **Columns:**
  | Column | Type | Constraints | Description |
  | :--- | :--- | :--- | :--- |
  | `id` | `BIGINT` | Primary Key (Identity) | Server primary key |
  | `restaurant_id` | `VARCHAR(64)` | NOT NULL, UNIQUE | Business tenant UUID |
  | `name` | `VARCHAR(150)` | NOT NULL | Registered trading business name |
  | `address` | `TEXT` | NULL | Full physical address for receipt printing |
  | `phone` | `VARCHAR(15)` | NOT NULL | Owner contact phone number |
  | `email` | `VARCHAR(255)` | NULL | Official business email |
  | `gstin` | `VARCHAR(15)` | NULL | 15-character Indian GSTIN registration |
  | `fssai_number` | `VARCHAR(14)` | NULL | 14-digit FSSAI license/registration number |
  | `tax_enabled` | `BOOLEAN` | NOT NULL DEFAULT false | Whether tax calculation is active |
  | `whatsapp_number` | `VARCHAR(15)` | NULL | WhatsApp number for automated e-bills |
  | `logo_url` | `VARCHAR(500)` | NULL | CDN URL to receipt/header logo image |
  | `version` | `BIGINT` | NOT NULL DEFAULT 0 | Optimistic locking counter |
  | `updated_at` | `BIGINT` | NOT NULL | BaseSyncEntity epoch milliseconds |
  | `created_at` | `BIGINT` | NOT NULL | BaseSyncEntity epoch milliseconds |

### 2.2 `users` (Authentication & Access Control)
* **Table:** `users`
* **Constraints:** Unique on `email`, `login_id`, `phone_number`.
* **Columns:**
  | Column | Type | Constraints | Description |
  | :--- | :--- | :--- | :--- |
  | `id` | `BIGINT` | Primary Key (Identity) | Server primary key |
  | `restaurant_id` | `BIGINT` | NOT NULL, Indexed | Multi-tenant boundary |
  | `name` | `VARCHAR(100)` | NOT NULL | Full name of staff member or owner |
  | `login_id` | `VARCHAR(100)` | NOT NULL, UNIQUE | Username for credentials auth |
  | `email` | `VARCHAR(255)` | NULL, UNIQUE | Email address |
  | `phone_number` | `VARCHAR(15)` | NULL, UNIQUE | Mobile phone number |
  | `google_email` | `VARCHAR(255)` | NULL, Indexed | Linked Google SSO address |
  | `auth_provider` | `VARCHAR(20)` | NOT NULL DEFAULT 'PHONE' | `PHONE`, `GOOGLE`, `PASSWORD` |
  | `password_hash` | `VARCHAR(255)` | NULL | BCrypt password hash (WRITE_ONLY) |
  | `role` | `VARCHAR(20)` | NOT NULL | `OWNER`, `SHOP_STAFF`, `KBOOK_ADMIN` |
  | `is_active` | `BOOLEAN` | NOT NULL DEFAULT true | Whether account can authenticate |

### 2.3 `bills`, `bill_items`, and `bill_payments` (Financial Core)
* **Table:** `bills`
  * **Composite Unique Constraint:** `(restaurant_id, device_id, local_id)`
  * **Status Versioning:** `status_version INT NOT NULL DEFAULT 0` — incremented by Android POS on intentional state changes to safeguard against stale Last-Write-Wins overwrites.
  * **Invoice Numbering:** `invoice_number VARCHAR(50)` generated as `KB-{RestaurantId}-{TerminalSeries}-{FinancialYear}-{Seq}`.
* **Table:** `bill_items`
  * **Composite Unique Constraint:** `(restaurant_id, device_id, local_id)`
  * **Historical Snapshots:** Captures `item_name`, `variant_name`, `quantity`, `price`, `tax_rate`, and `subtotal`. Subsequent price adjustments on `menuitems` never alter historical bill records.
* **Table:** `bill_payments`
  * **Composite Unique Constraint:** `(restaurant_id, device_id, local_id)`
  * Supports split settlements across `CASH`, `UPI`, `CARD`, `CREDIT`.

### 2.4 `categories`, `menuitems`, and `itemvariants` (Menu Engine)
* **Table:** `categories`
  * `(restaurant_id, device_id, local_id)` composite unique key.
  * Fields: `name`, `is_veg`, `sort_order`, `is_active`, `is_favourite`.
* **Table:** `menuitems`
  * Contains local `category_id` (Room reference) and server `server_category_id` (`categories.id` foreign key).
  * Fields: `name`, `base_price`, `food_type`, `description`, `is_available` (real-time availability), `is_favourite`, `image_url`.
* **Table:** `itemvariants`
  * Maps portions ("Half", "Full", "Small", "Regular") to parent `menuitems`.

### 2.5 Inventory & Recipe Management
* **`raw_materials`:** Stores raw ingredients (` Basmati Rice`, `Cooking Oil`, `Chicken Breasts`) with unit of measure, stock balance, reorder levels, and unit costs.
* **`item_recipes`:** Maps each menu item to required raw material quantities (`menu_item_id`, `raw_material_id`, `quantity_required`). Billed items automatically trigger stock deduction.
* **`stock_movements`:** Audit trail for inventory adjustments (`PURCHASE`, `SALE_DEDUCTION`, `WASTAGE`, `PHYSICAL_AUDIT_ADJUSTMENT`).
* **`purchase_orders` & `vendors`:** Tracks supplier PO procurement cycles (`DRAFT`, `ORDERED`, `RECEIVED`, `CANCELLED`).

### 2.6 Compliance & Payment Integration Tables
* **`easebuzz_sub_merchant`:** Sub-merchant account credentials, KYC status (`APPROVED`, `SUBMITTED`, `ACTION_REQUIRED`), Easebuzz sub-merchant virtual ID, bank account details.
* **`easebuzz_webhook_events`:** Inbound webhook payloads, processed status, and SHA-512 verification logs.
* **`fssai_tracker`:** 14-digit FSSAI license record, FoSCoS validity date, license category (Registration vs State License), and renewal notifications.
* **`merchant_agreement`:** Owner terms-of-service acceptance, IP address, timestamp, and signed legal contract blobs.

---

## 3. Android Room Database Schema (Edge Engine - Schema v82)

The Android POS client runs a local SQLite database encrypted with **SQLCipher** (`khanabook_lite_db_<restaurantId>`), managed via `AppDatabase.kt` (Room Schema **v82**).

### 3.1 17 Room Entity Classes

| # | Room Entity Class | Table Name | Purpose on Edge POS |
| :-: | :--- | :--- | :--- |
| 1 | `UserEntity` | `users` | Local cached credentials, offline PIN verification, active shift login |
| 2 | `RestaurantProfileEntity` | `restaurantprofiles` | Cached business identity, receipt tax rates, header info |
| 3 | `CategoryEntity` | `categories` | Menu classification and POS tab ordering |
| 4 | `MenuItemEntity` | `menuitems` | Dish catalog, live 86/availability toggles, base pricing |
| 5 | `ItemVariantEntity` | `itemvariants` | Dish portions and variant pricing |
| 6 | `PrinterProfileEntity` | `printer_profiles` | USB, Bluetooth, and IP thermal ESC/POS printer hardware configurations |
| 7 | `KitchenPrintQueueEntity` | `kitchen_print_queue` | Local queue ensuring KOTs print reliably even during network dropouts |
| 8 | `BillEntity` | `bills` | Complete offline bill record, split tenders, order status, monotonic version |
| 9 | `BillItemEntity` | `bill_items` | Line items attached to offline bills with price snapshots |
| 10 | `BillPaymentEntity` | `bill_payments` | Tender details (Cash, Dynamic QR, Card) |
| 11 | `SyncQuarantineEntity` | `sync_quarantine` | Isolates malformed or conflicted records from blocking the sync pipeline |
| 12 | `MenuItemCategoryRepairEntity` | `menuitem_category_repairs` | Edge heal mechanism for orphaned category-item links during sync |
| 13 | `KotEventEntity` | `kot_events` | Kitchen ticket lifecycles (Send, Edit, Void, Reprint) |
| 14 | `TerminalDailyCounterEntity` | `terminal_daily_counters` | Monotonic token sequence generation for daily order counters |
| 15 | `NotificationEntity` | `notifications` | Local push notification feed from FCM / Web Admin |
| 16 | `StaffPermissionEntity` | `staff_permissions` | Local cached permissions matrix for the logged-in user |
| 17 | `PermissionCacheEntity` | `permission_cache` | High-speed cache for instant POS UI permission checks |

---

## 4. Database Indexing & Performance Optimization

```sql
-- Multi-tenant sync delta lookups (used by /sync/pull)
CREATE INDEX idx_bills_tenant_updated ON bills (restaurant_id, updated_at);
CREATE INDEX idx_menuitems_tenant_updated ON menuitems (restaurant_id, updated_at);
CREATE INDEX idx_categories_tenant_updated ON categories (restaurant_id, updated_at);
CREATE INDEX idx_restaurantprofiles_tenant_updated ON restaurantprofiles (restaurant_id, updated_at);

-- Composite key lookups (used by /sync/push upserts)
CREATE INDEX idx_bills_device ON bills (restaurant_id, device_id, local_id);
CREATE INDEX idx_bill_items_device ON bill_items (restaurant_id, device_id, local_id);
CREATE INDEX idx_bill_payments_device ON bill_payments (restaurant_id, device_id, local_id);
CREATE INDEX idx_menuitems_device ON menuitems (restaurant_id, device_id, local_id);

-- Business reporting and analytics lookups
CREATE INDEX idx_bills_daily_order ON bills (restaurant_id, daily_order_id);
CREATE INDEX idx_bills_created_at ON bills (restaurant_id, created_at DESC);
CREATE INDEX idx_bills_order_status ON bills (restaurant_id, order_status);

-- Hardware and session lookups
CREATE INDEX idx_restaurant_terminal_restaurant ON restaurant_terminal (restaurant_id);
CREATE INDEX idx_users_login_id ON users (login_id);
CREATE INDEX idx_users_restaurant_role ON users (restaurant_id, role);
```

---

## 5. Migration Discipline & Data Integrity

1. **Flyway Migrations (`services/server/src/main/resources/db/migration/`):**
   * Currently at version **`V115`**.
   * Forward-only discipline: Existing migration scripts (`V1` through `V115`) MUST NEVER be modified or deleted.
   * New schema changes require a sequentially numbered migration (e.g. `V116__...sql`).
2. **Room Database Migrations (`AppDatabase.kt`):**
   * Version **`82`**. Every schema alteration must define an explicit `Migration(from, to)` block in `AppDatabase.kt` and pass `DatabaseMigrationListConsistencyTest.kt`.
3. **Zero Destruction Guarantee:**
   * Production columns and tables are never dropped. Unused fields are marked `@Deprecated` in entity code and left intact in the database to guarantee backward compatibility with edge terminals.

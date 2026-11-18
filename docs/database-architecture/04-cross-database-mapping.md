# 04 - Cross-Database Entity & Field Mapping Specification
> **KhanaBook POS & SaaS Platform Architecture**  
> **SQLite (Room v84) ↔ PostgreSQL 16 (`kbook_saas` at Flyway V130)**  

---

## 1. Executive Summary & Mapping Principles

The KhanaBook architecture synchronizes business master data between offline Android POS tablets (SQLite) and the central cloud database (PostgreSQL) using the following contract:
1. **Dual Primary Key Architecture:**
   - `local_id` in PostgreSQL stores the client-minted SQLite `id`.
   - `server_id` in SQLite stores the central PostgreSQL `id` returned upon successful sync acknowledgment.
2. **Global Idempotency Anchors:**
   - Every transactional entity carries a UUID `public_token` (for customer receipts and web verification) and a composite `operation_id` (`restaurantId:terminalId:uuid:action`).
3. **Monetary Precision Strategy:**
   - SQLite stores amounts as stringified `TEXT` (e.g. `'149.50'`) to eliminate binary IEEE 754 floating-point inaccuracies.
   - PostgreSQL stores amounts as `NUMERIC(12,2)` with strict database check constraints (`CHECK (total_amount >= 0)`).
4. **Boolean Representation:**
   - SQLite stores booleans as `INTEGER` (`0` or `1`).
   - PostgreSQL stores native `BOOLEAN` (`true` or `false`). Wire mappers in `SyncMapper.java` and `SyncEntityMappers.kt` bridge this seamlessly.
5. **Timestamps & Timezones:**
   - Epoch milliseconds (`BIGINT`) are used across all operational entities (`created_at`, `updated_at`, `server_updated_at`), avoiding timezone string serialization drift.

---

## 2. Shared Entity Cross-Database Mapping Tables

### 2.1 Business Entity: `Orders & Invoices`
- **SQLite Table:** `bills`
- **PostgreSQL Table:** `bills`
- **Sync Direction:** Bidirectional (Client-authoritative creation, Server-authoritative conflict resolution)

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Direct mapping of client identity |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server central row identity returned upon sync push |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant isolation partition key |
| `device_id` | `TEXT` | `device_id` | `VARCHAR(255)` | Hardware device identifier (SSAID / IMEI) |
| `terminal_id` | `TEXT` | `terminal_id` | `VARCHAR(255)` | Terminal pairing UUID |
| `terminal_series` | `TEXT` | `terminal_series` | `VARCHAR(255)` | Assigned series letter ('A'..'E') |
| `created_terminal_id` | `TEXT` | `created_terminal_id` | `VARCHAR(100)` | Originating terminal |
| `current_owner_terminal_id` | `TEXT` | `current_owner_terminal_id` | `VARCHAR(100)` | Active editor terminal for concurrency lock |
| `daily_order_id` | `INTEGER` | `daily_order_id` | `BIGINT` | Bill Number (resets to 1 daily) |
| `daily_order_display` | `TEXT` | `daily_order_display` | `VARCHAR(255)` | Formatted display Bill No (e.g. 'A01') |
| `lifetime_order_id` | `INTEGER` | `lifetime_order_id` | `BIGINT` | Monotonic lifetime Invoice Number sequence |
| `invoice_number` | `TEXT` | `invoice_number` | `VARCHAR(255)` | Formal GST invoice number (e.g. 'INV-A-2026-27-0001') |
| `invoice_series` | `TEXT` | `invoice_series` | `VARCHAR(255)` | Tax fiscal year + terminal series |
| `invoice_sequence` | `INTEGER` | `invoice_sequence` | `BIGINT` | Monotonic counter within invoice_series |
| `financial_year` | `TEXT` | `financial_year` | `VARCHAR(255)` | Fiscal year (e.g. '2627') |
| `order_type` | `TEXT` | `order_type` | `VARCHAR(100)` | DINE_IN, TAKEAWAY, DELIVERY |
| `order_status` | `TEXT` | `order_status` | `VARCHAR(100)` | DRAFT, COMPLETED, CANCELLED |
| `payment_status` | `TEXT` | `payment_status` | `VARCHAR(100)` | PENDING, PAID, PARTIALLY_PAID |
| `payment_mode` | `TEXT` | `payment_mode` | `VARCHAR(100)` | CASH, UPI, POS, PART_CASH_UPI, etc. |
| `customer_name` | `TEXT` | `customer_name` | `VARCHAR(255)` | Customer or Table name |
| `customer_whatsapp` | `TEXT` | `customer_whatsapp` | `VARCHAR(255)` | 10-digit customer mobile |
| `subtotal` | `TEXT` | `subtotal` | `NUMERIC(12,2)` | Amount before taxes |
| `gst_percentage` | `TEXT` | `gst_percentage` | `NUMERIC(12,2)` | Total GST rate (e.g. 5.0) |
| `cgst_amount` | `TEXT` | `cgst_amount` | `NUMERIC(12,2)` | Central GST amount |
| `sgst_amount` | `TEXT` | `sgst_amount` | `NUMERIC(12,2)` | State GST amount |
| `total_amount` | `TEXT` | `total_amount` | `NUMERIC(12,2)` | Final bill amount charged |
| `part_amount_1` | `TEXT` | `part_amount_1` | `NUMERIC(12,2)` | Split payment part 1 amount |
| `part_amount_2` | `TEXT` | `part_amount_2` | `NUMERIC(12,2)` | Split payment part 2 amount |
| `refund_amount` | `TEXT` | `refund_amount` | `NUMERIC(12,2)` | Recorded refund amount |
| `cancel_reason` | `TEXT` | `cancel_reason` | `VARCHAR(500)` | Cancellation justification |
| `public_token` | `TEXT` | `public_token` | `UUID` | Global UUID for receipt sharing |
| `operation_id` | `TEXT` | `operation_id` | `VARCHAR(255)` | Idempotency work key |
| `source_channel` | `TEXT` | `source_channel` | `VARCHAR(100)` | Origin channel (POS, QR_ORDER) |
| `last_reset_date` | `TEXT` | `last_reset_date` | `VARCHAR(50)` | Business date of bill (YYYY-MM-DD) |
| `created_by` | `INTEGER` | `created_by` | `BIGINT` | Staff user local ID |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation epoch millis |
| `paid_at` | `INTEGER` | `paid_at` | `BIGINT` | Payment epoch millis |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Client last modification timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server authoritative modification epoch millis |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion indicator (0/1 vs false/true) |
| `status_version` | `INTEGER` | `status_version` | `INTEGER` | Optimistic concurrency version guard |

---

### 2.2 Business Entity: `Order Items (Line Items)`
- **SQLite Table:** `bill_items`
- **PostgreSQL Table:** `bill_items`
- **Sync Direction:** Push with Bill / Cascaded Pull

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client line item ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server line item identity |
| `bill_id` | `INTEGER` | `bill_id` | `BIGINT` | Client parent bill FK |
| `server_bill_id` | `INTEGER` | `server_bill_id` | `BIGINT` | Server parent bill FK |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `menu_item_id` | `INTEGER` | `menu_item_id` | `BIGINT` | Local dish catalog FK |
| `server_menu_item_id` | `INTEGER` | `server_menu_item_id` | `BIGINT` | Server dish catalog FK |
| `variant_id` | `INTEGER` | `variant_id` | `BIGINT` | Local variant portion FK |
| `server_variant_id` | `INTEGER` | `server_variant_id` | `BIGINT` | Server variant portion FK |
| `item_name` | `TEXT` | `item_name` | `VARCHAR(255)` | Snapshot dish name |
| `variant_name` | `TEXT` | `variant_name` | `VARCHAR(255)` | Snapshot variant name (e.g. 'Full') |
| `quantity` | `INTEGER` | `quantity` | `INTEGER` | Item quantity ordered |
| `price` | `TEXT` | `price` | `NUMERIC(12,2)` | Unit price at time of sale |
| `item_total` | `TEXT` | `item_total` | `NUMERIC(12,2)` | Line total (price * quantity) |
| `special_instruction` | `TEXT` | `special_instruction` | `TEXT` | Kitchen cooking instructions |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---

### 2.3 Business Entity: `Payments`
- **SQLite Table:** `bill_payments`
- **PostgreSQL Table:** `bill_payments`
- **Sync Direction:** Bidirectional (Push from client / Webhook updates pulled from server)

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client payment record ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server payment identity |
| `bill_id` | `INTEGER` | `bill_id` | `BIGINT` | Client parent bill FK |
| `server_bill_id` | `INTEGER` | `server_bill_id` | `BIGINT` | Server parent bill FK |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `payment_mode` | `TEXT` | `payment_mode` | `VARCHAR(50)` | CASH, UPI, POS, ONLINE |
| `amount` | `TEXT` | `amount` | `NUMERIC(12,2)` | Tender amount |
| `operation_id` | `TEXT` | `operation_id` | `VARCHAR(255)` | Idempotency Work Key (UNIQUE) |
| `gateway_txn_id` | `TEXT` | `gateway_txn_id` | `VARCHAR(64)` | Payment gateway transaction reference |
| `gateway_status` | `TEXT` | `gateway_status` | `VARCHAR(32)` | SUCCESS, FAILED, PENDING |
| `verified_by` | `TEXT` | `verified_by` | `VARCHAR(64)` | 'manual' vs 'easebuzz_webhook' |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---

### 2.4 Business Entity: `Menu Categories`
- **SQLite Table:** `categories`
- **PostgreSQL Table:** `categories`
- **Sync Direction:** Bidirectional (Owner edits on POS or Web Admin)

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client category ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server category ID |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `name` | `TEXT` | `name` | `VARCHAR(255)` | Category name (UNIQUE per tenant) |
| `sort_order` | `INTEGER` | `sort_order` | `INTEGER` | Display sort ordering |
| `is_favourite` | `INTEGER` | `is_favourite` | `BOOLEAN` | Pinned category banner toggle |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Client update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---

### 2.5 Business Entity: `Menu Items (Dishes)`
- **SQLite Table:** `menu_items`
- **PostgreSQL Table:** `menuitems`
- **Sync Direction:** Bidirectional

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client dish ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server dish ID |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `category_id` | `INTEGER` | `category_id` | `BIGINT` | Parent category local FK |
| `server_category_id` | `INTEGER` | `server_category_id` | `BIGINT` | Parent category server FK |
| `name` | `TEXT` | `name` | `VARCHAR(255)` | Dish name (UNIQUE per tenant) |
| `base_price` | `TEXT` | `base_price` | `NUMERIC(12,2)` | Base item rate |
| `food_type` | `TEXT` | `food_type` | `VARCHAR(50)` | veg, non-veg |
| `description` | `TEXT` | `description` | `TEXT` | Menu description |
| `is_available` | `INTEGER` | `is_available` | `BOOLEAN` | In-stock / 86'd out toggle |
| `barcode` | `TEXT` | `barcode` | `VARCHAR(255)` | UPC / EAN item barcode for scanner |
| `has_variants` | `INTEGER` | `has_variants` | `BOOLEAN` | Flag indicating multiple portion sizes |
| `is_favourite` | `INTEGER` | `is_favourite` | `BOOLEAN` | Quick-access shortcut dish |
| `image_url` | `TEXT` | `image_url` | `VARCHAR(500)` | S3/CDN photo URL |
| `image_version` | `INTEGER` | `image_version` | `INTEGER` | Cache busting image version |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---

### 2.6 Business Entity: `Item Variants (Portions)`
- **SQLite Table:** `item_variants`
- **PostgreSQL Table:** `itemvariants`
- **Sync Direction:** Bidirectional

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client variant ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server variant ID |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `menu_item_id` | `INTEGER` | `menu_item_id` | `BIGINT` | Parent dish local FK |
| `server_menu_item_id` | `INTEGER` | `server_menu_item_id` | `BIGINT` | Parent dish server FK |
| `variant_name` | `TEXT` | `variant_name` | `VARCHAR(255)` | Portion name (e.g. 'Half', 'Full') |
| `price` | `TEXT` | `price` | `NUMERIC(12,2)` | Specific portion rate |
| `is_available` | `INTEGER` | `is_available` | `BOOLEAN` | Portion availability |
| `sort_order` | `INTEGER` | `sort_order` | `INTEGER` | Display sort index |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---

### 2.7 Business Entity: `Restaurant Profile & Store Settings`
- **SQLite Table:** `restaurant_profile`
- **PostgreSQL Table:** `restaurantprofiles`
- **Sync Direction:** Bidirectional (Owner-restricted)

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK (Singleton)` | `restaurant_id` | `BIGINT PK (Singleton)` | Tenant Snowflake Identity |
| `shop_name` | `TEXT` | `shop_name` | `VARCHAR(255)` | Store business name |
| `shop_address` | `TEXT` | `shop_address` | `VARCHAR(500)` | Physical restaurant address |
| `whatsapp_number` | `TEXT` | `whatsapp_number` | `VARCHAR(20)` | Store WhatsApp number |
| `email` | `TEXT` | `email` | `VARCHAR(255)` | Store owner email |
| `logo_url` | `TEXT` | `logo_url` | `VARCHAR(500)` | Store logo asset URL |
| `logo_version` | `INTEGER` | `logo_version` | `INTEGER` | Logo cache bust counter |
| `fssai_number` | `TEXT` | `fssai_number` | `VARCHAR(14)` | 14-digit FSSAI license |
| `gst_enabled` | `INTEGER` | `gst_enabled` | `BOOLEAN` | GST registration toggle |
| `gstin` | `TEXT` | `gstin` | `VARCHAR(15)` | 15-character GSTIN |
| `gst_percentage` | `REAL` | `gst_percentage` | `NUMERIC(5,2)` | Store-wide GST rate |
| `is_tax_inclusive` | `INTEGER` | `is_tax_inclusive` | `BOOLEAN` | Tax inclusive pricing toggle |
| `business_day_cutoff_time` | `TEXT` | `business_day_cutoff_time` | `TIME` | EOD rollover time (e.g. 04:00:00) |
| `opening_time` | `TEXT` | `opening_time` | `TIME` | Operating opening time |
| `closing_time` | `TEXT` | `closing_time` | `TIME` | Operating closing time |
| `order_payment_flow_mode` | `TEXT` | `order_payment_flow_mode` | `VARCHAR(50)` | pay_before_food / pay_after_food |
| `invoice_footer` | `TEXT` | `invoice_footer` | `TEXT` | Footer text printed on receipts |
| `review_url` | `TEXT` | `review_url` | `VARCHAR(500)` | Google Maps review QR link |
| `email_daily_closing_report` | `INTEGER` | `email_daily_closing_report` | `BOOLEAN` | EOD report email trigger |
| `daily_closing_email_recipients` | `TEXT` | `daily_closing_email_recipients` | `TEXT` | Recipient emails |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |

---

### 2.8 Business Entity: `Users & Staff`
- **SQLite Table:** `users`
- **PostgreSQL Table:** `users`
- **Sync Direction:** Server-authoritative pull / Local profile update

| SQLite Column | SQLite Type | PostgreSQL Column | PostgreSQL Type | Compatibility & Architectural Notes |
|---|---|---|---|---|
| `id` | `INTEGER PK` | `local_id` | `BIGINT` | Client user ID |
| `server_id` | `INTEGER` | `id` | `BIGINT PK` | Server user ID |
| `restaurant_id` | `INTEGER` | `restaurant_id` | `BIGINT` | Tenant partition key |
| `name` | `TEXT` | `name` | `VARCHAR(255)` | Staff name |
| `email` | `TEXT` | `email` | `VARCHAR(255)` | Staff email (login username) |
| `phone_number` | `TEXT` | `phone_number` | `VARCHAR(20)` | Staff phone |
| `role` | `TEXT` | `role` | `VARCHAR(50)` | OWNER, SHOP_STAFF, KBOOK_ADMIN |
| `is_active` | `INTEGER` | `is_active` | `BOOLEAN` | Account active toggle |
| `created_at` | `INTEGER` | `created_at` | `BIGINT` | Creation timestamp |
| `updated_at` | `INTEGER` | `updated_at` | `BIGINT` | Update timestamp |
| `server_updated_at` | `INTEGER` | `server_updated_at` | `BIGINT` | Server sync timestamp |
| `is_deleted` | `INTEGER` | `is_deleted` | `BOOLEAN` | Soft deletion flag |

---


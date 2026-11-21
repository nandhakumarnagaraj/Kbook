# 03 - PostgreSQL Complete Schema & Column-Level Specification
> **KhanaBook SaaS Cloud Database (`kbook_saas` at Flyway V130)**  
> **Engine:** PostgreSQL 16.15 | Multi-Tenant Architecture (`restaurant_id` Isolation)  
> **Total Tables:** 25 | **Total Columns:** 393  

---

## Table: `bill_items`
- **Total Columns:** 22
- **Primary Key:** `id`
- **Foreign Keys:** 4 (restaurant_id -> `itemvariants.restaurant_id` (RESTRICT), bill_id -> `bills.restaurant_id` (CASCADE), server_menu_item_id -> `menuitems.restaurant_id` (RESTRICT), server_variant_id -> `itemvariants.restaurant_id` (RESTRICT))
- **Indexes:** 10 (`bill_items_restaurant_id_device_id_local_id_key` (UNIQUE), `idx_bill_items_bill_id`, `idx_bill_items_device`, `idx_bill_items_server_bill`, `idx_bill_items_server_bill_updated`, `idx_bill_items_server_menuitem`, `idx_bill_items_server_variant`, `idx_bill_items_tenant_deleted`, `idx_bill_items_tenant_server_updated`, `idx_bill_items_tenant_updated`)
- **Check Constraints:** `chk_bill_items_price`: `((price >= (0)::numeric))`, `chk_bill_items_quantity`: `((quantity > 0))`, `chk_bill_items_total`: `((item_total >= (0)::numeric))`, `2200_41951_1_not_null`: `id IS NOT NULL`, `2200_41951_2_not_null`: `local_id IS NOT NULL`, `2200_41951_3_not_null`: `device_id IS NOT NULL`, `2200_41951_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41951_5_not_null`: `updated_at IS NOT NULL`, `2200_41951_6_not_null`: `is_deleted IS NOT NULL`, `2200_41951_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41951_8_not_null`: `created_at IS NOT NULL`, `2200_41951_9_not_null`: `bill_id IS NOT NULL`, `2200_41951_13_not_null`: `item_name IS NOT NULL`, `2200_41951_17_not_null`: `price IS NOT NULL`, `2200_41951_18_not_null`: `quantity IS NOT NULL`, `2200_41951_19_not_null`: `item_total IS NOT NULL`, `2200_41951_21_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | `itemvariants.restaurant_id` (RESTRICT) | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `bill_id` | bigint | NO | NO | `bills.restaurant_id` (CASCADE) | `-` | Bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_bill_id` | bigint | YES | NO | - | `-` | Server bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `menu_item_id` | bigint | YES | NO | - | `-` | Menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_menu_item_id` | bigint | YES | NO | `menuitems.restaurant_id` (RESTRICT) | `-` | Server menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `item_name` | varchar(255) | NO | NO | - | `-` | Item name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_id` | bigint | YES | NO | - | `-` | Variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_variant_id` | bigint | YES | NO | `itemvariants.restaurant_id` (RESTRICT) | `-` | Server variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_name` | varchar(255) | YES | NO | - | `-` | Variant name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `price` | numeric(12,2) | NO | NO | - | `-` | Price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `quantity` | integer | NO | NO | - | `-` | Quantity. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `item_total` | numeric(12,2) | NO | NO | - | `-` | Item total. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `special_instruction` | varchar(500) | YES | NO | - | `-` | Special instruction. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `bill_payments`
- **Total Columns:** 18
- **Primary Key:** `id`
- **Foreign Keys:** 2 (restaurant_id -> `bills.restaurant_id` (CASCADE), bill_id -> `bills.restaurant_id` (CASCADE))
- **Indexes:** 10 (`bill_payments_restaurant_id_device_id_local_id_key` (UNIQUE), `idx_bill_payments_bill_id`, `idx_bill_payments_device`, `idx_bill_payments_server_bill`, `idx_bill_payments_server_bill_updated`, `idx_bill_payments_tenant_deleted`, `idx_bill_payments_tenant_server_updated`, `idx_bill_payments_tenant_updated`, `uq_bill_payments_gateway_txn` (UNIQUE), `uq_bill_payments_restaurant_operation` (UNIQUE))
- **Check Constraints:** `chk_bill_payments_amount`: `((amount >= (0)::numeric))`, `chk_bill_payment_mode`: `(((payment_mode)::text = ANY ((ARRAY['cash'::character varying, 'upi'::character varying, 'easebuzz'::character varying, 'pos'::character varying, 'part_cash_upi'::character varying, 'part_cash_pos'::character varying, 'part_upi_pos'::character varying, 'part_cash_easebuzz'::character varying, 'part_easebuzz_pos'::character varying, 'zomato'::character varying, 'swiggy'::character varying, 'own_website'::character varying])::text[])))`, `2200_41967_1_not_null`: `id IS NOT NULL`, `2200_41967_2_not_null`: `local_id IS NOT NULL`, `2200_41967_3_not_null`: `device_id IS NOT NULL`, `2200_41967_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41967_5_not_null`: `updated_at IS NOT NULL`, `2200_41967_6_not_null`: `is_deleted IS NOT NULL`, `2200_41967_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41967_8_not_null`: `created_at IS NOT NULL`, `2200_41967_9_not_null`: `bill_id IS NOT NULL`, `2200_41967_11_not_null`: `payment_mode IS NOT NULL`, `2200_41967_12_not_null`: `amount IS NOT NULL`, `2200_41967_13_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | `bills.restaurant_id` (CASCADE) | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `bill_id` | bigint | NO | NO | `bills.restaurant_id` (CASCADE) | `-` | Bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_bill_id` | bigint | YES | NO | - | `-` | Server bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `payment_mode` | varchar(100) | NO | NO | - | `-` | Payment mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `amount` | numeric(12,2) | NO | NO | - | `-` | Amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gateway_txn_id` | varchar(64) | YES | NO | - | `-` | Gateway txn id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gateway_status` | varchar(32) | YES | NO | - | `-` | Gateway status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `verified_by` | varchar(16) | YES | NO | - | `'manual'::character varying` | Verified by. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `operation_id` | varchar(255) | YES | NO | - | `-` | Operation id. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `bills`
- **Total Columns:** 43
- **Primary Key:** `id`
- **Indexes:** 18 (`bills_restaurant_id_device_id_local_id_key` (UNIQUE), `idx_bills_device`, `idx_bills_owner_visibility`, `idx_bills_public_token_unique` (UNIQUE), `idx_bills_restaurant_terminal_updated`, `idx_bills_tenant_created`, `idx_bills_tenant_daily_order`, `idx_bills_tenant_date`, `idx_bills_tenant_deleted`, `idx_bills_tenant_payment_status`, `idx_bills_tenant_server_updated`, `idx_bills_tenant_status`, `idx_bills_tenant_updated`, `idx_bills_terminal_visibility`, `ux_bills_public_token` (UNIQUE), `ux_bills_restaurant_invoice_series_active` (UNIQUE), `ux_bills_restaurant_terminal_daily_active` (UNIQUE), `ux_bills_tenant_id` (UNIQUE))
- **Check Constraints:** `chk_bills_subtotal`: `((subtotal >= (0)::numeric))`, `chk_bills_total`: `((total_amount >= (0)::numeric))`, `2200_41935_1_not_null`: `id IS NOT NULL`, `2200_41935_2_not_null`: `local_id IS NOT NULL`, `2200_41935_3_not_null`: `device_id IS NOT NULL`, `2200_41935_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41935_5_not_null`: `updated_at IS NOT NULL`, `2200_41935_6_not_null`: `is_deleted IS NOT NULL`, `2200_41935_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41935_8_not_null`: `created_at IS NOT NULL`, `2200_41935_9_not_null`: `daily_order_id IS NOT NULL`, `2200_41935_12_not_null`: `order_type IS NOT NULL`, `2200_41935_15_not_null`: `subtotal IS NOT NULL`, `2200_41935_20_not_null`: `total_amount IS NOT NULL`, `2200_41935_21_not_null`: `payment_mode IS NOT NULL`, `2200_41935_24_not_null`: `payment_status IS NOT NULL`, `2200_41935_25_not_null`: `order_status IS NOT NULL`, `2200_41935_28_not_null`: `last_reset_date IS NOT NULL`, `2200_41935_29_not_null`: `version IS NOT NULL`, `2200_41935_30_not_null`: `cancel_reason IS NOT NULL`, `2200_41935_31_not_null`: `refund_amount IS NOT NULL`, `2200_41935_32_not_null`: `public_token IS NOT NULL`, `2200_41935_33_not_null`: `source_channel IS NOT NULL`, `2200_41935_51_not_null`: `inventory_deducted IS NOT NULL`, `2200_41935_52_not_null`: `status_version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_id` | bigint | NO | NO | - | `-` | Daily order id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_display` | varchar(255) | YES | NO | - | `-` | Daily order display. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `lifetime_order_id` | bigint | YES | NO | - | `-` | Lifetime order id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `order_type` | varchar(100) | NO | NO | - | `-` | Order type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `customer_name` | varchar(255) | YES | NO | - | `-` | Customer name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `customer_whatsapp` | varchar(255) | YES | NO | - | `-` | Customer whatsapp. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `subtotal` | numeric(12,2) | NO | NO | - | `-` | Subtotal. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gst_percentage` | numeric(12,2) | YES | NO | - | `-` | Gst percentage. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `cgst_amount` | numeric(12,2) | YES | NO | - | `-` | Cgst amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `sgst_amount` | numeric(12,2) | YES | NO | - | `-` | Sgst amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `total_amount` | numeric(12,2) | NO | NO | - | `-` | Total amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `payment_mode` | varchar(100) | NO | NO | - | `-` | Payment mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `part_amount_1` | numeric(12,2) | YES | NO | - | `-` | Part amount 1. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `part_amount_2` | numeric(12,2) | YES | NO | - | `-` | Part amount 2. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `payment_status` | varchar(100) | NO | NO | - | `-` | Payment status. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `order_status` | varchar(100) | NO | NO | - | `-` | Order status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_by` | bigint | YES | NO | - | `-` | Created by. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `paid_at` | bigint | YES | NO | - | `-` | Paid at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_reset_date` | varchar(50) | NO | NO | - | `-` | Last reset date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cancel_reason` | varchar(500) | NO | NO | - | `''::character varying` | Cancel reason. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `refund_amount` | numeric(12,2) | NO | NO | - | `0` | Refund amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `public_token` | uuid | NO | NO | - | `gen_random_uuid()` | Public token. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `source_channel` | varchar(100) | NO | NO | - | `''::character varying` | Source channel. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_series` | varchar(255) | YES | NO | - | `-` | Terminal series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `financial_year` | varchar(255) | YES | NO | - | `-` | Financial year. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_series` | varchar(255) | YES | NO | - | `-` | Invoice series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_sequence` | bigint | YES | NO | - | `-` | Invoice sequence. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_number` | varchar(255) | YES | NO | - | `-` | Invoice number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_terminal_id` | varchar(100) | YES | NO | - | `-` | Created terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_device_id` | varchar(100) | YES | NO | - | `-` | Created device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `current_owner_terminal_id` | varchar(100) | YES | NO | - | `-` | Current owner terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `inventory_deducted` | boolean | NO | NO | - | `false` | Inventory deducted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `status_version` | integer | NO | NO | - | `0` | Status version. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `cashier_shifts`
- **Total Columns:** 26
- **Primary Key:** `id`
- **Indexes:** 4 (`idx_cashier_shifts_restaurant_date`, `uq_cashier_shifts_device_local` (UNIQUE), `ux_cashier_shifts_one_active_per_terminal` (UNIQUE), `ux_cashier_shifts_public_token` (UNIQUE))
- **Check Constraints:** `2200_222082_1_not_null`: `id IS NOT NULL`, `2200_222082_2_not_null`: `local_id IS NOT NULL`, `2200_222082_3_not_null`: `device_id IS NOT NULL`, `2200_222082_4_not_null`: `restaurant_id IS NOT NULL`, `2200_222082_5_not_null`: `terminal_id IS NOT NULL`, `2200_222082_9_not_null`: `business_date IS NOT NULL`, `2200_222082_10_not_null`: `opened_at IS NOT NULL`, `2200_222082_12_not_null`: `opening_float IS NOT NULL`, `2200_222082_18_not_null`: `status IS NOT NULL`, `2200_222082_21_not_null`: `public_token IS NOT NULL`, `2200_222082_25_not_null`: `is_deleted IS NOT NULL`, `2200_222082_26_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | NO | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_series` | varchar(255) | YES | NO | - | `-` | Terminal series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cashier_user_id` | bigint | YES | NO | - | `-` | Cashier user id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cashier_name` | varchar(255) | YES | NO | - | `-` | Cashier name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `business_date` | date | NO | NO | - | `-` | Business date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opened_at` | bigint | NO | NO | - | `-` | Opened at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `closed_at` | bigint | YES | NO | - | `-` | Closed at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opening_float` | numeric(12,2) | NO | NO | - | `0` | Opening float. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `declared_cash` | numeric(12,2) | YES | NO | - | `-` | Declared cash. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `expected_cash` | numeric(12,2) | YES | NO | - | `-` | Expected cash. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cash_variance` | numeric(12,2) | YES | NO | - | `-` | Cash variance. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `total_revenue` | numeric(12,2) | YES | NO | - | `-` | Total revenue. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `total_orders` | integer | YES | NO | - | `-` | Total orders. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `status` | varchar(30) | NO | NO | - | `'ACTIVE'::character varying` | Status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `close_source` | varchar(30) | YES | NO | - | `-` | Close source. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `snapshot_json` | text | YES | NO | - | `-` | Snapshot json. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `public_token` | uuid | NO | NO | - | `gen_random_uuid()` | Public token. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `created_at` | bigint | YES | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | YES | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | YES | NO | - | `-` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `categories`
- **Total Columns:** 15
- **Primary Key:** `id`
- **Indexes:** 5 (`idx_categories_device`, `idx_categories_tenant_deleted`, `idx_categories_tenant_server_updated`, `idx_categories_tenant_updated`, `ux_categories_tenant_id` (UNIQUE))
- **Check Constraints:** `2200_41896_1_not_null`: `id IS NOT NULL`, `2200_41896_2_not_null`: `local_id IS NOT NULL`, `2200_41896_3_not_null`: `device_id IS NOT NULL`, `2200_41896_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41896_5_not_null`: `updated_at IS NOT NULL`, `2200_41896_6_not_null`: `is_deleted IS NOT NULL`, `2200_41896_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41896_8_not_null`: `created_at IS NOT NULL`, `2200_41896_9_not_null`: `name IS NOT NULL`, `2200_41896_10_not_null`: `is_veg IS NOT NULL`, `2200_41896_12_not_null`: `is_active IS NOT NULL`, `2200_41896_13_not_null`: `version IS NOT NULL`, `2200_41896_15_not_null`: `is_favourite IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | varchar(255) | NO | NO | - | `-` | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_veg` | boolean | NO | NO | - | `false` | Is veg. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sort_order` | integer | YES | NO | - | `-` | Sort order. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_active` | boolean | NO | NO | - | `true` | Is active. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_favourite` | boolean | NO | NO | - | `false` | Is favourite. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `device_registration_request`
- **Total Columns:** 18
- **Primary Key:** `id`
- **Indexes:** 3 (`idx_device_request_restaurant_requested`, `idx_device_request_restaurant_status`, `ux_device_request_pending` (UNIQUE))
- **Check Constraints:** `2200_42372_1_not_null`: `id IS NOT NULL`, `2200_42372_2_not_null`: `restaurant_id IS NOT NULL`, `2200_42372_3_not_null`: `requested_by_user_id IS NOT NULL`, `2200_42372_4_not_null`: `device_id IS NOT NULL`, `2200_42372_7_not_null`: `request_type IS NOT NULL`, `2200_42372_8_not_null`: `status IS NOT NULL`, `2200_42372_10_not_null`: `requested_at IS NOT NULL`, `2200_42372_15_not_null`: `created_at IS NOT NULL`, `2200_42372_18_not_null`: `challenge_attempts IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('device_registration_request_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `requested_by_user_id` | bigint | NO | NO | - | `-` | Requested by user id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_model` | varchar(255) | YES | NO | - | `-` | Device model. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_name` | varchar(255) | YES | NO | - | `-` | Device name. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `request_type` | varchar(50) | NO | NO | - | `'NEW_DEVICE'::character varying` | Request type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `status` | varchar(50) | NO | NO | - | `'PENDING'::character varying` | Status. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `matched_terminal_id` | bigint | YES | NO | - | `-` | Matched terminal id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `requested_at` | bigint | NO | NO | - | `-` | Requested at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `processed_at` | bigint | YES | NO | - | `-` | Processed at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `processed_by_user_id` | bigint | YES | NO | - | `-` | Processed by user id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `rejection_reason` | varchar(500) | YES | NO | - | `-` | Rejection reason. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `assigned_terminal_id` | bigint | YES | NO | - | `-` | Assigned terminal id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `((EXTRACT(epoch FROM now()) * (1000)::numeric))::bigint` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `challenge_code` | varchar(8) | YES | NO | - | `-` | Challenge code. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `challenge_expires_at` | bigint | YES | NO | - | `-` | Challenge expires at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `challenge_attempts` | integer | NO | NO | - | `0` | Challenge attempts. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `device_tokens`
- **Total Columns:** 9
- **Primary Key:** `id`
- **Indexes:** 3 (`idx_device_tokens_restaurant`, `idx_device_tokens_restaurant_user`, `idx_device_tokens_token`)
- **Check Constraints:** `2200_66012_1_not_null`: `id IS NOT NULL`, `2200_66012_2_not_null`: `restaurant_id IS NOT NULL`, `2200_66012_3_not_null`: `token IS NOT NULL`, `2200_66012_6_not_null`: `active IS NOT NULL`, `2200_66012_7_not_null`: `created_at IS NOT NULL`, `2200_66012_8_not_null`: `updated_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('device_tokens_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `token` | varchar(500) | NO | NO | - | `-` | Token. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: Yes] |
| `platform` | varchar(20) | YES | NO | - | `'android'::character varying` | Platform. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | YES | NO | - | `-` | Device id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `active` | boolean | NO | NO | - | `true` | Active. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `user_id` | bigint | YES | NO | - | `-` | User id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `email_outbox`
- **Total Columns:** 16
- **Primary Key:** `id`
- **Indexes:** 2 (`idx_email_outbox_pending`, `uq_email_outbox_revision` (UNIQUE))
- **Check Constraints:** `2200_222100_1_not_null`: `id IS NOT NULL`, `2200_222100_2_not_null`: `restaurant_id IS NOT NULL`, `2200_222100_3_not_null`: `report_type IS NOT NULL`, `2200_222100_4_not_null`: `reference_key IS NOT NULL`, `2200_222100_5_not_null`: `revision IS NOT NULL`, `2200_222100_6_not_null`: `recipient IS NOT NULL`, `2200_222100_7_not_null`: `subject IS NOT NULL`, `2200_222100_8_not_null`: `html_body IS NOT NULL`, `2200_222100_9_not_null`: `status IS NOT NULL`, `2200_222100_10_not_null`: `attempt_count IS NOT NULL`, `2200_222100_11_not_null`: `max_attempts IS NOT NULL`, `2200_222100_14_not_null`: `created_at IS NOT NULL`, `2200_222100_15_not_null`: `updated_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `report_type` | varchar(50) | NO | NO | - | `-` | Report type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `reference_key` | varchar(255) | NO | NO | - | `-` | Reference key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `revision` | integer | NO | NO | - | `1` | Revision. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `recipient` | text | NO | NO | - | `-` | Recipient. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `subject` | text | NO | NO | - | `-` | Subject. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `html_body` | text | NO | NO | - | `-` | Html body. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `status` | varchar(30) | NO | NO | - | `'PENDING'::character varying` | Status. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `attempt_count` | integer | NO | NO | - | `0` | Attempt count. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `max_attempts` | integer | NO | NO | - | `5` | Max attempts. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `next_attempt_at` | bigint | YES | NO | - | `-` | Next attempt at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `last_error` | text | YES | NO | - | `-` | Last error. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `sent_at` | bigint | YES | NO | - | `-` | Sent at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `feature_flag`
- **Total Columns:** 6
- **Primary Key:** `flag_key`
- **Check Constraints:** `2200_65964_1_not_null`: `flag_key IS NOT NULL`, `2200_65964_2_not_null`: `kill_switched IS NOT NULL`, `2200_65964_3_not_null`: `default_enabled IS NOT NULL`, `2200_65964_5_not_null`: `created_at IS NOT NULL`, `2200_65964_6_not_null`: `updated_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `flag_key` | varchar(64) | NO | YES | - | `-` | Flag key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `kill_switched` | boolean | NO | NO | - | `true` | Kill switched. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `default_enabled` | boolean | NO | NO | - | `false` | Default enabled. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `description` | varchar(255) | YES | NO | - | `-` | Description. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `feature_flag_audit`
- **Total Columns:** 9
- **Primary Key:** `id`
- **Indexes:** 1 (`idx_feature_flag_audit_flag`)
- **Check Constraints:** `2200_65987_1_not_null`: `id IS NOT NULL`, `2200_65987_2_not_null`: `flag_key IS NOT NULL`, `2200_65987_3_not_null`: `scope IS NOT NULL`, `2200_65987_6_not_null`: `new_state IS NOT NULL`, `2200_65987_9_not_null`: `changed_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('feature_flag_audit_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `flag_key` | varchar(64) | NO | NO | - | `-` | Flag key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `scope` | varchar(16) | NO | NO | - | `-` | Scope. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | YES | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `previous_state` | varchar(16) | YES | NO | - | `-` | Previous state. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `new_state` | varchar(16) | NO | NO | - | `-` | New state. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `actor_user_id` | bigint | YES | NO | - | `-` | Actor user id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `actor_username` | varchar(255) | YES | NO | - | `-` | Actor username. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `changed_at` | bigint | NO | NO | - | `-` | Changed at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `feature_flag_override`
- **Total Columns:** 6
- **Primary Key:** `id`
- **Foreign Keys:** 1 (flag_key -> `feature_flag.flag_key` (NO ACTION))
- **Indexes:** 2 (`idx_feature_flag_override_restaurant`, `uq_feature_flag_override` (UNIQUE))
- **Check Constraints:** `2200_65972_1_not_null`: `id IS NOT NULL`, `2200_65972_2_not_null`: `flag_key IS NOT NULL`, `2200_65972_3_not_null`: `restaurant_id IS NOT NULL`, `2200_65972_4_not_null`: `enabled IS NOT NULL`, `2200_65972_5_not_null`: `created_at IS NOT NULL`, `2200_65972_6_not_null`: `updated_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('feature_flag_override_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `flag_key` | varchar(64) | NO | NO | `feature_flag.flag_key` (NO ACTION) | `-` | Flag key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `enabled` | boolean | NO | NO | - | `-` | Enabled. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `flyway_schema_history`
- **Total Columns:** 10
- **Primary Key:** `installed_rank`
- **Indexes:** 1 (`flyway_schema_history_s_idx`)
- **Check Constraints:** `2200_41856_1_not_null`: `installed_rank IS NOT NULL`, `2200_41856_3_not_null`: `description IS NOT NULL`, `2200_41856_4_not_null`: `type IS NOT NULL`, `2200_41856_5_not_null`: `script IS NOT NULL`, `2200_41856_7_not_null`: `installed_by IS NOT NULL`, `2200_41856_8_not_null`: `installed_on IS NOT NULL`, `2200_41856_9_not_null`: `execution_time IS NOT NULL`, `2200_41856_10_not_null`: `success IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `installed_rank` | integer | NO | YES | - | `-` | Installed rank. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `version` | varchar(50) | YES | NO | - | `-` | Version. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `description` | varchar(200) | NO | NO | - | `-` | Description. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `type` | varchar(20) | NO | NO | - | `-` | Type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `script` | varchar(1000) | NO | NO | - | `-` | Script. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `checksum` | integer | YES | NO | - | `-` | Checksum. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `installed_by` | varchar(100) | NO | NO | - | `-` | Installed by. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `installed_on` | timestamp without time zone | NO | NO | - | `now()` | Installed on. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `execution_time` | integer | NO | NO | - | `-` | Execution time. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `success` | boolean | NO | NO | - | `-` | Success. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `itemvariants`
- **Total Columns:** 18
- **Primary Key:** `id`
- **Foreign Keys:** 2 (restaurant_id -> `menuitems.restaurant_id` (RESTRICT), server_menu_item_id -> `menuitems.restaurant_id` (RESTRICT))
- **Indexes:** 6 (`idx_itemvariants_device`, `idx_itemvariants_server_menuitem`, `idx_itemvariants_tenant_deleted`, `idx_itemvariants_tenant_server_updated`, `idx_itemvariants_tenant_updated`, `ux_itemvariants_tenant_id` (UNIQUE))
- **Check Constraints:** `chk_itemvariants_price`: `((price >= (0)::numeric))`, `2200_41922_1_not_null`: `id IS NOT NULL`, `2200_41922_2_not_null`: `local_id IS NOT NULL`, `2200_41922_3_not_null`: `device_id IS NOT NULL`, `2200_41922_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41922_5_not_null`: `updated_at IS NOT NULL`, `2200_41922_6_not_null`: `is_deleted IS NOT NULL`, `2200_41922_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41922_8_not_null`: `created_at IS NOT NULL`, `2200_41922_9_not_null`: `menu_item_id IS NOT NULL`, `2200_41922_11_not_null`: `variant_name IS NOT NULL`, `2200_41922_12_not_null`: `price IS NOT NULL`, `2200_41922_13_not_null`: `is_available IS NOT NULL`, `2200_41922_17_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | `menuitems.restaurant_id` (RESTRICT) | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `menu_item_id` | bigint | NO | NO | - | `-` | Menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_menu_item_id` | bigint | YES | NO | `menuitems.restaurant_id` (RESTRICT) | `-` | Server menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_name` | varchar(255) | NO | NO | - | `-` | Variant name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `price` | numeric(12,2) | NO | NO | - | `-` | Price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `is_available` | boolean | NO | NO | - | `true` | Is available. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sort_order` | integer | YES | NO | - | `-` | Sort order. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `current_stock` | numeric(12,4) | YES | NO | - | `-` | Current stock. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `low_stock_threshold` | numeric(12,4) | YES | NO | - | `-` | Low stock threshold. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `menu_extraction_jobs`
- **Total Columns:** 8
- **Primary Key:** `id`
- **Indexes:** 2 (`idx_menu_extraction_jobs_restaurant`, `idx_menu_extraction_jobs_status`)
- **Check Constraints:** `2200_41991_1_not_null`: `id IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | YES | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `file_path` | varchar(500) | YES | NO | - | `-` | File path. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `status` | varchar(50) | YES | NO | - | `-` | Status. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `extracted_data_json` | text | YES | NO | - | `-` | Extracted data json. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | timestamp without time zone | YES | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `completed_at` | timestamp without time zone | YES | NO | - | `-` | Completed at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `error_message` | text | YES | NO | - | `-` | Error message. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `menuitems`
- **Total Columns:** 25
- **Primary Key:** `id`
- **Foreign Keys:** 2 (restaurant_id -> `categories.restaurant_id` (RESTRICT), server_category_id -> `categories.restaurant_id` (RESTRICT))
- **Indexes:** 11 (`idx_menu_items_category`, `idx_menuitems_device`, `idx_menuitems_has_variants`, `idx_menuitems_image`, `idx_menuitems_server_category`, `idx_menuitems_tenant_category_fav`, `idx_menuitems_tenant_deleted`, `idx_menuitems_tenant_server_updated`, `idx_menuitems_tenant_updated`, `ux_menuitems_restaurant_category_name_norm` (UNIQUE), `ux_menuitems_tenant_id` (UNIQUE))
- **Check Constraints:** `chk_menuitems_price`: `((base_price >= (0)::numeric))`, `2200_41909_1_not_null`: `id IS NOT NULL`, `2200_41909_2_not_null`: `local_id IS NOT NULL`, `2200_41909_3_not_null`: `device_id IS NOT NULL`, `2200_41909_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41909_5_not_null`: `updated_at IS NOT NULL`, `2200_41909_6_not_null`: `is_deleted IS NOT NULL`, `2200_41909_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41909_8_not_null`: `created_at IS NOT NULL`, `2200_41909_9_not_null`: `category_id IS NOT NULL`, `2200_41909_11_not_null`: `name IS NOT NULL`, `2200_41909_12_not_null`: `base_price IS NOT NULL`, `2200_41909_15_not_null`: `is_available IS NOT NULL`, `2200_41909_19_not_null`: `version IS NOT NULL`, `2200_41909_23_not_null`: `image_version IS NOT NULL`, `2200_41909_24_not_null`: `has_variants IS NOT NULL`, `2200_41909_25_not_null`: `is_favourite IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | `categories.restaurant_id` (RESTRICT) | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `category_id` | bigint | NO | NO | - | `-` | Category id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_category_id` | bigint | YES | NO | `categories.restaurant_id` (RESTRICT) | `-` | Server category id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | varchar(255) | NO | NO | - | `-` | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `base_price` | numeric(12,2) | NO | NO | - | `-` | Base price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `food_type` | varchar(50) | YES | NO | - | `-` | Food type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `description` | text | YES | NO | - | `-` | Description. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_available` | boolean | NO | NO | - | `true` | Is available. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `current_stock` | numeric(12,4) | YES | NO | - | `-` | Current stock. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `low_stock_threshold` | numeric(12,4) | YES | NO | - | `-` | Low stock threshold. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `barcode` | varchar(255) | YES | NO | - | `-` | Barcode. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `permission_revision_at_creation` | bigint | YES | NO | - | `-` | Permission revision at creation. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `image_url` | varchar(512) | YES | NO | - | `-` | Image url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `image_version` | integer | NO | NO | - | `0` | Image version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `has_variants` | boolean | NO | NO | - | `false` | Has variants. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_favourite` | boolean | NO | NO | - | `false` | Is favourite. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `notification_events`
- **Total Columns:** 12
- **Primary Key:** `id`
- **Indexes:** 3 (`idx_notification_events_read`, `idx_notification_events_restaurant`, `idx_notification_events_type`)
- **Check Constraints:** `2200_66025_1_not_null`: `id IS NOT NULL`, `2200_66025_2_not_null`: `restaurant_id IS NOT NULL`, `2200_66025_3_not_null`: `notification_type IS NOT NULL`, `2200_66025_4_not_null`: `title IS NOT NULL`, `2200_66025_9_not_null`: `is_read IS NOT NULL`, `2200_66025_10_not_null`: `is_pushed IS NOT NULL`, `2200_66025_11_not_null`: `created_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('notification_events_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `notification_type` | varchar(50) | NO | NO | - | `-` | Notification type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `title` | varchar(200) | NO | NO | - | `-` | Title. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `message` | text | YES | NO | - | `-` | Message. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `reference_id` | varchar(255) | YES | NO | - | `-` | Reference id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `reference_type` | varchar(50) | YES | NO | - | `-` | Reference type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `amount` | numeric(12,2) | YES | NO | - | `-` | Amount. [Sync: NO (Cloud Only) \| Fin: Yes \| Sensitive: No] |
| `is_read` | boolean | NO | NO | - | `false` | Is read. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `is_pushed` | boolean | NO | NO | - | `false` | Is pushed. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `read_at` | bigint | YES | NO | - | `-` | Read at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `otp_requests`
- **Total Columns:** 7
- **Primary Key:** `id`
- **Indexes:** 3 (`idx_otp_requests_expires_at`, `idx_otp_requests_phone`, `ux_otp_requests_challenge_key` (UNIQUE))
- **Check Constraints:** `2200_41999_1_not_null`: `id IS NOT NULL`, `2200_41999_2_not_null`: `challenge_key IS NOT NULL`, `2200_41999_3_not_null`: `phone_number IS NOT NULL`, `2200_41999_4_not_null`: `otp IS NOT NULL`, `2200_41999_5_not_null`: `expires_at IS NOT NULL`, `2200_41999_6_not_null`: `attempts IS NOT NULL`, `2200_41999_7_not_null`: `created_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `challenge_key` | varchar(255) | NO | NO | - | `-` | Challenge key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `phone_number` | varchar(255) | NO | NO | - | `-` | Phone number. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `otp` | varchar(60) | NO | NO | - | `-` | Otp. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `expires_at` | bigint | NO | NO | - | `-` | Expires at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `attempts` | integer | NO | NO | - | `0` | Attempts. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `rate_limit_attempts`
- **Total Columns:** 5
- **Primary Key:** `id`
- **Indexes:** 2 (`idx_rate_limit_attempts_cleanup`, `idx_rate_limit_attempts_lookup`)
- **Check Constraints:** `2200_57775_1_not_null`: `id IS NOT NULL`, `2200_57775_2_not_null`: `rate_key IS NOT NULL`, `2200_57775_3_not_null`: `action_type IS NOT NULL`, `2200_57775_4_not_null`: `attempted_at IS NOT NULL`, `2200_57775_5_not_null`: `expires_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('rate_limit_attempts_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `rate_key` | varchar(255) | NO | NO | - | `-` | Rate key. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `action_type` | varchar(50) | NO | NO | - | `-` | Action type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `attempted_at` | timestamp without time zone | NO | NO | - | `now()` | Attempted at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `expires_at` | timestamp without time zone | NO | NO | - | `-` | Expires at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `refresh_tokens`
- **Total Columns:** 8
- **Primary Key:** `id`
- **Foreign Keys:** 1 (user_id -> `users.id` (CASCADE))
- **Indexes:** 3 (`idx_refresh_tokens_expires_at`, `idx_refresh_tokens_token_hash`, `idx_refresh_tokens_user_id`)
- **Check Constraints:** `2200_66103_1_not_null`: `id IS NOT NULL`, `2200_66103_2_not_null`: `token_hash IS NOT NULL`, `2200_66103_3_not_null`: `user_id IS NOT NULL`, `2200_66103_4_not_null`: `restaurant_id IS NOT NULL`, `2200_66103_5_not_null`: `expires_at IS NOT NULL`, `2200_66103_6_not_null`: `created_at IS NOT NULL`, `2200_66103_8_not_null`: `revoked IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `token_hash` | varchar(64) | NO | NO | - | `-` | Token hash. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: Yes] |
| `user_id` | bigint | NO | NO | `users.id` (CASCADE) | `-` | User id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `expires_at` | bigint | NO | NO | - | `-` | Expires at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_fingerprint` | varchar(255) | YES | NO | - | `-` | Device fingerprint. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `revoked` | boolean | NO | NO | - | `false` | Revoked. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `restaurant_terminal`
- **Total Columns:** 14
- **Primary Key:** `id`
- **Indexes:** 6 (`idx_restaurant_terminal_last_seen`, `idx_restaurant_terminal_restaurant`, `idx_restaurant_terminal_status`, `ux_restaurant_terminal_device` (UNIQUE), `ux_restaurant_terminal_primary` (UNIQUE), `ux_restaurant_terminal_series` (UNIQUE))
- **Check Constraints:** `2200_42327_1_not_null`: `id IS NOT NULL`, `2200_42327_2_not_null`: `restaurant_id IS NOT NULL`, `2200_42327_3_not_null`: `terminal_series IS NOT NULL`, `2200_42327_8_not_null`: `is_active IS NOT NULL`, `2200_42327_9_not_null`: `status IS NOT NULL`, `2200_42327_10_not_null`: `credential_version IS NOT NULL`, `2200_42327_12_not_null`: `is_primary IS NOT NULL`, `2200_42327_13_not_null`: `terminal_type IS NOT NULL`, `2200_42327_14_not_null`: `cutoff_capable IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `terminal_series` | varchar(255) | NO | NO | - | `-` | Terminal series. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | YES | NO | - | `-` | Device id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | YES | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | YES | NO | - | `-` | Updated at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `terminal_name` | varchar(255) | YES | NO | - | `-` | Terminal name. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `is_active` | boolean | NO | NO | - | `true` | Is active. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `status` | varchar(50) | NO | NO | - | `'ACTIVE'::character varying` | Status. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `credential_version` | bigint | NO | NO | - | `1` | Credential version. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `last_seen_at` | bigint | YES | NO | - | `-` | Last seen at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `is_primary` | boolean | NO | NO | - | `false` | Is primary. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `terminal_type` | varchar(20) | NO | NO | - | `'BILLING'::character varying` | Terminal type. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `cutoff_capable` | boolean | NO | NO | - | `false` | Cutoff capable. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `restaurantprofiles`
- **Total Columns:** 43
- **Primary Key:** `id`
- **Indexes:** 6 (`idx_restaurantprofiles_device`, `idx_restaurantprofiles_suspended`, `idx_restaurantprofiles_tenant_updated`, `idx_restaurantprofiles_whatsapp_number`, `restaurantprofiles_restaurant_id_device_id_local_id_key` (UNIQUE), `uq_restaurantprofiles_restaurant_id` (UNIQUE))
- **Check Constraints:** `2200_41880_1_not_null`: `id IS NOT NULL`, `2200_41880_2_not_null`: `local_id IS NOT NULL`, `2200_41880_3_not_null`: `device_id IS NOT NULL`, `2200_41880_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41880_5_not_null`: `updated_at IS NOT NULL`, `2200_41880_6_not_null`: `is_deleted IS NOT NULL`, `2200_41880_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41880_8_not_null`: `created_at IS NOT NULL`, `2200_41880_49_not_null`: `version IS NOT NULL`, `2200_41880_50_not_null`: `last_reset_date_proper IS NOT NULL`, `2200_41880_61_not_null`: `logo_version IS NOT NULL`, `2200_41880_71_not_null`: `order_payment_flow_mode IS NOT NULL`, `2200_41880_73_not_null`: `is_suspended IS NOT NULL`, `2200_41880_80_not_null`: `opening_time IS NOT NULL`, `2200_41880_81_not_null`: `closing_time IS NOT NULL`, `2200_41880_82_not_null`: `business_day_cutoff_time IS NOT NULL`, `2200_41880_84_not_null`: `email_daily_closing_report IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `shop_name` | varchar(255) | YES | NO | - | `-` | Shop name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `shop_address` | varchar(255) | YES | NO | - | `-` | Shop address. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `whatsapp_number` | varchar(255) | YES | NO | - | `-` | Whatsapp number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email` | varchar(255) | YES | NO | - | `-` | Email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `fssai_number` | varchar(255) | YES | NO | - | `-` | Fssai number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gst_enabled` | boolean | YES | NO | - | `-` | Gst enabled. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gstin` | varchar(255) | YES | NO | - | `-` | Gstin. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `is_tax_inclusive` | boolean | YES | NO | - | `-` | Is tax inclusive. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gst_percentage` | numeric(12,2) | YES | NO | - | `-` | Gst percentage. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `currency` | varchar(50) | YES | NO | - | `-` | Currency. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_enabled` | boolean | YES | NO | - | `-` | Upi enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_handle` | varchar(255) | YES | NO | - | `-` | Upi handle. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cash_enabled` | boolean | YES | NO | - | `-` | Cash enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `pos_enabled` | boolean | YES | NO | - | `-` | Pos enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_counter` | bigint | YES | NO | - | `-` | Daily order counter. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `lifetime_order_counter` | bigint | YES | NO | - | `-` | Lifetime order counter. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_reset_date` | varchar(50) | YES | NO | - | `-` | Last reset date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `session_timeout_minutes` | integer | YES | NO | - | `-` | Session timeout minutes. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `timezone` | varchar(100) | YES | NO | - | `'Asia/Kolkata'::character varying` | Timezone. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `review_url` | varchar(500) | YES | NO | - | `-` | Review url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_reset_date_proper` | date | NO | NO | - | `CURRENT_DATE` | Last reset date proper. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `logo_url` | varchar(500) | YES | NO | - | `-` | Logo url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `logo_version` | integer | NO | NO | - | `0` | Logo version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_footer` | text | YES | NO | - | `-` | Invoice footer. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `order_payment_flow_mode` | varchar(32) | NO | NO | - | `'pay_before_food'::character varying` | Order payment flow mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_suspended` | boolean | NO | NO | - | `false` | Is suspended. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `field_revisions` | text | YES | NO | - | `-` | Field revisions. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opening_time` | time without time zone | NO | NO | - | `'08:00:00'::time without time zone` | Opening time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `closing_time` | time without time zone | NO | NO | - | `'23:00:00'::time without time zone` | Closing time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `business_day_cutoff_time` | time without time zone | NO | NO | - | `'00:00:00'::time without time zone` | Business day cutoff time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cutoff_effective_from` | bigint | YES | NO | - | `-` | Cutoff effective from. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email_daily_closing_report` | boolean | NO | NO | - | `false` | Email daily closing report. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_closing_email_recipients` | text | YES | NO | - | `-` | Daily closing email recipients. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `security_audit_log`
- **Total Columns:** 11
- **Primary Key:** `id`
- **Indexes:** 1 (`idx_security_audit_restaurant`)
- **Check Constraints:** `2200_42359_1_not_null`: `id IS NOT NULL`, `2200_42359_8_not_null`: `action IS NOT NULL`, `2200_42359_9_not_null`: `outcome IS NOT NULL`, `2200_42359_11_not_null`: `created_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `nextval('security_audit_log_id_seq'::regclass)` | Id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | YES | NO | - | `-` | Restaurant id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `user_id` | varchar(255) | YES | NO | - | `-` | User id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(100) | YES | NO | - | `-` | Terminal id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `terminal_series` | varchar(100) | YES | NO | - | `-` | Terminal series. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `target_canonical_id` | varchar(255) | YES | NO | - | `-` | Target canonical id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `target_owner_terminal` | varchar(100) | YES | NO | - | `-` | Target owner terminal. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `action` | varchar(100) | NO | NO | - | `-` | Action. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `outcome` | varchar(100) | NO | NO | - | `-` | Outcome. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `request_id` | varchar(100) | YES | NO | - | `-` | Request id. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `stock_logs`
- **Total Columns:** 16
- **Primary Key:** `id`
- **Foreign Keys:** 3 (restaurant_id -> `itemvariants.restaurant_id` (RESTRICT), server_menu_item_id -> `menuitems.restaurant_id` (RESTRICT), server_variant_id -> `itemvariants.restaurant_id` (RESTRICT))
- **Indexes:** 6 (`idx_stock_logs_device`, `idx_stock_logs_server_menuitem`, `idx_stock_logs_server_variant`, `idx_stock_logs_tenant_deleted`, `idx_stock_logs_tenant_server_updated`, `idx_stock_logs_tenant_updated`)
- **Check Constraints:** `2200_41980_1_not_null`: `id IS NOT NULL`, `2200_41980_2_not_null`: `local_id IS NOT NULL`, `2200_41980_3_not_null`: `device_id IS NOT NULL`, `2200_41980_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41980_5_not_null`: `updated_at IS NOT NULL`, `2200_41980_6_not_null`: `is_deleted IS NOT NULL`, `2200_41980_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41980_8_not_null`: `created_at IS NOT NULL`, `2200_41980_9_not_null`: `menu_item_id IS NOT NULL`, `2200_41980_13_not_null`: `delta IS NOT NULL`, `2200_41980_14_not_null`: `reason IS NOT NULL`, `2200_41980_15_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | `itemvariants.restaurant_id` (RESTRICT) | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `menu_item_id` | bigint | NO | NO | - | `-` | Menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_menu_item_id` | bigint | YES | NO | `menuitems.restaurant_id` (RESTRICT) | `-` | Server menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_id` | bigint | YES | NO | - | `-` | Variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_variant_id` | bigint | YES | NO | `itemvariants.restaurant_id` (RESTRICT) | `-` | Server variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `delta` | numeric(12,4) | NO | NO | - | `-` | Delta. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `reason` | varchar(255) | NO | NO | - | `-` | Reason. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `token_blocklist`
- **Total Columns:** 3
- **Primary Key:** `jti`
- **Indexes:** 1 (`idx_token_blocklist_expires`)
- **Check Constraints:** `2200_42005_1_not_null`: `jti IS NOT NULL`, `2200_42005_2_not_null`: `expires_at IS NOT NULL`, `2200_42005_3_not_null`: `revoked_at IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `jti` | varchar(64) | NO | YES | - | `-` | Jti. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `expires_at` | bigint | NO | NO | - | `-` | Expires at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |
| `revoked_at` | bigint | NO | NO | - | `-` | Revoked at. [Sync: NO (Cloud Only) \| Fin: No \| Sensitive: No] |

---

## Table: `users`
- **Total Columns:** 25
- **Primary Key:** `id`
- **Indexes:** 13 (`idx_users_device`, `idx_users_google_email`, `idx_users_last_seen`, `idx_users_phone_number` (UNIQUE), `idx_users_restaurant_active`, `idx_users_tenant_server_updated`, `idx_users_tenant_updated`, `idx_users_whatsapp_number`, `uk_users_email` (UNIQUE), `ux_users_google_email_ci` (UNIQUE), `ux_users_login_id_ci` (UNIQUE), `ux_users_phone_digits` (UNIQUE), `ux_users_whatsapp_number` (UNIQUE))
- **Check Constraints:** `chk_users_auth_provider`: `(((auth_provider)::text = ANY ((ARRAY['PHONE'::character varying, 'GOOGLE'::character varying])::text[])))`, `2200_41866_1_not_null`: `id IS NOT NULL`, `2200_41866_2_not_null`: `local_id IS NOT NULL`, `2200_41866_3_not_null`: `device_id IS NOT NULL`, `2200_41866_4_not_null`: `restaurant_id IS NOT NULL`, `2200_41866_5_not_null`: `updated_at IS NOT NULL`, `2200_41866_6_not_null`: `is_deleted IS NOT NULL`, `2200_41866_7_not_null`: `server_updated_at IS NOT NULL`, `2200_41866_8_not_null`: `created_at IS NOT NULL`, `2200_41866_9_not_null`: `name IS NOT NULL`, `2200_41866_11_not_null`: `login_id IS NOT NULL`, `2200_41866_13_not_null`: `auth_provider IS NOT NULL`, `2200_41866_16_not_null`: `role IS NOT NULL`, `2200_41866_17_not_null`: `is_active IS NOT NULL`, `2200_41866_18_not_null`: `version IS NOT NULL`

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Purpose & Attributes |
|---|---|:---:|:---:|:---:|---|---|
| `id` | bigint | NO | YES | - | `-` | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_id` | bigint | NO | NO | - | `-` | Local id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | varchar(255) | NO | NO | - | `-` | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | bigint | NO | NO | - | `-` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `updated_at` | bigint | NO | NO | - | `-` | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | boolean | NO | NO | - | `false` | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | bigint | NO | NO | - | `0` | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | bigint | NO | NO | - | `-` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | varchar(255) | NO | NO | - | `-` | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email` | varchar(255) | YES | NO | - | `-` | Email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `login_id` | varchar(255) | NO | NO | - | `-` | Login id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `google_email` | varchar(255) | YES | NO | - | `-` | Google email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `auth_provider` | varchar(20) | NO | NO | - | `-` | Auth provider. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `password_hash` | varchar(255) | YES | NO | - | `-` | Password hash. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `whatsapp_number` | varchar(255) | YES | NO | - | `-` | Whatsapp number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `role` | varchar(50) | NO | NO | - | `'OWNER'::character varying` | Role. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_active` | boolean | NO | NO | - | `true` | Is active. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | bigint | NO | NO | - | `0` | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `phone_number` | varchar(255) | YES | NO | - | `-` | Phone number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `token_invalidated_at` | bigint | YES | NO | - | `-` | Token invalidated at. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `terminal_id` | varchar(255) | YES | NO | - | `-` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `pin_hash` | varchar(255) | YES | NO | - | `-` | Pin hash. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `pin_set_at` | bigint | YES | NO | - | `-` | Pin set at. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `phone_digits` | varchar(10) | YES | NO | - | `-` | Phone digits. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_seen_at` | bigint | YES | NO | - | `-` | Last seen at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---


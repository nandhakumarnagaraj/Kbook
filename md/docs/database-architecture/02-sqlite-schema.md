# 02 - SQLite Complete Schema & Column-Level Specification
> **KhanaBook POS Android Local Room Database (v84)**  
> **Encryption:** Encrypted via SQLCipher (`khanabook_lite_db_$restaurantId`)  
> **Total Tables:** 18 | **Total Columns:** 349  

---

## Table: `bill_items`
- **Total Columns:** 21
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Foreign Keys:** 3 configured (bills, menu_items, item_variants)
- **Indices:** 3 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `bill_id` | INTEGER | NO | NO | `bills.id` | `-` | `index_bill_items_bill_id` | Bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `menu_item_id` | INTEGER | YES | NO | `menu_items.id` | `-` | `index_bill_items_menu_item_id` | Menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `item_name` | TEXT | NO | NO | - | `-` | - | Item name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_id` | INTEGER | YES | NO | `item_variants.id` | `-` | `index_bill_items_variant_id` | Variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_name` | TEXT | YES | NO | - | `-` | - | Variant name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `price` | TEXT | NO | NO | - | `-` | - | Price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `quantity` | INTEGER | NO | NO | - | `-` | - | Quantity. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `item_total` | TEXT | NO | NO | - | `-` | - | Item total. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `special_instruction` | TEXT | YES | NO | - | `-` | - | Special instruction. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sent_to_kot` | INTEGER | NO | NO | - | `0` | - | Sent to kot. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_bill_id` | INTEGER | YES | NO | - | `-` | - | Server bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_menu_item_id` | INTEGER | YES | NO | - | `-` | - | Server menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_variant_id` | INTEGER | YES | NO | - | `-` | - | Server variant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `bill_payments`
- **Total Columns:** 21
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Foreign Keys:** 1 configured (bills)
- **Indices:** 2 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `bill_id` | INTEGER | NO | NO | `bills.id` | `-` | `index_bill_payments_bill_id` | Bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `payment_mode` | TEXT | NO | NO | - | `-` | - | Payment mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `amount` | TEXT | NO | NO | - | `-` | - | Amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `0` | - | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `idx_bill_payments_restaurant_operation` (UNIQUE) | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | TEXT | YES | NO | - | `NULL` | - | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `bill_public_token` | TEXT | YES | NO | - | `NULL` | - | Bill public token. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `operation_id` | TEXT | YES | NO | - | `NULL` | `idx_bill_payments_restaurant_operation` (UNIQUE) | Operation id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sync_status` | TEXT | NO | NO | - | `'pending'` | - | Sync status. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_bill_id` | INTEGER | YES | NO | - | `-` | - | Server bill id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gateway_txn_id` | TEXT | YES | NO | - | `-` | - | Gateway txn id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gateway_status` | TEXT | YES | NO | - | `-` | - | Gateway status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `verified_by` | TEXT | NO | NO | - | `'manual'` | - | Verified by. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | INTEGER | NO | NO | - | `0` | - | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `bills`
- **Total Columns:** 56
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Foreign Keys:** 1 configured (users)
- **Indices:** 11 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | TEXT | YES | NO | - | `NULL` | `index_bills_restaurant_id_terminal_id_created_at` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_terminal_id` | TEXT | YES | NO | - | `NULL` | `index_bills_terminal_origin_scope` | Created terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_device_id` | TEXT | YES | NO | - | `NULL` | - | Created device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_id` | INTEGER | NO | NO | - | `-` | `index_bills_daily_order_id` | Daily order id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_display` | TEXT | NO | NO | - | `-` | - | Daily order display. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `lifetime_order_id` | INTEGER | YES | NO | - | `-` | - | Lifetime order id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `order_type` | TEXT | NO | NO | - | `'order'` | - | Order type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `customer_name` | TEXT | YES | NO | - | `-` | - | Customer name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `customer_whatsapp` | TEXT | YES | NO | - | `-` | - | Customer whatsapp. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `subtotal` | TEXT | NO | NO | - | `-` | - | Subtotal. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gst_percentage` | TEXT | NO | NO | - | `'0.0'` | - | Gst percentage. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `cgst_amount` | TEXT | NO | NO | - | `'0.0'` | - | Cgst amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `sgst_amount` | TEXT | NO | NO | - | `'0.0'` | - | Sgst amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `custom_tax_amount` | TEXT | NO | NO | - | `'0.0'` | - | Custom tax amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `total_amount` | TEXT | NO | NO | - | `-` | - | Total amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `payment_mode` | TEXT | NO | NO | - | `-` | - | Payment mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `source_channel` | TEXT | NO | NO | - | `''` | - | Source channel. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `part_amount_1` | TEXT | NO | NO | - | `'0.0'` | - | Part amount 1. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `part_amount_2` | TEXT | NO | NO | - | `'0.0'` | - | Part amount 2. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `payment_status` | TEXT | NO | NO | - | `-` | - | Payment status. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `order_status` | TEXT | NO | NO | - | `-` | `index_bills_order_status` | Order status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `status_version` | INTEGER | NO | NO | - | `0` | - | Status version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_by` | INTEGER | YES | NO | `users.id` | `-` | `index_bills_created_by` | Created by. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_by_user_id` | INTEGER | YES | NO | - | `NULL` | - | Created by user id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | `index_bills_restaurant_id_terminal_id_created_at` | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `paid_at` | INTEGER | YES | NO | - | `-` | - | Paid at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_reset_date` | TEXT | NO | NO | - | `''` | - | Last reset date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `-` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cancel_reason` | TEXT | NO | NO | - | `''` | - | Cancel reason. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `public_token` | TEXT | YES | NO | - | `-` | `index_bills_restaurant_public_token` (UNIQUE) | Public token. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `owner_user_id` | INTEGER | YES | NO | - | `NULL` | - | Owner user id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `owner_restaurant_id` | INTEGER | YES | NO | - | `NULL` | - | Owner restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sync_status` | TEXT | NO | NO | - | `'pending'` | - | Sync status. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `sync_failure_reason` | TEXT | YES | NO | - | `-` | - | Sync failure reason. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `sync_failed_at` | INTEGER | YES | NO | - | `-` | - | Sync failed at. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `terminal_series` | TEXT | YES | NO | - | `NULL` | - | Terminal series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `financial_year` | TEXT | YES | NO | - | `NULL` | `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` | Financial year. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_series` | TEXT | YES | NO | - | `NULL` | `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` | Invoice series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_sequence` | INTEGER | YES | NO | - | `NULL` | `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` | Invoice sequence. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_number` | TEXT | YES | NO | - | `NULL` | - | Invoice number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `refund_amount` | TEXT | YES | NO | - | `NULL` | - | Refund amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `current_owner_terminal_id` | TEXT | YES | NO | - | `NULL` | - | Current owner terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `version` | INTEGER | NO | NO | - | `0` | - | Version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `lock_status` | TEXT | NO | NO | - | `'unlocked'` | - | Lock status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `operation_id` | TEXT | YES | NO | - | `NULL` | - | Operation id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `record_origin` | TEXT | NO | NO | - | `'local_created'` | `index_bills_terminal_origin_scope` | Record origin. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `record_scope` | TEXT | NO | NO | - | `'terminal_operational'` | `index_bills_terminal_origin_scope` | Record scope. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `payment_attempt_status` | TEXT | NO | NO | - | `'none'` | `index_bills_payment_attempt_status` | Payment attempt status. [Sync: NO (Local State Machine) \| Fin: Yes \| Sensitive: No] |
| `payment_attempt_started_at` | INTEGER | YES | NO | - | `NULL` | - | Payment attempt started at. [Sync: Yes \| Fin: Yes \| Sensitive: No] |

---

## Table: `categories`
- **Total Columns:** 14
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 1 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | TEXT | NO | NO | - | `-` | `index_categories_restaurant_id_name` (UNIQUE) | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_veg` | INTEGER | NO | NO | - | `-` | - | Is veg. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sort_order` | INTEGER | NO | NO | - | `0` | - | Sort order. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_active` | INTEGER | NO | NO | - | `1` | - | Is active. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_favourite` | INTEGER | NO | NO | - | `0` | - | Is favourite. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_categories_restaurant_id_name` (UNIQUE) | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `item_variants`
- **Total Columns:** 16
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Foreign Keys:** 1 configured (menu_items)
- **Indices:** 1 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `menu_item_id` | INTEGER | NO | NO | `menu_items.id` | `-` | `index_item_variants_menu_item_id` | Menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `variant_name` | TEXT | NO | NO | - | `-` | - | Variant name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `price` | TEXT | NO | NO | - | `-` | - | Price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `is_available` | INTEGER | NO | NO | - | `1` | - | Is available. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `sort_order` | INTEGER | NO | NO | - | `0` | - | Sort order. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `current_stock` | TEXT | NO | NO | - | `'0.0'` | - | Current stock. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `low_stock_threshold` | TEXT | NO | NO | - | `'10.0'` | - | Low stock threshold. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_menu_item_id` | INTEGER | YES | NO | - | `-` | - | Server menu item id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `kitchen_print_queue`
- **Total Columns:** 16
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 1 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `bill_id` | INTEGER | NO | NO | - | `-` | `index_kitchen_print_queue_bill_id_printer_mac` (UNIQUE) | Bill id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `terminal_id` | TEXT | YES | NO | - | `NULL` | - | Terminal id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | YES | NO | - | `NULL` | - | Device id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `printer_mac` | TEXT | NO | NO | - | `-` | `index_kitchen_print_queue_bill_id_printer_mac` (UNIQUE) | Printer mac. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `attempts` | INTEGER | NO | NO | - | `-` | - | Attempts. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `last_error` | TEXT | YES | NO | - | `-` | - | Last error. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `dispatch_status` | TEXT | NO | NO | - | `-` | - | Dispatch status. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `last_attempt_at` | INTEGER | YES | NO | - | `-` | - | Last attempt at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `public_token` | TEXT | YES | NO | - | `-` | - | Public token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `bill_public_token` | TEXT | YES | NO | - | `NULL` | - | Bill public token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `print_event_token` | TEXT | YES | NO | - | `NULL` | - | Print event token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `kot_revision` | TEXT | YES | NO | - | `-` | - | Kot revision. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `-` | - | Updated at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `kot_events`
- **Total Columns:** 12
- **Primary Key:** `public_token, kot_revision` (AutoGenerate: `False`)
- **Indices:** 2 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `public_token` | TEXT | NO | YES | - | `-` | `index_kot_events_public_token` | Public token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `kot_revision` | TEXT | NO | YES | - | `-` | - | Kot revision. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `event_type` | TEXT | NO | NO | - | `-` | - | Event type. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `item_snapshot_json` | TEXT | NO | NO | - | `-` | - | Item snapshot json. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `originating_device_id` | TEXT | NO | NO | - | `-` | `index_kot_events_originating_device_id` | Originating device id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `origin_terminal_id` | TEXT | YES | NO | - | `NULL` | - | Origin terminal id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `origin_device_id` | TEXT | YES | NO | - | `NULL` | - | Origin device id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `event_token` | TEXT | YES | NO | - | `NULL` | - | Event token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `bill_public_token` | TEXT | YES | NO | - | `NULL` | - | Bill public token. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: Yes] |
| `event_version` | INTEGER | NO | NO | - | `0` | - | Event version. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `is_printed` | INTEGER | NO | NO | - | `-` | - | Is printed. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `local_cashier_shifts`
- **Total Columns:** 25
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 3 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_local_cashier_shifts_restaurant_id_business_date` | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_id` | TEXT | NO | NO | - | `-` | `index_local_cashier_shifts_restaurant_id_terminal_id_status` | Terminal id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `terminal_series` | TEXT | YES | NO | - | `-` | - | Terminal series. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cashier_user_id` | INTEGER | YES | NO | - | `-` | - | Cashier user id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cashier_name` | TEXT | YES | NO | - | `-` | - | Cashier name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `business_date` | TEXT | NO | NO | - | `-` | `index_local_cashier_shifts_restaurant_id_business_date` | Business date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opened_at` | INTEGER | NO | NO | - | `-` | - | Opened at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `closed_at` | INTEGER | YES | NO | - | `-` | - | Closed at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opening_float` | TEXT | NO | NO | - | `'0.00'` | - | Opening float. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `declared_cash` | TEXT | YES | NO | - | `-` | - | Declared cash. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `expected_cash` | TEXT | YES | NO | - | `-` | - | Expected cash. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cash_variance` | TEXT | YES | NO | - | `-` | - | Cash variance. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `total_revenue` | TEXT | YES | NO | - | `-` | - | Total revenue. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `total_orders` | INTEGER | YES | NO | - | `-` | - | Total orders. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `status` | TEXT | NO | NO | - | `'ACTIVE'` | `index_local_cashier_shifts_restaurant_id_terminal_id_status` | Status. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `close_source` | TEXT | YES | NO | - | `-` | - | Close source. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `snapshot_json` | TEXT | YES | NO | - | `-` | - | Snapshot json. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `public_token` | TEXT | NO | NO | - | `-` | `index_local_cashier_shifts_public_token` (UNIQUE) | Public token. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `menu_item_category_repairs`
- **Total Columns:** 5
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 1 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_menu_item_category_repairs_restaurant_id_item_local_id` (UNIQUE) | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `item_local_id` | INTEGER | NO | NO | - | `-` | `index_menu_item_category_repairs_restaurant_id_item_local_id` (UNIQUE) | Item local id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `destination_server_category_id` | INTEGER | NO | NO | - | `-` | - | Destination server category id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `0` | - | Created at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `menu_items`
- **Total Columns:** 26
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Foreign Keys:** 1 configured (categories)
- **Indices:** 2 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `category_id` | INTEGER | NO | NO | `categories.id` | `-` | `index_menu_items_category_id` | Category id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | TEXT | NO | NO | - | `-` | - | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `base_price` | TEXT | NO | NO | - | `-` | - | Base price. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `food_type` | TEXT | NO | NO | - | `'veg'` | - | Food type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `description` | TEXT | YES | NO | - | `-` | - | Description. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_available` | INTEGER | NO | NO | - | `1` | `index_menu_items_is_available` | Is available. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `current_stock` | TEXT | NO | NO | - | `'0.0'` | - | Current stock. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `low_stock_threshold` | TEXT | NO | NO | - | `'10.0'` | - | Low stock threshold. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `barcode` | TEXT | YES | NO | - | `NULL` | - | Barcode. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `image_url` | TEXT | YES | NO | - | `-` | - | Image url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `image_version` | INTEGER | NO | NO | - | `0` | - | Image version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `has_variants` | INTEGER | NO | NO | - | `0` | - | Has variants. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_category_id` | INTEGER | YES | NO | - | `-` | - | Server category id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `permission_revision_at_creation` | INTEGER | YES | NO | - | `-` | - | Permission revision at creation. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `changed_fields` | TEXT | YES | NO | - | `-` | - | Changed fields. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_favourite` | INTEGER | NO | NO | - | `0` | - | Is favourite. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `local_favourite` | INTEGER | NO | NO | - | `0` | - | Local favourite. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `notifications`
- **Total Columns:** 10
- **Primary Key:** `id` (AutoGenerate: `False`)

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | NO | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `notification_type` | TEXT | NO | NO | - | `-` | - | Notification type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `title` | TEXT | NO | NO | - | `-` | - | Title. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `message` | TEXT | YES | NO | - | `-` | - | Message. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `reference_id` | TEXT | YES | NO | - | `-` | - | Reference id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `reference_type` | TEXT | YES | NO | - | `-` | - | Reference type. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `amount` | TEXT | YES | NO | - | `-` | - | Amount. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `is_read` | INTEGER | NO | NO | - | `-` | - | Is read. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `permission_cache`
- **Total Columns:** 4
- **Primary Key:** `user_id` (AutoGenerate: `False`)

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `user_id` | INTEGER | NO | YES | - | `-` | - | User id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `granted_csv` | TEXT | NO | NO | - | `-` | - | Granted csv. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `permission_revision` | INTEGER | NO | NO | - | `-` | - | Permission revision. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `-` | - | Updated at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `printer_profiles`
- **Total Columns:** 15
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 1 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `role` | TEXT | NO | NO | - | `-` | `index_printer_profiles_restaurant_id_role` (UNIQUE) | Role. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_printer_profiles_restaurant_id_role` (UNIQUE) | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `name` | TEXT | NO | NO | - | `-` | - | Name. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `mac_address` | TEXT | NO | NO | - | `-` | - | Mac address. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `connection_type` | TEXT | NO | NO | - | `'BLUETOOTH'` | - | Connection type. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `host` | TEXT | YES | NO | - | `-` | - | Host. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `port` | INTEGER | NO | NO | - | `9100` | - | Port. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `enabled` | INTEGER | NO | NO | - | `-` | - | Enabled. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `auto_print` | INTEGER | NO | NO | - | `-` | - | Auto print. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `paper_size` | TEXT | NO | NO | - | `-` | - | Paper size. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `include_logo` | INTEGER | NO | NO | - | `-` | - | Include logo. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `copies` | INTEGER | NO | NO | - | `-` | - | Copies. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `-` | - | Updated at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `restaurant_profile`
- **Total Columns:** 65
- **Primary Key:** `id` (AutoGenerate: `False`)

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `shop_name` | TEXT | YES | NO | - | `-` | - | Shop name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `shop_address` | TEXT | YES | NO | - | `-` | - | Shop address. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `whatsapp_number` | TEXT | YES | NO | - | `-` | - | Whatsapp number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email` | TEXT | YES | NO | - | `-` | - | Email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `logo_path` | TEXT | YES | NO | - | `-` | - | Logo path. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `logo_url` | TEXT | YES | NO | - | `-` | - | Logo url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `logo_version` | INTEGER | NO | NO | - | `0` | - | Logo version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `fssai_number` | TEXT | YES | NO | - | `-` | - | Fssai number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `fssai_expiry_date` | TEXT | YES | NO | - | `-` | - | Fssai expiry date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email_invoice_consent` | INTEGER | NO | NO | - | `0` | - | Email invoice consent. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `country` | TEXT | YES | NO | - | `'India'` | - | Country. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `gst_enabled` | INTEGER | NO | NO | - | `0` | - | Gst enabled. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gstin` | TEXT | YES | NO | - | `-` | - | Gstin. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `is_tax_inclusive` | INTEGER | NO | NO | - | `0` | - | Is tax inclusive. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `gst_percentage` | REAL | NO | NO | - | `0.0` | - | Gst percentage. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `custom_tax_name` | TEXT | YES | NO | - | `-` | - | Custom tax name. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `custom_tax_number` | TEXT | YES | NO | - | `-` | - | Custom tax number. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `custom_tax_percentage` | REAL | NO | NO | - | `0.0` | - | Custom tax percentage. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `currency` | TEXT | YES | NO | - | `'INR'` | - | Currency. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_enabled` | INTEGER | NO | NO | - | `0` | - | Upi enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_qr_path` | TEXT | YES | NO | - | `-` | - | Upi qr path. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_qr_url` | TEXT | YES | NO | - | `-` | - | Upi qr url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_qr_version` | INTEGER | NO | NO | - | `0` | - | Upi qr version. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_handle` | TEXT | YES | NO | - | `-` | - | Upi handle. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `upi_mobile` | TEXT | YES | NO | - | `-` | - | Upi mobile. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cash_enabled` | INTEGER | NO | NO | - | `1` | - | Cash enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `pos_enabled` | INTEGER | NO | NO | - | `0` | - | Pos enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `easebuzz_enabled` | INTEGER | NO | NO | - | `0` | - | Easebuzz enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `printer_enabled` | INTEGER | NO | NO | - | `0` | - | Printer enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `printer_name` | TEXT | YES | NO | - | `-` | - | Printer name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `printer_mac` | TEXT | YES | NO | - | `-` | - | Printer mac. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `paper_size` | TEXT | NO | NO | - | `'58mm'` | - | Paper size. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `auto_print_on_success` | INTEGER | NO | NO | - | `0` | - | Auto print on success. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `include_logo_in_print` | INTEGER | NO | NO | - | `1` | - | Include logo in print. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `printer_mode` | TEXT | NO | NO | - | `'DUAL'` | - | Printer mode. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `print_customer_whatsapp` | INTEGER | NO | NO | - | `1` | - | Print customer whatsapp. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `kitchen_printer_enabled` | INTEGER | NO | NO | - | `0` | - | Kitchen printer enabled. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `kitchen_printer_name` | TEXT | YES | NO | - | `-` | - | Kitchen printer name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `kitchen_printer_mac` | TEXT | YES | NO | - | `-` | - | Kitchen printer mac. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `kitchen_printer_paper_size` | TEXT | NO | NO | - | `'58mm'` | - | Kitchen printer paper size. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `daily_order_counter` | INTEGER | NO | NO | - | `0` | - | Daily order counter. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `lifetime_order_counter` | INTEGER | NO | NO | - | `0` | - | Lifetime order counter. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `last_reset_date` | TEXT | YES | NO | - | `-` | - | Last reset date. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `session_timeout_minutes` | INTEGER | NO | NO | - | `30` | - | Session timeout minutes. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `order_payment_flow_mode` | TEXT | NO | NO | - | `'pay_before_food'` | - | Order payment flow mode. [Sync: Yes \| Fin: Yes \| Sensitive: No] |
| `collect_customer_number` | INTEGER | NO | NO | - | `1` | - | Collect customer number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `timezone` | TEXT | YES | NO | - | `'Asia/Kolkata'` | - | Timezone. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `review_url` | TEXT | YES | NO | - | `-` | - | Review url. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `invoice_footer` | TEXT | YES | NO | - | `-` | - | Invoice footer. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `show_branding` | INTEGER | NO | NO | - | `1` | - | Show branding. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `mask_customer_phone` | INTEGER | NO | NO | - | `1` | - | Mask customer phone. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `changed_fields` | TEXT | YES | NO | - | `-` | - | Changed fields. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `opening_time` | TEXT | NO | NO | - | `'08:00'` | - | Opening time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `closing_time` | TEXT | NO | NO | - | `'23:00'` | - | Closing time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `business_day_cutoff_time` | TEXT | NO | NO | - | `'00:00'` | - | Business day cutoff time. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `cutoff_effective_from` | INTEGER | YES | NO | - | `-` | - | Cutoff effective from. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email_daily_closing_report` | INTEGER | NO | NO | - | `0` | - | Email daily closing report. [Sync: Yes \| Fin: No \| Sensitive: No] |

---

## Table: `staff_permissions`
- **Total Columns:** 7
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 2 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `-` | `index_staff_permissions_restaurant_id_user_id_permission_key` (UNIQUE) | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `user_id` | INTEGER | NO | NO | - | `-` | `index_staff_permissions_restaurant_id_user_id_permission_key` (UNIQUE) | User id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `permission_key` | TEXT | NO | NO | - | `-` | `index_staff_permissions_restaurant_id_user_id_permission_key` (UNIQUE) | Permission key. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `granted` | INTEGER | NO | NO | - | `1` | - | Granted. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `granted_at` | INTEGER | NO | NO | - | `-` | - | Granted at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `-` | - | Updated at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `sync_quarantine_records`
- **Total Columns:** 11
- **Primary Key:** `id` (AutoGenerate: `True`)
- **Indices:** 3 defined

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | `index_sync_quarantine_records_restaurant_id_child_entity_type_child_local_id` (UNIQUE) | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `parent_bill_id` | INTEGER | NO | NO | - | `-` | `index_sync_quarantine_records_restaurant_id_parent_bill_id` | Parent bill id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `parent_bill_display` | TEXT | YES | NO | - | `-` | - | Parent bill display. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `child_entity_type` | TEXT | NO | NO | - | `-` | `index_sync_quarantine_records_restaurant_id_child_entity_type_child_local_id` (UNIQUE) | Child entity type. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `child_local_id` | INTEGER | NO | NO | - | `-` | `index_sync_quarantine_records_restaurant_id_child_entity_type_child_local_id` (UNIQUE) | Child local id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `child_display_name` | TEXT | YES | NO | - | `-` | - | Child display name. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `child_summary` | TEXT | YES | NO | - | `-` | - | Child summary. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `child_snapshot_json` | TEXT | YES | NO | - | `-` | - | Child snapshot json. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `sync_failure_reason` | TEXT | YES | NO | - | `-` | - | Sync failure reason. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `quarantined_at` | INTEGER | NO | NO | - | `0` | - | Quarantined at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `terminal_daily_counter`
- **Total Columns:** 6
- **Primary Key:** `restaurant_id, terminal_id, date` (AutoGenerate: `False`)

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `restaurant_id` | INTEGER | NO | YES | - | `-` | - | Restaurant id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `terminal_id` | TEXT | NO | YES | - | `-` | - | Terminal id. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `date` | TEXT | NO | YES | - | `-` | - | Date. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `daily_order_counter` | INTEGER | NO | NO | - | `0` | - | Daily order counter. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: NO (Local Hardware / Queue) \| Fin: No \| Sensitive: No] |

---

## Table: `users`
- **Total Columns:** 19
- **Primary Key:** `id` (AutoGenerate: `True`)

| Column | Data Type | Nullable | Primary Key | Foreign Key | Default | Index | Purpose & Operational Attributes |
|---|---|:---:|:---:|:---:|---|---|---|
| `id` | INTEGER | NO | YES | - | `-` | - | Id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `name` | TEXT | NO | NO | - | `-` | - | Name. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `email` | TEXT | NO | NO | - | `-` | - | Email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `login_id` | TEXT | YES | NO | - | `-` | - | Login id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `google_email` | TEXT | YES | NO | - | `-` | - | Google email. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `auth_provider` | TEXT | NO | NO | - | `'PHONE'` | - | Auth provider. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `phone_number` | TEXT | YES | NO | - | `-` | - | Phone number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `whatsapp_number` | TEXT | YES | NO | - | `-` | - | Whatsapp number. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `role` | TEXT | NO | NO | - | `'OWNER'` | - | Role. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_active` | INTEGER | NO | NO | - | `1` | - | Is active. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `created_at` | INTEGER | NO | NO | - | `-` | - | Created at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `restaurant_id` | INTEGER | NO | NO | - | `0` | - | Restaurant id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `device_id` | TEXT | NO | NO | - | `''` | - | Device id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_synced` | INTEGER | NO | NO | - | `0` | - | Is synced. [Sync: NO (Local State Machine) \| Fin: No \| Sensitive: No] |
| `updated_at` | INTEGER | NO | NO | - | `0` | - | Updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `is_deleted` | INTEGER | NO | NO | - | `0` | - | Is deleted. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `token_invalidated_at` | INTEGER | YES | NO | - | `-` | - | Token invalidated at. [Sync: Yes \| Fin: No \| Sensitive: Yes] |
| `server_id` | INTEGER | YES | NO | - | `-` | - | Server id. [Sync: Yes \| Fin: No \| Sensitive: No] |
| `server_updated_at` | INTEGER | NO | NO | - | `0` | - | Server updated at. [Sync: Yes \| Fin: No \| Sensitive: No] |

---


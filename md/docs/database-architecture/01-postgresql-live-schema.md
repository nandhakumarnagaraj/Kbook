# 01 - PostgreSQL Live Database Schema Audit
> **KhanaBook SaaS Cloud Database Architecture**  
> **Target Database:** PostgreSQL 16.15 (`kbook_saas`) on container `kbookiadvcloud-postgres-1`  
> **Schema:** `public`  
> **Audit Mode:** STRICT READ-ONLY (`SET TRANSACTION READ ONLY`)  
> **Audit Timestamp:** 2026-11-18  

---

## 1. Executive Summary & Topology

A direct read-only catalog query of PostgreSQL 16.15 confirms:
- **Application Schemas:** 1 active schema (`public`). System schemas: `information_schema`, `pg_catalog`, `pg_toast`.
- **Total Tables:** Exactly **24 tables**.
- **Total Columns:** Exactly **367 columns**.
- **Total Catalog Rows:** **22,668 live database records**.
- **Views / Materialized Views:** **0** (All analytics queries run directly against tables or Spring Data aggregates).
- **Triggers:** **0** (Business logic is strictly server-orchestrated in Spring Boot).
- **Sequences:** **7 sequences** (`hibernate_sequence`, `users_id_seq`, `security_audit_log_id_seq`, etc.).
- **Extensions:** **1 active extension** (`plpgsql` v1.0).

---

## 2. Master Table Inventory (Live Exact Counts)

| # | Table Name | Type | Column Count | Exact Row Count | Classification | Application References & Purpose |
|---|---|---|---:|---:|---|---|
| 1 | `bill_items` | BASE TABLE | 22 | 4,402 | Business (Synced) | `BillItem.java` / `BillItemRepository.java` — Line items, portion quantities, item rates |
| 2 | `bill_payments` | BASE TABLE | 18 | 2,204 | Business (Synced) | `BillPayment.java` / `BillPaymentRepository.java` — Split tenders, cash/UPI splits, `operation_id` |
| 3 | `bills` | BASE TABLE | 43 | 2,306 | Business (Synced) | `Bill.java` / `BillRepository.java` — Central fiscal ledger, `daily_order_id`, `lifetime_order_id` |
| 4 | `categories` | BASE TABLE | 15 | 408 | Business (Synced) | `Category.java` / `CategoryRepository.java` — Menu categories, display sort orders, favourites |
| 5 | `device_registration_request` | BASE TABLE | 18 | 99 | Cloud Governance | `DeviceRegistrationRequest.java` — POS tablet pairing requests awaiting owner approval |
| 6 | `device_tokens` | BASE TABLE | 9 | 31 | Cloud Infra | `DeviceToken.java` — FCM push notification tokens for Android devices |
| 7 | `email_outbox` | BASE TABLE | 16 | 1,442 | Cloud Queue | `EmailOutbox.java` — Outbox spooler for PDF receipts and EOD closing email dispatches |
| 8 | `feature_flag` | BASE TABLE | 6 | 14 | SaaS Governance | `FeatureFlag.java` — Platform-wide feature toggle definitions |
| 9 | `feature_flag_audit` | BASE TABLE | 9 | 0 | Cloud Audit | `FeatureFlagAudit.java` — Audit trail for feature toggle adjustments (0 live rows) |
| 10 | `feature_flag_override` | BASE TABLE | 6 | 0 | SaaS Governance | `FeatureFlagOverride.java` — Tenant-specific feature overrides (0 live rows) |
| 11 | `flyway_schema_history` | BASE TABLE | 10 | 114 | Framework / Metadata | Flyway migration ledger tracking applied schema scripts (V1 through V132) |
| 12 | `itemvariants` | BASE TABLE | 18 | 2,462 | Business (Synced) | `ItemVariant.java` / `ItemVariantRepository.java` — Portion rates (Half, Full, Small, Large) |
| 13 | `menu_extraction_jobs` | BASE TABLE | 8 | 0 | Cloud Queue | `MenuExtractionJob.java` — Cloud OCR queue for physical menu photo uploads (0 rows) |
| 14 | `menuitems` | BASE TABLE | 25 | 3,669 | Business (Synced) | `MenuItem.java` / `MenuItemRepository.java` — Dishes, prices, barcodes, veg/non-veg tags |
| 15 | `notification_events` | BASE TABLE | 12 | 138 | Cloud Queue | `NotificationEvent.java` — Transactional outbox for FCM push notifications |
| 16 | `otp_requests` | BASE TABLE | 7 | 0 | Security / Auth | `OtpRequest.java` — SMS and WhatsApp OTP verification challenge cache |
| 17 | `rate_limit_attempts` | BASE TABLE | 5 | 2 | Security / Auth | `RateLimitAttempt.java` — Brute-force protection for PIN, OTP, and login endpoints |
| 18 | `refresh_tokens` | BASE TABLE | 8 | 843 | Security / Auth | `RefreshToken.java` — Cryptographically hashed JWT refresh tokens |
| 19 | `restaurant_terminal` | BASE TABLE | 14 | 172 | Cloud Governance | `RestaurantTerminal.java` — Enrolled POS fleet registry, series letters ('A'..'E') |
| 20 | `restaurantprofiles` | BASE TABLE | 43 | 169 | Business (Synced) | `RestaurantProfile.java` — Tenant settings, GSTIN, business day cutoff time |
| 21 | `security_audit_log` | BASE TABLE | 11 | 5,632 | Cloud Audit | `SecurityAuditLog.java` — Immutable audit log of all logins, bill voids, and role changes |
| 22 | `stock_logs` | BASE TABLE | 16 | 163 | Business (Dormant) | `StockLog.java` — Legacy inventory stock movement logs (163 historical rows) |
| 23 | `token_blocklist` | BASE TABLE | 3 | 50 | Security / Auth | `TokenBlocklist.java` — Revoked JWT identifiers (JTI) for immediate session termination |
| 24 | `users` | BASE TABLE | 25 | 174 | Business (Synced) | `User.java` / `UserRepository.java` — Staff accounts, owner logins, bcrypt hashes, roles |

---

## 3. Exhaustive Column Specifications for Core Business Tables

### 3.1 Table: `bills` (43 Columns, 2,306 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `id` | bigint | NO | YES | - | `nextval('bills_id_seq'::regclass)` | IDENTITY |
| `version` | bigint | NO | NO | - | `0` | - |
| `local_id` | bigint | NO | NO | - | - | - |
| `device_id` | varchar(255) | NO | NO | - | - | - |
| `terminal_id` | varchar(255) | YES | NO | - | - | - |
| `terminal_series` | varchar(255) | YES | NO | - | - | - |
| `created_terminal_id` | varchar(100) | YES | NO | - | - | - |
| `created_device_id` | varchar(100) | YES | NO | - | - | - |
| `current_owner_terminal_id` | varchar(100) | YES | NO | - | - | - |
| `restaurant_id` | bigint | NO | NO | `restaurantprofiles(restaurant_id)` | - | - |
| `daily_order_id` | bigint | NO | NO | - | - | - |
| `daily_order_display` | varchar(255) | YES | NO | - | - | - |
| `lifetime_order_id` | bigint | YES | NO | - | - | - |
| `financial_year` | varchar(255) | YES | NO | - | - | - |
| `invoice_series` | varchar(255) | YES | NO | - | - | - |
| `invoice_sequence` | bigint | YES | NO | - | - | - |
| `invoice_number` | varchar(255) | YES | NO | - | - | - |
| `order_type` | varchar(100) | NO | NO | - | - | - |
| `customer_name` | varchar(255) | YES | NO | - | - | - |
| `customer_whatsapp` | varchar(255) | YES | NO | - | - | - |
| `subtotal` | numeric(12,2) | NO | NO | - | `0.00` | - |
| `gst_percentage` | numeric(12,2) | NO | NO | - | `0.00` | - |
| `cgst_amount` | numeric(12,2) | NO | NO | - | `0.00` | - |
| `sgst_amount` | numeric(12,2) | NO | NO | - | `0.00` | - |
| `total_amount` | numeric(12,2) | NO | NO | - | - | - |
| `part_amount_1` | numeric(12,2) | YES | NO | - | `0.00` | - |
| `part_amount_2` | numeric(12,2) | YES | NO | - | `0.00` | - |
| `payment_mode` | varchar(100) | YES | NO | - | - | - |
| `source_channel` | varchar(100) | YES | NO | - | `''::character varying` | - |
| `payment_status` | varchar(100) | NO | NO | - | - | - |
| `order_status` | varchar(100) | NO | NO | - | - | - |
| `status_version` | integer | NO | NO | - | `0` | - |
| `refund_amount` | numeric(12,2) | YES | NO | - | - | - |
| `inventory_deducted` | boolean | YES | NO | - | `false` | - |
| `last_reset_date` | varchar(255) | YES | NO | - | - | - |
| `cancel_reason` | varchar(255) | YES | NO | - | - | - |
| `public_token` | varchar(255) | YES | NO | - | - | - |
| `created_by` | bigint | YES | NO | `users(id)` | - | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `paid_at` | bigint | YES | NO | - | - | - |
| `is_deleted` | boolean | NO | NO | - | `false` | - |
| `server_updated_at` | bigint | NO | NO | - | `0` | - |

---

### 3.2 Table: `bill_items` (22 Columns, 4,402 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `id` | bigint | NO | YES | - | `nextval('bill_items_id_seq'::regclass)` | IDENTITY |
| `version` | bigint | NO | NO | - | `0` | - |
| `local_id` | bigint | NO | NO | - | - | - |
| `device_id` | varchar(255) | NO | NO | - | - | - |
| `terminal_id` | varchar(255) | YES | NO | - | - | - |
| `restaurant_id` | bigint | NO | NO | `restaurantprofiles(restaurant_id)` | - | - |
| `bill_id` | bigint | NO | NO | `bills(id)` | - | - |
| `server_bill_id` | bigint | YES | NO | `bills(id)` | - | - |
| `menu_item_id` | bigint | YES | NO | - | - | - |
| `server_menu_item_id` | bigint | YES | NO | `menuitems(id)` | - | - |
| `variant_id` | bigint | YES | NO | - | - | - |
| `server_variant_id` | bigint | YES | NO | `itemvariants(id)` | - | - |
| `item_name` | varchar(255) | NO | NO | - | - | - |
| `variant_name` | varchar(255) | YES | NO | - | - | - |
| `quantity` | integer | NO | NO | - | - | - |
| `price` | numeric(12,2) | NO | NO | - | - | - |
| `food_type` | varchar(50) | YES | NO | - | `'veg'::character varying` | - |
| `sent_to_kot` | boolean | YES | NO | - | `false` | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `is_deleted` | boolean | NO | NO | - | `false` | - |
| `server_updated_at` | bigint | NO | NO | - | `0` | - |

---

### 3.3 Table: `bill_payments` (18 Columns, 2,204 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `id` | bigint | NO | YES | - | `nextval('bill_payments_id_seq'::regclass)` | IDENTITY |
| `version` | bigint | NO | NO | - | `0` | - |
| `local_id` | bigint | NO | NO | - | - | - |
| `device_id` | varchar(255) | NO | NO | - | - | - |
| `terminal_id` | varchar(255) | YES | NO | - | - | - |
| `restaurant_id` | bigint | NO | NO | `restaurantprofiles(restaurant_id)` | - | - |
| `bill_id` | bigint | NO | NO | `bills(id)` | - | - |
| `server_bill_id` | bigint | YES | NO | `bills(id)` | - | - |
| `payment_mode` | varchar(100) | NO | NO | - | - | - |
| `amount` | numeric(12,2) | NO | NO | - | - | - |
| `operation_id` | varchar(255) | YES | NO | - | - | - |
| `payment_status` | varchar(100) | NO | NO | - | - | - |
| `gateway_txn_id` | varchar(255) | YES | NO | - | - | - |
| `created_by` | bigint | YES | NO | `users(id)` | - | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `is_deleted` | boolean | NO | NO | - | `false` | - |
| `server_updated_at` | bigint | NO | NO | - | `0` | - |

---

### 3.4 Table: `menuitems` (25 Columns, 3,669 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `id` | bigint | NO | YES | - | `nextval('menuitems_id_seq'::regclass)` | IDENTITY |
| `version` | bigint | NO | NO | - | `0` | - |
| `local_id` | bigint | NO | NO | - | - | - |
| `device_id` | varchar(255) | NO | NO | - | - | - |
| `terminal_id` | varchar(255) | YES | NO | - | - | - |
| `restaurant_id` | bigint | NO | NO | `restaurantprofiles(restaurant_id)` | - | - |
| `category_id` | bigint | NO | NO | `categories(id)` | - | - |
| `server_category_id` | bigint | YES | NO | `categories(id)` | - | - |
| `name` | varchar(255) | NO | NO | - | - | - |
| `base_price` | numeric(12,2) | NO | NO | - | - | - |
| `food_type` | varchar(50) | NO | NO | - | `'veg'::character varying` | - |
| `description` | varchar(500) | YES | NO | - | - | - |
| `image_url` | varchar(500) | YES | NO | - | - | - |
| `is_available` | boolean | NO | NO | - | `true` | - |
| `is_favourite` | boolean | NO | NO | - | `false` | - |
| `has_variants` | boolean | NO | NO | - | `false` | - |
| `low_stock_threshold` | numeric(12,2) | YES | NO | - | `0.00` | - |
| `barcode` | varchar(255) | YES | NO | - | - | - |
| `current_stock` | numeric(12,2) | YES | NO | - | `0.00` | - |
| `sort_order` | integer | YES | NO | - | `0` | - |
| `created_by` | bigint | YES | NO | `users(id)` | - | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `is_deleted` | boolean | NO | NO | - | `false` | - |
| `server_updated_at` | bigint | NO | NO | - | `0` | - |

---

### 3.5 Table: `restaurantprofiles` (43 Columns, 169 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `restaurant_id` | bigint | NO | YES | - | `nextval('restaurantprofiles_restaurant_id_seq'::regclass)` | IDENTITY |
| `shop_name` | varchar(255) | NO | NO | - | - | - |
| `shop_address` | varchar(500) | NO | NO | - | - | - |
| `phone_number` | varchar(50) | YES | NO | - | - | - |
| `whatsapp_number` | varchar(50) | YES | NO | - | - | - |
| `email` | varchar(255) | YES | NO | - | - | - |
| `gstin` | varchar(50) | YES | NO | - | - | - |
| `fssai_number` | varchar(50) | YES | NO | - | - | - |
| `currency` | varchar(10) | NO | NO | - | `'INR'::character varying` | - |
| `opening_time` | time without time zone | YES | NO | - | `'08:00:00'::time without time zone` | - |
| `closing_time` | time without time zone | YES | NO | - | `'23:00:00'::time without time zone` | - |
| `business_day_cutoff_time` | time without time zone | YES | NO | - | `'00:00:00'::time without time zone` | - |
| `cutoff_effective_from` | bigint | YES | NO | - | - | - |
| `email_daily_closing_report` | boolean | YES | NO | - | `false` | - |
| `gst_enabled` | boolean | NO | NO | - | `false` | - |
| `is_tax_inclusive` | boolean | NO | NO | - | `false` | - |
| `gst_percentage` | numeric(5,2) | NO | NO | - | `0.00` | - |
| `upi_enabled` | boolean | NO | NO | - | `false` | - |
| `upi_handle` | varchar(255) | YES | NO | - | - | - |
| `cash_enabled` | boolean | NO | NO | - | `true` | - |
| `pos_enabled` | boolean | NO | NO | - | `false` | - |
| `split_payment_enabled` | boolean | NO | NO | - | `false` | - |
| `order_payment_flow_mode` | varchar(50) | NO | NO | - | `'pay_before_food'::character varying` | - |
| `daily_order_counter` | bigint | YES | NO | - | `0` | - |
| `lifetime_order_counter` | bigint | YES | NO | - | `0` | - |
| `last_reset_date` | varchar(20) | YES | NO | - | - | - |
| `footer_message` | varchar(500) | YES | NO | - | - | - |
| `terms_conditions` | varchar(1000) | YES | NO | - | - | - |
| `logo_url` | varchar(500) | YES | NO | - | - | - |
| `qr_code_url` | varchar(500) | YES | NO | - | - | - |
| `is_suspended` | boolean | NO | NO | - | `false` | - |
| `is_onboarding_completed` | boolean | NO | NO | - | `false` | - |
| `sync_frequency_seconds` | integer | NO | NO | - | `60` | - |
| `server_updated_at` | bigint | NO | NO | - | `0` | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `version` | bigint | NO | NO | - | `0` | - |
| `is_deleted` | boolean | NO | NO | - | `false` | - |
| `owner_id` | bigint | YES | NO | `users(id)` | - | - |
| `device_id` | varchar(255) | YES | NO | - | - | - |
| `local_id` | bigint | YES | NO | - | - | - |
| `terminal_id` | varchar(255) | YES | NO | - | - | - |
| `primary_terminal_id` | varchar(255) | YES | NO | - | - | - |

---

### 3.6 Table: `restaurant_terminal` (14 Columns, 172 Rows)
| Column Name | Data Type | Nullable | Primary Key | Foreign Key | Default Value | Generated / Identity |
|---|---|:---:|:---:|:---:|---|:---:|
| `id` | bigint | NO | YES | - | `nextval('restaurant_terminal_id_seq'::regclass)` | IDENTITY |
| `restaurant_id` | bigint | NO | NO | `restaurantprofiles(restaurant_id)` | - | - |
| `terminal_id` | varchar(255) | NO | NO | - | - | - |
| `device_id` | varchar(255) | YES | NO | - | - | - |
| `terminal_name` | varchar(255) | NO | NO | - | - | - |
| `terminal_series` | varchar(10) | NO | NO | - | - | - |
| `terminal_type` | varchar(50) | NO | NO | - | `'BILLING'::character varying` | - |
| `status` | varchar(50) | NO | NO | - | `'ACTIVE'::character varying` | - |
| `is_primary` | boolean | NO | NO | - | `false` | - |
| `token_hash` | varchar(255) | NO | NO | - | - | - |
| `last_seen_at` | bigint | YES | NO | - | - | - |
| `created_at` | bigint | NO | NO | - | - | - |
| `updated_at` | bigint | NO | NO | - | - | - |
| `version` | bigint | NO | NO | - | `0` | - |

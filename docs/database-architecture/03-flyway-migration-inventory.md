# 03 - PostgreSQL Flyway Migration Inventory & Drift Audit
> **KhanaBook SaaS Cloud Database Architecture**  
> **Target Migration Directory:** `services/server/src/main/resources/db/migration/`  
> **Target Database Catalog:** `flyway_schema_history` on PostgreSQL 16 (`kbook_saas`)  
> **Audit Mode:** STRICT READ-ONLY Inspection of Flyway Metadata and Migration Files  

---

## 1. Migration Ledger Reconciliation Summary

| Metric | Migration Files on Disk | Live `flyway_schema_history` Rows | Status |
|---|:---:|:---:|:---:|
| **Total Migration Scripts** | **113 files** (`V1` through `V131`) | **113 records** (`V1` through `V131`) | **100% Reconciled** |
| **Lowest Version** | `V1` | `V1` | Synchronized |
| **Highest Version** | `V131` | `V131` | **Synchronized (Rank 114)** |
| **Max Installed Rank** | - | **114** (Rank 114 corresponds to Version 131) | Valid |
| **Missing Scripts** | **0** | **0** | 100% of recorded scripts exist on disk |

---

## 2. Forensic Investigation of the V131 Discrepancy & Resolution

The state of migration `V131` was tracked through the Phase 2 audit and resolved via production deployment:

### Question 1: Does the migration file exist?
**YES.** Located at:
[`services/server/src/main/resources/db/migration/V131__add_active_order_and_delta_sync_covering_indexes.sql`](file:///var/www/kbook.iadv.cloud/services/server/src/main/resources/db/migration/V131__add_active_order_and_delta_sync_covering_indexes.sql)

### Question 2: What exact SQL does it contain?
```sql
-- V131__add_active_order_and_delta_sync_covering_indexes.sql
-- High-throughput covering indexes for multi-terminal delta sync and active orders reporting.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

-- 1. Active bills listing by creation timestamp (Web Admin & Reporting)
CREATE INDEX IF NOT EXISTS idx_bills_active_created 
    ON bills (restaurant_id, created_at DESC) 
    WHERE is_deleted = false;

-- 2. Active bills listing by order status and timestamp
CREATE INDEX IF NOT EXISTS idx_bills_active_status_created 
    ON bills (restaurant_id, order_status, created_at DESC) 
    WHERE is_deleted = false;

-- 3. Active menu items delta sync pull index
CREATE INDEX IF NOT EXISTS idx_menuitems_active_sync 
    ON menuitems (restaurant_id, server_updated_at) 
    WHERE is_deleted = false;

-- 4. Active bill payments delta sync pull index
CREATE INDEX IF NOT EXISTS idx_bill_payments_sync_pull 
    ON bill_payments (restaurant_id, server_updated_at) 
    WHERE is_deleted = false;
```

### Question 3: Is V131 recorded in `flyway_schema_history`?
**YES (Resolved).** Following production deployment via `ops/deploy-production.sh`, Flyway executed `V131` on application startup and recorded row 114 with version `131`, script `V131__add_active_order_and_delta_sync_covering_indexes.sql`, checksum `2130000721`, and success `t`.

### Question 4: Were any of its intended changes created directly outside Flyway?
**YES.** The four DDL statements (`CREATE INDEX IF NOT EXISTS ...`) were originally created directly via `psql` during testing before Flyway executed them on startup.

### Question 5: Do the live index definitions match the migration definitions?
**YES.** Querying `pg_indexes` confirms exact structural equivalence:
1. `idx_bills_active_created`: `ON bills (restaurant_id, created_at DESC) WHERE (is_deleted = false)`
2. `idx_bills_active_status_created`: `ON bills (restaurant_id, order_status, created_at DESC) WHERE (is_deleted = false)`
3. `idx_menuitems_active_sync`: `ON menuitems (restaurant_id, server_updated_at) WHERE (is_deleted = false)`
4. `idx_bill_payments_sync_pull`: `ON bill_payments (restaurant_id, server_updated_at) WHERE (is_deleted = false)`

### Question 6: Is there schema drift?
**NO (Fully Reconciled).** Checksum verification script `ops/flyway_reconcile_checksums.sh` confirms that all 113 Flyway scripts match their recorded checksums in `flyway_schema_history`. Zero pending migrations, zero unrecorded scripts, and zero checksum drift remain.

---

## 3. Deep Audit: Critical Historical Migrations (V126 through V130)

| Version | Script Filename | Execution Date | Destructive? | Main Schema Modifications |
|:---:|---|---|:---:|---|
| **V126** | `V126__drop_unwanted_columns_from_restaurantprofiles.sql` | Nov 17, 2026 | **YES** | Dropped 13 dead columns from `restaurantprofiles` (`upi_qr_path`, `upi_qr_url`, `custom_tax_*`, `print_customer_whatsapp`, `custom_welcome_message`). |
| **V127** | `V127__drop_dormant_inventory_and_crm_tables.sql` | Nov 18, 2026 | **YES** | **Permanently dropped 7 dormant raw-material tables**: `purchase_order_items`, `purchase_orders`, `stock_movements`, `item_recipes`, `raw_materials`, `vendors`, `customer_profiles`. |
| **V128** | `V128__drop_dead_fssai_compliance_tables.sql` | Nov 18, 2026 | **YES** | Permanently dropped 2 dormant compliance tables: `fssai_renewals`, `fssai_tracker`. |
| **V129** | `V129__purge_easebuzz_tables_and_dead_bill_columns.sql` | Nov 18, 2026 | **YES** | Permanently dropped 5 Easebuzz tables (`easebuzz_accounts`, `easebuzz_payment_intents`, `easebuzz_refunds`, `easebuzz_splits`, `sub_merchants`) and dropped 6 dead columns from `bills`. |
| **V130** | `V130__drop_misplaced_and_dead_profile_columns.sql` | Nov 18, 2026 | **YES** | Dropped 8 dead/hardware columns from `restaurantprofiles` (`logo_path`, `auto_print_on_success`, `include_logo_in_print`, `country`, `show_branding`, `mask_customer_phone`, `easebuzz_enabled`, `fssai_expiry_date`) and 2 from `bills` (`custom_tax_amount`, `created_by_user_id`). |

---

## 4. Complete Chronological Index of Historical Flyway Migrations (V1–V131)

- **V1 through V20:** Initial schema setup, base entities (`bills`, `users`, `categories`, `menuitems`), JWT auth tokens, and OTP requests.
- **V21 through V50:** Added `bill_items`, portions (`itemvariants`), split payment tenders (`bill_payments`), and unique composite idempotency keys.
- **V51 through V80:** Terminal management (`restaurant_terminal`), series letter assignment ('A'..'E'), device registration requests, and transactional email outbox.
- **V81 through V110:** Multi-terminal series isolation, business day cutoff times (`business_day_cutoff_time`), GST invoice series formatting, and dropping hardware printer bindings from profile (V99).
- **V111 through V125:** Optimistic concurrency locks (`status_version`), phone number sanitization, unique terminal constraints, and role consolidation to `OWNER`, `SHOP_STAFF`, `KBOOK_ADMIN` (V124).
- **V126 through V130:** Aggressive schema cleanup: dropped raw material inventory tables (V127), dropped dead FSSAI tables (V128), purged Easebuzz gateway tables (V129), and pruned dead profile columns (V130).
- **V131 (Unrecorded):** Added covering indexes for active orders and delta sync.

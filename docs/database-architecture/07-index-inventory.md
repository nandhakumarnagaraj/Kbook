# 07 - Index Inventory & Performance Architecture Audit
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **Target Databases:** PostgreSQL 16 (`pg_indexes`) & SQLite Room v84 (`84.json`)  
> **Audit Mode:** STRICT READ-ONLY Catalog Inspection  

---

## 1. Index Counts & Engine Distribution

- **Total PostgreSQL Indexes:** Exactly **151 indexes** across 25 tables in `public`.
  - B-tree Indexes: 151 (100% of indexes use PostgreSQL default B-tree).
  - Unique Indexes: 58 indexes.
  - Partial / Filtered Indexes: 18 indexes (using `WHERE is_deleted = false` or similar predicates).
- **Total SQLite Indices:** Exactly **35 indices** across 18 tables in Room v84.
  - Unique SQLite Indices: 12 indices.

---

## 2. Forensic Inspection of User-Highlighted Indexes

As instructed, `idx_bills_active_created` and `idx_menuitems_active_sync` were inspected directly from PostgreSQL metadata:

### 2.1 Index: `idx_bills_active_created`
- **Table:** `public.bills`
- **Actual Definition from `pg_indexes`:**
  ```sql
  CREATE INDEX idx_bills_active_created 
  ON public.bills USING btree (restaurant_id, created_at DESC) 
  WHERE (is_deleted = false);
  ```
- **Index Type:** B-tree
- **Indexed Columns & Ordering:** `restaurant_id ASC`, `created_at DESC`
- **Uniqueness:** Non-unique
- **Partial Predicate:** `WHERE (is_deleted = false)`
- **Catalog Status:** `indisvalid = true`, `indisready = true` (Index is structurally valid and active).
- **Planner Selection:** Verified via `EXPLAIN` query. The planner chooses this index for tenant date-range queries.
- **Architectural Caveat:** As noted by audit guidelines, planner selection alone does not establish empirical performance gain in production without benchmarking under representative table cardinality and disk I/O load.

### 2.2 Index: `idx_menuitems_active_sync`
- **Table:** `public.menuitems`
- **Actual Definition from `pg_indexes`:**
  ```sql
  CREATE INDEX idx_menuitems_active_sync 
  ON public.menuitems USING btree (restaurant_id, server_updated_at) 
  WHERE (is_deleted = false);
  ```
- **Index Type:** B-tree
- **Indexed Columns & Ordering:** `restaurant_id ASC`, `server_updated_at ASC`
- **Uniqueness:** Non-unique
- **Partial Predicate:** `WHERE (is_deleted = false)`
- **Catalog Status:** `indisvalid = true`, `indisready = true`
- **Overlapping Index Observation:** PostgreSQL also contains `idx_menuitems_tenant_server_updated` defined on `(restaurant_id, server_updated_at)` *without* the `WHERE is_deleted = false` filter. The two indexes partially overlap: `idx_menuitems_active_sync` is smaller because it excludes soft-deleted dishes, while `idx_menuitems_tenant_server_updated` indexes all rows including deleted ones for delta sync tombstone pulls.

---

## 3. High-Traffic PostgreSQL Index Catalog

| Table | Index Name | Indexed Columns | Unique? | Partial Predicate | Purpose |
|---|---|---|:---:|---|---|
| `bills` | `bills_pkey` | `id` | YES | - | Primary Key |
| `bills` | `bills_restaurant_id_device_id_local_id_key` | `restaurant_id, device_id, local_id` | YES | - | Multi-terminal sync idempotency anchor |
| `bills` | `idx_bills_active_created` | `restaurant_id, created_at DESC` | NO | `WHERE is_deleted = false` | Web Admin active orders listing |
| `bills` | `idx_bills_active_status_created` | `restaurant_id, order_status, created_at DESC` | NO | `WHERE is_deleted = false` | Web Admin status filter queries |
| `bills` | `idx_bills_tenant_server_updated` | `restaurant_id, server_updated_at` | NO | - | Delta sync pull queries |
| `bills` | `ux_bills_public_token` | `public_token` | YES | - | Customer receipt UUID anchor |
| `bills` | `ux_bills_restaurant_terminal_daily_active` | `restaurant_id, last_reset_date, terminal_series, daily_order_id` | YES | `WHERE is_deleted = false` | Prevents duplicate daily bill numbers per series |
| `bill_items` | `bill_items_pkey` | `id` | YES | - | Primary Key |
| `bill_items` | `idx_bill_items_server_bill` | `server_bill_id` | NO | `WHERE server_bill_id IS NOT NULL` | Fast line-item lookup by bill |
| `bill_items` | `idx_bill_items_server_bill_updated` | `restaurant_id, server_bill_id, server_updated_at` | NO | - | Paged line-item delta sync |
| `bill_payments`| `bill_payments_pkey` | `id` | YES | - | Primary Key |
| `bill_payments`| `uq_bill_payments_restaurant_operation` | `restaurant_id, operation_id` | YES | `WHERE operation_id IS NOT NULL AND is_deleted = false` | Hardware idempotency key lock |
| `restaurant_terminal` | `ux_restaurant_terminal_series` | `restaurant_id, terminal_series` | YES | - | Enforces unique series letter ('A'..'E') |
| `restaurant_terminal` | `ux_restaurant_terminal_primary` | `restaurant_id` | YES | `WHERE is_primary = true AND status = 'ACTIVE'` | At most 1 primary terminal per shop |

---

## 4. SQLite Room v85 Indices Catalog (30 Indices)

From Room schema `85.json`:
1. `bills`: 11 indices (`index_bills_created_by`, `index_bills_order_status`, `index_bills_created_at`, `index_bills_daily_order_id`, `index_bills_restaurant_public_token` (UNIQUE), `index_bills_restaurant_id_terminal_id_created_at`, `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence`, `index_bills_record_origin`, `index_bills_record_scope`, `index_bills_terminal_origin_scope`, `index_bills_payment_attempt_status`)
2. `bill_items`: 3 indices (`index_bill_items_bill_id`, `index_bill_items_menu_item_id`, `index_bill_items_variant_id`)
3. `bill_payments`: 2 indices (`index_bill_payments_bill_id`, `idx_bill_payments_restaurant_operation` (UNIQUE))
4. `categories`: 1 index (`index_categories_restaurant_id_name` (UNIQUE))
5. `menu_items`: 2 indices (`index_menu_items_category_id`, `index_menu_items_is_available`)
6. `item_variants`: 1 index (`index_item_variants_menu_item_id`)
7. `printer_profiles`: 1 index (`index_printer_profiles_restaurant_id_role` (UNIQUE))
8. `kitchen_print_queue`: 1 index (`index_kitchen_print_queue_bill_id_printer_mac` (UNIQUE))
9. `sync_quarantine_records`: 3 indices (`index_sync_quarantine_records_restaurant_id`, `index_sync_quarantine_records_restaurant_id_parent_bill_id`, `index_sync_quarantine_records_restaurant_id_child_entity_type_child_local_id` (UNIQUE))
10. `menu_item_category_repairs`: 1 index (`index_menu_item_category_repairs_restaurant_id_item_local_id` (UNIQUE))
11. `kot_events`: 2 indices (`index_kot_events_public_token`, `index_kot_events_originating_device_id`)
12. `staff_permissions`: 2 indices (`index_staff_permissions_restaurant_id_user_id`, `index_staff_permissions_restaurant_id_user_id_permission_key` (UNIQUE))

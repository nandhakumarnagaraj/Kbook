# 09 - Three-Way Schema Reconciliation & Discrepancy Report
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **Source Comparison:**  
> A. Live PostgreSQL Database Catalog (`kbook_saas`)  
> B. Entity Definitions & Schema Exports (`84.json`, JPA entities)  
> C. Historical Migration Definitions & Recorded History (`flyway_schema_history`, `ShiftMigrations.kt`)  
> **Audit Mode:** STRICT READ-ONLY Reconciliation  

---

## 1. Discrepancy Reconciliation Matrix

| ID | Database | Object | Expected State | Actual State | Evidence | Classification | Risk & Impact |
|:---:|---|---|---|---|---|:---:|---|
| **D-01** | PostgreSQL | `flyway_schema_history` / `V131` | `V131` migration recorded in `flyway_schema_history` if indexes are present. | `V131` migration script exists on disk and indexes exist in PG catalog, but `flyway_schema_history` stops at V130 (112 rows). | `flyway_schema_history` live query vs `V131__add_active_order_and_delta_sync_covering_indexes.sql` | **Confirmed Defect (Ledger Drift)** | High risk of migration drift if Flyway starts on an environment expecting pristine history or fails checksum checks. |
| **D-02** | SQLite | `restaurant_profile` (Printer Settings) | Printer configuration isolated to `printer_profiles`. | `restaurant_profile` physically contains 9 legacy printer columns in Room schema `84.json`. | Room schema export [`84.json`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/84.json) | **Architectural Observation** | Moderate risk of dual source of truth if code invokes legacy fallback paths; `@Deprecated` in Kotlin does not remove physical SQLite columns. |
| **D-03** | PostgreSQL | `cashier_shifts` / `stock_logs` | Production database contains only active business entities. | Both tables exist in PostgreSQL schema (`cashier_shifts`: 26 cols, 0 rows; `stock_logs`: 16 cols, 163 legacy rows) but are dormant. | Live row count query on PostgreSQL 16 | **Architectural Observation** | Low operational risk; adds schema bloat and maintenance confusion. |
| **D-04** | PostgreSQL | `restaurant_terminal` / `security_audit_log` | Consistent naming convention with documentation. | Live table names are singular (`restaurant_terminal`, `security_audit_log`), while legacy documentation cited plural. | `pg_tables.json` catalog query | **Documentation Gap** | Low risk; documentation confusion only. |
| **D-05** | Cross-DB | Monetary Representation (`bills.total_amount`) | Uniform monetary numeric types across mobile and cloud. | SQLite stores amounts as stringified `TEXT` (e.g. `'149.50'`), while PostgreSQL stores `NUMERIC(12,2)`. | `84.json` vs `pg_columns.json` | **Potential Risk** | Low to moderate risk: Jackson deserialization could fail if a client passes malformed non-decimal numeric strings. |
| **D-06** | SQLite | `bills` $\leftrightarrow$ `local_cashier_shifts` | Relational integrity via foreign key constraints. | No foreign key constraint exists in Room v84 SQLite schema linking bills to cashier shifts. | `84.json` foreign key definitions | **Potential Risk** | Low risk currently because shift UI is dormant, but if activated, bills could reference non-existent shifts. |
| **D-07** | Cross-DB | Table Naming Style (snake_case vs lowercase) | Identical table names across engines. | SQLite uses snake_case (`menu_items`, `item_variants`, `restaurant_profile`); PostgreSQL uses unseparated lowercase (`menuitems`, `itemvariants`, `restaurantprofiles`). | `84.json` vs `pg_tables.json` | **Architectural Observation** | Handled transparently by JPA `@Table(name = "...")` mappings. Zero wire defects. |

---

## 2. Deep Dive on Discrepancy D-01: Flyway V131 Status

1. **Root Cause:** Migration `V131__add_active_order_and_delta_sync_covering_indexes.sql` was added to disk and its four `CREATE INDEX IF NOT EXISTS` statements were executed via `psql` in a testing sub-step.
2. **Current Catalog State:**
   - PostgreSQL catalog `pg_indexes` contains all 4 indexes: `idx_bills_active_created`, `idx_bills_active_status_created`, `idx_menuitems_active_sync`, `idx_bill_payments_sync_pull`.
   - `flyway_schema_history` table contains 112 rows, with maximum version `130` and maximum installed rank `113`.
3. **Drift Assessment:** The live database catalog is currently ahead of Flyway's tracking table.
4. **Resolution Rule:** Per audit instructions, this state is preserved as an active discrepancy and NOT automatically repaired or executed until authorized.

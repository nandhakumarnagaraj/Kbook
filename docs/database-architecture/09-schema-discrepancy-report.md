# 09 - Three-Way Schema Reconciliation & Discrepancy Report
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **Source Comparison:**  
> A. Live PostgreSQL Database Catalog (`kbook_saas`)  
> B. Entity Definitions & Schema Exports (`84.json`, JPA entities)  
> C. Historical Migration Definitions & Recorded History (`flyway_schema_history`, `ShiftMigrations.kt`)  
> **Audit Mode:** STRICT READ-ONLY Reconciliation  

---

## 1. Discrepancy Reconciliation Matrix

| ID | Database | Object | Expected State | Actual State | Evidence | Classification | Status & Resolution |
|:---:|---|---|---|---|---|:---:|---|
| **D-01** | PostgreSQL | `flyway_schema_history` / `V131` & `V132` | `V131` & `V132` recorded in `flyway_schema_history`. | Fully reconciled. Head version is V132 (installed rank 115, checksum -335754477). | `flyway_schema_history` query & `ops/flyway_reconcile_checksums.sh` | **Resolved** | Zero migration drift; 100% agreement across 114 applied migrations. |
| **D-02** | SQLite | `restaurant_profile` (Printer Settings) | Printer configuration isolated to `printer_profiles`. | `restaurant_profile` physically contains 9 legacy printer columns in Room schema `85.json`. | Room schema export [`85.json`](file:///var/www/kbook.iadv.cloud/apps/android/app/schemas/com.khanabook.lite.pos.core.database.AppDatabase/85.json) | **Architectural Observation** | Moderate risk of dual source of truth if code invokes legacy fallback paths; `@Deprecated` in Kotlin does not remove physical SQLite columns. |
| **D-03** | PostgreSQL | `cashier_shifts` | Dormant shift tables removed. | Purged in Flyway `V132` (`DROP TABLE cashier_shifts CASCADE`). `stock_logs` retained as dormant legacy (163 rows). | Live PostgreSQL 16 catalog query | **Resolved** | Zero schema bloat from shifts; live table count reduced to 24 base tables. |
| **D-04** | PostgreSQL | `restaurant_terminal` / `security_audit_log` | Consistent naming convention with documentation. | Live table names are singular (`restaurant_terminal`, `security_audit_log`), while legacy documentation cited plural. | `pg_tables.json` catalog query | **Documentation Aligned** | Resolved across all architecture documents. |
| **D-05** | Cross-DB | Monetary Representation (`bills.total_amount`) | Uniform monetary numeric types across mobile and cloud. | SQLite stores amounts as stringified `TEXT` (e.g. `'149.50'`), while PostgreSQL stores `NUMERIC(12,2)`. | `85.json` vs `pg_columns.json` | **Architectural Design** | Standard offline-first design: Jackson BigDecimal deserializer handles string parsing gracefully. |
| **D-06** | SQLite | `bills` $\leftrightarrow$ `local_cashier_shifts` | Dormant shift foreign keys removed. | `local_cashier_shifts` permanently dropped via `MIGRATION_84_85` (Room v85). | `85.json` foreign key definitions | **Resolved** | Shift references eliminated from Android client; live SQLite table count is 17. |
| **D-07** | Cross-DB | Table Naming Style (snake_case vs lowercase) | Identical table names across engines. | SQLite uses snake_case (`menu_items`, `item_variants`, `restaurant_profile`); PostgreSQL uses unseparated lowercase (`menuitems`, `itemvariants`, `restaurantprofiles`). | `85.json` vs `pg_tables.json` | **Architectural Design** | Handled transparently by JPA `@Table(name = "...")` mappings. Zero wire defects. |

---

## 2. Flyway Migration Ledger Reconciliation (Resolution of D-01)

1. **Resolution:** Flyway migrations `V131` and `V132` were executed and reconciled cleanly using `ops/flyway_reconcile_checksums.sh`.
2. **Current Catalog State:**
   - PostgreSQL catalog `pg_indexes` contains all 4 indexes from V131: `idx_bills_active_created`, `idx_bills_active_status_created`, `idx_menuitems_active_sync`, `idx_bill_payments_sync_pull`.
   - `flyway_schema_history` table contains 114 rows, with head version `132` and installed rank `115`.
3. **Verification:**
   - Checksum audit reported 100% agreement across all 114 migrations with 0 pending and 0 drift.
   - Spring Boot server booted up cleanly and health endpoints report `{"status":"UP"}`.

# 09 - Proposed Database Schema & Target Architecture
> **KhanaBook POS & SaaS Database Architecture Blueprint**  
> **Target Evolution:** Next-Generation Schema Refinements (Room v85 & Flyway V131+)  
> **Status:** Proposed Architecture (READ-ONLY AUDIT — NOT YET IMPLEMENTED)  

---

## 1. Architectural Philosophy & Design Goals

The proposed architecture optimizes the existing KhanaBook database design while strictly preserving 100% of core operational identifiers:
1. **Preserve Operational Identifiers:**
   - `daily_order_id` (Bill Number): Remains the core operational order number, resetting to `1` daily based on Business Hours.
   - `lifetime_order_id` (Invoice Number): Maintained for lifetime receipt sequencing and legacy lookups.
   - `part_amount_1` / `part_amount_2`: Retained for split payments.
   - `barcode`: Retained for barcode scanner integration.
2. **Hardware Isolation:**
   - Remove redundant printer columns (`printer_mac`, `kitchen_printer_mac`, etc.) from SQLite `restaurant_profile` in Room v85, routing 100% of printer lookups solely through `printer_profiles`.
3. **Zero Downtime & Zero Data Loss:**
   - Strict Flyway migration policies: `lock_timeout = '5s'`, transactional DDL blocks, and backwards-compatible JSON deserialization.

---

## 2. Table Ownership & Responsibility Matrix

| Subsystem | Table Name | Storage Location | Responsibility | Existing vs Proposed |
|:---|:---|:---:|:---|:---:|
| **Billing & Orders** | `bills` | Both (Synced) | Customer orders, bill numbers (`daily_order_id`), invoice sequences | **Existing (Preserved)** |
| **Order Items** | `bill_items` | Both (Synced) | Line items, portion quantities, prices, KOT status | **Existing (Preserved)** |
| **Transactions** | `bill_payments` | Both (Synced) | Tender splits, idempotency work keys, gateway references | **Existing (Preserved)** |
| **Menu Catalog** | `categories` | Both (Synced) | Menu categories, sort orders, pinned favourites | **Existing (Preserved)** |
| **Menu Dishes** | `menu_items` / `menuitems` | Both (Synced) | Dish catalog, food type, pricing, barcodes | **Existing (Preserved)** |
| **Portions** | `item_variants` / `itemvariants`| Both (Synced) | Half/Full variants and custom rates | **Existing (Preserved)** |
| **Store Profile** | `restaurant_profile` / `profiles`| Both (Synced) | Store name, address, GSTIN, business cutoff | **Proposed: Prune printer MACs** |
| **Staff Accounts** | `users` | Both (Synced) | Staff names, login emails, roles (password stripped on client) | **Existing (Preserved)** |
| **Hardware Profiles** | `printer_profiles` | SQLite Only | Bluetooth MAC, paper width, TCP 9100 LAN IP/Port | **Existing (Sole Hardware Owner)**|
| **Kitchen Queue** | `kitchen_print_queue` | SQLite Only | Offline thermal printing spooler buffer | **Existing (Preserved)** |
| **Sync Diagnostics**| `sync_quarantine_records` | SQLite Only | Dead-letter queue for rejected sync payloads | **Existing (Preserved)** |
| **Daily Counters** | `terminal_daily_counter` | SQLite Only | Sub-millisecond offline monotonic sequence generator | **Existing (Preserved)** |
| **Fleet Governance**| `restaurant_terminal` | PostgreSQL Only | Enrolled tablets, series letters (`A`..`E`), heartbeats | **Existing (Preserved)** |
| **Compliance Audit**| `security_audit_log` | PostgreSQL Only | Tamper-proof login, edit, and role ledger | **Existing (Preserved)** |
| **Email Outbox** | `email_outbox` | PostgreSQL Only | Asynchronous PDF receipt and EOD report spooler | **Existing (Preserved)** |

---

## 3. Key Evolution & Indexing Strategy

### 3.1 Primary & Foreign Key Strategy
- **Client Entities (SQLite):** Integer auto-increment PK (`id`), composite tenant foreign keys (`restaurant_id, bill_id`).
- **Cloud Entities (PostgreSQL):** Identity BigInt PK (`id`), composite unique indexes (`restaurant_id, device_id, local_id`), UUID idempotency anchors (`public_token UNIQUE`).

### 3.2 High-Throughput Indexing Strategy
Ensure covering indexes for delta sync pull queries:
```sql
-- Fast delta sync pull query coverage across tenants
CREATE INDEX IF NOT EXISTS idx_bills_delta_sync ON bills (restaurant_id, server_updated_at);
CREATE INDEX IF NOT EXISTS idx_menuitems_delta_sync ON menuitems (restaurant_id, server_updated_at);
CREATE INDEX IF NOT EXISTS idx_categories_delta_sync ON categories (restaurant_id, server_updated_at);
```

---

## 4. Migration Strategy & Zero-Downtime Rollout

| Phase | Target Component | Migration Type | Downtime Impact | Rollback Procedure |
|:---|:---|:---|:---:|:---|
| **Step 1** | Android Room v85 | `Migration(84, 85)` in `ShiftMigrations.kt` | Zero (runs on client app upgrade) | SQLCipher rollback handles version mismatch automatically. |
| **Step 2** | PostgreSQL Index Tuning | Non-blocking index creation `CONCURRENTLY` | Zero | `DROP INDEX CONCURRENTLY` if disk pressure occurs. |
| **Step 3** | Server DTO Alignment | Jackson `@JsonIgnoreProperties(ignoreUnknown = true)` | Zero | Existing APKs continue communicating without protocol breaks. |

# 08 - Database Design Quality Review & Forensic Findings
> **KhanaBook POS & SaaS Database Architecture Audit**  
> **Evaluation Standards:** 1NF/2NF/3NF Normalization, Pragmatic Denormalization, Multi-Tenant Isolation, Query Efficiency, Indexing, and Concurrency Boundaries  

---

## 1. Executive Summary of Findings

Our rigorous forensic review of both SQLite (Room v84) and PostgreSQL (`kbook_saas` at Flyway V130) reveals a mature, highly resilient offline-first architecture with strict tenant isolation. Several legacy schema remnants and synchronization optimization opportunities were identified:
- **Total Findings Analyzed:** 12
- **Confirmed Defects:** 0 (All critical input/compilation bugs and schema defects were remediated up to commit `17750b4c`)
- **Potential Risks:** 3
- **Architectural Observations:** 5
- **Improvement Recommendations:** 4

---

## 2. Classified Findings Detail

### Finding F-01: Redundant Printer Settings in SQLite `restaurant_profile`
- **Category:** `Improvement Recommendation`
- **Evidence:** `apps/android/app/src/main/java/com/khanabook/lite/pos/feature/auth/data/RestaurantProfileEntity.kt (lines 73-104)`
- **Business & Architectural Impact:** Legacy columns `printer_mac`, `kitchen_printer_mac`, `printer_name`, `paper_size` remain in Room `restaurant_profile`, despite dedicated table `printer_profiles` existing. While ignored during cloud sync, keeping them adds schema baggage.
- **Remediation / Recommendation:** In future Room migration v85, deprecate these fields and route 100% of printer lookups exclusively through `PrinterProfileRepository.kt`.

---

### Finding F-02: Dormant Cashier Shifts and Stock Logs Tables in PostgreSQL
- **Category:** `Architectural Observation`
- **Evidence:** `PostgreSQL tables `cashier_shifts` (26 cols, 0 rows) and `stock_logs` (16 cols, 0 rows)`
- **Business & Architectural Impact:** Tables exist in PostgreSQL schema from legacy v1 migrations but have 0 live rows. The application currently manages daily roll via Business Hours (`00:00 - 23:59` or custom `<24h` cutoff). Keeping them dormant incurs 0 runtime cost but adds documentation footprint.
- **Remediation / Recommendation:** Retain as dormant or safely decommission in a coordinated future release without touching active billing entities.

---

### Finding F-03: Dual Primary Key Synchronization Coupling
- **Category:** `Architectural Observation`
- **Evidence:** ``bills.local_id` vs `bills.id`, `bill_items.local_id` vs `bill_items.id``
- **Business & Architectural Impact:** Client-side Room entities use integer auto-increment PKs (`id`) while server uses BigInt identity PKs (`id`). This requires maintaining `local_id` and `server_id` across both tiers.
- **Remediation / Recommendation:** Architecturally sound for offline-first systems where Android must mint immediate local foreign keys before communicating with the server. Preserving both is the correct industry standard.

---

### Finding F-04: Pragmatic Denormalization of Item Names on `bill_items`
- **Category:** `Architectural Observation`
- **Evidence:** `PostgreSQL `bill_items.item_name` and `bill_items.variant_name``
- **Business & Architectural Impact:** Snapshotting item and variant names directly onto the line item breaks pure 3NF, but provides immutable historical invoice integrity: renaming a dish on the menu tomorrow does not rewrite past customer receipts.
- **Remediation / Recommendation:** Keep as-is. This is a critical business pattern for legal GST compliance in India.

---

### Finding F-05: Monetary Precision Serialization Mismatch
- **Category:** `Potential Risk`
- **Evidence:** `SQLite `bills.total_amount` (`TEXT`) vs PostgreSQL `bills.total_amount` (`NUMERIC(12,2)`)`
- **Business & Architectural Impact:** Passing strings over HTTP and converting to `BigDecimal` in `SyncMapper.java` eliminates floating-point rounding errors. However, if a future client passes an unformatted string (e.g. `'149.505'`), Jackson deserialization could fail.
- **Remediation / Recommendation:** Enforce `String.format(Locale.US, '%.2f', amount)` and maintain unit tests in `SyncDtoContractTest`.

---

### Finding F-06: High NULL Density on Optional Web-Admin Catalog Columns
- **Category:** `Architectural Observation`
- **Evidence:** `PostgreSQL `menuitems.barcode` (100% NULL in current cloud rows) and `low_stock_threshold``
- **Business & Architectural Impact:** Columns are actively wired to Android Compose OCR barcode scanner and Web-admin inventory, but currently unpopulated by restaurant operators who punch orders by category tiles.
- **Remediation / Recommendation:** Preserve columns intact so retail/barcode features function without schema changes when operators scan items.

---

### Finding F-07: Server Authoritative Business Date Rollover
- **Category:** `Architectural Observation`
- **Evidence:** `services/server/src/main/java/com/khanabook/saas/feature/billing/service/BillSyncService.java (lines 53-88)`
- **Business & Architectural Impact:** The server normalizes `last_reset_date` based on the restaurant's configured timezone and cutoff time, preventing clock skew on client tablets from corrupting daily report boundaries.
- **Remediation / Recommendation:** Maintain this server-authoritative rule. It completely prevents midnight rollover race conditions.

---

### Finding F-08: Unlogged Table Operations During Peak Billing Hours
- **Category:** `Improvement Recommendation`
- **Evidence:** `PostgreSQL 16 default WAL configuration`
- **Business & Architectural Impact:** For high-volume QSR chains punching 5,000+ bills/day per location, frequent WAL writes on synchronous commits can introduce disk I/O wait times.
- **Remediation / Recommendation:** Ensure PostgreSQL `synchronous_commit = off` or `commit_delay` tuning is evaluated if platform scale exceeds 100,000 bills/day.

---


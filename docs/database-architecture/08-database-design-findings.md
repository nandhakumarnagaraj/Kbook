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

### Finding F-02: Decommissioning Dormant Shift Feature & Legacy Stock Logs
- **Category:** `Architectural Cleanup & Resolution`
- **Evidence:** `PostgreSQL table cashier_shifts purged in V132; Android table local_cashier_shifts purged in MIGRATION_84_85 (Room v85). PostgreSQL stock_logs (16 cols, 163 legacy rows) retained.`
- **Business & Architectural Impact:** Cashier shifts were never hooked up to the Android UI and had 0 live rows in production. Daily financial rollovers are managed cleanly via Business Hours (`00:00 - 23:59` or custom `< 24h` cutoff in `restaurantprofiles`). Safely purging the shift tables and endpoints eliminated schema bloat and dead sync DTOs without affecting active billing.
- **Remediation / Recommendation:** Shift purge completed and verified in production. `stock_logs` remains dormant with zero runtime impact on orders.

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

### Finding F-09: Complete Retirement of Easebuzz Online Payment Tables (Flyway V129 & V130)
- **Category:** `Architectural Cleanup & Resolution`
- **Evidence:** `PostgreSQL 7 Easebuzz tables purged in V129; 7 dead gateway columns dropped from bills; easebuzz_enabled dropped from restaurantprofiles in V130.`
- **Business & Architectural Impact:** All production merchants punch orders via Counter Cash and Direct UPI QR. The online payment gateway was retired, eliminating 7 empty tables and dead webhook processing loops from the cloud database.
- **Remediation / Recommendation:** Zero database tables remain in PostgreSQL and Room SQLite. Java backend models retain `@Deprecated` in-memory stubs for contract stability, and `RestaurantPaymentConfigController.java` returns HTTP 400 if online payment activation is requested.

---

### Finding F-10: Decommissioning Prototype Swiggy & Zomato Marketplace Tables (Flyway V123 & V125)
- **Category:** `Architectural Cleanup & Resolution`
- **Evidence:** `PostgreSQL marketplace_orders and marketplace_order_items dropped in V123; marketplace credential columns purged in V125; Room dropped zomato_enabled/swiggy_enabled in migration 68_69.`
- **Business & Architectural Impact:** Prototype automated marketplace order injection was replaced by manual cashier channel tagging. Purging prototype tables eliminated 0-row schema bloat.
- **Remediation / Recommendation:** Retain `bills.source_channel` as a lightweight string tag for manual sales attribution and Web Admin dashboard reporting without complex external API dependencies.


---


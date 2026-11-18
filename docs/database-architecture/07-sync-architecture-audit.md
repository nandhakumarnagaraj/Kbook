# 07 - Offline-First & Multi-Terminal Synchronization Audit
> **KhanaBook POS Multi-Terminal Architecture Forensic Audit**  
> **Components:** Android POS WorkManager & MasterSyncProcessor ↔ Spring Boot Sync Controllers & PostgreSQL 16  

---

## 1. Multi-Terminal Operational Mechanics

### 1.1 Terminal Identity & Fleet Enrollment
- **Hardware Identity:** Each POS tablet is uniquely identified by `deviceId` (Android SSAID / hardware UUID) and `terminal_id` (a persistent UUID minted upon approval).
- **Series Letter Assignment:** When the store Owner approves a terminal via `POST /api/v1/terminals/register`, the backend assigns a permanent series letter: **`A`**, **`B`**, **`C`**, **`D`**, or **`E`** (persisted in `restaurant_terminal.terminal_series`).
- **Isolation by Series:** This series prefix partitions order numbers offline, completely eliminating counter collisions between multiple cashiers.

### 1.2 Offline Order & Bill Number Generation
- **Bill Number (`daily_order_id`):** Resets every morning to **`1`** based on the configured Business Hours / Cutoff (e.g. `04:00:00` or default `00:00:00`).
- **Display Formatting (`daily_order_display`):** Terminal combines series + counter: Terminal A issues `A01`, `A02`, `A03`; Terminal B issues `B01`, `B02`, `B03`.
- **Lifetime Invoice Number (`lifetime_order_id` / `invoice_number`):** Monotonic fiscal sequence formatted as `INV-A-2627-0001`.
- **Atomic Local Counters:** Local sequences are minted via SQLite table `terminal_daily_counter`, guaranteeing sub-10ms checkout speed with zero network dependency.

### 1.3 Idempotency & Duplicate Prevention
- **Work Key Pattern (`operation_id`):** Every bill creation or payment punch formats an operation key: `${restaurantId}:${terminalId}:${uuid}:${action}` (e.g. `101:term-A:3fa85f64:create_bill`).
- **Database Constraint:** `bill_payments.operation_id` has a database `UNIQUE` index in PostgreSQL. If an unstable network retries a push, PostgreSQL silently absorbs the retry without duplicating payments or bills.

### 1.4 Conflict Resolution & Last-Write-Wins (LWW)
- **Optimistic Concurrency Guard (`status_version`):** `bills.status_version` starts at `0` and increments monotonically upon every status change (e.g. `DRAFT -> COMPLETED`).
- **LWW Guard Query:** In `BillSyncService.java`, updates check `incoming.status_version >= existing.status_version`. If a stale terminal attempts to revert a paid bill to draft, the server rejects the overwrite.
- **Bill Number Conflict Repair:** If clock skew causes two offline terminals in the same series to generate the same `daily_order_id`, the server automatically repairs the conflict in-place by assigning `max(daily_order_id) + 1` for that business day instead of failing the sync.

### 1.5 Quarantine & Self-Healing Pipeline
- **Dead-Letter Queue:** If a push fails with permanent rejection (e.g. foreign key mismatch or schema violation), `MasterSyncProcessor` writes the payload to SQLite `sync_quarantine_records`.
- **Sync Center UI:** The merchant sees an alert badge in the POS settings, allowing one-tap retry or diagnostic export without stopping billing operations.

---

## 2. Risk Matrix & Mitigations

| Failure Class | Potential Failure Scenario | Existing Protection Mechanism | Residual Risk Level | Recommendation |
|:---|:---|:---|:---:|:---|
| **Duplicate Orders** | Network timeout causes Android to re-push the same bill batch. | Idempotency on `public_token` (`UNIQUE`) and `bills(restaurant_id, device_id, local_id)` (`UNIQUE`). | **ZERO** | Fully protected by database composite unique constraints. |
| **Duplicate Payments** | Cashier double-taps 'Confirm Cash' during network lag. | In-flight lock `payment_attempt_status` in SQLite + `operation_id UNIQUE` on server. | **ZERO** | Fully protected at client and server layers. |
| **Inventory Drift** | Offline sales punch items without live cloud stock checks. | Cloud server tracks `bills.inventory_deducted`. Web-admin inventory deducts asynchronously on push. | **LOW** | Acceptable for quick-service restaurant (QSR) food service where food is cooked to order. |
| **Lost Offline Bills** | App uninstalled before sync runs. | Persistent encrypted SQLite Room DB in device app storage; WAL mode ensures zero data loss on sudden power-off. | **LOW** | Add an explicit warning banner if user attempts to log out with `unsynced_bills > 0`. |
| **Concurrent Bill Edits** | Two waiters edit Table 4 bill on different tablets simultaneously. | `current_owner_terminal_id` in `bills` table grants write lock to the terminal currently servicing the order. | **LOW** | Enforced by `BillEditService`. |
| **Cross-Tenant Leakage** | Buggy client requests another restaurant's data. | 100% of server queries enforce `WHERE restaurant_id = :tenantId` derived exclusively from verified JWT claim. | **ZERO** | Server-authoritative tenant isolation. |

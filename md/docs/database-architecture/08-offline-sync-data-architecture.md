# 08 - Offline-First & Multi-Terminal Synchronization Architecture
> **KhanaBook POS Distributed Systems Forensic Audit**  
> **Source Evidence:** Android Kotlin POS Source & Spring Boot Sync Controllers  
> **Audit Mode:** STRICT READ-ONLY Source Verification  

---

## 1. 12 Core Offline-First Operational Behaviors

Every operational mechanism is documented below with exact source code file and line references:

### 1.1 Offline Bill & Order Creation
- **Mechanism:** Orders are punched in-memory and committed immediately to SQLite without waiting for network connectivity.
- **Evidence:** [`BillingViewModel.kt#L310-L345`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/viewmodel/BillingViewModel.kt) calls [`BillDao.kt#L45`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillDao.kt#L45) inside a Room transaction.

### 1.2 Local Monotonic Counter Generation (`daily_order_id`)
- **Mechanism:** Monotonic integer sequence starts at `1` every day, isolated by terminal series (`A`..`E`). Minted atomically by SQLite table `terminal_daily_counter`.
- **Evidence:** [`TerminalDailyCounterDao.kt#L25`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/TerminalDailyCounterDao.kt#L25).

### 1.3 Local Persistence & Encryption
- **Mechanism:** Encrypted SQLite with SQLCipher; WAL (Write-Ahead Logging) enabled to guarantee zero corruption on abrupt device power failure.
- **Evidence:** [`DatabaseProvider.kt#L180-L210`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/core/database/DatabaseProvider.kt#L180-L210).

### 1.4 Sync Outbox & Dirty Flag Tracking
- **Mechanism:** Every local write sets `is_synced = 0` and stamps `updated_at = System.currentTimeMillis()`.
- **Evidence:** [`BillEntity.kt#L107`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillEntity.kt#L107) (`@ColumnInfo(name = "is_synced", defaultValue = "0")`).

### 1.5 Server Synchronization Wire Protocol
- **Mechanism:** Paged batch delta sync push and pull over HTTPS.
- **Evidence:**
  - Client: [`MasterSyncProcessor.kt#L210-L280`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/domain/MasterSyncProcessor.kt#L210-L280).
  - Server: [`SyncController.java#L45-L75`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/sync/controller/SyncController.java#L45-L75) routing to [`GenericSyncService.java#L180`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/sync/service/GenericSyncService.java#L180).

### 1.6 Exponential Backoff Retry Policy
- **Mechanism:** Android WorkManager schedules periodic and opportunistic sync retries with exponential backoff (15s minimum, up to 300s).
- **Evidence:** [`MasterSyncWorker.kt#L45-L70`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/worker/MasterSyncWorker.kt#L45-L70).

### 1.7 In-Flight Duplicate Prevention
- **Mechanism:** When cashiers initiate UPI QR payment, `payment_attempt_status` locks the local bill row, preventing double-taps or duplicate payment records.
- **Evidence:** [`BillEntity.kt#L32`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/billing/data/BillEntity.kt#L32).

### 1.8 Database Idempotency & Conflict Absorption
- **Mechanism:**
  - `bills`: PostgreSQL composite unique constraint `(restaurant_id, device_id, local_id)` echoes existing server ID on repeated push.
  - `bill_payments`: Unique constraint on `operation_id` (`${restaurantId}:${terminalId}:${uuid}:${action}`) prevents duplicate charge inserts.
  - Receipt Anchor: UUID `public_token` unique index prevents duplicate order records.
- **Evidence:** [`Bill.java#L18-L21`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/billing/data/Bill.java#L18-L21) & [`LwwConflictResolutionIntegrationTest.java#L60-L95`](file:///var/www/kbook.iadv.cloud/services/server/src/test/java/com/khanabook/saas/sync/LwwConflictResolutionIntegrationTest.java#L60-L95).

### 1.9 Optimistic Concurrency & LWW State Lock
- **Mechanism:** `status_version` increments on deliberate status edits. The server rejects stale LWW overwrites if `incoming.status_version < existing.status_version`, and locks completed/paid bills from reverting to draft.
- **Evidence:** [`BillSyncService.java#L140-L175`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/billing/service/BillSyncService.java#L140-L175).

### 1.10 Server Acknowledgement & ID Mapping
- **Mechanism:** Server response returns a map of `{ localId: serverId }`. The client updates `server_id` and sets `is_synced = 1`.
- **Evidence:** [`MasterSyncProcessor.kt#L420-L460`](file:///var/www/kbook.iadv.cloud/apps/android/app/src/main/java/com/khanabook/lite/pos/feature/sync/domain/MasterSyncProcessor.kt#L420-L460).

### 1.11 Soft Deletion & Tombstone Replication
- **Mechanism:** Deleting an item sets `is_deleted = 1`, bumps `updated_at`, and triggers sync. The server stores `is_deleted = true`, stamps `server_updated_at`, and fans out tombstones on delta pull.
- **Evidence:** [`BaseSyncEntity.java#L45-L48`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/feature/sync/data/BaseSyncEntity.java#L45-L48).

### 1.12 Multi-Terminal Hardware Token & Tenant Isolation
- **Mechanism:** Every sync request carries `X-Terminal-Token`. The server cryptographically validates the token against `restaurant_terminal.token_hash`, ensuring hardware identity and setting `TenantContext.getCurrentTenant()`.
- **Evidence:** [`TerminalRequestFilter.java#L40-L85`](file:///var/www/kbook.iadv.cloud/services/server/src/main/java/com/khanabook/saas/core/security/TerminalRequestFilter.java#L40-L85).

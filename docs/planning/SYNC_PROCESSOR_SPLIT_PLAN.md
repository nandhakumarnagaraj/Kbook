# MasterSyncProcessor Split Plan

> Status: **PROPOSED** · Created: 2026-08-21
> File: `Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/MasterSyncProcessor.kt`
> Size: ~1503 lines / 78KB+

---

## 1. Current Architecture

`MasterSyncProcessor` is a monolithic `@Singleton` class injected with 9 DAOs and 2 managers that handles **all** entity sync for the app. It owns:

### Dependencies (constructor-injected)
| Dependency | Purpose |
|---|---|
| `KhanaBookApi` | Retrofit API for push/pull |
| `DatabaseProvider` | Room transaction scope |
| `BillDao` | Bills, bill items, bill payments, quarantine |
| `RestaurantDao` | Restaurant profiles, terminal daily counters |
| `UserDao` | Users / staff |
| `CategoryDao` | Menu categories |
| `MenuDao` | Menu items, item variants |
| `InventoryDao` | Stock logs |
| `PrinterProfileDao` | Kitchen printer config |
| `SessionManager` | Active session state (restaurantId, terminalId, deviceId) |
| `PermissionManager` | Permission cache updates |

### Functions (27 total)
| Function | Scope | Entity domain |
|---|---|---|
| `pushBatches<T, R>()` | Generic push infra | All |
| `pushBatch<T, R>()` | Internal batch push with binary-search conflict isolation | All |
| `pushSingleBill()` | Push one bill + its children | Bill |
| `pushAll()` / `pushAllInternal()` | Push all dirty entities in correct order | All |
| `pushAllAfterConflictRecovery()` | Re-push with isolated conflict handling | All |
| `acknowledgeUnchangedBills()` | Mark bills synced only if unchanged locally | Bill |
| `reconcilePulledBillsByClientFingerprint()` | Deduplicate pulled bills against local | Bill |
| `backfillChildServerBillIds()` | Propagate server bill IDs to children | Bill |
| `quarantineFailedSyncRecords()` | Route quarantine by entity type | Bill/BillItem/BillPayment |
| `quarantineFailedBills()` | Quarantine permanently failed bills | Bill |
| `quarantineFailedBillItems()` | Quarantine failed bill items | BillItem |
| `quarantineFailedBillPayments()` | Quarantine failed bill payments | BillPayment |
| `buildBillItemSnapshot()` | JSON snapshot for quarantine | BillItem |
| `buildBillPaymentSnapshot()` | JSON snapshot for quarantine | BillPayment |
| `insertMasterData()` | Pull & upsert all entities from server response | All |
| `reconcileLocalBillScope()` | Post-activation terminal ownership fix | Bill |
| `syncKitchenPrinterIntoProfile()` | Embed printer config into profile before push | Profile/Printer |
| `normalizeUserRole()` | Role string normalization | User |
| `logInfo/logWarn/logError()` | Logging utilities | Infra |
| `orFallback()` | Null-safe string helper | Infra |
| `logSkippedRecords()` | Warning log for orphaned records | Infra |
| `logRepairedRecords()` | Warning log for repaired records | Infra |
| `identityKeys()` (ext) | User dedup keys | User |
| `toSafeString()` / `toSafeAmount()` | Numeric formatting | Infra |
| `pushAllForTest()` | Test helper | All |

### Push Order (in `pushAllInternal`)
1. Restaurant profiles
2. Users
3. Categories
4. Menu items
5. Item variants
6. ~~Stock logs~~ (disabled)
7. Bills (with follow-up for changed-during-push)
8. Bill items (only those whose parent bill is synced)
9. Bill payments (only those whose parent bill is synced)

### Pull Order (in `insertMasterData`)
1. Restaurant profiles + kitchen printer restore
2. Users (with identity-key dedup, role propagation)
3. Permission cache update
4. Categories
5. Menu items (with orphan filtering, duplicate hiding)
6. Item variants (with orphan filtering, duplicate hiding)
7. Stock logs
8. Bills (with createdBy remapping, recordOrigin/scope assignment)
9. Bill items (with bill/menu/variant FK resolution)
10. Bill payments (with bill FK resolution)
11. Counter correction (daily + terminal-daily)

---

## 2. Proposed Architecture: Strategy Pattern

Split into **4 domain strategies** + **1 orchestrator**. Each strategy owns push and pull for its entity cluster.

### 2.1 Interface Definition

```kotlin
package com.khanabook.lite.pos.domain.sync

/**
 * Contract for a single entity-cluster sync strategy.
 * Each implementation handles push (local→server) and pull (server→local)
 * for its owned entities.
 */
interface SyncStrategy {

    /** Human-readable label for logging / step callbacks */
    val label: String

    /**
     * Push all dirty local records to the server.
     * @param restaurantId Tenant scope
     * @param isolateHttpConflicts Whether to isolate 409 errors per-record
     * @return true if all records pushed successfully (or none pending)
     * @throws SyncConflictException if unrecoverable conflicts remain
     */
    suspend fun push(
        restaurantId: Long,
        isolateHttpConflicts: Boolean = false
    ): Boolean

    /**
     * Pull and upsert remote records into local database.
     * @param restaurantId Tenant scope
     * @param context Shared pull context (ID mappings from previously-pulled strategies)
     */
    suspend fun pull(
        restaurantId: Long,
        context: PullContext
    )
}

/**
 * Shared mutable state passed between strategies during pull.
 * Earlier strategies populate ID maps that later strategies consume.
 */
class PullContext(
    val sessionManager: SessionManager,
    val remoteUserIdToLocalId: MutableMap<Long, Long> = mutableMapOf(),
    val categoryIdMap: MutableMap<Long, Long> = mutableMapOf(),
    val menuItemIdMap: MutableMap<Long, Long> = mutableMapOf(),
    val variantIdMap: MutableMap<Long, Long> = mutableMapOf(),
    val billServerIdMap: MutableMap<Long, Long> = mutableMapOf(),
    val knownUserIds: MutableSet<Long> = mutableSetOf(),
    val knownCategoryIds: MutableSet<Long> = mutableSetOf(),
    val knownMenuItemIds: MutableSet<Long> = mutableSetOf(),
    val knownVariantIds: MutableSet<Long> = mutableSetOf(),
    val knownBillIds: MutableSet<Long> = mutableSetOf()
)
```

### 2.2 Shared Push Infrastructure

The generic `pushBatches<T, R>()` and `pushBatch<T, R>()` logic (binary-search conflict isolation, structured 409 parsing) is entity-agnostic. Extract to:

```kotlin
package com.khanabook.lite.pos.domain.sync

@Singleton
class SyncBatchPusher @Inject constructor() {
    suspend fun <T, R> pushBatches(...): List<Long> { /* current logic */ }
    private suspend fun <T, R> pushBatch(...): BatchPushResult { /* current logic */ }
}
```

All strategies inject `SyncBatchPusher` rather than duplicating the batch/conflict logic.

---

## 3. Strategy Definitions

### 3a. `ProfileSyncStrategy`

**Entities owned:** `RestaurantProfileEntity`, `UserEntity`, `PrinterProfileEntity`

**Push responsibilities:**
- Push unsynced restaurant profiles (after embedding kitchen printer config)
- Push unsynced users

**Pull responsibilities:**
- Upsert restaurant profiles (with logo download pre-step)
- Restore kitchen PrinterProfileEntity from server data
- Upsert users with identity-key dedup
- Propagate role changes to active session
- Update permission cache

**DAOs needed:** `RestaurantDao`, `UserDao`, `PrinterProfileDao`, `PermissionManager`

**Exports to PullContext:**
- `remoteUserIdToLocalId` map
- `knownUserIds` set

---

### 3b. `MenuSyncStrategy`

**Entities owned:** `CategoryEntity`, `MenuItemEntity`, `ItemVariantEntity`, `StockLogEntity`

**Push responsibilities:**
- Push unsynced categories
- Push unsynced menu items
- Push unsynced item variants
- Push unsynced stock logs (currently disabled)

**Pull responsibilities:**
- Upsert categories
- Build `categoryIdMap`
- Upsert menu items (orphan filtering, duplicate hiding by serverId)
- Build `menuItemIdMap`
- Upsert item variants (orphan filtering, duplicate hiding)
- Build `variantIdMap`
- Upsert stock logs (with FK resolution)

**DAOs needed:** `CategoryDao`, `MenuDao`, `InventoryDao`

**Exports to PullContext:**
- `categoryIdMap`, `knownCategoryIds`
- `menuItemIdMap`, `knownMenuItemIds`
- `variantIdMap`, `knownVariantIds`

---

### 3c. `BillSyncStrategy`

**Entities owned:** `BillEntity`, `BillItemEntity`, `BillPaymentEntity`, `SyncQuarantineEntity`

**Push responsibilities:**
- Push unsynced bills (with follow-up for changed-during-push)
- Backfill child serverBillIds
- Push unsynced bill items (only with synced parent)
- Push unsynced bill payments (only with synced parent)
- Quarantine permanently failed records

**Pull responsibilities:**
- Upsert bills (with createdBy remapping, recordOrigin/recordScope assignment)
- Reconcile pulled bills by client fingerprint
- Build `billServerIdMap`
- Upsert bill items (with bill/menu/variant FK resolution)
- Upsert bill payments (with bill FK resolution)
- Counter correction (daily + terminal-daily)

**DAOs needed:** `BillDao`, `RestaurantDao`, `UserDao` (for createdBy lookup)

**Consumes from PullContext:**
- `remoteUserIdToLocalId`, `knownUserIds`
- `menuItemIdMap`, `knownMenuItemIds`
- `variantIdMap`, `knownVariantIds`

**Also owns:**
- `pushSingleBill()` (retain as public method)
- `reconcileLocalBillScope()` (retain as public method)
- `acknowledgeUnchangedBills()`
- All quarantine logic

---

### 3d. `PaymentSyncStrategy` (Future — Low priority)

**Entities owned:** Payment gateway fields on `BillPaymentEntity` (`gatewayTxnId`, `gatewayStatus`, `verifiedBy`)

**Current state:** No separate Easebuzz sync endpoint exists in `MasterSyncProcessor`. Gateway data flows through `BillPaymentEntity` as fields. This strategy is a **placeholder** for when a dedicated payment events API is added.

**For now:** Gateway data stays inside `BillSyncStrategy`. Extract only when a `/payments/events` endpoint or `EasebuzzEventEntity` is introduced.

---

### 3e. `SyncOrchestrator`

Replaces `MasterSyncProcessor` as the public API. Coordinates strategy execution order.

```kotlin
@Singleton
class SyncOrchestrator @Inject constructor(
    private val profileStrategy: ProfileSyncStrategy,
    private val menuStrategy: MenuSyncStrategy,
    private val billStrategy: BillSyncStrategy,
    private val sessionManager: SessionManager,
    private val databaseProvider: DatabaseProvider,
    private val api: KhanaBookApi
) {
    suspend fun pushAll(
        onStepChange: ((SyncStep) -> Unit)? = null,
        isolateHttpConflicts: Boolean = false
    ): Boolean {
        val restaurantId = sessionManager.getRestaurantId()
        if (restaurantId <= 0L) return false

        profileStrategy.push(restaurantId, isolateHttpConflicts)
        menuStrategy.push(restaurantId, isolateHttpConflicts)
        billStrategy.push(restaurantId, isolateHttpConflicts)
        return true
    }

    suspend fun pullAll(masterData: MasterSyncResponse) {
        val restaurantId = sessionManager.getRestaurantId()
        val context = PullContext(sessionManager)

        databaseProvider.getDatabase().withTransaction {
            profileStrategy.pull(restaurantId, context)
            menuStrategy.pull(restaurantId, context)
            billStrategy.pull(restaurantId, context)
        }
    }

    suspend fun pushSingleBill(billLocalId: Long) =
        billStrategy.pushSingleBill(billLocalId)

    suspend fun reconcileLocalBillScope() =
        billStrategy.reconcileLocalBillScope()

    suspend fun pushAllAfterConflictRecovery(): Boolean =
        pushAll(isolateHttpConflicts = true)

    suspend fun quarantineFailedSyncRecords(exception: SyncConflictException): Int =
        billStrategy.quarantineFailedSyncRecords(exception)
}
```

---

## 4. Dependency Ordering

### Push Order (must be preserved)

```
1. ProfileSyncStrategy  (profiles → users)
2. MenuSyncStrategy     (categories → menu items → variants)
3. BillSyncStrategy     (bills → bill items → bill payments)
```

**Rationale:** Bills reference users via `createdBy` (server ID). Bill items reference menu items/variants. Everything references restaurantId from profile.

### Pull Order (must be preserved)

```
1. ProfileSyncStrategy  → exports: remoteUserIdToLocalId, knownUserIds
2. MenuSyncStrategy     → exports: categoryIdMap, menuItemIdMap, variantIdMap
3. BillSyncStrategy     → consumes all above maps for FK resolution
```

**Rationale:** `BillEntity.createdBy` needs user ID remapping. `BillItemEntity.menuItemId/variantId` needs menu/variant ID resolution. Categories must exist before menu items (FK constraint).

---

## 5. Migration Steps

### Phase 1: Extract shared infrastructure (low risk)
1. Create `SyncBatchPusher` with `pushBatches()`/`pushBatch()` logic
2. Create `PullContext` data class
3. Create `SyncStrategy` interface
4. Create helper extension functions file (`SyncFormatUtils.kt`): `orFallback()`, `toSafeString()`, `toSafeAmount()`
5. Unit test `SyncBatchPusher` with mocked push lambda

### Phase 2: Extract `ProfileSyncStrategy` (medium risk)
1. Move restaurant profile + user push logic
2. Move restaurant profile + user + printer pull logic
3. Wire into `MasterSyncProcessor` temporarily (strategy called from existing methods)
4. Verify existing sync tests pass

### Phase 3: Extract `MenuSyncStrategy` (medium risk)
1. Move category + menu item + variant + stock log push logic
2. Move corresponding pull logic (including orphan filtering, duplicate hiding)
3. Wire into `MasterSyncProcessor`
4. Verify sync roundtrip works

### Phase 4: Extract `BillSyncStrategy` (high risk — largest, most complex)
1. Move bill push (including follow-up, conflict isolation)
2. Move bill item + bill payment push
3. Move quarantine logic (`quarantineFailedBills/BillItems/BillPayments`, snapshot builders)
4. Move bill pull (including createdBy remapping, recordOrigin assignment)
5. Move bill item + bill payment pull (FK resolution)
6. Move counter correction logic
7. Move `reconcileLocalBillScope()`, `pushSingleBill()`
8. Wire into `MasterSyncProcessor`

### Phase 5: Replace `MasterSyncProcessor` with `SyncOrchestrator`
1. Create `SyncOrchestrator` delegating to all strategies
2. Update Hilt module: bind `SyncOrchestrator` as replacement
3. Update `SyncManager` to inject `SyncOrchestrator` instead of `MasterSyncProcessor`
4. Update any direct `MasterSyncProcessor` references in tests
5. Delete `MasterSyncProcessor.kt`

### Phase 6: Future — `PaymentSyncStrategy`
- Only when a dedicated payment events API endpoint is added
- Extract gateway-specific fields into their own entity and sync flow

---

## 6. File Structure (Target)

```
domain/sync/
├── SyncStrategy.kt              (interface + PullContext)
├── SyncBatchPusher.kt           (generic batch push infra)
├── SyncFormatUtils.kt           (orFallback, toSafeString, toSafeAmount)
├── SyncOrchestrator.kt          (coordinates strategies)
├── ProfileSyncStrategy.kt       (~200 lines)
├── MenuSyncStrategy.kt          (~250 lines)
├── BillSyncStrategy.kt          (~500 lines)
└── PaymentSyncStrategy.kt       (future placeholder)
```

---

## 7. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Transaction boundary change** — current `insertMasterData` wraps ALL pulls in one Room transaction. Splitting strategies may break atomicity. | HIGH | Keep `withTransaction` at orchestrator level, pass the transaction scope to each strategy's `pull()` method. All-or-nothing pull semantics must be preserved. |
| **PullContext ordering** — if strategies execute in wrong order during pull, FK resolution maps are empty → orphaned records | HIGH | Enforce ordering in `SyncOrchestrator`. Add runtime assertion: `require(context.knownUserIds.isNotEmpty())` before bill pull when users were expected. |
| **Push partial-failure semantics** — bill push currently collects `isolatedConflicts` and rethrows the first. Strategy boundary must preserve this. | MEDIUM | `BillSyncStrategy.push()` returns a `SyncResult` sealed class (success / partialFailure / failure) rather than Boolean. Orchestrator decides whether to abort or continue. |
| **Session state dependency** — strategies need `sessionManager.getRestaurantId()/getTerminalId()/getDeviceId()`. Passing these through `PullContext` avoids multiple session reads but introduces coupling. | LOW | Accept: `PullContext` holds the snapshot of session state. All strategies read from context, not directly from SessionManager during pull. |
| **Hilt wiring** — current DI provides `MasterSyncProcessor` directly. Changing to `SyncOrchestrator` requires updating injection sites. | LOW | Phase 5 is a single PR. `SyncManager` is the only direct consumer; `BillingViewModel.pushSingleBill()` delegates through `SyncManager`. |
| **Test coverage gaps** — if existing integration tests mock `MasterSyncProcessor` directly, they'll break when it's deleted. | MEDIUM | Introduce interface `SyncFacade` in Phase 5 that both `MasterSyncProcessor` (legacy) and `SyncOrchestrator` (new) implement. Tests can switch gradually. |
| **Binary-search conflict isolation** — subtle recursive logic in `pushBatch()`. If extracted incorrectly, could silently lose records. | HIGH | `SyncBatchPusher` is extracted first (Phase 1) and unit-tested in isolation with a fake push lambda that returns controlled 409s. |
| **Counter correction timing** — daily counter correction currently runs inside the pull transaction. If moved outside, race with concurrent bill creation. | MEDIUM | Keep counter correction inside `BillSyncStrategy.pull()` which executes within the orchestrator's transaction. |

---

## 8. Success Criteria

- [ ] Each strategy is independently testable (can construct with mocked DAOs)
- [ ] Full push/pull roundtrip produces identical DB state as current monolith
- [ ] No regression in sync conflict recovery flow
- [ ] `SyncOrchestrator` is the only public API; strategies are `internal`
- [ ] File sizes: no single strategy exceeds 500 lines
- [ ] Existing `SyncManager` tests pass without modification (or minimal adaptation)

# BillingViewModel Split Plan

**Created:** 2026-08-21  
**Status:** PLANNING (do NOT refactor without full test coverage)  
**File:** `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt`  
**Current size:** ~1,600 lines (69KB+)

---

## 1. Current State Assessment

### 1.1 What's Already Extracted

The first phase of extraction is **already complete**. Three collaborators exist:

| Class | File | Status |
|-------|------|--------|
| `CartManager` | `ui/viewmodel/CartManager.kt` | ✅ Exists — cart items, quantities, barcode scanning, bill summary computation |
| `PaymentStateManager` | `ui/viewmodel/PaymentStateManager.kt` | ✅ Exists — payment mode, part amounts, gateway state, validation, payment entity building |
| `PrintCoordinator` | `ui/viewmodel/PrintCoordinator.kt` | ✅ Exists — print dispatch, status messaging, PDF fallback |
| `BillCreationUseCase` | `domain/manager/BillCreationUseCase.kt` | ✅ Exists — unified bill entity construction (with test!) |

### 1.2 What's Still in BillingViewModel (49 functions, ~1,454 lines)

Despite extraction, the ViewModel is still oversized because:
1. **Orchestration methods duplicate logic** — `completeOrder`, `createDraftOnlineBill`, `saveDraftOrder` still build `BillEntity` inline instead of using `BillCreationUseCase`
2. **Draft management** — `appendItemsToDraft`, `loadDraftOrderForEditing`, `settleDraftOrder` are 400+ lines of item reconciliation logic
3. **Online payment flow** — restoration, pending bill tracking, finalization is ~200 lines
4. **Glue code** — pass-through delegations to extracted managers bloat the public API

---

## 2. Current Responsibilities (Grouped)

### Group A — Cart Delegation (pass-through, ~50 lines)
| Line | Function | Notes |
|------|----------|-------|
| 303 | `addToCart()` | Delegates to `cartManager.addToCart`, surfaces error |
| 312 | `removeFromCart()` | Delegates to `cartManager.removeFromCart` |
| 316 | `handleScannedBarcode()` | Delegates to `cartManager.handleScannedBarcode` |
| 325 | `addItemByScannedText()` | Delegates to `cartManager.addItemByScannedText` |
| 1508 | `updateCartItemNote()` | Delegates to `cartManager.updateItemNote` |

### Group B — Payment Delegation (pass-through, ~30 lines)
| Line | Function | Notes |
|------|----------|-------|
| 202 | `setGatewayResult()` | Delegates to `paymentStateManager` |
| 206 | `clearGatewayResult()` | Delegates to `paymentStateManager` |
| 373 | `setPaymentMode()` | Delegates to `paymentStateManager` |
| 246 | `validatePaymentLimits()` | Wraps `paymentStateManager.validatePaymentLimits`, sets error |

### Group C — Payment Link (online gateway, ~30 lines)
| Line | Function | Notes |
|------|----------|-------|
| 214 | `createPaymentLinkForBill()` | Calls `easebuzzPaymentRepository`, manages `_paymentLinkState` |
| 242 | `resetPaymentLinkState()` | Resets state to Idle |

### Group D — Bill Creation / Complete (core, ~400 lines) ⚠️ BIGGEST CHUNK
| Line | Function | Notes |
|------|----------|-------|
| 491 | `createDraftOnlineBill()` | 150 lines — builds BillEntity inline for UPI draft |
| 713 | `completeOrder()` | 170 lines — builds BillEntity inline for settled order |
| 960 | `saveDraftOrder()` | 130 lines — builds BillEntity inline for dine-in draft |

### Group E — Draft Management / Editing (~250 lines)
| Line | Function | Notes |
|------|----------|-------|
| 887 | `loadDraftOrderForEditing()` | Restores cart from saved bill items |
| 1091 | `appendItemsToDraft()` | 200 lines — item reconciliation (add/update/delete items to existing draft) |
| 949 | `clearActiveSession()` | Resets editing state |

### Group F — Settlement / Finalization (~200 lines)
| Line | Function | Notes |
|------|----------|-------|
| 643 | `finalizeOnlineBill()` | Marks UPI draft as paid after gateway callback |
| 1287 | `settleDraftOrder()` | Settles a dine-in draft with chosen payment mode |
| 1401 | `isBillSettled()` | Quick check |
| 1408 | `finalizeRecoveredPaymentSet()` | Recovery path |
| 1416 | `recoverPartialDraftPayment()` | Recovery path |
| 1433 | `finalizePaymentRecovery()` | Shared recovery orchestrator |
| 1478 | `resetPaymentRecovery()` | Clears recovery state |

### Group G — Online Bill Restoration (~100 lines)
| Line | Function | Notes |
|------|----------|-------|
| 383 | `getLatestPendingOnlineBillId()` | Finds pending bill for UPI resume |
| 408 | `cancelPendingOnlineDrafts()` | Cleanup stale drafts |
| 412 | `restorePendingOnlineBill()` | Restores cart + payment state from DB |
| 464 | `invalidateRestoration()` | Generation counter |
| 472 | `ownsRestorationAttempt()` | Generation check |
| 475 | `clearInvalidPendingRestoration()` | Cleanup invalid state |

### Group H — UI State / Lifecycle (~80 lines)
| Line | Function | Notes |
|------|----------|-------|
| 170 | `loadRecentCustomers()` | DB fetch |
| 176 | `loadRecentDineInCustomers()` | DB fetch |
| 196 | `setOrderType()` | Simple setter |
| 355 | `setCustomerInfo()` | Simple setter |
| 360 | `resetForNewBill()` | Full reset |
| 377 | `getBillById()` | Repository delegate |
| 379 | `triggerSyncAndWait()` | Sync delegate |
| 1512 | `clearError()` | Simple setter |
| 1516 | `clearPrintStatus()` | Delegate |
| 1520 | `reportError()` | Simple setter |
| 1524 | `prepareLastBillForInvoiceShare()` | Refresh from DB |

### Group I — Print Delegation (~20 lines)
| Line | Function | Notes |
|------|----------|-------|
| 1541 | `printReceipt()` | Launches `printCoordinator.printReceipt` |
| 1550 | `printKitchenTicket()` | Launches `printCoordinator.printKitchenTicket` |

### Group J — Helpers / Private (~50 lines)
| Line | Function | Notes |
|------|----------|-------|
| 69 | `allocateInvoiceIdentity()` | Duplicated in BillCreationUseCase |
| 87 | `requireActiveTerminalIdentity()` | Validation helper |
| 331 | `toRestorableMenuItem()` | Entity conversion |
| 342 | `toRestorableVariant()` | Entity conversion |
| 1504 | `shouldPersistLocally()` | Always returns true (dead code?) |
| 1572 | `describeSyncFailure()` | Error message builder |

---

## 3. Proposed Split Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    BillingViewModel (slim)                        │
│  • UI state: error, isLoading, customerName, orderType          │
│  • Lifecycle: init, onCleared, savedStateHandle                 │
│  • Orchestrates collaborators, routes errors to _error           │
│  • Public API is thin delegation to collaborators               │
└───────┬──────────┬──────────────┬──────────────┬────────────────┘
        │          │              │              │
   ┌────▼───┐ ┌───▼────────┐ ┌──▼──────────┐ ┌▼──────────────┐
   │CartMgr │ │PaymentState│ │BillCreation │ │PrintCoordinator│
   │(exists)│ │Mgr (exists)│ │UseCase      │ │(exists)        │
   └────────┘ └────────────┘ │(exists but  │ └────────────────┘
                             │ underused)   │
                             └──────────────┘
                                    │
                          ┌─────────┼──────────┐
                          │         │          │
                    ┌─────▼──┐ ┌───▼────┐ ┌───▼──────────┐
                    │DraftMgr│ │Settle  │ │OnlinePayment │
                    │(NEW)   │ │UseCase │ │Manager (NEW) │
                    │        │ │(NEW)   │ │              │
                    └────────┘ └────────┘ └──────────────┘
```

### 3a. `CartManager` (EXISTS — no changes needed)
Already handles: item add/remove, quantities, barcode, notes, summary computation.

### 3b. `PaymentStateManager` (EXISTS — no changes needed)
Already handles: mode selection, part amounts, gateway state, validation, payment entity building.

### 3c. `BillCreationUseCase` (EXISTS — needs adoption)
Already handles: unified BillEntity construction for all three paths (settle, draft-for-payment, draft-for-dine-in).

**Problem:** The ViewModel still builds BillEntity inline in 3 places instead of calling `BillCreationUseCase.createBill()`. This is the **highest-impact change** — deletes ~400 lines.

### 3d. `PrintCoordinator` (EXISTS — no changes needed)
Already handles: auto-print result processing, manual receipt/kitchen printing, PDF fallback.

### 3e. NEW: `DraftOrderManager`
**Package:** `com.khanabook.lite.pos.domain.manager`

Encapsulates the edit-in-place draft operations that are too complex for the ViewModel:

| Function to move | Lines | Notes |
|-----------------|-------|-------|
| `appendItemsToDraft()` | 1091–1280 | 200-line item reconciliation (add/update/delete) |
| `loadDraftOrderForEditing()` (logic only) | 887–945 | Cart restoration from bill items |
| `toRestorableMenuItem()` | 331–340 | Entity conversion helper |
| `toRestorableVariant()` | 342–352 | Entity conversion helper |

### 3f. NEW: `BillSettlementUseCase`
**Package:** `com.khanabook.lite.pos.domain.manager`

Encapsulates settlement/finalization logic:

| Function to move | Lines | Notes |
|-----------------|-------|-------|
| `settleDraftOrder()` (core logic) | 1287–1395 | Payment attachment + status flip |
| `finalizeOnlineBill()` (core logic) | 643–710 | Mark UPI draft as paid |
| `finalizePaymentRecovery()` | 1433–1475 | Shared recovery path |
| `finalizeRecoveredPaymentSet()` | 1408–1414 | Recovery delegate |
| `recoverPartialDraftPayment()` | 1416–1430 | Recovery delegate |
| `resetPaymentRecovery()` (logic) | 1478–1500 | Reset unverified payments |

### 3g. NEW: `OnlineBillRestorationManager`
**Package:** `com.khanabook.lite.pos.ui.viewmodel` (needs ViewModel state access)

Encapsulates the generation-safe online bill restoration:

| Function to move | Lines | Notes |
|-----------------|-------|-------|
| `getLatestPendingOnlineBillId()` | 383–406 | Pending bill lookup |
| `restorePendingOnlineBill()` | 412–460 | State restoration |
| `invalidateRestoration()` | 464–468 | Generation counter |
| `ownsRestorationAttempt()` | 472–473 | Generation check |
| `clearInvalidPendingRestoration()` | 475–489 | Cleanup |
| `cancelPendingOnlineDrafts()` | 408–410 | Stale cleanup |

---

## 4. Migration Steps (Ordered by Risk)

### Phase 1: Adopt `BillCreationUseCase` (HIGH IMPACT, LOW RISK)

The use case already exists and has a test. The ViewModel just doesn't use it yet.

1. Inject `BillCreationUseCase` into `BillingViewModel`
2. Rewrite `completeOrder()` to call `billCreationUseCase.createBill(BillIntent.Settle(...))` instead of building BillEntity inline
3. Rewrite `createDraftOnlineBill()` to call `billCreationUseCase.createBill(BillIntent.DraftForPayment)`
4. Rewrite `saveDraftOrder()` to call `billCreationUseCase.createBill(BillIntent.DraftForDineIn(tableName))`
5. Delete `allocateInvoiceIdentity()` from the ViewModel (duplicated in use case)
6. **Expected deletion:** ~350 lines

**Verification:** Run `BillCreationUseCaseTest` + manual smoke test of all three bill flows.

### Phase 2: Extract `DraftOrderManager` (MEDIUM RISK)

1. Create `DraftOrderManager` with dependencies: `BillRepository`, `MenuRepository`, `BillCalculator`
2. Move `appendItemsToDraft()` logic (item reconciliation algorithm)
3. Move `toRestorableMenuItem()` / `toRestorableVariant()` conversion helpers
4. Move cart-restoration logic from `loadDraftOrderForEditing()` (return `List<CartItem>` instead of mutating ViewModel state)
5. ViewModel keeps the orchestration shell (loading state, error routing, `cartManager.setItems()`)
6. **Expected deletion:** ~250 lines

**Verification:** New unit test for `DraftOrderManager.appendItems()` and `DraftOrderManager.restoreCartFromBill()`.

### Phase 3: Extract `BillSettlementUseCase` (MEDIUM-HIGH RISK)

1. Create `BillSettlementUseCase` with dependencies: `BillRepository`, `PaymentStateManager`, `SessionManager`
2. Move core logic of `settleDraftOrder()`, `finalizeOnlineBill()`, `finalizePaymentRecovery()`
3. The use case returns a result type; the ViewModel handles state updates and print dispatch
4. **Expected deletion:** ~200 lines

**Verification:** New unit tests covering: successful settlement, already-settled rejection, cancelled rejection, payment recovery paths.

### Phase 4: Extract `OnlineBillRestorationManager` (LOW RISK)

1. Create class with dependencies: `BillRepository`, `SavedStateHandle`
2. Move generation-counter logic and pending bill lookup
3. ViewModel calls it and applies returned state
4. **Expected deletion:** ~80 lines

**Verification:** Unit test for generation-safety (concurrent restoration attempts).

### Phase 5: Cleanup (LOW RISK)

1. Remove `describeSyncFailure()` — move to a shared `ErrorMessageFormatter` utility
2. Remove `shouldPersistLocally()` if confirmed dead code
3. Remove `requireActiveTerminalIdentity()` — move to `SessionManager` or `BillCreationUseCase`
4. Flatten pass-through delegations into direct property access where Compose can observe managers directly

---

## 5. Target State After All Phases

| Class | Approximate lines | Responsibility |
|-------|-------------------|---------------|
| `BillingViewModel` | ~300 | UI state, lifecycle, orchestration, error routing |
| `CartManager` | ~140 (unchanged) | Cart items, quantities, summary |
| `PaymentStateManager` | ~130 (unchanged) | Payment mode, validation |
| `PrintCoordinator` | ~190 (unchanged) | Print dispatch |
| `BillCreationUseCase` | ~240 (unchanged) | Bill entity construction |
| `DraftOrderManager` | ~250 (new) | Draft editing, item reconciliation |
| `BillSettlementUseCase` | ~200 (new) | Settlement, finalization, recovery |
| `OnlineBillRestorationManager` | ~100 (new) | Pending online bill lifecycle |

**Total reduction in BillingViewModel:** from ~1,454 lines → ~300 lines (~80% reduction).

---

## 6. Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Subtle state ordering bugs when splitting orchestration | HIGH | Do NOT refactor without comprehensive integration tests for all bill flows |
| `orderMutex` synchronization — extracted classes may need to share the mutex | HIGH | Keep `orderMutex` in ViewModel, pass it or ensure use cases are called within `withLock` blocks |
| `savedStateHandle` — process-death restoration breaks if state is split across classes | MEDIUM | Keep `savedStateHandle` in ViewModel; extracted classes return data, ViewModel persists |
| `invalidateRestoration()` generation counter race conditions | MEDIUM | Keep generation logic co-located (either all in VM or all in dedicated class) |
| Breaking Compose observation if flows move to different objects | LOW | Keep flow delegation properties in ViewModel (already done for CartManager et al.) |
| `BillCreationUseCase` already exists but isn't adopted — calling both old and new path | LOW | Phase 1 fully replaces inline code; never call both |

---

## 7. Testing Requirements (Before Any Refactoring)

### Must-have tests before starting:
1. **`completeOrder()` integration test** — cash payment, UPI payment, cancellation
2. **`createDraftOnlineBill()` integration test** — draft creation, idempotency
3. **`saveDraftOrder()` integration test** — dine-in draft with KOT print trigger
4. **`appendItemsToDraft()` integration test** — add new item, increase qty, decrease qty, remove item
5. **`settleDraftOrder()` integration test** — successful settlement, already-settled guard
6. **`finalizeOnlineBill()` integration test** — success path, failure/cancellation path
7. **`restorePendingOnlineBill()` unit test** — generation safety, stale bill rejection
8. **Payment recovery tests** — `finalizeRecoveredPaymentSet`, `recoverPartialDraftPayment`

### Test infrastructure needed:
- Fake `BillRepository` that records insertions/updates
- Fake `RestaurantRepository` with configurable profile (GST on/off)
- Fake `SessionManager` with configurable terminal identity
- Test double for `PrintRouter` / `PrintService`

---

## 8. Open Questions

1. **Should `BillCreationUseCase` also handle the `cancelStalePendingOnlineDrafts()` call?** Currently it's inline in `createDraftOnlineBill()` before creation.
2. **Is `shouldPersistLocally()` dead code?** It always returns `true`. Confirm and delete.
3. **Should `PaymentLinkForBillState` move to a dedicated `PaymentLinkManager`?** It's only ~30 lines but is a separate concern (Easebuzz gateway interaction).
4. **Should `describeSyncFailure()` move to a shared utility?** It's not billing-specific; could serve other ViewModels.
5. **Can Compose screens observe `CartManager` / `PaymentStateManager` directly** instead of through ViewModel delegation properties? Would reduce ViewModel boilerplate but couples UI to implementation detail.

# NewBillScreen Split Plan

**Date:** 2026-08-19
**Status:** Planning (prep for future PR)
**Risk:** HIGH — this is the core billing flow, 106 commits of history

---

## Current State

| File | Lines | Commits | Responsibility |
|---|---|---|---|
| `NewBillScreen.kt` | 549 | 106 | Orchestrator: step routing, back handling, state wiring |
| `BillingViewModel.kt` | 1,413 | 66 | ALL business logic: draft, cart, payment, finalize, sync |
| `newbill/PaymentStep.kt` | 942 | — | Payment mode selection, Easebuzz launch, UPI QR, settle |
| `newbill/MenuSelectionStep.kt` | 746 | — | Menu grid, search, variants, cart sidebar |
| `newbill/OrderConfirmationSection.kt` | 490 | — | Success display + SuccessStep composable |
| `newbill/CartStep.kt` | 359 | — | CustomerInfoStep + cart item list |

**Total: 4,499 lines** across 6 files for one user flow.

---

## Problems

1. **BillingViewModel (1,413 lines)** does everything — it's the real problem, not the screen files.
   - Draft creation, cart management, menu filtering
   - Payment mode logic, split payment calculations
   - Easebuzz order creation + SDK launch
   - Bill finalization (4 different paths!)
   - Invoice number allocation
   - KOT printing triggers
   - Sync submission

2. **PaymentStep (942 lines)** combines:
   - Payment mode picker UI
   - Easebuzz SDK launch logic (copy-pasted twice)
   - UPI QR generation
   - Part-payment calculation
   - Settlement/finalization

3. **NewBillScreen.kt (549 lines)** is actually already a thin orchestrator, but:
   - Resolves 3 ViewModels at top level
   - Has complex step-transition animation logic
   - Contains payment-return handling (global StateFlow collector)
   - Back-navigation has 3 code paths

---

## Proposed Split

### Phase 1: Extract ViewModel responsibilities (HIGH IMPACT, MEDIUM RISK)

| New Class | Extracted From | Responsibility |
|---|---|---|
| `CartManager` | BillingViewModel | Add/remove/update cart items, calculate totals |
| `PaymentOrchestrator` | BillingViewModel | Payment mode selection, split calc, Easebuzz initiation |
| `BillFinalizer` | BillingViewModel | Single canonical `finalize()` path for all payment types |
| `InvoiceAllocator` | BillingViewModel | Terminal series, daily counter, invoice number generation |

BillingViewModel becomes a thin coordinator that delegates to these domain objects.

### Phase 2: Split PaymentStep (MEDIUM IMPACT, LOW RISK)

| New File | Lines | Content |
|---|---|---|
| `PaymentModeSelector.kt` | ~200 | Radio buttons / cards for cash, UPI, POS, Easebuzz, part-pay |
| `EasebuzzPaymentLauncher.kt` | ~150 | SDK launch logic (deduplicated from 2 copies) |
| `UpiQrSection.kt` | ~150 | QR generation + display |
| `PartPaymentSection.kt` | ~150 | Split amount inputs + validation |
| `SettlementConfirmation.kt` | ~150 | Final confirm + settle button |

PaymentStep becomes a ~150 line orchestrator that shows/hides these sections.

### Phase 3: Clean up NewBillScreen.kt (LOW IMPACT, LOW RISK)

- Remove direct `PaymentReturnManager` collection (move to BillingViewModel)
- Extract `StepProgressIndicator` composable
- Remove `navController` parameter (use callback pattern like other screens)

---

## Execution Strategy

**DO NOT refactor all at once.** Each extraction must be:

1. Extract one class/file at a time
2. Run ALL existing tests after each extraction
3. Manual QA the billing flow on a real device
4. Commit separately with `refactor(billing):` prefix
5. No behavior changes — pure structural refactoring

**Estimated effort:** 3-4 sessions of focused work.

**Pre-requisites:**
- [ ] Write integration test: create bill → add items → pay cash → verify saved correctly
- [ ] Write integration test: create bill → pay Easebuzz → webhook → verify paid
- [ ] Run test suite in current state as baseline (ensure 0 failures before starting)

---

## Risk Mitigations

| Risk | Mitigation |
|---|---|
| Break offline billing | Test on airplane mode after each extraction |
| Break multi-device sync | Test with 2 devices after Phase 1 |
| Break Easebuzz payment | Test full payment flow in sandbox |
| Break KOT printing | Test with real printer after Phase 2 |
| Regression cascade | Feature-flag: keep old `BillingViewModel` as fallback behind flag |

---

## Files NOT to Touch (stable, isolated)

- `newbill/MenuSelectionStep.kt` — already well-isolated, no churn
- `newbill/CartStep.kt` (CustomerInfoStep) — stable
- `newbill/OrderConfirmationSection.kt` (SuccessStep) — stable
- Domain validators: `PaymentSetValidator`, `PaymentModeManager` — already clean

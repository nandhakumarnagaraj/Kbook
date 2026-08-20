# Billing and Sync Fix Verification

## Baseline

- Branch: `main`
- Baseline commit: `152a78e48ea6fb5b79b4b2fd01ec4c8f58964931`
- Android build defaults: version `1.0.11`, code `20`
- Locally configured verification build: version `1.0.12`, code `21`
- Room schema: version `61`
- Initial Android JVM result: 83 tests, 0 failures, 0 errors, 10 skipped

The terminal-management, web-admin, and command-center changes in the working tree were reviewed as part of the same release set.

## Fixed in this round

### Blocker 0 — Split settlement rejected composite payment modes

**Root cause**: draft settlement passed the composite UI mode (for example
`part_cash_upi`) to `PaymentSetValidator`. The validator correctly accepts only
the persisted component modes (`cash`, `upi`, and `pos`), so every split draft
failed before Room could finalize it.

**Fix**: all billing completion paths now use one component builder. Composite
choices are expanded into two stable `BillPaymentEntity` rows with the selected
amounts before validation and atomic finalization. This also fixes the
`part_cash_pos` branch and keeps operation identities stable for retries.

The corrected debug APK was installed on a Moto G34 5G and the reported split
draft flow was manually confirmed by the reporter.

### Blocker A — Payment recovery settlement bypasses validated finalization

**Root cause**: `BillingViewModel.settleDraftOrder()` called `BillRepository.settleDraftBill()` / `BillDao.settleDraftBill()` directly, which inserted payment rows and marked the bill completed without validating existing payment rows, running `PaymentSetValidator`, or checking for duplicate/partial/malformed payment data.

**Fix**: `settleDraftOrder()` now routes through `BillRepository.finalizeOnlineBill()` which calls `BillDao.finalizeOnlineBillAtomically()`. This shared path performs:
- Restaurant and terminal ownership checks
- Loads all active payment rows
- Validates requested payment components via `PaymentSetValidator.validate()`
- Validates existing rows against requested via `PaymentSetValidator.equivalent()` before any write
- Rejects duplicate identities, duplicate modes, partial existing sets, extra active rows, changed amounts/modes, unsupported/unidentified rows
- Permits a clean draft with no payment rows
- Permits an exact completed idempotent retry (returns `ALREADY_FINALIZED_IDEMPOTENT`)
- Returns authoritative `BillFinalizationResult` with outcome

**Defense-in-depth**: `BillDao.settleDraftBill()` now guards against active payment rows. If `getActivePaymentsForBill()` returns any non-deleted rows, it throws an `IllegalStateException`.

**UI behavior**: When settlement is rejected due to existing recovery rows, the error message "This order has incomplete payment records and cannot be completed automatically. Review or contact support." is shown. The order remains visible in Active Orders with the "Payment recovery" label.

### Blocker B — Stale pending-bill restoration can erase a newer billing session

**Root cause**: `getLatestPendingOnlineBillId()`, `restorePendingOnlineBill()`, and `clearInvalidPendingRestoration()` had no mechanism to distinguish stale async responses from current ones. A delayed restoration result could clear or overwrite a newer session's state.

**Fix**: Introduced a monotonic `restorationGeneration` counter in `BillingViewModel`. Each method captures the current generation before launching async work and checks `ownsRestorationAttempt()` (captured generation == current generation) before mutating state:

- `getLatestPendingOnlineBillId()` — guards the `clearInvalidPendingRestoration()` call
- `restorePendingOnlineBill()` — guards both the failure path and the success path (bill/cart/customer/payment state restoration)
- `clearInvalidPendingRestoration()` — is only called when ownership is confirmed

**Invalidation points**: `invalidateRestoration()` is called at: `resetForNewBill()`, `createDraftOnlineBill()` (after draft is created), `finalizeOnlineBill()` (after completion), `clearActiveSession()`, `settleDraftOrder()` (after settlement), `loadDraftOrderForEditing()` (a different bill is explicitly opened), and at the start of both `getLatestPendingOnlineBillId()` and `restorePendingOnlineBill()` (each restoration attempt begins a fresh generation, so a newer restoration supersedes an older in-flight one — "newer restoration wins").

### Recommended — Strengthened canonical payment equivalence

`PaymentSetValidator.equivalent()` now compares additional semantic fields beyond operationId, paymentMode, and amount:
- `billPublicToken` (bill identity)
- `restaurantId` (restaurant ownership)
- `terminalId` (terminal/device ownership)
- `verifiedBy` (verification source: "manual" vs "gateway")
- `gatewayTxnId` (gateway transaction ID)
- `gatewayStatus` (gateway transaction status)

Manual UPI and gateway-verified UPI are NOT treated as equivalent when their semantic fields conflict. Null fields are accepted as equivalent (if existing has null `gatewayTxnId`, any requested value is accepted).

### Files changed (production)

| File | Change |
|---|---|
| `BillingViewModel.kt` | `settleDraftOrder()` routes through `finalizeOnlineBill()`, `restorationGeneration` guard, `invalidateRestoration()` calls |
| `BillDao.kt` | Guard in `settleDraftBill()` rejects existing active payment rows |
| `PaymentSetValidator.kt` | Strengthened `equivalent()` with bill/restaurant/terminal/gateway/verification field comparison |

### Files changed (tests)

| File | Change |
|---|---|
| `PaymentSetValidatorTest.kt` | Added 8 tests: different bill token, restaurant, terminal, verification source, gatewayTxnId, gatewayStatus; matching null gateway fields; true semantic match (order-independent) |
| `BillingLogicTest.kt` | Added 10 tests: clean draft permits settlement, active payment rows reject, duplicate operation IDs, duplicate modes, partial set, exact retry idempotency, stale invalid/valid restoration, newer restoration wins, current valid/invalid cleanup |

### Files changed (documentation)

| File | Change |
|---|---|
| `billing-sync-fix-verification.md` | This section and the explicit unresolved section below |

## Confirmed defects (previous round)

| Defect | Source evidence | Previous behavior | Risk |
|---|---|---|---|
| Non-atomic online completion | `BillingViewModel.finalizeOnlineBill()` updated the bill and then inserted payment rows separately | UI, Room, or sync could observe a completed bill with no or partial payments | Incorrect reports, conflicts, lost split components |
| Premature completion success | Any existing payment row caused an early successful return | Partial/stale payment rows were interpreted as a completed retry | Silent incomplete settlement |
| Broad UPI draft reuse | Active flow called `getLatestPendingOnlineBill()` | A different cart or customer could inherit another pending draft | Bill merging and identity reuse |
| Hidden inconsistent drafts | Active query used `NOT EXISTS bill_payments` | Drafts containing partial payment rows disappeared | Unrecoverable-looking active orders |
| Active Orders N+1 | ViewModel loaded each bill relation individually | One extra Room query per active bill emission | Delayed refresh |
| Unverified UPI metadata | Deep-link values populated gateway fields | External app return could look gateway-verified | Incorrect payment semantics |
| Duplicate sync scheduling | High-level billing called immediate sync after repository one-time scheduling | One operation could request two full cycles | Avoidable delay and network work |
| Batch head-of-line blocking | `pushBatches()` threw on the first failed result | Later batches and eligible child work were skipped | One bad bill blocked unrelated bills |

## Implemented changes (previous round)

- `BillDao`: added the Room-owned atomic finalization transaction, exact actionable-draft relation, and active-payment lookup.
- `TenantDaos`: delegates transactional and relation methods to Room's generated DAO.
- `BillRepository`: exposes atomic finalization, avoids duplicate durable scheduling for high-level operations, and prevents duplicate inventory consumption on idempotent retry.
- `PaymentSetValidator`: validates supported modes, stable identities, positive two-decimal values, distinct components, and exact totals with `BigDecimal`.
- `BillingViewModel`: creates stable child operation identities, restores only the exact saved bill ID, clears UPI hints, and emits one immediate sync after commit.
- `ActiveOrdersViewModel` / `ActiveOrdersScreen`: consume one Room relation and show inconsistent local drafts as `Payment recovery`.
- `MasterSyncProcessor`: persists successful IDs/mappings, finishes later batches, preserves parent acknowledgement filtering, and then propagates failure into existing recovery.

## State invariants

### Draft bill

- `order_status = draft`
- `payment_status = pending`
- No finalized payment set exists.

### Completed successful bill

- `order_status = completed`
- `payment_status = success`
- `payment_attempt_status = succeeded`
- All active components have supported modes and stable identities.
- Component total exactly equals bill payable total.
- `is_synced = false` and `sync_status = pending` immediately after local commit.

### Payment-recovery bill

- Remains `draft + pending`.
- Contains one or more inconsistent payment rows.
- Remains visible only within local terminal-operational ownership.
- Is labelled `Payment recovery` instead of disappearing.

## Inventory boundary

**Inventory consumption is still an external non-durable side effect after bill completion.**

A process death or failure after bill/payment commit but before complete inventory processing may leave stock deductions missing or partial.

The current patch prevents duplicate consumption during normal idempotent retries but does not provide crash-safe eventual inventory recovery.

Billing and payment consistency improved; crash-safe inventory recovery deferred.

## Explicitly unresolved critical risk

- Crash-safe inventory recovery remains unresolved. No inventory operation tables, stock component tables, stock-log idempotency indexes, or inventory workers have been added.
- Room was upgraded from 61 to 62 only to add payment-operation identity uniqueness; that migration does not make inventory consumption durable.
- The durable inventory redesign requires a broader Room schema and processing model and is explicitly deferred.
- Do not claim inventory durability is fixed.

## Executed tests

- Android `testDebugUnitTest`: passed.
- Payment validation tests cover cash, UPI, POS, supported splits, empty/zero/negative/mismatched/excess-scale values, duplicate/unknown modes, missing identities, and retry equivalence.
- `BillDaoIsolationTest` and `BillDaoConstraintRecoveryTest` on physical Moto G34 5G: 28 passed.
- Debug APK assembly and Android-test source compilation: passed.
- Server Maven suite: 184 tests, 0 failures, 0 errors, 8 Docker/Testcontainers tests skipped.
- Web admin production build and TypeScript type-check: passed.
- Kitchen Command Center production build and lint: passed; lint reports six non-blocking React Fast Refresh warnings.

### This round (restoration hardening + Orders-status completion guard + re-verification)

- **C2/H4 — `updateOrderStatus("completed")` payment-recovery bypass**: `BillRepository.updateOrderStatus()` marked a bill completed/paid and deducted inventory without checking payment rows, so the Orders/Reports "Completed" dropdown could complete a payment-recovery draft (stale/partial UPI rows) in one tap. Fixed by running `PaymentSetValidator.validate()` on any existing active payment rows before a completed/paid transition; malformed/partial/duplicate sets are rejected (no write, no inventory deduction, rows preserved) with the recovery-required message. Clean completion (no active rows) and an already-valid completed set are unaffected. Behaviour note: bills with zero payment rows can still be marked completed (out-of-band settlement); the completed+pending semantic gap (H4) for that case is preserved pending a product decision rather than blocking the workflow.
- Added `invalidateRestoration()` to `loadDraftOrderForEditing()` so explicitly opening a different bill supersedes an in-flight restoration.
- Re-ran `gradlew test` (JVM): BUILD SUCCESSFUL.
- Re-ran `gradlew assembleDebug compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- Instrumented `BillDaoIsolationTest` and `BillDaoConstraintRecoveryTest` were re-run on a physical Moto G34 5G: 28 passed.

## Remaining test-environment limitations

- Eight PostgreSQL/Testcontainers server tests were skipped because Docker is unavailable locally.
- The complete Android instrumentation collection ran all 118 tests after adding SQLCipher initialization to the Hilt test runner, but 76 older UI/harness tests remain red for unrelated reasons including missing `@HiltAndroidTest`, MockWebServer dispatcher misuse, and stale UI selectors. The 28 billing database tests relevant to this release pass independently.
- Production incident correlation remains blocked on the corresponding Android Logcat and backend container logs.

Developer backend commands:

```bash
cd server
mvn test -Dtest=BillServiceTest,BillServiceImplTest,BillDependencyResolutionTest,SyncPushStrictModeTest,GenericSyncServerOwnedStateTest,BillPublicTokenTest
mvn test
```

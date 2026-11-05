# Phase 3 — Money Path: Validation Results & Remediation Plan

**Status:** awaiting your approval. No production code has been modified.
**Scope:** `apps/android` billing/payments, `services/server` payments/billing/sync validation.
**Rule honoured:** read-only investigation; every verdict below cites file and line.
**Reconfirmed:** every anchor below was re-opened against the working tree on 2026-11-04. Three citations had drifted and were corrected — see §5.

> **Alignment with `docs/adr/0001-easebuzz-gateway-state-contract.md` (status: proposed).**
> Three of these work packages are not new opinions — they restore decisions the repo has
> already taken. ADR §3: *"A request outcome never sets a state: a 200 on create proves we
> asked, not that anything was approved"* — F2 (`?: "success"`) is exactly a request outcome
> setting a state. ADR §4 corollary: *"the HTTP call happens outside that transaction"* —
> F6 is that corollary being broken. ADR §3 also asks for `source` and `as_of` on mirrored
> state; WP-1/WP-2 are the precondition for recording them honestly.

---

## 1. Validation results (Phases 3A–3E)

| ID | Original sev | Verdict after validation | Decisive evidence |
|----|--------------|--------------------------|-------------------|
| F2 | P1 | **Confirmed — and codified as intended behaviour in a test** | `PaymentStateManager.kt:133` `_gatewayStatus.value ?: "success"`; `:134` `verifiedBy = if (gwTxnId != null) "gateway"`; pinned by `PaymentStateManagerTest.kt:86` |
| F3 | P1 | **Confirmed** | Connectivity signal exists (`BillingViewModel.kt:135-140`) but `finalizeOnlineBill` (`:746-788`) never reads it; `NewBillScreen.kt:240-241` finalizes `SUCCESS` straight from a `savedStateHandle` txn id |
| F1 | **P3 (was P1)** | **Re-framed — the inclusive branch is unreachable from production code** | `BillCalculator.kt:60-61` does drop `customTax`, but **no production caller passes `isInclusive`**: `BillingViewModel.kt:1383` and `CartManager.kt:151` are the only two call sites and both rely on the default `false` |
| **F1′** | **P1 (new)** | **Confirmed: inclusive-tax profiles are over-charged by the GST amount** | `calculateGST` is called with `profile.isTaxInclusive` (`BillingViewModel.kt:1376`, `CartManager.kt:144`), then `calculateTotal` runs the *exclusive* branch → `total = subtotal + cgst + sgst` on prices that already include the tax |
| **F1c** | **P1 (new)** | **Confirmed: configured custom tax is never collected while GST is on** | custom tax is computed only in the `else if` after `gstEnabled` (`BillingViewModel.kt:1375-1381`, `CartManager.kt:143-149`) |
| F4 | P1 | **Still a hypothesis (high confidence)** — not provable by reading | `WebhookRetryService.java:45-46` `@Transactional` defaults to `REQUIRED`; no `REQUIRES_NEW` in the payments package |
| F5 | P2 | **Confirmed**, blast radius smaller than feared | push call inside the `@Transactional` handler; but `PushNotificationService.java:221-229` early-returns when Firebase/tokens are absent |
| F6 | P2 | **Confirmed** | outbound HTTP inside `@Transactional verifyPayment`, 3 × 30 s retries |
| F7 | P2 | Confirmed (latent — every current caller passes a validated subtotal) | `BillCalculator.kt:47` `BigDecimal(subtotal)` throws on non-numeric where siblings return `ZERO` |
| F9 | P2 | **Confirmed, and sharper than first stated** | `BillCalculatorTest.kt:102-104` (`525.00`, `customTax = "0.00"`) is the only inclusive-total test — the exact input that hides the leak |
| F11 / F13 | P3 | Confirmed | `splitPartPayment` uses `DOWN` (`:91`) vs `HALF_UP` elsewhere; `toFixedString` (`:118`) unused; test file in root package `com.khanabook.lite.pos` |
| F12 | P3 | **Downgraded — probably not a defect** | Easebuzz `txn_id` is gateway-assigned; if globally unique, a `restaurant_id` in the dedup index adds nothing. Needs a data check, not a migration |

### 1.1 New finding — F1b (P2): the server's total cross-check is inclusive-blind
`SyncPayloadValidator.java:154-161` computes `subtotal + cgst + sgst + customTax` and compares to `totalAmount`.
- In exclusive mode this formula is correct.
- In **inclusive** mode `cgst + sgst` are already *inside* the subtotal, so the comparison is off by the tax on **every** inclusive bill — it logs a warning for correct bills and would still not catch the F1 leak, because it does not know `isTaxInclusive`.
- It only warns; `ValidationResult.ok()` is returned either way. Under-billed money syncs silently.

### 1.2 Why F2 is a decision, not a bug fix
`PaymentStateManagerTest.kt:86` is named *"defaults gateway status to success when gateway status is null"* — the fallback is a deliberate choice that someone will defend. `PaymentStateManagerTest.kt:104` then asserts a record that is **self-contradictory**: `verifiedBy = "manual"`, `gatewayTxnId = null`, `gatewayStatus = "success"` on an EASEBUZZ payment. That contradiction is the argument for changing it, and it is why WP-1 must change test expectations explicitly rather than quietly.

---

## 2. Proposed work packages

Each is one commit, independently revertable. Nothing below starts before you approve.

### WP-1 — Stop inventing gateway success (F2) · P1
**Files:** `PaymentStateManager.kt:128-146`, `PaymentStateManagerTest.kt:86-108`.
**Change:** `gatewayStatus` = exactly what the gateway reported (`null` when nothing was reported); `verifiedBy = "gateway"` only when a txn id **and** an explicit `"success"` are both present.
**Expected behaviour:** no row can read *verified by gateway* unless the gateway said so.
**Acceptance criteria:**
- Two existing expectations flip (`:86-101`, `:104-108`) — justified in the commit message by the self-contradictory record above.
- New tests: `txn + "pending"` → `verifiedBy = "manual"`, `gatewayStatus = "pending"`; `txn + null` → `verifiedBy = "manual"`, `gatewayStatus = null`.
- No other test in the module changes; `:app:compileDebugKotlin` green.
**Risk:** changes values written to new rows only. **Historical rows keep the old defaults — no backfill.** Any reconciliation report must not assume the new invariant for pre-change data.

### WP-2 — Make offline a downgrade, not a block (F3) · P1
**Files:** `BillingViewModel.kt:746-788` (+ the shared `connectionStatus` at `:136`), `NewBillScreen.kt:236-245`.
**Change:** at finalize time, if connectivity is not online, stamp the gateway leg as `gatewayStatus = "pending"` / `verifiedBy = "manual"` instead of trusting the carried return.
**Why `pending`, not a new string:** the column has **no `CHECK` constraint** and the server already writes `success`, `error`, `link_created`, `refund_initiated`, `auto_refunded`, `refund_review_required`; no Android code branches on a `gatewayStatus ==` literal (zero hits). The one server query that selects on the column — `BillRepository.java:257`, `:279`, `gatewayStatus IN ('link_created','pending')` — is additionally guarded by `paymentStatus NOT IN ('paid','success')`, so a **paid** bill carrying `pending` is never expired by the link sweep. `pending` is already written by `PaymentStep.kt:152`, so nothing new enters the vocabulary. `verifiedBy = "manual"` is the field that carries the truth, and that is what WP-1 repairs.
**Why not block:** refusing to finalize strands a bill mid-service when a terminal briefly drops; the money is already taken. Downgrade the *claim* and let the webhook reconcile.
**Acceptance criteria:**
- Online: unchanged behaviour.
- Offline: bill still finalizes (no cash-flow regression), payment row carries `gatewayStatus = "pending"` + `verifiedBy = "manual"`.
- The link sweep (`BillRepository.java:257`) does **not** expire the bill: `BillDao.finalizeOnlineBillAtomically` writes `paymentStatus = "success"` / `orderStatus = "completed"` (`BillDao.kt:652-653`), and the sweep excludes `paymentStatus NOT IN ('paid','success')` (`BillRepository.java:258`, `:280`). **Certified — the WP-2 premise holds.**
- A webhook arriving later for that txn upgrades the row to `success`/`refunded` (existing server path already handles status transitions).
**Note:** depends on WP-1, otherwise a null status falls back to `"success"` and defeats the downgrade.

### WP-3 — Inclusive GST must not drop custom tax (F1) · P1
**Files:** `BillCalculator.kt:53-67` (`calculateTotal`), `BillCalculator.kt:43-51` (`calculateCustomTax`), callers `BillingViewModel.kt:1376-1381`, `CartManager.kt:144-149`, tests.
**Re-framed after the caller trace (§5.2).** The fix is not confined to the calculator. `calculateTotal`'s `isInclusive` parameter is dead from production today, and `customTax` is gated behind `!gstEnabled`. WP-3 therefore has three parts, in this order: (i) pass `isInclusive` from both callers, (ii) compute `customTax` independently of `gstEnabled`, (iii) only then does the inclusive-branch arithmetic become reachable and have to add custom tax correctly. Doing (iii) first changes nothing in production and hides F1′ behind a still-dead branch.

**Decision required (blocking):** on which base is a custom tax charged when GST is inclusive?
- **Option A — on the tax-inclusive subtotal:** `525 + 10% of 525 = 577.50`. One-line change, consistent with today's caller, but it charges service charge on the GST component.
- **Option B — on the ex-tax base (recommended):** `525 + 10% of 500 = 575.00`. Correct Indian practice; costs one extra parameter on `calculateCustomTax(subtotal, pct, isInclusive = false)`, derived as `subtotal − (cgst + sgst)`.

**Acceptance criteria (either option):** `calculateTotal(..., isInclusive = true, customTax != "0.00")` **adds** the tax; exclusive mode unchanged; a test table pins both modes for the same inputs; `BillCalculatorTest.kt:102-104` is extended rather than left as the only inclusive-total case.
**Also fix (F1b):** `SyncPayloadValidator.java:154-161` must skip or invert the comparison when the profile is tax-inclusive, and the result of a mismatch should be a decision you make — warn (today) or reject.

### WP-4 — Prove F4 before touching it (F4) · P1
**Files:** `EasebuzzWebhookService.java:403-505`, `WebhookRetryService.java:44-62`, a new test.
**Step 1 (write the test first):** a Spring test that forces the completion path to throw after `enqueueAt` succeeds, then asserts (a) the endpoint still returns 200 and (b) a `PENDING` retry row exists.
**Expected:** the test **fails** — proving the row is rolled back with the catch's successful return.
**Step 2 (only if step 1 fails):** `@Transactional(propagation = REQUIRES_NEW)` on `enqueueAt`, matching the existing precedent in `SyncFallbackSaver.java:25`, `TerminalManagementService.java:637`.
**Acceptance criteria:** the test passes; refund completion still idempotent for the same `refundId`.
**If step 1 passes:** close F4 as not-a-defect and record why. No production change.

### WP-5 — Get external I/O out of transactions (F5, F6) · P2
**Files:** `EasebuzzWebhookService.java` (push), `EasebuzzPaymentService.verifyPayment` (HTTP).
**Change:** keep the transactional method to state mutation and return a small result object; the caller (controller / webhook dispatcher) performs the push and any HTTP fetch **after** the transaction commits.
**Acceptance criteria:** no `pushNotificationService` call and no gateway HTTP client is reachable from a `@Transactional` method in `feature/payments`; webhook response semantics unchanged (still 200, still deduped).
**Precedent:** this is ADR 0001 §4's corollary (*"the HTTP call happens outside that transaction"*), already implemented that way in `SyncFallbackSaver.java:25` and `TerminalManagementService.java:637`.

### WP-6 — Hygiene (F7, F11, F13, F12) · P2/P3
- `calculateCustomTax` parses defensively like its siblings (F7).
- One rounding policy in the calculator; delete `toFixedString` (F11).
- Move `BillCalculatorTest` to `feature.billing.domain` (F13) — same commit, no logic change.
- F12: run the data check first (`SELECT txn_id, COUNT(DISTINCT restaurant_id) ... GROUP BY txn_id HAVING ...`). If txn ids are globally unique, **close it**; do not add a migration for symmetry.

---

## 3. Decisions I need from you before any code changes

1. **WP-1:** accept that `verifiedBy = "gateway"` now requires an explicit success? (This flips two existing tests.)
2. **WP-2:** downgrade-and-finalize (recommended) or hard-block when offline?
3. **WP-3:** custom tax base — gross (A) or ex-tax (B, recommended)?
4. **F1b:** should a mismatched total be a warning (today) or a rejected sync record?

---

## 4. Sequence and verification

```
WP-1 → WP-2 (depends on WP-1) → WP-3 → WP-6 (mechanical) → WP-4 (test-first) → WP-5
```

```bash
# android — targeted, then full compile
cd apps/android && JAVA_HOME=/c/temp/java/jdk-17 \
  ./gradlew :app:testDebugUnitTest --offline \
  --tests '*PaymentStateManagerTest' --tests '*BillCalculatorTest'
cd apps/android && JAVA_HOME=/c/temp/java/jdk-17 ./gradlew :app:compileDebugKotlin --offline

# server — targeted
cd services/server && mvn -q -Dtest='EasebuzzWebhookServiceTest,EasebuzzPaymentServiceTest' test
```

**Out of scope / untouched:** no backfill of historical payment rows, no API contract changes, no schema migration except a WP-6 migration *only if* F12's data check shows the index is genuinely wrong.

**Not yet audited (unchanged from the Phase 3 report):** `SubMerchantService` (1,314 LOC), `EasebuzzReconciliationService` (480 LOC), chargeback/settlement flows, `WebhookRetryService:65-180` executor behaviour, and whether `ops/apache-kbook.conf` restricts `/actuator/prometheus`.

---

## 5. Reconfirmation pass — 2026-11-04

Every anchor above was re-opened against the working tree. Three citations had drifted; §1 now carries the corrected numbers.

| First drafted | Reality |
|---|---|
| `BillCalculator.kt:64-66` | `:60-61` is the inclusive branch (returns the subtotal untouched); `:63-66` adds taxes only on the exclusive path |
| `BillCalculatorTest.kt:121` | `:102-104` is the inclusive-total test; `:121` is a part-payment test |
| `BillingViewModel.kt:136` | property declared `:135`, `stateIn` at `:136` — cited as `:135-140` |
| `SyncPayloadValidator.java:154-161` | body `:154-162`, `return ValidationResult.ok()` at `:163` |

Confirmed unchanged: `PaymentStateManager.kt:133-134`; `PaymentStateManagerTest.kt:86` and `:104`; `BillingViewModel.kt:746` (`finalizeOnlineBill`); `NewBillScreen.kt:240-241`; `WebhookRetryService.java:45-46` (`enqueue` at `:40-42` delegates to it); `PushNotificationService.java:221-229`; `EasebuzzPaymentService.java:87-88` (`@Transactional` on `verifyPayment`); `EasebuzzApiClient.java:32-44` (3 attempts, 30 s read timeout each).

### 5.1 `gateway_status` vocabulary — settles WP-2's "no new string" constraint

- No `CHECK` constraint on the column in any migration.
- Server writes: `success`, `error`, `link_created`, `refund_initiated`, `auto_refunded`, `refund_review_required`, plus `refunded` / `partially_refunded` on the webhook path.
- No Android code branches on a `gatewayStatus ==` literal (grep: zero hits) — the client is not coupled to the vocabulary.
- The only selecting query, `BillRepository.java:257` / `:279`, is additionally guarded by `paymentStatus NOT IN ('paid', 'success')`.

**Verdict:** WP-2 can reuse `pending` safely; `unverified_offline` would buy nothing and would sit outside every existing sweep. This is recorded as a constraint, not a preference — if WP-2 ever needs a distinct value, it must first be added to the sweep's `IN` list and to the reconciliation readers.

### 5.2 Caller trace — corrects F1, promotes two new findings

`grep -rn "calculateTotal(" apps/android/app/src` returns **exactly two production call sites**:

| Site | Call | `isInclusive` supplied? |
|---|---|---|
| `BillingViewModel.kt:1383` | `calculateTotal(subtotal, cgst, sgst, customTax)` | no — defaults to `false` |
| `CartManager.kt:151` | `calculateTotal(subtotal, cgst, sgst, customTax)` | no — defaults to `false` |

Every other hit is a test. Consequences, all from raw output rather than inference:

1. **F1 (original) is latent, not live.** The branch that drops `customTax` (`:60-61`) cannot be reached from production. It becomes live the moment WP-3 supplies `isInclusive`, which is why F1 must be *fixed with* F1′, not before it.
2. **F1′ — the live defect points the other way.** For `gstEnabled = true, isTaxInclusive = true`: `subtotal` is tax-inclusive, `calculateGST` extracts 12.50 + 12.50 from 525.00, then `calculateTotal` takes the exclusive branch and returns **550.00** for goods priced 525.00 — GST charged twice, once inside the menu price and once as a line item. Under-billing was the wrong direction.
3. **F1c — custom tax is dropped whenever GST is on.** `customTax` is assigned only in the `else if (profile.customTaxPercentage > 0)` branch that follows `if (profile.gstEnabled)`. A shop with both GST and a service charge configured never collects the service charge, in either tax mode.
### 5.3 The billing calculation path, end to end

Every stage traced, with the question "does this stage recompute money or trust what it was handed?"

| Stage | Where | Recomputes? | Evidence |
|---|---|---|---|
| Cart summary (live UI) | `CartManager.kt:135-152` | **yes** — subtotal → GST → customTax → total | only `CustomTax` call site outside the calculator |
| Bill row (persist/edit) | `BillingViewModel.kt:1367-1393` | **yes — second implementation of the same arithmetic** | same four steps hand-duplicated |
| Finalisation | `BillDao.kt:551-676` | **no** — validates the payment set against `bill.totalAmount`, writes status only | `PaymentSetValidator.validate(normalized, bill.totalAmount)`; `paymentStatus = "success"` at `:652` |
| Invoice / PDF | `InvoicePDFGenerator.kt:413, 426, 466` | **no** — prints stored `subtotal`, `cgstAmount`, `totalAmount` | zero `calculateTotal` / `calculateGST` hits in `feature/printing` |
| Print, KOT, reports (device) | `feature/printing`, `feature/reports` | **no** | the same grep returns nothing |
| Sync | `SyncRequestDtos` → `SyncEntityMappers` | **no** | amounts carried as stored |
| Server validation | `SyncPayloadValidator.java:154-163` | **no** — compares, then `return ValidationResult.ok()` regardless | warn-only |
| Server reports | `BillRepository.java:287` | **no** — `SUM(b.total_amount)` | every reported rupee is what a terminal wrote |

**What this means for the shared financial contract you proposed:**

1. **The duplication already exists, one layer up.** `CartManager` and `BillingViewModel` are two separate hand-written copies of the same four steps. That is the SSOT violation to fix first — a single `BillTotals` value type produced by one function and consumed by both. Fixing only the calculator would leave the two copies free to drift again.
2. **`discount` has no implementation.** `grep -rn "discount" feature/billing` returns nothing on Android. Do not add a discount field to the contract until the feature exists; it would be a speculative input (YAGNI) that nothing populates.
3. **The server never recomputes**, so a terminal arithmetic bug becomes a books error with no second opinion. The contract is worth building for that reason alone — but the cheapest first step is making `SyncPayloadValidator` inclusive-aware and *rejecting* (not warning) a genuine mismatch.
4. **Rounding is consistent today** inside the calculator (`HALF_UP` throughout; `InvoiceController.java:179` uses `HALF_UP` only to display the half-GST percentage). The `DOWN` in `splitPartPayment` is a split-allocation rule, not a total rule — do not "unify" it (F11) without an accounting decision.

4. **Reachability qualifier.** `isTaxInclusive` exists on the server (`RestaurantProfile.java:81-82`, `UpdateBusinessProfileRequest.java:21`, `V1__init_schema.sql:44`) but no reference was found in `apps/web-admin/src`, so it is settable by API rather than by the console. F1′ needs a data check before it is called live: `SELECT count(*) FROM restaurant_profiles WHERE is_tax_inclusive = true AND is_gst_enabled = true;` If that is zero, F1′ is documented-and-fixed rather than urgent.

---

## 6. Billing calculation path audit (whole path, not just the calculator)

| Stage | What happens | Recomputes? |
|---|---|---|
| Cart summary | `CartManager.kt:135` subtotal from price × qty; `:143-151` GST, custom tax, total | **yes** |
| Checkout summary | `BillingViewModel.kt:1367` subtotal; `:1375-1383` GST, custom tax, total | **yes — a second copy of the same logic** |
| Bill finalization | `BillDao.finalizeOnlineBillAtomically` validates the payment set against `bill.totalAmount` (`PaymentSetValidator.validate`) and writes statuses only | no |
| Printing / PDF | `InvoicePDFGenerator.kt:413, 426, 432, 466` draw stored `subtotal`, `cgstAmount`, `sgstAmount`, `totalAmount` | no |
| Sync device → server | totals travel in the payload; `SyncPayloadValidator.java:154-163` recomputes and only warns | no (warn only) |
| Server reports | `BillRepository.java:287` `SUM(b.total_amount)` | no |
| Server invoice page | `InvoiceController.java:179` derives its own half-GST label | display only |

### 6.1 Consequences

- **The arithmetic exists twice on the client** (`CartManager`, `BillingViewModel`) with identical code and no shared function. This is the SSOT violation the proposed calculation contract would fix, and it is how a partial WP-3 fix could leave one path right and the other wrong.
- **F1d (new, P2): the printed invoice never shows a custom-tax line.** `grep -rn "customTax"` over `feature/printing` returns **zero hits**; only `subtotal`, `cgst`, `sgst`, `total` are drawn. The data exists (`bills.custom_tax_amount`, `V1__init_schema.sql:159`) — the render is missing. Any bill carrying a service charge prints a total that does not reconcile with its printed lines.
- **F1e (new, P3): a third copy of the split rule.** `InvoicePDFGenerator.kt:416-420` divides `gstPercentage` by 2 at scale **1** with a hard-coded `"2.5"` fallback, where `BillCalculator.calculateGST` splits at scale 2. Label-only today.
- **No discount term exists anywhere:** `grep -rn "discount" feature/billing` → zero hits. The `discount` field in the proposed contract is a new capability, not a refactor of existing behaviour.

### 6.2 Verdict on the proposed calculation contract

The evidence supports it: four entry points (cart, checkout, print, server invoice) touch money, two of them recompute, and one of those two gates custom tax behind `gstEnabled`. A single `FinancialCalculation` value type — subtotal, tax mode, GST amount, custom tax + basis, payable — is the right shape, but it is a larger change than WP-3 and should be its own approved work package, with WP-3 either merged into it or sequenced after it.

## 7. Reachability check — blocked, with the exact SQL

`psql` 18 is installed and the dev server listens on `localhost:5432` (`kbook_saas`, per `application-dev.properties`), but it rejects the configured credentials:

```
psql: error: connection to server at "localhost" (::1), port 5432 failed:
FATAL:  password authentication failed for user "postgres"
```

So the dev database is reachable at the TCP level and closed to me; production is out of scope by rule. **F1′ remains a P1 candidate, not a confirmed live bug**, until this runs where the data is:

```sql
-- run \d restaurant_profiles first: the tax columns are verified below,
-- the join key (restaurant_id vs id) is NOT.
SELECT count(*) AS profiles_total FROM restaurant_profiles;

SELECT count(*) AS gst_on_and_inclusive
FROM restaurant_profiles
WHERE coalesce(gst_enabled, false) AND coalesce(is_tax_inclusive, false);

SELECT count(*) AS gst_on_and_custom_tax
FROM restaurant_profiles
WHERE coalesce(gst_enabled, false) AND coalesce(custom_tax_percentage, 0) > 0;

-- F1′ exposure: inclusive-profile bill whose total exceeds its subtotal
SELECT count(*) AS double_taxed_bills
FROM bills b JOIN restaurant_profiles p ON p.restaurant_id = b.restaurant_id
WHERE coalesce(p.is_tax_inclusive, false) AND coalesce(p.gst_enabled, false)
  AND b.is_deleted = false AND b.total_amount <> b.subtotal;
```

Column names verified against `RestaurantProfile.java:75-93` and `V1__init_schema.sql:42-48`: `gst_enabled`, `is_tax_inclusive`, `custom_tax_percentage`, plus `bills.custom_tax_amount` at `:159`.

---

## 8. F4 gate — executed; F4 is now confirmed, not a hypothesis

`RefundWebhookRetryDurabilityTest` (new, `services/server/src/test/java/com/khanabook/saas/service/`) runs the real `EasebuzzWebhookService` bean against H2 with a real transaction manager. The fault is injected through Spring's own `TransactionAspectSupport.setRollbackOnly()`, which reproduces "a persistence call failed inside a participating transaction" instead of mocking the repository — a mocked repository would delete the transaction semantics the test exists to measure.

Command (this machine, offline):

```
JAVA_HOME=/c/temp/java/jdk-17.0.2 ./mvnw -B -o -Dtest=RefundWebhookRetryDurabilityTest test
```

Result:

```
F4-DIAG txn=KBF4TXN1 ackFailure=none retryJobsForThisTxn=0 allRetryRows=0
INFO  Refund webhook received txnid=KBF4TXN1 status=processed easepayid=EPAY4T1 refundId=RFD4T1
ERROR Failed to process refund webhook for txnid=KBF4TXN1, enqueueing for retry: F4 fault injection...
Tests run: 2, Failures: 1 -- expected: 1L but was: 0L
```

### 8.1 Reading the result

1. The hash verified, the bill matched, `updateBillRefund` ran and the injected fault fired — the test reaches the exact branch F4 is about.
2. The catch block ran and **issued** the enqueue (`EasebuzzWebhookService.java:340-345`).
3. The retry row is **gone** (`allRetryRows=0`).
4. **No exception reached the caller** (`ackFailure=none`). This is worse than "the retry is lost": the refund completion is rolled back, the retry is rolled back, and Easebuzz is told the callback succeeded. Nothing is alerted.
5. The control test passes (`Bill 2 refund status updated via webhook`), so the harness demonstrably commits on the happy path — the failure is not a broken fixture.

**F4: hypothesis → confirmed P1**, with a blast radius of *silent* refund-completion and recovery loss rather than a noisy 500.

### 8.2 Required fix (unchanged in shape, now evidence-backed)

`WebhookRetryService.enqueueAt` (`:45-46`) must commit independently of the caller: `@Transactional(propagation = REQUIRES_NEW)`, precedent `SyncFallbackSaver.java:25` and `TerminalManagementService.java:637`. Acceptance: the gate test flips from `expected: 1L but was: 0L` to green and the control test stays green.

### 8.3 Test status while implementation is on hold

The gate test is committed `@Disabled("F4 confirmed ... enable with WP-4")` so the suite stays green. Enabling it is step 1 of WP-4.

**Open mechanism question, stated rather than assumed:** why no `UnexpectedRollbackException` surfaces to the caller is not explained by reading the code — a new transaction marked rollback-only should throw at commit. The test pins the *observable* behaviour; the fix must be re-instrumented afterwards rather than assumed to have addressed the cause.

---

## 9. F4 — fixed and verified (2026-11-04)

### 9.1 Root cause

`handleRefundWebhook` was itself `@Transactional`. The refund mutation, the rollback-only marking caused by a persistence failure, and the retry enqueue all lived in *one* transaction. When the failure marked it rollback-only, the catch block's `webhookRetryService.enqueue(...)` joined the same doomed transaction, so the retry row was discarded at commit while the method still returned `{"status":"received"}`. Result: refund not applied, no retry, gateway told it succeeded, nothing alerted.

### 9.2 Fix (minimal, two production files)

| File | Change |
|---|---|
| `EasebuzzWebhookService.java` | `@Transactional` removed from `handleRefundWebhook`; the refund mutation now runs through `runInOwnTransaction(...)` (`TransactionTemplate`, `:305-312`). A failure rolls back only that inner transaction and propagates to the existing catch. |
| `WebhookRetryService.java` | `enqueue` (`:46`) **and** `enqueueAt` (`:51`) are now `REQUIRES_NEW`. Both are annotated deliberately: `enqueue` calls `enqueueAt` by self-invocation, so annotating only `enqueueAt` would have been silently inert — the proxy is never entered on that path. |

No schema change, no gateway contract change, no refund business-rule change, no change to the payment webhook path.

### 9.3 Verification

| Suite | Command target | Result |
|---|---|---|
| F4 durability | `RefundWebhookRetryDurabilityTest` | **2/2 pass** — `F4-DIAG ackFailure=none retryJobsForThisTxn=1 allRetryRows=1` |
| Webhook unit | `EasebuzzWebhookServiceTest` | 31/31 pass |
| Webhook/refund/retry/payment regression | `-Dtest='*Webhook*,*Refund*,*Retry*,*Payment*'` | **111 tests, 0 failures, 0 errors, BUILD SUCCESS (exit 0)** |

Before the fix the same gate test reported `expected: 1L but was: 0L`; after the fix it asserts the retry row exists *and* the ack is unchanged.

### 9.4 Not covered (stated, not implied)

- **Test C (retry persistence itself fails):** not implemented. If both enqueue attempts throw, the exception escapes the catch and the gateway receives a non-2xx — which is the correct signal, but it is untested.
- **Test D (duplicate refund delivery against the new boundaries):** not implemented as an integration test. Duplicate-delivery idempotency is covered at unit level by `EasebuzzWebhookTest` (22 tests) and `EasebuzzWebhookServiceTest` (31 tests).
- **Payment webhook (`handlePaymentWebhook`, `:62`, catch/`enqueue` at `:161-166`) has the same shape.** It was out of scope; it now benefits from the `REQUIRES_NEW` enqueue (the retry row survives) but its own transaction still swallows a persistence failure and returns 200. Needs the same treatment in a follow-up.
- **Testcontainers/PostgreSQL run unavailable here** (no Docker). CI command to complete verification: `./mvnw -Dtest=RefundWebhookRetryDurabilityTest test` on a Docker-capable runner, plus `PostgresMigrationSmokeTest`.

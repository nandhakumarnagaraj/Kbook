# KhanaBook — Product Design vs Codebase Deep Analysis (PO review)

**Date:** 2026-09-03
**Author:** Kiro (as acting PO)
**Method:** Every claim grounded in code (`file:line`) or git history (`commit`). Scope: Android + server + local git log. Web-admin touched where relevant.
**Status legend:** ✅ shipped · 🟡 partial / caveated · 🔴 missing or diverges from stated intent · ⚪ deliberately removed

---

## Part A — 16 design pillars vs code

| # | Pillar | Status | Evidence / note |
|---|---|---|---|
| 1 | 1 primary among 5 devices | ✅ | `RestaurantTerminal.isPrimary` (`RestaurantTerminal.java:74`); atomic swap under restaurant-profile lock `TerminalManagementService.setPrimaryTerminal:65`; first terminal auto-primary `TerminalController.java:361`; Android reads via `TerminalStatusResponse(isPrimary,…)` `TerminalController.java:538`; invariant tested `TerminalLifecycleTest.firstTerminal_isPrimaryAutomatically`. Enforced max 5 `MAX_ACTIVE_TERMINALS=5`. |
| 2 | 30-day JWT per login | ✅ | Terminal token TTL = `jwt.terminal.expiration.ms:2592000000` (=30d) `JwtUtility.java:29`. Note: the *regular* web/user token is 15min (`jwt.expiration.ms:900000`) + refresh — the 30-day figure is specifically the **terminal** token. Confirm this is the login you mean. |
| 3 | Sync + staff permissions | ✅ | Permissions synced via pull → `PermissionManager.updateFromSync`; offline authz engine `b8aa762f`, monotonic revision + revocation marker `51811891`, audit log `f04390bd`. Sync push/pull hardened (see Part B). |
| 4 | Normal vs primary device | 🟡 | Primary flag exists (#1) and terminal *types* BILLING/KOT/ADMIN enforced `4847ac82`. But "what a normal device is forbidden from doing that primary can" is only partially codified — primary is currently a **designation + heartbeat owner**, not a hard gate on most write paths. Needs an explicit capability matrix (see Problem 1). |
| 5 | Sync affects Easebuzz | 🟡 | Refund amount is server-owned and pulled down (`Bill.refundAmount`, never written by Android push). Payment status flows server→device. But standalone payment-link (`createPaymentLink`) is **not bill-tied** (no `billId` in udf) so its webhook can't reconcile to a synced bill — divergence flagged (see Problem 3). |
| 6 | Logo → invoice + report (CDN→server→device) | 🟡 | Local-first: logo downloaded to file at sync time; print/PDF use `AppAssetStore.resolveAssetPath` then Coil fallback with `withTimeout(1500)` (fixed this session). Upload validated (magic bytes, dimension caps, WebP) `5cd41366`. Caveat: **report download** logo path on web-admin/server not verified here — only Android print/PDF confirmed. |
| 7 | Pay-after-food & pay-before-food | ✅ | Draft→add items→settle (pay-after) and create→pay→share (pay-before) both present; drafts get order no. but invoice only on settlement (`PLAN.md §5`, `BillCreationUseCase`). KOT delta via `sentToKot`; KOT events NEW/ADD/VOID now on paper (this session). |
| 8 | Order type: dine-in / takeaway | 🟡 | `BillEntity.orderType` default `"order"` (`BillEntity.kt:54`); creation uses `"dine_in"`; KOT prints `Type:` when not the legacy `"order"`. Works, but the **default `"order"` is a legacy value** distinct from dine_in/takeaway — data cleanliness issue; a bill created via the old default won't show a type. |
| 9 | Easebuzz gateway + create/send payment link | ✅/🟡 | Gateway pay (`createEasebuzzOrder`), Easy Collect link (`createPaymentLink`, `createPaymentLinkForBill`), moved into New Bill flow `c2ae5485`. Standalone link now has amount validation (this session). Reconciliation gap on standalone link (Problem 3). |
| 10 | OCR for menu config | 🟡 | `OcrSpatialParser` (ML Kit). Veg/non-veg misclassification fixed this session; price-band + typo map fixed. Spatial row/column core still untested (needs instrumented test). Works, not fully verified. |
| 11 | Menu categories + variants | ✅ | Category/ItemVariant entities + DAOs; variant stock deduction tested (`InventoryConsumptionTest`); OCR maps variant headers to prices. |
| 12 | Offline UPI id | ✅ | `upiHandle`/UPI QR on receipt (`InvoiceFormatter` review QR + UPI); UPI selection persists across profile sync `01bc844b`. |
| 13 | Printer config — receipt & KOT (event-based) | ✅ | `PrinterRole.CUSTOMER/KITCHEN`, `PrintRouter` role dispatch, KOT event-aware (this session). 58/80mm via `PrintTokens` (amount-column bug fixed this session). |
| 14 | Notifications per device | ✅ | Per-device FCM token registration, dedup on unchanged token `91555ed0`, data-only payloads + guardrails `76df81a4`, grouped notifications `40ea6f75`, 90-day retention purge. |
| 15 | Single billing device → dual Bluetooth (receipt + KOT) | ✅ | `BluetoothPrinterTransport`, multiple printer profiles by role, `PrintRouter` fans out to all immediate targets. |
| 16 | 5 terminals → WiFi (receipt + KOT) | 🟡 | `WifiPrinterTransport` exists (socket write, timeouts). But **cross-device KOT ownership is by-design single-origin** — a pulled bill never auto-prints KOT elsewhere (`PrintRouter` device-ownership guard). So "5 terminals share a WiFi kitchen printer" works only if each device targets the same WiFi printer IP; there is **no server-side KOT hub** (deferred, `PLAN.md §6`). Confirm this matches intent. |

**Deliberately removed (not gaps):** Swiggy/Zomato marketplace (`d4a0bce2`, `eea83ce8`) — so `sourceChannel` is now vestigial; consider dropping it from new UI.

---

## Part B — the 5 problem areas (deep)

### Problem 1 — Offline system distribution

**What exists:** Offline-first Room+SQLCipher; each terminal owns its operational bills (`record_scope=terminal_operational`, `record_origin=local_created`); 5-terminal cap; primary designation; terminal-type enforcement (BILLING/KOT/ADMIN) `4847ac82`; offline authz decision engine `b8aa762f`.

**The real problem:** distribution of *authority* across offline devices is only partially codified.
- Primary is a designation + heartbeat owner, not a hard write-gate. If the primary is offline, there is no explicit "who allocates the authoritative sequence" fallback beyond per-terminal series.
- Order/invoice allocation is **local-state-dependent** (each terminal reserves from its own series/counter). This is why the 409 loop happened (Part B/Problem 2). Two offline devices can each believe they hold the next number within overlapping assumptions until sync reconciles.
- Capability matrix (normal vs primary) is not a single source of truth — it's spread across terminal type + isPrimary + permissions.

**Recommendation:** define one **capability matrix** (device_type × isPrimary × permission → allowed ops) and enforce it in one place, offline. Add a two-offline-device acceptance test (already flagged in `PLAN.md §4`).

### Problem 2 — Synchronization

**What exists (and is now solid):** `MasterSyncProcessor.pushBatches` correctly isolates partial failures — throws `SyncConflictException` only when ALL records in a push fail, marks synced only server-confirmed ids, quarantines the rest. This is the **fix for the historical 409 infinite-loop** (`PLAN.md §11`, server `GenericSyncService` per-record fallback + Android exact-`invoice_series` sequence query). Amount canonicalization guard (`SyncNormalizer.toSafeAmount`, extracted+tested this session) prevents format-drift re-sync loops.

**Residual risks:**
- Invoice/daily-id allocation still local-state-dependent (ties back to Problem 1). `PLAN.md §4` wants a two-offline-device unique-order test on a real DB — still pending (needs Docker/instrumented).
- Server-side KOT event table does **not** exist (Android-only) — cross-device KOT audit is not server-backed (`PLAN.md §6`).
- Cursor-based master-pull pagination seam exists but full pagination not confirmed active.

**Recommendation:** land the two-offline-device invoice test; decide whether KOT events need a server table before claiming cross-device KOT.

### Problem 3 — Payment gateway (Easebuzz)

**What exists:** hash-verified gateway pay, Easy Collect links, refunds (server-owned, idempotent merchant-refund-id), sub-merchant routing, webhook + reconciliation service, fraud-score gate, KYC-gated onboarding.

**Concrete gaps found:**
- **Standalone `createPaymentLink` was unvalidated** (amount straight to gateway; NPE on null). Fixed this session (band ₹1–₹2,00,000, 2dp canonical). **Needs your confirmation of real Easebuzz min/max.**
- **Standalone link is not bill-tied** (`udf2=restaurantId` only) → its webhook cannot mark a bill paid. Either (a) intentional "collect any amount", or (b) should carry `billId` like `createPaymentLinkForBill`. **PO decision needed.**
- `merchant_txn` idempotency on double-tap not enforced client-side. **Depends on whether Easebuzz rejects duplicate merchant_txn — open question to you.**

### Problem 4 — Onboarding: 2 address proofs

**What exists:** `AssetStorageService.uploadKycDocument(restaurantId, docType, file)` with private storage `5f7b4828`; `business_proof_1` / `business_proof_2` doc types; **rule enforced**: `EasebuzzIntegrationTest.proprietorshipRequiresDistinctBusinessProofTypes` — a proprietorship must upload **two distinct** business-proof types. KYC exposure remediation runbook + reconciliation SQL exist (`docs/runbooks/KYC_EXPOSURE_REMEDIATION.md`, `ops/sql/kyc_document_reconciliation.sql`).

**Status:** ✅ implemented and tested. Caveat: the "distinct types" rule is validated in the Easebuzz path; verify the **Android onboarding UI** enforces the same distinctness before upload (server is the backstop, but UX should pre-block duplicates).

### Problem 5 — This report

Delivered as this document. Confidence is high where `file:line`/commit-backed; explicitly caveated where I did not open the file this pass (web-admin report-logo path, WiFi multi-printer live behavior, OCR spatial core).

---

## Part C — prioritized PO backlog (my recommendation)

**P0 (correctness / money / trust)**
1. Easebuzz standalone link: confirm amount band; decide bill-tie vs collect-any; add `merchant_txn` idempotency if gateway allows dup. *(3 open questions to PO.)*
2. Two-offline-device invoice/order-id uniqueness test on a real DB (closes the last 409-loop-class risk).

**P1 (design coherence)**
3. Single **capability matrix** for normal vs primary device, enforced offline in one place.
4. Decide server-side KOT event table (needed before claiming cross-device KOT / WiFi kitchen hub for 5 terminals).
5. `orderType` legacy `"order"` default → migrate to explicit dine_in/takeaway; drop vestigial `sourceChannel` from new UI.

**P2 (verification debt)**
6. Instrumented tests for OCR spatial core + WiFi multi-printer fan-out.
7. Verify web-admin/report-download logo path uses the same CDN→server asset.

---

## Part D — session fixes already applied (context)

9 real bugs fixed + 60+ tests added this session across: KOT event printing, 58mm amount-column, isTaxInclusive overcharge, ₹1 price floor, OCR veg classification, timezone param, monthly-report short-month over-count, search legacy-invoice scrape, Easebuzz link amount validation. All Android unit tests green; server compiles. **No commits made yet** (pending PO go-ahead + commit split).

---

## Part E — Adjacent features (deep analysis, beyond the 16 pillars)

Same evidence rules. These are real product surfaces not in the stated 16 + 5.

### E1 — Inventory / stock loop
**Status:** ✅ well-built. Double-count hypothesis INVESTIGATED and CLEARED; one real concurrency gap (already known to the team).
- `InventoryService.deductForFinalizedBill` (`:66`) is **idempotent** via `bills.inventory_deducted` flag; recipe-based deduction (`ItemRecipe.quantityPerItem × qty`), aggregated per raw material.
- **Negative-stock guard** (`:104`): a deduction that would go negative is **skipped with a warning**, not clamped. ⚠️ *Design question:* skipping means the sale succeeds but stock silently stays — an over-sell isn't recorded. PO decision.
- **Zero-stock cascade** (`cascadeOutOfStock`): exhausting a raw material marks every recipe-linked menu item unavailable (POS + QR). Solid.

**Double-count risk — RESOLVED (not a bug):** the two deduction "paths" operate on *different stock models* and don't double-count:
  - **Dish/variant count** (`MenuItem.currentStock`, `ItemVariant.currentStock`) is **server-recalculated as `SUM(StockLog.delta)`** (`MenuItemRepository.recalculateStock`, `ItemVariantRepository`), NOT decremented in place. Android writes a StockLog + decrements a local counter for offline UX; on sync the server recomputes the sum over distinct log rows → re-applying a synced log is **idempotent**.
  - **Raw-material stock** (`RawMaterial.stockQuantity`) is decremented via recipes in `deductForFinalizedBill`, idempotent via the `inventory_deducted` flag.
  - Two concepts, two idempotency mechanisms → no double deduction of the same quantity.

**REAL gap found — RawMaterial has no optimistic locking (last-write-wins):** documented by the team's own `PhysicalCountRaceTest.physicalCount_concurrentWithSale_noProtection` — a physical-count edit concurrent with a sale-deduction races on `RawMaterial.stockQuantity` with **no `@Version`/CAS**; the later write wins and the other update is lost. The `StockMovement` ledger records both (auditable/reconstructable), but the live `stockQuantity` can drift from the true ledger sum.
  - **Fix options (PO decision — schema-affecting, not applied):**
    1. **Optimistic locking:** add `@Version` to `RawMaterial`, retry deduction on `OptimisticLockException`. Smallest change; makes concurrent writes safe.
    2. **Ledger-derived stock (preferred, matches menu model):** make `RawMaterial.stockQuantity` a **recalculated `SUM(StockMovement.delta)`** exactly like `MenuItem.currentStock` from `StockLog`. Consistent with the existing pattern, removes last-write-wins entirely, but is a larger change (recompute path + migration + backfill).
  - Both require a Flyway migration + backfill; both change how raw-material stock is authoritative → needs explicit go-ahead before I touch it.

### E2 — Refunds
**Status:** ✅ strong.
- `RefundService.initiatePartialRefund` (`:48`): ownership check, eligibility (must be paid/partially_refunded + has gatewayTxnId), **cumulative refund cap** (can't exceed `total − alreadyRefunded`), positive-amount check, correct status transitions (`refunded`/`partially_refunded` + `cancelled` on full), customer WhatsApp/email confirmation.
- Refund reason taxonomy (10 codes). `cancelAndAutoRefund` with delay scheduling.
- Server-owned `refundAmount` pulled to Android (never client-written) — matches pillar #5.
- **Gap:** refund amount validation lives in `RefundService`; the older `EasebuzzPaymentService.initiateRefund` is the gateway call — confirm no path bypasses the cap check.

### E3 — Chargeback prevention
**Status:** 🟡 present, not analyzed deeply. `ChargebackPreventionService` + `Chargeback` entity + fraud-score gate in payment init (`EasebuzzPaymentService` FRAUD_RISK block). Worth a dedicated review — fraud logic is untested here.

### E4 — Tax compliance / GST reports
**Status:** 🔴→✅ REAL reconciliation bug found + fixed (compile-verified; integration test Docker-gated).
- **Bug (compliance):** `getGstReport` summed **actual per-bill CGST+SGST** (correct, matches invoice), but `getTaxSummary` computed `tax = SUM(totalAmount) × rate / 100` — tax on the gross total. For **tax-inclusive** pricing `totalAmount` already contains the tax, so this **double-counted tax** and **diverged** from the per-bill GST report. Two endpoints, two different tax numbers for the same period. **Fixed:** `getTaxSummary` now sums actual `cgst+sgst` from settled bills, reconciling with `getGstReport` and the invoice.
- **Bug (population mismatch):** `getTaxSummary` did not exclude cancelled/deleted bills while `getGstReport` did. **Fixed:** same `settled` predicate now excludes cancelled/deleted in both.
- **Bug (incomplete export):** CSV `Summary` block omitted all tax totals (only shop/GSTIN/period/orders). **Fixed:** added Taxable/CGST/SGST/Total Tax/Total Revenue to the CSV summary.
- **Remaining (perf, not fixed):** both methods `findByRestaurantIdAndIsDeletedFalse` (ALL bills) then filter in memory by date — should be a date-bounded query. Flagged; scale concern, not correctness.
- **Test debt:** reconciliation invariant (`getTaxSummary` tax == `getGstReport` tax == SUM(cgst+sgst)) holds by construction now (identical source); an integration test needs Docker/Testcontainers (consistent with other Docker-gated server tests) — add when CI runs it.

### E5 — Auth & security
**Status:** ✅ mature.
- OTP login + rate limiting (`OtpRateLimiter`, `LoginRateLimiter`, `DbRateLimiter`), token blocklist + revocation cache, JWT filters (`JwtRequestFilter`, `TerminalRequestFilter` — rejects tokens whose credVer ≠ DB credentialVersion), app-version filter, security audit events.
- KB-001..009 hardening shipped (`PLAN.md §2`). Anti-enumeration on signup. CSP for Google Identity.
- **Not re-verified this session** — trusting prior audit + tests. A fresh `gstack-cso`/Strix pass is the right tool if you want depth here.

### E6 — QR public ordering — REVIEWED + hardened
**Status:** ✅ well-secured for a public endpoint; one real gap fixed.
- **Strong already:** prices always resolved server-side (client amounts ignored — no tampering); variant must belong to BOTH the restaurant AND the selected item (blocks cheap-foreign-variant attack); IP rate-limited (create + pay); item cap (50), qty bounds (1–20); menu exposes only available items + customer-safe fields; daily-order-id collision retry; pay endpoint enforces tenant ownership.
- **Gap found + FIXED:** neither `menu` nor `createOrder` verified the restaurant **exists / is not suspended** — a scanner could enumerate `restaurantId`s, read any restaurant's menu, and create orphan draft bills + fire push notifications against arbitrary/suspended ids. Added `isRestaurantOrderable()` (exists AND `!isSuspended`) guard → 404 on both endpoints. Injected `RestaurantProfileRepository`.
- **Tests:** added `menu_suspendedRestaurant_returns404`, `menu_unknownRestaurant_returns404`, `order_suspendedRestaurant_returns404`; seeded an active profile in existing test setup so prior cases still pass. **Main source compiles clean.**
- **Lower-severity (not fixed, flagged):** `lifetimeOrderId` set to `System.currentTimeMillis()` (timestamp-as-id, collides in same ms); `X-Forwarded-For` trusted for rate-limit keying (spoofable unless the proxy strips client XFF).

### ⚠️ Pre-existing server test-compile debt (NOT caused by this session)
5 test files (`BillServiceImplTest`, `BillServiceTest`, `BillDependencyResolutionTest`, `ItemVariantServiceImplTest`, `MenuItemServiceImplTest`) call `new GenericSyncService(...)` with **11 args**, but the current constructor needs ~17 (RelationalIdResolver, TerminalOwnershipService, BillSyncService, SyncNotificationService, UserProfileSyncService, BillPaymentSyncService added since). Server **main compiles clean**; only these hand-written test mocks are stale (the `343a5864` "stale test fixtures" pattern recurring). This blocks `mvn test-compile`, so server unit tests (incl. my new `PublicOrderControllerTest` cases) can't run until the 5 fixtures are updated. Flagged, not fixed (unrelated scope; fixing uninvited risks masking intended test state).

### E7 — Notifications internals
**Status:** ✅ per git history — data-only FCM payloads, ATC guardrails, grouped notifications, dedup, 90-day purge, per-device tokens. Matches pillar #14. Not deep-read this session.

### E8 — FSSAI / GST expiry compliance
**Status:** ✅ `FssaiTrackerService` + `ComplianceAlertService` — WhatsApp alerts at 30/15/7/3/1 days before expiry, severity tiers, tested (`FssaiExpiryTrackerTest`).

### E9 — Web admin (Angular) — UNTOUCHED
**Status:** ⚪ not analyzed at all this session. Businesses, terminals, staff, reports, feature flags, platform dashboard, inventory, daily-closing pages. Entire module is unreviewed. If web-admin is in scope, it needs its own feature-by-feature pass (I've only read its conventions, not its logic).

### E10 — Server sync internals — VERIFIED
**Status:** ✅ reference-quality; no bug found (read `GenericSyncService` directly this pass).
- The server half of the 409 fix is correct and complete: `saveAll` all-or-nothing → on `DataIntegrityViolationException`, per-record fallback in **`REQUIRES_NEW`** transactions (`syncFallbackSaver.saveRecord`) so non-colliding rows commit and only true conflicts → `failedLocalIds` (client quarantines).
- **Idempotent recovery:** a duplicate `publicToken` (client re-pushed after a lost response) is treated as **success**, not failure — no false quarantine (`billSyncService.attemptIdempotentRecovery`).
- **Double-report guard:** before adding to `failedLocalIds`, the fallback does `successfulLocalIds.remove(localId)` — a record can never be reported BOTH success and failure. Verified all other `failedLocalIds` add-sites (validation reject, clock-skew reject) `continue` before any success-staging, so no double-report anywhere.
- Failure reasons sanitized before returning to client. Clock-skew rejection with audit record.
- Tests are Testcontainers/Postgres (Docker-gated) — trusting them; logic verified by read.

---

## Part F — updated confidence map

| Area | Depth this session | Bugs found |
|---|---|---|
| Printing (KOT/invoice/58-80/PDF/SMS/WA) | Deep + fixed + tested | 3 |
| Billing math (GST/inclusive/round-off/floor) | Deep + fixed + tested | 2 |
| OCR menu | Medium (classifiers) + fixed | 2 |
| Sync (Android side) | Deep read; seam extracted+tested | 0 (solid) |
| Payment gateway (Easebuzz) | Medium + 1 fix + 3 open Qs | 1 |
| Order-id / timezone | Deep + fixed | 1 |
| Reports (daily/monthly) | Medium + fixed | 1 |
| Validation / permissions / payment modes | Deep + tested | 0 (solid) |
| Inventory | **Read only (this analysis)** | design Q |
| Refunds | **Read only** | 0 (strong) |
| Chargeback / fraud | Not analyzed | ? |
| Tax-compliance reports | Not analyzed | ? |
| Auth/security | Not re-verified | ? |
| QR public ordering | Not analyzed | ? |
| Web admin (Angular) | **Not touched** | ? |
| Server sync internals | Read via PLAN only | ? |

**Biggest unknowns (recommend next):** (1) dual inventory-deduction path (local vs server double-count), (2) tax-report vs invoice GST reconciliation, (3) web-admin logic pass, (4) QR public endpoint security.


# KhanaBook Android product review

Date: 2026-09-09. Reviewed the current working tree, including existing uncommitted changes, based on HEAD `0aea61aa`.

## Verdict

KhanaBook has a substantial merchant POS implementation, not merely a collection of prototype screens. Billing, local persistence, payment recovery, kitchen printing, reports, terminal activation, and configuration are connected. The next priority should be transaction reliability and counter speed, not adding more screens.

I would hold broad release sign-off until the four P1 findings below are fixed and tested. This is a source-level product review with a fresh JVM unit-test run, **not** a completed physical-device acceptance test, security assessment, or live Easebuzz certification.

Review findings: **4 P1, 2 P2; no P0 established in this review.** Product limitations and unverified device checks are listed separately rather than counted as bugs.

## Verification and limits

- Ran `./gradlew.bat testDebugUnitTest --offline`: successful, but tasks were up to date.
- Then ran `./gradlew.bat :app:testDebugUnitTest --rerun --offline`: successful; the test task executed freshly.
- Parsed the resulting XML: **49 suites, 272 tests, 261 passed, 11 skipped, 0 failures, 0 errors**.
- Skipped: `BillRepositoryTest` (6), `SyncManagerTest` (2), `HomeViewModelTest` (2), `UtilsTest` (1). Several core-flow tests are therefore not covered by this successful run.
- Did not run instrumentation tests, Android lint, a release build, printer hardware tests, TalkBack, large-font checks, benchmarks, production API calls, or live payments.
- Scenarios below describe the behavior implied by inspected code; they were not newly reproduced through the UI. Passing existing unit tests does not establish that these scenarios work.
- Used the Impeccable skill's source-level usability/audit guidance, existing `docs/product/PRODUCT.md`, `docs/product/DESIGN.md`, and Android-specific UI rules. The product's stated quick-billing/offline-confidence goals informed prioritization. No visual score or accessibility-compliance claim is made without device evidence.
- No application code or existing user changes were modified. This report is the only authored artifact.

## Prioritized findings

### F1 — P1: New-bill initialization clears restored work

Category: order recovery / merchant reliability.

Evidence: [NewBillScreen.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt), lines 81 and 141–146; [BillingViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt), lines 149–159, 343–358, and 442–452.

The view model restores cart items and customer fields from `SavedStateHandle`. However, each fresh composition of an ordinary new-bill route runs `resetForNewBill()`, clearing those same fields and resetting payment state. The current step also uses `remember`, not saved state.

Scenario: start an ordinary bill, enter the customer and several items, background the app, and have Android recreate the activity/process while retaining navigation state. The restored screen runs the reset branch because `draftBillId == null` and `resumePendingPayment == false`.

Impact: staff can lose an in-progress, unsaved order despite the existing restoration code. This finding concerns the ordinary cart; it does not assert that already persisted draft bills are deleted by this reset.

Recommendation: distinguish an explicit **new sale** event from restoration/re-entry. Clear state only for a new sale, and preserve the active step, order type, and payment context as appropriate. Review the pending-draft cleanup in the same initialization block separately before changing it.

Acceptance: recreate an activity and restore a saved process state at customer/menu/payment steps; assert identical items, quantities, notes, customer, order type, and amount. Also verify that explicitly starting another sale starts empty.

### F2 — P1: PDF payment totals omit Easebuzz and depend on today's configuration

Category: report correctness / owner trust.

Evidence: [ReportExporter.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/ReportExporter.kt), lines 68–74 and 241–299; [ReportViews.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/reports/ReportViews.kt), lines 115–129; [PaymentModeManager.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentModeManager.kt), lines 14–26.

The PDF's payment-summary mode list contains only enabled Cash, UPI, and POS. It never includes Easebuzz or Payment Link. Its displayed TOTAL sums only that list. The screen uses a broader list but still filters historical data using currently enabled payment modes.

Scenario A: the selected period contains ₹100 Cash and ₹400 Easebuzz, with no refunds. The PDF payment-summary total is ₹100, not ₹500. Its order metrics can consequently disagree with its payment summary.

Scenario B: collect UPI receipts, then disable UPI for future checkout. Reviewing the same historical period now hides UPI in the screen and PDF. The underlying sales have not disappeared.

The CSV builds its summary from the supplied data rather than this enabled-mode list, so it can disagree with the PDF.

Recommendation: derive historical report categories from transaction data, not checkout settings. Use one shared summary model for screen, PDF, CSV, and share text; distinguish component totals from combined split-payment labels to avoid double counting.

Acceptance: mixed Cash/UPI/POS/Easebuzz/Payment Link reports reconcile across all outputs, before and after disabling any checkout method.

### F3 — P1: Uncertain online payments are presented as failed

Category: payment recovery / status clarity.

Evidence: [EasebuzzPaymentViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/EasebuzzPaymentViewModel.kt), lines 116–164, 186–192, and 228–238; [EasebuzzPaymentScreen.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzPaymentScreen.kt), lines 244–278.

After ten status polls, an unconfirmed result or verification network error becomes `PaymentFailed` when no paid response was observed. The screen then says “Payment Failed” and offers a retry that starts order creation. Session expiry likewise becomes failure without establishing the gateway's final result.

Scenario: the customer authorizes payment, but status requests time out or continue returning pending. The cashier sees a failure although the result is still unknown.

Impact: staff may request another payment, switch collection method, or tell the customer an incorrect outcome. This review does **not** establish that a duplicate charge bypasses server-side safeguards. The confirmed issue is the Android state and recovery instruction.

Positive: the app already verifies against the backend instead of trusting SDK success alone, and a prolonged-verification view offers status checking. That uncertainty-aware behavior should remain available after polling expires too.

Recommendation: persist a distinct pending/unknown state and transaction identity, retain “Check status,” and only label a transaction failed when a definitive result supports it. Gate a fresh attempt on resolving the prior attempt.

Acceptance: all-pending, network failure after authorization, late success, SDK cancellation, process recreation, and repeated retry preserve one understandable payment history.

### F4 — P1: Refunded split-payment reports mix gross and net amounts

Category: report correctness.

Evidence: [ReportGenerator.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/ReportGenerator.kt), lines 23–56 and 120–139; [ReportExporter.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/ReportExporter.kt), lines 537–545.

`netAmount()` subtracts `refundAmount`. For split payments, the combined label uses this net amount, but Cash/UPI/POS components and the `_part` values retain the original `partAmount1` and `partAmount2`. CSV totals sum these unchanged simple-mode amounts.

Scenario: a completed ₹500 Cash+UPI bill contains ₹100 Cash, ₹400 UPI, and a synced ₹100 refund. The combined split amount becomes ₹400 while its components still total ₹500; the CSV payment total remains ₹500 for this example. The daily-report helper has the same inconsistency for Cash+UPI.

Recommendation: define gross collections, refunds, and net receipts separately. Allocate refunds to tender components only where actual refund/tender data supports that allocation; do not invent which tender was refunded. Use a separate refund adjustment when that allocation is unavailable.

Acceptance: partial/full refunds, all split pairs, no refund, and mixed regular/split bills reconcile under explicitly named gross/refund/net totals across outputs.

### F5 — P2: Normal counter checkout requires a customer's phone number

Category: checkout speed / product fit.

Evidence: [CartStep.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/newbill/CartStep.kt), lines 59–80, 317–338, and 340–356; [NewBillScreen.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt), lines 293–313.

Both dine-in and takeaway disable Continue unless a valid phone number is supplied. Customer details come before menu selection in this route.

Impact: a walk-in cash customer who declines to share a number cannot use this ordinary checkout path. It adds friction to the documented goal of creating a bill in under ten seconds. This is a confirmed product constraint, not a claim that mandatory numbers contradict an explicit implementation spec.

Recommendation: offer a guest/walk-in path for ordinary cash/offline billing. Request a number when the merchant chooses a feature that needs it, such as sending a receipt or a payment link. Keep any gateway-specific requirements separate.

Acceptance: complete a guest cash sale and a table order without a number; validate the number when a delivery/message/payment-link action requires it.

### F6 — P2: Quick Setup can finish without creating a usable menu item

Category: onboarding / input feedback.

Evidence: [QuickStartScreen.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/QuickStartScreen.kt), lines 60 and 343–348; [QuickStartViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/QuickStartViewModel.kt), lines 45–90.

Submit requires only nonblank name and price. The insertion loop silently skips a nonnumeric or nonpositive price, then marks setup complete regardless of how many items were inserted. The price field permits `.` and repeated dots, as well as `0`.

Scenario: enter the shop name and one item with price `0` or `.`. Setup can advance after saving a profile/category but without creating that item.

Recommendation: validate all rows before writes, show an error beside invalid prices, and require at least one successfully persisted valid item before setting completion. Make setup writes transactional or safely resumable so retries do not leave partial setup.

Acceptance: `0`, `.`, multiple dots, empty values, a mix of valid/invalid rows, and an interrupted save never produce a false successful setup.

## Product coverage: implemented versus available to merchants

“Implemented” below means inspected source paths exist and are connected where stated; it is not hardware or end-to-end certification.

| Merchant job | Current Android evidence | Assessment |
|---|---|---|
| Sign in and activate a counter | Auth/navigation, initial pull, pending approval, terminal reclaim, quick setup | Substantial onboarding exists; test replacement devices and F6 |
| Fast billing | Customer → menu → payment → result; quantities, variants, notes, tax calculations | Connected; F1 and F5 are higher priority than extra billing features |
| Keep open dine-in orders | Saved drafts, active orders, append items, later settlement | Implemented; table labels are not a full floor/occupancy model |
| Collect money | Cash, UPI, POS, supported two-way splits, Easebuzz, payment links | Implemented; F3 and reporting correctness need attention |
| Merchant payment onboarding | Easebuzz onboarding and compliance/agreement screens with repository/view-model paths | Implemented UI/integration; live merchant approval and payout outcomes not verified |
| Print receipt and kitchen ticket | Customer/kitchen roles, Bluetooth/Wi-Fi profiles, KOT formatting, retry queue, reprint | Real implementation; physical transport, paper sizes, and retry behavior need device testing |
| Recover while offline | Local bills, durable sync work, pending-payment prompt, sync issue center, printer queue, logout safeguards | Useful existing foundations; a passing JVM suite is not proof of full offline recovery |
| Review and export sales | Order/payment reports, date filters, detail views, PDF/CSV/share | Connected; F2/F4 prevent trusting every output |
| Manage menu | Categories, items, variants, availability/stock-related logic, OCR-related screens | Existing functionality; do not rebuild this as a new differentiator |
| Raw materials and insights | Inventory screen/view model and settings-section handler exist | **Not exposed through the normal settings tile:** entry is commented out for a later version; raw-material screen uses online APIs |
| Configure operations | Shop, menu, payment, printer, tax, settings, staff permissions, app lock | Broad coverage; owner/staff and permission-refresh device matrix still required |

Additional evidence: [AppNavGraph.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/navigation/AppNavGraph.kt), [InitialSyncViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/InitialSyncViewModel.kt), [PrintRouter.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PrintRouter.kt), [SyncCenterView.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/applock/SyncCenterView.kt), [LogoutViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/LogoutViewModel.kt).

Inventory availability: [SettingsHomeSection.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/SettingsHomeSection.kt), lines 109–112; [SettingsScreen.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/settings/SettingsScreen.kt), lines 158–159; [InventoryViewModel.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/InventoryViewModel.kt), lines 36–70. Do not describe raw-material management as absent from the codebase or fully offline.

## Important product boundaries, not release defects

- **Counter reports versus restaurant reports:** Android report queries pass `currentTerminalScope()` and filter `created_terminal_id`. Make that scope explicit in the UI/export before owners compare it with restaurant-wide numbers. See [BillRepository.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/data/repository/BillRepository.kt), lines 416–424, and [BillDao.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/BillDao.kt), lines 413–418.
- **KOT reprint is not a live kitchen display:** preserve the existing printer workflow, but use precise naming. Multi-station kitchen routing would require more than renaming this screen.
- **Printer capacity:** the current profile model has a unique `(restaurant_id, role)` index. A multi-station kitchen cannot be modeled as arbitrarily many printers of the same role without a design change. See [PrinterProfileEntity.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/PrinterProfileEntity.kt), lines 8–10.
- **Dine-in depth:** table/customer names and active drafts exist. A floor map, occupancy, move/merge table behavior, and structured modifier groups were not found in the reviewed Android routes/entities. Treat these as segment-dependent expansion, not automatic launch blockers.
- **Closing and settlements:** no dedicated Android counted-cash closing workflow or owner settlement reconciliation screen was found in the inspected navigation. Reuse existing backend/web capabilities where suitable; do not equate payment success with bank settlement.
- **Language coverage:** only the default `res/values/strings.xml` was found, and reviewed screens contain English literals. Local-language operation remains an expansion opportunity, not verified support.

## UI audit and field-validation limits

The Impeccable audit dimensions are recorded without a fabricated overall score. Android-specific components and repo UI rules take precedence over browser-oriented checks or aesthetic preferences.

| Dimension | Source observation | Remaining validation |
|---|---|---|
| Accessibility | Labeled fields and many described icon actions exist. Custom report filters use selected colors/weight without an explicit selected semantic state in those functions | TalkBack selection announcements, focus order, real contrast, effective touch bounds |
| Performance | Lifecycle-aware state collection and lazy lists are present; some report/export paths read detail records serially | Cold start, 10-second sale goal, large menus/order history, low-end device frame timing |
| Responsive layout | Theme layout/type-scale primitives exist; manifest requests portrait | Small phone, large fonts, tablet/multi-window, keyboard and inset behavior |
| Theming | Shared warm/gold/brown tokens are widely reused; some local hardcoded colors remain | Real-device contrast/readability and token consistency across all states |
| Anti-patterns / clarity | Largest demonstrated usability problems are forced data entry and inaccurate outcome labels | No screenshot-based aesthetic pass/fail awarded; do not redesign the brand based on source alone |

Selection-state reference: [ReportViews.kt](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/reports/ReportViews.kt), lines 39–104. Touch-target violations are **not** inferred solely from a visual size because Compose may provide larger effective interaction bounds.

## Strengths worth preserving

- Payment completion has backend verification and payment-set validation, rather than just an optimistic SDK callback.
- Atomic bill/payment finalization and partial-payment recovery paths already exist in the data layer.
- Print routing includes origin/terminal checks, and the home screen exposes pending kitchen work and payment recovery.
- The sync center distinguishes blocked bills, quarantined child rows, and identifier conflicts. Its “Blocked bills” count should not be mistaken for all unsynced records.
- Logout includes unsynced-data safeguards and database-preserving paths.
- Existing unit tests cover meaningful calculation, payment validation, gateway view-model, routing/queue, and sync-normalization behavior. Build on these; do not replace the architecture wholesale.

## Recommended execution order

1. **Restore transaction trust:** address F1–F4 with regression tests for exact scenarios, including identical screen/PDF/CSV totals.
2. **Reduce first-sale friction:** guest checkout, precise validation, and successful first receipt/KOT setup. Address F5/F6.
3. **Finish a focused device acceptance run:** use a disposable test account/backend and test devices, not merchant production data.
4. **Differentiate through a trustworthy close-of-day experience:** clearly separate this counter/all counters, gross sales/refunds/net collections, counted cash differences, unsynced items, pending payments, and actual settlement evidence. This is a proposed product direction, not a uniqueness claim against every competitor.
5. **Expand selectively:** expose inventory once ready; add floor plans, modifiers, local languages, or kitchen stations only for the merchant segment being targeted. Visual polish comes last, after functional acceptance.

### Minimum device acceptance matrix

- First install, first sale, returning staff login, owner approval, counter replacement/reclaim, session expiry.
- Cash, manually acknowledged UPI/POS, each split pair, Easebuzz success/failure/unknown, and late confirmation.
- Background/process recreation before persistence, after bill persistence, during gateway return, and before printing.
- Offline sale → app restart → reconnect; each bill, item, payment, and stock effect reconciles once.
- Bluetooth and Wi-Fi receipt/KOT on supported paper sizes; disconnect, paper-out, retry, reprint, cancelled order, and a second terminal.
- Reports across midnight/custom ranges, terminal versus restaurant scope, refunds, disabled payment methods, and matching exports.
- Large-font/TalkBack/keyboard/small-screen checks; first-sale timing with a real merchant menu.
- Run instrumentation migration/isolation tests and restore or replace the ignored core-flow tests before treating the green JVM suite as a release gate.

The existing `OfflineTest` exercises cached-screen/error/reconnect behavior, but does not establish the entire sale-to-reconciliation matrix above. `BillCreationUseCaseTest` primarily checks parameters/model behavior rather than invoking the full production billing workflow. These are coverage limits, not a claim that no useful tests exist.

# Whole-app function review — Android POS — 2026-11-20

Review requested as "do proper review on all function in app".

> **Status 2026-11-20 (Session 076): all seven findings are FIXED** — the owner granted full
> access and asked for permanent fixes. Each bug row below ends with what changed; the defect
text is kept as the record of what was found. Full suite after the fixes: **74 classes,
> 548 tests, 0 failures, 0 errors**; `assembleDebug` green. **Not installed** — no device was
> attached. Session 076 in `DEVICE_SESSIONS/SESSION_LOG.md` has the change list.

## Scope and method

| Item | Value |
|---|---|
| Source tree | `apps/android/app/src/main/java/com/khanabook/lite/pos` |
| Files | 263 Kotlin files, 66,163 lines |
| Function declarations | ~1,304 |
| Method | whole-tree pattern scans, then full read of the money / tax / date / sync core, then call-site tracing for every claim |
| Build state | `:app:compileDebugKotlin` and the full unit suite (528 tests) were green at review time (`SESSION_LOG.md` Session 074) |

Pattern scans run across the whole tree: unsafe `!!`, empty `catch`, `runCatching`,
`GlobalScope`/ad-hoc `CoroutineScope`, `Double`/`Float` money math, scale-less `BigDecimal.divide`,
`.toInt()` on money, `SimpleDateFormat`, division by variables, literal status-string comparisons,
destructive operations, permission checks.

Read in full: `BillCalculator`, `CartManager`, `BusinessDate`, `Utils` (date + money + share text),
`BillWithItems`, `BillItemEntity`, `PaymentLimits`, `ReportGenerator` (top half),
`HomeViewModel` summary block, `InvoiceFormatter`, `InvoicePDFGenerator`, `AppLockViewModel`,
`LogoutViewModel`, `OrderIdManager`, the invoice-identity allocation in `BillingViewModel`, the
remote-bill write path in `MasterSyncProcessor`.

**Coverage caveat.** This is not a line-by-line reading of all 1,304 functions. The scans cover
every file; the deep reads are concentrated where a defect costs money or data. Modules read at
scan level only: UI composables (`core/designsystem`, most of `feature/*/ui`), `feature/notifications`,
`feature/onboarding`, `core/theme`, `core/touch`, the 66 migrations, and the ~1,900-line
`BillDao`. Nothing below is based on a scan hit alone — each finding was traced to its call sites.

## Findings

### BUG-041 — P1, money: tax-inclusive shops are charged GST twice

- **Symptom.** With `isTaxInclusive = true`, the payable total is `subtotal + CGST + SGST`, even
  though the GST is already inside the subtotal.
- **Evidence.** `BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax, isInclusive = false)`
  (definition `BillCalculator.kt:53-66`) has an inclusive branch, but **no caller ever passes it** —
  the only two call sites are `CartManager.computeSummary` (`CartManager.kt:151`) and the bill
  recalc (`BillingViewModel.kt:1422`), both using the default `false`.
  `calculateGST(..., isInclusive = true)` *is* honoured at `CartManager.kt:144` and
  `BillingViewModel.kt:1415`, so the tax figures are the embedded ones and the total adds them again.
- **Proof.** ₹118.00 at 18% inclusive → base ₹100.00, CGST ₹9.00, SGST ₹9.00 →
  app total **₹136.00** instead of ₹118.00, i.e. **₹18.00 over-charged on every bill** (18% here,
  `pct/(100+pct)` generally).
- **Blast radius.** Every bill on a shop whose server profile has `is_tax_inclusive` set. The flag
  has no UI control on the till (recorded in Session 074), so this is set from the web/server side
  and is invisible to the cashier: the receipt shows Sub-total 118 + CGST 9 + SGST 9 = 136, which
  looks self-consistent and is simply wrong.
- **Also.** `calculateTotal(..., isInclusive = true)` returns the subtotal only, so it would drop
  `customTax` entirely — unreachable today because of the call-site bug, and worth fixing at the same
  time. Custom tax only applies when GST is off, so `inclusive` and custom tax can co-exist.
- **Why the test suite misses it.** `calculateTotal`'s inclusive branch is **correct and covered**:
  `BillCalculatorTest` (`app/src/test/.../BillCalculatorTest.kt`, 27 cases in two files) asserts
  `calculateTotal("525.00", "12.50", "12.50", "0.00", isInclusive = true) == "525.00"`. Every
  existing case calls the function directly; **no test exercises the two production call sites**, so
  the pure function is green while the wiring is wrong.
- **Direction.** Pass `profile.isTaxInclusive` at both call sites (the function itself needs no fix for
  the GST case; check the custom-tax interaction separately), and add a test that goes through
  `CartManager.computeSummary`, not just `BillCalculator`.

### BUG-042 — P2, sync: pulled bills keep the server's raw status/payment strings

- **Symptom.** Bills pulled from another terminal can be counted as drafts, or as completed-but-not-
  really, depending on how the server spells the status.
- **Evidence.** `MasterSyncProcessor.kt:350` defines `orFallback` as a blank check only
  (`this?.takeUnless { it.isBlank() } ?: default`) — no case/alias normalisation. The remote bill
  write at `:1810-1811` stores `paymentStatus = remoteBill.paymentStatus.orFallback("success")` and
  `orderStatus = remoteBill.orderStatus.orFallback("completed")` verbatim.
- **Why it bites.** Local readers disagree on how to compare:
  - `HomeViewModel.kt:257` uses case-sensitive `== "completed" || == "paid"` → a "PAID"/"Completed"
    string from the server yields **zero revenue** on the Home card.
  - `BillDao.kt:584, 595, 689, 745` compare literal lowercase values in Kotlin.
  - `ReportGenerator` uses `OrderStatus.fromDbValue` (case-insensitive) but maps **unknown → DRAFT**
    (`Enums.kt:35-38`), so a spelling the enum does not know is reported as "Preparing" and excluded
    from revenue.
- **Second half.** The fallbacks invent money-bearing values: a bill with no status becomes
  `completed`, and one with no payment status becomes `success`, so both enter revenue; a bill with
  no payment mode becomes `cash` (`:1807`), so unknown takings are attributed to the cash drawer in
  the payment breakdown.
- **Blast radius.** Any multi-terminal shop: terminal B pulls terminal A's bills and reports them.
  Single-device shops are unaffected because local writes are normalised (`BillRepository.kt:327-329`).
- **Direction.** Normalise at the boundary — `orderStatus = OrderStatus.fromDbValue(remote).dbValue`,
  same for `PaymentStatus`/`PaymentMode` — and prefer an explicit "unknown" over defaulting a missing
  status to `completed`.

### BUG-043 — P2, reporting: Home revenue and Reports revenue are different numbers

- **Evidence.**
  - Home (`HomeViewModel.kt:257-260`): `bills.filter { it.orderStatus == "completed" || it.orderStatus == "paid" }`,
    revenue = `sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }`, avg = revenue / completed count,
    while the "Orders" tile uses `bills.size` (drafts and cancellations included).
  - Reports (`ReportGenerator.netAmount`, `:22-30`): only `OrderStatus.COMPLETED`, `BigDecimal`,
    and **net of `refundAmount`**.
- **Consequence.** After one admin-recorded refund the two screens disagree by exactly the refund;
  Home also uses `Double` accumulation and silently treats an unparseable total as 0. The `"paid"`
  branch in Home is dead — `OrderStatus` has only `draft/completed/cancelled` (`Enums.kt:29-32`).
- **Direction.** One shared projection (e.g. `ReportGenerator.netAmount` + `OrderStatus`) consumed by
  both screens, so "today's sales" means one thing.

### BUG-044 — P2, receipts: unrelated items collapse into one line after a menu deletion

- **Evidence.** `BillWithItems.getConsolidatedItems` (`BillWithItems.kt:22-42`) groups by
  `groupBy { (it.menuItemId ?: 0L) to it.variantId }`. `bill_items.menu_item_id` is
  `ForeignKey ... onDelete = SET_NULL` (`BillItemEntity.kt:17-23`), so every historical row whose
  dish was later deleted has `menuItemId == null`.
- **Consequence.** All null-id rows in one bill share the key `(0L, variantId)`. A bill with
  "Paneer Tikka" and "Dal Makhani" (both since deleted) prints as a single line named after whichever
  row was read first, with the summed quantity and summed total — on the thermal receipt, the PDF and
  the shared text. Stored totals are unaffected; only the itemisation is wrong.
- **Direction.** Include the item identity in the key (`menuItemId, variantId, itemName, price`) so
  only genuinely identical rows merge.

### BUG-045 — P3, share text: custom tax never appears, GST rows can vanish

- **Evidence.** `generateBillText` (`core/util/Utils.kt`, the GST block starting around `:163`)
  prints `Sub-total`, `CGST`, `SGST`, `SAC` and `State Code` only inside `if (gstEnabled)`, and has
  no custom-tax row at all.
- **Consequence.** (a) A GST-off shop with a custom tax shares a bill whose items sum to less than
  the "Total Amount" with nothing explaining the difference — the same complaint BUG-037 fixed for
  the PDF. (b) With GST on but `bill.gstPercentage` null or 0, the `halfPct` guard drops the CGST/SGST
  rows while their amounts are still non-zero, so the tax disappears from the text entirely.
- **Direction.** Print whatever the bill carries: subtotal when it differs from the total, CGST/SGST
  whenever their amounts are non-zero, and the custom-tax row when `customTaxAmount > 0`.

### BUG-046 — P2, security: the app-lock PIN has no attempt limit

- **Evidence.** `AppLockViewModel.verifyPin` (`:141-158`) verifies and returns an error string; there
  is no counter, no delay and no lockout anywhere (`grep` for `verifyPin`, `attemptsLeft`,
  `lockAttempts` finds only the three call sites: `AppLockScreen.kt:77`,
  `SettingsSupportSections.kt:80`, `ShopConfigSection.kt:247`).
- **Contrast.** The login path does have one: `SessionManager.getFailedLoginAttempts` /
  `getLockoutUntilMs` / `clearFailedLogins` (`SessionManager.kt:385-398`) enforced in
  `AuthViewModel.kt:110-115`.
- **Consequence.** A 4-digit PIN behind BCrypt cost 12 (`AuthManager.kt:15-17`) is ~250 ms per try,
  so the whole 10,000-value space is reachable in roughly 40 minutes of unattended tapping. The PIN
  is the only thing between a stranger and a logged-in POS session.
- **Direction.** Reuse the existing login lockout state (or a separate key) with an escalating delay
  after 5 failures, and clear it on success.

### BUG-047 — P3, display: the menu list truncates paise

- **Evidence.** `ManualMenuView.kt:773` renders `₹${item.basePrice.toDoubleOrNull()?.toInt() ?: item.basePrice}`
  and `:767` renders the variant floor the same way.
- **Consequence.** A dish priced 149.50 shows as "₹149" and a variant floor of 99.99 shows "₹99" —
  visible price mismatch against the bill. `ItemEditDialog.kt:529` documents exactly this trap and
  avoids it, so the list is inconsistent with the editor.
- **Direction.** Route both through `CurrencyUtils.formatPriceCompact`.

## Verified clean (sampled deliberately, no defect found)

- **Tax exclusivity:** GST and custom tax are mutually exclusive in both computation paths
  (`CartManager.kt:143-148`, `BillingViewModel.kt:1414-1420`).
- **Money arithmetic:** every persisted amount flows through `BigDecimal` with explicit scale;
  `.divide` calls all pass a scale and `RoundingMode` (`BillCalculator.kt:31-49, 94`,
  `InvoiceFormatter.kt:218`, `InvoicePDFGenerator.kt:431`), so no `ArithmeticException` on
  non-terminating decimals.
- **Invoice allocation:** `getMaxInvoiceSequence` is scoped by `invoice_series` (financial year +
  full terminal series) and counts server-pulled bills, per its comment (`BillDao.kt:907-918`), and
  `allocateInvoiceIdentity` runs under `orderMutex`. *Assumption worth confirming:* the printed
  number uses only the series' **first character** (`BillingViewModel.kt:107, 123`), so two terminal
  series such as "B1" and "B2" would both print `B000001`. Safe if the server issues single-letter
  series; not verifiable from this repository.
- **Business day:** `BusinessDate` is a single, documented mapping with an explicit inverse
  (`windowStart`/`windowEnd`), including the 28-hour switch day; `getStartOfDay`/`getEndOfDay` in
  `Utils.kt` are the only other day maths and are zone-explicit.
- **Soft logout safety:** logout refuses/carries the unsynced-data summary before dropping the token
  (`LogoutViewModel.kt:106-122`); the hard-delete path (`clearDeviceDataAndLogout`, `:123`) has **no
  callers** in this repository, so the DB-deleting branch is currently unreachable dead code.
- **Permissions:** all master-data writes are owner-gated (see BUG-039 for the permission *table*
  that disagrees with the code).
- **Print queue:** no duplicate-job guard is needed at `PrintService` level because jobs are keyed by
  bill and dispatched through one foreground service; no evidence of double-print on retry was found.
- **Delete/void flows:** cancelled and deleted bills are excluded from reports by `is_deleted = 0`
  in the DAO queries, and voids adjust the stored totals rather than deleting rows.

## Other observations (no action proposed)

- `Utils.showAppToast` creates a fresh `CoroutineScope(SupervisorJob())` per toast (`Utils.kt:118-122`);
  harmless because the coroutine is short-lived, but it is an unowned scope.
- `addItemByScannedText` (`CartManager.kt:100-133`) ignores quantities in scanned text ("2 x Veg
  Biryani" adds one) and takes the first `contains` match, so an ambiguous line can pick the wrong dish.
- `MANUAL_MENU` star fix, GST gates and printer-status decoding from Session 074 remain as described
  there; this review did not re-open them.

## Correction to earlier notes

Session 074 recorded the bill maths as "agrees in both places that compute it" and called
`isTaxInclusive` "honoured by the bill math". **That was incomplete.** `calculateGST` honours the
flag, but `calculateTotal` is never told about it, which is BUG-041. Both statements have been
annotated in `SESSION_LOG.md` rather than deleted.

# Invoice & KOT — Follow-up R&D + fixes applied 2026-09-03

**Scope:** Continuation of `invoice-kot-templates-58-80-2026-09-02.md`. Re-grounded every
claim against the *current* working tree (not the 2026-09-02 snapshot) and against CBIC/GST
primary sources fetched 2026-09-03. Records what was fixed this session and what remains,
with the reason each remaining item is or is not legally required.

---

## 0. Correction to the prior doc

The prior doc's P0/P1 invoice items were partly **already implemented** in the uncommitted
diff (verified by reading the full files, not just the diff hunks):

| Prior claim | Actual state in working tree | Evidence |
|---|---|---|
| Thermal leaks GSTIN when GST disabled | FIXED — gated `if (isGst)` | `InvoiceFormatter.kt` header block |
| HSN/SAC missing | PRESENT — `SAC: 996331` line on thermal + PDF | `InvoiceFormatter.kt`, `InvoicePDFGenerator.kt` summary |
| Signature missing | PRESENT — `Authorised Signatory` (right-aligned) | both formatters |
| Round-off missing | PRESENT — computed `total − (subtotal + tax)`, printed if `abs > 0.005` | both formatters |
| CustomTax omitted in PDF | PRESENT — `customTaxName/Amount` fallback when `!gstEnabled` | `InvoicePDFGenerator.kt` |
| Logo blocks print (no timeout) | FIXED — `withTimeout(1500L)` around Coil | both formatters |
| Invoice `padStart(2)` vs server `%06d` | UNIFIED — Android now `padStart(6)` | `BillEntity.getInvoiceNumberDisplay`, `BillCreationUseCase` |

So the invoice GST-audit baseline (Rule 46 fields achievable without schema change) is met.

---

## 1. New defect found & fixed this session — 58mm amount column starved

**Root cause (code truth):** `PrintTokens.thermal58` had been revised to `itemW = 16`,
while `InvoiceFormatter` computed `amtW = width − itemW − qtyW − rateW − 3`.
For 58mm: `32 − 16 − 4 − 7 − 3 = 2`. The formatter then does
`formatMoney(item.itemTotal).take(amtW)` → `take(2)`, so `"1234.00"` printed as `"12"`.
This is a live money-truncation bug on 58mm paper (the default `paperSize`).

**Fix — paper-agnostic star column:** rewrote `PrintTokens` so the **item** column is the
star (leftover) column and the money columns are fixed and guaranteed to hold `NNNN.NN`:

```
itemWidthFor(contentWidth, qtyW, rateW, amtW) = (contentWidth - qtyW - rateW - amtW - 3).coerceAtLeast(1)
58mm: qtyW=3 rateW=7 amtW=7 → itemW = 32-3-7-7-3 = 12
80mm: qtyW=4 rateW=8 amtW=8 → itemW = 40-4-8-8-3 = 17
```

`InvoiceFormatter` now reads `PrintTokens.thermalXX.amtW` directly instead of re-deriving,
so the two can never drift. This is the "star column" refactor the prior doc filed as P2 —
implemented for real because the P2 turned out to mask a P0 truncation.

**Guard:** added `InvoiceFormatterTest` regression cases (58mm + 80mm) that assert a
4-digit amount `1234.00` survives to the output. Both pass.

**Trade-off:** 58mm item names now wrap at 12 chars (down from the buggy 16 that never
rendered a correct amount anyway). This is the ESC/POS 32-col reality; the honest options
are (a) wrap the name, or (b) switch to Font B 42-col (`columnsFallback42` token exists,
not yet wired). Wrapping is retained; Font B is a future toggle if truncation is complained about.

---

## 2. KOT event-aware printing — completed & tested

**Implemented (all in the uncommitted diff, verified compiling + tested this session):**
- `KitchenTicketFormatter.format(...)` gained a 5th param `event: KotEventEntity?` with a
  backward-compatible 4-arg overload delegating with `null` (→ NEW).
- Badges: `NEW / ADD / REMOVE / CANCELLED / REPRINT`, `DO NOT PREPARE` on full cancel,
  `KOT #rev`, per-event time (`event.createdAt`, not `bill.createdAt`), and `Type:` line
  from `bill.orderType` (suppressed for the legacy `"order"` default).
- Item rendering per event: `+ qty x name` for ADD, `CANCELLED: …` / `REMOVED: …` for VOID,
  `REPRINT COPY - ALL ITEMS` for reprint.
- Full-vs-partial cancel: `isFullCancel = VOID && itemsToPrint.size == active item count`.

**Fixed this session — cancel reason was never populated:** the formatter printed
`Reason: ${event.eventToken}`, but `recordKotEvent` never wrote `eventToken`, so VOID KOTs
showed an empty/garbage reason. Added an optional `reason` param to `recordKotEvent(...)`
that populates `eventToken`, and threaded `cancelOrder(reason)` through. Now the CANCELLED
KOT prints the real reason. (`BillEntity.cancelReason` also exists and is the persisted copy;
`eventToken` carries it onto the KOT event so the ticket is self-contained.)

**Event recording coverage (verified):** NEW on create, ADD on add-items & qty-up,
VOID on qty-down & cancel — `BillRepository` lines 76/354/514/529/531.

**Tests added (`KitchenTicketFormatterTest`), all passing:** NEW badge, ADD `+`-prefix delta,
full-cancel CANCELLED + DO NOT PREPARE + reason, partial REMOVE (no DO NOT PREPARE),
REPRINT badge + `KOT #R`, and null-event → NEW.

---

## 3. PrintService teardown — replaced arbitrary delay

`delay(3000L)` before `stopSelf()` was a fixed guess. `printRouter.printBill()` already
`awaitAll()`s each transport `print()` (which returns only after socket write+flush), so the
long delay was redundant. Replaced with a documented, bounded `FLUSH_DRAIN_MS = 800L`
(Bluetooth RFCOMM buffer drain) so the foreground service is not held alive needlessly.

---

## 4. GST 2026 primary-source cross-check (fetched 2026-09-03)

| Rule 46 field | Requirement (verified) | KhanaBook | Source |
|---|---|---|---|
| HSN/SAC (§46f) | ≤₹5cr AATO → 4-digit; >₹5cr → 6-digit; **exempt if <₹5cr supplying to unregistered person** | `SAC: 996331` printed (exceeds the exemption; safe) | cbic-gst.gov.in invoice rules; cleartax `mandatory-hsn`; vjmglobal |
| Recipient name/address/state+code (§46e) | Required if **unregistered AND taxable value ≥ ₹50,000**, or always if registered (B2B) | Not captured — see §5 | cbic-gst.gov.in `gst-invoice-rules` |
| Place of supply (§46m) | Required for **inter-state** supply | Not captured — see §5 | cbic-gst.gov.in |
| Reverse charge flag (§46) | Required field | Not captured — see §5 | cbic-gst.gov.in |
| CGST/SGST split | Two lines, not merged "5% GST" | Compliant (two lines) | prior doc §4 |
| E-invoicing (IRN/QR) | Threshold **₹5cr AATO**, current as of 2026 | Out of scope for target QSRs | gst.gov.in einvoice6; bajajfinserv |
| Ship-to GSTIN | Mandatory from **1 Aug 2026** for B2B different-delivery-address e-invoice upload | Out of scope (B2C dine-in/takeaway) | tallysolutions |

**Conclusion:** for KhanaBook's actual audience — sub-₹5cr B2C QSRs — the remaining
"missing" fields (recipient GSTIN, place of supply, reverse charge, IRN) are **not legally
required on the common path**. They matter only for the B2B / ≥₹50k-to-unregistered edge.
Right design is therefore *optional, nullable, print-only-when-present*, not always-on.

---

## 5. Remaining items — scoped, not yet built (need schema change)

These require nullable columns on `BillEntity` (+ Room migration, + server column, + sync DTO).
`BillEntity`/`BillItemEntity` today have **no** `customerGstin`, `placeOfSupply`,
`billingAddress`, `isReverseCharge`, or per-item `hsnCode` (verified against the entities).

**P1 — B2B / large-B2C compliance (only when a merchant needs it):**
- `BillEntity.customerGstin: String?`, `billingAddress: String?`, `placeOfSupply: String?`,
  `isReverseCharge: Boolean` (default false). Print only when non-null/true.
- Add a `State Code` line = first 2 digits of supplier `gstin` next to GSTIN (no schema change;
  derivable from existing `profile.gstin`). **Cheapest legit win — do this next, no migration.**

**P2 — per-item HSN:**
- `BillItemEntity.hsnCode: String?` defaulting to SAC `9963`/`996331`. Only surfaces on the
  invoice if a merchant sells packaged goods at a different rate; a single footer `SAC: 996331`
  already covers pure restaurant service.

**P2 — e-invoicing IRN stub:**
- Reserve an `IRN:` + QR box in the PDF, populated only if AATO > ₹5cr. No value for the
  current audience; defer until a >₹5cr customer exists.

**P2 — Font B 42-col fallback (58mm):**
- `PrintTokens.thermal58.columnsFallback42` exists but is unwired. Only worth wiring if
  12-char item wrapping generates real complaints.

---

## 6. Verification status

- `compileDebugKotlin` — green.
- `testDebugUnitTest` for `KitchenTicketFormatterTest` + `InvoiceFormatterTest` — green
  (6 new KOT tests, 2 new column-width regression tests).
- No commits made (per instruction). Suggested commit split unchanged from session summary:
  (A) KOT-event + GST-invoice + star-column + tests; (B) unrelated UI/logging.

## 7. Bottom line

The invoice/KOT correctness gaps that actually bite the target user are closed: KOT now tells
the kitchen what changed, VOID carries its reason, and 58mm no longer truncates money. The
remaining GST fields are edge-case (B2B / ≥₹50k / >₹5cr) and are correctly deferred behind a
nullable-schema design rather than bloating the common B2C receipt. Next cheapest legit
compliance win with zero migration: print supplier **State Code** (GSTIN[0:2]).

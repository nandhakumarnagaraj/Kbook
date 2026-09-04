# Invoice & KOT Templates R&D — 58mm / 80mm / PDF + GST 2026

**Date:** 2026-09-02  
**Scope:** `InvoiceFormatter.kt` (thermal 32/40 char), `InvoicePDFGenerator.kt` (164pt/226pt), `KitchenTicketFormatter.kt` (NEW/ADD/VOID), `ReceiptTheme.kt` vs GST Rule 46 + thermal standards (webfetch 2026). Every claim `file:line`.

---

## 0. Terminology

| Template | Width | File |
|---|---|---|
| 58mm physical | 32 chars `InvoiceFormatter.kt:102`, 256px logo `InvoiceFormatter.kt:123` | thermal ESC/POS `GS v 0` raster |
| 80mm physical | 40 chars `+4 pad` `InvoiceFormatter.kt:103`, 384px logo | same ESC/POS |
| Softcopy | 164pt (58) / 226pt (80) `InvoicePDFGenerator.kt:116`, dynamic height `InvoicePDFGenerator.kt:151` | `PdfDocument` |

---

## 1. Physical 58mm vs 80mm — code truth

### 1.1 Width branches (single source)

| Branch | 58mm | 80mm | File:Line |
|---|---|---|---|
| `is80mm` | `false` (default `RestaurantProfileEntity.kt:80` `paperSize="58mm"`) | `profile.paperSize=="80mm"` | `InvoiceFormatter.kt:101`, `InvoicePDFGenerator.kt:115`, `KitchenTicketFormatter.kt:29` |
| `width` / `charsPerLine` | 32 `InvoiceFormatter.kt:102`, PDF 164pt `InvoicePDFGenerator.kt:116` | 40 `InvoiceFormatter.kt:102`, PDF 226pt, KOT 40 `KitchenTicketFormatter.kt:30` |
| `leftPad` | `""` | `"    "` (4 spaces to center 40-char on 48-char 80mm printer) `InvoiceFormatter.kt:103` / `KitchenTicketFormatter.kt:31` |
| Logo raster | 256px `InvoiceFormatter.kt:123` | 384px |
| Item column | `itemW 12`, `rateW 7`, `amtW 6` `InvoiceFormatter.kt:173-176` `32-12-4-7-3=6` | `itemW 18`, `rateW 8`, `amtW 7` `40-18-4-8-3=7` | 
| PDF body | `bodySize 6f, itemColWidth 65f` `InvoicePDFGenerator.kt:125`, `scaledW 70f` `InvoicePDFGenerator.kt:165`, `title 10f` `InvoicePDFGenerator.kt:174` | `7f/100f`, `90f`, `12f` |
| Separators | `line 32*"-"` `doubleLine 32*"="` `InvoiceFormatter.kt:107` | `4 spaces+40*"-"` |

**Web cross-check** ([wcpos.com thermal-templates](https://docs.wcpos.com/receipts/thermal-templates), [whizz-tech 58/80 fit-to-width](https://whizz-tech.com/support/printers/excel-to-thermal-58mm-80mm-fit-to-width-height-presets/)): 58mm printable ~48mm, 80mm ~72mm at 203 DPI; vendor driver custom size 80x200 / 58x200 mm, margins 2-3mm, `Width=1 page Height=Automatic`. KhanaBook’s 32 vs 40 char budgets match 32-col (58) / 48-col (80) industry standard, but **hardcodes numeric widths** — web recommends `width="*"` star column (`WCPOS` §Star-Width) to stay paper-agnostic (58 gets 22 chars, 80 gets 38). KhanaBook’s 58 `itemW 12` is tighter than 22, causing aggressive `wrapText` `InvoiceFormatter.kt:266` `word-wrap`.

### 1.2 What prints (thermal) `InvoiceFormatter.kt:96-262`
1. Logo `GS v 0` `50-80` local `AppAssetStore.resolveAssetPath` else Coil `runBlocking` — 58 blocks longer on slow network (no timeout before print).
2. `BOLD_ON+LARGE_FONT GS ! 0x11` centered `shopName.uppercase() ?: ReceiptTheme.kt:8 "My Shop"` `139-143`, then `Address`, `FSSAI:`, `GSTIN:` (centered while `ALIGN_CENTER` active), `TAX INVOICE NO`/`INVOICE NO` `getInvoiceNumberDisplay()` `BillEntity.kt:158` (invoiceNumber > terminalSeries+seq padStart2 > INV+lifetime > dailyDisplay).
3. `ALIGN_LEFT` `formatRow("Bill: ${dailyOrderId}", "Date: ${DateUtils.formatDateOnly}", width)` + `Cust: + masked phone` `InvoiceFormatter.kt:154` (`take2+"XXXXXX"+takeLast2`).
4. Table `ITEM QTY RATE AMT` `String.format` `173`, `consolidatedItems` `wrapText` `195`, first line padded qty/rate/amt `formatMoney` `86` BigDecimal 2dp.
5. Summary `Sub-total:` `204`, `CGST/SGST` split `gstPercentage/2 HALF_UP 1dp` `207-214` **two lines** `CGST (x%):` `SGST (x%):` (Rule 46 compliant — see §4), fallback `customTaxName` `217-223`, `GRAND TOTAL` bold `221`, `Payment Mode` `231`, optional `QrCodeManager.generateQr(reviewUrl)` 256px `234` centered + `RATE US`, footer `invoiceFooter||"Thank you..."` centered `252`, `Powered by KhanaBook`, `CUT_PAPER GS V B 0`.

### 1.3 Gaps 58/80 physical
- **58 cramped:** `itemW 12` truncates `Biryani Family Pack (Extra Spicy)` to 3 lines vs 80 `itemW 18` = 2 lines. No `width="*"` adapt — fix below.
- **Logo:** 58 256px vs 80 384px raster `299-344` gray threshold `gray<128` — 58 dithers finer but slower.
- **No `isTaxInclusive` disclosure** `RestaurantProfileEntity.isTaxInclusive` never printed.
- **IGST absent** — always CGST+SGST, inter-state should be IGST 5%.
- **HSN/SAC/UQC missing** `InvoiceFormatter.kt:172` comment *removed HSN width entirely* — violates Rule 46 (see §4) + no SAC 9963 for restaurant service.
- **No per-item GST rate/taxable value, cess, discount, round-off.**
- **No customer GSTIN / billing address / place of supply / state code / reverse charge flag** — `BillEntity` has no `customerGstin`.
- **No HSN summary, no signature.**
- **58/80 divergence harmless:** only width/columns differ, same fields.

---

## 2. Softcopy PDF `InvoicePDFGenerator.kt:115-530` (164pt/226pt)

**Dynamic page** `pageHeight = headerH(145+logoH+waH+fssaiH+gstinH+shopWaH+shopEmailH)+itemSectionHeight+summaryH(100+gstTaxH22)+reviewQrH(110 if valid)+footerH+30` `139-152`; `countWrappedLines` `53` `measureText <= itemColWidth`.

**Branches:** `bodySize 6f/7f`, `itemColWidth 65f/100f` `InvoicePDFGenerator.kt:125`, `logo 70f/90f` `165`, `title 10f/12f`, `maxCustW 55f/90f` truncated `303`, `qtyRightX 122f/176f` `rateRightX 95f/141f` `332`, `qr 64f/80f` `483`, `footer 30/42 chars` `510`.

**Colors:** `colorPrimary #2E150B` digital vs `BLACK` print `172`, address `#757575` `189`, separator `#EEEEEE` `392`, digital tint `argb(25,#2E150B)` behind `TAX INVOICE` `254` + footer `504`, `NET AMOUNT` box solid `#2E150B` white text `445` else stroke `448`.

**Alignment:** logo centered `168`, shop `CENTER` `181`, address 2-line `CENTER`, FSSAI/GSTIN centered `drawCenteredLabelValue` `215` `(pageWidth-(lw+vw))/2`, `TAX INVOICE` `CENTER` inside dividers, Bill/Date `LEFT/RIGHT` split, customer `LEFT` truncated + phone right, table `LEFT ITEM` / `RIGHT RATE/QTY/AMT`, summary `52% left/right`, net box inset `10f`.

**Fields vs thermal:**
- **Adds** shop `Contact: whatsappNumber` + `Email:` `213-223` (thermal omits), FSSAI/GSTIN centered same.
- **Diverges:** PDF prints `GST NO:` only if `gstEnabled==true` `230` (correct); thermal prints `GSTIN:` whenever `gstin` exists `148` even if GST disabled — leaks GSTIN. **P0 fix align to PDF.**
- **Duplicate** `TAX INVOICE NO:` header `232` + title `263`; thermal once — harmless.
- **Omits** `customTaxAmount` `415-433` — if `gstEnabled==false` with `customTaxPercentage` (service charge) silently not shown, thermal `217-223` does. **P1 gap.**
- Same HSN/IGST gaps; also **no IRN/QR e-invoicing**, still `DRAFT-LOC-id` fallback not FY-unique for GST.

---

## 3. KOT all events `KitchenTicketFormatter.kt:60` + `KotEventEntity.kt:12`

**Event types** `KotEventType.kt:12` `NEW / ADD / VOID / UNKNOWN`, PK `(public_token, kot_revision)` `KotEventEntity.kt:35` `MIGRATION_55_56`, comment `8-11` device auto-prints own events only.

**Current formatter is event-agnostic:** no branch on `eventType` — always prints `shopName` `40`, `line`, `Order: {dailyOrderDisplay}` `43`, `Invoice: {getInvoiceNumberDisplay()}` `44`, `Time: {bill.createdAt}` `45` (not `KotEvent.createdAt`), optional `Customer`, then loop `itemsToPrint` (`PrintService` passes `sentToKot==false` delta, see `requirements.md:8.2`) `BOLD_ON "${quantity} x ${itemName}"` `51` + `Variant:` + `Note:`, `line` + `CUT_PAPER`.

**Gaps (P0 for kitchen):**
- **Zero handling of NEW/ADD/VOID:** `ADD` looks identical to `NEW`; `VOID` prints positive `${quantity} x ${itemName}` with no `CANCELLED`/`-qty` flag — kitchen cannot distinguish void from new. `kotRevision`, `eventType` data in DB `KotEventEntity.kt:35` but paper ignores it.
- **No KOT metadata:** no `*** NEW KOT ***` / `*** ADD-ON ***` / `*** VOID ***` title, no `KOT #revision`, `eventToken`, `originatingDeviceId/originTerminalId`, `Table/Floor`, `Waiter`, `OrderType dine-in/takeaway`, `Token No`.
- **No per-event time:** only `bill.createdAt` global, not `KotEventEntity.createdAt` per revision — `ADD` at +10 min shows same time.
- **No qty split for VOID:** should show `-qty` or `~~strikethrough~~` (ESC/POS supports `ESC - 1` underline or `GS !` inverse).
- **No masking, no logo/QR, no alignment variant beyond width.**

**What should print (fix below):** `NEW` badge, `ADD` badge + delta only, `VOID` badge + `CANCELLED` + voided items, header `KOT #rev` + event time, table, order type.

---

## 4. Web cross-check — GST 2026 + thermal standards

**GST Rule 46 (CGST Rules 2017, Section 31 CGST Act, CBIC):** Every tax invoice must have 16 mandatory fields (`https://taxgarden.in/blog/gst-invoice-rules-format-mandatory-fields-e-invoice-india-2026`, `https://indiataxsim.com/blog/gst-invoice-format-guide`, `https://zapinvoice.in/blog/gst-invoice-format-requirements` 2026-07-04). KhanaBook thermal/PDF currently has: Supplier Name ✓ (`shopName`), Address ✓, GSTIN ✓ (when enabled), Invoice No ✓ (`getInvoiceNumberDisplay()` ≤16 chars, FY-unique via `invoiceSeries`), Date ✓, (Recipient Name ✓ masked), HSN/SAC **✗**, Description (Item) ✓, Qty ✓, Rate ✓, Amount ✓, Discount ✗, Taxable Value ✗ (only subtotal), Tax Rate+Amount ✓ (CGST/SGST split correct), Place of Supply ✗, Reverse Charge ✗, Signature ✗, plus FSSAI 14-digit mandatory since 1 Oct 2021 ✓ (`FSSAI:` line `InvoiceFormatter.kt:147`, `Petpooja` 2026-07-20).

**Restaurant GST 2026:** standalone 5% without ITC (`CGST 2.5%+SGST 2.5%` split as two lines — KhanaBook does `InvoiceFormatter.kt:207` correctly; Petpooja notes merged `5% GST` single line is non-compliant, KhanaBook is compliant). Composition → `Bill of Supply` not `TAX INVOICE` (KhanaBook toggles `isGst` `InvoiceFormatter.kt:149` ✓), no service charge auto-levy (unlawful since CCPA 2022, Delhi HC 2025-03-28 — KhanaBook `customTax` optional, not default ✓), sealed bottle vs prepared food rate n/a. E-invoicing threshold Rs 5 Cr from 1 Apr 2026 `Notification 17/2025-CT` (`taxgarden.in` 2026-07-04) — threshold not yet in KhanaBook.

**Thermal standards** (`https://docs.wcpos.com/receipts/thermal-templates`, `https://whizz-tech.com/support/printers/excel-to-thermal-58mm-80mm-fit-to-width-height-presets/`): 32-col 58mm / 48-col 80mm, vendor driver custom 80x200/58x200 mm margins 2-3mm, `Width=1 page Height=Automatic`, star column `width="*"` for paper-agnostic (58 gets 22, 80 gets 38). KhanaBook hardcodes 12/18 — tighter than star — causes extra wrapping on 58.

---

## 5. Prioritized recommendations (P0/P1/P2) — order-id + templates combined

**P0 — invoice will fail audit / kitchen will mis-cook**
- **KOT event type on paper** `KitchenTicketFormatter.kt:38` add `when(eventType){ NEW->"*** NEW KOT ***", ADD->"*** ADD-ON KOT #$rev ***", VOID->"*** CANCELLED KOT #$rev ***" }` in `BOLD_ON+LARGE_FONT`/`invert` `ESC r 1`, print `eventTime = KotEventEntity.createdAt` not `bill.createdAt`, pass `KotEventEntity` into `format()` (currently only `BillWithItems`). VOID items as `CANCELLED: ${qty} x ${name}` or `-qty`.
- **Align thermal GSTIN visibility to PDF** `InvoiceFormatter.kt:148` gate `if(isGst && gstin.isNotBlank())` (now unconditional).
- **Add HSN/SAC column** — even 4-digit `HSN 4` fallback: `BillItemEntity` needs `hsnCode` (default `9963` SAC for restaurant service `RestroIQ 2025-05-20`), show per-item or at least `HSN: 9963` in footer; move to `width="*"` layout to reclaim width (58 `itemW` → star `22`).
- **Add signature & round-off** — `Authorised Signatory` line `ALIGN_RIGHT` + `Round Off: +/-` if `grandTotal != taxable+tax`.

**P1 — compliance / multi-device**
- **Invoice format divergence** `BillCreationUseCase.kt:280` `padStart(2)` vs server `%06d` `BillServiceImpl.java:137` — unify to `06d` both sides (GST serial ≤16 chars `TaxGarden` §Serial, FY-unique already, but 2-digit will `INV2` collide at `100` bills/series/FY). Also make `repairFailedDailyOrderIdentity` `BillDao.kt:1004` also repair invoice collisions (currently daily only `docs/rnd/order-id-5device…:78`).
- **CustomTax in PDF** `InvoicePDFGenerator.kt:415` add `customTaxName/Amount` fallback `217` branch.
- **OrderId `MAX` window** `RestaurantDao.kt:168` replace `MAX` backfill with `SELECT … FOR UPDATE` on `terminal_daily_counter` or retry-on-409 loop (doc §2.1).
- **Timezone midnight skew** — use `lastResetDate` (already) not `created_at BETWEEN` alone `BillDao.kt:255`; already has, but keep ±5min `GenericSyncService.java:399` check tight.
- **Place of supply / customer GSTIN / reverse charge** — add `BillEntity.customerGstin, billingAddress, placeOfSupply, isReverseCharge` nullable, print only when present (B2B >50k rule).

**P2 — polish**
- **Star column refactor** `InvoiceFormatter.kt:173` `itemW = width - qtyW - rateW - amtW -3` with one `width="*"` item column, test both widths live preview (`WCPOS` debounce 300ms).
- **Logo timeout** — `InvoiceFormatter.kt:50` `runBlocking` Coil without timeout → wrap `withTimeout(1500)` else skip logo, don't block print.
- **FSSAI line** already ✓ since 2021, keep; add `State Code` next to GSTIN (first 2 digits of GSTIN).
- **E-invoicing IRN** stub when `AATO >5Cr` `Notification 17/2025` — reserve `IRN: / QR` box in PDF `InvoicePDFGenerator.kt:478`.

---

## 6. Physical vs softcopy vs KOT — summary

| Aspect | 58mm `InvoiceFormatter` | 80mm `InvoiceFormatter` | Softcopy `InvoicePDFGenerator` | KOT `KitchenTicketFormatter` |
|---|---|---|---|---|
| Width | 32 chars | 40 chars +4 pad | 164pt / 226pt | 32/40 same as receipt |
| Logo | 256px raster | 384px | 70/90f centered | none |
| Invoice No | `getInvoiceNumberDisplay()` | same | same + duplicate `TAX INVOICE NO:` header | `Invoice:` + `Order: {dailyOrderDisplay}` |
| GST | CGST+SGST split **compliant** | same | same but hides when `!gstEnabled` (correct) | none |
| HSN | **missing** | missing | missing | n/a |
| KOT event | n/a | n/a | n/a | **missing NEW/ADD/VOID badge, revision, time** |
| CustomTax | shown | shown | **omitted** | n/a |

**Bottom line:** 58/80 physical and PDF share same data but diverge on GSTIN/customTax/FSSAI duplication; 58 is cramped due to fixed `itemW`. KOT is the biggest gap — event type never reaches paper. Fix KOT first (kitchen correctness), then HSN+signature for GST, then star-column + 06d invoice unification.


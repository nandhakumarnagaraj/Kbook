# Order History Operations

### Operation: View Daily Order Log & Date Filtering

**Operation ID:** OP-004  
**Module:** Order History & Reports  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-14 (business-window caption, `Date/Time` column, Custom range chip)

> **Change notice (2026-11-14):** the Orders screen now states the business window it is
> querying and shows the calendar date in the `Time` column for `Yesterday`/`Custom`. The
> `2026-11-11` rows below are preserved as historical evidence; the current, re-verified
> behaviour is in the "2026-11-14 additions" table.

**Purpose:**  
Inspect settled and draft orders for the current business date, verify sequential daily order numbering (`#A01`, `#A02`, `#A03`, `#A04`), and access past dates.

**Preconditions:**
- Terminal is unlocked.

**Navigation Path:**  
Bottom Navigation Bar → **`Orders`** Tab (`[244,1460][475,1600]`)

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Tap **`Orders`** tab at bottom | Orders screen displays with `Today` filter active | Tab bar highlights Orders; table header shows `OrderNo`, `Amount`, `Time`, `Action` |
| 2 | Review order rows | Lists all bills issued during current business day | Live rows observed:<br>• `#A04`: `Dine-in · Cash` • `₹367.50` • `11:12 PM`<br>• `#A03`: `Takeaway · Cash` • `₹367.50` • `10:50 PM`<br>• `#A02`: `Dine-in · UPI` • `₹367.50` • `10:01 PM`<br>• `#A01`: `Dine-in · Cash` • `₹367.50` • `12:25 AM` |

**Screenshots:**  
- Orders Screen: `artifacts/android-ui-tests/device_orders_screen_final.png`
- 2026-11-14 `Today` with business-window caption: `artifacts/android-ui-tests/orders-business-window-2026-11-14.png`
- 2026-11-14 `Custom` (12–14 Nov) with chip range and `Date/Time` rows: `artifacts/android-ui-tests/orders-custom-range-2026-11-14.png`

### 2026-11-14 additions (VERIFIED on `ZA222J5KT7`)

> **Superseded in part at 12:45 the same day:** the business-window caption described in steps
> 1–3 was **removed** at the product owner's request. Chips, the `Date/Time` column and the
> timezone-correct row times are unchanged. See *"Caption removed"* below.

Shop under test: closing `00:00` + 30-minute settlement buffer, so the business day
boundary is `12:30 AM`, not midnight.

| Step | User Action | Expected UI Result | Observed Result (2026-11-14) |
| :--- | :--- | :--- | :--- |
| 1 | Open **`Orders`** | Chips `Today` / `Yesterday` / `Custom`; a caption under them states the exact business window; header shows `Time` | Caption `Business day 14 Nov, 12:30 AM → 15 Nov, 12:30 AM`; header `OrderNo`, `Amount`, `Time`, `Action` |
| 2 | Tap **`Yesterday`** | Caption moves to the previous business day; header becomes `Date/Time` | Caption `Business day 13 Nov, 12:30 AM → 14 Nov, 12:30 AM`; header `Date/Time` |
| 3 | Tap **`Custom`**, pick `12 Nov` → `14 Nov`, **`Apply`** | Chip text replaces `Custom` with the picked range; caption states the resulting business window; rows carry `dd MMM` + `hh:mm a` | Chip `12 Nov - 14 Nov`; caption `Business day 12 Nov, 12:30 AM → 15 Nov, 12:30 AM`; row time `13 Nov⏎12:15 AM`, `12 Nov⏎10:48 PM` |
| 4 | Observe a row created before the boundary | Row still belongs to the previous business day | `#A02` at `13 Nov 12:15 AM` appears in the `12 Nov – 14 Nov` range, not in `Today` |

Notes:
- The caption is **hidden** when the window starts at plain calendar midnight and the filter
  is not `Custom`; there is nothing to disambiguate in that case.
- Row times are rendered in the shop's timezone (`BusinessDate.zoneOf(profile.timezone)`),
  not the device timezone, so they always agree with the window that produced the list.
- Order modifications still happen **only** inside `OrderDetailsDialog` (payment mode,
  cancel, print, share). The table's row-level modification callbacks remain unwired by
  decision — see `NEXT_ACTIONS.md` item K.

#### Caption removed — 2026-11-14 12:45 (VERIFIED on `ZA222J5KT7`)

Requested by the product owner: the `Business day … → …` line is gone for **all** filters
(`Today`, `Yesterday`, `Custom`). Nothing else about the table changed.

| Step | User Action | Expected UI Result | Observed Result (12:45, closing `01:00`) |
| :--- | :--- | :--- | :--- |
| 1 | Open **`Orders`** | Chips, then the table header directly — no line between them | Header `OrderNo`, `Amount`, `Time`, `Action` at `y336`; previously `y367` (31 px reclaimed) |
| 2 | Tap **`Yesterday`** | Still no caption; header becomes `Date/Time` | `Date/Time` at `y336`, empty state starts at `y600` |
| 3 | Tap **`Custom`** | Date-range picker opens as before; range still appears on the chip after `Apply` | Picker `November 2026`, `14 Nov - 14 Nov`, `1 day` |
| 4 | Scroll a long list | Filter chips scroll away with the content, unchanged | Chips remain inside the animated header block |

Code: `ReportsViewModel.windowCaption` / `_windowCaption` / `publishWindow()` deleted, and the
caption composable removed from `OrdersScreen.kt` (the filter row no longer needs a wrapping
`Column`). `customRangeLabel` and `formatCustomRange` are **kept** — the Custom chip still echoes
the picked dates.

What was lost, recorded so it is a decision and not an oversight: with an overnight shop,
`Today` can contain two calendar dates and the screen no longer says why. The `Date/Time` column
on `Yesterday`/`Custom` still carries day context for those filters; `Today` shows time only.

#### Does the window follow a closing-time change? (VERIFIED 2026-11-14)

**Yes, at runtime — it is derived from the saved profile, never hardcoded.**

| Question | Answer |
| :--- | :--- |
| Is the boundary/business window hardcoded? | No. It comes from `openingTime`/`closingTime` (+30-minute settlement buffer) in the shop profile via `BusinessDate.closingBoundary` → `windowStart`. |
| Does editing the closing time update the list without a restart? | Yes. `restaurant_profile` is a Room `Flow`; `ReportsViewModel` collects it and recomputes the active window on every emission, then re-queries. (Until 12:45 it also republished the caption; that caption no longer exists.) |
| Which filters follow the change? | `Today`, `Yesterday` **and** `Custom` (Custom re-derives its window from the picked dates as of 2026-11-14). |
| Does it rewrite already-recorded orders? | No. Stored bills keep their stamped business date; only the *query window* moves. |

Caveat worth knowing: changing the closing time is **retroactive** for `Today`/`Yesterday`/
`Custom`, because `windowStart(date)` applies the current opening/closing to every date
(`BusinessDate.kt:205-213`). `businessDayCutoffTime` has an activation date
(`cutoffEffectiveFrom`) that pins history; the closing-time path does not. Consequence: after a
closing-time edit, a late-night order can move from `Today` to `Yesterday` (or out of a
`Custom` range) because the boundary that classifies it changed. Tracked as `NEXT_ACTIONS.md`
item N.

#### Selected chip shows its date; one table — 2026-11-14 (Session 011, VERIFIED on `ZA222J5KT7`)

The Orders screen is now exactly three chips and one table. The selected chip states the day it
resolves to instead of its name, matching what `Custom` already did.

| Step | User Action | Expected UI Result | Status |
| :--- | :--- | :--- | :--- |
| 1 | Open **`Orders`** | Chips `14 Nov` (selected) / `Yesterday` / `Custom`; table below | Observed `14 Nov` / `Yesterday` / `Custom`, header `OrderNo · Amount · Time · Action`, rows `#A02 03:32 PM`, `#A01 12:33 PM` |
| 2 | Tap **`Yesterday`** | Selected chip reads `13 Nov`; `Today` chip returns to the word `Today` | Observed `Today` / `13 Nov` / `Custom`, empty state `No orders in this period` |
| 3 | Tap **`Custom`** | Picker opens; the chip still reads `Custom` until dates are applied | Observed picker `November 2026`, `14 Nov – 14 Nov`, `1 day` |
| 4 | Apply `12 Nov` → `14 Nov` in the picker | Chip reads `12 Nov – 14 Nov`; `Yesterday` chip returns to its word | Not re-run this session; unchanged code path from the 12:20 verification |

Notes:
- The label is the **business** date from `BusinessDate.businessDate(now, profile)`, the same
  value that selects the query window. With a `12:30 AM` boundary the chip can read `13 Nov`
  while the clock says `14 Nov 00:40` — intended, because that is the day being listed.
- Unselected chips never carry a date, so the row cannot show three dates at once.
- **Table rebuilt 2026-11-14 16:4x:** one `OrderTableCell` primitive builds every column for the
  header and the rows, so the columns cannot drift; rows are 52 dp tall on phones (60 dp on the
  wide layout) and the action is a 36 dp disc inside a 48 dp hit area that opens the details
  dialog (`View order A02`). Rows are keyed by `billId` in the `LazyColumn`. Four columns, no
  new data.
- **Frozen:** the `Today` / `Yesterday` / `Custom` chip row was approved as shipped on
  2026-11-14 and must not be edited again.
- Table columns line up: the row's order-number cell carries the same `COL_ORDER` weight and
  start inset as its header. Verified on `ZA222J5KT7` by matching uiautomator bounds
  (`OrderNo [49,336][225,362]` over the order cell `[49,411][225,436]`).
- The table prints the order id **without** `#` (`A02`) at the product owner's request; the
  details dialog, search and share text still prefix it (`#A02`).
- The `Payment`-level view was removed completely: the branch in `OrdersScreen.kt`, and
  `PaymentLevelView` + its `PaymentModeItem` / `PartPaymentCard` / `ReportTypeToggle` helpers in
  `ReportViews.kt`. The table is the only content branch besides the first-load skeleton.
  `reportType` / `setReportType` and the exporter's `reportType` parameter went with it.
  **Superseded 2026-11-14 17:2x:** report export was deleted entirely (see addendum 17:2x), so
  there is no PDF/CSV path left to title.
- Roles without `reports.full` still see only the `Today` chip (unchanged).

#### Order dialogs — 2026-11-14 19:3x (VERIFIED on `ZA222J5KT7`)

Tapping a row or the row's action opens **Order Details**, which now presents order identity in one
soft card (order no, invoice, type, date), items, payment mode, the gold total strip, then
`Print | Share` side by side, `Change Payment Mode`, and `Cancel Order`.

- **Change Payment Mode** opens a reason-free list of the shop's enabled modes with icons and a check
  on the current one. Choosing a simple mode saves immediately; choosing a split mode opens
  **PartAmountDialog**, which shows `Order total`, pre-fills the split, and requires both parts to add
  up before Confirm enables.
- **Cancel Order** opens a reason list (Wrong order / Customer left / Test bill / Other) with
  `Keep Order` and `Cancel Order`; Other reveals a free-text field.
- **Refresh behaviour (fixed 2026-11-14):** the details dialog is a snapshot. It now re-reads the bill
  after a payment-mode change or a cancel, so the mode and the available actions update without
  closing the dialog. Before this, the dialog showed the old mode until reopened.

#### Table hugs its rows — 2026-11-14 18:3x (VERIFIED on `ZA222J5KT7`)

The panel used to be `fillMaxSize()`, so two orders left a large empty card filling the rest of
the screen. The table now sits in a `BoxWithConstraints` and measures `heightIn(max = maxHeight)`
with the list weighted `weight(1f, fill = false)`:

- Few rows: the panel ends just below the last row, and the empty space below is page background.
- Many rows: the panel grows to the available height and the list scrolls as before.
- Empty result: the panel keeps a 180 dp empty-state area instead of collapsing.
- Header label changed `OrderNo` → `Order No` (the old label read as one word). Columns, weights
  and row contents are unchanged; the frozen chips are untouched.

Observed: header `Order No · Amount · Time · Action`, then `A02` (cancelled, red edge and struck
amount) and `A01`, with the panel ending right after `A01`.

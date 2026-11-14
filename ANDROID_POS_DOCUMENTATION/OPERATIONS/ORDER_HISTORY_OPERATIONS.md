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

#### Does the window follow a closing-time change? (VERIFIED 2026-11-14)

**Yes, at runtime — it is derived from the saved profile, never hardcoded.**

| Question | Answer |
| :--- | :--- |
| Is the boundary/business window hardcoded? | No. It comes from `openingTime`/`closingTime` (+30-minute settlement buffer) in the shop profile via `BusinessDate.closingBoundary` → `windowStart`. |
| Does editing the closing time update the list without a restart? | Yes. `restaurant_profile` is a Room `Flow`; `ReportsViewModel` collects it and recomputes the active window on every emission, then republishes the caption. |
| Which filters follow the change? | `Today`, `Yesterday` **and** `Custom` (Custom re-derives its window from the picked dates as of 2026-11-14). |
| Does it rewrite already-recorded orders? | No. Stored bills keep their stamped business date; only the *query window* moves. |

Caveat worth knowing: changing the closing time is **retroactive** for `Today`/`Yesterday`/
`Custom`, because `windowStart(date)` applies the current opening/closing to every date
(`BusinessDate.kt:205-213`). `businessDayCutoffTime` has an activation date
(`cutoffEffectiveFrom`) that pins history; the closing-time path does not. Consequence: after a
closing-time edit, a late-night order can move from `Today` to `Yesterday` (or out of a
`Custom` range) because the boundary that classifies it changed. Tracked as `NEXT_ACTIONS.md`
item N.

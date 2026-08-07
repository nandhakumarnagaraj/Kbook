# KhanaBook UI Redesign — Gap Audit & Plan

**Date:** 2026-06-16
**Source mockups:** `C:\Users\nandh\Desktop\Current App Screen\` (Login Page.jpeg, UI-1…UI-4.png — light & dark themes)
**Scope:** All mockup screens (already exist in code) → redesign to match.
**Decisions:** Build order = Phases A→F. Fidelity = **match structure/sections/components, keep existing design tokens** (shapes, spacing, type scale, colors). Light-theme correctness is a cross-cutting requirement (mockups show both themes).

App root: `Android/app/src/main/java/com/khanabook/lite/pos/ui`. Stack: Jetpack Compose, complete `theme/Kb*` token system already exists.

---

## High-impact gaps (structural)

| # | Screen | Mockup | Current | Action |
|---|--------|--------|---------|--------|
| 1 | Order Detail | Payment timeline Created→Paid→Printed→Completed (check circles + connectors); full screen | No timeline; detail is `OrderDetailsDialog`/`OrderDetailsPane` | New `PaymentTimeline` composable; consider full-screen detail |
| 2 | Reports | 4 KPI cells (Total Orders, Total Revenue, Avg Ticket, Cancelled) above table | KPIs calculated (`ReportsScreen.kt:103-134`) but never rendered | Render 4-cell stat grid |
| 3 | Home/Today's Summary | 4 equal tiles: Total Orders, Avg Ticket, Revenue, Cancelled + View All | 1 big revenue card + 2 tiles (Orders Today, Pending); Avg Ticket & Cancelled missing | Restructure to 4-tile grid; add missing metrics |
| 4 | Home/Quick Actions | New Bill (big) · Orders · Reprint Bill · Order Status · Find Bill · Call Customers | New Bill + Orders/Menu/Customers + Find Bill/Reprint KDS/Settings | Re-map action grid |
| 5 | Help & Support | Prominent "KYC Verified" status card at top | Missing entirely (`SettingsSupportSections.kt`) | Add KYC status card (reuse Easebuzz KYC state) |
| 6 | Create Bill | Order-type selector (Walk-in/Dine-in/Takeaway/Delivery) + subtotal/tax/total | `OrderTypeButton` defined but unused; totals show only item-count+total | Integrate selector; add tax/subtotal rows |

## Medium gaps (layout/affordance)

| # | Screen | Gap |
|---|--------|-----|
| 7 | Home header | Mockup has profile avatar + "Good Evening / Nandha"; current shows only logo card |
| 8 | Orders header | Mockup: search + filter as top-right icons; current: inline below title |
| 9 | Reports table | Mockup columns Order ID · Status · Amount · Date · Action; current order-level omits visible Amount column |
| 10 | Login | Mockup header has 3 badges (Secure Data · Works Offline · Auto Backup) + "Quick Login" divider; current has neither (divider reads "or continue with") |

## Low gaps (token consistency — aligns with ongoing cleanup)

| # | Where | Gap |
|---|-------|-----|
| 11 | Light-theme correctness | Hardcoded dark values: `KbMidnightBase` bg (`NotificationsScreen.kt:56`), filter containers `Color(0xFF0E0822)` (Orders/Reports), revenue gradient end `Color(0xFFEA580C)` (`HomeScreen.kt:302`) |
| 12 | HomeScreen | ~26 raw `fontSize=` instead of `MaterialTheme.typography.*` |
| 13 | NewBillScreen | Stray `RoundedCornerShape(8/12/20.dp)` + one `.height(48.dp)` instead of `KbShape.*` / `KbButtonSize.HeightLarge` |

## Already matches (no work)
Login fields/CTA/Google/SignUp · Orders POS/Online toggle + filter chips + order cards w/ status stripe · Order items + payment-details block · Reports time filters + Order/Payment toggle + Download button · App Lock PIN pad · Profile config groups + Sign Out · Store/Menu config · Help support cards + FAQ + Rate banner.

---

## Plan (phases, priority order)

- **Phase A — Order Detail full screen + Payment Timeline** (#1)
- **Phase B — Dashboard restructure** (#3, #4, #7)
- **Phase C — Reports KPI cells + Amount column** (#2, #9)
- **Phase D — Create Bill order-type + tax breakdown** (#6)
- **Phase E — Smaller deltas** (#5 KYC card, #8 Orders header icons, #10 Login badges)
- **Phase F — Light-theme + token cleanup** (#11–#13); verify both themes on-device

Each phase: `./gradlew :app:compileDebugKotlin` + on-device check in light & dark. Debug app id `com.piquantservices.khanabooklite.debug`; install `adb install -r --user 0 <apk>`.

## Status tracker
- [x] Phase A — Order Detail timeline — added `screens/OrderDetailComponents.kt` (`PaymentTimeline`), wired into `OrderDetailsDialog` (ReportsScreen) + `OrderDetailsPane` (OrdersScreen). Compiles. (Also fixed pre-existing broken working-tree edit in HomeScreen.kt: duplicate closing block + missing `mutableStateListOf`/`BorderStroke` imports.)
- [x] Phase B — Dashboard — Today's Summary now 4 equal tiles (Total Orders/Revenue/Avg Ticket/Cancelled, avg=revenue÷orders) + "View All"→Reports tab (new `onViewReports` callback wired in MainScreen). Quick Actions re-mapped to mockup set (Orders/Order Status/Find Bill · Reprint Bill/Call Customers/Menu); dropped Utilities group. Compiles clean.
- [x] Phase C — Reports KPIs — added `ReportKpiCells`/`KpiCell` (Total Orders/Total Revenue/Avg Ticket/Cancelled) below the header, using the already-computed values. Added visible **Amount** column: new `amount` field on `OrderLevelRow` (populated from `bill.totalAmount` in ReportGenerator), table reordered to Order No·Status·Amount·Date·Action. Compiles.
- [x] Phase D — Create Bill — **order type now persists**: added `_orderType` state + `setOrderType()` to BillingViewModel (reset in `resetForNewBill`, threaded into both bill-creation sites; Walk-in→legacy `"order"`, plus `dinein`/`takeaway`/`delivery`). Wired the previously-unused `OrderTypeButton` into a 4-option selector in `CustomerInfoStep`. Added subtotal + Tax (GST) breakdown rows above the total in `MenuSelectionStep` (new `BillSummaryRow` helper, fed by `billSummary` flow). Compiles.
- [x] Phase E — Small deltas — (5) KYC status card added to Help & Support (`KycStatusCard` in SettingsSupportSections, driven by `SettingsViewModel.profile`; Verified when FSSAI present, else Pending — *assumption: no local Easebuzz KYC-verified flag exists, derived from profile completeness*). (8) Orders header: added top-right Search + Filter icon buttons to **both** wide & narrow headers (`showSearch`/`showFilters` state); search field now icon-revealed (hidden by default), time-filter strip toggled by filter icon (default visible). (10) Login: 3 feature badges (Secure Data/Works Offline/Auto Backup, `LoginFeatureBadge`) under subtitle + divider text "or continue with"→"Quick Login". Compiles.
- [x] Phase F — Theme/token cleanup — **investigation revised the audit**: most flagged "light-theme bugs" turned out to be non-bugs. `KbMidnightBase` is already theme-aware (light cream `0xFFFAF8F5` in light mode), and `kbHeaderGradient` (=`KbMidnightGradient`) is dark-violet in **both** themes — so the `Color(0xFF0E0822)` filter-track chips sit on an always-dark header and render correctly in light mode too (left as-is; no exact token exists for a translucent on-header track). Safe changes applied: (11) `NotificationsScreen` bg `KbMidnightBase`→`MaterialTheme.kbBgPrimary` (more idiomatic semantic token). (13) NewBillScreen two `.height(48.dp)`→`KbButtonSize.HeightLarge` (exact match). **Deferred** (would change appearance, no exact token; belongs to the separate UI-cleanup project): NewBillScreen `RoundedCornerShape(8/12/20.dp)` (KbShape steps are 6/10/14/18 — no exact match) and (12) HomeScreen raw `fontSize=` overrides (remapping to typography.* would shift sizes; many are intentional `.copy(fontSize=)` overrides). Compiles.

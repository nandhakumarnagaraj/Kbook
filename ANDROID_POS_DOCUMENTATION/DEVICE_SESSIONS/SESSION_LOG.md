# Device Testing Session Log

## Session 011: Orders Screen — One Table Only, Preset Chips Show Their Resolved Date
- **Session ID:** SES-20261114-011
- **Date & Time:** 2026-11-14 (source work, then APK install at 16:10:35 and UI checks 16:1x)
- **Target Device / Architecture:** Physical `ZA222J5KT7` — Motorola moto g34 5G, Android 15 / API 35, debug build `com.piquantservices.khanabooklite.debug`. The OnePlus Nord (`Nord_IND`) was left on its older APK, as recorded in `NEXT_ACTIONS.md` item 5.
- **Objective:** Product request — the Orders screen must contain exactly `Today` / `Yesterday` / `Custom` and a single table; the selected chip must state the day it resolves to, the way `Custom` already swapped its label for the picked range.

### Operations Performed
1. Read `NEXT_ACTIONS.md`, `OPERATIONS/ORDER_HISTORY_OPERATIONS.md` and the head of this log before editing, per the AGENTS.md policy.
2. Read `OrdersScreen.kt`, `ReportViews.kt` (`OrderDateFilterComponent`) and `ReportsViewModel.kt` (`updateDateRangeAndLoad`, `setCustomDateRange`, `formatCustomRange`).
3. Source changes:
   - `feature/reports/ui/OrdersScreen.kt`
     - Removed the `reportType == "Payment"` branch, the `PaymentLevelView` import, and the `reportType` / `paymentBreakdown` collections. `reportType` is only ever `"Order"` (`setReportType` has no caller), so `PaymentLevelView` could not have been reached from this screen — but the branch was the only thing that could have replaced the table, and it is now gone. The screen renders the skeleton on first load and `OrderHistoryTableComponent` otherwise.
   - `feature/reports/viewmodel/ReportsViewModel.kt`
     - New `dateFilterLabels: StateFlow<Map<String, String>>` (`Today`/`Yesterday` → `d MMM`).
     - `refreshDateFilterLabels()` runs at the start of `updateDateRangeAndLoad`, so the labels come from the same `BusinessDate.businessDate(now, currentProfile)` the queries use and refresh on filter change, on resume (`refreshCurrentFilter`) and when the profile re-emits (closing-time edit).
   - `feature/reports/ui/ReportViews.kt`
     - `OrderDateFilterComponent` / `OrderDateFilterTabs` accept `dateLabels: Map<String, String>`. The selected chip substitutes its resolved label; `Custom` still substitutes `customRangeLabel` only after a range is picked. Unselected chips keep their names.
4. `./gradlew :app:compileDebugKotlin --offline` → **BUILD SUCCESSFUL**, exit 0 (checked with `PIPESTATUS`, not the pipe's status).

### Verification Status
- **VERIFIED by build:** compile exits 0 with no suppression or workaround.
- **VERIFIED by source:** the Orders screen has one content branch (table); labels derive from `BusinessDate.businessDate`; labels are substituted only for the selected chip.
- **VERIFIED on device (16:1x):** see the addendum below — chips `14 Nov` / `13 Nov`, table-only content, and the Custom picker opening as before.
- **NOT VERIFIED:** the Custom chip's post-Apply range was not re-picked on device this session (behaviour unchanged from Session 010, source-verified). Label ellipsis at the largest type scale was not exercised.
- **UNCHANGED ON PURPOSE:** the `canViewFullReports` gate — roles without `reports.full` still see only the `Today` chip. `PaymentLevelView`, `_reportType` and `setReportType` remain in the code base (the exporter still branches on `reportType`); nothing in the UI reaches them.

### Behaviour detail worth knowing
The chip shows the **business** date, not the calendar date, because that is the day the query covers. With a `12:30 AM` boundary, the chip can read `13 Nov` while the clock says `14 Nov 00:40`; that is correct and intentional.

### Addendum 16:10 — Dead payment report path deleted, APK installed, chips verified

Follow-up request: delete the unreachable `PaymentLevelView` and the unused `reportType` plumbing; then update the APK on the connected device.

Source changes:
- `feature/reports/ui/ReportViews.kt` — deleted `PaymentLevelView` (the only caller was the branch removed earlier today), plus its now-orphaned `PaymentModeItem`, `PartPaymentCard` and the unused `ReportTypeToggle`. Removed the imports that only those composables used (`SettingsViewModel`, `CreditCard`, `PointOfSale`, `QrCode2`, `Payments`, `CallSplit`, `lazy.items`).
- `feature/reports/viewmodel/ReportsViewModel.kt` — deleted `_reportType` / `reportType` / `setReportType`; `exportReport` no longer reads a report type or guards the "Payment" empty case; `buildShareText` lists order rows unconditionally.
- `feature/reports/domain/ReportExporter.kt` — removed the `reportType` parameter from `exportToPdf` and `exportToCsv`; both now always title the document `Orders Report` and the CSV always writes the `ORDER DETAILS` block. `paymentBreakdown` was **kept**: it feeds the live payment summary in both exports and is not part of the report-type plumbing.

Build and install:
- `./gradlew :app:compileDebugKotlin --offline` → exit 0; `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL (46s), exit 0.
- APK 42,754,743 B, SHA-256 `3a7c98f5ee947bffdbbf9759b8a3b49e52ff7b5d220221fa0e9f6e1d313abc6d`.
- `adb -s ZA222J5KT7 install -r` → `Success`; `lastUpdateTime` 2026-11-14 **16:10:35**, `versionCode=38`, `versionName=1.0.38`.

Live UI verification (uiautomator dumps):
- `Today` (default): chips `14 Nov` (selected) / `Yesterday` / `Custom`; header `OrderNo · Amount · Time · Action`; rows `#A02 ₹367.50 03:32 PM`, `#A01 ₹367.50 12:33 PM`. No payment-mode cards anywhere on the screen.
- Tap `Yesterday` (`[302,245][419,275]`): chips `Today` / `13 Nov` (selected) / `Custom`; empty state `No orders in this period` — the selected chip carries the date and the unselected one returns to the word `Today`.
- Tap `Custom`: the range picker opens (`November 2026`, `14 Nov – 14 Nov`, `1 day`); the pre-pick `Custom` chip behaviour is unchanged. Dismissed with Back and returned to `Today` (`14 Nov` selected).

Not verified: the post-`Apply` Custom range on device (unchanged code path from Session 010) and label ellipsis at the largest type scale.

### Addendum 16:2x — Table header/row alignment repaired, `#` restored

Product direction this session: the `Today` / `Yesterday` / `Custom` chips are **frozen** — approved as
shipped, do not edit them again. Work moved to the table header and data rows.

Defects found by comparing the Session 010 screenshots against `ReportViews.kt`:
1. **Header and rows were no longer aligned.** `OrderTableHeaderCell("OrderNo", COL_ORDER)` is a
   weighted cell, but the row's order-number `Text` had no weight at all. After the details line
   was removed, that cell shrank to the width of the order id, so `Amount`, `Time` and `Action`
   each slid left and stopped sitting under their centred headers (on the 12–14 Nov screenshot
   `Amount` centred near x≈194 against a header centred near x≈305).
2. **Order number lost its `#`.** The table printed `A01`, while `OrderDetailsDialog`,
   `SearchScreen`, `ReportsViewModel.buildShareText` and the earlier screenshot all print `#A01`.
   **Reversed the same day at the product owner's request** — see the 16:3x note below.

Fix (`feature/reports/ui/ReportViews.kt`, `OrderTableRow` only): the order-number cell is now
`weight(COL_ORDER)` with `padding(start = spacing.extraSmall)` — the same weight and start inset
`OrderTableHeader` uses. The chips, the header band and every other row style are untouched.

Build + device:
- `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL (21s), exit 0. APK SHA-256
  `7cae5f3e3201c7bf9666a3fa8195ed983ecf45c30fb6d23770ea31582538d93e`.
- `adb -s ZA222J5KT7 install -r` → `Success`.
- uiautomator dump on the installed build shows the header and row cells sharing identical bounds:
  `OrderNo [49,336][225,362]` vs the order cell `[49,411][225,436]`; `Amount [225,336][392,362]` vs
  `₹367.50 [225,411][392,436]`; `Time` starts at `392`, `Action` at `559`. Before the fix the row's
  amount cell began near `x148`, i.e. outside its header.

### Addendum 17:0x — Dead-code audit of every Android screen (read-only)

Method: every `fun`/`class` declaration in `app/src/main/java/**/ui/**` was extracted and each
name counted across **all** main + test sources with word boundaries; a symbol with a single
occurrence (its own declaration) was then re-checked with `grep -rnw`. Counts below are from that
grep. Nothing was deleted — decisions are in `NEXT_ACTIONS.md` items Q and R.

Verified dead (declaration only, no caller anywhere including tests):

| Screen / owner file | Dead symbol |
| :--- | :--- |
| ~~`auth/ui/AppLockScreen.kt`~~ | ~~`onAuthenticationSucceeded`, `onAuthenticationError`, `onAuthenticationFailed`~~ — **KEPT, false positive (corrected 17:2x):** these are `BiometricPrompt.AuthenticationCallback` overrides invoked by the framework, not dead code. Deleting them would have broken biometric unlock. |
| `auth/ui/ForgotPasswordDialog.kt` | `loginTextFieldColors` |
| `billing/ui/ActiveOrderComponents.kt` | `BillInfoHeader`, `ActiveOrderItemCard`, `CompactMenuSection` |
| `billing/ui/MenuSelectionStep.kt` | `FoodTypeIcon` |
| `billing/ui/OrderTableComponents.kt` | `TableHeader` |
| `billing/ui/PaymentSection.kt` | `BillSummaryDisplay`, `PaymentModeSelector`, `loadShopLogoBlockingSection` |
| `payments/ui/OnlinePaymentsSetupScreen.kt` | `NotStartedContent` |
| `reports/ui/OrderDetailsDialog.kt` | `DetailStatusRow`, local `formatDate` |
| `reports/ui/ReportViews.kt` | `OrderDateFilterTabs`, `OrderRowItem`, `OrderHistoryTable`, `OrderLevelView`, `ReportDownloadBottomBar` |
| `settings/ui/SettingsSupportSections.kt` | `AppInfoSection` |
| `settings/ui/ShopConfigComponents.kt` | `CustomerNumberCollectorSelector` |
| `core/designsystem/KhanaInvoiceShareSheet.kt` | whole `KhanaInvoiceShareSheet` |
| `core/designsystem/SignaturePad.kt` | whole `SignaturePad` |
| `core/util/Utils.kt` | `directPrint` |
| `core/theme/PaymentColors.kt` | `payModeColor`, `getPayModeIconTint` |
| `reports/viewmodel/ReportsViewModel.kt` | `exportReport`, `buildShareText` (no UI caller) |

Screens with no dead declarations found: `LoginScreen`, `SignUpScreen`, `RoleAccessScreen`,
`ActiveOrdersScreen`, `ActiveOrderDetailScreen` (its dead pieces live in `ActiveOrderComponents.kt`),
`CallCustomerScreen`, `NewBillScreen`, `SearchScreen`, `MenuConfigurationScreen`,
`OcrScannerScreen`, `ReviewDetectedItemsScreen`, `NotificationsScreen`,
`NotificationPreferencesScreen`, `ComplianceDocumentsScreen`, `ReprintKotScreen`, `HomeScreen`,
`QuickStartScreen`, `SettingsScreen` (its dead pieces live in the two other settings files),
`DeviceSetupScreen`, `MainScreen`.

False positives ruled out by grep (each has a real caller): `DisplayScaleView`,
`ProfileCard`, `LogoutSection`, `PaymentFlowToggleSwitch`, `businessDayRolloverLabel`,
`formatClockTime`, `QuantitySelector`, `DraftItemRow`, `DeviceRow`, `EasebuzzOnboardingHub`,
`launchGoogleSignIn`, `KhanaTimePickerDialog`, and the sync-repair `SettingsViewModel` functions
(`retryFailedBillSync`, `repairFailedBillSync`, `retryAllFailedBillSyncs`, `autoResolveAndSyncAll`,
`repairOrderIdCounters`, `cancelDuplicateConflictBill`, `dismissQuarantinedSyncRecord`).

Functionality that exists but is not reachable from the UI (separate from per-symbol dead code):
1. **Report export** — `ReportsViewModel.exportReport` (PDF/CSV) and `buildShareText` have no
   caller; `ReportDownloadBottomBar`, the only button that would trigger them, is dead. The export
   stack in `ReportExporter` is still reachable only through those two functions.
2. **Orders row actions** — `onStatusChange` / `onPayModeChange` (incl. split payment) /
   `onRequestCancel` are threaded from `OrdersScreen` through `OrderHistoryTableComponent` into
   `OrderTableRow` and never invoked. Tracked as item K.
3. **Order status row** — `DetailStatusRow` in the details dialog is not rendered.
4. **Active-order pieces** — `BillInfoHeader`, `ActiveOrderItemCard`, `CompactMenuSection` are not
   rendered by `ActiveOrderDetailScreen`.
5. **Settings sections** — `AppInfoSection` (about/app info) and `CustomerNumberCollectorSelector`
   are not placed on any settings screen.
6. **Orders date column** — `includeDate` is hard-wired `false`, so the date half of the time
   column is never shown (product decision 2026-11-14).
7. **Role gate** — `Today`-only chips for roles without `reports.full` (intentional permission
   gate, not dead code).

### Addendum 17:2x — Dead-code removals executed (owner approved all groups)

Owner instruction: "do all things (and cross check it currently features with works should be fine)".

Deleted files (all were declaration-only):
- `feature/reports/domain/ReportExporter.kt` — the entire PDF/CSV exporter, reachable only from the
  now-deleted `exportReport`. Deleting the exporter means the app has no report-export capability.
- `feature/billing/ui/ActiveOrderComponents.kt` — `BillInfoHeader`, `ActiveOrderItemCard`,
  `CompactMenuSection` and their private `menuTextFieldColorsActive` helper.
- `feature/billing/ui/PaymentSection.kt` — `BillSummaryDisplay`, `SummaryRow`, `PaymentModeSelector`,
  `loadShopLogoBlockingSection` (none had a caller; `SummaryRow` was used only by `BillSummaryDisplay`).
- `core/designsystem/KhanaInvoiceShareSheet.kt`, `core/designsystem/SignaturePad.kt`.

Deleted declarations in surviving files:
- `ReportViews.kt`: `OrderDateFilterTabs`, `ReportDownloadBottomBar`, `OrderRowItem`,
  `OrderHistoryTable`, `OrderLevelView`, plus the dead `enabledModes` / `onStatusChange` /
  `onPayModeChange` / `onRequestCancel` parameters on `OrderTableRow` and
  `OrderHistoryTableComponent`. `OrderDateFilterComponent` (the frozen chips) was not touched.
- `OrdersScreen.kt`: the three table callbacks and the `OrderLevelView` import. Order edits still run
  from `OrderDetailsDialog` (print / share / change payment mode / cancel all verified live).
- `ReportsViewModel.kt`: `exportReport`, `buildShareText`, `updateOrderStatus` (its only caller was
  the deleted table callback).
- `OrderDetailsDialog.kt`: `DetailStatusRow`, `formatDate`.
- `SettingsSupportSections.kt`: `AppInfoSection`. `ShopConfigComponents.kt`:
  `CustomerNumberCollectorSelector`.
- `OrderTableComponents.kt`: `TableHeader`. `MenuSelectionStep.kt`: `FoodTypeIcon`.
- `OnlinePaymentsSetupScreen.kt`: `NotStartedContent`. `ForgotPasswordDialog.kt`:
  `loginTextFieldColors`.
- `Utils.kt`: `directPrint`. `PaymentColors.kt`: `payModeColor`, `getPayModeIconTint`
  (`getPayModeColor` / `getPayModeIcon` stay — both are used).

**Correction (do not treat 17:0x as final):** the three `AppLockScreen` symbols in the 17:0x table are
**not** dead. They are `BiometricPrompt.AuthenticationCallback` overrides called by the framework;
the grep count of 1 was a false positive. They were left in place deliberately.

Also checked and left alone: `SettingsViewModel` sync-repair functions, `KhanaTimePickerDialog`,
`DisplayScaleView`, `launchGoogleSignIn`, and the `reports.full` role gate — all have real callers or
are intentional permission behaviour.

Verification:
- `:app:compileDebugKotlin --offline` → BUILD SUCCESSFUL (exit 0).
- `:app:testDebugUnitTest --offline` → BUILD SUCCESSFUL (exit 0), full unit suite.
- `:app:assembleDebug --offline` → exit 0. APK SHA-256
  `9423d557e1ca8d13474560074ddfe3284c417b918ef258499e948f47777c894e`.
- Installed on `ZA222J5KT7` (`lastUpdateTime=2026-11-14 17:18:40`, package
  `com.piquantservices.khanabooklite.debug`) and launched: Home renders (stats, Quick/Normal toggle,
  Create Quick Bill, Active Orders, Reprint KOT); Orders renders chips `14 Nov` / `Yesterday` /
  `Custom`, table `OrderNo / Amount / Time / Action` with `A02` struck through (cancelled) and `A01`;
  tapping a row opens `Order Details` with Print Receipt / Share Invoice / Change Payment Mode /
  Cancel Order; Settings renders profile card, all config rows, App Settings and Sign Out with no
  gap where `AppInfoSection` used to be.
- Screenshots: `artifacts/android-ui-tests/deadcode-home-2026-11-14.png`,
  `deadcode-orders-2026-11-14.png`, `deadcode-order-details-2026-11-14.png`,
  `deadcode-settings-2026-11-14.png`, `deadcode-settings-app-2026-11-14.png`.

### Addendum 18:0x — Lint cleanup (owner delegated as product owner)

Owner instruction: act as product owner/delivery and handle the audit findings. Android Lint was the
audit tool (`:app:lintDebug`); `modern-web-guidance` does not apply to a Compose app.

Baseline → result: **12 errors / 605 warnings → 0 errors / 469 warnings**, `lintDebug` passes.

Errors fixed:
1. `NetworkPrinterScanner.kt:237` — `RouteInfo.hasGateway()` is API 29; replaced with
   `it.gateway != null`. On API 26-28 the old call threw inside `runCatching`, so Wi-Fi printer
   gateway/subnet prefill silently returned null.
2. `res/values/styles.xml` — `clipToOutline` (API 31) and five `lineHeight` (API 28) items. The
   whole file was dead Views-era styling: no layout, theme or Kotlin code referenced it, so the file
   was deleted rather than versioned into `values-v28`/`values-v31`.
3. `MenuSelectionStep.kt:338` — `SuspiciousIndentation`; the comment and `val isInitialLoading` were
   dedented out of the enclosing block. Re-indented only, no logic change.
4. `res/values/fonts.xml` — four `<font family=…>` entries directly under `<resources>` are invalid
   and inert; file deleted.

Resource cleanup (lint `UnusedResources`): deleted `res/raw/anim_payment_success.json`, the unused
`drawable/ic_google_logo.xml`, the unused *drawable* copy of `ic_launcher_foreground.xml` (the
launcher uses `@mipmap/ic_launcher_foreground`), `splash_footer.png`, `splash_full_brand.png`,
`splash_logo_with_card.xml`, `splash_logo_with_glow.xml`, plus 18 colors, 18 dimens and 45 strings
with no reference. Side effect: the 12 `PxUsage` warnings disappeared because the px-based dimens
were the culprits.

Other warnings fixed:
- `DefaultLocale` (7): `Locale.US` on `String.format` in `Utils.CurrencyUtils` (2), `SignUpScreen` (1),
  `QuickStartScreen` (2), `ShopConfigSection` (2) so currency/OTP timers format identically in every
  device language.
- `ObsoleteSdkInt` (13): removed API-26 guards and redundant `@RequiresApi(O)` annotations in
  `NotificationHelper`, `NotificationChannels`, and the two always-true `SDK_INT >= O` branches in
  `PrintService` (minSdk is 26).

False start, recorded for honesty: `font_certs.xml` was deleted with the other font files, but it
backs `R.array.com_google_android_gms_fonts_certs` used by the downloadable Poppins setup in
`Type.kt`. The build failed with `Unresolved reference 'array'` and the file was restored from git
before continuing.

Verification:
- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` — all
  BUILD SUCCESSFUL (exit 0); lint reports `0 errors, 469 warnings`.
- APK SHA-256 `9ea11e388320c76cd2d77ec417956c156cdbdb9767eee20963981c477a3f48ad`, installed on
  `ZA222J5KT7`; cold start splash renders the logo, Home renders with summary + bill actions.
- Screenshots: `artifacts/android-ui-tests/lint-cleanup-splash-2026-11-14.png`,
  `lint-cleanup-home-2026-11-14.png`.

Not changed (deliberate): launcher-icon warnings (`IconLauncherShape`, `IconLocation`,
`IconDuplicates`, `MonochromeLauncherIcon`, `mipmap-anydpi-v26` folder), `UseKtx` (76) and
`UseTomlInstead` (14) style churn, and the 27 `Typos` false positives on base64 lines in
`font_certs.xml`. Tracked as items S and T in `NEXT_ACTIONS.md`.

### Addendum 21:0x — Shop Logo header, Order Details redesign, dialog refresh bug

Owner request: add a header for the shop logo under Restaurant Configuration, and review/redesign
the dialogs on the Orders screen (⋮ → Order Details, Change Payment Mode, Cancel Order), including
functionality.

1. **Restaurant Configuration — Shop Logo header** (`ShopConfigSection.kt`): the card now opens with
   `Shop Logo` / `Shown on bills and receipts` above the logo + Change Logo row.
2. **Order Details redesign** (`OrderDetailsDialog.kt`):
   - Order No / Invoice / Type / Date are grouped in one soft `Surface` card instead of four loose
     rows separated by spacers.
   - Print and Share are side by side (`Print` / `Share`), so the dialog is one row shorter and the
     two safe actions no longer compete with Change Payment Mode / Cancel for height.
   - Payment Mode picker now shows each mode's icon.
3. **Shared selection dialog** (`KhanaBookSelectionDialog.kt`): added an optional `leadingIcon` and a
   selected-state check icon. This improves both the payment picker and the Cancel Order reason list
   without forking either dialog.
4. **Split amount dialog** (`PartAmountDialog` in `OrderTableComponents.kt`): gold "Order total"
   strip, a "Both parts must add up to the total." hint, and a filled gold Confirm button instead of
   a text button.

**Functional bug found and fixed:** the Order Details dialog holds a snapshot, not an observed query.
After a successful payment-mode change the dialog kept showing the old mode (the table behind it was
correct), and after a cancel it would keep offering live actions. `ReportsViewModel.refreshOpenBill`
now re-reads the bill after `updatePaymentMode` / `cancelOrder` when that bill is the one open.

Verified on `ZA222J5KT7` (APK SHA-256 `f6c0a4c6fd24c2cf96ac33d4d1f00a27904c7a2f0c1e1b2604f7da879ed5e36b`):
- A01 `Cash + UPI` → Change Payment Mode → `Cash`: dialog updated to `Cash` immediately, no reopen.
- Back to `Cash + UPI`: split dialog showed `Order total ₹367.50`, pre-filled 183.75 / 183.75; Confirm
  restored the bill and the dialog updated to `Cash + UPI` on the spot. Data returned to its
  original state.
- Cancel Order opened the reason list (check on `Customer left`, `Keep Order` / `Cancel Order`) and
  was dismissed with **Keep Order** — no order was cancelled, so the device data is unchanged.
- `:app:compileDebugKotlin`, `:app:assembleDebug` and `:app:testDebugUnitTest` exit 0.

Screenshots: `shop-logo-header-2026-11-14.png`, `order-details-redesign-2026-11-14.png`,
`payment-mode-picker-2026-11-14.png`, `part-amount-dialog-new-2026-11-14.png`,
`payment-mode-refresh-2026-11-14.png`, `payment-mode-refreshed-split-2026-11-14.png`,
`cancel-order-dialog-2026-11-14.png` (all under `artifacts/android-ui-tests/`).

Note on testing: early taps in this session changed A01's mode to `Cash + POS`/`UPI` before the
refresh fix existed. The bill was restored to its original `Cash + UPI` split and the final state was
confirmed by reopening the dialog.

### Addendum 20:0x — Orders table height + Restaurant Configuration header removal

Owner feedback: the Orders table "was not look good" and the Restaurant Configuration screen
should drop the "Restaurant Details" / "Configure your restaurant information" header.

Orders table (`ReportViews.kt`):
- Root cause of the visual problem: the panel was `fillMaxSize()`, so with two orders it drew a
  full-height card with a large empty area under the rows.
- The panel is now inside `BoxWithConstraints` with `heightIn(max = maxHeight)` and the list uses
  `weight(1f, fill = false)`: it hugs the rows when few and still fills/scrolls when many. Empty
  results keep a fixed 180 dp empty state.
- Header label changed from `OrderNo` to `Order No`. Columns, weights, row actions and the frozen
  `Today` / `Yesterday` / `Custom` chips are unchanged.
- Verified on `ZA222J5KT7`: panel now ends immediately below `A01`; `A02` still shows the cancelled
  red edge and struck-through amount.

Restaurant Configuration (`ShopConfigSection.kt`):
- Removed the in-card header row (storefront icon + "Restaurant Details" + "Configure your
  restaurant information"). The card now starts with the logo / Change Logo row, then the fields.
  The screen title `Restaurant Configuration` in the app bar is unchanged.
- Verified on `ZA222J5KT7`: card top is the logo row, all fields and the Business Hours / Billing
  Mode cards below are unchanged.

Verification:
- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:testDebugUnitTest` all exit 0.
- APK SHA-256 `0ab8c374a5fe333d5463483372fa85b68742d1f76667c4b08eb8c333039bc4b9`, installed on
  `ZA222J5KT7` (`lastUpdateTime` 2026-11-14 18:36:13).
- Screenshots: `artifacts/android-ui-tests/orders-table-fixed-2026-11-14.png`,
  `artifacts/android-ui-tests/restaurant-config-no-header-2026-11-14.png`.

### Addendum 19:0x — Launcher icon modernization

Owner request: merge the adaptive icon out of `mipmap-anydpi-v26`, add a monochrome (Android 13
themed-icon) layer, verify on the device.

What changed:
- Adaptive icon XML moved from `res/mipmap-anydpi-v26/` to `res/mipmap/` (folder deleted). The first
  attempt used `res/mipmap-anydpi/`, but AAPT then reported `resource mipmap/ic_launcher not found`;
  lint's own suggestion is "merge all the resources in this folder into mipmap", which is what was
  done, and the build passes.
- Added `<monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>` to both
  `mipmap/ic_launcher.xml` and `mipmap/ic_launcher_round.xml`.
- Generated `ic_launcher_monochrome.png` for mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi (108/162/216/324/432 px)
  from the existing foreground artwork: pixels whose luminance is near white become transparent and
  everything else becomes solid black, so the logo's white paper and the receipt's inner lines read
  as negative space. This is an alpha-mask silhouette, which is what the system tints for themed
  icons.
- Deleted the legacy square `ic_launcher.png` / `ic_launcher_round.png` from all five density folders
  (minSdk 26 always resolves the adaptive XML; these were the `IconLauncherShape` warnings).
- Deduplicated the logo bitmaps: `HelpSupportView` now uses `R.drawable.khanabook_logo` and
  `about_app_logo.png` was deleted; `khanabook_logo.png` moved to `res/drawable-nodpi/` so lint stops
  flagging a densityless bitmap (and the splash `layer-list` reference still resolves).

Verification:
- `:app:assembleDebug` exit 0; APK contains `res/mipmap/ic_launcher.xml`, `ic_launcher_round.xml` and
  all five `mipmap-*-v4/ic_launcher_monochrome.png` entries (checked with `unzip -l`).
- `:app:lintDebug` 0 errors, and the icon warnings are gone: `MonochromeLauncherIcon`,
  `ObsoleteSdkInt` (folder), `IconLauncherShape`, `IconLocation`, `IconDuplicates` all at 0.
  Warnings 469 → 458.
- `:app:testDebugUnitTest` exit 0.
- APK SHA-256 `7c8ac92e4c459232177746b1521aa0f495260323b6f091089122c6238aac21c5`, installed on
  `ZA222J5KT7`; app drawer shows both KhanaBook entries with the logo masked to the launcher's circle,
  and the app opens to Home normally. Screenshots:
  `artifacts/android-ui-tests/launcher-icon-final-2026-11-14.png`,
  `launcher-icon-home-final-2026-11-14.png`.

Not verified: the themed-icon *rendering* itself. The device launcher does not expose a themed-icon
toggle in this session, so the monochrome layer is verified by build, lint, APK contents and the
rendered silhouette — not by an actual themed-icon screenshot.

### Addendum 16:4x — Orders table rebuilt

Product direction: rebuild the table completely and make it good, **within what it already has** —
the same four columns (`OrderNo`, `Amount`, `Time`, `Action`), the same data, the frozen chip row.
No column, field or feature was added.

Structure (`OrderTableHeaderCell` / `OrderTableHeader` / `OrderTableRow` / `ViewOrderAction` /
`OrderHistoryTableComponent`):
- **One cell primitive** — `private RowScope.OrderTableCell(weight, align)` — is now the only way a
  column is built. Header and rows both go through it, and the container padding is identical
  (`spacing.smallMedium`), so column alignment is a property of the weights rather than of each
  cell's own padding. `OrderTableHeaderCell` (public) was replaced by this private helper.
- **Responsive type tokens** — header uses `layout.tableHeaderFontSize`, rows use
  `layout.tableCellFontSize` (the token existed but was unused), both with tabular numerals.
- **Row density** — `heightIn(min = 52dp)` compact, `60dp` on the wide layout. The row has no
  vertical padding: the 48dp action target already sets the content height, and adding padding on
  top first produced ~65dp rows, which was measured and corrected.
- **Action target** — `ViewOrderAction` is a 36dp gold disc inside a 48dp hit area (measured
  `[573,398][657,482]` = 84×84 px = 48×48 dp). Its description now says `View order A02` instead
  of `More options`, because it opens the details dialog like the row tap; no menu was added.
- **Stable keys** — `itemsIndexed(rows, key = { _, row -> row.billId })`, so a refresh updates rows
  in place instead of rebuilding the list. Empty state, hairlines, cancelled-row treatment and the
  `includeDate` two-line time form are unchanged in behaviour.

Build + device (APK SHA-256 `f153eedbcdb914b73029325e8d74eb19a998c5876f680f5d25cabd3d8b1ac903`):
`:app:assembleDebug --offline` exit 0, `adb install -r` → `Success`.
uiautomator on the installed build: `OrderNo [49,343][145,369]` over `A02 [49,425][97,450]`
(shared left edge 49); `Amount [264,343][356,369]` centre 310 over `₹367.50 [259,425][360,450]`
centre 309.5; `Time [446,343][500,369]` centre 473 over `03:32 PM [419,425][526,450]` centre 472.5.
Row pitch 93 px = 53 dp; row hit area `[28,392][692,483]` (52 dp tall).

Not verified: the wide/tablet layout (60 dp row) and a cancelled row on device; the Custom range
path was not re-picked. Chips untouched.

**16:3x — `#` removed again at the product owner's request.** The table prints the bare order id
(`A02`), and only this table does; the details dialog, search and share text keep their `#`. The
weight/inset alignment fix is untouched, re-verified on the reinstalled build (APK SHA-256
`7bdb313048216fcbb6ac1e5e1a23a2c070789fa171b75f3df01f6889779e5342`): `OrderNo [49,336][225,362]`
over `A02 [49,411][225,436]`, `Amount [225,336][392,362]` over `₹367.50 [225,411][392,436]`.

Not verified: tablet / wide layout (`compactLayout = false`) was not exercised; only the phone
layout was dumped.

Open, deliberately not changed: the row action is a `MoreVert` (⋮) button whose description says
"More options" but which opens `OrderDetailsDialog` exactly like the row tap. Whether it becomes a
real menu or a "view" affordance is the `NEXT_ACTIONS.md` item K decision; no second path was added.

## Session 010: Orders Screen — Business-Window Caption, Date/Time Column, Custom Range Chip
- **Session ID:** SES-20261114-010
- **Date & Time:** 2026-11-14 (build 12:1x, install 12:13:05 device wall-clock, UI checks 12:2x)
- **Target Device / Architecture:** Physical `ZA222J5KT7` — Motorola moto g34 5G, Android 15 / API 35, debug build `com.piquantservices.khanabooklite.debug`, launcher `com.khanabook.lite.pos.core.app.MainActivity`. Shop profile under test: closing `00:00`, settlement buffer 30 min → business-day boundary `12:30 AM`.
- **Initial Screen:** Motorola launcher; app forced-stopped and relaunched for the install smoke test.
- **Objective:** Implement the Session 009 suitability gaps H, I and J (make the business-hours window visible, add day context to the table, render row times in the shop timezone) without removing any existing Orders-screen feature, then verify on device.

### Operations Performed
1. Read `README.md`, `NEXT_ACTIONS.md`, `OPERATIONS/ORDER_HISTORY_OPERATIONS.md`, `OPERATIONS/BUSINESS_SETTINGS_OPERATIONS.md`, `02_DEVICE_AND_ENVIRONMENT_SETUP.md` and the head of this log before touching code, per the AGENTS.md policy.
2. Source changes (all additive; no existing element removed):
   - `feature/reports/viewmodel/ReportsViewModel.kt`
     - `publishWindow(from, toInclusive, filter)` — builds the caption from the exact range handed to Room, in `BusinessDate.zoneOf(profile.timezone)`; suppressed when the window starts at calendar midnight and the filter is not `Custom`.
     - `formatCustomRange(fromDate, toDate)` — `d MMM` or `d MMM – d MMM` chip label from the picked dates.
     - New `windowCaption` / `customRangeLabel` StateFlows; caption published from `loadReports` **before** its same-range early return, chip label set in `setCustomDateRange`.
   - `feature/reports/ui/ReportViews.kt`
     - `FilterChip` text is now `maxLines = 1` + ellipsis, so a long Custom label cannot wrap.
     - `OrderDateFilterComponent` / `OrderDateFilterTabs` accept `customRangeLabel` and substitute it for `Custom` **only while Custom is selected**.
     - `formatOrderRowTime(row, profile, includeDate)` renders in the profile zone; two-line `dd MMM⏎hh:mm a` when a date is requested, `hh:mm a` otherwise; legacy `row.date` rows still fall back to `DateUtils.formatTableDate`.
     - `includeDate` threaded through `OrderTableHeader` (`Time` → `Date/Time`), `OrderTableRow`, `OrderRowItem`, `OrderHistoryTableComponent`, `OrderHistoryTable`, `OrderLevelView`. Column weights untouched.
   - `feature/reports/ui/OrdersScreen.kt`
     - Collects `windowCaption` / `customRangeLabel`; caption rendered under the chips (`labelSmall`, `OrdersTextSecondary`, max 2 lines).
     - `includeDate = timeFilter != "Today"`.
     - `canViewFullReports` is now read live instead of `remember { … }`, so a role change widens the filter row without recreating the screen.
3. `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL (25s).
4. `.\gradlew.bat :app:testDebugUnitTest` → BUILD SUCCESSFUL (1m 31s), no failures.
5. `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL (6s). APK `apps/android/app/build/outputs/apk/debug/app-debug.apk`, 42,754,743 B, SHA-256 `56A89A9691A45D3CAC9E8342F5346D4C0E40D32A6CB29829CA2004B04384C94C` (previous session's APK was `85D13E8E…3F62FF`).
6. `adb -s ZA222J5KT7 install -r <apk>` → `Performing Streamed Install` / `Success`. `lastUpdateTime` moved 2026-11-14 11:57:06 → **12:13:05**; `versionCode=38`, `versionName=1.0.38` unchanged (pinned in `local.properties`, so version is not evidence — the timestamp and hash are).
7. Live UI verification on the installed build (uiautomator dumps read with UTF-8 decoding; the `→`, `–` and `₹` glyphs survive in the XML and are verified present, not missing):
   - `Today`: chips `Today` / `Yesterday` / `Custom`; caption `Business day 14 Nov, 12:30 AM → 15 Nov, 12:30 AM`; header `OrderNo · Amount · Time · Action`.
   - `Yesterday`: caption `Business day 13 Nov, 12:30 AM → 14 Nov, 12:30 AM`; header `Date/Time`.
   - `Custom` (picker 12 Nov → 14 Nov, Apply): chip `12 Nov - 14 Nov`; caption `Business day 12 Nov, 12:30 AM → 15 Nov, 12:30 AM`; header `Date/Time`; rows `#A02` = `13 Nov⏎12:15 AM`, `#A01` = `12 Nov⏎10:48 PM`.
   - Boundary sanity: `#A02` at `13 Nov 12:15 AM` (before the 12:30 AM boundary) appears inside the `12 Nov – 14 Nov` range, which is the expected business-day mapping.
8. Docs updated: `OPERATIONS/ORDER_HISTORY_OPERATIONS.md` (change notice + verified 2026-11-14 table), `NEXT_ACTIONS.md` (H/I/J marked DONE, K/L/M still open), this log entry. No earlier entry modified.

### Verification Status
- **VERIFIED on device:** every UI result listed in step 7, on the APK identified in step 5/6.
- **VERIFIED by build:** compile, unit tests and `assembleDebug` all exit 0 with no source/dependency/build-config workaround.
- **VERIFIED by source:** caption suppression rule, profile-zone row times, `includeDate` plumbing, live permission read.
- **NOT VERIFIED:** any shop whose closing time is not `00:00` (only the midnight-closing + 30-minute-buffer case was exercised). `NEXT_ACTIONS.md` item 2 (midnight crossover test) is still pending.
- **NOT VERIFIED:** the row-level modification callbacks — still declared and still never invoked (item K).
- **NOT DONE / NOT AUTHORISED:** nothing was committed or pushed; items K, L and M were not implemented.

### Addendum 12:20 — Custom now re-derives its window from the current profile

**Defect found while answering "is the window dynamic when the closing time changes?"**

`updateDateRangeAndLoad("Custom")` used to return the cached instants (`currentFrom`/`currentTo`).
Because this method also runs every time the saved profile re-emits, editing the closing time
moved `Today`/`Yesterday` but left `Custom` on the **old** boundary — the caption then described
a window the new configuration no longer produced.

Fix: the picked calendar dates are now stored (`customFromDate`/`customToDate`) and the window is
recomputed from `currentProfile` on every call, so `Custom` follows a closing-time edit exactly
like `Today`/`Yesterday`. `setCustomDateRange` now feeds the same single path.

Re-verified on `ZA222J5KT7` after reinstalling (APK SHA-256
`3F44E90028617EA5C508441289D919457835F94B2F9D3F1E6AE45C546F4BD81C`, `lastUpdateTime`
2026-11-14 12:20:55): Custom 12–14 Nov still renders chip `12 Nov - 14 Nov`, caption
`Business day 12 Nov, 12:30 AM → 15 Nov, 12:30 AM`, header `Date/Time`, rows
`13 Nov⏎12:15 AM` and `12 Nov⏎10:48 PM`. Compile and `:app:testDebugUnitTest` exit 0.

**How the window tracks configuration (VERIFIED by source):**
- `ReportsViewModel.init` collects `restaurantRepository.getProfileFlow()`, which is a Room
  `@Query` returning `Flow` (`RestaurantDao.kt:27-28`). Any `restaurant_profile` write re-emits.
- `SettingsViewModel.saveProfile` writes through `RestaurantRepository.saveProfile` →
  `restaurantDao.saveProfile` (Room), so a closing-time edit invalidates the query.
- Each emission calls `updateDateRangeAndLoad(_timeFilter.value)` → window recomputed from the
  new profile → `loadReports` republishes the caption. No restart, no screen re-entry needed.
- The caption is never hardcoded: on this shop (`closingTime = 00:00`) the boundary is
  `12:30 AM`, which is what the device shows, not midnight.

### Addendum 12:27 — self-review pass (observable permission, hoisted formatters)

Second review of the same diff, same day. Build/install identity: SHA-256
`F161B2D988F9CE0BBD8219B8B48D85B20BEB7C892D47BD74B16AB4B7C98DB4E2`, `lastUpdateTime`
2026-11-14 12:27:36.

1. **`canViewFullReports` was not actually live.** The first attempt just called
   `viewModel.canViewFullReports()` from composition, which does not observe anything — the
   answer only changed if some *other* state happened to recompose the screen. Replaced with
   `ReportsViewModel.canViewFullReportsFlow` (`permissionManager.grantedPermissions.map { … }
   .stateIn(..., Eagerly, …)`), and the screen now collects it, so a `reports.full` grant
   arriving from sync widens the filter row.
2. **Per-row formatter allocation removed.** `DateTimeFormatter.ofPattern(...)` was being
   constructed inside `formatOrderRowTime`, i.e. once per visible row on every scroll frame.
   Both patterns are now file-level `val`s (`ORDER_ROW_TIME_FORMAT`,
   `ORDER_ROW_DATE_TIME_FORMAT`); `DateTimeFormatter` is immutable and thread-safe.
3. Compile ✅, `:app:testDebugUnitTest` ✅, installed and smoke-checked: Orders still renders
   chips + caption, no crash entry for `com.piquantservices.khanabooklite.debug` in
   `logcat -b crash` or `dumpsys dropbox`.
4. **Tightened the caption's invariant.** `loadReports` is now `private`: `publishWindow`
   labels the range with `_timeFilter`, so a caller passing an unrelated range would have
   printed a caption for the wrong window. No external caller existed (verified by search
   across `apps/android`), so nothing broke. Also added the call-site note that
   `onStatusChange`/`onPayModeChange`/`onRequestCancel` are still not invoked by any row
   (item K), so the next reader is not misled by the wiring.

Final build identity for this session: SHA-256
`34805D6DC98451017622454FE5137AFE12B2634737473A6CF0E0A9EAA7E28CE9`, `lastUpdateTime`
2026-11-14 12:29:55; `:app:testDebugUnitTest` re-run ✅; Orders screen re-checked on device
(caption `Business day 14 Nov, 1:30 AM → 15 Nov, 1:30 AM`).

**Incidental live proof that the window is config-derived, not cached.** Between the 12:20 and
12:27 installs the shop's closing time changed from `00:00` to `01:00` (pulled by master sync —
"Last sync: 14 Nov, 12:27 pm"; not edited by this session). No code change was involved, and:

| Source | Value |
| :--- | :--- |
| `Restaurant Configuration` → Business Hours subtitle | `Daily Order Number will reset at 1:30 AM` |
| Orders screen caption | `Business day 14 Nov, 1:30 AM → 15 Nov, 1:30 AM` |

Both are derived from `BusinessDate.closingBoundary` = closing + 30-minute buffer, so the caption
followed the configuration with no rebuild and no restart.

### Addendum 12:45 — business-window caption removed (product decision)

The product owner asked for the `Business day … → …` line to be removed **for all filters**,
`Today` included. It is gone; the feature's other half (business-hours-aware queries, `Date/Time`
column, shop-zone row times) is untouched.

Removed from code:

| Symbol | File | Fate |
| :--- | :--- | :--- |
| `_windowCaption`, `windowCaption` | `ReportsViewModel.kt` | deleted |
| `publishWindow(from, toInclusive, filter)` | `ReportsViewModel.kt` | deleted (and its call in `loadReports`) |
| `LocalTime` import | `ReportsViewModel.kt` | deleted (was only used by the midnight check) |
| caption `Text` + wrapping `Column` | `OrdersScreen.kt` | deleted; the filter row is now the whole animated block |
| `TextOverflow` import | `OrdersScreen.kt` | deleted (only the caption used it) |

Kept on purpose: `customRangeLabel` + `formatCustomRange`, so the Custom chip still shows
`12 Nov – 14 Nov`; `includeDate` / the `Date/Time` header; `BusinessDate`-derived query windows.

Verified on `ZA222J5KT7` (build SHA-256
`86F4691BE5432352744DCDD5DA4A52DEC32AE4DFD1989EF05F7F83526B7D5219`, `lastUpdateTime`
2026-11-14 12:43:11), closing `01:00`:

| Screen | Result |
| :--- | :--- |
| `Today` | `OrderNo/Amount/Time/Action` header at `y336` (was `y367` — 31 px reclaimed); row `#A01 / Quick Bill • Dine-in · UPI / ₹367.50 / 12:33 PM` at `y404` |
| `Yesterday` | No caption; header `Date/Time` at `y336`; empty state `No orders in this period` at `y600` |
| `Custom` | Tap still opens the picker (`November 2026`, `14 Nov - 14 Nov`, `1 day`) |
| Crash buffers | No `com.piquantservices.khanabooklite.debug` entry in `logcat -b crash` |

`:app:compileDebugKotlin` ✅ · `:app:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅.

Trade-off accepted, recorded so it stays a decision: an overnight shop's `Today` can contain two
calendar dates and the screen no longer explains why. Filters that span days (`Yesterday`,
`Custom`) still print `dd MMM` per row.

Evidence: `artifacts/android-ui-tests/orders-no-caption-2026-11-14.png`.

### Addendum 13:44 — header and rows became one table panel (design fix)

The owner asked why the header was visually separated from the first data row. There was **no
divider** in the design: the header was a `Surface` and every row its own `KhanaBookCard`, each
with a 1 dp border, and the gap between them (4 dp, `LazyColumn` `contentPadding.top`) was *half*
the gap between rows (8 dp, `verticalArrangement = spacedBy`). Two outlines 4 dp apart read as an
accidental seam.

Structural change (all in `ReportViews.kt`):

| Before | After |
| :--- | :--- |
| `OrderTableHeader` = `Surface` with its own fill, 1 dp border, `KhanaRadii.md` | header is the first **band** of the panel: tinted `OrdersDivider @ 22%` over `OrdersCard`, no outline |
| each row = `KhanaBookCard` + 1 dp border + `KhanaRadii.md` + 8 dp gaps | each row is a plain `Row` inside the panel, separated by a 1 dp `HorizontalDivider` |
| cancelled row = red card border (`DangerRed @ 35%`) | cancelled row = 3 dp `DangerRed` left edge + 5% red wash (the card border no longer exists) |
| `LazyColumn` had `padding(horizontal = 16dp)`, `spacedBy(8dp)`, `contentPadding(4dp, 16dp)` | `LazyColumn` has no padding or spacing; the panel owns the 16 dp side margin and the outline |
| empty state was a block under the header, `vertical = 32dp` | empty state is centred in the panel's remaining height |

One panel now: `Column` → `padding(horizontal = 16dp)` → `clip(KhanaRadii.md)` →
`background(OrdersCard)` → `border(1dp, OrdersDivider @ 70%, KhanaRadii.md)`, containing the
header band, a hairline, then the `LazyColumn` (`weight(1f)`, scrollbar). The header stays outside
the LazyColumn, so it still pins while the list scrolls. Rows gained `heightIn(min = 48.dp)` so
flush rows remain a legal touch target.

Measured on `ZA222J5KT7` (density 280 dpi → **1.75 px/dp**, 720×1600), build SHA-256
`E1664E65085B8CF3FB5D88456A3307D8BB7A364E326EE38EB99EDE027F0322E5`, `lastUpdateTime`
2026-11-14 13:44:12:

| Element | Geometry | Colour sampled |
| :--- | :--- | :--- |
| Panel | `x28..692`, 1 dp outline | border `#3E261F` (`OrdersDivider @ 70%` over `OrdersCard`), fill `#260F10` |
| Header band | `y322..376` (54 px ≈ 31 dp) | `#2E1714` (band tint) |
| Header→row hairline | `y376..377` | `#3E261F` |
| Row | `y378..469` = 91 px ≈ 52 dp; next row at `y471` | row pitch **93 px** (was 107 px with card gaps) |
| Row→row hairline | `y469..470` | `#3E261F` |
| Empty state | `y932..1005` (vertically centred in the panel) | — |

Rows are now ~13 % denser than the card layout while each row is still 52 dp tall.

`:app:compileDebugKotlin` ✅ · `:app:testDebugUnitTest` ✅ · `:app:assembleDebug` ✅; no crash
entries for the package. Evidence: `artifacts/android-ui-tests/orders-table-panel-2026-11-14.png`.

### Evidence
- Screenshots: `artifacts/android-ui-tests/orders-business-window-2026-11-14.png` (Today), `artifacts/android-ui-tests/orders-custom-range-2026-11-14.png` (Custom 12–14 Nov).
- uiautomator dumps captured during the run (TextViews quoted above).

### Known Limitations / Notes
- A second device, OnePlus Nord `AC2001` (`adb` serial `adb-dbd06e58-N8yA4u._adb-tls-connect._tcp`, `transport_id:20`), is now attached wirelessly. Nothing was installed to it in this session; `NEXT_ACTIONS.md` item 5 (OnePlus Nord redeploy) is still open.
- `versionName`/`versionCode` are pinned to `1.0.38`/`38` for every build type, so they cannot prove a deploy; use `lastUpdateTime` + APK SHA-256.

## Session 009: Orders Screen Gap Review — Today / Yesterday / Custom vs Business Hours
- **Session ID:** SES-20261114-009
- **Date & Time:** 2026-11-14
- **Target Device / Architecture:** No device used. Static source inspection of `apps/android/app/src/main/java/com/khanabook/lite/pos/feature/reports/**` and `core/util/BusinessDate.kt`.
- **Initial Screen:** None — no UI operations performed.
- **Objective:** Answer whether the Orders screen's Today/Yesterday/Custom list can be shown business-hours-aware, and what the current design does not cover. Review only; **no source change was authorised or made**.

### Operations Performed
1. Read `README.md`, `NEXT_ACTIONS.md`, `OPERATIONS/ORDER_HISTORY_OPERATIONS.md`, `OPERATIONS/BUSINESS_SETTINGS_OPERATIONS.md` and the head of this log before inspecting code, per the AGENTS.md policy.
2. Read the Orders stack: `OrdersScreen.kt`, `ReportViews.kt`, `ReportsViewModel.kt`, `ReportModels.kt`, `CustomDateRangePickerDialog.kt`, `OrderDetailsDialog.kt`, `core/util/BusinessDate.kt`, `core/util/Utils.kt`.
3. Compared each existing element against the requested behaviour and recorded the gaps below.

### What already exists (verified by source)
- **Filter chips Today / Yesterday / Custom** — `ReportViews.kt:96-123`. Non-`REPORTS_FULL` roles see only `Today` (`:103-107`).
- **Filters are already business-hours-aware.** `ReportsViewModel.updateDateRangeAndLoad` (`:150-178`) maps `Today`/`Yesterday` through `BusinessDate.windowStart/windowEnd`, not calendar midnight, and `setCustomDateRange` (`:188-197`) maps the picked calendar dates through the same windows.
- **Boundary rule** — `BusinessDate.closingBoundary` (`:80-100`) = closing time + `SETTLEMENT_BUFFER_MINUTES` (30, `:42`); overnight/midnight/late-evening closings roll over at that buffered time; `windowStart` (`:193-226`) is its exact inverse; `businessDate` (`:137-172`) is the forward mapping.
- **Rollover on resume** — `OrdersScreen.kt:127-129` calls `refreshCurrentFilter` (`ReportsViewModel.kt:184-186`) so `Today` re-resolves after the boundary.
- **Table** — header `OrderNo | Amount | Time | Action` (`ReportViews.kt:399-421`); row `:453-567`; live Room projection so edits appear without a manual refresh (`ReportsViewModel.kt:207-226`).
- **Search** filters the loaded rows locally (`OrdersScreen.kt:102-120`).
- **Order modifications** — `OrderDetailsDialog` (`OrderDetailsDialog.kt:45-55`) supports **change payment mode**, **cancel**, print and share.

### Gaps against the requested feature
1. **The active business window is never shown.** With a 12:20 AM close, `Today` actually begins at 12:50 AM the previous calendar day; nothing on screen states that, so the chip label misleads a cashier reconciling a late shift.
2. **No Date column.** Rows show only `hh:mm a` (`ReportViews.kt:352-360`), so a multi-day Custom range renders several `#A01` rows with identical-looking times and no day.
3. **Custom shows no chosen range** on the chip or anywhere else.
4. **Row time uses the device zone**, not the profile's business zone (`ReportViews.kt:355`), so a non-matching terminal clock shows the wrong time while the window logic uses the profile zone (`BusinessDate.zoneOf`, `:113-119`).
5. **Row-level modification callbacks are declared but never invoked.** `OrderTableRow` accepts `onStatusChange`, `onPayModeChange`, `onRequestCancel` (`ReportViews.kt:453-462`) and ignores all three — only `onViewDetails` is wired (`:561-563`). Edits therefore exist only inside the details dialog.
6. **Status change is unreachable from this screen.** `ReportsViewModel.updateOrderStatus` (`:255-268`) exists, but `OrderDetailsDialog` has no status action and the row callback that would call it is dead.
7. **Calendar-day presets in the picker** (`CustomDateRangePickerDialog.kt:465-501`) would contradict the business-day rule; they are currently disabled here (`OrdersScreen.kt:212 showQuickPresets = false`), so the trap is latent rather than live.
8. **No period summary** (count / gross / cancelled) on the Order view, although the Payment view has a breakdown (`ReportViews.kt:165-225`).
9. **Permission snapshot is taken once** — `OrdersScreen.kt:65 remember { … }` will not react to a role change without recomposition from scratch.

### Verification Status
- **VERIFIED (by source inspection):** every statement above cites file:line in the working tree at `HEAD=176b2b34`.
- **NOT VERIFIED:** any on-device rendering. No APK was installed and no screen was opened in this session; the Session 008 build is the last artefact on `ZA222J5KT7`.
- **NOT VERIFIED:** whether the 30-minute buffer matches the configured shop; `NEXT_ACTIONS.md` item 2 (midnight crossover test) is still pending.
- **PROPOSAL ONLY:** the suitability changes listed in `NEXT_ACTIONS.md` under the 2026-11-14 heading were not implemented.

### Evidence
- Source references quoted inline above.

### Known Limitations / Notes
- No source, dependency or build-config file was modified; nothing was committed or pushed.

## Session 008: Debug APK Build + Install On ZA222J5KT7
- **Session ID:** SES-20261114-008
- **Date & Time:** 2026-11-14 (build 11:56, install 11:57, screenshot 11:57:42 - all device/local wall-clock as printed by the tools)
- **Target Device / Architecture:** Physical `ZA222J5KT7` - Motorola moto g34 5G (`ro.product.device=fogos`, `product:fogos_g`, `transport_id:12`), Android 15 / API 35. Debug build `com.piquantservices.khanabooklite.debug`.
- **Initial Screen:** Unknown at session start (device was idle on the Motorola launcher, `mCurrentFocus=com.motorola.launcher3/...CustomizationPanelLauncher`). No UI operation was performed before the install.
- **Objective:** Build the current debug APK from source, install/update it on the attached physical device, prove the new build landed, and record the evidence. No source, dependency or build-config change was authorised or made.

### Operations Performed
1. Read `README.md`, `02_DEVICE_AND_ENVIRONMENT_SETUP.md`, `NEXT_ACTIONS.md` and the head of this log before operating, per the AGENTS.md policy.
2. `adb devices -l` confirmed the device still attached: `ZA222J5KT7 device product:fogos_g model:moto_g34_5G device:fogos transport_id:12`. No emulator was started.
3. Recorded the pre-install baseline from `dumpsys package com.piquantservices.khanabooklite.debug`: `versionCode=38`, `versionName=1.0.38`, `lastUpdateTime=2026-11-13 18:15:53`, `firstInstallTime=2026-11-03 01:36:47`.
4. Ran `.\gradlew.bat :app:assembleDebug --console=plain` in `apps/android` with network available for dependency resolution (`--offline` deliberately *not* passed): **BUILD SUCCESSFUL in 2m 41s**, `43 actionable tasks: 6 executed, 37 up-to-date`, **GRADLE_EXIT:0**.
   - Tasks that actually executed: `:app:kspDebugKotlin`, `:app:compileDebugKotlin`, `:app:transformDebugClassesWithAsm`, `:app:dexBuilderDebug`, `:app:mergeProjectDexDebug`, `:app:packageDebug`. The Kotlin compile was not up-to-date, so the APK was genuinely repackaged rather than reused.
5. Artifact: `apps/android/app/build/outputs/apk/debug/app-debug.apk`, **42,754,743 bytes (40.77 MB)**, `LastWriteTime=2026-11-14 11:56:17`, SHA-256 `85D13E8E3D4D6C998FBA71445E311947D90CE9F6D8BC247DB02FD2949E3F62FF`.
6. `adb -s ZA222J5KT7 install -r "…\app-debug.apk"` returned `Performing Streamed Install` then **`Success`**, ADB exit status 0.
7. Post-install `dumpsys package`: `versionCode=38`, `versionName=1.0.38`, **`lastUpdateTime=2026-11-14 11:57:06`** (advanced from `2026-11-13 18:15:53`), `firstInstallTime` unchanged at `2026-11-03 01:36:47` - an in-place update, not a fresh install.
8. Launched the app once and captured one screenshot (see *Documentation Corrections* for the activity path).
9. Read the runtime state back with `dumpsys window`, `uiautomator dump`, `logcat -b crash`, `pidof`.

### Versioning: the versionCode did not change
`apps/android/local.properties` pins `RELEASE_VERSION_CODE=38` / `RELEASE_VERSION_NAME=1.0.38` (and `SIGN_DEBUG_WITH_RELEASE=true`), and `app/build.gradle.kts:44-45,90-91` feeds those into `defaultConfig` for every build type. A rebuilt debug APK therefore keeps **versionCode 38 / versionName 1.0.38**, so a version bump cannot be used to prove this deploy landed. The proof used instead is `lastUpdateTime` (advanced) plus the APK `LastWriteTime` (advanced) and SHA-256 above.

The rebuilt APK is byte-length identical to the 2026-11-13 artifact (42,754,743 B), consistent with no source change under `apps/android` (`git status --porcelain -- apps/android` returned 0 lines at `HEAD=176b2b34`). Whether the previous artifact was byte-identical cannot be checked: its SHA-256 was never recorded and the file has been overwritten.

### Documentation Corrections Found This Session
Both are recorded here as verified corrections, not as edits to historical evidence:
1. **Launcher activity.** `02_DEVICE_AND_ENVIRONMENT_SETUP.md` listed `com.khanabook.lite.pos.MainActivity`. The installed manifest resolves the launcher to **`com.khanabook.lite.pos.core.app.MainActivity`**; `am start` against the documented path failed with `Error type 3 / Activity class {com.piquantservices.khanabooklite.debug/com.khanabook.lite.pos.MainActivity} does not exist`. Confirmed independent of my intent string via `cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.piquantservices.khanabooklite.debug`, which returned `com.piquantservices.khanabooklite.debug/com.khanabook.lite.pos.core.app.MainActivity`.
2. **OS level.** The same document listed "Android 14 (API 34)". The device reports `ro.build.version.release=15`, `ro.build.version.sdk=35` - **Android 15 / API 35**. (`minSdk=26 targetSdk=36` is unchanged and matches the build.)
Both values were corrected in `02_DEVICE_AND_ENVIRONMENT_SETUP.md` with a dated note pointing back to this entry.

### Newly Discovered UI Operations
None. The app was started and its landing screen was captured and identified, but no tap, swipe or text entry was driven, so no `OPERATIONS/` document needs an update from this session.

### Verification Status
- **VERIFIED (by Gradle):** `:app:assembleDebug` succeeds on the current tree; exit status 0; six tasks executed including `kspDebugKotlin`, `compileDebugKotlin` and `packageDebug`.
- **VERIFIED (by adb):** `install -r` returned `Success` for package `com.piquantservices.khanabooklite.debug`.
- **VERIFIED (by dumpsys):** installed `versionCode=38`, `versionName=1.0.38`, `lastUpdateTime=2026-11-14 11:57:06` - later than the pre-install baseline and later than the APK's own mtime, so the on-device copy is the artifact built this session.
- **VERIFIED (by dumpsys window):** the app came to the foreground - `mCurrentFocus=Window{1ba641f u0 com.piquantservices.khanabooklite.debug/com.khanabook.lite.pos.core.app.MainActivity}`.
- **VERIFIED (by process/crash buffers):** the process is alive (`pidof` → `26770`) and `logcat -b crash` contains no entry mentioning `khanabook`.
- **VERIFIED (by uiautomator dump):** the foreground screen is **Restaurant Configuration**; the dump contains `Restaurant Configuration`, `Restaurant Details`, `Configure your restaurant information`, `Change Logo`, `Piquant Services's Restaurant`, `Shop Name *`, `Shop Address *`, `WhatsApp Number`, `nandhakumar@indiaadvocacy.com`, `Email *`, `Review Link`, `Invoice Footer`, `Business Hours`, `Daily Order Number will reset at 12:30 AM`.
- **NOT VERIFIED:** the screenshot's *visual* appearance. The capturing agent cannot read PNG content, so the screen above was identified from the UI-hierarchy text dump only. A human should eyeball `install-smoke-2026-11-14.png` before it is cited as visual evidence.
- **NOT VERIFIED:** any functional behaviour. A clean cold start is not evidence that billing, KOT, sync or payment flows work; nothing was exercised beyond launch.
- **NOT VERIFIED:** `NEXT_ACTIONS.md` item 3 (Restaurant Configuration layout re-verification). Its trigger condition - "after the next APK install" - is now met, but the Business Hours collapse/expand behaviour and the Billing Mode card surface were not checked in this session.

### Evidence
- Gradle console log (full): `C:\Users\nandh\AppData\Local\Temp\opencode\assembleDebug.log`; final lines `BUILD SUCCESSFUL in 2m 41s` / `43 actionable tasks: 6 executed, 37 up-to-date`; wrapper exit status `0`.
- APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk` - 42,754,743 B, 2026-11-14 11:56:17, SHA-256 `85D13E8E3D4D6C998FBA71445E311947D90CE9F6D8BC247DB02FD2949E3F62FF`.
- Install output: `Performing Streamed Install` / `Success`.
- Screenshot: `artifacts/android-ui-tests/install-smoke-2026-11-14.png` (134,296 B, 2026-11-14 11:57:42).
- Pre/post `dumpsys package` version and `lastUpdateTime` lines quoted above.

### Known Limitations / Notes
- OnePlus Nord redeploy (`NEXT_ACTIONS.md` item 5) is unaffected and still pending - no OnePlus device was attached this session.
- No source, dependency or build-config file was modified; `apps/android` was clean at `HEAD=176b2b34` before and after.
- Nothing was committed or pushed.

## Session 007: Static Audit Of POS, Compile Repair, Three Bugs Registered
- **Session ID:** SES-20261113-007
- **Date & Time:** 2026-11-13
- **Target Device / Architecture:** No device connected. Android POS source audit (`ChangePasswordView.kt`, `AuthViewModel.kt`, `QuickStartScreen.kt`, `ShopConfigSection.kt`, `SessionManager.kt`) plus `apps/web-admin` and `services/server` inspection.
- **Initial Screen:** None — no UI operations performed.
- **Objective:** Run the requested codebase/product audit, repair why `main` did not compile, and record findings in this knowledge base.

### Operations Performed
1. Read `README.md`, `NEXT_ACTIONS.md`, `KNOWN_LIMITATIONS.md` and this log before inspecting any code, per the AGENTS.md policy.
2. Pulled `a733025b` (owner email editable via OTP) and ran `./gradlew :app:compileDebugKotlin --offline` in `apps/android`: **FAILED** — `ChangePasswordView.kt:79:36 Expecting '->'`, from commit `8a600882`, not from the pulled commit.
3. Repaired four breakages in that commit (see BUG-007): the malformed `when`, the undeclared OTP resend timer, `completePasswordReset` pasted inside `sealed class ResetPasswordResult` and calling a non-existent repository method, and a call to a `PasswordField` composable that was never written.
4. Re-ran `./gradlew :app:compileDebugKotlin --offline`: **BUILD SUCCESSFUL**.
5. Ran `./gradlew :app:testDebugUnitTest --offline`: **PASS**, Gradle exit status 0 (captured via `PIPESTATUS`, not the pipe's exit status).
6. Audited the three surfaces and wrote 16 documents under `docs/product-audit/` (overview, structure, modules, features, frontend, backend, database, API, security, architecture, performance, UI/UX, testing, debt, roadmap, checklist).
7. Registered BUG-005 (plaintext Google password), BUG-006 (email verification state resets on profile refresh) and BUG-007 (non-compiling commit on `main`) in `BUG_REGISTER.md`.
8. Added the audit-derived actions at the top of `NEXT_ACTIONS.md`; the pre-existing list was left untouched.

### Newly Discovered UI Operations
None. No device or emulator was used, so no new screen interaction was observed and no `OPERATIONS/` document needed an update.

### Verification Status
- **VERIFIED (by compiler):** the Android tree compiles again — `:app:compileDebugKotlin`, exit 0.
- **VERIFIED (by test run):** `:app:testDebugUnitTest`, Gradle exit 0.
- **VERIFIED (by source inspection):** the Quick Start password is written to `SharedPreferences` in plaintext (`SessionManager.kt:20,124-126`), is never sent to the server, and `getPassword()` has no callers.
- **NOT VERIFIED:** any on-device behaviour — Change Password, Quick Start, and the email OTP path were not exercised. No screenshots were captured in this session; there is no image evidence to reference, and none is claimed.
- **ASSUMPTION (unchecked):** that the `CI` workflow which runs `testDebugUnitTest` on every push to `main` actually executed for commit `8a600882`. The commit cannot compile, so the job could not have passed; whether it ran, failed-and-was-ignored, or never started is unconfirmed from this environment.

### Evidence
- Compile failure output: `e: .../ChangePasswordView.kt:79:36 Expecting '->'` followed by `Execution failed for task ':app:kspDebugKotlin'`.
- Post-repair compile: empty output, exit 0.
- Post-repair unit tests: `GRADLE_EXIT:0`.
- Full findings and file:line references: `docs/product-audit/00-AUDIT-OVERVIEW.md`.

### Known Limitations / Notes
- OnePlus Nord still runs a pre-Session-004 build (carried over from Session 006).
- `main` at `a733025b` still does not compile from a clean checkout until the BUG-007 repair is committed.

### Addendum (same session, after product-owner sign-off)

The audit's findings were then acted on. Nothing below was verified on a device.

1. **Quick Start password removed entirely** (BUG-005): the field, the `canSubmit` gate, `SessionManager.savePassword/getPassword` and the `google_password` preference are gone. No plaintext credential is written any more. *Verified:* `:app:compileDebugKotlin` and `:app:testDebugUnitTest` exit 0; `grep -rn "google_password" apps/android` returns nothing.
2. **Server compile break repaired** (BUG-008): the server did not compile at HEAD either — `UserServiceImpl` imported a non-existent `PasswordEncoder`, injected the non-bean `PhoneNormalizer`, and had lost six private helpers plus its OTP service field. Restored and re-implemented; `./mvnw -DskipTests compile` exit 0.
3. **Master-pull transaction poisoning fixed** (BUG-009): `getSubscriptionSummary` lazily inserts a default subscription row while running inside the pull's read-only transaction, aborting it so every later query in the request failed. Moved to `REQUIRES_NEW`. *Verified:* the five previously failing sync/bill integration classes pass.
4. **Dead `completePasswordReset` deleted** from the server: it skipped OTP validation entirely, so exposing it would have been an authentication bypass. `/auth/reset-password` already resolves email logins, so nothing is lost.
5. **`nosniff` re-enabled**; **CI de-duplicated** (`android-ci.yml` keeps lint + instrumented, `web-admin.yml` now enforces all ten `check:*` design gates plus the production build).
6. **`TerminalManagementConcurrencyTest` fixed:** its scenarios now pin the restaurant to a 5-terminal `ENTERPRISE` fixture plan, because `BaseIntegrationTest` uses `ddl-auto=create` and skips Flyway, so no plan rows exist in that context. Green.
7. **Full server suite result:** 955 tests, 2 failures, 0 errors (down from 6 failures + 5 errors). The two remaining are order-dependent 401s in `ConcurrentBillPushTest` and `TerminalAccessControlGapTest`; both pass when run together in isolation, so this is cross-class test interference, not a product defect.

## Session 006: Read-Only Survey Of POS + Web Admin, BUG-004 Fix
- **Session ID:** SES-20261113-006
- **Date & Time:** 2026-11-13
- **Target Device / Architecture:** No device connected. Android POS + `apps/web-admin` + `services/server` source review.
- **Initial Screen:** None — no UI operations performed.
- **Objective:** Survey the current state of both client apps and fix the open findings from the 2026-11-12 sessions.

### Operations Performed
1. Read `01_APPLICATION_OVERVIEW.md`, `CHANGELOG.md`, `KNOWN_LIMITATIONS.md`, `UNVERIFIED_ASSUMPTIONS.md`, `NEXT_ACTIONS.md`, `BUG_REGISTER.md` and this log before inspecting code.
2. Surveyed `apps/android` structure (11 feature modules, 194 feature sources, 97 test files, 22 screens) and `apps/web-admin` (127 TS sources, 46 specs, 27 page areas, 10 `check:*` design scripts).
3. Ran `npm run build:dev` in `apps/web-admin`: **BUILD SUCCESSFUL** (13.9 s, initial bundle 1.64 MB).
4. Fixed BUG-004 in `services/server/.../notifications/service/EmailNotificationService.java` (see `BUG_REGISTER.md`).
5. Added `EmailNotificationServiceTest.java`; `./mvnw -Dtest=EmailNotificationServiceTest test` → **Tests run: 4, Failures: 0, Errors: 0**.
6. Ran all ten `check:*` design scripts in `apps/web-admin` (motion, type, tokens, breakpoints, spacing, z-index, colors, contrast, accent-budget, inline-styles) → all exit 0.
7. Ran the Karma suite `npx ng test --watch=false`. The first run reported **309 SUCCESS / 39 FAILED**; all 39 were repaired (details below) and the final run reported **350 SUCCESS / 0 FAILED** (exit 0).
8. No Android build was run and no APK was installed in this session.

### Web Admin Test Repairs (39 failures → 0)
- **Stale API mocks (38 failures):** `business-dashboard-page.component.spec.ts`, `micro-interactions.spec.ts`, `table-design.spec.ts`, `refresh-reload.spec.ts` and `form-validation.spec.ts` built `AdminApiService` spy mocks that predated two calls the dashboard/businesses pages now make. Added `getProfile` → `of({})` (dashboard) and `getDashboardSummary` → `of({})` (businesses) to those mocks. No assertion was relaxed; the specs still assert the same rendered behaviour, they simply provide the newly required endpoints.
- **Feature-flags spec rewritten:** `feature-flags-page.component.spec.ts` asserted a hardcoded 28-flag catalogue that no longer exists — the page now renders whatever `AdminApiService.getFeatureFlags()` returns. The spec was rewritten against the API-driven behaviour with a `flag()` fixture helper instead of being deleted.
- **Outdated toast expectation:** `form-validation.spec.ts` expected the Businesses header action to answer with a `/prototype sample data shown/` toast. The page now calls `addBusiness()`, which shows `'Restaurant onboarding workflow initiated.'` the moment it starts the real onboarding workflow. The spec was renamed to "the header action starts onboarding rather than opening a create form" and now asserts that exact message, keeping its original guard that no `.modal-box` appears.
- **Dashboard 13px floor:** the dashboard's own legibility spec caught eleven- and twelve-pixel text. `span.dash-eyebrow`, `.printer-station__label`, `.printer-station__sub` and `.printer-tactile-btn` were raised from `--kb-text-2xs` (11px) to `--kb-text-sm` (13px). The simulated 80mm receipt body inside `.receipt-inner` is exempted in the spec with a comment: it depicts thermal print output, so its glyph sizes are part of the drawing rather than screen copy. Nothing else was exempted.

### Verification Status
- **VERIFIED (by unit test):** blank `spring.mail.password` no longer sends and no longer throws; `emailEnabled=false` still short-circuits; a configured host still sends; a configured host whose transport fails still throws `IllegalStateException`.
- **VERIFIED (by compiler):** `apps/web-admin` development build completes.
- **NOT VERIFIED:** end-to-end Email OTP delivery on hardware — requires `MAIL_PASSWORD` on the server the POS talks to.
- **ASSUMPTION (unchecked):** the 2026-11-12 observation was produced by a server build whose `MAIL_PASSWORD` was unset. The log line to confirm is `Email OTP not delivered: spring.mail.password is empty` (new) versus an SMTP `AuthenticationFailedException` (configured but wrong credentials).

### Known Limitations / Notes
- Android release is configured at `versionCode 38 / 1.0.38`, but only `1.0.35` and `1.0.36` AABs are present under `apps/android/`.
- OnePlus Nord still runs a pre-Session-004 build.

## Session 005: Business Day Card Merged Into Business Hours
- **Session ID:** SES-20261112-005
- **Date & Time:** 2026-11-12 (implementation verified by compile only)
- **Target Device / Architecture:** Android POS (`ShopConfigSection.kt`, `ShopConfigComponents.kt`)
- **Initial Screen:** Restaurant Configuration (`SCR-08`)
- **Objective:** Remove the duplicated `Business Day` card; move the daily order-number rollover message into the `Business Hours` card subtitle, computed from the closing time.

### Operations Performed
1. `ShopConfigComponents.kt`: deleted the `BusinessDayInfoCard` composable (and its chevron/expanded drawer) and added `businessDayRolloverLabel(...)`, which mirrors `BusinessDate.businessDate()` (activated cutoff → closing boundary with settlement buffer → calendar midnight) and returns a formatted `h:mm AM/PM` label.
2. `ShopConfigSection.kt`: removed the standalone `Business Day` card render block; the `Business Hours` subtitle now renders `"Daily Order Number will reset at " + businessDayRolloverLabel(...)`. Opening and Closing time pickers are untouched.
3. Compiled via `./gradlew compileDebugKotlin`: **BUILD SUCCESSFUL**.
4. Assembled debug APK via `./gradlew assembleDebug`: **BUILD SUCCESSFUL** (`app-debug.apk`, 42.75 MB, written 18:32:43 IST).
5. Installed onto **Motorola moto g34 5G (`ZA222J5KT7`)** via `adb -s ZA222J5KT7 install -r` → `Success`; device `lastUpdateTime=2026-11-12 18:32:56`. Relaunched `MainActivity`, running with PID 31883.

### Follow-up Change: Billing Mode Surface Aligned (2026-11-12 18:55 IST)
- Developer observation: the **Billing Mode** card's background differed from the other cards.
- Confirmed in code: `RestaurantPaymentFlowSelector` was not wrapped in a `Card` and painted `DarkBrown2.copy(alpha = 0.45f)` (dark maroon) with a 45% gold border, while Restaurant Details / Business Hours use `CardBG` (0xCC78461E) with 25% `BorderGold`.
- Fixed in `ShopConfigComponents.kt`: `CardBG` fill, `BorderGold` 25%, `KhanaRadii.lg`, `spacing.large` padding, 28 dp gold icon, bold `titleMedium` title and a `bodySmall` subtitle for parity with the sibling cards.
- Also fixed the Business Hours subtitle wrapping mid-time by using a non-breaking space in the rollover label.
- **VERIFIED on device:** `artifacts/android-ui-tests/cards_aligned.png`.

### Follow-up Change: Collapsible Business Hours (2026-11-12 18:43-18:44 IST)
- Requested by the developer: hide Opening/Closing time by default and keep a dropdown under the header that reveals them on tap.
- Implemented in `ShopConfigSection.kt`: `isBusinessHoursExpanded` state (default `false`), clickable header row with chevron, and `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically + fadeOut)` around the two `KhanaTimePickerField`s.
- **VERIFIED on device `ZA222J5KT7`:** collapsed state (`artifacts/android-ui-tests/after_restart.png`) shows only icon + title + subtitle + chevron; tapping the header expands and reveals `Opening Time 06:00 AM` and `Closing Time 12:00 AM` with the chevron flipped up (`artifacts/android-ui-tests/business_hours_expanded.png`).
- **VERIFIED live recalculation:** tapping Closing Time opened the `KhanaTimePickerDialog` (`closing_time_picker.png`, `picker_minutes.png`); selecting `11:30 AM` + OK changed the field to `11:30 AM` and the subtitle immediately became **"Daily Order Number will reset at 12:00 AM"** (`closing_time_changed.png`). For a daytime service (closing after opening, buffer does not cross midnight) this is the correct calendar-midnight rule from `BusinessDate.closingBoundary`, i.e. the label matches the numbering rule rather than the closing time.
- Note: the test changed only in-memory state — nothing was saved, so the shop's stored closing time is unchanged.
- Note: the expanded/collapsed flag is a `remember` in the screen scope, so it returns to collapsed when the screen is recreated or the composable leaves composition.
- Compiled `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL; `assembleDebug` + `adb install -r` → Success.

### Verification Status
- **VERIFIED (on device, screenshot `artifacts/android-ui-tests/business_hours_screen.png`, 2026-11-12 18:36 IST):**
  - The standalone `Business Day` card is gone.
  - `Business Hours` renders: clock icon, title, subtitle **"Daily Order Number will reset at 12:30 AM"** with Opening Time `06:00 AM` and Closing Time `12:00 AM` side by side. The 12:30 AM label is the correct closing `12:00 AM` + 30-minute settlement buffer.
  - No dropdown/chevron affordance: each time field is a read-only `ParchmentTextField` with a trailing clock icon, and tapping anywhere in the field opens the `KhanaTimePickerDialog` (Material clock dial + AM/PM), not a dropdown list. Reported by the developer as "that dropdown is missing".
  - **Open request:** whether these fields should become a real dropdown (exposed menu of times) or keep the clock-dial dialog with an added chevron affordance.
- **BUG OBSERVED DURING VERIFICATION (not fixed):** tapping `Get OTP` on the Email field produced the inline error **"Failed to send verification email. Please try again."** while the typed address was still being edited, i.e. the request failed at dispatch time rather than as a duplicate-account rejection, indicating a failing email send path (SMTP/mail configuration on the server build being used). A subsequent tap on a live address returned the expected `"This email is already related to another shop."` guard message.
- **NOT VERIFIED:** changing Closing Time in the UI and watching the subtitle update live (only compilation + read-only rendering confirmed).
- **NOT DEPLOYED:** OnePlus Nord was not connected during this session, so it still runs an older build.

### Known Limitations / Notes
- The subtitle is a single small line; long rollover labels (e.g. `12:30 AM`) fit, but this is unverified on narrow devices.
- Historical evidence for the removed card remains in `OPERATIONS/BUSINESS_SETTINGS_OPERATIONS.md` (OP-005) and `artifacts/android-ui-tests/device_business_day_card.png`.

## Session 004: Restaurant Configuration Visual Redesign & Card Architecture
- **Session ID:** SES-20261112-004
- **Date & Time:** 2026-11-12 16:30 – 16:55 IST
- **Target Device / Architecture:** Android POS (`ShopConfigSection.kt`, `ShopConfigComponents.kt`, `KhanaBookScreenScaffold.kt`)
- **Initial Screen:** Restaurant Configuration (`SCR-08`)
- **Objective:** Modernize Restaurant Configuration UI to eliminate monolithic container, improve information architecture, add leading icons to all 6 inputs, compact Send OTP action, side-by-side time pickers, collapsible business day buffer, and full-width gold pill save button.

### Operations Performed
1. Updated `CommonComponents.kt` (`ParchmentTextField`) to support `placeholder` and `leadingIcon`.
2. Updated `KhanaBookScreenScaffold.kt` to accept `titleAlignment: TextAlign = TextAlign.Center`, allowing left-aligned headers next to the back button.
3. Updated `SettingsScreen.kt` to pass `titleAlignment = TextAlign.Start` when viewing `shop`.
4. Modernized `ShopConfigComponents.kt`: added expand/collapse chevron animation to `BusinessDayInfoCard`.
5. Redesigned `ShopConfigSection.kt` into 4 dedicated cards:
   - Card 1: Restaurant Details (Storefront icon, subtitle, logo changer, and 6 inputs with leading icons + compact gold `[ ➤ Send OTP ]` pill button).
   - Card 2: Business Hours (Clock icon, subtitle, Opening & Closing time pickers side-by-side).
   - Card 3: Business Day (Info icon, rollover time summary, collapsible settlement buffer explanation).
   - Card 4: Billing Mode (`RestaurantPaymentFlowSelector` with PIN lock protection).
   - Bottom CTA: Full-width rounded gold pill button for `Save Configuration` with loading spinner.
6. Compiled Kotlin via `./gradlew compileDebugKotlin`: **BUILD SUCCESSFUL**.
7. Ran Android unit tests via `./gradlew testDebugUnitTest --tests "com.khanabook.lite.pos.feature.auth.viewmodel.EmailOtpViewModelTest"`: **BUILD SUCCESSFUL** (3/3 passed).
8. Assembled debug APK via `./gradlew assembleDebug`: **BUILD SUCCESSFUL** (`app-debug.apk`).
9. Deployed and updated APK on both connected physical devices:
   - **Motorola moto g34 5G (`ZA222J5KT7`)**: Streamed install **Success**, launched with PID 28519.
   - **OnePlus Nord AC2001 (`adb-tls-connect`)**: Streamed install **Success**, launched with PID 1147.
10. Updated documentation `04_SCREEN_INVENTORY.md`, `CHANGELOG.md`, `SESSION_LOG.md`, and `NEXT_ACTIONS.md`.

---

## Session 003: Restaurant Configuration Email OTP Protection Implementation

- **Session ID:** SES-20261112-003
- **Date & Time:** 2026-11-12 15:35 – 16:05 IST
- **Target Device ID:** `ZA222J5KT7` (Motorola moto g34 5G, API 34)
- **App Version / Package:** `com.piquantservices.khanabooklite.debug`
- **Initial Screen:** Restaurant Configuration (`SCR-08`)
- **Objective:** Prevent unauthorized or accidental email changes in Restaurant Configuration by requiring 6-digit OTP verification for non-Google users, locking Google OAuth accounts, and implementing backend endpoints and database migrations.

### Operations Performed
1. Authored Flyway migration `V121__widen_otp_requests_phone_number.sql` widening `otp_requests.phone_number` to `VARCHAR(255)`.
2. Updated entity `OtpRequest.java` (`length = 255`).
3. Added `sendVerificationOtp(toEmail, otp)` in `EmailNotificationService.java` with development logging fallback.
4. Added `issueEmailUpdateOtp` and `validateEmailUpdateOtpOrThrow` in `PasswordResetOtpService.java`.
5. Added `requestEmailUpdateOtp` and `confirmEmailUpdate` in `UserService.java` & `UserServiceImpl.java` with OAuth check and uniqueness validation across tenants.
6. Added endpoints `/update-email/request` and `/update-email` with DTOs `UpdateEmailOtpRequest` and `UpdateEmailRequest` in `UserController.java`.
7. Added Android DTOs `UpdateEmailOtpRequest.kt` and `UpdateEmailRequest.kt` and API declarations in `KhanaBookApi.kt`.
8. Added Room DAO method `updateEmail` in `UserDao.kt` and delegation in `TenantDaos.kt`.
9. Added `requestEmailUpdateOtp` and `confirmEmailUpdate` in `UserRepository.kt` and `AuthViewModel.kt`.
10. Added email format validation, inline `Send OTP` action, 6-digit OTP entry, countdown timer, auto-verification, and save button protection in `ShopConfigSection.kt`.
11. Ran and verified backend unit tests in `UserServiceEmailUpdateTest`: 4/4 passed.
12. Ran and verified Android unit tests in `EmailOtpViewModelTest`: 3/3 passed.
13. Compiled and assembled debug APK (`app-debug.apk`) via `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
14. Streamed and updated APK onto connected device `ZA222J5KT7` via `adb install -r`: **Success**.
15. Launched application and verified PID 6678 running on device.

---

## Session 002: Quick Start Wizard Skip Delay & WhatsApp OTP Onboarding

- **Session ID:** SES-20261112-002
- **Date & Time:** 2026-11-12 14:35 – 14:52 IST
- **Target Device / Architecture:** Android POS / Compose Core (`QuickStartScreen.kt`, `QuickStartViewModel.kt`)
- **Initial Screen:** Quick Start Setup Wizard (`SCR-09`)
- **Objective:** Polish first-time onboarding by introducing WhatsApp number capture with OTP verification, adding a "Watch Tutorials" action, removing the arrow from "Start Billing", and delaying the `[Skip]` action button.

### Operations Performed
1. Explored Google signup navigation flow across Web Admin (`/business/dashboard`) and Android POS (`device_setup` → `quick_start` → `main/0`).
2. Updated `QuickStartScreen.kt` with a 5000ms delay timer and `AnimatedVisibility` fade-in for the `[Skip]` text button.
3. Added "WhatsApp number" input field under Shop Name in `QuickStartScreen.kt` with a "Get OTP" action button, 6-digit OTP confirmation, and visual verification indicator.
4. Updated `QuickStartViewModel.kt` to persist verified WhatsApp numbers to `RestaurantProfileEntity` and update `UserRepository` for the active owner user.
5. Redesigned bottom bar into a dual-button row containing `Watch Tutorials` (Outlined) and `Start Billing` (Primary Gold without arrow symbol).
6. Verified compilation with `./gradlew compileDebugKotlin` on JDK 17: **BUILD SUCCESSFUL**.
7. Updated screen inventory `04_SCREEN_INVENTORY.md` and `CHANGELOG.md`.
8. Built fresh debug APK via `./gradlew assembleDebug` (`apps/android/app/build/outputs/apk/debug/app-debug.apk`).
9. Installed/updated build onto connected hardware `ZA222J5KT7` via `adb install -r`: **Success**.
10. Launched updated application and verified active state with screenshot `artifacts/android-ui-tests/quickstart_update_verification.png`.

---

## Session 001: Initial Setup, Dine-In Lifecycle & Multi-Tender Verification

- **Session ID:** SES-20261111-001
- **Date & Time:** 2026-11-11 22:45 – 23:45 IST
- **Target Device ID:** `ZA222J5KT7` (Motorola moto g34 5G)
- **App Version / Package:** `com.piquantservices.khanabooklite.debug`
- **Initial Screen:** Home Dashboard (`SCR-01`)
- **Objective:** Verify live UI billing, Dine-in KOT generation, multi-tender settlement (Cash + UPI), and daily order numbering mechanics.

### Operations Performed
1. Verified connected target device `ZA222J5KT7` via ADB.
2. Navigated to Settings → Shop Profile: verified closing time `12:20 AM` + `30 min buffer` = `12:50 AM` reset cutoff.
3. Settled active Dine-in order `#A02` (₹367.50) using dynamic UPI QR code. Verified invoice `A000057`.
4. Executed full Dine-in lifecycle on `TableOne` with 2 items (`Livetest` + `webtest`). Fired KOT. Generated order `#A04`.
5. Settled order `#A04` (₹367.50) using Cash tender. Verified invoice `A000059`.
6. Verified Orders tab displaying sequential history: `#A04`, `#A03`, `#A02`, `#A01`.
7. Discovered physical USB disconnection of device `ZA222J5KT7` at 23:40 IST (`Present = False`).

### Test Cases Executed
- `TC-FUNC-001` (Dine-in Order Creation & KOT): **PASS**
- `TC-FUNC-002` (Cash Settlement): **PASS**
- `TC-FUNC-003` (Dynamic UPI Settlement): **PASS**
- `TC-FUNC-004` (Order History Tracking): **PASS**
- `TC-BIZ-001` (GST 5% Calculation Parity): **PASS**
- `TC-BIZ-002` (Terminal Prefix & Sequencing): **PASS**
- `TC-BIZ-003` (Settlement Buffer Rollover Card): **PASS**

### Screenshots Captured
- `device_business_day_card.png`
- `device_orders_screen.png`
- `device_upi_qr_screen.png`
- `device_payment_success.png`
- `device_cash_settlement_success.png`
- `device_orders_screen_updated.png`
- `device_orders_screen_final.png`

### Final Application State
- All 4 orders (`#A01` through `#A04`) settled.
- 0 active orders waiting on Home dashboard.
- Device temporarily disconnected from USB; awaiting user reconnection.

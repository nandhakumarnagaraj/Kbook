# Changelog

## [2026-11-14] - Shop Logo header, Order Details redesign, dialog refresh fix
### Added
- Restaurant Configuration: `Shop Logo` / `Shown on bills and receipts` header above the logo and Change Logo row.
- Payment mode picker entries now show the mode icon; the shared selection dialog (`KhanaBookSelectionDialog`) gained an optional `leadingIcon` and a selected check, so Cancel Order reasons get the same treatment.

### Changed
- Order Details dialog: order number, invoice, type and date are grouped in one soft card; Print and Share are side by side; split-amount dialog (`PartAmountDialog`) has an "Order total" strip, a sum hint and a filled gold Confirm button.

### Fixed
- **Order Details showed stale data after edits.** The dialog renders a snapshot, so changing payment mode left the old mode on screen and a cancelled order could still show live actions until the dialog was reopened. `ReportsViewModel.refreshOpenBill` now re-reads the bill after `updatePaymentMode` / `cancelOrder`.

### Verified
- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:testDebugUnitTest` exit 0; APK SHA-256 `f6c0a4c6…79ed5e36b` installed on `ZA222J5KT7`.
- Live: A01 Cash + UPI → Cash updated the dialog immediately; split dialog confirmed 183.75 / 183.75 and reverted the bill to Cash + UPI; Cancel Order opened with the selected-reason check and was dismissed via Keep Order, leaving data untouched. Screenshots under `artifacts/android-ui-tests/` (`shop-logo-header-*`, `order-details-redesign-*`, `payment-mode-picker-*`, `part-amount-dialog-new-*`, `payment-mode-refresh*`, `cancel-order-dialog-*`).

## [2026-11-14] - Orders table no longer stretches; Restaurant Configuration header removed
### Changed
- **Orders table (`ReportViews.kt`)**: the panel used `fillMaxSize()`, so two orders left a full-height empty card. It now measures inside `BoxWithConstraints` with `heightIn(max = maxHeight)` and a `weight(1f, fill = false)` list — it hugs the rows when short and still fills and scrolls when long. Empty results keep a 180 dp empty state. Header label `OrderNo` → `Order No`. Chips, columns and row actions untouched.
- **Restaurant Configuration (`ShopConfigSection.kt`)**: removed the in-card header (storefront icon, "Restaurant Details", "Configure your restaurant information"). The card starts with the logo / Change Logo row; fields and lower cards are unchanged.

### Verified
- `:app:compileDebugKotlin`, `:app:assembleDebug` and `:app:testDebugUnitTest` exit 0; APK SHA-256 `0ab8c374…039bc4b9`, installed on `ZA222J5KT7`.
- Device check: Orders panel ends right under `A01` with `A02` still shown as cancelled; Restaurant Configuration shows the logo row at the top of the card. Screenshots `artifacts/android-ui-tests/orders-table-fixed-2026-11-14.png` and `restaurant-config-no-header-2026-11-14.png`.

## [2026-11-14] - Launcher icon modernization
### Changed
- Adaptive launcher icon moved out of `res/mipmap-anydpi-v26/` into `res/mipmap/` (the `v26` folder is deleted; minSdk 26 makes the qualifier redundant).
- Added the Android 13 `<monochrome>` layer to both `ic_launcher.xml` and `ic_launcher_round.xml`, backed by new `ic_launcher_monochrome.png` assets at mdpi–xxxhdpi. The mask is generated from the existing foreground: near-white pixels become transparent, everything else solid black, so the logo silhouette drives themed icons.
- Removed the legacy square `ic_launcher.png` / `ic_launcher_round.png` from all density folders (adaptive icon always wins at minSdk 26).
- Deduplicated the brand logo: `HelpSupportView` uses `R.drawable.khanabook_logo`, `about_app_logo.png` deleted, and `khanabook_logo.png` moved to `drawable-nodpi/` to satisfy the densityless-bitmap rule.

### Verified
- `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug` all exit 0; lint icon warnings (`MonochromeLauncherIcon`, `IconLauncherShape`, `IconLocation`, `IconDuplicates`, folder `ObsoleteSdkInt`) are all 0 and total warnings fell 469 → 458.
- APK SHA-256 `7c8ac92e…6238aac21c5` installed on `ZA222J5KT7`: launcher shows the adaptive icon in the drawer and the app opens to Home. Screenshots `artifacts/android-ui-tests/launcher-icon-final-2026-11-14.png` and `launcher-icon-home-final-2026-11-14.png`.
- **Limitation:** themed-icon rendering itself was not screenshotted; this launcher has no themed-icon toggle. The monochrome layer is verified by APK contents, lint and the generated silhouette only.

## [2026-11-14] - Lint to zero errors, dead resources removed
### Fixed
- **Wi-Fi printer subnet prefill on Android 8.0–9:** `RouteInfo.hasGateway()` (API 29) replaced with `it.gateway != null`, so gateway detection no longer throws and silently returns nothing on API 26–28.
- **Locale-dependent formatting:** `String.format` now passes `Locale.US` for currency amounts and OTP countdowns (`CurrencyUtils`, `SignUpScreen`, `QuickStartScreen`, `ShopConfigSection`).
- **Dead SDK branches:** removed API-26 guards and redundant `@RequiresApi(O)` annotations from `NotificationHelper` / `NotificationChannels`, and the always-true `SDK_INT >= O` branches in `PrintService`.
- **Misindented block:** comment + `val isInitialLoading` in `MenuSelectionStep` re-indented into their enclosing scope (formatting only).

### Removed
- `res/values/styles.xml` (dead Views-era styles that carried the API 28 `lineHeight` and API 31 `clipToOutline` errors) and `res/values/fonts.xml` (invalid, inert `<font>` entries).
- Unused resources: `raw/anim_payment_success.json`, `drawable/ic_google_logo.xml`, the unused drawable `ic_launcher_foreground.xml`, `splash_footer.png`, `splash_full_brand.png`, `splash_logo_with_card.xml`, `splash_logo_with_glow.xml`, plus 18 colors, 18 dimens and 45 strings with no references.

### Verified
- `:app:lintDebug` went from **12 errors / 605 warnings** to **0 errors / 469 warnings** and now passes; `:app:compileDebugKotlin`, `:app:assembleDebug` and `:app:testDebugUnitTest` all exit 0.
- APK SHA-256 `9ea11e38…77a3f48ad` installed on `ZA222J5KT7`: cold-start splash and Home verified. Screenshots `artifacts/android-ui-tests/lint-cleanup-*-2026-11-14.png`.

## [2026-11-14] - Dead-code removal across Android screens
### Removed
- Report export in its entirety: `ReportsViewModel.exportReport` / `buildShareText`, `ReportDownloadBottomBar`, and `feature/reports/domain/ReportExporter.kt` (PDF/CSV generation). None of it had a UI caller, so the app no longer offers report download or export share text.
- Orders row-action plumbing: `onStatusChange` / `onPayModeChange` / `onRequestCancel` and the now-unused `enabledModes` parameter on `OrderTableRow` and `OrderHistoryTableComponent`, the callbacks in `OrdersScreen`, and `ReportsViewModel.updateOrderStatus`. Order editing remains available in `OrderDetailsDialog`.
- Four `ReportViews` compatibility aliases (`OrderDateFilterTabs`, `OrderRowItem`, `OrderHistoryTable`, `OrderLevelView`), `DetailStatusRow` + local `formatDate` in `OrderDetailsDialog`.
- `feature/billing/ui/ActiveOrderComponents.kt` (`BillInfoHeader`, `ActiveOrderItemCard`, `CompactMenuSection`), `feature/billing/ui/PaymentSection.kt` (`BillSummaryDisplay`, `SummaryRow`, `PaymentModeSelector`, `loadShopLogoBlockingSection`), `OrderTableComponents.TableHeader`, `MenuSelectionStep.FoodTypeIcon`, `OnlinePaymentsSetupScreen.NotStartedContent`, `ForgotPasswordDialog.loginTextFieldColors`.
- `AppInfoSection` and `CustomerNumberCollectorSelector` from settings, `KhanaInvoiceShareSheet.kt`, `SignaturePad.kt`, `Utils.directPrint`, `PaymentColors.payModeColor` / `getPayModeIconTint`.

### Kept deliberately
- The three `AppLockScreen` biometric callbacks: they are `BiometricPrompt.AuthenticationCallback` overrides called by the framework, not dead code (grep false positive).
- `OrderDateFilterComponent` and its `Today` / `Yesterday` / `Custom` chips (frozen by product decision), `getPayModeColor` / `getPayModeIcon`, and the `reports.full` role gate.

### Verified
- `:app:compileDebugKotlin --offline`, `:app:testDebugUnitTest --offline` and `:app:assembleDebug --offline` all exit 0. APK SHA-256 `9423d557…47777c894e`, installed on `ZA222J5KT7` at 2026-11-14 17:18:40.
- Live check: Home, Orders (chips `14 Nov`, table with cancelled `A02` struck through and `A01`), Order Details dialog (Print / Share / Change Payment Mode / Cancel) and Settings (config rows, App Settings, Sign Out) all render normally. Screenshots in `artifacts/android-ui-tests/deadcode-*-2026-11-14.png`.

## [2026-11-14] - Orders Screen: One Table, Selected Chip Shows Its Date
### Changed
- `OrdersScreen.kt` no longer has a `reportType == "Payment"` branch: the screen renders the first-load skeleton and then `OrderHistoryTableComponent`, nothing else. The branch was unreachable (`reportType` is always `"Order"`) but it was the only path that could replace the table.
- The selected date chip now shows the day it resolves to — `Today` → `14 Nov`, `Yesterday` → `13 Nov` — while unselected chips keep their names. `Custom` keeps its existing behaviour (`Custom` until a range is picked, then `12 Nov – 14 Nov`).
- `ReportsViewModel.dateFilterLabels` derives those labels from `BusinessDate.businessDate(now, profile)`, the same business date that selects the query window, and refreshes on filter change, on resume and when the profile re-emits.

### Removed (same day, 16:10)
- `PaymentLevelView` and its orphaned `PaymentModeItem` / `PartPaymentCard` / `ReportTypeToggle` helpers in `ReportViews.kt`.
- `ReportsViewModel._reportType` / `reportType` / `setReportType`, the `"Payment"` branches in `exportReport` and `buildShareText`, and the `reportType` parameter of `ReportExporter.exportToPdf` / `exportToCsv`. Both exports now always title as `Orders Report`; `paymentBreakdown` is kept because it feeds the payment summary inside them.

### Verified
- `:app:compileDebugKotlin --offline` and `:app:assembleDebug --offline` exit 0.
- Installed on `ZA222J5KT7` (`lastUpdateTime` 2026-11-14 16:10:35, APK SHA-256 `3a7c98f5…d313abc6d`) and checked by uiautomator: `Today` chip `14 Nov`, `Yesterday` chip `13 Nov`, `Custom` chip unchanged and its picker opens, table-only content.

### Fixed (16:2x)
- **Orders table columns were misaligned.** The row's order-number cell had no weight while the header cell did, so after the details line was removed every cell after it shifted left and stopped sitting under its centred header. The cell now uses `weight(COL_ORDER)` and the same start inset as the header; verified on `ZA222J5KT7` with matching header/row bounds (`[49,336][225,362]` vs `[49,411][225,436]`, `[225,336][392,362]` vs `[225,411][392,436]`).
- **Order number shows no `#`** — the table prints `A02` at the product owner's request (16:3x); the details dialog, search and share text still prefix it. A `#` was briefly added and removed the same session.
- The `Today` / `Yesterday` / `Custom` chips are frozen at the product owner's request — not touched by this change.

### Rebuilt (16:4x)
- Orders table rebuilt in place: a single `OrderTableCell` primitive now builds every column for both the header and the rows, so alignment comes from the shared column weights instead of per-cell padding. Rows use the existing responsive `tableCellFontSize` token (previously unused); the header keeps `tableHeaderFontSize`.
- Row density: 52 dp minimum on phones, 60 dp on the wide layout, with no vertical padding (the 48 dp action target sets the content height).
- The row action is a 36 dp disc in a 48 dp hit area and its description now reads `View order A02` — it opens the details dialog, it is not a menu. No menu was added.
- `LazyColumn` rows are keyed by `billId`, so refreshes update rows in place. Columns, data, empty state, hairlines and cancelled-row styling are otherwise unchanged; the frozen chips were not touched.
- Verified on `ZA222J5KT7` (APK SHA-256 `f153eedb…3d8b1ac903`): header and row cell centres agree (`Amount` 310 vs 309.5, `Time` 473 vs 472.5), order cells share left edge 49, 53 dp row pitch, 48×48 dp action hit area.

### Not Verified / Not Changed
- A Custom range was not re-picked after `Apply` on device (path unchanged since 12:20).
- Tablet/wide layout (60 dp rows) and a cancelled row were not exercised on device.
- The `canViewFullReports` gate and the row-level callbacks are unchanged (item K).

## [2026-11-13] - Web Admin Spec Suite Repaired (39 Failures → 0)
### Fixed
- `apps/web-admin` Karma suite reported **309 SUCCESS / 39 FAILED**; it now reports **350 SUCCESS / 0 FAILED** (`npx ng test --watch=false`, exit 0).
- 38 failures were stale `AdminApiService` spy mocks: the specs never added `getProfile` (business dashboard) or `getDashboardSummary` (businesses), which the pages now call on load. The mocks now return `of({})` for both.
- `feature-flags-page.component.spec.ts` asserted a hardcoded 28-flag catalogue that the page no longer contains — it renders `AdminApiService.getFeatureFlags()`. Rewritten against the API-driven behaviour rather than deleted.
- `form-validation.spec.ts` expected a `/prototype sample data shown/` toast from the Businesses header action; the page now starts the real onboarding workflow and shows `'Restaurant onboarding workflow initiated.'`. The assertion was updated to the current message and the test renamed to match.
- Dashboard legibility: `.dash-eyebrow`, `.printer-station__label`, `.printer-station__sub` and `.printer-tactile-btn` moved from `--kb-text-2xs` (11px) to `--kb-text-sm` (13px). The simulated 80mm receipt body (`.receipt-inner`) is exempted from the floor check because it draws thermal print output rather than screen copy.

### Verified
- `npm run build:dev` exit 0; all ten `check:*` design scripts exit 0; Karma 350/350.

### Not Changed
- No production behaviour was altered to make tests pass; only spec fixtures, one spec rewritten to the shipped behaviour, and four dashboard font sizes raised to the existing 13px floor.

## [2026-11-13] - Email OTP No Longer Fails On Unconfigured SMTP (BUG-004)
### Fixed
- `EmailNotificationService.sendVerificationOtp` treated any configured-looking mail host as deliverable. Because `application.properties` defaults `khanabook.email.enabled=true` and a real `spring.mail.username` while leaving `spring.mail.password` empty, every Email `Get OTP` tap on such a server attempted an authenticated SMTP send, threw, and rolled back the OTP row inside `PasswordResetOtpService.issueEmailOtp` — surfacing "Failed to send verification email. Please try again." to the POS for a perfectly valid address.
- The method now also checks `spring.mail.password`; when it is blank it logs the generated code with a warning that names `MAIL_PASSWORD` and returns without contacting the mail server, matching the documented development fallback. A send failure on a genuinely configured host still throws `IllegalStateException`.
- Added `EmailNotificationServiceTest` covering all four branches (unconfigured, disabled, delivered, transport failure): 4/4 pass.

### Not Changed
- No Android code was modified; the POS already displays the server's message and needs no client-side change.

### Remaining Deployment Action
- Verified by unit test only. Actual delivery still requires `MAIL_PASSWORD` on the target server; until it is set, codes appear in the server log and never reach the inbox. End-to-end re-verification on device is pending.

## [2026-11-12] - Billing Mode Card Matched To The Other Cards
### Fixed
- `ShopConfigComponents.kt` (`RestaurantPaymentFlowSelector`): the Billing Mode panel painted its own `DarkBrown2.copy(alpha = 0.45f)` surface with a 45%-alpha gold border, so it appeared as a darker maroon block next to the `CardBG` (`0xCC78461E`) Restaurant Details and Business Hours cards.
- It now uses the same surface language: `CardBG` fill, `BorderGold` at 25% alpha, `KhanaRadii.lg` corners, `spacing.large` padding.
- Header aligned too: 28 dp gold icon, `titleMedium` bold title in `TextLight`, and a `bodySmall` `TextGold` subtitle ("Choose when customers settle the bill").
- Business Hours subtitle: the rollover time now uses a non-breaking space internally (`12:30\u00A0AM`) so it no longer wraps between the minutes and the AM/PM suffix.

### Verified on device (`ZA222J5KT7`, 2026-11-12 18:55 IST)
- `artifacts/android-ui-tests/cards_aligned.png` shows Restaurant Details, Business Hours and Billing Mode sharing one card surface, with the subtitle wrapping as "Daily Order Number will reset at" / "12:30 AM".

## [2026-11-12] - Business Hours Card Collapses (Times Hidden Until Tapped)
### Changed
- `ShopConfigSection.kt`: the Business Hours card now opens **collapsed**. Only the clock icon, the `Business Hours` title, the rollover subtitle and a chevron are visible; the Opening Time / Closing Time fields are hidden by default.
- Tapping anywhere on the card header expands the two time fields (220 ms `expandVertically` + `fadeIn`); tapping again collapses them (180 ms `shrinkVertically` + `fadeOut`). Chevron flips between down/up with content descriptions "Show opening and closing time" / "Hide opening and closing time".
- `BusinessDate.closingBoundary`-derived rollover subtitle stays visible while collapsed, so the reset time is readable without expanding.

### Verified on device (`ZA222J5KT7`, 2026-11-12 18:43-18:47 IST)
- Collapsed: `artifacts/android-ui-tests/after_restart.png`
- Expanded: `artifacts/android-ui-tests/business_hours_expanded.png`
- Closing Time dialog: `artifacts/android-ui-tests/closing_time_picker.png`
- Live recalculation after picking `11:30 AM`: `artifacts/android-ui-tests/closing_time_changed.png` (subtitle became "Daily Order Number will reset at 12:00 AM")

## [2026-11-12] - Business Day Card Merged Into Business Hours
### Changed
- Removed the separate `Business Day` card from Restaurant Configuration (`ShopConfigSection.kt`); its rollover information now lives in the **Business Hours** card subtitle so the schedule and the daily order-number reset read as one setting.
- Business Hours subtitle changed from the generic "Set your restaurant operating hours" to `"Daily Order Number will reset at <time>"`, derived live from the Closing Time picker (+30-minute settlement buffer, or the server-activated cutoff when one is in force).
- Opening Time and Closing Time time pickers are unchanged.
- Replaced the `BusinessDayInfoCard` composable in `ShopConfigComponents.kt` with the pure helper `businessDayRolloverLabel(cutoffTime, cutoffEffectiveFrom, openingTime, closingTime)`.

## [2026-11-12] - In-Flight Loading State, Persistent Inline Errors & Inline Resend OTP
### Added
- Added `OtpVerificationResult.Loading` state in `AuthViewModel.kt` for reactive in-flight network observation.
- Added in-flight loading spinners on both WhatsApp and Email `[ Get OTP ]` buttons (`isOtpLoading` / `isEmailOtpLoading`) to eliminate duplicate taps.
- Added persistent inline field errors (`phoneServerError` / `emailServerError`) directly beneath the WhatsApp and Email inputs, maintaining red error borders until the input is edited.
- Added inline `[ Resend ]` button inside the 6-digit OTP prompt trailing icon area when the 120-second countdown reaches `00:00`.

## [2026-11-12] - Restaurant Configuration Modern Card Architecture Redesign
### Changed
- Redesigned Restaurant Configuration screen (`ShopConfigSection.kt` and `ShopConfigComponents.kt`) matching high-craft visual spec:
  - **Top Bar Header**: Aligned title `"Restaurant Configuration"` directly adjacent to the back button `(←)` using `titleAlignment = TextAlign.Start` in `KhanaBookScreenScaffold.kt` and `SettingsScreen.kt`.
  - **Card 1 (Restaurant Details)**:
    - Added bold title with gold Storefront hero icon and descriptive subtitle.
    - Added compact logo thumbnail with "Change Logo" button.
    - Integrated leading icons for all 6 inputs: `Storefront` (Shop Name), `LocationOn` (Shop Address), `Phone` (WhatsApp Number), `Email` (Email), `Link` (Review Link), `Description` (Invoice Footer).
    - Compact gold pill button `[ ➤ Send OTP ]` positioned inside the Email trailing icon space.
  - **Card 2 (Business Hours)**:
    - Gold clock icon, descriptive subtitle, and side-by-side time picker fields (`KhanaTimePickerField`) for Opening and Closing time.
  - **Card 3 (Business Day)**:
    - Refactored `BusinessDayInfoCard` with gold Info icon, clean rollover summary, and collapsible settlement buffer drawer toggled via chevron (`KeyboardArrowDown` / `KeyboardArrowUp`).
  - **Card 4 (Billing Mode)**:
    - Retained PIN-protected `RestaurantPaymentFlowSelector` styled cohesively with card surface.
  - **Bottom Action Button**:
    - Replaced dual back/save row with a single full-width rounded gold pill button for `Save Configuration` (`height = 52.dp`, `shape = KhanaRadii.xl`).
  - **Scrollbar**: Slender 3.dp vertical scrollbar thumb.
- Updated `SCR-08` in `04_SCREEN_INVENTORY.md`.

## [2026-11-12] - Email OTP Protection & Quick Start Wizard Onboarding Polish
### Added
- Backend Email OTP endpoints: `/api/v1/sync/config/users/update-email/request` and `/api/v1/sync/config/users/update-email`.
- Flyway migration `V121__widen_otp_requests_phone_number.sql` widening `otp_requests.phone_number` to `VARCHAR(255)` to support email OTP challenge storage.
- Verification email dispatch in `EmailNotificationService.java` with development logging fallback when SMTP is unconfigured.
- Unit tests `UserServiceEmailUpdateTest.java` (backend) and `EmailOtpViewModelTest.kt` (Android POS).

### Changed
- Added Email OTP verification guard to `ShopConfigSection.kt`:
  - Enforced Google account OAuth email immutability (`enabled = !isGoogleAuth`).
  - Added email format validation (`ValidationUtils.isValidEmail`).
  - Added inline `Send OTP` button in trailing icon when a valid new email is entered.
  - Added inline 6-digit OTP verification field with countdown timer and auto-submission on the 6th digit.
  - Guarded profile save action: rejects save if email is altered but unverified.
- Updated `SCR-08` in `04_SCREEN_INVENTORY.md`.
- Added WhatsApp number field with inline OTP verification (`Get OTP` / 6-digit confirmation) under Shop Name in `QuickStartScreen.kt`, auto-propagating to `RestaurantProfileEntity` and `UserEntity`.
- Refactored bottom action layout in `QuickStartScreen.kt` to a dual-button row: removed arrow symbol from `Start Billing` and added `Watch Tutorials` button side by side.
- Updated `QuickStartScreen.kt` to delay the appearance of the `[Skip]` action button by 5 seconds with a smooth fade-in (`AnimatedVisibility`), ensuring first-time restaurant owners review the initial shop and menu setup before skipping to the POS billing grid.
- Added and updated `SCR-09` (Quick Start Setup Wizard) in `04_SCREEN_INVENTORY.md`.

## [2026-11-11] - Session 001 Verification
### Added
- Created complete `ANDROID_POS_DOCUMENTATION/` knowledge base tree.
- Documented operational manuals for Dine-In, Payments, and Order History.
- Documented complete Dine-In sequence workflow from seating to cash checkout.
- Added verified test cases for functional billing and business logic.
- Recorded live audit log for orders `#A01`, `#A02`, `#A03`, `#A04` on device `ZA222J5KT7`.
- Added `Android POS UI Operations Documentation Policy` to `AGENTS.md`.

### Verified & Resolved
- 30-minute settlement buffer confirmed across Android POS and Server core logic.
- Dynamic UPI QR rendering and cashier cash tendered calculation confirmed on physical hardware.
- Fixed BUG-003: Aligned `ReportsViewModel.kt` date filtering (`Today`, `Yesterday`, `Custom`) to `BusinessDate.windowStart / windowEnd`, preventing midnight shift fragmentation.
- Added dining table identifier (`customerName`) to the Orders list row in `ReportViews.kt`.

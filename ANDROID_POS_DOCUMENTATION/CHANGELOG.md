# Changelog

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

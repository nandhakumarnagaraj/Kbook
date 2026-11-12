# Changelog

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

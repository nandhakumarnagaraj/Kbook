# Device Testing Session Log

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
8. Assembled debug APK via `./gradlew assembleDebug`: **BUILD SUCCESSFUL** (`app-debug.apk` ready for hardware deployment).
9. Updated documentation `04_SCREEN_INVENTORY.md`, `CHANGELOG.md`, `SESSION_LOG.md`, and `NEXT_ACTIONS.md`.

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

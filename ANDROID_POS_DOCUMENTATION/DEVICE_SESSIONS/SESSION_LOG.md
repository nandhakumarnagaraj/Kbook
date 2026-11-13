# Device Testing Session Log

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

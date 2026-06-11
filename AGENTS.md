# KhanaBook v2 — Agent Context

## Goal
Complete Android Compose UI rebuild to match 20 Stitch-generated design mockups using Premium Saffron palette + semantic opacity tokens.

## Constraints & Preferences
- Easebuzz API hash sequences must exactly match official Stoplight docs
- Sub-merchant lifecycle managed exclusively via admin web UI
- All changes go to `v2` branch (not `main` which is v1 production)
- KhanaBook brand logo from Android `drawable` used in web admin
- Android: use existing KhanaBookTheme tokens + KbOpacity, maintain dark/light mode support
- POS environments: 95–100% opaque cards, 8–16% tinted backgrounds, no glassmorphism
- Android target: API 26–36, mobile (small/medium/large) + tablet (small/medium/large)
- Premium Saffron palette: brand `#F97316`, bg `#FAF8F5` light / `#121212` dark, ink `#1F2937`/`#F5F5F5`
- All Stitch designs use Premium Saffron design system (saffron primary, Plus Jakarta Sans headlines, Inter body, ROUND_TWELVE)

## Progress
### Done
- **Easebuzz Transaction Status → V2.1**: endpoint `/transaction/v2.1/retrieve`, hash `key|txnid|salt`
- **Easebuzz Update Sub-Merchant**: same endpoint `/merchant/v1/submerchant/create` with `sub_merchant_id` in body, no password fields
- **Split Label Create API**: `/split/v1/create`, hash `key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt`
- **Post-Transaction Split Create**: `/post-split/v1/create/`, hash `key|merchant_request_id|easebuzz_id|salt`, max 3 attempts per transaction
- **Post-Transaction Split Retrieve**: `/post-split/v1/retrieve/`, hash `key|merchant_request_id|salt`
- **KYC Access Key API**: `/submerchant/v1/generate_kyc_access_key`, hash `merchant_key|sub_merchant_id|name|email|phone|salt`
- Server entity: added `branchName`, `splitLabel` columns to `EasebuzzSubMerchant`
- `SubMerchantService.create()` now sets `branchName` from request data
- `AdminSubMerchantController`: added 4 new endpoints — `update-on-easebuzz`, `kyc-access-key`, `split-label`
- Web admin `api.models.ts`: added `bankName`, `branchName`, `splitLabel`, `upiDeductionLtLimit`, `dcDeductionGtTwoThousand`, `idProofUrl`, `bankProofUrl`, `easebuzzResponse` to `EasebuzzSubMerchant`; added `bankName`, `branchName`, `upiDeductionLtLimit`, `dcDeductionGtTwoThousand` to `EasebuzzSubMerchantRequest`
- Web admin `admin-api.service.ts`: fixed `generateKyc` URL → `/kyc-access-key`, removed dead `registerSubMerchant`, added `submitToEasebuzz()`, `updateOnEasebuzz()`, `createSplitLabel()`
- Web admin sub-merchant page: context-sensitive action buttons (Submit 🚀, KYC 🔑, Sync 🔄, Split 🏷️), form fields for `bankName`/`branchName`, detail panel showing bank info + split label
- **KYC Aging Column**: added to sub-merchant admin table (`formatAge()` utility in `formatters.ts`)
- **Responsive UI Fixes**: orders refund modal width (`max-width: unset` on mobile), business dashboard metric card contrast (stronger border/shadow on mobile), global `styles.css` refactored with `@media (prefers-reduced-motion: reduce)` and `100dvh` support
- Brand logo: created SVG placeholder → replaced with PNG from Android `khanabook_logo.png` → deployed to web admin sidebar + login page + og:image + favicon
- SEO: meta description, og:title/description/type/image in `index.html`
- Web admin login page: added logo, updated copy, removed old `h1`/`.eyebrow` CSS
- Web admin sidebar: added `.brand-logo` image, removed text-only branding
- **GST/FSSAI Lookup UI wired**: Android `TaxConfigView` now has "Fetch" buttons next to FSSAI and GSTIN fields, plus a "Fetch Both" button. `SettingsViewModel` calls `/api/v2/business/lookup/gst`, `/fssai`, `/both` and shows results in an AlertDialog. Users can apply fetched `businessName`/`address` to `shopName`/`shopAddress` and update `gstin`/`fssaiNumber`. Build compiles successfully.
- **Server Compilation Fixed**: Added Maven wrapper (`mvnw`) to `server/`. `target/classes` now reflects new `.java` files. `NoResourceFoundException` for `/api/v2/business/profile` resolved.
- **Easebuzz Hash Corrections** (critical): Initiate Payment hash fixed from `udf6-udf10` to 5 empty pipes (`||||||`). Refund endpoint upgraded from v1 (`/transaction/v1/refund`) to v2 (`/transaction/v2/refund`) with correct hash `key|txnid|amount|salt`.
- **New Easebuzz APIs Added**: `getRefundStatus()` (v2), `cancelTransaction()`, `initiatePayout()` (v2), `initiateOnDemandSettlement()` — all wired in `EasebuzzApiClient.java`
- **Documentation**: Created `easebuzz-submerchant-business-model.md` (comprehensive business model + POS features + 20 sections). Updated `sub-merchant-apis.md` to 15 APIs with official Easebuzz Stoplight doc URLs for each.
- Git: committed `04599cd` (14 files, 1845 insertions), pushed to `origin/v2`
- **OTP APIs Implemented**: `verifyOtp()` + `resendOtp()` in `EasebuzzApiClient.java`, service methods in `SubMerchantService.java`, endpoints in `AdminSubMerchantController.java`, OTP action buttons (📱 OTP / 🔄 OTP) in web admin UI, API methods in `admin-api.service.ts`
- **Missing Endpoints Wired**: `cancelTransaction`, `initiatePayout`, `initiateOnDemandSettlement`, `retrieveTransactionSplit` — all wired from client → service → controller
- **Web Admin Split Retrieve**: 🔍 Retrieve Split Status button in detail panel
- **Android Refund Status**: Added `getEasebuzzRefundStatus()` API + DTO + repo method
- **Documentation Updated**: `sub-merchant-apis.md` endpoint table now includes all 22 endpoints

### 2026-05-21 Session — Typed Exceptions, Webhook HMAC Auth, Bug Fixes
- **Typed exception hierarchy**: `BusinessRuleException` (→ 422), `EasebuzzApiException` (→ 502), `EntityNotFoundException` (→ 404) — all registered in `GlobalExceptionHandler`
- **Replaced all raw `RuntimeException` throws** in `EasebuzzPaymentService`, `SubMerchantService` with typed exceptions for correct HTTP status codes
- **txnid format fixed**: `KB{billTail5}{restTail5}{uuid8}` = exactly 20 chars (Easebuzz max limit); was `T{timestamp}` which could exceed 20 chars
- **PostSplitService idempotency**: skip if bill already settled (`settledAt != null`); stable `merchantRequestId` using `KB{billId}_{easebuzzIdPrefix}` — Easebuzz deduplicates replays
- **MarketplaceWebhookController**: HMAC-SHA256 `X-Hub-Signature-256` verification added; onboarding mode accepts null signature when no secrets configured; `rawPayload` now serialized as valid JSON via `ObjectMapper` (was `Map.toString()`)
- **MasterSyncController `hasMore` bug fixed**: raw list size now checked BEFORE truncation (was checking truncated size — never triggered)
- **MenuExtractionController**: tenant isolation enforced on `GET /jobs/{id}`; new `GET /jobs` list endpoint added
- **MenuExtractionJobRepository**: added `findByRestaurantIdOrderByCreatedAtDesc`
- **All 133 tests passing** after changes; committed `05683fc`, pushed to `origin/v2`

### 2026-05-26 Session — Dashboard Layout, Sub-Merchant & Business Directory Modal Fixes, Order Management & Marketplace Setup UI Rebuilds
- **Platform & Payment Dashboard Headers**: Wrapped page titles and subtitles/dates in `platform-dashboard-page.component.ts` and `payment-dashboard-page.component.ts` to display on a single line. Reduced stat card padding and icon size in `payment-dashboard-page.component.ts` to prevent cards from wrapping.
- **Sub-Merchant Details View (Modal Conversion)**: Refactored sub-merchant details layout in `sub-merchants-page.component.ts` from a fixed side panel to a clean overlay modal (`MatDialog` via `#detailDialog` `<ng-template>`). Table now occupies full screen width.
- **Business Directory (Table Spacing & Actions Modals)**: Re-styled the table headers and cells in `businesses-page.component.ts` to match the premium design (uppercase uppercase header text, border bottom, shadows, row hover). Right-aligned the Actions header cell `Action`. Replaced bottom drawer with `MatDialog` modals for View Details, Edit Business, and Suspend actions, with local state mapping/simulation and snackbar alerts for testing.
- **Header Toolbar Alignment**: Styled the profile trigger button as a borderless flex item and removed the Material button wrapper, ensuring that the Toggle Light/Dark Mode button and user avatar align on the exact same vertical center line.
- **Quick Shortcuts Feature Removal**: Removed the "Quick Shortcuts" button, popover modal template dialog, and key listener events completely from `business-dashboard-page.component.ts` to declutter the header actions layout.
- **POS Stats Cards Compact Styling**: Reduced size, padding, gaps, and hover scaling on the "Total Orders" and "Total Revenue" stats cards under the Store (POS) tab on the Order Management page.
- **Marketplace Order "Completed" State Integration**: Integrated "Completed" order counts, status box filters, Kanban board columns, list columns, and action flows (allowing transitioned "Ready" orders to be marked as "Completed" via API call and refreshing dashboard state).
- **Marketplace Setup UI Rebuild**: Rebuilt the Zomato and Swiggy configuration UI with premium cards, glassmorphic accents, brand-colored glow animations, custom slide toggles, an Easebuzz Settlement Onboarding horizontal timeline tracker, custom detailed tiles, copy-to-clipboard functionality, and responsive formatting.
- **Build & Verification**: Built web-admin using `npm run build` and ran all 133 backend unit tests, verifying successful compilation and passes.
- **Git**: Committed changes to local tracking branch `origin/v2` (no remote pushes).

### 2026-05-26 Session (Afternoon) — Accessibility Overhaul, Route Animations, Shared Components, CSS Optimization
- **Route Transition Animations**: Added `fadeSlide` animation trigger in `AppComponent` with staggered enter/leave transitions. All routes now have `animation` metadata in `app.routes.ts`.
- **New Shared Components**: Created `BreadcrumbComponent` (configurable breadcrumb nav with home icon), `EmptyStateComponent` (icon + title + description + ng-content), `ErrorStateComponent` (icon + title + description + detail + retry button with EventEmitter), `SkeletonComponent` (configurable shimmer loader with text/card/circle variants).
- **Accessibility Improvements**: Added `aria-hidden="true"` on all decorative icons, `aria-label` on action buttons and toolbars, `aria-describedby` on critical actions, `#main-content` ID on `<main>` for skip-to-content, global `:focus-visible` ring, `text-balance` utility class for headings, `tabular-nums` for financial data.
- **CSS Transition Optimization**: Replaced all `transition: all` with specific properties (e.g., `transform`, `background`, `box-shadow`, `color`, `border-color`) across all components for better rendering performance.
- **Global Style Refinements**: Added `--shadow-sm` through `--shadow-xl` CSS custom properties, refined theme variables, added `overscroll-behavior: contain` for dialogs/overlays.
- **Sub-Merchant Page**: Added `BreadcrumbComponent` integration with new `crumbs` array, fixed duplicate `MatTooltipModule` import.
- **Build & Tests**: Web-admin builds successfully (12s), all 133 backend tests pass.
- Git: Committed `288b9ab` (36 files, 2546 insertions, 165 deletions), pushed to `origin/v2`.

### 2026-05-26 Session (Night) — UI/UX Rebuild Complete & Verified
- **Platform & Business Dashboards Rebuilt**: Refactored `platform-dashboard-page.component.ts` to include responsive layout adjustments, glassmorphism (`backdrop-filter`), spring physics transitions (`cubic-bezier(0.34, 1.56, 0.64, 1)`), and standardized spacing. Refactored `business-dashboard-page.component.ts` charts to read theme colors dynamically from CSS variables on the DOM root, ensuring full dark/light theme consistency.
- **Android POS Screens Overhauled**: Rebuilt `HomeScreen.kt` with spring curve transitions for staggered entries and upgraded the compliance banner close button to a 48.dp `IconButton` to satisfy WCAG touch targets. Rebuilt `NewBillScreen.kt` transitions with Compose spring specs (`dampingRatio = 0.85f`, `stiffness = StiffnessMediumLow`) and cart item lists with unique keys and `Modifier.animateItem()` list entry/exit animations. Overhauled `EasebuzzKycScreen.kt` to use a premium, visually engaging timeline tracker canvas component (`KycTimeline` and `KycTimelineItem`).
- **Builds Compiled and Verified**: Verified Angular dashboard production compilation (`ng build` succeeded in 16s) and Android Gradle Kotlin compilation (`.\gradlew.bat compileDebugKotlin` succeeded in 1m 33s).
- **Git**: Committed and pushed commit `e9078ab` to `origin/v2`.

### 2026-05-27 Session — Design System Tokens Mapped & Tactile Polish
- **Applied "Grounded Earth & Saffron" Design System**:
  - Web Admin: Configured missing brand saffron color variables under `:root` and `.dark-theme` blocks. Overrode Material flat and raised buttons to use the brand saffron color (`#C85A00`), `48px` height, and `10px` rounded corners.
  - Android POS: Added saffron color constants (`KbBrandSaffron`, `KbBrandSaffronLight`, `KbBrandSaffronDark`, `KbBrandSaffronAndroid`) in `Color.kt` and bound them as primary theme values in `Theme.kt`. Synced rounded corner scaling in `Shape.kt` and `KbTokens.kt` (`sm` -> `6.dp`, `md` -> `10.dp`, `lg` -> `14.dp`, `xl` -> `18.dp`). Set primary button CTA height to `48.dp`.
- **UI/UX Polish & Micro-interactions**:
  - Web Admin: Added smooth theme transitions for background/panels, spring active press states (`transform: scale(0.97)`) on action buttons, and subtle mesh gradients (`linear-gradient`) on platform stats cards. Verified compilation via `ng build`.
  - Android POS: Integrated tactile haptic feedback (`LocalHapticFeedback` and `HapticFeedbackType.LongPress`) on quantity selectors in `NewBillScreen.kt` cart update flow.
- **Stitch MCP Setup**: Initialized authentication configuration using Stitch API Key and created a non-interactive, automated environment configuration.
- **Android Theme Token Migration & Compilation Fixes**: Migrated all hardcoded color references across 50+ Android files to use theme-aware `MaterialTheme.kb*` extension properties (kbPrimary, kbSecondary, kbTextPrimary, kbTextSecondary, kbOutlineSuble, kbBgCard, etc.). Fixed missing imports in KhanaBookSelectionDialog.kt (kbTextSecondary), OcrScannerScreen.kt (MaterialTheme), and 5 other files. Added `KhanaBookGlassCard` composable with saffron gradient border. Android `compileDebugKotlin` passes with BUILD SUCCESSFUL.

### 2026-06-01 Session — Android Compose UI Rebuild to Match 20 Stitch Designs
- **Stitch Project Linkage**: Linked and verified active project `16315279777734818483` named `KhanaBook POS Design System` with the custom design system `assets/30a3dedd9fb342258a425c0c4d252c88` (Warm Hospitality POS).
- **Stitch Design Mockups Generated**: Generated core visual screens (Home Dashboard, Login, Splash Screen, OTP Verification, Initial Sync, Orders List, New Bill, Menu Configuration, Easebuzz Payment, Settings list, Printer Config, Tax Config, KYC Verification, and Marketplace Orders) directly on the Stitch project `16315279777734818483` using the design system.
- **10 Android Compose Screens Rebuilt**:
  - `HomeScreen.kt` — saffron header strip (greeting + bell), 3 MetricCards (Orders/Revenue/Avg Order) with saffron top border + shadow, Quick Actions row (New Bill filled, Orders outlined, Menu outlined), grid action cards
  - `MainScreen.kt` — bottom nav tabs Home/Orders/Menu/Settings with `KbBrandSaffron` active indicator
  - `NavigationItems.kt` — tabs updated from Home/Reports/Orders/Profile → Home/Orders/Menu/Settings
  - `NewBillScreen.kt` — "New Order #1042" header, horizontal category chips (saffron active), compact menu cards, bottom cart panel with qty controls + grand total + "Place Order" saffron button
  - `OrdersScreen.kt` — filter chips, search bar, order cards with Order#/name/items/time/status chip using `KbOpacity.StatusBg`/`StatusBorder`
  - `MenuConfigurationScreen.kt` — header, category chips, item cards (toggle/edit), FAB
  - `EasebuzzPaymentScreen.kt` — large ₹ amount display, 2×2 payment method grid (Cash/UPI-QR/Card/Easebuzz), Process Payment button
  - `SplashScreen.kt` — full `KbBrandSaffron` bg, 80dp white logo, 32sp "KhanaBook", 14sp tagline, white spinner
  - `LoginScreen.kt` — white bg, saffron strip with logo + "KhanaBook", phone +91 prefix, password, forgot password, Continue saffron button, Google sign-in, Sign Up link
  - `SignUpScreen.kt` — white bg, saffron strip with step indicator (Phone→OTP→Profile dots), phone input, Send OTP saffron button
- **4 More Screens Rebuilt** (round 2):
  - `OtpVerificationScreen.kt` — new screen with 6-digit OTP input boxes, resend countdown, saffron "Verify OTP" button, "Change number?" link
  - `InitialSyncScreen.kt` — TopAppBar with logo + spinning sync icon, 75% circular progress arc, 4 sync steps (Downloading menu/orders → Configuring → Ready) with checkmarks/spinner/pending states
  - `EasebuzzKycScreen.kt` — "KYC Verification" header, 4-step horizontal stepper (Identity → Address → Bank → Submit) with green/saffron/gray, dashed document upload area, "Next" saffron button
  - `MarketplaceOrdersScreen.kt` — platform filter chips (All/Zomato#E23744/Swiggy#FC8019), order cards with platform badge + items + amount + status chip + Accept/Reject buttons
  - `ReportsScreen.kt` — "Reports" header, date range selector, 2×2 stat cards (Sales/Orders/Avg/Cancel rate), 7-day bar chart card, popular items list
- **Web Admin Hex Cleanup**: ~175 raw hex colors replaced with CSS vars across 26 component files
- **CSS Status Chip Vars**: Added `--success/bg/border`, `--danger/bg/border`, `--warn/bg/border`, `--info/bg/border` in both themes in `styles.scss`
- **APK Version 2 built & deployed**: `assembleDebug` → installed on device ZA222J5KT7

### In Progress
- None currently

### 2026-05-31 Session — Post-V2 Feature Enhancements & Critical Fixes
- **RefreshToken Entity Type Fixed**: Changed `expiresAt` and `createdAt` from `Instant` to `Long` (epoch millis) in `RefreshToken.java` to match V29 migration's `BIGINT` columns. Updated `AuthServiceImpl.java` to use `System.currentTimeMillis()`.
- **Missing Flyway Migrations Created**:
  - V30__add_chargebacks.sql: chargebacks table with indexes on restaurant_id, status, bill_id
  - V31__add_customer_profiles.sql: customer_profiles table with unique constraint on (restaurant_id, phone_hash)
  - V32__add_webhook_retry_jobs.sql: webhook_retry_jobs table with indexes on status and next_attempt_at
  - V33__add_customer_privacy_fields.sql: added opted_out and opted_out_at columns to customer_profiles
- **WebhookRetryService Retry Execution**: Added `registerWebhookExecutor()` method for registering type-specific retry handlers. `processPendingRetries()` now actually executes retries via registered executors.
- **InstantSettlementService Easebuzz Integration**: Now calls `SubMerchantService.initiateOnDemandSettlement()` for actual payout instead of returning stub status.
- **PaymentRoutingService Enhanced**: Added actual routing logic with fallback mechanisms, bank/BIN-specific rules, payment method rate tracking, and `selectOptimalPaymentMethod()` endpoint for intelligent method selection based on success rates and amount.
- **ChargebackPreventionService Velocity Checks**: Added 24-hour velocity window to detect multiple chargebacks per phone (max 3 per 24h). Enhanced scoring with very high-value threshold (50K), restaurant history analysis, and recommended actions (BLOCK_AND_REVIEW, MANUAL_REVIEW, ADDITIONAL_VERIFICATION, AUTO_APPROVE).
- **CustomerDataService Privacy Compliance**: Added opt-out/opt-in, data deletion (GDPR/CCPA anonymization), and data export functionality. Customers with `opted_out=true` are excluded from insights and churn risk.
- **TaxComplianceService CSV Export**: Added `generateGstReportCsv()` method for downloadable GST reports with line-item detail and summary section.
- **Build & Tests**: Server compiles successfully, all 133 tests pass.

### 2026-05-19 Session — Sandbox Testing & Marketplace Webhooks
- **All 36 tests passing**: MarketplaceWebhookControllerTest (9), MarketplaceOrderServiceTest (13), EasebuzzIntegrationTest (5), EasebuzzNewFeaturesTest (5), EasebuzzWebhookTest (4)
- **MarketplaceWebhookController.processOrder()** rewritten to parse nested UrbanPiper payload format (`customer.name`, `order.details`, `order.store.merchant_ref_id`, `order.payment[0].option`) instead of flat payload
- **Bug fix**: `EasebuzzApiClient.getTransactionStatus()` now uses `getDashboardBaseUrl()` (was `getPaymentBaseUrl()` — sandbox confirmed `testpay.easebuzz.in/transaction/v2.1/retrieve` returns 404)
- **Sandbox verification**: Hash generation is correct (tested with key `ADNX3KYX5` + salt `Z4UFP4939` against `testdashboard.easebuzz.in`); transaction status API responds with correct JSON
- **KYC API is live-only** (confirmed by Easebuzz support) — cannot be tested on sandbox; all sub-merchant management features may require live mode
- **Server started** on port 8081 with `--spring.profiles.active=sandbox,dev`

### Fixed (Code Review — 2026-05-19)
- **Dev endpoints guarded**: `/auth/signup/dev`, `/auth/signup/dev-admin`, `/auth/dev-reset`, `dev-refresh` all gated behind `@Profile("dev")`
- **Salt removed from debug logs**: `EasebuzzApiClient.generateHash()` logs only hash length, not raw input
- **Post-split retry outside @Transactional**: `PostSplitService.attemptPostSplit()` no longer holds DB connection across `Thread.sleep()`; bill update extracted to `@Transactional` helper
- **Post-split async executor**: Added `postSplitExecutor` bean with `TenantContext` propagation
- **Webhook idempotency**: `EasebuzzWebhookService.handlePaymentWebhook()` skips if bill is already `"paid"`
- **Constant-time hash comparison**: All webhook verifiers use `MessageDigest.isEqual()` instead of `equalsIgnoreCase()`
- **Rate limiter eviction**: `LoginRateLimiter` and `OtpRateLimiter` evict stale bucket entries after 30 min
- **MarketplaceWebhookController**: Replaced `findAll().stream()` with indexed JPA queries by `swiggyStoreId`/`zomatoOutletId`
- **AdminSubMerchantController**: Validates `subMerchantId` is non-blank on assign; `dev-refresh` gated behind `@Profile("dev")`
- **Sandbox config**: Removed hardcoded Easebuzz credentials and stale ngrok URLs; all use env vars now
- **WIRE sandbox URL**: Fixed from `https://testwire.easebuzz.in` to `https://testdashboard.easebuzz.in` (confirmed via ERA)
- **Removed non-existent WIRE endpoint**: `GET /api/v1/merchants/{submerchant_id}/` for lookup-by-ID does not exist per ERA; removed `wireLookupById()` from client, service, controller, and web admin UI
- **`.env.example`**: Added `EASEBUZZ_MERCHANT_KEY`, `EASEBUZZ_SALT`, `EASEBUZZ_PAYMENT_BASE_URL`, `EASEBUZZ_DASHBOARD_BASE_URL`, return/notify URL vars
- **`environment.prod.ts`**: Fixed API base URL from `/api/v1` to `/api/v2`
- **Android EasebuzzPaymentScreen**: Added `LaunchedEffect(Unit)` to call `createOrderWithRetry()` on first composition (was stuck on INITIALIZING)
- **Android payment idempotency**: Skip `createOrder()` if `accessToken` already set
- **Android gateway txns recorded**: `BillingViewModel.finalizeOnlineBill()` and `completeOrder()` now write `gatewayTxnId`/`gatewayStatus` from `_gatewayTxnId`/`_gatewayStatus` StateFlows into `BillPaymentEntity`

### Blocked
- KYC API (`/submerchant/v1/generate_kyc_access_key`) is** live-only** — sandbox returns error, confirmed by Easebuzz support
- Sub-merchant management (`/merchant/v1/submerchant/create/`), split, and refund APIs may also require live mode or manual sandbox feature enablement by Easebuzz Ops

## Key Decisions
- Two separate Split APIs: `/split/v1/create` (one-time bank-to-label linkage) and `/post-split/v1/create/` (per-transaction fund distribution)
- Split labels auto-generated as `sm_{sub_merchant_id}` for consistent convention
- Brand logo SVG deprecated in favour of PNG from Android drawable for visual accuracy
- `!important` in global media queries kept only where Angular Emulated ViewEncapsulation prevents override (`.metric-grid`, `.metric-card`, `.metric-value`, `.metric-icon`, `.stat-card`)
- Google Sign-In button width not hardcoded — instead constrained via CSS `max-width: 100%` for responsive layout

## Next Steps
1. ~~Wire GST/FSSAI lookup UI~~ ✅ Done
2. ~~Fix remaining responsive UI issues~~ ✅ Done
3. ~~Compile server~~ ✅ Done
4. ~~Add sub-merchant KYC aging column~~ ✅ Done
5. ~~Implement OTP APIs (Verify + Resend)~~ ✅ Done
6. ~~Wire Cancel/Payout/Settlement/Split-Retrieve endpoints~~ ✅ Done
7. ~~Add Android Refund Status API~~ ✅ Done
8. ~~Test all Easebuzz sub-merchant APIs against sandbox~~ ✅ Done (KYC = live-only, sub-merchant 500 needs feature enablement)
9. ~~Verify `getRefundStatus()` and `cancelTransaction()` work correctly in sandbox~~ ✅ Done (404 — need feature enablement)
10. ~~Send sandbox support email to Easebuzz — enable sub-merchant/split/refund features~~ ✅ Draft exists (`easebuzz-sandbox-support-email.md`)
11. ~~Create live-mode test plan for KYC, sub-merchant CRUD, split APIs~~ ✅ Done (`easebuzz-live-test-plan.md`)
12. ~~Fix web-admin subscription leaks — add `takeUntilDestroyed()` to 45 `.subscribe()` calls~~ ✅ Committed
13. ~~Commit pending code review fixes & tests~~ ✅ Committed (`e2855a9`)
14. ~~Typed exceptions + webhook HMAC auth + txnid fix + sync hasMore fix~~ ✅ Committed (`05683fc`)
15. ~~Post-V2 Feature Enhancements~~ ✅ Done (payment routing, chargeback velocity, privacy compliance, CSV export, webhook retry execution)
16. Update `sub-merchant-password-reset` flow when Easebuzz ops enables it (blocked)

## Critical Context
- Backend base path: `/api/v2` (dev)
- Easebuzz production: `https://pay.easebuzz.in`, `https://dashboard.easebuzz.in`
- Easebuzz sandbox: `https://testpay.easebuzz.in`, `https://testdashboard.easebuzz.in`
- All hash sequences verified from official Stoplight docs
- Sub-merchant webhook: nested JSON `{"status":"1","data":{"submerchant_id":"...","status":"True/False/Pending"}}`
- Payment webhook: standard Easebuzz transaction data hash-verified
- Android SDK V2: `in.easebuzz:android-v2:1.0.6`, uses `EasebuzzSDK.getInstance().open()` with `PaymentListener`
- `v2` branch remote tracking set to `origin/v2`
- Angular Emulated ViewEncapsulation adds `_ngcontent-xxx` attribute selectors — global responsive overrides need `!important` when competing with component-level `.class` rules
- KYC API is live-only — sandbox returns error, confirmed by Easebuzz support
- Sub-merchant management APIs return 500/400 on sandbox — likely need manual feature enablement by Easebuzz Ops
- All 133 tests passing
- Web-admin has 45 raw `.subscribe()` calls with no cleanup — Angular 18 has `takeUntilDestroyed()` available

## Relevant Files
- `server/.../service/EasebuzzApiClient.java` — 9 API methods (initiate payment, transaction status V2.1, refund, refund status, cancel, payout, settlement, create/update sub-merchant, split label create, post-split create/retrieve, KYC access key, verify OTP, resend OTP)
- `server/.../service/SubMerchantService.java` — create, submit/update to Easebuzz, KYC access key, split label, verify OTP, resend OTP, cancel transaction, payout, settlement, split retrieve
- `server/.../entity/EasebuzzSubMerchant.java` — added `branchName`, `splitLabel`
- `server/.../webadmin/controller/AdminSubMerchantController.java` — 16 endpoints total
- `web-admin/src/styles.css` — global styles (responsive fixes applied)
- `web-admin/src/app/pages/sub-merchants/sub-merchants-page.component.ts` — full CRUD UI with responsive breakpoints
- `web-admin/src/app/core/models/api.models.ts` — `EasebuzzSubMerchant` + `EasebuzzSubMerchantRequest`
- `web-admin/src/app/core/services/admin-api.service.ts` — all sub-merchant API calls
- `web-admin/src/app/layout/sidebar-layout/sidebar-layout.component.ts` — brand logo + 100dvh
- `web-admin/src/app/pages/login/login-page.component.ts` — login with brand logo, responsive Google button
- `web-admin/src/index.html` — SEO meta tags, favicon
- `sub-merchant-apis.md` — complete API reference document
- `web-admin/public/khanabook_logo.png` — brand logo from Android drawable
- `Android/app/.../data/remote/api/KhanaBookApi.kt` — added GST/FSSAI/both lookup endpoints
- `Android/app/.../ui/viewmodel/SettingsViewModel.kt` — added lookup methods + `LookupResult` state
- `Android/app/.../ui/screens/TaxConfigSection.kt` — Fetch buttons + lookup result dialog + apply logic
- `Android/app/.../ui/screens/SettingsScreen.kt` — wired lookup callbacks and state collection
- `server/.../controller/MarketplaceWebhookController.java` — Swiggy/Zomato webhooks (UrbanPiper payload)
- `server/.../test/.../controller/MarketplaceWebhookControllerTest.java` — 9 tests
- `server/.../test/.../service/MarketplaceOrderServiceTest.java` — 13 tests
- `server/.../test/.../service/EasebuzzIntegrationTest.java` — 5 tests
- `server/.../test/.../service/EasebuzzNewFeaturesTest.java` — 5 tests
- `server/.../test/.../service/EasebuzzWebhookTest.java` — 4 tests
- `easebuzz-sandbox-support-email.md` — draft support email for feature enablement
- `easebuzz-live-test-plan.md` — live-mode test plan for KYC/sub-merchant/split APIs

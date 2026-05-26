# KhanaBook v2 — Agent Context

## Goal
Complete the Easebuzz payment gateway integration (sub-merchant split APIs, KYC, brand logo, responsive UI fixes) for the v2 development branch.

## Constraints & Preferences
- Easebuzz API hash sequences must exactly match official Stoplight docs
- Sub-merchant lifecycle managed exclusively via admin web UI
- All changes go to `v2` branch (not `main` which is v1 production)
- KhanaBook brand logo from Android `drawable` used in web admin

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

### 2026-05-26 Session — Platform Dashboard Header & Sub-Merchant Modal Fixes
- **Platform Overview Header**: Wrapped the page title "Platform Overview" and the liveDate subtitle in a `.title-container` inline-flex layout inside `platform-dashboard-page.component.ts` to display them on a single line instead of wrapping to two lines.
- **Sub-Merchant Details View (Modal Conversion)**: Refactored the sub-merchant details layout in `sub-merchants-page.component.ts` from a fixed side panel to a clean overlay modal (`MatDialog` via `#detailDialog` `<ng-template>`). The table now occupies full screen width, improving list scannability.
- **Build & Verification**: Built web-admin using `npm run build` and verified successful compilation.
- **Git**: Committed and pushed changes to remote tracking branch `origin/v2`.

### In Progress
- None currently

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
15. Update `sub-merchant-password-reset` flow when Easebuzz ops enables it (blocked)

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
- All 36 tests passing: 9 marketplace controller + 13 marketplace service + 14 Easebuzz unit tests
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

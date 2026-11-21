# 03 — Feature Inventory

Status vocabulary (as requested): **Implemented**, **Partially implemented**, **Needs fixing**, **Needs improvement**, **Unverified**.

Rule applied throughout: a feature is **not** marked Implemented because a screen or an endpoint exists. `Implemented [C]` means the full path — UI, view model/service, persistence, endpoint — was read and is consistent. `Unverified [U]` means only one layer or none could be confirmed.

## 1. Android POS

| Feature | Status | Evidence | Notes |
| --- | --- | --- | --- |
| Login (phone + password) | Implemented [C] | `feature/auth/ui/LoginScreen.kt`, `AuthViewModel.login` | cited pass on hardware in `DEVICE_SESSIONS` Session 001 |
| Google sign-in | Implemented [C] | `loginWithGoogleToken`, `/auth/google` | |
| Signup + OTP | Implemented [C] | `AuthViewModel` signup flow, `/auth/signup/request` | |
| Change password via OTP | **Needs fixing → fixed** | `ChangePasswordView.kt`, AUD-001 | did not compile on `main`; repaired 2026-11-13. Runtime not exercised. |
| Quick Start onboarding | **Needs fixing** | `QuickStartScreen.kt`, `QuickStartViewModel.kt`, AUD-001/AUD-002 | password field was a call to a non-existent composable; password is stored plaintext and never read |
| Dine-in order + KOT | Implemented [C] | `feature/billing`, `feature/printing` | Session 001 PASS on device |
| Takeaway / quick bill | Unverified [U] | `QuickStartScreen` mentions quick bill; workflow doc absent | listed in `NEXT_ACTIONS.md` item 6 |
| Cash settlement | Implemented [C] | `feature/payments` | Session 001 PASS |
| UPI QR settlement | Implemented [C] | dynamic QR generation | Session 001 PASS |
| Card / split tender | Unverified [U] | `androidTest` mentions part payment | instrumented suite never runs (AUD-007) |
| Daily order numbering + rollover | Implemented [C] | `dailyOrderSequence`, `businessDayRolloverLabel` | midnight crossover still unverified (`NEXT_ACTIONS.md` item 2) |
| Order history + reprint | Implemented [C] | `reports` / `orders` | Session 001 PASS for listing |
| Restaurant configuration | Implemented [C] | `ShopConfigSection.kt` | owner email now editable through OTP (`a733025b`); SMTP delivery unverified (BUG-004) |
| Owner email change with OTP | Partially implemented | `sendEmailUpdateOtp` / `confirmEmailUpdate` | client + server code present; actual email delivery blocked on `MAIL_PASSWORD` |
| Owner WhatsApp change with OTP | Implemented [C] | `confirmMobileNumberUpdate` | |
| App lock / PIN + billing-mode PIN | Implemented [C] | `AppLockViewModel`, `showModePinDialog` | |
| Staff management on POS | Correctly absent [V] | `KNOWN_LIMITATIONS.md` §2 | architecture rule: web-admin only |
| Thermal printing | Partially implemented | `feature/printing` | hardware-dependent; queues to Room when no printer (`KNOWN_LIMITATIONS.md` §3) |
| Offline-first sync | Implemented [C] | `sync`, `MasterSyncProcessor` | conflict/replay behaviour not exercised in this audit |
| FCM notifications | Implemented [C] | `NotificationRepository` | |
| Reports on device | Implemented [C] | `feature/reports` | |

## 2. Web Admin

| Area | Status | Evidence |
| --- | --- | --- |
| Login + session | Implemented [C] | `core/auth/*`, `sessionStorage` tokens |
| Dashboard | **Needs improvement** | 3,901-line component (AUD-009) |
| Business settings | Partially implemented | identity changes must go through OTP (`REQUIREMENT_IMPLEMENTATION_GAPS.md` §1) |
| Menu management | **Needs improvement** | 2,398-line component |
| Orders / sales overview / reports | Implemented [C] | dedicated pages + specs |
| Staff | Implemented [C] | |
| Terminals | Implemented [C] | |
| Inventory | Implemented [C] | |
| Daily closing | Implemented [C] | |
| Support tickets | **Needs improvement** | server-backed but also written to `localStorage` (AUD-010) |
| Platform (businesses, feature flags, system health) | Implemented [C] | `KBOOK_ADMIN` role gate in `SecurityConfig` |
| Test coverage | Partially implemented | 46 spec files; suite green at 350/350 per Session 006, but not gated in CI (`NEXT_ACTIONS.md` item 7) |

## 3. Backend capabilities

| Capability | Status | Evidence |
| --- | --- | --- |
| JWT auth + refresh + blocklist | Implemented [C] | `JwtUtility`, `TokenBlocklist`, `/auth/logout` |
| Role model (OWNER, SHOP_STAFF, KBOOK_ADMIN) | Implemented [C] | `SecurityConfig` matchers + `@RequireRole` aspect |
| Rate limiting (login, OTP, check-user) | Implemented [C] | `isLoginAllowed`, `isOtpAllowed` in `AuthController` |
| Multi-tenancy | **Needs improvement** | application-level only, no ORM filter (AUD-003) |
| Payments + webhooks | Implemented [C] | `EasebuzzWebhookService`, signature verification downstream |
| GST/invoice generation | Implemented [C] | `billing`, `resources/templates/invoice.html` |
| Email templates | Implemented [C] | 6 templates under `resources/templates/email` |
| SMS/WhatsApp OTP delivery | Unverified [U] | provider config external |
| Reporting / commission | Implemented [C] | `AdminCommissionController` returns `findAll()` (AUD-006) |
| Subscription / trials | Implemented [C] | `subscription` module |
| Compliance records | Unverified [U] | module present, no exercising path found in this pass |
| Password reset for web admin (temp token) | Implemented [C] | `WebAdminPasswordResetService` |
| Password reset by loginId | **Needs fixing** | server method `completePasswordReset` has no endpoint (AUD-011); its Android counterpart was removed as dead code |

## 4. Features that look implemented but are not reachable

These are the highest-value items in this inventory because they pass a shallow code review:

1. **`UserService.completePasswordReset(loginId, otp, newPassword)`** — implemented in `UserServiceImpl:115`, declared in the interface, exposed by **no controller**. `/auth/reset-password` still calls `resetPassword(phoneNumber, …)`. So Google/email owners cannot reset by login id. [V]
2. **`SessionManager.getPassword()`** — a stored Google password with **zero readers**. The Quick Start screen demands it before enabling "Start Billing". [V]
3. **Instrumented tests (30 files)** — referenced by `android-ci.yml`, executed with `continue-on-error: true`, and the workflow comment states the suite "has never been executed". Treat every `androidTest` result as absent. [V]

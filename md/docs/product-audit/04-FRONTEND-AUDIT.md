# 04 — Frontend Audit

Covers both client surfaces: `apps/android` (Compose) and `apps/web-admin` (Angular 18).
**Not covered:** rendered appearance. No browser or emulator session was run; visual claims are from code and from the screenshots already stored in `docs/audit/` and `artifacts/android-ui-tests/`.

## 1. Android — strengths [V]

- **Design system is real, not aspirational.** `core/theme` exposes colour roles (`PrimaryGold`, `CardBG`, `BorderGold`, `DangerRed`, `SuccessGreen`, `TextGold`, `TextLight`), `KhanaRadii`, `KhanaBookTheme.spacing/layout/iconSize`. Screens consume tokens rather than literals; `docs/product/DESIGN.md` matches the code.
- **Shared input components** (`ParchmentTextField`, `KhanaTimePickerField`, `KhanaBookDialog`, `KhanaToast`) mean validation, error styling and toasts are consistent by construction.
- **State that must survive recomposition is hoisted deliberately.** `ShopConfigSection.kt` documents *why* success handling moved to `SettingsScreen`'s shared effect — a disposed `LaunchedEffect` was swallowing the first save tap ("Save needs two clicks"). This is the kind of comment that prevents a regression from being reintroduced.
- **Error text is sanitised before display.** Every user-facing backend message passes `UserMessageSanitizer.sanitizeBackendMessage(...)`, so raw stack traces or provider strings do not reach a cashier.
- **Sensitive input uses the right keyboard/transformation**: `KeyboardType.Email/Phone/Number/Password`, `PasswordVisualTransformation` with an explicit visibility toggle, digit filters with `take(10)` / `take(6)`.

## 2. Android — findings

### AUD-002 (P1) — Quick Start password: plaintext, unused, and mandatory [V]

`QuickStartScreen` collects a password for Google-auth owners and `canSubmit` refuses to enable "Start Billing" until it is non-blank. But:

- `QuickStartViewModel:112` calls `sessionManager.savePassword(password)`.
- `SessionManager:124-126` writes it to `SharedPreferences` under `google_password` — no encryption, no Keystore, no hashing.
- `SessionManager:120` `getPassword()` has **no callers anywhere in the app**.
- Nothing in the flow sends it to the server; `/auth/google` and the signup path never see it.
- `QuickStartScreen`'s submit call previously did not even pass `password` to the view model, so the value was collected and dropped (fixed as part of restoring compilation).

Net effect: the app gates onboarding on a credential it stores insecurely and never uses. For a POS device that may be shared with staff, plaintext credentials in app-private storage are recoverable on a rooted or backed-up device.

**Recommendation (no new feature):** pick one honest behaviour and implement only that —
(a) remove the field and the `canSubmit` password condition until a server-side password-set endpoint exists; or
(b) keep it and send it to an existing password-setting endpoint, with `EncryptedSharedPreferences` for storage.
Option (a) is the smallest and matches "do not build more software until what exists is correct". Requires product-owner sign-off because it changes an onboarding rule.

### AUD-001 (P1) — `main` did not compile [V]

Commit `8a600882` introduced three independent breaks that all shipped to `main`:

| File | Break |
| --- | --- |
| `feature/auth/ui/ChangePasswordView.kt:77` | `when {}` missing `->` — hard syntax error |
| `feature/auth/ui/ChangePasswordView.kt:213-218` | `otpTimer` / `canResendOtp` referenced but never declared |
| `feature/auth/viewmodel/AuthViewModel.kt:485` | a function pasted inside `sealed class ResetPasswordResult`, calling a repository method that does not exist |
| `feature/settings/ui/QuickStartScreen.kt:203` | call to `PasswordField(...)`, a composable never written |

Repaired 2026-11-13 (see `15-IMPLEMENTATION-CHECKLIST.md`). The process failure matters more than the code failure: see `12-TESTING-AUDIT.md` §3.

### AUD-012 (P2) — `ShopConfigSection.kt` is a 973-line composable [V]

One function (`ShopConfigView`) holds: profile form state, logo upload + two-stage cache fallback, two independent OTP flows each with its own timer/status/field errors, an unsaved-changes `BackHandler`, a PIN dialog, payment-flow selection, and save orchestration (~25 `remember`ed values). The email/WhatsApp OTP duplication is copy-paste: `otpStatus`/`emailOtpStatus`, `otpTimer`/`emailOtpTimer`, `otpSent`/`emailOtpSent`, `isOtpVerified`/`isEmailOtpVerified`.

**Risk:** the two flows will drift. The email flow already differs (it verifies via `sendEmailUpdateOtp`, the phone flow via `sendOtp(...,"update_whatsapp")`), and a fix applied to one will miss the other.

**Recommendation:** extract an `OtpFieldController` (state holder, not a screen) and a `OtpVerificationField` composable, then rewire both flows to it. No UI change, no new capability.

### AUD-013 (P3) — behavioural rules live in the composable [V]

Save validation (`numberChanged && !isOtpVerified`, `emailChanged && !isEmailOtpVerified`, email format) is inline in `onClick`. Nothing tests it. Moving it to a pure function next to `ValidationUtils` makes it unit-testable without Compose.

## 3. Web Admin — findings

### Strengths [V]

- **Token handling is deliberate.** `TokenStorageService` uses `sessionStorage`, not `localStorage`, with a comment explaining the XSS rationale; the refresh token is stored only so logout can revoke it and is "never used to refresh".
- **Design-system enforcement is scripted**, not documented: ten `npm run check:*` scripts (`check:colors`, `check:contrast`, `check:spacing`, `check:type`, `check:tokens`, `check:motion`, `check:breakpoints`, `check:z-index`, `check:accent-budget`, `check:inline-styles`). This is the strongest single practice in the repository — style regressions fail a command instead of a review.
- **Angular 18 signals** are used for session state rather than manual `BehaviorSubject` plumbing.
- **Resilience specs exist** for reload/refresh and responsive layout (`refresh-reload.spec.ts`, `responsive-layout.spec.ts`).

### AUD-009 (P3) — god-components [V]

| File | Lines |
| --- | --- |
| `pages/business-dashboard/business-dashboard-page.component.ts` | 3,901 |
| `pages/menu/menu-page.component.ts` | 2,398 |
| `pages/business-settings/business-settings-page.component.ts` | 1,695 |
| `pages/login/login-page.component.ts` | 1,464 |
| `pages/help/help-support-page.component.ts` | 1,462 |

A 3,901-line component cannot be reviewed, diffed or reasoned about reliably, and its spec will assert on implementation detail rather than behaviour.

**Recommendation:** extract the dashboard's data derivation into a service and its tab bodies into child components, one tab at a time, keeping the existing spec green after each move. Do not redesign.

### AUD-010 (P3) — support tickets double-persisted [V]

`support-tickets-page.component.ts:276,287` reads/writes `localStorage['kbook_admin_support_tickets']` in addition to the API. Consequences: stale tickets render for a different admin on a shared console, cleared server-side tickets reappear from the browser, and the entry grows without bound.

**Recommendation:** if it is a cache, key it per user and clear on logout; if it is a fallback, delete it. Code path reads like a cache, so per-user keying plus a clear-on-logout is the minimal correct change.

## 4. Frontend recommendations, ordered

1. Resolve AUD-002 (product decision, smallest honest option).
2. De-duplicate the two OTP flows in `ShopConfigSection.kt` (AUD-012) — highest regression risk per line changed.
3. Extract dashboard/menu logic (AUD-009) — no behaviour change, tested by existing specs.
4. Gate the existing `check:*` scripts and `ng test` in CI (see `12`) — the scripts already exist; only the pipeline step is missing.
5. Bound or remove the localStorage ticket cache (AUD-010).

# Staff Creation & User-Identity / Password Flow Analysis — 2026-09-12

Analysis + recommendation only. No code was changed. Every claim is cited to `file:line`.
Repo root: `C:\Users\nandh\Desktop\Khanabook\KhanaBook`.

---

## 1. Findings Table

| # | Area | Current behavior | Evidence (file:line) | Risk |
|---|------|------------------|----------------------|------|
| A1 | Staff creation — no shared secret | Staff created with `BCrypt(random UUID)` hash; no temp password generated or returned | `BusinessWriteService.java:71` (`passwordEncoder.encode(UUID.randomUUID().toString())`) | none |
| A2 | Staff creation — auto OTP onboarding | After save, `passwordResetOtpService.issueOtp(phone)` is auto-issued; `otpSent` returned | `BusinessWriteService.java:101-110`, `StaffCreatedResponse.java:3-9` | none |
| A3 | Staff creation — OTP send failure is non-fatal | OTP failure is caught, logged, `otpSent=false`; staff still created; web-admin shows "Resend code" | `BusinessWriteService.java:104-109`, `staff-form-modal.component.ts:57-64` | low |
| A4 | Account active immediately, no `passwordInitialized` flag | `user.setIsActive(true)` at creation; there is no flag distinguishing "password never set" from "set". Login is blocked only because the hash is unguessable, not by an explicit state | `BusinessWriteService.java:80`, `User.java` (no such column) | low |
| A5 | Duplicate-phone handling | Rejects if `existsByPhoneNumber` OR `existsByLoginId` → `DuplicateStaffPhoneException` | `BusinessWriteService.java:59-64` | none |
| A6 | Soft-deleted number reuse (staff create) | `createStaff` checks `existsByPhoneNumber`/`existsByLoginId` which are NOT `isDeleted`-aware. Unlike signup (`findActiveByAnyIdentifier` + `releaseIdentifierFromDeletedUsers`), staff create has no tombstone-release path, so a soft-deleted staff row can permanently block re-adding that phone | `BusinessWriteService.java:59-64` vs `AuthServiceImpl.java` `ensurePhoneNumberAvailableForSignup` / `releaseIdentifierFromDeletedUsers` | med |
| A7 | OTP challenge key is per-phone, not per-account | `issueOtp` keys on `password-reset:<phone>`. Fine while phone==loginId for PHONE staff | `PasswordResetOtpService.java:54-56,95` | none |
| B1 | Two `whatsappNumber` fields exist | `User.whatsappNumber` (per person) vs `RestaurantProfile.whatsappNumber` (shop contact) | `User.java` (`whatsapp_number`), `RestaurantProfile.java` (`whatsapp_number`) | n/a |
| B2 | `loginId` is the true per-account identifier | `login_id` is `nullable=false, unique=true`; JWT subject = `getLoginIdentifier(user)` = `loginId`; server `/auth/change-password` resolves `principal.getName()` | `User.java` (`login_id` col), `AuthServiceImpl.java` `getLoginIdentifier`, `AuthController.java:185` | n/a |
| B3 | Change Password targets phone, not loginId | `initialPhone = currentUser?.phoneNumber ?: currentUser?.whatsappNumber ?: ""`; reset is sent to this phone. `loginId` never consulted | `ChangePasswordView.kt:78`, and `:96-101` (`accountPhone` fallback also phone→whatsapp only) | **high** |
| B4 | Profile card prefers SHOP number over user number | `displayPhone = profile?.whatsappNumber ?: user?.whatsappNumber` — shop contact shadows the person's own number in the UI | `SettingsSharedComponents.kt:54` | med |
| B5 | Google user has blank phone AND blank whatsapp | Server google path: `user.setWhatsappNumber(null)`, `phoneNumber` never set, `loginId=email` | `AuthServiceImpl.java` google `orElseGet` branch (`setWhatsappNumber(null)`, `setLoginId(email)`) | **high** |
| B6 | Android Google login passes no fallbackPhone | `remoteGoogleLogin` calls `upsertAuthenticatedUser(... authProvider="GOOGLE", googleEmail=userEmail)` with `fallbackPhone` omitted → `phoneNumber=null`, `whatsappNumber=null` | `UserRepository.kt` `remoteGoogleLogin` (no `fallbackPhone` arg) vs `remoteLogin`/`remoteSignup` (which pass it) | **high** |
| B7 | Change Password unusable for Google account | With B5+B6, `initialPhone=""` → Send OTP button disabled (`enabled = ... phone.trim().isNotBlank()`); Google owner cannot change password in-app | `ChangePasswordView.kt:78,96-101`, button `enabled` guard | med |
| B8 | Reset resolves account by phone-ish identifiers | `findUserByLoginId` tries phone→loginId→email→whatsapp; a PHONE staff typing their 10-digit number resolves fine; a Google user typing a phone will NOT match (their loginId is an email) | `AuthServiceImpl.java` `findUserByLoginId`, `resetPassword` | low |
| C1 | Forgot-password is OTP-gated | `ForgotPasswordDialog` → `sendOtp("reset")` → `resetPassword(phone, otp, newPassword)` | `ForgotPasswordDialog.kt` (step machine), `UserRepository.kt` `remoteResetPassword` → `api.resetPassword` | none |
| C2 | Change-password (in-app) is OTP-gated & bound to logged-in phone | `ChangePasswordView` reuses the reset flow; phone field is read-only, locked to the signed-in user's phone | `ChangePasswordView.kt:120-127` (readOnly), `:96-101` | low (see B3) |
| C3 | Server `resetPassword` validates OTP, rejects new==old, revokes tokens | `validateOtpOrThrow`; `if (matches(newPassword, hash)) throw`; `setTokenInvalidatedAt`; `refreshTokenRepository.revokeAllForUser` | `AuthServiceImpl.java` `resetPassword` | none |
| C4 | Separate non-OTP `/auth/change-password` exists on server | `POST /change-password` → `changePassword(principal.getName(), current, new)`; validates current pw, rejects new==old, revokes tokens | `AuthController.java:180-186`, `AuthServiceImpl.java:404` | none |
| C5 | Android cannot reach `/auth/change-password` | No Retrofit binding for `change-password` in `KhanaBookApi.kt` (only `reset-password` + `reset-password/request`) | `KhanaBookApi.kt` auth block | none (by design) |
| D1 | Staff count unlimited | No cap in `createStaff`; only role validation | `BusinessWriteService.java:56-116` | none |
| D2 | Active terminals capped at 5 | `MAX_ACTIVE_TERMINALS = 5`, enforced on activate/approve/reactivate | `TerminalManagementService.java:31,337,352,528`, `BusinessWriteService.java:272` | none |
| D3 | New-device → owner-approval flow | `createOrReuseRegistrationRequest` → `notifyOwners("New Device Request")`; approve/reject via web-admin terminals page | `TerminalManagementService.java:162,211`, `TerminalController.java:304,379` | none |

---

## 2. Simple-but-Competitive Design Recommendation

### Competitive framing
Petpooja / Arow-style POS onboarding: **owner adds staff → staff self-onboards with their own credential → fast PIN switching on a shared terminal.** KhanaBook already has most of the machinery:
- OTP self-onboarding (A2) — matches "staff sets own password."
- Per-person `loginId` (B2) — matches "per-person credential."
- `pinHash`/`pinSetAt` on `User` + `pinLogin`/`setPin` on the server (`AuthServiceImpl.setPin`, `AuthServiceImpl.pinLogin`) — the fast-switch PIN primitive **already exists server-side.**

So the competitive gap is not missing features; it is **identity-resolution correctness** and **exposing the PIN switch in the app.** Keep the redesign small.

### Ranked recommendations

| Rank | Change | Size | Files touched |
|------|--------|------|---------------|
| **Do-Now** | Fix Change Password/OTP to resolve the account's **own** identifier (see §3). Prefer `loginId`; only use phone when `authProvider==PHONE`. Never fall back to `RestaurantProfile.whatsappNumber`. | S | `Android/.../applock/ChangePasswordView.kt` |
| **Do-Now** | For Google accounts, route in-app password change through the existing server `/auth/change-password` (current+new), OR hide the Change-Password entry point when the account has no phone and is Google-auth — so the flow is never silently disabled. Add the Retrofit binding only if you choose the change-password route. | S–M | `Android/.../applock/ChangePasswordView.kt`, `Android/.../data/remote/api/KhanaBookApi.kt` (only if adding binding) |
| **Do-Now** | Stop `RestaurantProfile.whatsappNumber` from shadowing the person's number in the profile card: prefer `user.whatsappNumber ?: user.phoneNumber` for the *account* line; keep shop number only where "shop contact" is meant. | S | `Android/.../ui/screens/SettingsSharedComponents.kt` |
| **Soon** | Make staff-create tombstone-aware for soft-deleted phones: reuse `findActiveByAnyIdentifier` + `releaseIdentifierFromDeletedUsers` (already in `AuthServiceImpl`) so a re-added staff phone isn't permanently blocked. | M | `server/.../webadmin/service/BusinessWriteService.java` (+ reuse existing `UserRepository` queries) |
| **Soon** | Expose the **existing** PIN fast-switch in the Android app (set PIN in Settings, PIN-login on shared terminal). Server endpoints already exist (`setPin`, `pinLogin`); this is UI + one Retrofit binding, no schema churn. | M | `Android/.../data/remote/api/KhanaBookApi.kt`, a new Compose PIN screen, `AuthViewModel` |
| **Later** | Add an explicit `passwordInitialized` boolean on `User` so "never set a password yet" is a first-class state (better UX copy, safer than relying on an unguessable hash). Schema migration required. | L | `server/.../entity/User.java` + Flyway migration + `BusinessWriteService`, Android `UserEntity` + migration |

Everything in Do-Now/Soon **reuses existing endpoints** and needs **no schema change**. The only schema churn is the optional Later item.

---

## 3. The Single Most Important Correctness Fix

**Problem.** In-app Change Password targets the wrong identifier and can fall back to a shared/shop-adjacent number or be silently disabled.

- `ChangePasswordView.kt:78` — `val initialPhone = currentUser?.phoneNumber ?: currentUser?.whatsappNumber ?: ""`
- `ChangePasswordView.kt:96-101` — the lock-in `LaunchedEffect` again uses `phoneNumber ?: whatsappNumber`, never `loginId`.
- For a **Google** account both are blank (`AuthServiceImpl` google branch sets `whatsappNumber=null` and never sets `phoneNumber`; `UserRepository.remoteGoogleLogin` passes no `fallbackPhone`), so `initialPhone=""` → the Send-OTP button is disabled and the user is stuck.
- `SettingsSharedComponents.kt:54` shows the pattern of preferring a shop-level number (`profile.whatsappNumber`) over the user's own — the same shadowing hazard.

**The reliable per-account identifier is `loginId`** (`User.java` `login_id` is `nullable=false, unique=true`; it is the JWT subject via `AuthServiceImpl.getLoginIdentifier`; the server's own `/auth/change-password` resolves the account from `principal.getName()` at `AuthController.java:185`).

**Minimal change shape (Do-Now, size S), in `ChangePasswordView.kt`:**

```kotlin
// Resolve the account's OWN reset identifier. Prefer the account identity
// (loginId), and only use phone when this is a PHONE-auth account. Never fall
// back to the shared RestaurantProfile number.
val accountResetId = when {
    currentUser?.authProvider.equals("PHONE", ignoreCase = true) ->
        currentUser?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: currentUser?.loginId
    else -> currentUser?.loginId          // Google/email accounts
}?.takeIf { it.isNotBlank() }
```

Then bind `phone`/`initialPhone` to `accountResetId` (replacing `:78` and the `:96-101` effect), and pass it to `authViewModel.sendOtp(...)` / `resetPassword(...)`.

Because the server's `findUserByLoginId` already resolves `loginId`/`email`/`phone`/`whatsapp` (`AuthServiceImpl.findUserByLoginId`), sending the OTP against `loginId` works for both PHONE and Google accounts with no server change. For Google accounts whose `loginId` is an email, the OTP channel (WhatsApp) has no phone target — so pair this with the Do-Now Google item: for non-PHONE accounts, route in-app change through the existing `/auth/change-password` (current+new password) instead of the OTP reset, or hide the OTP path.

**Net:** one small Android edit makes password change bind to the account's own identity and stops any shared/shop-number fallback; the Google case is closed by reusing an endpoint that already exists on the server.

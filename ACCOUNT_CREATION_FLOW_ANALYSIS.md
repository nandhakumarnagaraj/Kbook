# ACCOUNT CREATION FLOW ANALYSIS

**Scope:** Phase 2 — every path that creates an account, traced from UI to database.
**Mode:** READ-ONLY audit. Nothing was modified.
**Date:** 2026-11-17

Every path is documented with the twelve required attributes. `[VERIFIED]` = read from source,
`[REPRODUCED]` = observed against a real server + PostgreSQL 16 in an isolated environment,
`[SUSPECTED]` = reachable by code path but not exercised.

---

## 0. Lifecycle diagram

```mermaid
flowchart TD
  subgraph Clients
    A1[Android SignUpScreen]
    A2[Android LoginScreen + Google SDK]
    A3[Android LoginScreen password]
    W1[Web admin login page]
    W2[Web admin /business/staff]
    W3[KBOOK_ADMIN /admin/businesses]
    D1[Android POS master-sync worker]
  end

  subgraph API["/api/v1"]
    E1["POST /auth/signup/request"]
    E2["POST /auth/signup"]
    E3["POST /auth/google"]
    E4["POST /auth/login"]
    E5["POST /business/staff"]
    E6["POST /admin/businesses"]
    E7["POST /sync/config/users/push"]
    E8["POST /sync/config/users/update-mobile|email"]
  end

  subgraph Service
    S1[AuthServiceImpl.signup]
    S2[AuthServiceImpl.googleLogin]
    S3[AuthServiceImpl.login]
    S4[BusinessWriteService.createStaff]
    S5[AdminWriteService.createBusiness]
    S6["GenericSyncService.handlePushSync<br/>+ UserProfileSyncService"]
    S7[UserServiceImpl]
  end

  subgraph DB[(PostgreSQL)]
    T1[(users)]
    T2[(restaurantprofiles)]
    T3[(otp_requests)]
    T4[(subscription)]
    T5[(staff_permissions)]
  end

  A1 --> E1 --> S1
  A1 --> E2 --> S1
  A2 --> E3 --> S2
  A3 --> E4 --> S3
  W1 --> E4
  W2 --> E5 --> S4
  W3 --> E6 --> S5
  D1 --> E7 --> S6
  W2 --> E8 --> S7

  S1 --> T2
  S1 --> T4
  S1 --> T1
  S1 --> T3
  S2 --> T2
  S2 --> T4
  S2 --> T1
  S3 --> T1
  S4 --> T1
  S4 --> T5
  S5 --> T2
  S5 --> T4
  S5 --> T1
  S6 --> T1
  S7 --> T1
  S7 --> T2
```

**Lifecycle in one line:** identity is created at `users.insert`; the tenant is created at
`restaurantprofiles.insert`; the two are linked only by the shared value of `restaurant_id`. There is
no identity-resolution step shared by all paths — each path re-implements its own.

---

## PATH 1 — Android phone signup (the primary path)

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | OTP request, then signup |
| 2 | Screen | `apps/android/.../feature/auth/ui/SignUpScreen.kt` |
| 3 | API | `POST /api/v1/auth/signup/request`; `POST /api/v1/auth/signup` |
| 4 | Controller | `AuthController.requestSignupOtp:139`, `AuthController.signup:131` |
| 5 | Service | `AuthServiceImpl.requestSignupOtp:250`, `AuthServiceImpl.signup:114` |
| 6 | Repository | `UserRepository.findActiveByAnyIdentifier`, `findDeletedHoldingIdentifier`, `saveAndFlush`; `RestaurantProfileRepository.findLiveShopsForDevice`, `save` |
| 7 | Tables | `otp_requests`, `restaurantprofiles`, `subscription*`, `users`, `refresh_tokens` |
| 8 | Identity matching | `PhoneNormalizer.normalize` → `findActiveByAnyIdentifier(normalized)`; the query is `isDeleted=false AND (phoneNumber OR loginId OR email OR whatsappNumber = :identifier)` |
| 9 | Existing-account handling | **Rejected** with `IllegalArgumentException("This number is already registered.")` at `AuthServiceImpl:577-586`, before the OTP is even issued (`requestSignupOtp` calls the same guard) |
| 10 | Error handling | 400 via `GlobalExceptionHandler.handleIllegalArgument`; DB race surfaces as 409 via `handleDataIntegrityViolation` |
| 11 | Transaction | `@Transactional` on `signup`. Profile + subscription + user + refresh token in one unit. Verified: a failed insert leaves **no orphan profile** `[REPRODUCED]` |
| 12 | Related entities | `restaurantprofiles` stub (`local_id=1`, `shop_name="<name>'s Restaurant"`), trial subscription, `refresh_tokens`, one `users` row with `email=NULL`, `login_id = phone_number = whatsapp_number = <phone>`, `role=OWNER` |

Reproduced behaviour `[REPRODUCED]`:

```
POST /auth/signup        → 200, restaurantId 2947293429186540726, loginId 9812345670, userEmail null
repeat signup (same no.) → 400 {"error":"This number is already registered."}
repeat signup/request    → 400 {"error":"This number is already registered."}
+91 / 0-prefixed forms   → 400 {"fields":{"phoneNumber":"Phone number must be exactly 10 digits"}}
```

Client-side: `AuthViewModel.signUp()` has **no in-flight guard**; double-submit is prevented only by
`enabled = !isLoading` plus a full-screen `KhanaBookLoadingOverlay`
(`SignUpScreen.kt:90,496,530`). Between the first click and the first recomposition a second click can
still enqueue a second coroutine. Harmless for data (the server rejects the duplicate as above) but it
consumes the 5-req/min auth budget (see RC-6).

---

## PATH 2 — Android / web Google sign-in

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Google account chooser returns an ID token |
| 2 | Screen | `LoginScreen.kt` → `AuthViewModel.loginWithGoogleToken` → `UserRepository.remoteGoogleLogin` |
| 3 | API | `POST /api/v1/auth/google` |
| 4 | Controller | `AuthController.loginWithGoogle:144` |
| 5 | Service | `AuthServiceImpl.googleLogin:259` |
| 6 | Repository | `findByLoginIdIgnoreCase` → `findByEmailIgnoreCase` → `findByGoogleEmailIgnoreCase`; then `saveAndFlush` |
| 7 | Tables | `users`, `restaurantprofiles`, `subscription*`, `refresh_tokens` |
| 8 | Identity matching | **Email only.** `AuthServiceImpl.java:270-272`. No `findByPhoneNumber`, no `findByWhatsappNumber` |
| 9 | Existing-account handling | If found: activate, attach `google_email` if empty, backfill `login_id` if empty, set `auth_provider=GOOGLE`, set `device_id="server"`, return a token. If **not** found: **create a brand-new tenant + OWNER account** (`orElseGet`, lines 291-357) |
| 10 | Error handling | Invalid/absent token → 400 `"Invalid Google ID token."` / `"Google login failed."`; verifier not configured → 400 |
| 11 | Transaction | `@Transactional`; the `orElseGet` branch writes profile + subscription + user |
| 12 | Related entities | On the create branch: new `restaurantprofiles` row, trial subscription, user with `email = login_id = google_email = <gmail>`, `whatsapp_number = NULL`, `password_hash="GOOGLE_AUTH"`, `role=OWNER` |

**This is the duplicate-account mechanism.** A human who signed up by phone has
`email=NULL, google_email=NULL, login_id=<phone>`. The three lookups all miss (reproduced below), so the
`orElseGet` branch mints a **second account in a second tenant**:

```
[REPRODUCED] lookups performed by googleLogin for a phone-registered human:
  lookup loginId     -> 0 rows
  lookup email       -> 0 rows
  lookup googleEmail -> 0 rows
[REPRODUCED] inserts performed by the orElseGet branch succeed (all five unique indexes allow them):
  accounts for one human            -> 2
  distinct tenants for one human    -> 2
```

Mitigating factor `[VERIFIED]`: the same branch calls `rejectSecondShopOnSameInstall(deviceId)`, which
refuses a second live shop **on the same `device_id`**. `SessionManager.getDeviceId()` prefers
`ANDROID_ID` (stable across reinstall) and falls back to a random `dev_xxxxxxxx`
(`SessionManager.kt:85-111`). So the split is blocked on the *same handset* but not on a second handset,
a tablet, or any install whose `ANDROID_ID` is unavailable/blank (the `9774d56d682e549c` fallback).
The guard keys on hardware, not on person.

Note also: `deviceId` is mandatory in `SignupRequest` but **optional** in `GoogleLoginRequest`, and the
Google branch defaults a missing one to the literal `"web-admin"` — which `rejectSecondShopOnSameInstall`
skips as a sentinel.

---

## PATH 3 — Password login (`POST /auth/login`)

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Login form |
| 2 | Screen | `LoginScreen.kt` / `apps/web-admin/src/app/pages/login/login-page.component.ts` |
| 3 | API | `POST /api/v1/auth/login` |
| 4 | Controller | `AuthController.login:90` |
| 5 | Service | `AuthServiceImpl.login:73` |
| 6 | Repository | `findByLoginIdIgnoreCase` → `findByEmailIgnoreCase` → `findByPhoneNumber` → `findByWhatsappNumber` |
| 7 | Tables | `users` (read), `refresh_tokens` (write) |
| 8 | Identity matching | Four sequential single-result lookups, `AuthServiceImpl.java:76-82` |
| 9 | Existing-account handling | Found → BCrypt verify → `is_active` → suspension → surface guard (`surface="web"` + `SHOP_STAFF` → 403) → token |
| 10 | Error handling | Unknown id or bad password → 400 `"Invalid login ID or password"` (same message — good); disabled → 400; suspended → 403 `BUSINESS_SUSPENDED`; `IncorrectResultSizeDataAccessException` → **500** (RC-2) |
| 11 | Transaction | Not transactional (read + refresh-token insert) |
| 12 | Related entities | `refresh_tokens` row; access token with subject = `login_id` |

`[REPRODUCED]` login with `+91…` → **400 validation**, never reaches the normalizer (RC-4).

---

## PATH 4 — Soft-deleted account takeover of a number (re-registration)

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Signup with a number previously used by a soft-deleted account |
| 2–7 | as PATH 1 | |
| 8 | Identity matching | `findDeletedHoldingIdentifier` (phone/login_id/whatsapp only — **not `email`**) |
| 9 | Existing-account handling | The deleted row is *detached*, not reused: `phone_number=NULL`, `whatsapp_number=NULL`, `login_id += "\|deleted:<id>"` (`AuthServiceImpl:596-621`) |
| 10 | Error handling | none specific |
| 11 | Transaction | same as signup |
| 12 | Related entities | New tenant; old tenant keeps all its history, now with an owner row that has no usable identifier |

`[VERIFIED]` Consequence: the deleted account can never be signed into again (its `login_id` is
tombstoned), and nothing records that the old and new accounts are the same human.

---

## PATH 5 — Owner mobile number change (identity mutation, not creation)

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Settings → change mobile |
| 2 | Screen | Android settings (`ChangePasswordView.kt` family) / web business settings |
| 3 | API | `POST /sync/config/users/update-mobile/request`, `POST /sync/config/users/update-mobile` |
| 4 | Controller | `UserController` (OWNER only per `SecurityConfig`) |
| 5 | Service | `UserServiceImpl.requestMobileNumberUpdateOtp`, `confirmMobileNumberUpdate` |
| 6 | Repository | `findByLoginId`, `findByEmail`, `findByWhatsappNumber`, `findByPhoneNumber` (all exact, case-sensitive) |
| 7 | Tables | `otp_requests` (`mobile-update:<tenantId>`), `users`, `restaurantprofiles` |
| 8 | Identity matching | Primary user of the tenant (`local_id=1`, else first OWNER) |
| 9 | Existing-account handling | `ensureMobileNumberAvailable` blocks a number tied to another shop / another account |
| 10 | Error handling | 400 `"This number is already related to another shop."` / `"...another account."` |
| 11 | Transaction | `@Transactional`; notification registered `afterCommit` |
| 12 | Related entities | **`email` is overwritten with the phone number** when `auth_provider=PHONE` (`UserServiceImpl.java:206-212`); profile `whatsapp_number` updated and field-revision stamped |

`[VERIFIED]` This is the origin of "phone numbers living in the email column", which is why the
`email` column cannot be treated as a mailbox anywhere in the system.

---

## PATH 6 — Owner email change

| # | Attribute | Value |
| :-- | :-- | :-- |
| 3 | API | `POST /sync/config/users/update-email/request`, `.../update-email` |
| 5 | Service | `UserServiceImpl.requestEmailUpdateOtp` / `confirmEmailUpdate` |
| 8 | Identity matching | tenant primary user |
| 9 | Existing-account handling | `ensureEmailAvailable` checks `findByEmail` and `findByLoginId` — **exact match only**, so a case-variant duplicate passes and later breaks login (RC-2) |
| 12 | Related entities | `login_id` moved only if it already contains `@`; profile `email` updated |

---

## PATH 7 — Staff creation ("employee invitation")

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Owner adds a staff member |
| 2 | Screen | Web admin staff page / Android staff management |
| 3 | API | `POST /api/v1/business/staff` |
| 4 | Controller | `BusinessAdminController:141` |
| 5 | Service | `BusinessWriteService.createStaff:158` |
| 6 | Repository | `findActiveByAnyIdentifier`, `findDeletedHoldingIdentifier`, `save` |
| 7 | Tables | `users`, `staff_permissions` / `role_templates`, `otp_requests`(none) |
| 8 | Identity matching | phone normalized, then `findActiveByAnyIdentifier(phone)` |
| 9 | Existing-account handling | `DuplicateStaffPhoneException` → **409 `PHONE_NUMBER_EXISTS`** — the only path with a clean, machine-readable duplicate response |
| 10 | Error handling | 409 as above; a credentials-send failure is logged and swallowed (`credentialsSent=false`) |
| 11 | Transaction | `@Transactional` for the user+permissions write; the WhatsApp send is deliberately outside the rollback contract |
| 12 | Related entities | `users` row (`auth_provider=PHONE`, generated BCrypt password, `device_id="web-admin"`, `local_id=System.currentTimeMillis()`), default read-only permissions |

`[VERIFIED]` `CreateStaffRequest.email` carries **no validation and no normalization**
(`feature/business/dto/CreateStaffRequest.java`), and is written verbatim at
`BusinessWriteService.java:183`. A staff email of `RAVI@GMAIL.COM` and an owner email of
`ravi@gmail.com` are two different `uk_users_email` values — this is the injection point for RC-2.

`[VERIFIED]` No invitation token exists. The "invite" is a generated password delivered over WhatsApp;
there is no acceptance step, and no proof that the invited person controls the phone.

---

## PATH 8 — KBOOK_ADMIN business provisioning

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | Admin console "create business" |
| 2 | Screen | Web admin dashboard |
| 3 | API | `POST /api/v1/admin/businesses` |
| 4 | Controller | `AdminDashboardController:74` (`@RequireRole(KBOOK_ADMIN)`) |
| 5 | Service | `AdminWriteService.createBusiness:61` |
| 6 | Repository | `findActiveByAnyIdentifier`, `findDeletedHoldingIdentifier`, `profileRepository.findByRestaurantId` (id collision loop), `saveAndFlush` |
| 7 | Tables | `restaurantprofiles`, `subscription*`, `users`, `categories`, `security_audit_log` |
| 8 | Identity matching | `findActiveByAnyIdentifier(request.getOwnerPhone().trim())` — **no `PhoneNormalizer`** |
| 9 | Existing-account handling | `IllegalArgumentException("Phone number already in use by another active user: …")` |
| 10 | Error handling | 400 for the pre-check; a digit-variant phone that slips past the pre-check hits `ux_users_phone_digits` and surfaces as a generic **409** `"A data conflict occurred…"` |
| 11 | Transaction | `@Transactional`, incl. `subscriptionService.initializeTrialSubscription` |
| 12 | Related entities | `restaurant_id` chosen as `100000 + rand(0..899999)` (6 digits, unlike signup's UUID), owner user with `email` from the request **trimmed only**, 3 seed categories |

---

## PATH 9 — Device-driven user creation over sync

| # | Attribute | Value |
| :-- | :-- | :-- |
| 1 | Entry point | POS master-sync push of a `users` row |
| 2 | Screen | none (background `SyncManager` worker) |
| 3 | API | `POST /api/v1/sync/config/users/push` |
| 4 | Controller | sync controller (`hasAnyRole(OWNER, SHOP_STAFF)`) |
| 5 | Service | `UserServiceImpl.pushData` → `GenericSyncService.handlePushSync:284` |
| 6 | Repository | `SyncRepository` (generic), `UserProfileSyncService.findExistingUserByIdentity` |
| 7 | Tables | `users` |
| 8 | Identity matching | server id, then `login_id`, `email`, `google_email`, `whatsapp_number` — **all scoped to the caller's tenant** (`UserProfileSyncService:32-77`) |
| 9 | Existing-account handling | matched row → LWW merge with `mergeUserFields` preserving server-owned fields; unmatched row → **new account** |
| 10 | Error handling | `BusinessRuleException` (`SYNC_LOGIN_CONFLICT`, `SYNC_EMAIL_CONFLICT`, `SYNC_WHATSAPP_CONFLICT`) — but only for *changed* identity fields of an existing row |
| 11 | Transaction | inside `handlePushSync` batch transaction |
| 12 | Related entities | `users` only; non-admin callers are forced to `role=OWNER` (`enforceNewUserRoleForCaller`), and a non-OWNER caller is refused outright |

`[VERIFIED]` `enforceNewUserRoleForCaller` returns `false` for non-owners so the record is quarantined —
this closes a previously documented tenant-takeover. The remaining risk is *duplicate creation by an
owner's own device*: uniqueness is checked only when an identity field **changes on an existing row**;
a brand-new row is inserted without any `existsBy…` pre-check.

---

## PATH 10 — Dev-only endpoints

`[VERIFIED]` `POST /auth/signup/dev` and `POST /auth/signup/dev-admin` are annotated `@Profile("dev")`.
They bypass OTP and create accounts (and `devAdminSignup` mints a `KBOOK_ADMIN`). They are absent from
a default-profile server (`[REPRODUCED]`: not routed on the audit instance, which ran without `dev`).
`AuthServiceImpl.devReset()` deletes **all** users, profiles and refresh tokens — also dev-time.

---

## Paths explicitly checked and found not to create accounts

| Candidate | Finding |
| :-- | :-- |
| Web-admin signup | Does not exist. The login page links to the marketing site `[VERIFIED]` |
| Onboarding (`/business/onboarding/*`) | Read-only progress + prefill `[VERIFIED]` |
| Background jobs | The auth/staff/platform packages contain no account-creating `@Scheduled`/`@Async` work; the schedulers found are OTP cleanup and token-blocklist cleanup `[VERIFIED]` |
| Payment/webhook consumers | Touch bills/sub-merchants, not `users` `[VERIFIED]` (no `User` writes in `feature/payments`) |
| Refresh / heartbeat / logout | Session only `[VERIFIED]` |

---

## Reproduction matrix (isolated environment)

Environment: throwaway `postgres:16-alpine`, the project's own jar, env-var overrides only, real
migrations applied, WhatsApp OTP delivery replaced by the built-in `otp.dev-log-fallback` log path.
No project file, no production system touched.

| ID | Scenario | Expected (per comments/docs) | Observed | Verdict |
| :-- | :-- | :-- | :-- | :-- |
| R0 | Boot current tree | starts | `FlywayException: Found more than one migration with version 110` | **FAIL (Critical)** |
| R1 | OTP request, new number | 200 | 200, OTP issued | PASS |
| R2 | Signup with valid OTP | 200 + token | 200, tenant minted, `userEmail:null` | PASS |
| R3 | Repeat signup, same number | rejected | 400 `"This number is already registered."` | PASS |
| R3b | Repeat OTP request, same number | rejected | 400 same message | PASS |
| R4 | `+91…`, `0…` variants | normalized or rejected | 400 validation error | PASS (safe) / see RC-4 |
| R5 | `check-user` for known/unknown | client comment: "always true" | **real** existence (`true`/`false`) | Comment is stale; oracle real |
| R6 | Login, phone account | 200 | 200 + token | PASS |
| R7 | 3 concurrent signups, same unused phone | one success, no duplicates | `200` + `409` + `400`; **1** user row, **0** orphan profiles | PASS (DB-safe) |
| R8 | Login where `email` exists twice in case variants | 200 or clean 401 | **HTTP 500**, `IncorrectResultSizeDataAccessException` | **FAIL (High)** |
| R9 | Google sign-in for a phone-only human | recognise existing account | lookups return 0 rows; the create branch's inserts succeed → 2 accounts / 2 tenants | **FAIL (Critical)** |
| R10 | Auth request budget | generous per-endpoint budget | 5 req/min/IP **across all `/auth/**`** → 429 on ordinary retry bursts | See RC-6 |

Raw evidence retained in `.audit-env/` (`boot-attempt-1.log`, `server.log`, `c1..c3.json`). That
directory is scratch output created by this audit; delete it freely.

---

## Summary: creation-path consistency table

The single most important Phase 2 result — creation paths do **not** share normalization or
duplicate-check rules:

| Path | Phone normalized | Email normalized | Pre-check before insert | DB constraint relied on | Duplicate response |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Signup | yes (`PhoneNormalizer`) | n/a (writes NULL) | yes | 4 indexes | 400 `"already registered"` |
| Google login | n/a | lowercase | **no** | email/login/google only | none (creates) |
| Password login | yes but unreachable | lowercase | n/a | n/a | 400 |
| Mobile update | yes | — | yes (exact match) | 3 indexes | 400 |
| Email update | — | lowercase | yes (exact match) | 2 indexes | 400 |
| Staff create | yes | **no** | yes | 3 indexes | 409 `PHONE_NUMBER_EXISTS` |
| Admin provision | **no** | **no** | yes | 2 indexes | 400, or generic 409 |
| Sync push | via DTO | via DTO | **no** | DB | quarantine / exception |

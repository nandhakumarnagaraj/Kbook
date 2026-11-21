# ACCOUNT DUPLICATION — ROOT CAUSE ANALYSIS

**Scope:** Phase 3 — why duplicate accounts occur, separated into **confirmed** and **suspected** causes.
**Mode:** READ-ONLY audit. No fix has been implemented. Nothing below is a proposal unless it says so.
**Date:** 2026-11-17

---

## 0. How to read this document

| Tag | Meaning |
| :-- | :-- |
| `[REPRODUCED]` | Observed against a real Spring Boot instance + real PostgreSQL 16, in an isolated environment |
| `[VERIFIED]` | Read directly from the implementation, database catalogue, or both |
| `[SUSPECTED]` | Code path is reachable and the mechanism is proven elsewhere, but the exact scenario was not driven end-to-end |
| `[UNVERIFIED]` | Would need production instrumentation to settle |

Each finding carries: severity, evidence, actual behaviour, expected behaviour, root cause, affected
files, test coverage, status.

---

## 1. Symptom → cause map

The reported symptoms, mapped to what this audit actually found:

| Reported symptom | Real explanation | Finding |
| :-- | :-- | :-- |
| "A user can create a new account when an account already exists" | True for the **Google** path only. The phone path refuses correctly. | RC-1 |
| "Existing users may be treated as new users during registration" | True when the existing account has no `email`/`google_email`/`login_id` matching the Google account. | RC-1 |
| "Same user may have multiple account records" | Confirmed mechanism: second account in a second tenant. No person key exists to detect it. | RC-1, RC-5 |
| "Registration and login may not consistently recognise an existing account" | Four different lookup chains. `email` uniqueness is case-sensitive while lookups are case-insensitive. | RC-2 |
| "Different auth methods may create separate accounts" | `auth_provider` is single-valued on the account; there is no binding table. | RC-1, RC-5 |
| "Duplicate records across user/restaurant/owner/employee/subscription" | Not caused by identifier collisions (those are blocked). Caused by per-path tenant minting. | RC-1, RC-8 |
| "Duplicate registration after retry/timeout" | DB-safe for the same phone (`[REPRODUCED]` 409), but the response is unintelligible to the client. | RC-8, RC-6 |

---

## 2. CONFIRMED root causes

### RC-1 — Google sign-in resolves identity by email only, so a phone-registered human gets a second account and a second tenant

**Severity: CRITICAL** — creates duplicate tenants, splits bills/menu/subscription history, and is
invisible to every duplicate report the system can currently run.
**Status:** `[REPRODUCED]` (data layer) + `[VERIFIED]` (code)

**Evidence**

* `services/server/src/main/java/com/khanabook/saas/feature/auth/service/AuthServiceImpl.java:270-272`
  ```java
  return userRepository.findByLoginIdIgnoreCase(email)
          .or(() -> userRepository.findByEmailIgnoreCase(email))
          .or(() -> userRepository.findByGoogleEmailIgnoreCase(email))
  ```
  There is **no** `findByPhoneNumber` and **no** `findByWhatsappNumber` in this chain — unlike `login()`
  (`:76-82`) and `findUserByLoginId()` (`:499-503`), which both check `phone_number` and `whatsapp_number`.
* `AuthServiceImpl.java:291-357` — when the chain misses, the `orElseGet` branch mints a new
  `restaurant_id`, a new `restaurantprofiles` row, a trial subscription and a new `OWNER` `users` row.
* `AuthServiceImpl.java:150` — phone signup always sets `user.setEmail(null)` and never sets
  `google_email`, so a phone account has *nothing* an email-based lookup can match.

**Reproduced** (isolated PostgreSQL, exact lookups and exact inserts from the code above):

```
lookup loginId     -> 0 rows
lookup email       -> 0 rows
lookup googleEmail -> 0 rows
insert (as orElseGet does) -> OK
RESULT: accounts for one human -> 2
RESULT: distinct tenants for one human -> 2
```

**Actual behaviour:** the human is authenticated into a brand-new empty tenant, orphaning the previous
one. Their menu, bills, terminals, staff, subscription and reports stay in the old tenant.
**Expected behaviour:** the returning human is recognised as the owner of the existing tenant, or is
told to link Google to their existing account.

**Root cause:** identity resolution is *provider-shaped*, not *identity-shaped*. Email is the only key
the Google path knows, and phone signup deliberately leaves email empty. There is no account-linking
step, and no schema in which "these two rows are one person" can be recorded.

**Why the device guard does not save it** `[VERIFIED]`: `rejectSecondShopOnSameInstall` blocks a
second live shop only for the *same* `device_id`. `ANDROID_ID` is stable per handset but differs per
handset; a new phone, a second terminal or a blank `ANDROID_ID` (the `9774d56d682e549c` fallback →
random `dev_xxxxxxxx`) passes the guard. The guard protects a device, not a person.

**Affected files:** `AuthServiceImpl.java` (googleLogin, signup), `AuthController.java`,
`UserRepository.java`, no schema support in any migration.

**Test coverage:** **none**. `AuthServiceImplTest.googleLogin_ownerAccount_isAllowed` stubs
`findByLoginIdIgnoreCase("owner@khanabook.com")` against a fixture whose real `login_id` is
`"9876543210"` — a state the database cannot produce. The missing phone lookup is therefore invisible
to the suite.

---

### RC-2 — `email` is case-sensitively unique while every lookup is case-insensitive, and two creation paths store un-normalized emails → HTTP 500 on login

**Severity: HIGH** — a legitimate user cannot sign in (500), and the same identity can exist twice.
**Status:** `[REPRODUCED]`

**Evidence**

* Schema `[VERIFIED]` (deployed `pg_indexes`): `uk_users_email ON users(email)` — **plain, case-sensitive**.
  Compare with `ux_users_login_id_ci`, `ux_users_google_email_ci`, which *are* `lower(...)`.
* Application `[VERIFIED]`: every read is `findByEmailIgnoreCase` (`UserRepository:17`, used by
  `login`, `googleLogin`, `findUserByLoginId`, `UserProfileSyncService`).
* Un-normalized writes `[VERIFIED]`:
  * `AdminWriteService.java:112` — `owner.setEmail(request.getOwnerEmail()…trim())`, no case folding.
  * `BusinessWriteService.java:183` and `:289` — `user.setEmail(req.email())`, and
    `CreateStaffRequest.email` has **no annotation at all**.
  * `UserServiceImpl.ensureEmailAvailable` (`:158`) uses `findByEmail` (exact) and `findByLoginId`
    (exact) — so a case-variant duplicate passes the pre-check.
  * `V4__identity_uniqueness_hardening.sql` lower-cased existing rows once; nothing keeps them that way.

**Reproduced**

```
INSERT two users with email 'August@Example.com' and 'august@example.com'  -> both accepted
POST /auth/login {"loginId":"August@Example.com", ...}
  -> HTTP 500 {"errorId":"a251ea4b","error":"An unexpected error occurred. Please try again."}
server log: org.springframework.dao.IncorrectResultSizeDataAccessException:
            Query did not return a unique result: 2 results were returned
```

**Actual behaviour:** 500. The user sees "unexpected error" and cannot sign in; nothing is logged for
them, and the account appears healthy in the console.
**Expected behaviour:** either the second email is rejected as a duplicate at creation time (409), or
login picks one deterministically.

**Root cause:** a mismatch between the uniqueness rule the database enforces (`email` as an opaque
string) and the equality rule the application applies (case-folded). All other identity columns were
migrated to case-insensitive indexes in V4; `email` was not — likely because `email` also stores phone
numbers (see RC-5), where case folding is meaningless.

**Affected files:** `V1__init_schema.sql:273`, `V4__identity_uniqueness_hardening.sql`,
`AdminWriteService.java:112`, `BusinessWriteService.java:183,289`,
`feature/business/dto/CreateStaffRequest.java`, `UserServiceImpl.java:145-172`, `UserRepository.java`.

**Test coverage:** none. No test asserts case-insensitive email uniqueness, and no test creates users
through the two un-normalized writers.

---

### RC-3 — Two migrations share version `V110`; the application cannot start on the next deployment

**Severity: CRITICAL (deployment blocker)** — not a data bug, but it blocks every fix that follows.
**Status:** `[REPRODUCED]`

**Evidence**

* `[VERIFIED]` `git status` → `?? services/server/src/main/resources/db/migration/V110__prevent_duplicate_device_restaurants.sql`
  (untracked, new) while `V110__add_sub_merchant_restaurant_format.sql` is committed.
* `[VERIFIED]` `spring.flyway.out-of-order=false` (`application.properties:58`); Flyway refuses duplicate versions.
* `[VERIFIED]` The built jar contains **both** files.
* `[REPRODUCED]` Boot:
  ```
  org.flywaydb.core.api.FlywayException: Found more than one migration with version 110
  -> V110__prevent_duplicate_device_restaurants.sql (SQL)
  -> V110__add_sub_merchant_restaurant_format.sql (SQL)
  ```

**Actual behaviour:** the context fails during `flywayInitializer`; exit code 1; the POS and console get
no server.
**Expected behaviour:** each migration has a unique, monotonically increasing version.
**Root cause:** a new migration file was added to the working tree without checking the current maximum
version (121) or the occupied set (both V110s already existed at the time of writing).
**Affected files:** `services/server/src/main/resources/db/migration/`.
**Test coverage:** none — no build step validates migration version uniqueness.

**Note:** production is unaffected *today* only because the file is untracked and the deployed image
predates it. Any build from this tree is un-deployable.

---

### RC-4 — Phone normalization in `login`/`signup` is unreachable; bean validation rejects every variant first

**Severity: MEDIUM** — no duplicate risk (variants are rejected), but the documented remediation does
not do what its comments claim, and clients get a generic validation error.
**Status:** `[REPRODUCED]`

**Evidence**

* `AuthController.java:337` — `LoginRequest.loginId` is
  `@Pattern("^(\\d{10}|[^\\s@]+@[^\\s@]+\\.[^\\s@]+)$")`, so `+919812345670` is a **400** before the method runs.
* `AuthServiceImpl.normalizeLoginIdentifier` (`:62-73`) contains a long comment about collapsing
  `+91` / leading-0 / spaced variants — unreachable for those inputs.
* Same for `SignupRequest.phoneNumber`: `@Pattern("^\\d{10}$")`; the `PhoneNormalizer` call in `signup`
  can only ever receive an already-bare 10-digit string.

**Reproduced**

```
POST /auth/login {"loginId":"+919812345670"} -> 400
  {"fields":{"loginId":"Login ID must be a 10-digit phone number or a valid email"}}
```
(`/auth/check-user` *does* normalize first — `AuthController.check-user` — because it has no `@Pattern`.)

**Root cause:** two layers solve the same problem and the outer one wins; the intended normalization is
dead code for exactly the inputs it names.
**Affected files:** `AuthController.java` (`LoginRequest`, `SignupRequest`, `SignupOtpRequest`,
`PasswordResetOtpRequest`), `AuthServiceImpl.java:62-73`.
**Test coverage:** none.

---

### RC-5 — There is no identity model: `email` is conflated with phone, and identity is a set of columns rather than an entity

**Severity: HIGH (architectural)** — this is the reason RC-1 cannot be fixed locally.
**Status:** `[VERIFIED]`

**Evidence**

* `UserServiceImpl.java:206-212` — on a mobile change for a `PHONE` account:
  `login_id = newMobile`, **`email = newMobile`**, `phone_number = newMobile`, `whatsapp_number = newMobile`.
  A phone number is written into the mailbox column.
* `googleLogin`'s create branch writes `password_hash = "GOOGLE_AUTH"` — a sentinel, not a credential.
* `auth_provider` is a single enum on `users`, so a human can be `PHONE` **or** `GOOGLE` but never both.
  Every Google login of an existing phone account *flips* `auth_provider` to `GOOGLE` (`:283`) without
  recording that a phone credential is still valid.
* No `verified_at` / `verified` column exists on any identity column. Verification lives only as
  deletable rows in `otp_requests` (`validateOtpOrThrow` deletes the challenge on success).
* No `user_identities`, no `restaurant_memberships`, no person table (`V1`…`V121` reviewed).

**Actual behaviour:** "who is this person" is answered by trying up to four string columns in a fixed
order, per call site.
**Expected behaviour for a multi-tenant restaurant SaaS:** one person, N verified identities, M tenant
memberships with roles; lookups then ask the identity table, not the account row.
**Root cause:** the account row doubles as the person, the credential, and the membership.
**Affected files:** entire `users` schema; `AuthServiceImpl`, `UserServiceImpl`, `UserProfileSyncService`,
`AdminWriteService`, `BusinessWriteService`.
**Test coverage:** n/a (structural).
**Note:** this is documented as an evaluation, not a defect to be silently re-architected — Phase 4
(`ACCOUNT_IDENTITY_ARCHITECTURE.md`) sets out options and open decisions.

---

## 3. SUSPECTED causes (reachable, not driven end-to-end)

### RC-6 — The auth rate limiter collapses legitimate retry into 429, and the client cannot distinguish it from abuse

**Severity: MEDIUM** · `[SUSPECTED]` for user impact, `[VERIFIED]` for the mechanism.

* `RateLimitingInterceptor.java:20-21` — **5 requests / minute / IP** for **all** of `/auth/**`
  (also `/public/**`, `/cdn/**`, webhooks), registered in `WebMvcConfig.addInterceptors`.
* `[REPRODUCED]` A normal sequence — `signup/request` → `signup` → `login` → `check-user` — exhausts the
  budget, and subsequent calls return a **bodyless 429** (Tomcat's default JSON, not the app's error
  envelope). The Android client maps 429 to "Too many attempts. Please wait a moment and try again."
* Consequence: a user whose OTP SMS is delayed, who retries after a timeout, or who simply mistypes
  twice, is locked out of *every* auth endpoint for a minute, with no indication of which one failed.
* Because signup is **not idempotent** (no idempotency key in `SignupRequest`), a client retry after a
  network timeout cannot be de-duplicated with certainty by the client; the server relies purely on
  the phone unique index.
* `[VERIFIED]` The per-account limit (`isAccountLoginAllowed`, 5/15 min) and the in-memory limiters
  stack on top of the interceptor budget.

### RC-7 — `findByAnyIdentifier` can match more than one row, and `JwtRequestFilter` swallows the failure

**Severity: HIGH if reached** · `[SUSPECTED]` (mechanism proven, scenario not driven)

* `JwtRequestFilter.java:105-106` resolves the caller with
  `userRepository.findByAnyIdentifier(username)` — a Spring Data `Optional` over
  `phoneNumber = :x OR loginId = :x OR email = :x OR whatsappNumber = :x`, **not tenant-scoped**.
* `Optional` over a multi-row result throws `IncorrectResultSizeDataAccessException`, and the filter's
  `catch (Exception e) { logger.debug(...) }` (`:170-172`) **swallows it**, leaving the request
  unauthenticated while the chain continues.
* `[REPRODUCED]` the same query shape does throw this exception (R8 above). What is *not* proven is a
  production-shaped collision: the four per-column indexes and the guards in `ensureMobileNumberAvailable`
  make one hard to reach. The realistic route is a `CreateStaffRequest.email` that duplicates another
  user's `login_id` (that DTO has no validation), which is exactly what RC-2's un-normalized writers allow.
* Impact if reached: the affected user's every authenticated request fails, with no error in the log at
  INFO+ level.

### RC-8 — Concurrent/duplicate signup returns a generic conflict instead of an actionable one

**Severity: LOW** · `[REPRODUCED]`

* Three concurrent signups for one unused number: `200`, `409 {"error":"A data conflict occurred. A
  duplicate or invalid reference was detected."}`, `400` (device guard).
* The database **did** protect identity (one user row, zero orphan profiles — rollback verified).
* But `409` here means "already registered", which the Android client renders as a raw server message;
  the same condition via the normal path returns `"This number is already registered."`. Two contracts
  for one condition. `GlobalExceptionHandler.java:132-140`.

### RC-9 — `/auth/check-user` is a real account-existence oracle, and the client's stated rationale is false

**Severity: MEDIUM (enumeration)** · `[VERIFIED]` + `[REPRODUCED]`

* `AuthController.checkUser` returns `authService.checkUserExists(normalized)` — the **real** answer
  (`findActiveByAnyIdentifier`). Its own comment concedes "This is an account-existence oracle by design".
* `AuthViewModel.checkUserExists` (`:86-95`) states the endpoint "ALWAYS returns true for any valid
  10-digit number" and therefore does not pre-flag existence. That comment is **stale/wrong**: the
  endpoint returns `false` for unknown numbers (`[REPRODUCED]` logic path; the R5 call was rate-limited
  before it could be printed, and the controller code is unambiguous).
* Net effect: the oracle exists, the mitigation is only a shared 5/min IP budget, and the client's
  documented reason for ignoring it is incorrect.

### RC-10 — Google login mutates the account's device identity and provider without recording the change

**Severity: LOW/MEDIUM** · `[VERIFIED]`

* `AuthServiceImpl.java:284-288` — `setAuthProvider(GOOGLE)`, `setDeviceId("server")`,
  `setUpdatedAt(now)` on the existing account. A phone account that signs in with Google loses its
  recorded `device_id` (used by `rejectSecondShopOnSameInstall` and terminal identity) and its
  provider label flips, while the phone credential remains valid.
* No audit row is written on this identity change, although `SecurityAuditService` exists and is used
  for provisioning.

### RC-11 — `deviceId` is mandatory for phone signup but optional for Google signup

**Severity: LOW** · `[VERIFIED]`

`SignupRequest.deviceId` is `@NotBlank`; `GoogleLoginRequest.deviceId` is `@Size(max=128)` only, and the
service defaults it to `"web-admin"` — a sentinel the device guard deliberately skips
(`AuthServiceImpl:563-566`). A Google client that omits `deviceId` therefore bypasses the only
same-install duplicate guard the system has.

---

## 4. Categories investigated and found sound (do not "fix" these)

Recording the negatives matters as much as the positives — these are defended and must not regress.

| Category | Verdict | Evidence |
| :-- | :-- | :-- |
| Duplicate phone signup (sequential) | **Protected** | `[REPRODUCED]` 400 at both `/signup/request` and `/signup` |
| Duplicate phone signup (concurrent) | **Protected at DB level** | `[REPRODUCED]` 200/409/400; one row; transaction rollback leaves no orphan |
| Phone format variants | **Rejected before they reach the DB** | `[REPRODUCED]` 400; plus `ux_users_phone_digits` as a second layer |
| Soft-deleted identifiers | **Correctly reusable, non-destructively** | `releaseIdentifierFromDeletedUsers` tombstones rather than deletes (`AuthServiceImpl:596-621`) |
| OTP binding | **Correct** | Challenge key includes a purpose prefix and the phone/tenant; wrong number invalidates the challenge; consumed on success; 5-attempt limit; 10-minute TTL (`PasswordResetOtpService`) |
| OTP namespace confusion | **Fixed earlier** | The unredeemable `staff-invite:` namespace was removed; staff onboarding now sends a generated password |
| Tenant isolation on sync | **Correct** | `UserProfileSyncService.findExistingUserByIdentity` scopes every lookup to the caller's tenant |
| Staff-token account creation | **Correctly refused** | `enforceNewUserRoleForCaller` returns false for non-owners and quarantines the record |
| Token invalidation | **Correct** | `token_invalidated_at` checked in the filter; refresh tokens revoked on password change |
| Application-level duplicate validation only | **No** — DB constraints exist | five unique indexes verified in the deployed catalogue |

---

## 5. Severity roll-up

| Severity | Count | IDs |
| :-- | :-- | :-- |
| Critical | 2 | RC-1, RC-3 |
| High | 3 | RC-2, RC-5, RC-7 |
| Medium | 3 | RC-4, RC-6, RC-9 |
| Low | 3 | RC-8, RC-10, RC-11 |

---

## 6. Explicit non-findings

* `[VERIFIED]` There is **no evidence of duplicate rows for the same phone number** in production
  (0 case-variant phone duplicates, 0 cross-column collisions, 0 duplicated `restaurant_id` in
  `restaurantprofiles`). The "duplicate account" experience is the RC-1 cross-identifier class, not
  identifier collisions.
* `[VERIFIED]` Case-variant duplicate emails are currently **0** in production — RC-2 is a latent
  defect, not an active incident.
* `[UNVERIFIED]` How many of production's 76 `GOOGLE` accounts belong to humans who also hold a `PHONE`
  account cannot be determined from the current schema. That is precisely RC-5, and is the first thing
  Phase 6 (duplicate data investigation) would need to establish, with an owner-verification step —
  **not** an automatic merge on matching name.

---

## 7. Reproduction environment and honesty statement

* Isolated `postgres:16-alpine` container; the project's own jar; environment overrides only.
* Migrations applied from a copy of the migration directory with the duplicate `V110` removed, because
  otherwise the server does not start (RC-3). This deviation affects no other finding.
* `POST /auth/google` was **not** driven end-to-end: no Google-signed ID token was available, and
  `GoogleIdTokenVerifier.verify()` cannot be bypassed without editing source (forbidden). RC-1 is
  therefore labelled `[REPRODUCED]` at the data layer and `[VERIFIED]` at the code layer — the lookups
  and the inserts were executed exactly as the code performs them.
* Production was read with `SELECT` only. No schema change, no data change, no environment change.
* The scratch environment has been torn down. `.audit-env/` holds logs from the run and can be deleted.

---

## 8. What this means for the fix (not implemented)

Any fix must address RC-1 and RC-5 together — matching a Google login to a phone account without an
identity table would require guessing, and the brief rightly forbids merging on a matching identifier
alone. RC-3 must be resolved before any migration can ship at all. RC-2 needs a schema-level decision
(case-insensitive uniqueness on a column that also holds phone numbers). RC-4, RC-6, RC-8, RC-9,
RC-10, RC-11 are contained and can be sequenced after the identity model is settled.

Solution design is **Phase 5** and is deliberately not started: awaiting review of Phases 1–4.

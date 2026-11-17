# ACCOUNT SYSTEM ARCHITECTURE AUDIT

**Scope:** Phase 1 of the account-lifecycle forensic audit — complete system discovery.
**Mode:** READ-ONLY. No source, schema, production data, configuration, environment value or existing
document was modified while producing this audit.
**Date:** 2026-11-17
**Repository:** KhanaBook SaaS monorepo (`kbook.iadv.cloud` working tree, branch `main`)

> **Rule applied throughout:** implementation is the source of truth. Every claim below is tagged
> `[VERIFIED]`, `[REPRODUCED]`, `[SUSPECTED]`, or `[UNVERIFIED]`. Nothing is documented as implemented
> behaviour on the strength of a comment, a name, or an intention.

---

## 1. Executive summary

KhanaBook has **one authentication module** and **at least ten distinct code paths that can create a
`users` row**. Identity is expressed as a set of loosely-related string columns on `users`
(`login_id`, `email`, `phone_number`, `whatsapp_number`, `google_email`) with **five independent
database uniqueness rules** and **four different lookup chains** in application code.

That model holds for the phone-only case, which is well defended. It breaks in four places, two of
which are reproduced below:

| # | Finding | Severity | Status |
| :-- | :-- | :-- | :-- |
| 1 | Google sign-in cannot resolve a phone-registered account, so the same human gets a second account **and a second tenant** | Critical | `[REPRODUCED]` (data layer) / `[VERIFIED]` (code) |
| 2 | The working tree **cannot boot**: two migrations with version `V110` | Critical | `[REPRODUCED]` |
| 3 | `email` uniqueness is case-sensitive while every lookup is case-insensitive, and two creation paths store un-normalized emails → login returns HTTP 500 | High | `[REPRODUCED]` |
| 4 | Phone normalization in `login`/`signup` is unreachable behind bean validation | Medium | `[REPRODUCED]` |

Two further structural gaps are documented without a live exploit (tagged `[SUSPECTED]` or
`[VERIFIED]` as appropriate) in `ACCOUNT_DUPLICATION_ROOT_CAUSE.md`.

---

## 2. Applications in the repository

| Path | What it is | Evidence |
| :-- | :-- | :-- |
| `services/server/` | The backend. Spring Boot 3, Java 21, Maven, PostgreSQL 16, Flyway. | `[VERIFIED]` `services/server/pom.xml`, `spring-boot` jar at `target/saas-backend-0.0.1-SNAPSHOT.jar` |
| `apps/android/` | The POS client. Kotlin, Jetpack Compose, Hilt, Room, Retrofit; 354 Kotlin files under `app/src`. | `[VERIFIED]` `find app/src -name '*.kt' \| wc -l` → 354 |
| `apps/web-admin/` | The web console. Angular (standalone components, `src/app/pages/login`). Login only — no signup page exists; the login page links out to `https://khanabook.in/get-started`. | `[VERIFIED]` `apps/web-admin/src/app/pages/login/login-page.component.ts:269` |
| `web-admin/`, `Android/`, `server/` | Stale build-output copies (`.angular`/`dist`, Gradle `build/`, a 59 MB `server.log`). Not source. | `[VERIFIED]` directory listings |

Backend package layout under `services/server/src/main/java/com/khanabook/saas`:

```
core/        security (JWT filter, SecurityConfig, rate limiting, TenantContext), exception, config
feature/auth        ← identity, credentials, OTP, tokens, rate limiters
feature/restaurants ← tenant profile + terminal identity
feature/business    ← staff CRUD (account creation), owner identity edits
feature/staff       ← permissions, role templates
feature/platform    ← KBOOK_ADMIN provisioning, feature flags
feature/subscription, billing, payments, sync, menu, inventory, reports, onboarding, compliance, shifts, notifications
```

### 2.1 Runtime configuration (verified, no secrets read)

* `server.servlet.context-path=/api/v1` — clients call `/api/v1/auth/...`, controllers are mapped at `/auth/...`.
* `spring.jpa.hibernate.ddl-auto=validate` — **Hibernate does not create or fix schema**. Every
  constraint that exists is a Flyway migration. `@Column(unique = true)` on the `User` entity is
  therefore *decorative*; it expresses intent, not enforcement. `[VERIFIED]` `application.properties:45`
* `spring.flyway.enabled=true`, `out-of-order=false`, `baseline-on-migrate=true`.

---

## 3. The identity-bearing data model

### 3.1 `users` (the account table)

`services/server/src/main/resources/db/migration/V1__init_schema.sql:1-23`, as amended by later
migrations. Base columns come from `BaseSyncEntity`: `id`, `local_id`, `device_id`, `restaurant_id`,
`updated_at`, `server_updated_at`, `created_at`, `is_deleted`, `version`.

Identity-relevant columns:

| Column | Meaning in practice | Written by |
| :-- | :-- | :-- |
| `login_id` | The JWT subject. "What you type to sign in". | signup, staff creation, admin provisioning, mobile update, Google login, sync backfill |
| `phone_number` | Server-owned verified mobile. | signup, staff creation, admin provisioning, mobile-update flow only |
| `whatsapp_number` | Messaging target, also accepted as a login identifier. | signup, staff creation, mobile update |
| `email` | **Conflated**: real mailbox *or* a phone number *or* NULL. | `updatePrimaryMobileNumber` writes the phone number into this column (`UserServiceImpl.java:210`) |
| `google_email` | Google-bound mailbox. | Google login only |
| `auth_provider` | `PHONE` \| `GOOGLE` (DB CHECK constraint `chk_users_auth_provider`). | all creation paths |
| `password_hash` | BCrypt. Literal `"GOOGLE_AUTH"` for Google-created accounts. | creation + reset flows |
| `pin_hash`, `pin_set_at` | Device/terminal PIN. | terminal/auth flows |
| `role` | `OWNER` \| `SHOP_STAFF` \| `KBOOK_ADMIN`. **Also the tenant membership.** | creation paths, permission flows |
| `restaurant_id` | The tenant key. One row = one membership. | creation paths |
| `is_active`, `token_invalidated_at`, `is_deleted` | Account state. | admin/owner actions, password reset |

Key structural facts:

* `[VERIFIED]` There is **no person entity**, no `user_identities` table, no `restaurant_memberships`
  table, and no verified/unverified flag on any identity column. Verification exists only as
  short-lived rows in `otp_requests`.
* `[VERIFIED]` Membership and account are the same row: a user belongs to exactly one restaurant
  because `restaurant_id` is a column on `users`, not a join table.

### 3.2 Database uniqueness — as it actually exists in the deployed database

Read directly from `pg_indexes` on the running deployment (`kbook_saas`), read-only `SELECT`:

| Index | Definition | Effective rule |
| :-- | :-- | :-- |
| `ux_users_login_id_ci` | `UNIQUE lower(login_id) WHERE btrim<>''` | login id unique, case-insensitive |
| `ux_users_phone_digits` | `UNIQUE (phone_digits) WHERE NOT NULL` | last-10-digits unique (V105 generated column) |
| `idx_users_phone_number` | `UNIQUE (phone_number) WHERE NOT NULL` | raw phone unique |
| `ux_users_whatsapp_number` | `UNIQUE (whatsapp_number) WHERE btrim<>''` | raw whatsapp unique, **case-sensitive** |
| `uk_users_email` | `UNIQUE (email)` | email unique, **case-sensitive**, no partial predicate |
| `ux_users_google_email_ci` | `UNIQUE lower(google_email) WHERE btrim<>''` | google email unique, case-insensitive |

`[VERIFIED]` **There is no cross-column uniqueness.** Nothing stops user A's `login_id` from equalling
user B's `email`, because those are two independent indexes on two independent columns.

`[VERIFIED]` `uk_users_email` is `UNIQUE (email)` — plain, case-sensitive, and it covers the empty
string. `V4__identity_uniqueness_hardening.sql` lower-cased existing emails once, but nothing enforces
that new writes are lower-cased.

### 3.3 Related tables

| Table | Account relationship |
| :-- | :-- |
| `restaurantprofiles` | Tenant root, keyed by `restaurant_id`. V107 deletes signup "stub" duplicates and conditionally creates `uq_restaurantprofiles_restaurant_id`. `[VERIFIED]` duplicate count = 0 in prod; index existence `[UNVERIFIED]` |
| `otp_requests` | One row per challenge, keyed by `challenge_key` = `<purpose-prefix><phone-or-tenant>`. Namespaces are `signup:`, `password-reset:`, `web-password-reset:`, `mobile-update:<tenantId>`, `email-update:<tenantId>`. |
| `refresh_tokens` | Bound to `user_id` + `restaurant_id` + `device_id`; revoked on password change. |
| `rate_limit_attempts` | DB-backed limiter rows (`OTP_REQUEST`, `LOGIN_ATTEMPT`, `PERMISSION_REQUEST`). |
| `token_blocklist` | Access-token JTI blocklist for logout. |
| `subscription*` (V120) | Trial/paid subscription keyed by `restaurant_id`. Created inside the signup transaction. |
| `staff_permissions`, `role_templates` | Per-user grants inside a tenant. |

### 3.4 Migrations and deployment state

* `[VERIFIED]` 104 migration files in `services/server/src/main/resources/db/migration` (103 applied in the reproduction, i.e. all but the duplicate `V110`).
* `[VERIFIED]` Deployed `flyway_schema_history` stops at **V121**, and its **V110 is
  `add sub merchant restaurant format`**.
* `[VERIFIED]` The working tree contains an **untracked second V110**,
  `V110__prevent_duplicate_device_restaurants.sql` (`git status` → `??`).
* `[REPRODUCED]` Booting the current `target/saas-backend-0.0.1-SNAPSHOT.jar` (built 2026-11-17 09:03,
  contains *both* V110 files) fails immediately:

```
org.flywaydb.core.api.FlywayException: Found more than one migration with version 110
-> .../db/migration/V110__prevent_duplicate_device_restaurants.sql (SQL)
-> .../db/migration/V110__add_sub_merchant_restaurant_format.sql (SQL)
    at org.flywaydb.core.internal.resolver.CompositeMigrationResolver.checkForIncompatibilities
```

* `[VERIFIED]` Two working-tree edits are uncommitted and therefore **not deployed**: a `Locale.ROOT`
  fold in `rejectSecondShopOnSameInstall`, an uppercase-`deviceId` comparison in
  `RestaurantProfileRepository.findLiveShopsForDevice`, and a validation-annotation reshuffle in
  `AuthController.SignupRequest.deviceId`.

### 3.5 Security infrastructure relevant to accounts

* `[VERIFIED]` `JwtRequestFilter` resolves the caller by **`userRepository.findByAnyIdentifier(username)`**
  — an `Optional` over a 4-column `OR` (`phone_number`, `login_id`, `email`, `whatsapp_number`),
  then sets tenant/role from **the resolved row**, not from the token. It enforces: token blocklist,
  `token_invalidated_at`, `is_active`, business suspension (except `KBOOK_ADMIN`), and device binding
  via the `X-Device-Id` header (browser device ids exempt).
* `[VERIFIED]` Global exception handling maps `DataIntegrityViolationException` → **409**, and an
  unexpected exception → **500** with an `errorId` — see `GlobalExceptionHandler.java:132`, `:241`.
* `[VERIFIED]` `RateLimitingInterceptor` applies **5 requests per minute per IP across all of
  `/auth/**`, `/sync/**`, `/public/**`, `/cdn/**`** (`RateLimitingInterceptor.java:20-21`, registered in
  `WebMvcConfig.addInterceptors`). `/sync/**` has its own 30/min bucket.
* `[VERIFIED]` `SecurityConfig` permits `/auth/login`, `/auth/signup`, `/auth/signup/**`,
  `/auth/google`, `/auth/check-user`, `/auth/reset-password*`, `/auth/forgot-password/*` anonymously.

---

## 4. Account-lifecycle surface map

Entry points that can mutate identity, all `[VERIFIED]` by source:

| Surface | Endpoint | Handler |
| :-- | :-- | :-- |
| Android signup | `POST /auth/signup/request`, `POST /auth/signup` | `AuthController:139,131` → `AuthServiceImpl.signup` |
| Android / web login | `POST /auth/login` | `AuthController:90` → `AuthServiceImpl.login` |
| Android / web Google | `POST /auth/google` | `AuthController:144` → `AuthServiceImpl.googleLogin` |
| Token refresh | `POST /auth/refresh` | `AuthServiceImpl.refreshAccessToken` |
| Logout | `POST /auth/logout` | blocklist + refresh revoke |
| Password reset | `POST /auth/reset-password/request`, `/auth/reset-password` | OTP namespace `password-reset:` |
| Web-admin forgot password | `POST /auth/forgot-password/{request-otp,verify-otp,reset-password}` | `WebAdminPasswordResetService` |
| Owner mobile change | `POST /sync/config/users/update-mobile` (+`/request`) | `UserServiceImpl` |
| Owner email change | `POST /sync/config/users/update-email` (+`/request`) | `UserServiceImpl` |
| Staff creation (invite) | `POST /business/staff` | `BusinessWriteService.createStaff:158` |
| Staff re-credential | `POST /business/staff/{id}/resend-credentials` | `BusinessWriteService` |
| Admin provisioning | `POST /admin/businesses` | `AdminWriteService.createBusiness:61` |
| Device user push | `POST /sync/config/users/push` | `UserServiceImpl.pushData` → `GenericSyncService` |
| Presence | `POST /auth/heartbeat` | `PresenceService` (no identity mutation) |
| Dev-only | `POST /auth/signup/dev`, `POST /auth/signup/dev-admin` | `@Profile("dev")` only |

---

## 5. Test coverage of identity behaviour

`[VERIFIED]` Relevant tests present:

* `services/server/src/test/java/com/khanabook/saas/service/AuthServiceImplTest.java` — mock-based.
  Covers login success, new signup, uuid restaurant ids, live-number rejection, soft-deleted number
  release, web-surface staff rejection, google staff rejection/owner allow, and the device guard.
* `UserProfileSyncServiceMergeTest`, `UserProfileSyncServiceRoleTest`, `UserServiceMobileUpdateTest`,
  `UserServiceEmailUpdateTest`, `BusinessWriteServiceProfileIdentityTest`.

`[VERIFIED]` Gaps observed by reading those tests:

* **No test exercises "phone-registered human then signs in with Google."** `googleLogin_ownerAccount_isAllowed`
  stubs `findByLoginIdIgnoreCase("owner@khanabook.com")` for a user whose real `login_id` is
  `"9876543210"` — a mock-only path that cannot occur against the database. The test therefore
  proves the happy branch and *hides* the missing phone lookup.
* No test asserts case-insensitive uniqueness of `email` (which the schema does not provide).
* No concurrency test against the real database; the uniqueness enforcement is only exercised through
  mocks, so the DB constraint layer is untested.
* No test asserts the `@Pattern` versus `PhoneNormalizer` interaction.

---

## 6. Live production data snapshot (read-only aggregate queries)

Read-only `SELECT`s against `kbook_saas`. No rows were modified. Aggregate counts only:

| Metric | Value |
| :-- | :-- |
| `users` rows | 166 (all `is_deleted=false`) |
| `auth_provider` | 90 `PHONE`, 76 `GOOGLE` |
| Users with `email IS NULL` | 34 |
| Distinct `restaurant_id` in `users` | 163 |
| Restaurants with >1 live user row | 1 (a staff member) |
| Restaurants with >1 `OWNER` | 0 |
| `restaurantprofiles` duplicate `restaurant_id` groups | 0 |
| Case-variant duplicate emails (`lower(email)` groups > 1) | 0 |
| Cross-column identity collisions | 0 |

Interpretation: the **database-level** duplication class is currently clean in production. The
duplication that users actually experience is therefore *not* rows colliding on one identifier — it is
**the same human holding several rows under different identifiers** (phone account + Google account),
which no current query above can see because it has no person key. That distinction drives the whole
root-cause report.

`[VERIFIED]` Risk note: the untracked directory `.backup-srcafe-20261113-175029/` in the working tree
contains `users.csv`, `restaurantprofiles.csv`, `restaurant_terminal.csv`,
`device_registration_request.csv` — production PII sitting un-ignored in the repository directory. Not
opened during this audit.

---

## 7. Limitations of this audit

1. `[VERIFIED]` **`POST /auth/google` could not be exercised end-to-end.** `GoogleIdTokenVerifier.verify()`
   requires a Google-signed token; forging one is out of scope. The Google path was therefore proven
   by (a) source reading and (b) reproducing the exact rows it writes and the exact lookups it performs
   against a real PostgreSQL 16 instance. Labelled accordingly everywhere.
2. `[VERIFIED]` Production data was inspected with `SELECT` only. No write, migration, `EXPLAIN ANALYZE`
   on writes, or schema change was executed against `kbook_saas`.
3. Reproduction used a throwaway `postgres:16-alpine` container plus the project's own jar, pointed at
   that container with environment overrides. No project file was edited. The container has been removed.
4. `[VERIFIED]` To boot at all, the reproduction excluded the duplicate `V110` from a copy of the
   migration set. That deviation is the subject of finding #2 above, not a workaround for any other issue.
5. `RestaurantTerminal` / terminal-activation identity (a second, device-level identity system) was
   identified but not audited in depth; it is out of scope for account identity.
6. `[UNVERIFIED]` Whether `uq_restaurantprofiles_restaurant_id` exists in production (V107 creates it
   conditionally). Not queried.

---

## 8. What Phase 1 establishes for the later phases

1. Identity is **column-based, not entity-based**. There is no place in the schema to say "these two
   rows are the same person."
2. Uniqueness is enforced **per column, never across columns and never case-insensitively on `email`**.
3. There are **ten account-creating paths**, each with its own normalization and its own duplicate
   check (`[REPRODUCED]` differences documented in Phase 2).
4. Phone-only signup is **well defended** (`[REPRODUCED]`: repeat → 400, concurrent → 409, variant
   formats → 400).
5. The Google path is the **structural hole**: it resolves identity by email only
   (`AuthServiceImpl.java:270-272`).
6. The tree is **not deployable** in its current state (`[REPRODUCED]`).

---

## Finding index (Phase 1 artifacts → later deliverables)

| ID | Title | Severity | Where detailed |
| :-- | :-- | :-- | :-- |
| F-01 | Duplicate Flyway `V110`; application cannot start | Critical | `ACCOUNT_DUPLICATION_ROOT_CAUSE.md` RC-3 |
| F-02 | Google sign-in blind to phone identity; creates second tenant | Critical | RC-1 |
| F-03 | `email` unique-but-not-case-insensitive + un-normalized writes → login 500 | High | RC-2 |
| F-04 | Phone normalization unreachable behind `@Pattern` | Medium | RC-4 |
| F-05 | No person/identity/membership model; `email` column conflation | High (architectural) | RC-5, `ACCOUNT_IDENTITY_ARCHITECTURE.md` |
| F-06 | `findByAnyIdentifier` can match >1 row; filter swallows the exception | High | RC-7 (`[SUSPECTED]`) |
| F-07 | Auth rate limiter 5/min/IP collapses legitimate retry into 429 | Medium | RC-6 |
| F-08 | Concurrent signup surfaces a generic 409, not "already registered" | Low | RC-8 |
| F-09 | `/auth/check-user` is a real existence oracle; client comment says otherwise | Medium | RC-9/RC-10 |
| F-10 | Production PII CSVs in the working tree | High (process) | §6 |

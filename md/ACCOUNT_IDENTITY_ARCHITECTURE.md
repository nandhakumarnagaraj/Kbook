# ACCOUNT IDENTITY ARCHITECTURE (Phase 4)

**Scope:** Phase 4 — evaluate whether KhanaBook correctly separates person identity, authentication
identity, account, business, membership, role, staff identity and billing ownership; and document
candidate identity architectures **for review only**.
**Mode:** READ-ONLY audit. **No implementation.** Every proposal below is explicitly marked `[PROPOSED]`
and requires your approval before any code or migration is written.
**Date:** 2026-11-17

---

## 1. Current separation, layer by layer

The brief asks whether eight things are separated. Verdict per layer, against the actual schema and code.

| # | Concern | Where it lives today | Separated? |
| :-- | :-- | :-- | :-- |
| 1 | **Person identity** | Nowhere. No `person`/`identity` entity. A human is inferred from matching strings on `users`. | ❌ **No** |
| 2 | **Authentication identity** | Columns on `users`: `login_id`, `phone_number`, `whatsapp_number`, `email`, `google_email`, `password_hash`, `pin_hash`, plus a single-valued `auth_provider` enum. | ⚠️ **Partial** — credential and account are the same row; a person cannot hold two providers at once |
| 3 | **Application user account** | `users` — doubles as credential + person + membership | ⚠️ **Conflated** |
| 4 | **Restaurant / business entity** | `restaurantprofiles.restaurant_id` (and `restaurant_id` on every tenant-scoped table) | ✅ **Yes** — clean tenant key |
| 5 | **Restaurant membership** | `users.restaurant_id` + `users.role`. One row = one membership. A user row *is* its single membership. | ❌ **No** — no join table, so one person cannot belong to two restaurants |
| 6 | **Owner / administrator role** | `users.role` enum (`OWNER`, `SHOP_STAFF`, `KBOOK_ADMIN`) + `staff_permissions` grants | ⚠️ **Partial** — role is per-account; with multi-membership it would need to move to the membership |
| 7 | **Employee / staff identity** | A `users` row with `role=SHOP_STAFF` in the same table as owners; a dedicated phone is mandatory and globally unique | ⚠️ **Partial** — staff are tenants-scoped rows, but the number may not be reused across shops, so a person cannot be staff at two restaurants without a second number |
| 8 | **Subscription / billing ownership** | `subscription*` (V120) keyed by `restaurant_id`; Easebuzz sub-merchant keyed by `restaurant_id` | ✅ **Yes** — billing belongs to the business, not the human. Correct for SaaS. |

### 1.1 What that means concretely

**Supported today:**

* One owner → one restaurant (enforced by convention, not by schema).
* Many users → one restaurant (owner + staff) ✅
* One human, one provider ⚠️
* Business-level billing ✅
* Roles inside a tenant ✅ (`staff_permissions`, `PermissionKey`, role templates)
* Account suspension (`is_active`), business suspension (`restaurantprofiles.is_suspended`), session
  revocation (`token_invalidated_at` + blocklist) ✅

**Not supported today:**

* One user managing multiple restaurants. Impossible without a second `users`
  row — which then needs a second unique phone, and if it uses the same phone it is refused by
  `ux_users_phone_digits`. `[VERIFIED]`: `ensurePhoneNumberAvailableForSignup`,
  `DuplicateStaffPhoneException`, `ensureMobileNumberAvailable`.
* One person with both a phone credential **and** a Google credential. `auth_provider` is a single
  column, and Google login *overwrites* it (`AuthServiceImpl:283`).
* A staff member working at two restaurants with one phone.
* Recording that two existing accounts are the same person (RC-1's damage cannot be expressed, let
  alone repaired).
* A notion of **verified** identity. `otp_requests` proves control for ~10 minutes and is then deleted
  (`PasswordResetOtpService.validateOtpOrThrow`), so the account row never learns that its phone was
  ever verified.
* Ownership transfer as a first-class operation. Transferring a shop means editing `users.restaurant_id`
  / `role` by hand; there is no audited operation and no invariant that a restaurant keeps exactly one
  owner (`[VERIFIED]`: production has 0 restaurants with >1 OWNER, but that is convention, not a constraint).

### 1.2 Business rules that are currently preserved and must stay preserved

Per the brief — *preserve existing business rules unless evidence demonstrates they are defective* —
these are working and must survive any redesign:

1. Phone number is globally unique (a real operational constraint: it is the WhatsApp channel).
2. Phone accounts can be created only with an OTP proving control of that number.
3. A soft-deleted account releases its number non-destructively (history retained, `login_id` tombstoned).
4. A suspended business cannot authenticate on any path.
5. `SHOP_STAFF` cannot sign in to web admin.
6. Tokens are device-bound (`X-Device-Id`), and password change invalidates older tokens.
7. Billing and payments belong to `restaurant_id`, never to a person.
8. One live shop per `device_id` (the anti-duplicate guard that already stopped several duplicates).
9. Staff cannot mint accounts (sync role enforcement).
10. Existing JWT shape (`sub` = `login_id`, `restaurantId`, `role`, `deviceId`) — changing it would force
    every live Android install to re-authenticate. Treat as a compatibility constraint.

---

## 2. The core architectural problem, stated precisely

> Identity resolution is **provider-shaped**. Each entry point has its own idea of what identifies a
> human, so "the same person" is a different query depending on which door they came through.

Three consequences follow directly, and they match the reported symptoms:

1. **A returning person can be invisible** (phone account + Google login → RC-1).
2. **A person can hold several accounts with no way to say so** (RC-5).
3. **Duplicate detection can only ever be per-identifier**, so the duplicate *report* is blind to the
   duplicates that users actually experience.

Everything else in the root-cause report (RC-2, RC-4, RC-6, RC-8, RC-9, RC-10, RC-11) is a defect to be
fixed inside whatever model is chosen. RC-1 and RC-5 are the model itself.

---

## 3. Options for the target model

Two viable directions. Both are additive and both preserve the ten rules above. **Neither has been
implemented.** Presenting them here is the Phase 4 deliverable; choosing between them is decision D-1.

### Option A — Additive identity table (minimal change, lowest risk)

`[PROPOSED]`

Keep `users` as the account. Add one table that answers "which account owns this identifier":

```
user_identities
  id                BIGSERIAL PK
  user_id           BIGINT NOT NULL REFERENCES users(id)
  provider          VARCHAR(20) NOT NULL      -- PHONE | GOOGLE | EMAIL
  identifier        VARCHAR(320) NOT NULL     -- E.164 for PHONE, lower(trim(...)) for EMAIL
  verified_at       BIGINT NOT NULL
  created_at        BIGINT NOT NULL
  UNIQUE (provider, identifier)               -- the identity rule, expressed once
```

* All creation paths obtain identity through **one** resolver
  (`findAccount(provider, identifier)`) instead of four hand-written chains.
* Google login first resolves `(GOOGLE, email)`; if absent, it may offer to *link* to an existing
  account only after proof (see D-3) — never a silent merge.
* `users` keeps `login_id` and the existing unique indexes for backward compatibility, so old clients
  and the current JWT contract continue to work; the identity table becomes the source of truth and the
  legacy columns become a denormalized projection maintained by the resolver.
* Migration is additive: backfill one row per existing account from
  `phone_number` (verified if the account was created by OTP) and `google_email` (verified, Google
  vouched for it).

**Pros:** small diff; no JWT change; no client change; fixes RC-1 and RC-5's *lookup* half; every
duplicate rule becomes one unique constraint.
**Cons:** `users` still holds identity columns (dual source of truth during transition); still one
membership per account, so multi-restaurant is not solved.

### Option B — Full person / credential / membership split

`[PROPOSED]`

```
persons            (id, display_name, created_at)
identities         (id, person_id -> persons, provider, identifier, verified_at)   UNIQUE(provider, identifier)
accounts           (id, person_id -> persons, is_active, token_invalidated_at, ...)  -- = today's `users`
memberships        (id, account_id -> accounts, restaurant_id, role, status)        UNIQUE(account_id, restaurant_id)
restaurants        (= today's restaurantprofiles)
subscriptions      (restaurant_id)   -- unchanged; billing stays business-owned
```

* Solves multi-restaurant-per-person, staff-at-two-shops, unified provider linking, ownership transfer
  as a membership operation, and "same person, two accounts" reconciliation.
* **Cost:** touches the JWT (a `membership_id`/active-restaurant claim), every
  `users.restaurant_id` read across sync, permissions, reports and billing, and the whole
  `RESTAURANT_ID` tenant resolution in `JwtRequestFilter`. That is a platform migration, not a fix.

### Option C — Do nothing structural; only patch Google login

`[PROPOSED, and rejected on the evidence]`

Adding `findByPhoneNumber` to the Google chain would "work" only if the Google account's email is
somehow already on the phone account — which phone signup deliberately leaves NULL (RC-1). Any other
behaviour means matching a phone account to a Google email with no proof of common ownership, which the
brief explicitly forbids. Rejected as unsafe.

### Recommendation (for your decision, not action)

**Option A now, Option B later if and only if multi-restaurant becomes a product requirement.**
Option A is the smallest change that makes identity resolution single-sourced, idempotent and provable,
without touching the JWT contract or the Android client. It also creates the `verified_at` fact that
RC-1's safe linking needs. Option B should not be attempted as part of a duplicate-account fix.

---

## 4. Account linking policy (the part that must not be guessed)

`[PROPOSED]` — rules for when a Google login may attach to an existing account:

1. **Never merge two live accounts automatically.** Not on matching name, not on matching phone.
2. **Link** only when the caller proves control of the identifier being added:
   * Google → existing phone account: no silent link. Respond `LINK_REQUIRED`; the client asks the user
     to confirm, then proves phone control with an OTP (the existing `mobile-update` challenge
     namespace can carry it), and only then is `(GOOGLE, email)` added to that account.
   * Phone → existing Google account: symmetric, OTP on the phone.
3. **First claim wins, then verification.** A brand-new `(provider, identifier)` always creates a
   *person with one account*; it never adopts an existing one.
4. **All linking is audited** (`SecurityAuditService` already exists) and reversible by support.
5. **Account enumeration is not worsened**: a link offer discloses that an account exists for an email.
   Accept (Google already revealed the email to its owner) but rate-limit and log it.

---

## 5. Multi-tenant rules to state explicitly (currently implicit)

`[PROPOSED]` — these are the invariants the current code enforces only by accident of its column layout:

| Invariant | Today | Target |
| :-- | :-- | :-- |
| Every restaurant has ≥1 `OWNER` membership | convention (`[VERIFIED]`: holds in prod) | enforce on membership change |
| Exactly one primary owner, transferable | implicit (`local_id=1` heuristic in `UserServiceImpl.getPrimaryUser`) | explicit `is_primary_owner` |
| A person may own several restaurants | impossible | allowed, one membership each |
| A staff person may serve several restaurants | impossible (phone unique) | allowed once membership is separate |
| Staff cannot hold `OWNER` | enforced (`enforceNewUserRoleForCaller`) | keep |
| Suspension suspends the business, not the person | `restaurantprofiles.is_suspended` | keep |

---

## 6. Decisions required from you

These block Phase 5 (solution design). None has been decided for you.

| ID | Decision | Options | Why it blocks |
| :-- | :-- | :-- | :-- |
| **D-1** | Target model | A (identity table) / B (person+membership) / A-now-B-later | Determines every migration and every repository signature |
| **D-2** | Is multi-restaurant-per-user a product requirement in the next 2 releases? | yes / no / not yet | If no, Option B is speculative complexity |
| **D-3** | Google-login linking UX | (a) `LINK_REQUIRED` + OTP proof; (b) Google-only new account is acceptable and linking is support-assisted; (c) block Google login for numbers that already exist | Directly decides what the returning user sees |
| **D-4** | Should `email` get case-insensitive uniqueness given it also stores phone numbers? | (a) new `lower(email)` partial unique index ignoring digit-only values; (b) stop writing phones into `email` and migrate existing values; (c) leave as-is and normalize only on write | RC-2 cannot ship without this |
| **D-5** | Are accounts allowed to exist without a verified phone? | yes (Google-only) / no | Decides whether `verified_at` is required for `PHONE` identities |
| **D-6** | Existing duplicate tenants in production: reconcile, or leave and prevent new ones? | reconcile with owner proof / prevent-only | Phase 6 scope; **no production write will happen without an explicit approval** |

> Three further questions surfaced later, while examining production data: **D-7** (is one live shop
> per `device_id` a real rule at all?), **D-8** (ship the device index?) and **D-9** (`/auth/check-user`
> as an existence oracle?). They are recorded, with the provisional D-8 resolution, in
> `ACCOUNT_ARCHITECTURE_DECISIONS.md`. This section intentionally lists only D-1…D-6 because those are
> the ones the identity model depends on.

---

## 7. Non-goals of this phase

* No schema change, no migration, no code change, no data change.
* No automatic merging of any production record, ever, on a matching identifier.
* No JWT contract change without an explicit decision (see D-1/D-2), because every live Android install
  and the web console depend on it.

---

## 8. Phase 4 conclusion

KhanaBook separates the **business** (restaurant) and **billing** cleanly. It does **not** separate the
**person** from the **credential** from the **membership**: all three are the same `users` row, keyed by
up to five loosely-related identifier strings with per-column uniqueness. That is the structural
condition that produces the reported duplicates, and it is why "fix the registration API" cannot work.

Two additive options are on the table (§3). Option A is recommended as the immediate target; Option B
is a separate platform programme. Six decisions (§6) must be answered before Phase 5 begins, per your
instruction that no implementation proceeds until you have reviewed and approved these findings.

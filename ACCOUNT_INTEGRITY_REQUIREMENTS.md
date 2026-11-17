# ACCOUNT INTEGRITY REQUIREMENTS

**Scope:** Phase 7 requirements engineering for the account identity work.
**Status:** For approval. No requirement below is implemented unless marked `[IMPLEMENTED]`.
**Date:** 2026-11-17

I use RFC-2119 language deliberately: **must** = required for acceptance, **should** = expected unless
justified, **may** = optional. Every requirement is derived from a verified finding (RC-n) or a decision
(D-n), not from preference.

---

## 1. Functional requirements

### 1.1 Identity resolution

| ID | Requirement | Source |
| :-- | :-- | :-- |
| FR-1 | Every account-creating or authenticating entry point **must** resolve identity through one shared resolver. A new path is not complete until it does. | RC-1, RC-5 |
| FR-2 | The resolver **must** match on *verified* identity only: phone proven by OTP, email proven by a Google-verified token. | RC-1, RC-5 |
| FR-3 | A Google sign-in for a human who already holds a phone account **must not** create a second account or a second tenant. | RC-1 |
| FR-4 | When automatic resolution is impossible, the API **must** answer with an explicit "verification required" outcome rather than silently creating an account. | RC-1, D-3 |
| FR-5 | A phone number and an email **must** be independently unique and independently case-normalised. | RC-2, D-4 |
| FR-6 | Identifier normalisation **must** happen before validation, not after it, so a variant never reaches a lookup in an unnormalised form. | RC-4 |

### 1.2 Idempotent registration

| ID | Requirement | Source |
| :-- | :-- | :-- |
| FR-7 | Repeating a registration that already succeeded **must** be idempotent: no new account, no new tenant. | RC-8 |
| FR-8 | Concurrent identical registrations **must** produce exactly one account and exactly one tenant. | `[IMPLEMENTED]` — enforced by the unique indexes; pinned by `PhoneIdentityIntegrityTest` |
| FR-9 | A duplicate registration **must** return a distinguishable contract (see §4), not a generic conflict. | RC-8 |
| FR-10 | A failed registration **must** leave no partial state (no orphan profile, no orphan subscription). | `[IMPLEMENTED]` — verified by rollback test |

### 1.3 Account lifecycle

| ID | Requirement | Source |
| :-- | :-- | :-- |
| FR-11 | An identity change (phone or email) **must** require proof of control of the *new* identifier, and **must** notify the *old* one. | `[IMPLEMENTED]` — `UserServiceImpl`, `EmailNotificationService` |
| FR-12 | Linking a second provider to an existing account **must** require proof of control of the identifier being added. | D-3 |
| FR-13 | Every identity mutation **must** write a security audit event. | RC-10 — `googleLogin` currently writes none |
| FR-14 | A soft-deleted account **must not** block re-registration of its number, and its history **must** be preserved. | `[IMPLEMENTED]` — `releaseIdentifierFromDeletedUsers` |

---

## 2. Non-functional requirements

| ID | Requirement | Target |
| :-- | :-- | :-- |
| NFR-1 | Registration and login p95 latency | ≤ 400 ms server-side (excludes WhatsApp delivery) |
| NFR-2 | Duplicate-account rate after release | 0 new class-D duplicates; measured by a dedicated metric |
| NFR-3 | Migration safety | Every migration applies to a restored production copy with no duplicate-key failure |
| NFR-4 | Startup | The application must start from a clean build with no manual intervention |
| NFR-5 | Backward compatibility | Existing Android builds and the web console keep working for ≥ 3 releases |
| NFR-6 | Observability | Every duplicate rejection emits a metric; every 5xx carries a searchable `errorId` |
| NFR-7 | Reversibility | Every change ships with a written rollback that has been exercised on a copy |

---

## 3. Business rules that must be preserved

Ratified from the current implementation — these are working and are **not** defects:

1. Phone number is globally unique (one WhatsApp channel per number).
2. A phone account can be created only after an OTP proves control of the number.
3. A soft-deleted account releases its number non-destructively.
4. A suspended business cannot authenticate on any path.
5. `SHOP_STAFF` cannot sign in to web admin.
6. Tokens are device-bound; a password change invalidates older tokens.
7. Subscription and payment ownership attach to `restaurant_id`, never to a person.
8. Staff cannot mint accounts.
9. One live shop per install **is a soft rule** — see FR-15.

**FR-15 (open):** the "one live shop per `device_id`" rule must be re-specified. Production has **16 real
businesses with bills** sharing a `device_id` with another live shop
(`ACCOUNT_DUPLICATE_DATA_REPORT.md` §3). A database constraint would either fail to apply or destroy
those. Requirement: hardware identity must not be a business identity.

---

## 4. API response consistency

One condition must have one contract. Today "already registered" returns **400** on the pre-check path
and **409** with a generic message on the race path.

| Condition | HTTP | Body | Notes |
| :-- | :-- | :-- | :-- |
| New account created | 200/201 | tokens + `restaurantId` | unchanged |
| Existing account recognised | 200 | tokens | unchanged |
| Verification required to link | 409 | `code: "LINK_REQUIRED"` | **new** (D-3) |
| Duplicate registration | 409 | `code: "ACCOUNT_EXISTS"` | unify the 400/409 split |
| Invalid credentials | 400 | generic message | keep — do not leak which field |
| Account disabled | 400/403 | `code: "ACCOUNT_DISABLED"` | unchanged |
| Business suspended | 403 | `code: "BUSINESS_SUSPENDED"` | `[IMPLEMENTED]` |
| Temporary failure | 503 | `code: "RETRY_LATER"` + `Retry-After` | **new** |

Backward compatibility: status codes for existing conditions must not change except where the brief
explicitly accepts a breaking change. Moving a 400 → 409 **is** a client-visible change and needs the
Android `BackendErrorParser` updated in the same release.

---

## 5. Security requirements

| ID | Requirement | Source |
| :-- | :-- | :-- |
| SR-1 | Account linking requires proof of control; no merge on a matching identifier. | RC-1 |
| SR-2 | OTP remains single-use, purpose-scoped, attempt-limited, TTL-bounded. | `[IMPLEMENTED]` |
| SR-3 | The existence oracle (`/auth/check-user`) must be documented as intentional, or removed. The client comment claiming it "always returns true" must be corrected either way. | RC-9 |
| SR-4 | Rate limiting must distinguish a legitimate retry from brute force; a 429 must carry a body and `Retry-After`. | RC-6 |
| SR-5 | JWT resolution must be deterministic: a lookup that can match several rows must not silently downgrade to "unauthenticated". | RC-7 |
| SR-6 | Identity changes must be audited with actor, old value, new value, timestamp. | RC-10 |
| SR-7 | Cross-tenant access must remain impossible; verified on every new endpoint. | `[IMPLEMENTED]` — `TenantContext` from signed token |

---

## 6. Acceptance criteria

The identity work is accepted only when all of the following are demonstrable:

1. A phone-registered owner signing in with Google reaches **their original tenant**, or receives
   `LINK_REQUIRED` — never a new tenant. *(FR-3, FR-4)*
2. Two concurrent signups for one number yield one account, one tenant, no orphan rows. *(FR-8)*
3. A repeated registration after a network timeout returns the same account outcome, not a 500. *(FR-7)*
4. No SQL exception reaches the client as a 500 for any identity condition. *(FR-9)*
5. `PhoneIdentityIntegrityTest` and its case-insensitive-email counterpart both pass with no `@Disabled`.
6. Every migration applies cleanly to a restored production copy. *(NFR-3)*
7. Existing Android and web-admin builds authenticate and sync unchanged. *(NFR-5)*
8. A tenant-isolation test proves owner A cannot read owner B's data through any endpoint.

---

## 7. Explicitly out of scope

* Multi-restaurant membership (unless D-2 says otherwise).
* Social providers other than Google.
* Changing the JWT claim shape (would force every install to re-authenticate).
* Any destructive data operation on production.

---

## 8. Traceability

| Requirement group | Finding | Design | Test |
| :-- | :-- | :-- | :-- |
| FR-1…FR-6 | RC-1, RC-2, RC-4, RC-5 | Option A (ADR 0002) | unit + integration |
| FR-7…FR-10 | RC-8 | resolver + unified contract | concurrency |
| FR-11…FR-14 | RC-10 | audit events | integration |
| SR-3…SR-5 | RC-6, RC-7, RC-9 | rate-limit + filter hardening | security |
| FR-15 | Class A data | product decision | dry-run |

# ACCOUNT SECURITY REVIEW

**Scope:** Phase 7 security review of the identity system and its current exposure.
**Mode:** Read-only. Findings verified by code reading and anonymous HTTP probes against the live host.
**Date:** 2026-11-17

Severity uses CVSS-style judgement: **Critical** = account/data takeover, **High** = cross-tenant or
identity compromise, **Medium** = meaningful information disclosure or availability, **Low** = hygiene.

---

## 1. Identity and authentication findings

| ID | Finding | Severity | Status |
| :-- | :-- | :-- | :-- |
| SEC-1 | Google sign-in cannot resolve a phone account → duplicate tenant; no ownership proof required to create a parallel business | Critical | Confirmed (RC-1) |
| SEC-2 | `email` uniqueness is case-sensitive while lookups are case-insensitive → login 500 + identity ambiguity | High | Confirmed (RC-2), reproduced |
| SEC-3 | `findByAnyIdentifier` can match >1 row; `JwtRequestFilter` swallows the exception → request continues unauthenticated | High | Suspected (RC-7) |
| SEC-4 | `googleLogin` flips `auth_provider` and overwrites `device_id` with no audit event | Medium | Confirmed (RC-10) |
| SEC-5 | `/auth/check-user` is a real existence oracle; the client's documented reason for ignoring it is false | Medium | Confirmed (RC-9) |
| SEC-6 | Auth rate limiting collapses legitimate retry into a bodyless 429, with no `Retry-After` | Low | Confirmed (RC-6) |
| SEC-7 | Platform PII CSVs sitting in the repository working tree | High (process) | Mitigated — now `.gitignore`d; file still present |

### SEC-1 detail — why it is Critical, not High

The attacker does not need credentials. A person who knows a merchant's phone number can sign in with
**their own** Google account, and if the merchant registered by phone (`email = NULL`, verified in
production: 34 such rows) the lookup misses and the system provisions a **new tenant** for the attacker.
No data crosses over — but the effect is a persistent phantom business, an orphaned trial subscription,
and a tenant that support cannot reconcile. It also makes the "same person" question unanswerable
(the 76 Google-with-no-phone accounts).

### SEC-2 detail

Reproduced: two rows holding `August@Example.com` and `august@example.com` coexist
(`uk_users_email` is byte-unique), and `POST /auth/login` then returns **HTTP 500**
`IncorrectResultSizeDataAccessException`. Any creation path that skips normalisation can reach this:
`AdminWriteService:112` and `BusinessWriteService:183,289`, plus `CreateStaffRequest.email`, which has no
validation at all.

---

## 2. Verified controls (working — do not regress)

| Control | Evidence |
| :-- | :-- |
| Tenant isolation | `TenantContext` derives from the **signed JWT claim**; every owner-scoped repository call filters `restaurantId.equals(...)`. Verified across staff, menu, category, billing services. No endpoint accepts a tenant id from the client for tenant data. |
| Role from database, not token | `JwtRequestFilter` re-reads the user row each request; a stale token cannot carry an escalated role. |
| OTP integrity | purpose-scoped challenge key, single use, 10-min TTL, 5-attempt cap, wrong-identifier invalidation, BCrypt-hashed at rest. |
| Password storage | BCrypt; `password_hash` marked `WRITE_ONLY` on the entity (never serialised). |
| Token lifecycle | JTI blocklist on logout, `token_invalidated_at` checked on every request, refresh rotation (old token revoked on use). |
| Device binding | `X-Device-Id` must match the token's device claim; browser ids exempt. |
| Soft-delete safety | identifier release tombstones rather than deletes. |
| Sync privilege | non-owner callers cannot create accounts (`enforceNewUserRoleForCaller` returns false → quarantined). |
| Web-admin surface | `SHOP_STAFF` rejected at login and at Google login. |
| Admin IP allowlist (app) | present in code, currently inactive because `ADMIN_ALLOWED_IPS` is unset — see SEC-9. |

---

## 3. Exposure of the running system (new, verified today)

These are configuration and deployment facts, not identity-code defects, but they change the account
system's real-world threat model.

| ID | Finding | Severity | Evidence |
| :-- | :-- | :-- | :-- |
| SEC-8 | **pgAdmin4 is publicly reachable** from any IP (allowlist removed at owner's request today). Only its internal login protects it; the stored connections in its config DB lead to the production database. | High | `/pgadmin4/browser/` → 302 → login; master user `adm***@iadv.cloud` active; no `limit_req` |
| SEC-9 | `/api/v1/actuator/prometheus` returns **200 anonymously**. `SecurityConfig` claims a reverse-proxy restriction that no longer exists (Apache is not running; nginx has none). | Medium | `curl` → 200 + live metrics |
| SEC-10 | `/api/v1/api-docs` + Swagger UI public: 253 paths, 125 schemas — full API surface map | Low–Medium | `curl` → 200 |
| SEC-11 | `/cdn/**` is enumerable: `<restaurantId>/logo_v<N>.webp`, `menu_items/item_<id>_v<N>.webp`. Anonymous 200s verified for another shop's assets. Directory listing correctly off (`/cdn/` → 403). No UPI/QR assets present. | Low | asset fetches returned 200 with no token |
| SEC-12 | Production DB credentials are readable via `docker inspect` and the same password appears in the deployment env | Medium | env inspection |

**Deliberate policy note.** SEC-8/SEC-10/SEC-11 are the consequence of a stated owner decision: "sites
public, login is the gate, no IP allowlists." This review records the decision and its residual risk
rather than re-litigating it. SEC-9 is *not* covered by that decision — it exposes live metrics, not a
login page — and should be closed.

---

## 4. Threat model (STRIDE, identity scope)

| Threat | Vector | Current control | Gap |
| :-- | :-- | :-- | :-- |
| **S**poofing | Google token with someone else's email | Google verifies the email | none for the token; the *linking* is the gap (SEC-1) |
| Spoofing | OTP guessing | 6 digits, 5 attempts, 10 min | adequate |
| **T**ampering | JWT claim edit | HMAC signature (`JWT_SECRET`) | adequate |
| Tampering | direct DB access | OS + DB auth | pgAdmin public (SEC-8) |
| **R**epudiation | identity change with no record | partial (`SecurityAuditService` exists) | Google login writes nothing (SEC-4) |
| **I**nfo disclosure | cross-tenant read | `TenantContext` scoping | none found |
| Info disclosure | enumeration | `check-user` rate-limited only by a shared 5/min IP budget | SEC-5 |
| **D**oS | auth endpoint flooding | interceptor 5/min/IP + DB limiter | aggressive enough to hurt legitimate users (SEC-6) |
| **E**levation | staff → owner via sync | `enforceNewUserRoleForCaller` | none found |

---

## 5. Prioritised remediation

1. **Close SEC-9** (prometheus). One nginx location or a `SecurityConfig` role check. No policy conflict.
2. **Decide SEC-8.** If pgAdmin stays public: unique long password + 2FA if supported + `limit_req` on
   `/pgadmin4/` + alert on `401` spikes + monthly patch cadence.
3. **SEC-1** — the identity fix itself (needs D-1…D-6). It is the only Critical item.
4. **SEC-2** — pending D-4. Cheap, isolated, testable.
5. **SEC-4** — add audit events on provider/identity change. Small, no dependency on D-1.
6. **SEC-3** — make the filter fail loudly: log at WARN and return 401 on a non-unique resolution
   instead of continuing unauthenticated.
7. **SEC-5** — either remove `check-user` or document it as an accepted oracle and correct the client
   comment (the comment is currently false, which is its own hazard).
8. **SEC-6** — return a JSON body + `Retry-After` on 429; raise the auth budget above a realistic
   retry pattern.
9. **SEC-12** — move DB credentials to a secret store; rotate.

---

## 6. Limitations

* No penetration test was performed; this is source-and-surface review.
* `POST /auth/google` could not be executed (no Google-signed token available), so SEC-1 is proven at
  the code and data layers only.
* GET-only anonymous probing; state-changing endpoints were not fired at production.
* No attempt was made to authenticate to pgAdmin.

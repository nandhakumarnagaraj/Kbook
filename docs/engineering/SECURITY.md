# KhanaBook — Security

> Security posture, secret handling, and the open remediation items. This is a working
> document for the team, not a marketing statement.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Related:
> `../product/ROLES_AND_PERMISSIONS.md`, `SYNC_CONTRACT.md`, `../runbooks/DEPLOYMENT.md`,
> `CREDENTIAL_ROTATION_PLAN.md`, `SECURITY_ROTATION_REQUIRED.md`.

## 1. Principles

1. **The server is the authority** for authentication, authorization, and tenant scope.
   Client-side visibility proves nothing.
2. **Offline-first must not become offline-authorization-forever** — permissions are
   revalidated against pulled revisions.
3. **Secrets never ship in the repo, in logs, or in a client bundle.**
4. **Tenant data is query-scoped.** A cross-tenant read must return 404, not 403.

## 2. Secrets inventory and policy

| Secret | Location | Policy |
|---|---|---|
| Database credentials, `JWT_SECRET`, Easebuzz keys | VPS `.env` | never committed; `.env` is git-ignored |
| `EASEBUZZ_MERCHANT_KEY`, `EASEBUZZ_SALT`, wire API key | environment | required at boot for the payment profiles |
| Android release signing + `RELEASE_VERSION_*` | `local.properties` | never committed; CI writes only stubs |
| Firebase `google-services.json` | per-developer, git-ignored | CI writes a stub file with real application ids but fake keys |
| SQLCipher DB passphrase | Android Keystore-backed prefs (`KeystoreBackedPreferences`) | 32 random bytes per install, migrated from the legacy scheme on first run |

Guards: `.github/workflows/guards.yml` → `guard-credential-files` fails a PR that tracks
a credential filename; `guard-v2-artifacts` blocks resurrected debug tooling.

## 3. Open rotation items (HIGH — carried since 2026-08-19)

From `SECURITY_ROTATION_REQUIRED.md`. Files were removed from tracking, but **the values
still exist in git history**:

| Item | Status |
|---|---|
| Rotate the PostgreSQL password (VPS + compose `.env`) | ☐ open |
| Rotate the Firebase API key | ☐ open |
| Rotate the Easebuzz salt if it was ever in `.env.v2` | ☐ open |
| Optional: purge history with BFG (`CREDENTIAL_ROTATION_PLAN.md`) | ☐ open |

Treat this section as the live checklist. A rotation is only complete when the new value
is in `.env`, the stack restarted, and the old value invalidated at the provider.

## 4. Authentication

- **JWT** (`jwt.secret`, default lifetime 180 days; terminal tokens configured
  separately) issued per surface/login; the token carries role and tenant.
- **Refresh tokens** are stored hashed (`refresh_tokens.token_hash`).
- **Revocation**: `token_blocklist` + in-memory `TokenRevocationCache`, checked by
  `JwtRequestFilter` on every request (DB fallback for older revocations). A revoked
  token is rejected with 401 `Token has been revoked`.
- **OTP flows** (`otp_requests`, unique challenge key, expiry index) cover signup,
  password reset, and mobile update.
- **Surface separation**: `SHOP_STAFF` cannot log in to web-admin; the server rejects the
  web surface before minting a token.

## 5. Authorization

| Layer | Mechanism |
|---|---|
| URL | `SecurityConfig` rules (`/admin/**` KBOOK_ADMIN, `/business/**` OWNER, `/sync/**` restaurant roles, actuator health open, `/actuator/**` KBOOK_ADMIN) |
| Method | `@RequireRole` + `RequireRoleAspect` |
| Permission | `PermissionService.hasPermission` against fixed role sets + `staff_permissions` rows |
| Push | `SyncPushGuard.requirePermission`, `MenuPushAuthorizer` |
| Offline | `OfflineAuthDecider` → ACCEPT / REJECT / QUARANTINE (+ `OfflineAuthClass` per operation) |

Known asymmetry to keep in mind: the trailing `.anyRequest().authenticated()` means any
endpoint without an explicit rule is open to every authenticated role — controllers must
carry their own gate. See `../product/PROBLEM.md` item 14.

## 6. Terminal and device identity

- Each device has a terminal identity; sync requests carry `X-Terminal-Token`, enforced
  strictly in production (`terminal.sync.strict=true`).
- Terminal management (`/business/terminals/**`) is OWNER-only; device onboarding
  (`/sync/terminal/**`) is allowed for OWNER and SHOP_STAFF.
- Bill identity is `(restaurant_id, device_id, local_id)` — a device cannot overwrite
  another terminal's bill.
- Print ownership is guarded in `PrintRouter` so a terminal cannot reprint or re-emit
  another terminal's KOT.

## 7. Client-side protections (Android)

| Control | Detail |
|---|---|
| Encrypted database | SQLCipher, one DB per restaurant, key held in Keystore-backed prefs |
| TLS | cleartext **disabled**; `network_security_config.xml` trusts system CAs only (not user-installed certs) |
| Certificate pinning | Three SPKI SHA-256 pins for `kbook.iadv.cloud`, `pin-set expiration="2028-06-04"`, with an in-file rollover runbook. **Read it before any server cert change** |
| Release build guards | signing, explicit version, real Google client id, HTTPS `BACKEND_URL` on the pinned host — all hard-fail |
| Logging | OkHttp body logging only when `BuildConfig.DEBUG` **and** the backend is not the production host |
| App lock | PIN/app-lock screen with `AppLockViewModel`; trusted external-app return handling |
| Crash reporting | `GlobalCrashHandler` |

## 8. Rate limiting and abuse

- `RateLimitingInterceptor` (with `rate_limit_attempts` persistence) returns **429** and
  honours `X-Forwarded-For` so limits work behind Apache.
- Login/OTP endpoints are the sensitive surface — keep them covered when adding new
  auth flows.

## 9. Audit trail

`security_audit_log` records privileged actions, including permission grant/revoke and
financial operations such as manual refunds (`MANUAL_REFUND`). Permission changes also
bump the staff revision, which is what makes offline revalidation possible.

**Gap:** `POST /admin/notifications/send` writes no audit event and does not validate the
target tenant (`../product/PROBLEM.md` item 6).

## 10. Hardening backlog

| Item | Risk |
|---|---|
| Admin push: validate tenant + audit every send | platform push to customer devices is currently unattributable |
| Untracked credential values still present in git history | rotate + optional history purge |
| `zomato_api_key` / `swiggy_*_secret` columns on `restaurantprofiles` | unused credential columns; confirm never serialized, then drop |
| `GET /restaurants/payment-config/easebuzz` readable by any authenticated tenant role | decide owner-only vs intended |
| Authorization style drift (URL rules vs annotations vs inline string checks) | a missed inline check inherits the authenticated-only fallback |
| Two mocking stacks / test isolation failures | weakens regression confidence, not a direct security issue |

## 11. Reporting a vulnerability

Internal channel only while the product is in its current phase: raise it in
`docs/product/PROBLEM.md` item list (or a private issue), include reproduction steps and
blast radius, and **do not** write the exploit into a public issue. For credential
exposure, follow §3 and record the rotation date here.

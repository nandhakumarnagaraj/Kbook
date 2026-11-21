# 08 — Security Audit

**Method:** configuration review (`SecurityConfig`, `JwtUtility`, `TokenStorageService`), pattern sweeps for secrets and injection, and manual tracing of the highest-risk paths (login identity, payments, tenant scoping, credentials at rest). **No penetration testing or dynamic scanning was performed.**

## 1. Verified controls (strengths)

| Control | Evidence |
| --- | --- |
| JWT secret only from environment, with a startup minimum-length check | `application.properties:81` `jwt.secret=${JWT_SECRET}`; dev/sandbox use `${JWT_SECRET:?…}` so the app refuses to boot without it; `JwtUtility` rejects short keys with an actionable message [V] |
| No hardcoded credentials in any main source tree | regex sweep for literal `secret/password/apikey/token` values across server, Android and web-admin returned only constant **names** [V] |
| Stateless sessions | `SessionCreationPolicy.STATELESS` [V] |
| Default-deny authorisation | `anyRequest().authenticated()` last in the matcher chain [V] |
| Owner-only matchers for login-identity mutation | `/sync/config/users/update-mobile*` / `update-email*` → `hasRole("OWNER")`, doubled by `@RequireRole(OWNER)` [V] |
| Admin surface separated | `/admin/**`, `/actuator/**` → `KBOOK_ADMIN` [V] |
| Token revocation | access-token jti blocklist + refresh-token revocation on logout, wrapped in try/catch so a malformed token cannot 500 the logout [C] |
| Security headers | CSP (default-src 'self', frame-ancestors 'none', object-src 'none'), HSTS 1 year incl. subdomains, `frameOptions` DENY, `Referrer-Policy: strict-origin-when-cross-origin` [V] |
| Web tokens in `sessionStorage`, not `localStorage`, with the reason documented | `token-storage.service.ts` [V] |
| Release build minified/obfuscated | `isMinifyEnabled = true` + ProGuard rules (`app/build.gradle.kts:123-130`) [V] |
| Android binds every user-facing backend message through a sanitiser | `UserMessageSanitizer.sanitizeBackendMessage` at each toast site [V] |
| No string-built SQL | 77 `@Query` declarations, all parameterised; no dynamic `WHERE` concatenation found in controllers/services [V] |
| Payment webhooks deliberately public, payment APIs deliberately not | comment + matcher in `SecurityConfig` [V] |
| Provider credentials removed from the tenant schema | `V9__remove_easebuzz_credentials_from_restaurantprofiles.sql` [V] |

## 2. Findings

### AUD-002 (P1) — plaintext credential at rest on the POS device [V]

The Google sign-in password captured at Quick Start is written to `SharedPreferences` as `google_password` (`SessionManager:124-126`) with no Keystore, no `EncryptedSharedPreferences`, no hashing. It is never transmitted and has no reader (`getPassword()` is uncalled). A POS device is often shared, resettable, or rooted; app-private prefs are readable on a rooted device and are included in some backup configurations.

**Fix options (smallest first):**
1. Remove the field and the `canSubmit` password condition (no credential, no risk) — requires product decision.
2. If it must exist: `EncryptedSharedPreferences` (AndroidX Security, already available via the AndroidX stack) and actually use it, or stop storing it locally and send it to a password-set endpoint.

**Also applies:** never log the value. No logging of it was found — keep it that way.

### AUD-003 (P1) — tenant isolation by convention, not enforcement [V]

55 files read `TenantContext`, but no Hibernate `@Filter` / `StatementInspector` / `@TenantId` exists. Cross-tenant read = one omitted `restaurant_id` predicate. With 46 unbounded list endpoints (AUD-006), one omission is a full-table disclosure, not a single record.

This is the highest-severity issue in the product because it is **silent**: no exception is thrown, logs look normal, and the response is valid JSON.

**Fix:** ORM-level tenant filter + admin bypass + one cross-tenant test per module (details in `05` and `15`).

### AUD-004 (P2) — `/actuator/prometheus` is unauthenticated [C]

`SecurityConfig:150-158` permits it because Prometheus cannot present a JWT, and delegates protection to `ops/apache-kbook.conf`. This is a documented, reasoned trade-off — but the control is **out of band**. It holds only as long as the app is unreachable except through that proxy. In-band protection (scrape token or separate management port) is preferable and is a configuration change, not a feature.

### AUD-005 (P2) — `X-Content-Type-Options: nosniff` disabled [V]

`SecurityConfig:103` explicitly disables `contentTypeOptions`. Every other header in the same block is hardened, so this reads as a deliberate workaround for something (an upload or a proxied asset), not an oversight. The effect is that browsers may MIME-sniff a response, which matters for any endpoint that echoes user-supplied content (logo upload, invoice HTML, help content).

**Fix:** re-enable `nosniff`, then fix whatever triggered the workaround (most likely a served asset with the wrong `Content-Type`). If a specific path genuinely needs sniffing, scope it there and document why.

## 3. OWASP Top 10 (2021) mapping

| Category | Status | Notes |
| --- | --- | --- |
| A01 Broken access control | **At risk** | application-level tenancy (AUD-003); otherwise strong role separation and default-deny |
| A02 Cryptographic failures | Minor | plaintext local credential (AUD-002); JWT/HSTS/CSP otherwise sound |
| A03 Injection | **No issue found** | parameterised queries only; no string-built SQL; Compose/Angular escaping by default |
| A04 Insecure design | Minor | unbounded list endpoints enable bulk exfiltration once access control fails (AUD-006) |
| A05 Security misconfiguration | Low | nosniff disabled (AUD-005); public metrics (AUD-004); CSRF disabled but stateless bearer tokens make this defensible |
| A06 Vulnerable components | **Unverified** | dependency versions not audited against CVE feeds in this pass (`mvn dependency-check`, `npm audit`, Gradle equivalents) |
| A07 Identification/authentication failures | Minor | IP-keyed OTP limiting may be bypassable by IP rotation — verify; login lockout exists (`sessionManager.clearLockout`) |
| A08 Software/data integrity | Low | offline bill push is idempotent via `(restaurant_id, device_id, local_id)`; no signature on queued payloads |
| A09 Logging/monitoring failures | Minor | request-id filter exists; broad `catch (Exception)` blocks can lose the cause (AUD-014) |
| A10 SSRF | Low | no user-supplied URL fetched server-side was identified; review `menu` image extraction and logo URL handling before closing |

## 4. Not verified

- Dependency CVE status for Maven, npm and Gradle.
- Webhook signature verification inside `EasebuzzWebhookService`.
- Effective `cors.allowed-origins` per environment.
- Android manifest exported components / backup flags (`android:allowBackup`).
- Actual reverse-proxy configuration on the deployed host.
- TLS termination and certificate configuration.

## 5. Priority

1. **AUD-003** tenant isolation (P1, silent data leak class).
2. **AUD-002** plaintext password (P1, product decision needed).
3. **AUD-004** in-band metrics restriction (P2).
4. **AUD-005** restore `nosniff` (P2).
5. Dependency CVE sweep + webhook signature verification (P2, unverified).

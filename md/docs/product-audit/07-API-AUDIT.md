# 07 — API Audit

**Measured:** 49 controllers, 285 mapped endpoints, base path `/api/v1` (e.g. `/api/v1/auth/login`, `/api/v1/sync/master/pull`).

## 1. Conventions observed [C]

- **Versioned prefix** `/api/v1` — present and consistent in the client (`KhanaBookApi.kt`) and the security matchers.
- **JWT bearer auth**, stateless (`SessionCreationPolicy.STATELESS`), with `/auth/refresh` for renewal and `/auth/logout` blocklisting the access token jti plus revoking the refresh token.
- **JSON error envelope** `{ "error": ..., "message": ... }` produced by a shared `authenticationEntryPoint` / `accessDeniedHandler`, so 401/403 are machine-readable and the client can sanitise them for the cashier.
- **Field-level validation errors** returned as a map and bound to individual form fields on Android (`UserMessageSanitizer.sanitizeWithDetails(...).fieldErrors`).
- **Forward-only, additive schema access** — no endpoint exposes raw SQL; 77 `@Query` declarations are parameterised JPQL/native methods, not string-built SQL. **No SQL-injection surface found** in the controller/service layer. [V]
- **Idempotency for offline replay** — the `bills(restaurant_id, device_id, local_id)` unique index makes a re-pushed bill a no-op rather than a duplicate. Sync push is explicitly a DTO boundary ("uses SyncDto, NOT Room entities — fix #1" in `KhanaBookApi.kt`), which prevents client-side schema details leaking into the API contract.

## 2. Authorisation map [V]

From `SecurityConfig`, in evaluation order:

| Path class | Rule |
| --- | --- |
| `/auth/login`, `/auth/refresh`, `/auth/signup/**`, `/auth/google`, `/auth/check-user`, `/auth/reset-password*`, `/auth/forgot-password/*` | `permitAll` (rate-limited in the controller) |
| `/payments/easebuzz/webhook`, `/payments/easebuzz/*/webhook`, `/payments/easebuzz/return` | `permitAll` — server-to-server callbacks |
| `/actuator/health/**` | `permitAll` |
| `/actuator/prometheus` | `permitAll` (see AUD-004) |
| `/actuator/**` | `KBOOK_ADMIN` |
| `/admin/**` | `KBOOK_ADMIN` |
| `/business/terminals/**`, `/business/terminal-requests/**`, `/business/**` | `OWNER` |
| `/sync/terminal/**` | `OWNER` or `SHOP_STAFF` |
| `GET /sync/master/pull` | any restaurant role |
| `/sync/config/users/update-mobile*`, `update-email*` | `OWNER` only |
| `/sync/**` | any restaurant role, then per-key permission checks |
| `/restaurants/logo`, `/menus/**` | `OWNER` / `KBOOK_ADMIN` |
| anything else | authenticated |

This is a well-reasoned default-deny chain (`anyRequest().authenticated()` last). The two risk points are the exempted actuator scrape and the breadth of `/sync/**`, both covered in `08`.

## 3. Findings

### AUD-006 (P2) — no pagination on growing collections [V]

6 controller signatures use `Pageable`/`Page`; 46 return `List<…>`. Endpoints whose tables grow without bound (bills, orders, payments, refunds, activity logs, sub-merchants) return the entire tenant (or platform) set.

**Recommendation:** paginate those, cap the rest. Keep responses additive.

### AUD-017 (P2) — public actuator surface depends on an out-of-band proxy rule [C]

`/actuator/prometheus` is `permitAll` with a comment that the network restriction lives in `ops/apache-kbook.conf` ("restricts /api/v1/actuator/prometheus to the monitoring host"). If the Spring app is ever reachable without that proxy — a direct container port, a tunnel, a staging host — metrics (including per-endpoint and per-tenant labels) are public.

**Recommendation:** make the restriction in-band. Either (a) require a static scrape token on `/actuator/prometheus` via a small filter, or (b) bind actuator to a management port that is not published. Then update the comment so it describes what the code enforces. Do not widen the matcher.

### AUD-018 (P3) — API surface breadth [C]

285 endpoints across 49 controllers for a single-tenant-per-user POS is large. Many are sync variants (`/sync/config/*`) of resources that also have REST endpoints. That is a legitimate design for offline-first, but it means two contracts to keep aligned for every field.

**Recommendation:** no consolidation now (that would be a redesign). Instead, document which resource is authoritative per field in `docs/api/` so the mobile sync path and the web-admin REST path cannot diverge silently.

## 4. What could not be verified

- **Rate limiting effectiveness** [U] — `isLoginAllowed` / `isOtpAllowed` exist and return 429, but the backing store, window size and per-IP vs per-account logic were not exercised.
- **Webhook signature verification** [U] — the endpoint is public by necessity; verification must happen in `EasebuzzWebhookService` and was not traced end-to-end.
- **CORS behaviour** [C] — `cors.allowed-origins` is an env property; the effective value in each environment was not inspected.
- **Payload limits / timeouts / retries** [U] — no server-side read of connection or request-size limits was performed.

## 5. Recommendations, ordered

1. In-band restriction for `/actuator/prometheus` (AUD-017).
2. Paginate unbounded endpoints (AUD-006).
3. Add an explicit per-account (not only per-IP) OTP rate limit — an attacker rotating IPs bypasses an IP-only bucket. Verify the current implementation first; if it already keys on the phone number, record that here and close the item.
4. Document authoritative-field ownership between `/sync/*` and the REST surface.

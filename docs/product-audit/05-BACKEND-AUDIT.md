# 05 — Backend Audit

`services/server` — Spring Boot 3.5.12, Java 17, Maven, Flyway, PostgreSQL.
**Measured:** 353 main classes, 49 `@RestController`, 285 mapped endpoints, 77 `@Query` declarations, 143 test classes.

## 1. Structure and layering [C]

`feature/<domain>/{controller, service, repository|data, entity|dto, util}` plus `core/{security, utility, config, exception}`. Controllers are thin: they resolve the caller from `Principal`/`TenantContext`, delegate to a service, and map to `ResponseEntity`. Business rules sit in services (`BusinessWriteService`, `GenericSyncService`, `SubMerchantService`). This is the correct direction of dependency and is followed consistently across all 16 modules.

## 2. Strengths [V]

- **Authorisation is layered, not assumed.** URL matchers in `SecurityConfig` plus method-level `@RequireRole` with an aspect that "fails closed on a null role (terminal staff tokens included)" (`REQUIREMENT_IMPLEMENTATION_GAPS.md` §2). Two independent gates for the highest-risk endpoints.
- **The dangerous paths are called out in code.** The `SecurityConfig` block explaining that staff tokens could re-point the owner's login identity is exactly the comment that prevents someone "tidying up" the matcher away.
- **Consistent error envelope.** `jsonAuthenticationEntryPoint` / `jsonAccessDeniedHandler` return `{error, message}`, and the client has a matching sanitiser.
- **Sanitised validation errors with field maps.** `UserMessageSanitizer.sanitizeWithDetails` produces `fieldErrors` the Android UI binds per field, instead of one opaque message.
- **Webhook routes are explicitly justified** as `permitAll` with a note that payment endpoints themselves still require a bearer token.
- **No hardcoded credentials.** A regex sweep for literal secrets across `services/server/src/main` returned nothing but constant *names*. JWT is `${JWT_SECRET}` with a startup length check (`JwtUtility`).
- **Bean validation** (`@Valid`) is applied on request bodies.

## 3. Findings

### AUD-003 (P1) — tenant isolation is application-level only [V]

Multi-tenancy is enforced by convention: services resolve the restaurant from `TenantContext` (55 files reference it) and pass `restaurantId` into queries. There is **no** `@FilterDef`/`@Filter`, no `@TenantId`, no `StatementInspector` and no `HibernatePropertiesCustomizer` anywhere in the codebase.

Why this is the highest architectural risk in the product:

- One forgotten `AND restaurant_id = ?` returns another restaurant's bills, staff, payouts or KYC.
- 46 controller methods return unbounded `List` results (AUD-006), so a leak is not a single row — it is the whole table for every tenant.
- The `/sync/**` surface is `hasAnyRole("OWNER","SHOP_STAFF")`, so the caller is not necessarily the tenant owner; the code itself notes the target is resolved *by tenant, not by caller*.

**Recommendation (defence in depth, no new feature):**
1. Add a Hibernate `@Filter` (or a repository base class) that applies `restaurant_id = :tenantId` to every tenant-owned entity, enabled from `TenantContext` per request.
2. Add `KBOOK_ADMIN` bypass explicitly, so admin reporting keeps working.
3. Write one cross-tenant test per module: authenticate as tenant A, request tenant B's id, assert 404/403.
This is a containment change; it should precede any new work on the 46 list endpoints.

### AUD-006 (P2) — unbounded collections, no pagination [V]

Only 6 controller signatures use `Pageable`/`Page`; 46 methods return `List<…>` directly. Concrete example: `AdminCommissionController:29` returns `subMerchantRepository.findAll()` — every sub-merchant on the platform, with payout/KYC fields, in one response.

**Impact:** memory and GC pressure proportional to tenant count; slow mobile responses on a cashier device; no way for a client to resume a large list.

**Recommendation:** add `Pageable` to the endpoints whose underlying table grows without bound (bills, orders, payments, refunds, activity logs, sub-merchants) and cap the rest with a hard `LIMIT`. Keep the response shape additive (a wrapper with `content` + `total`) so existing clients degrade rather than break.

### AUD-011 (P3) — dead password-reset capability [V]

`UserService.completePasswordReset(loginId, otp, newPassword)` is implemented at `UserServiceImpl:115` and declared in the interface, but **no controller calls it**; `/auth/reset-password` still routes to `resetPassword(phoneNumber, …)`. The Android counterpart (`AuthViewModel.completePasswordReset`) called a repository method that never existed and was removed during the AUD-001 repair.

**Consequence:** the commit message for `8a600882` claims "Server: completePasswordReset(loginId, otp, newPassword) method added", and the feature inventory would read it as done. It is not reachable. Either expose it (for Google/email owners, who have no phone number to reset with) or delete it. Product decision.

### AUD-014 (P2) — exception handling breadth [C]

Some service methods catch `Exception` and convert to a generic sanitised message (`catch (e: Exception) { … "Unexpected error. Please try again." }`). That is good for the user but loses server-side context when the cause is a bug rather than a client error.

**Recommendation:** in those catch blocks, log the throwable at `warn`/`error` with the request id (`RequestIdFilter` already exists) before returning the sanitised message. No API change.

## 4. Testing posture (detailed in `12`)

143 test classes against 353 main classes is a healthy ratio, and there are dedicated tests for business rules (`BusinessWriteServiceProfileIdentityTest`, `UserServiceEmailUpdateTest`, `EasebuzzWebhookTest`). The gap is not test quantity; it is that the Android compile gate did not block a red build.

## 5. Backend recommendations, ordered

1. Tenant isolation at the ORM boundary + cross-tenant tests (AUD-003).
2. Paginate/cap unbounded list endpoints (AUD-006).
3. Decide the fate of `completePasswordReset` (AUD-011).
4. Log the throwable in broad catch blocks (AUD-014).
5. Re-enable `nosniff` (AUD-005, see `08`).

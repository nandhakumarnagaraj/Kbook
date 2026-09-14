# User & Team Management — Code Review (2026-09-12)

Read-only review across server (Spring Boot), Android (Kotlin/Compose), web-admin (Angular).
Every finding cites `file:line`. Fixes favor reuse of existing patterns.

## What's solid

- **Authorization is server-enforced, not UI-only.** All staff-write and permission-mutation
  endpoints are gated with `@RequireRole(OWNER)` and enforced by an AOP aspect that reads the
  role from the JWT-derived `TenantContext` (`RequireRoleAspect.java:28-41`,
  `BusinessAdminController.java:99,102-124`, `PermissionController.java:66,80,95,109`).
- **Tenant isolation / IDOR resistance is strong.** `restaurantId` is always taken from the
  authenticated principal (`currentUser.getRestaurantId()`), never from the request body
  (`PermissionController.java:67-113`). Service mutations resolve the target via
  `requireTenantUser`/`findTenantUser` (`PermissionService.grantPermission:187`,
  `getUserPermissions:412`), templates check `template.getRestaurantId().equals(restaurantId)`
  (`PermissionService.applyTemplate`), and request approve/reject re-check
  `TenantContext.getCurrentTenant()` against the request's restaurant
  (`PermissionService.approveRequest`/`rejectRequest`). Staff writes filter
  `findById(userId).filter(u -> restaurantId.equals(u.getRestaurantId()))`
  (`BusinessWriteService.java:105-107,141-142,154-155`).
- **Token/authorization invalidation is consistent.** Deactivate and role-change both set
  `tokenInvalidatedAt` (`BusinessWriteService.java:128,145`), and `JwtRequestFilter` rejects
  tokens issued before it (`JwtRequestFilter.java:101-106`). Permission grant/revoke bump a
  monotonic revision (`PermissionService.bumpRevision`), and revoke stamps
  `lastRevokedRevision` for strict sync revalidation (`PermissionService.revokePermission`).
- **Offline-first authorization with a revision.** Android caches the granted set + revision in
  Room and restores on cold start (`PermissionManager.restoreFromCache`,
  `updateFromSync(perms, revision)`); OWNER short-circuits to all-true, SHOP_STAFF auto-gets the
  billing set (`PermissionManager.hasPermission:96-108`).
- **Rate limiting + audit on sensitive ops.** Permission requests are rate-limited
  (`PermissionService.submitRequest`), and grant/revoke are audited
  (`auditPermissionChange`).

## Findings

| # | Area | Finding | Evidence | Severity | Fix |
|---|------|---------|----------|----------|-----|
| 1 | Identity — Change Password | (FIXED this session) OTP target resolved from account's own identity, PHONE-only, never the shop number; Google accounts get a clear message | `ChangePasswordView.kt:80-88`, `SettingsSharedComponents.kt:49-54` | resolved | done |
| 2 | Staff create — soft-delete reuse | `createStaff` uses `existsByPhoneNumber`/`existsByLoginId`, which are NOT `isDeleted`-aware, unlike signup (`ensurePhoneNumberAvailableForSignup` + `releaseIdentifierFromDeletedUsers`). A removed-then-re-added staff phone can be permanently blocked | `BusinessWriteService.java:59-64` | Med | Reuse `findActiveByAnyIdentifier` + release tombstoned identifiers, mirroring signup |
| 3 | applyTemplate — no revision bump on mass revoke | `applyTemplate` revokes all existing perms by directly setting `granted=false` on each row WITHOUT calling `bumpRevision`/`lastRevokedRevision`; only the subsequent `bulkGrant` bumps. A permission removed by a template swap may not stamp a revocation revision, weakening strict sync revalidation for that key | `PermissionService.applyTemplate` (revoke loop) vs `revokePermission` | Med | Route template revokes through `revokePermission(...)` (or bump+stamp) so removed keys get a revocation revision |
| 4 | applyTemplate — not atomic vs concurrent grant | Revoke-all-then-grant is two phases under one `@Transactional` but without the per-user pessimistic lock used elsewhere; concurrent grant/apply could interleave | `PermissionService.applyTemplate` | Low | Acceptable for single-owner reality; document, or add row lock if multi-owner |
| 5 | Google account has no OTP recovery channel | Google users have null phone/whatsapp, so OTP-based reset/change cannot deliver; in-app change is now gated with a message but there is no working recovery path for a Google-auth account that forgets access | `AuthServiceImpl` google branch (`setWhatsappNumber(null)`), `ChangePasswordView.kt` (canUseOtpReset=false) | Low | Route Google in-app change through existing `/auth/change-password` (current+new) — needs one Retrofit binding |
| 6 | No `passwordInitialized` state | "Never set a password" is inferred only from an unguessable hash; login-failure copy can't distinguish "wrong password" from "you haven't set one yet" | `BusinessWriteService.java:71,80`, `User.java` (no flag) | Low | Optional `passwordInitialized` boolean (schema migration) for clearer first-login UX |
| 7 | PIN fast-switch not exposed on Android | Server has `setPin`/`pinLogin` (per-user 4–6 digit) but no Android UI, so competitor-style fast staff switching on a shared terminal is unavailable | `AuthServiceImpl.setPin`/`pinLogin`, no binding in `KhanaBookApi.kt` | Low (feature) | Add Settings PIN screen + PIN-login on shared terminal; server already supports it |
| 8 | Client permission set is advisory | Android `PermissionManager` is the UX gate; correctness must rest on server re-check at write/sync time. Confirm every privileged sync/write path re-authorizes server-side (menu edits use revision revalidation; verify void/refund/staff writes are all server-gated) | `PermissionManager.hasPermission` (client), server `@RequireRole` + sync revalidation | Low (verify) | Ensure no privileged mutation trusts client-only checks; add server authorizer tests where missing |

## Top 5 prioritized suggestions

| Rank | Suggestion | Size | Files |
|------|------------|------|-------|
| 1 | **Make staff-create soft-delete-aware** (Finding 2) — reuse signup's tombstone-release so re-adding a previously-removed staff phone works | M | `server/.../webadmin/service/BusinessWriteService.java` (+ existing `UserRepository`/`AuthServiceImpl` helpers) |
| 2 | **Fix applyTemplate revocation revision** (Finding 3) — route template revokes through `revokePermission` so removed keys stamp a revocation revision | S | `server/.../service/PermissionService.java` |
| 3 | **Expose the existing PIN fast-switch** (Finding 7) — competitor parity, no schema churn; server endpoints already exist | M | `Android/.../data/remote/api/KhanaBookApi.kt`, new Compose PIN screen, `AuthViewModel` |
| 4 | **Working recovery for Google accounts** (Finding 5) — wire in-app change through `/auth/change-password` (current+new) for non-PHONE accounts | S–M | `Android/.../applock/ChangePasswordView.kt`, `Android/.../data/remote/api/KhanaBookApi.kt` |
| 5 | **Add server authorizer tests** (Finding 8) — lock in that privileged mutations reject a SHOP_STAFF/foreign-tenant caller at the server, preventing future client-trust regressions | S | `server/src/test/.../authz/*` |

## Bottom line

The subsystem is well-designed: server-enforced RBAC, principal-derived tenancy (no IDOR),
consistent token/revision invalidation, offline-first permission caching, rate limiting and
audit. No Critical/High correctness or security holes found (the one High — Change Password
identifier — was fixed this session). Remaining items are one Medium data-integrity gap
(soft-delete reuse), one Medium consistency gap (template revoke revision), and low-severity
feature/UX improvements.

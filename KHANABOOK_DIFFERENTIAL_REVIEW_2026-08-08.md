# KhanaBook v1 Review Validation

## Executive Summary

| Severity | Confirmed |
|---|---:|
| Critical | 0 |
| High | 0 |
| Medium | 6 |
| Low / technical debt | 7 |

**Overall risk:** Medium
**Recommendation:** Conditional approval after the confirmed sync and cart correctness gaps are scheduled.
**Key result:** 8 of the 22 submitted findings are false, obsolete, or unsupported by the current code. Finding 3 was confirmed and fixed in this review.

## What Changed

**Branch:** `main`
**Codebase size:** 410 Android/server/web-admin source files (surgical review)
**Review basis:** current working tree, including uncommitted v1 refactoring

| File | Change | Risk | Blast radius |
|---|---|---|---|
| `BillingViewModel.kt` | Move all restoration suspension before the final generation check | Medium | Low: one production caller |
| `TerminalRequestFilter.java` | Earlier review fix: reject missing, malformed, deleted, or cross-tenant terminal identities | High | Broad: terminal-token requests |
| `TerminalTokenSecurityTest.java` | Regression coverage for invalid terminal identities | Low | Test only |

Recent relevant history includes `ad0d2623` (billing sync and terminal hardening), `23108256` (pending UPI draft fix), and `bf2c0d6c` (body-size limit and security fixes).

## Findings Validation

| # | Verdict | Corrected severity | Evidence |
|---:|---|---|---|
| 1 | Rejected | None | `clearContext()` runs in `finally` only after `chain.doFilter()` returns. Removing it would risk request-thread authentication leakage; it does not clear context from downstream synchronous filters. `@Async` propagation requires an explicit delegating executor and is unrelated. |
| 2 | Rejected as bug | Low design note | `SyncManager` is `@Singleton`; its process-lifetime scope is intentional. Process death cancels it. Lifecycle scope would incorrectly cancel application sync when UI backgrounds. |
| 3 | Confirmed and fixed | Medium | `restorePendingOnlineBill()` had multiple repository suspension points after its sole generation check, allowing reset state to be overwritten later. All loading now finishes before the final check and non-suspending commit. |
| 4 | Rejected | None | `getActiveDraftBillsFlow()` passes both `restaurantId` and terminal scope; the DAO query filters `restaurant_id = :restaurantId`. Isolation coverage exists in `BillDaoIsolationTest`. |
| 5 | Confirmed performance issue | Medium | JWT authentication can execute four sequential identifier lookups. A one-query replacement must preserve current identifier precedence and handle cross-field collisions; a naive derived `OR` returning `Optional` can throw on multiple matches. |
| 6 | Confirmed resilience gap | Medium | Debounced immediate sync has no short retry/backoff. Periodic WorkManager remains the eventual fallback, so this is not data loss. |
| 7 | Partially confirmed | Medium | A 5 MiB Tomcat post limit is configured, but JSON/body-limit behavior should be verified at the deployed Apache and embedded-Tomcat layers; multipart limits do not protect JSON sync payloads. Add an integration test before claiming full mitigation. |
| 8 | Confirmed smell | Low | Nullable inventory and kitchen dependencies widen `BillRepository` responsibilities; this is refactoring debt, not a runtime defect. |
| 9 | Rejected as written | None | `setAllowedOrigins` does not interpret `*.iadv.cloud` as a wildcard pattern. `*` combined with credentials is rejected by Spring validation rather than silently enabling credential theft. Production origin pinning remains sensible hardening. |
| 10 | Rejected / already fixed | None | `jwtInterceptor` already clears token storage and redirects to `/login` on HTTP 401, and is registered in `app.config.ts`. Refresh-token rotation is an optional product/security enhancement. |
| 11 | Confirmed audit gap | Low | Admin tenant override emits a warning log but does not persist `SecurityAuditEvent`. Access is already role-gated; this is compliance/auditability rather than impersonation bypass. |
| 12 | Confirmed correctness issue | Medium | `CartManager.addToCart()` reloads the item but does not reject unavailable items or variants. The API currently returns `Unit`, so a proper fix should return a typed result for UI feedback instead of silently ignoring the add. |
| 13 | Confirmed intentional weakness | Medium | Device mismatch is warn-only. Exploitation requires a stolen valid JWT; terminal tokens have separate binding and credential-version controls. |
| 14 | Resolved in working tree | None | Current uncommitted server and Angular changes implement pageable order retrieval with total counts and page navigation. |
| 15 | Confirmed configuration debt | Low | UPI limit is compiled into `PaymentLimits`; remote configuration would allow policy changes without an app release. |
| 16 | Confirmed technical debt | Low | Saved cart JSON has no explicit schema version. Gson compatibility reduces immediate risk but does not provide migration guarantees. |
| 17 | Rejected | None | `computeSummary()` still calculates subtotal and total when profile is null; it does not return all zeros unless the cart is empty. |
| 18 | Recommendation only | None | BCrypt cost 12 is an accepted password hash configuration. Argon2id migration can be planned but is not a defect. |
| 19 | Informational only | None | The 15-minute WorkManager minimum is a platform constraint, not a code issue. |
| 20 | Partially confirmed | Low | General 500 errors are sanitized with an error ID. `IllegalArgumentException` text is returned, but current uses are mostly deliberate client-facing validation messages; audit future throw sites for sensitive details. |
| 21 | Confirmed hardening debt | Low | CSP includes `script-src 'unsafe-inline'`. Remove it only after verifying Google sign-in and Angular production output under a report-only policy. |
| 22 | Rejected as proposed | None | Room has no JPA-style `@Version` optimistic-lock annotation. Concurrent draft protection requires conditional DAO updates/transactions and a version column, backed by a reproduced lost-update case. |

## Critical Findings

No critical or high-severity finding from the submitted list was validated.

### Medium: Restoration can overwrite a newer billing session

**File:** `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt`
**Blast radius:** One UI caller (`PaymentStep`)
**Test coverage:** Existing generation-guard logic tests; focused suite passes

Before the fix, reset could increment the generation while restoration was suspended during payment/menu lookups. The resumed coroutine then wrote stale payment or cart state. The fix prepares recovery and cart data first, performs the final ownership check, and commits without further suspension.

### Medium: Unavailable menu items can enter a new cart

**File:** `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/CartManager.kt`
**Blast radius:** Four cart-ingress paths
**Test coverage:** No direct CartManager availability test found

A stale UI item can reach `addToCart`; the repository refresh is performed but `isAvailable` is ignored. Recommended fix: return an `AddToCartResult` covering unavailable item/variant and stock conditions, then surface it through manual, barcode, and OCR entry paths.

## Test Coverage Analysis

- ✅ `gradlew testDebugUnitTest --tests com.khanabook.lite.pos.BillingLogicTest`
- ✅ Android debug compilation completed as part of the focused test run.
- ✅ Targeted `git diff --check` passed for the earlier terminal-token fix.
- ⚠️ Server tests remain blocked because Maven and a Maven wrapper are unavailable locally.
- ⚠️ The restoration test suite models generation behavior but does not instantiate the ViewModel with controlled suspension.

## Blast Radius Analysis

| Function/path | Callers | Priority |
|---|---:|---|
| `restorePendingOnlineBill()` | 1 | P2 |
| `getActiveDraftBillsFlow()` | 2 | No issue |
| `JwtRequestFilter` | All authenticated server requests | P1 performance follow-up |
| `CartManager.addToCart()` | 4 ingress paths | P1 correctness follow-up |

## Historical Context

- Security-context cleanup dates to the initial JWT filter implementation (`8805b173`); it is not a recent regression.
- Recent commits explicitly added body-size and terminal/billing hardening, making findings 7 and 14 stale or partially stale against the working tree.
- No security validation removal was found in the reviewed changes.

## Recommendations

### Before production

- Add a typed unavailable-item result in `CartManager` and direct tests.
- Add bounded exponential retry for failed immediate sync while retaining WorkManager as the durable fallback.
- Verify the effective JSON request limit through Apache and embedded Tomcat with an oversized authenticated integration request.
- Persist admin tenant overrides through `SecurityAuditService`.

### Technical debt

- Replace four JWT user lookups with one collision-safe repository query or a short-lived cache with explicit invalidation.
- Version saved-cart JSON.
- Trial a CSP without inline scripts in report-only mode.

## Analysis Methodology

**Strategy:** Surgical review of a 410-file multi-module repository.
**Coverage:** All 22 submitted claims were traced to current code; high-risk authentication claims received history and filter-order review.
**Techniques:** current-tree inspection, git blame/log, caller search, test search, adversarial reachability checks, and focused Android execution.
**Limitations:** no live Apache/VPS verification, no server test execution, and no performance benchmark for JWT database queries.
**Confidence:** High for rejected claims 1, 4, 9, 10, 17, and 22; medium overall.

# KhanaBook POS — Codebase & Product Audit — Overview

- **Date:** 2026-11-13
- **Repository:** `nandhakumarnagaraj/Kbook`, branch `main` @ `a733025b`
- **Method:** static inspection of the repository plus targeted build/test execution. No device session was performed for this audit.
- **Constraint honoured:** no features were added, no product scope was expanded, and no recommendation below was implemented. The only code changes made during the audit session were repairs that restored compilation (see AUD-001); everything else is a recommendation awaiting authorization.

## 1. What the product is

KhanaBook Lite is a restaurant point-of-sale system with three deliverables in one monorepo:

| Surface | Path | Stack |
| --- | --- | --- |
| Android POS (owner + staff, offline-first) | `apps/android` | Kotlin 2.x, Jetpack Compose, Room, Hilt/KSP, `compileSdk 36`, `minSdk 26` |
| Web Admin (owner console + KBOOK_ADMIN) | `apps/web-admin` | Angular 18.2, TypeScript 5.5 |
| API / backend | `services/server` | Spring Boot 3.5.12, Java 17, Maven, Flyway, PostgreSQL |

Sizes measured with `wc -l` / `find` on 2026-11-13:

- 353 Java main-source files on the server, 261 Kotlin main-source files on Android.
- 148,450 source lines across the three surfaces.
- 49 `@RestController` classes exposing 285 mapped endpoints.
- 103 Flyway migrations, latest `V121__widen_otp_requests_phone_number.sql`.
- 143 server test files, 97 Android test files (unit + instrumented), 46 Angular spec files.

## 2. Evidence levels used in this audit

Every claim below carries one of these markers, because a feature that merely has a UI or an endpoint is not a working feature:

- **[V]** **Verified** — read the implementation and, where stated, executed a check (`./gradlew :app:compileDebugKotlin`, `git`, `grep`, `find`).
- **[C]** **Code-level** — the implementation was read and is internally consistent, but the behaviour was not executed.
- **[I]** **Inferred** — concluded from configuration or structure, not from a run (e.g. CI gating).
- **[U]** **Unverified** — could not be established from the repository; listed so it is not mistaken for an assumption.

Per `AGENTS.md`, this audit does not implement its own recommendations, and it does not delete or overwrite historical findings. Related prior evidence lives in `ANDROID_POS_DOCUMENTATION/` (device sessions), `REQUIREMENT_IMPLEMENTATION_GAPS.md` (open identity/OTP gaps), and `docs/audit/` (earlier web-admin audits).

## 3. Headline findings

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| AUD-001 | P1 | `main` did not compile. Commit `8a600882` landed three separate breaks in the password/quick-start work. Repaired during this session; `:app:compileDebugKotlin` now passes. | [V] |
| AUD-002 | P1 | The Google-account password collected at Quick Start is stored **plaintext** in `SharedPreferences`, is never sent to the server, and has **no reader** — while the UI now blocks submission until it is filled. | [V] |
| AUD-003 | P1 | Tenant isolation is **application-level only**: 55 files consult `TenantContext`, but there is no Hibernate filter/interceptor enforcing `restaurantId`. 46 controller methods return unbounded `List`; only 6 use `Pageable`. One missing predicate is a cross-tenant read. | [V] |
| AUD-004 | P2 | `/actuator/prometheus` is `permitAll()`. The mitigation is documented as a reverse-proxy rule; the deployed proxy config was not inspected. | [C] |
| AUD-005 | P2 | `X-Content-Type-Options: nosniff` is explicitly disabled. | [V] |
| AUD-006 | P2 | 46 endpoints return unbounded collections (all rows for a tenant), including `findAll()` over sub-merchants. | [V] |
| AUD-007 | P2 | The Android instrumented suite (30 files) is `continue-on-error: true` and has never run, per the workflow's own comment. | [V] |
| AUD-008 | P3 | Three stale, untracked duplicate trees sit at the repo root (`server/`, `web-admin/`, `Android/`). | [V] |
| AUD-009 | P3 | God-files concentrate risk: 3,901-line Angular dashboard, 2,398-line menu page, 2,387-line `AppDatabase.kt`, 2,054-line `MasterSyncProcessor.kt`. | [V] |
| AUD-010 | P3 | Web-admin support tickets are persisted to `localStorage` in addition to the server. | [V] |
| AUD-011 | P3 | `UserServiceImpl.completePasswordReset` exists but no controller exposes it — dead server code matching the dead Android method removed in AUD-001. | [V] |

This table is the headline set. Findings discovered later in the per-area passes are numbered `AUD-012` … `AUD-027` and are defined where they are evidenced: `04` (AUD-012, AUD-013), `05` (AUD-014), `06` (AUD-015, AUD-016), `07` (AUD-017, AUD-018), `09` (AUD-019, AUD-020), `10` (AUD-021 … AUD-024), `11` (AUD-025, AUD-026), `12` (AUD-007 detail), `13` (AUD-027).

## 4. Overall assessment

**Quality is materially above the median for a project of this size.** The security posture is deliberate rather than accidental: environment-only JWT secrets with a length check, role-specific matchers (including owner-only matchers for login-identity endpoints), a CSP, HSTS, stateless sessions, `sessionStorage` rather than `localStorage` for web tokens, ProGuard/R8 on release, and no hardcoded credentials anywhere in the three main source trees. Comments state *why* a guard exists and cite the bug that motivated it, which is the single strongest maintainability signal in the repo.

The weaknesses are concentrated in three places:

1. **The verification gate is weaker than it looks.** Five workflows exist, yet red main got through, and one whole test tier is knowingly non-blocking.
2. **Multi-tenancy is a convention, not a mechanism.** With 46 unbounded list endpoints and no ORM-level tenant filter, the blast radius of a single forgotten predicate is another restaurant's data.
3. **Credential handling in the newest flow is not finished.** A password is collected, demanded, persisted in the clear, and then never used.

None of the above requires new features to fix. All of them are addressed in `14-IMPROVEMENT-ROADMAP.md` and `15-IMPLEMENTATION-CHECKLIST.md`.

## 5. What this audit did **not** establish

- No device or emulator session: touch behaviour, printer output, offline replay, and midnight rollover remain as recorded in `ANDROID_POS_DOCUMENTATION/UNVERIFIED_ASSUMPTIONS.md`.
- Test suites were not executed end-to-end (server `mvnw test`, Angular `ng test`, Android instrumented). Counts and gating were inspected; pass/fail was not re-established.
- The deployed reverse proxy (`ops/apache-kbook.conf`) was not verified against the running host, so AUD-004 remains open.
- No load or query-plan measurements were taken, so performance findings are structural, not measured.
- Payment-provider sandbox behaviour (Easebuzz) was not exercised.

## 6. Reproducing this audit

```bash
git log --oneline -3                     # audit baseline: a733025b
cd apps/android && ./gradlew :app:compileDebugKotlin --offline   # AUD-001 check
grep -rn "TenantContext" services/server/src/main/java | wc -l   # 55
grep -rE "@(Get|Post|Put|Patch|Delete)Mapping" services/server/src/main/java | wc -l   # 285
find services/server/src/main/resources/db/migration -name "*.sql" | wc -l             # 103
```

## 7. Resolutions made after the audit (2026-11-13, same session)

The audit's own constraint is "no new features" — these are repairs, not features. Every one was verified by compiler and/or test run.

| Item | What changed | Verification |
| --- | --- | --- |
| AUD-001 | Android compile breaks repaired (`ChangePasswordView` ×2, dead `completePasswordReset`, missing `PasswordField`) | `:app:compileDebugKotlin` exit 0 |
| **AUD-028 (new, P0)** | The **server also did not compile**: `UserServiceImpl` imported a non-existent `PasswordEncoder`, injected the non-bean `PhoneNormalizer`, and had lost six private helpers plus its OTP service field. Restored from `31150be3`, re-implemented against the current call sites and tests. | `mvnw compile` exit 0; `UserServiceEmailUpdateTest` + `UserServiceMobileUpdateTest` green |
| **AUD-029 (new, P0)** | Master pull poisoned its own transaction: `subscriptionService.getSubscriptionSummary` lazy-**inserts** a default subscription but ran inside `pullMasterSync`'s `@Transactional(readOnly = true)`, so PostgreSQL aborted the tx and every later query failed. Moved it to `REQUIRES_NEW`. | `SourceChannelSyncContractTest`, `BillLifecycleSyncPostgresIntegrationTest`, `ConcurrentBillPushTest`, `LwwConflictResolutionIntegrationTest`, `MasterSyncTerminalIsolationTest` now green |
| AUD-002 | Quick Start password field, its `canSubmit` gate, `SessionManager.savePassword/getPassword` and the plaintext `google_password` pref removed entirely | compile + unit tests exit 0; `grep google_password` → none |
| AUD-005 | `X-Content-Type-Options: nosniff` re-enabled | compiles; header emitted by Spring Security default |
| AUD-011 | Dead, OTP-skipping `completePasswordReset` deleted; `/auth/reset-password` already resolves email logins via `findByEmailIgnoreCase` | `grep completePasswordReset` → none; auth tests green |
| AUD-019 | Duplicate CI removed: `android-ci.yml` keeps lint + instrumented; `web-admin.yml` keeps build and now runs all ten `check:*` gates, dropping the duplicated Karma run | both YAML files parse |
| — | `createFallbackPlan` granted 5 terminals while V120 seeds PRO with 3 — an entitlement mismatch when the plan table is empty. Set to 3. | `TerminalManagementPostgresConcurrencyTest` green |
| — | `TerminalManagementPostgresConcurrencyTest` rewritten to the seeded 3-terminal limit | green |

### Still open (deliberately)

- **AUD-003** (tenant isolation) and **AUD-006** (unbounded lists) are architectural; they need a test cycle across 16 modules, not a blind edit. Unchanged.
- **`TerminalManagementConcurrencyTest`** still hardcodes a 5-terminal limit across ~12 assertions against a plan that allows 3. Fix = assert against `getSubscriptionSummary().getMaxTerminals()` or seed ENTERPRISE in `setUp`.
- **AUD-004, AUD-007, AUD-009, AUD-010, AUD-012, AUD-015, AUD-020, AUD-021, AUD-023, AUD-027** unchanged — see `14` and `15`.
- **Full server suite (completed):** 955 tests, 2 failures, 0 errors — down from 6 failures + 5 errors. The two remaining are order-dependent 401s in `ConcurrentBillPushTest` and `TerminalAccessControlGapTest`; both pass when run together in isolation, so this is cross-class test interference, not a product defect.

### Corrections to this audit's own findings

- **AUD-008** (stale root duplicates): already ignored at `.gitignore:164-168`. The recommendation to "add them to .gitignore" was wrong; deletion remains a local cleanup.
- **AUD-026** (OTP verified badges reset): **withdrawn**. `LaunchedEffect(profile)` also repopulates every form field from the profile, so resetting the verified flags to `true` matches the values now on screen. It is consistent, not a bug.

## 8. Document index

`01` structure · `02` modules · `03` features · `04` frontend · `05` backend · `06` database · `07` API · `08` security · `09` architecture · `10` performance · `11` UI/UX · `12` testing · `13` debt · `14` roadmap · `15` checklist.

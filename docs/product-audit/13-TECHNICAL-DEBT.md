# 13 — Technical Debt Register

Debt is classified by **what happens if it is not paid**, not by how ugly it looks. Priorities follow the requested scheme: P0 critical security/data integrity, P1 major functional/architectural, P2 important quality/performance, P3 maintainability.

| ID | P | Debt | File / location | Consequence if unpaid | Effort |
| --- | --- | --- | --- | --- | --- |
| AUD-003 | P1 | Tenant scoping enforced by convention only | `core/security`, 55 files reading `TenantContext`; no Hibernate `@Filter` | Silent cross-tenant data disclosure | M (1 filter + per-module tests) |
| AUD-002 | P1 | Plaintext, unused Google password demanded at onboarding | `SessionManager.kt:20,120-126`; `QuickStartViewModel.kt:112`; `QuickStartScreen.kt` | Credential exposure on shared/rooted POS; user misled | S (remove field) / M (make it real) |
| AUD-001 | P1 | Non-compiling commit reached `main` | `8a600882` | Any release built from `main` was impossible; already repaired | **Done** |
| AUD-007 | P2 | Instrumented suite permanently non-blocking and never run | `.github/workflows/android-ci.yml` (`continue-on-error: true`) | 30 test files of false assurance; device-only bugs ship | S–M (triage) |
| AUD-006 | P2 | 46 unbounded list endpoints, 6 paginated | `*/controller/*.java`; `AdminCommissionController:29` | Memory/latency grow with tenant count; bulk exfiltration if access control fails | M |
| AUD-004 | P2 | Prometheus unauthenticated, protection out of band | `SecurityConfig.java:150-158` | Metrics exposure if app reachable off-proxy | S |
| AUD-005 | P2 | `nosniff` disabled | `SecurityConfig.java:103` | MIME-sniffing on user-influenced responses | S |
| AUD-012/AUD-025 | P2 | Two hand-written OTP state machines in one composable | `ShopConfigSection.kt` (~973 lines) | Behaviour drift between phone and email verification | M |
| AUD-020 | P2 | Sync layer extended by editing central switches | `GenericSyncService.java` (1,704), `MasterSyncProcessor.kt` (2,054) | Every new field touches the two riskiest files | L |
| AUD-023 | P2 | Dirty-state recomputation with 10-key `remember` | `ShopConfigSection.kt` | Avoidable recomposition per keystroke | S |
| AUD-015 | P2 | 5/18 Room entities without indices; all migrations in one 2,387-line file | `AppDatabase.kt` | A bad `ALTER` reaches a cashier; migration tests non-blocking | M |
| AUD-009 | P3 | God-components | 3,901-line dashboard, 2,398-line menu page, 1,695/1,464/1,462 others | Changes are unreviewable; small fixes avoided | L |
| AUD-008 | P3 | Stale untracked duplicate trees | root `server/`, `web-admin/`, `Android/` | Greps/IDE index a second codebase | S |
| AUD-010 | P3 | Support tickets mirrored to `localStorage` | `support-tickets-page.component.ts:276,287` | Stale/duplicated admin state across users | S |
| AUD-011 | P3 | Dead password-reset capability | `UserServiceImpl.java:115`, `UserService.java:25` | Module map advertises an unreachable feature | S (delete or expose) |
| AUD-013 | P3 | Business rules inside composables | `ShopConfigSection.kt` save validation | Rules cannot be unit-tested | S |
| AUD-014 | P2 | Broad `catch (Exception)` without logging the cause | service layer | Bugs look like client errors; retry storms | S |
| AUD-019 | P3 | Duplicate CI workflows | `ci.yml` + `android-ci.yml`, `web-admin.yml` | Two gates to drift; doubled CI cost | S |
| AUD-026 | P3 | OTP verified-state reset keyed on whole profile | `ShopConfigSection.kt` `LaunchedEffect(profile)` | Verification indicator resets spuriously | S |
| AUD-027 | P3 | No dependency CVE sweep | Maven/npm/Gradle | Unknown vulnerable transitive deps | S |

## Debt categories

**Paid-down (credit where due):** no hardcoded secrets; secrets removed from schema (V9); tenant-leading composite indexes from V1; idempotent offline push; CSP/HSTS installed; tokens in `sessionStorage`; 10 machine-enforced web-admin style checks; sanitiser on every user-facing backend message; comments that record the *reason* a guard exists.

**Accruing interest fastest (fix first):**
1. AUD-003 — every new endpoint added without a tenant filter increases the chance of an omission.
2. AUD-006 — every new tenant makes an existing dashboard slower.
3. AUD-012/AUD-025 — every OTP tweak applied to one flow leaves the other behind.
4. AUD-020 — every new syncable field widens the two largest files.

**Cosmetic / no functional risk (can wait):** AUD-008, AUD-009 naming, AUD-019.

## Do-not-do list

These look like debt but are not worth paying, and "fixing" them would expand scope:

- Converting to microservices or splitting the server into Maven modules — the failure modes observed are enforcement and file size, not deployment topology.
- Rewriting the sync engine — it works and is idempotent; register handlers only when a new entity is added (AUD-020).
- Redesigning either UI — no finding in `11` requires a visual change.
- Removing indexes "for write performance" — no measurement supports it (`10`).

# Phase 1 Preservation Baseline — result set

Spec: `.kiro/specs/v2-feature-integration` — Phase 1, task 4.5
Requirements: 29.1, 29.2 (at least one passing test per preservation requirement 4–12)

| Field | Value |
|---|---|
| Branch | `v3` at `3b47c39c` + tooling/CI commits (HEAD during recording: `fa35a9b6`) |
| Recorded on | 2026-08-05 |
| Server suite | `mvn test` — **192 tests, 0 failures, 0 errors, 11 skipped** (Testcontainers/Postgres classes, no local Docker; run in CI) |
| Android suite | `gradlew.bat testDebugUnitTest` — **PASS** (includes the new 4.3/4.4 unit tests) |
| Android instrumented | `:app:compileDebugAndroidTestKotlin` — **PASS** (4.1 migration test compiles; runs on device/CI) |
| Web admin | `npm run build` (prod) — **PASS** (task 3.1 gate) |
| Environment | Local Windows; JDK 25 (server targets 17); no Docker → 11 Postgres/Testcontainers tests skipped locally |

## Requirement-to-test mapping (Requirement 29.2)

| Req | Preservation requirement | Passing test(s) |
|---|---|---|
| 4 | Multi-terminal system (cap, pessimistic lock, bill visibility, approval, reclaim) | `TerminalControllerTest` (registration + cap + 429 cooldown), `TerminalManagementConcurrencyTest` (pessimistic serialization), `TerminalLifecycleTest` (PENDING→APPROVED, reclaim reuses slot), `TerminalIsolationIntegrationTest` (per-terminal bill visibility), `MasterSyncTerminalIsolationTest`; retention per 4.10 of `TerminalManagementPostgresConcurrencyTest` + `TerminalIsolationIntegrationTest` (both present) |
| 5 | Role & authorization layer (SHOP_ADMIN, @RequireRole, RequireRoleAspect) | `RequireRoleAspectContextTest` (new, task 4.3 — satisfies 5.7), `SpringRoleTest` (403 on wrong role), `RoleBasedAccessControlProperties` (jqwik RBAC) |
| 6 | Security hardening set (audit, rate limit, RBAC 403, sanitized errors) | `TerminalTokenSecurityTest` (SecurityAuditEvent written, terminal-token auth), `TerminalControllerTest.activate_rejectionCooldown_returns429` (rate limit 429), `JwtRequestFilterTest`, `RoleBasedAccessControlProperties`, Android `UserMessageSanitizerTest` (sanitized messages) |
| 7 | Per-tenant isolated Android DBs | `GenericSyncCrossTenantTest` (retained per 7.7), `MultiDeviceInvoiceSyncIntegrationTest` (retained), `DatabaseMigrationListConsistencyTest` (new, task 4.4 — DatabaseProvider/DatabaseModule wiring) |
| 8 | KOT & printing system | `BillRepositoryTest` (KotEvent NEW/ADD/VOID), `KitchenPrintQueueManagerTest` (enqueue on failure, reprint), `PrintRouterTest` (receipt/kitchen routing), `KitchenTicketFormatterTest`, `WifiPrinterTransportTest`, `PrintFeedbackTest` |
| 9 | Sync engine hardening (strict modes, cross-tenant, schema conformance) | `LegacySyncReplayTest` (new, task 4.2 — captured v1 request set replay + schema conformance), `MasterSyncStrictModeTest`, `SyncPushStrictModeTest`, `BillPullStrictModeTest`, `GenericSyncCrossTenantTest`, `MasterSyncProcessorConflictIsolationTest` (Android); 9.9 retention of `BillLifecycleSyncPostgresIntegrationTest` + `MultiDeviceInvoiceSyncIntegrationTest` (both present) |
| 10 | v1 billing behaviour (payment set validation, tax split, active orders, pricing) | `PaymentSetValidatorTest` (reject non-reconciling sets), `BillCalculatorTest` (CGST+SGST = total tax), `BillRepositoryActiveOrdersTest` (ActiveOrders retained), `MenuLogicTest` (MenuPricingRules), `BillingLogicTest`, `InvoiceFormatterTest` |
| 11 | v1 web admin capabilities | `WebAdminControllerTest` (staff/menu/business endpoints), `npm run build` strict-mode TS compile (task 3.1 gate), `login-page.property.spec.ts`, `orders-page.property.spec.ts` |
| 12 | CI, ops, test assets | Presence-verified: `.github/workflows/ci.yml`, `gated-tests.yml`, `web-admin.yml`, `Android/.github/workflows/android-tests.yml`, `deploy-web.sh`, `ops/apache-kbook-security.conf.example`, `ops/sql/public_token_reconciliation.sql`, `git-commit-id` + `build-info` in `pom.xml`, jqwik + surefire config, `PostgresMigrationSmokeTest` (retained); new `guards.yml` (tasks 4.6/4.7) runs on push/PR to `main|v1|v3` |

## New tests added in Phase 1

| Task | Test | Result |
|---|---|---|
| 4.1 | `AppDatabaseV62NoOpMigrationTest` (androidTest) | Compiles; device-gated |
| 4.2 | `LegacySyncReplayTest` + `legacy-sync/v1-push-fixture.json` | 1/1 pass |
| 4.3 | `RequireRoleAspectContextTest` | 4/4 pass |
| 4.4 | `DatabaseMigrationListConsistencyTest` | 1/1 pass |
| 4.6/4.7 | `guards.yml` CI jobs | grep logic verified locally, pass on v3 |

## Explicitly not claimed

- That the 11 skipped Postgres/Testcontainers tests passed locally; they are retained and pass in CI with Docker (they passed in the task 3.1 baseline run on a Docker-enabled environment previously).
- That every criterion inside requirements 4–12 has a dedicated test; Requirement 29.2 requires at least one test per numbered requirement, which this table satisfies.
- That this baseline covers Phase 2+ features (flags, webhook inbox, marketplace, Easebuzz); those phases record their own result sets under 29.1.

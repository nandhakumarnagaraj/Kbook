# Requirements Document

## Introduction

KhanaBook has two divergent branches. `main` (v1) is LIVE in production on `kbook.iadv.cloud`, shipping Android 1.0.11 (versionCode 20), Room DB version 62, Flyway head V45, serving `/api/v1`. `v2` is a 241-commit development branch, last touched 2026-06-20, never deployed, at Room DB 48, Flyway head V39, serving `/api/v2`. The two branches share merge base `9d78f64acdaad251f0abc1bd8c0f5f2f48d52fce`; the full diff is 1032 files, +83,445 / -114,791.

This feature ports the v2 feature set into `main` as a **one-way harvest**. `main` is the sole base of truth. `v2` is a read-only source of feature code that gets re-implemented or transplanted onto `main`'s architecture. A wholesale merge is explicitly out of scope, because `v2` predates and therefore lacks `main`'s entire safety layer (multi-terminal enforcement, SHOP_ADMIN role, `@RequireRole` AOP, KB-001..KB-009 security fixes, per-tenant isolated Android databases, KOT delta printing, sync quarantine/hardening, order payment flow modes, CI workflows, and 23 server test classes). Merging `v2` into `main` would regress production on all of those.

Three structural blockers govern sequencing:

1. **Flyway version collision.** Both branches independently used V22-V26, V28, V29 for entirely different schemas. Production has already applied `main`'s versions. The resolution is not to renumber and copy `v2`'s scripts: those scripts assume `v2`'s schema lineage, and several of them are corrected by later `v2` migrations. Instead every schema change a ported feature needs is authored fresh as an additive migration at V48 or above, written against the state `main`'s V45 produces.
2. **Room schema gap.** `main` is at DB version 62 with schemas 59-62 exported; `v2` is at 48 with schemas 40-43. Every v2 Android entity must arrive as a new migration on top of `main`'s chain starting at 63. Room migration is forward-only: there is no supported downgrade path, so a defective Android release is remediated by a fixed forward build, never by reverting the database version.
3. **v2 lacks main's safety layer.** Every capability in that layer needs an explicit, testable preservation guarantee.

A fourth constraint governs how failure is handled. Once a Phase is deployed and production accepts writes, restoring a pre-deployment database dump would destroy those writes. Rollback is therefore a forward-fix discipline: migrations are additive so the prior server image remains compatible with the migrated schema, every ported feature sits behind a runtime flag that can be turned off without redeploying, and the pre-deployment dump exists for disaster recovery rather than as the routine reversal path.

The deliverable is a phased integration plan where each phase is independently shippable, reversible without data loss, and gated on proof that v1 behaviour is unchanged.

One precondition applies before Phase 0 can complete. `main`'s working tree carries uncommitted changes, including edits to protected files (`TenantDaos.kt`, `BillDao.kt`, `BillingViewModel.kt`, `NewBillScreen.kt`, `SettingsScreen.kt`, `RestaurantDao.kt`, `RestaurantRepository.kt`), a launcher-icon format migration from webp to png, build-configuration drift (`build.gradle.kts`, `libs.versions.toml`, `gradle-wrapper.properties`), a modified tracked `google-services.json`, and several untracked directories.

These changes have no verified release provenance. A dirty working tree establishes only that the changes are absent from committed `main`; it does not establish what the artifact currently distributed to merchants contains, because an artifact could have been built locally from an uncommitted tree and uploaded. Phase 0 therefore has to establish provenance positively rather than infer it, and it has to do so per module, because the Android app, the server, and Web_Admin release on independent cadences and are expected to sit on different commits. Until each module's Deployed_Module_Representation is established and reconciled against the baseline candidate, the identity of the production baseline is unknown.

## Glossary

- **Integration_Codebase**: The working branch cut from `main` onto which v2 features are ported. All acceptance criteria that name this system apply to the state of that branch at the end of each phase.
- **Base_Branch**: `main`. The sole base of truth for this integration.
- **Source_Branch**: `v2`. A read-only donor tree; no commit from it is merged wholesale.
- **Merge_Base**: Commit `9d78f64acdaad251f0abc1bd8c0f5f2f48d52fce`, the last shared ancestor.
- **KhanaBook_Server**: The Spring Boot 3.5.x server module under `server/`.
- **KhanaBook_Android_App**: The Kotlin/Compose Android application under `Android/`.
- **Web_Admin**: The Angular 18 administration frontend under `web-admin/`.
- **Flyway_Migration_Set**: The ordered set of SQL migration scripts under `server/src/main/resources/db/migration`.
- **Flyway_History**: The `flyway_schema_history` table in the production PostgreSQL database.
- **Room_Migration_Chain**: The ordered set of Room migrations plus exported schema JSON files in the Android module.
- **Terminal_Management_Service**: `main`'s server-side multi-terminal subsystem: `RestaurantTerminal`, `DeviceRegistrationRequest`, `TerminalManagementService`, `TerminalRequestFilter`, `BillTerminalUtil`, `TerminalController`, `TerminalManagementController`.
- **Terminal_Cap**: The server-enforced limit of 5 concurrently active terminals per restaurant, guarded by a pessimistic database lock.
- **Terminal_Token**: The `X-Terminal-Token` request header used to authorize a registered device.
- **Role_Authorization_Layer**: `main`'s `@RequireRole` annotation, `RequireRoleAspect`, and the `spring-boot-starter-aop` dependency that makes the aspect active.
- **SHOP_ADMIN**: The `User.Role` enum value present on `main` (14 files) and absent from `v2`, used for terminal approval and shop-level administration.
- **Security_Hardening_Set**: `main`'s security fixes KB-001 through KB-009, plus `SecurityAuditEvent`, `SecurityAuditService`, `RateLimitAttempt`, `DbRateLimiter`, `RateLimiterConfig`.
- **Tenant_Database_Layer**: `main`'s per-restaurant isolated encrypted Room databases, implemented by `DatabaseProvider.kt` and `TenantDaos.kt` and wired through `DatabaseModule`, `MasterSyncProcessor`, `MainActivity`, `LogoutViewModel`, `UserRepository`, and `MenuViewModel`.
- **KOT_System**: `main`'s kitchen order ticket subsystem: `KotEventEntity`, its DAO, delta KOT printing via `sentToKot`, `KitchenPrintQueueEntity`, the KDS pending queue and reprint screen, and `PrintService.kt`.
- **Sync_Engine**: The offline-first bidirectional sync subsystem spanning `KhanaBook_Android_App` and `KhanaBook_Server`.
- **Sync_Hardening_Set**: `main`'s sync robustness features: `SyncQuarantineEntity`, end-to-end `sourceChannel` propagation, strict-mode sync paths, the 409 invoice-series loop breaker, the stale-ack fix, restaurant-scoped payment operation uniqueness, and `PaymentSetValidator`.
- **Order_Payment_Flow_Mode**: `main`'s `PAY_BEFORE_FOOD` / `PAY_AFTER_FOOD` billing flow configuration, defaulting to `PAY_BEFORE_FOOD`.
- **V1_Android_Design_System**: `main`'s dark-only Compose theme: `KhanaBookTheme.spacing` / `iconSize` / `layout`, `KhanaShapes`, the `PrimaryGold` / `DarkBrown1` / `DarkBrown2` / `RichEspresso` palette, Poppins typography, and the `ui/designsystem/` component set.
- **V1_Web_Admin_Design_System**: `main`'s plain-CSS styling (`styles.css`, `wave-c.css`, `wave-de.css`) with CSS custom properties and the `.page-shell` / `.panel` / `.data-table` layout conventions.
- **V2_Design_System**: `v2`'s rejected styling stack: `KbTokens`, `KbMotion`, `AdaptiveUtils`, the light/dark theme toggle, the saffron palette, and the `styles.scss` SCSS pipeline.
- **Notification_Service**: The ported FCM push notification subsystem (server `PushNotificationService`, `FirebaseConfig`, `NotificationController`, `AdminNotificationController`, `DeviceToken`, `NotificationEvent`).
- **Easebuzz_Payment_Service**: The ported Easebuzz gateway subsystem (API clients, `EasebuzzPaymentService`, `EasebuzzWebhookService`, `SubMerchantService`, `PostSplitService`, `RefundService`, and the `EasebuzzSubMerchant` / `EasebuzzPayout` / `EasebuzzWebhookEvent` / `EasebuzzSubMerchantWebhookEvent` entities).
- **Sub_Merchant**: An Easebuzz sub-merchant record representing one restaurant's onboarded payment identity.
- **Post_Split**: The post-transaction commission split operation that apportions a settled payment between platform and Sub_Merchant.
- **WIRE_Platform_Client** (DEFERRED, Req 21): The ported WIRE platform integration (lookup by email or sub-merchant key, KYC profile URL, insta-collect and payout webhooks).
- **Fssai_Compliance_Service**: The ported FSSAI/GST compliance subsystem (`FssaiTracker`, `FssaiRenewal`, `FssaiTrackerService`, `ComplianceAlertService`, `GstFssaiLookupService`).
- **Marketplace_Order_Service**: The ported Swiggy/Zomato order management subsystem (`MarketplaceOrder`, `MarketplaceOrderItem`, `MarketplaceOrderService`, `MarketplaceOrderController`, `MarketplaceWebhookController`).
- **Fintech_Admin_Tranche** (2 pages in scope, 17 DEFERRED, Req 25): The 19 new v2 Web_Admin pages and 22 v2 webadmin controllers covering chargebacks, customer CDP, webhook health, instant settlements, settlement reports, payment routing, refund automation, tax compliance, financing, unified commerce, developer portal, onboarding tracker, commission config/report, transaction monitor, payment dashboard, sub-merchants, and restaurant settings.
- **Refresh_Token_Flow** (DEFERRED, Req 23): The ported refresh-token rotation mechanism backed by the `RefreshToken` entity, replacing reliance on plain 30-day JWTs alone.
- **Legacy_Client**: Any deployed `KhanaBook_Android_App` build at versionCode 20 or lower that has not been updated.
- **Excluded_Debris_Set**: v2 artifacts that must be kept out of Integration_Codebase: `DbCheck.java`, `QuickDbCheck.java`, the `/auth/signup/dev-debug` endpoint, the `/{id}/dev-refresh` endpoint, production-enabled Swagger, the removal of `spring-boot-starter-aop`, the removal of the jqwik dependency and surefire include configuration, the removal of the `git-commit-id` plugin, and the removal of the `build-info` execution.
- **Phase**: One independently shippable unit of the integration, with its own migration set, test gate, deploy step, and rollback procedure.
- **Feature_Flag**: A runtime switch whose state is persisted in PostgreSQL, resolved per restaurant with a dominant global kill switch, and applied without a container rebuild, restart, or redeployment. Governs one ported feature end to end across KhanaBook_Server, Web_Admin, and KhanaBook_Android_App entry points.
- **Webhook_Endpoint**: An inbound HTTP endpoint that a payment or marketplace provider calls. Exempt from the disabled-state HTTP 503 behaviour in Requirement 30.16 and governed instead by Requirement 33.
- **Webhook_Inbox**: The durable store of signature-verified inbound provider payloads, unique on the pair of provider identity and provider event identifier, from which business processing is driven and replayed.
- **Inbox_Ordering_Key**: The per-provider sort key used to drain the Webhook_Inbox: the provider-supplied sequence value where one exists, the provider event timestamp otherwise, with receipt timestamp as final tie-break. Never the provider event identifier, which is not guaranteed monotonic or sortable.
- **Inbox_Aggregate**: The scope within which Webhook_Inbox ordering is guaranteed, derived from the domain identifier of the entity a record mutates: external order for marketplace events, gateway transaction for payment and refund events, payout or sub-merchant identifier for payout events, sub-merchant identifier for KYC and onboarding events, restaurant plus licence identifier for compliance events. Never the restaurant alone. Ordering is not guaranteed between distinct aggregates, and they may drain concurrently.
- **Inbox_Worker**: The execution context that applies business state changes from persisted Webhook_Inbox records. The only path by which a webhook may mutate business state; the receiving HTTP request never does.
- **Flag_Admin_Surface**: The `KBOOK_ADMIN`-restricted API and Web_Admin page through which Feature_Flag state is read, mutated, and audited, satisfying Requirements 30.20 through 30.22.
- **Baseline_Candidate**: The mutable working commit on Base_Branch that provenance reconciliation lands on. It advances as production content is ported onto it, and it is the comparison target for every Deployed_Module_Representation. It becomes Baseline_Tag once reconciliation is complete and the baseline build and tests pass.
- **Baseline_Tag**: The immutable annotated git tag cut at the finalised Baseline_Candidate commit, whose module paths contain the behaviour production is running for all three modules. Every Phase branch descends from it and the Preservation_Test_Suite results are measured against it. Content equivalence with each Deployed_Module_Representation is required; ancestry is not. Once cut it is never moved or re-cut.
- **Deployed_Module_Representation**: What one module is actually running in production, expressed either as a resolved commit SHA or, where no commit can be resolved, as an artifact-derived snapshot of that module's paths reconstructed from the deployed artifact. Both kinds are comparable against Baseline_Candidate.
- **Android_Baseline_Commit**: The commit from which the Android artifact currently distributed to merchants was built, established from the Play Console artifact, its `versionCode`, and the release signing and build records. Not required to be reachable from Base_Branch.
- **Server_Baseline_Commit**: The commit currently running in the production server container, established from the `git.commit.id` reported by authenticated `/api/v1/actuator/info`. Not required to be reachable from Base_Branch.
- **WebAdmin_Baseline_Commit**: The commit from which the bundle currently served from the production docroot was built, established from its build manifest and the `deploy-web.sh` run that placed it. Not required to be reachable from Base_Branch.
- **Preservation_Test_Suite**: The union of `main`'s 23 server test classes (including the Testcontainers PostgreSQL suites and the 6 jqwik property suites) and `main`'s Android unit tests, used as the regression gate for every Phase.
- **Database_Backup_Gate**: A verified PostgreSQL dump taken by `ops/backup_postgres.sh` immediately before any deployment that applies a Flyway migration.
- **Deployment_Process**: The VPS Docker Compose deploy path (`deploy-production.sh` for server, `deploy-web.sh` for Web_Admin).
- **CI_Pipeline**: The GitHub Actions workflows `ci.yml`, `gated-tests.yml`, and `web-admin.yml`.

## Requirements

### Requirement 1: One-Way Integration Direction

**User Story:** As the KhanaBook maintainer, I want v2's features harvested onto main rather than main merged into v2, so that no production capability is silently lost.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL be created as a branch whose first commit is an ancestor-preserving descendant of Base_Branch head.
2. THE Integration_Codebase SHALL retain every file present on Base_Branch head, except files whose removal is listed as an explicit in-scope change in the design document.
3. WHEN a v2 feature is ported, THE Integration_Codebase SHALL express that feature using Base_Branch's architecture, entity model, and dependency set.
4. THE Integration_Codebase SHALL contain zero merge commits that take Source_Branch as a parent.
5. WHEN Base_Branch receives a production hotfix during the integration, THE Integration_Codebase SHALL rebase or forward-merge Base_Branch before the next Phase deployment.
6. THE Integration_Codebase SHALL record, for each v2 payload item specified in Requirements 17 through 26, a scope decision of `IN_PHASE_N` or `DEFERRED`.

### Requirement 2: Forward-Only Additive Schema Authoring

**User Story:** As the operator of a live PostgreSQL database, I want the schema changes each ported feature needs authored fresh as additive migrations above main's head, so that Flyway never re-applies a version production already recorded and no ported migration carries v2's schema assumptions or its subsequently-fixed bugs.

#### Acceptance Criteria

1. THE Flyway_Migration_Set SHALL contain exactly one script per version number.
2. THE Flyway_Migration_Set SHALL preserve, byte-for-byte, the content and version number of every migration V1 through V45 that Base_Branch defines.
3. THE Integration_Codebase SHALL author every new migration as a new script numbered V48 or higher, written against the schema state that Base_Branch V45 produces.
4. THE Integration_Codebase SHALL NOT copy or renumber any Source_Branch migration script.
5. WHEN a ported feature requires schema objects that Source_Branch created across several migrations including a later corrective migration, THE Integration_Codebase SHALL author a single migration expressing the corrected end state and SHALL NOT reproduce the intermediate defective state.
6. THE Integration_Codebase SHALL record, for each new migration, the Source_Branch migrations whose intent it subsumes.
7. THE Integration_Codebase SHALL restrict every new migration to additive operations: creating tables, adding nullable columns, adding columns with defaults, creating indexes, and creating constraints that hold over existing rows.
8. THE Integration_Codebase SHALL NOT include a migration that drops a column, drops a table, renames a column, renames a table, or narrows a column type.
9. WHERE a ported feature requires a column on an existing Base_Branch table, THE Integration_Codebase SHALL add that column as nullable or with a default so that the pre-deployment server image continues to write valid rows.
10. WHEN the Flyway_Migration_Set is applied to an empty PostgreSQL database, THE KhanaBook_Server SHALL complete startup and report `{"status":"UP"}` on `/api/v1/actuator/health`.
11. WHEN the Flyway_Migration_Set is applied to a restored copy of the production database, THE KhanaBook_Server SHALL complete startup and report `{"status":"UP"}` on `/api/v1/actuator/health`.
12. IF Flyway reports a checksum mismatch or an out-of-order version against the restored production database, THEN THE Deployment_Process SHALL halt before the server container is started.
13. THE Integration_Codebase SHALL include a reconciliation script that reports every row in Flyway_History whose recorded checksum differs from the corresponding script in Flyway_Migration_Set.
14. THE Preservation_Test_Suite SHALL include a migration smoke test that applies the full Flyway_Migration_Set against a Testcontainers PostgreSQL instance.
15. THE Preservation_Test_Suite SHALL include a test asserting that the Base_Branch server image starts successfully against a database migrated to the Integration_Codebase head version.

### Requirement 3: Room Migration Chain Continuity

**User Story:** As a user of the live Android app, I want my local encrypted database upgraded in place, so that unsynced bills survive the app update.

#### Acceptance Criteria

1. THE Room_Migration_Chain SHALL treat database version 62 as its starting point.
2. WHEN a v2 Android entity is ported, THE Room_Migration_Chain SHALL introduce that entity through a migration whose target version is 63 or higher.
3. THE Room_Migration_Chain SHALL define a contiguous migration for every version transition from 62 up to the new head version.
4. THE Integration_Codebase SHALL export a Room schema JSON file for every database version from 62 up to the new head version.
5. THE KhanaBook_Android_App SHALL omit `fallbackToDestructiveMigration` from its Room database builder configuration.
6. WHEN the Room_Migration_Chain is applied to a database at version 62 that contains unsynced bills, payments, and bill items, THE KhanaBook_Android_App SHALL retain every one of those rows after migration.
7. WHEN the Room_Migration_Chain is applied to a database at version 62, THE KhanaBook_Android_App SHALL produce a schema identical to the schema produced by creating the database fresh at the new head version.
8. THE Integration_Codebase SHALL exclude v2's exported schema JSON files for versions 40 through 43.
9. THE Integration_Codebase SHALL NOT author a Room migration that reduces the database version.
10. WHEN a merchant updates across more than one Phase at once, THE Room_Migration_Chain SHALL migrate that database from its stored version to the head version in a single upgrade pass.
11. THE Preservation_Test_Suite SHALL include a migration test for each version transition introduced by a Phase, seeded with unsynced bills, bill items, and payments.

### Requirement 4: Preservation of the Multi-Terminal System

**User Story:** As a restaurant owner running 5 billing devices, I want terminal enforcement and isolation to behave exactly as it does today, so that invoice numbers stay unique and staff only see their own terminal's bills.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain every class in Terminal_Management_Service.
2. THE Integration_Codebase SHALL retain `TerminalDailyCounterEntity` and `TerminalIdentity.kt`.
3. WHEN a sixth terminal registration is attempted for a restaurant that already has 5 active terminals, THE KhanaBook_Server SHALL reject the registration and leave the active terminal count at 5.
4. WHEN concurrent terminal registration requests arrive for the same restaurant, THE KhanaBook_Server SHALL serialize them under a pessimistic lock and admit at most Terminal_Cap terminals.
5. WHEN a bill list is requested with a Terminal_Token, THE KhanaBook_Server SHALL return only bills owned by or visible to that terminal.
6. WHEN two terminals of the same restaurant each create a bill on the same calendar day, THE KhanaBook_Server SHALL assign each bill an invoice number that is unique within the restaurant.
7. WHEN a request that requires terminal authorization arrives without a valid Terminal_Token, THE KhanaBook_Server SHALL reject the request.
8. WHEN a device registration request is approved by a SHOP_ADMIN, THE KhanaBook_Server SHALL transition the request state from `PENDING` to `APPROVED`.
9. WHEN a terminal is reclaimed after an app reinstall, THE KhanaBook_Server SHALL reuse the existing terminal record rather than consuming an additional slot against Terminal_Cap.
10. THE Preservation_Test_Suite SHALL retain `TerminalManagementPostgresConcurrencyTest` and `TerminalIsolationIntegrationTest` and both SHALL pass at the end of every Phase.

### Requirement 5: Preservation of the Role and Authorization Layer

**User Story:** As the platform owner, I want SHOP_ADMIN and the role-checking aspect intact, so that authorization boundaries survive the integration.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `SHOP_ADMIN` as a value of the `User.Role` enum.
2. THE Integration_Codebase SHALL retain the `spring-boot-starter-aop` dependency in the server `pom.xml`.
3. THE Integration_Codebase SHALL retain `@RequireRole` and `RequireRoleAspect`.
4. WHEN a request reaches a handler annotated with `@RequireRole` and the caller's role is absent from the annotation's allowed set, THE KhanaBook_Server SHALL reject the request with HTTP 403.
5. WHEN a new endpoint is added by a ported v2 feature, THE Integration_Codebase SHALL annotate that endpoint with `@RequireRole` naming the roles permitted to call it.
6. THE Integration_Codebase SHALL retain role-based routing in Web_Admin for `KBOOK_ADMIN`, `OWNER`, and `SHOP_ADMIN`.
7. THE Preservation_Test_Suite SHALL include a test asserting that `RequireRoleAspect` is active in the Spring application context.

### Requirement 6: Preservation of the Security Hardening Set

**User Story:** As the platform owner, I want every KB-001..KB-009 fix to remain effective, so that the integration does not reopen an audited vulnerability.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `SecurityAuditEvent`, `SecurityAuditService`, `RateLimitAttempt`, `DbRateLimiter`, and `RateLimiterConfig`.
2. WHEN a user sync push payload contains a role value higher than the pushing caller's role, THE KhanaBook_Server SHALL reject the role change and persist the caller's existing role (KB-001).
3. WHEN a payment deep link is received whose signature or target restaurant does not match the active session, THE KhanaBook_Android_App SHALL discard the deep link (KB-002).
4. WHEN a legacy bill without an invoice number is displayed, THE KhanaBook_Android_App SHALL render a placeholder other than the literal string `INVnull` (KB-005).
5. WHEN a manual refund action is performed, THE KhanaBook_Server SHALL write a SecurityAuditEvent recording the actor, the bill, and the amount (KB-006).
6. WHEN a UPI draft bill is abandoned and a new session begins, THE KhanaBook_Android_App SHALL retain at most the drafts belonging to the current session (KB-007).
7. THE KhanaBook_Server SHALL disable Swagger and OpenAPI endpoints under the `prod` profile (KB-008).
8. WHEN a draft bill originating on the current terminal is synced, THE KhanaBook_Server SHALL preserve the incoming `recordOrigin` value (KB-009).
9. WHEN the configured rate limit for login, OTP, or general API traffic is exceeded, THE KhanaBook_Server SHALL reject subsequent requests within the window with HTTP 429.
10. WHEN an unhandled exception occurs, THE KhanaBook_Server SHALL return a sanitized message plus an error identifier and SHALL write the stack trace to the server log only.
11. THE Integration_Codebase SHALL retain SSL certificate pinning, device binding, the admin IP allowlist, and the PIN/biometric app lock with its 30-second grace period.

### Requirement 7: Preservation of Per-Tenant Isolated Android Databases

**User Story:** As a staff member sharing a device across two restaurants, I want each restaurant's data kept in its own encrypted database, so that data never leaks between tenants.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `DatabaseProvider.kt` and `TenantDaos.kt` and their wiring in `DatabaseModule`, `MasterSyncProcessor`, `MainActivity`, `LogoutViewModel`, `UserRepository`, and `MenuViewModel`.
2. WHEN a user logs in to a restaurant different from the previously active restaurant, THE KhanaBook_Android_App SHALL switch to that restaurant's database file atomically.
3. WHILE a tenant database is open, THE KhanaBook_Android_App SHALL encrypt that database with SQLCipher.
4. WHEN a query is executed through a tenant DAO, THE KhanaBook_Android_App SHALL return only rows belonging to the active restaurant.
5. WHEN a v2 Android entity is ported, THE Integration_Codebase SHALL register that entity in the tenant database rather than in a shared database.
6. WHEN a soft logout occurs, THE KhanaBook_Android_App SHALL retain unsynced rows in the tenant database.
7. THE Preservation_Test_Suite SHALL retain `GenericSyncCrossTenantTest` and it SHALL pass at the end of every Phase.

### Requirement 8: Preservation of the KOT and Printing System

**User Story:** As a kitchen operator, I want delta KOT printing and the KDS queue to keep working, so that only newly added items reach the kitchen printer.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `KotEventEntity`, its DAO, `KitchenPrintQueueEntity`, the KDS pending queue screen, the KDS reprint screen, and `PrintService.kt`.
2. WHEN items are added to an order that already has a printed KOT, THE KhanaBook_Android_App SHALL print only the items whose `sentToKot` flag is unset.
3. WHEN a KOT is printed successfully, THE KhanaBook_Android_App SHALL set `sentToKot` on every printed item.
4. WHEN a KOT print fails, THE KhanaBook_Android_App SHALL enqueue the KOT in the kitchen print queue and leave `sentToKot` unset for the affected items.
5. WHEN a print job is dispatched, THE KhanaBook_Android_App SHALL execute the job on `PrintService` without blocking the foreground UI.
6. THE Integration_Codebase SHALL retain multi-printer routing for receipt and kitchen destinations, 80mm thermal alignment, PDF invoice generation, and offline logo caching for print.

### Requirement 9: Preservation of Sync Engine Hardening

**User Story:** As a merchant with intermittent connectivity, I want sync to stay as robust as it is today, so that bills are neither lost nor duplicated.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain every class in Sync_Hardening_Set.
2. WHEN a sync payload is rejected as unprocessable, THE Sync_Engine SHALL move the offending record to `SyncQuarantineEntity` and SHALL continue processing the remaining records in the batch.
3. WHEN the server returns HTTP 409 for an invoice-series unique violation, THE Sync_Engine SHALL stop retrying that record after the configured attempt limit.
4. WHEN a bill is created on a device, THE Sync_Engine SHALL propagate `sourceChannel` unchanged from the Android record through the server to any subsequent pull of that bill.
5. WHEN an acknowledgement arrives for a record version older than the locally stored version, THE Sync_Engine SHALL retain the local version.
6. WHEN two restaurants submit payment operations with the same operation identifier, THE KhanaBook_Server SHALL accept both and scope uniqueness to the restaurant.
7. WHEN a payment set is submitted whose payment amounts do not reconcile against the bill total, THE `PaymentSetValidator` SHALL reject the payment set.
8. WHEN a push completes, THE Sync_Engine SHALL apply the returned `localToServerIdMap` to the local records.
9. THE Preservation_Test_Suite SHALL retain `BillLifecycleSyncPostgresIntegrationTest` and `MultiDeviceInvoiceSyncIntegrationTest` and both SHALL pass at the end of every Phase.

### Requirement 10: Preservation of v1 Billing Behaviour

**User Story:** As a restaurant using pay-before and pay-after workflows, I want my existing billing screens and rules unchanged, so that daily operations are unaffected by the integration.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `OrderPaymentFlowMode` with both `PAY_BEFORE_FOOD` and `PAY_AFTER_FOOD` values and all 22 files that reference it.
2. THE Integration_Codebase SHALL retain the ActiveOrders screens and their ViewModels, `MenuPricingRules`, `PaymentLimits`, `PricingConstants`, `RoleAccessScreen`, and `CustomDateRangePickerDialog`.
3. WHEN a bill is finalized, THE KhanaBook_Android_App SHALL compute CGST and SGST components whose sum equals the total tax shown on the receipt.
4. WHEN an order is cancelled, THE KhanaBook_Android_App SHALL require a non-empty cancellation reason.
5. THE Integration_Codebase SHALL retain Cash, manual UPI, and POS terminal payment modes as selectable options independent of any ported gateway.
6. THE Integration_Codebase SHALL retain daily, weekly, and monthly order-level reports with PDF and Excel export, and WhatsApp and SMS invoice sharing.

### Requirement 11: Preservation of v1 Web Admin Capabilities

**User Story:** As a platform administrator, I want today's web-admin pages to keep working, so that terminal approval and menu OCR remain available.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain the Web_Admin terminals page, reports page, OCR menu upload page, staff create/read/update/delete flows, business suspend and activate actions, the order detail view, and the OTP-based password reset service.
2. WHEN a Web_Admin build is produced with `npm run build`, THE Web_Admin SHALL compile with zero TypeScript errors under strict mode.
3. THE Web_Admin SHALL resolve its API base to `https://kbook.iadv.cloud/api/v1` in the production configuration.
4. WHEN a user with a role that lacks access opens a restricted route, THE Web_Admin SHALL render the limited-access page.

### Requirement 12: Preservation of CI, Ops, and Test Assets

**User Story:** As the maintainer, I want the CI pipeline and test suites intact, so that regressions are caught before deployment.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain `.github/workflows/ci.yml`, `.github/workflows/gated-tests.yml`, `.github/workflows/web-admin.yml`, and `Android/.github/workflows/android-tests.yml`.
2. THE Integration_Codebase SHALL retain `deploy-web.sh`, `ops/apache-kbook-security.conf.example`, and `ops/sql/public_token_reconciliation.sql`.
3. THE Integration_Codebase SHALL retain the `git-commit-id` plugin and the `build-info` execution in the server `pom.xml`.
4. WHEN `/api/v1/actuator/info` is requested, THE KhanaBook_Server SHALL return the git commit identifier of the deployed build.
5. THE Integration_Codebase SHALL retain the jqwik dependency and the surefire include configuration that runs the 6 property test suites.
6. THE Integration_Codebase SHALL retain all 23 server test classes present on Base_Branch, including `PostgresMigrationSmokeTest`.
7. WHEN a Phase branch is pushed, THE CI_Pipeline SHALL run the Preservation_Test_Suite and SHALL report failure if any test fails.
8. THE Integration_Codebase SHALL retain the `Kitchen Command Center/` prototype, `docs/angular-handoff`, and `web-admin-preview/` design assets.

### Requirement 13: Single API Version Namespace

**User Story:** As the operator of the Apache reverse proxy, I want every endpoint served under /api/v1, so that no proxy or client configuration change is required.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL use `/api/v1` as its servlet context path in every Spring profile.
2. WHEN a v2 controller is ported, THE Integration_Codebase SHALL expose its endpoints under the `/api/v1` context path.
3. THE Integration_Codebase SHALL exclude the `/api/v2` context path configuration from Source_Branch.
4. THE KhanaBook_Server SHALL listen on port 8081 inside the production container.
5. THE KhanaBook_Android_App SHALL resolve `WEB_ADMIN_URL` to a value that omits a `/v2/` path segment.
6. WHEN a ported endpoint path collides with an existing Base_Branch endpoint path, THE Integration_Codebase SHALL keep the Base_Branch behaviour at that path and assign the ported endpoint a distinct path.

### Requirement 14: Backward Compatibility for the Live Android Fleet

**User Story:** As a merchant who has not updated the app, I want to keep billing and syncing after the server is upgraded, so that my business is not interrupted.

#### Acceptance Criteria

1. WHEN a Legacy_Client calls any sync endpoint that exists on Base_Branch, THE KhanaBook_Server SHALL accept the request and return a response conforming to the Base_Branch response schema.
2. WHEN a ported feature adds a field to an existing response body, THE KhanaBook_Server SHALL add that field as an addition and SHALL retain every field name and type that Base_Branch returned.
3. WHEN a ported feature adds a field to an existing request body, THE KhanaBook_Server SHALL treat that field as optional and SHALL apply a documented default when it is absent.
4. WHEN a Legacy_Client omits a device token, THE Notification_Service SHALL skip push delivery for that device and SHALL complete the originating operation successfully.
5. WHEN a Legacy_Client creates a bill after Easebuzz_Payment_Service is deployed, THE KhanaBook_Server SHALL accept the bill with a non-gateway payment mode.
6. THE Integration_Codebase SHALL retain every existing enum value in every wire-format enum and SHALL introduce new gateway, notification, and marketplace states as additional values.
7. WHEN the KhanaBook_Server returns a record that a Legacy_Client originated, THE KhanaBook_Server SHALL populate every wire-format enum on that record with a value that Base_Branch defines.
8. THE Preservation_Test_Suite SHALL include a compatibility test that replays a captured Base_Branch sync request set against the Integration_Codebase server and asserts schema conformance.

### Requirement 15: UI Scope Constraint

**User Story:** As the product owner, I want v1's Android UI and web-admin styling kept exactly as shipped, so that merchants see no visual change beyond genuinely new screens.

#### Acceptance Criteria

1. THE KhanaBook_Android_App SHALL define exactly one Compose color scheme, and that scheme SHALL be a `darkColorScheme`.
2. THE Integration_Codebase SHALL exclude every artifact of V2_Design_System.
3. THE Integration_Codebase SHALL exclude a user-facing light/dark theme toggle from the KhanaBook_Android_App.
4. WHERE a ported v2 feature requires a screen that has no Base_Branch equivalent, THE KhanaBook_Android_App SHALL implement that screen using V1_Android_Design_System tokens and components.
5. THE Integration_Codebase SHALL express all new Android dimensions through `KhanaBookTheme.spacing` and `KhanaBookTheme.iconSize` and all new Android text styles through `MaterialTheme.typography`.
6. THE Integration_Codebase SHALL retain `styles.css`, `wave-c.css`, and `wave-de.css` as the Web_Admin styling source and SHALL exclude `styles.scss`.
7. WHERE a ported v2 feature requires a Web_Admin page that has no Base_Branch equivalent, THE Web_Admin SHALL implement that page using the `.page-shell` / `.panel` / `.data-table` conventions and the existing CSS custom properties.
8. THE Integration_Codebase SHALL exclude the v2 redesigns of the login, signup, forgot-password, settings, and splash screens.
9. THE Integration_Codebase SHALL retain the Base_Branch logo and branding assets.

### Requirement 16: Exclusion of v2 Debris

**User Story:** As the maintainer, I want v2's debug and de-hardening changes kept out, so that the integration does not weaken production.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL exclude every artifact in Excluded_Debris_Set.
2. WHEN the CI_Pipeline runs, THE CI_Pipeline SHALL fail if `DbCheck.java` or `QuickDbCheck.java` is present in the server source tree.
3. WHEN the CI_Pipeline runs, THE CI_Pipeline SHALL fail if a request mapping containing `dev-debug` or `dev-refresh` is present in the server source tree.
4. WHEN the server starts under the `prod` profile, THE KhanaBook_Server SHALL return HTTP 404 for `/api/v1/swagger-ui/index.html` and `/api/v1/v3/api-docs`.
5. THE Integration_Codebase SHALL retain Spring Boot version 3.5.12 or higher in the server `pom.xml`.

### Requirement 17: Push Notification Feature Port

**User Story:** As a restaurant owner, I want push notifications for orders, payments, and compliance events, so that I am informed without opening the app.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL expose device token registration, notification listing, notification read-marking, and admin custom-push endpoints under `/api/v1/notifications`.
2. WHEN a device token is registered, THE Notification_Service SHALL associate the token with the authenticated user and the active restaurant.
3. WHEN the same device token is registered twice, THE Notification_Service SHALL store exactly one active record for that token.
4. WHEN a notifiable event occurs for login, new order, payment confirmation, order cancellation, KYC status change, or payout completion, THE Notification_Service SHALL persist a `NotificationEvent` and SHALL attempt delivery to every active device token of the target restaurant.
5. IF push delivery to a device token fails with a permanent error, THEN THE Notification_Service SHALL mark that token inactive and SHALL complete the originating business operation successfully.
6. IF the Firebase credential is absent or invalid, THEN THE Notification_Service SHALL log the failure and SHALL complete the originating business operation successfully.
7. WHEN a notification is delivered to the KhanaBook_Android_App, THE KhanaBook_Android_App SHALL store it in the tenant database as a `NotificationEntity` and SHALL display it in the notifications screen.
8. WHEN a notification carrying a deep link is opened, THE KhanaBook_Android_App SHALL navigate to the screen named by that deep link.
9. WHEN the device completes boot, THE KhanaBook_Android_App SHALL re-register its FCM token and SHALL reschedule its background workers.
10. THE KhanaBook_Android_App SHALL group notification channels into orders, payments, system, and promotions.
11. THE Integration_Codebase SHALL exclude every Firebase service-account key file from version control.
12. WHERE the deployment environment prohibits service-account keys, THE Notification_Service SHALL authenticate to Firebase using the OAuth2 refresh-token credential path.
13. THE Integration_Codebase SHALL supply the server-side Firebase credential from an environment variable or a mounted file and SHALL NOT read it from a path inside the source tree.
14. IF the server-side Firebase credential is absent, THEN THE KhanaBook_Server SHALL start with the Notification_Service Feature_Flag resolved to disabled.

### Requirement 18: FSSAI and GST Compliance Port

**User Story:** As a restaurant owner, I want FSSAI expiry tracking with renewal reminders, so that my licence does not lapse.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL persist FSSAI licence number, status, expiry date, and alert history in a `fssai_tracker` record per restaurant.
2. WHEN the compliance alert schedule runs and an FSSAI licence expiry falls within a configured alert window, THE Fssai_Compliance_Service SHALL create a compliance alert and SHALL request a push notification for that restaurant.
3. WHEN a compliance alert has already been sent for a given licence and alert window, THE Fssai_Compliance_Service SHALL skip creating a duplicate alert for that window.
4. THE KhanaBook_Server SHALL expose GST lookup, FSSAI lookup, and FSSAI renewal order creation endpoints under `/api/v1`.
5. WHERE the Easebuzz_Payment_Service Feature_Flag is enabled AND a merchant selects `Pay Now` on an FSSAI renewal notification, THE KhanaBook_Android_App SHALL open the FSSAI renewal screen for the corresponding renewal record.
6. WHERE the Easebuzz_Payment_Service Feature_Flag is disabled, THE KhanaBook_Android_App SHALL omit the `Pay Now` action from FSSAI renewal notifications and SHALL present `Remind Me` only.
7. WHEN a merchant selects `Remind Me` on an FSSAI renewal notification, THE Fssai_Compliance_Service SHALL schedule the next alert for that licence at the configured deferral interval.
8. WHEN an FSSAI renewal payment settles, THE Fssai_Compliance_Service SHALL update the tracker expiry date and SHALL set the renewal record state to completed.
9. THE Web_Admin SHALL display FSSAI and GST expiry status for each restaurant.

### Requirement 19: Marketplace Order Management Port

**User Story:** As a restaurant owner on Swiggy and Zomato, I want marketplace orders visible and actionable in the app, so that I manage all channels in one place.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL retain Base_Branch's `MarketplaceConfigController` behaviour and SHALL add Marketplace_Order_Service alongside it.
2. THE KhanaBook_Server SHALL expose marketplace order list, detail, accept, reject, status update, and item-availability endpoints under `/api/v1`.
3. WHEN a Swiggy or Zomato webhook delivers an order payload, THE Marketplace_Order_Service SHALL persist a `MarketplaceOrder` with its `MarketplaceOrderItem` rows.
4. WHEN the same marketplace webhook payload is delivered more than once, THE Marketplace_Order_Service SHALL store exactly one `MarketplaceOrder` for that external order identifier.
5. IF a marketplace webhook payload fails signature verification, THEN THE Marketplace_Order_Service SHALL reject the request with HTTP 401 and SHALL persist no order.
6. WHEN a restaurant has Swiggy or Zomato API keys configured and a sync occurs, THE Marketplace_Order_Service SHALL enable marketplace ordering for that restaurant.
7. WHEN marketplace ordering is enabled for a restaurant that already has stored orders, THE Marketplace_Order_Service SHALL make those existing orders visible.
8. WHEN a menu item is marked unavailable, THE Marketplace_Order_Service SHALL propagate the unavailability to the configured marketplaces.
9. WHEN marketplace orders are listed for a restaurant, THE Marketplace_Order_Service SHALL return only orders belonging to that restaurant.
10. THE KhanaBook_Android_App SHALL present marketplace orders on a screen built with V1_Android_Design_System tokens.

### Requirement 20: Easebuzz Payment Gateway Port

**User Story:** As a restaurant owner, I want to collect digital payments through Easebuzz with automatic commission splits, so that settlements and platform fees are handled without manual work.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL expose the Easebuzz payment endpoints under `/api/v1/payments/easebuzz` and the sub-merchant administration endpoints under `/api/v1/admin/sub-merchants`.
2. WHEN a Sub_Merchant onboarding request is submitted with KYC documents, THE Easebuzz_Payment_Service SHALL persist an `EasebuzzSubMerchant` record in a pending state and SHALL submit the onboarding payload to Easebuzz.
3. WHEN a Sub_Merchant onboarding reaches a `FAILED` state, THE Easebuzz_Payment_Service SHALL permit resubmission of that Sub_Merchant.
4. WHILE a restaurant's Sub_Merchant state is other than `ACTIVE`, THE Easebuzz_Payment_Service SHALL route that restaurant's collections to the parent merchant account.
5. WHEN a gateway webhook is received, THE Easebuzz_Payment_Service SHALL verify the HMAC signature before applying any state change.
6. IF a gateway webhook fails HMAC verification, THEN THE Easebuzz_Payment_Service SHALL reject the request with HTTP 401 and SHALL leave the payment state unchanged.
7. WHEN the same gateway webhook event identifier is received more than once, THE Easebuzz_Payment_Service SHALL persist exactly one `EasebuzzWebhookEvent` and SHALL apply the resulting state change exactly once.
8. WHEN a payment settles, THE Post_Split SHALL apportion the settled amount such that the platform share plus the Sub_Merchant share equals the settled amount.
9. WHEN a Post_Split is requested for a payment that already has a completed split, THE Easebuzz_Payment_Service SHALL return the existing split and SHALL create no additional split.
10. WHEN a refund is requested for an amount greater than the amount the gateway recorded as paid, THE RefundService SHALL reject the refund.
11. WHEN the sum of refunds for a payment would exceed the gateway-paid amount, THE RefundService SHALL reject the refund that crosses the threshold.
12. WHEN a payout is reported by the gateway, THE Easebuzz_Payment_Service SHALL persist an `EasebuzzPayout` linked to the corresponding Sub_Merchant.
13. WHEN a merchant completes a payment through the Easebuzz Android SDK, THE KhanaBook_Android_App SHALL restore the in-progress billing state and SHALL record the payment against the originating bill.
14. IF the app process is terminated during an Easebuzz payment, THEN THE `EasebuzzPaymentRecoveryWorker` SHALL reconcile the payment state against the server on the next app start.
15. THE Integration_Codebase SHALL include `application-sandbox.properties` for gateway sandbox testing and SHALL exclude live merchant credentials from version control.
16. WHEN an Easebuzz API call fails, THE Easebuzz_Payment_Service SHALL raise `EasebuzzApiException` and SHALL leave the local payment record in a state that permits retry.
17. THE KhanaBook_Server SHALL map `EasebuzzApiException` to HTTP 502.
18. THE Integration_Codebase SHALL deliver Sub_Merchant onboarding and KYC in a Phase that precedes the Phase delivering payment collection.
19. THE KhanaBook_Server SHALL default the Easebuzz_Payment_Service Feature_Flag to disabled, and SHALL require sandbox validation to pass before the flag is enabled in production.
20. WHEN the Easebuzz_Payment_Service Feature_Flag is disabled, THE KhanaBook_Android_App SHALL omit Easebuzz from the selectable payment modes and SHALL leave Cash, manual UPI, and POS terminal behaviour unchanged.
21. THE Integration_Codebase SHALL introduce Easebuzz as an additional payment mode through new methods or adapters, and SHALL NOT replace `BillingViewModel`, `BillRepository`, `PaymentSetValidator`, or the existing bill and payment tables with Source_Branch equivalents.
22. WHEN a payment is initiated through the Easebuzz Android SDK, THE KhanaBook_Android_App SHALL persist the originating bill identifier and payment operation identifier before the SDK activity is launched.

### Requirement 21: WIRE Platform Integration Deferral

**User Story:** As the product owner, I want WIRE treated as a later project, so that it does not extend the critical path of the merchant-facing release.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL treat the WIRE_Platform_Client as `DEFERRED`.
2. THE Integration_Codebase SHALL exclude `EasebuzzWireApiClient`, the WIRE lookup endpoints, the WIRE insta-collect and payout webhook endpoints, and the Web_Admin WIRE lookup action.
3. THE Integration_Codebase SHALL record WIRE as a follow-up specification with its own requirements, and SHALL NOT create schema objects for it.
4. WHERE Sub_Merchant reconciliation is required before WIRE ships, THE Web_Admin SHALL rely on the Easebuzz sub-merchant status endpoints already in scope under Requirement 20.

### Requirement 22: Operational Infrastructure Port

**User Story:** As the maintainer, I want v2's caching, structured logging, MDC propagation, and typed exception mapping, so that the server is easier to operate and debug.

These capabilities are cross-cutting server infrastructure, not restaurant-facing features. They have no meaningful disabled state, they are not gated by a Feature_Flag, and they never return HTTP 503. They ship in their own Phase whose only acceptance gate is that Base_Branch behaviour is unchanged.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL configure a Caffeine cache manager through `CacheConfig`.
2. THE KhanaBook_Server SHALL emit logs as structured JSON through `logback-spring.xml` and `logstash-logback-encoder`.
3. WHEN a request is handled on an asynchronous executor, THE KhanaBook_Server SHALL propagate the MDC trace identifier to that executor.
4. THE KhanaBook_Server SHALL map `BusinessRuleException` to HTTP 400 and `EntityNotFoundException` to HTTP 404.
5. WHEN structured logging is enabled, THE KhanaBook_Server SHALL continue to write the error identifier that operators grep for in container logs.
6. THE Integration_Codebase SHALL NOT define a Feature_Flag for any capability in this requirement.
7. WHEN a capability in this requirement is deployed, THE KhanaBook_Server SHALL return the same status and response field set for every Base_Branch endpoint as the Baseline_Tag server returns for the same request.
8. THE Integration_Codebase SHALL introduce caching only on read paths, and SHALL NOT cache any response whose staleness would alter a billing, payment, sync, or terminal decision.
9. WHEN structured logging is deployed, THE Deployment_Process SHALL confirm that the log lines the ops runbook greps for remain retrievable through `docker compose logs`.
10. THE Integration_Codebase SHALL require no Flyway migration and no Room migration for this requirement.


### Requirement 23: Refresh Token Rotation Deferral

**User Story:** As the product owner, I want refresh-token rotation treated as a later project, so that an authentication change does not sit on the critical path of the merchant-facing release.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL treat Refresh_Token_Flow as `DEFERRED`.
2. THE Integration_Codebase SHALL exclude the `RefreshToken` entity, its repository, its endpoints, and its schema objects.
3. THE KhanaBook_Server SHALL continue to authenticate every client with the Base_Branch 30-day JWT for the duration of this feature.
4. THE Integration_Codebase SHALL record Refresh_Token_Flow as a follow-up specification, and SHALL note that introducing it later requires the Legacy_Client compatibility criteria in Requirement 14 to be re-evaluated against the then-current fleet.

### Requirement 24: KYC Document Upload Port

**User Story:** As a restaurant owner, I want to upload KYC documents from my phone, so that sub-merchant onboarding can be completed without a computer.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL expose a KYC document upload endpoint under `/api/v1` that stores uploaded files on the configured local CDN path.
2. WHEN a KYC document is uploaded, THE KhanaBook_Server SHALL validate the file content type and the file size against the configured limits before storing the file.
3. IF an uploaded KYC document exceeds the configured size limit or has a disallowed content type, THEN THE KhanaBook_Server SHALL reject the upload with HTTP 400 and SHALL store no file.
4. WHEN a KYC document is stored, THE KhanaBook_Server SHALL restrict read access to the owning restaurant and to `KBOOK_ADMIN`.
5. WHEN a merchant selects a document on the KhanaBook_Android_App, THE KhanaBook_Android_App SHALL use the Android 13+ `PickVisualMedia` picker on supporting devices.

### Requirement 25: Fintech Admin Tranche Scope

**User Story:** As the product owner, I want the large fintech admin surface treated as its own tranche, so that it does not block the revenue-bearing and merchant-facing features.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL treat the sub-merchants page and the restaurant-settings page as in scope, and SHALL treat the remaining 17 Fintech_Admin_Tranche pages as `DEFERRED`.
2. THE Web_Admin SHALL implement the sub-merchants page and the restaurant-settings page with V1_Web_Admin_Design_System conventions and SHALL guard their backing endpoints with `@RequireRole`.
3. THE Integration_Codebase SHALL exclude the Angular route, the controller, the service, the entities, and the migrations for every `DEFERRED` Fintech_Admin_Tranche page.
4. THE Integration_Codebase SHALL exclude the chargeback, customer-profile, customer-privacy, and webhook-retry schema objects, because no in-scope page requires them.
5. THE Web_Admin SHALL provide the restaurant-settings business profile read and update endpoints as a prerequisite for the sub-merchant onboarding flow.
6. WHERE a `DEFERRED` page's capability is required by an in-scope feature, THE Integration_Codebase SHALL record that dependency and SHALL promote only the specific endpoint required rather than the whole page.

### Requirement 26: Storefront Customer Orders Exclusion

**User Story:** As the product owner, I want the storefront code that main deleted to stay deleted, so that unimplemented dead code is not resurrected by accident.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL exclude the storefront customer-orders capability.
2. THE Integration_Codebase SHALL exclude `StorefrontOrderRepository`, `StorefrontOrdersViewModel`, `StorefrontOrderDtos`, and every storefront endpoint, entity, and migration.
3. WHEN the CI_Pipeline runs, THE CI_Pipeline SHALL fail if a source file whose name contains `Storefront` is present in the Integration_Codebase.

### Requirement 27: Secrets and Configuration Management

**User Story:** As the operator, I want every new credential supplied through environment configuration, so that no secret enters version control.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL read the Firebase credential, Easebuzz merchant keys, SMTP credentials, and Swiggy and Zomato API keys from environment variables or externally mounted files.
2. THE Integration_Codebase SHALL list every new configuration key in `.env.example` with a placeholder value.
3. THE Integration_Codebase SHALL retain `.env`, `local.properties`, keystores, and service-account key files in `.gitignore`.
4. WHEN the CI_Pipeline runs, THE CI_Pipeline SHALL fail if a newly tracked file matches a known credential filename pattern.
5. THE Integration_Codebase SHALL record `Android/app/google-services.json` as a pre-existing tracked client-configuration file, and SHALL NOT treat its removal from version control as part of this feature. Untracking it later requires only a `git rm --cached` plus a `.gitignore` entry; history rewriting would be required only if material in past commits were judged sensitive enough to purge.
6. THE Integration_Codebase SHALL NOT add any server-side credential, merchant key, or service-account key to `Android/app/google-services.json` or to any other tracked file.
7. IF a required configuration key for a ported feature is absent at startup, THEN THE KhanaBook_Server SHALL start successfully with that feature disabled and SHALL log the disabled feature name.
8. WHEN a feature is disabled for missing configuration, THE KhanaBook_Server SHALL reject calls to that feature's endpoints with HTTP 503.

### Requirement 28: Phased Delivery, Gates, and Forward-Fix Rollback

**User Story:** As the operator of a live system, I want each phase independently deployable and reversible without losing production writes, so that a bad phase is contained without a destructive restore.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL define an ordered Phase list in which each Phase is independently deployable to production.
2. THE Integration_Codebase SHALL order the Phase list so that additive features precede features that modify billing, payment, or sync code paths.
3. WHEN a Phase is prepared for deployment, THE Preservation_Test_Suite SHALL pass in full before the Deployment_Process begins.
4. WHEN a Phase includes a Flyway migration, THE Deployment_Process SHALL complete the Database_Backup_Gate before the server container is rebuilt.
5. IF the Database_Backup_Gate does not produce a verified dump, THEN THE Deployment_Process SHALL halt.
6. WHEN a Phase deployment completes, THE Deployment_Process SHALL verify that `/api/v1/actuator/health` returns `{"status":"UP"}` before the Phase is declared successful.
7. WHEN a deployed Phase misbehaves, THE Deployment_Process SHALL disable the Phase's Feature_Flag as the first remediation step and SHALL NOT alter the database.
8. IF disabling the Feature_Flag does not restore correct behaviour, THEN THE Deployment_Process SHALL redeploy the previous server image and SHALL leave the migrated schema in place.
9. THE Integration_Codebase SHALL treat the Database_Backup_Gate dump as a disaster-recovery artifact and SHALL NOT define database restore as the routine rollback step for any Phase.
10. WHERE a database restore is unavoidable, THE Deployment_Process SHALL first capture a dump of the current post-deployment state so that writes accepted after the migration remain recoverable.
11. THE Integration_Codebase SHALL document, for each Phase, the rollback sequence in the order: disable Feature_Flag, redeploy previous server image, restore Web_Admin docroot from its timestamped backup.
12. THE Integration_Codebase SHALL tag Base_Branch head before the first Phase branch is created.
13. WHEN a Phase changes the Android database version, THE Integration_Codebase SHALL increment the Android `versionCode` above 20 and SHALL publish the corresponding Room schema JSON export in the same commit.
14. WHEN a released Android build proves defective, THE Integration_Codebase SHALL remediate by disabling the feature through server-supplied configuration or by publishing a higher `versionCode` build, and SHALL NOT lower the Room database version.
15. THE Integration_Codebase SHALL define a post-deployment smoke checklist per Phase covering bill creation, sync push, sync pull, KOT print, and terminal registration.
16. WHEN a Phase is released to the Android fleet, THE Integration_Codebase SHALL stage the rollout to an internal test track and then to a single pilot restaurant before general availability.

### Requirement 29: Regression Evidence per Phase

**User Story:** As the maintainer, I want documented proof that v1 behaviour is unchanged after each phase, so that the preservation guarantee is verifiable rather than assumed.

#### Acceptance Criteria

1. WHEN a Phase is completed, THE Integration_Codebase SHALL record the Preservation_Test_Suite result set for that Phase.
2. THE Preservation_Test_Suite SHALL include at least one test for each preservation requirement numbered 4 through 12.
3. WHEN a ported feature touches a file that Base_Branch also modified after Merge_Base, THE Integration_Codebase SHALL record that file in a conflict register together with the resolution taken.
4. WHEN a Phase adds a server endpoint, THE Integration_Codebase SHALL record that endpoint's path, method, required role, and target consumer.
5. THE Integration_Codebase SHALL record the Flyway version range and the Room version range introduced by each Phase.

### Requirement 30: Feature Flags

**User Story:** As the operator, I want every ported feature switchable at runtime, so that a misbehaving feature is contained without a redeploy or a database change.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL define a Feature_Flag for each ported feature specified in Requirements 17 through 20, 24, and 25.
2. THE Integration_Codebase SHALL NOT define a Feature_Flag for Requirement 22, because that requirement covers cross-cutting infrastructure with no meaningful disabled state.

**Storage and resolution**

3. THE KhanaBook_Server SHALL persist Feature_Flag state in a PostgreSQL table created by a Flyway migration.
4. THE Integration_Codebase SHALL deliver the Feature_Flag table in the Phase that establishes the integration foundation, and SHALL assign that migration the first available version at V48 or above.
5. THE Feature_Flag model SHALL support a global state per flag and a per-restaurant override.
6. WHEN a per-restaurant override exists, THE KhanaBook_Server SHALL resolve the effective state from that override.
7. WHEN no per-restaurant override exists, THE KhanaBook_Server SHALL resolve the effective state from the global state.
8. WHEN the global state of a flag is disabled, THE KhanaBook_Server SHALL resolve the effective state to disabled for every restaurant regardless of any per-restaurant override.
9. THE KhanaBook_Server SHALL default every global Feature_Flag state to disabled on first migration.
10. IF a configuration value required by a feature is absent or invalid, THEN THE KhanaBook_Server SHALL resolve that feature's effective state to disabled regardless of the persisted state.
11. WHEN a Feature_Flag row is absent for a known flag, THE KhanaBook_Server SHALL resolve that flag's effective state to disabled.

**Propagation without redeployment**

12. WHEN a Feature_Flag is changed, THE KhanaBook_Server SHALL apply the new effective state without a container rebuild, without a container restart, and without a redeployment.
13. WHEN a Feature_Flag is changed, THE KhanaBook_Server SHALL apply the new effective state to subsequent requests within a configured propagation deadline.
14. THE Integration_Codebase SHALL record the configured propagation deadline, and the Deployment_Process SHALL treat that deadline as the worst-case time to contain a misbehaving feature.
15. WHERE the KhanaBook_Server caches resolved Feature_Flag state, THE KhanaBook_Server SHALL bound that cache by the propagation deadline.

**Server behaviour when disabled**

16. WHEN a Feature_Flag is disabled, THE KhanaBook_Server SHALL reject calls to that feature's merchant-facing and administration endpoints with HTTP 503 and SHALL leave every Base_Branch endpoint unaffected.
17. WHEN a Feature_Flag is disabled, THE KhanaBook_Server SHALL skip that feature's scheduled jobs and outbound provider calls.
18. THE HTTP 503 behaviour in criterion 16 SHALL NOT apply to any Webhook_Endpoint, which instead follows Requirement 33.
19. WHEN a Feature_Flag is disabled after that feature has written rows, THE KhanaBook_Server SHALL retain those rows and SHALL resume processing them when the flag is re-enabled.

**Audit**

20. WHEN a Feature_Flag is changed, THE KhanaBook_Server SHALL write an audit record capturing the flag, the previous state, the new state, the scope, the actor, and the timestamp.
21. THE KhanaBook_Server SHALL restrict Feature_Flag mutation to `KBOOK_ADMIN` and SHALL enforce that restriction through `@RequireRole`.
22. THE Web_Admin SHALL present the current effective state of every Feature_Flag and its change history to `KBOOK_ADMIN`.

**Client behaviour**

23. WHEN the KhanaBook_Android_App starts a session, THE KhanaBook_Server SHALL report the effective state of every ported feature for that restaurant.
24. WHERE a ported feature is reported disabled, THE KhanaBook_Android_App SHALL hide that feature's entry points rather than presenting an action that fails.
25. IF the KhanaBook_Android_App cannot retrieve feature state, THEN THE KhanaBook_Android_App SHALL treat every ported feature as disabled and SHALL leave all Base_Branch functionality available.
26. THE KhanaBook_Android_App SHALL expire cached feature state after a configured interval, and on expiry SHALL treat every ported feature as disabled until state is retrieved again.
27. WHILE the KhanaBook_Android_App is offline, THE KhanaBook_Android_App SHALL retain full Base_Branch offline billing, printing, and sync-queueing behaviour.

**Verification**

28. THE Preservation_Test_Suite SHALL include a test asserting that with every Feature_Flag disabled, the Integration_Codebase server satisfies the Base_Branch endpoint contract.
29. THE Preservation_Test_Suite SHALL include a test asserting that a per-restaurant override cannot enable a feature whose global state is disabled.
30. THE Preservation_Test_Suite SHALL include a test asserting that absent configuration forces the effective state to disabled even when the persisted state is enabled.


### Requirement 31: Invoice Template Migration

**User Story:** As a merchant, I want the invoice to keep rendering exactly the same content after it moves to a template engine, so that my receipts and GST breakdown are unchanged.

This is a rewrite of a path that every existing bill already exercises, so it is neither infrastructure nor a new feature. It ships in its own Phase behind a rendering switch rather than a restaurant-facing Feature_Flag.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL render invoices from a Thymeleaf `invoice.html` template.
2. WHEN an invoice is rendered from the template, THE KhanaBook_Server SHALL produce the same field values for invoice number, line items, subtotal, CGST, SGST, total tax, total, and footer as the Base_Branch StringBuilder output produces for the same bill.
3. THE Integration_Codebase SHALL retain the Base_Branch StringBuilder renderer alongside the template renderer, selectable by configuration.
4. THE KhanaBook_Server SHALL default the renderer selection to the Base_Branch StringBuilder renderer.
5. IF template rendering raises an exception, THEN THE KhanaBook_Server SHALL fall back to the Base_Branch StringBuilder renderer and SHALL record the failure.
6. THE Integration_Codebase SHALL remove the Base_Branch StringBuilder renderer only in a later Phase, after the template renderer has served production traffic for a recorded observation period.
7. THE Preservation_Test_Suite SHALL include a differential test asserting field equality between the two renderers across a generated bill set.

### Requirement 32: Transactional Email

**User Story:** As a restaurant owner, I want onboarding and refund emails, so that I have a record outside the app.

Email is triggered by specific features, so each template ships in the Phase that owns its trigger rather than in an infrastructure Phase.

#### Acceptance Criteria

1. THE KhanaBook_Server SHALL send transactional email through `EmailNotificationService`.
2. THE Integration_Codebase SHALL deliver the onboarding-welcome template in the Phase that delivers Sub_Merchant onboarding.
3. THE Integration_Codebase SHALL deliver the refund-confirmation template in the Phase that delivers Easebuzz payment collection.
4. THE Integration_Codebase SHALL exclude the settlement-notification and chargeback-alert templates, because Requirement 25 defers the features that would trigger them.
5. IF SMTP credentials are absent, THEN THE `EmailNotificationService` SHALL log the failure and SHALL complete the originating business operation successfully.
6. WHEN the Feature_Flag of the feature that triggers an email is disabled, THE `EmailNotificationService` SHALL send no email for that trigger.
7. THE `EmailNotificationService` SHALL NOT block the request thread of the operation that triggered the email.

### Requirement 33: Webhook Inbox and Disabled-State Behaviour

**User Story:** As the operator, I want inbound provider webhooks durably captured even while their feature is switched off, so that disabling a flag never triggers a provider retry storm and never loses a settlement or order event.

Requirement 30.16 returns HTTP 503 for a disabled feature's endpoints. That response is correct for merchant-facing and admin endpoints, and wrong for provider webhooks: Easebuzz, Swiggy, and Zomato retry on 5xx against a finite budget, so a flag left off long enough converts into permanent event loss. Webhook endpoints are therefore exempt from Requirement 30.16 and follow the contract below.

#### Acceptance Criteria

1. THE Integration_Codebase SHALL classify every inbound provider webhook endpoint as a Webhook_Endpoint and SHALL exempt it from the HTTP 503 behaviour in Requirement 30.16.
2. WHEN a request arrives at a Webhook_Endpoint, THE KhanaBook_Server SHALL verify the request signature or HMAC before any other processing, irrespective of Feature_Flag state.
3. IF signature verification fails, THEN THE KhanaBook_Server SHALL reject the request with HTTP 401 and SHALL persist no inbox record.
4. WHEN signature verification succeeds, THE KhanaBook_Server SHALL persist the raw payload, the provider identity, the provider event identifier, the provider-supplied sequence value or event timestamp, and the receipt timestamp to the Webhook_Inbox before any business processing.
5. WHEN an inbox record is persisted, THE KhanaBook_Server SHALL return the success response that the originating provider's specification requires.
6. WHERE the owning Feature_Flag is disabled, THE KhanaBook_Server SHALL persist the inbox record, SHALL return the provider success response, and SHALL NOT apply any business state change.
7. WHEN the same provider identity and provider event identifier pair is received more than once, THE Webhook_Inbox SHALL retain exactly one record for that pair.
8. THE Webhook_Inbox SHALL enforce uniqueness on the pair of provider identity and provider event identifier, and SHALL NOT enforce uniqueness on the provider event identifier alone.
9. WHEN a Webhook_Inbox record is processed more than once, THE KhanaBook_Server SHALL produce the same final business state as processing it once.
**Ordering**

10. THE Integration_Codebase SHALL define, per provider, an Inbox_Ordering_Key composed of the provider-supplied sequence value where the provider supplies one, and the provider event timestamp otherwise, with the receipt timestamp as the final tie-break.
11. THE Integration_Codebase SHALL NOT derive processing order from the provider event identifier, because provider event identifiers are not guaranteed to be monotonic or lexically sortable.
12. THE KhanaBook_Server SHALL derive an Inbox_Aggregate for each record from the domain identifier of the entity that record mutates, as follows:

    | Event class | Inbox_Aggregate |
    |---|---|
    | Marketplace order | provider identity + restaurant + external order identifier |
    | Payment | provider identity + restaurant + gateway transaction identifier |
    | Refund | provider identity + restaurant + gateway transaction identifier of the payment being refunded |
    | Payout | provider identity + payout identifier, or the sub-merchant identifier where the provider reports no payout identifier |
    | Sub-merchant KYC or onboarding status | sub-merchant identifier |
    | Compliance or licence event | restaurant + licence identifier |

13. THE KhanaBook_Server SHALL NOT use the restaurant alone as an Inbox_Aggregate for any event class, because doing so would let one failed event halt every unrelated event for that restaurant.
14. WHERE a record's domain identifier cannot be extracted from the payload, THE KhanaBook_Server SHALL assign that record an Inbox_Aggregate unique to itself, so that it blocks no other record.
15. WHEN unprocessed records are drained, THE KhanaBook_Server SHALL process records within one Inbox_Aggregate in ascending Inbox_Ordering_Key order.
16. THE KhanaBook_Server SHALL NOT impose an ordering constraint between records belonging to different Inbox_Aggregates, and MAY process different aggregates concurrently.
17. IF a record within an Inbox_Aggregate cannot be processed, THEN THE KhanaBook_Server SHALL halt processing of later records in that same Inbox_Aggregate and SHALL continue processing other aggregates.
18. IF a provider supplies neither a sequence value nor an event timestamp, THEN THE KhanaBook_Server SHALL order that provider's records by receipt timestamp and SHALL record that the ordering is receipt-derived.

**Credential availability**

19. IF the credential required to verify a Webhook_Endpoint's signature is absent or invalid, THEN THE KhanaBook_Server SHALL reject the request with HTTP 503, SHALL persist no inbox record, and SHALL record the configuration failure.
20. THE KhanaBook_Server SHALL NOT persist an unverified payload to the Webhook_Inbox under any condition, including missing credentials.
21. THE KhanaBook_Server SHALL NOT return a provider success response for a request it could not verify.
22. WHERE a provider's specification defines a retry-eliciting status other than HTTP 503, THE KhanaBook_Server SHALL return that provider's retry-eliciting status instead.
23. THE KhanaBook_Server SHALL surface missing or invalid webhook verification credentials as a startup and runtime health signal, so that the condition is visible before a provider's retry budget is consumed.

**Failure handling and retention**

24. IF business processing of an inbox record fails, THEN THE KhanaBook_Server SHALL retain the record as unprocessed, SHALL record the failure reason and attempt count, and SHALL NOT return a failure to the provider.
25. WHEN an inbox record has failed processing more than the configured attempt limit, THE KhanaBook_Server SHALL mark it for operator review and SHALL surface it in the Web_Admin.
26. THE KhanaBook_Server SHALL expose unprocessed and failed Webhook_Inbox counts through `/api/v1/actuator/health` detail or an authenticated `KBOOK_ADMIN` endpoint.
27. THE Integration_Codebase SHALL retain Webhook_Inbox records for a configured retention period and SHALL NOT delete an unprocessed record.
28. THE Webhook_Inbox SHALL scope every record to the restaurant the provider event resolves to, and SHALL record events that resolve to no known restaurant as unresolved rather than discarding them.

**Single processing path**

The equivalence guarantee in criterion 39 is only achievable if there is one processing path. If an arriving event were applied inline while the flag is enabled, and a drained event were applied by a worker, those would be two implementations that could diverge, and no test could establish that draining reproduces live behaviour. Business processing therefore always runs from the durable inbox, never in the receiving request.

29. THE KhanaBook_Server SHALL apply business state changes for a webhook event only from a persisted Webhook_Inbox record, and SHALL NOT apply them in the HTTP request thread that received the webhook.
30. THE Webhook_Endpoint request handler SHALL perform only signature verification, inbox persistence, and provider acknowledgement, and SHALL then return.
31. THE Integration_Codebase SHALL process Webhook_Inbox records through an Inbox_Worker that runs independently of the receiving request.
32. WHERE the owning Feature_Flag is enabled when a record arrives, THE Inbox_Worker SHALL process that record through the same code path it uses when draining records that arrived while the flag was disabled.
33. THE Inbox_Worker SHALL apply the ordering rules in criteria 10 through 18 irrespective of the Feature_Flag state at the moment each record arrived.
34. WHEN the owning Feature_Flag transitions from disabled to enabled, THE Inbox_Worker SHALL process every unprocessed Webhook_Inbox record for that provider without further operator action.
35. WHILE the owning Feature_Flag is disabled, THE Inbox_Worker SHALL leave that provider's records unprocessed and SHALL continue processing providers whose flags are enabled.
36. THE Inbox_Worker SHALL bound the delay between inbox persistence and processing by a configured interval, and THE Integration_Codebase SHALL record that interval.
37. THE Integration_Codebase SHALL prevent concurrent Inbox_Worker executions from processing the same Webhook_Inbox record simultaneously.

**Verification**

38. THE Preservation_Test_Suite SHALL include a test asserting that with the Easebuzz and marketplace Feature_Flags disabled, a signed webhook returns the provider success response and produces exactly one unprocessed inbox record.
39. THE Preservation_Test_Suite SHALL include a test asserting that enabling the flag afterwards drains the inbox to the same final state as processing the events with the flag enabled from the start.
40. THE Preservation_Test_Suite SHALL include a test asserting that two providers may deliver events sharing the same provider event identifier and that both records are retained.
41. THE Preservation_Test_Suite SHALL include a test asserting that a webhook arriving while its verification credential is absent produces no inbox record and a retry-eliciting response.
42. THE Preservation_Test_Suite SHALL include a test asserting that a failing record in one Inbox_Aggregate does not delay processing of records in any other Inbox_Aggregate.

## Correctness Properties for Property-Based Testing

These properties are derived from the acceptance criteria above and are suitable for jqwik (server) and Kotlin property tests (Android). They extend the 6 existing jqwik suites on Base_Branch rather than replacing them.

### Invariants

- **P1 (Terminal cap, Req 4.3, 4.4):** For any sequence of terminal registration and release operations against one restaurant, the count of active terminals is always in `0..5`.
- **P2 (Invoice uniqueness, Req 4.6):** For any interleaving of bill creations across up to 5 terminals of one restaurant on one calendar day, the set of assigned invoice numbers has no duplicates.
- **P3 (Tax decomposition, Req 10.3):** For any cart, `cgst + sgst == totalTax` and `subtotal + totalTax == billTotal`, within the defined rounding tolerance.
- **P4 (Split conservation, Req 20.8):** For any settled amount and commission configuration, `platformShare + subMerchantShare == settledAmount` and both shares are non-negative.
- **P5 (Refund cap, Req 20.10, 20.11):** For any sequence of refund requests against a payment, the sum of accepted refunds never exceeds the gateway-paid amount.
- **P6 (Tenant isolation, Req 7.4):** For any two restaurants and any generated record set, a query executed under restaurant A returns no row belonging to restaurant B.

### Round-Trip Properties

- **P7 (Invoice render round-trip, Req 31.2):** For any bill, the field values extracted from the Thymeleaf-rendered invoice equal the corresponding field values on the bill entity.
- **P8 (Sync push/pull round-trip, Req 9.4):** For any locally created bill with children, pushing then pulling that bill yields a record whose business fields, including `sourceChannel`, equal the original.
- **P9 (Room migration round-trip, Req 3.7):** For any generated database populated at version 62, migrating to the head version and reading back yields the same row set, and the resulting schema equals a freshly created head-version schema.
- **P10 (Webhook payload parse/serialize, Req 19.3, 20.5):** For any valid marketplace or gateway webhook payload, parsing then serializing then parsing yields an equivalent object.

### Idempotence

- **P11 (Device token registration, Req 17.3):** Registering the same device token `n` times yields exactly one active token record.
- **P12 (Webhook replay, Req 20.7, 19.4):** Delivering the same webhook event identifier `n` times produces the same final state as delivering it once.
- **P13 (Post-split, Req 20.9):** Applying Post_Split `n` times to one payment produces the same split rows as applying it once.
- **P14 (Compliance alert dedup, Req 18.3):** Running the compliance alert schedule `n` times within one alert window produces exactly one alert per licence.
- **P15 (Marketplace auto-enable, Req 19.6):** Running sync `n` times for a configured restaurant leaves marketplace ordering enabled exactly once, with no duplicate enablement records.

### Metamorphic Properties

- **P16 (Delta KOT, Req 8.2):** For any order, the item count on the second KOT print equals the number of items added since the first print.
- **P17 (Quarantine progress, Req 9.2):** For any sync batch containing `k` unprocessable records, the number of successfully processed records equals `batchSize - k` and the quarantine table grows by exactly `k`.
- **P18 (Additive response schema, Req 14.2):** For any endpoint present on Base_Branch, the Integration_Codebase response field set is a superset of the Base_Branch response field set with identical types on the shared fields.
- **P19 (Sub-merchant fallback, Req 20.4):** For any Sub_Merchant state other than `ACTIVE`, the resolved collection account equals the parent merchant account; for `ACTIVE` it equals the Sub_Merchant account.

### Confluence

- **P20 (Sync order independence, Req 9.5):** For any set of independent sync records, applying them in any permutation yields the same final server state.
- **P21 (Last-write-wins, Req 9.5):** For any set of updates to one record with distinct versions, the final stored version equals the maximum version regardless of arrival order.

### Error Conditions

- **P22 (HMAC rejection, Req 20.6, 19.5):** For any payload with a corrupted signature, the webhook is rejected and no state change is observable.
- **P23 (Role gate, Req 5.4):** For any endpoint annotated with `@RequireRole` and any role outside the allowed set, the response status is 403.
- **P24 (Upload validation, Req 24.3):** For any file exceeding the size limit or carrying a disallowed content type, the upload is rejected and no file is written.
- **P25 (Missing config, Req 27.7, 27.8, Req 30.10):** For any subset of new configuration keys removed, the server starts, the affected features resolve to disabled, and all Base_Branch endpoints continue to return their normal statuses.
- **P26 (Flag-off equivalence, Req 30.16, 30.28):** For any subset of Feature_Flags disabled, every Base_Branch endpoint returns the same status and response field set as the Baseline_Tag server for the same request.
- **P27 (Additive migration compatibility, Req 2.7, 2.15):** For any Phase migration set applied to a Baseline_Tag database, the Baseline_Tag server image starts and every Base_Branch write operation still succeeds.
- **P28 (Flag toggle preserves data, Req 30.19):** For any sequence of enable and disable operations on one Feature_Flag, the row set that feature wrote is unchanged by the toggling.
- **P29 (Global kill switch dominance, Req 30.8):** For any combination of per-restaurant overrides, a disabled global state resolves every restaurant's effective state to disabled.
- **P30 (Webhook capture under any flag state, Req 33.6, 33.7, 33.38):** For any signed webhook payload and any Feature_Flag state, the response is the provider success response and the inbox contains exactly one record for that pair of provider identity and provider event identifier.
- **P31 (Inbox drain equivalence, Req 33.9, 33.32, 33.39):** For any set of webhook events delivered while a flag is disabled, enabling the flag and draining the inbox yields the same final business state as delivering the same events with the flag enabled throughout, for any arrival order and any number of redeliveries.
- **P32 (Renderer equivalence, Req 31.2, 31.7):** For any generated bill, the Thymeleaf renderer and the StringBuilder renderer produce equal values for every invoice field.
- **P33 (Composite inbox key, Req 33.7, 33.8, 33.40):** For any two distinct provider identities emitting the same provider event identifier, the inbox retains one record per provider; for any single provider identity redelivering one identifier `n` times, the inbox retains exactly one record.
- **P34 (Aggregate ordering and isolation, Req 33.15, 33.16, 33.17, 33.42):** For any generated event set, records within one Inbox_Aggregate are applied in ascending Inbox_Ordering_Key order regardless of arrival order; final state is independent of the interleaving across distinct aggregates; and a failing record halts only its own aggregate's later records, delaying no other aggregate.
- **P35 (Verification fail-closed, Req 33.19, 33.20, 33.21, 33.41):** For any payload and any state of the verification credential, an unverified request produces no inbox record and no provider success response.
- **P36 (Single processing path, Req 33.29, 33.30, 33.32):** For any webhook event and any Feature_Flag state at arrival, no business state change is observable until a Webhook_Inbox record exists for that event.
- **P37 (Aggregate derivation totality, Req 33.12, 33.13, 33.14):** For any valid provider payload, the derived Inbox_Aggregate is non-null and is not the restaurant alone; for any payload whose domain identifier is unextractable, the derived aggregate is unique to that record.

### Deliberately Not Property-Tested

- Firebase, Easebuzz, Swiggy, and Zomato live API behaviour — covered by 1-3 example integration tests against sandbox endpoints, since the behaviour does not vary meaningfully with generated input and the call cost is high.
- Docker Compose, Apache proxy, and deploy script wiring — covered by the post-deployment smoke checklist.
- SMTP delivery — covered by a single example test with a mock mail sender.
- Structured logging output format — covered by one example assertion on a captured log line.

## Resolved Decisions

| # | Decision | Resolution | Encoded in |
|---|---|---|---|
| 1 | Fintech_Admin_Tranche scope | Sub-merchants and restaurant-settings in scope; the other 17 pages `DEFERRED` | Req 25 |
| 2 | Easebuzz go-live gating | Ship disabled by default behind a Feature_Flag; sandbox validation precedes production enablement; onboarding lands in an earlier Phase than payments | Req 20.18-20.22, Req 30 |
| 3 | UI scope | v1 dark-only Material 3 Compose and v1 plain-CSS Web_Admin are the only design systems; new screens rebuilt with v1 tokens | Req 15 |
| 4 | Storefront customer orders | `DROP` | Req 26 |
| 5 | WIRE platform | `DEFERRED` to a follow-up specification | Req 21 |
| 6 | Refresh token rotation | `DEFERRED` to a follow-up specification | Req 23 |
| 7 | Flyway strategy | Author fresh additive migrations at V48+; never renumber or copy v2 scripts | Req 2 |
| 8 | Rollback model | Forward-fix: disable Feature_Flag, then redeploy previous image; database restore is disaster recovery only | Req 28.7-28.11 |
| 9 | Android rollback | Forward-only; remediate by disabling the feature or shipping a higher `versionCode`, never by lowering the Room version | Req 3.9, Req 28.14 |
| 10 | `google-services.json` | Pre-existing tracked client config; out of scope for this feature. Server-side credentials stay external | Req 17.13, Req 27.5 |

## Phase Sequence

The Phase list satisfying Requirement 28.2. The Reversal column states how each Phase is contained if it misbehaves; not every Phase is flag-reversible.

| Phase | Deliverable | Flyway | Room | Reversal |
|---|---|---|---|---|
| 0 | Establish baseline provenance, resolve the working tree, verify build and tests, cut Baseline_Tag | none | none | not deployed |
| 1 | Preservation harness: tests covering Requirements 4 through 12 | none | none | not deployed |
| 2 | Integration foundation: Feature_Flag table and audit table, Flag_Admin_Surface (API + Web_Admin page), Webhook_Inbox table, endpoint register, conflict register, `.env.example` keys | V48 | none | redeploy previous image |
| 3 | Operational infrastructure: Caffeine caching, structured JSON logging, MDC propagation, exception mapping (Req 22) | none | none | redeploy previous image |
| 4 | Push notifications (Req 17) | V49 | 62 to 63 | Feature_Flag |
| 5 | FSSAI and GST compliance, `Remind Me` only (Req 18) | next | next if required | Feature_Flag |
| 6 | Marketplace orders (Req 19) | next | next | Feature_Flag |
| 7 | Easebuzz sub-merchant onboarding, KYC upload, onboarding-welcome email (Req 20 onboarding, Req 24, Req 32) | next | next | Feature_Flag |
| 8 | Easebuzz payment collection and recovery, refund-confirmation email; enables FSSAI `Pay Now` (Req 20 payments) | next | next | Feature_Flag |
| 9 | Invoice template migration behind renderer selection (Req 31) | none | none | renderer selection |
| 10 | Pilot rollout and general availability | none | none | Feature_Flag per feature |
| 11 | Follow-up specifications: WIRE, refresh tokens, the 17 deferred admin pages, StringBuilder renderer removal | separate | separate | separate |

Notes on ordering:

- Phase 2 must precede every flagged Phase, because Requirement 30.3 puts flag state in PostgreSQL and Requirement 33.4 puts the inbox there. Both tables land in V48, so notifications begin at V49.
- Phase 2 must also deliver the Flag_Admin_Surface, not just the tables. Requirements 30.20 through 30.22 make audit and `KBOOK_ADMIN` visibility part of the flag contract, and Requirement 28.7 makes flag disablement the first rollback step. A flag that can only be changed by direct SQL does not satisfy either, so the API and the Web_Admin page are Phase 2 deliverables rather than follow-up work.
- Phase 3 carries no Feature_Flag by Requirement 22.6. It is reversed by redeploying the previous image, and its acceptance gate is Base_Branch behavioural equivalence rather than a new capability.
- Phase 8 depends on Phase 7: Requirement 20.18 requires onboarding to ship before collection.
- Requirement 18.5 depends on Phase 8. Phase 5 ships the tracker and `Remind Me`; `Pay Now` stays hidden behind the Easebuzz Feature_Flag until collection exists.
- Phase 9 is separated from Phase 3 because it rewrites a path every existing bill already exercises, so it needs its own observation window and its own reversal switch rather than being bundled with infrastructure.
- Phase 11 removes the StringBuilder renderer only after Requirement 31.6's observation period.

## Baseline Precondition

Phase 0 cannot complete until every uncommitted working-tree change on Base_Branch is resolved by explicit decision, and until the production baseline's provenance is positively established.

**Provenance correlation.** The three modules release independently: the Android app ships through Play, the server ships through `deploy-production.sh`, and Web_Admin ships through `deploy-web.sh`. They are therefore expected to sit on different commits at any given moment, and requiring their identifiers to agree would describe a state the project has never been in.

Phase 0 SHALL instead establish three separate identifiers, reconcile each module's deployed content onto a mutable candidate commit, and only then cut the immutable tag:

| Identifier | Established from |
|---|---|
| Android_Baseline_Commit | The Play Console artifact currently distributed to merchants, its `versionCode`, and the release signing and build records that produced it |
| Server_Baseline_Commit | The `git.commit.id` that authenticated `/api/v1/actuator/info` reports for the running production container |
| WebAdmin_Baseline_Commit | The build manifest of the bundle currently served from the production docroot, correlated against the `deploy-web.sh` run that placed it there |

The requirement is **content equivalence, not ancestry**. A deployed commit may sit on a deleted branch, on a fork, or nowhere reachable at all, and demanding that every deployed commit already be an ancestor would either be vacuous or would block Phase 0 on history that cannot be recovered. What matters is that the baseline's module paths contain the behaviour production is running.

Reconciliation therefore happens against a mutable working commit, not against the tag. Baseline_Candidate is the branch head that reconciliation work lands on; Baseline_Tag is the immutable annotated tag cut at that head once reconciliation is finished and the baseline tests pass. Porting content "onto Baseline_Tag" would require writing to a tag that does not yet exist, so all porting targets Baseline_Candidate.

**Establishing the deployed representation**

1. Phase 0 SHALL establish a Deployed_Module_Representation for each of the three modules.
2. WHERE a module's deployed identifier resolves to a commit in the repository, THE Deployed_Module_Representation SHALL be that commit SHA, whether or not it is reachable from Base_Branch.
3. WHERE a module's deployed identifier cannot be resolved to any commit in the repository, THE Deployed_Module_Representation SHALL be an artifact-derived module snapshot reconstructed from the deployed artifact, and Phase 0 SHALL mark that module's provenance as artifact-derived.
4. THE artifact-derived module snapshot SHALL be materialised as a comparable tree of that module's paths, so that the comparison in criterion 7 applies uniformly to both representation kinds.
5. WHERE an artifact-derived snapshot cannot reproduce a source path because the artifact contains only compiled or bundled output, Phase 0 SHALL record that path as unverifiable and SHALL list it as a residual provenance risk rather than treating it as equivalent.

**Reconciling onto the candidate**

6. Phase 0 SHALL designate a Baseline_Candidate commit on Base_Branch after every uncommitted working-tree change is resolved.
7. FOR each module, Phase 0 SHALL compare that module's paths at Baseline_Candidate against the same paths in that module's Deployed_Module_Representation.
8. THE module path sets SHALL be `Android/` for Android_Baseline_Commit, `server/` for Server_Baseline_Commit, and `web-admin/` for WebAdmin_Baseline_Commit.
9. WHEN the comparison reports no difference for a module, Phase 0 SHALL record that module as reconciled.
10. IF the comparison reports content present in a module's Deployed_Module_Representation and absent at Baseline_Candidate, THEN Phase 0 SHALL port that content onto Baseline_Candidate by cherry-pick or by re-implementation, and SHALL record the reconciliation decision for each differing path.
11. WHEN content is ported onto Baseline_Candidate, Baseline_Candidate SHALL advance to the resulting commit and the comparison in criterion 7 SHALL be repeated for that module.
12. IF the comparison reports content present at Baseline_Candidate and absent in the Deployed_Module_Representation, THEN Phase 0 SHALL record that the module's next deployment will carry that content into production for the first time, and SHALL include it in that module's first Phase smoke checklist.

**Finalising the baseline**

13. Phase 0 SHALL run the v1 build and the Preservation_Test_Suite against the finalised Baseline_Candidate.
14. IF the build or the Preservation_Test_Suite fails against Baseline_Candidate, THEN Phase 0 SHALL resolve the failure and SHALL NOT cut Baseline_Tag.
15. Baseline_Tag SHALL be cut as an immutable annotated tag at the finalised Baseline_Candidate commit, only after every module is recorded as reconciled under criterion 9, following any required criterion 10 through 11 reconciliation loop, and criterion 13 has passed.
16. THE Integration_Codebase SHALL NOT move or re-cut Baseline_Tag once it exists; a later correction SHALL be expressed as a new tag with a recorded supersession reason.
17. THE Preservation_Test_Suite results for Phase 1 SHALL be measured against Baseline_Tag, not against any individual Deployed_Module_Representation.
18. Phase 0 SHALL record the reconciliation outcome per module, so that each Phase knows which module content was ported, which was already equivalent, which is newly reaching production, and which paths remain unverifiable under criterion 5.

**Working-tree inventory.** The categories below are dated rather than counted, because the count changes with ongoing local work. Phase 0 SHALL regenerate this inventory from `git status` at the time the baseline is cut and SHALL resolve every entry it produces, not only the categories listed here.

*Inventory as observed on the date this requirement was written:*

| Item | Files | Decision needed |
|---|---|---|
| Tenant DAO and bill DAO edits | `TenantDaos.kt`, `BillDao.kt`, `RestaurantDao.kt`, `RestaurantRepository.kt` | Commit into baseline, or revert |
| Billing edits | `BillingViewModel.kt`, `NewBillScreen.kt` | Commit into baseline, or revert |
| Settings and app-lock edits | `SettingsScreen.kt`, `SettingsViewModel.kt`, `AppLockConfigSection.kt` | Commit into baseline, or revert |
| Launcher icon format migration | 15 deleted `.webp`, 15 untracked `.png`, 2 modified `mipmap-anydpi-v26` XML | Complete and commit, or revert |
| Build configuration drift | `build.gradle.kts`, `libs.versions.toml`, `gradle-wrapper.properties` | Commit into baseline, or revert |
| Tracked credential file modified | `Android/app/google-services.json` | Confirm the change is intended client config |
| New interaction-feedback work | `ui/feedback/`, `InteractionFeedbackSection.kt`, matching unit and androidTest dirs | Commit into baseline, or park on a branch |
| Untracked directories | `Android/branding/`, `KhanabookUI/`, `docs/p0-remediation/`, `performance/` | Commit, gitignore, or remove |
| Isolation test edit | `BillDaoIsolationTest.kt` | Commit into baseline, or revert |

Baseline_Tag SHALL be cut only after the working tree is clean and `mvn test`, `./gradlew.bat testDebugUnitTest`, and `npm run build` all pass.

## Per-Phase Gate

Every Phase SHALL pass this gate before the Phase is declared complete:

- `mvn test` in `server/`
- `./gradlew.bat testDebugUnitTest` and `./gradlew.bat assembleDebug` in `Android/`
- Room migration tests for every version transition the Phase introduces
- `npm run build` in `web-admin/` when the Phase changes Web_Admin
- No protected v1 class deleted or replaced
- No `/api/v2` route present
- No V2_Design_System artifact present
- No newly tracked credential file
- Smoke: bill creation, sync push, sync pull, KOT print, terminal registration
- Recorded Flyway version range, Room version range, and rollback sequence

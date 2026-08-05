# Implementation Plan

## Overview

Phases map 1:1 to the Phase Sequence in `requirements.md`. Every phase ends at the Per-Phase Gate: `mvn test`, `./gradlew.bat testDebugUnitTest`, `./gradlew.bat assembleDebug`, Room migration tests for any version transition, `npm run build` when web-admin changes, no protected v1 class deleted, no `/api/v2` route, no V2_Design_System artifact, no newly tracked credential, and the five smoke checks.

Rollback for any deployed phase is: set `kill_switched = true` (one `UPDATE`, no schema change), then redeploy the previous image, then restore the web-admin docroot. Database restore is disaster recovery only.

Tasks that are process constraints rather than code are marked and still require a verifiable artifact.

## Task Dependency Graph

```
Phase 0  (1 → 2 → 3)                       baseline provenance, no deploy
   ▼
Phase 1  (4)                               preservation harness, no deploy
   ▼
Phase 2  (5 → 6 → 7, 5 → 8, 9)             V48 flags + inbox + Flag_Admin_Surface
   ├────────────────▼
   │            Phase 3  (10)              operational infra, non-flagged
   ▼
Phase 4  (11 → 12)                         V49, Room 63, notifications
   ▼
Phase 5  (13)                              V50, FSSAI  [Pay Now blocked until Phase 8]
   ▼
Phase 6  (14)                              V51, marketplace
   ▼
Phase 7  (15 → 16 → 17)                    V52, onboarding + KYC
   ▼
Phase 8  (18 → 19, 20)                     Easebuzz payments, no migration
   ▼
Phase 9  (21)                              invoice dual renderer
   ▼
Phase 10 (22)                              pilot → GA
   ▼
Phase 11 (23)                              deferred scope confirmation
```

```json
{
  "waves": [
    { "wave": 1,  "tasks": ["1.1", "1.2", "1.3"] },
    { "wave": 2,  "tasks": ["1.4", "2.1", "2.2", "2.3"] },
    { "wave": 3,  "tasks": ["2.4"] },
    { "wave": 4,  "tasks": ["2.5", "3.1"] },
    { "wave": 5,  "tasks": ["3.2"] },
    { "wave": 6,  "tasks": ["3.3", "4.1", "4.2", "4.3", "4.4", "4.6", "4.7"] },
    { "wave": 7,  "tasks": ["4.5"] },
    { "wave": 8,  "tasks": ["5.1"] },
    { "wave": 9,  "tasks": ["5.2", "5.3", "5.4", "6.1"] },
    { "wave": 10, "tasks": ["6.2", "6.3"] },
    { "wave": 11, "tasks": ["6.4", "6.5", "6.6", "8.1"] },
    { "wave": 12, "tasks": ["6.7", "7.1", "8.2"] },
    { "wave": 13, "tasks": ["7.2", "7.3", "8.3", "8.4"] },
    { "wave": 14, "tasks": ["8.5"] },
    { "wave": 15, "tasks": ["8.6", "8.7", "8.8"] },
    { "wave": 16, "tasks": ["8.9"] },
    { "wave": 17, "tasks": ["8.10", "8.11"] },
    { "wave": 18, "tasks": ["8.12", "8.13", "9.1", "9.2", "9.3"] },
    { "wave": 19, "tasks": ["10.1", "10.2", "10.3", "10.4", "11.1", "11.2"] },
    { "wave": 20, "tasks": ["10.5", "11.3", "11.5", "12.1"] },
    { "wave": 21, "tasks": ["11.4", "11.6", "11.7", "12.2"] },
    { "wave": 22, "tasks": ["12.3", "12.4", "12.5"] },
    { "wave": 23, "tasks": ["12.6", "12.7"] },
    { "wave": 24, "tasks": ["13.1"] },
    { "wave": 25, "tasks": ["13.2", "13.3", "13.4", "13.5"] },
    { "wave": 26, "tasks": ["13.6", "13.7"] },
    { "wave": 27, "tasks": ["14.1"] },
    { "wave": 28, "tasks": ["14.2", "14.3"] },
    { "wave": 29, "tasks": ["14.4", "14.5", "14.6"] },
    { "wave": 30, "tasks": ["14.7", "14.8", "14.9"] },
    { "wave": 31, "tasks": ["15.1", "15.2"] },
    { "wave": 32, "tasks": ["15.3", "15.6", "16.1"] },
    { "wave": 33, "tasks": ["15.4", "15.5", "15.7", "16.2", "16.3"] },
    { "wave": 34, "tasks": ["16.4", "16.5", "16.6", "17.1"] },
    { "wave": 35, "tasks": ["17.2", "17.3", "18.1"] },
    { "wave": 36, "tasks": ["18.2", "18.3", "18.4", "18.5"] },
    { "wave": 37, "tasks": ["18.6", "18.7"] },
    { "wave": 38, "tasks": ["18.8", "20.1"] },
    { "wave": 39, "tasks": ["19.1", "20.2", "20.7", "20.8"] },
    { "wave": 40, "tasks": ["19.2", "20.3", "20.4", "20.5", "20.6"] },
    { "wave": 41, "tasks": ["19.3", "19.4", "20.9", "20.10"] },
    { "wave": 42, "tasks": ["20.11"] },
    { "wave": 43, "tasks": ["21.1", "21.2", "21.3"] },
    { "wave": 44, "tasks": ["21.4"] },
    { "wave": 45, "tasks": ["22.1", "22.2", "22.3"] },
    { "wave": 46, "tasks": ["22.4"] },
    { "wave": 47, "tasks": ["22.5", "22.6", "22.7"] },
    { "wave": 48, "tasks": ["23.1", "23.2", "23.3"] },
    { "wave": 49, "tasks": ["23.4"] }
  ]
}
```

Hard ordering constraints:

- **2 blocks everything.** Requirement 30.3 puts flag state in PostgreSQL and 33.4 puts the inbox there; both land in V48, so no flagged phase can precede it. 8.5–8.11 also supply the claim, fencing, and eligibility machinery every later handler depends on.
- **3 is independent of 4–9** and may ship in parallel with Phase 4. It carries no flag (Requirement 22.6) and no migration.
- **13.6 depends on 19–20.** FSSAI `Pay Now` needs a live payment path, so Phase 5 ships `Remind Me` only and 20.10 enables the action.
- **15 blocks 18–20.** Requirement 20.18 requires onboarding to ship before collection.
- **18.7 blocks 19.3.** Recovery cannot work until all five pending-online-bill queries recognise the Easebuzz modes.
- **21.4 blocks 23.4.** The StringBuilder renderer is removed only after the differential test has run in production for the recorded observation period.

## Tasks

### Phase 0 — Baseline provenance

- [ ] 1. Establish the three Deployed_Module_Representations
- [x] 1.1 Record `Android_Baseline_Commit` from the Play artifact, its `versionCode`, and the release signing/build records; if unresolvable to a repo commit, materialise an artifact-derived snapshot of `Android/` and mark provenance artifact-derived
  - _Requirements: 1.1, 1.2, 1.3_
- [ ] 1.2 Record `Server_Baseline_Commit` from authenticated `/api/v1/actuator/info` `git.commit.id`
  - _Requirements: 1.1, 1.2_
- [x] 1.3 Record `WebAdmin_Baseline_Commit` from the production docroot bundle manifest and the `deploy-web.sh` run that placed it
  - _Requirements: 1.1, 1.2_
- [x] 1.4 List any path a snapshot cannot reproduce because the artifact carries only compiled output, and record it as residual provenance risk rather than as equivalent
  - _Requirements: 1.5_

- [ ] 2. Resolve the working tree and reconcile onto Baseline_Candidate
- [x] 2.1 Regenerate the working-tree inventory from `git status` and resolve every entry by explicit decision (commit / stash / discard), covering the protected-file edits to `TenantDaos.kt`, `BillDao.kt`, `RestaurantDao.kt`, `RestaurantRepository.kt`, `BillingViewModel.kt`, `NewBillScreen.kt`, `SettingsScreen.kt`
  - _Requirements: 1.6_
- [x] 2.2 Resolve the half-finished launcher-icon webp→png migration and the build-config drift in `build.gradle.kts`, `libs.versions.toml`, `gradle-wrapper.properties`
  - _Requirements: 1.6_
- [x] 2.3 Confirm the pending `Android/app/google-services.json` modification is intended client configuration and contains no server-side credential
  - _Requirements: 27.5, 27.6_
- [ ] 2.4 Diff each module's paths (`Android/`, `server/`, `web-admin/`) at Baseline_Candidate against its Deployed_Module_Representation; port any content present in production and absent at the candidate, advancing the candidate and re-running the diff until reconciled
  - _Requirements: 1.7, 1.8, 1.9, 1.10, 1.11_
- [x] 2.5 Record content present at the candidate but absent in production, and add it to that module's first-phase smoke checklist
  - _Requirements: 1.12_

- [ ] 3. Cut Baseline_Tag
- [x] 3.1 Run `mvn test`, `./gradlew.bat testDebugUnitTest`, `./gradlew.bat assembleDebug`, `npm run build` against the finalised candidate and resolve any failure before tagging
  - _Gate result 2026-08-05 on `v3`: all four pass. mvn test 187/0/0 (11 Testcontainers skipped); jqwik tries reduced 200/100 → 30/20. assembleDebug BUILD SUCCESSFUL. npm run build SUCCESSFUL. Runs against current v3 state; candidate still pending 1.2/2.4 provenance closure._
  - _Requirements: 1.13, 1.14_
- [ ] 3.2 Cut the immutable annotated `Baseline_Tag` and record the per-module reconciliation outcome
  - _Requirements: 1.15, 1.16, 1.18_
- [ ] 3.3 Create the integration branch from Baseline_Tag and add a CI check asserting no merge commit names a `v2` commit as a parent
  - _Requirements: 1.4_

### Phase 1 — Preservation harness

- [x] 4. Build the regression gate before any feature work
- [x] 4.1 Add a Room 62→62 no-op migration test seeded with unsynced bills, bill items, and payments, asserting every row survives
  - _Requirements: 3.6, 3.11_
- [x] 4.2 Add a legacy sync replay test that plays a captured Base_Branch request set and asserts response schema conformance
  - _Requirements: 14.1, 14.8_
- [x] 4.3 Add a test asserting `RequireRoleAspect` is active in the Spring application context
  - _Requirements: 5.7_
- [x] 4.4 Add a test asserting the Room migration lists in `DatabaseProvider.buildDatabaseWithName` and `DatabaseModule.buildDatabase` are identical
  - _Requirements: 3.3_
- [x] 4.5 Add or confirm at least one passing test for each preservation requirement 4 through 12, and record the result set as the Phase 1 baseline
  - _Requirements: 29.1, 29.2_
- [x] 4.6 Add CI greps failing the build on `DbCheck`, `QuickDbCheck`, `dev-debug`, `dev-refresh`, or any `Storefront`-named source file
  - _Requirements: 16.2, 16.3, 26.3_
- [x] 4.7 Add a CI check failing on a newly tracked credential-filename pattern
  - _Requirements: 27.4_

### Phase 2 — Integration foundation (V48)

- [x] 5. Author V48 migration
- [x] 5.1 Write `V46__feature_flags_and_webhook_inbox.sql` creating `feature_flag` (`kill_switched` default TRUE, `default_enabled` default FALSE), `feature_flag_override`, `feature_flag_audit`, and `webhook_inbox` including `claim_token`, `claimed_by`, `claimed_at`, `lease_expires_at`, `next_attempt_at`; additive operations only
  - _Requirements: 2.3, 2.7, 2.8, 30.3, 30.4, 33.4_
- [x] 5.2 Seed one `feature_flag` row per key with both columns at their safe defaults; add a comment recording why the orphan V6/V7/V8 tables are left untouched
  - _Requirements: 30.9, 2.6, 2.8_
- [x] 5.3 Add the `PostgresMigrationSmokeTest` case applying V1–V48 to an empty Testcontainers database, and a test asserting the Baseline_Tag server image starts against a V48 schema
  - _Requirements: 2.10, 2.14, 2.15_
  - _Note: V1–V48 chain proven empirically against throwaway PostgreSQL 18 (35 SQL rows, head = 48) in place of Testcontainers; test authored and compiling, skipped locally (no Docker)._
- [x] 5.4 Add the Flyway_History checksum reconciliation script
  - _Requirements: 2.13_
  - _Note: `ops/flyway_reconcile_checksums.sh` validated end-to-end against live history — 35/35 reconcile (exit 0). Checksum mirrors Flyway 11.7.2 ChecksumCalculator (CRC-32 over readLine outputs, BOM-only first-line strip, signed int). psql CRLF + Windows python stub handled._

- [ ] 6. Implement FeatureFlagService
- [x] 6.1 Add `FeatureFlag`, `FeatureFlagOverride`, `FeatureFlagAudit` entities and repositories
  - _Requirements: 30.3, 30.5_
- [x] 6.2 Implement `resolve` in the five-step order: row absent → config guard → `kill_switched` → override → `default_enabled`
  - _Requirements: 30.6, 30.7, 30.8, 30.10, 30.11_
  - _Note: implemented in `FeatureFlagServiceImpl.computeEnabled`; absent rows are never cached, so an externally-created row is honoured without a restart._
- [x] 6.3 Implement `FeatureConfigGuard` per flag and wire it as resolution step 2 so absent configuration overrides persisted state
  - _Requirements: 27.7, 30.10_
  - _Note: `easebuzz_payments` requires EASEBUZZ_MERCHANT_KEY+EASEBUZZ_SALT; `notifications` requires a Firebase credential (FIREBASE_CREDENTIALS_PATH or FIREBASE_REFRESH_TOKEN); `marketplace_orders` constant true (per-restaurant keys)._
- [x] 6.4 Add the Caffeine cache with `expireAfterWrite` bound to `propagation-deadline-seconds`, plus eager invalidation on write
  - _Requirements: 30.13, 30.15_
  - _Note: instance-owned Caffeine (design D4), key `flagKey:restaurantId`, max 10k entries; global writes invalidate every cached value for the flag, restaurant writes invalidate that restaurant._
- [x] 6.5 Implement `isProviderProcessable` as the global tier: row present, not kill-switched, config guard satisfied — with no restaurant resolution
  - _Requirements: 33.35_
- [x] 6.6 Write the audit row on every mutation, capturing flag, scope, previous state, new state, actor, timestamp
  - _Requirements: 30.20_
  - _Note: actor from TenantContext (userId + role); audit persistence is best-effort and never rolls back the flag transition._
- [x] 6.7 Add tests for the six operational states including single-restaurant pilot, kill-switch dominance, and config-forces-disabled
  - _Requirements: 30.28, 30.29, 30.30_
  - _Note: 11 tests, all green (full suite 236 pass); config-forces-disabled ordered last to avoid cache poisoning. Boot validated against real V48 PostgreSQL (entities map cleanly, health UP)._

- [ ] 7. Implement Flag_Admin_Surface
- [ ] 7.1 Add the six `/admin/feature-flags` endpoints, each annotated `@RequireRole(UserRole.KBOOK_ADMIN)`
  - _Requirements: 5.5, 30.6, 30.21_
- [ ] 7.2 Add the `admin/feature-flags` web-admin page showing effective state and change history, using `.page-shell`/`.panel`/`.data-table`
  - _Requirements: 15.7, 30.22_
- [ ] 7.3 Add `enabledFeatures` as an additive field on `MasterSyncResponseDTO`
  - _Requirements: 14.2, 30.23_

- [ ] 8. Implement the webhook inbox
- [ ] 8.1 Add the `WebhookInbox` entity, repository, and `WebhookInboxService.persist()`
  - _Requirements: 33.4_
- [ ] 8.2 Define `WebhookDescriptor` with `verify`, `providerEventId`, `eventClass`, `aggregateKey`, `orderingKey`, `resolveRestaurantId`
  - _Requirements: 33.2, 33.10, 33.12_
- [ ] 8.3 Implement `aggregateKey` per the Requirement 33.12 table, never the restaurant alone, with the `unresolved:` self-unique fallback
  - _Requirements: 33.12, 33.13, 33.14_
- [ ] 8.4 Implement `OrderingKey` normalisation across sequence, event timestamp, and receipt, recording `ordering_source`
  - _Requirements: 33.10, 33.11, 33.18_
- [ ] 8.5 Implement the claim query using `ORDER BY ordering_key ASC LIMIT 1 FOR UPDATE SKIP LOCKED`, minting a `claim_token`
  - _Requirements: 33.15, 33.37_
- [ ] 8.6 Implement fenced terminal writes, fenced lease renewal on a heartbeat, and the bounded handler timeout with a startup assertion that `handler-timeout < lease`
  - _Requirements: 33.37, 33.9_
- [ ] 8.7 Implement failure handling with exponential jittered backoff via `next_attempt_at`, `NEEDS_REVIEW` past the attempt limit, and no failure ever returned to the provider
  - _Requirements: 33.24, 33.25_
- [ ] 8.8 Add `inboxTaskScheduler` and `inboxExecutor` as dedicated pools, leaving the three existing `@Scheduled` cleanup jobs on the default scheduler
  - _Requirements: 33.31, 33.36_
- [ ] 8.9 Implement `process(row, token)` setting `TenantContext` explicitly from `row.restaurantId` with `clear()` in `finally`, setting no role, and setting the `requestId` MDC key
  - _Requirements: 33.31, 7.4_
- [ ] 8.10 Implement tier-2 per-record eligibility that releases the claim without incrementing backoff when the row's restaurant is not enabled
  - _Requirements: 30.9, 33.34, 33.35_
- [ ] 8.11 Implement tier-3 pre-mutation recheck inside the handler transaction
  - _Requirements: 33.32, 33.33_
- [ ] 8.12 Add the `admin/webhook-inbox` page and the unprocessed/failed count endpoint
  - _Requirements: 33.25, 33.28_
- [ ] 8.13 Add tests: concurrent claim never double-claims; simulated crash leaves the row reclaimable; a fenced stale worker's terminal write is rejected; a failed head row blocks only its own aggregate; every handler completes with a null role
  - _Requirements: 33.37, 33.42, 33.17_

- [ ] 9. Foundation housekeeping
- [ ] 9.1 Fix the stale `expectedRoomVersion=58` log strings in `DatabaseProvider` to read from the actual schema version
  - _Requirements: 3.1_
- [ ] 9.2 Add every new configuration key to `.env.example` with placeholder values
  - _Requirements: 27.1, 27.2_
- [ ] 9.3 Create the endpoint register and the conflict register, and record V48's Flyway range
  - _Requirements: 29.3, 29.4, 29.5_

### Phase 3 — Operational infrastructure (non-flagged)

- [ ] 10. Caching, logging, MDC, typed exceptions
- [ ] 10.1 Add `CacheConfig` with a Caffeine `CacheManager` registering only `restaurantProfileReadOnly` at a 60-second TTL, used by web-admin read paths and explicitly not by sync, billing, or terminal paths
  - _Requirements: 22.1, 22.8_
- [ ] 10.2 Add `logback-spring.xml` with `logstash-logback-encoder`, keeping the `errorId` field greppable, and update `ops/PRODUCTION_STACK.md` in the same commit
  - _Requirements: 22.2, 22.5, 22.9_
- [ ] 10.3 Extend `AsyncConfig`'s `TaskDecorator` to copy the `requestId` MDC key alongside `TenantContext`
  - _Requirements: 22.3_
- [ ] 10.4 Add `BusinessRuleException` → 400 and `EntityNotFoundException` → 404 to `GlobalExceptionHandler`
  - _Requirements: 22.4_
- [ ] 10.5 Assert no `Feature_Flag` is defined for anything in this phase and that every Base_Branch endpoint returns identical status and field set to Baseline_Tag
  - _Requirements: 22.6, 22.7, 22.10_

### Phase 4 — Push notifications (V49, Room 63)

- [ ] 11. Server notification stack
- [ ] 11.1 Author `V49` creating `device_token`, `notification_event`, `notification_template`, subsuming v2 V36 and V39 and recording that mapping
  - _Requirements: 2.3, 2.5, 2.6, 17.1_
- [ ] 11.2 Add `FirebaseConfig` reading the credential from an environment variable or mounted file, never from a source-tree path, with the OAuth2 refresh-token fallback path
  - _Requirements: 17.11, 17.12, 17.13_
- [ ] 11.3 Implement `PushNotificationService` with token-string upsert so repeat registration yields exactly one active row
  - _Requirements: 17.2, 17.3_
- [ ] 11.4 Add the `/api/v1/notifications` endpoints for device-token register/delete, list, unread-count, read, mark-all-read
  - _Requirements: 17.1_
- [ ] 11.5 Add a dedicated `notificationExecutor` separate from `inboxExecutor`, and dispatch fire-and-forget so a push never extends or fails the originating operation
  - _Requirements: 17.5, 17.6_
- [ ] 11.6 Mark a token inactive on permanent delivery failure and complete the business operation successfully
  - _Requirements: 17.4, 17.5_
- [ ] 11.7 Wire event triggers for login, new order, payment confirmation, order cancellation, KYC status change, payout completion
  - _Requirements: 17.4_

- [ ] 12. Android notification stack
- [ ] 12.1 Add `NotificationEntity`, bump `AppDatabase` to 63, write `MIGRATION_62_63` with `CREATE TABLE IF NOT EXISTS`, and register it in both `DatabaseProvider` and `DatabaseModule`
  - _Requirements: 3.2, 3.4, 3.9, 3.10_
- [ ] 12.2 Add `NotificationDao`, `TenantNotificationDao` following the `dao get()` / `runFlow {}` split, and the Hilt binding; filter every query by `restaurantId`
  - _Requirements: 7.4, 7.5_
- [ ] 12.3 Export the Room 63 schema JSON in the same commit and bump `versionCode` above 20
  - _Requirements: 3.4, 28.13_
- [ ] 12.4 Add `KhanaBookFirebaseMessagingService`, `NotificationHelper` with orders/payments/system/promotions channel groups, and `NotificationActionReceiver`
  - _Requirements: 17.7, 17.10_
- [ ] 12.5 Add `BootReceiver` with `RECEIVE_BOOT_COMPLETED` to re-register the token and reschedule workers
  - _Requirements: 17.9_
- [ ] 12.6 Add `NotificationsScreen` and `NotificationReliabilityScreen` using `KhanaBookTheme.spacing`/`iconSize` and `MaterialTheme.typography` only
  - _Requirements: 15.4, 15.5, 17.7_
- [ ] 12.7 Route notification deep links through the existing nav graph, and hide notification entry points when the flag resolves disabled or state retrieval fails
  - _Requirements: 17.8, 30.24, 30.25, 30.26_

### Phase 5 — FSSAI and GST compliance (V50)

- [ ] 13. Compliance tracking
- [ ] 13.1 Author `V50` creating `fssai_tracker`, `fssai_renewal`, and nullable compliance expiry columns on `restaurantprofiles`, subsuming v2 V28/V37/V38 with the mapping recorded
  - _Requirements: 2.3, 2.5, 2.6, 2.9, 18.1_
- [ ] 13.2 Add a unique constraint on `(tracker_id, alert_window)` so repeated schedule runs are idempotent by construction
  - _Requirements: 18.3_
- [ ] 13.3 Implement `FssaiTrackerService` and `ComplianceAlertService` on the default scheduler, requesting a push on entry into an alert window
  - _Requirements: 18.2_
- [ ] 13.4 Implement `GstFssaiLookupService` and the GST, FSSAI, and both lookup endpoints
  - _Requirements: 18.4_
- [ ] 13.5 Implement `Remind Me` deferral scheduling
  - _Requirements: 18.7_
- [ ] 13.6 Add `FssaiRenewalScreen` with v1 tokens, showing `Remind Me` only and omitting `Pay Now` while the `easebuzz_payments` flag is disabled
  - _Requirements: 15.4, 18.5, 18.6_
- [ ] 13.7 Add the `business/compliance` web-admin page showing FSSAI and GST expiry status
  - _Requirements: 18.9_

### Phase 6 — Marketplace orders (V51)

- [ ] 14. Marketplace ingestion and actions
- [ ] 14.1 Author `V51` creating `marketplace_order` and `marketplace_order_item` with a unique external-order-id per provider and restaurant, subsuming v2 V25/V27
  - _Requirements: 2.3, 2.5, 2.6, 19.3, 19.4_
- [ ] 14.2 Add Swiggy and Zomato `WebhookDescriptor` implementations with signature verification and the marketplace aggregate format
  - _Requirements: 19.5, 33.2, 33.12_
- [ ] 14.3 Implement `MarketplaceOrderService` with an idempotent inbox handler, retaining `MarketplaceConfigController` behaviour unchanged alongside it
  - _Requirements: 19.1, 19.3, 33.9_
- [ ] 14.4 Add the list, pending, counts, accept, reject, mark-ready, and complete endpoints, each `@RequireRole` annotated and tenant-scoped
  - _Requirements: 5.5, 19.2, 19.9_
- [ ] 14.5 Implement auto-enable on sync when provider keys are configured, idempotently, and make already-stored orders visible on enablement
  - _Requirements: 19.6, 19.7_
- [ ] 14.6 Implement item-availability propagation to configured marketplaces
  - _Requirements: 19.8_
- [ ] 14.7 Add `MarketplaceOrderRepository` as a direct REST client with no Room table, plus `MarketplaceOrdersScreen` with v1 tokens and an explicit offline state
  - _Requirements: 15.4, 19.10_
- [ ] 14.8 Confirm `OrdersScreen`, `ActiveOrdersScreen`, and all billing behaviour are unchanged, and that marketplace rows remain excluded from operational bill queries
  - _Requirements: 10.2, 19.10_
- [ ] 14.9 Add the `business/marketplace-orders` web-admin page
  - _Requirements: 15.7_

### Phase 7 — Easebuzz onboarding and KYC (V52)

- [ ] 15. Sub-merchant onboarding
- [ ] 15.1 Author `V52` creating `easebuzz_sub_merchant`, `easebuzz_post_split` with `UNIQUE (gateway_txn_id)`, and `easebuzz_payout` with `UNIQUE (payout_reference)`, subsuming v2 V22/V23/V24/V26/V34/V35 as one corrected migration and recording the mapping
  - _Requirements: 2.3, 2.5, 2.6, 20.9_
- [ ] 15.2 Add an entity for the existing orphan `restaurant_payment_config` rather than creating a new credential table
  - _Requirements: 2.8, 20.15_
- [ ] 15.3 Implement `SubMerchantService` and `EasebuzzApiClient`, persisting a pending record before submitting the onboarding payload
  - _Requirements: 20.2_
- [ ] 15.4 Permit resubmission from `FAILED`
  - _Requirements: 20.3_
- [ ] 15.5 Add the sub-merchant administration endpoints under `/api/v1/admin/sub-merchants`, `@RequireRole` annotated
  - _Requirements: 5.5, 20.1_
- [ ] 15.6 Add the `SUBMERCHANT_KYC` inbox descriptor and handler with a monotonic status transition set
  - _Requirements: 33.9, 33.12_
- [ ] 15.7 Map `EasebuzzApiException` to 502 and leave the local record retryable
  - _Requirements: 20.16, 20.17_

- [ ] 16. KYC document upload
- [ ] 16.1 Add the KYC upload endpoint validating content type and size before writing any bytes, reusing the existing `kbook.cdn.*` configuration
  - _Requirements: 24.1, 24.2, 24.3_
- [ ] 16.2 Restrict document read access to the owning restaurant and `KBOOK_ADMIN`
  - _Requirements: 24.4_
- [ ] 16.3 Capture business-proof documents and send FSSAI, legal entity name, and address for CPV compliance
  - _Requirements: 20.2_
- [ ] 16.4 Add `EasebuzzKycScreen` and `EasebuzzOnboardingScreen` with v1 tokens, using the Android 13+ `PickVisualMedia` picker
  - _Requirements: 15.4, 24.5_
- [ ] 16.5 Add the `business/settings` profile read/update endpoints and page as an onboarding prerequisite
  - _Requirements: 25.5_
- [ ] 16.6 Add the `admin/sub-merchants` web-admin page
  - _Requirements: 25.2_

- [ ] 17. Onboarding email
- [ ] 17.1 Add `EmailNotificationService` with the `onboarding-welcome` template only, on a dedicated `emailExecutor`, never blocking the triggering request
  - _Requirements: 32.1, 32.2, 32.7_
- [ ] 17.2 Log and continue when SMTP credentials are absent; send nothing when the triggering feature's flag is disabled
  - _Requirements: 32.5, 32.6_
- [ ] 17.3 Confirm `settlement-notification` and `chargeback-alert` templates are excluded
  - _Requirements: 32.4_

### Phase 8 — Easebuzz payments

- [ ] 18. Android payment mode expansion
- [ ] 18.1 Add `EASEBUZZ`, `PART_CASH_EASEBUZZ`, `PART_EASEBUZZ_POS` to `PaymentMode`, retaining every existing value
  - _Requirements: 14.6, 20.21_
- [ ] 18.2 Extend all four `PaymentModeManager` functions: `getEnabledModes` taking `easebuzzEnabled` as a parameter, `isPartPayment`, `getPartLabels`, `getPaymentComponents`
  - _Requirements: 20.20, 20.21_
- [ ] 18.3 Add `"easebuzz"` to `PaymentSetValidator.supportedModes` as an addition, leaving all other validator logic untouched
  - _Requirements: 10.1, 20.21_
- [ ] 18.4 Add the `part_cash_easebuzz` and `part_easebuzz_pos` arms to `BillDao.finalizeOnlineBillAtomically`'s mode-set derivation
  - _Requirements: 20.21_
- [ ] 18.5 Add `EASEBUZZ` to `BillingViewModel.recoverPartialDraftPayment`'s permitted mode set and to `PaymentGatewayHelper`'s UPI-family predicate
  - _Requirements: 20.21_
- [ ] 18.6 Connect `buildPaymentEntities` to the existing `_gatewayTxnId`/`_gatewayStatus` flows, setting `verifiedBy = "easebuzz"` for gateway components
  - _Requirements: 20.14, 20.21_
- [ ] 18.7 Add the three Easebuzz modes to every pending-online-bill query: both `getLatestPendingOnlineBill` overloads, `getPendingOnlineBillsFlow`, `getRestorablePendingOnlineBillWithItems`, `cancelStalePendingOnlineDrafts`
  - _Requirements: 20.14, 20.15_
- [ ] 18.8 Add a regression test asserting each of those five queries recognises all three Easebuzz modes
  - _Requirements: 20.15_

- [ ] 19. SDK checkout and recovery
- [ ] 19.1 Add the Easebuzz Android SDK dependency and `EasebuzzPaymentScreen` with v1 tokens
  - _Requirements: 15.4, 20.14_
- [ ] 19.2 Launch the SDK from `PaymentStep` when the selected mode includes an Easebuzz component, reusing `createDraftOnlineBill()` so bill id and operation id are persisted before launch, and reusing `paymentFlowLocked`
  - _Requirements: 20.14, 20.22_
- [ ] 19.3 Add `EasebuzzPaymentRecoveryWorker` reconciling against the server on next app start
  - _Requirements: 20.15_
- [ ] 19.4 Omit Easebuzz from selectable modes when the flag is disabled, leaving Cash, manual UPI, and POS behaviour unchanged
  - _Requirements: 20.19, 20.20, 10.5_

- [ ] 20. Server payment, split, refund
- [ ] 20.1 Implement `EasebuzzPaymentService` and the `/api/v1/payments/easebuzz` endpoints
  - _Requirements: 20.1_
- [ ] 20.2 Add the payment, refund, and payout `WebhookDescriptor` implementations with HMAC verification and their aggregate formats
  - _Requirements: 20.5, 20.6, 33.2, 33.12_
- [ ] 20.3 Implement the `PAYMENT` inbox handler matching `bill_payments` by `gateway_txn_id` so repeat processing updates rather than duplicates
  - _Requirements: 20.7, 33.9_
- [ ] 20.4 Implement `PostSplitService` apportioning shares to sum exactly to the settled amount, relying on `uq_easebuzz_post_split_txn` for idempotency
  - _Requirements: 20.8, 20.9_
- [ ] 20.5 Implement `RefundService` recomputing the refund sum inside the inserting transaction to cap against the gateway-paid amount
  - _Requirements: 20.10, 20.11_
- [ ] 20.6 Implement the `PAYOUT` handler persisting `EasebuzzPayout` linked to its sub-merchant
  - _Requirements: 20.12_
- [ ] 20.7 Route collections to the parent merchant while the sub-merchant state is not `ACTIVE`
  - _Requirements: 20.4_
- [ ] 20.8 Add `application-sandbox.properties`, keep live credentials out of version control, and default the `easebuzz_payments` flag to disabled pending sandbox validation
  - _Requirements: 20.18, 27.1, 27.3_
- [ ] 20.9 Add the `refund-confirmation` email template
  - _Requirements: 32.3_
- [ ] 20.10 Enable FSSAI `Pay Now` now that a payment path exists, and update the tracker on renewal settlement
  - _Requirements: 18.5, 18.8_
- [ ] 20.11 Add tests for split conservation, refund cap, inbox drain equivalence, and repeat `post_split` returning the existing row
  - _Requirements: 20.8, 20.9, 20.10, 20.11, 33.39_

### Phase 9 — Invoice template migration

- [ ] 21. Dual-renderer invoice
- [ ] 21.1 Add the Thymeleaf dependency and `invoice.html`, retaining the StringBuilder renderer alongside it and selecting by configuration
  - _Requirements: 31.1, 31.3_
- [ ] 21.2 Default the renderer selection to StringBuilder
  - _Requirements: 31.4_
- [ ] 21.3 Fall back to StringBuilder and record the failure when template rendering raises
  - _Requirements: 31.5_
- [ ] 21.4 Add a differential test asserting field equality across a generated bill set for invoice number, line items, subtotal, CGST, SGST, total tax, total, and footer
  - _Requirements: 31.2, 31.7_

### Phase 10 — Pilot rollout

- [ ] 22. Staged release
- [ ] 22.1 Deploy each phase with its flag kill-switched, verify `/api/v1/actuator/health` returns UP, and complete the Database_Backup_Gate before any migration
  - _Requirements: 28.3, 28.4, 28.5, 28.6_
- [ ] 22.2 Replay legacy sync requests against production and confirm schema conformance
  - _Requirements: 14.1, 14.8_
- [ ] 22.3 Release Android to the internal test track and run the Room 62→63 upgrade test on physical devices
  - _Requirements: 3.6, 28.16_
- [ ] 22.4 Enable one pilot restaurant via override with `default_enabled` still false, and confirm its webhook backlog drains
  - _Requirements: 28.16, 33.34_
- [ ] 22.5 Expand by override while monitoring sync quarantine, payment reconciliation, notification delivery, and inbox `NEEDS_REVIEW` counts
  - _Requirements: 28.16, 33.28_
- [ ] 22.6 Document the per-phase rollback sequence — kill switch, then previous image, then docroot — and the post-deployment smoke checklist
  - _Requirements: 28.7, 28.8, 28.11, 28.15_
- [ ] 22.7 Record the Preservation_Test_Suite result set, endpoint register, conflict register, and Flyway/Room ranges per phase
  - _Requirements: 29.1, 29.3, 29.4, 29.5_

### Phase 11 — Follow-up specifications

- [ ] 23. Deferred scope
- [ ] 23.1 Confirm WIRE remains excluded with no schema, endpoints, or client, and record it as a follow-up specification
  - _Requirements: 21.1, 21.2, 21.3_
- [ ] 23.2 Confirm refresh-token rotation remains excluded and the 30-day JWT is retained, recording the fleet re-evaluation note
  - _Requirements: 23.1, 23.2, 23.3, 23.4_
- [ ] 23.3 Confirm the 17 deferred fintech admin pages have no route, controller, service, entity, or migration
  - _Requirements: 25.1, 25.3, 25.4_
- [ ] 23.4 Remove the StringBuilder renderer after the recorded observation period
  - _Requirements: 31.6_

## Notes

### Requirements satisfied by process rather than code

Three requirements have no component and would vanish without explicit task coverage. Each is covered above and each produces a CI artifact:

| Requirement | Task | Artifact |
|---|---|---|
| 1.4 — no `Source_Branch` merge parent | 3.3 | CI check over `git log --merges` |
| 16.2, 16.3 — debris exclusion | 4.6 | CI greps on `DbCheck`, `QuickDbCheck`, `dev-debug`, `dev-refresh` |
| 26.3 — storefront exclusion | 4.6 | CI grep on `Storefront`-named sources |
| 27.4 — credential filename patterns | 4.7 | CI check on newly tracked files |

### Requirements with no task by intent

| Requirement | Reason | Confirmed by |
|---|---|---|
| 21 — WIRE platform | `DEFERRED` to a follow-up spec | 23.1 |
| 23 — refresh token rotation | `DEFERRED`; 30-day JWT retained | 23.2 |
| 25.1, 25.3, 25.4 — 17 fintech admin pages | `DEFERRED`; migrations excluded from V48–V52 | 23.3 |
| 26.1, 26.2 — storefront customer orders | `DROP`; table retained under Requirement 2.8 | 4.6 |

### Preservation requirements 4 through 12

These generate no feature tasks because their design disposition is "no change". They are enforced by task 4.5, which requires at least one passing test per requirement recorded as the Phase 1 baseline, and by the Per-Phase Gate re-running the full Preservation_Test_Suite at every phase boundary. A phase that breaks terminal isolation, KOT delta printing, tenant DB isolation, or sync hardening fails its own gate.

### The three sharpest edges

1. **Task 18.7** — five separate `BillDao` queries filter `payment_mode IN ('upi','part_cash_upi','part_upi_pos')`. Missing any one leaves an interrupted Easebuzz payment unrecoverable, with no compile error. 18.8 is the dedicated guard.
2. **Task 8.6** — the startup assertion `handler-timeout < lease` is what keeps the effect-once guarantee true. A configuration that inverts it silently degrades the inbox to unfenced concurrency.
3. **Task 8.10** — releasing a tier-2 ineligible claim *without* incrementing backoff. Incrementing would march a not-yet-enabled restaurant's backlog toward `NEEDS_REVIEW` while it waits to be switched on.

### Migration and Room ranges per phase

| Phase | Flyway | Room | Android `versionCode` |
|---|---|---|---|
| 0, 1 | none | none | unchanged |
| 2 | V48 | none | unchanged |
| 3 | none | none | unchanged |
| 4 | V49 | 62→63 | bump above 20 |
| 5 | V50 | none | unchanged |
| 6 | V51 | none | unchanged |
| 7 | V52 | none | unchanged |
| 8 | none | none | bump |
| 9, 10, 11 | none | none | as needed |

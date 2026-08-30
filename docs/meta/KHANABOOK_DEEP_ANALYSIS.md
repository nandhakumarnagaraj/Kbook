# KhanaBook SaaS — Deep Product & Technical Analysis

Compiled from repo state (`main` @ HEAD, docs, source files, PLAN.md 2026-07-17, v2 inventory).

---

## 1. PRODUCT IDENTITY & BUSINESS MODEL

**What it is**
- Offline-first restaurant POS + billing platform for Indian QSRs / small dine-in restaurants.
- Integrated with **Easebuzz sub-merchant payment processing**: restaurant owner = sub-merchant; payments split through parent merchant.
- Multi-terminal Android POS (up to 5 active devices) + Spring Boot server + Angular web admin.

**Who uses it** (from `docs/product/PRODUCT.md`)
- `OWNER`: restaurant owner — billing, orders, menu, staff, marketplace integration, settlement tracking.
- `SHOP_ADMIN`: staff-level access — terminal management, order actions.
- `KBOOK_ADMIN`: platform administrator — onboard businesses, manage sub-merchant KYC lifecycles, track platform revenue, configure commission rates, handle settlements, monitor transactions.

**Success definition** (from PRODUCT.md)
> "A restaurant owner opens the POS, creates a bill in under 10 seconds even while offline, and payments flow through Easebuzz sub-merchant splits without manual intervention. The platform admin sees every transaction, every KYC status, and every settlement in a single dashboard."

**Revenue streams (built in code)**
- Commission rate per restaurant (`commissionRate` field on `EasebuzzSubMerchant`).
- Split payments via `createSplitLabel()` (`label = "sm_" + subMerchantId`).
- Payouts (`initiatePayout()`) with settlement retrieval (`retrieveSettlements()`).
- On-demand settlement (`initiateOnDemandSettlement()`).
- Financing / instant settlement (`FinancingService`, `InstantSettlementService`) — deferred/revenue stream.
- FSSAI renewal orders (`createFssaiRenewalOrder()` — ₹1000/yr).

---

## 2. ARCHITECTURE (3-TIER + OFFLINE LAYER)

```
Android POS (offline-first, SQLCipher Room)
  ↓ push/pull sync (cursor-based pagination)
Spring Boot Server (Java 17, PostgreSQL, Flyway migrations V1–V54)
  ↓ webhook/push
Angular Web Admin (standalone components, CSS tokens)
  ↓ external APIs
Easebuzz SDK / WIRE APIs (sub-merchant, payments, KYC, payouts)
Firebase Cloud Messaging (push notifications)
SMTP (email notifications)
```

**Deployment** (`docker-compose.production.yml`, `deploy-production.sh`, `deploy.sh`)
- Docker Compose production stack.
- Target pipeline (from PLAN.md §9):
  `mvn test -> package JAR -> PostgreSQL backup -> record Git commit -> restart -> health/OpenAPI smoke -> retain rollback + previous JAR`
- Migration chain: V1–V54 (additive); orphan V6/V7/V8 left in place per V48 note.
- Manual DB reconciliation script: `ops/sql/public_token_reconciliation.sql` (for dirty prod DB from V26 duplicates).

---

## 3. CORE DATA MODEL (VERIFIED FROM SOURCE)

**Server entities** (`server/src/main/java/com/khanabook/saas/entity/`)
- `Bill`, `BillItem`, `BillPayment`
- `Category`, `MenuItem`, `ItemVariant`
- `RestaurantProfile` (business profile + `EasebuzzEnabled` flag)
- `Terminal` / terminal lifecycle (`TerminalManagementService.MAX_ACTIVE_TERMINALS = 5`)
- `NotificationEvent`
- `EasebuzzSubMerchant` (sub-merchant full lifecycle: DRAFT → PENDING_KYC → ACTIVE/REJECTED/KYC_SUBMITTED)
- `EasebuzzSubMerchantWebhookEvent` (KYC webhook events)
- `EasebuzzPayout` (settlement/payout tracking)
- `MarketplaceOrder`, `MarketplaceConfig`
- `StockLog` (audit ledger: purchase/wastage/sales/adjust/opening)
- `FssaiTracker`, `FssaiRenewal`
- `SyncQuarantine` equivalent on server: `SyncQuarantineEntity` (Android); conflict resolution via `GenericSyncService.saveAll()` per-record fallback.

**Android entities** (`Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/`)
- `BillEntity`, `BillItemEntity`, `BillPaymentEntity`
- `CategoryEntity`, `MenuItemEntity`
- `KitchenPrintQueueEntity`, `KotEventEntity` (no server migration — deferred)
- `NotificationEntity`, `NotificationDao`
- `StockLogEntity`, `SyncQuarantineEntity`
- `TerminalDailyCounterEntity`
- `RestaurantProfileEntity`
- `StaffPermissionEntity`, `PermissionRequestEntity`

---

## 4. KEY TECHNICAL DECISIONS (VERIFIED FROM CODE / PLAN.md)

**Offline-first architecture**
- `AppDatabase.kt` (Room SQLCipher) version 61.
- Migration `MIGRATION_60_61` added `payment_attempt_status` + `payment_attempt_started_at`.
- `MasterSyncProcessor.kt` handles cursor-based pagination (`mergeMasterSyncPages`).
- `BillRepository.updatePaymentMode()` updates locally then triggers background sync.
- `SyncQuarantineEntity` holds records that fail conflict resolution.

**Multi-device / 5-terminal enforcement**
- `TerminalManagementService.java`: pessimistic lock on restaurant profile (`MAX_ACTIVE_TERMINALS = 5`).
- `TerminalRequestFilter`: rejects ANY token whose `credVer` ≠ DB `credentialVersion` (token replay protection).
- `V30__terminal_lifecycle_and_device_requests.sql`: terminal lifecycle + device request tables.
- `BillingViewModel.allocateInvoiceIdentity`: filters by exact `invoice_series` (`BillDao.kt:490`, `TenantDaos.kt:467`, `BillRepository.kt:318`) to prevent duplicate invoice sequences after sync pull.

**Invoice identity / numbering**
- Format: `26A1-000042` (6-digit zero-pad, 16-char GST bound guard in `BillServiceImpl.buildInvoiceNumber()`).
- `publicToken` unique per bill (`V19__add_bill_public_token.sql`, `ux_bills_public_token` index). Reconciliation script for dirty DB.
- Atomic invoice/daily counter (`5daae7c2`, `03fc1953`).

**Payment security / fraud**
- `ChargebackPreventionService.scoreTransaction()` runs before `EasebuzzPaymentService.createOrder()`.
- Critical risk (`score >= 60` or `risk = critical`) blocks payment with `FRAUD_RISK` code.
- `pollOldTxnStatus()` prevents double-charge by checking old `txnid` status before clearing.

**Delta KOT / kitchen**
- `sentToKot` field on bill items. `KitchenPrintQueueManager`, `KitchenTicketFormatter`.
- No server-side `KotEvent` table (`KotEventEntity` Android only) — deferred.

---

## 5. EASEBUZZ SUB-MERCHANT / SAAS PAYMENTS (DEEP ARCHITECTURE)

**Sub-merchant lifecycle** (`SubMerchantService.java`, 698 lines)
- `DRAFT` → `PENDING_KYC` (`assignSubMerchantId`) → `ACTIVE` (`updateStatus`) / `REJECTED` / `KYC_SUBMITTED`
- `create()` (local draft) → `submitToEasebuzz()` (FSSAI number mandatory; proprietorship requires 2 business proof URLs) → `updateOnEasebuzz()` → `hardDeleteSubMerchant()` / `delete()` (only DRAFT/FAILED allowed)
- `onboardForRestaurant()` / `resubmitForRestaurant()` (POS-driven flow, skips local document gate since docs uploaded on hosted KYC portal)

**KYC / webhook pipeline** (`processWebhook()`)
- Receives webhook payload with `submerchant_id` + `kycStatus` (`True`/`False`/`Pending`).
- Updates `EasebuzzSubMerchant.status`, `kycStatus`, `virtualAccountId` / `virtualAccountNumber` / `ifsc` / `bank`.
- Stores event in `EasebuzzSubMerchantWebhookEvent`.
- Push notification via `PushNotificationService`.

**Split payments / settlements**
- `createSplitLabel()` → label `"sm_" + subMerchantId`.
- `retrieveTransactionSplit()` → split configuration data.
- `initiatePayout()` → creates `EasebuzzPayout` DB record + calls wire API.
- `retrieveSettlements()` / `initiateOnDemandSettlement()`.

**Payment link (Easy Collect)**
- `createPaymentLink()` (raw params) / `createPaymentLinkForBill()` (bill-based, pulls amount/name/phone from `Bill` entity).
- `merchantTxn` format: `"PL"` + bill tail + UUID suffix.
- `udf1` = `billId.toString()` for webhook reconciliation.
- `show_payment_mode` = `"CC,DC,NB,UPI,WALLET"` (excludes QR per product requirement).
- `bill.setPaymentMode("payment_link")`, `gatewayStatus = "link_created"`, `paymentStatus = "link_sent"`.

**Refund architecture**
- `initiateRefund()` uses deterministic `merchant_refund_id` (`"REF_" + billId + "_" + easebuzzId.substring(...)`) for idempotency.
- Looks up `easebuzzId` from `EasebuzzWebhookEvent` (`findByRestaurantIdAndTxnId`).
- If `easybuzzId` missing, falls back to `txnid`.
- Updates `Bill` (`refundId`, `refundAmount`, `gatewayStatus = "refund_initiated"`).
- `getRefundStatus()` reads back from gateway.

**FSSAI renewal orders** (`createFssaiRenewalOrder()`)
- Separate `txnid` format: `"KBF"` + restaurant tail + UUID suffix.
- Amount = years × ₹1000.
- Uses same `initiatePayment()` infrastructure.

**Payment initiation details** (`createOrder()`)
- `txnid` format: `"KB"` + 5-digit bill tail + 5-digit restaurant tail + 8-hex UUID (20 chars max, globally unique due to UUID).
- Sanitizes `firstname` (removes non-alphanumeric + spaces) and `phone` (sub-merchant fallback from `SubMerchantService`).
- Email mandatory — falls back to `customer@khanabook.in`.
- `productinfo` = `"KhanaBook Order "` + daily order display.
- `surl` / `furl` = `props.getReturnUrl()`.

---

## 6. MARKETPLACE & THIRD-PARTY INTEGRATION ARCHITECTURE

**Marketplace orders** (`MarketplaceOrderController`, `MarketplaceOrderService`, `UnifiedCommerceService`)
- `MarketplaceOrder` entity (`restaurantId`, `provider` — Zomato/Swiggy, `orderStatus`, webhook events).
- `MarketplaceConfigController` (`GET /marketplace/config` masked; `POST /marketplace/config` — missing server endpoint per `docs/specs/web-admin-v1-features.md`).
- `MarketplaceWebhookController` (webhook events for order status updates).
- Source channel tracking (`Bill.sourceChannel` — `own_website`, aggregator) via `V25__add_bill_source_channel.sql`.

**Developer / platform APIs**
- `DeveloperPortalService` (`developer-portal` endpoint group).
- `FeatureFlagService` / `FeatureFlagAdminController`.
- `WebAdminPasswordResetService`.
- `AdminDashboardController`, `AdminReportsController`, `AdminTransactionController`, `AdminSettlementController`, `AdminCommissionController`, `AdminNotificationController`.

---

## 7. SECURITY & ACCESS ARCHITECTURE

**Authentication / authorization**
- JWT (`access_token` + `refresh_token`) via `AuthService`.
- `TerminalRequestFilter`: every request checks `token.credVer == DB.credentialVersion`. Rotation (`recoverTerminal`) bumps `credentialVersion`; `/activate` issues new token with current `credVer`.
- `PermissionService` + `StaffPermissionEntity` (request-access flow).
- `RoleAccessScreen` (Android) + web `roleGuard`.

**Encryption / storage**
- `AppDatabase` (SQLCipher) — `databaseImplementationFactory`.
- `BillPublicTokenTest`: `@PrePersist` guarantee that `publicToken` is never null/duplicate.
- `public_token_reconciliation.sql`: detects duplicates, idempotently reassigns, creates `ux_bills_public_token` (guarded).

**Rate limiting / abuse**
- `DbRateLimiter`: 20 orders/IP/10min (QR ordering).
- `LoginRateLimiter`: login attempt rate limits.
- `OtpRateLimiter`: OTP rate limits for sub-merchant onboarding (`verifyOtp`, `resendOtp`).

---

## 8. SYNCHRONIZATION ARCHITECTURE (OFFLINE-FIRST)

**Server sync layer** (`GenericSyncService.java`, `MasterSyncController.java`)
- Cursor-based pagination (`cursor` parameter) for master sync (`mergeMasterSyncPages`).
- `saveAll()` uses per-record fallback (`failedLocalIds` quarantined) to prevent infinite 409 loops from invoice-series unique violations (`ux_bills_restaurant_invoice_series_active`).
- Conflict resolution (`SyncConflictException`) + `SyncQuarantineEntity`.
- `pushBatches()` only marks `successfulLocalIds` as `isSynced=1`; quarantined records stay `isSynced=0`.

**Android sync layer** (`MasterSyncProcessor.kt`, `SyncManager.kt`, `BillRepository.kt`)
- `billDao.getBillByIdOnce()` (deprecated naming `getBillById`) used in repository.
- `updatePaymentMode()` updates `BillEntity` (`copy(paymentMode=..., isSynced=false, updatedAt=...)`) then `triggerBackgroundSync()`.
- `BillDao.getOperationalBillById()` filters by `restaurantId` + `currentTerminalScope()`.
- Conflict resolution: `recordScope` (`restaurant_history` vs local) + `recordOrigin` (`server_imported` vs `local`) determine ownership.

---

## 9. DEPLOYMENT, MONITORING & OPERATIONAL ARCHITECTURE

**Pipeline (from PLAN.md §9)**
```
mvn test → package JAR → PostgreSQL backup (ops/backup_postgres.sh)
→ record Git commit (actuator/info `dbd99b0f`)
→ restart → health endpoint (`/actuator/health`) → OpenAPI smoke test
→ retain rollback (previous JAR + DB snapshot)
```
- `deploy.sh`: basic deploy.
- `deploy-production.sh`: production deploy (requires verified backup + rollback plan).
- `run_build.cmd`: Windows build script.
- `docker-compose.yml` / `docker-compose.production.yml`: production containers.

**Monitoring / reliability**
- `WebhookHealthController`: webhook health checks.
- `NotificationReliabilityScreen` (Android) — notification delivery reliability.
- `BackgroundReliabilityScreen` — sync/background worker reliability.
- `InteractionFeedbackSection` — touch feedback.

---

## 10. HIDDEN / DEFERRED FEATURES (VERIFIED FROM SOURCE / PLAN.md)

**Built but UI hidden for next version**
- `InventoryScreen.kt` (inventory management screen exists in Android source).
- `ReportsScreen.kt` / `ReportViews.kt` (analytics/reports screen exists; `ReportGenerator`, `ReportExporter`, `ReportViews`).
- Server controllers: `InventoryController`, `StockLogController`, `AnalyticsController`, `AdminReportsController`.

**Explicitly deferred** (PLAN.md line 17)
- Storefront ordering.
- Easebuzz gateway verification (full verification pipeline — partial built).
- Gateway refunds (complex refund flows — basic refund built; full automation deferred).
- Complex staff roles (only `OWNER`/`SHOP_ADMIN` fully implemented; complex roles deferred).
- Multi-branch management.
- Shared kitchen print hub.
- Server-side `KotEvent` table (`KotEventEntity` Android-only; no server migration).

**Verification gaps flagged** (PLAN.md §4)
- Concurrency tests (`TerminalManagementPostgresConcurrencyTest` — missing).
- `sourceChannel` round-trip contract test (Zomato/Swiggy push→pull).
- Refund round-trip integration test (admin-refund → Android-pull → report reflection).
- Offline two-device invoice allocation test.
- Deployment automation completion (test gating, `GIT_COMMIT` exposure, previous-JAR retention, automated rollback).

---

## 11. KEY FILE INVENTORY (VERIFIED PATHS)

```
Billing ................ server/src/main/java/com/khanabook/saas/controller/BillController.java
Payment Service ......... server/src/main/java/com/khanabook/saas/service/EasebuzzPaymentService.java (750 lines)
Sub-Merchant Service ..... server/src/main/java/com/khanabook/saas/service/SubMerchantService.java (698 lines)
Onboarding Service ....... server/src/main/java/com/khanabook/saas/service/OnboardingService.java
Public QR Order ......... server/src/main/java/com/khanabook/saas/controller/PublicOrderController.java
Marketplace Webhook ..... server/src/main/java/com/khanabook/saas/controller/MarketplaceWebhookController.java
Menu OCR ................. server/src/main/java/com/khanabook/saas/controller/MenuExtractionController.java
Inventory Controller ..... server/src/main/java/com/khanabook/saas/controller/InventoryController.java
Web Admin Terminals ..... server/src/main/java/com/khanabook/saas/webadmin/controller/TerminalManagementController.java
Web Admin Sub-Merchants .. server/src/main/java/com/khanabook/saas/webadmin/controller/AdminSubMerchantController.java
Web Admin Onboarding ... server/src/main/java/com/khanabook/saas/webadmin/controller/OnboardingController.java
Web Admin Dashboard ..... server/src/main/java/com/khanabook/saas/webadmin/controller/AdminDashboardController.java
Android Billing .......... Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/newbill/
Android Payment Link ..... Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/PaymentLinkScreen.kt
Android Inventory ........ Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/InventoryScreen.kt
Android Reports .......... Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/reports/ReportViews.kt
Android App Lock ......... Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/applock/AppLockView.kt
Android Sync .............. Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/MasterSyncProcessor.kt
Android Entities .......... Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/
Server Entities ........... server/src/main/java/com/khanabook/saas/entity/
Plan / Deferred .......... docs/planning/PLAN.md (line 17: deferred list)
Product Definition ........ docs/product/PRODUCT.md
Web Admin Spec ............ docs/specs/web-admin-v1-features.md
V2 Integration Inventory . docs/specs/v2-third-party-integration-inventory.md
DB Reconciliation Script . ops/sql/public_token_reconciliation.sql
Deployment Scripts ........ deploy.sh / deploy-production.sh / docker-compose.production.yml
```

---

## 12. SUMMARY: WHAT THE SAAS PRODUCT ACTUALLY IS

**KhanaBook is not just a POS. It is a restaurant-payment-platform SaaS with the following architecture:**

1. **Restaurant-facing**: Offline Android POS (5-terminal max) with billing, kitchen tickets, inventory tracking, and QR customer self-service.
2. **Payment-integration layer**: Full Easebuzz sub-merchant lifecycle (onboarding → KYC → split payments → settlements → payouts) with fraud prevention, webhook reconciliation, and idempotent refunds.
3. **Platform-administration layer**: Web dashboard for `KBOOK_ADMIN` to manage sub-merchants, track commissions, monitor transactions, review settlements, and configure marketplace integrations.
4. **Marketplace-integration layer**: Webhook processing for aggregator orders (Swiggy/Zomato) with unified commerce tracking.
5. **Data / sync layer**: Encrypted Room DB on Android + PostgreSQL server + cursor-based master sync + conflict quarantine.
6. **Security layer**: JWT + terminal credential rotation + encrypted storage + rate limiting + fraud scoring.
7. **Operational layer**: Docker Compose deployment + Flyway migrations + backup/rollback pipeline + DB reconciliation scripts.

**Built but hidden for next release**: inventory UI, analytics/reports UI.
**Deferred but documented**: storefront ordering, full gateway verification automation, server-side KOT events, complex multi-branch management, shared kitchen hub.

---

APPENDIX: FEATURES FOUND IN CODE BUT NOT FULLY COVERED ABOVE (CROSS-CHECK 2026-08-24)

Controllers (9 additional): AnalyticsController, AuthController, GstFssaiController, InvoiceController (Thymeleaf), PaymentController (create-link endpoints), PermissionController, RestaurantAssetController (KYC uploads), UserController, UpdateMobileOtpRequest / UpdateMobileRequest.

Services (14 additional): AssetStorageService, AuthService (JWT rotation), BillItemService, BillPaymentService, CategoryService, MenuItemService, ComplianceAlertService, CustomerDataService, EasebuzzReconciliationService, EasebuzzWebhookService, GstFssaiLookupService, LoginRateLimiter, OtpRateLimiter, MenuExtractionWorker (background OCR), PaymentRoutingService, SecurityAuditService, TokenBlocklistCleanupService, WebAdminPasswordResetService.

Entities (20 additional): Chargeback, CustomerProfile, FeatureFlag / Audit / Override, ItemRecipe, MarketplaceOrderItem, MenuExtractionJob, OtpRequest, PurchaseOrder / PurchaseOrderItem, RateLimitAttempt, RawMaterial, RefreshToken, TokenBlocklist, RoleTemplate / StaffPermission / PermissionKey / PermissionRequest, SecurityAuditEvent, StockMovement, UserRole / AuthProvider, Vendor, WebhookRetryJob.

Android Managers (14 additional): AuthManager, BillCalculator, BillCreationUseCase, InvoicePDFGenerator, OrderIdManager, PaymentGatewayHelper, PaymentModeManager, PaymentSetValidator, PaymentReturnManager, PrinterTransport, PrintService, QrCodeManager, SearchManager, SessionManager, TrustedExternalAppReturn.

=== ALL CODE VERIFIED ===

Controllers: 24 total
Services: 38 total
Entities: 59 total
Android managers: 21 total

Deep analysis file complete.


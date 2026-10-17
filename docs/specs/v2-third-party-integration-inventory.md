# v2 Third-Party Integration Inventory

Complete inventory of every third-party integration shipped on the `v2` branch, with the actual
code implementation files (server / Android / web-admin). This is the deletion safety net: before
`v2` is deleted from GitHub, every integration below must be present and proven in `v3`
(`docs/planning/post-all-phases-checklist.md` §1, §3).

Inventory taken from `origin/v2` (0df0098a) via worktree `C:\tmp\KhanaBook-v2-install`.

---

## 1. Easebuzz Payments (payment gateway collection)

**SDK/deps:** `in.easebuzz:android-v2:1.0.6` (Android), none server-side (REST client).

**Server files**
- `services/server/src/main/java/com/khanabook/saas/config/EasebuzzProperties.java`
- `services/server/src/main/java/com/khanabook/saas/service/EasebuzzApiClient.java`
- `services/server/src/main/java/com/khanabook/saas/service/EasebuzzPaymentService.java`
- `services/server/src/main/java/com/khanabook/saas/service/EasebuzzWebhookService.java`
- `services/server/src/main/java/com/khanabook/saas/service/EasebuzzWireApiClient.java`
- `services/server/src/main/java/com/khanabook/saas/service/PaymentRoutingService.java`
- `services/server/src/main/java/com/khanabook/saas/service/InstantSettlementService.java`
- `services/server/src/main/java/com/khanabook/saas/service/RefundService.java`
- `services/server/src/main/java/com/khanabook/saas/service/PostSplitService.java`
- `services/server/src/main/java/com/khanabook/saas/entity/EasebuzzPayout.java`
- `services/server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchant.java`
- `services/server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchantWebhookEvent.java`
- `services/server/src/main/java/com/khanabook/saas/entity/EasebuzzWebhookEvent.java`
- `services/server/src/main/java/com/khanabook/saas/repository/EasebuzzPayoutRepository.java`
- `services/server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantRepository.java`
- `services/server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantWebhookEventRepository.java`
- `services/server/src/main/java/com/khanabook/saas/repository/EasebuzzWebhookEventRepository.java`
- `services/server/src/main/java/com/khanabook/saas/exception/EasebuzzApiException.java`
- `services/server/src/main/java/com/khanabook/saas/controller/PaymentController.java`
- `services/server/src/main/java/com/khanabook/saas/controller/GstFssaiController.java` (renewal Pay Now path)
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/AdminSubMerchantController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/RestaurantPaymentConfigController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/AdminTransactionController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/service/PaymentMetricsService.java`
- `services/server/src/main/java/com/khanabook/saas/service/ChargebackPreventionService.java`

**Android files**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/remote/dto/EasebuzzPaymentDtos.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/repository/EasebuzzPaymentRepository.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/repository/EasebuzzSdkPaymentRepository.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzPaymentScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/worker/EasebuzzPaymentRecoveryWorker.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/PaymentConfigSection.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentModeManager.kt` (EASEBUZZ modes)
- `apps/android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentGatewayHelper.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/BillDao.kt` (pending-online-bill queries)
- `apps/android/app/src/main/java/com/khanabook/lite/pos/domain/model/Enums.kt` (PaymentMode values)
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt` (PaymentStep SDK launch)
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt`

**Web-admin files**
- `apps/web-admin/src/app/pages/transaction-monitor/transaction-monitor-page.component.ts`
- `apps/web-admin/src/app/pages/settlement-reports/settlement-reports-page.component.ts`
- `apps/web-admin/src/app/pages/sub-merchants/sub-merchants-page.component.ts`
- `apps/web-admin/src/app/pages/commission-config/commission-config-page.component.ts`
- `apps/web-admin/src/app/core/services/admin-api.service.ts`

**Migrations:** V6, V7 (orphan tables on v1; NOT adopted — see V48 comment)

---

## 2. Push Notifications (Firebase Cloud Messaging)

**Deps:** `com.google.firebase:firebase-admin` (server), `firebase-messaging:24.1.1` (Android).

**Server files**
- `services/server/src/main/java/com/khanabook/saas/config/FirebaseConfig.java`
- `services/server/src/main/java/com/khanabook/saas/service/PushNotificationService.java`
- `services/server/src/main/java/com/khanabook/saas/entity/NotificationEvent.java`
- `services/server/src/main/java/com/khanabook/saas/repository/NotificationEventRepository.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/NotificationController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/AdminNotificationController.java`

**Android files**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/worker/KhanaBookFirebaseMessagingService.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/worker/NotificationHelper.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/worker/NotificationActionReceiver.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/NotificationEntity.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/NotificationDao.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/repository/NotificationRepository.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NotificationsScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NotificationReliabilityScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/NotificationViewModel.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/designsystem/KhanaBookNotificationPanel.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/local/AppDatabase.kt` (Room 62→63)
- `apps/android/app/src/main/java/com/khanabook/lite/pos/di/DatabaseModule.kt`

**Migration:** V36 `device_token`/notification tables, V39 custom notification messages

---

## 3. FSSAI / GST Compliance

**Server files**
- `services/server/src/main/java/com/khanabook/saas/entity/FssaiTracker.java`
- `services/server/src/main/java/com/khanabook/saas/entity/FssaiRenewal.java`
- `services/server/src/main/java/com/khanabook/saas/repository/FssaiTrackerRepository.java`
- `services/server/src/main/java/com/khanabook/saas/repository/FssaiRenewalRepository.java`
- `services/server/src/main/java/com/khanabook/saas/service/FssaiTrackerService.java`
- `services/server/src/main/java/com/khanabook/saas/service/ComplianceAlertService.java`
- `services/server/src/main/java/com/khanabook/saas/service/GstFssaiLookupService.java`
- `services/server/src/main/java/com/khanabook/saas/service/TaxComplianceService.java`
- `services/server/src/main/java/com/khanabook/saas/controller/GstFssaiController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/TaxComplianceController.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/dto/AdminBusinessDetailResponse.java`

**Android files**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/FssaiRenewalScreen.kt`

**Migrations:** V28 (tracker), V37, V38 (renewals)

---

## 4. Marketplace Orders (Swiggy / Zomato)

**Server files**
- `services/server/src/main/java/com/khanabook/saas/entity/MarketplaceOrder.java`
- `services/server/src/main/java/com/khanabook/saas/entity/MarketplaceOrderItem.java`
- `services/server/src/main/java/com/khanabook/saas/repository/MarketplaceOrderRepository.java`
- `services/server/src/main/java/com/khanabook/saas/service/MarketplaceOrderService.java`
- `services/server/src/main/java/com/khanabook/saas/controller/MarketplaceOrderController.java`
- `services/server/src/main/java/com/khanabook/saas/controller/MarketplaceWebhookController.java`
- `services/server/src/main/java/com/khanabook/saas/controller/MarketplaceConfigController.java`
- `services/server/src/main/java/com/khanabook/saas/service/UnifiedCommerceService.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/dto/AdminMarketplaceWebhookEventResponse.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/dto/BusinessMarketplaceSetupResponse.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/dto/UpdateBusinessIntegrationsRequest.java`

**Android files**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/remote/dto/MarketplaceOrderDtos.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/data/repository/MarketplaceOrderRepository.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/MarketplaceOrdersScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/OrdersScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/HomeScreen.kt`

**Web-admin files**
- `apps/web-admin/src/app/pages/marketplace-setup/marketplace-setup-page.component.ts`
- `apps/web-admin/src/app/pages/orders/orders-page.component.ts`

**Migrations:** V25, V27 (marketplace integration fields), V8 (orphan storefront table)

---

## 5. Sub-Merchant Onboarding (Easebuzz KYC)

**Server files**
- `services/server/src/main/java/com/khanabook/saas/service/SubMerchantService.java`
- `services/server/src/main/java/com/khanabook/saas/service/OnboardingService.java`
- `services/server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchant.java`
- `services/server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantRepository.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/AdminSubMerchantController.java`
- `services/server/src/main/java/com/khanabook/saas/controller/RestaurantAssetController.java` (KYC docs)

**Android files**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzKycScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzOnboardingScreen.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/EasebuzzOnboardingViewModel.kt`

**Web-admin files**
- `apps/web-admin/src/app/pages/sub-merchants/sub-merchants-page.component.ts`

**Migrations:** V22, V23, V24, V26, V34, V35

---

## 6. Transactional Email

**Deps:** `spring-boot-starter-mail`

**Server files**
- `services/server/src/main/java/com/khanabook/saas/service/EmailNotificationService.java`

**Migrations:** none

---

## 7. Invoice Template (Thymeleaf)

**Deps:** `spring-boot-starter-thymeleaf`

**Server files**
- `services/server/src/main/java/com/khanabook/saas/controller/InvoiceController.java`
- Template resources under `services/server/src/main/resources/templates/` (invoice.html)

**Android files (invoice rendering parity)**
- `apps/android/app/src/main/java/com/khanabook/lite/pos/domain/manager/InvoicePDFGenerator.kt`
- `apps/android/app/src/main/java/com/khanabook/lite/pos/domain/util/InvoiceFormatter.kt`

---

## 8. Webhooks / Retry plumbing (shared infra)

- `services/server/src/main/java/com/khanabook/saas/config/WebhookRetryConfig.java`
- `services/server/src/main/java/com/khanabook/saas/entity/Chargeback.java`

---

## Cross-cutting platform services (not 3rd-party, but v2-only code)

- `services/server/src/main/java/com/khanabook/saas/service/DeveloperPortalService.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/service/BusinessReadService.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/service/AdminReadService.java`
- `services/server/src/main/java/com/khanabook/saas/webadmin/controller/BusinessAdminController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/dto/*` (business profile/staff responses)
- Android: `SettingsHomeSection.kt`, `SettingsSupportSections.kt`, `ShopConfigSection.kt`,
  `TaxConfigSection.kt`, `AppLockConfigSection.kt`, `PaymentConfigSection.kt`

---

## Config keys (`.env`/application props) required by these integrations

From `application-prod.properties`/`application-dev.properties` on v2 (verify exact names when porting):
Easebuzz merchant key/salt/encryption (`payment.crypto.secret`, EasebuzzProperties fields),
Firebase credential path/env var, SMTP host/user/pass, marketplace provider keys.

---

## Status

- [ ] Every file above re-checked against v3 after each phase (phase cross-check)
- [ ] No integration left only on v2 when `v2` is deleted from GitHub (final gate, checklist §3)

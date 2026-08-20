# v2 Third-Party Integration Inventory

Complete inventory of every third-party integration shipped on the `v2` branch, with the actual
code implementation files (server / Android / web-admin). This is the deletion safety net: before
`v2` is deleted from GitHub, every integration below must be present and proven in `v3`
(`docs/post-all-phases-checklist.md` §1, §3).

Inventory taken from `origin/v2` (0df0098a) via worktree `C:\tmp\KhanaBook-v2-install`.

---

## 1. Easebuzz Payments (payment gateway collection)

**SDK/deps:** `in.easebuzz:android-v2:1.0.6` (Android), none server-side (REST client).

**Server files**
- `server/src/main/java/com/khanabook/saas/config/EasebuzzProperties.java`
- `server/src/main/java/com/khanabook/saas/service/EasebuzzApiClient.java`
- `server/src/main/java/com/khanabook/saas/service/EasebuzzPaymentService.java`
- `server/src/main/java/com/khanabook/saas/service/EasebuzzWebhookService.java`
- `server/src/main/java/com/khanabook/saas/service/EasebuzzWireApiClient.java`
- `server/src/main/java/com/khanabook/saas/service/PaymentRoutingService.java`
- `server/src/main/java/com/khanabook/saas/service/InstantSettlementService.java`
- `server/src/main/java/com/khanabook/saas/service/RefundService.java`
- `server/src/main/java/com/khanabook/saas/service/PostSplitService.java`
- `server/src/main/java/com/khanabook/saas/entity/EasebuzzPayout.java`
- `server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchant.java`
- `server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchantWebhookEvent.java`
- `server/src/main/java/com/khanabook/saas/entity/EasebuzzWebhookEvent.java`
- `server/src/main/java/com/khanabook/saas/repository/EasebuzzPayoutRepository.java`
- `server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantRepository.java`
- `server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantWebhookEventRepository.java`
- `server/src/main/java/com/khanabook/saas/repository/EasebuzzWebhookEventRepository.java`
- `server/src/main/java/com/khanabook/saas/exception/EasebuzzApiException.java`
- `server/src/main/java/com/khanabook/saas/controller/PaymentController.java`
- `server/src/main/java/com/khanabook/saas/controller/GstFssaiController.java` (renewal Pay Now path)
- `server/src/main/java/com/khanabook/saas/webadmin/controller/AdminSubMerchantController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/RestaurantPaymentConfigController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/AdminTransactionController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/service/PaymentMetricsService.java`
- `server/src/main/java/com/khanabook/saas/service/ChargebackPreventionService.java`

**Android files**
- `Android/app/src/main/java/com/khanabook/lite/pos/data/remote/dto/EasebuzzPaymentDtos.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/repository/EasebuzzPaymentRepository.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/repository/EasebuzzSdkPaymentRepository.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzPaymentScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/worker/EasebuzzPaymentRecoveryWorker.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/PaymentConfigSection.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentModeManager.kt` (EASEBUZZ modes)
- `Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PaymentGatewayHelper.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/BillDao.kt` (pending-online-bill queries)
- `Android/app/src/main/java/com/khanabook/lite/pos/domain/model/Enums.kt` (PaymentMode values)
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt` (PaymentStep SDK launch)
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt`

**Web-admin files**
- `web-admin/src/app/pages/transaction-monitor/transaction-monitor-page.component.ts`
- `web-admin/src/app/pages/settlement-reports/settlement-reports-page.component.ts`
- `web-admin/src/app/pages/sub-merchants/sub-merchants-page.component.ts`
- `web-admin/src/app/pages/commission-config/commission-config-page.component.ts`
- `web-admin/src/app/core/services/admin-api.service.ts`

**Migrations:** V6, V7 (orphan tables on v1; NOT adopted — see V48 comment)

---

## 2. Push Notifications (Firebase Cloud Messaging)

**Deps:** `com.google.firebase:firebase-admin` (server), `firebase-messaging:24.1.1` (Android).

**Server files**
- `server/src/main/java/com/khanabook/saas/config/FirebaseConfig.java`
- `server/src/main/java/com/khanabook/saas/service/PushNotificationService.java`
- `server/src/main/java/com/khanabook/saas/entity/NotificationEvent.java`
- `server/src/main/java/com/khanabook/saas/repository/NotificationEventRepository.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/NotificationController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/AdminNotificationController.java`

**Android files**
- `Android/app/src/main/java/com/khanabook/lite/pos/worker/KhanaBookFirebaseMessagingService.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/worker/NotificationHelper.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/worker/NotificationActionReceiver.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/NotificationEntity.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/NotificationDao.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/repository/NotificationRepository.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NotificationsScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NotificationReliabilityScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/NotificationViewModel.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/designsystem/KhanaBookNotificationPanel.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/local/AppDatabase.kt` (Room 62→63)
- `Android/app/src/main/java/com/khanabook/lite/pos/di/DatabaseModule.kt`

**Migration:** V36 `device_token`/notification tables, V39 custom notification messages

---

## 3. FSSAI / GST Compliance

**Server files**
- `server/src/main/java/com/khanabook/saas/entity/FssaiTracker.java`
- `server/src/main/java/com/khanabook/saas/entity/FssaiRenewal.java`
- `server/src/main/java/com/khanabook/saas/repository/FssaiTrackerRepository.java`
- `server/src/main/java/com/khanabook/saas/repository/FssaiRenewalRepository.java`
- `server/src/main/java/com/khanabook/saas/service/FssaiTrackerService.java`
- `server/src/main/java/com/khanabook/saas/service/ComplianceAlertService.java`
- `server/src/main/java/com/khanabook/saas/service/GstFssaiLookupService.java`
- `server/src/main/java/com/khanabook/saas/service/TaxComplianceService.java`
- `server/src/main/java/com/khanabook/saas/controller/GstFssaiController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/TaxComplianceController.java`
- `server/src/main/java/com/khanabook/saas/webadmin/dto/AdminBusinessDetailResponse.java`

**Android files**
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/FssaiRenewalScreen.kt`

**Migrations:** V28 (tracker), V37, V38 (renewals)

---

## 4. Marketplace Orders (Swiggy / Zomato)

**Server files**
- `server/src/main/java/com/khanabook/saas/entity/MarketplaceOrder.java`
- `server/src/main/java/com/khanabook/saas/entity/MarketplaceOrderItem.java`
- `server/src/main/java/com/khanabook/saas/repository/MarketplaceOrderRepository.java`
- `server/src/main/java/com/khanabook/saas/service/MarketplaceOrderService.java`
- `server/src/main/java/com/khanabook/saas/controller/MarketplaceOrderController.java`
- `server/src/main/java/com/khanabook/saas/controller/MarketplaceWebhookController.java`
- `server/src/main/java/com/khanabook/saas/controller/MarketplaceConfigController.java`
- `server/src/main/java/com/khanabook/saas/service/UnifiedCommerceService.java`
- `server/src/main/java/com/khanabook/saas/webadmin/dto/AdminMarketplaceWebhookEventResponse.java`
- `server/src/main/java/com/khanabook/saas/webadmin/dto/BusinessMarketplaceSetupResponse.java`
- `server/src/main/java/com/khanabook/saas/webadmin/dto/UpdateBusinessIntegrationsRequest.java`

**Android files**
- `Android/app/src/main/java/com/khanabook/lite/pos/data/remote/dto/MarketplaceOrderDtos.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/data/repository/MarketplaceOrderRepository.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/MarketplaceOrdersScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/OrdersScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/HomeScreen.kt`

**Web-admin files**
- `web-admin/src/app/pages/marketplace-setup/marketplace-setup-page.component.ts`
- `web-admin/src/app/pages/orders/orders-page.component.ts`

**Migrations:** V25, V27 (marketplace integration fields), V8 (orphan storefront table)

---

## 5. Sub-Merchant Onboarding (Easebuzz KYC)

**Server files**
- `server/src/main/java/com/khanabook/saas/service/SubMerchantService.java`
- `server/src/main/java/com/khanabook/saas/service/OnboardingService.java`
- `server/src/main/java/com/khanabook/saas/entity/EasebuzzSubMerchant.java`
- `server/src/main/java/com/khanabook/saas/repository/EasebuzzSubMerchantRepository.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/AdminSubMerchantController.java`
- `server/src/main/java/com/khanabook/saas/controller/RestaurantAssetController.java` (KYC docs)

**Android files**
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzKycScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzOnboardingScreen.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/EasebuzzOnboardingViewModel.kt`

**Web-admin files**
- `web-admin/src/app/pages/sub-merchants/sub-merchants-page.component.ts`

**Migrations:** V22, V23, V24, V26, V34, V35

---

## 6. Transactional Email

**Deps:** `spring-boot-starter-mail`

**Server files**
- `server/src/main/java/com/khanabook/saas/service/EmailNotificationService.java`

**Migrations:** none

---

## 7. Invoice Template (Thymeleaf)

**Deps:** `spring-boot-starter-thymeleaf`

**Server files**
- `server/src/main/java/com/khanabook/saas/controller/InvoiceController.java`
- Template resources under `server/src/main/resources/templates/` (invoice.html)

**Android files (invoice rendering parity)**
- `Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/InvoicePDFGenerator.kt`
- `Android/app/src/main/java/com/khanabook/lite/pos/domain/util/InvoiceFormatter.kt`

---

## 8. Webhooks / Retry plumbing (shared infra)

- `server/src/main/java/com/khanabook/saas/config/WebhookRetryConfig.java`
- `server/src/main/java/com/khanabook/saas/entity/Chargeback.java`

---

## Cross-cutting platform services (not 3rd-party, but v2-only code)

- `server/src/main/java/com/khanabook/saas/service/DeveloperPortalService.java`
- `server/src/main/java/com/khanabook/saas/webadmin/service/BusinessReadService.java`
- `server/src/main/java/com/khanabook/saas/webadmin/service/AdminReadService.java`
- `server/src/main/java/com/khanabook/saas/webadmin/controller/BusinessAdminController.java`
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

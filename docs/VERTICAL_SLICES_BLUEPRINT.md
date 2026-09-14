# KhanaBook Vertical Slices Blueprint

**Status:** Draft for approval
**Scope:** Android, Server (Spring Boot), Web Admin (Angular)
**Mode:** Safe package restructure — file moves + import updates only. **Behavior identical.**
**Strategy:** Slice-by-slice execution with a platform build gate after every slice.

---

## 1. Goals & Non-Goals

### Goals
- Reorganize each platform from **layered** (`ui/`, `data/`, `domain/` / `controller→service→repository→entity`) to **feature-first vertical slices**: each feature owns its UI, view-models, domain managers, repositories, entities, DTOs, and workers.
- Keep everything compiling and behaviorally identical after each slice. No feature logic changes.
- Make each feature easy to navigate, own, and test in isolation.

### Non-Goals (explicitly out of scope for this pass)
- No new features, no API/endpoint changes, no schema/DB changes.
- No refactor of logic inside any class (only package declarations + imports move).
- No sealed entry-point APIs / module privacy (that's a deeper, riskier phase 2 we can do later).
- No renaming of classes or methods.
- No deleting/merging of functionality (apart from `web-admin` dead backup files — flagged for removal in §7).

---

## 2. Target Package Structure

### 2.1 Android
Base package: `com.khanabook.lite.pos`

```
com.khanabook.lite.pos
├── core/                 # cross-cutting infra shared by all features
│   ├── app/              # KhanaBookApplication, MainActivity
│   ├── di/               # Hilt modules (DatabaseModule, NetworkModule, SessionManagerEntryPoint)
│   ├── database/         # AppDatabase, DatabaseProvider, migrations, TenantDaos (shared DB wiring)
│   ├── network/          # KhanaBookApi (Retrofit interface), AuthInterceptor
│   ├── theme/            # Color, Type, Spacing, Shape, Theme, IconSize, Responsive, Accessibility
│   ├── designsystem/     # shared reusable components
│   ├── components/       # CommandComponents, ManagerPinDialog + shared UI bits
│   ├── navigation/       # AppNavGraph, NavigationItems, gestures, feedback (UiMessage)
│   ├── model/            # shared domain enums/models used across features
│   └── util/             # cross-cutting utils (Utils, AppConstants, validation, crash handler, etc.)
└── feature/
    ├── auth/             # login, signup, session, app lock, security
    ├── billing/          # new bill, orders, active orders, bill search, KOT reprint entry
    ├── menu/             # menu config, categories, items, variants, OCR
    ├── inventory/        # stock, consumption, stock logs
    ├── reports/          # dashboard/home, reports, analytics, export
    ├── sync/             # offline-first sync engine (worker, manager, models, quarantine)
    ├── printing/         # Bluetooth/KOT/KDS, printers, invoice PDF, receipt theme
    ├── payments/         # Easebuzz, UPI QR, payment links, payment modes, limits
    ├── onboarding/       # Easebuzz onboarding, settlement KYC, merchant agreement
    ├── settings/         # shop config, tax, payment config, app reliability, quick start
    ├── staff/            # staff, permissions, user management
    └── notifications/    # in-app notifications, FSSAI reminders, FCM
```

Each feature keeps its own internal layering for readability:
```
feature/<name>/
├── ui/          # composables, screens
├── viewmodel/   # state holders
├── domain/      # managers, use cases, models, utils
├── data/        # repositories, DAOs, entities, DTOs, relations
└── worker/      # WorkManager jobs (if any)
```

**Key structural decisions (Android):**
- `AppDatabase.kt` stays in `core/database` (it holds all migrations and 18 entities by reference — splitting it would be a logic change). Entities/DAOs themselves move into their feature slice; `AppDatabase` imports them from `feature.*`.
- `KhanaBookApi` (Retrofit) stays in `core/network` since it spans every feature; feature-specific request/response DTOs move to their slices.
- `TenantDaos` / tenant wrappers stay in `core/database`.
- `ui/theme/PrintTokens.kt` + `ReceiptTheme.kt` → `feature/printing` theme; `PaymentColors.kt` → `feature/payments` theme.
- `ui/navigation/AppNavGraph`.kt references all screens by import — it lives in `core/navigation` and imports from each feature slice.

### 2.2 Server
Base package: `com.khanabook.saas`

```
com.khanabook.saas
├── core/
│   ├── config/           # Spring config (Async, Cache, Jackson, OpenApi, RateLimiter, WebMvc, EasebuzzProps, Firebase, GoogleAuth)
│   ├── security/         # JWT, filters, roles, tenant, rate limit, revocation
│   ├── exception/        # GlobalExceptionHandler + custom exceptions
│   ├── monitoring/       # health indicator
│   ├── util/, utility/   # constants, JwtUtility, DbCheck etc.
│   └── ...               # cross-cutting service seams: shared sync infra
├── feature/
│   ├── auth/             # auth controller/service/entity/repo/DTO (+ password reset, OTP, rate limiting seams)
│   ├── billing/          # bills, bill items, bill payments, post-split, invoice (public)
│   ├── menu/             # menu items, categories, variants, AI extraction, assets
│   ├── inventory/        # materials, recipes, purchase orders, stock movement, vendors
│   ├── reports/          # analytics, dashboards (business + admin reports)
│   ├── sync/             # offline-first sync engine (master + per-entity push/pull)
│   ├── payments/         # Easebuzz: create/verify/refund/status/cancel, sub-merchant lifecycle, settlement, payout, webhooks, reconciliation, routing, chargebacks, metrics
│   ├── onboarding/       # online payments setup, KYC/settlement docs, merchant agreement, restaurants asset upload
│   ├── platform/         # KBOOK_ADMIN super-admin surrogate (businesses, feature flags, developer portal, commission, notifications-admin, webhooks admin)
│   ├── business/         # OWNER web portal surface (dashboard, orders detail, staff CRUD, menu mgmt, terminals, settings, refunds/void, instant settlement, chargebacks, customers, financing, tax, unified-commerce, onboarding progress)
│   ├── staff/            # users, staff, permissions, role templates (device sync for users lives here too)
│   ├── restaurants/      # restaurant profile, terminals, terminal management
│   ├── compliance/       # GST/FSSAI lookup, tax compliance, FSSAI tracker
│   └── notifications/    # push/email notifications, device tokens, admin notifications
└── db/ (unchanged) flyway in resources
```

**Key structural decisions (Server):**
- Sync engine is complex and spans billing + menu + stock. The **sync machinery** (`sync/service` generic sync, `sync/repository`, `sync/entity/BaseSyncEntity`, `sync/validation`, `sync/dto`) moves to `feature/sync` wholesale. The per-entity controllers (`BillController`, `MenuItemController`, etc.) are currently split between `controller/` (entity sync endpoints) and their sync DTOs — they move to their **feature** slice, importing the generic sync core from `feature/sync`.
- `entity/`, `repository/`, `service/`, `service/impl` files move into feature slices so each feature is self-contained.
- `webadmin/` splits into `feature/platform` (KBOOK_ADMIN) and `feature/business` (OWNER), DTOs follow their controller.
- Cross-package service imports remain legal (Java allows package cycles); we avoid introducing cycles intentionally but no behavior changes.

### 2.3 Web Admin
Base: `web-admin/src/app`

```
src/app
├── core/                 # auth, guards, jwt interceptor, models, theme, toast, firebase, store, utils, api-state
├── layout/               # sidebar layout, bottom action bar
├── shared/               # order-detail-modal, date-range-selector, confirm-dialog, empty-state, api-state, skeleton, secret-input, global-toast, formatters
└── features/
    ├── auth/             # login, forgot password, limited access
    ├── billing/          # orders, active-orders, daily-closing (order-centric), business order search
    ├── business/         # business settings, terminals (device mgmt), dashboard+reports (owner umbrella)
    ├── menu/             # menu management
    ├── inventory/        # inventory page
    ├── staff/            # staff, permissions
    ├── platform/         # platform dashboard, businesses, feature-flags
    └── reports/          # reports, dashboard
```

**Key structural decisions (Web Admin):**
- Feature folders already exist under `pages/`; the churn is small because each page is a standalone component.
- `BusinessApiService` (~315 lines) and `AdminApiService` (~114 lines + stubs) stay as **shared facade services in core** for now — they are cross-feature HTTP facades. Splitting them per feature is optional phase 2 (not required for the slice move).
- Remove the 9 dead backup files (`business-dashboard/*.backup*`, `business-settings/*.backup*`) — they are not compiled.

---

## 3. Android File Map (slice → source → destination)

Moved from `Android/app/src/main/java/com/khanabook/lite/pos/`.

### 3.1 `core/` (shared)
| Source | Destination |
|---|---|
| `KhanaBookApplication.kt` | `core/app/KhanaBookApplication.kt` |
| `ui/MainActivity.kt` | `core/app/MainActivity.kt` |
| `di/DatabaseModule.kt`, `di/NetworkModule.kt`, `di/SessionManagerEntryPoint.kt` | `core/di/` |
| `data/local/AppDatabase.kt`, `data/local/DatabaseProvider.kt`, `data/local/dao/TenantDaos.kt` | `core/database/` |
| `data/remote/api/KhanaBookApi.kt`, `data/remote/interceptor/AuthInterceptor.kt` | `core/network/` |
| `ui/theme/{Color,Theme,Type,Spacing,Shape,IconSize,Responsive,Accessibility}.kt` | `core/theme/` |
| `ui/theme/PrintTokens.kt`, `ui/theme/ReceiptTheme.kt` | `feature/printing/ui/theme/` |
| `ui/theme/PaymentColors.kt` | `feature/payments/ui/theme/` |
| `ui/designsystem/*` (generic shared components) | `core/designsystem/` |
| `ui/components/CommonComponents.kt`, `ui/components/ManagerPinDialog.kt` | `core/components/` |
| `ui/navigation/AppNavGraph.kt`, `ui/navigation/NavigationItems.kt` | `core/navigation/` |
| `ui/gesture/NavigationGestures.kt` | `core/navigation/` |
| `ui/feedback/UiMessage.kt` | `core/feedback/` |
| `domain/model/Enums.kt` (shared cross-feature enums) | `core/model/` |
| `domain/util/{Utils,AppConstants,ValidationUtils,GlobalCrashHandler,BackendErrorParser,MultipartUtils,AppAssetStore,UserMessageSanitizer}.kt` | `core/util/` |

### 3.2 `feature/auth/`
| Source | Destination |
|---|---|
| `ui/screens/LoginScreen.kt`, `ui/screens/RoleAccessScreen.kt`, `ui/screens/BrandedStartFrame.kt` | `feature/auth/ui/` |
| `ui/screens/auth/{SignUpScreen,SignUpComponents,ForgotPasswordDialog}.kt` | `feature/auth/ui/` |
| `ui/screens/AppLockScreen.kt`, `ui/screens/AppLockConfigSection.kt`, `ui/screens/applock/{AppLockView,ChangePasswordView,HelpSupportView}.kt` | `feature/auth/ui/` |
| `ui/screens/BackgroundReliabilityScreen.kt` | `feature/auth/ui/` |
| `ui/viewmodel/{AuthViewModel,LogoutViewModel,AppLockViewModel,DeviceSessionViewModel}.kt` | `feature/auth/viewmodel/` |
| `domain/manager/{AuthManager,SessionManager,TrustedExternalAppReturn}.kt` | `feature/auth/domain/` |
| `domain/model/TerminalIdentity.kt` | `feature/auth/domain/` |
| `domain/util/{KeystoreBackedPreferences,LegacyEncryptedPrefsMigration,BatteryOptimizationHelper}.kt` | `feature/auth/domain/` |
| `data/repository/{UserRepository,RestaurantRepository}.kt` | `feature/auth/data/` |
| `data/local/entity/{UserEntity,RestaurantProfileEntity}.kt`, `data/local/dao/{UserDao,RestaurantDao}.kt` | `feature/auth/data/` |
| `data/remote/api/AuthModels.kt`, `data/remote/dto/{UpdateMobileRequest,UpdateMobileOtpRequest}.kt`, `data/remote/ResetPasswordRequest.kt` | `feature/auth/data/` |

> Note: `HelpSupportView`, `SyncCenterView`, `ChangePasswordView` stay co-located (all four `applock/` files move together under auth for simplicity).

### 3.3 `feature/billing/`
| Source | Destination |
|---|---|
| `ui/screens/NewBillScreen.kt`, `ui/screens/newbill/{CartStep,MenuSelectionStep,OrderConfirmationSection,PaymentStep}.kt` | `feature/billing/ui/` |
| `ui/screens/{OrdersScreen,ActiveOrdersScreen,ActiveOrderScreen,ActiveOrderDetailScreen,SearchScreen,CallCustomerScreen}.kt` | `feature/billing/ui/` |
| `ui/screens/orders/{OrderFilters,OrderTableComponents}.kt`, `ui/screens/activeorder/{ActiveOrderComponents,DetailComponents,PaymentSection}.kt`, `ui/screens/search/SearchComponents.kt` | `feature/billing/ui/` |
| `ui/screens/ReprintKdsScreen.kt` | `feature/printing/ui/` (see 3.8) |
| `ui/viewmodel/{BillingViewModel,CartManager,PaymentStateManager,ActiveOrdersViewModel,ActiveOrderDetailViewModel,SearchViewModel}.kt` | `feature/billing/viewmodel/` |
| `domain/manager/{BillCreationUseCase,BillCalculator,OrderIdManager,SearchManager}.kt` | `feature/billing/domain/` |
| `domain/util/InvoiceFormatter.kt`, `domain/model/ReportModels.kt`? (reports—see 3.5) | `feature/billing/domain/` |
| `data/repository/BillRepository.kt`, `data/local/dao/BillDao.kt` | `feature/billing/data/` |
| `data/local/entity/{BillEntity,BillItemEntity,BillPaymentEntity,TerminalDailyCounterEntity}.kt`, `data/local/relation/BillWithItems.kt` | `feature/billing/data/` |
| `data/remote/api/CounterResponse.kt` | `feature/billing/data/` |

### 3.4 `feature/menu/`
| Source | Destination |
|---|---|
| `ui/screens/MenuConfigurationScreen.kt`, `ui/screens/menuconfig/{ItemEditDialog,CategoryEditDialog,ManualMenuView,ModeSelectionView,ReviewDetectedItemsScreen}.kt` | `feature/menu/ui/` |
| `ui/screens/OcrScannerScreen.kt`, `ui/screens/ocr/OcrComponents.kt` | `feature/menu/ui/` |
| `ui/viewmodel/MenuViewModel.kt` | `feature/menu/viewmodel/` |
| `domain/util/{MenuPricingRules,OcrSpatialParser,TextRecognitionHelper}.kt` | `feature/menu/domain/` |
| `data/repository/{MenuRepository,CategoryRepository}.kt`, `data/local/dao/{MenuDao,CategoryDao}.kt` | `feature/menu/data/` |
| `data/local/entity/{MenuItemEntity,CategoryEntity,ItemVariantEntity}.kt`, `data/local/relation/MenuWithVariants.kt` | `feature/menu/data/` |
| `ui/feedback/{MenuFeedbackPreferences,MenuItemAddFeedback}.kt` | `feature/menu/ui/` |

### 3.5 `feature/inventory/`
| Source | Destination |
|---|---|
| `ui/screens/InventoryScreen.kt` | `feature/inventory/ui/` |
| `ui/viewmodel/InventoryViewModel.kt` | `feature/inventory/viewmodel/` |
| `domain/manager/InventoryConsumptionManager.kt` | `feature/inventory/domain/` |
| `data/repository/InventoryRepository.kt`, `data/local/dao/InventoryDao.kt`, `data/local/entity/StockLogEntity.kt` | `feature/inventory/data/` |
| `data/remote/api/InventoryModels.kt` | `feature/inventory/data/` |

### 3.6 `feature/reports/`
| Source | Destination |
|---|---|
| `ui/screens/{HomeScreen,ReportsScreen}.kt`, `ui/screens/home/HomeComponents.kt`, `ui/screens/reports/{ReportViews,OrderDetailsDialog}.kt` | `feature/reports/ui/` |
| `ui/viewmodel/{HomeViewModel,ReportsViewModel}.kt` | `feature/reports/viewmodel/` |
| `domain/manager/{ReportGenerator,ReportExporter}.kt`, `domain/model/ReportModels.kt` | `feature/reports/domain/` |
| `ui/screens/CustomDateRangePickerDialog.kt` | `core/components/` (shared) |

### 3.7 `feature/sync/`
| Source | Destination |
|---|---|
| `worker/{MasterSyncWorker,BootReceiver}.kt` | `feature/sync/worker/` |
| `ui/screens/InitialSyncScreen.kt`, `ui/screens/applock/SyncCenterView.kt` | `feature/sync/ui/` |
| `ui/viewmodel/InitialSyncViewModel.kt` | `feature/sync/viewmodel/` |
| `domain/manager/{SyncManager,MasterSyncProcessor,SyncNormalizer}.kt`, `domain/model/SyncModels.kt` | `feature/sync/domain/` |
| `domain/util/{SyncWorkScheduler,SyncWorkNames,NetworkMonitor,TerminalPendingApprovalException}.kt` | `feature/sync/domain/` |
| `data/remote/dto/{SyncRequestDtos,SyncEntityMappers,PushSyncResponse}.kt`, `data/remote/api/MasterSyncResponse.kt`, `data/local/entity/SyncQuarantineEntity.kt` | `feature/sync/data/` |

### 3.8 `feature/printing/`
| Source | Destination |
|---|---|
| `ui/screens/PrinterConfigSection.kt`, `ui/screens/printerconfig/PrinterTargetCard.kt`, `ui/screens/ReprintKdsScreen.kt` | `feature/printing/ui/` |
| `ui/viewmodel/PrintCoordinator.kt` | `feature/printing/viewmodel/` |
| `domain/manager/{BluetoothPrinterManager,PrinterTransport,PrintRouter,PrintService,KitchenTicketFormatter,KitchenPrintQueueManager,InvoicePDFGenerator}.kt` | `feature/printing/domain/` |
| `domain/model/{PrinterConnectionType,PrinterRole}.kt`, `domain/util/PdfOpener.kt` | `feature/printing/domain/` |
| `data/repository/{PrinterProfileRepository,KitchenPrintQueueRepository}.kt`, `data/local/dao/{PrinterProfileDao,KitchenPrintQueueDao,KotEventDao}.kt`, `data/local/entity/{PrinterProfileEntity,KitchenPrintQueueEntity,KotEventEntity}.kt` | `feature/printing/data/` |
| `ui/feedback/{PrintFeedback,PrintSpoolerStatusBadge}.kt` | `feature/printing/ui/` |

### 3.9 `feature/payments/`
| Source | Destination |
|---|---|
| `ui/screens/PaymentConfigSection.kt`, `ui/screens/PaymentLinkScreen.kt`, `ui/screens/EasebuzzOnboardingScreen.kt` | `feature/payments/ui/` |
| `ui/viewmodel/{PaymentLinkViewModel,EasebuzzOnboardingViewModel}.kt` | `feature/payments/viewmodel/` |
| `domain/manager/{PaymentGatewayHelper,PaymentModeManager,PaymentReturnManager,PaymentSetValidator,QrCodeManager}.kt`, `domain/model/OrderPaymentFlowMode.kt` | `feature/payments/domain/` |
| `domain/util/{PaymentLimits,AgreementPdfGenerator}.kt` | `feature/payments/domain/` (AgreementPdfGenerator → onboarding? kept here, see 3.10) |
| `data/repository/{EasebuzzOnboardingRepository,EasebuzzPaymentRepository,MerchantAgreementRepository}.kt` | `feature/payments/data/` |
| `data/remote/dto/{EasebuzzOnboardingDtos,EasebuzzPaymentDtos}.kt` | `feature/payments/data/` |

### 3.10 `feature/onboarding/`
| Source | Destination |
|---|---|
| `ui/screens/ComplianceDocumentsScreen.kt`, `ui/screens/MerchantAgreementScreen.kt` | `feature/onboarding/ui/` |
| `ui/viewmodel/MerchantAgreementViewModel.kt` | `feature/onboarding/viewmodel/` |
| `domain/util/AgreementPdfGenerator.kt` | `feature/onboarding/domain/` |
| `data/repository/MerchantAgreementRepository.kt` | `feature/onboarding/data/` |

> Note: depends on `feature/payments` for Easebuzz types. MerchantAgreementRepository + AgreementPdfGenerator are **assigned here** (not payments) so onboarding is the compliance-owner slice.

### 3.11 `feature/settings/`
| Source | Destination |
|---|---|
| `ui/screens/SettingsScreen.kt`, `ui/screens/settings/SettingsComponents.kt`, `ui/screens/{SettingsHomeSection,SettingsSharedComponents,SettingsSupportSections,ShopConfigSection,ShopConfig?}` | `feature/settings/ui/` |
| `ui/screens/shopconfig/ShopConfigComponents.kt`, `ui/screens/TaxConfigSection.kt`, `ui/screens/QuickStartScreen.kt` | `feature/settings/ui/` |
| `ui/viewmodel/{SettingsViewModel,QuickStartViewModel}.kt` | `feature/settings/viewmodel/` |
| `domain/manager/NotificationRouteManager.kt`? (→ notifications) | see 3.12 |
| `data/repository/RestaurantRepository.kt`? — already auth | co-resident |

> **Correction:** RestaurantRepository/RestaurantProfileEntity live with `feature/auth` (3.2); settings slice is the UI surface for shop/tax/payment config screens.

### 3.12 `feature/staff/`
| Source | Destination |
|---|---|
| `ui/screens/StaffPermissionScreen.kt`, `ui/screens/InteractionFeedbackSection.kt` | `feature/staff/ui/` |
| `ui/viewmodel/{StaffPermissionViewModel,UserManagementViewModel}.kt` | `feature/staff/viewmodel/` |
| `domain/manager/PermissionManager.kt` | `feature/staff/domain/` |
| `data/local/entity/{StaffPermissionEntity,PermissionRequestEntity,PermissionCacheEntity}.kt`, `data/local/dao/PermissionCacheDao.kt` | `feature/staff/data/` |
| `data/remote/api/PermissionApiModels.kt` | `feature/staff/data/` |

### 3.13 `feature/notifications/`
| Source | Destination |
|---|---|
| `ui/screens/NotificationsScreen.kt` | `feature/notifications/ui/` |
| `ui/viewmodel/NotificationViewModel.kt` | `feature/notifications/viewmodel/` |
| `domain/manager/NotificationRouteManager.kt` | `feature/notifications/domain/` |
| `data/repository/NotificationRepository.kt`, `data/local/dao/NotificationDao.kt`, `data/local/entity/NotificationEntity.kt` | `feature/notifications/data/` |
| `worker/{KhanaBookFirebaseMessagingService,NotificationActionReceiver,NotificationHelper,FssaiReminderWorker}.kt` | `feature/notifications/worker/` |

### 3.14 Remaining / unassigned
- `ui/screens/QuickStartScreen.kt` + `ui/viewmodel/QuickStartViewModel.kt` → `feature/settings`
- `ui/screens/InteractionFeedbackSection.kt` → `feature/staff` OR `core/feedback` (decide during execution; default staff)

---

## 4. Server File Map

### 4.1 `core/`
| Source | Destination |
|---|---|
| `config/*` (AsyncConfig, CacheConfig, FeatureConfigGuard, JacksonConfig, OpenApiConfig, RateLimiterConfig, WebMvcConfig) | `core/config/` |
| `security/**` (all 16) | `core/security/` |
| `exception/**` (all 7) | `core/exception/` |
| `monitoring/ServerInfoHealthIndicator.java` | `core/monitoring/` |
| `util/{DbCheck,QuickDbCheck}.java`, `utility/{AppConstants,JwtUtility,PricingConstants}.java` | `core/util?` keep `utility/` as-is under core |
| `debug/DebugNDJSONLogger.java` | `core/debug/` |
| `KhanaBookSaaSApplication.java` | `core/` (root stays `com.khanabook.saas`) |
| `config/EasebuzzProperties.java`, `config/WebhookRetryConfig.java`, `config/FirebaseConfig.java`, `config/GoogleAuthConfig.java` | → feature slices (payments / notifications / auth) — see below |

### 4.2 `feature/auth/`
| Source | Destination |
|---|---|
| `controller/AuthController.java` (+ `UpdateMobileRequest`, `UpdateMobileOtpRequest`) | `feature/auth/controller/` |
| `service/AuthService.java`, `service/impl/AuthServiceImpl.java`, `service/{OtpRateLimiter,LoginRateLimiter,DbRateLimiter,WebAdminPasswordResetService,PasswordResetOtpService,SecurityAuditService}.java` | `feature/auth/service/` |
| `entity/{User,UserRole,AuthProvider,RefreshToken,TokenBlocklist,OtpRequest,RateLimitAttempt,SecurityAuditEvent}.java` | `feature/auth/entity/` |
| `repository/{UserRepository,RefreshTokenRepository,TokenBlocklistRepository,OtpRequestRepository,RateLimitAttemptRepository,SecurityAuditLogRepository}.java` | `feature/auth/repository/` |
| `dto/PermissionDtos.java` | `feature/staff/dto/` (permissions) |
| `config/GoogleAuthConfig.java` | `feature/auth/config/` |
| `service/TokenBlocklistCleanupService.java` | `feature/auth/service/` |

### 4.3 `feature/billing/`
| Source | Destination |
|---|---|
| `controller/{BillController,BillItemController,BillPaymentController,InvoiceController}.java` | `feature/billing/controller/` |
| `service/{BillService,BillItemService,BillPaymentService,PostSplitService}.java` + `service/impl/{BillServiceImpl,BillItemServiceImpl,BillPaymentServiceImpl}.java` | `feature/billing/service/` |
| `entity/{Bill,BillItem,BillPayment}.java` | `feature/billing/entity/` |
| `repository/{BillRepository,BillItemRepository,BillPaymentRepository}.java` | `feature/billing/repository/` |
| `sync/service/{BillSyncService,BillPaymentSyncService}.java` | `feature/billing/service/` |
| `sync/dto/payload/{BillDTO,BillItemDTO,BillPaymentDTO}.java` | `feature/billing/dto/` |
| `util/BillTerminalUtil.java` | `feature/billing/util/` |

### 4.4 `feature/menu/`
| Source | Destination |
|---|---|
| `controller/{MenuItemController,CategoryController,ItemVariantController,MenuExtractionController,RestaurantAssetController}.java` | `feature/menu/controller/` |
| `service/{MenuItemService,CategoryService,ItemVariantService,AiMenuExtractionService,MenuExtractionWorker,AssetStorageService}.java` + `service/impl/{MenuItemServiceImpl,CategoryServiceImpl,ItemVariantServiceImpl}.java` | `feature/menu/service/` |
| `entity/{MenuItem,Category,ItemVariant,ItemRecipe,MenuExtractionJob}.java` | `feature/menu/entity/` |
| `repository/{MenuItemRepository,CategoryRepository,ItemVariantRepository,ItemRecipeRepository,MenuExtractionJobRepository}.java` | `feature/menu/repository/` |
| `sync/dto/payload/{MenuItemDTO,CategoryDTO,ItemVariantDTO}.java` | `feature/menu/dto/` |

### 4.5 `feature/inventory/`
| Source | Destination |
|---|---|
| `controller/{InventoryController,StockLogController}.java` | `feature/inventory/controller/` |
| `service/{InventoryService,StockLogService}.java` + `service/impl/StockLogServiceImpl.java` | `feature/inventory/service/` |
| `entity/{RawMaterial,PurchaseOrder,PurchaseOrderItem,StockMovement,StockLog,Vendor}.java` | `feature/inventory/entity/` |
| `repository/{RawMaterialRepository,PurchaseOrderRepository,StockMovementRepository,StockLogRepository,VendorRepository}.java` | `feature/inventory/repository/` |
| `dto/PurchaseOrderDtos.java`, `sync/dto/payload/StockLogDTO.java` | `feature/inventory/dto/` |

### 4.6 `feature/reports/`
| Source | Destination |
|---|---|
| `controller/AnalyticsController.java` | `feature/reports/controller/` |
| `webadmin/controller/{AdminDashboardController,AdminReportsController,AdminCommissionController}.java` | `feature/reports/controller/` |
| `webadmin/dto/{AdminDashboardSummaryResponse,AdminSettlementResponse?,DashboardTrendsResponse,AdminAuditEventResponse}.java` (dashboard/reports DTOs) | `feature/reports/dto/` |
| `webadmin/service/{AdminReadService?,AdminWriteService?}` → see 4.15 business/platform split | — |

### 4.7 `feature/sync/`
| Source | Destination |
|---|---|
| `controller/MasterSyncController.java` | `feature/sync/controller/` |
| `sync/**` : entity/BaseSyncEntity.java, repository/SyncRepository.java, service/{GenericSyncService,RelationalIdResolver,SyncFallbackSaver,TerminalOwnershipService,SyncNotificationService,UserProfileSyncService}.java, validation/{SyncPayloadValidator,SyncPushGuard}.java, dto/PushSyncResponse.java, dto/payload/MasterSyncResponseDTO.java, dto/payload/SyncMapper.java | `feature/sync/` |
| `service/PostSplitService.java`? — billing (already in 4.3) | — |
| `security/authz/{OfflineAuthClass,OfflineAuthDecider,MenuPushAuthorizer,MenuChangeType}.java` | `feature/sync/security/` (offline sync authz) |

### 4.8 `feature/payments/`
| Source | Destination |
|---|---|
| `controller/PaymentController.java`, `webadmin/controller/{AdminSubMerchantController,AdminSettlementController,AdminTransactionController,PaymentMetricsController,PaymentRoutingController,InstantSettlementController,RefundController,ChargebackController,RestaurantPaymentConfigController,WebhookHealthController}.java` | `feature/payments/controller/` |
| `service/{EasebuzzPaymentService,EasebuzzApiClient,EasebuzzWireApiClient,EasebuzzWebhookService,EasebuzzReconciliationService,RefundService,InstantSettlementService,SubMerchantService,PaymentRoutingService,ChargebackPreventionService,WebhookRetryService}.java`, `webadmin/service/PaymentMetricsService.java` | `feature/payments/service/` |
| `entity/{EasebuzzSubMerchant,EasebuzzSubMerchantWebhookEvent,EasebuzzWebhookEvent,EasebuzzPayout,Chargeback,WebhookRetryJob}.java` | `feature/payments/entity/` |
| `repository/{EasebuzzSubMerchantRepository,EasebuzzSubMerchantWebhookEventRepository,EasebuzzWebhookEventRepository,EasebuzzPayoutRepository,ChargebackRepository,WebhookRetryJobRepository}.java` | `feature/payments/repository/` |
| `webadmin/dto/{AdminSubMerchantWebhookEventResponse,AdminSettlementResponse,AdminTransactionResponse,RefundBillRequest,RequestOtpRequest,VerifyOtpRequest,VerifyOtpResponse,...}.java` (payments/settlement DTOs) | `feature/payments/dto/` |
| `config/{EasebuzzProperties,WebhookRetryConfig}.java` | `feature/payments/config/` |

### 4.9 `feature/onboarding/`
| Source | Destination |
|---|---|
| `controller/{OnboardingController,KycDocumentController,MerchantAgreementController}.java` | `feature/onboarding/controller/` |
| `service/{OnboardingService,MerchantAgreementService}.java` | `feature/onboarding/service/` |
| `entity/MerchantAgreement.java`, `repository/MerchantAgreementRepository.java` | `feature/onboarding/entity+repository/` |
| `webadmin/dto/{RequestOtpRequest,VerifyOtpRequest,VerifyOtpResponse,UpdateBusinessIntegrationsRequest,...}.java` | `feature/onboarding/dto/` |

### 4.10 `feature/compliance/`
| Source | Destination |
|---|---|
| `controller/{GstFssaiController,TaxComplianceController}.java` | `feature/compliance/controller/` |
| `service/{GstFssaiLookupService,FssaiTrackerService,TaxComplianceService,ComplianceAlertService}.java` | `feature/compliance/service/` |
| `entity/{FssaiTracker,FssaiRenewal}.java`, `repository/{FssaiTrackerRepository,FssaiRenewalRepository}.java` | `feature/compliance/` |
| `webadmin/controller/TaxComplianceController.java` → single home (device `GstFssaiController` + webadmin tax = same feature) | — |

### 4.11 `feature/notifications/`
| Source | Destination |
|---|---|
| `controller/NotificationController.java`, `webadmin/controller/{NotificationController,NotificationTestController,AdminNotificationController}.java` | `feature/notifications/controller/` |
| `service/{PushNotificationService,EmailNotificationService}.java` | `feature/notifications/service/` |
| `entity/{NotificationEvent,DeviceToken,DeviceRegistrationRequest}.java`, `repository/{NotificationEventRepository,DeviceTokenRepository,DeviceRegistrationRequestRepository}.java` | `feature/notifications/` |
| `config/FirebaseConfig.java` | `feature/notifications/config/` |
| `sync/...` FCM token sync endpoint? (register-fcm-token lives on sync controller) | keep in `feature/sync/controller/` |

### 4.12 `feature/staff/`
| Source | Destination |
|---|---|
| `controller/{PermissionController,UserController?}.java` (UserController is device `/sync/config/users`) | `feature/staff/controller/` + sync-user handling in `feature/auth` or staff |
| `service/{PermissionService,UserService,UserServiceImpl}.java` | `feature/staff/service/` |
| `entity/{StaffPermission,StaffPermissionRevision,PermissionKey,PermissionRequest,RoleTemplate}.java` | `feature/staff/entity/` |
| `repository/{StaffPermissionRepository,StaffPermissionRevisionRepository,PermissionRequestRepository,RoleTemplateRepository}.java` | `feature/staff/repository/` |
| `sync/service/UserProfileSyncService.java`, `sync/dto/payload/UserDTO.java` | `feature/auth/` (users sync) or `feature/staff/` — **default `feature/auth`** to keep users/auth together |

### 4.13 `feature/restaurants/`
| Source | Destination |
|---|---|
| `controller/{TerminalController,TerminalManagementController,RestaurantProfileController}.java` | `feature/restaurants/controller/` |
| `service/{TerminalManagementService,RestaurantProfileService,RestaurantProfileServiceImpl}.java`, `sync/service/TerminalOwnershipService.java` | `feature/restaurants/service/` |
| `entity/{RestaurantProfile,RestaurantTerminal}.java`, `repository/{RestaurantProfileRepository,RestaurantTerminalRepository}.java` | `feature/restaurants/` |
| `sync/dto/payload/RestaurantProfileDTO.java` | `feature/restaurants/dto/` |
| `webadmin/controller/{InstantSettlementController?,BusinessAdminController?}` → split below | — |

### 4.14 `feature/business/` (OWNER web portal)
| Source | Destination |
|---|---|
| `webadmin/controller/BusinessAdminController.java` | `feature/business/controller/` |
| `webadmin/service/{BusinessReadService,BusinessWriteService}.java` | `feature/business/service/` |
| `webadmin/dto/{BusinessDashboardResponse,BusinessOrderListItemResponse,OrderDetailResponse,OrderLineItemResponse,PaginatedOrdersResponse,BusinessMenuListItemResponse,BusinessStaffListItemResponse,BusinessProfileResponse,CreateMenuItemRequest,UpdateMenuItemRequest,CreateStaffRequest,UpdateStaffRequest,StaffCreatedResponse,BusinessCategoryResponse,UpdateBusinessProfileRequest}.java` | `feature/business/dto/` |
| Split: dashboard/orders → `billing`+`reports`, staff → `staff`, menu → `menu`? — **default**: keep BusinessAdminController in `feature/business` as the owner portal facade (it already encapsulates all owner endpoints); sub-slicing its handler methods is optional phase 2. |

### 4.15 `feature/platform/` (KBOOK_ADMIN super-admin)
| Source | Destination |
|---|---|
| `webadmin/controller/{BusinessAdminController? none — Admin* only, DeveloperPortalController,FeatureFlagAdminController}.java` | `feature/platform/controller/` |
| `webadmin/service/{AdminReadService,AdminWriteService}.java` | `feature/platform/service/` |
| `webadmin/dto/{AdminBusinessListItemResponse,AdminBusinessDetailResponse,AdminCreateBusinessRequest,FeatureFlagAdminResponse,AdminAuditEventResponse,AdminSettlementResponse?...}.java` | `feature/platform/dto/` |
| `entity/{FeatureFlag,FeatureFlagOverride,FeatureFlagAudit}.java`, `repository/{FeatureFlagRepository,FeatureFlagOverrideRepository,FeatureFlagAuditRepository}.java`, `service/{FeatureFlagService,FeatureFlagServiceImpl}.java` | `feature/platform/` |

> **Server Rule of thumb:** `BusinessAdminController` and the `Business*` services stay together in `feature/business`; keep the whole controller file intact rather than surgically splitting handler methods (safer). Optional refinements in phase 2.

---

## 5. Web Admin File Map

Moved from `web-admin/src/app/`.

### 5.1 `core/` (unchanged location, stays)
- `core/**` (auth, guards, models, services facades, theme, toast, firebase, store, utils, api-state)
- `layout/**`, `shared/**` stays in place (they are cross-cutting)
- `app.component.ts`, `app.config.ts`, `app.routes.ts` stay at `app/` root

### 5.2 `features/auth/`
| Source | Destination |
|---|---|
| `pages/login/login-page.component.ts` (+ spec) | `features/auth/login/` |
| `pages/limited-access/limited-access-page.component.ts` | `features/auth/limited-access/` |

### 5.3 `features/billing/`
| Source | Destination |
|---|---|
| `pages/orders/orders-page.component.ts` (+ spec) | `features/billing/orders/` |
| `pages/active-orders/active-orders-page.component.ts` | `features/billing/active-orders/` |
| `pages/daily-closing/daily-closing-page.component.ts` | `features/billing/daily-closing/` |

### 5.4 `features/menu/`
- `pages/menu/menu-page.component.ts` → `features/menu/menu-page.component.ts`

### 5.5 `features/inventory/`
- `pages/inventory/inventory-page.component.ts` → `features/inventory/inventory-page.component.ts`

### 5.6 `features/reports/`
| Source | Destination |
|---|---|
| `pages/reports/reports-page.component.ts` | `features/reports/reports-page.component.ts` |
| `pages/business-dashboard/business-dashboard-page.component.ts` (+ cleanup backups) | `features/reports/dashboard/` |

### 5.7 `features/staff/`
| Source | Destination |
|---|---|
| `pages/staff/staff-page.component.ts`, `staff-form-modal.component.ts`, `staff-permissions-modal.component.ts` | `features/staff/` |

### 5.8 `features/business-settings/`
| Source | Destination |
|---|---|
| `pages/business-settings/*` (3 components + cleanup backups) | `features/settings/` |
| `pages/terminals/terminals-page.component.ts` | `features/settings/terminals/` |

### 5.9 `features/platform/`
| Source | Destination |
|---|---|
| `pages/platform-dashboard/platform-dashboard-page.component.ts` | `features/platform/dashboard/` |
| `pages/businesses/businesses-page.component.ts` | `features/platform/businesses/` |
| `pages/feature-flags/feature-flags-page.component.ts` | `features/platform/feature-flags/` |

> Web Admin routes in `app.routes.ts` updated to the new import paths. `BusinessApiService`/`AdminApiService` facade patterns stay in `core/services`.

---

## 6. Execution Order & Verification Gates

Approach: **one slice per step, build the platform, then move on.** No giant one-shot moves.

### Android (per-feature, order chosen to minimize churn)
1. `core/` (app, di, database, network, theme split, designsystem, components, navigation, util) → `assembleDebug`
2. `feature/auth` → `assembleDebug`
3. `feature/billing` → build
4. `feature/menu` → build
5. `feature/inventory` → build
6. `feature/reports` → build
7. `feature/sync` → build
8. `feature/printing` → build
9. `feature/payments` → build
10. `feature/onboarding` → build
11. `feature/settings` → build
12. `feature/staff` → build
13. `feature/notifications` → build
14. Final `./gradlew.bat assembleDebug testDebugUnitTest` (workdir `Android/`)

### Server (per-feature)
1. `core/` (config/security/exception/monitoring) → `mvn -q compile` (workdir `server/`)
2. `feature/auth` → compile
3. `feature/billing` → compile
4. `feature/menu` → compile
5. `feature/inventory` → compile
6. `feature/reports` → compile
7. `feature/sync` → compile
8. `feature/payments` → compile
9. `feature/onboarding` → compile
10. `feature/compliance` → compile
11. `feature/notifications` → compile
12. `feature/staff` → compile
13. `feature/restaurants` → compile
14. `feature/business` + `feature/platform` (webadmin split) → compile
15. Final `mvn -q package` (+ run relevant tests)

### Web Admin
1. `features/*` page moves + `app.routes.ts` import updates → `npx tsc --noEmit` (workdir `web-admin/`)
2. Remove 9 dead backup files
3. Final `npm run build`

---

## 7. Risks & Mitigations (safe-mode guardrails)

| Risk | Mitigation |
|---|---|
| **Android `applock/SyncCenterView`** held by `article`… actually device/references | SyncCenterView ↔ Sync feature import; keep `HelpSupportView`/`ChangePasswordView`/`AppLockView` together in `feature/auth` as one unit |
| **AGENTS.md references** absolute paths/package names | Update `docs/meta/AGENTS.md` + `CLAUDE.md` key-paths at end of each platform pass |
| **`AndroidManifest.xml`** references workers/services by FQN (FssaiReminderWorker, FirebaseMessagingService, BootReceiver, etc.) | Manually update manifest class names → `feature.*` after moves |
| **`AppDatabase`** migrations reference entities by package | Migrations live in `core/database`; add correct imports after each entity move; verify `assembleDebug` (kapt runs migration codegen) |
| **Graphify/gstack caches & `graphify-out/`** | Outside source packages; regenerate/ignore (already gitignored) |
| **Retrofit `KhanaBookApi`** references moved DTOs | Update its imports in `core/network` once per move (compile verifies) |
| **Server JPA entity scan & component scan** | `@SpringBootApplication` root remains `com.khanabook.saas`; feature sub-packages auto-scanned. No `@MapperScan`/`@EntityScan` override present (verify) |
| **Server `webadmin/` split** derails `BusinessAdminController` service injection | Keep each controller whole with its service cluster in `feature/business`/`feature/platform` (owner/platform facades) |
| **Sugar imports across features** | Java/Kotlin resolve by full package; compile per slice catches missing imports |
| **Web Admin dead backup files** | Delete 9 `.backup*`/`.pre-*`/`.p*-start` files in `business-dashboard/` + `business-settings/` |
| **Route/guard path updates in Angular** | `app.routes.ts` lazy `loadComponent` paths updated in same commit as page moves, `tsc` verifies |

---

## 8. Open Questions (for approval)

1. **Android `SettingsScreen`/`ShopConfig` duplication** — `SettingsScreen.kt` exists in both `screens/` and `screens/settings/`; I'll keep both, move them together into `feature/settings/ui/`.
2. **Server `UserController`/user-sync home** — default `feature/auth` (users are auth subjects); staff slice holds permissions. Confirm OK.
3. **`feature/business` vs `reports+billing+staff` sub-slice in server** — default keep `BusinessAdminController` intact in `feature/business`. Confirm OK.
4. **Web Admin `BusinessApiService`/`AdminApiService`** — keep as shared facades in `core/services` this pass (splitting is phase 2). Confirm OK.
5. **Commit strategy** — I will NOT commit unless you ask. Recommend one commit per platform after final build passes. OK?
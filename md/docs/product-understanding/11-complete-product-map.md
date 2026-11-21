# 11 - Complete Product Map & Architecture Cross-Reference
> **KhanaBook SaaS Platform Discovery & Architecture Specification**  
> **Repository:** `/var/www/kbook.iadv.cloud`  
> **Document:** `docs/product-understanding/11-complete-product-map.md`  
> **Status:** Active / Production  

---

## 1. Master Cross-Platform Connectivity Map

This unified matrix maps every capability across all tiers: **Platform → Module → Feature → Allowed Roles → UI Screen → API Endpoint → Backend Service → Database Tables**.

| Platform | Module | Feature | Allowed Roles | UI Screen / Route | API Endpoint | Backend Service | Database Tables Involved |
|---|---|---|---|---|---|---|---|
| **Android POS** | Billing | Order Creation & Cart | `OWNER`, `SHOP_STAFF` | `NewBillScreen.kt`<br/>`Routes.NEW_BILL` | *Local execution* | `CartManager.kt`<br/>`BillingViewModel.kt` | SQLite: `bills`, `bill_items`, `terminal_daily_counter` |
| **Android POS** | Billing | Split / Part Tendering | `OWNER`, `SHOP_STAFF` | `PaymentStep.kt` | *Local execution* | `PaymentStateManager.kt` | SQLite: `bills`, `bill_payments` |
| **Android POS** | Billing | Delta Sync Push | System / Auto | *Background* | `POST /sync/bills/push`<br/>`POST /sync/bills/items/push`<br/>`POST /sync/bills/payments/push` | `GenericSyncService.java`<br/>`BillSyncService.java`<br/>`BillPaymentSyncService.java` | SQLite: `bills`, `bill_items`, `bill_payments`<br/>PostgreSQL: `bills`, `bill_items`, `bill_payments` |
| **Android POS** | Kitchen | KOT Thermal Print | `OWNER`, `SHOP_STAFF` | `KitchenTicketFormatter.kt` | *Local execution* | `PrintRouter.kt`<br/>`PrintQueueManager.kt` | SQLite: `kot_events`, `kitchen_print_queue`, `printer_profiles` |
| **Android POS** | Kitchen | KOT Ticket Reprint | `OWNER`, `SHOP_STAFF` | `ReprintKdsScreen.kt`<br/>`Routes.REPRINT_KOT` | *Local execution* | `PrintRouter.kt` | SQLite: `kot_events`, `printer_profiles` |
| **Android POS** | Operations | Live Floor / Tabs | `OWNER`, `SHOP_STAFF` | `ActiveOrdersScreen.kt`<br/>`Routes.ACTIVE_ORDERS` | *Local execution* | `BillingViewModel.kt` | SQLite: `bills` (where `payment_status = 'pending'`) |
| **Android POS** | Reports | Terminal Day Summary | `OWNER`, `SHOP_STAFF` | `ReportsScreen.kt`<br/>(Tab: Day Summary) | *Local execution* | `ReportsViewModel.kt` | SQLite: `bills`, `bill_payments` |
| **Android POS** | Menu | Portions & Portions Edit | `OWNER` | `MenuConfigurationScreen.kt` | `POST /business/menu`<br/>`POST /sync/menuitem/push` | `MenuRepository.kt`<br/>`VariantDraftReconciler.kt` | SQLite: `categories`, `menu_items`, `item_variants`<br/>PostgreSQL: `categories`, `menuitems`, `itemvariants` |
| **Android POS** | Menu | Barcode Camera Scanner | `OWNER`, `SHOP_STAFF` | `OcrScannerScreen.kt`<br/>`Routes.OCR_SCANNER` | *Local execution* | `CartManager.kt` (`handleScannedBarcode`) | SQLite: `menu_items` (barcode lookup) |
| **Android POS** | Menu | AI / OCR Menu Scan | `OWNER` | `OcrScannerScreen.kt` | *Local ML Kit* | `OcrComponents.kt` | SQLite: `menu_items`, `categories` |
| **Android POS** | Hardware | Printer Hardware Config | `OWNER`, `SHOP_STAFF` | `SettingsScreen.kt`<br/>(Printers Tab) | *Local execution* | `SettingsViewModel.kt`<br/>`PrintRouter.kt` | SQLite: `printer_profiles` (Bluetooth MAC, USB ID, LAN IP) |
| **Android POS** | Settings | Store Branding & Hours | `OWNER` | `SettingsScreen.kt`<br/>(Profile Tab) | `PUT /restaurants/profile` | `RestaurantProfileRepository.kt` | SQLite: `restaurant_profile`<br/>PostgreSQL: `restaurantprofiles` |
| **Android POS** | Auth | Terminal Activation | `OWNER`, `SHOP_STAFF` | `InitialSyncScreen.kt`<br/>`Routes.DEVICE_SETUP` | `POST /sync/terminal/activate` | `TerminalController.java`<br/>`TerminalOwnershipService.java`| PostgreSQL: `restaurant_terminals`, `device_registration_request` |
| **Web Admin** | Operations | Live Floor Management | `OWNER` | `OrdersPageComponent`<br/>`/business/orders` (Live Floor) | `GET /business/orders/active` | `BillController.java` | PostgreSQL: `bills`, `bill_items` |
| **Web Admin** | Operations | Completed Order History | `OWNER` | `OrdersPageComponent`<br/>`/business/orders` (History Tab) | `GET /business/orders` | `BillController.java` | PostgreSQL: `bills`, `bill_items`, `bill_payments` |
| **Web Admin** | Operations | Customer Bill Refund | `OWNER` | `OrdersPageComponent`<br/>(Refund Modal) | `POST /business/bills/{id}/manual-refund`| `BillController.java`<br/>`SecurityAuditService.java` | PostgreSQL: `bills`, `bill_payments`, `security_audit_logs` |
| **Web Admin** | Menu | Category CRUD & Reorder | `OWNER` | `MenuPageComponent`<br/>`/business/menu` | `GET /business/menu/categories`<br/>`POST /business/menu/categories` | `CategoryController.java` | PostgreSQL: `categories` |
| **Web Admin** | Menu | Dish & Variant CRUD | `OWNER` | `MenuPageComponent`<br/>`/business/menu` | `GET /business/menu`<br/>`POST /business/menu` | `MenuItemController.java`<br/>`ItemVariantController.java` | PostgreSQL: `menuitems`, `itemvariants` |
| **Web Admin** | Reports | Business Sales Overview | `OWNER` | `ReportsPageComponent`<br/>`/business/reports` (Overview) | `GET /analytics/revenue` | `AnalyticsController.java` | PostgreSQL: `bills`, `bill_payments` |
| **Web Admin** | Reports | GST Sales Ledger & CSV | `OWNER` | `ReportsPageComponent`<br/>`/business/reports` (GST Tab) | `GET /analytics/gst-ledger`<br/>`GET /analytics/gst-ledger/csv` | `AnalyticsController.java` | PostgreSQL: `bills`, `bill_items` |
| **Web Admin** | Closing | End-of-Day Till Closing | `OWNER` | `DailyClosingPageComponent`<br/>`/business/daily-closing` | `GET /analytics/daily-closing`<br/>`POST /analytics/daily-closing/submit`| `AnalyticsController.java`<br/>`DailyClosingService.java` | PostgreSQL: `bills`, `bill_payments`, `restaurantprofiles` |
| **Web Admin** | Staff | Staff User CRUD | `OWNER` | `StaffPageComponent`<br/>`/business/staff` | `GET /business/staff`<br/>`POST /business/staff` | `UserController.java`<br/>`PermissionService.java` | PostgreSQL: `users` |
| **Web Admin** | Terminals | Fleet Approval & Series | `OWNER` | `TerminalsPageComponent`<br/>`/business/terminals` | `GET /business/terminals`<br/>`POST /business/terminals/requests/{id}/approve` | `TerminalManagementController.java` | PostgreSQL: `restaurant_terminals`, `device_registration_request` |
| **Web Admin** | Settings | Business Profile & Hours | `OWNER` | `BusinessSettingsPageComponent`<br/>`/business/settings` | `GET /restaurants/profile`<br/>`PUT /restaurants/profile` | `RestaurantProfileController.java` | PostgreSQL: `restaurantprofiles` |
| **Web Admin** | SaaS Admin | Tenant Businesses List | `KBOOK_ADMIN` | `BusinessesPageComponent`<br/>`/admin/businesses` | `GET /admin/businesses`<br/>`PUT /admin/businesses/{id}/status` | `BusinessAdminController.java` | PostgreSQL: `restaurantprofiles` |
| **Web Admin** | SaaS Admin | Platform Feature Flags | `KBOOK_ADMIN` | `FeatureFlagsPageComponent`<br/>`/admin/feature-flags` | `GET /admin/feature-flags`<br/>`POST /admin/feature-flags` | `FeatureFlagAdminController.java` | PostgreSQL: `feature_flag`, `feature_flag_override`, `feature_flag_audit`|
| **Web Admin** | SaaS Admin | System Health & Pools | `KBOOK_ADMIN` | `SystemHealthPageComponent`<br/>`/admin/system-health` | `GET /admin/system-health` | `SystemHealthController.java` | PostgreSQL: In-memory HikariCP + JVM stats |
| **Cloud Worker**| Outbox | Async Email Delivery | System / Auto | *Background Scheduler* | *SMTP Port 587* | `ScheduledTasks.java`<br/>`EmailOutboxService.java` | PostgreSQL: `email_outbox` |
| **Cloud Worker**| Delivery | WhatsApp Digital Bill | System / Auto | *Async Executor* | `POST https://graph.facebook.com/...` | `SyncNotificationService.java` | PostgreSQL: `bills`, `restaurantprofiles` |

---

## 2. Product Architecture Flowchart

```mermaid
flowchart TD
    subgraph CounterEdge["Counter Edge: Android POS (Terminal Series A..E)"]
        Touch["Cashier Touch / Scan"] --> Cart["CartManager.kt"]
        Cart --> BillLocal["Save Bill to Room SQLite<br/>(daily_order_display = 'A01')"]
        BillLocal --> KOT["KitchenTicketFormatter.kt"]
        KOT --> Print["Thermal ESC/POS Printer<br/>(Bluetooth / USB / LAN)"]
        BillLocal --> Pay["PaymentStateManager.kt<br/>(Cash / UPI Split)"]
        Pay --> Receipt["InvoiceFormatter.kt<br/>(Customer Receipt: 'Bill: A01')"]
    end

    subgraph SyncEngine["Background Delta Sync Engine"]
        BillLocal -.-> PushQueue["SyncManager.kt<br/>(NonCancellable Mutex)"]
        PushQueue --> BatchHTTP["POST /sync/bills/push<br/>(Pages of 100 Bills, 200 Items)"]
    end

    subgraph CloudCore["Cloud Core: Spring Boot 3 + PostgreSQL 16"]
        BatchHTTP --> TenantCheck["TenantContext Scoping<br/>(WHERE restaurantId = :id)"]
        TenantCheck --> Upsert["Idempotent Upsert<br/>(device_id + local_id)"]
        Upsert --> PG[(PostgreSQL Master DB<br/>kbook_saas)]
        Upsert --> Offload["Async Executor Queue"]
        Offload --> WA["Meta WhatsApp API<br/>(Digital Invoice Delivery)"]
        Offload --> Mail["Email Outbox Scheduler<br/>(PDF Invoice Dispatch)"]
    end

    subgraph OwnerPortal["Owner Portal: Angular 19 Web Admin"]
        PG --> LiveOrders["Live Floor Monitoring<br/>(/business/orders)"]
        PG --> RevAnalytics["Revenue & GST Ledgers<br/>(/business/reports)"]
        PG --> DailyClose["Daily Drawer Closing<br/>(/business/daily-closing)"]
        LiveOrders --> OwnerAction["Owner Modifies Menu / Staff / Fleet"]
        OwnerAction --> PG
    end

    PG -.->|Delta Pull Update| PullSync["GET /sync/master/pull"]
    PullSync -.-> PushQueue
    PushQueue -.->|Upsert LWW| BillLocal
```

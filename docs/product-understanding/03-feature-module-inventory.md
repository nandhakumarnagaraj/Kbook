# 03 - Feature & Module Inventory
> **KhanaBook SaaS Platform Discovery & Architecture Specification**  
> **Repository:** `/var/www/kbook.iadv.cloud`  
> **Document:** `docs/product-understanding/03-feature-module-inventory.md`  
> **Status:** Active / Production  

---

## 1. Master Feature & Module Catalogue

| Module | Feature | Purpose | Platform | User | Implementation Status |
|---|---|---|---|---|---|
| **Billing** | Fast Order Entry & Cart | Rapid dish selection, modifier picking, quantity adjusting | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Billing** | Split & Part Tendering | Tender bill using multiple payment methods (Cash + UPI) | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Billing** | Discounts & Item Comping | Percentage or fixed value bill discounts | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Billing** | Bill Voiding & Cancellation | Void unpaid or draft bills with audit reason | Android POS, Web Admin | Staff (POS), Owner | **FULLY IMPLEMENTED** |
| **Billing** | Customer Bill Refund | Refund settled bills with reason logging | Android POS, Web Admin | Owner, Staff | **FULLY IMPLEMENTED** |
| **Billing** | Multi-Terminal Invoicing | Generate sequential GST invoices with terminal prefix (`A01`) | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Operations** | Live Floor / Active Orders | Monitor open dine-in and takeaway tabs in real time | Android POS, Web Admin | Staff, Owner | **FULLY IMPLEMENTED** |
| **Operations** | Order History & Search | Search historical bills by date, customer, or bill number | Android POS, Web Admin | Staff, Owner | **FULLY IMPLEMENTED** |
| **Kitchen** | Kitchen Order Ticket (KOT) | Dispatch order line items to thermal kitchen printer | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Kitchen** | KOT Reprint & Void Slips | Reprint tickets or fire item cancellation slips | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Kitchen** | Call Customer Token | Notify customer when order is ready via buzzer / display | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Menu** | Category Hierarchy & Sorting | Group dishes and control display order | Android POS, Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Menu** | Dish CRUD & Portions | Create dishes, set base pricing, food type (Veg/Non-Veg) | Android POS, Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Menu** | Item Variants (Half/Full) | Configure multiple size rates per dish | Android POS, Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Menu** | Pinned Favorites | Pin popular items to top of POS grid for 1-tap ordering | Android POS, Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Menu** | Barcode Camera Scanning | Scan packaged food barcodes to instantly add to cart | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Menu** | AI / OCR Menu Digitisation | Scan physical paper menu to auto-extract dishes and rates | Android POS, Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Reports** | Terminal Day Summary | Real-time Z-report showing cash collected, UPI, and tax | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Reports** | Consolidated Web Analytics | Multi-terminal revenue, peak hour, category sales graphs | Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Reports** | GST B2B / B2C Tax Ledger | Taxable value, CGST, SGST breakdown with CSV export | Web Admin, Android | Owner | **FULLY IMPLEMENTED** |
| **Reports** | Daily Closing Reconciliation | End-of-day drawer balancing, expected vs actual cash | Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Staff** | User & Waiter Management | Add cashiers, assign PINs, view active terminal logins | Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Staff** | Pinned Role Permissions | Constant 18-action capability matrix for cashiers | Android, Server | Owner, Staff | **FULLY IMPLEMENTED** |
| **Terminals** | Terminal Series Isolation | Assign terminal letters (`A`..`E`) to physical hardware | Android, Server | Owner | **FULLY IMPLEMENTED** |
| **Terminals** | Device Fleet Activation | Approve/reject new POS devices requesting access | Web Admin | Owner | **FULLY IMPLEMENTED** |
| **Printing** | ESC/POS Receipt Formatter | Direct receipt formatting for 58mm and 80mm thermal paper | Android POS | Staff, Owner | **FULLY IMPLEMENTED** |
| **Printing** | PDF Invoice Generator | Generate branded PDF receipts with QR payment code | Android POS, Web Admin | Staff, Owner | **FULLY IMPLEMENTED** |
| **Delivery** | WhatsApp Digital Invoice | Send receipt download links to customer WhatsApp | Server | Customer | **FULLY IMPLEMENTED** |
| **Delivery** | Email Outbox Dispatch | Async delivery of invoices and daily summary PDFs | Server | Customer, Owner | **FULLY IMPLEMENTED** |
| **Governance**| Feature Flags Engine | Dynamically enable/disable features per tenant | Web Admin, Server | Super Admin | **FULLY IMPLEMENTED** |

---

## 2. In-Depth Feature Architecture (The 8 Dimensions)

### 2.1 Feature: Multi-Terminal Invoicing & Fast Order Entry
1. **What does it do?** Enables cashiers to tap categories, select dishes or portion variants (Half/Full), apply item-level or bill-level discounts, add special preparation notes, and generate an isolated bill number prefixed with the terminal series (e.g., `A01`, `B02`).
2. **Why does the product need it?** Fast checkout is critical in restaurants. Multi-terminal isolation ensures two counters billing simultaneously never generate duplicate bill numbers or lock the database.
3. **Who uses it?** Cashiers (`SHOP_STAFF`) and Restaurant Owners (`OWNER`).
4. **Where is it accessible?** Android POS screen `NewBillScreen.kt` (nav route `Routes.NEW_BILL`).
5. **What business workflow does it support?** Dine-in and Takeaway ordering, counter billing, checkout.
6. **What data does it create or consume?**
   - Consumes: `categories`, `menu_items`, `item_variants`, `restaurant_profile`.
   - Creates: `bills` (order record), `bill_items` (line items), `terminal_daily_counter` (increment).
7. **APIs, Services, and Database Tables:**
   - Client: `BillingViewModel.kt`, `CartManager.kt`, `AppDatabase.kt` (tables `bills`, `bill_items`).
   - Server: `BillSyncService.java`, `GenericSyncService.java` (`POST /sync/bills/push`), PostgreSQL tables `bills`, `bill_items`.
8. **What other modules depend on it?** Live Floor / Active Orders, KOT Kitchen Printing, Reports & Analytics, WhatsApp Receipts.

### 2.2 Feature: Kitchen Order Tickets (KOT) & Local Print Queue
1. **What does it do?** Prints newly added or modified dish items to kitchen thermal printers. Tracks whether items have been `sentToKot`, maintains an event-sourced audit log (`kot_events`), and handles printer disconnects via an offline spool queue (`kitchen_print_queue`).
2. **Why does the product need it?** Kitchen staff require clear, immediate paper tickets showing table number, item name, portion size, and special cooking instructions. Cloud round-trips for kitchen printing introduce unacceptable latency and failure risk.
3. **Who uses it?** Waiters, Cashiers, Kitchen Chefs.
4. **Where is it accessible?** Android POS order screen, `ReprintKdsScreen.kt`, and automatic trigger on order placement.
5. **What business workflow does it support?** Food preparation, kitchen dispatch, course ordering.
6. **What data does it create or consume?**
   - Consumes: `bill_items`, `printer_profiles`.
   - Creates: `kot_events` (immutable audit entries: ADD, VOID, REPRINT), `kitchen_print_queue` (print jobs).
7. **APIs, Services, and Database Tables:**
   - Strictly device-local! Postgres has zero KOT tables.
   - Client: `KitchenTicketFormatter.kt`, `PrintRouter.kt`, `PrintQueueManager.kt`, SQLite tables `kitchen_print_queue`, `kot_events`.
8. **What other modules depend on it?** Active Orders, Billing.

### 2.3 Feature: Split & Part Tendering
1. **What does it do?** Permits paying a single bill across multiple tenders (e.g., ₹200 in Cash and ₹300 via UPI QR), storing `part_amount_1` and `part_amount_2` with individual transaction timestamps and idempotency keys.
2. **Why does the product need it?** In Indian dining, customers routinely split bills between cash and UPI. A rigid single-tender POS causes accounting discrepancies.
3. **Who uses it?** Cashiers and Store Owners.
4. **Where is it accessible?** Android POS checkout modal `PaymentStep.kt`.
5. **What business workflow does it support?** Bill settlement and payment collection.
6. **What data does it create or consume?**
   - Consumes: `bills`.
   - Creates: `bill_payments` rows (with tender modes `CASH`, `UPI`, `CARD`), updates `bills.payment_status = 'paid'`.
7. **APIs, Services, and Database Tables:**
   - Client: `PaymentStateManager.kt`, `BillDao.kt`, SQLite table `bill_payments`.
   - Server: `BillPaymentSyncService.java` (`POST /sync/bills/payments/push`), PostgreSQL table `bill_payments`.
8. **What other modules depend on it?** Daily Closing, Z-Reports, Cashier Shift Float.

### 2.4 Feature: Menu Management, Portions & In-Place Variant Drafts
1. **What does it do?** Enables complete catalog setup: categories, dish names, tax rates, food types (Veg/Non-Veg/Egg), portion sizes (Half/Full/Quarter), and in-place pricing updates.
2. **Why does the product need it?** Menu rates and dish availability fluctuate daily. The reconciler (`VariantDraftReconciler.kt`) updates variants in place rather than dropping and recreating them, preventing orphaned rows.
3. **Who uses it?** Restaurant Owner (`OWNER` role only; cashiers are strictly blocked from editing menu rates).
4. **Where is it accessible?** Web Admin `/business/menu` (`MenuPageComponent`) and Android POS `MenuConfigurationScreen.kt`.
5. **What business workflow does it support?** Menu creation, price adjustment, 86'ing out-of-stock items.
6. **What data does it create or consume?**
   - Creates/Updates: `categories`, `menu_items`, `item_variants`.
7. **APIs, Services, and Database Tables:**
   - Client: `MenuRepository.kt`, `VariantDraftReconciler.kt`, SQLite tables `categories`, `menu_items`, `item_variants`.
   - Server: `MenuItemController.java`, `CategoryController.java` (`/business/menu/**`), PostgreSQL tables `categories`, `menuitems`, `itemvariants`.
8. **What other modules depend on it?** Fast Order Entry, Live Floor, KOT.

### 2.5 Feature: AI & Camera OCR Menu Digitisation
1. **What does it do?** Captures physical paper restaurant menus via the Android camera or text paste on Web Admin, processes text using ML Kit OCR, and parses dish names and prices into draft menu items for one-click approval.
2. **Why does the product need it?** Manual menu entry is the single largest friction point in restaurant onboarding. OCR reduces onboarding time from hours to minutes.
3. **Who uses it?** Restaurant Owner.
4. **Where is it accessible?** Android POS `OcrScannerScreen.kt` and Web Admin Menu Import dialog.
5. **What business workflow does it support?** New restaurant onboarding, seasonal menu updates.
6. **What data does it create or consume?**
   - Consumes: Camera image / image URI.
   - Creates: Draft `menu_items` and `categories`.
7. **APIs, Services, and Database Tables:**
   - Client: `OcrScannerScreen.kt`, `OcrComponents.kt` (ML Kit TextRecognition).
   - Server: `MenuExtractionController.java`, PostgreSQL table `menu_extraction_jobs`.
8. **What other modules depend on it?** Menu Management.

### 2.6 Feature: Live Floor & Active Orders Monitoring
1. **What does it do?** Displays all currently open tables and takeaway orders across all active terminals in the restaurant, showing order duration, item counts, total amount, and unpaid status.
2. **Why does the product need it?** Floor staff need to see which tables are waiting, add items to existing tabs, or settle payments when guests request the bill. The owner needs remote visibility from the web console.
3. **Who uses it?** Waiters, Cashiers, Restaurant Owner.
4. **Where is it accessible?** Android POS `ActiveOrdersScreen.kt` and Web Admin `/business/orders` (Live Floor tab).
5. **What business workflow does it support?** Dine-in service, table turnover tracking, tab settlement.
6. **What data does it create or consume?**
   - Consumes: `bills` (where `payment_status = 'pending'` and `is_deleted = 0`).
7. **APIs, Services, and Database Tables:**
   - Client: `BillingViewModel.kt`, SQLite table `bills`.
   - Server: `BillController.java` (`GET /business/orders/active`), PostgreSQL table `bills`.
8. **What other modules depend on it?** Billing, KOT Printing, Table Settlement.

### 2.7 Feature: Daily Closing & Cash Drawer Balancing
1. **What does it do?** Calculates expected cash (opening cash float + cash sales - cash refunds), records counted drawer cash, flags cash discrepancies/variance, audits payment channel totals (Cash, UPI, Card), and locks the daily business cycle.
2. **Why does the product need it?** Eliminates cashier theft and drawer reconciliation errors at end of day (EOD).
3. **Who uses it?** Store Owner (`OWNER`).
4. **Where is it accessible?** Web Admin `/business/daily-closing` (`DailyClosingPageComponent`).
5. **What business workflow does it support?** End-of-Day cash closing, till audit.
6. **What data does it create or consume?**
   - Consumes: All `bills` and `bill_payments` within the restaurant's operational business hours window.
   - Creates: Closing audit snapshots.
7. **APIs, Services, and Database Tables:**
   - Server: `AnalyticsController.java` (`GET /analytics/daily-closing`), PostgreSQL tables `bills`, `bill_payments`, `restaurantprofiles`.
8. **What other modules depend on it?** Reports & Analytics, Accounting.

### 2.8 Feature: Terminal Fleet Governance & Series Assignment
1. **What does it do?** Controls which physical hardware devices can access the restaurant database. Assigns an isolated terminal series letter (`A`, `B`, `C`, `D`, or `E`) to each registered device, records device hardware fingerprints, and permits owners to approve or revoke counter access.
2. **Why does the product need it?** Prevents unauthorized devices from accessing the restaurant's financial data and guarantees collision-free bill numbering.
3. **Who uses it?** Store Owner on Web Admin, Platform Operator on Super Admin.
4. **Where is it accessible?** Web Admin `/business/terminals` (`TerminalsPageComponent`) and Android POS `InitialSyncScreen.kt`.
5. **What business workflow does it support?** Counter setup, hardware replacement, security revocation.
6. **What data does it create or consume?**
   - Consumes/Creates: `restaurant_terminals`, `device_registration_request`.
7. **APIs, Services, and Database Tables:**
   - Client: `SessionManager.kt`, `SyncManager.ensureTerminalActivated`.
   - Server: `TerminalController.java`, `TerminalManagementController.java`, PostgreSQL tables `restaurant_terminals`, `device_registration_request`.
8. **What other modules depend on it?** Multi-Terminal Invoicing, Delta Synchronization, Auth.

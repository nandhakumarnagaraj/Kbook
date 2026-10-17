# KhanaBook — Feature Inventory

> Canonical feature map for all three surfaces. Verified against the working tree at
> `main` / HEAD `afa5cc86` on 2026-10-17.
>
> **Evidence rule:** every row names the code that implements it. A feature is only
> listed as `COMPLETE` when a client path and the server (or local DB) path both exist.
> `ORPHAN` means the server implements it and no client calls it.
>
> Companion docs: `PRODUCT.md` (what/who), `DESIGN.md` (tokens),
> `../ARCHITECTURE.md` (blast radius), `PROBLEM.md` (open issues + gaps).
> Supersedes the feature matrix in the 2026-10-14 `FEATURE_AUDIT_REPORT.txt`.

## Status vocabulary

| Status | Meaning |
|---|---|
| **COMPLETE** | Reachable end-to-end on the named surface; authorization enforced |
| **PARTIAL** | Reachable, but a known piece is missing (validation, parity, feedback) |
| **INTENTIONAL** | Deliberately absent or deliberately different by surface; documented |
| **ORPHAN** | Server capability exists; no client calls it |
| **MISSING** | Not implemented on that surface |

`➖` = not applicable on that surface (the surface is not the place that capability lives).

## Roles and surfaces

| Surface | Roles that can use it | Enforcement |
|---|---|---|
| Android POS | `OWNER`, `SHOP_STAFF` | `MainActivity:82` → `SessionManager.canUsePos()`; `KBOOK_ADMIN` redirected to `role_access` |
| Web admin | `OWNER`, `KBOOK_ADMIN` | `roleGuard` + route `data.roles`; `SHOP_STAFF` rejected at login (`AuthServiceImpl` G3) |
| Server | authority for all | URL rules (`SecurityConfig`) + `@RequireRole` aspect + service-level permission checks |

Role capabilities:

- **OWNER** — everything on Android and the business web console.
- **SHOP_STAFF** — fixed permission set (18 keys). Can bill, void, discount, settle,
  refund via gateway, view menu/orders/reports, configure printers, activate the device.
  Cannot edit menu/master data, shop/payment/tax config, staff, or export reports.
  Explicit permission rows never expand this set (`PermissionService`, `PermissionManager`).
- **KBOOK_ADMIN** — platform only: `/admin/**`, no `/business/**`, no Android POS.

## Feature matrix

| Module | Android | Web admin | Server / API | Status |
|---|---|---|---|---|
| **Billing** — new order, cart, discounts, split/part payments | ✅ | ➖ (web can edit status/payment mode) | `/sync/bills/**`, `BillingViewModel`, `BillCalculator` | COMPLETE |
| **Active orders / live floor** | ✅ `ActiveOrdersScreen`, `ActiveOrderDetailScreen` | ✅ `business/active-orders` (`live-orders-tab`) | `/business/orders`, terminal-scoped pull | COMPLETE |
| **Order history** | ✅ reports + search | ✅ `business/orders` (`order-history-tab`) | `/business/orders` paginated | COMPLETE |
| **Find bill / search** | ✅ `SearchScreen` | ➖ | local DAO + server lookup | COMPLETE |
| **KOT / kitchen tickets** | ✅ event-sourced (`kot_events` → `PrintRouter`) | ➖ | KOT state rides on `bill_items.kotState` | COMPLETE |
| **Reprint KOT** | ✅ `ReprintKdsScreen` | ➖ | local only | COMPLETE |
| **Call customer** | ✅ `CallCustomerScreen` | ➖ | local only | COMPLETE |
| **Invoice / receipt PDF** | ✅ `InvoicePDFGenerator` | ✅ invoice HTML endpoint | `InvoiceController` | COMPLETE |
| **Menu items CRUD** | ✅ OWNER only (`MenuConfigurationScreen`) | ✅ `business/menu` | `/business/menu`, `/sync/menuitem` | COMPLETE |
| **Categories + reorder** | ✅ `ManageCategoriesDialog` | ✅ category modal | `/business/menu/categories`, `/sync/menu/categories` | COMPLETE |
| **Menu item variants** | ✅ read/select | ✅ create/delete | `/business/menu/{id}/variants` | COMPLETE |
| **Favourites / pinned dishes** | ✅ category pin + item pin + favourites list (`MenuDao` favourites query) | ✅ star categories and dishes, favourites view | `PUT .../favourite`, `/sync/menuitem` field pull | COMPLETE |
| **Menu images** | ✅ display | ✅ upload/delete | `/menus/items/{id}/image`, `/restaurants/logo` | COMPLETE |
| **Menu OCR / AI extract** | ✅ `OcrScannerScreen` + review | ✅ paste-text extraction | `/menus/ai-extract-text`, `MenuExtractionController` | COMPLETE |
| **Move dish between categories** | ✅ row action + dialog | ✅ bulk move | `/business/menu` | COMPLETE |
| **Raw-material inventory** | ❌ (intentionally out of Android scope) | ✅ `business/inventory` — materials, stock in/wastage/count, movements, variance | `/inventory/**` | COMPLETE |
| **Recipes + auto-deduction** | ❌ | ✅ Item Recipes tab | `/inventory/recipes/**`, `deductForFinalizedBill` | COMPLETE |
| **Purchase orders** | ❌ | ✅ create/list/receive/cancel | `/inventory/purchase-orders/**` | COMPLETE |
| **Vendors** | ❌ | ✅ list/create | `/inventory/vendors` | PARTIAL — no edit/delete |
| **Reports — day summary / full / GST** | ✅ `ReportsScreen` (terminal-scoped) | ✅ `business/reports` tabs: Overview & Revenue, GST, Items | `/analytics/*` | COMPLETE |
| **Daily closing** | ➖ | ✅ `business/daily-closing` — cash drawer, channels, ticket audit | `/analytics/daily-closing` | COMPLETE |
| **Report CSV export** | ✅ OWNER only | ✅ | server CSV | COMPLETE |
| **Staff management** | ❌ (no staff UI on Android) | ✅ CRUD, sessions, resend/activate/revoke | `/business/staff`, `/permissions/**` | COMPLETE |
| **Permission viewer** | ✅ `PermissionManager` (fixed role set) | ✅ modal: read-only for SHOP_STAFF, template editor otherwise | `PermissionService` | COMPLETE |
| **Terminals — activate/reclaim** | ✅ `InitialSyncScreen`, `SyncManager.ensureTerminalActivated` | ➖ | `/sync/terminal/**` | COMPLETE |
| **Terminals — fleet admin** | ➖ | ✅ approve/reject/recover/rename/primary/request list | `/business/terminals/**` | COMPLETE |
| **Easebuzz onboarding** | ✅ `EasebuzzOnboardingScreen` (KYC, OTP, resubmit) | ❌ phone-only | `/restaurants/payment-config/easebuzz/**` | PARTIAL — web has config read/write only |
| **UPI QR / hosted checkout** | ✅ `PaymentStep`, `PaymentStateManager` | ➖ | `/payments/easebuzz/**` | COMPLETE |
| **Gateway refunds** | ✅ | ✅ | `/payments/easebuzz/refund` | COMPLETE |
| **Manual refunds** | ❌ INTENTIONAL | ✅ OWNER only, audited | `/business/bills/{id}/manual-refund` | COMPLETE |
| **Settlements / sub-merchants / transactions** | ➖ | ✅ platform dashboard tabs | `/admin/settlements`, `/admin/sub-merchants`, `/admin/transactions` | COMPLETE |
| **Commission / routing / metrics / webhooks** | ➖ | ⚠️ no page or service wrapper (dashboard copy mentions webhook status, but only settlements/sub-merchants/transactions are rendered) | `/admin/commission`, `/admin/payment-routing`, `/admin/payment-metrics`, `/admin/webhooks` | PARTIAL |
| **KYC documents / merchant agreement** | ✅ upload | ✅ owner + admin download | `/onboarding/**`, `/admin/kyc-document/**` | COMPLETE |
| **Notifications** | ✅ in-app + FCM + preferences | ✅ list + admin send | `/notifications/**`, `/admin/notifications/send` | PARTIAL — admin push unvalidated/unaudited |
| **Sync** | ✅ `SyncManager`, `MasterSyncProcessor`, Sync Center | ➖ | `/sync/*` push + `/sync/master/pull` | COMPLETE |
| **Feature flags** | ➖ | ✅ `admin/feature-flags` | `/admin/feature-flags/**` | COMPLETE |
| **Platform businesses** | ➖ | ✅ list + suspend/activate | `/admin/businesses/**` | COMPLETE |
| **GST return / tax compliance** | ❌ | ❌ | `/business/tax/**` | **ORPHAN** |
| **Customer insights / consent / erasure** | ❌ | ❌ | `/business/customers/**` | **ORPHAN** |
| **Financing** | ❌ | ❌ | `/business/financing/**` | **ORPHAN** |
| **Unified commerce** | ❌ | ❌ | `/business/commerce/**` | **ORPHAN** |
| **Owner onboarding** | ❌ | ❌ | `/business/onboarding/**` | **ORPHAN** |
| **Legacy stock ledger** | ❌ | ❌ | `/sync/stocklog`, `stock_logs` table | **ORPHAN / dead path** |

## Android POS — verified capabilities

**Shell.** Bottom tabs Home / Reports / Settings (`MainScreen`), double-back-to-exit.
Routes in `apps/android/app/src/main/java/com/khanabook/lite/pos/core/navigation/Routes.kt`;
graph in `AppNavGraph`.

**Home** (`apps/android/app/src/main/java/com/khanabook/lite/pos/feature/reports/ui/HomeScreen.kt`)
— five actions always visible:
Create New Order, Active Orders, Find Bill, Reprint KOT, Call Customer.

**Billing** (`feature/billing`):
- `NewBillScreen` steps: menu selection → cart → payment (tablet split-view at ≥840 dp).
- Quick mode vs normal mode; dine-in pay-after-food drafts; resume payment.
- Split payment (`BillCalculator.splitPartPayment`), part cash/UPI, change calculation.
- Discounts, void/cancel, order status edits; owner can edit same-day, non-cancelled bills.
- Draft persistence and recovery; terminal daily bill counter + `operationId` idempotency.
- `BillingViewModel` is the money path and the largest file (~1,750 LOC).

**Menu** (`feature/menu`):
- Manual menu view, category management (add/rename/delete/drag-order), item editor
  (price, variants, veg/non-veg, availability), OCR import with review screen.
- Category and item favourites; favourites float to the top and have a dedicated list.
- Master-data writes gated by `SessionManager.canWriteMasterData()` (OWNER); staff get
  an explicit "permission blocked" dialog.

**Printing** (`feature/printing`):
- Bluetooth, USB, and network transports; role→printer profiles; paper size, copies, logo.
- Event-sourced KOT: ADD/VOID/REPRINT with immutable snapshots, batch tokens,
  `PrintRouter` terminal-ownership guard, 30 s retry queue.
- Invoice PDF generation and share/open.

**Payments** (`feature/payments`):
- Easebuzz sub-merchant onboarding (bank/IFSC/PAN/OTP), resubmit, KYC access key.
- Hosted checkout + dynamic UPI QR for the bill amount; verification on the server.
- Gateway refund and refund-status polling; cancel pending attempts.

**Reports** (`feature/reports`): day summary, full report, GST report; order details
dialog; cancel/edit from the report row; export restricted to OWNER.

**Settings** (`feature/settings`): shop profile, tax/GST, payment config, printer config,
app lock/PIN, display/feedback, sync center, support, sign-out.

**Sync** (`feature/sync`): serialized mutex, debounced push, paged push (bills 100,
items/payments 200), optimistic ack mapping, stale-push guard, conflict recovery,
quarantine table, paged master pull, terminal activation/reclaim.

**Notifications** (`feature/notifications`): in-app banner + list + preferences, FCM
service, FSSAI reminder worker.

## Web admin — verified pages

| Route | Page | What it does |
|---|---|---|
| `/login` | Login | owner/admin sign-in |
| `/limited-access` | Limited access | rejection surface for disallowed roles |
| `/admin/dashboard` | Platform dashboard | overview + settlements, sub-merchants, transactions tabs |
| `/admin/businesses` | Businesses | list, suspend/activate |
| `/admin/feature-flags` | Feature flags | global + per-restaurant overrides + audit |
| `/business/dashboard` | Business dashboard | KPIs, hourly bars, live floor |
| `/business/reports` | Reports | Overview & Revenue, GST, Items; CSV export |
| `/business/daily-closing` | Daily closing | cash reconciliation, channel split, ticket audit |
| `/business/orders` | Orders | Live orders + Order history tabs |
| `/business/active-orders` | Redirect | → orders live tab |
| `/business/menu` | Menu | items, variants, categories, images, OCR import, favourites |
| `/business/inventory` | Inventory | materials, recipes, purchase orders, movements, variance |
| `/business/staff` | Staff | CRUD, activate/deactivate, sessions, permission viewer |
| `/business/terminals` | Terminals | fleet, pairing requests, approve/reject/recover, rename, primary |
| `/business/settings` | Settings | identity/invoicing, payment methods, tax, password |

**Conventions:** standalone components, signals, lazy `loadComponent`, shared
`PageHeaderComponent`, plain CSS with `--kb-color-*` tokens, `styles/bento.css` vocabulary.

## Server — verified packages

`feature/`: auth · billing · business · compliance · inventory · menu · notifications ·
onboarding · payments · platform · reports · restaurants · staff · sync.

Notable controllers: `BusinessAdminController` (owner console), `InventoryController`
(OWNER on writes), the per-entity sync controllers (10 `/pull` + 9 `/push` endpoints)
plus `MasterSyncController` (master pull), `PaymentController`,
`RefundController`, `AdminSubMerchantController`, `AdminSettlementController`,
`FeatureFlagAdminController`, `TaxComplianceController` (orphan),
`CustomerDataController` (orphan), `FinancingController` (orphan),
`UnifiedCommerceController` (orphan), `OnboardingController` (orphan),
`StockLogController` (legacy).

Persistence: PostgreSQL + Flyway (91 migration files, latest `V109`), 43 `@Entity` classes.

## Data residency

| Local SQLite only (encrypted) | PostgreSQL (server truth) |
|---|---|
| `printer_profiles`, `kitchen_print_queue`, `kot_events`, `terminal_daily_counter`, `sync_quarantine_records`, `permission_cache`, `notifications` | bills, bill items/payments (including KOT state), menu/categories/variants, restaurant profile, users/staff/permissions, inventory, payments/Easebuzz, terminals, notifications broadcast |

Android Room schema: **v82** (17 entity files, per-restaurant encrypted DBs).

## Recently added / changed (since the 2026-10-14 audit)

- Menu favourites: star dishes on Android and web; favourites view/sort. Category
  pins are server-owned; dishes support both a server pin and a device-local pin.
- "Open bill" now renders as **Preparing** on both surfaces.
- Web live order cancel action labelled **Cancel** and styled destructive.
- Move-to-category dialog reachable from the dish row.
- Sync: gateway-paid bill's paid time protected from device overwrite; stale-push guard
  now fires; restaurant joining date kept out of the till; master pull no longer erases
  a pinned category.
- Admin "Last Used" now counts only settled bills.
- Platform dashboard KPI entrance/hourly bars animated (animejs).

## Explicitly out of scope

Per `docs/meta/AGENTS.md` — do not build: Zomato/Swiggy/marketplace integrations,
storefront ordering, Android raw-material inventory UI, Android staff-management UI,
kitchen display screen.

> Note: `AGENTS.md` also lists "payment links" as out of scope, but Android's
> `EasebuzzPaymentRepository.createPaymentLink(ForBill)` is wired into
> `BillingViewModel`. Treat that entry as needing clarification rather than as a
> prohibition on the existing hosted-checkout flow.

## Verification

```bash
# Android feature surface
find apps/android/app/src/main/java/com/khanabook/lite/pos/feature -name "*.kt"

# Web routes / pages
grep -n "path:" apps/web-admin/src/app/app.routes.ts
find apps/web-admin/src/app/pages -name "*.component.ts"

# Server controllers and endpoints
find services/server/src/main/java -path "*controller*" -name "*.java"
grep -rn "@RequireRole" services/server/src/main/java --include="*.java"
```

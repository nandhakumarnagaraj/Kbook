# KhanaBook — Product Specification

> **Offline-first multi-terminal restaurant PoS. Free forever. No subscriptions.**

---

## 1. Users

### Primary Users

| User | Context | Primary Device | Core Jobs-to-be-Done |
|------|---------|----------------|----------------------|
| **Restaurant Owner** | Small-to-medium restaurant (5–50 seats), single or multi-outlet | Android POS tablet (counter) + Web Admin (laptop/phone) | Run daily operations, manage menu/prices, view real-time sales, reconcile closing, manage staff & devices, zero recurring cost |
| **Shop Staff** (Waiter / Cashier / Floor Manager) | Floor or counter, high-pressure service hours | Android POS tablet (handheld or counter-mounted) | Take dine-in/takeaway orders fast, split bills, print KOT, accept cash/UPI/card, zero training overhead |
| **KBook Admin** (Platform Operator) | SaaS operations team | Web Admin (desktop) | Onboard restaurants, monitor platform health, feature-flag rollouts, support tickets, system-wide analytics |

### Secondary Users
- **Accountant / CA** — Exports daily closing, GST reports, invoice PDFs from Web Admin
- **Kitchen Staff** — Receives printed KOTs (no direct app interaction)
- **Customers** — Scan UPI QR on table tent / bill, receive WhatsApp invoice link

---

## 2. Problem & Purpose

### The Problem
> **Indian restaurants lose 15–30% revenue to:** (a) subscription PoS that locks features behind tiers, (b) cloud-only PoS that fails during peak hours when internet drops, (c) fragmented tools — billing on one app, menu on another, staff on WhatsApp, reports in Excel.

### The Solution
**KhanaBook** is a **single, free, offline-first PoS + Admin suite** that runs on commodity Android hardware. It gives owners:
- **Billing that never stops** — Room DB + background sync; bills mint offline, push when online
- **Multi-terminal without licence fees** — Add tablets freely; owner approves each device once
- **Owner writes, staff reads** — Menu, prices, tax, payment config edited only by Owner; staff terminals are read-only bill mints
- **Web Admin for the "back office"** — Dashboard, orders, inventory, staff, terminals, settings — no VPN, no RDP
- **Zero recurring cost** — Free forever; revenue via optional payment-gateway partnerships (Easebuzz), not SaaS subscriptions

### Success Metrics
| Metric | Target |
|--------|--------|
| **Offline bill success rate** | ≥ 99.9% (no bill lost to connectivity) |
| **Cold-start to first bill** | < 8 sec on 4-year-old Android tablet |
| **Sync latency (online)** | < 2 s median for bill push |
| **Owner NPS** | > 60 (measured quarterly via in-app prompt) |
| **Monthly active terminals / restaurant** | ≥ 2 (proves multi-terminal value) |
| **Platform churn (restaurant)** | < 3% monthly (free product, churn = abandonment) |

---

## 3. Tone & Design Principles

### Brand Voice
- **Practical, not flashy** — "It just works" over "AI-powered"
- **Respectful of chaos** — Kitchen is loud, hands are greasy, internet is flaky
- **Owner-first language** — "Your menu", "Your devices", "Your data"
- **No dark patterns** — No upsell modals, no trial countdowns, no feature gating

### Visual Rules (Per Platform)
| Rule | Android POS (Compose) | Web Admin (Angular + Astryx) |
|------|----------------------|------------------------------|
| **Theme** | **Dark-only** (forced dark, bright kitchen environments) | **Light-only** (light workspace, `color-scheme: light`) |
| **Brand Primary** | **Gold/Saffron** `#C8960C` (`PrimaryGold`) | **Orange/Saffron** `#FF5A0A` (`--kb-color-primary`) |
| **Brand Accent** | LightGold `#F0C040`, TextGold `#D4A843` | Brand Violet `#552AAF` (sidebar, links), Brand Orange `#EC8C17` (money accent) |
| **Background** | `DarkBrown1` `#1A0A0A` | `--kb-color-bg-app` `#F3F8FE` (light blue-white) |
| **Surface** | `DarkBrown2` `#2D1010` | `--kb-color-surface` `#FFFFFF` (white cards) |
| **Surface Elevated** | — | `--kb-color-surface-2` `#F1F5F9`, `--kb-color-surface-3` `#FFFFFF` |
| **On-Surface Text** | `TextLight` `#F5E6C8` | `--kb-color-foreground` `#111827`, `--kb-color-foreground-2` `#334155`, `--kb-color-muted-foreground` `#586F94` |
| **Semantic Colours** | SuccessGreen `#388E3C`, DangerRed `#C62828`, WarningYellow `#FFB300`, PrimaryGold `#C8960C` (borders on DarkBrown2) | Success `#166534`, Warning `#9A3F08`, Danger `#B91C1C`, DangerSolid `#B91C1C`, Info `#4A21A6` (deep steps for light mode) |
| **Typography** | **Poppins** (Google Font, fallback to system sans-serif); responsive scaling per device tier | **Inter** (body) / **Plus Jakarta Sans** (display); 9-step scale 11–32 px |
| **Font Scale** | **Respects system font scale** (no pinning; layouts accommodate via scrolling/wrapping) | Browser default; no app-level override |
| **Spacing** | 4 dp base (`extraSmall=4` … `extraHuge=64`); scales by window size tier (Compact 0.9×, Medium 1.0×, Large 1.05×, Tablet 1.15×) | 4 px base (`--kb-space-1=4px` … `--kb-space-16=64px`) |
| **Touch Target** | Minimum 48×48 dp (enforced via `buttonHeightCompact=48dp`) | Minimum 44×44 px (`--kb-touch-min`), primary 48 px (`--kb-touch-primary`) |
| **Elevation** | Material 3 dark scheme (surface/background separation) | 3 shadow levels (`--kb-shadow-xs` … `--kb-shadow-lg`) |
| **Border Radius** | `KhanaRadii.card` = `lg` (14 dp), `KhanaRadii.modal` = `lg` (14 dp), `KhanaRadii.pill` (999 dp) | `--kb-radius-sm=4px` … `--kb-radius-2xl=16px`, `--kb-radius-full=9999px` |
| **Motion** | Material 3 standard easing; respects system animation scale | `--kb-motion-fast=150ms`, `--kb-motion-base=200ms`, `--kb-motion-slow=300ms`; `prefers-reduced-motion` respected |
| **Iconography** | Material Icons Filled (24 dp) + Lottie for tab selection | Inline SVG via Astryx `<ax-icon>`; 24×24 default |

### Accessibility (Non-Negotiable)
- **Android**: System font scale respected (no override); TalkBack `contentDescription` on every interactive element; 48 dp touch targets; colour contrast ≥ 4.5:1 on dark surfaces
- **Web**: WCAG 2.2 AA; semantic HTML; focus-visible outlines (2px solid primary + halo); `aria-live` for toasts; keyboard navigation everywhere; `check-contrast.mjs` in CI

### Copy Style
- **Imperative, present tense**: "Tap to pay", "Swipe to split", "Pull to refresh"
- **No jargon**: "KOT" explained as "Kitchen ticket" on first use
- **Hindi/English bilingual** where space permits (Android strings.xml + Web i18n keys)

---

## 4. Offer & Boundaries

### What's Included (Free Forever)
| Capability | Android POS | Web Admin |
|------------|-------------|-----------|
| **Dine-in billing** (tables, cart, KOT, split, merge) | ✅ | ✅ (live view) |
| **Takeaway billing** | ✅ | ✅ (live view) |
| **Payments**: Cash, UPI QR (dynamic), Card (Easebuzz) | ✅ | ✅ (reports) |
| **Menu management** (categories, items, variants, prices, availability) | ✅ (Owner only) | 🔄 v2 (read-only v1) |
| **Inventory** (stock, low-stock alerts) | ❌ | ✅ (Owner) |
| **Staff management** (add/disable, roles) | ✅ (Owner) | 🔄 v2 (terminals only v1) |
| **Terminal/device management** (approve, rename, deactivate) | ❌ | ✅ (Owner + Shop Admin) |
| **Daily closing** (cash settlement, shift report) | ✅ | ✅ (Owner) |
| **Reports** (sales, GST, item-wise, payment-mode) | ✅ (PDF/CSV export) | ✅ (Owner) |
| **OCR menu upload** (PDF/image → extraction) | ✅ | 🔄 v1 (web upload) |
| **Swiggy / Zomato marketplace config** | ❌ | 🔄 v1 (fix pending) |
| **Invoice viewer / share (WhatsApp, link)** | ✅ | 🔄 v1 (web view) |
| **Multi-restaurant (single owner)** | ⚠️ Backend only (no UI switcher v1) | ⚠️ Login per restaurant |
| **Offline-first sync** (bills, menu, config) | ✅ | N/A (reads synced data) |
| **PIN app lock, display scale, printer config** | ✅ | ❌ |
| **Platform admin** (businesses, users, flags, health, logs) | ❌ | ✅ (KBook Admin) |

### What's Explicitly Out of Scope (v1)
- **Menu CRUD on Web** — requires new server write endpoints; Android is authoring surface
- **Staff CRUD on Web** — same; managed on Android
- **Shop profile editor on Web** — name, address, GSTIN, FSSAI, UPI, logo, tax, footer
- **Reports / analytics page on Web** — no server API; Android `ReportGenerator` is offline-only
- **Full SHOP_ADMIN parity** — Web grants SHOP_ADMIN terminals only; other pages OWNER-only
- **Loyalty / CRM / marketing** — not a PoS concern
- **Table reservation / queue management** — separate domain
- **Hardware peripherals beyond** USB/Bluetooth printer, cash drawer, barcode scanner
- **iOS / Windows / Linux native apps** — Android + Web only

### Pricing & Commercial Model
| Item | Price | Note |
|------|-------|------|
| **KhanaBook PoS (Android)** | ₹0 / month / terminal | Unlimited terminals per restaurant |
| **KhanaBook Web Admin** | ₹0 / month / user | Unlimited Owner + Staff + Admin logins |
| **Payment gateway (Easebuzz)** | Per-transaction (MDR) | Optional; owner enables in Settings; KhanaBook takes no cut |
| **Swiggy / Zomato integration** | ₹0 setup | Merchant onboards directly; KhanaBook only stores encrypted keys |
| **Support** | Community (GitHub Issues) | Priority email for restaurants > 20 terminals (informal) |
| **Custom deployment / white-label** | Negotiated | Separate agreement; not part of free tier |

### Data Ownership & Portability
- **Restaurant owns all data** — bills, menu, customers, staff
- **Export anytime**: CSV (bills, menu, stock), PDF (daily closing, GST), JSON (full backup via Web Admin)
- **No vendor lock-in** — SQLite DB on device; REST API documented for migration
- **GDPR/PDPB ready** — Delete-restaurant endpoint purges all PII

---

## 5. Feature Breakdown (Operational Detail)

> This section documents every user-facing capability with its exact behaviour, platform ownership, and configuration knobs. It is the **source of truth** for what the product does today (v1). If code and this doc disagree, **code wins** — update this doc.

### 5.1 Business Hours (Daily Reset Boundary)

| Aspect | Detail |
|--------|--------|
| **Concept** | A *business day* ≠ calendar day. Orders, bill numbers, daily closing, and reports roll over at the configured **closing time**. |
| **Modes** | **Default Time** — 00:00 (midnight) to 23:59 (full 24 h).<br>**Custom Time** — Owner picks Opening Time + Closing Time (HH:mm, 24 h format). |
| **Overnight support** | Fully supported. If Closing < Opening (e.g., 18:00 → 04:00), the business day spans midnight. Duration must be < 24 h (enforced in UI). |
| **Default values** | DB defaults: Opening `08:00` · Closing `23:00` (treated as **Custom Time**). "Default Time" mode = 00:00–23:59 (full 24 h). UI treats `23:59` or `00:00` as Default. |
| **Where configured** | Android: Settings → Shop Configuration → Business Hours card (segmented toggle: Default / Custom).<br>Web Admin: *read-only in v1* (shop profile read slated for v1.2). |
| **Persistence** | `restaurant_profile.opening_time` / `closing_time` (TEXT, `HH:mm`). Synced via `MasterSyncProcessor` → all terminals. |
| **Rollover label** | UI shows "Daily Order Number will reset at **HH:MM AM/PM**" (e.g., "04:00 AM" for overnight). Uses `BusinessDate.businessDayRolloverLabel()`. |
| **Closing alerts** | Banner appears 30 min / 15 min / at closing / grace period (30 min past). `ClosingAlertCalculator` drives toast + banner. |
| **Daily closing workflow** | Uses business-day window (not calendar day) for cash settlement, shift report, order-number reset. |
| **Validation** | Opening ≠ Closing; duration ≤ 23 h 59 m. Toast warning if violated on save. |
| **Custom Time defaults** | When switching from Default → Custom: Opening `09:00` · Closing `23:00` (UX defaults). |
| **Advanced Cutoff (separate)** | `businessDayCutoffTime` + `cutoffEffectiveFrom` allow an explicit rollover time ≠ closing time. Only active when `cutoffEffectiveFrom` is set (future-dated). Used for regulatory compliance; not exposed in v1 UI. |

### 5.2 Billing Flow Mode (Pay Before / After Food)

| Aspect | Detail |
|--------|--------|
| **Concept** | Determines **when the payment screen appears** during dine-in. |
| **Modes** | **Pay Before Food** (default) — Payment screen shown *before* KOT prints / order sent to kitchen. Customer pays upfront.<br>**Pay After Food** — Payment screen shown *after* items served; KOT prints immediately. Bill stays in DRAFT until payment. |
| **Enum** | `OrderPaymentFlowMode`: `PAY_BEFORE_FOOD` (`pay_before_food`) / `PAY_AFTER_FOOD` (`pay_after_food`). |
| **Where configured** | Android: Settings → Shop Configuration → Billing Mode card (animated toggle switch).<br>Web Admin: *read-only in v1*. |
| **Persistence** | `restaurant_profile.order_payment_flow_mode` (TEXT, default `pay_before_food`). Synced to all terminals. |
| **PIN protection** | Changing mode requires PIN (if app lock enabled) — prevents staff from switching mid-service. |
| **Effect on flows** | - **Pay Before**: New Bill → Item selection → **Payment** → KOT print → Order COMPLETED.<br>- **Pay After**: New Bill → Item selection → KOT print → Order DRAFT → **Payment** (later) → Order COMPLETED. |
| **Takeaway** | Always Pay Before (food handed over after payment). |
| **Reports** | Daily closing / GST ledger use `OrderStatus.COMPLETED` bills only; DRAFT bills excluded. |

### 5.3 Payments (Modes, Splits, Gateway)

| Payment Mode | Enum Value | Display Label | Use Case |
|--------------|------------|---------------|----------|
| **Cash** | `CASH` | "Cash" | Exact tender + change calculation. |
| **UPI (Dynamic QR)** | `UPI` | "UPI" | Generates UPI intent QR per bill (amount embedded). Works offline; no gateway. |
| **POS Machine (Card)** | `POS` | "POS Machine" | External card terminal; manual entry. |
| **Easebuzz (Online)** | `EASEBUZZ` | "Pay Online" | Redirects to Easebuzz checkout (web). Requires internet. |
| **Payment Link** | `PAYMENT_LINK` | "Send Payment Link" | WhatsApp/SMS link to Easebuzz hosted page. |
| **Split: Cash + UPI** | `PART_CASH_UPI` | "Cash + UPI" | Two amounts entered; both recorded. |
| **Split: Cash + POS** | `PART_CASH_POS` | "Cash + POS" | Two amounts entered. |
| **Split: UPI + POS** | `PART_UPI_POS` | "UPI + POS" | Two amounts entered. |

**Key behaviours**
- **No duplicate modes** in a single bill (validated in `PaymentSetValidator` via `require(modes.add(...))`).
- **Part payments** store `part_amount_1` / `part_amount_2` on the **`bills`** table (default `'0.0'`); `bill_payments` stores one row per split component.
- **UPI QR** generated client-side via `upi://pay?pa=...&am=...&cu=INR` — works fully offline.
- **Easebuzz** configured in Settings → Payment Channels (Owner only). Keys encrypted at rest (`KeystoreBackedPreferences`).
- **Gateway limits**: UPI per-transaction cap validated in `PaymentStateManager.validatePaymentLimits()`.
- **Payment status**: `PENDING` → `SUCCESS` / `FAILED` (enum `PaymentStatus`).
- **Refunds**: Manual cash refund recorded as negative payment; gateway refunds via Easebuzz dashboard (not in-app).

### 5.4 Tax & Compliance (GST + Custom Tax + FSSAI)

| Component | Fields | Validation | Notes |
|-----------|--------|------------|-------|
| **GST Registration** | `gstEnabled` (boolean), `gstin` (15-char), `gstPercentage` (0–100, decimal) | GSTIN regex: `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$` | Enabled toggle reveals GSTIN + % fields. Verified via `/business/lookup/gst` (Web Admin) / server sync. |
| **Tax Mode** | `isTaxInclusive` (boolean) | — | **Exclusive** (default): tax added on top of item price.<br>**Inclusive**: item price includes tax (MRP-style). |
| **GST Split** | CGST + SGST = `gstPercentage / 2` each | Calculated per bill line; rounded to 2 dp (half-up). | Intra-state only (inter-state IGST not supported in v1). |
| **Custom Tax (Service Charge)** | `customTaxName` (string), `customTaxNumber` (string), `customTaxPercentage` (0–100) | Optional; percentage validated 0–100. | Added *after* GST. Printed as separate line on invoice (e.g., "Service Charge (5%)"). |
| **FSSAI License** | `fssaiNumber` (14 digits) | Regex `^\d{14}$`; expiry date tracked from server. | Verified via `/business/lookup/fssai`. Expiry banner: **Expired** (red), ≤ 30 days (red), ≤ 90 days (amber). |
| **Country** | `country` (default "India") | Read-only dropdown (India only in v1). | Foundation for future multi-country tax rules. |

**Where configured**
- Android: Settings → Tax & Compliance (Owner only; `canWriteConfig()` gate).
- Web Admin: Business Settings → Tax Settings section (Owner; read/write in v1).

**Invoice printing**
- GST-enabled: "Tax Invoice No", GSTIN, State Code, SAC, CGST/SGST lines.
- GST-disabled: "Invoice No", no tax lines, no GSTIN.
- Custom tax printed as separate line with name + %.

### 5.5 Printing (Receipt + KOT)

| Concept | Detail |
|---------|--------|
| **Printer Roles** | `CUSTOMER` (receipt) · `KITCHEN` (KOT). Each role = separate `printer_profiles` row. |
| **Connection Types** | **Bluetooth** (MAC address), **Wi-Fi** (host:port), **USB** (`usb:` prefixed key = VID:PID[:serial]). |
| **Printer Modes** | **DUAL** (default) — Receipt on CUSTOMER printer, KOT on KITCHEN printer.<br>**SINGLE** — One physical printer prints both; KOT first, then receipt. Kitchen row retained for instant revert. |
| **Paper Sizes** | 58 mm / 80 mm (configurable per printer profile). |
| **KOT Features** | Delta KOT (only new items on reprint), auto-reprint on bill edit, manual reprint from Order History. |
| **Receipt Features** | Logo, GST/Custom tax breakdown, UPI QR (if UPI payment), footer text, QR for invoice link. |
| **Formatting** | ESC/POS thermal commands via `InvoiceFormatter` / `KitchenTicketFormatter`. |
| **Fallback** | If printer offline → PDF viewer intent (system print/share). Toast: "Printer offline. Opening PDF viewer." |
| **Where configured** | Android: Settings → Printer Configuration (Owner + Staff can write printer config).<br>Web Admin: *not in v1* (terminal page shows status only). |
| **Sync** | Printer profiles are **device-local** (not synced). Each terminal pairs its own hardware. `printer_mode` synced via `RestaurantProfileEntity`. |

### 5.6 Menu Management (Android Authoring Surface)

| Capability | Detail |
|------------|--------|
| **Hierarchy** | Category → Item → Variant (size/add-on). Each variant has own price. |
| **Availability** | Per-variant toggle (Available / Unavailable). Real-time on POS. |
| **Food Type** | `VEG` / `NON_VEG` (badge on KOT + receipt). |
| **Tax Override** | Item-level GST % (defaults to shop %). |
| **Images** | Local-first; optional cloud sync. Thumbnail in cart/KOT. |
| **OCR Import** | PDF / image → `/menus/upload` → extraction job → preview → "Import to menu" (Android v1; Web v1.1). |
| **Sync** | Owner edits → PushSync → all terminals pull on next sync. Staff terminals read-only cache. |
| **Web Admin** | Read-only list + search in v1; CRUD in v2.0 (needs server write endpoints). |

### 5.7 Inventory (Web Admin Only)

| Feature | Detail |
|---------|--------|
| **Stock Tracking** | Per variant: current qty, low-stock threshold, unit (pcs/kg/ltr). |
| **Low-Stock Alerts** | Dashboard KPI + notification banner when qty ≤ threshold. |
| **Stock Adjustment** | Manual + / − with reason (wastage, receive, count). Audit trail. |
| **Recipe / BOM** | *Out of scope v1* (planned v2.5). |
| **Sync** | Web → Server → Android pull (read-only on POS). |

### 5.8 Staff & Terminal Management

| Entity | Android (Owner) | Web Admin (Owner + Shop Admin) |
|--------|-----------------|--------------------------------|
| **Staff** | **No CRUD UI** — only permission constants (`STAFF_ADD/EDIT/REMOVE`) and read-only `@GET("api/v1/business/staff")`. Role normalization to `SHOP_STAFF` is enforced. | Terminals only in v1; full CRUD in v2.0 |
| **Terminals** | Registers via app; shows pending/active | List, rename, deactivate, approve/reject pending, recover |
| **Terminal Approval** | Device registers → owner approves on Web → terminal activates | Same; Web is approval surface |
| **Device Identity** | `deviceId` (ANDROID_ID-based) + `terminalSeries` + `terminalId` | Shown in terminal list with last active, status |

### 5.9 Daily Closing (Shift Reconciliation)

| Step | Detail |
|------|--------|
| **Trigger** | Owner taps "Close Day" (Android) or Web Admin Daily Closing page. |
| **Window** | Uses **business day** (closing-time boundary), not calendar day. |
| **Cash Settlement** | Expected = Σ cash payments; Actual = counted; Variance = Actual − Expected. |
| **Shift Report** | PDF: sales by mode, item, category; tax summary; cash variance; open tables. |
| **Order Number Reset** | Resets to 1 at business-day rollover. Display IDs are auto-prefixed with `terminalSeries` (`"$terminalSeries$displayCounter"`); bill ref `"$prefix-LOC-$id"`. No configurable prefix in v1. |
| **Grace Buffer** | 30 min past closing (fixed; `SETTLEMENT_BUFFER_MINUTES = 30`). |
| **Offline** | Closing runs on local data; pushes on next sync. |

### 5.10 Reports & Exports

| Report | Android | Web Admin |
|--------|---------|-----------|
| **Sales Overview** | ✅ (today / yesterday / custom) | ✅ (dashboard KPIs + charts) |
| **GST Ledger** | ✅ (PDF/CSV: invoice, date, customer, taxable, CGST, SGST, total) | ✅ (interactive table + CSV export) |
| **Item-wise Sales** | ✅ (qty, amount, category) | ✅ |
| **Payment Mode Summary** | ✅ (cash/UPI/POS/Easebuzz totals) | ✅ |
| **Terminal-wise** | ✅ | ✅ |
| **Daily Closing PDF** | ✅ | ✅ |
| **Invoice PDF (single)** | ✅ (share via WhatsApp) | 🔄 v1.1 (view in browser) |

### 5.11 Multi-Restaurant (Single Owner)

- Owner can own multiple `restaurant_id` entities.
- **No UI switcher in v1** — Android has backend support (`DatabaseProvider.switchToDatabase()`) but no drawer switcher; Web Admin requires separate login per restaurant.
- Each restaurant: independent menu, staff, terminals, tax, printer, closing.
- Sync, billing, reports fully isolated per `restaurant_id`.
- Web Admin KBook Admin sees all businesses; Owner sees only own (per login).

### 5.12 Offline-First Sync Protocol

| Property | Detail |
|----------|--------|
| **Local DB** | Room (SQLite) per restaurant; `TenantDaos` scope by `restaurant_id`. |
| **PushSync** | Bills, payments, stock adjustments → server when `NetworkMonitor.Available`. |
| **PullSync** | Menu, profile, staff, tax, settings → server → local upsert. |
| **Conflict Resolution** | Server wins on master data (menu, profile, tax, staff). Client wins on bills (append-only, immutable after COMPLETED). |
| **Versioning** | Entity `version` + `updatedAt`; `last_sync_timestamp` per restaurant. |
| **Quarantine** | Failed pushes → `sync_quarantine` table; retried on next sync. |
| **Boot Sync** | `BootReceiver` → `MasterSyncWorker` on device boot (if token exists). |
| **Periodic** | `WorkManager` periodic (15 min) + immediate on network change. |

### 5.13 Security & Access Control

| Layer | Mechanism |
|-------|-----------|
| **Auth** | JWT access (15 min) + refresh (30 d); stored in `KeystoreBackedPreferences`. |
| **Device Binding** | `terminal_token` per terminal; invalidated on deactivate. |
| **App Lock** | PIN (SHA-256 hash in keystore); grace period 30 s; bypass for trusted external apps (WhatsApp, dialer, payment). |
| **Session** | No inactivity timeout (GPay-style). Explicit logout only. |
| **Role Enforcement** | Android: `SessionManager.canWriteMasterData()` (Owner only).<br>Server: `@RequireRole` aspect on controllers. |
| **Data Encryption** | Secrets (Easebuzz keys, gateway secrets) encrypted at rest via Android Keystore + server-side AES-GCM. |

---

## 6. Technical Boundaries (for Agents)

| Layer | Constraint |
|-------|------------|
| **Android minSdk** | 24 (Android 7.0) — covers 99%+ active devices in India |
| **TargetSdk** | 35 (Android 15) — edge-to-edge, predictive back, granular permissions |
| **Architecture** | Single-Activity, Compose Navigation, Hilt DI, Room + WorkManager sync |
| **Offline contract** | Bills written locally → `PushSync` when `NetworkMonitor.Available`; no optimistic UI for payments |
| **Sync protocol** | Versioned entity batches; server wins on menu/config; client wins on bills (append-only) |
| **Auth** | JWT (access + refresh); device-bound `terminal_token`; PIN lock local only |
| **Web Admin** | Angular 18, Signals (no RxJS), Astryx v0.7+, standalone components, lazy routes |
| **Server** | Spring Boot 3.3, Java 21, PostgreSQL, Flyway, Spring Security + `RequireRole` aspect |
| **CI/CD** | GitHub Actions: Android (`android-ci.yml`), Web (`web-admin.yml`), Server (`ci.yml`) |
| **Release** | Android: Play Store Internal → Production; Web: `ops/deploy-web.sh` to VPS; Server: `ops/deploy-production.sh` |

---

## 7. Roadmap Signals (Not Commitments)

| Horizon | Theme |
|---------|-------|
| **v1.1** | Web: Device management, Invoice viewer, OCR upload, Marketplace fix |
| **v1.2** | Web: Menu read + search, Staff read, Shop profile read |
| **v2.0** | Web: Menu/Staff/Profile CRUD (new server write APIs); Loyalty lite; Multi-outlet consolidated dashboard |
| **v2.5** | Android: Customer-facing display (second screen); Kitchen display system (KDS) app |
| **v3.0** | Platform: Franchise / multi-brand hierarchy; Central commissary inventory; API for 3rd-party integrators |

---

## 8. Toast & Notification Design (Cross-Platform)

### 8.1 Android (Compose) — `KhanaToast` / `KhanaBookSnackbar`

| Property | Detail |
|----------|--------|
| **Component** | `KhanaToast.show(message, kind, actionLabel?)` → renders via `KhanaBookSnackbar` + `KhanaBookSnackbarHost` (bottom-anchored, above nav bar). |
| **Kinds** | `Success` (green border), `Error` (red), `Warning` (amber), `Info` (gold). |
| **Durations (competitor-benchmarked)** | Success: 2 s · Error: 3.5 s · Warning: 3.5 s · Info: 2 s. Action toasts = indefinite (dismiss or tap action). |
| **Visuals** | Background: `DarkBrown2` (`#2D1010`). 1.5 dp coloured border per kind: SuccessGreen `#388E3C`, DangerRed `#C62828`, WarningYellow `#FFB300`, PrimaryGold `#C8960C`. Content: `TextLight` `#F5E6C8`. Icon (✓ / ! / ⚠ / i) tinted to border colour. Max 3 lines. Optional action button + dismiss ×. |
| **Deduplication** | Same message + kind within 2 s → suppressed. Only one toast visible at a time (new cancels previous). |
| **Accessibility** | `LiveRegionMode.Polite` on snackbar; `role="status"` (info/success/warning) or `role="alert"` (error) via web parity. |
| **Placement** | Bottom, horizontal padding 24 dp (`spacing.large`), vertical 8 dp (`spacing.small`) + nav-bar inset (host adds `navigationBarsPadding()` + `spacing.extraLarge` = 32 dp bottom). |

### 8.2 Android — In-App Notification Banner (`InAppNotificationBanner`)

| Property | Detail |
|----------|--------|
| **Trigger** | Push notification arrives while app is foreground (not on login/setup/lock screens). |
| **Style** | LinkedIn-style slide-down from under status bar; background `DarkBrown2`, 4 dp wide coloured left accent bar + icon, 1 dp full perimeter border, title + optional message (2 lines), dismiss ×. |
| **Accent Colours** | Payment/QR: green `#16A34A` · Refund: red `#EF4444` · KYC: violet `#8B5CF6` · Settlement: blue `#0284C7` · FSSAI expiry: saffron `#F97316` · Default: purple `#7C5CDB`. |
| **Auto-dismiss** | 6 s (configurable via `AUTO_DISMISS_MS`). Tap → deep-link to target screen; dismiss → consume banner. |
| **Animation** | `slideInVertically` + `fadeIn` enter; `slideOutVertically` + `fadeOut` exit. |
| **Placement** | Top, under status bar, horizontal 16 dp, vertical 6 dp, shadow 6 dp. |

### 8.3 Web Admin (Angular + Astryx) — `GlobalToastComponent` / `ToastService`

| Property | Detail |
|----------|--------|
| **Service** | `ToastService` (signal-based): `show(text, type, duration?)`, `error()`, `success()`, `info()`, `dismiss()`. |
| **Types** | `info` (default), `success`, `error`. **No `warning` type** (mapped to `info` if needed). |
| **Durations** | Error: 7 s · Success/Info: 3.5 s (configurable per call). |
| **Visuals** | Fixed bottom-right (1.5 rem), max width 380 px (`width: min(380px, calc(100vw - 2rem))`), rendered as `<aside class="global-toast">`, background `--kb-color-surface-3` (`#FFFFFF`), border `1px solid var(--kb-color-border-strong)`, color `--kb-color-foreground` (`#111827`), circular mark with glyph (! / ✓ / i), dismiss ×. The `.toast` CSS rule (360 px) is **not used** by the component. |
| **Accessibility** | `role="alert"` (error, assertive) / `role="status"` (success/info, polite), `aria-live`, `aria-atomic`, focus-visible outline on close button. |
| **Responsive** | ≤ 480 px: 1 rem bottom/right. |
| **Single-toast** | Only one visible; new call replaces current (timer reset). |

### 8.4 Design Consistency Rules

| Rule | Android | Web |
|------|---------|-----|
| **Colour coding** | 1.5 dp border accent per kind | Border `border-strong` + mark background (error/success) |
| **Icon + glyph** | Material icon per kind (tinted to accent) | Circular mark with ! / ✓ / i |
| **Duration hierarchy** | Error/Warning > Success/Info | Error > Success/Info |
| **Action toasts** | Indefinite (action button) | Not supported (single dismissible) |
| **Deduplication** | 2 s window per message+kind | None (last write wins) |
| **Z-order** | Above nav bar | `--kb-z-toast` (1100) |

---

## 9. Reference Links

- **Android POS docs**: `ANDROID_POS_DOCUMENTATION/README.md`
- **Web Admin spec**: `docs/specs/web-admin-v1-features.md`
- **Architecture decisions**: `docs/adr/`
- **UI design reference**: `docs/android/ANDROID_DESIGN_REFERENCE_PACK.md`
- **Server API** (OpenAPI): `services/server/target/openapi.json` (after build)
- **Component library**: `apps/web-admin/` (Astryx), `apps/android/app/src/main/java/.../core/designsystem/`

---

> **Last updated**: 2026-11-19  
> **Version**: 1.2 (aligned with `main` @ codebase ground truth)  
> **Maintainers**: KhanaBook Core Team — see `AGENTS.md` for AI agent operating rules
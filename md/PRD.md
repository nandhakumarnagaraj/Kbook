# KhanaBook — Product Requirements Document (PRD)

**Document Version:** 2.1.0 (Codebase Aligned)  
**Status:** Single Source of Truth for Product Scope  
**Target Domain:** [https://kbook.iadv.cloud](https://kbook.iadv.cloud) (Web Admin) & Android POS Client  
**Codebase Provenance:** Verified against `apps/web-admin`, `apps/android`, and `services/server`  

---

## 1. Problem Statement & Value Proposition

### 1.1 The Problem
Small, medium, and multi-counter food establishments in India operate in high-turnover, chaotic environments subject to internet dropouts, staff fraud, and reconciliation errors:
* **Connectivity Failures:** Cloud-only POS systems freeze when local internet drops, stopping table turnovers.
* **Payment Misalignment:** Paper static QR codes require cashiers to look at customer phone screens, inviting fake screenshots and unverified transactions.
* **Inventory & Recipe Leakage:** Dishes are sold without tracking underlying ingredient consumption or stock depletion.
* **Complex Multi-Device Coordination:** Counter billers, stewards, and kitchen printers often print colliding bill numbers or out-of-order KOTs.

### 1.2 Value Proposition
KhanaBook delivers an **offline-first restaurant POS and real-time cloud management platform** built for restaurant owners:
1. **100% Offline Billing Resilience:** Local encrypted SQLite Room database on Android terminals maintains uninterrupted counter checkout.
2. **Instant Dynamic UPI Checkout:** Generates bill-specific dynamic QR codes via Easebuzz aggregator with exact bill amounts, auto-reconciling every payment.
3. **Owner Cloud Portal:** Angular 18 Web Admin (`https://kbook.iadv.cloud`) providing real-time sales visibility, menu management, raw material inventory/recipe control, and staff session management.
4. **Automated Daily Shift Reconciliation:** Tracks cash in hand, digital settlements, GST ledgers, and terminal sync health at shift close.

---

## 2. User Roles & Access Surface (Codebase Aligned)

Per architecture migration `V94__collapse_staff_roles`, KhanaBook implements a simplified, hardened 3-role security model:

| Role | Permitted Access Surfaces | Responsibilities & Capabilities |
| :--- | :--- | :--- |
| **`OWNER`** | **Web Admin** & **Android POS** | Full administrative and business control. Can configure restaurants, modify menu prices, manage raw material inventory, view financial/tax reports, manage staff accounts & permissions on Web Admin, and revoke sessions. |
| **`SHOP_STAFF`** | **Android POS Only** *(Rejected at Web Admin login)* | Counter billers, stewards, and kitchen staff. Can create bills, accept cash/UPI payments, print KOTs, and trigger thermal receipts. Cannot access financial reports or Web Admin. |
| **`KBOOK_ADMIN`** | **Internal Platform Only** | Super-administrators managing multi-tenant onboarding, Easebuzz aggregator merchant agreements, platform feature flags, and sub-merchant KYC. |

> **Surface Separation Rule:**
> Staff Management (account creation, PIN assignment, permission delegation, deactivation) is strictly a **Web Admin-only module** for the `OWNER`. The Android POS app does **not** include any staff management screens or controls; it consumes staff profiles via read-only sync exclusively for operator sign-in and bill stamping.

---

## 3. Core Functional Modules

### 3.1 Offline-First Billing & Kitchen Orders (POS)
* **Sub-Second Order Flow:** Cashier adds items, chooses order type (`DINE_IN`, `TAKEAWAY`, `DELIVERY`), and generates bills.
* **KOT Generation:** Dispatches Kitchen Order Tickets directly to thermal printers over USB, Bluetooth, or ESC/POS network interfaces.
* **Zero Collision Identity:** Invoices generated with composite identity `(restaurant_id, device_id, local_id)` and sequential bill numbering.

### 3.2 Dynamic Payments & Easebuzz Gateway
* Automatic sub-merchant onboarding via Easebuzz RBI aggregator API.
* Real-time dynamic UPI QR code rendering on terminal screens.
* Instant webhook payment reconciliation with HMAC SHA-512 signature verification.
* Split payment support (Cash + UPI / Card).

### 3.3 Menu Engineering & Catalog Control
* Categories with server-owned favorites (`is_favourite`) and custom sort ordering (`sort_order`).
* Dishes with food type flags (`VEG`, `NON_VEG`, `EGG`, `BEVERAGE`), variants (Half/Full), and image attachments.
* Real-time availability toggling (`is_available: true/false`) pushed to all terminals.
* Starred dishes (`is_favourite`) pin high-margin items to lead their category on POS screens.
* OCR menu card scanner support for automated initial menu extraction.

### 3.4 Raw Materials & Recipe Inventory (Server & Web Admin)
* Raw material stock ledger (`raw_materials`, `stock_movements`, `purchase_orders`).
* Recipe mapping (`item_recipes`): Automatically deducts ingredient quantities when dishes are billed.
* Vendor management and purchase order tracking.

### 3.5 Terminal Management & Heartbeats
* Up to 5 registered Android hardware terminals per restaurant.
* Terminal roles: Primary Billing Counter, Secondary Counter, Steward Terminal.
* Remote terminal session recovery challenge protocol.

### 3.6 Daily Shift Closing & Tax Audits
* Shift cash drawer opening balance, expected cash, actual counted cash, and variance logging.
* Digital payment reconciliation against Easebuzz payout ledgers.
* Composite GST Tax Ledger generation exportable for accounting compliance.

---

## 4. Scope Boundaries

### In Scope (Authoritative)
* Offline-first GST billing, KOT printing, and thermal receipt dispatch.
* Menu catalog with variants, categories, and availability toggles.
* Easebuzz sub-merchant payments and dynamic UPI QR generation.
* 5-terminal local mesh sync with cloud ingestion.
* Raw material inventory, recipes, and vendor purchase orders.
* Owner Web Admin management and daily shift closing.

### Out of Scope (Removed or Never Planned)
* Third-party delivery marketplace auto-injection (Swiggy/Zomato APIs — schema residue only).
* Public consumer storefront ordering app.
* Standalone kitchen display screen (KDS) tablet hardware (Android POS handles kitchen thermal KOT printing directly).
* Customer loyalty points and rewards programs.

---

## 5. Success Metrics
* **Billing Latency:** < 500ms local order creation on Android terminals.
* **Zero Offline Data Loss:** 100% of offline bills sync cleanly upon network reconnection without sequence collisions.
* **Zero Reconciliation Gap:** Exact 1:1 match between Easebuzz UPI settlements and Web Admin closing reports.

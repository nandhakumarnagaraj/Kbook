# KhanaBook — Product Requirements Document (PRD)

**Document Version:** 2.0.0  
**Status:** Approved Source of Truth  
**Target Domain:** [https://kbook.iadv.cloud](https://kbook.iadv.cloud) / Android POS  

---

## 1. Problem Statement & Value Proposition

### 1.1 The Problem
Small and mid-sized restaurants, quick-service eateries (QSR), and food outlets in India face severe operational friction:
* **Internet Unreliability:** Peak billing hours suffer when broadband or cellular connectivity drops, halting traditional cloud POS systems.
* **Payment Reconciliation Gaps:** Cashiers use static paper UPI QR standees that require manual amount verification, leading to fraud, missed payments, and settlement mismatches.
* **Complex Enterprise Software:** Legacy desktop POS software is clunky, expensive, hard to train staff on, and lacks seamless multi-device or mobile owner monitoring.
* **Compliance Overlap:** Navigating GST slabs, FSSAI license compliance, and daily cash-vs-digital closing requires manual spreadsheet compilation.

### 1.2 Value Proposition
KhanaBook delivers an **offline-first restaurant POS and real-time cloud management platform** built specifically for restaurant owners:
1. **Zero-Downtime Billing:** Local SQLite + Room mesh network on Android terminals keeps billing running 100% offline.
2. **Instant Dynamic UPI Payments:** Generates order-specific dynamic QR codes with exact bill amounts via Easebuzz payment gateway, auto-reconciling every transaction.
3. **Owner Visibility Anywhere:** A lightweight, responsive Web Admin portal (`https://kbook.iadv.cloud`) providing live floor monitoring, menu management, staff RBAC, and tax reporting.
4. **Automated Daily Closing:** One-click day-end reconciliation calculating cash in hand, digital settlements, GST ledgers, and terminal sync status.

---

## 2. Target Users & User Personas

| Persona | Primary Interface | Primary Goals & Jobs to Be Done |
| :--- | :--- | :--- |
| **Restaurant Owner (`OWNER`)** | Web Admin & Android Tablet | Complete business visibility, menu engineering, pricing, staff permissions, daily revenue reports, GST/tax audits, and bank settlement tracking. |
| **Store Manager (`MANAGER`)** | Android Terminal & Web Admin | Shift management, opening/closing cash drawers, resolving order voids/refunds, inventory monitoring, and staff attendance. |
| **Biller / Cashier (`CASHIER`)** | Android Terminal (Handheld/Tablet) | Ultra-fast order entry (under 5 seconds), dynamic QR generation, cash handling, and instant thermal receipt printing. |
| **Kitchen Staff (`KITCHEN`)** | Kitchen Display Screen (KDS) / Ticket Printer | Receiving Kitchen Order Tickets (KOT) in real-time, tracking prep time, and marking items out-of-stock. |

---

## 3. Core User Journeys

### Journey 1: Ultra-Fast Table/Counter Checkout (Cashier)
1. Cashier taps categories and dishes on the POS terminal (or scans barcode/shortcodes).
2. Taps "Charge" -> Selects payment mode (Cash or Dynamic UPI).
3. POS displays a dynamic QR code on screen with exact bill amount.
4. Customer scans QR using GPay/PhonePe/Paytm; webhook/polling confirms receipt in < 2 seconds.
5. Receipt auto-prints to thermal printer, order syncs to cloud in background.

### Journey 2: Owner Menu & Price Management (Owner)
1. Owner logs into `https://kbook.iadv.cloud/login`.
2. Navigates to **Menu** -> views dish list with instant search and category filters.
3. Toggles dish availability `[On]` / `[Off]` with immediate real-time sync to all billing counters.
4. Adds a new dish with name, category, veg/non-veg flag, tax rate, and pricing variants.
5. Marks high-margin dishes with a star `★` to lead the category on POS terminals.

### Journey 3: Daily Shift Closing & Reconciliation (Manager/Owner)
1. At shift end, manager navigates to **Daily Closing**.
2. System auto-calculates total cash collected vs digital UPI settlements from Easebuzz.
3. Cashier enters physical cash count; system highlights any cash drawer variance.
4. Manager reviews voided bills and discounts, then closes the business day.
5. Closing summary is dispatched to owner's WhatsApp and cloud ledger.

---

## 4. Functional Requirements & Feature Matrix

### 4.1 Authentication & Multi-Role Access Control (RBAC)
* Phone number or Email authentication with secure password or OTP verification.
* Strict role hierarchy: `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`.
* Session persistence via secure stateless tokens (`sessionStorage` on Web Admin, encrypted storage on Android).
* Safe session revocation: Ability for Owner to revoke any staff session remotely without deleting their profile.

### 4.2 Menu Engineering & Catalog Management
* Category hierarchy with custom ordering and pin-to-top favorites.
* Dish attributes: Name, photo, category, veg/non-veg indicator, base price, tax rate, short code.
* Variant pricing (e.g., Half / Full, Small / Regular / Large).
* Real-time availability toggling (`Available` / `Unavailable`) syncing instantly across all connected terminals.
* Bulk dish reassignment when moving items between categories.

### 4.3 POS Billing & Offline-First Sync
* Sub-second order creation and item search.
* Multiple order types: Dine-In (table management), Takeaway / Counter, Delivery.
* Split billing, order item customization, notes, and custom discounts (percentage or fixed).
* Offline operation: Orders save locally to Room/SQLite; sync engine pushes batches upon network restoration with monotonic version vector conflict resolution.

### 4.4 Payments & Integrated Settlement
* Integration with Easebuzz payment aggregator for sub-merchant onboarding.
* Generation of dynamic UPI QR codes embedding bill ID and exact amount.
* Automated webhook confirmation and fallback status polling.
* Multi-mode payment support: Cash, UPI, Credit/Debit Cards, Due / Customer Ledger.

### 4.5 Terminals & Hardware Management
* Device registration with unique Hardware ID and Terminal role (e.g. Counter 1, Bar, Kitchen).
* Real-time online/offline status monitoring and heartbeat detection.
* Primary billing counter assignment.
* USB, Bluetooth, and Network (ESC/POS) thermal printer integration.

### 4.6 Reports, Taxes & Business Intelligence
* Real-time dashboard KPI cards: Net Revenue, Order Count, Average Order Value (AOV), Cash vs UPI split.
* Item-wise sales breakdown with margin analysis.
* GST Tax Ledger generation exportable for accounting compliance.
* Audit trail logging all cancelled, voided, or discounted orders with cashier IDs.

---

## 5. Non-Functional Requirements

| Metric / Dimension | Requirement |
| :--- | :--- |
| **Availability** | 99.9% cloud uptime; 100% offline terminal billing availability. |
| **Performance** | Billing transaction completion < 3 seconds; Web Admin page loads < 1.5 seconds. |
| **Security** | TLS 1.3 in transit; AES-256 for credentials at rest; RBI-compliant payment storage (no raw card data stored). |
| **Data Integrity** | Monotonically increasing bill numbers per business without gaps or duplicates across offline terminals. |
| **Responsive Web** | Full desktop (1440×900), tablet (768×1024), and mobile (375×812) compatibility with zero horizontal overflow. |
| **Accessibility** | WCAG 2.1 AA compliant colors (minimum 4.5:1 text contrast ratio) and visible keyboard focus indicators. |

---

## 6. Page & Screen Structure (Web Admin)

1. `/login`: Clean, accessible sign-in with phone/email and password visibility toggle.
2. `/business/dashboard`: Executive overview, KPI metric tiles, terminal counts, hourly revenue charts.
3. `/business/orders`: Live floor order tracker (Live Floor) and searchable historical settlement logs.
4. `/business/menu`: Searchable dish table, category manager, favorites toggle, and Add/Edit Dish modal.
5. `/business/staff`: Employee list, assigned roles, status pills, session revocation, and staff modal.
6. `/business/inventory`: Stock level tracking, low-stock warnings, and reorder triggers.
7. `/business/terminals`: Terminal hardware overview, online status badges, role assignment, and health checks.
8. `/business/daily-closing`: Shift totals, cash drawer verification, digital settlement totals, and day close action.
9. `/business/reports`: Tabbed analytics (Revenue Overview, GST Tax Ledger, Item-Wise Sales).
10. `/business/settings`: Business profile (Shop Name, Address, WhatsApp, Email), GSTIN/FSSAI verification, and printer configs.

---

## 7. Scope Boundaries

### In Scope (MVP)
* Core billing, KOT generation, and ESC/POS thermal printing.
* Dynamic UPI QR generation via Easebuzz.
* Local SQLite terminal storage with background cloud synchronization.
* Web Admin management for Menu, Staff, Orders, Terminals, Daily Closing, and Reports.
* Role-based access control (`OWNER` vs `SHOP_STAFF`).

### Out of Scope (Post-MVP)
* Multi-outlet franchise warehouse inventory sync.
* Direct consumer food delivery marketplace integrations (Swiggy/Zomato auto-order injection).
* Customer loyalty point redemption programs.
* Automated AI voice billing.

---

## 8. Success Criteria
* **Checkout Speed:** Average counter checkout time reduced from 45 seconds to under 15 seconds.
* **Payment Reconciliation:** 100% zero-discrepancy automated reconciliation between Easebuzz settlements and POS daily closing reports.
* **Offline Resilience:** Zero lost orders during complete internet outages lasting up to 24 hours.

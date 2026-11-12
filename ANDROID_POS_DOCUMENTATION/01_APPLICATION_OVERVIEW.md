# 01 Application Overview: KhanaBook Lite POS

## 1. System Mission & Persona
KhanaBook Lite POS is an Android-native point-of-sale solution engineered specifically for Indian restaurants, cafes, bakeries, quick-service restaurants (QSR), and fine dining establishments.

The app acts as the front-line terminal for:
- Cashiers taking orders and settling payments.
- Captains/waitstaff assigning dining tables and sending kitchen orders.
- Restaurant owners configuring closing schedules and reviewing daily revenue.

---

## 2. Core Architectural Tenets

### A. Offline-First Resilience
- Restaurants often suffer from fluctuating broadband or mobile connections during peak dinner rushes.
- KhanaBook operates **100% offline**. Every order, table state, menu catalog item, customer contact, and daily counter is stored in a tenant-partitioned SQLite Room database on the device (`khanabook_lite_db_<restaurantId>`).
- Cashiers can create bills, print thermal receipts, and complete transactions entirely without internet.

### B. Background Bidirectional Cloud Synchronization
- A background `MasterSyncWorker` (driven by Android `WorkManager`) periodically pushes completed bills and cash audit events to the cloud server while pulling catalog and tax changes made on the Web Admin.
- The UI maintains a persistent heartbeat indicator (**"Cloud Synced"**) at the top of the Home dashboard.

### C. Shift & Overnight Business Date Boundaries
- Restaurants frequently operate past midnight (e.g. closing at 12:20 AM or 02:00 AM).
- Standard calendar midnight (`00:00:00`) does **not** reset daily billing counters.
- KhanaBook implements an adaptive `BusinessDate` engine with a configurable **Closing Time** and a **30-Minute Settlement Buffer**. Daily counter `#01` only begins once the buffer period lapses.

### D. Architectural Invariants
- **Staff Management Invariant**: Staff accounts, roles, and access rights are managed exclusively on the Web Admin (`apps/web-admin/src/app/pages/staff`). The Android POS application contains zero staff management screens; it consumes staff data strictly as read-only entities for lock-screen cashier authentication.

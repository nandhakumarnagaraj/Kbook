# Device Testing Session Log

## Session 001: Initial Setup, Dine-In Lifecycle & Multi-Tender Verification

- **Session ID:** SES-20261111-001
- **Date & Time:** 2026-11-11 22:45 – 23:45 IST
- **Target Device ID:** `ZA222J5KT7` (Motorola moto g34 5G)
- **App Version / Package:** `com.piquantservices.khanabooklite.debug`
- **Initial Screen:** Home Dashboard (`SCR-01`)
- **Objective:** Verify live UI billing, Dine-in KOT generation, multi-tender settlement (Cash + UPI), and daily order numbering mechanics.

### Operations Performed
1. Verified connected target device `ZA222J5KT7` via ADB.
2. Navigated to Settings → Shop Profile: verified closing time `12:20 AM` + `30 min buffer` = `12:50 AM` reset cutoff.
3. Settled active Dine-in order `#A02` (₹367.50) using dynamic UPI QR code. Verified invoice `A000057`.
4. Executed full Dine-in lifecycle on `TableOne` with 2 items (`Livetest` + `webtest`). Fired KOT. Generated order `#A04`.
5. Settled order `#A04` (₹367.50) using Cash tender. Verified invoice `A000059`.
6. Verified Orders tab displaying sequential history: `#A04`, `#A03`, `#A02`, `#A01`.
7. Discovered physical USB disconnection of device `ZA222J5KT7` at 23:40 IST (`Present = False`).

### Test Cases Executed
- `TC-FUNC-001` (Dine-in Order Creation & KOT): **PASS**
- `TC-FUNC-002` (Cash Settlement): **PASS**
- `TC-FUNC-003` (Dynamic UPI Settlement): **PASS**
- `TC-FUNC-004` (Order History Tracking): **PASS**
- `TC-BIZ-001` (GST 5% Calculation Parity): **PASS**
- `TC-BIZ-002` (Terminal Prefix & Sequencing): **PASS**
- `TC-BIZ-003` (Settlement Buffer Rollover Card): **PASS**

### Screenshots Captured
- `device_business_day_card.png`
- `device_orders_screen.png`
- `device_upi_qr_screen.png`
- `device_payment_success.png`
- `device_cash_settlement_success.png`
- `device_orders_screen_updated.png`
- `device_orders_screen_final.png`

### Final Application State
- All 4 orders (`#A01` through `#A04`) settled.
- 0 active orders waiting on Home dashboard.
- Device temporarily disconnected from USB; awaiting user reconnection.

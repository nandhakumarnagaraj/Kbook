# Changelog

## [2026-11-11] - Session 001 Verification
### Added
- Created complete `ANDROID_POS_DOCUMENTATION/` knowledge base tree.
- Documented operational manuals for Dine-In, Payments, and Order History.
- Documented complete Dine-In sequence workflow from seating to cash checkout.
- Added verified test cases for functional billing and business logic.
- Recorded live audit log for orders `#A01`, `#A02`, `#A03`, `#A04` on device `ZA222J5KT7`.
- Added `Android POS UI Operations Documentation Policy` to `AGENTS.md`.

### Verified & Resolved
- 30-minute settlement buffer confirmed across Android POS and Server core logic.
- Dynamic UPI QR rendering and cashier cash tendered calculation confirmed on physical hardware.
- Fixed BUG-003: Aligned `ReportsViewModel.kt` date filtering (`Today`, `Yesterday`, `Custom`) to `BusinessDate.windowStart / windowEnd`, preventing midnight shift fragmentation.
- Added dining table identifier (`customerName`) to the Orders list row in `ReportViews.kt`.

# Unverified Assumptions

The following items are defined in specifications or code but pending live hardware verification:

1. **Live Midnight Boundary Rollover**:
   - *Hypothesis:* An order taken at `12:05 AM` will receive order number `#A05` (same business day), while an order taken at `12:51 AM` will reset to `#A01` (new business day).
   - *Current Status:* Code math verified in unit tests; scheduled for live hardware test between 12:01 AM and 12:50 AM IST.

2. **Offline Quarantined Records Recovery**:
   - *Hypothesis:* Records corrupted during sync are stored in `SyncQuarantineEntity` and can be re-pushed via Sync Center.
   - *Current Status:* Unverified on hardware.

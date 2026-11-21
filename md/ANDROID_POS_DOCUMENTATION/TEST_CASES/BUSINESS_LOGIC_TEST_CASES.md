# Business Logic Test Cases

| Test Case ID | Scenario | Verification Rule | Observed Real Behavior | Status |
| :--- | :--- | :--- | :--- | :--- |
| **TC-BIZ-001** | GST 5% Tax Calculation | Item 1 (₹120) + Item 2 (₹230) = ₹350 Subtotal.<br>5% GST on ₹350 = ₹17.50.<br>Grand Total = ₹367.50. | Cart display and transaction summary both showed exactly `₹ 367.50`. | **PASS** |
| **TC-BIZ-002** | Terminal Prefix & Sequential Counter | Orders created on Terminal 1 follow format `#A01`, `#A02`, `#A03`, `#A04` without duplicate counters. | Orders screen verified with `#A01`, `#A02`, `#A03`, `#A04`. | **PASS** |
| **TC-BIZ-003** | Settlement Buffer Rollover Display | Closing time `12:20 AM` + `30 min` buffer produces computed daily reset time of `12:50 AM`. | Shop Profile explicitly displays: *"Daily order number will reset to 01 at 12:50 AM"*. | **PASS** |
| **TC-BIZ-004** | Midnight Boundary Crossover (12:01 AM – 12:50 AM) | Orders placed after calendar midnight but before 12:50 AM remain in the previous day's trading sequence. | Under active test schedule. | **PENDING LIVE BOUNDARY TEST** |

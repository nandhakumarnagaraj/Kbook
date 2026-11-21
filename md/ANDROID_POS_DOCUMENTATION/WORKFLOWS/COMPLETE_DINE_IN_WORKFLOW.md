# Complete Dine-In Workflow: Seating to Cash Settlement

**Workflow ID:** WF-001  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Associated Test Cases:** TC-FUNC-001, TC-PAY-001  

```mermaid
sequenceDiagram
    autonumber
    actor Cashier
    participant UI as POS Interface
    participant DB as Local Room DB
    participant Kitchen as Kitchen Queue

    Cashier->>UI: Tap "Create New Order"
    UI->>Cashier: Display Step 1 (Customer & Table)
    Cashier->>UI: Tap TableOne chip + Continue
    UI->>Cashier: Display Step 2 (Menu Catalog)
    Cashier->>UI: Tap "+ Add" on Livetest (₹120) & webtest (₹230)
    UI->>Cashier: Update Cart (2 items, ₹367.50 with GST)
    Cashier->>UI: Tap "Send to Kitchen (KOT)"
    UI->>Kitchen: Queue KOT event
    UI->>DB: Save draft bill (#A04, Occupied)
    UI->>Cashier: Return to Home (1 active order)
    
    Note over Cashier,UI: Guests finish meal; Cashier opens bill
    Cashier->>UI: Tap Active Orders -> Tap "Payment"
    UI->>Cashier: Display Step 3 Complete Payment (Payable: ₹367.50)
    Cashier->>UI: Select Cash -> Tap "Payment Successful"
    UI->>DB: Update bill status (Completed, Cash)
    UI->>Cashier: Display Step 4 Transaction Summary (Invoice A000059)
    Cashier->>UI: Tap "Back to Home"
    UI->>Cashier: Return to Home (Table released to Available)
```

## Step-by-Step Execution Record

1. **Step 1 (Table Assignment)**:
   - Started on Home dashboard (`SCR-01`).
   - Tapped `Create New Order` at `[182,689][554,741]`.
   - Selected `TableOne` chip (`[56,547][160,572]`) with phone `9150677849`.
   - Tapped `Continue` at `[297,1513][424,1547]`.

2. **Step 2 (Cart Assembly & KOT)**:
   - Added `Livetest` (`₹120.00`) via `+ Add` button (`[241,862][308,887]`).
   - Added `webtest` (`₹230.00`) via `+ Add` button (`[583,861][650,886]`).
   - Cart subtotal computed: ₹350.00 + 5% GST (₹17.50) = **₹367.50**.
   - Tapped `Send to Kitchen (KOT)` at `[384,1508][643,1538]`.
   - App returned to Home, indicating `1 order waiting • 1 Dine-in`.

3. **Step 3 (Payment Collection)**:
   - Tapped `Active Orders` tile (`[161,941][350,975]`).
   - Order card showed `OrderNo: A04`, `TableOne`, `₹ 367.50`.
   - Tapped `Payment` at `[176,422][273,447]`.
   - Complete Payment screen opened with mode defaulted to `Cash`.
   - Tapped `Payment Successful` at `[221,1415][500,1449]`.

4. **Step 4 (Summary & Release)**:
   - Step 4 displayed `Payment Successful!` with Invoice `A000059` and Amount Received `₹ 367.50`.
   - Tapped `Back to Home` at `[290,1513][484,1547]`.
   - Home screen displayed `0 Active Orders`. Table was freed up for the next party.
   - Orders tab displayed `#A04` as `Dine-in · Cash` at `11:12 PM`.

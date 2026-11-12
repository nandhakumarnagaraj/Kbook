# Payment Operations

### Operation: Settle Bill with Cash

**Operation ID:** OP-002  
**Module:** Payments & Settlement  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-11  

**Purpose:**  
Settle a running table bill using Cash tender, compute exact change to return, release the table, and generate the final tax invoice.

**Preconditions:**
- An active Dine-in order exists in `Active Orders` (`SCR-04`).

**Navigation Path:**  
Home → `Active Orders` → Order Card `Payment` → Step 3 (Payment) → `Payment Successful`

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Tap **`Active Orders`** (`[161,941][350,975]`) | Active orders screen opens displaying occupied tables | Card shows `OrderNo: A04`, `TableOne • 9150677849`, `₹ 367.50` |
| 2 | Tap **`Payment`** (`[176,422][273,447]`) | Navigates to Step 3 Complete Payment screen | Shows payable amount `₹367.50`, mode defaulted to `Cash` |
| 3 | Tap **`Payment Successful`** (`[221,1415][500,1449]`) | Completes settlement, moves to Step 4 Transaction Summary | Displays Invoice No `A000059`, Amount `₹ 367.50` |
| 4 | Tap **`Back to Home`** (`[290,1513][484,1547]`) | Returns to Home, releases table to Available | Home screen reflects `0 Active Orders` |

**Screenshots:**  
- Cash Payment Success: `artifacts/android-ui-tests/device_cash_settlement_success.png`

---

### Operation: Settle Bill with Dynamic UPI QR Code

**Operation ID:** OP-003  
**Module:** Payments & Settlement  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-11  

**Purpose:**  
Display a dynamic Bharat UPI QR code on the POS screen encoded with the merchant VPA and exact payable sum for mobile scan-and-pay.

**Preconditions:**
- Terminal is connected to the network or has merchant UPI VPA stored locally.

**Navigation Path:**  
Complete Payment → Payment Mode Dropdown → `UPI` → Dynamic QR Code Display → `Payment Successful`

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Open Payment screen for order (e.g. `#A02`) | Complete Payment screen opens | Shows payable amount `₹367.50` |
| 2 | Tap Payment Mode dropdown (`[28,809][281,839]`) | Dropdown menu opens showing payment modes | Options appear: `Cash`, `UPI`, `POS Machine`, etc. |
| 3 | Tap **`UPI`** | Screen dynamically renders UPI QR Code | Dynamic QR code displayed with merchant VPA and amount |
| 4 | Tap **`Payment Successful`** | Bill settles, displays Transaction Summary | Displays Invoice No `A000057`, Mode `UPI` |

**Screenshots:**  
- UPI QR Screen: `artifacts/android-ui-tests/device_upi_qr_screen.png`
- UPI Success Screen: `artifacts/android-ui-tests/device_payment_success.png`

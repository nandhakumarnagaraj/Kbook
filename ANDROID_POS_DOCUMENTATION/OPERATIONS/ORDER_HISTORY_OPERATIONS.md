# Order History Operations

### Operation: View Daily Order Log & Date Filtering

**Operation ID:** OP-004  
**Module:** Order History & Reports  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-11  

**Purpose:**  
Inspect settled and draft orders for the current business date, verify sequential daily order numbering (`#A01`, `#A02`, `#A03`, `#A04`), and access past dates.

**Preconditions:**
- Terminal is unlocked.

**Navigation Path:**  
Bottom Navigation Bar → **`Orders`** Tab (`[244,1460][475,1600]`)

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Tap **`Orders`** tab at bottom | Orders screen displays with `Today` filter active | Tab bar highlights Orders; table header shows `OrderNo`, `Amount`, `Time`, `Action` |
| 2 | Review order rows | Lists all bills issued during current business day | Live rows observed:<br>• `#A04`: `Dine-in · Cash` • `₹367.50` • `11:12 PM`<br>• `#A03`: `Takeaway · Cash` • `₹367.50` • `10:50 PM`<br>• `#A02`: `Dine-in · UPI` • `₹367.50` • `10:01 PM`<br>• `#A01`: `Dine-in · Cash` • `₹367.50` • `12:25 AM` |

**Screenshots:**  
- Orders Screen: `artifacts/android-ui-tests/device_orders_screen_final.png`

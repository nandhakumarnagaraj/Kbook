# Dine-In Operations

### Operation: Create Dine-In Order & Fire KOT

**Operation ID:** OP-001  
**Module:** Billing & Table Management  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-11  

**Purpose:**  
Assign a dining party to an available table, select food items, and fire a Kitchen Order Ticket (KOT) while leaving the table tab active for later settlement.

**Preconditions:**
- Terminal is unlocked on the **Home** dashboard (`SCR-01`).
- At least one table is available.
- Menu catalog contains active categories and food items.

**Navigation Path:**  
Home → `Create New Order` → Step 1 (Customer & Table) → Step 2 (Menu & Cart) → `Send to Kitchen (KOT)`

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Tap **`Create New Order`** (`[182,689][554,741]`) | App navigates to Step 1 (Customer & Table) | Step 1 screen opens with Dine-In selected by default |
| 2 | Tap table chip (e.g. `TableOne` at `[56,547][160,572]`) | Table name and customer phone populate input fields | `TableOne` and `9150677849` populated in inputs |
| 3 | Tap **`Continue`** (`[297,1513][424,1547]`) | App navigates to Step 2 (Menu Catalog) | Step 2 screen loads category chips and dish grid |
| 4 | Tap **`+ Add`** on `Livetest` (`[241,862][308,887]`) | Item is added to cart, bottom bar updates to `1 Items Added • ₹ 126.00` | Stepper appears showing `1`, cart reflects total with tax |
| 5 | Tap **`+ Add`** on `webtest` (`[583,861][650,886]`) | Second item added, bottom bar updates to `2 Items Added • ₹ 367.50` | Cart displays `2 Items Added • ₹ 367.50` |
| 6 | Tap **`Send to Kitchen (KOT)`** (`[384,1508][643,1538]`) | KOT queued, table marked Occupied, returns to Home dashboard | Home dashboard reflects `3 Orders` and `1 order waiting • 1 Dine-in` |

**Actual Observed Behavior:**  
The operation completed without lag. Order `#A04` was generated in draft state and the Home screen immediately displayed the active order tile.

**Screenshots:**  
- Step 2 Menu & Cart: `artifacts/android-ui-tests/03_order_items_added.png`

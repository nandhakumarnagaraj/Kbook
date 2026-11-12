# Business Settings Operations

### Operation: Inspect Operating Schedule & Settlement Buffer

**Operation ID:** OP-005  
**Module:** Business Settings & Shift Configuration  
**Verification Status:** VERIFIED (Verified on device `ZA222J5KT7`)  
**Last Tested:** 2026-11-11  

**Purpose:**  
Verify the configured restaurant closing time, overnight shift handling, and the 30-minute settlement buffer that dictates when daily order numbers reset.

**Preconditions:**
- Terminal is unlocked.

**Navigation Path:**  
Bottom Navigation Bar → **`Settings`** Tab → **`Restaurant Configuration`** (Shop Profile)

**Step-by-Step Instructions:**

| Step | User Action | Expected UI Result | Verification / Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | Tap **`Settings`** tab (`[489,1460][720,1600]`) | Settings home menu opens | Menu list opens showing Shop, Payment, Printer, Tax |
| 2 | Tap **`Restaurant Configuration`** | Restaurant profile screen opens | Displays store name, operating hours, and Business Day card |
| 3 | Inspect **Business Day** card | Shows closing time and calculated reset boundary | Extracted text confirms:<br>• Closing time: `12:20 AM`<br>• "Daily order number will reset to 01 at 12:50 AM"<br>• "Includes a 30-minute settlement buffer after your 12:20 AM closing time, so the daily order number continues in the same series." |

**Business Logic Rule:**  
For an overnight closing time of `12:20 AM` with a `30-minute buffer`:
- Orders placed between `12:01 AM` and `12:49 AM` **remain in the existing daily series** (`#A04`, `#A05`...).
- Orders placed on or after `12:50 AM` **reset to `#A01`** under the new business date.

**Screenshots:**  
- Business Day Card: `artifacts/android-ui-tests/device_business_day_card.png`

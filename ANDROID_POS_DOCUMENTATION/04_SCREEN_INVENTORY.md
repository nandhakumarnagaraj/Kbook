# 04 Screen Inventory

This catalog lists all verified graphical user interface screens in KhanaBook Lite POS, including primary interactive touch elements.

---

## Screen Inventory Table

| Screen ID | Screen Name | Route / Path | Primary Purpose | Verified Touch Elements & Bounds |
| :--- | :--- | :--- | :--- | :--- |
| **SCR-01** | **Home Dashboard** | `main?tab=0` | Operational overview, stats, launchers | • `Create New Order` (`[182,689][554,741]`)<br>• `Normal Bill` (`[481,504][616,534]`)<br>• `Active Orders` (`[161,941][350,975]`)<br>• `Orders Tab` (`[244,1460][475,1600]`)<br>• `Settings Tab` (`[489,1460][720,1600]`) |
| **SCR-02** | **New Bill: Step 1 (Customer & Table)** | `new_bill?targetStep=1` | Dine-in vs Takeaway type, table and mobile assignment | • Order Type (`Dine-In`, `Takeaway`)<br>• Recent Tables chips (`TableOne` at `[56,547][160,572]`)<br>• Table name input field<br>• WhatsApp Number input field<br>• `Continue` button (`[297,1513][424,1547]`) |
| **SCR-03** | **New Bill: Step 2 (Menu & Cart)** | `new_bill?targetStep=2` | Visual category filter, item addition, quantity steppers | • Category filter chips (`Favourites`, `Sample`)<br>• Dish cards with `+ Add` button (`[241,862][308,887]`)<br>• Quantity steppers (`-`, `+`)<br>• Bottom Cart summary banner<br>• `Send to Kitchen (KOT)` (`[384,1508][643,1538]`) |
| **SCR-04** | **Active Orders Screen** | `active_orders` | Manage running occupied tables & open bills | • Active table cards (e.g. `TableOne • 9150677849`)<br>• `Payment` button (`[176,422][273,447]`)<br>• `More Actions` dropdown |
| **SCR-05** | **New Bill: Step 3 (Complete Payment)** | `new_bill?targetStep=3` | Select payment method, tender cash or show UPI QR | • Payable Amount display<br>• Payment Mode dropdown (`[28,809][281,839]`)<br>• Cash Tendered input field<br>• `Payment Successful` button (`[221,1415][500,1449]`) |
| **SCR-06** | **New Bill: Step 4 (Transaction Summary)** | `new_bill?targetStep=4` | Confirmation of settled invoice and receipt triggers | • Invoice Number label (e.g. `A000059`)<br>• Amount Received metric<br>• `Print Invoice` button (`[298,1373][476,1407]`)<br>• `Share Invoice` button<br>• `Back to Home` button (`[290,1513][484,1547]`) |
| **SCR-07** | **Orders History Screen** | `main?tab=1` | Daily bill sequence review, historical search | • Date filter tabs: `Today`, `Yesterday`, `Custom`<br>• Order row list (`#A01`, `#A02`, `#A03`, `#A04`)<br>• Payment mode badge (`Cash`, `UPI`) |
| **SCR-08** | **Restaurant Configuration** | `main?tab=2&section=shop` | Business operating schedule & settlement buffer | • Closing time selector (e.g. `12:20 AM`)<br>• Settlement Buffer card ("30-minute settlement buffer")<br>• Save Configuration button |

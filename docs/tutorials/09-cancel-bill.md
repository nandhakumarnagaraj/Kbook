# 9. Cancel a Bill

Cancel a bill that was made today — with a reason.

**Rules (enforced by the app):**
- **Today's bills only** — *"Only today's orders can be edited."*
- **Cancelled is permanent** — there is no un-cancel.
- Any staff member can cancel (no extra permission in the app).

---

## Steps

1. Open the **Reports** tab (bottom nav). The **"Report Details"** screen opens
   with **Today** pre-selected and the **Orders Report** toggle active.
2. Find the bill in the list (OrderNo, Mode, Status).
3. Tap the bill's **Status** chip (e.g. *Completed*) → tap **Cancel Order**
   (red).
4. In the **Cancel Order** dialog, select a reason:
   - **Wrong order**
   - **Customer left** (default)
   - **Test bill**
   - **Other** — type the reason in the **Other Reason** field
     (*"Describe the reason..."*).
5. Tap **Cancel Order** to confirm (or **Keep Order** to back out).

The bill's status becomes **Cancelled**, the reason shows under its row
(*"Reason: <reason>"*), and any refunds/reports net it out.

**Kitchen notified automatically:** if a KOT was already sent for this order,
the kitchen printer immediately prints a cancellation slip:

```
*** ORDER CANCELLED ***
```

---

## Cancelling an Active (Unpaid) Order

In pay-after-food mode, an order that hasn't been paid yet can be cancelled
from the **Active Orders** card on Home:

1. Open **Active Orders** → tap **More Actions** on the order.
2. Tap **Cancel Order** → confirm in the **"Cancel active order?"** dialog
   (**Cancel Order** / **Keep Order**).

See [11. Active Orders & KOT](11-active-orders-kot.md).

---

## Other Notes

- A pending online payment on Home (**"Unresolved Payment"** card) can be
  abandoned with its **Cancel** button — that cancels the *payment attempt*,
  not the bill.
- Editing attempts on the wrong bill toast:
  - *"Cancelled orders can't be changed."*
  - *"Paid orders can't be edited."* (for mode changes on bills outside today)
  - *"Only today's orders can be edited."*

Next: [10. Change Payment Mode](10-change-payment-mode.md)

# 8. Normal Bill

Full billing with customer details, all payment modes, split payments, and
payment links.

**Who can do this:** anyone with the *Create Bills* permission (owner and staff
by default). If blocked you'll see **"Access Restricted"**.

---

## Start a Bill

On **Home**, tap **Create Normal Bill** (or **Create New Order** in
pay-after-food mode). The bill screen has steps:
**Customer → Menu → Payment → Success**.

## Step 1 — Customer

1. Pick **Order Type**: **Dine-In** or **Takeaway**.
2. **Dine-In** — enter the **Table name / Customer name** and the customer's
   **WhatsApp Number \*** (mandatory, 10 digits — error: *"Enter 10-digit
   number"*). Tables you've used recently appear as **Recent Tables** chips —
   tap one to refill; open drafts show **Add** / **Settle** buttons.
3. **Takeaway** — **WhatsApp Number \*** (mandatory) + optional **Customer
   Name**. **Recent Customers** chips refill past customers.
4. Tap **Continue** (enabled only when the number is valid).

> The WhatsApp number is how the customer receives their invoice.

## Step 2 — Menu

- Tap items to add; items with variants open a picker.
- Use the **Favourites** chip for your bestsellers, or search
  (**"Search items..."**).
- Tap an added item to change quantity or remove it; add per-item notes.
- *(Pay-after-food dine-in orders)* tap **Send to Kitchen (KOT)** to fire the
  order to the kitchen now, or **Update & Send KOT** after edits — see
  [11. Active Orders & KOT](11-active-orders-kot.md).
- Tap **Proceed to Pay**. Empty cart? You'll get
  *"Add at least one item to proceed"*.

## Step 3 — Payment

The **Payable Amount** card shows the total. Open **Select Payment Mode:** and
choose:

| Mode | What happens |
|------|--------------|
| **Cash** | Default. Record cash received. |
| **UPI** | A **"Scan to Pay"** QR appears (from your [Offline UPI QR](06-payments.md) setting). Tap to enlarge. |
| **POS Machine** | Card machine payment — record it. |
| **Pay Online** | Easebuzz dynamic QR/gateway (owner must have [activated Easebuzz](06-payments.md)). |
| **Send Payment Link** | Creates a link — dialog **"Payment Link Created"** with **WhatsApp**, **SMS**, **Copy**, **Send Email (...)**, or **Keep Pending & Exit**. |
| **Cash + UPI** / **Cash + POS** / **UPI + POS** | Split payment: two amount fields pre-filled 50/50. Validation: *"Sum must equal ₹x (Current: ₹y)"*. |

Then tap **Payment Successful**. (Tap **Payment Failed / Cancelled** instead to
abort — it records the reason "Customer left".)

> **UPI limit:** amounts over ₹1,00,000 auto-switch to a split —
> *"Amount exceeds UPI limit (₹1,00,000). Auto-switched to split payment."*
> with the excess collected as cash.

> **Changing mode mid-bill:** switch freely with the dropdown. If a pending
> online payment locks the screen, tap **Reset / Change Mode** to abandon the
> attempt and pick again.

## Step 4 — Success

**Payment Successful!** shows a TRANSACTION SUMMARY with:

- **Print Invoice** — sends it to your receipt printer
  ([printer setup](05-printer.md)).
- **Share Invoice** — WhatsApp the invoice (only shown when a WhatsApp number
  is present).
- **Back to Home**.

---

## Offline Billing

No internet? You'll see a yellow banner: *"Offline — bill will sync when back
online."* Bills are saved on the device and sync to the cloud automatically in
the background. Everything — printing, reports, cancelling — keeps working
offline.

## Partial Payments

If a bill was half-paid and interrupted, reopening it shows:
*"Partial payment found — Recorded ₹x. Collect only ₹y to complete this
order."* with a **Confirm Remaining Payment** button. Finish collecting the
balance and confirm. See also [11. Active Orders & KOT](11-active-orders-kot.md).

---

## Notes

- There is **no discount field** in billing — discounts aren't part of the app.
- Find an old bill from Home → **Find Bill** (search by Order No, e.g.
  `A000042`, or invoice no.) to reprint or share it.
- Cancelled bills: [9. Cancel a Bill](09-cancel-bill.md).

Next: [9. Cancel a Bill](09-cancel-bill.md)

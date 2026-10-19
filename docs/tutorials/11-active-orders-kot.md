# 11. Active Orders & KOT (Pay After Food)

The dine-in workflow for restaurants that take the order first and collect
payment later: kitchen tickets (KOT), active orders, and reprinting.

**Setup needed:** Billing Mode set to **Pay After Food** in
[Restaurant Profile](02-restaurant-profile.md). Home's main button becomes
**Create New Order** and an **Active Orders** card appears.

---

## Take a Dine-In Order

1. Home → **Create New Order** → Step 1 **Customer**: pick **Dine-In**, enter
   the **Table name / Customer name** + **WhatsApp Number \***. Recent tables
   appear as chips (**Add** / **Settle**).
2. Step 2 **Menu**: add items, tap **Send to Kitchen (KOT)** (toast: "Table
   saved"). The order is now **active** — the customer eats, you get paid later.
3. To add more items later, reopen the order and tap **Update & Send KOT**
   (toast: "Table updated") — only the new items go to the kitchen.

## Active Orders Card & List

The Home **Active Orders** card shows a summary
(*"2 orders waiting   1 Dine-in   1 Takeaway"* — singular *"1 order waiting"*
when there's one). Tap it to open the list:

- Each row shows **OrderNo: A01**, a chip **KOT Pending** or **KOT Sent**,
  and buttons:
  - **Payment** — settle the bill now (opens the payment step).
  - **Recover** — appears when a partial payment was interrupted; finishes it.
  - **More Actions** — open order detail.
- Empty state: *"No active orders right now. Saved table orders will appear
  here."*

## Order Detail Actions

Open an order to get four actions:

| Action | What it does |
|--------|--------------|
| **Modify** | Add/edit items and notes before the KOT update |
| **Update KOT** | Send newly added items to the kitchen (enabled only when there are unsent items) |
| **Payment** | Settle — pick a payment mode and complete the bill |
| **Cancel Order** | Confirm in the **"Cancel active order?"** dialog (**Cancel Order** / **Keep Order**) |

## Settle the Bill

Tap **Payment** → choose the mode (Cash/UPI/POS/split, same as
[Normal Bill](08-normal-bill.md)) → **Payment Successful** → print/share. The
order leaves the Active Orders list.

**Partial payment recovery:** if a payment was interrupted halfway, you'll see
*"Partial payment found — Recorded ₹x. Collect only ₹y to complete this
order."* — collect the balance and tap **Confirm Remaining Payment**.

---

## Reprint KOT

Lost a kitchen ticket? Home → **Reprint KOT** ("Kitchen Order Ticket"):

1. Search by **Order No** (or **Invoice No**) and/or **Select Date**.
2. Optionally filter with the chips **KOT SENT** / **KOT PENDING** /
   **CANCELLED**.
3. Tap **Reprint KOT** (shows "Printing KOT...").

Cancelled orders show a notice on the reprint screen.

---

## Cancellations Print Automatically

Cancelling an order (active or from Reports) makes the kitchen printer print:

```
*** ORDER CANCELLED ***
```

so the kitchen stops preparing it. Cancel steps:
[9. Cancel a Bill](09-cancel-bill.md).

---

## Pay-Before-Food Mode?

Active Orders and KOT buttons are part of pay-after-food only. In the default
**Pay Before Food** mode every bill settles immediately at the payment step,
and Home shows no Active Orders card.

Next: [12. App Settings & Sync](12-app-settings-sync.md)

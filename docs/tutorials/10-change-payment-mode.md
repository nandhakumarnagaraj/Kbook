# 10. Change Payment Mode

Recorded a bill as Cash but the customer actually paid by UPI? Fix it — either
right while billing, or afterwards from Reports.

**Rules:** today's bills only; cancelled bills can't be changed. Any staff
member can do this.

---

## After the Bill Is Made

1. Open the **Reports** tab (**Today** filter) → **Orders Report**.
2. Find the bill and tap its **Mode** chip (shows *Cash*, *UPI*, etc.).
3. Pick the correct mode from the dropdown (the current mode is excluded).
4. Done — the change syncs to the cloud in the background. The reports update
   immediately.

### If the new mode is a split

Choosing **Cash + UPI**, **Cash + POS**, or **UPI + POS** opens an amount
dialog (title = the mode, e.g. "Cash + UPI", showing **Total: ₹x**):

1. Enter the two amounts (e.g. Cash Amount and UPI Amount) — they're the only
   fields; validation: *"Sum must equal ₹x"*.
2. Tap **Confirm**.

---

## During a Bill (Before Confirming)

On the payment step, just open **Select Payment Mode:** and pick another mode —
you can switch freely until you tap **Payment Successful**.

**Exception — pending online payment:** if an online (Easebuzz) attempt is
pending and the controls are locked, tap **Reset / Change Mode** next to the
dropdown. The pending attempt is finalized as failed (reason: "Cancelled by
user to change payment mode") and mode selection re-enables.

**Interrupted online payment on Home:** a bill stuck mid-payment shows an
**"Unresolved Payment"** card with **Resume** (reopen the payment step) or
**Cancel** (abandon the attempt).

---

## Tips

- Find a bill fast from Home → **Find Bill** (search by Order No), then check
  its mode in the result.
- A cancelled bill's mode can't be changed — *"Cancelled orders can't be
  changed."*

Next: [11. Active Orders & KOT](11-active-orders-kot.md)

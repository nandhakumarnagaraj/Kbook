# 5. Printer Setup

Connect thermal printers for customer receipts and kitchen tickets (KOT).

**Who can edit:** owner **or** staff — printer settings are open to both.

**Where:** **Settings** tab → **Printer Configuration**.

---

## The Two Printers

The screen has a **"Receipt and Kitchen Printers"** card with two slots:

| Slot | What it prints |
|------|----------------|
| **Customer Receipt Printer** | Customer bills/invoices. Name defaults to **"No Printer"** until connected. |
| **Kitchen Ticket Printer** | KOT (kitchen order tickets). Helper: *"Receives new and updated KOT items. Receipt printing remains on the customer printer."* |

Each slot has an **enable switch** next to its title.

Below them, a **Print Options** card has a **Mask Customer Phone** toggle
(hides parts of customer numbers on printed receipts).

Tap **Save** after changes → toast **"Printer settings saved"**.

---

## Connect a Printer (per slot)

Tap into a printer slot. You'll see its status (badge: **"No printer"**,
**"Ready"**, **"Connected"**, **"Paper low"**, **"Paper out"**, **"Offline"**…)
and three connection buttons: **USB**, **Wi-Fi**, **Bluetooth**. Then tap
**Test Printer** to confirm — *"Test print sent"* or
*"Test print failed. Check the printer connection."*

### Bluetooth

1. Tap **Bluetooth**. Allow the permission prompts
   (*"Allow permissions to continue"* if denied).
   - If Bluetooth is off, Android asks you to turn it on.
   - On older Android versions a **"Turn on Location"** dialog may appear —
     this is an Android requirement for discovering nearby devices; the app
     never uses your location. Tap **Open Settings** to enable it.
2. A bottom sheet (**"Select Customer Printer"** or **"Select Kitchen Printer"**)
   scans for devices — pick yours from the list (name + MAC address).
3. Toast **"Printer connected"** (or **"Couldn't connect printer"** — move
   closer and retry).

### Wi-Fi

1. Tap **Wi-Fi**. A dialog opens (**"Customer Receipt Wi-Fi Printer"** or
   **"Kitchen Ticket Wi-Fi Printer"**): *"Enter the printer's local-network
   address and raw TCP port. Most thermal printers use port 9100."*
2. Either tap **Auto-Find Wi-Fi Printers** (shows *"Scanning local network..."*
   then **"Found N Printer(s):"** — tap a result to fill in the fields), or
   type the **IP address or host** (e.g. `192.168.1.50`) and **Port**
   (default `9100`).
3. Tap **Save** → toast **"Wi-Fi printer saved"**.

### USB

1. Connect the printer with an OTG cable, tap **USB**.
2. Pick it from the sheet (**"Select Customer USB Printer"** /
   **"Select Kitchen USB Printer"**) — status **"Ready"** or **"Tap to allow"**.
3. Toast **"USB printer saved"**. If blocked: *"USB permission denied. Allow
   access to use this printer."*; if nothing appears:
   *"No USB printers found. Connect one via OTG cable."*

---

## Per-Printer Options

Inside the **Customer Receipt Printer** card you'll also find:

- **Auto Print** — print the receipt automatically after every successful
  payment.
- **Include Logo** — print your shop logo (set in
  [Restaurant Profile](02-restaurant-profile.md)) at the top of the bill.
- **Paper Size** — radio options **58mm** / **80mm** (set per printer; the KOT
  printer has its own setting).

Helper under the connection buttons: *"Contact us to find a compatible
printer."*

---

## Tips

- Set **Test Printer** after every change — it's the fastest way to confirm the
  right printer is selected for the right slot.
- Receipts print from the billing success screen (**Print Invoice**) or from
  **Find Bill** on Home (**Print Receipt**).
- Kitchen tickets print automatically when you **Send to Kitchen (KOT)** —
  see [11. Active Orders & KOT](11-active-orders-kot.md). Cancellations print a
  "*** ORDER CANCELLED ***" slip automatically.
- Lost a ticket? Home → **Reprint KOT**.

Next: [6. Payments Setup](06-payments.md)

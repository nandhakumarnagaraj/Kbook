# KhanaBook User Tutorials

Step-by-step guides for every task in the KhanaBook app. Every button and field name
below is the exact text you see on screen.

## Tutorials

| # | Tutorial | What you'll learn |
|---|----------|-------------------|
| 1 | [Create Account & First Launch](#1-create-account-first-launch) | Sign up, log in, device setup, Quick Setup wizard |
| 2 | [Restaurant Profile](#2-restaurant-profile) | Shop name, logo, address, WhatsApp, billing mode |
| 3 | [Menu](#3-menu) | Categories, items, variants, photos, AI scan |
| 4 | [Tax (GST & FSSAI)](#4-tax-gst-fssai) | FSSAI licence, GSTIN, GST percentage |
| 5 | [Printer Setup](#5-printer-setup) | Receipt & kitchen printers, Bluetooth/Wi-Fi/USB |
| 6 | [Payments Setup](#6-payments-setup) | Cash, UPI QR, POS, Easebuzz online payments |
| 7 | [Quick Bill](#7-quick-bill) | 4-tap billing without customer details |
| 8 | [Normal Bill](#8-normal-bill) | Full billing, payment modes, split payments, payment links |
| 9 | [Cancel a Bill](#9-cancel-a-bill) | Cancel today's orders with a reason |
| 10 | [Change Payment Mode](#10-change-payment-mode) | Fix a payment mode after or during a bill |
| 11 | [Active Orders & KOT](#11-active-orders-kot-pay-after-food) | Pay-after-food workflow, kitchen tickets, reprint KOT |
| 12 | [App Settings & Sync](#12-app-settings-sync) | App lock, password, notifications, Sync Center |
| 13 | [Web-Admin-Only Features](#13-web-admin-only-features) | Staff, refunds, device approval — done in the web panel |

---

## New Shop Quickstart — your first bill in 5 minutes

1. **Install** KhanaBook and tap **Sign Up**. Enter your **Shop Name**, your
   **WhatsApp number**, tap **Send OTP**, enter the 6-digit code (it arrives on
   WhatsApp), set a password, tap **Sign Up**. → [Full guide](#1-create-account-first-launch)
2. The app downloads your shop data (**"Setting up your workspace..."**). If this
   is a brand-new shop, the **Quick Setup** wizard opens: confirm your shop name,
   add up to 10 popular items with prices, tap **Start Billing →**.
3. On Home, tap **Create Quick Bill** (or **Create Normal Bill**), tap your menu
   items, tap **Proceed to Pay**, then **Payment Successful**. Print or share the
   invoice. → [Quick Bill](#7-quick-bill)
4. Later, as the owner, open the **Settings** tab to add GST, pair a printer, and
   turn on UPI payments.

---

## Home Screen Tour

The Home tab is the hub for everything billing.

```
┌─────────────────────────────────────┐
│  Good Morning, <Shop>        [ 🔔 ] │  ← greeting + notification bell
│  Works offline. Sync runs in        │
│  background.                        │
│  [ Cloud Synced ]                   │  ← sync status chip
├─────────────────────────────────────┤
│  Today's Summary                    │
│  Orders │ Avg Order │ KOT Pending   │
├─────────────────────────────────────┤
│  ⚠ alert pill (only when needed)    │
│  "Unresolved payment" / "Sync       │
│  issue" / "N alerts" → tap to open  │
├─────────────────────────────────────┤
│  [ Quick Bill ] [ Normal Bill ]     │  ← billing mode toggle
│                                     │
│   [ Create Quick Bill ]             │  ← main button (label
│                                     │     changes with mode)
├─────────────────────────────────────┤
│  Active Orders   Find Bill          │  ← quick action tiles
│  Reprint KOT     Call Customer      │
└─────────────────────────────────────┘
```

**Toggle pills** — switch between **Quick Bill** (no customer details) and
**Normal Bill** (customer step included). The choice is remembered.

**Main button** — one of:
- **Create Quick Bill** (quick mode on)
- **Create Normal Bill** (normal, pay-before-food)
- **Create New Order** (pay-after-food mode — see
  [Active Orders & KOT](#11-active-orders-kot-pay-after-food))

**Quick action tiles:**
- **Active Orders** — dine-in orders waiting for payment (pay-after-food mode)
- **Find Bill** — "Search previous invoices" by order or invoice number; reprint
  or WhatsApp-share
- **Reprint KOT** — reprint a kitchen ticket by order number
- **Call Customer** — "Dial from saved customers"

**Sync status chip** — what it means:
| Chip | Meaning |
|------|---------|
| `Cloud Synced` / `All data synced to cloud` | Everything is saved to the cloud |
| `N items pending sync` | Working offline or sync running — data is safe on this device |
| `Offline` / `Offline - no internet connection` | No internet; bills sync later automatically |
| `Auth Required` / `Session expired - login required` | Log in again |

**Notification bell** — opens the in-app notification center with filters
**All / Payments / Orders / Alerts** and **Mark all read**.

---

## Who Can Do What (Owner vs Staff)

| Area | Owner | Staff |
|------|-------|-------|
| Create bills, cancel bills, change payment mode | ✅ | ✅ |
| Printer settings, app settings | ✅ | ✅ |
| Restaurant profile, menu, tax, payment settings | ✅ | Read-only |
| Reports (custom dates, PDF export) | ✅ | Today only |
| Staff management, refunds, device approval | — | — (web admin only, see [13](#13-web-admin-only-features)) |

If a staff member opens a locked setting they see a notice like
**"Read-only: only the restaurant owner can edit the shop configuration."**

---

<a id="1-create-account-first-launch"></a>

## 1. Create Account & First Launch

How to sign up, log in, and set up your device on first launch.

> **Note:** Your sign-up WhatsApp number becomes your login and is used for OTP
> verification. The OTP message arrives **on WhatsApp**, not by SMS.

---

### Sign Up (new shop)

1. Open KhanaBook and tap **Sign Up** (or the **"Sign Up"** link at the bottom of
   the login screen).
2. Fill in the form:
   - **Shop Name** — your restaurant's name (e.g. `Sharma's Chai Point`).
   - **WhatsApp** — a 10-digit number. This is where your OTP and invoices go.
     Tap the inline **Send OTP** button once the number is entered.
3. You'll see the toast **"OTP Sent to your WhatsApp!"**. Open WhatsApp, read the
   6-digit code, and enter it in **Enter OTP**. (A countdown shows how long until
   you can resend.)
4. Set a **Password** — minimum 8 characters with an uppercase letter, a digit,
   and a special character. Repeat it in **Confirm Password**.
5. Tap **Sign Up**. You'll see **"Creating Account..."**, then
   **"Account created successfully"**, and you're logged in automatically.

Your restaurant is created right here — only the shop name is needed at this
point. Address, GST, printers, and payments are configured later from Settings.

### Log In (returning user)

1. On the login screen, enter your **Login ID** — the 10-digit phone number you
   signed up with, or your email — and your **Password**.
2. Tap **Log In**.

**Other ways in:**
- **Google** — tap the circular **G** button ("or Continue with") to sign in with
  a Google account.
- **Forgot Password?** — tap the link, enter your **WhatsApp Number**, tap
   **Verify OTP**, then set a **New Password** and **Confirm Password**, and tap
   **Reset Password** → **Done**.

---

### First Launch: Device Setup

After the first login the app prepares your workspace — you'll see
**"Setting up your workspace..."** with stages like **"Downloading your menu..."**
and **"Loading restaurant profile..."**.

#### Brand-new shop (your own shop, no data yet)

The **Quick Setup** wizard opens automatically:

1. **Your Shop** — confirm or edit your **Shop name**.
2. **Your Menu** — add your most popular items: **Item name** and **₹ Price**.
   Tap **Add another item** for up to 10 items. You can also tap **Skip**.
3. Tap **Start Billing →** (loading: **"Setting up your shop..."**).

You can add categories, variants, GST, and printer settings later from the
**Settings** tab — the wizard says exactly that.

#### Joining an existing shop (counter already exists)

If the shop already has registered counters, the **Device Setup** screen appears:

- You'll see **"N counters found for this shop. Is this device replacing a
  previous counter?"** with each counter's name, series, and **Last active** time.
- **Reclaiming a counter:** select a counter from the list and tap
  **Reclaim Selected Counter**. If another device is actively using it, you'll
  see **"This device is already set up as another counter. Contact your shop
  owner."** — the owner has to free it first.
- **New counter:** tap **Register as a New Device Instead**.

> ⚠️ **Owner approval required:** a brand-new device joining an existing shop
> shows **"Waiting for Approval"** with a confirmation number. The shop owner
> must approve it on the **KhanaBook web admin panel** — the app screen updates
> automatically once approved (it re-checks every 15 seconds; you can also tap
> **Check Again**).

#### Other first-launch states

| Screen | What to do |
|--------|------------|
| **Session expired. Please login again.** | Tap **Login Again** and log in |
| Error with **Retry** | Tap **Retry** (usually a network issue) |
| **Setup Complete!** | Automatic — you land on the Home screen |

---

### Troubleshooting

| Problem | Fix |
|---------|-----|
| OTP never arrives | Wait for the countdown, then tap **Resend OTP**. The message is a normal WhatsApp message — check that WhatsApp works on that number. |
| "Shop name too short" | Enter at least a few characters for the shop name. |
| "Passwords do not match" | Re-type both password fields identically. |
| Password rejected | It must be min 8 chars with uppercase, digit, and special character. |
| Stuck on "Waiting for Approval" | Ask the shop owner to approve the device number on the web admin panel, then tap **Check Again**. |

Next: [2. Restaurant Profile](#2-restaurant-profile)

---

<a id="2-restaurant-profile"></a>

## 2. Restaurant Profile

Set your shop name, address, logo, contact details, and billing mode.

**Who can edit:** owner only. Staff see the notice
**"Read-only: only the restaurant owner can edit the shop configuration."**

**Where:** **Settings** tab → **Restaurant Configuration**.

---

### Shop Profile

The screen shows a **Shop Profile** card with:

1. **Logo** — tap **Change Logo** to pick an image from your gallery. The logo
   is printed on customer invoices. (If upload fails you'll see
   **"Logo upload failed..."** — check your connection and retry.)
2. **Shop Name** — your restaurant's display name.
3. **Shop Address** — printed on the invoice.
4. **Whatsapp Number** — changing this requires re-verification:
   tap **Send OTP**, enter the **Enter 6-digit OTP** you receive, then save.
   (You'll be warned to verify before saving.)
5. **Email** — if you signed up with Google, this shows as
   **"Email (Google account)"** and is locked.
6. **Review Link** — a URL printed on the receipt so customers can leave a review.
7. **Invoice Footer** — text printed at the bottom of every bill.

Tap **Save** to apply (you'll see **"Saving..."**, then **"Profile saved"**).

> If you leave with unsaved edits, a dialog asks **"Unsaved Changes"** —
> **"Keep Editing"** or **"Discard"**.

---

### Billing Mode (Pay Before / Pay After Food)

The **Billing Mode** card decides when the customer pays:

| Mode | How it works |
|------|--------------|
| **Pay Before Food** (default) | Customer pays at ordering time. Home shows **Create Normal Bill** / **Create Quick Bill**. |
| **Pay After Food** | Order first, pay later. Home shows **Create New Order**, an **Active Orders** card appears, and dine-in bills get **Send to Kitchen (KOT)** buttons. |

To switch, tap the mode button. If you've set an app PIN you'll see
**"Confirm Billing Mode"** — *"Enter your app PIN to switch to …"*.

> Pay-after-food changes the whole billing flow — see
> [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food).

---

### Notes & Limits

- Whether bills collect the customer's WhatsApp number is **not** set here —
  switch it with the **Quick Bill / Normal Bill** pills on the Home screen.
  See [7. Quick Bill](#7-quick-bill).

- The **Invoice Footer** above is the only custom receipt text you can set in
  the app. A custom tax label (e.g. "Service Charge" instead of "Tax") is set
  in the **web admin panel** — see
  [13. Web-Admin-Only Features](#13-web-admin-only-features).
- Everything here works offline. Edits are saved on the device and sync to the
  cloud in the background.

Next: [3. Menu](#3-menu)

---

<a id="3-menu"></a>

## 3. Menu

Create categories, add items with prices and photos, manage variants, and use
the AI scanner.

**Who can edit:** owner only. Staff see the menu in read-only mode —
*"Viewing menu in read-only mode. Only the restaurant owner or an admin can edit
the menu."*

**Where:** **Settings** tab → **Menu Configuration**.

---

### Choose How to Add Items

On first open you're asked: **"How would you like to add items?"**

- **Manual Entry** — *"Add, view & Edit items one by one"* (Add / View / Edit).
- **Smart AI** — *"Extract from camera gallery, pdf."* (Camera / Gallery / PDF).
  Footer warning: *"AI can make mistakes. Please review before saving."*

The screen also shows stats: **Categories** and **Total Items**.

---

### Add a Category

1. Tap the green **Add Category** chip (first chip in the category row).
2. In the **Add Category** dialog, type the **Category Name** (e.g. "Starters").
3. Tap **Save** → toast **"Category added"**.

#### Rename, reorder, or pin categories

1. Tap the ⋮ menu on the selected category → **Manage Categories**.
2. In the **Manage Categories** dialog:
   - **Reorder** — *"Press and hold, then drag a category to reorder it."*
   - **Rename** — tap the pencil icon next to a name.
   - **Star (pin)** — tap the star icon to pin the category to the top of the
     billing strip; tap a filled star (**"Unpin …"**) to unpin. Owner-only.
3. Tap **Save** — the button reads **"No Changes"** until you actually move a
   category.

---

### Add an Item (Manual Entry)

1. Select the category chip the item belongs to (items are added to the
   **currently selected** category).
2. Tap the gold **Add New Item** button at the bottom.
3. In the **Add New Item** dialog:
   - *(Optional)* tap the photo box **"Tap to add a dish photo"** and pick an
     image from your gallery.
   - **Item Name** — e.g. `Paneer Butter Masala`.
   - **Base Price (₹)** — shown only if the item has no variants.
   - Pick **Veg** or **Non-Veg**.
   - *(Optional)* tap **Add Variant** to add sizes: **Variant Name**
     (e.g. Small / Medium / Large) and **Price (₹)**, then **Add**.
4. Tap **Add Item** → toast **"Item added successfully"**.

> **Duplicate name?** If an item with the same name exists in this category you
> get *"An item with this name already exists in this category. Do you want to
> overwrite/update the existing item?"* with **Cancel** / **Overwrite**.

---

### Edit Items

- **Tap an item row** (or ⋮ → **Edit**) to open the **Edit Item** dialog — change
  name, price, veg/non-veg, photo, variants. Tap **Save**.
- **Availability** — flip the switch on the item row to mark it
  **Available** / **Unavailable** (it disappears from billing when off).
  Read-only devices see a pill instead of a switch.
- **Move to Category** — ⋮ → **Move to Category** (only if other categories
  exist).
- **Favourites** — tap the **star** on an item row to add it to *this till's*
  favourites. Favourites appear as a chip on the billing screen for quick
  re-ordering. (Category pinning is separate and owner-only.)

---

### Smart AI Import

Skip typing — let AI read your printed menu.

1. In Menu Configuration, choose **Smart AI**, then **Camera**, **Gallery**, or
   **PDF**.
2. For Camera: frame the menu (hint: *"Ensure menu is fully in frame, well-lit,
   text in English."*) → **Capture Photo** → **Use Photo** (or **Retake**).
3. Wait for **"Processing..."**.
4. In **"Review Detected Items"**, check each row (**Type**, **Item Name**,
   **Price**), fix anything wrong, then tap **Add N Items**.
   - Duplicate items trigger **"Conflicts Found"** with **Overwrite All** /
     **Merge & Skip** / **Cancel**.
   - Leaving the screen asks **"Discard Items?"** (**Discard** / **Keep Editing**).

---

### Tips

- There is no barcode field for items — barcode scanning is used in billing to
  find items, not to create them.
- The item's category is whatever chip is selected — there is no category
  dropdown inside the item dialog.
- Menu edits work offline and sync in the background.

Next: [4. Tax (GST & FSSAI)](#4-tax-gst-fssai)

---

<a id="4-tax-gst-fssai"></a>

## 4. Tax (GST & FSSAI)

Enter your FSSAI licence and GST details so bills print as tax invoices.

**Who can edit:** owner only. Staff see
**"Read-only: only the restaurant owner can edit tax settings."**

**Where:** **Settings** tab → **Tax Configuration**.

---

### Steps

1. **Country** — pre-selected to **India**.
2. **FSSAI License \*** — enter your 14-digit FSSAI number
   (*"Enter exactly 14 digits"*). Below the field the app shows the licence
   validity: **"FSSAI valid until dd MMM yyyy"**, with a suffix of
   **"N days left"** or **"Expired"** as the date approaches/passes.
3. Turn on the **GST Registered** switch if you charge GST.
4. When GST is on, two more fields appear:
   - **GSTIN \*** — your 15-character GSTIN
     (*"Enter valid 15-character GSTIN format"*).
   - **GST % \*** — the tax percentage (*"Enter whole number from 0 to 100"*).
5. Tap **Save** → toast **"Tax settings saved"**.

Once GST is on, customer bills print as **"Tax Invoice"** with the GSTIN and
tax breakdown.

---

### What Happens When FSSAI Expires

- The app shows a warning countdown (**"N days left"**) before expiry, and
  **"Expired"** after.
- You also get expiry reminder notifications.
- Update the licence number here after renewing — bills keep working.

---

### Notes

- A **custom tax name and percentage** (e.g. printing "Service Charge" instead
  of "Tax") is set in the **web admin panel** under *Tax & Compliance
  Configuration* — shown there only when GST is switched off. The app prints
  the custom label automatically once it syncs down. See
  [13. Web-Admin-Only Features](#13-web-admin-only-features).
- The FSSAI expiry date itself is read from the licence number; you only enter
  the 14 digits.

Next: [5. Printer Setup](#5-printer-setup)

---

<a id="5-printer-setup"></a>

## 5. Printer Setup

Connect thermal printers for customer receipts and kitchen tickets (KOT).

**Who can edit:** owner **or** staff — printer settings are open to both.

**Where:** **Settings** tab → **Printer Configuration**.

---

### The Two Printers

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

### Connect a Printer (per slot)

Tap into a printer slot. You'll see its status (badge: **"No printer"**,
**"Ready"**, **"Connected"**, **"Paper low"**, **"Paper out"**, **"Offline"**…)
and three connection buttons: **USB**, **Wi-Fi**, **Bluetooth**. Then tap
**Test Printer** to confirm — *"Test print sent"* or
*"Test print failed. Check the printer connection."*

#### Bluetooth

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

#### Wi-Fi

1. Tap **Wi-Fi**. A dialog opens (**"Customer Receipt Wi-Fi Printer"** or
   **"Kitchen Ticket Wi-Fi Printer"**): *"Enter the printer's local-network
   address and raw TCP port. Most thermal printers use port 9100."*
2. Either tap **Auto-Find Wi-Fi Printers** (shows *"Scanning local network..."*
   then **"Found N Printer(s):"** — tap a result to fill in the fields), or
   type the **IP address or host** (e.g. `192.168.1.50`) and **Port**
   (default `9100`).
3. Tap **Save** → toast **"Wi-Fi printer saved"**.

#### USB

1. Connect the printer with an OTG cable, tap **USB**.
2. Pick it from the sheet (**"Select Customer USB Printer"** /
   **"Select Kitchen USB Printer"**) — status **"Ready"** or **"Tap to allow"**.
3. Toast **"USB printer saved"**. If blocked: *"USB permission denied. Allow
   access to use this printer."*; if nothing appears:
   *"No USB printers found. Connect one via OTG cable."*

---

### Per-Printer Options

Inside the **Customer Receipt Printer** card you'll also find:

- **Auto Print** — print the receipt automatically after every successful
  payment.
- **Include Logo** — print your shop logo (set in
  [Restaurant Profile](#2-restaurant-profile)) at the top of the bill.
- **Paper Size** — radio options **58mm** / **80mm** (set per printer; the KOT
  printer has its own setting).

Helper under the connection buttons: *"Contact us to find a compatible
printer."*

---

### Tips

- Set **Test Printer** after every change — it's the fastest way to confirm the
  right printer is selected for the right slot.
- Receipts print from the billing success screen (**Print Invoice**) or from
  **Find Bill** on Home (**Print Receipt**).
- Kitchen tickets print automatically when you **Send to Kitchen (KOT)** —
  see [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food). Cancellations print a
  "*** ORDER CANCELLED ***" slip automatically.
- Lost a ticket? Home → **Reprint KOT**.

Next: [6. Payments Setup](#6-payments-setup)

---

<a id="6-payments-setup"></a>

## 6. Payments Setup

Turn on the payment methods you accept: cash, UPI QR, POS machine, and online
payments (Easebuzz).

**Who can edit:** owner only. Staff see
**"Read-only: only the restaurant owner can edit payment settings."**

**Where:** **Settings** tab → **Payment Configuration**.

---

### Payment Methods

Under **"Payment Methods"** (currency is **INR**), toggle what you accept:

| Toggle | What it does |
|--------|--------------|
| **Cash Payment** | Cash bills (on by default) |
| **POS Machine** | Card machine / POS payments |
| **Offline UPI QR** | Shows a static UPI QR on the payment screen — customer scans with any UPI app |
| **Voice Soundbox (Audio Alert)** | Audio cue on payment events |
| **Easebuzz Online** | Online payments (UPI gateway + payment links) |

Enabled methods appear in the **Select Payment Mode:** dropdown on every bill.
Tap **Save** → toast **"Payment settings saved"**.

> There is no separate "Card" toggle — cards are covered by **POS Machine** and
> **Easebuzz Online**.

---

### Offline UPI QR

1. Turn on **Offline UPI QR**.
2. Enter **UPI ID \*** — your VPA, e.g. `cafe@okhdfcbank`
   (hint: *"Format: yourname@bank (e.g. cafe@okhdfcbank)"*).
3. Tap **Test UPI QR Code ↗** to preview the QR the customer will see
   (shows the VPA and a ₹1.00 test amount) → **Done**.
4. **Save**.

During billing, the payment step renders this QR under **"Scan to Pay"** — the
customer scans it and pays; no internet needed on your side.

---

### Easebuzz Online (accept online payments)

Owner-only activation with a guided setup.

#### Activate

1. Turn on **Easebuzz Online** and tap **Save**.
2. Read the readiness line under the toggle — for example:
   - *"Easebuzz onboarding and KYC must be active before payment links can be
     created."*
   - *"Payment link setup is ready. Khanabook commission: 0%; Easebuzz
     processing fees may apply."*

#### Onboard (one time, ~2 minutes)

1. In the payment screen's Easebuzz hub, tap **Setup Online Payments →**
   (*"Link your bank account & PAN to start accepting customer payments via
   Dynamic UPI QR & Payment Gateway in 2 minutes."*).
2. Fill **Business Details** (**Business Type \***, **State \***,
   **Outlet Format \***) — use **Auto-fill Details from FSSAI** if you've
   entered your FSSAI number in [Tax Configuration](#4-tax-gst-fssai) — then
   **Start Registration** / **Next → Bank Details**.
3. Enter **Bank Details**, verify with the **Enter 6-digit OTP**.
4. Sign the merchant agreement and upload compliance documents
   (**FSSAI License — Mandatory for Food Merchants**, **Address Proof 1 —
   Mandatory**, **Address Proof 2 — Optional**).
5. Success: **"Online Payments Active! 🎉"** → **Done**. You can track status
   with **Refresh Status**.

#### Troubleshooting KYC states

| You see | Do this |
|---------|---------|
| **KYC Rejected** | Tap **Edit & Resubmit** with a fresh document |
| **KYC Expired** | Tap **Open Easebuzz KYC Portal ↗** and re-verify |
| *"A submitted document was rejected. Please upload a fresh copy."* | **Re-upload Proof →** |
| *"Your KYC has lapsed, so online payments are paused. Re-verify to resume."* | **Re-verify KYC →** |
| *"Address proof submitted. Payout verification is under review by Easebuzz."* | Wait for review; check **View Uploaded Proofs** |
| *"Payment agreement needs the owner's signature..."* | Open **Payment Agreement ↗** and sign |
| *"Easebuzz account status is unavailable. Reconnect and open this screen again."* | Check internet, reopen the screen |

Once active, bills can be paid via **Pay Online** (dynamic UPI QR / gateway) or
**Send Payment Link** — both covered in
[8. Normal Bill](#8-normal-bill). Payouts settle to your bank (daily 6:00 AM
after payout verification).

---

### Notes

- **Refunds** of online payments are processed by the owner from the **web
  admin panel** — the app shows refund notifications but has no refund button.
  See [13. Web-Admin-Only Features](#13-web-admin-only-features).
- **Billing Mode** (Pay Before Food / Pay After Food) lives in
  [Restaurant Profile](#2-restaurant-profile), not here.

Next: [7. Quick Bill](#7-quick-bill)

---

<a id="7-quick-bill"></a>

## 7. Quick Bill

The fastest way to bill: no customer details, ~4 taps per bill.

Use it for counter/takeaway sales where you don't need the customer's WhatsApp
number.

> **Video:** [How to Create a QuickBill](https://kbook.iadv.cloud/cdn/tutorials/create-quickbill.webm) (0:42)

---

### Turn On Quick Mode

On the **Home** screen, tap the **Quick Bill** pill in the
**[ Quick Bill ] [ Normal Bill ]** toggle.

- This is a shop-level setting — it syncs across all tills — and stays on
  until you switch back to **Normal Bill**. There is no separate switch in
  Settings.
- The main button changes to **Create Quick Bill**.

---

### Make a Quick Bill

1. Tap **Create Quick Bill**. The bill screen opens titled **"Quick Bill"**,
   starting directly on the menu (the customer step is skipped).
2. *(Optional)* toggle **Dine-In / Takeaway** at the top — defaults to Takeaway.
3. Tap menu items to add them (or use the **Favourites** chip for fast
   re-ordering). Items with variants ask you to pick one (Small/Medium/Large).
   Use the quantity stepper for multiple servings.
4. Tap **Proceed to Pay** on the cart card ("n Items Added" + total).
5. Pick the payment mode (defaults to **Cash**) and tap **Payment Successful**.
6. On the **Payment Successful!** screen, tap **Print Invoice** and/or
   **Share Invoice**, then **Back to Home**.

That's it — a bill takes about 4 taps.

### What Quick Mode Skips

- **No customer step** — the customer is recorded as "Quick Bill" with no
  WhatsApp number.
- **No share-by-WhatsApp option** — without a number there's nothing to share
  (printing still works).
- The **Call Customer** tile disappears from Home.

Everything else is identical to a normal bill: payment modes, split payments,
offline billing, reports.

---

### Tips

- Switch back to **Normal Bill** any time from the same pills — the setting is
  remembered for every till.
- Offline? You'll see *"Offline — bill will sync when back online"* — the bill
  is saved on the device and syncs automatically.

Next: [8. Normal Bill](#8-normal-bill)

---

<a id="8-normal-bill"></a>

## 8. Normal Bill

Full billing with customer details, all payment modes, split payments, and
payment links.

**Who can do this:** anyone with the *Create Bills* permission (owner and staff
by default). If blocked you'll see **"Access Restricted"**.

---

### Start a Bill

On **Home**, tap **Create Normal Bill** (or **Create New Order** in
pay-after-food mode). The bill screen has steps:
**Customer → Menu → Payment → Success**.

### Step 1 — Customer

1. Pick **Order Type**: **Dine-In** or **Takeaway**.
2. **Dine-In** — enter the **Table name / Customer name** and the customer's
   **WhatsApp Number \*** (mandatory, 10 digits — error: *"Enter 10-digit
   number"*). Tables you've used recently appear as **Recent Tables** chips —
   tap one to refill; open drafts show **Add** / **Settle** buttons.
3. **Takeaway** — **WhatsApp Number \*** (mandatory) + optional **Customer
   Name**. **Recent Customers** chips refill past customers.
4. Tap **Continue** (enabled only when the number is valid).

> The WhatsApp number is how the customer receives their invoice.

### Step 2 — Menu

- Tap items to add; items with variants open a picker.
- Use the **Favourites** chip for your bestsellers, or search
  (**"Search items..."**).
- Tap an added item to change quantity or remove it; add per-item notes.
- *(Pay-after-food dine-in orders)* tap **Send to Kitchen (KOT)** to fire the
  order to the kitchen now, or **Update & Send KOT** after edits — see
  [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food).
- Tap **Proceed to Pay**. Empty cart? You'll get
  *"Add at least one item to proceed"*.

### Step 3 — Payment

The **Payable Amount** card shows the total. Open **Select Payment Mode:** and
choose:

| Mode | What happens |
|------|--------------|
| **Cash** | Default. Record cash received. |
| **UPI** | A **"Scan to Pay"** QR appears (from your [Offline UPI QR](#offline-upi-qr) setting). Tap to enlarge. |
| **POS Machine** | Card machine payment — record it. |
| **Pay Online** | Easebuzz dynamic QR/gateway (owner must have [activated Easebuzz](#6-payments-setup)). |
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

### Step 4 — Success

**Payment Successful!** shows a TRANSACTION SUMMARY with:

- **Print Invoice** — sends it to your receipt printer
  ([printer setup](#5-printer-setup)).
- **Share Invoice** — WhatsApp the invoice (only shown when a WhatsApp number
  is present).
- **Back to Home**.

---

### Offline Billing

No internet? You'll see a yellow banner: *"Offline — bill will sync when back
online."* Bills are saved on the device and sync to the cloud automatically in
the background. Everything — printing, reports, cancelling — keeps working
offline.

### Partial Payments

If a bill was half-paid and interrupted, reopening it shows:
*"Partial payment found — Recorded ₹x. Collect only ₹y to complete this
order."* with a **Confirm Remaining Payment** button. Finish collecting the
balance and confirm. See also [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food).

---

### Notes

- There is **no discount field** in billing — discounts aren't part of the app.
- Find an old bill from Home → **Find Bill** (search by Order No, e.g.
  `A000042`, or invoice no.) to reprint or share it.
- Cancelled bills: [9. Cancel a Bill](#9-cancel-a-bill).

Next: [9. Cancel a Bill](#9-cancel-a-bill)

---

<a id="9-cancel-a-bill"></a>

## 9. Cancel a Bill

Cancel a bill that was made today — with a reason.

> **Video:** [How to Cancel a Bill & Change Payment Mode](https://kbook.iadv.cloud/cdn/tutorials/cancel-bill-change-payment-mode.webm) (0:27)

**Rules (enforced by the app):**
- **Today's bills only** — *"Only today's orders can be edited."*
- **Cancelled is permanent** — there is no un-cancel.
- Any staff member can cancel (no extra permission in the app).

---

### Steps

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

### Cancelling an Active (Unpaid) Order

In pay-after-food mode, an order that hasn't been paid yet can be cancelled
from the **Active Orders** card on Home:

1. Open **Active Orders** → tap **More Actions** on the order.
2. Tap **Cancel Order** → confirm in the **"Cancel active order?"** dialog
   (**Cancel Order** / **Keep Order**).

See [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food).

---

### Other Notes

- A pending online payment on Home (**"Unresolved Payment"** card) can be
  abandoned with its **Cancel** button — that cancels the *payment attempt*,
  not the bill.
- Editing attempts on the wrong bill toast:
  - *"Cancelled orders can't be changed."*
  - *"Paid orders can't be edited."* (for mode changes on bills outside today)
  - *"Only today's orders can be edited."*

Next: [10. Change Payment Mode](#10-change-payment-mode)

---

<a id="10-change-payment-mode"></a>

## 10. Change Payment Mode

Recorded a bill as Cash but the customer actually paid by UPI? Fix it — either
right while billing, or afterwards from Reports.

> **Video:** [How to Cancel a Bill & Change Payment Mode](https://kbook.iadv.cloud/cdn/tutorials/cancel-bill-change-payment-mode.webm) (0:27) — the same clip also covers changing the payment mode.

**Rules:** today's bills only; cancelled bills can't be changed. Any staff
member can do this.

---

### After the Bill Is Made

1. Open the **Reports** tab (**Today** filter) → **Orders Report**.
2. Find the bill and tap its **Mode** chip (shows *Cash*, *UPI*, etc.).
3. Pick the correct mode from the dropdown (the current mode is excluded).
4. Done — the change syncs to the cloud in the background. The reports update
   immediately.

#### If the new mode is a split

Choosing **Cash + UPI**, **Cash + POS**, or **UPI + POS** opens an amount
dialog (title = the mode, e.g. "Cash + UPI", showing **Total: ₹x**):

1. Enter the two amounts (e.g. Cash Amount and UPI Amount) — they're the only
   fields; validation: *"Sum must equal ₹x"*.
2. Tap **Confirm**.

---

### During a Bill (Before Confirming)

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

### Tips

- Find a bill fast from Home → **Find Bill** (search by Order No), then check
  its mode in the result.
- A cancelled bill's mode can't be changed — *"Cancelled orders can't be
  changed."*

Next: [11. Active Orders & KOT](#11-active-orders-kot-pay-after-food)

---

<a id="11-active-orders-kot-pay-after-food"></a>

## 11. Active Orders & KOT (Pay After Food)

The dine-in workflow for restaurants that take the order first and collect
payment later: kitchen tickets (KOT), active orders, and reprinting.

**Setup needed:** Billing Mode set to **Pay After Food** in
[Restaurant Profile](#2-restaurant-profile). Home's main button becomes
**Create New Order** and an **Active Orders** card appears.

---

### Take a Dine-In Order

1. Home → **Create New Order** → Step 1 **Customer**: pick **Dine-In**, enter
   the **Table name / Customer name** + **WhatsApp Number \***. Recent tables
   appear as chips (**Add** / **Settle**).
2. Step 2 **Menu**: add items, tap **Send to Kitchen (KOT)** (toast: "Table
   saved"). The order is now **active** — the customer eats, you get paid later.
3. To add more items later, reopen the order and tap **Update & Send KOT**
   (toast: "Table updated") — only the new items go to the kitchen.

### Active Orders Card & List

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

### Order Detail Actions

Open an order to get four actions:

| Action | What it does |
|--------|--------------|
| **Modify** | Add/edit items and notes before the KOT update |
| **Update KOT** | Send newly added items to the kitchen (enabled only when there are unsent items) |
| **Payment** | Settle — pick a payment mode and complete the bill |
| **Cancel Order** | Confirm in the **"Cancel active order?"** dialog (**Cancel Order** / **Keep Order**) |

### Settle the Bill

Tap **Payment** → choose the mode (Cash/UPI/POS/split, same as
[Normal Bill](#8-normal-bill)) → **Payment Successful** → print/share. The
order leaves the Active Orders list.

**Partial payment recovery:** if a payment was interrupted halfway, you'll see
*"Partial payment found — Recorded ₹x. Collect only ₹y to complete this
order."* — collect the balance and tap **Confirm Remaining Payment**.

---

### Reprint KOT

Lost a kitchen ticket? Home → **Reprint KOT** ("Kitchen Order Ticket"):

1. Search by **Order No** (or **Invoice No**) and/or **Select Date**.
2. Optionally filter with the chips **KOT SENT** / **KOT PENDING** /
   **CANCELLED**.
3. Tap **Reprint KOT** (shows "Printing KOT...").

Cancelled orders show a notice on the reprint screen.

---

### Cancellations Print Automatically

Cancelling an order (active or from Reports) makes the kitchen printer print:

```
*** ORDER CANCELLED ***
```

so the kitchen stops preparing it. Cancel steps:
[9. Cancel a Bill](#9-cancel-a-bill).

---

### Pay-Before-Food Mode?

Active Orders and KOT buttons are part of pay-after-food only. In the default
**Pay Before Food** mode every bill settles immediately at the payment step,
and Home shows no Active Orders card.

Next: [12. App Settings & Sync](#12-app-settings-sync)

---

<a id="12-app-settings-sync"></a>

## 12. App Settings & Sync

Lock the app with a PIN, change your password, tune feedback sounds, manage
notifications, and check sync health.

**Where:** **Settings** tab → **App Settings**.

---

### Security

#### App Lock

Protect the app with a 4-digit PIN (plus fingerprint/face if your device
supports it — *"Fingerprint, face, or device screen lock is available…"*).

1. Turn on the switch. Status shows **"PIN lock is active"** or
   *"Disabled — anyone can open the app"*.
2. Follow **"Set a new 4-digit PIN"** → **"Confirm your PIN"**.
3. **PIN Options → Change PIN** to change it; disabling requires
   *"Enter current PIN to verify/disable"*.

> The app PIN is also required to **sign out** when there's unsynced data
> (see Sign Out below) and to switch **Billing Mode**.

#### Change Password

A 3-step WhatsApp OTP flow: **Verify your phone** → **Enter the OTP**
(**Send OTP** / **Resend OTP**) → **Set a new password** (**New Password**,
**Confirm New Password**, **Set Password**).

---

### Appearance

**Interaction Feedback** — control the billing feedback cues:
- **Menu item sound** — *"Play a short soft pop when an item is added"*.
- **Touch feedback** — *"Add a subtle tap vibration with the sound"*.
- **Test enabled feedback** — preview the cues.

---

### Data

#### Notifications Preferences

Channel switches grouped **Payments & Transactions**, **System & Security**,
and **Operations**. The app notifies you about received payments, QR orders,
cancellations, refunds, KYC status, settlements, FSSAI expiry, low inventory,
and device events — turn any group off here.

#### Sync Center

Shows cloud sync health and lets you fix stuck data:

| Item | Meaning |
|------|---------|
| **Blocked bills** | Bills the server refused — retry or repair them |
| **Rows needing review** | Conflicted data (quarantine) awaiting a decision |
| **ID conflicts** | Duplicate-ID rows |
| **Retrying now** / **N pending** | Current sync activity |

Buttons: **Retry all**, **Auto-Resolve Conflicts & Sync All** (owner),
**Refresh sync health**, and per-row **Retry** / **Repair** /
**Mark duplicate cancelled**. There's also a **"What this screen does"**
explainer.

**On Home**, sync problems surface as an alert pill:
- *"N items need review before they can sync"* → **Open** Sync Center.
- *"Unresolved payment"* → **Resume** / **Cancel** the pending payment.
- **"Device Clock Time Drift Detected"** — *"clock is off by more than 3
  minutes…"* — fix your device's date/time; wrong clocks cause sync conflicts.

---

### About

- **Help & Support** — *"We're here to help you succeed"*: **Chat on WhatsApp**
  (fastest reply), **Email Support** (kbook@pcts.tech), **Support Hours**
  (Mon – Sat, 10 AM – 7 PM IST).
- **About App** — version, **Privacy Policy**, **Terms of Use**.

---

### Sign Out

Settings → **Sign Out** (red button at the bottom):

- Normal sign-out asks **"Sign Out?"**.
- If there's unsynced data you'll get the **"Unsynced Data Warning"** — the
  app first tries **"Syncing data before sign out..."**; if you insist, it asks
  for your **App PIN** (**Logout Anyway**).

> Always wait for the sync chip on Home to show **"All data synced to cloud"**
> before signing out or handing over a device.

Next: [13. Web-Admin-Only Features](#13-web-admin-only-features)

---

<a id="13-web-admin-only-features"></a>

## 13. Web-Admin-Only Features

Some things are deliberately managed from the **KhanaBook web admin panel**
(browser), not the app. This page tells you where to find them so you don't
search the app for a button that isn't there.

> Admins signed into the app with a platform-admin account see a
> **"Platform administration"** screen: *"Your platform administrator account is
> designed for KhanaBook Web Admin…"* with a button
> **"Open the KhanaBook Web Admin in your browser to continue."**

---

### Staff Management

Creating staff accounts, assigning roles (owner / shop staff), and permissions
— **View Staff, Add Staff, Edit Staff, Deactivate Staff, Manage Permissions** —
happens in the web admin panel.

The app only *enforces* permissions: staff see read-only settings
(*"Read-only: only the restaurant owner can edit…"*) or an
**"Access Restricted"** dialog when they lack a permission.

### Refunds

Recording a refund (including Easebuzz online-payment refunds) is done by the
owner in the web admin panel, in order history: the **Record Manual Refund**
dialog with a **Refund Amount** field — *"Use this only after the money has been
returned outside KhanaBook. This action records the refund."* It needs the
**Process Refunds** permission.

The app:
- shows refund **notifications** ("Refund Updates" channel), and
- nets refunds out in reports,

but has no refund button.

### Approving New Devices

When a new tablet registers to an existing shop, the app shows
**"Waiting for Approval"** with a confirmation number. The owner approves or
rejects it on the web admin panel's **POS Devices & Terminals** page
(**Manage Devices** permission). The app screen updates automatically once
approved. See [1. Create Account & First Launch](#1-create-account-first-launch).

### Tax Details Not in the App

These tax fields sync *into* the app (they affect billing and printed invoices)
but are edited only in the web admin panel, under
**Tax & Compliance Configuration**:

- **Custom Tax Name** and **Custom Tax Percentage (%)** — e.g. printing
  "Service Charge" instead of "Tax". The app prints the custom label
  automatically once it syncs down. A custom tax is an alternative to GST: the
  web panel shows these two fields only when **Enable Goods & Services Tax
  (GST)** is switched *off* there.
- The web panel can also **Verify GSTIN** and **Verify FSSAI** against the
  official registries — the app only stores what you type.

The app's Tax Configuration covers only FSSAI, GST toggle, GSTIN, and GST % —
see [4. Tax (GST & FSSAI)](#4-tax-gst-fssai).

> **Tax-inclusive pricing** exists as a shop setting on the billing engine, but
> it has no switch in the app *or* in the web admin panel. GST/FSSAI/GST %
> are the tax fields you can change yourself.

### Billing-Adjacent Admin Actions

- **Record Manual Refund** — in the web panel's order history:
  *"Use this only after the money has been returned outside KhanaBook. This
  action records the refund"* (Refund Amount field).
- **Suspend/activate** a restaurant or terminal.

---

### Quick Reference

| I want to… | Where |
|------------|-------|
| Add or remove staff, set permissions | Web admin → Staff |
| Refund a payment | Web admin → Order history → **Record Manual Refund** |
| Approve a new tablet/counter | Web admin → POS Devices & Terminals |
| Set a custom tax label on invoices | Web admin → Tax & Compliance Configuration (GST off) |
| Verify a GSTIN or FSSAI number against the registry | Web admin → Tax & Compliance Configuration |

Anything else — profile, menu, tax (FSSAI/GST), printers, payments, billing,
cancels, reports — is done in the app. Start at the
[README index](#khanabook-user-tutorials).

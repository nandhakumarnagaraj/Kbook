# 6. Payments Setup

Turn on the payment methods you accept: cash, UPI QR, POS machine, and online
payments (Easebuzz).

**Who can edit:** owner only. Staff see
**"Read-only: only the restaurant owner can edit payment settings."**

**Where:** **Settings** tab → **Payment Configuration**.

---

## Payment Methods

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

## Offline UPI QR

1. Turn on **Offline UPI QR**.
2. Enter **UPI ID \*** — your VPA, e.g. `cafe@okhdfcbank`
   (hint: *"Format: yourname@bank (e.g. cafe@okhdfcbank)"*).
3. Tap **Test UPI QR Code ↗** to preview the QR the customer will see
   (shows the VPA and a ₹1.00 test amount) → **Done**.
4. **Save**.

During billing, the payment step renders this QR under **"Scan to Pay"** — the
customer scans it and pays; no internet needed on your side.

---

## Easebuzz Online (accept online payments)

Owner-only activation with a guided setup.

### Activate

1. Turn on **Easebuzz Online** and tap **Save**.
2. Read the readiness line under the toggle — for example:
   - *"Easebuzz onboarding and KYC must be active before payment links can be
     created."*
   - *"Payment link setup is ready. Khanabook commission: 0%; Easebuzz
     processing fees may apply."*

### Onboard (one time, ~2 minutes)

1. In the payment screen's Easebuzz hub, tap **Setup Online Payments →**
   (*"Link your bank account & PAN to start accepting customer payments via
   Dynamic UPI QR & Payment Gateway in 2 minutes."*).
2. Fill **Business Details** (**Business Type \***, **State \***,
   **Outlet Format \***) — use **Auto-fill Details from FSSAI** if you've
   entered your FSSAI number in [Tax Configuration](04-tax.md) — then
   **Start Registration** / **Next → Bank Details**.
3. Enter **Bank Details**, verify with the **Enter 6-digit OTP**.
4. Sign the merchant agreement and upload compliance documents
   (**FSSAI License — Mandatory for Food Merchants**, **Address Proof 1 —
   Mandatory**, **Address Proof 2 — Optional**).
5. Success: **"Online Payments Active! 🎉"** → **Done**. You can track status
   with **Refresh Status**.

### Troubleshooting KYC states

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
[8. Normal Bill](08-normal-bill.md). Payouts settle to your bank (daily 6:00 AM
after payout verification).

---

## Notes

- **Refunds** of online payments are processed by the owner from the **web
  admin panel** — the app shows refund notifications but has no refund button.
  See [13. Web-Admin-Only Features](13-web-admin-only.md).
- **Billing Mode** (Pay Before Food / Pay After Food) lives in
  [Restaurant Profile](02-restaurant-profile.md), not here.

Next: [7. Quick Bill](07-quick-bill.md)

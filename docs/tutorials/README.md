# KhanaBook User Tutorials

Step-by-step guides for every task in the KhanaBook app. Every button and field name
below is the exact text you see on screen.

## Tutorials

| # | Tutorial | What you'll learn |
|---|----------|-------------------|
| 1 | [Create Account & First Launch](01-create-account.md) | Sign up, log in, device setup, Quick Setup wizard |
| 2 | [Restaurant Profile](02-restaurant-profile.md) | Shop name, logo, address, WhatsApp, billing mode |
| 3 | [Menu](03-menu.md) | Categories, items, variants, photos, AI scan |
| 4 | [Tax (GST & FSSAI)](04-tax.md) | FSSAI licence, GSTIN, GST percentage |
| 5 | [Printer Setup](05-printer.md) | Receipt & kitchen printers, Bluetooth/Wi-Fi/USB |
| 6 | [Payments Setup](06-payments.md) | Cash, UPI QR, POS, Easebuzz online payments |
| 7 | [Quick Bill](07-quick-bill.md) | 4-tap billing without customer details |
| 8 | [Normal Bill](08-normal-bill.md) | Full billing, payment modes, split payments, payment links |
| 9 | [Cancel a Bill](09-cancel-bill.md) | Cancel today's orders with a reason |
| 10 | [Change Payment Mode](10-change-payment-mode.md) | Fix a payment mode after or during a bill |
| 11 | [Active Orders & KOT](11-active-orders-kot.md) | Pay-after-food workflow, kitchen tickets, reprint KOT |
| 12 | [App Settings & Sync](12-app-settings-sync.md) | App lock, password, notifications, Sync Center |
| 13 | [Web-Admin-Only Features](13-web-admin-only.md) | Staff, refunds, device approval — done in the web panel |

---

## New Shop Quickstart — your first bill in 5 minutes

1. **Install** KhanaBook and tap **Sign Up**. Enter your **Shop Name**, your
   **WhatsApp number**, tap **Send OTP**, enter the 6-digit code (it arrives on
   WhatsApp), set a password, tap **Sign Up**. → [Full guide](01-create-account.md)
2. The app downloads your shop data (**"Setting up your workspace..."**). If this
   is a brand-new shop, the **Quick Setup** wizard opens: confirm your shop name,
   add up to 10 popular items with prices, tap **Start Billing →**.
3. On Home, tap **Create Quick Bill** (or **Create Normal Bill**), tap your menu
   items, tap **Proceed to Pay**, then **Payment Successful**. Print or share the
   invoice. → [Quick Bill](07-quick-bill.md)
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
  [Active Orders & KOT](11-active-orders-kot.md))

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
| Staff management, refunds, device approval | — | — (web admin only, see [13](13-web-admin-only.md)) |

If a staff member opens a locked setting they see a notice like
**"Read-only: only the restaurant owner can edit the shop configuration."**

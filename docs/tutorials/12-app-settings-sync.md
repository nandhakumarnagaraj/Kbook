# 12. App Settings & Sync

Lock the app with a PIN, change your password, tune feedback sounds, manage
notifications, and check sync health.

**Where:** **Settings** tab → **App Settings**.

---

## Security

### App Lock

Protect the app with a 4-digit PIN (plus fingerprint/face if your device
supports it — *"Fingerprint, face, or device screen lock is available…"*).

1. Turn on the switch. Status shows **"PIN lock is active"** or
   *"Disabled — anyone can open the app"*.
2. Follow **"Set a new 4-digit PIN"** → **"Confirm your PIN"**.
3. **PIN Options → Change PIN** to change it; disabling requires
   *"Enter current PIN to verify/disable"*.

> The app PIN is also required to **sign out** when there's unsynced data
> (see Sign Out below) and to switch **Billing Mode**.

### Change Password

A 3-step WhatsApp OTP flow: **Verify your phone** → **Enter the OTP**
(**Send OTP** / **Resend OTP**) → **Set a new password** (**New Password**,
**Confirm New Password**, **Set Password**).

---

## Appearance

**Interaction Feedback** — control the billing feedback cues:
- **Menu item sound** — *"Play a short soft pop when an item is added"*.
- **Touch feedback** — *"Add a subtle tap vibration with the sound"*.
- **Test enabled feedback** — preview the cues.

---

## Data

### Notifications Preferences

Channel switches grouped **Payments & Transactions**, **System & Security**,
and **Operations**. The app notifies you about received payments, QR orders,
cancellations, refunds, KYC status, settlements, FSSAI expiry, low inventory,
and device events — turn any group off here.

### Sync Center

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

## About

- **Help & Support** — *"We're here to help you succeed"*: **Chat on WhatsApp**
  (fastest reply), **Email Support** (kbook@pcts.tech), **Support Hours**
  (Mon – Sat, 10 AM – 7 PM IST).
- **About App** — version, **Privacy Policy**, **Terms of Use**.

---

## Sign Out

Settings → **Sign Out** (red button at the bottom):

- Normal sign-out asks **"Sign Out?"**.
- If there's unsynced data you'll get the **"Unsynced Data Warning"** — the
  app first tries **"Syncing data before sign out..."**; if you insist, it asks
  for your **App PIN** (**Logout Anyway**).

> Always wait for the sync chip on Home to show **"All data synced to cloud"**
> before signing out or handing over a device.

Next: [13. Web-Admin-Only Features](13-web-admin-only.md)

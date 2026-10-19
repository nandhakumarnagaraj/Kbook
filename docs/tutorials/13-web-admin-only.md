# 13. Web-Admin-Only Features

Some things are deliberately managed from the **KhanaBook web admin panel**
(browser), not the app. This page tells you where to find them so you don't
search the app for a button that isn't there.

> Admins signed into the app with a platform-admin account see a
> **"Platform administration"** screen: *"Your platform administrator account is
> designed for KhanaBook Web Admin…"* with a button
> **"Open the KhanaBook Web Admin in your browser to continue."**

---

## Staff Management

Creating staff accounts, assigning roles (owner / shop staff), and permissions
— **View Staff, Add Staff, Edit Staff, Deactivate Staff, Manage Permissions** —
happens in the web admin panel.

The app only *enforces* permissions: staff see read-only settings
(*"Read-only: only the restaurant owner can edit…"*) or an
**"Access Restricted"** dialog when they lack a permission.

## Refunds

Recording a refund (including Easebuzz online-payment refunds) is done by the
owner in the web admin panel, in order history: the **Record Manual Refund**
dialog with a **Refund Amount** field — *"Use this only after the money has been
returned outside KhanaBook. This action records the refund."* It needs the
**Process Refunds** permission.

The app:
- shows refund **notifications** ("Refund Updates" channel), and
- nets refunds out in reports,

but has no refund button.

## Approving New Devices

When a new tablet registers to an existing shop, the app shows
**"Waiting for Approval"** with a confirmation number. The owner approves or
rejects it on the web admin panel's **POS Devices & Terminals** page
(**Manage Devices** permission). The app screen updates automatically once
approved. See [1. Create Account & First Launch](01-create-account.md).

## Tax Details Not in the App

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
see [4. Tax (GST & FSSAI)](04-tax.md).

> **Tax-inclusive pricing** exists as a shop setting on the billing engine, but
> it has no switch in the app *or* in the web admin panel. GST/FSSAI/GST %
> are the tax fields you can change yourself.

## Billing-Adjacent Admin Actions

- **Record Manual Refund** — in the web panel's order history:
  *"Use this only after the money has been returned outside KhanaBook. This
  action records the refund"* (Refund Amount field).
- **Suspend/activate** a restaurant or terminal.

---

## Quick Reference

| I want to… | Where |
|------------|-------|
| Add or remove staff, set permissions | Web admin → Staff |
| Refund a payment | Web admin → Order history → **Record Manual Refund** |
| Approve a new tablet/counter | Web admin → POS Devices & Terminals |
| Set a custom tax label on invoices | Web admin → Tax & Compliance Configuration (GST off) |
| Verify a GSTIN or FSSAI number against the registry | Web admin → Tax & Compliance Configuration |

Anything else — profile, menu, tax (FSSAI/GST), printers, payments, billing,
cancels, reports — is done in the app. Start at the
[README index](README.md).

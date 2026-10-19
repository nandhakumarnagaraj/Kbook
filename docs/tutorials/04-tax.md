# 4. Tax (GST & FSSAI)

Enter your FSSAI licence and GST details so bills print as tax invoices.

**Who can edit:** owner only. Staff see
**"Read-only: only the restaurant owner can edit tax settings."**

**Where:** **Settings** tab → **Tax Configuration**.

---

## Steps

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

## What Happens When FSSAI Expires

- The app shows a warning countdown (**"N days left"**) before expiry, and
  **"Expired"** after.
- You also get expiry reminder notifications.
- Update the licence number here after renewing — bills keep working.

---

## Notes

- A **custom tax name and percentage** (e.g. printing "Service Charge" instead
  of "Tax") is set in the **web admin panel** under *Tax & Compliance
  Configuration* — shown there only when GST is switched off. The app prints
  the custom label automatically once it syncs down. See
  [13. Web-Admin-Only Features](13-web-admin-only.md).
- The FSSAI expiry date itself is read from the licence number; you only enter
  the 14 digits.

Next: [5. Printer Setup](05-printer.md)

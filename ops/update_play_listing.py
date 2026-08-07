"""
Update KhanaBook Play Store listing via Google Play Developer API.
Uses service account credentials from ~/.config/khanabook/play-publisher.json
"""
import json
from googleapiclient.discovery import build
from google.oauth2 import service_account

PACKAGE_NAME = "com.piquantservices.khanabooklite"
SERVICE_ACCOUNT_FILE = r"C:\Users\nandh\.config\khanabook\play-publisher.json"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

SHORT_DESCRIPTION = "Free offline restaurant billing — GST, KOT printer, UPI QR, multi-device sync"

FULL_DESCRIPTION = """India's only FREE restaurant POS that works even when WiFi goes down.

Take orders, print KOT to kitchen, generate GST bills, and accept Cash/UPI/Card — all without internet. Your data syncs automatically when connectivity returns.

No monthly fees. No hardware to buy. Works on any Android phone.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⚡ WHY RESTAURANT OWNERS CHOOSE KHANABOOK

🔌 WORKS OFFLINE — WiFi down during dinner rush? Keep billing. Sync later.
📱 YOUR PHONE IS YOUR POS — No tablet, computer, or special hardware needed
🖨️ BLUETOOTH PRINTER — Any ₹1,500 thermal printer = complete POS setup
📷 PHOTO MENU IMPORT — Photograph your paper menu, items added in seconds
👥 5 DEVICES, 1 ACCOUNT — Counter + kitchen + owner phone, all synced live
🧾 GST INVOICES + KOT — Proper CGST/SGST tax bills + Kitchen Order Tickets
💸 UPI QR ON BILL — Customer scans QR, pays instantly, bill marked paid
📊 DAILY REPORTS — Day-end cash total, UPI collected, payment breakdowns
🔒 BANK-LEVEL SECURITY — Encrypted database + PIN/biometric app lock

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 COMPLETE FEATURE LIST

BILLING
• Dine-in and takeaway orders
• Keep orders open until payment
• Cash, UPI, Card, and split payments
• Generate UPI QR codes for instant payment
• Add items to active orders anytime
• Search and reprint previous bills

KITCHEN MANAGEMENT
• Automatic KOT printing to kitchen printer
• NEW / ADD / VOID kitchen tickets
• Separate billing and kitchen printers
• 58mm and 80mm thermal printers supported

MULTI-DEVICE (up to 5 terminals)
• All terminals sync menus, orders, and payments
• Each terminal gets its own invoice series (A1, A2, A3)
• New devices need owner approval — no unauthorized access
• Rename, deactivate, or recover terminals anytime

MENU MANAGEMENT
• Categories, items, variants, and pricing
• Mark items available/unavailable instantly
• OCR import — camera, gallery, or PDF menu scan
• Review and edit before saving

REPORTS & TAX
• Daily, weekly, monthly, and custom date reports
• Sales breakdown by payment method
• Download and share PDF reports
• GST configuration (CGST + SGST)
• GSTIN and FSSAI details on invoices
• Custom logo and invoice footer

SECURITY
• Encrypted local database (SQLCipher)
• PIN and biometric app lock
• Secure terminal registration
• Owner-approved device access only

DIGITAL INVOICES
• Share bills via WhatsApp
• Share via SMS
• Generate PDF invoices
• Hosted invoice links for customers

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🏪 PERFECT FOR
• Restaurants & dhabas
• Cafes & coffee shops
• Bakeries & sweet shops
• Quick-service restaurants (QSR)
• Food trucks & stalls
• Juice shops & ice cream parlours
• Cloud kitchens
• Any food business in India

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🆓 100% FREE — No trial period. No "upgrade to continue." No hidden charges.

Internet needed for: sign-in, device registration, sync.
Works without internet for: billing, printing, reports, order search.

Questions? WhatsApp us: +91 94716 76935"""


def main():
    credentials = service_account.Credentials.from_service_account_file(
        SERVICE_ACCOUNT_FILE, scopes=SCOPES
    )
    service = build("androidpublisher", "v3", credentials=credentials)

    # Create an edit
    edit_request = service.edits().insert(body={}, packageName=PACKAGE_NAME)
    edit = edit_request.execute()
    edit_id = edit["id"]
    print(f"Created edit: {edit_id}")

    # Update the listing for en-IN (Indian English)
    listing_body = {
        "language": "en-IN",
        "title": "KhanaBook Lite",
        "shortDescription": SHORT_DESCRIPTION,
        "fullDescription": FULL_DESCRIPTION,
    }

    service.edits().listings().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        language="en-IN",
        body=listing_body,
    ).execute()
    print("Updated en-IN listing")

    # Also update default (en-US) listing
    listing_body["language"] = "en-US"
    service.edits().listings().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        language="en-US",
        body=listing_body,
    ).execute()
    print("Updated en-US listing")

    # Commit the edit
    service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print("✅ Play Store listing updated successfully!")


if __name__ == "__main__":
    main()

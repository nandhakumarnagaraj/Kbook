"""Update release notes for the current production track."""
import json
from googleapiclient.discovery import build
from google.oauth2 import service_account

PACKAGE_NAME = "com.piquantservices.khanabooklite"
SERVICE_ACCOUNT_FILE = r"C:\Users\nandh\.config\khanabook\play-publisher.json"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

RELEASE_NOTES_EN = """What's new:

- Multi-device sync — up to 5 terminals on one account
- UPI QR code on bills for instant payment
- Kitchen Order Tickets (KOT): NEW, ADD, VOID
- OCR menu import — photo your paper menu to add items
- Cart validation — blocks billing unavailable items
- Faster first-login setup
- Security: short-lived tokens + encrypted database
- Offline billing reliability improvements"""

credentials = service_account.Credentials.from_service_account_file(
    SERVICE_ACCOUNT_FILE, scopes=SCOPES
)
service = build("androidpublisher", "v3", credentials=credentials)

edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
edit_id = edit["id"]

# Get current production track
track = service.edits().tracks().get(
    packageName=PACKAGE_NAME, editId=edit_id, track="production"
).execute()

print("Current track:", json.dumps(track, indent=2, default=str)[:500])

# Update release notes on existing release
if track.get("releases"):
    release = track["releases"][0]
    release["releaseNotes"] = [
        {"language": "en-US", "text": RELEASE_NOTES_EN},
        {"language": "en-IN", "text": RELEASE_NOTES_EN},
    ]

    service.edits().tracks().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        track="production",
        body=track,
    ).execute()
    print("Updated release notes")

    service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print("Committed. Release notes updated!")
else:
    print("No releases found on production track.")
    service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()

# Known Limitations

1. **Hardware USB Cable Stability**: Physical cable disconnects terminate ADB sessions. Reconnecting USB and re-enabling USB debugging is required if the connection drops.
2. **Staff Management Access**: Staff accounts cannot be created or edited on the Android POS; this is an intentional system architecture rule (Web Admin only).
3. **Receipt Printing Hardware**: ESC/POS thermal printing relies on paired Bluetooth or USB printer devices. In the absence of a paired physical printer, KOTs safely queue in the local SQLite Room database.

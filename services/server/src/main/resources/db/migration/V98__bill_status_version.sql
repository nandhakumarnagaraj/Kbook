-- Deliberate bill status / payment-mode edits vs stale last-write-wins pushes.
--
-- Bills sync last-write-wins by timestamp, so BillSyncService.protectBillState had to
-- refuse every transition out of a finalized state (completed/paid) to stop a stale
-- offline device from silently reverting a gateway-confirmed payment. That also blocked
-- legitimate edits — cancelling a completed bill appeared to work on the device and was
-- then quietly undone on the next pull.
--
-- status_version makes intent explicit (object versioning instead of trusting clocks):
-- the device increments it only on a deliberate user action, so the server can allow a
-- transition when the incoming version is strictly newer and keep reverting when it is not.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS status_version INTEGER NOT NULL DEFAULT 0;

-- Move shop logo + offline UPI QR to server-side CDN storage and add a
-- per-restaurant invoice footer. Files live under
-- /var/www/cdn.kbook.iadv.cloud/restaurants/{id}/ and are served from
-- https://cdn.kbook.iadv.cloud/. Legacy logo_path / upi_qr_path columns are
-- kept for one release so existing devices can migrate their local images.

ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS logo_url        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS upi_qr_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_version    INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS upi_qr_version  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS invoice_footer  TEXT;

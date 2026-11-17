-- ==============================================================================
-- Migration V126: Drop 12 unwanted/dead columns from restaurantprofiles
-- ==============================================================================
-- In accordance with docs/engineering/MIGRATION_SAFETY.md:
-- 1. All operations are idempotent (DROP COLUMN IF EXISTS).
-- 2. Pre-flight backup taken and verified: kbook_saas_20261117T131438Z.sql.gz.
-- 3. Audit confirms:
--    - upi_qr_path, upi_qr_url, upi_qr_version: 100% NULL (superseded by dynamic on-device QR via upi_handle)
--    - upi_mobile: superseded by upi_handle; Android sets null on save
--    - email_invoice_consent: 100% false/null; no UI toggle
--    - custom_tax_name, custom_tax_number, custom_tax_percentage: 100% NULL/0; no UI input
--    - print_customer_whatsapp: 100% NULL; no UI toggle, handled locally on Android
--    - custom_welcome_message, custom_fssai_message: 100% NULL; no UI input
--    - gst_expiry_date: 100% NULL; GST registration does not expire in India
-- ==============================================================================

-- 1. Obsolete static QR image columns & redundant upi_mobile
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS upi_qr_path;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS upi_qr_url;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS upi_qr_version;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS upi_mobile;

-- 2. Dead email invoice consent
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS email_invoice_consent;

-- 3. Dead custom tax columns
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS custom_tax_name;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS custom_tax_number;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS custom_tax_percentage;

-- 4. Dead print preference
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS print_customer_whatsapp;

-- 5. Dead custom message templates
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS custom_welcome_message;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS custom_fssai_message;

-- 6. Bogus GST expiry date
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS gst_expiry_date;

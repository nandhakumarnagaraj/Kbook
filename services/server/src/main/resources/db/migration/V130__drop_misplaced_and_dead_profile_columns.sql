-- V130__drop_misplaced_and_dead_profile_columns.sql
-- Drop misplaced hardware print settings, dead local paths, redundant country/tax columns,
-- and unused compliance baggage from restaurantprofiles and bills.
--
-- WHY:
-- 1. Printing is 100% device-local to the Android POS hardware (printer_profiles in SQLite).
--    restaurantprofiles.auto_print_on_success and include_logo_in_print are dead duplicate columns.
-- 2. Print formatting (mask_customer_phone, show_branding) is handled by Android thermal invoice
--    formatters; they are 77.2% NULL in PostgreSQL and unused by the cloud server.
-- 3. logo_path is legacy local disk path storage (82% NULL). All active logos use logo_url,
--    which already encodes the version in the filename (e.g., logo_v1.webp).
-- 4. KhanBook SaaS is built exclusively for Indian restaurants (GST + INR).
--    bills.custom_tax_amount is 0.00 across all 2,286 rows. country is always 'India'.
-- 5. Easebuzz was purged in V129, but easebuzz_enabled remained (96.4% NULL, 0 true).
-- 6. FSSAI renewals are tracked via fssai_trackers table (V128), but fssai_expiry_date in profile remained (100% NULL).
-- 7. bills.created_by_user_id is dead (created_by is active).

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

-- 1. Drop dead/misplaced columns from restaurantprofiles (8 columns)
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS logo_path;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS auto_print_on_success;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS include_logo_in_print;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS country;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS show_branding;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS mask_customer_phone;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS easebuzz_enabled;
ALTER TABLE restaurantprofiles DROP COLUMN IF EXISTS fssai_expiry_date;

-- 2. Drop index and dead columns from bills (2 columns)
DROP INDEX IF EXISTS idx_bills_created_by_user;
ALTER TABLE bills DROP COLUMN IF EXISTS custom_tax_amount;
ALTER TABLE bills DROP COLUMN IF EXISTS created_by_user_id;

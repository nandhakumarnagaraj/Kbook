-- KhanaBook DB Data Reset Script
-- Note: This truncates data tables but preserves the restaurant_profile to avoid re-configuration.

TRUNCATE TABLE bill_payments CASCADE;
TRUNCATE TABLE bill_items CASCADE;
TRUNCATE TABLE bills CASCADE;
TRUNCATE TABLE stock_logs CASCADE;
TRUNCATE TABLE item_variants CASCADE;
TRUNCATE TABLE menu_items CASCADE;
TRUNCATE TABLE categories CASCADE;
-- TRUNCATE TABLE users CASCADE; -- Uncomment if you want to clear users too (requires re-registration)

-- If you want to restart IDs from 1
ALTER SEQUENCE bill_payments_id_seq RESTART WITH 1;
ALTER SEQUENCE bill_items_id_seq RESTART WITH 1;
ALTER SEQUENCE bills_id_seq RESTART WITH 1;
ALTER SEQUENCE stock_logs_id_seq RESTART WITH 1;
ALTER SEQUENCE item_variants_id_seq RESTART WITH 1;
ALTER SEQUENCE menu_items_id_seq RESTART WITH 1;
ALTER SEQUENCE categories_id_seq RESTART WITH 1;

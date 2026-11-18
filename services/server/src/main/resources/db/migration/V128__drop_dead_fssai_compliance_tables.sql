-- V128__drop_dead_fssai_compliance_tables.sql
-- Purge 2 dead compliance tables: fssai_tracker and fssai_renewals.
--
-- WHY:
-- 1. Real FSSAI license details are stored directly on `restaurantprofiles`
--    (`fssai_number` and `fssai_expiry_date`), which are printed on bills and
--    edited in restaurant settings.
-- 2. `fssai_renewals` has 0 rows in production (paid gateway renewals were never used).
-- 3. `fssai_tracker` has 3 legacy test rows with status UNKNOWN.
-- 4. Android POS and Web-Admin have 0 references to these tables.
-- 5. Zero foreign keys exist to or from these tables.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

DROP TABLE IF EXISTS fssai_renewals CASCADE;
DROP TABLE IF EXISTS fssai_tracker CASCADE;

-- V127__drop_dormant_inventory_and_crm_tables.sql
-- Drop 7 dormant raw material inventory and CRM tables.
-- All 7 tables have 0 rows in production, 0 foreign keys from active core tables,
-- and are not part of the active restaurant POS workflow.
-- Android POS already removed raw_materials from Room in AppDatabase.kt.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

DROP TABLE IF EXISTS purchase_order_items CASCADE;
DROP TABLE IF EXISTS purchase_orders CASCADE;
DROP TABLE IF EXISTS stock_movements CASCADE;
DROP TABLE IF EXISTS item_recipes CASCADE;
DROP TABLE IF EXISTS raw_materials CASCADE;
DROP TABLE IF EXISTS vendors CASCADE;
DROP TABLE IF EXISTS customer_profiles CASCADE;

-- V129__purge_easebuzz_tables_and_dead_bill_columns.sql
-- Purge 7 Easebuzz gateway tables and 7 dead columns from bills table.
--
-- WHY:
-- 1. All 167 restaurants in production use offline UPI / Cash billing.
-- 2. Easebuzz tables (chargebacks, easebuzz_payouts, easebuzz_refund_attempts,
--    easebuzz_sub_merchant_webhook_event, easebuzz_webhook_events, webhook_retry_jobs)
--    have 0 production rows. easebuzz_sub_merchant has only 2 dormant sandbox test rows.
-- 3. In `bills`, the 7 columns (gateway_link_created_at, settled_amount, settled_at,
--    commission_amount, refund_id, gateway_txn_id, gateway_status) are 100% NULL
--    across all 2,281 bills in production, and are never written by Android POS.
-- 4. Dropping them simplifies the bills table from 52 to 45 columns and reduces total
--    database tables from 32 to 25.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

-- 1. Drop indexes on dead bill columns
DROP INDEX IF EXISTS idx_bills_gateway_txn;
DROP INDEX IF EXISTS idx_bills_gateway_stuck;

-- 2. Drop dead columns from bills table
ALTER TABLE bills DROP COLUMN IF EXISTS gateway_link_created_at;
ALTER TABLE bills DROP COLUMN IF EXISTS settled_amount;
ALTER TABLE bills DROP COLUMN IF EXISTS settled_at;
ALTER TABLE bills DROP COLUMN IF EXISTS commission_amount;
ALTER TABLE bills DROP COLUMN IF EXISTS refund_id;
ALTER TABLE bills DROP COLUMN IF EXISTS gateway_txn_id;
ALTER TABLE bills DROP COLUMN IF EXISTS gateway_status;

-- 3. Drop 7 Easebuzz tables
DROP TABLE IF EXISTS chargebacks CASCADE;
DROP TABLE IF EXISTS easebuzz_payouts CASCADE;
DROP TABLE IF EXISTS easebuzz_refund_attempts CASCADE;
DROP TABLE IF EXISTS easebuzz_sub_merchant_webhook_event CASCADE;
DROP TABLE IF EXISTS easebuzz_webhook_events CASCADE;
DROP TABLE IF EXISTS webhook_retry_jobs CASCADE;
DROP TABLE IF EXISTS easebuzz_sub_merchant CASCADE;

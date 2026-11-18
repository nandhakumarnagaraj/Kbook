-- V131__add_active_order_and_delta_sync_covering_indexes.sql
-- High-throughput covering indexes for multi-terminal delta sync and active orders reporting.
--
-- WHY:
-- 1. Accelerates Web Admin order listing and revenue dashboards that query:
--    WHERE restaurant_id = :restaurant_id AND is_deleted = false ORDER BY created_at DESC
-- 2. Optimizes multi-terminal delta sync pull queries by filtering out deleted records
--    at index scan time instead of fetching heap tuples.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '10s';

-- 1. Active bills listing by creation timestamp (Web Admin & Reporting)
CREATE INDEX IF NOT EXISTS idx_bills_active_created 
    ON bills (restaurant_id, created_at DESC) 
    WHERE is_deleted = false;

-- 2. Active bills listing by order status and timestamp
CREATE INDEX IF NOT EXISTS idx_bills_active_status_created 
    ON bills (restaurant_id, order_status, created_at DESC) 
    WHERE is_deleted = false;

-- 3. Active menu items delta sync pull index
CREATE INDEX IF NOT EXISTS idx_menuitems_active_sync 
    ON menuitems (restaurant_id, server_updated_at) 
    WHERE is_deleted = false;

-- 4. Active bill payments delta sync pull index
CREATE INDEX IF NOT EXISTS idx_bill_payments_sync_pull 
    ON bill_payments (restaurant_id, server_updated_at) 
    WHERE is_deleted = false;

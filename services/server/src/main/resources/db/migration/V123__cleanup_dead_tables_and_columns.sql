-- Migration V123: Cleanup dead prototype tables and unmapped columns.
--
-- WHY: An audit of the live PostgreSQL schema vs JPA entities and running JAR on 2026-11-17
--      identified 8 orphan tables and 1 unmapped column leftover from early prototypes
--      (storefront ordering, early backend payments, and Swiggy/Zomato marketplace).
--      All 8 tables have 0 rows in production, and bills.marketplace_order_id is 100% NULL.
--      None of these objects are referenced by active Java entities or queries.
--
-- SAFETY & PRECONDITIONS:
--   - Verified 0 rows across all 8 tables on 2026-11-17.
--   - Verified bills.marketplace_order_id has 0 non-null values across 2,192 rows.
--   - A full backup was taken immediately prior to this migration (docs/runbooks/BACKUP_AND_RESTORE.md).
--   - All statements use IF EXISTS and CASCADE (for child FK constraints) so the migration is
--     idempotent and safe to run multiple times without failure.
--
-- OBJECTS REMOVED:
--   1. customer_order_items (0 rows, prototype storefront order items)
--   2. customer_orders (0 rows, prototype storefront orders)
--   3. marketplace_order_items (0 rows, prototype marketplace order items)
--   4. marketplace_orders (0 rows, prototype marketplace orders)
--   5. payment_webhook_logs (0 rows, prototype backend payment logs)
--   6. payments (0 rows, prototype backend payments, superseded by Easebuzz)
--   7. restaurant_payment_config (0 rows, prototype payment config)
--   8. webhook_inbox (0 rows, unused webhook queue)
--   9. bills.marketplace_order_id (column and index idx_bills_marketplace_order)

-- 1. Drop orphan marketplace objects
DROP INDEX IF EXISTS idx_bills_marketplace_order;
ALTER TABLE IF EXISTS bills DROP COLUMN IF EXISTS marketplace_order_id;

DROP TABLE IF EXISTS marketplace_order_items CASCADE;
DROP TABLE IF EXISTS marketplace_orders CASCADE;

-- 2. Drop orphan customer storefront objects
DROP TABLE IF EXISTS customer_order_items CASCADE;
DROP TABLE IF EXISTS customer_orders CASCADE;

-- 3. Drop orphan early payment prototype objects
DROP TABLE IF EXISTS payment_webhook_logs CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS restaurant_payment_config CASCADE;

-- 4. Drop orphan webhook inbox
DROP TABLE IF EXISTS webhook_inbox CASCADE;

-- ============================================================
-- V11: Referential integrity + performance indexes
-- ============================================================

-- 1. Cascade delete: orphaned bill_items when a bill is hard-deleted
--    (Soft-delete is the norm, but admin hard-deletes must not leave orphans)
ALTER TABLE bill_items
    DROP CONSTRAINT IF EXISTS fk_bill_items_bill,
    ADD CONSTRAINT fk_bill_items_bill
        FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE;

ALTER TABLE bill_payments
    DROP CONSTRAINT IF EXISTS fk_bill_payments_bill,
    ADD CONSTRAINT fk_bill_payments_bill
        FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE;

-- 2. Composite indexes for high-frequency date-range and status queries
--    HomeViewModel:  WHERE restaurant_id = ? AND updated_at BETWEEN ? AND ?
--    ReportsViewModel: ORDER BY status, date
CREATE INDEX IF NOT EXISTS idx_bills_tenant_date
    ON bills(restaurant_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_bills_tenant_status
    ON bills(restaurant_id, order_status);

CREATE INDEX IF NOT EXISTS idx_bills_tenant_payment_status
    ON bills(restaurant_id, payment_status);

-- 3. Bill items: fast lookup by bill (join queries)
CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id
    ON bill_items(bill_id);

-- 4. Bill payments: fast lookup by bill
CREATE INDEX IF NOT EXISTS idx_bill_payments_bill_id
    ON bill_payments(bill_id);

-- 5. Menu items: fast lookup by category
CREATE INDEX IF NOT EXISTS idx_menu_items_category
    ON menuitems(restaurant_id, category_id);

-- #19: Add missing index on bills(restaurant_id, daily_order_id).
-- Reports and order lookup by daily order number ("Order #042") were doing a full
-- table scan per tenant. This composite index makes those lookups instant.
-- Also adds a covering index on bills(restaurant_id, created_at DESC) for the
-- business dashboard "recent orders" query which sorts by creation time.

CREATE INDEX IF NOT EXISTS idx_bills_tenant_daily_order
    ON bills (restaurant_id, daily_order_id);

-- Index for customer_orders lookup by restaurant (used in merchant storefront views)
CREATE INDEX IF NOT EXISTS idx_customer_orders_restaurant_status
    ON customer_orders (restaurant_id, order_status);

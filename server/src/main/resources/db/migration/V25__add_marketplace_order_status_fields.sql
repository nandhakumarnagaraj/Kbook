-- Add restaurant-side order status tracking for marketplace orders
ALTER TABLE marketplace_orders
    ADD COLUMN IF NOT EXISTS order_status VARCHAR(50) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS accepted_at BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_reason TEXT,
    ADD COLUMN IF NOT EXISTS ready_at BIGINT,
    ADD COLUMN IF NOT EXISTS completed_at BIGINT;

CREATE INDEX IF NOT EXISTS idx_marketplace_orders_status ON marketplace_orders(restaurant_id, order_status);

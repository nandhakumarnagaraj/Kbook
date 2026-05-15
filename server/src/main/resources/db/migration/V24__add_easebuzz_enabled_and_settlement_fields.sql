-- Re-add easebuzz_enabled flag (dropped in V9, now needed for V2 sub-merchant toggle)
ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS easebuzz_enabled BOOLEAN DEFAULT FALSE;

-- Add settlement tracking columns to bills
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS gateway_txn_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS gateway_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS settled_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS settled_at BIGINT,
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS marketplace_order_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_bills_gateway_txn ON bills(gateway_txn_id);
CREATE INDEX IF NOT EXISTS idx_bills_marketplace_order ON bills(marketplace_order_id);

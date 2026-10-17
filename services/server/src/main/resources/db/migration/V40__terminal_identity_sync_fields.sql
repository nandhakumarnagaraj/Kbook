ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS terminal_name VARCHAR(255);
ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE restaurant_terminal
SET terminal_name = COALESCE(NULLIF(terminal_name, ''), 'Terminal ' || id::text),
    is_active = COALESCE(is_active, TRUE);

ALTER TABLE users ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE restaurantprofiles ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE menuitems ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE itemvariants ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE stock_logs ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);
ALTER TABLE bill_payments ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(255);

UPDATE bills b
SET terminal_id = COALESCE(
    NULLIF(b.terminal_id, ''),
    t.id::text,
    NULLIF(b.terminal_series, ''),
    'LEGACY_UNRESOLVED'
)
FROM restaurant_terminal t
WHERE t.restaurant_id = b.restaurant_id
  AND t.terminal_series = b.terminal_series
  AND (b.terminal_id IS NULL OR b.terminal_id = '');

UPDATE bill_items bi
SET terminal_id = b.terminal_id
FROM bills b
WHERE b.id = bi.bill_id
  AND b.restaurant_id = bi.restaurant_id
  AND (bi.terminal_id IS NULL OR bi.terminal_id = '');

UPDATE bill_payments bp
SET terminal_id = b.terminal_id
FROM bills b
WHERE b.id = bp.bill_id
  AND b.restaurant_id = bp.restaurant_id
  AND (bp.terminal_id IS NULL OR bp.terminal_id = '');

CREATE INDEX IF NOT EXISTS idx_bills_restaurant_terminal_updated
    ON bills (restaurant_id, terminal_id, updated_at);


ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS created_terminal_id VARCHAR(100);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS created_device_id VARCHAR(100);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS current_owner_terminal_id VARCHAR(100);

UPDATE bills
SET created_terminal_id = COALESCE(NULLIF(created_terminal_id, ''), NULLIF(terminal_id, ''), NULLIF(terminal_series, ''), 'LEGACY_UNRESOLVED'),
    current_owner_terminal_id = COALESCE(NULLIF(current_owner_terminal_id, ''), NULLIF(created_terminal_id, ''), NULLIF(terminal_id, ''), NULLIF(terminal_series, ''), 'LEGACY_UNRESOLVED'),
    created_device_id = COALESCE(NULLIF(created_device_id, ''), NULLIF(device_id, ''))
WHERE created_terminal_id IS NULL
   OR created_terminal_id = ''
   OR current_owner_terminal_id IS NULL
   OR current_owner_terminal_id = ''
   OR created_device_id IS NULL
   OR created_device_id = '';

CREATE INDEX IF NOT EXISTS idx_bills_terminal_visibility
    ON bills (restaurant_id, created_terminal_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bills_owner_visibility
    ON bills (restaurant_id, current_owner_terminal_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bill_items_server_bill_updated
    ON bill_items (restaurant_id, server_bill_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bill_payments_server_bill_updated
    ON bill_payments (restaurant_id, server_bill_id, server_updated_at);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS created_terminal_id VARCHAR(100);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS created_device_id VARCHAR(100);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS current_owner_terminal_id VARCHAR(100);

-- v3-only guard (owner-approved deviation; main/v1 keeps this migration byte-for-byte):
-- this backfill referenced bills.terminal_id, which is only added by V40, so it crashed any
-- fresh database (Req 2.10/2.14). Production avoided the crash only because its schema predates
-- the April 2026 consolidation. When terminal_id exists (production history / V40 forward) the
-- original statement runs unchanged; when it is absent (fresh DB), the backfill degrades to the
-- terminal_series/device_id sources and V40 performs the terminal_id backfill when it adds the
-- column. Recorded in docs/baseline-provenance/ conflict register.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'bills' AND column_name = 'terminal_id') THEN
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
    ELSE
        UPDATE bills
        SET created_terminal_id = COALESCE(NULLIF(created_terminal_id, ''), NULLIF(terminal_series, ''), 'LEGACY_UNRESOLVED'),
            current_owner_terminal_id = COALESCE(NULLIF(current_owner_terminal_id, ''), NULLIF(created_terminal_id, ''), NULLIF(terminal_series, ''), 'LEGACY_UNRESOLVED'),
            created_device_id = COALESCE(NULLIF(created_device_id, ''), NULLIF(device_id, ''))
        WHERE created_terminal_id IS NULL
           OR created_terminal_id = ''
           OR current_owner_terminal_id IS NULL
           OR current_owner_terminal_id = ''
           OR created_device_id IS NULL
           OR created_device_id = '';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bills_terminal_visibility
    ON bills (restaurant_id, created_terminal_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bills_owner_visibility
    ON bills (restaurant_id, current_owner_terminal_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bill_items_server_bill_updated
    ON bill_items (restaurant_id, server_bill_id, server_updated_at);

CREATE INDEX IF NOT EXISTS idx_bill_payments_server_bill_updated
    ON bill_payments (restaurant_id, server_bill_id, server_updated_at);

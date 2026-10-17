-- Terminal heartbeat: last authenticated sync activity per terminal
-- The canonical table is restaurant_terminal (singular, V26 + entity). Some legacy
-- prod DBs carry restaurant_terminals (plural), so cover both.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'restaurant_terminal') THEN
        ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS last_seen_at BIGINT;
        CREATE INDEX IF NOT EXISTS idx_restaurant_terminal_last_seen ON restaurant_terminal(restaurant_id, last_seen_at);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'restaurant_terminals') THEN
        ALTER TABLE restaurant_terminals ADD COLUMN IF NOT EXISTS last_seen_at BIGINT;
        CREATE INDEX IF NOT EXISTS idx_restaurant_terminals_last_seen ON restaurant_terminals(restaurant_id, last_seen_at);
    END IF;
END $$;

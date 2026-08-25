-- Terminal heartbeat: last authenticated sync activity per terminal
-- Guard: restaurant_terminals may not exist on some prod DBs (V27/V30 were skipped)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'restaurant_terminals') THEN
        ALTER TABLE restaurant_terminals ADD COLUMN IF NOT EXISTS last_seen_at BIGINT;
        CREATE INDEX IF NOT EXISTS idx_restaurant_terminals_last_seen ON restaurant_terminals(restaurant_id, last_seen_at);
    END IF;
END $$;

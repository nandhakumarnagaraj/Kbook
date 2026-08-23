-- Terminal heartbeat: last authenticated sync activity per terminal
ALTER TABLE restaurant_terminals ADD COLUMN IF NOT EXISTS last_seen_at BIGINT;
CREATE INDEX IF NOT EXISTS idx_restaurant_terminals_last_seen ON restaurant_terminals(restaurant_id, last_seen_at);

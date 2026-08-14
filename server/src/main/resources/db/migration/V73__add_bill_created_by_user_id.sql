-- V47: Track which staff member created each bill
ALTER TABLE bills ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_bills_created_by_user ON bills (restaurant_id, created_by_user_id);

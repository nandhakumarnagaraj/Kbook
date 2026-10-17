-- Link device tokens to the staff user who registered them (per-user push targeting)
ALTER TABLE device_tokens ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_device_tokens_restaurant_user ON device_tokens(restaurant_id, user_id);

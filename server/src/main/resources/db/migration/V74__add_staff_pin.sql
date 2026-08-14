-- V48: Add PIN column for fast staff switching on shared devices
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_set_at BIGINT;

-- ============================================================
-- V9: V2 Schema Fixes
-- ============================================================

-- 1. Add cancel_reason to bills (sync Android cancel/void feature)
ALTER TABLE bills ADD COLUMN IF NOT EXISTS cancel_reason TEXT NOT NULL DEFAULT '';

-- 2. Add dedicated phone_number column to users
--    (phone was incorrectly stored in email field)
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_invalidated_at BIGINT;

-- Backfill phone_number from login_id for phone-auth users
UPDATE users SET phone_number = login_id
WHERE auth_provider = 'PHONE' AND phone_number IS NULL AND login_id IS NOT NULL;

-- 3. Make email nullable (phone users don't have emails)
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- 4. Unique index on phone_number (allow NULL for Google-only users)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number
    ON users (phone_number) WHERE phone_number IS NOT NULL;

-- 5. Index for token invalidation lookups
CREATE INDEX IF NOT EXISTS idx_users_restaurant_active
    ON users (restaurant_id, is_active);

-- V42: Add is_suspended column for business lifecycle management
ALTER TABLE restaurantprofiles ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_restaurantprofiles_suspended
    ON restaurantprofiles (restaurant_id, is_suspended);

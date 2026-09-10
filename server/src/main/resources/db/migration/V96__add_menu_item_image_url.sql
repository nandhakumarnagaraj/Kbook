-- V96: Add menu item food photo URL and version tracking for CDN sync
ALTER TABLE menuitems
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(512);

ALTER TABLE menuitems
    ADD COLUMN IF NOT EXISTS image_version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_menuitems_image ON menuitems (restaurant_id) WHERE image_url IS NOT NULL;

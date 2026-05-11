-- Persist marketplace integration metadata for restaurant profiles.
-- These fields are used by the current entity model and must exist before
-- Hibernate schema validation runs.

ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS zomato_outlet_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS swiggy_store_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS marketplace_notes TEXT;

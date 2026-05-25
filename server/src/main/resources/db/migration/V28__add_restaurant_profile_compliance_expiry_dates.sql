ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS fssai_expiry_date DATE,
    ADD COLUMN IF NOT EXISTS gst_expiry_date DATE;

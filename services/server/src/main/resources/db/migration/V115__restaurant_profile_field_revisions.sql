-- Per-field revision map for restaurant_profile sync conflicts.
-- Format: "field=revision,field=revision" (lowercase field names). Lets the server reject a
-- masked profile push only when a field it names changed after the device's claimed revision,
-- instead of rejecting every push whenever the shared row revision moved.
ALTER TABLE restaurantprofiles ADD COLUMN IF NOT EXISTS field_revisions TEXT;

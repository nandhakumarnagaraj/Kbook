-- Easebuzz credentials are now exclusively managed via restaurant_payment_config table.
-- These columns were never populated via Android sync (not in RestaurantProfileDTO)
-- and are not used by the payment flow. Drop them to remove ambiguity.

ALTER TABLE restaurantprofiles
    DROP COLUMN IF EXISTS easebuzz_merchant_key,
    DROP COLUMN IF EXISTS easebuzz_salt,
    DROP COLUMN IF EXISTS easebuzz_env,
    DROP COLUMN IF EXISTS easebuzz_enabled;

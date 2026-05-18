-- easebuzz_sub_merchant.restaurant_id stores the tenant restaurant_id used by
-- JWTs and payment flows, not the restaurantprofiles primary key.
ALTER TABLE easebuzz_sub_merchant
    DROP CONSTRAINT IF EXISTS fk_sub_merchant_restaurant;

CREATE INDEX IF NOT EXISTS idx_sub_merchant_restaurant_id
    ON easebuzz_sub_merchant(restaurant_id);

-- V125: Drop obsolete marketplace integration columns from restaurantprofiles.
-- These columns were created in V1, V20, and V21 for third-party marketplace experiments.
-- The Android POS app already dropped them in Room migration 68_69, all 163 production
-- restaurant rows carry NULL/false, and standalone marketplace tables were dropped in V123.

ALTER TABLE restaurantprofiles
    DROP COLUMN IF EXISTS zomato_enabled,
    DROP COLUMN IF EXISTS swiggy_enabled,
    DROP COLUMN IF EXISTS own_website_enabled,
    DROP COLUMN IF EXISTS zomato_outlet_id,
    DROP COLUMN IF EXISTS swiggy_store_id,
    DROP COLUMN IF EXISTS marketplace_notes,
    DROP COLUMN IF EXISTS zomato_api_key,
    DROP COLUMN IF EXISTS zomato_webhook_secret,
    DROP COLUMN IF EXISTS swiggy_api_key,
    DROP COLUMN IF EXISTS swiggy_webhook_secret;

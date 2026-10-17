-- V43: Number-matching challenge for terminal recovery/registration approval.
--
-- Adds a short 2-digit challenge (GitHub-Mobile style) to device registration
-- requests. The requesting device displays the number; the approving OWNER /
-- SHOP_ADMIN must enter the matching number in the web admin before the request
-- can be approved. This defeats blind/accidental approval.
--
-- All columns are nullable / defaulted so existing rows without a generated
-- challenge remain approvable. New requests receive a challenge and must match it.

ALTER TABLE device_registration_request
    ADD COLUMN IF NOT EXISTS challenge_code VARCHAR(8);

ALTER TABLE device_registration_request
    ADD COLUMN IF NOT EXISTS challenge_expires_at BIGINT;

ALTER TABLE device_registration_request
    ADD COLUMN IF NOT EXISTS challenge_attempts INTEGER NOT NULL DEFAULT 0;

-- EaseBuzz compliance / CPV onboarding fields.
-- Adds the verified legal entity name, state, and FSSAI license details that
-- must be submitted to EaseBuzz at sub-merchant creation. Previously the legal
-- name fell back to a display name and state/address were hardcoded, causing
-- CPV mismatches; FSSAI was never transmitted at all.

ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS legal_entity_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS state             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS fssai_number      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fssai_expiry_date BIGINT;

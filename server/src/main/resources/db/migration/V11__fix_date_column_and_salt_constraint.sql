-- FIX #12: bills.last_reset_date VARCHAR → DATE migration
-- The VARCHAR column is fragile: "2026-4-1" ≠ "2026-04-01" so date comparisons
-- for daily counter resets can silently fail. We backfill the new DATE column
-- from the existing VARCHAR and then add a NOT NULL constraint.
--
-- The new column last_reset_date_proper (DATE) was added in V1 but left nullable.
-- This migration backfills it from last_reset_date (VARCHAR) where missing,
-- then enforces NOT NULL going forward.

-- Step 1: Backfill any NULLs in the proper DATE column from the VARCHAR column.
-- We cast via TO_DATE with a safe fallback to today's date if the VARCHAR is malformed.
UPDATE bills
SET last_reset_date_proper = (
    CASE
        WHEN last_reset_date IS NOT NULL AND last_reset_date ~ '^\d{4}-\d{1,2}-\d{1,2}$'
        THEN TO_DATE(last_reset_date, 'YYYY-MM-DD')
        ELSE CURRENT_DATE
    END
)
WHERE last_reset_date_proper IS NULL;

-- Step 2: Enforce NOT NULL now that all rows are backfilled.
ALTER TABLE bills
    ALTER COLUMN last_reset_date_proper SET NOT NULL,
    ALTER COLUMN last_reset_date_proper SET DEFAULT CURRENT_DATE;

-- FIX #5: Validate encrypted_salt is non-trivially encrypted.
-- We cannot enforce AES-GCM in SQL, but we can enforce minimum length
-- (a real AES-GCM/Base64 ciphertext is always >32 chars) and reject
-- values that look like plain phone numbers (all digits, <20 chars).
ALTER TABLE restaurant_payment_config
    ADD CONSTRAINT chk_encrypted_salt_looks_encrypted
    CHECK (
        encrypted_salt IS NULL
        OR (
            LENGTH(encrypted_salt) >= 32
            AND encrypted_salt !~ '^\d+$'
        )
    );

-- Add a comment documenting the expected format.
COMMENT ON COLUMN restaurant_payment_config.encrypted_salt
    IS 'AES-GCM encrypted, Base64-encoded salt. Must be ≥32 chars. Plain text values are rejected by chk_encrypted_salt_looks_encrypted.';

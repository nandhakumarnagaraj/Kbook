-- Fix #3 (issues.txt): DB-level enforcement against phone format-variant duplicates.
-- Adds a generated column with the digit-normalized phone and a unique partial
-- index over it. Even a buggy client can then no longer create
-- "+91XXXXXXXXXX" / "0XXXXXXXXXX" / spaced duplicates of an existing account.

-- 1) Generated column: digits extracted from phone_number, keeping the final 10.
--    Mirrors PhoneNormalizer.normalize() server-side logic.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_digits VARCHAR(10)
    GENERATED ALWAYS AS (
        CASE
            WHEN phone_number IS NULL THEN NULL
            ELSE RIGHT(REGEXP_REPLACE(phone_number, '[^0-9]', '', 'g'), 10)
        END
    ) STORED;

-- 2) Backfill safety no-op (generated column computes itself), then the
--    unique partial index over the normalized digits. Partial: only rows
--    with a phone participate. Collisions must be zero (verified in prod:
--    0 digit-variant duplicates as of 2026-09-26).
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_phone_digits
    ON users (phone_digits)
    WHERE phone_digits IS NOT NULL;

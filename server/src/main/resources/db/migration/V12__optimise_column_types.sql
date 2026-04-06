-- ============================================================
-- V12: Column type optimisations
-- ============================================================

-- 1. cancel_reason: narrow from TEXT to VARCHAR(500).
--    PostgreSQL stores short VARCHARs inline; TEXT is identical on-disk but
--    VARCHAR(500) adds a server-side length constraint.
ALTER TABLE bills ALTER COLUMN cancel_reason TYPE VARCHAR(500);
ALTER TABLE bills ALTER COLUMN cancel_reason SET NOT NULL;
ALTER TABLE bills ALTER COLUMN cancel_reason SET DEFAULT '';

-- 2. otp.phone_number: already VARCHAR(20) — nothing to do.

-- 3. Tighten payment_mode to avoid garbage values (informational constraint).
--    We don't use ENUM to keep migrations reversible; a CHECK constraint works.
ALTER TABLE bill_payments
    ADD CONSTRAINT chk_bill_payment_mode
    CHECK (payment_mode IN ('cash','upi','pos','part_cash_upi','part_cash_pos','part_upi_pos','zomato','swiggy','own_website'));

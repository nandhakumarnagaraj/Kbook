-- Add operation_id column to bill_payments for Android-side payment identity.
-- Android generates a unique operation_id for each payment component. The column
-- is nullable so legacy clients (old APKs without operation_id) continue to sync.
-- The partial unique index prevents duplicate payment operation identities within
-- a restaurant without rejecting historical rows with NULL operation_id.
--
-- This pairs with the Android Room migration 61→62 partial unique index
-- (idx_bill_payments_restaurant_operation) to ensure consistent dedup on both
-- client and server.

ALTER TABLE bill_payments
    ADD COLUMN IF NOT EXISTS operation_id VARCHAR(255);

-- Partial unique index: only non-null, non-blank operation_ids are constrained.
-- Matching the Android-side scope: (restaurant_id, operation_id).
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_payments_restaurant_operation
    ON bill_payments (restaurant_id, operation_id)
    WHERE operation_id IS NOT NULL AND operation_id <> '' AND is_deleted = FALSE;

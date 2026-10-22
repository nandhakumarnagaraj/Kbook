-- Retire the KhanaBook <-> restaurant "virtual" e-agreement.
--
-- The signed-PDF agreement has been removed from the product (Android POS and Web Admin), and
-- nothing gates payment-link creation on it any more. Forward-only discipline: V88/V101 stay
-- untouched; this migration removes their result. Dropping the table also drops any remaining
-- storage keys, which no endpoint serves once the agreement controller is gone.
DROP INDEX IF EXISTS idx_merchant_agreement_latest;
DROP INDEX IF EXISTS idx_merchant_agreement_restaurant;
DROP TABLE IF EXISTS merchant_agreement;

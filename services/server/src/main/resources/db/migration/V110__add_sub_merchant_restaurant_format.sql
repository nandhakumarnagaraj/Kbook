-- Easebuzz sets sub_merchant_mcc_code when the sub-merchant is created and cannot change it
-- afterwards (ERA, 2026-10-17): dine-in 5812, QSR 5814, bar 5813, caterer 5811. The chosen
-- restaurant format is stored here so the MCC sent on create matches the outlet, and so an
-- existing row can still be inspected/corrected before its first submission.
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS restaurant_format VARCHAR(40);

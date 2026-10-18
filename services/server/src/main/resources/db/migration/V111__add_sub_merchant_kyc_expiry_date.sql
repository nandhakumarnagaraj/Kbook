-- Easebuzz sends `kyc_expiry_date` on the MERCHANT_KYC_APPROVAL webhook (e.g.
-- "2023-03-02 12:27:22"). Once it lapses the sub-merchant can no longer accept payments,
-- and Easebuzz sends no expiry webhook, so the value is stored as received: the raw string
-- keeps the payload lossless and lets the UI show exactly what the gateway reported.
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS kyc_expiry_date VARCHAR(40);

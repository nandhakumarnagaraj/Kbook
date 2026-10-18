-- Easebuzz reports bank verification separately from KYC approval (kyc_status / bank_status on
-- MERCHANT_KYC_APPROVAL) and sends no webhook when bank_status later changes. Recording the
-- value lets an operator see why a merchant is not ACTIVE; without it there is no local
-- evidence at all. Nullable: older rows and payloads without the field keep NULL.
ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS bank_status BOOLEAN;

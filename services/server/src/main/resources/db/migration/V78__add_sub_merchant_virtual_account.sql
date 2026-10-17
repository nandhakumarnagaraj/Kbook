-- Add virtual_account fields from KYC approval webhook payload
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS virtual_account_id VARCHAR(255);
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS virtual_account_number VARCHAR(255);
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS virtual_account_ifsc VARCHAR(255);
ALTER TABLE easebuzz_sub_merchant ADD COLUMN IF NOT EXISTS virtual_account_bank VARCHAR(255);

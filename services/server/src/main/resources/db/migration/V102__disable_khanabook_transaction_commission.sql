-- KhanaBook does not charge restaurants a transaction commission.
-- Keep historical bill.commission_amount values for audit; normalize only the
-- current sub-merchant configuration used for future payments.
UPDATE easebuzz_sub_merchant
SET commission_rate = 0
WHERE commission_rate IS DISTINCT FROM 0;

ALTER TABLE easebuzz_sub_merchant
ALTER COLUMN commission_rate SET NOT NULL;

ALTER TABLE easebuzz_sub_merchant
ADD CONSTRAINT ck_easebuzz_sub_merchant_zero_commission
CHECK (commission_rate = 0);

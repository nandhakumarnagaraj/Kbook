-- V76: Backfill schema objects referenced by entities but missing from the
-- consolidated migration set (v2 feature port gap, commit 9e8570c2):
--   1. easebuzz_payouts table (EasebuzzPayout entity)
--   2. easebuzz_sub_merchant settlement/compliance columns referenced by
--      EasebuzzSubMerchant but never created by any migration

CREATE TABLE IF NOT EXISTS easebuzz_payouts (
    id                  BIGSERIAL PRIMARY KEY,
    restaurant_id       BIGINT NOT NULL,
    merchant_request_id VARCHAR(255) NOT NULL,
    payout_id           VARCHAR(255),
    amount              NUMERIC(12,2) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    beneficiary_name    VARCHAR(255),
    account_number      VARCHAR(255),
    ifsc                VARCHAR(32),
    utr                 VARCHAR(64),
    error_message       TEXT,
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL,
    CONSTRAINT uk_easebuzz_payout_merchant_request UNIQUE (merchant_request_id)
);

CREATE INDEX IF NOT EXISTS idx_easebuzz_payouts_restaurant
    ON easebuzz_payouts (restaurant_id, created_at);

ALTER TABLE easebuzz_sub_merchant
    ADD COLUMN IF NOT EXISTS upi_deduction_lt_limit      NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS dc_deduction_gt_two_thousand NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS bank_name                   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS branch_name                 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS split_label                 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS id_proof_url                TEXT,
    ADD COLUMN IF NOT EXISTS bank_proof_url              TEXT;

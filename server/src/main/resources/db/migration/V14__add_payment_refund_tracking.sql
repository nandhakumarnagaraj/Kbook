ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_mode VARCHAR(32);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(32);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(12,2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_reason TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS merchant_refund_id VARCHAR(128);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_gateway_refund_id VARCHAR(128);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_arn_number VARCHAR(128);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_requested_at BIGINT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_processed_at BIGINT;

CREATE INDEX IF NOT EXISTS idx_payments_refund_gateway_refund
    ON payments (refund_gateway_refund_id);

CREATE INDEX IF NOT EXISTS idx_payments_merchant_refund
    ON payments (merchant_refund_id);

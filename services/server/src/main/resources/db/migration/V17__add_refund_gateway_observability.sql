ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_last_gateway_payload TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_last_gateway_sync_at BIGINT;

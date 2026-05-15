-- Webhook events specific to sub-merchant lifecycle (KYC status, sub-merchant status changes)

CREATE TABLE IF NOT EXISTS easebuzz_sub_merchant_webhook_event (
    id                  BIGSERIAL       PRIMARY KEY,
    sub_merchant_id     VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    raw_status          VARCHAR(50),
    payload             JSONB           NOT NULL,
    processed           BOOLEAN         NOT NULL DEFAULT FALSE,
    received_at         BIGINT          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sub_merchant_webhook_event_sub ON easebuzz_sub_merchant_webhook_event(sub_merchant_id);
CREATE INDEX IF NOT EXISTS idx_sub_merchant_webhook_event_processed ON easebuzz_sub_merchant_webhook_event(processed);

-- Easebuzz payment gateway support.
-- Adds gateway credentials to restaurantprofiles and gateway tracking fields
-- to bill_payments. Manual payment flow still works when these are null.

ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS easebuzz_enabled       BOOLEAN      DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS easebuzz_merchant_key  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS easebuzz_salt          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS easebuzz_env           VARCHAR(16)  DEFAULT 'test';

ALTER TABLE bill_payments
    ADD COLUMN IF NOT EXISTS gateway_txn_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS gateway_status  VARCHAR(32),
    ADD COLUMN IF NOT EXISTS verified_by     VARCHAR(16) DEFAULT 'manual';

-- Webhook records — authoritative source of payment status from Easebuzz.
CREATE TABLE IF NOT EXISTS easebuzz_webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL,
    txn_id          VARCHAR(64)  NOT NULL,
    easebuzz_id     VARCHAR(64),
    status          VARCHAR(32)  NOT NULL,
    amount          NUMERIC(12,2),
    raw_payload     TEXT,
    received_at     BIGINT       NOT NULL,
    UNIQUE (restaurant_id, txn_id)
);

CREATE INDEX IF NOT EXISTS idx_easebuzz_webhook_txn ON easebuzz_webhook_events (txn_id);
CREATE INDEX IF NOT EXISTS idx_easebuzz_webhook_restaurant ON easebuzz_webhook_events (restaurant_id);

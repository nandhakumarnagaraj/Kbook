CREATE TABLE IF NOT EXISTS restaurant_payment_config (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL,
    gateway_name    VARCHAR(32)  NOT NULL,
    merchant_key    VARCHAR(255) NOT NULL,
    encrypted_salt  TEXT         NOT NULL,
    environment     VARCHAR(16)  NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    CONSTRAINT uq_restaurant_payment_config_restaurant_gateway
        UNIQUE (restaurant_id, gateway_name)
);

CREATE INDEX IF NOT EXISTS idx_restaurant_payment_config_restaurant
    ON restaurant_payment_config (restaurant_id);

CREATE TABLE IF NOT EXISTS payments (
    id                  BIGSERIAL PRIMARY KEY,
    restaurant_id       BIGINT         NOT NULL,
    bill_id             BIGINT         NOT NULL,
    user_id             BIGINT,
    amount              NUMERIC(12,2)  NOT NULL,
    currency            VARCHAR(8)     NOT NULL,
    gateway             VARCHAR(32)    NOT NULL,
    gateway_txn_id      VARCHAR(128)   NOT NULL,
    gateway_payment_id  VARCHAR(128),
    gateway_status      VARCHAR(64),
    payment_status      VARCHAR(32)    NOT NULL,
    payment_method      VARCHAR(32)    NOT NULL,
    checkout_url        TEXT,
    failure_reason      TEXT,
    created_at          BIGINT         NOT NULL,
    updated_at          BIGINT         NOT NULL,
    verified_at         BIGINT,
    CONSTRAINT uq_payments_gateway_txn UNIQUE (gateway_txn_id)
);

CREATE INDEX IF NOT EXISTS idx_payments_restaurant_bill
    ON payments (restaurant_id, bill_id);
CREATE INDEX IF NOT EXISTS idx_payments_gateway_payment
    ON payments (gateway_payment_id);

CREATE TABLE IF NOT EXISTS payment_webhook_logs (
    id               BIGSERIAL PRIMARY KEY,
    payment_id       BIGINT,
    gateway          VARCHAR(32) NOT NULL,
    txn_id           VARCHAR(128),
    payload          TEXT,
    signature_valid  BOOLEAN     NOT NULL,
    processed        BOOLEAN     NOT NULL DEFAULT FALSE,
    received_at      BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_logs_payment
    ON payment_webhook_logs (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_logs_txn
    ON payment_webhook_logs (txn_id);

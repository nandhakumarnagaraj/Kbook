CREATE TABLE easebuzz_refund_attempts (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL REFERENCES bills(id),
    restaurant_id BIGINT NOT NULL,
    gateway_txn_id VARCHAR(255) NOT NULL,
    easebuzz_payment_id VARCHAR(255) NOT NULL,
    merchant_refund_id VARCHAR(255) NOT NULL UNIQUE,
    gateway_refund_id VARCHAR(255) UNIQUE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_easebuzz_refund_attempts_bill_status
    ON easebuzz_refund_attempts (bill_id, status);

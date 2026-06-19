-- Track paid FSSAI renewal requests per restaurant
CREATE TABLE IF NOT EXISTS fssai_renewals (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    fssai_number VARCHAR(100) NOT NULL,
    years INT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    easebuzz_txn_id VARCHAR(100) UNIQUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fssai_renewals_restaurant ON fssai_renewals(restaurant_id);

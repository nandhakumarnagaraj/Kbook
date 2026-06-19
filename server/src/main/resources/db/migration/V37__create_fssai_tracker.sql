-- FSSAI tracking and compliance metadata per shop
CREATE TABLE IF NOT EXISTS fssai_tracker (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL UNIQUE,
    fssai_number VARCHAR(100),
    company_name VARCHAR(255),
    address TEXT,
    status VARCHAR(50) DEFAULT 'UNKNOWN',
    expiry_date DATE,
    application_submission_date DATE,
    last_updated_on DATE,
    last_checked_at BIGINT,
    is_alert_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_alert_sent_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_fssai_tracker_expiry ON fssai_tracker(expiry_date);
CREATE INDEX IF NOT EXISTS idx_fssai_tracker_restaurant ON fssai_tracker(restaurant_id);

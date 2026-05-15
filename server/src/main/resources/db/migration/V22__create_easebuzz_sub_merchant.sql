-- Easebuzz sub-merchant (parent-child) model for V2.
-- Each restaurant is a sub-merchant under KhanaBook's master merchant account.

CREATE TABLE IF NOT EXISTS easebuzz_sub_merchant (
    id                  BIGSERIAL       PRIMARY KEY,
    restaurant_id       BIGINT          NOT NULL,
    sub_merchant_id     VARCHAR(255),
    status              VARCHAR(50)     NOT NULL DEFAULT 'DRAFT',
    business_name       VARCHAR(255)    NOT NULL,
    business_type       VARCHAR(100),
    pan                 VARCHAR(20),
    gst                 VARCHAR(50),
    bank_account_no     VARCHAR(255),
    ifsc                VARCHAR(20),
    beneficiary_name    VARCHAR(255),
    business_address    TEXT,
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(20),
    kyc_status          VARCHAR(50),
    kyc_portal_url      VARCHAR(500),
    kyc_submitted_at    BIGINT,
    kyc_activated_at    BIGINT,
    commission_rate     NUMERIC(5,2)    DEFAULT 0.00,
    easebuzz_response   JSONB,
    created_at          BIGINT          NOT NULL,
    updated_at          BIGINT          NOT NULL,
    CONSTRAINT fk_sub_merchant_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurantprofiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_sub_merchant_restaurant UNIQUE (restaurant_id)
);

CREATE INDEX IF NOT EXISTS idx_sub_merchant_status ON easebuzz_sub_merchant(status);
CREATE INDEX IF NOT EXISTS idx_sub_merchant_kyc_status ON easebuzz_sub_merchant(kyc_status);

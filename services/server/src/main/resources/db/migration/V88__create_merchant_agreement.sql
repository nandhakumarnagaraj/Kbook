-- KhanaBook <-> restaurant owner e-agreement (Sejda-signed PDF).
-- This is a KhanaBook-side legal record. It is intentionally NOT part of the
-- Easebuzz onboarding payload and NOT synced to Android devices (kept off
-- restaurantprofiles, which is a BaseSyncEntity). The signed PDF is stored on a
-- PRIVATE filesystem path (never under the public /cdn/** handler) and served
-- only through authenticated, tenant-scoped endpoints. This table holds the
-- storage key + signing metadata, not a public URL.
CREATE TABLE IF NOT EXISTS merchant_agreement (
    id                 BIGSERIAL PRIMARY KEY,
    restaurant_id      BIGINT      NOT NULL,
    storage_key        TEXT        NOT NULL,             -- relative path under the private docs base (no public URL)
    original_filename  VARCHAR(255),
    content_type       VARCHAR(100),
    size_bytes         BIGINT,
    signed_at          BIGINT,                           -- epoch millis when the agreement was signed/uploaded
    signer_name        VARCHAR(255),
    agreement_version  VARCHAR(50),                      -- e.g. document/template version
    created_at         BIGINT      NOT NULL,
    updated_at         BIGINT      NOT NULL,
    CONSTRAINT ux_merchant_agreement_restaurant UNIQUE (restaurant_id)
);

CREATE INDEX IF NOT EXISTS idx_merchant_agreement_restaurant
    ON merchant_agreement (restaurant_id);

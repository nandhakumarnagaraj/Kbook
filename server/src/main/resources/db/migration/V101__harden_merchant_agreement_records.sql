-- Keep every signed agreement revision immutable and retain verification metadata.
ALTER TABLE merchant_agreement
    DROP CONSTRAINT IF EXISTS ux_merchant_agreement_restaurant;

ALTER TABLE merchant_agreement
    ADD COLUMN IF NOT EXISTS document_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS signer_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS signature_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SIGNED';

CREATE INDEX IF NOT EXISTS idx_merchant_agreement_latest
    ON merchant_agreement (restaurant_id, signed_at DESC, id DESC);

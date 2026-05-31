ALTER TABLE customer_profiles ADD COLUMN opted_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customer_profiles ADD COLUMN opted_out_at BIGINT;

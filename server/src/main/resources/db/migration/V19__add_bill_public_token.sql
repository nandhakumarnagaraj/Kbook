-- Add public_token to bills for secure, unguessable invoice URLs
ALTER TABLE bills ADD COLUMN public_token UUID DEFAULT gen_random_uuid();

-- Ensure all existing bills get a token
UPDATE bills SET public_token = gen_random_uuid() WHERE public_token IS NULL;

-- Make it NOT NULL for future safety
ALTER TABLE bills ALTER COLUMN public_token SET NOT NULL;

-- Add index for fast lookup
CREATE INDEX idx_bills_public_token ON bills(public_token);

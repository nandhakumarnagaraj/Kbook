-- Idempotency constraint: prevent duplicate bill syncs from multiple terminals.
-- Partial unique index allows NULLs (old bills without public_token).
CREATE UNIQUE INDEX IF NOT EXISTS idx_bills_public_token_unique
  ON bills (public_token)
  WHERE public_token IS NOT NULL;

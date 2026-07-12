-- Persist the sales channel of a bill (in-store POS vs marketplace).
-- Android already stores and pushes `sourceChannel`, but the server had no
-- column, so the value was silently dropped on every sync and never returned
-- on pull. This restores the round-trip.

ALTER TABLE bills ADD COLUMN IF NOT EXISTS source_channel VARCHAR(100) NOT NULL DEFAULT '';

-- Backfill only where the payment mode unambiguously identifies a marketplace
-- channel. Ambiguous in-store modes (cash, upi, pos, splits) are left blank
-- rather than asserting a channel we do not actually know.
UPDATE bills
   SET source_channel = payment_mode
 WHERE (source_channel IS NULL OR source_channel = '')
   AND payment_mode IN ('zomato', 'swiggy', 'own_website');

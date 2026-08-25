-- V87: Primary terminal designation
-- One terminal per restaurant is designated primary (like GPay's primary account).
-- Invariant: exactly one ACTIVE primary per restaurant. Enforced in application code
-- under the restaurant-profile pessimistic lock; the partial unique index below is a
-- DB-level backstop against double-primary corruption.

ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT false;

-- Backfill: oldest ACTIVE terminal per restaurant becomes primary.
-- Restaurants with no active terminals get no primary (assigned on next activation).
UPDATE restaurant_terminal t
SET is_primary = true
WHERE t.status = 'ACTIVE'
  AND t.id = (
    SELECT t2.id FROM restaurant_terminal t2
    WHERE t2.restaurant_id = t.restaurant_id AND t2.status = 'ACTIVE'
    ORDER BY t2.id ASC
    LIMIT 1
  );

-- Backstop: at most one ACTIVE primary per restaurant
CREATE UNIQUE INDEX IF NOT EXISTS ux_restaurant_terminal_primary
    ON restaurant_terminal (restaurant_id)
    WHERE is_primary = true AND status = 'ACTIVE';

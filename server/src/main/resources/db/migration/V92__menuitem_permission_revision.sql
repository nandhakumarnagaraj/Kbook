-- V91: Capture the acting user's permission revision at the time a menu row was
-- created/edited on-device. Enables full Decision-A-strict offline revalidation
-- of menu price/availability changes at sync (see MenuPushAuthorizer + OfflineAuthDecider).
-- Nullable: legacy/older clients that do not stamp it fall back to the P0 grant-only gate.
ALTER TABLE menuitems
    ADD COLUMN IF NOT EXISTS permission_revision_at_creation BIGINT;

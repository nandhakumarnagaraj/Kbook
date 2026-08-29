-- Distributed authorization: permission revision + per-key revocation marker.
--
-- Purpose: let the sync path deterministically decide whether an offline-created
-- operation was authorized, using a monotonic per-user permission revision and a
-- per-permission-key "last revoked at revision" marker.
--
-- This is SEPARATE from terminal credential_version (terminal identity domain).
-- Server remains the final authority; these fields are the mechanism, not a
-- client-trusted value.
--
-- Rule (Decision A, strict): an operation created at revision R is unauthorized
-- for a permission key if that key was revoked at any revision >= R
-- (i.e. last_revoked_revision >= R), even if later re-granted.

-- Per-user monotonic authorization revision.
CREATE TABLE IF NOT EXISTS staff_permission_revision (
    restaurant_id BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    revision      BIGINT NOT NULL DEFAULT 1,
    updated_at    BIGINT NOT NULL,
    PRIMARY KEY (restaurant_id, user_id)
);

-- Per-key marker: the revision at which this key was most recently revoked.
-- NULL = never revoked. Used by sync revalidation (Decision A strict).
ALTER TABLE staff_permissions ADD COLUMN IF NOT EXISTS last_revoked_revision BIGINT;

-- Backfill: every existing (restaurant,user) that has any permission row starts at revision 1.
INSERT INTO staff_permission_revision (restaurant_id, user_id, revision, updated_at)
SELECT DISTINCT restaurant_id, user_id, 1, EXTRACT(EPOCH FROM now()) * 1000
FROM staff_permissions
ON CONFLICT (restaurant_id, user_id) DO NOTHING;

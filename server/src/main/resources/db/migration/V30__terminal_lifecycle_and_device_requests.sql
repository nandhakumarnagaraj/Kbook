-- V30: Terminal lifecycle management + device registration requests
-- Part of SHOP_ADMIN + 5-terminal-limit feature (Phase 2)

-- 1. Add 'status' column to restaurant_terminal (source of truth for lifecycle state)
--    Existing is_active boolean is retained for backward compatibility but must always
--    equal (status = 'ACTIVE'). Application code sets both atomically.
ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

-- Backfill status from existing is_active
UPDATE restaurant_terminal SET status = CASE WHEN is_active = true THEN 'ACTIVE' ELSE 'INACTIVE' END
WHERE status = 'ACTIVE' AND is_active = false;

-- 2. Add credential_version for immediate token revocation on recovery/deactivation.
--    Terminal JWT embeds this version; TerminalRequestFilter compares against DB value.
--    Incrementing revokes all previously-issued terminal tokens for this terminal.
ALTER TABLE restaurant_terminal ADD COLUMN IF NOT EXISTS credential_version BIGINT NOT NULL DEFAULT 1;

-- 3. Index on restaurant_id + status for fast active-count queries
CREATE INDEX IF NOT EXISTS idx_restaurant_terminal_status ON restaurant_terminal (restaurant_id, status);

-- 4. Device registration request table (pending devices that are NOT yet terminals)
CREATE TABLE IF NOT EXISTS device_registration_request (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    device_model VARCHAR(255),
    device_name VARCHAR(255),
    request_type VARCHAR(50) NOT NULL DEFAULT 'NEW_DEVICE',
    -- NEW_DEVICE: brand new terminal request
    -- RECOVERY: reinstall recovery of existing terminal
    -- REPLACEMENT: physical device replacement for existing terminal
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING, APPROVED, REJECTED, EXPIRED
    matched_terminal_id BIGINT,
    requested_at BIGINT NOT NULL,
    processed_at BIGINT,
    processed_by_user_id BIGINT,
    rejection_reason VARCHAR(500),
    assigned_terminal_id BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- Partial unique index: at most one PENDING request per device per restaurant
CREATE UNIQUE INDEX IF NOT EXISTS ux_device_request_pending
    ON device_registration_request (restaurant_id, device_id)
    WHERE status = 'PENDING';

-- Lookup indexes
CREATE INDEX IF NOT EXISTS idx_device_request_restaurant_status
    ON device_registration_request (restaurant_id, status);

CREATE INDEX IF NOT EXISTS idx_device_request_restaurant_requested
    ON device_registration_request (restaurant_id, requested_at);

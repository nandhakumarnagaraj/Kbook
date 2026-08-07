-- V72: Dynamic custom permissions system
-- Supports: granular per-user permissions, request/approve flow, role templates

-- Staff permissions: what each user can do
CREATE TABLE IF NOT EXISTS staff_permissions (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    permission_key  VARCHAR(100) NOT NULL,
    granted         BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by      BIGINT,
    granted_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    revoked_at      BIGINT,
    updated_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,

    CONSTRAINT uk_staff_permission UNIQUE (restaurant_id, user_id, permission_key)
);

CREATE INDEX idx_staff_perm_user ON staff_permissions (user_id, restaurant_id);
CREATE INDEX idx_staff_perm_restaurant ON staff_permissions (restaurant_id);

-- Permission requests: staff requesting access from owner
CREATE TABLE IF NOT EXISTS permission_requests (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    permission_key  VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason          TEXT,
    requested_at    BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    resolved_by     BIGINT,
    resolved_at     BIGINT,
    rejection_reason TEXT,

    CONSTRAINT chk_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_perm_req_restaurant ON permission_requests (restaurant_id, status);
CREATE INDEX idx_perm_req_user ON permission_requests (user_id, status);

-- Role templates: preset permission bundles (e.g., "Counter Staff", "Kitchen")
CREATE TABLE IF NOT EXISTS role_templates (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    permissions     TEXT NOT NULL,  -- JSON array of permission_key strings
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      BIGINT,
    created_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,

    CONSTRAINT uk_role_template_name UNIQUE (restaurant_id, name)
);

-- Seed default templates (applied per-restaurant on first access)
-- Actual seeding happens in application code, not here.

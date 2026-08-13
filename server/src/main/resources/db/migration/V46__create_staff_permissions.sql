-- V46: Staff permissions system
-- Supports: granular per-user permission toggles, role templates, discount limits

-- Staff permissions: what each user can/cannot do
CREATE TABLE IF NOT EXISTS staff_permissions (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    permission_key  VARCHAR(100) NOT NULL,
    granted         BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by      BIGINT,
    granted_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,

    CONSTRAINT uk_staff_permission UNIQUE (restaurant_id, user_id, permission_key),
    CONSTRAINT fk_staff_perm_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant_profiles(id),
    CONSTRAINT fk_staff_perm_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_staff_perm_user ON staff_permissions (user_id, restaurant_id);
CREATE INDEX idx_staff_perm_restaurant ON staff_permissions (restaurant_id);

-- Role templates: reusable permission bundles (e.g., "Counter Staff", "Manager")
CREATE TABLE IF NOT EXISTS role_templates (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    permissions     TEXT NOT NULL,  -- JSON array of permission_key strings
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      BIGINT,
    created_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,

    CONSTRAINT uk_role_template_name UNIQUE (restaurant_id, name),
    CONSTRAINT fk_role_template_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant_profiles(id)
);

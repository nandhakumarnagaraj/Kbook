CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT,
    user_id VARCHAR(255),
    terminal_id VARCHAR(100),
    terminal_series VARCHAR(100),
    target_canonical_id VARCHAR(255),
    target_owner_terminal VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_security_audit_restaurant
    ON security_audit_log (restaurant_id, created_at);

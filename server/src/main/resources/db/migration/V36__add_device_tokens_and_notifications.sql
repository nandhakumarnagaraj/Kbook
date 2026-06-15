-- Device tokens for FCM push notifications
CREATE TABLE IF NOT EXISTS device_tokens (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) DEFAULT 'android',
    device_id VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_restaurant ON device_tokens(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_device_tokens_token ON device_tokens(token);

-- Notification events for push notification history and in-app display
CREATE TABLE IF NOT EXISTS notification_events (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    reference_id VARCHAR(255),
    reference_type VARCHAR(50),
    amount NUMERIC(12,2),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_pushed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    read_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_notification_events_restaurant ON notification_events(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_notification_events_read ON notification_events(restaurant_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notification_events_type ON notification_events(restaurant_id, notification_type);

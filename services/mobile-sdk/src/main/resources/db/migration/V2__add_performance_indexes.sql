-- Add additional indexes and constraints for mobile SDK performance optimization

-- Composite indexes for common query patterns
CREATE INDEX idx_mobile_devices_user_trusted ON mobile_devices(user_id, is_trusted);
CREATE INDEX idx_mobile_devices_user_risk ON mobile_devices(user_id, risk_score);
CREATE INDEX idx_mobile_devices_type_status ON mobile_devices(device_type, status);
CREATE INDEX idx_mobile_devices_activity_status ON mobile_devices(last_activity_at, status) WHERE status = 'ACTIVE';

-- Session performance indexes
CREATE INDEX idx_mobile_sessions_user_security ON mobile_sessions(user_id, security_level);
CREATE INDEX idx_mobile_sessions_device_created ON mobile_sessions(device_id, created_at);
CREATE INDEX idx_mobile_sessions_active_expires ON mobile_sessions(is_active, expires_at) WHERE is_active = TRUE;

-- Notification performance indexes
CREATE INDEX idx_push_notifications_status_retry ON push_notifications(status, retry_count, created_at) WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_push_notifications_delivery_read ON push_notifications(delivery_status, read_at);
CREATE INDEX idx_push_notifications_user_created ON push_notifications(user_id, created_at DESC);

-- Partial indexes for active data
CREATE INDEX idx_mobile_devices_active_push ON mobile_devices(user_id, push_token) 
    WHERE status = 'ACTIVE' AND push_enabled = TRUE AND push_token IS NOT NULL;

CREATE INDEX idx_mobile_sessions_recent_active ON mobile_sessions(user_id, last_activity_at DESC) 
    WHERE is_active = TRUE AND expires_at > CURRENT_TIMESTAMP;

-- Functional indexes for text search
CREATE INDEX idx_mobile_devices_device_name_lower ON mobile_devices(LOWER(device_name));
CREATE INDEX idx_push_notifications_title_gin ON push_notifications USING gin(to_tsvector('english', title));

-- Check constraints for data integrity
ALTER TABLE mobile_devices ADD CONSTRAINT chk_mobile_devices_timestamps 
    CHECK (last_activity_at >= registered_at);

ALTER TABLE mobile_sessions ADD CONSTRAINT chk_mobile_sessions_timestamps 
    CHECK (expires_at > created_at);

ALTER TABLE mobile_sessions ADD CONSTRAINT chk_mobile_sessions_refresh_expires 
    CHECK (refresh_expires_at IS NULL OR refresh_expires_at > expires_at);

ALTER TABLE push_notifications ADD CONSTRAINT chk_push_notifications_delivery_timestamps 
    CHECK (
        (sent_at IS NULL OR sent_at >= created_at) AND
        (delivered_at IS NULL OR (sent_at IS NOT NULL AND delivered_at >= sent_at)) AND
        (read_at IS NULL OR (delivered_at IS NOT NULL AND read_at >= delivered_at))
    );

-- Add foreign key constraints for referential integrity
-- Note: We can't add FK to users table as it's in a different service
-- These would be handled at application level or through database views

-- Comments for new constraints
COMMENT ON CONSTRAINT chk_mobile_devices_timestamps ON mobile_devices IS 'Ensure last_activity_at is not before registration';
COMMENT ON CONSTRAINT chk_mobile_sessions_timestamps ON mobile_sessions IS 'Ensure session expires after creation';
COMMENT ON CONSTRAINT chk_mobile_sessions_refresh_expires ON mobile_sessions IS 'Ensure refresh token expires after session token';
COMMENT ON CONSTRAINT chk_push_notifications_delivery_timestamps ON push_notifications IS 'Ensure notification delivery timestamp progression is logical';

-- Mobile SDK Database Schema
-- Create tables for mobile device management, sessions, and push notifications

-- Mobile Devices Table
CREATE TABLE mobile_devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    device_type VARCHAR(50) NOT NULL CHECK (device_type IN ('ANDROID', 'IOS', 'WEB')),
    device_name VARCHAR(255) NOT NULL,
    operating_system VARCHAR(100),
    os_version VARCHAR(50),
    app_version VARCHAR(50),
    device_fingerprint TEXT,
    push_token TEXT,
    
    -- Security attributes
    biometric_enabled BOOLEAN DEFAULT FALSE,
    pin_enabled BOOLEAN DEFAULT FALSE,
    location_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    is_trusted BOOLEAN DEFAULT FALSE,
    is_rooted_or_jailbroken BOOLEAN DEFAULT FALSE,
    risk_score DECIMAL(4,2) DEFAULT 5.0 CHECK (risk_score >= 0.0 AND risk_score <= 10.0),
    
    -- Status and tracking
    status VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'PENDING_VERIFICATION', 'REMOVED')),
    registration_ip INET,
    registration_location TEXT,
    last_ip_address INET,
    last_location TEXT,
    
    -- Timestamps
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for mobile_devices
CREATE INDEX idx_mobile_devices_user_id ON mobile_devices(user_id);
CREATE INDEX idx_mobile_devices_device_id ON mobile_devices(device_id);
CREATE INDEX idx_mobile_devices_status ON mobile_devices(status);
CREATE INDEX idx_mobile_devices_push_token ON mobile_devices(push_token) WHERE push_token IS NOT NULL;
CREATE INDEX idx_mobile_devices_risk_score ON mobile_devices(risk_score);
CREATE INDEX idx_mobile_devices_last_activity ON mobile_devices(last_activity_at);
CREATE INDEX idx_mobile_devices_user_status ON mobile_devices(user_id, status);

-- Mobile Sessions Table
CREATE TABLE mobile_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_token TEXT NOT NULL UNIQUE,
    refresh_token TEXT UNIQUE,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL REFERENCES mobile_devices(id) ON DELETE CASCADE,
    
    -- Session details
    login_method VARCHAR(50),
    ip_address INET,
    location TEXT,
    user_agent TEXT,
    security_level INTEGER DEFAULT 1 CHECK (security_level >= 1 AND security_level <= 3),
    risk_score DECIMAL(4,2) DEFAULT 5.0 CHECK (risk_score >= 0.0 AND risk_score <= 10.0),
    
    -- Authentication flags
    mfa_verified BOOLEAN DEFAULT FALSE,
    biometric_verified BOOLEAN DEFAULT FALSE,
    pin_verified BOOLEAN DEFAULT FALSE,
    
    -- Session lifecycle
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE,
    termination_reason TEXT,
    terminated_at TIMESTAMP WITH TIME ZONE,
    
    -- Activity tracking
    activity_count BIGINT DEFAULT 0,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for mobile_sessions
CREATE INDEX idx_mobile_sessions_session_token ON mobile_sessions(session_token);
CREATE INDEX idx_mobile_sessions_refresh_token ON mobile_sessions(refresh_token) WHERE refresh_token IS NOT NULL;
CREATE INDEX idx_mobile_sessions_user_id ON mobile_sessions(user_id);
CREATE INDEX idx_mobile_sessions_device_id ON mobile_sessions(device_id);
CREATE INDEX idx_mobile_sessions_user_active ON mobile_sessions(user_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_mobile_sessions_expires_at ON mobile_sessions(expires_at);
CREATE INDEX idx_mobile_sessions_risk_score ON mobile_sessions(risk_score);
CREATE INDEX idx_mobile_sessions_created_at ON mobile_sessions(created_at);
CREATE INDEX idx_mobile_sessions_device_active ON mobile_sessions(device_id, is_active) WHERE is_active = TRUE;

-- Push Notifications Table
CREATE TABLE push_notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL REFERENCES mobile_devices(id) ON DELETE CASCADE,
    
    -- Notification content
    type VARCHAR(50) NOT NULL CHECK (type IN ('TRANSACTION', 'SECURITY', 'MARKETING', 'SYSTEM', 'ALERT')),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    data JSONB,
    
    -- Notification settings
    badge_count INTEGER DEFAULT 0,
    sound VARCHAR(100),
    category VARCHAR(100),
    priority VARCHAR(20) DEFAULT 'normal' CHECK (priority IN ('low', 'normal', 'high')),
    deep_link TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Delivery tracking
    status VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    delivery_status VARCHAR(50) DEFAULT 'UNKNOWN' CHECK (delivery_status IN ('UNKNOWN', 'DELIVERED', 'READ', 'FAILED')),
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    
    -- Timestamps
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for push_notifications
CREATE INDEX idx_push_notifications_user_id ON push_notifications(user_id);
CREATE INDEX idx_push_notifications_device_id ON push_notifications(device_id);
CREATE INDEX idx_push_notifications_type ON push_notifications(type);
CREATE INDEX idx_push_notifications_status ON push_notifications(status);
CREATE INDEX idx_push_notifications_delivery_status ON push_notifications(delivery_status);
CREATE INDEX idx_push_notifications_created_at ON push_notifications(created_at);
CREATE INDEX idx_push_notifications_user_type ON push_notifications(user_id, type);
CREATE INDEX idx_push_notifications_expires_at ON push_notifications(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_push_notifications_retry ON push_notifications(status, retry_count) WHERE status = 'FAILED';

-- Update timestamp triggers
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
CREATE TRIGGER update_mobile_devices_updated_at 
    BEFORE UPDATE ON mobile_devices 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_mobile_sessions_updated_at 
    BEFORE UPDATE ON mobile_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_push_notifications_updated_at 
    BEFORE UPDATE ON push_notifications 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE mobile_devices IS 'Registered mobile devices for users';
COMMENT ON TABLE mobile_sessions IS 'Active and historical mobile sessions';
COMMENT ON TABLE push_notifications IS 'Push notification delivery tracking';

COMMENT ON COLUMN mobile_devices.device_fingerprint IS 'Unique device fingerprint for security';
COMMENT ON COLUMN mobile_devices.risk_score IS 'Device risk score from 0.0 (safe) to 10.0 (high risk)';
COMMENT ON COLUMN mobile_sessions.security_level IS '1=Basic, 2=Enhanced, 3=High security';
COMMENT ON COLUMN push_notifications.data IS 'Additional notification data as JSON';

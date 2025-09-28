-- Create database views and functions for mobile SDK analytics and reporting

-- View: Device Summary Statistics
CREATE OR REPLACE VIEW v_device_summary_stats AS
SELECT 
    user_id,
    COUNT(*) as total_devices,
    COUNT(*) FILTER (WHERE status = 'ACTIVE') as active_devices,
    COUNT(*) FILTER (WHERE is_trusted = TRUE) as trusted_devices,
    COUNT(*) FILTER (WHERE risk_score > 7.0) as high_risk_devices,
    COUNT(*) FILTER (WHERE biometric_enabled = TRUE OR pin_enabled = TRUE) as secured_devices,
    COUNT(*) FILTER (WHERE device_type = 'ANDROID') as android_devices,
    COUNT(*) FILTER (WHERE device_type = 'IOS') as ios_devices,
    AVG(risk_score) as avg_risk_score,
    MAX(last_activity_at) as last_device_activity,
    MIN(registered_at) as first_device_registered
FROM mobile_devices
GROUP BY user_id;

-- View: Session Analytics
CREATE OR REPLACE VIEW v_session_analytics AS
SELECT 
    user_id,
    device_id,
    DATE_TRUNC('day', created_at) as session_date,
    COUNT(*) as session_count,
    COUNT(*) FILTER (WHERE security_level >= 2) as elevated_sessions,
    COUNT(*) FILTER (WHERE mfa_verified = TRUE) as mfa_sessions,
    COUNT(*) FILTER (WHERE biometric_verified = TRUE) as biometric_sessions,
    AVG(risk_score) as avg_session_risk,
    AVG(EXTRACT(EPOCH FROM (COALESCE(terminated_at, CURRENT_TIMESTAMP) - created_at))/60) as avg_duration_minutes,
    SUM(activity_count) as total_activities
FROM mobile_sessions
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY user_id, device_id, DATE_TRUNC('day', created_at);

-- View: Notification Performance
CREATE OR REPLACE VIEW v_notification_performance AS
SELECT 
    user_id,
    type,
    DATE_TRUNC('day', created_at) as notification_date,
    COUNT(*) as total_sent,
    COUNT(*) FILTER (WHERE delivery_status = 'DELIVERED') as delivered,
    COUNT(*) FILTER (WHERE delivery_status = 'READ') as read,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed,
    ROUND(
        COUNT(*) FILTER (WHERE delivery_status = 'DELIVERED')::DECIMAL / 
        NULLIF(COUNT(*), 0) * 100, 2
    ) as delivery_rate_pct,
    ROUND(
        COUNT(*) FILTER (WHERE delivery_status = 'READ')::DECIMAL / 
        NULLIF(COUNT(*) FILTER (WHERE delivery_status = 'DELIVERED'), 0) * 100, 2
    ) as read_rate_pct,
    AVG(EXTRACT(EPOCH FROM (delivered_at - sent_at))/60) FILTER (WHERE delivered_at IS NOT NULL AND sent_at IS NOT NULL) as avg_delivery_time_minutes
FROM push_notifications
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY user_id, type, DATE_TRUNC('day', created_at);

-- Function: Clean up expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    cleaned_count INTEGER;
BEGIN
    UPDATE mobile_sessions 
    SET 
        is_active = FALSE,
        termination_reason = 'Session expired',
        terminated_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE 
        is_active = TRUE 
        AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS cleaned_count = ROW_COUNT;
    
    RETURN cleaned_count;
END;
$$ LANGUAGE plpgsql;

-- Function: Get user device security score
CREATE OR REPLACE FUNCTION get_user_device_security_score(p_user_id UUID)
RETURNS DECIMAL(4,2) AS $$
DECLARE
    security_score DECIMAL(4,2);
BEGIN
    SELECT 
        CASE 
            WHEN COUNT(*) = 0 THEN 0.0
            ELSE (
                -- Base score starts at 10, deduct points for risks
                10.0 
                - (COUNT(*) FILTER (WHERE is_rooted_or_jailbroken = TRUE) * 3.0)  -- -3 per rooted device
                - (COUNT(*) FILTER (WHERE NOT (biometric_enabled OR pin_enabled)) * 2.0)  -- -2 per unsecured device
                - (COUNT(*) FILTER (WHERE risk_score > 7.0) * 1.5)  -- -1.5 per high-risk device
                - (COUNT(*) FILTER (WHERE status != 'ACTIVE') * 1.0)  -- -1 per inactive device
                + (COUNT(*) FILTER (WHERE is_trusted = TRUE) * 0.5)  -- +0.5 per trusted device
            )
        END
    INTO security_score
    FROM mobile_devices
    WHERE user_id = p_user_id;
    
    -- Ensure score is between 0 and 10
    RETURN GREATEST(0.0, LEAST(10.0, COALESCE(security_score, 0.0)));
END;
$$ LANGUAGE plpgsql;

-- Function: Update device activity
CREATE OR REPLACE FUNCTION update_device_activity(
    p_device_id UUID,
    p_ip_address INET DEFAULT NULL,
    p_location TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
BEGIN
    UPDATE mobile_devices 
    SET 
        last_activity_at = CURRENT_TIMESTAMP,
        last_ip_address = COALESCE(p_ip_address, last_ip_address),
        last_location = COALESCE(p_location, last_location),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = p_device_id;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- Function: Get notification statistics
CREATE OR REPLACE FUNCTION get_notification_stats(
    p_user_id UUID,
    p_from_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_DATE - INTERVAL '30 days',
    p_to_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
)
RETURNS TABLE(
    notification_type VARCHAR(50),
    total_count BIGINT,
    delivered_count BIGINT,
    read_count BIGINT,
    failed_count BIGINT,
    delivery_rate DECIMAL(5,2),
    read_rate DECIMAL(5,2)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        pn.type as notification_type,
        COUNT(*) as total_count,
        COUNT(*) FILTER (WHERE pn.delivery_status = 'DELIVERED') as delivered_count,
        COUNT(*) FILTER (WHERE pn.delivery_status = 'READ') as read_count,
        COUNT(*) FILTER (WHERE pn.status = 'FAILED') as failed_count,
        ROUND(
            COUNT(*) FILTER (WHERE pn.delivery_status = 'DELIVERED')::DECIMAL / 
            NULLIF(COUNT(*), 0) * 100, 2
        ) as delivery_rate,
        ROUND(
            COUNT(*) FILTER (WHERE pn.delivery_status = 'READ')::DECIMAL / 
            NULLIF(COUNT(*) FILTER (WHERE pn.delivery_status = 'DELIVERED'), 0) * 100, 2
        ) as read_rate
    FROM push_notifications pn
    WHERE 
        pn.user_id = p_user_id
        AND pn.created_at >= p_from_date
        AND pn.created_at <= p_to_date
    GROUP BY pn.type
    ORDER BY total_count DESC;
END;
$$ LANGUAGE plpgsql;

-- Create indexes on views for better performance
CREATE INDEX idx_mobile_devices_user_summary ON mobile_devices(user_id, status, is_trusted, risk_score);
CREATE INDEX idx_mobile_sessions_analytics ON mobile_sessions(user_id, device_id, created_at, security_level);
CREATE INDEX idx_push_notifications_performance ON push_notifications(user_id, type, created_at, delivery_status, status);

-- Comments for database objects
COMMENT ON VIEW v_device_summary_stats IS 'Summary statistics for user devices including security metrics';
COMMENT ON VIEW v_session_analytics IS 'Daily session analytics per user and device';
COMMENT ON VIEW v_notification_performance IS 'Daily notification delivery and engagement metrics';
COMMENT ON FUNCTION cleanup_expired_sessions() IS 'Marks expired sessions as inactive';
COMMENT ON FUNCTION get_user_device_security_score(UUID) IS 'Calculates overall device security score for a user';
COMMENT ON FUNCTION update_device_activity(UUID, INET, TEXT) IS 'Updates device last activity with optional location data';
COMMENT ON FUNCTION get_notification_stats(UUID, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) IS 'Returns notification statistics for a user within date range';

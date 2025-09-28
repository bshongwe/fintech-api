-- Admin Dashboard Database Schema
-- Creates tables for admin user management, system alerts, and metrics

-- Admin Permissions Table
CREATE TABLE admin_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(200),
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Admin Roles Table
CREATE TABLE admin_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(200),
    level INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Admin Role Permissions Junction Table
CREATE TABLE admin_role_permissions (
    role_id UUID NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES admin_permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Admin Users Table
CREATE TABLE admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Admin User Roles Junction Table
CREATE TABLE admin_user_roles (
    admin_user_id UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (admin_user_id, role_id)
);

-- System Alerts Table
CREATE TABLE system_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(100) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    category VARCHAR(30) NOT NULL,
    service VARCHAR(50) NOT NULL,
    threshold_value DECIMAL(19,4),
    actual_value DECIMAL(19,4),
    occurrence_count INTEGER DEFAULT 1,
    first_occurrence TIMESTAMP,
    last_occurrence TIMESTAMP,
    acknowledged_by UUID REFERENCES admin_users(id),
    acknowledged_at TIMESTAMP,
    resolved_by UUID REFERENCES admin_users(id),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Alert Metadata Table
CREATE TABLE alert_metadata (
    alert_id UUID NOT NULL REFERENCES system_alerts(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    PRIMARY KEY (alert_id, key)
);

-- System Metrics Table
CREATE TABLE system_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service VARCHAR(50) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(19,4),
    metric_unit VARCHAR(20),
    metric_type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Metric Tags Table
CREATE TABLE metric_tags (
    metric_id UUID NOT NULL REFERENCES system_metrics(id) ON DELETE CASCADE,
    tag_key VARCHAR(100) NOT NULL,
    tag_value VARCHAR(255),
    PRIMARY KEY (metric_id, tag_key)
);

-- Create Indexes for Performance
CREATE INDEX idx_admin_username ON admin_users(username);
CREATE INDEX idx_admin_email ON admin_users(email);
CREATE INDEX idx_admin_status ON admin_users(status);

CREATE INDEX idx_role_name ON admin_roles(name);
CREATE INDEX idx_role_level ON admin_roles(level);

CREATE INDEX idx_permission_name ON admin_permissions(name);
CREATE INDEX idx_permission_resource ON admin_permissions(resource);
CREATE INDEX idx_permission_action ON admin_permissions(action);

CREATE INDEX idx_alert_severity ON system_alerts(severity);
CREATE INDEX idx_alert_status ON system_alerts(status);
CREATE INDEX idx_alert_service ON system_alerts(service);
CREATE INDEX idx_alert_created_at ON system_alerts(created_at);
CREATE INDEX idx_alert_resolved_at ON system_alerts(resolved_at);

CREATE INDEX idx_metrics_service ON system_metrics(service);
CREATE INDEX idx_metrics_name ON system_metrics(metric_name);
CREATE INDEX idx_metrics_timestamp ON system_metrics(timestamp);
CREATE INDEX idx_metrics_service_name_timestamp ON system_metrics(service, metric_name, timestamp);

-- Insert Default Permissions
INSERT INTO admin_permissions (name, description, resource, action) VALUES
-- User Management
('users:read', 'View admin users', 'users', 'read'),
('users:write', 'Create and update admin users', 'users', 'write'),
('users:delete', 'Delete admin users', 'users', 'delete'),

-- Role Management
('roles:read', 'View admin roles', 'roles', 'read'),
('roles:write', 'Create and update admin roles', 'roles', 'write'),
('roles:delete', 'Delete admin roles', 'roles', 'delete'),

-- Alert Management
('alerts:read', 'View system alerts', 'alerts', 'read'),
('alerts:write', 'Acknowledge and resolve alerts', 'alerts', 'write'),

-- System Monitoring
('monitoring:read', 'View system monitoring data', 'monitoring', 'read'),

-- Reports
('reports:read', 'View reports', 'reports', 'read'),
('reports:generate', 'Generate reports', 'reports', 'generate'),

-- Compliance
('compliance:read', 'View compliance data', 'compliance', 'read'),
('compliance:write', 'Manage compliance policies', 'compliance', 'write'),

-- Fraud Investigation
('fraud:read', 'View fraud detection data', 'fraud', 'read'),
('fraud:investigate', 'Conduct fraud investigations', 'fraud', 'investigate'),

-- System Administration
('system:read', 'View system configuration', 'system', 'read'),
('system:write', 'Modify system configuration', 'system', 'write');

-- Insert Default Roles
INSERT INTO admin_roles (name, description, level) VALUES
('SUPER_ADMIN', 'Super Administrator with full system access', 1),
('ADMIN', 'Administrator with most system access', 2),
('MANAGER', 'Manager with operational oversight', 3),
('ANALYST', 'Analyst with read-only access to most data', 4),
('OPERATOR', 'Operator with limited operational access', 5);

-- Assign Permissions to Roles
-- Super Admin gets all permissions
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM admin_roles r, admin_permissions p 
WHERE r.name = 'SUPER_ADMIN';

-- Admin gets most permissions except system write
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM admin_roles r, admin_permissions p 
WHERE r.name = 'ADMIN' 
AND p.name NOT IN ('system:write');

-- Manager gets monitoring, alerts, and reports
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM admin_roles r, admin_permissions p 
WHERE r.name = 'MANAGER' 
AND p.name IN (
    'users:read', 'alerts:read', 'alerts:write', 'monitoring:read', 
    'reports:read', 'reports:generate', 'compliance:read', 'fraud:read'
);

-- Analyst gets read access to most data
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM admin_roles r, admin_permissions p 
WHERE r.name = 'ANALYST' 
AND p.action = 'read';

-- Operator gets basic operational access
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM admin_roles r, admin_permissions p 
WHERE r.name = 'OPERATOR' 
AND p.name IN (
    'alerts:read', 'alerts:write', 'monitoring:read', 'reports:read'
);

-- Create Default Super Admin User (password will need to be set on first deployment)
INSERT INTO admin_users (username, email, first_name, last_name, password_hash, status) 
VALUES (
    'superadmin', 
    'admin@fintech.com', 
    'Super', 
    'Administrator', 
    '$2a$12$placeholder.hash.will.be.replaced', 
    'ACTIVE'
);

-- Assign Super Admin role to default user
INSERT INTO admin_user_roles (admin_user_id, role_id)
SELECT u.id, r.id 
FROM admin_users u, admin_roles r 
WHERE u.username = 'superadmin' AND r.name = 'SUPER_ADMIN';

-- Initialize mobile SDK database with required extensions and settings

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Set timezone
SET timezone = 'UTC';

-- Create application user if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'mobilesdk_user') THEN
        CREATE ROLE mobilesdk_user LOGIN PASSWORD 'mobilesdk_password';
    END IF;
END
$$;

-- Grant permissions
GRANT CONNECT ON DATABASE mobilesdk_db TO mobilesdk_user;
GRANT USAGE ON SCHEMA public TO mobilesdk_user;
GRANT CREATE ON SCHEMA public TO mobilesdk_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mobilesdk_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO mobilesdk_user;

-- Create audit schema for compliance
CREATE SCHEMA IF NOT EXISTS audit;
GRANT USAGE ON SCHEMA audit TO mobilesdk_user;
GRANT CREATE ON SCHEMA audit TO mobilesdk_user;

-- Performance settings
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET log_statement = 'all';
ALTER SYSTEM SET log_duration = on;
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- Reload configuration
SELECT pg_reload_conf();

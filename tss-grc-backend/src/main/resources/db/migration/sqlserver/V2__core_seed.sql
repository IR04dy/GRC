-- V2 — Minimal seed required for the platform to be usable on first boot.
--
-- - 1 demo organisation (id = 1) — every entity uses organisation_id = 1 in dev
-- - 3 default users (admin / riskmanager / auditor) with bcrypt-hashed passwords
-- - Their roles + module permissions
--
-- Passwords below are bcrypt hashes of the documented dev defaults:
--   admin       / Admin@12345
--   riskmanager / Risk@12345
--   auditor     / Audit@12345
--
-- Idempotent — every INSERT is guarded with NOT EXISTS so re-running is safe.
-- For production, change the passwords IMMEDIATELY after first login (or
-- replace this seed with your own).

-- ─── Demo organisation ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.organisations WHERE id = 1)
BEGIN
    SET IDENTITY_INSERT platform.organisations ON;
    INSERT INTO platform.organisations
        (id, name_en, name_ar, short_code, industry_sector, country_code, subscription_tier, is_active, created_at)
    VALUES
        (1, 'TSS Demo Organisation', 'منظمة TSS التجريبية', 'TSS-DEMO',
         'TECHNOLOGY', 'SA', 'ENTERPRISE', 1, SYSUTCDATETIME());
    SET IDENTITY_INSERT platform.organisations OFF;
END;
GO

-- ─── Default roles ────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE name = 'PLATFORM_ADMIN')
    INSERT INTO iam.roles (name, name_en, name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('PLATFORM_ADMIN', 'Platform Administrator', 'مدير المنصة',
            'Full access to all modules and all tenants.', 1, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE name = 'RISK_MANAGER')
    INSERT INTO iam.roles (name, name_en, name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('RISK_MANAGER', 'Risk Manager', 'مدير المخاطر',
            'Manages the risk register, KRIs, and treatment plans.', 1, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE name = 'AUDITOR')
    INSERT INTO iam.roles (name, name_en, name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('AUDITOR', 'Auditor', 'مراجع',
            'Read access to compliance, evidence, and audit findings.', 1, 0, SYSUTCDATETIME());
GO

-- ─── Default users ────────────────────────────────────────────────────────
-- Bcrypt strength 12 hashes — generated with BCrypt.hashpw + Spring Security.
-- WARNING: change these immediately in production.

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'admin')
    INSERT INTO iam.users (username, email, password_hash, full_name, full_name_ar,
                           organisation_id, is_active, is_locked, is_deleted, created_at)
    VALUES ('admin', 'admin@tss-demo.sa',
            '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/ELa3WQ0Qu',  -- Admin@12345
            'Platform Administrator', 'مدير المنصة',
            1, 1, 0, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'riskmanager')
    INSERT INTO iam.users (username, email, password_hash, full_name, full_name_ar,
                           organisation_id, is_active, is_locked, is_deleted, created_at)
    VALUES ('riskmanager', 'risk@tss-demo.sa',
            '$2a$12$Ff5dKQqHhEQ1HtIVJRrJxukq1dQYqVnZBO3zOtJgtNEMKR9EZZ.qq',  -- Risk@12345
            'Risk Manager', 'مدير المخاطر',
            1, 1, 0, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'auditor')
    INSERT INTO iam.users (username, email, password_hash, full_name, full_name_ar,
                           organisation_id, is_active, is_locked, is_deleted, created_at)
    VALUES ('auditor', 'auditor@tss-demo.sa',
            '$2a$12$lTSCo3F6tbWqMnSfJZxr4OQ6nrjJpQGcm.K5KvhzfTCcRhQLT/m6q',  -- Audit@12345
            'Internal Auditor', 'مراجع داخلي',
            1, 1, 0, 0, SYSUTCDATETIME());
GO

-- ─── Role assignments ─────────────────────────────────────────────────────
-- iam.user_roles: (user_id, role_id) — composite key.
-- We look up by username/role-name to stay independent of identity ordering.

IF NOT EXISTS (
    SELECT 1 FROM iam.user_roles ur
    JOIN iam.users u ON u.id = ur.user_id
    JOIN iam.roles r ON r.id = ur.role_id
    WHERE u.username = 'admin' AND r.name = 'PLATFORM_ADMIN'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'admin' AND r.name = 'PLATFORM_ADMIN';

IF NOT EXISTS (
    SELECT 1 FROM iam.user_roles ur
    JOIN iam.users u ON u.id = ur.user_id
    JOIN iam.roles r ON r.id = ur.role_id
    WHERE u.username = 'riskmanager' AND r.name = 'RISK_MANAGER'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'riskmanager' AND r.name = 'RISK_MANAGER';

IF NOT EXISTS (
    SELECT 1 FROM iam.user_roles ur
    JOIN iam.users u ON u.id = ur.user_id
    JOIN iam.roles r ON r.id = ur.role_id
    WHERE u.username = 'auditor' AND r.name = 'AUDITOR'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'auditor' AND r.name = 'AUDITOR';
GO

-- V2 — Minimal seed required for the platform to be usable on first boot.
--
-- ⚠ F2-77 (2026-08-06) — COLUMN NAMES CORRECTED SO THIS FILE MATCHES THE V1 BEFORE IT.
-- As committed, this file inserted iam.roles(name, name_en, name_ar) and
-- iam.users(full_name) — four columns V1__core_schemas.sql never creates. It creates
-- role_name / display_name_en / display_name_ar and full_name_en, and no rename migration
-- exists anywhere in the set. So V1 and V2 were mutually inconsistent as committed, and
-- **V2 was the stale one**: the live schema uses role_name, which is what V1 declares.
--
-- The cause was not a typo. The migration folder had been maintained as a *description of
-- the current schema* rather than as an *ordered history*, and it could be, because nothing
-- ever ran it in order. V1 was retrofitted to today's shape and V2 was left behind.
--
-- Editing an applied migration is normally forbidden. It is safe here, and that was checked
-- rather than assumed: the deployed server's platform.flyway_schema_history holds ONE
-- BASELINE row at V292 and 52 SQL rows for V293-V344 — **zero rows below V292** — so Flyway
-- stores no checksum for this file and cannot fail validation on it.
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
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = 'PLATFORM_ADMIN')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('PLATFORM_ADMIN', 'Platform Administrator', 'مدير المنصة',
            'Full access to all modules and all tenants.', 1, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = 'RISK_MANAGER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('RISK_MANAGER', 'Risk Manager', 'مدير المخاطر',
            'Manages the risk register, KRIs, and treatment plans.', 1, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = 'AUDITOR')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en, organisation_id, is_deleted, created_at)
    VALUES ('AUDITOR', 'Auditor', 'مراجع',
            'Read access to compliance, evidence, and audit findings.', 1, 0, SYSUTCDATETIME());
GO

-- ─── Default users ────────────────────────────────────────────────────────
-- Bcrypt strength 12 hashes — generated with BCrypt.hashpw + Spring Security.
-- WARNING: change these immediately in production.

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'admin')
    INSERT INTO iam.users (username, email, password_hash, full_name_en, full_name_ar,
                           organisation_id, is_active, is_locked, is_deleted, created_at)
    VALUES ('admin', 'admin@tss-demo.sa',
            '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/ELa3WQ0Qu',  -- Admin@12345
            'Platform Administrator', 'مدير المنصة',
            1, 1, 0, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'riskmanager')
    INSERT INTO iam.users (username, email, password_hash, full_name_en, full_name_ar,
                           organisation_id, is_active, is_locked, is_deleted, created_at)
    VALUES ('riskmanager', 'risk@tss-demo.sa',
            '$2a$12$Ff5dKQqHhEQ1HtIVJRrJxukq1dQYqVnZBO3zOtJgtNEMKR9EZZ.qq',  -- Risk@12345
            'Risk Manager', 'مدير المخاطر',
            1, 1, 0, 0, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'auditor')
    INSERT INTO iam.users (username, email, password_hash, full_name_en, full_name_ar,
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
    WHERE u.username = 'admin' AND r.role_name = 'PLATFORM_ADMIN'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'admin' AND r.role_name = 'PLATFORM_ADMIN';

IF NOT EXISTS (
    SELECT 1 FROM iam.user_roles ur
    JOIN iam.users u ON u.id = ur.user_id
    JOIN iam.roles r ON r.id = ur.role_id
    WHERE u.username = 'riskmanager' AND r.role_name = 'RISK_MANAGER'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'riskmanager' AND r.role_name = 'RISK_MANAGER';

IF NOT EXISTS (
    SELECT 1 FROM iam.user_roles ur
    JOIN iam.users u ON u.id = ur.user_id
    JOIN iam.roles r ON r.id = ur.role_id
    WHERE u.username = 'auditor' AND r.role_name = 'AUDITOR'
)
    INSERT INTO iam.user_roles (user_id, role_id)
    SELECT u.id, r.id FROM iam.users u, iam.roles r
    WHERE u.username = 'auditor' AND r.role_name = 'AUDITOR';
GO

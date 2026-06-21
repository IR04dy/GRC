-- ============================================================================
-- V227 — iam.users performance + uniqueness indexes  (audit PERF-IX-003)
--
-- V1__core_schemas.sql creates iam.users with ONLY the clustered primary key
-- (pk_users on id). On a Flyway-provisioned database that means:
--   * username / email login lookups and per-request principal resolution scan
--     the whole table (no supporting index), and
--   * nothing enforces username/email uniqueness — duplicate accounts are
--     possible, which breaks the app's "resolve the user by username" login.
-- (The dev database only avoids this because its Python bootstrap separately
--  created UQ_username / UQ_email; the Flyway chain never did.)
--
-- This migration backfills the missing indexes. It is fully idempotent — each
-- index is guarded by IF NOT EXISTS on its name — so it is a safe no-op on any
-- database that already carries an equivalent index.
--
-- NOTE on scope: login authenticates by username with no tenant context, so the
-- uniqueness here is GLOBAL (username/email unique across the platform), NOT
-- per-organisation. The indexes are deliberately NON-filtered, matching the
-- dev bootstrap's UQ_username / UQ_email: this avoids the filtered-index
-- SET-option requirement (QUOTED_IDENTIFIER ON for every later DML on the
-- table) and keeps username/email globally unique even across soft-deleted
-- rows, consistent with how the app already behaves.
-- ============================================================================

-- Unique + covering index for the login path: a single seek by username returns
-- the auth columns (no key lookup to the clustered PK) AND enforces uniqueness.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'ix_users_username' AND object_id = OBJECT_ID('iam.users'))
    CREATE UNIQUE NONCLUSTERED INDEX ix_users_username
        ON iam.users (username)
        INCLUDE (organisation_id, password_hash, is_active, is_locked,
                 locked_until, failed_login_attempts, must_change_password);
GO

-- Unique email ("email already in use" / account recovery).
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'ix_users_email' AND object_id = OBJECT_ID('iam.users'))
    CREATE UNIQUE NONCLUSTERED INDEX ix_users_email
        ON iam.users (email);
GO

-- Org-scoped listing / user-admin screens filter by organisation_id; give them
-- a supporting index instead of a clustered scan.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'ix_users_org' AND object_id = OBJECT_ID('iam.users'))
    CREATE NONCLUSTERED INDEX ix_users_org
        ON iam.users (organisation_id);
GO

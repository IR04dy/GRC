-- V181 — Server-side refresh-token store + SDAIA breach-notification fields.
--
-- Bundles two Open-Issues-register schema changes that land together
-- in the batch-3 follow-up (Open Issues #002 + #006):
--
--   Issue #002 — Refresh token revocation
--     Adds platform.refresh_token: one row per issued refresh token,
--     with a jti (JWT ID claim, opaque random UUID), issuance/expiry
--     timestamps, optional revocation timestamp + reason, and an
--     audit-trail capture of the originating user-agent + IP. Lets
--     /api/auth/refresh do a fast jti lookup before the JWT signature
--     verify path, so a stolen refresh token can be revoked at the
--     server in < 1s rather than waiting 7 days for natural expiry.
--
--   Issue #006 — SDAIA (ex-PDPC) breach-notification workflow
--     Adds 3 columns to privacy.data_breaches so the manual SDAIA
--     submission workflow has somewhere to write its outcome:
--       - sdaia_notification_status (NOT_REPORTED | IN_PREPARATION |
--         SUBMITTED | ACKNOWLEDGED)
--       - sdaia_submission_ref (NVARCHAR — reference returned by the
--         SDAIA portal acknowledgement)
--       - sdaia_submitted_at (DATETIME2 — clock for the PDPL Art. 31
--         72-hour notification window discharge)
--
-- Both changes follow the existing schema conventions:
--   - QUOTED_IDENTIFIER ON
--   - Idempotent (IF NOT EXISTS / sys.columns guard)
--   - ROWVERSION on the new table
--   - Filtered indexes on the hot paths
--   - Standard created_at/updated_at/created_by/updated_by audit columns

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── platform.refresh_token (Issue #002) ─────────────────────────────

IF SCHEMA_ID('platform') IS NULL EXEC('CREATE SCHEMA platform');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'refresh_token' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.refresh_token (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        user_id                     BIGINT          NOT NULL,
        username                    NVARCHAR(120)   NULL,     -- denormalised for audit reporting

        -- The opaque JWT ID claim. Generated server-side (UUIDv4), embedded
        -- in the refresh token under the `jti` claim, looked up on every
        -- /api/auth/refresh call.
        jti                         VARCHAR(64)     NOT NULL,

        issued_at                   DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        expires_at                  DATETIME2       NOT NULL,

        -- Revocation. NULL = active. Any non-NULL value means the token
        -- is dead and assertValid(jti) must throw.
        revoked_at                  DATETIME2       NULL,
        revoked_by_user_id          BIGINT          NULL,
        revoke_reason               VARCHAR(100)    NULL,
        -- USER_LOGOUT | ROLLING_REFRESH | ADMIN_REVOKE_USER |
        -- SUSPECTED_COMPROMISE | PASSWORD_RESET | TENANT_SUSPENDED

        -- Audit-trail capture (the request that minted the token)
        user_agent                  NVARCHAR(500)   NULL,
        ip_address                  NVARCHAR(64)    NULL,

        -- Standard audit columns + rowversion. No soft-delete column —
        -- revocation is the "delete" semantic for this entity, so adding
        -- is_deleted would just create two parallel boolean flags.
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    -- jti is the verify-path lookup column — must be globally unique so a
    -- captured token can't be replayed against a different user_id.
    CREATE UNIQUE INDEX UX_refresh_token_jti
        ON platform.refresh_token(jti);

    -- "List my active sessions" — the /api/v1/auth/sessions endpoint reads
    -- WHERE user_id = ? AND revoked_at IS NULL ORDER BY issued_at DESC.
    CREATE INDEX IX_refresh_token_user_active
        ON platform.refresh_token(organisation_id, user_id, issued_at DESC)
        WHERE revoked_at IS NULL;

    -- "Revoke all for user" + admin reporting need the user_id scan.
    CREATE INDEX IX_refresh_token_org_user
        ON platform.refresh_token(organisation_id, user_id);

    -- Housekeeping: a future @Scheduled sweep prunes expired+revoked
    -- rows after a retention window (60d) — this index makes the sweep
    -- a seek not a scan.
    CREATE INDEX IX_refresh_token_expires
        ON platform.refresh_token(expires_at);
END
GO

-- ─── privacy.data_breaches SDAIA columns (Issue #006) ────────────────

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'data_breaches' AND schema_id = SCHEMA_ID('privacy'))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE Name = N'sdaia_notification_status'
                     AND Object_ID = OBJECT_ID(N'privacy.data_breaches'))
    BEGIN
        ALTER TABLE privacy.data_breaches
            ADD sdaia_notification_status   VARCHAR(40)     NOT NULL
                CONSTRAINT DF_data_breaches_sdaia_status DEFAULT 'NOT_REPORTED';
        -- NOT_REPORTED | IN_PREPARATION | SUBMITTED | ACKNOWLEDGED
    END

    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE Name = N'sdaia_submission_ref'
                     AND Object_ID = OBJECT_ID(N'privacy.data_breaches'))
    BEGIN
        ALTER TABLE privacy.data_breaches
            ADD sdaia_submission_ref        NVARCHAR(255)   NULL;
    END

    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE Name = N'sdaia_submitted_at'
                     AND Object_ID = OBJECT_ID(N'privacy.data_breaches'))
    BEGIN
        ALTER TABLE privacy.data_breaches
            ADD sdaia_submitted_at          DATETIME2       NULL;
    END

    -- Filtered index for the "what's still unreported past the 72h
    -- PDPL Art. 31 window?" CISO dashboard query.
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'IX_data_breaches_sdaia_pending'
                     AND object_id = OBJECT_ID('privacy.data_breaches'))
    BEGIN
        EXEC('CREATE INDEX IX_data_breaches_sdaia_pending
              ON privacy.data_breaches(organisation_id, discovered_at)
              WHERE sdaia_notification_status IN (''NOT_REPORTED'',''IN_PREPARATION'')
                AND is_deleted = 0');
    END
END
GO

PRINT 'V181: platform.refresh_token created + privacy.data_breaches SDAIA columns added.';
GO

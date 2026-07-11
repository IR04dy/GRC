-- V282 (RBAC Access-Governance P2 — plan §6.5): access recertification campaigns.
-- A campaign snapshots the org's role assignments (scope: PRIVILEGED / MODULE / ALL)
-- into review items; each item is dispositioned CERTIFY / REVOKE / MODIFY (REVOKE
-- auto-executes by hard-deleting the iam.user_roles row). Closure is blocked until
-- 100% dispositioned, or exception-approved by GRC_ADMINISTRATOR with a note.
-- Windows carry Hijri + Gregorian stamps (NCA ECC 2-2 / SAMA CSF periodic review).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'access_review_campaigns' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.access_review_campaigns (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT           NOT NULL,
        external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        title_en        NVARCHAR(300)    NOT NULL,
        title_ar        NVARCHAR(300)    NULL,
        scope           NVARCHAR(20)     NOT NULL,   -- PRIVILEGED | MODULE | ALL
        scope_module    NVARCHAR(40)     NULL,       -- required when scope = MODULE
        window_start    DATE             NOT NULL,
        window_end      DATE             NOT NULL,
        window_hijri    NVARCHAR(120)    NULL,       -- Hijri stamp of the review window
        status          NVARCHAR(15)     NOT NULL DEFAULT 'OPEN',   -- OPEN | CLOSED
        closed_at       DATETIME2        NULL,
        closed_by       BIGINT           NULL,
        exception_note  NVARCHAR(1000)   NULL,       -- set when closed with pending items
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE INDEX ix_arc_org_status ON iam.access_review_campaigns(organisation_id, status);
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'access_review_items' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.access_review_items (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT        NOT NULL,
        campaign_id       BIGINT        NOT NULL,     -- soft FK iam.access_review_campaigns
        user_id           BIGINT        NOT NULL,
        username          NVARCHAR(100) NOT NULL,     -- snapshot at generation time
        role_id           BIGINT        NOT NULL,
        role_name         NVARCHAR(100) NOT NULL,     -- snapshot at generation time
        assignment_source NVARCHAR(20)  NULL,         -- ADMIN | REQUEST | JML | BREAK_GLASS
        valid_to          DATETIME2     NULL,         -- snapshot of the validity window
        decision          NVARCHAR(15)  NOT NULL DEFAULT 'PENDING',
        -- PENDING | CERTIFY | REVOKE | MODIFY
        decided_by        BIGINT        NULL,
        decided_at        DATETIME2     NULL,
        note              NVARCHAR(500) NULL,
        is_deleted        BIT           NOT NULL DEFAULT 0,
        created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_ari_campaign ON iam.access_review_items(campaign_id, decision);
END

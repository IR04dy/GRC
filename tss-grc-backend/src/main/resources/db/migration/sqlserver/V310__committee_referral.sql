-- V310 (Track D, S0): route Change-CAB/ECAB and Policy approvals THROUGH the
-- committee engine. The committee becomes the system-of-record for *referred*
-- items via an ADDITIVE link — non-referred changes/policies keep the legacy
-- CAB/approval path byte-for-byte, so this migration changes NO behaviour on its
-- own. Idempotent; no destructive DDL; no seed (the gate map is tenant data).

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_referral')
BEGIN
    CREATE TABLE admin.committee_referral (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT        NOT NULL,
        source_module     NVARCHAR(30)  NOT NULL,   -- CHANGE | POLICY
        source_entity_id  BIGINT        NOT NULL,   -- change_request_id | policy_document id
        committee_id      BIGINT        NOT NULL,
        meeting_id        BIGINT        NULL,        -- set when a meeting hosts the motion
        resolution_id     BIGINT        NULL,        -- set when the motion is raised
        gate              NVARCHAR(10)  NOT NULL,    -- CAB | ECAB (from the CH-17 matrix)
        mode              NVARCHAR(20)  NOT NULL CONSTRAINT DF_cref_mode    DEFAULT ('APPROVAL'),   -- APPROVAL | RATIFICATION (emergency)
        status            NVARCHAR(20)  NOT NULL CONSTRAINT DF_cref_status  DEFAULT ('PENDING'),    -- PENDING | SCHEDULED | DECIDED | WITHDRAWN
        referred_by       NVARCHAR(150) NULL,
        is_deleted        BIT           NOT NULL CONSTRAINT DF_cref_del      DEFAULT (0),
        created_at        DATETIME2     NOT NULL CONSTRAINT DF_cref_created  DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_cref_module CHECK (source_module IN ('CHANGE','POLICY')),
        CONSTRAINT CK_cref_gate   CHECK (gate IN ('CAB','ECAB')),
        CONSTRAINT CK_cref_mode   CHECK (mode IN ('APPROVAL','RATIFICATION')),
        CONSTRAINT CK_cref_status CHECK (status IN ('PENDING','SCHEDULED','DECIDED','WITHDRAWN'))
    );
    -- one active referral per source item
    CREATE UNIQUE INDEX UX_cref_active_source
        ON admin.committee_referral (source_module, source_entity_id) WHERE is_deleted = 0;
    CREATE INDEX IX_cref_resolution
        ON admin.committee_referral (resolution_id) WHERE is_deleted = 0;
    CREATE INDEX IX_cref_org_status
        ON admin.committee_referral (organisation_id, status) WHERE is_deleted = 0;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_gate_map')
BEGIN
    -- Per-tenant map (user decision #1): which committee acts as the CAB / ECAB
    -- for that org. Referral creation resolves gate -> committee_id from here.
    CREATE TABLE admin.committee_gate_map (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT       NOT NULL,
        gate             NVARCHAR(10) NOT NULL,   -- CAB | ECAB
        committee_id     BIGINT       NOT NULL,
        is_deleted       BIT          NOT NULL CONSTRAINT DF_cgm_del     DEFAULT (0),
        created_at       DATETIME2    NOT NULL CONSTRAINT DF_cgm_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_cgm_gate CHECK (gate IN ('CAB','ECAB'))
    );
    CREATE UNIQUE INDEX UX_cgm_org_gate
        ON admin.committee_gate_map (organisation_id, gate) WHERE is_deleted = 0;
END
GO

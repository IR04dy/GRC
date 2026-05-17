-- V78 — NDI Audit Log (slice 14)
--
-- Append-only ledger of state changes across the NDI module. Audit
-- rows are written by the service layer of each slice (or by a manual
-- /audit/record endpoint for ad-hoc entries).
--
-- Field semantics:
--   entity_type   the NDI entity touched (CYCLE, MATURITY, COMPLIANCE,
--                 OPEX_KPI, DOMAIN, EVIDENCE, GAP, CAPA, PLAN,
--                 PLAN_ITEM, SUBMISSION_PACKAGE, REPORT, SETTING)
--   entity_id     PK of the row in that table
--   action        CREATE | UPDATE | DELETE | TRANSITION | APPROVE
--                 | SUBMIT | ACKNOWLEDGE | REJECT | EXPORT | IMPORT
--   from_state    optional — workflow status before
--   to_state      optional — workflow status after
--   diff_json     compact JSON of changed fields (null for CREATE)
--   actor_*       who triggered the change (JWT subject)
--   correlation_id reserved for tracing across slices (e.g. submit
--                 generates audit rows in plan / capa / submission)

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'audit_event' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.audit_event (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        cycle_id            BIGINT        NULL,

        entity_type         NVARCHAR(40)  NOT NULL,
        entity_id           BIGINT        NULL,
        entity_code         NVARCHAR(80)  NULL,

        action              NVARCHAR(30)  NOT NULL,
        from_state          NVARCHAR(40)  NULL,
        to_state            NVARCHAR(40)  NULL,
        severity            NVARCHAR(20)  NOT NULL DEFAULT N'INFO',  -- INFO | WARN | ERROR

        actor_username      NVARCHAR(120) NULL,
        actor_role          NVARCHAR(80)  NULL,
        actor_ip            NVARCHAR(50)  NULL,

        summary             NVARCHAR(500) NULL,
        diff_json           NVARCHAR(MAX) NULL,
        correlation_id      NVARCHAR(64)  NULL,

        occurred_at         DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT ck_ndi_audit_action CHECK (action IN
            (N'CREATE', N'UPDATE', N'DELETE', N'TRANSITION', N'APPROVE',
             N'SUBMIT', N'ACKNOWLEDGE', N'REJECT', N'EXPORT', N'IMPORT',
             N'REGENERATE', N'REOPEN', N'CHECK_EXPIRY', N'MARK_OFFICIAL',
             N'MARK_PLACEHOLDER')),
        CONSTRAINT ck_ndi_audit_sev CHECK (severity IN (N'INFO', N'WARN', N'ERROR'))
    );

    CREATE INDEX ix_ndi_audit_org_time    ON ndi.audit_event(organisation_id, occurred_at DESC);
    CREATE INDEX ix_ndi_audit_cycle       ON ndi.audit_event(organisation_id, cycle_id, occurred_at DESC);
    CREATE INDEX ix_ndi_audit_entity      ON ndi.audit_event(entity_type, entity_id);
    CREATE INDEX ix_ndi_audit_action      ON ndi.audit_event(organisation_id, action, occurred_at DESC);
    CREATE INDEX ix_ndi_audit_correlation ON ndi.audit_event(correlation_id);
END
GO

-- Demo seed: a handful of historical events so the Audit tab isn't empty.
IF NOT EXISTS (SELECT 1 FROM ndi.audit_event WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.audit_event (
        organisation_id, cycle_id, entity_type, entity_id, entity_code,
        action, from_state, to_state, severity, actor_username, summary
    ) VALUES
    (1, 1, N'CYCLE',              1, N'NDI-2025-Q1', N'CREATE',     NULL,        N'DRAFT',                  N'INFO',  N'admin',       N'Created NDI Readiness Cycle 2025'),
    (1, 1, N'CYCLE',              1, N'NDI-2025-Q1', N'TRANSITION', N'PLANNING', N'ASSESSMENT_IN_PROGRESS', N'INFO',  N'admin',       N'Advanced cycle to ASSESSMENT_IN_PROGRESS'),
    (1, 1, N'EVIDENCE',           1, N'EV-001',      N'CREATE',     NULL,        N'ACTIVE',                 N'INFO',  N'admin',       N'Added Data Governance Charter as evidence'),
    (1, 1, N'GAP',                3, N'GAP-003',     N'CREATE',     NULL,        N'OPEN',                   N'WARN',  N'admin',       N'Opened CRITICAL gap: ROPA register coverage'),
    (1, 1, N'CAPA',               5, N'CAPA-GAP-003-1', N'CREATE',  NULL,        N'NOT_STARTED',            N'INFO',  N'admin',       N'Added corrective action for GAP-003'),
    (1, 1, N'SUBMISSION_PACKAGE', 1, N'PKG-2025-v1', N'CREATE',     NULL,        N'DRAFT',                  N'INFO',  N'admin',       N'Created submission package v1'),
    (1, 1, N'SUBMISSION_PACKAGE', 1, N'PKG-2025-v1', N'TRANSITION', N'DRAFT',    N'READY',                  N'INFO',  N'admin',       N'Finalized submission package'),
    (1, 1, N'SUBMISSION_PACKAGE', 1, N'PKG-2025-v1', N'APPROVE',    N'READY',    N'READY',                  N'INFO',  N'director',    N'Approved submission package'),
    (1, 1, N'SUBMISSION_PACKAGE', 1, N'PKG-2025-v1', N'SUBMIT',     N'READY',    N'SUBMITTED',              N'INFO',  N'cdo',         N'Submitted package to SDAIA with attestation'),
    (1, 1, N'SUBMISSION_PACKAGE', 1, N'PKG-2025-v1', N'ACKNOWLEDGE',N'SUBMITTED',N'ACKNOWLEDGED',           N'INFO',  N'system',      N'SDAIA acknowledged: SDAIA-NDI-2025-00427');
END
GO

-- V22 — Approvals module — Sprint 4 §3.2 #1.
--
-- Universal approval-request table. Any module can create approval requests
-- against any entity ("approve risk acceptance", "approve treatment plan",
-- "approve change request"). State machine:
--   PENDING → APPROVED | REJECTED | DELEGATED | EXPIRED
--   DELEGATED → PENDING (now assigned to delegate)
--   APPROVED/REJECTED/EXPIRED are terminal.

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'approvals')
    EXEC('CREATE SCHEMA approvals');
GO

CREATE TABLE approvals.requests (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(64)  NOT NULL,                 -- APR-2026-001
    organisation_id       BIGINT        NOT NULL,
    entity_type           NVARCHAR(60)  NOT NULL,                 -- e.g. 'risk.treatment', 'change.request'
    entity_id             NVARCHAR(120) NOT NULL,                 -- the foreign external id
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500) NOT NULL,
    summary_en            NVARCHAR(MAX),
    summary_ar            NVARCHAR(MAX),
    requested_by          NVARCHAR(255) NOT NULL,
    requested_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    -- Current assignee (changes on DELEGATED)
    assigned_to           NVARCHAR(255) NOT NULL,
    -- Original requested approver (kept even after delegation)
    original_assigned_to  NVARCHAR(255) NOT NULL,
    priority              NVARCHAR(10)  NOT NULL DEFAULT 'P3',    -- P1/P2/P3/P4
    sla_deadline          DATETIME2,                              -- optional hard deadline
    status                NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    decided_at            DATETIME2,
    decided_by            NVARCHAR(255),
    decision_comment_en   NVARCHAR(MAX),
    decision_comment_ar   NVARCHAR(MAX),
    delegated_from        NVARCHAR(255),                          -- previous assignee on DELEGATED
    delegation_reason_en  NVARCHAR(MAX),
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_apr_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_apr_org_status   ON approvals.requests (organisation_id, status, is_deleted);
CREATE INDEX ix_apr_assignee     ON approvals.requests (organisation_id, assigned_to, status, is_deleted);
CREATE INDEX ix_apr_entity       ON approvals.requests (organisation_id, entity_type, entity_id, is_deleted);
GO

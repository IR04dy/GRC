-- V125: generic workflow_submissions ledger.
--
-- The Saudi-GRC §7 catalog ships 24 wizards. Twelve of them (Risk Treatment,
-- Risk Acceptance, Incident RCA, Management Review, Digital Signature,
-- Control Test, Cloud CCC, NCA ECC Self-Assessment, Consent Management,
-- Cross-Border Transfer, Supplier Exit Plan, Board Pack) don't yet have a
-- dedicated schema — each would need its own table + entity + service.
--
-- Rather than block on 12 separate migrations, we ship a single generic
-- table that stores every wizard submission as JSON. The wizards write
-- the same draft they already build; the table captures the workflow_id +
-- bilingual title + summary so the saved row is searchable and
-- regulator-visible without losing any field.
--
-- Dedicated per-wizard tables can replace this for high-volume wizards
-- later (e.g. when a vendor wants typed Control-Test reporting). For
-- prospect demos + buyer assessments, this is the right shape.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('workflow') IS NULL EXEC('CREATE SCHEMA workflow AUTHORIZATION dbo;');
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.tables
     WHERE name = 'workflow_submissions'
       AND schema_id = SCHEMA_ID('workflow')
)
BEGIN
    CREATE TABLE workflow.workflow_submissions (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Catalog identifier (workflowCatalog.ts id):
        --   'erm-risk-treatment', 'governance-board-pack', etc.
        workflow_id         NVARCHAR(80)    NOT NULL,

        -- Module bucket: ERM | BCM | SCM | NCA | PDPL | COMPLIANCE |
        --                EVIDENCE | INCIDENT | GOVERNANCE
        workflow_module     NVARCHAR(40)    NOT NULL,

        title_en            NVARCHAR(300)   NOT NULL,
        title_ar            NVARCHAR(300)   NULL,

        -- DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | CLOSED
        status              NVARCHAR(40)    NOT NULL DEFAULT 'SUBMITTED',

        -- One-line summary the wizard computes for the register view.
        summary_en          NVARCHAR(1000)  NULL,
        summary_ar          NVARCHAR(1000)  NULL,

        -- Optional anchor (regulatory citation): 'PDPL Art. 23', 'ISO 22301 §9.3'.
        regulatory_anchor   NVARCHAR(60)    NULL,

        -- Full wizard draft as JSON. NVARCHAR(MAX) on SQL Server holds 2 GB.
        payload_json        NVARCHAR(MAX)   NOT NULL,

        submitted_by        BIGINT          NULL,
        submitted_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        reviewed_by         BIGINT          NULL,
        reviewed_at         DATETIME2       NULL,
        reviewer_note       NVARCHAR(1000)  NULL,

        is_deleted          BIT             NOT NULL DEFAULT 0,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT          NULL,
        updated_at          DATETIME2       NULL,
        updated_by          BIGINT          NULL
    );

    CREATE INDEX IX_workflow_submissions_org_workflow
        ON workflow.workflow_submissions(organisation_id, workflow_id)
     WHERE is_deleted = 0;

    CREATE INDEX IX_workflow_submissions_org_module
        ON workflow.workflow_submissions(organisation_id, workflow_module)
     WHERE is_deleted = 0;

    CREATE INDEX IX_workflow_submissions_org_status
        ON workflow.workflow_submissions(organisation_id, status)
     WHERE is_deleted = 0;
END
GO

PRINT 'V125 workflow.workflow_submissions ledger created.';
GO

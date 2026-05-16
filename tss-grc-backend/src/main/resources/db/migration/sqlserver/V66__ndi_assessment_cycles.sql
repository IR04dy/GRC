-- V66 — SDAIA National Data Index (NDI / نضيء) — slice 1 — Assessment Cycles
--
-- Foundation entity for the SDAIA-NDI module. One row per NDI readiness
-- cycle (typically annual). Every other NDI entity in slices 2-14 hangs
-- off a cycle_id.
--
-- Schema: `ndi` (new). Mirrors the `sust` schema layout convention.
--
-- Status machine (13 states from the spec, in transition order):
--   DRAFT
--      → PLANNING
--      → DATA_COLLECTION
--      → ASSESSMENT_IN_PROGRESS
--      → EVIDENCE_REVIEW
--      → GAP_REMEDIATION
--      → READY_FOR_DATA_OFFICE_REVIEW
--      → UNDER_DATA_OFFICE_REVIEW
--      → READY_FOR_EXECUTIVE_APPROVAL
--      → APPROVED
--      → SUBMITTED
--      → CLOSED
--      → ARCHIVED
--
-- Score columns (maturity/compliance/op-ex/overall, evidence_completion_pct,
-- gap_count, critical_gap_count) are denormalised summaries — services
-- recompute them after each transition. The dashboard reads these
-- directly rather than re-aggregating across child tables on every page
-- load.
--
-- Placeholder/official-import flag: not stored at cycle level. Each
-- framework_item (slice 2) carries its own is_placeholder / is_official_import
-- flags so a cycle can mix official + placeholder content if SDAIA
-- updates partway through a reporting year.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- Create the `ndi` schema once.
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'ndi')
    EXEC('CREATE SCHEMA ndi AUTHORIZATION dbo');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'assessment_cycle' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.assessment_cycle (
        id                            BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id               BIGINT        NOT NULL,
        external_id                   NVARCHAR(64)  NULL,
        cycle_code                    NVARCHAR(40)  NULL,

        cycle_name_en                 NVARCHAR(255) NOT NULL,
        cycle_name_ar                 NVARCHAR(255) NULL,

        reporting_year                SMALLINT      NOT NULL,
        period_start                  DATE          NULL,
        period_end                    DATE          NULL,

        scope                         NVARCHAR(MAX) NULL,
        entity_name_en                NVARCHAR(255) NULL,
        entity_name_ar                NVARCHAR(255) NULL,

        data_office_owner_name        NVARCHAR(255) NULL,
        data_office_owner_email       NVARCHAR(255) NULL,
        assessment_manager_name       NVARCHAR(255) NULL,
        assessment_manager_email      NVARCHAR(255) NULL,

        -- See the 13-state machine in the header comment above.
        status                        NVARCHAR(40)  NOT NULL DEFAULT N'DRAFT',

        -- Denormalised summary scores (0..100). NULL until first compute.
        maturity_score                DECIMAL(5,2)  NULL,
        compliance_score              DECIMAL(5,2)  NULL,
        operational_excellence_score  DECIMAL(5,2)  NULL,
        overall_score                 DECIMAL(5,2)  NULL,

        evidence_completion_percent   DECIMAL(5,2)  NULL,
        gap_count                     INT           NULL,
        critical_gap_count            INT           NULL,

        -- ON_TRACK | AT_RISK | BLOCKED — surfaced on the dashboard
        -- "Submission Readiness Gauge" widget.
        submission_readiness_status   NVARCHAR(20)  NULL,

        approved_by                   NVARCHAR(255) NULL,
        approved_at                   DATETIME2     NULL,
        submitted_at                  DATETIME2     NULL,
        closed_at                     DATETIME2     NULL,

        notes                         NVARCHAR(MAX) NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        created_at                    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    NVARCHAR(100) NULL,
        updated_at                    DATETIME2     NULL,
        updated_by                    NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_cycle_status CHECK (status IN (
            N'DRAFT', N'PLANNING', N'DATA_COLLECTION',
            N'ASSESSMENT_IN_PROGRESS', N'EVIDENCE_REVIEW', N'GAP_REMEDIATION',
            N'READY_FOR_DATA_OFFICE_REVIEW', N'UNDER_DATA_OFFICE_REVIEW',
            N'READY_FOR_EXECUTIVE_APPROVAL', N'APPROVED', N'SUBMITTED',
            N'CLOSED', N'ARCHIVED')),
        CONSTRAINT ck_ndi_cycle_readiness CHECK (submission_readiness_status IS NULL
            OR submission_readiness_status IN (N'ON_TRACK', N'AT_RISK', N'BLOCKED')),
        CONSTRAINT ck_ndi_cycle_year CHECK (reporting_year BETWEEN 2020 AND 2100),
        CONSTRAINT ck_ndi_cycle_scores CHECK (
            (maturity_score IS NULL OR maturity_score BETWEEN 0 AND 100)
            AND (compliance_score IS NULL OR compliance_score BETWEEN 0 AND 100)
            AND (operational_excellence_score IS NULL OR operational_excellence_score BETWEEN 0 AND 100)
            AND (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
        )
    );

    -- Soft-delete-aware index on (org, year). The "one active per year"
    -- rule is enforced in the service layer because SQL Server's
    -- filtered-index predicates don't support NOT IN — a CLOSED/ARCHIVED
    -- cycle leaving the (org, year) free for a follow-up is a service
    -- responsibility, not a DB constraint.
    CREATE INDEX ix_ndi_cycle_org_year_active ON ndi.assessment_cycle(organisation_id, reporting_year)
        WHERE is_deleted = 0;

    CREATE INDEX ix_ndi_cycle_org_status ON ndi.assessment_cycle(organisation_id, status);
    CREATE INDEX ix_ndi_cycle_org_year   ON ndi.assessment_cycle(organisation_id, reporting_year);
END
GO

-- ─── Seed — one 2025 demo cycle in ASSESSMENT_IN_PROGRESS ────────────
IF NOT EXISTS (SELECT 1 FROM ndi.assessment_cycle WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.assessment_cycle (
        organisation_id, cycle_code, cycle_name_en, cycle_name_ar,
        reporting_year, period_start, period_end,
        scope, entity_name_en, entity_name_ar,
        data_office_owner_name, data_office_owner_email,
        assessment_manager_name, assessment_manager_email,
        status, notes
    )
    VALUES (
        1, N'NDI-2025-Q1',
        N'NDI Readiness Cycle 2025',
        N'دورة جاهزية نضيء 2025',
        2025, '2025-01-01', '2025-12-31',
        N'Enterprise-wide — all 14 data governance domains',
        N'TSS GRC Platform Demo Entity',
        N'جهة تجريبية لمنصة TSS GRC',
        N'Sara Al-Zahrani (CSO / Data Office Manager)', N's.alzahrani@tss.sa',
        N'Khalid Al-Otaibi (NDI Assessment Manager)',  N'k.alotaibi@tss.sa',
        N'ASSESSMENT_IN_PROGRESS',
        N'Q1 2026 internal-readiness pass before SDAIA submission window. Framework items currently flagged is_placeholder=1 pending official NDI control-library import.'
    );
END
GO

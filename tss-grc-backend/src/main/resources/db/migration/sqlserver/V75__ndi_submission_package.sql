-- V75 — NDI Submission Package (slice 10)
--
-- The Submission Package is the immutable snapshot that the entity
-- sends to SDAIA at the close of each cycle. It freezes the overall
-- NDI score, every artefact count, and a JSON snapshot of the
-- maturity / compliance / opex / domains / evidence / gaps / plan
-- state at the moment of submission.
--
-- Versioning: each cycle can have multiple packages (resubmissions
-- after RETURNED, demo dry-runs, etc.). The version column increments
-- per cycle, starting at 1.
--
-- Status machine:
--   DRAFT          editable; service regenerates snapshot on demand
--   READY          pre-flight checks passed; snapshot frozen
--   SUBMITTED      pushed to SDAIA; awaiting acknowledgement
--   ACKNOWLEDGED   SDAIA returned a reference; cycle is sealed
--   REJECTED       SDAIA returned issues; package returnable to DRAFT
--   ARCHIVED       superseded by a newer version
--
-- Rule 15 (pre-flight): finalize() refuses to leave DRAFT unless every
--                       CRITICAL gap has at least one CAPA, every
--                       compliance row is APPROVED or NA, and the
--                       overall NDI score is ≥ 0 (i.e., some data exists).
-- Rule 16 (immutability): once SUBMITTED, snapshot_json and
--                       manifest_json are read-only; the only legal
--                       writes are status transitions and SDAIA fields.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'submission_package' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.submission_package (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT        NOT NULL,
        external_id             NVARCHAR(64)  NULL,

        cycle_id                BIGINT        NOT NULL,
        package_code            NVARCHAR(40)  NULL,
        version                 INT           NOT NULL DEFAULT 1,

        -- Frozen snapshot (assembled by the service from all NDI slices).
        snapshot_json           NVARCHAR(MAX) NULL,
        manifest_json           NVARCHAR(MAX) NULL,
        manifest_checksum       NVARCHAR(128) NULL,

        -- Frozen rollups (also live in snapshot_json — duplicated for fast queries).
        overall_ndi_pct         DECIMAL(5,2)  NULL,
        maturity_score_pct      DECIMAL(5,2)  NULL,
        compliance_pct          DECIMAL(5,2)  NULL,
        opex_score_pct          DECIMAL(5,2)  NULL,

        total_evidence          INT           NOT NULL DEFAULT 0,
        total_gaps              INT           NOT NULL DEFAULT 0,
        critical_gaps           INT           NOT NULL DEFAULT 0,
        total_actions           INT           NOT NULL DEFAULT 0,
        completed_actions       INT           NOT NULL DEFAULT 0,

        -- Workflow.
        status                  NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        preparer_name           NVARCHAR(255) NULL,
        preparer_email          NVARCHAR(255) NULL,
        reviewer_name           NVARCHAR(255) NULL,
        approver_name           NVARCHAR(255) NULL,
        submitter_name          NVARCHAR(255) NULL,

        prepared_at             DATETIME2     NULL,
        reviewed_at             DATETIME2     NULL,
        approved_at             DATETIME2     NULL,
        submitted_at            DATETIME2     NULL,
        acknowledged_at         DATETIME2     NULL,

        sdaia_reference         NVARCHAR(100) NULL,
        attestation_en          NVARCHAR(MAX) NULL,
        attestation_ar          NVARCHAR(MAX) NULL,
        rejection_reason        NVARCHAR(MAX) NULL,

        is_placeholder          BIT           NOT NULL DEFAULT 1,    -- True until official content imported.
        notes                   NVARCHAR(MAX) NULL,

        is_deleted              BIT           NOT NULL DEFAULT 0,
        created_at              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              NVARCHAR(100) NULL,
        updated_at              DATETIME2     NULL,
        updated_by              NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_sub_status CHECK (status IN
            (N'DRAFT', N'READY', N'SUBMITTED', N'ACKNOWLEDGED', N'REJECTED', N'ARCHIVED'))
    );

    CREATE INDEX ix_ndi_sub_org_cycle ON ndi.submission_package(organisation_id, cycle_id);
    CREATE UNIQUE INDEX ux_ndi_sub_cycle_version
        ON ndi.submission_package(organisation_id, cycle_id, version) WHERE is_deleted = 0;
END
GO

-- Pre-flight check view — used by the service to gate DRAFT→READY.
-- Returns one row per cycle with the counts that drive Rule 15.

IF EXISTS (SELECT 1 FROM sys.views WHERE name = N'submission_preflight_v')
BEGIN
    DROP VIEW ndi.submission_preflight_v;
END
GO

CREATE VIEW ndi.submission_preflight_v AS
SELECT
    c.id AS cycle_id,
    c.organisation_id,
    (SELECT COUNT(*) FROM ndi.gap        WHERE cycle_id = c.id AND severity = N'CRITICAL'
        AND is_deleted = 0 AND status NOT IN (N'RESOLVED', N'CLOSED', N'ACCEPTED_RISK'))         AS open_critical_gaps,
    (SELECT COUNT(*) FROM ndi.gap        WHERE cycle_id = c.id AND severity = N'CRITICAL'
        AND is_deleted = 0 AND capa_count = 0)                                                   AS critical_gaps_without_capa,
    (SELECT COUNT(*) FROM ndi.compliance_assessment WHERE cycle_id = c.id
        AND is_deleted = 0 AND lifecycle_status <> N'APPROVED'
        AND compliance_status <> N'NOT_APPLICABLE')                                              AS compliance_not_approved,
    (SELECT COUNT(*) FROM ndi.maturity_assessment   WHERE cycle_id = c.id
        AND is_deleted = 0 AND status <> N'APPROVED'
        AND current_level IS NOT NULL)                                                           AS maturity_not_approved,
    (SELECT COUNT(*) FROM ndi.evidence_item         WHERE cycle_id = c.id
        AND is_deleted = 0 AND status = N'EXPIRED')                                              AS expired_evidence
FROM ndi.assessment_cycle c
WHERE c.is_deleted = 0;
GO

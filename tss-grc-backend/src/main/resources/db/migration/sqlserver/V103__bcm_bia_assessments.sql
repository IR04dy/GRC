-- V103: rich BIA Assessment entity for the BIA wizard (audit Tier-1 #3).
--
-- The legacy bcm.bia_records (V98) is a thin 7-column row tied to a process
-- with only RTO/RPO/MTPD/overallImpact. Spec §E asks for:
--   - time-band impact matrix (0-2h, 2-4h, 4-8h, 8-24h, 1-3d, 3-7d, >7d)
--   - 10 impact dimensions (regulatory, financial, operational, reputation,
--     legal_contractual, public_service, national_sovereign, data, cyber,
--     supplier) scored per time band
--   - MBCO (Minimum Business Continuity Objective) narrative
--   - minimum resources (staff, tech, facility, supplier)
--   - manual workaround flag + description
--   - peak periods narrative
--   - overall_criticality (LOW..SOVEREIGN, computed from matrix)
--   - approval workflow + generated BCP/DRP linkage
--
-- This migration adds a NEW table (bcm.bia_assessments) rather than mutating
-- the legacy one. Legacy rows stay untouched. Future sprint will migrate
-- legacy rows into this table and deprecate the old.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bia_assessments' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bia_assessments (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        bia_code                        NVARCHAR(50)    NOT NULL,    -- BIA-YYYY-NNNNNN

        -- Scope: at least one of service_id or process_id required
        cycle_id                        BIGINT          NULL,
        service_id                      BIGINT          NULL,
        process_id                      BIGINT          NULL,
        department                      NVARCHAR(255)   NULL,
        bia_owner_id                    BIGINT          NULL,
        service_owner_id                BIGINT          NULL,
        process_owner_id                BIGINT          NULL,
        description                     NVARCHAR(MAX)   NULL,

        -- Recovery targets (hours)
        mtpd_hours                      INT             NULL,
        rto_hours                       INT             NULL,
        rpo_hours                       INT             NULL,
        mbco_description                NVARCHAR(MAX)   NULL,

        -- Minimum resources
        min_staff                       INT             NULL,
        min_tech_text                   NVARCHAR(1000)  NULL,
        min_facility_text               NVARCHAR(1000)  NULL,
        min_supplier_text               NVARCHAR(1000)  NULL,

        -- Workaround
        manual_workaround_available     BIT             NOT NULL DEFAULT 0,
        manual_workaround_description   NVARCHAR(MAX)   NULL,
        peak_periods_text               NVARCHAR(1000)  NULL,

        -- The two-dimensional impact matrix:
        --   impact_time_bands_json shape:
        --     {"0-2h":{"regulatory":1,"financial":2,…},"2-4h":{…},…}
        --   impact_dimensions_json is the per-dimension MAX across time bands
        --   (denormalised at calculate-criticality time so dashboards can sum
        --    without re-parsing the matrix).
        impact_time_bands_json          NVARCHAR(MAX)   NULL,
        impact_dimensions_json          NVARCHAR(MAX)   NULL,

        -- Overall criticality — derived from impact_dimensions_json by
        -- calculateCriticality(). LOW (1-2) | MEDIUM (3) | HIGH (4) | CRITICAL (5)
        -- | SOVEREIGN (national_sovereign dim ≥ 4 forces this).
        overall_criticality             NVARCHAR(20)    NULL,

        evidence_id                     BIGINT          NULL,
        reviewer_id                     BIGINT          NULL,

        -- Lifecycle status
        approval_status                 NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | UNDER_REVIEW | CHANGES_REQUIRED | APPROVED | ACTIVE | ARCHIVED

        approved_by                     NVARCHAR(255)   NULL,
        approved_at                     DATETIME2       NULL,

        -- Generation linkage: when "Generate BCP/DRP from BIA" is clicked,
        -- the resulting row IDs are stored here so re-generation can prompt.
        generated_bcp_id                BIGINT          NULL,
        generated_drp_id                BIGINT          NULL,

        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION
    );
    CREATE UNIQUE INDEX UX_bia_assessments_org_code   ON bcm.bia_assessments(organisation_id, bia_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_assessments_org_status ON bcm.bia_assessments(organisation_id, approval_status) WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_assessments_org_service ON bcm.bia_assessments(organisation_id, service_id) WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_assessments_org_process ON bcm.bia_assessments(organisation_id, process_id) WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_assessments_org_cycle   ON bcm.bia_assessments(organisation_id, cycle_id)   WHERE is_deleted = 0;

    -- RTO ≤ MTPD constraint at the DB level (defensive belt + service-layer suspenders).
    -- Allows both to be NULL during the wizard's early phases.
    ALTER TABLE bcm.bia_assessments
        ADD CONSTRAINT CK_bia_rto_le_mtpd
        CHECK (rto_hours IS NULL OR mtpd_hours IS NULL OR rto_hours <= mtpd_hours);
END
GO

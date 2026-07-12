-- V293 (Green-Finance completion — plan §0 remaining items 2–9): the scoped backend
-- gaps left after G0–G4.
--  a) esg_metric.reading_required — metrics flagged here (ENERGY/WATER/WASTE ops
--     categories, plan §5.6/REG-6) block annual-report approval while they have no
--     reading (assurance lineage, ISAE 3410 direction).
--  b) financed_emissions_record.asset_ref — soft FK to sust.portfolio_assets so the
--     PCAF records roll up per portfolio asset (plan §5.1).
--  c) green_projects.actual_impact_basis — ACTUAL | ESTIMATE flag (plan §4.2: impact
--     counts actuals unless explicitly flagged ESTIMATE; summaries surface the flag).
--  d) sust.esg_target_milestones — interim milestones; trajectory becomes piecewise
--     linear through them (plan §5.5).
--  e) sust.env_permit_violations — violation register per NCEC permit; every
--     violation raises its own auto-CAPA (plan §5.6; reuses ck_ca_source ENV_PERMIT).

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.esg_metric') AND name = 'reading_required')
    ALTER TABLE sust.esg_metric ADD reading_required BIT NOT NULL DEFAULT 0;
GO
-- The operational REG-6 metrics live under category=ENVIRONMENTAL with
-- subcategory ENERGY/WATER/WASTE (verified against the live catalogue).
UPDATE sust.esg_metric SET reading_required = 1
WHERE subcategory IN ('ENERGY', 'WATER', 'WASTE') AND is_deleted = 0;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.financed_emissions_record') AND name = 'asset_ref')
    ALTER TABLE sust.financed_emissions_record ADD asset_ref BIGINT NULL;   -- soft FK sust.portfolio_assets

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.green_projects') AND name = 'actual_impact_basis')
    ALTER TABLE sust.green_projects ADD actual_impact_basis NVARCHAR(10) NOT NULL DEFAULT 'ACTUAL';

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'esg_target_milestones' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.esg_target_milestones (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        target_id       BIGINT        NOT NULL,     -- soft FK sust.esg_targets
        [year]          INT           NOT NULL,     -- strictly between baseline and target year
        value           DECIMAL(18,4) NOT NULL,
        label_en        NVARCHAR(200) NULL,
        label_ar        NVARCHAR(200) NULL,
        is_deleted      BIT           NOT NULL DEFAULT 0,
        created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE UNIQUE INDEX ux_target_milestone_year ON sust.esg_target_milestones(target_id, [year]) WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'env_permit_violations' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.env_permit_violations (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        permit_id       BIGINT        NOT NULL,     -- soft FK sust.env_permits
        violation_date  DATE          NOT NULL,
        description_en  NVARCHAR(500) NOT NULL,
        description_ar  NVARCHAR(500) NULL,
        severity        NVARCHAR(10)  NOT NULL DEFAULT 'HIGH',   -- CRITICAL | HIGH | MEDIUM
        status          NVARCHAR(15)  NOT NULL DEFAULT 'OPEN',   -- OPEN | RESOLVED
        linked_capa_id  BIGINT        NULL,
        is_deleted      BIT           NOT NULL DEFAULT 0,
        created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_epv_permit ON sust.env_permit_violations(permit_id, status);
END

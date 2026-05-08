-- V18 — BCM Lifecycle module backend tables.
-- Mirrors lib/bcmLifecycle.ts. Aligned with NCEMA (National Center for Emergency Management),
-- ISO 22301 BCMS, and SAMA Cyber Resilience for financial-sector members.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'bcm_lc')
    EXEC('CREATE SCHEMA bcm_lc');
GO

-- ─── BIA — Business Impact Analysis ────────────────────────────────────────
CREATE TABLE bcm_lc.bia_records (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,                      -- BIA-2026-001
    organisation_id     BIGINT        NOT NULL,
    process_name_en     NVARCHAR(255) NOT NULL,
    process_name_ar     NVARCHAR(255) NOT NULL,
    business_function   NVARCHAR(100) NOT NULL,                      -- FINANCE/OPERATIONS/IT/HR/CUSTOMER_SERVICE/...
    process_owner       NVARCHAR(255),
    criticality         NVARCHAR(20)  NOT NULL,                      -- TIER1/TIER2/TIER3/TIER4
    rto_hours           INT           NOT NULL,                      -- Recovery Time Objective
    rpo_hours           INT           NOT NULL,                      -- Recovery Point Objective
    mtpd_hours          INT           NOT NULL,                      -- Max Tolerable Period of Disruption
    impact_financial    NVARCHAR(MAX),                                -- JSON {hour1, hour4, hour24, hour72}
    impact_regulatory   NVARCHAR(MAX),
    impact_reputation   NVARCHAR(MAX),
    impact_operations   NVARCHAR(MAX),
    upstream_deps_en    NVARCHAR(MAX),
    downstream_deps_en  NVARCHAR(MAX),
    peak_periods_en     NVARCHAR(MAX),
    peak_periods_ar     NVARCHAR(MAX),
    status              NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',      -- DRAFT/IN_REVIEW/APPROVED/SUPERSEDED
    approved_by         NVARCHAR(255),
    approved_at         DATETIME2,
    review_due_at       DATE,
    created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_bia_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_bia_status ON bcm_lc.bia_records (organisation_id, status, is_deleted);
GO

-- ─── Recovery strategy ─────────────────────────────────────────────────────
CREATE TABLE bcm_lc.recovery_strategies (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,                      -- RS-2026-001
    organisation_id     BIGINT        NOT NULL,
    bia_id              NVARCHAR(64)  NOT NULL,
    strategy_type       NVARCHAR(30)  NOT NULL,                      -- ACTIVE_ACTIVE/HOT_STANDBY/WARM_STANDBY/COLD_STANDBY/MANUAL_WORKAROUND
    description_en      NVARCHAR(MAX) NOT NULL,
    description_ar      NVARCHAR(MAX) NOT NULL,
    estimated_rto_hours INT           NOT NULL,
    estimated_rpo_hours INT           NOT NULL,
    annual_cost_sar     DECIMAL(18,2),
    site_location       NVARCHAR(255),                                -- e.g. RIY-DC2 / DXB-DR
    runbook_url         NVARCHAR(500),
    last_tested_at      DATETIME2,
    test_outcome        NVARCHAR(30),                                 -- PASSED/PARTIAL/FAILED
    status              NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_rs_external UNIQUE (organisation_id, external_id)
);
GO

-- ─── Drill / Exercise ──────────────────────────────────────────────────────
CREATE TABLE bcm_lc.drills (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,                      -- DRILL-2026-001
    organisation_id     BIGINT        NOT NULL,
    title_en            NVARCHAR(500) NOT NULL,
    title_ar            NVARCHAR(500) NOT NULL,
    drill_type          NVARCHAR(30)  NOT NULL,                      -- TABLETOP/WALKTHROUGH/FUNCTIONAL/FULL_SIMULATION
    scenario_en         NVARCHAR(MAX) NOT NULL,
    scenario_ar         NVARCHAR(MAX) NOT NULL,
    scheduled_at        DATETIME2     NOT NULL,
    executed_at         DATETIME2,
    duration_minutes    INT,
    participants        NVARCHAR(MAX),                                -- JSON array of user ids
    bia_ids             NVARCHAR(MAX),                                -- comma-sep
    rto_target_hours    INT,
    rto_actual_hours    DECIMAL(10,2),
    rpo_actual_hours    DECIMAL(10,2),
    outcome             NVARCHAR(20),                                 -- PASSED/PARTIAL/FAILED
    findings_en         NVARCHAR(MAX),
    findings_ar         NVARCHAR(MAX),
    capa_count          INT           NOT NULL DEFAULT 0,
    status              NVARCHAR(20)  NOT NULL DEFAULT 'PLANNED',    -- PLANNED/IN_PROGRESS/COMPLETED/CANCELLED
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_drill_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_drill_scheduled ON bcm_lc.drills (organisation_id, scheduled_at, is_deleted);
GO

-- ─── Invocation — PRIMARY state-machine entity (real disruption response) ─
CREATE TABLE bcm_lc.invocations (
    id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id                 NVARCHAR(64)  NOT NULL,              -- INV-2026-007
    organisation_id             BIGINT        NOT NULL,
    title_en                    NVARCHAR(500) NOT NULL,
    title_ar                    NVARCHAR(500) NOT NULL,
    severity                    NVARCHAR(10)  NOT NULL,              -- SEV1/SEV2/SEV3/SEV4
    trigger_event_en            NVARCHAR(MAX) NOT NULL,
    trigger_event_ar            NVARCHAR(MAX) NOT NULL,
    affected_bia_ids            NVARCHAR(MAX) NOT NULL,              -- comma-sep BIA externalids
    affected_sites              NVARCHAR(MAX),                        -- comma-sep, e.g. RIY-HQ,JED-DC
    incident_commander          NVARCHAR(255),
    bcm_coordinator             NVARCHAR(255),

    -- ─── State machine (NCEMA-aligned) ───────────────────────────────────
    -- STANDBY              → DECLARED, EXERCISE
    -- DECLARED             → INVOKED, STAND_DOWN
    -- INVOKED              → STABILISED, STAND_DOWN
    -- STABILISED           → STAND_DOWN
    -- STAND_DOWN           → POST_INCIDENT_REVIEW
    -- POST_INCIDENT_REVIEW → CLOSED
    status                      NVARCHAR(30)  NOT NULL DEFAULT 'STANDBY',

    detected_at                 DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    declared_at                 DATETIME2,
    declared_by                 NVARCHAR(255),
    invoked_at                  DATETIME2,
    invoked_strategies          NVARCHAR(MAX),                        -- comma-sep RS externalids
    stabilised_at               DATETIME2,
    stand_down_at               DATETIME2,
    stand_down_authoriser       NVARCHAR(255),
    review_completed_at         DATETIME2,

    -- Operational metrics
    actual_rto_hours            DECIMAL(10,2),
    actual_rpo_hours            DECIMAL(10,2),
    estimated_loss_sar          DECIMAL(18,2),

    -- Regulatory notification (NCEMA, SAMA for banks)
    regulator_notified_at       DATETIME2,
    regulator_reference         NVARCHAR(100),
    sama_notified               BIT           NOT NULL DEFAULT 0,
    ncema_notified              BIT           NOT NULL DEFAULT 0,

    -- PIR (Post-Incident Review)
    pir_findings_en             NVARCHAR(MAX),
    pir_findings_ar             NVARCHAR(MAX),
    pir_capa_count              INT           NOT NULL DEFAULT 0,

    is_deleted                  SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inv_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_inv_org_status ON bcm_lc.invocations (organisation_id, status, is_deleted);
CREATE INDEX ix_inv_severity   ON bcm_lc.invocations (organisation_id, severity, status, is_deleted);
GO

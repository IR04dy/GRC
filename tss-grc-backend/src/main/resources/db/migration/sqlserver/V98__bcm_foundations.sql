-- V98: BCM foundations — Sprint BCM-1 of the BCM Improvement Plan.
--
-- Adds the four BCM-cycle-bearing tables that the rest of the
-- improvement plan (BCM-2 through BCM-6) builds on:
--   bcm.assessment_cycles      — one cycle per reporting year
--   bcm.governance_records     — policy + scope + committee
--   bcm.bcm_risks              — BCM-specific risk register
--   bcm.continuity_strategies  — selected strategies per service/process
--
-- All entities are tenant-scoped (organisation_id), bilingual
-- (*_en + *_ar), soft-deletable, and publish audit events through
-- the F7 platform.audit_log + F8 automation rule engine when the
-- service layer changes their state.
--
-- See docs/bcm/BCM_IMPROVEMENT_PLAN.md §5 for the full field-by-
-- field rationale.

-- ─── Assessment cycles ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'assessment_cycles' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.assessment_cycles (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        cycle_name_en               NVARCHAR(255)   NOT NULL,
        cycle_name_ar               NVARCHAR(255)   NULL,
        reporting_year              SMALLINT        NOT NULL,
        start_date                  DATE            NULL,
        end_date                    DATE            NULL,
        scope                       NVARCHAR(MAX)   NULL,

        bcm_manager_id              BIGINT          NULL,
        executive_sponsor_id        BIGINT          NULL,

        status                      NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | IN_PROGRESS | UNDER_REVIEW | APPROVED | ARCHIVED

        overall_score               DECIMAL(5,2)    NULL,
        bia_completion_percent      DECIMAL(5,2)    NULL,
        bcp_completion_percent      DECIMAL(5,2)    NULL,
        drp_completion_percent      DECIMAL(5,2)    NULL,
        evidence_completion_percent DECIMAL(5,2)    NULL,
        open_gap_count              INT             NOT NULL DEFAULT 0,
        critical_gap_count          INT             NOT NULL DEFAULT 0,

        approved_by                 NVARCHAR(120)   NULL,
        approved_at                 DATETIME2(0)    NULL,

        is_deleted                  BIT             NOT NULL DEFAULT 0,
        created_at                  DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2(0)    NULL,

        CONSTRAINT uq_cycle_org_year UNIQUE (organisation_id, reporting_year),
        CONSTRAINT ck_cycle_status   CHECK (status IN ('DRAFT','IN_PROGRESS','UNDER_REVIEW','APPROVED','ARCHIVED'))
    );
    CREATE INDEX ix_cycle_org_status ON bcm.assessment_cycles(organisation_id, status);
END
GO

-- ─── Governance records ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'governance_records' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.governance_records (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        policy_title_en             NVARCHAR(255)   NOT NULL,
        policy_title_ar             NVARCHAR(255)   NULL,
        policy_version              NVARCHAR(20)    NOT NULL DEFAULT '1.0',
        scope                       NVARCHAR(MAX)   NULL,
        objective                   NVARCHAR(MAX)   NULL,

        bcm_manager_id              BIGINT          NULL,
        executive_sponsor_id        BIGINT          NULL,
        committee_id                BIGINT          NULL,

        review_frequency            NVARCHAR(30)    NULL,
        -- ANNUAL | SEMI_ANNUAL | QUARTERLY | ON_CHANGE
        last_review_date            DATE            NULL,
        next_review_date            DATE            NULL,
        evidence_id                 BIGINT          NULL,

        approval_status             NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | UNDER_REVIEW | CHANGES_REQUIRED | APPROVED | ACTIVE | REVIEW_DUE | ARCHIVED

        approved_by                 NVARCHAR(120)   NULL,
        approved_at                 DATETIME2(0)    NULL,

        is_deleted                  BIT             NOT NULL DEFAULT 0,
        created_at                  DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2(0)    NULL,

        CONSTRAINT ck_gov_status CHECK (approval_status IN
            ('DRAFT','UNDER_REVIEW','CHANGES_REQUIRED','APPROVED','ACTIVE','REVIEW_DUE','ARCHIVED'))
    );
    CREATE INDEX ix_gov_org_status ON bcm.governance_records(organisation_id, approval_status);
END
GO

-- ─── BCM risks ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bcm_risks' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bcm_risks (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cycle_id                    BIGINT          NULL,

        related_service_id          BIGINT          NULL,
        related_process_id          BIGINT          NULL,
        related_asset_id            BIGINT          NULL,
        related_supplier_id         BIGINT          NULL,

        title_en                    NVARCHAR(255)   NOT NULL,
        title_ar                    NVARCHAR(255)   NULL,
        cause                       NVARCHAR(MAX)   NULL,
        event                       NVARCHAR(MAX)   NULL,
        consequence                 NVARCHAR(MAX)   NULL,

        threat_category             NVARCHAR(60)    NOT NULL,
        -- TECH_FAILURE | CYBERATTACK | DATA_CENTER_OUTAGE | CLOUD_OUTAGE
        -- TELECOM_OUTAGE | POWER_FAILURE | FACILITY_UNAVAILABILITY
        -- WORKFORCE_UNAVAILABILITY | SUPPLIER_FAILURE | PANDEMIC
        -- NATURAL_HAZARD | FIRE | FLOOD | REGIONAL_DISRUPTION
        -- REGULATORY_DISRUPTION | DATA_LOSS | THIRD_PARTY_DEPENDENCY
        -- CRITICAL_INFRASTRUCTURE
        existing_controls           NVARCHAR(MAX)   NULL,

        likelihood                  INT             NULL,    -- 1..5
        impact                      INT             NULL,    -- 1..5
        inherent_risk_score         INT             NULL,    -- computed: likelihood × impact
        control_effectiveness       NVARCHAR(30)    NULL,
        -- EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED
        residual_risk_score         INT             NULL,

        risk_owner_id               BIGINT          NULL,
        treatment_plan              NVARCHAR(MAX)   NULL,

        related_bia_id              BIGINT          NULL,
        related_bcp_id              BIGINT          NULL,
        related_drp_id              BIGINT          NULL,
        related_erm_risk_id         BIGINT          NULL,

        status                      NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | ASSESSED | TREATMENT_PLANNED | ACCEPTED | MITIGATED | CLOSED

        is_deleted                  BIT             NOT NULL DEFAULT 0,
        created_at                  DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2(0)    NULL,

        CONSTRAINT ck_bcm_risk_status CHECK (status IN
            ('DRAFT','ASSESSED','TREATMENT_PLANNED','ACCEPTED','MITIGATED','CLOSED')),
        CONSTRAINT ck_bcm_risk_likelihood CHECK (likelihood IS NULL OR (likelihood BETWEEN 1 AND 5)),
        CONSTRAINT ck_bcm_risk_impact     CHECK (impact     IS NULL OR (impact     BETWEEN 1 AND 5)),
        CONSTRAINT ck_bcm_risk_ce         CHECK (control_effectiveness IS NULL OR control_effectiveness IN
            ('EFFECTIVE','NEEDS_IMPROVEMENT','INEFFECTIVE','NOT_TESTED'))
    );
    CREATE INDEX ix_bcm_risk_org_cycle  ON bcm.bcm_risks(organisation_id, cycle_id);
    CREATE INDEX ix_bcm_risk_org_status ON bcm.bcm_risks(organisation_id, status);
    CREATE INDEX ix_bcm_risk_service    ON bcm.bcm_risks(related_service_id);
END
GO

-- ─── Continuity strategies ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'continuity_strategies' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.continuity_strategies (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cycle_id                    BIGINT          NULL,

        related_service_id          BIGINT          NULL,
        related_process_id          BIGINT          NULL,
        related_bia_id              BIGINT          NULL,

        rto_requirement_hours       INT             NULL,
        rpo_requirement_hours       INT             NULL,

        selected_strategy           NVARCHAR(60)    NOT NULL,
        -- ACTIVE_ACTIVE | ACTIVE_PASSIVE | BACKUP_SITE | ALTERNATE_WORKPLACE
        -- REMOTE_WORK | MANUAL_WORKAROUND | ALTERNATE_SUPPLIER | STOCKPILING
        -- CLOUD_FAILOVER | DATA_REPLICATION | MUTUAL_AID | PROCESS_TRANSFER
        -- WORKFORCE_CROSS_TRAINING | EMERGENCY_PROCUREMENT | CRISIS_COMMUNICATION
        strategy_description_en     NVARCHAR(MAX)   NULL,
        strategy_description_ar     NVARCHAR(MAX)   NULL,
        cost_estimate               DECIMAL(15,2)   NULL,
        feasibility                 NVARCHAR(20)    NULL,
        -- HIGH | MEDIUM | LOW
        residual_risk               NVARCHAR(20)    NULL,
        -- LOW | MEDIUM | HIGH | CRITICAL
        required_resources          NVARCHAR(MAX)   NULL,
        dependencies                NVARCHAR(MAX)   NULL,
        owner_id                    BIGINT          NULL,
        evidence_id                 BIGINT          NULL,

        approval_status             NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | UNDER_REVIEW | APPROVED | REJECTED | ARCHIVED
        implementation_status       NVARCHAR(30)    NULL,
        -- NOT_STARTED | IN_PROGRESS | COMPLETED | ON_HOLD

        is_deleted                  BIT             NOT NULL DEFAULT 0,
        created_at                  DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2(0)    NULL,

        CONSTRAINT ck_strategy_approval CHECK (approval_status IN
            ('DRAFT','UNDER_REVIEW','APPROVED','REJECTED','ARCHIVED')),
        CONSTRAINT ck_strategy_impl CHECK (implementation_status IS NULL OR implementation_status IN
            ('NOT_STARTED','IN_PROGRESS','COMPLETED','ON_HOLD')),
        CONSTRAINT ck_strategy_feasibility CHECK (feasibility IS NULL OR feasibility IN ('HIGH','MEDIUM','LOW')),
        CONSTRAINT ck_strategy_residual    CHECK (residual_risk IS NULL OR residual_risk IN ('LOW','MEDIUM','HIGH','CRITICAL'))
    );
    CREATE INDEX ix_strategy_org_cycle   ON bcm.continuity_strategies(organisation_id, cycle_id);
    CREATE INDEX ix_strategy_org_approval ON bcm.continuity_strategies(organisation_id, approval_status);
    CREATE INDEX ix_strategy_service     ON bcm.continuity_strategies(related_service_id);
END
GO

-- ─── Seed: BCM Readiness Cycle 2025 + governance baseline ─────────
IF NOT EXISTS (SELECT 1 FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2025)
BEGIN
    INSERT INTO bcm.assessment_cycles
      (organisation_id, cycle_name_en, cycle_name_ar, reporting_year,
       start_date, end_date, scope, status)
    VALUES
      (1,
       'BCM Readiness Cycle 2025',
       N'دورة جاهزية استمرارية الأعمال 2025',
       2025,
       '2025-01-01', '2025-12-31',
       'Demo cycle covering every critical service in the tenant. Re-seed via V98 if dropped.',
       'IN_PROGRESS');
END
GO

IF NOT EXISTS (SELECT 1 FROM bcm.governance_records WHERE organisation_id = 1 AND policy_title_en = 'BCM Governance Policy v1.0')
BEGIN
    INSERT INTO bcm.governance_records
      (organisation_id, policy_title_en, policy_title_ar, policy_version,
       scope, objective, review_frequency, approval_status)
    VALUES
      (1,
       'BCM Governance Policy v1.0',
       N'سياسة حوكمة استمرارية الأعمال — الإصدار 1.0',
       '1.0',
       'Applies to every critical service, supplier dependency, and digital channel operated by the tenant.',
       'Maintain organisational resilience aligned to ISO 22301 + SAMA / DGA BCM expectations.',
       'ANNUAL',
       'DRAFT');
END
GO

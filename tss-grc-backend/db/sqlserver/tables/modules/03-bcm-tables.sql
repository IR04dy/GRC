-- ============================================================================
-- TSS GRC Platform - Business Continuity Management Module Tables
-- Schema: bcm
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- bcm.critical_processes
-- ============================================================================
CREATE TABLE bcm.critical_processes (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    bu_id               BIGINT                  NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(300)           NOT NULL,
    name_ar             NVARCHAR(300)           NULL,
    description_en      NVARCHAR(MAX)           NULL,
    description_ar      NVARCHAR(MAX)           NULL,
    process_owner       BIGINT                  NULL,
    priority            INT                     NOT NULL DEFAULT 3
        CONSTRAINT CK_cp_priority CHECK (priority BETWEEN 1 AND 5),
    rto_hours           INT                     NULL,
    rpo_hours           INT                     NULL,
    mtpd_hours          INT                     NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT CK_cp_status CHECK (status IN ('ACTIVE','INACTIVE','UNDER_REVIEW')),
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_critical_processes PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_cp_code UNIQUE (org_id, code),
    CONSTRAINT FK_cp_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_cp_bu FOREIGN KEY (bu_id) REFERENCES platform.business_units(id),
    CONSTRAINT FK_cp_owner FOREIGN KEY (process_owner) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- bcm.process_dependencies
-- ============================================================================
CREATE TABLE bcm.process_dependencies (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    process_id              BIGINT                  NOT NULL,
    dependency_type         NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_pd_type CHECK (dependency_type IN (
            'INTERNAL_PROCESS','EXTERNAL_SERVICE','IT_SYSTEM',
            'VENDOR','PEOPLE','FACILITY','OTHER')),
    depends_on_process_id   BIGINT                  NULL,
    dependency_name         NVARCHAR(300)           NULL,
    description             NVARCHAR(MAX)           NULL,
    criticality             NVARCHAR(50)            NOT NULL DEFAULT 'HIGH'
        CONSTRAINT CK_pd_crit CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_process_dependencies PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_pd_process FOREIGN KEY (process_id) REFERENCES bcm.critical_processes(id),
    CONSTRAINT FK_pd_depends FOREIGN KEY (depends_on_process_id) REFERENCES bcm.critical_processes(id)
);
GO

-- ============================================================================
-- bcm.business_impact_analyses
-- ============================================================================
CREATE TABLE bcm.business_impact_analyses (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    process_id              BIGINT                  NOT NULL,
    analyst_user_id         BIGINT                  NULL,
    analysis_date           DATE                    NOT NULL,
    financial_impact        NVARCHAR(50)            NULL
        CONSTRAINT CK_bia_fin CHECK (financial_impact IN ('CATASTROPHIC','MAJOR','MODERATE','MINOR','NEGLIGIBLE')),
    operational_impact      NVARCHAR(50)            NULL
        CONSTRAINT CK_bia_ops CHECK (operational_impact IN ('CATASTROPHIC','MAJOR','MODERATE','MINOR','NEGLIGIBLE')),
    reputational_impact     NVARCHAR(50)            NULL
        CONSTRAINT CK_bia_rep CHECK (reputational_impact IN ('CATASTROPHIC','MAJOR','MODERATE','MINOR','NEGLIGIBLE')),
    regulatory_impact       NVARCHAR(50)            NULL
        CONSTRAINT CK_bia_reg CHECK (regulatory_impact IN ('CATASTROPHIC','MAJOR','MODERATE','MINOR','NEGLIGIBLE')),
    overall_impact_level    NVARCHAR(50)            NULL,
    financial_impact_value  DECIMAL(18,4)           NULL,
    outage_2h_impact        NVARCHAR(MAX)           NULL,
    outage_24h_impact       NVARCHAR(MAX)           NULL,
    outage_1week_impact     NVARCHAR(MAX)           NULL,
    recommendations         NVARCHAR(MAX)           NULL,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT CK_bia_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','ARCHIVED')),
    approved_by             BIGINT                  NULL,
    approved_at             DATETIME2(7)            NULL,
    -- Link to risk register
    linked_risk_ids         NVARCHAR(MAX)           NULL,  -- JSON array of risk IDs
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_bia PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_bia_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_bia_process FOREIGN KEY (process_id) REFERENCES bcm.critical_processes(id),
    CONSTRAINT FK_bia_analyst FOREIGN KEY (analyst_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- bcm.bcm_plans
-- ============================================================================
CREATE TABLE bcm.bcm_plans (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(300)           NOT NULL,
    name_ar             NVARCHAR(300)           NULL,
    description_en      NVARCHAR(MAX)           NULL,
    plan_type           NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_plan_type CHECK (plan_type IN (
            'BCP','DRP','CRISIS_MANAGEMENT','ERP','COOP','OTHER')),
    version             NVARCHAR(20)            NOT NULL DEFAULT '1.0',
    status              NVARCHAR(50)            NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT CK_plan_status CHECK (status IN (
            'DRAFT','UNDER_REVIEW','APPROVED','ACTIVE','ARCHIVED')),
    owner_user_id       BIGINT                  NULL,
    approved_by         BIGINT                  NULL,
    approved_at         DATETIME2(7)            NULL,
    effective_date      DATE                    NULL,
    review_date         DATE                    NULL,
    last_tested_at      DATE                    NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_bcm_plans PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_bcm_plans_code UNIQUE (org_id, code),
    CONSTRAINT FK_bcm_plans_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_bcm_plans_owner FOREIGN KEY (owner_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- bcm.recovery_strategies
-- ============================================================================
CREATE TABLE bcm.recovery_strategies (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    plan_id                 BIGINT                  NOT NULL,
    process_id              BIGINT                  NULL,
    strategy_type           NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_rs_type CHECK (strategy_type IN (
            'MANUAL_WORKAROUND','ALTERNATE_SITE','CLOUD_FAILOVER',
            'MUTUAL_AID','VENDOR_SUPPORT','OTHER')),
    title_en                NVARCHAR(300)           NOT NULL,
    title_ar                NVARCHAR(300)           NULL,
    description_en          NVARCHAR(MAX)           NULL,
    resources_required      NVARCHAR(MAX)           NULL,
    estimated_cost          DECIMAL(18,4)           NULL,
    rto_achievable_hours    INT                     NULL,
    steps                   NVARCHAR(MAX)           NULL,
    is_primary              BIT                     NOT NULL DEFAULT 1,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(7)            NULL,
    CONSTRAINT PK_recovery_strategies PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_rs_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcm_plans(id),
    CONSTRAINT FK_rs_process FOREIGN KEY (process_id) REFERENCES bcm.critical_processes(id)
);
GO

-- ============================================================================
-- bcm.bcm_exercises
-- ============================================================================
CREATE TABLE bcm.bcm_exercises (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    plan_id             BIGINT                  NULL,
    name_en             NVARCHAR(300)           NOT NULL,
    name_ar             NVARCHAR(300)           NULL,
    exercise_type       NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_ex_type CHECK (exercise_type IN (
            'TABLETOP','WALKTHROUGH','SIMULATION','FULL_SCALE','CALL_TREE')),
    scheduled_date      DATE                    NULL,
    executed_date       DATE                    NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'PLANNED'
        CONSTRAINT CK_ex_status CHECK (status IN (
            'PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')),
    facilitator_user_id BIGINT                  NULL,
    objectives          NVARCHAR(MAX)           NULL,
    findings            NVARCHAR(MAX)           NULL,
    lessons_learned     NVARCHAR(MAX)           NULL,
    action_items        NVARCHAR(MAX)           NULL,
    next_exercise_date  DATE                    NULL,
    overall_result      NVARCHAR(50)            NULL
        CONSTRAINT CK_ex_result CHECK (overall_result IN ('PASSED','PARTIALLY_PASSED','FAILED','NOT_RATED')),
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_bcm_exercises PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ex_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_ex_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcm_plans(id)
);
GO

-- ============================================================================
-- bcm.bcm_incidents
-- ============================================================================
CREATE TABLE bcm.bcm_incidents (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    code                    NVARCHAR(50)            NOT NULL,
    title                   NVARCHAR(500)           NOT NULL,
    description             NVARCHAR(MAX)           NULL,
    incident_type           NVARCHAR(100)           NOT NULL,
    severity                NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM',
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT CK_bcmi_status CHECK (status IN (
            'ACTIVE','RESOLVED','CLOSED')),
    occurred_at             DATETIME2(7)            NULL,
    resolved_at             DATETIME2(7)            NULL,
    activated_plan_id       BIGINT                  NULL,
    incident_commander      BIGINT                  NULL,
    impact_description      NVARCHAR(MAX)           NULL,
    linked_risk_id          BIGINT                  NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_bcm_incidents PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_bcmi_code UNIQUE (org_id, code),
    CONSTRAINT FK_bcmi_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_bcmi_plan FOREIGN KEY (activated_plan_id) REFERENCES bcm.bcm_plans(id),
    CONSTRAINT FK_bcmi_risk FOREIGN KEY (linked_risk_id) REFERENCES risk.risks(id)
);
GO

PRINT 'BCM module tables created successfully.';
GO

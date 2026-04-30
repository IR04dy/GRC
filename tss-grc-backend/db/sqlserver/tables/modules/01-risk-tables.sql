-- ============================================================================
-- TSS GRC Platform - Risk Module Tables
-- Schema: risk
-- Compatible: SQL Server 2019 Express Edition
-- Purpose: Enterprise Risk Management (ERM) module tables
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- risk.risk_categories
-- ============================================================================
CREATE TABLE risk.risk_categories (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL CONSTRAINT DF_risk_categories_ext_id DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(200)           NOT NULL,
    name_ar             NVARCHAR(200)           NULL,
    description_en      NVARCHAR(1000)          NULL,
    description_ar      NVARCHAR(1000)          NULL,
    parent_id           BIGINT                  NULL,
    display_order       INT                     NOT NULL DEFAULT 0,
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    deleted_by          BIGINT                  NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_risk_categories PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_risk_categories_code UNIQUE (org_id, code),
    CONSTRAINT FK_risk_categories_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations(id),
    CONSTRAINT FK_risk_categories_parent FOREIGN KEY (parent_id)
        REFERENCES risk.risk_categories(id)
);
GO

-- ============================================================================
-- risk.risks
-- Central risk register — aggregates risks from all modules
-- ============================================================================
CREATE TABLE risk.risks (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL CONSTRAINT DF_risks_ext_id DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    bu_id                   BIGINT                  NULL,
    code                    NVARCHAR(50)            NOT NULL,
    title_en                NVARCHAR(500)           NOT NULL,
    title_ar                NVARCHAR(500)           NULL,
    description_en          NVARCHAR(MAX)           NULL,
    description_ar          NVARCHAR(MAX)           NULL,
    category_id             BIGINT                  NULL,
    risk_type               NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_risks_type CHECK (risk_type IN (
            'STRATEGIC','OPERATIONAL','FINANCIAL','COMPLIANCE',
            'CYBER','REPUTATIONAL','ENVIRONMENTAL','LEGAL')),
    -- Source tracking for unified risk view
    source_module           NVARCHAR(50)            NOT NULL DEFAULT 'ERM'
        CONSTRAINT CK_risks_source CHECK (source_module IN (
            'ERM','CYBER','BCM','PRIVACY','COMPLIANCE')),
    source_entity_id        BIGINT                  NULL,
    -- Inherent risk (before controls)
    likelihood              INT                     NULL
        CONSTRAINT CK_risks_likelihood CHECK (likelihood BETWEEN 1 AND 5),
    impact                  INT                     NULL
        CONSTRAINT CK_risks_impact CHECK (impact BETWEEN 1 AND 5),
    inherent_score          DECIMAL(5,2)            NULL,
    -- Residual risk (after controls)
    residual_likelihood     INT                     NULL
        CONSTRAINT CK_risks_res_likelihood CHECK (residual_likelihood BETWEEN 1 AND 5),
    residual_impact         INT                     NULL
        CONSTRAINT CK_risks_res_impact CHECK (residual_impact BETWEEN 1 AND 5),
    residual_score          DECIMAL(5,2)            NULL,
    risk_level              NVARCHAR(50)            NULL
        CONSTRAINT CK_risks_level CHECK (risk_level IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT CK_risks_status CHECK (status IN (
            'DRAFT','OPEN','UNDER_TREATMENT','ACCEPTED','CLOSED','ARCHIVED')),
    treatment_strategy      NVARCHAR(50)            NULL
        CONSTRAINT CK_risks_treatment CHECK (treatment_strategy IN (
            'ACCEPT','MITIGATE','TRANSFER','AVOID','SHARE')),
    owner_user_id           BIGINT                  NULL,
    review_date             DATE                    NULL,
    last_reviewed_at        DATETIME2(7)            NULL,
    reviewed_by             BIGINT                  NULL,
    -- Workflow linkage
    workflow_instance_id    BIGINT                  NULL,
    -- Audit
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    deleted_by              BIGINT                  NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_risks PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_risks_code UNIQUE (org_id, code),
    CONSTRAINT FK_risks_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations(id),
    CONSTRAINT FK_risks_bu FOREIGN KEY (bu_id)
        REFERENCES platform.business_units(id),
    CONSTRAINT FK_risks_category FOREIGN KEY (category_id)
        REFERENCES risk.risk_categories(id),
    CONSTRAINT FK_risks_owner FOREIGN KEY (owner_user_id)
        REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- risk.risk_assessments
-- ============================================================================
CREATE TABLE risk.risk_assessments (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    risk_id             BIGINT                  NOT NULL,
    org_id              BIGINT                  NOT NULL,
    assessed_by         BIGINT                  NULL,
    assessment_date     DATE                    NOT NULL,
    likelihood          INT                     NOT NULL CONSTRAINT CK_ra_likelihood CHECK (likelihood BETWEEN 1 AND 5),
    impact              INT                     NOT NULL CONSTRAINT CK_ra_impact CHECK (impact BETWEEN 1 AND 5),
    score               DECIMAL(5,2)            NOT NULL,
    risk_level          NVARCHAR(50)            NULL,
    assessment_type     NVARCHAR(50)            NOT NULL DEFAULT 'INHERENT'
        CONSTRAINT CK_ra_type CHECK (assessment_type IN ('INHERENT','RESIDUAL')),
    methodology         NVARCHAR(100)           NULL,
    notes               NVARCHAR(MAX)           NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_risk_assessments PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ra_risk FOREIGN KEY (risk_id) REFERENCES risk.risks(id),
    CONSTRAINT FK_ra_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_ra_assessor FOREIGN KEY (assessed_by) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- risk.risk_treatments
-- ============================================================================
CREATE TABLE risk.risk_treatments (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    risk_id                 BIGINT                  NOT NULL,
    org_id                  BIGINT                  NOT NULL,
    title_en                NVARCHAR(500)           NOT NULL,
    title_ar                NVARCHAR(500)           NULL,
    description_en          NVARCHAR(MAX)           NULL,
    description_ar          NVARCHAR(MAX)           NULL,
    treatment_type          NVARCHAR(50)            NOT NULL DEFAULT 'MITIGATE'
        CONSTRAINT CK_rt_type CHECK (treatment_type IN (
            'ACCEPT','MITIGATE','TRANSFER','AVOID','SHARE')),
    responsible_user_id     BIGINT                  NULL,
    target_date             DATE                    NULL,
    actual_completion_date  DATE                    NULL,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'PLANNED'
        CONSTRAINT CK_rt_status CHECK (status IN (
            'PLANNED','IN_PROGRESS','COMPLETED','CANCELLED','OVERDUE')),
    effectiveness_rating    NVARCHAR(50)            NULL,
    cost_estimate           DECIMAL(18,4)           NULL,
    notes                   NVARCHAR(MAX)           NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_risk_treatments PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_rt_risk FOREIGN KEY (risk_id) REFERENCES risk.risks(id),
    CONSTRAINT FK_rt_responsible FOREIGN KEY (responsible_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- risk.risk_controls (bridge table: risk ↔ control)
-- ============================================================================
CREATE TABLE risk.risk_controls (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    risk_id                 BIGINT                  NOT NULL,
    control_id              BIGINT                  NOT NULL,
    effectiveness_rating    NVARCHAR(50)            NULL,
    notes                   NVARCHAR(1000)          NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_risk_controls PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_risk_controls UNIQUE (risk_id, control_id),
    CONSTRAINT FK_rc_risk FOREIGN KEY (risk_id) REFERENCES risk.risks(id),
    CONSTRAINT FK_rc_control FOREIGN KEY (control_id) REFERENCES platform.controls(id)
);
GO

-- ============================================================================
-- risk.risk_appetite_statements
-- ============================================================================
CREATE TABLE risk.risk_appetite_statements (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    org_id              BIGINT                  NOT NULL,
    category_id         BIGINT                  NULL,
    risk_type           NVARCHAR(50)            NULL,
    appetite_level      NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_ras_level CHECK (appetite_level IN (
            'VERY_LOW','LOW','MODERATE','HIGH','VERY_HIGH')),
    threshold_value     DECIMAL(5,2)            NULL,
    description_en      NVARCHAR(MAX)           NULL,
    description_ar      NVARCHAR(MAX)           NULL,
    approved_by         BIGINT                  NULL,
    approved_at         DATETIME2(7)            NULL,
    valid_from          DATE                    NULL,
    valid_to            DATE                    NULL,
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_risk_appetite PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ras_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_ras_category FOREIGN KEY (category_id) REFERENCES risk.risk_categories(id)
);
GO

-- ============================================================================
-- risk.risk_indicators (Key Risk Indicators)
-- ============================================================================
CREATE TABLE risk.risk_indicators (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    risk_id             BIGINT                  NOT NULL,
    org_id              BIGINT                  NOT NULL,
    name_en             NVARCHAR(200)           NOT NULL,
    name_ar             NVARCHAR(200)           NULL,
    metric              NVARCHAR(500)           NULL,
    unit                NVARCHAR(50)            NULL,
    threshold_green     DECIMAL(18,4)           NULL,
    threshold_amber     DECIMAL(18,4)           NULL,
    threshold_red       DECIMAL(18,4)           NULL,
    current_value       DECIMAL(18,4)           NULL,
    kri_status          NVARCHAR(20)            NULL
        CONSTRAINT CK_kri_status CHECK (kri_status IN ('GREEN','AMBER','RED','NOT_SET')),
    last_updated        DATETIME2(7)            NULL,
    owner_user_id       BIGINT                  NULL,
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_risk_indicators PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_kri_risk FOREIGN KEY (risk_id) REFERENCES risk.risks(id),
    CONSTRAINT FK_kri_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

PRINT 'Risk module tables created successfully.';
GO

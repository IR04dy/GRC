-- ============================================================================
-- TSS GRC Platform - Data Governance / Data Office Module Tables
-- Schema: platform (shared — data governance is a cross-cutting concern)
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- platform.data_assets
-- ============================================================================
CREATE TABLE platform.data_assets (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    bu_id               BIGINT                  NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(300)           NOT NULL,
    name_ar             NVARCHAR(300)           NULL,
    description_en      NVARCHAR(MAX)           NULL,
    asset_type          NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_da_type CHECK (asset_type IN (
            'DATABASE','FILE_SYSTEM','APPLICATION','API',
            'REPORT','DATAWAREHOUSE','STREAMING','OTHER')),
    classification_id   BIGINT                  NULL,
    owner_user_id       BIGINT                  NULL,
    steward_user_id     BIGINT                  NULL,
    business_criticality NVARCHAR(50)           NULL
        CONSTRAINT CK_da_crit CHECK (business_criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    storage_location    NVARCHAR(500)           NULL,
    technology          NVARCHAR(200)           NULL,
    is_personal_data    BIT                     NOT NULL DEFAULT 0,
    retention_policy    NVARCHAR(200)           NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT CK_da_status CHECK (status IN ('ACTIVE','INACTIVE','DECOMMISSIONED')),
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_data_assets PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_da_code UNIQUE (org_id, code),
    CONSTRAINT FK_da_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_da_bu FOREIGN KEY (bu_id) REFERENCES platform.business_units(id),
    CONSTRAINT FK_da_owner FOREIGN KEY (owner_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- platform.data_classifications
-- ============================================================================
CREATE TABLE platform.data_classifications (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    org_id              BIGINT                  NOT NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(100)           NOT NULL,
    name_ar             NVARCHAR(100)           NULL,
    description_en      NVARCHAR(500)           NULL,
    level               INT                     NOT NULL,  -- 1=highest sensitivity
    color_code          NVARCHAR(20)            NULL,
    handling_policy     NVARCHAR(MAX)           NULL,
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_data_classifications PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_dc_code UNIQUE (org_id, code),
    CONSTRAINT FK_dc_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- Add FK from data_assets to data_classifications (after both tables exist)
ALTER TABLE platform.data_assets
    ADD CONSTRAINT FK_da_classification
    FOREIGN KEY (classification_id) REFERENCES platform.data_classifications(id);
GO

-- ============================================================================
-- platform.data_quality_rules
-- ============================================================================
CREATE TABLE platform.data_quality_rules (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    data_asset_id       BIGINT                  NOT NULL,
    org_id              BIGINT                  NOT NULL,
    rule_name           NVARCHAR(200)           NOT NULL,
    dimension           NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_dqr_dim CHECK (dimension IN (
            'COMPLETENESS','ACCURACY','CONSISTENCY','TIMELINESS',
            'VALIDITY','UNIQUENESS','INTEGRITY')),
    rule_description    NVARCHAR(MAX)           NULL,
    check_frequency     NVARCHAR(50)            NULL,
    threshold_value     DECIMAL(5,2)            NULL,
    last_check_date     DATE                    NULL,
    last_check_score    DECIMAL(5,2)            NULL,
    last_check_status   NVARCHAR(50)            NULL
        CONSTRAINT CK_dqr_status CHECK (last_check_status IN ('PASS','FAIL','WARNING','NOT_RUN')),
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_data_quality_rules PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_dqr_asset FOREIGN KEY (data_asset_id) REFERENCES platform.data_assets(id),
    CONSTRAINT FK_dqr_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- ============================================================================
-- platform.data_lineage
-- ============================================================================
CREATE TABLE platform.data_lineage (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    org_id                  BIGINT                  NOT NULL,
    source_asset_id         BIGINT                  NOT NULL,
    target_asset_id         BIGINT                  NOT NULL,
    lineage_type            NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_dl_type CHECK (lineage_type IN (
            'COPY','TRANSFORM','AGGREGATE','DERIVE','INGEST','PUBLISH')),
    transformation_desc     NVARCHAR(MAX)           NULL,
    is_active               BIT                     NOT NULL DEFAULT 1,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_data_lineage PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_dl_source_target UNIQUE (source_asset_id, target_asset_id, lineage_type),
    CONSTRAINT FK_dl_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_dl_source FOREIGN KEY (source_asset_id) REFERENCES platform.data_assets(id),
    CONSTRAINT FK_dl_target FOREIGN KEY (target_asset_id) REFERENCES platform.data_assets(id)
);
GO

-- ============================================================================
-- platform.data_stewards
-- ============================================================================
CREATE TABLE platform.data_stewards (
    id              BIGINT IDENTITY(1,1)    NOT NULL,
    data_asset_id   BIGINT                  NOT NULL,
    user_id         BIGINT                  NOT NULL,
    steward_type    NVARCHAR(50)            NOT NULL DEFAULT 'DATA_STEWARD'
        CONSTRAINT CK_ds_type CHECK (steward_type IN (
            'DATA_OWNER','DATA_STEWARD','DATA_CUSTODIAN')),
    is_primary      BIT                     NOT NULL DEFAULT 0,
    assigned_by     BIGINT                  NULL,
    assigned_at     DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    valid_until     DATE                    NULL,
    CONSTRAINT PK_data_stewards PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_ds_asset_user_type UNIQUE (data_asset_id, user_id, steward_type),
    CONSTRAINT FK_ds_asset FOREIGN KEY (data_asset_id) REFERENCES platform.data_assets(id),
    CONSTRAINT FK_ds_user FOREIGN KEY (user_id) REFERENCES iam.users(id)
);
GO

PRINT 'Data Governance module tables created successfully.';
GO

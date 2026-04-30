-- ============================================================================
-- TSS GRC Platform - Business Units Table
-- Schema: platform
-- Purpose: Organizational hierarchy within each tenant
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.business_units', 'U') IS NOT NULL
    DROP TABLE platform.business_units;
GO

CREATE TABLE platform.business_units
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id     UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    org_id          BIGINT                          NOT NULL,
    code            NVARCHAR(50)                    NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    type            NVARCHAR(50)                    NOT NULL,
    parent_bu_id    BIGINT                          NULL,
    head_user_id    BIGINT                          NULL,       -- FK added after iam.users is created
    level           INT                             NULL,
    display_order   INT                             NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,
    is_deleted      BIT                             NOT NULL    DEFAULT 0,
    deleted_at      DATETIME2                       NULL,
    deleted_by      BIGINT                          NULL,
    row_version     ROWVERSION                      NOT NULL,

    CONSTRAINT PK_business_units PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_business_units_external_id UNIQUE (external_id),

    CONSTRAINT CK_business_units_type CHECK (type IN (N'DIVISION', N'DEPARTMENT', N'SECTION', N'TEAM')),

    CONSTRAINT FK_business_units_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_business_units_parent FOREIGN KEY (parent_bu_id)
        REFERENCES platform.business_units (id)
);
GO

PRINT 'Table platform.business_units created successfully.';
GO

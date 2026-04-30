-- ============================================================================
-- TSS GRC Platform - Organizations Table
-- Schema: platform
-- Purpose: Multi-tenant organization hierarchy (tenant, subsidiary, branch)
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.organizations', 'U') IS NOT NULL
    DROP TABLE platform.organizations;
GO

CREATE TABLE platform.organizations
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id     UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    code            NVARCHAR(50)                    NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    type            NVARCHAR(50)                    NOT NULL,
    parent_org_id   BIGINT                          NULL,
    country         NVARCHAR(100)                   NOT NULL    DEFAULT N'Saudi Arabia',
    city            NVARCHAR(100)                   NULL,
    address         NVARCHAR(500)                   NULL,
    phone           NVARCHAR(50)                    NULL,
    email           NVARCHAR(200)                   NULL,
    website         NVARCHAR(200)                   NULL,
    logo_path       NVARCHAR(500)                   NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,
    is_deleted      BIT                             NOT NULL    DEFAULT 0,
    deleted_at      DATETIME2                       NULL,
    deleted_by      BIGINT                          NULL,
    row_version     ROWVERSION                      NOT NULL,

    CONSTRAINT PK_organizations PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_organizations_code UNIQUE (code),

    CONSTRAINT UQ_organizations_external_id UNIQUE (external_id),

    CONSTRAINT CK_organizations_type CHECK (type IN (N'TENANT', N'SUBSIDIARY', N'BRANCH')),

    CONSTRAINT FK_organizations_parent FOREIGN KEY (parent_org_id)
        REFERENCES platform.organizations (id)
);
GO

PRINT 'Table platform.organizations created successfully.';
GO

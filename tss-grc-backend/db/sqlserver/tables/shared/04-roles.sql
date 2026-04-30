-- ============================================================================
-- TSS GRC Platform - Roles Table
-- Schema: iam
-- Purpose: Application roles for role-based access control
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('iam.roles', 'U') IS NOT NULL
    DROP TABLE iam.roles;
GO

CREATE TABLE iam.roles
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id     UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    code            NVARCHAR(100)                   NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    description_ar  NVARCHAR(500)                   NULL,
    module_code     NVARCHAR(50)                    NULL,
    is_system       BIT                             NOT NULL    DEFAULT 0,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,
    is_deleted      BIT                             NOT NULL    DEFAULT 0,
    deleted_at      DATETIME2                       NULL,
    deleted_by      BIGINT                          NULL,
    row_version     ROWVERSION                      NOT NULL,

    CONSTRAINT PK_roles PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_roles_external_id UNIQUE (external_id),

    CONSTRAINT UQ_roles_code UNIQUE (code)
);
GO

PRINT 'Table iam.roles created successfully.';
GO

-- ============================================================================
-- TSS GRC Platform - Permissions Table
-- Schema: iam
-- Purpose: Granular permission definitions for RBAC
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('iam.permissions', 'U') IS NOT NULL
    DROP TABLE iam.permissions;
GO

CREATE TABLE iam.permissions
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    code            NVARCHAR(200)                   NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    module_code     NVARCHAR(50)                    NULL,
    resource_type   NVARCHAR(100)                   NULL,
    action          NVARCHAR(50)                    NOT NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_permissions PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_permissions_code UNIQUE (code),

    CONSTRAINT CK_permissions_action CHECK (action IN (
        N'CREATE', N'READ', N'UPDATE', N'DELETE',
        N'APPROVE', N'REJECT', N'EXPORT', N'IMPORT', N'CONFIGURE'
    ))
);
GO

PRINT 'Table iam.permissions created successfully.';
GO

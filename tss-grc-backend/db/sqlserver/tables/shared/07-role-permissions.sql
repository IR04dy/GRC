-- ============================================================================
-- TSS GRC Platform - Role Permissions Table
-- Schema: iam
-- Purpose: Maps roles to permissions
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('iam.role_permissions', 'U') IS NOT NULL
    DROP TABLE iam.role_permissions;
GO

CREATE TABLE iam.role_permissions
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    role_id         BIGINT                          NOT NULL,
    permission_id   BIGINT                          NOT NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_role_permissions PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_role_permissions UNIQUE (role_id, permission_id),

    CONSTRAINT FK_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES iam.roles (id),

    CONSTRAINT FK_role_permissions_permission FOREIGN KEY (permission_id)
        REFERENCES iam.permissions (id)
);
GO

PRINT 'Table iam.role_permissions created successfully.';
GO

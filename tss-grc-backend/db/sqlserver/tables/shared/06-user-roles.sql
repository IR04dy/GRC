-- ============================================================================
-- TSS GRC Platform - User Roles Table
-- Schema: iam
-- Purpose: Maps users to roles with org/BU scoping and time-bounded validity
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('iam.user_roles', 'U') IS NOT NULL
    DROP TABLE iam.user_roles;
GO

CREATE TABLE iam.user_roles
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    user_id         BIGINT                          NOT NULL,
    role_id         BIGINT                          NOT NULL,
    org_id          BIGINT                          NOT NULL,
    bu_id           BIGINT                          NULL,
    assigned_by     BIGINT                          NOT NULL,
    assigned_at     DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    valid_from      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    valid_to        DATETIME2                       NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_user_roles PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_user_roles_assignment UNIQUE (user_id, role_id, org_id, bu_id),

    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id)
        REFERENCES iam.users (id),

    CONSTRAINT FK_user_roles_role FOREIGN KEY (role_id)
        REFERENCES iam.roles (id),

    CONSTRAINT FK_user_roles_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_user_roles_bu FOREIGN KEY (bu_id)
        REFERENCES platform.business_units (id),

    CONSTRAINT FK_user_roles_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES iam.users (id)
);
GO

PRINT 'Table iam.user_roles created successfully.';
GO

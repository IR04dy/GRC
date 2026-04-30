-- ============================================================================
-- TSS GRC Platform - Licenses Table
-- Schema: admin
-- Purpose: Module licensing per organization
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('admin.licenses', 'U') IS NOT NULL
    DROP TABLE admin.licenses;
GO

CREATE TABLE admin.licenses
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    org_id          BIGINT                          NOT NULL,
    module_id       BIGINT                          NOT NULL,
    license_key     NVARCHAR(500)                   NULL,
    license_type    NVARCHAR(50)                    NOT NULL,
    tier            NVARCHAR(50)                    NOT NULL,
    max_users       INT                             NULL,
    current_users   INT                             NOT NULL    DEFAULT 0,
    starts_at       DATETIME2                       NOT NULL,
    expires_at      DATETIME2                       NOT NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    issued_by       BIGINT                          NULL,
    issued_at       DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_licenses PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_licenses_type CHECK (license_type IN (N'NAMED_USER', N'CONCURRENT', N'ENTERPRISE')),

    CONSTRAINT CK_licenses_tier CHECK (tier IN (N'ESSENTIAL', N'PROFESSIONAL', N'ENTERPRISE')),

    CONSTRAINT FK_licenses_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_licenses_module FOREIGN KEY (module_id)
        REFERENCES admin.modules (id),

    CONSTRAINT FK_licenses_issued_by FOREIGN KEY (issued_by)
        REFERENCES iam.users (id)
);
GO

PRINT 'Table admin.licenses created successfully.';
GO

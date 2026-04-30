-- ============================================================================
-- TSS GRC Platform - Modules Table
-- Schema: admin
-- Purpose: Platform module registry for licensing and navigation
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('admin.modules', 'U') IS NOT NULL
    DROP TABLE admin.modules;
GO

CREATE TABLE admin.modules
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    code            NVARCHAR(50)                    NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    description_ar  NVARCHAR(500)                   NULL,
    icon            NVARCHAR(100)                   NULL,
    version         NVARCHAR(20)                    NULL,
    display_order   INT                             NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    is_core         BIT                             NOT NULL    DEFAULT 0,
    url_prefix      NVARCHAR(100)                   NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_modules PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_modules_code UNIQUE (code)
);
GO

PRINT 'Table admin.modules created successfully.';
GO

-- ============================================================================
-- TSS GRC Platform - Control Frameworks & Domains Tables
-- Schema: platform
-- Purpose: Regulatory/standard frameworks and their domain hierarchies
-- ============================================================================

USE TSS_GRC;
GO

-- --------------------------------------------------------------------------
-- 1. Control Frameworks
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.control_frameworks', 'U') IS NOT NULL
    DROP TABLE platform.control_frameworks;
GO

CREATE TABLE platform.control_frameworks
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    code            NVARCHAR(50)                    NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    version         NVARCHAR(20)                    NULL,
    issuing_body_en NVARCHAR(200)                   NULL,
    issuing_body_ar NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    description_ar  NVARCHAR(500)                   NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_control_frameworks PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_control_frameworks_code UNIQUE (code)
);
GO

-- --------------------------------------------------------------------------
-- 2. Control Framework Domains (hierarchical)
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.control_framework_domains', 'U') IS NOT NULL
    DROP TABLE platform.control_framework_domains;
GO

CREATE TABLE platform.control_framework_domains
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    framework_id    BIGINT                          NOT NULL,
    code            NVARCHAR(50)                    NOT NULL,
    name_en         NVARCHAR(300)                   NOT NULL,
    name_ar         NVARCHAR(300)                   NULL,
    parent_domain_id BIGINT                         NULL,
    display_order   INT                             NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_control_framework_domains PRIMARY KEY CLUSTERED (id),

    CONSTRAINT FK_cf_domains_framework FOREIGN KEY (framework_id)
        REFERENCES platform.control_frameworks (id),

    CONSTRAINT FK_cf_domains_parent FOREIGN KEY (parent_domain_id)
        REFERENCES platform.control_framework_domains (id)
);
GO

PRINT 'Tables platform.control_frameworks and platform.control_framework_domains created successfully.';
GO

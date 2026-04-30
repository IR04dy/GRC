-- ============================================================================
-- TSS GRC Platform - Control Framework Mappings Table
-- Schema: platform
-- Purpose: Maps internal controls to framework reference codes
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.control_framework_mappings', 'U') IS NOT NULL
    DROP TABLE platform.control_framework_mappings;
GO

CREATE TABLE platform.control_framework_mappings
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    control_id      BIGINT                          NOT NULL,
    framework_id    BIGINT                          NOT NULL,
    domain_id       BIGINT                          NULL,
    reference_code  NVARCHAR(50)                    NOT NULL,
    reference_title NVARCHAR(500)                   NULL,
    notes           NVARCHAR(MAX)                   NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_control_framework_mappings PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_cfm_control_framework_ref UNIQUE (control_id, framework_id, reference_code),

    CONSTRAINT FK_cfm_control FOREIGN KEY (control_id)
        REFERENCES platform.controls (id),

    CONSTRAINT FK_cfm_framework FOREIGN KEY (framework_id)
        REFERENCES platform.control_frameworks (id),

    CONSTRAINT FK_cfm_domain FOREIGN KEY (domain_id)
        REFERENCES platform.control_framework_domains (id)
);
GO

PRINT 'Table platform.control_framework_mappings created successfully.';
GO

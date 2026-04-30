-- ============================================================================
-- TSS GRC Platform - Controls Table
-- Schema: platform
-- Purpose: Central control library shared across all GRC modules
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.controls', 'U') IS NOT NULL
    DROP TABLE platform.controls;
GO

CREATE TABLE platform.controls
(
    id                      BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id             UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    org_id                  BIGINT                          NOT NULL,
    code                    NVARCHAR(50)                    NOT NULL,
    title_en                NVARCHAR(300)                   NOT NULL,
    title_ar                NVARCHAR(300)                   NULL,
    description_en          NVARCHAR(MAX)                   NULL,
    description_ar          NVARCHAR(MAX)                   NULL,
    control_type            NVARCHAR(50)                    NOT NULL,
    control_category        NVARCHAR(100)                   NULL,
    implementation_status   NVARCHAR(50)                    NOT NULL    DEFAULT N'NOT_IMPLEMENTED',
    effectiveness           NVARCHAR(50)                    NULL,
    owner_user_id           BIGINT                          NULL,
    review_frequency        NVARCHAR(50)                    NULL,
    last_reviewed_at        DATETIME2                       NULL,
    next_review_at          DATETIME2                       NULL,
    is_active               BIT                             NOT NULL    DEFAULT 1,
    created_by              BIGINT                          NULL,
    created_at              DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                          NULL,
    updated_at              DATETIME2                       NULL,
    is_deleted              BIT                             NOT NULL    DEFAULT 0,
    deleted_at              DATETIME2                       NULL,
    deleted_by              BIGINT                          NULL,
    row_version             ROWVERSION                      NOT NULL,

    CONSTRAINT PK_controls PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_controls_external_id UNIQUE (external_id),

    CONSTRAINT CK_controls_type CHECK (control_type IN (
        N'PREVENTIVE', N'DETECTIVE', N'CORRECTIVE', N'DIRECTIVE'
    )),

    CONSTRAINT CK_controls_impl_status CHECK (implementation_status IN (
        N'NOT_IMPLEMENTED', N'PARTIALLY_IMPLEMENTED', N'FULLY_IMPLEMENTED'
    )),

    CONSTRAINT CK_controls_effectiveness CHECK (effectiveness IN (
        N'INEFFECTIVE', N'PARTIALLY_EFFECTIVE', N'EFFECTIVE', N'HIGHLY_EFFECTIVE'
    )),

    CONSTRAINT FK_controls_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_controls_owner FOREIGN KEY (owner_user_id)
        REFERENCES iam.users (id)
);
GO

PRINT 'Table platform.controls created successfully.';
GO

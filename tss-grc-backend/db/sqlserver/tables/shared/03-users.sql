-- ============================================================================
-- TSS GRC Platform - Users Table
-- Schema: iam
-- Purpose: Application user accounts with authentication and profile data
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('iam.users', 'U') IS NOT NULL
    DROP TABLE iam.users;
GO

CREATE TABLE iam.users
(
    id                      BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id             UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    username                NVARCHAR(200)                   NOT NULL,
    email                   NVARCHAR(200)                   NOT NULL,
    first_name_en           NVARCHAR(100)                   NOT NULL,
    last_name_en            NVARCHAR(100)                   NOT NULL,
    first_name_ar           NVARCHAR(100)                   NULL,
    last_name_ar            NVARCHAR(100)                   NULL,
    display_name_en         NVARCHAR(200)                   NULL,
    display_name_ar         NVARCHAR(200)                   NULL,
    password_hash           NVARCHAR(500)                   NULL,
    org_id                  BIGINT                          NOT NULL,
    bu_id                   BIGINT                          NULL,
    job_title_en            NVARCHAR(200)                   NULL,
    job_title_ar            NVARCHAR(200)                   NULL,
    phone                   NVARCHAR(50)                    NULL,
    mobile                  NVARCHAR(50)                    NULL,
    is_active               BIT                             NOT NULL    DEFAULT 1,
    is_locked               BIT                             NOT NULL    DEFAULT 0,
    failed_login_attempts   INT                             NOT NULL    DEFAULT 0,
    locked_until            DATETIME2                       NULL,
    last_login_at           DATETIME2                       NULL,
    password_changed_at     DATETIME2                       NULL,
    preferred_locale        NVARCHAR(10)                    NOT NULL    DEFAULT N'en',
    timezone                NVARCHAR(50)                    NULL,
    mfa_enabled             BIT                             NOT NULL    DEFAULT 0,
    mfa_secret              NVARCHAR(500)                   NULL,
    must_change_password    BIT                             NOT NULL    DEFAULT 0,
    created_by              BIGINT                          NULL,
    created_at              DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                          NULL,
    updated_at              DATETIME2                       NULL,
    is_deleted              BIT                             NOT NULL    DEFAULT 0,
    deleted_at              DATETIME2                       NULL,
    deleted_by              BIGINT                          NULL,
    row_version             ROWVERSION                      NOT NULL,

    CONSTRAINT PK_users PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_users_external_id UNIQUE (external_id),

    CONSTRAINT UQ_users_username UNIQUE (username),

    CONSTRAINT UQ_users_email UNIQUE (email),

    CONSTRAINT FK_users_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_users_bu FOREIGN KEY (bu_id)
        REFERENCES platform.business_units (id)
);
GO

-- Now add the deferred FK from business_units.head_user_id to iam.users
ALTER TABLE platform.business_units
    ADD CONSTRAINT FK_business_units_head_user
    FOREIGN KEY (head_user_id) REFERENCES iam.users (id);
GO

PRINT 'Table iam.users created successfully.';
GO

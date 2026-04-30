-- ============================================================================
-- TSS GRC Platform - Notifications & Notification Templates Tables
-- Schema: platform
-- Purpose: In-app notifications and configurable notification templates
-- ============================================================================

USE TSS_GRC;
GO

-- --------------------------------------------------------------------------
-- 1. Notifications
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.notifications', 'U') IS NOT NULL
    DROP TABLE platform.notifications;
GO

CREATE TABLE platform.notifications
(
    id          BIGINT          IDENTITY(1,1)   NOT NULL,
    org_id      BIGINT                          NOT NULL,
    user_id     BIGINT                          NOT NULL,
    type        NVARCHAR(50)                    NOT NULL,
    title_en    NVARCHAR(300)                   NOT NULL,
    title_ar    NVARCHAR(300)                   NULL,
    body_en     NVARCHAR(MAX)                   NULL,
    body_ar     NVARCHAR(MAX)                   NULL,
    link_url    NVARCHAR(500)                   NULL,
    is_read     BIT                             NOT NULL    DEFAULT 0,
    read_at     DATETIME2                       NULL,
    created_at  DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),

    CONSTRAINT PK_notifications PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_notifications_type CHECK (type IN (
        N'INFO', N'WARNING', N'ACTION_REQUIRED', N'SYSTEM'
    )),

    CONSTRAINT FK_notifications_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id)
        REFERENCES iam.users (id)
);
GO

-- --------------------------------------------------------------------------
-- 2. Notification Templates
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.notification_templates', 'U') IS NOT NULL
    DROP TABLE platform.notification_templates;
GO

CREATE TABLE platform.notification_templates
(
    id                  BIGINT          IDENTITY(1,1)   NOT NULL,
    code                NVARCHAR(100)                   NOT NULL,
    name_en             NVARCHAR(200)                   NOT NULL,
    event_type          NVARCHAR(100)                   NOT NULL,
    channel             NVARCHAR(50)                    NOT NULL,
    subject_en          NVARCHAR(500)                   NULL,
    subject_ar          NVARCHAR(500)                   NULL,
    body_template_en    NVARCHAR(MAX)                   NULL,
    body_template_ar    NVARCHAR(MAX)                   NULL,
    is_active           BIT                             NOT NULL    DEFAULT 1,
    created_by          BIGINT                          NULL,
    created_at          DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                          NULL,
    updated_at          DATETIME2                       NULL,

    CONSTRAINT PK_notification_templates PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_notification_templates_code UNIQUE (code),

    CONSTRAINT CK_notification_templates_channel CHECK (channel IN (
        N'EMAIL', N'IN_APP'
    ))
);
GO

PRINT 'Tables platform.notifications and platform.notification_templates created successfully.';
GO

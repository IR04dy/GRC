-- ============================================================================
-- TSS GRC Platform - Workflow Definitions & Steps Tables
-- Schema: workflow
-- Purpose: Configurable approval/review workflow templates
-- ============================================================================

USE TSS_GRC;
GO

-- --------------------------------------------------------------------------
-- 1. Workflow Definitions
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.workflow_steps', 'U') IS NOT NULL
    DROP TABLE workflow.workflow_steps;
IF OBJECT_ID('platform.workflow_definitions', 'U') IS NOT NULL
    DROP TABLE workflow.workflow_definitions;
GO

CREATE TABLE workflow.workflow_definitions
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    code            NVARCHAR(100)                   NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    module_code     NVARCHAR(50)                    NULL,
    entity_type     NVARCHAR(100)                   NULL,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    version         INT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_workflow_definitions PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_workflow_definitions_code UNIQUE (code)
);
GO

-- --------------------------------------------------------------------------
-- 2. Workflow Steps
-- --------------------------------------------------------------------------
CREATE TABLE workflow.workflow_steps
(
    id                  BIGINT          IDENTITY(1,1)   NOT NULL,
    workflow_def_id     BIGINT                          NOT NULL,
    step_order          INT                             NOT NULL,
    name_en             NVARCHAR(200)                   NOT NULL,
    name_ar             NVARCHAR(200)                   NULL,
    step_type           NVARCHAR(50)                    NOT NULL,
    assignee_type       NVARCHAR(50)                    NOT NULL,
    assignee_role_id    BIGINT                          NULL,
    assignee_user_id    BIGINT                          NULL,
    sla_hours           INT                             NULL,
    escalation_role_id  BIGINT                          NULL,
    allow_delegation    BIT                             NOT NULL    DEFAULT 1,
    allow_rejection     BIT                             NOT NULL    DEFAULT 1,
    is_parallel         BIT                             NOT NULL    DEFAULT 0,
    created_by          BIGINT                          NULL,
    created_at          DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                          NULL,
    updated_at          DATETIME2                       NULL,

    CONSTRAINT PK_workflow_steps PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_workflow_steps_type CHECK (step_type IN (
        N'APPROVAL', N'REVIEW', N'NOTIFICATION', N'ASSIGNMENT', N'CONDITIONAL'
    )),

    CONSTRAINT CK_workflow_steps_assignee_type CHECK (assignee_type IN (
        N'ROLE', N'USER', N'BU_HEAD'
    )),

    CONSTRAINT FK_workflow_steps_def FOREIGN KEY (workflow_def_id)
        REFERENCES workflow.workflow_definitions (id),

    CONSTRAINT FK_workflow_steps_role FOREIGN KEY (assignee_role_id)
        REFERENCES iam.roles (id),

    CONSTRAINT FK_workflow_steps_user FOREIGN KEY (assignee_user_id)
        REFERENCES iam.users (id),

    CONSTRAINT FK_workflow_steps_escalation_role FOREIGN KEY (escalation_role_id)
        REFERENCES iam.roles (id)
);
GO

PRINT 'Tables workflow.workflow_definitions and workflow.workflow_steps created successfully.';
GO

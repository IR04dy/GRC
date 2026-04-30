-- ============================================================================
-- TSS GRC Platform - Workflow Instances & Instance Steps Tables
-- Schema: workflow
-- Purpose: Runtime workflow execution tracking
-- ============================================================================

USE TSS_GRC;
GO

-- --------------------------------------------------------------------------
-- 1. Workflow Instances
-- --------------------------------------------------------------------------
IF OBJECT_ID('workflow.workflow_instance_steps', 'U') IS NOT NULL
    DROP TABLE workflow.workflow_instance_steps;
IF OBJECT_ID('workflow.workflow_instances', 'U') IS NOT NULL
    DROP TABLE workflow.workflow_instances;
GO

CREATE TABLE workflow.workflow_instances
(
    id                  BIGINT          IDENTITY(1,1)   NOT NULL,
    workflow_def_id     BIGINT                          NOT NULL,
    entity_type         NVARCHAR(100)                   NOT NULL,
    entity_id           BIGINT                          NOT NULL,
    org_id              BIGINT                          NOT NULL,
    current_step_id     BIGINT                          NULL,
    status              NVARCHAR(50)                    NOT NULL    DEFAULT N'DRAFT',
    initiated_by        BIGINT                          NOT NULL,
    initiated_at        DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    completed_at        DATETIME2                       NULL,
    cancelled_at        DATETIME2                       NULL,
    cancelled_by        BIGINT                          NULL,
    due_at              DATETIME2                       NULL,
    created_by          BIGINT                          NULL,
    created_at          DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                          NULL,
    updated_at          DATETIME2                       NULL,

    CONSTRAINT PK_workflow_instances PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_workflow_instances_status CHECK (status IN (
        N'DRAFT', N'IN_PROGRESS', N'COMPLETED', N'CANCELLED', N'ESCALATED'
    )),

    CONSTRAINT FK_wf_instances_def FOREIGN KEY (workflow_def_id)
        REFERENCES workflow.workflow_definitions (id),

    CONSTRAINT FK_wf_instances_org FOREIGN KEY (org_id)
        REFERENCES platform.organizations (id),

    CONSTRAINT FK_wf_instances_current_step FOREIGN KEY (current_step_id)
        REFERENCES workflow.workflow_steps (id),

    CONSTRAINT FK_wf_instances_initiated_by FOREIGN KEY (initiated_by)
        REFERENCES iam.users (id),

    CONSTRAINT FK_wf_instances_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES iam.users (id)
);
GO

-- --------------------------------------------------------------------------
-- 2. Workflow Instance Steps
-- --------------------------------------------------------------------------
CREATE TABLE workflow.workflow_instance_steps
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    instance_id     BIGINT                          NOT NULL,
    step_def_id     BIGINT                          NOT NULL,
    status          NVARCHAR(50)                    NOT NULL    DEFAULT N'PENDING',
    assigned_to     BIGINT                          NULL,
    delegated_to    BIGINT                          NULL,
    started_at      DATETIME2                       NULL,
    completed_at    DATETIME2                       NULL,
    due_at          DATETIME2                       NULL,
    decision        NVARCHAR(50)                    NULL,
    comments        NVARCHAR(MAX)                   NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_workflow_instance_steps PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_wf_instance_steps_status CHECK (status IN (
        N'PENDING', N'IN_PROGRESS', N'COMPLETED', N'SKIPPED', N'ESCALATED'
    )),

    CONSTRAINT CK_wf_instance_steps_decision CHECK (decision IN (
        N'APPROVED', N'REJECTED', N'RETURNED'
    )),

    CONSTRAINT FK_wf_instance_steps_instance FOREIGN KEY (instance_id)
        REFERENCES workflow.workflow_instances (id),

    CONSTRAINT FK_wf_instance_steps_step_def FOREIGN KEY (step_def_id)
        REFERENCES workflow.workflow_steps (id),

    CONSTRAINT FK_wf_instance_steps_assigned_to FOREIGN KEY (assigned_to)
        REFERENCES iam.users (id),

    CONSTRAINT FK_wf_instance_steps_delegated_to FOREIGN KEY (delegated_to)
        REFERENCES iam.users (id)
);
GO

PRINT 'Tables workflow.workflow_instances and workflow.workflow_instance_steps created successfully.';
GO

-- V262 — IR playbook RUN model (epic SOC-B, finding SOC "playbook invoke spawns no work")
--
-- FINDING: POST /playbooks/{id}/invoke only incremented invocation_count and
-- published an F8 event. It created ZERO actionable work — no assigned tasks, no
-- clock, no way to track that the runbook was actually executed step-by-step.
-- A SOC that "invokes the ransomware playbook" got a counter tick, not a to-do list.
--
-- FIX: model an execution. Invoking a playbook now instantiates a RUN
-- (ir_playbook_run) plus one clocked, owner-assigned TASK per step
-- (ir_playbook_run_task). Completing tasks advances the run; when the last task
-- is done the run flips to COMPLETED. The run/task rows snapshot the step content
-- at invocation time so later edits to the template don't rewrite history.
--
-- PURELY ADDITIVE — two new tables + their indexes. Writes NO rows into existing
-- tables. Forward-only + idempotent (guarded by OBJECT_ID / sys.indexes). Dev is
-- Flyway-off; hand-applied via pyodbc.

-- 1) Run header — one row per playbook invocation.
IF OBJECT_ID('socops.ir_playbook_run', 'U') IS NULL
CREATE TABLE socops.ir_playbook_run (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    playbook_id           BIGINT          NOT NULL,
    playbook_code         NVARCHAR(40),                                   -- snapshot for display
    title_en              NVARCHAR(300),                                  -- snapshot of the playbook title at invocation
    title_ar              NVARCHAR(300),
    status                NVARCHAR(30)    NOT NULL DEFAULT 'RUNNING',     -- RUNNING/COMPLETED/CANCELLED
    incident_id           BIGINT,                                         -- optional: run raised in response to a cyber.incidents row
    triggered_by_event_id BIGINT,                                         -- optional: SOC security_event that triggered it
    invoked_by            NVARCHAR(255),                                  -- username who invoked
    started_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    completed_at          DATETIME2,
    total_tasks           INT             NOT NULL DEFAULT 0,
    completed_tasks       INT             NOT NULL DEFAULT 0,
    target_mttr_minutes   INT,                                            -- snapshot of the playbook MTTR target
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT fk_ir_run_playbook FOREIGN KEY (playbook_id) REFERENCES socops.ir_playbook(id)
);
GO

IF OBJECT_ID('socops.ir_playbook_run') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ir_run_org_status' AND object_id = OBJECT_ID('socops.ir_playbook_run'))
    CREATE INDEX ix_ir_run_org_status
        ON socops.ir_playbook_run(organisation_id, status) WHERE is_deleted = 0;
GO

IF OBJECT_ID('socops.ir_playbook_run') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ir_run_org_playbook' AND object_id = OBJECT_ID('socops.ir_playbook_run'))
    CREATE INDEX ix_ir_run_org_playbook
        ON socops.ir_playbook_run(organisation_id, playbook_id, started_at DESC) WHERE is_deleted = 0;
GO

-- 2) Run task — one clocked, owner-assigned row per playbook step.
IF OBJECT_ID('socops.ir_playbook_run_task', 'U') IS NULL
CREATE TABLE socops.ir_playbook_run_task (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    run_id                BIGINT          NOT NULL,
    playbook_id           BIGINT          NOT NULL,
    step_id               BIGINT,                                         -- source step (nullable — survives step deletion)
    step_order            INT             NOT NULL,
    phase                 NVARCHAR(40)    NOT NULL,                       -- DETECT/TRIAGE/CONTAIN/ERADICATE/RECOVER/LESSONS_LEARNED
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    instructions_en       NVARCHAR(MAX),
    instructions_ar       NVARCHAR(MAX),
    owner_role            NVARCHAR(120),                                  -- assigned owner (from step responsible_role)
    status                NVARCHAR(30)    NOT NULL DEFAULT 'PENDING',     -- PENDING/IN_PROGRESS/DONE/SKIPPED
    is_critical           BIT             NOT NULL DEFAULT 0,
    estimated_minutes     INT,
    due_at                DATETIME2,                                      -- the clock: cumulative from run start
    started_at            DATETIME2,
    completed_at          DATETIME2,
    completed_by          NVARCHAR(255),
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT fk_ir_run_task_run FOREIGN KEY (run_id) REFERENCES socops.ir_playbook_run(id)
);
GO

IF OBJECT_ID('socops.ir_playbook_run_task') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ir_run_task_org_run' AND object_id = OBJECT_ID('socops.ir_playbook_run_task'))
    CREATE INDEX ix_ir_run_task_org_run
        ON socops.ir_playbook_run_task(organisation_id, run_id, step_order) WHERE is_deleted = 0;
GO

IF OBJECT_ID('socops.ir_playbook_run_task') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ir_run_task_org_status' AND object_id = OBJECT_ID('socops.ir_playbook_run_task'))
    CREATE INDEX ix_ir_run_task_org_status
        ON socops.ir_playbook_run_task(organisation_id, status, due_at) WHERE is_deleted = 0;
GO

-- V269 — WF-1: platform-level workflow REQUEST engine (WorkflowRequest + recipients + event trail)
--
-- The workflow-logic audit found that Send/Request/Assign actions across modules are generic
-- buttons: no recipient selection, no routing mode, no lifecycle, no SLA, no follow-up. This
-- migration creates the shared request backbone every module can route through:
--
--   workflow.requests            — the request object (§6 of the remediation spec): who asked,
--                                  who must act, routing mode, due/SLA, priority, escalation
--                                  path, full lifecycle status.
--   workflow.request_recipients  — one row per recipient (USER / ROLE / DEPARTMENT / COMMITTEE /
--                                  EXTERNAL), with the actor role (assignee/reviewer/approver/
--                                  observer/escalation), sequential step number, and per-recipient
--                                  response state.
--   workflow.request_events      — append-only audit/timeline: every transition, who did it,
--                                  from→to status, note.
--
-- Forward-only; idempotent. Hand-applied in dev via pyodbc (Flyway is off in dev).

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'workflow' AND t.name = 'requests')
BEGIN
    CREATE TABLE workflow.requests (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        source_module        NVARCHAR(40)  NOT NULL,     -- RISK / BCM / CYBER / ESG / EVIDENCE / PLATFORM ...
        source_record_type   NVARCHAR(60)  NULL,          -- anchor aggregate (CyberControl, Risk, BiaRecord ...)
        source_record_id     BIGINT        NULL,
        request_type         NVARCHAR(40)  NOT NULL,      -- EVIDENCE_REQUEST / APPROVAL / REVIEW / TASK / ...
        title_en             NVARCHAR(300) NOT NULL,
        title_ar             NVARCHAR(300) NULL,
        description          NVARCHAR(MAX) NULL,
        initiated_by         BIGINT        NULL,
        initiated_by_name    NVARCHAR(150) NULL,
        recipient_category   NVARCHAR(30)  NULL,          -- primary category summary (USER/ROLE/DEPARTMENT/...)
        routing_mode         NVARCHAR(20)  NOT NULL CONSTRAINT df_wfr_routing DEFAULT 'PARALLEL',
        completion_rule      NVARCHAR(10)  NOT NULL CONSTRAINT df_wfr_completion DEFAULT 'ALL',
        current_step         INT           NOT NULL CONSTRAINT df_wfr_step DEFAULT 1,
        required_action      NVARCHAR(60)  NULL,
        due_date             DATE          NULL,
        sla_hours            INT           NULL,
        priority             NVARCHAR(20)  NOT NULL CONSTRAINT df_wfr_priority DEFAULT 'MEDIUM',
        severity             NVARCHAR(20)  NULL,
        reminder_days_before INT           NOT NULL CONSTRAINT df_wfr_remdays DEFAULT 3,
        last_reminder_at     DATETIME2     NULL,
        reminder_count       INT           NOT NULL CONSTRAINT df_wfr_remcount DEFAULT 0,
        escalation_role      NVARCHAR(60)  NULL,          -- escalation path: a role ...
        escalation_user_id   BIGINT        NULL,          -- ... and/or a named user
        escalated_at         DATETIME2     NULL,
        status               NVARCHAR(30)  NOT NULL CONSTRAINT df_wfr_status DEFAULT 'DRAFT',
        response_required    BIT           NOT NULL CONSTRAINT df_wfr_resp DEFAULT 1,
        evidence_required    BIT           NOT NULL CONSTRAINT df_wfr_ev DEFAULT 0,
        closure_condition    NVARCHAR(300) NULL,
        reviewer_id          BIGINT        NULL,
        approver_id          BIGINT        NULL,
        closed_at            DATETIME2     NULL,
        reopened_at          DATETIME2     NULL,
        -- BaseEntity columns
        is_deleted           BIT           NOT NULL CONSTRAINT df_wfr_isdel DEFAULT 0,
        deleted_at           DATETIME2     NULL,
        deleted_by           BIGINT        NULL,
        created_at           DATETIME2     NOT NULL CONSTRAINT df_wfr_cat DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2     NULL,
        created_by           BIGINT        NULL,
        updated_by           BIGINT        NULL,
        row_version          ROWVERSION    NOT NULL,
        CONSTRAINT ck_wfr_routing CHECK (routing_mode IN ('SEQUENTIAL','PARALLEL','BROADCAST_ACK')),
        CONSTRAINT ck_wfr_completion CHECK (completion_rule IN ('ALL','ANY')),
        CONSTRAINT ck_wfr_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
        CONSTRAINT ck_wfr_status CHECK (status IN
            ('DRAFT','SENT','ASSIGNED','IN_PROGRESS','WAITING_RESPONSE','SUBMITTED','UNDER_REVIEW',
             'RETURNED','RESUBMITTED','APPROVED','REJECTED','ESCALATED','OVERDUE','CLOSED','REOPENED','ARCHIVED'))
    );
    CREATE INDEX ix_wfr_org_status ON workflow.requests(organisation_id, status, is_deleted);
    CREATE INDEX ix_wfr_source ON workflow.requests(organisation_id, source_record_type, source_record_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'workflow' AND t.name = 'request_recipients')
BEGIN
    CREATE TABLE workflow.request_recipients (
        id               BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id  BIGINT        NOT NULL,
        request_id       BIGINT        NOT NULL,
        recipient_type   NVARCHAR(20)  NOT NULL,      -- USER / ROLE / DEPARTMENT / COMMITTEE / EXTERNAL
        recipient_id     BIGINT        NULL,           -- user / department / committee id
        recipient_key    NVARCHAR(120) NULL,           -- role name or external contact (email)
        recipient_label  NVARCHAR(200) NULL,           -- human label captured at send time
        actor_role       NVARCHAR(30)  NOT NULL CONSTRAINT df_wrr_role DEFAULT 'ASSIGNEE',
        step_no          INT           NOT NULL CONSTRAINT df_wrr_step DEFAULT 1,
        status           NVARCHAR(30)  NOT NULL CONSTRAINT df_wrr_status DEFAULT 'PENDING',
        responded_at     DATETIME2     NULL,
        responded_by     BIGINT        NULL,
        response         NVARCHAR(MAX) NULL,
        -- BaseEntity columns
        is_deleted       BIT           NOT NULL CONSTRAINT df_wrr_isdel DEFAULT 0,
        deleted_at       DATETIME2     NULL,
        deleted_by       BIGINT        NULL,
        created_at       DATETIME2     NOT NULL CONSTRAINT df_wrr_cat DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2     NULL,
        created_by       BIGINT        NULL,
        updated_by       BIGINT        NULL,
        row_version      ROWVERSION    NOT NULL,
        CONSTRAINT fk_wrr_request FOREIGN KEY (request_id) REFERENCES workflow.requests(id),
        CONSTRAINT ck_wrr_type CHECK (recipient_type IN ('USER','ROLE','DEPARTMENT','COMMITTEE','EXTERNAL')),
        CONSTRAINT ck_wrr_role CHECK (actor_role IN ('ASSIGNEE','REVIEWER','APPROVER','OBSERVER','ESCALATION')),
        CONSTRAINT ck_wrr_status CHECK (status IN
            ('PENDING','NOTIFIED','IN_PROGRESS','RESPONDED','APPROVED','REJECTED','SKIPPED'))
    );
    CREATE INDEX ix_wrr_request ON workflow.request_recipients(organisation_id, request_id, is_deleted);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'workflow' AND t.name = 'request_events')
BEGIN
    CREATE TABLE workflow.request_events (
        id               BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id  BIGINT        NOT NULL,
        request_id       BIGINT        NOT NULL,
        event_type       NVARCHAR(40)  NOT NULL,      -- CREATED / SENT / RESPONDED / SUBMITTED / ...
        from_status      NVARCHAR(30)  NULL,
        to_status        NVARCHAR(30)  NULL,
        actor            NVARCHAR(150) NULL,
        actor_user_id    BIGINT        NULL,
        note             NVARCHAR(MAX) NULL,
        created_at       DATETIME2     NOT NULL CONSTRAINT df_wre_cat DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_wre_request FOREIGN KEY (request_id) REFERENCES workflow.requests(id)
    );
    CREATE INDEX ix_wre_request ON workflow.request_events(organisation_id, request_id);
END
GO

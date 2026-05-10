-- ============================================================================
-- TSS GRC — Configurable Workflow Engine (assessment §6.1 — strategic flagship)
-- ============================================================================
-- Generic state-machine engine that lets administrators define workflows for
-- any module without backend code changes. Replaces the hard-coded transitions
-- currently scattered across BCM/SCM/ERM/PDPL/Compliance/Incidents/Evidence
-- service layers.
--
-- Design:
--   workflow_definitions  — top-level workflow (name, target module/entity, version)
--   workflow_states       — states inside a definition (with is_initial / is_terminal flags)
--   workflow_transitions  — transitions between states (action code, required role, condition)
--   workflow_instances    — running instances per target entity
--   workflow_action_log   — audit trail of every executed transition
--
-- Schema lives in 'workflow' (new). Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── Schema ────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'workflow')
    EXEC('CREATE SCHEMA workflow');

-- ── Tables ────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'workflow' AND t.name = 'definitions')
BEGIN
    CREATE TABLE workflow.definitions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_wf_def_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        code               NVARCHAR(100) NOT NULL,
        name_en            NVARCHAR(255) NOT NULL,
        name_ar            NVARCHAR(255) NULL,
        target_module      NVARCHAR(50) NOT NULL,    -- RISK, BCM, SCM, COMPLIANCE, PRIVACY, INCIDENT, EVIDENCE, CYBER
        target_entity      NVARCHAR(100) NOT NULL,   -- e.g. Risk, BcmPlan, ScmVendorOnboarding
        version            NVARCHAR(20) NOT NULL CONSTRAINT DF_wf_def_ver DEFAULT N'1.0',
        description_en     NVARCHAR(MAX) NULL,
        is_active          BIT NOT NULL CONSTRAINT DF_wf_def_active DEFAULT 1,
        is_deleted         BIT NOT NULL CONSTRAINT DF_wf_def_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_wf_def_created DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2 NULL,
        CONSTRAINT UQ_wf_def_org_code UNIQUE (organisation_id, code, version)
    );
    PRINT N'Created workflow.definitions';
END
ELSE PRINT N'workflow.definitions already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'workflow' AND t.name = 'states')
BEGIN
    CREATE TABLE workflow.states (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        definition_id      BIGINT NOT NULL,
        code               NVARCHAR(50) NOT NULL,    -- enum-like, used as the value persisted on target entity status field
        name_en            NVARCHAR(150) NOT NULL,
        name_ar            NVARCHAR(150) NULL,
        is_initial         BIT NOT NULL CONSTRAINT DF_wf_state_initial DEFAULT 0,
        is_terminal        BIT NOT NULL CONSTRAINT DF_wf_state_terminal DEFAULT 0,
        sort_order         INT NOT NULL CONSTRAINT DF_wf_state_sort DEFAULT 0,
        ui_color           NVARCHAR(20) NULL,        -- hex or palette token, e.g. 'success' / 'danger' / '#0E5E5E'
        description_en     NVARCHAR(500) NULL,
        CONSTRAINT FK_wf_state_def FOREIGN KEY (definition_id) REFERENCES workflow.definitions(id),
        CONSTRAINT UQ_wf_state UNIQUE (definition_id, code)
    );
    PRINT N'Created workflow.states';
END
ELSE PRINT N'workflow.states already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'workflow' AND t.name = 'transitions')
BEGIN
    CREATE TABLE workflow.transitions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        definition_id      BIGINT NOT NULL,
        from_state_id      BIGINT NOT NULL,
        to_state_id        BIGINT NOT NULL,
        action_code        NVARCHAR(50) NOT NULL,     -- the API action; URL fragment used by clients (e.g. 'submit-for-review')
        action_name_en     NVARCHAR(150) NOT NULL,
        action_name_ar     NVARCHAR(150) NULL,
        required_role      NVARCHAR(100) NULL,        -- if set, only users with this role can execute
        condition_expr     NVARCHAR(500) NULL,        -- optional SpEL or simple key=value gate; NULL = unconditional
        sla_hours          INT NULL,                  -- if set, transition must occur within this many hours of entering from_state
        sort_order         INT NOT NULL CONSTRAINT DF_wf_tr_sort DEFAULT 0,
        description_en     NVARCHAR(500) NULL,
        CONSTRAINT FK_wf_tr_def  FOREIGN KEY (definition_id) REFERENCES workflow.definitions(id),
        CONSTRAINT FK_wf_tr_from FOREIGN KEY (from_state_id) REFERENCES workflow.states(id),
        CONSTRAINT FK_wf_tr_to   FOREIGN KEY (to_state_id)   REFERENCES workflow.states(id),
        CONSTRAINT UQ_wf_tr UNIQUE (definition_id, from_state_id, action_code)
    );
    PRINT N'Created workflow.transitions';
END
ELSE PRINT N'workflow.transitions already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'workflow' AND t.name = 'instances')
BEGIN
    CREATE TABLE workflow.instances (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_wf_inst_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        definition_id      BIGINT NOT NULL,
        target_module      NVARCHAR(50) NOT NULL,
        target_entity      NVARCHAR(100) NOT NULL,
        target_entity_id   BIGINT NOT NULL,
        current_state_id   BIGINT NOT NULL,
        owner_id           BIGINT NULL,
        started_at         DATETIME2 NOT NULL CONSTRAINT DF_wf_inst_started DEFAULT SYSUTCDATETIME(),
        completed_at       DATETIME2 NULL,
        last_transition_at DATETIME2 NULL,
        CONSTRAINT FK_wf_inst_def FOREIGN KEY (definition_id) REFERENCES workflow.definitions(id),
        CONSTRAINT FK_wf_inst_st  FOREIGN KEY (current_state_id) REFERENCES workflow.states(id)
    );
    CREATE INDEX IX_wf_inst_target ON workflow.instances(target_module, target_entity, target_entity_id);
    PRINT N'Created workflow.instances';
END
ELSE PRINT N'workflow.instances already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'workflow' AND t.name = 'action_log')
BEGIN
    CREATE TABLE workflow.action_log (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        instance_id        BIGINT NOT NULL,
        transition_id      BIGINT NULL,            -- nullable so manual overrides can be logged
        action_code        NVARCHAR(50) NOT NULL,
        from_state_id      BIGINT NULL,
        to_state_id        BIGINT NOT NULL,
        executed_by_id     BIGINT NULL,
        executed_at        DATETIME2 NOT NULL CONSTRAINT DF_wf_log_at DEFAULT SYSUTCDATETIME(),
        comments           NVARCHAR(MAX) NULL,
        CONSTRAINT FK_wf_log_inst FOREIGN KEY (instance_id) REFERENCES workflow.instances(id)
    );
    CREATE INDEX IX_wf_log_inst ON workflow.action_log(instance_id);
    PRINT N'Created workflow.action_log';
END
ELSE PRINT N'workflow.action_log already exists';

-- ── Seed: reference workflow — Risk lifecycle (mirrors RiskService hard-coded transitions) ──
DECLARE @org BIGINT = 1;
DECLARE @def_id BIGINT;

SELECT @def_id = id FROM workflow.definitions
WHERE organisation_id = @org AND code = 'RISK_LIFECYCLE_V1' AND is_deleted = 0;

IF @def_id IS NULL
BEGIN
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (@org, 'RISK_LIFECYCLE_V1',
            N'ISO 31000 Risk Lifecycle (reference)',
            N'دورة حياة المخاطر — ISO 31000 (مرجعي)',
            'RISK', 'Risk', '1.0',
            N'Reference workflow mirroring the hard-coded RiskService transitions. Demonstrates the engine; real Risk records still use the legacy service-layer logic until migration lands as a follow-up.',
            1);
    SET @def_id = SCOPE_IDENTITY();

    -- States
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color, description_en) VALUES
      (@def_id, 'OPEN',          N'Open',                N'مفتوحة',             1, 0, 1, 'info',     N'Risk identified, not yet under formal review.'),
      (@def_id, 'UNDER_REVIEW',  N'Under Review',        N'قيد المراجعة',       0, 0, 2, 'primary',  N'CRO / risk owner reviewing assessment.'),
      (@def_id, 'MITIGATING',    N'Mitigating',          N'قيد المعالجة',       0, 0, 3, 'accent',   N'Treatment plan approved; controls being implemented.'),
      (@def_id, 'ACCEPTED',      N'Accepted',            N'مقبولة',             0, 0, 4, 'warning',  N'Formal management acceptance of residual risk.'),
      (@def_id, 'CLOSED',        N'Closed',              N'مغلقة',              0, 1, 5, 'success',  N'Risk eliminated or terminated.');

    -- Helper to fetch state ids
    DECLARE @s_open BIGINT = (SELECT id FROM workflow.states WHERE definition_id=@def_id AND code='OPEN');
    DECLARE @s_review BIGINT = (SELECT id FROM workflow.states WHERE definition_id=@def_id AND code='UNDER_REVIEW');
    DECLARE @s_mitig BIGINT = (SELECT id FROM workflow.states WHERE definition_id=@def_id AND code='MITIGATING');
    DECLARE @s_accept BIGINT = (SELECT id FROM workflow.states WHERE definition_id=@def_id AND code='ACCEPTED');
    DECLARE @s_closed BIGINT = (SELECT id FROM workflow.states WHERE definition_id=@def_id AND code='CLOSED');

    -- Transitions
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, sort_order, description_en) VALUES
      (@def_id, @s_open,   @s_review, 'submit-for-review', N'Submit for Review',     N'إرسال للمراجعة',     1, N'Risk owner submits the assessment.'),
      (@def_id, @s_review, @s_mitig,  'approve',           N'Approve for Treatment', N'اعتماد للمعالجة',    2, N'CRO approves the treatment plan.'),
      (@def_id, @s_open,   @s_accept, 'accept',            N'Formally Accept',       N'قبول رسمي',          3, N'Senior management accepts the risk without further treatment.'),
      (@def_id, @s_review, @s_accept, 'accept',            N'Formally Accept',       N'قبول رسمي',          4, N'Senior management accepts the risk during review.'),
      (@def_id, @s_mitig,  @s_closed, 'close',             N'Close',                 N'إغلاق',              5, N'Treatment complete; risk eliminated.'),
      (@def_id, @s_accept, @s_closed, 'close',             N'Close',                 N'إغلاق',              6, N'Risk lifecycle complete.'),
      (@def_id, @s_closed, @s_open,   'reopen',            N'Reopen',                N'إعادة الفتح',        7, N'Recurrence — restart the lifecycle.');

    PRINT N'Seeded reference workflow: RISK_LIFECYCLE_V1 with 5 states + 7 transitions';
END
ELSE
    PRINT N'RISK_LIFECYCLE_V1 already seeded';

-- ── Result summary ───────────────────────────────────────────────────────
SELECT
    N'Workflow engine DDL complete' AS status,
    (SELECT COUNT(*) FROM workflow.definitions WHERE is_deleted=0) AS definitions,
    (SELECT COUNT(*) FROM workflow.states)                          AS states,
    (SELECT COUNT(*) FROM workflow.transitions)                     AS transitions;

GO

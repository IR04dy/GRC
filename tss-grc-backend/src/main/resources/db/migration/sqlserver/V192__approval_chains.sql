-- V192 — Sprint 13: Multi-step Workflow Approval Chains.
--
-- Every wizard submission today has a single "approved/rejected"
-- outcome from a single approver. Real Saudi-regulated banks need
-- configurable approval ladders: a CAPA closure passes Risk Owner →
-- Department Head → DPO → CISO; a cross-border data-transfer passes
-- DPO → Legal → Compliance Officer → Board. This slice introduces a
-- generic approval-chain engine with template + step + instance +
-- action tables, decoupled from any single wizard schema.
--
-- Four tables:
--
--   workflow.approval_chain_template — the ladder blueprint
--   workflow.approval_chain_step     — one ladder rung, ordered
--   workflow.approval_chain_instance — a live in-flight ladder
--   workflow.approval_chain_action   — every actor decision on a rung
--
-- Why four tables? Because the template is long-lived and slowly
-- changing (the bank rewrites the CAPA ladder once a year and only
-- after a Compliance Committee paper), but every wizard submission
-- materialises an instance plus a per-rung action stream. Mirrors the
-- shape of Sprint 11's obligation register (template/cycle/attestation).
--
-- Regulatory anchors:
--   - SAMA Corporate Governance §5.3 — documented multi-step
--     authorisation for material decisions, with named officer per step.
--   - NCA ECC-2 control 1-3-2 — "documented chain of approvals for
--     cybersecurity exceptions".
--   - PDPL Article 32 — DPO sign-off chain before any cross-border
--     personal-data transfer.
--   - SOX-equivalent ICFR — material-change CAB ladder (CAB Lead →
--     CISO + DPO parallel → Change Manager).
--
-- Instance state machine (enforced in ApprovalChainInstanceService):
--   PENDING     → IN_PROGRESS | WITHDRAWN | EXPIRED
--   IN_PROGRESS → APPROVED | REJECTED | WITHDRAWN | EXPIRED
--   APPROVED   (terminal)
--   REJECTED   (terminal)
--   WITHDRAWN  (terminal)
--   EXPIRED    (terminal)
--
-- Action types (one row per actor decision per step):
--   APPROVED              — actor approved the step
--   REJECTED              — actor rejected; instance becomes REJECTED
--   DELEGATED             — actor handed to another user (audit trail)
--   REQUESTED_CHANGES     — soft-block; originator must resubmit
--   RECUSED               — actor declined for COI; another must act
--
-- Step types:
--   SERIAL    — must complete before next step
--   PARALLEL  — multiple step rows with same step_order; all mandatory
--               ones must complete before advancing
--
-- Pre-seeded: 3 templates for org=1 (TSS Bank) covering CAPA closure,
-- cross-border data transfer, and material change with a parallel
-- rung. All bilingual EN+AR with Saudi-native phrasing.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('workflow') IS NULL EXEC('CREATE SCHEMA workflow');
GO

-- ─── Approval chain template (the ladder blueprint) ───────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'approval_chain_template' AND schema_id = SCHEMA_ID('workflow'))
BEGIN
    CREATE TABLE workflow.approval_chain_template (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        template_code                   NVARCHAR(64)    NOT NULL,  -- e.g. CHAIN-CAPA-CLOSURE
        title_en                        NVARCHAR(500)   NOT NULL,
        title_ar                        NVARCHAR(500)   NOT NULL,
        description_en                  NVARCHAR(MAX)   NULL,
        description_ar                  NVARCHAR(MAX)   NULL,

        -- Comma-separated wizard codes this template gates
        -- (e.g. "CAPA_CLOSURE,RISK_ACCEPTANCE"). Empty / NULL means
        -- the template only matches by module scoping below.
        applies_to_workflow_codes       NVARCHAR(MAX)   NULL,

        -- Alternative scoping — useful when a template covers every
        -- wizard in a module. NULL means the workflow_codes column is
        -- authoritative.
        applies_to_module               VARCHAR(40)     NULL,

        -- Per-org-and-scope default — used when a wizard submission
        -- doesn't pick a specific template. Service guards uniqueness.
        is_default                      BIT             NOT NULL DEFAULT 0,

        -- Standard audit columns + soft-delete + rowversion
        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION
    );

    -- Unique template_code per tenant (active rows only).
    CREATE UNIQUE INDEX UX_chain_template_org_code
        ON workflow.approval_chain_template(organisation_id, template_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_template_org_module
        ON workflow.approval_chain_template(organisation_id, applies_to_module)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_template_org_default
        ON workflow.approval_chain_template(organisation_id, is_default)
        WHERE is_deleted = 0;
END
GO

-- ─── Approval chain step (one rung) ────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'approval_chain_step' AND schema_id = SCHEMA_ID('workflow'))
BEGIN
    CREATE TABLE workflow.approval_chain_step (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        template_id                     BIGINT          NOT NULL,

        step_order                      INT             NOT NULL,  -- 1, 2, 3...
        -- SERIAL    — one row at this step_order, must complete first
        -- PARALLEL  — multiple rows with same step_order, all mandatory
        --             rows complete before advancing
        step_type                       VARCHAR(20)     NOT NULL DEFAULT 'SERIAL',

        title_en                        NVARCHAR(500)   NOT NULL,
        title_ar                        NVARCHAR(500)   NOT NULL,

        -- Flexible matching — exactly one of these three SHOULD be set
        -- for a meaningful step. Service validates at template-write
        -- time. The required_role_in_chain column is the "second-leg"
        -- delegation slot (when the originator names a specific role
        -- inside the chain payload, e.g. the data steward owning a
        -- particular dataset).
        required_role                   VARCHAR(40)     NULL,
        required_user_id                BIGINT          NULL,
        required_role_in_chain          VARCHAR(40)     NULL,

        is_mandatory                    BIT             NOT NULL DEFAULT 1,
        can_delegate                    BIT             NOT NULL DEFAULT 1,

        -- Escalation threshold — if the step has not been actioned
        -- within sla_hours of becoming current, the scheduler nudges
        -- the escalation_role.
        sla_hours                       INT             NULL,
        escalation_role                 VARCHAR(40)     NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_chain_step_template
            FOREIGN KEY (template_id) REFERENCES workflow.approval_chain_template(id)
    );

    CREATE INDEX IX_chain_step_template_order
        ON workflow.approval_chain_step(template_id, step_order)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_step_org_template
        ON workflow.approval_chain_step(organisation_id, template_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Approval chain instance (a live in-flight ladder) ─────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'approval_chain_instance' AND schema_id = SCHEMA_ID('workflow'))
BEGIN
    CREATE TABLE workflow.approval_chain_instance (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        template_id                     BIGINT          NOT NULL,

        -- Source-of-truth pointer. Either the chain gates a
        -- WorkflowSubmission row, OR it gates an arbitrary entity
        -- (CAPA, vendor exception, policy approval...). Service
        -- requires at least one identifier set.
        source_workflow_submission_id   BIGINT          NULL,
        source_entity_type              VARCHAR(40)     NULL,
        source_entity_id                BIGINT          NULL,

        -- PENDING | IN_PROGRESS | APPROVED | REJECTED | WITHDRAWN | EXPIRED
        status                          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',

        started_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        completed_at                    DATETIME2       NULL,
        current_step_order              INT             NOT NULL DEFAULT 1,

        originator_user_id              BIGINT          NOT NULL,
        title_en                        NVARCHAR(500)   NULL,
        title_ar                        NVARCHAR(500)   NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_chain_instance_template
            FOREIGN KEY (template_id) REFERENCES workflow.approval_chain_template(id)
    );

    CREATE INDEX IX_chain_instance_org_status
        ON workflow.approval_chain_instance(organisation_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_instance_org_originator
        ON workflow.approval_chain_instance(organisation_id, originator_user_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_instance_source_submission
        ON workflow.approval_chain_instance(organisation_id, source_workflow_submission_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_instance_source_entity
        ON workflow.approval_chain_instance(organisation_id, source_entity_type, source_entity_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Approval chain action (one actor decision per rung) ───────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'approval_chain_action' AND schema_id = SCHEMA_ID('workflow'))
BEGIN
    CREATE TABLE workflow.approval_chain_action (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        instance_id                     BIGINT          NOT NULL,
        step_order                      INT             NOT NULL,
        step_id                         BIGINT          NOT NULL,

        actor_user_id                   BIGINT          NOT NULL,

        -- APPROVED | REJECTED | DELEGATED | REQUESTED_CHANGES | RECUSED
        action                          VARCHAR(20)     NOT NULL,

        decided_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        comment_en                      NVARCHAR(MAX)   NULL,
        comment_ar                      NVARCHAR(MAX)   NULL,

        -- When action=DELEGATED, the user receiving the delegation.
        delegated_to_user_id            BIGINT          NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_chain_action_instance
            FOREIGN KEY (instance_id) REFERENCES workflow.approval_chain_instance(id),
        CONSTRAINT FK_chain_action_step
            FOREIGN KEY (step_id) REFERENCES workflow.approval_chain_step(id)
    );

    CREATE INDEX IX_chain_action_instance_step
        ON workflow.approval_chain_action(instance_id, step_order)
        WHERE is_deleted = 0;

    CREATE INDEX IX_chain_action_org_actor
        ON workflow.approval_chain_action(organisation_id, actor_user_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed 3 templates for org=1 (TSS Bank) ─────────────────────
--
-- Idempotent — checks template_code uniqueness before inserting.

-- (1) CHAIN-CAPA-CLOSURE — Risk Owner → Dept Head → DPO → CISO (serial)
IF NOT EXISTS (SELECT 1 FROM workflow.approval_chain_template
               WHERE organisation_id = 1 AND template_code = 'CHAIN-CAPA-CLOSURE')
BEGIN
    INSERT INTO workflow.approval_chain_template (
        organisation_id, template_code, title_en, title_ar,
        description_en, description_ar,
        applies_to_workflow_codes, applies_to_module, is_default
    ) VALUES (
        1, 'CHAIN-CAPA-CLOSURE',
        'CAPA Closure — Risk Owner → Department Head → DPO → CISO',
        N'إغلاق الإجراءات التصحيحية والوقائية — مالك المخاطرة ← رئيس الإدارة ← مسؤول حماية البيانات ← رئيس أمن المعلومات',
        'Four-step serial ladder enforced before any CAPA can be moved to CLOSED status. Mandated by SAMA CSF §3.5 (CAPA governance) and NCA ECC-2 control 2-13.',
        N'سلسلة اعتماد تسلسلية من أربع خطوات قبل إغلاق أي إجراء تصحيحي. تستند إلى المادة §3.5 من إطار الأمن السيبراني لمؤسسة النقد وضابط 2-13 من الضوابط الأساسية للأمن السيبراني للهيئة الوطنية للأمن السيبراني.',
        'CAPA_CLOSURE,FINDING_CLOSURE', 'COMPLIANCE', 1
    );

    DECLARE @capa_id BIGINT = (SELECT id FROM workflow.approval_chain_template
                               WHERE organisation_id = 1 AND template_code = 'CHAIN-CAPA-CLOSURE');

    INSERT INTO workflow.approval_chain_step
        (organisation_id, template_id, step_order, step_type, title_en, title_ar,
         required_role, is_mandatory, can_delegate, sla_hours, escalation_role)
    VALUES
        (1, @capa_id, 1, 'SERIAL',
         'Risk Owner sign-off',
         N'اعتماد مالك المخاطرة',
         'RISK_OWNER', 1, 1, 48, 'RISK_MANAGER'),
        (1, @capa_id, 2, 'SERIAL',
         'Department Head review',
         N'مراجعة رئيس الإدارة',
         'DEPARTMENT_HEAD', 1, 1, 48, 'PLATFORM_ADMIN'),
        (1, @capa_id, 3, 'SERIAL',
         'DPO attestation',
         N'إقرار مسؤول حماية البيانات',
         'DPO', 1, 0, 72, 'COMPLIANCE_OFFICER'),
        (1, @capa_id, 4, 'SERIAL',
         'CISO final approval',
         N'الاعتماد النهائي لرئيس أمن المعلومات',
         'CISO', 1, 0, 72, 'PLATFORM_ADMIN');
END
GO

-- (2) CHAIN-CROSS-BORDER-XFER — DPO → Legal → Compliance Officer → Board (serial)
IF NOT EXISTS (SELECT 1 FROM workflow.approval_chain_template
               WHERE organisation_id = 1 AND template_code = 'CHAIN-CROSS-BORDER-XFER')
BEGIN
    INSERT INTO workflow.approval_chain_template (
        organisation_id, template_code, title_en, title_ar,
        description_en, description_ar,
        applies_to_workflow_codes, applies_to_module, is_default
    ) VALUES (
        1, 'CHAIN-CROSS-BORDER-XFER',
        'Cross-border Personal-Data Transfer — DPO → Legal → Compliance → Board',
        N'نقل البيانات الشخصية عبر الحدود — مسؤول حماية البيانات ← الإدارة القانونية ← مسؤول الالتزام ← المجلس',
        'Mandatory ladder for every cross-border transfer of personal data per PDPL Article 32 and its Implementing Regulations Article 24. Board signature is required for adequacy-decision transfers exceeding 10,000 records.',
        N'سلسلة الاعتماد الإلزامية لأي نقل للبيانات الشخصية إلى خارج المملكة وفق المادة 32 من نظام حماية البيانات الشخصية والمادة 24 من اللائحة التنفيذية. توقيع المجلس مطلوب للنقل القائم على قرار كفاية الحماية الذي يتجاوز 10,000 سجل.',
        'CROSS_BORDER_TRANSFER,DPIA_APPROVAL', 'PRIVACY', 1
    );

    DECLARE @cbx_id BIGINT = (SELECT id FROM workflow.approval_chain_template
                              WHERE organisation_id = 1 AND template_code = 'CHAIN-CROSS-BORDER-XFER');

    INSERT INTO workflow.approval_chain_step
        (organisation_id, template_id, step_order, step_type, title_en, title_ar,
         required_role, is_mandatory, can_delegate, sla_hours, escalation_role)
    VALUES
        (1, @cbx_id, 1, 'SERIAL',
         'DPO adequacy assessment',
         N'تقييم كفاية الحماية من مسؤول حماية البيانات',
         'DPO', 1, 0, 72, 'COMPLIANCE_OFFICER'),
        (1, @cbx_id, 2, 'SERIAL',
         'Legal — contract review',
         N'مراجعة العقد من الإدارة القانونية',
         'LEGAL_COUNSEL', 1, 1, 72, 'COMPLIANCE_OFFICER'),
        (1, @cbx_id, 3, 'SERIAL',
         'Compliance Officer attestation',
         N'إقرار مسؤول الالتزام',
         'COMPLIANCE_OFFICER', 1, 1, 48, 'PLATFORM_ADMIN'),
        (1, @cbx_id, 4, 'SERIAL',
         'Board approval (≥10k records, adequacy basis)',
         N'موافقة المجلس (للنقل بأكثر من 10,000 سجل القائم على قرار كفاية الحماية)',
         'BOARD_CHAIR', 1, 0, 168, 'PLATFORM_ADMIN');
END
GO

-- (3) CHAIN-MATERIAL-CHANGE — Originator → CAB Lead → CISO + DPO (parallel) → Change Mgr
-- 3 conceptual steps but step_order=3 has TWO parallel rows (CISO + DPO).
IF NOT EXISTS (SELECT 1 FROM workflow.approval_chain_template
               WHERE organisation_id = 1 AND template_code = 'CHAIN-MATERIAL-CHANGE')
BEGIN
    INSERT INTO workflow.approval_chain_template (
        organisation_id, template_code, title_en, title_ar,
        description_en, description_ar,
        applies_to_workflow_codes, applies_to_module, is_default
    ) VALUES (
        1, 'CHAIN-MATERIAL-CHANGE',
        'Material Change — Originator → CAB Lead → CISO + DPO (parallel) → Change Manager',
        N'تغيير جوهري — صاحب الطلب ← قائد لجنة استشارية للتغيير ← رئيس أمن المعلومات + مسؤول حماية البيانات (بالتوازي) ← مدير التغيير',
        'CAB ladder for material changes. Steps 3a and 3b run in parallel — both CISO and DPO must approve before the Change Manager can release. Anchored on ITIL Change Enablement and SAMA CSF §4.3 (Change Management governance).',
        N'سلسلة لجنة استشارية للتغيير المخصّصة للتغييرات الجوهرية. الخطوتان 3أ و 3ب تجريان بالتوازي — يجب اعتماد رئيس أمن المعلومات ومسؤول حماية البيانات قبل أن يصدر مدير التغيير الموافقة. وفق إطار ITIL لتمكين التغيير والمادة §4.3 من إطار الأمن السيبراني لمؤسسة النقد.',
        'CHANGE_REQUEST,CAB_REVIEW', 'CHANGE', 1
    );

    DECLARE @cab_id BIGINT = (SELECT id FROM workflow.approval_chain_template
                              WHERE organisation_id = 1 AND template_code = 'CHAIN-MATERIAL-CHANGE');

    -- step_order=1 (CAB Lead), step_order=2 (CISO + DPO parallel),
    -- step_order=3 (Change Manager). The originator is implicit (the
    -- person who started the chain) and not represented as a step.
    INSERT INTO workflow.approval_chain_step
        (organisation_id, template_id, step_order, step_type, title_en, title_ar,
         required_role, is_mandatory, can_delegate, sla_hours, escalation_role)
    VALUES
        (1, @cab_id, 1, 'SERIAL',
         'CAB Lead triage',
         N'فرز قائد لجنة استشارية للتغيير',
         'CAB_LEAD', 1, 1, 24, 'PLATFORM_ADMIN'),
        (1, @cab_id, 2, 'PARALLEL',
         'CISO security review',
         N'المراجعة الأمنية من رئيس أمن المعلومات',
         'CISO', 1, 0, 48, 'PLATFORM_ADMIN'),
        (1, @cab_id, 2, 'PARALLEL',
         'DPO privacy review',
         N'مراجعة الخصوصية من مسؤول حماية البيانات',
         'DPO', 1, 0, 48, 'COMPLIANCE_OFFICER'),
        (1, @cab_id, 3, 'SERIAL',
         'Change Manager release',
         N'إصدار مدير التغيير',
         'CHANGE_MANAGER', 1, 1, 24, 'PLATFORM_ADMIN');
END
GO

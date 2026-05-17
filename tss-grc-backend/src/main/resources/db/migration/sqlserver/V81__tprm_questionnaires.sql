-- V81 — TPRM Questionnaire Engine
--
-- Three new tables under the existing `tprm` schema. Replaces the
-- free-form vendor_assessments.risk_score with a deterministic
-- weighted-question rubric so two assessors of the same vendor land on
-- the same number.
--
--   tprm.questionnaire_templates   reusable question bundles per category
--                                  (CYBER / PRIVACY / FINANCIAL /
--                                  OPERATIONAL / COMPLIANCE / GENERAL).
--                                  Lifecycle DRAFT → ACTIVE → ARCHIVED.
--
--   tprm.questionnaire_questions   versioned questions inside a template.
--                                  Answer types: SCALE_1_5, BOOLEAN,
--                                  SELECT, TEXT. Each question carries a
--                                  weight that drives the rollup.
--
--   tprm.questionnaire_responses   per-vendor-assessment answers. Each
--                                  row scores between 0 and max_points
--                                  (depends on answer_type). The service
--                                  rolls them up into
--                                  vendor_assessments.risk_score.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- ─── Templates ────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'questionnaire_templates' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.questionnaire_templates (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        template_code       NVARCHAR(40)  NOT NULL,
        version             NVARCHAR(20)  NOT NULL DEFAULT N'1.0',

        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        category            NVARCHAR(40)  NOT NULL DEFAULT N'GENERAL',
        applicable_to       NVARCHAR(MAX) NULL,   -- CSV of vendor_type strings, NULL = all

        -- Risk-band thresholds applied to the final 0..100 score.
        threshold_low_max       INT       NOT NULL DEFAULT 30,    -- <= → LOW
        threshold_medium_max    INT       NOT NULL DEFAULT 60,    -- <= → MEDIUM
        threshold_high_max      INT       NOT NULL DEFAULT 80,    -- <= → HIGH, above → CRITICAL

        status              NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        activated_at        DATETIME2     NULL,
        archived_at         DATETIME2     NULL,

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_qt_status   CHECK (status IN (N'DRAFT', N'ACTIVE', N'ARCHIVED')),
        CONSTRAINT ck_qt_category CHECK (category IN
            (N'CYBER', N'PRIVACY', N'FINANCIAL', N'OPERATIONAL', N'COMPLIANCE', N'GENERAL')),
        CONSTRAINT ck_qt_thresh   CHECK (threshold_low_max < threshold_medium_max
                                     AND threshold_medium_max < threshold_high_max
                                     AND threshold_high_max <= 100)
    );

    CREATE UNIQUE INDEX ux_qt_org_code_version
        ON tprm.questionnaire_templates(organisation_id, template_code, version) WHERE is_deleted = 0;
    CREATE INDEX ix_qt_status ON tprm.questionnaire_templates(organisation_id, status);
END
GO

-- ─── Questions ────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'questionnaire_questions' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.questionnaire_questions (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        template_id         BIGINT        NOT NULL,

        section_code        NVARCHAR(40)  NOT NULL,
        section_name_en     NVARCHAR(255) NULL,
        section_name_ar     NVARCHAR(255) NULL,

        question_code       NVARCHAR(40)  NOT NULL,
        question_en         NVARCHAR(MAX) NOT NULL,
        question_ar         NVARCHAR(MAX) NULL,

        -- SCALE_1_5     answer 1..5; max_points = 5
        -- BOOLEAN       answer YES/NO; YES = 1 (or "negative" boolean if invert_score), NO = 0
        -- SELECT        answer matches one of options_json keys; max_points = max value in options
        -- TEXT          free-text, scored manually; max_points = 5 if reviewer provides scored_points
        answer_type         NVARCHAR(20)  NOT NULL DEFAULT N'SCALE_1_5',
        options_json        NVARCHAR(MAX) NULL,    -- JSON map { "value": points }
        weight              DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
        invert_score        BIT           NOT NULL DEFAULT 0,    -- when true, lower answer = better
        mandatory           BIT           NOT NULL DEFAULT 1,

        sequence_order      INT           NOT NULL DEFAULT 100,
        guidance_en         NVARCHAR(MAX) NULL,
        guidance_ar         NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2     NULL,

        CONSTRAINT ck_qq_type CHECK (answer_type IN
            (N'SCALE_1_5', N'BOOLEAN', N'SELECT', N'TEXT'))
    );

    CREATE INDEX ix_qq_template ON tprm.questionnaire_questions(template_id);
    CREATE UNIQUE INDEX ux_qq_template_code
        ON tprm.questionnaire_questions(template_id, question_code) WHERE is_deleted = 0;
END
GO

-- ─── Responses ────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'questionnaire_responses' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.questionnaire_responses (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,

        vendor_assessment_id BIGINT       NOT NULL,
        question_id         BIGINT        NOT NULL,
        template_id         BIGINT        NOT NULL,       -- denormalised for fast filters

        answer_value        NVARCHAR(500) NULL,
        scored_points       DECIMAL(5,2)  NULL,            -- service-set on save
        max_points          DECIMAL(5,2)  NOT NULL DEFAULT 5.00,
        weighted_points     DECIMAL(8,2)  NULL,            -- scored_points × weight (service-set)

        answer_notes_en     NVARCHAR(MAX) NULL,
        answer_notes_ar     NVARCHAR(MAX) NULL,
        evidence_ref        NVARCHAR(255) NULL,
        not_applicable      BIT           NOT NULL DEFAULT 0,
        na_justification    NVARCHAR(MAX) NULL,

        answered_by         NVARCHAR(120) NULL,
        answered_at         DATETIME2     NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2     NULL
    );

    CREATE INDEX ix_qr_assessment ON tprm.questionnaire_responses(vendor_assessment_id);
    CREATE INDEX ix_qr_question   ON tprm.questionnaire_responses(question_id);
    CREATE UNIQUE INDEX ux_qr_assessment_question
        ON tprm.questionnaire_responses(vendor_assessment_id, question_id) WHERE is_deleted = 0;
END
GO

-- Add a workflow status to vendor_assessments so the questionnaire flow has
-- somewhere to live: NOT_STARTED → IN_PROGRESS → COMPLETED → REVIEWED.
-- The free-form `status` column has been a workflow-style field for a while
-- but the values were never constrained; introduce a parallel
-- `workflow_status` instead of disturbing existing rows.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'tprm.vendor_assessments')
      AND name = N'workflow_status')
BEGIN
    ALTER TABLE tprm.vendor_assessments
        ADD workflow_status    NVARCHAR(20) NOT NULL CONSTRAINT df_va_wf DEFAULT N'NOT_STARTED',
            template_id        BIGINT       NULL,
            answered_count     INT          NOT NULL CONSTRAINT df_va_ac DEFAULT 0,
            total_questions    INT          NOT NULL CONSTRAINT df_va_tq DEFAULT 0,
            computed_score_pct DECIMAL(5,2) NULL;
END
GO

-- ─── Seed — 1 template + 8 questions ──────────────────────────────

IF NOT EXISTS (SELECT 1 FROM tprm.questionnaire_templates WHERE organisation_id = 1)
BEGIN
    INSERT INTO tprm.questionnaire_templates (
        organisation_id, template_code, version,
        name_en, name_ar, description_en, description_ar,
        category, applicable_to, status, activated_at
    ) VALUES (
        1, N'QT-CYBER-DD', N'1.0',
        N'Critical Vendor Cyber Due Diligence',
        N'العناية الواجبة السيبراني للموردين الحرجين',
        N'Pre-onboarding due-diligence questionnaire for vendors handling production data or critical infrastructure. Aligned with NCA ECC v3 + SAMA CSF.',
        N'استبيان العناية الواجبة قبل الإدراج للموردين الذين يتعاملون مع بيانات الإنتاج أو البنية التحتية الحرجة. مُواءَم مع NCA ECC v3 وإطار ساما السيبراني.',
        N'CYBER', N'CRITICAL,HIGH', N'ACTIVE', SYSUTCDATETIME()
    );

    DECLARE @t BIGINT = SCOPE_IDENTITY();

    INSERT INTO tprm.questionnaire_questions (
        organisation_id, template_id, section_code, section_name_en, section_name_ar,
        question_code, question_en, question_ar,
        answer_type, weight, mandatory, sequence_order, guidance_en
    ) VALUES
    -- Section A — Information Security
    (1, @t, N'A', N'Information Security', N'أمن المعلومات',
     N'A-01', N'Is the vendor ISO 27001 certified or equivalent?', N'هل المورد حاصل على شهادة ISO 27001 أو ما يعادلها؟',
     N'BOOLEAN', 2.00, 1, 10, N'Accept ISO 27001, SOC 2 Type II, or equivalent national framework.'),
    (1, @t, N'A', N'Information Security', N'أمن المعلومات',
     N'A-02', N'How mature is the vendor''s vulnerability management programme?', N'ما مدى نضج برنامج إدارة الثغرات لدى المورد؟',
     N'SCALE_1_5', 1.50, 1, 20, N'1 = ad-hoc, 5 = continuous scanning + SLA-tracked remediation.'),
    (1, @t, N'A', N'Information Security', N'أمن المعلومات',
     N'A-03', N'Does the vendor enforce MFA on all privileged accounts?', N'هل يفرض المورد المصادقة متعدّدة العوامل على جميع الحسابات المُميَّزة؟',
     N'BOOLEAN', 2.00, 1, 30, NULL),

    -- Section B — Privacy
    (1, @t, N'B', N'Privacy', N'الخصوصية',
     N'B-01', N'Is the vendor PDPL-aligned (Saudi Personal Data Protection Law)?', N'هل المورد متوافق مع نظام حماية البيانات الشخصية السعودي؟',
     N'BOOLEAN', 2.00, 1, 40, N'Verify DPO appointed, ROPA maintained, breach process documented.'),
    (1, @t, N'B', N'Privacy', N'الخصوصية',
     N'B-02', N'How mature is the vendor''s data-breach notification capability?', N'ما مدى نضج قدرة المورد على الإبلاغ عن انتهاكات البيانات؟',
     N'SCALE_1_5', 1.00, 1, 50, N'1 = ad-hoc, 5 = tested playbook + automated 72h regulator notification.'),

    -- Section C — Operational resilience
    (1, @t, N'C', N'Operational Resilience', N'المرونة التشغيلية',
     N'C-01', N'Does the vendor publish RTO and RPO commitments for the contracted service?', N'هل ينشر المورد التزامات RTO و RPO للخدمة المُتعاقَد عليها؟',
     N'BOOLEAN', 1.50, 1, 60, NULL),
    (1, @t, N'C', N'Operational Resilience', N'المرونة التشغيلية',
     N'C-02', N'When was the vendor''s last successful DR exercise?', N'متى كان آخر تمرين تعافي ناجح للمورد؟',
     N'SCALE_1_5', 1.00, 1, 70, N'1 = never / > 3 years, 5 = within last 6 months with documented results.'),
    (1, @t, N'C', N'Operational Resilience', N'المرونة التشغيلية',
     N'C-03', N'Is the vendor''s service hosted in the Kingdom of Saudi Arabia?', N'هل خدمة المورد مُستضافة في المملكة العربية السعودية؟',
     N'BOOLEAN', 1.00, 0, 80, N'Soft check — informational only; not a disqualifier on its own.');
END
GO

-- V195 — Sprint 13: Whistleblower / Ethics Hotline.
--
-- Saudi banks are required by SAMA Corporate Governance Regulations
-- Article 28 + CMA Listed-Companies CG Code Article 87 to operate a
-- confidential whistleblower channel where employees and third
-- parties can report ethical violations, fraud, conflicts of
-- interest, or compliance breaches without fear of retaliation.
-- NCA ECC-2 control 5-1-3 adds the anonymous-reporting capability
-- for cybersecurity violations.
--
-- Today most banks use a generic outsourced hotline service. This
-- migration gives the bank an in-platform module the Ethics
-- Committee owns, with the audit trail external services don't
-- expose.
--
-- Three tables under the existing `ethics` schema (the `cases` table
-- already in the schema is a separate generic ethics-case register —
-- this slice adds the dedicated whistleblower workflow with public-
-- portal submission + reporter-side status tracking):
--
--   ethics.whistleblower_case                  — one row per report
--   ethics.whistleblower_case_update           — timeline + reporter messages
--   ethics.whistleblower_retaliation_complaint — protection sub-flow
--
-- Key design decisions:
--
--   1. submission_token (UNIQUEIDENTIFIER) is the *only* identifier
--      the public submitter receives. They use it to check status
--      anonymously — we don't store IP, we don't recover lost tokens,
--      we don't tie it to any user account. Mirrors the BCP attestation
--      token pattern (Slice 10-D).
--
--   2. is_visible_to_reporter on every case_update row controls
--      what shows on the public /status/{token} page. Investigation
--      notes default to NOT visible; closure summaries + status
--      transitions default to visible. The Ethics Officer can flip
--      either way per-update.
--
--   3. involves_senior_executive is a hard escalation flag — when
--      set, the F8 trigger WHISTLEBLOWER_SENIOR_EXEC_FLAGGED fires
--      on submit and triage so the Ethics Committee Chair + the
--      Internal Auditor are notified out-of-band.
--
--   4. The retaliation_complaint table is a separate sub-flow because
--      a retaliation claim is itself a protected report and is owned
--      by HR / an independent investigator, not the original case's
--      investigator. The two are linked via related_case_id.
--
--   5. linked_capa_task_id closes the loop with the existing CAPA
--      module — a substantiated case spawns a corrective action.
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- Filtered indexes — QUOTED_IDENTIFIER ON required.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'ethics') IS NULL EXEC(N'CREATE SCHEMA ethics');
GO

-- ─── 1. Whistleblower case ─────────────────────────────────────────
IF OBJECT_ID(N'ethics.whistleblower_case', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.whistleblower_case (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,

        -- Human-readable code, e.g. WB-2026-001. Unique per tenant
        -- within live rows.
        case_code                     NVARCHAR(64)  NOT NULL,

        -- Opaque token the public submitter retains. The ONLY
        -- identifier they get. Cannot be recovered if lost.
        submission_token              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        submitted_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        -- Channel the report came through:
        --   PUBLIC_PORTAL    — external /submit page (anonymous-friendly)
        --   INTERNAL_PORTAL  — authenticated employee submission
        --   EMAIL            — manual capture from ethics@<bank>
        --   PHONE            — captured from hotline call
        --   IN_PERSON        — captured from face-to-face meeting
        --   EXTERNAL_PARTNER — referred by external counsel / regulator
        submission_channel            VARCHAR(20)   NOT NULL DEFAULT N'PUBLIC_PORTAL',

        -- Who the reporter is (NOT who the report is about):
        --   EMPLOYEE | MANAGER | CONTRACTOR | VENDOR | CUSTOMER |
        --   EXTERNAL | ANONYMOUS
        reporter_type                 VARCHAR(20)   NOT NULL DEFAULT N'ANONYMOUS',

        -- Reporter contact fields — all NULL when is_anonymous = 1.
        reporter_name                 NVARCHAR(255) NULL,
        reporter_email                NVARCHAR(255) NULL,
        reporter_phone                NVARCHAR(50)  NULL,
        reporter_employee_id          NVARCHAR(64)  NULL,

        -- Default anonymous; the submission form lets the reporter
        -- opt in to identification.
        is_anonymous                  BIT           NOT NULL DEFAULT 1,

        -- Category — drives routing + reporting:
        --   FRAUD | CORRUPTION | CONFLICT_OF_INTEREST | HARASSMENT |
        --   DISCRIMINATION | SAFETY | ENVIRONMENT | PRIVACY_VIOLATION |
        --   FINANCIAL_MISCONDUCT | IT_SECURITY |
        --   REGULATORY_NON_COMPLIANCE | OTHER
        category                      VARCHAR(40)   NOT NULL,

        -- Severity — initial triage; can be revised by Ethics Officer.
        --   CRITICAL | HIGH | MEDIUM | LOW
        severity                      VARCHAR(20)   NOT NULL DEFAULT N'MEDIUM',

        -- Bilingual subject + free-form narrative.
        subject_en                    NVARCHAR(255) NOT NULL,
        subject_ar                    NVARCHAR(255) NOT NULL,
        narrative_en                  NVARCHAR(MAX) NOT NULL,
        narrative_ar                  NVARCHAR(MAX) NOT NULL,

        -- Names / departments of the people the report names.
        parties_involved              NVARCHAR(1000) NULL,
        alleged_period_start          DATE          NULL,
        alleged_period_end            DATE          NULL,

        -- Hard escalation flag — when set, the F8 trigger
        -- WHISTLEBLOWER_SENIOR_EXEC_FLAGGED fires so the Ethics
        -- Committee Chair + Internal Auditor are notified out-of-
        -- band, bypassing the normal manager-of-the-accused chain.
        involves_senior_executive     BIT           NOT NULL DEFAULT 0,

        -- Case lifecycle:
        --   RECEIVED            — submitted, not yet picked up
        --   TRIAGE              — Ethics Officer reviewing
        --   UNDER_INVESTIGATION — investigator assigned, fact-finding underway
        --   SUBSTANTIATED       — investigation found the allegation true
        --   UNSUBSTANTIATED     — investigation found no merit
        --   REFERRED            — handed to regulator / law enforcement
        --   CLOSED_NO_ACTION    — terminal: no action warranted
        --   CLOSED_REMEDIATED   — terminal: CAPA spawned + closed
        status                        VARCHAR(20)   NOT NULL DEFAULT N'RECEIVED',

        -- Triage outcome — recorded when status moves out of TRIAGE.
        triage_by_user_id             BIGINT        NULL,
        triage_at                     DATETIME2(3)  NULL,
        triage_decision_en            NVARCHAR(1000) NULL,
        triage_decision_ar            NVARCHAR(1000) NULL,

        -- Investigation assignment.
        assigned_investigator_user_id BIGINT        NULL,
        investigation_started_at      DATETIME2(3)  NULL,

        -- Closure.
        closed_at                     DATETIME2(3)  NULL,
        closure_summary_en            NVARCHAR(2000) NULL,
        closure_summary_ar            NVARCHAR(2000) NULL,

        -- Link to CAPA module's corrective-action task (when one is
        -- spawned). Soft FK — the CAPA module may not be loaded.
        linked_capa_task_id           BIGINT        NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_wb_channel CHECK (submission_channel IN
            (N'PUBLIC_PORTAL', N'INTERNAL_PORTAL', N'EMAIL', N'PHONE',
             N'IN_PERSON', N'EXTERNAL_PARTNER')),
        CONSTRAINT CK_wb_reporter_type CHECK (reporter_type IN
            (N'EMPLOYEE', N'MANAGER', N'CONTRACTOR', N'VENDOR',
             N'CUSTOMER', N'EXTERNAL', N'ANONYMOUS')),
        CONSTRAINT CK_wb_category CHECK (category IN
            (N'FRAUD', N'CORRUPTION', N'CONFLICT_OF_INTEREST',
             N'HARASSMENT', N'DISCRIMINATION', N'SAFETY', N'ENVIRONMENT',
             N'PRIVACY_VIOLATION', N'FINANCIAL_MISCONDUCT', N'IT_SECURITY',
             N'REGULATORY_NON_COMPLIANCE', N'OTHER')),
        CONSTRAINT CK_wb_severity CHECK (severity IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_wb_status CHECK (status IN
            (N'RECEIVED', N'TRIAGE', N'UNDER_INVESTIGATION',
             N'SUBSTANTIATED', N'UNSUBSTANTIATED', N'REFERRED',
             N'CLOSED_NO_ACTION', N'CLOSED_REMEDIATED'))
    );
END
GO

-- Filtered indexes.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_wb_case_code'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case'))
    CREATE UNIQUE INDEX UQ_wb_case_code ON ethics.whistleblower_case
        (organisation_id, case_code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_wb_submission_token'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case'))
    CREATE UNIQUE INDEX UQ_wb_submission_token ON ethics.whistleblower_case
        (submission_token) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wb_status'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case'))
    CREATE INDEX IX_wb_status ON ethics.whistleblower_case
        (organisation_id, status, severity) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wb_category'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case'))
    CREATE INDEX IX_wb_category ON ethics.whistleblower_case
        (organisation_id, category) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wb_senior_exec'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case'))
    CREATE INDEX IX_wb_senior_exec ON ethics.whistleblower_case
        (organisation_id, involves_senior_executive)
        WHERE is_deleted = 0 AND involves_senior_executive = 1;
GO

-- ─── 2. Whistleblower case update (timeline) ────────────────────────
IF OBJECT_ID(N'ethics.whistleblower_case_update', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.whistleblower_case_update (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,

        case_id                       BIGINT NOT NULL,

        -- Update type:
        --   TRIAGE_DECISION       — Ethics Officer recorded triage outcome
        --   INVESTIGATOR_ASSIGNED — investigator picked
        --   FINDING_RECORDED      — investigator added a finding
        --   STATUS_CHANGED        — status transition
        --   REPORTER_REPLY        — submitter responded via /status/{token}
        --   INVESTIGATION_NOTE    — internal investigator note
        --   CLOSURE               — case closed
        update_type                   VARCHAR(40)   NOT NULL,

        -- NULL when the reporter posted via the public token path.
        actor_user_id                 BIGINT        NULL,

        -- Controls what the reporter sees on /status/{token}. Investigation
        -- notes default to NOT visible; status transitions + closure
        -- default to visible.
        is_visible_to_reporter        BIT           NOT NULL DEFAULT 0,

        body_en                       NVARCHAR(MAX) NOT NULL,
        body_ar                       NVARCHAR(MAX) NOT NULL,

        -- Pipe-delimited list of attachment refs (file paths / DMS ids).
        attachment_refs               NVARCHAR(500) NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_wbu_case FOREIGN KEY (case_id)
            REFERENCES ethics.whistleblower_case(id),
        CONSTRAINT CK_wbu_type CHECK (update_type IN
            (N'TRIAGE_DECISION', N'INVESTIGATOR_ASSIGNED',
             N'FINDING_RECORDED', N'STATUS_CHANGED', N'REPORTER_REPLY',
             N'INVESTIGATION_NOTE', N'CLOSURE'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wbu_case'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_case_update'))
    CREATE INDEX IX_wbu_case ON ethics.whistleblower_case_update
        (organisation_id, case_id, created_at DESC) WHERE is_deleted = 0;
GO

-- ─── 3. Retaliation complaint (sub-flow) ────────────────────────────
IF OBJECT_ID(N'ethics.whistleblower_retaliation_complaint', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.whistleblower_retaliation_complaint (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,

        -- The original whistleblower case this retaliation claim
        -- relates to.
        related_case_id               BIGINT NOT NULL,

        -- Either the user-id (if identified) or the name (if free-form).
        claimant_user_id              BIGINT        NULL,
        claimant_name                 NVARCHAR(255) NULL,

        claimed_actor                 NVARCHAR(255) NOT NULL,
        claimed_action                NVARCHAR(MAX) NOT NULL,
        claimed_at_period_start       DATE          NULL,

        -- Claim lifecycle:
        --   RECEIVED  — submitted
        --   VALIDATED — independent review confirmed the claim is bona-fide
        --   UPHELD    — terminal: retaliation confirmed; disciplinary action triggered
        --   DISMISSED — terminal: claim could not be substantiated
        status                        VARCHAR(20)   NOT NULL DEFAULT N'RECEIVED',

        -- Independent protector (typically HR Director or external
        -- investigator) — must NOT be in the chain that supervises
        -- the original case's investigator.
        assigned_protector_user_id    BIGINT        NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_wbr_case FOREIGN KEY (related_case_id)
            REFERENCES ethics.whistleblower_case(id),
        CONSTRAINT CK_wbr_status CHECK (status IN
            (N'RECEIVED', N'VALIDATED', N'UPHELD', N'DISMISSED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wbr_case'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_retaliation_complaint'))
    CREATE INDEX IX_wbr_case ON ethics.whistleblower_retaliation_complaint
        (organisation_id, related_case_id) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wbr_status'
               AND object_id = OBJECT_ID(N'ethics.whistleblower_retaliation_complaint'))
    CREATE INDEX IX_wbr_status ON ethics.whistleblower_retaliation_complaint
        (organisation_id, status) WHERE is_deleted = 0;
GO

-- ─── 4. Seed 3 demo whistleblower cases for org=1 (TSS Bank) ────────
-- Saudi-native bilingual EN/AR. Flagged DEMO ONLY — clearly for
-- training / dev-environment use, not real reports.

-- WB-2026-001 — ANONYMOUS / FRAUD / HIGH — suspected expense fraud
IF NOT EXISTS (SELECT 1 FROM ethics.whistleblower_case
               WHERE organisation_id = 1 AND case_code = N'WB-2026-001' AND is_deleted = 0)
INSERT INTO ethics.whistleblower_case
    (organisation_id, case_code, submission_channel, reporter_type,
     is_anonymous, category, severity,
     subject_en, subject_ar,
     narrative_en, narrative_ar,
     parties_involved, alleged_period_start, alleged_period_end,
     involves_senior_executive, status, submitted_at)
VALUES
    (1, N'WB-2026-001', N'PUBLIC_PORTAL', N'ANONYMOUS',
     1, N'FRAUD', N'HIGH',
     N'[DEMO] Suspected expense-claim fraud in Western Region sales team',
     N'[تجريبي] اشتباه في احتيال على نفقات فريق المبيعات بالمنطقة الغربية',
     N'[DEMO ROW — FOR TRAINING ONLY] Over the past quarter several members of the Western Region sales team appear to have been submitting duplicated hotel and entertainment expense claims for client visits that did not occur. The pattern suggests at least two of the claims were copy-pasted from a previous quarter''s reimbursement. The line manager seems to be approving without scrutiny. Estimated exposure is between SAR 80,000 and SAR 150,000 for the quarter. Please review the expense submissions for cost-centre WR-SALES between 1 January and 31 March 2026.',
     N'[سجل تجريبي — لأغراض التدريب فقط] خلال الربع الماضي تبيّن أن بعض أعضاء فريق المبيعات بالمنطقة الغربية يقدّمون مطالبات نفقات فندقية وضيافة مكرّرة لزيارات عملاء لم تتم فعلاً. ويظهر النمط أن مطالبتين على الأقل منسوختان حرفياً من تعويضات الربع السابق. ويبدو أن المدير المباشر يعتمدها دون تدقيق. تقدير حجم المبلغ المعرّض للخطر بين 80,000 و150,000 ريال سعودي للربع. يُرجى مراجعة مطالبات النفقات لمركز التكلفة WR-SALES من 1 يناير حتى 31 مارس 2026.',
     N'Western Region sales team (3 named in attachment); RW-Sales line manager',
     '2026-01-01', '2026-03-31',
     0, N'TRIAGE',
     DATEADD(day, -3, SYSUTCDATETIME()));
GO

-- WB-2026-002 — EMPLOYEE / HARASSMENT / MEDIUM — workplace harassment, identified
IF NOT EXISTS (SELECT 1 FROM ethics.whistleblower_case
               WHERE organisation_id = 1 AND case_code = N'WB-2026-002' AND is_deleted = 0)
INSERT INTO ethics.whistleblower_case
    (organisation_id, case_code, submission_channel, reporter_type,
     reporter_name, reporter_email, reporter_employee_id,
     is_anonymous, category, severity,
     subject_en, subject_ar,
     narrative_en, narrative_ar,
     parties_involved, alleged_period_start, alleged_period_end,
     involves_senior_executive, status,
     triage_at, triage_decision_en, triage_decision_ar,
     assigned_investigator_user_id, investigation_started_at,
     submitted_at)
VALUES
    (1, N'WB-2026-002', N'INTERNAL_PORTAL', N'EMPLOYEE',
     N'Sara Al-Otaibi', N'sara.alotaibi@demo.tssbank.sa', N'EMP-04421',
     0, N'HARASSMENT', N'MEDIUM',
     N'[DEMO] Repeated inappropriate comments from desk supervisor — Operations Department',
     N'[تجريبي] تعليقات غير لائقة متكرّرة من مشرف القسم — إدارة العمليات',
     N'[DEMO ROW — FOR TRAINING ONLY] My desk supervisor in the Operations Department has on multiple occasions made comments about my appearance and personal life in the presence of colleagues during the morning standup. I have asked him twice to stop in private and he has not stopped. The most recent incident was in front of three other team members on the 12th of this month. I would like the matter looked at without my line being aware until the Ethics Officer has decided how to handle it.',
     N'[سجل تجريبي — لأغراض التدريب فقط] أبدى مشرفي المباشر في إدارة العمليات في عدة مناسبات تعليقات بشأن مظهري وحياتي الشخصية أمام الزملاء أثناء الاجتماع الصباحي. وقد طلبت منه مرتين على انفراد التوقف عن ذلك ولم يتوقف. وكانت آخر حادثة بحضور ثلاثة من أعضاء الفريق في الثاني عشر من هذا الشهر. أرغب في النظر في الأمر دون إبلاغ مديري المباشر حتى يقرّر مسؤول الأخلاقيات كيفية التعامل معه.',
     N'Operations Department desk supervisor (named in private comms)',
     '2026-04-12', '2026-05-12',
     0, N'UNDER_INVESTIGATION',
     DATEADD(day, -5, SYSUTCDATETIME()),
     N'Allegation substantively raises an HR matter — proceed with formal investigation. Move out of the reporter''s reporting line for the duration.',
     N'البلاغ يطرح أمراً يخصّ الموارد البشرية — يُمضى به إلى التحقيق الرسمي. ينقَل الموظف خارج خط الإبلاغ المباشر لمدة التحقيق.',
     NULL,
     DATEADD(day, -4, SYSUTCDATETIME()),
     DATEADD(day, -7, SYSUTCDATETIME()));
GO

-- WB-2026-003 — VENDOR / CONFLICT_OF_INTEREST / CRITICAL — senior-exec involvement
IF NOT EXISTS (SELECT 1 FROM ethics.whistleblower_case
               WHERE organisation_id = 1 AND case_code = N'WB-2026-003' AND is_deleted = 0)
INSERT INTO ethics.whistleblower_case
    (organisation_id, case_code, submission_channel, reporter_type,
     reporter_email, is_anonymous, category, severity,
     subject_en, subject_ar,
     narrative_en, narrative_ar,
     parties_involved, alleged_period_start, alleged_period_end,
     involves_senior_executive, status, submitted_at)
VALUES
    (1, N'WB-2026-003', N'EXTERNAL_PARTNER', N'VENDOR',
     N'contact@demo-supplier.sa', 0, N'CONFLICT_OF_INTEREST', N'CRITICAL',
     N'[DEMO] Procurement-cycle conflict — senior executive''s relative awarded contract',
     N'[تجريبي] تضارب مصالح في دورة المشتريات — ترسية عقد على قريب لمسؤول تنفيذي',
     N'[DEMO ROW — FOR TRAINING ONLY] We were one of three pre-qualified suppliers for the data-centre cooling-system contract awarded in March 2026. After the award we discovered through public corporate records that the winning bidder is a company in which the brother-in-law of the bank''s Chief Procurement Officer holds a 35% stake — this was not declared during the tender. The pricing of the winning bid was approximately 18% above our quoted price for a substantively similar specification. We are raising this as a conflict-of-interest matter under the bank''s vendor code of conduct.',
     N'[سجل تجريبي — لأغراض التدريب فقط] كنّا أحد ثلاثة موردين مؤهّلين مسبقاً لعقد منظومة تبريد مركز البيانات الذي رُسّي في مارس 2026. واكتشفنا بعد الترسية، من خلال السجلات الرسمية للشركات، أن الجهة الفائزة شركة يملك فيها قريبٌ لرئيس قطاع المشتريات بالبنك نسبة 35٪، ولم يُفصَح عن ذلك أثناء المناقصة. وكان سعر العرض الفائز أعلى بنحو 18٪ من سعرنا المقدّم لنفس المواصفات تقريباً. نرفع هذا الأمر بوصفه تضارب مصالح وفق ميثاق سلوك الموردين الخاص بالبنك.',
     N'Chief Procurement Officer; awarded vendor (named in attachment); CPO''s brother-in-law (public ownership records)',
     '2026-02-15', '2026-03-31',
     1, N'TRIAGE',
     DATEADD(day, -1, SYSUTCDATETIME()));
GO

-- ─── 5. Seed initial timeline updates for the demo cases ─────────────

-- WB-2026-002 — triage decision update (visible to reporter)
IF NOT EXISTS (SELECT 1 FROM ethics.whistleblower_case_update wbu
               JOIN ethics.whistleblower_case wbc ON wbc.id = wbu.case_id
               WHERE wbc.organisation_id = 1 AND wbc.case_code = N'WB-2026-002'
                 AND wbu.update_type = N'TRIAGE_DECISION' AND wbu.is_deleted = 0)
INSERT INTO ethics.whistleblower_case_update
    (organisation_id, case_id, update_type, is_visible_to_reporter,
     body_en, body_ar)
SELECT 1, c.id, N'TRIAGE_DECISION', 1,
       N'Your report has been reviewed by the Ethics Officer and accepted for formal investigation. An investigator from HR Audit will reach out via your registered email. You will not be required to face your supervisor during the investigation.',
       N'تمّت مراجعة بلاغك من قِبل مسؤول الأخلاقيات وقُبل للتحقيق الرسمي. وسيتواصل معك المحقّق من مراجعة الموارد البشرية عبر بريدك المسجّل، ولن يُطلب منك مواجهة المشرف خلال التحقيق.'
FROM ethics.whistleblower_case c
WHERE c.organisation_id = 1 AND c.case_code = N'WB-2026-002' AND c.is_deleted = 0;
GO

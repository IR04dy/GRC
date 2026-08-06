-- V40 — Committee workflows (Saudi GRC §3.1 + gap analysis item 6).
--
-- Adds dedicated committee + meeting entities so we have a structured
-- record of governance-body proceedings rather than overloading the
-- generic ApprovalRequest. Two tables:
--
--   admin.committees          standing governance bodies (charter,
--                              chair, secretary, members, cadence)
--   admin.committee_meetings  per-meeting instance with agenda,
--                              attendees, minutes, decisions, action
--                              items, and an approve-the-minutes flow
--
-- Lifecycle (committee):       ACTIVE → INACTIVE → DISSOLVED
-- Lifecycle (meeting):         SCHEDULED → IN_PROGRESS → COMPLETED
--                              → MINUTES_APPROVED  (or CANCELLED)

SET QUOTED_IDENTIFIER ON;
GO

-- ─── 1. committees ──────────────────────────────────────────────────
IF OBJECT_ID(N'admin.committees', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committees (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id     BIGINT NOT NULL,

        code                NVARCHAR(64)  NOT NULL,    -- COM-RISK, COM-AUDIT, COM-BCM
        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        -- BOARD | EXEC_COMMITTEE | RISK_COMMITTEE | AUDIT_COMMITTEE
        -- BCM_COMMITTEE | COMPLIANCE_COMMITTEE | TECH_COMMITTEE | OTHER
        committee_type      NVARCHAR(30)  NOT NULL,

        chairperson_name    NVARCHAR(255) NULL,
        chairperson_role    NVARCHAR(100) NULL,
        secretary_name      NVARCHAR(255) NULL,

        -- [{name, role, votingMember:bool, organisation?}, ...]
        members_json        NVARCHAR(MAX) NULL,

        charter_en          NVARCHAR(MAX) NULL,
        charter_ar          NVARCHAR(MAX) NULL,

        meeting_cadence     NVARCHAR(20)  NOT NULL DEFAULT N'MONTHLY',
                                            -- WEEKLY | BIWEEKLY | MONTHLY | QUARTERLY | AD_HOC
        quorum_pct          INT           NULL,   -- 0-100

        status              NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',
                                            -- ACTIVE | INACTIVE | DISSOLVED
        dissolved_at        DATETIME2(3)  NULL,
        dissolved_reason    NVARCHAR(500) NULL,

        external_id         NVARCHAR(64)  NULL,
        is_deleted          SMALLINT      NOT NULL DEFAULT 0,
        is_active           SMALLINT      NOT NULL DEFAULT 1,
        deleted_at          DATETIME2(3)  NULL,
        deleted_by          BIGINT        NULL,
        created_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT        NULL,
        updated_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT        NULL,
        row_version         BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_com_type CHECK (committee_type IN
            -- F2-77 (2026-08-06): V40 listed 8 of these 22 and no migration ever extended it,
            -- so a migration-built database rejected 14 committee types the platform validates
            -- and uses. The live CHECK and CommitteeTypes.VALUES already agree on all 22; only
            -- this file was behind, which is why V159 could not seed AI_ETHICS/CYBER_SECURITY/
            -- PRIVACY_DPO. Sourced from CommitteeTypes.VOCABULARY and gated by
            -- CommitteeTypeVocabularyContractTest so the two cannot drift again.
            (N'BOARD', N'EXEC_COMMITTEE', N'RISK_COMMITTEE', N'AUDIT_COMMITTEE',
             N'BCM_COMMITTEE', N'COMPLIANCE_COMMITTEE', N'TECH_COMMITTEE', N'NOMINATION_REMUNERATION',
             N'CYBER_SECURITY', N'DATA_GOVERNANCE', N'PRIVACY_DPO', N'CRISIS_MANAGEMENT',
             N'AML', N'IT_STEERING', N'ALCO', N'INVESTMENT',
             N'AI_ETHICS', N'ANTI_FRAUD', N'INTERNAL_CONTROL', N'ESG_SUSTAINABILITY',
             N'ARCH_REVIEW', N'OTHER')),
        CONSTRAINT CK_com_cadence CHECK (meeting_cadence IN
            (N'WEEKLY', N'BIWEEKLY', N'MONTHLY', N'QUARTERLY', N'AD_HOC')),
        CONSTRAINT CK_com_status CHECK (status IN
            (N'ACTIVE', N'INACTIVE', N'DISSOLVED')),
        CONSTRAINT UQ_com_code UNIQUE (organisation_id, code)
    );
END
GO

-- ─── 2. committee_meetings ──────────────────────────────────────────
IF OBJECT_ID(N'admin.committee_meetings', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_meetings (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id     BIGINT NOT NULL,

        committee_id        BIGINT        NOT NULL,
        committee_code      NVARCHAR(64)  NULL,        -- denormalised
        committee_name_en   NVARCHAR(255) NULL,

        meeting_code        NVARCHAR(64)  NOT NULL,    -- MTG-2026-Q1-RISK-001
        title_en            NVARCHAR(255) NULL,
        title_ar            NVARCHAR(255) NULL,

        scheduled_at        DATETIME2(3)  NOT NULL,
        started_at          DATETIME2(3)  NULL,
        ended_at            DATETIME2(3)  NULL,
        location_or_link    NVARCHAR(500) NULL,

        -- [{seq, topicEn, topicAr, presenter, minutesAllocated}, ...]
        agenda_json         NVARCHAR(MAX) NULL,

        -- [{name, role, present, votingMember}, ...]
        attendees_json      NVARCHAR(MAX) NULL,
        quorum_met          SMALLINT      NULL,        -- 0/1

        minutes_en          NVARCHAR(MAX) NULL,
        minutes_ar          NVARCHAR(MAX) NULL,

        -- [{seq, decisionEn, decisionAr, voteFor, voteAgainst, voteAbstain, outcome}]
        decisions_json      NVARCHAR(MAX) NULL,

        -- [{seq, actionEn, owner, dueDate, status, linkedRecord}]
        action_items_json   NVARCHAR(MAX) NULL,

        chair_name          NVARCHAR(255) NULL,
        secretary_name      NVARCHAR(255) NULL,

        -- SCHEDULED | IN_PROGRESS | COMPLETED | MINUTES_APPROVED | CANCELLED
        status              NVARCHAR(20)  NOT NULL DEFAULT N'SCHEDULED',
        cancel_reason       NVARCHAR(500) NULL,

        minutes_approved_at DATETIME2(3)  NULL,
        minutes_approved_by BIGINT        NULL,
        approver_name       NVARCHAR(255) NULL,

        external_id         NVARCHAR(64)  NULL,
        is_deleted          SMALLINT      NOT NULL DEFAULT 0,
        is_active           SMALLINT      NOT NULL DEFAULT 1,
        deleted_at          DATETIME2(3)  NULL,
        deleted_by          BIGINT        NULL,
        created_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT        NULL,
        updated_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT        NULL,
        row_version         BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_mtg_status CHECK (status IN
            (N'SCHEDULED', N'IN_PROGRESS', N'COMPLETED',
             N'MINUTES_APPROVED', N'CANCELLED')),
        CONSTRAINT UQ_mtg_code UNIQUE (organisation_id, meeting_code)
    );
END
GO

IF OBJECT_ID(N'admin.committee_meetings', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_mtg_committee' AND object_id = OBJECT_ID(N'admin.committee_meetings'))
BEGIN
    CREATE INDEX IX_mtg_committee ON admin.committee_meetings (organisation_id, committee_id, scheduled_at DESC)
        WHERE is_deleted = 0;
END
GO

-- ═══ Seed: 4 committees + 8 meetings spanning the lifecycle ═════════
IF OBJECT_ID(N'admin.committees', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM admin.committees WHERE code = N'COM-BOARD')
BEGIN
    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status,
         dissolved_at, dissolved_reason, external_id)
    VALUES
        (1, N'COM-BOARD',
         N'Board of Directors', N'مجلس الإدارة',
         N'Highest governance body — sets strategy, appoints CEO, approves annual plan and budget, oversees risk appetite.',
         N'أعلى هيئة حوكمة - يحدد الاستراتيجية، يعيّن الرئيس التنفيذي، يعتمد الخطة والميزانية السنوية، يشرف على شهية المخاطر.',
         N'BOARD',
         N'Abdullah Al-Saud', N'Chairman',
         N'Khalid Al-Mutairi (Company Secretary)',
         N'[{"name":"Abdullah Al-Saud","role":"Chairman","votingMember":true},{"name":"Layla Al-Otaibi","role":"Vice-Chair","votingMember":true},{"name":"Mohammed Al-Ghamdi","role":"Independent Director","votingMember":true},{"name":"Nasser Al-Dosari","role":"Independent Director","votingMember":true},{"name":"Sara Al-Qahtani","role":"Independent Director","votingMember":true},{"name":"CEO","role":"Executive Director","votingMember":true}]',
         N'Provides strategic leadership, approves the annual plan / budget / dividend, sets risk appetite, hires + fires CEO, oversees executive committee. Meets quarterly with one strategy off-site per year.',
         N'يوفر القيادة الاستراتيجية، يعتمد الخطة السنوية والميزانية والأرباح، يحدد شهية المخاطر، يعيّن ويُقيل الرئيس التنفيذي، يشرف على اللجنة التنفيذية. يجتمع ربع سنوياً مع خلوة استراتيجية سنوية.',
         N'QUARTERLY', 75, N'ACTIVE',
         NULL, NULL, N'COM-BOARD'),

        (1, N'COM-RISK',
         N'Risk Management Committee', N'لجنة إدارة المخاطر',
         N'Reviews the risk register monthly, approves risk acceptances above appetite, reviews KRI breaches, sponsors scenario-analysis runs.',
         N'تراجع سجل المخاطر شهرياً، تعتمد قبول المخاطر فوق الشهية، تراجع تجاوزات مؤشرات المخاطر، ترعى محاكاة السيناريوهات.',
         N'RISK_COMMITTEE',
         N'CRO (Chief Risk Officer)', N'CRO',
         N'Risk Manager',
         N'[{"name":"CRO","role":"Chair","votingMember":true},{"name":"CFO","role":"Member","votingMember":true},{"name":"COO","role":"Member","votingMember":true},{"name":"CISO","role":"Member","votingMember":true},{"name":"General Counsel","role":"Member","votingMember":true},{"name":"DPO","role":"Standing Invitee","votingMember":false},{"name":"Head of Internal Audit","role":"Standing Invitee","votingMember":false}]',
         N'Monthly review of risk register; approve risk acceptances above amber appetite; recommend risk appetite changes to Board; oversee scenario analysis programme.',
         N'مراجعة شهرية لسجل المخاطر؛ اعتماد قبول المخاطر فوق الشهية الكهرمانية؛ التوصية بتغييرات الشهية للمجلس؛ الإشراف على برنامج تحليل السيناريوهات.',
         N'MONTHLY', 67, N'ACTIVE',
         NULL, NULL, N'COM-RISK'),

        (1, N'COM-AUDIT',
         N'Audit Committee', N'لجنة المراجعة',
         N'Oversees internal audit programme, external auditor relationship, financial reporting integrity, NCA ECC / SAMA cyber attestations.',
         N'تشرف على برنامج المراجعة الداخلية، العلاقة مع المراجع الخارجي، نزاهة التقارير المالية، اعتمادات NCA ECC وضوابط ساما السيبرانية.',
         N'AUDIT_COMMITTEE',
         N'Mohammed Al-Ghamdi', N'Independent Director',
         N'Head of Internal Audit',
         N'[{"name":"Mohammed Al-Ghamdi","role":"Chair (Independent)","votingMember":true},{"name":"Nasser Al-Dosari","role":"Member (Independent)","votingMember":true},{"name":"Sara Al-Qahtani","role":"Member (Independent)","votingMember":true},{"name":"Head of Internal Audit","role":"Standing Invitee","votingMember":false},{"name":"External Auditor","role":"Standing Invitee","votingMember":false}]',
         N'Quarterly review of internal audit plan + findings; pre-approves external auditor scope/fees; reviews annual financial statements; sponsors NCA ECC + SAMA cyber attestation evidence packages.',
         N'مراجعة ربع سنوية لخطة المراجعة الداخلية والملاحظات؛ اعتماد مسبق لنطاق وأتعاب المراجع الخارجي؛ مراجعة القوائم المالية السنوية؛ رعاية حزم أدلة اعتماد NCA ECC و SAMA.',
         N'QUARTERLY', 67, N'ACTIVE',
         NULL, NULL, N'COM-AUDIT'),

        (1, N'COM-BCM',
         N'BCM Steering Committee', N'لجنة توجيه استمرارية الأعمال',
         N'Sponsors the BCM programme, signs off BCPs and BIA outputs, approves DRP test results, activates crisis response.',
         N'ترعى برنامج استمرارية الأعمال، تعتمد خطط الاستمرارية ومخرجات BIA، تعتمد نتائج اختبارات DRP، تفعّل الاستجابة للأزمات.',
         N'BCM_COMMITTEE',
         N'COO', N'COO',
         N'BCM Officer',
         N'[{"name":"COO","role":"Chair","votingMember":true},{"name":"CIO","role":"Member","votingMember":true},{"name":"CISO","role":"Member","votingMember":true},{"name":"CFO","role":"Member","votingMember":true},{"name":"BCM Officer","role":"Secretary","votingMember":false},{"name":"Facilities Director","role":"Member","votingMember":true}]',
         N'Quarterly review of BIAs + BCPs; approves DRP test outcomes; calls crisis activations; oversees BCM exercises.',
         N'مراجعة ربع سنوية لتحليلات الأثر وخطط الاستمرارية؛ اعتماد نتائج اختبارات DRP؛ استدعاء تفعيلات الأزمات؛ الإشراف على تمارين BCM.',
         N'QUARTERLY', 67, N'ACTIVE',
         NULL, NULL, N'COM-BCM'),

        (1, N'COM-OLD-TECH',
         N'Technology Steering Committee (legacy)',
         N'لجنة توجيه التقنية (قديمة)',
         N'Former tech-strategy committee dissolved 2025-Q4 — responsibilities merged into Executive Committee + new Architecture Review Board.',
         N'لجنة استراتيجية التقنية السابقة - تم حلها في الربع الرابع 2025 - دُمجت مسؤولياتها مع اللجنة التنفيذية ومجلس مراجعة المعمارية الجديد.',
         N'TECH_COMMITTEE',
         N'Former CTO', N'CTO',
         N'Former PMO Lead',
         N'[{"name":"Former CTO","role":"Chair","votingMember":true}]',
         N'(Dissolved.) Was responsible for tech roadmap + architecture standards before Architecture Review Board was stood up.',
         N'(تم حلها.) كانت مسؤولة عن خريطة طريق التقنية ومعايير المعمارية قبل تأسيس مجلس مراجعة المعمارية.',
         N'AD_HOC', 50, N'DISSOLVED',
         DATEADD(MONTH, -6, SYSUTCDATETIME()),
         N'Responsibilities merged into Executive Committee + new Architecture Review Board.',
         N'COM-OLD-TECH');
END
GO

-- ─── Seed meetings tied to committees ───────────────────────────────
DECLARE @board_id BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-BOARD');
DECLARE @risk_id  BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-RISK');
DECLARE @audit_id BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-AUDIT');
DECLARE @bcm_id   BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-BCM');

IF OBJECT_ID(N'admin.committee_meetings', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-01')
BEGIN
    INSERT INTO admin.committee_meetings
        (organisation_id, committee_id, committee_code, committee_name_en,
         meeting_code, title_en, title_ar,
         scheduled_at, started_at, ended_at, location_or_link,
         agenda_json, attendees_json, quorum_met,
         minutes_en, minutes_ar,
         decisions_json, action_items_json,
         chair_name, secretary_name,
         status, cancel_reason,
         minutes_approved_at, minutes_approved_by, approver_name,
         external_id)
    VALUES
        -- 1. Board Q1 2026 — MINUTES_APPROVED
        (1, @board_id, N'COM-BOARD', N'Board of Directors',
         N'MTG-DEMO-01',
         N'Q1 2026 Board meeting',
         N'اجتماع مجلس الإدارة - الربع الأول 2026',
         DATEADD(DAY, -75, SYSUTCDATETIME()),
         DATEADD(DAY, -75, SYSUTCDATETIME()),
         DATEADD(DAY, -75, DATEADD(HOUR, 3, SYSUTCDATETIME())),
         N'TSS HQ Boardroom + Microsoft Teams hybrid',
         N'[{"seq":1,"topicEn":"Q4 2025 financial results","topicAr":"النتائج المالية للربع الرابع 2025","presenter":"CFO","minutesAllocated":30},{"seq":2,"topicEn":"2026 strategic plan refresh","topicAr":"تحديث الخطة الاستراتيجية 2026","presenter":"CEO","minutesAllocated":45},{"seq":3,"topicEn":"Top 10 enterprise risks","topicAr":"أعلى 10 مخاطر مؤسسية","presenter":"CRO","minutesAllocated":30},{"seq":4,"topicEn":"NCA ECC compliance status","topicAr":"حالة الامتثال لـ NCA ECC","presenter":"CISO","minutesAllocated":20},{"seq":5,"topicEn":"AOB","topicAr":"أمور أخرى","presenter":"Chair","minutesAllocated":15}]',
         N'[{"name":"Abdullah Al-Saud","role":"Chairman","present":true,"votingMember":true},{"name":"Layla Al-Otaibi","role":"Vice-Chair","present":true,"votingMember":true},{"name":"Mohammed Al-Ghamdi","role":"Independent","present":true,"votingMember":true},{"name":"Nasser Al-Dosari","role":"Independent","present":true,"votingMember":true},{"name":"Sara Al-Qahtani","role":"Independent","present":false,"votingMember":true},{"name":"CEO","role":"Executive","present":true,"votingMember":true}]',
         1,
         N'The Board reviewed Q4 2025 financial results — revenue up 12% YoY, EBITDA margin 22%. The 2026 strategic plan refresh was approved with one amendment (Vision-2030 alignment section expanded). Top-10 risks reviewed; the Aramco contract renewal delay (RSK-2026-004) flagged as amber. NCA ECC compliance reported at 94% control coverage, on track for full attestation by 2026-Q3. The Board approved a 12% interim dividend pending year-end audit confirmation.',
         N'استعرض المجلس النتائج المالية للربع الرابع 2025 - الإيرادات +12% سنوياً، هامش EBITDA 22%. تم اعتماد تحديث الخطة الاستراتيجية 2026 مع تعديل واحد (توسعة قسم التوافق مع رؤية 2030). تم استعراض أعلى 10 مخاطر؛ تم وسم تأخر تجديد عقد أرامكو (RSK-2026-004) كهرماني. الإبلاغ عن امتثال NCA ECC بنسبة 94% من تغطية الضوابط، على المسار للاعتماد الكامل بحلول الربع الثالث 2026. اعتمد المجلس توزيع أرباح مرحلية 12% بانتظار تأكيد المراجعة السنوية.',
         N'[{"seq":1,"decisionEn":"Approve 2026 strategic plan with V2030 expansion","decisionAr":"اعتماد الخطة الاستراتيجية 2026 مع توسعة رؤية 2030","voteFor":5,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"},{"seq":2,"decisionEn":"Approve 12% interim dividend","decisionAr":"اعتماد توزيع أرباح مرحلية 12%","voteFor":5,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"},{"seq":3,"decisionEn":"Risk appetite for FX exposure raised to SAR 700k","decisionAr":"رفع شهية مخاطر صرف العملة إلى 700 ألف ريال","voteFor":4,"voteAgainst":1,"voteAbstain":0,"outcome":"APPROVED"}]',
         N'[{"seq":1,"actionEn":"Publish revised V2030 alignment section in 2026 plan","owner":"CEO Office","dueDate":"2026-03-15","status":"COMPLETED"},{"seq":2,"actionEn":"Quarterly Aramco contract update to Board","owner":"COO","dueDate":"2026-Q2","status":"IN_PROGRESS"},{"seq":3,"actionEn":"Submit dividend approval to ZATCA","owner":"CFO","dueDate":"2026-03-31","status":"COMPLETED"}]',
         N'Abdullah Al-Saud', N'Khalid Al-Mutairi',
         N'MINUTES_APPROVED', NULL,
         DATEADD(DAY, -68, SYSUTCDATETIME()), 1, N'Abdullah Al-Saud',
         N'MTG-DEMO-01'),

        -- 2. Risk Committee April 2026 — COMPLETED (minutes pending approval)
        (1, @risk_id, N'COM-RISK', N'Risk Management Committee',
         N'MTG-DEMO-02',
         N'Risk Committee — April 2026',
         N'لجنة المخاطر - أبريل 2026',
         DATEADD(DAY, -12, SYSUTCDATETIME()),
         DATEADD(DAY, -12, SYSUTCDATETIME()),
         DATEADD(DAY, -12, DATEADD(HOUR, 2, SYSUTCDATETIME())),
         N'Microsoft Teams',
         N'[{"seq":1,"topicEn":"KRI breach review (March)","topicAr":"مراجعة تجاوزات مؤشرات المخاطر (مارس)","presenter":"Risk Manager","minutesAllocated":30},{"seq":2,"topicEn":"Aramco contract delay scenario walk-through","topicAr":"عرض سيناريو تأخر عقد أرامكو","presenter":"CRO","minutesAllocated":30},{"seq":3,"topicEn":"Emerging risks: GenAI shadow-IT","topicAr":"المخاطر الناشئة: ذكاء اصطناعي توليدي","presenter":"CISO","minutesAllocated":20},{"seq":4,"topicEn":"AOB","topicAr":"أمور أخرى","presenter":"Chair","minutesAllocated":10}]',
         N'[{"name":"CRO","role":"Chair","present":true,"votingMember":true},{"name":"CFO","role":"Member","present":true,"votingMember":true},{"name":"COO","role":"Member","present":true,"votingMember":true},{"name":"CISO","role":"Member","present":true,"votingMember":true},{"name":"General Counsel","role":"Member","present":false,"votingMember":true},{"name":"DPO","role":"Standing Invitee","present":true,"votingMember":false}]',
         1,
         N'KRI breaches: 3 in March (FX exposure, vendor-concentration, MTTR-Tier1). Aramco contract delay scenario (SCN-DEMO-01) reviewed; recommended Board acceptance with 18-month review trigger. GenAI shadow-IT (ERX-DEMO-05) — Netskope CASB flagged 240 events/month; enterprise Copilot rollout sponsored.',
         N'تجاوزات مؤشرات المخاطر: 3 في مارس (التعرض للصرف، تركز المورد، MTTR-Tier1). تم استعراض سيناريو تأخر عقد أرامكو (SCN-DEMO-01)؛ توصية بقبول المجلس مع محفز مراجعة 18 شهراً. ذكاء اصطناعي توليدي (ERX-DEMO-05) - Netskope CASB رصد 240 حدث/شهر؛ تم رعاية نشر Copilot للمؤسسة.',
         N'[{"seq":1,"decisionEn":"Recommend Board acceptance of Aramco contract scenario","decisionAr":"التوصية بقبول المجلس لسيناريو عقد أرامكو","voteFor":4,"voteAgainst":0,"voteAbstain":1,"outcome":"APPROVED"},{"seq":2,"decisionEn":"Sponsor enterprise Copilot rollout (M365)","decisionAr":"رعاية نشر Copilot المؤسسي (M365)","voteFor":4,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"}]',
         N'[{"seq":1,"actionEn":"Submit Aramco scenario to Board Q2","owner":"CRO","dueDate":"2026-06-15","status":"PENDING"},{"seq":2,"actionEn":"Issue enterprise Copilot RFP","owner":"CISO","dueDate":"2026-05-30","status":"IN_PROGRESS"},{"seq":3,"actionEn":"Block consumer-LLM domains via DNS","owner":"SecOps Lead","dueDate":"2026-05-20","status":"PENDING"}]',
         N'CRO', N'Risk Manager',
         N'COMPLETED', NULL,
         NULL, NULL, NULL,
         N'MTG-DEMO-02'),

        -- 3. Audit Committee Q1 — MINUTES_APPROVED
        (1, @audit_id, N'COM-AUDIT', N'Audit Committee',
         N'MTG-DEMO-03',
         N'Audit Committee — Q1 2026',
         N'لجنة المراجعة - الربع الأول 2026',
         DATEADD(DAY, -90, SYSUTCDATETIME()),
         DATEADD(DAY, -90, SYSUTCDATETIME()),
         DATEADD(DAY, -90, DATEADD(HOUR, 2, SYSUTCDATETIME())),
         N'TSS HQ Boardroom',
         N'[{"seq":1,"topicEn":"FY2025 external audit findings","topicAr":"ملاحظات المراجعة الخارجية للسنة المالية 2025","presenter":"External Auditor","minutesAllocated":45},{"seq":2,"topicEn":"Internal audit Q1 plan","topicAr":"خطة المراجعة الداخلية للربع الأول","presenter":"Head of Internal Audit","minutesAllocated":30},{"seq":3,"topicEn":"NCA ECC evidence package review","topicAr":"مراجعة حزمة أدلة NCA ECC","presenter":"CISO","minutesAllocated":25}]',
         N'[{"name":"Mohammed Al-Ghamdi","role":"Chair","present":true,"votingMember":true},{"name":"Nasser Al-Dosari","role":"Member","present":true,"votingMember":true},{"name":"Sara Al-Qahtani","role":"Member","present":true,"votingMember":true},{"name":"Head of Internal Audit","role":"Standing Invitee","present":true,"votingMember":false},{"name":"External Auditor (KPMG)","role":"Standing Invitee","present":true,"votingMember":false}]',
         1,
         N'External auditor reported 2 management-letter points (segregation-of-duties in journal posting, IT GC user-access reviews). Internal audit Q1 plan approved as drafted. NCA ECC evidence package at 94% — gap in 1-2 control areas around backup verification frequency; agreed remediation by 2026-Q2.',
         N'أبلغ المراجع الخارجي عن نقطتي خطاب إدارة (فصل المهام في قيد اليومية، مراجعات وصول مستخدمي IT GC). تم اعتماد خطة المراجعة الداخلية للربع الأول كما هي. حزمة أدلة NCA ECC بنسبة 94% - فجوة في 1-2 مجال ضوابط حول تواتر التحقق من النسخ الاحتياطية؛ تم الاتفاق على المعالجة بحلول الربع الثاني 2026.',
         N'[{"seq":1,"decisionEn":"Approve internal audit Q1 plan","decisionAr":"اعتماد خطة المراجعة الداخلية للربع الأول","voteFor":3,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"},{"seq":2,"decisionEn":"Endorse FY2025 audit opinion","decisionAr":"تأييد رأي المراجعة للسنة المالية 2025","voteFor":3,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"}]',
         N'[{"seq":1,"actionEn":"Implement journal-posting SoD control","owner":"Finance Director","dueDate":"2026-04-30","status":"COMPLETED"},{"seq":2,"actionEn":"Quarterly IT GC user-access reviews","owner":"CISO","dueDate":"2026-Q2","status":"IN_PROGRESS"},{"seq":3,"actionEn":"Close NCA ECC backup-verification gap","owner":"SecOps Lead","dueDate":"2026-06-30","status":"IN_PROGRESS"}]',
         N'Mohammed Al-Ghamdi', N'Head of Internal Audit',
         N'MINUTES_APPROVED', NULL,
         DATEADD(DAY, -83, SYSUTCDATETIME()), 1, N'Mohammed Al-Ghamdi',
         N'MTG-DEMO-03'),

        -- 4. BCM Q2 meeting — SCHEDULED (future)
        (1, @bcm_id, N'COM-BCM', N'BCM Steering Committee',
         N'MTG-DEMO-04',
         N'BCM Steering — Q2 2026',
         N'توجيه استمرارية الأعمال - الربع الثاني 2026',
         DATEADD(DAY, 20, SYSUTCDATETIME()),
         NULL, NULL,
         N'TSS HQ Meeting Room 3 + Teams',
         N'[{"seq":1,"topicEn":"DRP test results — Jeddah failover exercise","topicAr":"نتائج اختبار DRP - تمرين التحويل إلى جدة","presenter":"BCM Officer","minutesAllocated":30},{"seq":2,"topicEn":"BIA refresh for new fintech subsidiary","topicAr":"تحديث تحليل الأثر لشركة فينتك الفرعية الجديدة","presenter":"BCM Officer","minutesAllocated":25},{"seq":3,"topicEn":"Q3 exercise calendar","topicAr":"تقويم تمارين الربع الثالث","presenter":"BCM Officer","minutesAllocated":20}]',
         N'[{"name":"COO","role":"Chair","present":null,"votingMember":true},{"name":"CIO","role":"Member","present":null,"votingMember":true},{"name":"CISO","role":"Member","present":null,"votingMember":true},{"name":"CFO","role":"Member","present":null,"votingMember":true},{"name":"BCM Officer","role":"Secretary","present":null,"votingMember":false},{"name":"Facilities Director","role":"Member","present":null,"votingMember":true}]',
         NULL,
         NULL, NULL, NULL, NULL,
         N'COO', N'BCM Officer',
         N'SCHEDULED', NULL,
         NULL, NULL, NULL,
         N'MTG-DEMO-04'),

        -- 5. Risk Committee March 2026 — MINUTES_APPROVED
        (1, @risk_id, N'COM-RISK', N'Risk Management Committee',
         N'MTG-DEMO-05',
         N'Risk Committee — March 2026',
         N'لجنة المخاطر - مارس 2026',
         DATEADD(DAY, -42, SYSUTCDATETIME()),
         DATEADD(DAY, -42, SYSUTCDATETIME()),
         DATEADD(DAY, -42, DATEADD(HOUR, 2, SYSUTCDATETIME())),
         N'Microsoft Teams',
         N'[{"seq":1,"topicEn":"Monthly KRI dashboard","topicAr":"لوحة مؤشرات المخاطر الشهرية","presenter":"Risk Manager","minutesAllocated":25},{"seq":2,"topicEn":"Cross-border transfer requests (ROPA-2026-007)","topicAr":"طلبات النقل العابر للحدود","presenter":"DPO","minutesAllocated":20},{"seq":3,"topicEn":"Q2 risk-acceptance pipeline","topicAr":"خط أنابيب قبول المخاطر للربع الثاني","presenter":"CRO","minutesAllocated":25}]',
         N'[{"name":"CRO","role":"Chair","present":true,"votingMember":true},{"name":"CFO","role":"Member","present":true,"votingMember":true},{"name":"COO","role":"Member","present":false,"votingMember":true},{"name":"CISO","role":"Member","present":true,"votingMember":true},{"name":"General Counsel","role":"Member","present":true,"votingMember":true},{"name":"DPO","role":"Standing Invitee","present":true,"votingMember":false}]',
         1,
         N'KRI dashboard reviewed; 2 amber breaches. Cross-border transfer to TCS India approved subject to BCR completion. Q2 risk-acceptance pipeline includes Aramco contract (RSK-2026-004) and FX hedge breach (RSK-2026-007).',
         N'تمت مراجعة لوحة مؤشرات المخاطر؛ تجاوزان كهرمانيان. تم اعتماد النقل العابر للحدود إلى TCS الهند رهناً بإكمال BCR. خط أنابيب قبول المخاطر للربع الثاني يشمل عقد أرامكو (RSK-2026-004) واختراق تحوط الصرف (RSK-2026-007).',
         N'[{"seq":1,"decisionEn":"Approve CBT to TCS India subject to BCR","decisionAr":"اعتماد النقل العابر إلى TCS الهند رهناً بـ BCR","voteFor":4,"voteAgainst":0,"voteAbstain":0,"outcome":"APPROVED"}]',
         N'[{"seq":1,"actionEn":"Complete BCR documentation","owner":"DPO","dueDate":"2026-05-30","status":"IN_PROGRESS"},{"seq":2,"actionEn":"Risk acceptance pack for FX breach","owner":"CFO","dueDate":"2026-05-15","status":"COMPLETED"}]',
         N'CRO', N'Risk Manager',
         N'MINUTES_APPROVED', NULL,
         DATEADD(DAY, -35, SYSUTCDATETIME()), 1, N'CRO',
         N'MTG-DEMO-05'),

        -- 6. BCM Q1 — IN_PROGRESS (special meeting happening today)
        (1, @bcm_id, N'COM-BCM', N'BCM Steering Committee',
         N'MTG-DEMO-06',
         N'BCM Special Session — Red Sea contingency',
         N'جلسة خاصة BCM - طوارئ البحر الأحمر',
         DATEADD(HOUR, -1, SYSUTCDATETIME()),
         DATEADD(HOUR, -1, SYSUTCDATETIME()),
         NULL,
         N'TSS HQ Crisis Room',
         N'[{"seq":1,"topicEn":"Red Sea route closure - what-if scenario","topicAr":"إغلاق طريق البحر الأحمر - سيناريو افتراضي","presenter":"COO","minutesAllocated":45},{"seq":2,"topicEn":"Cape route activation criteria","topicAr":"معايير تفعيل طريق رأس الرجاء الصالح","presenter":"Procurement Lead","minutesAllocated":30}]',
         N'[{"name":"COO","role":"Chair","present":true,"votingMember":true},{"name":"CIO","role":"Member","present":false,"votingMember":true},{"name":"CISO","role":"Member","present":true,"votingMember":true},{"name":"CFO","role":"Member","present":true,"votingMember":true},{"name":"BCM Officer","role":"Secretary","present":true,"votingMember":false},{"name":"Facilities Director","role":"Member","present":true,"votingMember":true}]',
         1,
         N'Special session called by COO in response to renewed Red Sea security advisory. Discussion ongoing.',
         N'جلسة خاصة دعا إليها الرئيس التنفيذي للعمليات استجابة لإنذار أمني متجدد في البحر الأحمر. النقاش جارٍ.',
         NULL, NULL,
         N'COO', N'BCM Officer',
         N'IN_PROGRESS', NULL,
         NULL, NULL, NULL,
         N'MTG-DEMO-06'),

        -- 7. Audit Q2 planning — CANCELLED
        (1, @audit_id, N'COM-AUDIT', N'Audit Committee',
         N'MTG-DEMO-07',
         N'Audit Committee — Q2 planning (cancelled)',
         N'لجنة المراجعة - تخطيط الربع الثاني (ملغى)',
         DATEADD(DAY, -7, SYSUTCDATETIME()),
         NULL, NULL,
         N'TSS HQ Boardroom',
         N'[{"seq":1,"topicEn":"Q2 internal audit scoping","topicAr":"تحديد نطاق المراجعة الداخلية للربع الثاني","presenter":"Head of Internal Audit","minutesAllocated":60}]',
         N'[{"name":"Mohammed Al-Ghamdi","role":"Chair","present":null,"votingMember":true},{"name":"Nasser Al-Dosari","role":"Member","present":null,"votingMember":true},{"name":"Sara Al-Qahtani","role":"Member","present":null,"votingMember":true}]',
         NULL,
         NULL, NULL, NULL, NULL,
         N'Mohammed Al-Ghamdi', N'Head of Internal Audit',
         N'CANCELLED',
         N'Quorum unavailable due to Eid scheduling conflict. Rescheduled to 2026-05-25.',
         NULL, NULL, NULL,
         N'MTG-DEMO-07'),

        -- 8. Board off-site Strategy 2026 — SCHEDULED
        (1, @board_id, N'COM-BOARD', N'Board of Directors',
         N'MTG-DEMO-08',
         N'Board Strategy Off-site — 2026',
         N'خلوة استراتيجية المجلس - 2026',
         DATEADD(DAY, 45, SYSUTCDATETIME()),
         NULL, NULL,
         N'AlUla, Saudi Arabia',
         N'[{"seq":1,"topicEn":"Vision 2030 alignment deep-dive","topicAr":"التعمق في التوافق مع رؤية 2030","presenter":"CEO + external strategist","minutesAllocated":180},{"seq":2,"topicEn":"M&A pipeline review","topicAr":"مراجعة خط أنابيب الاندماج والاستحواذ","presenter":"CFO","minutesAllocated":120},{"seq":3,"topicEn":"3-year capital allocation plan","topicAr":"خطة تخصيص رأس المال لثلاث سنوات","presenter":"CFO","minutesAllocated":90}]',
         N'[{"name":"Abdullah Al-Saud","role":"Chairman","present":null,"votingMember":true},{"name":"Layla Al-Otaibi","role":"Vice-Chair","present":null,"votingMember":true},{"name":"Mohammed Al-Ghamdi","role":"Independent","present":null,"votingMember":true},{"name":"Nasser Al-Dosari","role":"Independent","present":null,"votingMember":true},{"name":"Sara Al-Qahtani","role":"Independent","present":null,"votingMember":true},{"name":"CEO","role":"Executive","present":null,"votingMember":true}]',
         NULL,
         NULL, NULL, NULL, NULL,
         N'Abdullah Al-Saud', N'Khalid Al-Mutairi',
         N'SCHEDULED', NULL,
         NULL, NULL, NULL,
         N'MTG-DEMO-08');
END
GO

PRINT N'V40 — admin.committees (5 rows) + admin.committee_meetings (8 rows) applied.';
GO

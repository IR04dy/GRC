-- V194 — Sprint 13: Internal Audit Annual Plan + Engagement Workflow.
--
-- Saudi banks' Internal Audit (IA) functions today produce their annual
-- plan in Excel and run each engagement through a Word planning memo
-- → fieldwork notebooks → final report deck. The Audit Committee gets
-- PowerPoint summaries with no way to drill into source data. This
-- slice replaces that with a structured register aligned to IIA 2200-
-- 2600 standards and SAMA Internal Audit framework (2018).
--
-- Four tables, all under the existing audit_lc schema (already created
-- by V16):
--
--   audit_lc.ia_annual_plan          — one row per cycle_year, owned by CAE
--   audit_lc.ia_audit_universe_entry — the master universe of auditable
--                                      processes (plan_id NULL = master,
--                                      plan_id NOT NULL = a snapshot
--                                      pulled into a specific plan)
--   audit_lc.ia_engagement           — a single auditable assignment,
--                                      lifecycle PLANNED → PLANNING →
--                                      FIELDWORK → REPORTING →
--                                      REPORT_ISSUED → FOLLOW_UP → CLOSED
--   audit_lc.ia_engagement_milestone — kickoff / draft report /
--                                      management response / final
--                                      report / follow-up due
--
-- Regulatory anchors:
--   - SAMA Internal Audit Framework (2018) §3.1 — risk-based annual
--     planning, documented methodology, Audit Committee approval.
--   - IIA 2200 — Engagement Planning (planning memo per engagement)
--   - IIA 2300 — Performing the Engagement (working papers, evidence)
--   - IIA 2400 — Communicating Results (final report, opinion)
--   - IIA 2500 — Monitoring Progress (follow-up tracking)
--   - CMA Listed-Companies Corporate Governance Code Article 54 —
--     Audit Committee reviews IA annual plan + monitors execution.
--
-- Lifecycle state machines (enforced in IaAnnualPlanService and
-- IaEngagementService):
--
--   Plan:        DRAFT → BOARD_REVIEW → APPROVED → EXECUTING → COMPLETED → ARCHIVED
--   Engagement:  PLANNED → PLANNING → FIELDWORK → REPORTING → REPORT_ISSUED → FOLLOW_UP → CLOSED
--                                                                                          ↑
--                            (CANCELLED is a terminal off-ramp from any non-CLOSED state)
--
-- Pre-seed for org=1 (TSS Bank):
--   - 1 annual plan IAP-2026 (DRAFT)
--   - 20 audit-universe entries across the bank (retail, treasury,
--     IB, wealth, IT general controls, cloud, vendor, AML, PCI-DSS,
--     ESG reporting, AI governance...) all bilingual EN+AR with
--     Saudi-native phrasing.
--   - 5 planned engagements covering the highest-residual-risk
--     universe entries (AML KYC, Privileged Access, Mobile App
--     Security, GoAML/STR filing, Tawakkalna/SADAD integrations).
--   - 5 milestones per engagement = 25 milestone rows.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- audit_lc schema already exists from V16 — guard anyway.
IF SCHEMA_ID('audit_lc') IS NULL EXEC('CREATE SCHEMA audit_lc');
GO

-- ─── ia_annual_plan ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ia_annual_plan' AND schema_id = SCHEMA_ID('audit_lc'))
BEGIN
    CREATE TABLE audit_lc.ia_annual_plan (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        plan_code                       NVARCHAR(64)    NOT NULL,   -- e.g. IAP-2026
        cycle_year                      INT             NOT NULL,
        title_en                        NVARCHAR(500)   NOT NULL,
        title_ar                        NVARCHAR(500)   NOT NULL,

        prepared_by_user_id             BIGINT          NULL,       -- CAE
        status                          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',

        methodology_summary_en          NVARCHAR(MAX)   NULL,
        methodology_summary_ar          NVARCHAR(MAX)   NULL,

        audit_universe_count            INT             NOT NULL DEFAULT 0,
        total_engagements_planned       INT             NOT NULL DEFAULT 0,

        approved_by_user_id             BIGINT          NULL,
        approved_at                     DATETIME2       NULL,

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

    CREATE UNIQUE INDEX UX_ia_plan_org_code
        ON audit_lc.ia_annual_plan(organisation_id, plan_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_plan_org_year
        ON audit_lc.ia_annual_plan(organisation_id, cycle_year)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_plan_org_status
        ON audit_lc.ia_annual_plan(organisation_id, status)
        WHERE is_deleted = 0;
END
GO

-- ─── ia_audit_universe_entry ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ia_audit_universe_entry' AND schema_id = SCHEMA_ID('audit_lc'))
BEGIN
    CREATE TABLE audit_lc.ia_audit_universe_entry (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- NULL = master universe entry (the library)
        -- NOT NULL = snapshot pulled into a specific plan
        plan_id                         BIGINT          NULL,

        entry_code                      NVARCHAR(64)    NOT NULL,   -- e.g. AU-CREDIT-CARD-001
        title_en                        NVARCHAR(255)   NOT NULL,
        title_ar                        NVARCHAR(255)   NOT NULL,

        process_name_en                 NVARCHAR(255)   NULL,
        process_name_ar                 NVARCHAR(255)   NULL,
        business_unit_en                NVARCHAR(255)   NULL,
        business_unit_ar                NVARCHAR(255)   NULL,

        -- 0-25 scale typical for IA risk scoring
        inherent_risk_score             DECIMAL(4,2)    NULL,
        control_environment_score       DECIMAL(4,2)    NULL,
        residual_risk_score             DECIMAL(4,2)    NULL,

        last_audited_date               DATE            NULL,
        last_audit_rating               VARCHAR(20)     NULL,   -- SATISFACTORY/PARTIAL/UNSATISFACTORY

        planned_audit_year              INT             NULL,
        planned_quarter                 VARCHAR(10)     NULL,   -- Q1/Q2/Q3/Q4

        -- Cross-link to ERM risk + control register
        linked_risk_id                  BIGINT          NULL,
        linked_control_ids              NVARCHAR(500)   NULL,   -- CSV

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_ia_universe_plan
            FOREIGN KEY (plan_id) REFERENCES audit_lc.ia_annual_plan(id)
    );

    -- entry_code unique per (org, plan_id) — same code can appear in
    -- master (plan_id IS NULL) and in each plan snapshot.
    CREATE UNIQUE INDEX UX_ia_universe_org_plan_code
        ON audit_lc.ia_audit_universe_entry(organisation_id, plan_id, entry_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_universe_org_plan
        ON audit_lc.ia_audit_universe_entry(organisation_id, plan_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_universe_org_residual
        ON audit_lc.ia_audit_universe_entry(organisation_id, residual_risk_score)
        WHERE is_deleted = 0;
END
GO

-- ─── ia_engagement ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ia_engagement' AND schema_id = SCHEMA_ID('audit_lc'))
BEGIN
    CREATE TABLE audit_lc.ia_engagement (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        plan_id                         BIGINT          NOT NULL,
        universe_entry_id               BIGINT          NULL,

        engagement_code                 NVARCHAR(64)    NOT NULL,   -- e.g. ENG-2026-001
        title_en                        NVARCHAR(500)   NOT NULL,
        title_ar                        NVARCHAR(500)   NOT NULL,

        -- OPERATIONAL | FINANCIAL | COMPLIANCE | IT_GENERAL |
        -- IT_APPLICATION | FRAUD | SPECIAL_REQUEST | FOLLOW_UP |
        -- MANAGEMENT_REVIEW
        audit_type                      VARCHAR(40)     NOT NULL,

        scope_summary_en                NVARCHAR(MAX)   NULL,
        scope_summary_ar                NVARCHAR(MAX)   NULL,

        lead_auditor_user_id            BIGINT          NULL,
        team_member_ids                 NVARCHAR(500)   NULL,       -- CSV user IDs
        sponsor_user_id                 BIGINT          NULL,       -- senior leader

        planned_start_date              DATE            NULL,
        planned_end_date                DATE            NULL,
        actual_start_date               DATE            NULL,
        actual_end_date                 DATE            NULL,

        -- Phase machine (see Service.transitionPhase)
        -- PLANNED → PLANNING → FIELDWORK → REPORTING →
        -- REPORT_ISSUED → FOLLOW_UP → CLOSED  (CANCELLED off-ramp)
        phase                           VARCHAR(20)     NOT NULL DEFAULT 'PLANNED',

        planning_memo_url               NVARCHAR(500)   NULL,
        planning_memo_signed_off_at     DATETIME2       NULL,

        opinion                         VARCHAR(20)     NULL,       -- SATISFACTORY/PARTIAL/UNSATISFACTORY/NA

        finding_count                   INT             NOT NULL DEFAULT 0,
        critical_finding_count          INT             NOT NULL DEFAULT 0,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_ia_eng_plan
            FOREIGN KEY (plan_id) REFERENCES audit_lc.ia_annual_plan(id),
        CONSTRAINT FK_ia_eng_universe
            FOREIGN KEY (universe_entry_id) REFERENCES audit_lc.ia_audit_universe_entry(id)
    );

    CREATE UNIQUE INDEX UX_ia_eng_org_code
        ON audit_lc.ia_engagement(organisation_id, engagement_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_eng_org_plan_phase
        ON audit_lc.ia_engagement(organisation_id, plan_id, phase)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_eng_org_lead
        ON audit_lc.ia_engagement(organisation_id, lead_auditor_user_id)
        WHERE is_deleted = 0;
END
GO

-- ─── ia_engagement_milestone ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ia_engagement_milestone' AND schema_id = SCHEMA_ID('audit_lc'))
BEGIN
    CREATE TABLE audit_lc.ia_engagement_milestone (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,

        engagement_id                   BIGINT          NOT NULL,

        -- KICKOFF | FIELDWORK_START | DRAFT_REPORT |
        -- MANAGEMENT_RESPONSE | FINAL_REPORT | FOLLOW_UP_DUE
        milestone_type                  VARCHAR(40)     NOT NULL,

        planned_date                    DATE            NULL,
        actual_date                     DATE            NULL,
        completed_by_user_id            BIGINT          NULL,

        notes_en                        NVARCHAR(500)   NULL,
        notes_ar                        NVARCHAR(500)   NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_ia_milestone_eng
            FOREIGN KEY (engagement_id) REFERENCES audit_lc.ia_engagement(id)
    );

    CREATE INDEX IX_ia_milestone_eng
        ON audit_lc.ia_engagement_milestone(organisation_id, engagement_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_ia_milestone_eng_type
        ON audit_lc.ia_engagement_milestone(organisation_id, engagement_id, milestone_type)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed for org=1 ────────────────────────────────────────────
--
-- Idempotent — every INSERT checks for existing rows.

-- (1) Annual plan IAP-2026
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_annual_plan
               WHERE organisation_id = 1 AND plan_code = 'IAP-2026')
BEGIN
    INSERT INTO audit_lc.ia_annual_plan (
        organisation_id, plan_code, cycle_year, title_en, title_ar,
        status, methodology_summary_en, methodology_summary_ar,
        audit_universe_count, total_engagements_planned
    ) VALUES (
        1, 'IAP-2026', 2026,
        '2026 Internal Audit Annual Plan',
        N'خطة المراجعة الداخلية السنوية لعام 2026',
        'DRAFT',
        'Risk-based plan covering 20 auditable processes across the bank. Methodology follows SAMA IA Framework §3.1 and IIA 2200: inherent risk × control environment → residual risk score (0-25), with quarterly distribution weighted to high-residual entries.',
        N'خطة قائمة على المخاطر تغطي 20 عملية قابلة للمراجعة على مستوى البنك. تتبع المنهجية إطار المراجعة الداخلية لمؤسسة النقد §3.1 ومعيار IIA 2200: الخطر المتأصل × بيئة الضبط ← الخطر المتبقي بمقياس (0-25)، مع توزيع ربعي بحسب الجَردة عالية المخاطر المتبقية.',
        20, 5
    );
END
GO

DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

-- (2) 20 audit-universe master entries (plan_id = NULL means master library;
--      but per the spec, each row is associated with IAP-2026 — we set plan_id).
--
-- Codes prefixed AU-* with bilingual EN+AR, inherent + control env + residual
-- scores on the 0-25 scale (IA convention: inherent 0-25, control_env strength
-- 0-1, residual = inherent × (1 - control_env_score)).
--
-- Listed in roughly descending residual-risk order so the first 5 are the
-- 5 engagements seeded below.

IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_audit_universe_entry
               WHERE organisation_id = 1 AND entry_code = 'AU-AML-KYC-001' AND plan_id = @plan_id)
BEGIN
    INSERT INTO audit_lc.ia_audit_universe_entry
        (organisation_id, plan_id, entry_code, title_en, title_ar,
         process_name_en, process_name_ar, business_unit_en, business_unit_ar,
         inherent_risk_score, control_environment_score, residual_risk_score,
         last_audited_date, last_audit_rating, planned_audit_year, planned_quarter)
    VALUES
        (1, @plan_id, 'AU-AML-KYC-001',
         'AML / KYC Onboarding and Periodic Review',
         N'مكافحة غسل الأموال / إجراءات اعرف عميلك للاستقبال والمراجعة الدورية',
         'Customer onboarding, EDD, periodic refresh', N'استقبال العميل، العناية الواجبة المعزّزة، التحديث الدوري',
         'Compliance', N'الالتزام',
         22.00, 0.30, 15.40, '2025-03-15', 'PARTIAL', 2026, 'Q1'),

        (1, @plan_id, 'AU-PAM-PRIVILEGED-002',
         'Privileged Access Management (PAM)',
         N'إدارة الوصول المتميّز',
         'Vault rotation, JIT elevation, session recording', N'تدوير خزنة كلمات السر، الترقية في حينها، تسجيل الجلسات',
         'Cyber & IT', N'الأمن السيبراني وتقنية المعلومات',
         21.00, 0.35, 13.65, '2025-06-20', 'PARTIAL', 2026, 'Q1'),

        (1, @plan_id, 'AU-MOBILE-APP-003',
         'Mobile Banking App Security',
         N'أمن تطبيق الخدمات المصرفية عبر الجوّال',
         'Authentication, jailbreak detection, secure storage', N'المصادقة، رصد كسر الحماية، التخزين الآمن',
         'Digital Channels', N'القنوات الرقمية',
         20.50, 0.40, 12.30, '2024-11-05', 'PARTIAL', 2026, 'Q2'),

        (1, @plan_id, 'AU-GOAML-STR-004',
         'GoAML / Suspicious Transaction Report (STR) Filing',
         N'الإبلاغ عن المعاملات المشبوهة عبر منصّة GoAML',
         'STR detection, internal review, SAFIU filing', N'رصد المعاملات المشبوهة، المراجعة الداخلية، الإبلاغ لإدارة التحريات المالية',
         'Compliance / AML Operations', N'الالتزام / عمليات مكافحة غسل الأموال',
         20.00, 0.40, 12.00, '2025-01-10', 'UNSATISFACTORY', 2026, 'Q2'),

        (1, @plan_id, 'AU-TAWAKKALNA-SADAD-005',
         'Tawakkalna / SADAD Payment Integrations',
         N'تكاملات منصّتي توكلنا وسداد للمدفوعات',
         'API authentication, settlement reconciliation, error handling', N'مصادقة واجهات الربط، تسوية المدفوعات، معالجة الأخطاء',
         'Payments & Settlements', N'المدفوعات والتسويات',
         19.50, 0.40, 11.70, NULL, NULL, 2026, 'Q3');
END
GO

DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_audit_universe_entry
               WHERE organisation_id = 1 AND entry_code = 'AU-CREDIT-CARD-006' AND plan_id = @plan_id)
BEGIN
    INSERT INTO audit_lc.ia_audit_universe_entry
        (organisation_id, plan_id, entry_code, title_en, title_ar,
         process_name_en, process_name_ar, business_unit_en, business_unit_ar,
         inherent_risk_score, control_environment_score, residual_risk_score,
         last_audited_date, last_audit_rating, planned_audit_year, planned_quarter)
    VALUES
        (1, @plan_id, 'AU-CREDIT-CARD-006',
         'Credit Card Lending and Limit Management',
         N'إقراض البطاقات الائتمانية وإدارة سقوف الائتمان',
         'Application underwriting, limit review, delinquency', N'الاكتتاب على الطلبات، مراجعة السقوف، التعثّر',
         'Retail Banking', N'الخدمات المصرفية للأفراد',
         19.00, 0.45, 10.45, '2024-09-12', 'SATISFACTORY', 2026, 'Q2'),

        (1, @plan_id, 'AU-MORTGAGE-007',
         'Mortgage Origination',
         N'إصدار التمويل العقاري',
         'Property valuation, REDF approvals, repayment schedule', N'تقييم العقار، اعتمادات صندوق التنمية العقارية، جدول السداد',
         'Retail Banking', N'الخدمات المصرفية للأفراد',
         18.50, 0.50, 9.25, '2025-02-28', 'SATISFACTORY', 2026, 'Q3'),

        (1, @plan_id, 'AU-TREASURY-SETTLE-008',
         'Treasury Settlement and FX Operations',
         N'تسوية الخزينة وعمليات الصرف الأجنبي',
         'SAR/USD bookings, SARIE settlement, intraday liquidity', N'حجوزات الريال/الدولار، تسوية نظام سريع، السيولة اليومية',
         'Treasury', N'الخزينة',
         18.00, 0.50, 9.00, '2024-10-18', 'SATISFACTORY', 2026, 'Q3'),

        (1, @plan_id, 'AU-INVESTMENT-BANK-009',
         'Investment Banking — Underwriting and Advisory',
         N'الخدمات المصرفية الاستثمارية — التغطية والاستشارات',
         'IPO underwriting, M&A advisory, deal disclosure', N'تغطية الطروحات، الاستشارات في الدمج والاستحواذ، الإفصاح عن الصفقات',
         'Investment Banking', N'الخدمات المصرفية الاستثمارية',
         17.50, 0.50, 8.75, NULL, NULL, 2026, 'Q4'),

        (1, @plan_id, 'AU-WEALTH-MGT-010',
         'Wealth Management and Discretionary Portfolios',
         N'إدارة الثروات والمحافظ الاستثمارية التفويضية',
         'Suitability assessment, mandate compliance, performance reporting', N'تقييم الملاءمة، الالتزام بالتفويض، التقارير الأدائية',
         'Wealth Management', N'إدارة الثروات',
         17.00, 0.55, 7.65, '2025-04-22', 'SATISFACTORY', 2026, 'Q4');
END
GO

DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_audit_universe_entry
               WHERE organisation_id = 1 AND entry_code = 'AU-TRADE-FINANCE-011' AND plan_id = @plan_id)
BEGIN
    INSERT INTO audit_lc.ia_audit_universe_entry
        (organisation_id, plan_id, entry_code, title_en, title_ar,
         process_name_en, process_name_ar, business_unit_en, business_unit_ar,
         inherent_risk_score, control_environment_score, residual_risk_score,
         last_audited_date, last_audit_rating, planned_audit_year, planned_quarter)
    VALUES
        (1, @plan_id, 'AU-TRADE-FINANCE-011',
         'Trade Finance — LC and Guarantees',
         N'التمويل التجاري — الاعتمادات المستندية والضمانات',
         'LC issuance, sanction screening, document checking', N'إصدار الاعتمادات المستندية، فحص العقوبات، تدقيق المستندات',
         'Corporate Banking', N'الخدمات المصرفية للشركات',
         17.00, 0.55, 7.65, '2024-12-08', 'PARTIAL', 2026, 'Q4'),

        (1, @plan_id, 'AU-RETAIL-BRANCH-012',
         'Retail Branch Operations',
         N'عمليات الفروع للأفراد',
         'Cash handling, account opening, KYC at branch', N'مناولة النقد، فتح الحسابات، اعرف عميلك في الفرع',
         'Retail Banking — Branch Network', N'الخدمات المصرفية للأفراد — شبكة الفروع',
         16.50, 0.55, 7.43, '2025-05-15', 'SATISFACTORY', 2026, 'Q3'),

        (1, @plan_id, 'AU-DIGITAL-CHANNELS-013',
         'Internet Banking and Digital Channels Governance',
         N'حوكمة الخدمات المصرفية عبر الإنترنت والقنوات الرقمية',
         'Multi-factor auth, fraud detection, dispute handling', N'المصادقة متعدّدة العوامل، رصد الاحتيال، معالجة الاعتراضات',
         'Digital Channels', N'القنوات الرقمية',
         18.00, 0.55, 8.10, '2025-07-30', 'PARTIAL', 2026, 'Q2'),

        (1, @plan_id, 'AU-CLOUD-INFRA-014',
         'Cloud Infrastructure (NCA CCC compliance)',
         N'البنية التحتية السحابية (الالتزام بضوابط الحوسبة السحابية للهيئة الوطنية)',
         'Tenant segregation, encryption keys, residency', N'فصل المستأجرين، مفاتيح التشفير، توطين البيانات',
         'Cyber & IT — Cloud', N'الأمن السيبراني وتقنية المعلومات — السحابة',
         18.50, 0.55, 8.33, NULL, NULL, 2026, 'Q1'),

        (1, @plan_id, 'AU-VENDOR-MGMT-015',
         'Third-Party / Vendor Management',
         N'إدارة الأطراف الخارجية والموردين',
         'TPRM assessments, contract clauses, exit plans', N'تقييمات إدارة مخاطر الأطراف الخارجية، البنود التعاقدية، خطط الخروج',
         'Compliance / Procurement', N'الالتزام / المشتريات',
         16.00, 0.50, 8.00, '2024-08-25', 'PARTIAL', 2026, 'Q3');
END
GO

DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_audit_universe_entry
               WHERE organisation_id = 1 AND entry_code = 'AU-BCM-READINESS-016' AND plan_id = @plan_id)
BEGIN
    INSERT INTO audit_lc.ia_audit_universe_entry
        (organisation_id, plan_id, entry_code, title_en, title_ar,
         process_name_en, process_name_ar, business_unit_en, business_unit_ar,
         inherent_risk_score, control_environment_score, residual_risk_score,
         last_audited_date, last_audit_rating, planned_audit_year, planned_quarter)
    VALUES
        (1, @plan_id, 'AU-BCM-READINESS-016',
         'BCM / DR Readiness and Exercises',
         N'جاهزية استمرارية الأعمال والتعافي من الكوارث وتمارين الاختبار',
         'BIA, BCP exercises, vendor MTPD', N'تحليل أثر الأعمال، تمارين خطة الاستمرارية، حد الانقطاع الأقصى للموردين',
         'BCM / Operational Risk', N'استمرارية الأعمال / مخاطر التشغيل',
         15.50, 0.55, 6.98, '2025-01-20', 'PARTIAL', 2026, 'Q4'),

        (1, @plan_id, 'AU-CLIMATE-REPORTING-017',
         'TCFD Climate Reporting and Scope-3 Emissions',
         N'الإفصاح المناخي وفق TCFD وانبعاثات النطاق الثالث',
         'Scenario analysis, financed emissions, board reporting', N'تحليل السيناريوهات، الانبعاثات المُموَّلة، التقارير للمجلس',
         'ESG / Sustainability', N'الحوكمة البيئية والاجتماعية / الاستدامة',
         14.00, 0.50, 7.00, NULL, NULL, 2026, 'Q4'),

        (1, @plan_id, 'AU-AI-GOVERNANCE-018',
         'AI / ML Model Governance (SDAIA + SAMA)',
         N'حوكمة نماذج الذكاء الاصطناعي وتعلّم الآلة (الهيئة السعودية للبيانات والذكاء الاصطناعي + مؤسسة النقد)',
         'Model registry, bias testing, explainability', N'سجل النماذج، اختبار التحيّز، قابلية التفسير',
         'Data & AI', N'البيانات والذكاء الاصطناعي',
         16.00, 0.50, 8.00, NULL, NULL, 2026, 'Q2'),

        (1, @plan_id, 'AU-PCI-DSS-019',
         'PCI-DSS — Payment Card Data Environment',
         N'معيار حماية بيانات بطاقات الدفع PCI-DSS',
         'Cardholder data scoping, segmentation, QSA evidence', N'تحديد نطاق بيانات حاملي البطاقات، الفصل الشبكي، أدلّة المقيِّم المؤهَّل',
         'Cyber & IT — Payments', N'الأمن السيبراني وتقنية المعلومات — المدفوعات',
         17.00, 0.55, 7.65, '2025-08-14', 'SATISFACTORY', 2026, 'Q1'),

        (1, @plan_id, 'AU-ESG-REPORTING-020',
         'Saudi Exchange ESG Disclosure',
         N'إفصاح الحوكمة البيئية والاجتماعية لتداول السعودية',
         'Materiality, KPI assurance, board sign-off', N'الأهمية النسبية، التأكيد على المؤشرات، اعتماد المجلس',
         'ESG / Investor Relations', N'الحوكمة البيئية والاجتماعية / علاقات المستثمرين',
         13.00, 0.55, 5.85, NULL, NULL, 2026, 'Q3');
END
GO

-- (3) 5 engagements covering the 5 highest-residual-risk universe entries.

DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

-- AU-AML-KYC-001 → ENG-2026-001
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement
               WHERE organisation_id = 1 AND engagement_code = 'ENG-2026-001')
BEGIN
    DECLARE @uid_aml BIGINT = (SELECT id FROM audit_lc.ia_audit_universe_entry
                               WHERE organisation_id = 1 AND entry_code = 'AU-AML-KYC-001' AND plan_id = @plan_id);
    INSERT INTO audit_lc.ia_engagement
        (organisation_id, plan_id, universe_entry_id, engagement_code, title_en, title_ar,
         audit_type, scope_summary_en, scope_summary_ar,
         planned_start_date, planned_end_date, phase)
    VALUES
        (1, @plan_id, @uid_aml, 'ENG-2026-001',
         'AML / KYC Operations Audit — Q1 2026',
         N'مراجعة عمليات مكافحة غسل الأموال واعرف عميلك — الربع الأول 2026',
         'COMPLIANCE',
         'End-to-end review of customer onboarding, periodic refresh, and Enhanced Due Diligence (EDD) for PEPs. Coverage: 200 sampled accounts opened in 2025, 50 EDD files, sanctions screening logs, GoAML feed integration.',
         N'مراجعة شاملة لعملية استقبال العملاء والتحديث الدوري والعناية الواجبة المعزّزة للأشخاص المعرَّضين سياسياً. التغطية: عيّنة من 200 حساب مفتوح خلال 2025، 50 ملف عناية واجبة معزَّزة، سجلات فحص العقوبات، تكامل تغذية منصّة GoAML.',
         '2026-01-15', '2026-03-30', 'PLANNED');
END
GO

-- AU-PAM-PRIVILEGED-002 → ENG-2026-002
DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement
               WHERE organisation_id = 1 AND engagement_code = 'ENG-2026-002')
BEGIN
    DECLARE @uid_pam BIGINT = (SELECT id FROM audit_lc.ia_audit_universe_entry
                               WHERE organisation_id = 1 AND entry_code = 'AU-PAM-PRIVILEGED-002' AND plan_id = @plan_id);
    INSERT INTO audit_lc.ia_engagement
        (organisation_id, plan_id, universe_entry_id, engagement_code, title_en, title_ar,
         audit_type, scope_summary_en, scope_summary_ar,
         planned_start_date, planned_end_date, phase)
    VALUES
        (1, @plan_id, @uid_pam, 'ENG-2026-002',
         'Privileged Access Management Audit — Q1 2026',
         N'مراجعة إدارة الوصول المتميّز — الربع الأول 2026',
         'IT_GENERAL',
         'Review of vault rotation, just-in-time elevation, session recording, and break-glass procedures. Coverage: CyberArk PAM, 80 named admin accounts, 20 sampled session recordings, vault key rotation logs.',
         N'مراجعة تدوير خزنة كلمات السر، الترقية في حينها، تسجيل الجلسات، وإجراءات كسر الزجاج. التغطية: منصّة CyberArk، 80 حساب مدير مُسمّى، عيّنة من 20 تسجيل جلسة، سجلات تدوير مفاتيح الخزنة.',
         '2026-02-01', '2026-03-31', 'PLANNED');
END
GO

-- AU-MOBILE-APP-003 → ENG-2026-003
DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement
               WHERE organisation_id = 1 AND engagement_code = 'ENG-2026-003')
BEGIN
    DECLARE @uid_mob BIGINT = (SELECT id FROM audit_lc.ia_audit_universe_entry
                               WHERE organisation_id = 1 AND entry_code = 'AU-MOBILE-APP-003' AND plan_id = @plan_id);
    INSERT INTO audit_lc.ia_engagement
        (organisation_id, plan_id, universe_entry_id, engagement_code, title_en, title_ar,
         audit_type, scope_summary_en, scope_summary_ar,
         planned_start_date, planned_end_date, phase)
    VALUES
        (1, @plan_id, @uid_mob, 'ENG-2026-003',
         'Mobile Banking App Security Audit — Q2 2026',
         N'مراجعة أمن تطبيق الخدمات المصرفية عبر الجوّال — الربع الثاني 2026',
         'IT_APPLICATION',
         'Security review of iOS and Android mobile banking apps: authentication, jailbreak/root detection, secure storage, OWASP MASVS coverage, third-party SDKs.',
         N'مراجعة أمنية لتطبيقي iOS و Android للخدمات المصرفية: المصادقة، رصد كسر الحماية والصلاحيات الجذرية، التخزين الآمن، تغطية معيار OWASP MASVS، حزم التطوير من الأطراف الثالثة.',
         '2026-04-01', '2026-06-15', 'PLANNED');
END
GO

-- AU-GOAML-STR-004 → ENG-2026-004
DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement
               WHERE organisation_id = 1 AND engagement_code = 'ENG-2026-004')
BEGIN
    DECLARE @uid_goaml BIGINT = (SELECT id FROM audit_lc.ia_audit_universe_entry
                                 WHERE organisation_id = 1 AND entry_code = 'AU-GOAML-STR-004' AND plan_id = @plan_id);
    INSERT INTO audit_lc.ia_engagement
        (organisation_id, plan_id, universe_entry_id, engagement_code, title_en, title_ar,
         audit_type, scope_summary_en, scope_summary_ar,
         planned_start_date, planned_end_date, phase)
    VALUES
        (1, @plan_id, @uid_goaml, 'ENG-2026-004',
         'GoAML STR Filing Process Audit — Q2 2026',
         N'مراجعة عملية الإبلاغ عن المعاملات المشبوهة عبر منصّة GoAML — الربع الثاني 2026',
         'COMPLIANCE',
         'Re-audit following the UNSATISFACTORY rating in 2025. Coverage: STR triage workflow, internal review timeliness, SAFIU filing completeness, false-positive analysis, MLRO escalation log.',
         N'إعادة مراجعة عقب التقييم غير المُرضي في 2025. التغطية: تدفّق فرز المعاملات المشبوهة، توقيتية المراجعة الداخلية، اكتمال الإبلاغ لإدارة التحريات المالية، تحليل النتائج الإيجابية الكاذبة، سجلّ تصعيد مسؤول الإبلاغ عن غسل الأموال.',
         '2026-04-15', '2026-06-30', 'PLANNED');
END
GO

-- AU-TAWAKKALNA-SADAD-005 → ENG-2026-005
DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');
IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement
               WHERE organisation_id = 1 AND engagement_code = 'ENG-2026-005')
BEGIN
    DECLARE @uid_tw BIGINT = (SELECT id FROM audit_lc.ia_audit_universe_entry
                              WHERE organisation_id = 1 AND entry_code = 'AU-TAWAKKALNA-SADAD-005' AND plan_id = @plan_id);
    INSERT INTO audit_lc.ia_engagement
        (organisation_id, plan_id, universe_entry_id, engagement_code, title_en, title_ar,
         audit_type, scope_summary_en, scope_summary_ar,
         planned_start_date, planned_end_date, phase)
    VALUES
        (1, @plan_id, @uid_tw, 'ENG-2026-005',
         'Tawakkalna / SADAD Integration Audit — Q3 2026',
         N'مراجعة تكاملات منصّتي توكلنا وسداد — الربع الثالث 2026',
         'IT_APPLICATION',
         'First-time audit of the integrations launched late 2025. Coverage: OAuth2 token lifecycle, settlement reconciliation, error-handling and replay, audit logging, NCA CCC alignment.',
         N'أوّل مراجعة للتكاملات التي أُطلقت في نهاية 2025. التغطية: دورة حياة رموز OAuth2، تسوية المدفوعات، معالجة الأخطاء وإعادة الإرسال، تسجيل الأحداث، التوافق مع ضوابط الحوسبة السحابية للهيئة الوطنية.',
         '2026-07-01', '2026-09-15', 'PLANNED');
END
GO

-- (4) 5 milestones per engagement = 25 rows
DECLARE @plan_id BIGINT = (SELECT id FROM audit_lc.ia_annual_plan
                           WHERE organisation_id = 1 AND plan_code = 'IAP-2026');

DECLARE @eng_cursor CURSOR;
DECLARE @eng_id BIGINT, @eng_start DATE, @eng_end DATE, @eng_code NVARCHAR(64);

SET @eng_cursor = CURSOR LOCAL FAST_FORWARD FOR
    SELECT id, planned_start_date, planned_end_date, engagement_code
    FROM audit_lc.ia_engagement
    WHERE organisation_id = 1 AND plan_id = @plan_id;

OPEN @eng_cursor;
FETCH NEXT FROM @eng_cursor INTO @eng_id, @eng_start, @eng_end, @eng_code;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (SELECT 1 FROM audit_lc.ia_engagement_milestone
                   WHERE organisation_id = 1 AND engagement_id = @eng_id AND milestone_type = 'KICKOFF')
    BEGIN
        INSERT INTO audit_lc.ia_engagement_milestone
            (organisation_id, engagement_id, milestone_type, planned_date, notes_en, notes_ar)
        VALUES
            (1, @eng_id, 'KICKOFF',
             @eng_start,
             'Kickoff meeting with sponsor and auditee leadership; planning memo distribution',
             N'اجتماع الانطلاق مع الجهة المسؤولة وقيادة الجهة الخاضعة للمراجعة، وتوزيع مذكّرة التخطيط'),
            (1, @eng_id, 'FIELDWORK_START',
             DATEADD(DAY, 14, @eng_start),
             'Fieldwork begins — sample selection, walkthroughs, test of design',
             N'بدء العمل الميداني — اختيار العيّنات، الجولات التطبيقية، اختبار التصميم'),
            (1, @eng_id, 'DRAFT_REPORT',
             DATEADD(DAY, -21, @eng_end),
             'Draft report circulated to lead auditor and quality reviewer',
             N'تعميم مسوّدة التقرير على المراجع الرئيسي ومراجع الجودة'),
            (1, @eng_id, 'MANAGEMENT_RESPONSE',
             DATEADD(DAY, -10, @eng_end),
             'Management response received and incorporated into the final report',
             N'استلام ردّ الإدارة وتضمينه في التقرير النهائي'),
            (1, @eng_id, 'FINAL_REPORT',
             @eng_end,
             'Final report issued to Audit Committee and sponsor',
             N'إصدار التقرير النهائي إلى لجنة المراجعة والجهة المسؤولة');
    END
    FETCH NEXT FROM @eng_cursor INTO @eng_id, @eng_start, @eng_end, @eng_code;
END
CLOSE @eng_cursor;
DEALLOCATE @eng_cursor;
GO

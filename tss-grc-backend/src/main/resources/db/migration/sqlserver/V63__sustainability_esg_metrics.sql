-- V63 — ESG Metrics Register: Environmental / Social / Governance
--       (Sustainability · slice 9)
--
-- Unified register for the spec's three metric categories (Sections
-- 4-I, 4-J, 4-K). Single table with a `category` discriminator instead
-- of three parallel tables — one mental model + one service + one UI
-- with a category filter chip.
--
-- Each metric tracks: baseline / current / target with a unit, an
-- owner, a reporting period, and an optional commitment link
-- (linked_sgi_commitment_id) so the dashboard can roll "energy
-- intensity" up to "50% renewable mix by 2030".
--
-- Progress is computed on read by the service (direction-aware,
-- mirroring the SGI pattern) — both raise-to-target ("renewable %")
-- and lower-to-target ("LTIFR", "water intensity") supported.
--
-- Status:
--   ACTIVE         — currently tracked
--   AT_TARGET      — current_value meets target (computed snapshot)
--   OFF_TRACK      — current value worse than baseline
--   ARCHIVED       — retired metric

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'esg_metric' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.esg_metric (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        metric_code              NVARCHAR(40)  NULL,

        reporting_year           SMALLINT      NOT NULL,

        -- ENVIRONMENTAL | SOCIAL | GOVERNANCE
        category                 NVARCHAR(20)  NOT NULL,

        -- Sub-category — finer grouping within the pillar.
        -- ENVIRONMENTAL: ENERGY | WATER | WASTE | EMISSIONS | BIODIVERSITY | FLEET
        -- SOCIAL:        SAUDIZATION | GENDER | TRAINING | SAFETY | COMMUNITY | PROCUREMENT
        -- GOVERNANCE:    BOARD | POLICY | ETHICS | AUDIT | WHISTLEBLOWING | TRAINING_GOV
        subcategory              NVARCHAR(40)  NULL,

        metric_name_en           NVARCHAR(255) NOT NULL,
        metric_name_ar           NVARCHAR(255) NULL,
        description_en           NVARCHAR(MAX) NULL,
        description_ar           NVARCHAR(MAX) NULL,

        unit                     NVARCHAR(40)  NOT NULL,    -- "%", "MWh", "tCO2e/yr", etc.

        baseline_value           DECIMAL(20,4) NULL,
        baseline_year            SMALLINT      NULL,
        current_value            DECIMAL(20,4) NULL,
        current_value_as_of      DATE          NULL,
        target_value             DECIMAL(20,4) NULL,
        target_year              SMALLINT      NULL,

        -- ACTIVE | AT_TARGET | OFF_TRACK | ARCHIVED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',

        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,
        responsible_department   NVARCHAR(120) NULL,

        -- Link to a Saudi Green Initiative commitment (V59).
        linked_sgi_commitment_id BIGINT        NULL,
        evidence_ref             NVARCHAR(255) NULL,

        notes                    NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_esg_m_category CHECK (category IN
            (N'ENVIRONMENTAL', N'SOCIAL', N'GOVERNANCE')),
        CONSTRAINT ck_esg_m_status   CHECK (status IN
            (N'ACTIVE', N'AT_TARGET', N'OFF_TRACK', N'ARCHIVED'))
    );

    CREATE INDEX ix_esg_m_org_year         ON sust.esg_metric(organisation_id, reporting_year);
    CREATE INDEX ix_esg_m_org_category     ON sust.esg_metric(organisation_id, category);
    CREATE INDEX ix_esg_m_org_subcategory  ON sust.esg_metric(organisation_id, category, subcategory);
END
GO

-- ─── Seed — 15 metrics across the 3 categories (5 each) ─────────────
-- Mirrors the spec's "Environmental Metrics" / "Social Metrics" /
-- "Governance Metrics" sections (4-I/J/K). FY2025 values pulled
-- from the existing seed datasets so the register lines up with
-- the rest of the sustainability stack.

IF NOT EXISTS (SELECT 1 FROM sust.esg_metric WHERE organisation_id = 1 AND reporting_year = 2025)
BEGIN
    INSERT INTO sust.esg_metric (
        organisation_id, metric_code, reporting_year, category, subcategory,
        metric_name_en, metric_name_ar, description_en, description_ar,
        unit, baseline_value, baseline_year, current_value, current_value_as_of,
        target_value, target_year, status,
        owner_name, owner_email, responsible_department, evidence_ref
    )
    VALUES
    -- ─── Environmental (5) ─────────────────────────────────────────
    (1, N'ENV-ENERGY-TOTAL', 2025, N'ENVIRONMENTAL', N'ENERGY',
     N'Total energy consumption',
     N'إجمالي استهلاك الطاقة',
     N'All energy consumed across operations and facilities.',
     N'كامل الطاقة المُستهلكة عبر العمليات والمرافق.',
     N'MWh', 92500, 2025, 89400, '2026-03-31',
     80000, 2030, N'ACTIVE',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'Energy & Operations', N'Energy mix 2025'),

    (1, N'ENV-RENEW-PCT', 2025, N'ENVIRONMENTAL', N'ENERGY',
     N'Renewable energy share',
     N'حصة الطاقة المتجدّدة',
     N'Share of renewable energy (solar PPA + on-site PV) in the total energy mix.',
     N'حصة الطاقة المتجدّدة (شراء طاقة شمسية + ألواح شمسية بالموقع) من إجمالي مزيج الطاقة.',
     N'%', 8.0, 2025, 22.0, '2026-03-31',
     50.0, 2030, N'ACTIVE',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'Energy & Operations', N'Sudair PPA 2026'),

    (1, N'ENV-WATER-INT', 2025, N'ENVIRONMENTAL', N'WATER',
     N'Water intensity',
     N'كثافة استهلاك المياه',
     N'Cubic metres of water withdrawn per SAR 1,000 of revenue.',
     N'الأمتار المكعبة من المياه المسحوبة لكل ألف ريال من الإيرادات.',
     N'm³/SAR1k', 4.2, 2025, 3.7, '2026-03-31',
     3.15, 2030, N'ACTIVE',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'Operations', N'Water-intensity Q1 2026'),

    (1, N'ENV-WASTE-DIVERT', 2025, N'ENVIRONMENTAL', N'WASTE',
     N'Waste diversion rate',
     N'نسبة تحويل النفايات بعيداً عن المكبّ',
     N'Share of non-hazardous waste diverted from landfill (recycle + reuse).',
     N'حصة النفايات غير الخطرة المُحوَّلة بعيداً عن المكبّ (إعادة التدوير + الاستخدام).',
     N'%', 18.0, 2025, 38.0, '2026-03-31',
     60.0, 2030, N'ACTIVE',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'Operations', N'GAMEP partnership 2025'),

    (1, N'ENV-EV-FLEET', 2025, N'ENVIRONMENTAL', N'FLEET',
     N'EV fleet conversion',
     N'تحويل الأسطول إلى مركبات كهربائية',
     N'Share of light-vehicle fleet converted to electric vehicles.',
     N'حصة الأسطول الخفيف المُحوَّل إلى المركبات الكهربائية.',
     N'%', 0, 2025, 12.0, '2026-03-31',
     30.0, 2030, N'ACTIVE',
     N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     N'Fleet Management', N'EV fleet Q1 2026'),

    -- ─── Social (5) ─────────────────────────────────────────────────
    (1, N'SOC-NITAQAT', 2025, N'SOCIAL', N'SAUDIZATION',
     N'Saudization rate (Nitaqat)',
     N'نسبة السعودة (نطاقات)',
     N'Saudi nationals as % of total workforce. Platinum-tier threshold is 39% for our sector.',
     N'المواطنون السعوديون كنسبة من إجمالي العاملين. عتبة الفئة البلاتينية 39% لقطاعنا.',
     N'%', 38.0, 2025, 41.5, '2026-03-31',
     45.0, 2030, N'ACTIVE',
     N'Mohammed Al-Sulaiman (HR Director)', N'm.alsulaiman@tss.sa',
     N'HR', N'HRSD 2025 Q4'),

    (1, N'SOC-FEMALE-PCT', 2025, N'SOCIAL', N'GENDER',
     N'Female workforce participation',
     N'مشاركة المرأة في القوى العاملة',
     N'Share of female employees across the organisation.',
     N'حصة الموظفات في المنشأة.',
     N'%', 22.0, 2025, 27.0, '2026-03-31',
     35.0, 2030, N'ACTIVE',
     N'Mohammed Al-Sulaiman (HR Director)', N'm.alsulaiman@tss.sa',
     N'HR', N'HR diversity 2025'),

    (1, N'SOC-LTIFR', 2025, N'SOCIAL', N'SAFETY',
     N'Lost-time injury frequency rate',
     N'معدّل تكرار الإصابات المُفقِدة للوقت',
     N'LTIFR = (lost-time injuries × 1,000,000) / total hours worked. Lower is better.',
     N'LTIFR = (الإصابات المُفقِدة للوقت × 1,000,000) / إجمالي ساعات العمل. الأقل أفضل.',
     N'per 1M hrs', 2.4, 2025, 1.8, '2026-03-31',
     1.0, 2030, N'ACTIVE',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'HSSE', N'HSSE report 2025'),

    (1, N'SOC-TRAINING-HRS', 2025, N'SOCIAL', N'TRAINING',
     N'Training hours per employee',
     N'ساعات التدريب لكل موظف',
     N'Average training hours per employee across the year.',
     N'متوسط ساعات التدريب لكل موظف خلال السنة.',
     N'hrs/emp', 18, 2025, 24, '2026-03-31',
     40, 2030, N'ACTIVE',
     N'Mohammed Al-Sulaiman (HR Director)', N'm.alsulaiman@tss.sa',
     N'L&D', N'L&D dashboard 2025'),

    (1, N'SOC-LOCAL-PROC', 2025, N'SOCIAL', N'PROCUREMENT',
     N'Local procurement share',
     N'حصة المشتريات المحلية',
     N'Share of procurement spend with Saudi-domiciled suppliers (Vision 2030 alignment).',
     N'حصة إنفاق المشتريات لدى مورّدين سعوديين (محاذاة مع رؤية 2030).',
     N'%', 58.0, 2025, 67.0, '2026-03-31',
     75.0, 2030, N'ACTIVE',
     N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     N'Procurement', N'Procurement scorecard 2025'),

    -- ─── Governance (5) ─────────────────────────────────────────────
    (1, N'GOV-BOARD-IND', 2025, N'GOVERNANCE', N'BOARD',
     N'Board independence ratio',
     N'نسبة استقلالية المجلس',
     N'Share of independent directors on the board.',
     N'حصة الأعضاء المستقلّين في المجلس.',
     N'%', 33.0, 2025, 44.0, '2026-03-31',
     50.0, 2030, N'ACTIVE',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Corporate Affairs', N'Board charter v3.0'),

    (1, N'GOV-POL-APPROVED', 2025, N'GOVERNANCE', N'POLICY',
     N'ESG policies approved',
     N'سياسات الاستدامة المعتمدة',
     N'Number of board-approved ESG policies (Code of Conduct, Whistleblowing, Climate, Diversity, etc.).',
     N'عدد سياسات الاستدامة المعتمدة من المجلس (ميثاق السلوك، الإبلاغ عن المخالفات، المناخ، التنوّع، إلخ).',
     N'count', 4, 2025, 7, '2026-03-31',
     10, 2030, N'ACTIVE',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Corporate Affairs', N'Policy register 2025'),

    (1, N'GOV-ANTICOR-TRN', 2025, N'GOVERNANCE', N'ETHICS',
     N'Anti-corruption training coverage',
     N'تغطية تدريب مكافحة الفساد',
     N'Share of in-scope employees who completed annual anti-corruption training.',
     N'حصة الموظفين المعنيين الذين أكملوا تدريب مكافحة الفساد السنوي.',
     N'%', 78.0, 2025, 92.0, '2026-03-31',
     100.0, 2030, N'ACTIVE',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Compliance & Ethics', N'Ethics training 2025'),

    (1, N'GOV-WHISTLE-CASES', 2025, N'GOVERNANCE', N'WHISTLEBLOWING',
     N'Whistleblowing cases resolved within SLA',
     N'حالات الإبلاغ عن المخالفات المعالَجة ضمن SLA',
     N'Share of whistleblowing cases resolved within the 30-day SLA.',
     N'حصة حالات الإبلاغ عن المخالفات المعالَجة ضمن SLA لـ 30 يوماً.',
     N'%', 65.0, 2025, 82.0, '2026-03-31',
     95.0, 2030, N'ACTIVE',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Compliance & Ethics', N'SpeakUp 2025 report'),

    (1, N'GOV-AUDIT-COV', 2025, N'GOVERNANCE', N'AUDIT',
     N'Internal-audit ESG coverage',
     N'تغطية المراجعة الداخلية للاستدامة',
     N'Share of in-scope ESG processes covered by an internal-audit engagement this year.',
     N'حصة عمليات الاستدامة المعنية المُغطّاة بمهمّة مراجعة داخلية خلال السنة.',
     N'%', 30.0, 2025, 55.0, '2026-03-31',
     80.0, 2030, N'ACTIVE',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Internal Audit', N'IA plan 2025');
END
GO

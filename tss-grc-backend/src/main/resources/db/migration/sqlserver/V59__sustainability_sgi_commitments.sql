-- V59 — Saudi Green Initiative Commitments (Sustainability · slice 5)
--
-- Promotes the 7 canonical SGI commitments from static frontend constants
-- (esg.ts/SGI_COMMITMENTS) to a database-backed entity. Adds owner +
-- evidence + reassessment narrative + auditable lifecycle so SGI work
-- joins the rest of the sustainability stack.
--
-- Composition with V58 (Corrective Actions): an at-risk SGI commitment
-- raises a CA-SUST-* record via source_type='SGI_COMMITMENT' /
-- source_ref=commitment_code. CA-SUST-008 in the V58 seed already targets
-- SGI-NETZERO-2060 — that link will now resolve to a real row.
--
-- Status machine:
--   ACTIVE → ACHIEVED   (terminal — target met)
--   ACTIVE → RETIRED    (terminal — commitment dropped or replaced)
--
-- Progress tier (computed by the service from baseline/current/target +
-- target_year/baseline_year):
--   ON_TRACK   — progress_pct >= expected_pct
--   AT_RISK    — progress_pct in [0.7 × expected, expected)
--   DELAYED    — progress_pct < 0.7 × expected
-- The service handles both increase-to-target ("50% renewable by 2030")
-- and decrease-to-target ("25% water intensity reduction by 2030") by
-- looking at sign(target - baseline).
--
-- Service-layer rules:
--   Rule A — ACHIEVED requires current_value to meet/exceed target
--            (within direction).
--   Rule B — RETIRED requires retired_reason.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'sgi_commitment' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.sgi_commitment (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        commitment_code          NVARCHAR(40)  NOT NULL,

        -- ENERGY | INDUSTRY | TRANSPORT | WASTE | FORESTRY | WATER | LAND | OTHER
        sector                   NVARCHAR(30)  NOT NULL,

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,
        description_en           NVARCHAR(MAX) NULL,
        description_ar           NVARCHAR(MAX) NULL,

        baseline_value           DECIMAL(20,4) NULL,
        baseline_year            SMALLINT      NULL,
        current_value            DECIMAL(20,4) NULL,
        current_value_as_of      DATE          NULL,
        target_value             DECIMAL(20,4) NOT NULL,
        target_year              SMALLINT      NOT NULL,
        unit                     NVARCHAR(40)  NOT NULL,

        -- ACTIVE | ACHIEVED | RETIRED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',

        -- Owner + cadence ────────────────────────────────────────
        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,
        next_milestone_date      DATE          NULL,
        next_milestone_en        NVARCHAR(500) NULL,
        next_milestone_ar        NVARCHAR(500) NULL,

        -- Reassessment narrative — what's driving / blocking progress
        notes_en                 NVARCHAR(MAX) NULL,
        notes_ar                 NVARCHAR(MAX) NULL,

        evidence_ref             NVARCHAR(255) NULL,

        achieved_at              DATETIME2     NULL,
        achieved_by              NVARCHAR(255) NULL,
        retired_at               DATETIME2     NULL,
        retired_by               NVARCHAR(255) NULL,
        retired_reason           NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_sgi_status CHECK (status IN (N'ACTIVE', N'ACHIEVED', N'RETIRED')),
        CONSTRAINT ck_sgi_sector CHECK (sector IN (
            N'ENERGY', N'INDUSTRY', N'TRANSPORT', N'WASTE',
            N'FORESTRY', N'WATER', N'LAND', N'OTHER')),
        CONSTRAINT ck_sgi_year   CHECK (target_year >= 2020 AND target_year <= 2100)
    );

    CREATE UNIQUE INDEX uq_sgi_org_code     ON sust.sgi_commitment(organisation_id, commitment_code)
        WHERE is_deleted = 0;
    CREATE INDEX        ix_sgi_org_status   ON sust.sgi_commitment(organisation_id, status);
    CREATE INDEX        ix_sgi_org_sector   ON sust.sgi_commitment(organisation_id, sector);
END
GO

-- ─── Seed — 7 canonical SGI commitments ─────────────────────────────
-- Values mirror the frontend's SGI_COMMITMENTS so the SGI dashboard tile
-- carries identical numbers on first load. Baseline year is 2025 (current
-- reporting cycle). current_value_as_of is the Q1-2026 close date.

IF NOT EXISTS (SELECT 1 FROM sust.sgi_commitment WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.sgi_commitment (
        organisation_id, commitment_code, sector,
        title_en, title_ar,
        baseline_value, baseline_year, current_value, current_value_as_of,
        target_value, target_year, unit,
        status, owner_name, owner_email,
        next_milestone_date, next_milestone_en, next_milestone_ar,
        notes_en, notes_ar, evidence_ref
    )
    VALUES
    (1, N'SGI-NETZERO-2060', N'ENERGY',
     N'Net-zero greenhouse gases by 2060',
     N'الحياد الكربوني بحلول 2060',
     195610.0000, 2025, 195610.0000, '2026-03-31',
     0.0000, 2060, N'tCO2e/yr',
     N'ACTIVE', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     '2026-06-30',
     N'Publish 2030 interim milestone (-35% vs 2025 baseline) in next annex version',
     N'نشر هدف 2030 المرحلي (-35% مقارنة بخط أساس 2025) في الإصدار التالي من الملحق',
     N'Aligned to Saudi Green Initiative national target. Renewable PPA programme + grid-decarbonisation reductions on track for the 2030 interim milestone (linked to CA-SUST-008).',
     N'متّسق مع هدف المبادرة الخضراء الوطني. برنامج اتفاقيات شراء الطاقة المتجدّدة وخفض الانبعاثات على المسار للهدف المرحلي 2030 (مرتبط بـ CA-SUST-008).',
     N'SGI-NetZero-2060-trajectory.xlsx'),

    (1, N'SGI-30PCT-2030', N'ENERGY',
     N'Cut Scope 1+2 emissions 30% by 2030',
     N'خفض انبعاثات النطاقين 1 و 2 بنسبة 30٪ بحلول 2030',
     60300.0000, 2025, 53130.0000, '2026-03-31',
     42210.0000, 2030, N'tCO2e/yr',
     N'ACTIVE', N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     '2026-06-30',
     N'Sign 12-month diesel→EV fleet conversion contract for the next 80 light vehicles',
     N'توقيع عقد تحويل 80 مركبة خفيفة من الديزل إلى الكهربائية خلال 12 شهراً',
     N'Behind plan — Scope 2 reduction outpaced expectations but Scope 1 fleet electrification slower than baseline. Tracking 39.6% to target.',
     N'متأخّر عن الخطّة — انخفاض النطاق 2 سَبَق المتوقَّع لكن كهربة أسطول النطاق 1 أبطأ من المتوقّع. التتبّع 39.6% من الهدف.',
     N'SGI-30pct-Q1-2026.pdf'),

    (1, N'SGI-RENEWABLE-50', N'ENERGY',
     N'50% renewable energy mix by 2030',
     N'50٪ مزيج طاقة متجدّدة بحلول 2030',
     8.0000, 2025, 22.0000, '2026-03-31',
     50.0000, 2030, N'%',
     N'ACTIVE', N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     '2026-09-30',
     N'Sudair PV plant offtake go-live — adds 8% renewable share',
     N'تشغيل شراء طاقة محطة سدير الشمسية — يضيف 8% من حصة المتجدّدة',
     N'Solar PPA signed at three production sites. Sudair PV plant offtake adds 8% expected by Q3 2026.',
     N'وُقّعت اتفاقيات الطاقة الشمسية في ثلاثة مواقع إنتاج. شراء طاقة محطة سدير الشمسية يضيف 8% بحلول الربع الثالث 2026.',
     N'Sudair-PPA-2026.pdf'),

    (1, N'SGI-PLANTING-10B', N'FORESTRY',
     N'Contribute to the 10-billion-trees national target',
     N'المساهمة في هدف 10 مليارات شجرة الوطني',
     0.0000, 2025, 124000.0000, '2026-03-31',
     500000.0000, 2030, N'trees',
     N'ACTIVE', N'Mohammed Al-Sulaiman (Sustainability Lead)', N'm.alsulaiman@tss.sa',
     '2026-10-15',
     N'Q4 2026 planting cycle — Red Sea coast restoration phase 2',
     N'دورة الزراعة للربع الرابع 2026 — المرحلة الثانية لاستعادة ساحل البحر الأحمر',
     N'Native-species planting on coast restoration and corporate-campus rewilding programmes. Phase 1 (Riyadh) complete; Phase 2 (Red Sea) in planning.',
     N'زراعة أصناف محلية ضمن استعادة الساحل وإعادة تأهيل الحرم المؤسسي. المرحلة 1 (الرياض) مكتملة؛ المرحلة 2 (البحر الأحمر) قيد التخطيط.',
     N'NCVC-planting-permit-2026.pdf'),

    (1, N'SGI-EV-FLEET-2030', N'TRANSPORT',
     N'Convert 30% of light-vehicle fleet to EV by 2030',
     N'تحويل 30٪ من أسطول المركبات الخفيفة إلى كهربائي بحلول 2030',
     0.0000, 2025, 12.0000, '2026-03-31',
     30.0000, 2030, N'%',
     N'ACTIVE', N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     '2026-07-31',
     N'EV charging deployment at 8 additional sites — completes Tier-1 site coverage',
     N'نشر شحن المركبات الكهربائية في 8 مواقع إضافية — يكتمل غطاء مواقع المستوى 1',
     N'EV charging deployed at 18 sites. Lucid + BYD procurement framework in place; first 40 vehicles delivered, next 80 in 12-month contract (linked to SGI-30PCT-2030 milestone).',
     N'نُشرت محطات شحن المركبات الكهربائية في 18 موقعاً. إطار شراء Lucid وBYD معتمد؛ سُلّمت أول 40 مركبة، 80 إضافية في عقد 12 شهراً (مرتبط بهدف SGI-30PCT-2030 المرحلي).',
     N'EV-fleet-Q1-2026.xlsx'),

    (1, N'SGI-WASTE-DIVERT-2030', N'WASTE',
     N'60% non-hazardous waste diverted from landfill by 2030',
     N'تحويل 60٪ من النفايات غير الخطرة بعيداً عن المكبّ بحلول 2030',
     18.0000, 2025, 38.0000, '2026-03-31',
     60.0000, 2030, N'%',
     N'ACTIVE', N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     '2026-08-31',
     N'Onboard the remaining 4 production sites onto the source-separation programme',
     N'إدخال المواقع الإنتاجية الأربعة المتبقّية في برنامج الفصل من المصدر',
     N'On-site segregation programme + GAMEP partnership covering 14 sites. Remaining 4 sites scheduled for Q3 2026.',
     N'برنامج الفصل في الموقع + شراكة الجمعية السعودية للحفاظ على البيئة تشمل 14 موقعاً. المواقع الأربعة المتبقّية مجدولة للربع الثالث 2026.',
     N'GAMEP-MOU-2025.pdf'),

    (1, N'SGI-WATER-INTENSITY', N'WATER',
     N'Reduce water intensity 25% by 2030',
     N'خفض كثافة استهلاك المياه 25٪ بحلول 2030',
     4.2000, 2025, 3.7000, '2026-03-31',
     3.1500, 2030, N'm³/SAR1k revenue',
     N'ACTIVE', N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     '2026-09-30',
     N'Closed-loop cooling rollout — fourth production plant',
     N'تطبيق التبريد بالدائرة المغلقة — المصنع الإنتاجي الرابع',
     N'Closed-loop cooling at three plants + recycled-water integration for landscaping. Targeting fourth plant in Q3 2026.',
     N'تبريد دائري مغلق في ثلاثة مصانع + استخدام المياه المُعاد تدويرها في التنسيق. استهداف المصنع الرابع في الربع الثالث 2026.',
     N'Water-intensity-Q1-2026.xlsx');
END
GO

-- V186 — TCFD Climate Scenarios + Scope-3 Financed Emissions (Sprint 11).
--
-- Saudi listed banks face a tri-track climate-disclosure surface starting
-- FY2025:
--   • CMA Implementing Regulations (2023) — IFRS S2 climate-related
--     disclosures mandatory for listed entities from FY2025.
--   • SAMA Sustainable Finance Roadmap (2024) — TCFD recommendations
--     baked into prudential supervision; scenario analysis required for
--     material climate risks (transition + physical).
--   • TADAWUL ESG disclosure index — part of the Saudi Exchange's ESG
--     scoring; scenarios + financed emissions are observed inputs.
--   • PCAF (Partnership for Carbon Accounting Financials) — the global
--     methodology for measuring Scope-3 Category 15 (Financed Emissions).
--     SAMA Sustainable Finance Working Group has aligned member banks to
--     PCAF.
--
-- Four tables under the existing `sust` schema:
--
--   sust.tcfd_climate_scenario        ← scenario header (ORDERLY /
--                                       DISORDERLY / HOT_HOUSE / CUSTOM)
--   sust.tcfd_sector_impact           ← GICS sector exposure under a
--                                       given scenario (transition +
--                                       physical risk scores + PD uplift)
--   sust.financed_emissions_record    ← PCAF asset-class-level row per
--                                       reporting year (Scope-1/2/3 ×
--                                       attribution factor)
--   sust.financed_emissions_target    ← Net-zero / interim reduction
--                                       commitments (SBTI / NZBA frameworks)
--
-- Org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for the whole script — SQL Server requires it for
-- filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'sust') IS NULL EXEC(N'CREATE SCHEMA sust');
GO

-- ─── 1. TCFD climate scenario header ───────────────────────────────
IF OBJECT_ID(N'sust.tcfd_climate_scenario', N'U') IS NULL
BEGIN
    CREATE TABLE sust.tcfd_climate_scenario (
        id                              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id                 BIGINT NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        scenario_code                   NVARCHAR(64)  NOT NULL,
        title_en                        NVARCHAR(255) NOT NULL,
        title_ar                        NVARCHAR(255) NULL,
        description_en                  NVARCHAR(MAX) NULL,
        description_ar                  NVARCHAR(MAX) NULL,

        -- ORDERLY / DISORDERLY / HOT_HOUSE / CUSTOM (per NGFS taxonomy).
        scenario_type                   VARCHAR(20)   NOT NULL,
        -- Reference framework: NGFS / IEA_NZE_2050 / IPCC_RCP_*
        --                     / IFRS_S2 / CUSTOM.
        reference_framework             VARCHAR(40)   NOT NULL,
        time_horizon_years              INT           NOT NULL,
        global_warming_celsius          DECIMAL(4,2)  NOT NULL,

        assumptions_json                NVARCHAR(MAX) NULL,
        portfolio_at_risk_sar_billions  DECIMAL(18,2) NULL,
        expected_loss_sar_millions      DECIMAL(18,2) NULL,
        probability_weight              DECIMAL(4,3)  NOT NULL DEFAULT 0,

        lifecycle_stage                 VARCHAR(20)   NOT NULL DEFAULT N'DRAFT',
        approved_by_user_id             BIGINT        NULL,
        approved_at                     DATETIME2(3)  NULL,

        is_deleted                      BIT           NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2(3)  NULL,
        deleted_by                      BIGINT        NULL,
        created_at                      DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      BIGINT        NULL,
        updated_at                      DATETIME2(3)  NULL,
        updated_by                      BIGINT        NULL,
        row_version                     ROWVERSION,

        CONSTRAINT CK_tcfd_scn_type CHECK (scenario_type IN
            (N'ORDERLY', N'DISORDERLY', N'HOT_HOUSE', N'CUSTOM')),
        CONSTRAINT CK_tcfd_scn_framework CHECK (reference_framework IN
            (N'NGFS', N'IEA_NZE_2050', N'IPCC_RCP_2_6',
             N'IPCC_RCP_4_5', N'IPCC_RCP_8_5', N'IFRS_S2', N'CUSTOM')),
        CONSTRAINT CK_tcfd_scn_stage CHECK (lifecycle_stage IN
            (N'DRAFT', N'IN_REVIEW', N'BOARD_APPROVED', N'ARCHIVED')),
        CONSTRAINT CK_tcfd_scn_weight CHECK (probability_weight >= 0 AND probability_weight <= 1),
        CONSTRAINT CK_tcfd_scn_horizon CHECK (time_horizon_years > 0),
        CONSTRAINT CK_tcfd_scn_warming CHECK (global_warming_celsius >= 0)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_tcfd_scn_code' AND object_id = OBJECT_ID(N'sust.tcfd_climate_scenario'))
    CREATE UNIQUE INDEX UQ_tcfd_scn_code ON sust.tcfd_climate_scenario (organisation_id, scenario_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_tcfd_scn_stage' AND object_id = OBJECT_ID(N'sust.tcfd_climate_scenario'))
    CREATE INDEX IX_tcfd_scn_stage ON sust.tcfd_climate_scenario (organisation_id, lifecycle_stage, scenario_type) WHERE is_deleted = 0;
GO

-- ─── 2. TCFD sector impact ─────────────────────────────────────────
IF OBJECT_ID(N'sust.tcfd_sector_impact', N'U') IS NULL
BEGIN
    CREATE TABLE sust.tcfd_sector_impact (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        scenario_id                 BIGINT NOT NULL,

        sector_code                 VARCHAR(20)   NOT NULL,
        sector_name_en              NVARCHAR(255) NOT NULL,
        sector_name_ar              NVARCHAR(255) NULL,

        exposure_sar_millions       DECIMAL(18,2) NOT NULL,
        transition_risk_score       DECIMAL(4,2)  NOT NULL,
        physical_risk_score         DECIMAL(4,2)  NOT NULL,
        expected_pd_uplift_bps      INT           NOT NULL DEFAULT 0,
        mitigant_actions_en         NVARCHAR(1000) NULL,
        mitigant_actions_ar         NVARCHAR(1000) NULL,

        is_deleted                  BIT           NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2(3)  NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2(3)  NULL,
        updated_by                  BIGINT        NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_tcfd_sector_scn FOREIGN KEY (scenario_id)
            REFERENCES sust.tcfd_climate_scenario(id),
        CONSTRAINT CK_tcfd_sector_trans CHECK (transition_risk_score >= 0 AND transition_risk_score <= 100),
        CONSTRAINT CK_tcfd_sector_phys  CHECK (physical_risk_score   >= 0 AND physical_risk_score   <= 100)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_tcfd_sector_scn_code' AND object_id = OBJECT_ID(N'sust.tcfd_sector_impact'))
    CREATE UNIQUE INDEX UQ_tcfd_sector_scn_code ON sust.tcfd_sector_impact (scenario_id, sector_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_tcfd_sector_org' AND object_id = OBJECT_ID(N'sust.tcfd_sector_impact'))
    CREATE INDEX IX_tcfd_sector_org ON sust.tcfd_sector_impact (organisation_id, scenario_id) WHERE is_deleted = 0;
GO

-- ─── 3. Financed emissions record (PCAF asset-class row) ──────────
IF OBJECT_ID(N'sust.financed_emissions_record', N'U') IS NULL
BEGIN
    CREATE TABLE sust.financed_emissions_record (
        id                                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id                   BIGINT NOT NULL,
        external_id                       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        reporting_year                    INT          NOT NULL,
        reporting_period                  VARCHAR(10)  NOT NULL DEFAULT N'ANNUAL',

        asset_class                       VARCHAR(40)  NOT NULL,
        asset_class_label_en              NVARCHAR(255) NOT NULL,
        asset_class_label_ar              NVARCHAR(255) NULL,

        outstanding_amount_sar_millions   DECIMAL(18,2) NOT NULL,
        attribution_factor                DECIMAL(8,6)  NOT NULL,
        data_quality_score                INT           NOT NULL,

        emissions_scope1_tco2e            DECIMAL(18,2) NOT NULL DEFAULT 0,
        emissions_scope2_tco2e            DECIMAL(18,2) NOT NULL DEFAULT 0,
        emissions_scope3_tco2e            DECIMAL(18,2) NOT NULL DEFAULT 0,
        total_financed_emissions_tco2e    DECIMAL(18,2) NOT NULL DEFAULT 0,

        methodology_en                    NVARCHAR(1000) NULL,
        methodology_ar                    NVARCHAR(1000) NULL,

        lifecycle_stage                   VARCHAR(20)   NOT NULL DEFAULT N'DRAFT',

        is_deleted                        BIT           NOT NULL DEFAULT 0,
        deleted_at                        DATETIME2(3)  NULL,
        deleted_by                        BIGINT        NULL,
        created_at                        DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                        BIGINT        NULL,
        updated_at                        DATETIME2(3)  NULL,
        updated_by                        BIGINT        NULL,
        row_version                       ROWVERSION,

        CONSTRAINT CK_fe_period CHECK (reporting_period IN
            (N'ANNUAL', N'Q1', N'Q2', N'Q3', N'Q4')),
        CONSTRAINT CK_fe_asset_class CHECK (asset_class IN
            (N'LISTED_EQUITY', N'CORPORATE_BONDS', N'BUSINESS_LOANS',
             N'PROJECT_FINANCE', N'COMMERCIAL_REAL_ESTATE', N'MORTGAGES',
             N'MOTOR_VEHICLE_LOANS', N'SOVEREIGN_DEBT')),
        CONSTRAINT CK_fe_quality CHECK (data_quality_score BETWEEN 1 AND 5),
        CONSTRAINT CK_fe_attribution CHECK (attribution_factor >= 0 AND attribution_factor <= 1),
        CONSTRAINT CK_fe_stage CHECK (lifecycle_stage IN
            (N'DRAFT', N'IN_REVIEW', N'APPROVED', N'PUBLISHED', N'ARCHIVED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_fe_year_class_period' AND object_id = OBJECT_ID(N'sust.financed_emissions_record'))
    CREATE UNIQUE INDEX UQ_fe_year_class_period ON sust.financed_emissions_record (organisation_id, reporting_year, reporting_period, asset_class) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_fe_year_stage' AND object_id = OBJECT_ID(N'sust.financed_emissions_record'))
    CREATE INDEX IX_fe_year_stage ON sust.financed_emissions_record (organisation_id, reporting_year, lifecycle_stage) WHERE is_deleted = 0;
GO

-- ─── 4. Financed emissions target (net-zero / interim) ────────────
IF OBJECT_ID(N'sust.financed_emissions_target', N'U') IS NULL
BEGIN
    CREATE TABLE sust.financed_emissions_target (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        target_code                 NVARCHAR(64)  NOT NULL,
        title_en                    NVARCHAR(255) NOT NULL,
        title_ar                    NVARCHAR(255) NULL,

        target_year                 INT           NOT NULL,
        baseline_year               INT           NOT NULL,
        baseline_emissions_tco2e    DECIMAL(18,2) NOT NULL,
        target_emissions_tco2e      DECIMAL(18,2) NOT NULL,
        reduction_percent           DECIMAL(5,2)  NOT NULL,

        sbti_aligned                BIT           NOT NULL DEFAULT 0,
        alignment_framework         VARCHAR(40)   NOT NULL,

        is_deleted                  BIT           NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2(3)  NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2(3)  NULL,
        updated_by                  BIGINT        NULL,
        row_version                 ROWVERSION,

        CONSTRAINT CK_fe_target_framework CHECK (alignment_framework IN
            (N'SBTI_1_5C', N'SBTI_WELL_BELOW_2C', N'NZBA', N'CUSTOM')),
        CONSTRAINT CK_fe_target_years CHECK (target_year > baseline_year)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_fe_target_code' AND object_id = OBJECT_ID(N'sust.financed_emissions_target'))
    CREATE UNIQUE INDEX UQ_fe_target_code ON sust.financed_emissions_target (organisation_id, target_code) WHERE is_deleted = 0;
GO

-- ─── 5. Seed data — 3 TCFD scenarios + 18 sector impacts (org=1) ──
-- Saudi-native AR throughout — assumes a Saudi bank reading IFRS S2 in
-- Arabic. NGFS framework + IEA NZE 2050 references where appropriate.

-- TCFD-2026-ORDERLY — 1.5°C, immediate carbon-price ramp.
IF NOT EXISTS (SELECT 1 FROM sust.tcfd_climate_scenario WHERE organisation_id = 1 AND scenario_code = N'TCFD-2026-ORDERLY' AND is_deleted = 0)
INSERT INTO sust.tcfd_climate_scenario
    (organisation_id, scenario_code, title_en, title_ar,
     description_en, description_ar,
     scenario_type, reference_framework, time_horizon_years, global_warming_celsius,
     assumptions_json, portfolio_at_risk_sar_billions, expected_loss_sar_millions,
     probability_weight, lifecycle_stage)
VALUES
    (1, N'TCFD-2026-ORDERLY',
     N'Orderly net-zero by 2050 (NGFS Orderly)',
     N'انتقال منظَّم نحو الحياد الكربوني بحلول 2050',
     N'NGFS Orderly scenario: immediate, predictable carbon-price ramp starting 2026 (USD 80/tCO2e rising to USD 200 by 2030). Aligned with Vision 2030 + Saudi Green Initiative. Largest exposure: hydrocarbon-linked corporate book repricing; partially offset by renewables project finance pipeline.',
     N'سيناريو NGFS المنظَّم: ارتفاع متوقَّع وفوري في تسعيرة الكربون يبدأ من 2026 (80 دولار للطن مرتفعاً إلى 200 دولار بحلول 2030). متوافق مع رؤية المملكة 2030 والمبادرة السعودية الخضراء. أكبر التعرّضات: إعادة تسعير محفظة الشركات الهيدروكربونية، يقابلها جزئياً خط تمويل مشاريع الطاقة المتجدّدة.',
     N'ORDERLY', N'NGFS', 30, 1.50,
     N'{"carbon_price_2026_usd_per_tco2e": 80, "carbon_price_2030_usd_per_tco2e": 200, "renewable_share_2030": 0.50, "policy_path": "immediate_predictable"}',
     142.50, 1850.00, 0.500, N'BOARD_APPROVED');
GO

-- TCFD-2026-DISORDERLY — 2.5°C, delayed transition.
IF NOT EXISTS (SELECT 1 FROM sust.tcfd_climate_scenario WHERE organisation_id = 1 AND scenario_code = N'TCFD-2026-DISORDERLY' AND is_deleted = 0)
INSERT INTO sust.tcfd_climate_scenario
    (organisation_id, scenario_code, title_en, title_ar,
     description_en, description_ar,
     scenario_type, reference_framework, time_horizon_years, global_warming_celsius,
     assumptions_json, portfolio_at_risk_sar_billions, expected_loss_sar_millions,
     probability_weight, lifecycle_stage)
VALUES
    (1, N'TCFD-2026-DISORDERLY',
     N'Disorderly delayed transition (NGFS Delayed)',
     N'انتقال غير منظَّم ومتأخّر',
     N'NGFS Delayed transition: policy action postponed to 2030, then a sharp catch-up. Carbon price jumps to USD 280 by 2032 — stranded-asset shock on legacy upstream loans + sudden repricing of high-emission corporate bonds. Saudi sovereign + sovereign-linked exposure cushioned by NDC commitments + PIF green facility.',
     N'انتقال NGFS المتأخّر: تأجيل التدخّل السياسي إلى 2030 ثم لحاق حادّ. ترتفع تسعيرة الكربون إلى 280 دولار للطن بحلول 2032 — صدمة الأصول العالقة على قروض الاستخراج التقليدية وإعادة تسعير مفاجئة لسندات الشركات عالية الانبعاثات. تعرّضنا السيادي والمرتبط بالسيادي مُخفَّف بمساهمات المملكة المحدَّدة وطنياً وتسهيل صندوق الاستثمارات العامة الأخضر.',
     N'DISORDERLY', N'NGFS', 30, 2.50,
     N'{"carbon_price_2030_usd_per_tco2e": 90, "carbon_price_2032_usd_per_tco2e": 280, "stranded_asset_haircut": 0.35, "policy_path": "delayed_then_sharp"}',
     198.75, 4250.00, 0.350, N'BOARD_APPROVED');
GO

-- TCFD-2026-HOTHOUSE — 3.5°C+, business-as-usual.
IF NOT EXISTS (SELECT 1 FROM sust.tcfd_climate_scenario WHERE organisation_id = 1 AND scenario_code = N'TCFD-2026-HOTHOUSE' AND is_deleted = 0)
INSERT INTO sust.tcfd_climate_scenario
    (organisation_id, scenario_code, title_en, title_ar,
     description_en, description_ar,
     scenario_type, reference_framework, time_horizon_years, global_warming_celsius,
     assumptions_json, portfolio_at_risk_sar_billions, expected_loss_sar_millions,
     probability_weight, lifecycle_stage)
VALUES
    (1, N'TCFD-2026-HOTHOUSE',
     N'Hot-house world — business as usual (NGFS Current Policies)',
     N'عالَم الاحتباس الحراري — استمرار السياسات الحالية',
     N'NGFS Current Policies: no new policy. By 2050 global warming reaches 3.5°C+. Transition risk minimal but physical risk dominates: extreme heat days in Riyadh/Jeddah, coastal infrastructure exposure in Jubail/Yanbu, Red Sea bleaching impacting tourism megaprojects (NEOM, Red Sea Global). Mortgage book exposure to cooling-load + insurability shifts.',
     N'سياسات NGFS الحالية: لا سياسات جديدة. الاحترار العالمي يبلغ 3.5 درجة مئوية بحلول 2050. مخاطر الانتقال منخفضة لكن المخاطر المادّية تهيمن: أيام شديدة الحرارة في الرياض وجدة، تعرّض البنية التحتية الساحلية في الجبيل وينبع، تبييض الشعاب في البحر الأحمر يؤثّر على المشاريع السياحية الكبرى (نيوم، البحر الأحمر العالمية). تعرّض محفظة الرهن العقاري لأحمال التبريد وتحوّلات قابلية التأمين.',
     N'HOT_HOUSE', N'NGFS', 30, 3.50,
     N'{"physical_loss_pct_gdp": 0.045, "heat_days_riyadh_2050": 165, "coastal_assets_at_risk_pct": 0.18, "policy_path": "no_new_policy"}',
     176.30, 3120.00, 0.150, N'BOARD_APPROVED');
GO

-- ─── 6. Sector impacts — 6 sectors × 3 scenarios = 18 rows ────────
-- Sectors: ENERGY, MATERIALS, INDUSTRIALS, REAL_ESTATE, UTILITIES, FINANCIALS.

-- ORDERLY × 6 sectors.
INSERT INTO sust.tcfd_sector_impact
    (organisation_id, scenario_id, sector_code, sector_name_en, sector_name_ar,
     exposure_sar_millions, transition_risk_score, physical_risk_score,
     expected_pd_uplift_bps, mitigant_actions_en, mitigant_actions_ar)
SELECT 1, s.id, v.sector_code, v.sector_name_en, v.sector_name_ar,
       v.exposure, v.trans_score, v.phys_score, v.pd_uplift,
       v.mit_en, v.mit_ar
FROM sust.tcfd_climate_scenario s
CROSS JOIN (VALUES
    (N'ENERGY',      N'Energy',       N'الطاقة',
     45000.00, 78.00, 22.00, 145,
     N'Transition financing facility for hydrocarbon clients toward CCS + hydrogen; engagement track with ARAMCO sustainability office.',
     N'تسهيل تمويل الانتقال لعملاء الهيدروكربون نحو احتجاز الكربون والهيدروجين، مع مسار حوار مع مكتب أرامكو للاستدامة.'),
    (N'MATERIALS',   N'Materials',    N'المواد الأساسية',
     18500.00, 62.00, 35.00, 95,
     N'Cement + petrochemicals — engage SABIC and Yamama on Scope-1 reduction roadmaps; tie pricing to verified emission cuts.',
     N'الإسمنت والبتروكيماويات — حوار مع سابك واليمامة على خرائط طريق خفض النطاق الأول، مع ربط التسعير بالخفض المُتحقَّق.'),
    (N'INDUSTRIALS', N'Industrials',  N'الصناعات',
     12300.00, 45.00, 40.00, 65,
     N'Energy-efficiency CapEx loan product; targeted at MODON tenants — partial subsidy via MISA green industry scheme.',
     N'منتج إقراض كفاءة الطاقة الرأسمالية، موجَّه لمستأجري مدن، بدعم جزئي عبر برنامج الصناعة الخضراء لوزارة الاستثمار.'),
    (N'REAL_ESTATE', N'Real Estate',  N'العقارات',
     32600.00, 38.00, 55.00, 70,
     N'Green mortgage pricing tiers; require Mostadam certification for new build-to-rent commitments.',
     N'شرائح تسعير الرهن العقاري الأخضر، مع اشتراط شهادة مستدام للالتزامات الجديدة في البناء بغرض الإيجار.'),
    (N'UTILITIES',   N'Utilities',    N'المرافق',
     21800.00, 70.00, 30.00, 110,
     N'Co-finance renewables pipeline with PIF + ACWA Power; phase down financing for coal-adjacent peakers (none in KSA but offshore book).',
     N'تمويل مشترك لخط الطاقة المتجدّدة مع صندوق الاستثمارات وأكوا باور، والتدرّج في إنهاء تمويل توربينات الذروة القريبة من الفحم (لا توجد في المملكة لكن في المحفظة الخارجية).'),
    (N'FINANCIALS',  N'Financials',   N'القطاع المالي',
     8200.00,  22.00, 18.00, 25,
     N'Counterparty climate-risk overlay on FI lines; align with NZBA peer banks on transition-finance KPIs.',
     N'طبقة مخاطر مناخية على خطوط الأطراف المؤسسية المالية، مع المواءمة مع البنوك النظيرة في تحالف الحياد الصافي على مؤشّرات تمويل الانتقال.')
) v(sector_code, sector_name_en, sector_name_ar,
    exposure, trans_score, phys_score, pd_uplift, mit_en, mit_ar)
WHERE s.organisation_id = 1 AND s.scenario_code = N'TCFD-2026-ORDERLY' AND s.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sust.tcfd_sector_impact x
      WHERE x.scenario_id = s.id AND x.sector_code = v.sector_code AND x.is_deleted = 0);
GO

-- DISORDERLY × 6 sectors (higher transition risk, similar physical).
INSERT INTO sust.tcfd_sector_impact
    (organisation_id, scenario_id, sector_code, sector_name_en, sector_name_ar,
     exposure_sar_millions, transition_risk_score, physical_risk_score,
     expected_pd_uplift_bps, mitigant_actions_en, mitigant_actions_ar)
SELECT 1, s.id, v.sector_code, v.sector_name_en, v.sector_name_ar,
       v.exposure, v.trans_score, v.phys_score, v.pd_uplift,
       v.mit_en, v.mit_ar
FROM sust.tcfd_climate_scenario s
CROSS JOIN (VALUES
    (N'ENERGY',      N'Energy',       N'الطاقة',
     45000.00, 92.00, 26.00, 320,
     N'Accelerate exit timetable on legacy upstream; cap exposure to non-CCS oil & gas; covenant tightening on Scope-1.',
     N'تسريع جدول الخروج من الاستخراج التقليدي، تقييد التعرّض لنفط وغاز بلا احتجاز كربون، تشديد التعهّدات على النطاق الأول.'),
    (N'MATERIALS',   N'Materials',    N'المواد الأساسية',
     18500.00, 85.00, 40.00, 220,
     N'Mandatory transition plan covenants on syndicated facilities > 500m SAR.',
     N'تعهّدات إلزامية لخطة الانتقال على التسهيلات المشتركة فوق 500 مليون ريال.'),
    (N'INDUSTRIALS', N'Industrials',  N'الصناعات',
     12300.00, 68.00, 45.00, 150,
     N'Re-rate exposures with no transition plan; offer transition-aligned product at 30bps discount.',
     N'إعادة تصنيف التعرّضات بدون خطة انتقال، تقديم منتج موائم للانتقال بخصم 30 نقطة أساس.'),
    (N'REAL_ESTATE', N'Real Estate',  N'العقارات',
     32600.00, 58.00, 60.00, 130,
     N'Retrofit-financing product; assume 25% of legacy office book needs efficiency upgrade by 2030.',
     N'منتج تمويل تطوير المباني، بافتراض أن 25% من محفظة المكاتب القديمة تحتاج رفع كفاءة بحلول 2030.'),
    (N'UTILITIES',   N'Utilities',    N'المرافق',
     21800.00, 88.00, 32.00, 240,
     N'Accelerate renewables co-finance; haircut on any non-aligned thermal generation in offshore book.',
     N'تسريع التمويل المشترك للمتجدّدة، مع تخفيض على أي توليد حراري غير موائم في المحفظة الخارجية.'),
    (N'FINANCIALS',  N'Financials',   N'القطاع المالي',
     8200.00,  45.00, 22.00, 70,
     N'Counterparty downgrade trigger when peer fails transition disclosure.',
     N'محفّز خفض تصنيف الطرف المقابل عند تخلّف النظير عن إفصاح الانتقال.')
) v(sector_code, sector_name_en, sector_name_ar,
    exposure, trans_score, phys_score, pd_uplift, mit_en, mit_ar)
WHERE s.organisation_id = 1 AND s.scenario_code = N'TCFD-2026-DISORDERLY' AND s.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sust.tcfd_sector_impact x
      WHERE x.scenario_id = s.id AND x.sector_code = v.sector_code AND x.is_deleted = 0);
GO

-- HOT_HOUSE × 6 sectors (low transition, high physical).
INSERT INTO sust.tcfd_sector_impact
    (organisation_id, scenario_id, sector_code, sector_name_en, sector_name_ar,
     exposure_sar_millions, transition_risk_score, physical_risk_score,
     expected_pd_uplift_bps, mitigant_actions_en, mitigant_actions_ar)
SELECT 1, s.id, v.sector_code, v.sector_name_en, v.sector_name_ar,
       v.exposure, v.trans_score, v.phys_score, v.pd_uplift,
       v.mit_en, v.mit_ar
FROM sust.tcfd_climate_scenario s
CROSS JOIN (VALUES
    (N'ENERGY',      N'Energy',       N'الطاقة',
     45000.00, 30.00, 65.00, 120,
     N'Adaptation finance for cooling + water resilience at upstream sites.',
     N'تمويل التكيّف لمنشآت الاستخراج للتبريد ومقاومة شُح المياه.'),
    (N'MATERIALS',   N'Materials',    N'المواد الأساسية',
     18500.00, 35.00, 72.00, 145,
     N'Water-scarcity overlay on cement clients; engage on alternative-water sourcing.',
     N'طبقة شُح المياه على عملاء الإسمنت، مع حوار حول مصادر المياه البديلة.'),
    (N'INDUSTRIALS', N'Industrials',  N'الصناعات',
     12300.00, 28.00, 68.00, 110,
     N'Heatwave business-continuity attestation as a condition for renewing working-capital lines.',
     N'إقرار استمرارية الأعمال خلال موجات الحرّ كشرط لتجديد خطوط رأس المال العامل.'),
    (N'REAL_ESTATE', N'Real Estate',  N'العقارات',
     32600.00, 25.00, 82.00, 195,
     N'Insurability check at origination; coastal-zone mortgage cap (Jeddah/Jubail/Yanbu zones).',
     N'فحص قابلية التأمين عند التأسيس، مع سقف للرهن العقاري في النطاقات الساحلية (جدة، الجبيل، ينبع).'),
    (N'UTILITIES',   N'Utilities',    N'المرافق',
     21800.00, 32.00, 70.00, 165,
     N'Grid-resilience capex line; price in extreme-heat outage frequency.',
     N'خط تمويل رأسمالي لمرونة الشبكة، مع تسعير تكرار انقطاعات الحرّ الشديد.'),
    (N'FINANCIALS',  N'Financials',   N'القطاع المالي',
     8200.00,  18.00, 38.00, 40,
     N'Inter-bank stress-test correlation on physical-risk peers.',
     N'ارتباط اختبارات الضغط بين البنوك على النظراء ذوي التعرّض للمخاطر المادّية.')
) v(sector_code, sector_name_en, sector_name_ar,
    exposure, trans_score, phys_score, pd_uplift, mit_en, mit_ar)
WHERE s.organisation_id = 1 AND s.scenario_code = N'TCFD-2026-HOTHOUSE' AND s.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sust.tcfd_sector_impact x
      WHERE x.scenario_id = s.id AND x.sector_code = v.sector_code AND x.is_deleted = 0);
GO

-- ─── 7. Financed emissions records (FY2026) — 8 asset classes ─────
-- Saudi-native: methodology in Arabic refers to PCAF, attribution
-- factor = outstanding / EVIC for listed equity & corporate bonds, =
-- outstanding / total capital employed for business loans, etc.

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'LISTED_EQUITY' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'LISTED_EQUITY',
     N'Listed equity', N'الأسهم المُدرَجة',
     6800.00, 0.024500, 2,
     85000.00, 32000.00, 145000.00, 6420.50,
     N'PCAF — attribution factor = outstanding amount / enterprise value including cash (EVIC). Issuer Scope-1 and Scope-2 reported (verified by Big-4); Scope-3 estimated from sector intensity (data quality 2 — partial reported, partial proxied).',
     N'منهجية PCAF — معامل الإسناد = المبلغ القائم على قيمة المنشأة شاملةً النقد (EVIC). يُبلِّغ المُصدِر عن النطاقين الأول والثاني (مُدقَّق من إحدى الكبار الأربع)، أمّا النطاق الثالث فمقدَّر من كثافة القطاع (جودة البيانات 2 — جزء مُبلَّغ وجزء مُقدَّر بالوكالة).',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'CORPORATE_BONDS' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'CORPORATE_BONDS',
     N'Corporate bonds', N'السندات والصكوك المؤسسية',
     14200.00, 0.018200, 2,
     420000.00, 95000.00, 680000.00, 21761.40,
     N'PCAF — attribution factor for non-listed corporate debt = outstanding / total balance sheet. Includes sukuk and conventional bonds. Saudi issuers reporting under SOCPA Sustainability Standard.',
     N'منهجية PCAF — معامل الإسناد للدين المؤسسي غير المُدرَج = القائم / إجمالي قائمة المركز المالي. يشمل الصكوك والسندات التقليدية. المُصدرون السعوديون يُبلِّغون وفق معيار الاستدامة الصادر عن الهيئة السعودية للمحاسبين القانونيين.',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'BUSINESS_LOANS' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'BUSINESS_LOANS',
     N'Business loans', N'قروض الأعمال',
     58900.00, 0.082000, 3,
     1850000.00, 420000.00, 2350000.00, 379640.00,
     N'PCAF — corporate / SME loan attribution = outstanding / total enterprise value. Data quality 3 — large clients have third-party verified inventories, smaller SME book uses sector + activity intensity proxies (NACE/ISIC).',
     N'منهجية PCAF — إسناد قروض الشركات والمنشآت = القائم / إجمالي قيمة المنشأة. جودة البيانات 3 — العملاء الكبار لديهم جرد مُتحقَّق من طرف ثالث، أمّا المنشآت الصغيرة فتستخدم كثافات القطاع والنشاط (تصنيف NACE/ISIC).',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'PROJECT_FINANCE' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'PROJECT_FINANCE',
     N'Project finance', N'تمويل المشاريع',
     22400.00, 0.450000, 1,
     780000.00, 240000.00, 95000.00, 501750.00,
     N'PCAF — project finance attribution = outstanding / total project cost. Highest data quality (1) — project-specific emissions reported and verified. Includes NEOM, Red Sea Global, Sudair PV, Yanbu Phase 3 IWP, ACWA renewables portfolio.',
     N'منهجية PCAF — إسناد تمويل المشاريع = القائم / إجمالي التكلفة الكلّية للمشروع. أعلى مستوى لجودة البيانات (1) — انبعاثات المشاريع مُبلَّغة ومُتحقَّقة. يشمل نيوم، البحر الأحمر العالمية، شمسية سدير، الفنر الثالث في ينبع، محفظة أكوا للطاقة المتجدّدة.',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'COMMERCIAL_REAL_ESTATE' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'COMMERCIAL_REAL_ESTATE',
     N'Commercial real estate', N'العقارات التجارية',
     38500.00, 0.620000, 3,
     0.00, 412000.00, 18000.00, 266600.00,
     N'PCAF — CRE attribution = outstanding / property value at origination. Scope-1 negligible (no on-site combustion in book); Scope-2 from grid electricity using SEC emission factor; Scope-3 from embodied carbon estimates.',
     N'منهجية PCAF — إسناد العقارات التجارية = القائم / قيمة العقار وقت التأسيس. النطاق الأول صفر تقريباً (لا احتراق موقعي في المحفظة)، النطاق الثاني من كهرباء الشبكة بمعامل انبعاث الشركة السعودية للكهرباء، النطاق الثالث من تقديرات الكربون المُتضمَّن في المواد.',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'MORTGAGES' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'MORTGAGES',
     N'Residential mortgages', N'قروض الرهن العقاري السكنية',
     82400.00, 0.680000, 4,
     0.00, 295000.00, 0.00, 200600.00,
     N'PCAF — mortgage attribution = outstanding / property value. Scope-2 estimated from average dwelling-size + SEC residential factor; data quality 4 (proxied from average — no per-property meter data yet). Bayan dwelling-size enrichment planned for FY2027.',
     N'منهجية PCAF — إسناد الرهن العقاري = القائم / قيمة العقار. النطاق الثاني مُقدَّر من متوسط مساحة المسكن ومعامل الشركة السعودية للكهرباء السكني، جودة البيانات 4 (مُقدَّر بالوكالة من المتوسط — لا توجد بيانات عدّاد لكل عقار حتى الآن). يخطَّط لإثراء مساحات المساكن عبر بيان في 2027.',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'MOTOR_VEHICLE_LOANS' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'MOTOR_VEHICLE_LOANS',
     N'Motor vehicle loans', N'قروض السيارات',
     14800.00, 0.580000, 3,
     142000.00, 0.00, 28000.00, 98600.00,
     N'PCAF — motor vehicle attribution = outstanding / asset value. Scope-1 from fuel consumption × average annual km × emission factor by vehicle type (sedan/SUV/pickup). Toyota and Hyundai dominate book; EV share 2.1% in FY2026.',
     N'منهجية PCAF — إسناد قروض السيارات = القائم / قيمة الأصل. النطاق الأول من استهلاك الوقود × متوسّط الكيلومترات السنوية × معامل الانبعاث حسب نوع المركبة (سيدان/دفع رباعي/بيك آب). تويوتا وهيونداي تهيمنان على المحفظة، حصّة السيارات الكهربائية 2.1% في 2026.',
     N'PUBLISHED');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_record WHERE organisation_id = 1 AND reporting_year = 2026 AND asset_class = N'SOVEREIGN_DEBT' AND reporting_period = N'ANNUAL' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_record
    (organisation_id, reporting_year, reporting_period, asset_class,
     asset_class_label_en, asset_class_label_ar,
     outstanding_amount_sar_millions, attribution_factor, data_quality_score,
     emissions_scope1_tco2e, emissions_scope2_tco2e, emissions_scope3_tco2e,
     total_financed_emissions_tco2e,
     methodology_en, methodology_ar, lifecycle_stage)
VALUES
    (1, 2026, N'ANNUAL', N'SOVEREIGN_DEBT',
     N'Sovereign debt', N'الدين السيادي',
     45200.00, 0.000180, 4,
     185000000.00, 0.00, 0.00, 33300.00,
     N'PCAF (sovereign debt extension, 2022) — attribution = outstanding / PPP-adjusted GDP. Country emissions = territorial Scope-1 per UNFCCC inventory. Saudi Arabia 2024 NDC submitted to UNFCCC informs the path.',
     N'منهجية PCAF (ملحق الدين السيادي، 2022) — معامل الإسناد = القائم / الناتج المحلي الإجمالي بتعادل القوى الشرائية. انبعاثات الدولة = النطاق الأول الإقليمي حسب جرد UNFCCC. مساهمة المملكة المُحدَّثة لـ 2024 المُقدَّمة للاتفاقية الإطارية تُغذّي المسار.',
     N'PUBLISHED');
GO

-- ─── 8. Net-zero / interim reduction targets ──────────────────────

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_target WHERE organisation_id = 1 AND target_code = N'FE-TARGET-NETZERO-2050' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_target
    (organisation_id, target_code, title_en, title_ar,
     target_year, baseline_year, baseline_emissions_tco2e, target_emissions_tco2e,
     reduction_percent, sbti_aligned, alignment_framework)
VALUES
    (1, N'FE-TARGET-NETZERO-2050',
     N'Net-zero financed emissions by 2050',
     N'الحياد الصافي للانبعاثات المُموَّلة بحلول 2050',
     2050, 2026, 1508671.40, 0.00,
     100.00, 1, N'SBTI_1_5C');
GO

IF NOT EXISTS (SELECT 1 FROM sust.financed_emissions_target WHERE organisation_id = 1 AND target_code = N'FE-TARGET-2030-INTERIM' AND is_deleted = 0)
INSERT INTO sust.financed_emissions_target
    (organisation_id, target_code, title_en, title_ar,
     target_year, baseline_year, baseline_emissions_tco2e, target_emissions_tco2e,
     reduction_percent, sbti_aligned, alignment_framework)
VALUES
    (1, N'FE-TARGET-2030-INTERIM',
     N'Interim 50% reduction by 2030',
     N'خفض مرحلي بنسبة 50% بحلول 2030',
     2030, 2026, 1508671.40, 754335.70,
     50.00, 1, N'NZBA');
GO

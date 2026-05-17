-- V55 — Sustainability schema + Climate Risk & Opportunity register
--
-- First slice of the Sustainability & Environmental Governance module
-- (ESG-SUST). Creates the dedicated `sust` schema and the first table —
-- climate_risk_opportunity, which holds physical / transition / regulatory
-- / market / technology / reputation / supply-chain risks and the
-- opportunity register (each row has a `kind` discriminator).
--
-- Each row optionally links to:
--   - the ERM risk register (related_erm_risk_id, BIGINT FK by id only
--     to avoid a hard schema-cross-link; the service layer validates)
--   - a TCFD recommendation row (related_tcfd_id, populated in V57)
--   - a supplier, asset, or service (free-form refs for now)
--
-- State machine: IDENTIFIED → ASSESSED → TREATED → CLOSED | RETIRED
-- Tone in service layer drives the risk_score (likelihood × impact, 1-5
-- each → 1-25). Service auto-suggests "link to ERM" when score >= 15.
--
-- Seed: 5 rows showcasing the spectrum — physical (flood), transition
-- (carbon-pricing), regulatory (PDPL-climate-disclosure), opportunity
-- (renewable-PPA), and supply-chain (Scope 3 vendor exit). Matches the
-- 5 records the spec's seed-data section asks for.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- ─── Schema ──────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'sust')
BEGIN
    EXEC('CREATE SCHEMA sust');
END
GO

-- ─── Table: climate_risk_opportunity ─────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'climate_risk_opportunity' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.climate_risk_opportunity (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT        NOT NULL,
        external_id                 NVARCHAR(64)  NULL,
        item_code                   NVARCHAR(40)  NULL,

        reporting_year              SMALLINT      NOT NULL,

        -- RISK | OPPORTUNITY — drives the lifecycle + which fields apply.
        kind                        NVARCHAR(20)  NOT NULL DEFAULT N'RISK',

        -- For RISK rows: PHYSICAL | TRANSITION | REGULATORY | MARKET |
        --                TECHNOLOGY | REPUTATION | SUPPLY_CHAIN
        -- For OPPORTUNITY rows: RESOURCE_EFFICIENCY | ENERGY_SOURCE |
        --                       PRODUCT_SERVICE | MARKETS | RESILIENCE
        category                    NVARCHAR(30)  NOT NULL,

        title_en                    NVARCHAR(255) NOT NULL,
        title_ar                    NVARCHAR(255) NULL,
        description_en              NVARCHAR(MAX) NULL,
        description_ar              NVARCHAR(MAX) NULL,

        -- SHORT (≤1y) | MEDIUM (1-5y) | LONG (>5y)
        time_horizon                NVARCHAR(10)  NOT NULL DEFAULT N'MEDIUM',

        likelihood                  SMALLINT      NOT NULL DEFAULT 3,  -- 1..5
        impact                      SMALLINT      NOT NULL DEFAULT 3,  -- 1..5
        risk_score                  SMALLINT      NOT NULL DEFAULT 9,  -- likelihood * impact

        -- Financial materiality in SAR (rough estimate).
        financial_impact_sar        DECIMAL(18,2) NULL,

        -- Free-form ref strings — keep schema cross-links shallow.
        related_service             NVARCHAR(255) NULL,
        related_supplier            NVARCHAR(255) NULL,
        related_asset               NVARCHAR(255) NULL,
        related_erm_risk_id         BIGINT        NULL,
        related_tcfd_recommendation NVARCHAR(40)  NULL,

        controls_summary            NVARCHAR(MAX) NULL,
        treatment_plan              NVARCHAR(MAX) NULL,
        metrics_summary             NVARCHAR(MAX) NULL,

        evidence_ref                NVARCHAR(255) NULL,
        owner_name                  NVARCHAR(255) NULL,
        owner_email                 NVARCHAR(255) NULL,

        -- IDENTIFIED | ASSESSED | TREATED | CLOSED | RETIRED
        status                      NVARCHAR(20)  NOT NULL DEFAULT N'IDENTIFIED',

        identified_at               DATETIME2     NULL,
        assessed_at                 DATETIME2     NULL,
        treated_at                  DATETIME2     NULL,
        closed_at                   DATETIME2     NULL,
        review_date                 DATE          NULL,

        is_deleted                  BIT           NOT NULL DEFAULT 0,
        created_at                  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  NVARCHAR(100) NULL,
        updated_at                  DATETIME2     NULL,
        updated_by                  NVARCHAR(100) NULL,

        CONSTRAINT ck_cro_kind     CHECK (kind IN (N'RISK', N'OPPORTUNITY')),
        CONSTRAINT ck_cro_horizon  CHECK (time_horizon IN (N'SHORT', N'MEDIUM', N'LONG')),
        CONSTRAINT ck_cro_status   CHECK (status IN (N'IDENTIFIED', N'ASSESSED', N'TREATED', N'CLOSED', N'RETIRED')),
        CONSTRAINT ck_cro_lik_imp  CHECK (likelihood BETWEEN 1 AND 5 AND impact BETWEEN 1 AND 5)
    );

    CREATE INDEX ix_cro_org_year   ON sust.climate_risk_opportunity(organisation_id, reporting_year);
    CREATE INDEX ix_cro_org_status ON sust.climate_risk_opportunity(organisation_id, status);
    CREATE INDEX ix_cro_org_kind   ON sust.climate_risk_opportunity(organisation_id, kind);
END
GO

-- ─── Seed data — 5 records, 4 risks + 1 opportunity ──────────────────
-- NOT EXISTS guard so re-running the migration is idempotent.

IF NOT EXISTS (SELECT 1 FROM sust.climate_risk_opportunity WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.climate_risk_opportunity (
        organisation_id, reporting_year, kind, category, item_code,
        title_en, title_ar, description_en, description_ar,
        time_horizon, likelihood, impact, risk_score, financial_impact_sar,
        related_service, related_supplier, related_asset, related_erm_risk_id,
        related_tcfd_recommendation, controls_summary, treatment_plan, metrics_summary,
        owner_name, owner_email, status, identified_at, review_date
    )
    VALUES
    -- 1. Physical risk — Jeddah DC flood exposure (high score → service should suggest ERM link)
    (1, 2025, N'RISK', N'PHYSICAL', N'CRO-001',
     N'Jeddah DC flood exposure under Red Sea storm intensification',
     N'تعرّض مركز بيانات جدة لفيضانات الساحل الغربي',
     N'2030 climate models project a 2.3× increase in Red Sea coastal storm intensity. Jeddah primary DC sits 1.4km from coast at 2m elevation. Tabletop simulated 18h outage = SAR 4.2M revenue loss + customer SLA breaches.',
     N'تشير نماذج المناخ 2030 إلى زيادة شدّة عواصف الساحل الغربي 2.3×. يقع مركز جدة على بُعد 1.4 كم من الساحل وعلى ارتفاع 2 م. المحاكاة قدّرت توقّفًا 18 ساعة بخسارة 4.2 مليون ريال.',
     N'MEDIUM', 3, 5, 15, 4200000.00,
     N'Customer-facing data services', NULL, N'Jeddah-DC-Primary', NULL,
     N'STRATEGY_C',
     N'Flood gates installed 2024; UPS rated for 8h; off-site backup to Riyadh hourly.',
     N'Q3 2026: relocate primary to inland Riyadh-2 facility; Jeddah becomes DR. Capex SAR 28M.',
     N'Storm-readiness drill quarterly; flood-gate inspection monthly; UPS load test weekly.',
     N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa',
     N'ASSESSED', '2025-09-12', '2026-09-12'),

    -- 2. Transition risk — carbon pricing (regulatory + financial)
    (1, 2025, N'RISK', N'TRANSITION', N'CRO-002',
     N'Saudi carbon pricing scheme — financial exposure on Scope 1+2',
     N'تسعير الكربون السعودي — التعرّض المالي للنطاق 1 + 2',
     N'GASTAT + Energy Ministry signalled a Saudi ETS launch H2 2027. At indicative SAR 75/tCO2e and current 53,130 tCO2e (Scope 1+2), annual exposure ≈ SAR 4M, escalating to ≈ SAR 11M by 2030 as the cap tightens.',
     N'أشارت الجهات السعودية إلى إطلاق نظام تداول الانبعاثات في النصف الثاني من 2027. التعرّض السنوي المقدّر ≈ 4 مليون ريال يرتفع إلى ≈ 11 مليون ريال بحلول 2030.',
     N'MEDIUM', 4, 4, 16, 11000000.00,
     NULL, NULL, NULL, NULL,
     N'STRATEGY_A',
     N'Scope 1+2 reduction roadmap aligned to SGI -30% by 2030; on-site solar PPA 2026.',
     N'Accelerate fleet EV conversion + on-site solar to clip 30% off baseline by 2027 (pre-ETS launch).',
     N'tCO2e/SAR-revenue; renewable share %; ETS-allowance procurement plan.',
     N'Lina Al-Harbi', N'l.alharbi@tss.sa',
     N'ASSESSED', '2025-10-04', '2026-04-04'),

    -- 3. Regulatory risk — PDPL climate-disclosure extension (lower score)
    (1, 2025, N'RISK', N'REGULATORY', N'CRO-003',
     N'SDAIA + CMA climate-disclosure tightening — gap on Scope 3 + scenario analysis',
     N'تشديد إفصاحات المناخ من سدايا وهيئة السوق المالية — فجوة في النطاق 3 وتحليل السيناريوهات',
     N'Anticipated CMA Article 92(b) update + SDAIA climate-data taxonomy will require Scope 3 ≥85% coverage (currently 67%) and 2°C scenario quantitative analysis (currently qualitative only).',
     N'تحديث متوقّع للمادة 92(ب) سيتطلّب تغطية نطاق 3 لا تقلّ عن 85٪ (الحالية 67٪) وتحليل كمّي لسيناريو 2°م.',
     N'SHORT', 4, 3, 12, NULL,
     NULL, NULL, NULL, NULL,
     N'METRICS_TARGETS_B',
     N'Scope 3 vendor survey rolled out 2025-Q3; covers 67% spend.',
     N'Expand vendor survey to remaining 33% by Q2 2026; engage external climate-scenario consultancy.',
     N'Scope 3 coverage %; vendor survey response rate; scenario-analysis maturity (qualitative → quantitative).',
     N'Sara Al-Zahrani', N's.alzahrani@tss.sa',
     N'IDENTIFIED', '2025-11-15', '2026-03-15'),

    -- 4. Supply-chain risk — high-emissions vendor exit
    (1, 2025, N'RISK', N'SUPPLY_CHAIN', N'CRO-004',
     N'High-emissions supplier consolidation risk on net-zero pathway',
     N'مخاطر تركّز موردين ذوي انبعاثات عالية على مسار الحياد الكربوني',
     N'Three top-spend Scope 3 vendors (combined 38% of upstream emissions) have no SBTi-aligned target. Forcing exit without alternates ready triggers 14% raw-material cost increase.',
     N'ثلاثة موردين ضمن أعلى الإنفاق لا يملكون أهدافًا متوافقة مع SBTi. الخروج المفاجئ دون بدائل يرفع تكلفة المواد الأوّليّة 14٪.',
     N'MEDIUM', 3, 4, 12, NULL,
     NULL, N'Top-3 raw-material vendors', NULL, NULL,
     NULL,
     N'TPRM Scope 3 vendor scoring live since 2025-Q2; quarterly engagement calls.',
     N'Score top-50 vendors by SBTi alignment; develop 2-year alternate qualification for top-3.',
     N'Scope 3 emissions weighted by SBTi-aligned vendor share; alternate-vendor readiness %.',
     N'Mohammed Al-Sulaiman', N'm.alsulaiman@tss.sa',
     N'ASSESSED', '2025-08-20', '2026-08-20'),

    -- 5. Opportunity — on-site solar + corporate PPA
    (1, 2025, N'OPPORTUNITY', N'ENERGY_SOURCE', N'CRO-005',
     N'Riyadh-2 facility — 8MW on-site solar + Sudair PPA',
     N'مرفق الرياض-2 — طاقة شمسية في الموقع 8 ميجاواط + اتفاقية شراء طاقة من سدير',
     N'Combining 8MW rooftop PV at Riyadh-2 with a 25MW slice of the Sudair Solar PPA could lift renewable mix from 33% to 51% by 2027, beat the 2030 SGI target three years early, and reduce Scope 2 by 18,400 tCO2e/yr.',
     N'الجمع بين طاقة شمسية 8 ميجاواط ومشاركة 25 ميجاواط من PPA سدير يرفع نسبة الطاقة المتجدّدة من 33٪ إلى 51٪ بحلول 2027.',
     N'MEDIUM', 4, 4, 16, -22000000.00,
     N'All Riyadh operations', NULL, N'Riyadh-2-Rooftop', NULL,
     N'STRATEGY_A',
     N'Solar feasibility complete; PPA term sheet under legal review.',
     N'Sign PPA Q1 2026; rooftop PV construction Q2-Q3 2026; commissioning Q4 2026.',
     N'Renewable mix %; tCO2e avoided; LCOE delta vs grid.',
     N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa',
     N'ASSESSED', '2025-07-08', '2026-01-08');
END
GO

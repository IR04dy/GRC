-- V62 — ESG Materiality Assessment (Sustainability · slice 8)
--
-- Double-materiality scoring of ESG topics — each topic gets two
-- scores on a 1-5 scale (stakeholder importance × business impact);
-- materiality_score = (stakeholder + business) / 2 with priority tier
-- bands ON the resulting matrix:
--   - CRITICAL: avg score ≥ 4.0
--   - HIGH:     avg score ≥ 3.0
--   - MEDIUM:   avg score ≥ 2.0
--   - LOW:      avg score < 2.0
--
-- Workflow: DRAFT → SCORED → APPROVED → ARCHIVED. Topics linked to a
-- material_assessment_id (one per reporting year per organisation).
-- Approved topics anchor downstream linkages — CMA Article 92(a)
-- ("Material ESG topics — assessment") points here, climate risks
-- reference the topic id, materiality matrix is rendered from the
-- CRITICAL + HIGH topics.
--
-- Seed: 5 canonical Saudi-relevant material topics for FY2025.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'material_topic' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.material_topic (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT        NOT NULL,
        external_id                 NVARCHAR(64)  NULL,
        topic_code                  NVARCHAR(40)  NULL,

        reporting_year              SMALLINT      NOT NULL,

        -- ENVIRONMENTAL | SOCIAL | GOVERNANCE
        category                    NVARCHAR(20)  NOT NULL,

        topic_en                    NVARCHAR(255) NOT NULL,
        topic_ar                    NVARCHAR(255) NULL,
        description_en              NVARCHAR(MAX) NULL,
        description_ar              NVARCHAR(MAX) NULL,

        -- Double-materiality axes — 1-5 scale.
        stakeholder_importance      DECIMAL(3,1)  NOT NULL DEFAULT 0,    -- impact ON stakeholders
        business_impact             DECIMAL(3,1)  NOT NULL DEFAULT 0,    -- impact ON business
        -- Computed by service: (stakeholder + business) / 2.
        materiality_score           DECIMAL(3,1)  NULL,
        -- Computed bucket: CRITICAL | HIGH | MEDIUM | LOW.
        priority_tier               NVARCHAR(20)  NULL,

        -- Stakeholder groups that scored this topic (free-text list).
        stakeholder_groups          NVARCHAR(MAX) NULL,

        -- DRAFT | SCORED | APPROVED | ARCHIVED
        status                      NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',

        -- Cross-module links (no FK — service joins).
        linked_cma_disclosure_id    BIGINT        NULL,    -- → sust.cma_disclosure
        linked_climate_risk_id      BIGINT        NULL,    -- → sust.climate_risk_opportunity
        linked_capa_id              BIGINT        NULL,    -- → sust.corrective_action

        evidence_ref                NVARCHAR(255) NULL,
        owner_name                  NVARCHAR(255) NULL,
        owner_email                 NVARCHAR(255) NULL,
        notes                       NVARCHAR(MAX) NULL,

        approved_at                 DATETIME2     NULL,
        approved_by                 NVARCHAR(255) NULL,

        is_deleted                  BIT           NOT NULL DEFAULT 0,
        created_at                  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  NVARCHAR(100) NULL,
        updated_at                  DATETIME2     NULL,
        updated_by                  NVARCHAR(100) NULL,

        CONSTRAINT ck_mt_status      CHECK (status IN (N'DRAFT', N'SCORED', N'APPROVED', N'ARCHIVED')),
        CONSTRAINT ck_mt_category    CHECK (category IN (N'ENVIRONMENTAL', N'SOCIAL', N'GOVERNANCE')),
        CONSTRAINT ck_mt_tier        CHECK (priority_tier IS NULL OR priority_tier IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT ck_mt_stake_score CHECK (stakeholder_importance BETWEEN 0 AND 5),
        CONSTRAINT ck_mt_biz_score   CHECK (business_impact BETWEEN 0 AND 5)
    );

    CREATE INDEX ix_mt_org_year     ON sust.material_topic(organisation_id, reporting_year);
    CREATE INDEX ix_mt_org_category ON sust.material_topic(organisation_id, category);
    CREATE INDEX ix_mt_org_tier     ON sust.material_topic(organisation_id, priority_tier);
END
GO

-- ─── Seed — 5 canonical FY2025 material topics ──────────────────────
-- One CRITICAL, two HIGH, two MEDIUM — spread across ENV/SOC/GOV so
-- the materiality matrix has anchor points in every category.

IF NOT EXISTS (SELECT 1 FROM sust.material_topic WHERE organisation_id = 1 AND reporting_year = 2025)
BEGIN
    INSERT INTO sust.material_topic (
        organisation_id, topic_code, reporting_year, category,
        topic_en, topic_ar,
        description_en, description_ar,
        stakeholder_importance, business_impact,
        materiality_score, priority_tier,
        stakeholder_groups, status,
        owner_name, owner_email, evidence_ref,
        approved_at, approved_by
    )
    VALUES
    (1, N'MT-CLIMATE-001', 2025, N'ENVIRONMENTAL',
     N'Climate-change response & GHG emissions',
     N'الاستجابة لتغيّر المناخ وانبعاثات الغازات الدفيئة',
     N'Reduction trajectory toward net-zero 2060, alignment with SGI 50% renewable target, and disclosure of Scope 1/2/3 emissions per TCFD.',
     N'مسار الخفض نحو الحياد الكربوني 2060، والمحاذاة مع هدف SGI لـ 50% طاقة متجدّدة، والإفصاح عن انبعاثات النطاقات 1/2/3 وفق TCFD.',
     4.7, 4.5, 4.6, N'CRITICAL',
     N'Regulators (CMA, NCEC), Investors, Customers, Board',
     N'APPROVED',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa', N'Materiality matrix 2025',
     '2026-04-15', N'Khalid Al-Otaibi (CTO)'),

    (1, N'MT-SAUDIZATION-001', 2025, N'SOCIAL',
     N'Saudization (Nitaqat) compliance & workforce localization',
     N'الالتزام بالسعودة (نطاقات) وتوطين القوى العاملة',
     N'Maintaining Platinum-tier Nitaqat status, expanding Saudi female-workforce participation, and aligning with Vision 2030 employment targets.',
     N'الحفاظ على فئة نطاقات البلاتيني، وتوسيع مشاركة المرأة السعودية في القوى العاملة، والمحاذاة مع أهداف رؤية 2030 للتوظيف.',
     4.2, 3.8, 4.0, N'HIGH',
     N'HRSD, Employees, Investors, Government',
     N'APPROVED',
     N'Mohammed Al-Sulaiman (HR Director)', N'm.alsulaiman@tss.sa', N'HRSD 2025 Q4 + diversity report',
     '2026-04-15', N'Khalid Al-Otaibi (CTO)'),

    (1, N'MT-DATA-PRIVACY-001', 2025, N'GOVERNANCE',
     N'Data privacy & PDPL compliance',
     N'حماية البيانات والامتثال لنظام حماية البيانات الشخصية',
     N'PDPL Article 4 compliance, 72-hour SDAIA breach notification readiness, and DPIA coverage for high-risk processing activities.',
     N'الامتثال للمادة 4 من نظام حماية البيانات الشخصية، وجاهزية إشعار سدايا خلال 72 ساعة، وتغطية تقييم أثر حماية البيانات لأنشطة المعالجة عالية المخاطر.',
     4.0, 3.7, 3.9, N'HIGH',
     N'SDAIA, Customers, Regulators, Board',
     N'APPROVED',
     N'Lina Al-Harbi (CRO)', N'l.alharbi@tss.sa', N'PDPL compliance scorecard 2025',
     '2026-04-15', N'Khalid Al-Otaibi (CTO)'),

    (1, N'MT-WATER-001', 2025, N'ENVIRONMENTAL',
     N'Water stewardship & intensity',
     N'إدارة المياه والكثافة',
     N'Water-intensity reduction targets under SGI, closed-loop cooling at production plants, and recycled-water integration for landscaping.',
     N'أهداف خفض كثافة استهلاك المياه ضمن SGI، والتبريد بالدائرة المغلقة في مصانع الإنتاج، ودمج المياه المُعاد تدويرها في التنسيق.',
     3.5, 2.8, 3.2, N'HIGH',
     N'Regulators, Local communities, Operations',
     N'SCORED',
     N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa', N'Water-intensity Q1 2026',
     NULL, NULL),

    (1, N'MT-SUPPLIER-ESG-001', 2025, N'GOVERNANCE',
     N'Supplier ESG due diligence & Scope 3 transparency',
     N'العناية الواجبة في الاستدامة لدى المورّدين وشفافية النطاق 3',
     N'Tier-1 supplier ESG screening, Scope 3 spend-based emissions coverage, and supplier code-of-conduct enforcement aligned to OECD guidelines.',
     N'فحص الاستدامة لمورّدي المستوى 1، وتغطية انبعاثات النطاق 3 المعتمدة على الإنفاق، وفرض ميثاق سلوك المورّدين بمحاذاة إرشادات OECD.',
     2.8, 2.5, 2.6, N'MEDIUM',
     N'Procurement, Suppliers, Investors',
     N'SCORED',
     N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa', N'Supplier ESG screening 2025',
     NULL, NULL);
END
GO

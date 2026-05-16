-- V61 — TCFD Alignment Matrix (Sustainability · slice 7)
--
-- Promotes the 11 canonical TCFD recommendations across 4 pillars from
-- a static frontend constant (esg.ts/TCFD_ITEMS) to a real entity with
-- disclosure lifecycle, gap tracking, and climate-risk linkage.
--
-- Activates Rule 7: when a Strategy-pillar resilience recommendation is
-- marked PARTIAL or NOT_DISCLOSED, the service tags it for climate-risk
-- gap creation. Surfaces via the aggregate endpoint as
-- strategyResilienceGap=true so the dashboard / CapaTab can offer to
-- raise a CA-SUST-* corrective action.
--
-- Schema mirrors V60 (CMA) — same 6-status set, same Rule 5 + Rule 6
-- gap/evidence semantics, plus a related_climate_risk_id pointer for
-- the TCFD → climate-risk register link.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'tcfd_item' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.tcfd_item (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,

        reporting_year           SMALLINT      NOT NULL,
        -- GOVERNANCE | STRATEGY | RISK_MGMT | METRICS_TARGETS
        pillar                   NVARCHAR(20)  NOT NULL,
        recommendation_code      NVARCHAR(5)   NOT NULL,    -- "a", "b", "c"

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,
        recommendation_en        NVARCHAR(MAX) NULL,
        recommendation_ar        NVARCHAR(MAX) NULL,

        -- Same 6-status set as CMA (V60) for consistent UX.
        disclosure_status        NVARCHAR(30)  NOT NULL DEFAULT N'UNDER_REVIEW',

        evidence_ref             NVARCHAR(255) NULL,
        evidence_owner           NVARCHAR(255) NULL,

        -- Required when disclosure_status IN (PARTIAL, NOT_DISCLOSED).
        gap_description_en       NVARCHAR(MAX) NULL,
        gap_description_ar       NVARCHAR(MAX) NULL,

        -- TCFD-specific cross-module pointers.
        related_climate_risk_id  BIGINT        NULL,    -- → sust.climate_risk_opportunity
        related_metric_id        BIGINT        NULL,    -- → sust.esg_metric (V63, future)
        linked_capa_id           BIGINT        NULL,

        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,
        reviewer_name            NVARCHAR(255) NULL,
        last_reviewed_at         DATETIME2     NULL,
        approval_status          NVARCHAR(30)  NULL,
        approved_by              NVARCHAR(255) NULL,
        approved_at              DATETIME2     NULL,

        notes                    NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_tcfd_status CHECK (disclosure_status IN (
            N'DISCLOSED', N'PARTIAL', N'NOT_DISCLOSED', N'NOT_APPLICABLE',
            N'UNDER_REVIEW', N'EVIDENCE_REQUIRED')),
        CONSTRAINT ck_tcfd_pillar CHECK (pillar IN (
            N'GOVERNANCE', N'STRATEGY', N'RISK_MGMT', N'METRICS_TARGETS')),
        CONSTRAINT ck_tcfd_approval CHECK (approval_status IS NULL OR approval_status IN
            (N'PENDING', N'APPROVED', N'REJECTED'))
    );

    -- One row per (org, year, pillar+recommendation).
    CREATE UNIQUE INDEX uq_tcfd_org_year_rec ON sust.tcfd_item(
        organisation_id, reporting_year, pillar, recommendation_code
    ) WHERE is_deleted = 0;

    CREATE INDEX ix_tcfd_org_year    ON sust.tcfd_item(organisation_id, reporting_year);
    CREATE INDEX ix_tcfd_org_status  ON sust.tcfd_item(organisation_id, disclosure_status);
    CREATE INDEX ix_tcfd_org_pillar  ON sust.tcfd_item(organisation_id, pillar);
END
GO

-- ─── Seed — 11 TCFD recommendations across 4 pillars ────────────────
-- Three items are PARTIAL (Strategy b + c, Metrics b). Each carries a
-- bilingual gap description so Rule 5 is satisfied at seed time. The
-- Strategy-c resilience-scenario item is the Rule 7 trigger.

IF NOT EXISTS (SELECT 1 FROM sust.tcfd_item WHERE organisation_id = 1 AND reporting_year = 2025)
BEGIN
    INSERT INTO sust.tcfd_item (
        organisation_id, reporting_year, pillar, recommendation_code,
        title_en, title_ar,
        recommendation_en, recommendation_ar,
        disclosure_status, evidence_ref, evidence_owner,
        gap_description_en, gap_description_ar,
        owner_name, reviewer_name, last_reviewed_at, approval_status
    )
    VALUES
    -- ─── Governance ────────────────────────────────────────────────
    (1, 2025, N'GOVERNANCE', N'a',
     N'Board oversight of climate risks',
     N'إشراف المجلس على المخاطر المناخية',
     N'Describe the board''s oversight of climate-related risks and opportunities.',
     N'وصف إشراف المجلس على المخاطر والفرص المتعلّقة بالمناخ.',
     N'DISCLOSED', N'Board ESG Committee charter v3.0', N'Sara Al-Zahrani (CSO)',
     NULL, NULL,
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-20', N'APPROVED'),

    (1, 2025, N'GOVERNANCE', N'b',
     N'Management role in assessing climate risks',
     N'دور الإدارة في تقييم المخاطر المناخية',
     N'Describe management''s role in assessing and managing climate-related risks and opportunities.',
     N'وصف دور الإدارة في تقييم وإدارة المخاطر والفرص المتعلّقة بالمناخ.',
     N'DISCLOSED', N'CRO mandate v2.0', N'Lina Al-Harbi (CRO)',
     NULL, NULL,
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-20', N'APPROVED'),

    -- ─── Strategy ──────────────────────────────────────────────────
    (1, 2025, N'STRATEGY', N'a',
     N'Climate-related risks + opportunities (short / medium / long term)',
     N'المخاطر والفرص المناخية (قصير / متوسط / طويل الأمد)',
     N'Describe the climate-related risks and opportunities identified over short, medium, and long-term horizons.',
     N'وصف المخاطر والفرص المتعلّقة بالمناخ المحدّدة على المدى القصير والمتوسط والطويل.',
     N'DISCLOSED', N'2026 Annual Report §4', N'Sara Al-Zahrani (CSO)',
     NULL, NULL,
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-22', N'APPROVED'),

    (1, 2025, N'STRATEGY', N'b',
     N'Impact on business, strategy, financial planning',
     N'الأثر على الأعمال والاستراتيجية والتخطيط المالي',
     N'Describe the impact of climate-related risks and opportunities on the business, strategy, and financial planning.',
     N'وصف أثر المخاطر والفرص المناخية على الأعمال والاستراتيجية والتخطيط المالي.',
     N'PARTIAL', N'Business plan 2026 (climate add-on)', N'Lina Al-Harbi (CRO)',
     N'Quantitative SAR-impact for the carbon-pricing scenarios not yet integrated into FY2026 financial plan — CA-SUST-002 in flight.',
     N'لم يُدمج بعد الأثر المالي بالريال لسيناريوهات السعر السعودي للكربون في خطة 2026 — CA-SUST-002 قيد التنفيذ.',
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-22', N'PENDING'),

    (1, 2025, N'STRATEGY', N'c',
     N'Resilience under different climate scenarios (incl. 2°C)',
     N'الصمود تحت سيناريوهات مناخية مختلفة (بما في ذلك 2°م)',
     N'Describe the resilience of the organisation''s strategy, taking into consideration different climate-related scenarios including a 2°C-or-lower scenario.',
     N'وصف صمود استراتيجية المنشأة آخذاً في الاعتبار سيناريوهات مناخية مختلفة بما في ذلك سيناريو 2°م أو أقل.',
     N'PARTIAL', N'Scenario analysis FY2026', N'Lina Al-Harbi (CRO)',
     N'Partial scenario coverage — only NGFS Net-Zero 2050 modelled to date. NGFS Delayed Transition + Current Policies scenarios pending Q3 2026 (Rule 7 trigger).',
     N'تغطية جزئية للسيناريوهات — حتى الآن تم نمذجة سيناريو NGFS Net-Zero 2050 فقط. سيناريوهات Delayed Transition و Current Policies مُعلّقة حتى الربع الثالث 2026 (محرّك القاعدة 7).',
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-22', N'PENDING'),

    -- ─── Risk Management ───────────────────────────────────────────
    (1, 2025, N'RISK_MGMT', N'a',
     N'Process for identifying climate risks',
     N'إجراء تحديد المخاطر المناخية',
     N'Describe the organisation''s processes for identifying and assessing climate-related risks.',
     N'وصف إجراءات المنشأة لتحديد وتقييم المخاطر المتعلّقة بالمناخ.',
     N'DISCLOSED', N'POL-CLIMATE-RISK-001', N'Lina Al-Harbi (CRO)',
     NULL, NULL,
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-25', N'APPROVED'),

    (1, 2025, N'RISK_MGMT', N'b',
     N'Process for managing climate risks',
     N'إجراء إدارة المخاطر المناخية',
     N'Describe the organisation''s processes for managing climate-related risks.',
     N'وصف إجراءات المنشأة لإدارة المخاطر المتعلّقة بالمناخ.',
     N'DISCLOSED', N'Risk appetite — climate band', N'Lina Al-Harbi (CRO)',
     NULL, NULL,
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-25', N'APPROVED'),

    (1, 2025, N'RISK_MGMT', N'c',
     N'Integration into overall risk management',
     N'التكامل مع إدارة المخاطر الشاملة',
     N'Describe how processes for identifying, assessing, and managing climate-related risks are integrated into the overall risk-management framework.',
     N'وصف كيفية دمج إجراءات تحديد وتقييم وإدارة المخاطر المتعلّقة بالمناخ ضمن إطار إدارة المخاطر الشامل.',
     N'DISCLOSED', N'ERM Framework v4.1', N'Lina Al-Harbi (CRO)',
     NULL, NULL,
     N'Lina Al-Harbi (CRO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-25', N'APPROVED'),

    -- ─── Metrics & Targets ─────────────────────────────────────────
    (1, 2025, N'METRICS_TARGETS', N'a',
     N'Metrics for climate risks + opportunities',
     N'مقاييس المخاطر والفرص المناخية',
     N'Disclose the metrics used to assess climate-related risks and opportunities in line with the strategy and risk-management process.',
     N'الإفصاح عن المقاييس المستخدمة لتقييم المخاطر والفرص المناخية بما يتوافق مع الاستراتيجية وإجراء إدارة المخاطر.',
     N'DISCLOSED', N'GHG inventory 2025', N'Sara Al-Zahrani (CSO)',
     NULL, NULL,
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-28', N'APPROVED'),

    (1, 2025, N'METRICS_TARGETS', N'b',
     N'Scope 1, 2, 3 emissions disclosure',
     N'الإفصاح عن انبعاثات النطاقات 1 و 2 و 3',
     N'Disclose Scope 1, Scope 2, and (if appropriate) Scope 3 greenhouse-gas (GHG) emissions and the related risks.',
     N'الإفصاح عن انبعاثات النطاقين 1 و 2، وإن كان ملائماً النطاق 3، من غازات الدفيئة والمخاطر المرتبطة.',
     N'PARTIAL', N'Scope 3 — 67% coverage', N'Sara Al-Zahrani (CSO)',
     N'Scope 3 weighted coverage at 67% — below 70% Rule 1 threshold. CA-SUST-004 and CA-SUST-005 raising coverage.',
     N'تغطية النطاق 3 الموزونة 67% — دون عتبة 70% للقاعدة 1. CA-SUST-004 و CA-SUST-005 يرفعان التغطية.',
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-28', N'PENDING'),

    (1, 2025, N'METRICS_TARGETS', N'c',
     N'Targets to manage climate risks + performance',
     N'الأهداف لإدارة المخاطر المناخية والأداء',
     N'Describe the targets used by the organisation to manage climate-related risks and opportunities and performance against targets.',
     N'وصف الأهداف التي تستخدمها المنشأة لإدارة المخاطر والفرص المناخية والأداء مقارنةً بها.',
     N'DISCLOSED', N'Net-zero 2060 commitment + SGI register', N'Sara Al-Zahrani (CSO)',
     NULL, NULL,
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)',
     '2026-04-28', N'APPROVED');
END
GO

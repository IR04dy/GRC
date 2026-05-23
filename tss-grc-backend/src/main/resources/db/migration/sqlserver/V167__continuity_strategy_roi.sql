-- V167 — Slice 9-D: Continuity Strategy ROI analysis.
--
-- Builds on top of V162 (Quantitative BIA — monetised loss curves per
-- 24h/72h/1w/2w/1m band) by attaching a board-ready ROI table to every
-- proposed continuity strategy. The CFO can finally answer the Board's
-- question: "if we spend SAR X on this strategy, what is the avoidable
-- loss over its planning horizon and what is the ROI %?"
--
-- One table:
--
--   bcm.strategy_roi_analysis    — one row per strategy candidate
--                                  per BIA, with capex/opex inputs,
--                                  derived avoidable loss, ROI %,
--                                  NPV at 8% SAMA reference rate,
--                                  payback period, and a recommendation
--                                  state machine.
--
-- Recommendation state machine (enforced in StrategyRoiService):
--   PENDING_REVIEW → APPROVED | DEFERRED | REJECTED
--   DEFERRED       → APPROVED | REJECTED (re-review allowed)
--   APPROVED/REJECTED are terminal until re-opened to PENDING_REVIEW.
--
-- Worked example seeded against BIA-Q-2026-001 (SARIE/SADAD/mada retail
-- payments) — three strategies (ACTIVE_ACTIVE, WARM_DR, COLD_DR) with
-- realistic Saudi-bank capex/opex profiles and pre-computed ROI/NPV/
-- payback values that match the StrategyRoiService formulas. The
-- ACTIVE_ACTIVE option is marked APPROVED (highest ROI, sub-hour RTO),
-- WARM_DR is PENDING_REVIEW (board-pack candidate), and COLD_DR is
-- DEFERRED (cheapest but RTO 24h breaches MTPD from the BIA at 72h).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ─── ROI analysis table ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'strategy_roi_analysis' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.strategy_roi_analysis (
        id                                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                     BIGINT          NOT NULL,
        external_id                         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        analysis_code                       VARCHAR(64)     NOT NULL,        -- e.g. ROI-2026-001
        bia_quantitative_id                 BIGINT          NOT NULL,        -- FK to bcm.bia_quantitative
        strategy_id                         BIGINT          NULL,            -- optional FK to bcm.continuity_strategies

        strategy_name_en                    NVARCHAR(255)   NOT NULL,
        strategy_name_ar                    NVARCHAR(255)   NOT NULL,
        strategy_type                       VARCHAR(40)     NOT NULL,        -- ACTIVE_ACTIVE | WARM_DR | COLD_DR | VENDOR_FAILOVER | MANUAL_WORKAROUND | INSURANCE_TRANSFER

        planning_horizon_years              INT             NOT NULL DEFAULT 5,

        capex_sar                           DECIMAL(18,2)   NOT NULL,        -- one-time setup cost
        opex_annual_sar                     DECIMAL(18,2)   NOT NULL,        -- annual running cost

        expected_rto_hours                  INT             NOT NULL,        -- recovery time the strategy achieves
        expected_incidents_per_year         DECIMAL(8,2)    NOT NULL DEFAULT 0.5,

        -- Derived (recomputed every save by the service)
        avoidable_loss_per_incident_sar     DECIMAL(18,2)   NOT NULL,        -- BIA loss at the matching RTO band
        total_avoidable_loss_sar            DECIMAL(18,2)   NOT NULL,        -- avoidable × frequency × horizon
        total_cost_sar                      DECIMAL(18,2)   NOT NULL,        -- capex + (opex × horizon)
        roi_percent                         DECIMAL(8,2)    NOT NULL,        -- ((avoidable - cost) / cost) × 100
        payback_period_months               DECIMAL(8,2)    NULL,            -- NULL when no payback within horizon
        npv_sar                             DECIMAL(18,2)   NULL,            -- discounted at 8% SAMA reference rate
        discount_rate_percent               DECIMAL(5,2)    NOT NULL DEFAULT 8.00,

        -- Recommendation state machine
        recommendation                      VARCHAR(40)     NOT NULL DEFAULT 'PENDING_REVIEW',
        -- PENDING_REVIEW | APPROVED | REJECTED | DEFERRED
        recommendation_rationale_en         NVARCHAR(1000)  NULL,
        recommendation_rationale_ar         NVARCHAR(1000)  NULL,
        decided_by_user_id                  BIGINT          NULL,
        decided_by_name                     NVARCHAR(255)   NULL,
        decided_at                          DATETIME2       NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_at                          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                          DATETIME2       NULL,
        created_by                          BIGINT          NULL,
        updated_by                          BIGINT          NULL,
        is_deleted                          BIT             NOT NULL DEFAULT 0,
        deleted_at                          DATETIME2       NULL,
        deleted_by                          BIGINT          NULL,
        row_version                         ROWVERSION,

        CONSTRAINT FK_strategy_roi_bia
            FOREIGN KEY (bia_quantitative_id) REFERENCES bcm.bia_quantitative(id)
    );

    -- Unique analysis_code per tenant
    CREATE UNIQUE INDEX UX_strategy_roi_org_code
        ON bcm.strategy_roi_analysis(organisation_id, analysis_code)
        WHERE is_deleted = 0;

    -- "Show me pending approvals" — the CFO/Board landing query
    CREATE INDEX IX_strategy_roi_org_rec
        ON bcm.strategy_roi_analysis(organisation_id, recommendation)
        WHERE is_deleted = 0;

    -- Comparison view — all ROI analyses for one BIA
    CREATE INDEX IX_strategy_roi_org_bia
        ON bcm.strategy_roi_analysis(organisation_id, bia_quantitative_id)
        WHERE is_deleted = 0;

    -- Strategy register cross-reference (when wired up)
    CREATE INDEX IX_strategy_roi_org_strategy
        ON bcm.strategy_roi_analysis(organisation_id, strategy_id)
        WHERE is_deleted = 0 AND strategy_id IS NOT NULL;
END
GO

-- ─── Worked example: 3 ROI analyses against BIA-Q-2026-001 ──────────
--
-- The BIA-Q-2026-001 row carries these per-band totals (V162):
--   24h: SAR  12.95M   72h: SAR  32.65M   1w: SAR 71.4M
--   2w:  SAR 128.9M    1m:  SAR 245.2M
--   max_tolerable_loss = SAR 25M;   mtpd_breach_at = '72h'
--
-- For ROI purposes we map expected_rto_hours → the avoidable loss band:
--   ≤ 1h   → use 24h band loss (incident contained before band closes)
--   ≤ 4h   → use 24h band loss
--   ≤ 24h  → use 24h band loss
--   ≤ 72h  → use 72h band cumulative
--   ≤ 168h → use 1w band cumulative
--   else   → use 1m band cumulative
--
-- All three strategies assume 0.5 expected incidents per year (one major
-- outage every two years — consistent with Saudi-bank SARIE outage data
-- 2020-2025) over a 5-year horizon.

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bia_quantitative' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    DECLARE @bia_id BIGINT;
    SELECT TOP 1 @bia_id = id
    FROM bcm.bia_quantitative
    WHERE organisation_id = 1 AND assessment_code = 'BIA-Q-2026-001' AND is_deleted = 0;

    IF @bia_id IS NOT NULL
    BEGIN
        -- ─── 1: ACTIVE_ACTIVE — sub-hour RTO, high capex, high ROI ───
        -- avoidable_per_incident = 24h band cumulative (RTO 0.5h ≤ 24h)
        --                        = 12,950,000
        -- total_avoidable = 12,950,000 × 0.5 × 5 = 32,375,000
        -- total_cost      = 8,000,000 + (1,500,000 × 5) = 15,500,000
        -- roi%            = (32,375,000 - 15,500,000) / 15,500,000 × 100 = 108.87
        -- payback (months)= 8,000,000 / (12,950,000 × 0.5) × 12 = 14.83
        -- NPV @ 8% over 5y of annual avoidable - opex, less capex:
        --   yearly net cashflow = (12,950,000 × 0.5) - 1,500,000 = 4,975,000
        --   discount factors (1.08^t for t=1..5):  1.08, 1.1664, 1.2597, 1.3605, 1.4693
        --   PV sum ≈ 4,975,000 × 3.9927 ≈ 19,863,683
        --   NPV    = 19,863,683 - 8,000,000 ≈ 11,863,683
        IF NOT EXISTS (SELECT 1 FROM bcm.strategy_roi_analysis
                       WHERE organisation_id = 1 AND analysis_code = 'ROI-2026-001')
        INSERT INTO bcm.strategy_roi_analysis (
            organisation_id, analysis_code, bia_quantitative_id, strategy_id,
            strategy_name_en, strategy_name_ar, strategy_type,
            planning_horizon_years, capex_sar, opex_annual_sar,
            expected_rto_hours, expected_incidents_per_year,
            avoidable_loss_per_incident_sar, total_avoidable_loss_sar,
            total_cost_sar, roi_percent, payback_period_months,
            npv_sar, discount_rate_percent,
            recommendation, recommendation_rationale_en, recommendation_rationale_ar,
            decided_by_name, decided_at
        )
        VALUES (
            1, 'ROI-2026-001', @bia_id, NULL,
            'Active-active SARIE feed across Riyadh + Jeddah data centres',
            N'بثّ سريع نشط-نشط بين مركزَي بيانات الرياض وجدة',
            'ACTIVE_ACTIVE',
            5, 8000000.00, 1500000.00,
            1, 0.50,
            12950000.00, 32375000.00,
            15500000.00, 108.87, 14.83,
            11863683.00, 8.00,
            'APPROVED',
            'Recommended: highest avoidable loss capture (24h band fully contained), ' +
            'payback inside FY1 of investment, NPV positive at SAMA 8% reference rate. ' +
            'Eliminates 72h MTPD breach band identified in BIA-Q-2026-001. ' +
            'Capex aligned with SAMA Operational Resilience Framework §5.3 dual-site ' +
            'mandate for D-SIB designated banks.',
            N'موصى به: أعلى قدرة على تجنُّب الخسائر (احتواء كامل ضمن نافذة 24 ساعة)، ' +
            N'استرداد رأس المال خلال السنة الأولى، صافي القيمة الحالية موجب عند معدل ' +
            N'الخصم المرجعي لساما 8%. يُلغي خرق الحد الأقصى للانقطاع المسموح (72 ساعة) ' +
            N'المُحدَّد في تحليل الأثر الكمي BIA-Q-2026-001. النفقات الرأسمالية متوافقة ' +
            N'مع المادة §5.3 من إطار المرونة التشغيلية لساما للبنوك ذات الأهمية النظامية.',
            'Faisal Al-Subaie (Group CFO)', '2026-04-25T10:00:00'
        );

        -- ─── 2: WARM_DR — 4h RTO, moderate capex, pending review ────
        -- avoidable_per_incident = 24h band cumulative (RTO 4h ≤ 24h)
        --                        = 12,950,000
        -- total_avoidable = 12,950,000 × 0.5 × 5 = 32,375,000
        -- total_cost      = 3,000,000 + (800,000 × 5) = 7,000,000
        -- roi%            = (32,375,000 - 7,000,000) / 7,000,000 × 100 = 362.50
        -- payback (months)= 3,000,000 / (12,950,000 × 0.5) × 12 = 5.56
        -- NPV @ 8%:
        --   yearly net cashflow = (12,950,000 × 0.5) - 800,000 = 5,675,000
        --   PV sum ≈ 5,675,000 × 3.9927 ≈ 22,658,576
        --   NPV    = 22,658,576 - 3,000,000 ≈ 19,658,576
        IF NOT EXISTS (SELECT 1 FROM bcm.strategy_roi_analysis
                       WHERE organisation_id = 1 AND analysis_code = 'ROI-2026-002')
        INSERT INTO bcm.strategy_roi_analysis (
            organisation_id, analysis_code, bia_quantitative_id, strategy_id,
            strategy_name_en, strategy_name_ar, strategy_type,
            planning_horizon_years, capex_sar, opex_annual_sar,
            expected_rto_hours, expected_incidents_per_year,
            avoidable_loss_per_incident_sar, total_avoidable_loss_sar,
            total_cost_sar, roi_percent, payback_period_months,
            npv_sar, discount_rate_percent,
            recommendation, recommendation_rationale_en, recommendation_rationale_ar,
            decided_by_name, decided_at
        )
        VALUES (
            1, 'ROI-2026-002', @bia_id, NULL,
            'Warm DR site (stc Business managed-services data centre, Jeddah)',
            N'موقع تعافٍ دافئ — مركز بيانات stc Business في جدة',
            'WARM_DR',
            5, 3000000.00, 800000.00,
            4, 0.50,
            12950000.00, 32375000.00,
            7000000.00, 362.50, 5.56,
            19658576.00, 8.00,
            'PENDING_REVIEW',
            'Strong ROI candidate; 4h RTO still inside 24h band so avoidable loss ' +
            'matches active-active. Lower capex (SAR 3M vs SAR 8M) but introduces ' +
            'a 4h customer-visible service window. Awaiting Risk Committee assessment ' +
            'of reputational impact vs cost differential.',
            N'مرشَّح ذو عائد قوي؛ نافذة تعافٍ 4 ساعات تبقى ضمن خانة 24 ساعة لذا ' +
            N'الخسارة القابلة للتجنُّب تساوي خيار النشط-النشط. نفقات رأسمالية ' +
            N'أقل (3 ملايين ريال مقابل 8 ملايين) لكن مع نافذة انقطاع 4 ساعات ' +
            N'مرئية للعميل. بانتظار تقييم لجنة المخاطر للأثر السمعي مقابل فرق ' +
            N'التكلفة.',
            NULL, NULL
        );

        -- ─── 3: COLD_DR — 24h RTO, low capex, deferred ──────────────
        -- avoidable_per_incident = 24h band cumulative (RTO 24h = 24h)
        --                        = 12,950,000
        -- total_avoidable = 12,950,000 × 0.5 × 5 = 32,375,000
        -- total_cost      = 500,000 + (100,000 × 5) = 1,000,000
        -- roi%            = (32,375,000 - 1,000,000) / 1,000,000 × 100 = 3137.50
        -- payback (months)= 500,000 / (12,950,000 × 0.5) × 12 = 0.93
        -- NPV @ 8%:
        --   yearly net cashflow = (12,950,000 × 0.5) - 100,000 = 6,375,000
        --   PV sum ≈ 6,375,000 × 3.9927 ≈ 25,453,584
        --   NPV    = 25,453,584 - 500,000 ≈ 24,953,584
        --
        -- Apparent ROI is the highest, but the strategy is DEFERRED because
        -- 24h RTO leaves the 72h MTPD breach band from the BIA in place —
        -- numerically attractive, operationally unacceptable for SAMA D-SIB.
        IF NOT EXISTS (SELECT 1 FROM bcm.strategy_roi_analysis
                       WHERE organisation_id = 1 AND analysis_code = 'ROI-2026-003')
        INSERT INTO bcm.strategy_roi_analysis (
            organisation_id, analysis_code, bia_quantitative_id, strategy_id,
            strategy_name_en, strategy_name_ar, strategy_type,
            planning_horizon_years, capex_sar, opex_annual_sar,
            expected_rto_hours, expected_incidents_per_year,
            avoidable_loss_per_incident_sar, total_avoidable_loss_sar,
            total_cost_sar, roi_percent, payback_period_months,
            npv_sar, discount_rate_percent,
            recommendation, recommendation_rationale_en, recommendation_rationale_ar,
            decided_by_name, decided_at
        )
        VALUES (
            1, 'ROI-2026-003', @bia_id, NULL,
            'Cold DR — tape restore from off-site vault (Saudi Post Aramex)',
            N'تعافٍ بارد — استرداد من شرائط في خزائن خارجية (سعودي بوست/أراميكس)',
            'COLD_DR',
            5, 500000.00, 100000.00,
            24, 0.50,
            12950000.00, 32375000.00,
            1000000.00, 3137.50, 0.93,
            24953584.00, 8.00,
            'DEFERRED',
            'Highest raw ROI but operationally unacceptable: 24h RTO leaves the ' +
            '72h MTPD breach band from BIA-Q-2026-001 unaddressed and exposes the ' +
            'bank to SAMA ORF §5.3 escalation. Retained as fallback-of-last-resort ' +
            'only. Deferred pending evaluation of insurance-transfer overlay to ' +
            'fund the residual exposure window.',
            N'أعلى عائد رقمي ولكنه غير مقبول تشغيلياً: نافذة 24 ساعة تترك خرق ' +
            N'الحد الأقصى للانقطاع المسموح (72 ساعة) المُحدَّد في BIA-Q-2026-001 ' +
            N'دون معالجة، وتعرِّض البنك لتصعيد المادة §5.3 من إطار المرونة ' +
            N'التشغيلية لساما. مُحتفَظ به كملاذ أخير فقط. مُؤجَّل بانتظار تقييم ' +
            N'تغطية بوليصة تأمين لتمويل نافذة الانكشاف المتبقية.',
            'Faisal Al-Subaie (Group CFO)', '2026-04-25T10:30:00'
        );
    END
END
GO

-- V35 — Risk scenario analysis (Saudi GRC prompt §7.3 + §3.1).
--
-- The frontend RiskLifecyclePage already runs Monte-Carlo simulations
-- over triangular impact / likelihood distributions, but the result was
-- transient — close the tab and the model is gone. This migration
-- persists each run as a RiskScenario so analysts can:
--
--   • compare scenarios over time on the same risk
--   • attach a scenario to a risk acceptance decision (audit trail)
--   • approve / archive a scenario after review
--
-- Three scenario types are supported via a single check constraint:
--   MONTE_CARLO  — triangular-distribution simulation (the existing one)
--   STRESS_TEST  — single-point shocked inputs (e.g. SAR -30%)
--   WHAT_IF      — narrative-driven impact projection
--
-- Lifecycle: DRAFT → APPROVED | REJECTED | ARCHIVED
--   DRAFT     — author drafting; can re-run the model
--   APPROVED  — CRO / risk owner signed off; locked
--   REJECTED  — terminal; reason captured
--   ARCHIVED  — superseded by a newer run

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'risk.scenarios', N'U') IS NULL
BEGIN
    CREATE TABLE risk.scenarios (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- Parent risk. Nullable so analysts can stage "what-if" scenarios
        -- before a risk has been formally registered.
        risk_id                  BIGINT        NULL,
        risk_code                NVARCHAR(64)  NULL,
        risk_title_en            NVARCHAR(255) NULL,

        -- Identification.
        scenario_code            NVARCHAR(64)  NOT NULL,   -- e.g. SCN-2026-0042
        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,
        description_en           NVARCHAR(MAX) NULL,
        description_ar           NVARCHAR(MAX) NULL,

        -- MONTE_CARLO | STRESS_TEST | WHAT_IF
        scenario_type            NVARCHAR(20)  NOT NULL DEFAULT N'MONTE_CARLO',

        -- Time horizon over which the loss is projected, in months.
        horizon_months           INT           NOT NULL DEFAULT 12,

        -- Reporting currency (SAR by default; allow multi-currency org).
        currency                 NVARCHAR(3)   NOT NULL DEFAULT N'SAR',

        /*
         * input_json shape varies by scenario_type:
         *
         * MONTE_CARLO:
         *   { "pMin":0.05, "pMode":0.15, "pMax":0.35,
         *     "iMin":50000, "iMode":250000, "iMax":1500000,
         *     "trials":10000 }
         *
         * STRESS_TEST:
         *   { "factors":[{ "name":"SAR", "shockPct":-30 },
         *                { "name":"oilPriceUsd", "shockPct":-40 }] }
         *
         * WHAT_IF:
         *   { "narrative": "...", "assumedImpact": 5000000 }
         */
        input_json               NVARCHAR(MAX) NOT NULL,

        /*
         * result_json shape:
         *   { "expectedLoss":..., "p95Loss":..., "p99Loss":..., "stdev":...,
         *     "histogram":[{"rangeStart":..,"rangeEnd":..,"count":..}, ...] }
         */
        result_json              NVARCHAR(MAX) NULL,

        -- Computed convenience columns for fast dashboard reads.
        expected_loss            DECIMAL(18,2) NULL,
        p95_loss                 DECIMAL(18,2) NULL,
        p99_loss                 DECIMAL(18,2) NULL,

        assumptions_en           NVARCHAR(MAX) NULL,
        assumptions_ar           NVARCHAR(MAX) NULL,
        methodology_notes_en     NVARCHAR(MAX) NULL,
        methodology_notes_ar     NVARCHAR(MAX) NULL,

        -- Lifecycle.
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        approved_at              DATETIME2(3)  NULL,
        approved_by              BIGINT        NULL,
        approver_name            NVARCHAR(255) NULL,
        rejection_reason         NVARCHAR(500) NULL,
        archived_at              DATETIME2(3)  NULL,
        archived_by              BIGINT        NULL,
        archive_reason           NVARCHAR(500) NULL,

        -- BaseEntity audit fields.
        external_id              NVARCHAR(64)  NULL,
        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_scenario_type CHECK (scenario_type IN
            (N'MONTE_CARLO', N'STRESS_TEST', N'WHAT_IF')),
        CONSTRAINT CK_scenario_status CHECK (status IN
            (N'DRAFT', N'APPROVED', N'REJECTED', N'ARCHIVED')),
        CONSTRAINT CK_scenario_rejection CHECK (status <> N'REJECTED' OR rejection_reason IS NOT NULL),
        CONSTRAINT UQ_scenario_code UNIQUE (organisation_id, scenario_code)
    );
END
GO

-- Filtered indices for the hot reads.
IF OBJECT_ID(N'risk.scenarios', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_scenario_org_status' AND object_id = OBJECT_ID(N'risk.scenarios'))
BEGIN
    CREATE INDEX IX_scenario_org_status ON risk.scenarios (organisation_id, status)
        WHERE is_deleted = 0;
END
GO

IF OBJECT_ID(N'risk.scenarios', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_scenario_risk' AND object_id = OBJECT_ID(N'risk.scenarios'))
BEGIN
    CREATE INDEX IX_scenario_risk ON risk.scenarios (organisation_id, risk_id)
        WHERE is_deleted = 0 AND risk_id IS NOT NULL;
END
GO

-- ─── Demo seed — 6 scenarios across types + status spread ─────────
IF OBJECT_ID(N'risk.scenarios', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM risk.scenarios WHERE scenario_code = N'SCN-DEMO-01')
BEGIN
    INSERT INTO risk.scenarios
        (organisation_id, risk_id, risk_code, risk_title_en, scenario_code,
         title_en, title_ar, description_en, description_ar,
         scenario_type, horizon_months, currency,
         input_json, result_json,
         expected_loss, p95_loss, p99_loss,
         assumptions_en, assumptions_ar,
         methodology_notes_en, methodology_notes_ar,
         status, approved_at, approved_by, approver_name, rejection_reason,
         external_id)
    VALUES
        -- 1. APPROVED Monte Carlo — Aramco contract delay
        (1, 4, N'RSK-2026-004', N'Aramco contract renewal delay',
         N'SCN-DEMO-01',
         N'Aramco contract delay — Monte Carlo (base case)',
         N'تأخر عقد أرامكو - محاكاة مونت كارلو (السيناريو الأساسي)',
         N'10,000-trial Monte-Carlo on the financial exposure of an 18-month renewal delay assuming current pricing and current run-rate operating cost. Triangular distribution on both likelihood and impact.',
         N'محاكاة مونت كارلو بـ 10,000 تجربة على التعرض المالي لتأخير التجديد 18 شهراً بافتراض التسعير ومعدل التشغيل الحاليين.',
         N'MONTE_CARLO', 18, N'SAR',
         N'{"pMin":0.18,"pMode":0.32,"pMax":0.55,"iMin":1800000,"iMode":4200000,"iMax":8500000,"trials":10000}',
         N'{"expectedLoss":1387500,"p95Loss":3220000,"p99Loss":4750000,"stdev":920000,"histogram":[{"rangeStart":0,"rangeEnd":500000,"count":4120},{"rangeStart":500000,"rangeEnd":1000000,"count":1850},{"rangeStart":1000000,"rangeEnd":1500000,"count":1320},{"rangeStart":1500000,"rangeEnd":2000000,"count":860},{"rangeStart":2000000,"rangeEnd":2500000,"count":620},{"rangeStart":2500000,"rangeEnd":3000000,"count":480},{"rangeStart":3000000,"rangeEnd":3500000,"count":340},{"rangeStart":3500000,"rangeEnd":4000000,"count":210},{"rangeStart":4000000,"rangeEnd":4500000,"count":110},{"rangeStart":4500000,"rangeEnd":5000000,"count":60},{"rangeStart":5000000,"rangeEnd":5500000,"count":20},{"rangeStart":5500000,"rangeEnd":6000000,"count":10}]}',
         1387500.00, 3220000.00, 4750000.00,
         N'Renewal cycle of 18 months. Operating run-rate assumed flat (no inflation). Likelihood includes Aramco procurement-cycle slippage history (3-year average).',
         N'دورة التجديد 18 شهراً. معدل التشغيل ثابت (بدون تضخم). تشمل الاحتمالية تاريخ تأخر دورة المشتريات في أرامكو (متوسط 3 سنوات).',
         N'Triangular distribution; cached random seed for reproducibility (seed=42).',
         N'توزيع مثلث؛ تم تثبيت بذرة عشوائية للنسخ المتطابق (seed=42).',
         N'APPROVED', DATEADD(DAY, -3, SYSUTCDATETIME()), 1, N'admin', NULL,
         N'SCN-DEMO-01'),

        -- 2. APPROVED stress test — SAR depreciation
        (1, 7, N'RSK-2026-007', N'FX exposure on imported equipment',
         N'SCN-DEMO-02',
         N'SAR depreciation -15% stress test',
         N'اختبار إجهاد - انخفاض الريال 15%',
         N'Single-point stress test on imported equipment cost assuming a 15% SAR depreciation against USD over a 6-month replenishment window. Used in the H2 2026 currency-risk acceptance pack.',
         N'اختبار إجهاد على تكلفة المعدات المستوردة بافتراض انخفاض الريال 15% أمام الدولار خلال نافذة تجديد 6 أشهر.',
         N'STRESS_TEST', 6, N'SAR',
         N'{"factors":[{"name":"SAR_USD","shockPct":-15},{"name":"importVolume","shockPct":0}],"baseExposureSar":4200000}',
         N'{"shockedExposureSar":4830000,"deltaSar":630000,"breachesAppetite":true,"appetiteCeilingSar":500000}',
         630000.00, 630000.00, 630000.00,
         N'Replenishment window 6 months. Import volume held constant. Shock applied uniformly across vendor mix.',
         N'نافذة التجديد 6 أشهر. حجم الاستيراد ثابت. الصدمة موزعة بالتساوي على مزيج الموردين.',
         N'Single deterministic shock — no distribution; uses 31-day FX hedging horizon as a comparator.',
         N'صدمة حتمية منفردة - بدون توزيع؛ يستخدم أفق تحوط صرف لمدة 31 يوماً للمقارنة.',
         N'APPROVED', DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin', NULL,
         N'SCN-DEMO-02'),

        -- 3. DRAFT what-if — Datacentre failover
        (1, 12, N'RSK-2026-012', N'Riyadh datacentre power outage',
         N'SCN-DEMO-03',
         N'Datacentre failover — what-if (DR site Jeddah)',
         N'تجاوز فشل مركز البيانات - افتراضي (موقع التعافي جدة)',
         N'Narrative-driven what-if: assume Riyadh primary datacentre offline for 36 hours and failover to Jeddah DR site fully operational within 4 hours (RTO target). Estimates loss from degraded performance during failover only.',
         N'سيناريو افتراضي وصفي: مركز البيانات الأساسي بالرياض خارج الخدمة لمدة 36 ساعة والتعافي إلى موقع جدة يعمل بالكامل خلال 4 ساعات (هدف RTO).',
         N'WHAT_IF', 1, N'SAR',
         N'{"narrative":"Riyadh primary OFFLINE for 36h; Jeddah DR site reaches steady state within 4h (RTO=4h, RPO=1h validated 2026-Q1).","assumedImpactSar":850000,"degradationFactor":0.35}',
         N'{"projectedLossSar":297500,"degradedHours":4,"fullOutageHours":0,"notes":"Only degradation window is monetised (full outage avoided by failover)."}',
         297500.00, 297500.00, 297500.00,
         N'RTO 4h / RPO 1h previously validated in 2026-Q1 BCM exercise. Customer-facing systems prioritised in failover sequence.',
         N'تم التحقق من RTO 4 ساعات و RPO ساعة واحدة في تمرين BCM للربع الأول من 2026. أنظمة العملاء لها الأولوية في تسلسل التعافي.',
         N'Narrative-driven; degradation factor based on 2024-Q3 partial-outage observation.',
         N'وصفي؛ معامل التدهور مبني على ملاحظة الانقطاع الجزئي في الربع الثالث من 2024.',
         N'DRAFT', NULL, NULL, NULL, NULL,
         N'SCN-DEMO-03'),

        -- 4. APPROVED Monte Carlo — Phishing risk
        (1, 18, N'RSK-2026-018', N'Successful spear-phishing campaign',
         N'SCN-DEMO-04',
         N'Spear-phishing — Monte Carlo (post-MFA rollout)',
         N'تصيّد موجه - محاكاة مونت كارلو (بعد نشر المصادقة الثنائية)',
         N'5,000-trial run modelling residual exposure after the company-wide MFA rollout in Q1 2026. Likelihood drops from 0.45 to 0.18; impact drops because credential theft no longer grants single-factor access.',
         N'5,000 تجربة لمحاكاة التعرض المتبقي بعد نشر المصادقة الثنائية على مستوى الشركة في الربع الأول من 2026.',
         N'MONTE_CARLO', 12, N'SAR',
         N'{"pMin":0.10,"pMode":0.18,"pMax":0.35,"iMin":400000,"iMode":1200000,"iMax":3500000,"trials":5000}',
         N'{"expectedLoss":390000,"p95Loss":1850000,"p99Loss":2680000,"stdev":620000,"histogram":[{"rangeStart":0,"rangeEnd":250000,"count":2480},{"rangeStart":250000,"rangeEnd":500000,"count":820},{"rangeStart":500000,"rangeEnd":750000,"count":480},{"rangeStart":750000,"rangeEnd":1000000,"count":350},{"rangeStart":1000000,"rangeEnd":1500000,"count":410},{"rangeStart":1500000,"rangeEnd":2000000,"count":260},{"rangeStart":2000000,"rangeEnd":2500000,"count":140},{"rangeStart":2500000,"rangeEnd":3000000,"count":50},{"rangeStart":3000000,"rangeEnd":3500000,"count":10}]}',
         390000.00, 1850000.00, 2680000.00,
         N'MFA enrolment 96% complete (2026-04-30). Residual users granted 30-day exception window.',
         N'تسجيل المصادقة الثنائية 96% (30 أبريل 2026). المستخدمون المتبقون لديهم نافذة استثناء 30 يوماً.',
         N'Likelihood priors updated from Mimecast 2025 phishing-rate benchmark for Saudi enterprises.',
         N'تم تحديث احتمالات المنشأ من مرجع Mimecast لمعدل التصيّد للمؤسسات السعودية 2025.',
         N'APPROVED', DATEADD(DAY, -2, SYSUTCDATETIME()), 1, N'admin', NULL,
         N'SCN-DEMO-04'),

        -- 5. REJECTED — too optimistic
        (1, 4, N'RSK-2026-004', N'Aramco contract renewal delay',
         N'SCN-DEMO-05',
         N'Aramco contract delay — optimistic case',
         N'تأخر عقد أرامكو - السيناريو المتفائل',
         N'Re-run with optimistic priors (procurement reform accelerates), pMode 0.10 vs base-case 0.32. Submitted as a "best case" to bracket the appetite decision.',
         N'إعادة تشغيل بافتراضات متفائلة (تسارع إصلاح المشتريات)، pMode 0.10 مقابل 0.32 في السيناريو الأساسي.',
         N'MONTE_CARLO', 18, N'SAR',
         N'{"pMin":0.05,"pMode":0.10,"pMax":0.20,"iMin":1800000,"iMode":4200000,"iMax":8500000,"trials":10000}',
         N'{"expectedLoss":520000,"p95Loss":1280000,"p99Loss":1980000,"stdev":380000,"histogram":[]}',
         520000.00, 1280000.00, 1980000.00,
         N'Assumes 2026 procurement-reform programme delivers on time. No precedent for that pace.',
         N'يفترض أن برنامج إصلاح المشتريات 2026 يسلم في الموعد. لا توجد سابقة بهذه الوتيرة.',
         NULL, NULL,
         N'REJECTED', NULL, NULL, NULL,
         N'Likelihood priors not defensible — pMode 0.10 contradicts the 3-year procurement-slippage average of 0.30+. Resubmit with sensitivity bracket if needed.',
         N'SCN-DEMO-05'),

        -- 6. DRAFT Monte Carlo — Vendor concentration
        (1, 22, N'RSK-2026-022', N'Concentration risk — single managed-services vendor',
         N'SCN-DEMO-06',
         N'Vendor concentration — Monte Carlo (Solutions by STC)',
         N'تركز المورد - محاكاة مونت كارلو (الحلول من STC)',
         N'Quantifies exposure if Solutions by STC (managed services) experiences a 5-business-day outage. Includes lost productivity, contractual penalty exposure, and alternate-vendor onboarding cost.',
         N'تحديد كمي للتعرض في حال تعرضت الحلول من STC لانقطاع 5 أيام عمل.',
         N'MONTE_CARLO', 12, N'SAR',
         N'{"pMin":0.06,"pMode":0.12,"pMax":0.22,"iMin":1500000,"iMode":3200000,"iMax":6500000,"trials":10000}',
         NULL,
         NULL, NULL, NULL,
         N'No alternate vendor pre-onboarded. Penalty exposure SAR 800k/day uncapped after day 3.',
         N'لا يوجد مورد بديل مهيأ مسبقاً. التعرض للغرامة 800 ألف ريال يومياً غير محدد بعد اليوم الثالث.',
         NULL, NULL,
         N'DRAFT', NULL, NULL, NULL, NULL,
         N'SCN-DEMO-06');
END
GO

PRINT N'V35 risk.scenarios — table + 6 demo rows applied.';
GO

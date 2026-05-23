-- V162 — Slice 9-C: Quantitative Business Impact Analysis (BIA).
--
-- The existing bcm.bia_records (V98) + bcm.bia_assessments (V103) are
-- qualitative — RTO/RPO/MTPD targets plus a 5-band overall_impact label.
-- That is fine for ISO 22301 §8.2.2 minimum, but SAMA's Operational
-- Resilience Framework (ORF §5.3) and the NCA ECC-2 Business Continuity
-- domain both expect the BCM Manager to quantify financial loss per
-- outage band and ladder it against a board-set "maximum tolerable
-- financial loss" threshold. That is what this migration enables.
--
-- Two tables, parent + lines:
--
--   bcm.bia_quantitative              — header, one row per process × cycle
--   bcm.bia_quantitative_loss_line    — the (time_band × loss_type) matrix
--
-- Each header has a five-column loss profile (24h / 72h / 1w / 2w / 1m)
-- summed across eight loss categories (revenue, regulatory fine,
-- reputational, operational recovery, legal, productivity, churn,
-- contractual). The header carries the board-set max_tolerable_loss_sar
-- and the first time-band where cumulative loss breaches that ceiling
-- (mtpd_breach_at) — that band is what the CFO and the Board read.
--
-- Status state machine (enforced in QuantitativeBiaService):
--   DRAFT → IN_REVIEW → CFO_REVIEW → CFO_APPROVED → BOARD_APPROVED → ARCHIVED
--                                  └─> REJECTED (terminal until re-opened to DRAFT)
--
-- Worked example seeded for org=1 ("TSS GRC" tenant): "Retail Payments
-- (SARIE/SADAD/mada) — core processing" with realistic Saudi-bank-scale
-- SAR amounts and an MTPD breach at 72h against a SAR 25M board ceiling.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ─── Header table ──────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bia_quantitative' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bia_quantitative (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        process_id                  BIGINT          NOT NULL,    -- soft FK to bcm.critical_processes
        assessment_code             NVARCHAR(80)    NOT NULL,    -- e.g. BIA-Q-2026-001
        assessment_date             DATE            NOT NULL,
        cycle_year                  INT             NOT NULL,
        currency                    NVARCHAR(3)     NOT NULL DEFAULT 'SAR',

        methodology_notes_en        NVARCHAR(MAX)   NULL,
        methodology_notes_ar        NVARCHAR(MAX)   NULL,

        -- Per-band rollups — recomputed by the service from lines on save.
        total_loss_24h_sar          DECIMAL(18,2)   NULL,
        total_loss_72h_sar          DECIMAL(18,2)   NULL,
        total_loss_1w_sar           DECIMAL(18,2)   NULL,
        total_loss_2w_sar           DECIMAL(18,2)   NULL,
        total_loss_1m_sar           DECIMAL(18,2)   NULL,

        -- Board-set ceiling (the "maximum tolerable financial loss") and
        -- the first time-band whose CUMULATIVE loss crosses it.
        max_tolerable_loss_sar      DECIMAL(18,2)   NULL,
        mtpd_breach_at              NVARCHAR(20)    NULL,        -- 24h | 72h | 1w | 2w | 1m | null=no breach

        status                      NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | IN_REVIEW | CFO_REVIEW | CFO_APPROVED | BOARD_APPROVED | ARCHIVED | REJECTED

        prepared_by_user_id         BIGINT          NULL,
        prepared_by_name            NVARCHAR(255)   NULL,
        prepared_at                 DATETIME2       NULL,

        bcm_lead_reviewed_at        DATETIME2       NULL,
        bcm_lead_reviewed_by        NVARCHAR(255)   NULL,

        cfo_approved_at             DATETIME2       NULL,
        cfo_approver_name           NVARCHAR(255)   NULL,
        cfo_comments                NVARCHAR(MAX)   NULL,

        board_approved_at           DATETIME2       NULL,
        board_minutes_ref           NVARCHAR(255)   NULL,

        rejection_reason            NVARCHAR(MAX)   NULL,

        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    CREATE UNIQUE INDEX UX_bia_quant_org_code     ON bcm.bia_quantitative(organisation_id, assessment_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_quant_org_process  ON bcm.bia_quantitative(organisation_id, process_id)      WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_quant_org_status   ON bcm.bia_quantitative(organisation_id, status)          WHERE is_deleted = 0;
    CREATE INDEX        IX_bia_quant_org_cycle    ON bcm.bia_quantitative(organisation_id, cycle_year)      WHERE is_deleted = 0;
END
GO

-- ─── Loss-line table ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bia_quantitative_loss_line' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bia_quantitative_loss_line (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        bia_quantitative_id         BIGINT          NOT NULL,
        organisation_id             BIGINT          NOT NULL,    -- denormalised for tenant filtering

        time_band                   NVARCHAR(10)    NOT NULL,    -- 24h | 72h | 1w | 2w | 1m
        loss_type                   NVARCHAR(40)    NOT NULL,    -- REVENUE_LOSS | REGULATORY_FINE | ...
        amount_sar                  DECIMAL(18,2)   NOT NULL,

        basis_narrative_en          NVARCHAR(MAX)   NULL,
        basis_narrative_ar          NVARCHAR(MAX)   NULL,

        confidence                  NVARCHAR(10)    NOT NULL DEFAULT 'MEDIUM',   -- HIGH | MEDIUM | LOW
        is_estimate                 BIT             NOT NULL DEFAULT 1,           -- vs actual historical

        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_bia_quant_loss_line_header
            FOREIGN KEY (bia_quantitative_id) REFERENCES bcm.bia_quantitative(id)
    );

    CREATE UNIQUE INDEX UX_bia_quant_loss_unique
        ON bcm.bia_quantitative_loss_line(bia_quantitative_id, time_band, loss_type)
        WHERE is_deleted = 0;
    CREATE INDEX IX_bia_quant_loss_org
        ON bcm.bia_quantitative_loss_line(organisation_id, bia_quantitative_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Worked example: SARIE/SADAD/mada retail payments (org=1) ──────
-- Realistic Saudi-bank-scale impact. The 24h band already shows ~SAR 13M
-- of cumulative loss against the SAR 25M board ceiling; the 72h band
-- pushes cumulative above the ceiling so mtpd_breach_at = '72h'.
--
-- Saudi-native framing:
--   - SARIE      = Saudi Arabian Riyal Interbank Express (SAMA RTGS)
--   - SADAD      = SAMA bill-payment network
--   - mada       = national debit/POS scheme (SPAN)
--   - PDPL fines = up to SAR 5M per Article 35; SAMA Cyber Security
--                  Framework fines + ORF penalties on top.

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'critical_processes' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    -- Ensure the process exists (org=1) — seed_real_data.py creates several,
    -- but we anchor on a deterministic process name so re-runs find it.
    DECLARE @process_id BIGINT;

    SELECT TOP 1 @process_id = id
    FROM bcm.critical_processes
    WHERE organisation_id = 1
      AND is_deleted = 0
      AND (process_name_en LIKE '%Payment%' OR process_name_en LIKE '%SARIE%' OR process_name_en LIKE '%mada%')
    ORDER BY id;

    IF @process_id IS NULL
    BEGIN
        INSERT INTO bcm.critical_processes (organisation_id, process_name_en, process_name_ar, criticality, is_deleted, created_at)
        VALUES (1,
                'Retail Payments — SARIE / SADAD / mada core processing',
                N'المدفوعات الأفراد — سريع / سداد / مدى',
                'CRITICAL', 0, SYSUTCDATETIME());
        SET @process_id = SCOPE_IDENTITY();
    END

    -- Idempotent header insert
    IF NOT EXISTS (SELECT 1 FROM bcm.bia_quantitative
                   WHERE organisation_id = 1 AND assessment_code = 'BIA-Q-2026-001')
    BEGIN
        INSERT INTO bcm.bia_quantitative (
            organisation_id, process_id, assessment_code, assessment_date, cycle_year, currency,
            methodology_notes_en, methodology_notes_ar,
            total_loss_24h_sar, total_loss_72h_sar, total_loss_1w_sar, total_loss_2w_sar, total_loss_1m_sar,
            max_tolerable_loss_sar, mtpd_breach_at,
            status,
            prepared_by_name, prepared_at,
            bcm_lead_reviewed_at, bcm_lead_reviewed_by,
            cfo_approved_at, cfo_approver_name, cfo_comments
        )
        VALUES (
            1, @process_id, 'BIA-Q-2026-001', '2026-04-15', 2026, 'SAR',
            'Quantitative loss model for SARIE/SADAD/mada core processing. Revenue ' +
            'loss derived from rolling-30-day average interchange and float income ' +
            '(SAR ~340M/yr → SAR ~930K/day baseline; doubled for peak SADAD bill-cycle ' +
            'windows). Regulatory fines benchmarked against SAMA Cyber Security ' +
            'Framework historical fines (median SAR 1.5–3M for >12h core-banking ' +
            'outages) plus PDPL Article 35 ceiling (SAR 5M). Reputational damage ' +
            'modelled as Net Promoter Score erosion × CLV across retail base. ' +
            'Aligns with SAMA Operational Resilience Framework §5.3 and NCA ECC-2 ' +
            'business-continuity domain.',
            N'نموذج الخسارة الكمية لمعالجة المدفوعات الأساسية عبر سريع/سداد/مدى. ' +
            N'احتُسبت خسارة الإيرادات من متوسط دخل العمولات والعائم على مدى 30 ' +
            N'يومًا (~340 مليون ريال/سنة → ~930 ألف ريال/يوم، مع مضاعفة في فترات ' +
            N'ذروة سداد). قُيِّست الغرامات التنظيمية مقابل غرامات إطار الأمن ' +
            N'السيبراني للبنوك (ساما) التاريخية (وسيط 1.5–3 مليون ريال لانقطاع ' +
            N'الأنظمة الأساسية أكثر من 12 ساعة) إضافةً إلى سقف المادة 35 من ' +
            N'نظام حماية البيانات الشخصية (5 مليون ريال). نُمذِجت أضرار السمعة ' +
            N'كتآكل في صافي نقاط الترويج × قيمة العميل عبر قاعدة الأفراد. ' +
            N'يتوافق مع إطار المرونة التشغيلية لساما §5.3 ومجال استمرارية ' +
            N'الأعمال في ECC-2 الصادر عن الهيئة الوطنية للأمن السيبراني.',
            -- Per-band totals (sums of the 8 loss lines below per band)
            12950000.00,   -- 24h
            32650000.00,   -- 72h  ← cumulative > 25M ceiling fires here
            71400000.00,   -- 1w
            128900000.00,  -- 2w
            245200000.00,  -- 1m
            25000000.00,   -- board-set ceiling
            '72h',         -- breach band
            'CFO_APPROVED',
            'Layla Al-Otaibi (BCM Lead, Risk & Resilience)', '2026-04-15T09:30:00',
            '2026-04-18T11:15:00', 'Abdulrahman Al-Ghamdi (BCM Manager)',
            '2026-04-22T14:00:00', 'Faisal Al-Subaie (Group CFO)',
            'Approved with reservation: the 72h MTPD breach band is unacceptable ' +
            'for a SAMA-supervised bank with SARIE membership. Mandating dual-site ' +
            'active/active for SARIE feed within FY2026 H2 and pre-funded SADAD ' +
            'queue redirector. Re-test required after each change. Board copy ' +
            'attached to Risk Committee Q2 minutes.'
        );

        DECLARE @bia_id BIGINT = SCOPE_IDENTITY();

        -- 24h band — 8 loss types
        INSERT INTO bcm.bia_quantitative_loss_line
            (bia_quantitative_id, organisation_id, time_band, loss_type, amount_sar,
             basis_narrative_en, basis_narrative_ar, confidence, is_estimate)
        VALUES
            (@bia_id, 1, '24h', 'REVENUE_LOSS',         8500000.00,
             'Lost interchange (mada POS + SADAD bill-pay commissions) + suspended SARIE float. Baseline SAR ~930K/day × 1.15 peak factor × 8h critical window weighted.',
             N'فقد عمولات (مدى نقاط البيع + سداد) + تجميد عائم سريع. الأساس ~930 ألف ريال/يوم × 1.15 عامل ذروة × 8 ساعات نافذة حرجة مرجَّحة.',
             'HIGH', 1),
            (@bia_id, 1, '24h', 'REGULATORY_FINE',      2000000.00,
             'SAMA Cyber Security Framework + ORF SLA breach penalty (median SAR 1.5–2M for first 24h on critical-tier outage).',
             N'غرامة خرق اتفاقية مستوى الخدمة وفق إطار الأمن السيبراني وإطار المرونة التشغيلية لساما (وسيط 1.5–2 مليون ريال خلال أول 24 ساعة).',
             'MEDIUM', 1),
            (@bia_id, 1, '24h', 'REPUTATIONAL_DAMAGE',  1200000.00,
             'NPS erosion × CLV across active retail base. mada outage trends on X (Twitter) within 90min; SABB/Alinma comparisons drive churn intent.',
             N'تآكل صافي نقاط الترويج × قيمة العميل عبر قاعدة الأفراد النشطة. انقطاع مدى يَتصدَّر منصة X خلال 90 دقيقة؛ مقارنات سَاب/الإنماء تُغذي نيَّة الخروج.',
             'LOW', 1),
            (@bia_id, 1, '24h', 'OPERATIONAL_RECOVERY',  450000.00,
             'War-room standup (24×7), incident-bridge tooling, T24 + Murex specialist on-call premium, courier/runner for manual reconciliation.',
             N'تفعيل غرفة الأزمات (24×7)، أدوات جسر الاستجابة، علاوة استدعاء خبراء T24 و Murex، مندوبون للتسوية اليدوية.',
             'HIGH', 1),
            (@bia_id, 1, '24h', 'LEGAL_COSTS',           150000.00,
             'External counsel engagement (Al-Tamimi/Kabbani) for SAMA notification drafting and customer-complaint defence prep.',
             N'الاستعانة بمحامين خارجيين (التميمي/قباني) لصياغة إخطار ساما وإعداد دفاع شكاوى العملاء.',
             'HIGH', 1),
            (@bia_id, 1, '24h', 'PRODUCTIVITY_LOSS',     400000.00,
             '~2,800 retail-branch + contact-centre staff at reduced throughput while core is down. SAR ~145/hr loaded cost × 8h ÷ 10 effective.',
             N'~2,800 موظفاً في فروع التجزئة ومراكز الاتصال بإنتاجية منخفضة. ~145 ريال/ساعة تكلفة محمَّلة × 8 ساعات ÷ 10 فعّال.',
             'MEDIUM', 1),
            (@bia_id, 1, '24h', 'CUSTOMER_CHURN',        100000.00,
             '24h-window churn signal — partial; full churn impact ladders into 1w/2w bands.',
             N'إشارة خروج ضمن 24 ساعة — جزئية؛ يظهر التأثير الكامل في نوافذ أسبوع/أسبوعين.',
             'LOW', 1),
            (@bia_id, 1, '24h', 'CONTRACTUAL_PENALTY',   150000.00,
             'Merchant-acquirer SLAs (Carrefour KSA, Panda, Jarir) — first-tier SAR/hr penalty per master service agreement.',
             N'اتفاقيات مستوى الخدمة مع التُجار (كارفور السعودية، بنده، جرير) — غرامة الشريحة الأولى ريال/ساعة وفق العقد الإطاري.',
             'MEDIUM', 1);

        -- 72h band — cumulative crosses SAR 25M ceiling here
        INSERT INTO bcm.bia_quantitative_loss_line
            (bia_quantitative_id, organisation_id, time_band, loss_type, amount_sar,
             basis_narrative_en, basis_narrative_ar, confidence, is_estimate)
        VALUES
            (@bia_id, 1, '72h', 'REVENUE_LOSS',        22000000.00,
             'Three full processing days × peak SADAD bill-cycle window. SARIE float lost to interbank competitors.',
             N'ثلاثة أيام معالجة كاملة × نافذة ذروة دورة سداد. خسارة عائم سريع لمنافسين بين المصارف.',
             'HIGH', 1),
            (@bia_id, 1, '72h', 'REGULATORY_FINE',     5000000.00,
             'PDPL Article 35 ceiling triggers when customer data freshness lapses past 48h. SAMA ORF escalation tier (notify Governor''s office).',
             N'يُفعَّل سقف المادة 35 من نظام حماية البيانات الشخصية عند انقضاء 48 ساعة على تحديث بيانات العملاء. شريحة تصعيد إطار المرونة التشغيلية (إبلاغ مكتب المحافظ).',
             'MEDIUM', 1),
            (@bia_id, 1, '72h', 'REPUTATIONAL_DAMAGE',  3000000.00,
             'Press coverage now mainstream (Al-Arabiya, Al-Eqtisadiah). Sustained X campaign #بنك_متعطل drives competitor switching.',
             N'تغطية صحفية رئيسية (العربية، الاقتصادية). حملة X مستمرة بوسم #بنك_متعطل تدفع للتحوُّل لمنافسين.',
             'MEDIUM', 1),
            (@bia_id, 1, '72h', 'OPERATIONAL_RECOVERY', 1200000.00,
             'Third-party DR specialist (Saudi Aramco IT or stc Business managed-services) deployment + Oracle Exadata field engineering.',
             N'استدعاء خبير تعافٍ خارجي (أرامكو السعودية لتقنية المعلومات أو stc Business) + هندسة ميدانية لأوراكل Exadata.',
             'HIGH', 1),
            (@bia_id, 1, '72h', 'LEGAL_COSTS',          400000.00,
             'Customer-complaint defence files now spinning up at scale; CMA + SAMA inquiry response prep.',
             N'ملفات دفاع شكاوى العملاء تتراكم بحجم كبير؛ تجهيز الردود لاستفسارات هيئة السوق المالية وساما.',
             'MEDIUM', 1),
            (@bia_id, 1, '72h', 'PRODUCTIVITY_LOSS',     800000.00,
             'Three full business days of degraded branch/contact-centre throughput.',
             N'ثلاثة أيام عمل كاملة بإنتاجية فروع ومراكز اتصال متدنية.',
             'MEDIUM', 1),
            (@bia_id, 1, '72h', 'CUSTOMER_CHURN',        250000.00,
             'Active app uninstalls begin showing in analytics; ~0.4% of retail base initiates account-close intent.',
             N'تبدأ إلغاءات تثبيت التطبيق بالظهور في التحليلات؛ ~0.4% من قاعدة الأفراد يبدأ إجراءات إغلاق الحساب.',
             'LOW', 1),
            (@bia_id, 1, '72h', 'CONTRACTUAL_PENALTY',  0.00,
             'Merchant-acquirer SLA tier-1 (24-72h) — already included in 24h bucket; tier-2 fires after 72h.',
             N'الشريحة الأولى من اتفاقيات التجار (24-72) محتسبة في خانة 24 ساعة؛ تُفعَّل الشريحة الثانية بعد 72 ساعة.',
             'HIGH', 0);

        -- 1w band
        INSERT INTO bcm.bia_quantitative_loss_line
            (bia_quantitative_id, organisation_id, time_band, loss_type, amount_sar,
             basis_narrative_en, basis_narrative_ar, confidence, is_estimate)
        VALUES
            (@bia_id, 1, '1w', 'REVENUE_LOSS',         42000000.00,
             'Six additional days; SARIE/SADAD competitors capture sustained share of bill-payment volume.',
             N'ستة أيام إضافية؛ يكتسب منافسو سريع/سداد حصةً مستدامة من حجم دفع الفواتير.',
             'HIGH', 1),
            (@bia_id, 1, '1w', 'REGULATORY_FINE',     12000000.00,
             'SAMA escalation to formal supervisory action; CMA listed-bank disclosure obligation. NCA ECC-2 non-compliance referral.',
             N'تصعيد ساما لإجراء رقابي رسمي؛ التزام الإفصاح للبنك المُدرَج لدى هيئة السوق المالية. إحالة عدم امتثال ECC-2 إلى الهيئة الوطنية للأمن السيبراني.',
             'MEDIUM', 1),
            (@bia_id, 1, '1w', 'REPUTATIONAL_DAMAGE',  8000000.00,
             'Brand-equity erosion measurable in tracker surveys; competitor switching campaigns from Alinma/SNB visible on X/TikTok.',
             N'تآكل قياس الأثر العلامة التجارية يظهر في استبيانات التتبُّع؛ حملات تحوُّل من الإنماء/الأهلي مرئية على X/TikTok.',
             'LOW', 1),
            (@bia_id, 1, '1w', 'OPERATIONAL_RECOVERY', 2800000.00,
             'Sustained DR site operations cost + reconciliation backlog clearing (manual SAR clearing entries × 7 days).',
             N'تكلفة تشغيل موقع التعافي المستدامة + تصفية عمل التسويات المتراكمة (قيود مقاصة ريال يدوية × 7 أيام).',
             'HIGH', 1),
            (@bia_id, 1, '1w', 'LEGAL_COSTS',          1100000.00,
             'Class-action exposure assessment retained; regulator-response counsel embedded.',
             N'تقييم الانكشاف على دعوى جماعية مُتعاقَد عليه؛ محامو ردود الجهات التنظيمية متفرّغون.',
             'MEDIUM', 1),
            (@bia_id, 1, '1w', 'PRODUCTIVITY_LOSS',    1900000.00,
             'Whole-of-bank degraded posture for a full work-week.',
             N'وضع إنتاجية متدنّ على مستوى البنك كاملاً لأسبوع عمل كامل.',
             'MEDIUM', 1),
            (@bia_id, 1, '1w', 'CUSTOMER_CHURN',       2800000.00,
             '~1.2% of retail base completes account-close; CLV-weighted loss.',
             N'~1.2% من قاعدة الأفراد يكمل إغلاق الحساب؛ خسارة موزونة بقيمة العميل.',
             'MEDIUM', 1),
            (@bia_id, 1, '1w', 'CONTRACTUAL_PENALTY',   800000.00,
             'Merchant-acquirer tier-2 SLA penalties.',
             N'غرامات الشريحة الثانية من اتفاقيات التُجار.',
             'HIGH', 1);

        -- 2w band
        INSERT INTO bcm.bia_quantitative_loss_line
            (bia_quantitative_id, organisation_id, time_band, loss_type, amount_sar,
             basis_narrative_en, basis_narrative_ar, confidence, is_estimate)
        VALUES
            (@bia_id, 1, '2w', 'REVENUE_LOSS',         78000000.00,
             'Two-week sustained outage erodes ~22% of monthly transaction-fee income.',
             N'انقطاع مستمر لأسبوعين يآكل ~22% من دخل عمولات المعاملات الشهري.',
             'HIGH', 1),
            (@bia_id, 1, '2w', 'REGULATORY_FINE',     22000000.00,
             'SAMA enforcement notice; potential licensing restrictions; PDPL repeat-breach multiplier (Article 36).',
             N'إشعار إنفاذ من ساما؛ قيود ترخيص محتملة؛ مضاعِف تكرار الخرق وفق المادة 36 من نظام حماية البيانات الشخصية.',
             'MEDIUM', 1),
            (@bia_id, 1, '2w', 'REPUTATIONAL_DAMAGE', 14000000.00,
             'Two-week visibility window — sustained competitor share gain; corporate-banking client RFP exits.',
             N'نافذة ظهور لأسبوعين — مكاسب مستدامة لحصة المنافسين؛ خروج عملاء الخدمات المصرفية للشركات من العطاءات.',
             'LOW', 1),
            (@bia_id, 1, '2w', 'OPERATIONAL_RECOVERY', 5500000.00,
             'Extended DR/BAU bridge state; data-reconciliation specialist engagement at scale.',
             N'حالة جسر طويلة بين التعافي والتشغيل المعتاد؛ استعانة بخبراء تسوية البيانات على نطاق واسع.',
             'HIGH', 1),
            (@bia_id, 1, '2w', 'LEGAL_COSTS',          2200000.00,
             'Class-action filings expected at the 14-day mark; corporate-defence ramp.',
             N'يُتوقَّع تقديم دعاوى جماعية عند الأسبوع الثاني؛ تصعيد الدفاع المؤسسي.',
             'MEDIUM', 1),
            (@bia_id, 1, '2w', 'PRODUCTIVITY_LOSS',    3800000.00,
             'Two full degraded business weeks.',
             N'أسبوعا عمل كاملان بإنتاجية متدنية.',
             'MEDIUM', 1),
            (@bia_id, 1, '2w', 'CUSTOMER_CHURN',       6000000.00,
             '~2.6% retail base completes close; corporate-banking attrition begins.',
             N'~2.6% من قاعدة الأفراد يكمل الإغلاق؛ يبدأ تآكل قاعدة الشركات.',
             'MEDIUM', 1),
            (@bia_id, 1, '2w', 'CONTRACTUAL_PENALTY',  1700000.00,
             'Merchant SLA tier-3 + corporate-banking compensation triggers.',
             N'الشريحة الثالثة لاتفاقيات التجار + مُحفِّزات تعويض الخدمات المصرفية للشركات.',
             'HIGH', 1);

        -- 1m band
        INSERT INTO bcm.bia_quantitative_loss_line
            (bia_quantitative_id, organisation_id, time_band, loss_type, amount_sar,
             basis_narrative_en, basis_narrative_ar, confidence, is_estimate)
        VALUES
            (@bia_id, 1, '1m', 'REVENUE_LOSS',        152000000.00,
             'Full-month outage scenario — recovery basis effectively zero for the duration.',
             N'سيناريو انقطاع شهر كامل — أساس الاسترداد صفر فعلياً طيلة المدة.',
             'MEDIUM', 1),
            (@bia_id, 1, '1m', 'REGULATORY_FINE',     35000000.00,
             'Composite ceiling — SAMA, PDPL, CMA, NCA stacked.',
             N'سقف مُركَّب — ساما + نظام حماية البيانات + هيئة السوق المالية + الهيئة الوطنية للأمن السيبراني.',
             'LOW', 1),
            (@bia_id, 1, '1m', 'REPUTATIONAL_DAMAGE', 22000000.00,
             'Sustained brand damage modelled vs prior MENA banking-outage benchmarks.',
             N'ضرر علامة تجارية مستدام مُقارَن بمعايير انقطاعات مصرفية سابقة في منطقة الشرق الأوسط وشمال أفريقيا.',
             'LOW', 1),
            (@bia_id, 1, '1m', 'OPERATIONAL_RECOVERY',12000000.00,
             'Full rebuild scenario; T24/Murex consultancy team residency.',
             N'سيناريو إعادة بناء كامل؛ بقاء فريق استشاري T24/Murex مقيم.',
             'MEDIUM', 1),
            (@bia_id, 1, '1m', 'LEGAL_COSTS',          4500000.00,
             'Multi-jurisdiction class-action exposure (KSA + GCC corporates).',
             N'انكشاف دعاوى جماعية متعددة الولايات (المملكة + شركات دول الخليج).',
             'MEDIUM', 1),
            (@bia_id, 1, '1m', 'PRODUCTIVITY_LOSS',    7800000.00,
             'Whole-of-bank impacted for a month.',
             N'تأثُّر البنك بالكامل لشهر.',
             'MEDIUM', 1),
            (@bia_id, 1, '1m', 'CUSTOMER_CHURN',      11000000.00,
             '~5% retail base; corporate attrition firmed.',
             N'~5% من قاعدة الأفراد؛ تآكل قاعدة الشركات يثبت.',
             'LOW', 1),
            (@bia_id, 1, '1m', 'CONTRACTUAL_PENALTY',  900000.00,
             'Cap reached — most contracts hit per-incident ceiling by week three.',
             N'بلوغ الحد الأعلى — معظم العقود تصل سقفها لكل حادثة بحلول الأسبوع الثالث.',
             'HIGH', 1);
    END
END
GO

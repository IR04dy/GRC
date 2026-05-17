-- V39 — Emerging risk management (Saudi GRC §3.1 + gap analysis item 1).
--
-- Tracks signals + horizon-scanning items that are not yet registered
-- risks. Different problem domain than risk.risks: emerging risks are
-- about *spotting* a future threat before it crystalises. Once a
-- signal matures, it gets escalated to a regular risk.row.
--
-- Lifecycle:  HORIZON → MONITORING → (ESCALATED | MATURED | DISMISSED)
--   HORIZON     just spotted; minimal commentary
--   MONITORING  tracking signals; periodic review
--   ESCALATED   moved to risk.risks (linked_risk_id set)
--   MATURED     no longer "emerging" — it's now a real materialised risk
--   DISMISSED   false signal / no longer relevant

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'risk.emerging_risks', N'U') IS NULL
BEGIN
    CREATE TABLE risk.emerging_risks (
        id                      BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id         BIGINT NOT NULL,

        risk_code               NVARCHAR(64)  NOT NULL,    -- ERX-2026-001
        title_en                NVARCHAR(255) NOT NULL,
        title_ar                NVARCHAR(255) NULL,
        description_en          NVARCHAR(MAX) NULL,
        description_ar          NVARCHAR(MAX) NULL,

        -- REGULATORY | GEOPOLITICAL | TECHNOLOGY | ENVIRONMENTAL | ECONOMIC | SOCIAL | OTHER
        category                NVARCHAR(20)  NOT NULL,
        -- HORIZON_SCAN | INDUSTRY_REPORT | NEWS | REGULATOR | INTERNAL_INTEL | OTHER
        source                  NVARCHAR(20)  NOT NULL DEFAULT N'HORIZON_SCAN',
        source_url              NVARCHAR(500) NULL,
        source_citation         NVARCHAR(500) NULL,

        -- NEAR_TERM_6M | MID_TERM_12M | LONG_TERM_24M
        horizon                 NVARCHAR(20)  NOT NULL,
        -- SLOW | MEDIUM | FAST
        velocity                NVARCHAR(10)  NOT NULL DEFAULT N'MEDIUM',
        -- WEAK | MODERATE | STRONG | CONFIRMED
        signal_strength         NVARCHAR(15)  NOT NULL DEFAULT N'WEAK',

        estimated_likelihood    INT           NULL,           -- 1-5
        estimated_impact        INT           NULL,           -- 1-5

        mitigation_thoughts_en  NVARCHAR(MAX) NULL,
        mitigation_thoughts_ar  NVARCHAR(MAX) NULL,

        -- Cross-link when ESCALATED
        linked_risk_id          BIGINT        NULL,
        linked_risk_code        NVARCHAR(64)  NULL,

        -- HORIZON | MONITORING | ESCALATED | MATURED | DISMISSED
        status                  NVARCHAR(20)  NOT NULL DEFAULT N'HORIZON',

        first_observed_at       DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        last_reviewed_at        DATETIME2(3)  NULL,
        last_reviewed_by        BIGINT        NULL,
        last_review_notes       NVARCHAR(MAX) NULL,
        next_review_due         DATE          NULL,

        escalated_at            DATETIME2(3)  NULL,
        escalated_by            BIGINT        NULL,
        matured_at              DATETIME2(3)  NULL,
        dismissed_at            DATETIME2(3)  NULL,
        dismiss_reason          NVARCHAR(500) NULL,

        owner                   NVARCHAR(255) NULL,
        tags                    NVARCHAR(MAX) NULL,           -- CSV

        external_id             NVARCHAR(64)  NULL,
        is_deleted              SMALLINT      NOT NULL DEFAULT 0,
        is_active               SMALLINT      NOT NULL DEFAULT 1,
        deleted_at              DATETIME2(3)  NULL,
        deleted_by              BIGINT        NULL,
        created_at              DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT        NULL,
        updated_at              DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by              BIGINT        NULL,
        row_version             BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_erisk_category CHECK (category IN
            (N'REGULATORY', N'GEOPOLITICAL', N'TECHNOLOGY', N'ENVIRONMENTAL',
             N'ECONOMIC', N'SOCIAL', N'OTHER')),
        CONSTRAINT CK_erisk_source CHECK (source IN
            (N'HORIZON_SCAN', N'INDUSTRY_REPORT', N'NEWS', N'REGULATOR',
             N'INTERNAL_INTEL', N'OTHER')),
        CONSTRAINT CK_erisk_horizon CHECK (horizon IN
            (N'NEAR_TERM_6M', N'MID_TERM_12M', N'LONG_TERM_24M')),
        CONSTRAINT CK_erisk_velocity CHECK (velocity IN (N'SLOW', N'MEDIUM', N'FAST')),
        CONSTRAINT CK_erisk_signal CHECK (signal_strength IN
            (N'WEAK', N'MODERATE', N'STRONG', N'CONFIRMED')),
        CONSTRAINT CK_erisk_status CHECK (status IN
            (N'HORIZON', N'MONITORING', N'ESCALATED', N'MATURED', N'DISMISSED')),
        CONSTRAINT UQ_erisk_code UNIQUE (organisation_id, risk_code)
    );
END
GO

IF OBJECT_ID(N'risk.emerging_risks', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_erisk_org_status' AND object_id = OBJECT_ID(N'risk.emerging_risks'))
BEGIN
    CREATE INDEX IX_erisk_org_status ON risk.emerging_risks (organisation_id, status)
        WHERE is_deleted = 0;
END
GO

-- ═══ Demo seed — 10 emerging risks across 6 categories + 5 statuses ═
IF OBJECT_ID(N'risk.emerging_risks', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM risk.emerging_risks WHERE risk_code = N'ERX-DEMO-01')
BEGIN
    INSERT INTO risk.emerging_risks
        (organisation_id, risk_code, title_en, title_ar, description_en, description_ar,
         category, source, source_citation, horizon, velocity, signal_strength,
         estimated_likelihood, estimated_impact,
         mitigation_thoughts_en, mitigation_thoughts_ar,
         linked_risk_code, status,
         first_observed_at, last_reviewed_at, last_reviewed_by, last_review_notes, next_review_due,
         escalated_at, escalated_by, matured_at, dismissed_at, dismiss_reason,
         owner, tags, external_id)
    VALUES
        -- 1. HORIZON regulatory — SAMA crypto guidelines
        (1, N'ERX-DEMO-01',
         N'SAMA crypto-asset custody guidelines (anticipated)',
         N'إرشادات حفظ الأصول المشفرة المتوقعة من ساما',
         N'SAMA signalled in Q1 2026 a forthcoming circular on virtual-asset custody for licensed banks. Scope unclear; expected H2 2026.',
         N'أشارت ساما في الربع الأول 2026 إلى تعميم قادم بشأن حفظ الأصول الافتراضية للبنوك المرخصة. النطاق غير واضح؛ متوقع النصف الثاني 2026.',
         N'REGULATORY', N'REGULATOR', N'SAMA financial-stability bulletin Q1 2026, p.34',
         N'MID_TERM_12M', N'MEDIUM', N'MODERATE',
         3, 4,
         N'Engage external counsel for proactive review; map gaps against FATF crypto guidance.',
         N'إشراك مستشار خارجي للمراجعة الاستباقية؛ وضع خريطة الفجوات مقابل إرشادات FATF للعملات المشفرة.',
         NULL, N'HORIZON',
         DATEADD(DAY, -25, SYSUTCDATETIME()), NULL, NULL, NULL,
         DATEADD(MONTH, 1, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'Head of Compliance', N'SAMA,CRYPTO,REGULATORY',
         N'ERX-DEMO-01'),

        -- 2. MONITORING geopolitical — Red Sea shipping
        (1, N'ERX-DEMO-02',
         N'Red Sea shipping disruption — recurring',
         N'اضطراب الشحن في البحر الأحمر - متكرر',
         N'Houthi attacks on commercial shipping driving sustained 30-40% premium on Red Sea freight insurance through 2026. Direct impact on imported equipment lead time.',
         N'هجمات الحوثيين على الشحن التجاري ترفع علاوة التأمين على الشحن في البحر الأحمر 30-40% خلال 2026. أثر مباشر على زمن تسليم المعدات المستوردة.',
         N'GEOPOLITICAL', N'NEWS', N'Lloyd''s List + IMO monthly bulletin 2026-04',
         N'NEAR_TERM_6M', N'FAST', N'STRONG',
         4, 3,
         N'Maintain 60-day inventory buffer on Tier-1 components; negotiate Cape route premium with primary forwarder.',
         N'الحفاظ على مخزون 60 يوماً للمكونات الأساسية؛ التفاوض على علاوة طريق رأس الرجاء الصالح مع شركة الشحن الأساسية.',
         NULL, N'MONITORING',
         DATEADD(DAY, -90, SYSUTCDATETIME()), DATEADD(DAY, -10, SYSUTCDATETIME()), 1,
         N'Premium stabilised at 35% for the second consecutive month. No change to mitigation.',
         DATEADD(MONTH, 1, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'COO', N'GEOPOLITICAL,SHIPPING,SUPPLY_CHAIN',
         N'ERX-DEMO-02'),

        -- 3. ESCALATED technology — Quantum-safe crypto deadline (linked to a real risk)
        (1, N'ERX-DEMO-03',
         N'Post-quantum cryptography mandate (anticipated 2028)',
         N'تفويض التشفير المقاوم للكم المتوقع 2028',
         N'NIST PQC standards ratified 2024. NCA expected to mandate PQC migration for critical systems by 2028. Inventory of all TLS endpoints required.',
         N'تم التصديق على معايير PQC من NIST 2024. متوقع أن تفوض NCA الترحيل إلى PQC للأنظمة الحساسة بحلول 2028. مطلوب جرد جميع نقاط نهاية TLS.',
         N'TECHNOLOGY', N'INDUSTRY_REPORT', N'NIST FIPS 203/204/205 + NCA roadmap 2025',
         N'LONG_TERM_24M', N'SLOW', N'CONFIRMED',
         4, 4,
         N'Spawn PQC inventory + migration roadmap as a registered risk; align with NCA CSCC update.',
         N'إنشاء جرد PQC + خريطة طريق الترحيل كمخاطرة مسجلة؛ التوافق مع تحديث NCA CSCC.',
         N'RSK-2026-031', N'ESCALATED',
         DATEADD(DAY, -180, SYSUTCDATETIME()), DATEADD(DAY, -90, SYSUTCDATETIME()), 2,
         N'Escalated to risk register as RSK-2026-031 after NCA roadmap publication.',
         NULL,
         DATEADD(DAY, -90, SYSUTCDATETIME()), 1, NULL, NULL, NULL,
         N'CISO', N'NIST,PQC,NCA,CRYPTOGRAPHY',
         N'ERX-DEMO-03'),

        -- 4. MONITORING regulatory — PDPL 2024 enforcement uptick
        (1, N'ERX-DEMO-04',
         N'SDAIA PDPL enforcement uptick (DSR penalties)',
         N'تصاعد إنفاذ سدايا لنظام حماية البيانات الشخصية (غرامات حقوق أصحاب البيانات)',
         N'SDAIA published 3 enforcement decisions in Q1 2026 with penalties for late DSR responses (>30 days). Pattern: financial sector targeted first.',
         N'نشرت سدايا 3 قرارات إنفاذ في الربع الأول 2026 مع غرامات على ردود حقوق أصحاب البيانات المتأخرة (>30 يوماً). النمط: استهداف القطاع المالي أولاً.',
         N'REGULATORY', N'REGULATOR', N'SDAIA enforcement bulletin 2026-Q1 (3 decisions)',
         N'NEAR_TERM_6M', N'FAST', N'STRONG',
         4, 3,
         N'Audit current DSR SLA performance (target <25 days for buffer); train DSR team on SDAIA 2025 guidance.',
         N'تدقيق أداء اتفاقية مستوى الخدمة الحالية لحقوق أصحاب البيانات (الهدف <25 يوماً كهامش)؛ تدريب الفريق على إرشادات سدايا 2025.',
         NULL, N'MONITORING',
         DATEADD(DAY, -55, SYSUTCDATETIME()), DATEADD(DAY, -5, SYSUTCDATETIME()), 3,
         N'DSR SLA currently averaging 18 days; on track. Continue monitoring SDAIA bulletins.',
         DATEADD(MONTH, 1, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'DPO', N'SDAIA,PDPL,DSR,ENFORCEMENT',
         N'ERX-DEMO-04'),

        -- 5. HORIZON technology — Generative AI risk
        (1, N'ERX-DEMO-05',
         N'Generative-AI exposure in regulated workflows',
         N'تعرض الذكاء الاصطناعي التوليدي في تدفقات العمل الخاضعة للتنظيم',
         N'Employees using public LLMs (ChatGPT, Gemini) for client data summarisation. PDPL exposure if customer PII leaks into training data. Shadow-IT scanning shows 240 usage events / month.',
         N'الموظفون يستخدمون نماذج لغوية عامة (ChatGPT، Gemini) لتلخيص بيانات العملاء. تعرض لنظام حماية البيانات الشخصية إذا تسربت معلومات العملاء إلى بيانات التدريب. مسح ظل تقنية المعلومات يظهر 240 حدث استخدام شهرياً.',
         N'TECHNOLOGY', N'INTERNAL_INTEL', N'Netskope CASB scan results 2026-04',
         N'NEAR_TERM_6M', N'FAST', N'CONFIRMED',
         4, 4,
         N'Roll out enterprise GenAI policy + Copilot for M365 (data residency in tenant); block consumer LLM domains via DNS.',
         N'نشر سياسة المؤسسة للذكاء الاصطناعي التوليدي + Copilot لـ M365 (إقامة البيانات في المستأجر)؛ حظر نطاقات LLMs الاستهلاكية عبر DNS.',
         NULL, N'HORIZON',
         DATEADD(DAY, -15, SYSUTCDATETIME()), NULL, NULL, NULL,
         DATEADD(WEEK, 2, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'CISO', N'AI,SHADOW_IT,DLP,PDPL',
         N'ERX-DEMO-05'),

        -- 6. DISMISSED — Crypto banking ban rumour (turned out false)
        (1, N'ERX-DEMO-06',
         N'Rumoured KSA crypto-exchange ban (2026-Q2)',
         N'إشاعة حظر منصات العملات المشفرة في المملكة (الربع الثاني 2026)',
         N'Social-media rumours of imminent SAMA ban on crypto exchanges. Investigated; no regulatory basis found; SAMA spokesperson denied 2026-04-08.',
         N'إشاعات على وسائل التواصل بحظر وشيك من ساما على منصات العملات المشفرة. تم التحقيق؛ لا أساس تنظيمي؛ المتحدث باسم ساما نفى 2026-04-08.',
         N'REGULATORY', N'NEWS', N'X/Twitter trend cluster + SAMA press denial 2026-04-08',
         N'NEAR_TERM_6M', N'FAST', N'WEAK',
         1, 2,
         N'No mitigation needed; signal dismissed.',
         N'لا حاجة للتخفيف؛ تم رفض الإشارة.',
         NULL, N'DISMISSED',
         DATEADD(DAY, -40, SYSUTCDATETIME()), DATEADD(DAY, -33, SYSUTCDATETIME()), 1,
         N'SAMA spokesperson denied 2026-04-08; signal dismissed.',
         NULL,
         NULL, NULL, NULL,
         DATEADD(DAY, -33, SYSUTCDATETIME()),
         N'False signal — SAMA spokesperson denied 2026-04-08. Closing as DISMISSED.',
         N'Head of Compliance', N'CRYPTO,RUMOUR,DISMISSED',
         N'ERX-DEMO-06'),

        -- 7. MATURED — Cloud cost spike (it happened)
        (1, N'ERX-DEMO-07',
         N'AWS Riyadh region cost escalation',
         N'تصاعد تكاليف منطقة AWS الرياض',
         N'AWS pricing for the Riyadh region drifted 18% in 2026-Q1 vs Bahrain. Materialised as a real cost overrun against the cloud budget; treated as a regular financial risk now.',
         N'تسعير AWS لمنطقة الرياض ارتفع 18% في الربع الأول 2026 مقابل البحرين. تحقق كتجاوز فعلي في الميزانية السحابية؛ يُعامل الآن كمخاطرة مالية عادية.',
         N'ECONOMIC', N'INTERNAL_INTEL', N'Cloudability cost dashboard 2026-Q1 + Vendor RFP responses',
         N'NEAR_TERM_6M', N'FAST', N'CONFIRMED',
         5, 3,
         N'Move from emerging to materialised — handled as registered risk RSK-2026-022.',
         N'انتقل من ناشئ إلى متحقق - يُعالج كمخاطرة مسجلة RSK-2026-022.',
         N'RSK-2026-022', N'MATURED',
         DATEADD(DAY, -210, SYSUTCDATETIME()), DATEADD(DAY, -120, SYSUTCDATETIME()), 1,
         N'Cost variance crossed appetite; reclassified as MATURED.',
         NULL,
         NULL, NULL,
         DATEADD(DAY, -120, SYSUTCDATETIME()), NULL, NULL,
         N'CFO', N'AWS,CLOUD_COST,FINANCIAL',
         N'ERX-DEMO-07'),

        -- 8. MONITORING environmental — Water stress
        (1, N'ERX-DEMO-08',
         N'Riyadh datacentre water stress (cooling)',
         N'الإجهاد المائي لمركز بيانات الرياض (التبريد)',
         N'NWC tariff for industrial water rose 22% in 2026-Q1; long-term outlook for KSA water availability pressured by Vision-2030 industrial growth. Cooling cost up 14% YoY at the Riyadh primary.',
         N'تعرفة الشركة الوطنية للمياه للمياه الصناعية ارتفعت 22% في الربع الأول 2026؛ التوقعات طويلة الأمد لتوافر المياه في المملكة مضغوطة بنمو رؤية 2030 الصناعي. تكلفة التبريد ارتفعت 14% سنوياً في الموقع الأساسي بالرياض.',
         N'ENVIRONMENTAL', N'INTERNAL_INTEL', N'NWC tariff schedule 2026 + internal facilities Q1 review',
         N'MID_TERM_12M', N'MEDIUM', N'MODERATE',
         3, 3,
         N'Explore liquid-immersion cooling pilot in Jeddah DR site; benchmark against hyperscaler PUE targets.',
         N'استكشاف تجربة التبريد بالغمر السائل في موقع التعافي بجدة؛ المقارنة مع أهداف PUE لمزودي الحوسبة السحابية الكبار.',
         NULL, N'MONITORING',
         DATEADD(DAY, -75, SYSUTCDATETIME()), DATEADD(DAY, -20, SYSUTCDATETIME()), 1,
         N'Liquid-immersion pilot scoped; capex estimate due 2026-Q3.',
         DATEADD(MONTH, 2, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'Head of Facilities', N'WATER,COOLING,ESG,VISION2030',
         N'ERX-DEMO-08'),

        -- 9. HORIZON social — Saudi workforce expectations
        (1, N'ERX-DEMO-09',
         N'Generational workforce expectations (Gen-Z retention)',
         N'توقعات قوة العمل بين الأجيال (الاحتفاظ بالجيل Z)',
         N'KSA Gen-Z attrition rate trending toward 28% vs millennial 14%. Public-sector Vision-2030 hiring competing for the same talent. Risk: institutional knowledge loss.',
         N'معدل دوران الجيل Z في المملكة يتجه نحو 28% مقابل 14% لجيل الألفية. التوظيف في القطاع العام لرؤية 2030 ينافس على نفس المواهب. المخاطر: فقدان المعرفة المؤسسية.',
         N'SOCIAL', N'INDUSTRY_REPORT', N'PwC ME workforce-trends report 2026',
         N'MID_TERM_12M', N'MEDIUM', N'MODERATE',
         3, 3,
         N'Knowledge-base + buddy-rotation pilot; benchmark salary bands against govt Vision-2030 packages.',
         N'قاعدة معرفة + برنامج تدوير الأقران؛ مقارنة نطاقات الرواتب مع حزم رؤية 2030 الحكومية.',
         NULL, N'HORIZON',
         DATEADD(DAY, -30, SYSUTCDATETIME()), NULL, NULL, NULL,
         DATEADD(MONTH, 2, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'CHRO', N'WORKFORCE,GEN_Z,RETENTION,VISION2030',
         N'ERX-DEMO-09'),

        -- 10. MONITORING technology — Open-source license shifts
        (1, N'ERX-DEMO-10',
         N'Open-source license shifts (BSL / SSPL adoption)',
         N'تحولات تراخيص المصدر المفتوح (اعتماد BSL/SSPL)',
         N'Multiple key OSS vendors (HashiCorp, MongoDB-adjacent) shifted from Apache 2.0 to BSL/SSPL since 2023. Our Terraform + Vault stack falls under this; commercial licence cost projected +SAR 1.2m/yr.',
         N'عدة موردي مصادر مفتوحة رئيسيين (HashiCorp ومن يدور حول MongoDB) انتقلوا من Apache 2.0 إلى BSL/SSPL منذ 2023. مكدسنا Terraform + Vault يقع تحت هذا؛ تكلفة الترخيص التجاري متوقعة +1.2 مليون ريال سنوياً.',
         N'TECHNOLOGY', N'INDUSTRY_REPORT', N'OSS license-watch newsletter 2026-Q1 + internal SCA scan',
         N'MID_TERM_12M', N'SLOW', N'STRONG',
         3, 3,
         N'Evaluate OpenTofu (Terraform fork) + community Vault alternatives; budget for commercial licences as fallback.',
         N'تقييم OpenTofu (نسخة Terraform المتفرعة) + بدائل Vault المجتمعية؛ ميزانية تراخيص تجارية كبديل احتياطي.',
         NULL, N'MONITORING',
         DATEADD(DAY, -60, SYSUTCDATETIME()), DATEADD(DAY, -15, SYSUTCDATETIME()), 2,
         N'OpenTofu evaluation 80% complete; recommend migration plan in Sprint 6.',
         DATEADD(MONTH, 1, CAST(SYSUTCDATETIME() AS DATE)),
         NULL, NULL, NULL, NULL, NULL,
         N'Cloud Platform Lead', N'OSS,LICENSING,HASHICORP,TERRAFORM',
         N'ERX-DEMO-10');
END
GO

PRINT N'V39 — risk.emerging_risks: 10 demo rows across 6 categories + 5 statuses applied.';
GO

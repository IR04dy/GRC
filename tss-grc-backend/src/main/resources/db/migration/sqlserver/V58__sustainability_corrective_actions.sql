-- V58 — Corrective Actions (Sustainability · slice 4)
--
-- CAPA-like remediation register that closes the loop on gaps surfaced
-- by V55 (Climate Risks unlinked to ERM at score≥15), V56 (Scope-3
-- coverage <70% — Rule 1), and V57 (Annual Annex open_gaps_count).
--
-- Cross-module composition: one row per remediation item, linked to a
-- source entity via (source_type, source_ref). No FK — service layer
-- joins so future slices that promote CMA/SGI/TCFD to real entities
-- plug into the same register.
--
-- Status machine:
--   OPEN → IN_PROGRESS → COMPLETED → VERIFIED → CLOSED
--   any  → CANCELLED
--
-- Service-layer rules (mirror the spec's CAPA rules):
--   Rule A — verified_by must not equal owner_name (separation of duties)
--   Rule B — COMPLETED requires evidence_ref
--   Rule C — CLOSED is terminal (no transitions out, only soft-delete)
--
-- Seed: 9 actions tied to real V55/V56/V57 gaps — 2 climate-risk, 3 GHG
-- Scope-3 coverage, 2 CMA partial disclosures, 1 SGI commitment, 1 manual
-- ESG↔NCA-ECC crosswalk task. Status spread spans the full lifecycle so
-- dashboards / inbox / verifier views all have meaningful data.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'corrective_action' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.corrective_action (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        action_code              NVARCHAR(40)  NULL,

        -- CLIMATE_RISK | GHG_RECORD | ANNEX_GAP | CMA_DISCLOSURE |
        -- TCFD_ITEM | SGI_COMMITMENT | MANUAL
        source_type              NVARCHAR(30)  NOT NULL,
        source_ref               NVARCHAR(64)  NULL,   -- e.g. CRO-001, GHG-S3-001, 2025-CMA-91

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,

        description_en           NVARCHAR(MAX) NULL,
        description_ar           NVARCHAR(MAX) NULL,

        -- CRITICAL | HIGH | MEDIUM | LOW
        priority                 NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',
        -- OPEN | IN_PROGRESS | COMPLETED | VERIFIED | CLOSED | CANCELLED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'OPEN',

        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,
        assigned_to              NVARCHAR(255) NULL,

        target_date              DATE          NULL,
        started_at               DATETIME2     NULL,
        completed_at             DATETIME2     NULL,
        verified_by              NVARCHAR(255) NULL,
        verified_at              DATETIME2     NULL,
        closed_at                DATETIME2     NULL,

        root_cause_en            NVARCHAR(MAX) NULL,
        root_cause_ar            NVARCHAR(MAX) NULL,
        remediation_plan_en      NVARCHAR(MAX) NULL,
        remediation_plan_ar      NVARCHAR(MAX) NULL,
        evidence_ref             NVARCHAR(255) NULL,

        cost_estimate_sar        DECIMAL(18,2) NULL,
        effort_hours             INT           NULL,

        -- Cross-module pointers — no FK; service joins on demand.
        linked_capa_id           BIGINT        NULL,
        linked_risk_id           BIGINT        NULL,

        notes                    NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_ca_priority CHECK (priority IN (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT ck_ca_status   CHECK (status IN
            (N'OPEN', N'IN_PROGRESS', N'COMPLETED', N'VERIFIED', N'CLOSED', N'CANCELLED')),
        CONSTRAINT ck_ca_source   CHECK (source_type IN (
            N'CLIMATE_RISK', N'GHG_RECORD', N'ANNEX_GAP', N'CMA_DISCLOSURE',
            N'TCFD_ITEM', N'SGI_COMMITMENT', N'MANUAL'))
    );

    CREATE INDEX ix_ca_org_status      ON sust.corrective_action(organisation_id, status);
    CREATE INDEX ix_ca_org_source      ON sust.corrective_action(organisation_id, source_type, source_ref);
    CREATE INDEX ix_ca_org_priority    ON sust.corrective_action(organisation_id, priority, status);
    CREATE INDEX ix_ca_org_target_date ON sust.corrective_action(organisation_id, target_date);
END
GO

-- ─── Seed — 9 actions wired to real V55/V56/V57 gaps ────────────────

IF NOT EXISTS (SELECT 1 FROM sust.corrective_action WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.corrective_action (
        organisation_id, action_code, source_type, source_ref,
        title_en, title_ar,
        description_en, description_ar,
        priority, status,
        owner_name, owner_email, assigned_to,
        target_date, started_at, completed_at, verified_by, verified_at,
        root_cause_en, root_cause_ar,
        remediation_plan_en, remediation_plan_ar,
        evidence_ref, cost_estimate_sar, effort_hours
    )
    VALUES
    -- ── Climate-risk gaps (V55) ─────────────────────────────────────
    (1, N'CA-SUST-001', N'CLIMATE_RISK', N'CRO-001',
     N'Install perimeter flood barriers — Jeddah DC',
     N'تركيب حواجز فيضانات محيطية — مركز بيانات جدة',
     N'Climate risk CRO-001 (Jeddah-DC western-coast flood exposure) sits at score 16 and is unlinked to ERM. Install rated flood barriers and raise critical-equipment plinths by 600 mm to bring the residual score below 10.',
     N'مخاطر CRO-001 (تعرّض مركز بيانات جدة لفيضانات الساحل الغربي) عند درجة 16 وغير مرتبطة بإدارة المخاطر المؤسسية. تركيب حواجز فيضانات معتمدة ورفع قواعد المعدّات الحرجة 600 ملم لتخفيض الدرجة المتبقية دون 10.',
     N'CRITICAL', N'IN_PROGRESS',
     N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa', N'Khalid Al-Otaibi',
     '2026-09-30', '2026-04-08', NULL, NULL, NULL,
     N'Site sits within the Jeddah western-coast 100-year flood envelope per the latest climate-modelling reassessment.',
     N'يقع الموقع ضمن منطقة فيضانات الساحل الغربي لجدة لفترة عودة 100 عام وفق إعادة التقييم المناخي الأخير.',
     N'Phase 1 — install rated barriers along the eastern and northern perimeter (Q2 2026). Phase 2 — raise plinths on Tier-1 racks (Q3 2026). Phase 3 — stress-test drainage capacity.',
     N'المرحلة 1: تركيب حواجز معتمدة على المحيط الشرقي والشمالي (الربع الثاني 2026). المرحلة 2: رفع قواعد الأرفف من المستوى 1 (الربع الثالث 2026). المرحلة 3: اختبار قدرة التصريف.',
     NULL, 4250000, 320),

    (1, N'CA-SUST-002', N'CLIMATE_RISK', N'CRO-002',
     N'Internal carbon-price model — board pre-read',
     N'نموذج السعر الداخلي للكربون — مذكّرة المجلس',
     N'Risk CRO-002 (Saudi carbon-pricing exposure) needs financial modelling at SAR 100, 200, 400 per tCO2e for the next board pre-read; finance has not produced a number yet.',
     N'المخاطر CRO-002 (التعرّض للسعر السعودي للكربون) تتطلّب نمذجة مالية لسيناريوهات 100 و200 و400 ريال للطن في مذكّرة المجلس القادمة؛ لم تُنتج المالية رقماً بعد.',
     N'HIGH', N'OPEN',
     N'Lina Al-Harbi', N'l.alharbi@tss.sa', N'Lina Al-Harbi',
     '2026-07-15', NULL, NULL, NULL, NULL,
     N'No internal carbon-pricing capability — finance treats all carbon-exposure scenarios as zero-cost.',
     N'لا توجد قدرة داخلية لتسعير الكربون — المالية تعامل سيناريوهات التعرّض ككلفة صفرية.',
     N'Stand up a shadow carbon price aligned with CMA Article 91 transition-risk disclosure expectations. Three scenarios — SAR 100 / 200 / 400 per tCO2e by 2030.',
     N'بناء سعر داخلي ظلّي للكربون متوافق مع توقّعات إفصاح مخاطر التحوّل في المادة 91. ثلاثة سيناريوهات: 100/200/400 ريال للطن بحلول 2030.',
     NULL, NULL, 80),

    -- ── GHG Scope-3 coverage gaps (V56 — Rule 1 trigger at <70%) ────
    (1, N'CA-SUST-003', N'GHG_RECORD', N'GHG-S3-001',
     N'Improve purchased-goods coverage from 72% to 85%',
     N'رفع تغطية مشتريات السلع من 72% إلى 85%',
     N'Record GHG-S3-001 (purchased ICT goods) sits at 72% coverage — within Rule 1''s 70% threshold but tight. Push to 85% by binding the procurement-spend extract to the top-12 vendors instead of the current top-50 cutoff.',
     N'سجل GHG-S3-001 (مشتريات تقنية المعلومات) عند تغطية 72% — ضمن حدّ القاعدة 1 لكن ضيّق. الوصول إلى 85% بربط مستخرج إنفاق المشتريات بأكبر 12 مورّداً بدلاً من أكبر 50.',
     N'MEDIUM', N'COMPLETED',
     N'Sara Al-Zahrani', N's.alzahrani@tss.sa', N'Sara Al-Zahrani',
     '2026-04-30', '2026-03-12', '2026-04-22', NULL, NULL,
     N'Procurement extract used a top-50 vendor cutoff which missed 11 long-tail ICT suppliers contributing ~13% of spend.',
     N'استخدم مستخرج المشتريات حدّ أكبر 50 مورّداً، ما فوّت 11 مورّداً صغيراً يساهمون بنحو 13% من الإنفاق.',
     N'Reissue Q4 2025 and Q1 2026 procurement extracts with top-12 vendor binding. Recompute emissions; expect coverage at 85%.',
     N'إعادة إصدار مستخرج المشتريات للربعين الرابع 2025 والأول 2026 بربط أكبر 12 مورّداً. إعادة احتساب الانبعاثات؛ التغطية المتوقّعة 85%.',
     N'Procurement-spend-2026Q1-rebind.xlsx', NULL, 24),

    (1, N'CA-SUST-004', N'GHG_RECORD', N'GHG-S3-003',
     N'Lift employee-commute coverage from 54% to ≥70%',
     N'رفع تغطية تنقّل الموظفين من 54% إلى ≥70%',
     N'Record GHG-S3-003 (employee commuting) sits at 54% coverage. Below the 70% Rule 1 threshold — this single record drives the "Partial Scope-3 disclosure" gap on the 2025 annex.',
     N'سجل GHG-S3-003 (تنقّل الموظفين) عند تغطية 54%. دون عتبة 70% للقاعدة 1 — هذا السجل وحده يولّد ثغرة "إفصاح جزئي للنطاق 3" في ملحق 2025.',
     N'HIGH', N'OPEN',
     N'Mohammed Al-Sulaiman', N'm.alsulaiman@tss.sa', N'Mohammed Al-Sulaiman',
     '2026-08-31', NULL, NULL, NULL, NULL,
     N'The last commute survey covered Riyadh HQ only (54% of headcount). Jeddah and Dammam offices were not surveyed.',
     N'الاستبيان الأخير للتنقّل غطّى مقر الرياض فقط (54% من العمالة). مكاتب جدة والدمام لم تُستطلع.',
     N'Run a combined Riyadh+Jeddah+Dammam commute survey in Q2 2026 ahead of the Q3 reporting cycle. Target a 70%+ response rate per office.',
     N'تشغيل استبيان تنقّل مشترك للرياض وجدة والدمام في الربع الثاني 2026 قبل دورة الربع الثالث. استهداف معدل استجابة ≥70% لكل مكتب.',
     NULL, 85000, 60),

    (1, N'CA-SUST-005', N'GHG_RECORD', N'GHG-S3-004',
     N'Capital-goods coverage — link to full capex register',
     N'تغطية السلع الرأسمالية — الربط بسجل النفقات الرأسمالية الكامل',
     N'Record GHG-S3-004 (capital goods — buildings + DC expansion) at 48% coverage. The hybrid spend+activity method needs the full capex register, currently linked only to the Jeddah-DC project.',
     N'سجل GHG-S3-004 (السلع الرأسمالية — المباني وتوسعة مركز البيانات) بتغطية 48%. الطريقة الهجينة تتطلّب السجل الكامل للنفقات الرأسمالية، المرتبط حالياً بمشروع مركز بيانات جدة فقط.',
     N'MEDIUM', N'IN_PROGRESS',
     N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa', N'Khalid Al-Otaibi',
     '2026-10-15', '2026-04-15', NULL, NULL, NULL,
     N'Capex emission-factor mapping covers only the largest project; smaller IT-refresh capex falls outside the model.',
     N'تخطيط عوامل الانبعاثات للنفقات الرأسمالية يغطّي المشروع الأكبر فقط؛ نفقات تحديث تقنية المعلومات الأصغر خارج النموذج.',
     N'Pull the full FY2025 capex register from ERP; map each line to a CDP capital-goods emission factor; recompute. Target coverage ≥75%.',
     N'سحب سجل النفقات الرأسمالية الكامل للسنة المالية 2025 من تخطيط موارد المؤسسة؛ ربط كل بند بعامل انبعاثات سلع رأسمالية من CDP؛ إعادة الاحتساب. التغطية المستهدفة ≥75%.',
     NULL, NULL, 40),

    -- ── CMA partial-disclosure gaps (V57 — open_gaps_count = 3) ─────
    (1, N'CA-SUST-006', N'CMA_DISCLOSURE', N'2025-CMA-91',
     N'Close partial disclosure — CMA Article 91 (climate risks)',
     N'إغلاق إفصاح جزئي — المادة 91 (المخاطر المناخية)',
     N'The 2025 annex flags CMA Article 91 (climate-related financial-risk disclosure) as Partial. Missing element: quantified financial impact for the SAR-carbon-pricing scenario.',
     N'الملحق السنوي 2025 يصنّف المادة 91 (إفصاح المخاطر المالية المرتبطة بالمناخ) كجزئي. العنصر الناقص: الأثر المالي المقدّر لسيناريو السعر السعودي للكربون.',
     N'HIGH', N'OPEN',
     N'Lina Al-Harbi', N'l.alharbi@tss.sa', N'Lina Al-Harbi',
     '2026-07-31', NULL, NULL, NULL, NULL,
     N'The Article-91 narrative is present but the quantitative financial impact has not been modelled yet (linked to CA-SUST-002).',
     N'النصّ السردي للمادة 91 موجود لكن الأثر المالي الكمّي لم يُنمذَج بعد (مرتبط بـ CA-SUST-002).',
     N'Pull the SAR-carbon-pricing model from CA-SUST-002 into the Article-91 disclosure draft. Quantify EBITDA impact at all three scenarios.',
     N'سحب نموذج السعر السعودي للكربون من CA-SUST-002 إلى مسوّدة إفصاح المادة 91. تقدير الأثر على الأرباح قبل الفوائد والضرائب لكل سيناريو.',
     NULL, NULL, 32),

    (1, N'CA-SUST-007', N'CMA_DISCLOSURE', N'2025-CMA-93',
     N'Close partial disclosure — CMA Article 93 (board oversight)',
     N'إغلاق إفصاح جزئي — المادة 93 (إشراف المجلس)',
     N'The 2025 annex flags CMA Article 93 (board-level sustainability oversight disclosure) as Partial. The board ESG-committee charter exists but Q1 2026 minutes are not yet attached.',
     N'الملحق السنوي 2025 يصنّف المادة 93 (إفصاح إشراف المجلس على الاستدامة) كجزئي. ميثاق لجنة الاستدامة على مستوى المجلس موجود لكن محاضر الربع الأول 2026 غير مرفقة.',
     N'MEDIUM', N'VERIFIED',
     N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa', N'Khalid Al-Otaibi',
     '2026-05-30', '2026-04-02', '2026-04-29', N'Sara Al-Zahrani (CSO)', '2026-05-04',
     N'Q1 2026 board ESG-committee minutes were published on 2026-04-28 but had not been attached to the disclosure packet.',
     N'محاضر لجنة الاستدامة على مستوى المجلس للربع الأول 2026 نُشرت في 28-04-2026 لكن لم تُرفق بحزمة الإفصاح.',
     N'Attach Board-ESG-Q1-2026.pdf to the Article-93 disclosure; regenerate the annex.',
     N'إرفاق ملف Board-ESG-Q1-2026.pdf بإفصاح المادة 93؛ إعادة توليد الملحق.',
     N'Board-ESG-Q1-2026.pdf', NULL, 4),

    -- ── SGI commitment gap ─────────────────────────────────────────
    (1, N'CA-SUST-008', N'SGI_COMMITMENT', N'SGI-NET-ZERO-2060',
     N'Publish interim 2030 milestone — net-zero 2060 trajectory',
     N'نشر هدف المرحلة 2030 — مسار صافي الانبعاثات الصفري 2060',
     N'The Saudi Green Initiative net-zero-by-2060 commitment requires an interim 2030 milestone in the annex. The trajectory chart currently shows 2060 only.',
     N'التزام مبادرة السعودية الخضراء بصافي صفري بحلول 2060 يتطلّب هدف مرحلي 2030 في الملحق. مخطّط المسار يُظهر حالياً 2060 فقط.',
     N'MEDIUM', N'IN_PROGRESS',
     N'Sara Al-Zahrani', N's.alzahrani@tss.sa', N'Sara Al-Zahrani',
     '2026-06-30', '2026-04-22', NULL, NULL, NULL,
     N'The trajectory model was built bottom-up to 2060 but interim-checkpoint reporting was never wired in.',
     N'بُني نموذج المسار تصاعدياً حتى 2060 لكن إعداد نقاط التقدّم المرحلية لم يُربط بعد.',
     N'Define the 2030 absolute target (-35% vs the 2025 baseline) and publish it in the next annex version with quarterly progress-reporting hooks.',
     N'تحديد هدف 2030 المطلق (-35% مقارنة بخط أساس 2025) ونشره في الإصدار التالي من الملحق مع روابط تقدّم ربعي.',
     NULL, NULL, 24),

    -- ── Manual — Saudi cyber-ESG crosswalk ─────────────────────────
    (1, N'CA-SUST-009', N'MANUAL', NULL,
     N'Map ESG controls to NCA ECC v3 for Tier-1 entities',
     N'ربط ضوابط الاستدامة بضوابط الهيئة الوطنية للأمن السيبراني v3 للجهات من الفئة 1',
     N'Sustainability controls (data-centre energy, water, e-waste) are not yet mapped to the NCA ECC v3 control catalog. Required for joint cyber+ESG board reporting at Tier-1 entities.',
     N'ضوابط الاستدامة (طاقة مراكز البيانات والمياه والنفايات الإلكترونية) لم تُربط بكتالوج ضوابط NCA ECC v3. مطلوبة للتقرير المشترك السيبراني-الاستدامة لمجلس الإدارة في الجهات من الفئة 1.',
     N'LOW', N'OPEN',
     N'Mohammed Al-Sulaiman', N'm.alsulaiman@tss.sa', N'Mohammed Al-Sulaiman',
     '2026-12-15', NULL, NULL, NULL, NULL,
     N'No crosswalk exists between the ESG control inventory and NCA ECC v3 — duplicate control evidence in joint audits.',
     N'لا يوجد ربط بين قائمة ضوابط الاستدامة وضوابط NCA ECC v3 — تكرار في أدلّة الضوابط أثناء المراجعات المشتركة.',
     N'Use the existing /control-crosswalk module to build the ESG↔NCA-ECC mappings. Start with the 8 data-centre controls.',
     N'استخدام وحدة /control-crosswalk لبناء ربط الاستدامة بضوابط NCA ECC. البدء بالضوابط الثمانية لمراكز البيانات.',
     NULL, NULL, 64);
END
GO

-- V171 — Slice 10-A: Exercise Scenario Library + scenario→exercise wizard.
--
-- Builds on top of V111 (which already created bcm.exercise_scenarios and
-- seeded 10 scenarios). This migration:
--
--   1. Extends bcm.exercise_scenarios with the columns the library UI
--      needs to surface (scenario_type, complexity, target_audience,
--      framework_alignment, learning_objectives_en/_ar, success_criteria
--      _en/_ar, recommended_observers, recommended_duration_minutes,
--      usage_count). The legacy injects_json / success_criteria_json
--      / objectives_en/_ar columns stay where they are — read-only fall-
--      back content for older callers; the new normalised inject table
--      below is the source of truth for the wizard.
--
--   2. Creates bcm.exercise_scenario_inject — one row per timed inject
--      with EN/AR text, expected_action, type (INFORMATION/ESCALATION/
--      DECISION/MEDIA/REGULATOR) and time_offset_minutes. All standard
--      audit + soft-delete + ROWVERSION columns.
--
--   3. Backfills the 5 highest-value Saudi-native scenarios in the
--      library (3 already in V111, 2 inserted here) with full
--      learning_objectives, success_criteria, recommended observers,
--      and 6 timed injects each → 30 inject rows total. Every scenario
--      and every inject is bilingual EN/AR, Saudi-native (SARIE, SADAD,
--      SAMA SCRO, NCA, PDPL Art.31/32, Hajj/Umrah/Nusuk/Tawakkalna,
--      Aramco DCS, Honeywell, IEC 61511 SIL 3, Tawuniya, etc.).
--
-- The five scenarios with full inject sequences are:
--
--   SCN-RANSOMWARE-CORE-BANKING   — Akira ransom on T24 core banking
--   SCN-OT-IT-BLEED-PETROCHEM     — OT-IT firewall ACL drift at Aramco
--   SCN-HAJJ-DDOS-NUSUK           — pilgrim-season DDoS + fake Nusuk clone
--   SCN-PDPL-BREACH-200K          — PDPL Art.31 72h SDAIA clock
--   SCN-SAMA-REGONLINE-OUTAGE     — STR filing deadline + SCRO MoU
--
-- All inserts are guarded by IF NOT EXISTS — the migration is idempotent
-- and safe to run on a DB that already has the V111 baseline.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ============================================================================
-- 1. Extend bcm.exercise_scenarios with library-friendly columns.
-- ============================================================================

IF COL_LENGTH('bcm.exercise_scenarios', 'scenario_type') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD scenario_type VARCHAR(40) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'complexity') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD complexity VARCHAR(20) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'target_audience') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD target_audience NVARCHAR(200) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'recommended_duration_minutes') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD recommended_duration_minutes INT NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'framework_alignment') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD framework_alignment NVARCHAR(500) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'learning_objectives_en') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD learning_objectives_en NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'learning_objectives_ar') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD learning_objectives_ar NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'success_criteria_en') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD success_criteria_en NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'success_criteria_ar') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD success_criteria_ar NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'recommended_observers') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD recommended_observers NVARCHAR(500) NULL;
GO

IF COL_LENGTH('bcm.exercise_scenarios', 'usage_count') IS NULL
    ALTER TABLE bcm.exercise_scenarios ADD usage_count INT NOT NULL CONSTRAINT DF_exercise_scenarios_usage_count DEFAULT 0;
GO

-- Indexes for the library filters (idempotent — re-run safe).
IF OBJECT_ID('bcm.exercise_scenarios') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_exercise_scenarios_scenario_type' AND object_id = OBJECT_ID('bcm.exercise_scenarios'))
    CREATE INDEX IX_exercise_scenarios_scenario_type ON bcm.exercise_scenarios(scenario_type);
GO

IF OBJECT_ID('bcm.exercise_scenarios') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_exercise_scenarios_complexity' AND object_id = OBJECT_ID('bcm.exercise_scenarios'))
    CREATE INDEX IX_exercise_scenarios_complexity ON bcm.exercise_scenarios(complexity);
GO

IF OBJECT_ID('bcm.exercise_scenarios') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_exercise_scenarios_usage_count' AND object_id = OBJECT_ID('bcm.exercise_scenarios'))
    CREATE INDEX IX_exercise_scenarios_usage_count ON bcm.exercise_scenarios(usage_count DESC);
GO

-- Add scenario_id linkage to bcm.exercises so instantiated runs can be
-- traced back to their library scenario (and so the inject timeline is
-- queryable on the runtime exercise page).
IF COL_LENGTH('bcm.exercises', 'scenario_id') IS NULL
    ALTER TABLE bcm.exercises ADD scenario_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_exercises_scenario_id')
   AND OBJECT_ID('bcm.exercises','U') IS NOT NULL
BEGIN
    ALTER TABLE bcm.exercises
      ADD CONSTRAINT FK_exercises_scenario_id
        FOREIGN KEY (scenario_id) REFERENCES bcm.exercise_scenarios(id);
END
GO

IF OBJECT_ID('bcm.exercises') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_exercises_scenario_id' AND object_id = OBJECT_ID('bcm.exercises'))
    CREATE INDEX IX_exercises_scenario_id ON bcm.exercises(scenario_id) WHERE scenario_id IS NOT NULL;
GO

-- ============================================================================
-- 2. New table bcm.exercise_scenario_inject — one row per timed inject.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'exercise_scenario_inject' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_scenario_inject (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        scenario_id             BIGINT          NOT NULL,
        inject_order            INT             NOT NULL,
        time_offset_minutes     INT             NOT NULL,
        inject_type             VARCHAR(40)     NOT NULL,
        -- INFORMATION | ESCALATION | DECISION | MEDIA | REGULATOR

        inject_text_en          NVARCHAR(2000)  NOT NULL,
        inject_text_ar          NVARCHAR(2000)  NULL,
        expected_action_en      NVARCHAR(1000)  NULL,
        expected_action_ar      NVARCHAR(1000)  NULL,

        -- Standard audit + soft-delete + rowversion
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NULL,
        created_by              BIGINT          NULL,
        updated_by              BIGINT          NULL,
        is_deleted              BIT             NOT NULL DEFAULT 0,
        deleted_at              DATETIME2       NULL,
        deleted_by              BIGINT          NULL,
        row_version             ROWVERSION,

        CONSTRAINT FK_scenario_inject_scenario
            FOREIGN KEY (scenario_id) REFERENCES bcm.exercise_scenarios(id),
        CONSTRAINT CK_scenario_inject_type CHECK (inject_type IN
            ('INFORMATION','ESCALATION','DECISION','MEDIA','REGULATOR'))
    );

    CREATE UNIQUE INDEX UX_scenario_inject_scenario_order
        ON bcm.exercise_scenario_inject(scenario_id, inject_order)
        WHERE is_deleted = 0;

    CREATE INDEX IX_scenario_inject_scenario
        ON bcm.exercise_scenario_inject(scenario_id)
        WHERE is_deleted = 0;
END
GO

-- ============================================================================
-- 3. Seed the 5 high-value scenarios — extend existing rows or insert new.
--    All are flagged is_system=1 so they appear in every tenant's library.
-- ============================================================================

-- 3a. Update existing scenario from V111: EXS-RANSOMWARE-TABLETOP-2026
--     Re-purposed as the canonical "Ransomware on core banking" library
--     entry for the slice 10-A scenario library.
UPDATE bcm.exercise_scenarios
SET scenario_type              = 'TABLETOP',
    complexity                 = 'ADVANCED',
    target_audience            = N'Crisis Manager, CISO, SOC Lead, IT Recovery, Comms Lead, Legal, BCM Manager',
    recommended_duration_minutes = 180,
    framework_alignment        = N'SAMA_CSF,NCA_ECC2,ISO_22301,PDPL,NCA_CSCC',
    learning_objectives_en     = N'1. Validate the 4-hour SAMA OPRES notification window for core-banking incidents. ' +
                                 N'2. Exercise the dual CISO + BCM Manager activation authority for ransomware containment. ' +
                                 N'3. Test the SARIE/SADAD payment-rail rerouting decision tree under board pressure. ' +
                                 N'4. Rehearse the do-not-pay policy stance against SAR-denominated ransom demands. ' +
                                 N'5. Confirm the immutable-backup pool integrity check protocol completes within RTO.',
    learning_objectives_ar     = N'1. التحقق من نافذة إشعار SAMA OPRES (٤ ساعات) لحوادث الأنظمة المصرفية الأساسية. ' +
                                 N'2. تطبيق صلاحية التفعيل المزدوجة (مسؤول أمن المعلومات + مدير BCM) لاحتواء الفدية. ' +
                                 N'3. اختبار شجرة قرار إعادة توجيه مسارات SARIE / SADAD تحت ضغط مجلس الإدارة. ' +
                                 N'4. التدرّب على موقف عدم الدفع تجاه طلبات الفدية المُقوَّمة بالريال السعودي. ' +
                                 N'5. التأكد من إنجاز بروتوكول التحقق من سلامة النسخ غير القابلة للتعديل ضمن نافذة التعافي.',
    success_criteria_en        = N'• SAMA SCRO + NCA notifications drafted within 240 minutes (target: ≤ 240). ' +
                                 N'• Crisis bridge convened with quorum (CISO, BCM Mgr, SOC Lead, IR retainer) within 30 minutes. ' +
                                 N'• Core-banking isolation decision documented with named approver and timestamp. ' +
                                 N'• Customer comms (app banner + SMS in EN/AR) approved by Legal before broadcast. ' +
                                 N'• After-action: at least 3 CAPA items raised against gaps surfaced during play.',
    success_criteria_ar        = N'• مسوّدة إشعار SAMA SCRO و NCA خلال ٢٤٠ دقيقة (الهدف: ≤ ٢٤٠). ' +
                                 N'• انعقاد جسر الأزمات بالنصاب (CISO، مدير BCM، قائد SOC، الجهة الاستشارية) خلال ٣٠ دقيقة. ' +
                                 N'• توثيق قرار عزل الأنظمة المصرفية باسم المعتمِد والطابع الزمني. ' +
                                 N'• اعتماد القانوني لرسائل العملاء (لافتة التطبيق + رسائل قصيرة عربي/إنجليزي) قبل البث. ' +
                                 N'• ورشة الدروس: لا تقل عن ٣ بنود CAPA مفتوحة لمعالجة الثغرات التي ظهرت خلال التمرين.',
    recommended_observers      = N'Internal Audit lead, SAMA liaison officer, Board Risk Committee observer, Independent IR retainer',
    usage_count                = ISNULL(usage_count, 0)
WHERE scenario_code = 'EXS-RANSOMWARE-TABLETOP-2026';
GO

-- 3b. Update existing scenario: EXS-PDPL-BREACH-TABLETOP-2026
UPDATE bcm.exercise_scenarios
SET scenario_type              = 'TABLETOP',
    complexity                 = 'INTERMEDIATE',
    target_audience            = N'DPO, CISO, Legal Counsel, Communications Lead, Customer Service Director, Board Secretary',
    recommended_duration_minutes = 150,
    framework_alignment        = N'PDPL,SDAIA,SAMA_CSF,NCA_ECC2,ISO_27701',
    learning_objectives_en     = N'1. Exercise the PDPL Article 31 72-hour SDAIA notification clock from breach confirmation. ' +
                                 N'2. Validate the Article 32 customer notification decision matrix (when, how, in what language). ' +
                                 N'3. Test the bilingual breach notice template stack (SDAIA portal + customer SMS + customer email). ' +
                                 N'4. Rehearse the Tawuniya cyber-insurance claim activation protocol. ' +
                                 N'5. Confirm board emergency convocation triggers within 4 hours of breach confirmation.',
    learning_objectives_ar     = N'1. تطبيق ساعة المادة ٣١ من نظام حماية البيانات الشخصية (٧٢ ساعة لإشعار سدايا) من لحظة تأكيد الخرق. ' +
                                 N'2. التحقق من مصفوفة قرار إشعار العميل (المادة ٣٢): متى، كيف، بأي لغة. ' +
                                 N'3. اختبار حزمة نماذج الإشعار الثنائية اللغة (بوابة سدايا + رسائل قصيرة + بريد). ' +
                                 N'4. التدرّب على بروتوكول تفعيل مطالبة تأمين سيبراني عبر التعاونية. ' +
                                 N'5. التأكد من تفعيل اجتماع طارئ لمجلس الإدارة خلال ٤ ساعات من تأكيد الخرق.',
    success_criteria_en        = N'• SDAIA notification submission file (Article 31 format) drafted within 60 minutes. ' +
                                 N'• Customer notification decision recorded with named approver and Art.32 rationale. ' +
                                 N'• Tawuniya cyber claim file opened with incident reference + initial loss estimate. ' +
                                 N'• Board emergency convened within 4 hours, with materiality assessment delivered. ' +
                                 N'• Lessons-learned identifies at least one improvement to PDPL incident response runbook.',
    success_criteria_ar        = N'• مسوّدة ملف إشعار سدايا (وفق صيغة المادة ٣١) خلال ٦٠ دقيقة. ' +
                                 N'• توثيق قرار إشعار العميل باسم المعتمِد وبيان المادة ٣٢. ' +
                                 N'• فتح ملف مطالبة التأمين السيبراني لدى التعاونية مع المرجع والتقدير الأولي للخسارة. ' +
                                 N'• انعقاد اجتماع طارئ لمجلس الإدارة خلال ٤ ساعات وتسليم تقييم الجوهرية. ' +
                                 N'• ورشة الدروس تطرح تحسيناً واحداً على الأقل لدليل الاستجابة لحوادث نظام حماية البيانات.',
    recommended_observers      = N'External legal counsel, SDAIA liaison, Tawuniya cyber underwriter, Internal Audit',
    usage_count                = ISNULL(usage_count, 0)
WHERE scenario_code = 'EXS-PDPL-BREACH-TABLETOP-2026';
GO

-- 3c. Update existing scenario: EXS-HAJJ-PEAK-LOAD-2026
--     Re-purposed as the "Pilgrim-season DDoS (Hajj/Umrah)" library entry.
UPDATE bcm.exercise_scenarios
SET scenario_type              = 'FUNCTIONAL',
    complexity                 = 'ADVANCED',
    target_audience            = N'CISO, SOC Lead, Digital Channels Owner, Public Affairs, Mecca Region Liaison, Network Engineering',
    recommended_duration_minutes = 240,
    framework_alignment        = N'NCA_ECC2,SAMA_CSF,ISO_22301,NCA_CSCC',
    learning_objectives_en     = N'1. Test pilgrim-traffic surge handling for the 7 days surrounding Tarwiyah Day at Mecca consular gates. ' +
                                 N'2. Validate fake-app takedown protocol against Nusuk lookalikes in Apple/Google stores + Saudi app catalogues. ' +
                                 N'3. Rehearse the Tawakkalna impersonation domain reporting chain to NCA + Communications & IT Commission. ' +
                                 N'4. Exercise multi-language pilgrim safety comms in Arabic + English + Urdu + Indonesian. ' +
                                 N'5. Confirm the DDoS mitigation runbook produces < 30 minutes time-to-mitigate against 100 Gbps L7 floods.',
    learning_objectives_ar     = N'1. اختبار التعامل مع تدفق حركة الحجاج في الأيام السبعة المحيطة بيوم التروية عند بوابات مكة القنصلية. ' +
                                 N'2. التحقق من بروتوكول إزالة التطبيقات المزيّفة (نسخ نسك المزوّرة) من متاجر آبل / قوقل والمتاجر السعودية. ' +
                                 N'3. التدرّب على سلسلة الإبلاغ عن نطاقات انتحال «توكلنا» إلى NCA وهيئة الاتصالات وتقنية المعلومات. ' +
                                 N'4. اختبار اتصالات سلامة الحجاج بأربع لغات: العربية والإنجليزية والأردو والإندونيسية. ' +
                                 N'5. التأكد من أن دليل تخفيف هجمات حجب الخدمة يحقق زمن استجابة أقل من ٣٠ دقيقة ضد فيضانات الطبقة السابعة بسعة ١٠٠ قيقابت.',
    success_criteria_en        = N'• Anti-DDoS scrubbing activated within 30 minutes; pilgrim-facing services restored within 60 minutes. ' +
                                 N'• Fake-Nusuk takedown requests filed with Apple, Google, and Communications & IT Commission within 90 minutes. ' +
                                 N'• Multi-lingual safety advisory broadcast (AR/EN/UR/ID) approved and dispatched within 120 minutes. ' +
                                 N'• Mecca region pilgrim safety authority briefed at T+60 and again at T+180. ' +
                                 N'• Lessons-learned captures at least one capacity uplift item for next Hajj season.',
    success_criteria_ar        = N'• تفعيل تنقية حركة الـDDoS خلال ٣٠ دقيقة واستعادة الخدمات الموجهة للحجاج خلال ٦٠ دقيقة. ' +
                                 N'• تقديم طلبات إزالة نسك المزوّرة لدى آبل وقوقل وهيئة الاتصالات خلال ٩٠ دقيقة. ' +
                                 N'• بث الإرشاد متعدد اللغات (عربي/إنجليزي/أردو/إندونيسي) معتمَداً ومُرسَلاً خلال ١٢٠ دقيقة. ' +
                                 N'• إحاطة الجهة المختصة بسلامة الحجاج في منطقة مكة عند الدقيقة ٦٠ ومجدداً عند الدقيقة ١٨٠. ' +
                                 N'• ورشة الدروس ترصد بنداً واحداً على الأقل لرفع السعة استعداداً لموسم الحج القادم.',
    recommended_observers      = N'NCA observer, Communications & IT Commission liaison, Mecca region authority, Telco NOC representative',
    usage_count                = ISNULL(usage_count, 0)
WHERE scenario_code = 'EXS-HAJJ-PEAK-LOAD-2026';
GO

-- 3d. New scenario: SCN-OT-IT-BLEED-PETROCHEM (OT-IT bleed at Aramco / petrochemical)
IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenarios WHERE scenario_code = 'SCN-OT-IT-BLEED-PETROCHEM')
BEGIN
    INSERT INTO bcm.exercise_scenarios (
        scenario_code, title_en, title_ar, scenario_category, exercise_type,
        duration_minutes, description_en, description_ar,
        objectives_en, objectives_ar,
        injects_json, success_criteria_json,
        scenario_type, complexity, target_audience, recommended_duration_minutes,
        framework_alignment,
        learning_objectives_en, learning_objectives_ar,
        success_criteria_en, success_criteria_ar,
        recommended_observers, usage_count
    )
    VALUES (
        'SCN-OT-IT-BLEED-PETROCHEM',
        N'OT-IT bleed at petrochemical plant (Aramco-type DCS)',
        N'تسرّب من شبكة التحكم الصناعي إلى شبكة المعلومات في مجمّع بتروكيماوي',
        N'CYBER', N'SIMULATION',
        240,
        N'A firewall ACL drift between the corporate IT zone and the Honeywell Experion DCS plant zone allows a worm to cross into the OT network. SIL 3 safety instrumented system on a sulphur unit trips. The drill exercises the joint OT engineering + IT cyber-incident bridge that Saudi Aramco-style operators must hold under IEC 61511 + NCA OT-CSCC.',
        N'انجراف في قواعد جدار الحماية بين نطاق تقنية المعلومات المؤسسية ونطاق نظام التحكم الموزع (Honeywell Experion) في المصنع يسمح لدودة بالعبور إلى شبكة التشغيل، فينطلق نظام السلامة الآلي (SIL 3) لوحدة الكبريت. يمارس التمرين جسر الاستجابة المشترك بين هندسة التشغيل ومركز الأمن الذي يفرضه IEC 61511 و NCA OT-CSCC.',
        N'OT-IT incident bridge: roles, hand-offs, escalation thresholds.',
        N'جسر استجابة التشغيل/المعلومات: الأدوار والتسليمات وحدود التصعيد.',
        N'[]', N'[]',
        'SIMULATION', 'ADVANCED',
        N'OT Engineering Manager, Plant Manager, CISO, Honeywell Field Service Liaison, HSE Director, Regulator Affairs',
        240,
        N'NCA_OT_CSCC,IEC_61511,IEC_62443,SAMA_CSF,NCA_ECC2,ISO_22301',
        N'1. Exercise the SIL 3 trip-recovery decision tree under simulated cyber pressure (operator hesitation on restart). ' +
        N'2. Test the OT-IT bridge call protocol — who calls plant control room first, plant manager or CISO? ' +
        N'3. Validate the Honeywell Riyadh service escalation path within the 4-hour critical-incident SLA. ' +
        N'4. Rehearse regulator notification to MEIM + NCA for a confirmed OT-side compromise. ' +
        N'5. Stress-test the shutdown decision point — production loss vs. safety integrity restoration.',
        N'١. تطبيق شجرة قرار التعافي من إطلاق SIL 3 تحت ضغط هجوم سيبراني محاكَى (تردّد المشغّل في إعادة التشغيل). ' +
        N'٢. اختبار بروتوكول جسر التشغيل/المعلومات — من يتصل بغرفة التحكم أولاً: مدير المصنع أم مسؤول أمن المعلومات؟ ' +
        N'٣. التحقق من مسار تصعيد خدمة هانيويل الرياض ضمن اتفاقية مستوى الخدمة (٤ ساعات للحوادث الحرجة). ' +
        N'٤. التدرّب على إشعار وزارة الصناعة والثروة المعدنية و NCA باختراق مؤكَّد في جانب التشغيل. ' +
        N'٥. اختبار نقطة قرار الإغلاق — خسارة الإنتاج مقابل استعادة سلامة العمليات.',
        N'• OT-IT bridge convened with both Plant Manager + CISO within 20 minutes of SIL trip. ' +
        N'• Honeywell Riyadh field service ticket opened with P1 severity within 30 minutes. ' +
        N'• Shutdown vs. controlled-restart decision documented with HSE Director sign-off. ' +
        N'• MEIM + NCA notifications drafted within 4 hours of OT-side compromise confirmation. ' +
        N'• Lessons-learned identifies at least one Purdue-model segmentation control to add.',
        N'• انعقاد جسر التشغيل/المعلومات بحضور مدير المصنع ومسؤول أمن المعلومات خلال ٢٠ دقيقة من إطلاق SIL. ' +
        N'• فتح بطاقة خدمة ميدانية لدى هانيويل الرياض بأولوية P1 خلال ٣٠ دقيقة. ' +
        N'• توثيق قرار الإغلاق أو إعادة التشغيل المُتحكَّم به باعتماد مدير الصحة والسلامة والبيئة. ' +
        N'• مسوّدات إشعار وزارة الصناعة و NCA خلال ٤ ساعات من تأكيد الاختراق في جانب التشغيل. ' +
        N'• ورشة الدروس ترصد ضابطاً إضافياً على الأقل لتقسيم نموذج بيردو (Purdue).',
        N'IEC 61511 functional safety auditor, NCA OT observer, Honeywell field engineer, HSE independent',
        0
    );
END
GO

-- 3e. New scenario: SCN-SAMA-REGONLINE-OUTAGE (SAMA RegOnline outage)
IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenarios WHERE scenario_code = 'SCN-SAMA-REGONLINE-OUTAGE')
BEGIN
    INSERT INTO bcm.exercise_scenarios (
        scenario_code, title_en, title_ar, scenario_category, exercise_type,
        duration_minutes, description_en, description_ar,
        objectives_en, objectives_ar,
        injects_json, success_criteria_json,
        scenario_type, complexity, target_audience, recommended_duration_minutes,
        framework_alignment,
        learning_objectives_en, learning_objectives_ar,
        success_criteria_en, success_criteria_ar,
        recommended_observers, usage_count
    )
    VALUES (
        'SCN-SAMA-REGONLINE-OUTAGE',
        N'SAMA RegOnline outage during STR filing window',
        N'انقطاع منصة SAMA RegOnline أثناء نافذة تقديم تقارير المعاملات المشبوهة',
        N'REGULATORY', N'TABLETOP',
        120,
        N'The SAMA RegOnline portal goes dark while the bank is mid-submission of a batch of suspicious transaction reports (STRs) approaching the statutory filing deadline. The drill exercises the retrospective-filing protocol, the SCRO MoU activation for secure email backchannel, and the internal audit involvement required by SAMA Anti-Financial Crime regulation §6.4.',
        N'تنقطع منصة SAMA RegOnline أثناء إرسال البنك دفعة تقارير معاملات مشبوهة (STR) قبيل الموعد القانوني للتقديم. يمارس التمرين بروتوكول التقديم بأثر رجعي وتفعيل مذكرة التفاهم مع وحدة العلاقات التنظيمية في ساما لاستخدام قناة البريد المؤمَّن البديلة، ومشاركة المراجعة الداخلية المطلوبة بموجب نظام مكافحة الجرائم المالية §٦٫٤.',
        N'Regulatory submission failover and retrospective-filing.',
        N'تجاوز فشل التقديم التنظيمي والتقديم بأثر رجعي.',
        N'[]', N'[]',
        'TABLETOP', 'INTERMEDIATE',
        N'MLRO, Compliance Director, Internal Audit Lead, IT Service Owner, SAMA Liaison Officer, CRO',
        120,
        N'SAMA_AFC,SAMA_CSF,NCA_ECC2,ISO_22301',
        N'1. Exercise the bank''s retrospective-filing protocol for STR submissions missed due to platform outage. ' +
        N'2. Test the SAMA SCRO MoU activation for the secure-email backchannel. ' +
        N'3. Validate internal audit involvement timing per SAMA AFC §6.4. ' +
        N'4. Rehearse the alternative-submission documentation pack (delivery proof + hash + acknowledgement). ' +
        N'5. Confirm the bank''s position when a portal returns mid-filing — re-submit or rely on backchannel acknowledgement.',
        N'١. تطبيق بروتوكول التقديم بأثر رجعي لتقارير المعاملات المشبوهة التي فاتت بسبب انقطاع المنصة. ' +
        N'٢. اختبار تفعيل مذكرة التفاهم مع وحدة SCRO في ساما لاستخدام قناة البريد المؤمَّن البديلة. ' +
        N'٣. التحقق من توقيت إشراك المراجعة الداخلية وفقاً للمادة ٦٫٤ من نظام مكافحة الجرائم المالية. ' +
        N'٤. التدرّب على حزمة توثيق التقديم البديل (إثبات التسليم + البصمة الرقمية + سند الاستلام). ' +
        N'٥. تثبيت موقف البنك حين تعود المنصة أثناء التقديم: إعادة الإرسال أم الاعتماد على سند البريد البديل.',
        N'• MLRO confirms outage with SAMA OPRES within 20 minutes and obtains backchannel authorisation. ' +
        N'• Backchannel STR submission package (PDF + hash + delivery proof) dispatched before statutory deadline. ' +
        N'• Internal Audit notified within 60 minutes per SAMA AFC §6.4 cadence. ' +
        N'• Retrospective re-filing performed within 24 hours of RegOnline restoration, with cross-reference. ' +
        N'• Lessons-learned identifies at least one improvement to STR-submission resiliency runbook.',
        N'• تأكيد المسؤول عن مكافحة غسل الأموال للانقطاع لدى SAMA OPRES خلال ٢٠ دقيقة والحصول على إذن القناة البديلة. ' +
        N'• إرسال حزمة STR عبر القناة البديلة (PDF + بصمة + إثبات تسليم) قبل الموعد القانوني. ' +
        N'• إبلاغ المراجعة الداخلية خلال ٦٠ دقيقة وفقاً لإيقاع المادة ٦٫٤ من نظام مكافحة الجرائم المالية. ' +
        N'• إعادة التقديم بأثر رجعي خلال ٢٤ ساعة من عودة RegOnline مع الإحالة المتبادلة. ' +
        N'• ورشة الدروس ترصد تحسيناً واحداً على الأقل لمرونة دليل تقديم STR.',
        N'SAMA SCRO liaison, External Audit partner, Internal Audit, MLRO peer reviewer',
        0
    );
END
GO

-- ============================================================================
-- 4. Seed 6 injects each for the 5 scenarios = 30 inject rows.
-- ============================================================================

-- Helper pattern: looked up by scenario_code, idempotent on (scenario_id, inject_order).

-- ─── 4a. SCN-RANSOMWARE on EXS-RANSOMWARE-TABLETOP-2026 ────────────────
DECLARE @sid BIGINT;
SELECT @sid = id FROM bcm.exercise_scenarios WHERE scenario_code = 'EXS-RANSOMWARE-TABLETOP-2026';

IF @sid IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 1)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 1, 0, 'INFORMATION',
        N'T+0: EDR on a core-banking application server flags an Akira ransom note (HOW_TO_DECRYPT.txt) on the shared production storage. Two minutes later, branch teller terminals begin reporting "file system unavailable" errors.',
        N'الدقيقة صفر: يرصد EDR على خادم تطبيق الأنظمة المصرفية ملف فدية من عصابة Akira (HOW_TO_DECRYPT.txt) على وحدة التخزين الإنتاجية. بعد دقيقتين، تبدأ شاشات صرّافي الفروع بالإبلاغ عن أخطاء «نظام الملفات غير متاح».',
        N'SOC opens an incident bridge; CISO is paged; BCM Manager joins; first task is to confirm scope (is it one host or many).',
        N'يفتح مركز عمليات الأمن جسر الحادثة، يُبلَّغ مسؤول أمن المعلومات، ينضمّ مدير BCM؛ المهمة الأولى تأكيد النطاق (مضيف واحد أم أكثر).');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 2)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 2, 10, 'ESCALATION',
        N'T+10: SARIE/SADAD payment rails freeze. Mada switch starts queueing transactions. Branch ATMs respond but balance enquiries fail. The core-banking T24 environment is confirmed compromised across 14 application servers.',
        N'الدقيقة ١٠: تتجمد مسارات SARIE / SADAD. سويتش Mada يبدأ بطابور المعاملات. تستجيب الصرّافات الآلية لكن استعلامات الرصيد تفشل. تأكَّد اختراق بيئة T24 على ١٤ خادم تطبيق.',
        N'Activate dual CISO + BCM Manager authority; isolate the affected VLAN; trigger the SAMA OPRES desk notification protocol (4h window starts now).',
        N'تفعيل الصلاحية المزدوجة (CISO + مدير BCM)؛ عزل الشبكة المتأثرة؛ بدء بروتوكول إشعار مكتب SAMA OPRES (نافذة الـ ٤ ساعات تبدأ الآن).');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 3)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 3, 30, 'REGULATOR',
        N'T+30: SAMA SCRO calls the CISO''s direct line. They have customer complaints on social media and want a status update within 30 minutes. They will dispatch an on-site assessor unless they receive a written incident summary by T+60.',
        N'الدقيقة ٣٠: يتصل مكتب SCRO في ساما مباشرة بمسؤول أمن المعلومات. لديهم شكاوى عملاء على وسائل التواصل ويطلبون إحاطة خلال ٣٠ دقيقة. سيُرسلون مقيِّماً ميدانياً ما لم يستلموا ملخصاً مكتوباً للحادثة قبل الدقيقة ٦٠.',
        N'Compliance Director drafts the written summary; Crisis Manager assigns a single SAMA liaison; CISO commits to a callback time.',
        N'مدير الالتزام يعدّ الملخص المكتوب؛ مدير الأزمات يُعيّن مسؤول اتصال واحداً مع ساما؛ مسؤول أمن المعلومات يلتزم بموعد المعاودة.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 4)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 4, 45, 'MEDIA',
        N'T+45: The attacker posts a sample of 5,000 customer names + national IDs on a Telegram channel as a credibility proof. A local newspaper journalist messages the corporate spokesperson asking for comment within the hour.',
        N'الدقيقة ٤٥: ينشر المهاجم عيّنة من ٥٠٠٠ اسم عميل وأرقام هوية وطنية على قناة تيليجرام كدليل على المصداقية. تتواصل صحيفة محلية مع الناطق الرسمي للبنك طالبةً تعليقاً خلال ساعة.',
        N'Legal + Comms decide on the holding statement; PDPL Article 31 clock starts (72h to SDAIA); the do-not-pay policy is reaffirmed.',
        N'القانوني والاتصالات يقرّران بيان الانتظار؛ تبدأ ساعة المادة ٣١ من نظام حماية البيانات (٧٢ ساعة لسدايا)؛ تتم إعادة تأكيد سياسة عدم الدفع.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 5)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 5, 90, 'DECISION',
        N'T+90: The attacker raises the ransom demand to SAR 50,000,000 in monero. They threaten to release the full 800,000-record customer list within 12 hours unless paid. The board chair calls the CEO directly asking whether the bank should pay.',
        N'الدقيقة ٩٠: يرفع المهاجم طلب الفدية إلى ٥٠٬٠٠٠٬٠٠٠ ريال بعملة مونيرو. يهدّد بإفشاء قائمة الـ ٨٠٠٬٠٠٠ عميل كاملة خلال ١٢ ساعة ما لم يُدفع له. رئيس مجلس الإدارة يتصل بالرئيس التنفيذي مباشرة سائلاً هل يدفع البنك؟',
        N'CEO + Board Chair walk through the do-not-pay policy; CRO presents the residual exposure; insurance retainer is engaged.',
        N'الرئيس التنفيذي ورئيس المجلس يستعرضان سياسة عدم الدفع؛ CRO يعرض الانكشاف المتبقي؛ يُستدعى مستشار التأمين.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 6)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 6, 120, 'MEDIA',
        N'T+120: Two national outlets publish the story. Customer call-centre wait times spike to 45 minutes. A media response is required within 30 minutes — silence will be read as a cover-up.',
        N'الدقيقة ١٢٠: تنشر صحيفتان وطنيتان الخبر. زمن انتظار مركز اتصال العملاء يقفز إلى ٤٥ دقيقة. الردّ الإعلامي مطلوب خلال ٣٠ دقيقة؛ الصمت سيُفسَّر تستّراً.',
        N'Comms Lead delivers the approved public statement (EN/AR); customer-care script update is broadcast to call-centre agents.',
        N'قائد الاتصالات يُصدر البيان العام المعتمد (عربي/إنجليزي)؛ تحديث سيناريو خدمة العملاء يُبَثّ لوكلاء مركز الاتصال.');
END
GO

-- ─── 4b. SCN-OT-IT-BLEED-PETROCHEM ─────────────────────────────────────
DECLARE @sid BIGINT;
SELECT @sid = id FROM bcm.exercise_scenarios WHERE scenario_code = 'SCN-OT-IT-BLEED-PETROCHEM';

IF @sid IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 1)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 1, 0, 'INFORMATION',
        N'T+0: Plant control room reports loss of communications to two Honeywell Experion controllers on the sulphur recovery unit. The DCS engineer suspects a network problem but the IT NOC sees no fault on the corporate side.',
        N'الدقيقة صفر: تبلّغ غرفة التحكم بفقدان الاتصال مع وحدتي تحكم Honeywell Experion على وحدة استرداد الكبريت. مهندس DCS يشكّ في عطل شبكي لكن مركز عمليات شبكة المؤسسة لا يرى عطلاً في جانب المؤسسة.',
        N'OT engineer opens an OT-side incident; IT Cyber bridge is paged so both sides see the same picture.',
        N'مهندس التشغيل يفتح حادثة في جانب التشغيل، ويُستدعى جسر الأمن المؤسسي ليرى الجانبان الصورة ذاتها.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 2)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 2, 15, 'ESCALATION',
        N'T+15: IT cyber team confirms an ACL drift on the corporate-OT firewall — a maintenance window last night left a rule disabled. The CISO''s console shows worm propagation across 6 IT endpoints. Two of those endpoints peer with engineering workstations on the OT DMZ.',
        N'الدقيقة ١٥: فريق الأمن المؤسسي يؤكد انجراف ACL على جدار الحماية بين المؤسسة والتشغيل — نافذة صيانة ليلة أمس تركت قاعدة معطّلة. كونسول CISO يُظهر انتشار دودة عبر ٦ نقاط نهاية مؤسسية. اثنتان منها تتصلان بمحطات هندسية في DMZ التشغيل.',
        N'IT cyber team isolates the corporate-OT firewall; OT engineering captures a network trace on the DMZ; CISO + Plant Manager join the bridge.',
        N'فريق الأمن المؤسسي يعزل جدار الحماية المشترك؛ هندسة التشغيل تلتقط تتبّعاً شبكياً على DMZ؛ ينضمّ CISO ومدير المصنع إلى الجسر.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 3)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 3, 25, 'ESCALATION',
        N'T+25: The IEC 61511 SIL 3 safety instrumented system on the sulphur unit trips. The plant goes to a controlled shutdown sequence. Production loss estimate is SAR 4,000,000 per hour. Operators are uncertain whether the trip was caused by the worm or by an unrelated process upset.',
        N'الدقيقة ٢٥: ينطلق نظام السلامة الآلي SIL 3 (وفق IEC 61511) على وحدة الكبريت، ويدخل المصنع تسلسل إغلاق مُتحكَّم فيه. تقدير خسارة الإنتاج ٤٬٠٠٠٬٠٠٠ ريال في الساعة. المشغّلون غير متأكدين هل سبب الإطلاق دودة أم اضطراب عملية غير ذي صلة.',
        N'HSE Director confirms the trip is safe; OT engineering documents the trip cause hypothesis; CISO scopes IT-side blast radius.',
        N'مدير الصحة والسلامة والبيئة يؤكد أن الإطلاق آمن؛ هندسة التشغيل توثّق فرضية سبب الإطلاق؛ CISO يحدد نطاق التأثير في جانب المعلومات.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 4)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 4, 45, 'ESCALATION',
        N'T+45: Honeywell Riyadh service desk is engaged at P1. They commit to a remote engineer within 30 minutes and a field engineer within 4 hours. They request a controller log dump and a confirmed clean network path before they will reconnect.',
        N'الدقيقة ٤٥: يُستدعى مكتب خدمة هانيويل الرياض بأولوية P1. يلتزمون بمهندس عن بُعد خلال ٣٠ دقيقة ومهندس ميداني خلال ٤ ساعات. يطلبون تفريغ سجلات وحدة التحكم ومسار شبكي نظيف مؤكَّد قبل إعادة التوصيل.',
        N'OT engineering pulls the controller log; IT cyber team certifies a clean isolated VLAN; bridge agrees on the reconnect criteria.',
        N'هندسة التشغيل تسحب سجل وحدة التحكم؛ فريق الأمن المؤسسي يعتمد VLAN معزولة نظيفة؛ يتفق الجسر على معايير إعادة التوصيل.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 5)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 5, 90, 'REGULATOR',
        N'T+90: NCA OT-CSCC duty officer calls — they have seen abnormal traffic from the plant''s perimeter telco link and want confirmation of an OT-side compromise. MEIM (Ministry of Industry & Mineral Resources) safety inspector requests an HSE report within 4 hours.',
        N'الدقيقة ٩٠: يتصل ضابط واجب NCA OT-CSCC — رصدوا حركة غير طبيعية من رابط الاتصالات المحيط بالمصنع ويطلبون تأكيد اختراق في جانب التشغيل. مفتش سلامة وزارة الصناعة والثروة المعدنية يطلب تقرير الصحة والسلامة والبيئة خلال ٤ ساعات.',
        N'Regulator Affairs drafts a written status note to NCA + MEIM; HSE Director owns the safety report; CISO supplies indicators of compromise.',
        N'العلاقات التنظيمية تعدّ مذكرة حالة مكتوبة إلى NCA و وزارة الصناعة؛ مدير الصحة والسلامة يتولى تقرير السلامة؛ CISO يزوّد بمؤشرات الاختراق.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 6)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 6, 150, 'DECISION',
        N'T+150: Restart-vs-extended-shutdown decision point. Honeywell field engineer estimates 3 hours to certify the unit is safe to restart. Continued shutdown adds SAR 12M of production loss. The Plant Manager seeks executive direction.',
        N'الدقيقة ١٥٠: نقطة قرار إعادة التشغيل مقابل الإغلاق الممتد. مهندس هانيويل الميداني يقدّر ٣ ساعات لاعتماد سلامة الوحدة لإعادة التشغيل. الإغلاق الممتد يضيف خسارة إنتاج بـ ١٢ مليون ريال. مدير المصنع يلتمس توجيهاً تنفيذياً.',
        N'COO + HSE Director + CISO converge on a restart authorisation note; risk-acceptance entry is opened with the BCM Manager.',
        N'الرئيس التنفيذي للعمليات + مدير الصحة والسلامة + CISO يتفقون على مذكرة اعتماد إعادة التشغيل؛ يُفتح بند قبول مخاطرة لدى مدير BCM.');
END
GO

-- ─── 4c. SCN-HAJJ-DDOS-NUSUK on EXS-HAJJ-PEAK-LOAD-2026 ────────────────
DECLARE @sid BIGINT;
SELECT @sid = id FROM bcm.exercise_scenarios WHERE scenario_code = 'EXS-HAJJ-PEAK-LOAD-2026';

IF @sid IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 1)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 1, 0, 'INFORMATION',
        N'T+0: 48 hours before Tarwiyah Day. Brand-monitoring tool surfaces a fake "Nusuk Express" app on Google Play with 12,000 installs. The app phishes pilgrim Tawakkalna credentials and a Nafath OTP via an in-app overlay.',
        N'الدقيقة صفر: ٤٨ ساعة قبل يوم التروية. أداة مراقبة العلامة التجارية ترصد تطبيقاً مزوّراً «Nusuk Express» على Google Play بـ ١٢٬٠٠٠ تنزيل. يصطاد التطبيق بيانات «توكلنا» ورمز نفاذ المؤقت عبر تراكب داخلي.',
        N'Brand Protection raises a takedown with Google; NCA + Communications & IT Commission are notified.',
        N'حماية العلامة التجارية ترفع طلب إزالة لدى جوجل؛ يُبلَّغ NCA وهيئة الاتصالات وتقنية المعلومات.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 2)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 2, 30, 'ESCALATION',
        N'T+30: Volumetric DDoS opens on the bank''s consular-gate APIs — 60 Gbps L3/L4 plus 1.2M rps L7. Pilgrim payment cards see decline rates climb from 1.8% to 22%. Mada switch sees back-pressure.',
        N'الدقيقة ٣٠: تنطلق هجمة DDoS حجمية على واجهات بوابات القنصلية لدى البنك — ٦٠ قيقابت طبقة ٣/٤ مع ١٫٢ مليون طلب/ثانية طبقة ٧. ترتفع نسبة رفض بطاقات الحجاج من ١٫٨٪ إلى ٢٢٪. سويتش Mada يرى ضغطاً عكسياً.',
        N'CISO activates anti-DDoS scrubbing; SOC Lead opens an L7 mitigation; Digital Channels Owner monitors decline rates.',
        N'مسؤول أمن المعلومات يفعّل تنقية حركة DDoS؛ قائد SOC يفتح تخفيف طبقة ٧؛ مالك القنوات الرقمية يراقب معدلات الرفض.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 3)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 3, 50, 'MEDIA',
        N'T+50: Pilgrim WhatsApp groups (Urdu + Indonesian) start circulating screenshots of failed payments. Two video clips of frustrated pilgrims at Aziziyah hotel desks reach 800,000 views in 20 minutes.',
        N'الدقيقة ٥٠: مجموعات الحجاج على واتساب (أردو + إندونيسي) تبدأ بتداول لقطات لمدفوعات فاشلة. مقطعا فيديو لحجاج مستائين عند مكاتب فندق العزيزية يصلان ٨٠٠٬٠٠٠ مشاهدة خلال ٢٠ دقيقة.',
        N'Comms Lead prepares a four-language safety advisory (AR/EN/UR/ID); Mecca-region liaison briefed.',
        N'قائد الاتصالات يُعدّ إرشاداً رباعي اللغة (عربي/إنجليزي/أردو/إندونيسي)؛ ممثل منطقة مكة يُحاط علماً.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 4)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 4, 75, 'INFORMATION',
        N'T+75: A Tawakkalna lookalike domain (tawak1ana.app) starts harvesting OTPs. NCA brand-protection desk has not picked it up yet. The bank''s threat intel feed surfaces it via Saudi CERT''s morning bulletin.',
        N'الدقيقة ٧٥: نطاق مزيّف يحاكي «توكلنا» (tawak1ana.app) يبدأ بحصاد رموز OTP. مكتب حماية العلامات في NCA لم يلتقطه بعد. تستخرجه تغذية الاستخبارات لدى البنك من نشرة CERT السعودي الصباحية.',
        N'Threat Intel files an urgent report with Saudi CERT + NCA; outbound block on the domain published to all proxy tiers.',
        N'الاستخبارات الإلكترونية تقدّم بلاغاً عاجلاً إلى CERT السعودي و NCA؛ يُنشَر حظر النطاق على كل طبقات البروكسي.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 5)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 5, 110, 'REGULATOR',
        N'T+110: Mecca region pilgrim safety authority requests a written impact note within 60 minutes — they need to brief the regional command. SAMA OPRES also opens a watch ticket because Mada decline rates breached 15% threshold.',
        N'الدقيقة ١١٠: هيئة سلامة الحجاج بمنطقة مكة تطلب مذكرة أثر مكتوبة خلال ٦٠ دقيقة لإحاطة القيادة الإقليمية. مكتب SAMA OPRES يفتح بطاقة مراقبة أيضاً لأن نسبة رفض Mada تجاوزت عتبة ١٥٪.',
        N'BCM Manager owns the regional authority note; Compliance Director owns the SAMA OPRES update; single message stack feeds both.',
        N'مدير BCM يتولى مذكرة الهيئة الإقليمية؛ مدير الالتزام يتولى تحديث SAMA OPRES؛ حزمة رسالة موحَّدة تغذّي الجانبين.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 6)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 6, 180, 'DECISION',
        N'T+180: DDoS continues. Anti-DDoS provider asks whether the bank wants to switch to "always-on" scrubbing for the remainder of the Hajj season (cost: SAR 600,000). Plant ops are not impacted, but pilgrim transactions remain degraded by 8%.',
        N'الدقيقة ١٨٠: تستمر الهجمة. مزوّد تخفيف DDoS يسأل عن الرغبة في التحويل إلى تنقية «دائمة التشغيل» لما تبقى من موسم الحج (التكلفة ٦٠٠٬٠٠٠ ريال). العمليات سليمة لكن معاملات الحجاج تبقى متدهورة بنسبة ٨٪.',
        N'CIO + CFO decide on the always-on uplift; risk-acceptance ticket opened for the residual 8% degradation window.',
        N'CIO و CFO يقرّران الترقية الدائمة؛ يُفتح بند قبول مخاطرة لنافذة التدهور المتبقية البالغة ٨٪.');
END
GO

-- ─── 4d. SCN-PDPL-BREACH-200K on EXS-PDPL-BREACH-TABLETOP-2026 ─────────
DECLARE @sid BIGINT;
SELECT @sid = id FROM bcm.exercise_scenarios WHERE scenario_code = 'EXS-PDPL-BREACH-TABLETOP-2026';

IF @sid IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 1)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 1, 0, 'INFORMATION',
        N'T+0: SOC flags an unusual 4 GB outbound to a hosting provider in eastern Europe from a CRM analytics workstation. Forensic preview suggests 200,000 customer records (name + national ID + phone + email + KYC tier).',
        N'الدقيقة صفر: يرصد SOC تدفقاً صادراً غير معتاد بحجم ٤ قيقابايت إلى مزوّد استضافة في شرق أوروبا من محطة تحليلات CRM. المعاينة الجنائية تشير إلى ٢٠٠٬٠٠٠ سجل عميل (اسم + هوية وطنية + جوّال + بريد + درجة اعرف عميلك).',
        N'DPO is paged; legal counsel engaged; CISO opens a containment ticket; PDPL Article 31 72h clock has not yet started (needs confirmation).',
        N'يُبلَّغ مسؤول حماية البيانات؛ يُستدعى القانوني؛ مسؤول أمن المعلومات يفتح بطاقة احتواء؛ ساعة المادة ٣١ (٧٢ ساعة) لم تبدأ بعد (تنتظر التأكيد).');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 2)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 2, 20, 'ESCALATION',
        N'T+20: Forensic team confirms the exfil. Account belongs to a contractor analyst who left two weeks ago — JML offboarding missed the access revocation step. The PDPL Article 31 SDAIA notification clock starts now.',
        N'الدقيقة ٢٠: الفريق الجنائي يؤكد التسريب. الحساب يخصّ محلّلاً متعاقداً غادر قبل أسبوعين — عملية إنهاء الخدمة (JML) أغفلت سحب الصلاحيات. تبدأ الآن ساعة إشعار سدايا بموجب المادة ٣١.',
        N'DPO confirms scope; HR + IAM begin the JML root-cause; Risk + Audit owners pulled in.',
        N'مسؤول حماية البيانات يثبّت النطاق؛ الموارد البشرية وإدارة الهويات يبدآن السبب الجذري لخلل JML؛ يُستدعى مالكا المخاطر والمراجعة.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 3)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 3, 45, 'DECISION',
        N'T+45: PDPL Article 32 individual-notification decision point. Legal asks: does this breach meet the "high risk to data subject rights" threshold that mandates individual notice in addition to SDAIA filing?',
        N'الدقيقة ٤٥: نقطة قرار إشعار الأفراد وفق المادة ٣٢. القانوني يسأل: هل يبلغ هذا الخرق عتبة «الخطر المرتفع على حقوق صاحب البيانات» التي تستلزم إشعاراً فردياً إلى جانب إيداع سدايا؟',
        N'DPO chairs the call; Legal records the rationale; CEO signs the determination.',
        N'مسؤول حماية البيانات يدير الجلسة؛ القانوني يدوّن المسوّغ؛ الرئيس التنفيذي يوقّع القرار.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 4)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 4, 90, 'INFORMATION',
        N'T+90: Tawuniya cyber insurance broker calls — they need the incident reference + preliminary loss estimate to open a claim file. They warn the policy''s 48-hour notification clause has already started.',
        N'الدقيقة ٩٠: وسيط التأمين السيبراني لدى التعاونية يتصل — يحتاج المرجع وتقديراً أولياً للخسارة لفتح ملف مطالبة. يُحذّر من أن شرط الإشعار خلال ٤٨ ساعة قد بدأ.',
        N'CFO opens the claim file; CRO supplies the initial estimate; Legal reviews the policy notification language.',
        N'الرئيس التنفيذي المالي يفتح ملف المطالبة؛ CRO يقدّم التقدير الأولي؛ القانوني يراجع نص الإشعار في البوليصة.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 5)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 5, 180, 'REGULATOR',
        N'T+180: SDAIA notification draft ready. The DPO walks through the Article 31 fields: nature of breach, categories of data, approximate number of subjects, contact for further information, likely consequences, mitigation measures.',
        N'الدقيقة ١٨٠: مسوّدة إشعار سدايا جاهزة. مسؤول حماية البيانات يستعرض حقول المادة ٣١: طبيعة الخرق، فئات البيانات، العدد التقديري للأشخاص، جهة الاتصال للاستفسار، التبعات المحتملة، إجراءات التخفيف.',
        N'Legal counter-signs the draft; DPO submits via the SDAIA portal; ack reference stored against the incident.',
        N'القانوني يوقّع المسوّدة بالموازاة؛ مسؤول حماية البيانات يقدّم عبر بوابة سدايا؛ يُحفَظ مرجع الاستلام مع الحادثة.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 6)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 6, 240, 'MEDIA',
        N'T+240: Board emergency meeting opens. The chair asks: how do we tell customers, do we tell them today or wait until the SDAIA acknowledgement comes back, and what is the materiality disclosure stance to Tadawul?',
        N'الدقيقة ٢٤٠: يُفتتح اجتماع طارئ لمجلس الإدارة. يسأل رئيس المجلس: كيف نُبلّغ العملاء، اليوم أم ننتظر استلام إقرار سدايا، وما موقف الإفصاح الجوهري لتداول؟',
        N'CEO + DPO present the comms timeline; IR team owns the Tadawul disclosure; CRO presents residual loss range.',
        N'الرئيس التنفيذي ومسؤول حماية البيانات يعرضان جدول الاتصالات؛ علاقات المستثمرين تتولى إفصاح تداول؛ CRO يعرض نطاق الخسارة المتبقية.');
END
GO

-- ─── 4e. SCN-SAMA-REGONLINE-OUTAGE ─────────────────────────────────────
DECLARE @sid BIGINT;
SELECT @sid = id FROM bcm.exercise_scenarios WHERE scenario_code = 'SCN-SAMA-REGONLINE-OUTAGE';

IF @sid IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 1)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 1, 0, 'INFORMATION',
        N'T+0: MLRO team is uploading a batch of 47 STR submissions on the last day before the statutory deadline. The SAMA RegOnline portal returns a 503 mid-upload. Three submissions are confirmed sent; the remaining 44 are in unknown state.',
        N'الدقيقة صفر: فريق المسؤول عن مكافحة غسل الأموال يرفع دفعة من ٤٧ تقرير معاملات مشبوهة في آخر يوم قبل الموعد القانوني. تعيد بوابة SAMA RegOnline رمز ٥٠٣ أثناء الرفع. تأكّد تسليم ثلاثة تقارير؛ الباقي (٤٤) في حالة غير معروفة.',
        N'MLRO captures the timestamp + transaction list; Compliance Director is briefed; IT Service Owner verifies the bank''s outbound side is healthy.',
        N'المسؤول عن مكافحة غسل الأموال يلتقط الطابع الزمني وقائمة المعاملات؛ يُحاط مدير الالتزام علماً؛ مالك خدمة تقنية المعلومات يتأكد من سلامة جانب البنك.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 2)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 2, 10, 'ESCALATION',
        N'T+10: Compliance Director calls SAMA OPRES. They confirm RegOnline is down for the whole sector and the outage is expected to last 2-6 hours. They authorise the backchannel (secure email to the SCRO desk) for time-critical submissions.',
        N'الدقيقة ١٠: يتصل مدير الالتزام بمكتب SAMA OPRES. يؤكدون أن RegOnline متعطّلة للقطاع كاملاً ويُتوقع استمرار الانقطاع من ساعتين إلى ست. يأذنون باستخدام القناة البديلة (بريد مؤمَّن إلى مكتب SCRO) للتقديم الحرج زمنياً.',
        N'MLRO + Compliance prepare the backchannel package format (PDF + SHA-256 + delivery proof); Internal Audit observer joins per AFC §6.4.',
        N'المسؤول عن مكافحة غسل الأموال والالتزام يُعدّان صيغة حزمة القناة البديلة (PDF + SHA-256 + إثبات تسليم)؛ ينضمّ مراقب المراجعة الداخلية وفقاً للمادة ٦٫٤.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 3)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 3, 30, 'DECISION',
        N'T+30: Decision point — submit all 44 reports via backchannel now (some may already have been received by RegOnline before the 503), or wait for the portal to come back and re-upload the full batch with cross-references?',
        N'الدقيقة ٣٠: نقطة قرار — هل نقدّم الـ ٤٤ تقريراً عبر القناة البديلة الآن (بعضها ربما وصل قبل خطأ ٥٠٣)، أم ننتظر عودة البوابة ونعيد رفع الدفعة كاملةً مع إحالات متبادلة؟',
        N'MLRO recommends backchannel-now (statutory deadline trumps duplicate-handling risk); CRO + Compliance Director approve.',
        N'المسؤول عن مكافحة غسل الأموال يوصي بالقناة البديلة الآن (الموعد القانوني يتقدّم على مخاطرة التكرار)؛ CRO ومدير الالتزام يعتمدان.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 4)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 4, 60, 'INFORMATION',
        N'T+60: Backchannel package dispatched. SCRO desk acknowledges receipt with a manual reference. Internal Audit Lead joins the bridge and starts a parallel control-deviation log per SAMA AFC §6.4.',
        N'الدقيقة ٦٠: تم إرسال حزمة القناة البديلة. مكتب SCRO يردّ بسند استلام يدوي. ينضم رئيس المراجعة الداخلية إلى الجسر ويبدأ سجلاً موازياً لانحراف الضوابط وفقاً للمادة ٦٫٤.',
        N'MLRO files the ack reference in the STR register; Internal Audit logs the deviation; IT Service Owner re-tests the portal every 15 minutes.',
        N'المسؤول عن مكافحة غسل الأموال يدوّن سند الاستلام في سجل STR؛ المراجعة الداخلية تُسجّل الانحراف؛ مالك خدمة تقنية المعلومات يعيد اختبار البوابة كل ١٥ دقيقة.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 5)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 5, 110, 'ESCALATION',
        N'T+110: RegOnline returns. The portal confirms 12 of the 44 STRs had reached the system before the 503 — they are now showing duplicate submissions matching the backchannel package. The portal asks the user to choose: keep originals, keep duplicates, or merge.',
        N'الدقيقة ١١٠: تعود RegOnline. تؤكد البوابة أن ١٢ من الـ ٤٤ تقريراً قد وصلت قبل خطأ ٥٠٣ — وهي تظهر الآن مكرّرة مع حزمة القناة البديلة. تطلب البوابة من المستخدم الاختيار: الإبقاء على الأصول، الإبقاء على المكرّرات، أو الدمج.',
        N'MLRO chooses "keep originals", records the duplicate-handling decision, opens a re-submission window for the 32 still missing.',
        N'المسؤول عن مكافحة غسل الأموال يختار «الإبقاء على الأصول»، يدوّن قرار التعامل مع التكرارات، يفتح نافذة إعادة تقديم لـ ٣٢ تقريراً لا يزال ناقصاً.');

    IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenario_inject WHERE scenario_id = @sid AND inject_order = 6)
    INSERT INTO bcm.exercise_scenario_inject (scenario_id, inject_order, time_offset_minutes, inject_type, inject_text_en, inject_text_ar, expected_action_en, expected_action_ar)
    VALUES (@sid, 6, 150, 'REGULATOR',
        N'T+150: All 44 STRs reconciled in RegOnline with backchannel cross-references. SCRO closes the manual ticket and confirms compliance with the statutory deadline. After-action: identify whether the bank should pre-stage backchannel readiness for the next AFC reporting cycle.',
        N'الدقيقة ١٥٠: تمت تسوية الـ ٤٤ تقريراً في RegOnline مع إحالات القناة البديلة. مكتب SCRO يغلق البطاقة اليدوية ويؤكد الالتزام بالموعد القانوني. ورشة الدروس: هل ينبغي للبنك تجهيز قناة بديلة مسبقاً للدورة التالية من تقارير مكافحة الجرائم المالية؟',
        N'BCM Manager opens an AAR; lessons-learned includes adding pre-staged backchannel readiness as a CAPA item.',
        N'مدير BCM يفتح ورشة دروس مستفادة؛ ضمن البنود: إضافة جاهزية القناة البديلة مسبقاً كبند CAPA.');
END
GO

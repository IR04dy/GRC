-- V111: Plan templates + Exercise scenarios + KSA peak-season lens.
--
-- Three audit follow-ups in one migration (Tier-3 #15 + #16, Tier-4 #28).
-- They share the same "catalog of named, ready-to-use content" shape, so
-- packing them together keeps the migration history readable.
--
-- IMPORTANT: apply with sqlcmd -f 65001 (UTF-8 input codepage) on Windows
-- so the Arabic N'...' literals survive parsing.
--
--  Tables:
--   bcm.plan_templates       — 8 canonical BCP/DRP scenarios with default
--                              sections (ransomware, datacentre, supplier,
--                              key-person, regional, cloud, telecom,
--                              workforce/pandemic)
--   bcm.exercise_scenarios   — 10 ready-to-run exercise scenarios with
--                              injects + success criteria as JSON
--   bcm.peak_seasons         — 6 KSA windows for 2026: Hajj, Umrah peak,
--                              Ramadan, Eid Al-Fitr, Eid Al-Adha, National
--                              Day. Tagging on a BIA's peak_periods_text
--                              keyword surfaces it in the peak-season lens.

SET NOCOUNT ON;
GO

-- ============================================================================
-- 1. PLAN TEMPLATES — Tier-3 #15
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'plan_templates' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.plan_templates (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        template_code       NVARCHAR(50)    NOT NULL,    -- TPL-BCP-RANSOMWARE
        template_name_en    NVARCHAR(255)   NOT NULL,
        template_name_ar    NVARCHAR(255)   NULL,
        plan_type           NVARCHAR(20)    NOT NULL,    -- BCP | DRP
        scenario_category   NVARCHAR(50)    NOT NULL,
        -- CYBER | INFRASTRUCTURE | SUPPLIER | WORKFORCE | REGIONAL | CLOUD | TELECOM | FACILITY
        description_en      NVARCHAR(MAX)   NULL,
        description_ar      NVARCHAR(MAX)   NULL,
        default_rto_hours   INT             NULL,
        default_rpo_hours   INT             NULL,
        default_mtpd_hours  INT             NULL,

        -- Section bodies as JSON map { section_key: { en, ar } }
        sections_json       NVARCHAR(MAX)   NOT NULL,

        -- Optional tags array as JSON ["NCEMA","SAMA","ISO_22301"]
        tags_json           NVARCHAR(MAX)   NULL,

        is_system           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT uq_plan_template_code UNIQUE (template_code),
        CONSTRAINT ck_plan_template_type CHECK (plan_type IN ('BCP', 'DRP'))
    );
    CREATE INDEX IX_plan_templates_type     ON bcm.plan_templates(plan_type);
    CREATE INDEX IX_plan_templates_category ON bcm.plan_templates(scenario_category);
END
GO

-- ============================================================================
-- 2. EXERCISE SCENARIOS — Tier-3 #16
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'exercise_scenarios' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_scenarios (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        scenario_code       NVARCHAR(50)    NOT NULL,    -- EXS-CYBER-RANSOM-2026
        title_en            NVARCHAR(255)   NOT NULL,
        title_ar            NVARCHAR(255)   NULL,
        scenario_category   NVARCHAR(50)    NOT NULL,
        -- Same list as plan_templates so the two catalogs can cross-reference
        exercise_type       NVARCHAR(30)    NOT NULL,
        -- TABLETOP | WALKTHROUGH | FUNCTIONAL | FAILOVER | SIMULATION | FULL_DR

        duration_minutes    INT             NOT NULL DEFAULT 90,

        description_en      NVARCHAR(MAX)   NOT NULL,
        description_ar      NVARCHAR(MAX)   NULL,

        objectives_en       NVARCHAR(MAX)   NULL,
        objectives_ar       NVARCHAR(MAX)   NULL,

        -- Injects: ordered timed events the facilitator drops on the team.
        -- Shape: [{ "at_min": 0, "title_en": "...", "title_ar": "...",
        --           "detail_en": "...", "expected_action": "..." }]
        injects_json        NVARCHAR(MAX)   NOT NULL,

        -- Success criteria: ordered checklist scored at the after-action review.
        -- Shape: [{ "criterion_en": "...", "criterion_ar": "...", "weight": 1.0 }]
        success_criteria_json NVARCHAR(MAX) NOT NULL,

        -- Suggested role assignments. [{role, count, notes}]
        role_assignments_json NVARCHAR(MAX) NULL,

        tags_json           NVARCHAR(MAX)   NULL,

        is_system           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT uq_exercise_scenario_code UNIQUE (scenario_code),
        CONSTRAINT ck_exercise_scenario_type CHECK (exercise_type IN
            ('TABLETOP','WALKTHROUGH','FUNCTIONAL','FAILOVER','SIMULATION','FULL_DR'))
    );
    CREATE INDEX IX_exercise_scenarios_category ON bcm.exercise_scenarios(scenario_category);
    CREATE INDEX IX_exercise_scenarios_type     ON bcm.exercise_scenarios(exercise_type);
END
GO

-- ============================================================================
-- 3. PEAK SEASONS — Tier-4 #28 (KSA-native)
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'peak_seasons' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.peak_seasons (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        season_code         NVARCHAR(50)    NOT NULL,
        season_name_en      NVARCHAR(255)   NOT NULL,
        season_name_ar      NVARCHAR(255)   NOT NULL,
        season_type         NVARCHAR(40)    NOT NULL,
        -- HAJJ | UMRAH_PEAK | RAMADAN | EID_AL_FITR | EID_AL_ADHA |
        -- NATIONAL_DAY | FOUNDING_DAY | RIYADH_SEASON | MOH_CAPACITY |
        -- BACK_TO_SCHOOL | SAUDI_QUARTER_END

        gregorian_start_date DATE           NOT NULL,
        gregorian_end_date   DATE           NOT NULL,
        hijri_start_year     INT            NULL,
        hijri_start_month    INT            NULL,

        risk_dimensions_json NVARCHAR(MAX)  NULL,
        -- ["TRANSACTION_VOLUME","TRAVEL_LOAD","REGULATORY_VISIBILITY","STAFF_AVAILABILITY"]
        affected_services    NVARCHAR(1000) NULL,
        -- comma-separated free text — what services typically peak in this window
        recommended_actions  NVARCHAR(MAX)  NULL,

        notes_en             NVARCHAR(MAX)  NULL,
        notes_ar             NVARCHAR(MAX)  NULL,

        is_system            BIT            NOT NULL DEFAULT 1,
        is_deleted           BIT            NOT NULL DEFAULT 0,
        created_at           DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT uq_peak_seasons_code UNIQUE (season_code),
        CONSTRAINT ck_peak_seasons_dates CHECK (gregorian_end_date >= gregorian_start_date)
    );
    CREATE INDEX IX_peak_seasons_type   ON bcm.peak_seasons(season_type);
    CREATE INDEX IX_peak_seasons_dates  ON bcm.peak_seasons(gregorian_start_date, gregorian_end_date);
END
GO

-- ─── Seed: 8 plan templates ──────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.plan_templates)
BEGIN
    INSERT INTO bcm.plan_templates
      (template_code, template_name_en, template_name_ar, plan_type, scenario_category,
       description_en, description_ar, default_rto_hours, default_rpo_hours, default_mtpd_hours,
       sections_json, tags_json)
    VALUES

    -- 1. Ransomware (BCP)
    (N'TPL-BCP-RANSOMWARE',
     N'Ransomware containment & business continuity',
     N'احتواء برامج الفدية واستمرارية الأعمال',
     N'BCP', N'CYBER',
     N'Tier-1 cyber scenario: ransomware encrypts production storage. Plan covers detection, isolation, business workaround, regulator notifications (SAMA / NCA), and restore from immutable backups.',
     N'سيناريو سيبراني من المستوى الأول: تشفير وحدات الإنتاج. تغطي الخطة الاكتشاف والعزل والبديل التشغيلي وإشعار الجهات التنظيمية (SAMA و NCA) والاستعادة من نسخ احتياطية غير قابلة للتعديل.',
     4, 1, 24,
     N'{"overview":{"en":"Ransomware affecting tier-1 systems requires immediate isolation, executive notification, and engagement of the cyber-incident response team. This plan assumes EDR has flagged at least one encryption event and that the SOC has triggered the standing crisis bridge.","ar":"يتطلب الفدية المؤثرة على الأنظمة الحيوية عزلاً فورياً وإخطار الإدارة العليا وتفعيل فريق الاستجابة السيبرانية. تفترض الخطة وجود حدث تشفير اكتشفه EDR وأن مركز عمليات الأمن قد فعّل جسر الأزمات الدائم."},"scope":{"en":"Tier-1 customer-facing services, treasury, and core-banking. Excludes BAU patching, low-criticality endpoints, and supplier-owned environments (which trigger separate supplier plans).","ar":"الخدمات الرئيسية الموجهة للعملاء والخزينة والخدمات المصرفية الأساسية. تستثنى عمليات الترقيع المعتادة ونقاط النهاية منخفضة الأهمية وبيئات المورّدين."},"roles":{"en":"Crisis Manager · CISO · SOC Lead · IT-Recovery Owner · Comms Lead · Legal · BCM Manager. Activation authority sits with the CISO + BCM Manager jointly.","ar":"مدير الأزمات · مسؤول أمن المعلومات · قائد مركز العمليات · مالك تعافي تقنية المعلومات · قائد الاتصالات · القانوني · مدير BCM. صلاحية التفعيل لمسؤول الأمن ومدير BCM معاً."},"activation_criteria":{"en":"Any one of: (a) EDR confirms encryption of ≥1 production host, (b) ≥2 unrelated hosts trigger ransomware indicators within 30 minutes, (c) SOC observes external claim of access on dark-web monitor.","ar":"أيّ مما يلي: (أ) تأكيد EDR لتشفير مضيف إنتاجي واحد فأكثر، (ب) رصد مؤشرات فدية على مضيفين منفصلين خلال 30 دقيقة، (ج) رصد ادعاء وصول على مراقبة الشبكة المظلمة."},"recovery_priorities":{"en":"1. Mada switch · 2. SARIE clearing · 3. Internet & mobile banking · 4. Treasury front office · 5. Internal HR / payroll. Lower-priority systems may restore overnight from clean backup.","ar":"1) سويتش Mada · 2) مقاصة SARIE · 3) الخدمات الرقمية · 4) واجهة الخزينة · 5) الموارد البشرية والرواتب. الأنظمة الأقل أهمية تُستعاد ليلاً من نسخ نظيفة."},"recovery_steps":{"en":"1. Isolate at network layer (VLAN quarantine) · 2. Snapshot affected hosts for forensics · 3. Trigger BCP comms tree · 4. Reroute customer traffic to DR region · 5. Validate clean backup pool integrity · 6. Restore in priority order · 7. Reconcile transactions and re-open channels.","ar":"1) العزل على طبقة الشبكة · 2) أخذ نسخ للتحقيق · 3) تفعيل شجرة الاتصال · 4) إعادة توجيه الحركة إلى موقع التعافي · 5) التحقق من سلامة النسخ النظيفة · 6) الاستعادة بالأولوية · 7) تسوية المعاملات وإعادة فتح القنوات."},"comms_plan":{"en":"Internal: pager + Teams crisis room. External: SAMA OPRES desk within 4h, NCA cyber-incident desk within 4h, PDPL within 72h if personal data exposed. Customer comms via app banner + SMS once status established.","ar":"داخلياً: نداء + غرفة Teams. خارجياً: مكتب SAMA OPRES خلال 4 ساعات و NCA خلال 4 ساعات و PDPL خلال 72 ساعة عند الكشف عن بيانات شخصية. إخطار العملاء عبر التطبيق والرسائل بعد تثبيت الحالة."},"manual_workaround":{"en":"Branch tellers fall back to paper ledger with dual-control sign-off, cap of SAR 10,000 per transaction. End-of-day reconciliation against core ledger.","ar":"يتحوّل صرّاف الفرع إلى السجل الورقي بتوقيع مزدوج بحد 10,000 ريال للمعاملة، مع تسوية نهاية اليوم."},"resources":{"en":"People: 12 on-call (SOC + IR + recovery). Tech: immutable backup repository, clean room VLAN, EDR central console. Facility: Riyadh primary + Jeddah DR. Supplier: Mada hotline, SAMA OPRES contact, IR retainer.","ar":"الأفراد: 12 جاهزاً. التقنية: مستودع نسخ غير قابلة للتعديل وشبكة معزولة وكونسول EDR. المرافق: الرياض وجدّة. المورّد: خط Mada الساخن وجهة SAMA و الجهة الاستشارية للحوادث."},"supplier_escalation":{"en":"Tier-1 vendors: Mada NOC (15-min SLA), SAMA OPRES desk, hyperscaler enterprise support, EDR vendor IR cell, IR retainer.","ar":"المورّدون الحيويون: مركز Mada و SAMA و المزوّد السحابي و فريق EDR و الاستشاري."},"return_to_normal":{"en":"Sequenced restore complete, two clean nightly runs observed, regulator de-brief filed, lessons-learned workshop within 5 working days.","ar":"اكتمال الاستعادة المتسلسلة ومراقبة دورتين نظيفتين وإحاطة الجهة التنظيمية وورشة الدروس خلال 5 أيام عمل."},"appendices":{"en":"A. Contact list. B. Detailed runbooks per Tier-1 service. C. Sample customer comms (EN/AR). D. SAMA + NCA notification templates.","ar":"أ) جهات الاتصال. ب) أدلة تشغيل تفصيلية. ج) نماذج اتصال للعملاء. د) قوالب إخطار SAMA و NCA."}}',
     N'["NCA","SAMA","ISO_22301","CYBER"]'),

    -- 2. Datacentre outage (BCP)
    (N'TPL-BCP-DATACENTRE',
     N'Primary datacentre outage',
     N'انقطاع مركز البيانات الرئيسي',
     N'BCP', N'INFRASTRUCTURE',
     N'Power, cooling, or network failure renders the Riyadh primary datacentre unavailable. Plan covers failover to Jeddah DR site, service prioritisation, and customer comms.',
     N'فشل في الطاقة أو التبريد أو الشبكة يجعل مركز بيانات الرياض غير متاح. تغطي الخطة التحوّل إلى موقع التعافي بجدّة وأولويات الخدمة واتصالات العملاء.',
     2, 1, 8,
     N'{"overview":{"en":"Total datacentre outage. Trigger DC failover via standing runbook; first-call to STC NOC and hyperscaler if cloud-dependency suspect.","ar":"انقطاع كامل لمركز البيانات. تفعيل التحوّل بدليل دائم والاتصال أولاً بـ STC ثم المزوّد السحابي عند الشك."},"scope":{"en":"All workloads hosted in Riyadh primary DC. Cloud-resident workloads (already multi-region) follow their own runbook.","ar":"كل الأعباء في الرياض. الأعباء السحابية متعددة المناطق تتبع دليلها الخاص."},"roles":{"en":"IT-Recovery Owner · Network Lead · DBA Lead · Crisis Manager · BCM Manager.","ar":"مالك التعافي · قائد الشبكة · قائد قواعد البيانات · مدير الأزمات · مدير BCM."},"activation_criteria":{"en":"Loss of all production hosts in primary DC for ≥10 min OR confirmed loss of any one of (power, cooling, primary fibre).","ar":"فقدان جميع المضيفين في DC الرئيسي لـ 10+ دقائق أو فقد الطاقة/التبريد/الألياف الرئيسية."},"recovery_priorities":{"en":"Cards switch · Core banking · Treasury · Digital channels · CRM · HR.","ar":"سويتش البطاقات · الخدمات الأساسية · الخزينة · القنوات الرقمية · CRM · الموارد البشرية."},"recovery_steps":{"en":"1. Declare DC outage · 2. Engage facility & infra leads · 3. Trigger Jeddah failover (DB → app → traffic) · 4. Verify Mada acceptance · 5. Notify SAMA OPRES.","ar":"1) إعلان الانقطاع · 2) قادة المرافق والبنية التحتية · 3) تحوّل جدّة (قواعد البيانات ثم التطبيق ثم الحركة) · 4) قبول Mada · 5) إشعار SAMA."},"comms_plan":{"en":"Customer banner + SMS for digital channels. SAMA OPRES desk within 4h.","ar":"لافتة + رسائل للعملاء. مكتب SAMA خلال 4 ساعات."},"manual_workaround":{"en":"Branches operate on offline ATM batch mode for cash; deposits manual.","ar":"تعمل الفروع في وضع ATM دفعي للنقد والإيداع اليدوي."},"resources":{"en":"DR site capacity, runbook, DBA on-call, network team, hyperscaler enterprise support.","ar":"سعة موقع التعافي وأدلة تشغيل وفريق قواعد البيانات والشبكة والدعم السحابي."},"supplier_escalation":{"en":"STC NOC, hyperscaler enterprise, building facility manager, generator vendor, SEC.","ar":"STC والمزوّد السحابي والمرافق ومورّد المولّد و SEC."},"return_to_normal":{"en":"Two clean validation cycles, transactions reconciled, lessons workshop.","ar":"دورتا تحقق نظيفتان وتسوية وورشة الدروس."},"appendices":{"en":"Failover runbook, contact list, MoU with hyperscaler.","ar":"دليل التحوّل وجهات الاتصال واتفاق المزوّد السحابي."}}',
     N'["SAMA","ISO_22301","INFRASTRUCTURE"]'),

    -- 3. Supplier failure (BCP)
    (N'TPL-BCP-SUPPLIER-FAIL',
     N'Critical supplier failure',
     N'تعثّر مورّد حيوي',
     N'BCP', N'SUPPLIER',
     N'Critical supplier (clearing-house, telecom, HSM vendor, SaaS provider) suffers a material disruption. Plan covers alternate routing, regulator notification, and BAU resumption.',
     N'يتعرض مورّد حيوي (مقاصّة، اتصالات، HSM، SaaS) لتعطّل جوهري. تغطي الخطة التوجيه البديل وإشعار الجهة التنظيمية واستئناف الأعمال.',
     8, 4, 48,
     N'{"overview":{"en":"Material disruption of a tier-1 supplier triggers this plan. Two distinct response paths depending on supplier type: payments (Mada / SARIE) vs technology (HSM / SaaS).","ar":"تعطّل مورّد حيوي يفعّل الخطة. مساران للاستجابة: المدفوعات (Mada / SARIE) والتقنية (HSM / SaaS)."},"scope":{"en":"All supplier categories ranked Tier-1 or Sovereign in the SCM register.","ar":"كل المورّدين Tier-1 و Sovereign في سجل SCM."},"roles":{"en":"SCM Manager · BCM Manager · Crisis Manager · CIO · Affected Service Owner.","ar":"مدير SCM · مدير BCM · مدير الأزمات · CIO · مالك الخدمة المتأثرة."},"activation_criteria":{"en":"Supplier confirms outage ≥4h OR SLA breach for two consecutive measurement windows.","ar":"تأكيد المورّد لانقطاع 4+ ساعات أو خرق SLA لنافذتين متتاليتين."},"recovery_priorities":{"en":"Re-route payment flows · Activate alternate supplier · Engage regulator if required · Customer comms.","ar":"إعادة توجيه المدفوعات · تفعيل بديل · إشعار الجهة التنظيمية · إخطار العملاء."},"recovery_steps":{"en":"1. Confirm outage scope with supplier · 2. Decide on alternate path · 3. Execute alternate routing runbook · 4. Notify SAMA if payment supplier · 5. Customer comms · 6. Monitor restoration.","ar":"1) التأكد من النطاق · 2) القرار · 3) تنفيذ المسار البديل · 4) إشعار SAMA · 5) الإخطار · 6) المراقبة."},"comms_plan":{"en":"Internal escalation chain. Customer banner if customer-impacting. SAMA OPRES if payments. Supplier daily sync.","ar":"تصعيد داخلي. لافتة عند تأثر العميل. SAMA عند المدفوعات. مزامنة يومية مع المورّد."},"manual_workaround":{"en":"For payment supplier: route via alternate clearing partner under bilateral MoU. For tech supplier: switch to in-house fallback (degraded mode).","ar":"للمدفوعات: شريك مقاصّة بديل. للتقنية: بديل داخلي بوضع منخفض."},"resources":{"en":"SCM supplier register, alternate supplier MoUs, payments runbooks, IT bridge call.","ar":"سجل SCM واتفاقيات البدلاء وأدلة المدفوعات."},"supplier_escalation":{"en":"Supplier account director · supplier exec contact · SAMA · alternate supplier on-call.","ar":"مدير الحساب · جهة تنفيذية · SAMA · بديل جاهز."},"return_to_normal":{"en":"Supplier restores; cut-over back per runbook; lessons workshop including supplier.","ar":"يستعيد المورّد ثم التحوّل العكسي ثم ورشة الدروس بمشاركة المورّد."},"appendices":{"en":"Supplier matrix, alternate-supplier MoUs, regulator templates.","ar":"مصفوفة المورّدين وقوالب الجهة التنظيمية."}}',
     N'["SAMA","SCM","ISO_22301"]'),

    -- 4. Key-person unavailability (BCP)
    (N'TPL-BCP-KEY-PERSON',
     N'Key-person unavailability',
     N'عدم توفر الكوادر الحيوية',
     N'BCP', N'WORKFORCE',
     N'Specialist treasury, dealing, or fraud-ops staff become unavailable (resignation cluster, illness, travel restriction). Plan covers cross-training fallback and external augmentation.',
     N'عدم توفر متخصصي الخزينة أو التداول أو مكافحة الاحتيال (استقالات أو مرض أو قيود سفر). تغطي الخطة التغطية التبادلية والدعم الخارجي.',
     24, 24, 120,
     N'{"overview":{"en":"Concentration risk realised: critical role(s) unstaffed for ≥1 business day. Triggers cross-training playbook and emergency procurement of contractors.","ar":"تحقق تركّز المخاطر: أدوار حرجة بدون شاغل لـ 1+ يوم عمل. تفعيل التدريب المتبادل والتعاقد العاجل."},"scope":{"en":"Treasury Front-Office, Treasury Back-Office, Cards Fraud-Ops, Core-Banking SRE, AML Investigations.","ar":"الخزينة (واجهة ومكتب خلفي) و عمليات احتيال البطاقات و SRE للخدمات الأساسية و التحقيقات AML."},"roles":{"en":"HR Director · Business Unit Head · CIO · BCM Manager.","ar":"مدير الموارد البشرية · رئيس وحدة الأعمال · CIO · مدير BCM."},"activation_criteria":{"en":"Critical role unstaffed > 1 business day OR ≥2 critical roles unstaffed within a 7-day window.","ar":"دور حرج بدون شاغل > يوم عمل أو 2+ أدوار خلال 7 أيام."},"recovery_priorities":{"en":"Reassign backup, contractor procurement, reduced-mode operations, training pipeline.","ar":"المعاون البديل والتعاقد والوضع المخفّض والتدريب."},"recovery_steps":{"en":"1. Activate cross-trained backup · 2. Issue emergency contract via procurement MSA · 3. Cap discretionary actions, require dual-control on critical decisions · 4. Reduce throughput if needed · 5. Onboard contractor.","ar":"1) المعاون · 2) التعاقد العاجل · 3) ضوابط مزدوجة · 4) خفض القدرة · 5) إدماج المتعاقد."},"comms_plan":{"en":"Internal only for HR-sensitive details. CEO-tier briefing if treasury affected.","ar":"داخلياً للحساسية. إحاطة CEO عند الخزينة."},"manual_workaround":{"en":"Reduced-throughput mode with dual-control on every transaction.","ar":"وضع منخفض بضبط مزدوج لكل معاملة."},"resources":{"en":"Cross-training matrix, contractor MSAs, training budget.","ar":"مصفوفة التدريب وعقود المتعاقدين وميزانية التدريب."},"supplier_escalation":{"en":"Recruitment partners, contractor agencies.","ar":"شركاء التوظيف وكالات المتعاقدين."},"return_to_normal":{"en":"Permanent hire onboarded, knowledge transfer signed off, controls returned to normal.","ar":"تعيين دائم ونقل المعرفة وعودة الضوابط."},"appendices":{"en":"Cross-training matrix, contractor MSA register, training plan.","ar":"مصفوفة التدريب وسجل المتعاقدين وخطة التدريب."}}',
     N'["ISO_22301","WORKFORCE"]'),

    -- 5. Regional disruption (BCP)
    (N'TPL-BCP-REGIONAL',
     N'Regional disruption — Eastern / Western',
     N'تعطل إقليمي — المنطقة الشرقية أو الغربية',
     N'BCP', N'REGIONAL',
     N'Weather, civil-defence advisory, or transport disruption isolates a regional cluster of branches and ATMs. Plan covers staff safety, channel redirection, and cash management.',
     N'حدث جوي أو إنذار للدفاع المدني أو تعطّل نقل يعزل تجمّعاً إقليمياً من الفروع والصرّافات. تغطي الخطة سلامة العاملين وتوجيه القنوات وإدارة النقد.',
     24, 12, 72,
     N'{"overview":{"en":"Regional cluster isolation event. Staff safety first; service continuity via digital channels + alternate branches.","ar":"عزل إقليمي. سلامة العاملين أولاً ثم استمرار الخدمة عبر القنوات الرقمية والفروع البديلة."},"scope":{"en":"Branch network in the affected region, regional ATMs, regional customer-service centre.","ar":"شبكة الفروع في المنطقة والصرّافات ومركز الخدمة الإقليمي."},"roles":{"en":"Regional GM · Branch Ops Lead · BCM Manager · Civil Defence liaison.","ar":"مدير المنطقة · قائد عمليات الفروع · مدير BCM · جهة اتصال الدفاع المدني."},"activation_criteria":{"en":"Civil Defence advisory affecting the region OR loss of access to ≥2 contiguous branches.","ar":"إنذار الدفاع المدني يخص المنطقة أو فقدان الوصول لـ 2+ فروع متجاورة."},"recovery_priorities":{"en":"Staff safety · ATM availability (where safe) · digital channel banner · cash-in-transit reroute.","ar":"السلامة · توفر ATM · لافتة القنوات الرقمية · إعادة توجيه نقل النقد."},"recovery_steps":{"en":"1. Coordinate with Civil Defence · 2. Close affected branches · 3. Stand up customer SMS · 4. Reroute cash logistics · 5. Monitor reopening criteria.","ar":"1) التنسيق · 2) إغلاق الفروع · 3) رسائل العملاء · 4) إعادة نقل النقد · 5) المراقبة."},"comms_plan":{"en":"Customer SMS + banner. Branch staff via WhatsApp groups. NCEMA briefing if event tier ≥3.","ar":"رسائل ولافتة. واتساب لطاقم الفروع. إحاطة NCEMA إذا كان المستوى 3+."},"manual_workaround":{"en":"Pre-positioned cash + offline ATM batch mode. Mobile teller van for high-impact zones.","ar":"نقد جاهز + ATM دفعي + فروع متنقّلة."},"resources":{"en":"Regional ops plan, mobile-branch unit, Civil Defence channels.","ar":"خطة المنطقة وفروع متنقلة وقنوات الدفاع المدني."},"supplier_escalation":{"en":"Cash-in-transit vendor, mobile-network provider, fuel logistics.","ar":"شركة نقل النقد والاتصالات والوقود."},"return_to_normal":{"en":"Civil Defence all-clear, branches reopen sequentially, lessons review.","ar":"رفع الإنذار وإعادة فتح الفروع وورشة الدروس."},"appendices":{"en":"Regional contact tree, Civil Defence reference numbers.","ar":"شجرة الاتصال الإقليمية وأرقام الدفاع المدني."}}',
     N'["NCEMA","CIVIL_DEFENCE","ISO_22301"]'),

    -- 6. Cloud provider regional outage (DRP)
    (N'TPL-DRP-CLOUD-REGION',
     N'Hyperscaler regional outage failover',
     N'تحوّل عند انقطاع منطقة المزوّد السحابي',
     N'DRP', N'CLOUD',
     N'Hyperscaler availability zone or region fails. DRP covers failover to the secondary region for digital channels, identity, and analytics workloads.',
     N'فشل منطقة المزوّد السحابي. يغطي DRP التحوّل إلى المنطقة الثانوية للقنوات الرقمية والهوية والتحليلات.',
     2, 1, 8,
     N'{"overview":{"en":"AZ-level or region-level outage at the hyperscaler. Assumes multi-region deployment with hot standby in the alternate region.","ar":"انقطاع منطقة أو AZ لدى المزوّد السحابي. تفترض الخطة نشراً متعدد المناطق مع وضع جاهز."},"scope":{"en":"Cloud-native services: digital channels, identity provider, API gateway, observability stack.","ar":"الخدمات السحابية: القنوات الرقمية والهوية وبوّابة API ومنصة المراقبة."},"roles":{"en":"Cloud Lead · SRE · Network Lead · IT-Recovery Owner.","ar":"قائد السحاب · SRE · قائد الشبكة · مالك التعافي."},"activation_criteria":{"en":"Hyperscaler status page confirms incident OR observability sees >50% error rate in primary region for 10+ min.","ar":"تأكيد المزوّد للحادث أو معدّل خطأ 50%+ في المنطقة الرئيسية لـ 10+ دقائق."},"recovery_priorities":{"en":"Identity → API gateway → digital channels → analytics (last).","ar":"الهوية ثم البوّابة ثم القنوات ثم التحليلات."},"recovery_steps":{"en":"1. DNS health-check triggers failover · 2. Validate identity in secondary region · 3. Switch API gateway traffic · 4. Validate end-to-end transaction · 5. Stand down primary until provider all-clear.","ar":"1) DNS · 2) الهوية · 3) البوّابة · 4) معاملة شاملة · 5) إيقاف الرئيسية حتى التعافي."},"comms_plan":{"en":"Customer banner on degradation. SAMA OPRES if digital channels degraded > 4h.","ar":"لافتة عند التدهور. SAMA إذا تجاوز التدهور 4 ساعات."},"manual_workaround":{"en":"None — digital channels are the workaround for branch-channel events.","ar":"لا يوجد — القنوات الرقمية هي البديل لانقطاع الفروع."},"resources":{"en":"Multi-region IaC, hot standby footprint, runbook, hyperscaler enterprise support.","ar":"IaC متعدد المناطق ووضع جاهز ودليل والدعم المؤسسي للمزوّد."},"supplier_escalation":{"en":"Hyperscaler TAM, enterprise support, internal cloud lead, CIO.","ar":"TAM ودعم مؤسسي وقائد سحاب و CIO."},"return_to_normal":{"en":"Provider all-clear; orchestrated failback in non-business hours; reconcile traffic + telemetry.","ar":"رفع الإنذار وتحوّل عكسي وتسوية الحركة."},"appendices":{"en":"Multi-region runbook, DNS TTL policy, supplier contacts.","ar":"دليل متعدد المناطق وسياسة DNS وجهات الاتصال."}}',
     N'["SAMA","CLOUD","ISO_22301"]'),

    -- 7. Telecom outage (BCP)
    (N'TPL-BCP-TELECOM',
     N'Telecom outage (STC / Mobily MPLS)',
     N'انقطاع شبكة الاتصالات (STC / Mobily)',
     N'BCP', N'TELECOM',
     N'Single-carrier fibre cut or MPLS outage isolates HQ from DR site or branches from HQ. Plan covers backup-carrier cutover and degraded-mode operations.',
     N'انقطاع كابل أو MPLS يعزل المقر عن موقع التعافي أو الفروع عن المقر. تغطي الخطة التحوّل لمزوّد ثانوي والوضع المخفّض.',
     4, 1, 24,
     N'{"overview":{"en":"Single-carrier fibre cut. Replication and inter-DC traffic affected. Backup carrier (Mobily) or 4G/5G LTE failover engaged.","ar":"انقطاع مزوّد منفرد. تأثر النسخ والحركة بين المراكز. تفعيل المزوّد الثانوي أو LTE."},"scope":{"en":"Primary STC MPLS routes between Riyadh HQ ↔ Jeddah DR, Riyadh ↔ branch backbone.","ar":"مسارات STC MPLS بين الرياض وجدّة والفروع."},"roles":{"en":"Network Lead · Telecom Vendor Manager · IT-Recovery Owner.","ar":"قائد الشبكة · مدير المورّد · مالك التعافي."},"activation_criteria":{"en":"Carrier reports outage OR observability sees ≥30% packet loss for 5+ min.","ar":"إقرار المزوّد أو فقد حزم 30%+ لـ 5+ دقائق."},"recovery_priorities":{"en":"Cards switch replication · core banking replication · branch backbone · digital channels.","ar":"نسخ سويتش البطاقات ثم الخدمات الأساسية ثم الفروع ثم القنوات."},"recovery_steps":{"en":"1. Confirm with carrier · 2. Activate backup carrier · 3. Validate replication lag · 4. Reroute branch traffic · 5. Notify SAMA if cards-switch lag exceeds threshold.","ar":"1) التأكيد · 2) المزوّد الثانوي · 3) فحص النسخ · 4) إعادة توجيه الفروع · 5) إشعار SAMA."},"comms_plan":{"en":"Internal-only unless customer impact realised. Carrier daily sync.","ar":"داخلياً ما لم يتأثر العميل. مزامنة يومية مع المزوّد."},"manual_workaround":{"en":"Branches operate via 4G LTE backup APN for transaction-light operations.","ar":"تعمل الفروع عبر APN احتياطي للمعاملات الخفيفة."},"resources":{"end":"Dual-carrier MPLS contract, 4G/5G LTE APN, network monitoring.","ar":"عقد مزدوج و APN و مراقبة الشبكة."},"supplier_escalation":{"en":"STC NOC, Mobily NOC, internal network team, CIO.","ar":"NOC STC و NOC Mobily والشبكة الداخلية و CIO."},"return_to_normal":{"en":"Carrier restored; orchestrated cutback; replication validated.","ar":"استعادة المزوّد وعودة منظّمة وتحقق النسخ."},"appendices":{"en":"Carrier MSAs, network topology, escalation tree.","ar":"عقود المزوّدين والرسم الطوبوغرافي وشجرة التصعيد."}}',
     N'["SAMA","TELECOM","ISO_22301"]'),

    -- 8. Pandemic / mass-absenteeism (BCP)
    (N'TPL-BCP-PANDEMIC',
     N'Pandemic / mass workforce absenteeism',
     N'جائحة أو غياب جماعي',
     N'BCP', N'WORKFORCE',
     N'Communicable-disease event triggers mass remote-work and ≥30% workforce unavailability. Plan covers remote enablement, branch reduced-mode, and supplier coordination.',
     N'حدث صحي يؤدي إلى عمل عن بُعد جماعي وعدم توفر 30%+ من القوى العاملة. تغطي الخطة التمكين عن بُعد والوضع المخفّض للفروع وتنسيق المورّدين.',
     12, 4, 720,
     N'{"overview":{"en":"Public-health event. Activation typically lasts weeks; phases the team through enable-remote → reduce-branch → sustain-essential-only.","ar":"حدث صحة عامة. التفعيل يستمر أسابيع: تمكين عن بُعد ثم خفض الفروع ثم الأساسي فقط."},"scope":{"en":"All workforce; all branches; suppliers required for cash, logistics, IT.","ar":"كل العاملين والفروع والمورّدين الحيويين."},"roles":{"en":"CEO · COO · HR Director · BCM Manager · IT-Recovery Owner.","ar":"CEO · COO · مدير HR · مدير BCM · مالك التعافي."},"activation_criteria":{"en":"Government health advisory OR ≥15% workforce absent for 3 consecutive days.","ar":"إنذار حكومي أو غياب 15%+ من القوى لـ 3 أيام متتالية."},"recovery_priorities":{"en":"Critical services first (cards · core banking · treasury); branches reduced-mode; back-office shift-rotation.","ar":"الخدمات الحيوية أولاً ثم الفروع بوضع مخفّض ثم التناوب."},"recovery_steps":{"en":"1. Activate remote-work playbook · 2. Issue staff guidance · 3. Reduce branch hours / staffing · 4. Engage suppliers on contingencies · 5. Set up daily exec sync.","ar":"1) العمل عن بُعد · 2) الإرشاد · 3) خفض الفروع · 4) المورّدون · 5) مزامنة يومية."},"comms_plan":{"en":"Internal daily exec memo; external customer banner; MoH liaison.","ar":"مذكرة داخلية يومية ولافتة خارجية وتنسيق مع MoH."},"manual_workaround":{"en":"Digital channels carry the bulk; branches by appointment.","ar":"القنوات الرقمية تحمل المعظم؛ الفروع بمواعيد."},"resources":{"en":"Remote-work licenses, VPN/ZTNA capacity, branch sanitisation supplies, HR comms.","ar":"تراخيص العمل عن بُعد وشبكة VPN و مستلزمات التعقيم وإدارة الموارد البشرية."},"supplier_escalation":{"en":"Cleaning services, mobile-network providers, cash-in-transit, IT vendor support.","ar":"التنظيف والاتصالات ونقل النقد ودعم تقنية المعلومات."},"return_to_normal":{"en":"Phased return to office, retain hybrid policy, lessons review with HR.","ar":"عودة تدريجية للمكتب مع سياسة هجينة ودروس مع HR."},"appendices":{"en":"Remote-work playbook, MoH contact, hybrid policy.","ar":"دليل العمل عن بُعد و MoH وسياسة هجينة."}}',
     N'["MOH","NCEMA","ISO_22301","WORKFORCE"]');
END
GO

-- ─── Seed: 10 exercise scenarios ────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.exercise_scenarios)
BEGIN
    INSERT INTO bcm.exercise_scenarios
      (scenario_code, title_en, title_ar, scenario_category, exercise_type,
       duration_minutes, description_en, description_ar, objectives_en, objectives_ar,
       injects_json, success_criteria_json, role_assignments_json, tags_json)
    VALUES

    (N'EXS-RANSOMWARE-TABLETOP-2026', N'Ransomware tabletop — Q2 2026', N'تمرين فدية حواري Q2 2026',
     N'CYBER', N'TABLETOP', 90,
     N'90-minute facilitated discussion. Crisis team walks through detection → isolation → notification → recovery decisions for a ransomware event hitting tier-1 systems.',
     N'تمرين حواري لمدة 90 دقيقة. يسير فريق الأزمات عبر قرارات الاكتشاف والعزل والإشعار والاستعادة لحدث فدية على الأنظمة الحيوية.',
     N'Validate the BCP-RANSOMWARE plan readiness, role coverage, and escalation timing.',
     N'التحقق من جاهزية خطة الفدية وتغطية الأدوار وتوقيتات التصعيد.',
     N'[{"at_min":0,"title_en":"EDR alert: 3 hosts encrypted","title_ar":"تنبيه EDR: تشفير 3 مضيفين","expected_action":"SOC confirms scope and pages the crisis manager"},{"at_min":15,"title_en":"Spreading laterally","title_ar":"انتشار جانبي","expected_action":"Network isolates affected VLANs"},{"at_min":35,"title_en":"Dark-web post claiming exfiltration","title_ar":"منشور على الشبكة المظلمة يدّعي التسريب","expected_action":"Trigger PDPL assessment and SAMA notification"},{"at_min":60,"title_en":"Customer-facing app unavailable","title_ar":"تعطّل تطبيق العملاء","expected_action":"Activate BCP-RANSOMWARE recovery_steps"}]',
     N'[{"criterion_en":"Crisis manager acknowledges within 15 min","criterion_ar":"يُقرّ مدير الأزمات خلال 15 دقيقة","weight":1.0},{"criterion_en":"All affected VLANs isolated within 30 min","criterion_ar":"عزل كل VLAN المتأثر خلال 30 دقيقة","weight":1.0},{"criterion_en":"SAMA notification draft ready within 60 min","criterion_ar":"إشعار SAMA جاهز خلال 60 دقيقة","weight":1.5},{"criterion_en":"Customer comms drafted within 60 min","criterion_ar":"اتصالات العملاء خلال 60 دقيقة","weight":1.0}]',
     N'[{"role":"Crisis Manager","count":1},{"role":"CISO","count":1},{"role":"SOC Lead","count":1},{"role":"Comms Lead","count":1},{"role":"Legal","count":1},{"role":"BCM Manager","count":1}]',
     N'["CYBER","TABLETOP","SAMA","NCA"]'),

    (N'EXS-DC-FAILOVER-FUNCTIONAL-2026', N'DC failover functional exercise', N'تمرين تحوّل مركز البيانات الفعلي',
     N'INFRASTRUCTURE', N'FAILOVER', 180,
     N'Live failover of non-production replica to Jeddah DR site for two hours, with full validation of cards switch acceptance and treasury feed continuity.',
     N'تحوّل فعلي لنسخة غير إنتاجية إلى موقع جدّة لمدة ساعتين مع التحقق من قبول سويتش البطاقات واستمرارية تغذية الخزينة.',
     N'Prove the DC failover runbook works end-to-end without manual intervention beyond go/no-go points.',
     N'إثبات أن دليل التحوّل يعمل دون تدخل يدوي خارج نقاط القرار.',
     N'[{"at_min":0,"title_en":"Simulated power loss Riyadh DC","title_ar":"محاكاة فقد طاقة في DC الرياض","expected_action":"Trigger declaration"},{"at_min":10,"title_en":"DR systems healthy","title_ar":"أنظمة DR سليمة","expected_action":"Begin DNS cutover"},{"at_min":40,"title_en":"Mada acceptance test","title_ar":"اختبار قبول Mada","expected_action":"Run synthetic transaction"},{"at_min":90,"title_en":"Treasury feed restored","title_ar":"عودة تغذية الخزينة","expected_action":"Sign-off by Treasury Lead"}]',
     N'[{"criterion_en":"Declaration to DNS cutover ≤ 15 min","criterion_ar":"الإعلان إلى تحوّل DNS ≤ 15 دقيقة","weight":1.5},{"criterion_en":"Mada synthetic transaction passes","criterion_ar":"اختبار Mada يجتاز","weight":2.0},{"criterion_en":"No manual escalation outside go/no-go points","criterion_ar":"لا تصعيد يدوي","weight":1.0},{"criterion_en":"Treasury Lead sign-off within 90 min","criterion_ar":"اعتماد قائد الخزينة خلال 90 دقيقة","weight":1.0}]',
     N'[{"role":"IT-Recovery Owner","count":1},{"role":"DBA Lead","count":2},{"role":"Network Lead","count":1},{"role":"Treasury Lead","count":1}]',
     N'["SAMA","INFRASTRUCTURE","FAILOVER"]'),

    (N'EXS-SUPPLIER-WALKTHROUGH-2026', N'Supplier outage walkthrough', N'استعراض انقطاع المورّد',
     N'SUPPLIER', N'WALKTHROUGH', 60,
     N'Walk through the SCM register, pick the tier-1 supplier most exposed this quarter, and walk the BCP-SUPPLIER-FAIL plan against that supplier''s specific dependencies.',
     N'استعراض سجل SCM واختيار المورّد الأكثر تعرّضاً هذا الربع وتطبيق خطة BCP-SUPPLIER-FAIL على تبعياته.',
     N'Refresh the supplier failure runbook with current contact info and re-validate the alternate-supplier MoU.',
     N'تحديث الدليل ببيانات الاتصال الحالية وإعادة التحقق من اتفاقية المورّد البديل.',
     N'[{"at_min":0,"title_en":"Pick supplier from SCM Tier-1 list","title_ar":"اختيار مورّد Tier-1","expected_action":"Owner walks through dependencies"},{"at_min":20,"title_en":"Alternate supplier MoU status","title_ar":"حالة اتفاقية البديل","expected_action":"Confirm signature + escalation contacts"},{"at_min":45,"title_en":"Simulate 6-hour outage","title_ar":"محاكاة انقطاع 6 ساعات","expected_action":"Walk through customer comms"}]',
     N'[{"criterion_en":"Contact list verified up-to-date","criterion_ar":"تحديث جهات الاتصال","weight":1.0},{"criterion_en":"Alternate-supplier MoU validated","criterion_ar":"التحقق من الاتفاقية","weight":1.0},{"criterion_en":"Regulator notification template ready","criterion_ar":"قالب الإشعار جاهز","weight":1.0}]',
     N'[{"role":"SCM Manager","count":1},{"role":"BCM Manager","count":1},{"role":"Affected Service Owner","count":1}]',
     N'["SCM","SAMA"]'),

    (N'EXS-CALL-TREE-Q1-2026', N'Quarterly call-tree test', N'اختبار شجرة الاتصال الربعي',
     N'WORKFORCE', N'WALKTHROUGH', 45,
     N'Phone test of the full crisis-comms tree. Each contact is dialled in escalation order; result recorded as PASSED / FAILED / NOT_REACHED.',
     N'اختبار هاتفي لشجرة الاتصال الكاملة. يُتصل بكل جهة بترتيب التصعيد وتُسجّل النتيجة.',
     N'Verify ≥ 95% reachability and refresh stale numbers within 5 business days.',
     N'التحقق من قابلية الوصول 95%+ وتحديث الأرقام القديمة خلال 5 أيام عمل.',
     N'[{"at_min":0,"title_en":"BCM Manager initiates round","title_ar":"يبدأ مدير BCM الجولة","expected_action":"Call cascade starts"},{"at_min":15,"title_en":"Escalation tier 1 complete","title_ar":"اكتمال المستوى 1","expected_action":"Record metrics"},{"at_min":30,"title_en":"Escalation tier 2 complete","title_ar":"اكتمال المستوى 2","expected_action":"Identify unreachable contacts"}]',
     N'[{"criterion_en":"≥95% contacts answered within SLA","criterion_ar":"95%+ ضمن SLA","weight":2.0},{"criterion_en":"Stale numbers logged within 24h","criterion_ar":"تسجيل الأرقام القديمة خلال 24 ساعة","weight":1.0},{"criterion_en":"Gap raised for any tier-1 unreachable","criterion_ar":"فجوة لكل tier-1 غير مُتصل","weight":1.0}]',
     N'[{"role":"BCM Manager","count":1},{"role":"Crisis Comms Lead","count":1}]',
     N'["BCM","COMMS"]'),

    (N'EXS-REGIONAL-WEATHER-2026', N'Regional weather simulation — Eastern Province', N'محاكاة جوية إقليمية — المنطقة الشرقية',
     N'REGIONAL', N'SIMULATION', 120,
     N'Simulated Civil Defence flooding advisory closes 3 branches in Dammam/Khobar for 36 hours. Team exercises regional disruption plan including cash-in-transit reroute.',
     N'محاكاة إنذار للدفاع المدني يغلق 3 فروع في الدمام/الخبر 36 ساعة. يطبّق الفريق خطة التعطل الإقليمي مع إعادة توجيه نقل النقد.',
     N'Validate the BCP-REGIONAL plan including Civil Defence liaison process and cash-in-transit MoU.',
     N'التحقق من خطة BCP-REGIONAL والتنسيق مع الدفاع المدني واتفاقية نقل النقد.',
     N'[{"at_min":0,"title_en":"Civil Defence advisory issued","title_ar":"إنذار الدفاع المدني","expected_action":"Activate regional plan"},{"at_min":20,"title_en":"3 branches closed","title_ar":"إغلاق 3 فروع","expected_action":"Customer SMS"},{"at_min":60,"title_en":"Cash-in-transit reroute requested","title_ar":"إعادة نقل النقد","expected_action":"CIT vendor confirms"},{"at_min":100,"title_en":"Mobile teller van deployed","title_ar":"نشر الفروع المتنقلة","expected_action":"Service partial restoration"}]',
     N'[{"criterion_en":"Customer SMS dispatched within 30 min","criterion_ar":"إرسال SMS خلال 30 دقيقة","weight":1.0},{"criterion_en":"Civil Defence reference logged","criterion_ar":"تسجيل مرجع الدفاع المدني","weight":1.0},{"criterion_en":"CIT vendor confirms reroute within 60 min","criterion_ar":"يؤكّد المورّد إعادة التوجيه خلال 60 دقيقة","weight":1.5}]',
     N'[{"role":"Regional GM","count":1},{"role":"Branch Ops Lead","count":1},{"role":"BCM Manager","count":1}]',
     N'["NCEMA","CIVIL_DEFENCE","REGIONAL"]'),

    (N'EXS-CLOUD-AZ-FAILOVER-2026', N'Hyperscaler AZ failover test', N'اختبار تحوّل AZ',
     N'CLOUD', N'FAILOVER', 60,
     N'Functional test of multi-region cloud failover for digital channels. Traffic shifted to secondary region for 60 minutes.',
     N'اختبار وظيفي للتحوّل متعدد المناطق للقنوات الرقمية. تحويل الحركة إلى المنطقة الثانوية لـ 60 دقيقة.',
     N'Confirm DNS health-checks trigger failover within 2 minutes and that the secondary region handles full load.',
     N'تأكيد تحفيز DNS لتحوّل خلال دقيقتين وتحمّل المنطقة الثانوية للحمل الكامل.',
     N'[{"at_min":0,"title_en":"Block traffic to primary AZ","title_ar":"حجب الحركة عن AZ الرئيسي","expected_action":"Trigger DNS failover"},{"at_min":5,"title_en":"Validate end-to-end transaction","title_ar":"تأكيد معاملة شاملة","expected_action":"Synthetic monitor passes"},{"at_min":50,"title_en":"Plan failback","title_ar":"تخطيط العودة","expected_action":"Notify ops"}]',
     N'[{"criterion_en":"DNS failover completes ≤ 2 min","criterion_ar":"تحوّل DNS ≤ 2 دقيقة","weight":2.0},{"criterion_en":"Secondary region passes synthetic","criterion_ar":"اجتياز الثانوية","weight":2.0},{"criterion_en":"Failback completes ≤ 30 min","criterion_ar":"العودة ≤ 30 دقيقة","weight":1.0}]',
     N'[{"role":"Cloud Lead","count":1},{"role":"SRE","count":2}]',
     N'["CLOUD","SAMA"]'),

    (N'EXS-PDPL-BREACH-TABLETOP-2026', N'PDPL Article 23 breach tabletop', N'تمرين خرق PDPL المادة 23',
     N'CYBER', N'TABLETOP', 60,
     N'Tabletop exercise for a personal-data breach: 50,000 customer records exposed. Team walks through PDPL Article 23 notification timeline and customer-comms drafting.',
     N'تمرين حواري لخرق بيانات شخصية: تسرّب 50,000 سجل عميل. يطبّق الفريق توقيت الإشعار وفق المادة 23 ومسودة إخطار العملاء.',
     N'Validate the 72-hour PDPL notification timeline and the customer-comms draft chain.',
     N'التحقق من مهلة 72 ساعة وسلسلة إخطار العملاء.',
     N'[{"at_min":0,"title_en":"Breach confirmed","title_ar":"تأكيد الخرق","expected_action":"Page DPO + Legal + CISO"},{"at_min":15,"title_en":"Scope: 50K records","title_ar":"النطاق: 50 ألف سجل","expected_action":"PDPL impact assessment"},{"at_min":40,"title_en":"Draft customer notification","title_ar":"مسودة إخطار العملاء","expected_action":"Legal review"}]',
     N'[{"criterion_en":"DPO paged within 15 min","criterion_ar":"إشعار DPO خلال 15 دقيقة","weight":1.5},{"criterion_en":"PDPL impact assessment drafted within 60 min","criterion_ar":"تقييم التأثير خلال 60 دقيقة","weight":1.5},{"criterion_en":"Customer notification draft ready within 24h","criterion_ar":"الإخطار خلال 24 ساعة","weight":2.0}]',
     N'[{"role":"DPO","count":1},{"role":"Legal","count":1},{"role":"CISO","count":1},{"role":"Comms Lead","count":1}]',
     N'["PDPL","CYBER"]'),

    (N'EXS-TREASURY-OPS-2026', N'Treasury back-office key-person exercise', N'تمرين كوادر الخزينة',
     N'WORKFORCE', N'WALKTHROUGH', 60,
     N'Walkthrough exercise: Treasury back-office lead becomes unavailable on a settlement day. Cross-trained backup steps in.',
     N'تمرين استعراض: عدم توفر قائد الخزينة في يوم تسوية. يحل المعاون المتدرّب.',
     N'Validate cross-training coverage and dual-control kicks in for any non-standard decision.',
     N'التحقق من تغطية التدريب وضوابط مزدوجة لأي قرار غير قياسي.',
     N'[{"at_min":0,"title_en":"Treasury lead unavailable","title_ar":"عدم توفر قائد الخزينة","expected_action":"Activate backup"},{"at_min":15,"title_en":"Settlement window opens","title_ar":"فتح نافذة التسوية","expected_action":"Backup executes runbook"},{"at_min":40,"title_en":"SARIE settlement","title_ar":"تسوية SARIE","expected_action":"Dual-control sign-off"}]',
     N'[{"criterion_en":"Backup runs end-to-end without escalation","criterion_ar":"يكمل المعاون دون تصعيد","weight":2.0},{"criterion_en":"Dual-control on every settlement","criterion_ar":"ضبط مزدوج لكل تسوية","weight":1.0},{"criterion_en":"Settlement completes within window","criterion_ar":"التسوية ضمن النافذة","weight":1.5}]',
     N'[{"role":"Treasury Backup","count":1},{"role":"Settlements Officer","count":1},{"role":"Compliance","count":1}]',
     N'["TREASURY","SAMA"]'),

    (N'EXS-HAJJ-PEAK-LOAD-2026', N'Hajj peak load drill — pilgrim payments', N'تمرين ذروة الحج — مدفوعات الحجاج',
     N'REGIONAL', N'SIMULATION', 90,
     N'Simulate 4x normal POS / Mada transaction volume in Makkah + Madinah during Hajj week, plus a card-issuer outage at peak.',
     N'محاكاة 4x الحركة في مكة والمدينة خلال أسبوع الحج مع انقطاع مُصدِر بطاقة في الذروة.',
     N'Confirm cards switch capacity headroom and the Hajj-specific runbook works under degraded conditions.',
     N'تأكيد سعة سويتش البطاقات ودليل الحج تحت ظروف منخفضة.',
     N'[{"at_min":0,"title_en":"Traffic ramp begins","title_ar":"بدء صعود الحركة","expected_action":"Capacity team monitors"},{"at_min":30,"title_en":"Peak hit — 4x normal","title_ar":"الذروة — 4x","expected_action":"Auto-scale validates"},{"at_min":50,"title_en":"Card-issuer connector down","title_ar":"انقطاع موصّل المصدّر","expected_action":"Fallback to stand-in processing"},{"at_min":75,"title_en":"Issuer restored","title_ar":"استعادة المصدّر","expected_action":"Reconcile"}]',
     N'[{"criterion_en":"Auto-scale absorbs 4x without errors","criterion_ar":"التوسّع التلقائي يستوعب 4x","weight":2.0},{"criterion_en":"Stand-in processing activates ≤ 60 sec","criterion_ar":"المعالجة البديلة ≤ 60 ث","weight":2.0},{"criterion_en":"Reconciliation completes same-day","criterion_ar":"التسوية في اليوم","weight":1.0}]',
     N'[{"role":"Cards Lead","count":1},{"role":"SRE","count":2},{"role":"Capacity Engineer","count":1}]',
     N'["HAJJ","SAMA","MADA","CAPACITY"]'),

    (N'EXS-RAMADAN-STAFFING-2026', N'Ramadan reduced-staffing exercise', N'تمرين قوة العمل في رمضان',
     N'WORKFORCE', N'WALKTHROUGH', 60,
     N'Walkthrough of operational impact of reduced Ramadan hours and Eid week skeleton staffing on critical operations.',
     N'استعراض الأثر التشغيلي لساعات رمضان وأسبوع العيد المخفّض على العمليات الحيوية.',
     N'Confirm shift-rotation coverage and dual-control compensation for reduced bench depth.',
     N'تأكيد تغطية التناوب وتعويض الضبط المزدوج عن قلة العمق.',
     N'[{"at_min":0,"title_en":"Ramadan hours start","title_ar":"بدء ساعات رمضان","expected_action":"Confirm coverage matrix"},{"at_min":20,"title_en":"Eid week begins","title_ar":"بدء أسبوع العيد","expected_action":"Skeleton staffing validated"},{"at_min":45,"title_en":"Simulated incident — small outage","title_ar":"محاكاة حادث صغير","expected_action":"Confirm response time"}]',
     N'[{"criterion_en":"Coverage matrix complete","criterion_ar":"اكتمال مصفوفة التغطية","weight":1.0},{"criterion_en":"Response within standard SLA in Eid week","criterion_ar":"الاستجابة ضمن SLA","weight":1.5},{"criterion_en":"Dual-control honoured throughout","criterion_ar":"ضبط مزدوج محترم","weight":1.0}]',
     N'[{"role":"HR Director","count":1},{"role":"BCM Manager","count":1},{"role":"Operations Lead","count":1}]',
     N'["RAMADAN","EID","WORKFORCE"]');
END
GO

-- ─── Seed: 6 KSA peak seasons (Gregorian 2026, Hijri 1447/1448) ────
IF NOT EXISTS (SELECT 1 FROM bcm.peak_seasons)
BEGIN
    INSERT INTO bcm.peak_seasons
      (season_code, season_name_en, season_name_ar, season_type,
       gregorian_start_date, gregorian_end_date, hijri_start_year, hijri_start_month,
       risk_dimensions_json, affected_services, recommended_actions, notes_en, notes_ar)
    VALUES

    (N'KSA-HAJJ-2026', N'Hajj 2026', N'موسم الحج 1447',
     N'HAJJ',
     '2026-05-22', '2026-06-02', 1447, 12,
     N'["TRANSACTION_VOLUME","TRAVEL_LOAD","REGULATORY_VISIBILITY","STAFF_AVAILABILITY","NETWORK_LOAD"]',
     N'Pilgrim payments, ATM availability in Makkah/Madinah, Mada acceptance, card issuer connectivity, Tawakkalna integration',
     N'Pre-Hajj tabletop in Q1; capacity test at +4x normal; reduce non-essential change windows; coordinate with SAMA + MoH; 24/7 NOC coverage.',
     N'Annual peak in Makkah + Madinah covering Tarwiyah, Arafat, Eid Al-Adha, and the days of Tashreeq. Card-issuer connectors and Mada switch are the typical pinch points.',
     N'الذروة السنوية في مكة والمدينة تغطي التروية وعرفة وعيد الأضحى وأيام التشريق. موصّلات المصدّر وسويتش Mada هي نقاط الاختناق المعتادة.'),

    (N'KSA-RAMADAN-2026', N'Ramadan 2026', N'رمضان 1447',
     N'RAMADAN',
     '2026-02-17', '2026-03-18', 1447, 9,
     N'["STAFF_AVAILABILITY","REDUCED_HOURS","TRANSACTION_VOLUME","COMMS_LOAD"]',
     N'All branches (reduced hours), digital channels (load spike post-iftar), ATM cash logistics',
     N'Schedule changes during reduced-hours window; coordinate cash-in-transit overnight; expect post-iftar transaction spikes 21:00–24:00.',
     N'Operating-hours reduction across the Kingdom. Post-iftar window typically sees 2-3x normal digital-channel transactions.',
     N'تخفيض ساعات العمل في المملكة. عادة ما تشهد فترة ما بعد الإفطار 2-3x للحركة الرقمية.'),

    (N'KSA-EID-FITR-2026', N'Eid Al-Fitr 2026', N'عيد الفطر 1447',
     N'EID_AL_FITR',
     '2026-03-19', '2026-03-25', 1447, 10,
     N'["TRANSACTION_VOLUME","STAFF_AVAILABILITY","CASH_DEMAND"]',
     N'ATM cash demand, eidiyah transfers, branch closures, customer comms',
     N'Pre-position cash in ATMs; expect SARIE transfer spikes for eidiyah; reduce branch coverage; freeze non-essential change windows.',
     N'Eid week — high cash demand, peak transfer volumes for eidiyah, reduced branch hours.',
     N'أسبوع العيد — طلب نقد عالٍ، ذروة تحويلات العيدية، ساعات فروع مخفّضة.'),

    (N'KSA-EID-ADHA-2026', N'Eid Al-Adha 2026', N'عيد الأضحى 1447',
     N'EID_AL_ADHA',
     '2026-05-26', '2026-06-02', 1447, 12,
     N'["TRANSACTION_VOLUME","CASH_DEMAND","REGIONAL_LOAD"]',
     N'ATM cash demand (Makkah/Madinah peak), branch closures, livestock-trade transfers',
     N'Coincides with Hajj peak — double allocation of capacity and ops attention.',
     N'Concurrent with Hajj — capacity strain doubled. Heavy cash demand for livestock and gift-giving.',
     N'بالتوازي مع الحج — ضغط مضاعف. طلب نقد كثيف للأضاحي والهدايا.'),

    (N'KSA-NATIONAL-DAY-2026', N'Saudi National Day 2026', N'اليوم الوطني 1447',
     N'NATIONAL_DAY',
     '2026-09-23', '2026-09-23', NULL, NULL,
     N'["TRANSACTION_VOLUME","NETWORK_LOAD","COMMS_LOAD"]',
     N'Digital channels (campaign load), branch closures, marketing payments',
     N'Expect marketing campaigns + 96th anniversary spike; pre-warm autoscaling.',
     N'Single-day spike from marketing + national-celebration traffic. Branches closed.',
     N'ذروة يوم واحد بسبب التسويق والاحتفال الوطني. الفروع مغلقة.'),

    (N'KSA-UMRAH-PEAK-2026-Q4', N'Umrah peak season Q4 2026', N'ذروة موسم العمرة Q4 2026',
     N'UMRAH_PEAK',
     '2026-10-15', '2026-12-15', 1448, 4,
     N'["TRANSACTION_VOLUME","TRAVEL_LOAD","TAWAKKALNA_INTEGRATION"]',
     N'Pilgrim payments, hotel booking flows, MasterCard/Visa acceptance in Makkah',
     N'2-month peak post-summer; coordinate with Hajj+Umrah ministry; ensure Tawakkalna integration health.',
     N'Autumn Umrah peak — ~60-day window of elevated traffic. Less acute than Hajj but longer duration.',
     N'ذروة العمرة الخريفية — نافذة 60 يوماً من الحركة المرتفعة. أقل حدّة من الحج لكنها أطول.');
END
GO

PRINT 'V111 templates + scenarios + peak-seasons seeded.';
GO

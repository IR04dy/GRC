-- V191 — Sprint 12: Embedded Contextual Help.
--
-- Every page in the platform now hosts a floating "?" button that opens
-- a context-aware help drawer. The drawer resolves which topic to show
-- based on the current URL path. The same drawer surfaces a Saudi-
-- regulator anchor (SAMA / NCA / SDAIA / CMA / ZATCA article) and a
-- pointer to the related Knowledge-Base article so users have a clear
-- escalation path: in-line tip → KB article → regulator portal.
--
-- Why a dedicated `help_topic` table (rather than reusing kb.articles)?
--   1. Routing semantics — help is keyed by URL pattern, KB is keyed
--      by slug. The drawer needs an O(1) lookup by `route_pattern`.
--   2. Lifecycle — help topics are short, every-page snippets curated
--      by content-editors; KB articles are long-form, owned by SMEs.
--      Mixing the two pollutes both surfaces.
--   3. Multi-tenancy — every help row carries a nullable
--      organisation_id. NULL = platform-global (the 30 baseline rows
--      pre-seeded below). Non-NULL = org-specific override (a Saudi
--      bank rephrases the risk-register tip for its own RBAC names).
--      The service layer prefers the org-specific row when present.
--
-- Regulatory anchor: NCA ECC-2 control 1-5 (Cybersecurity Awareness
-- and Training) requires a documented training surface. In-platform
-- contextual help is that surface for the operator population. We
-- persist the route + topic_code together so audit reviews can
-- reconstruct "which help text was visible on which page when".
--
-- Soft-delete + audit + ROWVERSION on the table. F7+F8 events fire
-- from HelpTopicService on every mutation + on `track-view`.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'platform') IS NULL EXEC(N'CREATE SCHEMA platform');
GO

-- ─── 1. help_topic table ──────────────────────────────────────────
IF OBJECT_ID(N'platform.help_topic', N'U') IS NULL
BEGIN
    CREATE TABLE platform.help_topic (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        -- Nullable: NULL means platform-global (visible to every tenant).
        -- A non-null value scopes the row to one tenant which overrides
        -- the global row for that route.
        organisation_id               BIGINT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Stable string id used by both the UI (deep-link to a topic
        -- from a Cmd+K result) and by the seed migration. Globally
        -- unique within live rows.
        topic_code                    NVARCHAR(64)  NOT NULL,

        title_en                      NVARCHAR(255) NOT NULL,
        title_ar                      NVARCHAR(255) NOT NULL,
        summary_en                    NVARCHAR(500) NULL,
        summary_ar                    NVARCHAR(500) NULL,

        -- Markdown body. The drawer renders this via a minimal
        -- bilingual markdown renderer (no remote images, no scripts).
        body_en                       NVARCHAR(MAX) NOT NULL,
        body_ar                       NVARCHAR(MAX) NOT NULL,

        -- URL path or regex this topic matches. Examples:
        --   /risk/register                     — exact match
        --   /cyber/attack-surface              — exact match
        --   /cyber/attack-surface/*            — wildcard (regex)
        --   /bcm/exercises/run/*/observe       — wildcard mid-path
        -- Matching precedence is: exact > wildcard, longest-wildcard
        -- wins on ties. Org-specific row beats platform-global.
        route_pattern                 NVARCHAR(255) NOT NULL,

        -- Module the topic belongs to. Used by the admin page's
        -- coverage-gap widget + by "Related wizards" filtering.
        -- RISK/CYBER/COMPLIANCE/BCM/PRIVACY/TPRM/ESG/AUDIT/
        -- AI_GOVERNANCE/CCM/GENERAL/ADMIN
        module                        VARCHAR(40)   NOT NULL,

        -- Free-form short string with the regulator citation. Example:
        -- "SAMA CSF 3.3.8 + NCA ECC-2 2-12". Kept as a single column
        -- (rather than a multi-row anchor table) because the drawer
        -- always renders it as a single text line + link.
        regulatory_anchor             NVARCHAR(500) NULL,
        regulator_link_url            NVARCHAR(500) NULL,

        -- Soft-FK to the KB article that goes deeper on the same
        -- topic. NULL when no curated long-form article exists.
        related_kb_article_id         BIGINT        NULL,

        -- Comma-separated wizard codes the help drawer should
        -- surface as "Related wizards" (e.g. "WF-ERM-RISK-IDENT,
        -- WF-ERM-RISK-TREATMENT" on the risk-register help topic).
        related_workflow_codes        NVARCHAR(500) NULL,

        -- Visual tone in the drawer header. INFO/TIP/WARNING.
        severity                      VARCHAR(20)   NOT NULL DEFAULT N'INFO',

        -- Sort key inside the drawer's "this page" list when more
        -- than one topic matches the route (e.g. parent + child).
        display_order                 INT           NOT NULL DEFAULT 100,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_help_topic_severity CHECK (severity IN
            (N'INFO', N'TIP', N'WARNING')),
        CONSTRAINT CK_help_topic_module CHECK (module IN
            (N'RISK', N'CYBER', N'COMPLIANCE', N'BCM', N'PRIVACY',
             N'TPRM', N'ESG', N'AUDIT', N'AI_GOVERNANCE', N'CCM',
             N'GENERAL', N'ADMIN'))
    );
END
GO

-- Unique topic code per live row. The seed loads platform-global
-- (organisation_id NULL) topics first; an org-specific override
-- gets a different topic_code (e.g. HELP-RISK-REGISTER-SAMA-BANK)
-- so the unique constraint never blocks a tenant from carrying
-- both rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_help_topic_code' AND object_id = OBJECT_ID(N'platform.help_topic'))
    CREATE UNIQUE INDEX UQ_help_topic_code ON platform.help_topic (topic_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_help_topic_external' AND object_id = OBJECT_ID(N'platform.help_topic'))
    CREATE UNIQUE INDEX UQ_help_topic_external ON platform.help_topic (external_id) WHERE is_deleted = 0;
GO
-- Hot-path lookup: drawer queries "topic for this route" on every
-- open. Filtered index keeps the lookup tight when there are many
-- rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_help_topic_route' AND object_id = OBJECT_ID(N'platform.help_topic'))
    CREATE INDEX IX_help_topic_route ON platform.help_topic (organisation_id, route_pattern) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_help_topic_module' AND object_id = OBJECT_ID(N'platform.help_topic'))
    CREATE INDEX IX_help_topic_module ON platform.help_topic (organisation_id, module) WHERE is_deleted = 0;
GO

-- ─── 2. Pre-seed 30 platform-global topics ─────────────────────────
-- Every row has organisation_id NULL so it surfaces to every tenant.
-- Bilingual EN/AR for every topic. Saudi-native AR phrasing — not
-- literal translations. Idempotent — IF NOT EXISTS on topic_code.
--
-- All bodies are short, action-first markdown. The drawer expects
-- a "Quick guide / Key steps" pattern and renders the regulator
-- anchor as a separate highlighted callout.

-- Helper: each INSERT block guards the row with NOT EXISTS so the
-- migration is re-runnable on a partially-seeded DB.

-- 1. Dashboard ─────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-DASHBOARD' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-DASHBOARD',
     N'Welcome to TSS GRC',
     N'مرحباً بكم في منصة الحوكمة وإدارة المخاطر والامتثال',
     N'Your one-screen view of risk, compliance, cyber and continuity posture.',
     N'لوحة واحدة تجمع وضع المخاطر والامتثال والأمن السيبراني واستمرارية الأعمال.',
     N'## Quick guide

The dashboard pulls together every register the platform tracks: risks, controls, incidents, vendors, vulnerabilities, evidence, and committee actions.

### Key steps
1. Use the top KPIs to spot what needs attention this week.
2. Drill from any tile into the underlying register.
3. Press `Cmd/Ctrl+K` to open the command palette and jump to any page.

The "?" button you just clicked stays available on every screen — open it whenever you want regulator-aware guidance.',
     N'## دليل سريع

تجمع اللوحة الرئيسية كل سجلات المنصة: المخاطر، الضوابط، الحوادث، الموردون، الثغرات، الأدلة، وقرارات اللجان.

### الخطوات الأساسية
1. ابدأ بمؤشرات الأداء العلوية لمعرفة أولويات الأسبوع.
2. اضغط على أيّ بطاقة للتعمّق في السجل المعني.
3. استخدم `Cmd/Ctrl+K` لفتح لوحة الأوامر والانتقال السريع.

سيبقى زر «؟» الذي ضغطته متاحاً في كل صفحة — افتحه متى أردت إرشاداً متوافقاً مع المنظّمين السعوديين.',
     N'/dashboard', N'GENERAL',
     N'NCA ECC-2 1-5 (Cybersecurity Awareness)',
     N'https://nca.gov.sa/regulations/ecc/ar',
     NULL, N'INFO', 10);

-- 2. Risk register ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-RISK-REGISTER' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-RISK-REGISTER',
     N'Risk register',
     N'سجل المخاطر',
     N'Identify, score and treat enterprise risks against SAMA CSF + NCA ECC-2.',
     N'تحديد المخاطر وتقييمها ومعالجتها وفق الإطار السيبراني لساما وضوابط ECC-2.',
     N'## Quick guide

The risk register is the source of truth for every enterprise risk: inherent score, residual score, owner, treatment plan and acceptance trail.

### Key steps
1. Open the **Risk Identification** wizard to capture a new risk.
2. Score it on the **5×5** likelihood/impact matrix.
3. Pick a treatment plan (mitigate / transfer / avoid / accept).
4. Link the risk to the controls that bring residual score down.

Use the **Bow-tie** view (`/risk/bowtie`) for top-tier risks that need a defensive map.',
     N'## دليل سريع

سجل المخاطر هو المرجع الموحّد لكل مخاطر المؤسسة: التقييم الأصلي، المتبقّي، المالك، خطة المعالجة، وسجل القبول.

### الخطوات الأساسية
1. افتح معالج «تحديد المخاطر» لتسجيل مخاطرة جديدة.
2. قيّمها بمصفوفة **الاحتمالية × الأثر (5×5)**.
3. اختر خطة المعالجة: التخفيف، النقل، التجنّب، أو القبول.
4. اربط المخاطرة بالضوابط التي تخفّض التقييم المتبقّي.

استخدم عرض **شجرة العنق** (`/risk/bowtie`) للمخاطر العليا التي تحتاج خريطة دفاع.',
     N'/risk', N'RISK',
     N'SAMA CSF Domain 2 + NCA ECC-2 1-3',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/CyberSecurity/Cyber%20Security%20Framework.pdf',
     N'WF-ERM-RISK-IDENT,WF-ERM-RISK-TREATMENT,WF-ERM-RISK-ACCEPTANCE',
     N'INFO', 10);

-- 3. Risk bow-tie ──────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-RISK-BOWTIE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-RISK-BOWTIE',
     N'Bow-tie analysis',
     N'تحليل شجرة العنق',
     N'Map threats, preventive controls, hazard, recovery controls and consequences on one canvas.',
     N'خريطة واحدة تربط التهديدات، الضوابط الوقائية، الحدث المركزي، ضوابط التعافي، والعواقب.',
     N'## Quick guide

Bow-tie is the recommended visual for a Tier-1 enterprise risk. The hazard sits in the middle; threats fan out to the left; consequences fan out to the right; controls sit between each side and the hazard.

### Key steps
1. Anchor the hazard (e.g. "Sustained outage of online-banking portal").
2. List 3-5 threats (the **left** side of the bow).
3. Place preventive controls on each threat path.
4. List 3-5 consequences (the **right** side).
5. Place recovery controls between hazard and consequence.

ISO 31010 §B.4.2 is the canonical reference for this technique.',
     N'## دليل سريع

تحليل شجرة العنق هو العرض المُوصى به للمخاطر العليا. يقع الحدث المركزي في المنتصف، والتهديدات على اليسار، والعواقب على اليمين، والضوابط بين كل جانب وبين الحدث.

### الخطوات الأساسية
1. ثبّت الحدث المركزي (مثال: «انقطاع طويل لخدمة الخدمات المصرفية عبر الإنترنت»).
2. سجّل 3-5 تهديدات على اليسار.
3. ضع الضوابط الوقائية على كل مسار تهديد.
4. سجّل 3-5 عواقب على اليمين.
5. ضع ضوابط التعافي بين الحدث والعاقبة.

المرجع: المواصفة ISO 31010 §B.4.2.',
     N'/risk/bowtie', N'RISK',
     N'ISO 31010 §B.4.2 + SAMA CSF 2.3.1',
     N'https://www.iso.org/standard/72140.html',
     N'WF-ERM-RISK-IDENT,WF-ERM-RISK-TREATMENT', N'INFO', 10);

-- 4. Crisis Room ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-CRISIS-ROOM' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-CRISIS-ROOM',
     N'Crisis Room',
     N'غرفة الأزمات',
     N'War-room for live incidents — timeline, decisions, comms and stakeholders in one place.',
     N'غرفة عمليات الحدث الحرج — الخطّ الزمني والقرارات والاتصالات وأصحاب المصلحة في مكان واحد.',
     N'## Quick guide

The Crisis Room turns an active incident into a structured working session. Every decision, message and timestamp is recorded against the crisis event so the after-action review (AAR) writes itself.

### Key steps
1. Press **Declare crisis** to open the room and stop the SLA clock on the underlying incident.
2. Use **Decisions** for any choice that needs board-level visibility.
3. Use **Comms** to dispatch pre-approved templates (staff / customer / regulator).
4. Close the room only after the SAMA / NCA notification clock has stopped.

The mobile companion (`/crisis/mobile`) carries the same room on a phone for executives on the move.',
     N'## دليل سريع

تحوّل «غرفة الأزمات» الحدث الجاري إلى جلسة عمل منظّمة. تُسجَّل كل القرارات والرسائل والأوقات مقابل الحدث، ليُكتَب تقرير ما بعد الإجراء تلقائياً.

### الخطوات الأساسية
1. اضغط **إعلان أزمة** لفتح الغرفة وإيقاف عدّاد اتفاقية الخدمة على الحادث الأصلي.
2. استخدم **القرارات** لأي خيار يحتاج اطّلاع المجلس.
3. استخدم **الاتصالات** لإرسال القوالب المعتمدة (موظفون / عملاء / منظِّم).
4. لا تُغلق الغرفة قبل إيقاف عدّادات إخطار ساما والهيئة الوطنية للأمن السيبراني.

الواجهة الهاتفية المرافقة (`/crisis/mobile`) تُحضر الغرفة نفسها للقياديين أثناء التنقّل.',
     N'/bcm/crisis-room', N'BCM',
     N'SAMA BCF Annex C + NCA ECC-2 2-13',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     N'WF-BCM-EXERCISE', N'WARNING', 10);

-- 5. Mobile Crisis ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-MOBILE-CRISIS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-MOBILE-CRISIS',
     N'Crisis Commander mobile',
     N'القائد الميداني للأزمات — الجوّال',
     N'Hand-held companion for the executive on call — same room, fewer controls.',
     N'الواجهة الجوّالة للقياديين خارج المكتب — الغرفة ذاتها بضوابط مختصرة.',
     N'## Quick guide

The mobile Crisis Commander is built for the CISO or COO answering a 3 a.m. call. Push notifications, one-tap acknowledgement, and a strict list of approved actions keep decisions on-record.

### Key steps
1. Tap **Acknowledge** to confirm you have the lead.
2. Use the pre-approved **decisions** menu — no free-form actions on mobile.
3. Approve dispatching a comm template by single tap.
4. Hand back to the desktop room when you''re at a terminal.',
     N'## دليل سريع

صُمّمت واجهة «القائد الميداني» للجوّال للحالات التي يُستدعى فيها رئيس أمن المعلومات ليلاً. إشعارات فورية، إقرار بضغطة واحدة، وقائمة محدّدة من الإجراءات المعتمدة لتبقى القرارات مُوثَّقة.

### الخطوات الأساسية
1. اضغط **إقرار** للتأكيد بأنك تتولّى القيادة.
2. استخدم قائمة **القرارات المعتمدة** فقط — لا إجراءات حرّة من الجوّال.
3. اعتمد إرسال قالب اتصال بضغطة واحدة.
4. سلّم القيادة لواجهة المكتب فور وصولك إليها.',
     N'/crisis/mobile', N'BCM',
     N'SAMA BCF Annex C',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     NULL, N'TIP', 10);

-- 6. Obligation register ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-OBLIGATION-REGISTER' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-OBLIGATION-REGISTER',
     N'Obligation register',
     N'سجل الالتزامات التنظيمية',
     N'Track every regulator obligation against a named owner and an attestation cycle.',
     N'متابعة كل التزام تنظيمي مع مالك مُسمّى ودورة تصديق دورية.',
     N'## Quick guide

The obligation register is the spine of the compliance posture. Each row maps a regulator article to one accountable owner, an attestation cadence (monthly / quarterly / annual), and the evidence pack that proves compliance.

### Key steps
1. Filter by authority (SAMA / NCA / SDAIA / CMA / ZATCA).
2. Open an obligation to see its current owner and last attestation date.
3. Trigger an **attestation campaign** if the next cycle is due.
4. Attach evidence to close the loop.

PDPL Article 4 (data-protection principles) and SAMA Compliance Framework §3 are the typical anchors.',
     N'## دليل سريع

يُعدّ سجل الالتزامات العمود الفقري لوضع الامتثال. يربط كل صفّ مادةً تنظيمية بمالكٍ مساءَل، ودورة تصديق (شهرية / ربعية / سنوية)، وحزمة الأدلة المُثبتة.

### الخطوات الأساسية
1. صفِّ حسب الجهة (ساما / الهيئة الوطنية للأمن السيبراني / سدايا / السوق المالية / هيئة الزكاة).
2. افتح التزاماً لرؤية مالكه الحالي وآخر تصديق له.
3. أطلق **حملة تصديق** إذا حلّت الدورة التالية.
4. أرفق الأدلة لإغلاق الحلقة.

المادة 4 من نظام حماية البيانات الشخصية وإطار الامتثال لساما §3 هما المرجعان الشائعان.',
     N'/compliance/obligations', N'COMPLIANCE',
     N'PDPL Art.4 + SAMA Compliance Framework §3',
     N'https://sdaia.gov.sa/ar/SDAIA/about/Files/PersonalDataProtectionEnglish.pdf',
     N'WF-COMPLIANCE-CONTROL-TEST', N'INFO', 10);

-- 7. CCM Dashboard ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-CCM-DASHBOARD' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-CCM-DASHBOARD',
     N'Continuous control monitoring',
     N'المراقبة المستمرة للضوابط',
     N'Automated tests run on a schedule and flag breaks the moment they happen.',
     N'اختبارات آلية مُجدوَلة ترصد إخفاق الضابط لحظة وقوعه.',
     N'## Quick guide

Continuous Control Monitoring (CCM) turns "point-in-time" control testing into "always-on" surveillance. Each test runs on its own cadence and writes a pass/fail row into the dashboard.

### Key steps
1. Filter by **status** (failing tests sit at the top).
2. Open a failing test to see the last 30 runs and the underlying control.
3. Click **Open CAPA** to start a corrective-action workflow.

SAMA CSF 2.6 (Monitoring & Continuous Improvement) is the anchor — manual annual testing alone no longer satisfies it for Tier-1 banks.',
     N'## دليل سريع

تحوّل المراقبة المستمرة الاختبار «اللحظي» للضوابط إلى مراقبة دائمة. يعمل كل اختبار بوتيرته الخاصة ويكتب صفّ نجاح/إخفاق في اللوحة.

### الخطوات الأساسية
1. صفِّ حسب **الحالة** (الاختبارات المُخفقة في الأعلى).
2. افتح اختباراً مُخفقاً لرؤية آخر 30 جولة والضابط المُرتبط.
3. اضغط **فتح إجراء تصحيحي** لبدء سير عمل المعالجة.

المرجع: الإطار السيبراني لساما 2.6 (المراقبة والتحسين المستمر) — لم يعد الاختبار السنوي اليدوي وحده كافياً للبنوك من الفئة الأولى.',
     N'/ccm/dashboard', N'CCM',
     N'SAMA CSF 2.6 (Continuous Monitoring)',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/CyberSecurity/Cyber%20Security%20Framework.pdf',
     N'WF-COMPLIANCE-CONTROL-TEST', N'INFO', 10);

-- 8. Vuln SLA ──────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-VULN-SLA' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-VULN-SLA',
     N'Risk-based vulnerability SLA',
     N'اتفاقية مستوى الخدمة للثغرات على أساس المخاطر',
     N'CVSS severity is only part of the answer — exposure and criticality close the rest.',
     N'تصنيف CVSS وحده لا يكفي — يُكمل الإطار درجة التعرّض ودرجة أهمية الأصل.',
     N'## Quick guide

A CRITICAL CVE on a public-facing portal is not the same urgency as the same CVE on an internal HR system. The SLA engine multiplies three drivers:

```
effective_sla_hours = base_sla × exposure_multiplier × criticality_multiplier
```

### Key steps
1. Open a finding to see the three drivers and the effective clock.
2. If the asset is mis-classified, fix it on the asset (the SLA recomputes).
3. Use **policies** to override the default for a business unit.

NCA ECC-2 2-12 requires "business-aligned" vulnerability remediation; the default policy is the SAMA-CSF 3.3.8 baseline.',
     N'## دليل سريع

ثغرة CRITICAL على بوّابة عامّة ليست بإلحاح الثغرة ذاتها على نظام موارد بشرية داخلي. يضرب محرّك اتفاقية الخدمة ثلاثة عوامل:

```
ساعات الاتفاقية الفعلية = الساعات الأساسية × مُعامِل التعرّض × مُعامِل أهمية الأصل
```

### الخطوات الأساسية
1. افتح أيّ ملاحظة لمشاهدة العوامل الثلاثة والعدّاد الفعلي.
2. إن كان تصنيف الأصل خاطئاً، صحّحه على الأصل (تُعاد الحوسبة تلقائياً).
3. استخدم **السياسات** لتعديل القاعدة الافتراضية لوحدة أعمال محدّدة.

تتطلّب الضوابط ECC-2 2-12 معالجة الثغرات بصورة متوافقة مع الأعمال؛ السياسة الافتراضية مبنيّة على الإطار السيبراني لساما 3.3.8.',
     N'/cyber/vuln-sla/*', N'CYBER',
     N'NCA ECC-2 2-12 + SAMA CSF 3.3.8',
     N'https://nca.gov.sa/regulations/ecc/ar',
     N'WF-NCA-VULNERABILITY-TRIAGE', N'WARNING', 10);

-- 9. Sub-processors ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-SUBPROCESSORS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-SUBPROCESSORS',
     N'Sub-processor register',
     N'سجل المعالجين الفرعيين',
     N'Track every entity that touches personal data on your behalf — PDPL Article 30.',
     N'تتبّع كل جهة تعالج البيانات الشخصية نيابةً عنك — المادة 30 من نظام حماية البيانات.',
     N'## Quick guide

PDPL Article 30 says the controller stays responsible for every processor and sub-processor downstream. The register is the surface you show the regulator on demand.

### Key steps
1. List every named processor + the data category each touches.
2. Attach the signed DPA (data-processing agreement) to each row.
3. Keep the **cross-border-transfer** field accurate — SDAIA cares about residency.
4. Re-attest annually as part of the privacy posture review.',
     N'## دليل سريع

تنصّ المادة 30 من نظام حماية البيانات الشخصية على أنّ المُتحكِّم يظلّ مسؤولاً عن كل معالج وعن كل معالج فرعي تابع له. السجل هو السطح الذي تعرضه على الجهة المنظِّمة عند الطلب.

### الخطوات الأساسية
1. سجّل كل معالج بالاسم مع فئة البيانات التي يلامسها.
2. أرفق اتفاقية معالجة البيانات الموقَّعة لكل صفّ.
3. حافظ على دقّة حقل **النقل العابر للحدود** — تهتمّ سدايا بالإقامة الجغرافية للبيانات.
4. أعِد التصديق سنوياً ضمن مراجعة وضع الخصوصية.',
     N'/privacy/subprocessors', N'PRIVACY',
     N'PDPL Art.30',
     N'https://sdaia.gov.sa/ar/SDAIA/about/Files/PersonalDataProtectionEnglish.pdf',
     N'WF-PDPL-CROSS-BORDER', N'INFO', 10);

-- 10. AI Models ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-AI-MODELS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-AI-MODELS',
     N'AI / ML model registry',
     N'سجل نماذج الذكاء الاصطناعي',
     N'Every production model needs an owner, a risk tier and a fairness review on file.',
     N'لكل نموذج إنتاجي مالكٌ، فئة مخاطر مُعتمدة، ومراجعة عدالة مُسجّلة.',
     N'## Quick guide

The model registry is the audit surface that SDAIA reviews when a bank deploys an AI use-case (credit scoring, fraud detection, KYC). Every model row carries the risk tier, the training data snapshot, the bias / fairness review and the named owner.

### Key steps
1. Record the model + intended use-case (e.g. "credit-decisioning, retail loans").
2. Pick a **risk tier** per the SDAIA AI Ethics 2023 taxonomy.
3. Attach the fairness review (demographic parity / equal opportunity).
4. Re-review on every major version bump.',
     N'## دليل سريع

سجل النماذج هو السطح الذي تراجعه سدايا عند نشر بنك حالة استخدام للذكاء الاصطناعي (تقدير ائتماني، رصد احتيال، اعرف عميلك). يحمل كل صفّ فئة المخاطر، صورة بيانات التدريب، مراجعة العدالة، والمالك المُسمّى.

### الخطوات الأساسية
1. سجّل النموذج مع حالة الاستخدام المقصودة (مثال: «اتّخاذ قرار الائتمان، قروض الأفراد»).
2. حدّد **فئة المخاطر** وفق تصنيف أخلاقيات الذكاء الاصطناعي من سدايا 2023.
3. أرفق مراجعة العدالة (تكافؤ الفرص / التكافؤ الديموغرافي).
4. أعِد المراجعة عند كل ترقية إصدار رئيسية.',
     N'/ai/models', N'AI_GOVERNANCE',
     N'SDAIA AI Ethics 2023',
     N'https://sdaia.gov.sa/ar/Sectors/Pages/AIEthics.aspx',
     NULL, N'INFO', 10);

-- 11. TCFD ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-TCFD' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-TCFD',
     N'TCFD climate scenarios',
     N'سيناريوهات المناخ وفق إطار TCFD',
     N'Run 1.5°C / 2°C / 4°C scenarios against your portfolio — IFRS S2 expects them.',
     N'احتسب سيناريوهات 1.5°م و2°م و4°م على المحفظة — وفق متطلّبات معيار IFRS S2.',
     N'## Quick guide

TCFD asks every financial institution to disclose how climate risk hits its portfolio under three scenarios. The engine maps each loan / position to a sector path and computes physical + transition risk.

### Key steps
1. Pick the scenario (orderly / disorderly / hot-house).
2. Review the portfolio impact by sector.
3. Export the disclosure pack for the annual report.

IFRS S2 §29 + SAMA Sustainable Finance Roadmap §4.2 are the Saudi anchors.',
     N'## دليل سريع

يطلب إطار TCFD من كل مؤسسة مالية أن تُفصح عن أثر المخاطر المناخية على محفظتها في ثلاثة سيناريوهات. يربط المحرّك كل قرض / مركز بقطاعه ويحسب الأثر المادي وأثر الانتقال.

### الخطوات الأساسية
1. اختر السيناريو (منظَّم / غير منظَّم / مرتفع الحرارة).
2. راجع الأثر على المحفظة بحسب القطاع.
3. صدّر حزمة الإفصاح للتقرير السنوي.

المرجعان السعوديان: المعيار IFRS S2 §29 وخارطة طريق التمويل المستدام لساما §4.2.',
     N'/esg/tcfd-scenarios', N'ESG',
     N'IFRS S2 + SAMA Sustainable Finance Roadmap',
     N'https://www.sama.gov.sa/ar-sa/News/Pages/news-12092023.aspx',
     NULL, N'INFO', 10);

-- 12. Financed emissions ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-FIN-EMISSIONS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-FIN-EMISSIONS',
     N'Scope-3 financed emissions',
     N'الانبعاثات المُموَّلة — النطاق الثالث',
     N'PCAF-methodology engine that turns lending books into scope-3 disclosures.',
     N'محرّك وفق منهجية PCAF يحوّل دفاتر الإقراض إلى إفصاحات النطاق الثالث.',
     N'## Quick guide

For a bank, the bulk of climate impact is in scope-3 category 15 — the financed emissions of borrowers and investees. PCAF gives the standard attribution math.

### Key steps
1. Upload (or sync) the loan portfolio with the borrower''s emission factor.
2. The engine applies the PCAF attribution share (loan ÷ borrower-EVIC).
3. Review by sector — heavy industry usually dominates.
4. Sign off and lock the period for the annual disclosure.',
     N'## دليل سريع

في البنوك يكون معظم الأثر المناخي ضمن الفئة 15 من النطاق الثالث: الانبعاثات المُموَّلة للعملاء المُقترضين والمستثَمر فيهم. تُقدّم منهجية PCAF حسابات النسبة المعيارية.

### الخطوات الأساسية
1. حمّل (أو زامِن) محفظة القروض مع معامل الانبعاثات لكل مُقترض.
2. يطبّق المحرّك حصّة الإسناد وفق PCAF (القرض ÷ EVIC للمُقترض).
3. راجع النتائج حسب القطاع — الصناعات الثقيلة عادةً ما تتصدّر.
4. اعتمد الفترة وأقفلها لأغراض الإفصاح السنوي.',
     N'/esg/financed-emissions', N'ESG',
     N'PCAF Global Standard + SAMA Sustainable Finance Roadmap',
     N'https://carbonaccountingfinancials.com',
     NULL, N'INFO', 10);

-- 13. Audit Committee ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-AUDIT-COMMITTEE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-AUDIT-COMMITTEE',
     N'Audit committee dashboard',
     N'لوحة لجنة المراجعة',
     N'Curated executive view — open findings, overdue CAPAs, regulator clocks.',
     N'عرض تنفيذي منسَّق — الملاحظات المفتوحة، الإجراءات التصحيحية المتأخّرة، عدّادات المنظّمين.',
     N'## Quick guide

The audit-committee dashboard is the single screen the chair brings to every quarterly meeting. It is curated, not exploratory — every tile is sized for a committee discussion.

### Key steps
1. Open the **snapshot** for the meeting date.
2. Review overdue CAPAs (the chair''s standing first agenda item).
3. Drill into any item directly from the dashboard.
4. Export the **board pack** for the minute book.

SAMA Corporate Governance §6 governs audit-committee composition and reporting cadence.',
     N'## دليل سريع

لوحة لجنة المراجعة هي الشاشة الوحيدة التي يستعرضها رئيس اللجنة في كل اجتماع ربعي. اللوحة منسَّقة وليست استكشافية — كل بطاقة بحجم يناسب نقاش اللجنة.

### الخطوات الأساسية
1. افتح **اللقطة** المعنيّة بتاريخ الاجتماع.
2. راجع الإجراءات التصحيحية المتأخّرة (بند الرئيس الأوّل).
3. تعمّق مباشرةً من اللوحة لأيّ بند.
4. صدّر **حزمة المجلس** لكتاب المحاضر.

تحكم لائحة حوكمة الشركات §6 الصادرة عن ساما تشكيلَ اللجنة ودورية تقاريرها.',
     N'/audit-committee/dashboard', N'AUDIT',
     N'SAMA Corporate Governance §6',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions',
     N'WF-GOVERNANCE-BOARD-PACK', N'INFO', 10);

-- 14. Attack surface ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-ATTACK-SURFACE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-ATTACK-SURFACE',
     N'Attack-surface management',
     N'إدارة سطح الهجوم',
     N'Every internet-facing asset belongs in the register — discovered or sanctioned.',
     N'كل أصل مُتاح للعموم يجب أن يكون في السجل — سواء اكتُشف لاحقاً أو اعتُمد مسبقاً.',
     N'## Quick guide

ASM is the bank''s external view of itself — the same view an attacker has. The register holds every public hostname, IP, certificate, API and bucket attributed to the institution.

### Key steps
1. Review newly **discovered** assets weekly and confirm ownership.
2. Move sanctioned ones to **approved**; reject or block shadow IT.
3. Track findings per asset (expired cert, exposed port, etc.).
4. Use the **risk score** to prioritise public-facing critical assets.

NCA OTCC and the SAMA CSF asset-inventory clauses both anchor this register.',
     N'## دليل سريع

تُمثّل إدارة سطح الهجوم رؤية البنك لنفسه من الخارج — الرؤية ذاتها التي يملكها المهاجم. يضمّ السجل كل اسم نطاق عام، عنوان IP، شهادة، واجهة API، وحاوية تخزين منسوبة للمؤسسة.

### الخطوات الأساسية
1. راجع الأصول **المُكتشَفة** أسبوعياً وأثبت ملكيّتها.
2. انقل المُعتمدة إلى **مُعتَمد**؛ ارفض أو احجب «تقنية الظل».
3. تابِع الملاحظات لكل أصل (شهادة منتهية، منفذ مكشوف، إلخ).
4. استخدم **درجة الخطورة** لترتيب الأصول العامة الحرجة.

يستند السجل إلى ضوابط الحوسبة التشغيلية لـ«الهيئة الوطنية» وبنود جرد الأصول في الإطار السيبراني لساما.',
     N'/cyber/attack-surface', N'CYBER',
     N'NCA OTCC + SAMA CSF 4.2.1',
     N'https://nca.gov.sa/regulations/otcc/ar',
     NULL, N'INFO', 10);

-- 15. Observer console ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-OBSERVER-CONSOLE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-OBSERVER-CONSOLE',
     N'Exercise observer console',
     N'وحدة مراقبة التمرين',
     N'Read-only view of a running exercise — auditor / CISO / regulator liaison.',
     N'عرض قراءة فقط لتمرين جارٍ — للمراجع أو رئيس أمن المعلومات أو ضابط ارتباط المنظِّم.',
     N'## Quick guide

The observer console is intentionally read-only. The auditor sees every injection, every decision and every comm in real time but cannot influence the exercise. This separation is what makes the exercise evidence audit-grade.

### Key steps
1. Watch the live timeline (decisions and injections roll in chronologically).
2. Take **observer notes** — they sync into the AAR automatically.
3. Tag a moment as a **finding** or a **strength**.
4. The console stays available for 30 days after the exercise closes for late notes.',
     N'## دليل سريع

وحدة المراقبة وُضعت عمداً للقراءة فقط. يرى المراجع كل مدخل وكل قرار وكل اتصال لحظةً بلحظة، لكنه لا يؤثّر على مجرى التمرين. هذا الفصل هو ما يجعل أدلّة التمرين صالحةً للتدقيق.

### الخطوات الأساسية
1. راقب الخطّ الزمني المباشر (تظهر القرارات والمداخل بترتيب زمني).
2. سجّل **ملاحظات المراقِب** — تُدمج تلقائياً في تقرير ما بعد الإجراء.
3. وسّم لحظةً بوصفها **ملاحظة** أو **نقطة قوّة**.
4. تبقى الوحدة متاحة 30 يوماً بعد إغلاق التمرين لإضافة ملاحظات متأخّرة.',
     N'/bcm/exercises/run/*/observe', N'BCM',
     N'SAMA BCF Annex C + ISO 22301 §8.5',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     N'WF-BCM-EXERCISE', N'INFO', 10);

-- 16. Exercise library ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-EXERCISE-LIBRARY' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-EXERCISE-LIBRARY',
     N'Exercise scenario library',
     N'مكتبة سيناريوهات التمارين',
     N'Pre-curated tabletop and live-injection scenarios mapped to SAMA BCF Annex C.',
     N'سيناريوهات طاولة وحقن مباشر مُجهَّزة ومربوطة بملحق ج من إطار ساما لاستمرارية الأعمال.',
     N'## Quick guide

The library carries ready-to-run scenarios for the four SAMA BCF Annex C exercise types: walkthrough, tabletop, functional, full-scale. Each scenario has a facilitator script, expected injects and an evaluation rubric.

### Key steps
1. Pick a scenario by exercise type + business service.
2. Schedule a run and assign facilitator / participants / observers.
3. The facilitator console drives the live injects.
4. The AAR pulls in scenario, decisions and observer notes automatically.',
     N'## دليل سريع

تضمّ المكتبة سيناريوهات جاهزة للتشغيل وفق الأنواع الأربعة في ملحق ج من إطار ساما لاستمرارية الأعمال: استعراض، تمرين طاولة، تمرين وظيفي، تمرين شامل. لكل سيناريو نصّ للميسّر، مداخل متوقّعة، ومسطرة تقييم.

### الخطوات الأساسية
1. اختر السيناريو حسب نوع التمرين والخدمة الحرجة.
2. جدوِل التشغيل وعيّن الميسّر والمشاركين والمراقبين.
3. تقود وحدة الميسّر الحقن المباشر.
4. يُولّد تقرير ما بعد الإجراء من السيناريو والقرارات وملاحظات المراقِب تلقائياً.',
     N'/bcm/scenarios', N'BCM',
     N'SAMA BCF Annex C',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     N'WF-BCM-EXERCISE', N'INFO', 10);

-- 17. Vendor BCP campaigns ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-VENDOR-BCP' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-VENDOR-BCP',
     N'Vendor BCP attestation campaigns',
     N'حملات تصديق استمرارية الأعمال للموردين',
     N'Annual sweep — every critical vendor attests its BCP is current and tested.',
     N'حملة سنوية — يُقرّ كل مورد حرج بحداثة خطته لاستمرارية الأعمال واختبارها.',
     N'## Quick guide

The campaign sends every critical vendor a portal link to attest its BCP. Their answers feed straight into the resilience score and into the SAMA outsourcing return.

### Key steps
1. Pick the vendor population by **criticality**.
2. Configure the deadline and reminder cadence (workday-aware).
3. The vendor portal handles the submission — no platform account needed.
4. Review responses, escalate misses, close the campaign.',
     N'## دليل سريع

ترسل الحملة لكل مورد حرج رابط بوّابة لتصديق خطّته لاستمرارية الأعمال. تُغذّي إجاباته مباشرةً مؤشّر الجاهزية والإفصاحات الخاصّة بالتعهيد لدى ساما.

### الخطوات الأساسية
1. حدّد قائمة الموردين حسب **الأهمية**.
2. اضبط الموعد النهائي ووتيرة التذكير (مع الانتباه لأيام العمل السعودية).
3. تتولّى بوّابة المورد تقديم الإقرار — دون الحاجة لحساب على المنصة.
4. راجع الإجابات، صعّد التأخّرات، ثم أغلق الحملة.',
     N'/tprm/bcp-campaigns', N'TPRM',
     N'SAMA BCF §3.5 + SAMA Outsourcing Rules',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions',
     N'WF-SCM-SUPPLIER-ONBOARDING', N'INFO', 10);

-- 18. MTPD inheritance ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-MTPD-INHERITANCE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-MTPD-INHERITANCE',
     N'MTPD inheritance check',
     N'فحص توارث الحدّ الأقصى للانقطاع المسموح',
     N'A vendor''s MTPD cannot be longer than the service it supports — the check enforces this.',
     N'لا يجوز أن يتجاوز الحدّ الأقصى للانقطاع لدى المورد قيمة الخدمة التي يدعمها — هذا الفحص يفرض ذلك.',
     N'## Quick guide

If your online-banking service has an MTPD of 4 hours, a vendor in its dependency chain cannot promise 6 hours. The inheritance check catches that mismatch automatically.

### Key steps
1. Open the inheritance view to see every business service vs its vendor chain.
2. Red rows are violations — open them to see the offending vendor.
3. Either reduce the vendor''s MTPD (renegotiate / re-architect) or re-classify the service.

SAMA BCF §3.4 and ISO 22301 §8.2 require consistent recovery objectives across the chain.',
     N'## دليل سريع

إذا كان الحدّ الأقصى للانقطاع المسموح لخدمة الخدمات المصرفية الإلكترونية لديك 4 ساعات، فلا يجوز لمورد ضمن سلسلتها أن يعد بـ 6 ساعات. يكتشف فحص التوارث هذا التضارب تلقائياً.

### الخطوات الأساسية
1. افتح عرض التوارث لرؤية كل خدمة أعمال وسلسلة مورديها.
2. الصفوف الحمراء مخالفات — افتحها لرؤية المورد المُسبّب.
3. إمّا تقليل MTPD لدى المورد (إعادة تفاوض / إعادة معمارية)، أو إعادة تصنيف الخدمة.

تتطلّب الفقرة §3.4 من إطار ساما لاستمرارية الأعمال وكذلك المواصفة ISO 22301 §8.2 اتّساق أهداف التعافي عبر السلسلة.',
     N'/bcm/mtpd-inheritance', N'BCM',
     N'SAMA BCF §3.4 + ISO 22301 §8.2',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     NULL, N'WARNING', 10);

-- 19. Crisis comms ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-CRISIS-COMMS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-CRISIS-COMMS',
     N'Crisis communications templates',
     N'قوالب الاتصالات في الأزمات',
     N'Pre-approved bilingual templates for staff / customer / regulator / media.',
     N'قوالب ثنائية اللغة مُعتمدة مسبقاً للموظفين والعملاء والمنظِّم والإعلام.',
     N'## Quick guide

Mid-crisis is the wrong time to write a customer notification from scratch. Each template carries a target audience, a legal / compliance pre-approval, and bilingual EN/AR copy.

### Key steps
1. Pick a template by audience.
2. Fill in the variables (incident reference, ETA to resolve).
3. Send through the Crisis Room — the dispatch is logged for the AAR.

Every dispatch is captured for the SAMA / NCA notification clock.',
     N'## دليل سريع

ليس الوقت الذي يحدث فيه حدث حرج وقتاً ملائماً لصياغة رسائل عملاء من الصفر. كل قالب يحمل جمهوراً مستهدفاً، وموافقة قانونية / امتثال مسبقة، ونصّاً ثنائي اللغة.

### الخطوات الأساسية
1. اختر القالب حسب الجمهور.
2. عبّئ المتغيّرات (مرجع الحدث، الوقت المتوقّع للحل).
3. أرسل عبر «غرفة الأزمات» — تُسجَّل عملية الإرسال في تقرير ما بعد الإجراء.

تُلتقط كل عملية إرسال لخدمة عدّاد إخطار ساما والهيئة الوطنية للأمن السيبراني.',
     N'/bcm/comms/templates', N'BCM',
     N'SAMA BCF Annex C',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/BCM',
     NULL, N'TIP', 10);

-- 20. Three lines of defense ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-3LOD' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-3LOD',
     N'Three lines of defense',
     N'خطوط الدفاع الثلاثة',
     N'Operational / risk-and-compliance / internal-audit — the canonical accountability map.',
     N'العمليات / المخاطر والامتثال / المراجعة الداخلية — خريطة المساءلة الكلاسيكية.',
     N'## Quick guide

The IIA 2020 model formalises who owns each control: the business owns it (line 1), risk and compliance challenge it (line 2), and internal audit assures it (line 3). The screen lays your control library across the three lines so gaps are visible.

### Key steps
1. Look for controls without a line-1 owner (red).
2. Look for controls assured at line 3 but not challenged at line 2.
3. Use the **gap report** as the audit-committee handout.',
     N'## دليل سريع

يُرسّخ نموذج معهد المراجعين الداخليين IIA 2020 ملكية الضوابط: تملك الأعمال الضابط (الخطّ الأول)، وتتحدّاه المخاطر والامتثال (الخطّ الثاني)، وتُؤكّده المراجعة الداخلية (الخطّ الثالث). تعرض الشاشة مكتبة ضوابطك موزّعةً على الخطوط الثلاثة بحيث تظهر الفجوات.

### الخطوات الأساسية
1. ابحث عن الضوابط دون مالك في الخطّ الأول (الحمراء).
2. ابحث عن الضوابط المُؤكّدة في الخطّ الثالث دون أن يتحدّاها الثاني.
3. استخدم **تقرير الفجوات** نشرةً للجنة المراجعة.',
     N'/governance/three-lines-of-defense', N'GENERAL',
     N'IIA 2020 + SAMA CSF 1.1.1',
     N'https://www.theiia.org/en/content/articles/global-knowledge-brief/2020/july/the-iias-three-lines-model/',
     NULL, N'INFO', 10);

-- 21. Incidents ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-INCIDENTS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-INCIDENTS',
     N'Incident register',
     N'سجل الحوادث',
     N'NIST SP 800-61 lifecycle — detect, classify, contain, eradicate, recover, lessons.',
     N'دورة الحادث وفق NIST SP 800-61 — كشف، تصنيف، احتواء، استئصال، تعافٍ، دروس.',
     N'## Quick guide

The incident register tracks every cyber incident through the NIST six-phase lifecycle. Every transition stamps the regulator clock if it applies (SAMA 4-hour rule, NCA notification windows).

### Key steps
1. Triage open incidents at the top of the list (worst severity first).
2. Move through the phases — the platform enforces phase order.
3. Attach evidence and the IR playbook used.
4. The AAR is generated on closure.',
     N'## دليل سريع

يتتبّع سجلّ الحوادث كلّ حادث سيبراني عبر دورة الحياة السداسية في معيار NIST. تُسجّل كل مرحلة عدّاد المنظِّم متى انطبق (قاعدة الـ4 ساعات لساما ونوافذ إخطار «الهيئة الوطنية للأمن السيبراني»).

### الخطوات الأساسية
1. صنّف الحوادث المفتوحة من أعلى القائمة (الأشدّ خطورةً أوّلاً).
2. انتقل بين المراحل — تفرض المنصة ترتيبها.
3. أرفق الأدلّة وكتيّب الاستجابة المستخدم.
4. يُولَّد تقرير ما بعد الإجراء عند الإغلاق.',
     N'/incidents', N'CYBER',
     N'NIST SP 800-61 + NCA ECC-2 5-1',
     N'https://csrc.nist.gov/publications/detail/sp/800-61/rev-2/final',
     N'WF-NCA-CYBER-INCIDENT,WF-INCIDENT-RCA', N'WARNING', 10);

-- 22. Compliance posture ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-COMPLIANCE-POSTURE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-COMPLIANCE-POSTURE',
     N'Compliance posture',
     N'وضع الامتثال',
     N'Coverage map: every framework × every control × the evidence behind it.',
     N'خريطة التغطية: كل إطار × كل ضابط × الأدلّة المُسانِدة.',
     N'## Quick guide

The posture view shows you which framework controls are covered today and which are not. Sort by framework (SAMA CSF / NCA ECC-2 / PDPL / ISO 27001) and the heat-map tells you where to look first.

### Key steps
1. Pick the framework to colour the heat-map.
2. Click a red cell to see the unmapped control.
3. Use the unified control library to map your existing control to it (don''t create a new one).',
     N'## دليل سريع

يُظهر «وضع الامتثال» الضوابط المُغطّاة اليوم وتلك التي لا تُغطّى. صنّف حسب الإطار (الإطار السيبراني لساما / ECC-2 / نظام حماية البيانات / ISO 27001) لتظهر الخريطة الحرارية أين تبدأ.

### الخطوات الأساسية
1. اختر الإطار لتلوين الخريطة الحرارية.
2. اضغط خلية حمراء لرؤية الضابط غير المُغطّى.
3. استخدم مكتبة الضوابط الموحّدة لربط ضابطك القائم به (لا تُنشئ ضابطاً جديداً).',
     N'/compliance/posture', N'COMPLIANCE',
     N'SAMA CSF + NCA ECC-2 + PDPL',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions/CyberSecurity/Cyber%20Security%20Framework.pdf',
     NULL, N'INFO', 10);

-- 23. Control library ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-CONTROL-LIBRARY' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-CONTROL-LIBRARY',
     N'Unified control library',
     N'مكتبة الضوابط الموحّدة',
     N'One control row, many framework anchors — test once, satisfy many.',
     N'صفّ ضابط واحد، إسنادات لعدّة أطر — اختبر مرّة، أوفِ بمتطلّبات متعدّدة.',
     N'## Quick guide

The library lets you test a control once and credit it against every framework that requires it. A single SOC-monitoring control might satisfy SAMA CSF 3.4.1, NCA ECC-2 5-1, and ISO 27001 A.12.4 simultaneously.

### Key steps
1. Search for an existing control before creating a new one.
2. Open a control to see its **framework crosswalk** — the inheritance map.
3. Run the **CCM test** linked to the control — that''s your continuous assurance.',
     N'## دليل سريع

تتيح المكتبة اختبار الضابط مرّةً واحدة واحتسابه ضمن كلّ الأطر التي تتطلّبه. قد يُلبّي ضابط مراقبة عمليات أمنية واحد متطلّبات الإطار السيبراني لساما 3.4.1 وECC-2 5-1 وISO 27001 A.12.4 في آنٍ معاً.

### الخطوات الأساسية
1. ابحث عن ضابط قائم قبل إنشاء ضابط جديد.
2. افتح الضابط لمعرفة **مصفوفة الأطر** المُسنَدة — خريطة التوارث.
3. شغّل **اختبار المراقبة المستمرة** المُرتبط بالضابط — هذا هو ضمانك المستمر.',
     N'/controls/library', N'COMPLIANCE',
     N'SAMA CSF + NCA ECC-2 + ISO 27001 + NIST CSF',
     N'https://nca.gov.sa/regulations/ecc/ar',
     N'WF-COMPLIANCE-CONTROL-TEST', N'INFO', 10);

-- 24. Regulatory library ──────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-REG-LIBRARY' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-REG-LIBRARY',
     N'Saudi regulator library',
     N'مكتبة المنظّمين السعوديين',
     N'Source-of-truth catalog: SAMA, NCA, SDAIA, CMA, ZATCA, MOH, Nazaha.',
     N'الكتالوج المرجعي: ساما، الهيئة الوطنية للأمن السيبراني، سدايا، السوق المالية، الزكاة، الصحة، نزاهة.',
     N'## Quick guide

The library is read-only — it''s the curated source of the Saudi regulator universe the platform reasons against. Use it to research an article before linking your control to it.

### Key steps
1. Filter by **authority**.
2. Open any document for the bilingual full text plus the platform''s control mapping.
3. From any article, jump to the controls that satisfy it.',
     N'## دليل سريع

المكتبة للقراءة فقط — هي المرجع المنسَّق لعالم المنظّمين السعوديين الذي تستند إليه المنصّة. استخدمها للبحث في مادة قبل ربط ضابطك بها.

### الخطوات الأساسية
1. صفِّ حسب **الجهة**.
2. افتح أيّ وثيقة لقراءة النص الكامل ثنائي اللغة مع تخطيط الضوابط في المنصة.
3. من أيّ مادة، انتقل مباشرةً إلى الضوابط التي تُلبّيها.',
     N'/regulatory/library', N'COMPLIANCE',
     N'SAMA, NCA, SDAIA, CMA, ZATCA',
     N'https://laws.boe.gov.sa',
     NULL, N'INFO', 10);

-- 25. Calendar ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-CALENDAR' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-CALENDAR',
     N'Compliance calendar',
     N'تقويم الامتثال',
     N'Workday-aware reminders — Saudi weekend (Fri-Sat) and public holidays are skipped.',
     N'تذكيرات تراعي أيّام العمل — تُخطّى الجمعة والسبت والعُطل الرسمية السعودية.',
     N'## Quick guide

Every periodic obligation (KYC re-attestation, control test, audit follow-up, regulator submission) writes a row here. The reminder cadence respects the Saudi calendar, including Eid Al-Fitr and Eid Al-Adha clusters.

### Key steps
1. Open the calendar to see the next 30 days at a glance.
2. Click an event to open the underlying obligation.
3. Snooze with care — moves are audited.',
     N'## دليل سريع

يكتب كلّ التزام دوري (إعادة تصديق «اعرف عميلك»، اختبار ضابط، متابعة تدقيق، إفصاح تنظيمي) صفّاً هنا. تحترم وتيرة التذكيرات التقويم السعودي بما فيه عيدا الفطر والأضحى.

### الخطوات الأساسية
1. افتح التقويم لرؤية الـ30 يوماً القادمة دفعةً واحدة.
2. اضغط على الحدث لفتح الالتزام المعني.
3. استعمل التأجيل بحرص — كل تحريك يُسجَّل في سجل التدقيق.',
     N'/calendar', N'GENERAL',
     N'SAMA Compliance Framework §4',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions',
     NULL, N'TIP', 10);

-- 26. Workflows hub ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-WORKFLOWS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-WORKFLOWS',
     N'Workflows hub',
     N'مركز سير العمل',
     N'Every multi-step procedure — risk identification, DSR fulfilment, incident triage — is a wizard.',
     N'كل إجراء متعدّد الخطوات — تحديد المخاطر، تنفيذ طلب صاحب البيانات، فرز الحادث — هو معالج موجَّه.',
     N'## Quick guide

A wizard is a saved, repeatable sequence. The hub lists every wizard by module — risk, privacy, cyber, compliance, governance.

### Key steps
1. Pick a wizard by the work you''re doing (e.g. "Risk Acceptance").
2. The wizard collects every required field and stamps every step.
3. On submit, it lands in the **Submissions** register.
4. Approvers and watchers are notified automatically.',
     N'## دليل سريع

المعالج هو تسلسل محفوظ قابل لإعادة الاستخدام. يعرض المركز كل المعالجات مصنّفةً حسب الوحدة — المخاطر، الخصوصية، السيبراني، الامتثال، الحوكمة.

### الخطوات الأساسية
1. اختر المعالج بحسب العمل المطلوب (مثال: «قبول مخاطرة»).
2. يجمع المعالج كل الحقول المطلوبة ويُسجّل كل خطوة.
3. عند الإرسال يُسجَّل في **سجل المُقدَّمات**.
4. يُبلَّغ المعتمدون والمراقبون تلقائياً.',
     N'/workflows', N'GENERAL',
     N'SAMA Operational Risk Framework §3',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions',
     NULL, N'INFO', 10);

-- 27. Workflow submissions ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-WORKFLOW-SUBMISSIONS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-WORKFLOW-SUBMISSIONS',
     N'Workflow submissions',
     N'سجل المُقدَّمات',
     N'Single register of every wizard outcome — pending / approved / rejected / withdrawn.',
     N'سجل واحد لكل نتائج المعالجات — قيد الانتظار / مُعتَمد / مرفوض / مسحوب.',
     N'## Quick guide

The submissions register is the auditor''s starting point for any procedural review: it shows who submitted, who approved, what the wizard captured, and when each step happened.

### Key steps
1. Filter by **module** + **status** to narrow down.
2. Open a submission to see its full step-by-step history.
3. Export for the auditor on demand.',
     N'## دليل سريع

سجل المُقدَّمات هو نقطة بداية المراجع في أيّ مراجعة إجرائية: يُظهر من قدّم، من اعتمد، ماذا التقط المعالج، ومتى وقعت كل خطوة.

### الخطوات الأساسية
1. صفِّ حسب **الوحدة** + **الحالة**.
2. افتح المُقدَّم لرؤية تاريخه الكامل خطوة بخطوة.
3. صدِّر للمراجع عند الطلب.',
     N'/workflows/submissions', N'GENERAL',
     N'SAMA Compliance Framework §3',
     N'https://www.sama.gov.sa/ar-sa/RulesInstructions',
     NULL, N'INFO', 10);

-- 28. Evidence ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-EVIDENCE' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-EVIDENCE',
     N'Evidence vault',
     N'خزانة الأدلّة',
     N'Tamper-evident, retention-aware storage for every artefact the auditor will want.',
     N'تخزين مقاوم للعبث، يحترم سياسات الاحتفاظ، لكل دليل قد يطلبه المراجع.',
     N'## Quick guide

Every artefact (screenshot, signed PDF, log export, attestation form) lives here with a hash, a retention class and a chain-of-custody trail. The vault refuses to delete a row that''s still inside its retention window.

### Key steps
1. Upload directly or attach from any module ("Attach evidence").
2. Set the **retention class** — the vault auto-purges when the clock expires.
3. Pull an **evidence chain** for any control or finding when asked.',
     N'## دليل سريع

يقيم كل دليل (صورة شاشة، PDF موقَّع، تصدير سجلّ، استمارة تصديق) هنا مع بصمة وفئة احتفاظ ومسار حِفظ. ترفض الخزانة حذف صفّ لا تزال فترة احتفاظه قائمة.

### الخطوات الأساسية
1. ارفع مباشرةً أو أرفق من أيّ وحدة («إرفاق دليل»).
2. حدّد **فئة الاحتفاظ** — تُطهَّر الخزانة تلقائياً عند انتهاء المدة.
3. استخرج **سلسلة الأدلة** لأيّ ضابط أو ملاحظة عند الطلب.',
     N'/evidence', N'GENERAL',
     N'SAMA Compliance Framework §5 + NCA ECC-2 1-4',
     N'https://nca.gov.sa/regulations/ecc/ar',
     NULL, N'INFO', 10);

-- 29. PDPL DSR ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-PDPL-DSR' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-PDPL-DSR',
     N'Data subject requests (DSR)',
     N'طلبات أصحاب البيانات الشخصية',
     N'PDPL Article 4 — every DSR has a 30-day SLA from receipt.',
     N'المادة 4 من نظام حماية البيانات — كل طلب من صاحب بيانات يخضع لمدّة 30 يوماً من تاريخ الاستلام.',
     N'## Quick guide

Saudi PDPL gives data subjects access, rectification, deletion and portability rights. Every request must be fulfilled within 30 days; the clock starts when the request reaches the controller.

### Key steps
1. Open a DSR to confirm identity and request type.
2. Use the wizard to gather the data from every source system.
3. Stamp the closure with the response (granted / partial / refused with grounds).
4. The vault keeps the response for 5 years.',
     N'## دليل سريع

يمنح نظام حماية البيانات الشخصية أصحاب البيانات حقّ الاطّلاع، التصحيح، الحذف، والنقل. يجب الاستجابة لكل طلب خلال 30 يوماً، وتبدأ المدة عند وصول الطلب إلى المُتحكِّم.

### الخطوات الأساسية
1. افتح الطلب للتأكد من هوية مُقدّمه ونوع الطلب.
2. استخدم المعالج لجمع البيانات من كل الأنظمة المصدرية.
3. سجّل الإغلاق بالاستجابة (مُلبّى / جزئي / مرفوض مع التبرير).
4. تحتفظ الخزانة بالاستجابة لمدة 5 سنوات.',
     N'/privacy', N'PRIVACY',
     N'PDPL Art.4 + Art.5',
     N'https://sdaia.gov.sa/ar/SDAIA/about/Files/PersonalDataProtectionEnglish.pdf',
     N'WF-PDPL-DSR-FULFILMENT', N'WARNING', 10);

-- 30. PDPL RoPA ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM platform.help_topic WHERE topic_code = N'HELP-PDPL-RECORDS' AND is_deleted = 0)
INSERT INTO platform.help_topic
    (organisation_id, topic_code, title_en, title_ar, summary_en, summary_ar,
     body_en, body_ar, route_pattern, module, regulatory_anchor, regulator_link_url,
     related_workflow_codes, severity, display_order)
VALUES
    (NULL, N'HELP-PDPL-RECORDS',
     N'Records of processing (RoPA)',
     N'سجل عمليات المعالجة',
     N'PDPL Article 20 — full inventory of every processing activity with purpose, basis, retention.',
     N'المادة 20 من نظام حماية البيانات — جرد كامل لكل نشاط معالجة مع الغرض والأساس ومدة الاحتفاظ.',
     N'## Quick guide

RoPA is the spine of Saudi PDPL compliance. Every processing activity (KYC, payroll, marketing, fraud monitoring) needs one row capturing the purpose, the legal basis, the data categories, the retention, and the recipients.

### Key steps
1. Use the wizard to add a new activity — it asks every Article-20 field.
2. Re-attest annually as part of the privacy posture review.
3. Export the full RoPA for SDAIA on demand.',
     N'## دليل سريع

يُمثّل سجل المعالجة العمود الفقري لامتثال نظام حماية البيانات. كل نشاط معالجة (اعرف عميلك، الرواتب، التسويق، رصد الاحتيال) يحتاج صفّاً يلتقط الغرض والأساس النظامي وفئات البيانات ومدة الاحتفاظ والمستلمين.

### الخطوات الأساسية
1. استخدم المعالج لإضافة نشاط جديد — يطلب كل حقول المادة 20.
2. أعِد التصديق سنوياً ضمن مراجعة وضع الخصوصية.
3. صدّر السجل كاملاً لسدايا عند الطلب.',
     N'/privacy/records', N'PRIVACY',
     N'PDPL Art.20',
     N'https://sdaia.gov.sa/ar/SDAIA/about/Files/PersonalDataProtectionEnglish.pdf',
     N'WF-PDPL-ROPA', N'INFO', 10);

GO

PRINT N'V191 — Contextual Help: 1 table + 30 platform-global help topics seeded.';
GO

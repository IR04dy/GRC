-- V67 — NDI Framework Library (slice 2)
--
-- Configurable catalog of NDI framework items. Each row is a single
-- requirement / question across the three NDI components:
--   • Compliance
--   • Maturity
--   • Operational Excellence
--
-- The seed below is INTENTIONALLY flagged is_placeholder=1 on every
-- row. The spec is explicit about this — without an authoritative
-- SDAIA control-library import, every framework item must carry the
-- bilingual placeholder marker so dashboards / reports can be honest
-- about what's official and what's interim.
--
-- A future official-import endpoint (slice 2 follow-up) will flip
-- is_official_import=1 and is_placeholder=0 on a per-item basis.
--
-- Status machine on the row itself:
--   DRAFT → ACTIVE → RETIRED   (single forward path; RETIRED is terminal)
--
-- Cross-mapping columns (related_ndmo_control / related_pdpl_requirement
-- / related_cyber_control / related_iso_mapping) store free-text
-- references — no FK so the library can land before the linked modules
-- catch up.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'framework_item' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.framework_item (
        id                         BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id            BIGINT        NOT NULL,
        external_id                NVARCHAR(64)  NULL,

        framework_version          NVARCHAR(20)  NOT NULL DEFAULT N'v0.1-placeholder',
        component                  NVARCHAR(30)  NOT NULL,  -- COMPLIANCE | MATURITY | OPERATIONAL_EXCELLENCE
        domain                     NVARCHAR(80)  NOT NULL,
        subdomain                  NVARCHAR(80)  NULL,

        requirement_code           NVARCHAR(40)  NOT NULL,  -- e.g. NDI-DG-001
        title_en                   NVARCHAR(255) NOT NULL,
        title_ar                   NVARCHAR(255) NULL,
        description_en             NVARCHAR(MAX) NULL,
        description_ar             NVARCHAR(MAX) NULL,
        question_en                NVARCHAR(MAX) NULL,
        question_ar                NVARCHAR(MAX) NULL,
        evidence_guidance_en       NVARCHAR(MAX) NULL,
        evidence_guidance_ar       NVARCHAR(MAX) NULL,

        scoring_method             NVARCHAR(40)  NULL,      -- LEVEL_0_5 | BINARY | PERCENT | RUBRIC
        weight                     DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
        priority                   NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',  -- CRITICAL | HIGH | MEDIUM | LOW
        mandatory                  BIT           NOT NULL DEFAULT 0,
        owner_role                 NVARCHAR(80)  NULL,
        review_frequency           NVARCHAR(30)  NULL,      -- ANNUAL | SEMI_ANNUAL | QUARTERLY | MONTHLY

        related_ndmo_control       NVARCHAR(120) NULL,
        related_pdpl_requirement   NVARCHAR(120) NULL,
        related_cyber_control      NVARCHAR(120) NULL,
        related_iso_mapping        NVARCHAR(120) NULL,

        -- Provenance markers. Spec REQUIRES both flags so the UI can
        -- prominently show which items are official-imported vs interim.
        is_official_import         BIT           NOT NULL DEFAULT 0,
        is_placeholder             BIT           NOT NULL DEFAULT 1,

        status                     NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',  -- DRAFT | ACTIVE | RETIRED
        effective_date             DATE          NULL,

        notes                      NVARCHAR(MAX) NULL,

        is_deleted                 BIT           NOT NULL DEFAULT 0,
        created_at                 DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                 NVARCHAR(100) NULL,
        updated_at                 DATETIME2     NULL,
        updated_by                 NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_fw_component CHECK (component IN
            (N'COMPLIANCE', N'MATURITY', N'OPERATIONAL_EXCELLENCE')),
        CONSTRAINT ck_ndi_fw_priority  CHECK (priority IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT ck_ndi_fw_status    CHECK (status IN (N'DRAFT', N'ACTIVE', N'RETIRED')),
        CONSTRAINT ck_ndi_fw_weight    CHECK (weight >= 0 AND weight <= 10)
    );

    CREATE UNIQUE INDEX uq_ndi_fw_org_code
        ON ndi.framework_item(organisation_id, framework_version, requirement_code)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ndi_fw_org_component ON ndi.framework_item(organisation_id, component);
    CREATE INDEX ix_ndi_fw_org_domain    ON ndi.framework_item(organisation_id, domain);
    CREATE INDEX ix_ndi_fw_org_placeholder
        ON ndi.framework_item(organisation_id, is_placeholder, is_official_import);
END
GO

-- ─── Seed — 20 placeholder framework items across 3 components ──────
-- Every row has is_placeholder=1 + is_official_import=0 as required
-- by the spec until the real SDAIA library is imported.

IF NOT EXISTS (SELECT 1 FROM ndi.framework_item WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.framework_item (
        organisation_id, framework_version, component, domain, subdomain,
        requirement_code, title_en, title_ar,
        description_en, description_ar,
        question_en, question_ar,
        evidence_guidance_en, evidence_guidance_ar,
        scoring_method, weight, priority, mandatory, owner_role, review_frequency,
        related_ndmo_control, related_pdpl_requirement, related_cyber_control, related_iso_mapping,
        is_official_import, is_placeholder, status, effective_date
    )
    VALUES
    -- ── MATURITY component — 8 items across domains ────────────────
    (1, N'v0.1-placeholder', N'MATURITY', N'Data Governance', N'Governance Framework',
     N'NDI-DG-001', N'Data governance framework established',
     N'إطار حوكمة البيانات معتمد',
     N'Documented data governance framework approved by executive management with clear scope and authority.',
     N'إطار موثق لحوكمة البيانات مع نطاق ومرجعية معتمدة من الإدارة التنفيذية.',
     N'Is there an executive-approved data governance framework with documented scope and authority?',
     N'هل يوجد إطار حوكمة بيانات معتمد من الإدارة التنفيذية مع توثيق للنطاق والمرجعية؟',
     N'Executive-approved framework document; approval minutes; published policy reference.',
     N'وثيقة الإطار المعتمدة من الإدارة التنفيذية؛ محضر الاعتماد؛ مرجعية السياسة المنشورة.',
     N'LEVEL_0_5', 2.0, N'CRITICAL', 1, N'Data Office Manager', N'ANNUAL',
     N'NDMO-DG-FRAMEWORK', NULL, NULL, N'ISO 38505',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Data Quality', N'Quality Rules',
     N'NDI-DQ-001', N'Data quality rules defined for critical datasets',
     N'قواعد جودة البيانات معرّفة للبيانات الحرجة',
     N'Critical data assets have documented quality rules (completeness / accuracy / timeliness / uniqueness).',
     N'الأصول البيانية الحرجة لها قواعد جودة موثقة (اكتمال / دقة / حداثة / تفرّد).',
     N'What percentage of critical data assets have documented quality rules in production?',
     N'ما نسبة الأصول الحرجة التي لها قواعد جودة موثقة في الإنتاج؟',
     N'DQ rule register; sample DQ rule definitions; DQ monitoring dashboard screenshot.',
     N'سجل قواعد الجودة؛ نماذج تعريفات؛ لقطة شاشة من لوحة مراقبة الجودة.',
     N'LEVEL_0_5', 1.5, N'HIGH', 1, N'Data Quality Lead', N'QUARTERLY',
     N'NDMO-DQ-RULES', NULL, NULL, N'DAMA DMBOK Ch 13',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Data Catalog and Metadata', N'Catalog Coverage',
     N'NDI-DC-001', N'Data catalog covers critical data assets',
     N'فهرس البيانات يغطي الأصول الحرجة',
     N'A centralised data catalog with metadata records for critical data assets across all domains.',
     N'فهرس بيانات مركزي يضم تسجيلات البيانات الوصفية للأصول الحرجة عبر جميع المجالات.',
     N'What percentage of critical data assets are registered in the central data catalog?',
     N'ما نسبة الأصول الحرجة المسجّلة في الفهرس المركزي؟',
     N'Catalog export; coverage report by domain; sample metadata records.',
     N'تصدير الفهرس؛ تقرير التغطية حسب المجال؛ نماذج من تسجيلات البيانات الوصفية.',
     N'LEVEL_0_5', 1.5, N'HIGH', 1, N'Data Catalog Owner', N'QUARTERLY',
     N'NDMO-METADATA', NULL, NULL, N'DAMA DMBOK Ch 12',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Data Classification', N'Classification Coverage',
     N'NDI-CL-001', N'Data classification applied across the enterprise',
     N'تصنيف البيانات مطبّق على مستوى الجهة',
     N'All organisational data is classified per Saudi Data Classification Policy (Top Secret / Secret / Confidential / Public).',
     N'جميع بيانات الجهة مصنّفة وفق سياسة تصنيف البيانات السعودية (سري للغاية / سري / مقيّد / عام).',
     N'What percentage of enterprise data is classified per the national classification scheme?',
     N'ما نسبة بيانات الجهة المصنّفة وفق المخطط الوطني؟',
     N'Classification policy; classification matrix; sample classified datasets; tooling screenshot.',
     N'سياسة التصنيف؛ مصفوفة التصنيف؛ عيّنات من البيانات المصنّفة؛ لقطة من الأداة.',
     N'LEVEL_0_5', 2.0, N'CRITICAL', 1, N'Data Classification Lead', N'SEMI_ANNUAL',
     N'NDMO-CLASSIFY', N'PDPL Art. 5', N'NCA ECC 2-1-3', N'ISO 27001 A.5.12',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Personal Data Protection', N'PDPL Compliance',
     N'NDI-PDPL-001', N'Personal data processing activities documented (Article 18)',
     N'سجل أنشطة معالجة البيانات الشخصية موثّق (المادة 18)',
     N'A Record of Processing Activities (ROPA) covering all personal data processing as required by PDPL Article 18.',
     N'سجل أنشطة المعالجة (ROPA) يغطي جميع أنشطة معالجة البيانات الشخصية وفق المادة 18 من النظام.',
     N'Is the ROPA register complete, reviewed within the past 12 months, and aligned with PDPL Article 18?',
     N'هل سجل أنشطة المعالجة كامل وتمت مراجعته خلال آخر 12 شهراً ومتوافق مع المادة 18؟',
     N'ROPA register export; latest review approval; sample processing activity records.',
     N'تصدير سجل ROPA؛ آخر اعتماد للمراجعة؛ نماذج من تسجيلات النشاط.',
     N'LEVEL_0_5', 2.0, N'CRITICAL', 1, N'Data Protection Officer', N'ANNUAL',
     N'NDMO-PDPL', N'PDPL Art. 18', NULL, N'ISO 27701',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Data Security', N'Security Controls',
     N'NDI-DS-001', N'Data-at-rest encryption for critical datasets',
     N'تشفير البيانات الساكنة للأصول الحرجة',
     N'Encryption-at-rest applied to all classified-as-Secret-or-higher datasets per NCA ECC requirements.',
     N'تشفير في حالة السكون لجميع الأصول المصنّفة سري وأعلى وفق متطلبات NCA ECC.',
     N'Are all Secret+ datasets encrypted at rest with NCA-compliant key management?',
     N'هل جميع الأصول المصنّفة سري وأعلى مشفّرة في حالة السكون بإدارة مفاتيح متوافقة مع الهيئة الوطنية للأمن السيبراني؟',
     N'Encryption configuration; key management policy; scan-result evidence.',
     N'إعدادات التشفير؛ سياسة إدارة المفاتيح؛ نتائج فحص الإثبات.',
     N'LEVEL_0_5', 2.0, N'CRITICAL', 1, N'CISO Office', N'QUARTERLY',
     N'NDMO-SECURITY', NULL, N'NCA ECC 2-3', N'ISO 27001 A.10.1',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Data Sharing', N'Sharing Agreements',
     N'NDI-DSH-001', N'Inter-entity data-sharing agreements in place',
     N'اتفاقيات مشاركة البيانات بين الجهات معتمدة',
     N'Formal data-sharing agreements for all cross-entity data exchanges aligned with Saudi data-sharing policy.',
     N'اتفاقيات رسمية لمشاركة البيانات لكل تبادل بين الجهات متوافقة مع السياسة الوطنية لمشاركة البيانات.',
     N'Are all active data-sharing exchanges covered by an approved agreement?',
     N'هل جميع تبادلات البيانات الفعّالة مغطّاة باتفاقية معتمدة؟',
     N'Signed agreements register; renewal schedule; sample agreement documents.',
     N'سجل الاتفاقيات الموقّعة؛ جدول التجديد؛ نماذج من الاتفاقيات.',
     N'LEVEL_0_5', 1.5, N'HIGH', 1, N'Data Sharing Officer', N'ANNUAL',
     N'NDMO-SHARING', NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'MATURITY', N'Open Data', N'Open Data Publication',
     N'NDI-OD-001', N'Open data publication aligned with Saudi Open Data Policy',
     N'نشر البيانات المفتوحة متوافق مع السياسة الوطنية',
     N'Eligible datasets published to the national open-data portal in machine-readable formats with proper metadata.',
     N'الأصول المؤهلة منشورة في البوابة الوطنية للبيانات المفتوحة بصيغ قابلة للقراءة الآلية مع بيانات وصفية صحيحة.',
     N'What percentage of eligible open datasets are published with complete metadata?',
     N'ما نسبة الأصول المفتوحة المؤهلة المنشورة ببيانات وصفية كاملة؟',
     N'Open-data portal listing; metadata schema; publication-rate report.',
     N'قائمة البوابة؛ مخطط البيانات الوصفية؛ تقرير معدل النشر.',
     N'LEVEL_0_5', 1.0, N'MEDIUM', 0, N'Open Data Steward', N'QUARTERLY',
     N'NDMO-OPEN-DATA', NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    -- ── COMPLIANCE component — 7 items ───────────────────────────
    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Data Governance', N'Policy Approval',
     N'NDI-DG-C-001', N'Approved data governance policy published',
     N'سياسة حوكمة البيانات المعتمدة منشورة',
     N'Board-approved data governance policy is published, current (last reviewed within 12 months), and accessible to all staff.',
     N'سياسة حوكمة البيانات معتمدة من المجلس ومنشورة، تمت مراجعتها خلال آخر 12 شهراً، ومتاحة للجميع.',
     N'Is the current data governance policy approved by the board and accessible to all employees?',
     N'هل سياسة حوكمة البيانات الحالية معتمدة من المجلس ومتاحة لجميع الموظفين؟',
     N'Board approval minutes; policy document with effective date; intranet publication evidence.',
     N'محضر اعتماد المجلس؛ وثيقة السياسة بتاريخ السريان؛ دليل النشر على الإنترانت.',
     N'BINARY', 2.0, N'CRITICAL', 1, N'Data Office Manager', N'ANNUAL',
     N'NDMO-POLICY', NULL, NULL, N'ISO 38505',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Personal Data Protection', N'Privacy Notice',
     N'NDI-PDPL-C-001', N'Privacy notices comply with PDPL Article 12',
     N'إشعارات الخصوصية تتوافق مع المادة 12',
     N'Privacy notices are published in Arabic and English, cover all mandatory elements per PDPL Article 12, and updated annually.',
     N'إشعارات الخصوصية منشورة بالعربية والإنجليزية، تغطي جميع العناصر الإلزامية وفق المادة 12، ومحدّثة سنوياً.',
     N'Do privacy notices include all PDPL Article 12 mandatory elements?',
     N'هل تتضمن إشعارات الخصوصية جميع العناصر الإلزامية وفق المادة 12؟',
     N'Published privacy notice URL; bilingual versions; review approval.',
     N'رابط إشعار الخصوصية المنشور؛ النسختان العربية والإنجليزية؛ اعتماد المراجعة.',
     N'BINARY', 1.5, N'HIGH', 1, N'Data Protection Officer', N'ANNUAL',
     NULL, N'PDPL Art. 12', NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Data Security', N'Access Controls',
     N'NDI-DS-C-001', N'Role-based access control on critical datasets',
     N'التحكم في الوصول حسب الدور للأصول الحرجة',
     N'RBAC applied to all critical datasets; access reviewed at least quarterly; segregation of duties enforced.',
     N'RBAC مطبّق على جميع الأصول الحرجة؛ مراجعة الوصول كل ثلاثة أشهر على الأقل؛ فصل المهام مفعّل.',
     N'Is RBAC enforced on all critical datasets with quarterly access reviews?',
     N'هل RBAC مفعّل على جميع الأصول الحرجة مع مراجعات وصول ربع سنوية؟',
     N'IAM policy export; latest access review report; sample role definitions.',
     N'تصدير سياسة IAM؛ آخر تقرير مراجعة وصول؛ نماذج تعريفات الأدوار.',
     N'BINARY', 1.5, N'HIGH', 1, N'CISO Office', N'QUARTERLY',
     NULL, NULL, N'NCA ECC 2-2', N'ISO 27001 A.9',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Master and Reference Data', N'MDM Governance',
     N'NDI-MDM-C-001', N'Master data governance applied to top entities',
     N'حوكمة البيانات الرئيسية مطبّقة على الكيانات الأهم',
     N'Master data domains (Customer / Supplier / Employee / Asset) have assigned stewards and documented match-merge rules.',
     N'مجالات البيانات الرئيسية (العميل / المورّد / الموظف / الأصل) لها مشرفون معيّنون وقواعد مطابقة موثّقة.',
     N'Are the top master-data domains under formal stewardship with documented merge rules?',
     N'هل المجالات الرئيسية تحت إشراف رسمي مع قواعد دمج موثّقة؟',
     N'Steward register; match-merge rule definitions; MDM tool screenshot.',
     N'سجل المشرفين؛ تعريفات قواعد الدمج؛ لقطة شاشة من أداة MDM.',
     N'BINARY', 1.0, N'MEDIUM', 0, N'MDM Lead', N'ANNUAL',
     N'NDMO-MDM', NULL, NULL, N'DAMA DMBOK Ch 10',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Data Catalog and Metadata', N'Metadata Standards',
     N'NDI-DC-C-001', N'Metadata follows national standards (DCAT-AP-SA)',
     N'البيانات الوصفية تتبع المعايير الوطنية (DCAT-AP-SA)',
     N'Critical data assets carry metadata compliant with the Saudi DCAT profile (DCAT-AP-SA).',
     N'الأصول الحرجة تحمل بيانات وصفية متوافقة مع ملف DCAT السعودي (DCAT-AP-SA).',
     N'Are metadata records on critical assets compliant with DCAT-AP-SA?',
     N'هل البيانات الوصفية للأصول الحرجة متوافقة مع DCAT-AP-SA؟',
     N'DCAT-AP-SA mapping document; sample compliant record; gap report.',
     N'وثيقة المخطط؛ نموذج تسجيل متوافق؛ تقرير الفجوات.',
     N'BINARY', 1.0, N'MEDIUM', 0, N'Data Catalog Owner', N'ANNUAL',
     N'NDMO-METADATA-STD', NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Data Lifecycle Management', N'Retention',
     N'NDI-DLM-C-001', N'Data retention policy aligned with national requirements',
     N'سياسة الاحتفاظ بالبيانات متوافقة مع المتطلبات الوطنية',
     N'Retention schedule covers all classified data, aligned with PDPL retention principles and national archives policy.',
     N'جدول الاحتفاظ يغطي جميع البيانات المصنّفة، ومتوافق مع مبادئ الاحتفاظ في النظام والسياسة الوطنية للأرشيف.',
     N'Is the retention schedule current, executive-approved, and operationally enforced?',
     N'هل جدول الاحتفاظ حديث ومعتمد ومُنفَّذ تشغيلياً؟',
     N'Retention schedule document; approval record; sample disposition evidence.',
     N'وثيقة جدول الاحتفاظ؛ سجل الاعتماد؛ نماذج من إثباتات الإتلاف.',
     N'BINARY', 1.0, N'MEDIUM', 0, N'Records Manager', N'ANNUAL',
     N'NDMO-DLM', N'PDPL Art. 18(2)', NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'COMPLIANCE', N'Data Literacy and Awareness', N'Training',
     N'NDI-DL-C-001', N'Mandatory data governance training completed (95%+)',
     N'إكمال تدريب حوكمة البيانات الإلزامي (95%+)',
     N'All employees complete the mandatory annual data governance / PDPL training, with a completion rate ≥ 95%.',
     N'جميع الموظفين أكملوا تدريب حوكمة البيانات / النظام السنوي الإلزامي، بمعدل إكمال ≥ 95%.',
     N'Is annual mandatory training completion ≥ 95% across all employees?',
     N'هل معدل إكمال التدريب الإلزامي السنوي ≥ 95% لجميع الموظفين؟',
     N'Training-system completion report; HR confirmation; non-completion exception list.',
     N'تقرير إكمال نظام التدريب؛ تأكيد الموارد البشرية؛ قائمة الاستثناءات.',
     N'PERCENT', 1.0, N'MEDIUM', 1, N'HR / Training Lead', N'ANNUAL',
     NULL, NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    -- ── OPERATIONAL_EXCELLENCE component — 5 items ─────────────────
    (1, N'v0.1-placeholder', N'OPERATIONAL_EXCELLENCE', N'Data Quality', N'Issue Resolution',
     N'NDI-OE-001', N'Mean time to resolve data quality issues',
     N'متوسط زمن حل مشاكل جودة البيانات',
     N'Average time from data-quality-issue detection to closure across all severity tiers.',
     N'متوسط الزمن من اكتشاف مشكلة الجودة حتى إغلاقها عبر جميع مستويات الخطورة.',
     N'What is the mean resolution time (in days) for data-quality issues over the last 12 months?',
     N'ما متوسط زمن الحل (بالأيام) لمشاكل الجودة خلال آخر 12 شهراً؟',
     N'DQ-ticket export; SLA report; resolution-time trend chart.',
     N'تصدير تذاكر الجودة؛ تقرير اتفاقية الخدمة؛ مخطط اتجاه زمن الحل.',
     N'PERCENT', 1.0, N'HIGH', 0, N'Data Quality Lead', N'MONTHLY',
     NULL, NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'OPERATIONAL_EXCELLENCE', N'Data Catalog and Metadata', N'Coverage',
     N'NDI-OE-002', N'Critical-data-assets catalogued percentage',
     N'نسبة الأصول الحرجة المسجّلة في الفهرس',
     N'Percentage of critical data assets (per the enterprise critical-assets register) that are registered in the central catalog with complete metadata.',
     N'نسبة الأصول الحرجة (وفق سجل الأصول الحرجة) المسجّلة في الفهرس المركزي ببيانات وصفية كاملة.',
     N'What percentage of critical data assets are cataloged?',
     N'ما نسبة الأصول الحرجة المسجّلة في الفهرس؟',
     N'Catalog export; critical-assets register; coverage report.',
     N'تصدير الفهرس؛ سجل الأصول الحرجة؛ تقرير التغطية.',
     N'PERCENT', 1.5, N'HIGH', 1, N'Data Catalog Owner', N'QUARTERLY',
     NULL, NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'OPERATIONAL_EXCELLENCE', N'Data Sharing', N'SLA',
     N'NDI-OE-003', N'Data-sharing requests processed within SLA',
     N'طلبات مشاركة البيانات تتم وفق اتفاقية الخدمة',
     N'Percentage of inter-entity data-sharing requests fulfilled within the published SLA window.',
     N'نسبة طلبات مشاركة البيانات بين الجهات التي تم تلبيتها ضمن نافذة اتفاقية الخدمة المنشورة.',
     N'What percentage of data-sharing requests were processed within SLA over the last 12 months?',
     N'ما نسبة طلبات المشاركة التي تمت وفق اتفاقية الخدمة خلال آخر 12 شهراً؟',
     N'Request-ticket export; SLA report; backlog age analysis.',
     N'تصدير التذاكر؛ تقرير اتفاقية الخدمة؛ تحليل عمر التراكم.',
     N'PERCENT', 1.0, N'MEDIUM', 0, N'Data Sharing Officer', N'QUARTERLY',
     NULL, NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'OPERATIONAL_EXCELLENCE', N'Personal Data Protection', N'DPIA',
     N'NDI-OE-004', N'Privacy impact assessments completed before launch',
     N'تقييم أثر الخصوصية مكتمل قبل الإطلاق',
     N'Percentage of new processing activities (qualifying under PDPL Art. 25) with a completed and approved PIA before go-live.',
     N'نسبة أنشطة المعالجة الجديدة المؤهلة (المادة 25) التي لها تقييم أثر مكتمل ومعتمد قبل الإطلاق.',
     N'What percentage of qualifying new processing activities had a completed PIA before go-live?',
     N'ما نسبة أنشطة المعالجة الجديدة المؤهلة التي أُكمل لها تقييم الأثر قبل الإطلاق؟',
     N'PIA register; approval workflow records; sample completed PIA.',
     N'سجل التقييم؛ سجلات مسار الاعتماد؛ نموذج تقييم مكتمل.',
     N'PERCENT', 1.5, N'HIGH', 1, N'Data Protection Officer', N'QUARTERLY',
     NULL, N'PDPL Art. 25', NULL, N'ISO 27701',
     0, 1, N'ACTIVE', '2025-01-01'),

    (1, N'v0.1-placeholder', N'OPERATIONAL_EXCELLENCE', N'Data Literacy and Awareness', N'Coverage',
     N'NDI-OE-005', N'Data literacy programme employee coverage',
     N'تغطية برنامج التوعية البياناتية للموظفين',
     N'Percentage of employees who completed the data-literacy programme in the past 12 months.',
     N'نسبة الموظفين الذين أكملوا برنامج التوعية البياناتية خلال آخر 12 شهراً.',
     N'What percentage of employees completed the data-literacy programme this year?',
     N'ما نسبة الموظفين الذين أكملوا برنامج التوعية البياناتية هذا العام؟',
     N'Training-system completion data; programme curriculum; HR roster.',
     N'بيانات إكمال نظام التدريب؛ منهج البرنامج؛ سجل الموارد البشرية.',
     N'PERCENT', 1.0, N'MEDIUM', 0, N'HR / Data Literacy Lead', N'SEMI_ANNUAL',
     NULL, NULL, NULL, NULL,
     0, 1, N'ACTIVE', '2025-01-01');
END
GO

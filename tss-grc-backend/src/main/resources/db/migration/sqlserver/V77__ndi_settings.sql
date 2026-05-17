-- V77 — NDI Settings (slice 13)
--
-- Key-value configuration store scoped to organisation. Each setting
-- carries a typed value (string/integer/decimal/boolean/json), an
-- optional default override, and metadata.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'settings' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.settings (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,

        setting_key         NVARCHAR(80)  NOT NULL,
        category            NVARCHAR(40)  NOT NULL DEFAULT N'GENERAL',
        value_type          NVARCHAR(20)  NOT NULL DEFAULT N'STRING',  -- STRING | INTEGER | DECIMAL | BOOLEAN | JSON
        value_text          NVARCHAR(MAX) NULL,

        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        default_value       NVARCHAR(MAX) NULL,
        is_locked           BIT           NOT NULL DEFAULT 0,    -- Locked settings ignore client writes.

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_set_type CHECK (value_type IN
            (N'STRING', N'INTEGER', N'DECIMAL', N'BOOLEAN', N'JSON'))
    );

    CREATE UNIQUE INDEX ux_ndi_set_org_key
        ON ndi.settings(organisation_id, setting_key) WHERE is_deleted = 0;
    CREATE INDEX ix_ndi_set_category ON ndi.settings(organisation_id, category);
END
GO

-- Default settings for the demo organisation.

IF NOT EXISTS (SELECT 1 FROM ndi.settings WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.settings (
        organisation_id, setting_key, category, value_type, value_text,
        name_en, name_ar, description_en, description_ar, default_value
    ) VALUES
    -- GENERAL
    (1, N'default_language', N'GENERAL', N'STRING', N'AR',
       N'Default UI language', N'لغة الواجهة الافتراضية',
       N'AR or EN. Per-user override remains available.',
       N'AR أو EN. التجاوز لكل مستخدم يبقى متاحاً.', N'AR'),
    (1, N'entity_name_en', N'GENERAL', N'STRING', N'Demo Entity',
       N'Entity name (English)', N'اسم الجهة (إنجليزي)',
       N'Shown on report headers and the submission package.',
       N'يظهر في رؤوس التقارير وحزمة التقديم.', N'Demo Entity'),
    (1, N'entity_name_ar', N'GENERAL', N'STRING', N'الجهة التجريبية',
       N'Entity name (Arabic)', N'اسم الجهة (عربي)',
       N'Shown on report headers and the submission package.',
       N'يظهر في رؤوس التقارير وحزمة التقديم.', N'الجهة التجريبية'),

    -- THRESHOLDS — drive score banding in dashboards and reports.
    (1, N'ndi_threshold_advanced', N'THRESHOLDS', N'INTEGER', N'80',
       N'NDI advanced band threshold (%)', N'حد نطاق "متقدّم" لنضيء (%)',
       N'Scores ≥ this percent show the Advanced label.',
       N'النتائج ≥ هذه النسبة تُعرَض بوسم "متقدّم".', N'80'),
    (1, N'ndi_threshold_on_track', N'THRESHOLDS', N'INTEGER', N'60',
       N'NDI on-track band threshold (%)', N'حد نطاق "على المسار" لنضيء (%)',
       N'Scores ≥ this percent (and below advanced) show On-track.',
       N'النتائج ≥ هذه النسبة (وأقل من المتقدّم) تُعرَض كـ "على المسار".', N'60'),
    (1, N'ndi_threshold_needs_work', N'THRESHOLDS', N'INTEGER', N'40',
       N'NDI needs-work band threshold (%)', N'حد نطاق "يحتاج تحسين" لنضيء (%)',
       N'Scores ≥ this percent (and below on-track) show Needs-work.',
       N'النتائج ≥ هذه النسبة (وأقل من على المسار) تُعرَض كـ "يحتاج تحسين".', N'40'),

    -- WORKFLOW
    (1, N'evidence_expiry_warning_days', N'WORKFLOW', N'INTEGER', N'60',
       N'Evidence expiry warning horizon (days)', N'مدة تنبيه انتهاء الأدلة (يوماً)',
       N'Evidence within this many days of expiry shows on the dashboard.',
       N'الأدلة على بُعد هذا العدد من الأيام من الانتهاء تظهر في لوحة المعلومات.', N'60'),
    (1, N'critical_gap_capa_max_days', N'WORKFLOW', N'INTEGER', N'90',
       N'Critical-gap CAPA max target horizon (days)', N'الحدّ الأقصى لمدة هدف الإجراء التصحيحي للفجوة الحرجة',
       N'Critical gaps require a CAPA with target_date within this window.',
       N'الفجوات الحرجة تتطلب إجراءً تصحيحياً بتاريخ هدف ضمن هذه النافذة.', N'90'),
    (1, N'require_attestation_on_submit', N'WORKFLOW', N'BOOLEAN', N'true',
       N'Require attestation text on submission', N'اشتراط نص الإقرار عند التقديم',
       N'When true, /submit refuses an empty attestation.',
       N'عند التفعيل، /submit يرفض الإقرار الفارغ.', N'true'),

    -- ATTESTATION templates
    (1, N'attestation_template_en', N'ATTESTATION', N'STRING',
       N'I, the undersigned, declare that the data submitted in this NDI package is accurate and represents the entity''s state as of the submission date.',
       N'Default attestation (English)', N'نص الإقرار الافتراضي (إنجليزي)',
       N'Pre-fills the submission attestation prompt.',
       N'يملأ مسبقاً مربع حوار الإقرار عند التقديم.',
       N'I, the undersigned, declare that the data submitted in this NDI package is accurate and represents the entity''s state as of the submission date.'),
    (1, N'attestation_template_ar', N'ATTESTATION', N'STRING',
       N'أُقرّ — أنا الموقّع أدناه — بأن البيانات المُقدَّمة في هذه الحزمة دقيقة وتمثّل واقع الجهة في تاريخ التقديم.',
       N'Default attestation (Arabic)', N'نص الإقرار الافتراضي (عربي)',
       N'Pre-fills the submission attestation prompt.',
       N'يملأ مسبقاً مربع حوار الإقرار عند التقديم.',
       N'أُقرّ — أنا الموقّع أدناه — بأن البيانات المُقدَّمة في هذه الحزمة دقيقة وتمثّل واقع الجهة في تاريخ التقديم.'),

    -- PLACEHOLDER guardrail
    (1, N'official_library_imported', N'GUARDRAIL', N'BOOLEAN', N'false',
       N'Official SDAIA library imported', N'مكتبة سدايا الرسمية مستوردة',
       N'Flip to true only after the official SDAIA control library is imported. UI uses this to suppress the placeholder banner sitewide.',
       N'حوّل إلى true فقط بعد استيراد مكتبة الضوابط الرسمية من سدايا. الواجهة تستخدم هذه القيمة لإخفاء شارة المكتبة المؤقتة في كل المكان.',
       N'false');
END
GO

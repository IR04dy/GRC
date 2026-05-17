-- V76 — NDI Reports (slice 11)
--
-- Saved report definitions feeding the Reports tab. Each row carries
-- the report type, format, optional filters JSON, optional schedule
-- (cron expression — execution loop is wired up in a later slice), and
-- the metadata from the last run.
--
-- The 12 canonical report types from the spec are seeded as inactive
-- examples (status='ACTIVE' for them so they're visible, but each
-- entity will edit them or create their own).
--
-- Status: ACTIVE | DISABLED | ARCHIVED.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'report_definition' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.report_definition (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        report_code         NVARCHAR(40)  NULL,
        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        report_type         NVARCHAR(40)  NOT NULL,
            -- NDI_EXECUTIVE_SUMMARY | MATURITY_HEATMAP | COMPLIANCE_STATUS
            -- | OPEX_KPI_DASHBOARD | GAP_REGISTER | CAPA_REGISTER
            -- | IMPROVEMENT_PLAN_ROADMAP | EVIDENCE_INVENTORY
            -- | DOMAIN_ROLLUP | CYCLE_HISTORY | AUDIT_TRAIL
            -- | SUBMISSION_PACKAGE_EXPORT
        format              NVARCHAR(10)  NOT NULL DEFAULT N'JSON',  -- JSON | PDF | XLSX | CSV
        language            NVARCHAR(5)   NOT NULL DEFAULT N'EN',    -- EN | AR | BOTH

        filters_json        NVARCHAR(MAX) NULL,
        schedule_cron       NVARCHAR(100) NULL,
        recipients          NVARCHAR(MAX) NULL,    -- pipe-separated email list

        last_run_at         DATETIME2     NULL,
        last_run_status     NVARCHAR(20)  NULL,    -- SUCCESS | FAILED | RUNNING
        last_run_message    NVARCHAR(MAX) NULL,
        last_run_size_bytes BIGINT        NULL,

        status              NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_rep_type CHECK (report_type IN
            (N'NDI_EXECUTIVE_SUMMARY', N'MATURITY_HEATMAP', N'COMPLIANCE_STATUS',
             N'OPEX_KPI_DASHBOARD', N'GAP_REGISTER', N'CAPA_REGISTER',
             N'IMPROVEMENT_PLAN_ROADMAP', N'EVIDENCE_INVENTORY',
             N'DOMAIN_ROLLUP', N'CYCLE_HISTORY', N'AUDIT_TRAIL',
             N'SUBMISSION_PACKAGE_EXPORT')),
        CONSTRAINT ck_ndi_rep_format CHECK (format IN (N'JSON', N'PDF', N'XLSX', N'CSV')),
        CONSTRAINT ck_ndi_rep_lang   CHECK (language IN (N'EN', N'AR', N'BOTH')),
        CONSTRAINT ck_ndi_rep_status CHECK (status IN (N'ACTIVE', N'DISABLED', N'ARCHIVED'))
    );

    CREATE INDEX ix_ndi_rep_org    ON ndi.report_definition(organisation_id);
    CREATE INDEX ix_ndi_rep_type   ON ndi.report_definition(organisation_id, report_type);
    CREATE INDEX ix_ndi_rep_status ON ndi.report_definition(organisation_id, status);
END
GO

-- Seed 12 canonical report definitions for the demo organisation.

IF NOT EXISTS (SELECT 1 FROM ndi.report_definition WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.report_definition (
        organisation_id, report_code, name_en, name_ar,
        description_en, description_ar,
        report_type, format, language, status
    ) VALUES
    (1, N'RPT-001', N'NDI executive summary',         N'الملخص التنفيذي لنضيء',
       N'Overall NDI score, per-domain blend, component summary for executives.',
       N'النتيجة الإجمالية لنضيء، خليط المجالات، ملخص المكوّنات للقيادة التنفيذية.',
       N'NDI_EXECUTIVE_SUMMARY', N'PDF', N'BOTH', N'ACTIVE'),
    (1, N'RPT-002', N'Maturity heatmap',              N'خريطة حرارية للنضج',
       N'CMMI 0-5 levels across every domain in the cycle.',
       N'مستويات CMMI من 0 إلى 5 عبر كل مجال في الدورة.',
       N'MATURITY_HEATMAP', N'XLSX', N'BOTH', N'ACTIVE'),
    (1, N'RPT-003', N'Compliance status report',      N'تقرير حالة الالتزام',
       N'Per-requirement compliance status with evidence references.',
       N'حالة الالتزام لكل متطلب مع مراجع الأدلة.',
       N'COMPLIANCE_STATUS', N'XLSX', N'BOTH', N'ACTIVE'),
    (1, N'RPT-004', N'OpEx KPI dashboard',            N'لوحة مؤشّرات التميز التشغيلي',
       N'All operational excellence KPIs with progress, target, status.',
       N'جميع مؤشرات التميز التشغيلي مع التقدم والهدف والحالة.',
       N'OPEX_KPI_DASHBOARD', N'PDF', N'BOTH', N'ACTIVE'),
    (1, N'RPT-005', N'Gap register',                  N'سجل الفجوات',
       N'All gaps with severity, owner, target date, CAPA count.',
       N'جميع الفجوات مع الخطورة والمالك وتاريخ الهدف وعدد الإجراءات.',
       N'GAP_REGISTER', N'XLSX', N'BOTH', N'ACTIVE'),
    (1, N'RPT-006', N'Corrective action register',    N'سجل الإجراءات التصحيحية',
       N'All CAPAs with parent gap, progress, status.',
       N'جميع الإجراءات التصحيحية مع الفجوة الأصل والتقدم والحالة.',
       N'CAPA_REGISTER', N'XLSX', N'BOTH', N'ACTIVE'),
    (1, N'RPT-007', N'Improvement plan roadmap',      N'خارطة طريق خطة التحسين',
       N'Time-bound roadmap grouped by quarter with budget rollup.',
       N'خارطة طريق محددة المدة مُجمَّعة حسب الربع مع تجميع الميزانية.',
       N'IMPROVEMENT_PLAN_ROADMAP', N'PDF', N'BOTH', N'ACTIVE'),
    (1, N'RPT-008', N'Evidence inventory',            N'فهرس الأدلة',
       N'All evidence with type, classification, expiry.',
       N'جميع الأدلة مع النوع والتصنيف والانتهاء.',
       N'EVIDENCE_INVENTORY', N'XLSX', N'BOTH', N'ACTIVE'),
    (1, N'RPT-009', N'Domain rollup',                 N'تجميع المجالات',
       N'Per-domain blended score with maturity / compliance / opex breakdown.',
       N'النتيجة المُختلطة لكل مجال مع تفصيل النضج والالتزام والتميز التشغيلي.',
       N'DOMAIN_ROLLUP', N'PDF', N'BOTH', N'ACTIVE'),
    (1, N'RPT-010', N'Cycle history',                 N'سجل الدورات',
       N'All assessment cycles with status and reporting year.',
       N'جميع دورات التقييم مع الحالة وسنة الإبلاغ.',
       N'CYCLE_HISTORY', N'JSON', N'BOTH', N'ACTIVE'),
    (1, N'RPT-011', N'Audit trail',                   N'مسار التدقيق',
       N'Filtered audit events across the NDI module.',
       N'أحداث التدقيق المُصفّاة في وحدة نضيء.',
       N'AUDIT_TRAIL', N'JSON', N'BOTH', N'ACTIVE'),
    (1, N'RPT-012', N'Submission package export',     N'تصدير حزمة التقديم',
       N'Full submission package with snapshot + manifest + checksum.',
       N'حزمة التقديم الكاملة مع اللقطة والبيان والبصمة.',
       N'SUBMISSION_PACKAGE_EXPORT', N'JSON', N'BOTH', N'ACTIVE');
END
GO

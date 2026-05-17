-- V73 — NDI Gaps + Corrective Actions (slice 8)
--
-- Two tables:
--
--   ndi.gap                  one row per identified gap (from compliance,
--                            maturity or other sources). Carries severity,
--                            owner, target date, and a link back to the
--                            source assessment row + the corrective action.
--
--   ndi.corrective_action    one row per CAPA — multiple per gap allowed.
--                            Carries action type, progress %, owner, target
--                            and completion dates, and an evidence link.
--
-- Rule 7 (gap → CAPA)   : a gap can have many CAPAs; closing all CAPAs
--                         lets the service auto-close the gap.
-- Rule 8 (severity)     : CRITICAL gaps require a CAPA with target_date
--                         ≤ 90 days from open_date.
-- Rule 9 (progress)     : progress_pct is constrained to 0..100 and
--                         drives the gap's rollup_progress in
--                         service-side recomputation.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'gap' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.gap (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT        NOT NULL,
        external_id             NVARCHAR(64)  NULL,

        cycle_id                BIGINT        NOT NULL,
        gap_code                NVARCHAR(40)  NULL,

        source_type             NVARCHAR(20)  NOT NULL,     -- COMPLIANCE | MATURITY | KPI | AUDIT | MANUAL
        source_id               BIGINT        NULL,         -- FK into the source table (compliance/maturity/etc.)
        domain                  NVARCHAR(80)  NULL,

        title_en                NVARCHAR(255) NOT NULL,
        title_ar                NVARCHAR(255) NULL,
        description_en          NVARCHAR(MAX) NULL,
        description_ar          NVARCHAR(MAX) NULL,
        root_cause              NVARCHAR(MAX) NULL,
        impact_summary          NVARCHAR(MAX) NULL,

        severity                NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',  -- CRITICAL | HIGH | MEDIUM | LOW
        likelihood              NVARCHAR(20)  NULL,                        -- RARE | UNLIKELY | POSSIBLE | LIKELY | ALMOST_CERTAIN
        risk_score              INT           NULL,                        -- service-computed 1..25

        owner_name              NVARCHAR(255) NULL,
        owner_email             NVARCHAR(255) NULL,

        open_date               DATE          NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATE),
        target_date             DATE          NULL,
        actual_close_date       DATE          NULL,

        rollup_progress         INT           NOT NULL DEFAULT 0,          -- 0..100, service-set
        capa_count              INT           NOT NULL DEFAULT 0,
        capa_completed_count    INT           NOT NULL DEFAULT 0,

        status                  NVARCHAR(20)  NOT NULL DEFAULT N'OPEN',    -- OPEN | IN_PROGRESS | RESOLVED | ACCEPTED_RISK | CLOSED
        risk_id                 BIGINT        NULL,                        -- link into ERM
        notes                   NVARCHAR(MAX) NULL,

        is_deleted              BIT           NOT NULL DEFAULT 0,
        created_at              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              NVARCHAR(100) NULL,
        updated_at              DATETIME2     NULL,
        updated_by              NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_gap_source   CHECK (source_type IN (N'COMPLIANCE', N'MATURITY', N'KPI', N'AUDIT', N'MANUAL')),
        CONSTRAINT ck_ndi_gap_sev      CHECK (severity IN (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT ck_ndi_gap_status   CHECK (status IN (N'OPEN', N'IN_PROGRESS', N'RESOLVED', N'ACCEPTED_RISK', N'CLOSED')),
        CONSTRAINT ck_ndi_gap_progress CHECK (rollup_progress BETWEEN 0 AND 100)
    );

    CREATE INDEX ix_ndi_gap_org_cycle ON ndi.gap(organisation_id, cycle_id);
    CREATE INDEX ix_ndi_gap_status    ON ndi.gap(organisation_id, status);
    CREATE INDEX ix_ndi_gap_severity  ON ndi.gap(organisation_id, severity);
    CREATE INDEX ix_ndi_gap_source    ON ndi.gap(source_type, source_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'corrective_action' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.corrective_action (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT        NOT NULL,
        external_id             NVARCHAR(64)  NULL,

        gap_id                  BIGINT        NOT NULL,
        action_code             NVARCHAR(40)  NULL,

        action_en               NVARCHAR(MAX) NOT NULL,
        action_ar               NVARCHAR(MAX) NULL,
        action_type             NVARCHAR(30)  NOT NULL DEFAULT N'CONTROL',  -- CONTROL | POLICY | TECH | TRAINING | PROCESS | GOVERNANCE

        owner_name              NVARCHAR(255) NULL,
        owner_email             NVARCHAR(255) NULL,

        target_date             DATE          NULL,
        completion_date         DATE          NULL,

        progress_pct            INT           NOT NULL DEFAULT 0,           -- 0..100
        status                  NVARCHAR(20)  NOT NULL DEFAULT N'NOT_STARTED', -- NOT_STARTED | IN_PROGRESS | BLOCKED | COMPLETED | DEFERRED

        evidence_id             BIGINT        NULL,                         -- link to ndi.evidence_item
        blocker_note            NVARCHAR(MAX) NULL,
        notes                   NVARCHAR(MAX) NULL,

        is_deleted              BIT           NOT NULL DEFAULT 0,
        created_at              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              NVARCHAR(100) NULL,
        updated_at              DATETIME2     NULL,
        updated_by              NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_capa_type     CHECK (action_type IN
            (N'CONTROL', N'POLICY', N'TECH', N'TRAINING', N'PROCESS', N'GOVERNANCE')),
        CONSTRAINT ck_ndi_capa_status   CHECK (status IN
            (N'NOT_STARTED', N'IN_PROGRESS', N'BLOCKED', N'COMPLETED', N'DEFERRED')),
        CONSTRAINT ck_ndi_capa_progress CHECK (progress_pct BETWEEN 0 AND 100)
    );

    CREATE INDEX ix_ndi_capa_org    ON ndi.corrective_action(organisation_id);
    CREATE INDEX ix_ndi_capa_gap    ON ndi.corrective_action(gap_id);
    CREATE INDEX ix_ndi_capa_status ON ndi.corrective_action(organisation_id, status);
END
GO

-- ─── Seed — illustrative gaps + CAPAs for the demo cycle ───────────

IF NOT EXISTS (SELECT 1 FROM ndi.gap WHERE organisation_id = 1)
BEGIN
    -- 5 gaps derived from the BEHIND_PLAN / NON_COMPLIANT signals seeded
    -- by earlier slices.

    INSERT INTO ndi.gap (
        organisation_id, cycle_id, gap_code, source_type, domain,
        title_en, title_ar, description_en, description_ar,
        root_cause, severity, owner_name, owner_email, target_date, status
    ) VALUES
    (1, 1, N'GAP-001', N'COMPLIANCE', N'Data Catalog and Metadata',
     N'Catalog coverage below 90% target',
     N'تغطية الفهرس تحت هدف 90%',
     N'Critical data assets catalogued is 65%, below the 90% target. Long-tail finance and legacy HR systems remain uncatalogued.',
     N'الأصول الحرجة المسجلة 65% أقل من الهدف 90%. الأنظمة المالية ذات الذيل الطويل وأنظمة الموارد البشرية القديمة لم تُسجَّل بعد.',
     N'No automated ingestion pipeline for legacy systems; manual catalog entry is bottleneck.',
     N'HIGH', N'Data Catalog Owner', N'catalog@example.gov.sa', '2026-09-30', N'IN_PROGRESS'),

    (1, 1, N'GAP-002', N'COMPLIANCE', N'Data Quality',
     N'Critical DQ rules implementation 55% vs 80% target',
     N'تطبيق قواعد جودة البيانات الحرجة 55% مقابل هدف 80%',
     N'Only 55% of critical DQ rules are implemented in production. Customer + transaction-master rules outstanding.',
     N'فقط 55% من قواعد جودة البيانات الحرجة مُطبَّقة في الإنتاج. قواعد العميل وبيانات المعاملات الرئيسية لم تُنفَّذ بعد.',
     N'DQ tooling licensed but limited engineering capacity to author rules.',
     N'HIGH', N'Data Quality Lead', N'dq@example.gov.sa', '2026-08-31', N'IN_PROGRESS'),

    (1, 1, N'GAP-003', N'COMPLIANCE', N'Personal Data Protection',
     N'ROPA register only 75% of identified activities documented',
     N'سجل ROPA يوثّق 75% فقط من الأنشطة المُحدَّدة',
     N'25% of identified personal-data processing activities remain undocumented in ROPA — concentrated in legacy HR + procurement systems.',
     N'25% من أنشطة معالجة البيانات الشخصية المُحدَّدة غير موثّقة في ROPA — مُركَّزة في أنظمة الموارد البشرية والمشتريات القديمة.',
     N'Legacy systems lack metadata catalogue integration; ROPA team relies on manual surveys.',
     N'CRITICAL', N'Data Protection Officer', N'dpo@example.gov.sa', '2026-07-31', N'OPEN'),

    (1, 1, N'GAP-004', N'COMPLIANCE', N'Open Data',
     N'Open data publication backlog (40% vs 70% target)',
     N'تراكم نشر البيانات المفتوحة (40% مقابل هدف 70%)',
     N'12 high-value eligible datasets remain in the publication queue. Service-side metadata completeness is the blocker.',
     N'12 مجموعة بيانات مؤهلة عالية القيمة لا تزال في قائمة انتظار النشر. اكتمال البيانات الوصفية على مستوى الخدمة هو العائق.',
     N'Metadata templates for the national portal recently updated; existing datasets need re-validation.',
     N'MEDIUM', N'Open Data Steward', N'opendata@example.gov.sa', '2026-10-31', N'OPEN'),

    (1, 1, N'GAP-005', N'MATURITY', N'Data Classification',
     N'Unstructured-data classification not automated',
     N'تصنيف البيانات غير المهيكَلة ليس مؤتمتاً',
     N'Automated classification for structured stores is live; unstructured data (documents, emails, file shares) still relies on manual labelling.',
     N'التصنيف المؤتمت للمخازن المهيكَلة فعّال، لكن البيانات غير المهيكَلة (الوثائق، البريد، الملفات المشتركة) لا تزال تعتمد على الوسم اليدوي.',
     N'No DLP/classification engine for unstructured stores in current toolchain.',
     N'MEDIUM', N'Data Classification Lead', N'classification@example.gov.sa', '2026-12-31', N'OPEN');

    -- CAPAs — multiple per gap where helpful

    INSERT INTO ndi.corrective_action (
        organisation_id, gap_id, action_code, action_en, action_ar, action_type,
        owner_name, target_date, status, progress_pct
    )
    SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-1',
           N'Build automated metadata-extraction pipeline for legacy finance + HR systems',
           N'بناء خط معالجة آلي لاستخراج البيانات الوصفية لأنظمة المالية والموارد البشرية القديمة',
           N'TECH', N'Data Catalog Owner', '2026-08-31', N'IN_PROGRESS', 40
    FROM ndi.gap g WHERE g.gap_code = N'GAP-001'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-2',
           N'Train 12 business stewards on catalog entry — bulk manual catch-up sprint',
           N'تدريب 12 من المسؤولين على إدخال الفهرس — سباق لحاق يدوي جماعي',
           N'TRAINING', N'Data Office Manager', '2026-07-15', N'IN_PROGRESS', 70
    FROM ndi.gap g WHERE g.gap_code = N'GAP-001'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-1',
           N'Author customer master DQ rules — 8 rules in Q2 sprint',
           N'كتابة قواعد جودة البيانات لقاعدة العملاء الرئيسية — 8 قواعد في سباق الربع الثاني',
           N'CONTROL', N'Data Quality Lead', '2026-06-30', N'IN_PROGRESS', 50
    FROM ndi.gap g WHERE g.gap_code = N'GAP-002'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-2',
           N'Author transaction-master DQ rules — 7 rules in Q3 sprint',
           N'كتابة قواعد جودة البيانات لبيانات المعاملات الرئيسية — 7 قواعد في سباق الربع الثالث',
           N'CONTROL', N'Data Quality Lead', '2026-08-31', N'NOT_STARTED', 0
    FROM ndi.gap g WHERE g.gap_code = N'GAP-002'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-1',
           N'ROPA discovery workshop for legacy HR + procurement — document remaining 25%',
           N'ورشة اكتشاف ROPA للموارد البشرية والمشتريات القديمة — توثيق 25% المتبقية',
           N'PROCESS', N'Data Protection Officer', '2026-07-15', N'IN_PROGRESS', 25
    FROM ndi.gap g WHERE g.gap_code = N'GAP-003'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-1',
           N'Re-validate metadata for 12 queued open datasets against new portal template',
           N'إعادة التحقق من البيانات الوصفية لـ 12 مجموعة بيانات مفتوحة في قائمة الانتظار مقابل النموذج الجديد للبوابة',
           N'PROCESS', N'Open Data Steward', '2026-09-30', N'NOT_STARTED', 0
    FROM ndi.gap g WHERE g.gap_code = N'GAP-004'

    UNION ALL SELECT 1, g.id, N'CAPA-' + g.gap_code + N'-1',
           N'Procure + roll out unstructured-data classification engine',
           N'شراء وتطبيق محرك تصنيف البيانات غير المهيكَلة',
           N'TECH', N'CISO + Data Classification Lead', '2026-11-30', N'NOT_STARTED', 0
    FROM ndi.gap g WHERE g.gap_code = N'GAP-005';

    -- Backfill capa_count and rollup_progress.
    UPDATE g SET
        capa_count = c.cnt,
        capa_completed_count = c.completed,
        rollup_progress = c.avg_progress
    FROM ndi.gap g
    INNER JOIN (
        SELECT gap_id,
               COUNT(*) AS cnt,
               SUM(CASE WHEN status = N'COMPLETED' THEN 1 ELSE 0 END) AS completed,
               AVG(progress_pct) AS avg_progress
        FROM ndi.corrective_action
        WHERE is_deleted = 0
        GROUP BY gap_id
    ) c ON c.gap_id = g.id;
END
GO

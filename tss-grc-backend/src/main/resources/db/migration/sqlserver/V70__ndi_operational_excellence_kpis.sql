-- V70 — NDI Operational Excellence KPIs (slice 5)
--
-- Per-cycle indicator register tracking the data-management performance
-- KPIs that feed the NDI operational-excellence component score.
-- Unlike Maturity (slice 3) and Compliance (slice 4), KPI rows are NOT
-- generated from framework items — they're standalone metrics with
-- baseline / target / actual / frequency, updated periodically as
-- operations report in.
--
-- Status set (computed by the service from actual vs target):
--   ON_TRACK         actual ≥ target × 0.90 (toward target direction)
--   AT_RISK          actual ≥ target × 0.70
--   BEHIND_PLAN      actual <  target × 0.70
--   ACHIEVED         actual ≥ target (or ≤ for lower-is-better)
--   DATA_MISSING     actual is null AND data_source is null
--   NOT_STARTED      actual is null AND data_source is set
--
-- Direction-aware: a KPI whose target is lower than the baseline (e.g.
-- "mean time to resolve issues" — lower is better) inverts the
-- comparison automatically. The service determines direction from
-- sign(target - baseline).

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'operational_excellence_kpi' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.operational_excellence_kpi (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT        NOT NULL,
        external_id                     NVARCHAR(64)  NULL,

        cycle_id                        BIGINT        NOT NULL,
        kpi_code                        NVARCHAR(40)  NULL,
        category                        NVARCHAR(60)  NOT NULL,

        indicator_name_en               NVARCHAR(255) NOT NULL,
        indicator_name_ar               NVARCHAR(255) NULL,

        definition_en                   NVARCHAR(MAX) NULL,
        definition_ar                   NVARCHAR(MAX) NULL,

        formula                         NVARCHAR(MAX) NULL,
        data_source                     NVARCHAR(255) NULL,

        baseline                        DECIMAL(20,4) NULL,
        target                          DECIMAL(20,4) NOT NULL,
        actual                          DECIMAL(20,4) NULL,
        unit                            NVARCHAR(40)  NULL,
        frequency                       NVARCHAR(30)  NULL,   -- MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL

        owner_name                      NVARCHAR(255) NULL,
        owner_email                     NVARCHAR(255) NULL,

        -- Computed by the service on save / update-actual.
        status                          NVARCHAR(20)  NOT NULL DEFAULT N'NOT_STARTED',
        trend                           NVARCHAR(20)  NULL,    -- IMPROVING / FLAT / DECLINING — service-set
        last_actual_at                  DATE          NULL,

        evidence_ref                    NVARCHAR(255) NULL,
        gap_id                          BIGINT        NULL,
        corrective_action_id            BIGINT        NULL,

        notes                           NVARCHAR(MAX) NULL,

        is_deleted                      BIT           NOT NULL DEFAULT 0,
        created_at                      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      NVARCHAR(100) NULL,
        updated_at                      DATETIME2     NULL,
        updated_by                      NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_kpi_status CHECK (status IN
            (N'ON_TRACK', N'AT_RISK', N'BEHIND_PLAN', N'ACHIEVED',
             N'DATA_MISSING', N'NOT_STARTED')),
        CONSTRAINT ck_ndi_kpi_trend CHECK (trend IS NULL OR trend IN
            (N'IMPROVING', N'FLAT', N'DECLINING'))
    );

    CREATE INDEX ix_ndi_kpi_cycle          ON ndi.operational_excellence_kpi(cycle_id);
    CREATE INDEX ix_ndi_kpi_cycle_status   ON ndi.operational_excellence_kpi(cycle_id, status);
    CREATE INDEX ix_ndi_kpi_cycle_category ON ndi.operational_excellence_kpi(cycle_id, category);
END
GO

-- ─── Seed — 10 canonical KPIs from the spec ─────────────────────────
-- All tied to the 2025 demo cycle (id=1, organisation_id=1).

IF NOT EXISTS (SELECT 1 FROM ndi.operational_excellence_kpi WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.operational_excellence_kpi (
        organisation_id, cycle_id, kpi_code, category,
        indicator_name_en, indicator_name_ar,
        definition_en, definition_ar, formula, data_source,
        baseline, target, actual, unit, frequency,
        owner_name, status, last_actual_at, notes
    )
    VALUES
    (1, 1, N'OPEX-001', N'Data Catalog Coverage',
     N'Critical data assets catalogued',
     N'الأصول الحرجة المسجّلة في الفهرس',
     N'Percentage of enterprise critical data assets registered in the central catalog with complete metadata.',
     N'نسبة الأصول الحرجة في الجهة المسجّلة في الفهرس المركزي ببيانات وصفية كاملة.',
     N'cataloged_critical_assets / total_critical_assets × 100',
     N'Central data catalog · ETL from CMDB',
     45.00, 90.00, 65.00, N'%', N'QUARTERLY',
     N'Data Catalog Owner', N'BEHIND_PLAN', '2026-03-31',
     N'72.2% of target — below the 90%-of-target ON_TRACK band.'),

    (1, 1, N'OPEX-002', N'Data Stewardship',
     N'Data assets with assigned data owners',
     N'الأصول البيانية المُسنَدة لمالك',
     N'Percentage of registered data assets with a named owner in the catalog.',
     N'نسبة الأصول المسجّلة المُسنَدة لمالك في الفهرس.',
     N'assets_with_owner / total_assets × 100',
     N'Catalog stewardship register',
     50.00, 95.00, 72.00, N'%', N'QUARTERLY',
     N'Data Office Manager', N'BEHIND_PLAN', '2026-03-31',
     N'75.8% of target — assigning the long-tail finance assets in Q2.'),

    (1, 1, N'OPEX-003', N'Data Quality',
     N'Critical DQ rules implemented',
     N'قواعد جودة البيانات الحرجة المُطبَّقة',
     N'Percentage of critical data quality rules implemented in production.',
     N'نسبة قواعد الجودة الحرجة المُطبَّقة في الإنتاج.',
     N'implemented_dq_rules / required_dq_rules × 100',
     N'DQ rule register · DQ monitoring tool',
     30.00, 80.00, 55.00, N'%', N'QUARTERLY',
     N'Data Quality Lead', N'BEHIND_PLAN', '2026-03-31',
     N'68.8% of target — Q2 sprint focuses on customer + transaction-master rules.'),

    (1, 1, N'OPEX-004', N'Metadata',
     N'Metadata completeness',
     N'اكتمال البيانات الوصفية',
     N'Average completeness of metadata fields across catalogued assets.',
     N'متوسط اكتمال حقول البيانات الوصفية عبر الأصول المسجّلة.',
     N'mean(filled_metadata_fields / total_metadata_fields) × 100',
     N'Data catalog metadata report',
     40.00, 85.00, 60.00, N'%', N'QUARTERLY',
     N'Data Catalog Owner', N'BEHIND_PLAN', '2026-03-31',
     N'70.6% of target — bulk-update tool for description fields ships in Q2.'),

    (1, 1, N'OPEX-005', N'Data Sharing',
     N'Data sharing requests processed within SLA',
     N'طلبات مشاركة البيانات ضمن اتفاقية الخدمة',
     N'Percentage of inter-entity data-sharing requests fulfilled within the published SLA window.',
     N'نسبة طلبات مشاركة البيانات بين الجهات التي تم تلبيتها ضمن نافذة اتفاقية الخدمة.',
     N'requests_within_sla / total_requests × 100',
     N'Service-desk ticket export',
     65.00, 90.00, 78.00, N'%', N'MONTHLY',
     N'Data Sharing Officer', N'AT_RISK', '2026-03-31',
     N'86.7% of target — AT_RISK band; on track to hit 90% by end of Q2.'),

    (1, 1, N'OPEX-006', N'Open Data',
     N'Open data publication rate',
     N'معدّل نشر البيانات المفتوحة',
     N'Percentage of eligible open datasets published to the national portal with complete metadata.',
     N'نسبة الأصول المفتوحة المؤهلة المنشورة في البوابة الوطنية ببيانات وصفية كاملة.',
     N'published_eligible_assets / total_eligible_assets × 100',
     N'National open-data portal export',
     20.00, 70.00, 40.00, N'%', N'QUARTERLY',
     N'Open Data Steward', N'BEHIND_PLAN', '2026-03-31',
     N'57.1% of target — backlog of 12 high-value datasets in publication queue.'),

    (1, 1, N'OPEX-007', N'Data Literacy',
     N'Data governance awareness completion',
     N'إكمال برنامج التوعية بالحوكمة',
     N'Percentage of employees completing the mandatory annual data-governance awareness programme.',
     N'نسبة الموظفين الذين أكملوا برنامج التوعية الإلزامي السنوي.',
     N'completed_employees / total_active_employees × 100',
     N'HR training-system completion export',
     70.00, 95.00, 88.00, N'%', N'ANNUAL',
     N'HR / Training Lead', N'AT_RISK', '2026-03-31',
     N'92.6% of target — close to the ON_TRACK band; Q2 reminder campaign planned.'),

    (1, 1, N'OPEX-008', N'Personal Data Protection',
     N'Personal data processing activities documented',
     N'أنشطة معالجة البيانات الشخصية الموثّقة',
     N'Percentage of identified personal-data processing activities documented in the ROPA register (PDPL Art. 18).',
     N'نسبة أنشطة معالجة البيانات الشخصية المُحدَّدة الموثّقة في سجل ROPA (المادة 18).',
     N'documented_activities / identified_activities × 100',
     N'ROPA register · PDPL module',
     40.00, 100.00, 75.00, N'%', N'QUARTERLY',
     N'Data Protection Officer', N'BEHIND_PLAN', '2026-03-31',
     N'75.0% of target — remaining 25% concentrated in legacy HR + procurement systems.'),

    (1, 1, N'OPEX-009', N'Data Classification',
     N'Data classification coverage',
     N'تغطية تصنيف البيانات',
     N'Percentage of enterprise data assets classified per the national classification policy.',
     N'نسبة الأصول البيانية المصنّفة وفق السياسة الوطنية للتصنيف.',
     N'classified_assets / total_assets × 100',
     N'Classification tool + manual register',
     35.00, 90.00, 68.00, N'%', N'QUARTERLY',
     N'Data Classification Lead', N'BEHIND_PLAN', '2026-03-31',
     N'75.6% of target — automated classification for structured stores live; unstructured next.'),

    (1, 1, N'OPEX-010', N'Data Quality',
     N'DQ issue resolution within SLA',
     N'حل مشاكل جودة البيانات ضمن اتفاقية الخدمة',
     N'Percentage of data-quality issues closed within the severity-tier SLA.',
     N'نسبة مشاكل جودة البيانات المُغلَقة ضمن اتفاقية الخدمة حسب الخطورة.',
     N'resolved_within_sla / total_resolved × 100',
     N'DQ ticket export',
     55.00, 85.00, 70.00, N'%', N'MONTHLY',
     N'Data Quality Lead', N'AT_RISK', '2026-03-31',
     N'82.4% of target — within AT_RISK band; staffing increase requested for Q2.');
END
GO

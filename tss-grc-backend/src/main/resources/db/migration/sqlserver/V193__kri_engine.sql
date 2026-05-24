-- V193 — Sprint 13: Key Risk Indicator (KRI) engine.
--
-- Saudi banks today track 30–50 KRIs (failed-login-attempts-per-day,
-- % overdue CAPAs, cross-border-transfer volume, AML-alert aging…).
-- They live in Excel pivot tables refreshed manually quarterly. The
-- result is that "the KRI everyone watches" is always the last one a
-- human had time to refresh — never the one that breached this morning.
--
-- This slice introduces a structured KRI register:
--
--   risk.kri_definition    — one row per KRI: thresholds, frequency,
--                            owner, regulatory anchor
--   risk.kri_measurement   — append-only series of recorded values
--                            with a GREEN/AMBER/RED status snapshot
--   risk.kri_breach        — one row per AMBER/RED breach window
--                            opened/closed by the engine
--
-- The status of a measurement is computed at insert time from the
-- KRI's value_direction + thresholds:
--
--   LOWER_IS_BETTER:  GREEN ≤ green_threshold
--                     AMBER ≤ amber_threshold
--                     RED   > amber_threshold (red_threshold is the
--                                              "screaming red" anchor
--                                              for SLA-style KRIs)
--
--   HIGHER_IS_BETTER: GREEN ≥ green_threshold
--                     AMBER ≥ amber_threshold
--                     RED   < amber_threshold (red_threshold is the
--                                              "floor" — e.g. LCR < 110%)
--
-- Why a separate `kri_measurement` row per evaluation (rather than
-- folding "current value" onto the definition)?
--   1. Trend charting — the sparkline on the detail page needs 90 days
--      of history, not just the latest.
--   2. Audit — when a Saudi regulator asks "what was the % of overdue
--      CAPAs on day X?", the answer is a row read, not a recomputation.
--   3. Replays — a back-fill of historical data can write a series
--      without touching the definition.
--
-- Regulatory anchors:
--   - SAMA Compliance Framework §4.2  — documented KRIs, reviewed
--                                       monthly minimum.
--   - NCA ECC-2 control 2-12          — continuous monitoring of
--                                       cybersecurity posture metrics.
--   - SAMA BCF §3.5                   — recovery-time metrics tracked
--                                       between exercises.
--   - PDPL Articles 4 + 29            — privacy posture (DSR aging,
--                                       breach response time) is a
--                                       KRI input.
--
-- Coexistence with the older `risk.kri_definitions` (plural) table
-- from the Sprint-2 stub: this Sprint-13 slice introduces SINGULAR
-- table names (`kri_definition`, `kri_measurement`, `kri_breach`) and
-- a richer schema (categories, value_direction, source_query_type,
-- audit + ROWVERSION). The older plural tables stay where they are
-- until a future consolidation slice retires them; nothing in this
-- migration touches them.
--
-- Soft-delete + audit + ROWVERSION on all three tables.
-- QUOTED_IDENTIFIER ON for filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'risk') IS NULL EXEC(N'CREATE SCHEMA risk');
GO

-- ─── 1. kri_definition ────────────────────────────────────────────
IF OBJECT_ID(N'risk.kri_definition', N'U') IS NULL
BEGIN
    CREATE TABLE risk.kri_definition (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Tenant-unique short code; the seed picks
        -- KRI-{CATEGORY}-{SHORT}-{NNN} (e.g. KRI-CYBER-FLA-001).
        kri_code                      NVARCHAR(64)  NOT NULL,

        title_en                      NVARCHAR(255) NOT NULL,
        title_ar                      NVARCHAR(255) NOT NULL,
        description_en                NVARCHAR(MAX) NULL,
        description_ar                NVARCHAR(MAX) NULL,

        -- CYBER / OPERATIONAL / CREDIT / MARKET / LIQUIDITY / COMPLIANCE
        -- / REPUTATIONAL / STRATEGIC / AML / PRIVACY / BCM / ESG.
        category                      VARCHAR(40)   NOT NULL,

        -- COUNT / PERCENTAGE / RATIO / CURRENCY_SAR / DAYS / HOURS / BPS.
        measurement_unit              VARCHAR(40)   NOT NULL,

        -- LOWER_IS_BETTER (default — most operational/risk KRIs go
        -- down when the bank is healthy) or HIGHER_IS_BETTER
        -- (capital/liquidity ratios where higher = safer).
        value_direction               VARCHAR(20)   NOT NULL DEFAULT N'LOWER_IS_BETTER',

        -- The four anchors: target (where the board wants it),
        -- green_threshold (still safe), amber_threshold (watch),
        -- red_threshold (escalate). Stored as 18,6 so percentages,
        -- ratios and currency amounts all fit.
        target_value                  DECIMAL(18,6) NULL,
        green_threshold               DECIMAL(18,6) NULL,
        amber_threshold               DECIMAL(18,6) NULL,
        red_threshold                 DECIMAL(18,6) NULL,

        -- DAILY / WEEKLY / MONTHLY / QUARTERLY / ANNUAL.
        frequency                     VARCHAR(20)   NOT NULL DEFAULT N'MONTHLY',

        -- MANUAL: a human keys in the value.
        -- SQL:    the engine runs source_query against the platform DB.
        -- CALLBACK: a Spring bean implements a Supplier<BigDecimal>.
        -- METRIC_API: external metric endpoint (e.g. Prometheus).
        source_query_type             VARCHAR(20)   NOT NULL DEFAULT N'MANUAL',
        source_query                  NVARCHAR(MAX) NULL,

        -- Soft FK to iam.users — KRI owner accountable for monthly
        -- review (SAMA Compliance Framework §4.2 — "documented owner").
        owner_user_id                 BIGINT        NULL,
        -- Soft FK to risk.risk — the parent risk this KRI is a leading
        -- indicator for. NULL when the KRI is a standalone metric
        -- (e.g. capital adequacy).
        linked_risk_id                BIGINT        NULL,

        is_active                     BIT NOT NULL DEFAULT 1,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_kri_def_category CHECK (category IN
            (N'CYBER', N'OPERATIONAL', N'CREDIT', N'MARKET', N'LIQUIDITY',
             N'COMPLIANCE', N'REPUTATIONAL', N'STRATEGIC', N'AML',
             N'PRIVACY', N'BCM', N'ESG')),
        CONSTRAINT CK_kri_def_unit CHECK (measurement_unit IN
            (N'COUNT', N'PERCENTAGE', N'RATIO', N'CURRENCY_SAR',
             N'DAYS', N'HOURS', N'BPS')),
        CONSTRAINT CK_kri_def_direction CHECK (value_direction IN
            (N'LOWER_IS_BETTER', N'HIGHER_IS_BETTER')),
        CONSTRAINT CK_kri_def_frequency CHECK (frequency IN
            (N'DAILY', N'WEEKLY', N'MONTHLY', N'QUARTERLY', N'ANNUAL')),
        CONSTRAINT CK_kri_def_source CHECK (source_query_type IN
            (N'MANUAL', N'SQL', N'CALLBACK', N'METRIC_API'))
    );
END
GO

-- Filtered unique indexes — kri_code unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_kri_def_code' AND object_id = OBJECT_ID(N'risk.kri_definition'))
    CREATE UNIQUE INDEX UQ_kri_def_code ON risk.kri_definition (organisation_id, kri_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_kri_def_external' AND object_id = OBJECT_ID(N'risk.kri_definition'))
    CREATE UNIQUE INDEX UQ_kri_def_external ON risk.kri_definition (external_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_kri_def_category' AND object_id = OBJECT_ID(N'risk.kri_definition'))
    CREATE INDEX IX_kri_def_category ON risk.kri_definition (organisation_id, category) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_kri_def_active' AND object_id = OBJECT_ID(N'risk.kri_definition'))
    CREATE INDEX IX_kri_def_active ON risk.kri_definition (organisation_id, is_active) WHERE is_deleted = 0;
GO

-- ─── 2. kri_measurement ───────────────────────────────────────────
IF OBJECT_ID(N'risk.kri_measurement', N'U') IS NULL
BEGIN
    CREATE TABLE risk.kri_measurement (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        kri_id                        BIGINT NOT NULL,

        -- When the value was *measured* (not when the row was inserted).
        -- Lets the engine ingest historical CSVs without distorting
        -- the trend.
        measured_at                   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        value                         DECIMAL(18,6) NOT NULL,

        -- GREEN / AMBER / RED / NA. NA is for KRIs where the source
        -- could not produce a value (e.g. SQL query returned NULL).
        status                        VARCHAR(10)   NOT NULL DEFAULT N'GREEN',

        -- AUTO_SQL / AUTO_CALLBACK / MANUAL / IMPORTED.
        source                        VARCHAR(40)   NOT NULL DEFAULT N'MANUAL',
        measured_by_user_id           BIGINT        NULL,
        notes_en                      NVARCHAR(1000) NULL,
        notes_ar                      NVARCHAR(1000) NULL,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_kri_meas_def FOREIGN KEY (kri_id)
            REFERENCES risk.kri_definition(id),
        CONSTRAINT CK_kri_meas_status CHECK (status IN
            (N'GREEN', N'AMBER', N'RED', N'NA')),
        CONSTRAINT CK_kri_meas_source CHECK (source IN
            (N'AUTO_SQL', N'AUTO_CALLBACK', N'MANUAL', N'IMPORTED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_kri_meas_external' AND object_id = OBJECT_ID(N'risk.kri_measurement'))
    CREATE UNIQUE INDEX UQ_kri_meas_external ON risk.kri_measurement (external_id) WHERE is_deleted = 0;
GO
-- Hot-path read: "last 90 days of measurements for KRI X".
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_kri_meas_kri_time' AND object_id = OBJECT_ID(N'risk.kri_measurement'))
    CREATE INDEX IX_kri_meas_kri_time ON risk.kri_measurement (organisation_id, kri_id, measured_at DESC) WHERE is_deleted = 0;
GO

-- ─── 3. kri_breach ────────────────────────────────────────────────
IF OBJECT_ID(N'risk.kri_breach', N'U') IS NULL
BEGIN
    CREATE TABLE risk.kri_breach (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        kri_id                        BIGINT NOT NULL,
        -- The measurement that opened the breach. The closing
        -- measurement (the first GREEN) writes closed_at directly on
        -- this row — no separate "closing_measurement_id" field, since
        -- the audit log already carries the closing measurement id in
        -- its payload.
        measurement_id                BIGINT NOT NULL,

        -- AMBER or RED. We open a fresh breach row when an AMBER
        -- breach escalates to RED (the AMBER row is closed and a new
        -- RED row is opened). Keeps the audit committee table tidy.
        breach_level                  VARCHAR(20)   NOT NULL,

        opened_at                     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        closed_at                     DATETIME2(3)  NULL,

        acknowledged_by_user_id       BIGINT        NULL,
        acknowledged_at               DATETIME2(3)  NULL,

        -- Soft FK to platform.issues — auto-opened when a breach has
        -- sustained RED for ≥3 measurements. Nullable while a breach
        -- is still under operator acknowledgement window.
        capa_task_id                  BIGINT        NULL,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_kri_breach_def FOREIGN KEY (kri_id)
            REFERENCES risk.kri_definition(id),
        CONSTRAINT FK_kri_breach_meas FOREIGN KEY (measurement_id)
            REFERENCES risk.kri_measurement(id),
        CONSTRAINT CK_kri_breach_level CHECK (breach_level IN
            (N'AMBER', N'RED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_kri_breach_external' AND object_id = OBJECT_ID(N'risk.kri_breach'))
    CREATE UNIQUE INDEX UQ_kri_breach_external ON risk.kri_breach (external_id) WHERE is_deleted = 0;
GO
-- Filtered index — "open breaches for this tenant". This is the most
-- frequently-read dashboard query.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_kri_breach_open' AND object_id = OBJECT_ID(N'risk.kri_breach'))
    CREATE INDEX IX_kri_breach_open ON risk.kri_breach (organisation_id, kri_id) WHERE closed_at IS NULL AND is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_kri_breach_kri' AND object_id = OBJECT_ID(N'risk.kri_breach'))
    CREATE INDEX IX_kri_breach_kri ON risk.kri_breach (organisation_id, kri_id, opened_at DESC) WHERE is_deleted = 0;
GO

-- ─── 4. Pre-seed 15 KRIs for org=1 ─────────────────────────────────
-- Each row pre-seeded for the seeded TSS-Bank tenant (organisation_id=1).
-- Saudi-native bilingual EN+AR. Idempotent: each INSERT guarded by
-- NOT EXISTS on kri_code.
--
-- Spread:
--   12 categories represented (CYBER twice + AML, COMPLIANCE, PRIVACY,
--   BCM, CREDIT, OPERATIONAL, STRATEGIC, ESG, REPUTATIONAL, LIQUIDITY).
--   The CYBER doubles cover the two distinct sub-domains a CISO
--   actually tracks (front-door auth + patch hygiene).

-- 1. KRI-CYBER-FLA-001 — Failed logins/day. Target ≤50; amber≤200; red>200.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-CYBER-FLA-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-CYBER-FLA-001',
     N'Failed login attempts per day',
     N'محاولات تسجيل الدخول الفاشلة في اليوم',
     N'Aggregated daily count of failed authentication attempts on production identity providers. Spike indicates credential stuffing or brute-force activity.',
     N'إجمالي محاولات المصادقة الفاشلة يومياً على مزوّدات الهوية في بيئة الإنتاج. القفزة المفاجئة دلالةٌ على هجوم حشو بيانات الاعتماد أو تخمين القوة الغاشمة.',
     N'CYBER', N'COUNT', N'LOWER_IS_BETTER',
     50, 50, 200, 200,
     N'DAILY', N'SQL', 1);

-- 2. KRI-CYBER-PATCH-001 — % endpoints unpatched >30d. Target ≤2%.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-CYBER-PATCH-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-CYBER-PATCH-001',
     N'Endpoints unpatched beyond 30 days (%)',
     N'نسبة الأجهزة الطرفية غير المُحدَّثة لأكثر من 30 يوماً',
     N'Share of managed endpoints whose latest OS / security patch is older than 30 days. NCA ECC-2 control 2-12 anchor for patch hygiene.',
     N'نسبة الأجهزة الطرفية المُدارة التي تأخّر آخر تحديث أمني أو لنظام التشغيل لديها أكثر من 30 يوماً. مرجعها ضابط 2-12 من ECC-2 الخاص بنظافة الترقيع.',
     N'CYBER', N'PERCENTAGE', N'LOWER_IS_BETTER',
     2, 2, 5, 10,
     N'WEEKLY', N'SQL', 1);

-- 3. KRI-AML-AGE-001 — AML alert avg aging days. Target ≤7d.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-AML-AGE-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-AML-AGE-001',
     N'AML alert average aging (days)',
     N'متوسط تقادم تنبيهات مكافحة غسل الأموال (بالأيام)',
     N'Average open-age of AML alerts in the queue. SAMA AML guidelines expect timely disposition; a rising number signals capacity strain.',
     N'متوسط مدّة بقاء تنبيهات مكافحة غسل الأموال في الطابور قبل المعالجة. تطلب إرشادات ساما البتّ السريع، وارتفاع الرقم مؤشّر على ضغط على الفريق.',
     N'AML', N'DAYS', N'LOWER_IS_BETTER',
     7, 7, 14, 21,
     N'WEEKLY', N'SQL', 1);

-- 4. KRI-CAPA-OVERDUE-001 — % CAPA overdue. Target ≤5%.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-CAPA-OVERDUE-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-CAPA-OVERDUE-001',
     N'Overdue corrective actions (%)',
     N'نسبة الإجراءات التصحيحية المتأخّرة',
     N'Share of corrective and preventive actions past their committed completion date. The audit committee opens every quarter with this number.',
     N'نسبة إجراءات المعالجة التصحيحية والوقائية التي تجاوزت موعد الإغلاق المُلتزَم به. تفتتح لجنة المراجعة كل اجتماع ربعي بهذا الرقم.',
     N'COMPLIANCE', N'PERCENTAGE', N'LOWER_IS_BETTER',
     5, 5, 10, 20,
     N'MONTHLY', N'SQL', 1);

-- 5. KRI-PDPL-DSR-AGE-001 — Avg DSR response days. Target ≤14d; red >25d (PDPL Art.4 30d SLA).
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-PDPL-DSR-AGE-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-PDPL-DSR-AGE-001',
     N'Average data-subject request response (days)',
     N'متوسط زمن الردّ على طلبات أصحاب البيانات (أيام)',
     N'Average elapsed days from DSR receipt to final response. PDPL Article 4 caps individual DSRs at 30 days; the amber/red thresholds bite before the regulator does.',
     N'متوسط الأيام من استلام طلب صاحب البيانات حتى الردّ النهائي. تحدّد المادة 4 من نظام حماية البيانات الشخصية مهلة 30 يوماً، ويُطلَق التنبيه قبل بلوغها بوقت كافٍ.',
     N'PRIVACY', N'DAYS', N'LOWER_IS_BETTER',
     14, 14, 25, 30,
     N'WEEKLY', N'SQL', 1);

-- 6. KRI-BCM-EXERCISE-001 — Months since last full-scale BCM exercise. Target ≤6m, red >12m.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-BCM-EXERCISE-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-BCM-EXERCISE-001',
     N'Months since last full-scale BCM exercise',
     N'عدد الأشهر منذ آخر تمرين شامل لاستمرارية الأعمال',
     N'Elapsed months since the last full-scale BCM exercise on a critical service. SAMA BCF Annex C expects no more than 12 months between full-scale runs.',
     N'عدد الأشهر منذ آخر تمرين شامل لاستمرارية الأعمال على خدمة حرجة. يَفترض ملحق ج من إطار ساما ألا تتجاوز الفترة بين التمرينين الشاملين 12 شهراً.',
     N'BCM', N'COUNT', N'LOWER_IS_BETTER',
     6, 6, 9, 12,
     N'MONTHLY', N'CALLBACK', 1);

-- 7. KRI-CREDIT-NPL-001 — NPL ratio. Target ≤2%.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-CREDIT-NPL-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-CREDIT-NPL-001',
     N'Non-performing loan ratio (%)',
     N'نسبة القروض المتعثّرة',
     N'Gross NPLs divided by total loan book. SAMA Compliance Framework §4.2 cites NPL ratio as a board-level leading indicator of credit-cycle stress.',
     N'إجمالي القروض المتعثّرة مقسوماً على إجمالي محفظة القروض. يستشهد إطار ساما للامتثال §4.2 بنسبة القروض المتعثّرة كمؤشّر قيادي على المستوى التنفيذي لقياس إجهاد الدورة الائتمانية.',
     N'CREDIT', N'PERCENTAGE', N'LOWER_IS_BETTER',
     2, 2, 4, 6,
     N'MONTHLY', N'SQL', 1);

-- 8. KRI-OPS-INCIDENT-COUNT-001 — Ops incidents/quarter. Target ≤10.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-OPS-INCIDENT-COUNT-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-OPS-INCIDENT-COUNT-001',
     N'Operational incidents per quarter',
     N'عدد الحوادث التشغيلية في الربع',
     N'Count of operational risk events recorded against the loss-event taxonomy in a 90-day window. Rising count flags emerging operational fragility.',
     N'عدد أحداث المخاطر التشغيلية المُسجَّلة وفق تصنيف خسائر التشغيل خلال 90 يوماً. ارتفاع العدد دلالة على هشاشة تشغيلية ناشئة.',
     N'OPERATIONAL', N'COUNT', N'LOWER_IS_BETTER',
     10, 10, 20, 30,
     N'QUARTERLY', N'SQL', 1);

-- 9. KRI-VENDOR-RTO-EXCEED-001 — Vendors with attested RTO > contractual. Target=0.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-VENDOR-RTO-EXCEED-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-VENDOR-RTO-EXCEED-001',
     N'Vendors with attested RTO exceeding contractual RTO',
     N'عدد الموردين الذين تجاوز هدف زمن التعافي المُقَرّ لديهم قيمة العقد',
     N'Count of critical vendors whose most-recent BCP attestation reported an RTO longer than the contractual commitment. SAMA Outsourcing Rules require parity between the two.',
     N'عدد الموردين الحرجين الذين أعلن آخر تصديقٍ لخطّتهم لاستمرارية الأعمال عن زمن تعافٍ يتجاوز ما نصّ عليه العقد. تشترط ضوابط التعهيد لساما المُماثلة بين القيمتين.',
     N'STRATEGIC', N'COUNT', N'LOWER_IS_BETTER',
     0, 0, 2, 5,
     N'MONTHLY', N'SQL', 1);

-- 10. KRI-AI-DRIFT-001 — AI models with unack drift. Target=0.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-AI-DRIFT-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-AI-DRIFT-001',
     N'AI models with unacknowledged drift',
     N'نماذج الذكاء الاصطناعي ذات الانحراف غير المُعتَمَد',
     N'Count of production AI models whose drift detector has flagged data or concept drift without an owner acknowledgement. SDAIA AI Ethics 2023 anchors mandatory drift review.',
     N'عدد نماذج الذكاء الاصطناعي الإنتاجية التي رصد كاشف الانحراف لديها انحرافاً في البيانات أو في المفاهيم دون إقرار من المالك. مرجعها وثيقة أخلاقيات الذكاء الاصطناعي من سدايا 2023.',
     N'OPERATIONAL', N'COUNT', N'LOWER_IS_BETTER',
     0, 0, 1, 3,
     N'WEEKLY', N'CALLBACK', 1);

-- 11. KRI-ESG-EMISSIONS-001 — Scope-3 emissions YoY % change. Target ≤-3%.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-ESG-EMISSIONS-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-ESG-EMISSIONS-001',
     N'Scope-3 financed emissions YoY change (%)',
     N'التغيّر السنوي في الانبعاثات المُموَّلة — النطاق الثالث',
     N'Year-on-year percentage change in PCAF-attributed scope-3 financed emissions. SAMA Sustainable Finance Roadmap expects a negative trajectory aligned with national NDC.',
     N'التغيّر السنوي في الانبعاثات المُموَّلة المنسوبة وفق منهجية PCAF ضمن النطاق الثالث. تتوقّع خارطة طريق التمويل المستدام لساما مساراً تنازلياً متّسقاً مع المساهمة الوطنية.',
     N'ESG', N'PERCENTAGE', N'LOWER_IS_BETTER',
     -3, -3, 0, 3,
     N'ANNUAL', N'CALLBACK', 1);

-- 12. KRI-REG-FINES-001 — Regulatory fines/quarter. Target=0.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-REG-FINES-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-REG-FINES-001',
     N'Regulatory fines per quarter (SAR)',
     N'الغرامات التنظيمية في الربع (بالريال السعودي)',
     N'Sum of regulatory fines levied by SAMA / NCA / SDAIA / CMA / ZATCA in a 90-day window. Any non-zero quarter is reportable to the audit committee.',
     N'مجموع الغرامات التنظيمية الصادرة عن ساما والهيئة الوطنية للأمن السيبراني وسدايا والسوق المالية وهيئة الزكاة خلال 90 يوماً. أيّ مبلغ غير صفري يستوجب الرفع إلى لجنة المراجعة.',
     N'COMPLIANCE', N'CURRENCY_SAR', N'LOWER_IS_BETTER',
     0, 0, 100000, 500000,
     N'QUARTERLY', N'SQL', 1);

-- 13. KRI-COMPLIANCE-OBLIG-001 — % obligations overdue. Target ≤2%.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-COMPLIANCE-OBLIG-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-COMPLIANCE-OBLIG-001',
     N'Regulatory obligations overdue (%)',
     N'نسبة الالتزامات التنظيمية المتأخّرة',
     N'Share of obligations in the register whose next attestation is past due. Direct measure of SAMA Compliance Framework §3 attestation discipline.',
     N'نسبة الالتزامات في سجل الالتزامات التي تجاوز موعد التصديق المقبل لها. مقياس مباشر لانضباط التصديقات وفق إطار ساما للامتثال §3.',
     N'COMPLIANCE', N'PERCENTAGE', N'LOWER_IS_BETTER',
     2, 2, 5, 10,
     N'MONTHLY', N'SQL', 1);

-- 14. KRI-CYBER-MTTR-001 — MTTR P1 cyber incidents — hours. Target ≤4h, red >24h.
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-CYBER-MTTR-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-CYBER-MTTR-001',
     N'Mean time to resolve P1 cyber incidents (hours)',
     N'متوسط زمن إغلاق الحوادث السيبرانية من المستوى الأول (بالساعات)',
     N'Mean elapsed hours from declaration to closure for P1 cyber incidents. SAMA CSF expects sub-day resolution for tier-1 banks on highest-severity events.',
     N'متوسط الساعات من إعلان الحادث السيبراني من المستوى الأول حتى إغلاقه. يَفترض الإطار السيبراني لساما إغلاقاً دون يومٍ كاملٍ في البنوك من الفئة الأولى للحوادث الأعلى خطورة.',
     N'CYBER', N'HOURS', N'LOWER_IS_BETTER',
     4, 4, 12, 24,
     N'MONTHLY', N'SQL', 1);

-- 15. KRI-LIQUIDITY-LCR-001 — LCR %. Target ≥130%, red <110% (HIGHER_IS_BETTER).
IF NOT EXISTS (SELECT 1 FROM risk.kri_definition WHERE organisation_id = 1 AND kri_code = N'KRI-LIQUIDITY-LCR-001' AND is_deleted = 0)
INSERT INTO risk.kri_definition
    (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
     category, measurement_unit, value_direction,
     target_value, green_threshold, amber_threshold, red_threshold,
     frequency, source_query_type, is_active)
VALUES
    (1, N'KRI-LIQUIDITY-LCR-001',
     N'Liquidity Coverage Ratio (%)',
     N'نسبة تغطية السيولة',
     N'Stock of high-quality liquid assets divided by 30-day net cash outflows under stress. SAMA Basel III implementation requires LCR ≥100%; the bank''s internal floor is 130% with a 110% screaming-red anchor.',
     N'مخزون الأصول السائلة عالية الجودة مقسوماً على صافي التدفقات النقدية لمدة 30 يوماً في سيناريو الإجهاد. تطلب ساما تطبيق بازل III بنسبة لا تقل عن 100%، والحدّ الداخلي للبنك 130% مع مرساة حمراء عند 110%.',
     N'LIQUIDITY', N'PERCENTAGE', N'HIGHER_IS_BETTER',
     130, 130, 120, 110,
     N'DAILY', N'SQL', 1);

GO

PRINT N'V193 — KRI engine: 3 tables created (kri_definition, kri_measurement, kri_breach) + 15 KRIs seeded for org=1.';
GO

-- ============================================================================
-- TSS GRC — ERM W3: KRI threshold engine + Risk appetite vs. exposure
-- ============================================================================
-- Buyer-assessment §5.1 ERM remainder:
--   #5 KRI threshold engine          — kri_definitions + kri_readings +
--                                      kri_breach_events with green/amber/red
--                                      bands and auto-breach generation
--   #6 Risk appetite vs. exposure    — risk_appetite_statements +
--                                      appetite_breach_events tied to
--                                      categories or top-level risks
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── KRI definitions ────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='risk' AND t.name='kri_definitions')
BEGIN
    CREATE TABLE risk.kri_definitions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_kri_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        risk_id            BIGINT NULL,                  -- optional FK to risk.risks
        category_id        BIGINT NULL,                  -- optional FK to risk.risk_categories
        code               NVARCHAR(50) NULL,            -- e.g. KRI-CYBER-001
        name_en            NVARCHAR(255) NOT NULL,
        name_ar            NVARCHAR(255) NULL,
        description_en     NVARCHAR(MAX) NULL,
        description_ar     NVARCHAR(MAX) NULL,
        unit               NVARCHAR(40) NULL,            -- %, $, count, days, …
        direction          NVARCHAR(20) NOT NULL CONSTRAINT DF_kri_direction DEFAULT N'HIGHER_IS_WORSE',
        -- HIGHER_IS_WORSE  → reading >= amber/red triggers
        -- LOWER_IS_WORSE   → reading <= amber/red triggers
        green_max          DECIMAL(18,4) NULL,           -- HIGHER_IS_WORSE: reading <= green_max → green
        amber_max          DECIMAL(18,4) NULL,           -- HIGHER_IS_WORSE: green_max < reading <= amber_max → amber
        -- For LOWER_IS_WORSE: green if >= green_max, amber if amber_max < reading < green_max, red if <= amber_max
        target_value       DECIMAL(18,4) NULL,
        frequency          NVARCHAR(40) NULL,            -- DAILY, WEEKLY, MONTHLY, QUARTERLY, AD_HOC
        owner              NVARCHAR(255) NULL,
        data_source        NVARCHAR(500) NULL,           -- system / dashboard URL / API endpoint
        framework          NVARCHAR(100) NULL,           -- ISO 31000, COSO ERM, NCA, SAMA, …
        is_active          BIT NOT NULL CONSTRAINT DF_kri_active DEFAULT 1,
        is_deleted         BIT NOT NULL CONSTRAINT DF_kri_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_kri_created DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2 NULL,
        created_by         BIGINT NULL,
        updated_by         BIGINT NULL
    );
    CREATE INDEX IX_kri_org_risk ON risk.kri_definitions(organisation_id, risk_id) WHERE is_deleted = 0;
    PRINT N'Created risk.kri_definitions';
END
ELSE PRINT N'risk.kri_definitions already exists';

-- ── KRI readings (time-series) ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='risk' AND t.name='kri_readings')
BEGIN
    CREATE TABLE risk.kri_readings (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        kri_id             BIGINT NOT NULL,
        reading_value      DECIMAL(18,4) NOT NULL,
        reading_at         DATETIME2 NOT NULL CONSTRAINT DF_krir_at DEFAULT SYSUTCDATETIME(),
        band               NVARCHAR(10) NULL,            -- GREEN | AMBER | RED (snapshot of band when recorded)
        notes              NVARCHAR(MAX) NULL,
        recorded_by        BIGINT NULL,
        CONSTRAINT FK_krir_kri FOREIGN KEY (kri_id) REFERENCES risk.kri_definitions(id)
    );
    CREATE INDEX IX_krir_kri_at ON risk.kri_readings(kri_id, reading_at DESC);
    PRINT N'Created risk.kri_readings';
END
ELSE PRINT N'risk.kri_readings already exists';

-- ── KRI breach events ───────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='risk' AND t.name='kri_breach_events')
BEGIN
    CREATE TABLE risk.kri_breach_events (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        kri_id             BIGINT NOT NULL,
        reading_id         BIGINT NOT NULL,
        breach_band        NVARCHAR(10) NOT NULL,        -- AMBER | RED
        reading_value      DECIMAL(18,4) NOT NULL,
        threshold_crossed  DECIMAL(18,4) NOT NULL,
        breached_at        DATETIME2 NOT NULL CONSTRAINT DF_krib_at DEFAULT SYSUTCDATETIME(),
        acknowledged_at    DATETIME2 NULL,
        acknowledged_by_id BIGINT NULL,
        resolved_at        DATETIME2 NULL,
        resolution_notes   NVARCHAR(MAX) NULL,
        status             NVARCHAR(20) NOT NULL CONSTRAINT DF_krib_status DEFAULT N'OPEN',  -- OPEN | ACKNOWLEDGED | RESOLVED
        CONSTRAINT FK_krib_kri FOREIGN KEY (kri_id) REFERENCES risk.kri_definitions(id),
        CONSTRAINT FK_krib_reading FOREIGN KEY (reading_id) REFERENCES risk.kri_readings(id)
    );
    CREATE INDEX IX_krib_kri_status ON risk.kri_breach_events(kri_id, status);
    PRINT N'Created risk.kri_breach_events';
END
ELSE PRINT N'risk.kri_breach_events already exists';

-- ── Risk appetite statements ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='risk' AND t.name='risk_appetite_statements')
BEGIN
    CREATE TABLE risk.risk_appetite_statements (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_rapp_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        category_id        BIGINT NULL,                  -- optional category scope
        title_en           NVARCHAR(500) NOT NULL,
        title_ar           NVARCHAR(500) NULL,
        statement_en       NVARCHAR(MAX) NULL,
        statement_ar       NVARCHAR(MAX) NULL,
        risk_dimension     NVARCHAR(60) NOT NULL,        -- STRATEGIC | OPERATIONAL | FINANCIAL | COMPLIANCE | CYBER | REPUTATION | PRIVACY | THIRD_PARTY
        appetite_level     NVARCHAR(20) NOT NULL,        -- AVERSE | MINIMAL | CAUTIOUS | OPEN | HUNGRY
        tolerance_lower    DECIMAL(18,4) NULL,           -- accepted exposure band (low end)
        tolerance_upper    DECIMAL(18,4) NULL,           -- accepted exposure band (high end)
        unit               NVARCHAR(40) NULL,            -- %, $, count, score
        current_exposure   DECIMAL(18,4) NULL,
        approved_by        NVARCHAR(255) NULL,
        approved_at        DATE NULL,
        next_review_date   DATE NULL,
        status             NVARCHAR(30) NOT NULL CONSTRAINT DF_rapp_status DEFAULT N'DRAFT',  -- DRAFT | APPROVED | UNDER_REVIEW | RETIRED
        is_deleted         BIT NOT NULL CONSTRAINT DF_rapp_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_rapp_created DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2 NULL
    );
    CREATE INDEX IX_rapp_org ON risk.risk_appetite_statements(organisation_id) WHERE is_deleted = 0;
    PRINT N'Created risk.risk_appetite_statements';
END
ELSE PRINT N'risk.risk_appetite_statements already exists';

-- ── Appetite breach events ──────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='risk' AND t.name='appetite_breach_events')
BEGIN
    CREATE TABLE risk.appetite_breach_events (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        appetite_id        BIGINT NOT NULL,
        observed_value     DECIMAL(18,4) NOT NULL,
        breach_severity    NVARCHAR(20) NOT NULL,        -- WITHIN_TOLERANCE | EXCEEDED | CRITICAL
        breached_at        DATETIME2 NOT NULL CONSTRAINT DF_appb_at DEFAULT SYSUTCDATETIME(),
        notes              NVARCHAR(MAX) NULL,
        acknowledged_at    DATETIME2 NULL,
        acknowledged_by_id BIGINT NULL,
        status             NVARCHAR(20) NOT NULL CONSTRAINT DF_appb_status DEFAULT N'OPEN',
        CONSTRAINT FK_appb_app FOREIGN KEY (appetite_id) REFERENCES risk.risk_appetite_statements(id)
    );
    CREATE INDEX IX_appb_app_status ON risk.appetite_breach_events(appetite_id, status);
    PRINT N'Created risk.appetite_breach_events';
END
ELSE PRINT N'risk.appetite_breach_events already exists';

-- ── Seed: 5 representative KRIs ─────────────────────────────────────────────
INSERT INTO risk.kri_definitions
  (organisation_id, code, name_en, name_ar, description_en, unit, direction,
   green_max, amber_max, target_value, frequency, owner, data_source, framework)
SELECT v.org, v.code, v.en, v.ar, v.dsc, v.u, v.dir, v.gm, v.am, v.tv, v.fq, v.own, v.ds, v.fw
FROM (VALUES
  (1, N'KRI-CYB-001',
   N'Critical vulnerabilities open > 30 days',
   N'الثغرات الحرجة المفتوحة لأكثر من 30 يومًا',
   N'Number of critical-severity vulnerabilities that have been open beyond the 30-day SLA defined in the patch management policy.',
   N'count', N'HIGHER_IS_WORSE',  CAST(0 AS DECIMAL(18,4)), CAST(2 AS DECIMAL(18,4)), CAST(0 AS DECIMAL(18,4)), N'WEEKLY',
   N'CISO Office', N'Tenable Vulnerability Management dashboard', N'NCA ECC v2.0 §2-12-3'),
  (1, N'KRI-CYB-002',
   N'Phishing simulation click-through rate',
   N'معدل النقر في محاكاة التصيد',
   N'Percentage of employees who click on phishing simulation emails — leading indicator of awareness program effectiveness.',
   N'%', N'HIGHER_IS_WORSE', CAST(8 AS DECIMAL(18,4)), CAST(15 AS DECIMAL(18,4)), CAST(5 AS DECIMAL(18,4)), N'MONTHLY',
   N'Security Awareness Lead', N'KnowBe4 phishing campaign reports', N'NCA ECC v2.0 §2-13-3'),
  (1, N'KRI-OPS-001',
   N'Unplanned production downtime — minutes / month',
   N'الانقطاعات غير المخططة للإنتاج — دقائق/شهر',
   N'Total minutes of unplanned production system unavailability per calendar month.',
   N'minutes', N'HIGHER_IS_WORSE', CAST(60 AS DECIMAL(18,4)), CAST(180 AS DECIMAL(18,4)), CAST(30 AS DECIMAL(18,4)), N'MONTHLY',
   N'Head of IT Operations', N'Datadog SLO board · grafana.internal/prod-slo', N'ISO 22301'),
  (1, N'KRI-CMP-001',
   N'Overdue regulatory filings (ZATCA, SAMA)',
   N'التقارير التنظيمية المتأخرة (هيئة الزكاة، البنك المركزي)',
   N'Count of mandatory regulatory submissions past their due date.',
   N'count', N'HIGHER_IS_WORSE', CAST(0 AS DECIMAL(18,4)), CAST(1 AS DECIMAL(18,4)), CAST(0 AS DECIMAL(18,4)), N'WEEKLY',
   N'Group Compliance Officer', N'Compliance calendar — internal', N'ZATCA + SAMA quarterly returns'),
  (1, N'KRI-PRV-001',
   N'PDPL DSR responses overdue (>30 days)',
   N'طلبات أصحاب البيانات المتأخرة (>30 يومًا)',
   N'Number of Data Subject Rights requests still open beyond the 30-day PDPL Implementing Regulations SLA.',
   N'count', N'HIGHER_IS_WORSE', CAST(0 AS DECIMAL(18,4)), CAST(2 AS DECIMAL(18,4)), CAST(0 AS DECIMAL(18,4)), N'WEEKLY',
   N'Data Protection Officer', N'TSS GRC privacy module', N'SDAIA PDPL Implementing Regulations Art. 7')
) AS v(org, code, en, ar, dsc, u, dir, gm, am, tv, fq, own, ds, fw)
WHERE NOT EXISTS (SELECT 1 FROM risk.kri_definitions k WHERE k.organisation_id=v.org AND k.code=v.code AND k.is_deleted=0);

PRINT N'Seeded 5 KRIs.';

-- ── Seed: 4 representative appetite statements ──────────────────────────────
INSERT INTO risk.risk_appetite_statements
  (organisation_id, title_en, title_ar, statement_en, statement_ar,
   risk_dimension, appetite_level, tolerance_lower, tolerance_upper, unit,
   current_exposure, approved_by, approved_at, next_review_date, status)
SELECT v.org, v.en, v.ar, v.sten, v.star, v.dim, v.lvl, v.tl, v.tu, v.u,
       v.cur, v.appby, v.appat, v.next, v.st
FROM (VALUES
  (1, N'Cyber & Information Security Appetite',
      N'بيان قابلية المخاطر السيبرانية وأمن المعلومات',
      N'The Group has minimal appetite for cyber and information security risk. We will not knowingly accept any risk that compromises customer data confidentiality or our ISO 27001 / NCA ECC compliance posture.',
      N'لدى المجموعة قابلية ضئيلة لمخاطر الأمن السيبراني وأمن المعلومات. لا نقبل عن سابق علم أي مخاطرة تخل بسرية بيانات العملاء أو امتثالنا لـ ISO 27001 / الضوابط الأساسية للأمن السيبراني.',
      N'CYBER', N'MINIMAL', CAST(0 AS DECIMAL(18,4)), CAST(15 AS DECIMAL(18,4)), N'risk score',
      CAST(12 AS DECIMAL(18,4)), N'Board Risk Committee', '2025-01-20', '2026-01-20', N'APPROVED'),
  (1, N'Operational Resilience Appetite',
      N'بيان قابلية مخاطر المرونة التشغيلية',
      N'Operational disruption tolerance is set at 4 hours RTO for tier-1 services and 24 hours for tier-2. We will not accept exposure beyond these limits without a documented compensating control.',
      N'تتحدد قدرتنا على تحمل الانقطاع التشغيلي بـ 4 ساعات هدفًا لاستئناف الخدمات من المستوى الأول و 24 ساعة للمستوى الثاني.',
      N'OPERATIONAL', N'CAUTIOUS', CAST(0 AS DECIMAL(18,4)), CAST(20 AS DECIMAL(18,4)), N'risk score',
      CAST(18 AS DECIMAL(18,4)), N'Executive Risk Committee', '2025-02-15', '2026-02-15', N'APPROVED'),
  (1, N'Compliance & Regulatory Appetite',
      N'بيان قابلية مخاطر الامتثال والتنظيم',
      N'Zero appetite for regulatory non-compliance with NCA, SAMA, SDAIA and ZATCA mandates. All findings must be remediated within the regulator-specified window.',
      N'لا نقبل عدم الامتثال للجهات التنظيمية كهيئة الأمن السيبراني والبنك المركزي وهيئة البيانات وهيئة الزكاة.',
      N'COMPLIANCE', N'AVERSE', CAST(0 AS DECIMAL(18,4)), CAST(5 AS DECIMAL(18,4)), N'risk score',
      CAST(7 AS DECIMAL(18,4)), N'Board Audit Committee', '2025-01-15', '2026-01-15', N'APPROVED'),
  (1, N'Strategic Investment Appetite',
      N'بيان قابلية مخاطر الاستثمارات الاستراتيجية',
      N'Open to strategic investments aligned with Vision 2030 enabling sectors. Tolerance for new-market risk is limited to 12% of group EBITDA.',
      N'لدينا انفتاح على الاستثمارات الاستراتيجية المتوافقة مع رؤية 2030 ضمن حد 12٪ من الأرباح قبل الاستهلاك.',
      N'STRATEGIC', N'OPEN', CAST(0 AS DECIMAL(18,4)), CAST(35 AS DECIMAL(18,4)), N'risk score',
      CAST(22 AS DECIMAL(18,4)), N'Board of Directors', '2025-01-10', '2026-01-10', N'APPROVED')
) AS v(org, en, ar, sten, star, dim, lvl, tl, tu, u, cur, appby, appat, next, st)
WHERE NOT EXISTS (SELECT 1 FROM risk.risk_appetite_statements s WHERE s.organisation_id=v.org AND s.title_en=v.en AND s.is_deleted=0);

PRINT N'Seeded 4 appetite statements.';

-- ── Result summary ────────────────────────────────────────────────────────
SELECT
    N'ERM W3 — KRI + Appetite DDL complete' AS status,
    (SELECT COUNT(*) FROM risk.kri_definitions WHERE is_deleted=0) AS kris,
    (SELECT COUNT(*) FROM risk.risk_appetite_statements WHERE is_deleted=0) AS appetites;

GO

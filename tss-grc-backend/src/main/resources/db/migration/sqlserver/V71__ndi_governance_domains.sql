-- V71 — NDI Data Governance Domains (slice 6)
--
-- Master catalog of the data-governance domains that organise the NDI
-- assessment. Each underlying assessment row (maturity / compliance /
-- opex) already carries a free-text domain (category for opex) — this
-- catalog gives those strings a stable code, bilingual name, and a
-- weight so the service can roll up a per-domain score and combine
-- them into an overall NDI index using the weighted formula:
--
--   overall_ndi_pct = Σ ( domain_score_pct × domain_weight ) / Σ weight
--
-- Per-domain score is itself a weighted blend of its three components:
--
--   domain_score_pct
--     = (maturity_avg / 5.0 × 100 × weight_maturity)
--     + (compliance_pct                * weight_compliance)
--     + (opex_score_pct                * weight_opex)
--   where the three component weights sum to 1.0 per domain.
--
-- The `domain_aliases` column holds a pipe-separated list of the exact
-- strings that the service should treat as belonging to this domain
-- when scanning maturity_assessment.domain, compliance_assessment.domain
-- and operational_excellence_kpi.category. Aliases are case-sensitive
-- on disk; the matcher trims and compares case-insensitively.
--
-- Domains seeded here are PLACEHOLDER until an official SDAIA/NDI
-- domain library is imported. UI surfaces a bilingual banner on every
-- row where is_placeholder = 1.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'governance_domain' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.governance_domain (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        code                NVARCHAR(40)  NOT NULL,
        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        -- Pipe-separated aliases matched against upstream domain / category strings.
        domain_aliases      NVARCHAR(MAX) NULL,

        -- Domain weight in the overall NDI index (0..100, totals normalised at rollup time).
        weight              DECIMAL(6,2)  NOT NULL DEFAULT 10.00,
        sort_order          INT           NOT NULL DEFAULT 100,

        -- Per-domain component blend. Defaults emphasise compliance (40%) and
        -- maturity (35%) since opex KPIs are still maturing in many domains.
        weight_maturity     DECIMAL(5,4)  NOT NULL DEFAULT 0.3500,
        weight_compliance   DECIMAL(5,4)  NOT NULL DEFAULT 0.4000,
        weight_opex         DECIMAL(5,4)  NOT NULL DEFAULT 0.2500,

        owner_name          NVARCHAR(255) NULL,
        owner_email         NVARCHAR(255) NULL,

        is_active           BIT           NOT NULL DEFAULT 1,
        is_placeholder      BIT           NOT NULL DEFAULT 1,

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_domain_weight_components
            CHECK (ABS((weight_maturity + weight_compliance + weight_opex) - 1.0000) < 0.0050)
    );

    CREATE INDEX ix_ndi_domain_org    ON ndi.governance_domain(organisation_id);
    CREATE UNIQUE INDEX ux_ndi_domain_org_code
        ON ndi.governance_domain(organisation_id, code) WHERE is_deleted = 0;
END
GO

-- ─── Seed — 8 placeholder governance domains ────────────────────────
-- Aliases reflect the actual domain / category strings already used by
-- slices 2 (framework), 3 (maturity), 4 (compliance), 5 (opex).

IF NOT EXISTS (SELECT 1 FROM ndi.governance_domain WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.governance_domain (
        organisation_id, code, name_en, name_ar,
        description_en, description_ar,
        domain_aliases,
        weight, sort_order,
        weight_maturity, weight_compliance, weight_opex,
        owner_name, is_placeholder, notes
    )
    VALUES
    (1, N'GOV', N'Data Governance & Management', N'حوكمة وإدارة البيانات',
     N'Roles, policies, decision rights and operating model for enterprise data.',
     N'الأدوار والسياسات وصلاحيات اتخاذ القرار والنموذج التشغيلي لبيانات الجهة.',
     N'Data Governance|Data Stewardship|Data Literacy|Data Literacy and Awareness',
     15.00, 10, 0.4000, 0.4000, 0.2000,
     N'Chief Data Officer', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'CAT', N'Data Catalog & Metadata', N'الفهرس والبيانات الوصفية',
     N'Discoverability, inventory and metadata completeness of enterprise critical data assets.',
     N'إمكانية الاكتشاف وقائمة الأصول واكتمال البيانات الوصفية للأصول الحرجة.',
     N'Data Catalog and Metadata|Data Catalog Coverage|Metadata|Master and Reference Data',
     15.00, 20, 0.3500, 0.4000, 0.2500,
     N'Data Catalog Owner', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'QUA', N'Data Quality', N'جودة البيانات',
     N'Rules, monitoring and remediation of data accuracy, completeness and timeliness.',
     N'قواعد ومراقبة ومعالجة دقة واكتمال وتوقيت البيانات.',
     N'Data Quality|Data Lifecycle Management',
     15.00, 30, 0.3000, 0.3500, 0.3500,
     N'Data Quality Lead', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'SHA', N'Data Sharing', N'مشاركة البيانات',
     N'Inter-entity data sharing agreements, requests and SLA fulfilment.',
     N'اتفاقيات وطلبات مشاركة البيانات بين الجهات والتزام اتفاقيات الخدمة.',
     N'Data Sharing',
     12.50, 40, 0.3000, 0.4500, 0.2500,
     N'Data Sharing Officer', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'OPEN', N'Open Data', N'البيانات المفتوحة',
     N'Identification, publication and currency of open datasets on the national portal.',
     N'تحديد ونشر وتحديث البيانات المفتوحة في البوابة الوطنية.',
     N'Open Data',
     10.00, 50, 0.3000, 0.4000, 0.3000,
     N'Open Data Steward', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'PDP', N'Personal Data Protection', N'حماية البيانات الشخصية',
     N'PDPL alignment — ROPA, lawful basis, data-subject rights, cross-border transfers.',
     N'الالتزام بنظام حماية البيانات الشخصية — السجلات، الأساس القانوني، حقوق أصحاب البيانات، النقل العابر للحدود.',
     N'Personal Data Protection',
     15.00, 60, 0.3000, 0.5000, 0.2000,
     N'Data Protection Officer', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'SEC', N'Data Security', N'أمن البيانات',
     N'Technical and organisational safeguards protecting confidentiality, integrity and availability.',
     N'الضوابط التقنية والتنظيمية لحماية سرية وسلامة وتوافر البيانات.',
     N'Data Security',
     10.00, 70, 0.3500, 0.4500, 0.2000,
     N'CISO', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import'),

    (1, N'CLS', N'Data Classification', N'تصنيف البيانات',
     N'Classification per national policy, labelling, handling and access controls.',
     N'التصنيف وفق السياسة الوطنية، الوسم، والمعالجة وضوابط الوصول.',
     N'Data Classification',
     7.50, 80, 0.3500, 0.4500, 0.2000,
     N'Data Classification Lead', 1,
     N'Placeholder — requires official SDAIA/NDI domain library import');
END
GO

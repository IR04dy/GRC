-- V290 (Green-Finance uplift G4 — plan §5.4 / REG-1): IFRS S1/S2 readiness checklist
-- (SOCPA endorsement). Seeded requirement set with per-requirement coverage status;
-- the gap report is the SOCPA-readiness artefact. Statuses: COVERED | PARTIAL | GAP.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ifrs_s_requirements' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.ifrs_s_requirements (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT           NOT NULL,
        external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        req_code        NVARCHAR(30)     NOT NULL,
        standard        NVARCHAR(5)      NOT NULL,    -- S1 | S2
        pillar          NVARCHAR(30)     NOT NULL,    -- GOVERNANCE|STRATEGY|RISK_MGMT|METRICS_TARGETS
        title_en        NVARCHAR(400)    NOT NULL,
        title_ar        NVARCHAR(400)    NULL,
        status          NVARCHAR(10)     NOT NULL DEFAULT 'GAP',
        coverage_ref    NVARCHAR(300)    NULL,        -- what satisfies it (TCFD item / GHG record / target ...)
        owner_name      NVARCHAR(200)    NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_ifrs_req_code ON sust.ifrs_s_requirements(organisation_id, req_code) WHERE is_deleted = 0;
END

-- Seed the org-1 checklist (bilingual; statuses start GAP except where the platform
-- demonstrably already covers the content — TCFD/GHG/financed emissions exist).
INSERT INTO sust.ifrs_s_requirements (organisation_id, req_code, standard, pillar, title_en, title_ar, status, coverage_ref)
SELECT 1, v.code, v.std, v.pillar, v.en, v.ar, v.status, v.ref
FROM (VALUES
    ('S1-GOV-1',  'S1', 'GOVERNANCE',      'Governance body oversight of sustainability-related risks and opportunities', N'إشراف مجلس الإدارة على مخاطر وفرص الاستدامة', 'PARTIAL', 'CMA 91-94 disclosures + board committees'),
    ('S1-GOV-2',  'S1', 'GOVERNANCE',      'Management''s role in sustainability governance processes', N'دور الإدارة التنفيذية في حوكمة الاستدامة', 'GAP', NULL),
    ('S1-STR-1',  'S1', 'STRATEGY',        'Sustainability risks/opportunities affecting business model and strategy', N'أثر مخاطر وفرص الاستدامة على نموذج الأعمال والاستراتيجية', 'PARTIAL', 'Materiality matrix (material_topic)'),
    ('S1-STR-2',  'S1', 'STRATEGY',        'Financial effects: current and anticipated', N'الآثار المالية الحالية والمتوقعة', 'GAP', NULL),
    ('S1-RSK-1',  'S1', 'RISK_MGMT',       'Processes to identify, assess, prioritise and monitor sustainability risks', N'عمليات تحديد وتقييم ومراقبة مخاطر الاستدامة', 'PARTIAL', 'ERM integration (climate_risk_opportunity)'),
    ('S1-MET-1',  'S1', 'METRICS_TARGETS', 'Metrics and targets used to measure sustainability performance', N'المقاييس والمستهدفات لقياس أداء الاستدامة', 'PARTIAL', 'esg_metric + readings; esg_targets (V289)'),
    ('S2-GOV-1',  'S2', 'GOVERNANCE',      'Governance of climate-related risks and opportunities', N'حوكمة المخاطر والفرص المناخية', 'PARTIAL', 'TCFD governance items'),
    ('S2-STR-1',  'S2', 'STRATEGY',        'Climate risks/opportunities and effects on strategy (incl. transition plan)', N'المخاطر والفرص المناخية وأثرها على الاستراتيجية وخطة التحوّل', 'PARTIAL', 'TCFD items + scenarios'),
    ('S2-STR-2',  'S2', 'STRATEGY',        'Climate resilience assessment using scenario analysis', N'تقييم المرونة المناخية عبر تحليل السيناريوهات', 'COVERED', 'tcfd_climate_scenario (computed loss)'),
    ('S2-RSK-1',  'S2', 'RISK_MGMT',       'Processes for identifying and managing climate risks', N'عمليات تحديد وإدارة المخاطر المناخية', 'COVERED', 'climate_risk_opportunity register'),
    ('S2-MET-1',  'S2', 'METRICS_TARGETS', 'GHG emissions Scope 1 and Scope 2 (GHG Protocol)', N'انبعاثات النطاقين الأول والثاني وفق بروتوكول الغازات الدفيئة', 'COVERED', 'ghg_emission_record + verification flags'),
    ('S2-MET-2',  'S2', 'METRICS_TARGETS', 'GHG emissions Scope 3 (15 categories where material)', N'انبعاثات النطاق الثالث (الفئات الخمس عشرة متى كانت جوهرية)', 'PARTIAL', 'S3 single line + coverage % (category split pending)'),
    ('S2-MET-3',  'S2', 'METRICS_TARGETS', 'Industry-based metrics and financed emissions (FIs)', N'مقاييس القطاع والانبعاثات المموَّلة للمؤسسات المالية', 'COVERED', 'financed_emissions_record (PCAF attribution + DQ)'),
    ('S2-MET-4',  'S2', 'METRICS_TARGETS', 'Climate targets and use of carbon credits', N'المستهدفات المناخية واستخدام أرصدة الكربون', 'PARTIAL', 'esg_targets (V289) + carbon_credits (V288)')
) v(code, std, pillar, en, ar, status, ref)
WHERE NOT EXISTS (SELECT 1 FROM sust.ifrs_s_requirements r
                  WHERE r.organisation_id = 1 AND r.req_code = v.code);

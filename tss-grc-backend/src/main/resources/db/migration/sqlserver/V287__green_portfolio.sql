-- V287 (Green-Finance uplift G3 — plan §5.1/§5.2): Green Portfolio.
-- Versioned green taxonomy (ICMA GBP + Saudi CCE seed; swappable the day an
-- official Saudi taxonomy lands) + green-tagged portfolio assets with the
-- maker-checker tagging SoD (proposer ≠ approver, enforced in the service).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'taxonomy_entries' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.taxonomy_entries (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT           NOT NULL,   -- 0 = platform default set
        external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        code            NVARCHAR(40)     NOT NULL,
        name_en         NVARCHAR(200)    NOT NULL,
        name_ar         NVARCHAR(200)    NULL,
        source          NVARCHAR(20)     NOT NULL DEFAULT 'ICMA',   -- ICMA | CCE | NATIONAL | CUSTOM
        version         INT              NOT NULL DEFAULT 1,
        is_active       BIT              NOT NULL DEFAULT 1,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_taxonomy_code ON sust.taxonomy_entries(organisation_id, code, version) WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'portfolio_assets' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.portfolio_assets (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT           NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        asset_code          NVARCHAR(40)     NOT NULL,
        name_en             NVARCHAR(300)    NOT NULL,
        name_ar             NVARCHAR(300)    NULL,
        sector              NVARCHAR(80)     NULL,
        exposure_sar_mn     DECIMAL(18,4)    NOT NULL,
        taxonomy_code       NVARCHAR(40)     NULL,
        eligibility_status  NVARCHAR(20)     NOT NULL DEFAULT 'UNASSESSED',
        -- UNASSESSED | PROPOSED | ELIGIBLE | TRANSITION | INELIGIBLE
        proposed_status     NVARCHAR(20)     NULL,     -- what the proposer asked for
        proposed_by         BIGINT           NULL,
        approved_by         BIGINT           NULL,
        decided_at          DATETIME2        NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_portfolio_asset_code ON sust.portfolio_assets(organisation_id, asset_code) WHERE is_deleted = 0;
    CREATE INDEX ix_portfolio_org_status ON sust.portfolio_assets(organisation_id, eligibility_status);
END

-- Platform-default taxonomy set (org 0): ICMA GBP categories + Saudi CCE additions.
INSERT INTO sust.taxonomy_entries (organisation_id, code, name_en, name_ar, source, version)
SELECT 0, v.code, v.en, v.ar, v.src, 1
FROM (VALUES
    ('RENEWABLE_ENERGY',  'Renewable energy',              N'الطاقة المتجددة',                'ICMA'),
    ('ENERGY_EFFICIENCY', 'Energy efficiency',             N'كفاءة الطاقة',                   'ICMA'),
    ('CLEAN_TRANSPORT',   'Clean transportation',          N'النقل النظيف',                   'ICMA'),
    ('GREEN_BUILDINGS',   'Green buildings',               N'المباني الخضراء',                'ICMA'),
    ('WATER',             'Sustainable water & wastewater', N'إدارة المياه المستدامة',        'ICMA'),
    ('WASTE',             'Pollution prevention & waste',  N'منع التلوث وإدارة النفايات',     'ICMA'),
    ('CCUS',              'Carbon capture, utilisation & storage', N'التقاط الكربون واستخدامه وتخزينه', 'CCE'),
    ('CLEAN_HYDROGEN',    'Clean hydrogen',                N'الهيدروجين النظيف',              'CCE'),
    ('NATURE_BASED',      'Nature-based solutions (afforestation)', N'الحلول الطبيعية (التشجير)', 'CCE'),
    ('CLIMATE_ADAPTATION','Climate-change adaptation',     N'التكيّف مع تغيّر المناخ',        'ICMA')
) v(code, en, ar, src)
WHERE NOT EXISTS (SELECT 1 FROM sust.taxonomy_entries t
                  WHERE t.organisation_id = 0 AND t.code = v.code AND t.version = 1);

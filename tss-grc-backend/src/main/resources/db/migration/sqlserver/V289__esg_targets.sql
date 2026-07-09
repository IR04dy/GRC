-- V289 (Green-Finance uplift G4 — plan §5.5): org-wide ESG targets & transition
-- trajectory. Targets beyond financed emissions (GHG scopes, energy, water, waste,
-- renewables share), SGI/net-zero-2060 alignment flag, annual actuals; an off-track
-- trajectory raises an automatic corrective action (existing sust CAPA engine).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'esg_targets' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.esg_targets (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT           NOT NULL,
        external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        target_code     NVARCHAR(40)     NOT NULL,
        title_en        NVARCHAR(300)    NOT NULL,
        title_ar        NVARCHAR(300)    NULL,
        scope           NVARCHAR(30)     NOT NULL,   -- GHG_S1|GHG_S2|GHG_S3|ENERGY|WATER|WASTE|RENEWABLES_SHARE
        unit            NVARCHAR(40)     NOT NULL,
        baseline_year   INT              NOT NULL,
        baseline_value  DECIMAL(18,4)    NOT NULL,
        target_year     INT              NOT NULL,
        target_value    DECIMAL(18,4)    NOT NULL,
        sgi_aligned     BIT              NOT NULL DEFAULT 0,
        status          NVARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE|ACHIEVED|RETIRED
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_esg_target_code ON sust.esg_targets(organisation_id, target_code) WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'esg_target_actuals' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.esg_target_actuals (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        target_id       BIGINT        NOT NULL,     -- soft FK sust.esg_targets
        [year]          INT           NOT NULL,
        value           DECIMAL(18,4) NOT NULL,
        notes           NVARCHAR(500) NULL,
        is_deleted      BIT           NOT NULL DEFAULT 0,
        created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE UNIQUE INDEX ux_target_actual_year ON sust.esg_target_actuals(target_id, [year]) WHERE is_deleted = 0;
END

-- Extend the CAPA source-type whitelist (ck_ca_source, from the S-series ESG work)
-- with the two G4 auto-CAPA sources: ESG_TARGET (trajectory sweep, this migration)
-- and ENV_PERMIT (expiry sweep, V291 — V289 always runs first so both go in here).
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_ca_source' AND definition LIKE '%ESG_TARGET%')
BEGIN
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ca_source')
        ALTER TABLE sust.corrective_action DROP CONSTRAINT ck_ca_source;
    ALTER TABLE sust.corrective_action WITH CHECK ADD CONSTRAINT ck_ca_source
        CHECK (source_type IN (N'MANUAL', N'VENDOR_ESG', N'EVIDENCE', N'SGI_COMMITMENT',
                               N'TCFD_ITEM', N'CMA_DISCLOSURE', N'ANNEX_GAP', N'GHG_RECORD',
                               N'CLIMATE_RISK', N'ESG_TARGET', N'ENV_PERMIT'));
END

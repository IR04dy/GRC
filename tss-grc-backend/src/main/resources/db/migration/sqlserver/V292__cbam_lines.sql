-- V292 (Green-Finance uplift G4 — plan §5.7 / REG-10): EU CBAM exposure screen —
-- per exported product line, the embedded-emissions data an EU importer will demand
-- each reporting period (definitive regime from 2026), plus carbon-cost exposure.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'cbam_lines' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.cbam_lines (
        id                        BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id           BIGINT           NOT NULL,
        external_id               UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cn_group                  NVARCHAR(20)     NOT NULL,   -- ALUMINIUM|STEEL|CEMENT|FERTILISERS|HYDROGEN|ELECTRICITY
        product_desc_en           NVARCHAR(300)    NOT NULL,
        product_desc_ar           NVARCHAR(300)    NULL,
        period_year               INT              NOT NULL,
        period_quarter            INT              NOT NULL,   -- 1..4
        export_volume_tonnes      DECIMAL(18,4)    NOT NULL,
        embedded_tco2e_per_tonne  DECIMAL(18,6)    NOT NULL,
        total_embedded_tco2e      DECIMAL(18,4)    NOT NULL,
        eu_importer               NVARCHAR(200)    NULL,
        data_basis                NVARCHAR(30)     NOT NULL DEFAULT 'MEASURED',  -- MEASURED|DEFAULT_VALUES
        status                    NVARCHAR(15)     NOT NULL DEFAULT 'DRAFT',     -- DRAFT|REPORTED
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE INDEX ix_cbam_org_period ON sust.cbam_lines(organisation_id, period_year, period_quarter);
END

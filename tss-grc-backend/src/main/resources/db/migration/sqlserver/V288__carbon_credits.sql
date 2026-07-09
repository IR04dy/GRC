-- V288 (Green-Finance uplift G3 — plan §5.3 / REG-11): voluntary carbon-credit
-- ledger (RVCMC practice). Retirement REQUIRES a registry certificate reference
-- (enforced in the service) and carries the claim purpose so offsets are only
-- ever disclosed alongside gross reductions (no net-only claims).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'carbon_credits' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.carbon_credits (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT           NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        registry            NVARCHAR(80)     NOT NULL,   -- e.g. RVCMC auction / Verra / Gold Standard
        serial_range        NVARCHAR(200)    NOT NULL,
        vintage_year        INT              NOT NULL,
        project_type        NVARCHAR(80)     NULL,
        project_country     NVARCHAR(60)     NULL,
        quantity_tco2e      DECIMAL(18,4)    NOT NULL,
        price_sar_per_t     DECIMAL(18,4)    NULL,
        status              NVARCHAR(15)     NOT NULL DEFAULT 'PURCHASED',  -- PURCHASED | RETIRED
        retirement_date     DATE             NULL,
        retirement_purpose  NVARCHAR(300)    NULL,       -- which target/claim this offsets
        certificate_ref     NVARCHAR(200)    NULL,       -- registry retirement certificate (REQUIRED to retire)
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE INDEX ix_credits_org_status ON sust.carbon_credits(organisation_id, status);
END

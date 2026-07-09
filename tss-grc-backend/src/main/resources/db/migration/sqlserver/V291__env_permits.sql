-- V291 (Green-Finance uplift G4 — plan §5.6 / REG-5): NCEC environmental permits
-- register with expiry surveillance. Permits expiring within the lead window (or
-- already expired) raise an automatic corrective action via the sust CAPA engine.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'env_permits' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.env_permits (
        id                   BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id      BIGINT           NOT NULL,
        external_id          UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        permit_number        NVARCHAR(60)     NOT NULL,
        facility_en          NVARCHAR(300)    NOT NULL,
        facility_ar          NVARCHAR(300)    NULL,
        authority            NVARCHAR(40)     NOT NULL DEFAULT 'NCEC',
        permit_type          NVARCHAR(80)     NULL,     -- operating / discharge / air / waste-handling ...
        issue_date           DATE             NULL,
        expiry_date          DATE             NOT NULL,
        conditions_en        NVARCHAR(MAX)    NULL,
        conditions_ar        NVARCHAR(MAX)    NULL,
        monitoring_frequency NVARCHAR(40)     NULL,     -- MONTHLY / QUARTERLY / ANNUAL
        status               NVARCHAR(20)     NOT NULL DEFAULT 'VALID',  -- VALID|SUSPENDED|EXPIRED
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_env_permit_number ON sust.env_permits(organisation_id, permit_number) WHERE is_deleted = 0;
    CREATE INDEX ix_env_permit_expiry ON sust.env_permits(organisation_id, expiry_date);
END

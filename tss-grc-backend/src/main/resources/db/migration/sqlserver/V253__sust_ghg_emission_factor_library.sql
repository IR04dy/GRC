-- S-J (GHG hardening) — versioned emission-factor library + GHG record wiring.
--
-- 1) sust.emission_factor: a per-tenant, VERSIONED library of emission factors
--    (activity unit → factor value + factor unit + source). Creating a new
--    version of a factor_code supersedes the prior active row. GHG records may
--    reference a library factor instead of hand-typing the number.
-- 2) ghg_emission_record gains:
--      emission_factor_id → optional FK to the library factor used
--      value_source       → COMPUTED (derived activity×factor) vs ASSERTED (typed)
--
-- Idempotent (OBJECT_ID / COL_LENGTH guards). Dev DB is Flyway-OFF; applied by hand.

IF OBJECT_ID('sust.emission_factor','U') IS NULL
BEGIN
    CREATE TABLE sust.emission_factor (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        external_id     VARCHAR(64)   NULL,
        factor_code     VARCHAR(60)   NOT NULL,
        scope           SMALLINT      NOT NULL,
        category        VARCHAR(60)   NULL,
        title_en        NVARCHAR(255) NOT NULL,
        title_ar        NVARCHAR(255) NULL,
        activity_unit   VARCHAR(40)   NOT NULL,
        factor_value    DECIMAL(20,6) NOT NULL,
        factor_unit     VARCHAR(40)   NOT NULL,
        source          VARCHAR(255)  NULL,
        version         INT           NOT NULL CONSTRAINT df_emission_factor_version DEFAULT 1,
        is_active       BIT           NOT NULL CONSTRAINT df_emission_factor_active  DEFAULT 1,
        valid_from      DATE          NULL,
        valid_to        DATE          NULL,
        notes           NVARCHAR(MAX) NULL,
        is_deleted      BIT           NOT NULL CONSTRAINT df_emission_factor_deleted DEFAULT 0,
        created_at      DATETIME2     NOT NULL CONSTRAINT df_emission_factor_created DEFAULT SYSUTCDATETIME(),
        created_by      VARCHAR(100)  NULL,
        updated_at      DATETIME2     NULL,
        updated_by      VARCHAR(100)  NULL
    );
    CREATE INDEX ix_emission_factor_org_code ON sust.emission_factor(organisation_id, factor_code);
END;
GO

IF COL_LENGTH('sust.ghg_emission_record','emission_factor_id') IS NULL
    ALTER TABLE sust.ghg_emission_record ADD emission_factor_id BIGINT NULL;
GO

IF COL_LENGTH('sust.ghg_emission_record','value_source') IS NULL
    ALTER TABLE sust.ghg_emission_record
        ADD value_source VARCHAR(20) NOT NULL CONSTRAINT df_ghg_value_source DEFAULT 'ASSERTED';
GO

-- ERM remediation epic E-F — configurable methodology + multi-dimensional impact.
--
-- (1) risk.methodology_config : one row per org. Declares the active impact
--     dimensions and the likelihood/impact scale maxima. Consumed by the
--     multi-dimensional impact engine (dimension + scale validation) and read
--     by the UI heatmap as the single source of truth.
-- (2) risk.risk_impact_ratings: per-risk, per-dimension impact scores. The
--     risk's overall inherent impact is derived as the worst (max) dimension.

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'risk' AND t.name = 'methodology_config')
BEGIN
    CREATE TABLE risk.methodology_config (
        id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id        BIGINT        NOT NULL,
        impact_dimensions_csv  NVARCHAR(1000) NOT NULL
            DEFAULT 'FINANCIAL,OPERATIONAL,REGULATORY,REPUTATIONAL,CYBER,PRIVACY,SAFETY,STRATEGIC',
        likelihood_scale_max   INT           NOT NULL DEFAULT 5,
        impact_scale_max       INT           NOT NULL DEFAULT 5,
        is_deleted             BIT           NOT NULL DEFAULT 0,
        created_at             DATETIME2     NULL DEFAULT SYSUTCDATETIME(),
        updated_at             DATETIME2     NULL
    );
    CREATE UNIQUE INDEX ux_methodology_config_org
        ON risk.methodology_config (organisation_id) WHERE is_deleted = 0;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'risk' AND t.name = 'risk_impact_ratings')
BEGIN
    CREATE TABLE risk.risk_impact_ratings (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT       NOT NULL,
        risk_id         BIGINT       NOT NULL,
        dimension       NVARCHAR(40) NOT NULL,
        score           INT          NOT NULL,
        is_deleted      BIT          NOT NULL DEFAULT 0,
        created_at      DATETIME2    NULL DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2    NULL
    );
    CREATE INDEX ix_risk_impact_risk
        ON risk.risk_impact_ratings (organisation_id, risk_id) WHERE is_deleted = 0;
END
GO

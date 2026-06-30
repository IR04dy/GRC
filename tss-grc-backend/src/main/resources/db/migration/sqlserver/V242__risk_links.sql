-- ERM remediation epic E-G — typed register linkage.
-- A first-class, typed cross-reference from a risk to another register object
-- (objective / process / service / asset / supplier / project / incident /
-- control / obligation). Mirrors the lightweight risk.risk_controls bridge.

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'risk' AND t.name = 'risk_links')
BEGIN
    CREATE TABLE risk.risk_links (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        risk_id         BIGINT        NOT NULL,
        link_type       NVARCHAR(30)  NOT NULL,
        target_ref      NVARCHAR(300) NOT NULL,
        target_id       BIGINT        NULL,
        description_en  NVARCHAR(1000) NULL,
        is_deleted      BIT           NOT NULL DEFAULT 0,
        created_at      DATETIME2     NULL DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2     NULL
    );

    CREATE INDEX ix_risk_links_risk
        ON risk.risk_links (organisation_id, risk_id)
        WHERE is_deleted = 0;
END
GO

-- S-G (Complete IFRS S2 / TCFD annual report) — persisted, approval-gated,
-- content-locked report snapshot.
--
-- sust.climate_report stores a generated disclosure pack: the full assembled
-- content (scenarios + sector impacts + weighted EL + financed emissions +
-- TCFD 4-pillar checklist + GHG inventory + evidence index + gaps/CAPA) as a
-- JSON snapshot. Lifecycle DRAFT → IN_REVIEW → APPROVED → PUBLISHED; on APPROVE
-- the content is SHA-256 hashed + locked (is_locked=1) so the approved figures
-- are immutable — re-generation opens a new version row.
--
-- Idempotent (OBJECT_ID guard). Dev DB is Flyway-OFF; applied by hand.
IF OBJECT_ID('sust.climate_report','U') IS NULL
BEGIN
    CREATE TABLE sust.climate_report (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        external_id     VARCHAR(64)   NULL,
        reporting_year  INT           NOT NULL,
        report_code     VARCHAR(40)   NULL,
        title_en        NVARCHAR(255) NOT NULL,
        title_ar        NVARCHAR(255) NULL,
        status          VARCHAR(20)   NOT NULL CONSTRAINT df_climate_report_status  DEFAULT 'DRAFT',
        version         INT           NOT NULL CONSTRAINT df_climate_report_version DEFAULT 1,
        content_json    NVARCHAR(MAX) NULL,
        content_hash    VARCHAR(64)   NULL,
        generated_at    DATETIME2     NOT NULL CONSTRAINT df_climate_report_gen     DEFAULT SYSUTCDATETIME(),
        generated_by    VARCHAR(255)  NULL,
        approved_by     VARCHAR(255)  NULL,
        approved_at     DATETIME2     NULL,
        published_at    DATETIME2     NULL,
        is_locked       BIT           NOT NULL CONSTRAINT df_climate_report_locked  DEFAULT 0,
        is_deleted      BIT           NOT NULL CONSTRAINT df_climate_report_deleted DEFAULT 0,
        created_at      DATETIME2     NOT NULL CONSTRAINT df_climate_report_created DEFAULT SYSUTCDATETIME(),
        created_by      VARCHAR(100)  NULL,
        updated_at      DATETIME2     NULL,
        updated_by      VARCHAR(100)  NULL
    );
    CREATE INDEX ix_climate_report_org_year ON sust.climate_report(organisation_id, reporting_year, version);
END;
GO

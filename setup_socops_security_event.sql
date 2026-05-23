-- Recovery script for socops.security_event (V150 partial replay).
-- Each batch has its own SET options so filtered indexes parse correctly.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

CREATE TABLE socops.security_event (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(64)    NOT NULL,
    source_system         NVARCHAR(60)    NOT NULL,
    event_time            DATETIME2       NOT NULL,
    ingested_at           DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    severity              NVARCHAR(20)    NOT NULL,
    category              NVARCHAR(60),
    rule_name             NVARCHAR(300),
    source_host           NVARCHAR(255),
    source_ip             NVARCHAR(45),
    destination_host      NVARCHAR(255),
    destination_ip        NVARCHAR(45),
    username              NVARCHAR(255),
    asset_id              BIGINT,
    raw_payload           NVARCHAR(MAX),
    status                NVARCHAR(30)    NOT NULL DEFAULT 'NEW',
    assigned_to_user_id   BIGINT,
    incident_id           BIGINT,
    triaged_at            DATETIME2,
    resolved_at           DATETIME2,
    sla_due_at            DATETIME2,
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_security_event_external UNIQUE (organisation_id, source_system, external_id)
);
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

CREATE INDEX ix_security_event_org_status      ON socops.security_event(organisation_id, status)              WHERE is_deleted = 0;
GO
CREATE INDEX ix_security_event_org_event_time  ON socops.security_event(organisation_id, event_time DESC)     WHERE is_deleted = 0;
GO
CREATE INDEX ix_security_event_org_severity    ON socops.security_event(organisation_id, severity)            WHERE is_deleted = 0;
GO
CREATE INDEX ix_security_event_sla_due         ON socops.security_event(organisation_id, sla_due_at)
    WHERE is_deleted = 0 AND status NOT IN (N'RESOLVED', N'FALSE_POSITIVE');
GO

-- ── Licensing: entitle the three seeded tenants to SOCOPS ──────────────────
IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses
               WHERE organisation_id = 1 AND module_code = 'SOCOPS')
    INSERT INTO platform.organisation_licenses
        (organisation_id, module_code, tier, enabled, valid_from, notes, created_at)
    VALUES (1, 'SOCOPS', 'ENTERPRISE', 1, GETDATE(),
            N'Auto-entitled for dev tenant — SOC Operations module', GETDATE());

IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses
               WHERE organisation_id = 2 AND module_code = 'SOCOPS')
    INSERT INTO platform.organisation_licenses
        (organisation_id, module_code, tier, enabled, valid_from, notes, created_at)
    VALUES (2, 'SOCOPS', 'ENTERPRISE', 1, GETDATE(),
            N'Auto-entitled for Aramco Trading tenant', GETDATE());

IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses
               WHERE organisation_id = 3 AND module_code = 'SOCOPS')
    INSERT INTO platform.organisation_licenses
        (organisation_id, module_code, tier, enabled, valid_from, notes, created_at)
    VALUES (3, 'SOCOPS', 'ENTERPRISE', 1, GETDATE(),
            N'Auto-entitled for STC Bank demo tenant', GETDATE());
GO

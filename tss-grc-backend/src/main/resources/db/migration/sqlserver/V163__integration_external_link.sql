-- V163 — Sprint 12: Generic external-system link table for outbound
-- integrations (ServiceNow first; later Splunk, CrowdStrike, Tenable).
--
-- One row per (org, integration, grc_module, grc_record_id) — that is
-- the natural key. We never push the same GRC record twice into the
-- same integration: re-pushes update the row in place and bump
-- last_pushed_at.
--
-- Notes
--   * external_id      — opaque key the foreign system gives us back
--                        (e.g. ServiceNow sys_id GUID).
--   * external_number  — human-readable identifier the foreign system
--                        displays to its users (e.g. INC0012345). Used
--                        in cross-system narrative and search.
--   * external_url     — direct link a GRC user can click to open the
--                        record in the foreign system's UI.
--   * last_known_state — last status string we observed from the
--                        foreign system (via webhook or polling).
--   * grc_record_id    — NVARCHAR not BIGINT because some GRC records
--                        we link (e.g. compliance gaps) are keyed by
--                        external_id strings, not numeric PKs.
--
-- V162 is reserved for the BCM/BIA quantitative slice landing in
-- parallel — see sprint12 coordination notes. This is V163.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'integration_external_link')
BEGIN
    CREATE TABLE platform.integration_external_link (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        integration         NVARCHAR(40)    NOT NULL,    -- 'SERVICENOW', 'SPLUNK', 'CROWDSTRIKE', 'TENABLE'
        grc_module          NVARCHAR(40)    NOT NULL,    -- 'CYBER_INCIDENT' / 'AUDIT_FINDING' / 'CAPA_TASK' / 'COMPLIANCE_GAP'
        grc_record_id       NVARCHAR(120)   NOT NULL,    -- external_id of the originating GRC record
        external_id         NVARCHAR(120)   NOT NULL,    -- e.g. ServiceNow sys_id GUID
        external_number     NVARCHAR(80),                -- e.g. INC0012345 — what users see
        external_url        NVARCHAR(500),
        last_pushed_at      DATETIME2,
        last_pulled_at      DATETIME2,
        last_known_state    NVARCHAR(60),
        is_deleted          BIT             NOT NULL DEFAULT 0,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2,
        CONSTRAINT uq_int_external_link UNIQUE (organisation_id, integration, grc_module, grc_record_id)
    );

    CREATE INDEX ix_int_link_external
        ON platform.integration_external_link (integration, external_number)
        WHERE is_deleted = 0;

    CREATE INDEX ix_int_link_org_module
        ON platform.integration_external_link (organisation_id, grc_module)
        WHERE is_deleted = 0;
END
GO

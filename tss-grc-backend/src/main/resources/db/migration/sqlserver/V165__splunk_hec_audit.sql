-- V165 — Sprint 12 / Slice 2: Splunk SIEM bridge — HEC outbound audit log.
--
-- The generic external-link table (V163) already records WHICH GRC record
-- is mapped to which Splunk event. This table records each individual HEC
-- POST attempt so the SOC platform team can:
--
--   * Reconstruct delivery history per GRC record (replay / forensics).
--   * Aggregate failures by hec_index / http_status for circuit-breaker
--     decisions in a later slice.
--   * Spot-check median response-time during a Splunk outage and surface
--     it on the integrations dashboard.
--
-- Notes
--   * One row per attempt. A retry burst (3 attempts) produces 3 rows;
--     the attempt_count column on each row makes "final attempt" obvious.
--   * event_payload is the JSON we POSTed to Splunk (HEC envelope including
--     the wrapper {time, host, source, sourcetype, index, event}). Stored
--     as NVARCHAR(MAX) so the per-row footprint is unbounded in pathology
--     cases but the column is not indexed.
--   * V162 reserved for the BCM/BIA quantitative slice; V163 holds the
--     generic integration_external_link table; V164 reserved for the Risk
--     Bow-Tie agent slice running in parallel. This is V165.
--   * No FK to integration_external_link.id — the link row may not exist
--     yet when the first HEC attempt is logged (we record the audit row
--     before committing the link row). We carry (organisation_id,
--     source_module, source_id) instead so the audit row is always
--     self-contained.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'splunk_hec_outbound_log')
BEGIN
    CREATE TABLE platform.splunk_hec_outbound_log (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        source_module       NVARCHAR(40)    NOT NULL,    -- e.g. SECURITY_EVENT / FINDING / RISK / CAPA_TASK
        source_id           NVARCHAR(120)   NOT NULL,    -- GRC record id (string — matches integration_external_link)
        hec_index           NVARCHAR(120),               -- Splunk index targeted (e.g. tss_grc)
        event_payload       NVARCHAR(MAX),               -- JSON sent to /services/collector/event
        http_status         INT,                         -- 200 / 4xx / 5xx / NULL on connection failure
        response_time_ms    INT,                         -- wall-clock duration of the attempt
        attempt_count       INT             NOT NULL DEFAULT 1,
        error_message       NVARCHAR(2000),              -- captured exception text on failure
        is_deleted          BIT             NOT NULL DEFAULT 0,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX ix_splunk_hec_log_org_module
        ON platform.splunk_hec_outbound_log (organisation_id, source_module, created_at DESC)
        WHERE is_deleted = 0;

    CREATE INDEX ix_splunk_hec_log_source
        ON platform.splunk_hec_outbound_log (organisation_id, source_module, source_id)
        WHERE is_deleted = 0;

    CREATE INDEX ix_splunk_hec_log_failures
        ON platform.splunk_hec_outbound_log (organisation_id, http_status, created_at DESC)
        WHERE is_deleted = 0 AND http_status <> 200;
END
GO

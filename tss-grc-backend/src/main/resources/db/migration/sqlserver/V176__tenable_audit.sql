-- V176 — Sprint 12 / Slice 4: Tenable.io / Tenable.sc VM bridge — audit tables.
--
-- The generic external-link table (V163) records WHICH cyber.vulnerabilities
-- row maps to which Tenable (asset, plugin) pair. The two tables below add
-- the per-attempt audit trail the GRC platform team needs to:
--
--   * Reconstruct an inbound poll history (which findings we saw, which we
--     dropped via the dedupe guard on (asset_uuid, plugin_id), which mapped
--     to a cyber.vulnerabilities row).
--   * Trace every outbound risk-acceptance / recasted-severity decision
--     back to the SecurityContext user that requested it — NCA ECC-2,
--     SAMA CSF, and ISO 27001 A.5.7 require an attributable record of
--     "who decided to accept this risk and why".
--   * Aggregate failures by action_type / http_status for the
--     integrations dashboard (Sprint 13 circuit-breaker view).
--
-- Notes
--   * tenable_finding_log: one row per (asset_uuid, plugin_id) tenant pair.
--     The unique filtered index enforces dedupe at the database layer — the
--     application-side dedupe in TenableInboundService is a fast path, the
--     constraint is the safety net. A re-poll of the same finding updates
--     the existing row (last-seen wins) rather than inserting a duplicate.
--   * tenable_recast_log: one row per RECAST_SEVERITY / ACCEPT_RISK /
--     REMOVE_RECAST attempt. A retry burst produces multiple rows so
--     attempt_count makes "final attempt" obvious. requested_by_user_id
--     comes from SecurityContext — never from the scheduled poller.
--   * target_asset_uuid is NULLABLE — a global recast (applies to ALL
--     assets with this plugin) is permitted; an asset-scoped recast
--     carries the asset uuid.
--   * Wrap in SET QUOTED_IDENTIFIER ON / SET ANSI_NULLS ON so the filtered
--     indexes accept the WHERE clauses (SQL Server requires those session
--     options to match between index creation and query execution).

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── platform.tenable_finding_log ───────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'tenable_finding_log')
BEGIN
    CREATE TABLE platform.tenable_finding_log (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        tenable_asset_uuid          NVARCHAR(80)    NOT NULL,    -- Tenable asset UUID (RFC 4122)
        tenable_plugin_id           NVARCHAR(40)    NOT NULL,    -- Tenable plugin id (numeric in IO, string in SC)
        severity                    NVARCHAR(20),                -- CRITICAL / HIGH / MEDIUM / LOW / INFO (after mapping)
        cvss_v3_score               DECIMAL(4,1),                -- 0.0..10.0 — CVSS v3 base score
        cve_id                      NVARCHAR(50),                -- Primary CVE (e.g. CVE-2024-12345). Tenable findings often have multiple — the first wins.
        ingested_at                 DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        vulnerability_finding_id    BIGINT          NULL,        -- FK to cyber.vulnerabilities (nullable: dropped before write)
        http_status                 INT,                         -- 200 on a successful poll; NULL when this row records an internal-only event
        error_message               NVARCHAR(2000),
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_at                  DATETIME2,
        updated_by                  BIGINT,
        row_version                 ROWVERSION
    );

    -- One row per (asset, plugin) per tenant — the application-side dedupe in
    -- TenableInboundService is the fast path; this is the safety net. Filtered
    -- so soft-deleted history doesn't block re-ingest.
    CREATE UNIQUE INDEX uq_tenable_finding_asset_plugin
        ON platform.tenable_finding_log (organisation_id, tenable_asset_uuid, tenable_plugin_id)
        WHERE is_deleted = 0;

    CREATE INDEX ix_tenable_finding_org_severity
        ON platform.tenable_finding_log (organisation_id, severity)
        WHERE is_deleted = 0;

    CREATE INDEX ix_tenable_finding_org_cve
        ON platform.tenable_finding_log (organisation_id, cve_id)
        WHERE is_deleted = 0 AND cve_id IS NOT NULL;
END
GO

-- ─── platform.tenable_recast_log ────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'tenable_recast_log')
BEGIN
    CREATE TABLE platform.tenable_recast_log (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        action_type                 NVARCHAR(40)    NOT NULL,    -- RECAST_SEVERITY / ACCEPT_RISK / REMOVE_RECAST
        target_plugin_id            NVARCHAR(40)    NOT NULL,
        target_asset_uuid           NVARCHAR(80)    NULL,        -- NULL = global (applies to all assets with this plugin)
        new_severity                NVARCHAR(20)    NULL,        -- INFO / LOW / MEDIUM / HIGH / CRITICAL — only for RECAST_SEVERITY
        justification               NVARCHAR(MAX)   NOT NULL,    -- Required for the audit trail (min 30 chars enforced at service layer)
        expires_at                  DATETIME2       NULL,        -- For ACCEPT_RISK — when the acceptance window closes
        requested_by_user_id        BIGINT,                      -- SecurityContext user
        http_status                 INT,                         -- 200 on success; 4xx/5xx on failure
        response_payload            NVARCHAR(MAX),               -- Full Tenable response JSON for forensics
        attempt_count               INT             NOT NULL DEFAULT 1,
        error_message               NVARCHAR(2000),
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_at                  DATETIME2,
        updated_by                  BIGINT,
        row_version                 ROWVERSION
    );

    CREATE INDEX ix_tenable_recast_org_type_created
        ON platform.tenable_recast_log (organisation_id, action_type, created_at DESC)
        WHERE is_deleted = 0;

    CREATE INDEX ix_tenable_recast_org_plugin
        ON platform.tenable_recast_log (organisation_id, target_plugin_id, created_at DESC)
        WHERE is_deleted = 0;

    -- Failure-focused index: filtered indexes can't use OR, so we filter on
    -- "http_status >= 400" which covers 4xx + 5xx. NULL http_status rows
    -- (connection failures) won't match this filter but are visible via the
    -- main org_type_created index above.
    CREATE INDEX ix_tenable_recast_failures
        ON platform.tenable_recast_log (organisation_id, http_status, created_at DESC)
        WHERE is_deleted = 0 AND http_status >= 400;
END
GO

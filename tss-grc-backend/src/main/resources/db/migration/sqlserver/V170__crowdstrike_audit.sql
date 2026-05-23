-- V170 — Sprint 12 / Slice 3: CrowdStrike Falcon EDR bridge — audit tables.
--
-- The generic external-link table (V163) already records WHICH GRC record is
-- mapped to which Falcon detection. The two tables below add the per-attempt
-- audit trail the SOC platform team needs to:
--
--   * Reconstruct an inbound poll history (which detections we saw, which we
--     dropped via the dedupe guard, which mapped to a security_event).
--   * Trace every outbound containment action (host isolate / lift / hide)
--     back to the SecurityContext user that requested it — SAMA CSF, NCA
--     ECC-2, and ISO 27035 all require an attributable record of "who
--     contained which endpoint and why".
--   * Aggregate failures by severity / action_type / http_status for a
--     circuit-breaker view on the integrations dashboard (Sprint 13).
--
-- Notes
--   * crowdstrike_detection_log: one row per Falcon detection observed,
--     keyed by (organisation_id, falcon_detection_id). The unique constraint
--     enforces dedupe at the database layer — the application-side dedupe
--     in CrowdStrikeInboundService is a fast path, this constraint is the
--     safety net.
--   * crowdstrike_action_log: one row per CONTAIN_HOST / LIFT_CONTAINMENT /
--     HIDE_HOST request. A retry burst produces multiple rows (one per
--     attempt) so attempt_count makes "final attempt" obvious. requested_by_user_id
--     comes from SecurityContext on the controller path; the scheduled poll
--     never writes action rows.
--   * V168 + V169 are intentionally skipped — earlier coordination attempts
--     reserved those slots and may have written partial state in some local
--     dev DBs. V170 sidesteps the ambiguity.
--   * Wrap in SET QUOTED_IDENTIFIER ON / SET ANSI_NULLS ON so the filtered
--     indexes accept the WHERE clauses (SQL Server requires those session
--     options to match between index creation and query execution).

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── platform.crowdstrike_detection_log ─────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'crowdstrike_detection_log')
BEGIN
    CREATE TABLE platform.crowdstrike_detection_log (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        falcon_detection_id     NVARCHAR(160)   NOT NULL,    -- e.g. ldt:0123456789abcdef:99001 (Falcon composite id)
        falcon_host_id          NVARCHAR(64),                -- device "aid" (agent id) — 32-char hex GUID
        severity                NVARCHAR(20),                -- CRITICAL / HIGH / MEDIUM / LOW (after mapping)
        severity_raw            INT,                         -- raw 1..100 Falcon severity
        tactic                  NVARCHAR(120),               -- MITRE ATT&CK tactic name
        technique               NVARCHAR(120),               -- MITRE ATT&CK technique name
        mitre_attack_id         NVARCHAR(40),                -- e.g. T1078 / T1078.004
        ingested_at             DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        security_event_id       BIGINT          NULL,        -- FK to socops.security_event (nullable: detection may be dropped/dedup'd)
        http_status             INT,                         -- 200 on a successful poll; NULL when this row records an internal-only event
        error_message           NVARCHAR(2000),
        is_deleted              BIT             NOT NULL DEFAULT 0,
        deleted_at              DATETIME2,
        deleted_by              BIGINT,
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT,
        updated_at              DATETIME2,
        updated_by              BIGINT,
        row_version             ROWVERSION,
        CONSTRAINT fk_cs_detection_security_event
            FOREIGN KEY (security_event_id) REFERENCES socops.security_event(id)
    );

    -- One row per Falcon detection id per tenant — the application-side
    -- dedupe in CrowdStrikeInboundService is the fast path; this is the
    -- safety net. Filtered so soft-deleted history doesn't block re-ingest.
    CREATE UNIQUE INDEX uq_cs_detection_falcon_id
        ON platform.crowdstrike_detection_log (organisation_id, falcon_detection_id)
        WHERE is_deleted = 0;

    CREATE INDEX ix_cs_detection_org_severity
        ON platform.crowdstrike_detection_log (organisation_id, severity)
        WHERE is_deleted = 0;

    CREATE INDEX ix_cs_detection_org_host
        ON platform.crowdstrike_detection_log (organisation_id, falcon_host_id)
        WHERE is_deleted = 0;
END
GO

-- ─── platform.crowdstrike_action_log ────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'crowdstrike_action_log')
BEGIN
    CREATE TABLE platform.crowdstrike_action_log (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        action_type             NVARCHAR(40)    NOT NULL,    -- CONTAIN_HOST / LIFT_CONTAINMENT / HIDE_HOST
        target_falcon_host_id   NVARCHAR(64)    NOT NULL,
        security_event_id       BIGINT          NULL,        -- FK — the SOC event that justified the action (when known)
        requested_by_user_id    BIGINT,                      -- SecurityContext user when called via REST; NULL for scheduled flows
        reason_text             NVARCHAR(500),               -- analyst-supplied justification (auditable)
        http_status             INT,                         -- 200/202 on success; 4xx/5xx on failure
        response_payload        NVARCHAR(MAX),               -- full Falcon JSON envelope for forensics
        attempt_count           INT             NOT NULL DEFAULT 1,
        error_message           NVARCHAR(2000),
        is_deleted              BIT             NOT NULL DEFAULT 0,
        deleted_at              DATETIME2,
        deleted_by              BIGINT,
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT,
        updated_at              DATETIME2,
        updated_by              BIGINT,
        row_version             ROWVERSION,
        CONSTRAINT fk_cs_action_security_event
            FOREIGN KEY (security_event_id) REFERENCES socops.security_event(id)
    );

    CREATE INDEX ix_cs_action_org_type_created
        ON platform.crowdstrike_action_log (organisation_id, action_type, created_at DESC)
        WHERE is_deleted = 0;

    CREATE INDEX ix_cs_action_org_host
        ON platform.crowdstrike_action_log (organisation_id, target_falcon_host_id, created_at DESC)
        WHERE is_deleted = 0;

    -- Failure-focused index: filtered indexes can't use OR, so we filter on
    -- "http_status >= 400" which covers 4xx + 5xx. NULL http_status rows
    -- (connection failures) won't match this filter but are visible via the
    -- main org_type_created index above.
    CREATE INDEX ix_cs_action_failures
        ON platform.crowdstrike_action_log (organisation_id, http_status, created_at DESC)
        WHERE is_deleted = 0 AND http_status >= 400;
END
GO

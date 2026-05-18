-- V102: real Crisis Event entity (independent audit Tier-1, item #2).
--
-- The pre-existing /crisis-management page rides on BcmInvocation +
-- CrisisCommsDistribution and is missing the columns ISO 22301 §8.4.2
-- and NCEMA expect on a crisis event proper:
--   - 5-level severity scale per spec §J (Operational Event → National Emergency)
--   - affected services/processes/sites/suppliers/systems as structured JSON arrays
--   - SitReps timeline, decisions log, regulatory notifications log
--   - linkage back to activated BCPs and DRPs
--   - trigger flags (personal data affected, cyber related, supplier involved)
--     so F8 automation rules can fan out PDPL / cyber / supplier follow-ups.
--
-- One parent table + three child tables (one per timeline stream).
-- Child tables use FK so deleting a crisis event cascades — but we soft-
-- delete parent rows so the cascade only matters for hard cleanups.

-- ─── Crisis events ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_events' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_events (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        event_code                  NVARCHAR(50)    NOT NULL,    -- e.g. CRIS-2026-000123

        title_en                    NVARCHAR(500)   NOT NULL,
        title_ar                    NVARCHAR(500)   NULL,
        description                 NVARCHAR(MAX)   NULL,

        -- Severity per spec §J — five levels:
        --   1 = Operational Event       — حدث تشغيلي
        --   2 = Significant Incident    — حادث مؤثر
        --   3 = Major Disruption        — تعطل جوهري
        --   4 = Crisis                  — أزمة
        --   5 = National Emergency      — طارئ وطني أو قطاعي
        severity                    INT             NOT NULL DEFAULT 1,

        trigger_source              NVARCHAR(50)    NULL,
        -- MANUAL | INCIDENT | EXERCISE | THREAT_INTEL | SUPPLIER | CYBER | EXTERNAL_ALERT

        -- Affected scope as JSON arrays of soft FKs:
        affected_services_json      NVARCHAR(MAX)   NULL,
        affected_processes_json     NVARCHAR(MAX)   NULL,
        affected_sites_json         NVARCHAR(MAX)   NULL,
        affected_suppliers_json     NVARCHAR(MAX)   NULL,
        affected_systems_json       NVARCHAR(MAX)   NULL,

        detection_time              DATETIME2       NULL,
        activation_time             DATETIME2       NULL,
        start_time                  DATETIME2       NULL,
        closure_time                DATETIME2       NULL,

        crisis_manager_id           BIGINT          NULL,
        crisis_team_json            NVARCHAR(MAX)   NULL,    -- array of {userId, role}
        root_cause                  NVARCHAR(MAX)   NULL,

        -- Lifecycle status — spec §J workflow steps 1..11:
        --   DETECTED → ASSESSING → ACTIVATED → CONTAINED → RECOVERED → CLOSED
        --   (CLOSED is terminal; from DETECTED you can also short-circuit
        --    to CANCELLED if it turns out to be a false alarm.)
        status                      NVARCHAR(20)    NOT NULL DEFAULT 'DETECTED',

        related_bcp_ids_json        NVARCHAR(MAX)   NULL,    -- BCPs activated for this event
        related_drp_ids_json        NVARCHAR(MAX)   NULL,    -- DRPs activated for this event

        -- Trigger flags — F8 rules read these to fan out cross-module follow-ups:
        personal_data_affected      BIT             NOT NULL DEFAULT 0,   -- → PDPL breach
        cyber_related               BIT             NOT NULL DEFAULT 0,   -- → cyber incident
        supplier_involved           BIT             NOT NULL DEFAULT 0,   -- → supplier incident

        lessons_learned_id          BIGINT          NULL,    -- soft FK to bcm.lessons_learned

        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX UX_crisis_events_org_code   ON bcm.crisis_events(organisation_id, event_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_crisis_events_org_status ON bcm.crisis_events(organisation_id, status)     WHERE is_deleted = 0;
    CREATE INDEX        IX_crisis_events_org_sev    ON bcm.crisis_events(organisation_id, severity)   WHERE is_deleted = 0;

    -- Range constraint on severity — keeps junk out of the column.
    ALTER TABLE bcm.crisis_events
        ADD CONSTRAINT CK_crisis_events_severity CHECK (severity BETWEEN 1 AND 5);
END
GO

-- ─── Situation reports (SitReps) ─────────────────────────────────
-- Crisis team logs these on a 30/60-minute cadence during a major event.
-- Each row is immutable once written; corrections are appended as new rows.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_situation_reports' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_situation_reports (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        crisis_event_id     BIGINT          NOT NULL,
        reported_at         DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        reported_by         BIGINT          NULL,
        summary             NVARCHAR(MAX)   NOT NULL,
        status_update       NVARCHAR(50)    NULL,    -- WORSENING | STABILISING | RECOVERING | NORMAL
        severity_change     INT             NULL,    -- new severity if this SitRep escalated/de-escalated
        attachments_json    NVARCHAR(MAX)   NULL,    -- array of evidence IDs
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_sitreps_event FOREIGN KEY (crisis_event_id) REFERENCES bcm.crisis_events(id)
    );
    CREATE INDEX IX_sitreps_event_time ON bcm.crisis_situation_reports(crisis_event_id, reported_at);
END
GO

-- ─── Decisions log ───────────────────────────────────────────────
-- Every material decision taken during the event — what / who / when /
-- rationale / action_required. Forms the spine of the after-action report.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_decisions' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_decisions (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        crisis_event_id     BIGINT          NOT NULL,
        decided_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        decided_by          BIGINT          NULL,
        decided_by_name     NVARCHAR(255)   NULL,    -- denormalised for after-action reporting
        decision_text       NVARCHAR(MAX)   NOT NULL,
        rationale           NVARCHAR(MAX)   NULL,
        action_required     NVARCHAR(MAX)   NULL,
        action_owner_id     BIGINT          NULL,
        action_due_date     DATE            NULL,
        capa_id             BIGINT          NULL,    -- soft FK to bcm.corrective_actions
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_decisions_event FOREIGN KEY (crisis_event_id) REFERENCES bcm.crisis_events(id)
    );
    CREATE INDEX IX_decisions_event_time ON bcm.crisis_decisions(crisis_event_id, decided_at);
END
GO

-- ─── Regulatory notifications log ────────────────────────────────
-- Captures every regulator notification with reference number + ack tracking.
-- Critical for SAMA / NCEMA / NCA / CMA / PDPL audit trails.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_regulatory_notifications' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_regulatory_notifications (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        crisis_event_id             BIGINT          NOT NULL,
        regulator                   NVARCHAR(50)    NOT NULL,
        -- NCEMA | SAMA | NCA | CMA | PDPL | CIVIL_DEFENCE | MOH | MOE | OTHER
        notified_at                 DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        notified_by                 BIGINT          NULL,
        notification_method         NVARCHAR(50)    NULL,
        -- EMAIL | PORTAL | PHONE | LETTER | API
        reference_number            NVARCHAR(100)   NULL,    -- regulator-issued reference
        body_summary                NVARCHAR(MAX)   NULL,
        acknowledgement_received    BIT             NOT NULL DEFAULT 0,
        acknowledgement_at          DATETIME2       NULL,
        acknowledgement_reference   NVARCHAR(100)   NULL,
        attachments_json            NVARCHAR(MAX)   NULL,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted                  BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_regnotif_event FOREIGN KEY (crisis_event_id) REFERENCES bcm.crisis_events(id)
    );
    CREATE INDEX IX_regnotif_event_time ON bcm.crisis_regulatory_notifications(crisis_event_id, notified_at);
END
GO

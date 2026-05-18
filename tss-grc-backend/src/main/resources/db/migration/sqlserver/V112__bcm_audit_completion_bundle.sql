-- V112: Audit-completion bundle — Tier-3 + Tier-4 follow-ups.
--
-- IMPORTANT — apply with: sqlcmd -f 65001 (UTF-8 input codepage on Windows).
-- File contains Arabic N'...' literals that mojibake under the default ANSI codepage.
--
-- Schema additions for seven audit items in one tight migration so the
-- history stays readable and the auto-restart bounce is single-shot:
--
--  Tier-4 #24 SAMA OPRES impact-tolerance extension
--    bcm.bia_assessments + impact_tolerance_hours        — SAMA Article 9 — the
--                                                          critical operation's
--                                                          tolerance window.
--    bcm.bia_assessments + opres_critical_operation_flag — flagged as a "critical
--                                                          operation" under
--                                                          SAMA OPRES.
--
--  Tier-4 #27 Sovereign / national-critical service tier
--    bcm.bia_assessments + national_critical_tier        — SOVEREIGN | NATIONAL
--                                                          | SECTORAL | NORMAL.
--                                                          Drives a colour overlay
--                                                          on the dependency graph
--                                                          and a Vision-2030 lens.
--
--  Tier-4 #29 PDPL Article 23 breach-continuity linkage
--    bcm.crisis_events + pdpl_notification_drafted_at
--    bcm.crisis_events + pdpl_notification_reference     — once the crisis service
--                                                          drafts a PDPL Art. 23
--                                                          notification these stamp
--                                                          the trail.
--
--  Tier-3 #18 Management Review workflow (ISO 22301 §9.3)
--    bcm.management_reviews                              — meeting + attendees +
--                                                          agenda + decisions + outputs.
--
--  Tier-3 #21 SIEM / SOAR inbound webhook
--    bcm.siem_inbound_events                             — every external webhook
--                                                          POST lands here and an F8
--                                                          rule decides whether to
--                                                          escalate to a crisis event.

SET NOCOUNT ON;
GO

-- ─── Tier-4 #24 + #27: bia_assessments column adds ──────────────
IF COL_LENGTH('bcm.bia_assessments','impact_tolerance_hours') IS NULL
BEGIN
    ALTER TABLE bcm.bia_assessments ADD impact_tolerance_hours INT NULL;
END
GO
IF COL_LENGTH('bcm.bia_assessments','opres_critical_operation_flag') IS NULL
BEGIN
    ALTER TABLE bcm.bia_assessments ADD opres_critical_operation_flag BIT NOT NULL DEFAULT 0;
END
GO
IF COL_LENGTH('bcm.bia_assessments','national_critical_tier') IS NULL
BEGIN
    ALTER TABLE bcm.bia_assessments ADD national_critical_tier NVARCHAR(20) NOT NULL DEFAULT 'NORMAL';
    -- SOVEREIGN | NATIONAL | SECTORAL | NORMAL
END
GO

-- ─── Tier-4 #29: crisis_events PDPL fields ──────────────────────
IF COL_LENGTH('bcm.crisis_events','pdpl_notification_drafted_at') IS NULL
BEGIN
    ALTER TABLE bcm.crisis_events ADD pdpl_notification_drafted_at DATETIME2 NULL;
END
GO
IF COL_LENGTH('bcm.crisis_events','pdpl_notification_reference') IS NULL
BEGIN
    ALTER TABLE bcm.crisis_events ADD pdpl_notification_reference NVARCHAR(100) NULL;
END
GO

-- ─── Tier-3 #18: management_reviews ─────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='management_reviews' AND schema_id=SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.management_reviews (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        review_code              NVARCHAR(50)    NOT NULL,    -- MR-2026-Q1
        cycle_id                 BIGINT          NULL,

        review_date              DATE            NOT NULL,
        chairperson_name         NVARCHAR(255)   NOT NULL,
        attendees_json           NVARCHAR(MAX)   NULL,        -- [{name,role}]

        agenda_json              NVARCHAR(MAX)   NULL,        -- [{item, presenter, minutes}]
        inputs_summary_en        NVARCHAR(MAX)   NULL,
        inputs_summary_ar        NVARCHAR(MAX)   NULL,

        -- ISO 22301 §9.3 reviewables — capture freshness as JSON snapshot
        --   { "openGaps": 8, "criticalGaps": 1, "exercisesCompleted": 4,
        --     "drTestsLast12mo": 6, "avgReadinessScore": 73.2 }
        kpi_snapshot_json        NVARCHAR(MAX)   NULL,

        decisions_json           NVARCHAR(MAX)   NULL,        -- [{decision, rationale, owner, dueDate}]
        improvement_actions_json NVARCHAR(MAX)   NULL,        -- [{action, owner, dueDate}]

        status                   NVARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | IN_PROGRESS | APPROVED | ARCHIVED
        approved_by              NVARCHAR(255)   NULL,
        approved_at              DATETIME2       NULL,

        notes_en                 NVARCHAR(MAX)   NULL,
        notes_ar                 NVARCHAR(MAX)   NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2       NULL,

        CONSTRAINT uq_management_review_code UNIQUE (organisation_id, review_code),
        CONSTRAINT ck_management_review_status
            CHECK (status IN ('DRAFT','IN_PROGRESS','APPROVED','ARCHIVED'))
    );
    CREATE INDEX IX_management_reviews_org_date ON bcm.management_reviews(organisation_id, review_date DESC);
    CREATE INDEX IX_management_reviews_org_status ON bcm.management_reviews(organisation_id, status);
END
GO

-- ─── Tier-3 #21: siem_inbound_events ────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='siem_inbound_events' AND schema_id=SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.siem_inbound_events (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        source_system            NVARCHAR(100)   NOT NULL,    -- SPLUNK | SENTINEL | QRADAR | XSOAR | CROWDSTRIKE | CUSTOM
        source_alert_id          NVARCHAR(255)   NULL,        -- upstream alert id
        severity                 INT             NOT NULL DEFAULT 1,
        -- 1..5 — same scale as bcm.crisis_events.severity

        title                    NVARCHAR(500)   NOT NULL,
        summary                  NVARCHAR(MAX)   NULL,
        raw_payload_json         NVARCHAR(MAX)   NULL,        -- original payload, kept for audit

        received_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        processed_at             DATETIME2       NULL,
        processing_status        NVARCHAR(30)    NOT NULL DEFAULT 'RECEIVED',
        -- RECEIVED | EVALUATED | ESCALATED | IGNORED | ERROR
        crisis_event_id          BIGINT          NULL,        -- soft FK if escalated
        error_message            NVARCHAR(MAX)   NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,

        CONSTRAINT ck_siem_severity CHECK (severity BETWEEN 1 AND 5),
        CONSTRAINT ck_siem_status   CHECK (processing_status IN
            ('RECEIVED','EVALUATED','ESCALATED','IGNORED','ERROR'))
    );
    CREATE INDEX IX_siem_inbound_org_received ON bcm.siem_inbound_events(organisation_id, received_at DESC);
    CREATE INDEX IX_siem_inbound_org_status   ON bcm.siem_inbound_events(organisation_id, processing_status);
END
GO

-- ─── Seed: backfill new BIA fields with sensible demo values ────
UPDATE bcm.bia_assessments
   SET national_critical_tier = CASE overall_criticality
                                    WHEN 'SOVEREIGN' THEN 'SOVEREIGN'
                                    WHEN 'CRITICAL'  THEN 'NATIONAL'
                                    WHEN 'HIGH'      THEN 'SECTORAL'
                                    ELSE 'NORMAL'
                                END,
       opres_critical_operation_flag = CASE WHEN overall_criticality IN ('SOVEREIGN','CRITICAL') THEN 1 ELSE 0 END,
       -- Impact tolerance is typically 2-3x MTPD per SAMA OPRES Article 9.
       -- Cap at MTPD when MTPD is itself huge so the constraint MTPD ≥ tolerance
       -- holds. (Tolerance is the absolute upper bound; some BCM dialects
       -- treat MTPD as the equivalent.)
       impact_tolerance_hours = CASE
                                    WHEN overall_criticality = 'SOVEREIGN' THEN COALESCE(mtpd_hours, 8)
                                    WHEN overall_criticality = 'CRITICAL'  THEN COALESCE(mtpd_hours, 12)
                                    WHEN overall_criticality = 'HIGH'      THEN COALESCE(mtpd_hours, 24)
                                    ELSE COALESCE(mtpd_hours, 72)
                                END
 WHERE organisation_id = 1
   AND (national_critical_tier IS NULL OR national_critical_tier = 'NORMAL');
GO

-- ─── Seed: one Q1 management review ─────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.management_reviews WHERE organisation_id = 1)
BEGIN
    INSERT INTO bcm.management_reviews
      (organisation_id, review_code, review_date, chairperson_name,
       attendees_json, agenda_json, inputs_summary_en, inputs_summary_ar,
       kpi_snapshot_json, decisions_json, improvement_actions_json,
       status, approved_by, approved_at,
       notes_en, notes_ar)
    VALUES
      (1, 'MR-2026-Q1', '2026-04-15', 'Khalid Al-Mutairi',
       N'[{"name":"Khalid Al-Mutairi","role":"BCM Manager"},{"name":"Sara Al-Anezi","role":"CISO"},{"name":"Mohammed Al-Faraj","role":"Core Banking Lead"},{"name":"CEO","role":"Executive Sponsor"}]',
       N'[{"item":"Status of last management review actions","presenter":"BCM Manager","minutes":10},{"item":"Q1 exercise + DR test results","presenter":"IT Recovery Owner","minutes":15},{"item":"Open gaps and CAPAs","presenter":"BCM Manager","minutes":15},{"item":"Resource & budget needs","presenter":"BCM Manager","minutes":10},{"item":"Decisions and improvement plan","presenter":"All","minutes":10}]',
       N'Q1 exercise cycle complete; 1 critical gap open; readiness score 73.2/100; 2 CAPAs overdue.',
       N'اكتملت دورة تمارين Q1؛ فجوة حرجة واحدة مفتوحة؛ مؤشر الجاهزية 73.2/100؛ إجراءان تصحيحيان متأخران.',
       N'{"openGaps":8,"criticalGaps":1,"exercisesCompleted":4,"drTestsLast12mo":6,"avgReadinessScore":73.2}',
       N'[{"decision":"Approve continued investment in dual-feed power upgrade","rationale":"Primary DC single-feed risk realised in Q4 2025","owner":"CFO","dueDate":"2026-06-30"},{"decision":"Mandate quarterly tabletops for tier-1 services","rationale":"Q1 ransomware tabletop exposed comms-tree gaps","owner":"BCM Manager","dueDate":"2026-12-31"}]',
       N'[{"action":"Procure Mobily MPLS contract","owner":"CIO","dueDate":"2026-05-31"},{"action":"Refresh call-tree contact data","owner":"BCM Manager","dueDate":"2026-05-15"}]',
       'APPROVED', 'CEO', '2026-04-15 16:00:00',
       N'Q1 management review completed per ISO 22301 §9.3. Two strategic decisions, two improvement actions assigned.',
       N'اكتملت مراجعة الإدارة Q1 وفق ISO 22301 §9.3. تم تكليف قرارين استراتيجيين وإجراءين تصحيحيين.');
END
GO

-- ─── Seed: a couple of demo SIEM events ─────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.siem_inbound_events WHERE organisation_id = 1)
BEGIN
    INSERT INTO bcm.siem_inbound_events
      (organisation_id, source_system, source_alert_id, severity, title,
       summary, raw_payload_json, processing_status)
    VALUES
      (1, 'SENTINEL', 'AZ-SENT-2026-0042', 4,
       'High-confidence credential-theft detection',
       'EDR flagged credential dumping on workstation WS-OPS-014; user account flagged for password reset.',
       N'{"alertId":"AZ-SENT-2026-0042","severity":"High","entities":["WS-OPS-014","user@example.com"],"mitre":["T1003"]}',
       'EVALUATED'),
      (1, 'CROWDSTRIKE', 'CS-IOA-2026-1801', 2,
       'Suspicious PowerShell execution',
       'Encoded PowerShell command observed on WS-DEV-021; auto-isolated by Falcon.',
       N'{"alertId":"CS-IOA-2026-1801","severity":"Medium","host":"WS-DEV-021","action":"Quarantine"}',
       'IGNORED');
END
GO

PRINT 'V112 audit-completion bundle applied.';
GO

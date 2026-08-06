-- V46 — Cyber → PDPL breach automation rule (gap-analysis item A).
-- ===========================================================================
-- When a CyberIncident is flagged as affecting personal data, the bridge
-- service auto-creates (or auto-links) a corresponding DataBreach in the
-- privacy module. Cross-link is bidirectional + every rule execution is
-- recorded in a bridge_log table for audit / forensics.
-- ===========================================================================

SET QUOTED_IDENTIFIER ON;
GO

-- ─── 1. Extend cyber.incidents with PII-impact + cross-link columns ─
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'cyber.incidents')
                 AND name = N'affects_personal_data')
BEGIN
    ALTER TABLE cyber.incidents
        ADD affects_personal_data        SMALLINT      NOT NULL DEFAULT 0,
            data_subjects_affected_est   INT           NULL,
            data_categories_affected     NVARCHAR(MAX) NULL,
            personal_data_pii_assessment NVARCHAR(MAX) NULL,
            linked_data_breach_id        BIGINT        NULL,
            linked_data_breach_code      NVARCHAR(64)  NULL,
            pii_classified_at            DATETIME2(3)  NULL,
            pii_classified_by            BIGINT        NULL;
END
GO

-- ─── 2. Extend privacy.data_breaches with source-incident columns ───
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'privacy.data_breaches')
                 AND name = N'source_cyber_incident_id')
BEGIN
    ALTER TABLE privacy.data_breaches
        ADD source_cyber_incident_id     BIGINT        NULL,
            source_cyber_incident_number NVARCHAR(64)  NULL,
            auto_generated               SMALLINT      NOT NULL DEFAULT 0,
            auto_generation_rule         NVARCHAR(64)  NULL,
            auto_generated_at            DATETIME2(3)  NULL;
END
GO

-- ─── 3. New audit table — every bridge rule execution ──────────────
IF OBJECT_ID(N'privacy.cyber_breach_bridge_log', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.cyber_breach_bridge_log (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT       NOT NULL,

        event_code                  NVARCHAR(64) NOT NULL,
                                                      -- BRIDGE_FIRED, BRIDGE_SKIPPED,
                                                      -- BREACH_CREATED, BREACH_LINKED, ERROR

        cyber_incident_id           BIGINT       NULL,
        cyber_incident_number       NVARCHAR(64) NULL,
        data_breach_id              BIGINT       NULL,
        data_breach_external_id     NVARCHAR(64) NULL,

        rule_name                   NVARCHAR(64) NOT NULL DEFAULT N'CYBER_TO_PDPL_V1',
        trigger_reason              NVARCHAR(500) NULL,
        outcome                     NVARCHAR(20)  NOT NULL,
                                                      -- CREATED | LINKED | SKIPPED | ERROR
        outcome_detail              NVARCHAR(MAX) NULL,

        triggered_at                DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        triggered_by                BIGINT        NULL,
        triggered_by_username       NVARCHAR(255) NULL,

        is_deleted                  SMALLINT      NOT NULL DEFAULT 0,
        is_active                   SMALLINT      NOT NULL DEFAULT 1,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT CK_bridge_event CHECK (event_code IN
            (N'BRIDGE_FIRED', N'BRIDGE_SKIPPED',
             N'BREACH_CREATED', N'BREACH_LINKED', N'ERROR')),
        CONSTRAINT CK_bridge_outcome CHECK (outcome IN
            (N'CREATED', N'LINKED', N'SKIPPED', N'ERROR'))
    );
END
GO

IF OBJECT_ID(N'privacy.cyber_breach_bridge_log', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bridge_org_when'
                 AND object_id = OBJECT_ID(N'privacy.cyber_breach_bridge_log'))
BEGIN
    CREATE INDEX IX_bridge_org_when ON privacy.cyber_breach_bridge_log
        (organisation_id, triggered_at DESC) WHERE is_deleted = 0;
END
GO

-- ─── 4. Demo seed: classify 2 existing cyber incidents as PII-affecting
--      and create matching DataBreach rows + bridge_log entries. ─────
-- Find two cyber.incidents rows to enrich. If the cyber.incidents table
-- has at least 2 rows we use those; otherwise the seed is a no-op.

IF (SELECT COUNT(*) FROM cyber.incidents WHERE is_deleted = 0) >= 2
AND NOT EXISTS (SELECT 1 FROM privacy.cyber_breach_bridge_log
                WHERE event_code = N'BREACH_CREATED' AND rule_name = N'SEED_DEMO_V46')
BEGIN
    DECLARE @inc1 BIGINT, @inc1num NVARCHAR(64);
    DECLARE @inc2 BIGINT, @inc2num NVARCHAR(64);

    -- F2-77: `cyber.incidents` has no `incident_number` column and never had one — V1
    -- creates it without, and no migration adds it. The name belongs to the SIBLING table
    -- `cyber.cyber_incidents`, which does carry `incident_number`. So this seed read a
    -- column off the wrong table, and because SQL Server binds column names for the whole
    -- batch at compile time, the runtime guard above could not save it: V46 failed even
    -- though the branch would not have executed on an empty database.
    --
    -- The value is used in 14 string concatenations below, so it must be non-NULL: in
    -- SQL Server `N'text ' + NULL` is NULL, and a NULL here would silently blank every
    -- description this seed writes. `cyber.incidents` identifies a row by `id` alone, so
    -- the reference is that identifier formatted — not an invented code, which is the
    -- fabricated-seed-data problem F2-139 records one table over.
    SELECT TOP 1 @inc1 = id, @inc1num = CONCAT(N'INC-', id)
      FROM cyber.incidents WHERE is_deleted = 0 ORDER BY id ASC;
    SELECT TOP 1 @inc2 = id, @inc2num = CONCAT(N'INC-', id)
      FROM cyber.incidents WHERE is_deleted = 0 AND id <> @inc1 ORDER BY id ASC;

    -- Classify incidents as affecting personal data
    UPDATE cyber.incidents
       SET affects_personal_data        = 1,
           data_subjects_affected_est   = 1450,
           data_categories_affected     = N'EMAIL,NATIONAL_ID,PHONE_NUMBER',
           personal_data_pii_assessment = N'Phishing campaign captured 1,450 employee credentials; downstream M365 mailbox access exposed contact directory + national-ID copies stored in HR pipeline staging.',
           pii_classified_at            = DATEADD(DAY, -4, SYSUTCDATETIME()),
           pii_classified_by            = 1
     WHERE id = @inc1;

    UPDATE cyber.incidents
       SET affects_personal_data        = 1,
           data_subjects_affected_est   = 380,
           data_categories_affected     = N'EMAIL,SALARY',
           personal_data_pii_assessment = N'Misconfigured S3 bucket exposed Q1 payroll export (380 employee records, no national ID) for 6 hours; access logs reviewed; no third-party reads confirmed.',
           pii_classified_at            = DATEADD(DAY, -10, SYSUTCDATETIME()),
           pii_classified_by            = 1
     WHERE id = @inc2;

    -- Create matching DataBreach rows (auto-generated)
    DECLARE @newBreach1 BIGINT, @newBreach2 BIGINT;
    -- F2-77: `privacy.data_breaches.external_id` is a UNIQUEIDENTIFIER in V1 and in the live
    -- schema, and this seed was inserting a `DBR_AUTO_<hex>` STRING into it — written against
    -- an imagined nvarchar 'code' column. Two distinct values are needed, and conflating them
    -- is what broke the file:
    --   @extId*  the row's real external_id, a GUID, as every other table uses.
    --   @ext*    the human-readable label, which belongs in the columns that are nvarchar --
    --            cyber_breach_bridge_log.data_breach_external_id and
    --            cyber.incidents.linked_data_breach_code.
    DECLARE @extId1 UNIQUEIDENTIFIER = NEWID();
    DECLARE @extId2 UNIQUEIDENTIFIER = NEWID();
    DECLARE @ext1 NVARCHAR(64) = N'DBR_AUTO_' + LEFT(REPLACE(CAST(@extId1 AS NVARCHAR(36)),N'-',N''),12);
    DECLARE @ext2 NVARCHAR(64) = N'DBR_AUTO_' + LEFT(REPLACE(CAST(@extId2 AS NVARCHAR(36)),N'-',N''),12);

    INSERT INTO privacy.data_breaches
        (organisation_id, external_id, title_en, description_en,
         breach_type, discovered_at, data_subjects_affected_count,
         pdpc_notification_required, status,
         source_cyber_incident_id, source_cyber_incident_number,
         auto_generated, auto_generation_rule, auto_generated_at,
         is_deleted, created_at)
    VALUES
        (1, @extId1,
         N'Auto-generated breach from cyber incident ' + @inc1num,
         N'Cyber → PDPL bridge rule v1 created this breach automatically when cyber incident ' + @inc1num + N' was classified as affecting personal data. 1,450 data subjects estimated. See linked cyber incident for full IR detail.',
         N'UNAUTHORIZED_ACCESS',
         DATEADD(DAY, -4, SYSUTCDATETIME()),
         1450, 1, N'INVESTIGATING',
         @inc1, @inc1num,
         1, N'CYBER_TO_PDPL_V1', DATEADD(DAY, -4, SYSUTCDATETIME()),
         0, DATEADD(DAY, -4, SYSUTCDATETIME()));
    SET @newBreach1 = SCOPE_IDENTITY();

    INSERT INTO privacy.data_breaches
        (organisation_id, external_id, title_en, description_en,
         breach_type, discovered_at, data_subjects_affected_count,
         pdpc_notification_required, status,
         source_cyber_incident_id, source_cyber_incident_number,
         auto_generated, auto_generation_rule, auto_generated_at,
         is_deleted, created_at)
    VALUES
        (1, @extId2,
         N'Auto-generated breach from cyber incident ' + @inc2num,
         N'Cyber → PDPL bridge rule v1 created this breach automatically when cyber incident ' + @inc2num + N' was classified as affecting personal data. 380 data subjects estimated. Lower severity — no national-ID category affected.',
         N'ACCIDENTAL_DISCLOSURE',
         DATEADD(DAY, -10, SYSUTCDATETIME()),
         380, 0, N'OPEN',
         @inc2, @inc2num,
         1, N'CYBER_TO_PDPL_V1', DATEADD(DAY, -10, SYSUTCDATETIME()),
         0, DATEADD(DAY, -10, SYSUTCDATETIME()));
    SET @newBreach2 = SCOPE_IDENTITY();

    -- Cross-link incidents back
    UPDATE cyber.incidents
       SET linked_data_breach_id   = @newBreach1,
           linked_data_breach_code = @ext1
     WHERE id = @inc1;
    UPDATE cyber.incidents
       SET linked_data_breach_id   = @newBreach2,
           linked_data_breach_code = @ext2
     WHERE id = @inc2;

    -- Bridge log entries
    INSERT INTO privacy.cyber_breach_bridge_log
        (organisation_id, event_code, cyber_incident_id, cyber_incident_number,
         data_breach_id, data_breach_external_id,
         rule_name, trigger_reason, outcome, outcome_detail,
         triggered_at, triggered_by, triggered_by_username)
    VALUES
        (1, N'BRIDGE_FIRED', @inc1, @inc1num, NULL, NULL,
         N'SEED_DEMO_V46',
         N'Cyber incident classified as affecting personal data (affects_personal_data=true, 1450 data subjects)',
         N'CREATED', N'Auto-created DataBreach UNAUTHORIZED_ACCESS; PDPL notification required (SDAIA 72h SLA).',
         DATEADD(DAY, -4, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BREACH_CREATED', @inc1, @inc1num, @newBreach1, @ext1,
         N'SEED_DEMO_V46',
         N'Auto-created DataBreach for cyber incident ' + @inc1num,
         N'CREATED', N'DataBreach ' + @ext1 + N' created and bidirectionally linked to incident ' + @inc1num,
         DATEADD(DAY, -4, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BRIDGE_FIRED', @inc2, @inc2num, NULL, NULL,
         N'SEED_DEMO_V46',
         N'Cyber incident classified as affecting personal data (affects_personal_data=true, 380 data subjects, no national-ID)',
         N'CREATED', N'Auto-created DataBreach ACCIDENTAL_DISCLOSURE; PDPL notification optional (no national-ID, < 500 subjects).',
         DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BREACH_CREATED', @inc2, @inc2num, @newBreach2, @ext2,
         N'SEED_DEMO_V46',
         N'Auto-created DataBreach for cyber incident ' + @inc2num,
         N'CREATED', N'DataBreach ' + @ext2 + N' created and bidirectionally linked to incident ' + @inc2num,
         DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin'),
        -- a SKIPPED example for variety
        (1, N'BRIDGE_SKIPPED', @inc1, @inc1num, @newBreach1, @ext1,
         N'CYBER_TO_PDPL_V1',
         N'Bridge re-evaluation triggered by status change (DETECTION → TRIAGE); incident already linked',
         N'SKIPPED', N'DataBreach already exists for this cyber incident; no action taken.',
         DATEADD(DAY, -2, SYSUTCDATETIME()), 1, N'admin');
END
GO

PRINT N'V46 — cyber→PDPL bridge: schema columns + cyber_breach_bridge_log + demo seed applied.';
GO

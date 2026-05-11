-- V46a — Seed fix for V46. cyber.incidents has no `incident_number`
-- column — we synthesise "INC-{id}" for cross-reference and skip seed
-- if the bridge already fired for these incidents.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF (SELECT COUNT(*) FROM cyber.incidents WHERE is_deleted = 0) >= 2
AND NOT EXISTS (SELECT 1 FROM privacy.cyber_breach_bridge_log
                WHERE rule_name = N'SEED_DEMO_V46')
BEGIN
    DECLARE @inc1 BIGINT, @inc1num NVARCHAR(64), @inc1title NVARCHAR(255);
    DECLARE @inc2 BIGINT, @inc2num NVARCHAR(64), @inc2title NVARCHAR(255);

    -- Pick the phishing incident if present (id 1 in seed data)
    SELECT TOP 1 @inc1 = id, @inc1title = title FROM cyber.incidents
      WHERE is_deleted = 0 AND title LIKE N'%Phishing%' ORDER BY id ASC;
    IF @inc1 IS NULL
        SELECT TOP 1 @inc1 = id, @inc1title = title FROM cyber.incidents
          WHERE is_deleted = 0 ORDER BY id ASC;
    SET @inc1num = N'INC-' + RIGHT(N'0000' + CAST(@inc1 AS NVARCHAR(10)), 5);

    -- Pick the data-exposure incident (Unencrypted Customer Data in Shared Drive)
    SELECT TOP 1 @inc2 = id, @inc2title = title FROM cyber.incidents
      WHERE is_deleted = 0 AND id <> @inc1
        AND (title LIKE N'%Unencrypted%' OR title LIKE N'%Customer Data%')
      ORDER BY id ASC;
    IF @inc2 IS NULL
        SELECT TOP 1 @inc2 = id, @inc2title = title FROM cyber.incidents
          WHERE is_deleted = 0 AND id <> @inc1 ORDER BY id ASC;
    SET @inc2num = N'INC-' + RIGHT(N'0000' + CAST(@inc2 AS NVARCHAR(10)), 5);

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

    -- Create matching DataBreach rows
    -- privacy.data_breaches.external_id is uniqueidentifier — use NEWID() directly
    DECLARE @newBreach1 BIGINT, @newBreach2 BIGINT;
    DECLARE @ext1 UNIQUEIDENTIFIER = NEWID();
    DECLARE @ext2 UNIQUEIDENTIFIER = NEWID();
    DECLARE @ext1str NVARCHAR(64) = CONVERT(NVARCHAR(64), @ext1);
    DECLARE @ext2str NVARCHAR(64) = CONVERT(NVARCHAR(64), @ext2);

    INSERT INTO privacy.data_breaches
        (organisation_id, external_id, title_en, description_en,
         breach_type, discovered_at, data_subjects_affected_count,
         pdpc_notification_required, status,
         source_cyber_incident_id, source_cyber_incident_number,
         auto_generated, auto_generation_rule, auto_generated_at,
         is_deleted, created_at)
    VALUES
        (1, @ext1,
         N'Auto-generated breach from cyber incident ' + @inc1num + N' — ' + @inc1title,
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
        (1, @ext2,
         N'Auto-generated breach from cyber incident ' + @inc2num + N' — ' + @inc2title,
         N'Cyber → PDPL bridge rule v1 created this breach automatically when cyber incident ' + @inc2num + N' was classified as affecting personal data. 380 data subjects estimated. Lower severity — no national-ID category affected.',
         N'ACCIDENTAL_DISCLOSURE',
         DATEADD(DAY, -10, SYSUTCDATETIME()),
         380, 0, N'OPEN',
         @inc2, @inc2num,
         1, N'CYBER_TO_PDPL_V1', DATEADD(DAY, -10, SYSUTCDATETIME()),
         0, DATEADD(DAY, -10, SYSUTCDATETIME()));
    SET @newBreach2 = SCOPE_IDENTITY();

    UPDATE cyber.incidents
       SET linked_data_breach_id   = @newBreach1,
           linked_data_breach_code = @ext1str
     WHERE id = @inc1;
    UPDATE cyber.incidents
       SET linked_data_breach_id   = @newBreach2,
           linked_data_breach_code = @ext2str
     WHERE id = @inc2;

    INSERT INTO privacy.cyber_breach_bridge_log
        (organisation_id, event_code, cyber_incident_id, cyber_incident_number,
         data_breach_id, data_breach_external_id,
         rule_name, trigger_reason, outcome, outcome_detail,
         triggered_at, triggered_by, triggered_by_username)
    VALUES
        (1, N'BRIDGE_FIRED', @inc1, @inc1num, NULL, NULL,
         N'SEED_DEMO_V46',
         N'Cyber incident classified as affecting personal data (1450 data subjects, NATIONAL_ID category)',
         N'CREATED', N'Auto-created DataBreach UNAUTHORIZED_ACCESS; SDAIA notification required (72h SLA).',
         DATEADD(DAY, -4, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BREACH_CREATED', @inc1, @inc1num, @newBreach1, @ext1str,
         N'SEED_DEMO_V46',
         N'Auto-created DataBreach for cyber incident ' + @inc1num,
         N'CREATED', N'DataBreach ' + @ext1str + N' created and bidirectionally linked to incident ' + @inc1num,
         DATEADD(DAY, -4, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BRIDGE_FIRED', @inc2, @inc2num, NULL, NULL,
         N'SEED_DEMO_V46',
         N'Cyber incident classified as affecting personal data (380 data subjects, no national-ID)',
         N'CREATED', N'Auto-created DataBreach ACCIDENTAL_DISCLOSURE; SDAIA notification optional.',
         DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BREACH_CREATED', @inc2, @inc2num, @newBreach2, @ext2str,
         N'SEED_DEMO_V46',
         N'Auto-created DataBreach for cyber incident ' + @inc2num,
         N'CREATED', N'DataBreach ' + @ext2str + N' created and bidirectionally linked to incident ' + @inc2num,
         DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin'),
        (1, N'BRIDGE_SKIPPED', @inc1, @inc1num, @newBreach1, @ext1str,
         N'CYBER_TO_PDPL_V1',
         N'Bridge re-evaluation triggered by status change; incident already linked',
         N'SKIPPED', N'DataBreach already exists for this cyber incident; no action taken.',
         DATEADD(DAY, -2, SYSUTCDATETIME()), 1, N'admin');
END
GO

PRINT N'V46a — bridge seed applied (2 incidents classified, 2 breaches auto-created, 5 bridge log entries).';
GO

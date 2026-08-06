-- V124: add unique constraint on bcm.crisis_aar.report_code per tenant.
--
-- Backstops the race-tolerance loop in CrisisAarService.nextReportCode().
-- Without this, two concurrent generate() calls could both compute N+1
-- and persist duplicate report codes, breaking downstream reports that
-- treat report_code as an identifier.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF OBJECT_ID('bcm.crisis_aar') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_crisis_aar_org_report_code' AND object_id = OBJECT_ID('bcm.crisis_aar'))
BEGIN
    -- First defuse any duplicates that V117 demo seeds + a prior race may
    -- have left behind — suffix collisions with a short hex tag so the
    -- unique index can be created cleanly.
    ;WITH dups AS (
        SELECT id, report_code,
               ROW_NUMBER() OVER (PARTITION BY organisation_id, report_code
                                  ORDER BY id) AS rn
          FROM bcm.crisis_aar
    )
    UPDATE bcm.crisis_aar
       SET report_code = CONCAT(d.report_code, N'-DUP',
                                RIGHT(LOWER(CONVERT(NVARCHAR(36), NEWID())), 4))
      FROM dups d
     WHERE bcm.crisis_aar.id = d.id
       AND d.rn > 1;

    CREATE UNIQUE INDEX UQ_crisis_aar_org_report_code
        ON bcm.crisis_aar(organisation_id, report_code)
     WHERE is_deleted = 0;
END
GO

PRINT 'V124 unique constraint on bcm.crisis_aar(organisation_id, report_code) applied.';
GO

-- V307 (CH-17): change freeze / blackout windows.
--
-- No freeze, blackout, or maintenance-window concept existed for Change: the
-- FE's advisory MONTH_END_FREEZE dropdown value was never enforced server-side,
-- so a change could be scheduled straight into a financial close or peak-trade
-- window. This table holds per-tenant freeze windows; ChangeManagementService
-- rejects (409) a move to SCHEDULED whose [scheduled_start, scheduled_end]
-- intersects an active freeze — unless the change is EMERGENCY or the window
-- explicitly allows emergency changes.
--
-- scope = ALL (whole org) or CATEGORY (only changes of scope_value's category).
-- Idempotent on (organisation_id, code).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'change_mgmt' AND t.name = 'change_freeze_window')
BEGIN
    CREATE TABLE change_mgmt.change_freeze_window (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT       NOT NULL,
        code             NVARCHAR(40)  NOT NULL,
        name_en          NVARCHAR(200) NOT NULL,
        name_ar          NVARCHAR(200) NULL,
        starts_at        DATETIME2    NOT NULL,
        ends_at          DATETIME2    NOT NULL,
        scope            NVARCHAR(20)  NOT NULL CONSTRAINT DF_cfw_scope DEFAULT ('ALL'),
        scope_value      NVARCHAR(50)  NULL,      -- category when scope = CATEGORY
        allow_emergency  BIT          NOT NULL CONSTRAINT DF_cfw_emg DEFAULT (1),
        active           BIT          NOT NULL CONSTRAINT DF_cfw_active DEFAULT (1),
        notes_en         NVARCHAR(MAX) NULL,
        notes_ar         NVARCHAR(MAX) NULL,
        is_deleted       BIT          NOT NULL CONSTRAINT DF_cfw_del DEFAULT (0),
        created_at       DATETIME2    NOT NULL CONSTRAINT DF_cfw_created DEFAULT (SYSUTCDATETIME()),
        created_by       NVARCHAR(255) NULL,
        CONSTRAINT CK_cfw_scope CHECK (scope IN ('ALL','CATEGORY'))
    );
    CREATE UNIQUE INDEX UX_cfw_org_code
        ON change_mgmt.change_freeze_window (organisation_id, code)
        WHERE is_deleted = 0;
END
GO

-- Illustrative year-end financial-close freeze per tenant (allows emergencies).
;WITH f (code, name_en, name_ar, starts_at, ends_at, scope, allow_emergency, notes_en, notes_ar) AS (
    SELECT * FROM (VALUES
        (N'YEAR-END-2026',
         N'Year-End Financial Close Freeze',
         N'تجميد الإغلاق المالي لنهاية العام',
         CONVERT(DATETIME2, '2026-12-20T00:00:00'), CONVERT(DATETIME2, '2027-01-05T23:59:59'),
         N'ALL', 1,
         N'No non-emergency production changes during the year-end financial close.',
         N'لا تغييرات إنتاجية غير طارئة خلال فترة الإغلاق المالي لنهاية العام.')
    ) v (code, name_en, name_ar, starts_at, ends_at, scope, allow_emergency, notes_en, notes_ar)
)
INSERT INTO change_mgmt.change_freeze_window
    (organisation_id, code, name_en, name_ar, starts_at, ends_at, scope, allow_emergency, notes_en, notes_ar)
SELECT o.id, f.code, f.name_en, f.name_ar, f.starts_at, f.ends_at, f.scope, f.allow_emergency, f.notes_en, f.notes_ar
FROM f
CROSS JOIN platform.organisations o
WHERE ISNULL(o.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_freeze_window cfw
                  WHERE cfw.organisation_id = o.id AND cfw.code = f.code);
GO

PRINT 'V307: created change_mgmt.change_freeze_window + seeded a year-end freeze per tenant.';
GO

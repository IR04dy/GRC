-- V356 — S8g / CE-8: the forward schedule becomes publishable, and an outage becomes declarable.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-05
-- ════════════════════════════════════════════════════════════════════════════════════════
-- G-8 (severity M): "No FSC/PSO — no published forward schedule, no declared outage,
-- collision detection unpublished."
--
-- ⚠ THE PLAN'S TABLE SHAPE WAS WRONG, AND MEASURING SAID SO BEFORE ANY CODE WAS WRITTEN.
-- §14 specifies `change_schedule_entry` carrying `change_id`, `window_start`, `window_end`.
-- But `change_requests` ALREADY carries `scheduled_start` and `scheduled_end`, populated on
-- **15 of 17** changes in dev, and `/calendar` has served them since CH-17. A second table
-- holding the same window would give the platform two answers to "when is this change
-- happening?" — and the moment they disagreed, the FSC and the change register would each be
-- able to cite themselves. That is the duplication S7e refused when it declined to store a
-- `CONDITIONAL` literal, and S8f refused again by deriving findings instead of storing them.
--
-- So the window is NOT re-modelled. What measurement showed is genuinely absent:
--
--     tables holding an outage / maintenance-window concept ....... 0 platform-wide
--       (only `reporting.report_schedules`, which schedules reports)
--     columns on change_requests naming an outage or service ...... 0 of 52
--     any record of the schedule having been PUBLISHED ............ none
--
-- The live calendar changes every time somebody reschedules, and nothing anywhere records
-- what the business was actually told. "Published" is the whole of the FSC as a control: an
-- unpublished forward schedule is a query, not a communication.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHAT THIS MIGRATION ADDS, AND THE TWO CONSTRAINTS THAT ARE THE POINT
-- ════════════════════════════════════════════════════════════════════════════════════════
-- 1. PSO on the change (`outage_expected`, `outage_minutes`, `services_affected`).
--    `ck_change_pso` refuses `outage_expected = 1` without a positive duration AND the
--    services affected. "There will be an outage" with no length and no named service is not
--    a declaration, it is a rumour — the same reasoning that produced `ck_cmodel_preapproval`
--    (V354) and `ck_ucd_disposed` (V355).
--
--    ⚠ It arrives INERT and this migration asserts it: `outage_expected` defaults 0 and
--    self-check 4 THROWs if any existing row carries 1. No change already in flight is
--    refused (S6c's `quorum_requires_chair` reasoning, third application).
--
-- 2. `change_schedule_publication` — the publication act, with the schedule it published.
--    Following `board_pack.packs` (V26), whose own comment says the JSON snapshots exist "so
--    the pack reflects a fixed point": the entries are captured at publish time, because the
--    question an auditor asks is "what did you tell the business on the 4th?", and deriving
--    that from today's rows answers a different question. A SHA-256 digest over the snapshot
--    follows the evidence subsystem's convention, so a published FSC is tamper-evident.
--
--    ⚠ THE ROW IS IMMUTABLE BY DESIGN. There is no `updated_at` and no status column: a
--    published schedule that can be edited afterwards is not a publication. Superseding one
--    means publishing again, which is why the natural read is "latest by published_at" and
--    why nothing here is a soft-delete workflow.
--
-- Applied twice to dev by hand (Flyway is OFF in dev). Every self-check THROWs and every one
-- is true on the second pass.
-- ════════════════════════════════════════════════════════════════════════════════════════

-- ── 1. PSO columns ───────────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_requests'
                 AND COLUMN_NAME='outage_expected')
    ALTER TABLE change_mgmt.change_requests
        ADD outage_expected BIT NOT NULL CONSTRAINT df_change_outage_expected DEFAULT 0;
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_requests'
                 AND COLUMN_NAME='outage_minutes')
    ALTER TABLE change_mgmt.change_requests ADD outage_minutes INT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_requests'
                 AND COLUMN_NAME='services_affected')
    ALTER TABLE change_mgmt.change_requests ADD services_affected NVARCHAR(1000) NULL;
GO

-- ⚠ The constraint this half of the migration exists for.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name='ck_change_pso'
                 AND parent_object_id=OBJECT_ID('change_mgmt.change_requests'))
    ALTER TABLE change_mgmt.change_requests ADD CONSTRAINT ck_change_pso CHECK (
        outage_expected = 0
        OR (outage_minutes IS NOT NULL AND outage_minutes > 0
            AND services_affected IS NOT NULL AND LEN(LTRIM(services_affected)) > 0));
GO

-- ── 2. the publication act ───────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
               WHERE s.name='change_mgmt' AND t.name='change_schedule_publication')
BEGIN
    CREATE TABLE change_mgmt.change_schedule_publication (
        id                 BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id    BIGINT         NOT NULL,
        -- The period the published schedule covers. NOT the period it was published in.
        window_from        DATETIME2(6)   NOT NULL,
        window_to          DATETIME2(6)   NOT NULL,
        -- Counts, so a list of publications is readable without parsing every snapshot.
        entry_count        INT            NOT NULL,
        outage_count       INT            NOT NULL CONSTRAINT df_csp_outage DEFAULT 0,
        freeze_count       INT            NOT NULL CONSTRAINT df_csp_freeze DEFAULT 0,
        -- The snapshot. Following board_pack.packs (V26): captured at publish time, because
        -- "what did we tell the business" cannot be re-derived from rows that have since moved.
        entries_json       NVARCHAR(MAX)  NOT NULL,
        -- SHA-256 over entries_json, following the evidence subsystem: a published schedule
        -- that cannot be shown to be unaltered is a claim about the past with nothing behind it.
        content_sha256     CHAR(64)       NOT NULL,
        published_by_id    BIGINT         NULL,
        published_by_name  NVARCHAR(255)  NOT NULL,
        published_at       DATETIME2(6)   NOT NULL,
        notes              NVARCHAR(2000) NULL,
        is_deleted         BIT            NOT NULL CONSTRAINT df_csp_del DEFAULT 0,
        created_at         DATETIME2(6)   NOT NULL CONSTRAINT df_csp_created DEFAULT SYSUTCDATETIME(),

        CONSTRAINT pk_change_schedule_publication PRIMARY KEY CLUSTERED (id),

        -- A window that ends before it starts describes no period at all.
        CONSTRAINT ck_csp_window CHECK (window_to > window_from),

        -- ⚠ The constraint this half exists for: a publication names who published it, when,
        -- and carries a full-length digest of what was published.
        CONSTRAINT ck_csp_published CHECK (
            LEN(LTRIM(published_by_name)) > 0
            AND LEN(content_sha256) = 64
            AND entry_count >= 0 AND outage_count >= 0 AND freeze_count >= 0),

        -- Deliberately NO updated_at and NO status: see the header. Immutability is the point.
        CONSTRAINT ck_csp_counts CHECK (outage_count <= entry_count AND freeze_count <= entry_count)
    );
END
GO

IF OBJECT_ID('change_mgmt.change_schedule_publication') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_csp_org_published' AND object_id = OBJECT_ID('change_mgmt.change_schedule_publication'))
    CREATE INDEX ix_csp_org_published
        ON change_mgmt.change_schedule_publication (organisation_id, published_at DESC)
        WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. Every column the entities map is present.
DECLARE @m1 NVARCHAR(MAX) = '';
SELECT @m1 = @m1 + c.n + ' '
FROM (VALUES ('outage_expected'), ('outage_minutes'), ('services_affected')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_requests'
                    AND COLUMN_NAME=c.n);
IF LEN(@m1) > 0
    THROW 56001, 'V356: change_requests is missing the PSO columns the entity maps.', 1;
GO

DECLARE @m2 NVARCHAR(MAX) = '';
SELECT @m2 = @m2 + c.n + ' '
FROM (VALUES ('organisation_id'), ('window_from'), ('window_to'), ('entry_count'),
             ('outage_count'), ('freeze_count'), ('entries_json'), ('content_sha256'),
             ('published_by_id'), ('published_by_name'), ('published_at'), ('notes'),
             ('is_deleted'), ('created_at')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_schedule_publication'
                    AND COLUMN_NAME=c.n);
IF LEN(@m2) > 0
    THROW 56002, 'V356: change_schedule_publication is missing columns the entity maps.', 1;
GO

-- 2. ⚠ The publication row must stay IMMUTABLE — no updated_at, no status. If either appears,
--    somebody has started modelling a publication that can be revised, and a revisable
--    publication is not a publication.
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA='change_mgmt' AND TABLE_NAME='change_schedule_publication'
             AND COLUMN_NAME IN ('updated_at', 'status'))
    THROW 56003, 'V356: change_schedule_publication has grown updated_at or status. A published schedule that can be edited afterwards is not a publication — supersede it by publishing again.', 1;
GO

-- 3. All four constraints exist, and are TRUSTED and ENABLED. A disabled CHECK is a comment.
DECLARE @missingCk NVARCHAR(MAX) = '';
SELECT @missingCk = @missingCk + c.n + ' '
FROM (VALUES ('ck_change_pso')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM sys.check_constraints
                  WHERE name = c.n AND parent_object_id = OBJECT_ID('change_mgmt.change_requests'));
SELECT @missingCk = @missingCk + c.n + ' '
FROM (VALUES ('ck_csp_window'), ('ck_csp_published'), ('ck_csp_counts')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM sys.check_constraints
                  WHERE name = c.n
                    AND parent_object_id = OBJECT_ID('change_mgmt.change_schedule_publication'));
IF LEN(@missingCk) > 0
    THROW 56004, 'V356: one or more CHECK constraints are missing.', 1;
GO

DECLARE @msgTrust NVARCHAR(2048) = N'V356: a CHECK exists but is disabled or not trusted. An '
    + N'unenforced constraint on a published schedule or a declared outage advertises a '
    + N'guarantee it is not keeping.';
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE ((parent_object_id = OBJECT_ID('change_mgmt.change_requests')
                   AND name = 'ck_change_pso')
              OR  (parent_object_id = OBJECT_ID('change_mgmt.change_schedule_publication')
                   AND name IN ('ck_csp_window', 'ck_csp_published', 'ck_csp_counts')))
             AND (is_disabled = 1 OR is_not_trusted = 1))
    THROW 56005, @msgTrust, 1;
GO

-- 4. The PSO flag arrives INERT — measured, not promised. Every existing change must carry 0,
--    so this migration refuses nothing already in flight.
DECLARE @on INT = (SELECT COUNT(*) FROM change_mgmt.change_requests WHERE outage_expected = 1);
DECLARE @msgOn NVARCHAR(2048) = N'V356: outage_expected is already 1 on '
    + CAST(@on AS NVARCHAR(20)) + N' change(s). It must arrive inert: a declared outage needs a '
    + N'duration and a named service, and back-filling that for existing rows would be inventing '
    + N'operational facts.';
IF @on > 0
    THROW 56006, @msgOn, 1;
GO

-- 5. ⚠ Prove ck_change_pso REJECTS. Reading sys.check_constraints proves a NAME; only an
--    attempted write proves the rule fires. Inside a transaction that always rolls back.
DECLARE @rejPso BIT = 0;
BEGIN TRANSACTION;
BEGIN TRY
    UPDATE TOP (1) change_mgmt.change_requests SET outage_expected = 1 WHERE is_deleted = 0;
END TRY
BEGIN CATCH
    SET @rejPso = 1;
END CATCH
ROLLBACK TRANSACTION;
DECLARE @msgPso NVARCHAR(2048) = N'V356: ck_change_pso accepted outage_expected = 1 with no '
    + N'outage_minutes and no services_affected. A declared outage that names neither a length '
    + N'nor a service is exactly what this constraint exists to refuse.';
IF @rejPso = 0
    THROW 56007, @msgPso, 1;
GO

-- 6. ⚠ And prove ck_csp_published REJECTS a short digest — the tamper-evidence claim.
DECLARE @rejSha BIT = 0;
BEGIN TRANSACTION;
BEGIN TRY
    INSERT INTO change_mgmt.change_schedule_publication
        (organisation_id, window_from, window_to, entry_count, outage_count, freeze_count,
         entries_json, content_sha256, published_by_name, published_at)
    VALUES (1, '2026-01-01', '2026-02-01', 0, 0, 0, N'[]', 'deadbeef', 'V356 self-check',
            SYSUTCDATETIME());
END TRY
BEGIN CATCH
    SET @rejSha = 1;
END CATCH
ROLLBACK TRANSACTION;
DECLARE @msgSha NVARCHAR(2048) = N'V356: ck_csp_published accepted a content_sha256 that is not '
    + N'64 characters. A digest that is not a digest makes the tamper-evidence claim false.';
IF @rejSha = 0
    THROW 56008, @msgSha, 1;
GO

-- 7. ⚠ And prove ck_csp_window REJECTS a backwards window.
DECLARE @rejWin BIT = 0;
BEGIN TRANSACTION;
BEGIN TRY
    INSERT INTO change_mgmt.change_schedule_publication
        (organisation_id, window_from, window_to, entry_count, outage_count, freeze_count,
         entries_json, content_sha256, published_by_name, published_at)
    VALUES (1, '2026-02-01', '2026-01-01', 0, 0, 0, N'[]',
            REPLICATE('a', 64), 'V356 self-check', SYSUTCDATETIME());
END TRY
BEGIN CATCH
    SET @rejWin = 1;
END CATCH
ROLLBACK TRANSACTION;
IF @rejWin = 0
    THROW 56009, 'V356: ck_csp_window accepted a window ending before it starts.', 1;
GO

-- 8. The table ships EMPTY. A seeded publication would assert the business was told something.
DECLARE @rows INT = (SELECT COUNT(*) FROM change_mgmt.change_schedule_publication);
DECLARE @msgSeed NVARCHAR(2048) = N'V356: change_schedule_publication must ship empty; found '
    + CAST(@rows AS NVARCHAR(20)) + N' row(s). A pre-seeded publication claims a communication '
    + N'that never happened.';
IF @rows > 0
    THROW 56010, @msgSeed, 1;
GO

-- 9. No self-check row survived its rolled-back transaction.
IF EXISTS (SELECT 1 FROM change_mgmt.change_schedule_publication
           WHERE published_by_name = 'V356 self-check')
    THROW 56011, 'V356: a self-check row survived — its transaction did not roll back.', 1;
GO

-- V355 — S8f / CE-11: the unauthorised-change register's one stored half.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-05
-- ════════════════════════════════════════════════════════════════════════════════════════
-- G-11 of the uplift plan, severity **High**: "Unauthorised changes undetectable — no
-- reconciliation of implemented vs authorised."
--
-- ⚠ CE-11 is specified as "unapproved changes detected from configuration drift". Measured
-- before writing anything, as every slice in this programme now is:
--
--     CMDB / configuration-item tables ............................... 0
--     configuration baseline tables .................................. 0
--     drift tables ................................................... 1, and it is
--       `ai_governance.ml_model_drift_observation` — ML model drift, a different subject
--     tables in the `integrations` schema ............................ 0
--
-- So nothing feeds this platform a record of what actually changed in production, and drift
-- detection would have required inventing its own input. What DOES exist, complete in both
-- databases, is the reconciliation G-11 actually asks for:
--
--     change_authorisation_matrix (what was REQUIRED) ................ 36 cells, 12 per tenant
--     change_approvals            (what was RECORDED) ................ 31 votes
--     change_requests.status      (whether it WENT AHEAD) ............ 17 changes
--
-- Run over the seeded data, that reconciliation finds real failures — among them change 3, an
-- EMERGENCY/CRITICAL change sitting at IMPLEMENTED against an ECAB cell demanding **2**
-- approvals with **1** recorded; and change 229, which reached CAB_APPROVED with no recorded
-- vote at all. Before this slice nothing in the platform said so.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- ⚠ WHY THIS MIGRATION IS SMALL — THE FINDINGS ARE NOT STORED
-- ════════════════════════════════════════════════════════════════════════════════════════
-- The findings are DERIVED on every read from the three tables above, so they cannot drift from
-- the rows they describe. That is the same decision S7e took when it refused to persist a
-- `CONDITIONAL` literal (a stored word can disagree with the rows) and V352 records the reasoning.
--
-- What cannot be derived is a human's judgement ABOUT a finding, so that — and only that — needs a
-- table. One row per (change, reason): a change can fail reconciliation for two independent reasons
-- and each is disposed on its own merits.
--
-- ⚠ AND A DISPOSITION NEVER ERASES A FINDING. There is no CLOSED state and no way to suppress a
-- derived row, because "somebody signed this off" and "this never happened" are different facts.
-- Concretely: the `unauthorisedChangeCount` KPI counts every authority gap, disposed or not. Were
-- it to count only open ones, a clean dashboard would be reachable by dispositioning rather than by
-- fixing anything — F2-107's failure mode (a percentage over a convenient denominator) wearing a
-- governance hat.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE CONSTRAINT THAT IS THE POINT OF THIS MIGRATION
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `ck_ucd_disposed`: a row may not name a disposition state without naming **who** decided and
-- **when**. Same shape as V354's `ck_cmodel_preapproval` and V345's `ck_change_ratification_clock`
-- — make the incomplete state unrepresentable rather than validating it in a service that a later
-- caller bypasses.
--
-- ⚠ As with V354, the CHECK is only SUFFICIENT because the request DTO is narrow. `disposed_by_id`,
-- `disposed_by_name` and `disposed_at` are set from the authenticated principal and the server
-- clock and are never accepted from the caller. The constraint tests that the fields AGREE; it
-- cannot test who supplied them. The schema rule and the DTO are one control, not two.
--
-- Applied twice to dev by hand (Flyway is OFF in dev). Every self-check THROWs and every one is
-- true on the second pass.
-- ════════════════════════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
               WHERE s.name = 'change_mgmt' AND t.name = 'change_unauthorised_disposition')
BEGIN
    CREATE TABLE change_mgmt.change_unauthorised_disposition (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id     BIGINT        NOT NULL,
        change_request_id   BIGINT        NOT NULL,
        -- Which reconciliation failure this dispositions. Mirrors
        -- UnauthorisedChangeFinding.REASON_CODES; a test asserts the two vocabularies agree, the
        -- same way ChangeModelCatalogueTest pins V354's.
        reason_code         NVARCHAR(40)  NOT NULL,
        state               NVARCHAR(40)  NOT NULL,
        -- Single-language, following `ratification_notes` and `cab_decision_notes`: house rule 7
        -- governs user-visible RECORDS, and the translatable part of this row is its vocabulary
        -- (state + reason_code are stable codes the frontend renders in both languages). Demanding
        -- an Arabic translation of an investigator's free text would produce empty fields, not
        -- bilingual ones.
        note                NVARCHAR(2000) NULL,
        disposed_by_id      BIGINT        NULL,
        disposed_by_name    NVARCHAR(255) NOT NULL,
        disposed_at         DATETIME2(6)  NOT NULL,
        is_deleted          BIT           NOT NULL CONSTRAINT df_ucd_del DEFAULT 0,
        created_at          DATETIME2(6)  NOT NULL CONSTRAINT df_ucd_created DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2(6)  NULL,

        CONSTRAINT pk_change_unauthorised_disposition PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_ucd_change FOREIGN KEY (change_request_id)
            REFERENCES change_mgmt.change_requests (id),

        -- The closed vocabulary. FALSE_POSITIVE is deliberate and is not an escape hatch: a
        -- detector an investigator can tell it was wrong is a detector whose false-positive rate
        -- can be measured. One that cannot be contradicted gets ignored wholesale instead.
        CONSTRAINT ck_ucd_state CHECK (state IN
            ('ACCEPTED_RISK', 'RETROSPECTIVELY_AUTHORISED', 'REVERSED', 'FALSE_POSITIVE')),

        -- ⚠ THE CONSTRAINT THIS MIGRATION EXISTS FOR.
        CONSTRAINT ck_ucd_disposed CHECK (
            LEN(LTRIM(disposed_by_name)) > 0 AND disposed_at IS NOT NULL)
    );
END
GO

-- One live disposition per (change, reason) — re-dispositioning UPDATEs rather than appending, so
-- "what is the current judgement on this finding?" has exactly one answer.
IF OBJECT_ID('change_mgmt.change_unauthorised_disposition') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_ucd_change_reason' AND object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition'))
    CREATE UNIQUE INDEX ux_ucd_change_reason
        ON change_mgmt.change_unauthorised_disposition (organisation_id, change_request_id, reason_code)
        WHERE is_deleted = 0;
GO

-- The register reads every disposition for a tenant in one query and indexes them by finding key.
IF OBJECT_ID('change_mgmt.change_unauthorised_disposition') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ucd_org' AND object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition'))
    CREATE INDEX ix_ucd_org
        ON change_mgmt.change_unauthorised_disposition (organisation_id) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. Every column the entity maps is present.
DECLARE @missing NVARCHAR(MAX) = '';
SELECT @missing = @missing + c.n + ' '
FROM (VALUES ('organisation_id'), ('change_request_id'), ('reason_code'), ('state'), ('note'),
             ('disposed_by_id'), ('disposed_by_name'), ('disposed_at'), ('is_deleted'),
             ('created_at'), ('updated_at')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'change_mgmt'
                    AND TABLE_NAME = 'change_unauthorised_disposition'
                    AND COLUMN_NAME = c.n);
IF LEN(@missing) > 0
    THROW 55001, 'V355: change_unauthorised_disposition is missing columns the entity maps.', 1;
GO

-- 2. Both constraints exist.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ucd_state'
               AND parent_object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition'))
    THROW 55002, 'V355: ck_ucd_state is missing — any word could be a disposition.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ucd_disposed'
               AND parent_object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition'))
    THROW 55003, 'V355: ck_ucd_disposed is missing — a finding could be signed off by nobody.', 1;
GO

-- 3. Both are TRUSTED and ENABLED. A disabled CHECK is a comment (V354 check 3's reasoning).
DECLARE @msgTrust NVARCHAR(2048) = N'V355: ck_ucd_state or ck_ucd_disposed exists but is disabled '
    + N'or not trusted. An unenforced CHECK on a governance sign-off is worse than none, because '
    + N'the schema then advertises a guarantee it is not keeping.';
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE parent_object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition')
             AND name IN ('ck_ucd_state', 'ck_ucd_disposed')
             AND (is_disabled = 1 OR is_not_trusted = 1))
    THROW 55004, @msgTrust, 1;
GO

-- 4. ⚠ Prove ck_ucd_disposed REJECTS. Reading sys.check_constraints proves a NAME exists; only an
--    attempted insert proves the rule fires. Inside a transaction that always rolls back.
-- ⚠ F2-77 (2026-08-06): THIS PROBE PASSED IN DEV AND FAILED ON A FRESH INSTALL, AND THE
-- CONSTRAINT WAS NEVER AT FAULT.
--
-- It used `SELECT TOP 1 ... FROM change_mgmt.change_requests WHERE is_deleted = 0`. Dev has 17
-- such rows; a migration-built database has ZERO. With no source row the INSERT inserts nothing,
-- violates nothing, raises nothing, CATCH never runs, @rejectedBlank stays 0 — and the THROW then
-- accuses ck_ucd_disposed of accepting whitespace. It does not: LTRIM('   ') is '' and LEN('') is
-- 0, so the CHECK is correct and was simply never exercised.
--
-- F2-112's shape exactly: a control that cannot fire over a statement that cannot run. The probe
-- now builds its own parent, so it proves the same thing on an empty database.
--
-- ⚠ The parent INSERT sits deliberately OUTSIDE the TRY. Inside it, a failure to create the
-- parent would be caught, set @rejectedBlank = 1 and report a PASS — a probe that succeeds because
-- its own setup broke is worse than one that cannot run.
DECLARE @rejectedBlank BIT = 0;
DECLARE @probeOrg BIGINT = COALESCE((SELECT MIN(organisation_id) FROM admin.committees), 1);
DECLARE @probeChange BIGINT;
BEGIN TRANSACTION;
SELECT TOP 1 @probeChange = c.id FROM change_mgmt.change_requests c
 WHERE c.is_deleted = 0 ORDER BY c.id;
IF @probeChange IS NULL
BEGIN
    INSERT INTO change_mgmt.change_requests (organisation_id, title_en)
    VALUES (@probeOrg, N'V355 self-check probe — rolled back, never committed');
    SET @probeChange = SCOPE_IDENTITY();
END
SET @probeOrg = (SELECT organisation_id FROM change_mgmt.change_requests WHERE id = @probeChange);
BEGIN TRY
    INSERT INTO change_mgmt.change_unauthorised_disposition
        (organisation_id, change_request_id, reason_code, state, disposed_by_name, disposed_at)
    VALUES (@probeOrg, @probeChange, 'INSUFFICIENT_APPROVALS', 'ACCEPTED_RISK', '   ',
            SYSUTCDATETIME());
END TRY
BEGIN CATCH
    SET @rejectedBlank = 1;
END CATCH
ROLLBACK TRANSACTION;
DECLARE @msgBlank NVARCHAR(2048) = N'V355: ck_ucd_disposed accepted a disposition whose '
    + N'disposed_by_name is whitespace. A sign-off with nobody behind it is exactly what this '
    + N'constraint exists to refuse.';
IF @rejectedBlank = 0
    THROW 55005, @msgBlank, 1;
GO

-- 5. ⚠ And prove ck_ucd_state REJECTS an invented state, for the same reason.
-- ⚠ This probe declares and builds its OWN parent rather than reusing the one above, because a GO
-- separates the two batches and T-SQL variables do not survive a batch boundary. My first attempt
-- referenced @probeOrg from the previous batch and failed with "Must declare the scalar variable"
-- — the identical mistake made in V159 the same afternoon, where the comment warning about it is
-- now also written. Knowing the rule and applying it are apparently two different things.
DECLARE @rejectedState BIT = 0;
DECLARE @probeOrg2 BIGINT = COALESCE((SELECT MIN(organisation_id) FROM admin.committees), 1);
DECLARE @probeChange2 BIGINT;
BEGIN TRANSACTION;
SELECT TOP 1 @probeChange2 = c.id FROM change_mgmt.change_requests c
 WHERE c.is_deleted = 0 ORDER BY c.id;
IF @probeChange2 IS NULL
BEGIN
    INSERT INTO change_mgmt.change_requests (organisation_id, title_en)
    VALUES (@probeOrg2, N'V355 state self-check probe — rolled back, never committed');
    SET @probeChange2 = SCOPE_IDENTITY();
END
SET @probeOrg2 = (SELECT organisation_id FROM change_mgmt.change_requests WHERE id = @probeChange2);
BEGIN TRY
    INSERT INTO change_mgmt.change_unauthorised_disposition
        (organisation_id, change_request_id, reason_code, state, disposed_by_name, disposed_at)
    VALUES (@probeOrg2, @probeChange2, 'INSUFFICIENT_APPROVALS', 'WAIVED_BY_ME', 'V355 self-check',
            SYSUTCDATETIME());
END TRY
BEGIN CATCH
    SET @rejectedState = 1;
END CATCH
ROLLBACK TRANSACTION;
DECLARE @msgState NVARCHAR(2048) = N'V355: ck_ucd_state accepted a disposition state outside the '
    + N'published set. An open vocabulary here means the frontend cannot translate what it renders '
    + N'and the register cannot be grouped by outcome.';
IF @rejectedState = 0
    THROW 55006, @msgState, 1;
GO

-- 6. The uniqueness guarantee, without which one finding can carry two contradictory judgements.
IF OBJECT_ID('change_mgmt.change_unauthorised_disposition') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_ucd_change_reason' AND object_id = OBJECT_ID('change_mgmt.change_unauthorised_disposition'))
    THROW 55007, 'V355: ux_ucd_change_reason is missing — one finding could hold two dispositions.', 1;
GO

-- 7. The table ships EMPTY, and this is a measurement rather than a promise: nobody has
--    investigated a finding yet because until this migration there were no findings to investigate.
--    A seeded disposition would assert that somebody had.
DECLARE @rows INT = (SELECT COUNT(*) FROM change_mgmt.change_unauthorised_disposition);
DECLARE @msgSeed NVARCHAR(2048) = N'V355: change_unauthorised_disposition must ship empty; found '
    + CAST(@rows AS NVARCHAR(20)) + N' row(s). A pre-seeded sign-off claims an investigation that '
    + N'did not happen.';
IF @rows > 0
    THROW 55008, @msgSeed, 1;
GO

-- 8. Neither self-check row survived its rolled-back transaction.
IF EXISTS (SELECT 1 FROM change_mgmt.change_unauthorised_disposition
           WHERE disposed_by_name IN ('   ', 'V355 self-check'))
    THROW 55009, 'V355: a self-check row survived — its transaction did not roll back.', 1;
GO

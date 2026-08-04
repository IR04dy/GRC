-- V352 — S7e / CE-5 (second half): the conditions register. Approve-with-conditions becomes a
-- recorded, enforced obligation instead of a sentence in a notes field.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-04 IN BOTH DATABASES
-- ════════════════════════════════════════════════════════════════════════════════════════
-- G-5 of the uplift plan: "Decision vocabulary too narrow — no DEFER / MORE_INFO /
-- approve-with-conditions; conditions not recorded or enforced before implementation". S7c closed
-- the DEFER / MORE_INFO half. This closes the rest, and the measurement is total absence:
--
--     tables named %condition% platform-wide ......... 0   (dev and prod)
--     any conditions concept in change_mgmt/** ....... 0   (every grep hit is incidental prose)
--     any conditions concept in admin/committee/** ... 0
--
-- So a CAB that approved a change "subject to the DR rehearsal being re-run" had exactly one place
-- to put that: `cab_decision_notes`, free text nothing reads. The change then proceeded to
-- IMPLEMENTING with the obligation unrecorded, unowned and unchecked — the same shape as CH-11's
-- `pir_follow_up` before S7d, and the same shape as every finding in the F2-62 family: a governance
-- act recorded as prose is a governance act nobody can enforce.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- ⚠ WHY A NEW TABLE, AND NOT `change_tasks` — THE SUBSUMPTION QUESTION, ANSWERED BY DATA
-- ════════════════════════════════════════════════════════════════════════════════════════
-- S6 taught this project to ask whether the platform already stores the fact before adding a home
-- for it, and `change_tasks` looks like a candidate: bilingual title, owner, due date, status.
-- It is not, and the data settles it rather than the taxonomy:
--
--   * `change_tasks` holds the IMPLEMENTATION RUNBOOK — 41 rows in each database, sort-ordered,
--     reading "Snapshot EU-WEST-1 master", "Cut DNS to KSA region", "Promote KSA replica to
--     master". Steps performed *during* implementation.
--   * A condition is the opposite: an obligation imposed BY THE COMMITTEE as the price of
--     approval, which must be discharged BEFORE implementation starts.
--   * ⚠ And the guard proves they cannot share a table: `SCHEDULED -> IMPLEMENTING` must be refused
--     while an obligation is open. **24 of 41 tasks in dev and 23 of 41 in prod are PENDING** —
--     which is correct, because a runbook step is *supposed* to be pending when implementation
--     begins. A guard over `change_tasks` would refuse every change the platform holds, forever.
--   * Conditions also need two things a task has no room for: a WAIVER (an approval act, with an
--     author and a reason, that removes an obligation) and a satisfaction author distinct from the
--     owner who did the work — the seam S8's `ConditionsSatisfiedGuard` needs for verified evidence.
--
-- ⚠ WHY THE PARENT IS THE CHANGE, NOT A DECISION ROW. The uplift plan §7.2 names the table
-- `change_decision_condition` and hangs it off "the effective decision". **There is no
-- `ChangeDecision` entity and no `change_decision` table** — S7c's write-back was deliberately
-- shipped as columns on `change_requests` (`cab_decision_at` / `_by_id` / `_by_name` / `_notes`).
-- Creating a decision row now, solely to own conditions, would be a second home for facts the
-- change already carries. Same trap CE-7 walked into naming `ck_ca_source`: the spec's parent did
-- not exist.
--
-- ⚠ AND NO NEW STATUS OR OUTCOME VALUE. "Approved with conditions" is not a 17th `ck_change_status`
-- and not a sixth `CK_resolution_outcome`: an approve-with-conditions change IS approved
-- (CAB_APPROVED), and "is it conditional?" is *derived* from whether it carries conditions. A word
-- stored beside the conditions can disagree with them — a CONDITIONAL row whose conditions were
-- never entered reads conditional with nothing to satisfy — whereas the derivation cannot. Pinned
-- by `ChangeConditionRegisterTest`; to add the literal, amend that test in the same commit.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- NOTHING CHANGES ON DEPLOY. A new, empty table. The guard it enables can refuse no existing
-- change, because zero conditions exist anywhere: the enforcement starts from the first condition
-- a CAB actually imposes.
--
-- REVERSE RECIPE: DROP TABLE change_mgmt.change_condition;
-- ════════════════════════════════════════════════════════════════════════════════════════

SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_condition')
BEGIN
    CREATE TABLE change_mgmt.change_condition (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        -- organisation_id is carried explicitly, unlike `change_tasks` which scopes through its
        -- parent. Every repository query must filter it (CLAUDE.md, developer responsibility), and
        -- the register is read tenant-wide ("every open condition in this organisation"), which a
        -- parent-only scope turns into a join on every call.
        organisation_id     BIGINT       NOT NULL,
        change_request_id   BIGINT       NOT NULL,
        sort_order          INT          NOT NULL CONSTRAINT df_cc_sort DEFAULT (0),

        -- House rule 7: user-visible records carry both languages, and both are mandatory.
        text_en             NVARCHAR(1000) NOT NULL,
        text_ar             NVARCHAR(1000) NOT NULL,

        -- Who imposed it. A condition is part of an approval act, so its author is recorded the
        -- same way F2-93/F2-103 record a CAB decision's author — and for the same reason: an
        -- obligation with no named imposer cannot be appealed, varied or audited.
        imposed_by_id       BIGINT       NULL,
        imposed_by_name     NVARCHAR(255) NULL,
        imposed_at          DATETIME2(3) NOT NULL CONSTRAINT df_cc_imposed DEFAULT SYSUTCDATETIME(),

        -- Who must discharge it, and by when. Both nullable: a CAB can impose a condition without
        -- naming an owner, and refusing to record the condition until it has one would lose the
        -- governance fact to keep the metadata tidy (S7c's rule).
        owner_name          NVARCHAR(255) NULL,
        due_at              DATETIME2(3) NULL,

        status              NVARCHAR(20) NOT NULL CONSTRAINT df_cc_status DEFAULT ('OPEN'),

        -- One author trio for the closing act, because SATISFIED and WAIVED are mutually
        -- exclusive; `status` says which happened and `closure_notes` carries the evidence
        -- reference or the waiver reason. Two trios would let a row claim both.
        closed_by_id        BIGINT       NULL,
        closed_by_name      NVARCHAR(255) NULL,
        closed_at           DATETIME2(3) NULL,
        closure_notes       NVARCHAR(MAX) NULL,

        is_deleted          BIT          NOT NULL CONSTRAINT df_cc_deleted DEFAULT (0),
        created_at          DATETIME2(3) NOT NULL CONSTRAINT df_cc_created DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(255) NULL,
        updated_at          DATETIME2(3) NULL,
        updated_by          NVARCHAR(255) NULL,

        CONSTRAINT pk_change_condition PRIMARY KEY (id),
        CONSTRAINT fk_cc_change FOREIGN KEY (change_request_id)
            REFERENCES change_mgmt.change_requests (id),
        CONSTRAINT ck_cc_status CHECK (status IN (N'OPEN', N'SATISFIED', N'WAIVED')),
        -- A closed condition names who closed it and when. This is the constraint that stops the
        -- register degrading into the notes field it replaces: "satisfied" with no author is the
        -- F2-103 defect written into the schema.
        CONSTRAINT ck_cc_closure CHECK (
            (status = N'OPEN'  AND closed_at IS NULL AND closed_by_name IS NULL)
         OR (status <> N'OPEN' AND closed_at IS NOT NULL AND closed_by_name IS NOT NULL)),
        -- A waiver must say why. Satisfaction may stand on its evidence link alone; removing an
        -- obligation cannot stand on nothing.
        CONSTRAINT ck_cc_waiver_reason CHECK (
            status <> N'WAIVED' OR (closure_notes IS NOT NULL AND LEN(LTRIM(closure_notes)) > 0))
    );

    CREATE INDEX ix_cc_change  ON change_mgmt.change_condition (change_request_id, sort_order)
        WHERE is_deleted = 0;
    -- The register read: every open condition in a tenant, which is the KPI and the blocker list.
    CREATE INDEX ix_cc_open    ON change_mgmt.change_condition (organisation_id, status, due_at)
        WHERE is_deleted = 0;
END
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS — every one THROWs. Applied twice to dev via pyodbc (Flyway is off in dev), so
-- re-running this file must be a no-op.
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. The table exists with the shape the entity maps.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_condition')
    THROW 52001, 'V352: change_mgmt.change_condition was not created.', 1;
GO

DECLARE @missing NVARCHAR(1000) = N'';
SELECT @missing = @missing + c.n + N' '
FROM (VALUES ('organisation_id'), ('change_request_id'), ('sort_order'), ('text_en'), ('text_ar'),
             ('imposed_by_id'), ('imposed_by_name'), ('imposed_at'), ('owner_name'), ('due_at'),
             ('status'), ('closed_by_id'), ('closed_by_name'), ('closed_at'), ('closure_notes'),
             ('is_deleted')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_condition'
                    AND COLUMN_NAME = c.n);
IF LEN(@missing) > 0
    THROW 52002, 'V352: change_condition is missing columns the entity maps.', 1;
GO

-- 2. Both language columns are mandatory. House rule 7 is enforced in the schema and not only in
--    bean validation, because F2-30 measured what happens when it lives in prose alone.
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_condition'
             AND COLUMN_NAME IN ('text_en', 'text_ar') AND IS_NULLABLE = 'YES')
    THROW 52003, 'V352: text_en/text_ar must both be NOT NULL (house rule 7).', 1;
GO

-- 3. The closure constraint exists. Without it the register can hold "satisfied by nobody", which
--    is exactly the state F2-103 found on change_requests and exactly what this table is for.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_cc_closure'
                 AND parent_object_id = OBJECT_ID('change_mgmt.change_condition'))
    THROW 52004, 'V352: ck_cc_closure is missing — a closed condition could name no author.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_cc_waiver_reason'
                 AND parent_object_id = OBJECT_ID('change_mgmt.change_condition'))
    THROW 52005, 'V352: ck_cc_waiver_reason is missing — an obligation could be removed silently.', 1;
GO

-- 4. The status vocabulary is exactly three values, and it must stay equal to
--    `ChangeConditionStatus` in Java (`ChangeConditionRegisterTest` reads both).
DECLARE @def NVARCHAR(MAX) = (SELECT definition FROM sys.check_constraints
                              WHERE name = 'ck_cc_status'
                                AND parent_object_id = OBJECT_ID('change_mgmt.change_condition'));
IF @def IS NULL
    OR @def NOT LIKE '%OPEN%' OR @def NOT LIKE '%SATISFIED%' OR @def NOT LIKE '%WAIVED%'
    THROW 52006, 'V352: ck_cc_status must permit exactly OPEN / SATISFIED / WAIVED.', 1;
GO

-- 5. ⚠ This migration must not invent a "CONDITIONAL" status. The decision is recorded in the
--    file header: an approve-with-conditions change is CAB_APPROVED, and conditionality is derived
--    from the register. A status word stored beside the conditions can disagree with them.
-- ⚠ THROW takes a literal or a variable, never a concatenation — the first draft of this file
--   built both messages with `+` and SQL Server answered "Incorrect syntax near '+'". Declared
--   variables keep the long, useful message without splitting it into a shorter, less useful one.
DECLARE @msg7 NVARCHAR(2048) = N'V352: ck_change_status names CONDITIONAL. Conditionality is '
    + N'DERIVED from the register, never stored beside it — a literal can disagree with the rows. '
    + N'See this file''s header and ChangeConditionRegisterTest; amend both to change the decision.';
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_change_status'
             AND parent_object_id = OBJECT_ID('change_mgmt.change_requests')
             AND definition LIKE '%CONDITIONAL%')
    THROW 52007, @msg7, 1;
GO

-- 6. Nothing was seeded. A register whose first rows are demo data teaches the reader that
--    conditions are decoration; every row here must be a governance act somebody performed.
DECLARE @msg8 NVARCHAR(2048) = N'V352: change_condition must ship empty — the enforcement starts '
    + N'from the first condition a CAB actually imposes.';
IF EXISTS (SELECT 1 FROM change_mgmt.change_condition)
    THROW 52008, @msg8, 1;
GO

PRINT 'V352 OK — change_mgmt.change_condition created empty; CONDITIONAL deliberately absent.';
GO

-- V376 — F2-221: the two status vocabularies F2-220 left declared only in Java.
--
-- THE DEFECT. F2-220 closed nine registers whose create named a state the transition route
-- adjudicates. Two of the nine had no CHECK constraint on the column at all:
--
--     platform.issues.status      — the table has ZERO check constraints of any kind
--     bcm.crisis_events.status    — the table has one, CK_crisis_events_severity, and none on status
--
-- With no CHECK the vocabulary lives only in the heads of its readers (F2-196), and the consequence
-- is specific rather than theoretical: `IssueService.transition` and `CrisisEventService.transition`
-- both dispatch on the CURRENT value with `default -> false`, so a row holding a token the machine
-- does not know is **permanently untransitionable by any route** — a 409 whose allowed-set is empty.
-- That is F2-203's `audit_lc` shape (19 of 46 rows stuck), and until F2-220 it was creatable on
-- demand through the API rather than merely inherited from a seed.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation. This file
-- constrains exactly two columns because those two are the ones whose writers are provably closed,
-- and "provably" here means measured, not assumed:
--
--     platform.issues.status
--       · IssueRepository has exactly ONE user, IssueService
--       · IssueService contains exactly TWO setStatus sites — create (ServerOwnedState.requireInitial,
--         F2-220) and transition (the STATUSES machine)
--       · update() throws "Use transition() to change status"
--     bcm.crisis_events.status
--       · CrisisEventService contains exactly TWO setStatus sites, the same two shapes
--       · seven other classes hold CrisisEventRepository and none of them writes status
--       · no native @Modifying UPDATE touches either table
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-16), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev   TSS_GRC             issues 13 rows / 4 distinct · crisis_events 7 rows / 5 distinct
--     prod  TSS_GRC_Deployment  issues 13 rows / 4 distinct · crisis_events 7 rows / 5 distinct
--     violations: 0 and 0.  Every stored value is a member of its service's own vocabulary.
--
-- No WITH NOCHECK anywhere — SQL Server verifying the existing rows is the point, and the self-check
-- below fails the migration if either constraint lands untrusted.
--
-- ⚠ WHY THERE IS NO CASING UPDATE HERE, UNLIKE V375. The collation is case-INSENSITIVE, so a CHECK
-- alone would accept 'open' and store it verbatim (F2-179) — which is why V375 had to normalise
-- first. It does not apply to these two columns, and that was checked rather than assumed: both
-- writers compare case-EXACTLY (`Set.contains(toStatus)` and `ServerOwnedState.requireInitial`'s
-- equals), so a case-variant is REFUSED at the boundary rather than stored, and the pre-flight below
-- proves no historical row slipped through. If a future writer coerces instead of refusing, the
-- pre-flight is what will say so.
--
-- REVERSE RECIPE:
--   ALTER TABLE platform.issues        DROP CONSTRAINT CK_issues_status;
--   ALTER TABLE bcm.crisis_events      DROP CONSTRAINT CK_crisis_events_status;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending values BEFORE ALTER TABLE does ──────────────────────────
-- ADD CONSTRAINT would fail on its own, but with error 547 and no mention of which value in which
-- column. F2-180 measured what that costs: 547 covers both FOREIGN KEY and CHECK, and a diagnostic
-- that does not name the column sends the reader somewhere else entirely.

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src + N'=' + val), N', ')
  FROM (
    SELECT DISTINCT N'issues.status' src, status val FROM platform.issues
     WHERE status NOT IN (N'OPEN', N'IN_PROGRESS', N'BLOCKED',
                          N'RESOLVED', N'CLOSED', N'CANCELLED')
    UNION ALL
    SELECT DISTINCT N'crisis_events.status', status FROM bcm.crisis_events
     WHERE status NOT IN (N'DETECTED', N'ASSESSING', N'ACTIVATED', N'CONTAINED',
                          N'RECOVERED', N'CLOSED', N'CANCELLED')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V376 stopped before adding any constraint: a status column holds values outside its '
      + N'service''s own vocabulary — ' + @bad + N'. Each such row is ALREADY untransitionable, '
      + N'because transition() dispatches on the current value with default -> false, so this is a '
      + N'pre-existing defect this migration has surfaced rather than caused. Reconcile it with the '
      + N'owner first: collapsing a synonym rewrites live rows in a real database and is not a '
      + N'migration''s decision to make (F2-183, F2-196, F2-203).';
    THROW 51376, @badMsg, 1;
END
GO

-- Casing: the vocabularies above are matched case-INSENSITIVELY by the WHERE clauses, so a row
-- holding 'open' would have passed the pre-flight AND will pass the CHECK. BIN2 is what makes the
-- comparison see case at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;

SELECT @miscased =
    (SELECT COUNT(*) FROM platform.issues
      WHERE status COLLATE Latin1_General_100_BIN2 <> UPPER(status) COLLATE Latin1_General_100_BIN2)
  + (SELECT COUNT(*) FROM bcm.crisis_events
      WHERE status COLLATE Latin1_General_100_BIN2 <> UPPER(status) COLLATE Latin1_General_100_BIN2);
IF @miscased > 0
    THROW 51376, N'V376: a status is stored in non-canonical casing. Both writers compare case-exactly, so this cannot have come from the API — investigate the source before constraining, because the CHECK will accept it and the state machine will not.', 1;
GO

-- ── 2. The constraints ────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway will apply it once on
-- the deployed server.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_issues_status')
    ALTER TABLE platform.issues ADD CONSTRAINT CK_issues_status
        CHECK (status IN (N'OPEN', N'IN_PROGRESS', N'BLOCKED',
                          N'RESOLVED', N'CLOSED', N'CANCELLED'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_crisis_events_status')
    ALTER TABLE bcm.crisis_events ADD CONSTRAINT CK_crisis_events_status
        CHECK (status IN (N'DETECTED', N'ASSESSING', N'ACTIVATED', N'CONTAINED',
                          N'RECOVERED', N'CLOSED', N'CANCELLED'));
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, disabled against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_issues_status', 'CK_crisis_events_status')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 2
    THROW 51376, N'V376 self-check: expected 2 enabled, trusted CHECK constraints (CK_issues_status, CK_crisis_events_status).', 1;
GO

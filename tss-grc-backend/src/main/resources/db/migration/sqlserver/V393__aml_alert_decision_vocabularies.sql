-- V393 — F2-289: the ethics AML *transaction-monitoring* register's first CHECK constraints, and
-- the three vocabularies its decision path writes.
--
-- THE DEFECT. `AmlAlertService#transition` reacted to a decision by recording who made it and when
-- — and that reaction reached TWO of the switch's THREE decision branches. `CLEARED` and
-- `STR_FILED` each stamped `decided_by_name` and `decided_at`; `ESCALATED`, which records a
-- `TRUE_POSITIVE` disposition (the analyst's conclusion that a monitored transaction is genuinely
-- suspicious, and the referral an MLRO acts on under SAMA AML/CFT and FATF R.20), stamped neither.
-- `ESCALATED -> CLOSED` is a declared edge and the `CLOSED` branch keeps decision metadata as-is,
-- so an alert could be escalated as a confirmed true positive and closed with NO DECIDER RECORDED
-- ANYWHERE, permanently.
--
-- 🔑🔑 WHY NOBODY SAW IT: the comment explaining the mechanism was byte-identical in the two
-- branches that HAD it, and the third sat between them. Repetition reads as a rule, so a reader
-- scrolling the switch met the attribution twice and the gap once — and the duplicated comment's own
-- worked example was "the STR filed with the Saudi FIU", i.e. one of the two branches it appeared
-- in. F2-215's two-of-N, sharpened: a comment duplicated across the branches that have a mechanism
-- is stronger evidence of coverage than one stated once.
--
-- 🔑 AND F2-223 IS THE SLICE THAT ADDED THE ATTRIBUTION — to two of the three branches. F2-287's
-- headline again: the slice that establishes a rule is the worst-placed slice to apply it to its own
-- leftovers, because the branch it did not touch is the one its new comment makes look finished.
--
-- 🔑 THE VOCABULARY LIVED IN FOUR PLACES IN ONE FILE AND WAS ENFORCED IN NONE.
-- `ethics.aml_alerts` had ZERO CHECK constraints of any kind. `disposition` was written by
-- `body.get("disposition").toString()` at three sites with no membership test at all; `severity` was
-- copied straight out of an entity-bound PUT; and `analytics()` bucketed both columns by INCLUSION
-- LISTS typed out a second time (six status tokens, four severity tokens), which is how F2-196's
-- defect starts — a token nobody remembered is silently absent from the KPI rather than chased.
-- Both loops are now DERIVED from the machine and the vocabulary.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation, and on an
-- AML register that is a lost alert. All three columns constrained here have writers the SAME COMMIT
-- closes, and "closed" means read rather than assumed:
--
--     aml_alerts.disposition · ONE write site after this commit — recordDisposition(), which is
--                              require() on a caller value and a literal member otherwise
--     aml_alerts.severity    · one write site, applyEditable(), now require(); plus the entity's
--                              own `= "MEDIUM"` field initialiser, which is a member
--     aml_alerts.status      · four write sites, all already closed before this slice:
--                              create() ServerOwnedState.requireInitial(.., INITIAL_STATUS),
--                              update() requireEcho (refuses a change, writes nothing),
--                              assign()  the literal "INVESTIGATING",
--                              transition() the machine-guarded target
--
-- ⚠ `alert_type` IS DELIBERATELY LEFT OPEN. Ten distinct values are stored and no vocabulary for it
-- is declared anywhere in either codebase — no reader's inclusion list, no dropdown, no CHECK — so
-- constraining it would mean inventing one. Recorded here as a decision rather than left as an
-- absence, which is F2-183's rule about consumer.complaint.subcategory.
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-23), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev TSS_GRC / prod TSS_GRC_Deployment — byte-identical, 10 live rows each
--     status       CLEARED 3 · INVESTIGATING 3 · OPEN 3 · STR_FILED 1   (0 ESCALATED, 0 CLOSED)
--     severity     CRITICAL 3 · HIGH 3 · MEDIUM 3 · LOW 1
--     disposition  TRUE_POSITIVE 2, NULL on the other 8
--     violations: 0 in all three columns, in both databases. No WITH NOCHECK anywhere.
--
--     And the defect itself is LATENT, which this file states rather than implies: 0 rows are
--     ESCALATED, and both rows carrying a disposition also carry a decider. It is reachable from the
--     platform's own screen today — the Escalate control is offered on every INVESTIGATING alert and
--     three real alerts are in that state in both databases — so this is a latent-hazard closure and
--     not a defect discovery (F2-283's rule about saying which one you have).
--
-- ⚠ `disposition` IS NULLABLE AND STAYS SO. `disposition IN (...)` evaluates to UNKNOWN for a NULL
-- and a CHECK passes on UNKNOWN, so the constraint narrows the values without making the column
-- mandatory — which is right: 8 of 10 live rows have no disposition because they have not been
-- decided yet, and an alert awaiting an analyst is not a defect.
--
-- ⚠ WHY NO CASING UPDATE. The pre-flight below proves no historical row is stored in non-canonical
-- casing, so there is nothing to normalise; the future is covered by the writers, which now
-- upper-case before the value reaches the entity. A CHECK alone would NOT have been enough — the
-- collation is Latin1_General_100_CI_AS_SC_UTF8, so it accepts 'true_positive' — which is exactly
-- why the writer change and this file are one commit and not two (F2-179/F2-183, and F2-288 proved
-- it live one slice ago by applying its own migration first).
--
-- REVERSE RECIPE:
--   ALTER TABLE ethics.aml_alerts DROP CONSTRAINT CK_aml_alerts_disposition;
--   ALTER TABLE ethics.aml_alerts DROP CONSTRAINT CK_aml_alerts_severity;
--   ALTER TABLE ethics.aml_alerts DROP CONSTRAINT CK_aml_alerts_status;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending values BEFORE ALTER TABLE does ──────────────────────────
-- ADD CONSTRAINT would fail on its own, but with error 547, which covers both FOREIGN KEY and CHECK
-- and does not say which value in which column (F2-180).

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src + N'=' + val), N', ')
  FROM (
    SELECT DISTINCT N'aml_alerts.disposition' src, disposition val
      FROM ethics.aml_alerts
     WHERE disposition IS NOT NULL
       AND disposition NOT IN (N'TRUE_POSITIVE', N'FALSE_POSITIVE')
    UNION ALL
    SELECT DISTINCT N'aml_alerts.severity', severity
      FROM ethics.aml_alerts
     WHERE severity NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT DISTINCT N'aml_alerts.status', status
      FROM ethics.aml_alerts
     WHERE status NOT IN (N'OPEN', N'INVESTIGATING', N'CLEARED', N'ESCALATED',
                          N'STR_FILED', N'CLOSED')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V393 stopped before adding any constraint: ethics.aml_alerts holds values outside the '
      + N'vocabularies its own service writes — ' + @bad + N'. Do NOT widen the CHECK to admit them '
      + N'and do NOT rewrite them here: on an AML register a status or disposition word is a '
      + N'regulatory decision, so collapsing a synonym rewrites live rows in a real database and '
      + N'belongs to the owner (F2-183, F2-196, F2-203). Note especially that a status outside the '
      + N'machine cannot be advanced by ANY route, which is the ethics.cases defect F2-221 filed on '
      + N'the sibling table in this same schema.';
    THROW 51393, @badMsg, 1;
END
GO

-- Casing: the vocabularies above are matched case-INSENSITIVELY by the WHERE clauses, so a row
-- holding 'true_positive' would pass the pre-flight AND pass the CHECK. BIN2 is what makes the
-- comparison see case at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;
SELECT @miscased = COUNT(*)
  FROM ethics.aml_alerts
 WHERE (disposition IS NOT NULL
        AND disposition COLLATE Latin1_General_100_BIN2
            <> UPPER(disposition) COLLATE Latin1_General_100_BIN2)
    OR severity COLLATE Latin1_General_100_BIN2 <> UPPER(severity) COLLATE Latin1_General_100_BIN2
    OR status   COLLATE Latin1_General_100_BIN2 <> UPPER(status)   COLLATE Latin1_General_100_BIN2;
IF @miscased > 0
    THROW 51393, N'V393: ethics.aml_alerts holds a disposition, severity or status in non-canonical casing. That is the F2-179 defect having already happened — the CHECK will accept the row and every Java comparison will disagree with it. For `status` in particular a mis-cased value is not cosmetic: TRANSITIONS.getOrDefault(status, Set.of()) returns empty, so the alert cannot be advanced by any route. Normalise those rows with the owner before constraining.', 1;
GO

-- ── 2. The constraints ────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway will apply it once on
-- the deployed server. Two of the three names are quoted by a ClosedVocabulary in AmlAlertService
-- and ClosedVocabularyPinTest reads this file to check they agree token for token;
-- CK_aml_alerts_status is checked the same way by StateMachineColumnMirrorTest against the
-- TRANSITIONS map itself, which is why no fourth copy of the status vocabulary is declared in Java.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_aml_alerts_disposition')
    ALTER TABLE ethics.aml_alerts ADD CONSTRAINT CK_aml_alerts_disposition
        CHECK (disposition IN (N'TRUE_POSITIVE', N'FALSE_POSITIVE'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_aml_alerts_severity')
    ALTER TABLE ethics.aml_alerts ADD CONSTRAINT CK_aml_alerts_severity
        CHECK (severity IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_aml_alerts_status')
    ALTER TABLE ethics.aml_alerts ADD CONSTRAINT CK_aml_alerts_status
        CHECK (status IN (N'OPEN', N'INVESTIGATING', N'CLEARED', N'ESCALATED',
                          N'STR_FILED', N'CLOSED'));
GO

-- ── 3. Self-check: three constraints, enabled AND trusted ─────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, disabled against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_aml_alerts_disposition', 'CK_aml_alerts_severity', 'CK_aml_alerts_status')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 3
    THROW 51393, N'V393 self-check: expected 3 enabled, trusted CHECK constraints on ethics.aml_alerts. A constraint that is missing, disabled, or added WITH NOCHECK reads in sys.check_constraints exactly like one that verified every row.', 1;
GO

-- ── 4. Self-check: every token the service can WRITE is still admitted ────────────────────────
-- The mirror of §1. That clause asks whether stored data fits the vocabulary; this one asks whether
-- the vocabulary still admits everything the code emits. If a later migration narrowed one of these
-- and dropped a token, the affected route would start answering 547 on a request that is correct by
-- its own machine, and NOTHING else in the platform would say so — the transition route would simply
-- stop working for one edge. The status tokens are the six keys of AmlAlertService.TRANSITIONS; the
-- disposition tokens are the two branch defaults; MEDIUM is the entity's field initialiser.

DECLARE @probe TABLE (tok NVARCHAR(40), kind NVARCHAR(20));
INSERT INTO @probe (tok, kind) VALUES
    (N'OPEN', N'status'), (N'INVESTIGATING', N'status'), (N'CLEARED', N'status'),
    (N'ESCALATED', N'status'), (N'STR_FILED', N'status'), (N'CLOSED', N'status'),
    (N'TRUE_POSITIVE', N'disposition'), (N'FALSE_POSITIVE', N'disposition'),
    (N'MEDIUM', N'severity');

DECLARE @missing NVARCHAR(MAX);
SELECT @missing = STRING_AGG(CONVERT(NVARCHAR(MAX), kind + N':' + tok), N', ')
  FROM @probe
 WHERE (kind = N'status' AND tok NOT IN (N'OPEN', N'INVESTIGATING', N'CLEARED', N'ESCALATED',
                                         N'STR_FILED', N'CLOSED'))
    OR (kind = N'disposition' AND tok NOT IN (N'TRUE_POSITIVE', N'FALSE_POSITIVE'))
    OR (kind = N'severity' AND tok NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));

IF @missing IS NOT NULL
BEGIN
    DECLARE @missMsg NVARCHAR(2048) =
        N'V393 self-check: a token AmlAlertService can write is not admitted by the vocabulary '
      + N'constrained here — ' + @missing + N'. The route that writes it would answer 547 on a '
      + N'request its own state machine considers valid.';
    THROW 51393, @missMsg, 1;
END
GO

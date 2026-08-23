-- V394 — F2-296: the external-audit engagement register's first CHECK constraint, and the writer
-- that had to be closed in this same commit before it could be added.
--
-- THE DEFECT. `AuditEngagementService.apply` copied `status` straight out of the request:
--
--     if (src.getStatus() != null) target.setStatus(src.getStatus());
--
-- with NO vocabulary check and NO direction check, on a route that binds the ENTITY
-- (`PUT /api/v1/compliance/audit-engagements/{id}`), beside an `advance()` that is explicitly
-- forward-only (`if (to <= from) throw "must advance forward only"`). So the PUT was the register's
-- only reverse edge AND its only unvalidated one: a caller could move a CLOSED IIA engagement back
-- to PLANNED, or store a token no reader anywhere recognises.
--
-- 🔑 F2-294 CLOSED THE CREATE AND FILED THIS REGISTER AS HAVING "closed its only open writers".
-- It had not. That slice removed the entity's `= "PLANNED"` initialiser, which genuinely fixed the
-- PARTIAL-body reset — an omitted status now means "leave it alone" — and left this line, so a body
-- that STATED a status still performed an arbitrary transition. The filing then recommended THIS
-- constraint on the strength of that claim, which is exactly the sequence F2-183 warns about:
-- adding a CHECK over an open writer converts silently-stored junk into a constraint violation.
-- Re-deriving the filing before executing it (F2-221) is the only reason the order came out right.
--
-- ⚠⚠ A CHECK CANNOT SEE DIRECTION, AND SAYING SO IS THE POINT. This file narrows the VOCABULARY:
-- after it, `status = 'TOTALLY_MADE_UP'` is refused by the database. It does nothing whatever about
-- `CLOSED -> PLANNED`, because every token in that move is a legal member. The direction is the Java
-- guard's job (`ServerOwnedState.requireUnchanged`, added in this commit, which tolerates an ECHO
-- and refuses a CHANGE naming `/advance`). A migration that added the CHECK and stopped would leave
-- the reverse edge open WHILE APPEARING TO GUARD IT, which is worse than not having it (F2-183's
-- rule about the collation, one noun over: there the CHECK could not see case, here it cannot see
-- direction).
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-23), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev TSS_GRC / prod TSS_GRC_Deployment — byte-identical
--     compliance.audit_engagements   12 live rows · 7 distinct statuses
--       FIELDWORK 3 · CLOSED 2 · PLANNED 2 · REPORTING 2 · ENGAGED 1 · MGMT_RESPONSE 1
--       · OPENING_MEETING 1        (FOLLOW_UP is declared and stored on 0 rows)
--     outside the declared ladder: 0.  non-canonical casing: 0.  No WITH NOCHECK anywhere.
--
-- ⚠ THE VOCABULARY IS THE SERVICE'S OWN LADDER, not a list assembled for this file.
-- `AuditEngagementService.STATUS_ORDER` declares all eight in order, `advance()` walks it, the
-- frontend's `AUDIT_STATUS_FLOW` declares the same eight in the same order, and every value in both
-- databases is a member. Constraining to the 7 that happen to be STORED would refuse FOLLOW_UP —
-- the state `scheduleFollowUp()` writes — which is F2-180's defect exactly: a column refusing a
-- value its own screen offers.
--
-- ⚠ WHY NO CASING UPDATE. The pre-flight below proves no historical row is stored in non-canonical
-- casing, so there is nothing to normalise; the future is covered by the two writers, which now
-- compare case-insensitively and store the canonical spelling.
--
-- REVERSE RECIPE:
--   ALTER TABLE compliance.audit_engagements DROP CONSTRAINT CK_audit_engagements_status;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending values BEFORE ALTER TABLE does ──────────────────────────
-- ADD CONSTRAINT would fail on its own, but with error 547, which covers both FOREIGN KEY and CHECK
-- and does not name the value or the column (F2-180).

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), val), N', ')
  FROM (
    SELECT DISTINCT status val
      FROM compliance.audit_engagements
     WHERE status NOT IN (N'PLANNED', N'ENGAGED', N'OPENING_MEETING', N'FIELDWORK',
                          N'REPORTING', N'MGMT_RESPONSE', N'CLOSED', N'FOLLOW_UP')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V394 stopped before adding any constraint: compliance.audit_engagements holds a status '
      + N'outside its own declared ladder — ' + @bad + N'. Do NOT widen the CHECK to admit it and '
      + N'do NOT rewrite it here: on an IIA engagement register a status is a governance position, '
      + N'so collapsing a synonym rewrites live rows in a real database and belongs to the owner '
      + N'(F2-183, F2-196, F2-203). Note that such a row can also be advanced to ANY state by '
      + N'advance(), because STATUS_ORDER.indexOf() answers -1 for it and every target then counts '
      + N'as forward.';
    THROW 51394, @badMsg, 1;
END
GO

-- Casing: the vocabulary above is matched case-INSENSITIVELY by the WHERE clause, so a row holding
-- 'closed' would pass the pre-flight AND pass the CHECK. BIN2 is what makes the comparison see case
-- at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;
SELECT @miscased = COUNT(*)
  FROM compliance.audit_engagements
 WHERE status COLLATE Latin1_General_100_BIN2 <> UPPER(status) COLLATE Latin1_General_100_BIN2;
IF @miscased > 0
    THROW 51394, N'V394: compliance.audit_engagements holds a status in non-canonical casing. The CHECK added below would ACCEPT that row while advance() compares it with a case-sensitive List.indexOf and answers -1, which makes every forward target legal from it (F2-179). Normalise those rows with the owner before constraining.', 1;
GO

-- ── 2. The constraint ─────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway applies it once on the
-- deployed server.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_audit_engagements_status')
    ALTER TABLE compliance.audit_engagements ADD CONSTRAINT CK_audit_engagements_status
        CHECK (status IN (N'PLANNED', N'ENGAGED', N'OPENING_MEETING', N'FIELDWORK',
                          N'REPORTING', N'MGMT_RESPONSE', N'CLOSED', N'FOLLOW_UP'));
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, disabled against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name = 'CK_audit_engagements_status' AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 1
    THROW 51394, N'V394 self-check: expected 1 enabled, trusted CHECK constraint on compliance.audit_engagements.status. A constraint that is missing, disabled, or added WITH NOCHECK reads in sys.check_constraints exactly like one that verified every row.', 1;
GO

-- ── 4. Self-check: every rung of the ladder advance() walks is still a legal value ─────────────
-- If a later migration narrowed this vocabulary and dropped a rung, `advance()` would keep offering
-- that target, the UPDATE would fail with a raw 547, and nothing would say which rung had become
-- unreachable. This asserts the machine's own states survive the constraint that narrows them —
-- V392's pattern, and the reason a vocabulary should never be typed twice without one.

DECLARE @rung TABLE (tok NVARCHAR(60));
INSERT INTO @rung (tok) VALUES
    (N'PLANNED'), (N'ENGAGED'), (N'OPENING_MEETING'), (N'FIELDWORK'),
    (N'REPORTING'), (N'MGMT_RESPONSE'), (N'CLOSED'), (N'FOLLOW_UP');

DECLARE @missing NVARCHAR(MAX);
SELECT @missing = STRING_AGG(CONVERT(NVARCHAR(MAX), tok), N', ')
  FROM @rung
 WHERE tok NOT IN (N'PLANNED', N'ENGAGED', N'OPENING_MEETING', N'FIELDWORK',
                   N'REPORTING', N'MGMT_RESPONSE', N'CLOSED', N'FOLLOW_UP');

IF @missing IS NOT NULL
BEGIN
    DECLARE @missMsg NVARCHAR(2048) =
        N'V394 self-check: a rung of AuditEngagementService.STATUS_ORDER is not admitted by the '
      + N'vocabulary constrained here — ' + @missing + N'. advance() would keep offering that '
      + N'target and the UPDATE would fail with a raw 547.';
    THROW 51394, @missMsg, 1;
END
GO

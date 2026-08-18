-- V379 — F2-237: the double-materiality axes had a range rule in ONE route and a CHECK on HALF the
-- columns, and the two halves were not the same half.
--
-- THE DEFECT. `sust.material_topic` records CSRD/ESRS double materiality on two independent 0..5
-- axes — impact_materiality (inside-out) and financial_materiality (outside-in) — from which the
-- service derives materiality_score = MAX(impact, financial) and buckets it into priority_tier at
-- 4.0 / 3.0 / 2.0. Three routes write those axes:
--
--     POST /api/sustainability/materiality/{id}/score   refused anything outside 0..5, mirrored the
--                                                       legacy pair, recomputed, and wrote a SCORE
--                                                       audit row naming both values and the tier
--     POST /api/sustainability/materiality             wrote both axes raw
--     PUT  /api/sustainability/materiality/{id}        wrote all FOUR columns independently, raw
--
-- So the range was a property of one ROUTE rather than of the column, and the two spellings of the
-- same fact could be driven apart: a row could hold impact_materiality 5 with stakeholder_importance
-- 1 — F2-196's disjoint-vocabulary shape inside a single row.
--
-- ⚠⚠ AND THE DATABASE ALREADY SAID 0..5 ABOUT HALF OF IT. Measured 2026-08-18 from
-- sys.check_constraints: the table carries FIVE CHECK constraints, and two of them are
--
--     ck_mt_stake_score = ([stakeholder_importance] >= 0 AND [stakeholder_importance] <= 5)
--     ck_mt_biz_score   = ([business_impact]        >= 0 AND [business_impact]        <= 5)
--
-- — the LEGACY mirror pair — while the canonical impact_materiality / financial_materiality columns
-- that S-Q introduced, and that every current reader prefers, had no constraint of any kind. So the
-- database refused an out-of-range value in the columns nothing writes directly and accepted it in
-- the two the register is about. F2-215's two-of-N shape, at the schema: a reader who finds range
-- CHECKs on this table does not then ask WHICH columns they name.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation. Both
-- columns constrained here have provably closed writers as of the same commit: every axis write in
-- MaterialTopicService now goes through the one private writer applyAxes(), which calls requireAxis()
-- on each value before it reaches the entity, and validate() — called by create() and update() —
-- re-checks the stored pair so a future write path that forgets applyAxes still cannot store an
-- out-of-range score.
--
-- ⚠ require, not coerce (F2-182's test: what already refuses this?). The frontend gates 0..5 in the
-- create form, the edit form and the score dialog, so no request a human can make through the
-- platform starts failing — which is precisely why the hole was reachable only by a machine caller,
-- and machine callers are where this family of defect lives (F2-179). Clamping would silently record
-- a materiality score nobody chose.
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-18), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev   TSS_GRC              26 live rows
--     prod  TSS_GRC_Deployment   26 live rows   (byte-identical seed)
--     rows with an axis outside 0..5:                     0   in both
--     rows whose legacy mirror disagrees with its axis:    0   in both
--
-- No WITH NOCHECK — SQL Server verifying the existing rows is the point, and the self-check below
-- fails the migration if either constraint lands untrusted.
--
-- ⚠ NO CASING CONCERN HERE, unlike V375 and V378: these are decimal(3,1) columns, not text. The
-- collation trap this repo keeps meeting (F2-179) cannot apply to a numeric range — which is worth
-- saying rather than leaving the reader to wonder why the pre-flight is shorter than its siblings.
--
-- ⚙ FILED, NOT FIXED HERE — the stored DERIVED values. recomputeScore runs on every save, and
-- measured in both databases 20 of the 26 live rows hold a materiality_score LOWER than
-- MAX(impact, financial) and 0 hold one higher, so the register UNDER-states how material each topic
-- is; 14 priority tiers would move on the next save, 12 of them to CRITICAL; and 8 rows' stored tier
-- disagrees with their own stored score. Re-deriving rewrites 26 live rows in two real databases and
-- is an owner decision (F2-183, F2-196, F2-230), so this migration deliberately does not touch a
-- single row. What the same commit does add is an audit line that names the move, so the jump an
-- operator's first edit causes is recorded rather than silent.
--
-- REVERSE RECIPE:
--   ALTER TABLE sust.material_topic DROP CONSTRAINT CK_mt_impact_materiality;
--   ALTER TABLE sust.material_topic DROP CONSTRAINT CK_mt_financial_materiality;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending rows BEFORE ALTER TABLE does ────────────────────────────
-- ADD CONSTRAINT fails on its own, but with error 547 and no mention of which value in which column
-- (F2-180: 547 covers both FOREIGN KEY and CHECK, so a diagnostic that does not name the column
-- sends the reader hunting a foreign key that may not exist).

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src), N', ')
  FROM (
    SELECT DISTINCT N'impact_materiality=' + CONVERT(NVARCHAR(20), impact_materiality) src
      FROM sust.material_topic
     WHERE impact_materiality < 0 OR impact_materiality > 5
    UNION ALL
    SELECT DISTINCT N'financial_materiality=' + CONVERT(NVARCHAR(20), financial_materiality)
      FROM sust.material_topic
     WHERE financial_materiality < 0 OR financial_materiality > 5
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V379 stopped before adding any constraint: sust.material_topic holds a double-materiality '
      + N'axis outside the 0..5 range the register declares — ' + @bad + N'. These rows predate the '
      + N'range rule, so decide with the owner what the score should be: clamping or zeroing a '
      + N'materiality score rewrites a CMA Article 92(a) disclosure input and is not a migration''s '
      + N'decision to make (F2-183, F2-196).';
    THROW 51379, @badMsg, 1;
END
GO

-- ── 2. The two constraints the canonical pair never had ───────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway applies it once on the
-- deployed server. The `mt_` stem matches the legacy pair's ck_mt_stake_score / ck_mt_biz_score so
-- the four read as one rule; the `CK_` casing follows V375/V378, this repo's current convention.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_mt_impact_materiality')
    ALTER TABLE sust.material_topic ADD CONSTRAINT CK_mt_impact_materiality
        CHECK (impact_materiality >= 0 AND impact_materiality <= 5);
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_mt_financial_materiality')
    ALTER TABLE sust.material_topic ADD CONSTRAINT CK_mt_financial_materiality
        CHECK (financial_materiality >= 0 AND financial_materiality <= 5);
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, unverified against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_mt_impact_materiality', 'CK_mt_financial_materiality')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 2
    THROW 51379, N'V379 self-check: expected 2 enabled, trusted CHECK constraints on sust.material_topic (CK_mt_impact_materiality, CK_mt_financial_materiality).', 1;
GO

-- ── 4. And the four range constraints must now agree ──────────────────────────────────────────
-- The whole point of this file is that half the columns were constrained and half were not. If a
-- later migration drops one of the legacy pair, this asserts that the register noticed.
DECLARE @all INT;
SELECT @all = COUNT(*) FROM sys.check_constraints cc
  JOIN sys.tables t ON t.object_id = cc.parent_object_id
  JOIN sys.schemas s ON s.schema_id = t.schema_id
 WHERE s.name = 'sust' AND t.name = 'material_topic'
   AND cc.name IN ('ck_mt_stake_score', 'ck_mt_biz_score',
                   'CK_mt_impact_materiality', 'CK_mt_financial_materiality');
IF @all <> 4
    THROW 51379, N'V379 self-check: sust.material_topic should carry a 0..5 CHECK on all FOUR score columns — the canonical axes (impact_materiality, financial_materiality) and the legacy mirror (stakeholder_importance, business_impact). One of the four is missing, which is the exact asymmetry F2-237 closed.', 1;
GO

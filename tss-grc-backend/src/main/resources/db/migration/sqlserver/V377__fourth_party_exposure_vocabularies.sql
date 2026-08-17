-- V377 — F2-229: the fourth-party register's first CHECK constraints, and the tier vocabulary that
-- had no declaration anywhere.
--
-- THE DEFECT. `scm.fourth_party_exposures.concentration_risk` is derived: the service recomputes it
-- across the whole tenant on every create and every update. The derivation's output range is
-- LOW|MEDIUM|HIGH — there is no number of primary vendors that yields CRITICAL — and it ASSIGNED
-- that result rather than elevating to it, although the service javadoc said "auto-elevated", the
-- repository javadoc said "elevate", and the auto-CAPA hook was written as a rising edge. Measured
-- 2026-08-17, identical in dev and prod: all 13 live rows carry 13 DISTINCT fourth-party names, so
-- the multiplicity rule matches nothing and computes LOW for every row — and any write LOWERED 9 of
-- 13, including all three HIGH rows and the sole CRITICAL one (an underlying hyperscaler region).
--
-- 🔑 A RECOMPUTE WHOSE OUTPUT RANGE IS A STRICT SUBSET OF ITS COLUMN'S VOCABULARY DESTROYS THE
-- VALUES IT CANNOT EXPRESS, and cannot restore them afterwards. CRITICAL was stored in both
-- databases while being declared nowhere: the entity's field javadoc said LOW | MEDIUM | HIGH, the
-- frontend's FpConcentration type said the same, and the table had no CHECK constraint of any kind —
-- so the vocabulary existed only in the heads of its readers (F2-196) and every one of them was
-- narrower than the column.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation. All four
-- columns constrained here have provably closed writers, and "provably" means measured:
--
--     concentration_risk       · server-owned; SEC-FIN-004 keeps it off both request DTOs
--                             · exactly TWO write sites in FourthPartyExposureService — the create's
--                               initial ConcentrationTier.ladder().get(0), and recomputeConcentration
--                             · both write literals drawn from ConcentrationTier's own ladder
--     dependency_type         · from the request, and F2-229 routes both write sites through
--     downstream_criticality    ClosedVocabulary.require(), which trims, upper-cases and refuses
--                             · the previous hand-rolled Set.contains already refused anything
--                               outside the set, so nothing that succeeds today starts failing
--     status                  · server-owned; the create forces DISCOVERED and the three transition
--                               methods write named constants. No caller supplies a status.
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-17), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev   TSS_GRC             13 rows, 13 live
--     prod  TSS_GRC_Deployment  13 rows, 13 live   (byte-identical seed)
--     dependency_type        INFRASTRUCTURE 5 · DATA_PROCESSING 3 · SUPPORT 3 · SOFTWARE 2
--     downstream_criticality HIGH 5 · CRITICAL 4 · MEDIUM 3 · LOW 1
--     concentration_risk     MEDIUM 5 · LOW 4 · HIGH 3 · CRITICAL 1
--     status                 VALIDATED 10 · DISCOVERED 2 · MITIGATED 1
--     violations: 0 in all four columns, in both databases.
--
-- No WITH NOCHECK anywhere — SQL Server verifying the existing rows is the point, and the self-check
-- below fails the migration if any constraint lands untrusted.
--
-- ⚠ WHY THERE IS NO CASING UPDATE HERE, UNLIKE V375. The collation is case-INSENSITIVE, so a CHECK
-- alone accepts 'high' and stores it verbatim (F2-179). That does not apply to these four columns,
-- and it was checked rather than assumed: the two caller-supplied columns are normalised by
-- ClosedVocabulary.require() before they reach the entity, the other two are written from Java
-- literals, and the pre-flight below proves no historical row slipped through. If a future writer
-- ever coerces instead of refusing, that pre-flight is what will say so.
--
-- REVERSE RECIPE:
--   ALTER TABLE scm.fourth_party_exposures DROP CONSTRAINT CK_fpe_concentration_risk;
--   ALTER TABLE scm.fourth_party_exposures DROP CONSTRAINT CK_fpe_downstream_criticality;
--   ALTER TABLE scm.fourth_party_exposures DROP CONSTRAINT CK_fpe_dependency_type;
--   ALTER TABLE scm.fourth_party_exposures DROP CONSTRAINT CK_fpe_status;

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
    SELECT DISTINCT N'concentration_risk' src, concentration_risk val
      FROM scm.fourth_party_exposures
     WHERE concentration_risk NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT DISTINCT N'downstream_criticality', downstream_criticality
      FROM scm.fourth_party_exposures
     WHERE downstream_criticality NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT DISTINCT N'dependency_type', dependency_type
      FROM scm.fourth_party_exposures
     WHERE dependency_type NOT IN (N'DATA_PROCESSING', N'INFRASTRUCTURE', N'SOFTWARE',
                                   N'LOGISTICS', N'FINANCIAL', N'SUPPORT')
    UNION ALL
    SELECT DISTINCT N'status', status
      FROM scm.fourth_party_exposures
     WHERE status NOT IN (N'DISCOVERED', N'VALIDATED', N'MITIGATED', N'RETIRED')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V377 stopped before adding any constraint: scm.fourth_party_exposures holds values outside '
      + N'the service''s own vocabularies — ' + @bad + N'. Reconcile with the owner before '
      + N'constraining: collapsing a synonym rewrites live rows in a real database and is not a '
      + N'migration''s decision to make (F2-183, F2-196, F2-203). Note that CRITICAL in '
      + N'concentration_risk is EXPECTED and legal from V377 onward — it was stored in both '
      + N'databases while no artefact declared it, which is the finding F2-229 closed.';
    THROW 51377, @badMsg, 1;
END
GO

-- Casing: the vocabularies above are matched case-INSENSITIVELY by the WHERE clauses, so a row
-- holding 'high' would have passed the pre-flight AND will pass the CHECK. BIN2 is what makes the
-- comparison see case at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;
SELECT @miscased = COUNT(*)
  FROM scm.fourth_party_exposures
 WHERE concentration_risk     COLLATE Latin1_General_100_BIN2
         <> UPPER(concentration_risk)     COLLATE Latin1_General_100_BIN2
    OR downstream_criticality COLLATE Latin1_General_100_BIN2
         <> UPPER(downstream_criticality) COLLATE Latin1_General_100_BIN2
    OR dependency_type        COLLATE Latin1_General_100_BIN2
         <> UPPER(dependency_type)        COLLATE Latin1_General_100_BIN2
    OR status                 COLLATE Latin1_General_100_BIN2
         <> UPPER(status)                 COLLATE Latin1_General_100_BIN2;
IF @miscased > 0
    THROW 51377, N'V377: a vocabulary column on scm.fourth_party_exposures is stored in non-canonical casing. Every writer either normalises (ClosedVocabulary.require) or writes a Java literal, so this cannot have come from the API — investigate the source before constraining, because the CHECK will accept it and the Java comparisons will not (F2-179).', 1;
GO

-- ── 2. The constraints ────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway will apply it once on
-- the deployed server. The names are not decoration — each is quoted by a ClosedVocabulary in
-- FourthPartyExposureService / ConcentrationTier, and ClosedVocabularyPinTest reads this file to
-- check the two agree token for token.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_fpe_concentration_risk')
    ALTER TABLE scm.fourth_party_exposures ADD CONSTRAINT CK_fpe_concentration_risk
        CHECK (concentration_risk IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_fpe_downstream_criticality')
    ALTER TABLE scm.fourth_party_exposures ADD CONSTRAINT CK_fpe_downstream_criticality
        CHECK (downstream_criticality IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_fpe_dependency_type')
    ALTER TABLE scm.fourth_party_exposures ADD CONSTRAINT CK_fpe_dependency_type
        CHECK (dependency_type IN (N'DATA_PROCESSING', N'INFRASTRUCTURE', N'SOFTWARE',
                                   N'LOGISTICS', N'FINANCIAL', N'SUPPORT'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_fpe_status')
    ALTER TABLE scm.fourth_party_exposures ADD CONSTRAINT CK_fpe_status
        CHECK (status IN (N'DISCOVERED', N'VALIDATED', N'MITIGATED', N'RETIRED'));
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, disabled against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_fpe_concentration_risk', 'CK_fpe_downstream_criticality',
                'CK_fpe_dependency_type', 'CK_fpe_status')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 4
    THROW 51377, N'V377 self-check: expected 4 enabled, trusted CHECK constraints on scm.fourth_party_exposures (CK_fpe_concentration_risk, CK_fpe_downstream_criticality, CK_fpe_dependency_type, CK_fpe_status).', 1;
GO

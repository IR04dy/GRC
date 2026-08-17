-- V378 — F2-230: the third-party cyber register's first CHECK constraints, and the residual ladder
-- that had no declaration anywhere.
--
-- THE DEFECT. `tprm.third_party_cyber_assessments.residual_risk_tier` is derived: the service
-- recomputes it from a 0-100 posture score on create and on every posture recording. The derivation
-- produces a FIVE-rung ladder — LOW < MODERATE < ELEVATED < HIGH < CRITICAL — which existed nowhere
-- but as an inline ternary chain inside the recompute. The register's default ordering was then the
-- derived query method `findByOrganisationIdOrderByResidualRiskTierDescIdDesc`: a LEXICAL sort of an
-- NVARCHAR column, presented as a severity ranking. Alphabetically DESC those five rungs read
--
--     MODERATE > LOW > HIGH > ELEVATED > CRITICAL
--
-- so CRITICAL sorted LAST and LOW sorted SECOND. Measured 2026-08-17 in both real databases
-- (byte-identical): 16 rows holding LOW 7 / MODERATE 7 / ELEVATED 2, and `ORDER BY residual_risk_tier
-- DESC` returns six MODERATE rows before either ELEVATED one — the two most exposed vendors in the
-- register, pushed below every moderate row. The endpoint is PAGED, so on a larger tenant the most
-- exposed third parties are not merely mis-ordered, they are on a page nobody opens.
--
-- ⚠ There is an index on (organisation_id, residual_risk_tier) from V53, which is what made the wrong
-- ordering fast. A lexical sort backed by a covering index reads like a considered decision.
--
-- 🔑 A LEXICAL SORT IS A RANKING ONLY WHERE THE VOCABULARY HAPPENS TO BE IN ALPHABETICAL ORDER, and
-- the property that decides it is whether the vocabulary is ORDERED — not whether its name sounds
-- graded. F2-230 measured the whole platform from the values its columns actually store rather than
-- from their names: of 483 lexically-sorted columns, 118 are String-typed and 57 hold a closed
-- vocabulary, of which the great majority are CATEGORICAL (form_type, regulatory_domain, pillar,
-- asset_class …) where an alphabetical sort is a deterministic grouping that claims nothing.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation. All three
-- columns constrained here have provably closed writers, and the same commit is what closed two of
-- them:
--
--     residual_risk_tier   · server-owned; absent from every request path because `create` and
--                            `recordPosture` both call recompute(), which overwrites it
--                          · recompute() now indexes ResidualRiskTier.ladder() rather than naming the
--                            five tokens in a ternary, so the derivation cannot produce a non-member
--     inherent_cyber_risk  · from the request, and F2-230 routes it through
--                            ClosedVocabulary.require(), which trims, upper-cases and refuses. The
--                            previous hand-rolled Set.contains already refused anything outside the
--                            set, so nothing that succeeds today starts failing (F2-182).
--     status               · server-owned as of this commit. It was `if (status == null) status =
--                            "DRAFT"` — a DEFAULT, not a guard (F2-218), so a caller could create an
--                            assessment already APPROVED, skipping submit() and approve() and with
--                            them the only writers of assessed_at, approved_at and approver_name.
--                            Now ServerOwnedState.requireInitial(...). No screen has a create form
--                            for this register, so no working request sent a status.
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-17), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev   TSS_GRC              16 rows
--     prod  TSS_GRC_Deployment   16 rows   (byte-identical seed)
--     residual_risk_tier   LOW 7 · MODERATE 7 · ELEVATED 2
--     inherent_cyber_risk  HIGH 6 · CRITICAL 5 · MEDIUM 4 · LOW 1
--     status               APPROVED 14 · IN_REVIEW 1 · RETIRED 1
--     violations: 0 in all three columns, in both databases. Non-canonical casings: 0.
--     existing CHECK constraints on this table: NONE (of any kind, on any column).
--
-- No WITH NOCHECK anywhere — SQL Server verifying the existing rows is the point, and the self-check
-- below fails the migration if any constraint lands untrusted.
--
-- ⚠ NO CASING UPDATE HERE, and that was checked rather than assumed. The collation is
-- case-INSENSITIVE, so a CHECK alone accepts 'elevated' and stores it verbatim (F2-179) — but
-- residual_risk_tier and status are written from Java literals drawn from the declared ladder, and
-- inherent_cyber_risk goes through ClosedVocabulary.require(), which upper-cases before the value
-- reaches the entity. The pre-flight below proves no historical row slipped through, and it is what
-- will say so if a future writer ever coerces instead of refusing.
--
-- REVERSE RECIPE:
--   ALTER TABLE tprm.third_party_cyber_assessments DROP CONSTRAINT CK_tpc_residual_risk_tier;
--   ALTER TABLE tprm.third_party_cyber_assessments DROP CONSTRAINT CK_tpc_inherent_cyber_risk;
--   ALTER TABLE tprm.third_party_cyber_assessments DROP CONSTRAINT CK_tpc_status;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending values BEFORE ALTER TABLE does ──────────────────────────
-- ADD CONSTRAINT fails on its own, but with error 547 and no mention of which value in which column.
-- F2-180 measured what that costs: 547 covers both FOREIGN KEY and CHECK, so a diagnostic that does
-- not name the column sends the reader hunting a foreign key that may not exist.

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src + N'=' + val), N', ')
  FROM (
    SELECT DISTINCT N'residual_risk_tier' src, residual_risk_tier val
      FROM tprm.third_party_cyber_assessments
     WHERE residual_risk_tier NOT IN (N'LOW', N'MODERATE', N'ELEVATED', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT DISTINCT N'inherent_cyber_risk', inherent_cyber_risk
      FROM tprm.third_party_cyber_assessments
     WHERE inherent_cyber_risk NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT DISTINCT N'status', status
      FROM tprm.third_party_cyber_assessments
     WHERE status NOT IN (N'DRAFT', N'IN_REVIEW', N'APPROVED', N'RETIRED')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V378 stopped before adding any constraint: tprm.third_party_cyber_assessments holds values '
      + N'outside the service''s own vocabularies — ' + @bad + N'. Reconcile with the owner before '
      + N'constraining: collapsing a synonym rewrites live rows in a real database and is not a '
      + N'migration''s decision to make (F2-183, F2-196, F2-203). Note that HIGH and CRITICAL in '
      + N'residual_risk_tier are EXPECTED and legal from V378 onward — the recompute can produce '
      + N'both, no row has reached either yet, and neither had any declaration before F2-230.';
    THROW 51378, @badMsg, 1;
END
GO

-- Casing: the vocabularies above are matched case-INSENSITIVELY by those WHERE clauses, so a row
-- holding 'elevated' would pass the pre-flight AND pass the CHECK. BIN2 is what makes the comparison
-- see case at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;
SELECT @miscased = COUNT(*)
  FROM tprm.third_party_cyber_assessments
 WHERE residual_risk_tier  COLLATE Latin1_General_100_BIN2
         <> UPPER(residual_risk_tier)  COLLATE Latin1_General_100_BIN2
    OR inherent_cyber_risk COLLATE Latin1_General_100_BIN2
         <> UPPER(inherent_cyber_risk) COLLATE Latin1_General_100_BIN2
    OR status              COLLATE Latin1_General_100_BIN2
         <> UPPER(status)              COLLATE Latin1_General_100_BIN2;
IF @miscased > 0
    THROW 51378, N'V378: a vocabulary column on tprm.third_party_cyber_assessments is stored in non-canonical casing. Every writer either normalises (ClosedVocabulary.require) or writes a literal drawn from ResidualRiskTier''s ladder, so this cannot have come from the API — investigate the source before constraining, because the CHECK will accept it and the Java comparisons will not (F2-179).', 1;
GO

-- ── 2. The constraints ────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway applies it once on the
-- deployed server. The names are not decoration — each is quoted by a ClosedVocabulary or an
-- OrderedVocabulary in the tprm.cyber service, and ClosedVocabularyPinTest reads this file to check
-- the two agree token for token.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_tpc_residual_risk_tier')
    ALTER TABLE tprm.third_party_cyber_assessments ADD CONSTRAINT CK_tpc_residual_risk_tier
        CHECK (residual_risk_tier IN (N'LOW', N'MODERATE', N'ELEVATED', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_tpc_inherent_cyber_risk')
    ALTER TABLE tprm.third_party_cyber_assessments ADD CONSTRAINT CK_tpc_inherent_cyber_risk
        CHECK (inherent_cyber_risk IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_tpc_status')
    ALTER TABLE tprm.third_party_cyber_assessments ADD CONSTRAINT CK_tpc_status
        CHECK (status IN (N'DRAFT', N'IN_REVIEW', N'APPROVED', N'RETIRED'));
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, unverified against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_tpc_residual_risk_tier', 'CK_tpc_inherent_cyber_risk', 'CK_tpc_status')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 3
    THROW 51378, N'V378 self-check: expected 3 enabled, trusted CHECK constraints on tprm.third_party_cyber_assessments (CK_tpc_residual_risk_tier, CK_tpc_inherent_cyber_risk, CK_tpc_status).', 1;
GO

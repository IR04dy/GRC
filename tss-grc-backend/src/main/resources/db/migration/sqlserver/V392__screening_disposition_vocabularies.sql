-- V392 — F2-288: the ethics AML screening register's first CHECK constraints, and the three
-- vocabularies the escalation that makes a match matter both reads and dispatches on.
--
-- THE DEFECT. `KycService` reacted to a confirmed sanctions/PEP match by flagging the subject —
-- `is_sanctioned` + `risk_rating = PROHIBITED` for the four sanctions lists, `is_pep` +
-- `edd_required` + a rating raise for PEP_GLOBAL — and that reaction lived inline in `recordHit`
-- ONLY. `disposition()`, the route a reviewer uses to ADJUDICATE a hit, wrote the decision and
-- applied none of the consequences. So confirming a UN or OFAC match left the subject unflagged and
-- its rating untouched, and confirming a PEP match left `edd_required` false — the flag FATF R.12
-- and SAMA's AML/CFT rules turn on.
--
-- 🔑 THE COMMENT IN `recordHit` IS WHY NOBODY ASKED. It documents the reaction mechanism at length,
-- on the one path that had it, and warns against refusing the caller's disposition because "the
-- service *reacts* to it below". All true, and about `recordHit`. A reader who finds a mechanism
-- explained does not then ask which sibling route is missing it (F2-215's two-of-N). That same
-- comment lists THREE of the vocabulary's four tokens, which is what made it a fourth copy.
--
-- 🔑 AND THE VOCABULARY LIVED IN FOUR PLACES, ENFORCED IN NONE. `ethics.screening_hits` had ZERO
-- CHECK constraints of any kind, so `disposition` was narrowed by nothing: the frontend's
-- HIT_DISPOSITIONS dropdown (4 tokens), `recordHit`'s case-sensitive "TRUE_MATCH".equals(...), the
-- badge's tone map, and the comment above (3 tokens). `disposition()` stored whatever arrived.
-- Because the collation is case-INSENSITIVE, a caller sending 'true_match' had it stored verbatim
-- and the escalation never fired (F2-179) — the register recording a confirmed sanctions match
-- while the subject stayed unflagged.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS (F2-183). Adding a CHECK to a column whose
-- writer is still open converts today's silently-stored junk into a constraint violation, and on an
-- AML register that is a lost screening record. All three columns constrained here have writers the
-- SAME COMMIT closes, and "closed" means measured:
--
--     screening_hits.disposition   · two write sites, both now ClosedVocabulary
--                                    (recordHit: orDefault(..,'NEEDS_REVIEW'); disposition: require)
--     screening_hits.list_source   · one write site, recordHit, now require()
--     kyc_subjects.risk_rating     · four write sites: apply() now require(); create() now
--                                    orDefault(..,'STANDARD'); and the two escalation branches in
--                                    applyConfirmedMatch, which write the literals 'PROHIBITED'
--                                    and 'HIGH' — both members
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-23), because ADD CONSTRAINT
-- verifies the rows already present and one violation fails Flyway and blocks the whole next jar:
--
--     dev  TSS_GRC / prod TSS_GRC_Deployment — byte-identical on all three columns
--     screening_hits   12 rows · disposition  NEEDS_REVIEW 10 · TRUE_MATCH 2
--                              · list_source  PEP_GLOBAL 10 · UN_CONSOLIDATED 1 · ADVERSE_MEDIA 1
--     kyc_subjects     16 rows · risk_rating  STANDARD 8 · HIGH 6 · LOW 1 · PROHIBITED 1
--     violations: 0 in all three columns, in both databases. No WITH NOCHECK anywhere.
--
-- ⚠ THE FRONTEND'S DROPDOWNS ARE THE SOURCE OF THE TWO HIT VOCABULARIES, and that is deliberate
-- rather than lazy: they are the only place either was ever written down in full, they are what an
-- operator can actually pick, and every value stored in either database is a member. Constraining
-- to anything narrower would refuse the register's own data (F2-180 measured what that costs — 19
-- of 22 incident categories a screen offered were refused by its column's CHECK).
--
-- ⚠ WHY NO CASING UPDATE, UNLIKE V375. The pre-flight below proves no historical row is stored in
-- non-canonical casing, so there is nothing to normalise; the future is covered by the writers,
-- which now upper-case before the value reaches the entity. A CHECK alone would NOT have been
-- enough — it accepts 'true_match' — which is exactly why the writer change and this file are one
-- commit and not two.
--
-- REVERSE RECIPE:
--   ALTER TABLE ethics.screening_hits DROP CONSTRAINT CK_screening_hits_disposition;
--   ALTER TABLE ethics.screening_hits DROP CONSTRAINT CK_screening_hits_list_source;
--   ALTER TABLE ethics.kyc_subjects   DROP CONSTRAINT CK_kyc_subjects_risk_rating;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Pre-flight: name the offending values BEFORE ALTER TABLE does ──────────────────────────
-- ADD CONSTRAINT would fail on its own, but with error 547, which covers both FOREIGN KEY and
-- CHECK and does not say which value in which column (F2-180).

DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src + N'=' + val), N', ')
  FROM (
    SELECT DISTINCT N'screening_hits.disposition' src, disposition val
      FROM ethics.screening_hits
     WHERE disposition NOT IN (N'NEEDS_REVIEW', N'POTENTIAL_MATCH', N'TRUE_MATCH',
                               N'FALSE_POSITIVE')
    UNION ALL
    SELECT DISTINCT N'screening_hits.list_source', list_source
      FROM ethics.screening_hits
     WHERE list_source NOT IN (N'OFAC_SDN', N'UN_CONSOLIDATED', N'EU_CONSOLIDATED',
                               N'SAMA_LOCAL', N'PEP_GLOBAL', N'INTERPOL_RED',
                               N'ADVERSE_MEDIA', N'INTERNAL')
    UNION ALL
    SELECT DISTINCT N'kyc_subjects.risk_rating', risk_rating
      FROM ethics.kyc_subjects
     WHERE risk_rating NOT IN (N'LOW', N'STANDARD', N'HIGH', N'PROHIBITED')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V392 stopped before adding any constraint: the ethics AML register holds values outside '
      + N'the vocabularies its own screen offers — ' + @bad + N'. Do NOT widen the CHECK to admit '
      + N'them and do NOT rewrite them here: on an AML register a status word is a regulatory '
      + N'decision, so collapsing a synonym rewrites live rows in a real database and belongs to '
      + N'the owner (F2-183, F2-196, F2-203).';
    THROW 51392, @badMsg, 1;
END
GO

-- Casing: the vocabularies above are matched case-INSENSITIVELY by the WHERE clauses, so a row
-- holding 'true_match' would pass the pre-flight AND pass the CHECK. BIN2 is what makes the
-- comparison see case at all; without it this statement is a silent no-op (V375's lesson).
DECLARE @miscased INT;
SELECT @miscased = COUNT(*)
  FROM ethics.screening_hits
 WHERE disposition COLLATE Latin1_General_100_BIN2 <> UPPER(disposition) COLLATE Latin1_General_100_BIN2
    OR list_source COLLATE Latin1_General_100_BIN2 <> UPPER(list_source) COLLATE Latin1_General_100_BIN2;
IF @miscased > 0
    THROW 51392, N'V392: ethics.screening_hits holds a disposition or list_source in non-canonical casing. That is the F2-288 defect having already happened — the CHECK will accept the row and every Java comparison will disagree with it (F2-179). Normalise those rows with the owner before constraining, so the escalation can be re-evaluated for them.', 1;
GO

DECLARE @miscasedRating INT;
SELECT @miscasedRating = COUNT(*)
  FROM ethics.kyc_subjects
 WHERE risk_rating COLLATE Latin1_General_100_BIN2 <> UPPER(risk_rating) COLLATE Latin1_General_100_BIN2;
IF @miscasedRating > 0
    THROW 51392, N'V392: ethics.kyc_subjects holds a risk_rating in non-canonical casing. The PEP branch of applyConfirmedMatch reads this column to decide whether to raise the rating, so a mis-cased value means a confirmed PEP match silently left the rating alone.', 1;
GO

-- ── 2. The constraints ────────────────────────────────────────────────────────────────────────
-- Idempotent: applied twice to dev by hand (Flyway is off in dev), and Flyway will apply it once on
-- the deployed server. The names are not decoration — each is quoted by a ClosedVocabulary in
-- KycService, and ClosedVocabularyPinTest reads this file to check the two agree token for token.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_screening_hits_disposition')
    ALTER TABLE ethics.screening_hits ADD CONSTRAINT CK_screening_hits_disposition
        CHECK (disposition IN (N'NEEDS_REVIEW', N'POTENTIAL_MATCH', N'TRUE_MATCH',
                               N'FALSE_POSITIVE'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_screening_hits_list_source')
    ALTER TABLE ethics.screening_hits ADD CONSTRAINT CK_screening_hits_list_source
        CHECK (list_source IN (N'OFAC_SDN', N'UN_CONSOLIDATED', N'EU_CONSOLIDATED',
                               N'SAMA_LOCAL', N'PEP_GLOBAL', N'INTERPOL_RED',
                               N'ADVERSE_MEDIA', N'INTERNAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_kyc_subjects_risk_rating')
    ALTER TABLE ethics.kyc_subjects ADD CONSTRAINT CK_kyc_subjects_risk_rating
        CHECK (risk_rating IN (N'LOW', N'STANDARD', N'HIGH', N'PROHIBITED'));
GO

-- ── 3. Self-check ─────────────────────────────────────────────────────────────────────────────
-- Existence is not enough: a constraint added WITH NOCHECK is present, disabled against history and
-- reads in sys.check_constraints exactly like one that verified every row.

DECLARE @n INT;
SELECT @n = COUNT(*) FROM sys.check_constraints
 WHERE name IN ('CK_screening_hits_disposition', 'CK_screening_hits_list_source',
                'CK_kyc_subjects_risk_rating')
   AND is_disabled = 0 AND is_not_trusted = 0;
IF @n <> 3
    THROW 51392, N'V392 self-check: expected 3 enabled, trusted CHECK constraints on the ethics AML register. A constraint that is missing, disabled, or added WITH NOCHECK reads in sys.check_constraints exactly like one that verified every row.', 1;
GO

-- ── 4. Self-check: the escalation''s own dispatch keys are still legal values ──────────────────
-- applyConfirmedMatch dispatches on list_source and compares against the literal 'TRUE_MATCH'. If a
-- later migration narrowed either vocabulary and dropped one of those tokens, the escalation would
-- become unreachable for that list and NOTHING would say so — the register would keep accepting
-- confirmed matches and quietly stop flagging subjects. This asserts the dispatch keys survive.

DECLARE @probe TABLE (tok NVARCHAR(60), kind NVARCHAR(20));
INSERT INTO @probe (tok, kind) VALUES
    (N'TRUE_MATCH', N'disposition'),
    (N'OFAC_SDN', N'list_source'), (N'UN_CONSOLIDATED', N'list_source'),
    (N'EU_CONSOLIDATED', N'list_source'), (N'SAMA_LOCAL', N'list_source'),
    (N'PEP_GLOBAL', N'list_source'),
    (N'PROHIBITED', N'risk_rating'), (N'HIGH', N'risk_rating'),
    (N'STANDARD', N'risk_rating'), (N'LOW', N'risk_rating');

DECLARE @missing NVARCHAR(MAX);
SELECT @missing = STRING_AGG(CONVERT(NVARCHAR(MAX), kind + N':' + tok), N', ')
  FROM @probe
 WHERE (kind = N'disposition' AND tok NOT IN (N'NEEDS_REVIEW', N'POTENTIAL_MATCH',
                                              N'TRUE_MATCH', N'FALSE_POSITIVE'))
    OR (kind = N'list_source' AND tok NOT IN (N'OFAC_SDN', N'UN_CONSOLIDATED',
                                              N'EU_CONSOLIDATED', N'SAMA_LOCAL', N'PEP_GLOBAL',
                                              N'INTERPOL_RED', N'ADVERSE_MEDIA', N'INTERNAL'))
    OR (kind = N'risk_rating' AND tok NOT IN (N'LOW', N'STANDARD', N'HIGH', N'PROHIBITED'));

IF @missing IS NOT NULL
BEGIN
    DECLARE @missMsg NVARCHAR(2048) =
        N'V392 self-check: a token applyConfirmedMatch dispatches on is not admitted by the '
      + N'vocabulary constrained here — ' + @missing + N'. The escalation would be unreachable for '
      + N'that list and nothing else in the platform would say so.';
    THROW 51392, @missMsg, 1;
END
GO

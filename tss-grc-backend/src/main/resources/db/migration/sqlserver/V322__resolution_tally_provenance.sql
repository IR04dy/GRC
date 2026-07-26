-- =====================================================================
-- V322 — F2-40: a resolution's vote tally stops being indistinguishable from a computed one.
-- ---------------------------------------------------------------------
-- THE MEASUREMENT, AND WHAT IT CHANGED ABOUT THE FIX.
--
-- admin.committee_resolutions holds 20 rows. admin.committee_resolution_vote holds ZERO.
-- All 20 carry a scalar tally and outcome APPROVED:
--
--     RES-2026-Q1-BRD-001  APPROVED  5/0/0   votes=0
--     RES-2026-Q1-BRD-003  APPROVED  4/1/0   votes=0
--     ... 18 more, every one APPROVED, not one with a single vote row
--
-- This was carried out of F2-28 as "seeded scalar tallies", the resolution-level twin of the
-- quorum defect V319 fixed. F2-28's shape held: the mechanism (per-member votes ->
-- recomputeTally) was correct and its data never existed.
--
-- ⚠ BUT THE PRESCRIBED FIX WAS WRONG, and the UI is what falsified it. The obvious reading —
-- "the tally is caller-authored, so refuse the caller's tally" — would have broken the
-- platform's own minute-taking flow: CommitteesPage.tsx has a resolution form whose fields
-- include voteFor / voteAgainst / voteAbstain / outcome, filled in by the secretariat from the
-- minutes. A committee that votes on paper and has its decision recorded afterwards is not an
-- attacker; it is the normal case for a board. Ignoring those fields would have discarded the
-- recorded decision silently — the same class of mistake as F2-22a's prescribed @JsonIgnore,
-- which would have stripped `id` from every response.
--
-- So an asserted tally stays legal and becomes LABELLED, exactly as V319 did for quorum:
--
--     COMPUTED         derived by the platform from committee_resolution_vote rows
--     ASSERTED         recorded by a named user from minutes decided outside the platform
--     ASSERTED_LEGACY  present before V322, provenance unknown and unknowable
--
-- ---------------------------------------------------------------------
-- WHY THIS IS NOT COSMETIC: recomputeTally() OVERWRITES.
--
-- recomputeTally() re-derives vote_for/against/abstain from the vote rows and writes them
-- back, mapping the result onto an outcome — with zero vote rows that is 0/0/0 -> DEFERRED.
-- Its only caller is recordVote(). So one member casting one vote on any of these 20
-- resolutions would have rewritten a recorded 5/0/0 APPROVED board decision to 1/0/0, and a
-- RECUSED or ABSTAIN vote would have produced 0/0/0 DEFERRED — silently replacing an approved
-- governance outcome with an undecided one. Nothing warned, and the audit line would have
-- reported the new tally as if it were the true one.
--
-- The service half of this migration therefore REFUSES a live vote on an asserted resolution
-- (409) rather than converting it. Mixing a recorded tally with counted votes has no correct
-- arithmetic: the asserted figures include members who have no vote row and never will.
--
-- ---------------------------------------------------------------------
-- LEGACY DISPOSITION: labelled, never flipped.
--
-- The 20 rows keep their numbers and their APPROVED outcome. They are stamped
-- ASSERTED_LEGACY with tally_stamped_at = created_at and NO attribution, because there is
-- none to record — inventing one would be worse than admitting the gap. V315 (vote
-- provenance), V316 (CAB approvals) and V319 (quorum) all take this stance: a new control
-- must not retroactively invalidate approved governance history to make itself look clean.
--
-- Idempotent (column/constraint existence checks + a targeted backfill). Applied twice to dev.
-- =====================================================================

SET NOCOUNT ON;

-- 1. Provenance columns -------------------------------------------------------------------
IF COL_LENGTH('admin.committee_resolutions', 'tally_source') IS NULL
    ALTER TABLE admin.committee_resolutions ADD tally_source NVARCHAR(20) NULL;

IF COL_LENGTH('admin.committee_resolutions', 'tally_recorded_by_user_id') IS NULL
    ALTER TABLE admin.committee_resolutions ADD tally_recorded_by_user_id BIGINT NULL;

IF COL_LENGTH('admin.committee_resolutions', 'tally_recorded_by_name') IS NULL
    ALTER TABLE admin.committee_resolutions ADD tally_recorded_by_name NVARCHAR(255) NULL;

IF COL_LENGTH('admin.committee_resolutions', 'tally_stamped_at') IS NULL
    ALTER TABLE admin.committee_resolutions ADD tally_stamped_at DATETIME2 NULL;
GO

-- 2. Label what is already there ----------------------------------------------------------
-- A non-zero tally with no vote rows can only have been asserted; a tally with vote rows was
-- computed. 0/0/0 with no votes has nothing asserted yet, so it is COMPUTED (awaiting votes).
UPDATE r
   SET r.tally_source = 'ASSERTED_LEGACY',
       r.tally_stamped_at = r.created_at
  FROM admin.committee_resolutions r
 WHERE r.tally_source IS NULL
   AND (ISNULL(r.vote_for, 0) + ISNULL(r.vote_against, 0) + ISNULL(r.vote_abstain, 0)) > 0
   AND NOT EXISTS (SELECT 1 FROM admin.committee_resolution_vote v WHERE v.resolution_id = r.id);

UPDATE r
   SET r.tally_source = 'COMPUTED',
       r.tally_stamped_at = ISNULL(r.tally_stamped_at, r.created_at)
  FROM admin.committee_resolutions r
 WHERE r.tally_source IS NULL;
GO

-- 3. Constrain the vocabulary -------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_resolution_tally_source')
    ALTER TABLE admin.committee_resolutions
        ADD CONSTRAINT ck_resolution_tally_source
        CHECK (tally_source IS NULL
               OR tally_source IN ('COMPUTED', 'ASSERTED', 'ASSERTED_LEGACY'));
GO

-- 4. Verification -------------------------------------------------------------------------
-- Every row must be labelled, and no asserted row may have lost its numbers.
DECLARE @unlabelled INT = (SELECT COUNT(*) FROM admin.committee_resolutions WHERE tally_source IS NULL);
IF @unlabelled > 0
    THROW 50322, 'V322: resolutions remain without a tally_source — the backfill did not cover every row.', 1;

DECLARE @flipped INT = (
    SELECT COUNT(*) FROM admin.committee_resolutions
     WHERE tally_source = 'ASSERTED_LEGACY'
       AND (ISNULL(vote_for, 0) + ISNULL(vote_against, 0) + ISNULL(vote_abstain, 0)) = 0
);
IF @flipped > 0
    THROW 50323, 'V322: an ASSERTED_LEGACY resolution has a zero tally — legacy rows must be labelled, never flipped.', 1;

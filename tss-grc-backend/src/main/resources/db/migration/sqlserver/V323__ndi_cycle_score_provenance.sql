-- =====================================================================
-- V323 — F2-46: the NDI cycle compliance score reaches the regulator with nothing computing it.
-- ---------------------------------------------------------------------
-- THE MEASUREMENT.
--
-- ndi.assessment_cycle.compliance_score has NO setter anywhere in the codebase. Not a stale one,
-- not a broken one — the column is written only by whatever arrives in a request body, because
-- NdiAssessmentCycleService.create() calls repo.save(req) on the bound entity. (update() uses a
-- field whitelist and never copies it, so create is the only way in.)
--
-- Four consumers read it:
--
--     NdiDashboardService            averages it across cycles for the executive tile
--     NdiGovernanceDomainService     rolls it into the governance-domain view
--     NdiReportService               emits it as a report row
--     NdiSubmissionPackageService    puts it in the NDI SUBMISSION PACKAGE
--
-- The last one is the artefact that goes to the regulator.
--
-- And the number does not agree with the platform's own data. Cycle 2 asserts 68.00. Its seven
-- ndi.compliance_assessment children (100, 55, 60, 50, 95, 65, 40) average 66.43. So the figure
-- submitted upward differs from the figure derivable from the assessment it claims to summarise, and
-- nothing anywhere reconciles the two. Cycle 1 has NULL against seven unscored children, which is
-- consistent.
--
-- ⚠ One qualification, because it limits what this migration can claim. Those child values do NOT
-- match scoreFor()'s vocabulary, which yields only 100 / 50 / 0 — the seeded rows never passed
-- through applyRules(), so they are asserted numbers too. The derivation added alongside this
-- migration is therefore correct going forward (a row saved through the service gets scoreFor's
-- value and the roll-up follows) while today's inputs are themselves seeded. What is being fixed is
-- that the roll-up had NO derivation and disagreed with its own children — not that the children
-- were authoritative.
--
-- This is F2-40's shape (a scalar with no provenance feeding a governance artefact) with one
-- important difference: here a correct derivation already exists and its inputs are authoritative.
-- So the score becomes COMPUTED from the children rather than merely labelled.
--
-- ---------------------------------------------------------------------
-- LEGACY DISPOSITION: labelled, not flipped — the same stance as V315, V316, V319 and V322.
--
-- The asserted 68.00 is kept and marked ASSERTED_LEGACY. Overwriting it here would silently
-- restate a number that may already have been reported, and a migration is the wrong place to
-- change a governance figure. The first recompute triggered by an assessment score change will
-- replace it and stamp COMPUTED, which is a deliberate act with an audit trail behind it.
--
-- Idempotent. Applied twice to dev.
-- =====================================================================

SET NOCOUNT ON;

IF COL_LENGTH('ndi.assessment_cycle', 'score_source') IS NULL
    ALTER TABLE ndi.assessment_cycle ADD score_source NVARCHAR(20) NULL;

IF COL_LENGTH('ndi.assessment_cycle', 'score_computed_at') IS NULL
    ALTER TABLE ndi.assessment_cycle ADD score_computed_at DATETIME2 NULL;
GO

-- A score that is present but that nothing computed can only have been asserted.
UPDATE ndi.assessment_cycle
   SET score_source = 'ASSERTED_LEGACY'
 WHERE score_source IS NULL
   AND compliance_score IS NOT NULL;

-- No score yet: nothing has been asserted and nothing computed. Left NULL rather than labelled,
-- so "never scored" stays distinguishable from "computed as zero".
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ndi_cycle_score_source')
    ALTER TABLE ndi.assessment_cycle
        ADD CONSTRAINT ck_ndi_cycle_score_source
        CHECK (score_source IS NULL OR score_source IN ('COMPUTED', 'ASSERTED_LEGACY'));
GO

-- Verification: every scored row must be labelled, and no asserted value may have been altered.
DECLARE @unlabelled INT = (
    SELECT COUNT(*) FROM ndi.assessment_cycle
     WHERE compliance_score IS NOT NULL AND score_source IS NULL);
IF @unlabelled > 0
    THROW 50323, 'V323: a scored NDI cycle has no score_source — the backfill missed a row.', 1;

DECLARE @blanked INT = (
    SELECT COUNT(*) FROM ndi.assessment_cycle
     WHERE score_source = 'ASSERTED_LEGACY' AND compliance_score IS NULL);
IF @blanked > 0
    THROW 50324, 'V323: an ASSERTED_LEGACY cycle lost its score — legacy values are labelled, never cleared.', 1;

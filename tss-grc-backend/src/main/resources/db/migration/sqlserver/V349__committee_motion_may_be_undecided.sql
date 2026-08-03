-- V349 — S7a / F2-89 + F2-100: a committee motion must be raisable BEFORE it is decided,
--                              and the decision must be announced when it becomes binding.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- F2-89 — THERE IS NO WAY TO RAISE A MOTION UNDECIDED, AND EVERY PLACEHOLDER FIRES A RULE
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `outcome` is NOT NULL and CK_resolution_outcome permits four values, all of which are
-- DECISIONS: APPROVED | REJECTED | DEFERRED | WITHDRAWN. So to vote on a motion in-platform
-- you must first record a verdict for it. Worse, `create()` publishes
-- COMMITTEE_RESOLUTION_<outcome> immediately, and every placeholder a caller could pick is
-- wired to a rule that is ENABLED in this database:
--
--     APPROVED  → AR-146  regulator disclosure clock (EMAIL+SMS+INBOX, 24 h SLA) and
--                 AR-147  "file in the formal board-resolutions register within 48 h"
--     DEFERRED  → AR-152  "re-table at the next meeting"
--
-- So raising a motion either starts a statutory disclosure clock or asks the secretary to
-- re-table something that was never tabled. Measured 2026-08-03 in dev:
--
--     AR-152  enabled, fire_count = 38, last 2026-08-03 09:56:41
--     resolutions with outcome = 'DEFERRED' .................... 0
--
-- Thirty-eight fires, and not one deferred resolution exists — every one of those fires was
-- on a transient placeholder that later became APPROVED. The platform's own golden path is
-- the proof: CommitteeGoldenPathIT posts `outcome: "DEFERRED"` with the comment
-- "F2-89 — there is no undecided state", so the reference walkthrough for correct committee
-- governance cannot run without emitting a false deferral. And the only UI that creates a
-- resolution (CommitteesPage) DEFAULTS the field to APPROVED.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- F2-100 — AND THE DECISION ITSELF IS ANNOUNCED TO NOBODY
-- ════════════════════════════════════════════════════════════════════════════════════════
-- Found while measuring F2-89. `CommitteeResolutionService` has exactly ONE publishEvent, in
-- create(). recomputeTally() — the method that turns counted votes into an outcome — and
-- approveMinutes() — the act that makes a decision binding — publish nothing. So a motion
-- decided by real in-platform voting emits no outcome event at all, and AR-146/AR-147 fire
-- only for a tally typed in from paper minutes. The provenance-bearing path (F2-40's
-- COMPUTED) is the one automation cannot see.
--
-- Proven from the ledger rather than argued:
--
--     resolutions 69 + 70   created 2026-08-03 09:56:40, voted to APPROVED (2/0/0 and 1/0/0)
--     AR-147                last_fired_at 2026-07-26 14:19:20  ← a week EARLIER
--
-- Both reached APPROVED through counted votes; the APPROVED rule provably never fired.
-- Today's behaviour, in one sentence: raising a motion notifies the secretary to re-table it,
-- and deciding the motion notifies nobody. Exactly inverted.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHAT THIS MIGRATION ADDS
-- ════════════════════════════════════════════════════════════════════════════════════════
-- 1. `PROPOSED` — a motion raised and not yet decided. The fifth value is deliberately NOT a
--    decision, so `isDecided()` is a vocabulary test rather than a list of exceptions.
--
-- 2. `decision_published_at` — when the binding decision was announced. This is the
--    idempotency fact for the three publish sites (create, tally, minutes approval), and it
--    is a COLUMN rather than the audit-marker scan CommitteeScheduledSweep uses because
--    F2-90 had just finished showing that the ledger was not being written for meeting
--    actions at all: an idempotency check that depends on a log which may not have been
--    written will re-announce. A column cannot be missing.
--
-- ⚠ NON-RETROSPECTIVE, AND THE BACKFILL IS THE POINT. Existing decided rows are stamped
--   `decision_published_at = created_at`, which reads as "the platform will not announce
--   this as a new decision". For the 4 rows AR-147 already fired on that is literally true;
--   for the rest it is a SUPPRESSION stamp, and calling it anything else would be dressing a
--   suppression up as a history. Without it, the first thing this fix would do is announce 22
--   historical board decisions as new — which is F2-92's shape and the reason V348 shipped
--   opt-in. No outcome value changes, and nothing that was decided becomes undecided.
--
-- REVERSE RECIPE: ALTER TABLE admin.committee_resolutions DROP COLUMN decision_published_at;
--                 ALTER TABLE admin.committee_resolutions DROP CONSTRAINT CK_resolution_outcome;
--                 ALTER TABLE admin.committee_resolutions ADD CONSTRAINT CK_resolution_outcome
--                     CHECK (outcome IN (N'APPROVED', N'REJECTED', N'DEFERRED', N'WITHDRAWN'));
--                 -- safe only while no row is PROPOSED; check first.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. PROPOSED joins the outcome vocabulary ──────────────────────────────────────────
-- Drop-and-recreate: SQL Server cannot widen a CHECK in place. The constraint name is the
-- V154 one, so the recreate must reuse it or CommitteeSqlGuards and the IT's Java↔CK
-- ratchet would stop finding it.
IF EXISTS (SELECT 1 FROM sys.check_constraints cc
           JOIN sys.tables t  ON t.object_id = cc.parent_object_id
           JOIN sys.schemas s ON s.schema_id = t.schema_id
           WHERE s.name = 'admin' AND t.name = 'committee_resolutions'
             AND cc.name = 'CK_resolution_outcome'
             AND cc.definition NOT LIKE '%PROPOSED%')
BEGIN
    ALTER TABLE admin.committee_resolutions DROP CONSTRAINT CK_resolution_outcome;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
               JOIN sys.tables t  ON t.object_id = cc.parent_object_id
               JOIN sys.schemas s ON s.schema_id = t.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_resolutions'
                 AND cc.name = 'CK_resolution_outcome')
BEGIN
    ALTER TABLE admin.committee_resolutions
        ADD CONSTRAINT CK_resolution_outcome CHECK (outcome IN (
            N'PROPOSED', N'APPROVED', N'REJECTED', N'DEFERRED', N'WITHDRAWN'));
END
GO

-- ── 2. When the binding decision was announced ────────────────────────────────────────
IF COL_LENGTH('admin.committee_resolutions', 'decision_published_at') IS NULL
BEGIN
    ALTER TABLE admin.committee_resolutions ADD decision_published_at DATETIME2(3) NULL;
END
GO

-- ── 3. Suppress re-announcement of decisions that already exist ───────────────────────
-- Idempotent by construction: the second run finds nothing NULL left to stamp among rows
-- that pre-date this migration. A row created AFTER it is PROPOSED (or is stamped by the
-- service when it publishes), so this cannot suppress a future decision — the guard is
-- `outcome <> 'PROPOSED'`, not a date.
UPDATE admin.committee_resolutions
   SET decision_published_at = created_at
 WHERE decision_published_at IS NULL
   AND outcome <> N'PROPOSED';
GO

-- ── Self-checks — the property, not the statement ──────────────────────────────────────
-- THROW takes a literal or a variable, never a concatenation (cost three attempts on
-- V346/V347; it is not worth relearning).

DECLARE @ckMissing NVARCHAR(400) =
    N'V349 failed: CK_resolution_outcome does not permit PROPOSED, so a motion still cannot '
  + N'be raised undecided and F2-89 is not fixed.';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
               JOIN sys.tables t  ON t.object_id = cc.parent_object_id
               JOIN sys.schemas s ON s.schema_id = t.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_resolutions'
                 AND cc.name = 'CK_resolution_outcome'
                 AND cc.definition LIKE '%PROPOSED%')
    THROW 50000, @ckMissing, 1;
GO

DECLARE @colMissing NVARCHAR(400) =
    N'V349 failed: admin.committee_resolutions.decision_published_at was not created — the '
  + N'three publish sites would have no idempotency fact and could re-announce a decision.';
IF COL_LENGTH('admin.committee_resolutions', 'decision_published_at') IS NULL
    THROW 50000, @colMissing, 1;
GO

-- The four decided values must still be accepted. A recreate that fat-fingered one of them
-- would make every existing row unupdatable, and nothing else here would notice.
DECLARE @vocabLost NVARCHAR(400) =
    N'V349 failed: the recreated CK_resolution_outcome has lost one of the four decided '
  + N'values. Every existing resolution row would become unupdatable.';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
               WHERE cc.name = 'CK_resolution_outcome'
                 AND cc.definition LIKE '%APPROVED%' AND cc.definition LIKE '%REJECTED%'
                 AND cc.definition LIKE '%DEFERRED%' AND cc.definition LIKE '%WITHDRAWN%')
    THROW 50000, @vocabLost, 1;
GO

-- Nothing decided may have been left unstamped, or the fix's first act is to announce a
-- historical board decision as new.
DECLARE @unstamped INT;
DECLARE @unstampedMsg NVARCHAR(400) =
    N'V349 failed: a decided resolution still has decision_published_at NULL. The first '
  + N'publish pass would announce an existing board decision as if it had just been taken.';
SELECT @unstamped = COUNT(*) FROM admin.committee_resolutions
 WHERE decision_published_at IS NULL AND outcome <> N'PROPOSED';
IF @unstamped > 0 THROW 50000, @unstampedMsg, 1;
GO

-- And no outcome may have moved. Recorded governance facts are evidence (V348's stance).
DECLARE @undecided INT;
DECLARE @undecidedMsg NVARCHAR(400) =
    N'V349 failed: a resolution that existed before this migration is now PROPOSED. Adding '
  + N'an undecided state must not un-decide anything that a committee already decided.';
SELECT @undecided = COUNT(*) FROM admin.committee_resolutions
 WHERE outcome = N'PROPOSED' AND decision_published_at IS NOT NULL;
IF @undecided > 0 THROW 50000, @undecidedMsg, 1;
GO

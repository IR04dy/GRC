-- V348 — S6c: per-committee quorum policy may require the presiding officer to be present.
--
-- WHAT S6 ASKED FOR AND WHAT WAS MISSING. The S6 row lists "quorum policy (per-committee
-- `quorum_pct` semantics + required-roles-present)". Both halves shipped: `quorum_pct` is live
-- (`quorumPctOf` → `requiredFor` → `quorumRequired`, F2-28/V319), and required-roles-present shipped
-- as S6b-ii's 409. What the engine has never had is any notion of WHO must be in the room:
-- `computeQuorum` is purely numeric, so a CAB is quorate on headcount alone and can authorise a
-- change with nobody presiding.
--
-- ⚠ WHY THIS IS OPT-IN, DEFAULT 0 — AND WHY THAT IS NOT TIMIDITY. Measured in both databases before
-- writing this file:
--
--     dev   19 committees · 7 with a (non-vice) active chair seat · 12 WITHOUT one
--           18 meetings recorded quorate · chair present in 1
--     prod  17 committees · 5 with a chair seat · 12 without
--           17 meetings recorded quorate · chair present in 0
--
-- So a chair requirement switched on globally would refuse 17 of 18 quorate meetings in dev and
-- 17 of 17 in production, and 12 committees in each could never satisfy it at all — a control whose
-- first act refuses everything, with no action available to the user. That is F2-92's shape, and
-- S6b-ii spent an entire slice establishing that it is not acceptable. DEFAULT 0 means this
-- migration changes the behaviour of exactly nothing on deploy; a tenant turns it on per committee
-- when that committee's charter says so.
--
-- ⚠ AND IT IS NOT RETROSPECTIVE. Stored `quorum_met` values are left untouched. Recomputing them
-- under a policy that did not exist when the meeting sat would rewrite a recorded governance fact —
-- the same mistake F2-40 refused when it declined to merge an asserted tally into counted votes.
-- The policy binds the next attendance sheet, not the last one.
--
-- REVERSE RECIPE: ALTER TABLE admin.committees DROP CONSTRAINT df_committees_quorum_chair;
--                 ALTER TABLE admin.committees DROP COLUMN quorum_requires_chair;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── The policy flag ───────────────────────────────────────────────────────────────────
-- Named constraint so the reverse recipe above can drop it; an unnamed DEFAULT gets a
-- generated name that differs between databases and cannot be scripted.
IF COL_LENGTH('admin.committees', 'quorum_requires_chair') IS NULL
BEGIN
    ALTER TABLE admin.committees
        ADD quorum_requires_chair BIT NOT NULL
            CONSTRAINT df_committees_quorum_chair DEFAULT (0);
END
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a
-- concatenation (hit three times in one session on V346/V347).
IF COL_LENGTH('admin.committees', 'quorum_requires_chair') IS NULL
    THROW 50000, N'V348 failed: admin.committees.quorum_requires_chair was not created.', 1;
GO

-- Nothing may be opted in by this migration. If a later migration or a tenant sets the flag, that is
-- their decision; V348 asserting it here would be the migration changing behaviour behind the
-- opt-in it just introduced.
DECLARE @optedIn INT;
DECLARE @optedInMsg NVARCHAR(400) =
    N'V348 failed: quorum_requires_chair is already 1 on one or more committees. This migration '
  + N'must land inert — enabling a chair requirement retrospectively would unseat meetings that '
  + N'were quorate under the policy in force when they sat.';
SELECT @optedIn = COUNT(*) FROM admin.committees WHERE quorum_requires_chair = 1;
IF @optedIn > 0 THROW 50000, @optedInMsg, 1;
GO

-- And no stored quorum fact may have moved. Recorded governance facts are evidence.
DECLARE @quorate INT;
DECLARE @quorateMsg NVARCHAR(400) =
    N'V348 failed: no meeting is recorded quorate any more. This migration must not touch '
  + N'committee_meetings.quorum_met — the policy binds the next attendance sheet, not the last one.';
SELECT @quorate = COUNT(*) FROM admin.committee_meetings WHERE quorum_met = 1;
-- Measured 18 in dev / 17 in prod on 2026-08-03. Any environment with meetings must still have some.
IF @quorate = 0 AND EXISTS (SELECT 1 FROM admin.committee_meetings)
    THROW 50000, @quorateMsg, 1;
GO

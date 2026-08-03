-- V351 — S7c / X-2: a CAB that defers, or asks for more information, can finally say so.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `CommitteeResolution.outcome` has permitted DEFERRED since V154 — a committee CAN defer a
-- motion, and `AR-152 (COMMITTEE_RESOLUTION_DEFERRED → re-table at the next meeting)` is enabled
-- and tells the secretary about it. But the CHANGE had nowhere to go:
--
--     NORMAL_TRANSITIONS: CAB_PENDING -> { CAB_APPROVED, CAB_REJECTED }
--
-- and `assertDecisionApproves` throws unless the linked resolution is APPROVED. So a change the
-- committee considered and deferred stays at `CAB_PENDING`, and — because `cab_decision_at` is
-- stamped only on the APPROVED / REJECTED / EMERGENCY_APPROVED branches — it is **byte-identical
-- to a change the committee has never seen**. The requester watches `CAB_PENDING` forever with no
-- record that anyone looked at it.
--
-- That is F2-89's shape one level up: the committee could reach an outcome the change could not
-- represent. S7a fixed it for the motion; this fixes it for the change.
--
-- ⚠ WHY 14 → 16 AND NOT 14 → 17. X-2 originally reserved three values. `PENDING_RATIFICATION`
-- **lost its producer** when A17-D3 shipped ratification as columns ORTHOGONAL to `status`: an
-- emergency change is `IMPLEMENTED` *and* awaiting ratification simultaneously, so no lifecycle
-- status ever takes that value. Adding it would create a state nothing can reach — the same smell
-- as an empty package — so it is deliberately not added here. Re-measured 2026-08-03: 14 values in
-- dev and 14 in prod, identical.
--
-- ⚠ SAME MIGRATION AS THE CODE, which is this file's own convention for a status with a new
-- producer. The transitions, the `DECISION_TARGETS` classification and the write-back ship in the
-- same commit; shipping the CHECK ahead of them would leave two values nothing can reach, and
-- shipping the code ahead of the CHECK would 500 on the first deferral.
--
-- NOTHING CHANGES ON DEPLOY. Widening a CHECK cannot move a row: measured 3 CAB_PENDING in dev and
-- 2 in prod at the time of writing, and no row is rewritten here.
--
-- REVERSE RECIPE: ALTER TABLE change_mgmt.change_requests DROP CONSTRAINT ck_change_status;
--                 ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_status
--                     CHECK (status IN (N'DRAFT', N'SUBMITTED', N'IMPACT_ASSESSED', N'CAB_PENDING',
--                         N'CAB_APPROVED', N'CAB_REJECTED', N'EMERGENCY_APPROVED', N'SCHEDULED',
--                         N'IMPLEMENTING', N'IMPLEMENTED', N'ROLLED_BACK', N'POST_REVIEW',
--                         N'CLOSED', N'CANCELLED'));
--                 -- safe only while no row holds either new value; check first.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── Widen the status vocabulary ────────────────────────────────────────────────────────
-- Drop-and-recreate under the V300 constraint name: SQL Server cannot widen a CHECK in place, and
-- reusing the name is what keeps `SchemaContractRatchetTest` and the Java↔CK ratchet finding it.
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_change_status' AND definition NOT LIKE '%CAB_DEFERRED%')
BEGIN
    ALTER TABLE change_mgmt.change_requests DROP CONSTRAINT ck_change_status;
END
GO

-- The two new values are CAB_DEFERRED and MORE_INFO_REQUIRED: the committee considered the change
-- and did not authorise it, yet. Deliberately NO comment inside the value list below — a comma-split
-- parser reading this constraint (CabDecisionOutcomesTest does exactly that, and the Java/CK ratchet
-- pattern F2-91 established depends on it) breaks on the first bracket inside a comment.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_status')
BEGIN
    ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_status
        CHECK (status IN (
            N'DRAFT', N'SUBMITTED', N'IMPACT_ASSESSED', N'CAB_PENDING',
            N'CAB_APPROVED', N'CAB_REJECTED', N'CAB_DEFERRED', N'MORE_INFO_REQUIRED',
            N'EMERGENCY_APPROVED', N'SCHEDULED', N'IMPLEMENTING', N'IMPLEMENTED',
            N'ROLLED_BACK', N'POST_REVIEW', N'CLOSED', N'CANCELLED'));
END
GO

-- ── Self-checks — the property, not the statement ──────────────────────────────────────
-- THROW takes a literal or a variable, never a concatenation.

DECLARE @newMissing NVARCHAR(400) =
    N'V351 failed: ck_change_status does not permit CAB_DEFERRED and MORE_INFO_REQUIRED, so a '
  + N'committee that defers a change still has nowhere to record it and the change stays '
  + N'indistinguishable from one nobody has looked at.';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_change_status'
                 AND definition LIKE '%CAB_DEFERRED%' AND definition LIKE '%MORE_INFO_REQUIRED%')
    THROW 50000, @newMissing, 1;
GO

-- Every one of the 14 pre-existing values must still be accepted. A recreate that fat-fingered one
-- would make a whole class of change rows unupdatable, and nothing else here would notice.
DECLARE @lost NVARCHAR(400) =
    N'V351 failed: the recreated ck_change_status has lost one of the 14 original values. Change '
  + N'rows in that state would become unupdatable.';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_change_status'
                 AND definition LIKE '%DRAFT%'      AND definition LIKE '%SUBMITTED%'
                 AND definition LIKE '%IMPACT_ASSESSED%' AND definition LIKE '%CAB_PENDING%'
                 AND definition LIKE '%CAB_APPROVED%'    AND definition LIKE '%CAB_REJECTED%'
                 AND definition LIKE '%EMERGENCY_APPROVED%' AND definition LIKE '%SCHEDULED%'
                 AND definition LIKE '%IMPLEMENTING%'   AND definition LIKE '%IMPLEMENTED%'
                 AND definition LIKE '%ROLLED_BACK%'    AND definition LIKE '%POST_REVIEW%'
                 AND definition LIKE '%CLOSED%'         AND definition LIKE '%CANCELLED%')
    THROW 50000, @lost, 1;
GO

-- PENDING_RATIFICATION must NOT be here. A17-D3 models ratification orthogonally to status, so the
-- value has no producer; adding it would create an unreachable state. This check is what stops a
-- later reader "completing X-2" from the plan's original 14 → 17 wording.
DECLARE @ghost NVARCHAR(400) =
    N'V351 failed: ck_change_status permits PENDING_RATIFICATION. A17-D3 ships ratification as '
  + N'columns orthogonal to status — an emergency change is IMPLEMENTED *and* awaiting '
  + N'ratification — so no lifecycle status can ever take that value. Adding it creates a state '
  + N'nothing can reach.';
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_change_status' AND definition LIKE '%PENDING_RATIFICATION%')
    THROW 50000, @ghost, 1;
GO

-- And no change may have moved. Widening a CHECK cannot rewrite a row; if the count of changes in
-- a decided state has collapsed, something else in this file did more than it should.
DECLARE @decided INT;
DECLARE @decidedMsg NVARCHAR(400) =
    N'V351 failed: no change_requests row is in a post-CAB state any more. This migration widens '
  + N'a CHECK constraint and must not touch a row.';
SELECT @decided = COUNT(*) FROM change_mgmt.change_requests
 WHERE status IN (N'CAB_APPROVED', N'SCHEDULED', N'IMPLEMENTING', N'IMPLEMENTED',
                  N'POST_REVIEW', N'CLOSED', N'ROLLED_BACK');
IF @decided = 0 AND EXISTS (SELECT 1 FROM change_mgmt.change_requests)
    THROW 50000, @decidedMsg, 1;
GO

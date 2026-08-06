-- =====================================================================
-- V316 (DF-4 / finding F2-24) — one CAB vote per approver per change.
-- ---------------------------------------------------------------------
-- DF-3's twin, in the change-approval path. ChangeManagementController#recordApproval
-- bound the ChangeApproval JPA ENTITY straight from the request body and the service
-- trusted body.approverName, so:
--   * the CM-C2 segregation-of-duties gate compared the CLAIMED name against
--     requested_by_name / change_owner_name — a requester approved their own change
--     simply by submitting a different name;
--   * `approver_id` was client-settable, so votes were attributable to anyone;
--   * nothing enforced one vote per approver, so a single caller could submit several
--     names and manufacture a CAB majority;
--   * audit.log recorded the claimed name, corroborating the forgery.
-- The source comment conceded it: "(Name-based check; principal-id capture is a
-- follow-up.)" This is that follow-up.
--
-- The code fix derives the approver from Authentication and evaluates SoD on the
-- resolved user id. This migration adds the database-level guarantee that the code
-- fix assumes: an approver cannot be counted twice on the same change.
--
-- FILTERED index, deliberately. 3 of the 31 existing dev rows carry
-- approver_id NULL — pre-fix rows whose actor was never captured ("provenance
-- unknown"). They are NOT back-filled with a guess: inventing an approver for a
-- historical change decision is the defect, not the fix. The filter lets those rows
-- stay while every new row is constrained.
--
-- Verified before writing this: SELECT change_request_id, approver_id, COUNT(*) ...
-- GROUP BY ... HAVING COUNT(*) > 1 returns NO rows, so the index creates cleanly. If
-- it ever fails on another environment, that is a real duplicate to investigate —
-- report it, do not delete rows to force the index through.
--
-- Idempotent; safe to re-run. Applied to dev twice via pyodbc (Flyway off in dev).
-- Reverse recipe:
--   DROP INDEX ux_change_approval_one_vote_per_approver ON change_mgmt.change_approvals;
-- =====================================================================

IF OBJECT_ID('change_mgmt.change_approvals') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_change_approval_one_vote_per_approver' AND object_id = OBJECT_ID('change_mgmt.change_approvals'))
BEGIN
    CREATE UNIQUE INDEX ux_change_approval_one_vote_per_approver
        ON change_mgmt.change_approvals (change_request_id, approver_id)
        WHERE approver_id IS NOT NULL;
END
GO

-- Reporting/diagnostic helper: how many pre-fix rows have no captured actor. Surfaced
-- rather than silently fixed, so an assessor can see the boundary between
-- provenance-unknown history and provenance-enforced records.
IF OBJECT_ID('change_mgmt.change_approvals') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_change_approval_approver' AND object_id = OBJECT_ID('change_mgmt.change_approvals'))
BEGIN
    CREATE INDEX ix_change_approval_approver
        ON change_mgmt.change_approvals (approver_id)
        INCLUDE (change_request_id, decision, approver_name);
END
GO

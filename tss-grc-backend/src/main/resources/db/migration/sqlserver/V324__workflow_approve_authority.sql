-- =====================================================================
-- V324 — F2-13b: approving a workflow request needed no approval authority.
-- ---------------------------------------------------------------------
-- MEASURED, on the live grant data rather than from the report's summary:
--
--   WORKFLOW:WRITE    held by 55 of 57 roles
--   WORKFLOW:APPROVE  held by  3  (GRC_ADMINISTRATOR, ORG_ADMIN, RISK_MANAGER)
--
-- and `POST /api/v1/workflows/requests/{id}/approve` — plus `/reject` — were guarded by
-- **WRITE**. So 55 of 57 roles could approve any workflow request: VIEWER, REGULATOR_VIEWER,
-- EXTERNAL_AUDITOR, SUPPLIER_USER and all eight `*_INGEST` machine accounts among them. The
-- workflow engine is adopted by 14 modules, so this was the permissive path most module approvals
-- route through.
--
-- The engine's own siblings already do it correctly, which is what makes this an inconsistency
-- rather than a missing feature: `ApprovalRequestController#transition`, both decision endpoints of
-- `ApprovalChainController`, and `WorkflowSubmissionController` all guard the analogous act with
-- WORKFLOW:APPROVE. Only the request lifecycle used WRITE. Same shape as CM-C2 (change approval by
-- name) and SCR-003 (CHANGE:WRITE reaching approval states).
--
-- WHY A GRANT MIGRATION IS PART OF THE FIX
-- Lifting the guard alone would have left approval to 3 roles and broken the platform's documented
-- four-eyes flows on the first attempt: the standard second approver is a CISO, and CISO holds no
-- WORKFLOW:APPROVE. F2-13a's third transferable result was exactly this — enforcing a permission is
-- also a re-grant decision — and F2-43 is what happens when the grant is missed.
--
-- WHO GETS IT, DERIVED RATHER THAN CHOSEN — AND THE SECOND CONDITION THE FIRST DRAFT MISSED
-- The rule is TWO predicates, not one:
--
--   (1) the role already holds *some* module's APPROVE permission — the platform's own statement
--       about who is an approver persona, so it is data rather than my judgement; and
--   (2) the role already holds WORKFLOW:WRITE — it already participates in this engine.
--
-- Predicate (2) was NOT in the first draft, and leaving it out produced one incoherent grant that
-- the platform's own measurement caught: regenerating `Appendix/GRANT_TIGHTNESS.md` after applying
-- the migration added a new section-B4 row — **CHANGE_MANAGER · WORKFLOW · APPROVE · READ not
-- held**. CHANGE_MANAGER (created by V317 for change authority) holds *no* WORKFLOW permission at
-- all, so it had gained authority to decide requests it cannot list, open or read. B4's own framing
-- is that write-without-read is correct for a *machine*; for a human persona it is a 403 halfway
-- through a flow — the click is offered and the screen behind it will not load. The narrower rule
-- is also strictly safer: WORKFLOW:APPROVE holders are now a subset of WORKFLOW:WRITE holders, so
-- no role gains a foothold in a module it was not already in.
--
--   17 roles qualify -> BCM_MANAGER, BOARD_SECRETARY, CFO, CISO, COMPLIANCE_OFFICER,
--   CYBER_MANAGER, DPO, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, LEGAL_COUNSEL, ORG_ADMIN,
--   PLATFORM_ADMIN, PRIVACY_MANAGER, RISK_MANAGER, SERVICE_OWNER, SUPPLIER_MANAGER,
--   SUSTAINABILITY_OFFICER  (3 already held it; 14 are new)
--
--   All 17 hold WORKFLOW READ + WRITE + APPROVE — verified by verification 3 below, so the
--   half-grant shape cannot come back through a later edit of this file.
--
--   0 read-only, external or machine personas qualify — verified, not assumed: no VIEWER,
--   REGULATOR_VIEWER, EXTERNAL_AUDITOR, SUPPLIER_USER or *_INGEST role holds any APPROVE anywhere.
--   That is why this rule needs no hand-maintained exclusion list.
--
-- Net effect: roles able to approve a workflow request goes from 55 to 17.
--
-- ⚠ FOLLOW-UP, deliberately NOT done here: when Wave 3's CE-10 makes change the 15th module to
-- adopt the workflow engine, CHANGE_MANAGER will need WORKFLOW READ + WRITE + APPROVE *together*.
-- Granting the APPROVE half now, ahead of the adoption that gives it meaning, is the F2-02
-- nav-to-nowhere anti-pattern expressed as a grant.
--
-- WORKFLOW:WRITE IS DELIBERATELY LEFT ALONE. After the split it means "participate" — create,
-- send, respond, submit, resubmit. A SUPPLIER_USER responding to a vendor attestation and an
-- ingest account raising a request both need exactly that, so revoking it would break real flows
-- to tidy a report row. The 20-grant count in the report's section B3 also double-counts: it lists
-- the machines' CYBER:WRITE as wrong while its own section B4 defends the same grants as correct
-- least privilege for a machine. Both cannot be true; B4 is right.
--
-- Idempotent (NOT EXISTS per row); safe to re-run. Applied twice to the dev DB via pyodbc.
-- =====================================================================

SET NOCOUNT ON;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, wf.id
FROM iam.roles r
JOIN iam.permissions wf
  ON wf.module COLLATE DATABASE_DEFAULT = 'WORKFLOW' COLLATE DATABASE_DEFAULT
 AND wf.action COLLATE DATABASE_DEFAULT = 'APPROVE'  COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  -- predicate (1): already an approver somewhere in the platform
  AND EXISTS (
      SELECT 1
      FROM iam.role_permissions rp
      JOIN iam.permissions p ON p.id = rp.permission_id
      WHERE rp.role_id = r.id
        AND p.action COLLATE DATABASE_DEFAULT = 'APPROVE' COLLATE DATABASE_DEFAULT
  )
  -- predicate (2): already participates in THIS engine. Without this, CHANGE_MANAGER gained
  -- authority to decide requests it cannot read — see the header.
  AND EXISTS (
      SELECT 1
      FROM iam.role_permissions rp2
      JOIN iam.permissions p2 ON p2.id = rp2.permission_id
      WHERE rp2.role_id = r.id
        AND p2.module COLLATE DATABASE_DEFAULT = 'WORKFLOW' COLLATE DATABASE_DEFAULT
        AND p2.action COLLATE DATABASE_DEFAULT = 'WRITE'    COLLATE DATABASE_DEFAULT
  )
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions x
      WHERE x.role_id = r.id AND x.permission_id = wf.id
  );

-- Verification 1: the four-eyes second approver must be able to approve, or the flows the house
-- rules describe break on the first attempt.
DECLARE @ciso INT = (
    SELECT COUNT(*)
    FROM iam.roles r
    JOIN iam.role_permissions rp ON rp.role_id = r.id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name COLLATE DATABASE_DEFAULT = 'CISO' COLLATE DATABASE_DEFAULT
      AND r.is_deleted = 0
      AND p.module = 'WORKFLOW' AND p.action = 'APPROVE'
);
IF @ciso = 0
    THROW 50324, 'V324: CISO still cannot approve a workflow request — four-eyes flows would 403.', 1;

-- Verification 2: and the personas this exists to exclude must NOT have picked it up.
DECLARE @leaked NVARCHAR(400) = (
    SELECT STRING_AGG(r.role_name, ', ')
    FROM iam.roles r
    JOIN iam.role_permissions rp ON rp.role_id = r.id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.is_deleted = 0
      AND p.module = 'WORKFLOW' AND p.action = 'APPROVE'
      AND (r.role_name LIKE '%INGEST'
        OR r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR', 'SUPPLIER_USER'))
);
IF @leaked IS NOT NULL
    THROW 50324, 'V324: a read-only/external/machine persona holds WORKFLOW:APPROVE.', 1;

-- Verification 3: no holder may be missing WORKFLOW:READ. This is the one the first draft of this
-- migration failed — the report's section B4 found CHANGE_MANAGER holding APPROVE with no READ,
-- i.e. authority to decide a record it cannot open. Asserted here rather than left to the next
-- reader of the report, because a grant migration is exactly where the half-grant gets created.
DECLARE @halfGranted NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ')
    FROM (
        SELECT r.role_name
        FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'WORKFLOW' AND p.action = 'APPROVE'
          AND NOT EXISTS (
              SELECT 1 FROM iam.role_permissions rp3
              JOIN iam.permissions p3 ON p3.id = rp3.permission_id
              WHERE rp3.role_id = r.id
                AND p3.module = 'WORKFLOW' AND p3.action = 'READ'
          )
    ) x
);
IF @halfGranted IS NOT NULL
    THROW 50324, 'V324: a role holds WORKFLOW:APPROVE without WORKFLOW:READ — it could decide a request it cannot read.', 1;

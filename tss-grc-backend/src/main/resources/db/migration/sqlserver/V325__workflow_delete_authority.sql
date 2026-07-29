/* ============================================================================
   V325 — WORKFLOW:DELETE becomes enforceable, and is granted to the roles the
          platform already treats as deleters.

   THE FINDING (section C of Appendix/GRANT_TIGHTNESS.md, the one non-EXPORT row).
   `WORKFLOW:DELETE` was catalogued, held by exactly ONE role (ORG_ADMIN), and
   named by no @PreAuthorize anywhere — the report's words for that class: "the
   role editor offers a checkbox that is pure theatre". Meanwhile three delete
   endpoints exist in the workflow packages and none of them used it:

     ApprovalChainController#deleteTemplate   hasAnyRole(PLATFORM_ADMIN,
                                              COMPLIANCE_OFFICER)          2 roles
     ApprovalRequestController#delete         hasRole(PLATFORM_ADMIN)       1 role
     WorkflowRecipientListController#delete   hasPermission(WORKFLOW,WRITE) 55 roles

   The first two are TIGHTER than this permission would be, so they are left
   alone — moving them here would WIDEN them from 1-2 roles to 12, and a fix that
   loosens a guard is not a fix. Their `hasAnyRole` form is F2-05's conversion
   scope, not this migration's. Only the third changes.

   WHY THE THIRD MATTERS. WORKFLOW:WRITE is held by 55 of 57 roles — measured in
   F2-13b, and deliberately left that wide, because after the approve/write split
   it means *participate* and a SUPPLIER_USER answering a vendor attestation needs
   it. That population includes VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR,
   SUPPLIER_USER and all eight *_INGEST machine accounts. Participating in a
   workflow is not the same act as deleting the shared distribution list that 14
   modules route requests through. The delete is soft (is_deleted = 1) and
   audited, which bounds the damage; it does not make 55 the right number.

   THE GRANT IS DERIVED, NOT CHOSEN. Same rule as V324, one action over: a role
   gets WORKFLOW:DELETE if it (1) already holds SOME module's DELETE — the
   platform has already decided it may destroy things — AND (2) already holds
   WORKFLOW:WRITE, so it participates in THIS engine. Predicate (2) is what stops
   the half-grant V324 created on its first draft: authority over records the role
   cannot read. Measured result 1 -> 12 holders:

     BCM_MANAGER, COMPLIANCE_OFFICER, CYBER_MANAGER, DPO, GRC_ADMINISTRATOR,
     INTERNAL_AUDITOR, ORG_ADMIN, PLATFORM_ADMIN, PRIVACY_MANAGER, RISK_MANAGER,
     SUPPLIER_MANAGER, SUSTAINABILITY_OFFICER

   No read-only, external or machine persona qualifies — verified below rather
   than assumed, which is why this needs no hand-maintained exclusion list.

   Idempotent: NOT EXISTS on the insert; safe to run twice (Flyway is off in dev,
   so it is applied by hand twice per the house convention).
   ============================================================================ */
SET NOCOUNT ON;

DECLARE @before INT = (
    SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module COLLATE DATABASE_DEFAULT = 'WORKFLOW' COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'DELETE'   COLLATE DATABASE_DEFAULT);

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, wf.id
FROM iam.roles r
JOIN iam.permissions wf
  ON wf.module COLLATE DATABASE_DEFAULT = 'WORKFLOW' COLLATE DATABASE_DEFAULT
 AND wf.action COLLATE DATABASE_DEFAULT = 'DELETE'   COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  -- predicate (1): the platform already trusts this role to delete something
  AND EXISTS (SELECT 1 FROM iam.role_permissions rp
              JOIN iam.permissions p ON p.id = rp.permission_id
              WHERE rp.role_id = r.id
                AND p.action COLLATE DATABASE_DEFAULT = 'DELETE' COLLATE DATABASE_DEFAULT)
  -- predicate (2): ...and it already participates in THIS engine, so it is not
  -- handed authority over records it cannot list or open (the V324 half-grant).
  AND EXISTS (SELECT 1 FROM iam.role_permissions rp2
              JOIN iam.permissions p2 ON p2.id = rp2.permission_id
              WHERE rp2.role_id = r.id
                AND p2.module COLLATE DATABASE_DEFAULT = 'WORKFLOW' COLLATE DATABASE_DEFAULT
                AND p2.action COLLATE DATABASE_DEFAULT = 'WRITE'    COLLATE DATABASE_DEFAULT)
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = wf.id);

/* ── verification 1: ORG_ADMIN must still hold it (it is the only pre-existing
      holder; losing it would mean the predicates are wrong, not tighter). ── */
IF NOT EXISTS (
    SELECT 1 FROM iam.roles r
    JOIN iam.role_permissions rp ON rp.role_id = r.id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name = 'ORG_ADMIN' AND r.is_deleted = 0
      AND p.module = 'WORKFLOW' AND p.action = 'DELETE')
    THROW 50325, 'V325: ORG_ADMIN lost WORKFLOW:DELETE — the derivation is wrong.', 1;

/* ── verification 2: no read-only, external or machine persona may hold it. ── */
DECLARE @leaked NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'WORKFLOW' AND p.action = 'DELETE'
          AND (r.role_name LIKE '%INGEST'
               OR r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR', 'SUPPLIER_USER'))) x);
IF @leaked IS NOT NULL
    THROW 50325, 'V325: a read-only/external/machine persona holds WORKFLOW:DELETE.', 1;

/* ── verification 3: no holder may lack WORKFLOW:READ — the V324 lesson. A role
      able to delete a list it cannot list is a 403 halfway through a flow. ── */
DECLARE @halfGranted NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'WORKFLOW' AND p.action = 'DELETE'
          AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp3
                          JOIN iam.permissions p3 ON p3.id = rp3.permission_id
                          WHERE rp3.role_id = r.id
                            AND p3.module = 'WORKFLOW' AND p3.action = 'READ')) x);
IF @halfGranted IS NOT NULL
    THROW 50325, 'V325: a role holds WORKFLOW:DELETE without WORKFLOW:READ — it could delete a list it cannot see.', 1;

DECLARE @after INT = (
    SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'WORKFLOW' AND p.action = 'DELETE');
PRINT CONCAT('V325: WORKFLOW:DELETE holders ', @before, ' -> ', @after,
             ' (delete authority on recipient lists was 55 via WORKFLOW:WRITE).');

-- V231 — COMPLIANCE module role→permission grants.
--
-- Remediation plan §15 #7 / permissions gap (MODULE_REVIEW_AND_REMEDIATION_PLAN.md):
-- the COMPLIANCE_READ / COMPLIANCE_WRITE permission rows exist but were never
-- granted to any role, so every @PreAuthorize("hasPermission('COMPLIANCE',…)")
-- endpoint (policies, controls, regulatory change, audit engagements, CAPA,
-- framework anchors) worked for PLATFORM_ADMIN only. This grants them to the
-- appropriate roles, mirroring the V10 evidence-permission tiering.
--
-- It also ADDS the missing COMPLIANCE_DELETE permission: PolicyController.delete
-- requires hasPermission('COMPLIANCE','DELETE'), but no such permission row
-- existed, so deletes were impossible for everyone except the admin bypass.
--
-- Idempotent: re-running inserts nothing new. Ids are resolved by name so this
-- is portable across environments (dev/staging/prod ids differ).

-- 1. Add the missing COMPLIANCE_DELETE permission.
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'COMPLIANCE_DELETE')
  INSERT INTO iam.permissions (module, action, permission_name, description_en)
  VALUES ('COMPLIANCE', 'DELETE', 'COMPLIANCE_DELETE',
          'Delete compliance records (policies, controls, obligations, findings)');
GO

-- 2. Grant the three COMPLIANCE permissions to the right roles.
WITH grants AS (
  SELECT r.id AS role_id, p.id AS permission_id
  FROM iam.roles r
  JOIN iam.permissions p
    ON p.permission_name IN ('COMPLIANCE_READ', 'COMPLIANCE_WRITE', 'COMPLIANCE_DELETE')
  WHERE
    -- READ: every internal + assurance/oversight role (exclude the external
    -- supplier-portal role, which must not see internal compliance data).
    (p.permission_name = 'COMPLIANCE_READ' AND r.role_name <> 'SUPPLIER_USER')
    -- WRITE: roles that author / manage compliance content.
    OR (p.permission_name = 'COMPLIANCE_WRITE' AND r.role_name IN (
          'PLATFORM_ADMIN', 'ORG_ADMIN', 'RISK_MANAGER', 'COMPLIANCE_OFFICER',
          'INTERNAL_AUDITOR', 'DPO', 'BCM_MANAGER', 'GRC_ADMINISTRATOR',
          'CONTROL_OWNER', 'NCA_COMPLIANCE', 'PRIVACY_MANAGER',
          'LEGAL_REVIEWER', 'EXECUTIVE_REVIEWER'))
    -- DELETE: restricted to admins + the compliance lead.
    OR (p.permission_name = 'COMPLIANCE_DELETE' AND r.role_name IN (
          'PLATFORM_ADMIN', 'ORG_ADMIN', 'COMPLIANCE_OFFICER', 'GRC_ADMINISTRATOR'))
)
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT g.role_id, g.permission_id
FROM grants g
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_permissions rp
  WHERE rp.role_id = g.role_id AND rp.permission_id = g.permission_id
);
GO

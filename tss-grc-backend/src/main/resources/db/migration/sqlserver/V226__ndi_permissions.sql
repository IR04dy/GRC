-- ============================================================
-- V226 — NDI (NDMO Data Governance) Module: Permissions
--
-- Follow-up to audit finding SEC-NDI-001: the NDI controllers
-- (com.tss.grc.module.ndi.**) were hardened from
-- @PreAuthorize("isAuthenticated()") to granular
-- hasPermission('NDI','READ'|'WRITE'|'APPROVE'). The
-- ModulePermissionEvaluator resolves those to the granted
-- authorities NDI_READ / NDI_WRITE / NDI_APPROVE, but no such
-- permission rows existed — so every NDI endpoint was reachable
-- ONLY by ROLE_PLATFORM_ADMIN (which bypasses the evaluator) and
-- every other role got 403.
--
-- This inserts the three NDI permissions and grants them along
-- the same tiering the EVIDENCE module uses (see V10):
--   READ    → all roles
--   WRITE   → all roles except read-only / external viewers
--   APPROVE → governance approver + admin roles
--
-- Idempotent: every INSERT is guarded with NOT EXISTS, so the
-- migration is safe to re-run. (Flyway is disabled in the dev
-- profile — this runs in staging/prod; the same SQL was validated
-- against the dev DB.)
-- ============================================================

SET NOCOUNT ON;
GO

-- ── Insert permissions ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'NDI_READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('NDI', 'READ', 'NDI_READ', 'View NDMO data-governance domains, framework items, assessment cycles, evidence, gaps, audits and dashboards');

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'NDI_WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('NDI', 'WRITE', 'NDI_WRITE', 'Create and update NDMO governance domains, framework items, assessments, evidence, gaps and audits');

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'NDI_APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('NDI', 'APPROVE', 'NDI_APPROVE', 'Approve / sign off NDMO compliance assessments and governance decisions');
GO

-- ── Assign permissions to roles ───────────────────────────────
-- NDI_READ → every role (mirrors EVIDENCE_READ in V10).
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'NDI_READ'
  AND r.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NDI_WRITE → editing roles only. Mirrors EVIDENCE_WRITE in V10,
-- extended to exclude the external / regulator / supplier viewer
-- roles added in V116, and the auditor roles: auditors review NDMO
-- governance (read), they do not author its records.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'NDI_WRITE'
  AND r.is_deleted = 0
  AND r.role_name NOT IN ('VIEWER', 'READ_ONLY', 'INTERNAL_AUDITOR',
                          'EXTERNAL_AUDITOR', 'REGULATOR_VIEWER', 'SUPPLIER_USER')
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NDI_APPROVE → governance approver + admin roles only.
-- (CISO / CIO / CHIEF_DATA_OFFICER are listed for forward-compat —
-- they are not in the current catalogue, so they simply match
-- nothing until/unless those roles are added.)
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'NDI_APPROVE'
  AND r.is_deleted = 0
  AND r.role_name IN ('PLATFORM_ADMIN', 'ORG_ADMIN', 'GRC_ADMINISTRATOR',
                      'DPO', 'COMPLIANCE_OFFICER',
                      'CISO', 'CIO', 'CHIEF_DATA_OFFICER')
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
GO

PRINT 'V226 — NDI permissions inserted and assigned.';
GO

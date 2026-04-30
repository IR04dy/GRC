-- ============================================================
-- V10 — Evidence Module: Permissions
-- Inserts EVIDENCE_READ, EVIDENCE_WRITE, EVIDENCE_DELETE
-- and assigns them to PLATFORM_ADMIN and ORG_ADMIN roles.
-- ============================================================

USE TSS_GRC;
GO

SET NOCOUNT ON;

-- ── Insert permissions ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'EVIDENCE_READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('EVIDENCE', 'READ', 'EVIDENCE_READ', 'View evidence items, requests, reviews and frameworks');

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'EVIDENCE_WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('EVIDENCE', 'WRITE', 'EVIDENCE_WRITE', 'Create, update and manage evidence items and requests');

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'EVIDENCE_DELETE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('EVIDENCE', 'DELETE', 'EVIDENCE_DELETE', 'Delete evidence items and requests');
GO

-- ── Assign permissions to all roles (non-viewer) ─────────────
-- EVIDENCE_READ → all roles
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'EVIDENCE_READ'
  AND r.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- EVIDENCE_WRITE → all roles except VIEWER and READ_ONLY roles
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'EVIDENCE_WRITE'
  AND r.is_deleted = 0
  AND r.role_name NOT IN ('VIEWER', 'READ_ONLY')
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- EVIDENCE_DELETE → admin roles only
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'EVIDENCE_DELETE'
  AND r.is_deleted = 0
  AND r.role_name IN ('PLATFORM_ADMIN', 'ORG_ADMIN', 'RISK_MANAGER', 'COMPLIANCE_OFFICER')
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
GO

PRINT 'V10 — Evidence permissions inserted and assigned.';
GO

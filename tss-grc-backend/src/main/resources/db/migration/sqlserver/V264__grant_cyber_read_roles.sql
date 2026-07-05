-- V264 — grant CYBER module READ to oversight + operational roles (epic CY-G,
--        finding "cyber module is admin-only / auditors get 403 on reads").
--
-- FINDING: cyber controllers correctly gate GET reads with hasPermission('CYBER','READ')
-- and mutations with 'WRITE' — but the ONLY role holding CYBER_READ is PLATFORM_ADMIN.
-- So auditors, read-only viewers, and even the CISO / SOC analyst who operate the
-- module all get 403 on every cyber read. This grants CYBER_READ (read-only; WRITE +
-- DELETE stay admin-only, preserving least privilege) to the roles that legitimately
-- need to SEE the cyber module:
--   • oversight / read-only : AUDITOR, INTERNAL_AUDITOR, EXTERNAL_AUDITOR,
--                             REGULATOR_VIEWER, VIEWER
--   • cyber / SOC operators : CISO, SECURITY_ANALYST, CYBER_MANAGER
--
-- Idempotent (NOT EXISTS guard); grants only to roles that exist. SOCOPS has no
-- permission rows (it is role-gated via @PreAuthorize hasAnyRole), so no grant here.
-- Forward-only; dev is Flyway-off, hand-applied via pyodbc.

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name = 'CYBER_READ'
  AND r.role_name IN (
      'AUDITOR', 'INTERNAL_AUDITOR', 'EXTERNAL_AUDITOR', 'REGULATOR_VIEWER', 'VIEWER',
      'CISO', 'SECURITY_ANALYST', 'CYBER_MANAGER'
  )
  AND NOT EXISTS (
      SELECT 1 FROM iam.role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
GO

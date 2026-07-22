-- V301 (CH-1 / CM-C1): dedicated CHANGE permission module.
--
-- Change Management was the ONLY consumer of hasPermission('WORKFLOW',…) that is
-- not part of the workflow engine, yet WORKFLOW_WRITE is granted to 55 roles —
-- including pure viewers, auditors, regulators and suppliers — so anyone could
-- author, edit, vote on and drive a change through its lifecycle. WORKFLOW stays
-- as-is for the real engine (workflow_submission + workflow.requests, 3 other
-- controllers); only the change_mgmt controller repoints to CHANGE (code change).
--
-- Grants MIRROR the current per-action WORKFLOW holders into CHANGE, MINUS the
-- approved "tight" least-privilege exclusion set (2026-07-22):
--   * READ  excludes SUPPLIER_USER + 8 telemetry ingest service accounts
--           (external/machine principals get no change visibility). Human
--           observers (VIEWER/AUDITOR/REGULATOR_VIEWER/…) keep READ.
--   * WRITE/APPROVE/DELETE/EXPORT additionally exclude the 6 read-only/observer/
--           external human roles — 14 roles total become read-only for changes.
-- Idempotent (NOT EXISTS guards); applies to every tenant's roles by role_name.
-- PLATFORM_ADMIN needs no grant (evaluator bypass).

-- ── Part 1: catalogue CHANGE/{READ,WRITE,APPROVE,DELETE,EXPORT} ─────────────
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('CHANGE','READ',   'View change requests, tasks, approvals and links'),
    ('CHANGE','WRITE',  'Create/update change requests, tasks, links, comments; drive lifecycle transitions'),
    ('CHANGE','APPROVE','Cast CAB approval/rejection votes on change requests'),
    ('CHANGE','DELETE', 'Delete change requests and tasks'),
    ('CHANGE','EXPORT', 'Export the change register')
) v(module, action, descr)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p WHERE p.module = v.module AND p.action = v.action);
GO

-- ── Part 2a: CHANGE_READ — mirror WORKFLOW_READ holders, minus supplier+machine ─
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT rp.role_id, cp.id
FROM iam.role_permissions rp
JOIN iam.permissions wp ON wp.id = rp.permission_id AND wp.module = 'WORKFLOW' AND wp.action = 'READ'
JOIN iam.permissions cp ON cp.module = 'CHANGE' AND cp.action = 'READ'
JOIN iam.roles r ON r.id = rp.role_id
WHERE r.role_name NOT IN (
        'SUPPLIER_USER',
        'APPSEC_INGEST','BRAND_INGEST','DLP_INGEST','JML_INGEST',
        'SBOM_INGEST','SCANNER_INGEST','SIEM_INGEST','TI_INGEST')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = cp.id);
GO

-- ── Part 2b: CHANGE WRITE/APPROVE/DELETE/EXPORT — mirror matching WORKFLOW action,
--            minus the full 14-role read-only/observer/external/machine set ──────
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT rp.role_id, cp.id
FROM iam.role_permissions rp
JOIN iam.permissions wp ON wp.id = rp.permission_id AND wp.module = 'WORKFLOW'
                        AND wp.action IN ('WRITE','APPROVE','DELETE','EXPORT')
JOIN iam.permissions cp ON cp.module = 'CHANGE' AND cp.action = wp.action
JOIN iam.roles r ON r.id = rp.role_id
WHERE r.role_name NOT IN (
        'VIEWER','REGULATOR_VIEWER','AUDITOR','INTERNAL_AUDITOR','EXTERNAL_AUDITOR','SUPPLIER_USER',
        'APPSEC_INGEST','BRAND_INGEST','DLP_INGEST','JML_INGEST',
        'SBOM_INGEST','SCANNER_INGEST','SIEM_INGEST','TI_INGEST')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = cp.id);
GO

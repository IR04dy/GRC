-- =====================================================================
-- V248 — Sustainability/ESG (SUST) permissions, role, and grants (epic S-A).
-- ---------------------------------------------------------------------
-- The 12 `module/sust` sub-modules (+ climate-risk) gate on
-- hasPermission('SUST', READ|WRITE|DELETE), but NO SUST permission row ever
-- existed and no role was granted them, so ModulePermissionEvaluator resolved
-- every check to a missing authority → the entire Sustainability module was
-- reachable only by PLATFORM_ADMIN (the admin bypass). The climate sub-
-- controllers (TcfdScenario/FinancedEmissions/ClimateReport) additionally gated
-- on hasAnyRole('SUSTAINABILITY_OFFICER','CFO','CISO','BOARD_MEMBER',...) — roles
-- that were never seeded, so those endpoints were admin-only too and the
-- module's own target persona could not be assigned.
--
-- This migration (1) adds SUST_READ/WRITE/DELETE, (2) creates the
-- SUSTAINABILITY_OFFICER operational role, (3) seeds a role-appropriate grant
-- matrix (mirrors V231 compliance / V239 BCM). The climate controllers are
-- switched to hasPermission('SUST',…) in the same S-A change so the whole module
-- uses ONE authorization scheme. Idempotent: every insert is NOT EXISTS-guarded.
-- =====================================================================

-- 1. The three SUST permissions.
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'SUST_READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('SUST', 'READ', 'SUST_READ',
            'Read sustainability/ESG records (GHG, SGI, CMA, TCFD, climate, materiality, metrics, evidence, reports)');
GO
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'SUST_WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('SUST', 'WRITE', 'SUST_WRITE',
            'Create/update sustainability/ESG records and disclosure approvals');
GO
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'SUST_DELETE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('SUST', 'DELETE', 'SUST_DELETE', 'Delete sustainability/ESG records');
GO

-- 2. SUSTAINABILITY_OFFICER — the module's target persona (was never seeded).
--    Operational role, org-scoped like its peers (org 1 for dev); external_id
--    defaults to newid(). Prod should seed one per tenant org.
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = 'SUSTAINABILITY_OFFICER')
    INSERT INTO iam.roles (organisation_id, role_name, display_name_en, display_name_ar,
                           description_en, is_system_role, is_active)
    VALUES (1, 'SUSTAINABILITY_OFFICER', 'Sustainability Officer', N'مسؤول الاستدامة',
            'Owns ESG/sustainability reporting: GHG inventory, SGI commitments, CMA/TCFD disclosures, climate risk, materiality, evidence',
            1, 1);
GO

-- 3. Grant matrix. READ = every internal role (exclude the external supplier-portal
--    role); WRITE = ESG authors + admins + executive (approvals are SUST:WRITE);
--    DELETE = admins + the sustainability lead. Ids resolved by name (portable).
WITH grants AS (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM iam.roles r
    JOIN iam.permissions p
      ON p.permission_name IN ('SUST_READ', 'SUST_WRITE', 'SUST_DELETE')
    WHERE
        (p.permission_name = 'SUST_READ' AND r.role_name <> 'SUPPLIER_USER')
        OR (p.permission_name = 'SUST_WRITE' AND r.role_name IN (
              'PLATFORM_ADMIN', 'ORG_ADMIN', 'GRC_ADMINISTRATOR', 'SUSTAINABILITY_OFFICER',
              'RISK_MANAGER', 'COMPLIANCE_OFFICER', 'DATA_OWNER', 'EXECUTIVE_REVIEWER'))
        OR (p.permission_name = 'SUST_DELETE' AND r.role_name IN (
              'PLATFORM_ADMIN', 'ORG_ADMIN', 'GRC_ADMINISTRATOR', 'SUSTAINABILITY_OFFICER'))
)
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT g.role_id, g.permission_id
FROM grants g
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
     WHERE rp.role_id = g.role_id AND rp.permission_id = g.permission_id);
GO

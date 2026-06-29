-- =====================================================================
-- V239 — BCM role-permission grants (epic B-I — role ratchet).
-- ---------------------------------------------------------------------
-- The BCM module shipped with permission rows (BCM READ/WRITE/DELETE) but NO
-- role grants, so every BCM endpoint (the controllers gate on
-- hasPermission('BCM', …)) was reachable only by PLATFORM_ADMIN, which bypasses
-- the check. A BCM_MANAGER, BIA_OWNER, auditor, etc. got 403. Two endpoints also
-- gate on hasPermission('BCM','APPROVE') but no BCM_APPROVE permission existed
-- at all — admin-only forever.
--
-- This (a) adds the missing BCM_APPROVE permission, and (b) seeds a role-
-- appropriate grant matrix. Idempotent: the permission insert and every grant
-- are guarded by NOT EXISTS, so re-running is safe.
-- =====================================================================

-- (a) Missing APPROVE permission (id is IDENTITY — omit it).
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'BCM' AND action = 'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('BCM', 'APPROVE', 'BCM_APPROVE', 'Approve BCM records (BIA, plans, governance)');
GO

-- (b) Grant matrix. READ broadly (owners + auditors + viewer), WRITE to the
-- BCM authors, DELETE to admins + the BCM owner, APPROVE to admins + BCM owner
-- + executive reviewer.
;WITH grants(role_name, action) AS (
    SELECT v.role_name, v.action FROM (VALUES
        -- full control
        ('PLATFORM_ADMIN','READ'),('PLATFORM_ADMIN','WRITE'),('PLATFORM_ADMIN','DELETE'),('PLATFORM_ADMIN','APPROVE'),
        ('ORG_ADMIN','READ'),('ORG_ADMIN','WRITE'),('ORG_ADMIN','DELETE'),('ORG_ADMIN','APPROVE'),
        ('GRC_ADMINISTRATOR','READ'),('GRC_ADMINISTRATOR','WRITE'),('GRC_ADMINISTRATOR','DELETE'),('GRC_ADMINISTRATOR','APPROVE'),
        ('BCM_MANAGER','READ'),('BCM_MANAGER','WRITE'),('BCM_MANAGER','DELETE'),('BCM_MANAGER','APPROVE'),
        -- BCM sub-domain authors: read + write
        ('BIA_OWNER','READ'),('BIA_OWNER','WRITE'),
        ('DR_OWNER','READ'),('DR_OWNER','WRITE'),
        ('CRISIS_MANAGER','READ'),('CRISIS_MANAGER','WRITE'),
        ('SERVICE_OWNER','READ'),('SERVICE_OWNER','WRITE'),
        ('RISK_MANAGER','READ'),('RISK_MANAGER','WRITE'),
        -- executive can also approve
        ('EXECUTIVE_REVIEWER','READ'),('EXECUTIVE_REVIEWER','APPROVE'),
        -- read-only: auditors, board, regulator, viewer
        ('INTERNAL_AUDITOR','READ'),
        ('EXTERNAL_AUDITOR','READ'),
        ('BOARD_SECRETARY','READ'),
        ('REGULATOR_VIEWER','READ'),
        ('VIEWER','READ')
    ) v(role_name, action)
)
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM grants g
JOIN iam.roles r       ON r.role_name = g.role_name
JOIN iam.permissions p ON p.module = 'BCM' AND p.action = g.action
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
     WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

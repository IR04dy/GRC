-- =====================================================================
-- V321 — F2-13a: scheduled reporting was unreachable for every tenant persona.
-- ---------------------------------------------------------------------
-- The grant-tightness report (Appendix/scripts/grant_tightness.py, which joins the compiled
-- @PreAuthorize set to iam.role_permissions) found exactly one permission that guards live
-- endpoints and is held by ZERO of the 57 roles:
--
--   REPORTING:WRITE  ->  ReportScheduleController#create, #update, #runNow
--
-- Those three are the whole write surface of scheduled reporting — the feature that ticks every
-- minute in ReportSchedulerService, regenerates a PDF/XLSX and emails it. The guards are
-- permission-only (no ORed hasAnyRole leg to fall through to), so every request 403s except
-- PLATFORM_ADMIN's, which passes on ModulePermissionEvaluator's hardcoded code bypass rather than
-- on a grant. Fail-closed, so nothing logged and nothing looked broken to anyone testing as admin.
--
-- Meanwhile 18 roles hold REPORTING:READ and can list schedules they can never create, and
-- ORG_ADMIN holds REPORTING:DELETE — it can delete a schedule it can neither create nor list.
--
-- This is the same shape as F2-23 (ADMIN:APPROVE guarding 59 committee methods while absent from
-- the catalogue) and F2-27 (AI:EXPORT absent while 41 roles held AI:READ): the mechanism was
-- correct and the grant behind it never existed. The plan's own illustration of this class was
-- ADMIN:WRITE "in 0 roles" — measurement says ADMIN:WRITE is held by 3. REPORTING:WRITE is the
-- real instance, and it was invisible until the two halves were joined.
--
-- WHO GETS IT, AND WHY ONLY THEM
-- Creating a schedule mails a whole register to an arbitrary address on a cron, so create is an
-- administrative act, not a reader's convenience. The minimum that makes the feature reachable:
--
--   ORG_ADMIN          already trusted with REPORTING:DELETE; a role that can delete a schedule
--                      must be able to create one. Most privileged tenant role (94 grants).
--   GRC_ADMINISTRATOR  the tenant's GRC operations admin (83 grants); the owner of recurring
--                      compliance reporting. Holds no REPORTING permission at all today.
--
-- Both also get REPORTING:READ, without which they would administer schedules they cannot list —
-- the asymmetry ORG_ADMIN is in right now. Deliberately NOT granted: REPORTING:EXPORT (F2-27 made
-- that a live file-download right; widening exfiltration is not part of unblocking a feature) and
-- no fourth role — COMPLIANCE_OFFICER and EXECUTIVE_REVIEWER hold READ+EXPORT and a tenant whose
-- operating model wants them scheduling reports can grant WRITE from the role editor. Tightening
-- the over-broad grants the same report found is F2-13b.
--
-- Idempotent (NOT EXISTS per row); safe to re-run. Applied twice to the dev DB via pyodbc.
-- =====================================================================

SET NOCOUNT ON;

DECLARE @grants TABLE (role_name NVARCHAR(100), module NVARCHAR(50), action NVARCHAR(50));

INSERT INTO @grants (role_name, module, action) VALUES
    ('ORG_ADMIN',         'REPORTING', 'WRITE'),
    ('ORG_ADMIN',         'REPORTING', 'READ'),
    ('GRC_ADMINISTRATOR', 'REPORTING', 'WRITE'),
    ('GRC_ADMINISTRATOR', 'REPORTING', 'READ');

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM @grants g
JOIN iam.roles r
  ON r.role_name COLLATE DATABASE_DEFAULT = g.role_name COLLATE DATABASE_DEFAULT
 AND r.is_deleted = 0
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = g.module COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT = g.action COLLATE DATABASE_DEFAULT
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role_permissions x
    WHERE x.role_id = r.id AND x.permission_id = p.id
);

-- Verification: REPORTING:WRITE must no longer be held by zero roles.
DECLARE @holders INT = (
    SELECT COUNT(rp.role_id)
    FROM iam.permissions p
    LEFT JOIN iam.role_permissions rp ON rp.permission_id = p.id
    WHERE p.module = 'REPORTING' AND p.action = 'WRITE'
);
IF @holders = 0
    THROW 50321, 'V321: REPORTING:WRITE is still held by no role — scheduled reporting stays unreachable.', 1;

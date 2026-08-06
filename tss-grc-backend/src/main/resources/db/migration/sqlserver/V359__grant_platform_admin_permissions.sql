/* =====================================================================================
   V359 — grant the PLATFORM_ADMIN permission module to the PLATFORM_ADMIN role (F2-81)

   WHY
   ---
   F2-81 added the platform's first tenant-onboarding endpoint. It needs a guard whose
   population is "the host operator and nobody else".

   `hasRole('PLATFORM_ADMIN')` is the right population and the wrong mechanism:
   RoleGuardConversionRatchetTest counts pure role guards under com.tss.grc.module.** and
   lets them only decrease, because a role list cannot be re-granted without shipping a jar
   and is invisible to the grant-tightness join.

   ⚠ The obvious conversion was measured and rejected. `hasPermission('PLATFORM','WRITE')`
   would have been a privilege escalation: PLATFORM:WRITE is granted to ORG_ADMIN, a
   *tenant* administrator, so any customer's admin could have created tenants.

   `PLATFORM_ADMIN` is a separate catalogued permission module (READ / WRITE / EXPORT) that
   existed with **zero grants** — the F2-130 shape, a permission nobody holds. The guard
   would still have admitted the PLATFORM_ADMIN role via ModulePermissionEvaluator's code
   bypass, which is precisely the fragility worth removing: a guard whose only route to
   success is a hard-coded bypass fails closed the day the bypass is reconsidered, and no
   test would say why. This migration makes the grant real.

   IDEMPOTENT + SELF-CHECKING per CLAUDE.md: safe to apply twice, and THROWs rather than
   silently doing nothing if its assumptions do not hold.
   ===================================================================================== */
SET NOCOUNT ON;

DECLARE @roleId  BIGINT;
DECLARE @granted INT;

SELECT @roleId = id FROM iam.roles WHERE role_name = 'PLATFORM_ADMIN';

IF @roleId IS NULL
BEGIN
    /* Not a no-op: the guard this migration exists for would then authorise nobody at all,
       and the endpoint would be dead on arrival. Fail loudly. */
    THROW 51359, 'V359: role PLATFORM_ADMIN not found in iam.roles — the tenant-onboarding guard would authorise nobody.', 1;
END;

/* The three actions must already be catalogued; V272 created the 114-permission catalogue.
   If they are absent, granting would insert nothing and the guard would silently fail. */
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'PLATFORM_ADMIN' AND action = 'WRITE')
BEGIN
    THROW 51360, 'V359: permission PLATFORM_ADMIN:WRITE is not catalogued — cannot grant it.', 1;
END;

/* READ and WRITE only. EXPORT is deliberately left ungranted: nothing exports from this
   surface, and granting an action no endpoint requires is how a catalogue stops describing
   the platform. */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT @roleId, p.id
FROM   iam.permissions p
WHERE  p.module = 'PLATFORM_ADMIN'
  AND  p.action IN ('READ', 'WRITE')
  AND  NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                   WHERE  rp.role_id = @roleId AND rp.permission_id = p.id);

SELECT @granted = COUNT(*)
FROM   iam.role_permissions rp
JOIN   iam.permissions p ON p.id = rp.permission_id
WHERE  rp.role_id = @roleId AND p.module = 'PLATFORM_ADMIN' AND p.action IN ('READ', 'WRITE');

IF @granted <> 2
BEGIN
    THROW 51361, 'V359: PLATFORM_ADMIN role does not hold exactly PLATFORM_ADMIN READ+WRITE after this migration.', 1;
END;

PRINT 'V359: PLATFORM_ADMIN role holds PLATFORM_ADMIN READ+WRITE (tenant onboarding guard, F2-81).';

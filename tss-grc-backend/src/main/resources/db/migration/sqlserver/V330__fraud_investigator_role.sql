/* ============================================================================
   V330 — FRAUD_INVESTIGATOR: the persona V328 deliberately declined to pick.

   THE DECISION (ADR-017 D-9). V328 catalogued FRAUD:READ/FRAUD:WRITE and granted
   WRITE to GRC_ADMINISTRATOR + ORG_ADMIN, explicitly refusing to anoint an
   investigator because FORENSICS_LEAD, IR_LEAD and SECURITY_ANALYST were all
   plausible and picking one would have been a guess dressed as a fix. The owner
   delegated that call with a standard to apply — best practice, market-leader
   precedent, Saudi-market suitability — so it is now answered on grounds rather
   than on plausibility.

   WHY A NEW ROLE RATHER THAN AN EXISTING ONE. Measured, not assumed:

     FRAUD:WRITE holders today  = GRC_ADMINISTRATOR, ORG_ADMIN  (only these two)
     GRC_ADMINISTRATOR also holds 10 APPROVE permissions
       (ADMIN, BCM, CAPA, CHANGE, IAM, NDI, RISK, SIGNATURE, SUST, WORKFLOW)
     ORG_ADMIN also holds 8 APPROVE permissions

   So the only two roles that can investigate a fraud case are also the platform's
   two broadest approvers. That is the segregation-of-duties problem stated
   concretely: SAMA's fraud-control expectations and the AML/CTF Law treat fraud
   investigation as a function distinct from compliance and from approval
   authority, and today the two are the same people by construction.

   The three cyber candidates were checked and rejected on measurement, not taste:
   FORENSICS_LEAD (15 perms), IR_LEAD (17) and SECURITY_ANALYST (18) all hold
   ZERO approve permissions — correctly investigator-shaped — but every one of
   them is a *cyber* incident persona. Fraud is a financial-crime function; giving
   digital forensics the fraud case lifecycle would be a scope error, not a fix.
   There is no AML_OFFICER role and no AML module in iam.permissions, so FRAUD is
   the only financial-crime surface catalogued and it has no owner at all.

   ── WHY THIS IS NOT A REVERSAL OF "DO NOT SEED THE 40 GHOST ROLES" ──────────
   F2-52's rule forbids creating a role to make an UNCONVERTED guard fire —
   resurrecting FRAUD_OFFICER so a dead hasAnyRole leg lights up. That is not what
   this is. The guards are already permissions (V328 + the slice-3 conversion), the
   role is being created because the business function exists in the regulation and
   currently has no home, and the grant is a row in iam.role_permissions rather
   than a name compiled into Java. Opposite direction, opposite reasoning.

   Note also: ONE role, not two. The old ghost guard named both FRAUD_OFFICER and
   FRAUD_INVESTIGATOR for the same function. Creating both would re-import the
   habit that produced 40 ghost names in the first place.

   ── THE ROW SHAPE, EACH FIELD MEASURED AGAINST EXISTING ROLES ──────────────
     organisation_id = NULL  -> a GLOBAL persona. 47 of the 58 existing roles are
                                scoped to organisation_id = 1 and therefore do not
                                exist for orgs 2 and 3 at all; a platform-defined
                                persona must not inherit that. (That 47 is its own
                                finding, raised in the plan, not fixed here.)
     is_system_role  = 1     -> platform-defined, like 57 of the 58.
     is_privileged   = 0     -> checked what consumes this column before setting
                                it: the ONLY reader is BreakGlassEventRepository,
                                which uses it to detect who holds privileged roles
                                for break-glass reporting. A fraud investigator is
                                not an emergency-access role, so marking it
                                privileged would file every fraud investigator as a
                                break-glass subject. Matches FORENSICS_LEAD/IR_LEAD.
     display_name_ar         -> mandatory. House rule: user-visible records carry
                                *_en AND *_ar, and a role name appears in the role
                                editor.

   ── GRANTS: FRAUD:READ + FRAUD:WRITE, AND NOTHING ELSE ─────────────────────
   Least privilege, and sufficient: after F2-05 slice 3, AttachmentAccessPolicy's
   FRAUD_CASE entry is ParentGuard.perm("FRAUD"), so case attachments are already
   covered by the same two permissions. No EVIDENCE grant is needed and none is
   given.

   ⚠ WHAT THIS DOES NOT DO, STATED SO IT IS NOT ASSUMED. Creating a distinct
   investigator makes SoD POSSIBLE; it does not enforce it. Measured: FraudService
   has no self-check and no four-eyes gate on setOutcome/close, so one principal
   can still open, investigate and resolve the same case. And this migration
   deliberately does NOT revoke FRAUD:WRITE from the two administrators — doing so
   would leave every tenant with no fraud writer at all until someone assigns the
   new role, which is an outage rather than a control. The maker-checker gate on
   fraud case outcome is a separate slice, raised in the plan.

   Idempotent: NOT EXISTS-guarded throughout; safe to apply twice, which is how
   dev receives it (Flyway is off in dev).
   ============================================================================ */

SET NOCOUNT ON;
GO

/* ── 1. The role ──────────────────────────────────────────────────────────── */
INSERT INTO iam.roles (organisation_id, role_name, display_name_en, display_name_ar,
                       description_en, is_system_role, is_active, is_privileged, is_deleted)
SELECT NULL, 'FRAUD_INVESTIGATOR', 'Fraud Investigator', N'مُحقِّق الاحتيال',
       'Opens, triages, investigates, resolves and closes fraud cases. Distinct from '
       + 'compliance and from approval authority, per SAMA fraud-control expectations '
       + 'and the AML/CTF Law.',
       1, 1, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM iam.roles
    WHERE role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT);
GO

/* ── 2. FRAUD:READ + FRAUD:WRITE, nothing else ────────────────────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'FRAUD' COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT IN ('READ' COLLATE DATABASE_DEFAULT,
                                           'WRITE' COLLATE DATABASE_DEFAULT)
WHERE r.role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

/* ── 3. Verify by throwing ────────────────────────────────────────────────── */

/* 3a. The role exists and is usable (active, not deleted, global). */
IF NOT EXISTS (
    SELECT 1 FROM iam.roles
    WHERE role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
      AND is_active = 1 AND is_deleted = 0 AND organisation_id IS NULL)
    THROW 50330, 'V330 FAILED: FRAUD_INVESTIGATOR is missing, inactive, deleted, or scoped to one tenant instead of global.', 1;
GO

/* 3b. It holds EXACTLY FRAUD:READ and FRAUD:WRITE — two rows, no scope creep.
       A persona migration that quietly grants more than it says is how a role
       becomes an administrator by accretion. */
DECLARE @n INT = (
    SELECT COUNT(*)
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT);
DECLARE @fraud INT = (
    SELECT COUNT(*)
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
      AND p.module COLLATE DATABASE_DEFAULT = 'FRAUD' COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT IN ('READ' COLLATE DATABASE_DEFAULT,
                                                'WRITE' COLLATE DATABASE_DEFAULT));
IF @n <> 2 OR @fraud <> 2
    THROW 50330, 'V330 FAILED: FRAUD_INVESTIGATOR must hold exactly FRAUD:READ and FRAUD:WRITE.', 1;
GO

/* 3c. It must hold NO approve authority anywhere. This is the whole point of the
       role: an investigator who can approve is the SoD defect restated. */
IF EXISTS (
    SELECT 1
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'APPROVE' COLLATE DATABASE_DEFAULT)
    THROW 50330, 'V330 FAILED: FRAUD_INVESTIGATOR holds an APPROVE permission. The role exists to separate investigation from approval; granting both recreates the defect.', 1;
GO

/* 3d. It must NOT be flagged privileged. Only BreakGlassEventRepository reads that
       column, and a fraud investigator is not an emergency-access subject. */
IF EXISTS (
    SELECT 1 FROM iam.roles
    WHERE role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
      AND is_privileged = 1)
    THROW 50330, 'V330 FAILED: FRAUD_INVESTIGATOR is flagged privileged, which files every holder as a break-glass subject.', 1;
GO

/* 3e. The Arabic display name must be present and non-empty — bilingual is a
       backend invariant here, not a frontend nicety (F2-30). */
IF EXISTS (
    SELECT 1 FROM iam.roles
    WHERE role_name COLLATE DATABASE_DEFAULT = 'FRAUD_INVESTIGATOR' COLLATE DATABASE_DEFAULT
      AND (display_name_ar IS NULL OR LEN(LTRIM(RTRIM(display_name_ar))) = 0))
    THROW 50330, 'V330 FAILED: FRAUD_INVESTIGATOR has no Arabic display name.', 1;
GO

/* 3f. FRAUD:WRITE is not revoked from the administrators — asserted, so a later
       "tighten it" edit has to confront the reason. Revoking would leave a tenant
       with no fraud writer until the new role is assigned: an outage, not a
       control. The real gate is maker-checker on case outcome, which does not
       exist yet (FraudService has no self-check) and is tracked separately. */
IF NOT EXISTS (
    SELECT 1
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name COLLATE DATABASE_DEFAULT = 'GRC_ADMINISTRATOR' COLLATE DATABASE_DEFAULT
      AND p.module COLLATE DATABASE_DEFAULT = 'FRAUD' COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT)
    THROW 50330, 'V330 FAILED: GRC_ADMINISTRATOR lost FRAUD:WRITE. This migration adds a persona; it does not revoke, because revoking leaves tenants unable to work a fraud case until the new role is assigned.', 1;
GO

PRINT N'V330 — FRAUD_INVESTIGATOR created (global, system, non-privileged, bilingual) with exactly FRAUD:READ + FRAUD:WRITE; 6 verifications passed.';
GO

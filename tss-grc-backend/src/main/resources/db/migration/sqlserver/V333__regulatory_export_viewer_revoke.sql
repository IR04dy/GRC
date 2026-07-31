/* ============================================================================
   V333 — the generic read-only persona stops being able to take the tenant's
          whole assurance position as one signed ZIP.

   ADR-017 D-10b, finding F2-60. Idempotent: safe to run twice (dev applies by
   hand, Flyway off in dev).

   ── WHAT THIS PAIRS WITH ───────────────────────────────────────────────────
   The code change beside this migration re-homes

       GET /api/v1/reporting/regulator-package.zip

   from  hasPermission('COMPLIANCE','EXPORT') or hasPermission('REPORTING','EXPORT')
   to    hasPermission('REGULATORY','EXPORT')

   Measured on BOTH databases, so this is not a seed-data artefact:

       reachable before:  37 of 59 roles (dev)  ·  37 of 57 roles (PROD)
       reachable after:   14 of 59            ·  14 of 57
       after this migration: 13                ·  13

   The package is not a register extract. It is README.pdf +
   Requirement_Mapping.xlsx + Evidence_Index.xlsx + Audit_Trail.xlsx +
   manifest.json with SHA-256 hashes — the requirement-to-control mapping, the
   evidence index AND the audit trail, hash-manifested so a recipient can prove
   it was not altered. That is the tenant's entire assurance position in one
   download, and 24 of the 37 roles that could take it have no assurance role at
   all: ASSET_OWNER, PROCUREMENT_OWNER, SUPPLIER_MANAGER, EMERGENCY_RESPONDER,
   MA_LEAD, DR_OWNER, BIA_OWNER, VULN_MANAGER, SERVICE_OWNER, DATA_OWNER…

   ── WHY THE GUARD MOVES AND ONLY ONE GRANT ROW IS TOUCHED ──────────────────
   Same instrument as D-10a, for the same reason: the guard was an OR, so
   narrowing it by revoking grants would mean taking COMPLIANCE:EXPORT and
   REPORTING:EXPORT from 24 roles — and those two authorise the findings-aging
   export, the policy-document PDF, the KRI dashboard, the packet builder and
   the risk register. The collateral would dwarf the fix.

   REGULATORY:EXPORT is the right destination and this was measured, not assumed:

     · It already exists in iam.permissions and is already granted to 14 roles,
       so no role has to be granted anything for the new guard to be satisfiable
       (F2-23's rule: never annotate against a permission no role holds).
     · Those 14 are almost exactly the assurance audience — AUDITOR,
       EXTERNAL_AUDITOR, INTERNAL_AUDITOR, REGULATOR_VIEWER, NCA_COMPLIANCE,
       COMPLIANCE_OFFICER, DPO, LEGAL_COUNSEL, LEGAL_REVIEWER, CISO,
       EXECUTIVE_REVIEWER, ORG_ADMIN, GRC_ADMINISTRATOR — plus VIEWER, which is
       the one row this migration removes.
     · ⚠ REGULATORY:EXPORT currently guards NOTHING. A grep of every
       @PreAuthorize in the backend finds zero uses of it. So it is a catalogued
       permission, granted to 14 roles, that enforces nothing — the F2-23 shape
       for the fifth time. That makes it a safe destination (no existing
       behaviour depends on it) and it means this change also turns a dead
       permission into a live one, which is the second win.

   ── WHY VIEWER SPECIFICALLY, AND WHY ONLY THIS ONE PERMISSION ──────────────
   VIEWER is the generic read-only persona. F2-27's own ratified premise is that
   "the right to view one record on screen does not confer the right to extract
   the whole register as a file" — and VIEWER holds SEVENTEEN *:EXPORT
   permissions in dev and in production (AUDIT, BCM, CAPA, COMPLIANCE,
   CONTROL_LIB, CYBER, EVIDENCE, INCIDENT, NDI, PRIVACY, REGULATORY, REPORTING,
   RISK, SCM, SUST, TPRM, WORKFLOW). F2-27 enforced the guard side and nobody
   ever reviewed the grant side, so the newly-enforced permissions were handed
   to 32–55 roles including this one.

   That whole population is F2-61 and it needs a stated grant policy, not a
   sweep in a migration whose subject is one endpoint. Exactly one row is
   removed here, and its blast radius is exactly one endpoint, because
   REGULATORY:EXPORT guards exactly one thing after the code change beside this
   file. Anything wider would be deciding F2-61 by accident.

   Live before: viewer/Viewer@12345 GET regulator-package.zip -> 200 with the
   full ZIP. Live after: 403. Proven at the grant level as well, because
   PLATFORM_ADMIN bypasses in code and therefore proves nothing about a guard.
   ============================================================================ */

SET NOCOUNT ON;

DECLARE @permId  BIGINT;
DECLARE @viewerRoleCount INT;

SELECT @permId = id FROM iam.permissions WHERE module = 'REGULATORY' AND action = 'EXPORT';

/* Anti-vacuity #1 — if the permission is not catalogued, the DELETE below would
   silently remove nothing and this migration would report success having done
   nothing at all. The whole change rests on this permission existing. */
IF @permId IS NULL
    THROW 51333, 'V333: REGULATORY:EXPORT is not catalogued in iam.permissions. The regulator-package guard is being re-homed onto it, so a missing permission means an endpoint no role can reach (F2-23). Investigate before proceeding — do not create the permission here without also reviewing its grant list.', 1;

/* The revocation. Idempotent by construction: a second run deletes zero rows. */
DELETE rp
FROM iam.role_permissions rp
JOIN iam.roles r ON r.id = rp.role_id
WHERE rp.permission_id = @permId
  AND r.role_name = 'VIEWER';

/* Anti-vacuity #2 — VIEWER must no longer hold it. Checked in the direction of
   the intended effect. */
SELECT @viewerRoleCount = COUNT(*)
FROM iam.role_permissions rp
JOIN iam.roles r ON r.id = rp.role_id
WHERE rp.permission_id = @permId AND r.role_name = 'VIEWER';

IF @viewerRoleCount > 0
    THROW 51333, 'V333: VIEWER still holds REGULATORY:EXPORT after the revoke. The generic read-only persona would keep the regulator evidence package (requirement mapping + evidence index + audit trail + SHA-256 manifest), which is the exposure this migration exists to close.', 1;

/* Anti-vacuity #3 — in the OPPOSITE direction, which is the check that catches a
   revoke that went too far. The assurance audience must still be able to furnish;
   an empty population here would mean the endpoint is now unreachable by anyone
   except PLATFORM_ADMIN, which is the F2-52 failure (admin-only by attrition) and
   would break furnishing under BCL art. 18 / CML inspection powers. */
DECLARE @assuranceHolders INT;
SELECT @assuranceHolders = COUNT(DISTINCT r.role_name)
FROM iam.role_permissions rp
JOIN iam.roles r ON r.id = rp.role_id
WHERE rp.permission_id = @permId
  AND r.role_name IN ('AUDITOR','EXTERNAL_AUDITOR','INTERNAL_AUDITOR','REGULATOR_VIEWER',
                      'NCA_COMPLIANCE','COMPLIANCE_OFFICER','DPO','ORG_ADMIN','GRC_ADMINISTRATOR');

IF @assuranceHolders < 5
    THROW 51333, 'V333: fewer than 5 assurance roles hold REGULATORY:EXPORT. The regulator evidence package would be unreachable by the personas whose job is to furnish it, which is a worse defect than the over-grant being fixed.', 1;

PRINT 'V333: REGULATORY:EXPORT revoked from VIEWER; assurance population intact.';

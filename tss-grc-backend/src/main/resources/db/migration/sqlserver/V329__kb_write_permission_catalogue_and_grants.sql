/* ============================================================================
   V329 — KB:WRITE enters the permission catalogue, so the knowledge base can be
          authored by someone other than the platform administrator.

   THE FINDING (F2-53, resolved by ADR-017 D-7; module `knowledge`).
   KbArticleController's three write guards are:

     hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')     -- create, update, transition

   `KB_WRITE` is the raw-authority spelling of the permission `KB:WRITE`, which
   ModulePermissionEvaluator emits as a granted authority. But `KB` is not in
   iam.permissions AT ALL, so no role can hold `KB_WRITE`, so that leg is
   permanently false and the whole guard reduces to `hasRole('PLATFORM_ADMIN')`.
   Authoring, editing and publishing every knowledge-base article was the
   platform administrator's job alone — by attrition, not by decision.

   This is the failure mode the role-guard census's own source comment uses
   `hasAuthority('KB_WRITE')` as its worked example. It is a ghost *permission*
   rather than a ghost role, and it fails identically and just as silently.

   ── WHAT THE READS DO, AND WHY THEY ARE NOT TOUCHED ────────────────────────
   The two read endpoints are `@PreAuthorize("isAuthenticated()")`, and they STAY
   that way. This is deliberate and it is the opposite of the usual conversion.

   Measured before deciding, by reading the content rather than the guards: all
   22 articles are status PUBLISHED, visibility INTERNAL, and they are staff
   operational guidance —

     "Whistleblowing Channel — How to Report"
     "Disclosing a Conflict of Interest (Nazaha)"
     "Handling Data-Subject Requests under PDPL"
     "Business Continuity Activation Steps (NCEMA)"
     "Delegating approvals when you're away"
     "Employee Onboarding & Offboarding Access"
     "NCA ECC Control Domains — Staff Overview"
     "Classifying & Handling Data per NDMO / SDAIA"

   A whistleblowing procedure that only permission-holders can read is worse than
   no procedure: under Saudi anti-corruption practice (Nazaha) and the platform's
   own ethics module, every employee must be able to find out how to report. The
   same is true of the conflict-of-interest and DSR walkthroughs. `isAuthenticated()`
   is the CORRECT guard here, and converting it for uniformity would have replaced
   a rule that cannot wrongly exclude anyone with a grant list that can only ever
   wrongly exclude someone. ADR-017 D-7 proposed granting `KB:READ` broadly; having
   now read the content, granting it *at all* is worse than the guard it replaces,
   so this migration catalogues WRITE only and the ADR is amended to match.

   ── WHY `KB:READ` IS DELIBERATELY NOT CREATED ──────────────────────────────
   Cataloguing a permission that no guard names is a documented anti-pattern in
   this platform: F2-13b measured 15 permissions that are catalogued AND granted
   and guard no endpoint at all — pure noise in the role editor, and a standing
   invitation to "tighten" an endpoint onto a permission nobody thought about.
   Verification 4 below asserts KB:READ's ABSENCE, so a future symmetry-minded
   migration cannot quietly add it without also changing this file.

   The "no writer may lack READ" rule (the V324 half-grant lesson, now fifth
   migration running) is satisfied structurally rather than by a grant: reads are
   open to every authenticated principal, so every KB writer can already read
   what they write. There is no half-grant to create.

   ── THE GRANTS, AND WHERE THEY COME FROM ───────────────────────────────────
     KB:WRITE -> GRC_ADMINISTRATOR, ORG_ADMIN
                 ...the tenant's own administrators. Same derivation as V328:
                 they are the only two roles holding >= 80% of the platform's
                 module WRITE actions, and every other module's WRITE includes
                 them.
              -> COMPLIANCE_OFFICER
                 ...the content owner. The corpus above is compliance guidance —
                 PDPL, NCA ECC, NDMO/SDAIA, Nazaha, NCEMA, records retention —
                 and the persona accountable for that guidance being correct is
                 the one who should be able to publish it. Without this the
                 finding is only half fixed: authoring would move off the
                 platform administrator and onto the *tenant* administrator,
                 who is not the author of compliance content either.

   Nothing is revoked. Every role that could write before (PLATFORM_ADMIN, via
   the surviving role leg and the evaluator bypass) still can.

   Idempotent: every INSERT is NOT EXISTS-guarded; safe to apply twice, which is
   how dev receives it (Flyway is off in dev).
   ============================================================================ */

SET NOCOUNT ON;
GO

/* ── 1. Catalogue KB:WRITE (and only WRITE — see the header) ─────────────── */
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.pname, v.descr
FROM (VALUES
    ('KB', 'WRITE', 'KB_WRITE',
     'Create, edit, publish and archive knowledge-base articles')
) v(module, action, pname, descr)
WHERE NOT EXISTS (
    SELECT 1 FROM iam.permissions p
    WHERE p.module COLLATE DATABASE_DEFAULT = v.module COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = v.action COLLATE DATABASE_DEFAULT);
GO

/* ── 2. Grant it to the two administrators + the content owner ───────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'KB'    COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT
WHERE r.role_name COLLATE DATABASE_DEFAULT IN (
        'GRC_ADMINISTRATOR' COLLATE DATABASE_DEFAULT,
        'ORG_ADMIN'         COLLATE DATABASE_DEFAULT,
        'COMPLIANCE_OFFICER' COLLATE DATABASE_DEFAULT)
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

/* ── 3. Verify, by throwing rather than by asserting in prose ─────────────── */

/* 3a. The permission exists. A guard naming an uncatalogued permission is F2-23,
       and shipping this migration without it would recreate the very defect it
       is fixing. */
IF NOT EXISTS (SELECT 1 FROM iam.permissions
               WHERE module COLLATE DATABASE_DEFAULT = 'KB'    COLLATE DATABASE_DEFAULT
                 AND action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT)
    THROW 50329, 'V329 FAILED: KB:WRITE was not catalogued; the converted guards would admit nobody but PLATFORM_ADMIN.', 1;
GO

/* 3b. Granted to EXACTLY the three intended roles — not a superset. A too-wide
       grant here means any tenant administrator can rewrite the whistleblowing
       procedure, which is the opposite of the intent. */
DECLARE @holders INT = (
    SELECT COUNT(DISTINCT r.role_name)
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE p.module COLLATE DATABASE_DEFAULT = 'KB'    COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT);
DECLARE @intended INT = (
    SELECT COUNT(DISTINCT r.role_name)
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE p.module COLLATE DATABASE_DEFAULT = 'KB'    COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT
      AND r.role_name COLLATE DATABASE_DEFAULT IN (
            'GRC_ADMINISTRATOR'  COLLATE DATABASE_DEFAULT,
            'ORG_ADMIN'          COLLATE DATABASE_DEFAULT,
            'COMPLIANCE_OFFICER' COLLATE DATABASE_DEFAULT));
IF @holders <> 3 OR @intended <> 3
    THROW 50329, 'V329 FAILED: KB:WRITE must be held by exactly GRC_ADMINISTRATOR, ORG_ADMIN and COMPLIANCE_OFFICER.', 1;
GO

/* 3c. No read-only, external or machine persona may author staff guidance. A
       supplier or a SIEM ingest account editing the whistleblowing article is
       the failure this check exists to make impossible. */
IF EXISTS (
    SELECT 1
    FROM iam.role_permissions rp
    JOIN iam.roles r       ON r.id = rp.role_id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE p.module COLLATE DATABASE_DEFAULT = 'KB'    COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT
      AND (r.role_name COLLATE DATABASE_DEFAULT IN (
             'VIEWER'           COLLATE DATABASE_DEFAULT,
             'REGULATOR_VIEWER' COLLATE DATABASE_DEFAULT,
             'EXTERNAL_AUDITOR' COLLATE DATABASE_DEFAULT,
             'SUPPLIER_USER'    COLLATE DATABASE_DEFAULT)
        OR r.role_name COLLATE DATABASE_DEFAULT LIKE '%[_]INGEST' COLLATE DATABASE_DEFAULT))
    THROW 50329, 'V329 FAILED: a read-only, external or machine persona holds KB:WRITE.', 1;
GO

/* 3d. KB:READ must NOT exist. Its absence is a decision, not an oversight —
       reads are isAuthenticated() on purpose (see the header), and a catalogued
       permission that guards no endpoint is F2-13b's documented anti-pattern.
       If a future slice genuinely needs it, it must edit this check and say why. */
IF EXISTS (SELECT 1 FROM iam.permissions
           WHERE module COLLATE DATABASE_DEFAULT = 'KB'   COLLATE DATABASE_DEFAULT
             AND action COLLATE DATABASE_DEFAULT = 'READ' COLLATE DATABASE_DEFAULT)
    THROW 50329, 'V329 FAILED: KB:READ exists. Reads are deliberately isAuthenticated() because the corpus is staff guidance including the whistleblowing and conflict-of-interest procedures; a grant list can only wrongly exclude an employee. Remove it, or amend V329 and ADR-017 D-7 together.', 1;
GO

PRINT N'V329 — KB:WRITE catalogued and granted to GRC_ADMINISTRATOR, ORG_ADMIN, COMPLIANCE_OFFICER; KB:READ deliberately absent; 4 verifications passed.';
GO

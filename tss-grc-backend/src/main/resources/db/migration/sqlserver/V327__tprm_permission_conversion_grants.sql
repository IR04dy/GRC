/* ============================================================================
   V327 — TPRM permissions for the roles its guards named, and TPRM:APPROVE so
          the assessment approval tier survives the conversion.

   THE FINDING (F2-05 slice 2 / F2-52, module `tprm`).
   All 39 pure role guards in this module name `TPRM_LEAD`. It does not exist in
   iam.roles. Six other ghosts appear beside it — PROCUREMENT (the real role is
   PROCUREMENT_OWNER), OPS_MANAGER, INCIDENT_COMMANDER, VENDOR_CONTACT,
   BOARD_MEMBER, AUDIT_COMMITTEE_MEMBER. A role guard is not evaluated by
   ModulePermissionEvaluator, so it gets no PLATFORM_ADMIN bypass; the population
   that passes is exactly the surviving real names. Where TPRM_LEAD was the only
   non-admin leg, that left PLATFORM_ADMIN alone on 8 endpoints:

     VendorBcpCampaignController  open / close / cancel / send-reminders
     VendorBcpAttestationController  submit
     OutsourcingController  delete / record-event / resolve-event

   So the entire vendor-BCP attestation campaign lifecycle — the SAMA outsourcing
   control this module exists to evidence — could only be run by the platform
   administrator. Live-proven before the change: `rmanager` (RISK_MANAGER) got 403
   on campaign open, send-reminders and cancel, and on outsourcing delete.

   WHAT THE CONVERSION NEEDS FROM THE DATABASE. TPRM:READ/WRITE/DELETE are
   catalogued and already used by this module's other two controllers
   (TprmController, QuestionnaireController — 19 guards). But the grant sets and
   the role lists disagree, in BOTH directions:

     TPRM:READ  (13) AUDITOR, CFO, FINANCE_LEAD, GRC_ADMINISTRATOR,
                     INTERNAL_AUDITOR, MA_LEAD, ORG_ADMIN, PROCUREMENT_OWNER,
                     SERVICE_OWNER, SUPPLIER_MANAGER, SUPPLIER_USER,
                     SUSTAINABILITY_OFFICER, VIEWER
     TPRM:WRITE  (4) GRC_ADMINISTRATOR, ORG_ADMIN, PROCUREMENT_OWNER,
                     SUPPLIER_MANAGER

   The role guards, meanwhile, gave read AND write to CISO, RISK_MANAGER,
   COMPLIANCE_OFFICER and BCM_MANAGER — none of which hold either permission.
   Those are the four personas who actually maintain a SAMA outsourcing register:
   risk owns the arrangement, compliance owns the non-objection, BCM owns the exit
   plan and the vendor BCP attestation, the CISO owns third-party cyber. Without
   the grants below, converting the guards would have locked all four out of the
   register they run — a fix that breaks the feature.

   TPRM:APPROVE IS NEW, AND IS THE BEHAVIOUR-PRESERVING OPTION.
   ThirdPartyCyberAssessmentController#approve/#retire guarded on
   hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN') — all three real, so those
   two endpoints were NOT defective. The catalogue has no TPRM:APPROVE, so the
   choices were (a) fold them into TPRM:WRITE, which would widen an approval gate
   to every writer including SUPPLIER_MANAGER, or (b) catalogue the action and
   grant it to exactly the two real roles the guard named. (b) is behaviour-
   identical and is what F2-05's row authorises ("catalogue permissions for the
   role-guarded modules"). Inventing the guard WITHOUT the catalogue row would
   have been F2-23 again: a permanently closed endpoint.

   NOT DONE HERE, deliberately:
     · The seven ghost roles are NOT created. Creating a role is a persona
       decision; the permission catalogue carries the intent.
     · VENDOR_CONTACT is not mapped onto SUPPLIER_USER, and the reason is that
       there is nothing to fix. It looked at first like the vendor-facing half of
       a vendor-facing campaign had been locked out — but reading the controller
       rather than the guard list shows the vendor's real path is
       POST /bcp-attestations/by-token/{token}/submit, which is permitAll() with
       an opaque per-attestation UUID and is untouched by this migration.
       `#submit` on {id} is the INTERNAL manual submit-on-behalf, so the ghost
       cost an internal convenience, not the supplier's ability to attest.
       Granting SUPPLIER_USER TPRM:WRITE would have let a third party write your
       whole third-party register to fix a problem that does not exist.

   Idempotent: NOT EXISTS on every insert; safe to run twice (Flyway is off in
   dev, so it is applied by hand twice per the house convention).
   ============================================================================ */
SET NOCOUNT ON;

/* ── 1. catalogue TPRM:APPROVE ─────────────────────────────────────────────── */
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT 'TPRM', 'APPROVE', 'TPRM_APPROVE',
       'Approve or retire a third-party cyber assessment'
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions
                  WHERE module COLLATE DATABASE_DEFAULT = 'TPRM'    COLLATE DATABASE_DEFAULT
                    AND action COLLATE DATABASE_DEFAULT = 'APPROVE' COLLATE DATABASE_DEFAULT);

DECLARE @beforeRead  INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'TPRM' AND p.action = 'READ');
DECLARE @beforeWrite INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'TPRM' AND p.action = 'WRITE');

/* ── 2. READ + WRITE to the four register owners the role guards named ─────── */
DECLARE @owners TABLE (role_name NVARCHAR(100));
INSERT INTO @owners (role_name)
VALUES ('CISO'), ('RISK_MANAGER'), ('COMPLIANCE_OFFICER'), ('BCM_MANAGER');

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN @owners o ON o.role_name COLLATE DATABASE_DEFAULT = r.role_name COLLATE DATABASE_DEFAULT
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'TPRM' COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT IN ('READ' COLLATE DATABASE_DEFAULT,
                                           'WRITE' COLLATE DATABASE_DEFAULT)
WHERE r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);

/* ── 3. APPROVE to exactly the two roles the old approval guard named ──────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'TPRM'    COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT = 'APPROVE' COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  AND r.role_name COLLATE DATABASE_DEFAULT IN (
        SELECT v.n COLLATE DATABASE_DEFAULT FROM (VALUES ('CISO'), ('RISK_MANAGER')) v(n))
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);

/* ── verification 1: all four register owners hold READ *and* WRITE. Missing
      one means the conversion locks a real persona out of the register. ── */
DECLARE @missing NVARCHAR(400) = (
    SELECT STRING_AGG(x.d, ', ') FROM (
        SELECT o.role_name + ':' + a.act AS d
        FROM @owners o
        CROSS JOIN (VALUES ('READ'), ('WRITE')) a(act)
        WHERE NOT EXISTS (
            SELECT 1 FROM iam.roles r
            JOIN iam.role_permissions rp ON rp.role_id = r.id
            JOIN iam.permissions p ON p.id = rp.permission_id
            WHERE r.role_name COLLATE DATABASE_DEFAULT = o.role_name COLLATE DATABASE_DEFAULT
              AND r.is_deleted = 0
              AND p.module = 'TPRM'
              AND p.action COLLATE DATABASE_DEFAULT = a.act COLLATE DATABASE_DEFAULT)) x);
IF @missing IS NOT NULL
    THROW 50327, 'V327: a role the TPRM guards named still lacks TPRM READ or WRITE — access was lost, not preserved.', 1;

/* ── verification 2: TPRM:APPROVE is held by EXACTLY CISO and RISK_MANAGER.
      The old guard admitted those two plus PLATFORM_ADMIN, which bypasses in
      code — so this set being any wider means the approval tier widened. ── */
DECLARE @approvers NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0 AND p.module = 'TPRM' AND p.action = 'APPROVE') x);
IF @approvers IS NULL OR EXISTS (
        SELECT 1 FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0 AND p.module = 'TPRM' AND p.action = 'APPROVE'
          AND r.role_name NOT IN ('CISO', 'RISK_MANAGER'))
    THROW 50327, 'V327: TPRM:APPROVE is not exactly {CISO, RISK_MANAGER} — the assessment approval tier changed population.', 1;

/* ── verification 3: AUDITOR reads the register and must not write or approve
      it. The pre-change probe measured that 403 and it has to survive. ── */
IF EXISTS (
    SELECT 1 FROM iam.roles r
    JOIN iam.role_permissions rp ON rp.role_id = r.id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.role_name = 'AUDITOR' AND r.is_deleted = 0
      AND p.module = 'TPRM' AND p.action IN ('WRITE', 'APPROVE', 'DELETE'))
    THROW 50327, 'V327: AUDITOR gained TPRM write/approve/delete — an assurance role must not maintain the register it audits.', 1;

/* ── verification 4: no read-only, external or machine persona may write,
      approve or delete. SUPPLIER_USER keeps TPRM:READ/EXPORT by design (the
      supplier portal) and must gain nothing here — see F2-54. ── */
DECLARE @leaked NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT DISTINCT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'TPRM' AND p.action IN ('WRITE', 'APPROVE', 'DELETE')
          AND (r.role_name LIKE '%INGEST'
               OR r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR',
                                  'SUPPLIER_USER'))) x);
IF @leaked IS NOT NULL
    THROW 50327, 'V327: a read-only/external/machine persona holds TPRM write, approve or delete.', 1;

/* ── verification 5: no writer or approver lacks read — the V324 half-grant. ── */
DECLARE @halfGranted NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT DISTINCT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'TPRM' AND p.action IN ('WRITE', 'APPROVE')
          AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                          JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                          WHERE rp2.role_id = r.id
                            AND p2.module = 'TPRM' AND p2.action = 'READ')) x);
IF @halfGranted IS NOT NULL
    THROW 50327, 'V327: a role holds TPRM write/approve without TPRM:READ — it could change a record it cannot open.', 1;

DECLARE @afterRead  INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'TPRM' AND p.action = 'READ');
DECLARE @afterWrite INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'TPRM' AND p.action = 'WRITE');
PRINT CONCAT('V327: TPRM:READ ', @beforeRead, ' -> ', @afterRead,
             ' | TPRM:WRITE ', @beforeWrite, ' -> ', @afterWrite,
             ' | TPRM:APPROVE catalogued, held by ', @approvers, '.');

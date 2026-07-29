/* ============================================================================
   V326 — AUDIT:READ for the three oversight roles the internal-audit plan
          guards named, so converting those guards loses nobody.

   THE FINDING (F2-05, first module: audit_lc/ia_plan).
   IaController's 26 endpoints authorised by role name. Twelve names on the read
   guards, up to five on the writes — and FIVE OF THE TWELVE DO NOT EXIST in
   iam.roles: CHIEF_AUDIT_EXECUTIVE, AUDIT_DIRECTOR, INTERNAL_AUDITOR_LEAD,
   AUDIT_COMMITTEE_MEMBER, BOARD_MEMBER. A role guard is not evaluated by
   ModulePermissionEvaluator, so it does not get the PLATFORM_ADMIN bypass
   either; the population that passed was exactly the surviving real names. On
   11 of the 26 endpoints that was PLATFORM_ADMIN alone — creating the annual
   plan, building the audit universe, transitioning a plan or engagement, bulk
   import, residual recompute. Live-proven before the change: `auditor`
   (AUDITOR + INTERNAL_AUDITOR) got 403 on POST /api/v1/ia/plan.

   WHY THIS MIGRATION IS SMALL. The conversion target was not a design decision:
   AUDIT:READ/WRITE/DELETE is already catalogued, already granted, and already
   used by the two sibling controllers over the same domain
   (AuditLifecycleController on /api/audit-lc and audit_eng's
   AuditEngagementController). So the code change is a repoint, and the only
   thing the database owes is the difference between the two populations.

   THE DIFFERENCE, MEASURED. Roles that the old READ guard admitted and that
   AUDIT:READ did not already cover:

     CISO                 read the audit plan as a named role, no AUDIT:READ
     RISK_MANAGER         same
     COMPLIANCE_OFFICER   same

   Those three are granted below. Nothing is revoked. The conversion also widens
   reads to BOARD_SECRETARY, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN
   and VIEWER, which needs no migration because they already hold AUDIT:READ —
   and already read the sibling register at /api/audit-lc/annual-plans, so the
   widening makes two views of the same domain agree rather than opening a new
   one.

   WHAT IS DELIBERATELY NOT DONE HERE:
     · The five ghost roles are NOT created. Creating a role is a persona
       decision (which permissions, which tenant, which SoD implications), not a
       side-effect of a guard conversion. The intent they encoded — senior
       internal audit may plan the year — is now carried by AUDIT:WRITE, which
       INTERNAL_AUDITOR already holds.
     · AUDIT:APPROVE is NOT added to the catalogue. Plan approval stays a WRITE
       in both registers, exactly as AuditLifecycleController has always had it.
       Whether approving the annual audit plan deserves its own permission and a
       four-eyes gate is a product question; inventing the permission here would
       repeat F2-23 (a guard on an uncatalogued permission is permanently
       closed).

   Idempotent: NOT EXISTS on the insert; safe to run twice (Flyway is off in dev,
   so it is applied by hand twice per the house convention).
   ============================================================================ */
SET NOCOUNT ON;

DECLARE @before INT = (
    SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module COLLATE DATABASE_DEFAULT = 'AUDIT' COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = 'READ'  COLLATE DATABASE_DEFAULT);

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, ar.id
FROM iam.roles r
JOIN iam.permissions ar
  ON ar.module COLLATE DATABASE_DEFAULT = 'AUDIT' COLLATE DATABASE_DEFAULT
 AND ar.action COLLATE DATABASE_DEFAULT = 'READ'  COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  AND r.role_name COLLATE DATABASE_DEFAULT IN (
        SELECT v.n COLLATE DATABASE_DEFAULT
        FROM (VALUES ('CISO'), ('RISK_MANAGER'), ('COMPLIANCE_OFFICER')) v(n))
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = ar.id);

/* ── verification 1: all three named roles now hold AUDIT:READ. They exist —
      unlike the five the guards recited — so a miss here means the insert is
      wrong, not that the role is absent. ── */
DECLARE @missing NVARCHAR(400) = (
    SELECT STRING_AGG(x.n, ', ') FROM (
        SELECT v.n FROM (VALUES ('CISO'), ('RISK_MANAGER'), ('COMPLIANCE_OFFICER')) v(n)
        WHERE NOT EXISTS (
            SELECT 1 FROM iam.roles r
            JOIN iam.role_permissions rp ON rp.role_id = r.id
            JOIN iam.permissions p ON p.id = rp.permission_id
            WHERE r.role_name COLLATE DATABASE_DEFAULT = v.n COLLATE DATABASE_DEFAULT
              AND r.is_deleted = 0
              AND p.module = 'AUDIT' AND p.action = 'READ')) x);
IF @missing IS NOT NULL
    THROW 50326, 'V326: a role the old IA read guard named still lacks AUDIT:READ — access was lost, not preserved.', 1;

/* ── verification 2: this migration must not touch write authority. The
      conversion moves 13 writes and 3 deletes onto AUDIT:WRITE/DELETE, whose
      holder set is deliberately unchanged (GRC_ADMINISTRATOR, INTERNAL_AUDITOR,
      ORG_ADMIN). If a read-only or external persona has picked up either, the
      widening went further than intended. ── */
DECLARE @leaked NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT DISTINCT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'AUDIT' AND p.action IN ('WRITE', 'DELETE')
          AND (r.role_name LIKE '%INGEST'
               OR r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR',
                                  'SUPPLIER_USER', 'AUDITOR'))) x);
IF @leaked IS NOT NULL
    THROW 50326, 'V326: a read-only/external persona holds AUDIT:WRITE or AUDIT:DELETE — the IA conversion would hand it plan authority.', 1;

/* ── verification 3: every AUDIT:WRITE holder can also read. A role that may
      create an audit plan but not list one is the V324 half-grant. ── */
DECLARE @halfGranted NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0
          AND p.module = 'AUDIT' AND p.action = 'WRITE'
          AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                          JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                          WHERE rp2.role_id = r.id
                            AND p2.module = 'AUDIT' AND p2.action = 'READ')) x);
IF @halfGranted IS NOT NULL
    THROW 50326, 'V326: a role holds AUDIT:WRITE without AUDIT:READ — it could create an audit plan it cannot open.', 1;

DECLARE @after INT = (
    SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'AUDIT' AND p.action = 'READ');
PRINT CONCAT('V326: AUDIT:READ holders ', @before, ' -> ', @after,
             ' (CISO/RISK_MANAGER/COMPLIANCE_OFFICER keep the IA plan read they had by role name).');

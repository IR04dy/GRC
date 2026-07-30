/* ============================================================================
   V328 — FRAUD enters the permission catalogue, so the fraud case lifecycle can
          be operated by someone other than the platform administrator.

   THE FINDING (F2-05 slice 3 / F2-52, module `fraud`).
   FraudController's ten guards live in two CONSTANTS rather than ten
   annotations:

     READ_GUARD  = hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR',
                              'COMPLIANCE_OFFICER','INTERNAL_AUDITOR','RISK_MANAGER')
     WRITE_GUARD = hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR')

   FRAUD_OFFICER and FRAUD_INVESTIGATOR do not exist in iam.roles. A role guard
   is not evaluated by ModulePermissionEvaluator, so it gets no PLATFORM_ADMIN
   bypass either; the population that passes is exactly the surviving real names.
   On WRITE_GUARD those two ghosts were the ONLY non-admin legs, so the entire
   fraud case lifecycle — open a case, triage it, record an update, set the
   outcome, close it — was PLATFORM_ADMIN-only. Live-proven before the change:
   `compliance` (COMPLIANCE_OFFICER + GRC_ADMINISTRATOR, 86 of 96 permissions)
   got 403 opening a case; `admin` got 201. In a Saudi bank, fraud case handling
   being available only to the platform administrator is both an operational
   dead end and an SoD problem.

   (The constants are also why this needed reflection to find: F2-27 recorded that
   a source regex missed 8 constant-valued guards. These are two more.)

   WHY THIS MIGRATION IS BIGGER THAN V326/V327. Those two modules already had
   their permission catalogued and granted — the conversion inherited a target.
   `FRAUD` is not in iam.permissions at all. So this migration must CREATE the
   permission before anything can be granted, and "grant to exactly the roles
   previously named" cannot be applied to the write tier, because the only two
   roles it named do not exist.

   THE GRANTS, AND WHERE EACH COMES FROM.
     FRAUD:READ  -> COMPLIANCE_OFFICER, INTERNAL_AUDITOR, RISK_MANAGER
                    ...exactly the three REAL roles READ_GUARD named. Behaviour-
                    identical: no reader gains or loses. Note VIEWER is NOT
                    included — unlike the AUDIT and TPRM conversions, where VIEWER
                    already held the module's READ, nothing here grants it, so the
                    read population does not widen at all.
                 -> plus GRC_ADMINISTRATOR and ORG_ADMIN, because a role that may
                    write must be able to read (the V324 half-grant rule, fourth
                    migration running).
     FRAUD:WRITE -> GRC_ADMINISTRATOR and ORG_ADMIN, DERIVED not chosen: they are
                    the only two roles in the platform holding >= 80% of module
                    WRITE actions (measured: ORG_ADMIN 24 of 26, GRC_ADMINISTRATOR
                    21 of 26; the next is COMPLIANCE_OFFICER at 14). They are the
                    tenant's own administrators, and every other module's WRITE
                    already includes them.

   WHAT IS DELIBERATELY LEFT OPEN — and is now cheap to close.
   Which persona *investigates* fraud is a product decision this migration does
   not take. FRAUD_OFFICER and FRAUD_INVESTIGATOR are not created (creating a role
   is a persona decision: which permissions, which tenant, which SoD
   implications), and no existing role is anointed as the fraud investigator —
   FORENSICS_LEAD, IR_LEAD and SECURITY_ANALYST all exist and all are plausible,
   which is exactly why picking one here would be guessing. The change that
   matters is that the answer is now a checkbox in the role editor instead of a
   Java edit and a jar: `FRAUD:WRITE` exists and can be granted to whoever the
   customer decides. Before this migration that sentence was not true.

   COMPLIANCE_OFFICER deliberately does NOT get FRAUD:WRITE. It reads the register
   today and the old guard did not let it write; granting write here would widen
   the tier on my own judgement about where fraud investigation sits, which is the
   decision being left open above.

   Idempotent: NOT EXISTS on every insert; safe to run twice (Flyway is off in
   dev, so it is applied by hand twice per the house convention).
   ============================================================================ */
SET NOCOUNT ON;

/* ── 1. catalogue FRAUD:READ and FRAUD:WRITE ───────────────────────────────── */
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.pname, v.descr
FROM (VALUES
    ('FRAUD', 'READ',  'FRAUD_READ',  'View fraud cases, typologies and the fraud dashboard'),
    ('FRAUD', 'WRITE', 'FRAUD_WRITE', 'Open, triage, update, resolve and close fraud cases')
) v(module, action, pname, descr)
WHERE NOT EXISTS (
    SELECT 1 FROM iam.permissions p
    WHERE p.module COLLATE DATABASE_DEFAULT = v.module COLLATE DATABASE_DEFAULT
      AND p.action COLLATE DATABASE_DEFAULT = v.action COLLATE DATABASE_DEFAULT);

/* ── 2. FRAUD:READ to the three real roles the old guard named, plus the two
      administrators that must read what they may write ────────────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'FRAUD' COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT = 'READ'  COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  AND r.role_name COLLATE DATABASE_DEFAULT IN (
        SELECT v.n COLLATE DATABASE_DEFAULT FROM (VALUES
            ('COMPLIANCE_OFFICER'), ('INTERNAL_AUDITOR'), ('RISK_MANAGER'),
            ('GRC_ADMINISTRATOR'), ('ORG_ADMIN')) v(n))
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);

/* ── 3. FRAUD:WRITE to the two tenant administrators, derived above ────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p
  ON p.module COLLATE DATABASE_DEFAULT = 'FRAUD' COLLATE DATABASE_DEFAULT
 AND p.action COLLATE DATABASE_DEFAULT = 'WRITE' COLLATE DATABASE_DEFAULT
WHERE r.is_deleted = 0
  AND r.role_name COLLATE DATABASE_DEFAULT IN (
        SELECT v.n COLLATE DATABASE_DEFAULT
        FROM (VALUES ('GRC_ADMINISTRATOR'), ('ORG_ADMIN')) v(n))
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);

/* ── verification 1: both permissions exist. Everything else is meaningless
      without this, and a guard on an uncatalogued permission is F2-23. ── */
IF (SELECT COUNT(*) FROM iam.permissions WHERE module = 'FRAUD' AND action IN ('READ', 'WRITE')) <> 2
    THROW 50328, 'V328: FRAUD:READ / FRAUD:WRITE were not catalogued — the guards would be permanently closed.', 1;

/* ── verification 2: the three roles the old READ_GUARD named keep their read.
      They are real, so a miss means the insert is wrong, not the role absent. ── */
DECLARE @lostRead NVARCHAR(400) = (
    SELECT STRING_AGG(x.n, ', ') FROM (
        SELECT v.n FROM (VALUES ('COMPLIANCE_OFFICER'), ('INTERNAL_AUDITOR'), ('RISK_MANAGER')) v(n)
        WHERE NOT EXISTS (
            SELECT 1 FROM iam.roles r
            JOIN iam.role_permissions rp ON rp.role_id = r.id
            JOIN iam.permissions p ON p.id = rp.permission_id
            WHERE r.role_name COLLATE DATABASE_DEFAULT = v.n COLLATE DATABASE_DEFAULT
              AND r.is_deleted = 0 AND p.module = 'FRAUD' AND p.action = 'READ')) x);
IF @lostRead IS NOT NULL
    THROW 50328, 'V328: a role the fraud READ guard named lost its read — access was lost, not preserved.', 1;

/* ── verification 3: FRAUD:WRITE is EXACTLY the two administrators. Wider means
      this migration decided who investigates fraud, which it must not. ── */
IF EXISTS (
    SELECT 1 FROM iam.roles r
    JOIN iam.role_permissions rp ON rp.role_id = r.id
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE r.is_deleted = 0 AND p.module = 'FRAUD' AND p.action = 'WRITE'
      AND r.role_name NOT IN ('GRC_ADMINISTRATOR', 'ORG_ADMIN'))
    THROW 50328, 'V328: FRAUD:WRITE is not exactly {GRC_ADMINISTRATOR, ORG_ADMIN} — the fraud-investigator decision was pre-empted.', 1;

/* ── verification 4: no read-only, external or machine persona holds either.
      VIEWER in particular must NOT gain fraud read — the earlier conversions
      widened reads because VIEWER already held those permissions; here it does
      not, so the read population must be unchanged. ── */
DECLARE @leaked NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT DISTINCT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0 AND p.module = 'FRAUD'
          AND (r.role_name LIKE '%INGEST'
               OR r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR',
                                  'SUPPLIER_USER'))) x);
IF @leaked IS NOT NULL
    THROW 50328, 'V328: a read-only/external/machine persona holds a FRAUD permission — fraud cases name suspects.', 1;

/* ── verification 5: no writer lacks read — the V324 half-grant rule. ── */
DECLARE @halfGranted NVARCHAR(400) = (
    SELECT STRING_AGG(x.role_name, ', ') FROM (
        SELECT DISTINCT r.role_name FROM iam.roles r
        JOIN iam.role_permissions rp ON rp.role_id = r.id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE r.is_deleted = 0 AND p.module = 'FRAUD' AND p.action = 'WRITE'
          AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                          JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                          WHERE rp2.role_id = r.id
                            AND p2.module = 'FRAUD' AND p2.action = 'READ')) x);
IF @halfGranted IS NOT NULL
    THROW 50328, 'V328: a role holds FRAUD:WRITE without FRAUD:READ — it could open a case it cannot list.', 1;

DECLARE @rd INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'FRAUD' AND p.action = 'READ');
DECLARE @wr INT = (SELECT COUNT(DISTINCT rp.role_id)
    FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
    JOIN iam.roles r ON r.id = rp.role_id AND r.is_deleted = 0
    WHERE p.module = 'FRAUD' AND p.action = 'WRITE');
PRINT CONCAT('V328: FRAUD catalogued. FRAUD:READ held by ', @rd, ' roles, FRAUD:WRITE by ', @wr,
             ' (was: no permission existed, and the write tier was PLATFORM_ADMIN only).');

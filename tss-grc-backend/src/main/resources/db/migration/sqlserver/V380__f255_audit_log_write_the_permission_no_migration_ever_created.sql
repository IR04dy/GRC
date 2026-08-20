-- V380 — F2-255: AUDIT_LOG:WRITE is cited by a live guard and created by no migration in the platform.
--
-- A ROLE NOBODY HOLDS HAS AN ADMINISTRATOR REMEDY. A PERMISSION NOBODY CAN HOLD DOES NOT.
-- F2-253 measured 24 guards left "admitting PLATFORM_ADMIN alone, by attrition" because they recite a role
-- assigned to no user, and noted that a tenant administrator can fix that without a jar — "a remedy, not a
-- defence". This is the same lockout one dimension across, and the remedy is gone: nothing can be granted
-- because there is nothing in iam.permissions to grant.
--
--     GET  /api/v1/audit-log/integrity       @PreAuthorize hasPermission('AUDIT_LOG','READ')
--     POST /api/v1/audit-log/integrity/seal  @PreAuthorize hasPermission('AUDIT_LOG','WRITE')
--
-- V272 catalogued ('AUDIT_LOG','READ'); V320 added ('AUDIT_LOG','EXPORT'). `AUDIT_LOG:WRITE` appears in NO
-- migration in the packaged set, and in neither database — dev holds 140 (module, action) pairs and prod 114,
-- and it is absent from both. So `ModulePermissionEvaluator` can only ever answer false for it, and the route
-- reduces to the PLATFORM_ADMIN code bypass. Probed live before this file: `admin` seals 5 rows at 200 while
-- GRC_ADMINISTRATOR, CISO and AUDITOR — all three holding AUDIT_LOG:READ and all three returning 200 on
-- /integrity — are refused the seal with 403.
--
-- WHY THAT PARTICULAR ROUTE MATTERS, IN ITS OWN WORDS. Its javadoc: "It exists so the control can be
-- EXERCISED rather than only observed: a sealing pass whose only trigger is a timer is a control nobody can
-- demonstrate on request, and DF-1 showed what an unobserved scheduled sweep costs." The route was built for
-- the sole purpose of being demonstrable by whoever is asked, and the permission it demands cannot be held by
-- anyone the request would be put to. The observe half of the same F2-31 control, in the method immediately
-- above it, is catalogued and has four live holders. Two adjacent methods, one permission each, and the one
-- that was catalogued is the one that already worked.
--
-- WHAT THIS FILE DOES
--   1. Catalogues ('AUDIT_LOG','WRITE').
--   2. Grants it to ORG_ADMIN, GRC_ADMINISTRATOR and CISO, as literal role names.
--
-- WHO IS GRANTED, AND WHY THIS IS A NARROWING OF THE READ TIER RATHER THAN A PROMOTION OF IT.
-- AUDIT_LOG:READ and AUDIT_LOG:EXPORT are held by the same six roles: AUDITOR, CISO, GRC_ADMINISTRATOR,
-- IAM_LEAD, INTERNAL_AUDITOR, ORG_ADMIN (four with a live holder in dev and prod alike). V320 granted EXPORT
-- to that whole tier, which is coherent because exporting is read-shaped: you export what you may read.
-- Sealing is not read-shaped, and V362's landmine is precisely this — it classified actions by method shape
-- and promoted whole WRITE tiers to APPROVE in four of the six modules it touched, which V367 and V368 then
-- had to undo. So the grant is decided per role:
--
--     ORG_ADMIN           GRANTED  — the tenant's own administrator; operating the governance substrate is
--                                    the job.
--     GRC_ADMINISTRATOR   GRANTED  — the GRC function that runs the platform day to day.
--     CISO                GRANTED  — accountable for tamper-evidence of the audit trail under NCA ECC, and
--                                    the person most likely to be asked to demonstrate it.
--     AUDITOR             WITHHELD — the external-audit persona. An examiner asks for the demonstration and
--                                    watches it; they are not the party that writes to the ledger.
--     INTERNAL_AUDITOR    WITHHELD — reads the ledger in order to audit it. Operating the seal would make
--                                    internal audit the author of the evidence it then examines.
--     IAM_LEAD            WITHHELD — its AUDIT_LOG:READ exists for reviewing identity events, not for
--                                    operating the ledger.
--     PLATFORM_ADMIN      WITHHELD — V272's own header states the convention: "PLATFORM_ADMIN needs no
--                                    grants (evaluator bypass)". No AUDIT_LOG action grants it.
--
-- Widening this — an internal auditor who may seal, or an external auditor who may — is an owner's decision
-- and is filed rather than smuggled in under a catalogue fix. What is NOT a decision is that the route
-- currently admits nobody but the platform administrator and cannot be configured to admit anyone else.
--
-- Sealing cannot rewrite history: `sealTenant` computes the hash chain over rows that carry no hash yet. The
-- worst a holder can do is seal earlier than the periodic pass would have. That is why the grant is about who
-- can DEMONSTRATE the control rather than about who is trusted with the ledger's contents.
--
-- Idempotent: every statement is existence-guarded and the self-checks assert the resulting PROPERTY, so a
-- second application is a no-op that still verifies. Applied twice to the dev database via pyodbc before
-- commit (Flyway is off in dev).

-- ── 1. The action the guard has always demanded ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AUDIT_LOG' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    -- description_en is NVARCHAR(300) — resolved from sys.columns, not assumed. The first draft of this
    -- file was 520 characters and failed with SQL Server 2628 (truncation), which names the column but not
    -- the limit.
    VALUES (N'AUDIT_LOG', N'WRITE', N'AUDIT_LOG_WRITE',
            N'Seal this tenant''s unsealed audit-ledger rows on demand, so the F2-31 tamper-evidence '
          + N'control can be exercised in front of an examiner and not only observed. Mutates the three '
          + N'integrity columns; it cannot alter or remove a recorded event. See V380 for who is '
          + N'deliberately excluded.');
GO

-- ── 2. Granted to exactly the three roles that operate this platform's governance ──────
-- Literal role names, joined to the catalogue AFTER step 1 has created it. Deriving the pair from
-- iam.role_permissions would grant nothing and report success: NOT EXISTS cannot tell "already granted"
-- from "nothing matched".
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'AUDIT_LOG' AND p.action = N'WRITE'
   AND r.is_deleted = 0
   AND r.role_name IN (N'ORG_ADMIN', N'GRC_ADMINISTRATOR', N'CISO')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- ── Self-checks: the property, not the statement ───────────────────────────────────────
-- THROW takes a literal or a variable, never a concatenation.
DECLARE @catalogued INT;
DECLARE @cataloguedMsg NVARCHAR(400) =
    N'V380 failed: AUDIT_LOG:WRITE is still absent from iam.permissions. POST /audit-log/integrity/seal '
  + N'cites it, so without this row the F2-31 seal control admits the PLATFORM_ADMIN code bypass and '
  + N'nobody else, and no tenant administrator can grant it to the person asked to demonstrate it.';
SELECT @catalogued = COUNT(*)
  FROM iam.permissions WHERE module = N'AUDIT_LOG' AND action = N'WRITE';
IF @catalogued <> 1 THROW 50000, @cataloguedMsg, 1;
GO

DECLARE @granted INT;
DECLARE @grantedMsg NVARCHAR(400) =
    N'V380 failed: AUDIT_LOG:WRITE is not granted to exactly ORG_ADMIN, GRC_ADMINISTRATOR and CISO. A '
  + N'catalogued permission nobody holds is the same lockout expressed one layer down.';
SELECT @granted = COUNT(DISTINCT r.role_name)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
  JOIN iam.roles r ON r.id = rp.role_id
 WHERE p.module = N'AUDIT_LOG' AND p.action = N'WRITE';
IF @granted <> 3 THROW 50000, @grantedMsg, 1;
GO

-- Containment: nobody may seal a ledger they cannot read.
DECLARE @orphan INT;
DECLARE @orphanMsg NVARCHAR(400) =
    N'V380 failed: a role holds AUDIT_LOG:WRITE without AUDIT_LOG:READ. Sealing a ledger you cannot read '
  + N'is not a tier, it is a gap — the seal reports how many rows it hashed and the holder cannot then '
  + N'inspect what it did.';
SELECT @orphan = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = N'AUDIT_LOG' AND p.action = N'WRITE'
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                     JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                    WHERE rp2.role_id = rp.role_id
                      AND p2.module = N'AUDIT_LOG' AND p2.action = N'READ');
IF @orphan <> 0 THROW 50000, @orphanMsg, 1;
GO

-- Strictness: WRITE is a NARROWING of the READ tier, not a promotion of it. Containment alone is satisfied
-- by granting the whole tier, which is exactly V362's defect, so the gradient is asserted separately.
DECLARE @strict INT;
DECLARE @strictMsg NVARCHAR(400) =
    N'V380 failed: AUDIT_LOG:WRITE is held by as many roles as AUDIT_LOG:READ. Equal populations satisfy '
  + N'the containment check above while expressing no separation at all — V362 promoted four whole WRITE '
  + N'tiers to APPROVE that way and V367/V368 had to undo two of them.';
SELECT @strict = CASE WHEN
        (SELECT COUNT(DISTINCT r.role_name) FROM iam.role_permissions rp
           JOIN iam.permissions p ON p.id = rp.permission_id
           JOIN iam.roles r ON r.id = rp.role_id
          WHERE p.module = N'AUDIT_LOG' AND p.action = N'WRITE')
      < (SELECT COUNT(DISTINCT r.role_name) FROM iam.role_permissions rp
           JOIN iam.permissions p ON p.id = rp.permission_id
           JOIN iam.roles r ON r.id = rp.role_id
          WHERE p.module = N'AUDIT_LOG' AND p.action = N'READ')
    THEN 1 ELSE 0 END;
IF @strict <> 1 THROW 50000, @strictMsg, 1;
GO

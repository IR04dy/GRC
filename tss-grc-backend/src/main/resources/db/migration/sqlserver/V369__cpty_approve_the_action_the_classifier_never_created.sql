-- V369 — F2-145(e) slice 4 prerequisite: CPTY:APPROVE, the action V362's classifier never created.
--
-- THE DEFECT, AND IT IS THE MIRROR IMAGE OF V368's. V362 derived each module's action set from its
-- endpoints: the HTTP verb, "or APPROVE when the method is approve/reject/transition/decide/sign-shaped".
-- `CptyController` guards two endpoints with APPROVE_GUARD — `assignRating` (POST
-- /counterparties/{id}/ratings) and `upsertLimit` (POST /limits) — and **neither method name matches that
-- shape**. So the classifier read both as plain writes, catalogued CPTY with READ and WRITE only, and
-- folded the APPROVE tier's population into WRITE:
--
--     guard APPROVE_GUARD  -> PLATFORM_ADMIN, CRO (seated nowhere: a ghost), CFO
--     catalogue APPROVE    -> DOES NOT EXIST
--     catalogue WRITE      -> CFO, CREDIT_OFFICER, RISK_MANAGER
--                             ^^^ CFO is named on the READ and APPROVE guards, never on WRITE
--
-- V368 recorded the same classifier promoting a WRITE tier to APPROVE. Here it demoted an APPROVE tier to
-- WRITE. **One cause, both directions, and the direction depends on nothing but whether a method happens
-- to be named like a transition.** That is the clearest statement yet of why "derived from the guards" is
-- not "faithful to the guards".
--
-- ⚠ WHY THIS BLOCKS THE CONVERSION, AND WHY THE OBVIOUS SHORTCUT IS THE DANGEROUS ONE. F2-145(e) cannot
-- convert `APPROVE_GUARD` while no CPTY:APPROVE exists. The tempting move is to point it at the
-- CPTY:WRITE that does exist — and that would take the approval population from {PLATFORM_ADMIN, CFO} to
-- {PLATFORM_ADMIN, CFO, CREDIT_OFFICER, RISK_MANAGER}, i.e. **a credit officer could assign the internal
-- credit rating on their own counterparty and set their own large-exposure limit**. Under SAMA's
-- large-exposure and credit-risk rules that separation is the control. A missing catalogue entry is not a
-- gap that makes a conversion impossible; it is a gap that makes the WRONG conversion easy.
--
-- WHAT THIS FILE DOES.
--   1. Catalogues CPTY:APPROVE, described by what the guarded endpoints actually do.
--   2. Grants it to CFO — the only role named on APPROVE_GUARD that exists. CRO is seated nowhere, so
--      there is nothing to grant it; PLATFORM_ADMIN needs no grant because hasPermission bypasses in code.
--   3. Revokes CFO's CPTY:WRITE, which no WRITE guard has ever conferred.
--   4. Corrects the WRITE description, which claims "credit ratings and exposure limits" — both of which
--      are the APPROVE tier. It is the classifier's belief written down, and V368 showed such a
--      description agreeing with a wrong grant and contradicting the guard.
--
-- After it, all three CPTY populations equal their guards' surviving names exactly, so the conversion in
-- the accompanying commit changes no request's outcome.
--
-- ⚠ WHAT THIS DOES NOT FIX, AND MUST NOT BE READ AS FIXING. CFO is held by **0 active users** in dev, so
-- CPTY:APPROVE will be reachable only by the platform administrator's in-code bypass. That is **exactly
-- true today** — APPROVE_GUARD names PLATFORM_ADMIN, a ghost, and a role nobody holds — so this migration
-- PRESERVES a narrowness rather than creating one, and refusing to ship it would leave the same narrowness
-- hidden inside a name list instead of visible in a grant table. The narrowness itself is F2-52's
-- attrition shape and belongs to tenant provisioning: assign CFO to a real person, or seat CRO. Recorded
-- in F2-145, not smuggled in here. Distinguishing "this migration caused a lockout" from "this migration
-- revealed one" is the whole reason the population was measured before the edit rather than after.
--
-- REVERSE RECIPE:
--   DELETE rp FROM iam.role_permissions rp
--     JOIN iam.permissions p ON p.id = rp.permission_id
--    WHERE p.module = 'CPTY' AND p.action = 'APPROVE';
--   DELETE FROM iam.permissions WHERE module = 'CPTY' AND action = 'APPROVE';
--   INSERT INTO iam.role_permissions (role_id, permission_id)
--   SELECT r.id, p.id FROM iam.roles r, iam.permissions p
--    WHERE r.role_name = 'CFO' AND p.module = 'CPTY' AND p.action = 'WRITE';
--   -- (WRITE description: see V362 for the original text.)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. The action the classifier never created ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CPTY' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CPTY', N'APPROVE', N'CPTY_APPROVE',
            N'Assign a counterparty''s internal credit rating and set or raise its large-exposure limit.');
GO

-- ── 2. Granted to the one seated role the APPROVE guard names ──────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                 JOIN iam.roles r       ON r.id = rp.role_id
                 JOIN iam.permissions p ON p.id = rp.permission_id
                WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id
      FROM iam.roles r, iam.permissions p
     WHERE r.role_name = N'CFO' AND r.is_deleted = 0
       AND p.module = N'CPTY' AND p.action = N'APPROVE';
GO

-- ── 3. The mis-derived write grant, revoked ────────────────────────────────────────────
DELETE rp
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'WRITE';
GO

-- ── 4. The description that described the classifier's tiers, not the controller's ─────
UPDATE iam.permissions
   SET description_en = N'Create and maintain counterparty records, and record rating actions and limit '
                      + N'utilisation.'
 WHERE module = N'CPTY' AND action = N'WRITE';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @approve INT;
DECLARE @approveMsg NVARCHAR(400) =
    N'V369 failed: CPTY:APPROVE is not held by CFO. Without it the conversion of CptyController''s '
  + N'APPROVE guards has nothing faithful to point at, and pointing them at CPTY:WRITE would let a '
  + N'credit officer rate and limit its own counterparty.';
SELECT @approve = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'APPROVE';
IF @approve <> 1 THROW 50000, @approveMsg, 1;
GO

-- CFO must NOT hold WRITE: it is named on the READ and APPROVE guards and on no WRITE guard.
DECLARE @stillWrites INT;
DECLARE @stillWritesMsg NVARCHAR(400) =
    N'V369 failed: CFO still holds CPTY:WRITE, a grant no WRITE guard in the module ever conferred. '
  + N'Converting with it in place would widen the write tier.';
SELECT @stillWrites = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'WRITE';
IF @stillWrites > 0 THROW 50000, @stillWritesMsg, 1;
GO

-- CFO must KEEP read: it is on the READ guard, and an approver who cannot see the register it approves
-- into is a lockout dressed as a control. This also fails loudly if V362 has not run.
DECLARE @canRead INT;
DECLARE @canReadMsg NVARCHAR(400) =
    N'V369 failed: CFO does not hold CPTY:READ. Either the revocation over-reached, or V362 has not been '
  + N'applied to this database.';
SELECT @canRead = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'READ';
IF @canRead <> 1 THROW 50000, @canReadMsg, 1;
GO

-- The write tier must still have holders — the two roles the WRITE guard actually names.
DECLARE @writers INT;
DECLARE @writersMsg NVARCHAR(400) =
    N'V369 failed: CPTY:WRITE has fewer than the two role holders its guard names (RISK_MANAGER, '
  + N'CREDIT_OFFICER). Removing CFO must not have removed anybody else.';
SELECT @writers = COUNT(DISTINCT rp.role_id)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = N'CPTY' AND p.action = N'WRITE';
IF @writers < 2 THROW 50000, @writersMsg, 1;
GO

-- And the corrected description must be in place: an UPDATE that matched no row reports success just as
-- loudly as one that worked.
DECLARE @stale INT;
DECLARE @staleMsg NVARCHAR(400) =
    N'V369 failed: the CPTY WRITE description still claims credit ratings and exposure limits, both of '
  + N'which are the APPROVE tier. A permission description that contradicts its guard misleads.';
SELECT @stale = COUNT(*)
  FROM iam.permissions
 WHERE module = N'CPTY' AND action = N'WRITE' AND description_en NOT LIKE N'%limit utilisation%';
IF @stale > 0 THROW 50000, @staleMsg, 1;
GO

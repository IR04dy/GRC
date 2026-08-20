-- V381 — F2-258: keep CIO and CRO in the audience the AI-lifecycle guards just stopped naming.
--
-- WHY THIS FILE EXISTS. F2-258 converts AiLifecycleController's write and approve tiers from two role
-- guards to hasPermission('AI','WRITE'). The retired lists were
--
--     creates      hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CIO','DPO')
--     transitions  hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO')
--
-- and of those names DPO already holds AI:WRITE while CIO and CRO hold NO module permission at all —
-- measured, 0 grants of any module in dev, and V373's own header filed that consequence in those terms:
-- "a user holding only CIO or CRO passes these 79 guards and holds no module permission, so the
-- permission-driven parts of the SPA show them nothing". So converting without this file would NARROW the
-- shipped audience by two roles, and narrowing a shipped default is a policy change rather than a fix.
-- This grant is a translation, not a provisioning decision: it grants exactly the capability the guard being
-- replaced already declared these two roles have, on exactly the routes it declared it on.
--
-- WHY V373 GRANTED NOTHING AND WHY THAT REASONING DOES NOT REACH THIS MODULE. V373 seated CIO and CRO so
-- that 79 hasAnyRole sites would become satisfiable, and said: "of the five modules involved only AI is
-- catalogued at all, where AiLifecycleController is role-guarded anyway, so any grant would be inert." That
-- is circular — the grant was inert BECAUSE the guard was a role guard, which is the half that could change.
-- Once the guard consults the catalogue the grant is the only thing that carries these two roles, and it is
-- the opposite of V373's stated worry ("authoring an access policy from a role's job title"): the policy was
-- authored by whoever wrote the guard, and this file merely makes it expressible in iam.role_permissions.
-- CIO's own V373 description_en reads "Accountable executive for technology: AI/ML lifecycle gates, data
-- classification and cloud/outsourcing arrangements" — the capability is in the role's own definition.
--
-- WHAT IS GRANTED, AND THE ONE MEASURED WIDENING.
--     CIO  <- AI:READ, AI:WRITE
--     CRO  <- AI:READ, AI:WRITE
-- AI:WRITE guards 13 routes once F2-258 lands: the 6 AI-lifecycle routes these two were already named on,
-- plus 7 on AiSuggestionController (accept / reject / apply an AI-generated GRC suggestion), which they were
-- NOT. That is a real widening and it is stated rather than left as a side effect (the V362 landmine).
-- It is in remit for both — the CIO for AI/ML governance and the CRO for AI-generated risk suggestions — and
-- it is the granularity the catalogue offers: AI has one WRITE, not one per controller. AI:READ is granted
-- with it because a principal that may write what it cannot read is F2-253's write-without-read hole; it adds
-- 2 read routes on the same suggestion controller.
--     PLATFORM_ADMIN  WITHHELD — V272's convention: "PLATFORM_ADMIN needs no grants (evaluator bypass)".
--     AUDITOR         UNTOUCHED — it holds AI:EXPORT and deliberately NOT AI:READ (the only role in either
--                     database with that shape), which is why F2-258 leaves the lifecycle READ tier as a role
--                     guard: converting it would drop the examiner. Do not "tidy" that into a READ grant
--                     without deciding whether an examiner may read every AI model record.
--     AI:EXPORT       NOT granted to CIO or CRO. The retired guards named neither on an export route, and
--                     MlModelController's EXPORT_GUARD ANDs its own role list which names neither.
--
-- LIVE EFFECT TODAY: NONE, and that is the point. CIO and CRO are held by no user in either database, so this
-- changes no live audience — it changes what a tenant administrator gets when they assign one of them, which
-- before this file was "these 79 role guards and no permission-driven screen at all".
--
-- Idempotent: each grant is guarded on its own (role, permission) pair and re-runnable. Verified at the end
-- by re-reading iam.role_permissions and THROWing, because a guarded insert that silently did nothing looks
-- identical to one that worked — and per the repo's grant landmine the triples are declared as LITERALS here
-- rather than derived from iam.role_permissions, which cannot tell "already granted" from "nothing matched".

SET NOCOUNT ON;
GO

-- ── 1. Anti-vacuity FIRST. A grant chain whose role or permission is catalogued later inserts nothing
--       and reports success, so refuse to proceed rather than silently no-op. ────────────────────────
DECLARE @roles INT = (SELECT COUNT(*) FROM iam.roles
                       WHERE role_name IN (N'CIO', N'CRO') AND is_deleted = 0 AND is_active = 1);
IF @roles <> 2
    THROW 51381, 'V381 check 0: CIO and CRO are not both seated. V373 creates them and must run first; a grant chain that cannot resolve its role name inserts nothing and still succeeds, which is the defect this file is written against.', 1;

DECLARE @perms INT = (SELECT COUNT(*) FROM iam.permissions
                       WHERE module = N'AI' AND action IN (N'READ', N'WRITE'));
IF @perms <> 2
    THROW 51381, 'V381 check 0b: AI:READ / AI:WRITE are not both catalogued. V313 creates them. F2-258 converts AiLifecycleController onto AI:WRITE, so if the pair is absent the conversion has nothing to resolve and every write route falls back to the PLATFORM_ADMIN code bypass.', 1;
GO

-- ── 2. The grants, as declared literals ────────────────────────────────────────────────────────────
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES (N'CIO', N'AI', N'READ'),
               (N'CIO', N'AI', N'WRITE'),
               (N'CRO', N'AI', N'READ'),
               (N'CRO', N'AI', N'WRITE')) AS t(role_name, module, action)
  JOIN iam.roles r       ON r.role_name = t.role_name AND r.is_deleted = 0
  JOIN iam.permissions p ON p.module = t.module AND p.action = t.action
 WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- ── 3. Self-checks ─────────────────────────────────────────────────────────────────────────────────
DECLARE @granted INT = (
    SELECT COUNT(*) FROM iam.role_permissions rp
      JOIN iam.roles r       ON r.id = rp.role_id
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE r.role_name IN (N'CIO', N'CRO') AND p.module = N'AI'
       AND p.action IN (N'READ', N'WRITE'));
IF @granted <> 4
    THROW 51381, 'V381 check 1: fewer than 4 (role, AI action) grants exist for CIO/CRO. Without them F2-258''s guard conversion drops two roles the retired hasAnyRole lists named, which is a narrowing of the shipped audience disguised as a refactor.', 1;

-- Check 2 — CONTAINMENT. Nobody may write what they cannot read. Scoped to READ/WRITE deliberately:
-- AI:EXPORT is held by AUDITOR without AI:READ in both databases, and that is a live, intended shape
-- (MlModelController's EXPORT_GUARD ANDs its own role list), so a check spanning EXPORT would fail on
-- correct data — the one thing a self-check must never do.
DECLARE @writeNoRead INT = (
    SELECT COUNT(*) FROM iam.roles r
     WHERE EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND p.module = N'AI' AND p.action = N'WRITE')
       AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
                        WHERE rp.role_id = r.id AND p.module = N'AI' AND p.action = N'READ'));
IF @writeNoRead <> 0
    THROW 51381, 'V381 check 2: a role holds AI:WRITE and not AI:READ. Every AI write route is reached from a screen that lists the records first, so a write-only grant produces a principal that can decide a model recertification and cannot see the register it belongs to.', 1;

-- Check 3 — ANTI-VACUITY on the audience this conversion depends on. The case for converting the guards is
-- that AI:WRITE already has real holders; if that tier were ever emptied the six routes would be
-- administrator-only again, which is exactly the defect F2-258 fixes, and check 1 would not notice.
DECLARE @writeRoles INT = (
    SELECT COUNT(*) FROM iam.role_permissions rp
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE p.module = N'AI' AND p.action = N'WRITE');
IF @writeRoles < 10
    THROW 51381, 'V381 check 3: AI:WRITE is granted to fewer than 10 roles. F2-258 converted six role-guarded AI-lifecycle routes onto this one permission on the measured basis that 41 roles hold it, 8 with a live holder; if the tier has collapsed the conversion has re-created the lockout it removed.', 1;

PRINT 'V381: CIO and CRO hold AI:READ + AI:WRITE; F2-258''s guard conversion drops no named role.';
GO

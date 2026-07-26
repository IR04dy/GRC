-- =====================================================================
-- V320 — F2-27: make MODULE:EXPORT real before the code starts requiring it.
-- ---------------------------------------------------------------------
-- 24 EXPORT permissions were catalogued and granted to as many as 55 of 58 roles, and
-- exactly ONE of the platform's 39 file-returning endpoints enforced any of them
-- (IAM:EXPORT on the access-assurance ZIP). Every other export — the risk register, the
-- change register, the CAB pack, the board pack, the regulator ZIP, the sustainability
-- annex, the climate report, the green-finance evidence package, the SGI register, the BCP
-- binder, the NDI reports — was authorised by a *READ* permission or by a raw role-name
-- list, so the right to view one record on screen conferred the right to extract the whole
-- register as a file. A tenant administrator granting or revoking CHANGE:EXPORT in the role
-- screen changed nothing, which is worse than the permission not existing: it manufactures
-- false assurance in the customer's own access review. This is the exact mirror of F2-23 (a
-- permission no role held guarding 59 methods); both are the catalogue and the code
-- disagreeing, and neither had a test.
--
-- The companion commit lifts every export guard from READ to EXPORT. That change 403s
-- somebody the moment it lands unless the grants are fixed FIRST — which is why this
-- migration is authored before the annotations, not after.
--
-- ── THE MEASUREMENT THAT DECIDED EVERY LINE BELOW ────────────────────────────
-- Per-tenant impact query, dev DB, 2026-07-26 (roles holding MODULE:READ *without*
-- MODULE:EXPORT — i.e. exactly who loses export access when the guard is lifted):
--
--   RISK 0 · COMPLIANCE 0 · SUST 0 · BCM 0 · REPORTING 0 · CYBER 0 · NDI 0 ·
--   EVIDENCE 0 · IAM 0 · PLATFORM 0 · WORKFLOW 0        <- lift is a NO-OP for these
--   CHANGE 5 roles (2 real users) · ADMIN 14 roles (3 real users)
--   AI  *** EXPORT NOT CATALOGUED AT ALL ***
--
-- Eleven of fourteen modules grant READ and EXPORT to identical role sets, so lifting
-- their guards breaks nobody — the defect was pure enforcement, not grants. Only three
-- modules needed a decision, and each is handled below.
--
-- Also measured, and the reason the composition rule is "lift each guard in place" rather
-- than the register's proposed AND-chain: of the 18 roles holding REPORTING:EXPORT, EIGHT
-- lack RISK:READ or COMPLIANCE:READ (CFO, CISO, CYBER_MANAGER, DPO, FINANCE_LEAD, MA_LEAD,
-- PRIVACY_MANAGER, SUSTAINABILITY_OFFICER). Requiring REPORTING:EXPORT *and* the
-- contributing modules' READ would have 403'd all eight on the board pack. Conversely a
-- single REPORTING:EXPORT gate on the board pack would strip GRC_ADMINISTRATOR, ORG_ADMIN,
-- RISK_MANAGER, RISK_ANALYST, RISK_OWNER and SUPPLIER_MANAGER (3 real dev users) of an
-- export they run today. The guards therefore keep their existing boolean shape and only
-- their action is lifted: `REPORTING:READ or RISK:READ` becomes
-- `REPORTING:EXPORT or RISK:EXPORT`. Because both modules grant READ and EXPORT to
-- identical role sets, that lift is exactly access-preserving.
--
-- ── PART 1 · AI:EXPORT did not exist (the F2-23 trap, avoided) ───────────────
-- iam.permissions held AI_READ (41 roles) and AI_WRITE, and no AI_EXPORT. Annotating
-- GET /api/v1/ai/models/export with hasPermission('AI','EXPORT') without this migration
-- would have produced a guard no tenant could satisfy — 403 for all 58 roles, the model
-- inventory unreachable, PLATFORM_ADMIN passing only via the evaluator's code bypass.
-- That is the fourth time in this register a mechanism has been shipped against data that
-- did not exist (F2-23 permission → F2-25 NOT NULL column → F2-26 link column →
-- F2-28 attendance table), which is why the impact query is a precondition and not a
-- courtesy.
--
-- The endpoint's existing guard is a raw role list —
--   hasAnyRole('RISK_MANAGER','CISO','MODEL_OWNER','DPO','AUDITOR','PLATFORM_ADMIN')
-- — which bypasses the catalogue entirely, so no AI permission can restrict it. The
-- companion commit AND-s hasPermission('AI','EXPORT') in front, narrowing and never
-- widening. Two grants are needed for that AND to preserve the author's intent:
--   * the 41 roles holding AI:READ (set-derived below, so the two stay in step), and
--   * AUDITOR, which the endpoint allows but which holds no AI:READ at all — without an
--     explicit grant the AND would 403 a role the author explicitly listed.
-- ('MODEL_OWNER' is not a defined role; the companion commit drops that dead entry.)
--
-- ── PART 2 · CHANGE:EXPORT for the assurance personas ────────────────────────
-- Five roles hold CHANGE:READ without CHANGE:EXPORT: AUDITOR, INTERNAL_AUDITOR,
-- EXTERNAL_AUDITOR, REGULATOR_VIEWER, VIEWER. The four assurance roles hold EXPORT on ~20
-- other modules (COMPLIANCE, RISK, BCM, SUST, NDI, REPORTING, TPRM, AUDIT_LOG…); the
-- CHANGE omission is an oversight in V301's grant set, not a policy — extracting the
-- change register IS auditing, and a regulator asking for the change trail is a first-class
-- Saudi use case (NCA ECC change-management evidence, SAMA CSF 3.3.x).
--
-- VIEWER is deliberately NOT granted. VIEWER is the read-only persona and is precisely who
-- this control exists for; it is also the live 403 prover for the whole slice (its JWT
-- carries CHANGE_READ and no CHANGE_EXPORT — verified against the running stack). One dev
-- user (`viewer`, org 1) loses the change-register export as a result. That is the single
-- intentional revocation in this migration and it is the point of the finding.
--
-- Note for the record: VIEWER still holds RISK:EXPORT, SUST:EXPORT, NDI:EXPORT and
-- REPORTING:EXPORT among 17 EXPORT grants. A read-only persona holding 17 export rights is
-- a *grant-tightness* problem (F2-13a), not an *enforcement* problem (F2-27), and F2-27
-- must not silently widen or narrow personas beyond what enforcement requires. Stated here
-- so the inconsistency is inherited deliberately rather than discovered later.
--
-- ── PART 3 · BCM:EXPORT for CISO and CFO (an AND-composition precondition) ───
-- BcpBinderController#download is guarded by hasAnyRole('BCM_MANAGER','CISO',
-- 'PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','BOARD_MEMBER','CFO') — again a role-name check
-- that bypasses the catalogue. The companion commit AND-s hasPermission('BCM','EXPORT') in
-- front. Measured: BCM_MANAGER, PLATFORM_ADMIN, RISK_MANAGER and AUDITOR already hold
-- BCM:EXPORT, but CISO and CFO hold neither BCM:READ nor BCM:EXPORT, so the AND would have
-- 403'd two roles the endpoint's author explicitly allowed. They are granted BCM:EXPORT
-- here — restoring the author's intent through the catalogue instead of around it.
-- ('BOARD_MEMBER' is not a defined role either; that dead entry is dropped as well.)
--
-- ── DELIBERATELY NOT IN THIS MIGRATION ───────────────────────────────────────
-- ADMIN:EXPORT is NOT widened past the 7 roles ADR-014 set, because the platform's only
-- ADMIN export is GET /committees/catalog/forms/{formCode}/export — a BLANK governance form
-- template carrying no tenant data. Lifting that guard would strip 14 roles (CISO, DPO,
-- BCM_MANAGER, CRISIS_MANAGER, RISK_MANAGER, LEGAL_COUNSEL…) of a blank PDF download for
-- zero DLP benefit. It stays ADMIN:READ and is an enumerated, justified entry in the new
-- export ratchet test's allowlist. V317 noted ADMIN:EXPORT gated no endpoint and granted it
-- for a Wave 2 committee-pack export; that is still true and still pending.
--
-- Grants join on role_name, so they apply to every tenant's role of that name (the V272
-- idiom; 47 of 58 roles are org-scoped copies).
--
-- Idempotent; safe to re-run. Applied to dev twice via pyodbc (Flyway off in dev).
-- Reverse recipe:
--   DELETE rp FROM iam.role_permissions rp
--     JOIN iam.permissions p ON p.id = rp.permission_id
--     JOIN iam.roles r ON r.id = rp.role_id
--    WHERE (p.module = 'AI' AND p.action = 'EXPORT')
--       OR (p.module = 'CHANGE' AND p.action = 'EXPORT'
--           AND r.role_name IN ('AUDITOR','INTERNAL_AUDITOR','EXTERNAL_AUDITOR','REGULATOR_VIEWER'))
--       OR (p.module = 'BCM' AND p.action = 'EXPORT' AND r.role_name IN ('CISO','CFO'));
--   DELETE FROM iam.permissions WHERE module = 'AI' AND action = 'EXPORT';
-- =====================================================================

-- ── Part 1a: catalogue AI:EXPORT ─────────────────────────────────────────────
-- permission_name follows the V272 convention MODULE_ACTION, which is also the granted
-- authority string ModulePermissionEvaluator matches on (module + "_" + action).
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('AI', 'EXPORT', 'Export/download the AI/ML model inventory and governance registers')
) v(module, action, descr)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                  WHERE p.module = v.module AND p.action = v.action);
GO

-- ── Part 1b: grant AI:EXPORT to the AI:READ population ───────────────────────
-- Set-derived rather than enumerated: the invariant is "whoever can see the model
-- inventory can export it", and deriving it keeps the two in step if AI:READ is ever
-- re-granted. Nobody gains access in substance — the endpoint's role list still applies.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT rd.role_id, pe.id
FROM (SELECT DISTINCT rp.role_id
        FROM iam.role_permissions rp
        JOIN iam.permissions p ON p.id = rp.permission_id
       WHERE p.module = 'AI' AND p.action = 'READ') rd
CROSS JOIN (SELECT id FROM iam.permissions WHERE module = 'AI' AND action = 'EXPORT') pe
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                  WHERE x.role_id = rd.role_id AND x.permission_id = pe.id);
GO

-- ── Parts 1c, 2 and 3: the enumerated grant repairs ──────────────────────────
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- AI:EXPORT — AUDITOR is in the endpoint's role list but holds no AI:READ, so the
    -- set-derived grant above misses it and the AND-composition would deny it (header 1).
    ('AUDITOR','AI','EXPORT'),

    -- CHANGE:EXPORT — assurance personas. NOT VIEWER (header Part 2).
    ('AUDITOR','CHANGE','EXPORT'),
    ('INTERNAL_AUDITOR','CHANGE','EXPORT'),
    ('EXTERNAL_AUDITOR','CHANGE','EXPORT'),
    ('REGULATOR_VIEWER','CHANGE','EXPORT'),

    -- BCM:EXPORT — the two roles the BCP-binder role list allows that the catalogue did
    -- not, so the AND-composition preserves the endpoint author's intent (header Part 3).
    ('CISO','BCM','EXPORT'),
    ('CFO','BCM','EXPORT')
) v(role_name, module, action)
JOIN iam.roles r ON r.role_name = v.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = v.module AND p.action = v.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

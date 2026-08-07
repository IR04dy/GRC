-- =====================================================================================================
-- V372 — F2-143's second half: the 214 role grants a fresh install never receives, and the chain
--        ORDER is why. Plus the two an auditor should never have had.
-- -----------------------------------------------------------------------------------------------------
-- F2-143 filed "a migration-built database is 36 permissions and 4 roles short". Both halves are now
-- CLOSED by the chain simply reaching V371: measured 2026-08-07 against a database built from this
-- folder alone (`full_chain_proof.py`, all 359 files applied to an empty database, 471 tables),
-- **0 permissions and 0 live roles are missing**. The finding's own remedy — "a catalogue-completion
-- migration generated from the code's @PreAuthorize set" — is not needed.
--
-- What F2-143 never measured is the GRANTS, and that is where the defect lives:
--
--     iam.role_permissions   fresh install 1,344      dev 1,567      missing 225
--
-- 🔑 THE MECHANISM. Every grant migration here is written as
--        INSERT INTO iam.role_permissions SELECT r.id, p.id
--          FROM (VALUES …) v JOIN iam.roles r ON r.role_name = v.role
--                            JOIN iam.permissions p ON p.module = … AND p.action = …
--         WHERE NOT EXISTS (… already granted …);
-- A set-based INSERT over a JOIN grants nothing when either side is absent, and `NOT EXISTS` cannot
-- tell "already granted" from "nothing matched". So a grant whose role or permission row is created
-- LATER in the chain is silently dropped — no error, no row, and the migration reports success.
-- On dev the Python bootstrap had already created both sides before Flyway ran, so the same file
-- granted; a customer built from the folder gets the subset that happened to be orderable.
--
-- Proven by replay, not argued: running this identical folder a SECOND time over the built database
-- adds **875 grants across 20 migrations** — the same files doing different work depending on when
-- they run. The extreme case is V284's stated "READ-parity default": it grants MODULE:EXPORT to every
-- holder of MODULE:READ *at the moment V284 runs*, so every READ granted after it (V301, V313, V320,
-- V327…V362) has no EXPORT partner. ⚠ A parity migration is a SNAPSHOT, not an invariant.
--
-- ⚠ AND A SECOND PASS IS NOT THE FIX: it overshoots dev by 686 grants because it also re-applies what
-- V333, V335 and V367–V371 deliberately REVOKED. Re-running an idempotent folder resurrects
-- revocations. That is why this migration is an explicit, measured row list.
--
-- WHAT A FRESH INSTALL COULD NOT DO BEFORE THIS FILE (each measured, not inferred):
--   * ORG_ADMIN — the primary tenant administrator, and the role F2-81's onboarding provisions —
--     held 30 fewer grants: no BCM, COMPLIANCE, EVIDENCE, SCM, SUST, AI or NDI at all.
--   * VIEWER 19 fewer, RISK_ANALYST 23, INTERNAL_AUDITOR 13, COMPLIANCE_OFFICER 11 — the compliance
--     officer could not read compliance.
--   * 33 roles affected. 78 of the 225 are EXPORT, and **0 of those are parity-restorable**, because
--     the matching READ is missing too: the EXPORT loss is downstream of the READ loss.
--
-- THE TWO AUTHORITIES, AND WHY BOTH ARE NEEDED (F2-143's rule: generate, do not hand-type, and do
-- not copy a database of unknown provenance):
--   GROUP A — 188 grants THIS FOLDER ALREADY DECLARES and loses only to ordering. Authority: the
--             folder itself, proven by the replay above.
--   GROUP B — 26 SCM grants declared by NO migration anywhere. `scm` (SAMA outsourcing / supply
--             chain) has 9 grants on a fresh install — AUDITOR, PROCUREMENT_OWNER, SUPPLIER_MANAGER,
--             so its own personas work — and 39 in dev. The 8 cross-functional roles here are the
--             ones dev's persona matrix gives it and no migration ever wrote. Dev is the only
--             authority for them, and this comment is the disclosure of that.
--
-- DELIBERATELY NOT GRANTED, and asserted below to stay that way:
--   * 8 PLATFORM_ADMIN grants (CYBER, SCM). V272's own header: "PLATFORM_ADMIN needs no grants
--     (evaluator bypass)". Granting them would make the bypass look like an entitlement.
--   * 3 RISK_ANALYST grants — AI:WRITE, CHANGE:WRITE, SIGNATURE:READ — declared by no migration, two
--     of them writes. Adding privilege on no authority is what F2-13b FILED, not what it asked for.
--     A fresh install therefore stays 11 grants below dev, by decision, and the two THROWs below
--     name the number so it cannot drift silently.
--
-- AND TWO GRANTS GO THE OTHER WAY — the folder is more permissive than dev, which is the direction
-- V366 taught this programme to check. 🔑 And measuring it found the two auditor roles EXACTLY
-- BACKWARDS on a fresh install, which is a better reason than the one this file first gave:
--   * a fresh install grants `AUDITOR` — the generic read-only assurance role — EVIDENCE:WRITE and
--     NDI:WRITE, while `INTERNAL_AUDITOR`, the role that does the fieldwork and uploads workpapers,
--     holds NEITHER, because its grants are among the 188 that ordering dropped. dev has it the other
--     way round. So group A restores INTERNAL_AUDITOR's and this DELETE takes AUDITOR's, and the two
--     halves of one inversion are fixed in one file. V283's SOD-02 (EVIDENCE WRITE/APPROVE as a
--     conflicting PAIR) supports the direction; it is not the reason on its own.
--   ⚠ Do NOT read this as "the auditor persona loses evidence write". Measured live: dev's `auditor`
--     USER holds BOTH roles, so the persona keeps it through INTERNAL_AUDITOR — proven by probe,
--     201 on POST /api/v1/evidence/items. Reading the AUDITOR ROLE alone said the opposite and had me
--     briefly filing CLAUDE.md's persona line as wrong. **A role is not a persona when a user holds
--     two of them.**
--
-- Idempotent: every INSERT is NOT EXISTS-guarded, the DELETE is set-based, and 8 self-checks
-- THROW. A no-op on dev and on prod, which already hold every row in groups A and B.
--
-- ⚠ WHAT THESE SELF-CHECKS DO **NOT** GUARANTEE, stated because a THROW reads stronger than it is:
-- Flyway runs a migration ONCE. So they guarantee the state a fresh install STARTS in, and nothing
-- afterwards. If a later change revokes one of the 214, nothing here notices — a migration is an
-- apply-time gate, not an invariant. (Mutation-tested: deleting a group-A grant and re-running V372
-- silently REPAIRS it, because the INSERT is idempotent; that is the right behaviour for a re-run and
-- it is also why a re-run cannot be the detector.) The standing instrument is the convergence diff
-- in `Appendix/scripts/` plus `RoleGrantDerivationRatchetTest`, which stops the CAUSE recurring.
-- =====================================================================================================

-- ── Group A — 188 grants the folder declares and chain order silently dropped ──────────────────
;WITH g(role_name, module, action) AS (SELECT v.role_name, v.module, v.action FROM (VALUES
        (N'ASSET_OWNER',N'COMPLIANCE',N'EXPORT'),(N'ASSET_OWNER',N'COMPLIANCE',N'READ'),(N'AUDITOR',N'CYBER',N'EXPORT'),
        (N'AUDITOR',N'CYBER',N'READ'),(N'BCM_MANAGER',N'BCM',N'DELETE'),(N'BCM_MANAGER',N'BCM',N'EXPORT'),
        (N'BCM_MANAGER',N'BCM',N'READ'),(N'BCM_MANAGER',N'BCM',N'WRITE'),(N'BCM_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'BCM_MANAGER',N'COMPLIANCE',N'READ'),(N'BCM_MANAGER',N'COMPLIANCE',N'WRITE'),(N'BCM_MANAGER',N'EVIDENCE',N'EXPORT'),
        (N'BCM_MANAGER',N'EVIDENCE',N'READ'),(N'BCM_MANAGER',N'EVIDENCE',N'WRITE'),(N'BCM_MANAGER',N'WORKFLOW',N'DELETE'),
        (N'BIA_OWNER',N'BCM',N'EXPORT'),(N'BIA_OWNER',N'BCM',N'READ'),(N'BIA_OWNER',N'BCM',N'WRITE'),
        (N'BIA_OWNER',N'COMPLIANCE',N'EXPORT'),(N'BIA_OWNER',N'COMPLIANCE',N'READ'),(N'BOARD_SECRETARY',N'BCM',N'EXPORT'),
        (N'BOARD_SECRETARY',N'BCM',N'READ'),(N'BOARD_SECRETARY',N'COMPLIANCE',N'EXPORT'),(N'BOARD_SECRETARY',N'COMPLIANCE',N'READ'),
        (N'CISO',N'CYBER',N'EXPORT'),(N'CISO',N'CYBER',N'READ'),(N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'EXPORT'),
        (N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'READ'),(N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'WRITE'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'DELETE'),
        (N'COMPLIANCE_OFFICER',N'EVIDENCE',N'EXPORT'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'READ'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'WRITE'),
        (N'CONTROL_OWNER',N'COMPLIANCE',N'EXPORT'),(N'CONTROL_OWNER',N'COMPLIANCE',N'READ'),(N'CONTROL_OWNER',N'COMPLIANCE',N'WRITE'),
        (N'CRISIS_MANAGER',N'BCM',N'EXPORT'),(N'CRISIS_MANAGER',N'BCM',N'READ'),(N'CRISIS_MANAGER',N'BCM',N'WRITE'),
        (N'CRISIS_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'CRISIS_MANAGER',N'COMPLIANCE',N'READ'),(N'CYBER_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'CYBER_MANAGER',N'COMPLIANCE',N'READ'),(N'CYBER_MANAGER',N'CYBER',N'EXPORT'),(N'CYBER_MANAGER',N'CYBER',N'READ'),
        (N'DATA_OWNER',N'COMPLIANCE',N'EXPORT'),(N'DATA_OWNER',N'COMPLIANCE',N'READ'),(N'DPO',N'COMPLIANCE',N'EXPORT'),
        (N'DPO',N'COMPLIANCE',N'READ'),(N'DPO',N'COMPLIANCE',N'WRITE'),(N'DPO',N'EVIDENCE',N'EXPORT'),
        (N'DPO',N'EVIDENCE',N'READ'),(N'DPO',N'EVIDENCE',N'WRITE'),(N'DR_OWNER',N'BCM',N'EXPORT'),
        (N'DR_OWNER',N'BCM',N'READ'),(N'DR_OWNER',N'BCM',N'WRITE'),(N'DR_OWNER',N'COMPLIANCE',N'EXPORT'),
        (N'DR_OWNER',N'COMPLIANCE',N'READ'),(N'EVIDENCE_OWNER',N'COMPLIANCE',N'EXPORT'),(N'EVIDENCE_OWNER',N'COMPLIANCE',N'READ'),
        (N'EXECUTIVE_REVIEWER',N'BCM',N'EXPORT'),(N'EXECUTIVE_REVIEWER',N'BCM',N'READ'),(N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'EXPORT'),
        (N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'READ'),(N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'WRITE'),(N'EXTERNAL_AUDITOR',N'BCM',N'EXPORT'),
        (N'EXTERNAL_AUDITOR',N'BCM',N'READ'),(N'EXTERNAL_AUDITOR',N'COMPLIANCE',N'EXPORT'),(N'EXTERNAL_AUDITOR',N'COMPLIANCE',N'READ'),
        (N'EXTERNAL_AUDITOR',N'CYBER',N'EXPORT'),(N'EXTERNAL_AUDITOR',N'CYBER',N'READ'),(N'GRC_ADMINISTRATOR',N'BCM',N'DELETE'),
        (N'GRC_ADMINISTRATOR',N'BCM',N'EXPORT'),(N'GRC_ADMINISTRATOR',N'BCM',N'READ'),(N'GRC_ADMINISTRATOR',N'BCM',N'WRITE'),
        (N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'EXPORT'),(N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'READ'),(N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'WRITE'),
        (N'INTERNAL_AUDITOR',N'BCM',N'EXPORT'),(N'INTERNAL_AUDITOR',N'BCM',N'READ'),(N'INTERNAL_AUDITOR',N'COMPLIANCE',N'EXPORT'),
        (N'INTERNAL_AUDITOR',N'COMPLIANCE',N'READ'),(N'INTERNAL_AUDITOR',N'COMPLIANCE',N'WRITE'),(N'INTERNAL_AUDITOR',N'CYBER',N'EXPORT'),
        (N'INTERNAL_AUDITOR',N'CYBER',N'READ'),(N'INTERNAL_AUDITOR',N'EVIDENCE',N'EXPORT'),(N'INTERNAL_AUDITOR',N'EVIDENCE',N'READ'),
        (N'INTERNAL_AUDITOR',N'EVIDENCE',N'WRITE'),(N'LEGAL_REVIEWER',N'COMPLIANCE',N'EXPORT'),(N'LEGAL_REVIEWER',N'COMPLIANCE',N'READ'),
        (N'LEGAL_REVIEWER',N'COMPLIANCE',N'WRITE'),(N'NCA_COMPLIANCE',N'COMPLIANCE',N'EXPORT'),(N'NCA_COMPLIANCE',N'COMPLIANCE',N'READ'),
        (N'NCA_COMPLIANCE',N'COMPLIANCE',N'WRITE'),(N'ORG_ADMIN',N'AI',N'EXPORT'),(N'ORG_ADMIN',N'AI',N'READ'),
        (N'ORG_ADMIN',N'AI',N'WRITE'),(N'ORG_ADMIN',N'BCM',N'APPROVE'),(N'ORG_ADMIN',N'BCM',N'DELETE'),
        (N'ORG_ADMIN',N'BCM',N'EXPORT'),(N'ORG_ADMIN',N'BCM',N'READ'),(N'ORG_ADMIN',N'BCM',N'WRITE'),
        (N'ORG_ADMIN',N'CHANGE',N'WRITE'),(N'ORG_ADMIN',N'COMPLIANCE',N'DELETE'),(N'ORG_ADMIN',N'COMPLIANCE',N'EXPORT'),
        (N'ORG_ADMIN',N'COMPLIANCE',N'READ'),(N'ORG_ADMIN',N'COMPLIANCE',N'WRITE'),(N'ORG_ADMIN',N'EVIDENCE',N'DELETE'),
        (N'ORG_ADMIN',N'EVIDENCE',N'EXPORT'),(N'ORG_ADMIN',N'EVIDENCE',N'READ'),(N'ORG_ADMIN',N'EVIDENCE',N'WRITE'),
        (N'ORG_ADMIN',N'NDI',N'APPROVE'),(N'ORG_ADMIN',N'NDI',N'EXPORT'),(N'ORG_ADMIN',N'NDI',N'READ'),
        (N'ORG_ADMIN',N'NDI',N'WRITE'),(N'ORG_ADMIN',N'SUST',N'DELETE'),(N'ORG_ADMIN',N'SUST',N'EXPORT'),
        (N'ORG_ADMIN',N'SUST',N'READ'),(N'ORG_ADMIN',N'SUST',N'WRITE'),(N'ORG_ADMIN',N'WORKFLOW',N'WRITE'),
        (N'PLATFORM_ADMIN',N'BCM',N'DELETE'),(N'PLATFORM_ADMIN',N'BCM',N'EXPORT'),(N'PLATFORM_ADMIN',N'BCM',N'READ'),
        (N'PLATFORM_ADMIN',N'BCM',N'WRITE'),(N'PLATFORM_ADMIN',N'COMPLIANCE',N'EXPORT'),(N'PLATFORM_ADMIN',N'COMPLIANCE',N'READ'),
        (N'PLATFORM_ADMIN',N'COMPLIANCE',N'WRITE'),(N'PRIVACY_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'PRIVACY_MANAGER',N'COMPLIANCE',N'READ'),
        (N'PRIVACY_MANAGER',N'COMPLIANCE',N'WRITE'),(N'PROCUREMENT_OWNER',N'COMPLIANCE',N'EXPORT'),(N'PROCUREMENT_OWNER',N'COMPLIANCE',N'READ'),
        (N'REGULATOR_VIEWER',N'BCM',N'READ'),(N'REGULATOR_VIEWER',N'COMPLIANCE',N'READ'),(N'REGULATOR_VIEWER',N'CYBER',N'READ'),
        (N'RISK_ANALYST',N'AI',N'EXPORT'),(N'RISK_ANALYST',N'AI',N'READ'),(N'RISK_ANALYST',N'CHANGE',N'EXPORT'),
        (N'RISK_ANALYST',N'CHANGE',N'READ'),(N'RISK_ANALYST',N'COMPLIANCE',N'EXPORT'),(N'RISK_ANALYST',N'COMPLIANCE',N'READ'),
        (N'RISK_ANALYST',N'EVIDENCE',N'EXPORT'),(N'RISK_ANALYST',N'EVIDENCE',N'READ'),(N'RISK_ANALYST',N'EVIDENCE',N'WRITE'),
        (N'RISK_ANALYST',N'NDI',N'EXPORT'),(N'RISK_ANALYST',N'NDI',N'READ'),(N'RISK_ANALYST',N'NDI',N'WRITE'),
        (N'RISK_ANALYST',N'SUST',N'EXPORT'),(N'RISK_ANALYST',N'SUST',N'READ'),(N'RISK_ANALYST',N'WORKFLOW',N'EXPORT'),
        (N'RISK_ANALYST',N'WORKFLOW',N'READ'),(N'RISK_ANALYST',N'WORKFLOW',N'WRITE'),(N'RISK_MANAGER',N'BCM',N'EXPORT'),
        (N'RISK_MANAGER',N'BCM',N'READ'),(N'RISK_MANAGER',N'BCM',N'WRITE'),(N'RISK_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'RISK_MANAGER',N'COMPLIANCE',N'READ'),(N'RISK_MANAGER',N'COMPLIANCE',N'WRITE'),(N'RISK_OWNER',N'COMPLIANCE',N'EXPORT'),
        (N'RISK_OWNER',N'COMPLIANCE',N'READ'),(N'SECURITY_ANALYST',N'CYBER',N'EXPORT'),(N'SECURITY_ANALYST',N'CYBER',N'READ'),
        (N'SERVICE_OWNER',N'BCM',N'EXPORT'),(N'SERVICE_OWNER',N'BCM',N'READ'),(N'SERVICE_OWNER',N'BCM',N'WRITE'),
        (N'SERVICE_OWNER',N'COMPLIANCE',N'EXPORT'),(N'SERVICE_OWNER',N'COMPLIANCE',N'READ'),(N'SUPPLIER_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'SUPPLIER_MANAGER',N'COMPLIANCE',N'READ'),(N'VIEWER',N'BCM',N'EXPORT'),(N'VIEWER',N'BCM',N'READ'),
        (N'VIEWER',N'CHANGE',N'READ'),(N'VIEWER',N'COMPLIANCE',N'EXPORT'),(N'VIEWER',N'COMPLIANCE',N'READ'),
        (N'VIEWER',N'CYBER',N'EXPORT'),(N'VIEWER',N'CYBER',N'READ'),(N'VIEWER',N'EVIDENCE',N'EXPORT'),
        (N'VIEWER',N'EVIDENCE',N'READ'),(N'VIEWER',N'NDI',N'EXPORT'),(N'VIEWER',N'NDI',N'READ'),
        (N'VIEWER',N'SIGNATURE',N'READ'),(N'VIEWER',N'SUST',N'EXPORT'),(N'VIEWER',N'SUST',N'READ'),
        (N'VIEWER',N'WORKFLOW',N'EXPORT'),(N'VIEWER',N'WORKFLOW',N'READ'),(N'VIEWER',N'WORKFLOW',N'WRITE'),
        (N'VULN_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'VULN_MANAGER',N'COMPLIANCE',N'READ')
) v(role_name, module, action))
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM g
JOIN iam.roles r       ON r.role_name = g.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = g.module AND p.action = g.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                   WHERE x.role_id = r.id AND x.permission_id = p.id);
GO

-- ── Group B — 26 SCM grants no migration has ever declared ────────────────────────────────────
-- `scm` carries SAMA's outsourcing register. Its own personas (SUPPLIER_MANAGER, PROCUREMENT_OWNER)
-- are granted by the folder; these eight are the cross-functional readers and authors dev's persona
-- matrix gives it — compliance, privacy, risk, BCM, internal audit, the tenant administrator and the
-- read-only viewer. Listed separately from group A because their authority is different, and a reader
-- deciding whether to keep them should be able to see which is which.
;WITH g(role_name, module, action) AS (SELECT v.role_name, v.module, v.action FROM (VALUES
        (N'BCM_MANAGER',N'SCM',N'EXPORT'),(N'BCM_MANAGER',N'SCM',N'READ'),(N'BCM_MANAGER',N'SCM',N'WRITE'),
        (N'COMPLIANCE_OFFICER',N'SCM',N'DELETE'),(N'COMPLIANCE_OFFICER',N'SCM',N'EXPORT'),(N'COMPLIANCE_OFFICER',N'SCM',N'READ'),
        (N'COMPLIANCE_OFFICER',N'SCM',N'WRITE'),(N'DPO',N'SCM',N'EXPORT'),(N'DPO',N'SCM',N'READ'),
        (N'DPO',N'SCM',N'WRITE'),(N'INTERNAL_AUDITOR',N'SCM',N'EXPORT'),(N'INTERNAL_AUDITOR',N'SCM',N'READ'),
        (N'INTERNAL_AUDITOR',N'SCM',N'WRITE'),(N'ORG_ADMIN',N'SCM',N'DELETE'),(N'ORG_ADMIN',N'SCM',N'EXPORT'),
        (N'ORG_ADMIN',N'SCM',N'READ'),(N'ORG_ADMIN',N'SCM',N'WRITE'),(N'RISK_ANALYST',N'SCM',N'EXPORT'),
        (N'RISK_ANALYST',N'SCM',N'READ'),(N'RISK_ANALYST',N'SCM',N'WRITE'),(N'RISK_MANAGER',N'SCM',N'DELETE'),
        (N'RISK_MANAGER',N'SCM',N'EXPORT'),(N'RISK_MANAGER',N'SCM',N'READ'),(N'RISK_MANAGER',N'SCM',N'WRITE'),
        (N'VIEWER',N'SCM',N'EXPORT'),(N'VIEWER',N'SCM',N'READ')
) v(role_name, module, action))
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM g
JOIN iam.roles r       ON r.role_name = g.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = g.module AND p.action = g.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                   WHERE x.role_id = r.id AND x.permission_id = p.id);
GO

-- ── The other direction — an auditor does not author what it audits ───────────────────────────────
DELETE rp FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'AUDITOR'
   AND p.action = N'WRITE'
   AND p.module IN (N'EVIDENCE', N'NDI');
GO

-- ── Self-checks. Every one of these THROWs. ───────────────────────────────────────────────────────
DECLARE @a INT, @b INT, @padmin INT, @auditor INT, @resurrect INT, @regv INT, @orgadmin INT;

-- (1) every group-A grant landed
;WITH g(role_name, module, action) AS (SELECT v.role_name, v.module, v.action FROM (VALUES
        (N'ASSET_OWNER',N'COMPLIANCE',N'EXPORT'),(N'ASSET_OWNER',N'COMPLIANCE',N'READ'),(N'AUDITOR',N'CYBER',N'EXPORT'),
        (N'AUDITOR',N'CYBER',N'READ'),(N'BCM_MANAGER',N'BCM',N'DELETE'),(N'BCM_MANAGER',N'BCM',N'EXPORT'),
        (N'BCM_MANAGER',N'BCM',N'READ'),(N'BCM_MANAGER',N'BCM',N'WRITE'),(N'BCM_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'BCM_MANAGER',N'COMPLIANCE',N'READ'),(N'BCM_MANAGER',N'COMPLIANCE',N'WRITE'),(N'BCM_MANAGER',N'EVIDENCE',N'EXPORT'),
        (N'BCM_MANAGER',N'EVIDENCE',N'READ'),(N'BCM_MANAGER',N'EVIDENCE',N'WRITE'),(N'BCM_MANAGER',N'WORKFLOW',N'DELETE'),
        (N'BIA_OWNER',N'BCM',N'EXPORT'),(N'BIA_OWNER',N'BCM',N'READ'),(N'BIA_OWNER',N'BCM',N'WRITE'),
        (N'BIA_OWNER',N'COMPLIANCE',N'EXPORT'),(N'BIA_OWNER',N'COMPLIANCE',N'READ'),(N'BOARD_SECRETARY',N'BCM',N'EXPORT'),
        (N'BOARD_SECRETARY',N'BCM',N'READ'),(N'BOARD_SECRETARY',N'COMPLIANCE',N'EXPORT'),(N'BOARD_SECRETARY',N'COMPLIANCE',N'READ'),
        (N'CISO',N'CYBER',N'EXPORT'),(N'CISO',N'CYBER',N'READ'),(N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'EXPORT'),
        (N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'READ'),(N'COMPLIANCE_OFFICER',N'COMPLIANCE',N'WRITE'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'DELETE'),
        (N'COMPLIANCE_OFFICER',N'EVIDENCE',N'EXPORT'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'READ'),(N'COMPLIANCE_OFFICER',N'EVIDENCE',N'WRITE'),
        (N'CONTROL_OWNER',N'COMPLIANCE',N'EXPORT'),(N'CONTROL_OWNER',N'COMPLIANCE',N'READ'),(N'CONTROL_OWNER',N'COMPLIANCE',N'WRITE'),
        (N'CRISIS_MANAGER',N'BCM',N'EXPORT'),(N'CRISIS_MANAGER',N'BCM',N'READ'),(N'CRISIS_MANAGER',N'BCM',N'WRITE'),
        (N'CRISIS_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'CRISIS_MANAGER',N'COMPLIANCE',N'READ'),(N'CYBER_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'CYBER_MANAGER',N'COMPLIANCE',N'READ'),(N'CYBER_MANAGER',N'CYBER',N'EXPORT'),(N'CYBER_MANAGER',N'CYBER',N'READ'),
        (N'DATA_OWNER',N'COMPLIANCE',N'EXPORT'),(N'DATA_OWNER',N'COMPLIANCE',N'READ'),(N'DPO',N'COMPLIANCE',N'EXPORT'),
        (N'DPO',N'COMPLIANCE',N'READ'),(N'DPO',N'COMPLIANCE',N'WRITE'),(N'DPO',N'EVIDENCE',N'EXPORT'),
        (N'DPO',N'EVIDENCE',N'READ'),(N'DPO',N'EVIDENCE',N'WRITE'),(N'DR_OWNER',N'BCM',N'EXPORT'),
        (N'DR_OWNER',N'BCM',N'READ'),(N'DR_OWNER',N'BCM',N'WRITE'),(N'DR_OWNER',N'COMPLIANCE',N'EXPORT'),
        (N'DR_OWNER',N'COMPLIANCE',N'READ'),(N'EVIDENCE_OWNER',N'COMPLIANCE',N'EXPORT'),(N'EVIDENCE_OWNER',N'COMPLIANCE',N'READ'),
        (N'EXECUTIVE_REVIEWER',N'BCM',N'EXPORT'),(N'EXECUTIVE_REVIEWER',N'BCM',N'READ'),(N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'EXPORT'),
        (N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'READ'),(N'EXECUTIVE_REVIEWER',N'COMPLIANCE',N'WRITE'),(N'EXTERNAL_AUDITOR',N'BCM',N'EXPORT'),
        (N'EXTERNAL_AUDITOR',N'BCM',N'READ'),(N'EXTERNAL_AUDITOR',N'COMPLIANCE',N'EXPORT'),(N'EXTERNAL_AUDITOR',N'COMPLIANCE',N'READ'),
        (N'EXTERNAL_AUDITOR',N'CYBER',N'EXPORT'),(N'EXTERNAL_AUDITOR',N'CYBER',N'READ'),(N'GRC_ADMINISTRATOR',N'BCM',N'DELETE'),
        (N'GRC_ADMINISTRATOR',N'BCM',N'EXPORT'),(N'GRC_ADMINISTRATOR',N'BCM',N'READ'),(N'GRC_ADMINISTRATOR',N'BCM',N'WRITE'),
        (N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'EXPORT'),(N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'READ'),(N'GRC_ADMINISTRATOR',N'COMPLIANCE',N'WRITE'),
        (N'INTERNAL_AUDITOR',N'BCM',N'EXPORT'),(N'INTERNAL_AUDITOR',N'BCM',N'READ'),(N'INTERNAL_AUDITOR',N'COMPLIANCE',N'EXPORT'),
        (N'INTERNAL_AUDITOR',N'COMPLIANCE',N'READ'),(N'INTERNAL_AUDITOR',N'COMPLIANCE',N'WRITE'),(N'INTERNAL_AUDITOR',N'CYBER',N'EXPORT'),
        (N'INTERNAL_AUDITOR',N'CYBER',N'READ'),(N'INTERNAL_AUDITOR',N'EVIDENCE',N'EXPORT'),(N'INTERNAL_AUDITOR',N'EVIDENCE',N'READ'),
        (N'INTERNAL_AUDITOR',N'EVIDENCE',N'WRITE'),(N'LEGAL_REVIEWER',N'COMPLIANCE',N'EXPORT'),(N'LEGAL_REVIEWER',N'COMPLIANCE',N'READ'),
        (N'LEGAL_REVIEWER',N'COMPLIANCE',N'WRITE'),(N'NCA_COMPLIANCE',N'COMPLIANCE',N'EXPORT'),(N'NCA_COMPLIANCE',N'COMPLIANCE',N'READ'),
        (N'NCA_COMPLIANCE',N'COMPLIANCE',N'WRITE'),(N'ORG_ADMIN',N'AI',N'EXPORT'),(N'ORG_ADMIN',N'AI',N'READ'),
        (N'ORG_ADMIN',N'AI',N'WRITE'),(N'ORG_ADMIN',N'BCM',N'APPROVE'),(N'ORG_ADMIN',N'BCM',N'DELETE'),
        (N'ORG_ADMIN',N'BCM',N'EXPORT'),(N'ORG_ADMIN',N'BCM',N'READ'),(N'ORG_ADMIN',N'BCM',N'WRITE'),
        (N'ORG_ADMIN',N'CHANGE',N'WRITE'),(N'ORG_ADMIN',N'COMPLIANCE',N'DELETE'),(N'ORG_ADMIN',N'COMPLIANCE',N'EXPORT'),
        (N'ORG_ADMIN',N'COMPLIANCE',N'READ'),(N'ORG_ADMIN',N'COMPLIANCE',N'WRITE'),(N'ORG_ADMIN',N'EVIDENCE',N'DELETE'),
        (N'ORG_ADMIN',N'EVIDENCE',N'EXPORT'),(N'ORG_ADMIN',N'EVIDENCE',N'READ'),(N'ORG_ADMIN',N'EVIDENCE',N'WRITE'),
        (N'ORG_ADMIN',N'NDI',N'APPROVE'),(N'ORG_ADMIN',N'NDI',N'EXPORT'),(N'ORG_ADMIN',N'NDI',N'READ'),
        (N'ORG_ADMIN',N'NDI',N'WRITE'),(N'ORG_ADMIN',N'SUST',N'DELETE'),(N'ORG_ADMIN',N'SUST',N'EXPORT'),
        (N'ORG_ADMIN',N'SUST',N'READ'),(N'ORG_ADMIN',N'SUST',N'WRITE'),(N'ORG_ADMIN',N'WORKFLOW',N'WRITE'),
        (N'PLATFORM_ADMIN',N'BCM',N'DELETE'),(N'PLATFORM_ADMIN',N'BCM',N'EXPORT'),(N'PLATFORM_ADMIN',N'BCM',N'READ'),
        (N'PLATFORM_ADMIN',N'BCM',N'WRITE'),(N'PLATFORM_ADMIN',N'COMPLIANCE',N'EXPORT'),(N'PLATFORM_ADMIN',N'COMPLIANCE',N'READ'),
        (N'PLATFORM_ADMIN',N'COMPLIANCE',N'WRITE'),(N'PRIVACY_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'PRIVACY_MANAGER',N'COMPLIANCE',N'READ'),
        (N'PRIVACY_MANAGER',N'COMPLIANCE',N'WRITE'),(N'PROCUREMENT_OWNER',N'COMPLIANCE',N'EXPORT'),(N'PROCUREMENT_OWNER',N'COMPLIANCE',N'READ'),
        (N'REGULATOR_VIEWER',N'BCM',N'READ'),(N'REGULATOR_VIEWER',N'COMPLIANCE',N'READ'),(N'REGULATOR_VIEWER',N'CYBER',N'READ'),
        (N'RISK_ANALYST',N'AI',N'EXPORT'),(N'RISK_ANALYST',N'AI',N'READ'),(N'RISK_ANALYST',N'CHANGE',N'EXPORT'),
        (N'RISK_ANALYST',N'CHANGE',N'READ'),(N'RISK_ANALYST',N'COMPLIANCE',N'EXPORT'),(N'RISK_ANALYST',N'COMPLIANCE',N'READ'),
        (N'RISK_ANALYST',N'EVIDENCE',N'EXPORT'),(N'RISK_ANALYST',N'EVIDENCE',N'READ'),(N'RISK_ANALYST',N'EVIDENCE',N'WRITE'),
        (N'RISK_ANALYST',N'NDI',N'EXPORT'),(N'RISK_ANALYST',N'NDI',N'READ'),(N'RISK_ANALYST',N'NDI',N'WRITE'),
        (N'RISK_ANALYST',N'SUST',N'EXPORT'),(N'RISK_ANALYST',N'SUST',N'READ'),(N'RISK_ANALYST',N'WORKFLOW',N'EXPORT'),
        (N'RISK_ANALYST',N'WORKFLOW',N'READ'),(N'RISK_ANALYST',N'WORKFLOW',N'WRITE'),(N'RISK_MANAGER',N'BCM',N'EXPORT'),
        (N'RISK_MANAGER',N'BCM',N'READ'),(N'RISK_MANAGER',N'BCM',N'WRITE'),(N'RISK_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'RISK_MANAGER',N'COMPLIANCE',N'READ'),(N'RISK_MANAGER',N'COMPLIANCE',N'WRITE'),(N'RISK_OWNER',N'COMPLIANCE',N'EXPORT'),
        (N'RISK_OWNER',N'COMPLIANCE',N'READ'),(N'SECURITY_ANALYST',N'CYBER',N'EXPORT'),(N'SECURITY_ANALYST',N'CYBER',N'READ'),
        (N'SERVICE_OWNER',N'BCM',N'EXPORT'),(N'SERVICE_OWNER',N'BCM',N'READ'),(N'SERVICE_OWNER',N'BCM',N'WRITE'),
        (N'SERVICE_OWNER',N'COMPLIANCE',N'EXPORT'),(N'SERVICE_OWNER',N'COMPLIANCE',N'READ'),(N'SUPPLIER_MANAGER',N'COMPLIANCE',N'EXPORT'),
        (N'SUPPLIER_MANAGER',N'COMPLIANCE',N'READ'),(N'VIEWER',N'BCM',N'EXPORT'),(N'VIEWER',N'BCM',N'READ'),
        (N'VIEWER',N'CHANGE',N'READ'),(N'VIEWER',N'COMPLIANCE',N'EXPORT'),(N'VIEWER',N'COMPLIANCE',N'READ'),
        (N'VIEWER',N'CYBER',N'EXPORT'),(N'VIEWER',N'CYBER',N'READ'),(N'VIEWER',N'EVIDENCE',N'EXPORT'),
        (N'VIEWER',N'EVIDENCE',N'READ'),(N'VIEWER',N'NDI',N'EXPORT'),(N'VIEWER',N'NDI',N'READ'),
        (N'VIEWER',N'SIGNATURE',N'READ'),(N'VIEWER',N'SUST',N'EXPORT'),(N'VIEWER',N'SUST',N'READ'),
        (N'VIEWER',N'WORKFLOW',N'EXPORT'),(N'VIEWER',N'WORKFLOW',N'READ'),(N'VIEWER',N'WORKFLOW',N'WRITE'),
        (N'VULN_MANAGER',N'COMPLIANCE',N'EXPORT'),(N'VULN_MANAGER',N'COMPLIANCE',N'READ')
) v(role_name, module, action))
SELECT @a = COUNT(*) FROM g
JOIN iam.roles r       ON r.role_name = g.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = g.module AND p.action = g.action
JOIN iam.role_permissions rp ON rp.role_id = r.id AND rp.permission_id = p.id;
IF @a <> 188
    THROW 51372, N'V372: group A is not fully granted — a role or permission row is missing, which is the very defect this file fixes.', 1;

-- (2) every group-B grant landed
;WITH g(role_name, module, action) AS (SELECT v.role_name, v.module, v.action FROM (VALUES
        (N'BCM_MANAGER',N'SCM',N'EXPORT'),(N'BCM_MANAGER',N'SCM',N'READ'),(N'BCM_MANAGER',N'SCM',N'WRITE'),
        (N'COMPLIANCE_OFFICER',N'SCM',N'DELETE'),(N'COMPLIANCE_OFFICER',N'SCM',N'EXPORT'),(N'COMPLIANCE_OFFICER',N'SCM',N'READ'),
        (N'COMPLIANCE_OFFICER',N'SCM',N'WRITE'),(N'DPO',N'SCM',N'EXPORT'),(N'DPO',N'SCM',N'READ'),
        (N'DPO',N'SCM',N'WRITE'),(N'INTERNAL_AUDITOR',N'SCM',N'EXPORT'),(N'INTERNAL_AUDITOR',N'SCM',N'READ'),
        (N'INTERNAL_AUDITOR',N'SCM',N'WRITE'),(N'ORG_ADMIN',N'SCM',N'DELETE'),(N'ORG_ADMIN',N'SCM',N'EXPORT'),
        (N'ORG_ADMIN',N'SCM',N'READ'),(N'ORG_ADMIN',N'SCM',N'WRITE'),(N'RISK_ANALYST',N'SCM',N'EXPORT'),
        (N'RISK_ANALYST',N'SCM',N'READ'),(N'RISK_ANALYST',N'SCM',N'WRITE'),(N'RISK_MANAGER',N'SCM',N'DELETE'),
        (N'RISK_MANAGER',N'SCM',N'EXPORT'),(N'RISK_MANAGER',N'SCM',N'READ'),(N'RISK_MANAGER',N'SCM',N'WRITE'),
        (N'VIEWER',N'SCM',N'EXPORT'),(N'VIEWER',N'SCM',N'READ')
) v(role_name, module, action))
SELECT @b = COUNT(*) FROM g
JOIN iam.roles r       ON r.role_name = g.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = g.module AND p.action = g.action
JOIN iam.role_permissions rp ON rp.role_id = r.id AND rp.permission_id = p.id;
IF @b <> 26
    THROW 51372, N'V372: group B (SCM) is not fully granted.', 1;

-- (3) the auditor no longer authors what it audits
SELECT @auditor = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'AUDITOR' AND p.action = N'WRITE' AND p.module IN (N'EVIDENCE', N'NDI');
IF @auditor <> 0
    THROW 51372, N'V372: AUDITOR still holds EVIDENCE:WRITE or NDI:WRITE — SOD-02 names that pair.', 1;

-- (4) PLATFORM_ADMIN gained nothing. The evaluator bypasses in code; a grant here would make the
--     bypass look like an entitlement, and V272 says so in its own header.
SELECT @padmin = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'PLATFORM_ADMIN' AND ((p.module = N'SCM') OR (p.module = N'CYBER'));
IF @padmin NOT IN (0, 8)
    THROW 51372, N'V372: PLATFORM_ADMIN''s SCM/CYBER grants are neither the fresh-install 0 nor dev''s 8 — this file must not change them either way.', 1;

-- (5) ANTI-RESURRECTION. V367–V371 each revoked exactly one grant to remove a self-review path, and
--     V333/V335 narrowed REGULATOR_VIEWER's standing extraction. A grant-restoring migration is the
--     obvious way to undo all six by accident, so it asserts they are still gone.
SELECT @resurrect = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE (r.role_name = N'SHARIA_AUDITOR'       AND p.module = N'SHARIA'      AND p.action = N'APPROVE')
    OR (r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'APPROVE')
    OR (r.role_name = N'INTERNAL_AUDITOR'     AND p.module = N'ETHICS'      AND p.action = N'READ');
IF @resurrect <> 0
    THROW 51372, N'V372: a V367–V371 revocation has been resurrected — the self-review paths those files closed are open again.', 1;

-- ⚠ The obvious form of this check — "REGULATOR_VIEWER holds NO export" — is WRONG, and writing it is
--   how this file found out: it THROWs on dev, on prod and on a fresh install alike. F2-59/V335 revoked
--   the ELEVEN standing raw-register extracts, but F2-60/V333 first re-homed `regulator-package.zip`
--   onto REGULATORY:EXPORT so the GOVERNED channel would survive the narrowing. The invariant is
--   therefore not zero, it is exactly one, and it is that one. Asserting an absence without naming
--   what must REMAIN is the D-10a trap this programme has now measured its way out of three times.
SELECT @regv = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'REGULATOR_VIEWER' AND p.action = N'EXPORT'
   AND p.module <> N'REGULATORY';
IF @regv <> 0
    THROW 51372, N'V372: REGULATOR_VIEWER holds a raw-register EXPORT again — F2-59/V335 revoked all eleven and left only the governed REGULATORY package.', 1;

-- (5b) …and the governed channel is still THERE. An absence-only check passes on a database where the
--      supervisory authority can furnish nothing at all, which is the inverse finding D-10b named:
--      obstruction under BCL art. 18 is a defect too, not a hardening.
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                 JOIN iam.roles r ON r.id = rp.role_id
                 JOIN iam.permissions p ON p.id = rp.permission_id
                WHERE r.role_name = N'REGULATOR_VIEWER' AND p.module = N'REGULATORY'
                  AND p.action = N'EXPORT' AND r.is_deleted = 0)
    THROW 51372, N'V372: REGULATOR_VIEWER cannot export the governed regulatory package — F2-60 put it there deliberately.', 1;

-- (6) the consequence, stated as the thing a customer cares about: the tenant administrator can
--     reach the modules it administers. 0 before this file.
SELECT @orgadmin = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = N'ORG_ADMIN'
   AND p.module IN (N'BCM', N'COMPLIANCE', N'EVIDENCE', N'SCM', N'SUST', N'AI', N'NDI')
   AND p.action = N'READ';
IF @orgadmin <> 7
    THROW 51372, N'V372: ORG_ADMIN cannot READ all seven of BCM/COMPLIANCE/EVIDENCE/SCM/SUST/AI/NDI.', 1;
GO

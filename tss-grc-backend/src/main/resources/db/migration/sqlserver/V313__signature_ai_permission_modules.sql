-- =====================================================================
-- V313 (S1 / W0-4) — permission catalogue: SIGNATURE + AI modules, CAPA APPROVE.
-- ---------------------------------------------------------------------
-- PREREQUISITE for the S1/S2 authorization work. The independent assessment
-- (finding F-1) showed ~45 business-write endpoints reachable by ANY
-- authenticated principal — including the read-only VIEWER — because they carry
-- only @PreAuthorize("isAuthenticated()"). The worst of them are the
-- e-signature writes: in a platform that sells e-signed minutes as
-- tamper-evidence (ADR-019), anyone logged in could create, sign or reject a
-- signature record.
--
-- Those endpoints cannot be guarded until the modules exist in the catalogue:
-- ModulePermissionEvaluator matches the granted authority MODULE_ACTION
-- exactly, so hasPermission('SIGNATURE','APPROVE') against an uncatalogued
-- module denies EVERYONE (fail-closed — pinned by ModulePermissionEvaluatorTest).
-- Hence catalogue + grants first, guards second, in that order.
--
-- Grant model (least privilege, mirroring the V301 precedent):
--   * SIGNATURE_READ    — the signature trail is evidence: mirror WORKFLOW_READ
--                         holders (human observers keep visibility), minus the
--                         supplier + 8 machine ingest accounts.
--   * SIGNATURE_WRITE   — raising a signature transaction is a governance act.
--   * SIGNATURE_APPROVE — actually signing/rejecting.
--   * CAPA_APPROVE      — approving a root-cause analysis / corrective action.
--     ...all three granted to the roles ALREADY trusted to approve something in
--     this platform (any existing *_APPROVE holder): BCM_MANAGER,
--     COMPLIANCE_OFFICER, DPO, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN,
--     RISK_MANAGER, SUSTAINABILITY_OFFICER. VIEWER/AUDITOR/REGULATOR_VIEWER/
--     SUPPLIER_USER and the machine accounts hold no APPROVE, so they are
--     excluded by construction (and again by the explicit NOT IN, belt-and-braces).
--   * AI_READ / AI_WRITE — AI suggestion endpoints read tenant governance data
--                         and draft recommendations: practitioners only. Mirror
--                         WORKFLOW_WRITE holders minus the 14-role read-only /
--                         observer / external / machine set used by V301 part 2b.
--
-- PLATFORM_ADMIN needs no grant (evaluator bypass). REVIEW_TEST_ROLE is excluded
-- everywhere — it is test scaffolding, not a persona.
--
-- Idempotent: NOT EXISTS guards on both catalogue and grants; safe to re-run.
-- Reverse recipe (no data loss — grants and catalogue rows only):
--   DELETE rp FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
--    WHERE p.module IN ('SIGNATURE','AI') OR (p.module = 'CAPA' AND p.action = 'APPROVE');
--   DELETE FROM iam.permissions
--    WHERE module IN ('SIGNATURE','AI') OR (module = 'CAPA' AND action = 'APPROVE');
-- =====================================================================

-- ── Part 1: catalogue the new module/action pairs ─────────────────────────────
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('SIGNATURE','READ',   'View the e-signature trail on governance records'),
    ('SIGNATURE','WRITE',  'Raise / expire a signature transaction'),
    ('SIGNATURE','APPROVE','Sign or reject a signature request (e-signature authority)'),
    ('AI','READ',          'View AI suggestions and control-mapping proposals'),
    ('AI','WRITE',         'Request AI suggestions over tenant governance data'),
    ('CAPA','APPROVE',     'Approve or reject a root-cause analysis / corrective action')
) v(module, action, descr)
WHERE NOT EXISTS (
    SELECT 1 FROM iam.permissions p WHERE p.module = v.module AND p.action = v.action);
GO

-- ── Part 2: SIGNATURE_READ — mirror WORKFLOW_READ, minus supplier + machine ──
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM iam.role_permissions rp
JOIN iam.permissions wp ON wp.id = rp.permission_id AND wp.module = 'WORKFLOW' AND wp.action = 'READ'
JOIN iam.permissions np ON np.module = 'SIGNATURE' AND np.action = 'READ'
JOIN iam.roles r        ON r.id = rp.role_id
WHERE r.role_name NOT IN (
        'SUPPLIER_USER','REVIEW_TEST_ROLE',
        'APPSEC_INGEST','BRAND_INGEST','DLP_INGEST','JML_INGEST',
        'SBOM_INGEST','SCANNER_INGEST','SIEM_INGEST','TI_INGEST')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                   WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
GO

-- ── Part 3: SIGNATURE_WRITE / SIGNATURE_APPROVE / CAPA_APPROVE ───────────────
--            → roles already trusted with an APPROVE action somewhere.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT approver.role_id, np.id
FROM (
    SELECT DISTINCT rp.role_id
    FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id AND p.action = 'APPROVE'
    JOIN iam.roles r       ON r.id = rp.role_id
    WHERE r.role_name NOT IN (
        'VIEWER','REGULATOR_VIEWER','AUDITOR','INTERNAL_AUDITOR','EXTERNAL_AUDITOR',
        'SUPPLIER_USER','REVIEW_TEST_ROLE',
        'APPSEC_INGEST','BRAND_INGEST','DLP_INGEST','JML_INGEST',
        'SBOM_INGEST','SCANNER_INGEST','SIEM_INGEST','TI_INGEST')
) approver
JOIN iam.permissions np
  ON (np.module = 'SIGNATURE' AND np.action IN ('WRITE','APPROVE'))
  OR (np.module = 'CAPA'      AND np.action = 'APPROVE')
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                   WHERE x.role_id = approver.role_id AND x.permission_id = np.id);
GO

-- ── Part 4: AI_READ + AI_WRITE — practitioners (mirror WORKFLOW_WRITE holders) ─
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM iam.role_permissions rp
JOIN iam.permissions wp ON wp.id = rp.permission_id AND wp.module = 'WORKFLOW' AND wp.action = 'WRITE'
JOIN iam.permissions np ON np.module = 'AI' AND np.action IN ('READ','WRITE')
JOIN iam.roles r        ON r.id = rp.role_id
WHERE r.role_name NOT IN (
        'VIEWER','REGULATOR_VIEWER','AUDITOR','INTERNAL_AUDITOR','EXTERNAL_AUDITOR',
        'SUPPLIER_USER','REVIEW_TEST_ROLE',
        'APPSEC_INGEST','BRAND_INGEST','DLP_INGEST','JML_INGEST',
        'SBOM_INGEST','SCANNER_INGEST','SIEM_INGEST','TI_INGEST')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                   WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
GO

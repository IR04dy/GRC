-- V280 (RBAC Access-Governance P0 — plan RBAC_ACCESS_GOVERNANCE_MODULE_PLAN.md §7):
-- foundation data changes for governing (not just enforcing) RBAC.
--
-- 1) iam.user_roles gains the assignment-lifecycle columns (validity window,
--    justification, approval, source, soft-delete) — additive + guarded, so the
--    existing bare (user_id, role_id) join rows keep working unchanged. The
--    service-layer logic that uses them lands in P1; the columns ship first so
--    the P0 console can already display them.
-- 2) Catalogue IAM_WRITE / IAM_APPROVE / IAM_DELETE (IAM had READ only — every
--    role/grant mutation was PLATFORM_ADMIN-only by construction).
-- 3) Least-privilege grants by role_name across all tenants (V272 idiom):
--    IAM_LEAD gets READ+WRITE (operates the console), GRC_ADMINISTRATOR gets
--    READ+WRITE+APPROVE+DELETE (approves + owns role lifecycle). 4-eyes stays
--    intact: WRITE proposes, APPROVE activates (enforced in the P1 service).

-- ── 1) assignment-lifecycle columns ─────────────────────────────────────────
IF COL_LENGTH('iam.user_roles', 'valid_from') IS NULL
    ALTER TABLE iam.user_roles ADD valid_from DATETIME2 NULL;
IF COL_LENGTH('iam.user_roles', 'valid_to') IS NULL
    ALTER TABLE iam.user_roles ADD valid_to DATETIME2 NULL;
IF COL_LENGTH('iam.user_roles', 'justification') IS NULL
    ALTER TABLE iam.user_roles ADD justification NVARCHAR(1000) NULL;
IF COL_LENGTH('iam.user_roles', 'approved_by') IS NULL
    ALTER TABLE iam.user_roles ADD approved_by BIGINT NULL;
IF COL_LENGTH('iam.user_roles', 'status') IS NULL
    ALTER TABLE iam.user_roles ADD status NVARCHAR(20) NOT NULL CONSTRAINT df_user_roles_status DEFAULT 'ACTIVE';
IF COL_LENGTH('iam.user_roles', 'source') IS NULL
    ALTER TABLE iam.user_roles ADD source NVARCHAR(20) NOT NULL CONSTRAINT df_user_roles_source DEFAULT 'ADMIN';
IF COL_LENGTH('iam.user_roles', 'is_deleted') IS NULL
    ALTER TABLE iam.user_roles ADD is_deleted BIT NOT NULL CONSTRAINT df_user_roles_is_deleted DEFAULT 0;

-- ── 2) catalogue the IAM governance actions ────────────────────────────────
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('IAM', 'WRITE',   'Manage roles, permission matrix and assignments (propose)'),
    ('IAM', 'APPROVE', 'Approve access requests and role activations (checker)'),
    ('IAM', 'DELETE',  'Retire roles and revoke assignments')
) v(module, action, descr)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                  WHERE p.module = v.module AND p.action = v.action);

-- ── 3) grants by role_name (all tenants, idempotent) ───────────────────────
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('IAM_LEAD',          'IAM', 'READ'),
    ('IAM_LEAD',          'IAM', 'WRITE'),
    ('GRC_ADMINISTRATOR', 'IAM', 'READ'),
    ('GRC_ADMINISTRATOR', 'IAM', 'WRITE'),
    ('GRC_ADMINISTRATOR', 'IAM', 'APPROVE'),
    ('GRC_ADMINISTRATOR', 'IAM', 'DELETE')
) m(role_name, module, action)
JOIN iam.roles r        ON r.role_name = m.role_name AND r.is_deleted = 0
JOIN iam.permissions p  ON p.module = m.module AND p.action = m.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

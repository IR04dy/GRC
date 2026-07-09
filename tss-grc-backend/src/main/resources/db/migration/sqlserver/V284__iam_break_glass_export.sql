-- V284 (RBAC Access-Governance P3 — plan §6.6/§6.7/§4.3): break-glass emergency
-- access, the privileged-role flag, and the EXPORT 6th action.
--  a) iam.roles.is_privileged — explicit flag (seeded: named admin roles + any role
--     granting APPROVE/DELETE); drives analytics + tighter review cadence.
--  b) iam.break_glass_events — activation ledger: mandatory incident ref, ≤24h
--     validity, auto-expiry, mandatory post-hoc review (PENDING_REVIEW → REVIEWED).
--  c) EMERGENCY_RESPONDER global template role (system, privileged) with an
--     operational-response grant set (incident/cyber/BCM/evidence W, risk/compliance R).
--  d) IAM/BREAK_GLASS dedicated permission — activation gate (CISO, IR_LEAD,
--     GRC_ADMINISTRATOR; PLATFORM_ADMIN bypasses in code).
--  e) EXPORT as a 6th standard action for every module that has READ, with
--     READ-parity default grants (behaviour unchanged until a tenant tightens).

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('iam.roles') AND name = 'is_privileged')
    ALTER TABLE iam.roles ADD is_privileged BIT NOT NULL DEFAULT 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'break_glass_events' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.break_glass_events (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id       BIGINT         NOT NULL,
        activated_by          BIGINT         NOT NULL,
        activated_by_username NVARCHAR(100)  NOT NULL,
        role_id               BIGINT         NOT NULL,      -- the emergency role granted
        incident_ref          NVARCHAR(100)  NOT NULL,      -- mandatory: ties to an incident
        reason                NVARCHAR(1000) NULL,
        activated_at          DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
        expires_at            DATETIME2      NOT NULL,      -- ≤ 24h after activation
        status                NVARCHAR(15)   NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | EXPIRED
        review_status         NVARCHAR(20)   NOT NULL DEFAULT 'PENDING_REVIEW',
        -- PENDING_REVIEW | REVIEWED (post-hoc review is mandatory)
        reviewed_by           BIGINT         NULL,
        reviewed_at           DATETIME2      NULL,
        review_note           NVARCHAR(1000) NULL,
        is_deleted            BIT            NOT NULL DEFAULT 0,
        created_at            DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_bge_org_status ON iam.break_glass_events(organisation_id, status);
END

-- ── EMERGENCY_RESPONDER global template (pre-provisioned, plan §6.6) ─────────
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = 'EMERGENCY_RESPONDER' AND organisation_id IS NULL AND is_deleted = 0)
INSERT INTO iam.roles (external_id, organisation_id, role_name, display_name_en, display_name_ar,
                       description_en, is_system_role, is_active, is_privileged)
VALUES (NEWID(), NULL, 'EMERGENCY_RESPONDER', N'Emergency Responder (Break-glass)',
        N'المستجيب للطوارئ (الوصول الاستثنائي)',
        N'Pre-provisioned break-glass role: operational write access during a declared incident, max 24h, mandatory post-hoc review.',
        1, 1, 1);

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('INCIDENT','READ'),('INCIDENT','WRITE'),
    ('CYBER','READ'),('CYBER','WRITE'),
    ('BCM','READ'),('BCM','WRITE'),
    ('EVIDENCE','READ'),('EVIDENCE','WRITE'),
    ('RISK','READ'),('COMPLIANCE','READ'),('REPORTING','READ')
) v(module, action)
JOIN iam.roles r ON r.role_name = 'EMERGENCY_RESPONDER' AND r.organisation_id IS NULL AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = v.module AND p.action = v.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- ── dedicated BREAK_GLASS activation permission (plan §8) ────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'IAM' AND action = 'BREAK_GLASS')
INSERT INTO iam.permissions (module, action, permission_name, description_en)
VALUES ('IAM', 'BREAK_GLASS', 'IAM_BREAK_GLASS', 'Activate break-glass emergency access (incident-bound, max 24h)');

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.module = 'IAM' AND p.action = 'BREAK_GLASS'
WHERE r.role_name IN ('CISO', 'IR_LEAD', 'GRC_ADMINISTRATOR') AND r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- ── privileged flag seed (named admin roles + any role granting APPROVE/DELETE) ─
UPDATE iam.roles SET is_privileged = 1
WHERE is_deleted = 0 AND (
      role_name IN ('PLATFORM_ADMIN', 'GRC_ADMINISTRATOR', 'EMERGENCY_RESPONDER')
   OR id IN (SELECT DISTINCT rp.role_id FROM iam.role_permissions rp
             JOIN iam.permissions p ON p.id = rp.permission_id
             WHERE p.action IN ('APPROVE', 'DELETE')));

-- ── EXPORT 6th action (plan §4.3) — catalogue + READ-parity default grants ───
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT DISTINCT p.module, 'EXPORT', p.module + '_EXPORT', 'Export/download ' + p.module + ' registers and reports'
FROM iam.permissions p
WHERE p.action = 'READ'
  AND NOT EXISTS (SELECT 1 FROM iam.permissions x WHERE x.module = p.module AND x.action = 'EXPORT');

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT rp.role_id, pe.id
FROM iam.role_permissions rp
JOIN iam.permissions pr ON pr.id = rp.permission_id AND pr.action = 'READ'
JOIN iam.permissions pe ON pe.module = pr.module AND pe.action = 'EXPORT'
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = pe.id);

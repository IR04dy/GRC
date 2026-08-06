-- V272 (R-2): RBAC catalogue completion — every hasPermission() pair catalogued,
-- every seeded role given a working persona-appropriate grant set.
--
-- Inventory (2026-07-06, code scan of @PreAuthorize across all controllers):
--   * 24 modules referenced by hasPermission('MODULE','ACTION'); 32 of the used
--     (module, action) pairs were MISSING from iam.permissions entirely — those
--     endpoints (INCIDENT, CAPA, TPRM, REGULATORY, CONTROL_LIB, AUDIT, AUDIT_LOG,
--     INTAKE, PLATFORM, AUTOMATION, IAM, WORKFLOW approve/delete, …) were
--     therefore PLATFORM_ADMIN-only for every tenant.
--   * RISK_READ/WRITE/DELETE and PRIVACY_READ/WRITE existed in the catalogue but
--     were granted to NO role (the risk and privacy modules were admin-only);
--     CYBER_WRITE/DELETE were held only by PLATFORM_ADMIN.
--
-- Part 1 catalogues the missing pairs. Part 2 grants a least-privilege persona
-- matrix keyed by role_name (idempotent NOT EXISTS guards; applies to every
-- tenant's roles with these names). PLATFORM_ADMIN needs no grants (evaluator
-- bypass); PLATFORM_ADMIN_* pairs are catalogued but deliberately granted to
-- nobody. Tenants can tune everything later in the role admin screen.

-- ── Part 1: catalogue every code-used (module, action) pair ────────────────
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('AUDIT',          'READ',   'View audit engagements and universe'),
    ('AUDIT',          'WRITE',  'Create/update audit engagements, findings, plans'),
    ('AUDIT',          'DELETE', 'Delete audit records'),
    ('AUDIT_LOG',      'READ',   'View the platform audit log'),
    ('AUTOMATION',     'WRITE',  'Create/update automation rules'),
    ('AUTOMATION',     'DELETE', 'Delete automation rules'),
    ('CAPA',           'READ',   'View corrective/preventive actions'),
    ('CAPA',           'WRITE',  'Create/update corrective/preventive actions'),
    ('CONTROL_LIB',    'READ',   'View the unified control library'),
    ('CONTROL_LIB',    'WRITE',  'Create/update control-library entries and mappings'),
    ('CONTROL_LIB',    'DELETE', 'Delete control-library entries'),
    ('IAM',            'READ',   'View users and roles'),
    ('INCIDENT',       'READ',   'View the incident register'),
    ('INCIDENT',       'WRITE',  'Create/update incidents'),
    ('INCIDENT',       'DELETE', 'Delete incidents'),
    ('INTAKE',         'READ',   'View intake/whistleblower/complaint cases'),
    ('INTAKE',         'WRITE',  'Create/update intake cases'),
    ('INTAKE',         'TRIAGE', 'Triage and route intake cases'),
    ('PLATFORM',       'READ',   'View platform configuration'),
    ('PLATFORM',       'WRITE',  'Update platform configuration'),
    ('PLATFORM_ADMIN', 'READ',   'Platform-operator read surface (admin only)'),
    ('PLATFORM_ADMIN', 'WRITE',  'Platform-operator write surface (admin only)'),
    ('PRIVACY',        'DELETE', 'Delete privacy records'),
    ('REGULATORY',     'READ',   'View regulatory intelligence/obligations'),
    ('REGULATORY',     'WRITE',  'Create/update regulatory records'),
    ('REGULATORY',     'DELETE', 'Delete regulatory records'),
    ('REPORTING',      'DELETE', 'Delete reports and schedules'),
    ('TPRM',           'READ',   'View third-party risk records'),
    ('TPRM',           'WRITE',  'Create/update third-party risk records'),
    ('TPRM',           'DELETE', 'Delete third-party risk records'),
    ('WORKFLOW',       'APPROVE','Approve workflow submissions/approval chains'),
    ('WORKFLOW',       'DELETE', 'Delete workflow definitions/records'),

    -- ── F2-143 (2026-08-06) ────────────────────────────────────────────────────
    -- Added when F2-77's chain proof first executed V1->V320 and showed that a
    -- database built from this folder held 79 of 115 permissions. This file's own
    -- header claims "every hasPermission() pair catalogued"; it was true for the 32
    -- pairs measured on 2026-07-06 and has not been true since.
    --
    -- Each pair below is enforced by hasPermission(...) in compiled code, present in
    -- the live catalogue, and created by no migration that runs before V321 — whose
    -- THROW ("REPORTING:WRITE is still held by no role") is what surfaced this.
    -- Sourced from that three-way intersection, derived twice and compared.
    --
    -- Deliberately EXCLUDED: PRIVACY:EXPORT and SCM:EXPORT are live and absent from a
    -- chain build but enforced by no code. A catalogued permission that guards nothing
    -- is F2-13b's anti-pattern and 15 such rows already exist.
    ('ADMIN',          'READ',   'View Admin records'),
    ('ADMIN',          'WRITE',  'Create/update Admin records'),
    ('ADMIN',          'EXPORT', 'Export/download ADMIN registers and reports'),
    ('AUTOMATION',     'READ',   'View automation rules, their fires, and notification reachability'),
    ('BCM',            'READ',   'View Bcm records'),
    ('BCM',            'WRITE',  'Create/update Bcm records'),
    ('BCM',            'DELETE', 'Delete Bcm records'),
    ('BCM',            'EXPORT', 'Export/download BCM registers and reports'),
    ('BOARD',          'EXPORT', 'Download the board pack (PDF/PPTX) — board and committee reporting material'),
    ('COMPLIANCE',     'READ',   'View Compliance records'),
    ('COMPLIANCE',     'WRITE',  'Create/update Compliance records'),
    ('COMPLIANCE',     'EXPORT', 'Export/download COMPLIANCE registers and reports'),
    ('CYBER',          'READ',   'View Cyber records'),
    ('CYBER',          'WRITE',  'Create/update Cyber records'),
    ('CYBER',          'DELETE', 'Delete Cyber records'),
    ('CYBER',          'EXPORT', 'Export/download CYBER registers and reports'),
    ('FRAUD',          'READ',   'View fraud cases, typologies and the fraud dashboard'),
    ('FRAUD',          'WRITE',  'Open, triage, update, resolve and close fraud cases'),
    ('INTEGRATIONS',   'READ',   'View integration health, bound configuration (secrets masked) and transmission timestamps'),
    ('INTEGRATIONS',   'WRITE',  'Administer integrations: transmit to a third party and read recorded request payloads'),
    ('KB',             'WRITE',  'Create, edit, publish and archive knowledge-base articles'),
    ('PRIVACY',        'READ',   'View Privacy records'),
    ('PRIVACY',        'WRITE',  'Create/update Privacy records'),
    ('REPORTING',      'READ',   'View Reporting records'),
    ('REPORTING',      'WRITE',  'Create/update Reporting records'),
    ('REPORTING',      'EXPORT', 'Export/download REPORTING registers and reports'),
    ('RISK',           'READ',   'View Risk records'),
    ('RISK',           'WRITE',  'Create/update Risk records'),
    ('RISK',           'DELETE', 'Delete Risk records'),
    ('RISK',           'EXPORT', 'Export/download RISK registers and reports'),
    ('SCM',            'READ',   'View supply chain vendors, assessments, dependencies and incidents'),
    ('SCM',            'WRITE',  'Create and update supply chain records, manage onboarding and assessments'),
    ('SCM',            'DELETE', 'Delete supply chain records'),
    ('TPRM',           'APPROVE','Approve or retire a third-party cyber assessment')
) v(module, action, descr)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                  WHERE p.module = v.module AND p.action = v.action);
GO

-- ── F2-143 (2026-08-06): global system roles this folder grants to and never creates ──
-- ⚠ PLACED BEFORE PART 2 ON PURPOSE. Part 2 grants by role_name, so a role
-- created after it receives nothing. Appending this block at the end of the file instead made
-- V325 fail with "ORG_ADMIN lost WORKFLOW:DELETE" -- one of this programme's own verification
-- THROWs catching an ordering mistake inside a fix for an ordering defect.

-- ⚠ Minimal column set on purpose. iam.roles gains is_privileged, is_system_role and
-- description_en in migrations LATER than V272, and SQL Server binds column names at compile time
-- for the whole batch -- naming them here made the batch fail with "Invalid column name" on a
-- first ordered run. Only the NOT NULL columns are supplied; the rest take their defaults, and
-- whichever migration adds them owns their values.
--
-- A chain-built database held 54 of 58 roles. ORG_ADMIN is the primary tenant administrator, so
-- F2-81's tenant onboarding provisioned a first administrator into a role a fresh installation
-- did not have; VIEWER and RISK_ANALYST are documented dev personas that could not be assigned.
--
-- Which roles are missing comes from diffing a chain build against the live catalogue. The
-- bilingual labels are the platform's own human-authored ones -- display_name_ar is NOT NULL
-- (house rule 7), and copying an existing translation is not fabricating a fact.

-- ⚠ is_system_role = 1 on all four, added 2026-08-06 (F2-77/V334).
--
-- These four went in with organisation_id NULL and no is_system_role, so it defaulted to 0:
-- global roles that the platform does not consider platform-shipped. V334 exists to make
-- system roles global and then VERIFY that nothing non-system became global, and on a
-- Flyway-built database its third check threw on exactly these rows.
--
-- ⚠ V334's message says "the UPDATE predicate must key on is_system_role = 1 only" and that
-- is a misdiagnosis: the predicate already does. The rows were global BEFORE V334 ran,
-- inserted that way here. V334 cannot be corrected (it is above the deployed baseline of 292
-- and its checksum is recorded), so the note lives here instead.
--
-- What the flag protects, measured rather than assumed: AccessGovernanceService blocks
-- rename (:182), deactivation (:208) and deletion (:225) for system roles. Without it these
-- four -- including ORG_ADMIN, the role F2-81's onboarding provisions into -- are
-- deletable. ⚠ NOT cross-tenant: mustOwnRole (:349) makes any global role read-only from a
-- tenant unless the caller is PLATFORM_ADMIN, so the reachable blast radius is a platform
-- admin deleting a shipped persona, not one tenant touching another's. Recorded because
-- overstating it would be as wrong as missing it.

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'ORG_ADMIN')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, organisation_id, is_system_role)
    VALUES (N'ORG_ADMIN', N'Organisation Administrator', N'مدير المنظمة', NULL, 1);

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'VIEWER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, organisation_id, is_system_role)
    VALUES (N'VIEWER', N'Read-Only Viewer', N'مشاهد', NULL, 1);

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'RISK_ANALYST')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, organisation_id, is_system_role)
    VALUES (N'RISK_ANALYST', N'Risk Analyst', N'محلل المخاطر', NULL, 1);

IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'FRAUD_INVESTIGATOR')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, organisation_id, is_system_role)
    VALUES (N'FRAUD_INVESTIGATOR', N'Fraud Investigator', N'مُحقِّق الاحتيال', NULL, 1);
GO

-- ── Part 2: persona grant matrix (by role_name, idempotent) ─────────────────
-- ⚠ F2-143/V324: WORKFLOW:READ added beside every WORKFLOW:APPROVE below. This file has
-- granted APPROVE to three roles since 2026-07-06 and READ to none of them, so each could decide a
-- request it could not read. V324 has a guard for exactly that and it never fired, because the
-- roles did not exist in a folder-built database -- the grants were silent no-ops. Creating the
-- roles is what made the guard able to speak.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- Tenant / GRC administrators: full business-module control
    ('ORG_ADMIN','RISK','READ'),('ORG_ADMIN','RISK','WRITE'),('ORG_ADMIN','RISK','DELETE'),('ORG_ADMIN','RISK','APPROVE'),
    ('ORG_ADMIN','PRIVACY','READ'),('ORG_ADMIN','PRIVACY','WRITE'),('ORG_ADMIN','PRIVACY','DELETE'),
    ('ORG_ADMIN','CYBER','WRITE'),('ORG_ADMIN','CYBER','DELETE'),
    ('ORG_ADMIN','INCIDENT','READ'),('ORG_ADMIN','INCIDENT','WRITE'),('ORG_ADMIN','INCIDENT','DELETE'),
    ('ORG_ADMIN','CAPA','READ'),('ORG_ADMIN','CAPA','WRITE'),
    ('ORG_ADMIN','TPRM','READ'),('ORG_ADMIN','TPRM','WRITE'),('ORG_ADMIN','TPRM','DELETE'),
    ('ORG_ADMIN','REGULATORY','READ'),('ORG_ADMIN','REGULATORY','WRITE'),('ORG_ADMIN','REGULATORY','DELETE'),
    ('ORG_ADMIN','CONTROL_LIB','READ'),('ORG_ADMIN','CONTROL_LIB','WRITE'),('ORG_ADMIN','CONTROL_LIB','DELETE'),
    ('ORG_ADMIN','AUDIT','READ'),('ORG_ADMIN','AUDIT','WRITE'),('ORG_ADMIN','AUDIT','DELETE'),
    ('ORG_ADMIN','AUDIT_LOG','READ'),
    ('ORG_ADMIN','INTAKE','READ'),('ORG_ADMIN','INTAKE','WRITE'),('ORG_ADMIN','INTAKE','TRIAGE'),
    ('ORG_ADMIN','REPORTING','DELETE'),
    ('ORG_ADMIN','AUTOMATION','WRITE'),('ORG_ADMIN','AUTOMATION','DELETE'),
    ('ORG_ADMIN','PLATFORM','READ'),('ORG_ADMIN','PLATFORM','WRITE'),
    ('ORG_ADMIN','IAM','READ'),
    ('ORG_ADMIN','WORKFLOW','READ'),('ORG_ADMIN','WORKFLOW','APPROVE'),('ORG_ADMIN','WORKFLOW','DELETE'),

    ('GRC_ADMINISTRATOR','RISK','READ'),('GRC_ADMINISTRATOR','RISK','WRITE'),('GRC_ADMINISTRATOR','RISK','DELETE'),
    ('GRC_ADMINISTRATOR','PRIVACY','READ'),('GRC_ADMINISTRATOR','PRIVACY','WRITE'),('GRC_ADMINISTRATOR','PRIVACY','DELETE'),
    ('GRC_ADMINISTRATOR','INCIDENT','READ'),('GRC_ADMINISTRATOR','INCIDENT','WRITE'),('GRC_ADMINISTRATOR','INCIDENT','DELETE'),
    ('GRC_ADMINISTRATOR','CAPA','READ'),('GRC_ADMINISTRATOR','CAPA','WRITE'),
    ('GRC_ADMINISTRATOR','TPRM','READ'),('GRC_ADMINISTRATOR','TPRM','WRITE'),('GRC_ADMINISTRATOR','TPRM','DELETE'),
    ('GRC_ADMINISTRATOR','REGULATORY','READ'),('GRC_ADMINISTRATOR','REGULATORY','WRITE'),('GRC_ADMINISTRATOR','REGULATORY','DELETE'),
    ('GRC_ADMINISTRATOR','CONTROL_LIB','READ'),('GRC_ADMINISTRATOR','CONTROL_LIB','WRITE'),('GRC_ADMINISTRATOR','CONTROL_LIB','DELETE'),
    ('GRC_ADMINISTRATOR','AUDIT','READ'),('GRC_ADMINISTRATOR','AUDIT','WRITE'),('GRC_ADMINISTRATOR','AUDIT','DELETE'),
    ('GRC_ADMINISTRATOR','AUDIT_LOG','READ'),
    ('GRC_ADMINISTRATOR','INTAKE','READ'),('GRC_ADMINISTRATOR','INTAKE','WRITE'),('GRC_ADMINISTRATOR','INTAKE','TRIAGE'),
    ('GRC_ADMINISTRATOR','AUTOMATION','WRITE'),('GRC_ADMINISTRATOR','AUTOMATION','DELETE'),
    ('GRC_ADMINISTRATOR','WORKFLOW','READ'),('GRC_ADMINISTRATOR','WORKFLOW','APPROVE'),

    -- Risk pillar
    ('RISK_MANAGER','RISK','READ'),('RISK_MANAGER','RISK','WRITE'),('RISK_MANAGER','RISK','DELETE'),
    ('RISK_MANAGER','INCIDENT','READ'),('RISK_MANAGER','INCIDENT','WRITE'),
    ('RISK_MANAGER','CAPA','READ'),('RISK_MANAGER','CAPA','WRITE'),
    ('RISK_MANAGER','CONTROL_LIB','READ'),('RISK_MANAGER','WORKFLOW','READ'),('RISK_MANAGER','WORKFLOW','APPROVE'),
    ('RISK_ANALYST','RISK','READ'),('RISK_ANALYST','RISK','WRITE'),
    ('RISK_ANALYST','INCIDENT','READ'),('RISK_ANALYST','INCIDENT','WRITE'),
    ('RISK_ANALYST','CAPA','READ'),('RISK_ANALYST','CAPA','WRITE'),('RISK_ANALYST','CONTROL_LIB','READ'),
    ('RISK_OWNER','RISK','READ'),('RISK_OWNER','RISK','WRITE'),('RISK_OWNER','CAPA','READ'),('RISK_OWNER','INCIDENT','READ'),
    ('EXECUTIVE_REVIEWER','RISK','READ'),('EXECUTIVE_REVIEWER','INCIDENT','READ'),('EXECUTIVE_REVIEWER','CAPA','READ'),
    ('EXECUTIVE_REVIEWER','AUDIT','READ'),('EXECUTIVE_REVIEWER','REGULATORY','READ'),('EXECUTIVE_REVIEWER','REPORTING','READ'),

    -- Compliance pillar
    ('COMPLIANCE_OFFICER','REGULATORY','READ'),('COMPLIANCE_OFFICER','REGULATORY','WRITE'),('COMPLIANCE_OFFICER','REGULATORY','DELETE'),
    ('COMPLIANCE_OFFICER','CONTROL_LIB','READ'),('COMPLIANCE_OFFICER','CONTROL_LIB','WRITE'),
    ('COMPLIANCE_OFFICER','CAPA','READ'),('COMPLIANCE_OFFICER','CAPA','WRITE'),
    ('COMPLIANCE_OFFICER','INTAKE','READ'),('COMPLIANCE_OFFICER','INTAKE','WRITE'),('COMPLIANCE_OFFICER','INTAKE','TRIAGE'),
    ('COMPLIANCE_OFFICER','RISK','READ'),('COMPLIANCE_OFFICER','INCIDENT','READ'),('COMPLIANCE_OFFICER','REPORTING','READ'),
    ('NCA_COMPLIANCE','REGULATORY','READ'),('NCA_COMPLIANCE','REGULATORY','WRITE'),
    ('NCA_COMPLIANCE','CONTROL_LIB','READ'),('NCA_COMPLIANCE','CAPA','READ'),('NCA_COMPLIANCE','CAPA','WRITE'),
    ('LEGAL_REVIEWER','REGULATORY','READ'),('LEGAL_REVIEWER','INTAKE','READ'),
    ('LEGAL_COUNSEL','REGULATORY','READ'),('LEGAL_COUNSEL','INTAKE','READ'),

    -- Privacy pillar
    ('DPO','PRIVACY','READ'),('DPO','PRIVACY','WRITE'),('DPO','PRIVACY','DELETE'),
    ('DPO','INCIDENT','READ'),('DPO','CAPA','READ'),('DPO','CAPA','WRITE'),('DPO','REGULATORY','READ'),('DPO','REPORTING','READ'),
    ('PRIVACY_MANAGER','PRIVACY','READ'),('PRIVACY_MANAGER','PRIVACY','WRITE'),('PRIVACY_MANAGER','PRIVACY','DELETE'),
    ('PRIVACY_MANAGER','INCIDENT','READ'),('PRIVACY_MANAGER','CAPA','READ'),('PRIVACY_MANAGER','CAPA','WRITE'),('PRIVACY_MANAGER','REPORTING','READ'),
    ('DATA_OWNER','PRIVACY','READ'),

    -- Cyber pillar
    ('CISO','CYBER','WRITE'),('CISO','INCIDENT','READ'),('CISO','INCIDENT','WRITE'),
    ('CISO','RISK','READ'),('CISO','CAPA','READ'),('CISO','CAPA','WRITE'),
    ('CISO','REGULATORY','READ'),('CISO','CONTROL_LIB','READ'),('CISO','REPORTING','READ'),('CISO','AUDIT_LOG','READ'),
    ('CYBER_MANAGER','CYBER','WRITE'),('CYBER_MANAGER','CYBER','DELETE'),
    ('CYBER_MANAGER','INCIDENT','READ'),('CYBER_MANAGER','INCIDENT','WRITE'),('CYBER_MANAGER','INCIDENT','DELETE'),
    ('CYBER_MANAGER','CAPA','READ'),('CYBER_MANAGER','CAPA','WRITE'),('CYBER_MANAGER','CONTROL_LIB','READ'),('CYBER_MANAGER','REPORTING','READ'),
    ('SECURITY_ANALYST','CYBER','WRITE'),('SECURITY_ANALYST','INCIDENT','READ'),('SECURITY_ANALYST','INCIDENT','WRITE'),('SECURITY_ANALYST','CAPA','READ'),
    ('IR_LEAD','CYBER','READ'),('IR_LEAD','CYBER','WRITE'),('IR_LEAD','INCIDENT','READ'),('IR_LEAD','INCIDENT','WRITE'),
    ('FORENSICS_LEAD','CYBER','READ'),('FORENSICS_LEAD','INCIDENT','READ'),('FORENSICS_LEAD','INCIDENT','WRITE'),
    ('VULN_MANAGER','CYBER','READ'),('VULN_MANAGER','CYBER','WRITE'),('VULN_MANAGER','INCIDENT','READ'),('VULN_MANAGER','CAPA','READ'),('VULN_MANAGER','CAPA','WRITE'),
    ('APPSEC_LEAD','CYBER','READ'),('APPSEC_LEAD','CYBER','WRITE'),
    ('OT_LEAD','CYBER','READ'),('OT_LEAD','CYBER','WRITE'),('OT_LEAD','INCIDENT','READ'),('OT_LEAD','INCIDENT','WRITE'),
    ('ASSET_OWNER','CYBER','READ'),('ASSET_OWNER','INCIDENT','READ'),
    ('DLP_REVIEWER','CYBER','READ'),('DLP_REVIEWER','INCIDENT','READ'),
    ('BRAND_MONITORING','CYBER','READ'),
    -- Machine ingest accounts: telemetry write only
    ('SIEM_INGEST','CYBER','WRITE'),('TI_INGEST','CYBER','WRITE'),('DLP_INGEST','CYBER','WRITE'),
    ('SBOM_INGEST','CYBER','WRITE'),('SCANNER_INGEST','CYBER','WRITE'),('APPSEC_INGEST','CYBER','WRITE'),
    ('BRAND_INGEST','CYBER','WRITE'),('JML_INGEST','CYBER','WRITE'),

    -- Audit pillar
    ('INTERNAL_AUDITOR','AUDIT','READ'),('INTERNAL_AUDITOR','AUDIT','WRITE'),('INTERNAL_AUDITOR','AUDIT','DELETE'),
    ('INTERNAL_AUDITOR','AUDIT_LOG','READ'),('INTERNAL_AUDITOR','RISK','READ'),('INTERNAL_AUDITOR','INCIDENT','READ'),
    ('INTERNAL_AUDITOR','CAPA','READ'),('INTERNAL_AUDITOR','CAPA','WRITE'),('INTERNAL_AUDITOR','REGULATORY','READ'),
    ('INTERNAL_AUDITOR','CONTROL_LIB','READ'),('INTERNAL_AUDITOR','PRIVACY','READ'),('INTERNAL_AUDITOR','TPRM','READ'),
    ('INTERNAL_AUDITOR','REPORTING','READ'),('INTERNAL_AUDITOR','INTAKE','READ'),
    -- AUDITOR: the read-everything assurance persona
    ('AUDITOR','RISK','READ'),('AUDITOR','PRIVACY','READ'),('AUDITOR','INCIDENT','READ'),('AUDITOR','CAPA','READ'),
    ('AUDITOR','TPRM','READ'),('AUDITOR','REGULATORY','READ'),('AUDITOR','CONTROL_LIB','READ'),('AUDITOR','AUDIT','READ'),
    ('AUDITOR','AUDIT_LOG','READ'),('AUDITOR','REPORTING','READ'),('AUDITOR','INTAKE','READ'),
    ('AUDITOR','COMPLIANCE','READ'),('AUDITOR','BCM','READ'),('AUDITOR','SCM','READ'),('AUDITOR','SUST','READ'),
    ('AUDITOR','EVIDENCE','READ'),('AUDITOR','NDI','READ'),
    ('EXTERNAL_AUDITOR','RISK','READ'),('EXTERNAL_AUDITOR','CAPA','READ'),('EXTERNAL_AUDITOR','REGULATORY','READ'),
    ('EXTERNAL_AUDITOR','AUDIT','READ'),('EXTERNAL_AUDITOR','REPORTING','READ'),
    ('REGULATOR_VIEWER','RISK','READ'),('REGULATOR_VIEWER','INCIDENT','READ'),('REGULATOR_VIEWER','REGULATORY','READ'),('REGULATOR_VIEWER','REPORTING','READ'),

    -- Third-party pillar
    ('SUPPLIER_MANAGER','TPRM','READ'),('SUPPLIER_MANAGER','TPRM','WRITE'),('SUPPLIER_MANAGER','TPRM','DELETE'),
    ('SUPPLIER_MANAGER','SCM','READ'),('SUPPLIER_MANAGER','SCM','WRITE'),('SUPPLIER_MANAGER','SCM','DELETE'),
    ('SUPPLIER_MANAGER','CAPA','READ'),('SUPPLIER_MANAGER','CAPA','WRITE'),('SUPPLIER_MANAGER','RISK','READ'),
    ('PROCUREMENT_OWNER','TPRM','READ'),('PROCUREMENT_OWNER','TPRM','WRITE'),('PROCUREMENT_OWNER','SCM','READ'),('PROCUREMENT_OWNER','SCM','WRITE'),
    ('SUPPLIER_USER','TPRM','READ'),
    ('SERVICE_OWNER','TPRM','READ'),('SERVICE_OWNER','INCIDENT','READ'),

    -- Resilience pillar (BCM grants already exist; add the incident/CAPA links)
    ('BCM_MANAGER','INCIDENT','READ'),('BCM_MANAGER','INCIDENT','WRITE'),
    ('BCM_MANAGER','CAPA','READ'),('BCM_MANAGER','CAPA','WRITE'),('BCM_MANAGER','RISK','READ'),('BCM_MANAGER','REPORTING','READ'),
    ('CRISIS_MANAGER','INCIDENT','READ'),('CRISIS_MANAGER','INCIDENT','WRITE'),('CRISIS_MANAGER','CAPA','READ'),
    ('BIA_OWNER','INCIDENT','READ'),('DR_OWNER','INCIDENT','READ'),

    -- ESG pillar
    ('SUSTAINABILITY_OFFICER','CAPA','READ'),('SUSTAINABILITY_OFFICER','CAPA','WRITE'),
    ('SUSTAINABILITY_OFFICER','EVIDENCE','READ'),('SUSTAINABILITY_OFFICER','EVIDENCE','WRITE'),
    ('SUSTAINABILITY_OFFICER','REPORTING','READ'),('SUSTAINABILITY_OFFICER','TPRM','READ'),

    -- Content owners
    ('EVIDENCE_OWNER','EVIDENCE','READ'),('EVIDENCE_OWNER','EVIDENCE','WRITE'),
    ('CONTROL_OWNER','CONTROL_LIB','READ'),('CONTROL_OWNER','CONTROL_LIB','WRITE'),
    ('CONTROL_OWNER','CAPA','READ'),('CONTROL_OWNER','CAPA','WRITE'),
    ('CONTROL_OWNER','EVIDENCE','READ'),('CONTROL_OWNER','EVIDENCE','WRITE'),

    -- Executive / support personas (read surfaces)
    ('VIEWER','RISK','READ'),('VIEWER','PRIVACY','READ'),('VIEWER','INCIDENT','READ'),('VIEWER','CAPA','READ'),
    ('VIEWER','TPRM','READ'),('VIEWER','REGULATORY','READ'),('VIEWER','CONTROL_LIB','READ'),('VIEWER','AUDIT','READ'),('VIEWER','REPORTING','READ'),
    ('BOARD_SECRETARY','REPORTING','READ'),('BOARD_SECRETARY','RISK','READ'),('BOARD_SECRETARY','AUDIT','READ'),
    ('CFO','RISK','READ'),('CFO','REPORTING','READ'),('CFO','TPRM','READ'),
    ('FINANCE_LEAD','RISK','READ'),('FINANCE_LEAD','REPORTING','READ'),('FINANCE_LEAD','TPRM','READ'),
    ('HR_LEAD','INTAKE','READ'),('HR_LEAD','INCIDENT','READ'),
    ('IAM_LEAD','IAM','READ'),('IAM_LEAD','AUDIT_LOG','READ'),('IAM_LEAD','CYBER','READ'),
    ('MA_LEAD','RISK','READ'),('MA_LEAD','TPRM','READ'),('MA_LEAD','REPORTING','READ')
) m(role_name, module, action)
JOIN iam.roles r ON r.role_name = m.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = m.module AND p.action = m.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- ── F2-143 verification: this file's header claim, made checkable ──
-- V272 has asserted since 2026-07-06 that every hasPermission() pair is catalogued. It stopped
-- being true and nothing noticed for a month, because the catalogue was widened in the database
-- instead. These THROWs mean the claim now fails loudly rather than ageing quietly.
DECLARE @permGap INT = (
    SELECT COUNT(*) FROM (VALUES
        (N'ADMIN', N'READ'),
        (N'ADMIN', N'WRITE'),
        (N'ADMIN', N'EXPORT'),
        (N'AUTOMATION', N'READ'),
        (N'BCM', N'READ'),
        (N'BCM', N'WRITE'),
        (N'BCM', N'DELETE'),
        (N'BCM', N'EXPORT'),
        (N'BOARD', N'EXPORT'),
        (N'COMPLIANCE', N'READ'),
        (N'COMPLIANCE', N'WRITE'),
        (N'COMPLIANCE', N'EXPORT'),
        (N'CYBER', N'READ'),
        (N'CYBER', N'WRITE'),
        (N'CYBER', N'DELETE'),
        (N'CYBER', N'EXPORT'),
        (N'FRAUD', N'READ'),
        (N'FRAUD', N'WRITE'),
        (N'INTEGRATIONS', N'READ'),
        (N'INTEGRATIONS', N'WRITE'),
        (N'KB', N'WRITE'),
        (N'PRIVACY', N'READ'),
        (N'PRIVACY', N'WRITE'),
        (N'REPORTING', N'READ'),
        (N'REPORTING', N'WRITE'),
        (N'REPORTING', N'EXPORT'),
        (N'RISK', N'READ'),
        (N'RISK', N'WRITE'),
        (N'RISK', N'DELETE'),
        (N'RISK', N'EXPORT'),
        (N'SCM', N'READ'),
        (N'SCM', N'WRITE'),
        (N'SCM', N'DELETE'),
        (N'TPRM', N'APPROVE')
    ) AS req(module, action)
    WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                       WHERE p.module = req.module AND p.action = req.action));
IF @permGap > 0
    THROW 51272, 'V272/F2-143: the permission catalogue is still short of the code''s enforced set.', 1;

DECLARE @roleGap INT = (
    SELECT COUNT(*) FROM (VALUES
        (N'ORG_ADMIN'), (N'VIEWER'), (N'RISK_ANALYST'), (N'FRAUD_INVESTIGATOR')
    ) AS req(role_name)
    WHERE NOT EXISTS (SELECT 1 FROM iam.roles r
                       WHERE r.role_name = req.role_name AND r.is_deleted = 0));
IF @roleGap > 0
    THROW 51273, 'V272/F2-143: a role the migrations grant to is still absent.', 1;
GO


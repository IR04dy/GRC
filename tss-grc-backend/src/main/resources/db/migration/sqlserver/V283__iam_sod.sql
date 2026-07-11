-- V283 (RBAC Access-Governance P2 — plan §6.4): segregation-of-duties rules engine.
-- iam.sod_rules holds PAIR rules (conflicting module/action grants) and STRUCTURAL
-- rules (persona-level constraints); organisation_id NULL = the global starter pack
-- (NCA ECC 2-2-3 / SAMA CSF 3.3.5 least-privilege & SoD). iam.sod_violations is the
-- detective-scan output with a disposition workflow: OPEN -> ACCEPTED_RISK (with
-- expiry) | REMEDIATED. PLATFORM_ADMIN holders are excluded from scans (the
-- super-admin bypasses the matrix by design; governed by break-glass, P3).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'sod_rules' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.sod_rules (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT        NULL,          -- NULL = global starter pack
        rule_code        NVARCHAR(20)  NOT NULL,
        rule_type        NVARCHAR(15)  NOT NULL,      -- PAIR | STRUCTURAL
        module_a         NVARCHAR(40)  NULL,
        action_a         NVARCHAR(20)  NULL,
        module_b         NVARCHAR(40)  NULL,
        action_b         NVARCHAR(20)  NULL,
        structural_key   NVARCHAR(40)  NULL,          -- READONLY_PERSONA_WRITE | SERVICE_MULTI_MODULE
        role_pattern_csv NVARCHAR(400) NULL,          -- role names / LIKE patterns for STRUCTURAL
        severity         NVARCHAR(10)  NOT NULL DEFAULT 'HIGH',   -- CRITICAL | HIGH | MEDIUM
        rationale_en     NVARCHAR(500) NOT NULL,
        rationale_ar     NVARCHAR(500) NULL,
        is_active        BIT           NOT NULL DEFAULT 1,
        is_deleted       BIT           NOT NULL DEFAULT 0,
        created_at       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_sod_rules_org ON iam.sod_rules(organisation_id, is_active);
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'sod_violations' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.sod_violations (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT         NOT NULL,
        rule_id          BIGINT         NOT NULL,      -- soft FK iam.sod_rules
        rule_code        NVARCHAR(20)   NOT NULL,      -- snapshot
        severity         NVARCHAR(10)   NOT NULL,      -- snapshot
        user_id          BIGINT         NOT NULL,
        username         NVARCHAR(100)  NOT NULL,      -- snapshot
        detail_en        NVARCHAR(500)  NULL,          -- what conflicted
        detection_source NVARCHAR(15)   NOT NULL DEFAULT 'SCAN',   -- SCAN | OVERRIDE
        status           NVARCHAR(20)   NOT NULL DEFAULT 'OPEN',
        -- OPEN | ACCEPTED_RISK | REMEDIATED
        detected_at      DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
        disposition_by   BIGINT         NULL,
        disposition_at   DATETIME2      NULL,
        disposition_note NVARCHAR(1000) NULL,
        accepted_until   DATE           NULL,          -- mandatory for ACCEPTED_RISK
        is_deleted       BIT            NOT NULL DEFAULT 0,
        created_at       DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_sodv_org_status ON iam.sod_violations(organisation_id, status);
    CREATE INDEX ix_sodv_rule_user  ON iam.sod_violations(rule_id, user_id);
END

-- ── starter rule-pack (global, plan §6.4) — idempotent per rule_code ─────────
IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-01' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-01', 'PAIR', 'RISK', 'WRITE', 'RISK', 'APPROVE', 'HIGH',
        N'Risk proposer must not approve their own risk assessments (maker ≠ checker).',
        N'لا يجوز لمُعِدّ تقييم المخاطر اعتماد تقييماته بنفسه (مبدأ الأربع عيون).');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-02' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-02', 'PAIR', 'EVIDENCE', 'WRITE', 'EVIDENCE', 'APPROVE', 'HIGH',
        N'Evidence submitter must not approve/accept the same evidence.',
        N'لا يجوز لمُقدِّم الأدلة اعتماد الأدلة ذاتها.');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-03' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-03', 'PAIR', 'COMPLIANCE', 'WRITE', 'COMPLIANCE', 'APPROVE', 'HIGH',
        N'Policy/obligation author must not approve their own compliance artefacts.',
        N'لا يجوز لمُعِدّ السياسات أو الالتزامات اعتماد ما أعدّه بنفسه.');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-04' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-04', 'PAIR', 'CAPA', 'WRITE', 'CAPA', 'APPROVE', 'HIGH',
        N'CAPA owner must not verify/close their own corrective actions.',
        N'لا يجوز لمالك الإجراء التصحيحي التحقق من إجراءاته أو إغلاقها بنفسه.');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-05' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-05', 'PAIR', 'TPRM', 'WRITE', 'TPRM', 'APPROVE', 'HIGH',
        N'Vendor-onboarding maker must not approve the same third party.',
        N'لا يجوز لمُسجِّل المورّد اعتماد المورّد ذاته.');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-06' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, module_a, action_a, module_b, action_b, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-06', 'PAIR', 'IAM', 'WRITE', 'AUDIT', 'WRITE', 'MEDIUM',
        N'Access administrator must not also maintain the audit universe (admin ≠ auditor).',
        N'لا يجوز لمسؤول إدارة الوصول إدارة نطاق التدقيق في آنٍ واحد.');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-07' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, structural_key, role_pattern_csv, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-07', 'STRUCTURAL', 'READONLY_PERSONA_WRITE',
        N'AUDITOR,INTERNAL_AUDITOR,EXTERNAL_AUDITOR,REGULATOR_VIEWER', 'CRITICAL',
        N'Assurance/regulator personas must stay read-only outside the audit domain (independence).',
        N'يجب أن تبقى أدوار التدقيق والجهات الرقابية للاطلاع فقط خارج نطاق التدقيق (الاستقلالية).');

IF NOT EXISTS (SELECT 1 FROM iam.sod_rules WHERE rule_code = 'SOD-08' AND organisation_id IS NULL)
INSERT INTO iam.sod_rules (organisation_id, rule_code, rule_type, structural_key, role_pattern_csv, severity, rationale_en, rationale_ar)
VALUES (NULL, 'SOD-08', 'STRUCTURAL', 'SERVICE_MULTI_MODULE', N'SVC_%', 'MEDIUM',
        N'Service accounts must be single-purpose: one module scope per account.',
        N'يجب أن تكون حسابات الخدمة أحادية الغرض: نطاق وحدة واحدة لكل حساب.');

-- V185 — Continuous Control Monitoring (CCM) Engine (Sprint 11).
--
-- Regulatory driver:
--   • SAMA Cyber Security Framework section 2.6 (Continuous Monitoring) —
--     regulated entities MUST continuously monitor effectiveness of
--     critical controls and surface any deviation in near-real-time.
--   • NCA Essential Cybersecurity Controls (ECC-2) control 2-12
--     (Cybersecurity Event Logs & Monitoring Management) — every
--     control protecting a critical asset must have a documented
--     monitoring + evidence-retention scheme.
--   • SOX 404 / Saudi MoCT internal-controls programme — quarterly
--     access certification of privileged groups; CCM replaces the
--     "screenshot to Word doc" manual ritual with automated tests.
--   • PDPL Article 16 — retention windows must be enforced AND
--     evidenced; CCM tests "log retention ≥ X days" and "customer
--     data not held past contractual window" against the live store.
--
-- Two tables under a new `ccm` schema (kept separate from `compliance`
-- because the cadence + evidence model is fundamentally different —
-- compliance assesses point-in-time, CCM produces a continuous stream).
--
--   ccm.control_test_definition  ← what to test, when, how often
--   ccm.control_test_run         ← each execution + tamper-evident hash
--
-- Tamper-evidence: every run computes a SHA-256 of
-- `actual_value_json || ISO-8601(run_at)` and stores it in
-- `evidence_hash`. The auditor reproducing the hash from the JSON +
-- timestamp confirms nobody has rewritten history post-hoc.
--
-- Operator opt-in: every seed test ships with `is_enabled = 0`.
-- The runner only touches external systems for tests the operator
-- has explicitly turned on. STUB runners (no live connector yet)
-- emit deterministic PASS/FAIL from a seeded RNG so the dashboard
-- has content while the live adapters are being built.
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'ccm') IS NULL EXEC(N'CREATE SCHEMA ccm');
GO

-- ─── 1. Test definitions ───────────────────────────────────────────
IF OBJECT_ID(N'ccm.control_test_definition', N'U') IS NULL
BEGIN
    CREATE TABLE ccm.control_test_definition (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        test_code                     NVARCHAR(64)  NOT NULL,
        title_en                      NVARCHAR(255) NOT NULL,
        title_ar                      NVARCHAR(255) NULL,
        description_en                NVARCHAR(MAX) NULL,
        description_ar                NVARCHAR(MAX) NULL,

        -- Soft FK to platform.controls (id of the unified control this
        -- test attests). NULL is allowed for tests not yet mapped.
        control_id                    BIGINT        NULL,

        test_type                     NVARCHAR(40)  NOT NULL,
        target_system_type            NVARCHAR(40)  NOT NULL,

        -- Connection config — URL, bind DN, OAuth client. Secrets are
        -- referenced as ${ENV_VAR} placeholders and resolved at run-time,
        -- never embedded.
        target_config_json            NVARCHAR(MAX) NULL,

        -- The check itself — e.g. {"groupDn": "...", "maxMembers": 5}.
        expected_condition_json       NVARCHAR(MAX) NULL,

        severity                      NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',
        frequency_cron                NVARCHAR(40)  NOT NULL,
        timeout_seconds               INT           NOT NULL DEFAULT 60,

        is_enabled                    BIT           NOT NULL DEFAULT 0,

        last_run_at                   DATETIME2(3)  NULL,
        last_run_status               NVARCHAR(20)  NULL,
        consecutive_failures          INT           NOT NULL DEFAULT 0,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_ccm_def_test_type CHECK (test_type IN
            (N'LDAP_GROUP_MEMBERSHIP', N'AD_PRIVILEGED_USERS',
             N'MFA_ENROLLMENT_PERCENTAGE', N'LOG_RETENTION_DAYS',
             N'NETWORK_ACL_RULE', N'SSL_CERT_EXPIRY',
             N'PASSWORD_POLICY', N'INACTIVE_ACCOUNTS',
             N'SHARED_ACCOUNT_USAGE', N'FILE_INTEGRITY',
             N'BACKUP_RECENCY', N'PATCH_LEVEL',
             N'CUSTOM_SQL', N'CUSTOM_HTTP')),
        CONSTRAINT CK_ccm_def_target_sys CHECK (target_system_type IN
            (N'LDAP_AD', N'AZURE_AD', N'SPLUNK', N'ELK',
             N'CROWDSTRIKE', N'TENABLE', N'SERVICENOW',
             N'INTERNAL_DB', N'FILE_SYSTEM', N'CUSTOM')),
        CONSTRAINT CK_ccm_def_severity CHECK (severity IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_ccm_def_last_status CHECK (last_run_status IS NULL OR last_run_status IN
            (N'PASS', N'FAIL', N'ERROR', N'SKIPPED', N'TIMEOUT'))
    );
END
GO

-- Filtered unique index — test_code is unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_ccm_def_test_code' AND object_id = OBJECT_ID(N'ccm.control_test_definition'))
    CREATE UNIQUE INDEX UQ_ccm_def_test_code ON ccm.control_test_definition (organisation_id, test_code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_def_enabled_next' AND object_id = OBJECT_ID(N'ccm.control_test_definition'))
    CREATE INDEX IX_ccm_def_enabled_next ON ccm.control_test_definition (organisation_id, is_enabled, last_run_at) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_def_type_target' AND object_id = OBJECT_ID(N'ccm.control_test_definition'))
    CREATE INDEX IX_ccm_def_type_target ON ccm.control_test_definition (organisation_id, test_type, target_system_type) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_def_control_id' AND object_id = OBJECT_ID(N'ccm.control_test_definition'))
    CREATE INDEX IX_ccm_def_control_id ON ccm.control_test_definition (organisation_id, control_id) WHERE is_deleted = 0 AND control_id IS NOT NULL;
GO

-- ─── 2. Run history ────────────────────────────────────────────────
IF OBJECT_ID(N'ccm.control_test_run', N'U') IS NULL
BEGIN
    CREATE TABLE ccm.control_test_run (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        test_definition_id            BIGINT NOT NULL,

        run_at                        DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        duration_ms                   INT           NULL,
        status                        NVARCHAR(20)  NOT NULL,

        actual_value_json             NVARCHAR(MAX) NULL,
        failure_reason                NVARCHAR(1000) NULL,

        -- SHA-256(actual_value_json || ISO-8601(run_at)) — tamper-evident.
        evidence_hash                 NVARCHAR(64)  NULL,

        -- Soft FK to capa.capa_tasks — set when consecutive CRITICAL
        -- failures trigger an auto-opened CAPA / Issue.
        capa_task_id                  BIGINT        NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_ccm_run_def FOREIGN KEY (test_definition_id)
            REFERENCES ccm.control_test_definition(id),
        CONSTRAINT CK_ccm_run_status CHECK (status IN
            (N'PASS', N'FAIL', N'ERROR', N'SKIPPED', N'TIMEOUT'))
    );
END
GO

-- Filtered indexes — the dashboard queries are (org, def, run_at DESC),
-- (org, status, run_at DESC), (org, def, status).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_run_org_def_run' AND object_id = OBJECT_ID(N'ccm.control_test_run'))
    CREATE INDEX IX_ccm_run_org_def_run ON ccm.control_test_run (organisation_id, test_definition_id, run_at DESC) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_run_org_status_run' AND object_id = OBJECT_ID(N'ccm.control_test_run'))
    CREATE INDEX IX_ccm_run_org_status_run ON ccm.control_test_run (organisation_id, status, run_at DESC) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ccm_run_org_def_status' AND object_id = OBJECT_ID(N'ccm.control_test_run'))
    CREATE INDEX IX_ccm_run_org_def_status ON ccm.control_test_run (organisation_id, test_definition_id, status) WHERE is_deleted = 0;
GO

-- ─── 3. Seed 10 test definitions for org=1 (TSS Bank) ──────────────
-- Saudi-native bilingual EN/AR. Every test ships with is_enabled = 0 so
-- the operator must opt in per-test before any external connection
-- attempt.

-- CCM-AD-PRIVGROUP-001 — AD Domain Admins ≤ 5 members
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-AD-PRIVGROUP-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-AD-PRIVGROUP-001',
     N'Domain Admins membership ≤ 5',
     N'عدد أعضاء مجموعة "مسؤولي النطاق" لا يتجاوز 5',
     N'Continuously verify the AD Domain Admins group has no more than five members. SOX-style quarterly privileged-access certification is the manual fallback; this test catches drift between certifications.',
     N'التحقّق المستمر من أن مجموعة "مسؤولي النطاق" في الـ Active Directory لا تضم أكثر من خمسة أعضاء. التصديق الربعي للوصول المتميّز على نمط ساوكس هو الحل اليدوي البديل؛ هذا الاختبار يلتقط الانحراف بين دورات التصديق.',
     N'LDAP_GROUP_MEMBERSHIP', N'LDAP_AD',
     N'{"url": "${LDAP_URL}", "bindDn": "${LDAP_BIND_DN}", "searchBase": "OU=Groups,DC=corp,DC=tss-bank,DC=sa"}',
     N'{"groupDn": "CN=Domain Admins,OU=Groups,DC=corp,DC=tss-bank,DC=sa", "maxMembers": 5}',
     N'CRITICAL', N'0 0 6 * * *', 60, 0);
GO

-- CCM-MFA-ENROLLMENT-001 — ≥ 95% admins MFA enrolled
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-MFA-ENROLLMENT-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-MFA-ENROLLMENT-001',
     N'Admin users MFA enrolment ≥ 95%',
     N'نسبة تفعيل المصادقة متعدّدة العوامل للمستخدمين الإداريّين لا تقل عن 95%',
     N'Azure AD: at least 95% of users in the privileged-admin scope have MFA enabled. NCA ECC-2 control 2-2 (Identity & Access Management) requires strong authentication for privileged roles.',
     N'في Azure AD: 95% على الأقل من المستخدمين الإداريّين لديهم المصادقة متعدّدة العوامل مفعّلة. الضابط 2-2 من إطار الضوابط الأساسية للأمن السيبراني (ECC-2) يستلزم مصادقة قوية للأدوار المتميّزة.',
     N'MFA_ENROLLMENT_PERCENTAGE', N'AZURE_AD',
     N'{"tenantId": "${AZURE_TENANT_ID}", "clientId": "${AZURE_CLIENT_ID}", "groupId": "Admins"}',
     N'{"minPercent": 95}',
     N'CRITICAL', N'0 0 6 * * *', 120, 0);
GO

-- CCM-LOG-RETENTION-001 — Splunk audit-log retention ≥ 365 days
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-LOG-RETENTION-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-LOG-RETENTION-001',
     N'Splunk audit-log retention ≥ 365 days',
     N'احتفاظ سجلّات التدقيق في سبلانك لا يقل عن 365 يوماً',
     N'Splunk index `audit` retains at least 365 days of events. SAMA CSF + NCA ECC require one full year of audit-trail retention for banking workloads.',
     N'فهرس "audit" في سبلانك يحتفظ بسجلات لا تقل مدّتها عن 365 يوماً. كل من إطار SAMA للأمن السيبراني والضوابط الأساسية NCA-ECC تستلزم الاحتفاظ بسنة كاملة من سجلّات التدقيق للأعمال المصرفية.',
     N'LOG_RETENTION_DAYS', N'SPLUNK',
     N'{"hecUrl": "${SPLUNK_HEC_URL}", "index": "audit"}',
     N'{"minRetentionDays": 365}',
     N'HIGH', N'0 0 7 * * MON', 90, 0);
GO

-- CCM-SSL-CERT-001 — Public-facing cert expires > 30 days out
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-SSL-CERT-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-SSL-CERT-001',
     N'Public TLS certificate expires in > 30 days',
     N'انتهاء شهادة TLS العامّة بعد أكثر من 30 يوماً',
     N'Public-facing TLS certificate on banking.tss-bank.sa must have at least 30 days until expiry. NCA ECC-2 control 2-9 (Cryptography) covers certificate lifecycle hygiene.',
     N'شهادة TLS العامّة على banking.tss-bank.sa يجب أن يتبقّى على انتهائها أكثر من 30 يوماً على الأقل. الضابط 2-9 من ECC-2 (التشفير) يغطي نظافة دورة حياة الشهادات.',
     N'SSL_CERT_EXPIRY', N'CUSTOM',
     N'{"host": "banking.tss-bank.sa", "port": 443}',
     N'{"minDaysToExpiry": 30}',
     N'HIGH', N'0 0 6 * * *', 30, 0);
GO

-- CCM-PASSWORD-POLICY-001 — AD password policy ≥ 14 chars + complexity
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-PASSWORD-POLICY-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-PASSWORD-POLICY-001',
     N'AD password policy ≥ 14 chars + complexity',
     N'سياسة كلمات المرور في AD: 14 خانة على الأقل مع التعقيد',
     N'AD default-domain-policy enforces password length ≥ 14 and complexity enabled. SAMA CSF section 3.3.4 (Password Management) is explicit on these floors for privileged systems.',
     N'سياسة النطاق الافتراضية في AD تفرض طول كلمة مرور لا يقل عن 14 خانة مع تفعيل التعقيد. القسم 3.3.4 من إطار SAMA للأمن السيبراني (إدارة كلمات المرور) صريح في هذه الحدود الدنيا للأنظمة المتميّزة.',
     N'PASSWORD_POLICY', N'LDAP_AD',
     N'{"url": "${LDAP_URL}", "bindDn": "${LDAP_BIND_DN}"}',
     N'{"minLength": 14, "complexityRequired": true}',
     N'HIGH', N'0 0 7 * * *', 60, 0);
GO

-- CCM-INACTIVE-ACCOUNTS-001 — Inactive > 90 days are disabled
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-INACTIVE-ACCOUNTS-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-INACTIVE-ACCOUNTS-001',
     N'AD accounts inactive > 90 days are disabled',
     N'حسابات AD غير النشطة لأكثر من 90 يوماً يجب أن تكون معطّلة',
     N'No enabled AD account has lastLogonTimestamp older than 90 days. SAMA CSF section 3.3.2 (User Access Management) — JML "Mover/Leaver" — requires automatic deactivation of stale accounts.',
     N'لا يوجد حساب AD مفعّل بقيمة lastLogonTimestamp أقدم من 90 يوماً. القسم 3.3.2 من إطار SAMA (إدارة وصول المستخدمين) ضمن منظومة JML للملتحقين والمنتقلين والمغادرين يستلزم إيقاف الحسابات الراكدة تلقائياً.',
     N'INACTIVE_ACCOUNTS', N'LDAP_AD',
     N'{"url": "${LDAP_URL}", "bindDn": "${LDAP_BIND_DN}", "searchBase": "OU=Users,DC=corp,DC=tss-bank,DC=sa"}',
     N'{"inactiveDays": 90, "maxOffenders": 0}',
     N'HIGH', N'0 0 6 * * *', 120, 0);
GO

-- CCM-BACKUP-RECENCY-001 — Last successful backup < 24h ago
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-BACKUP-RECENCY-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-BACKUP-RECENCY-001',
     N'Last successful backup within 24 hours',
     N'آخر عملية نسخ احتياطي ناجحة خلال آخر 24 ساعة',
     N'Core banking T24 backup log shows a SUCCESS event within the last 24 hours. SAMA CSF 3.3.13 (Backup & Recovery) — daily backup is the floor for Tier-1 systems.',
     N'سجل النسخ الاحتياطي للنظام البنكي الأساسي T24 يُظهر عملية ناجحة خلال آخر 24 ساعة. القسم 3.3.13 من إطار SAMA (النسخ الاحتياطي والاستعادة) - النسخ اليومي هو الحد الأدنى لأنظمة الفئة الأولى.',
     N'BACKUP_RECENCY', N'INTERNAL_DB',
     N'{"backupLogTable": "ops.backup_log", "systemTag": "T24_CORE"}',
     N'{"maxHoursSinceLastSuccess": 24}',
     N'CRITICAL', N'0 0 8 * * *', 30, 0);
GO

-- CCM-PATCH-LEVEL-001 — ≥ 95% endpoints at current patch level
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-PATCH-LEVEL-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-PATCH-LEVEL-001',
     N'Endpoints at current patch level ≥ 95%',
     N'الأجهزة الطرفية المُحدَّثة بأحدث التصحيحات بنسبة 95% أو أكثر',
     N'CrowdStrike Falcon inventory shows at least 95% of endpoints at the latest patch baseline. NCA ECC-2 control 2-10 (Patch Management) — quarterly review is mandatory; this test catches drift.',
     N'مخزون CrowdStrike Falcon يُظهر أن 95% من الأجهزة الطرفية على الأقل تعمل بأحدث خط أساس للتصحيحات. الضابط 2-10 من ECC-2 (إدارة التصحيحات) يستلزم مراجعة ربعية؛ هذا الاختبار يلتقط الانحراف.',
     N'PATCH_LEVEL', N'CROWDSTRIKE',
     N'{"baseUrl": "${CROWDSTRIKE_BASE_URL}", "clientId": "${CROWDSTRIKE_CLIENT_ID}"}',
     N'{"minPercentCurrent": 95}',
     N'HIGH', N'0 0 9 * * MON', 180, 0);
GO

-- CCM-NETWORK-ACL-001 — No SSH from internet on production segment
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-NETWORK-ACL-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-NETWORK-ACL-001',
     N'No SSH allowed from internet on production segment',
     N'عدم السماح بـ SSH من الإنترنت على شبكة الإنتاج',
     N'Production VLAN ACL has zero rules that permit tcp/22 from 0.0.0.0/0. NCA ECC-2 control 2-7 (Network Security) — SSH from public internet to production is an automatic finding.',
     N'قوائم التحكم بالوصول لشبكة الإنتاج لا تحتوي على أي قاعدة تسمح بمنفذ tcp/22 من العنوان 0.0.0.0/0. الضابط 2-7 من ECC-2 (أمن الشبكات) - الوصول إلى الإنتاج عبر SSH من الإنترنت العام مخالفة تلقائية.',
     N'NETWORK_ACL_RULE', N'CUSTOM',
     N'{"firewallSystem": "Palo Alto Panorama", "segment": "prod-vlan-100"}',
     N'{"forbiddenRules": [{"protocol": "tcp", "port": 22, "source": "0.0.0.0/0"}]}',
     N'CRITICAL', N'0 0 6 * * *', 60, 0);
GO

-- CCM-PDPL-RETENTION-001 — Customer data not held past contractual window
IF NOT EXISTS (SELECT 1 FROM ccm.control_test_definition WHERE organisation_id = 1 AND test_code = N'CCM-PDPL-RETENTION-001' AND is_deleted = 0)
INSERT INTO ccm.control_test_definition
    (organisation_id, test_code, title_en, title_ar, description_en, description_ar,
     test_type, target_system_type, target_config_json, expected_condition_json,
     severity, frequency_cron, timeout_seconds, is_enabled)
VALUES
    (1, N'CCM-PDPL-RETENTION-001',
     N'No customer data older than contractual retention window',
     N'لا توجد بيانات عملاء أقدم من نافذة الاحتفاظ التعاقدية',
     N'PDPL Article 16: customer records older than the 7-year retention window must be either anonymised or hard-deleted. Test counts rows where created_at < DATEADD(year, -7, GETDATE()).',
     N'المادة 16 من نظام حماية البيانات الشخصية: سجلات العملاء الأقدم من نافذة الاحتفاظ البالغة 7 سنوات يجب إمّا إخفاء هوية أصحابها أو حذفها نهائياً. يحسب الاختبار عدد السجلات التي يقل تاريخ إنشائها عن المدة المحددة.',
     N'CUSTOM_SQL', N'INTERNAL_DB',
     N'{"datasource": "default"}',
     N'{"sql": "SELECT COUNT(*) AS offenders FROM customer.customer WHERE created_at < DATEADD(year, -7, SYSUTCDATETIME()) AND anonymised_at IS NULL", "maxRowCount": 0}',
     N'HIGH', N'0 0 7 * * MON', 120, 0);
GO

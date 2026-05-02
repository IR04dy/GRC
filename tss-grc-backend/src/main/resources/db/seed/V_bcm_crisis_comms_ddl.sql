-- ============================================================================
-- TSS GRC — BCM Crisis Communication module (assessment §5.5 #5)
-- ============================================================================
-- Pre-approved templates + recipient audiences + distribution audit trail for
-- crisis-time communications. Saudi-flavoured: SMS via STC/Mobily-friendly
-- channel codes, Tawakkalna Business push channel reserved for pandemic-class
-- events, multi-channel send with bilingual EN+AR templates.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── Tables ────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='bcm' AND t.name='crisis_comms_templates')
BEGIN
    CREATE TABLE bcm.crisis_comms_templates (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_cct_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        code               NVARCHAR(50) NOT NULL,
        name_en            NVARCHAR(255) NOT NULL,
        name_ar            NVARCHAR(255) NULL,
        audience_role      NVARCHAR(100) NOT NULL,    -- EXECUTIVE, EMPLOYEES, CUSTOMERS, REGULATORS, PUBLIC, ALL
        channel            NVARCHAR(50) NOT NULL,     -- EMAIL, SMS, PUSH, TAWAKKALNA, IN_APP, MULTI
        subject_en         NVARCHAR(500) NULL,
        subject_ar         NVARCHAR(500) NULL,
        body_en            NVARCHAR(MAX) NOT NULL,
        body_ar            NVARCHAR(MAX) NULL,
        severity_band      NVARCHAR(20) NULL,         -- CRITICAL, HIGH, MEDIUM, LOW, ANY
        version            NVARCHAR(20) NOT NULL CONSTRAINT DF_cct_ver DEFAULT N'1.0',
        is_active          BIT NOT NULL CONSTRAINT DF_cct_active DEFAULT 1,
        is_deleted         BIT NOT NULL CONSTRAINT DF_cct_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_cct_created DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2 NULL,
        CONSTRAINT UQ_cct UNIQUE (organisation_id, code, version)
    );
    PRINT N'Created bcm.crisis_comms_templates';
END
ELSE PRINT N'bcm.crisis_comms_templates already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='bcm' AND t.name='crisis_comms_audiences')
BEGIN
    CREATE TABLE bcm.crisis_comms_audiences (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id    BIGINT NOT NULL,
        name               NVARCHAR(255) NOT NULL,
        name_ar            NVARCHAR(255) NULL,
        audience_role      NVARCHAR(100) NOT NULL,
        contacts_csv       NVARCHAR(MAX) NULL,        -- CSV of emails / phones / Tawakkalna IDs
        recipient_count    INT NOT NULL CONSTRAINT DF_cca_count DEFAULT 0,
        is_active          BIT NOT NULL CONSTRAINT DF_cca_active DEFAULT 1,
        is_deleted         BIT NOT NULL CONSTRAINT DF_cca_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_cca_created DEFAULT SYSUTCDATETIME()
    );
    PRINT N'Created bcm.crisis_comms_audiences';
END
ELSE PRINT N'bcm.crisis_comms_audiences already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='bcm' AND t.name='crisis_comms_distributions')
BEGIN
    CREATE TABLE bcm.crisis_comms_distributions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_ccd_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        plan_id            BIGINT NULL,                -- optional FK to bcm.bcm_plans
        template_id        BIGINT NOT NULL,
        audience_id        BIGINT NOT NULL,
        channel            NVARCHAR(50) NOT NULL,
        subject_snapshot   NVARCHAR(500) NULL,
        body_snapshot      NVARCHAR(MAX) NULL,
        recipient_count    INT NOT NULL CONSTRAINT DF_ccd_count DEFAULT 0,
        sent_at            DATETIME2 NOT NULL CONSTRAINT DF_ccd_sent DEFAULT SYSUTCDATETIME(),
        sent_by_id         BIGINT NULL,
        delivery_status    NVARCHAR(30) NOT NULL CONSTRAINT DF_ccd_status DEFAULT 'SIMULATED',  -- SIMULATED, SENT, DELIVERED, FAILED
        delivery_summary   NVARCHAR(MAX) NULL,
        CONSTRAINT FK_ccd_template FOREIGN KEY (template_id) REFERENCES bcm.crisis_comms_templates(id),
        CONSTRAINT FK_ccd_audience FOREIGN KEY (audience_id) REFERENCES bcm.crisis_comms_audiences(id)
    );
    CREATE INDEX IX_ccd_org_sent ON bcm.crisis_comms_distributions(organisation_id, sent_at DESC);
    PRINT N'Created bcm.crisis_comms_distributions';
END
ELSE PRINT N'bcm.crisis_comms_distributions already exists';

-- ── Seed: 5 representative bilingual templates ─────────────────────────────
INSERT INTO bcm.crisis_comms_templates (organisation_id, code, name_en, name_ar, audience_role, channel, subject_en, subject_ar, body_en, body_ar, severity_band)
SELECT v.org, v.code, v.en, v.ar, v.audience, v.channel, v.subj_en, v.subj_ar, v.body_en, v.body_ar, v.sev
FROM (VALUES
  (1, N'EXEC_INVOCATION',
   N'Executive — Plan Invocation Notice',
   N'إخطار التنفيذيين — تفعيل خطة الاستمرارية',
   N'EXECUTIVE', N'EMAIL',
   N'BCP Invocation: {plan_name}',
   N'تفعيل خطة استمرارية الأعمال: {plan_name}',
   N'The Business Continuity Plan {plan_name} has been formally invoked at {invocation_time} due to {trigger_reason}. The CRO and BCM team are coordinating response. Please join the bridge: {comms_bridge}.',
   N'تم تفعيل خطة استمرارية الأعمال {plan_name} رسمياً في {invocation_time} بسبب {trigger_reason}. يقوم مدير المخاطر وفريق استمرارية الأعمال بتنسيق الاستجابة. يرجى الانضمام إلى جسر الاتصال: {comms_bridge}.',
   N'CRITICAL'),
  (1, N'EMP_GENERAL',
   N'Employees — General Disruption Notice',
   N'إخطار الموظفين — تعطل عام',
   N'EMPLOYEES', N'MULTI',
   N'Service Disruption — {disruption_type}',
   N'تعطل في الخدمة — {disruption_type}',
   N'We are experiencing a {disruption_type} affecting {affected_services}. Please follow the work-from-home protocol if applicable. Updates will follow on this channel and via Tawakkalna Business. Thank you for your patience.',
   N'نواجه حالياً {disruption_type} تؤثر على {affected_services}. يُرجى اتباع بروتوكول العمل من المنزل إذا لزم الأمر. سيتم نشر التحديثات عبر هذه القناة وعبر تطبيق توكلنا للأعمال. شكراً لتعاونكم.',
   N'HIGH'),
  (1, N'CUST_BANKING',
   N'Customers — Banking Service Disruption',
   N'العملاء — تعطل الخدمة المصرفية',
   N'CUSTOMERS', N'SMS',
   N'Service notice',
   N'إشعار الخدمة',
   N'Dear customer, we are temporarily unable to process {service_type}. Estimated restoration: {eta}. We apologise for the inconvenience.',
   N'عزيزي العميل، نتعذّر مؤقتاً عن إتمام {service_type}. المدة المتوقعة للاستعادة: {eta}. نعتذر عن أي إزعاج.',
   N'HIGH'),
  (1, N'REG_NCA_24H',
   N'Regulator — NCA HEY 24-hour notice',
   N'الجهة التنظيمية — إخطار هيئة الأمن السيبراني خلال 24 ساعة',
   N'REGULATORS', N'EMAIL',
   N'Cybersecurity Incident Notification — {incident_code}',
   N'إخطار حادثة سيبرانية — {incident_code}',
   N'Per NCA Cybersecurity Incident Reporting requirements, we are notifying NCA HEY of incident {incident_code} discovered at {discovery_time}. Severity: {severity}. Initial impact summary: {impact_summary}. Containment status: {nist_phase}. CISO contact: {ciso_contact}.',
   N'طبقاً لمتطلبات الإبلاغ عن الحوادث السيبرانية لهيئة الأمن السيبراني، نُبلغ هيئة هيي عن الحادثة {incident_code} المكتشفة في {discovery_time}. الخطورة: {severity}. ملخص الأثر الأولي: {impact_summary}. حالة الاحتواء: {nist_phase}. تواصل مدير أمن المعلومات: {ciso_contact}.',
   N'CRITICAL'),
  (1, N'PUB_PRESS',
   N'Public — Press Statement',
   N'العامة — بيان صحفي',
   N'PUBLIC', N'EMAIL',
   N'Statement regarding {disruption_type}',
   N'بيان بخصوص {disruption_type}',
   N'On {discovery_date}, we identified a {disruption_type} affecting {affected_services}. Our team is engaged with all relevant stakeholders. We are committed to transparency and will publish updates as the situation develops. We thank our customers for their patience.',
   N'في {discovery_date}، حددنا {disruption_type} يؤثر على {affected_services}. فريقنا يعمل بالتنسيق مع كافة الجهات ذات العلاقة. نحن ملتزمون بالشفافية وسننشر التحديثات مع تطور الموقف. نشكر عملاءنا على تفهمهم.',
   N'CRITICAL')
) AS v(org, code, en, ar, audience, channel, subj_en, subj_ar, body_en, body_ar, sev)
WHERE NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_templates t WHERE t.organisation_id=v.org AND t.code=v.code AND t.is_deleted=0);

-- ── Seed: 4 audience groups ────────────────────────────────────────────────
INSERT INTO bcm.crisis_comms_audiences (organisation_id, name, name_ar, audience_role, contacts_csv, recipient_count)
SELECT v.org, v.name_en, v.name_ar, v.role, v.contacts, v.cnt
FROM (VALUES
  (1, N'Executive Committee',     N'اللجنة التنفيذية',         N'EXECUTIVE', N'ceo@example.sa, cfo@example.sa, cro@example.sa, ciso@example.sa, coo@example.sa', 5),
  (1, N'All Employees',            N'جميع الموظفين',            N'EMPLOYEES', N'{employee-list-managed-by-HR}', 0),
  (1, N'Saudi Data Office Liaison', N'مسؤول الاتصال بمكتب البيانات', N'REGULATORS', N'liaison@example.sa', 1),
  (1, N'Press & Media Relations',  N'العلاقات الإعلامية',       N'PUBLIC',    N'press@example.sa', 1)
) AS v(org, name_en, name_ar, role, contacts, cnt)
WHERE NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_audiences a WHERE a.organisation_id=v.org AND a.name=v.name_en AND a.is_deleted=0);

PRINT N'Seeded crisis-comms templates and audience groups.';

-- ── Result summary ────────────────────────────────────────────────────────
SELECT
    N'BCM Crisis Comms DDL complete' AS status,
    (SELECT COUNT(*) FROM bcm.crisis_comms_templates  WHERE is_deleted=0) AS templates,
    (SELECT COUNT(*) FROM bcm.crisis_comms_audiences  WHERE is_deleted=0) AS audiences,
    (SELECT COUNT(*) FROM bcm.crisis_comms_distributions) AS distributions;

GO

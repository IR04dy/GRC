-- V52 — Processor due-diligence assessments (gap-analysis item: PDPL Art. 28).
--
-- A formal, scored due-diligence assessment of a processor against the PDPL
-- Art. 28 control set. Distinct from the generic VendorAssessment because
-- it carries the specific PDPL processor checklist: instructions in writing,
-- confidentiality, security measures (Art. 27), sub-processor authorisation,
-- breach notification, assistance with DSR, deletion / return at end of
-- engagement, and audit-rights.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'processor_assessments' AND schema_id = SCHEMA_ID('privacy'))
BEGIN
    CREATE TABLE privacy.processor_assessments (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT        NOT NULL,
        external_id                 NVARCHAR(64)  NULL,
        assessment_code             NVARCHAR(40)  NULL,

        processor_vendor_id         BIGINT        NULL,
        processor_name              NVARCHAR(255) NOT NULL,
        processor_country           NVARCHAR(3)   NULL,
        processing_scope_en         NVARCHAR(MAX) NOT NULL,
        processing_scope_ar         NVARCHAR(MAX) NULL,

        -- ONBOARDING | ANNUAL | RENEWAL | AD_HOC | INCIDENT_TRIGGERED
        assessment_type             NVARCHAR(30)  NOT NULL,

        -- The eight PDPL Art. 28 mandatory controls — each scored
        --   PASS | PASS_WITH_CONDITIONS | FAIL | NOT_APPLICABLE
        c1_written_instructions     NVARCHAR(25)  NULL,
        c2_confidentiality          NVARCHAR(25)  NULL,
        c3_security_measures        NVARCHAR(25)  NULL,
        c4_sub_processor_auth       NVARCHAR(25)  NULL,
        c5_breach_notification      NVARCHAR(25)  NULL,
        c6_dsr_assistance           NVARCHAR(25)  NULL,
        c7_deletion_or_return       NVARCHAR(25)  NULL,
        c8_audit_rights             NVARCHAR(25)  NULL,

        evidence_refs               NVARCHAR(MAX) NULL,
        findings_en                 NVARCHAR(MAX) NULL,
        findings_ar                 NVARCHAR(MAX) NULL,
        recommendations_en          NVARCHAR(MAX) NULL,
        recommendations_ar          NVARCHAR(MAX) NULL,

        overall_outcome             NVARCHAR(25)  NULL,
        risk_score                  DECIMAL(4,1)  NULL,
        next_assessment_due         DATE          NULL,

        assessed_at                 DATETIME2     NULL,
        assessed_by                 BIGINT        NULL,
        assessor_name               NVARCHAR(255) NULL,
        approved_at                 DATETIME2     NULL,
        approver_name               NVARCHAR(255) NULL,

        -- IN_PROGRESS | COMPLETED | APPROVED | REJECTED
        status                      NVARCHAR(20)  NOT NULL DEFAULT N'IN_PROGRESS',

        is_deleted                  SMALLINT      NOT NULL DEFAULT 0,
        is_active                   SMALLINT      NOT NULL DEFAULT 1,
        deleted_at                  DATETIME2     NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by                  BIGINT        NULL,
        row_version                 BIGINT        NOT NULL DEFAULT 0
    );

    CREATE INDEX IX_pa_org_status  ON privacy.processor_assessments (organisation_id, status, is_deleted);
    CREATE INDEX IX_pa_vendor      ON privacy.processor_assessments (organisation_id, processor_vendor_id);
    CREATE INDEX IX_pa_next_due    ON privacy.processor_assessments (organisation_id, next_assessment_due);
END
GO

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy.processor_assessments WHERE organisation_id = @org)
BEGIN
    INSERT INTO privacy.processor_assessments
      (organisation_id, assessment_code, processor_name, processor_country,
       processing_scope_en, processing_scope_ar, assessment_type,
       c1_written_instructions, c2_confidentiality, c3_security_measures,
       c4_sub_processor_auth, c5_breach_notification, c6_dsr_assistance,
       c7_deletion_or_return, c8_audit_rights,
       evidence_refs, findings_en, recommendations_en,
       overall_outcome, risk_score, next_assessment_due,
       assessed_at, assessor_name, approved_at, approver_name, status)
    VALUES
      (@org, N'PROC-2026-001', N'AWS Inc. (us-east-1 region)', N'USA',
       N'Cloud hosting of customer-facing services + analytics pipelines processing CONTACT, BEHAVIORAL and DEMOGRAPHIC data.',
       N'استضافة سحابية للخدمات الموجّهة للعملاء وأنابيب التحليل التي تعالج بيانات الاتصال والسلوك والديموغرافيا.',
       N'ANNUAL',
       N'PASS', N'PASS', N'PASS', N'PASS_WITH_CONDITIONS', N'PASS', N'PASS', N'PASS', N'PASS_WITH_CONDITIONS',
       N'AWS DPA v2024-09 · SOC2 Type II · ISO 27001 cert · sub-processor list',
       N'AWS controls remain strong. Sub-processor authorisation is "general" rather than "specific" — they notify us of changes but consent is implicit, which the PDPL DPO flagged as a residual gap.',
       N'Negotiate a contractual addendum requiring 60-day prior notice + explicit opt-out for sub-processor changes affecting Saudi data sets.',
       N'PASS_WITH_CONDITIONS', 2.5, '2027-04-15',
       '2026-04-15T10:00:00', N'DPO — Sara Al-Otaibi', '2026-04-22T13:30:00', N'CISO', N'APPROVED'),

      (@org, N'PROC-2026-002', N'Workday Ltd', N'IRL',
       N'HCM SaaS hosting EMPLOYEE_PII, SALARY, BANK_ACCOUNT, HEALTH (sick-leave) for all KSA employees.',
       N'منصة الموارد البشرية كخدمة تحتضن بيانات الموظفين بما في ذلك الراتب والصحة.',
       N'ANNUAL',
       N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS',
       N'Workday DPA 2024-11 · ISO 27001 · ISO 27018 · CSA STAR L2 · annual pentest summary',
       N'All eight Art. 28 controls satisfied. Workday provides documented DSR assistance through their support portal with a 5-business-day SLA.',
       N'Continue annual cadence. No findings.',
       N'PASS', 1.5, '2027-03-30',
       '2026-03-30T11:00:00', N'DPO', '2026-04-05T15:00:00', N'CHRO', N'APPROVED'),

      (@org, N'PROC-2026-003', N'TCS Saudi Arabia (Payroll BPO)', N'SAU',
       N'Outsourced payroll operations processing EMPLOYEE_PII + SALARY for ~1,200 KSA staff. Sub-processed to TCS India delivery centres.',
       N'عمليات الرواتب الخارجية لـ 1200 موظف، تتم معالجتها أيضاً في مراكز التسليم في الهند.',
       N'AD_HOC',
       N'PASS', N'PASS', N'PASS_WITH_CONDITIONS', N'FAIL', N'PASS', N'PASS_WITH_CONDITIONS', N'PASS', N'PASS_WITH_CONDITIONS',
       N'TCS MSA 2024-Q3 · ISO 27001 · annual processor audit · India DC walk-through report',
       N'Sub-processor authorisation: TCS India delivery centres process Saudi PII without explicit per-engagement authorisation — the master agreement assumes blanket consent. DSR assistance is reliable but routes through TCS Global Service Desk and has a 7-business-day SLA, which would risk PDPL 30-day deadline for complex requests.',
       N'1. Re-paper the sub-processor authorisation to be specific. 2. Implement direct ticket queue with TCS Saudi for DSR work (target 3-day SLA).',
       N'PASS_WITH_CONDITIONS', 3.5, '2026-09-01',
       '2026-03-12T09:30:00', N'DPO + CISO', '2026-03-18T14:00:00', N'COO', N'APPROVED'),

      (@org, N'PROC-2026-004', N'OpenAI LLC (Azure tenant)', N'USA',
       N'LLM API used by compliance-copilot for free-text reasoning over regulatory questions. Inputs may contain CONTACT or FREE_TEXT subject to consent.',
       N'واجهة برمجية للذكاء التوليدي تستخدمها أداة المساعد للامتثال على نصوص قد تحتوي بيانات اتصال أو نصوص حرّة.',
       N'INCIDENT_TRIGGERED',
       N'PASS_WITH_CONDITIONS', N'PASS', N'PASS', N'FAIL', N'FAIL', N'FAIL', N'PASS_WITH_CONDITIONS', N'FAIL',
       N'OpenAI ToS · OpenAI Enterprise DPA · Azure sub-processor note',
       N'Four Art. 28 controls fail: sub-processor authorisation, breach notification, DSR assistance and audit rights are not properly contracted. OpenAI sub-processes to Microsoft US without per-engagement consent; no defined breach notification timeline; cannot guarantee subject access response for free-text prompts; no third-party audit rights.',
       N'Switch to EU-region OpenAI endpoint + Enterprise plan covering all four gaps, OR replace OpenAI with Azure OpenAI Service under our existing Microsoft DPA.',
       N'FAIL', 8.5, '2026-06-01',
       '2026-04-19T16:00:00', N'DPO + CISO', NULL, NULL, N'REJECTED'),

      (@org, N'PROC-2026-005', N'Stripe Payments Europe Ltd', N'NLD',
       N'Payment processing for the consumer mobile app. Processes FINANCIAL, IDENTITY, DEVICE data.',
       N'معالجة المدفوعات لتطبيق الجوال الاستهلاكي.',
       N'ONBOARDING',
       N'PASS', N'PASS', N'PASS', N'PASS_WITH_CONDITIONS', N'PASS', N'PASS', N'PASS', N'PASS',
       N'Stripe DPA 2025-12 · PCI DSS Level 1 · ISO 27001 · annual SOC2',
       N'Strong controls overall. Sub-processor list includes 12 named entities; we have right of objection but they default to deemed consent after 30 days.',
       N'Document our objection workflow and ensure procurement reviews Stripe sub-processor change notices.',
       N'PASS_WITH_CONDITIONS', 2.5, '2026-12-15',
       '2025-12-15T10:30:00', N'DPO', '2025-12-22T11:00:00', N'CFO', N'APPROVED'),

      (@org, N'PROC-2026-006', N'HubSpot Germany GmbH', N'DEU',
       N'Marketing automation, opt-in CONTACT + BEHAVIORAL data of consenting prospects only.',
       N'أتمتة التسويق للعملاء المحتملين الذين منحوا موافقتهم الصريحة فقط.',
       N'ANNUAL',
       N'PASS', N'PASS', N'PASS', N'PASS', N'PASS_WITH_CONDITIONS', N'PASS', N'PASS', N'PASS',
       N'HubSpot DPA 2025-11 · ISO 27001 · SOC2',
       N'Breach notification SLA is "without undue delay" rather than the 72h we''d prefer; HubSpot to confirm 72h commitment in writing.',
       N'Obtain written confirmation that HubSpot will notify within 72h of confirmed breach affecting our tenant.',
       N'PASS_WITH_CONDITIONS', 2.5, '2027-01-12',
       '2026-01-12T14:00:00', N'DPO', '2026-01-18T09:00:00', N'CMO', N'APPROVED'),

      (@org, N'PROC-2026-007', N'Microsoft 365 (Office tenancy)', N'IRL',
       N'Productivity suite: email, OneDrive, Teams, SharePoint. Touches all data categories in incidental scope.',
       N'حزمة الإنتاجية، تعالج جميع فئات البيانات بشكل ضمني.',
       N'ANNUAL',
       N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS', N'PASS',
       N'Microsoft DPA · M365 ISO 27018 · annual transparency report',
       N'Reference processor with mature, well-documented controls. EU-region tenancy keeps data in Ireland.',
       N'None — continue annual cadence.',
       N'PASS', 1.0, '2027-02-01',
       '2026-02-01T08:30:00', N'DPO', '2026-02-08T10:00:00', N'CIO', N'APPROVED'),

      (@org, N'PROC-2026-008', N'New vendor — Sentry IO (errors)', N'USA',
       N'Application error reporting. Stack traces may incidentally contain user IDs and IP addresses.',
       N'تقارير أخطاء التطبيقات، قد تحتوي على معرّفات مستخدمين وعناوين IP بشكل عرضي.',
       N'ONBOARDING',
       NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
       NULL,
       NULL,
       NULL,
       NULL, NULL, NULL,
       NULL, NULL, NULL, NULL, N'IN_PROGRESS');
END
GO

-- V53 — Third-party cyber risk assessment (gap-analysis item).
--
-- Distinct from the generic VendorAssessment and the PDPL-Art-28 processor
-- assessment, this entity captures a cyber-specific risk picture of each
-- third party: external attack surface posture, certification stack, breach
-- history, vulnerability hygiene, security questionnaire score, and the
-- derived overall risk tier.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'tprm')
    EXEC('CREATE SCHEMA tprm');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'third_party_cyber_assessments' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.third_party_cyber_assessments (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT        NOT NULL,
        external_id                 NVARCHAR(64)  NULL,
        assessment_code             NVARCHAR(40)  NULL,

        vendor_id                   BIGINT        NULL,
        vendor_name                 NVARCHAR(255) NOT NULL,
        vendor_country              NVARCHAR(3)   NULL,

        -- Inherent risk if the vendor were breached, set at creation time:
        --   LOW | MEDIUM | HIGH | CRITICAL
        inherent_cyber_risk         NVARCHAR(20)  NOT NULL,

        scope_description_en        NVARCHAR(MAX) NOT NULL,
        scope_description_ar        NVARCHAR(MAX) NULL,

        -- comma-separated list of certifications (ISO27001, SOC2, PCI_DSS, NCA_ECC, ISO22301, FedRAMP, CSA_STAR, HITRUST)
        certifications              NVARCHAR(MAX) NULL,
        certifications_expiry       NVARCHAR(MAX) NULL,   -- comma-separated dates parallel to above

        -- 0-100 score from external attack-surface monitoring (e.g. SecurityScorecard, BitSight)
        external_posture_score      INT           NULL,
        -- 0-100 internal security questionnaire score
        questionnaire_score         INT           NULL,
        questionnaire_completed_at  DATETIME2     NULL,

        breach_history_24m          SMALLINT      NOT NULL DEFAULT 0,
        last_known_breach_date      DATE          NULL,
        last_known_breach_summary   NVARCHAR(MAX) NULL,

        critical_vulns_open         INT           NULL,
        patch_sla_compliance_pct    INT           NULL,
        mfa_enforced                SMALLINT      NOT NULL DEFAULT 0,
        pen_test_within_12m         SMALLINT      NOT NULL DEFAULT 0,

        -- Derived overall tier (recomputed by service on save)
        --   LOW | MODERATE | ELEVATED | HIGH | CRITICAL
        residual_risk_tier          NVARCHAR(20)  NOT NULL DEFAULT N'MODERATE',
        risk_score                  DECIMAL(5,2)  NULL,

        findings_en                 NVARCHAR(MAX) NULL,
        findings_ar                 NVARCHAR(MAX) NULL,
        treatment_plan_en           NVARCHAR(MAX) NULL,
        treatment_plan_ar           NVARCHAR(MAX) NULL,
        next_review_due             DATE          NULL,

        assessed_at                 DATETIME2     NULL,
        assessor_name               NVARCHAR(255) NULL,
        approved_at                 DATETIME2     NULL,
        approver_name               NVARCHAR(255) NULL,

        -- DRAFT | IN_REVIEW | APPROVED | RETIRED
        status                      NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',

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

    CREATE INDEX IX_tpc_org_status   ON tprm.third_party_cyber_assessments (organisation_id, status, is_deleted);
    CREATE INDEX IX_tpc_residual     ON tprm.third_party_cyber_assessments (organisation_id, residual_risk_tier);
    CREATE INDEX IX_tpc_next_review  ON tprm.third_party_cyber_assessments (organisation_id, next_review_due);
END
GO

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM tprm.third_party_cyber_assessments WHERE organisation_id = @org)
BEGIN
    INSERT INTO tprm.third_party_cyber_assessments
      (organisation_id, assessment_code, vendor_name, vendor_country, inherent_cyber_risk,
       scope_description_en, scope_description_ar, certifications, certifications_expiry,
       external_posture_score, questionnaire_score, questionnaire_completed_at,
       breach_history_24m, last_known_breach_date, last_known_breach_summary,
       critical_vulns_open, patch_sla_compliance_pct, mfa_enforced, pen_test_within_12m,
       residual_risk_tier, risk_score,
       findings_en, treatment_plan_en, next_review_due,
       assessed_at, assessor_name, approved_at, approver_name, status)
    VALUES
      (@org, N'TPC-2026-001', N'Solutions by STC', N'SAU', N'CRITICAL',
       N'Managed cloud + connectivity for the production estate; access to all production network segments.',
       N'الخدمات السحابية المُدارة والاتصال للبيئة الإنتاجية مع وصول لجميع شرائح الشبكة.',
       N'ISO27001,SOC2,NCA_ECC,CSA_STAR', N'2026-09-30,2026-04-01,2025-12-01,2026-10-15',
       86, 92, '2026-03-15T10:00:00',
       0, NULL, NULL, 0, 95, 1, 1,
       N'LOW', 12.50,
       N'Strong overall posture. Saudi-native operator with NCA-mandated controls. Annual pentest report received and reviewed.',
       N'Continue annual cadence; track NCA_ECC certificate renewal due Dec 2025.',
       '2027-03-15',
       '2026-03-15T10:00:00', N'Vendor Risk Manager — Faisal Al-Dosari',
       '2026-03-22T14:00:00', N'CISO', N'APPROVED'),

      (@org, N'TPC-2026-002', N'AWS Inc.', N'USA', N'CRITICAL',
       N'Primary cloud provider; hosts all production data + analytics workloads in eu-west-1.',
       N'مزود السحابة الرئيسي يستضيف بيانات الإنتاج وأحمال التحليل في منطقة أيرلندا.',
       N'ISO27001,SOC2,PCI_DSS,FedRAMP,CSA_STAR', N'2027-06-30,2026-09-15,2026-04-30,2027-01-31,2026-11-01',
       94, 88, '2026-02-20T11:00:00',
       0, NULL, NULL, 0, 98, 1, 1,
       N'LOW', 8.00,
       N'AWS continues to be reference-grade. All major certifications current; pentest summary shared.',
       N'No action required.',
       '2027-02-20',
       '2026-02-20T11:00:00', N'Vendor Risk Manager',
       '2026-02-27T15:00:00', N'CISO', N'APPROVED'),

      (@org, N'TPC-2026-003', N'OpenAI LLC', N'USA', N'HIGH',
       N'LLM API used by compliance-copilot. Inputs may include CONTACT and FREE_TEXT consented data.',
       N'واجهة برمجة الذكاء التوليدي المستخدمة من قبل أداة مساعد الامتثال.',
       N'SOC2', N'2026-08-31',
       62, 55, '2026-04-10T14:00:00',
       1, '2023-03-20', N'Public 2023 incident — Redis bug exposed a small number of paid-customer billing details for ~9 hours. Remediated; OpenAI published RCA.',
       2, 65, 1, 0,
       N'ELEVATED', 58.50,
       N'External posture score 62/100 — well below cohort. SOC2 is the only certification; no ISO27001, no FedRAMP. Two open critical CVEs in their public stack. Pentest reports unavailable to non-Enterprise customers.',
       N'Migrate to Azure OpenAI Service under existing Microsoft DPA; if not feasible by Q3, require contractual commitment to ISO27001 by end of FY27.',
       '2026-10-10',
       '2026-04-10T14:00:00', N'Vendor Risk Manager + CISO',
       '2026-04-15T16:00:00', N'CISO', N'APPROVED'),

      (@org, N'TPC-2026-004', N'TCS Saudi Arabia', N'SAU', N'HIGH',
       N'Payroll BPO with access to EMPLOYEE_PII + SALARY. Delivery from KSA + India.',
       N'مزود BPO للرواتب يصل إلى بيانات الموظفين والرواتب، التسليم من السعودية والهند.',
       N'ISO27001,SOC2,ISO22301', N'2026-08-30,2026-05-01,2027-03-15',
       78, 82, '2026-01-28T09:30:00',
       0, NULL, NULL,
       1, 88, 1, 1,
       N'MODERATE', 28.00,
       N'Solid certification stack and reasonable scores. One open critical-severity CVE in their Citrix infrastructure (patch scheduled within SLA).',
       N'Follow up on Citrix CVE patch completion. No other action.',
       '2027-01-28',
       '2026-01-28T09:30:00', N'Vendor Risk Manager',
       '2026-02-04T13:00:00', N'CISO', N'APPROVED'),

      (@org, N'TPC-2026-005', N'HubSpot Germany GmbH', N'DEU', N'MEDIUM',
       N'Marketing automation with CONTACT + BEHAVIORAL data of consented prospects.',
       N'أتمتة التسويق لبيانات العملاء المحتملين الذين منحوا موافقتهم.',
       N'ISO27001,SOC2', N'2026-12-15,2026-10-30',
       81, 84, '2026-01-15T10:00:00',
       0, NULL, NULL, 0, 92, 1, 1,
       N'LOW', 16.00,
       N'Moderate inherent risk, solid posture. No findings.',
       N'No action required.',
       '2027-01-15',
       '2026-01-15T10:00:00', N'Vendor Risk Manager', '2026-01-22T11:00:00', N'CMO',
       N'APPROVED'),

      (@org, N'TPC-2026-006', N'New vendor — Sentry IO', N'USA', N'MEDIUM',
       N'Application error monitoring; stack traces may include incidental PII.',
       N'مراقبة أخطاء التطبيقات؛ قد تحتوي تتبعات المكدّس على بيانات شخصية عرضية.',
       N'SOC2', N'2026-11-30',
       73, NULL, NULL,
       0, NULL, NULL, 0, 80, 1, 0,
       N'MODERATE', 34.00,
       N'Questionnaire not yet completed. External posture acceptable but cert stack thin (SOC2 only).',
       N'Complete questionnaire; require ISO27001 within 24 months as part of renewal.',
       '2026-09-15',
       NULL, NULL, NULL, NULL, N'IN_REVIEW'),

      (@org, N'TPC-2026-007', N'Legacy vendor — Backup Tape Logistics LLC', N'SAU', N'LOW',
       N'Off-site backup-tape collection. Tapes are AES-256 encrypted; vendor has no logical access.',
       N'استلام النسخ الاحتياطية الخارجية. الأشرطة مشفّرة AES-256 ولا يوجد وصول منطقي.',
       N'', N'',
       45, 60, '2024-08-01T10:00:00',
       0, NULL, NULL, 0, 70, 0, 0,
       N'MODERATE', 30.00,
       N'External posture is weak (no website security headers, exposed admin login) but logical access is nil so impact is bounded to physical handling. Tapes encrypted at rest.',
       N'Either contract a more security-mature vendor or retire physical tape rotation (move to cloud immutable backups).',
       '2026-08-01',
       '2024-08-01T10:00:00', N'Legacy assessment', NULL, NULL, N'RETIRED'),

      (@org, N'TPC-2026-008', N'Stripe Payments Europe Ltd', N'NLD', N'CRITICAL',
       N'Payment processing for the consumer mobile app; PCI scope.',
       N'معالجة المدفوعات لتطبيق الجوال؛ نطاق PCI.',
       N'ISO27001,SOC2,PCI_DSS', N'2026-11-30,2027-02-15,2026-09-30',
       92, 90, '2025-12-15T10:30:00',
       0, NULL, NULL, 0, 99, 1, 1,
       N'LOW', 9.50,
       N'Reference-grade controls; PCI Level 1 attestation current; no findings.',
       N'No action required.',
       '2026-12-15',
       '2025-12-15T10:30:00', N'Vendor Risk Manager', '2025-12-22T11:00:00', N'CFO',
       N'APPROVED');
END
GO

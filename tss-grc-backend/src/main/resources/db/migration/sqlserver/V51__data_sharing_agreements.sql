-- V51 — Data Sharing Agreements (gap-analysis: PDPL data sharing / disclosure).
--
-- One row per agreement governing the disclosure of personal data from this
-- organisation to a named recipient. Distinct from a Processor agreement
-- (PDPL Art. 28 — see V52) because the recipient is a controller, not a
-- processor; common cases: regulator (NCA, SAMA, MoCI), credit bureau (SIMAH),
-- group company in another jurisdiction, joint venture partner.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'data_sharing_agreements' AND schema_id = SCHEMA_ID('privacy'))
BEGIN
    CREATE TABLE privacy.data_sharing_agreements (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        agreement_code           NVARCHAR(40)  NULL,

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,

        -- REGULATOR | CREDIT_BUREAU | GROUP_AFFILIATE | JOINT_VENTURE
        --   | PUBLIC_AUTHORITY | RESEARCH | COMMERCIAL_PARTNER
        recipient_type           NVARCHAR(30)  NOT NULL,
        recipient_name           NVARCHAR(255) NOT NULL,
        recipient_country        NVARCHAR(3)   NULL,
        recipient_legal_role     NVARCHAR(30)  NOT NULL DEFAULT N'CONTROLLER',

        -- comma-separated, e.g. "IDENTITY,CONTACT,FINANCIAL"
        data_categories          NVARCHAR(MAX) NOT NULL,
        -- comma-separated subjects, e.g. "CUSTOMER,EMPLOYEE"
        data_subjects            NVARCHAR(MAX) NOT NULL,

        purpose_en               NVARCHAR(MAX) NOT NULL,
        purpose_ar               NVARCHAR(MAX) NULL,

        -- PDPL Art. 6: CONSENT | LEGAL_OBLIGATION | CONTRACT | VITAL_INTEREST | PUBLIC_INTEREST | LEGITIMATE_INTEREST
        lawful_basis             NVARCHAR(30)  NOT NULL,
        -- PDPL Art. 29 mechanism if cross-border:
        --   NONE | ADEQUACY | SCC | BCR | EXPLICIT_CONSENT | CONTRACT_NECESSITY | VITAL_INTEREST | PUBLIC_INTEREST
        cross_border_mechanism   NVARCHAR(30)  NOT NULL DEFAULT N'NONE',

        retention_period_days    INT           NULL,
        sub_disclosure_allowed   SMALLINT      NOT NULL DEFAULT 0,
        subject_notified         SMALLINT      NOT NULL DEFAULT 1,

        -- linked workflows (free-form refs)
        linked_ropa_codes        NVARCHAR(MAX) NULL,
        linked_privacy_notice_id BIGINT        NULL,
        linked_cbt_id            BIGINT        NULL,

        effective_from           DATE          NULL,
        effective_to             DATE          NULL,
        signed_at                DATETIME2     NULL,
        signer_name              NVARCHAR(255) NULL,
        counterparty_signer      NVARCHAR(255) NULL,

        -- DRAFT | IN_REVIEW | ACTIVE | EXPIRED | TERMINATED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        termination_reason_en    NVARCHAR(MAX) NULL,

        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2     NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0
    );

    CREATE INDEX IX_dsa_org_status     ON privacy.data_sharing_agreements (organisation_id, status, is_deleted);
    CREATE INDEX IX_dsa_recipient_type ON privacy.data_sharing_agreements (organisation_id, recipient_type);
    CREATE INDEX IX_dsa_effective_to   ON privacy.data_sharing_agreements (organisation_id, effective_to);
END
GO

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy.data_sharing_agreements WHERE organisation_id = @org)
BEGIN
    INSERT INTO privacy.data_sharing_agreements
      (organisation_id, agreement_code, title_en, title_ar, recipient_type, recipient_name,
       recipient_country, recipient_legal_role, data_categories, data_subjects,
       purpose_en, purpose_ar, lawful_basis, cross_border_mechanism, retention_period_days,
       sub_disclosure_allowed, subject_notified, linked_ropa_codes,
       effective_from, effective_to, signed_at, signer_name, counterparty_signer, status)
    VALUES
      (@org, N'DSA-2026-001', N'SIMAH credit reporting disclosure',
       N'الإفصاح لشركة سمة لتقرير الائتمان',
       N'CREDIT_BUREAU', N'Saudi Credit Bureau (SIMAH)',
       N'SAU', N'CONTROLLER', N'IDENTITY,FINANCIAL,EMPLOYMENT',
       N'CUSTOMER',
       N'Monthly credit-event reporting and inquiry as required by SAMA''s consumer credit rules. Subject notice contained in the customer onboarding pack.',
       N'الإبلاغ الشهري عن أحداث الائتمان والاستفسار عنها وفق متطلبات البنك المركزي السعودي.',
       N'LEGAL_OBLIGATION', N'NONE', 1825, 0, 1, N'ROPA-001',
       '2024-01-01', '2027-01-01', '2023-11-12T10:00:00', N'DPO — Sara Al-Otaibi', N'SIMAH Legal — Khalid Al-Saad',
       N'ACTIVE'),

      (@org, N'DSA-2026-002', N'Yakeen identity verification with Elm',
       N'التحقق من الهوية مع شركة علم — يقين',
       N'PUBLIC_AUTHORITY', N'Elm Tabadul (Yakeen)',
       N'SAU', N'CONTROLLER', N'IDENTITY,CONTACT',
       N'CUSTOMER,EMPLOYEE',
       N'Real-time National ID verification for KYC + employee onboarding. Volumes capped at 30,000 lookups/month under the framework agreement.',
       N'التحقق الفوري من بطاقة الهوية الوطنية لأغراض اعرف عميلك وتعيين الموظفين.',
       N'CONTRACT', N'NONE', NULL, 0, 1, N'ROPA-001,ROPA-005',
       '2024-04-01', '2026-04-01', '2024-03-12T13:00:00', N'COO', N'Elm Account Manager',
       N'ACTIVE'),

      (@org, N'DSA-2026-003', N'Group HR data flow to TSS Holdings (Dubai)',
       N'تدفّق بيانات الموارد البشرية للمجموعة إلى TSS Holdings (دبي)',
       N'GROUP_AFFILIATE', N'TSS Holdings FZE',
       N'ARE', N'JOINT_CONTROLLER', N'EMPLOYEE_PII,SALARY',
       N'EMPLOYEE',
       N'Quarterly transfer of consolidated headcount + payroll figures for group financial reporting. No individual-level PII transferred — aggregated metrics only.',
       N'نقل ربع سنوي لبيانات إجمالية فقط عن أعداد الموظفين والرواتب لأغراض التقارير المالية للمجموعة.',
       N'LEGITIMATE_INTEREST', N'BCR', 2555, 1, 1, N'ROPA-005',
       '2024-01-15', '2027-01-15', '2023-12-04T09:30:00', N'CHRO', N'Group GC — Layla Al-Ghamdi',
       N'ACTIVE'),

      (@org, N'DSA-2026-004', N'NCA cyber-incident notification feed',
       N'تغذية إشعارات الحوادث السيبرانية لـ NCA',
       N'REGULATOR', N'National Cybersecurity Authority (NCA)',
       N'SAU', N'CONTROLLER', N'INCIDENT_METADATA',
       N'NOT_APPLICABLE',
       N'Mandatory notification of qualifying cyber incidents within the NCA-defined timeline (2h critical, 24h others). No customer PII shared unless explicitly authorised.',
       N'الإشعار الإلزامي بالحوادث السيبرانية المؤهلة وفق الإطار الزمني الذي حددته الهيئة الوطنية للأمن السيبراني.',
       N'LEGAL_OBLIGATION', N'NONE', NULL, 0, 0, NULL,
       '2024-01-01', NULL, '2023-10-01T08:00:00', N'CISO', N'NCA Liaison Officer',
       N'ACTIVE'),

      (@org, N'DSA-2026-005', N'Marketing analytics co-controller with HubSpot',
       N'تحليلات التسويق كمراقب مشترك مع HubSpot',
       N'COMMERCIAL_PARTNER', N'HubSpot Germany GmbH',
       N'DEU', N'JOINT_CONTROLLER', N'CONTACT,BEHAVIORAL',
       N'CUSTOMER',
       N'Joint controller arrangement for opt-in marketing analytics. HubSpot may use aggregated data to improve its product; cannot use individual records for cross-tenant insights.',
       N'ترتيب مراقب مشترك لتحليلات التسويق القائمة على الموافقة الصريحة.',
       N'CONSENT', N'SCC', 730, 0, 1, N'ROPA-004',
       '2025-02-01', '2027-02-01', '2025-01-22T11:30:00', N'CMO', N'HubSpot Data Protection Officer',
       N'ACTIVE'),

      (@org, N'DSA-2026-006', N'Academic partnership — KFUPM research collaboration',
       N'شراكة أكاديمية مع جامعة الملك فهد للبترول والمعادن',
       N'RESEARCH', N'King Fahd University of Petroleum and Minerals (KFUPM)',
       N'SAU', N'CONTROLLER', N'BEHAVIORAL',
       N'CUSTOMER',
       N'Anonymised behavioural-data feed for a 12-month fraud-detection ML study. Pseudonymisation key held by us — KFUPM never sees raw PII.',
       N'تغذية بيانات سلوكية مجهولة الهوية لدراسة لمدة 12 شهراً حول الكشف عن الاحتيال.',
       N'CONSENT', N'NONE', 365, 0, 1, N'ROPA-004',
       '2025-06-01', '2026-06-01', '2025-05-15T14:00:00', N'CISO', N'KFUPM Dean of Research',
       N'EXPIRED'),

      (@org, N'DSA-2026-007', N'Legacy MoCI consumer-complaint feed',
       N'تغذية بيانات شكاوى المستهلكين القديمة لوزارة التجارة',
       N'PUBLIC_AUTHORITY', N'Ministry of Commerce (MoCI)',
       N'SAU', N'CONTROLLER', N'IDENTITY,CONTACT,COMPLAINT_DETAILS',
       N'CUSTOMER',
       N'Legacy daily extract of consumer complaints. Superseded by the MoCI online portal in 2024-Q4 — agreement scheduled for termination but kept active for 6-month transition window.',
       N'تغذية يومية قديمة لبيانات شكاوى المستهلكين، حلّ محلّها البوابة الإلكترونية للوزارة.',
       N'LEGAL_OBLIGATION', N'NONE', NULL, 0, 0, NULL,
       '2022-01-01', '2025-12-31', '2021-12-12T10:00:00', N'Compliance Lead', N'MoCI Director',
       N'TERMINATED'),

      (@org, N'DSA-2026-008', N'Cross-border group cyber-threat intel sharing',
       N'تبادل معلومات التهديدات السيبرانية ضمن المجموعة عبر الحدود',
       N'GROUP_AFFILIATE', N'TSS Group SOCs (Dubai, Cairo, London)',
       N'ARE', N'JOINT_CONTROLLER', N'INCIDENT_METADATA,IOC_DATA',
       N'NOT_APPLICABLE',
       N'Bilateral indicators-of-compromise sharing between group SOCs. No personal data; technical artefacts only.',
       N'تبادل ثنائي لمؤشرات الاختراق التقنية بين مراكز عمليات الأمن المجموعة.',
       N'LEGITIMATE_INTEREST', N'BCR', NULL, 1, 0, NULL,
       '2024-09-01', NULL, '2024-08-12T15:00:00', N'CISO', N'Group CISO',
       N'IN_REVIEW');
END
GO

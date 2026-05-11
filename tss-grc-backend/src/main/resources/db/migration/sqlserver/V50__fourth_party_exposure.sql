-- V50 — Fourth-party exposure (gap-analysis item: SCM sub-tier visibility).
--
-- A fourth-party is a vendor-of-a-vendor: someone we don't contract with
-- directly but whose disruption / breach can cascade through our primary
-- supplier into our own operations. NCA / SAMA outsourcing rules increasingly
-- expect the institution to map at least one tier beyond its direct vendors
-- and to flag concentration risk where a single fourth-party sits behind
-- multiple primaries.
--
-- This table is the register. One row per (primary vendor → fourth party)
-- edge, with the dependency type, criticality of the downstream service,
-- concentration risk (DERIVED) and last-validated date.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'scm')
    EXEC('CREATE SCHEMA scm');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'fourth_party_exposures' AND schema_id = SCHEMA_ID('scm'))
BEGIN
    CREATE TABLE scm.fourth_party_exposures (
        id                        BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id           BIGINT        NOT NULL,
        external_id               NVARCHAR(64)  NULL,

        primary_vendor_id         BIGINT        NULL,
        primary_vendor_name       NVARCHAR(255) NOT NULL,
        fourth_party_name         NVARCHAR(255) NOT NULL,
        fourth_party_country      NVARCHAR(3)   NULL,

        -- DATA_PROCESSING | INFRASTRUCTURE | SOFTWARE | LOGISTICS | FINANCIAL | SUPPORT
        dependency_type           NVARCHAR(30)  NOT NULL,
        -- LOW | MEDIUM | HIGH | CRITICAL
        downstream_criticality    NVARCHAR(20)  NOT NULL,
        -- LOW | MEDIUM | HIGH (auto-elevated when same fourth party appears N+ times)
        concentration_risk        NVARCHAR(20)  NOT NULL DEFAULT N'LOW',

        service_description_en    NVARCHAR(MAX) NOT NULL,
        service_description_ar    NVARCHAR(MAX) NULL,

        data_categories_shared    NVARCHAR(MAX) NULL,
        contract_chain_clauses    NVARCHAR(MAX) NULL,
        sub_contracting_allowed   SMALLINT      NOT NULL DEFAULT 0,
        flow_down_clauses_present SMALLINT      NOT NULL DEFAULT 0,

        last_validated_at         DATETIME2     NULL,
        last_validated_by         BIGINT        NULL,
        validator_name            NVARCHAR(255) NULL,
        next_validation_due       DATE          NULL,

        -- DISCOVERED | VALIDATED | MITIGATED | RETIRED
        status                    NVARCHAR(20)  NOT NULL DEFAULT N'DISCOVERED',
        mitigation_notes_en       NVARCHAR(MAX) NULL,
        mitigation_notes_ar       NVARCHAR(MAX) NULL,

        is_deleted                SMALLINT      NOT NULL DEFAULT 0,
        is_active                 SMALLINT      NOT NULL DEFAULT 1,
        deleted_at                DATETIME2     NULL,
        deleted_by                BIGINT        NULL,
        created_at                DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                BIGINT        NULL,
        updated_at                DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by                BIGINT        NULL,
        row_version               BIGINT        NOT NULL DEFAULT 0
    );

    CREATE INDEX IX_4p_org_status   ON scm.fourth_party_exposures (organisation_id, status, is_deleted);
    CREATE INDEX IX_4p_primary      ON scm.fourth_party_exposures (organisation_id, primary_vendor_id);
    CREATE INDEX IX_4p_fourth_party ON scm.fourth_party_exposures (organisation_id, fourth_party_name);
END
GO

-- ─── Seed demo data ─────────────────────────────────────────────────────
DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM scm.fourth_party_exposures WHERE organisation_id = @org)
BEGIN
    INSERT INTO scm.fourth_party_exposures
      (organisation_id, primary_vendor_name, fourth_party_name, fourth_party_country,
       dependency_type, downstream_criticality, concentration_risk,
       service_description_en, service_description_ar, data_categories_shared,
       sub_contracting_allowed, flow_down_clauses_present, status,
       last_validated_at, next_validation_due, validator_name, mitigation_notes_en)
    VALUES
      (@org, N'Solutions by STC', N'Cloudflare Inc.', N'USA',
       N'INFRASTRUCTURE', N'HIGH', N'MEDIUM',
       N'WAF + edge caching layer in front of the STC managed-cloud egress; outage cascades to our customer portals.',
       N'طبقة جدار حماية تطبيقات الويب أمام بوابة STC السحابية؛ يؤدي انقطاعها إلى تعطّل بوابات العملاء.',
       N'IP addresses, request headers (no payload)',
       1, 1, N'VALIDATED',
       DATEADD(MONTH, -2, SYSUTCDATETIME()), DATEADD(MONTH, 10, SYSUTCDATETIME()), N'Ali Al-Qahtani',
       N'STC contract explicitly names Cloudflare as named sub-processor; quarterly attestation file received.'),

      (@org, N'AWS', N'NVIDIA Corporation', N'USA',
       N'INFRASTRUCTURE', N'MEDIUM', N'LOW',
       N'GPU silicon supplier underpinning the AWS Bedrock / SageMaker capacity we use for fraud-detection models.',
       N'مورّد رقاقات الذكاء الاصطناعي خلف خدمات AWS التي نستخدمها للكشف عن الاحتيال.',
       NULL, 1, 0, N'DISCOVERED',
       NULL, DATEADD(MONTH, 6, SYSUTCDATETIME()), NULL,
       N'Hardware supply-chain risk only; no PII flows. Monitor for export-control constraints.'),

      (@org, N'Workday Ltd', N'Amazon Web Services (AWS Dublin)', N'IRL',
       N'INFRASTRUCTURE', N'CRITICAL', N'HIGH',
       N'Workday HCM tenants host on AWS Ireland; outage = payroll halt. Same AWS region also hosts our own analytics workloads — concentration risk.',
       N'تستضيف Workday بياناتنا على AWS أيرلندا — نفس المنطقة المستخدمة لأحمالنا التحليلية مما يُنشئ خطر التركّز.',
       N'EMPLOYEE_PII, SALARY, BANK_ACCOUNT',
       1, 1, N'VALIDATED',
       DATEADD(MONTH, -1, SYSUTCDATETIME()), DATEADD(MONTH, 5, SYSUTCDATETIME()), N'Nora Al-Subaie',
       N'Concentration risk: AWS eu-west-1 is also our primary cloud region. Plan: shift Workday tenant to AWS Frankfurt by Q3 2026.'),

      (@org, N'HubSpot', N'Sendgrid (Twilio)', N'USA',
       N'SOFTWARE', N'MEDIUM', N'LOW',
       N'Email delivery layer used by HubSpot for marketing + transactional emails to customers.',
       N'طبقة إرسال البريد الإلكتروني المستخدمة من قبل HubSpot للحملات التسويقية ومراسلات العملاء.',
       N'CONTACT (email + name only)',
       1, 1, N'VALIDATED',
       DATEADD(MONTH, -3, SYSUTCDATETIME()), DATEADD(MONTH, 9, SYSUTCDATETIME()), N'Mohammed Al-Harbi',
       NULL),

      (@org, N'TCS Saudi Arabia', N'Tata Consultancy Services India HQ', N'IND',
       N'SUPPORT', N'HIGH', N'MEDIUM',
       N'Payroll BPO operations executed from TCS India delivery centres; staffed by Indian nationals processing Saudi PII.',
       N'تُنفّذ عمليات BPO من مراكز TCS الهند ويعالج موظفوها بيانات شخصية لمواطنين سعوديين.',
       N'EMPLOYEE_PII, SALARY',
       0, 1, N'MITIGATED',
       DATEADD(MONTH, -1, SYSUTCDATETIME()), DATEADD(MONTH, 5, SYSUTCDATETIME()), N'Reem Al-Mutairi',
       N'BCR + EU adequacy do not apply (PDPL transfer mechanism = BCR). Annual processor audit performed; access scoped to KSA payroll segment only.'),

      (@org, N'OpenAI LLC (Azure tenant)', N'Microsoft Corporation', N'USA',
       N'INFRASTRUCTURE', N'CRITICAL', N'HIGH',
       N'OpenAI API runs on Azure-hosted infrastructure; a Microsoft outage takes our LLM-backed compliance copilots with it. Microsoft also operates our Office 365 tenancy — concentration risk.',
       N'تعمل واجهة OpenAI على بنية Microsoft Azure؛ كما تستضيف Microsoft خدمات Office 365 لدينا مما يُنشئ خطر تركّز عالٍ.',
       N'CONTACT, IDENTITY, FREE_TEXT (no SPI)',
       1, 0, N'DISCOVERED',
       NULL, DATEADD(MONTH, 1, SYSUTCDATETIME()), NULL,
       N'No flow-down PDPL clauses in the OpenAI agreement reaching Microsoft. Action: insist on EU-region endpoint + verifiable sub-processor list, or replace with vendor-of-record solution.'),

      (@org, N'Dow Jones Risk & Compliance', N'Reuters / Refinitiv', N'GBR',
       N'DATA_PROCESSING', N'HIGH', N'LOW',
       N'Sanctions / PEP screening dataset back-end. Refinitiv ingests primary OFAC / UN / EU watchlists, normalised and re-distributed via Dow Jones.',
       N'مصدر بيانات قوائم العقوبات والشخصيات السياسية المعرّضة للمخاطر — تُغذّى أصلاً من Refinitiv.',
       N'No PII; reference data only',
       1, 1, N'VALIDATED',
       DATEADD(MONTH, -1, SYSUTCDATETIME()), DATEADD(MONTH, 11, SYSUTCDATETIME()), N'Faisal Al-Dosari',
       N'Reference-data only — concentration risk limited to data integrity rather than confidentiality.');
END
GO

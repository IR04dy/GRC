-- ============================================================================
-- TSS GRC — Compliance Module: Regulatory Change Management + Policy Lifecycle
-- ============================================================================
-- Implements the two CRITICAL-priority recommendations from §5.3 of the buyer
-- assessment:
--   #1 Regulatory change management — track new regulations / amendments
--      issued by Saudi authorities, run an applicability assessment, capture
--      impact, drive control updates, close out.
--   #2 Policy lifecycle — author → legal review → executive approval →
--      publication → review-due → retired.
--
-- DELIBERATELY no CHECK constraints on enum-like columns. The SCM module
-- demonstrated how easy it is for the frontend to drift from CHECK constraints
-- and break POSTs at the DB layer; this module documents allowed values inline
-- and trusts the service-layer state-machine guards instead.
--
-- Idempotent: safe to re-run (CREATE IF NOT EXISTS pattern, INSERT WHERE NOT
-- EXISTS for seed rows).
--
-- Apply via:
--   sqlcmd -S localhost -d TSS_GRC -U sa -P tss.123 -I -i V_compliance_change_policy_ddl.sql
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

-- ── Table: compliance.regulatory_changes ───────────────────────────────────
-- status                ∈ {NEW, APPLICABILITY_ASSESSED, IMPACT_ASSESSED, CONTROLS_UPDATED, CLOSED, NOT_APPLICABLE}
-- applicability         ∈ {UNKNOWN, APPLICABLE, NOT_APPLICABLE, PARTIAL}
-- impact_level          ∈ {LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN}
-- source_authority      free text, suggested values: NCA, SDAIA, SAMA, CMA,
--                        ZATCA, CITC, NCEMA, MoCI, HRSD, MoH, MOI, OTHER
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'compliance' AND t.name = 'regulatory_changes')
BEGIN
  CREATE TABLE compliance.regulatory_changes (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_reg_change_extid DEFAULT NEWID(),
    organisation_id       BIGINT NOT NULL,
    source_authority      NVARCHAR(50) NOT NULL,
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500) NULL,
    citation              NVARCHAR(255) NULL,
    summary_en            NVARCHAR(MAX) NULL,
    summary_ar            NVARCHAR(MAX) NULL,
    published_date        DATE NULL,
    effective_date        DATE NULL,
    applicability         NVARCHAR(30) NOT NULL CONSTRAINT DF_reg_change_appl DEFAULT 'UNKNOWN',
    impact_level          NVARCHAR(20) NOT NULL CONSTRAINT DF_reg_change_impact DEFAULT 'UNKNOWN',
    status                NVARCHAR(40) NOT NULL CONSTRAINT DF_reg_change_status DEFAULT 'NEW',
    impact_summary        NVARCHAR(MAX) NULL,
    controls_updated_count INT NOT NULL CONSTRAINT DF_reg_change_ctrlcount DEFAULT 0,
    assigned_to_id        BIGINT NULL,
    closed_at             DATETIME2 NULL,
    is_deleted            BIT NOT NULL CONSTRAINT DF_reg_change_isdel DEFAULT 0,
    created_at            DATETIME2 NOT NULL CONSTRAINT DF_reg_change_created DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2 NULL,
    created_by            BIGINT NULL,
    updated_by            BIGINT NULL,
    deleted_at            DATETIME2 NULL,
    deleted_by            BIGINT NULL,
    row_version           ROWVERSION
  );

  CREATE INDEX IX_reg_change_org      ON compliance.regulatory_changes(organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_reg_change_status   ON compliance.regulatory_changes(status, organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_reg_change_authority ON compliance.regulatory_changes(source_authority, organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_reg_change_effective ON compliance.regulatory_changes(effective_date) WHERE is_deleted = 0;

  PRINT N'Created compliance.regulatory_changes';
END
ELSE
BEGIN
  PRINT N'compliance.regulatory_changes already exists — DDL skipped';
END

-- ── Table: compliance.policies ─────────────────────────────────────────────
-- status   ∈ {DRAFT, LEGAL_REVIEW, EXEC_APPROVAL, PUBLISHED, REVIEW_DUE, RETIRED, REJECTED}
-- category free text — suggested: INFORMATION_SECURITY, PRIVACY, RISK_MANAGEMENT,
--          BUSINESS_CONTINUITY, ACCEPTABLE_USE, INCIDENT_RESPONSE, HR, FINANCE
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'compliance' AND t.name = 'policies')
BEGIN
  CREATE TABLE compliance.policies (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_policy_extid DEFAULT NEWID(),
    organisation_id       BIGINT NOT NULL,
    code                  NVARCHAR(50) NOT NULL,
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500) NULL,
    version               NVARCHAR(20) NOT NULL CONSTRAINT DF_policy_version DEFAULT N'1.0',
    category              NVARCHAR(50) NULL,
    content_en            NVARCHAR(MAX) NULL,
    content_ar            NVARCHAR(MAX) NULL,
    status                NVARCHAR(30) NOT NULL CONSTRAINT DF_policy_status DEFAULT 'DRAFT',
    author_id             BIGINT NULL,
    legal_reviewer_id     BIGINT NULL,
    legal_reviewed_at     DATETIME2 NULL,
    approver_id           BIGINT NULL,
    approved_at           DATETIME2 NULL,
    published_at          DATETIME2 NULL,
    effective_from        DATE NULL,
    next_review_date      DATE NULL,
    retired_at            DATETIME2 NULL,
    rejection_reason      NVARCHAR(MAX) NULL,
    is_deleted            BIT NOT NULL CONSTRAINT DF_policy_isdel DEFAULT 0,
    created_at            DATETIME2 NOT NULL CONSTRAINT DF_policy_created DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2 NULL,
    created_by            BIGINT NULL,
    updated_by            BIGINT NULL,
    deleted_at            DATETIME2 NULL,
    deleted_by            BIGINT NULL,
    row_version           ROWVERSION
  );

  CREATE INDEX IX_policy_org      ON compliance.policies(organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_policy_status   ON compliance.policies(status, organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_policy_review   ON compliance.policies(next_review_date) WHERE is_deleted = 0 AND status = 'PUBLISHED';
  CREATE UNIQUE INDEX IX_policy_code_org ON compliance.policies(organisation_id, code) WHERE is_deleted = 0;

  PRINT N'Created compliance.policies';
END
ELSE
BEGIN
  PRINT N'compliance.policies already exists — DDL skipped';
END

-- ── Seed: regulatory changes (Saudi-relevant samples) ──────────────────────
INSERT INTO compliance.regulatory_changes
  (organisation_id, source_authority, title_en, title_ar, citation, summary_en,
   published_date, effective_date, applicability, impact_level, status, impact_summary)
SELECT v.org, v.auth, v.en, v.ar, v.cite, v.summ, v.pubd, v.effd, v.appl, v.impact, v.status, v.imp_summ
FROM (VALUES
  (1, N'SDAIA', N'PDPL Implementing Regulations — published',
   N'اللائحة التنفيذية لنظام حماية البيانات الشخصية',
   N'Saudi PDPL Implementing Regulations 2024',
   N'Implementing Regulations operationalise the Personal Data Protection Law: define controllers and processors, set out data subject rights including the 30-day fulfilment window, mandate breach notification within 72 hours of discovery, and define cross-border transfer mechanisms requiring Saudi Data Office consultation for non-adequate jurisdictions.',
   '2024-09-14', '2024-09-14', N'APPLICABLE', N'CRITICAL', N'CONTROLS_UPDATED',
   N'Privacy module DSR 30-day SLA tracker and breach 72-hour clock implemented. RoPA records aligned to Article 30. DPIA workflow scheduled.'),
  (1, N'NCA', N'Essential Cybersecurity Controls v2.0',
   N'الضوابط الأساسية للأمن السيبراني الإصدار 2.0',
   N'NCA ECC v2.0',
   N'Updated 114-control baseline across five main domains: Cybersecurity Governance, Cybersecurity Defence, Cybersecurity Resilience, Third-Party Cybersecurity, and Industrial Control Systems Cybersecurity. Mandatory baseline for all government entities and Critical National Infrastructure. Two-year compliance window for new control additions.',
   '2024-01-15', '2024-07-15', N'APPLICABLE', N'HIGH', N'IMPACT_ASSESSED',
   N'29 representative controls pre-loaded into the Cyber module catalogue with EN+AR descriptions and ISO 27001 mapping. Remaining 85 controls are scheduled for catalogue expansion in Q3.'),
  (1, N'SAMA', N'Cybersecurity Framework — Annual Update',
   N'إطار الأمن السيبراني — التحديث السنوي',
   N'SAMA CSF 2025 amendment',
   N'Annual updates to the SAMA Cybersecurity Framework, principally tightening mandatory cyber incident sharing requirements, requiring quarterly attestation of control maturity by board-level CRO, and adding cloud-resident workload controls aligned with NCA CCC.',
   '2025-01-01', '2025-03-01', N'PARTIAL', N'HIGH', N'APPLICABILITY_ASSESSED',
   N'Applicable to SAMA-licensed entities only. Mid-tier banking customers in scope; non-banking customers exempt.'),
  (1, N'CITC', N'Cloud Computing Regulatory Framework v3',
   N'إطار تنظيمي للحوسبة السحابية الإصدار الثالث',
   N'CITC CCRF v3',
   N'Updated cloud computing regulatory framework introducing four data classification tiers (Highly Confidential / Confidential / Restricted / Public) and corresponding cloud-region-residency requirements. Level C and D workloads require Saudi-sovereign hosting.',
   '2024-11-01', '2025-05-01', N'UNKNOWN', N'UNKNOWN', N'NEW',
   N''),
  (1, N'CMA', N'Cybersecurity Guide for Listed Entities',
   N'دليل الأمن السيبراني للجهات المدرجة',
   N'CMA Cybersecurity Guide 2025',
   N'Issuer disclosure requirements for material cybersecurity incidents. Listed entities must disclose to the market within 24 hours of materiality determination, with quarterly cyber-readiness attestation.',
   '2025-02-15', '2025-08-15', N'UNKNOWN', N'UNKNOWN', N'NEW',
   N''),
  (1, N'ZATCA', N'E-Invoicing Phase 2 Wave 5',
   N'الفوترة الإلكترونية المرحلة الثانية — الموجة الخامسة',
   N'ZATCA Fatoora P2 Wave 5',
   N'Fifth wave of e-invoicing integration mandates, capturing taxpayers with annual revenue above SAR 30 million but below SAR 70 million. Integration with ZATCA Fatoora portal required including QR code generation and cryptographic stamping.',
   '2024-08-01', '2025-03-01', N'NOT_APPLICABLE', N'LOW', N'NOT_APPLICABLE',
   N'Customer falls below the revenue threshold for this wave; will be reassessed if next wave brings the threshold lower.')
) AS v(org, auth, en, ar, cite, summ, pubd, effd, appl, impact, status, imp_summ)
WHERE NOT EXISTS (
  SELECT 1 FROM compliance.regulatory_changes c
  WHERE c.organisation_id = v.org AND c.title_en = v.en AND c.is_deleted = 0
);

-- ── Seed: policies (representative samples) ───────────────────────────────
INSERT INTO compliance.policies
  (organisation_id, code, title_en, title_ar, version, category, status,
   effective_from, next_review_date, content_en)
SELECT v.org, v.code, v.en, v.ar, v.ver, v.cat, v.status, v.effd, v.nrd, v.content
FROM (VALUES
  (1, N'POL-INFO-001', N'Information Security Policy', N'سياسة أمن المعلومات',
   N'2.1', N'INFORMATION_SECURITY', N'PUBLISHED',
   '2025-01-01', '2026-01-01',
   N'This policy establishes the organisation''s framework for information security, aligned with NCA Essential Cybersecurity Controls v2.0 and ISO/IEC 27001:2022. All personnel and third parties handling organisational information are bound by this policy.'),
  (1, N'POL-PRIV-001', N'Personal Data Protection Policy', N'سياسة حماية البيانات الشخصية',
   N'1.0', N'PRIVACY', N'PUBLISHED',
   '2024-09-14', '2025-09-14',
   N'This policy defines how the organisation collects, processes, retains, and erases personal data of Saudi data subjects in accordance with the Personal Data Protection Law and its Implementing Regulations.'),
  (1, N'POL-BCM-001', N'Business Continuity Policy', N'سياسة استمرارية الأعمال',
   N'1.2', N'BUSINESS_CONTINUITY', N'PUBLISHED',
   '2024-06-01', '2025-06-01',
   N'This policy commits the organisation to maintaining critical business functions during and after disruptive events, aligned with ISO 22301:2019 and NCEMA standards.'),
  (1, N'POL-RISK-001', N'Enterprise Risk Management Policy', N'سياسة إدارة مخاطر المؤسسة',
   N'2.0', N'RISK_MANAGEMENT', N'EXEC_APPROVAL',
   NULL, NULL,
   N'This policy describes the organisation''s approach to identifying, assessing, treating, and monitoring enterprise risks, aligned with ISO 31000:2018 and COSO ERM 2017.'),
  (1, N'POL-AUP-001', N'Acceptable Use Policy', N'سياسة الاستخدام المقبول',
   N'1.5', N'ACCEPTABLE_USE', N'DRAFT',
   NULL, NULL,
   N'This policy defines acceptable use of organisational technology assets and information.')
) AS v(org, code, en, ar, ver, cat, status, effd, nrd, content)
WHERE NOT EXISTS (
  SELECT 1 FROM compliance.policies p
  WHERE p.organisation_id = v.org AND p.code = v.code AND p.is_deleted = 0
);

-- ── Result summary ─────────────────────────────────────────────────────────
SELECT
  N'Compliance change-and-policy DDL run complete' AS status,
  (SELECT COUNT(*) FROM compliance.regulatory_changes WHERE organisation_id=1 AND is_deleted=0) AS regulatory_changes,
  (SELECT COUNT(*) FROM compliance.policies WHERE organisation_id=1 AND is_deleted=0) AS policies;

GO

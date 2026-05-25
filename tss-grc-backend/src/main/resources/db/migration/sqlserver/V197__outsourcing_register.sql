-- V197 — Sprint 13: Outsourcing Arrangement Register.
--
-- SAMA Rules Governing Outsourcing (2019, updated 2023) require every
-- Saudi bank to maintain a register of all outsourcing arrangements —
-- material + non-material — with documented due diligence, ongoing
-- monitoring, contractual SLAs, exit planning, and PDPL-compliance
-- attestation. Today this lives in Procurement's Excel spreadsheets;
-- this migration ships the structured surface for Compliance + Audit
-- Committee.
--
-- Regulatory anchors:
--   - SAMA Rules Governing Outsourcing 2019 §3 — register + risk
--     assessment per arrangement (material + non-material).
--   - SAMA Rules Governing Outsourcing 2019 §4.2 — Material Outsourcing
--     requires SAMA non-objection before arrangement go-live.
--   - NCA ECC-2 control 4-1 — Third-Party Cybersecurity (every
--     outsourcing arrangement requires cybersecurity due diligence).
--   - PDPL Art. 29 — Personal data processing by processors requires
--     written DPA + cross-border safeguard if data leaves KSA.
--
-- Four tables, all under tprm schema (already created by V7):
--
--   tprm.outsourcing_arrangement   — one row per arrangement
--   tprm.outsourcing_due_diligence — multi-row, one per assessment
--   tprm.outsourcing_monitoring_event — events flagged during BAU
--   tprm.outsourcing_exit_plan     — one row per arrangement
--
-- Lifecycle state machine (enforced in OutsourcingArrangementService):
--   PLANNED          → DUE_DILIGENCE | EXITED
--   DUE_DILIGENCE    → AWAITING_SAMA | APPROVED | EXITED
--   AWAITING_SAMA    → APPROVED | EXITED
--   APPROVED         → ACTIVE | EXITED
--   ACTIVE           → MONITORING_ALERT | UNDER_REVIEW | EXIT_PLANNING | EXITED
--   MONITORING_ALERT → ACTIVE | UNDER_REVIEW | EXIT_PLANNING | EXITED
--   UNDER_REVIEW     → ACTIVE | EXIT_PLANNING | EXITED
--   EXIT_PLANNING    → EXIT_IN_PROGRESS | EXITED
--   EXIT_IN_PROGRESS → EXITED
--   EXITED           (terminal)
--
-- Pre-seed for org=1 (TSS Bank) — 8 arrangements, mix of material +
-- non-material, all bilingual EN+AR with Saudi-native phrasing:
--
--   OUT-2026-001 — Microsoft Azure KSA Region (IT_INFRASTRUCTURE, material)
--   OUT-2026-002 — AWS me-south-1 Region (CLOUD_SAAS, material)
--   OUT-2026-003 — Lean Technologies open banking (IT_INFRASTRUCTURE, material)
--   OUT-2026-004 — Solutions by stc call centre (CALL_CENTRE, material)
--   OUT-2026-005 — Bahri cash logistics (CASH_LOGISTICS, material)
--   OUT-2026-006 — SITE printing & distribution (PRINTING_DISTRIBUTION, non-material)
--   OUT-2026-007 — ComplyAdvantage sanctions API (AML_KYC_SCREENING, material, cross-border UK)
--   OUT-2026-008 — Tabseer CRC payments (PAYMENTS, material)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- tprm schema already exists from V7 — guard anyway.
IF SCHEMA_ID('tprm') IS NULL EXEC('CREATE SCHEMA tprm');
GO

-- ─── outsourcing_arrangement ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'outsourcing_arrangement' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.outsourcing_arrangement (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        arrangement_code                NVARCHAR(64)    NOT NULL,   -- e.g. OUT-2026-001
        title_en                        NVARCHAR(255)   NOT NULL,
        title_ar                        NVARCHAR(255)   NOT NULL,
        description_en                  NVARCHAR(MAX)   NULL,
        description_ar                  NVARCHAR(MAX)   NULL,

        -- IT_INFRASTRUCTURE | CLOUD_SAAS | CUSTOMER_OPERATIONS |
        -- BACK_OFFICE_PROCESSING | CALL_CENTRE | CASH_LOGISTICS |
        -- MARKETING | LEGAL | INTERNAL_AUDIT | IT_DEVELOPMENT |
        -- ADMINISTRATIVE | SHARIA_AUDIT | ANALYTICS_BI |
        -- AML_KYC_SCREENING | PRINTING_DISTRIBUTION | FACILITY_MGMT
        service_type                    VARCHAR(40)     NOT NULL,

        primary_vendor_id               BIGINT          NULL,
        co_vendor_ids                   NVARCHAR(500)   NULL,       -- CSV vendor IDs for multi-vendor arrangements

        is_material                     BIT             NOT NULL,
        materiality_basis_en            NVARCHAR(1000)  NULL,
        materiality_basis_ar            NVARCHAR(1000)  NULL,

        sama_non_objection_required     BIT             NOT NULL DEFAULT 0,
        sama_non_objection_obtained     BIT             NOT NULL DEFAULT 0,
        sama_non_objection_ref          NVARCHAR(255)   NULL,
        sama_non_objection_date         DATE            NULL,

        contract_start_date             DATE            NULL,
        contract_end_date               DATE            NULL,
        contract_value_sar_annual       DECIMAL(18,2)   NULL,
        contract_ref                    NVARCHAR(255)   NULL,
        contract_document_url           NVARCHAR(500)   NULL,

        -- PAYMENTS | CORE_BANKING | AML_CFT | CUSTOMER_DATA |
        -- CYBERSECURITY | BCM_DRP | REGULATORY_REPORTING |
        -- FINANCIAL_REPORTING | NA
        critical_function               VARCHAR(40)     NOT NULL DEFAULT 'NA',

        country_of_processing           VARCHAR(2)      NOT NULL,   -- ISO 3166-1 alpha-2
        region_of_processing            NVARCHAR(255)   NULL,
        data_residency_kdc              BIT             NOT NULL DEFAULT 1,    -- KSA Data Center

        personal_data_processed         BIT             NOT NULL DEFAULT 0,
        pdpl_dpa_signed                 BIT             NOT NULL DEFAULT 0,
        pdpl_dpa_date                   DATE            NULL,

        -- SCC_PLUS_TIA | NDMO_GA | BCR | DEROGATION | NA
        cross_border_safeguard_type     VARCHAR(40)     NULL,

        -- PLANNED | DUE_DILIGENCE | AWAITING_SAMA | APPROVED | ACTIVE |
        -- MONITORING_ALERT | UNDER_REVIEW | EXIT_PLANNING |
        -- EXIT_IN_PROGRESS | EXITED
        lifecycle_stage                 VARCHAR(20)     NOT NULL DEFAULT 'PLANNED',

        owner_user_id                   BIGINT          NOT NULL,
        sponsor_user_id                 BIGINT          NOT NULL,

        next_review_due_date            DATE            NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_outsourcing_vendor
            FOREIGN KEY (primary_vendor_id) REFERENCES tprm.vendors(id)
    );

    -- arrangement_code unique per tenant (active rows only)
    CREATE UNIQUE INDEX UX_outsourcing_org_code
        ON tprm.outsourcing_arrangement(organisation_id, arrangement_code)
        WHERE is_deleted = 0;

    -- "Show me material arrangements awaiting SAMA non-objection"
    CREATE INDEX IX_outsourcing_org_material_stage
        ON tprm.outsourcing_arrangement(organisation_id, is_material, lifecycle_stage)
        WHERE is_deleted = 0;

    -- "Show me arrangements overdue for review"
    CREATE INDEX IX_outsourcing_org_review_due
        ON tprm.outsourcing_arrangement(organisation_id, next_review_due_date)
        WHERE is_deleted = 0;

    -- "Filter by critical function"
    CREATE INDEX IX_outsourcing_org_critical_function
        ON tprm.outsourcing_arrangement(organisation_id, critical_function)
        WHERE is_deleted = 0;

    -- Vendor lookup
    CREATE INDEX IX_outsourcing_org_vendor
        ON tprm.outsourcing_arrangement(organisation_id, primary_vendor_id)
        WHERE is_deleted = 0;
END
GO

-- ─── outsourcing_due_diligence ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'outsourcing_due_diligence' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.outsourcing_due_diligence (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        arrangement_id                  BIGINT          NOT NULL,

        assessment_date                 DATE            NOT NULL,
        assessor_user_id                BIGINT          NOT NULL,

        -- Six sub-scores, 0-100. Composite is the simple average.
        financial_health_score          INT             NOT NULL,
        operational_capability_score    INT             NOT NULL,
        cybersecurity_score             INT             NOT NULL,
        bcm_score                       INT             NOT NULL,
        pdpl_compliance_score           INT             NOT NULL,
        reputation_score                INT             NOT NULL,

        composite_score                 DECIMAL(5,2)    NOT NULL,

        -- APPROVE | CONDITIONAL | REJECT
        recommendation                  VARCHAR(20)     NOT NULL,

        rationale_en                    NVARCHAR(2000)  NOT NULL,
        rationale_ar                    NVARCHAR(2000)  NOT NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_outsourcing_dd_arrangement
            FOREIGN KEY (arrangement_id) REFERENCES tprm.outsourcing_arrangement(id)
    );

    CREATE INDEX IX_outsourcing_dd_org_arr
        ON tprm.outsourcing_due_diligence(organisation_id, arrangement_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_outsourcing_dd_org_assess_date
        ON tprm.outsourcing_due_diligence(organisation_id, assessment_date)
        WHERE is_deleted = 0;
END
GO

-- ─── outsourcing_monitoring_event ──────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'outsourcing_monitoring_event' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.outsourcing_monitoring_event (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        arrangement_id                  BIGINT          NOT NULL,

        -- KPI_REVIEW | SLA_BREACH | INCIDENT | AUDIT_REPORT |
        -- CONTRACT_AMENDMENT | FINANCIAL_HEALTH_FLAG |
        -- REGULATORY_QUERY | CHANGE_OF_CONTROL | RFP_RECOMPETE |
        -- EXIT_TRIGGER
        event_type                      VARCHAR(40)     NOT NULL,
        event_date                      DATE            NOT NULL,

        -- INFO | MINOR | MODERATE | MAJOR | CRITICAL
        severity                        VARCHAR(20)     NOT NULL DEFAULT 'INFO',

        description_en                  NVARCHAR(2000)  NOT NULL,
        description_ar                  NVARCHAR(2000)  NOT NULL,

        action_taken_en                 NVARCHAR(1000)  NULL,
        action_taken_ar                 NVARCHAR(1000)  NULL,

        resolved                        BIT             NOT NULL DEFAULT 0,
        resolved_at                     DATETIME2       NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_outsourcing_evt_arrangement
            FOREIGN KEY (arrangement_id) REFERENCES tprm.outsourcing_arrangement(id)
    );

    CREATE INDEX IX_outsourcing_evt_org_arr
        ON tprm.outsourcing_monitoring_event(organisation_id, arrangement_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_outsourcing_evt_org_severity
        ON tprm.outsourcing_monitoring_event(organisation_id, severity, resolved)
        WHERE is_deleted = 0;

    CREATE INDEX IX_outsourcing_evt_org_type
        ON tprm.outsourcing_monitoring_event(organisation_id, event_type)
        WHERE is_deleted = 0;
END
GO

-- ─── outsourcing_exit_plan ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'outsourcing_exit_plan' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.outsourcing_exit_plan (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        arrangement_id                  BIGINT          NOT NULL,

        trigger_conditions_en           NVARCHAR(2000)  NOT NULL,
        trigger_conditions_ar           NVARCHAR(2000)  NOT NULL,

        -- JSON array: [{ "step": "...", "owner": "...", "target_date_relative_days": 30 }, ...]
        exit_steps_json                 NVARCHAR(MAX)   NULL,

        data_repatriation_plan_en       NVARCHAR(2000)  NULL,
        data_repatriation_plan_ar       NVARCHAR(2000)  NULL,

        -- NEVER | ANNUAL | BIANNUAL | NEVER_TESTED
        test_frequency                  VARCHAR(20)     NOT NULL DEFAULT 'NEVER_TESTED',
        last_tested_date                DATE            NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_outsourcing_exit_arrangement
            FOREIGN KEY (arrangement_id) REFERENCES tprm.outsourcing_arrangement(id)
    );

    -- One exit plan per arrangement (active rows only)
    CREATE UNIQUE INDEX UX_outsourcing_exit_arrangement
        ON tprm.outsourcing_exit_plan(organisation_id, arrangement_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_outsourcing_exit_org_test_date
        ON tprm.outsourcing_exit_plan(organisation_id, last_tested_date)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed for org=1 ────────────────────────────────────────────
--
-- Idempotent — every INSERT checks for existing rows. owner_user_id /
-- sponsor_user_id default to 1 (PLATFORM_ADMIN) — the bank's
-- operational owner/sponsor are updated post-seed via the PATCH
-- endpoint when the real user IDs exist.

-- (1) OUT-2026-001 — Microsoft Azure KSA Region
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-001')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-001',
         'Microsoft Azure — KSA Region (Riyadh + Jeddah)',
         N'مايكروسوفت أزور — منطقة المملكة العربية السعودية (الرياض + جدّة)',
         'Hyperscale cloud infrastructure hosting core regulatory workloads (data lake, analytics, M365 Copilot for compliance) on the KSA Azure region opened in 2024. Material under SAMA §4.2 — hosts customer data + regulatory reporting datasets.',
         N'بنية تحتية سحابية فائقة الحجم تستضيف الأعباء التنظيمية الجوهرية (بحيرة البيانات، التحليلات، Copilot للالتزام) على منطقة أزور بالمملكة العربية السعودية المفتوحة منذ 2024. إسناد جوهري وفق المادة §4.2 من قواعد مؤسسة النقد — يستضيف بيانات العملاء ومجموعات بيانات الرفع التنظيمي.',
         'IT_INFRASTRUCTURE', 1,
         'Hosts customer PII + SAMA Q-CAR returns + AML transaction analytics. Concentration risk: largest single cloud vendor in the bank.',
         N'يستضيف بيانات العملاء الشخصية + تقارير CAR لمؤسسة النقد + تحليلات معاملات مكافحة غسل الأموال. تركّز المخاطر: أكبر مورّد سحابي منفرد في البنك.',
         1, 1, 'SAMA-NO-2024-1234', '2024-03-15',
         '2024-04-01', '2027-03-31', 18500000.00, 'EA-MSFT-KSA-2024-001',
         'CUSTOMER_DATA', 'SA', 'Riyadh (Primary) / Jeddah (DR)', 1,
         1, 1, '2024-03-20',
         'ACTIVE', 1, 1, '2026-12-31');
END
GO

-- (2) OUT-2026-002 — AWS me-south-1
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-002',
         'AWS me-south-1 (Bahrain Region — KSA Local Zone)',
         N'AWS منطقة الشرق الأوسط me-south-1 (البحرين — مع المنطقة المحلية بالمملكة)',
         'Secondary hyperscale cloud for the digital banking app (Native Saudi mobile) — Aurora PostgreSQL, S3 object store, CloudFront. KSA Local Zone keeps customer data resident; control plane in me-south-1 (Bahrain).',
         N'سحابة هايبر سكيلر ثانوية لتطبيق المصرفية الرقمية (Native Saudi للجوال) — Aurora PostgreSQL و S3 و CloudFront. المنطقة المحلية بالمملكة تُبقي بيانات العملاء مُتوطّنة محلياً؛ مستوى التحكم في منطقة me-south-1 (البحرين).',
         'CLOUD_SAAS', 1,
         'Mobile-banking traffic path; concentration risk if Azure region unavailable. Customer data resident in KSA Local Zone but control plane outside.',
         N'مسار حركة المصرفية عبر الجوّال؛ تركّز المخاطر في حال تعطّل منطقة أزور. بيانات العملاء مُتوطّنة في المنطقة المحلية بالمملكة لكن مستوى التحكم خارجها.',
         1, 1, 'SAMA-NO-2024-2189', '2024-06-10',
         '2024-07-01', '2027-06-30', 12200000.00, 'EA-AWS-KSA-2024-002',
         'CUSTOMER_DATA', 'SA', 'KSA Local Zone (data) / me-south-1 Bahrain (control)', 1,
         1, 1, '2024-06-15',
         'ACTIVE', 1, 1, '2026-12-31');
END
GO

-- (3) OUT-2026-003 — Lean Technologies (open banking aggregator)
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-003')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-003',
         'Lean Technologies — Open Banking Account Information Service',
         N'Lean Technologies — خدمة معلومات الحسابات للخدمات المصرفية المفتوحة',
         'Account Information Service Provider (AISP) license under SAMA Open Banking Framework. Provides account aggregation, balance + transaction APIs to the bank''s PFM module.',
         N'مزوّد خدمة معلومات الحسابات (AISP) مرخّص ضمن إطار الخدمات المصرفية المفتوحة لمؤسسة النقد. يقدم تجميع الحسابات وواجهات الأرصدة والمعاملات لوحدة إدارة الميزانية الشخصية بالبنك.',
         'IT_INFRASTRUCTURE', 1,
         'Direct customer-data flow under SAMA Open Banking framework. Outage degrades PFM and onboarding journey.',
         N'تدفّق مباشر لبيانات العملاء وفق إطار الخدمات المصرفية المفتوحة لمؤسسة النقد. أيّ انقطاع يُضعف إدارة الميزانية الشخصية ورحلة فتح الحساب.',
         1, 1, 'SAMA-NO-2025-0042', '2025-01-20',
         '2025-02-01', '2027-01-31', 4800000.00, 'OB-LEAN-2025-001',
         'CUSTOMER_DATA', 'SA', 'Riyadh', 1,
         1, 1, '2025-01-25',
         'ACTIVE', 1, 1, '2026-09-30');
END
GO

-- (4) OUT-2026-004 — Solutions by stc (call centre)
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-004',
         'Solutions by stc — 24×7 Customer Contact Centre',
         N'حلول من stc — مركز التواصل مع العملاء على مدار الساعة',
         '24×7 multi-channel contact centre (inbound voice, chat, social, WhatsApp Business). 800-seat capacity across Riyadh + Jeddah delivery centres. Material under SAMA §4.2 — customer-facing operations + access to customer accounts via CRM.',
         N'مركز التواصل متعدد القنوات على مدار الساعة (الاتصال الوارد، الدردشة، وسائل التواصل، واتساب للأعمال). طاقة استيعابية 800 موظف عبر مركزَي تشغيل بالرياض وجدّة. إسناد جوهري وفق المادة §4.2 — عمليات تواجه العميل + وصول إلى حسابات العملاء عبر CRM.',
         'CALL_CENTRE', 1,
         'Agents have read access to customer account data. Outage prevents complaints handling within SAMA-mandated SLAs.',
         N'يملك الوكلاء صلاحية قراءة بيانات حسابات العملاء. الانقطاع يُعطّل معالجة الشكاوى ضمن مهل مؤسسة النقد الزمنية.',
         1, 1, 'SAMA-NO-2023-3401', '2023-11-08',
         '2024-01-01', '2026-12-31', 22400000.00, 'CC-STCSOL-2024-001',
         'CUSTOMER_DATA', 'SA', 'Riyadh + Jeddah delivery centres', 1,
         1, 1, '2023-11-15',
         'ACTIVE', 1, 1, '2026-08-31');
END
GO

-- (5) OUT-2026-005 — Bahri Logistics (cash logistics)
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-005')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-005',
         'Bahri Logistics — Cash-in-Transit + ATM Replenishment',
         N'البحري للخدمات اللوجستية — نقل النقد وتعبئة الصرّاف الآلي',
         'Armoured cash-in-transit (CIT) covering 1,200 ATMs across all 13 regions. Daily settlement to/from SAMA cash centres. Material because outage halts ATM availability.',
         N'النقل المدرّع للنقد يغطي 1,200 جهاز صرّاف آلي في المناطق الثلاث عشرة. التسوية اليومية من وإلى مراكز نقد مؤسسة النقد. جوهري لأنّ الانقطاع يُوقف توافر الصرّاف الآلي.',
         'CASH_LOGISTICS', 1,
         'Direct hit to branch + ATM availability if disrupted. SAMA monitors ATM uptime — every outage reportable.',
         N'تأثير مباشر على توافر الفروع والصرّاف الآلي عند التعطّل. مؤسسة النقد تُراقب جاهزية الصرّاف الآلي — كلّ انقطاع قابل للإبلاغ.',
         1, 1, 'SAMA-NO-2022-1789', '2022-09-12',
         '2023-01-01', '2025-12-31', 9600000.00, 'CIT-BAHRI-2023-001',
         'PAYMENTS', 'SA', 'All 13 regions of KSA', 1,
         0, 0, NULL,
         'UNDER_REVIEW', 1, 1, '2025-09-30');
END
GO

-- (6) OUT-2026-006 — SITE (printing & distribution) — NON-MATERIAL
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-006')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-006',
         'Saudi Information Technology Co (SITE) — Card Printing & Distribution',
         N'الشركة السعودية لتقنية المعلومات (SITE) — طباعة وتوزيع البطاقات',
         'Personalised card printing (mada + Visa + Mastercard) and secure courier delivery to branches + customer addresses. Non-material under SAMA §4.2: customer data minimised, no card-systems integration.',
         N'طباعة البطاقات الشخصية (مدى + فيزا + ماستركارد) والتوصيل الآمن إلى الفروع وعناوين العملاء. غير جوهري وفق المادة §4.2 من قواعد مؤسسة النقد: بيانات العملاء مُقلَّصة، لا تكامل مع أنظمة البطاقات.',
         'PRINTING_DISTRIBUTION', 0,
         NULL, NULL,
         0, 0,
         '2023-06-01', '2026-05-31', 1850000.00, 'PRT-SITE-2023-001',
         'NA', 'SA', 'Riyadh', 1,
         1, 1, '2023-05-20',
         'ACTIVE', 1, 1, '2026-05-31');
END
GO

-- (7) OUT-2026-007 — ComplyAdvantage (sanctions screening API) — cross-border
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-007')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         cross_border_safeguard_type,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-007',
         'ComplyAdvantage — Sanctions + PEP Screening API',
         N'ComplyAdvantage — واجهة برمجة فحص العقوبات والأشخاص المعرَّضين سياسياً',
         'Real-time sanctions + PEP + adverse media screening for customer onboarding + payment screening. Material — direct AML/CFT control. Data processed in UK (London region); cross-border safeguard via NDMO General Authorisation + SCC-PLUS-TIA.',
         N'فحص العقوبات والأشخاص المعرَّضين سياسياً والأخبار السلبية في الوقت الفعلي لفتح الحسابات وفحص المدفوعات. جوهري — ضابط مباشر لمكافحة غسل الأموال وتمويل الإرهاب. تتم المعالجة في المملكة المتحدة (منطقة لندن)؛ ضمانة النقل العابر للحدود عبر التفويض العام للمكتب الوطني لإدارة البيانات + شروط تعاقدية معيارية مع تقييم أثر النقل.',
         'AML_KYC_SCREENING', 1,
         'Direct AML/CFT control; SAMA SoF expects 24×7 availability. Cross-border processing requires PDPL Art.29 safeguards.',
         N'ضابط مباشر لمكافحة غسل الأموال وتمويل الإرهاب؛ تتوقّع مؤسسة النقد توافراً على مدار الساعة. المعالجة العابرة للحدود تستلزم ضمانات المادة 29 من نظام حماية البيانات الشخصية.',
         1, 1, 'SAMA-NO-2024-0918', '2024-04-15',
         '2024-05-01', '2027-04-30', 3200000.00, 'AML-COMPLY-2024-001',
         'AML_CFT', 'GB', 'London, United Kingdom', 0,
         1, 1, '2024-04-20',
         'SCC_PLUS_TIA',
         'ACTIVE', 1, 1, '2026-10-31');
END
GO

-- (8) OUT-2026-008 — Tabseer (CRC payments)
IF NOT EXISTS (SELECT 1 FROM tprm.outsourcing_arrangement
               WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-008')
BEGIN
    INSERT INTO tprm.outsourcing_arrangement
        (organisation_id, arrangement_code, title_en, title_ar,
         description_en, description_ar,
         service_type, is_material, materiality_basis_en, materiality_basis_ar,
         sama_non_objection_required, sama_non_objection_obtained,
         sama_non_objection_ref, sama_non_objection_date,
         contract_start_date, contract_end_date, contract_value_sar_annual, contract_ref,
         critical_function, country_of_processing, region_of_processing, data_residency_kdc,
         personal_data_processed, pdpl_dpa_signed, pdpl_dpa_date,
         lifecycle_stage, owner_user_id, sponsor_user_id, next_review_due_date)
    VALUES
        (1, 'OUT-2026-008',
         'Tabseer — Common Receivables Centre (CRC) Payment Routing',
         N'تبصير — توجيه مدفوعات مركز المستحقات المشترك (CRC)',
         'CRC-cleared payment routing for government & semi-government collections (utilities, traffic fines, government fees) over SADAD + STC Pay. Material — concentrated payment rail across all retail customers.',
         N'توجيه المدفوعات المُقاصّة عبر مركز المستحقات المشترك للتحصيلات الحكومية وشبه الحكومية (الخدمات العامة، المخالفات المرورية، الرسوم الحكومية) عبر سداد + STC Pay. جوهري — مسار دفع مُركَّز يخدم جميع عملاء الأفراد.',
         'PAYMENTS', 1,
         'CRC outage halts utility + government-fee payments for all retail customers. SAMA payment-availability SLA applies.',
         N'انقطاع CRC يُوقف مدفوعات الخدمات العامة والرسوم الحكومية لجميع عملاء الأفراد. تطبق مهلة توافر المدفوعات لدى مؤسسة النقد.',
         1, 1, 'SAMA-NO-2025-1144', '2025-03-22',
         '2025-04-01', '2028-03-31', 6700000.00, 'PMT-TABSEER-2025-001',
         'PAYMENTS', 'SA', 'Riyadh', 1,
         1, 1, '2025-03-30',
         'ACTIVE', 1, 1, '2026-11-30');
END
GO

-- ─── Due Diligence — one per material arrangement (7 rows) ─────────

DECLARE @arr001 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-001');
DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
DECLARE @arr003 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-003');
DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
DECLARE @arr005 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-005');
DECLARE @arr007 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-007');
DECLARE @arr008 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-008');

IF @arr001 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr001)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr001, '2024-02-15', 1,
         95, 92, 88, 90, 87, 96,
         91.33, 'APPROVE',
         'Microsoft passes all six gates: investment-grade balance sheet, KSA region with NCA-CCC Level-3 controls, ISO 27001 + SOC 2 Type II, DR across two regions, SDAIA-aligned PDPL DPA executed.',
         N'تجتاز مايكروسوفت البوّابات الستّ جميعها: ميزانية بدرجة استثمارية، منطقة بالمملكة بضوابط NCA-CCC من المستوى الثالث، ISO 27001 + SOC 2 Type II، التعافي من الكوارث عبر منطقتين، اتفاقية معالجة بيانات منسجمة مع نظام حماية البيانات الشخصية والهيئة السعودية للبيانات والذكاء الاصطناعي.');
END
GO

DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
IF @arr002 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr002)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr002, '2024-05-20', 1,
         96, 90, 89, 85, 82, 94,
         89.33, 'CONDITIONAL',
         'AWS conditional on data-residency proof. KSA Local Zone keeps customer data resident but control plane is in Bahrain. Approved subject to monthly residency attestation + quarterly cross-border-flow review.',
         N'AWS مشروط بإثبات توطين البيانات. المنطقة المحلية بالمملكة تُبقي بيانات العملاء مُتوطّنة لكن مستوى التحكم في البحرين. الاعتماد رهين بإقرار شهري لتوطين البيانات + مراجعة ربعية للتدفّق العابر للحدود.');
END
GO

DECLARE @arr003 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-003');
IF @arr003 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr003)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr003, '2024-12-10', 1,
         78, 85, 90, 75, 92, 80,
         83.33, 'APPROVE',
         'Lean Technologies is SAMA Open Banking sandbox graduate. Strong cybersecurity posture (ISO 27001 + recent SAMA-led red team). BCM gap closed via contractually-required dual-region deployment post-2025.',
         N'Lean Technologies خرّيج البيئة التجريبية للخدمات المصرفية المفتوحة لدى مؤسسة النقد. وضع سيبراني قوي (ISO 27001 + تمرين فريق أحمر بقيادة مؤسسة النقد). تمّ سدّ فجوة استمرارية الأعمال عبر إلزام تعاقدي بنشر ثنائي المناطق بدءاً من 2025.');
END
GO

DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
IF @arr004 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr004)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr004, '2023-10-05', 1,
         93, 88, 82, 84, 80, 90,
         86.17, 'APPROVE',
         'Solutions by stc is the de facto telco-grade contact-centre supplier; multi-site delivery + extensive Saudi national workforce (Nitaqat Platinum). PDPL DPA executed with separate sub-processor agreement for WhatsApp Business API.',
         N'حلول من stc هي المورّد الفعلي لمراكز الاتصال بمستوى الاتصالات؛ تشغيل متعدد المواقع + قوّة عاملة سعودية واسعة (نطاقات بلاتيني). اتفاقية معالجة بيانات مُوقَّعة مع اتفاقية معالج فرعي مستقلّة لواجهة WhatsApp Business.');
END
GO

DECLARE @arr005 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-005');
IF @arr005 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr005)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr005, '2022-08-22', 1,
         85, 75, 70, 70, 60, 82,
         73.67, 'CONDITIONAL',
         'Bahri Logistics conditional renewal — cybersecurity gaps (legacy CIT van GPS), BCM scenarios not exercised since 2021, no PDPL DPA (no personal data processing). Renewal at 2025 cycle.',
         N'تجديد مشروط لشركة البحري — فجوات سيبرانية (نظام تتبع مركبات النقل القديم)، سيناريوهات استمرارية الأعمال لم تُختبر منذ 2021، لا اتفاقية معالجة بيانات (لا توجد معالجة بيانات شخصية). التجديد في دورة 2025.');
END
GO

DECLARE @arr007 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-007');
IF @arr007 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr007)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr007, '2024-03-18', 1,
         82, 90, 93, 88, 78, 85,
         86.00, 'APPROVE',
         'ComplyAdvantage approved with cross-border safeguards: NDMO general authorisation, SCC + TIA executed. Real-time API latency well within SLA; multi-region UK + EU active-active.',
         N'اعتماد ComplyAdvantage بضمانات النقل العابر للحدود: التفويض العام من المكتب الوطني لإدارة البيانات + الشروط التعاقدية المعيارية وتقييم أثر النقل مُوقَّعَين. زمن استجابة واجهة برمجة التطبيقات في الوقت الفعلي ضمن مهلة الخدمة بفارق مريح؛ نشر نشط-نشط بين المملكة المتحدة وأوروبا.');
END
GO

DECLARE @arr008 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-008');
IF @arr008 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_due_diligence WHERE organisation_id = 1 AND arrangement_id = @arr008)
BEGIN
    INSERT INTO tprm.outsourcing_due_diligence
        (organisation_id, arrangement_id, assessment_date, assessor_user_id,
         financial_health_score, operational_capability_score, cybersecurity_score,
         bcm_score, pdpl_compliance_score, reputation_score,
         composite_score, recommendation, rationale_en, rationale_ar)
    VALUES
        (1, @arr008, '2025-02-28', 1,
         90, 88, 87, 92, 90, 89,
         89.33, 'APPROVE',
         'Tabseer (CRC) approved — SAMA-licensed payment service provider, Tier-IV data centre in Riyadh, near-zero RPO via continuous DR, PDPL DPA fully aligned.',
         N'اعتماد تبصير (مركز المستحقات المشترك) — مزوّد خدمة دفع مرخّص من مؤسسة النقد، مركز بيانات بمستوى Tier-IV في الرياض، نقطة استرداد قريبة من الصفر عبر تعافي مستمر، اتفاقية معالجة بيانات منسجمة بالكامل.');
END
GO

-- ─── Monitoring events — 5 across the register ──────────────────────

DECLARE @arr001 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-001');
DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
DECLARE @arr005 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-005');
DECLARE @arr007 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-007');

-- (E1) Azure regional latency spike — INFO
IF @arr001 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_monitoring_event WHERE organisation_id = 1 AND arrangement_id = @arr001 AND event_type = 'KPI_REVIEW' AND event_date = '2026-02-10')
BEGIN
    INSERT INTO tprm.outsourcing_monitoring_event
        (organisation_id, arrangement_id, event_type, event_date, severity,
         description_en, description_ar, action_taken_en, action_taken_ar,
         resolved, resolved_at)
    VALUES
        (1, @arr001, 'KPI_REVIEW', '2026-02-10', 'INFO',
         'Monthly KPI review — Azure KSA region latency P95 = 18ms (well within 50ms SLA). Availability 99.99% (above 99.9 commitment).',
         N'مراجعة المؤشرات الشهرية — منطقة أزور بالمملكة زمن استجابة P95 = 18 مللي ثانية (أدنى بكثير من مهلة 50 مللي ثانية). الجاهزية 99.99٪ (أعلى من التزام 99.9٪).',
         'No action required; logged for quarterly Audit Committee pack.',
         N'لا حاجة لإجراء؛ مُسجَّل ضمن حزمة لجنة المراجعة الربعية.',
         1, '2026-02-10T15:00:00');
END
GO

-- (E2) AWS me-south-1 SLA breach — MAJOR
DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
IF @arr002 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_monitoring_event WHERE organisation_id = 1 AND arrangement_id = @arr002 AND event_type = 'SLA_BREACH' AND event_date = '2026-03-04')
BEGIN
    INSERT INTO tprm.outsourcing_monitoring_event
        (organisation_id, arrangement_id, event_type, event_date, severity,
         description_en, description_ar, action_taken_en, action_taken_ar,
         resolved, resolved_at)
    VALUES
        (1, @arr002, 'SLA_BREACH', '2026-03-04', 'MAJOR',
         'AWS me-south-1 control-plane degradation 04-Mar-2026 09:18–11:42 — KSA Local Zone data plane unaffected but mobile-app DB failovers delayed. SLA penalty triggered.',
         N'تدهور مستوى التحكّم في AWS me-south-1 بتاريخ 04-مارس-2026 من 09:18 إلى 11:42 — لم تتأثر مستوى البيانات في المنطقة المحلية بالمملكة لكن تحويلات قاعدة بيانات تطبيق الجوال تأخّرت. تمّ تفعيل غرامة مستوى الخدمة.',
         'SLA credit claimed; cross-region failover playbook updated to expect control-plane delays during me-south-1 incidents.',
         N'تمّت المطالبة باسترداد مستوى الخدمة؛ تمّ تحديث دليل التحويل بين المناطق ليتوقع تأخّر مستوى التحكم خلال أعطال me-south-1.',
         1, '2026-03-15T10:00:00');
END
GO

-- (E3) Bahri financial-health flag — MODERATE
DECLARE @arr005 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-005');
IF @arr005 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_monitoring_event WHERE organisation_id = 1 AND arrangement_id = @arr005 AND event_type = 'FINANCIAL_HEALTH_FLAG' AND event_date = '2025-11-20')
BEGIN
    INSERT INTO tprm.outsourcing_monitoring_event
        (organisation_id, arrangement_id, event_type, event_date, severity,
         description_en, description_ar, action_taken_en, action_taken_ar,
         resolved, resolved_at)
    VALUES
        (1, @arr005, 'FINANCIAL_HEALTH_FLAG', '2025-11-20', 'MODERATE',
         'Bahri Logistics Q3 2025 results — operating margin declined 8% YoY; debt service coverage ratio 1.4× (below 1.6× threshold in contract). Trigger for enhanced monitoring per Outsourcing §5.4.',
         N'نتائج البحري للخدمات اللوجستية للربع الثالث 2025 — هبوط هامش التشغيل 8٪ على أساس سنوي؛ نسبة تغطية خدمة الدين 1.4× (دون عتبة العقد 1.6×). موجب لمراقبة معزّزة وفق المادة §5.4 من قواعد الإسناد.',
         'Moved to UNDER_REVIEW lifecycle stage. Quarterly financial review until DSCR recovers to 1.6× for two consecutive quarters.',
         N'تمّ نقله إلى مرحلة "تحت المراجعة" من دورة الحياة. مراجعة مالية ربعية حتى تتعافى نسبة تغطية خدمة الدين إلى 1.6× لربعَين متتاليَين.',
         0, NULL);
END
GO

-- (E4) ComplyAdvantage regulatory query — MINOR
DECLARE @arr007 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-007');
IF @arr007 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_monitoring_event WHERE organisation_id = 1 AND arrangement_id = @arr007 AND event_type = 'REGULATORY_QUERY' AND event_date = '2026-01-22')
BEGIN
    INSERT INTO tprm.outsourcing_monitoring_event
        (organisation_id, arrangement_id, event_type, event_date, severity,
         description_en, description_ar, action_taken_en, action_taken_ar,
         resolved, resolved_at)
    VALUES
        (1, @arr007, 'REGULATORY_QUERY', '2026-01-22', 'MINOR',
         'SAMA Compliance Department query on ComplyAdvantage data-flow diagram — requested updated DPIA showing 2025 expansion to EU region. Responded with refreshed DPIA + cross-border safeguard inventory.',
         N'استفسار من إدارة الالتزام بمؤسسة النقد بشأن مخطط تدفّق بيانات ComplyAdvantage — طلب تقييم أثر معالجة بيانات محدّث يُظهر التوسع لمنطقة أوروبا في 2025. تمّ الرد بتقييم أثر معالجة بيانات مُحدَّث + جرد ضمانات النقل العابر للحدود.',
         'DPIA v3.2 submitted to SAMA via official channel; closure letter received 05-Feb-2026.',
         N'تمّ تقديم تقييم أثر معالجة البيانات إصدار 3.2 لمؤسسة النقد عبر القناة الرسمية؛ تمّ استلام خطاب الإغلاق في 05-فبراير-2026.',
         1, '2026-02-05T10:00:00');
END
GO

-- (E5) Solutions by stc contract amendment — INFO
DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
IF @arr004 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_monitoring_event WHERE organisation_id = 1 AND arrangement_id = @arr004 AND event_type = 'CONTRACT_AMENDMENT' AND event_date = '2026-04-01')
BEGIN
    INSERT INTO tprm.outsourcing_monitoring_event
        (organisation_id, arrangement_id, event_type, event_date, severity,
         description_en, description_ar, action_taken_en, action_taken_ar,
         resolved, resolved_at)
    VALUES
        (1, @arr004, 'CONTRACT_AMENDMENT', '2026-04-01', 'INFO',
         'Amendment 03 — added Arabic-language WhatsApp Business support; updated PDPL sub-processor list to include Meta WhatsApp BSP. SAMA non-objection requested under §4.2 (material change).',
         N'الملحق 03 — إضافة دعم واتساب للأعمال باللغة العربية؛ تحديث قائمة المعالجين الفرعيين لنظام حماية البيانات لتشمل مزوّد خدمة واتساب للأعمال من Meta. تمّ طلب عدم ممانعة مؤسسة النقد وفق المادة §4.2 (تغيير جوهري).',
         'Filed with SAMA on 01-Apr; non-objection received 18-Apr-2026 (ref SAMA-NO-2026-0512). DPA amendment executed same day.',
         N'تمّ التقديم لمؤسسة النقد في 01-أبريل؛ تمّ استلام عدم الممانعة في 18-أبريل-2026 (المرجع SAMA-NO-2026-0512). تنفيذ ملحق اتفاقية معالجة البيانات في اليوم نفسه.',
         1, '2026-04-18T14:30:00');
END
GO

-- ─── Exit plans — 4 for the critical arrangements ───────────────────

DECLARE @arr001 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-001');
DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
DECLARE @arr008 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-008');

IF @arr001 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_exit_plan WHERE organisation_id = 1 AND arrangement_id = @arr001)
BEGIN
    INSERT INTO tprm.outsourcing_exit_plan
        (organisation_id, arrangement_id,
         trigger_conditions_en, trigger_conditions_ar,
         exit_steps_json,
         data_repatriation_plan_en, data_repatriation_plan_ar,
         test_frequency, last_tested_date)
    VALUES
        (1, @arr001,
         'Activated on: (a) SAMA notice of unacceptable risk, (b) breach of cybersecurity / PDPL obligations not cured within 30 days, (c) sustained P95 latency above 100ms for 4+ consecutive weeks, (d) Microsoft loss of CITC cloud licence in KSA.',
         N'تُفعَّل عند: (أ) إشعار من مؤسسة النقد بمخاطر غير مقبولة، (ب) خرق التزامات الأمن السيبراني أو نظام حماية البيانات الشخصية دون معالجة خلال 30 يوماً، (ج) استمرار زمن الاستجابة P95 فوق 100 مللي ثانية لأربع أسابيع متتالية أو أكثر، (د) فقدان مايكروسوفت لرخصة الحوسبة السحابية من هيئة الاتصالات في المملكة.',
         '[{"step":"Activate alternate cloud (AWS me-south-1 + on-prem DR)","owner":"CIO","target_date_relative_days":0},{"step":"Provision read-replica of regulatory data lake on AWS","owner":"DataPlatform Lead","target_date_relative_days":7},{"step":"Migrate AML workload to on-prem fallback","owner":"AML IT Lead","target_date_relative_days":14},{"step":"Repatriate Q-CAR + SAMA reporting datasets to on-prem","owner":"Regulatory Reporting","target_date_relative_days":30},{"step":"Confirm data deletion from Azure (signed attestation)","owner":"CISO","target_date_relative_days":60},{"step":"File SAMA exit completion notice","owner":"Chief Compliance Officer","target_date_relative_days":75}]',
         'Data repatriation via Azure Data Box (KSA) + ExpressRoute. Encrypted offline transfer for control-plane secrets. Verification: SHA-256 manifest comparison; signed deletion attestation from Microsoft.',
         N'استرداد البيانات عبر Azure Data Box (بالمملكة) + ExpressRoute. نقل مشفّر دون اتصال لأسرار مستوى التحكم. التحقّق: مقارنة فهرس SHA-256؛ إقرار حذف مُوقَّع من مايكروسوفت.',
         'ANNUAL', '2025-09-15');
END
GO

DECLARE @arr002 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-002');
IF @arr002 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_exit_plan WHERE organisation_id = 1 AND arrangement_id = @arr002)
BEGIN
    INSERT INTO tprm.outsourcing_exit_plan
        (organisation_id, arrangement_id,
         trigger_conditions_en, trigger_conditions_ar,
         exit_steps_json,
         data_repatriation_plan_en, data_repatriation_plan_ar,
         test_frequency, last_tested_date)
    VALUES
        (1, @arr002,
         'Activated on: (a) SAMA directive following an unrecoverable AWS regional event, (b) breach of KSA Local Zone data-residency guarantee, (c) AWS withdrawal from KSA market.',
         N'تُفعَّل عند: (أ) توجيه مؤسسة النقد إثر حدث إقليمي غير قابل للاسترداد في AWS، (ب) خرق ضمان توطين البيانات في المنطقة المحلية بالمملكة، (ج) انسحاب AWS من سوق المملكة.',
         '[{"step":"Cut mobile-app traffic to Azure region failover","owner":"CIO","target_date_relative_days":0},{"step":"Snapshot Aurora cluster to S3 then cross-cloud copy","owner":"DBA Lead","target_date_relative_days":3},{"step":"Restore on Azure Postgres Flexible Server","owner":"DBA Lead","target_date_relative_days":10},{"step":"Cutover CloudFront → Azure Front Door","owner":"Network Lead","target_date_relative_days":21},{"step":"Repatriate S3 objects (encrypted) to Azure Blob","owner":"Storage Lead","target_date_relative_days":30},{"step":"Confirm AWS data deletion + receive signed attestation","owner":"CISO","target_date_relative_days":60}]',
         'Cross-cloud data sync via signed encrypted transfer; SHA-256 verification on each object. AWS deletion attestation per SAMA Outsourcing §6.3.',
         N'مزامنة بيانات عبر السحب باستخدام نقل مشفّر مُوقَّع؛ تحقّق SHA-256 لكلّ عنصر. إقرار حذف من AWS وفق المادة §6.3 من قواعد الإسناد لمؤسسة النقد.',
         'ANNUAL', '2025-10-22');
END
GO

DECLARE @arr004 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-004');
IF @arr004 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_exit_plan WHERE organisation_id = 1 AND arrangement_id = @arr004)
BEGIN
    INSERT INTO tprm.outsourcing_exit_plan
        (organisation_id, arrangement_id,
         trigger_conditions_en, trigger_conditions_ar,
         exit_steps_json,
         data_repatriation_plan_en, data_repatriation_plan_ar,
         test_frequency, last_tested_date)
    VALUES
        (1, @arr004,
         'Activated on: (a) SAMA complaints-SLA breach attributable to vendor for 2+ consecutive months, (b) PDPL incident at vendor causing customer data loss, (c) Nitaqat downgrade below Platinum.',
         N'تُفعَّل عند: (أ) خرق مهلة شكاوى مؤسسة النقد المنسوب إلى المورّد لشهرين متتاليين أو أكثر، (ب) حادثة لنظام حماية البيانات لدى المورّد تسبّب فقد بيانات العملاء، (ج) تراجع تصنيف نطاقات دون البلاتيني.',
         '[{"step":"Place outsourced volume on in-house overflow team","owner":"Customer Care Director","target_date_relative_days":0},{"step":"Suspend new-hire training pipeline at stc","owner":"Vendor Manager","target_date_relative_days":7},{"step":"Re-tender with 90-day notice (3 alternatives identified)","owner":"Procurement","target_date_relative_days":14},{"step":"Migrate CRM credentials + Avaya call routing","owner":"IT Ops","target_date_relative_days":45},{"step":"Repatriate call recordings + customer-contact metadata","owner":"CISO","target_date_relative_days":75},{"step":"Confirm vendor data deletion + audit","owner":"Internal Audit","target_date_relative_days":120}]',
         'Call-recording metadata + CRM session logs repatriated via SFTP into in-house Genesys Cloud tenant; cryptographic deletion confirmation per SAMA Outsourcing §6.3.',
         N'استرداد بيانات تسجيلات المكالمات + سجلات جلسات إدارة علاقات العملاء عبر SFTP إلى مستأجر Genesys Cloud الداخلي؛ تأكيد حذف تشفيري وفق المادة §6.3 من قواعد الإسناد لمؤسسة النقد.',
         'BIANNUAL', '2025-06-10');
END
GO

DECLARE @arr008 BIGINT = (SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id = 1 AND arrangement_code = 'OUT-2026-008');
IF @arr008 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tprm.outsourcing_exit_plan WHERE organisation_id = 1 AND arrangement_id = @arr008)
BEGIN
    INSERT INTO tprm.outsourcing_exit_plan
        (organisation_id, arrangement_id,
         trigger_conditions_en, trigger_conditions_ar,
         exit_steps_json,
         data_repatriation_plan_en, data_repatriation_plan_ar,
         test_frequency, last_tested_date)
    VALUES
        (1, @arr008,
         'Activated on: (a) SAMA payment-rail availability breach (Tabseer CRC routing) for 24h+, (b) loss of SAMA PSP licence, (c) sustained settlement-failure rate above 0.5% for 30 days.',
         N'تُفعَّل عند: (أ) خرق توافر مسار الدفع لمؤسسة النقد (توجيه CRC عبر تبصير) لمدة 24 ساعة أو أكثر، (ب) فقدان رخصة مزوّد خدمات الدفع من مؤسسة النقد، (ج) استمرار معدّل فشل التسوية فوق 0.5٪ لمدة 30 يوماً.',
         '[{"step":"Switch to direct SADAD + STC Pay rails (bypass Tabseer)","owner":"Payments Lead","target_date_relative_days":0},{"step":"Reconcile in-flight transactions + customer remediation","owner":"Operations","target_date_relative_days":3},{"step":"Onboard alternate CRC aggregator (RFP shortlist ready)","owner":"Procurement","target_date_relative_days":21},{"step":"Migrate routing tables + customer billers","owner":"IT Ops","target_date_relative_days":45},{"step":"Repatriate transaction logs (7-year SAMA retention)","owner":"Regulatory Reporting","target_date_relative_days":60},{"step":"Audit Tabseer data deletion","owner":"Internal Audit","target_date_relative_days":90}]',
         'Seven-year transaction-log archive repatriated to internal SQL Server (SAMA retention). SHA-256 manifest + signed Tabseer deletion attestation.',
         N'استرداد أرشيف سجلات المعاملات لسبع سنوات (متطلب احتفاظ مؤسسة النقد) إلى SQL Server داخلي. فهرس SHA-256 + إقرار حذف مُوقَّع من تبصير.',
         'ANNUAL', NULL);
END
GO

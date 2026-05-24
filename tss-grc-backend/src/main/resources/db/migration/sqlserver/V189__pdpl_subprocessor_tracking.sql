-- V189 — Sprint 11: PDPL Sub-processor Tracking.
--
-- Saudi PDPL Article 30 (Implementing Regulation Article 39) makes the
-- controller (the bank) responsible for inventorying every
-- sub-processor used in personal-data processing AND the chain of
-- authorisation AND the cross-border-transfer safeguards each
-- sub-processor relies on. SDAIA audit inspections regularly ask for
-- "the sub-processor inventory" — today the Legal team assembles it
-- by hand from Procurement spreadsheets, IT-vendor lists, and a
-- separate cross-border tracker. This migration replaces that with
-- a single live register.
--
-- Two tables under the existing `privacy` schema:
--
--   privacy.subprocessor        — register row per sub-processor
--   privacy.subprocessor_event  — append-only event ledger
--
-- Why two tables (and not one wide one)? Lifecycle separates the
-- semi-static *what* (legal name, country, lawful basis, DPA) from
-- the dynamic *what happened* (added, suspended, cross-border-
-- safeguard-changed, reviewed, incident). The event ledger gives
-- the DPO an auditable narrative for each sub-processor over time,
-- and is exactly the artefact SDAIA inspectors ask to see when they
-- spot-check authorisations.
--
-- Cross-border safeguard typing (cross_border_safeguard_type) maps to
-- SDAIA Cross-Border Data Transfer Regulations 2023:
--   KSA_RESIDENT         — processing happens inside the Kingdom
--                          (no Article 29 safeguard needed)
--   SCC_PLUS_TIA         — SDAIA-approved Standard Contractual Clauses
--                          + a Transfer Impact Assessment
--   ADEQUACY_DECISION    — destination has an SDAIA adequacy decision
--   EXPLICIT_CONSENT     — data subject's specific, informed consent
--                          (last resort, narrow scope)
--   OTHER                — escape hatch; DPO must document
--
-- The service layer enforces: if country_of_processing != 'SA' AND
-- lifecycle_stage = APPROVED, then cross_border_safeguard_type MUST
-- be non-null and one of the above. The same gate applies on
-- dpoApprove().
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'privacy') IS NULL EXEC(N'CREATE SCHEMA privacy');
GO

-- ─── 1. Sub-processor register ─────────────────────────────────────
IF OBJECT_ID(N'privacy.subprocessor', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.subprocessor (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Human-readable code, e.g. SUBP-2026-001. Unique per tenant
        -- within live rows.
        subprocessor_code             NVARCHAR(64)  NOT NULL,

        -- Self-referential parent — for a sub-of-a-sub (e.g. Microsoft
        -- contracts Akamai for edge delivery; Akamai is the parent's
        -- child here).
        parent_processor_id           BIGINT        NULL,

        -- Legal name as written in the DPA — bilingual EN/AR.
        sub_processor_legal_name      NVARCHAR(255) NOT NULL,
        sub_processor_legal_name_ar   NVARCHAR(255) NULL,

        -- Soft FK to tprm.vendor — set when the sub-processor is also
        -- a tracked vendor (most are). Soft because Procurement may
        -- onboard the vendor later.
        vendor_id                     BIGINT        NULL,

        -- ISO 3166-1 alpha-2 country code where data is processed.
        country_of_processing         VARCHAR(2)    NOT NULL,
        -- Free-form region label, e.g. "Riyadh", "Frankfurt", "us-east-1".
        region_of_processing          NVARCHAR(255) NULL,

        -- JSON arrays of PDPL data categories (e.g. ["IDENTITY","CONTACT"])
        -- and data-subject categories (e.g. ["CUSTOMERS","EMPLOYEES"]).
        -- Stored as JSON for forward compatibility with the broader
        -- PDPL taxonomy work — service layer treats as opaque strings.
        data_categories_json          NVARCHAR(MAX) NULL,
        data_subject_categories_json  NVARCHAR(MAX) NULL,

        -- Why the bank is using this sub-processor (PDPL Art.5
        -- purpose-limitation principle) — bilingual.
        processing_purpose_en         NVARCHAR(1000) NOT NULL,
        processing_purpose_ar         NVARCHAR(1000) NULL,

        -- PDPL Article 6 lawful-basis enumeration.
        --   CONSENT | CONTRACT | LEGAL_OBLIGATION | VITAL_INTEREST |
        --   PUBLIC_INTEREST | LEGITIMATE_INTEREST | REGULATORY
        lawful_basis                  VARCHAR(40)   NOT NULL,

        -- Article 30: the controller can give the processor general
        -- authorisation to engage sub-processors (with notification of
        -- changes) OR require specific prior authorisation per sub.
        --   GENERAL  — covered by the controller-processor DPA's
        --              general authorisation clause
        --   SPECIFIC — a specific written authorisation exists per this row
        authorisation_type            VARCHAR(20)   NOT NULL,
        -- Reference (storage path, DMS ID, contract clause #) for the
        -- authorisation document. Required for SPECIFIC; optional for
        -- GENERAL (the controller-processor DPA covers it).
        authorisation_basis_doc_ref   NVARCHAR(500) NULL,

        -- SDAIA Cross-Border Data Transfer Regulations 2023.
        --   KSA_RESIDENT      — no Article 29 safeguard needed
        --   SCC_PLUS_TIA      — SCC + Transfer Impact Assessment
        --   ADEQUACY_DECISION — SDAIA adequacy decision
        --   EXPLICIT_CONSENT  — data-subject consent
        --   OTHER             — DPO-documented escape hatch
        cross_border_safeguard_type   VARCHAR(40)   NULL,
        -- Reference for the SCC / adequacy decision / consent record.
        cross_border_safeguard_doc_ref NVARCHAR(500) NULL,
        -- TIA reference (e.g. cross-border-transfer record code).
        tia_reference                 NVARCHAR(255) NULL,
        tia_completed_at              DATE          NULL,
        -- SDAIA adequacy-decision reference (e.g. "SDAIA-AD-2024-007").
        adequacy_decision_ref         NVARCHAR(255) NULL,

        -- Rough volume band — the controller doesn't track per-row
        -- counts but band drives DPIA-trigger heuristics.
        --   MINIMAL | LOW | MEDIUM | HIGH | VERY_HIGH
        data_volume_band              VARCHAR(20)   NOT NULL DEFAULT N'LOW',

        -- DPA (controller-processor agreement) signing state.
        has_dpa_in_place              BIT           NOT NULL DEFAULT 0,
        dpa_signed_date               DATE          NULL,
        dpa_expiry_date               DATE          NULL,

        -- Lifecycle stage:
        --   PROPOSED          — request raised by business
        --   UNDER_DPO_REVIEW  — DPO reviewing safeguards
        --   APPROVED          — live, in production
        --   SUSPENDED         — temporarily blocked (e.g. material change)
        --   RETIRED           — no longer in use
        lifecycle_stage               VARCHAR(20)   NOT NULL DEFAULT N'PROPOSED',
        dpo_approved_by_user_id       BIGINT        NULL,
        dpo_approved_at               DATETIME2(3)  NULL,

        -- 12-month review cadence (PDPL Article 30 + SDAIA
        -- inspection-readiness norm).
        last_reviewed_at              DATETIME2(3)  NULL,
        next_review_due_at            DATETIME2(3)  NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_subp_parent FOREIGN KEY (parent_processor_id)
            REFERENCES privacy.subprocessor(id),
        CONSTRAINT CK_subp_lifecycle CHECK (lifecycle_stage IN
            (N'PROPOSED', N'UNDER_DPO_REVIEW', N'APPROVED', N'SUSPENDED', N'RETIRED')),
        CONSTRAINT CK_subp_auth_type CHECK (authorisation_type IN
            (N'GENERAL', N'SPECIFIC')),
        CONSTRAINT CK_subp_cb_safeguard CHECK (cross_border_safeguard_type IS NULL OR
            cross_border_safeguard_type IN
            (N'KSA_RESIDENT', N'SCC_PLUS_TIA', N'ADEQUACY_DECISION',
             N'EXPLICIT_CONSENT', N'OTHER')),
        CONSTRAINT CK_subp_volume CHECK (data_volume_band IN
            (N'MINIMAL', N'LOW', N'MEDIUM', N'HIGH', N'VERY_HIGH')),
        CONSTRAINT CK_subp_lawful_basis CHECK (lawful_basis IN
            (N'CONSENT', N'CONTRACT', N'LEGAL_OBLIGATION', N'VITAL_INTEREST',
             N'PUBLIC_INTEREST', N'LEGITIMATE_INTEREST', N'REGULATORY'))
    );
END
GO

-- Filtered indexes — subprocessor_code is unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_subp_code' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE UNIQUE INDEX UQ_subp_code ON privacy.subprocessor (organisation_id, subprocessor_code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_subp_external' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE UNIQUE INDEX UQ_subp_external ON privacy.subprocessor (external_id) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_subp_country' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE INDEX IX_subp_country ON privacy.subprocessor (organisation_id, country_of_processing) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_subp_lifecycle' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE INDEX IX_subp_lifecycle ON privacy.subprocessor (organisation_id, lifecycle_stage) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_subp_review_due' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE INDEX IX_subp_review_due ON privacy.subprocessor (organisation_id, next_review_due_at) WHERE is_deleted = 0 AND next_review_due_at IS NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_subp_vendor' AND object_id = OBJECT_ID(N'privacy.subprocessor'))
    CREATE INDEX IX_subp_vendor ON privacy.subprocessor (organisation_id, vendor_id) WHERE is_deleted = 0 AND vendor_id IS NOT NULL;
GO

-- ─── 2. Sub-processor event ledger ─────────────────────────────────
IF OBJECT_ID(N'privacy.subprocessor_event', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.subprocessor_event (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        subprocessor_id               BIGINT        NOT NULL,

        -- Event type:
        --   ADDED                  — first record creation
        --   MATERIAL_CHANGE        — scope/purpose/safeguard changed
        --   SUSPENDED              — temporarily blocked
        --   RESUMED                — un-suspended
        --   RETIRED                — terminal
        --   CROSS_BORDER_CHANGE    — safeguard type changed
        --   REVIEW_COMPLETED       — 12-month review done
        --   INCIDENT_REPORTED      — privacy incident involving this sub
        event_type                    VARCHAR(40)   NOT NULL,

        event_date                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        recorded_by_user_id           BIGINT        NULL,

        description_en                NVARCHAR(2000) NULL,
        description_ar                NVARCHAR(2000) NULL,

        -- PDPL Article 30: when a sub-processor changes materially the
        -- controller must notify data subjects. These two fields
        -- track that obligation.
        data_subjects_notified        BIT           NOT NULL DEFAULT 0,
        data_subjects_notification_date DATE        NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_subp_evt_sub FOREIGN KEY (subprocessor_id)
            REFERENCES privacy.subprocessor(id),
        CONSTRAINT CK_subp_evt_type CHECK (event_type IN
            (N'ADDED', N'MATERIAL_CHANGE', N'SUSPENDED', N'RESUMED', N'RETIRED',
             N'CROSS_BORDER_CHANGE', N'REVIEW_COMPLETED', N'INCIDENT_REPORTED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_subp_evt_external' AND object_id = OBJECT_ID(N'privacy.subprocessor_event'))
    CREATE UNIQUE INDEX UQ_subp_evt_external ON privacy.subprocessor_event (external_id) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_subp_evt_sub' AND object_id = OBJECT_ID(N'privacy.subprocessor_event'))
    CREATE INDEX IX_subp_evt_sub ON privacy.subprocessor_event (organisation_id, subprocessor_id, event_date DESC) WHERE is_deleted = 0;
GO

-- ─── 3. Seed 8 sub-processors for org=1 (TSS Bank) ─────────────────
-- Saudi-native bilingual EN/AR. Every row is realistic for a tier-1
-- Saudi bank in 2026. APPROVED + reviewed within the last quarter so
-- the register loads with a clean baseline. Cross-border safeguards
-- match each vendor's actual posture:
--   - KSA_RESIDENT: Azure KSA region, Salesforce Hyperforce KSA,
--                   Tawakkalna, SIMAH (all process inside the Kingdom).
--   - SCC_PLUS_TIA: M365 (EU+ME), Adobe Sign (US), Twilio (US),
--                   Hubspot (US).

-- SUBP-2026-001 — Microsoft Azure (KSA region)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-001' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-001',
     N'Microsoft Saudi Arabia (Azure KSA region)',
     N'مايكروسوفت العربية السعودية (منطقة أزور بالمملكة)',
     'SA', N'Riyadh',
     N'["IDENTITY","CONTACT","FINANCIAL","BEHAVIOURAL"]',
     N'["CUSTOMERS","EMPLOYEES"]',
     N'Primary cloud infrastructure for the core banking workload — compute, storage, networking. All workloads pinned to the Riyadh region; no data exits the Kingdom.',
     N'بنية تحتية سحابية أساسية لأعباء الأنظمة المصرفية الجوهرية — حوسبة وتخزين وشبكات. تثبيت كافة الأعباء داخل منطقة الرياض؛ لا تخرج البيانات من المملكة.',
     N'CONTRACT', N'GENERAL', N'DPA-MS-2024-AZURE-KSA',
     N'KSA_RESIDENT',
     N'VERY_HIGH',
     1, '2024-03-01', '2027-02-28',
     N'APPROVED', DATEADD(DAY, -85, SYSUTCDATETIME()),
     DATEADD(DAY, -85, SYSUTCDATETIME()), DATEADD(DAY, 280, SYSUTCDATETIME()));
GO

-- SUBP-2026-002 — Microsoft 365 (EU+ME)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-002' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type, cross_border_safeguard_doc_ref,
     tia_reference, tia_completed_at,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-002',
     N'Microsoft Ireland Operations Ltd. (M365)',
     N'مايكروسوفت أيرلندا (مايكروسوفت 365)',
     'IE', N'Dublin / Frankfurt',
     N'["IDENTITY","CONTACT","EMPLOYMENT"]',
     N'["EMPLOYEES"]',
     N'Corporate productivity — Outlook, Teams, SharePoint, OneDrive. Employee data only; no customer banking data is permitted to leave the Kingdom via M365.',
     N'إنتاجية مؤسّسية — أوتلوك وتيمز وشيربوينت ووَن درايف. بيانات الموظفين فقط؛ لا يُسمح بخروج بيانات العملاء المصرفية من المملكة عبر M365.',
     N'CONTRACT', N'GENERAL', N'DPA-MS-2024-M365',
     N'SCC_PLUS_TIA', N'SCC-MS-M365-2024-v1',
     N'TIA-2024-M365-EMP', '2024-09-15',
     N'HIGH',
     1, '2024-09-20', '2027-09-19',
     N'APPROVED', DATEADD(DAY, -60, SYSUTCDATETIME()),
     DATEADD(DAY, -60, SYSUTCDATETIME()), DATEADD(DAY, 305, SYSUTCDATETIME()));
GO

-- SUBP-2026-003 — Adobe Sign (US)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-003' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type, cross_border_safeguard_doc_ref,
     tia_reference, tia_completed_at,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-003',
     N'Adobe Inc. (Adobe Sign / Acrobat Sign)',
     N'أدوبي (أدوبي ساين)',
     'US', N'United States (multi-region)',
     N'["IDENTITY","CONTACT","FINANCIAL"]',
     N'["CUSTOMERS","EMPLOYEES"]',
     N'Electronic signature on retail account-opening packs, SME credit agreements, and HR documents. Signatory identity verification only — substantive document content is hashed.',
     N'التوقيع الإلكتروني على حِزَم فتح الحسابات للأفراد وعقود ائتمان المنشآت الصغيرة والوثائق البشرية. يقتصر النقل على التحقّق من هوية الموقّع؛ المحتوى الجوهري للوثيقة يُمَرَّر مُجزَّأً.',
     N'CONTRACT', N'SPECIFIC', N'DPA-ADOBE-2024-SIGN',
     N'SCC_PLUS_TIA', N'SCC-ADOBE-2024-v2',
     N'TIA-2024-ADOBE-SIGN', '2024-11-10',
     N'MEDIUM',
     1, '2024-11-15', '2027-11-14',
     N'APPROVED', DATEADD(DAY, -40, SYSUTCDATETIME()),
     DATEADD(DAY, -40, SYSUTCDATETIME()), DATEADD(DAY, 325, SYSUTCDATETIME()));
GO

-- SUBP-2026-004 — Salesforce CRM (Hyperforce KSA)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-004' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-004',
     N'Salesforce.com (Hyperforce KSA)',
     N'سيلز فورس (هايبر فورس بالمملكة)',
     'SA', N'Riyadh',
     N'["IDENTITY","CONTACT","BEHAVIOURAL","FINANCIAL"]',
     N'["CUSTOMERS","PROSPECTS"]',
     N'Customer relationship management — leads, opportunities, retail and SME relationship-manager case-notes. Hyperforce KSA pin means all data is processed inside the Kingdom.',
     N'إدارة علاقات العملاء — العملاء المحتملون والفرص ومدوّنات مديري العلاقات للأفراد والمنشآت الصغيرة. تثبيت هايبر فورس بالمملكة يعني معالجة كافة البيانات داخل المملكة.',
     N'CONTRACT', N'GENERAL', N'DPA-SF-2024-HYPERFORCE',
     N'KSA_RESIDENT',
     N'HIGH',
     1, '2024-06-01', '2027-05-31',
     N'APPROVED', DATEADD(DAY, -120, SYSUTCDATETIME()),
     DATEADD(DAY, -120, SYSUTCDATETIME()), DATEADD(DAY, 245, SYSUTCDATETIME()));
GO

-- SUBP-2026-005 — Tawakkalna integration (KSA)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-005' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-005',
     N'Saudi Data and AI Authority — Tawakkalna platform',
     N'الهيئة السعودية للبيانات والذكاء الاصطناعي — منصّة توكلنا',
     'SA', N'Riyadh',
     N'["IDENTITY","CONTACT"]',
     N'["CUSTOMERS"]',
     N'National-identity verification and Nafath authentication during digital onboarding. Receives the customer''s consent before each identity assertion. Mandated by SDAIA digital-government integration directive.',
     N'التحقّق من الهوية الوطنية ومصادقة نفاذ خلال الإدخال الرقمي للعميل. يُؤخذ موافقة العميل قبل كل إثبات هوية. مفروض وفق توجيهات سدايا للتكامل الرقمي الحكومي.',
     N'LEGAL_OBLIGATION', N'SPECIFIC', N'AUTH-SDAIA-TAW-2024',
     N'KSA_RESIDENT',
     N'VERY_HIGH',
     1, '2024-01-15', '2029-01-14',
     N'APPROVED', DATEADD(DAY, -200, SYSUTCDATETIME()),
     DATEADD(DAY, -200, SYSUTCDATETIME()), DATEADD(DAY, 165, SYSUTCDATETIME()));
GO

-- SUBP-2026-006 — SIMAH credit-bureau (KSA)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-006' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-006',
     N'Saudi Credit Bureau (SIMAH)',
     N'الشركة السعودية للمعلومات الائتمانية (سمة)',
     'SA', N'Riyadh',
     N'["IDENTITY","FINANCIAL","CREDIT_HISTORY"]',
     N'["CUSTOMERS","GUARANTORS"]',
     N'Credit bureau enquiries on retail and SME borrowers, plus credit-history reporting back to SIMAH. Mandated by SAMA Credit Information Companies Regulation; lawful basis is regulatory.',
     N'استعلامات مكتب الائتمان عن المقترضين من الأفراد والمنشآت الصغيرة، وإرسال السجلّ الائتماني إلى سمة. مفروض وفق نظام شركات المعلومات الائتمانية الصادر عن البنك المركزي السعودي؛ الأساس النظامي رقابي.',
     N'REGULATORY', N'SPECIFIC', N'AUTH-SAMA-SIMAH-2023',
     N'KSA_RESIDENT',
     N'VERY_HIGH',
     1, '2023-04-10', '2028-04-09',
     N'APPROVED', DATEADD(DAY, -300, SYSUTCDATETIME()),
     DATEADD(DAY, -300, SYSUTCDATETIME()), DATEADD(DAY, 65, SYSUTCDATETIME()));
GO

-- SUBP-2026-007 — Twilio (US, SMS)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-007' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type, cross_border_safeguard_doc_ref,
     tia_reference, tia_completed_at,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-007',
     N'Twilio Inc. (SMS delivery)',
     N'تويليو (إرسال الرسائل النصية)',
     'US', N'United States',
     N'["CONTACT"]',
     N'["CUSTOMERS"]',
     N'Outbound SMS delivery — transactional OTPs, account-activity alerts, marketing campaigns. Only the mobile number and message body cross the border; no other personal data attached.',
     N'إرسال الرسائل النصية الصادرة — رموز التحقق العابرة وإشعارات حركة الحساب والحملات التسويقية. يَعبر الحدود رقم الجوّال ونص الرسالة فقط؛ لا تُرفق بيانات شخصية أخرى.',
     N'CONTRACT', N'GENERAL', N'DPA-TWILIO-2024',
     N'SCC_PLUS_TIA', N'SCC-TWILIO-2024-v1',
     N'TIA-2024-TWILIO-SMS', '2024-08-20',
     N'HIGH',
     1, '2024-08-25', '2027-08-24',
     N'APPROVED', DATEADD(DAY, -100, SYSUTCDATETIME()),
     DATEADD(DAY, -100, SYSUTCDATETIME()), DATEADD(DAY, 265, SYSUTCDATETIME()));
GO

-- SUBP-2026-008 — Hubspot (US, marketing)
IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor WHERE organisation_id = 1 AND subprocessor_code = N'SUBP-2026-008' AND is_deleted = 0)
INSERT INTO privacy.subprocessor
    (organisation_id, subprocessor_code, sub_processor_legal_name, sub_processor_legal_name_ar,
     country_of_processing, region_of_processing,
     data_categories_json, data_subject_categories_json,
     processing_purpose_en, processing_purpose_ar,
     lawful_basis, authorisation_type, authorisation_basis_doc_ref,
     cross_border_safeguard_type, cross_border_safeguard_doc_ref,
     tia_reference, tia_completed_at,
     data_volume_band,
     has_dpa_in_place, dpa_signed_date, dpa_expiry_date,
     lifecycle_stage, dpo_approved_at,
     last_reviewed_at, next_review_due_at)
VALUES
    (1, N'SUBP-2026-008',
     N'HubSpot Inc. (marketing automation)',
     N'هَب سبوت (أتمتة التسويق)',
     'US', N'United States',
     N'["IDENTITY","CONTACT","BEHAVIOURAL"]',
     N'["PROSPECTS"]',
     N'Marketing-automation platform for outbound campaigns to prospects who have consented to marketing communications. Customer financial data is never written to HubSpot — only contact details and campaign-engagement signals.',
     N'منصّة أتمتة التسويق للحملات الصادرة للعملاء المحتملين الذين منحوا موافقتهم على الاتصالات التسويقية. لا تُكتب البيانات المالية للعملاء في هَب سبوت؛ يقتصر النقل على بيانات الاتصال وإشارات التفاعل مع الحملات.',
     N'CONSENT', N'GENERAL', N'DPA-HUBSPOT-2024',
     N'SCC_PLUS_TIA', N'SCC-HUBSPOT-2024-v1',
     N'TIA-2024-HUBSPOT-MKT', '2024-07-05',
     N'MEDIUM',
     1, '2024-07-10', '2027-07-09',
     N'APPROVED', DATEADD(DAY, -150, SYSUTCDATETIME()),
     DATEADD(DAY, -150, SYSUTCDATETIME()), DATEADD(DAY, 215, SYSUTCDATETIME()));
GO

-- ─── 4. Seed ADDED events for the 8 sub-processors ─────────────────
-- Append-only ledger entry per row — gives the DPO a starting narrative
-- and demonstrates the event-ledger pattern at first load.

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-001'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Microsoft Saudi Arabia, Azure KSA region. Data residency confirmed inside the Kingdom.',
       N'إدخال المعالج الفرعي — مايكروسوفت العربية السعودية، منطقة أزور بالمملكة. تأكيد إقامة البيانات داخل المملكة.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-001';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-002'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Microsoft Ireland (M365). SCC + TIA completed; cross-border safeguard documented for employee productivity workload.',
       N'إدخال المعالج الفرعي — مايكروسوفت أيرلندا (مايكروسوفت 365). إكمال البنود التعاقدية القياسية وتقييم أثر النقل؛ توثيق ضمانة النقل عبر الحدود لعبء إنتاجية الموظفين.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-002';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-003'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Adobe Sign for e-signature on account-opening and HR packs.',
       N'إدخال المعالج الفرعي — أدوبي ساين للتوقيع الإلكتروني على حِزَم فتح الحسابات والوثائق البشرية.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-003';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-004'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Salesforce Hyperforce KSA pin for CRM. Data residency confirmed inside the Kingdom.',
       N'إدخال المعالج الفرعي — تثبيت سيلز فورس هايبر فورس بالمملكة لإدارة علاقات العملاء. تأكيد إقامة البيانات داخل المملكة.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-004';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-005'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Tawakkalna / Nafath integration for national-identity verification during digital onboarding.',
       N'إدخال المعالج الفرعي — تكامل توكلنا / نفاذ للتحقق من الهوية الوطنية خلال الإدخال الرقمي.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-005';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-006'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — SIMAH credit-bureau enquiries under SAMA Credit Information Companies Regulation.',
       N'إدخال المعالج الفرعي — استعلامات مكتب الائتمان لدى سمة وفق نظام شركات المعلومات الائتمانية الصادر عن البنك المركزي السعودي.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-006';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-007'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — Twilio for transactional and marketing SMS. SCC + TIA documented.',
       N'إدخال المعالج الفرعي — تويليو لإرسال الرسائل النصية الإجرائية والتسويقية. توثيق البنود التعاقدية القياسية وتقييم أثر النقل.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-007';
GO

IF NOT EXISTS (SELECT 1 FROM privacy.subprocessor_event e
               INNER JOIN privacy.subprocessor s ON s.id = e.subprocessor_id
               WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-008'
                 AND e.event_type = N'ADDED' AND e.is_deleted = 0)
INSERT INTO privacy.subprocessor_event
    (organisation_id, subprocessor_id, event_type, event_date,
     description_en, description_ar)
SELECT s.organisation_id, s.id, N'ADDED', s.created_at,
       N'Sub-processor onboarded — HubSpot for consent-based marketing automation to prospects.',
       N'إدخال المعالج الفرعي — هَب سبوت لأتمتة الحملات التسويقية للعملاء المحتملين بناءً على موافقاتهم.'
FROM privacy.subprocessor s
WHERE s.organisation_id = 1 AND s.subprocessor_code = N'SUBP-2026-008';
GO

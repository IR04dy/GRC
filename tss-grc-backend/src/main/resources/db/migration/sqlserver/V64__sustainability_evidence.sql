-- V64 — Sustainability-scoped Evidence Repository (slice 10)
--
-- The text `evidence_ref` columns scattered across V55-V63 (climate
-- risks, GHG records, CMA disclosures, TCFD items, SGI commitments,
-- material topics, ESG metrics, corrective actions) finally have a
-- real backing entity here. Each ESG record can point at a row in
-- this table via its evidence_ref column (string match on
-- evidence.title or evidence_code), AND/OR via an explicit pointer
-- once individual modules add foreign-key columns.
--
-- Activates Rule 9 — when an evidence row expires (valid_to / expiry
-- date passes), the service can sweep linked disclosures and mark
-- them EVIDENCE_REQUIRED. The expiry job is scaffolded; the actual
-- scheduler hookup is left for a follow-up.
--
-- Status set:
--   PENDING_REVIEW  — uploaded, waiting for review
--   APPROVED        — reviewer signed off
--   REJECTED        — sent back with comments
--   EXPIRED         — valid_to date has passed
--   SUPERSEDED      — replaced by a newer version

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'evidence' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.evidence (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        evidence_code            NVARCHAR(64)  NULL,        -- e.g. "EV-2025-GHG-001"

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,
        description_en           NVARCHAR(MAX) NULL,
        description_ar           NVARCHAR(MAX) NULL,

        -- Free-text discriminators — UI groups by these.
        -- GHG | SGI | CMA | TCFD | MATERIALITY | METRIC |
        -- CLIMATE_RISK | CORRECTIVE_ACTION | ANNEX | OTHER
        module_area              NVARCHAR(40)  NULL,
        -- POLICY | CHARTER | REPORT | CERTIFICATE | SPREADSHEET |
        -- INVOICE | EMAIL | MEETING_MINUTES | EXTERNAL_VERIFICATION |
        -- DATA_EXPORT | OTHER
        evidence_type            NVARCHAR(40)  NULL,

        -- The actual artefact reference. Until a real file-upload
        -- pipeline lands, this is a path / URL / system reference.
        file_url                 NVARCHAR(500) NULL,
        file_name                NVARCHAR(255) NULL,
        version                  NVARCHAR(20)  NULL DEFAULT N'v1.0',

        reporting_year           SMALLINT      NULL,

        -- Validity window — Rule 9 fires when valid_to < today.
        valid_from               DATE          NULL,
        valid_to                 DATE          NULL,
        expiry_date              DATE          NULL,        -- absolute drop-dead

        -- INTERNAL | RESTRICTED | CONFIDENTIAL | PUBLIC
        confidentiality          NVARCHAR(20)  NOT NULL DEFAULT N'INTERNAL',
        -- VERIFIED | UNVERIFIED | EXTERNAL_VERIFIED
        verification_status      NVARCHAR(30)  NULL,
        verification_provider    NVARCHAR(120) NULL,
        verification_date        DATE          NULL,

        -- PENDING_REVIEW | APPROVED | REJECTED | EXPIRED | SUPERSEDED
        approval_status          NVARCHAR(30)  NOT NULL DEFAULT N'PENDING_REVIEW',
        rejection_reason         NVARCHAR(MAX) NULL,

        digital_signature_status NVARCHAR(20)  NULL,        -- SIGNED | UNSIGNED | INVALID

        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,
        reviewer_name            NVARCHAR(255) NULL,
        reviewed_at              DATETIME2     NULL,
        approved_by              NVARCHAR(255) NULL,
        approved_at              DATETIME2     NULL,

        -- Versioning — points at the prior version when this row
        -- supersedes one.
        supersedes_id            BIGINT        NULL,

        notes                    NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_ev_approval CHECK (approval_status IN
            (N'PENDING_REVIEW', N'APPROVED', N'REJECTED', N'EXPIRED', N'SUPERSEDED')),
        CONSTRAINT ck_ev_conf     CHECK (confidentiality IN
            (N'INTERNAL', N'RESTRICTED', N'CONFIDENTIAL', N'PUBLIC')),
        CONSTRAINT ck_ev_verif    CHECK (verification_status IS NULL OR verification_status IN
            (N'VERIFIED', N'UNVERIFIED', N'EXTERNAL_VERIFIED'))
    );

    CREATE INDEX ix_ev_org_module       ON sust.evidence(organisation_id, module_area);
    CREATE INDEX ix_ev_org_approval     ON sust.evidence(organisation_id, approval_status);
    CREATE INDEX ix_ev_org_year         ON sust.evidence(organisation_id, reporting_year);
    CREATE INDEX ix_ev_org_expiry       ON sust.evidence(organisation_id, valid_to);
END
GO

-- ─── Seed — 10 evidence rows linked to existing entities ────────────
-- Mirrors the evidence_ref strings already in V55-V63 seed data so
-- the title-text join from existing rows resolves to real evidence
-- entries.

IF NOT EXISTS (SELECT 1 FROM sust.evidence WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.evidence (
        organisation_id, evidence_code, title_en, title_ar,
        module_area, evidence_type,
        file_name, version, reporting_year,
        valid_from, valid_to,
        confidentiality, verification_status, verification_provider, verification_date,
        approval_status, owner_name, owner_email, reviewer_name, approved_by, approved_at
    )
    VALUES
    -- GHG inventory evidence (verified by external provider)
    (1, N'EV-2025-GHG-001', N'GHG Inventory 2025 — verification report (Bureau Veritas)',
     N'تقرير التحقّق من سجل انبعاثات الغازات الدفيئة 2025 (بيرو فيريتاس)',
     N'GHG', N'EXTERNAL_VERIFICATION',
     N'BV-Verif-2025-Scope1-2.pdf', N'v1.0', 2025,
     '2026-02-12', '2027-02-11',
     N'CONFIDENTIAL', N'EXTERNAL_VERIFIED', N'Bureau Veritas KSA', '2026-02-12',
     N'APPROVED', N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)', '2026-04-25'),

    (1, N'EV-2025-GHG-002', N'Scope 3 — 67% coverage analysis',
     N'تحليل تغطية النطاق 3 — 67%',
     N'GHG', N'SPREADSHEET',
     N'Scope3-coverage-analysis.xlsx', N'v2.1', 2025,
     '2026-03-01', '2027-03-01',
     N'INTERNAL', N'UNVERIFIED', NULL, NULL,
     N'PENDING_REVIEW', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     NULL, NULL, NULL),

    -- CMA disclosure evidence
    (1, N'EV-2025-CMA-001', N'Annual Report 2025 §3.4 — Board oversight section',
     N'التقرير السنوي 2025 الفقرة 3.4 — قسم إشراف المجلس',
     N'CMA', N'REPORT',
     N'AnnualReport-2025.pdf', N'v1.0', 2025,
     '2026-04-01', '2027-04-01',
     N'PUBLIC', N'VERIFIED', NULL, NULL,
     N'APPROVED', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Khalid Al-Otaibi (CTO)', N'Khalid Al-Otaibi (CTO)', '2026-04-22'),

    (1, N'EV-2025-CMA-002', N'Sustainability Committee Charter v3.0',
     N'ميثاق لجنة الاستدامة v3.0',
     N'CMA', N'CHARTER',
     N'Committee-Charter-v3.0.pdf', N'v3.0', 2025,
     '2026-01-15', '2028-01-15',
     N'INTERNAL', N'VERIFIED', NULL, NULL,
     N'APPROVED', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Khalid Al-Otaibi (CTO)', N'Khalid Al-Otaibi (CTO)', '2026-04-22'),

    -- TCFD evidence
    (1, N'EV-2025-TCFD-001', N'TCFD Scenario Analysis FY2026 (NGFS Net-Zero 2050)',
     N'تحليل سيناريوهات TCFD للسنة 2026 (NGFS صافي صفري 2050)',
     N'TCFD', N'REPORT',
     N'Scenario-Analysis-FY2026.pdf', N'v1.0', 2025,
     '2026-03-15', '2027-03-15',
     N'CONFIDENTIAL', N'UNVERIFIED', NULL, NULL,
     N'PENDING_REVIEW', N'Lina Al-Harbi (CRO)', N'l.alharbi@tss.sa',
     NULL, NULL, NULL),

    -- SGI evidence
    (1, N'EV-2025-SGI-001', N'Sudair PV Plant — PPA agreement (signed)',
     N'محطة سدير للطاقة الشمسية — اتفاقية شراء الطاقة (موقّعة)',
     N'SGI', N'CHARTER',
     N'Sudair-PPA-2026.pdf', N'v1.0', 2025,
     '2026-01-15', '2056-01-15',
     N'RESTRICTED', N'VERIFIED', NULL, NULL,
     N'APPROVED', N'Lina Al-Harbi (Head of Energy)', N'l.alharbi@tss.sa',
     N'Sara Al-Zahrani (CSO)', N'Sara Al-Zahrani (CSO)', '2026-02-10'),

    -- ESG Metrics evidence
    (1, N'EV-2025-MET-001', N'HRSD 2025 Q4 — Nitaqat tier confirmation',
     N'HRSD الربع الرابع 2025 — تأكيد فئة نطاقات',
     N'METRIC', N'CERTIFICATE',
     N'HRSD-Nitaqat-Q4-2025.pdf', N'v1.0', 2025,
     '2026-02-01', '2026-04-30',
     N'INTERNAL', N'EXTERNAL_VERIFIED', N'Saudi HRSD', '2026-02-01',
     N'EXPIRED', N'Mohammed Al-Sulaiman (HR Director)', N'm.alsulaiman@tss.sa',
     N'Khalid Al-Otaibi (CTO)', N'Khalid Al-Otaibi (CTO)', '2026-02-05'),

    -- Climate risk evidence
    (1, N'EV-2025-CLI-001', N'Jeddah-DC flood-risk modelling (NCM 100-yr envelope)',
     N'نمذجة مخاطر الفيضانات لمركز بيانات جدة (مدى 100 عام للأرصاد)',
     N'CLIMATE_RISK', N'REPORT',
     N'Jeddah-DC-flood-modelling.pdf', N'v1.2', 2025,
     '2026-03-08', '2027-03-08',
     N'CONFIDENTIAL', N'EXTERNAL_VERIFIED', N'NCM (National Center for Meteorology)', '2026-03-08',
     N'APPROVED', N'Khalid Al-Otaibi (CTO)', N'k.alotaibi@tss.sa',
     N'Sara Al-Zahrani (CSO)', N'Khalid Al-Otaibi (CTO)', '2026-04-10'),

    -- Materiality evidence
    (1, N'EV-2025-MAT-001', N'Materiality Matrix 2025 — stakeholder survey results',
     N'مصفوفة الجوهرية 2025 — نتائج استبيان أصحاب المصلحة',
     N'MATERIALITY', N'SPREADSHEET',
     N'Materiality-Matrix-2025.xlsx', N'v1.0', 2025,
     '2026-04-15', '2027-04-15',
     N'INTERNAL', N'VERIFIED', NULL, NULL,
     N'APPROVED', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Khalid Al-Otaibi (CTO)', N'Khalid Al-Otaibi (CTO)', '2026-04-15'),

    -- Corrective action evidence
    (1, N'EV-2025-CAPA-001', N'Procurement spend extract — Q1 2026 rebind',
     N'مستخرج إنفاق المشتريات — إعادة ربط الربع الأول 2026',
     N'CORRECTIVE_ACTION', N'SPREADSHEET',
     N'Procurement-spend-2026Q1-rebind.xlsx', N'v1.0', 2025,
     '2026-04-22', '2027-04-22',
     N'INTERNAL', N'VERIFIED', NULL, NULL,
     N'APPROVED', N'Sara Al-Zahrani (CSO)', N's.alzahrani@tss.sa',
     N'Khalid Al-Otaibi (CTO)', N'Khalid Al-Otaibi (CTO)', '2026-04-29');
END
GO

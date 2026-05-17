-- V72 — NDI Evidence Repository (slice 7)
--
-- Stores attested evidence artefacts referenced by maturity and
-- compliance assessments. Each row holds metadata about a file or
-- attestation; the actual binary lives in the platform's evidence
-- module (the existing `evidence` schema) once uploaded — this NDI
-- table keeps a denormalised pointer + classification + expiry so the
-- NDI submission package can be assembled without round-tripping.
--
-- Rule 6 (expiry) is computed by the service: when expiry_date < today
-- the status auto-flips to EXPIRED and dependent compliance rows are
-- flagged via the compliance evidence_status column.
--
-- Status set:
--   ACTIVE       in date, accepted
--   EXPIRED      past expiry_date — service-set
--   SUPERSEDED   replaced by a newer version (link via supersedes_id)
--   REJECTED     reviewer rejected; needs replacement
--   PENDING      uploaded but not yet reviewed

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'evidence_item' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.evidence_item (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        cycle_id            BIGINT        NULL,    -- nullable: evidence may span cycles
        evidence_code       NVARCHAR(40)  NULL,

        title_en            NVARCHAR(255) NOT NULL,
        title_ar            NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        evidence_type       NVARCHAR(30)  NOT NULL,    -- POLICY|PROCEDURE|REPORT|SCREENSHOT|ATTESTATION|CERTIFICATE|AUDIT_LOG|DATASET|OTHER
        classification      NVARCHAR(20)  NOT NULL DEFAULT N'INTERNAL',  -- PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED

        file_ref            NVARCHAR(500) NULL,    -- URL / document-store path / external link
        mime_type           NVARCHAR(120) NULL,
        file_size_bytes     BIGINT        NULL,
        source_system       NVARCHAR(255) NULL,

        owner_name          NVARCHAR(255) NULL,
        owner_email         NVARCHAR(255) NULL,
        reviewer_name       NVARCHAR(255) NULL,

        effective_date      DATE          NULL,
        expiry_date         DATE          NULL,
        reviewed_at         DATE          NULL,

        status              NVARCHAR(20)  NOT NULL DEFAULT N'PENDING',
        supersedes_id       BIGINT        NULL,
        rejection_reason    NVARCHAR(MAX) NULL,

        tags                NVARCHAR(500) NULL,    -- pipe-separated
        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_ev_type CHECK (evidence_type IN
            (N'POLICY', N'PROCEDURE', N'REPORT', N'SCREENSHOT', N'ATTESTATION',
             N'CERTIFICATE', N'AUDIT_LOG', N'DATASET', N'OTHER')),
        CONSTRAINT ck_ndi_ev_class CHECK (classification IN
            (N'PUBLIC', N'INTERNAL', N'CONFIDENTIAL', N'RESTRICTED')),
        CONSTRAINT ck_ndi_ev_status CHECK (status IN
            (N'PENDING', N'ACTIVE', N'EXPIRED', N'SUPERSEDED', N'REJECTED'))
    );

    CREATE INDEX ix_ndi_ev_org_cycle  ON ndi.evidence_item(organisation_id, cycle_id);
    CREATE INDEX ix_ndi_ev_type       ON ndi.evidence_item(organisation_id, evidence_type);
    CREATE INDEX ix_ndi_ev_status     ON ndi.evidence_item(organisation_id, status);
    CREATE INDEX ix_ndi_ev_expiry     ON ndi.evidence_item(expiry_date);
END
GO

-- ─── Seed — illustrative evidence items for the demo cycle ─────────

IF NOT EXISTS (SELECT 1 FROM ndi.evidence_item WHERE organisation_id = 1)
BEGIN
    INSERT INTO ndi.evidence_item (
        organisation_id, cycle_id, evidence_code,
        title_en, title_ar, description_en, description_ar,
        evidence_type, classification, file_ref, mime_type,
        owner_name, owner_email,
        effective_date, expiry_date, status, tags
    ) VALUES
    (1, 1, N'EV-001',
     N'Data Governance Charter (signed)',
     N'ميثاق حوكمة البيانات (موقَّع)',
     N'Approved data-governance charter establishing roles, decision rights, and the data council operating model.',
     N'ميثاق حوكمة البيانات المعتمد الذي يُحدّد الأدوار وصلاحيات اتخاذ القرار والنموذج التشغيلي لمجلس البيانات.',
     N'POLICY', N'INTERNAL', N'/docs/policies/data-governance-charter-v3.pdf', N'application/pdf',
     N'Chief Data Officer', N'cdo@example.gov.sa',
     '2025-01-15', '2027-01-15', N'ACTIVE', N'governance|charter|signed'),

    (1, 1, N'EV-002',
     N'PDPL ROPA register export (Q1 2026)',
     N'تصدير سجل أنشطة المعالجة وفق نظام حماية البيانات الشخصية (الربع الأول 2026)',
     N'Quarterly export of the personal-data processing-activities register required by PDPL Article 18.',
     N'تصدير ربع سنوي لسجل أنشطة معالجة البيانات الشخصية المطلوب بموجب المادة 18 من نظام حماية البيانات.',
     N'REPORT', N'CONFIDENTIAL', N'/docs/pdpl/ropa-2026-Q1.xlsx', N'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
     N'Data Protection Officer', N'dpo@example.gov.sa',
     '2026-03-31', '2026-06-30', N'ACTIVE', N'pdpl|ropa|quarterly'),

    (1, 1, N'EV-003',
     N'Data Catalog screenshot — coverage dashboard',
     N'لقطة شاشة لفهرس البيانات — لوحة التغطية',
     N'Screenshot of the central data catalog coverage dashboard showing 65% catalogued assets as of Q1 2026.',
     N'لقطة شاشة للوحة تغطية الفهرس المركزي تُظهر 65% من الأصول المسجَّلة في الربع الأول 2026.',
     N'SCREENSHOT', N'INTERNAL', N'/docs/screenshots/catalog-coverage-2026-Q1.png', N'image/png',
     N'Data Catalog Owner', N'catalog@example.gov.sa',
     '2026-03-31', '2026-06-30', N'ACTIVE', N'catalog|coverage|screenshot'),

    (1, 1, N'EV-004',
     N'ISO 27001 certificate — 2025',
     N'شهادة آيزو 27001 — 2025',
     N'Current ISO 27001 certificate covering the data-platform scope.',
     N'شهادة آيزو 27001 السارية التي تغطي نطاق منصة البيانات.',
     N'CERTIFICATE', N'PUBLIC', N'/docs/certs/iso27001-2025.pdf', N'application/pdf',
     N'CISO', N'ciso@example.gov.sa',
     '2025-06-01', '2026-05-31', N'ACTIVE', N'iso|security|certificate'),

    (1, 1, N'EV-005',
     N'Data classification policy v2 (superseded)',
     N'سياسة تصنيف البيانات النسخة 2 (مستبدَلة)',
     N'Earlier version of the data-classification policy — kept for audit trail. Replaced by EV-006 in Jan 2026.',
     N'النسخة السابقة من سياسة تصنيف البيانات — مُحتفَظ بها لمسار التدقيق. استُبدلت بـ EV-006 في يناير 2026.',
     N'POLICY', N'INTERNAL', N'/docs/policies/classification-v2.pdf', N'application/pdf',
     N'Data Classification Lead', N'classification@example.gov.sa',
     '2024-03-01', '2026-01-15', N'SUPERSEDED', N'classification|policy|archived'),

    (1, 1, N'EV-006',
     N'Data classification policy v3 (current)',
     N'سياسة تصنيف البيانات النسخة 3 (سارية)',
     N'Current data-classification policy aligned with the national classification framework.',
     N'سياسة تصنيف البيانات السارية المُواءَمة مع الإطار الوطني للتصنيف.',
     N'POLICY', N'INTERNAL', N'/docs/policies/classification-v3.pdf', N'application/pdf',
     N'Data Classification Lead', N'classification@example.gov.sa',
     '2026-01-15', '2028-01-15', N'ACTIVE', N'classification|policy|current'),

    (1, 1, N'EV-007',
     N'Annual data governance awareness training — attendance log',
     N'سجل حضور التدريب السنوي للتوعية بحوكمة البيانات',
     N'Attendance log for the 2025 annual data-governance awareness programme — 88% completion.',
     N'سجل حضور برنامج التوعية السنوي بحوكمة البيانات لعام 2025 — نسبة الإكمال 88%.',
     N'ATTESTATION', N'INTERNAL', N'/docs/training/awareness-2025-attendance.xlsx', N'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
     N'HR / Training Lead', N'training@example.gov.sa',
     '2025-12-31', '2026-12-31', N'ACTIVE', N'training|literacy|attestation'),

    (1, 1, N'EV-008',
     N'Open data publication audit log (Q1 2026)',
     N'سجل تدقيق نشر البيانات المفتوحة (الربع الأول 2026)',
     N'Audit log of dataset publication events on the national open-data portal during Q1 2026.',
     N'سجل تدقيق أحداث نشر مجموعات البيانات على البوابة الوطنية للبيانات المفتوحة خلال الربع الأول 2026.',
     N'AUDIT_LOG', N'INTERNAL', N'/docs/audit/opendata-2026-Q1.log', N'text/plain',
     N'Open Data Steward', N'opendata@example.gov.sa',
     '2026-03-31', '2027-03-31', N'ACTIVE', N'opendata|audit|quarterly');

    -- Link EV-006 as superseding EV-005
    UPDATE ndi.evidence_item SET supersedes_id = (SELECT id FROM ndi.evidence_item WHERE evidence_code = N'EV-005')
        WHERE evidence_code = N'EV-006';
END
GO

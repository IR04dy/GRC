-- V79 — Policy Management module
--
-- Three tables under a new `policy` schema:
--
--   policy.documents       The policy library — one row per policy with a
--                          versioned lifecycle (DRAFT → REVIEW → APPROVED →
--                          PUBLISHED → EXPIRED → ARCHIVED). Stores bilingual
--                          title + summary, owner, applicability scope,
--                          review cadence, and effective/expiry dates.
--
--   policy.exceptions      Time-bound deviations from a published policy —
--                          REQUESTED → UNDER_REVIEW → APPROVED|REJECTED →
--                          EXPIRED. Each carries justification, compensating
--                          controls, and an expiry date.
--
--   policy.attestations    Per-user attestations of having read + accepted a
--                          policy — captured manually (training rollout) or
--                          by an external HR/LMS integration. Status:
--                          PENDING → ATTESTED → DECLINED.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'policy')
    EXEC('CREATE SCHEMA policy');
GO

-- ─── policy.documents ──────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'documents' AND schema_id = SCHEMA_ID('policy'))
BEGIN
    CREATE TABLE policy.documents (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        policy_code         NVARCHAR(40)  NOT NULL,
        version             NVARCHAR(20)  NOT NULL DEFAULT N'1.0',

        title_en            NVARCHAR(255) NOT NULL,
        title_ar            NVARCHAR(255) NULL,
        summary_en          NVARCHAR(MAX) NULL,
        summary_ar          NVARCHAR(MAX) NULL,
        body_url            NVARCHAR(500) NULL,    -- URL / docstore reference

        category            NVARCHAR(60)  NOT NULL DEFAULT N'GENERAL',
        applicability_scope NVARCHAR(MAX) NULL,    -- free-text or JSON list of orgs/departments

        owner_name          NVARCHAR(255) NULL,
        owner_email         NVARCHAR(255) NULL,
        approver_name       NVARCHAR(255) NULL,
        approver_email      NVARCHAR(255) NULL,

        effective_date      DATE          NULL,
        expiry_date         DATE          NULL,
        next_review_date    DATE          NULL,
        review_cadence      NVARCHAR(20)  NULL,    -- ANNUAL | BIENNIAL | TRIENNIAL | AD_HOC

        status              NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        published_at        DATETIME2     NULL,
        retired_reason      NVARCHAR(MAX) NULL,

        related_framework   NVARCHAR(120) NULL,    -- e.g. 'NCA-ECC-V3', 'PDPL', 'CMA-CG'
        related_controls    NVARCHAR(MAX) NULL,    -- pipe-separated control codes

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_policy_status CHECK (status IN
            (N'DRAFT', N'REVIEW', N'APPROVED', N'PUBLISHED', N'EXPIRED', N'ARCHIVED')),
        CONSTRAINT ck_policy_cadence CHECK (review_cadence IS NULL OR review_cadence IN
            (N'ANNUAL', N'BIENNIAL', N'TRIENNIAL', N'AD_HOC'))
    );

    CREATE INDEX ix_policy_doc_org      ON policy.documents(organisation_id);
    CREATE INDEX ix_policy_doc_status   ON policy.documents(organisation_id, status);
    CREATE INDEX ix_policy_doc_expiry   ON policy.documents(expiry_date);
    CREATE UNIQUE INDEX ux_policy_doc_org_code_version
        ON policy.documents(organisation_id, policy_code, version) WHERE is_deleted = 0;
END
GO

-- ─── policy.exceptions ─────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'exceptions' AND schema_id = SCHEMA_ID('policy'))
BEGIN
    CREATE TABLE policy.exceptions (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT        NOT NULL,
        external_id                 NVARCHAR(64)  NULL,

        policy_id                   BIGINT        NOT NULL,
        exception_code              NVARCHAR(40)  NULL,

        title_en                    NVARCHAR(255) NOT NULL,
        title_ar                    NVARCHAR(255) NULL,
        justification_en            NVARCHAR(MAX) NOT NULL,
        justification_ar            NVARCHAR(MAX) NULL,
        compensating_controls_en    NVARCHAR(MAX) NULL,
        compensating_controls_ar    NVARCHAR(MAX) NULL,

        requestor_name              NVARCHAR(255) NULL,
        requestor_email             NVARCHAR(255) NULL,
        reviewer_name               NVARCHAR(255) NULL,
        approver_name               NVARCHAR(255) NULL,

        risk_level                  NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',
        risk_id                     BIGINT        NULL,    -- soft link into ERM

        requested_at                DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        reviewed_at                 DATETIME2     NULL,
        approved_at                 DATETIME2     NULL,
        rejected_at                 DATETIME2     NULL,
        rejection_reason            NVARCHAR(MAX) NULL,

        effective_date              DATE          NULL,
        expiry_date                 DATE          NULL,

        status                      NVARCHAR(20)  NOT NULL DEFAULT N'REQUESTED',

        notes                       NVARCHAR(MAX) NULL,

        is_deleted                  BIT           NOT NULL DEFAULT 0,
        created_at                  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  NVARCHAR(100) NULL,
        updated_at                  DATETIME2     NULL,
        updated_by                  NVARCHAR(100) NULL,

        CONSTRAINT ck_policy_exc_status CHECK (status IN
            (N'REQUESTED', N'UNDER_REVIEW', N'APPROVED', N'REJECTED', N'EXPIRED', N'REVOKED')),
        CONSTRAINT ck_policy_exc_risk   CHECK (risk_level IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW'))
    );

    CREATE INDEX ix_policy_exc_org       ON policy.exceptions(organisation_id);
    CREATE INDEX ix_policy_exc_policy    ON policy.exceptions(policy_id);
    CREATE INDEX ix_policy_exc_status    ON policy.exceptions(organisation_id, status);
    CREATE INDEX ix_policy_exc_expiry    ON policy.exceptions(expiry_date);
END
GO

-- ─── policy.attestations ───────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'attestations' AND schema_id = SCHEMA_ID('policy'))
BEGIN
    CREATE TABLE policy.attestations (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        policy_id           BIGINT        NOT NULL,
        policy_version      NVARCHAR(20)  NULL,

        user_username       NVARCHAR(120) NOT NULL,
        user_full_name      NVARCHAR(255) NULL,
        user_role           NVARCHAR(80)  NULL,
        user_department     NVARCHAR(120) NULL,

        status              NVARCHAR(20)  NOT NULL DEFAULT N'PENDING',
        attested_at         DATETIME2     NULL,
        declined_at         DATETIME2     NULL,
        declined_reason     NVARCHAR(MAX) NULL,

        evidence_ref        NVARCHAR(255) NULL,    -- e.g. LMS completion id

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_policy_att_status CHECK (status IN
            (N'PENDING', N'ATTESTED', N'DECLINED', N'EXPIRED'))
    );

    CREATE INDEX ix_policy_att_org       ON policy.attestations(organisation_id);
    CREATE INDEX ix_policy_att_policy    ON policy.attestations(policy_id);
    CREATE INDEX ix_policy_att_user      ON policy.attestations(organisation_id, user_username);
    CREATE INDEX ix_policy_att_status    ON policy.attestations(organisation_id, status);
END
GO

-- ─── Seed — 6 policies + 3 exceptions + 6 attestations ─────────────

IF NOT EXISTS (SELECT 1 FROM policy.documents WHERE organisation_id = 1)
BEGIN
    INSERT INTO policy.documents (
        organisation_id, policy_code, version,
        title_en, title_ar, summary_en, summary_ar,
        category, owner_name, owner_email, approver_name,
        effective_date, expiry_date, next_review_date, review_cadence,
        status, published_at, related_framework, related_controls
    ) VALUES
    (1, N'POL-IS-001', N'2.1',
     N'Information Security Policy',
     N'سياسة أمن المعلومات',
     N'Top-level information-security policy aligned with NCA ECC v3, ISO 27001, and SAMA Cyber Resilience Framework.',
     N'السياسة الرئيسية لأمن المعلومات المُواءَمة مع NCA ECC v3 وآيزو 27001 وإطار ساما للمرونة السيبراني.',
     N'INFORMATION_SECURITY', N'CISO', N'ciso@example.gov.sa', N'CEO',
     '2025-01-15', '2027-01-14', '2026-01-15', N'ANNUAL',
     N'PUBLISHED', '2025-01-15T08:00:00', N'NCA-ECC-V3', N'ECC-1-1-1|ECC-1-2-1|ECC-2-1-1'),

    (1, N'POL-DG-001', N'1.3',
     N'Data Governance Policy',
     N'سياسة حوكمة البيانات',
     N'Enterprise data-governance roles, decision rights, and standards aligned with SDAIA NDI requirements.',
     N'الأدوار وصلاحيات اتخاذ القرار والمعايير لحوكمة البيانات وفق متطلبات نضيء.',
     N'DATA_GOVERNANCE', N'Chief Data Officer', N'cdo@example.gov.sa', N'CEO',
     '2025-06-01', '2027-05-31', '2026-06-01', N'ANNUAL',
     N'PUBLISHED', '2025-06-01T08:00:00', N'SDAIA-NDI', N'NDI-DG-001|NDI-DG-002'),

    (1, N'POL-PDPL-001', N'1.0',
     N'Personal Data Protection Policy',
     N'سياسة حماية البيانات الشخصية',
     N'PDPL compliance policy — lawful basis, data-subject rights, breach notification, cross-border transfers (Art. 29).',
     N'سياسة الالتزام بنظام حماية البيانات الشخصية — الأساس القانوني، حقوق أصحاب البيانات، الإبلاغ عن الانتهاكات، النقل العابر للحدود (المادة 29).',
     N'PRIVACY', N'Data Protection Officer', N'dpo@example.gov.sa', N'General Counsel',
     '2025-03-01', '2028-02-29', '2026-03-01', N'ANNUAL',
     N'PUBLISHED', '2025-03-01T08:00:00', N'PDPL', N'PDPL-12|PDPL-18|PDPL-29'),

    (1, N'POL-AUDIT-001', N'2.0',
     N'Internal Audit Charter',
     N'ميثاق التدقيق الداخلي',
     N'Internal audit purpose, authority, responsibility, and independence per IIA Standards + CMA CG Art. 95.',
     N'الغرض من التدقيق الداخلي وصلاحياته ومسؤولياته واستقلاليته وفق معايير IIA ومادة 95 من حوكمة الشركات.',
     N'GOVERNANCE', N'Head of Internal Audit', N'hia@example.gov.sa', N'Audit Committee Chair',
     '2025-02-15', '2028-02-14', '2026-02-15', N'ANNUAL',
     N'PUBLISHED', '2025-02-15T08:00:00', N'IIA-STANDARDS', N'IIA-1000|IIA-1100|IIA-1200'),

    (1, N'POL-AML-001', N'1.5',
     N'Anti-Money-Laundering Policy',
     N'سياسة مكافحة غسل الأموال',
     N'AML / CTF program — customer due diligence, transaction monitoring, suspicious transaction reporting per SAMA Rules.',
     N'برنامج مكافحة غسل الأموال وتمويل الإرهاب — العناية الواجبة بالعملاء، مراقبة العمليات، الإبلاغ عن العمليات المشبوهة وفق قواعد ساما.',
     N'COMPLIANCE', N'Chief Compliance Officer', N'cco@example.gov.sa', N'Board Risk Committee',
     '2024-12-01', '2026-11-30', '2025-12-01', N'ANNUAL',
     N'PUBLISHED', '2024-12-01T08:00:00', N'SAMA-AML', N'AML-CDD-1|AML-TM-1|AML-STR-1'),

    (1, N'POL-BCM-001', N'1.0',
     N'Business Continuity Policy',
     N'سياسة استمرارية الأعمال',
     N'BCM policy aligned with ISO 22301 — BIA, recovery time / point objectives, exercise programme.',
     N'سياسة استمرارية الأعمال المُواءَمة مع آيزو 22301 — تحليل تأثير الأعمال وأهداف الاسترداد وبرنامج التمارين.',
     N'BUSINESS_CONTINUITY', N'Head of BCM', N'bcm@example.gov.sa', N'COO',
     NULL, NULL, '2026-08-01', N'BIENNIAL',
     N'REVIEW', NULL, N'ISO-22301', N'BCM-1|BCM-2|BCM-3');
END
GO

IF NOT EXISTS (SELECT 1 FROM policy.exceptions WHERE organisation_id = 1)
BEGIN
    DECLARE @pol_is BIGINT = (SELECT id FROM policy.documents WHERE policy_code = N'POL-IS-001');
    DECLARE @pol_pdpl BIGINT = (SELECT id FROM policy.documents WHERE policy_code = N'POL-PDPL-001');

    INSERT INTO policy.exceptions (
        organisation_id, policy_id, exception_code,
        title_en, title_ar,
        justification_en, justification_ar,
        compensating_controls_en, compensating_controls_ar,
        requestor_name, approver_name, risk_level,
        requested_at, approved_at, effective_date, expiry_date, status
    ) VALUES
    (1, @pol_is, N'EXC-2026-001',
     N'Legacy AS/400 cannot enforce MFA on service accounts',
     N'منصة AS/400 القديمة لا يمكنها فرض MFA على حسابات الخدمة',
     N'15-year-old core banking platform predates MFA. Vendor end-of-life Q2 2027. Migration project underway.',
     N'منصة البنوك الجوهرية تسبق MFA بـ 15 عاماً. انتهاء دعم المورد في الربع الثاني 2027. مشروع الترحيل جارٍ.',
     N'Service accounts restricted to single jump-host with PAM session recording + IP allowlist + monthly review.',
     N'حسابات الخدمة مقيَّدة بمضيف واحد مع تسجيل جلسات PAM وقائمة IPs مسموح بها ومراجعة شهرية.',
     N'Khalid Al-Harbi (CIO)', N'Sara Al-Zahrani (CISO)', N'HIGH',
     '2026-01-10T09:00:00', '2026-01-20T14:30:00', '2026-01-20', '2027-06-30', N'APPROVED'),

    (1, @pol_pdpl, N'EXC-2026-002',
     N'Cross-border transfer to AWS Frankfurt for the data lake',
     N'النقل العابر للحدود إلى AWS فرانكفورت لبحيرة البيانات',
     N'Analytics workloads require AWS Frankfurt (no Saudi region yet for the chosen services). PDPL Art. 29 deviation.',
     N'أعباء التحليلات تتطلب AWS فرانكفورت (لا توجد منطقة سعودية حالياً للخدمات المختارة). انحراف عن المادة 29 من PDPL.',
     N'SCC + binding corporate rules signed with AWS. PII tokenised before egress. Data Protection Officer monthly review.',
     N'بنود تعاقدية معيارية + قواعد ملزمة مع AWS. الترميز قبل التصدير. مراجعة شهرية من مسؤول حماية البيانات.',
     N'Chief Data Officer', N'Data Protection Officer', N'MEDIUM',
     '2026-02-05T11:00:00', NULL, NULL, NULL, N'UNDER_REVIEW'),

    (1, @pol_is, N'EXC-2026-003',
     N'Temporary password complexity exemption — kiosk users',
     N'استثناء مؤقت من تعقيد كلمة المرور — مستخدمي الأكشاك',
     N'Branch kiosk users have 4-digit PINs (current device limitation). Replacement hardware ordered.',
     N'مستخدمو أكشاك الفروع لديهم رموز PIN من 4 أرقام (قيود الجهاز الحالي). تم طلب أجهزة بديلة.',
     N'Kiosk session limited to 60 seconds. CCTV monitoring. No financial transactions without secondary auth.',
     N'جلسة الكشك محدودة بـ 60 ثانية. مراقبة CCTV. لا توجد معاملات مالية دون مصادقة ثانوية.',
     N'Head of Branch Operations', N'CISO', N'LOW',
     '2026-03-15T10:00:00', NULL, NULL, '2026-09-30', N'REQUESTED');
END
GO

IF NOT EXISTS (SELECT 1 FROM policy.attestations WHERE organisation_id = 1)
BEGIN
    DECLARE @pol_is BIGINT = (SELECT id FROM policy.documents WHERE policy_code = N'POL-IS-001');
    DECLARE @pol_dg BIGINT = (SELECT id FROM policy.documents WHERE policy_code = N'POL-DG-001');
    DECLARE @pol_pdpl BIGINT = (SELECT id FROM policy.documents WHERE policy_code = N'POL-PDPL-001');

    INSERT INTO policy.attestations (
        organisation_id, policy_id, policy_version,
        user_username, user_full_name, user_role, user_department,
        status, attested_at
    ) VALUES
    (1, @pol_is, N'2.1', N'admin',         N'Platform Admin',           N'PLATFORM_ADMIN',  N'IT',           N'ATTESTED', '2026-01-20T09:15:00'),
    (1, @pol_is, N'2.1', N'riskmanager',   N'Sara Al-Zahrani',          N'RISK_MANAGER',    N'Risk',         N'ATTESTED', '2026-01-21T11:30:00'),
    (1, @pol_is, N'2.1', N'auditor',       N'Faisal Al-Otaibi',         N'AUDITOR',         N'Internal Audit', N'PENDING', NULL),
    (1, @pol_dg, N'1.3', N'admin',         N'Platform Admin',           N'PLATFORM_ADMIN',  N'IT',           N'ATTESTED', '2026-02-10T14:00:00'),
    (1, @pol_dg, N'1.3', N'riskmanager',   N'Sara Al-Zahrani',          N'RISK_MANAGER',    N'Risk',         N'PENDING',  NULL),
    (1, @pol_pdpl, N'1.0', N'admin',       N'Platform Admin',           N'PLATFORM_ADMIN',  N'IT',           N'ATTESTED', '2026-03-05T08:45:00');
END
GO

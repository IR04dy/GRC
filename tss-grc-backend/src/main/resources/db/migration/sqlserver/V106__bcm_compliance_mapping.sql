-- V106: BCM compliance mapping (audit Tier-1 #5).
--
-- Spec §P calls for ISO 22301 / DGA / SAMA / NRC requirements mapped to
-- BCM workflow items + evidence. Today nothing of the sort exists — F1's
-- authority documents catalog can hold the requirements list but no BCM-
-- specific mapping table renders it.
--
-- Schema is intentionally generic: each row is one (requirement × workflow-
-- item) link with its own assessment status + evidence pointer + gap link.
-- This shape supports any framework with a "control id → implementing
-- artefact" structure (ISO 22301 §X.Y → an approved BCP; SAMA OPRES Y →
-- a tested DRP; etc.).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'compliance_mappings' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.compliance_mappings (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        external_id             UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- ── Requirement side (the spec / framework clause) ──
        source_framework        NVARCHAR(50)    NOT NULL,
        -- ISO_22301 | SAMA_OPRES | DGA | NCEMA | INTERNAL | CUSTOM
        requirement_code        NVARCHAR(100)   NOT NULL,    -- e.g. "8.2.2" or "OPRES-RC-04"
        requirement_title_en    NVARCHAR(500)   NOT NULL,
        requirement_title_ar    NVARCHAR(500)   NULL,
        requirement_description NVARCHAR(MAX)   NULL,
        domain                  NVARCHAR(100)   NULL,        -- e.g. "Operational planning"
        is_placeholder          BIT             NOT NULL DEFAULT 1,  -- per the audit's "PLACEHOLDER until imports land" stance

        -- ── BCM workflow side (the implementing artefact, if any) ──
        related_workflow        NVARCHAR(50)    NULL,
        -- GOVERNANCE | BIA | BCP | DRP | EXERCISE | CRISIS | CALL_TREE | STRATEGY | TRAINING | EVIDENCE | OTHER
        related_record_type     NVARCHAR(100)   NULL,        -- e.g. "BcmBiaAssessment"
        related_record_id       BIGINT          NULL,        -- soft FK
        related_control_id      BIGINT          NULL,        -- soft FK to platform.controls (F2 unified library)

        -- ── Assessment side (who attested compliance, when, and how) ──
        owner_id                BIGINT          NULL,
        evidence_id             BIGINT          NULL,
        compliance_status       NVARCHAR(30)    NOT NULL DEFAULT 'NOT_ASSESSED',
        -- NOT_ASSESSED | COMPLIANT | PARTIAL | NON_COMPLIANT | NOT_APPLICABLE
        assessed_at             DATETIME2       NULL,
        assessed_by             NVARCHAR(255)   NULL,
        notes                   NVARCHAR(MAX)   NULL,

        -- ── Gap escalation pointer (when status = PARTIAL / NON_COMPLIANT) ──
        gap_id                  BIGINT          NULL,        -- soft FK to bcm.gaps

        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NULL,
        created_by              BIGINT          NULL,
        updated_by              BIGINT          NULL,
        is_deleted              BIT             NOT NULL DEFAULT 0,
        deleted_at              DATETIME2       NULL,
        deleted_by              BIGINT          NULL,
        row_version             ROWVERSION,

        CONSTRAINT ck_bcm_compliance_status CHECK (
            compliance_status IN ('NOT_ASSESSED','COMPLIANT','PARTIAL','NON_COMPLIANT','NOT_APPLICABLE'))
    );
    -- Uniqueness: one row per (org, framework, requirement). Mapping the SAME
    -- requirement to multiple artefacts is currently handled by appending a
    -- suffix to requirement_code; future sprint can split into a many-to-many
    -- bridge table if real use-cases demand it.
    CREATE UNIQUE INDEX UX_bcm_compl_org_req
        ON bcm.compliance_mappings(organisation_id, source_framework, requirement_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_bcm_compl_org_status
        ON bcm.compliance_mappings(organisation_id, compliance_status) WHERE is_deleted = 0;
    CREATE INDEX        IX_bcm_compl_org_workflow
        ON bcm.compliance_mappings(organisation_id, related_workflow) WHERE is_deleted = 0;
END
GO

-- ─── Seed PLACEHOLDER rows for the four headline frameworks ────────
-- These are stubs so the page renders something on a fresh install. Each
-- row carries is_placeholder=1 + "PLACEHOLDER — replace with official text"
-- in the title, matching the banner pattern used elsewhere in the platform.
IF NOT EXISTS (SELECT 1 FROM bcm.compliance_mappings WHERE organisation_id = 1 AND source_framework = 'ISO_22301')
BEGIN
    INSERT INTO bcm.compliance_mappings
      (organisation_id, source_framework, requirement_code, requirement_title_en, requirement_title_ar,
       domain, related_workflow, is_placeholder, compliance_status)
    VALUES
      -- ISO 22301:2019 clauses (placeholder text — replace with official translation)
      (1, 'ISO_22301', '4.1',  'PLACEHOLDER — Understanding the organisation and its context', N'مؤقت — فهم المؤسسة وسياقها',         'Context',                    'GOVERNANCE', 1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '5.1',  'PLACEHOLDER — Leadership and commitment',                       N'مؤقت — القيادة والالتزام',          'Leadership',                 'GOVERNANCE', 1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '6.2',  'PLACEHOLDER — BCM objectives and planning',                     N'مؤقت — أهداف وتخطيط الاستمرارية',  'Planning',                   'GOVERNANCE', 1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '8.2.2','PLACEHOLDER — Business Impact Analysis',                        N'مؤقت — تحليل أثر الأعمال',          'Operational planning',       'BIA',        1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '8.2.3','PLACEHOLDER — Risk assessment',                                 N'مؤقت — تقييم المخاطر',              'Operational planning',       'STRATEGY',   1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '8.3',  'PLACEHOLDER — Business continuity strategies and solutions',    N'مؤقت — استراتيجيات الاستمرارية',    'Operational planning',       'STRATEGY',   1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '8.4',  'PLACEHOLDER — Business continuity plans and procedures',        N'مؤقت — خطط وإجراءات الاستمرارية',  'Operational planning',       'BCP',        1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '8.5',  'PLACEHOLDER — Exercise programme',                              N'مؤقت — برنامج التمارين',            'Operational planning',       'EXERCISE',   1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '9.1',  'PLACEHOLDER — Monitoring, measurement, analysis and evaluation',N'مؤقت — الرصد والقياس والتقييم',     'Performance evaluation',     'GOVERNANCE', 1, 'NOT_ASSESSED'),
      (1, 'ISO_22301', '10.2', 'PLACEHOLDER — Nonconformity and corrective action',             N'مؤقت — عدم المطابقة والإجراء',       'Improvement',                'GOVERNANCE', 1, 'NOT_ASSESSED'),

      -- SAMA OPRES — 5 headline domains (placeholder)
      (1, 'SAMA_OPRES', 'OPRES-1', 'PLACEHOLDER — Governance and oversight',         N'مؤقت — الحوكمة والرقابة',                  'Governance',          'GOVERNANCE', 1, 'NOT_ASSESSED'),
      (1, 'SAMA_OPRES', 'OPRES-2', 'PLACEHOLDER — Critical operations identification', N'مؤقت — تحديد العمليات الحرجة',           'Critical ops',        'BIA',        1, 'NOT_ASSESSED'),
      (1, 'SAMA_OPRES', 'OPRES-3', 'PLACEHOLDER — Impact tolerance setting',         N'مؤقت — تحديد حدود الأثر',                    'Impact tolerance',    'BIA',        1, 'NOT_ASSESSED'),
      (1, 'SAMA_OPRES', 'OPRES-4', 'PLACEHOLDER — Mapping and dependency analysis',  N'مؤقت — التخطيط وتحليل التبعيات',           'Mapping',             'STRATEGY',   1, 'NOT_ASSESSED'),
      (1, 'SAMA_OPRES', 'OPRES-5', 'PLACEHOLDER — Scenario testing',                 N'مؤقت — اختبار السيناريوهات',                 'Testing',             'EXERCISE',   1, 'NOT_ASSESSED'),

      -- DGA — 3 headline (placeholder)
      (1, 'DGA',   'DGA-BCM-01', 'PLACEHOLDER — Digital service availability', N'مؤقت — توافر الخدمة الرقمية',                       'Availability', 'BCP', 1, 'NOT_ASSESSED'),
      (1, 'DGA',   'DGA-BCM-02', 'PLACEHOLDER — Citizen service continuity',   N'مؤقت — استمرارية خدمة المواطن',                     'Continuity',   'BCP', 1, 'NOT_ASSESSED'),
      (1, 'DGA',   'DGA-BCM-03', 'PLACEHOLDER — DR drill cadence',             N'مؤقت — تكرار اختبارات التعافي',                     'Testing',      'EXERCISE', 1, 'NOT_ASSESSED'),

      -- NCEMA — 2 headline (placeholder)
      (1, 'NCEMA', 'NCEMA-01', 'PLACEHOLDER — Crisis classification + tier',   N'مؤقت — تصنيف الأزمة',                              'Classification', 'CRISIS', 1, 'NOT_ASSESSED'),
      (1, 'NCEMA', 'NCEMA-02', 'PLACEHOLDER — Notification + escalation',      N'مؤقت — الإخطار والتصعيد',                          'Notification',   'CRISIS', 1, 'NOT_ASSESSED');
END
GO

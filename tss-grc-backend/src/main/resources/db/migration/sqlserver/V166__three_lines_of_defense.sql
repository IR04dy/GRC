-- V166 — Three Lines of Defense (IIA 2020) role-to-band mapping.
--
-- Sprint 11 — strategic GRC view that maps every system role to one of the
-- four IIA 2020 bands so the platform can present a coherent "who is
-- responsible for what?" picture across risks, controls, findings, CAPA,
-- policies and DSRs.
--
-- Why a mapping table (not a column on iam.roles)?
--   * Same role can legitimately mean different bands in different tenants
--     — e.g. a small bank's COMPLIANCE_OFFICER may also cover DPO duties
--     (LINE_2), but in a SAMA-licensed full-scope bank the COMPLIANCE_OFFICER
--     is strictly Line 2 with a separate DPO. Per-org overrides live in
--     this table, system-wide defaults sit on organisation_id = NULL.
--   * Saudi regulatory expectation (SAMA CSF 1.1.1 + NCA ECC-2 1-1) is to
--     prove "clear allocation of risk and compliance responsibilities" —
--     this table is the auditable, queryable artefact that proves it.
--   * Highest-band-wins resolution: when a user has multiple roles, the
--     service picks the highest band for assurance independence
--     (LINE_3 > LINE_2 > LINE_1; BOARD is a separate oversight band).
--     Implemented in ThreeLinesService.bandForUser(...).
--
-- Aligned with:
--   * IIA Three Lines Model (2020 update)
--   * SAMA Cyber-Security Framework 1.1.1 — Governance: clear allocation of
--     risk and compliance responsibilities
--   * NCA ECC-2 1-1 — Cybersecurity Governance
--   * COSO ERM 2017 — Governance & Culture component

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: platform.three_lines_role_mapping
-- ════════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'three_lines_role_mapping')
BEGIN
    CREATE TABLE platform.three_lines_role_mapping (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NULL,                          -- NULL = system-wide default
        role_name         NVARCHAR(80)    NOT NULL,                      -- matches iam.roles.role_name
        lod_band          NVARCHAR(20)    NOT NULL,                      -- LINE_1 | LINE_2 | LINE_3 | BOARD
        title_en          NVARCHAR(160)   NOT NULL,
        title_ar          NVARCHAR(160)   NOT NULL,
        rationale_en      NVARCHAR(500)   NULL,
        rationale_ar      NVARCHAR(500)   NULL,

        -- BaseEntity-aligned audit columns
        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2       NULL,
        created_by        BIGINT          NULL,
        updated_by        BIGINT          NULL,
        is_deleted        BIT             NOT NULL DEFAULT 0,
        deleted_at        DATETIME2       NULL,
        deleted_by        BIGINT          NULL,
        row_version       ROWVERSION      NOT NULL,

        CONSTRAINT CK_three_lines_role_mapping_band
            CHECK (lod_band IN ('LINE_1','LINE_2','LINE_3','BOARD'))
    );

    -- Fast lookup — "what band is this role in for this org?" Filtered to
    -- live rows. Distinguishes system-defaults (org NULL) from per-tenant
    -- overrides — the service prefers the per-tenant row when present.
    CREATE UNIQUE INDEX UX_three_lines_role_mapping_role_per_org
        ON platform.three_lines_role_mapping (organisation_id, role_name)
        WHERE is_deleted = 0;

    -- Band-fan-out lookup — "list all LINE_2 roles for this org" — fires
    -- when ThreeLinesService aggregates artefacts per band.
    CREATE INDEX IX_three_lines_role_mapping_band
        ON platform.three_lines_role_mapping (organisation_id, lod_band)
        WHERE is_deleted = 0;
END
GO

-- ════════════════════════════════════════════════════════════════════════
-- Seed: system-wide (organisation_id = NULL) IIA-aligned role mapping.
--
-- Each insert is guarded with NOT EXISTS so re-running this migration is
-- safe and does not duplicate or overwrite tenant-tuned mappings.
--
-- Mapping rationale (Saudi-native context):
--
--   LINE_1 — Operations / risk owners. Day-to-day controls, incident
--            reporting, CAPA execution. Frontline business units, branch
--            managers, IT operations, application owners.
--
--   LINE_2 — Risk / compliance / privacy / cyber-GRC oversight. Set
--            policy, monitor Line 1 control effectiveness, review
--            breaches, sign off treatment plans, DPO (PDPL Art.32).
--
--   LINE_3 — Internal Audit. Independent assurance reporting to the
--            Audit Committee. Includes external auditors invited in for
--            specific engagements.
--
--   BOARD  — Board, Audit Committee, Risk Committee — provide oversight,
--            approve risk appetite, receive CRITICAL findings and major
--            incidents. Not a "line" per IIA 2020 but tracked as a
--            governance band for the dashboard.
-- ════════════════════════════════════════════════════════════════════════

;WITH defaults (role_name, lod_band, title_en, title_ar, rationale_en, rationale_ar) AS (
    -- LINE 1 — Operations / risk owners
    SELECT 'RISK_OWNER',           'LINE_1', 'Risk Owner',                'مالك المخاطر التشغيلية',
           'Owns specific risks, executes day-to-day controls, attests on SAMA cadence.',
           N'يملك مخاطر محددة، ينفذ الضوابط اليومية، يصادق على دورة ساما.'
    UNION ALL SELECT 'CONTROL_OWNER',     'LINE_1', 'Control Owner',             N'مالك الضوابط',
           'Owns specific controls and their effectiveness rating in operations.',
           N'يملك ضوابط محددة وتقييم فعاليتها في العمليات.'
    UNION ALL SELECT 'OPS_MANAGER',       'LINE_1', 'Operations Manager',        N'مدير العمليات',
           'Frontline business operations — branch, ops, manufacturing.',
           N'إدارة العمليات الأمامية — الفروع والتشغيل والإنتاج.'
    UNION ALL SELECT 'BRANCH_MANAGER',    'LINE_1', 'Branch Manager',            N'مدير الفرع',
           'Branch-level control execution and incident escalation.',
           N'تنفيذ الضوابط على مستوى الفرع وتصعيد الحوادث.'
    UNION ALL SELECT 'IT_ADMIN',          'LINE_1', 'IT Administrator',          N'مسؤول تقنية المعلومات',
           'IT operations executing technical controls on systems.',
           N'تشغيل تقنية المعلومات وتنفيذ الضوابط الفنية على الأنظمة.'
    UNION ALL SELECT 'SERVICE_OWNER',     'LINE_1', 'Service Owner',             N'مالك الخدمة',
           'Owns a business service end-to-end including its controls.',
           N'يملك خدمة عمل من البداية للنهاية بما في ذلك ضوابطها.'
    UNION ALL SELECT 'ASSET_OWNER',       'LINE_1', 'Asset Owner',               N'مالك الأصل',
           'Owns assets and applications, accountable for their protection.',
           N'يملك الأصول والتطبيقات ومسؤول عن حمايتها.'
    UNION ALL SELECT 'BIA_OWNER',         'LINE_1', 'BIA Owner',                 N'مالك تحليل أثر الأعمال',
           'Conducts BIAs in business units and signs them off.',
           N'يجري تحليل أثر الأعمال في وحدات العمل ويعتمده.'
    UNION ALL SELECT 'EVIDENCE_OWNER',    'LINE_1', 'Evidence Owner',            N'مالك الأدلة',
           'Uploads and refreshes operational control evidence.',
           N'يرفع ويُحدّث أدلة الضوابط التشغيلية.'
    UNION ALL SELECT 'DATA_OWNER',        'LINE_1', 'Data Owner',                N'مالك البيانات',
           'Owns a data asset / processing activity in the business.',
           N'يملك أصلًا من البيانات أو نشاط معالجة في العمل.'

    -- LINE 2 — Risk / Compliance / Privacy / Cyber-GRC oversight
    UNION ALL SELECT 'RISK_MANAGER',      'LINE_2', 'Risk Manager',              N'مدير وظيفة المخاطر',
           'Risk management function — sets methodology, monitors Line 1.',
           N'وظيفة إدارة المخاطر — تضع المنهجية وتراقب الخط الأول.'
    UNION ALL SELECT 'COMPLIANCE_OFFICER','LINE_2', 'Compliance Officer',        N'مسؤول وظيفة الالتزام',
           'Compliance function — SAMA / NCA / PDPL obligation tracking.',
           N'وظيفة الالتزام — متابعة التزامات ساما والهيئة الوطنية ونظام حماية البيانات.'
    UNION ALL SELECT 'DPO',               'LINE_2', 'Data Protection Officer',   N'مسؤول حماية البيانات',
           'PDPL Article 32 designated officer — DSR/breach oversight + DPIA approval.',
           N'المسؤول المعيّن وفق المادة 32 من نظام حماية البيانات — الإشراف على طلبات أصحاب البيانات والاختراقات واعتماد تقييم الأثر.'
    UNION ALL SELECT 'CISO_GOVERNANCE',   'LINE_2', 'CISO Governance',           N'حوكمة الأمن السيبراني',
           'CISO governance leg — cybersecurity policy + Line 1 oversight.',
           N'الجانب الحوكمي للرئيس التنفيذي للأمن السيبراني — سياسات وإشراف على الخط الأول.'
    UNION ALL SELECT 'PRIVACY_MANAGER',   'LINE_2', 'Privacy Manager',           N'مدير الخصوصية',
           'Day-to-day privacy oversight working alongside the DPO.',
           N'الإشراف اليومي على الخصوصية بالعمل مع مسؤول حماية البيانات.'
    UNION ALL SELECT 'NCA_COMPLIANCE',    'LINE_2', 'NCA Compliance Owner',      N'مسؤول الالتزام للهيئة الوطنية',
           'NCA ECC / CSCC framework compliance + evidence oversight.',
           N'الإشراف على الالتزام بأطر الهيئة الوطنية للأمن السيبراني والأدلة.'
    UNION ALL SELECT 'LEGAL_REVIEWER',    'LINE_2', 'Legal Reviewer',            N'مراجع قانوني',
           'Reviews contracts, cross-border transfers, breach drafts.',
           N'مراجعة العقود ونقل البيانات عبر الحدود ومسودات إشعارات الاختراق.'

    -- LINE 3 — Internal Audit (independent assurance)
    UNION ALL SELECT 'INTERNAL_AUDITOR',  'LINE_3', 'Internal Auditor',          N'المراجع الداخلي',
           'Independent assurance — owns audit universe + engagements.',
           N'تأكيدات مستقلة — يملك كون التدقيق والارتباطات.'
    UNION ALL SELECT 'AUDIT_DIRECTOR',    'LINE_3', 'Audit Director',            N'مدير المراجعة الداخلية',
           'Heads Internal Audit, reports to Audit Committee — independence preserved.',
           N'رئيس المراجعة الداخلية، يرفع للجنة المراجعة — مع الحفاظ على الاستقلالية.'
    UNION ALL SELECT 'AUDITOR',           'LINE_3', 'Auditor',                   N'المراجع',
           'Audit team member — read-access to evidence + findings.',
           N'عضو فريق المراجعة — صلاحية القراءة للأدلة والملاحظات.'
    UNION ALL SELECT 'EXTERNAL_AUDITOR',  'LINE_3', 'External Auditor',          N'المراجع الخارجي',
           'Time-scoped read-only access for external assurance engagements.',
           N'وصول مؤقت للقراءة فقط لارتباطات التأكيد الخارجية.'

    -- BOARD — Oversight band (Audit Committee, Risk Committee, Board itself)
    UNION ALL SELECT 'BOARD_MEMBER',          'BOARD',  'Board Member',                  N'عضو مجلس الإدارة',
           'Board oversight — approves risk appetite, receives critical findings.',
           N'الإشراف من مجلس الإدارة — اعتماد سقف المخاطر واستقبال الملاحظات الحرجة.'
    UNION ALL SELECT 'AUDIT_COMMITTEE_CHAIR', 'BOARD',  'Audit Committee Chair',         N'رئيس لجنة المراجعة',
           'Chairs the Audit Committee — directs Line 3 (Internal Audit) reports.',
           N'يرأس لجنة المراجعة — يوجّه تقارير الخط الثالث (المراجعة الداخلية).'
    UNION ALL SELECT 'BOARD_SECRETARY',       'BOARD',  'Board Secretary',               N'أمين مجلس الإدارة',
           'Prepares board packs and records resolutions.',
           N'يعدّ حزم المجلس ويسجّل القرارات.'
    UNION ALL SELECT 'EXECUTIVE_REVIEWER',    'BOARD',  'Executive Reviewer',            N'مراجع تنفيذي',
           'C-level reviewer on dashboards and board packs.',
           N'مراجع على المستوى التنفيذي للوحات وحزم المجلس.'
)
INSERT INTO platform.three_lines_role_mapping
    (organisation_id, role_name, lod_band, title_en, title_ar, rationale_en, rationale_ar,
     created_at, is_deleted)
SELECT NULL, role_name, lod_band, title_en, title_ar, rationale_en, rationale_ar,
       SYSUTCDATETIME(), 0
  FROM defaults d
 WHERE NOT EXISTS (
     SELECT 1 FROM platform.three_lines_role_mapping m
      WHERE m.organisation_id IS NULL
        AND m.role_name = d.role_name
        AND m.is_deleted = 0
 );
GO

-- V116: AI suggestions ledger + RBAC role expansion + NCEMA AAR builder.
--
-- Three independent pieces, bundled because each is small:
--
--   (1) Saudi GRC implementation prompt §27 — AI module. We don't ship an
--       LLM gateway in this round; the 5 endpoints in module/ai/ are
--       deterministic heuristics that look like AI suggestions, with a
--       persistent ledger so every suggestion is auditable + a human can
--       accept/reject it before it changes any real record.
--
--   (2) Saudi GRC implementation prompt §13 — RBAC. The doc lists 28
--       roles; the V2 core seed only inserts 3. This migration adds the
--       remaining 25 as role rows under organisation_id = 1 so the user-
--       admin screen shows the full Saudi-GRC role catalog (permissions
--       still resolve dynamically through ModulePermissionEvaluator).
--
--   (3) Tier-4 audit #23 — NCEMA After-Action Report (AAR) builder. New
--       bcm.crisis_aar table tied to crisis_events. The AAR is the
--       Saudi-native version of an ISO 22301 lessons-learned bundle —
--       NCEMA expects a structured post-event report with timeline,
--       decisions, regulatory notifications, gaps, and corrective actions.
--
-- Apply with: sqlcmd -f 65001 (UTF-8) — Arabic role labels + AAR boilerplate.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) AI suggestions ledger
-- ══════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ai')
    EXEC('CREATE SCHEMA ai');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'ai_suggestions' AND schema_id = SCHEMA_ID('ai'))
BEGIN
    CREATE TABLE ai.ai_suggestions (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        external_id       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- RISK_DESCRIPTION   — given a short title, expands cause/consequence
        -- BIA_RTO_RPO        — given a service classification, recommends RTO/RPO
        -- EVIDENCE_QUALITY   — scores an evidence item against a control
        -- INCIDENT_ROOT_CAUSE — drafts root-cause from a crisis-event timeline
        -- BOARD_SUMMARY      — drafts an executive paragraph from a dashboard
        feature_code      NVARCHAR(40)    NOT NULL,

        -- Soft FKs — populated when the suggestion is tied to a record
        related_entity    NVARCHAR(40)    NULL,
        related_entity_id BIGINT          NULL,

        input_json        NVARCHAR(MAX)   NOT NULL,
        output_json       NVARCHAR(MAX)   NOT NULL,

        -- DRAFT | ACCEPTED | REJECTED | EXPIRED
        status            NVARCHAR(20)    NOT NULL DEFAULT 'DRAFT',

        confidence        DECIMAL(5,2)    NULL,     -- 0..100, heuristic-derived
        rationale         NVARCHAR(MAX)   NULL,     -- "why this suggestion"

        reviewed_by       BIGINT          NULL,
        reviewed_at       DATETIME2       NULL,
        reviewer_note     NVARCHAR(1000)  NULL,

        is_deleted        BIT             NOT NULL DEFAULT 0,
        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by        BIGINT          NULL,

        CONSTRAINT ck_ai_suggestion_status CHECK (status IN
            ('DRAFT','ACCEPTED','REJECTED','EXPIRED')),
        CONSTRAINT ck_ai_suggestion_confidence CHECK
            (confidence IS NULL OR (confidence >= 0 AND confidence <= 100))
    );
    CREATE INDEX IX_ai_suggestions_org_feature
        ON ai.ai_suggestions(organisation_id, feature_code, created_at DESC);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) RBAC role catalog expansion
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

-- Helper: insert role only when missing (skips the 3 already-seeded ones).
-- Actual column names in iam.roles: role_name, display_name_en, display_name_ar, description_en.
;WITH new_roles (role_name, display_name_en, display_name_ar, description_en) AS (
    SELECT 'GRC_ADMINISTRATOR',     'GRC Administrator',         N'مسؤول الحوكمة والمخاطر والامتثال',  'Tenant-level admin over registers, evidence, and workflows.'
    UNION ALL SELECT 'RISK_OWNER',        'Risk Owner',                N'مالك المخاطر',                       'Owns specific risks; attests them on the SAMA cadence.'
    UNION ALL SELECT 'CONTROL_OWNER',     'Control Owner',             N'مالك الضوابط',                       'Owns specific controls and their effectiveness rating.'
    UNION ALL SELECT 'EVIDENCE_OWNER',    'Evidence Owner',            N'مالك الأدلة',                        'Uploads, renews, and replies on evidence requests.'
    UNION ALL SELECT 'BCM_MANAGER',       'BCM Manager',               N'مدير استمرارية الأعمال',             'Owns the BCM cycle: BIA, BCP, exercises, lessons.'
    UNION ALL SELECT 'BIA_OWNER',         'BIA Owner',                 N'مالك تحليل أثر الأعمال',             'Conducts BIAs for assigned services and signs them off.'
    UNION ALL SELECT 'SERVICE_OWNER',     'Service Owner',             N'مالك الخدمة',                        'Owns a business service and its dependencies.'
    UNION ALL SELECT 'DR_OWNER',          'DR Owner',                  N'مالك خطة التعافي من الكوارث',        'Owns the IT DR plan, runs DR tests.'
    UNION ALL SELECT 'CRISIS_MANAGER',    'Crisis Manager',            N'قائد الأزمات',                       'Activates and commands the crisis room.'
    UNION ALL SELECT 'SUPPLIER_MANAGER',  'Supplier Manager',          N'مدير الموردين',                       'Owns supplier relationships, SLAs, exit plans.'
    UNION ALL SELECT 'PROCUREMENT_OWNER', 'Procurement Owner',         N'مسؤول المشتريات',                     'Owns procurement workflows + supplier onboarding.'
    UNION ALL SELECT 'CYBER_MANAGER',     'Cybersecurity Manager',     N'مدير الأمن السيبراني',               'Owns the NCA programmes (ECC/CSCC/CCC/OTCC) and incidents.'
    UNION ALL SELECT 'NCA_COMPLIANCE',    'NCA Compliance Owner',      N'مسؤول الامتثال للهيئة الوطنية للأمن السيبراني', 'Tracks NCA framework compliance + evidence.'
    UNION ALL SELECT 'ASSET_OWNER',       'Asset Owner',               N'مالك الأصل',                          'Owns assets, applications, and critical systems.'
    UNION ALL SELECT 'VULN_MANAGER',      'Vulnerability Manager',     N'مدير الثغرات',                        'Owns vulnerability + patch lifecycle.'
    UNION ALL SELECT 'DPO',               'Data Protection Officer',   N'مسؤول حماية البيانات',                'PDPL DPO — DSR/breach oversight + DPIA approval.'
    UNION ALL SELECT 'PRIVACY_MANAGER',   'Privacy Manager',           N'مدير الخصوصية',                       'Day-to-day privacy ops under the DPO.'
    UNION ALL SELECT 'DATA_OWNER',        'Data Owner',                N'مالك البيانات',                       'Owns a data asset / processing activity.'
    UNION ALL SELECT 'LEGAL_REVIEWER',    'Legal Reviewer',            N'مراجع قانوني',                        'Reviews contracts, cross-border transfers, breach drafts.'
    UNION ALL SELECT 'COMPLIANCE_OFFICER','Compliance Officer',        N'مسؤول الالتزام',                     'Tracks SAMA / DGA / NRC obligations.'
    UNION ALL SELECT 'INTERNAL_AUDITOR',  'Internal Auditor',          N'المراجع الداخلي',                     'Owns the audit universe + plan + engagements.'
    UNION ALL SELECT 'EXECUTIVE_REVIEWER','Executive Reviewer',        N'مراجع تنفيذي',                        'C-level reviewer on dashboards + board packs.'
    UNION ALL SELECT 'BOARD_SECRETARY',   'Board Secretary',           N'أمين المجلس',                         'Prepares board packs + records board decisions.'
    UNION ALL SELECT 'EXTERNAL_AUDITOR',  'External Auditor',          N'المراجع الخارجي',                     'Time-scoped read-only access to evidence + assessments.'
    UNION ALL SELECT 'SUPPLIER_USER',     'Supplier User',             N'مستخدم مورد',                         'External supplier-portal account (questionnaires + evidence).'
    UNION ALL SELECT 'REGULATOR_VIEWER',  'Regulator Viewer',          N'مراقب من الجهة التنظيمية',           'Time-scoped read-only access for regulator inspections.'
)
INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                       organisation_id, is_system_role, is_active, is_deleted, created_at)
SELECT role_name, display_name_en, display_name_ar, description_en,
       @org, 1, 1, 0, SYSUTCDATETIME()
  FROM new_roles n
 WHERE NOT EXISTS (SELECT 1 FROM iam.roles r WHERE r.role_name = n.role_name);
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) NCEMA After-Action Report (AAR)
-- ══════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'crisis_aar' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_aar (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        external_id       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        crisis_event_id   BIGINT          NOT NULL,    -- soft FK → bcm.crisis_events
        report_code       NVARCHAR(50)    NOT NULL,    -- AAR-2026-000123

        title_en          NVARCHAR(300)   NOT NULL,
        title_ar          NVARCHAR(300)   NULL,

        -- NCEMA AAR structured sections — each markdown body, bilingual.
        executive_summary_en   NVARCHAR(MAX)   NULL,
        executive_summary_ar   NVARCHAR(MAX)   NULL,

        timeline_md_en         NVARCHAR(MAX)   NULL,
        timeline_md_ar         NVARCHAR(MAX)   NULL,

        decisions_md_en        NVARCHAR(MAX)   NULL,
        decisions_md_ar        NVARCHAR(MAX)   NULL,

        what_worked_md_en      NVARCHAR(MAX)   NULL,
        what_worked_md_ar      NVARCHAR(MAX)   NULL,

        what_failed_md_en      NVARCHAR(MAX)   NULL,
        what_failed_md_ar      NVARCHAR(MAX)   NULL,

        root_cause_md_en       NVARCHAR(MAX)   NULL,
        root_cause_md_ar       NVARCHAR(MAX)   NULL,

        recommendations_md_en  NVARCHAR(MAX)   NULL,
        recommendations_md_ar  NVARCHAR(MAX)   NULL,

        regulatory_notifications_md  NVARCHAR(MAX) NULL,  -- summary of PDPL / NCA / SAMA notifications filed

        -- Soft-link JSON arrays — IDs of records spawned from this AAR.
        lessons_learned_ids_json     NVARCHAR(MAX) NULL,
        corrective_action_ids_json   NVARCHAR(MAX) NULL,
        gap_ids_json                 NVARCHAR(MAX) NULL,

        -- DRAFT | UNDER_REVIEW | APPROVED | PUBLISHED | RESCINDED
        status            NVARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
        approved_by       BIGINT          NULL,
        approved_at       DATETIME2       NULL,
        published_at      DATETIME2       NULL,

        digital_signature_txn_id BIGINT   NULL,  -- soft FK → signature.transactions

        is_deleted        BIT             NOT NULL DEFAULT 0,
        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2       NULL,
        created_by        BIGINT          NULL,
        updated_by        BIGINT          NULL,

        CONSTRAINT ck_crisis_aar_status CHECK (status IN
            ('DRAFT','UNDER_REVIEW','APPROVED','PUBLISHED','RESCINDED'))
    );
    CREATE INDEX IX_crisis_aar_org_event
        ON bcm.crisis_aar(organisation_id, crisis_event_id);
END
GO

PRINT 'V116 (AI ledger + RBAC role catalog + NCEMA AAR) applied.';
GO

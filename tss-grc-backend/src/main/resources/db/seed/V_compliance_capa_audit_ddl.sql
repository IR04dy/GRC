-- ============================================================================
-- TSS GRC — Compliance: CAPA closure verification + External audit lifecycle
-- ============================================================================
-- Buyer-assessment §5.3 recommendations:
--   #3 External audit lifecycle  — new audit_engagement table
--   #6 CAPA closure verification — extend compliance.findings with owner-test
--                                   + independent-test + audit-signoff fields
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── #6 CAPA closure verification — extend compliance.findings ──────────────
IF COL_LENGTH('compliance.findings', 'owner_test_at')             IS NULL
    ALTER TABLE compliance.findings ADD owner_test_at DATETIME2 NULL;
IF COL_LENGTH('compliance.findings', 'owner_test_result')         IS NULL
    ALTER TABLE compliance.findings ADD owner_test_result NVARCHAR(20) NULL;       -- PASS|FAIL|PARTIAL
IF COL_LENGTH('compliance.findings', 'owner_test_evidence_id')    IS NULL
    ALTER TABLE compliance.findings ADD owner_test_evidence_id BIGINT NULL;
IF COL_LENGTH('compliance.findings', 'owner_test_notes')          IS NULL
    ALTER TABLE compliance.findings ADD owner_test_notes NVARCHAR(MAX) NULL;
IF COL_LENGTH('compliance.findings', 'independent_test_at')       IS NULL
    ALTER TABLE compliance.findings ADD independent_test_at DATETIME2 NULL;
IF COL_LENGTH('compliance.findings', 'independent_test_by_id')    IS NULL
    ALTER TABLE compliance.findings ADD independent_test_by_id BIGINT NULL;
IF COL_LENGTH('compliance.findings', 'independent_test_result')   IS NULL
    ALTER TABLE compliance.findings ADD independent_test_result NVARCHAR(20) NULL; -- PASS|FAIL|PARTIAL
IF COL_LENGTH('compliance.findings', 'independent_test_notes')    IS NULL
    ALTER TABLE compliance.findings ADD independent_test_notes NVARCHAR(MAX) NULL;
IF COL_LENGTH('compliance.findings', 'audit_signoff_at')          IS NULL
    ALTER TABLE compliance.findings ADD audit_signoff_at DATETIME2 NULL;
IF COL_LENGTH('compliance.findings', 'audit_signoff_by_id')       IS NULL
    ALTER TABLE compliance.findings ADD audit_signoff_by_id BIGINT NULL;
IF COL_LENGTH('compliance.findings', 'audit_signoff_notes')       IS NULL
    ALTER TABLE compliance.findings ADD audit_signoff_notes NVARCHAR(MAX) NULL;
IF COL_LENGTH('compliance.findings', 'capa_stage')                IS NULL
    ALTER TABLE compliance.findings ADD capa_stage NVARCHAR(40) NOT NULL CONSTRAINT DF_finding_capa_stage DEFAULT N'IDENTIFIED';
    -- IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED → AUDIT_SIGNED_OFF → CLOSED

PRINT N'CAPA closure verification columns ensured on compliance.findings';

-- ── #3 External audit lifecycle — new audit_engagement table ───────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='compliance' AND t.name='audit_engagements')
BEGIN
    CREATE TABLE compliance.audit_engagements (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_aeng_extid DEFAULT NEWID(),
        organisation_id       BIGINT NOT NULL,
        engagement_code       NVARCHAR(50) NULL,
        title_en              NVARCHAR(500) NOT NULL,
        title_ar              NVARCHAR(500) NULL,
        audit_type            NVARCHAR(30) NOT NULL,    -- INTERNAL | EXTERNAL | REGULATORY | CERTIFICATION
        framework             NVARCHAR(100) NULL,       -- ISO 27001 | NCA ECC | SAMA CSF | ZATCA | PDPL | …
        auditor_firm          NVARCHAR(255) NULL,
        lead_auditor          NVARCHAR(255) NULL,
        engagement_letter_date DATE NULL,
        opening_meeting_date  DATE NULL,
        fieldwork_start_date  DATE NULL,
        fieldwork_end_date    DATE NULL,
        report_issued_date    DATE NULL,
        closure_date          DATE NULL,
        next_followup_date    DATE NULL,
        scope_summary         NVARCHAR(MAX) NULL,
        status                NVARCHAR(40) NOT NULL CONSTRAINT DF_aeng_status DEFAULT N'PLANNED',
        -- PLANNED → ENGAGED → OPENING_MEETING → FIELDWORK → REPORTING → MGMT_RESPONSE → CLOSED → FOLLOW_UP
        findings_count        INT NOT NULL CONSTRAINT DF_aeng_fcount DEFAULT 0,
        is_deleted            BIT NOT NULL CONSTRAINT DF_aeng_isdel DEFAULT 0,
        created_at            DATETIME2 NOT NULL CONSTRAINT DF_aeng_created DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2 NULL,
        created_by            BIGINT NULL,
        updated_by            BIGINT NULL
    );
    CREATE INDEX IX_aeng_org_status ON compliance.audit_engagements(organisation_id, status) WHERE is_deleted = 0;
    PRINT N'Created compliance.audit_engagements';
END
ELSE PRINT N'compliance.audit_engagements already exists';

-- ── Seed: 3 representative audit engagements ───────────────────────────────
INSERT INTO compliance.audit_engagements
  (organisation_id, engagement_code, title_en, title_ar, audit_type, framework, auditor_firm, lead_auditor,
   engagement_letter_date, opening_meeting_date, fieldwork_start_date, fieldwork_end_date, report_issued_date,
   scope_summary, status)
SELECT v.org, v.code, v.en, v.ar, v.atype, v.fw, v.firm, v.lead,
       v.eldate, v.omdate, v.fwstart, v.fwend, v.rdate, v.scope, v.st
FROM (VALUES
  (1, N'AUD-2025-001',
   N'ISO 27001:2022 Surveillance Audit — Cycle 2',
   N'تدقيق المتابعة وفق ISO 27001:2022 — الدورة الثانية',
   N'CERTIFICATION', N'ISO 27001:2022', N'BSI Saudi Arabia', N'Lead: Dr. Khalid Al-Otaibi',
   '2025-02-10', '2025-03-15', '2025-03-15', '2025-03-26', '2025-04-08',
   N'Surveillance audit of the ISMS scope: information processing facilities, all KSA-resident production environments, and the Riyadh + Jeddah head offices.',
   N'CLOSED'),
  (1, N'AUD-2025-002',
   N'NCA ECC v2.0 Compliance Assessment',
   N'تقييم الامتثال للضوابط الأساسية للأمن السيبراني',
   N'REGULATORY', N'NCA ECC v2.0', N'Internal Audit Function', N'Lead: Internal Audit',
   '2025-04-01', '2025-04-15', '2025-04-15', '2025-05-30', NULL,
   N'Annual NCA ECC compliance self-assessment. 114 controls across 5 main domains, scored against maturity model.',
   N'FIELDWORK'),
  (1, N'AUD-2025-003',
   N'SAMA Cybersecurity Framework — Quarterly Review',
   N'إطار الأمن السيبراني — المراجعة الربعية',
   N'REGULATORY', N'SAMA CSF v1.0', N'Internal Audit Function', N'Lead: Internal Audit',
   '2025-04-01', NULL, NULL, NULL, NULL,
   N'SAMA CSF Q2 2025 control attestation. Required quarterly under SAMA banking regulation.',
   N'PLANNED')
) AS v(org, code, en, ar, atype, fw, firm, lead, eldate, omdate, fwstart, fwend, rdate, scope, st)
WHERE NOT EXISTS (SELECT 1 FROM compliance.audit_engagements e WHERE e.organisation_id=v.org AND e.engagement_code=v.code AND e.is_deleted=0);

PRINT N'Seeded 3 sample audit engagements.';

-- ── Result summary ────────────────────────────────────────────────────────
SELECT
    N'Compliance CAPA + audit-engagement DDL complete' AS status,
    (SELECT COUNT(*) FROM compliance.audit_engagements WHERE is_deleted=0) AS engagements,
    (SELECT COUNT(*) FROM compliance.findings WHERE is_deleted=0) AS findings;

GO

-- ============================================================================
-- TSS GRC — Evidence module v2: chain-of-custody, version control, connector library
-- ============================================================================
-- Implements buyer-assessment §5.9 recommendations:
--   #1 Version control with diff      — new evidence.evidence_versions table
--   #2 Connector library MVP          — new evidence.connector_definitions + seed
--   #3 Chain-of-custody fields        — added to evidence.evidence_items
--   #4 WORM / hash chain foundation   — previous_hash + merkle_anchor on items
--   #5 Freshness engine               — relies on existing valid_to + new
--                                       review_due_date column
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

-- ── Extend evidence.evidence_items with chain-of-custody + version fields ──
IF COL_LENGTH('evidence.evidence_items', 'collection_method') IS NULL
    ALTER TABLE evidence.evidence_items ADD collection_method NVARCHAR(50) NULL;       -- MANUAL, AGENT, API_PULL, SIEM, SCANNER, NAFATH_SIGNED
IF COL_LENGTH('evidence.evidence_items', 'collector_name')    IS NULL
    ALTER TABLE evidence.evidence_items ADD collector_name NVARCHAR(255) NULL;
IF COL_LENGTH('evidence.evidence_items', 'collector_id')      IS NULL
    ALTER TABLE evidence.evidence_items ADD collector_id BIGINT NULL;
IF COL_LENGTH('evidence.evidence_items', 'witness_id')        IS NULL
    ALTER TABLE evidence.evidence_items ADD witness_id BIGINT NULL;
IF COL_LENGTH('evidence.evidence_items', 'tool_version')      IS NULL
    ALTER TABLE evidence.evidence_items ADD tool_version NVARCHAR(100) NULL;
IF COL_LENGTH('evidence.evidence_items', 'evidence_hash')     IS NULL
    ALTER TABLE evidence.evidence_items ADD evidence_hash NVARCHAR(128) NULL;          -- SHA-256 hex
IF COL_LENGTH('evidence.evidence_items', 'previous_hash')     IS NULL
    ALTER TABLE evidence.evidence_items ADD previous_hash NVARCHAR(128) NULL;          -- WORM hash chain
IF COL_LENGTH('evidence.evidence_items', 'merkle_anchor')     IS NULL
    ALTER TABLE evidence.evidence_items ADD merkle_anchor NVARCHAR(255) NULL;          -- external timestamp authority anchor
IF COL_LENGTH('evidence.evidence_items', 'collected_at')      IS NULL
    ALTER TABLE evidence.evidence_items ADD collected_at DATETIME2 NULL;
IF COL_LENGTH('evidence.evidence_items', 'review_due_date')   IS NULL
    ALTER TABLE evidence.evidence_items ADD review_due_date DATE NULL;
IF COL_LENGTH('evidence.evidence_items', 'connector_code')    IS NULL
    ALTER TABLE evidence.evidence_items ADD connector_code NVARCHAR(50) NULL;          -- which connector produced this evidence
IF COL_LENGTH('evidence.evidence_items', 'current_version_no') IS NULL
    ALTER TABLE evidence.evidence_items ADD current_version_no INT NOT NULL CONSTRAINT DF_evidence_curver DEFAULT 1;

PRINT N'Chain-of-custody columns ensured on evidence.evidence_items';

-- ── New table: evidence.evidence_versions (recommendation #1) ──────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'evidence' AND t.name = 'evidence_versions')
BEGIN
    CREATE TABLE evidence.evidence_versions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        evidence_id        BIGINT NOT NULL,
        version_no         INT NOT NULL,
        title_en           NVARCHAR(300) NULL,
        title_ar           NVARCHAR(300) NULL,
        description_en     NVARCHAR(MAX) NULL,
        evidence_hash      NVARCHAR(128) NULL,
        previous_hash      NVARCHAR(128) NULL,
        file_name          NVARCHAR(500) NULL,
        file_path          NVARCHAR(1000) NULL,
        file_size          BIGINT NULL,
        change_summary     NVARCHAR(1000) NULL,
        snapshotted_at     DATETIME2 NOT NULL CONSTRAINT DF_evver_snap DEFAULT SYSUTCDATETIME(),
        snapshotted_by     BIGINT NULL,
        CONSTRAINT FK_evver_evidence FOREIGN KEY (evidence_id) REFERENCES evidence.evidence_items(id),
        CONSTRAINT UQ_evver UNIQUE (evidence_id, version_no)
    );

    CREATE INDEX IX_evver_evidence ON evidence.evidence_versions(evidence_id);
    PRINT N'Created evidence.evidence_versions';
END
ELSE
    PRINT N'evidence.evidence_versions already exists';

-- ── New table: evidence.connector_definitions (recommendation #2) ──────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'evidence' AND t.name = 'connector_definitions')
BEGIN
    CREATE TABLE evidence.connector_definitions (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_conn_extid DEFAULT NEWID(),
        organisation_id    BIGINT NOT NULL,
        code               NVARCHAR(50) NOT NULL,
        name_en            NVARCHAR(255) NOT NULL,
        name_ar            NVARCHAR(255) NULL,
        connector_type     NVARCHAR(50) NOT NULL,                                       -- IDP, EDR, CLOUD_SECURITY, VULN_SCANNER, SIEM, THREAT_INTEL, OTHER
        vendor             NVARCHAR(100) NULL,
        description_en     NVARCHAR(MAX) NULL,
        config_schema_json NVARCHAR(MAX) NULL,                                          -- JSON schema for required configuration
        collection_plan_en NVARCHAR(MAX) NULL,                                          -- Documents which controls this connector helps satisfy
        target_frameworks  NVARCHAR(500) NULL,                                          -- CSV of framework codes the connector serves
        status             NVARCHAR(30) NOT NULL CONSTRAINT DF_conn_status DEFAULT 'AVAILABLE',  -- AVAILABLE, CONFIGURED, RUNNING, DISABLED
        last_run_at        DATETIME2 NULL,
        last_run_status    NVARCHAR(30) NULL,
        last_run_summary   NVARCHAR(MAX) NULL,
        evidence_collected_count INT NOT NULL CONSTRAINT DF_conn_collected DEFAULT 0,
        is_deleted         BIT NOT NULL CONSTRAINT DF_conn_isdel DEFAULT 0,
        created_at         DATETIME2 NOT NULL CONSTRAINT DF_conn_created DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2 NULL,
        CONSTRAINT UQ_conn_org_code UNIQUE (organisation_id, code)
    );

    PRINT N'Created evidence.connector_definitions';
END
ELSE
    PRINT N'evidence.connector_definitions already exists';

-- ── Seed: 4 connector stubs (recommendation #2 MVP) ────────────────────────
INSERT INTO evidence.connector_definitions (organisation_id, code, name_en, name_ar, connector_type, vendor, description_en, collection_plan_en, target_frameworks, status)
SELECT v.org, v.code, v.en, v.ar, v.ctype, v.vendor, v.descr, v.plan_text, v.tfw, 'AVAILABLE'
FROM (VALUES
  (1, N'AD_GROUPS',
   N'Active Directory — Group Membership',
   N'الدليل النشط — عضويات المجموعات',
   N'IDP', N'Microsoft',
   N'Pulls Active Directory group membership snapshots on a configurable cadence (default: weekly). Produces typed evidence per privileged-access group.',
   N'Satisfies: NCA ECC ECC-2-2 (Identity & Access Management), ISO 27001 A.5.18 (Access Rights), SAMA CSF 3.2. Each weekly run produces one evidence item per privileged group with the membership list as JSON attachment + SHA-256 hash. Collected evidence is automatically version-bumped if membership has changed since last run.',
   N'NCA_ECC,ISO_27001,SAMA_CSF'),
  (1, N'DEFENDER_ALERTS',
   N'Microsoft Defender — Alert Export',
   N'مايكروسوفت ديفندر — تصدير التنبيهات',
   N'EDR', N'Microsoft',
   N'Pulls Defender for Endpoint and Defender for Cloud alerts on a daily cadence. Produces evidence packages of alert volumes by severity for the trailing 30/60/90 days.',
   N'Satisfies: NCA ECC ECC-2-12 (Event Logs and Monitoring), ISO 27001 A.8.16 (Monitoring), NIST CSF 2.0 DE.CM-1.',
   N'NCA_ECC,ISO_27001,NIST_CSF'),
  (1, N'AWS_CONFIG',
   N'AWS Config — Compliance Snapshot',
   N'AWS Config — لقطة الامتثال',
   N'CLOUD_SECURITY', N'AWS',
   N'Pulls AWS Config compliance snapshots on a weekly cadence — flags non-compliant resources against the AWS Foundational Security Best Practices conformance pack and any custom packs configured.',
   N'Satisfies: NCA CCC v1.0 (Cloud Cybersecurity Controls), CSA CCM, ISO 27017. Each run produces one evidence item per conformance pack with the compliance summary + non-compliant resource list.',
   N'NCA_CCC,ISO_27001'),
  (1, N'NCA_HEY_THREAT_INTEL',
   N'NCA HEY — Saudi Threat Intelligence Feed',
   N'هيئة الأمن السيبراني — تغذية معلومات التهديد',
   N'THREAT_INTEL', N'NCA (Saudi)',
   N'Subscribes to the NCA HEY threat-intel feed (Saudi-only) and matches incoming indicators against the asset inventory. Produces evidence of intel ingestion + match results for each ingestion cycle.',
   N'Satisfies: NCA ECC ECC-2-13 (Cybersecurity Incident and Threat Management), SAMA Cyber Threat Intelligence Sharing requirement.',
   N'NCA_ECC,SAMA_CSF')
) AS v(org, code, en, ar, ctype, vendor, descr, plan_text, tfw)
WHERE NOT EXISTS (SELECT 1 FROM evidence.connector_definitions c
                  WHERE c.organisation_id = v.org AND c.code = v.code AND c.is_deleted = 0);

PRINT N'Connector library MVP — 4 connectors seeded.';

-- ── Backfill: snapshot existing evidence items into v1 of evidence_versions ─
INSERT INTO evidence.evidence_versions (evidence_id, version_no, title_en, title_ar, description_en, file_name, file_path, file_size, change_summary, snapshotted_at)
SELECT e.id, 1, e.title_en, e.title_ar, e.description_en, e.file_name, e.file_path, e.file_size, N'Initial version (backfilled)', SYSUTCDATETIME()
FROM evidence.evidence_items e
WHERE e.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.evidence_versions v WHERE v.evidence_id = e.id);

PRINT N'Backfilled v1 snapshots into evidence_versions for existing items.';

-- ── Result summary ─────────────────────────────────────────────────────────
SELECT
  N'Evidence v2 DDL run complete' AS status,
  (SELECT COUNT(*) FROM evidence.evidence_versions)   AS evidence_versions,
  (SELECT COUNT(*) FROM evidence.connector_definitions WHERE is_deleted = 0) AS connectors_seeded;

GO

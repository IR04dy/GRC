-- V150: SOC Operations Foundation — Tier-1 backbone for the platform's
--       transition from "Chief Risk Officer's tool" to "Chief Information
--       Security Officer's tool".
--
-- Closes the gaps a Saudi CISO can't run real operations without:
--   • Live security telemetry (SIEM-ingested events with auto-incident creation)
--   • Threat intelligence feed (Saudi-CERT advisories, NCA bulletins, sector ISACs)
--   • Vulnerability scanner ingest (Tenable / Qualys / Defender feeds)
--   • IR playbook library (phishing, ransomware, BEC, DDoS, insider, BCP-activation)
--   • Penetration test engagement tracker (SAMA examiners ask for the full PT history)
--   • Security awareness campaign tracker (phishing simulations + training)
--   • Cryptographic asset inventory (certs/keys/HSMs with expiry alerts)
--   • SBOM components (NCA & CNI requirement)
--   • Zero Trust maturity scorecard (NCA published direction)
--   • Regulator examination tracker (SAMA on-site exams + NCA inspections)
--   • Seasonal risk overlay (Hajj/Umrah/Ramadan DDoS+phishing windows)
--
-- All tables follow the existing platform conventions:
--   - organisation_id FK for multi-tenancy
--   - is_deleted/deleted_at/deleted_by for soft-delete
--   - created_at/updated_at/created_by/updated_by audit columns
--   - row_version (rowversion) for optimistic concurrency
--   - bilingual title_en/title_ar where user-visible

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Schema
-- ════════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'socops')
    EXEC('CREATE SCHEMA socops');
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.security_event
--   SIEM-ingested security events. Each row is an event the SOC needs to
--   triage — most arrive via the /api/v1/socops/siem-events webhook from
--   Splunk/QRadar/Sentinel/Logpoint. High-severity events auto-create
--   incidents downstream (handled by the service layer, not by trigger).
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.security_event (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(64)    NOT NULL,                       -- SIEM event ID
    source_system         NVARCHAR(60)    NOT NULL,                       -- 'SPLUNK' | 'QRADAR' | 'SENTINEL' | 'WEBHOOK'
    event_time            DATETIME2       NOT NULL,
    ingested_at           DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    severity              NVARCHAR(20)    NOT NULL,                       -- CRITICAL/HIGH/MEDIUM/LOW/INFO
    category              NVARCHAR(60),                                   -- e.g. 'MALWARE', 'AUTH_FAILURE', 'DLP', 'EXFIL'
    rule_name             NVARCHAR(300),                                  -- correlation rule that fired
    source_host           NVARCHAR(255),
    source_ip             NVARCHAR(45),
    destination_host      NVARCHAR(255),
    destination_ip        NVARCHAR(45),
    username              NVARCHAR(255),
    asset_id              BIGINT,                                         -- soft FK to cyber.cyber_assets.id
    raw_payload           NVARCHAR(MAX),                                  -- JSON of the original alert
    status                NVARCHAR(30)    NOT NULL DEFAULT 'NEW',         -- NEW/TRIAGED/IN_ANALYSIS/CONTAINED/RESOLVED/FALSE_POSITIVE
    assigned_to_user_id   BIGINT,
    incident_id           BIGINT,                                         -- soft FK if auto-promoted to incident
    triaged_at            DATETIME2,
    resolved_at           DATETIME2,
    sla_due_at            DATETIME2,                                      -- NCA ECC-2 SLA — Critical 1h, High 4h, Medium 24h
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_security_event_external UNIQUE (organisation_id, source_system, external_id)
);
CREATE INDEX ix_security_event_org_status ON socops.security_event(organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_security_event_org_event_time ON socops.security_event(organisation_id, event_time DESC) WHERE is_deleted = 0;
CREATE INDEX ix_security_event_org_severity ON socops.security_event(organisation_id, severity) WHERE is_deleted = 0;
CREATE INDEX ix_security_event_sla_due ON socops.security_event(organisation_id, sla_due_at) WHERE is_deleted = 0 AND status <> 'RESOLVED' AND status <> 'FALSE_POSITIVE';
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.threat_intel
--   Threat intelligence records — advisories, IOCs, TTPs. Sourced from
--   Saudi-CERT, NCA bulletins, sector ISACs (FS-ISAC for banking), MISP,
--   or commercial feeds (Recorded Future / Mandiant).
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.threat_intel (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(120)   NOT NULL,                       -- feed-specific ID
    source                NVARCHAR(60)    NOT NULL,                       -- 'SAUDI_CERT' | 'NCA' | 'FS_ISAC' | 'MISP' | 'COMMERCIAL'
    published_at          DATETIME2       NOT NULL,
    expires_at            DATETIME2,                                      -- IOC freshness window
    title_en              NVARCHAR(500)   NOT NULL,
    title_ar              NVARCHAR(500),
    summary               NVARCHAR(MAX),
    ioc_type              NVARCHAR(40),                                   -- 'DOMAIN' | 'IP' | 'HASH' | 'EMAIL' | 'URL' | 'CVE'
    ioc_value             NVARCHAR(500),
    severity              NVARCHAR(20),                                   -- CRITICAL/HIGH/MEDIUM/LOW
    confidence            NVARCHAR(20),                                   -- HIGH/MEDIUM/LOW
    tlp                   NVARCHAR(10),                                   -- WHITE/GREEN/AMBER/RED (Traffic Light Protocol)
    mitre_tactics         NVARCHAR(500),                                  -- comma-sep ATT&CK tactic IDs
    mitre_techniques      NVARCHAR(500),                                  -- comma-sep ATT&CK technique IDs
    affected_sectors      NVARCHAR(500),                                  -- comma-sep — 'BANKING', 'ENERGY', 'HEALTHCARE', 'GOVT'
    actioned              BIT             NOT NULL DEFAULT 0,
    actioned_at           DATETIME2,
    actioned_by_user_id   BIGINT,
    action_notes          NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_threat_intel_external UNIQUE (organisation_id, source, external_id)
);
CREATE INDEX ix_threat_intel_org_published ON socops.threat_intel(organisation_id, published_at DESC) WHERE is_deleted = 0;
CREATE INDEX ix_threat_intel_ioc ON socops.threat_intel(organisation_id, ioc_value) WHERE is_deleted = 0 AND ioc_value IS NOT NULL;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.ir_playbook
--   Incident response runbook header. Each playbook is a named procedure
--   the SOC follows when a specific kind of event fires (phishing, ransomware,
--   BEC, DDoS, insider, BCP-activation). Versioned so updates don't lose
--   prior execution history.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.ir_playbook (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    playbook_code         NVARCHAR(40)    NOT NULL,                       -- e.g. 'IR-PHISHING-01'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    description_en        NVARCHAR(MAX),
    description_ar        NVARCHAR(MAX),
    category              NVARCHAR(60)    NOT NULL,                       -- PHISHING/RANSOMWARE/BEC/DDOS/INSIDER/BCP_ACTIVATION/DATA_BREACH/CLOUD_BREACH/OT_INCIDENT
    severity_trigger      NVARCHAR(20),                                   -- minimum severity to invoke this playbook
    target_mttd_minutes   INT,                                            -- mean-time-to-detect target
    target_mttr_minutes   INT,                                            -- mean-time-to-respond target
    aligned_frameworks    NVARCHAR(500),                                  -- 'NCA_ECC,SAMA_CSF,ISO_27035,NIST_800_61'
    status                NVARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED/DRAFT
    version               INT             NOT NULL DEFAULT 1,
    last_reviewed_at      DATETIME2,
    last_reviewed_by      NVARCHAR(255),
    invocation_count      INT             NOT NULL DEFAULT 0,             -- denormalised: how many times this playbook was invoked
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_ir_playbook_code UNIQUE (organisation_id, playbook_code)
);
CREATE INDEX ix_ir_playbook_org_cat ON socops.ir_playbook(organisation_id, category) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.ir_playbook_step
--   Ordered steps within a playbook. The frontend renders these as a
--   checklist; analysts mark each step as the incident progresses.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.ir_playbook_step (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    playbook_id           BIGINT          NOT NULL,
    step_order            INT             NOT NULL,
    phase                 NVARCHAR(40)    NOT NULL,                       -- DETECT/TRIAGE/CONTAIN/ERADICATE/RECOVER/LESSONS_LEARNED (NIST SP 800-61)
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    instructions_en       NVARCHAR(MAX),
    instructions_ar       NVARCHAR(MAX),
    responsible_role      NVARCHAR(120),                                  -- 'SOC_ANALYST_L1' | 'SOC_ANALYST_L2' | 'IR_LEAD' | 'CISO'
    estimated_minutes     INT,
    is_critical           BIT             NOT NULL DEFAULT 0,             -- if true, step cannot be skipped
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT fk_ir_step_playbook FOREIGN KEY (playbook_id) REFERENCES socops.ir_playbook(id),
    CONSTRAINT uq_ir_step_order UNIQUE (playbook_id, step_order)
);
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.scanner_run
--   Vulnerability scanner ingest run — one row per scan execution.
--   The detailed findings are written into the existing cyber vulnerability
--   tables; this row is the "ingest receipt" + summary stats.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.scanner_run (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    scanner               NVARCHAR(40)    NOT NULL,                       -- 'TENABLE' | 'QUALYS' | 'DEFENDER' | 'RAPID7' | 'WIZ' | 'PRISMA_CLOUD' | 'OTHER'
    scan_name             NVARCHAR(300),
    scope                 NVARCHAR(MAX),                                  -- which assets/subnets the scan covered
    started_at            DATETIME2       NOT NULL,
    completed_at          DATETIME2,
    status                NVARCHAR(30)    NOT NULL DEFAULT 'RUNNING',     -- RUNNING/COMPLETED/FAILED/CANCELLED
    findings_total        INT             NOT NULL DEFAULT 0,
    findings_critical     INT             NOT NULL DEFAULT 0,
    findings_high         INT             NOT NULL DEFAULT 0,
    findings_medium       INT             NOT NULL DEFAULT 0,
    findings_low          INT             NOT NULL DEFAULT 0,
    assets_scanned        INT             NOT NULL DEFAULT 0,
    ingested_at           DATETIME2,
    raw_payload           NVARCHAR(MAX),                                  -- summary JSON the scanner returned
    error_message         NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL
);
CREATE INDEX ix_scanner_run_org_started ON socops.scanner_run(organisation_id, started_at DESC) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.pentest_engagement
--   Penetration test engagement tracker. SAMA examiners ask for the full
--   PT history in every inspection — this is the table they query.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.pentest_engagement (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    engagement_code       NVARCHAR(40)    NOT NULL,                       -- e.g. 'PT-2026-Q2-001'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    pentest_type          NVARCHAR(40)    NOT NULL,                       -- 'EXTERNAL' | 'INTERNAL' | 'WEB_APP' | 'MOBILE' | 'CLOUD' | 'OT' | 'RED_TEAM' | 'PURPLE_TEAM'
    scope                 NVARCHAR(MAX),                                  -- in-scope assets/URLs/subnets
    rules_of_engagement   NVARCHAR(MAX),
    vendor_name           NVARCHAR(255),
    vendor_lead_contact   NVARCHAR(255),
    nca_certified_vendor  BIT             NOT NULL DEFAULT 0,             -- NCA maintains an authorised PT vendor list
    started_at            DATETIME2,
    completed_at          DATETIME2,
    status                NVARCHAR(30)    NOT NULL DEFAULT 'SCOPING',     -- SCOPING/IN_PROGRESS/REPORT_PENDING/REPORT_DELIVERED/REMEDIATION/RETEST/CLOSED
    findings_total        INT             NOT NULL DEFAULT 0,
    findings_critical     INT             NOT NULL DEFAULT 0,
    findings_high         INT             NOT NULL DEFAULT 0,
    retest_due_at         DATETIME2,
    retest_completed_at   DATETIME2,
    report_artefact_id    BIGINT,                                         -- soft FK to evidence_items
    cost_sar              DECIMAL(18, 2),                                 -- contract value in SAR
    aligned_frameworks    NVARCHAR(500),                                  -- 'NCA_ECC,SAMA_CSF,PCI_DSS'
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_pentest_code UNIQUE (organisation_id, engagement_code)
);
CREATE INDEX ix_pentest_org_status ON socops.pentest_engagement(organisation_id, status) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.awareness_campaign
--   Security awareness campaigns (phishing simulations + training pushes).
--   NCA ECC 1-5-x compliance — every regulated entity must run a programme.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.awareness_campaign (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    campaign_code         NVARCHAR(40)    NOT NULL,
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    campaign_type         NVARCHAR(40)    NOT NULL,                       -- 'PHISHING_SIM' | 'TRAINING' | 'AWARENESS_MESSAGE' | 'WORKSHOP' | 'TABLETOP'
    target_audience       NVARCHAR(500),                                  -- 'ALL_STAFF' | 'PRIVILEGED_USERS' | dept code
    target_user_count     INT             NOT NULL DEFAULT 0,
    started_at            DATETIME2,
    ended_at              DATETIME2,
    status                NVARCHAR(30)    NOT NULL DEFAULT 'PLANNED',     -- PLANNED/RUNNING/COMPLETED/CANCELLED
    delivered_count       INT             NOT NULL DEFAULT 0,
    opened_count          INT             NOT NULL DEFAULT 0,
    clicked_count         INT             NOT NULL DEFAULT 0,             -- phishing clicks (lower is better)
    reported_count        INT             NOT NULL DEFAULT 0,             -- users who reported the phish
    completed_count       INT             NOT NULL DEFAULT 0,             -- training completion
    repeat_offender_count INT             NOT NULL DEFAULT 0,             -- users who clicked AGAIN after prior sim
    pass_rate_percent     DECIMAL(5, 2),
    aligned_controls      NVARCHAR(500),                                  -- which controls this evidences
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_awareness_code UNIQUE (organisation_id, campaign_code)
);
CREATE INDEX ix_awareness_org_status ON socops.awareness_campaign(organisation_id, status) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.crypto_asset
--   Cryptographic asset inventory — TLS/SSL certs, signing certs, HSMs,
--   service-account keys, KMS-managed keys. Drives the cert-expiry alert
--   workflow (NCA + SAMA both flag expired certs as control deficiencies).
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.crypto_asset (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    asset_code            NVARCHAR(80)    NOT NULL,
    asset_type            NVARCHAR(40)    NOT NULL,                       -- 'TLS_CERT' | 'SIGNING_CERT' | 'CODE_SIGNING' | 'SSH_KEY' | 'SERVICE_KEY' | 'KMS_KEY' | 'HSM_KEY' | 'PGP_KEY'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    issued_by             NVARCHAR(255),                                  -- CA or KMS
    subject_dn            NVARCHAR(500),
    fingerprint           NVARCHAR(255),
    algorithm             NVARCHAR(60),                                   -- e.g. 'RSA-2048', 'ECDSA-P256', 'AES-256'
    key_length            INT,
    quantum_safe          BIT             NOT NULL DEFAULT 0,             -- post-quantum readiness flag
    valid_from            DATETIME2,
    valid_until           DATETIME2,
    deployed_on           NVARCHAR(500),                                  -- hostname / service / endpoint
    owner                 NVARCHAR(255),
    rotation_period_days  INT,
    last_rotated_at       DATETIME2,
    classification        NVARCHAR(40),                                   -- per Saudi NDC: TOP_SECRET/SECRET/RESTRICTED/PUBLIC
    status                NVARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/EXPIRED/REVOKED/RETIRED/AT_RISK
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_crypto_asset_code UNIQUE (organisation_id, asset_code)
);
CREATE INDEX ix_crypto_asset_org_status ON socops.crypto_asset(organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_crypto_asset_org_valid ON socops.crypto_asset(organisation_id, valid_until) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.sbom_component
--   Software Bill of Materials — flattened component inventory across
--   all deployed applications. NCA requirement for critical infrastructure.
--   CISA SBOM-Sharing-Lifecycle aligned.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.sbom_component (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    application_id        BIGINT,                                         -- soft FK if this app is tracked in assets
    application_name      NVARCHAR(300)   NOT NULL,
    component_name        NVARCHAR(300)   NOT NULL,
    component_version     NVARCHAR(120),
    package_ecosystem     NVARCHAR(40),                                   -- 'NPM' | 'MAVEN' | 'PYPI' | 'NUGET' | 'GO' | 'CARGO' | 'OS_RPM' | 'OS_DEB'
    purl                  NVARCHAR(500),                                  -- Package URL spec
    license               NVARCHAR(120),
    supplier              NVARCHAR(300),
    has_known_cves        BIT             NOT NULL DEFAULT 0,
    highest_cve_severity  NVARCHAR(20),
    upgrade_available     BIT             NOT NULL DEFAULT 0,
    last_scanned_at       DATETIME2,
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL
);
CREATE INDEX ix_sbom_org_app ON socops.sbom_component(organisation_id, application_name) WHERE is_deleted = 0;
CREATE INDEX ix_sbom_has_cves ON socops.sbom_component(organisation_id, has_known_cves) WHERE is_deleted = 0 AND has_known_cves = 1;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.zt_maturity
--   Zero Trust maturity assessment. CISA ZTMM pillars, NCA-published
--   direction. One row per pillar per assessment cycle.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.zt_maturity (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    assessment_code       NVARCHAR(40)    NOT NULL,                       -- which cycle (e.g. '2026-H1')
    pillar                NVARCHAR(40)    NOT NULL,                       -- IDENTITY/DEVICES/NETWORK/APPLICATIONS/DATA/VISIBILITY/AUTOMATION
    current_stage         NVARCHAR(20)    NOT NULL,                       -- TRADITIONAL/INITIAL/ADVANCED/OPTIMAL
    target_stage          NVARCHAR(20),
    score                 INT,                                            -- 0-100
    evidence_summary      NVARCHAR(MAX),
    gap_summary           NVARCHAR(MAX),
    action_plan           NVARCHAR(MAX),
    assessed_at           DATETIME2,
    assessed_by           NVARCHAR(255),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_zt_maturity UNIQUE (organisation_id, assessment_code, pillar)
);
CREATE INDEX ix_zt_maturity_org ON socops.zt_maturity(organisation_id, assessment_code) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.regulator_examination
--   On-site regulator examinations + remote inspections. SAMA, NCA, NDMO,
--   CITC, ZATCA, MoH/CCHI. One row per examination engagement; findings
--   live in the existing compliance.audit_findings table linked back here.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.regulator_examination (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    examination_code      NVARCHAR(40)    NOT NULL,                       -- e.g. 'SAMA-2026-CYBER-Q2'
    regulator             NVARCHAR(40)    NOT NULL,                       -- 'SAMA' | 'NCA' | 'NDMO' | 'CITC' | 'ZATCA' | 'CCHI' | 'CMA' | 'OTHER'
    examination_type      NVARCHAR(60)    NOT NULL,                       -- 'ON_SITE' | 'REMOTE' | 'QUESTIONNAIRE' | 'THEMATIC_REVIEW' | 'FOLLOW_UP'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    scope_areas           NVARCHAR(MAX),                                  -- which control families / business lines
    lead_examiner_name    NVARCHAR(255),
    notified_at           DATETIME2,                                      -- when the regulator gave us heads-up
    fieldwork_start_at    DATETIME2,
    fieldwork_end_at      DATETIME2,
    draft_report_at       DATETIME2,
    final_report_at       DATETIME2,
    response_due_at       DATETIME2,                                      -- our management response deadline
    response_submitted_at DATETIME2,
    closed_at             DATETIME2,
    status                NVARCHAR(30)    NOT NULL DEFAULT 'NOTIFIED',    -- NOTIFIED/SCOPING/FIELDWORK/DRAFT_REPORT/AWAITING_RESPONSE/REMEDIATION/CLOSED
    findings_total        INT             NOT NULL DEFAULT 0,
    findings_critical     INT             NOT NULL DEFAULT 0,
    findings_high         INT             NOT NULL DEFAULT 0,
    findings_med_low      INT             NOT NULL DEFAULT 0,
    overall_rating        NVARCHAR(40),                                   -- regulator-assigned rating
    related_framework     NVARCHAR(120),                                  -- 'NCA_ECC_2' / 'SAMA_CSF' / 'PDPL' etc.
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_reg_exam_code UNIQUE (organisation_id, examination_code)
);
CREATE INDEX ix_reg_exam_org_status ON socops.regulator_examination(organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_reg_exam_org_regulator ON socops.regulator_examination(organisation_id, regulator) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: socops.seasonal_overlay
--   Saudi-specific seasonal risk windows. During Hajj/Umrah/Ramadan and
--   national holidays the threat profile shifts (DDoS surge against payment
--   rails, phishing spikes targeting expats, e-payment fraud peaks). This
--   table holds the windows; the F8 automation engine consults it to tighten
--   KRI thresholds and SLAs during active overlays.
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE socops.seasonal_overlay (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    overlay_code          NVARCHAR(40)    NOT NULL,                       -- e.g. 'HAJJ-1447'
    title_en              NVARCHAR(200)   NOT NULL,
    title_ar              NVARCHAR(200),
    starts_at             DATETIME2       NOT NULL,                       -- Gregorian
    ends_at               DATETIME2       NOT NULL,
    hijri_label           NVARCHAR(60),                                   -- e.g. '1447/12/01 — 1447/12/13'
    threat_profile        NVARCHAR(MAX),                                  -- narrative — "DDoS surge against payment rails; phishing impersonating Tawakkalna/Nusuk apps"
    auto_tighten_kris     BIT             NOT NULL DEFAULT 1,             -- automation engine tightens KRI thresholds during this window
    auto_tighten_slas     BIT             NOT NULL DEFAULT 1,             -- automation engine tightens incident SLAs
    notify_recipients     NVARCHAR(MAX),                                  -- comma-sep emails or role codes
    is_active             BIT             NOT NULL DEFAULT 1,
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_seasonal_overlay_code UNIQUE (organisation_id, overlay_code)
);
CREATE INDEX ix_seasonal_org_active ON socops.seasonal_overlay(organisation_id, starts_at, ends_at) WHERE is_deleted = 0 AND is_active = 1;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Seed: 6 IR playbook templates as system defaults (organisation_id = 0
--       = "template"; service layer clones into a real org on first use).
--       Six is the floor a CISO needs: phishing (most common), ransomware
--       (highest impact), BEC (highest financial loss), DDoS (most visible),
--       insider threat (hardest to detect), BCP activation (binds to BCM).
-- ════════════════════════════════════════════════════════════════════════
INSERT INTO socops.ir_playbook
    (organisation_id, playbook_code, title_en, title_ar, description_en, description_ar,
     category, severity_trigger, target_mttd_minutes, target_mttr_minutes,
     aligned_frameworks, status, version)
VALUES
    (0, 'IR-PHISHING-01',
     'Phishing Response',
     'الاستجابة لرسائل التصيد',
     'Standard handling for a confirmed phishing report — from user click through containment and lessons learned. Covers credential reset, mailbox sweep, EDR isolation, and awareness follow-up.',
     'مسار معالجة بلاغ تصيد مؤكد، من النقرة حتى الاحتواء واستخلاص الدروس. يشمل إعادة تعيين بيانات الاعتماد، ومسح البريد، وعزل الجهاز عبر EDR، والمتابعة التوعوية.',
     'PHISHING', 'MEDIUM', 15, 120, 'NCA_ECC,SAMA_CSF,ISO_27035,NIST_800_61', 'ACTIVE', 1),
    (0, 'IR-RANSOMWARE-01',
     'Ransomware Response',
     'الاستجابة لبرامج الفدية',
     'Ransomware detected — isolate, preserve evidence, assess blast radius, decide on ransom-payment stance (default: no), notify NCA per ECC-2 critical-incident rules, initiate BCP and DR.',
     'تم رصد برنامج فدية — العزل، حفظ الأدلة، تقييم نطاق الضرر، اتخاذ قرار دفع الفدية (الافتراضي: لا)، إبلاغ الهيئة الوطنية للأمن السيبراني وفق ECC-2، وتفعيل خطة الاستمرارية والتعافي.',
     'RANSOMWARE', 'CRITICAL', 5, 60, 'NCA_ECC,SAMA_CSF,ISO_27035,NIST_800_61', 'ACTIVE', 1),
    (0, 'IR-BEC-01',
     'Business Email Compromise',
     'اختراق البريد الإلكتروني التجاري',
     'Suspected wire-fraud BEC — preserve account session data, force MFA reset, freeze pending transactions with bank, file SAMA STR if amounts exceed threshold, engage legal.',
     'احتيال تحويل مالي مشتبه به — حفظ بيانات الجلسة، إعادة تعيين MFA، تجميد المعاملات المعلقة مع البنك، إيداع تقرير STR لدى ساما عند تجاوز الحد، والتنسيق مع الإدارة القانونية.',
     'BEC', 'HIGH', 30, 240, 'NCA_ECC,SAMA_CSF,SAMA_AML', 'ACTIVE', 1),
    (0, 'IR-DDOS-01',
     'DDoS Response',
     'الاستجابة لهجمات الحرمان من الخدمة',
     'Active DDoS detected — engage upstream scrubbing provider, switch to anycast / CDN failover, monitor capacity headroom, communicate to customers via pre-approved holding statement.',
     'هجوم DDoS نشط — تفعيل خدمة التنقية لدى المزود، التحويل إلى CDN الاحتياطي، مراقبة سعة الاستيعاب، التواصل مع العملاء عبر البيان المعتمد مسبقاً.',
     'DDOS', 'HIGH', 5, 30, 'NCA_ECC,SAMA_CSF', 'ACTIVE', 1),
    (0, 'IR-INSIDER-01',
     'Insider Threat',
     'تهديد داخلي',
     'Suspected insider threat (data exfil, sabotage, policy violation) — engage HR + legal before any technical action, preserve user-activity logs, scope account access for revocation on dismissal.',
     'تهديد داخلي مشتبه (تسريب بيانات، تخريب، مخالفة سياسة) — التنسيق مع الموارد البشرية والإدارة القانونية قبل أي إجراء تقني، حفظ سجلات نشاط المستخدم، تحديد نطاق صلاحيات الحساب للإلغاء عند الإنهاء.',
     'INSIDER', 'HIGH', 60, 480, 'NCA_ECC,SAMA_CSF,ISO_27035', 'ACTIVE', 1),
    (0, 'IR-BCP-ACTIVATION-01',
     'BCP/DR Activation',
     'تفعيل خطة الاستمرارية والتعافي',
     'Major disruption triggering BCP/DR — bridge into the relevant plan from the BCM module, log activation timestamp, kick off crisis-room workflow, notify executive sponsor and SAMA/NCA per regulator playbook.',
     'انقطاع كبير يستدعي تفعيل BCP/DR — الانتقال إلى الخطة في وحدة استمرارية الأعمال، تسجيل توقيت التفعيل، فتح غرفة الأزمة، إبلاغ الراعي التنفيذي وساما/NCA وفق دليل الجهة التنظيمية.',
     'BCP_ACTIVATION', 'CRITICAL', 5, 30, 'NCA_ECC,SAMA_CSF,ISO_22301', 'ACTIVE', 1);
GO

-- Seed playbook steps for the phishing playbook (the template the others
-- follow). Six phases × 1-3 critical steps each = a usable runbook on day 1.
DECLARE @phishingId BIGINT = (SELECT id FROM socops.ir_playbook WHERE playbook_code = 'IR-PHISHING-01' AND organisation_id = 0);

INSERT INTO socops.ir_playbook_step
    (organisation_id, playbook_id, step_order, phase, title_en, title_ar,
     instructions_en, instructions_ar, responsible_role, estimated_minutes, is_critical)
VALUES
    (0, @phishingId,  1, 'DETECT',
     'Receive user report or SIEM alert', 'استلام البلاغ من المستخدم أو تنبيه SIEM',
     'Confirm report source. If user-reported, capture the original .eml file. If SIEM-detected, pull the correlation rule details and the affected mailbox.',
     'تأكد من مصدر البلاغ. إن كان من المستخدم، احفظ ملف .eml الأصلي. إن كان من SIEM، استخرج تفاصيل قاعدة الربط والصندوق المتأثر.',
     'SOC_ANALYST_L1', 5, 1),
    (0, @phishingId,  2, 'TRIAGE',
     'Identify all recipients of the same campaign', 'حدد جميع متلقي نفس الحملة',
     'Query the mail gateway for all messages matching sender + subject + URL. Build the impact list.',
     'استعلم بوابة البريد عن جميع الرسائل المطابقة (المرسل + الموضوع + الرابط). جهز قائمة المتأثرين.',
     'SOC_ANALYST_L2', 10, 1),
    (0, @phishingId,  3, 'TRIAGE',
     'Check if any user clicked the link', 'تأكد من وجود نقرات على الرابط',
     'Pull proxy/EDR logs for last 7 days against the phishing URL. Flag every user who connected.',
     'استخرج سجلات البروكسي/EDR لآخر 7 أيام بحثاً عن رابط التصيد. حدد كل من اتصل به.',
     'SOC_ANALYST_L2', 15, 1),
    (0, @phishingId,  4, 'CONTAIN',
     'Force credential reset for clickers', 'فرض إعادة تعيين بيانات الاعتماد للمتأثرين',
     'Through IAM, invalidate sessions + force password reset + force MFA re-enrolment for each clicker.',
     'عبر منظومة IAM: إنهاء الجلسات + فرض تغيير كلمة المرور + إعادة تسجيل MFA لكل متأثر.',
     'IR_LEAD', 20, 1),
    (0, @phishingId,  5, 'CONTAIN',
     'Remove the phish from all mailboxes', 'حذف الرسالة من جميع الصناديق',
     'Use the mail-gateway message-trace + remove function (or Exchange compliance search) to purge the message from every recipient mailbox.',
     'استخدم وظيفة تتبع وحذف الرسائل في بوابة البريد (أو بحث الامتثال في Exchange) لحذف الرسالة من كل صندوق متأثر.',
     'IR_LEAD', 15, 1),
    (0, @phishingId,  6, 'ERADICATE',
     'Block sender + URL + IP at perimeter', 'حظر المرسل والرابط وعنوان IP على المحيط',
     'Add to mail-gateway blocklist, proxy DNS sinkhole, firewall deny rules. Document indicators in the threat-intel module.',
     'أضف إلى قائمة الحظر في بوابة البريد، وإسناد DNS للبروكسي، وقواعد الرفض في الجدار الناري. وثّق المؤشرات في وحدة الاستخبارات.',
     'SOC_ANALYST_L2', 10, 1),
    (0, @phishingId,  7, 'RECOVER',
     'Verify clean state', 'تأكد من نظافة الوضع',
     'Re-scan affected endpoints. Validate no follow-on persistence (scheduled tasks, mail rules, OAuth grants).',
     'أعد فحص الأجهزة المتأثرة. تحقق من عدم وجود استمرارية (مهام مجدولة، قواعد بريد، تفويضات OAuth).',
     'SOC_ANALYST_L2', 30, 0),
    (0, @phishingId,  8, 'LESSONS_LEARNED',
     'Enrol clickers in targeted training', 'إلحاق المتأثرين ببرنامج تدريب مستهدف',
     'Push a remedial micro-training to every clicker. Track completion. Repeat offenders escalate to HR.',
     'أضف كل من نقر إلى تدريب توعوي مستهدف. تتبع الإتمام. إحالة المتكررين إلى الموارد البشرية.',
     'IR_LEAD', 15, 0),
    (0, @phishingId,  9, 'LESSONS_LEARNED',
     'Document AAR and update playbook', 'وثّق تقرير ما بعد الحدث وحدّث الدليل',
     'Write an After-Action Report in the BCM AAR module. Flag any playbook gap as a CAPA action.',
     'اكتب تقرير ما بعد الحدث في وحدة BCM AAR. سجل أي ثغرة في الدليل كإجراء تصحيحي.',
     'IR_LEAD', 30, 0);
GO

-- Seed the 2026 Saudi seasonal-overlay windows as system templates
-- (organisation_id = 0 — service-layer copies into a real org on first use).
-- Hijri ↔ Gregorian conversion is approximate; the platform service layer
-- recomputes from HijrahDate at runtime for any given org's actual cycle.
INSERT INTO socops.seasonal_overlay
    (organisation_id, overlay_code, title_en, title_ar, starts_at, ends_at,
     hijri_label, threat_profile, auto_tighten_kris, auto_tighten_slas, is_active)
VALUES
    (0, 'RAMADAN-1447',
     'Ramadan 1447',
     'رمضان 1447',
     '2026-02-17 00:00:00', '2026-03-19 23:59:59',
     '1447/09/01 — 1447/09/30',
     'Phishing spikes targeting expat remittance flows; payment-card fraud peaks; impersonation of charitable causes; Tawakkalna/Nusuk app fakes proliferate. Tighten DLP egress rules and DMARC enforcement.',
     1, 1, 1),
    (0, 'HAJJ-1447',
     'Hajj 1447',
     'حج 1447',
     '2026-05-26 00:00:00', '2026-06-08 23:59:59',
     '1447/12/01 — 1447/12/13',
     'DDoS surge against payment rails + ministry portals; impersonation of MoH/Nusuk/Tawakkalna for visa scams; surge in card-not-present fraud; OT systems at Hajj sites (transport, accommodation) at elevated risk.',
     1, 1, 1),
    (0, 'NATIONAL-DAY-1448',
     'National Day 1448 / 2026',
     'اليوم الوطني 1448 / 2026',
     '2026-09-23 00:00:00', '2026-09-25 23:59:59',
     '1448/03/12 — 1448/03/14',
     'Hacktivist DDoS / deface activity against gov-facing sites; phishing impersonating government offers; brand-impersonation around national-day promotions.',
     1, 0, 1);
GO

-- ════════════════════════════════════════════════════════════════════════
-- Module licensing — entitle the demo tenant (org=1) to the new SOCOPS
-- module so the platform-admin licensing page shows it in the list and
-- the JWT factory picks it up. Other tenants stay opt-in (LicenseGuard
-- denies until they're entitled by a platform admin).
-- ════════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses
               WHERE organisation_id = 1 AND module_code = 'SOCOPS')
BEGIN
    INSERT INTO platform.organisation_licenses
        (organisation_id, module_code, tier, enabled, notes)
    VALUES (1, 'SOCOPS', 'ENTERPRISE', 1,
            'SOC Operations — SIEM ingest, IR playbooks, vuln scanners, threat intel, pentests, awareness, crypto inventory, SBOM, Zero Trust, regulator exams (V150)');
END
GO

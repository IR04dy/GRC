-- V152: Tier-2 + Tier-3 expansion of SOC Operations — closes the remaining
--       CISO gaps identified in the platform's transition arc:
--
--   Tier 2 (operationally critical):
--     • OT/ICS asset inventory  — Aramco/SABIC/SEC scale, Purdue-aware
--     • IAM/PAM JML workflow    — joiner-mover-leaver + privileged events
--     • AppSec findings         — SAST/DAST/IAST/SCA from Veracode/Snyk/Checkmarx
--     • DLP events              — Microsoft Purview / Forcepoint / Symantec
--
--   Tier 3 (mature program needs):
--     • Cyber insurance tracker — policies + claims history
--     • Brand / dark-web monitoring — leaked creds, fake domains, impersonation
--     • Forensics artefacts     — chain-of-custody (memory dumps / disk images / logs)
--     • M&A cyber due diligence — target-entity workflow
--
-- Lives under the existing socops schema (V150). Follows the same conventions:
-- organisation_id FK, soft-delete columns, audit columns, row_version,
-- bilingual title_en/title_ar where user-visible.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 2 — OT/ICS asset inventory
-- ════════════════════════════════════════════════════════════════════════
-- Industrial estates in KSA (Aramco, SABIC, Ma'aden, SEC, NWC) live on the
-- Purdue Enterprise Reference Architecture. IT-grade asset tables don't
-- carry the right context: Purdue level, industrial protocol, safety
-- integrity level (SIL), vendor (Siemens/Honeywell/Rockwell/Schneider/ABB),
-- and physical site. Without these columns OT risk register entries
-- can't be assessed against OTCC (NCA's OT cybersecurity controls).
CREATE TABLE socops.ot_asset (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    asset_code            NVARCHAR(80)    NOT NULL,
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    purdue_level          INT             NOT NULL,                       -- 0 (sensors) … 5 (enterprise IT DMZ)
    asset_class           NVARCHAR(60)    NOT NULL,                       -- 'PLC' | 'RTU' | 'IED' | 'HMI' | 'HISTORIAN' | 'SCADA_SERVER' | 'ENGINEERING_WORKSTATION' | 'SAFETY_PLC' | 'JUMP_HOST' | 'OT_FIREWALL'
    vendor                NVARCHAR(120),                                  -- Siemens | Honeywell | Rockwell | Schneider | ABB | Emerson | Yokogawa | Mitsubishi
    model                 NVARCHAR(120),
    firmware_version      NVARCHAR(120),
    serial_number         NVARCHAR(120),
    site_code             NVARCHAR(80),                                   -- physical site code
    site_name             NVARCHAR(300),
    process_function      NVARCHAR(300),                                  -- which process this asset controls
    industrial_protocols  NVARCHAR(500),                                  -- comma-sep: Modbus,DNP3,OPC-UA,EtherNet/IP,Profibus,IEC 61850
    safety_integrity_level NVARCHAR(20),                                  -- SIL-1 / SIL-2 / SIL-3 / SIL-4 (IEC 61511)
    criticality           NVARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',      -- CRITICAL/HIGH/MEDIUM/LOW
    last_patched_at       DATETIME2,                                      -- OT patching cadence is months, not days
    patch_window          NVARCHAR(200),                                  -- e.g. 'Annual turnaround' / 'Q3 maintenance window'
    remote_access_enabled BIT             NOT NULL DEFAULT 0,             -- elevated risk flag
    network_segment       NVARCHAR(120),                                  -- VLAN / Purdue zone
    status                NVARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_ot_asset_code UNIQUE (organisation_id, asset_code)
);
CREATE INDEX ix_ot_asset_org_level ON socops.ot_asset(organisation_id, purdue_level) WHERE is_deleted = 0;
CREATE INDEX ix_ot_asset_org_site ON socops.ot_asset(organisation_id, site_code) WHERE is_deleted = 0;
CREATE INDEX ix_ot_asset_remote ON socops.ot_asset(organisation_id, remote_access_enabled) WHERE is_deleted = 0 AND remote_access_enabled = 1;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 2 — IAM Joiner-Mover-Leaver event log
-- ════════════════════════════════════════════════════════════════════════
-- NCA ECC-2 control family 2 (Identity & Access Management) requires the
-- platform to evidence the full user-lifecycle audit trail. Each row is
-- one lifecycle action — account created, role granted, mover transition,
-- account disabled — driving the SAMA / NCA "who-had-what-when" question
-- that comes up in every regulator examination.
CREATE TABLE socops.iam_jml_event (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    event_code            NVARCHAR(80)    NOT NULL,                       -- e.g. 'JML-2026-04-00342'
    event_type            NVARCHAR(40)    NOT NULL,                       -- 'JOINER' | 'MOVER' | 'LEAVER' | 'ROLE_GRANT' | 'ROLE_REVOKE' | 'PRIVILEGED_ELEVATION' | 'PRIVILEGED_REVOKE' | 'PASSWORD_RESET' | 'MFA_ENROL' | 'ACCESS_REVIEW'
    occurred_at           DATETIME2       NOT NULL,
    -- Subject (the user whose access is being changed)
    subject_user_id       BIGINT,                                         -- internal user id when known
    subject_username      NVARCHAR(255),
    subject_email         NVARCHAR(255),
    subject_department    NVARCHAR(120),
    subject_job_title     NVARCHAR(200),
    -- Source / system the change happened in
    source_system         NVARCHAR(60)    NOT NULL,                       -- 'AD' | 'AZURE_AD' | 'OKTA' | 'JUMPCLOUD' | 'PING' | 'SAILPOINT' | 'CYBERARK' | 'BEYONDTRUST' | 'MANUAL'
    target_system         NVARCHAR(200),                                  -- which downstream system was touched (SAP, banking core, M365…)
    -- Access details
    role_or_entitlement   NVARCHAR(300),
    privileged            BIT             NOT NULL DEFAULT 0,
    justification         NVARCHAR(MAX),
    approver_username     NVARCHAR(255),
    approver_role         NVARCHAR(120),
    -- Outcome
    outcome               NVARCHAR(30)    NOT NULL,                       -- 'COMPLETED' | 'PENDING' | 'DENIED' | 'EXPIRED'
    sod_violation_detected BIT            NOT NULL DEFAULT 0,             -- segregation-of-duties violation
    sod_violation_detail  NVARCHAR(MAX),
    -- Lifecycle tracking
    requested_at          DATETIME2,
    approved_at           DATETIME2,
    completed_at          DATETIME2,
    revoked_at            DATETIME2,                                      -- for ROLE_GRANT later revoked
    review_due_at         DATETIME2,                                      -- when access needs re-attestation
    -- Audit linkage
    related_ticket_id     NVARCHAR(120),                                  -- HR system / ServiceNow ticket
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_iam_jml_code UNIQUE (organisation_id, event_code)
);
CREATE INDEX ix_iam_jml_org_subject ON socops.iam_jml_event(organisation_id, subject_username) WHERE is_deleted = 0;
CREATE INDEX ix_iam_jml_org_type ON socops.iam_jml_event(organisation_id, event_type) WHERE is_deleted = 0;
CREATE INDEX ix_iam_jml_priv_pending ON socops.iam_jml_event(organisation_id, outcome) WHERE is_deleted = 0 AND privileged = 1;
CREATE INDEX ix_iam_jml_sod ON socops.iam_jml_event(organisation_id, sod_violation_detected) WHERE is_deleted = 0 AND sod_violation_detected = 1;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 2 — Application security findings (SAST/DAST/IAST/SCA)
-- ════════════════════════════════════════════════════════════════════════
-- Modern secure-SDLC requires findings from Veracode/Checkmarx/Snyk/
-- SonarQube/GitHub Advanced Security to feed into the GRC posture view.
-- This table is the unified ingest destination — connectors POST one
-- row per finding; severity drives the NCA remediation SLA the same way
-- vulnerability records do.
CREATE TABLE socops.appsec_finding (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(120)   NOT NULL,                       -- tool-specific finding ID
    scanner               NVARCHAR(40)    NOT NULL,                       -- 'VERACODE' | 'CHECKMARX' | 'SNYK' | 'SONARQUBE' | 'GITHUB_AS' | 'FORTIFY' | 'SEMGREP'
    scan_type             NVARCHAR(20)    NOT NULL,                       -- 'SAST' | 'DAST' | 'IAST' | 'SCA' | 'SECRETS' | 'IAC'
    application_name      NVARCHAR(300)   NOT NULL,
    application_id        BIGINT,                                         -- soft FK if app is in asset register
    repository_url        NVARCHAR(500),
    branch                NVARCHAR(120),
    finding_title         NVARCHAR(500)   NOT NULL,
    cwe                   NVARCHAR(40),                                   -- e.g. 'CWE-89' (SQL injection)
    cve                   NVARCHAR(40),                                   -- when SCA finds a known CVE
    severity              NVARCHAR(20)    NOT NULL,                       -- CRITICAL/HIGH/MEDIUM/LOW/INFO
    cvss_score            DECIMAL(4, 1),                                  -- 0.0 - 10.0
    file_path             NVARCHAR(500),
    line_number           INT,
    component_name        NVARCHAR(300),                                  -- for SCA findings
    component_version     NVARCHAR(120),
    fixed_version         NVARCHAR(120),                                  -- when upgrade-available
    description           NVARCHAR(MAX),
    remediation_advice    NVARCHAR(MAX),
    status                NVARCHAR(30)    NOT NULL DEFAULT 'OPEN',        -- OPEN/TRIAGED/FIXED/ACCEPTED_RISK/FALSE_POSITIVE/SUPPRESSED
    assigned_to_user_id   BIGINT,
    discovered_at         DATETIME2       NOT NULL,
    fixed_at              DATETIME2,
    sla_due_at            DATETIME2,
    suppression_reason    NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_appsec_finding_ext UNIQUE (organisation_id, scanner, external_id)
);
CREATE INDEX ix_appsec_org_status ON socops.appsec_finding(organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_appsec_org_severity ON socops.appsec_finding(organisation_id, severity) WHERE is_deleted = 0;
CREATE INDEX ix_appsec_org_app ON socops.appsec_finding(organisation_id, application_name) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 2 — Data Loss Prevention events
-- ════════════════════════════════════════════════════════════════════════
-- Saudi National Data Classification Policy (TOP_SECRET / SECRET /
-- RESTRICTED / PUBLIC) tags drive DLP policy. Each row is one event
-- from Microsoft Purview / Forcepoint / Symantec / Netskope — the
-- platform surfaces the trends + acts on auto-blocks vs allow.
CREATE TABLE socops.dlp_event (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(120)   NOT NULL,
    source_system         NVARCHAR(40)    NOT NULL,                       -- 'PURVIEW' | 'FORCEPOINT' | 'SYMANTEC' | 'NETSKOPE' | 'PROOFPOINT' | 'CLOUD_DLP'
    event_time            DATETIME2       NOT NULL,
    policy_name           NVARCHAR(300),
    policy_category       NVARCHAR(80),                                   -- 'PII' | 'PCI' | 'PHI' | 'IP' | 'PDPL_SENSITIVE' | 'NDC_RESTRICTED' | …
    data_classification   NVARCHAR(40),                                   -- NDC level: TOP_SECRET / SECRET / RESTRICTED / PUBLIC
    -- Subject
    username              NVARCHAR(255),
    user_email            NVARCHAR(255),
    department            NVARCHAR(120),
    -- Channel + destination
    channel               NVARCHAR(40)    NOT NULL,                       -- 'EMAIL' | 'WEB_UPLOAD' | 'USB' | 'CHAT' | 'API' | 'PRINT' | 'CLOUD_STORAGE'
    destination           NVARCHAR(500),                                  -- e.g. external email address, URL, USB serial
    file_name             NVARCHAR(500),
    file_size_bytes       BIGINT,
    file_hash             NVARCHAR(128),
    match_count           INT,                                            -- how many policy matches in the payload
    severity              NVARCHAR(20),                                   -- CRITICAL/HIGH/MEDIUM/LOW
    action_taken          NVARCHAR(30)    NOT NULL,                       -- 'BLOCKED' | 'QUARANTINED' | 'NOTIFIED' | 'ALLOWED_WITH_AUDIT' | 'ENCRYPTED'
    reviewed              BIT             NOT NULL DEFAULT 0,
    review_outcome        NVARCHAR(40),                                   -- 'JUSTIFIED' | 'POLICY_VIOLATION' | 'FALSE_POSITIVE'
    review_notes          NVARCHAR(MAX),
    raw_payload           NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_dlp_event_ext UNIQUE (organisation_id, source_system, external_id)
);
CREATE INDEX ix_dlp_org_time ON socops.dlp_event(organisation_id, event_time DESC) WHERE is_deleted = 0;
CREATE INDEX ix_dlp_org_severity ON socops.dlp_event(organisation_id, severity) WHERE is_deleted = 0;
CREATE INDEX ix_dlp_org_unreviewed ON socops.dlp_event(organisation_id, reviewed) WHERE is_deleted = 0 AND reviewed = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 3 — Cyber insurance policies + claims
-- ════════════════════════════════════════════════════════════════════════
-- Cyber-insurance underwriters in KSA require evidence of GRC maturity
-- to issue or renew a policy. This table tracks the policy itself and
-- the claim history — the underwriter's renewal questionnaire pulls
-- straight from these rows.
CREATE TABLE socops.cyber_insurance (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    policy_number         NVARCHAR(80)    NOT NULL,
    carrier_name          NVARCHAR(255)   NOT NULL,
    broker_name           NVARCHAR(255),
    policy_type           NVARCHAR(40)    NOT NULL,                       -- 'STANDALONE_CYBER' | 'TECH_EO' | 'D_AND_O_RIDER' | 'CRIME_RIDER'
    coverage_areas        NVARCHAR(MAX),                                  -- comma-sep: 'BUSINESS_INTERRUPTION,RANSOMWARE,EXTORTION,INCIDENT_RESPONSE,LEGAL_DEFENSE,REGULATORY_FINES,DATA_RECOVERY,FORENSICS,CRIME'
    -- Limits + retention
    aggregate_limit_sar   DECIMAL(18, 2),
    per_event_limit_sar   DECIMAL(18, 2),
    retention_sar         DECIMAL(18, 2),                                 -- deductible
    -- Coverage period
    effective_from        DATE            NOT NULL,
    effective_to          DATE            NOT NULL,
    renewal_due_at        DATE,
    -- Status
    status                NVARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RENEWAL_PENDING/EXPIRED/CANCELLED
    -- Underwriting evidence
    underwriting_questionnaire_id BIGINT,                                 -- soft FK to evidence_items
    last_renewal_responses_at     DATETIME2,
    -- Claims rolling counters (denormalised — claim detail in separate
    -- table is future enhancement)
    claim_count           INT             NOT NULL DEFAULT 0,
    last_claim_at         DATETIME2,
    last_claim_paid_sar   DECIMAL(18, 2),
    aggregate_claims_paid_sar DECIMAL(18, 2),
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_cyber_insurance_policy UNIQUE (organisation_id, policy_number)
);
CREATE INDEX ix_cyber_insurance_renewal ON socops.cyber_insurance(organisation_id, renewal_due_at) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 3 — Brand / dark-web monitoring alerts
-- ════════════════════════════════════════════════════════════════════════
-- Recorded Future / Digital Shadows / SOCRadar feeds. Each row is one
-- alert: leaked credential, fake-domain registration, dark-forum mention,
-- exposed code repo. CISO uses the queue to take takedown action.
CREATE TABLE socops.brand_monitoring (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    external_id           NVARCHAR(120)   NOT NULL,
    source_system         NVARCHAR(40)    NOT NULL,                       -- 'RECORDED_FUTURE' | 'DIGITAL_SHADOWS' | 'SOCRADAR' | 'FLASHPOINT' | 'KELA' | 'GHOSTPROJECT' | 'CUSTOM_HUNTER'
    alert_type            NVARCHAR(40)    NOT NULL,                       -- 'LEAKED_CREDENTIAL' | 'FAKE_DOMAIN' | 'IMPERSONATION_SOCIAL' | 'EXPOSED_CREDS_REPO' | 'DARK_FORUM_MENTION' | 'PHISHING_KIT' | 'BRAND_ABUSE'
    detected_at           DATETIME2       NOT NULL,
    severity              NVARCHAR(20),                                   -- CRITICAL/HIGH/MEDIUM/LOW
    confidence            NVARCHAR(20),                                   -- HIGH/MEDIUM/LOW
    -- Asset under threat
    affected_asset        NVARCHAR(500),                                  -- e.g. 'admin@tssgrc.sa' or 'tssgrc.sa' or 'TSS GRC' brand
    -- Indicator content (truncated/redacted for credentials)
    indicator_value       NVARCHAR(500),
    indicator_context     NVARCHAR(MAX),                                  -- where it was seen
    raw_payload           NVARCHAR(MAX),
    -- Action workflow
    status                NVARCHAR(30)    NOT NULL DEFAULT 'NEW',         -- NEW/INVESTIGATING/TAKEDOWN_REQUESTED/TAKEDOWN_COMPLETE/FALSE_POSITIVE/SUPPRESSED
    takedown_requested_at DATETIME2,
    takedown_completed_at DATETIME2,
    action_notes          NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_brand_mon_ext UNIQUE (organisation_id, source_system, external_id)
);
CREATE INDEX ix_brand_mon_org_status ON socops.brand_monitoring(organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_brand_mon_org_type ON socops.brand_monitoring(organisation_id, alert_type) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 3 — Forensics artefacts + chain of custody
-- ════════════════════════════════════════════════════════════════════════
-- Real forensic capture during an incident — memory dumps, disk images,
-- packet captures, log exports. Each artefact has a hash + handler
-- chain so legal counsel can present the chain-of-custody in court if
-- required. The existing evidence module is document-shaped; this is
-- forensic-shaped.
CREATE TABLE socops.forensics_artefact (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    artefact_code         NVARCHAR(80)    NOT NULL,                       -- e.g. 'FOR-2026-001-MEM-DUMP'
    case_code             NVARCHAR(80),                                   -- ties to incident / crisis event
    related_incident_id   BIGINT,                                         -- soft FK
    related_crisis_event_id BIGINT,                                       -- soft FK
    artefact_type         NVARCHAR(40)    NOT NULL,                       -- 'MEMORY_DUMP' | 'DISK_IMAGE' | 'PACKET_CAPTURE' | 'LOG_EXPORT' | 'REGISTRY_HIVE' | 'BROWSER_HISTORY' | 'MOBILE_BACKUP' | 'EMAIL_EXPORT' | 'SCREENSHOT' | 'VOLATILE_STATE'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    source_host           NVARCHAR(255),                                  -- where the artefact was captured
    source_asset_id       BIGINT,                                         -- soft FK to cyber_assets
    -- Capture details
    captured_at           DATETIME2       NOT NULL,
    captured_by_handler   NVARCHAR(255)   NOT NULL,                       -- name + role of first handler
    capture_tool          NVARCHAR(200),                                  -- FTK Imager / dd / Volatility / Wireshark / EnCase / X-Ways
    capture_method        NVARCHAR(200),                                  -- 'LIVE' | 'OFFLINE' | 'REMOTE'
    -- Integrity
    size_bytes            BIGINT,
    md5_hash              NVARCHAR(64),
    sha256_hash           NVARCHAR(128),
    sha512_hash           NVARCHAR(256),
    integrity_verified_at DATETIME2,
    integrity_status      NVARCHAR(30)    NOT NULL DEFAULT 'VERIFIED',    -- VERIFIED/CORRUPTED/UNVERIFIED
    -- Chain of custody (latest handler — full chain in custody_log)
    current_custodian     NVARCHAR(255),
    current_location      NVARCHAR(300),                                  -- e.g. 'TSS evidence locker, room 4B-12' / 'SAMA forensics submission, case #1234'
    -- Disposal
    retention_until       DATETIME2,                                      -- when can be destroyed
    disposed_at           DATETIME2,
    disposal_method       NVARCHAR(200),
    -- Legal
    legal_hold            BIT             NOT NULL DEFAULT 0,             -- on legal hold? cannot be disposed
    privileged            BIT             NOT NULL DEFAULT 0,             -- attorney-client privileged
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_forensics_code UNIQUE (organisation_id, artefact_code)
);
CREATE INDEX ix_forensics_org_case ON socops.forensics_artefact(organisation_id, case_code) WHERE is_deleted = 0;
CREATE INDEX ix_forensics_legal_hold ON socops.forensics_artefact(organisation_id, legal_hold) WHERE is_deleted = 0 AND legal_hold = 1;
GO

-- ════════════════════════════════════════════════════════════════════════
-- TIER 3 — M&A cyber due diligence workflow
-- ════════════════════════════════════════════════════════════════════════
-- Saudi PIF-driven acquisitions are a constant feature of the market.
-- Pre-close + post-close cyber due diligence has to be a tracked workflow,
-- not a Word document on someone's laptop. This table is the master
-- engagement; per-area findings link to the existing AuditFinding table.
CREATE TABLE socops.ma_due_diligence (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id       BIGINT          NOT NULL,
    engagement_code       NVARCHAR(80)    NOT NULL,                       -- e.g. 'MA-2026-Q3-001'
    title_en              NVARCHAR(300)   NOT NULL,
    title_ar              NVARCHAR(300),
    target_entity_name    NVARCHAR(300)   NOT NULL,
    target_industry       NVARCHAR(120),
    target_country        NVARCHAR(80),                                   -- ISO country code
    deal_type             NVARCHAR(40)    NOT NULL,                       -- 'ACQUISITION' | 'MERGER' | 'JOINT_VENTURE' | 'DIVESTITURE' | 'INVESTMENT'
    -- Phase
    phase                 NVARCHAR(40)    NOT NULL DEFAULT 'PRE_LOI',     -- PRE_LOI / DUE_DILIGENCE / NEGOTIATION / SIGNED / CLOSED / INTEGRATION / TERMINATED
    deal_status           NVARCHAR(40),                                   -- 'PROCEEDING' | 'PAUSED' | 'TERMINATED' | 'COMPLETED'
    -- Risk areas assessed (comma-sep)
    risk_areas_assessed   NVARCHAR(MAX),                                  -- e.g. 'IDENTITY,DATA_PROTECTION,APPLICATIONS,INFRASTRUCTURE,VENDORS,REGULATORY,INCIDENTS_HISTORY,OT_ICS'
    -- Findings counters
    findings_total        INT             NOT NULL DEFAULT 0,
    findings_critical     INT             NOT NULL DEFAULT 0,
    findings_high         INT             NOT NULL DEFAULT 0,
    -- Risk-adjusted price impact in SAR
    price_adjustment_sar  DECIMAL(18, 2),
    -- Deal officers
    cyber_lead_name       NVARCHAR(255),
    legal_lead_name       NVARCHAR(255),
    finance_lead_name     NVARCHAR(255),
    -- Dates
    loi_signed_at         DATETIME2,
    fieldwork_start_at    DATETIME2,
    fieldwork_end_at      DATETIME2,
    final_report_at       DATETIME2,
    deal_close_at         DATETIME2,
    integration_complete_at DATETIME2,
    -- Recommendation
    cyber_recommendation  NVARCHAR(40),                                   -- 'PROCEED' | 'PROCEED_WITH_CONDITIONS' | 'PRICE_ADJUSTMENT' | 'TERMINATE'
    recommendation_notes  NVARCHAR(MAX),
    notes                 NVARCHAR(MAX),
    is_deleted            BIT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME2,
    deleted_by            BIGINT,
    created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2,
    created_by            BIGINT,
    updated_by            BIGINT,
    row_version           ROWVERSION      NOT NULL,
    CONSTRAINT uq_ma_dd_code UNIQUE (organisation_id, engagement_code)
);
CREATE INDEX ix_ma_dd_org_phase ON socops.ma_due_diligence(organisation_id, phase) WHERE is_deleted = 0;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Seed — minimal demo rows so the new tabs aren't blank on first load
-- ════════════════════════════════════════════════════════════════════════
DECLARE @org BIGINT = 1;

-- One OT asset (representative Aramco-shaped record — a Schneider PLC at
-- Purdue level 2 in a refinery process control loop).
IF NOT EXISTS (SELECT 1 FROM socops.ot_asset WHERE organisation_id = @org AND asset_code = N'OT-PLC-RFY-001')
BEGIN
    INSERT INTO socops.ot_asset
        (organisation_id, asset_code, title_en, title_ar,
         purdue_level, asset_class, vendor, model, firmware_version,
         site_code, site_name, process_function,
         industrial_protocols, safety_integrity_level, criticality,
         remote_access_enabled, network_segment, status, notes)
    VALUES
        (@org, N'OT-PLC-RFY-001',
         N'Refinery Unit 4 Process Control PLC',
         N'PLC التحكم في عملية الوحدة الرابعة بالمصفاة',
         2, N'PLC', N'Schneider', N'Modicon M580', N'SV3.20',
         N'RFY-01', N'Eastern Refinery Complex',
         N'Crude distillation column temperature + flow control',
         N'Modbus,EtherNet/IP', N'SIL-2', N'CRITICAL',
         0, N'PROCESS-VLAN-204', N'ACTIVE',
         N'Patches applied only during Q4 turnaround. Last firmware update: 2025-11-12.');
END

-- One IAM JML event (representative joiner with privileged AD role)
IF NOT EXISTS (SELECT 1 FROM socops.iam_jml_event WHERE organisation_id = @org AND event_code = N'JML-2026-04-00001')
BEGIN
    INSERT INTO socops.iam_jml_event
        (organisation_id, event_code, event_type, occurred_at,
         subject_username, subject_email, subject_department, subject_job_title,
         source_system, target_system, role_or_entitlement, privileged,
         outcome, approver_username, approver_role,
         requested_at, approved_at, completed_at, review_due_at)
    VALUES
        (@org, N'JML-2026-04-00001', N'JOINER', SYSUTCDATETIME(),
         N'ahmed.alqahtani', N'ahmed.alqahtani@tssgrc.sa',
         N'Information Security', N'SOC Analyst Tier 2',
         N'AZURE_AD', N'M365 + SIEM + Cyberark Vault',
         N'SOC_ANALYST_L2_GROUP', 0,
         N'COMPLETED', N'sara.almutairi', N'CISO',
         DATEADD(DAY, -3, SYSUTCDATETIME()),
         DATEADD(DAY, -2, SYSUTCDATETIME()),
         DATEADD(DAY, -1, SYSUTCDATETIME()),
         DATEADD(DAY, 90, SYSUTCDATETIME()));
END

-- One cyber insurance policy (representative — SAMA-supervised entity with
-- standalone cyber tower).
IF NOT EXISTS (SELECT 1 FROM socops.cyber_insurance WHERE organisation_id = @org AND policy_number = N'CYB-POL-2026-001')
BEGIN
    INSERT INTO socops.cyber_insurance
        (organisation_id, policy_number, carrier_name, broker_name,
         policy_type, coverage_areas,
         aggregate_limit_sar, per_event_limit_sar, retention_sar,
         effective_from, effective_to, renewal_due_at, status,
         notes)
    VALUES
        (@org, N'CYB-POL-2026-001', N'Bupa Arabia (Cyber)', N'Marsh Saudi Arabia',
         N'STANDALONE_CYBER',
         N'BUSINESS_INTERRUPTION,RANSOMWARE,EXTORTION,INCIDENT_RESPONSE,LEGAL_DEFENSE,REGULATORY_FINES,DATA_RECOVERY,FORENSICS',
         50000000.00, 25000000.00, 1000000.00,
         '2026-01-01', '2026-12-31',
         '2026-11-01', N'ACTIVE',
         N'Renewal questionnaire due 60 days before policy expiry. Pulls evidence directly from this platform.');
END

-- ════════════════════════════════════════════════════════════════════════
-- Module licensing — SOCOPS_PLUS for the Tier-2/3 expansion.
-- We keep it under the existing SOCOPS module code; tier upgrade goes
-- via the existing licensing tier mechanism (FREE / PRO / ENTERPRISE),
-- with ENTERPRISE required to access the V152 tables. No new module
-- entry needed; the @RequireModule("SOCOPS") on the new controllers
-- is the same gate.
-- ════════════════════════════════════════════════════════════════════════
GO

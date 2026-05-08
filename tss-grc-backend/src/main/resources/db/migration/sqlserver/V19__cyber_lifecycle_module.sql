-- V19 — Cyber Lifecycle module backend tables.
-- Mirrors lib/cyberLifecycle.ts. Aligned with NCA ECC-1:2018 / ECC-2:2024,
-- SAMA Cyber Security Framework, and CITC for telecoms.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'cyber_lc')
    EXEC('CREATE SCHEMA cyber_lc');
GO

-- ─── Asset inventory (lightweight; CMDB lives elsewhere) ──────────────────
CREATE TABLE cyber_lc.assets (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id        NVARCHAR(64)  NOT NULL,                       -- AST-2026-001
    organisation_id    BIGINT        NOT NULL,
    asset_name         NVARCHAR(255) NOT NULL,
    asset_type         NVARCHAR(30)  NOT NULL,                       -- SERVER/ENDPOINT/NETWORK/CLOUD/SAAS/IOT/OT/MOBILE/APPLICATION/DATABASE
    classification     NVARCHAR(20)  NOT NULL,                       -- TOP_SECRET/SECRET/CONFIDENTIAL/INTERNAL/PUBLIC (NCA ECC)
    business_owner     NVARCHAR(255),
    technical_owner    NVARCHAR(255),
    location_site      NVARCHAR(100),
    cloud_provider     NVARCHAR(50),                                  -- AWS/AZURE/GCP/STC_CLOUD/MOBILY_CLOUD/ORACLE_CLOUD
    environment        NVARCHAR(20),                                  -- PROD/UAT/DEV/DR
    criticality        NVARCHAR(20)  NOT NULL,                        -- TIER1/TIER2/TIER3/TIER4
    operational_status NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_scanned_at    DATETIME2,
    is_deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ast_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_ast_criticality ON cyber_lc.assets (organisation_id, criticality, is_deleted);
GO

-- ─── Threat intel feed entries ────────────────────────────────────────────
CREATE TABLE cyber_lc.threat_intel (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                        -- TI-2026-001
    organisation_id   BIGINT        NOT NULL,
    cve_id            NVARCHAR(20),                                   -- CVE-2026-1234
    title_en          NVARCHAR(500) NOT NULL,
    title_ar          NVARCHAR(500) NOT NULL,
    severity          NVARCHAR(20)  NOT NULL,                        -- CRITICAL/HIGH/MEDIUM/LOW
    cvss_score        DECIMAL(4,1),
    source            NVARCHAR(50),                                   -- NCA_HASEN/MS-ISAC/SAMA_CERT/VENDOR/INTERNAL
    published_at      DATETIME2,
    summary_en        NVARCHAR(MAX),
    summary_ar        NVARCHAR(MAX),
    affected_products NVARCHAR(MAX),                                  -- comma-sep
    mitigations_en    NVARCHAR(MAX),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ti_external UNIQUE (organisation_id, external_id)
);
GO

-- ─── Vulnerability — PRIMARY state-machine entity ─────────────────────────
CREATE TABLE cyber_lc.vulnerabilities (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id              NVARCHAR(64)  NOT NULL,                  -- VULN-2026-1042
    organisation_id          BIGINT        NOT NULL,
    asset_id                 NVARCHAR(64)  NOT NULL,                  -- references cyber_lc.assets.external_id
    cve_id                   NVARCHAR(20),
    title_en                 NVARCHAR(500) NOT NULL,
    title_ar                 NVARCHAR(500) NOT NULL,
    description_en           NVARCHAR(MAX),
    description_ar           NVARCHAR(MAX),
    severity                 NVARCHAR(20)  NOT NULL,                  -- CRITICAL/HIGH/MEDIUM/LOW
    cvss_score               DECIMAL(4,1),
    detection_source         NVARCHAR(50),                             -- TENABLE/QUALYS/CROWDSTRIKE/MANUAL/PEN_TEST/BUG_BOUNTY

    -- ─── State machine ──────────────────────────────────────────────────
    -- NEW                   → TRIAGED, FALSE_POSITIVE
    -- TRIAGED               → OWNER_ASSIGNED, RISK_ACCEPTED
    -- OWNER_ASSIGNED        → IN_REMEDIATION, RISK_ACCEPTED
    -- IN_REMEDIATION        → AWAITING_VERIFICATION
    -- AWAITING_VERIFICATION → CLOSED, IN_REMEDIATION
    -- RISK_ACCEPTED         → CLOSED
    -- FALSE_POSITIVE        → CLOSED
    status                   NVARCHAR(30)  NOT NULL DEFAULT 'NEW',

    detected_at              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    triaged_at               DATETIME2,
    triaged_by               NVARCHAR(255),
    owner_assigned_at        DATETIME2,
    owner                    NVARCHAR(255),
    remediation_started_at   DATETIME2,
    remediation_method_en    NVARCHAR(MAX),
    remediation_method_ar    NVARCHAR(MAX),
    verification_at          DATETIME2,
    verified_by              NVARCHAR(255),
    closed_at                DATETIME2,

    -- SLA per NCA ECC severity matrix
    sla_deadline             DATE,                                     -- 7d for CRITICAL, 30d HIGH, 60d MED, 90d LOW
    breach_count             INT           NOT NULL DEFAULT 0,
    sla_extended             BIT           NOT NULL DEFAULT 0,

    -- Risk acceptance (parallel terminal path)
    risk_accepted_by         NVARCHAR(255),
    risk_acceptance_expiry   DATE,
    risk_acceptance_reason_en NVARCHAR(MAX),
    risk_acceptance_reason_ar NVARCHAR(MAX),

    -- False positive
    false_positive_reason_en NVARCHAR(MAX),
    false_positive_reason_ar NVARCHAR(MAX),

    threat_intel_id          NVARCHAR(64),                             -- linked TI external_id
    is_deleted               SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_vuln_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_vuln_org_status   ON cyber_lc.vulnerabilities (organisation_id, status, is_deleted);
CREATE INDEX ix_vuln_severity_sla ON cyber_lc.vulnerabilities (organisation_id, severity, sla_deadline, is_deleted);
CREATE INDEX ix_vuln_asset        ON cyber_lc.vulnerabilities (organisation_id, asset_id, is_deleted);
GO

-- ─── Cyber incidents (separate from vulnerabilities) ──────────────────────
-- Lighter table for incident records. Detailed IR lives in module/incident.
CREATE TABLE cyber_lc.cyber_incidents (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,                        -- CINC-2026-007
    organisation_id     BIGINT        NOT NULL,
    title_en            NVARCHAR(500) NOT NULL,
    title_ar            NVARCHAR(500) NOT NULL,
    severity            NVARCHAR(10)  NOT NULL,                        -- SEV1..SEV4
    classification      NVARCHAR(30)  NOT NULL,                        -- DATA_BREACH/RANSOMWARE/PHISHING/DDOS/INSIDER/MALWARE/OTHER
    detected_at         DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    contained_at        DATETIME2,
    eradicated_at       DATETIME2,
    recovered_at        DATETIME2,
    root_cause_en       NVARCHAR(MAX),
    root_cause_ar       NVARCHAR(MAX),
    nca_notified_at     DATETIME2,
    nca_reference       NVARCHAR(100),
    sama_notified_at    DATETIME2,
    sdaia_notified_at   DATETIME2,                                      -- if PII involved
    status              NVARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_cinc_external UNIQUE (organisation_id, external_id)
);
GO

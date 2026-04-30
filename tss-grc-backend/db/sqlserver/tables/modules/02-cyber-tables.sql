-- ============================================================================
-- TSS GRC Platform - Cybersecurity Module Tables
-- Schema: cyber
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- cyber.assets (IT/OT Asset Inventory)
-- ============================================================================
CREATE TABLE cyber.assets (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    bu_id               BIGINT                  NULL,
    code                NVARCHAR(50)            NOT NULL,
    name_en             NVARCHAR(200)           NOT NULL,
    name_ar             NVARCHAR(200)           NULL,
    description         NVARCHAR(MAX)           NULL,
    asset_type          NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_assets_type CHECK (asset_type IN (
            'SERVER','WORKSTATION','NETWORK','MOBILE','APPLICATION',
            'DATABASE','CLOUD','OT_DEVICE','IOT','OTHER')),
    ip_address          NVARCHAR(50)            NULL,
    hostname            NVARCHAR(200)           NULL,
    location            NVARCHAR(200)           NULL,
    classification      NVARCHAR(50)            NOT NULL DEFAULT 'INTERNAL'
        CONSTRAINT CK_assets_class CHECK (classification IN (
            'TOP_SECRET','SECRET','CONFIDENTIAL','INTERNAL','PUBLIC')),
    criticality         NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT CK_assets_crit CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    owner_user_id       BIGINT                  NULL,
    custodian_user_id   BIGINT                  NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT CK_assets_status CHECK (status IN ('ACTIVE','INACTIVE','DECOMMISSIONED')),
    -- Audit
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    deleted_by          BIGINT                  NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_assets PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_assets_code UNIQUE (org_id, code),
    CONSTRAINT FK_assets_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_assets_bu FOREIGN KEY (bu_id) REFERENCES platform.business_units(id),
    CONSTRAINT FK_assets_owner FOREIGN KEY (owner_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- cyber.vulnerabilities
-- ============================================================================
CREATE TABLE cyber.vulnerabilities (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    asset_id                BIGINT                  NOT NULL,
    org_id                  BIGINT                  NOT NULL,
    title                   NVARCHAR(500)           NOT NULL,
    description             NVARCHAR(MAX)           NULL,
    cve_id                  NVARCHAR(50)            NULL,
    cvss_score              DECIMAL(4,1)            NULL,
    severity                NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT CK_vuln_severity CHECK (severity IN (
            'CRITICAL','HIGH','MEDIUM','LOW','INFORMATIONAL')),
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'OPEN'
        CONSTRAINT CK_vuln_status CHECK (status IN (
            'OPEN','IN_REMEDIATION','REMEDIATED','ACCEPTED','FALSE_POSITIVE')),
    discovered_at           DATETIME2(7)            NULL,
    remediation_due_date    DATE                    NULL,
    remediated_at           DATETIME2(7)            NULL,
    assigned_to             BIGINT                  NULL,
    remediation_notes       NVARCHAR(MAX)           NULL,
    source                  NVARCHAR(100)           NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_vulnerabilities PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_vuln_asset FOREIGN KEY (asset_id) REFERENCES cyber.assets(id),
    CONSTRAINT FK_vuln_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_vuln_assigned FOREIGN KEY (assigned_to) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- cyber.threats
-- ============================================================================
CREATE TABLE cyber.threats (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    org_id              BIGINT                  NOT NULL,
    name_en             NVARCHAR(200)           NOT NULL,
    name_ar             NVARCHAR(200)           NULL,
    description         NVARCHAR(MAX)           NULL,
    threat_type         NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_threats_type CHECK (threat_type IN (
            'APT','INSIDER','NATURAL_DISASTER','ACCIDENTAL',
            'NATION_STATE','CRIMINAL','HACKTIVIST','OTHER')),
    source              NVARCHAR(200)           NULL,
    likelihood          INT                     NULL CONSTRAINT CK_threats_like CHECK (likelihood BETWEEN 1 AND 5),
    potential_impact    INT                     NULL CONSTRAINT CK_threats_imp CHECK (potential_impact BETWEEN 1 AND 5),
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    CONSTRAINT PK_threats PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_threats_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- ============================================================================
-- cyber.incidents
-- ============================================================================
CREATE TABLE cyber.incidents (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    bu_id                   BIGINT                  NULL,
    code                    NVARCHAR(50)            NOT NULL,
    title                   NVARCHAR(500)           NOT NULL,
    description             NVARCHAR(MAX)           NULL,
    incident_type           NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_inc_type CHECK (incident_type IN (
            'DATA_BREACH','MALWARE','RANSOMWARE','UNAUTHORIZED_ACCESS',
            'DOS_DDOS','PHISHING','INSIDER_THREAT','PHYSICAL',
            'SUPPLY_CHAIN','OTHER')),
    severity                NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_inc_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'REPORTED'
        CONSTRAINT CK_inc_status CHECK (status IN (
            'REPORTED','TRIAGED','IN_INVESTIGATION','CONTAINED',
            'ERADICATED','RECOVERED','CLOSED','FALSE_POSITIVE')),
    detected_at             DATETIME2(7)            NULL,
    reported_at             DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    contained_at            DATETIME2(7)            NULL,
    resolved_at             DATETIME2(7)            NULL,
    closed_at               DATETIME2(7)            NULL,
    reporter_user_id        BIGINT                  NULL,
    assigned_to             BIGINT                  NULL,
    -- Cross-module linkage
    linked_risk_id          BIGINT                  NULL,
    -- Root cause and resolution
    root_cause              NVARCHAR(MAX)           NULL,
    corrective_action       NVARCHAR(MAX)           NULL,
    lessons_learned         NVARCHAR(MAX)           NULL,
    -- Regulatory
    requires_notification   BIT                     NOT NULL DEFAULT 0,
    notified_authority_at   DATETIME2(7)            NULL,
    -- Audit
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    deleted_by              BIGINT                  NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_incidents PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_incidents_code UNIQUE (org_id, code),
    CONSTRAINT FK_inc_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_inc_bu FOREIGN KEY (bu_id) REFERENCES platform.business_units(id),
    CONSTRAINT FK_inc_reporter FOREIGN KEY (reporter_user_id) REFERENCES iam.users(id),
    CONSTRAINT FK_inc_assigned FOREIGN KEY (assigned_to) REFERENCES iam.users(id),
    CONSTRAINT FK_inc_risk FOREIGN KEY (linked_risk_id) REFERENCES risk.risks(id)
);
GO

-- ============================================================================
-- cyber.incident_affected_assets (bridge: incident ↔ asset)
-- ============================================================================
CREATE TABLE cyber.incident_affected_assets (
    id              BIGINT IDENTITY(1,1)    NOT NULL,
    incident_id     BIGINT                  NOT NULL,
    asset_id        BIGINT                  NOT NULL,
    impact_notes    NVARCHAR(MAX)           NULL,
    created_at      DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_incident_assets PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_incident_asset UNIQUE (incident_id, asset_id),
    CONSTRAINT FK_ia_incident FOREIGN KEY (incident_id) REFERENCES cyber.incidents(id),
    CONSTRAINT FK_ia_asset FOREIGN KEY (asset_id) REFERENCES cyber.assets(id)
);
GO

-- ============================================================================
-- cyber.cyber_assessments
-- ============================================================================
CREATE TABLE cyber.cyber_assessments (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    framework_id        BIGINT                  NULL,
    title               NVARCHAR(500)           NOT NULL,
    scope               NVARCHAR(MAX)           NULL,
    assessor_user_id    BIGINT                  NULL,
    assessment_date     DATE                    NULL,
    due_date            DATE                    NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'PLANNED'
        CONSTRAINT CK_ca_status CHECK (status IN (
            'PLANNED','IN_PROGRESS','COMPLETED','APPROVED','ARCHIVED')),
    overall_score       DECIMAL(5,2)            NULL,
    notes               NVARCHAR(MAX)           NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_cyber_assessments PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ca_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_ca_framework FOREIGN KEY (framework_id) REFERENCES platform.control_frameworks(id)
);
GO

-- ============================================================================
-- cyber.cyber_controls (control assessment per cyber context)
-- ============================================================================
CREATE TABLE cyber.cyber_controls (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    assessment_id       BIGINT                  NOT NULL,
    control_id          BIGINT                  NOT NULL,
    asset_id            BIGINT                  NULL,
    compliance_status   NVARCHAR(50)            NOT NULL DEFAULT 'NOT_ASSESSED'
        CONSTRAINT CK_cyc_status CHECK (compliance_status IN (
            'COMPLIANT','PARTIALLY_COMPLIANT','NON_COMPLIANT','NOT_APPLICABLE','NOT_ASSESSED')),
    effectiveness       NVARCHAR(50)            NULL,
    notes               NVARCHAR(MAX)           NULL,
    assessed_by         BIGINT                  NULL,
    assessed_at         DATETIME2(7)            NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_cyber_controls PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_cyc_assessment FOREIGN KEY (assessment_id) REFERENCES cyber.cyber_assessments(id),
    CONSTRAINT FK_cyc_control FOREIGN KEY (control_id) REFERENCES platform.controls(id),
    CONSTRAINT FK_cyc_asset FOREIGN KEY (asset_id) REFERENCES cyber.assets(id)
);
GO

PRINT 'Cybersecurity module tables created successfully.';
GO

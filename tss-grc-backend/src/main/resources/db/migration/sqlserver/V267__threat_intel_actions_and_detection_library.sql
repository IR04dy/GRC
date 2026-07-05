-- V267 — SOC-E: threat-intel action linkage + detection-rule library (MITRE ATT&CK mapped)
--
-- Two additions that turn threat-intel from a read-only feed into an operational surface:
--
--  1. socops.detection_rule — a first-class detection-rule / use-case LIBRARY entity with
--     MITRE ATT&CK technique mapping. Before this, the SOC had nowhere to record the
--     SIEM/EDR/NDR content it maintains, nor how that content maps to ATT&CK. This is the
--     "detection library" the review flagged as missing.
--
--  2. socops.threat_intel_action — makes "actioning" an IOC CONCRETE. Previously
--     threat_intel.actioned was a single boolean flip with a free-text note — no record of
--     WHAT was done or against WHICH target. Each action row now links a threat_intel row to
--     a real platform target (ASSET / VULNERABILITY / DETECTION), producing an auditable,
--     multi-row action history (an IOC can be blocked on an asset AND linked to a detection).
--
-- Forward-only; idempotent. Hand-applied in dev via pyodbc (Flyway is off in dev).

------------------------------------------------------------------------------------------
-- 1. Detection-rule library
------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'socops' AND t.name = 'detection_rule')
BEGIN
    CREATE TABLE socops.detection_rule (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        rule_code            NVARCHAR(60)  NOT NULL,
        title_en             NVARCHAR(300) NOT NULL,
        title_ar             NVARCHAR(300) NULL,
        description          NVARCHAR(MAX) NULL,
        detection_type       NVARCHAR(30)  NOT NULL,   -- SIEM / EDR / NDR / IDS / CORRELATION / THREAT_HUNT / UEBA ...
        platform             NVARCHAR(80)  NULL,        -- Splunk / Sentinel / Elastic / CrowdStrike / QRadar ...
        log_source           NVARCHAR(300) NULL,
        detection_logic      NVARCHAR(MAX) NULL,        -- the actual query / correlation / signature text
        mitre_tactics        NVARCHAR(300) NULL,        -- TA0001,TA0002 (comma-separated)
        mitre_techniques     NVARCHAR(300) NOT NULL,    -- T1566,T1059.001 — REQUIRED (the ATT&CK mapping)
        data_sources         NVARCHAR(300) NULL,        -- ATT&CK data sources / log feeds relied on
        severity             NVARCHAR(20)  NULL,         -- CRITICAL / HIGH / MEDIUM / LOW / INFO
        status               NVARCHAR(20)  NOT NULL CONSTRAINT df_dr_status  DEFAULT 'DRAFT',
        enabled              BIT           NOT NULL CONSTRAINT df_dr_enabled DEFAULT 1,
        false_positive_rate  NVARCHAR(20)  NULL,         -- LOW / MEDIUM / HIGH
        aligned_frameworks   NVARCHAR(300) NULL,         -- NCA-ECC / SAMA-CSF control refs
        last_triggered_at    DATETIME2     NULL,
        -- BaseEntity columns
        is_deleted           BIT           NOT NULL CONSTRAINT df_dr_isdel DEFAULT 0,
        deleted_at           DATETIME2     NULL,
        deleted_by           BIGINT        NULL,
        created_at           DATETIME2     NOT NULL CONSTRAINT df_dr_cat DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2     NULL,
        created_by           BIGINT        NULL,
        updated_by           BIGINT        NULL,
        row_version          ROWVERSION    NOT NULL,
        CONSTRAINT ck_dr_type   CHECK (detection_type IN
            ('SIEM','EDR','NDR','IDS','IPS','CORRELATION','THREAT_HUNT','UEBA','SOAR','CLOUD','CUSTOM')),
        CONSTRAINT ck_dr_status CHECK (status IN ('DRAFT','ACTIVE','TUNING','DISABLED','RETIRED')),
        CONSTRAINT uq_dr_org_code UNIQUE (organisation_id, rule_code)
    );
    CREATE INDEX ix_dr_org ON socops.detection_rule(organisation_id, is_deleted);
END
GO

------------------------------------------------------------------------------------------
-- 2. Threat-intel action linkage
------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
              WHERE s.name = 'socops' AND t.name = 'threat_intel_action')
BEGIN
    CREATE TABLE socops.threat_intel_action (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        threat_intel_id      BIGINT        NOT NULL,
        action_type          NVARCHAR(30)  NOT NULL,    -- BLOCK / SCAN / HUNT / MONITOR / PATCH / BRIEF / LINK_DETECTION / REVIEWED / ESCALATE
        target_type          NVARCHAR(20)  NULL,        -- ASSET / VULNERABILITY / DETECTION / NONE
        target_id            BIGINT        NULL,        -- id of the linked asset / vulnerability / detection_rule
        target_label         NVARCHAR(300) NULL,        -- human label captured at action time (denormalised)
        notes                NVARCHAR(MAX) NULL,
        actioned_by_user_id  BIGINT        NULL,
        actioned_by          NVARCHAR(150) NULL,
        actioned_at          DATETIME2     NOT NULL CONSTRAINT df_tia_at DEFAULT SYSUTCDATETIME(),
        -- BaseEntity columns
        is_deleted           BIT           NOT NULL CONSTRAINT df_tia_isdel DEFAULT 0,
        deleted_at           DATETIME2     NULL,
        deleted_by           BIGINT        NULL,
        created_at           DATETIME2     NOT NULL CONSTRAINT df_tia_cat DEFAULT SYSUTCDATETIME(),
        updated_at           DATETIME2     NULL,
        created_by           BIGINT        NULL,
        updated_by           BIGINT        NULL,
        row_version          ROWVERSION    NOT NULL,
        CONSTRAINT fk_tia_ti FOREIGN KEY (threat_intel_id) REFERENCES socops.threat_intel(id),
        CONSTRAINT ck_tia_action CHECK (action_type IN
            ('BLOCK','SCAN','HUNT','MONITOR','PATCH','BRIEF','LINK_DETECTION','REVIEWED','ESCALATE')),
        CONSTRAINT ck_tia_target CHECK (target_type IS NULL OR target_type IN
            ('ASSET','VULNERABILITY','DETECTION','NONE'))
    );
    CREATE INDEX ix_tia_ti ON socops.threat_intel_action(organisation_id, threat_intel_id, is_deleted);
END
GO

-- V115: Round-2 engines for the three buyer-assessment modules.
--
-- V113 closed the first batch (KRI bands already shipped, risk-control bridge,
-- cross-border + special-category tagging, evidence freshness sweep). This
-- migration closes the four remaining items the buyer doc still listed:
--
--   §5.1 #6 — Periodic risk-owner attestation cycle (SAMA expectation; NCA
--             ECC §1-3 control). Adds attestation cadence columns to risks
--             + a per-attestation audit table.
--
--   §5.4 #1 — DSR fulfilment SLA engine. The sla_deadline column already
--             exists on dsr_cases; this migration adds the supporting
--             columns the sweep service needs (sla_breached_at, breach
--             notified flag) so we don't re-emit the F8 event each sweep.
--
--   §5.4 #2 — PDPL Article 23 72-hour breach notification deadline. The
--             CrisisEvent entity has pdpl_notification_drafted_at; add a
--             derived deadline column so dashboards can sort by remaining
--             time and the sweep can flag breaches.
--
--   §5.9 #2 — Evidence connector library MVP. Adds a registry table and
--             seeds 10 stub connector definitions (AD, Defender, Azure
--             Policy, AWS Config, Sentinel, Tenable, Qualys, ZScaler,
--             Splunk, GitHub). The existing SIEM-webhook intake stays the
--             actual ingestion path; connectors are catalog entries that
--             expose what we *can* talk to, plus per-tenant enablement
--             flags for the buyer-facing connector picker.
--
-- Apply with: sqlcmd -f 65001 (UTF-8 codepage) — Arabic literals below.

SET NOCOUNT ON;
-- risk.risks + bcm.crisis_events carry filtered indexes / computed columns
-- which require QUOTED_IDENTIFIER ON for any ALTER/UPDATE in the session.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- §5.1 #6 — Risk owner attestation cadence
-- ══════════════════════════════════════════════════════════════════

-- Per-column guards (multi-ALTER batches are non-resumable on partial failures).
IF COL_LENGTH('risk.risks','attestation_cadence_days') IS NULL
    ALTER TABLE risk.risks ADD attestation_cadence_days INT NOT NULL DEFAULT 90;
GO
IF COL_LENGTH('risk.risks','last_attested_at') IS NULL
    ALTER TABLE risk.risks ADD last_attested_at DATETIME2 NULL;
GO
IF COL_LENGTH('risk.risks','next_attestation_date') IS NULL
    ALTER TABLE risk.risks ADD next_attestation_date DATE NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'risk_attestations' AND schema_id = SCHEMA_ID('risk'))
BEGIN
    CREATE TABLE risk.risk_attestations (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        risk_id           BIGINT          NOT NULL,

        attested_at       DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        attested_by       BIGINT          NULL,         -- user id (soft FK)
        attested_by_name  NVARCHAR(200)   NULL,         -- snapshot

        -- CONFIRMED        — owner agrees risk + treatment are still current
        -- NEEDS_REVIEW     — owner wants a re-assessment
        -- TRANSFER_OWNER   — owner is delegating, with reason in statement
        decision          NVARCHAR(30)    NOT NULL DEFAULT 'CONFIRMED',

        statement_en      NVARCHAR(MAX)   NULL,
        statement_ar      NVARCHAR(MAX)   NULL,

        -- Snapshot of the risk's residual posture at attestation time, so we
        -- have a defensible audit trail even if the risk drifts later.
        residual_score_snapshot   INT             NULL,
        risk_level_snapshot       NVARCHAR(20)    NULL,

        next_due_date     DATE            NULL,         -- attested_at + cadence

        is_deleted        BIT             NOT NULL DEFAULT 0,
        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT ck_risk_attest_decision CHECK (decision IN
            ('CONFIRMED','NEEDS_REVIEW','TRANSFER_OWNER'))
    );
    CREATE INDEX IX_risk_attest_org_risk
        ON risk.risk_attestations(organisation_id, risk_id);
    CREATE INDEX IX_risk_attest_org_attested
        ON risk.risk_attestations(organisation_id, attested_at DESC);
END
GO

-- Backfill: any risk without next_attestation_date gets one set to
-- today + cadence (so the sweep can find them on first run).
UPDATE risk.risks
   SET next_attestation_date = DATEADD(DAY, attestation_cadence_days, CAST(SYSUTCDATETIME() AS DATE))
 WHERE next_attestation_date IS NULL
   AND is_deleted = 0;
GO

-- ══════════════════════════════════════════════════════════════════
-- §5.4 #1 — DSR SLA breach tracking columns
-- ══════════════════════════════════════════════════════════════════

IF COL_LENGTH('privacy_lc.dsr_cases','sla_breached_at') IS NULL
    ALTER TABLE privacy_lc.dsr_cases ADD sla_breached_at DATETIME2 NULL;
GO
IF COL_LENGTH('privacy_lc.dsr_cases','sla_breach_notified') IS NULL
    ALTER TABLE privacy_lc.dsr_cases ADD sla_breach_notified BIT NOT NULL DEFAULT 0;
GO

-- ══════════════════════════════════════════════════════════════════
-- §5.4 #2 — PDPL 72-hour breach deadline on CrisisEvent
-- ══════════════════════════════════════════════════════════════════

IF COL_LENGTH('bcm.crisis_events','pdpl_72h_deadline_at') IS NULL
    -- detection_time + 72 hours when personal_data_affected = 1.
    -- The service writes this on create/update; nulled when personal data
    -- isn't affected so dashboards skip the row.
    ALTER TABLE bcm.crisis_events ADD pdpl_72h_deadline_at DATETIME2 NULL;
GO
IF COL_LENGTH('bcm.crisis_events','pdpl_72h_breached') IS NULL
    ALTER TABLE bcm.crisis_events ADD pdpl_72h_breached BIT NOT NULL DEFAULT 0;
GO

-- Backfill the deadline for existing personal-data crises so old rows are
-- visible in the new dashboard view from day one.
UPDATE bcm.crisis_events
   SET pdpl_72h_deadline_at = DATEADD(HOUR, 72, detection_time)
 WHERE personal_data_affected = 1
   AND detection_time IS NOT NULL
   AND pdpl_72h_deadline_at IS NULL
   AND is_deleted = 0;
GO

-- Mark already-breached rows.
UPDATE bcm.crisis_events
   SET pdpl_72h_breached = 1
 WHERE personal_data_affected   = 1
   AND pdpl_72h_deadline_at    IS NOT NULL
   AND pdpl_notification_drafted_at IS NULL
   AND pdpl_72h_deadline_at    <  SYSUTCDATETIME()
   AND is_deleted = 0;
GO

-- ══════════════════════════════════════════════════════════════════
-- §5.9 #2 — Evidence connector registry
-- ══════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'evidence_connectors' AND schema_id = SCHEMA_ID('evidence'))
BEGIN
    CREATE TABLE evidence.evidence_connectors (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,

        code              NVARCHAR(50)    NOT NULL,    -- AD, DEFENDER, AWS_CONFIG…
        name_en           NVARCHAR(200)   NOT NULL,
        name_ar           NVARCHAR(200)   NULL,

        vendor            NVARCHAR(100)   NULL,        -- Microsoft, AWS, Tenable…
        category          NVARCHAR(50)    NOT NULL,    -- IDENTITY | ENDPOINT | CLOUD | VULN | NETWORK | SIEM | DEVOPS

        -- JSON describing what the connector can fetch:
        -- {"users":true,"groups":true,"mfa_status":true,"last_signin":true}
        capability_json   NVARCHAR(MAX)   NULL,

        -- DRAFT | AVAILABLE | ENABLED | DISABLED | DEPRECATED
        status            NVARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
        version           NVARCHAR(20)    NULL,

        config_json       NVARCHAR(MAX)   NULL,        -- per-tenant endpoint/secret refs
        last_synced_at    DATETIME2       NULL,
        last_sync_status  NVARCHAR(20)    NULL,        -- OK | ERROR | NEVER
        last_sync_note    NVARCHAR(500)   NULL,

        is_deleted        BIT             NOT NULL DEFAULT 0,
        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2       NULL,

        CONSTRAINT uq_evidence_connector_org_code UNIQUE (organisation_id, code),
        CONSTRAINT ck_evidence_connector_status CHECK (status IN
            ('DRAFT','AVAILABLE','ENABLED','DISABLED','DEPRECATED')),
        CONSTRAINT ck_evidence_connector_category CHECK (category IN
            ('IDENTITY','ENDPOINT','CLOUD','VULN','NETWORK','SIEM','DEVOPS','OTHER'))
    );
    CREATE INDEX IX_evidence_connectors_org_status
        ON evidence.evidence_connectors(organisation_id, status);
END
GO

-- Seed the 10 catalog entries per organisation for which we already have a
-- risk register (tenant id = 1 in dev; in prod the connector-bootstrap
-- service will seed on tenant creation). Guarded so re-running this
-- migration is idempotent.
DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_connectors WHERE organisation_id = @org AND code = 'AD')
INSERT INTO evidence.evidence_connectors
   (organisation_id, code, name_en, name_ar, vendor, category, capability_json, version, last_sync_status)
VALUES
   (@org, 'AD',           N'Active Directory',           N'الدليل النشط',                   N'Microsoft', N'IDENTITY',
    N'{"users":true,"groups":true,"last_signin":true,"disabled_accounts":true}', N'1.0', N'NEVER'),
   (@org, 'AAD',          N'Entra ID (Azure AD)',        N'هوية إنترا',                      N'Microsoft', N'IDENTITY',
    N'{"users":true,"mfa_status":true,"conditional_access":true,"signin_risk":true}', N'1.0', N'NEVER'),
   (@org, 'DEFENDER',     N'Microsoft Defender for Endpoint', N'مايكروسوفت ديفندر للنقاط الطرفية', N'Microsoft', N'ENDPOINT',
    N'{"machines":true,"vulns":true,"alerts":true,"av_status":true}', N'1.0', N'NEVER'),
   (@org, 'AZURE_POLICY', N'Azure Policy Compliance',     N'سياسات أزور',                     N'Microsoft', N'CLOUD',
    N'{"policy_state":true,"non_compliant_resources":true,"initiative_coverage":true}', N'1.0', N'NEVER'),
   (@org, 'SENTINEL',     N'Microsoft Sentinel',          N'مايكروسوفت سنتنل',                N'Microsoft', N'SIEM',
    N'{"alerts":true,"incidents":true,"watchlists":true}', N'1.0', N'NEVER'),
   (@org, 'AWS_CONFIG',   N'AWS Config Rules',            N'قواعد AWS Config',                N'AWS',       N'CLOUD',
    N'{"rule_state":true,"non_compliant_resources":true,"history":true}', N'1.0', N'NEVER'),
   (@org, 'TENABLE',      N'Tenable.io',                  N'تنابل لإدارة الثغرات',            N'Tenable',   N'VULN',
    N'{"scans":true,"vulns":true,"assets":true}', N'1.0', N'NEVER'),
   (@org, 'QUALYS',       N'Qualys VMDR',                  N'كواليس لإدارة الثغرات',           N'Qualys',    N'VULN',
    N'{"vulns":true,"assets":true,"scans":true}', N'1.0', N'NEVER'),
   (@org, 'ZSCALER',      N'Zscaler Internet Access',     N'زي سكيلر',                        N'Zscaler',   N'NETWORK',
    N'{"users":true,"blocked_traffic":true,"dlp_events":true}', N'1.0', N'NEVER'),
   (@org, 'SPLUNK',       N'Splunk Enterprise Security',  N'سبلونك الأمنية',                  N'Splunk',    N'SIEM',
    N'{"alerts":true,"notable_events":true,"searches":true}', N'1.0', N'NEVER'),
   (@org, 'GITHUB',       N'GitHub Advanced Security',    N'حماية GitHub المتقدمة',           N'GitHub',    N'DEVOPS',
    N'{"code_scanning":true,"secret_scanning":true,"dependabot":true}', N'1.0', N'NEVER');
GO

PRINT 'V115 round-2 engines (risk attestation + DSR/PDPL deadlines + evidence connectors) applied.';
GO

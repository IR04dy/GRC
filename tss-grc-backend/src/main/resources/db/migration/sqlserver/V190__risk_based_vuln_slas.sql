-- V190 — Sprint 11: Risk-Based Vulnerability SLA engine.
--
-- Today every vulnerability in the platform gets the same SLA based
-- purely on CVSS severity (7d CRITICAL / 30d HIGH / 60d MEDIUM / 90d LOW,
-- per NCA ECC-2). That's regulator-safe but operationally wrong: a
-- CRITICAL CVE on the customer-facing online-banking portal demands a
-- one-hour clock, while the same CVE on an internal HR system can live
-- on the regulator-default 7-day clock without breaching anything.
--
-- This slice blends THREE drivers into a single accelerated clock:
--
--   1. CVSS severity      (CRITICAL / HIGH / MEDIUM / LOW)
--   2. Asset exposure     (PUBLIC / PARTNER / INTERNAL / RESTRICTED)
--   3. Business criticality (CRITICAL / HIGH / MEDIUM / LOW)
--
--   effective_sla_hours = base_sla_hours
--                       × exposure_multiplier
--                       × criticality_multiplier
--   (then floored at 0.25 × base — prevents pathological sub-minute SLAs)
--
-- Auto-escalation:
--   - WARNING  fires at 75% of the clock (configurable)
--   - BREACHED fires at 100% (configurable)
--
-- Regulatory anchors:
--   - NCA ECC-2 control 2-12 (Vulnerability Management) requires a
--     vulnerability-management programme that is "aligned with the
--     organisation's business and risk context". Flat-severity SLAs do
--     not meet the "business-aligned" bar; a risk-based SLA does.
--   - SAMA Cyber Security Framework 3.3.8 requires "vulnerability
--     remediation timelines proportionate to business impact".
--   - CISA SSVC (Stakeholder-Specific Vulnerability Categorization) is
--     the emerging industry standard: exposure + asset criticality +
--     exploitation status drive prioritisation, not raw CVSS.
--
-- Two tables under the existing `cyber` schema:
--
--   cyber.vulnerability_sla_policy   — one row per policy per tenant
--                                       (default policy + per-business-unit overrides)
--   cyber.vulnerability_sla_finding  — one row per vulnerability with
--                                       the computed effective clock
--
-- Soft-delete + audit + ROWVERSION on both. QUOTED_IDENTIFIER ON for
-- filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'cyber') IS NULL EXEC(N'CREATE SCHEMA cyber');
GO

-- ─── 1. Policy table ──────────────────────────────────────────────
IF OBJECT_ID(N'cyber.vulnerability_sla_policy', N'U') IS NULL
BEGIN
    CREATE TABLE cyber.vulnerability_sla_policy (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        policy_code                   NVARCHAR(64)  NOT NULL,
        title_en                      NVARCHAR(255) NOT NULL,
        title_ar                      NVARCHAR(255) NULL,
        description_en                NVARCHAR(MAX) NULL,
        description_ar                NVARCHAR(MAX) NULL,

        -- Base SLA in hours per CVSS severity. Defaults match a
        -- regulator-safe baseline for a Saudi tier-1 bank: 1h CRITICAL
        -- (board-level), 4h HIGH (CISO-level), 24h MEDIUM (SOC daily),
        -- 72h LOW (weekly hygiene).
        critical_sla_hours            DECIMAL(7,2) NOT NULL DEFAULT 1.00,
        high_sla_hours                DECIMAL(7,2) NOT NULL DEFAULT 4.00,
        medium_sla_hours              DECIMAL(7,2) NOT NULL DEFAULT 24.00,
        low_sla_hours                 DECIMAL(7,2) NOT NULL DEFAULT 72.00,

        -- Exposure multipliers — public-facing is the most accelerated;
        -- restricted is allowed to be slower because the blast radius is
        -- the smallest. Numbers chosen to land on the SSVC "internet-
        -- accessible" prioritisation curve.
        exposure_public_mul           DECIMAL(5,3) NOT NULL DEFAULT 0.500,
        exposure_partner_mul          DECIMAL(5,3) NOT NULL DEFAULT 0.750,
        exposure_internal_mul         DECIMAL(5,3) NOT NULL DEFAULT 1.000,
        exposure_restricted_mul       DECIMAL(5,3) NOT NULL DEFAULT 1.500,

        -- Criticality multipliers — CRITICAL business systems get the
        -- shortest clock. LOW criticality is allowed to be 1.5× as long.
        criticality_critical_mul      DECIMAL(5,3) NOT NULL DEFAULT 0.500,
        criticality_high_mul          DECIMAL(5,3) NOT NULL DEFAULT 0.750,
        criticality_medium_mul        DECIMAL(5,3) NOT NULL DEFAULT 1.000,
        criticality_low_mul           DECIMAL(5,3) NOT NULL DEFAULT 1.500,

        -- Escalation triggers: emit a WARNING event when we cross
        -- escalation_warning_pct of the clock; emit a BREACHED event
        -- when we cross escalation_critical_pct. Both are fractions of
        -- the effective_sla_hours window.
        escalation_warning_pct        DECIMAL(4,3) NOT NULL DEFAULT 0.750,
        escalation_critical_pct       DECIMAL(4,3) NOT NULL DEFAULT 1.000,

        -- Exactly one policy per tenant is marked is_default = 1; that
        -- policy is applied by VulnerabilitySlaComputeService when a
        -- finding has no explicit policy assignment.
        is_default                    BIT NOT NULL DEFAULT 0,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_vln_sla_policy_warn_pct CHECK (escalation_warning_pct  > 0 AND escalation_warning_pct  <= 1),
        CONSTRAINT CK_vln_sla_policy_crit_pct CHECK (escalation_critical_pct > 0 AND escalation_critical_pct <= 1.5),
        CONSTRAINT CK_vln_sla_policy_pct_order CHECK (escalation_warning_pct < escalation_critical_pct)
    );
END
GO

-- Filtered unique index — policy_code unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_vln_sla_policy_code' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_policy'))
    CREATE UNIQUE INDEX UQ_vln_sla_policy_code ON cyber.vulnerability_sla_policy (organisation_id, policy_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_vln_sla_policy_external' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_policy'))
    CREATE UNIQUE INDEX UQ_vln_sla_policy_external ON cyber.vulnerability_sla_policy (external_id) WHERE is_deleted = 0;
GO
-- Filtered unique index — only ONE policy per tenant may be is_default = 1.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_vln_sla_policy_default' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_policy'))
    CREATE UNIQUE INDEX UQ_vln_sla_policy_default ON cyber.vulnerability_sla_policy (organisation_id) WHERE is_default = 1 AND is_deleted = 0;
GO

-- ─── 2. Finding table ─────────────────────────────────────────────
IF OBJECT_ID(N'cyber.vulnerability_sla_finding', N'U') IS NULL
BEGIN
    CREATE TABLE cyber.vulnerability_sla_finding (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Soft FK to the existing vulnerability register. Soft because
        -- the platform carries two parallel vulnerability tables (cyber
        -- + cyber_lc — pre-existing tech-debt that Sprint 11 doesn't
        -- consolidate). NOT NULL because every SLA row MUST belong to
        -- exactly one vulnerability.
        vulnerability_id              BIGINT NOT NULL,

        -- FK to the applied policy. NOT NULL — every row knows which
        -- policy produced its effective_sla_hours.
        policy_id                     BIGINT NOT NULL,

        -- Snapshot of the three drivers at compute time. Persisting
        -- them (rather than re-deriving on every read) means the audit
        -- log can prove which inputs produced which clock. If the
        -- underlying asset is later re-classified, the audit row stays
        -- intact until the next recompute.
        cvss_severity                 NVARCHAR(20)  NOT NULL,
        -- Soft FK to cyber.attack_surface_asset.id. Nullable because
        -- not every vulnerability is bound to an ASM-registered asset
        -- (some are infrastructure-only). When NULL the compute service
        -- falls back to INTERNAL / MEDIUM defaults.
        asset_id                      BIGINT        NULL,
        asset_exposure                NVARCHAR(20)  NOT NULL,
        asset_criticality             NVARCHAR(20)  NOT NULL,

        -- The base hours pulled from the policy for the given severity
        -- (e.g. 4.00 for HIGH if policy keeps the default high_sla_hours).
        base_sla_hours                DECIMAL(7,2) NOT NULL,
        -- The clock after multipliers + 0.25× floor. This is the
        -- canonical answer the dashboard ranks against.
        effective_sla_hours           DECIMAL(7,2) NOT NULL,

        opened_at                     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        due_at                        DATETIME2(3)  NOT NULL,
        last_recomputed_at            DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        last_escalation_at            DATETIME2(3)  NULL,

        -- OPEN/WARNING/BREACHED/REMEDIATED/ACCEPTED_RISK/FALSE_POSITIVE
        status                        NVARCHAR(20)  NOT NULL DEFAULT N'OPEN',
        remediated_at                 DATETIME2(3)  NULL,
        remediated_by                 BIGINT        NULL,

        is_deleted                    BIT NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_vln_sla_finding_policy FOREIGN KEY (policy_id)
            REFERENCES cyber.vulnerability_sla_policy(id),
        CONSTRAINT CK_vln_sla_finding_severity CHECK (cvss_severity IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_vln_sla_finding_exposure CHECK (asset_exposure IN
            (N'PUBLIC', N'PARTNER', N'INTERNAL', N'RESTRICTED')),
        CONSTRAINT CK_vln_sla_finding_criticality CHECK (asset_criticality IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_vln_sla_finding_status CHECK (status IN
            (N'OPEN', N'WARNING', N'BREACHED', N'REMEDIATED', N'ACCEPTED_RISK', N'FALSE_POSITIVE'))
    );
END
GO

-- Exactly one live SLA row per (tenant, vulnerability).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_vln_sla_finding_vuln' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_finding'))
    CREATE UNIQUE INDEX UQ_vln_sla_finding_vuln ON cyber.vulnerability_sla_finding (organisation_id, vulnerability_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_vln_sla_finding_external' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_finding'))
    CREATE UNIQUE INDEX UQ_vln_sla_finding_external ON cyber.vulnerability_sla_finding (external_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_vln_sla_finding_status' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_finding'))
    CREATE INDEX IX_vln_sla_finding_status ON cyber.vulnerability_sla_finding (organisation_id, status) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_vln_sla_finding_due' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_finding'))
    CREATE INDEX IX_vln_sla_finding_due ON cyber.vulnerability_sla_finding (organisation_id, due_at) WHERE is_deleted = 0 AND status IN (N'OPEN', N'WARNING');
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_vln_sla_finding_policy' AND object_id = OBJECT_ID(N'cyber.vulnerability_sla_finding'))
    CREATE INDEX IX_vln_sla_finding_policy ON cyber.vulnerability_sla_finding (policy_id) WHERE is_deleted = 0;
GO

-- ─── 3. Pre-seed default policy per organisation ──────────────────
-- Insert exactly one default policy per tenant. Idempotent — skipped
-- if the policy_code already exists for that org. Enumerates every
-- org in platform.organisations; falls back to org_id=1 in dev DBs
-- where the platform table might not be present.
--
-- The bilingual title is a Saudi-native phrase that operators will
-- recognise on the dashboard: this is "the default policy aligned with
-- the Essential Cybersecurity Controls" rather than a literal
-- translation of "default policy".

IF OBJECT_ID(N'platform.organisations', N'U') IS NOT NULL
BEGIN
    INSERT INTO cyber.vulnerability_sla_policy
        (organisation_id, policy_code, title_en, title_ar, description_en, description_ar,
         critical_sla_hours, high_sla_hours, medium_sla_hours, low_sla_hours,
         exposure_public_mul, exposure_partner_mul, exposure_internal_mul, exposure_restricted_mul,
         criticality_critical_mul, criticality_high_mul, criticality_medium_mul, criticality_low_mul,
         escalation_warning_pct, escalation_critical_pct, is_default)
    SELECT
        o.id, N'VLN-SLA-2026-DEFAULT-SAUDI',
        N'Default policy — aligned with the Essential Cybersecurity Controls',
        N'السياسة الافتراضية — متوافقة مع ضوابط الأمن السيبراني الأساسية',
        N'Saudi-native baseline policy. Aligns base hours with the NCA ECC-2 control 2-12 expectation (CRITICAL 1h, HIGH 4h, MEDIUM 24h, LOW 72h) and accelerates the clock when the affected asset is public-facing or business-critical, in line with SAMA CSF 3.3.8 and CISA SSVC.',
        N'سياسة أساسية مكيّفة للسياق السعودي. تضبط الساعات الأساسية وفق متطلبات الضابط 2-12 من ضوابط الأمن السيبراني الأساسية للهيئة الوطنية للأمن السيبراني (حرج خلال ساعة، مرتفع خلال 4 ساعات، متوسط خلال 24 ساعة، منخفض خلال 72 ساعة)، وتُسرّع المهلة عندما يكون الأصل المتأثر متاحاً للعموم أو حرجاً للأعمال، اتساقاً مع الإطار السيبراني للبنك المركزي السعودي 3.3.8 ومنهجية CISA SSVC.',
        1.00, 4.00, 24.00, 72.00,
        0.500, 0.750, 1.000, 1.500,
        0.500, 0.750, 1.000, 1.500,
        0.750, 1.000, 1
    FROM platform.organisations o
    WHERE NOT EXISTS (
        SELECT 1 FROM cyber.vulnerability_sla_policy p
         WHERE p.organisation_id = o.id
           AND p.policy_code = N'VLN-SLA-2026-DEFAULT-SAUDI'
           AND p.is_deleted = 0);
END
ELSE
BEGIN
    -- Dev fallback — insert for org 1 only when no platform.organisations table.
    IF NOT EXISTS (
        SELECT 1 FROM cyber.vulnerability_sla_policy
         WHERE organisation_id = 1 AND policy_code = N'VLN-SLA-2026-DEFAULT-SAUDI' AND is_deleted = 0)
    INSERT INTO cyber.vulnerability_sla_policy
        (organisation_id, policy_code, title_en, title_ar, description_en, description_ar,
         critical_sla_hours, high_sla_hours, medium_sla_hours, low_sla_hours,
         exposure_public_mul, exposure_partner_mul, exposure_internal_mul, exposure_restricted_mul,
         criticality_critical_mul, criticality_high_mul, criticality_medium_mul, criticality_low_mul,
         escalation_warning_pct, escalation_critical_pct, is_default)
    VALUES
        (1, N'VLN-SLA-2026-DEFAULT-SAUDI',
         N'Default policy — aligned with the Essential Cybersecurity Controls',
         N'السياسة الافتراضية — متوافقة مع ضوابط الأمن السيبراني الأساسية',
         N'Saudi-native baseline policy. Aligns base hours with the NCA ECC-2 control 2-12 expectation (CRITICAL 1h, HIGH 4h, MEDIUM 24h, LOW 72h) and accelerates the clock when the affected asset is public-facing or business-critical, in line with SAMA CSF 3.3.8 and CISA SSVC.',
         N'سياسة أساسية مكيّفة للسياق السعودي. تضبط الساعات الأساسية وفق متطلبات الضابط 2-12 من ضوابط الأمن السيبراني الأساسية للهيئة الوطنية للأمن السيبراني (حرج خلال ساعة، مرتفع خلال 4 ساعات، متوسط خلال 24 ساعة، منخفض خلال 72 ساعة)، وتُسرّع المهلة عندما يكون الأصل المتأثر متاحاً للعموم أو حرجاً للأعمال، اتساقاً مع الإطار السيبراني للبنك المركزي السعودي 3.3.8 ومنهجية CISA SSVC.',
         1.00, 4.00, 24.00, 72.00,
         0.500, 0.750, 1.000, 1.500,
         0.500, 0.750, 1.000, 1.500,
         0.750, 1.000, 1);
END
GO

-- ─── Done. ─────────────────────────────────────────────────────────
PRINT N'V190 — Risk-Based Vulnerability SLA engine: 2 tables + default policy per org.';
GO

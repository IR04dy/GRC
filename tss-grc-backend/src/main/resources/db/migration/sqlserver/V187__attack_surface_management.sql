-- V187 — Sprint 11: Attack Surface Management (ASM) register.
--
-- Every Saudi-regulated bank has a sprawling external attack surface —
-- production banking portals, customer-facing mobile APIs, marketing
-- microsites, vendor-integration endpoints, expired DNS records,
-- shadow-IT SaaS subscriptions. Today there's no single source of
-- truth: the SOC discovers exposed assets reactively (during an
-- incident or a Tenable scan), and the Cyber GRC team chases stale
-- spreadsheets. ASM gives the CISO a live inventory plus risk-based
-- prioritisation.
--
-- Three tables under the existing `cyber` schema:
--
--   cyber.attack_surface_asset      — discovered/registered external assets
--   cyber.attack_surface_finding    — issues attached to a specific asset
--   cyber.attack_surface_shadow_it  — discovered unsanctioned SaaS / cloud usage
--
-- Why three tables (and not one wider one)? Because they have
-- meaningfully different lifecycles:
--   - assets: long-lived inventory rows, transitioned manually or by a
--     discovery runner; carry their own risk score that aggregates
--     finding severity, exposure, and criticality
--   - findings: ephemeral defect rows that open + close against an
--     asset (think Jira tickets for the asset); cross-link to CAPA
--   - shadow IT: a *pending governance review* queue — sits outside
--     the asset register until promoted (sanctioned & moved under TPRM)
--     or blocked
--
-- Regulatory anchors:
--   - NCA ECC-2 control 2-3 (Asset Management): "comprehensive
--     inventory of all assets including external-facing".
--   - SAMA Cyber Security Framework 4.2.1: "Bank shall identify and
--     inventory all internet-facing assets".
--   - NCA OTCC: operational-technology assets discovered outside the
--     formal inventory must be flagged and brought under governance
--     within 30 days — shadow-IT is the SaaS analogue.
--   - CISA Cyber Hygiene Services (referenced by SAMA SCRO): continuous
--     monitoring of internet-facing assets requires a register to
--     attest against.
--
-- Risk-score formula (computed in service layer, persisted here):
--   risk_score = severityWeight × exposureMultiplier × criticalityMultiplier
--     where severityWeight  ∈ {CRITICAL=100, HIGH=70, MEDIUM=40, LOW=15}
--           exposureMul     ∈ {PUBLIC=1.0, PARTNER=0.7, INTERNAL=0.4, RESTRICTED=0.2}
--           criticalityMul  ∈ {CRITICAL=1.0, HIGH=0.85, MEDIUM=0.6,  LOW=0.3}
-- "severity" for the asset is the *highest* open-finding severity, or
-- LOW if no open findings exist. Recomputed on every finding mutation
-- so the dashboard prioritisation reflects current state.
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'cyber') IS NULL EXEC(N'CREATE SCHEMA cyber');
GO

-- ─── 1. Assets ─────────────────────────────────────────────────────
IF OBJECT_ID(N'cyber.attack_surface_asset', N'U') IS NULL
BEGIN
    CREATE TABLE cyber.attack_surface_asset (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        asset_code                    NVARCHAR(64)  NOT NULL,
        title_en                      NVARCHAR(255) NOT NULL,
        title_ar                      NVARCHAR(255) NULL,
        description_en                NVARCHAR(MAX) NULL,
        description_ar                NVARCHAR(MAX) NULL,

        -- WEB_APPLICATION/MOBILE_APP/API_ENDPOINT/DNS_RECORD/CERTIFICATE/
        -- IP_RANGE/EMAIL_SERVER/FILE_SHARE/VPN_GATEWAY/CLOUD_BUCKET/GIT_REPO/
        -- ADMIN_PORTAL/MARKETING_SITE/SHADOW_IT_SAAS
        asset_type                    NVARCHAR(40)  NOT NULL,

        hostname                      NVARCHAR(255) NULL,
        ip_address                    NVARCHAR(64)  NULL,
        port                          INT           NULL,

        -- HTTP/HTTPS/TCP/UDP/SSH/FTP/SMTP/DNS/ICMP
        protocol                      NVARCHAR(20)  NULL,

        cert_subject                  NVARCHAR(500) NULL,
        cert_issuer                   NVARCHAR(255) NULL,
        cert_expires_at               DATETIME2(3)  NULL,

        asset_owner_user_id           BIGINT        NOT NULL,
        business_unit_en              NVARCHAR(255) NULL,
        business_unit_ar              NVARCHAR(255) NULL,

        -- PUBLIC/PARTNER/INTERNAL/RESTRICTED
        exposure                      NVARCHAR(20)  NOT NULL,
        -- CRITICAL/HIGH/MEDIUM/LOW
        criticality                   NVARCHAR(20)  NOT NULL,

        -- DISCOVERED/IN_REVIEW/APPROVED/SHADOW_IT_PENDING/RETIRED
        lifecycle_stage               NVARCHAR(40)  NOT NULL DEFAULT N'DISCOVERED',

        -- MANUAL/TENABLE/CROWDSTRIKE/DNS_SCAN/CERT_TRANSPARENCY/SHODAN/
        -- CCM_RUNNER/CUSTOMER_TICKET
        discovery_source              NVARCHAR(40)  NOT NULL,

        discovered_at                 DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        approved_at                   DATETIME2(3)  NULL,
        retired_at                    DATETIME2(3)  NULL,

        tags                          NVARCHAR(500) NULL,
        risk_score                    DECIMAL(5,2)  NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_asm_asset_type CHECK (asset_type IN
            (N'WEB_APPLICATION', N'MOBILE_APP', N'API_ENDPOINT', N'DNS_RECORD',
             N'CERTIFICATE', N'IP_RANGE', N'EMAIL_SERVER', N'FILE_SHARE',
             N'VPN_GATEWAY', N'CLOUD_BUCKET', N'GIT_REPO', N'ADMIN_PORTAL',
             N'MARKETING_SITE', N'SHADOW_IT_SAAS')),
        CONSTRAINT CK_asm_asset_protocol CHECK (protocol IS NULL OR protocol IN
            (N'HTTP', N'HTTPS', N'TCP', N'UDP', N'SSH', N'FTP', N'SMTP', N'DNS', N'ICMP')),
        CONSTRAINT CK_asm_asset_exposure CHECK (exposure IN
            (N'PUBLIC', N'PARTNER', N'INTERNAL', N'RESTRICTED')),
        CONSTRAINT CK_asm_asset_criticality CHECK (criticality IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_asm_asset_lifecycle CHECK (lifecycle_stage IN
            (N'DISCOVERED', N'IN_REVIEW', N'APPROVED', N'SHADOW_IT_PENDING', N'RETIRED')),
        CONSTRAINT CK_asm_asset_source CHECK (discovery_source IN
            (N'MANUAL', N'TENABLE', N'CROWDSTRIKE', N'DNS_SCAN', N'CERT_TRANSPARENCY',
             N'SHODAN', N'CCM_RUNNER', N'CUSTOMER_TICKET'))
    );
END
GO

-- Filtered unique index — asset_code is unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_asm_asset_code' AND object_id = OBJECT_ID(N'cyber.attack_surface_asset'))
    CREATE UNIQUE INDEX UQ_asm_asset_code ON cyber.attack_surface_asset (organisation_id, asset_code) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_asm_asset_external' AND object_id = OBJECT_ID(N'cyber.attack_surface_asset'))
    CREATE UNIQUE INDEX UQ_asm_asset_external ON cyber.attack_surface_asset (external_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_asset_type_lifecycle' AND object_id = OBJECT_ID(N'cyber.attack_surface_asset'))
    CREATE INDEX IX_asm_asset_type_lifecycle ON cyber.attack_surface_asset (organisation_id, asset_type, lifecycle_stage) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_asset_exposure_crit' AND object_id = OBJECT_ID(N'cyber.attack_surface_asset'))
    CREATE INDEX IX_asm_asset_exposure_crit ON cyber.attack_surface_asset (organisation_id, exposure, criticality) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_asset_cert_expiry' AND object_id = OBJECT_ID(N'cyber.attack_surface_asset'))
    CREATE INDEX IX_asm_asset_cert_expiry ON cyber.attack_surface_asset (organisation_id, cert_expires_at) WHERE is_deleted = 0 AND cert_expires_at IS NOT NULL;
GO

-- ─── 2. Findings ───────────────────────────────────────────────────
IF OBJECT_ID(N'cyber.attack_surface_finding', N'U') IS NULL
BEGIN
    CREATE TABLE cyber.attack_surface_finding (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        asset_id                      BIGINT NOT NULL,

        -- EXPIRED_CERT/WEAK_CIPHER/UNPATCHED/EXPOSED_PORT/MISSING_HEADER/
        -- OUTDATED_FRAMEWORK/UNVERIFIED_OWNER/NO_WAF/NO_RATE_LIMIT/DNS_TAKEOVER_RISK
        finding_type                  NVARCHAR(40)  NOT NULL,
        severity                      NVARCHAR(20)  NOT NULL,

        -- TENABLE/CROWDSTRIKE/CCM_RUNNER/MANUAL/AUTOMATED_PROBE
        source                        NVARCHAR(40)  NOT NULL,
        source_ref                    NVARCHAR(255) NULL,

        description_en                NVARCHAR(2000) NOT NULL,
        description_ar                NVARCHAR(2000) NULL,
        recommendation_en             NVARCHAR(1000) NULL,
        recommendation_ar             NVARCHAR(1000) NULL,

        opened_at                     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        closed_at                     DATETIME2(3)  NULL,

        -- OPEN/INVESTIGATING/ACCEPTED_RISK/FIXED/FALSE_POSITIVE/OUT_OF_SCOPE
        status                        NVARCHAR(20)  NOT NULL DEFAULT N'OPEN',

        -- Soft FK to capa.capa_tasks — set when finding is escalated to CAPA.
        capa_task_id                  BIGINT        NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_asm_finding_asset FOREIGN KEY (asset_id)
            REFERENCES cyber.attack_surface_asset(id),
        CONSTRAINT CK_asm_finding_type CHECK (finding_type IN
            (N'EXPIRED_CERT', N'WEAK_CIPHER', N'UNPATCHED', N'EXPOSED_PORT',
             N'MISSING_HEADER', N'OUTDATED_FRAMEWORK', N'UNVERIFIED_OWNER',
             N'NO_WAF', N'NO_RATE_LIMIT', N'DNS_TAKEOVER_RISK')),
        CONSTRAINT CK_asm_finding_severity CHECK (severity IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW', N'INFO')),
        CONSTRAINT CK_asm_finding_source CHECK (source IN
            (N'TENABLE', N'CROWDSTRIKE', N'CCM_RUNNER', N'MANUAL', N'AUTOMATED_PROBE')),
        CONSTRAINT CK_asm_finding_status CHECK (status IN
            (N'OPEN', N'INVESTIGATING', N'ACCEPTED_RISK', N'FIXED', N'FALSE_POSITIVE', N'OUT_OF_SCOPE'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_asm_finding_external' AND object_id = OBJECT_ID(N'cyber.attack_surface_finding'))
    CREATE UNIQUE INDEX UQ_asm_finding_external ON cyber.attack_surface_finding (external_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_finding_asset_status' AND object_id = OBJECT_ID(N'cyber.attack_surface_finding'))
    CREATE INDEX IX_asm_finding_asset_status ON cyber.attack_surface_finding (organisation_id, asset_id, status) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_finding_severity_status' AND object_id = OBJECT_ID(N'cyber.attack_surface_finding'))
    CREATE INDEX IX_asm_finding_severity_status ON cyber.attack_surface_finding (organisation_id, severity, status) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_finding_capa' AND object_id = OBJECT_ID(N'cyber.attack_surface_finding'))
    CREATE INDEX IX_asm_finding_capa ON cyber.attack_surface_finding (organisation_id, capa_task_id) WHERE is_deleted = 0 AND capa_task_id IS NOT NULL;
GO

-- ─── 3. Shadow-IT ──────────────────────────────────────────────────
IF OBJECT_ID(N'cyber.attack_surface_shadow_it', N'U') IS NULL
BEGIN
    CREATE TABLE cyber.attack_surface_shadow_it (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        service_name                  NVARCHAR(255) NOT NULL,
        service_name_ar               NVARCHAR(255) NULL,
        vendor_domain                 NVARCHAR(255) NULL,

        -- EMAIL_SIGNUPS/EXPENSE_REPORT/NETWORK_TRAFFIC/USER_DECLARATION
        discovered_via                NVARCHAR(40)  NOT NULL,
        first_observed_at             DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        observed_user_count           INT           NULL,

        -- PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED/UNKNOWN
        data_classification           NVARCHAR(20)  NOT NULL DEFAULT N'UNKNOWN',
        pdpl_data_processed           BIT           NOT NULL DEFAULT 0,

        -- PENDING_REVIEW/IN_PROGRESS/SANCTIONED/BLOCKED/MIGRATED_TO_SANCTIONED/NA
        status                        NVARCHAR(40)  NOT NULL DEFAULT N'PENDING_REVIEW',

        -- Soft FK to tprm.vendor — set when shadow-IT is promoted to a sanctioned vendor.
        sanctioned_vendor_id          BIGINT        NULL,
        -- Optional soft FK to cyber.attack_surface_asset — set when also promoted to ASM register.
        promoted_asset_id             BIGINT        NULL,

        assigned_to_user_id           BIGINT        NULL,
        deadline                      DATE          NULL,

        notes_en                      NVARCHAR(2000) NULL,
        notes_ar                      NVARCHAR(2000) NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_asm_shadow_discovered_via CHECK (discovered_via IN
            (N'EMAIL_SIGNUPS', N'EXPENSE_REPORT', N'NETWORK_TRAFFIC', N'USER_DECLARATION')),
        CONSTRAINT CK_asm_shadow_data_class CHECK (data_classification IN
            (N'PUBLIC', N'INTERNAL', N'CONFIDENTIAL', N'RESTRICTED', N'UNKNOWN')),
        CONSTRAINT CK_asm_shadow_status CHECK (status IN
            (N'PENDING_REVIEW', N'IN_PROGRESS', N'SANCTIONED', N'BLOCKED',
             N'MIGRATED_TO_SANCTIONED', N'NA'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_asm_shadow_external' AND object_id = OBJECT_ID(N'cyber.attack_surface_shadow_it'))
    CREATE UNIQUE INDEX UQ_asm_shadow_external ON cyber.attack_surface_shadow_it (external_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_shadow_status' AND object_id = OBJECT_ID(N'cyber.attack_surface_shadow_it'))
    CREATE INDEX IX_asm_shadow_status ON cyber.attack_surface_shadow_it (organisation_id, status) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_asm_shadow_pdpl' AND object_id = OBJECT_ID(N'cyber.attack_surface_shadow_it'))
    CREATE INDEX IX_asm_shadow_pdpl ON cyber.attack_surface_shadow_it (organisation_id, pdpl_data_processed, status) WHERE is_deleted = 0;
GO

-- ─── 4. Seed 12 assets for org=1 (TSS Bank) ────────────────────────
-- Saudi-native bilingual EN/AR. Every asset is realistic for a tier-1
-- Saudi bank in 2026. Owner FK uses 1 (admin) as the placeholder
-- created_by — the real CISO assignment happens via the UI.

-- ASM-2026-001 — Customer-facing online banking portal
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-001' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-001',
     N'Customer online banking portal',
     N'بوّابة الخدمات المصرفية الإلكترونية للأفراد',
     N'Public-facing retail-banking web portal. Tier-1 surface — PIF-grade scrutiny applies; any defect here is a board-level event. Carries PDPL Article 5 customer personal data.',
     N'بوّابة الخدمات المصرفية للأفراد المعروضة للعموم. سطح من الفئة الأولى يخضع لتدقيق على مستوى صندوق الاستثمارات العامة؛ أي خلل فيها يُعدّ حدثاً يُرفع لمجلس الإدارة. تحوي بيانات شخصية للعملاء وفق المادة 5 من نظام حماية البيانات الشخصية.',
     N'WEB_APPLICATION', N'banking.tss-bank.sa', 443, N'HTTPS',
     N'CN=*.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 180, SYSUTCDATETIME()),
     1, N'Retail Banking', N'الخدمات المصرفية للأفراد',
     N'PUBLIC', N'CRITICAL', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -730, SYSUTCDATETIME()), DATEADD(DAY, -728, SYSUTCDATETIME()),
     N'production,pdpl,retail,sama-priority', 100.00);
GO

-- ASM-2026-002 — Mada Pay API endpoint
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-002' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-002',
     N'Mada Pay API endpoint',
     N'نقطة الاستدعاء البرمجي لمدى Pay',
     N'mTLS-mutual API endpoint serving the Mada Pay scheme. Outage here breaks the merchant POS network — SAMA reportable within two hours.',
     N'نقطة استدعاء برمجي بمصادقة متبادلة (mTLS) تخدم منظومة مدى Pay. أي انقطاع يكسر شبكة نقاط البيع التجارية، ويستوجب الإبلاغ للبنك المركزي السعودي خلال ساعتين.',
     N'API_ENDPOINT', N'mada-pay.api.tss-bank.sa', 443, N'HTTPS',
     N'CN=mada-pay.api.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 95, SYSUTCDATETIME()),
     1, N'Payments', N'المدفوعات',
     N'PARTNER', N'CRITICAL', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -540, SYSUTCDATETIME()), DATEADD(DAY, -538, SYSUTCDATETIME()),
     N'production,mada,sama-priority,mtls', 70.00);
GO

-- ASM-2026-003 — Tawakkalna integration callback URL
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-003' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-003',
     N'Tawakkalna integration callback URL',
     N'رابط الاستدعاء الراجع للتكامل مع توكلنا',
     N'OAuth2 callback endpoint for the Tawakkalna national-identity SSO flow. Required by SDAIA digital-government integration mandate.',
     N'نقطة استدعاء OAuth2 الراجعة لتدفّق الدخول الموحّد عبر هوية توكلنا الوطنية. مطلوبة وفق توجيهات الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا) للتكامل الرقمي الحكومي.',
     N'API_ENDPOINT', N'tawakkalna-callback.tss-bank.sa', 443, N'HTTPS',
     N'CN=tawakkalna-callback.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 220, SYSUTCDATETIME()),
     1, N'Digital Identity', N'الهوية الرقمية',
     N'PARTNER', N'HIGH', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -365, SYSUTCDATETIME()), DATEADD(DAY, -363, SYSUTCDATETIME()),
     N'production,sdaia,oauth2,partner-integration', 59.50);
GO

-- ASM-2026-004 — Investor relations marketing site
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-004' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-004',
     N'Investor relations marketing site',
     N'الموقع التسويقي لعلاقات المستثمرين',
     N'Static marketing site for investor relations — quarterly earnings, annual report PDFs. Defacement risk is reputational rather than data-loss.',
     N'موقع تسويقي ثابت لعلاقات المستثمرين — يعرض الأرباح الربعية والتقارير السنوية بصيغة PDF. خطر التشويه على هذا الموقع سمعي لا يتعلّق بفقد بيانات.',
     N'MARKETING_SITE', N'ir.tss-bank.sa', 443, N'HTTPS',
     N'CN=ir.tss-bank.sa', N'Let''s Encrypt R3', DATEADD(DAY, 45, SYSUTCDATETIME()),
     1, N'Investor Relations', N'علاقات المستثمرين',
     N'PUBLIC', N'LOW', N'APPROVED', N'CERT_TRANSPARENCY',
     DATEADD(DAY, -300, SYSUTCDATETIME()), DATEADD(DAY, -298, SYSUTCDATETIME()),
     N'production,public,marketing,cma-disclosure', 15.00);
GO

-- ASM-2026-005 — Internal VPN gateway
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-005' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-005',
     N'Internal VPN gateway',
     N'بوّابة الاتصال الافتراضي الداخلي',
     N'Corporate VPN concentrator — entry point for remote staff and third-party engineers. Compromise gives lateral access to the entire internal network.',
     N'مُجمِّع شبكة الاتصال الافتراضي للموظفين والمهندسين الخارجيّين. أي اختراق له يفتح حركة جانبية إلى الشبكة الداخلية بالكامل.',
     N'VPN_GATEWAY', N'vpn.tss-bank.sa', 443, N'HTTPS',
     N'CN=vpn.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 60, SYSUTCDATETIME()),
     1, N'IT Operations', N'عمليات تقنية المعلومات',
     N'PUBLIC', N'HIGH', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -800, SYSUTCDATETIME()), DATEADD(DAY, -798, SYSUTCDATETIME()),
     N'production,vpn,remote-access', 59.50);
GO

-- ASM-2026-006 — SADAD recurring-bill webhook
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-006' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-006',
     N'SADAD recurring-bill webhook',
     N'مستقبل إشعارات سداد للفواتير المتكررة',
     N'Inbound webhook receiving recurring-bill settlement notifications from SADAD. Outage drops customer-utility payment confirmations.',
     N'مستقبل إشعارات وارد من نظام سداد لتسوية الفواتير المتكررة. الانقطاع يُسقط تأكيدات سداد فواتير العملاء للجهات الحكومية والخدمية.',
     N'API_ENDPOINT', N'sadad-webhook.tss-bank.sa', 443, N'HTTPS',
     N'CN=sadad-webhook.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 120, SYSUTCDATETIME()),
     1, N'Payments', N'المدفوعات',
     N'PARTNER', N'CRITICAL', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -600, SYSUTCDATETIME()), DATEADD(DAY, -598, SYSUTCDATETIME()),
     N'production,sadad,webhook,sama-priority', 70.00);
GO

-- ASM-2026-007 — Branch ATM CIDR
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-007' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, ip_address, protocol,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-007',
     N'Branch ATM network CIDR',
     N'نطاق العناوين الشبكي لأجهزة الصرّاف الآلي في الفروع',
     N'/24 CIDR carrying all 312 branch ATMs nationwide. Reachable from the Mada switch network only. Any drift in exposure level is a SAMA matter.',
     N'نطاق عناوين شبكي بحجم /24 يضم كافة أجهزة الصرّاف الآلي في 312 فرعاً على مستوى المملكة. الوصول مقيّد بشبكة محوّل مدى فقط. أي انحراف في مستوى التعرّض يخص البنك المركزي.',
     N'IP_RANGE', N'10.74.0.0/24', N'TCP',
     1, N'Branch Operations', N'عمليات الفروع',
     N'PARTNER', N'HIGH', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -1200, SYSUTCDATETIME()), DATEADD(DAY, -1198, SYSUTCDATETIME()),
     N'production,atm,branch,mada-switch', 59.50);
GO

-- ASM-2026-008 — Corporate email gateway
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-008' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-008',
     N'Corporate email gateway',
     N'بوّابة البريد الإلكتروني المؤسّسي',
     N'Inbound MX edge for the corporate domain. First filter for phishing + business-email-compromise traffic. SPF/DKIM/DMARC posture is audited quarterly.',
     N'الواجهة المعرّفة بسجل MX للنطاق المؤسّسي. هي خط الترشيح الأول لرسائل التصيّد الاحتيالي وتهديدات اختراق البريد الإلكتروني للأعمال. وضعية SPF/DKIM/DMARC تخضع لمراجعة ربعية.',
     N'EMAIL_SERVER', N'mx.tss-bank.sa', 25, N'SMTP',
     1, N'IT Operations', N'عمليات تقنية المعلومات',
     N'PUBLIC', N'HIGH', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -900, SYSUTCDATETIME()), DATEADD(DAY, -898, SYSUTCDATETIME()),
     N'production,email,phishing-defense', 59.50);
GO

-- ASM-2026-009 — Vendor portal SSO callback
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-009' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-009',
     N'Vendor portal SSO callback',
     N'الاستدعاء الراجع للدخول الموحّد في بوّابة الموردين',
     N'SAML/OIDC callback for the procurement vendor portal. Compromise allows third-party impersonation in the supplier-onboarding flow.',
     N'استدعاء راجع SAML/OIDC لبوّابة الموردين الخاصة بإدارة المشتريات. أي اختراق يسمح بانتحال شخصية طرف ثالث في مسار إدخال الموردين.',
     N'API_ENDPOINT', N'vendor-sso.tss-bank.sa', 443, N'HTTPS',
     1, N'Procurement', N'المشتريات',
     N'PARTNER', N'MEDIUM', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -420, SYSUTCDATETIME()), DATEADD(DAY, -418, SYSUTCDATETIME()),
     N'production,vendor-portal,sso,saml', 28.00);
GO

-- ASM-2026-010 — Public-key cert for *.tss-bank.sa
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-010' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, protocol,
     cert_subject, cert_issuer, cert_expires_at,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-010',
     N'Wildcard TLS cert for *.tss-bank.sa',
     N'شهادة TLS العامّة بنمط البدل للنطاق ‎*.tss-bank.sa',
     N'Wildcard TLS certificate covering every customer-facing subdomain. Single cert rotation event affects portal + APIs + marketing all at once. Lifecycle owned by the CISO office.',
     N'شهادة TLS عامّة بنمط البدل تغطّي كل النطاقات الفرعية الموجّهة للعملاء. حدث تدوير واحد يؤثر على البوّابة والـ APIs والمواقع التسويقية مجتمعةً. ملكية دورة الحياة لمكتب الرئيس التنفيذي لأمن المعلومات.',
     N'CERTIFICATE', N'HTTPS',
     N'CN=*.tss-bank.sa', N'DigiCert Saudi Arabia G2', DATEADD(DAY, 25, SYSUTCDATETIME()),
     1, N'CISO Office', N'مكتب الرئيس التنفيذي لأمن المعلومات',
     N'PUBLIC', N'HIGH', N'APPROVED', N'CERT_TRANSPARENCY',
     DATEADD(DAY, -200, SYSUTCDATETIME()), DATEADD(DAY, -198, SYSUTCDATETIME()),
     N'production,wildcard-cert,ciso-owned', 70.00);
GO

-- ASM-2026-011 — Mobile app version-check API
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-011' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-011',
     N'Mobile app version-check API',
     N'نقطة الاستدعاء للتحقق من إصدار تطبيق الجوّال',
     N'Lightweight API the mobile app polls for force-upgrade decisions. Owns no customer data but a takeover lets an attacker force every installed app to redirect.',
     N'نقطة استدعاء خفيفة يستعلمها تطبيق الجوّال لاتخاذ قرار إلزامية الترقية. لا تحوي بيانات عملاء، لكن أي استيلاء عليها يتيح للمهاجم توجيه كل النسخ المثبّتة من التطبيق.',
     N'API_ENDPOINT', N'app-version.tss-bank.sa', 443, N'HTTPS',
     1, N'Mobile Channel', N'قناة الجوّال',
     N'PUBLIC', N'MEDIUM', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -260, SYSUTCDATETIME()), DATEADD(DAY, -258, SYSUTCDATETIME()),
     N'production,mobile,version-check', 40.00);
GO

-- ASM-2026-012 — SIMAH credit-bureau integration endpoint
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_asset WHERE organisation_id = 1 AND asset_code = N'ASM-2026-012' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_asset
    (organisation_id, asset_code, title_en, title_ar, description_en, description_ar,
     asset_type, hostname, port, protocol,
     asset_owner_user_id, business_unit_en, business_unit_ar,
     exposure, criticality, lifecycle_stage, discovery_source,
     discovered_at, approved_at, tags, risk_score)
VALUES
    (1, N'ASM-2026-012',
     N'SIMAH credit-bureau integration endpoint',
     N'نقطة التكامل مع شركة سمة للمعلومات الائتمانية',
     N'mTLS-mutual endpoint exchanging credit-bureau records with SIMAH. Carries PDPL Article 5 personal data plus credit history — RESTRICTED exposure, dedicated VPN tunnel.',
     N'نقطة تكامل بمصادقة متبادلة (mTLS) لتبادل سجلّات الائتمان مع شركة سمة. تحوي بيانات شخصية وفق المادة 5 من نظام حماية البيانات الشخصية إضافة إلى السجلّ الائتماني — تعرّض مقيّد عبر قناة VPN مخصّصة.',
     N'API_ENDPOINT', N'simah-bridge.internal.tss-bank.sa', 8443, N'HTTPS',
     1, N'Credit Risk', N'مخاطر الائتمان',
     N'RESTRICTED', N'CRITICAL', N'APPROVED', N'MANUAL',
     DATEADD(DAY, -1000, SYSUTCDATETIME()), DATEADD(DAY, -998, SYSUTCDATETIME()),
     N'production,simah,pdpl,credit-bureau,mtls', 20.00);
GO

-- ─── 5. Seed 6 findings — only on org=1 assets above ───────────────
-- Use scalar lookups by asset_code so the inserts are independent of
-- the surrounding seed-order/IDENTITY values.

-- Finding 1 — ASM-2026-010 wildcard cert expiring in 25 days
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-010'
                 AND f.finding_type = N'EXPIRED_CERT' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, status)
SELECT 1, a.id, N'EXPIRED_CERT', N'HIGH', N'CCM_RUNNER', N'CCM-SSL-CERT-001',
     N'Wildcard certificate *.tss-bank.sa expires in 25 days — under the 30-day NCA ECC-2 hygiene floor.',
     N'الشهادة العامّة ‎*.tss-bank.sa تنتهي صلاحيتها خلال 25 يوماً، أي تحت الحدّ الأدنى البالغ 30 يوماً وفق ECC-2 من الهيئة الوطنية للأمن السيبراني.',
     N'Schedule rotation with DigiCert this week; coordinate downtime window with portal + APIs + marketing teams.',
     N'تنسيق تدوير الشهادة مع موفّر DigiCert خلال الأسبوع الحالي؛ تنسيق نافذة الانقطاع مع فرق البوّابة والـ APIs والمواقع التسويقية.',
     DATEADD(DAY, -3, SYSUTCDATETIME()), N'OPEN'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-010' AND a.is_deleted = 0;
GO

-- Finding 2 — ASM-2026-002 weak cipher (Mada Pay API)
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-002'
                 AND f.finding_type = N'WEAK_CIPHER' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, status)
SELECT 1, a.id, N'WEAK_CIPHER', N'HIGH', N'TENABLE', N'plugin-26928',
     N'TLS 1.2 with TLS_RSA_WITH_AES_128_CBC_SHA available — predates current SAMA CSF cryptography guidance.',
     N'تفعيل TLS 1.2 مع الخوارزمية TLS_RSA_WITH_AES_128_CBC_SHA — لا يتماشى مع التوجيهات الحالية لإطار SAMA السيبراني بشأن خوارزميات التشفير.',
     N'Disable CBC + RSA key-exchange cipher suites; require TLS 1.3 by default, retain TLS 1.2 only with ECDHE + AEAD ciphers.',
     N'تعطيل مجموعات خوارزميات CBC وتبادل مفاتيح RSA؛ اشتراط TLS 1.3 افتراضياً والإبقاء على TLS 1.2 فقط مع خوارزميات ECDHE وAEAD.',
     DATEADD(DAY, -14, SYSUTCDATETIME()), N'INVESTIGATING'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-002' AND a.is_deleted = 0;
GO

-- Finding 3 — ASM-2026-008 missing DMARC reject policy
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-008'
                 AND f.finding_type = N'MISSING_HEADER' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, status)
SELECT 1, a.id, N'MISSING_HEADER', N'MEDIUM', N'AUTOMATED_PROBE', NULL,
     N'DMARC record at p=quarantine — SAMA CSF guidance recommends p=reject for banking domains.',
     N'سجل DMARC مضبوط على p=quarantine — توجيهات إطار SAMA السيبراني للنطاقات المصرفية تنصح بضبطه على p=reject.',
     N'After at least 30 days of DMARC reports without false positives, escalate the policy to p=reject.',
     N'بعد 30 يوماً على الأقل من تقارير DMARC دون نتائج إيجابية كاذبة، رفع السياسة إلى p=reject.',
     DATEADD(DAY, -45, SYSUTCDATETIME()), N'OPEN'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-008' AND a.is_deleted = 0;
GO

-- Finding 4 — ASM-2026-004 outdated framework on marketing site
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-004'
                 AND f.finding_type = N'OUTDATED_FRAMEWORK' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, closed_at, status)
SELECT 1, a.id, N'OUTDATED_FRAMEWORK', N'LOW', N'TENABLE', N'plugin-19506',
     N'WordPress 6.3.1 detected — current upstream is 6.5.x. No known CVEs at this minor; informational follow-up.',
     N'الإصدار 6.3.1 من WordPress قائم، بينما النسخة الحديثة في المصدر هي 6.5.x. لا توجد ثغرات معروفة لهذا الإصدار الفرعي؛ متابعة معلوماتية فقط.',
     N'Ship the next quarterly marketing-site refresh on WordPress 6.5.x with Saudi-hosted CDN reverification.',
     N'الترحيل في التحديث التسويقي الربعي القادم إلى الإصدار 6.5.x من WordPress مع إعادة التحقق من شبكة CDN المستضافة محلياً.',
     DATEADD(DAY, -60, SYSUTCDATETIME()), DATEADD(DAY, -10, SYSUTCDATETIME()), N'FIXED'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-004' AND a.is_deleted = 0;
GO

-- Finding 5 — ASM-2026-005 unpatched VPN concentrator
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-005'
                 AND f.finding_type = N'UNPATCHED' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, status)
SELECT 1, a.id, N'UNPATCHED', N'CRITICAL', N'TENABLE', N'plugin-191057',
     N'VPN concentrator missing CVE-2026-XXXX hotfix — pre-auth RCE in IKEv2 path. Active exploitation observed in regional CERT advisories.',
     N'مُجمِّع الشبكة الافتراضية ينقصه التحديث الأمني لثغرة CVE-2026-XXXX — تنفيذ تعليمات عن بُعد قبل التحقق في مسار IKEv2. وردت تقارير استغلال نشط في نشرات الفِرَق الإقليمية للاستجابة لطوارئ الحاسب.',
     N'Apply vendor hotfix within 24h per SAMA CSF emergency-patch SLA; coordinate maintenance with Branch Operations.',
     N'تطبيق التحديث العاجل من المورّد خلال 24 ساعة وفق اتفاقية SAMA لخدمة التصحيحات الطارئة؛ تنسيق نافذة الصيانة مع عمليات الفروع.',
     DATEADD(DAY, -1, SYSUTCDATETIME()), N'OPEN'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-005' AND a.is_deleted = 0;
GO

-- Finding 6 — ASM-2026-001 missing rate-limit on portal login
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_finding f
               JOIN cyber.attack_surface_asset a ON a.id = f.asset_id
               WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-001'
                 AND f.finding_type = N'NO_RATE_LIMIT' AND f.is_deleted = 0)
INSERT INTO cyber.attack_surface_finding
    (organisation_id, asset_id, finding_type, severity, source, source_ref,
     description_en, description_ar, recommendation_en, recommendation_ar,
     opened_at, status)
SELECT 1, a.id, N'NO_RATE_LIMIT', N'HIGH', N'MANUAL', NULL,
     N'Login endpoint accepts > 1,000 attempts/min from single IP — credential-stuffing exposure on the consumer portal.',
     N'نقطة تسجيل الدخول تقبل أكثر من 1000 محاولة في الدقيقة من عنوان IP واحد — تعرّض لهجمات حشو بيانات الاعتماد على بوّابة العملاء.',
     N'Enable WAF-side rate-limiting (5 attempts / 5 min / IP), wire to the customer-fraud-risk lookup at the IAM layer.',
     N'تفعيل تحديد المعدّل في جدار التطبيقات (5 محاولات لكل 5 دقائق لكل عنوان IP) ووصله بفحص مخاطر احتيال العملاء في طبقة إدارة الهوية.',
     DATEADD(DAY, -7, SYSUTCDATETIME()), N'OPEN'
FROM cyber.attack_surface_asset a WHERE a.organisation_id = 1 AND a.asset_code = N'ASM-2026-001' AND a.is_deleted = 0;
GO

-- ─── 6. Seed 4 shadow-IT entries ───────────────────────────────────

-- Shadow-IT 1 — Notion (data classification UNKNOWN)
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_shadow_it WHERE organisation_id = 1 AND service_name = N'Notion' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_shadow_it
    (organisation_id, service_name, service_name_ar, vendor_domain,
     discovered_via, first_observed_at, observed_user_count,
     data_classification, pdpl_data_processed, status,
     notes_en, notes_ar)
VALUES
    (1, N'Notion', N'نوشن', N'notion.so',
     N'EMAIL_SIGNUPS', DATEADD(DAY, -45, SYSUTCDATETIME()), 18,
     N'UNKNOWN', 0, N'PENDING_REVIEW',
     N'Eighteen employee signups detected across Strategy + Marketing teams. Data classification unclear — needs interview with the team leads to confirm whether customer data has been entered.',
     N'تم رصد 18 تسجيلاً لموظفين من فرق الاستراتيجية والتسويق. تصنيف البيانات غير واضح — يستلزم اجتماعاً مع قادة الفرق للتأكّد ما إذا كانت بيانات عملاء قد أُدخلت.');
GO

-- Shadow-IT 2 — Trello (CONFIDENTIAL — payroll discovered)
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_shadow_it WHERE organisation_id = 1 AND service_name = N'Trello' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_shadow_it
    (organisation_id, service_name, service_name_ar, vendor_domain,
     discovered_via, first_observed_at, observed_user_count,
     data_classification, pdpl_data_processed, status, deadline,
     notes_en, notes_ar)
VALUES
    (1, N'Trello', N'تريلو', N'trello.com',
     N'NETWORK_TRAFFIC', DATEADD(DAY, -12, SYSUTCDATETIME()), 4,
     N'CONFIDENTIAL', 1, N'IN_PROGRESS', DATEADD(DAY, 14, SYSUTCDATETIME()),
     N'HR team using a public Trello board for payroll-schedule tracking. Confirmed via screenshot — board contains employee names + salary band tags. PDPL Article 5 personal data; immediate migration required.',
     N'فريق الموارد البشرية يستخدم لوحة Trello عامّة لمتابعة جدول الرواتب. تأكّد ذلك بلقطة شاشة — اللوحة تحوي أسماء موظفين وفئات راتب. بيانات شخصية وفق المادة 5 من نظام حماية البيانات الشخصية؛ ترحيل فوري مطلوب.');
GO

-- Shadow-IT 3 — Random AI tool (PDPL data flagged)
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_shadow_it WHERE organisation_id = 1 AND service_name = N'ChatGPT (personal accounts)' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_shadow_it
    (organisation_id, service_name, service_name_ar, vendor_domain,
     discovered_via, first_observed_at, observed_user_count,
     data_classification, pdpl_data_processed, status, deadline,
     notes_en, notes_ar)
VALUES
    (1, N'ChatGPT (personal accounts)', N'تشات جي بي تي (حسابات شخصية)', N'chatgpt.com',
     N'NETWORK_TRAFFIC', DATEADD(DAY, -20, SYSUTCDATETIME()), 47,
     N'RESTRICTED', 1, N'PENDING_REVIEW', DATEADD(DAY, 30, SYSUTCDATETIME()),
     N'47 staff observed pasting content into chatgpt.com via personal accounts. Sampled requests include AML alerts and customer KYC text — PDPL + SDAIA AI ethics breach risk. DPO review required.',
     N'تم رصد 47 موظفاً يلصقون محتوى في موقع chatgpt.com عبر حسابات شخصية. عينات الطلبات تضمّنت تنبيهات مكافحة غسل الأموال وفحوصات اعرف عميلك للعملاء — خطر مخالفة نظام حماية البيانات الشخصية وأخلاقيات الذكاء الاصطناعي للهيئة (سدايا). مراجعة من مسؤول حماية البيانات مطلوبة.');
GO

-- Shadow-IT 4 — Zapier integration touching customer data
IF NOT EXISTS (SELECT 1 FROM cyber.attack_surface_shadow_it WHERE organisation_id = 1 AND service_name = N'Zapier' AND is_deleted = 0)
INSERT INTO cyber.attack_surface_shadow_it
    (organisation_id, service_name, service_name_ar, vendor_domain,
     discovered_via, first_observed_at, observed_user_count,
     data_classification, pdpl_data_processed, status, deadline,
     notes_en, notes_ar)
VALUES
    (1, N'Zapier', N'زابير', N'zapier.com',
     N'EXPENSE_REPORT', DATEADD(DAY, -60, SYSUTCDATETIME()), 1,
     N'CONFIDENTIAL', 1, N'IN_PROGRESS', DATEADD(DAY, 7, SYSUTCDATETIME()),
     N'Marketing analyst expensed Zapier Pro subscription. Integration discovered moves CRM leads from HubSpot to Google Sheets via personal Google Drive. Cross-border PDPL Article 29 implications.',
     N'محلّل تسويقي صرف اشتراك Zapier Pro. التكامل المكتشف ينقل عملاء محتملين من HubSpot إلى Google Sheets عبر Google Drive شخصي. آثار على نقل البيانات عبر الحدود وفق المادة 29 من نظام حماية البيانات الشخصية.');
GO

-- ─── Done. ─────────────────────────────────────────────────────────
PRINT N'V187 — Attack Surface Management seeded for org=1 (TSS Bank).';
GO

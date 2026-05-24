-- V180 — Slice 10-E: Vendor MTPD inheritance check.
--
-- SAMA Business Continuity Framework (BCF) §3.4 and ISO 22301 §8.2 both
-- require Saudi banks to evidence that "third-party MTPDs are reflected
-- in the organisation's own recovery objectives". NCA ECC-2 control
-- 4-2-3 adds the vendor-dependency mapping requirement.
--
-- A vendor's MTPD (Maximum Tolerable Period of Disruption) directly
-- bounds the bank's own MTPD for any business process that depends on
-- that vendor — if vendor MTPD > process MTPD, the process is
-- mathematically un-recoverable. Banks today track this in a
-- spreadsheet that is refreshed once a year at audit time. This
-- migration ships the structured surface that lets the BCM Manager
-- compute MTPD inheritance live, snapshot it for audit, and trigger
-- CAPA tasks against breach findings.
--
-- Three tables:
--
--   bcm.process_vendor_dependency       — process ↔ vendor edges
--   bcm.mtpd_inheritance_assessment     — one row per computed cycle
--   bcm.mtpd_inheritance_finding        — per-breach / per-warning rows
--
-- Design decisions baked in:
--
--   * Denormalised business_process_name_en/ar and vendor_name on
--     finding rows — historical snapshot at compute time, immune to
--     later renames or vendor terminations. An auditor can open a
--     2-year-old assessment and still see exactly what the bank's
--     posture looked like that quarter.
--
--   * WARNING threshold defaults to 80% of process MTPD (vendor
--     attested between 0.8× and 1.0× of process). Service code carries
--     the 0.8 constant so it can be tuned without a migration.
--
--   * Process ↔ vendor edges live in bcm (not tprm) because the BCM
--     Manager owns process recovery objectives and inherits dependency
--     data. The TPRM Lead provides the vendor register but does not
--     define which processes depend on which vendors.
--
--   * Pre-seed 5 dependency rows for org=1 covering Mada Payment,
--     SARIE, Tawakkalna integration, Cloud KMS, IBM TM1 — chosen to
--     overlap with vendors actually in the Slice 10-D campaign so
--     the inheritance compute has something to chew on out of the
--     box. All seed inserts are IF NOT EXISTS-guarded.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ─── 1. Process ↔ Vendor dependency edges ───────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'process_vendor_dependency' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.process_vendor_dependency (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        business_process_id         BIGINT          NOT NULL,
        vendor_id                   BIGINT          NOT NULL,

        -- REQUIRED  — vendor failure stops the process; counts for breach
        -- RECOMMENDED — degraded service; informational only
        -- FALLBACK  — used only on primary failure; out-of-scope for inherit
        dependency_type             VARCHAR(40)     NOT NULL DEFAULT 'REQUIRED',

        -- TIGHT     — process stops immediately when vendor is down
        -- LOOSE     — process degrades (slower / partial)
        -- TRANSIENT — process delayed only; eventual consistency
        dependency_strength         VARCHAR(20)     NOT NULL DEFAULT 'TIGHT',

        notes_en                    NVARCHAR(1000)  NULL,
        notes_ar                    NVARCHAR(1000)  NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    -- One active edge per (org, process, vendor).
    CREATE UNIQUE INDEX UX_pvdep_org_proc_vendor
        ON bcm.process_vendor_dependency(organisation_id, business_process_id, vendor_id)
        WHERE is_deleted = 0;

    -- "Vendors this process depends on" — service uses this when iterating.
    CREATE INDEX IX_pvdep_org_process
        ON bcm.process_vendor_dependency(organisation_id, business_process_id)
        WHERE is_deleted = 0;

    -- "Processes that depend on this vendor" — used by impact-analysis tooltips.
    CREATE INDEX IX_pvdep_org_vendor
        ON bcm.process_vendor_dependency(organisation_id, vendor_id)
        WHERE is_deleted = 0;
END
GO

-- ─── 2. Inheritance assessment header (one per cycle) ───────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'mtpd_inheritance_assessment' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.mtpd_inheritance_assessment (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        assessment_code             NVARCHAR(64)    NOT NULL,        -- e.g. MTPD-INH-2026-Q2
        cycle_year                  INT             NOT NULL,
        cycle_quarter               VARCHAR(10)     NULL,            -- Q1..Q4 or NULL for annual

        computed_at                 DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        computed_by_user_id         BIGINT          NULL,

        -- Roll-ups recomputed at compute time, immutable thereafter.
        total_processes             INT             NOT NULL DEFAULT 0,
        total_breaches              INT             NOT NULL DEFAULT 0,
        total_warnings              INT             NOT NULL DEFAULT 0,
        total_ok                    INT             NOT NULL DEFAULT 0,
        total_no_attestation        INT             NOT NULL DEFAULT 0,

        -- Publication makes the assessment visible to AUDITOR + Board
        -- read-only consumers. Drafts are operator-only.
        is_published                BIT             NOT NULL DEFAULT 0,
        published_at                DATETIME2       NULL,
        published_by_user_id        BIGINT          NULL,

        notes_en                    NVARCHAR(2000)  NULL,
        notes_ar                    NVARCHAR(2000)  NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    -- Unique assessment_code per tenant (active rows only).
    CREATE UNIQUE INDEX UX_mtpdi_assess_org_code
        ON bcm.mtpd_inheritance_assessment(organisation_id, assessment_code)
        WHERE is_deleted = 0;

    -- "Show me the most recent assessment" landing query.
    CREATE INDEX IX_mtpdi_assess_org_computed
        ON bcm.mtpd_inheritance_assessment(organisation_id, computed_at DESC)
        WHERE is_deleted = 0;

    -- Calendar/cycle view.
    CREATE INDEX IX_mtpdi_assess_org_cycle
        ON bcm.mtpd_inheritance_assessment(organisation_id, cycle_year, cycle_quarter)
        WHERE is_deleted = 0;
END
GO

-- ─── 3. Per-finding rows (one per (process,vendor) pair) ────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'mtpd_inheritance_finding' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.mtpd_inheritance_finding (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        assessment_id               BIGINT          NOT NULL,

        business_process_id         BIGINT          NOT NULL,
        -- Historical snapshot — frozen at compute time so renames in the
        -- process register don't rewrite the audit trail.
        business_process_name_en    NVARCHAR(255)   NOT NULL,
        business_process_name_ar    NVARCHAR(255)   NULL,

        vendor_id                   BIGINT          NOT NULL,
        vendor_name                 NVARCHAR(255)   NOT NULL,

        process_mtpd_hours          INT             NULL,
        vendor_attested_mtpd_hours  INT             NULL,
        variance_hours              INT             NULL,            -- vendor - process; null if either side null

        -- BREACH       — vendor > process (process is un-recoverable)
        -- WARNING      — vendor within 80%-100% of process MTPD
        -- OK           — vendor < 80% of process MTPD; sufficient buffer
        -- NO_ATTESTATION — vendor has no ACCEPTED attestation; gap in data
        finding_type                VARCHAR(20)     NOT NULL,

        -- CRITICAL  — TIGHT dependency + BREACH
        -- HIGH      — TIGHT + WARNING, or LOOSE + BREACH
        -- MEDIUM    — LOOSE + WARNING, or TRANSIENT + BREACH
        -- LOW       — anything else (including OK + NO_ATTESTATION)
        severity                    VARCHAR(20)     NOT NULL,

        dependency_strength         VARCHAR(20)     NOT NULL,        -- copied from edge
        dependency_type             VARCHAR(40)     NOT NULL,        -- copied from edge

        recommendation_en           NVARCHAR(1000)  NULL,
        recommendation_ar           NVARCHAR(1000)  NULL,

        -- Set when service.createCapaForFinding() opens a remediation task.
        capa_task_id                BIGINT          NULL,
        capa_external_id            NVARCHAR(64)    NULL,
        capa_created_at             DATETIME2       NULL,

        -- Snapshot of the vendor's attestation row at compute time (audit
        -- forensics — "which campaign did this MTPD come from?").
        source_attestation_id       BIGINT          NULL,
        source_campaign_code        NVARCHAR(64)    NULL,
        source_submitted_at         DATETIME2       NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_mtpdi_finding_assessment
            FOREIGN KEY (assessment_id) REFERENCES bcm.mtpd_inheritance_assessment(id)
    );

    -- "Show me breaches in this assessment" — landing query for the detail page.
    CREATE INDEX IX_mtpdi_find_org_assess_type
        ON bcm.mtpd_inheritance_finding(organisation_id, assessment_id, finding_type)
        WHERE is_deleted = 0;

    -- "Show me the history of findings on this process" — trend query.
    CREATE INDEX IX_mtpdi_find_org_proc_type
        ON bcm.mtpd_inheritance_finding(organisation_id, business_process_id, finding_type)
        WHERE is_deleted = 0;

    -- "Which findings on this vendor across all cycles?"
    CREATE INDEX IX_mtpdi_find_org_vendor
        ON bcm.mtpd_inheritance_finding(organisation_id, vendor_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seeded dependencies for org=1 ──────────────────────────────
--
-- Five rows covering core Saudi banking processes ↔ critical vendors.
-- Idempotent: each insert is guarded by a NOT EXISTS lookup against
-- (organisation_id, business_process_id, vendor_id). Where the upstream
-- vendor or process register is empty, the insert is silently skipped
-- — preserves the script's run-anywhere posture documented in CLAUDE.md.
--
-- Mapping:
--   1. Branch payment processing       ↔ SARIE (national RTGS)        TIGHT/REQUIRED
--   2. Mobile-banking payments         ↔ Mada Payment                  TIGHT/REQUIRED
--   3. Tawakkalna-gated KYC            ↔ Tawakkalna integration        LOOSE/REQUIRED
--   4. Card-data tokenisation          ↔ HSM cluster / Cloud KMS       TIGHT/REQUIRED
--   5. Treasury reporting              ↔ IBM TM1                        LOOSE/RECOMMENDED
--
-- The actual numeric vendor_id and business_process_id values are
-- looked up by name so the seed works against whatever IDs the
-- per-module bootstrap scripts assigned.

-- Saudi Payments Network (SARIE) ↔ Branch payment processing
IF EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1 AND (vendor_name LIKE '%SARIE%' OR vendor_name LIKE '%Saudi Payments%'))
   AND EXISTS (SELECT 1 FROM bcm.critical_processes WHERE organisation_id = 1 AND process_name_en LIKE '%Branch%payment%')
BEGIN
    INSERT INTO bcm.process_vendor_dependency (
        organisation_id, business_process_id, vendor_id,
        dependency_type, dependency_strength, notes_en, notes_ar
    )
    SELECT TOP 1 1, cp.id, v.id,
        'REQUIRED', 'TIGHT',
        'Branch payment processing posts to SARIE (national RTGS). SARIE outage stops same-day high-value clearing.',
        N'تتم معالجة مدفوعات الفروع عبر منظومة سريع للتسوية الفورية. توقف سريع يوقف المقاصة اليومية للمبالغ الكبيرة.'
    FROM bcm.critical_processes cp
    CROSS JOIN tprm.vendors v
    WHERE cp.organisation_id = 1
      AND v.organisation_id = 1
      AND cp.process_name_en LIKE '%Branch%payment%'
      AND (v.vendor_name LIKE '%SARIE%' OR v.vendor_name LIKE '%Saudi Payments%')
      AND NOT EXISTS (
          SELECT 1 FROM bcm.process_vendor_dependency d
          WHERE d.organisation_id = 1 AND d.business_process_id = cp.id AND d.vendor_id = v.id
      );
END
GO

-- Mada Payment ↔ Mobile-banking payments
IF EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1 AND vendor_name LIKE '%Mada%')
   AND EXISTS (SELECT 1 FROM bcm.critical_processes WHERE organisation_id = 1 AND (process_name_en LIKE '%Mobile%bank%' OR process_name_en LIKE '%Card%payment%'))
BEGIN
    INSERT INTO bcm.process_vendor_dependency (
        organisation_id, business_process_id, vendor_id,
        dependency_type, dependency_strength, notes_en, notes_ar
    )
    SELECT TOP 1 1, cp.id, v.id,
        'REQUIRED', 'TIGHT',
        'Card-present and Mada-network transactions transit Mada Payment switch.',
        N'تعتمد معاملات نقاط البيع وشبكة مدى على محوّل مدى للمدفوعات.'
    FROM bcm.critical_processes cp
    CROSS JOIN tprm.vendors v
    WHERE cp.organisation_id = 1
      AND v.organisation_id = 1
      AND (cp.process_name_en LIKE '%Mobile%bank%' OR cp.process_name_en LIKE '%Card%payment%')
      AND v.vendor_name LIKE '%Mada%'
      AND NOT EXISTS (
          SELECT 1 FROM bcm.process_vendor_dependency d
          WHERE d.organisation_id = 1 AND d.business_process_id = cp.id AND d.vendor_id = v.id
      );
END
GO

-- Tawakkalna ↔ KYC / customer onboarding
IF EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1 AND vendor_name LIKE '%Tawakkalna%')
   AND EXISTS (SELECT 1 FROM bcm.critical_processes WHERE organisation_id = 1 AND (process_name_en LIKE '%KYC%' OR process_name_en LIKE '%onboard%'))
BEGIN
    INSERT INTO bcm.process_vendor_dependency (
        organisation_id, business_process_id, vendor_id,
        dependency_type, dependency_strength, notes_en, notes_ar
    )
    SELECT TOP 1 1, cp.id, v.id,
        'REQUIRED', 'LOOSE',
        'Identity verification via Tawakkalna NIC integration. Outage degrades onboarding to manual channel.',
        N'التحقق من الهوية عبر تكامل توكلنا والمركز الوطني للمعلومات. تعطّل الخدمة يحوّل التحقق إلى القناة اليدوية.'
    FROM bcm.critical_processes cp
    CROSS JOIN tprm.vendors v
    WHERE cp.organisation_id = 1
      AND v.organisation_id = 1
      AND (cp.process_name_en LIKE '%KYC%' OR cp.process_name_en LIKE '%onboard%')
      AND v.vendor_name LIKE '%Tawakkalna%'
      AND NOT EXISTS (
          SELECT 1 FROM bcm.process_vendor_dependency d
          WHERE d.organisation_id = 1 AND d.business_process_id = cp.id AND d.vendor_id = v.id
      );
END
GO

-- Cloud KMS / HSM ↔ Card-data tokenisation
IF EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1 AND (vendor_name LIKE '%KMS%' OR vendor_name LIKE '%HSM%' OR vendor_name LIKE '%Azure%' OR vendor_name LIKE '%AWS%'))
   AND EXISTS (SELECT 1 FROM bcm.critical_processes WHERE organisation_id = 1 AND (process_name_en LIKE '%tokeni%' OR process_name_en LIKE '%encrypt%' OR process_name_en LIKE '%Card%'))
BEGIN
    INSERT INTO bcm.process_vendor_dependency (
        organisation_id, business_process_id, vendor_id,
        dependency_type, dependency_strength, notes_en, notes_ar
    )
    SELECT TOP 1 1, cp.id, v.id,
        'REQUIRED', 'TIGHT',
        'Card data tokenisation depends on cloud KMS / HSM cluster. Loss of KMS halts all token issuance.',
        N'إصدار رموز بيانات البطاقات يعتمد على عنقود إدارة المفاتيح السحابي. فقد الخدمة يوقف إصدار الرموز كلياً.'
    FROM bcm.critical_processes cp
    CROSS JOIN tprm.vendors v
    WHERE cp.organisation_id = 1
      AND v.organisation_id = 1
      AND (cp.process_name_en LIKE '%tokeni%' OR cp.process_name_en LIKE '%encrypt%' OR cp.process_name_en LIKE '%Card%')
      AND (v.vendor_name LIKE '%KMS%' OR v.vendor_name LIKE '%HSM%' OR v.vendor_name LIKE '%Azure%' OR v.vendor_name LIKE '%AWS%')
      AND NOT EXISTS (
          SELECT 1 FROM bcm.process_vendor_dependency d
          WHERE d.organisation_id = 1 AND d.business_process_id = cp.id AND d.vendor_id = v.id
      );
END
GO

-- IBM TM1 ↔ Treasury reporting (RECOMMENDED — degraded operation possible)
IF EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1 AND (vendor_name LIKE '%TM1%' OR vendor_name LIKE '%IBM%'))
   AND EXISTS (SELECT 1 FROM bcm.critical_processes WHERE organisation_id = 1 AND (process_name_en LIKE '%Treasury%' OR process_name_en LIKE '%Liquidity%'))
BEGIN
    INSERT INTO bcm.process_vendor_dependency (
        organisation_id, business_process_id, vendor_id,
        dependency_type, dependency_strength, notes_en, notes_ar
    )
    SELECT TOP 1 1, cp.id, v.id,
        'RECOMMENDED', 'LOOSE',
        'Treasury liquidity reporting uses IBM TM1 cube. Spreadsheet fallback is documented in CFO playbook.',
        N'إعداد تقارير السيولة في الخزينة يستخدم مكعّب IBM TM1. توجد خطة بديلة عبر جداول البيانات في دليل المدير المالي.'
    FROM bcm.critical_processes cp
    CROSS JOIN tprm.vendors v
    WHERE cp.organisation_id = 1
      AND v.organisation_id = 1
      AND (cp.process_name_en LIKE '%Treasury%' OR cp.process_name_en LIKE '%Liquidity%')
      AND (v.vendor_name LIKE '%TM1%' OR v.vendor_name LIKE '%IBM%')
      AND NOT EXISTS (
          SELECT 1 FROM bcm.process_vendor_dependency d
          WHERE d.organisation_id = 1 AND d.business_process_id = cp.id AND d.vendor_id = v.id
      );
END
GO

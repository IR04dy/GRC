-- V177 — Slice 10-D: Vendor BCP attestation campaign.
--
-- SAMA Business Continuity Framework (BCF) §3.4 "Material outsourced
-- services" and NCA ECC-2 Annex H "Third-party security including
-- their BCM posture" both require Saudi banks to obtain documented
-- evidence that each critical third party has a tested BCP/DRP and
-- that their RTO / MTPD are within contractual SLA bounds.
--
-- Banks today track this in a spreadsheet, chase by email, and lose
-- half the responses by quarter-end. This migration ships the
-- structured surface: launch a campaign, materialise invitations,
-- track reminder rounds, escalate overdue rows to the Audit
-- Committee, and produce regulator-ready evidence on close.
--
-- Two tables:
--
--   tprm.vendor_bcp_campaign      — one row per cycle launch
--   tprm.vendor_bcp_attestation   — one row per (campaign × vendor)
--
-- Campaign state machine (enforced in VendorBcpCampaignService):
--   DRAFT       → OPEN
--   OPEN        → REMINDED_R1 | ESCALATED | CLOSED | CANCELLED
--   REMINDED_R1 → REMINDED_R2 | ESCALATED | CLOSED | CANCELLED
--   REMINDED_R2 → ESCALATED | CLOSED | CANCELLED
--   ESCALATED   → CLOSED | CANCELLED
--   CLOSED      (terminal)
--   CANCELLED   (terminal)
--
-- Attestation state machine (per row):
--   PENDING_INVITE  → INVITED (campaign opened)
--   INVITED         → REMINDED | SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE
--   REMINDED        → SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE | ESCALATED
--   OVERDUE         → SUBMITTED | NA_OUT_OF_SCOPE | ESCALATED
--   SUBMITTED       → ACCEPTED | REJECTED | NA_OUT_OF_SCOPE
--   ACCEPTED        (terminal)
--   REJECTED        → (re-invitable: returns to INVITED on re-send)
--   NA_OUT_OF_SCOPE (terminal)
--
-- Pre-seeded sample campaign for org=1 (TSS Bank): VBCP-2026-ANNUAL —
-- "Annual critical-third-party BCP attestation 2026" covering
-- Microsoft Azure KSA, AWS me-south-1, Solutions by stc, Mobily, Lean
-- Technologies, Saudi Information Technology Company (SITE), and
-- Bahri (logistics). Threshold = CRITICAL_ONLY. The campaign starts
-- in DRAFT — VendorBcpCampaignService.openCampaign() materialises the
-- invitation rows when the TPRM Lead clicks "Open".

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('tprm') IS NULL EXEC('CREATE SCHEMA tprm');
GO

-- ─── Campaign table ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'vendor_bcp_campaign' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.vendor_bcp_campaign (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        campaign_code               NVARCHAR(64)    NOT NULL,        -- e.g. VBCP-2026-ANNUAL
        title_en                    NVARCHAR(255)   NOT NULL,
        title_ar                    NVARCHAR(255)   NOT NULL,
        description_en              NVARCHAR(MAX)   NULL,
        description_ar              NVARCHAR(MAX)   NULL,

        cycle_year                  INT             NOT NULL,
        cycle_quarter               VARCHAR(10)     NULL,            -- Q1..Q4 or NULL for annual

        opened_at                   DATETIME2       NULL,
        closes_at                   DATETIME2       NULL,
        closed_at                   DATETIME2       NULL,
        reminder_cadence_days       INT             NOT NULL DEFAULT 14,
        escalation_threshold_days   INT             NOT NULL DEFAULT 30,

        status                      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | OPEN | REMINDED_R1 | REMINDED_R2 | ESCALATED | CLOSED | CANCELLED

        mandatory_threshold         VARCHAR(20)     NOT NULL DEFAULT 'CRITICAL_ONLY',
        -- ALL | CRITICAL_ONLY | HIGH_AND_ABOVE

        regulator_reference         NVARCHAR(500)   NULL,            -- e.g. "SAMA BCF §3.4, NCA ECC-2 4-2-3"

        -- Campaign-level rollups recomputed on each transition.
        total_invited               INT             NOT NULL DEFAULT 0,
        total_submitted             INT             NOT NULL DEFAULT 0,
        total_accepted              INT             NOT NULL DEFAULT 0,
        total_rejected              INT             NOT NULL DEFAULT 0,
        total_overdue               INT             NOT NULL DEFAULT 0,
        total_na                    INT             NOT NULL DEFAULT 0,

        closing_summary_en          NVARCHAR(MAX)   NULL,
        closing_summary_ar          NVARCHAR(MAX)   NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_by_user_id          BIGINT          NULL,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    -- Unique campaign_code per tenant (active rows only)
    CREATE UNIQUE INDEX UX_vbcp_campaign_org_code
        ON tprm.vendor_bcp_campaign(organisation_id, campaign_code)
        WHERE is_deleted = 0;

    -- "Show me the active campaign" landing query
    CREATE INDEX IX_vbcp_campaign_org_status
        ON tprm.vendor_bcp_campaign(organisation_id, status)
        WHERE is_deleted = 0;

    -- Calendar/cycle view
    CREATE INDEX IX_vbcp_campaign_org_cycle
        ON tprm.vendor_bcp_campaign(organisation_id, cycle_year, cycle_quarter)
        WHERE is_deleted = 0;
END
GO

-- ─── Attestation table (one row per vendor per campaign) ────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'vendor_bcp_attestation' AND schema_id = SCHEMA_ID('tprm'))
BEGIN
    CREATE TABLE tprm.vendor_bcp_attestation (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        campaign_id                 BIGINT          NOT NULL,
        vendor_id                   BIGINT          NOT NULL,

        -- Submission token for the vendor-portal URL. Opaque, single-use
        -- per re-issuance; service can rotate on re-send if desired.
        submission_token            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Invitation + reminder lifecycle
        invited_at                  DATETIME2       NULL,
        first_reminder_at           DATETIME2       NULL,
        second_reminder_at          DATETIME2       NULL,
        escalated_at                DATETIME2       NULL,
        escalated_to_user_id        BIGINT          NULL,
        overdue_at                  DATETIME2       NULL,

        -- Submission payload (filled in via vendor portal)
        submitted_at                DATETIME2       NULL,
        submitted_by_contact_email  NVARCHAR(255)   NULL,
        submitted_by_contact_name   NVARCHAR(255)   NULL,
        rto_hours_attested          INT             NULL,
        mtpd_hours_attested         INT             NULL,
        contractual_rto_hours       INT             NULL,           -- snapshot at invite time for breach detection
        contractual_mtpd_hours      INT             NULL,
        rto_breach                  BIT             NOT NULL DEFAULT 0,    -- attested > contractual
        mtpd_breach                 BIT             NOT NULL DEFAULT 0,
        has_tested_bcp              BIT             NULL,
        last_bcp_test_date          DATE            NULL,
        last_dr_test_date           DATE            NULL,
        subprocessor_dependencies   NVARCHAR(MAX)   NULL,           -- JSON list of sub-processors
        evidence_file_path          NVARCHAR(500)   NULL,
        evidence_file_name          NVARCHAR(255)   NULL,

        -- Reviewer outcome
        status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING_INVITE',
        -- PENDING_INVITE | INVITED | REMINDED | OVERDUE | SUBMITTED |
        -- ACCEPTED | REJECTED | NA_OUT_OF_SCOPE

        reviewer_comment_en         NVARCHAR(1000)  NULL,
        reviewer_comment_ar         NVARCHAR(1000)  NULL,
        reviewed_by_user_id         BIGINT          NULL,
        reviewed_by_name            NVARCHAR(255)   NULL,
        reviewed_at                 DATETIME2       NULL,

        -- Standard audit columns + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_vbcp_attest_campaign
            FOREIGN KEY (campaign_id) REFERENCES tprm.vendor_bcp_campaign(id),
        CONSTRAINT FK_vbcp_attest_vendor
            FOREIGN KEY (vendor_id)   REFERENCES tprm.vendors(id)
    );

    -- One attestation per vendor per campaign (active rows only).
    CREATE UNIQUE INDEX UX_vbcp_attest_campaign_vendor
        ON tprm.vendor_bcp_attestation(campaign_id, vendor_id)
        WHERE is_deleted = 0;

    -- "Show me overdue rows in this campaign" (TPRM Lead landing query).
    CREATE INDEX IX_vbcp_attest_org_campaign_status
        ON tprm.vendor_bcp_attestation(organisation_id, campaign_id, status)
        WHERE is_deleted = 0;

    -- "What did vendor X submit last year?"
    CREATE INDEX IX_vbcp_attest_org_vendor_submitted
        ON tprm.vendor_bcp_attestation(organisation_id, vendor_id, submitted_at)
        WHERE is_deleted = 0;

    -- Lookup-by-token for the vendor portal endpoint.
    CREATE UNIQUE INDEX UX_vbcp_attest_token
        ON tprm.vendor_bcp_attestation(submission_token)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seeded sample campaign for org=1 ───────────────────────────
--
-- VBCP-2026-ANNUAL — Annual critical-third-party BCP attestation 2026.
-- Starts in DRAFT so the TPRM Lead can review the threshold filter
-- before opening. Threshold CRITICAL_ONLY narrows the materialised
-- invitation set to the bank's most-material vendors (Azure KSA, AWS
-- me-south-1, Solutions by stc, Mobily, Lean, SITE, Bahri).
IF NOT EXISTS (SELECT 1 FROM tprm.vendor_bcp_campaign
               WHERE organisation_id = 1 AND campaign_code = 'VBCP-2026-ANNUAL')
BEGIN
    INSERT INTO tprm.vendor_bcp_campaign (
        organisation_id, campaign_code,
        title_en, title_ar,
        description_en, description_ar,
        cycle_year, cycle_quarter,
        closes_at, reminder_cadence_days, escalation_threshold_days,
        status, mandatory_threshold, regulator_reference
    )
    VALUES (
        1, 'VBCP-2026-ANNUAL',
        'Annual critical-third-party BCP attestation — 2026 cycle',
        N'الشهادة السنوية لاستمرارية الأعمال للموردين الجوهريين — دورة 2026',
        'Covers all CRITICAL-rated third parties holding material outsourced services per SAMA BCF §3.4. ' +
        'Includes Microsoft Azure KSA region, AWS me-south-1, Solutions by stc (cloud + telco), Mobily ' +
        '(connectivity), Lean Technologies (open banking), Saudi Information Technology Company (SITE), ' +
        'and Bahri (logistics). Two reminder rounds at 14-day cadence; escalation to Audit Committee at 30 days.',
        N'تشمل جميع الأطراف الخارجية المُصنَّفة "جوهرية" والتي تقدم خدمات إسناد جوهرية وفق المادة §3.4 من ' +
        N'إطار استمرارية الأعمال لمؤسسة النقد. تتضمن منطقة مايكروسوفت أزور بالمملكة العربية السعودية، ' +
        N'و AWS منطقة الشرق الأوسط me-south-1، وحلول من stc (السحابة والاتصالات)، وموبايلي (الاتصال)، ' +
        N'وLean Technologies (الخدمات المصرفية المفتوحة)، والشركة السعودية لتقنية المعلومات (SITE)، ' +
        N'والبحري (الخدمات اللوجستية). جولتان للتذكير بفاصل 14 يوماً؛ تصعيد إلى لجنة المراجعة عند 30 يوماً.',
        2026, NULL,
        '2026-06-30T23:59:00', 14, 30,
        'DRAFT', 'CRITICAL_ONLY',
        'SAMA BCF §3.4 (material outsourced services); NCA ECC-2 control 4-2-3 (third-party security including BCM posture)'
    );
END
GO

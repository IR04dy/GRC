-- V184 — Sprint 11: Regulatory Obligation Register + Periodic Attestation.
--
-- Every Saudi-regulated bank has hundreds of recurring regulatory
-- obligations: file STR within 24h, submit quarterly CAR returns to
-- SAMA by day 15 of M+1, publish PDPL RoPA annually, attest annual
-- code-of-conduct, quarterly SOX-style access certification, etc.
-- Today these live in spreadsheets per business unit; this slice
-- ships a single register with owner assignment, due-date tracking,
-- attestation campaigns, and a regulator-grade audit trail.
--
-- Three tables:
--
--   compliance.obligation                       — the obligation register
--   compliance.obligation_attestation           — per-cycle attestation entries
--   compliance.obligation_attestation_campaign  — cycle launcher (quarterly/annual roll)
--
-- Why three tables (and not one wider one)? Because the obligation
-- itself is long-lived and slowly-changing (regulator + frequency +
-- owner), but the attestation evidence is per-cycle and must be
-- queryable as a row-set ("show me every overdue Q2 2026 attestation"),
-- and the campaign is the launcher object that materialises those rows
-- for a given quarter / regulator filter. Same shape that the
-- vendor-BCP attestation slice (V177) uses; mirroring it intentionally.
--
-- Regulatory anchors:
--   - SAMA Compliance Framework (Aug 2019) §3.2 — explicit register
--     requirement, including owner, frequency, severity-if-missed.
--   - NCA ECC-2 control 1-1 Governance — "documented and assigned
--     cybersecurity obligations".
--   - SAMA GRO inspections routinely demand signed periodic
--     attestations from named officers.
--
-- Obligation state machine (enforced in ObligationService):
--   DRAFT     → ACTIVE | RETIRED
--   ACTIVE    → SUSPENDED | RETIRED
--   SUSPENDED → ACTIVE | RETIRED
--   RETIRED   (terminal)
--
-- Per-cycle attestation state machine (enforced in
-- ObligationAttestationService):
--   DUE                       → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
--   OVERDUE                   → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE
--   IN_PROGRESS               → ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
--   ATTESTED                  → (reviewer can ACCEPT / REJECT / REQUEST_CHANGES via reviewer fields)
--   ATTESTED_WITH_EXCEPTIONS  → (reviewer can ACCEPT / REJECT / REQUEST_CHANGES via reviewer fields)
--   NOT_APPLICABLE            (terminal)
--
-- Campaign state machine:
--   DRAFT       → OPEN | CANCELLED
--   OPEN        → IN_PROGRESS | ESCALATED | CLOSED | CANCELLED
--   IN_PROGRESS → ESCALATED | CLOSED | CANCELLED
--   ESCALATED   → CLOSED | CANCELLED
--   CLOSED      (terminal)
--   CANCELLED   (terminal)
--
-- Pre-seeded: 20 obligations for org=1 (TSS Bank) covering SAMA + NCA
-- + SDAIA + CMA + ZATCA + Internal, mix of frequencies, all bilingual.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('compliance') IS NULL EXEC('CREATE SCHEMA compliance');
GO

-- ─── Obligation register (the long-lived record) ───────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'obligation' AND schema_id = SCHEMA_ID('compliance'))
BEGIN
    CREATE TABLE compliance.obligation (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        obligation_code             NVARCHAR(64)    NOT NULL,         -- e.g. OBL-SAMA-2026-001
        title_en                    NVARCHAR(500)   NOT NULL,
        title_ar                    NVARCHAR(500)   NOT NULL,
        description_en              NVARCHAR(MAX)   NULL,
        description_ar              NVARCHAR(MAX)   NULL,

        -- SAMA, NCA, SDAIA, CMA, ZATCA, MOH, GAZT, MOJ, NCEMA, CITC, MOC, MOI, INTERNAL
        regulator                   VARCHAR(40)     NOT NULL,
        regulation_reference        NVARCHAR(500)   NULL,             -- e.g. "SAMA CSF §3.2", "NCA ECC-2 1-1"

        -- REPORTING | ATTESTATION | DOCUMENTATION | NOTIFICATION |
        -- AUDIT | TRAINING | REVIEW | FILING | REGISTRATION
        obligation_type             VARCHAR(40)     NOT NULL,

        -- DAILY | WEEKLY | MONTHLY | QUARTERLY | SEMI_ANNUAL |
        -- ANNUAL | BIENNIAL | ON_EVENT | AD_HOC
        frequency                   VARCHAR(40)     NOT NULL,

        severity_if_missed          VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
        -- CRITICAL | HIGH | MEDIUM | LOW

        first_due_date              DATE            NULL,
        next_due_date               DATE            NOT NULL,
        lead_time_days              INT             NOT NULL DEFAULT 14,

        owner_user_id               BIGINT          NOT NULL,
        backup_owner_user_id        BIGINT          NULL,
        business_unit_en            NVARCHAR(255)   NULL,
        business_unit_ar            NVARCHAR(255)   NULL,

        -- Optional cross-references — the rule engine uses these to
        -- attach an obligation breach to the correct control + risk.
        related_control_id          BIGINT          NULL,
        related_risk_id             BIGINT          NULL,

        lifecycle_stage             VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | ACTIVE | SUSPENDED | RETIRED

        last_attested_at            DATETIME2       NULL,
        last_attested_by_user_id    BIGINT          NULL,

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

    -- Unique obligation_code per tenant (active rows only).
    CREATE UNIQUE INDEX UX_obligation_org_code
        ON compliance.obligation(organisation_id, obligation_code)
        WHERE is_deleted = 0;

    -- "Show me everything due in the next 30 days" landing query.
    CREATE INDEX IX_obligation_org_due
        ON compliance.obligation(organisation_id, next_due_date)
        WHERE is_deleted = 0;

    -- "What does owner X have to do?"
    CREATE INDEX IX_obligation_org_owner
        ON compliance.obligation(organisation_id, owner_user_id)
        WHERE is_deleted = 0;

    -- Regulator filter for the dashboard tiles.
    CREATE INDEX IX_obligation_org_regulator
        ON compliance.obligation(organisation_id, regulator, lifecycle_stage)
        WHERE is_deleted = 0;
END
GO

-- ─── Attestation campaign launcher (cycle roll) ────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'obligation_attestation_campaign' AND schema_id = SCHEMA_ID('compliance'))
BEGIN
    CREATE TABLE compliance.obligation_attestation_campaign (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        campaign_code               NVARCHAR(64)    NOT NULL,         -- e.g. OBLCAM-2026-Q2-SAMA
        title_en                    NVARCHAR(500)   NOT NULL,
        title_ar                    NVARCHAR(500)   NOT NULL,
        description_en              NVARCHAR(MAX)   NULL,
        description_ar              NVARCHAR(MAX)   NULL,

        cycle_year                  INT             NOT NULL,
        cycle_quarter               VARCHAR(10)     NULL,             -- Q1..Q4 or NULL for annual

        regulator_filter            VARCHAR(40)     NULL,             -- null = all regulators
        frequency_filter            VARCHAR(40)     NULL,             -- null = all frequencies

        opened_at                   DATETIME2       NULL,
        closes_at                   DATETIME2       NOT NULL,
        closed_at                   DATETIME2       NULL,
        reminder_cadence_days       INT             NOT NULL DEFAULT 7,

        status                      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | OPEN | IN_PROGRESS | ESCALATED | CLOSED | CANCELLED

        -- Rollups recomputed by ObligationCampaignService on each transition.
        total_invited               INT             NOT NULL DEFAULT 0,
        total_in_progress           INT             NOT NULL DEFAULT 0,
        total_attested              INT             NOT NULL DEFAULT 0,
        total_attested_with_excs    INT             NOT NULL DEFAULT 0,
        total_overdue               INT             NOT NULL DEFAULT 0,
        total_na                    INT             NOT NULL DEFAULT 0,

        closing_summary_en          NVARCHAR(MAX)   NULL,
        closing_summary_ar          NVARCHAR(MAX)   NULL,

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

    CREATE UNIQUE INDEX UX_oblcam_org_code
        ON compliance.obligation_attestation_campaign(organisation_id, campaign_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblcam_org_status
        ON compliance.obligation_attestation_campaign(organisation_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblcam_org_cycle
        ON compliance.obligation_attestation_campaign(organisation_id, cycle_year, cycle_quarter)
        WHERE is_deleted = 0;
END
GO

-- ─── Per-cycle attestation (one row per obligation per cycle) ──────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'obligation_attestation' AND schema_id = SCHEMA_ID('compliance'))
BEGIN
    CREATE TABLE compliance.obligation_attestation (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        obligation_id               BIGINT          NOT NULL,
        campaign_id                 BIGINT          NULL,             -- null = ad-hoc attestation, not part of a campaign

        period_label                NVARCHAR(64)    NULL,             -- e.g. "Q2-2026", "2026-04", "2026"
        period_start                DATE            NULL,
        period_end                  DATE            NULL,
        due_date                    DATE            NOT NULL,

        attestation_status          VARCHAR(40)     NOT NULL DEFAULT 'DUE',
        -- DUE | OVERDUE | IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE

        attested_by_user_id         BIGINT          NULL,
        attested_at                 DATETIME2       NULL,
        attestation_text_en         NVARCHAR(2000)  NULL,
        attestation_text_ar         NVARCHAR(2000)  NULL,
        evidence_refs               NVARCHAR(MAX)   NULL,              -- JSON list of evidence-pack ids or links
        exceptions_noted_en         NVARCHAR(2000)  NULL,
        exceptions_noted_ar         NVARCHAR(2000)  NULL,

        reviewer_user_id            BIGINT          NULL,
        reviewed_at                 DATETIME2       NULL,
        reviewer_decision           VARCHAR(20)     NULL,
        -- ACCEPTED | REJECTED | REQUEST_CHANGES
        reviewer_comment_en         NVARCHAR(1000)  NULL,
        reviewer_comment_ar         NVARCHAR(1000)  NULL,

        created_by_user_id          BIGINT          NULL,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_oblattest_obligation
            FOREIGN KEY (obligation_id) REFERENCES compliance.obligation(id),
        CONSTRAINT FK_oblattest_campaign
            FOREIGN KEY (campaign_id)   REFERENCES compliance.obligation_attestation_campaign(id)
    );

    -- One attestation per obligation per period_label per campaign (or null campaign).
    -- We enforce this combination in the service layer rather than at the
    -- index level because SQL Server doesn't allow ISNULL() inside index
    -- column lists. The supporting plain index below covers the lookup.
    CREATE INDEX IX_oblattest_obligation_campaign_period
        ON compliance.obligation_attestation(obligation_id, campaign_id, period_label)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblattest_org_status
        ON compliance.obligation_attestation(organisation_id, attestation_status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblattest_org_due
        ON compliance.obligation_attestation(organisation_id, due_date)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblattest_org_campaign
        ON compliance.obligation_attestation(organisation_id, campaign_id, attestation_status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_oblattest_org_obligation
        ON compliance.obligation_attestation(organisation_id, obligation_id, period_end)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed 20 obligations for org=1 (TSS Bank) ──────────────────
--
-- Mix of regulators (SAMA / NCA / SDAIA / CMA / ZATCA / Internal),
-- frequencies (daily → biennial → ad-hoc), and severities. All
-- bilingual EN+AR with Saudi-native phrasing (no Google-Translate
-- literalisms). Owner refs point at users we know exist via the
-- standard seed (1 = admin, 2 = riskmanager, 3 = auditor). If those
-- users do not exist in a fresh DB, the FK is soft (owner_user_id
-- has no DB FK constraint — it's enforced at the service layer when
-- IAM is wired).

-- 1) SAMA monthly CAR filing
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-001',
    'SAMA monthly Capital Adequacy Ratio (CAR) return',
    N'تقرير نسبة كفاية رأس المال الشهري لمؤسسة النقد',
    'File the monthly CAR return to SAMA Prudential Returns by day 15 of M+1. Owner is Treasury Head; backup is CFO.',
    N'تقديم تقرير كفاية رأس المال الشهري إلى إدارة المتابعة الاحترازية بمؤسسة النقد قبل اليوم الخامس عشر من الشهر التالي. المسؤول رئيس الخزينة، والاحتياطي هو المدير المالي.',
    'SAMA', 'SAMA Banking Rules — Prudential Returns §4.2', 'REPORTING', 'MONTHLY', 'CRITICAL',
    '2026-01-15', '2026-06-15', 7, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- 2) SAMA quarterly STR aggregate report
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-002',
    'Quarterly Suspicious Transaction Report (STR) aggregate to SAFIU',
    N'التقرير الربع سنوي المجمّع لتقارير العمليات المشبوهة إلى وحدة التحريات المالية',
    'Aggregate STR statistics for the quarter and submit to the Saudi Financial Investigation Unit by day 30 of Q+1.',
    N'تجميع إحصاءات تقارير العمليات المشبوهة للربع وتقديمها إلى وحدة التحريات المالية السعودية قبل اليوم الثلاثين من الربع التالي.',
    'SAMA', 'SAMA AML Rules §6.3; AML Law Art. 19', 'REPORTING', 'QUARTERLY', 'CRITICAL',
    '2026-04-30', '2026-07-30', 14, 1, 'Financial Crimes', N'مكافحة الجرائم المالية', 'ACTIVE'
);

-- 3) SAMA 24-hour cyber incident notification (ON_EVENT)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-003')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-003',
    'SAMA cyber incident notification within 24 hours of detection',
    N'إشعار مؤسسة النقد بالحادثة السيبرانية خلال 24 ساعة من الاكتشاف',
    'Notify SAMA Cybersecurity within 24 hours of detecting a material cyber incident, then submit detailed report within 7 days.',
    N'إشعار إدارة الأمن السيبراني بمؤسسة النقد خلال 24 ساعة من اكتشاف حادثة سيبرانية جوهرية، يليه تقرير مفصّل خلال 7 أيام.',
    'SAMA', 'SAMA Cybersecurity Framework §3.4.1 (incident reporting)', 'NOTIFICATION', 'ON_EVENT', 'CRITICAL',
    NULL, '2026-12-31', 0, 1, 'CISO Office', N'مكتب رئيس أمن المعلومات', 'ACTIVE'
);

-- 4) SAMA annual ICAAP submission
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-004')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-004',
    'Annual ICAAP / ILAAP submission to SAMA',
    N'التقرير السنوي لتقييم كفاية رأس المال الداخلي والسيولة (ICAAP / ILAAP) لمؤسسة النقد',
    'Submit Internal Capital Adequacy Assessment Process and Internal Liquidity Adequacy Assessment Process documents by end-March each year.',
    N'تقديم وثائق تقييم كفاية رأس المال الداخلي وتقييم كفاية السيولة الداخلية لمؤسسة النقد بنهاية مارس من كل عام.',
    'SAMA', 'SAMA Pillar II — ICAAP / ILAAP Guidance', 'REPORTING', 'ANNUAL', 'HIGH',
    '2026-03-31', '2027-03-31', 60, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- 5) SAMA quarterly Liquidity Coverage Ratio (LCR) reporting
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-005')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-005',
    'Quarterly Liquidity Coverage Ratio (LCR) reporting',
    N'تقرير ربع سنوي لنسبة تغطية السيولة',
    'Submit LCR template to SAMA Prudential Returns within 30 days of quarter-end.',
    N'تقديم نموذج نسبة تغطية السيولة إلى إدارة المتابعة الاحترازية بمؤسسة النقد خلال 30 يوماً من نهاية الربع.',
    'SAMA', 'SAMA Liquidity Rules — Basel III LCR', 'REPORTING', 'QUARTERLY', 'HIGH',
    '2026-04-30', '2026-07-30', 14, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- 6) NCA annual cybersecurity posture self-assessment
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-NCA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-NCA-2026-001',
    'NCA ECC-2 annual cybersecurity self-assessment filing',
    N'التقييم الذاتي السنوي للأمن السيبراني وفق ضوابط الهيئة الوطنية للأمن السيبراني ECC-2',
    'Complete the NCA Essential Cybersecurity Controls (ECC-2) self-assessment workbook and file via Haseen portal each year.',
    N'إكمال نموذج التقييم الذاتي للضوابط الأساسية للأمن السيبراني (ECC-2) للهيئة الوطنية للأمن السيبراني وتقديمه عبر منصة "حصين" كل عام.',
    'NCA', 'NCA ECC-2 control 1-1 (governance + obligation register)', 'AUDIT', 'ANNUAL', 'HIGH',
    '2026-06-30', '2027-06-30', 60, 1, 'CISO Office', N'مكتب رئيس أمن المعلومات', 'ACTIVE'
);

-- 7) NCA OT/ICS critical-systems posture (CSCC) annual filing
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-NCA-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-NCA-2026-002',
    'NCA Critical Systems Cybersecurity Controls (CSCC) annual filing',
    N'التقييم السنوي لضوابط الأمن السيبراني للأنظمة الحساسة (CSCC) للهيئة الوطنية للأمن السيبراني',
    'Annual posture filing against the 32 CSCC controls applicable to bank critical systems.',
    N'تقديم تقييم سنوي لـ 32 ضابطاً من ضوابط الأمن السيبراني للأنظمة الحساسة المطبّقة على الأنظمة المصرفية الحساسة.',
    'NCA', 'NCA CSCC-1 (Critical Systems Cybersecurity Controls)', 'AUDIT', 'ANNUAL', 'CRITICAL',
    '2026-09-30', '2027-09-30', 60, 1, 'CISO Office', N'مكتب رئيس أمن المعلومات', 'ACTIVE'
);

-- 8) NCA cyber-incident reporting (72h to Haseen)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-NCA-2026-003')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-NCA-2026-003',
    'NCA Haseen cyber-incident reporting within 72 hours',
    N'إبلاغ الهيئة الوطنية للأمن السيبراني عبر منصة "حصين" خلال 72 ساعة من الحادثة',
    'Report material cyber incidents to NCA via the Haseen portal within 72 hours of detection, with follow-up reports per investigation milestones.',
    N'إبلاغ الحوادث السيبرانية الجوهرية للهيئة الوطنية للأمن السيبراني عبر منصة "حصين" خلال 72 ساعة من الاكتشاف، مع تقارير متابعة وفق محطّات التحقيق.',
    'NCA', 'NCA Cyber Incident Reporting Procedure (Haseen)', 'NOTIFICATION', 'ON_EVENT', 'CRITICAL',
    NULL, '2026-12-31', 0, 1, 'CISO Office', N'مكتب رئيس أمن المعلومات', 'ACTIVE'
);

-- 9) SDAIA annual PDPL compliance attestation (RoPA publication)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SDAIA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SDAIA-2026-001',
    'Annual PDPL Record of Processing Activities (RoPA) publication and DPO attestation',
    N'النشر السنوي لسجل أنشطة معالجة البيانات الشخصية (RoPA) وإقرار مسؤول حماية البيانات وفق نظام حماية البيانات الشخصية',
    'Publish the updated RoPA and obtain DPO sign-off attesting accuracy each calendar year per PDPL Art. 31 + Implementing Regulations Art. 13.',
    N'نشر السجل المُحدَّث لأنشطة معالجة البيانات الشخصية والحصول على إقرار من مسؤول حماية البيانات بصحته سنوياً وفق المادة 31 من نظام حماية البيانات الشخصية والمادة 13 من اللائحة التنفيذية.',
    'SDAIA', 'PDPL Art. 31; Implementing Regulations Art. 13', 'ATTESTATION', 'ANNUAL', 'HIGH',
    '2026-12-31', '2026-12-31', 30, 1, 'Data Protection Office', N'مكتب حماية البيانات', 'ACTIVE'
);

-- 10) SDAIA PDPL data-breach notification (72h)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SDAIA-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SDAIA-2026-002',
    'SDAIA PDPL data-breach notification within 72 hours',
    N'إشعار هيئة البيانات والذكاء الاصطناعي بخرق البيانات الشخصية خلال 72 ساعة',
    'Notify SDAIA and affected data subjects within 72 hours of confirming a personal-data breach per PDPL Art. 20.',
    N'إشعار هيئة البيانات والذكاء الاصطناعي وأصحاب البيانات المتضررين خلال 72 ساعة من تأكيد حدوث خرق للبيانات الشخصية وفق المادة 20 من نظام حماية البيانات الشخصية.',
    'SDAIA', 'PDPL Art. 20 (breach notification)', 'NOTIFICATION', 'ON_EVENT', 'CRITICAL',
    NULL, '2026-12-31', 0, 1, 'Data Protection Office', N'مكتب حماية البيانات', 'ACTIVE'
);

-- 11) CMA quarterly market-abuse internal review
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-CMA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-CMA-2026-001',
    'CMA quarterly market-abuse internal review (Compliance Officer sign-off)',
    N'المراجعة الربع سنوية الداخلية لإساءة استخدام السوق وفق هيئة السوق المالية',
    'Compliance Officer reviews surveillance alerts, sample trades, and PA-dealing breaches each quarter; signs off the quarterly compliance attestation.',
    N'يقوم مسؤول الالتزام بمراجعة تنبيهات الرقابة وعينة من الصفقات ومخالفات التداول الشخصي كل ربع، ويوقّع على إقرار الالتزام الربع سنوي.',
    'CMA', 'CMA Market Conduct Regulations Part 5 (Market Abuse)', 'REVIEW', 'QUARTERLY', 'HIGH',
    '2026-04-30', '2026-07-30', 14, 1, 'Compliance', N'الالتزام', 'ACTIVE'
);

-- 12) CMA monthly investment-account reconciliation
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-CMA-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-CMA-2026-002',
    'CMA monthly investment-account reconciliation and segregation attestation',
    N'المطابقة الشهرية لحسابات الاستثمار وإقرار الفصل وفق هيئة السوق المالية',
    'Reconcile client investment accounts against custodian statements monthly and attest segregation of client money.',
    N'مطابقة حسابات الاستثمار الخاصة بالعملاء مع كشوف أمين الحفظ شهرياً، والإقرار بفصل أموال العملاء.',
    'CMA', 'CMA Authorised Persons Regulations §5 (client money)', 'ATTESTATION', 'MONTHLY', 'HIGH',
    '2026-01-31', '2026-06-30', 7, 1, 'Wealth Management Ops', N'عمليات إدارة الثروات', 'ACTIVE'
);

-- 13) ZATCA monthly e-invoicing reconciliation
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ZATCA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ZATCA-2026-001',
    'ZATCA monthly e-invoicing (Fatoora) reconciliation and gateway-error remediation',
    N'المطابقة الشهرية للفوترة الإلكترونية (فاتورة) ومعالجة أخطاء البوّابة وفق هيئة الزكاة والضريبة والجمارك',
    'Reconcile Fatoora Phase-2 invoice submissions vs Core Banking and clear gateway-rejection backlog each month.',
    N'مطابقة الفواتير المُرسَلة عبر المرحلة الثانية من نظام "فاتورة" مع النظام البنكي الأساسي، وتصفية المرفوضات من بوّابة الهيئة شهرياً.',
    'ZATCA', 'ZATCA E-Invoicing Regulation (Fatoora Phase 2)', 'REVIEW', 'MONTHLY', 'MEDIUM',
    '2026-01-15', '2026-06-15', 10, 1, 'Finance Operations', N'عمليات الشؤون المالية', 'ACTIVE'
);

-- 14) ZATCA monthly VAT return
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ZATCA-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ZATCA-2026-002',
    'ZATCA monthly VAT return filing',
    N'الإقرار الشهري لضريبة القيمة المضافة وفق هيئة الزكاة والضريبة والجمارك',
    'File monthly VAT return via ZATCA portal by the last day of M+1 (turnover above SAR 40m threshold).',
    N'تقديم الإقرار الشهري لضريبة القيمة المضافة عبر منصة الهيئة قبل آخر يوم من الشهر التالي (للمنشآت التي يتجاوز إيرادها 40 مليون ريال).',
    'ZATCA', 'KSA VAT Implementing Regulation Art. 62', 'FILING', 'MONTHLY', 'HIGH',
    '2026-02-28', '2026-06-30', 7, 1, 'Finance Operations', N'عمليات الشؤون المالية', 'ACTIVE'
);

-- 15) Internal annual code-of-conduct attestation
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-INT-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-INT-2026-001',
    'Annual employee code-of-conduct attestation',
    N'الإقرار السنوي بميثاق سلوك الموظفين',
    'Every active employee attests to having read the bank code of conduct, disclosed COI, and acknowledged anti-bribery and PA-dealing policies.',
    N'يُقِر كل موظف نشط بأنه اطّلع على ميثاق السلوك المعتمد للبنك، وأفصح عن تعارض المصالح، وأقَرَّ بسياسات مكافحة الرشوة والتداول الشخصي.',
    'INTERNAL', 'Bank Code of Conduct §10 + ISO 37001 §8.3', 'ATTESTATION', 'ANNUAL', 'MEDIUM',
    '2026-12-31', '2026-12-31', 30, 1, 'Human Capital', N'رأس المال البشري', 'ACTIVE'
);

-- 16) Internal quarterly access certification (SOX-style)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-INT-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-INT-2026-002',
    'Quarterly user-access certification for SOX-scope applications',
    N'المراجعة الربع سنوية لصلاحيات المستخدمين على التطبيقات الخاضعة لـ SOX',
    'Application owners certify each user account on SOX-scope systems (Core Banking, GL, Treasury front-office) quarterly. Stale/role-mismatch accounts are remediated within 5 business days.',
    N'يُصادِق مالكو التطبيقات على كل حساب مستخدم في الأنظمة الخاضعة لـ SOX (النظام البنكي الأساسي، النظام المحاسبي، نظام الخزينة) كل ربع. تُعالَج الحسابات غير المتطابقة خلال 5 أيام عمل.',
    'INTERNAL', 'Internal Control over Financial Reporting (ICFR) policy §4.2', 'REVIEW', 'QUARTERLY', 'HIGH',
    '2026-03-31', '2026-06-30', 14, 1, 'IT Security', N'أمن تقنية المعلومات', 'ACTIVE'
);

-- 17) SAMA Outsourcing — annual material-outsourcing attestation
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-006')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-006',
    'SAMA annual material-outsourcing attestation',
    N'الإقرار السنوي بالإسناد الجوهري لمؤسسة النقد',
    'CRO and Procurement Head jointly attest the bank material-outsourcing inventory is complete and each arrangement has SAMA pre-approval where required.',
    N'يُقِر رئيس إدارة المخاطر ومدير المشتريات معاً بأن سجل الإسناد الجوهري للبنك مكتمل، وأن كل ترتيب يحمل الموافقة المُسبقة من مؤسسة النقد حيث يلزم.',
    'SAMA', 'SAMA Outsourcing Rules for Banks §3.1', 'ATTESTATION', 'ANNUAL', 'HIGH',
    '2026-06-30', '2027-06-30', 60, 1, 'Procurement & TPRM', N'المشتريات وإدارة مخاطر الأطراف الخارجية', 'ACTIVE'
);

-- 18) SAMA daily Liquidity & Reserves monitoring
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-SAMA-2026-007')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-SAMA-2026-007',
    'Daily Liquidity & Statutory Reserves monitoring (SAMA daily template)',
    N'الرصد اليومي للسيولة والاحتياطيات النظامية وفق النموذج اليومي لمؤسسة النقد',
    'Daily computation and submission of statutory reserve position to SAMA before 10:00 KSA each business day.',
    N'الحساب والإرسال اليومي لمركز الاحتياطي النظامي إلى مؤسسة النقد قبل الساعة العاشرة صباحاً بتوقيت المملكة في كل يوم عمل.',
    'SAMA', 'SAMA Banking Rules — Statutory Reserve Requirement', 'REPORTING', 'DAILY', 'CRITICAL',
    '2026-01-02', '2026-06-02', 0, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- 19) NCEMA semi-annual business continuity exercise attestation
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-NCEMA-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-NCEMA-2026-001',
    'Semi-annual business continuity exercise — Audit Committee attestation',
    N'الإقرار نصف السنوي للجنة المراجعة بإجراء تمرين استمرارية الأعمال',
    'Conduct at least one BCM exercise every six months and obtain Audit Committee Chair attestation per NCEMA guidance + SAMA BCF §4.2.',
    N'إجراء تمرين واحد على الأقل لاستمرارية الأعمال كل ستة أشهر، والحصول على إقرار رئيس لجنة المراجعة وفق توجيهات الهيئة الوطنية لإدارة الكوارث والأزمات والمادة §4.2 من إطار استمرارية الأعمال لمؤسسة النقد.',
    'NCEMA', 'NCEMA BCM Standard §6.4; SAMA BCF §4.2', 'ATTESTATION', 'SEMI_ANNUAL', 'HIGH',
    '2026-06-30', '2026-06-30', 21, 1, 'BCM Office', N'مكتب استمرارية الأعمال', 'ACTIVE'
);

-- 20) Internal biennial fraud-risk re-assessment
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-INT-2026-003')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-INT-2026-003',
    'Biennial enterprise fraud-risk re-assessment',
    N'إعادة تقييم مخاطر الاحتيال على مستوى المؤسسة كل سنتين',
    'Refresh enterprise fraud-risk assessment every two years (covers internal, external, card, cyber-enabled, AML-adjacent fraud).',
    N'تحديث تقييم مخاطر الاحتيال على مستوى المؤسسة كل سنتين، ويشمل: الاحتيال الداخلي، والخارجي، واحتيال البطاقات، والاحتيال الإلكتروني، والاحتيال المرتبط بغسل الأموال.',
    'INTERNAL', 'Internal Fraud-Risk Management Policy §3.1; aligned with ACFE Fraud Risk Mgmt Guide', 'REVIEW', 'BIENNIAL', 'MEDIUM',
    '2026-12-31', '2028-12-31', 90, 1, 'Financial Crimes', N'مكافحة الجرائم المالية', 'ACTIVE'
);

GO

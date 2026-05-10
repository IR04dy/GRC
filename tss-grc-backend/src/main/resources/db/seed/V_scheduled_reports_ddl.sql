-- ============================================================================
-- TSS GRC — R4: Scheduled reports (cron-driven PDF email)
-- ============================================================================
-- Two tables:
--   reporting.report_schedules — per-tenant schedule definitions
--                                (report type, cadence, recipients, language)
--   reporting.report_runs       — execution history with success / failure
--
-- The Spring scheduler ticks every minute, picks up rows whose next_run_at
-- has elapsed, regenerates the PDF via the existing ExportService, attempts
-- delivery via JavaMailSender (graceful no-op when SMTP isn't configured),
-- writes a run record, and recomputes next_run_at.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'reporting')
    EXEC('CREATE SCHEMA reporting');
PRINT N'Schema reporting ensured.';

-- ── reporting.report_schedules ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='reporting' AND t.name='report_schedules')
BEGIN
    CREATE TABLE reporting.report_schedules (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id       UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_rsched_extid DEFAULT NEWID(),
        organisation_id   BIGINT NOT NULL,
        name              NVARCHAR(255) NOT NULL,
        description       NVARCHAR(MAX) NULL,
        report_type       NVARCHAR(40) NOT NULL,
        -- RISK_REGISTER | FINDINGS_AGING | KRI_DASHBOARD
        format            NVARCHAR(10) NOT NULL CONSTRAINT DF_rsched_fmt DEFAULT N'PDF',
        -- PDF | XLSX
        language          NVARCHAR(5) NOT NULL CONSTRAINT DF_rsched_lang DEFAULT N'en',
        cadence           NVARCHAR(20) NOT NULL CONSTRAINT DF_rsched_cad DEFAULT N'WEEKLY',
        -- DAILY | WEEKLY | MONTHLY | QUARTERLY
        cadence_anchor    NVARCHAR(20) NULL,
        -- Day-of-week (MONDAY..SUNDAY) for WEEKLY; day-of-month (1..28) for
        --   MONTHLY/QUARTERLY; NULL for DAILY.
        time_of_day       NVARCHAR(5) NOT NULL CONSTRAINT DF_rsched_tod DEFAULT N'06:00',
        recipients        NVARCHAR(MAX) NOT NULL,    -- comma-separated email list
        cc_recipients     NVARCHAR(MAX) NULL,
        is_active         BIT NOT NULL CONSTRAINT DF_rsched_active DEFAULT 1,
        last_run_at       DATETIME2 NULL,
        next_run_at       DATETIME2 NOT NULL,
        last_run_status   NVARCHAR(20) NULL,         -- SUCCESS | FAILED | SKIPPED
        last_run_message  NVARCHAR(MAX) NULL,
        is_deleted        BIT NOT NULL CONSTRAINT DF_rsched_isdel DEFAULT 0,
        created_at        DATETIME2 NOT NULL CONSTRAINT DF_rsched_created DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2 NULL,
        created_by        BIGINT NULL,
        updated_by        BIGINT NULL
    );
    CREATE INDEX IX_rsched_due ON reporting.report_schedules(is_active, next_run_at) WHERE is_deleted = 0;
    CREATE INDEX IX_rsched_org ON reporting.report_schedules(organisation_id) WHERE is_deleted = 0;
    PRINT N'Created reporting.report_schedules';
END
ELSE PRINT N'reporting.report_schedules already exists';

-- ── reporting.report_runs ──────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='reporting' AND t.name='report_runs')
BEGIN
    CREATE TABLE reporting.report_runs (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        schedule_id     BIGINT NOT NULL,
        organisation_id BIGINT NOT NULL,
        triggered_at    DATETIME2 NOT NULL CONSTRAINT DF_rrun_at DEFAULT SYSUTCDATETIME(),
        triggered_by    NVARCHAR(40) NOT NULL CONSTRAINT DF_rrun_by DEFAULT N'CRON',
        -- CRON | MANUAL
        status          NVARCHAR(20) NOT NULL,       -- SUCCESS | FAILED | SKIPPED
        duration_ms     BIGINT NULL,
        bytes           BIGINT NULL,                 -- size of the rendered file
        recipients      NVARCHAR(MAX) NULL,
        message         NVARCHAR(MAX) NULL,
        CONSTRAINT FK_rrun_sched FOREIGN KEY (schedule_id) REFERENCES reporting.report_schedules(id)
    );
    CREATE INDEX IX_rrun_sched ON reporting.report_runs(schedule_id, triggered_at DESC);
    PRINT N'Created reporting.report_runs';
END
ELSE PRINT N'reporting.report_runs already exists';

-- ── Seed: 3 representative schedules ──────────────────────────────────────
DECLARE @org BIGINT = 1;

INSERT INTO reporting.report_schedules
  (organisation_id, name, description, report_type, format, language,
   cadence, cadence_anchor, time_of_day, recipients, cc_recipients,
   is_active, next_run_at)
SELECT @org, v.n, v.d, v.rt, v.f, v.l, v.cad, v.anc, v.tod, v.rec, v.cc, v.act, v.nrun
FROM (VALUES
  (N'Weekly Risk Register — Exec',
   N'Sunday morning risk register PDF for the executive committee.',
   N'RISK_REGISTER', N'PDF', N'en', N'WEEKLY', N'SUNDAY', N'06:00',
   N'erc@tss.example.com,cro@tss.example.com',
   N'group-erm@tss.example.com',
   1, DATEADD(MINUTE, 5, SYSUTCDATETIME())),    -- triggers shortly after deploy
  (N'Monthly Findings Aging Report',
   N'1st of every month — aged findings + CAPA progress for the audit committee.',
   N'FINDINGS_AGING', N'PDF', N'en', N'MONTHLY', N'1', N'07:30',
   N'audit-committee@tss.example.com',
   NULL,
   1, DATEADD(DAY, 1, SYSUTCDATETIME())),
  (N'Quarterly KRI Dashboard — Bilingual',
   N'Quarterly KRI snapshot for SAMA examination prep (English + Arabic).',
   N'KRI_DASHBOARD', N'PDF', N'ar', N'QUARTERLY', N'1', N'08:00',
   N'reg-affairs@tss.example.com,ciso@tss.example.com',
   N'sama-prep@tss.example.com',
   1, DATEADD(DAY, 30, SYSUTCDATETIME()))
) AS v(n, d, rt, f, l, cad, anc, tod, rec, cc, act, nrun)
WHERE NOT EXISTS (SELECT 1 FROM reporting.report_schedules s
                  WHERE s.organisation_id = @org AND s.name = v.n AND s.is_deleted = 0);

PRINT N'Seeded 3 sample report schedules.';

SELECT
    N'R4 scheduled reports DDL complete' AS status,
    (SELECT COUNT(*) FROM reporting.report_schedules WHERE is_deleted=0) AS schedules,
    (SELECT COUNT(*) FROM reporting.report_runs)                          AS runs;
GO

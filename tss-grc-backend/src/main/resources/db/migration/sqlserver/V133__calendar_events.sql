-- V133: Saudi-workdays-aware compliance calendar
--
-- A unified calendar/reminder store for the whole platform. Two flows
-- feed it:
--
--   1. AUTO-AGGREGATION (read-only at query time) — the CalendarService
--      pulls dated rows from existing GRC tables (audit engagements,
--      workflow submissions, CAPA tasks, risk attestations, finding
--      remediations, AAR reviews, DR tests, DSR cases, report
--      schedules). NO rows are persisted here for those — the
--      aggregator stitches them on the fly so the calendar always
--      reflects the latest entity state without a stale snapshot.
--
--   2. USER-CREATED EVENTS — meetings, reminders, training sessions,
--      one-off audit days that don't have a parent record yet. These
--      ARE persisted in this table.
--
-- Saudi public holidays are also persisted here (with is_holiday=1
-- and organisation_id=1 for the demo tenant; per-tenant holidays
-- copy from this baseline). V134 seeds the 2026 set.
--
-- Workdays: Saudi week runs Sun–Thu (working) + Fri–Sat (weekend).
-- The CalendarService.isWorkday() helper combines this with the
-- holiday rows here to compute "next business day" for deadline
-- math — used by the F8 NOTIFY handler to delay reminder fires from
-- weekend days into the following Sunday so a buyer doesn't get a
-- 6 am Friday "deadline today" alert.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

CREATE TABLE platform.calendar_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    external_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

    -- Bilingual title + description (NDA + government buyers need AR).
    title_en NVARCHAR(300) NOT NULL,
    title_ar NVARCHAR(300),
    description_en NVARCHAR(MAX),
    description_ar NVARCHAR(MAX),

    -- Free-form classification — the aggregator and UI both use this
    -- to colour-code and filter:
    --   AUDIT_DAY / REPORT_DEADLINE / WORKFLOW_DEADLINE / CAPA_DUE
    --   RISK_ATTESTATION / FINDING_DEADLINE / AAR_REVIEW / DR_TEST
    --   DSR_DEADLINE / MEETING / REMINDER / TRAINING / PUBLIC_HOLIDAY
    --   CUSTOM
    event_type NVARCHAR(40) NOT NULL,

    -- Timing — all_day is the common case (deadlines, holidays).
    -- Meetings + training carry concrete start/end times.
    starts_at DATETIME2 NOT NULL,
    ends_at   DATETIME2,
    all_day   BIT NOT NULL DEFAULT 1,

    -- Operational metadata.
    location    NVARCHAR(200),
    owner_id    BIGINT,         -- iam.users.id; nullable for org-wide events
    severity    NVARCHAR(20),   -- LOW | MEDIUM | HIGH | CRITICAL (optional)

    -- Linkage to a GRC entity. When set, the UI navigates straight
    -- to that entity's detail page on click. Aggregated rows fill
    -- this; user-created events can fill it too if they want.
    entity_type NVARCHAR(50),   -- e.g. "WorkflowSubmission", "CapaTask"
    entity_id   BIGINT,

    -- Reminder ladder: comma-separated days-before the start (e.g.
    -- "30,7,1"). The schedule executor (V18-era) reads this column
    -- and posts F8 REMINDER_DUE events at each step. Default ladder
    -- for new user events = "7,1". Saudi workday shift: if a
    -- reminder lands on Fri/Sat, the executor pushes it to the next
    -- Sunday so reminders don't fire on the weekend.
    reminder_days NVARCHAR(50),

    -- Recurrence — kept simple. The aggregator expands the rule into
    -- concrete instances inside the requested date window so we
    -- don't bloat this table with one row per quarterly occurrence.
    --   NONE | DAILY | WEEKLY | MONTHLY | QUARTERLY | ANNUAL
    recurrence       NVARCHAR(20) NOT NULL DEFAULT N'NONE',
    recurrence_until DATE,   -- exclusive end; NULL = open-ended

    -- Saudi public holidays are stored with is_holiday=1 and a
    -- canonical event_type='PUBLIC_HOLIDAY'. UI renders these as a
    -- top-of-month pill rather than a normal event chip; the
    -- isWorkday() helper consults this column.
    is_holiday BIT NOT NULL DEFAULT 0,

    status NVARCHAR(20) NOT NULL DEFAULT N'SCHEDULED',
            -- SCHEDULED | COMPLETED | CANCELLED | SNOOZED

    is_deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT chk_calendar_event_type CHECK (
        event_type IN (
            N'AUDIT_DAY', N'REPORT_DEADLINE', N'WORKFLOW_DEADLINE',
            N'CAPA_DUE', N'RISK_ATTESTATION', N'FINDING_DEADLINE',
            N'AAR_REVIEW', N'DR_TEST', N'DSR_DEADLINE',
            N'MEETING', N'REMINDER', N'TRAINING',
            N'PUBLIC_HOLIDAY', N'CUSTOM')),
    CONSTRAINT chk_calendar_recurrence CHECK (
        recurrence IN (N'NONE', N'DAILY', N'WEEKLY', N'MONTHLY',
                       N'QUARTERLY', N'ANNUAL')),
    CONSTRAINT chk_calendar_status CHECK (
        status IN (N'SCHEDULED', N'COMPLETED', N'CANCELLED', N'SNOOZED'))
);
GO

-- Most queries are "events in date range for tenant" — composite
-- partial index on the live rows only.
CREATE INDEX ix_calendar_events_org_starts
    ON platform.calendar_events (organisation_id, starts_at)
    WHERE is_deleted = 0;
GO

-- Reverse lookup from an entity to its calendar rows — used by the
-- detail-page "On the calendar" panel.
CREATE INDEX ix_calendar_events_entity
    ON platform.calendar_events (entity_type, entity_id)
    WHERE is_deleted = 0 AND entity_type IS NOT NULL;
GO

-- Distinct unique constraint on external_id per tenant — same shape
-- as crisis_aar (V124).
CREATE UNIQUE INDEX ux_calendar_events_external
    ON platform.calendar_events (organisation_id, external_id);
GO

PRINT 'V133 platform.calendar_events table + 3 indexes created.';
GO

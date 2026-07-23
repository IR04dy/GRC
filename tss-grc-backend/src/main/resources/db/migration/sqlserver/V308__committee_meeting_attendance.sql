-- V308 (CH-14): structured committee-meeting attendance for deterministic quorum.
--
-- Committee meeting quorum was a manual Short flag the secretary set by hand
-- (committee_meetings.quorum_met), with attendance held only as free JSON. This
-- table records one row per attendee so quorum can be COMPUTED (present voting
-- members vs the committee's quorum_pct) and ENFORCED — minutes cannot be
-- approved for a meeting that did not reach quorum. Also captures a per-attendee
-- conflict-of-interest declaration.
--
-- Org-scoped; created idempotently. No seed.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_meeting_attendee')
BEGIN
    CREATE TABLE admin.committee_meeting_attendee (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id       BIGINT       NOT NULL,
        committee_meeting_id  BIGINT       NOT NULL,
        member_name           NVARCHAR(255) NOT NULL,
        member_role           NVARCHAR(120) NULL,
        is_voting_member      BIT          NOT NULL CONSTRAINT DF_cma_voting  DEFAULT (1),
        present               BIT          NOT NULL CONSTRAINT DF_cma_present DEFAULT (0),
        coi_declared          BIT          NOT NULL CONSTRAINT DF_cma_coi     DEFAULT (0),
        coi_note              NVARCHAR(MAX) NULL,
        is_deleted            BIT          NOT NULL CONSTRAINT DF_cma_del     DEFAULT (0),
        created_at            DATETIME2    NOT NULL CONSTRAINT DF_cma_created DEFAULT (SYSUTCDATETIME())
    );
    CREATE INDEX IX_cma_meeting ON admin.committee_meeting_attendee (committee_meeting_id) WHERE is_deleted = 0;
END
GO

PRINT 'V308: created admin.committee_meeting_attendee.';
GO

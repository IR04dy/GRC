-- V309 (CH-14): per-member votes on committee resolutions.
--
-- Resolutions carried only aggregate tallies (vote_for / vote_against /
-- vote_abstain) with no record of WHO voted or WHO recused. This table records
-- one vote per member per resolution (FOR | AGAINST | ABSTAIN | RECUSED), so the
-- aggregate can be DERIVED, recusals are explicit and excluded from the tally,
-- and a conflicted member (COI declared on the meeting attendance) is forced to
-- RECUSED. One vote per (resolution, member); org-scoped; created idempotently.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'admin' AND t.name = 'committee_resolution_vote')
BEGIN
    CREATE TABLE admin.committee_resolution_vote (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT       NOT NULL,
        resolution_id    BIGINT       NOT NULL,
        meeting_id       BIGINT       NULL,
        member_name      NVARCHAR(255) NOT NULL,
        member_role      NVARCHAR(120) NULL,
        vote             NVARCHAR(20) NOT NULL,   -- FOR | AGAINST | ABSTAIN | RECUSED
        rationale        NVARCHAR(MAX) NULL,
        is_deleted       BIT          NOT NULL CONSTRAINT DF_crv_del     DEFAULT (0),
        created_at       DATETIME2    NOT NULL CONSTRAINT DF_crv_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_crv_vote CHECK (vote IN ('FOR','AGAINST','ABSTAIN','RECUSED'))
    );
    CREATE UNIQUE INDEX UX_crv_res_member
        ON admin.committee_resolution_vote (resolution_id, member_name) WHERE is_deleted = 0;
    CREATE INDEX IX_crv_res ON admin.committee_resolution_vote (resolution_id) WHERE is_deleted = 0;
END
GO

PRINT 'V309: created admin.committee_resolution_vote.';
GO

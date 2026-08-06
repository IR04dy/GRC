-- =====================================================================
-- V315 (DF-3 / finding F2-21) — actor provenance on committee resolution votes.
-- ---------------------------------------------------------------------
-- Defect: CommitteeResolutionController#recordVote passed `memberName` straight
-- from the REQUEST BODY into the service, which never checked that the name was
-- the caller — or even a member of the committee. Consequences, all of them
-- controls this platform advertises:
--   * COI recusal and the Track-D SoD recusal compared the CLAIMED name, so
--     submitting a different name evaded both;
--   * quorum could be manufactured by submitting N distinct names;
--   * findByResolutionIdAndMemberName upserts by name, so one actor could
--     OVERWRITE another member's already-cast vote;
--   * audit.log recorded the claimed name, so the ledger corroborated the
--     forgery instead of exposing it.
-- And committee minutes are SHA-256 e-signed, so the signature attested to
-- content whose votes were forgeable. ADR-006 already required "immutable
-- authenticated user IDs" and "forged-actor tests" — this is a compliance
-- defect against the platform's own decision record.
--
-- This migration adds the provenance columns the fix records:
--   recorded_by_user_id  — the authenticated user who actually recorded the row
--   recorded_by_name     — their username, for minutes/exports that show names
--   on_behalf            — 1 when a secretary/chair recorded it for the member
--
-- DELIBERATE: existing rows are left with recorded_by_user_id NULL, which reads
-- as "provenance unknown (pre-fix)". They are NOT back-filled with a guess.
-- Inventing an actor for a historical governance decision would repeat exactly
-- the mistake this finding is about, and a GRC platform must be able to say
-- "these records predate provenance enforcement". The UI and exports surface
-- NULL as unknown.
--
-- Idempotent (COL_LENGTH guards); safe to re-run. Applied to dev twice via
-- pyodbc (Flyway off in dev); prod picks it up on the next jar.
-- Reverse recipe:
--   ALTER TABLE admin.committee_resolution_vote DROP COLUMN on_behalf;
--   ALTER TABLE admin.committee_resolution_vote DROP COLUMN recorded_by_name;
--   ALTER TABLE admin.committee_resolution_vote DROP COLUMN recorded_by_user_id;
--   -- (drop the default constraint on on_behalf first — see below)
-- =====================================================================

IF COL_LENGTH('admin.committee_resolution_vote', 'recorded_by_user_id') IS NULL
BEGIN
    ALTER TABLE admin.committee_resolution_vote ADD recorded_by_user_id BIGINT NULL;
END
GO

IF COL_LENGTH('admin.committee_resolution_vote', 'recorded_by_name') IS NULL
BEGIN
    ALTER TABLE admin.committee_resolution_vote ADD recorded_by_name NVARCHAR(255) NULL;
END
GO

-- on_behalf carries a named default constraint so the reverse recipe can drop it
-- deterministically (an unnamed default gets a random DF__ name).
IF COL_LENGTH('admin.committee_resolution_vote', 'on_behalf') IS NULL
BEGIN
    ALTER TABLE admin.committee_resolution_vote
        ADD on_behalf BIT NOT NULL
        CONSTRAINT df_committee_resolution_vote_on_behalf DEFAULT 0;
END
GO

-- Who recorded what, for the minutes pack and the audit sample an assessor takes.
IF OBJECT_ID('admin.committee_resolution_vote') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_committee_resolution_vote_recorded_by' AND object_id = OBJECT_ID('admin.committee_resolution_vote'))
BEGIN
    CREATE INDEX ix_committee_resolution_vote_recorded_by
        ON admin.committee_resolution_vote (organisation_id, recorded_by_user_id)
        INCLUDE (resolution_id, member_name, vote);
END
GO

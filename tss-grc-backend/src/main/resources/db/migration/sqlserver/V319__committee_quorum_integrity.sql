-- =====================================================================
-- V319 — F2-28 / DF-5: the committee quorum denominator stops being caller-authored.
-- ---------------------------------------------------------------------
-- THE MEASUREMENT THAT REFRAMES THE FINDING.
--
-- Before this migration, `admin.committee_meeting_attendee` holds **0 rows**. Not few — none.
-- CH-14 built structured attendance and a deterministic quorum computation, and
-- `recomputeQuorum` returns early when a meeting has no attendance rows, so the computation
-- has **never once executed** on real data. Every quorum fact in the database therefore came
-- from the `quorumMet` field of `POST /meetings/{id}/complete`:
--
--     17 meetings with quorum_met = 1 and zero attendance rows
--        (15 of them MINUTES_APPROVED and e-signed; 2 COMPLETED)
--     32 meetings carrying the legacy free-text attendees_json blob
--     20 resolutions whose tallies are seeded scalars with zero vote rows
--
-- So this is the same defect shape as F2-23 (a permission nobody held), F2-25 (a NOT NULL
-- column with no default) and F2-26 (a link column nobody filled), for the **fourth** time:
-- the mechanism was correct and its data never existed. The difference is what rests on this
-- one — `approveMinutes` gates on `quorum_met`, the minutes are SHA-256 e-signed, and Track D
-- asserts CAB_APPROVED on quorate MINUTES_APPROVED minutes. The signature is cryptographically
-- sound and attests to a decision whose validity precondition was typed in by the caller.
--
-- ---------------------------------------------------------------------
-- 1. THE DENOMINATOR MOVES FROM THE SHEET TO THE ROSTER.
--
-- `quorum()` derived totalVoting by counting voting rows **in the attendance sheet**. An
-- absent member therefore only counted in the denominator if somebody typed them in as
-- absent — so a committee of 7 where 3 attend and only those 3 are recorded read as 3/3,
-- quorate. That inverts what quorum means: the minimum proportion of **eligible** members who
-- must be present for business to be valid. The denominator is the committee's ACTIVE VOTING
-- ROSTER; the numerator is those present. F2-26 (V318) makes that safe by guaranteeing one
-- person cannot hold two active seats on one committee, so the roster cannot double-count.
--
-- Charters that "count only those present" are not expressing a different denominator — they
-- are expressing a different *rule* (majority-of-present is not quorum). Per-committee quorum
-- policy is S6/CE-3's work; `Committee.quorum_pct` already carries the threshold.
--
-- 2. A PAST QUORUM MUST BE IMMUTABLE.
--
-- If the denominator comes from the live roster, then appointing a member next year silently
-- changes last year's quorum arithmetic. So the computed facts are **persisted** at compute
-- time — denominator, present, required, and when — rather than recomputed on read. That is
-- the roster snapshot the register asked for, at five columns instead of a new table: the
-- attendance sheet is itself the per-seat snapshot once rows are seat-bound, and these columns
-- pin the arithmetic that used it.
--
-- 3. LEGACY IS LABELLED, NOT FLIPPED.
--
-- A retroactive "no attendance ⇒ not quorate" rule would strand or invalidate 15 e-signed,
-- already-approved sets of minutes — rewriting governance history to make a new control look
-- clean. `quorum_source` records the truth instead: ASSERTED_LEGACY for a flag a caller
-- supplied, COMPUTED for one the platform derived. Nothing is invented and nothing is
-- destroyed; the provenance simply becomes visible and queryable. This is the same stance V315
-- takes on pre-fix votes and V316 on pre-fix approvals.
--
-- 4. ATTENDANCE REPLACEMENT BECOMES ATTRIBUTABLE.
--
-- `recordAttendance` deletes the whole sheet and rebuilds it from the body with no record of
-- what it replaced and no status check — so a sheet could be replaced on a MINUTES_APPROVED
-- meeting and the quorum flag rewritten **after** the e-signature, which hashes only the
-- minutes text. The service now refuses that transition; `attendance_recorded_by` makes the
-- last writer attributable, and it is the input to the new four-eyes gate on minutes approval
-- (F2-39): the person who authored the attendance cannot be the one who approves the minutes
-- resting on it.
-- ---------------------------------------------------------------------
-- Idempotent: safe to run twice (Flyway is OFF in dev, so it is applied by hand twice).
-- =====================================================================

-- ── 1. persisted quorum facts on the meeting ─────────────────────────

IF COL_LENGTH('admin.committee_meetings', 'quorum_source') IS NULL
    ALTER TABLE admin.committee_meetings ADD quorum_source NVARCHAR(20) NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'quorum_denominator') IS NULL
    ALTER TABLE admin.committee_meetings ADD quorum_denominator INT NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'quorum_present') IS NULL
    ALTER TABLE admin.committee_meetings ADD quorum_present INT NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'quorum_required') IS NULL
    ALTER TABLE admin.committee_meetings ADD quorum_required INT NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'quorum_computed_at') IS NULL
    ALTER TABLE admin.committee_meetings ADD quorum_computed_at DATETIME2 NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'attendance_recorded_by') IS NULL
    ALTER TABLE admin.committee_meetings ADD attendance_recorded_by BIGINT NULL;
GO

IF COL_LENGTH('admin.committee_meetings', 'attendance_recorded_at') IS NULL
    ALTER TABLE admin.committee_meetings ADD attendance_recorded_at DATETIME2 NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_meeting_quorum_source')
    ALTER TABLE admin.committee_meetings WITH CHECK
        ADD CONSTRAINT ck_meeting_quorum_source CHECK (
            quorum_source IS NULL OR quorum_source IN ('COMPUTED', 'ASSERTED_LEGACY'));
GO

-- ── 2. attendance rows bind to a roster seat ─────────────────────────
--
-- NULL member_term_id + is_guest = 1 is the explicit representation of a non-member who was
-- in the room: an external auditor presenting, a subject-matter expert, a regulator observer.
-- They are recorded because the minutes must be factual, and they can never count toward
-- quorum. Without this, secretaries would either be blocked from recording reality or would
-- record guests as members — which is how a denominator gets inflated.

IF COL_LENGTH('admin.committee_meeting_attendee', 'member_term_id') IS NULL
    ALTER TABLE admin.committee_meeting_attendee ADD member_term_id BIGINT NULL;
GO

IF COL_LENGTH('admin.committee_meeting_attendee', 'is_guest') IS NULL
    ALTER TABLE admin.committee_meeting_attendee
        ADD is_guest BIT NOT NULL CONSTRAINT df_cma_is_guest DEFAULT 0;
GO

-- A row is either a roster seat or a declared guest — never neither, which is the free-text
-- state this migration exists to remove.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_cma_seat_or_guest')
    ALTER TABLE admin.committee_meeting_attendee WITH CHECK
        ADD CONSTRAINT ck_cma_seat_or_guest CHECK (
            member_term_id IS NOT NULL OR is_guest = 1);
GO

-- One row per seat per meeting: the same seat recorded twice would count twice toward
-- presence. Filtered so guest rows (NULL seat) are unconstrained.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_cma_meeting_seat')
    CREATE UNIQUE INDEX ux_cma_meeting_seat
        ON admin.committee_meeting_attendee (committee_meeting_id, member_term_id)
        WHERE member_term_id IS NOT NULL AND is_deleted = 0;
GO

-- ── 3. label the legacy assertions ──────────────────────────────────
--
-- Every meeting whose quorum flag was set with no structured attendance behind it. Measured
-- at authoring time: 17 rows (15 MINUTES_APPROVED + e-signed, 2 COMPLETED). The predicate is
-- written from the data rather than the count, so it stays correct on any tenant.

UPDATE m
SET    quorum_source = 'ASSERTED_LEGACY'
FROM   admin.committee_meetings m
WHERE  m.quorum_source IS NULL
  AND  m.quorum_met IS NOT NULL
  AND  NOT EXISTS (SELECT 1 FROM admin.committee_meeting_attendee a
                   WHERE a.committee_meeting_id = m.id AND a.is_deleted = 0);
GO

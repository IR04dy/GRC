-- V350 — S7b / CE-4: a referred item can appear on the agenda, because the referral now carries
--                    the label the agenda needs.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHAT S7 ASKED FOR: "agenda auto-build FROM THE CAB QUEUE"
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `populateAgenda` already exists, and it builds from the charter template's standing items plus
-- open actions rolled forward. What it has never included is the thing a CAB actually meets to
-- decide: the items referred to it. Measured 2026-08-03, dev committee 74 had a referral awaiting
-- decision and its meeting's agenda had no line for it.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHY A COLUMN AND NOT A JOIN — ADR-012
-- ════════════════════════════════════════════════════════════════════════════════════════
-- The agenda is built in `admin/committee`. The referred item is a `change_mgmt` record. S5a spent
-- a whole slice getting `change_mgmt`'s peer imports to zero behind `CommitteePort`, and
-- `ChangeModuleBoundaryTest` fails if they return. Rendering "CHG-2025-0014 — Upgrade the core
-- banking gateway" on an agenda therefore cannot mean the committee module reading
-- `change_requests`: that is a NEW reverse dependency, in the direction the boundary does not have
-- a port for.
--
-- So the referring module supplies the label at referral time — the same direction the existing
-- port already runs — and the referral row keeps it. A snapshot is also the *right* semantics for
-- an agenda: an agenda records what was put before the committee under the title it was put there
-- under, not whatever the record has been renamed to since.
--
-- ⚠ NO BACKFILL, AND THAT IS THE DESIGN.
-- Existing referrals get NULL. Two consequences, both deliberate:
--
--   1. Inventing a subject for an existing governance record would be fabricating one. There is
--      nothing to copy it from without the cross-module read this column exists to avoid.
--
--   2. The agenda builder includes ONLY referrals that carry a recorded `source_code`, so
--      pre-V350 rows are excluded for a stated reason rather than by accident. That matters here:
--      dev's only two PENDING referrals (#7 and #8, created by `compliance`) point at
--      `source_entity_id` 101 and 102, and **no change request with either id exists in any
--      organisation** — they are orphans left behind when their changes were deleted. An agenda
--      built naively from `committee_referral WHERE status = 'PENDING'` would have listed two
--      items that do not exist while omitting every real one. That is F2-96's shape — a confident
--      report wrong in both directions — and the subject requirement is what stops it.
--
-- REVERSE RECIPE: ALTER TABLE admin.committee_referral
--                     DROP COLUMN subject_en, subject_ar, source_code;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── The agenda line's label, snapshotted at referral time ─────────────────────────────
IF COL_LENGTH('admin.committee_referral', 'source_code') IS NULL
BEGIN
    -- The referring module's own reference for the item (e.g. CHG-2025-0014). An agenda names
    -- what it is deciding by its reference, and this is also the presence flag the builder tests.
    ALTER TABLE admin.committee_referral ADD source_code NVARCHAR(80) NULL;
END
GO

IF COL_LENGTH('admin.committee_referral', 'subject_en') IS NULL
BEGIN
    ALTER TABLE admin.committee_referral ADD subject_en NVARCHAR(500) NULL;
END
GO

-- House rule 7: a user-visible record carries *_en AND *_ar. Nullable rather than NOT NULL
-- because the referring module may genuinely not hold an Arabic title for an item, and refusing
-- the referral over it would block the governance path to protect a label — S6b-ii's lesson.
IF COL_LENGTH('admin.committee_referral', 'subject_ar') IS NULL
BEGIN
    ALTER TABLE admin.committee_referral ADD subject_ar NVARCHAR(500) NULL;
END
GO

-- ── Self-checks — the property, not the statement ──────────────────────────────────────
-- THROW takes a literal or a variable, never a concatenation.

DECLARE @colsMissing NVARCHAR(400) =
    N'V350 failed: admin.committee_referral is missing one of source_code / subject_en / '
  + N'subject_ar, so a referred item still cannot be rendered on an agenda without a '
  + N'cross-module read that ADR-012 forbids.';
IF COL_LENGTH('admin.committee_referral', 'source_code') IS NULL
   OR COL_LENGTH('admin.committee_referral', 'subject_en') IS NULL
   OR COL_LENGTH('admin.committee_referral', 'subject_ar') IS NULL
    THROW 50000, @colsMissing, 1;
GO

-- Nothing may have been backfilled. If a later migration or a tenant sets a subject that is their
-- decision; V350 asserting one here would be the migration inventing the title of an item that
-- was put before a committee.
DECLARE @backfilled INT;
DECLARE @backfilledMsg NVARCHAR(400) =
    N'V350 failed: a referral that pre-dates this migration now carries a source_code. The '
  + N'column is a snapshot taken at referral time — filling it in afterwards invents the title '
  + N'an item was tabled under, and makes an orphaned referral look like a real agenda item.';
SELECT @backfilled = COUNT(*) FROM admin.committee_referral
 WHERE source_code IS NOT NULL AND created_at < '2026-08-03';
IF @backfilled > 0 THROW 50000, @backfilledMsg, 1;
GO

-- And no referral may have been lost. The routing rows are what the change module's gate reads.
DECLARE @refs INT;
DECLARE @refsMsg NVARCHAR(400) =
    N'V350 failed: admin.committee_referral is empty. This migration adds three nullable '
  + N'columns and must not touch a row.';
SELECT @refs = COUNT(*) FROM admin.committee_referral;
-- Measured 4 in dev / 0 in prod on 2026-08-03 — so "empty" is only a failure where rows existed,
-- and prod legitimately has none.
IF @refs = 0 AND EXISTS (SELECT 1 FROM admin.committee_meetings)
   AND EXISTS (SELECT 1 FROM change_mgmt.change_requests WHERE status = 'CAB_APPROVED')
    THROW 50000, @refsMsg, 1;
GO

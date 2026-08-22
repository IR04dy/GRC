-- V389 — F2-275: six registers are bilingual for what the record IS and monolingual for WHY IT WAS
-- STOPPED. House rule 7 says a user-visible record carries *_en AND *_ar, and Arabic is the
-- platform's primary language.
--
-- THE SHAPE. F2-271 built the EN_ONLY census; F2-272 closed four members in `privacy` and found the
-- reusable framing — a table already bilingual for every OTHER text column and monolingual for
-- exactly one is F2-215's two-of-N, because the Arabic columns already present are precisely what
-- stops a reader asking about the missing one. This slice is that shape at its most systematic:
--
--   audit_eng.engagements    title/objective/scope bilingual (3 of 4 pairs) — cancellation_reason NOT
--   capa.tasks               title/description/progress_notes bilingual (3 of 4) — cancellation_reason NOT
--   regchange.changes        title/summary/impact_summary bilingual (3 of 4) — deprioritisation_reason NOT
--   approvals.requests       three pairs bilingual (3 of 4) — delegation_reason NOT
--   nca_ecc.filings          title/gap_summary bilingual (2 of 4) — cancellation_reason NOT
--   board_pack.packs         title bilingual (1 of 3) — cancellation_reason NOT
--
-- One concept, six registers, six modules: the happy-path fields were made bilingual and the
-- exception-path field was not, every time. These are the fields an auditor asks about — why was the
-- IIA engagement cancelled, why was the CAPA closed without completion, why was the SAMA/NCA/SDAIA
-- regulatory change deprioritised, why was the approval delegated, why was the board pack withdrawn,
-- why was the NCA ECC filing cancelled.
--
-- SHAPE OF THE FIX, per V387/V388 (this family now has a settled form):
--   * NULLABLE. A NOT NULL column would refuse every pre-existing row, and nothing can invent
--     Arabic governance text for a decision already recorded in English.
--   * WIDTH-MIRRORED against the English sibling and ASSERTED. A narrower Arabic column would
--     truncate a reason the English one accepts, and Arabic is not shorter.
--   * NO BACKFILL, asserted. An empty Arabic reason is an honest "not recorded"; a machine
--     translation would be a fabricated governance record.
--   * NO @NotBlank on the entity side. Bean validation fires on UPDATE too, so requiring Arabic
--     would make every record that predates this migration uneditable (F2-30's own reason for
--     being a ratchet rather than an invariant).
--
-- Measured in BOTH databases before writing this (F2-183): all six *_en columns are NVARCHAR(MAX)
-- and nullable, all six *_ar columns are absent, and the live row counts are
-- 1 / 16 / 14 / 4 / 14 / 21, identical in dev and prod.
--
-- ⚠ My FIRST reading of that said `capa.tasks` held 22 in dev against 15 in prod, and both figures
-- were probe residue rather than data: dev carried EIGHT leftover `[REVIEW-TEST]` rows from earlier
-- slices, which this slice's own cleanup swept (house rule 5's bare-tag sweep, F2-267's rule),
-- leaving exactly the 14 F2-208 recorded. Prod still carries ONE, soft-deleted and therefore
-- invisible to the API, and removing a row from a customer database is not a side effect of a
-- migration. 🔑 A register's row count is only a measurement if no probe residue is in it, and
-- "the two databases differ" is the shape that residue takes when you believe the first number.
--
-- Idempotent: every ALTER is guarded, so applying twice via pyodbc is a no-op (Flyway is off in dev).

SET NOCOUNT ON;
GO

-- ── 1. audit_eng.engagements.cancellation_reason_ar ─────────────────────────────────────────
IF COL_LENGTH('audit_eng.engagements', 'cancellation_reason_ar') IS NULL
    ALTER TABLE audit_eng.engagements ADD cancellation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── 2. board_pack.packs.cancellation_reason_ar ──────────────────────────────────────────────
IF COL_LENGTH('board_pack.packs', 'cancellation_reason_ar') IS NULL
    ALTER TABLE board_pack.packs ADD cancellation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── 3. capa.tasks.cancellation_reason_ar ────────────────────────────────────────────────────
IF COL_LENGTH('capa.tasks', 'cancellation_reason_ar') IS NULL
    ALTER TABLE capa.tasks ADD cancellation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── 4. nca_ecc.filings.cancellation_reason_ar ───────────────────────────────────────────────
IF COL_LENGTH('nca_ecc.filings', 'cancellation_reason_ar') IS NULL
    ALTER TABLE nca_ecc.filings ADD cancellation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── 5. regchange.changes.deprioritisation_reason_ar ─────────────────────────────────────────
IF COL_LENGTH('regchange.changes', 'deprioritisation_reason_ar') IS NULL
    ALTER TABLE regchange.changes ADD deprioritisation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── 6. approvals.requests.delegation_reason_ar ──────────────────────────────────────────────
IF COL_LENGTH('approvals.requests', 'delegation_reason_ar') IS NULL
    ALTER TABLE approvals.requests ADD delegation_reason_ar NVARCHAR(MAX) NULL;
GO

-- ── Self-checks. These THROW: a migration that reports success over a column it did not create
--    is worse than one that fails, because the entity mapping will then fail at runtime instead.
DECLARE @pairs TABLE (tbl SYSNAME, en SYSNAME, ar SYSNAME);
INSERT INTO @pairs (tbl, en, ar) VALUES
    ('audit_eng.engagements', 'cancellation_reason_en',     'cancellation_reason_ar'),
    ('board_pack.packs',      'cancellation_reason_en',     'cancellation_reason_ar'),
    ('capa.tasks',            'cancellation_reason_en',     'cancellation_reason_ar'),
    ('nca_ecc.filings',       'cancellation_reason_en',     'cancellation_reason_ar'),
    ('regchange.changes',     'deprioritisation_reason_en', 'deprioritisation_reason_ar'),
    ('approvals.requests',    'delegation_reason_en',       'delegation_reason_ar');

-- Check 1: all six Arabic columns exist.
DECLARE @missing INT = (
    SELECT COUNT(*) FROM @pairs p
     WHERE COL_LENGTH(p.tbl, p.ar) IS NULL);
IF @missing > 0
    THROW 53891, 'V389 check 1: one or more Arabic columns were not created.', 1;

-- Check 2: every Arabic column mirrors its English sibling's width. A narrower Arabic column
-- silently truncates a reason the English one accepts, and Arabic is not shorter than English.
DECLARE @mismatched INT = (
    SELECT COUNT(*) FROM @pairs p
     WHERE COL_LENGTH(p.tbl, p.ar) <> COL_LENGTH(p.tbl, p.en));
IF @mismatched > 0
    THROW 53892, 'V389 check 2: an Arabic column does not mirror its English sibling''s width.', 1;

-- Check 3: every Arabic column is NULLABLE. NOT NULL would refuse every pre-existing row.
DECLARE @notnull INT = (
    SELECT COUNT(*)
      FROM @pairs p
      JOIN sys.columns c ON c.object_id = OBJECT_ID(p.tbl) AND c.name = p.ar
     WHERE c.is_nullable = 0);
IF @notnull > 0
    THROW 53893, 'V389 check 3: an Arabic column is NOT NULL; it must be nullable.', 1;

-- Check 4: NOTHING was back-filled. An empty Arabic reason is an honest "not recorded"; a
-- machine-translated one would be a fabricated governance record. Asserted rather than intended.
DECLARE @backfilled INT = 0;
SELECT @backfilled = @backfilled + x.n FROM (
    SELECT COUNT(cancellation_reason_ar)     AS n FROM audit_eng.engagements UNION ALL
    SELECT COUNT(cancellation_reason_ar)          FROM board_pack.packs      UNION ALL
    SELECT COUNT(cancellation_reason_ar)          FROM capa.tasks            UNION ALL
    SELECT COUNT(cancellation_reason_ar)          FROM nca_ecc.filings       UNION ALL
    SELECT COUNT(deprioritisation_reason_ar)      FROM regchange.changes     UNION ALL
    SELECT COUNT(delegation_reason_ar)            FROM approvals.requests
) x;
IF @backfilled > 0
    THROW 53894, 'V389 check 4: an Arabic reason column was back-filled. Nothing may invent Arabic governance text.', 1;

-- Check 5: no CHECK constraint was added to any of the twelve columns. A free-text reason has no
-- vocabulary, and constraining one would refuse the register's own data (F2-183).
DECLARE @checks INT = (
    SELECT COUNT(*)
      FROM sys.check_constraints cc
      JOIN sys.columns c ON c.object_id = cc.parent_object_id
                        AND c.column_id = cc.parent_column_id
      JOIN @pairs p ON p.tbl = OBJECT_SCHEMA_NAME(cc.parent_object_id) + '.'
                              + OBJECT_NAME(cc.parent_object_id)
                   AND c.name IN (p.en, p.ar));
IF @checks > 0
    THROW 53895, 'V389 check 5: a CHECK constraint was placed on a free-text reason column.', 1;

PRINT 'V389 OK: six Arabic reason columns, width-mirrored, nullable, nothing back-filled.';
GO

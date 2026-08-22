/* ============================================================================================
   V386 — F2-267: a UNIQUE index on the supplier-resilience test reference.

   `scm.supplier_resilience_tests.test_code` was caller-named with NO minter anywhere in the
   owning service and NO unique index, so two operators typing the same code both succeeded — and
   the form's own placeholder ('SRT-2026-009') taught them to type the next number in the
   sequence. The same commit makes the server mint it (`ReferenceCodeMint`), which is why this
   index can ship: F2-183's rule is that a constraint and its writer land TOGETHER, because
   constraining a column whose writer is still open converts today's silently-stored duplicate
   into a violation on a caller doing nothing new.

   ⚠ FILTERED, for the same reason as V385: `test_code` is NULLABLE and SQL Server treats NULLs as
   EQUAL for uniqueness, so a plain UNIQUE index would allow at most ONE code-less test. That
   state is reachable today — the create used to store a blank code as null — so refusing it here
   would fail on data a customer database may already hold.

   Measured in BOTH databases before writing this (F2-183): dev and prod each hold 11 rows, 11
   non-null codes, 11 distinct, and ZERO duplicate (organisation_id, test_code) groups. So no
   WITH NOCHECK and nothing to reconcile first.

   ⚙ The register holds TWO code spaces — `SRT-2026-00n` (8 rows) and `SRT-GOV-00n` (3) — which is
   F2-203's disjoint-code-space shape in miniature. This index does not reconcile them and must
   not: both are distinct, both are legal, and collapsing two seeded notations is an owner
   decision about a register a SAMA reviewer reads, not a side effect of adding a constraint.
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: the table must exist, or this is being applied to the wrong schema ── */
IF OBJECT_ID('scm.supplier_resilience_tests') IS NULL
BEGIN
    THROW 51386, 'V386: scm.supplier_resilience_tests does not exist. This migration belongs after the SCM resilience tables; do not skip it.', 1;
END
GO

/* ── check 1: refuse rather than corrupt. A duplicate means the register needs reconciling ──
   ── first, and choosing which test keeps a code a reviewer cites is an owner decision.    ── */
IF EXISTS (
    SELECT 1 FROM scm.supplier_resilience_tests
     WHERE test_code IS NOT NULL
     GROUP BY organisation_id, test_code
    HAVING COUNT(*) > 1)
BEGIN
    THROW 51386, 'V386: scm.supplier_resilience_tests already holds duplicate (organisation_id, test_code) rows. Reconcile them before applying this index. Query: SELECT organisation_id, test_code, COUNT(*) FROM scm.supplier_resilience_tests WHERE test_code IS NOT NULL GROUP BY organisation_id, test_code HAVING COUNT(*) > 1;', 1;
END
GO

/* ── the index, idempotent so a re-run is a no-op rather than an error ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_srt_test_code'
       AND object_id = OBJECT_ID('scm.supplier_resilience_tests'))
BEGIN
    CREATE UNIQUE INDEX uq_srt_test_code
        ON scm.supplier_resilience_tests (organisation_id, test_code)
     WHERE test_code IS NOT NULL;
END
GO

/* ── check 2: assert what this migration claims to have done ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_srt_test_code'
       AND object_id = OBJECT_ID('scm.supplier_resilience_tests')
       AND is_unique = 1
       AND has_filter = 1)
BEGIN
    THROW 51386, 'V386: uq_srt_test_code is missing, not unique, or not filtered. A non-filtered unique index on a nullable column would permit only one code-less test, and a code-less test is a state this register already allowed.', 1;
END
GO

/* ── check 3: both columns, in order — a global unique index would let one tenant's code ──
   ── block another''s.                                                                   ── */
IF (SELECT COUNT(*)
      FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
      JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'uq_srt_test_code'
       AND i.object_id = OBJECT_ID('scm.supplier_resilience_tests')
       AND c.name IN ('organisation_id', 'test_code')) <> 2
BEGIN
    THROW 51386, 'V386: uq_srt_test_code must be keyed on (organisation_id, test_code). A global unique index on test_code alone would let one tenant''s code block another''s.', 1;
END
GO

/* ⚙ Deliberately NOT done, both recorded rather than left as absences:

   (a) test_code is NOT made NOT NULL. Every live row has one, but the column has always been
       nullable and the create used to store a blank as null, so a customer database this
       migration has not measured may hold a code-less row that a NOT NULL would refuse.

   (b) No backfill and no re-minting. All 22 live rows across the two databases already carry a
       distinct code, and re-minting a reference a reviewer may already cite would be the
       opposite of the fix.                                                                     */

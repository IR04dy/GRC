/* ============================================================================================
   V385 — F2-266: a UNIQUE index on the reference an FIU submission cites.

   `ethics.aml_alerts.alert_code` was caller-named with no minter anywhere and no unique index, so
   a duplicate of a real code was storable — probed live by F2-250, which recorded that a second
   `AML-2025-0001` was accepted. The same commit makes the server mint the code
   (`AmlAlertService.create` → `ReferenceCodeMint`), which is why this index can ship at all:
   F2-183's rule is that a constraint and its writer land TOGETHER, because adding a constraint to
   a column whose writer is still open converts today's silently-stored duplicate into a
   constraint violation on a caller who is doing nothing new.

   ⚠ FILTERED, and that is not a style choice. `alert_code` is NULLABLE, and SQL Server treats
   NULLs as EQUAL for uniqueness — so a plain UNIQUE index would allow at most ONE alert with no
   code, refusing a state the column's own nullability permits. `WHERE alert_code IS NOT NULL` is
   what makes the index a statement about codes rather than about absence.

   Measured in BOTH databases before writing this (F2-183 — a constraint verifies the rows already
   present, so one violation fails Flyway and blocks the whole next jar): dev and prod each hold
   10 rows, 10 non-null codes, 10 distinct, and ZERO duplicate (organisation_id, alert_code)
   groups. So no WITH NOCHECK, and nothing to reconcile first.

   Scoped to (organisation_id, alert_code) rather than to alert_code alone: every register in this
   platform is tenant-scoped, and a code is only ever cited within the tenant that issued it. A
   global unique index would make one tenant's reference unusable by another, which is a
   multi-tenancy defect dressed as strictness.
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: the table must exist, or this migration is being applied to the wrong schema ── */
IF OBJECT_ID('ethics.aml_alerts') IS NULL
BEGIN
    THROW 51385, 'V385: ethics.aml_alerts does not exist. This migration belongs after the ethics module''s tables; do not skip it.', 1;
END
GO

/* ── check 1: refuse rather than corrupt. A duplicate here means the register has to be ──
   ── reconciled by an owner first: choosing which of two alerts keeps a cited reference is  ──
   ── a decision about a regulator-facing record, not a side effect of a migration.          ── */
IF EXISTS (
    SELECT 1 FROM ethics.aml_alerts
     WHERE alert_code IS NOT NULL
     GROUP BY organisation_id, alert_code
    HAVING COUNT(*) > 1)
BEGIN
    THROW 51385, 'V385: ethics.aml_alerts already holds duplicate (organisation_id, alert_code) rows. Reconcile them before applying this index — deciding which alert keeps a code an FIU submission cites is an owner decision. Query: SELECT organisation_id, alert_code, COUNT(*) FROM ethics.aml_alerts WHERE alert_code IS NOT NULL GROUP BY organisation_id, alert_code HAVING COUNT(*) > 1;', 1;
END
GO

/* ── the index, idempotent so a re-run is a no-op rather than an error ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_aml_alert_code'
       AND object_id = OBJECT_ID('ethics.aml_alerts'))
BEGIN
    CREATE UNIQUE INDEX uq_aml_alert_code
        ON ethics.aml_alerts (organisation_id, alert_code)
     WHERE alert_code IS NOT NULL;
END
GO

/* ── check 2: assert what this migration claims to have done, rather than assuming it ──
   ── (a migration branch that is a no-op everywhere you can measure is untested code).  ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_aml_alert_code'
       AND object_id = OBJECT_ID('ethics.aml_alerts')
       AND is_unique = 1
       AND has_filter = 1)
BEGIN
    THROW 51385, 'V385: uq_aml_alert_code is missing, not unique, or not filtered. A non-filtered unique index on a nullable column would permit only one code-less alert, which is not what this migration means.', 1;
END
GO

/* ── check 3: the index must cover BOTH columns, in order. A single-column index would make ──
   ── one tenant''s reference unusable by another.                                           ── */
IF (SELECT COUNT(*)
      FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
      JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'uq_aml_alert_code'
       AND i.object_id = OBJECT_ID('ethics.aml_alerts')
       AND c.name IN ('organisation_id', 'alert_code')) <> 2
BEGIN
    THROW 51385, 'V385: uq_aml_alert_code must be keyed on (organisation_id, alert_code). A global unique index on alert_code alone would let one tenant''s code block another''s.', 1;
END
GO

/* ⚙ Deliberately NOT done here, and both are recorded rather than left as absences:

   (a) alert_code is NOT made NOT NULL. Every live row has one, but the column has always been
       nullable and this commit's minter fills it on every create — so a NOT NULL would be a
       schema change with no defect behind it, and it would refuse a historical row on a
       customer database this migration has not measured.

   (b) No backfill. All 20 live rows across the two databases already carry a distinct code, so
       there is nothing to mint retrospectively; and re-minting a code an FIU submission may
       already cite would be the opposite of the fix.                                           */

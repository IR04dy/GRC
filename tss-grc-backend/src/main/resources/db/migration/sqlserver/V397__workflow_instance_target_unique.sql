/* ============================================================================================
   V397 — F2-426: the backstop a check-then-insert needs, on the engine 14 modules adopt.

   `WorkflowEngineService.startInstance` refuses a second instance for one target with

       if (instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
               org, targetModule, targetEntity, targetEntityId).isPresent())
           throw new IllegalArgumentException("Workflow instance already exists for this target entity");

   — a check-then-insert, and `workflow.instances` carried NO unique index on those columns. Two
   concurrent starts for one target therefore both see `isPresent() == false` and both insert.

   ⚠⚠ The consequence is not a duplicate row, it is that THE GUARD ITSELF THEN FAILS. The finder
   returns `Optional`, so from the moment two rows exist Spring Data's SimpleJpaRepository throws
   IncorrectResultSizeDataAccessException — a 500 — for that target, on `startInstance` (so it can
   neither succeed nor correctly refuse) AND on `findInstanceForTarget`, which is what every screen
   asking "what is the workflow state of this record?" calls. `WorkflowInstance` has no
   `is_deleted` column and there is no delete route for an instance anywhere in the platform, so
   the corruption is PERMANENT and unrepairable through the API.

   🔑 The tell was already in the schema: `IX_wf_inst_target` exists on
   (target_module, target_entity, target_entity_id) and is NOT unique — somebody built the index
   for precisely this lookup and left it non-unique. This platform's own recorded idiom says what
   was missing: F2-267 noted that `SubprocessorService`'s check-then-insert is safe *because*
   `UQ_subp_code` backstops it.

   ⚠ NOT filtered, and unlike V385 that is not a choice either: all four columns are
   `nullable = false` (`sys.columns`, both databases), so there is no NULL-equality trap for a
   filter to work around — SQL Server treats NULLs as equal for uniqueness, which is why a
   nullable key needs `WHERE ... IS NOT NULL` and a NOT NULL key must not have one.

   Scoped to (organisation_id, ...) rather than to the three target columns alone: the guard is
   tenant-scoped and every register in this platform is. A global unique index would let one
   tenant's instance stop another tenant starting a workflow on the same target ids — a
   multi-tenancy defect dressed as strictness (F2-309).

   Measured in BOTH databases before writing this (F2-183 — a constraint verifies the rows already
   present, so one violation fails Flyway and blocks the whole next jar): 12 rows each, and ZERO
   duplicate (organisation_id, target_module, target_entity, target_entity_id) groups. So no
   WITH NOCHECK and nothing to reconcile first.

   ⚠ This index can only refuse a request that TODAY produces permanent corruption: the single-
   threaded path is already refused by the guard with a clean message, and the only caller the
   index newly refuses is the loser of a concurrent double-start, which currently corrupts the
   record instead. The loser now gets the 409 GlobalExceptionHandler maps a 2601/2627 to.

   ADDITIVE: `IX_wf_inst_target` is deliberately left in place and check 5 asserts it survived, so
   nobody can read this migration as having replaced the seek index.
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: the table must exist, or this is being applied to the wrong schema ── */
IF OBJECT_ID('workflow.instances') IS NULL
BEGIN
    THROW 51397, 'V397: workflow.instances does not exist. This migration belongs after the workflow engine''s tables; do not skip it.', 1;
END
GO

/* ── check 1: refuse rather than corrupt, and NAME the offending rows. A bare "duplicate key" ──
   ── sends the reader nowhere, and this file blocks every later migration until it is resolved. ── */
IF EXISTS (
    SELECT 1 FROM workflow.instances
     GROUP BY organisation_id, target_module, target_entity, target_entity_id
    HAVING COUNT(*) > 1)
BEGIN
    THROW 51397, 'V397: workflow.instances already holds duplicate (organisation_id, target_module, target_entity, target_entity_id) rows — the exact state that makes startInstance and findInstanceForTarget both 500 for that target. Reconcile them before applying this index; choosing which instance keeps a record''s workflow history is an owner decision. Query: SELECT organisation_id, target_module, target_entity, target_entity_id, COUNT(*) FROM workflow.instances GROUP BY organisation_id, target_module, target_entity, target_entity_id HAVING COUNT(*) > 1;', 1;
END
GO

/* ── the index, idempotent so a re-run is a no-op rather than an error ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_wf_instance_target'
       AND object_id = OBJECT_ID('workflow.instances'))
BEGIN
    CREATE UNIQUE INDEX uq_wf_instance_target
        ON workflow.instances (organisation_id, target_module, target_entity, target_entity_id);
END
GO

/* ── check 2: assert what this migration claims to have done rather than assuming it. ──
   ── `is_disabled` matters: a disabled unique index enforces NOTHING and looks identical to ──
   ── a live one in any listing that only checks the name (F2-299).                          ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'uq_wf_instance_target'
       AND object_id = OBJECT_ID('workflow.instances')
       AND is_unique = 1
       AND is_disabled = 0
       AND has_filter = 0)
BEGIN
    THROW 51397, 'V397: uq_wf_instance_target is missing, not unique, disabled, or filtered. All four key columns are NOT NULL, so a filter here would be a statement about absence that the schema does not permit.', 1;
END
GO

/* ── check 3: exactly the four key columns, in order. A subset would leave the race open; a ──
   ── superset would let a second instance exist for one target under a different definition. ── */
IF (SELECT COUNT(*)
      FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
      JOIN sys.columns c       ON c.object_id  = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'uq_wf_instance_target'
       AND i.object_id = OBJECT_ID('workflow.instances')
       AND ic.is_included_column = 0) <> 4
BEGIN
    THROW 51397, 'V397: uq_wf_instance_target must have exactly four key columns.', 1;
END
GO

IF NOT EXISTS (
    SELECT 1
      FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
      JOIN sys.columns c       ON c.object_id  = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'uq_wf_instance_target'
       AND i.object_id = OBJECT_ID('workflow.instances')
       AND ic.is_included_column = 0
       AND ic.key_ordinal = 1
       AND c.name = 'organisation_id')
BEGIN
    THROW 51397, 'V397: uq_wf_instance_target must lead with organisation_id. A global index would let one tenant''s instance block another tenant from starting a workflow on the same target ids.', 1;
END
GO

/* ── check 4: the guard's own columns. If the entity is ever remapped, this fails here rather ──
   ── than silently indexing something the finder does not ask about.                          ── */
IF (SELECT COUNT(*)
      FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
      JOIN sys.columns c       ON c.object_id  = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'uq_wf_instance_target'
       AND i.object_id = OBJECT_ID('workflow.instances')
       AND ic.is_included_column = 0
       AND c.name IN ('organisation_id', 'target_module', 'target_entity', 'target_entity_id')) <> 4
BEGIN
    THROW 51397, 'V397: uq_wf_instance_target does not cover the four columns startInstance''s guard reads.', 1;
END
GO

/* ── check 5: ADDITIVE. The pre-existing non-unique seek index must survive, so this migration ──
   ── cannot be read as having replaced it.                                                    ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'IX_wf_inst_target'
       AND object_id = OBJECT_ID('workflow.instances'))
BEGIN
    THROW 51397, 'V397: IX_wf_inst_target is gone. This migration is additive — it adds the uniqueness the guard needs and removes nothing.', 1;
END
GO

/* ── check 6: prove the index REFUSES, rather than describing it. ──
   ── ⚠ Conditional on a row existing: a fresh install runs this against an empty table, where ──
   ── the probe would verify nothing, and a migration branch that is a no-op everywhere you can ──
   ── measure is untested code (F2-183). Both real databases hold 12 rows, so it does run there.── */
IF EXISTS (SELECT 1 FROM workflow.instances)
BEGIN
    DECLARE @refused BIT = 0;

    BEGIN TRANSACTION;
    BEGIN TRY
        INSERT INTO workflow.instances
            (organisation_id, definition_id, target_module, target_entity, target_entity_id, current_state_id)
        SELECT TOP 1
             organisation_id, definition_id, target_module, target_entity, target_entity_id, current_state_id
          FROM workflow.instances;
    END TRY
    BEGIN CATCH
        SET @refused = 1;
    END CATCH
    ROLLBACK TRANSACTION;

    IF @refused = 0
    BEGIN
        THROW 51397, 'V397: a duplicate (organisation_id, target_module, target_entity, target_entity_id) row was ACCEPTED. The index exists but is not enforcing, so the concurrent double-start this migration is for is still reachable.', 1;
    END
END
GO

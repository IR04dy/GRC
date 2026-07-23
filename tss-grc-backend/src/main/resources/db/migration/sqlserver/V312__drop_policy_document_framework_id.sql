-- =====================================================================
-- V312 — PM-M5 / finding D18 (CONTRACT phase): drop the dead
--        policy.documents.framework_id column.
-- ---------------------------------------------------------------------
-- framework_id was added by V233 (C-12 EXPAND) as the intended FK
-- replacement for the free-text related_framework, but it was never
-- wired: no service reads/writes PolicyDocument.frameworkId, and no
-- JPQL/native query references it (verified across the whole backend).
-- The canonical policy <-> framework linkage now lives in
-- policy.document_framework_mappings (V302 / CH-6). The human-readable
-- related_framework free-text column is RETAINED, so the display value
-- the V233 backfill was derived from is preserved and this change is
-- fully reversible:
--
--   -- to reconstruct the column (reverse of this migration):
--   ALTER TABLE policy.documents ADD framework_id BIGINT NULL;
--   UPDATE d SET framework_id = f.id
--     FROM policy.documents d
--     JOIN compliance.frameworks f ON f.name_en = d.related_framework
--    WHERE d.related_framework IS NOT NULL;
--
-- Column facts (verified on dev): bigint NULL; no default, no index,
-- no FK constraint; 10 of 51 rows carried a backfilled value.
-- Idempotent: guarded on column existence; defensively drops any
-- default constraint that might have been attached in another env.
-- =====================================================================

IF COL_LENGTH('policy.documents', 'framework_id') IS NOT NULL
BEGIN
    DECLARE @df sysname;
    SELECT @df = dc.name
      FROM sys.default_constraints dc
     WHERE dc.parent_object_id = OBJECT_ID('policy.documents')
       AND dc.parent_column_id = COLUMNPROPERTY(OBJECT_ID('policy.documents'), 'framework_id', 'ColumnId');
    IF @df IS NOT NULL
        EXEC('ALTER TABLE policy.documents DROP CONSTRAINT ' + @df);

    ALTER TABLE policy.documents DROP COLUMN framework_id;
END
GO

-- ============================================================================
-- TSS GRC — Controls framework anchor (IA-3)
-- ============================================================================
-- Promotes compliance.controls.framework (string) to a typed FK
-- compliance.controls.framework_id → compliance.frameworks(id), so the
-- framework-anchor sheet view can list controls alongside policies and
-- findings under each framework.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('compliance.controls', 'framework_id') IS NULL
    ALTER TABLE compliance.controls ADD framework_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_controls_framework')
   AND OBJECT_ID('compliance.frameworks') IS NOT NULL
BEGIN
    ALTER TABLE compliance.controls
        ADD CONSTRAINT FK_controls_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_controls_framework';
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_controls_framework' AND object_id=OBJECT_ID('compliance.controls'))
BEGIN
    CREATE INDEX IX_controls_framework ON compliance.controls(framework_id) WHERE is_deleted = 0;
END
GO

-- Backfill framework_id from the existing `framework` string column.
-- Existing seed values align with the dictionary codes (ISO_27001, NCA_ECC,
-- PDPL, ISO_22301, SAMA_CSF, ISO_27701) so a simple JOIN populates everything.
UPDATE compliance.controls
   SET framework_id = f.id
  FROM compliance.controls c
  JOIN compliance.frameworks f ON f.code = c.framework
 WHERE c.framework_id IS NULL
   AND c.framework IS NOT NULL;

PRINT N'Controls framework_id backfilled from framework string column.';

SELECT
    N'Controls framework anchor DDL complete' AS status,
    (SELECT COUNT(*) FROM compliance.controls WHERE is_deleted = 0)                            AS controls,
    (SELECT COUNT(*) FROM compliance.controls WHERE is_deleted = 0 AND framework_id IS NOT NULL) AS taggedControls;

GO

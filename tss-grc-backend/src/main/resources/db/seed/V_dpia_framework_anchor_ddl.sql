-- ============================================================================
-- TSS GRC — DPIA framework anchor (IA-4)
-- ============================================================================
-- Adds framework_id + clause_ref to privacy.dpia. Default for KSA-resident
-- DPIAs is PDPL. Cross-border or international transfers may also reference
-- GDPR. ISO 27701 (PIMS) maturity assessments use the same DPIA template.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('privacy.dpia', 'framework_id') IS NULL
    ALTER TABLE privacy.dpia ADD framework_id BIGINT NULL;
IF COL_LENGTH('privacy.dpia', 'clause_ref')   IS NULL
    ALTER TABLE privacy.dpia ADD clause_ref NVARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_dpia_framework')
   AND OBJECT_ID('compliance.frameworks') IS NOT NULL
BEGIN
    ALTER TABLE privacy.dpia
        ADD CONSTRAINT FK_dpia_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_dpia_framework';
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_dpia_framework' AND object_id=OBJECT_ID('privacy.dpia'))
BEGIN
    CREATE INDEX IX_dpia_framework ON privacy.dpia(framework_id) WHERE is_deleted = 0;
END
GO

-- Backfill: PDPL is the default Saudi privacy framework
UPDATE privacy.dpia
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'PDPL'),
       clause_ref   = N'Art. 25'
 WHERE framework_id IS NULL
   AND is_deleted = 0;

PRINT N'DPIA framework_id backfilled to PDPL.';

SELECT
    N'DPIA framework anchor DDL complete' AS status,
    (SELECT COUNT(*) FROM privacy.dpia WHERE is_deleted = 0)                          AS dpias,
    (SELECT COUNT(*) FROM privacy.dpia WHERE is_deleted = 0 AND framework_id IS NOT NULL) AS taggedDpias;
GO

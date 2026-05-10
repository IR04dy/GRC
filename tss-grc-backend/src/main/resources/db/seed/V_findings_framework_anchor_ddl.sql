-- ============================================================================
-- TSS GRC — Audit Findings framework anchor (IA-2)
-- ============================================================================
-- Most findings originate from a specific framework audit (NCA ECC assessment,
-- ISO 27001 surveillance, SAMA quarterly review). Direct foreign-key from
-- compliance.findings to compliance.frameworks + optional clause_ref. Lets us
-- pull "all NCA findings open ≥ 30 days" in one query and show findings
-- grouped by framework sheet in the UI.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('compliance.findings', 'framework_id') IS NULL
    ALTER TABLE compliance.findings ADD framework_id BIGINT NULL;
IF COL_LENGTH('compliance.findings', 'clause_ref')   IS NULL
    ALTER TABLE compliance.findings ADD clause_ref NVARCHAR(100) NULL;

-- FK + index. Created only if columns + dictionary table exist.
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_findings_framework')
   AND COL_LENGTH('compliance.findings', 'framework_id') IS NOT NULL
   AND OBJECT_ID('compliance.frameworks') IS NOT NULL
BEGIN
    ALTER TABLE compliance.findings
        ADD CONSTRAINT FK_findings_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_findings_framework';
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_findings_framework' AND object_id=OBJECT_ID('compliance.findings'))
BEGIN
    CREATE INDEX IX_findings_framework ON compliance.findings(framework_id) WHERE is_deleted = 0;
END
GO

-- ── Backfill: tag existing seed findings with the most-likely framework ─────
-- Best-effort heuristic: any finding title mentioning a known acronym → that
-- framework. The UI lets users edit the value, so this is just a starting
-- point for the demo.
DECLARE @org BIGINT = 1;
DECLARE @fNCA   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='NCA_ECC');
DECLARE @fISO   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_27001');
DECLARE @fSAMA  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_CSF');
DECLARE @fPDPL  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='PDPL');
DECLARE @fZATCA BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ZATCA_EI');

UPDATE compliance.findings SET framework_id = @fNCA   WHERE framework_id IS NULL AND title LIKE N'%NCA%';
UPDATE compliance.findings SET framework_id = @fSAMA  WHERE framework_id IS NULL AND title LIKE N'%SAMA%';
UPDATE compliance.findings SET framework_id = @fISO   WHERE framework_id IS NULL AND (title LIKE N'%ISO 27001%' OR title LIKE N'%ISMS%');
UPDATE compliance.findings SET framework_id = @fPDPL  WHERE framework_id IS NULL AND (title LIKE N'%PDPL%' OR title LIKE N'%personal data%');
UPDATE compliance.findings SET framework_id = @fZATCA WHERE framework_id IS NULL AND title LIKE N'%ZATCA%';

PRINT N'Findings framework_id backfilled where heuristics matched.';

SELECT
    N'Findings framework anchor DDL complete' AS status,
    (SELECT COUNT(*) FROM compliance.findings WHERE is_deleted = 0)                           AS findings,
    (SELECT COUNT(*) FROM compliance.findings WHERE is_deleted = 0 AND framework_id IS NOT NULL) AS taggedFindings;

GO

-- V80 — Risk-Advanced backfill
--
-- The RiskTreatment entity declares a completed_at column that V15
-- (the original risk_lifecycle migration) didn't include. Add it now
-- so the new RiskTreatmentService can persist completion timestamps.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'risk.risk_treatments')
      AND name = N'completed_at')
BEGIN
    ALTER TABLE risk.risk_treatments
        ADD completed_at DATETIME2 NULL;
END
GO

-- Backfill: any treatment already in COMPLETED status without a
-- completion timestamp gets stamped now so existing rows render cleanly.
UPDATE risk.risk_treatments
   SET completed_at = SYSUTCDATETIME()
 WHERE status = N'COMPLETED'
   AND completed_at IS NULL;
GO

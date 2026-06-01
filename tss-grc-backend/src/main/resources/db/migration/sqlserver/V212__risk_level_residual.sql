-- Make risk.risks.risk_level reflect RESIDUAL (post-control) exposure.
--
-- The original computed column (V1) banded on inherent_likelihood * inherent_impact
-- only, so the headline rating ignored the effect of controls. Operating ERM
-- registers and board packs report on residual risk. This redefines the column
-- to band on the residual score when a residual assessment exists, falling back
-- to the inherent score otherwise (matches the GRC Expert / risk-register UI).
--
-- Bands are unchanged: CRITICAL >= 20, HIGH >= 12, MEDIUM >= 6, LOW >= 1.
--
-- A computed column cannot be ALTERed in place, so it is dropped and re-added.
-- Metadata-only: no stored data, no schemabound dependents, no index on the
-- column; the JPA entity maps it insertable=false/updatable=false. Idempotent —
-- only redefines when the current definition is still inherent-only.
-- Dev DB is Python-managed via setup_risk_level_residual.py; this covers Flyway.

IF EXISTS (
    SELECT 1 FROM sys.computed_columns
    WHERE object_id = OBJECT_ID('risk.risks')
      AND name = 'risk_level'
      AND definition NOT LIKE '%residual_likelihood%'
)
BEGIN
    ALTER TABLE risk.risks DROP COLUMN risk_level;

    ALTER TABLE risk.risks ADD risk_level AS (CASE
        WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact]) >= 20 THEN N'CRITICAL'
        WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact]) >= 12 THEN N'HIGH'
        WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact]) >= 6  THEN N'MEDIUM'
        WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact]) >= 1  THEN N'LOW'
    END);
END;
GO

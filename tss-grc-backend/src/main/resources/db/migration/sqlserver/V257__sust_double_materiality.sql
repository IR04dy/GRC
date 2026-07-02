-- ============================================================================
-- V257 — S-Q: true CSRD/ESRS double materiality
--
-- The material_topic model scored a SINGLE averaged number
-- (materiality_score = (stakeholder_importance + business_impact) / 2), which
-- collapses the two perspectives into one — not double materiality.
--
-- CSRD/ESRS double materiality assesses a topic on TWO INDEPENDENT axes:
--   * impact materiality    (inside-out) — the org's impact on people/environment
--   * financial materiality (outside-in) — the topic's effect on the org's
--                                          development / financial position
-- A topic is material if it is material from EITHER perspective. The service
-- now derives materiality_score = MAX(impact, financial) and the matrix plots
-- both axes with per-topic quadrant classification.
--
-- Add the two axes (NOT NULL DEFAULT 0 so existing rows stay valid) and backfill
-- them from the legacy axes so the historical matrix is not emptied
-- (impact ← stakeholder_importance, financial ← business_impact). The legacy
-- columns are retained (still populated by the compat path) but no longer drive
-- the score. Idempotent — safe to re-run on the Python-applied dev DB.
-- ============================================================================

IF COL_LENGTH('sust.material_topic', 'impact_materiality') IS NULL
    ALTER TABLE sust.material_topic
        ADD impact_materiality DECIMAL(3,1) NOT NULL CONSTRAINT df_mt_impact DEFAULT 0;
GO
IF COL_LENGTH('sust.material_topic', 'financial_materiality') IS NULL
    ALTER TABLE sust.material_topic
        ADD financial_materiality DECIMAL(3,1) NOT NULL CONSTRAINT df_mt_financial DEFAULT 0;
GO

-- Backfill from the legacy axes ONLY where the new axes are still at their
-- default 0 (freshly added) — so a re-run never clobbers real double-materiality
-- scores entered after this migration.
UPDATE sust.material_topic
   SET impact_materiality    = stakeholder_importance,
       financial_materiality = business_impact
 WHERE impact_materiality = 0
   AND financial_materiality = 0
   AND (stakeholder_importance <> 0 OR business_impact <> 0);
GO

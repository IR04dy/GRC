-- V296 (SGI plan §4 SGI-C — source lineage for sgi_commitment.current_value):
--  a) value_source   — provenance of current_value: MANUAL (default) | ESG_METRIC | GHG_TOTAL.
--  b) source_metric_id — nullable FK-by-convention to sust.esg_metric (ESG_METRIC source).
--  c) source_scope   — nullable GHG scope 1..3 to narrow a GHG_TOTAL refresh (NULL = all scopes).
-- Idempotent (re-runnable): dev applies twice via pyodbc (Flyway off in dev);
-- prod picks it up via baseline-aware Flyway.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.sgi_commitment') AND name = 'value_source')
    ALTER TABLE sust.sgi_commitment ADD value_source VARCHAR(20) NOT NULL CONSTRAINT df_sgi_value_source DEFAULT 'MANUAL';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.sgi_commitment') AND name = 'source_metric_id')
    ALTER TABLE sust.sgi_commitment ADD source_metric_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.sgi_commitment') AND name = 'source_scope')
    ALTER TABLE sust.sgi_commitment ADD source_scope TINYINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_sgi_value_source')
    ALTER TABLE sust.sgi_commitment WITH CHECK ADD CONSTRAINT ck_sgi_value_source
        CHECK (value_source IN ('MANUAL', 'ESG_METRIC', 'GHG_TOTAL'));
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_sgi_source_scope')
    ALTER TABLE sust.sgi_commitment WITH CHECK ADD CONSTRAINT ck_sgi_source_scope
        CHECK (source_scope IS NULL OR source_scope BETWEEN 1 AND 3);

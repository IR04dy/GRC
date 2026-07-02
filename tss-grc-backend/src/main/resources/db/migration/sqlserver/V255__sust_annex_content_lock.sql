-- S-I (Annex from live data + lock) — content hash + lock on the annex report.
--
-- sustainability_report gains:
--   content_hash → SHA-256 of the frozen (markdown+html+json) content, stamped on APPROVE
--   is_locked    → set on APPROVE so the approved content is immutable (re-generation
--                  opens a new version row rather than mutating an approved one)
--
-- Idempotent (COL_LENGTH guards). Dev DB is Flyway-OFF; applied by hand.
IF COL_LENGTH('sust.sustainability_report','content_hash') IS NULL
    ALTER TABLE sust.sustainability_report ADD content_hash VARCHAR(64) NULL;
GO

IF COL_LENGTH('sust.sustainability_report','is_locked') IS NULL
    ALTER TABLE sust.sustainability_report
        ADD is_locked BIT NOT NULL CONSTRAINT df_sust_report_locked DEFAULT 0;
GO

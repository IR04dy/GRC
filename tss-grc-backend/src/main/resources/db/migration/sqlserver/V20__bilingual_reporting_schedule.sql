-- V20 — Add Arabic columns to reporting.report_schedules.
-- Aligns with the project-wide bilingual data-model rule. The legacy `name` and
-- `description` columns are kept (they're already populated); the new `name_ar`
-- and `description_ar` are nullable so existing rows don't need backfill.

IF COL_LENGTH('reporting.report_schedules', 'name_ar') IS NULL
    ALTER TABLE reporting.report_schedules ADD name_ar NVARCHAR(255) NULL;
GO

IF COL_LENGTH('reporting.report_schedules', 'description_ar') IS NULL
    ALTER TABLE reporting.report_schedules ADD description_ar NVARCHAR(MAX) NULL;
GO

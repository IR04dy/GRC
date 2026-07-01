-- ERM epic E-E (KRI consolidation) — drop the retired legacy KRI engine tables.
--
-- The legacy engine's data was copied into the canonical risk.kri_* tables by
-- V246, and the legacy code (LegacyKriController, KriEngineService,
-- risk/entity/Kri*, their repos, KriDefinitionRequest) was deleted in E-E.5.
-- Nothing in the application maps or queries these tables anymore, so they are
-- dropped here. This runs after V246 (which migrates FROM them), so the Flyway
-- order is safe. Idempotent (IF OBJECT_ID guards); child tables first.

IF OBJECT_ID('risk.kri_breach_events', 'U') IS NOT NULL DROP TABLE risk.kri_breach_events;
GO

IF OBJECT_ID('risk.kri_readings', 'U') IS NOT NULL DROP TABLE risk.kri_readings;
GO

IF OBJECT_ID('risk.kri_definitions', 'U') IS NOT NULL DROP TABLE risk.kri_definitions;
GO

-- ERM epic E-E (KRI consolidation) — canonical parity.
--
-- Adds the compliance-framework anchor to the canonical risk.kri_definition so
-- the legacy KRI engine (risk.kri_definitions.framework_id/clause_ref) can be
-- retired without losing the compliance ↔ KRI crosswalk (FrameworkAnchorService).

IF COL_LENGTH('risk.kri_definition', 'linked_framework_id') IS NULL
    ALTER TABLE risk.kri_definition ADD linked_framework_id BIGINT NULL;
GO

IF COL_LENGTH('risk.kri_definition', 'clause_ref') IS NULL
    ALTER TABLE risk.kri_definition ADD clause_ref NVARCHAR(100) NULL;
GO

IF OBJECT_ID('risk.kri_definition') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_kri_definition_framework' AND object_id = OBJECT_ID('risk.kri_definition'))
    CREATE INDEX IX_kri_definition_framework ON risk.kri_definition (organisation_id, linked_framework_id) WHERE linked_framework_id IS NOT NULL;
GO

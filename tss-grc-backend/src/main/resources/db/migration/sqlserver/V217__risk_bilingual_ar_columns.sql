-- V217 — Arabic counterparts for risk treatment descriptions and assessment notes.
-- The platform is Arabic-primary; these user-visible free-text fields were EN-only.
-- Idempotent: guarded by COL_LENGTH so re-applying is a no-op.

IF COL_LENGTH('risk.risk_treatments', 'description_ar') IS NULL
    ALTER TABLE risk.risk_treatments ADD description_ar NVARCHAR(MAX) NULL;

IF COL_LENGTH('risk.risk_assessments', 'assessment_notes_ar') IS NULL
    ALTER TABLE risk.risk_assessments ADD assessment_notes_ar NVARCHAR(MAX) NULL;

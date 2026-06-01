-- V220 — Arabic description for the risk itself (Arabic-primary parity).
-- Companion to V217 (treatment/assessment _ar). Idempotent.

IF COL_LENGTH('risk.risks', 'description_ar') IS NULL
    ALTER TABLE risk.risks ADD description_ar NVARCHAR(MAX) NULL;

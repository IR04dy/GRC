-- V223 — AI/ML Model Registry: structured human oversight + data residency.
--
-- Two governance gaps surfaced in a Saudi-regulator review of the model
-- registry:
--
--   1. Human oversight (SDAIA "Humanity" principle · EU AI Act Art.14)
--      was only expressible in free-text description. Promote it to a
--      structured, queryable mode so the APPROVED_FOR_PROD gate can
--      require it for HIGH+ tier models.
--
--   2. Data residency / cross-border transfer (PDPL Art.29 · SDAIA) was
--      only *inferred* from `hosting`. Make residency explicit and capture
--      the legal basis when data leaves the Kingdom. For HIGH+ models with
--      GCC/GLOBAL residency the gate now also requires a transfer basis.
--
-- Both gate rules are enforced server-side in MlModelService.assertApprovalGate.
-- Idempotent — safe to re-run.

SET NOCOUNT ON;
GO

-- ─── Human oversight ───────────────────────────────────────────────
IF COL_LENGTH('ai_governance.ml_model', 'human_oversight') IS NULL
    ALTER TABLE ai_governance.ml_model ADD human_oversight NVARCHAR(30) NULL;
GO
IF COL_LENGTH('ai_governance.ml_model', 'human_oversight_notes_en') IS NULL
    ALTER TABLE ai_governance.ml_model ADD human_oversight_notes_en NVARCHAR(MAX) NULL;
GO
IF COL_LENGTH('ai_governance.ml_model', 'human_oversight_notes_ar') IS NULL
    ALTER TABLE ai_governance.ml_model ADD human_oversight_notes_ar NVARCHAR(MAX) NULL;
GO

-- ─── Data residency & cross-border transfer (PDPL Art.29) ──────────
IF COL_LENGTH('ai_governance.ml_model', 'data_residency') IS NULL
    ALTER TABLE ai_governance.ml_model ADD data_residency NVARCHAR(20) NULL;
GO
IF COL_LENGTH('ai_governance.ml_model', 'cross_border_transfer_basis') IS NULL
    ALTER TABLE ai_governance.ml_model ADD cross_border_transfer_basis NVARCHAR(40) NULL;
GO

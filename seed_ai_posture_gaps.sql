-- Introduce a small, realistic mixed governance posture on the AI registry so
-- the posture KPIs (no human oversight / cross-border-no-basis / no signed-off
-- AIA) and their drill-down filters are demonstrable. Dev demo only.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

-- 1) Cross-border gap: churn model now runs on a global analytics vendor but the
--    PDPL Art.29 transfer basis hasn't been assessed yet.
UPDATE ai_governance.ml_model
   SET data_residency = N'GLOBAL', cross_border_transfer_basis = N'NOT_ASSESSED'
 WHERE organisation_id = 1 AND model_code = N'MDL-CHR-004';

-- 2) Human-oversight gap: a HIGH-risk model in production whose oversight mode
--    was never documented.
UPDATE ai_governance.ml_model
   SET human_oversight = NULL, human_oversight_notes_en = NULL, human_oversight_notes_ar = NULL
 WHERE organisation_id = 1 AND model_code = N'MDL-OCR-005';

-- 3) AIA gap: a HIGH-risk model whose AI Impact Assessment is still pending.
UPDATE ai_governance.ml_model_audit
   SET is_deleted = 1
 WHERE audit_type = N'AIA'
   AND model_id = (SELECT id FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MDL-AML-003');

SELECT
  (SELECT COUNT(*) FROM ai_governance.ml_model
     WHERE organisation_id=1 AND is_deleted=0 AND risk_tier IN (N'CRITICAL',N'HIGH')
       AND lifecycle_stage IN (N'APPROVED_FOR_PROD',N'IN_PRODUCTION',N'MONITORING')
       AND (human_oversight IS NULL OR human_oversight=N'NONE')) AS missing_oversight,
  (SELECT COUNT(*) FROM ai_governance.ml_model
     WHERE organisation_id=1 AND is_deleted=0 AND data_residency IN (N'GCC',N'GLOBAL')
       AND (cross_border_transfer_basis IS NULL OR cross_border_transfer_basis=N'NOT_ASSESSED')) AS cross_border_no_basis;

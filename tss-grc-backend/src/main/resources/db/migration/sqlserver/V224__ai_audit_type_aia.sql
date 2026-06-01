-- V224 — add AIA (AI Impact Assessment) as a first-class model audit type.
--
-- SDAIA expects a dedicated AI Impact Assessment for high-risk AI, distinct
-- from the PDPL DPIA. AIA is now recorded as a model audit (reusing the
-- structured audit record: methodology, findings, status, metrics, CAPA)
-- and a signed-off (PASSED) AIA is a prerequisite of the APPROVED_FOR_PROD
-- gate for HIGH/CRITICAL models — enforced in MlModelService.
--
-- Widen the CK_ml_audit_type check constraint to include 'AIA'. Idempotent.

SET NOCOUNT ON;
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_ml_audit_type')
    ALTER TABLE ai_governance.ml_model_audit DROP CONSTRAINT CK_ml_audit_type;
GO

ALTER TABLE ai_governance.ml_model_audit
    ADD CONSTRAINT CK_ml_audit_type CHECK (audit_type IN (
        N'AIA', N'FAIRNESS', N'BIAS', N'EXPLAINABILITY',
        N'ROBUSTNESS', N'PRIVACY', N'SECURITY', N'PERFORMANCE'));
GO

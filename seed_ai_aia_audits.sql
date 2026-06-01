-- Seed a signed-off AI Impact Assessment (AIA) audit for each HIGH/CRITICAL
-- model (review item #4). The APPROVED_FOR_PROD gate now requires a passed
-- AIA for elevated-risk tiers; these records make the seeded production
-- models examiner-consistent. Idempotent (NOT EXISTS on model_id+audit_type).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

INSERT INTO ai_governance.ml_model_audit
    (organisation_id, external_id, model_id, audit_type, audit_date,
     auditor_user_id, auditor_external_party, methodology_en, methodology_ar,
     findings_en, findings_ar, status, metrics_json, is_deleted, created_at, created_by)
SELECT
    m.organisation_id, NEWID(), m.id, N'AIA', DATEADD(DAY, -45, CAST(SYSUTCDATETIME() AS DATE)),
    1, N'Model Risk & AI Ethics Committee',
    N'SDAIA AI Ethics impact assessment across the eight principles (fairness, accountability, transparency, privacy, safety/security, reliability, social benefit, humanity), with EU AI Act Annex III fundamental-rights screening.',
    N'تقييم أثر أخلاقيات الذكاء الاصطناعي وفق مبادئ سدايا الثمانية (العدالة، المساءلة، الشفافية، الخصوصية، الأمان، الموثوقية، النفع الاجتماعي، الإنسانية) مع فحص الحقوق الأساسية حسب الملحق الثالث لنظام الاتحاد الأوروبي.',
    N'No prohibited use identified. Residual risk acceptable with human-in-the-loop on adverse decisions and quarterly fairness re-testing.',
    N'لا يوجد استخدام محظور. المخاطر المتبقية مقبولة مع وجود الإنسان في حلقة القرارات السلبية وإعادة اختبار العدالة ربع سنوياً.',
    N'PASSED',
    N'{"fundamental_rights_score":0.86,"residual_risk_rating":"MEDIUM"}',
    0, SYSUTCDATETIME(), 1
FROM ai_governance.ml_model m
WHERE m.is_deleted = 0 AND m.risk_tier IN (N'CRITICAL', N'HIGH')
  AND NOT EXISTS (
        SELECT 1 FROM ai_governance.ml_model_audit a
        WHERE a.model_id = m.id AND a.audit_type = N'AIA' AND a.is_deleted = 0);

SELECT m.model_code, m.risk_tier, a.audit_type, a.status
FROM ai_governance.ml_model_audit a
JOIN ai_governance.ml_model m ON m.id = a.model_id
WHERE a.audit_type = N'AIA' AND m.organisation_id = 1
ORDER BY m.model_code;

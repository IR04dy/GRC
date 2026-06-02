/* ============================================================================
 * seed_ai_drift_observations.sql
 *
 * A realistic model-drift series for ML model #1 (org 1): a Population
 * Stability Index (PSI) climbing past its drift threshold over 10 weeks, with
 * severity escalating INFO → CRITICAL and the most recent breaches left
 * unacknowledged — so the AI-detail Drift chart shows a line breaching its
 * dashed threshold with pulsing red markers.
 *
 * Idempotent: inserts only if this model+metric has no observations yet.
 * Constraints respected:
 *   observation_type ∈ {DATA_DRIFT, CONCEPT_DRIFT, PERFORMANCE_DEGRADATION, FAIRNESS_REGRESSION}
 *   severity         ∈ {INFO, MINOR, MODERATE, MAJOR, CRITICAL}
 * ==========================================================================*/
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

DECLARE @org BIGINT = 1;
DECLARE @model BIGINT = (SELECT TOP 1 id FROM ai_governance.ml_model WHERE organisation_id = @org AND is_deleted = 0 ORDER BY id);

IF @model IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_drift_observation
    WHERE model_id = @model AND metric_name = 'psi' AND is_deleted = 0)
BEGIN
    DECLARE @s TABLE (w INT, val DECIMAL(9,4), sev NVARCHAR(20), ack BIT);
    INSERT INTO @s VALUES
      (10, 0.06, 'INFO',     1),
      (9,  0.07, 'INFO',     1),
      (8,  0.09, 'MINOR',    1),
      (7,  0.11, 'MINOR',    1),
      (6,  0.14, 'MODERATE', 1),
      (5,  0.17, 'MODERATE', 1),
      (4,  0.21, 'MAJOR',    1),  -- crosses the 0.20 threshold
      (3,  0.24, 'MAJOR',    0),
      (2,  0.28, 'CRITICAL', 0),
      (1,  0.31, 'CRITICAL', 0);

    INSERT INTO ai_governance.ml_model_drift_observation
        (organisation_id, external_id, model_id, observed_at, observation_type, severity,
         metric_name, metric_value, baseline_value, drift_threshold,
         notes_en, notes_ar, is_acknowledged, acknowledged_at, is_deleted, created_at, updated_at)
    SELECT @org, NEWID(), @model,
           DATEADD(week, -s.w, CAST(SYSUTCDATETIME() AS datetime2)),
           'DATA_DRIFT', s.sev,
           'psi', s.val, 0.05, 0.20,
           CONCAT('PSI ', s.val, ' vs baseline 0.05 (threshold 0.20).'),
           N'مؤشّر استقرار التوزيع مقابل الأساس.',
           s.ack,
           CASE WHEN s.ack = 1 THEN DATEADD(week, -s.w, CAST(SYSUTCDATETIME() AS datetime2)) ELSE NULL END,
           0, SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM @s s;

    PRINT CONCAT('seed_ai_drift_observations: inserted 10 PSI observations for model ', @model, '.');
END
ELSE
    PRINT 'seed_ai_drift_observations: skipped (model missing or PSI series already present).';

SELECT COUNT(*) AS psi_observations
FROM ai_governance.ml_model_drift_observation
WHERE model_id = @model AND metric_name = 'psi' AND is_deleted = 0;

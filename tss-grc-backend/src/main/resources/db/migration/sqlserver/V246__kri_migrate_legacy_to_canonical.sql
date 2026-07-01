-- ERM epic E-E (KRI consolidation) — data migration.
--
-- Copies the legacy KRI engine's data (risk.kri_definitions / kri_readings /
-- kri_breach_events) into the canonical Sprint-13 engine (risk.kri_definition /
-- kri_measurement / kri_breach) so the legacy stack can be retired without data
-- loss. Idempotent (NOT EXISTS guards) — safe to re-run.
--
-- Field mapping notes:
--   * DIRECTION INVERSION: legacy HIGHER_IS_WORSE ⇒ canonical LOWER_IS_BETTER,
--     legacy LOWER_IS_WORSE ⇒ canonical HIGHER_IS_BETTER (opposite conventions).
--   * category: legacy category_id (FK, all NULL here) → derived from the code
--     prefix (KRI-CYB/OPS/CMP/PRV → CYBER/OPERATIONAL/COMPLIANCE/PRIVACY).
--   * unit: count→COUNT, %→PERCENTAGE, minutes→HOURS (nearest canonical enum).
--   * legacy code is reused as the canonical kri_code (distinct from the
--     canonical seed codes) and is the idempotency key.
--   * breach.measurement_id is resolved via the natural key
--     (kri_id, measured_at, value) on the just-migrated measurements.

-- 1) Definitions ─────────────────────────────────────────────────────────────
INSERT INTO risk.kri_definition
  (organisation_id, kri_code, title_en, title_ar, description_en, description_ar,
   category, measurement_unit, value_direction, target_value, green_threshold,
   amber_threshold, frequency, source_query_type, is_active, linked_risk_id,
   linked_framework_id, clause_ref)
SELECT ld.organisation_id, ld.code, ld.name_en,
       COALESCE(NULLIF(ld.name_ar, N''), ld.name_en),
       ld.description_en, ld.description_ar,
       CASE WHEN ld.code LIKE 'KRI-CYB%' THEN 'CYBER'
            WHEN ld.code LIKE 'KRI-OPS%' THEN 'OPERATIONAL'
            WHEN ld.code LIKE 'KRI-CMP%' THEN 'COMPLIANCE'
            WHEN ld.code LIKE 'KRI-PRV%' THEN 'PRIVACY'
            ELSE 'OPERATIONAL' END,
       CASE ld.unit WHEN 'count' THEN 'COUNT' WHEN '%' THEN 'PERCENTAGE'
            WHEN 'minutes' THEN 'HOURS' ELSE 'COUNT' END,
       CASE WHEN ld.direction = 'LOWER_IS_WORSE' THEN 'HIGHER_IS_BETTER' ELSE 'LOWER_IS_BETTER' END,
       ld.target_value, ld.green_max, ld.amber_max,
       COALESCE(NULLIF(ld.frequency, N''), 'MONTHLY'),
       'MANUAL', ld.is_active, ld.risk_id, ld.framework_id, ld.clause_ref
FROM risk.kri_definitions ld
WHERE ld.is_deleted = 0
  AND ld.code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM risk.kri_definition cd
                  WHERE cd.organisation_id = ld.organisation_id AND cd.kri_code = ld.code);
GO

-- 2) Measurements (from readings) ────────────────────────────────────────────
INSERT INTO risk.kri_measurement
  (organisation_id, kri_id, measured_at, value, status, source, measured_by_user_id, notes_en)
SELECT ld.organisation_id, cd.id, r.reading_at, r.reading_value,
       CASE WHEN r.band IN ('GREEN', 'AMBER', 'RED') THEN r.band ELSE 'NA' END,
       'IMPORTED', r.recorded_by, r.notes
FROM risk.kri_readings r
JOIN risk.kri_definitions ld ON r.kri_id = ld.id
JOIN risk.kri_definition cd  ON cd.organisation_id = ld.organisation_id AND cd.kri_code = ld.code
-- kri_measurement.measured_at is datetime2(3); reading_at is datetime2(7) —
-- cast so the idempotency guard matches the (rounded) stored value on re-run.
WHERE NOT EXISTS (SELECT 1 FROM risk.kri_measurement m
                  WHERE m.kri_id = cd.id AND m.measured_at = CAST(r.reading_at AS datetime2(3))
                    AND m.value = r.reading_value AND m.source = 'IMPORTED');
GO

-- 3) Breaches (measurement_id resolved via natural key) ───────────────────────
INSERT INTO risk.kri_breach
  (organisation_id, kri_id, measurement_id, breach_level, opened_at, closed_at,
   acknowledged_by_user_id, acknowledged_at)
-- Preserve the legacy status: canonical has no status column — "closed" = closed_at
-- set. The legacy seed left resolved_at/acknowledged_at NULL despite status, so fall
-- back to breached_at to keep RESOLVED breaches closed + ACKNOWLEDGED ones acknowledged.
SELECT ld.organisation_id, cd.id, cm.id,
       be.breach_band, be.breached_at,
       COALESCE(be.resolved_at, CASE WHEN be.status = 'RESOLVED' THEN be.breached_at END),
       be.acknowledged_by_id,
       COALESCE(be.acknowledged_at, CASE WHEN be.status IN ('ACKNOWLEDGED', 'RESOLVED') THEN be.breached_at END)
FROM risk.kri_breach_events be
JOIN risk.kri_readings r     ON be.reading_id = r.id
JOIN risk.kri_definitions ld ON be.kri_id = ld.id
JOIN risk.kri_definition cd  ON cd.organisation_id = ld.organisation_id AND cd.kri_code = ld.code
JOIN risk.kri_measurement cm ON cm.kri_id = cd.id AND cm.measured_at = CAST(r.reading_at AS datetime2(3))
                            AND cm.value = r.reading_value AND cm.source = 'IMPORTED'
-- One canonical breach per (kri, opening measurement) — precision-independent
-- idempotency key (a datetime2 equality guard mis-fires on datetime2(3)↔(7)).
WHERE NOT EXISTS (SELECT 1 FROM risk.kri_breach b
                  WHERE b.kri_id = cd.id AND b.measurement_id = cm.id);
GO

-- =====================================================================
-- V237 — Exercise observation → gap link (epic B-E).
-- ---------------------------------------------------------------------
-- An exercise observation (an auditor/observer finding during a drill) can be
-- promoted to a tracked BCM gap, which then drives a CAPA to closure. We record
-- the created gap's id on the observation so the link is first-class and the UI
-- can show "→ Gap #N" and prevent a duplicate promotion. Nullable: most
-- observations (strengths, info notes) never become gaps.
-- =====================================================================

IF COL_LENGTH('bcm.exercise_observation', 'gap_id') IS NULL
    ALTER TABLE bcm.exercise_observation ADD gap_id BIGINT NULL;
GO

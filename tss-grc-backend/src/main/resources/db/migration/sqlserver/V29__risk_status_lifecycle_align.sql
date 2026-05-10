-- V29 — Align risk.risks status CHECK constraint with the service-layer
-- ISO 31000 / COSO ERM lifecycle state names.
--
-- Why this is needed
-- ──────────────────
-- The original CK_risks_status constraint (created in V1) allowed:
--   OPEN, UNDER_ASSESSMENT, TREATMENT_IN_PROGRESS, ACCEPTED, CLOSED, TRANSFERRED
--
-- During Sprint 4 the service-layer state machine was rewritten (RiskService.java)
-- to use the canonical ISO 31000 names:
--   OPEN → UNDER_REVIEW → MITIGATING → CLOSED
--   OPEN/UNDER_REVIEW → ACCEPTED → CLOSED
--   CLOSED → OPEN (reopen)
--
-- The two sets diverged. Calling submitForReview() / approve() failed with
-- HTTP 500 (CHECK constraint violation) because the new status names
-- (UNDER_REVIEW, MITIGATING) weren't allowed by the constraint.
--
-- This migration:
--   1. Migrates legacy rows: UNDER_ASSESSMENT → UNDER_REVIEW
--                            TREATMENT_IN_PROGRESS → MITIGATING
--   2. Drops the old constraint
--   3. Adds a new constraint covering the canonical lifecycle states
--
-- Verified manually in dev on 2026-05-08 — repaired 20 UNDER_ASSESSMENT rows
-- and 0 TREATMENT_IN_PROGRESS rows; all OPEN→UNDER_REVIEW transitions now work.

-- 1) Drop the old constraint if it exists (idempotent)
IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'CK_risks_status' AND parent_object_id = OBJECT_ID('risk.risks')
)
BEGIN
    ALTER TABLE risk.risks DROP CONSTRAINT CK_risks_status;
END;
GO

-- 2) Migrate legacy state names → canonical lifecycle names
UPDATE risk.risks SET status = 'UNDER_REVIEW' WHERE status = 'UNDER_ASSESSMENT';
UPDATE risk.risks SET status = 'MITIGATING'   WHERE status = 'TREATMENT_IN_PROGRESS';
GO

-- 3) Add the new constraint covering the canonical ISO 31000 lifecycle.
-- TRANSFERRED is preserved because it remains a valid terminal state for
-- risks transferred to insurance / a third party.
ALTER TABLE risk.risks ADD CONSTRAINT CK_risks_status
    CHECK (status IN ('OPEN','UNDER_REVIEW','MITIGATING','ACCEPTED','CLOSED','TRANSFERRED'));
GO

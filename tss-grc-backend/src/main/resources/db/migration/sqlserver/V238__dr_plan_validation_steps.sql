-- =====================================================================
-- V238 — DR plan validation checklist (epic B-F/B-G — DRP depth).
-- ---------------------------------------------------------------------
-- A DR plan already captures failover and failback (rollback) steps, but had no
-- place for the post-failover *validation* checklist — the checks a recovery
-- team runs to confirm the failover actually succeeded (data integrity, service
-- health, RPO met, transactions reconciled) before declaring recovery complete.
-- Review §3 #38 flagged this checklist as the missing piece. Stored as JSON,
-- same shape as failover_steps_json. Nullable.
-- =====================================================================

IF COL_LENGTH('bcm.dr_plans', 'validation_steps_json') IS NULL
    ALTER TABLE bcm.dr_plans ADD validation_steps_json NVARCHAR(MAX) NULL;
GO

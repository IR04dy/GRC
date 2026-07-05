-- V265 — CY-H: database-level defence for the cyber evidence locker.
--
-- The cyber write surface binds request bodies straight to JPA entities with no
-- DTO validation, so a phantom control_id (the review saw 999999 accepted) or a
-- nonsense status used to persist silently. CY-H adds service-layer 400s; this
-- migration is the belt-and-braces layer so direct-DB / connector / future-code
-- writes cannot reintroduce the gap either.
--
--   * FK   cyber_evidence.control_id -> cyber_controls(id)   (control_id is nullable)
--   * CHECK cyber_evidence.status IN (<freshness lifecycle>)  (no CHECK existed)
--
-- Both are added WITH NOCHECK: existing rows that predate CY-H are grandfathered
-- (a legacy phantom control_id / off-lifecycle status will not block the migration
-- in any environment), while every INSERT/UPDATE from here on IS enforced — which
-- is exactly the CY-H goal. Forward-only + idempotent (guards on sys.foreign_keys /
-- sys.check_constraints); safe to re-run on the Python-managed dev DB.

-- 1) Foreign key on control_id ------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_cyber_evidence_control')
    ALTER TABLE cyber.cyber_evidence WITH NOCHECK
        ADD CONSTRAINT FK_cyber_evidence_control
        FOREIGN KEY (control_id) REFERENCES cyber.cyber_controls(id);
GO

-- 2) Status enum check --------------------------------------------------------
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_cyber_evidence_status'
             AND parent_object_id = OBJECT_ID('cyber.cyber_evidence'))
    ALTER TABLE cyber.cyber_evidence DROP CONSTRAINT ck_cyber_evidence_status;
GO

ALTER TABLE cyber.cyber_evidence WITH NOCHECK
    ADD CONSTRAINT ck_cyber_evidence_status
    CHECK (status IN ('CURRENT','PENDING','STALE','EXPIRED','ARCHIVED','SUPERSEDED'));
GO

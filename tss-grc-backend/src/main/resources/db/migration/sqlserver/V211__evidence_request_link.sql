-- Evidence-lifecycle hardening: link an evidence item to the request it fulfils.
--
-- Closes the Request<->Item linkage gap. On APPROVED, the originating
-- request's fulfilled_by_id / fulfilled_at are stamped (see
-- EvidenceItemService.fulfilRequest); the nightly expiry sweep re-opens the
-- request when its evidence expires.
--
-- NEEDS_REVISION and EXPIRED are new evidence-item status string values that
-- fit the existing status column (length 30) — no DDL needed for those.
--
-- Dev DB is Python-bootstrapped via setup_evidence_lifecycle_hardening.py;
-- this migration covers Flyway-managed staging/prod. Idempotent.

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'evidence'
      AND TABLE_NAME   = 'evidence_items'
      AND COLUMN_NAME  = 'request_id'
)
BEGIN
    ALTER TABLE evidence.evidence_items ADD request_id BIGINT NULL;
END;

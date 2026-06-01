-- Allow NEEDS_REVISION in evidence.evidence_items.CK_ev_items_status.
--
-- The evidence-lifecycle-hardening change (V211 era) extended the Java state
-- machine (EvidenceItemService.ITEM_STATUSES / ITEM_TRANSITIONS and
-- EvidenceReviewService) to add NEEDS_REVISION as a distinct evidence-item
-- state, but the original CHECK constraint (V1) never listed it. Result:
-- POST /api/v1/evidence/reviews/{id}/complete?result=NEEDS_REVISION returned
-- HTTP 500 (CHECK constraint violation) — and that is the only path an officer
-- has to return evidence for revision.
--
-- Redefine the constraint to the full Java enum plus the two legacy terminal
-- values already present (ARCHIVED, SUPERSEDED). Idempotent — only rewrites
-- when NEEDS_REVISION is not yet allowed. Dev DB is Python-managed via
-- setup_evidence_status_constraint.py; this covers Flyway staging/prod.

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('evidence.evidence_items')
      AND name = 'CK_ev_items_status'
      AND definition NOT LIKE '%NEEDS_REVISION%'
)
BEGIN
    ALTER TABLE evidence.evidence_items DROP CONSTRAINT CK_ev_items_status;

    ALTER TABLE evidence.evidence_items ADD CONSTRAINT CK_ev_items_status
        CHECK (status IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED',
                          'NEEDS_REVISION','EXPIRED','ARCHIVED','SUPERSEDED'));
END;
GO

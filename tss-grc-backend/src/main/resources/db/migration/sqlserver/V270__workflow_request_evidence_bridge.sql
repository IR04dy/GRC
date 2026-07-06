-- V270 — WF-4: bridge the workflow request engine to the real evidence subsystem.
--
-- An EVIDENCE_REQUEST-type workflow request (with evidence_required = 1) now auto-opens a real
-- `evidence.evidence_requests` row on send (anchored entity_type='WORKFLOW_REQUEST',
-- entity_id=<workflow request id>) so the evidence team works their normal queue; this column
-- records the bridged evidence-request id on the workflow side so the two lifecycles stay
-- linked (evidence APPROVED → workflow APPROVED; evidence REJECTED → workflow RETURNED).
--
-- Forward-only; idempotent. Hand-applied in dev via pyodbc (Flyway is off in dev).

IF COL_LENGTH('workflow.requests', 'evidence_request_id') IS NULL
    ALTER TABLE workflow.requests ADD evidence_request_id BIGINT NULL;
GO

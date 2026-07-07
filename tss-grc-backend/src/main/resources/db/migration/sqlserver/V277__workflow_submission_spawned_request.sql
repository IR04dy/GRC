-- V277 — Forms remediation (G1): link a wizard submission to the engine WorkflowRequest it
-- spawned / was routed as, so the wizard-submission and request-engine entry systems are joined
-- by a persisted pointer (in addition to the sourceRecordType='WorkflowSubmission' anchor).
--
-- Nullable, additive — non-destructive. In dev (Flyway off) hand-applied; in non-dev profiles
-- Flyway runs it as part of the forward-only chain.
ALTER TABLE workflow.workflow_submissions ADD spawned_request_id BIGINT NULL;
GO

-- V219 — throttle column for the evidence-request SLA scheduler.
-- Records when the last overdue reminder was sent so the nightly sweep doesn't
-- re-notify the same request every night. Idempotent.

IF COL_LENGTH('evidence.evidence_requests', 'last_reminded_at') IS NULL
    ALTER TABLE evidence.evidence_requests ADD last_reminded_at DATETIME2 NULL;

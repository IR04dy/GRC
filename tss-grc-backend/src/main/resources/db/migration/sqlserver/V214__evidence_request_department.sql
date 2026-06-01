-- Allow an evidence request to be assigned to a department (in addition to /
-- instead of a named assignee). Supports the GRC Expert "Request evidence" flow
-- where a requester routes a sub-control's evidence to a responsible person
-- and/or owning department with a due date. Idempotent.
-- Dev DB is Python-managed via setup_evidence_request_department.py.

IF COL_LENGTH('evidence.evidence_requests', 'department_id') IS NULL
    ALTER TABLE evidence.evidence_requests ADD department_id BIGINT NULL;
GO

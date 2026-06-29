-- V235 — Generic entity reference on evidence requests.
--
-- Until now an evidence request could only point at a compliance sub-control
-- (sub_control_id). BCM (and any other module) needs to raise a real
-- "request evidence from a department" against its own records — a BIA, a
-- BCP/DRP plan, a governance policy, a crisis event, a CAPA — reusing the
-- exact same request lifecycle, SLA scheduler, reminders and escalation.
--
-- We add a free-form (entity_type, entity_id) pair (mirroring the generic
-- attachments table) so a request can be anchored to any aggregate. Both are
-- nullable: existing compliance requests keep using sub_control_id and leave
-- these null. The SLA scheduler is already entity-agnostic (it keys on
-- status + due_date only), so it needs no change.

IF COL_LENGTH('evidence.evidence_requests', 'entity_type') IS NULL
    ALTER TABLE evidence.evidence_requests ADD entity_type NVARCHAR(60) NULL;

IF COL_LENGTH('evidence.evidence_requests', 'entity_id') IS NULL
    ALTER TABLE evidence.evidence_requests ADD entity_id BIGINT NULL;
GO

-- Lookup index for "all requests against this record" (the by-entity finder).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_evreq_entity')
    CREATE INDEX ix_evreq_entity
        ON evidence.evidence_requests (organisation_id, entity_type, entity_id)
        WHERE is_deleted = 0 AND entity_type IS NOT NULL;
GO

-- V261 — Backfill pre-existing /cyber workspace incidents into the register (epic CY-B)
--
-- Companion to V260. V260 added the link columns and the service layer now mirrors
-- every NEW /cyber SOC-workspace incident into the canonical cyber.incidents
-- register (so it reaches the dashboard KPI + PDPL bridge). This migration does the
-- ONE-TIME backfill for workspace incidents that pre-date the change (those with
-- canonical_incident_id IS NULL): it registers a linked register row for each and
-- back-fills the link.
--
-- *** DATA BACKFILL — writes real rows. ***
-- In dev (Flyway-off) it is hand-applied ONLY on explicit confirmation. In non-dev
-- profiles Flyway runs it as part of the normal forward-only chain. It is fully
-- idempotent: guarded by canonical_incident_id IS NULL + NOT EXISTS on the unique
-- source_workspace_incident_id, so re-running matches 0 rows.
--
-- Mapping (identical to CyberIncidentService.workflowToStatus / IncidentService):
--   workflow_status -> status (CK_incidents_status: OPEN|INVESTIGATING|RESOLVED|CLOSED)
--   severity        -> sanitised to CK_incidents_severity (else MEDIUM)
--   category        -> 'CYBER' (CK_incidents_category is strict; type kept in description)

INSERT INTO cyber.incidents
    (organisation_id, source_workspace_incident_id, title, title_ar, severity, category,
     status, nist_phase, discovery_date, description, affects_personal_data, is_deleted, created_at)
SELECT
    w.organisation_id,
    w.id,
    COALESCE(NULLIF(LTRIM(RTRIM(w.title_en)), ''), 'Cyber incident #' + CAST(w.id AS varchar(20))),
    w.title_ar,
    CASE WHEN UPPER(w.severity) IN ('LOW','MEDIUM','HIGH','CRITICAL') THEN UPPER(w.severity) ELSE 'MEDIUM' END,
    'CYBER',
    CASE UPPER(w.workflow_status)
        WHEN 'CLOSURE'          THEN 'RESOLVED'
        WHEN 'RESOLVED'         THEN 'RESOLVED'
        WHEN 'LESSONS_LEARNED'  THEN 'CLOSED'
        WHEN 'CLOSED'           THEN 'CLOSED'
        WHEN 'DONE'             THEN 'CLOSED'
        WHEN 'CONTAINMENT'      THEN 'INVESTIGATING'
        WHEN 'INVESTIGATION'    THEN 'INVESTIGATING'
        WHEN 'INVESTIGATING'    THEN 'INVESTIGATING'
        WHEN 'ANALYSIS'         THEN 'INVESTIGATING'
        WHEN 'ERADICATION'      THEN 'INVESTIGATING'
        WHEN 'RECOVERY'         THEN 'INVESTIGATING'
        WHEN 'IN_PROGRESS'      THEN 'INVESTIGATING'
        WHEN 'IN_REVIEW'        THEN 'INVESTIGATING'
        ELSE 'OPEN'
    END,
    CASE UPPER(w.workflow_status)
        WHEN 'CLOSURE'          THEN 'RECOVERY'
        WHEN 'RESOLVED'         THEN 'RECOVERY'
        WHEN 'LESSONS_LEARNED'  THEN 'POST_INCIDENT'
        WHEN 'CLOSED'           THEN 'POST_INCIDENT'
        WHEN 'CONTAINMENT'      THEN 'CONTAINMENT'
        WHEN 'ERADICATION'      THEN 'ERADICATION'
        WHEN 'RECOVERY'         THEN 'RECOVERY'
        ELSE 'IDENTIFICATION'
    END,
    CAST(w.detected_at AS date),
    'Backfilled from /cyber SOC workspace incident #' + CAST(w.id AS varchar(20))
        + CASE WHEN w.incident_type IS NULL THEN '' ELSE ' · type=' + w.incident_type END,
    0, 0, COALESCE(w.created_at, SYSUTCDATETIME())
FROM cyber.cyber_incidents w
WHERE w.is_deleted = 0
  AND w.canonical_incident_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM cyber.incidents i
                  WHERE i.source_workspace_incident_id = w.id AND i.is_deleted = 0);
GO

-- Back-fill the reverse link on the workspace rows.
UPDATE w
   SET w.canonical_incident_id = i.id
FROM cyber.cyber_incidents w
JOIN cyber.incidents i
  ON i.source_workspace_incident_id = w.id AND i.is_deleted = 0
WHERE w.canonical_incident_id IS NULL AND w.is_deleted = 0;
GO

-- Seed vulnerability-SLA findings (a realistic status mix) so the Vuln-SLA
-- dashboard populates instead of showing all zeros. Findings are normally
-- materialised by the compute scheduler from vulnerabilities + the SLA policy;
-- here we seed them directly for the demo. Dev only; idempotent (no-op if any
-- finding already exists for org 1).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM cyber.vulnerability_sla_finding WHERE organisation_id = 1 AND is_deleted = 0)
BEGIN
    DECLARE @now DATETIME2 = SYSUTCDATETIME();

    INSERT INTO cyber.vulnerability_sla_finding
        (external_id, organisation_id, vulnerability_id, policy_id, cvss_severity,
         asset_id, asset_exposure, asset_criticality, base_sla_hours, effective_sla_hours,
         opened_at, due_at, last_recomputed_at, status, remediated_at, remediated_by,
         is_deleted, created_at, created_by)
    SELECT NEWID(), 1, v.vid, 1, v.sev,
           NULL, v.exposure, v.crit, v.sla, v.sla,
           DATEADD(HOUR, -v.agedHrs, @now),                         -- opened_at
           DATEADD(HOUR, v.sla - v.agedHrs, @now),                  -- due_at = opened + sla
           @now, v.status,
           CASE WHEN v.status = N'REMEDIATED' THEN DATEADD(HOUR, -2, @now) ELSE NULL END,
           CASE WHEN v.status = N'REMEDIATED' THEN 1 ELSE NULL END,
           0, @now, 1
    -- One finding per vulnerability (unique on org+vuln); 5 vulns → 5 statuses.
    FROM (VALUES
        (1, N'CRITICAL', N'PUBLIC',   N'CRITICAL', 72,   100, N'BREACHED'),
        (2, N'HIGH',     N'INTERNAL', N'HIGH',     168,  20,  N'OPEN'),
        (3, N'HIGH',     N'PARTNER',  N'HIGH',     168,  150, N'WARNING'),
        (4, N'MEDIUM',   N'INTERNAL', N'MEDIUM',   720,  50,  N'REMEDIATED'),
        (5, N'MEDIUM',   N'PUBLIC',   N'MEDIUM',   720,  30,  N'ACCEPTED_RISK')
    ) AS v(vid, sev, exposure, crit, sla, agedHrs, status);
END

SELECT status, COUNT(*) AS n FROM cyber.vulnerability_sla_finding
WHERE organisation_id = 1 AND is_deleted = 0 GROUP BY status ORDER BY status;

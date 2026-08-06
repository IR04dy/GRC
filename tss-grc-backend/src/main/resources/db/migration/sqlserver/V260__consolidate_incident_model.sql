-- V260 — Consolidate the incident model (epic CY-B, finding C-CRIT-3 / ARCH-1 / D5)
--
-- FINDING: two live incident tables. The /cyber SOC workspace writes
-- cyber.cyber_incidents (24 rows), but DashboardService KPIs + the PDPL breach
-- bridge (CyberPdplBridgeService) read/update cyber.incidents (20 rows, owned by
-- module/incident). Cyber-workspace incidents therefore NEVER reach the
-- dashboard "open incidents" KPI, the KPI trend, or PDPL breach linkage.
--
-- DIRECTION: cyber.incidents is the incident system of record — the dashboard,
-- the KPI trend, the PDPL bridge, the NCA/PDPL/SAMA/CITC/HRSD regulatory
-- notification timers, MITRE ATT&CK and the RCA sub-module already live on it.
-- Rather than repoint three consumers and migrate a whole schema, we connect the
-- SOC workspace TO the register: every /cyber workspace incident now registers a
-- linked cyber.incidents row (mirror-on-create + status-sync in the service
-- layer). The register stays the single source of truth; the workspace becomes
-- the SOC operating view over it.
--
-- This migration is PURELY ADDITIVE — two nullable link columns + a guard index.
-- It writes NO rows. Backfilling the 24 pre-existing workspace incidents into the
-- register is a SEPARATE, explicitly-confirmed data step (V261, not auto-applied).
--
-- Forward-only + idempotent (guarded by COL_LENGTH / sys.indexes). Dev is
-- Flyway-off; hand-applied via pyodbc.

-- 1) Workspace incident -> its canonical register row.
IF COL_LENGTH('cyber.cyber_incidents', 'canonical_incident_id') IS NULL
    ALTER TABLE cyber.cyber_incidents ADD canonical_incident_id BIGINT NULL;
GO

-- 2) Canonical register row -> the workspace incident it mirrors (reverse link,
--    lets us detect workspace-origin rows + keep the mirror idempotent).
IF COL_LENGTH('cyber.incidents', 'source_workspace_incident_id') IS NULL
    ALTER TABLE cyber.incidents ADD source_workspace_incident_id BIGINT NULL;
GO

-- 3) At most one live register mirror per workspace incident (idempotency guard
--    for both the create hook and any future backfill). Filtered so it only
--    constrains real, non-deleted links.
IF OBJECT_ID('cyber.incidents') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_incidents_source_workspace' AND object_id = OBJECT_ID('cyber.incidents'))
    CREATE UNIQUE INDEX UX_incidents_source_workspace
        ON cyber.incidents (source_workspace_incident_id)
        WHERE source_workspace_incident_id IS NOT NULL AND is_deleted = 0;
GO

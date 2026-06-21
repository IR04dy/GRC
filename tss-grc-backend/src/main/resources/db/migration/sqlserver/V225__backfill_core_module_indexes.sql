-- =============================================================================
-- V225 — Audit WS5/PERF-IX-002: backfill indexes on the Sprint-1/2 core tables.
--
-- V3 (compliance), V4 (incident) and V7 (tprm) shipped their CREATE TABLEs with
-- only a clustered PK on id and no secondary indexes, even though every read is
-- org-scoped (WHERE organisation_id = ?) and the registers filter by status /
-- severity and join on FK columns. This backfills a leading organisation_id
-- composite index per table plus an index on each FK column.
--
-- Plain (non-filtered) indexes are used deliberately: a filtered
-- "WHERE is_deleted = 0" index requires SET QUOTED_IDENTIFIER ON at creation,
-- which is not guaranteed across every migration runner. A leading
-- (organisation_id, ...) index serves the org-scoped + status/severity seeks
-- regardless of session SET options.
--
-- Every statement is guarded by sys.indexes existence, so the migration is
-- idempotent and safe to re-run. (Flyway is disabled in the dev profile — this
-- runs in staging/prod; the same DDL was validated against the dev DB.)
-- =============================================================================

-- SQL Server requires QUOTED_IDENTIFIER ON to create indexes on these tables
-- (they carry a ROWVERSION column). Set it explicitly so the migration is
-- correct under any runner, not just those that default it on (Flyway/JDBC do).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── compliance.controls ─────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_controls_org_status'
               AND object_id = OBJECT_ID('compliance.controls'))
    CREATE NONCLUSTERED INDEX IX_controls_org_status
        ON compliance.controls (organisation_id, status);
GO

-- ── compliance.assessments ──────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_assessments_org_status'
               AND object_id = OBJECT_ID('compliance.assessments'))
    CREATE NONCLUSTERED INDEX IX_assessments_org_status
        ON compliance.assessments (organisation_id, status);
GO

-- ── compliance.findings ─────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_findings_org_status'
               AND object_id = OBJECT_ID('compliance.findings'))
    CREATE NONCLUSTERED INDEX IX_findings_org_status
        ON compliance.findings (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_findings_assessment'
               AND object_id = OBJECT_ID('compliance.findings'))
    CREATE NONCLUSTERED INDEX IX_findings_assessment
        ON compliance.findings (assessment_id);
GO

-- ── cyber.incidents ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_incidents_org_status'
               AND object_id = OBJECT_ID('cyber.incidents'))
    CREATE NONCLUSTERED INDEX IX_incidents_org_status
        ON cyber.incidents (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_incidents_org_severity'
               AND object_id = OBJECT_ID('cyber.incidents'))
    CREATE NONCLUSTERED INDEX IX_incidents_org_severity
        ON cyber.incidents (organisation_id, severity);
GO

-- ── tprm.vendors ────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_vendors_org_status'
               AND object_id = OBJECT_ID('tprm.vendors'))
    CREATE NONCLUSTERED INDEX IX_vendors_org_status
        ON tprm.vendors (organisation_id, status);
GO

-- ── tprm.vendor_assessments (FK) ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_vendor_assessments_vendor'
               AND object_id = OBJECT_ID('tprm.vendor_assessments'))
    CREATE NONCLUSTERED INDEX IX_vendor_assessments_vendor
        ON tprm.vendor_assessments (vendor_id);
GO

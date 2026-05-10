-- ============================================================================
-- TSS GRC — Incidents: NIST SP 800-61r2 lifecycle + Saudi regulator reporting
-- ============================================================================
-- Adds NIST 800-61 phase tracking and Saudi regulator-notification timestamps
-- to the existing cyber.incidents table.
--
-- nist_phase ∈ {IDENTIFICATION, ANALYSIS, CONTAINMENT, ERADICATION, RECOVERY, POST_INCIDENT}
--   No CHECK constraint deliberately — the SCM enum-alignment incident this
--   session showed how easy it is for the frontend to drift when CHECKs are
--   added. The service layer guards transitions instead.
--
-- nca_hey_notified_at         — NCA HEY 24-hour reporting (cyber incidents)
-- data_office_notified_at     — SDAIA Data Office 72-hour PDPL Article 20 reporting
-- citc_notified_at            — CITC 24-hour notification (telcos / cloud providers)
-- sama_notified_at            — SAMA Cyber Threat Intelligence Sharing (banks)
-- hrsd_notified_at            — HRSD breach affecting employee data
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('cyber.incidents', 'nist_phase') IS NULL
BEGIN
    ALTER TABLE cyber.incidents ADD nist_phase NVARCHAR(30) NOT NULL CONSTRAINT DF_cyber_inc_nist_phase DEFAULT 'IDENTIFICATION';
    PRINT N'Added cyber.incidents.nist_phase';
END
ELSE
    PRINT N'cyber.incidents.nist_phase already exists';

-- Saudi regulator notification timestamps
IF COL_LENGTH('cyber.incidents', 'nca_hey_notified_at') IS NULL
    ALTER TABLE cyber.incidents ADD nca_hey_notified_at DATETIME2 NULL;

IF COL_LENGTH('cyber.incidents', 'data_office_notified_at') IS NULL
    ALTER TABLE cyber.incidents ADD data_office_notified_at DATETIME2 NULL;

IF COL_LENGTH('cyber.incidents', 'citc_notified_at') IS NULL
    ALTER TABLE cyber.incidents ADD citc_notified_at DATETIME2 NULL;

IF COL_LENGTH('cyber.incidents', 'sama_notified_at') IS NULL
    ALTER TABLE cyber.incidents ADD sama_notified_at DATETIME2 NULL;

IF COL_LENGTH('cyber.incidents', 'hrsd_notified_at') IS NULL
    ALTER TABLE cyber.incidents ADD hrsd_notified_at DATETIME2 NULL;

-- MITRE ATT&CK tactics + techniques (CSV) — recommendation #5 foundation
IF COL_LENGTH('cyber.incidents', 'mitre_tactics') IS NULL
    ALTER TABLE cyber.incidents ADD mitre_tactics NVARCHAR(500) NULL;

IF COL_LENGTH('cyber.incidents', 'mitre_techniques') IS NULL
    ALTER TABLE cyber.incidents ADD mitre_techniques NVARCHAR(500) NULL;

PRINT N'Saudi regulator notification timestamp columns + MITRE ATT&CK columns ensured.';

-- Index on nist_phase for fast filter on the live-incident dashboard
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_cyber_inc_nist' AND object_id = OBJECT_ID('cyber.incidents'))
    CREATE INDEX IX_cyber_inc_nist ON cyber.incidents(nist_phase) WHERE is_deleted = 0;

PRINT N'Incidents NIST 800-61 lifecycle DDL complete.';

GO

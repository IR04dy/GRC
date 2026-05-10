-- ============================================================================
-- BCM — Process dependency graph (M3 from punch-list)
-- ============================================================================
-- Models upstream/downstream relationships between critical processes for the
-- BIA dependency graph view. Idempotent.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='bcm' AND t.name='process_dependencies')
BEGIN
    CREATE TABLE bcm.process_dependencies (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        process_id               BIGINT NOT NULL,             -- the dependent process (consumer)
        depends_on_process_id    BIGINT NOT NULL,             -- the producer / upstream
        dependency_type          NVARCHAR(40) NOT NULL CONSTRAINT DF_pdep_type DEFAULT N'INTERNAL_PROCESS',
        -- INTERNAL_PROCESS | UPSTREAM_VENDOR | INFRASTRUCTURE | DATA_FEED | PEOPLE_SKILL
        criticality_impact       NVARCHAR(20) NULL,           -- CRITICAL|HIGH|MEDIUM|LOW
        notes                    NVARCHAR(MAX) NULL,
        is_deleted               BIT NOT NULL CONSTRAINT DF_pdep_isdel DEFAULT 0,
        created_at               DATETIME2 NOT NULL CONSTRAINT DF_pdep_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_pdep_proc FOREIGN KEY (process_id) REFERENCES bcm.critical_processes(id),
        CONSTRAINT FK_pdep_upstream FOREIGN KEY (depends_on_process_id) REFERENCES bcm.critical_processes(id),
        CONSTRAINT CK_pdep_no_self CHECK (process_id <> depends_on_process_id)
    );
    CREATE INDEX IX_pdep_org_proc ON bcm.process_dependencies(organisation_id, process_id) WHERE is_deleted = 0;
    CREATE INDEX IX_pdep_upstream ON bcm.process_dependencies(depends_on_process_id) WHERE is_deleted = 0;
    PRINT N'Created bcm.process_dependencies';
END
ELSE PRINT N'bcm.process_dependencies already exists';

-- Seed dependencies between the existing 5 critical processes
-- Map (process → depends_on) by name to avoid hard-coded IDs.
DECLARE @org BIGINT = 1;

INSERT INTO bcm.process_dependencies (organisation_id, process_id, depends_on_process_id, dependency_type, criticality_impact, notes)
SELECT @org, p.id, u.id, v.dt, v.ci, v.notes
FROM (VALUES
  (N'Core Banking Operations',        N'IT Infrastructure Management',  N'INFRASTRUCTURE',     N'CRITICAL', N'Core banking depends on data centre + network infrastructure.'),
  (N'Customer Onboarding',            N'Core Banking Operations',       N'INTERNAL_PROCESS',   N'HIGH',     N'KYC pipeline writes into core accounts post-approval.'),
  (N'Customer Onboarding',            N'IT Infrastructure Management',  N'INFRASTRUCTURE',     N'HIGH',     N'Onboarding portal hosted on shared infra.'),
  (N'Regulatory Reporting',           N'Core Banking Operations',       N'DATA_FEED',          N'CRITICAL', N'SAMA + ZATCA returns derive from core banking ledgers.'),
  (N'Regulatory Reporting',           N'IT Infrastructure Management',  N'INFRASTRUCTURE',     N'MEDIUM',   N'Reporting tools run on shared analytics cluster.'),
  (N'Payroll Processing',             N'IT Infrastructure Management',  N'INFRASTRUCTURE',     N'HIGH',     N'HR + payroll system shares the same DR pair.')
) AS v(consumer, producer, dt, ci, notes)
JOIN bcm.critical_processes p ON p.process_name_en = v.consumer AND p.is_deleted = 0
JOIN bcm.critical_processes u ON u.process_name_en = v.producer AND u.is_deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM bcm.process_dependencies d
    WHERE d.process_id = p.id AND d.depends_on_process_id = u.id AND d.is_deleted = 0
);

PRINT N'Seeded process dependencies (idempotent).';

SELECT
    N'BCM dependency graph DDL complete' AS status,
    (SELECT COUNT(*) FROM bcm.process_dependencies WHERE is_deleted=0) AS dependencies,
    (SELECT COUNT(*) FROM bcm.critical_processes WHERE is_deleted=0)   AS processes;
GO

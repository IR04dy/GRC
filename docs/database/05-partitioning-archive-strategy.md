# 05 - Partitioning and Archive Strategy
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> TSS GRC Platform | SQL Server 2019 | Document Version 1.0

## 1. Partitioning Candidates

| Table | Partition Column | Rationale |
|-------|-----------------|-----------|
| `audit.audit_logs` | `created_at` | Highest-volume table; time-range queries dominate; old data rarely accessed |
| `audit.system_events` | `created_at` | High-volume system event logging |
| `reporting.report_executions` | `started_at` | Grows with each report run; historical executions rarely queried |
| `platform.notifications` | `created_at` | High-volume; old notifications have low access frequency |

## 2. Partition Function

Yearly partitions provide the right granularity: fine enough for efficient range scans, coarse enough to keep partition count manageable.

```sql
-- Create partition function on DATETIME2
CREATE PARTITION FUNCTION pf_yearly (DATETIME2(7))
AS RANGE RIGHT FOR VALUES (
    '2024-01-01T00:00:00',
    '2025-01-01T00:00:00',
    '2026-01-01T00:00:00',
    '2027-01-01T00:00:00',
    '2028-01-01T00:00:00',
    '2029-01-01T00:00:00',
    '2030-01-01T00:00:00'
);
```

> **RANGE RIGHT** means the boundary value belongs to the right partition (i.e., `2025-01-01` starts the 2025 partition). This is the standard convention for date-based partitioning.

### Adding Future Partitions

A yearly maintenance job adds the next year's boundary before January 1:

```sql
-- Run in December of each year
ALTER PARTITION FUNCTION pf_yearly()
    SPLIT RANGE ('2031-01-01T00:00:00');
```

## 3. Partition Scheme

Maps partitions to filegroups. Current and recent years reside on fast storage (`FG_DATA` / `FG_AUDIT`); older years move to archive storage.

```sql
CREATE PARTITION SCHEME ps_yearly
AS PARTITION pf_yearly
TO (
    [FG_ARCHIVE],   -- data before 2024
    [FG_ARCHIVE],   -- 2024
    [FG_AUDIT],     -- 2025
    [FG_AUDIT],     -- 2026 (current year)
    [FG_AUDIT],     -- 2027
    [FG_AUDIT],     -- 2028
    [FG_AUDIT],     -- 2029
    [FG_AUDIT]      -- 2030+
);
```

## 4. Partitioned Table Creation

```sql
-- Audit logs: partitioned by created_at
CREATE TABLE audit.audit_logs (
    id              BIGINT          IDENTITY(1,1) NOT NULL,
    org_id          BIGINT          NOT NULL,
    entity_type     NVARCHAR(50)    NOT NULL,
    entity_id       BIGINT          NOT NULL,
    action          NVARCHAR(30)    NOT NULL,
    user_id         BIGINT          NULL,
    user_email      NVARCHAR(255)   NULL,
    ip_address      NVARCHAR(45)    NULL,
    user_agent      NVARCHAR(500)   NULL,
    old_values_json NVARCHAR(MAX)   NULL,
    new_values_json NVARCHAR(MAX)   NULL,
    description     NVARCHAR(1000)  NULL,
    created_at      DATETIME2(7)    NOT NULL DEFAULT SYSUTCDATETIME(),

    CONSTRAINT PK_audit_logs PRIMARY KEY NONCLUSTERED (id, created_at)
) ON ps_yearly (created_at);

-- Clustered index on partition column for efficient range scans
CREATE CLUSTERED INDEX CIX_audit_logs_created_at
    ON audit.audit_logs (created_at)
    ON ps_yearly (created_at);
```

> **Note:** The PK must include the partition column for partitioned tables. This is a SQL Server requirement for aligned indexes.

## 5. Partition-Aligned Indexes

All indexes on partitioned tables must be aligned (include the partition column) or be non-aligned with caution.

```sql
-- Aligned indexes (recommended)
CREATE NONCLUSTERED INDEX IX_audit_logs_entity
    ON audit.audit_logs (entity_type, entity_id, created_at DESC)
    INCLUDE (action, user_id)
    ON ps_yearly (created_at);

CREATE NONCLUSTERED INDEX IX_audit_logs_user
    ON audit.audit_logs (user_id, created_at DESC)
    INCLUDE (entity_type, entity_id, action)
    ON ps_yearly (created_at);

CREATE NONCLUSTERED INDEX IX_audit_logs_org
    ON audit.audit_logs (org_id, created_at DESC)
    ON ps_yearly (created_at);
```

## 6. Archive Strategy

### 6.1 Archive Table Pattern

Archive tables mirror the structure of the source table and reside on the `FG_ARCHIVE` filegroup.

```sql
-- Archive table for audit logs (non-partitioned, on archive filegroup)
CREATE TABLE audit.audit_logs_archive (
    id              BIGINT          NOT NULL,
    org_id          BIGINT          NOT NULL,
    entity_type     NVARCHAR(50)    NOT NULL,
    entity_id       BIGINT          NOT NULL,
    action          NVARCHAR(30)    NOT NULL,
    user_id         BIGINT          NULL,
    user_email      NVARCHAR(255)   NULL,
    ip_address      NVARCHAR(45)    NULL,
    user_agent      NVARCHAR(500)   NULL,
    old_values_json NVARCHAR(MAX)   NULL,
    new_values_json NVARCHAR(MAX)   NULL,
    description     NVARCHAR(1000)  NULL,
    created_at      DATETIME2(7)    NOT NULL,
    archived_at     DATETIME2(7)    NOT NULL DEFAULT SYSUTCDATETIME()
) ON [FG_ARCHIVE];

CREATE CLUSTERED INDEX CIX_audit_logs_archive_created_at
    ON audit.audit_logs_archive (created_at) ON [FG_ARCHIVE];
```

### 6.2 Partition Switch for Fast Archival

Partition switching is an O(1) metadata operation -- it moves an entire partition's data instantly by reassigning filegroup pointers.

```sql
-- Step 1: Create staging table with identical structure on target filegroup
CREATE TABLE audit.audit_logs_staging (
    -- identical column definitions as audit.audit_logs
    -- identical constraints and indexes
) ON [FG_ARCHIVE];

-- Step 2: Switch old partition to staging table
ALTER TABLE audit.audit_logs
    SWITCH PARTITION 1 TO audit.audit_logs_staging;

-- Step 3: Insert staging data into archive (or leave staging as archive)
INSERT INTO audit.audit_logs_archive
    SELECT *, SYSUTCDATETIME() AS archived_at
    FROM audit.audit_logs_staging;

-- Step 4: Truncate staging
TRUNCATE TABLE audit.audit_logs_staging;

-- Step 5: Merge empty partition boundary
ALTER PARTITION FUNCTION pf_yearly()
    MERGE RANGE ('2024-01-01T00:00:00');
```

## 7. Retention Policies

### 7.1 Configurable Retention Periods

Retention periods are stored in `admin.system_configurations` and enforced by a scheduled SQL Agent job.

| Entity Category | Default Retention | Configuration Key | Rationale |
|----------------|-------------------|-------------------|-----------|
| Audit logs | 7 years | `RETENTION_AUDIT_LOGS_YEARS` | Regulatory requirement (NCA, SAMA) |
| System events | 3 years | `RETENTION_SYSTEM_EVENTS_YEARS` | Operational monitoring |
| Report executions | 3 years | `RETENTION_REPORT_EXECUTIONS_YEARS` | Historical report outputs |
| Notifications | 1 year | `RETENTION_NOTIFICATIONS_YEARS` | Low long-term value |
| Risk assessments | 7 years | `RETENTION_RISK_ASSESSMENTS_YEARS` | Regulatory and historical analysis |
| Compliance assessments | 7 years | `RETENTION_COMPLIANCE_ASSESSMENTS_YEARS` | Audit trail requirement |
| Workflow instances (completed) | 5 years | `RETENTION_WORKFLOW_INSTANCES_YEARS` | Process history |
| Soft-deleted records | 90 days | `RETENTION_SOFT_DELETED_DAYS` | Grace period before hard delete |

### 7.2 Retention Enforcement Job

```sql
-- SQL Agent Job: TSS_GRC_Archive_Purge
-- Schedule: Weekly, Sunday at 01:00 AM
-- Step 1: Archive expired data

DECLARE @audit_retention_years INT;
SELECT @audit_retention_years = CAST(config_value AS INT)
FROM admin.system_configurations
WHERE config_key = 'RETENTION_AUDIT_LOGS_YEARS';

DECLARE @cutoff_date DATETIME2(7) = DATEADD(YEAR, -@audit_retention_years, SYSUTCDATETIME());

-- For partitioned tables: use partition switch (described above)
-- For non-partitioned tables: batch delete

DECLARE @batch_size INT = 10000;
DECLARE @rows_affected INT = 1;

WHILE @rows_affected > 0
BEGIN
    DELETE TOP (@batch_size)
    FROM platform.notifications
    WHERE created_at < @cutoff_date
      AND is_deleted = 1;

    SET @rows_affected = @@ROWCOUNT;
END
```

## 8. Archival Data Access

Archived data remains queryable for compliance and audit purposes.

```sql
-- View that unions current and archived data
CREATE VIEW audit.vw_audit_logs_all
AS
    SELECT id, org_id, entity_type, entity_id, action, user_id,
           user_email, ip_address, old_values_json, new_values_json,
           description, created_at, 'CURRENT' AS data_source
    FROM audit.audit_logs
    UNION ALL
    SELECT id, org_id, entity_type, entity_id, action, user_id,
           user_email, ip_address, old_values_json, new_values_json,
           description, created_at, 'ARCHIVE' AS data_source
    FROM audit.audit_logs_archive;
```

## 9. Export to External Storage

For long-term retention beyond SQL Server:

```
┌────────────────┐    Partition    ┌──────────────────┐    Export    ┌─────────────────┐
│  audit.        │ ──Switch──────> │  audit.           │ ──BCP/───> │  Azure Blob /   │
│  audit_logs    │                 │  audit_logs_      │   SSIS     │  NAS / Tape     │
│  (partitioned) │                 │  archive          │            │  (Parquet/CSV)  │
└────────────────┘                 └──────────────────┘            └─────────────────┘
```

- **Format:** Parquet (preferred for analytics) or compressed CSV.
- **Frequency:** After partition switch, export archive table data.
- **Validation:** Row count and checksum verification after export.
- **Catalog:** Maintain an export manifest table recording what was exported, when, and where.

## 10. Monitoring

```sql
-- Check partition row counts
SELECT
    p.partition_number,
    p.rows,
    prv.value AS boundary_value,
    fg.name AS filegroup_name
FROM sys.partitions p
JOIN sys.indexes i ON p.object_id = i.object_id AND p.index_id = i.index_id
JOIN sys.partition_schemes ps ON i.data_space_id = ps.data_space_id
JOIN sys.partition_functions pf ON ps.function_id = pf.function_id
LEFT JOIN sys.partition_range_values prv ON pf.function_id = prv.function_id
    AND p.partition_number = prv.boundary_id + 1
JOIN sys.destination_data_spaces dds ON ps.data_space_id = dds.partition_scheme_id
    AND p.partition_number = dds.destination_id
JOIN sys.filegroups fg ON dds.data_space_id = fg.data_space_id
WHERE p.object_id = OBJECT_ID('audit.audit_logs')
    AND i.index_id <= 1
ORDER BY p.partition_number;
```

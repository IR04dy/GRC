# 01 - Database Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> TSS GRC Platform | SQL Server 2019 | Document Version 1.0

## 1. Overview

The TSS GRC Platform uses a **single-database, multi-schema** architecture on Microsoft SQL Server 2019. The database name is `TSS_GRC`.

## 2. Architecture Decision: Single Database with Multiple Schemas

### Decision

All GRC modules reside in one database (`TSS_GRC`) with logical separation achieved through SQL Server schemas.

### Rationale

| Concern | Single Database Advantage |
|---------|--------------------------|
| Cross-module queries | Direct JOINs across schemas without linked servers or distributed transactions |
| Referential integrity | Foreign keys enforced across schemas within the same database |
| Transactions | Single-database transactions without MSDTC overhead |
| Backup/Restore | One backup captures the entire platform state consistently |
| Schema migrations | Flyway manages all schemas in a single migration chain |
| Connection pooling | One connection pool serves all modules |
| Licensing | Single SQL Server license; no per-database cost multiplication |

### Trade-offs Accepted

- All modules share the same recovery model (FULL recovery for point-in-time restore).
- A catastrophic failure affects all modules simultaneously (mitigated by HA/DR strategy).
- Schema-level permissions substitute for database-level isolation.

## 3. Schema Organization

Each schema corresponds to a functional module boundary. Schemas are owned by dedicated database roles for granular permission control.

```
TSS_GRC (Database)
├── platform    -- Shared business entities
├── iam         -- Identity and access management
├── admin       -- Administration and configuration
├── workflow    -- Workflow engine
├── risk        -- Enterprise Risk Management
├── cyber       -- Cybersecurity Risk & Compliance
├── bcm         -- Business Continuity Management
├── privacy     -- Privacy & Personal Data Protection
├── compliance  -- Compliance & Audit Management
├── reporting   -- Reporting & Analytics
└── audit       -- Audit trail and system events
```

## 4. Filegroup Layout

Filegroups separate data by access pattern and lifecycle for targeted backup, restore, and I/O optimization.

| Filegroup | Purpose | Files | Notes |
|-----------|---------|-------|-------|
| `PRIMARY` | System catalog, metadata | `TSS_GRC.mdf` | Small, rarely written |
| `FG_DATA` | All business entity tables | `TSS_GRC_Data_01.ndf`, `TSS_GRC_Data_02.ndf` | Multiple files for parallel I/O; add files as needed |
| `FG_INDEX` | Non-clustered indexes | `TSS_GRC_Index_01.ndf` | Separate I/O path from data reads |
| `FG_AUDIT` | Audit log and system event tables | `TSS_GRC_Audit_01.ndf` | High-write, append-only workload; isolated to avoid contention |
| `FG_ARCHIVE` | Partitioned historical data | `TSS_GRC_Archive_01.ndf` | Older partitions switched here; can be placed on slower storage |
| `FG_LOB` | Large objects (attachments metadata overflow, JSON) | `TSS_GRC_LOB_01.ndf` | Isolates LOB allocations from row data |

### File Growth Settings

| Filegroup | Initial Size | Auto-Growth Increment |
|-----------|-------------|----------------------|
| `PRIMARY` | 256 MB | 64 MB |
| `FG_DATA` | 1 GB per file | 256 MB |
| `FG_INDEX` | 512 MB | 128 MB |
| `FG_AUDIT` | 1 GB | 256 MB |
| `FG_ARCHIVE` | 512 MB | 256 MB |
| `FG_LOB` | 256 MB | 128 MB |
| Log file | 1 GB | 128 MB |

> **Important:** Never use percentage-based auto-growth. Fixed-size increments prevent runaway growth events and reduce VLF fragmentation.

## 5. Collation

```
Latin1_General_100_CI_AS_SC_UTF8
```

| Component | Meaning |
|-----------|---------|
| `Latin1_General_100` | Version 100 collation with improved Unicode sorting |
| `CI` | Case-insensitive comparisons |
| `AS` | Accent-sensitive comparisons |
| `SC` | Supplementary character support (full Unicode range) |
| `UTF8` | UTF-8 encoding for `VARCHAR`/`CHAR` types; `NVARCHAR` columns use UTF-16 natively |

### Why This Collation

- Full Arabic language support with correct sorting and comparison rules.
- English text handled natively.
- `NVARCHAR` columns store Arabic and English text without data loss.
- `SC` flag ensures supplementary Unicode characters (emoji, rare glyphs) are handled correctly.

## 6. Compatibility Level

```sql
ALTER DATABASE TSS_GRC SET COMPATIBILITY_LEVEL = 150; -- SQL Server 2019
```

This enables all SQL Server 2019 features:
- Intelligent Query Processing (batch mode on rowstore, memory grant feedback, table variable deferred compilation)
- UTF-8 collation support
- Accelerated Database Recovery (ADR)
- Resumable online index operations
- Optimized `sys.dm_exec_query_plan_stats` for last actual query plan

## 7. Recovery Model

```sql
ALTER DATABASE TSS_GRC SET RECOVERY FULL;
```

- Full recovery model enables point-in-time restore.
- Transaction log backups every 15 minutes.
- Full backup daily; differential every 6 hours.

## 8. Transparent Data Encryption (TDE)

TDE encrypts data at rest (data files, log files, TempDB, backups) with zero application changes.

```sql
-- 1. Create master key in master database
USE master;
CREATE MASTER KEY ENCRYPTION BY PASSWORD = '<strong-password>';

-- 2. Create certificate
CREATE CERTIFICATE TSS_GRC_TDE_Cert WITH SUBJECT = 'TSS GRC TDE Certificate';

-- 3. Create database encryption key
USE TSS_GRC;
CREATE DATABASE ENCRYPTION KEY
    WITH ALGORITHM = AES_256
    ENCRYPTION BY SERVER CERTIFICATE TSS_GRC_TDE_Cert;

-- 4. Enable encryption
ALTER DATABASE TSS_GRC SET ENCRYPTION ON;
```

> **Critical:** Back up the TDE certificate and private key immediately. Without them, backups cannot be restored on another server.

```sql
BACKUP CERTIFICATE TSS_GRC_TDE_Cert
    TO FILE = 'C:\Backup\Certs\TSS_GRC_TDE_Cert.cer'
    WITH PRIVATE KEY (
        FILE = 'C:\Backup\Certs\TSS_GRC_TDE_Cert.pvk',
        ENCRYPTION BY PASSWORD = '<cert-backup-password>'
    );
```

## 9. Row-Level Security Readiness

The schema is designed to support SQL Server Row-Level Security (RLS) for multi-tenant filtering.

### Design Pattern

Every business entity table includes an `org_id` column referencing `platform.organizations.id`. RLS filter predicates can be applied to restrict rows by organization.

```sql
-- Example RLS setup (applied when multi-tenancy is activated)
CREATE FUNCTION platform.fn_org_filter(@org_id BIGINT)
RETURNS TABLE
WITH SCHEMABINDING
AS
    RETURN SELECT 1 AS result
    WHERE @org_id = CAST(SESSION_CONTEXT(N'org_id') AS BIGINT)
       OR SESSION_CONTEXT(N'is_super_admin') = CAST(1 AS SQL_VARIANT);

CREATE SECURITY POLICY platform.OrgFilter
    ADD FILTER PREDICATE platform.fn_org_filter(org_id) ON risk.risks,
    ADD FILTER PREDICATE platform.fn_org_filter(org_id) ON cyber.cyber_assets,
    -- ... additional tables ...
    WITH (STATE = ON, SCHEMABINDING = ON);
```

### Application-Side Setup

```java
// Set session context on each request (Spring interceptor)
try (var stmt = connection.createStatement()) {
    stmt.execute("EXEC sp_set_session_context @key = N'org_id', @value = " + currentUser.getOrgId());
    stmt.execute("EXEC sp_set_session_context @key = N'is_super_admin', @value = " + (currentUser.isSuperAdmin() ? 1 : 0));
}
```

## 10. Database Creation Script

```sql
-- Create database with filegroups
CREATE DATABASE TSS_GRC
ON PRIMARY (
    NAME = N'TSS_GRC',
    FILENAME = N'D:\SQLData\TSS_GRC.mdf',
    SIZE = 256MB,
    FILEGROWTH = 64MB
),
FILEGROUP FG_DATA (
    NAME = N'TSS_GRC_Data_01',
    FILENAME = N'D:\SQLData\TSS_GRC_Data_01.ndf',
    SIZE = 1GB,
    FILEGROWTH = 256MB
),
FILEGROUP FG_INDEX (
    NAME = N'TSS_GRC_Index_01',
    FILENAME = N'D:\SQLIndex\TSS_GRC_Index_01.ndf',
    SIZE = 512MB,
    FILEGROWTH = 128MB
),
FILEGROUP FG_AUDIT (
    NAME = N'TSS_GRC_Audit_01',
    FILENAME = N'D:\SQLData\TSS_GRC_Audit_01.ndf',
    SIZE = 1GB,
    FILEGROWTH = 256MB
),
FILEGROUP FG_ARCHIVE (
    NAME = N'TSS_GRC_Archive_01',
    FILENAME = N'E:\SQLArchive\TSS_GRC_Archive_01.ndf',
    SIZE = 512MB,
    FILEGROWTH = 256MB
),
FILEGROUP FG_LOB (
    NAME = N'TSS_GRC_LOB_01',
    FILENAME = N'D:\SQLData\TSS_GRC_LOB_01.ndf',
    SIZE = 256MB,
    FILEGROWTH = 128MB
)
LOG ON (
    NAME = N'TSS_GRC_Log',
    FILENAME = N'L:\SQLLog\TSS_GRC_Log.ldf',
    SIZE = 1GB,
    FILEGROWTH = 128MB
);
GO

-- Set default filegroup for new tables
ALTER DATABASE TSS_GRC MODIFY FILEGROUP FG_DATA DEFAULT;
GO

-- Set collation
ALTER DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

-- Set compatibility level
ALTER DATABASE TSS_GRC SET COMPATIBILITY_LEVEL = 150;
GO

-- Set recovery model
ALTER DATABASE TSS_GRC SET RECOVERY FULL;
GO

-- Enable Accelerated Database Recovery
ALTER DATABASE TSS_GRC SET ACCELERATED_DATABASE_RECOVERY = ON;
GO

-- Enable Query Store for performance monitoring
ALTER DATABASE TSS_GRC SET QUERY_STORE = ON
(
    OPERATION_MODE = READ_WRITE,
    DATA_FLUSH_INTERVAL_SECONDS = 900,
    INTERVAL_LENGTH_MINUTES = 60,
    MAX_STORAGE_SIZE_MB = 1024,
    QUERY_CAPTURE_MODE = AUTO,
    SIZE_BASED_CLEANUP_MODE = AUTO,
    MAX_PLANS_PER_QUERY = 200,
    WAIT_STATS_CAPTURE_MODE = ON
);
GO
```

## 11. Schema Creation

```sql
USE TSS_GRC;
GO

-- Create database roles (schema owners)
CREATE ROLE role_platform;
CREATE ROLE role_iam;
CREATE ROLE role_admin;
CREATE ROLE role_workflow;
CREATE ROLE role_risk;
CREATE ROLE role_cyber;
CREATE ROLE role_bcm;
CREATE ROLE role_privacy;
CREATE ROLE role_compliance;
CREATE ROLE role_reporting;
CREATE ROLE role_audit;
GO

-- Create schemas with role ownership
CREATE SCHEMA platform AUTHORIZATION role_platform;
CREATE SCHEMA iam AUTHORIZATION role_iam;
CREATE SCHEMA admin AUTHORIZATION role_admin;
CREATE SCHEMA workflow AUTHORIZATION role_workflow;
CREATE SCHEMA risk AUTHORIZATION role_risk;
CREATE SCHEMA cyber AUTHORIZATION role_cyber;
CREATE SCHEMA bcm AUTHORIZATION role_bcm;
CREATE SCHEMA privacy AUTHORIZATION role_privacy;
CREATE SCHEMA compliance AUTHORIZATION role_compliance;
CREATE SCHEMA reporting AUTHORIZATION role_reporting;
CREATE SCHEMA audit AUTHORIZATION role_audit;
GO
```

## 12. TempDB Considerations

- Configure TempDB with **8 equal-sized data files** (matching CPU core count up to 8).
- Initial size: 512 MB per file.
- Growth: 256 MB per file (uniform).
- Place on fastest available storage (NVMe SSD preferred).
- Required for: hash joins in cross-schema queries, sort operations on large result sets, temporary table usage in reporting views, version store for snapshot isolation and temporal tables.

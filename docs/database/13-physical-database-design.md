# Physical Database Design — SQL Server 2019 Express Edition

## Purpose
Defines the physical implementation decisions for the TSS GRC Platform database, specifically targeting **SQL Server 2019 Express Edition** with awareness of its constraints and capabilities.

---

## SQL Server Express Edition — Key Constraints and Adaptations

| Feature | Express Limitation | Our Adaptation |
|---------|-------------------|----------------|
| Database size | **10 GB maximum** | Design for efficient storage; archive strategy critical |
| RAM | 1.4 GB buffer pool | Optimize queries; use covering indexes; minimize full scans |
| CPU | 1 socket / 4 cores | Application-level connection pooling; async processing |
| SQL Server Agent | ❌ Not available | Use Spring `@Scheduled` for all background jobs |
| Full-Text Search | ❌ Not available | LIKE-based search; upgrade to Standard when needed |
| Table Partitioning | ❌ Not available | Application-level archival to `_archive` tables |
| TDE (Encryption at Rest) | ❌ Not available | Application-level field encryption for sensitive columns |
| Always On / Mirroring | ❌ Not available | Manual backup/restore for HA; upgrade path documented |
| Columnstore Indexes | ✅ Available | Can be used for reporting tables |
| Temporal Tables | ✅ Available | Used for critical entity history |
| JSON Support | ✅ Available | Used for audit log values storage |
| Row-Level Security | ✅ Available | Planned for multi-tenant future |
| Transparent Data Encryption | ❌ Not available | **Use BitLocker/OS-level encryption on Express** |

---

## Primary Key Strategy

**Decision: BIGINT IDENTITY(1,1)** for all clustered PKs

Rationale:
- Sequential integers are optimal for clustered index performance (B-tree structure)
- Avoid page splits caused by NEWID() GUIDs
- 64-bit integer supports billions of records per table

**Additionally:** `external_id UNIQUEIDENTIFIER DEFAULT NEWID()` on key entities for:
- External API exposure (never expose internal BIGINT to URLs/APIs)
- Future inter-system integration IDs
- GUID uniqueness across distributed systems

```sql
id          BIGINT           IDENTITY(1,1) NOT NULL,
external_id UNIQUEIDENTIFIER NOT NULL      DEFAULT NEWID(),
CONSTRAINT PK_{table} PRIMARY KEY CLUSTERED (id ASC)
```

---

## Data Type Decisions

| Category | Type | Rationale |
|----------|------|-----------|
| All text | `NVARCHAR(n)` | Arabic + English Unicode; no VARCHAR allowed |
| Short text (names, codes) | `NVARCHAR(50-200)` | Indexed; length-constrained |
| Long text (descriptions) | `NVARCHAR(MAX)` | Flexible; not in indexes |
| Timestamps | `DATETIME2(7)` | Microsecond precision; range 0001–9999; recommended over DATETIME |
| Dates only | `DATE` | When time component not needed (review_date, target_date) |
| Boolean | `BIT NOT NULL DEFAULT 0` | SQL Server boolean; never NULL |
| Scores | `DECIMAL(5,2)` | Risk scores (0.00–25.00 for 5×5 matrix) |
| Financial amounts | `DECIMAL(18,4)` | Monetary values with 4 decimal places |
| Large identifiers | `BIGINT` | FK references to BIGINT PKs |
| External IDs | `UNIQUEIDENTIFIER` | API-facing identifiers |
| Concurrency | `ROWVERSION` | Optimistic locking; 8-byte auto-incrementing |
| JSON storage | `NVARCHAR(MAX)` | audit log old/new values; dashboard config |

---

## Filegroup Strategy

For SQL Server Express (single data file is sufficient initially):

```sql
-- Express Edition: Use default PRIMARY filegroup
-- Standard/Enterprise: Use dedicated filegroups
CREATE DATABASE TSS_GRC
ON PRIMARY (
    NAME = TSS_GRC_Data,
    FILENAME = 'C:\SQLData\TSS_GRC_Data.mdf',
    SIZE = 256MB,
    MAXSIZE = 9GB,       -- Express max is 10GB; leave 1GB buffer
    FILEGROWTH = 256MB
)
LOG ON (
    NAME = TSS_GRC_Log,
    FILENAME = 'C:\SQLLog\TSS_GRC_Log.ldf',
    SIZE = 64MB,
    MAXSIZE = 2GB,
    FILEGROWTH = 64MB
);
```

> **Express DB Size Alert:** At 10 GB limit, database will be read-only. Implement monitoring to alert at 8 GB. Archival strategy must run before limit is reached. Consider upgrading to SQL Server Standard at 5+ GB.

---

## Collation

```sql
COLLATE Latin1_General_100_CI_AS_SC_UTF8
```

Applied at database level; inherited by all columns. Arabic text is stored and sorted correctly.

---

## Concurrency Control

```sql
row_version ROWVERSION NOT NULL
```

- Automatically updated on every row modification
- Used by JPA `@Version` for optimistic locking
- Prevents lost updates in concurrent editing scenarios

---

## Auto-Growth Settings

Avoid event-driven auto-growth during peak usage:
- Pre-size database to expected 3-month volume at deployment
- Set auto-grow to **fixed increments** (not percentage): 256 MB data, 64 MB log
- Monitor and pre-grow manually when 70% full

---

## Security at Physical Level (Express Edition)

Since TDE is not available in Express:

1. **OS-level encryption**: Enable BitLocker (Windows) on the SQL Server data volume
2. **Application-level encryption**: Sensitive fields (MFA secrets, API keys) encrypted in Java before storage
3. **Network encryption**: Force encrypted connections via SQL Server Configuration Manager
4. **SQL Server login**: Use SQL authentication with strong password; prefer Windows Authentication for on-prem
5. **Minimum privilege**: Application uses a dedicated SQL login with only necessary permissions

```sql
-- Create dedicated application login
CREATE LOGIN grc_app WITH PASSWORD = N'[strong-password]',
    CHECK_POLICY = ON, CHECK_EXPIRATION = ON;
CREATE USER grc_app FOR LOGIN grc_app;

-- Grant schema-level permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::platform TO grc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::iam TO grc_app;
-- ... (all schemas except audit)
GRANT INSERT ON SCHEMA::audit TO grc_app;  -- INSERT ONLY for audit logs
```

---

## Storage Estimation (Express Edition)

| Table | Est. Rows/Year | Est. Size/Row | Annual Growth |
|-------|---------------|---------------|---------------|
| audit.audit_logs | 1,000,000 | ~2 KB | ~2 GB |
| workflow.workflow_instance_steps | 100,000 | ~1 KB | ~100 MB |
| risk.risks | 5,000 | ~3 KB | ~15 MB |
| compliance.assessment_controls | 50,000 | ~2 KB | ~100 MB |
| platform.attachments (metadata) | 20,000 | ~1 KB | ~20 MB |

**Total estimated growth: ~2.5 GB/year** — requires active archival to stay within 10 GB Express limit.

**Recommendation:** Upgrade to SQL Server Standard Edition when sustained production use begins to avoid the 10 GB constraint.

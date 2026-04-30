# Audit, History, and Versioning Strategy

## Purpose
Defines the SQL Server 2019 approach to audit logging, change history, and data versioning, including system-versioned temporal tables, application-level audit capture, and concurrency control.

---

## SQL Server Express Edition Note
> **SQL Server Express Edition** is the target for Phase 1. All features below are compatible with Express Edition. Temporal tables, JSON support, and row-level security are available in Express. TDE (Transparent Data Encryption) is NOT available in Express — application-level encryption must compensate for sensitive fields.

---

## Strategy 1: Application Audit Log Table

`audit.audit_logs` is the primary audit mechanism for all user-facing actions.

### Design Decisions
- **INSERT-only**: Application DB user has INSERT permission only; no UPDATE/DELETE
- **JSON storage**: `old_values` and `new_values` stored as `NVARCHAR(MAX)` JSON
- **No FK constraints**: audit log references entities by ID only (no FK) — ensures audit records survive entity deletion
- **Partitioned by month** (when data volume warrants, using application-level partitioning to avoid Enterprise Edition requirement)

```sql
CREATE TABLE audit.audit_logs (
    id                BIGINT IDENTITY(1,1) NOT NULL,
    event_timestamp   DATETIME2(7)         NOT NULL DEFAULT SYSUTCDATETIME(),
    user_id           BIGINT               NULL,     -- NULL for system actions
    username          NVARCHAR(200)        NULL,     -- Denormalized for audit integrity
    session_id        NVARCHAR(200)        NULL,
    org_id            BIGINT               NULL,
    module_code       NVARCHAR(50)         NULL,
    entity_type       NVARCHAR(200)        NOT NULL,
    entity_id         BIGINT               NULL,
    action            NVARCHAR(50)         NOT NULL,
    old_values        NVARCHAR(MAX)        NULL,     -- JSON
    new_values        NVARCHAR(MAX)        NULL,     -- JSON
    ip_address        NVARCHAR(50)         NULL,
    user_agent        NVARCHAR(500)        NULL,
    correlation_id    NVARCHAR(100)        NULL,
    additional_info   NVARCHAR(MAX)        NULL,
    status            NVARCHAR(50)         NOT NULL DEFAULT 'SUCCESS',
    CONSTRAINT PK_audit_logs PRIMARY KEY CLUSTERED (id ASC)
);
```

---

## Strategy 2: SQL Server Temporal Tables (System-Versioned)

Available in **all SQL Server 2019 editions including Express**.

Enabled on critical entities requiring full point-in-time history:

### Entities with Temporal Tables

| Table | Schema | History Table | Reason |
|-------|--------|--------------|--------|
| `risks` | `risk` | `risk.risks_history` | Track every risk score change |
| `assessments` | `compliance` | `compliance.assessments_history` | Evidence of assessment state |
| `assessment_controls` | `compliance` | `compliance.assessment_controls_history` | Control compliance changes |
| `controls` | `platform` | `platform.controls_history` | Control effectiveness history |
| `users` | `iam` | `iam.users_history` | Privileged access change history |

### Temporal Table Pattern

```sql
-- Add period columns and enable system versioning
ALTER TABLE risk.risks
ADD
    valid_from DATETIME2(7) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL
        CONSTRAINT DF_risks_valid_from DEFAULT SYSUTCDATETIME(),
    valid_to   DATETIME2(7) GENERATED ALWAYS AS ROW END   HIDDEN NOT NULL
        CONSTRAINT DF_risks_valid_to   DEFAULT CONVERT(DATETIME2, '9999-12-31 23:59:59.9999999'),
    PERIOD FOR SYSTEM_TIME (valid_from, valid_to);

ALTER TABLE risk.risks
SET (SYSTEM_VERSIONING = ON (
    HISTORY_TABLE = risk.risks_history,
    DATA_CONSISTENCY_CHECK = ON
));
```

### Querying History

```sql
-- Current state
SELECT * FROM risk.risks WHERE id = 42;

-- State at a specific point in time
SELECT * FROM risk.risks FOR SYSTEM_TIME AS OF '2025-06-01T10:00:00'
WHERE id = 42;

-- All versions of a record
SELECT * FROM risk.risks FOR SYSTEM_TIME ALL
WHERE id = 42
ORDER BY valid_from;
```

---

## Strategy 3: Optimistic Concurrency (ROWVERSION)

Prevents lost updates when multiple users edit the same record.

```sql
row_version ROWVERSION NOT NULL
```

In JPA:
```java
@Version
@Column(name = "row_version", columnDefinition = "ROWVERSION", insertable = false, updatable = false)
private byte[] rowVersion;
```

When two users submit updates simultaneously:
- First to commit succeeds
- Second receives `OptimisticLockException` → user sees "Record was modified by another user; please refresh and retry"

---

## Retention and Archival

| Table | Retention | Archive Strategy |
|-------|-----------|-----------------|
| `audit.audit_logs` | 7 years | Archive records > 2 years to `audit.audit_logs_archive` |
| Temporal history tables | 5 years | SQL Server manages automatically; prune history via `sp_cleanup_temporal_history` alternative |
| `workflow.workflow_instances` | 3 years | Archive completed instances > 1 year |

Archival process runs via Spring `@Scheduled` job (no SQL Server Agent in Express).

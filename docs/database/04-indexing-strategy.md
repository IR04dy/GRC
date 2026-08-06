# 04 - Indexing Strategy
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> TSS GRC Platform | SQL Server 2019 | Document Version 1.0

## 1. Clustered Index Strategy

Every table has exactly one clustered index. By default, the clustered index is on the primary key (`id BIGINT IDENTITY`).

```sql
-- Default: clustered PK on id
ALTER TABLE risk.risks ADD CONSTRAINT PK_risks
    PRIMARY KEY CLUSTERED (id) ON [FG_DATA];
```

### Exceptions

For the `audit.audit_logs` table (append-heavy, time-series queries), the clustered index is on `created_at` instead of `id`:

```sql
-- Audit logs: clustered on timestamp for range queries
ALTER TABLE audit.audit_logs ADD CONSTRAINT PK_audit_logs
    PRIMARY KEY NONCLUSTERED (id) ON [FG_INDEX];

CREATE CLUSTERED INDEX CIX_audit_logs_created_at
    ON audit.audit_logs (created_at) ON [FG_AUDIT];
```

## 2. Foreign Key Indexes

**Rule:** Every foreign key column gets a non-clustered index. Without these, DELETE and UPDATE on parent tables cause full table scans on child tables.

```sql
-- Template for FK indexes
CREATE NONCLUSTERED INDEX IX_{child_table}_{fk_column}
    ON {schema}.{child_table} ({fk_column})
    ON [FG_INDEX];
```

### Standard FK Indexes (Applied to All Business Entity Tables)

```sql
-- org_id (present on nearly every table)
CREATE NONCLUSTERED INDEX IX_risks_org_id ON risk.risks (org_id) ON [FG_INDEX];
CREATE NONCLUSTERED INDEX IX_controls_org_id ON platform.controls (org_id) ON [FG_INDEX];

-- owner_id / assigned_to
CREATE NONCLUSTERED INDEX IX_risks_owner_id ON risk.risks (owner_id) ON [FG_INDEX];

-- created_by, updated_by
CREATE NONCLUSTERED INDEX IX_risks_created_by ON risk.risks (created_by) ON [FG_INDEX];

-- parent_id (self-referencing hierarchies)
CREATE NONCLUSTERED INDEX IX_business_units_parent_id
    ON platform.business_units (parent_id) ON [FG_INDEX];
```

## 3. Filtered Indexes (Soft-Delete Optimization)

All tables using soft-delete (`is_deleted` column) should have filtered indexes that exclude deleted rows. This dramatically reduces index size and improves query performance for the 99% of queries that only access active records.

```sql
-- Filtered index template
CREATE NONCLUSTERED INDEX IX_risks_org_id_status_filtered
    ON risk.risks (org_id, status)
    WHERE is_deleted = 0
    ON [FG_INDEX];

CREATE NONCLUSTERED INDEX IX_controls_org_id_filtered
    ON platform.controls (org_id, control_code)
    WHERE is_deleted = 0
    ON [FG_INDEX];

CREATE NONCLUSTERED INDEX IX_users_email_filtered
    ON iam.users (email)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

> **Important:** Queries must include `WHERE is_deleted = 0` in the predicate for the optimizer to use filtered indexes. The JPA `@Where` annotation ensures this is always present.

## 4. Composite Indexes for Common Query Patterns

### 4.1 Dashboard Queries (Status + Organization)

```sql
-- Risk dashboard: risks by org and status
CREATE NONCLUSTERED INDEX IX_risks_org_status_level
    ON risk.risks (org_id, status, risk_level)
    INCLUDE (title_en, owner_id, created_at)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Compliance dashboard: assessments by org and status
CREATE NONCLUSTERED INDEX IX_assessments_org_status
    ON compliance.assessments (org_id, status)
    INCLUDE (title_en, framework_id, due_date, completion_percentage)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

### 4.2 Timeline and Date-Range Queries

```sql
-- Risks created in date range
CREATE NONCLUSTERED INDEX IX_risks_org_created_at
    ON risk.risks (org_id, created_at DESC)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Workflow instances by status and due date
CREATE NONCLUSTERED INDEX IX_workflow_instances_status_due
    ON workflow.workflow_instances (status, due_date)
    INCLUDE (entity_type, entity_id, assigned_to)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

### 4.3 Entity Type + Entity ID (Polymorphic References)

```sql
-- Attachments by entity
CREATE NONCLUSTERED INDEX IX_attachments_entity
    ON platform.attachments (entity_type, entity_id)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Notifications by entity
CREATE NONCLUSTERED INDEX IX_notifications_entity
    ON platform.notifications (entity_type, entity_id)
    ON [FG_INDEX];

-- Workflow instances by entity
CREATE NONCLUSTERED INDEX IX_workflow_instances_entity
    ON workflow.workflow_instances (entity_type, entity_id)
    ON [FG_INDEX];
```

### 4.4 Module-Specific Composite Indexes

```sql
-- Cyber: assets by org and asset type
CREATE NONCLUSTERED INDEX IX_cyber_assets_org_type
    ON cyber.cyber_assets (org_id, asset_type)
    INCLUDE (name_en, criticality, status)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- BCM: BIA by org and business unit
CREATE NONCLUSTERED INDEX IX_bias_org_bu
    ON bcm.business_impact_analyses (org_id, business_unit_id)
    INCLUDE (status, rto_hours, rpo_hours)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Privacy: processing activities by org and legal basis
CREATE NONCLUSTERED INDEX IX_processing_activities_org_basis
    ON privacy.processing_activities (org_id, legal_basis)
    INCLUDE (status, data_controller_id)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

## 5. Audit Log Indexes

The `audit.audit_logs` table is the highest-volume table and requires careful indexing.

```sql
-- Primary query: "what changed on entity X?"
CREATE NONCLUSTERED INDEX IX_audit_logs_entity
    ON audit.audit_logs (entity_type, entity_id, created_at DESC)
    INCLUDE (action, user_id)
    ON [FG_INDEX];

-- "What did user X do?"
CREATE NONCLUSTERED INDEX IX_audit_logs_user
    ON audit.audit_logs (user_id, created_at DESC)
    INCLUDE (entity_type, entity_id, action)
    ON [FG_INDEX];

-- Time-range scan (already optimized by clustered index on created_at)
-- Additional index for org-scoped time queries
CREATE NONCLUSTERED INDEX IX_audit_logs_org_time
    ON audit.audit_logs (org_id, created_at DESC)
    ON [FG_INDEX];
```

## 6. Unique Indexes

```sql
-- Users: unique email (active users only)
CREATE UNIQUE NONCLUSTERED INDEX UX_users_email
    ON iam.users (email)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Users: unique external_id
CREATE UNIQUE NONCLUSTERED INDEX UX_users_external_id
    ON iam.users (external_id)
    ON [FG_INDEX];

-- Organizations: unique code
CREATE UNIQUE NONCLUSTERED INDEX UX_organizations_code
    ON platform.organizations (code)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Controls: unique code within org
CREATE UNIQUE NONCLUSTERED INDEX UX_controls_org_code
    ON platform.controls (org_id, control_code)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Lookup values: unique code within category
CREATE UNIQUE NONCLUSTERED INDEX UX_lookup_values_category_code
    ON platform.lookup_values (category_id, code)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Roles: unique code within org
CREATE UNIQUE NONCLUSTERED INDEX UX_roles_org_code
    ON iam.roles (org_id, code)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

## 7. Full-Text Index Readiness

Full-text indexes are created on title and description columns for search functionality.

```sql
-- Create full-text catalog
CREATE FULLTEXT CATALOG FTC_TSS_GRC AS DEFAULT;

-- Risk search
CREATE FULLTEXT INDEX ON risk.risks (title_en, title_ar, description_en, description_ar)
    KEY INDEX PK_risks
    ON FTC_TSS_GRC
    WITH (CHANGE_TRACKING = AUTO);

-- Control search
CREATE FULLTEXT INDEX ON platform.controls (control_code, name_en, name_ar, description_en, description_ar)
    KEY INDEX PK_controls
    ON FTC_TSS_GRC
    WITH (CHANGE_TRACKING = AUTO);
```

> **Arabic full-text search:** SQL Server includes an Arabic word breaker. Ensure the Arabic language (langid 1025) is installed for proper tokenization.

## 8. Columnstore Index Readiness

For reporting/analytics tables that accumulate large volumes, consider nonclustered columnstore indexes:

```sql
-- Reporting: execution results (high-volume, analytical queries)
CREATE NONCLUSTERED COLUMNSTORE INDEX CSX_report_executions
    ON reporting.report_executions (
        report_definition_id, org_id, status,
        started_at, completed_at, row_count
    );

-- Audit: for analytical queries across large audit log
CREATE NONCLUSTERED COLUMNSTORE INDEX CSX_audit_logs
    ON audit.audit_logs (
        entity_type, action, org_id, user_id, created_at
    );
```

> **Note:** Columnstore indexes are added when table volume exceeds ~1M rows and analytical query patterns emerge. Do not add preemptively on low-volume tables.

## 9. Index Maintenance Plan

### Automated Maintenance Schedule

| Task | Frequency | Condition | Action |
|------|-----------|-----------|--------|
| Index fragmentation check | Daily at 02:00 | Fragmentation > 30% | `ALTER INDEX ... REBUILD` (online) |
| Index fragmentation check | Daily at 02:00 | Fragmentation 10-30% | `ALTER INDEX ... REORGANIZE` |
| Statistics update | Daily at 03:00 | After index maintenance | `UPDATE STATISTICS ... WITH FULLSCAN` |
| Statistics update | On-demand | After bulk data load | `UPDATE STATISTICS {table} WITH FULLSCAN` |
| Unused index review | Monthly | `sys.dm_db_index_usage_stats` reads = 0 for 30+ days | Evaluate for removal |

### Online Index Operations

SQL Server 2019 supports `ONLINE = ON` and `RESUMABLE = ON` for index rebuilds:

```sql
ALTER INDEX IX_risks_org_id_status_filtered ON risk.risks
    REBUILD WITH (ONLINE = ON, RESUMABLE = ON, MAX_DURATION = 60);
```

### Statistics Configuration

```sql
-- Auto-update statistics is ON by default; verify
ALTER DATABASE TSS_GRC SET AUTO_UPDATE_STATISTICS ON;
ALTER DATABASE TSS_GRC SET AUTO_CREATE_STATISTICS ON;
ALTER DATABASE TSS_GRC SET AUTO_UPDATE_STATISTICS_ASYNC ON; -- reduces query latency
```

## 10. Index Sizing Guidelines

| Table Category | Expected Rows | Index Strategy |
|---------------|---------------|----------------|
| Reference data (lookups, frameworks) | < 10K | Minimal indexes; FK and unique only |
| Business entities (risks, controls) | 10K - 100K | Filtered indexes, composite for dashboards |
| Transactional (workflow instances, notifications) | 100K - 1M | Composite indexes, covering for frequent queries |
| High-volume (audit logs) | 1M+ | Partitioned, columnstore, aggressive covering indexes |

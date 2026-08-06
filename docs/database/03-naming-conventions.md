# 03 - Naming Conventions
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> TSS GRC Platform | SQL Server 2019 | Document Version 1.0

## 1. General Rules

- All database object names use **lowercase_snake_case**.
- Names must be descriptive and unambiguous.
- Avoid abbreviations except for widely understood terms (e.g., `org` for organization, `bu` for business unit, `id` for identifier).
- Maximum identifier length: 128 characters (SQL Server limit), but aim for under 60.
- Reserved words must never be used as identifiers (e.g., do not name a column `user`, `order`, `index`).

## 2. Table Naming

| Rule | Convention | Example |
|------|-----------|---------|
| Case | lowercase_snake_case | `risk_assessments` |
| Plurality | Plural nouns | `users`, `risks`, `controls` |
| Schema prefix | Schema provides the namespace; no prefix in table name | `risk.risks` not `risk.risk_risks` |
| Junction tables | `{entity1}_{entity2}` alphabetical order or semantic order | `user_roles`, `control_framework_mappings` |
| History tables | `{table}_history` | `risk.risks_history` |
| Archive tables | `{table}_archive` | `audit.audit_logs_archive` |

### Examples

```
platform.organizations
platform.business_units
platform.controls
platform.control_frameworks
platform.lookup_categories
platform.lookup_values
iam.users
iam.roles
iam.permissions
iam.user_roles
iam.role_permissions
risk.risks
risk.risk_assessments
audit.audit_logs
```

## 3. Column Naming

| Rule | Convention | Example |
|------|-----------|---------|
| Case | lowercase_snake_case | `first_name_en`, `created_at` |
| Primary key | Always `id` | `id BIGINT IDENTITY(1,1)` |
| Foreign key column | `{referenced_entity_singular}_id` | `org_id`, `user_id`, `risk_id` |
| Boolean columns | `is_` prefix | `is_active`, `is_deleted`, `is_locked` |
| Timestamp columns | `_at` suffix | `created_at`, `updated_at`, `deleted_at` |
| Date-only columns | `_date` suffix | `target_date`, `review_date`, `due_date` |
| Bilingual text | `{field}_en`, `{field}_ar` | `name_en`, `name_ar`, `description_en`, `description_ar` |
| Count/quantity | `{noun}_count` or descriptive | `attempt_count`, `total_score` |
| Amount/money | descriptive with context | `estimated_loss_amount`, `budget_amount` |

### Standard Audit Columns

Every business entity table includes these columns in this exact order at the end of the column list:

```sql
created_by    BIGINT        NOT NULL,   -- FK to iam.users.id
created_at    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
updated_by    BIGINT        NULL,       -- FK to iam.users.id
updated_at    DATETIME2(7)  NULL,
is_deleted    BIT           NOT NULL DEFAULT 0,
deleted_by    BIGINT        NULL,       -- FK to iam.users.id
deleted_at    DATETIME2(7)  NULL,
row_version   ROWVERSION    NOT NULL    -- optimistic concurrency
```

### External Identifier Column

Tables exposed via API include an external identifier for safe public references:

```sql
external_id   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID()  -- used in API URLs instead of sequential id
```

## 4. Constraint Naming

| Object Type | Pattern | Example |
|-------------|---------|---------|
| Primary key | `PK_{table}` | `PK_risks` |
| Foreign key | `FK_{source_table}_{target_table}` | `FK_risks_organizations` |
| Foreign key (disambiguation) | `FK_{source_table}_{column}_{target_table}` | `FK_risks_owner_id_users` |
| Unique constraint | `UQ_{table}_{columns}` | `UQ_users_email` |
| Check constraint | `CK_{table}_{column}` | `CK_risks_risk_level` |
| Default constraint | `DF_{table}_{column}` | `DF_risks_is_deleted` |

### Foreign Key Examples

```sql
-- Simple: one FK to target table
ALTER TABLE risk.risks ADD CONSTRAINT FK_risks_organizations
    FOREIGN KEY (org_id) REFERENCES platform.organizations(id);

-- Disambiguated: multiple FKs to same target table
ALTER TABLE risk.risks ADD CONSTRAINT FK_risks_owner_id_users
    FOREIGN KEY (owner_id) REFERENCES iam.users(id);

ALTER TABLE risk.risks ADD CONSTRAINT FK_risks_created_by_users
    FOREIGN KEY (created_by) REFERENCES iam.users(id);
```

## 5. Index Naming

| Index Type | Pattern | Example |
|------------|---------|---------|
| Clustered (PK) | `PK_{table}` (auto-created with PK) | `PK_risks` |
| Clustered (non-PK) | `CIX_{table}_{columns}` | `CIX_audit_logs_timestamp` |
| Non-clustered | `IX_{table}_{columns}` | `IX_risks_org_id_status` |
| Unique non-clustered | `UX_{table}_{columns}` | `UX_users_external_id` |
| Filtered | `IX_{table}_{columns}_filtered` | `IX_risks_org_id_status_filtered` |
| Full-text | `FTX_{table}` | `FTX_risks` |
| Columnstore | `CSX_{table}` | `CSX_audit_logs` |

### Index Examples

```sql
-- Non-clustered on FK column
CREATE NONCLUSTERED INDEX IX_risks_org_id
    ON risk.risks (org_id)
    ON [FG_INDEX];

-- Composite with filter
CREATE NONCLUSTERED INDEX IX_risks_org_id_status_filtered
    ON risk.risks (org_id, status)
    WHERE is_deleted = 0
    ON [FG_INDEX];

-- Covering index for dashboard query
CREATE NONCLUSTERED INDEX IX_risks_org_id_status_level
    ON risk.risks (org_id, status, risk_level)
    INCLUDE (title_en, owner_id, created_at)
    WHERE is_deleted = 0
    ON [FG_INDEX];
```

## 6. View Naming

| Object Type | Pattern | Example |
|-------------|---------|---------|
| Standard view | `vw_{description}` | `vw_unified_risk_summary` |
| Indexed view | `vw_{description}` (same naming, indexed via `CREATE UNIQUE CLUSTERED INDEX`) | `vw_compliance_status` |

## 7. Stored Procedure and Function Naming

| Object Type | Pattern | Example |
|-------------|---------|---------|
| Stored procedure | `usp_{schema}_{action}` | `usp_audit_insert_log` |
| Table-valued function | `fn_{schema}_{description}` | `fn_platform_org_filter` |
| Scalar function | `fn_{schema}_{description}` | `fn_iam_has_permission` |

> **Note:** The application primarily uses JPA/Hibernate for data access. Stored procedures are used sparingly for: RLS filter functions, complex reporting queries, and bulk data operations.

## 8. Schema-Qualified References

All object references in SQL code must be schema-qualified:

```sql
-- CORRECT
SELECT * FROM risk.risks WHERE org_id = 1;
SELECT * FROM platform.organizations WHERE id = 1;

-- INCORRECT (relies on default schema resolution)
SELECT * FROM risks WHERE org_id = 1;
```

## 9. Bilingual Column Pairs

For every text field that requires bilingual support, create a paired set:

```sql
-- Master data example
name_en         NVARCHAR(200)   NOT NULL,
name_ar         NVARCHAR(200)   NULL,       -- Arabic may be populated later
description_en  NVARCHAR(2000)  NULL,
description_ar  NVARCHAR(2000)  NULL,
```

Rules:
- `_en` column is always `NOT NULL` for required fields (English is the default language).
- `_ar` column is `NULL` (Arabic translation may not be available immediately).
- Both columns use `NVARCHAR` for Unicode support.
- Application layer selects the appropriate column based on user locale.

## 10. Enumerated Status Values

Status fields use `NVARCHAR` with check constraints rather than integer codes, for readability:

```sql
status NVARCHAR(30) NOT NULL DEFAULT 'DRAFT',
CONSTRAINT CK_risks_status CHECK (status IN (
    'DRAFT', 'UNDER_REVIEW', 'APPROVED', 'ACTIVE',
    'MITIGATED', 'ACCEPTED', 'CLOSED', 'ARCHIVED'
))
```

Alternatively, for statuses shared across modules, reference `platform.lookup_values.code` and enforce via FK.

## 11. JSON Column Naming

Columns storing JSON data use a `_json` suffix and `NVARCHAR(MAX)` type:

```sql
metadata_json       NVARCHAR(MAX)   NULL,   -- flexible key-value pairs
old_values_json     NVARCHAR(MAX)   NULL,   -- audit: previous state
new_values_json     NVARCHAR(MAX)   NULL,   -- audit: new state
configuration_json  NVARCHAR(MAX)   NULL,   -- dynamic configuration
```

> SQL Server 2019 supports `ISJSON()`, `JSON_VALUE()`, `JSON_QUERY()`, and `OPENJSON()` for querying JSON columns.

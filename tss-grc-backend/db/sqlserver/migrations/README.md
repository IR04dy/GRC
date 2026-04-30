# Database Migration Strategy — Flyway for SQL Server 2019

## Overview

This directory contains Flyway migration scripts for the TSS GRC Platform database on SQL Server 2019 (Express Edition compatible).

---

## Migration File Organization

```
db/migration/sqlserver/
├── V1.0.0__create_database_settings.sql      ← Collation, settings
├── V1.0.1__create_schemas.sql                ← All schemas
├── V1.0.2__create_platform_tables.sql        ← platform schema tables
├── V1.0.3__create_iam_tables.sql             ← iam schema tables
├── V1.0.4__create_admin_tables.sql           ← admin schema tables
├── V1.0.5__create_workflow_tables.sql        ← workflow schema tables
├── V1.0.6__create_risk_tables.sql            ← risk schema tables
├── V1.0.7__create_cyber_tables.sql           ← cyber schema tables
├── V1.0.8__create_bcm_tables.sql             ← bcm schema tables
├── V1.0.9__create_privacy_tables.sql         ← privacy schema tables
├── V1.0.10__create_compliance_tables.sql     ← compliance schema tables
├── V1.0.11__create_reporting_tables.sql      ← reporting schema tables
├── V1.0.12__create_audit_tables.sql          ← audit schema tables
├── V1.0.13__create_shared_indexes.sql        ← Shared table indexes
├── V1.0.14__create_module_indexes.sql        ← Module table indexes
├── V1.0.15__enable_temporal_tables.sql       ← Temporal table setup
├── R__vw_unified_risk_summary.sql            ← Repeatable: reporting views
├── R__vw_compliance_status.sql
├── R__vw_active_controls.sql
├── R__vw_incident_summary.sql
├── R__seed_modules.sql                       ← Repeatable: module seed data
├── R__seed_lookup_categories.sql
├── R__seed_lookup_values.sql
├── R__seed_control_frameworks.sql
├── R__seed_nca_ecc_controls.sql
├── R__seed_iso27001_controls.sql
├── R__seed_permissions.sql
├── R__seed_default_roles.sql
└── R__seed_default_workflows.sql
```

---

## Naming Conventions

| Pattern | Example | Description |
|---------|---------|-------------|
| `V{maj}.{min}.{patch}__{desc}.sql` | `V1.0.5__create_workflow_tables.sql` | Versioned migration — runs once |
| `R__{desc}.sql` | `R__seed_permissions.sql` | Repeatable — re-runs when checksum changes |
| `U{version}__{desc}.sql` | Not used (Community Edition) | Undo migrations — Pro only |

**Rules:**
- Double underscore `__` separates version/prefix from description
- Description uses snake_case
- Version numbers align with application release versions (1.0.0 = Phase 2 initial release)

---

## Spring Boot Configuration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration/sqlserver
    baseline-on-migrate: false
    out-of-order: false
    validate-on-migrate: true
    table: flyway_schema_history
    encoding: UTF-8
    sql-migration-prefix: V
    repeatable-sql-migration-prefix: R
    sql-migration-separator: __
    connect-retries: 3
    # NEVER enable clean in production:
    clean-disabled: true
```

---

## Production Deployment Procedure

1. **Before every deployment:**
   - Take full database backup: `BACKUP DATABASE TSS_GRC TO DISK = '...\TSS_GRC_pre_deploy.bak'`
   - Store backup in a safe location external to the server

2. **Verify migration scripts in staging** before running in production

3. **Application startup runs Flyway automatically** — migrations execute in version order

4. **Verify success** via Flyway schema history:
   ```sql
   SELECT * FROM dbo.flyway_schema_history ORDER BY installed_rank;
   ```

5. **On failure:**
   - Flyway marks the failed migration with `success = 0`
   - Restore database from pre-deployment backup
   - Fix the migration script
   - Redeploy

---

## Rollback Strategy

Flyway Community Edition does not support automatic rollback. The strategy is:

1. **Database backup** before every migration run (non-negotiable in production)
2. **Forward-only fix migrations** for bugs: `V1.0.x__fix_{description}.sql`
3. **Emergency rollback**: Restore from backup (documented procedure in DR runbook)

### Example Fix Migration

If `V1.0.5` has a column type error:
```
V1.0.6__fix_workflow_step_sla_hours_type.sql
```
Never modify `V1.0.5` — always add a new `V1.0.6` fix.

---

## Seed Data Strategy

Seed data uses **Repeatable migrations** (R__ prefix) so they re-run whenever the seed data changes:

```sql
-- R__seed_lookup_values.sql
-- This file re-runs on every deployment if the file content changes

MERGE platform.lookup_categories AS target
USING (VALUES
    ('RISK_LEVEL', N'Risk Level', N'مستوى المخاطر', 1),
    ('RISK_STATUS', N'Risk Status', N'حالة المخاطر', 1)
) AS source (code, name_en, name_ar, is_system)
ON target.code = source.code
WHEN MATCHED THEN
    UPDATE SET name_en = source.name_en, name_ar = source.name_ar
WHEN NOT MATCHED THEN
    INSERT (code, name_en, name_ar, is_system)
    VALUES (source.code, source.name_en, source.name_ar, source.is_system);
GO
```

Always use MERGE (upsert) in seed scripts to make them idempotent.

---

## Express Edition Notes

- SQL Server Express supports all Flyway operations (DDL, DML, SELECT)
- The 10 GB database size limit means migration scripts should be mindful of large seed data
- SQL Server Agent is not available — Flyway runs via application startup only
- No need for scheduled migration runs in Express; always startup-triggered

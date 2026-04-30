# Database Migration and Versioning Strategy

## Purpose
Defines the Flyway-based database migration strategy for the TSS GRC Platform, ensuring all schema changes are version-controlled, repeatable, and environment-safe.

---

## Tool Decision: Flyway

**Decision: Flyway** (Community Edition)

| Criterion | Flyway | Liquibase |
|-----------|--------|-----------|
| SQL Server support | ✅ Native | ✅ Native |
| Spring Boot integration | ✅ `spring.flyway.*` | ✅ `spring.liquibase.*` |
| SQL files | ✅ Native T-SQL | ✅ but requires XML/YAML wrapper |
| Learning curve | Low | Medium |
| Community Edition | ✅ Free | ✅ Free |
| Undo migrations | ❌ (Pro only) | ✅ Available |
| Forward-only safety | ✅ Encouraged | Configurable |

**Rationale:** Flyway is simpler, uses native T-SQL directly, and integrates cleanly with Spring Boot. The forward-only migration pattern is appropriate for enterprise GRC systems where rollback is a deployment decision, not an automatic database operation.

---

## Migration File Naming Convention

```
V{version}__{description}.sql
```

| Pattern | Example | Use |
|---------|---------|-----|
| Versioned | `V1.0.0__create_schemas.sql` | Schema changes (run once) |
| Versioned | `V1.0.1__create_platform_tables.sql` | Table creation |
| Versioned | `V1.1.0__add_audit_indexes.sql` | Index additions |
| Repeatable | `R__vw_unified_risk_summary.sql` | Views (re-run on change) |
| Repeatable | `R__seed_lookup_values.sql` | Reference data (re-run on change) |
| Repeatable | `R__seed_permissions.sql` | Permission seed data |

**Versioning aligned with application release:** `V{major}.{minor}.{patch}__description.sql`

---

## Folder Structure

```
db/migration/
    sqlserver/
        V1.0.0__create_database_settings.sql
        V1.0.1__create_schemas.sql
        V1.0.2__create_platform_tables.sql
        V1.0.3__create_iam_tables.sql
        V1.0.4__create_admin_tables.sql
        V1.0.5__create_workflow_tables.sql
        V1.0.6__create_risk_tables.sql
        V1.0.7__create_cyber_tables.sql
        V1.0.8__create_bcm_tables.sql
        V1.0.9__create_privacy_tables.sql
        V1.0.10__create_compliance_tables.sql
        V1.0.11__create_reporting_tables.sql
        V1.0.12__create_audit_tables.sql
        V1.0.13__create_indexes.sql
        V1.0.14__enable_temporal_tables.sql
        R__vw_unified_risk_summary.sql
        R__vw_compliance_status.sql
        R__vw_active_controls.sql
        R__vw_incident_summary.sql
        R__seed_modules.sql
        R__seed_lookup_categories.sql
        R__seed_lookup_values.sql
        R__seed_control_frameworks.sql
        R__seed_permissions.sql
        R__seed_default_roles.sql
        R__seed_default_workflows.sql
```

---

## Spring Boot Configuration

```yaml
spring:
  flyway:
    enabled: true
    url: ${spring.datasource.url}
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    locations: classpath:db/migration/sqlserver
    schemas: platform,iam,admin,workflow,risk,cyber,bcm,privacy,compliance,reporting,audit
    baseline-on-migrate: false   # Set to true only for first migration on existing DB
    out-of-order: false          # Enforce sequential migration order
    validate-on-migrate: true    # Verify checksum of applied migrations
    table: flyway_schema_history
    sql-migration-prefix: V
    repeatable-sql-migration-prefix: R
    sql-migration-separator: __
    encoding: UTF-8
    connect-retries: 3
```

---

## Environment Handling

| Environment | Strategy |
|-------------|---------|
| Development | Flyway runs on startup; clean database option available |
| Testing | Flyway runs on startup against test database |
| Staging | Flyway runs on startup; reviewed before production |
| Production | Flyway runs on startup; backup taken BEFORE migration run |

### Environment-Specific Config

```yaml
# application-prod.yml
spring:
  flyway:
    clean-disabled: true   # NEVER allow clean in production
    out-of-order: false
    baseline-on-migrate: false
```

---

## Migration Best Practices

1. **Never modify an applied migration** — only add new migrations
2. **Each migration in a transaction** — wrap DDL in BEGIN TRAN / COMMIT where possible (some DDL auto-commits)
3. **Idempotent repeatable migrations** — use `CREATE OR ALTER VIEW`, `IF NOT EXISTS` checks
4. **Reference data in Repeatable migrations** — allows updates without versioning overhead
5. **Test migrations in staging first** — never run untested migrations in production
6. **Take backup before production migration** — documented in deployment runbook
7. **Migration descriptions in English** — e.g., `V1.1.0__add_org_branding_columns.sql`

---

## Rollback Strategy

Flyway Community Edition does not support automatic rollback. The strategy is:

1. **Database backup** taken before every migration run (automated via Spring `@EventListener(ApplicationReadyEvent)` or deployment script)
2. **Forward-only fix migrations** — if a migration has a bug, write `V1.x.x__fix_{description}.sql`
3. **Emergency rollback**: Restore from backup (documented in DR runbook)
4. **Blue-Green deployment** (future): Deploy new version alongside old; cut traffic only after migration verified

---

## Flyway Schema History Table

Flyway stores migration history in `dbo.flyway_schema_history`:
- `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`
- This table must NOT be modified manually

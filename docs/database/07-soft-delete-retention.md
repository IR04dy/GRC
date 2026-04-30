# Soft Delete and Data Retention Strategy

## Purpose
Defines the soft delete pattern, retention policies, and purge strategy for the TSS GRC Platform database.

---

## Soft Delete Pattern

All business entity tables implement soft delete via three columns:

```sql
is_deleted  BIT           NOT NULL DEFAULT 0,
deleted_at  DATETIME2(7)  NULL,
deleted_by  BIGINT        NULL  -- FK to iam.users(id) — not enforced as FK to allow user deletion
```

### Rationale
- **Referential integrity**: Soft-deleted records can still be referenced by FKs (e.g., an audit log can reference a deleted user)
- **Recoverability**: Admin can restore accidentally deleted records within retention period
- **Compliance**: Records required for audit/evidence cannot be permanently deleted during retention period
- **PDPL**: Personal data may require hard delete (anonymization) after retention; soft delete provides controlled lifecycle

---

## Application Enforcement

### JPA / Hibernate Filter (Recommended)

```java
@Entity
@Table(name = "risks", schema = "risk")
@FilterDef(name = "activeRecordsFilter",
           parameters = @ParamDef(name = "isDeleted", type = Boolean.class))
@Filter(name = "activeRecordsFilter", condition = "is_deleted = :isDeleted")
public class Risk extends BaseEntity {
    // ...
}
```

Enabled globally:
```java
// In repository or service layer
session.enableFilter("activeRecordsFilter").setParameter("isDeleted", false);
```

### Alternative: Spring Data JPA @Where Annotation

```java
@Where(clause = "is_deleted = 0")
public class Risk extends BaseEntity { }
```

All queries automatically append `WHERE is_deleted = 0`.

### Admin Override

Admin users can query soft-deleted records via a flag in the request context that disables the filter.

---

## Filtered Indexes

All primary query patterns include `WHERE is_deleted = 0` in non-clustered indexes:

```sql
CREATE INDEX IX_risks_org_status_active
ON risk.risks (org_id, status, risk_level)
WHERE is_deleted = 0;
```

This reduces index size and improves query performance.

---

## Soft Delete vs Hard Delete

| Scenario | Action | Reason |
|---------|--------|--------|
| User deletes a risk record | Soft delete | Audit integrity; may be referenced |
| Admin permanently purges expired data | Hard delete via archival job | Post-retention cleanup |
| PDPL erasure request for personal data | Field-level anonymization (not delete) | Record structure kept; personal values blanked |
| Test/demo data cleanup | Hard delete (DEV only) | Controlled environment only |

---

## Retention Policies

| Entity Type | Default Retention | After Retention |
|-------------|------------------|----------------|
| Risks | 5 years after closure | Archive, then purge |
| Compliance assessments | 5 years after completion | Archive |
| Audit logs | 7 years | Archive to cold storage |
| Incidents | 5 years after closure | Archive |
| Privacy breach records | Per PDPL (minimum 5 years) | Review before purge |
| User accounts | 2 years after deactivation | Anonymize, then purge |
| Attachments | Same as linked entity | Delete file + metadata |
| Workflow instances | 3 years after completion | Archive |

Policies are configurable per organization in `admin.org_configurations`.

---

## Purge Process

Runs as scheduled Spring `@Scheduled` job (quarterly or as configured):

1. Find records with `is_deleted = 1` AND `deleted_at < NOW() - retention_period`
2. Verify no active FK references from non-deleted records
3. Copy to archive table (same schema, `_archive` suffix) if archive mode enabled
4. Hard delete from main table
5. Delete associated file storage (via FileStorageService)
6. Log purge operation to audit log

---

## Personal Data Anonymization (PDPL)

For right-to-erasure requests, personal data is anonymized rather than deleted:

```sql
UPDATE iam.users
SET
    first_name_en  = 'ANONYMIZED',
    last_name_en   = 'ANONYMIZED',
    first_name_ar  = N'محذوف',
    last_name_ar   = N'محذوف',
    email          = CONCAT('anon_', id, '@deleted.tss'),
    phone          = NULL,
    mobile         = NULL,
    password_hash  = 'ANONYMIZED',
    is_deleted     = 1,
    deleted_at     = SYSUTCDATETIME(),
    anonymized_at  = SYSUTCDATETIME()
WHERE id = @userId;
```

Audit records referencing the user retain the user_id (for integrity) but the user's personal data is gone. The username is replaced with a non-identifying token.

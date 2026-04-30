# Audit and Evidence Architecture

## Purpose
Defines the audit trail, evidence management, and tamper-resistant logging architecture for the TSS GRC Platform, supporting regulatory compliance and accountability requirements.

---

## Audit Trail Architecture

### Two-Layer Audit Strategy

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| **Application Audit Log** | `audit.audit_logs` table | All user actions, business events, login/logout, exports |
| **Database Temporal Tables** | SQL Server System-Versioned Tables | Point-in-time data reconstruction for critical entities |

### Application Audit Log

Every state-changing operation records an entry in `audit.audit_logs`:

| Field | Description |
|-------|-------------|
| `event_timestamp` | UTC timestamp of the action |
| `user_id` / `username` | Who performed the action |
| `session_id` | Session identifier |
| `org_id` | Organization context |
| `module_code` | Which module (risk, cyber, etc.) |
| `entity_type` | Fully qualified entity name (e.g., `risk.risks`) |
| `entity_id` | Primary key of the affected record |
| `action` | CREATE, UPDATE, DELETE, APPROVE, REJECT, LOGIN, LOGOUT, EXPORT |
| `old_values` | JSON snapshot of values before change |
| `new_values` | JSON snapshot of values after change |
| `ip_address` | Client IP address |
| `user_agent` | Browser/client identifier |
| `correlation_id` | Request trace ID for log correlation |

### Implementation

```java
@Aspect
@Component
public class AuditAspect {

    @AfterReturning(
        pointcut = "@annotation(Auditable)",
        returning = "result"
    )
    public void logAudit(JoinPoint joinPoint, Object result) {
        AuditLog log = buildAuditLog(joinPoint, result);
        auditLogRepository.save(log);
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String entityType();
}
```

Plus JPA Entity Listeners on BaseEntity subclasses for automatic create/update/delete capture.

---

## Tamper Resistance

The application database user has **INSERT-only access** on `audit.audit_logs` — no UPDATE or DELETE permissions. This is enforced at the database level:

```sql
GRANT INSERT ON audit.audit_logs TO grc_app_user;
-- No UPDATE or DELETE granted
```

For compliance with regulations requiring tamper-evident logs, additional measures:
- Regular audit log export to immutable external storage (daily archive)
- Log file checksum verification
- Audit log access itself is logged (a read of audit logs creates an audit entry)

---

## SQL Server Temporal Tables

System-versioned temporal tables are enabled on critical entities:

```sql
ALTER TABLE risk.risks
ADD
    valid_from DATETIME2 GENERATED ALWAYS AS ROW START NOT NULL,
    valid_to   DATETIME2 GENERATED ALWAYS AS ROW END   NOT NULL,
    PERIOD FOR SYSTEM_TIME (valid_from, valid_to);

ALTER TABLE risk.risks
SET (SYSTEM_VERSIONING = ON (HISTORY_TABLE = risk.risks_history));
```

**Tables with temporal tables enabled:**
- `risk.risks`
- `compliance.assessments`
- `platform.controls`
- `iam.users`
- `compliance.assessment_controls`

This enables queries like:
```sql
-- What did this risk look like on a specific date?
SELECT * FROM risk.risks FOR SYSTEM_TIME AS OF '2025-01-01'
WHERE id = 42;
```

---

## Evidence Management

Evidence is stored as file attachments linked to entities via `platform.attachments`.

### Evidence Chain

```
compliance.assessment_controls (control assessment)
    → platform.attachments (evidence file)
compliance.evidence_items (formal evidence record)
    → platform.attachments (evidence document)
workflow.workflow_instance_steps (approval decision)
    → platform.attachments (supporting document)
```

### Evidence Lifecycle

1. **Submitted**: User uploads file and creates evidence record
2. **Pending Review**: Evidence awaits assessor review
3. **Accepted**: Assessor marks evidence as sufficient
4. **Rejected**: Assessor rejects with comments; user resubmits
5. **Archived**: Assessment closed; evidence retained per retention policy

### File Storage

- Files stored in configured storage location (local filesystem for Phase 1)
- File name: UUID-based (prevents predictable URLs)
- Metadata in `platform.attachments`: original name, MIME type, size, checksum
- Virus scan status tracked (integration point for antivirus; defaults to PENDING)
- Files not directly served from web root — streamed through controller with authorization check

---

## Retention Policy

| Entity Type | Default Retention | Regulatory Basis |
|-------------|------------------|-----------------|
| Audit logs | 7 years | Saudi NCA / general compliance |
| Compliance evidence | 5 years after assessment | ISO 27001 |
| Risk records | 5 years after closure | ISO 31000 |
| Privacy/PDPL records | Per PDPL requirements | Saudi PDPL |
| Workflow history | 3 years | Internal governance |

Retention is configurable per org via `admin.org_configurations`.

---

## Audit Reporting

| Report | Description |
|--------|-------------|
| User Activity Report | All actions by a specific user in a date range |
| Entity History Report | All changes to a specific record |
| Login/Logout Report | Authentication events |
| Admin Action Report | Privileged actions (user creation, role changes, config changes) |
| Export Activity Report | All data exports with user, timestamp, entity |

All audit reports are exportable to PDF and Excel.

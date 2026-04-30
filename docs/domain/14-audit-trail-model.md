# 14 - Audit Trail & Evidence Chain Domain Model

## Overview

The Audit Trail model provides a tamper-evident, INSERT-only log of all state-changing events across the platform. It is a cross-cutting concern implemented via JPA entity listeners and AOP, not a module. Evidence items from the Compliance module complete the audit evidence chain from log entry through to supporting documentation.

---

## 1. Core Entity: `audit.audit_logs`

Every create, update, delete, state-change, login, and export event is recorded here. **No rows are ever updated or deleted** — the table is append-only by application convention and enforced by a database trigger.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK (sequential, used for hash chaining) |
| `organisation_id` | BIGINT | Tenant discriminator |
| `event_type` | NVARCHAR(50) | CREATE / UPDATE / DELETE / STATE_CHANGE / LOGIN / LOGOUT / EXPORT / ACCESS_DENIED |
| `entity_type` | NVARCHAR(100) | e.g. `risk.risks`, `compliance.compliance_findings`, `iam.users` |
| `entity_id` | BIGINT | Internal ID of the affected entity |
| `entity_external_id` | UNIQUEIDENTIFIER | External ID (for API traceability) |
| `user_id` | BIGINT | FK → `iam.users` (actor; NULL for system events) |
| `username` | NVARCHAR(100) | Denormalised username (preserved even if user is deleted) |
| `ip_address` | NVARCHAR(45) | Client IP address |
| `user_agent` | NVARCHAR(500) | Browser/client user agent string |
| `before_value_json` | NVARCHAR(MAX) | JSON snapshot of entity state before change (NULL for CREATE) |
| `after_value_json` | NVARCHAR(MAX) | JSON snapshot of entity state after change (NULL for DELETE) |
| `change_summary_en` | NVARCHAR(500) | Human-readable summary (e.g. "Risk score changed from 12 to 20") |
| `event_timestamp` | DATETIME2(7) | UTC timestamp |
| `session_id` | NVARCHAR(100) | JWT `jti` claim (token ID) for session traceability |
| `chain_hash` | NVARCHAR(64) | SHA-256(previous_chain_hash + this_row_content) — tamper detection |
| `module` | NVARCHAR(30) | Source module: RISK / CYBER / BCM / PRIVACY / COMPLIANCE / ADMIN / IAM |

### Tamper-Evident Hash Chain

Each row's `chain_hash` is computed as:

```
chain_hash[n] = SHA-256(chain_hash[n-1] + id + event_type + entity_type + entity_id + user_id + event_timestamp + after_value_json)
```

The Admin audit log viewer can verify the chain integrity on demand:
`GET /api/v1/admin/audit/verify-chain?from=2025-01-01&to=2025-12-31`

---

## 2. Archival Strategy

SQL Server Express Edition does not support table partitioning. Audit logs older than **12 months** are moved to `audit.archived_audit_logs` (identical schema) by a monthly `@Scheduled` job:

```java
@Scheduled(cron = "0 0 2 1 * ?")  // 2am on the 1st of every month
public void archiveOldAuditLogs() {
    // INSERT INTO audit.archived_audit_logs SELECT ... WHERE event_timestamp < DATEADD(MONTH,-12,GETUTCDATE())
    // DELETE FROM audit.audit_logs WHERE event_timestamp < DATEADD(MONTH,-12,GETUTCDATE())
}
```

This keeps the live `audit_logs` table lean (< 1 year of data) while retaining full history in the archive table within the same 10 GB Express limit.

---

## 3. Temporal Tables (Point-in-Time History)

SQL Server Temporal Tables provide automatic versioning for the four highest-value entities:

| Table | Temporal History Table | Use Case |
|---|---|---|
| `risk.risks` | `risk.risks_history` | "What was this risk's score on 1 Jan 2025?" |
| `compliance.compliance_assessments` | `compliance.compliance_assessments_history` | "What was our ISO 27001 score last quarter?" |
| `platform.controls` | `platform.controls_history` | "When did this control change its requirement?" |
| `iam.users` | `iam.users_history` | "What roles did this user have on the date of the incident?" |

Temporal queries are served by `TemporalTableService`:

```java
// Get risk state as it was on a specific date
riskRepository.findByIdAsOf(riskId, LocalDateTime.of(2025, 1, 1, 0, 0));
// → SELECT * FROM risk.risks FOR SYSTEM_TIME AS OF '2025-01-01T00:00:00'
```

---

## 4. Audit Log Capture Mechanism

### JPA Entity Listener

`AuditEntityListener` (annotated with `@EntityListeners` on `BaseEntity`) captures `@PostPersist`, `@PostUpdate`, and `@PostRemove` events and publishes a Spring `AuditEvent` to `AuditEventListener`.

### AOP for Service-Layer Events

`@Around` advice on `@Audited` annotated service methods captures non-entity events (logins, exports, access denials):

```java
@Audited(eventType = "EXPORT", entityType = "risk.risks")
public byte[] exportRiskRegister(ExportRequest request) { ... }
```

### What is Captured

| Event | Mechanism | Before/After JSON |
|---|---|---|
| Entity create | JPA `@PostPersist` | NULL → after |
| Entity update | JPA `@PostUpdate` | before → after |
| Entity delete (soft) | JPA `@PostUpdate` on `is_deleted = true` | before → after |
| Status transition | JPA `@PostUpdate` | before state → after state |
| Login success | AOP `@Audited` on `AuthController` | NULL → NULL (event_type = LOGIN) |
| Login failure | AOP | NULL → NULL (event_type = LOGIN_FAILED) |
| Report export | AOP | NULL → NULL (event_type = EXPORT) |
| Permission denied | Spring Security `AccessDeniedHandler` | NULL → NULL (event_type = ACCESS_DENIED) |

---

## 5. Evidence Chain

The full traceability chain from a compliance requirement to physical evidence:

```
platform.control_frameworks
    └── platform.controls
            └── compliance.assessment_control_results
                    └── compliance.evidence_items
                            └── platform.attachments (file)
                                    └── audit.audit_logs (upload event)
                                            chain_hash (tamper detection)
```

This chain satisfies ISO 27001 Annex A.5.35 (Independent review of information security) and NCA ECC 2.0 audit evidence requirements.

---

## 6. Admin Audit Log Viewer API

| Endpoint | Description |
|---|---|
| `GET /api/v1/audit/logs` | Paginated log with filters: entity_type, user_id, module, date_range, event_type |
| `GET /api/v1/audit/logs/{id}` | Single log entry with before/after JSON diff |
| `GET /api/v1/audit/entity/{entityType}/{entityId}` | Full history for a specific entity |
| `GET /api/v1/audit/verify-chain` | Verify hash chain integrity for a date range |
| `GET /api/v1/audit/export` | Export audit log to Excel/PDF (triggers async report execution) |

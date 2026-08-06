# Reporting and Export Data Preparation Strategy
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines how SQL Server 2019 views and data models are structured to support efficient dashboard queries, report generation, and export operations.

---

## SQL Server Express Edition Constraints for Reporting

| Feature | Express Availability | Mitigation |
|---------|---------------------|-----------|
| Indexed Views (SCHEMABINDING) | ✅ Available | Use for high-frequency aggregations |
| JSON functions (FOR JSON, JSON_VALUE) | ✅ Available | Use for dashboard API responses |
| Window functions (ROW_NUMBER, RANK, LAG) | ✅ Available | Use for trend calculations |
| Table Partitioning | ❌ Not Available | Use archival strategy instead |
| Columnstore Indexes | ✅ Available (Express 2016+) | Consider for large reporting tables |
| Full-Text Search | ❌ Not Available | Use LIKE-based search |

---

## Reporting Views

### 1. Unified Risk Summary View

```sql
CREATE VIEW reporting.vw_unified_risk_summary
WITH SCHEMABINDING
AS
SELECT
    r.id,
    r.org_id,
    r.bu_id,
    r.title_en,
    r.title_ar,
    r.source_module,
    r.risk_type,
    r.likelihood,
    r.impact,
    r.inherent_score,
    r.residual_score,
    r.risk_level,
    r.status,
    r.owner_user_id,
    r.category_id,
    r.created_at
FROM risk.risks AS r
WHERE r.is_deleted = 0;
GO
```

### 2. Compliance Status View

```sql
CREATE VIEW reporting.vw_compliance_status
AS
SELECT
    a.org_id,
    cf.code            AS framework_code,
    cf.name_en         AS framework_name,
    a.id               AS assessment_id,
    a.status           AS assessment_status,
    a.overall_score,
    COUNT(ac.id)                                            AS total_controls,
    SUM(CASE WHEN ac.compliance_status = 'COMPLIANT' THEN 1 ELSE 0 END)          AS compliant_controls,
    SUM(CASE WHEN ac.compliance_status = 'NON_COMPLIANT' THEN 1 ELSE 0 END)      AS non_compliant_controls,
    SUM(CASE WHEN ac.compliance_status = 'PARTIALLY_COMPLIANT' THEN 1 ELSE 0 END) AS partial_controls
FROM compliance.assessments a
INNER JOIN compliance.compliance_frameworks cf ON a.framework_id = cf.id
LEFT JOIN compliance.assessment_controls ac ON a.id = ac.assessment_id
WHERE a.is_deleted = 0
GROUP BY a.org_id, cf.code, cf.name_en, a.id, a.status, a.overall_score;
GO
```

### 3. Active Controls Effectiveness View

```sql
CREATE VIEW reporting.vw_active_controls
AS
SELECT
    c.id,
    c.org_id,
    c.code,
    c.title_en,
    c.title_ar,
    c.control_type,
    c.implementation_status,
    c.effectiveness,
    c.owner_user_id,
    c.last_reviewed_at,
    c.next_review_at,
    u.display_name_en AS owner_name,
    STRING_AGG(cf.code, ', ') AS framework_codes
FROM platform.controls c
LEFT JOIN iam.users u ON c.owner_user_id = u.id
LEFT JOIN platform.control_framework_mappings cfm ON c.id = cfm.control_id
LEFT JOIN platform.control_frameworks cf ON cfm.framework_id = cf.id
WHERE c.is_deleted = 0 AND c.is_active = 1
GROUP BY c.id, c.org_id, c.code, c.title_en, c.title_ar,
         c.control_type, c.implementation_status, c.effectiveness,
         c.owner_user_id, c.last_reviewed_at, c.next_review_at,
         u.display_name_en;
GO
```

### 4. Incident Summary View

```sql
CREATE VIEW reporting.vw_incident_summary
AS
SELECT
    i.org_id,
    YEAR(i.detected_at)  AS incident_year,
    MONTH(i.detected_at) AS incident_month,
    i.type               AS incident_type,
    i.severity,
    i.status,
    COUNT(*)             AS incident_count
FROM cyber.incidents i
WHERE i.is_deleted = 0
GROUP BY i.org_id, YEAR(i.detected_at), MONTH(i.detected_at),
         i.type, i.severity, i.status;
GO
```

---

## Dashboard Data Caching Strategy

| Layer | Mechanism | TTL |
|-------|-----------|-----|
| SQL Views | Materialized at query time | N/A |
| Application Cache (Caffeine) | In-memory cache of dashboard aggregates | 5 minutes |
| HTTP Cache-Control | Browser caching for static dashboard HTML | 1 hour |

Dashboard REST endpoints:
```
GET /api/v1/dashboard/risk-summary?orgId={id}    → Cached 5 min
GET /api/v1/dashboard/compliance-status?orgId={id} → Cached 5 min
GET /api/v1/dashboard/incident-metrics?orgId={id}  → Cached 5 min
```

---

## Large Export Strategy

For exports > 1000 records:
1. Async export job initiated; returns `reportExecutionId`
2. Browser polls `GET /api/v1/reports/executions/{id}/status`
3. On completion: file downloadable for 24 hours
4. File stored temporarily in `/tmp/exports/` with UUID filename
5. Execution record in `reporting.report_executions` tracks status

---

## Read Optimization for SQL Server Express

Since Express has 1.4 GB RAM limit:
- **Minimize broad scans**: All dashboard queries use indexed columns in WHERE clause
- **Paginate everything**: Never fetch unbounded result sets
- **Aggregate at DB layer**: SUM/COUNT in SQL rather than in Java
- **Avoid SELECT ***: Specify only required columns in all queries
- **Use covering indexes**: Dashboard query columns included in index INCLUDE clause

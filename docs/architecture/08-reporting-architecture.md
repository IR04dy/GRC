# Reporting Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the reporting, dashboard, and analytics architecture for the TSS GRC Platform, covering dashboard design, chart/heatmap rendering, export formats, scheduled reports, and the Unified Risk View.

---

## Reporting Tiers

| Tier | Description | Users |
|------|-------------|-------|
| **Executive Dashboard** | High-level KPIs, unified risk view, compliance scores | CEO, Board, CRO, CISO |
| **Module Dashboards** | Operational metrics per module | Risk Managers, Compliance Officers, BCM Managers |
| **Operational Reports** | Tabular detail reports, filterable, exportable | All module users |
| **Scheduled Reports** | Automated generation and email delivery | Configured by admins |
| **Audit Reports** | Audit trail, user activity, change history | Auditors, Admins |

---

## Dashboard Framework

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Charts & Graphs | **Chart.js 4.x** (client-side, lightweight, supports RTL) |
| Risk Heatmap | Custom Chart.js matrix plugin + D3.js-style grid |
| Data Tables | **DataTables.net** with server-side processing |
| Dashboard Layout | CSS Grid + Bootstrap 5 cards |
| Real-time refresh | JavaScript polling (configurable interval, default 5 min) |

### Chart Types

| Chart | Use Case | Module |
|-------|---------|--------|
| Risk Heatmap (5×5 matrix) | Risk distribution by likelihood × impact | ERM, Cyber, Unified |
| Donut / Pie Chart | Compliance status distribution, risk by category | All |
| Bar Chart | Incidents by month, controls by status, findings by severity | All |
| Line Chart | Risk score trends, compliance score over time | ERM, Compliance |
| KPI Cards | Single metric highlight (total open risks, compliance %) | All dashboards |
| Gauge Chart | Risk appetite consumption | ERM |
| Timeline | Workflow step history, incident timeline | Workflow, Cyber |

---

## Unified Risk View

The Unified Risk View is a cross-module aggregation that consolidates all risk data into a single view. It is the flagship reporting feature.

### Data Sources

```
ERM Module          → risk.risks (source_module = 'ERM')
Cybersecurity       → cyber.incidents elevated to risk.risks (source_module = 'CYBER')
BCM                 → bcm.business_impact_analyses → risk.risks (source_module = 'BCM')
Privacy             → privacy.breach_records → risk.risks (source_module = 'PRIVACY')
Compliance          → compliance.findings with severity HIGH/CRITICAL → risk.risks (source_module = 'COMPLIANCE')
```

### SQL Server View

```sql
CREATE VIEW reporting.vw_unified_risk_summary AS
SELECT
    r.id, r.org_id, r.title_en, r.title_ar, r.source_module,
    r.risk_type, r.likelihood, r.impact, r.residual_score,
    r.risk_level, r.status, r.owner_user_id,
    u.display_name_en AS owner_name,
    rc.name_en AS category_name
FROM risk.risks r
LEFT JOIN iam.users u ON r.owner_user_id = u.id
LEFT JOIN risk.risk_categories rc ON r.category_id = rc.id
WHERE r.is_deleted = 0;
```

---

## Export Architecture

### Technologies

| Format | Library | Notes |
|--------|---------|-------|
| **Excel (.xlsx)** | Apache POI 5.x | Full formatting, multiple sheets, RTL support |
| **PDF** | OpenPDF (fork of iText 4) | Free/open, Arabic text via custom font (Amiri or Noto Arabic) |
| **Word (.docx)** | Apache POI XWPF | Table-based reports, RTL paragraph support |

### Arabic PDF Support

Arabic PDF export requires:
1. Embed Arabic Unicode font (Amiri or Noto Naskh Arabic) in the PDF
2. Set text direction to RTL for Arabic paragraphs
3. Use logical character ordering (Bidi algorithm)
4. OpenPDF supports this via `BaseFont.createFont()` with embedded Unicode font

### Export Flow

```
User clicks Export → ExportController → ExportService
    → Query data from DB (respects current filters)
    → Build report model (DTOs)
    → Pass to format-specific renderer (PdfRenderer, ExcelRenderer, WordRenderer)
    → Stream response to browser with correct Content-Type
```

### Large Export Handling

For reports with > 1000 rows:
- Async generation via `@Async` method
- Status tracked in `reporting.report_executions`
- User notified via in-app notification when ready
- Download link provided

---

## Scheduled Reports

```
Admin creates ReportSchedule → saved to reporting.report_schedules
    ↓
Spring @Scheduled (cron) reads active schedules
    ↓
For each due schedule: execute report query → generate file → email to recipients
    ↓
Execution logged to reporting.report_executions
```

**Note:** SQL Server Express has no SQL Server Agent. All scheduling uses Spring `@Scheduled` within the application.

---

## Report Definitions

Reports are defined in `reporting.report_definitions` table:
- `query_template`: parameterized SQL or JPQL template
- `parameters_schema`: JSON schema defining available filter parameters
- `output_formats`: allowed export formats (PDF, EXCEL, WORD)

This allows new reports to be added without code changes (configuration-driven report definitions).

---

## Dashboard Configuration

Per-user and per-organization dashboard configurations stored in `reporting.dashboard_configurations`:
- Widget placement (JSON layout)
- Widget configuration (date range, filters)
- Default dashboard per role (pre-configured layouts for Risk Manager, CISO, etc.)

---

## Performance Considerations

| Strategy | Details |
|----------|---------|
| Reporting views | SQL Server views pre-join frequently aggregated data |
| Dashboard caching | Dashboard data cached in-memory (Caffeine) with 5-minute TTL |
| Async export | Large reports generated asynchronously |
| Pagination | All tabular reports paginated; no full table scans in UI |
| Index coverage | Reporting views supported by appropriate non-clustered indexes |

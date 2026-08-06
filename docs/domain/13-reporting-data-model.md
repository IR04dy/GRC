# 13 - Reporting & Analytics Data Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Overview

The Reporting module provides executive dashboards, cross-module aggregations, and exportable reports. It is a **read-heavy** consumer of all other module schemas via SQL views, and produces outputs as JSON (for the frontend dashboard), Excel (Apache POI), and PDF (OpenPDF with Arabic RTL support).

---

## 1. Core Entities

### `reporting.report_definitions`

Saved report templates with parameters, layout, and scheduling configuration.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `report_name_en` | NVARCHAR(300) | Report name (English) |
| `report_name_ar` | NVARCHAR(300) | Report name (Arabic) |
| `report_type` | NVARCHAR(50) | RISK_SUMMARY / COMPLIANCE_STATUS / INCIDENT_TREND / BCM_READINESS / PRIVACY_REGISTER / EXECUTIVE_DASHBOARD / CUSTOM |
| `output_format` | NVARCHAR(10) | PDF / EXCEL / JSON |
| `parameters_json` | NVARCHAR(MAX) | JSON: filter parameters (framework_id, date_range, modules, etc.) |
| `is_public` | BIT | Visible to all users in the org (vs. creator only) |
| `created_by` | BIGINT | FK → `iam.users` |
| `is_deleted` | BIT | Soft delete |

### `reporting.report_executions`

Async execution tracking. Large reports are queued and generated in the background via `@Async`.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID — polled by frontend to check status |
| `organisation_id` | BIGINT | Tenant |
| `definition_id` | BIGINT | FK → `reporting.report_definitions` |
| `requested_by` | BIGINT | FK → `iam.users` |
| `requested_at` | DATETIME2(7) | When the job was queued |
| `started_at` | DATETIME2(7) | When processing began |
| `completed_at` | DATETIME2(7) | When the file was ready |
| `status` | NVARCHAR(20) | QUEUED / RUNNING / COMPLETED / FAILED |
| `output_format` | NVARCHAR(10) | PDF / EXCEL |
| `file_name` | NVARCHAR(300) | Generated file name |
| `attachment_id` | BIGINT | FK → `platform.attachments` (download source) |
| `error_message` | NVARCHAR(MAX) | Error detail if status = FAILED |
| `parameters_json` | NVARCHAR(MAX) | Actual parameters used for this run |
| `locale` | NVARCHAR(10) | 'en' or 'ar' — determines output language and RTL layout |

### `reporting.report_schedules`

Scheduled report delivery — replaces SQL Server Agent with Spring `@Scheduled`.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `definition_id` | BIGINT | FK → `reporting.report_definitions` |
| `cron_expression` | NVARCHAR(50) | Spring cron expression (e.g. `0 0 8 1 * ?` = 8am on 1st of month) |
| `recipients_json` | NVARCHAR(MAX) | JSON array of `{ userId, email }` delivery targets |
| `is_active` | BIT | Enable/disable schedule |
| `last_run_at` | DATETIME2(7) | Last execution timestamp |
| `next_run_at` | DATETIME2(7) | Calculated next run (informational) |

---

## 2. SQL Views (Reporting Layer)

All dashboard data is served from pre-defined SQL views. Views are read-only and query multiple schemas — they do not replace the module-owned tables.

### `reporting.vw_unified_risk_summary`
Joins `risk.risks` with `risk.risk_categories`, `risk.risk_assessments`, and `iam.users` to produce a flattened risk record with inherent score, residual score, risk level, owner, and `source_module` badge.

### `reporting.vw_compliance_status`
Aggregates `compliance.assessment_control_results` grouped by `(organisation_id, framework_id)` to show compliant/partial/non-compliant counts and the overall compliance percentage per framework.

### `reporting.vw_incident_summary`
Joins `cyber.cyber_incidents` with severity and status filters to show open, contained, and closed incident counts by month (last 12 months trend).

### `reporting.vw_active_controls`
Lists all `platform.controls` with their compliance status (from the most recent assessment) and linked evidence count.

### `reporting.vw_user_permissions`
Flattened RBAC view: `iam.users → iam.user_roles → iam.roles → iam.role_permissions → iam.permissions` — used by the Admin module's permission matrix UI.

---

## 3. Dashboard Aggregation API

The `DashboardController` calls `DashboardAggregationService` which caches results with Caffeine (5-minute TTL keyed by `organisationId`):

| Endpoint | Data Source | Cache Key |
|---|---|---|
| `GET /api/v1/dashboard/stats` | Multiple schemas | `dashboard-stats-{orgId}` |
| `GET /api/v1/dashboard/heatmap` | `risk.risks` | `dashboard-heatmap-{orgId}` |
| `GET /api/v1/dashboard/risk-by-category` | `risk.risks + risk.risk_categories` | `dashboard-cat-{orgId}` |
| `GET /api/v1/dashboard/compliance-by-framework` | `vw_compliance_status` | `dashboard-compliance-{orgId}` |
| `GET /api/v1/dashboard/recent-risks` | `risk.risks` | Not cached (always fresh) |
| `GET /api/v1/dashboard/recent-incidents` | `cyber.cyber_incidents` | Not cached |
| `GET /api/v1/dashboard/my-tasks` | `workflow.workflow_tasks` | Not cached (user-specific) |

---

## 4. Export Architecture

### Excel (Apache POI 5.x)
- `ExcelExportService` creates an `XSSFWorkbook` per report type
- Arabic reports: sheet title and header cells use right-to-left cell alignment (`CellStyle.ALIGN_RIGHT`), and the `Sheet.setRightToLeft(true)` flag is set when `locale = ar`
- Noto Kufi Arabic font embedded for Arabic column headers

### PDF (OpenPDF)
- `PdfExportService` uses OpenPDF `Document` API
- Arabic text rendered with `BaseFont.createFont("NotoKufiArabic-Regular.ttf", ...)` (bundled in `src/main/resources/fonts/`)
- Bidi text support: Arabic paragraphs wrapped in `Paragraph` with `Alignment.RIGHT` and run through `java.text.Bidi` for character reordering

### Async Flow
```
POST /api/v1/reports/execute  →  create ReportExecution (QUEUED)
                               →  @Async ReportGenerationJob.generate()
                               →  update status RUNNING → COMPLETED/FAILED
                               →  store file in platform.attachments

GET  /api/v1/reports/executions/{externalId}  ←  frontend polls every 2s
     status = COMPLETED  →  GET /api/v1/reports/executions/{id}/download
```

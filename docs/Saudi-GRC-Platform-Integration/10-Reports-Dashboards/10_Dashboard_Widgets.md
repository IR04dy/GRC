# 10 — Dashboard Widgets
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Widgets are the read-only building blocks of every page. Each widget
is a single backend endpoint returning a small JSON payload + a
frontend React component.

## Executive Dashboard (`/dashboard`)

| Widget | Backend | Type | Refresh |
|---|---|---|---|
| Overall GRC Health Score | `/api/v1/dashboard/health-score` | gauge 0-100 | 5 min |
| Top 5 enterprise risks | `/api/v1/risk/risks?top=5` | table | live |
| Risk Appetite Breaches | `/api/v1/risk/appetite/breaches` | KPI + sparkline | live |
| Critical Service Readiness | `/api/v1/bcm/readiness/summary` | RAG bar | hourly |
| BCM Maturity | `/api/v1/bcm/maturity` | radar chart | hourly |
| NCA Compliance Score | `/api/v1/cyber/nca/score?framework=ECC` | gauge | hourly |
| PDPL Compliance Score | `/api/v1/privacy/pdpl/score` | gauge | hourly |
| Supplier Resilience Score | `/api/v1/scm/resilience/score` | gauge | hourly |
| Overdue CAPAs | `/api/v1/capa/overdue/count` | KPI badge | 5 min |
| Major Incidents (30d) | `/api/v1/incident/recent?severity=CRITICAL` | list | live |
| Compliance Posture | `/api/v1/regulatory/posture` | heatmap (F6) | 5 min |
| 24h Audit Activity | `/api/v1/audit-log/stats` (F7) | counts + spark | live |
| Automation rule fires (7d) | `/api/v1/automation/stats` (F8) | counts | 5 min |

## BCM Dashboard widgets

| Widget | Backend |
|---|---|
| Critical services count | `/api/v1/bcm/services?critical=true` |
| BIA completion % | `/api/v1/bcm/bia/completion` |
| BCP completion % | `/api/v1/bcm/plans/completion` |
| DRP completion % | `/api/v1/bcm/drp/completion` |
| RTO compliance | `/api/v1/bcm/rto/compliance` |
| RPO compliance | `/api/v1/bcm/rpo/compliance` |
| Exercise success rate | `/api/v1/bcm/exercises/success-rate` |
| Failed exercises | `/api/v1/bcm/exercises?status=FAILED` |
| Open BCM gaps | `/api/v1/bcm/gaps?status=OPEN` |

## Supply Chain Dashboard widgets

| Widget | Backend |
|---|---|
| Critical suppliers | `/api/v1/scm/suppliers?criticality=CRITICAL` |
| No-valid-BCP suppliers | `/api/v1/scm/suppliers?missing=BCP` |
| SLA-breach suppliers | `/api/v1/scm/sla/breaches` |
| Concentration risk | `/api/v1/scm/concentration` (Herfindahl) |
| Single points of failure | `/api/v1/scm/single-points-of-failure` |
| 4th-party exposure | `/api/v1/scm/fourth-party/summary` |
| Supplier risk heatmap | `/api/v1/scm/heatmap` |
| Alternate supplier readiness | `/api/v1/scm/alternates/readiness` |

## ERM Dashboard widgets

| Widget | Backend |
|---|---|
| Risk heatmap (5×5) | `/api/v1/risk/heatmap` |
| Top 10 risks | `/api/v1/risk/risks?top=10` |
| Appetite breaches | `/api/v1/risk/appetite/breaches` |
| Treatment status | `/api/v1/risk/treatments/summary` |
| KRI breaches | `/api/v1/risk/kri/breaches` |
| Emerging risks | `/api/v1/risk/emerging` |
| Overdue risk reviews | `/api/v1/risk/reviews/overdue` |
| Control effectiveness | `/api/v1/controls/effectiveness` (F2) |

## NCA Dashboard widgets

| Widget | Backend |
|---|---|
| Overall NCA score | `/api/v1/cyber/nca/score` |
| Per-framework scores | `/api/v1/cyber/nca/scores` (ECC/CSCC/CCC/OTCC) |
| Open cyber gaps | `/api/v1/cyber/gaps?status=OPEN` |
| Critical non-compliances | `/api/v1/cyber/gaps?severity=CRITICAL` |
| Overdue remediation | `/api/v1/capa/overdue?domain=CYBER` |
| Critical vulnerabilities | `/api/v1/cyber/vulns?severity=CRITICAL` |
| Unpatched critical systems | `/api/v1/cyber/vulns?critical_system=true&status=OPEN` |
| Cyber incidents by severity | `/api/v1/cyber/incidents/by-severity` |
| Evidence expiry (60d) | `/api/v1/evidence/expiring?days=60&framework=NCA` |

## PDPL Dashboard widgets

| Widget | Backend |
|---|---|
| Processing activities count | `/api/v1/privacy/ropa/count` |
| High-risk activities | `/api/v1/privacy/ropa?risk=HIGH` |
| PIAs completed | `/api/v1/privacy/dpia?status=APPROVED` |
| PIAs overdue | `/api/v1/privacy/dpia/overdue` |
| DSRs open | `/api/v1/privacy/dsr?status=IN_PROGRESS` |
| DSR SLA breaches | `/api/v1/privacy/dsr/sla-breaches` |
| Active consents | `/api/v1/privacy/consent?status=GRANTED` |
| Withdrawn consents | `/api/v1/privacy/consent?status=WITHDRAWN` |
| Cross-border transfers | `/api/v1/privacy/cross-border/count` |
| Privacy incidents | `/api/v1/privacy/breaches/count` |
| Breach notifications | `/api/v1/privacy/breaches?status=NOTIFIED` |
| Processors without valid agreement | `/api/v1/privacy/processors?status=MISSING_DPA` |
| Retention violations | `/api/v1/privacy/retention/violations` |

## Widget component conventions

- Backend returns small JSON (KPI value + sparkline points + RAG
  band).
- Frontend component is `<Kpi label value tone />` or
  `<DataTable />` or `<BarChart3D />` etc.
- Polling is opt-in via the component's `refreshIntervalMs` prop.
- All widget data filtered by `TenantContext.getCurrentTenant()`.

---

**See also:** `module/dashboard`, `module/reporting`, the F6 hub.

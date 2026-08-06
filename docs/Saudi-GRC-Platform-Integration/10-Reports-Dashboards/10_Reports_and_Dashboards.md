# 10 — Reports & Dashboards
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Reports inventory (20 from prompt §15)

| # | Report | Audience | Source service | Export formats | Digital signature |
|---|---|---|---|---|---|
| 1 | Board Risk Report | Board | `reporting.BoardRiskReportService` | PDF + XLSX | ✅ |
| 2 | Executive GRC Dashboard | Exec | `dashboard.ExecutiveDashboardService` | UI + PDF snapshot | — |
| 3 | BCM Readiness Report | BCM Manager + Exec | `bcm.ReadinessReportService` | PDF + XLSX | — |
| 4 | Critical Services Continuity | Exec + Auditor | `bcm.ContinuityReportService` | PDF + XLSX | — |
| 5 | DR Readiness | DR Owner + Exec | `bcm.DrReadinessReportService` | PDF + XLSX | — |
| 6 | Crisis Exercise | Crisis Manager + Exec | `bcm.CrisisExerciseReportService` | PDF | — |
| 7 | Supplier Resilience | Supplier Manager + Risk Committee | `scm.SupplierResilienceReportService` | PDF + XLSX | — |
| 8 | NCA Compliance | NCA Compliance Owner + Auditor | `cyber.NcaComplianceReportService` | PDF + XLSX | ✅ on annual |
| 9 | PDPL Compliance | DPO + Legal | `privacy.PdplComplianceReportService` | PDF + XLSX | ✅ on annual |
| 10 | Regulatory Compliance (all) | Compliance Officer | `compliance.RegulatoryReportService` | PDF + XLSX | ✅ on annual |
| 11 | Corrective Action Status | CAPA Owner + Manager | `capa.CapaStatusReportService` | PDF + XLSX | — |
| 12 | Risk Appetite Breach | Risk Committee | `risk.AppetiteBreachReportService` | PDF + XLSX | — |
| 13 | Cyber Incident | Cyber Manager + Exec | `cyber.IncidentReportService` | PDF | — |
| 14 | Data Breach (PDPL §27) | DPO + Regulator | `privacy.DataBreachReportService` | PDF | ✅ |
| 15 | Audit Evidence Package | External / Internal Auditor | `audit_lc.EvidencePackageService` | ZIP + index PDF | ✅ |
| 16 | Management Review | Mgmt + Exec | `bcm.ManagementReviewService` | PDF | ✅ |
| 17 | Supplier Concentration Risk | Risk Manager + Procurement | `scm.ConcentrationReportService` | PDF + XLSX | — |
| 18 | Critical Systems Compliance | Cyber Manager | `cyber.CriticalSystemsReportService` | PDF + XLSX | — |
| 19 | Cloud Compliance (CCC) | Cyber Manager | `cyber.CccReportService` | PDF + XLSX | — |
| 20 | Privacy Risk Report | DPO + Risk Committee | `privacy.PrivacyRiskReportService` | PDF + XLSX | — |

## Report structure

Each report follows a uniform layout:

1. **Cover sheet** — org logo, tenant name, period, prepared by,
   approval signature block (if Sig=✅).
2. **Executive summary** (1 page) — RAG indicators + headline KPIs.
3. **Sections** — domain-specific, with charts + tables.
4. **Annex** — raw data export, evidence references.

## Refresh cadence

| Report | Cadence | Auto-run schedule |
|---|---|---|
| Executive Dashboard | live | n/a |
| Board Risk Report | Quarterly | `reporting.report_schedules` |
| BCM Readiness | Quarterly | `reporting.report_schedules` |
| NCA Compliance | Annual + on-demand | scheduled |
| PDPL Compliance | Annual + on-demand | scheduled |
| Supplier Resilience | Quarterly | scheduled |
| CAPA Status | Weekly | scheduled |
| All others | On-demand | manual run |

## Where the data comes from

Most reports read from `platform.audit_log` + `control_assessments`
+ domain registers. F6's `CompliancePostureService` already exposes
the heatmap data; reports 8/9/10/16 use it directly.

## Approval workflow

Reports with Digital Signature ✅ flow:

`DRAFT → REVIEW → APPROVED (signed) → DISTRIBUTED`

`reporting.report_runs.status` tracks state.

## Export endpoints

```
POST /api/v1/reporting/runs?definitionId={id}     run a defined report
GET  /api/v1/reporting/runs/{id}                  fetch result metadata
GET  /api/v1/reporting/runs/{id}/download?format=pdf|xlsx|zip
POST /api/v1/reporting/runs/{id}/sign             attach signature
POST /api/v1/reporting/runs/{id}/distribute       distribute to recipients
```

---

**See also:** `10_Dashboard_Widgets.md`, `module/reporting`,
`module/board_pack`.

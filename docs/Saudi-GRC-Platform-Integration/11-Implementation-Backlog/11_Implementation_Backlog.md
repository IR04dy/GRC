# 11 — Implementation Backlog
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Mirrors the epic structure in the prompt §16 and reflects what's
actually in the codebase + on the planned slice list.

## Epic status

| # | Epic | Status | Anchor commits / slices |
|---|---|---|---|
| 1 | Foundation Platform | ✅ Built | Sprint 1 |
| 2 | Multi-Tenant Configuration | ✅ Built | `TenantContext`, `JwtAuthenticationFilter` |
| 3 | Regulatory Library | ✅ Built (F1) | V91, `module/platform/regulatory` |
| 4 | Workflow Engine | ✅ Built | `module/workflow` |
| 5 | Evidence Repository | ✅ Built | `module/evidence` |
| 6 | ERM | ✅ Built (Sprint 1-2 + Risk-Advanced) | V80 |
| 7 | BCM | ✅ Built | `module/bcm` + `module/bcm_lc` |
| 8 | Supply Chain Resilience | ✅ Built | `module/scm` + `module/tprm` |
| 9 | NCA Compliance | ✅ Built | `module/cyber` |
| 10 | PDPL Compliance | ✅ Built | `module/privacy` + `module/privacy_lc` |
| 11 | Incident + Crisis | ✅ Built | `module/incident` + `module/bcm.crisis` |
| 12 | Reporting + Dashboards | ✅ Built | `module/reporting` + `module/dashboard` + F6 |
| 13 | Digital Signature Integration | ✅ Built | `module/signature` |
| 14 | Administration + Configuration | ✅ Built | `module/admin` + F9 licensing |
| 15 | API + Integration Layer | ✅ Built | SpringDoc OpenAPI |
| 16 | Bilingual Arabic/English UI | ✅ Built | `translations/{en,ar}.ts` + RTL |
| 17 | Audit Trail + Security | ✅ Built (F7) | V95 `platform.audit_log` |
| 18 | Notification + Escalation Engine | ✅ Built (F8) | V96 `automation_rules` + 20 seeded |
| 19 | Data Import/Export | ✅ Built | `csv-import`, evidence import, control library import |
| 20 | Demo Seed Data | ✅ Built | V1-V97 |

## Sprint plan (current)

| Sprint | Theme | Slices | Status |
|---|---|---|---|
| Sprint 1 | Foundation + ERM + BCM core | Risk register, BIA, BCP, DRP, Risk appetite | ✅ |
| Sprint 2 | Supply Chain, NCA, Privacy core | TPRM, NCA assessments, PDPL ROPA/DSR/DPIA | ✅ |
| Sprint 3 | SDAIA NDI cycle | 15-slice NDI build (cycles, framework lib, maturity/compliance/opex/gov/evidence/gaps/CAPA/plan/submission/reports/dashboard/settings/audit/QA) | ✅ |
| Sprint 4 | Risk-Advanced + TPRM Questionnaire + Policy | V79/V80/V81 | ✅ |
| Sprint 5 | Framework Intersection F1-F6 | V91-V94 + F5/F6 frontend | ✅ |
| Sprint 6 | Cross-cutting engines F7-F9 | V95 audit log, V96 automation, V97 licensing | ✅ |
| Sprint 7 | F10 docs + frontends for F8/F9 + automation rule action handlers | this PR + follow-ups | 🟡 In progress |
| Sprint 8 | AI assistance + CCM connectors | task #41 + future track | 🔜 Planned |

## Outstanding work tracked separately

| Task | Description |
|---|---|
| #36 | Dedicated dashboards for BCM / SCM / ERM / NCA / PDPL — single-page versions reusing widgets |
| #39 | Missing entities — Department, ConsentRecord, CloudService, Issue |
| #41 | AI-assisted features — 5 high-leverage endpoints |
| (F8 follow-ups) | Wire real action handlers into each consuming module (CREATE_RECORD, NOTIFY, ESCALATE, FLAG) |
| (F9 follow-ups) | JWT claim injection + LicenseGuard aspect + frontend admin UI |
| (RTL hardening) | Comprehensive RTL audit across newer pages |

## Backlog grooming cadence

- Weekly sync with the GRC consultancy lead.
- Monthly review against the Gartner IRM Magic Quadrant criteria
  (see `docs/grc/framework-intersection-plan.md` §R3).
- Per-release verification against the QA checklists in
  `docs/*/QA_CHECKLIST.md` + this `/Saudi-GRC-Platform-Integration/`
  package.

---

**See also:** `11_User_Stories.md`, `11_Acceptance_Criteria.md`,
`STATUS.md`, `sprints/sprint-{1,2,3}.md`.

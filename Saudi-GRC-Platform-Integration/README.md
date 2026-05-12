# Saudi GRC Platform — Integration Deliverable Package

Structured deliverables for the TSS GRC platform. Every document in this
package is anchored to the running code, schema, and seed data at HEAD
of branch `feat/pdpl-extensions`.

**Read order:** start with `01-Product-Blueprint`, then read whichever
section is relevant to your role (see "Owner / development usage" column
below).

---

## File output plan

| # | Folder | File | Type | Purpose | Owner | Development usage |
|---|---|---|---|---|---|---|
| 1 | `01-Product-Blueprint/` | `product-blueprint.md` | MD | Platform purpose, positioning, customers, regulatory coverage, modules, integration logic, licensing, differentiators, maturity roadmap | Product / GTM | Pitch + onboarding reference |
| 2 | `02-Module-Architecture/` | `module-architecture.md` | MD | Per-module description, personas, workflows, registers, screens, key fields, evidence, reports, integrations — all 10 modules | Tech leads | Design + handoff |
| 3 | `03-Workflow-Catalogue/` | `workflow-catalogue.md` | MD | Every workflow with EN/AR name, trigger, steps, roles, SLA, evidence, signature requirement | Implementation | Sprint planning + UAT |
| 3 | `03-Workflow-Catalogue/` | `workflow-lifecycles.md` | MD | Per-workflow state machine: status, allowed transitions, mover, SLA, notifications | Implementation | Workflow engine config |
| 4 | `04-Data-Model/` | `data-model.md` | MD | Logical data model + entity descriptions + key fields + relations + AR/EN labels | DB lead | Schema + ORM scaffolding |
| 5 | `05-UI-Screens/` | `ui-screens.md` | MD | Per-screen requirements (EN/AR title, sections, fields, filters, actions, validation, role permissions, empty states, RTL considerations) | UX + Frontend | Page build + design review |
| 6 | `06-Evidence-Library/` | `evidence-library.md` | MD | Per-control evidence catalogue: owner, frequency, types, expiry, approval, regulatory source | Compliance + Evidence | Evidence catalog seeding |
| 7 | `07-Regulatory-Mapping/` | `regulatory-mapping.md` | MD | DGA / SAMA / NRC / NCA / PDPL / ISO 22301 / 27001 / 31000 / 27701 mapping to modules + workflows + objects + evidence | Compliance | Regulatory alignment |
| 8 | `03-Workflow-Catalogue/` | `workflow-lifecycles.md` | MD | (see above — lifecycle definitions live alongside the catalogue) | Workflow Engine | Engine wiring |
| 9 | `08-RBAC/` | `rbac-matrix.md` | MD | Full RBAC matrix: 27 roles × 8 actions × 11 modules | Security + IAM | Permission seeding |
| 10 | `09-Automation-Rules/` | `automation-rules.md` | MD | 28 cross-module automation rules: trigger, condition, action, notification, escalation, audit | Backend | Service-layer hooks |
| 11 | `10-Reports-Dashboards/` | `reports-dashboards.md` | MD | 16 report specs: audience, sections, KPIs, charts, filters, formats, approval, signature | Reporting + Frontend | Report templates |
| 12 | `11-Implementation-Backlog/` | `implementation-backlog.md` | MD | Agile backlog: 16 epics × stories with priority + dependency + sprint grouping; status (DONE/IN_PROGRESS/PLANNED) | PMO + Engineering | Sprint planning |
| 13 | `13-API-Specification/` | `api-specification.md` | MD | REST endpoint catalogue + conventions; pointer to live OpenAPI at `/v3/api-docs` | Backend + Integrations | API contract |
| 14 | `12-Developer-Handoff/` | `developer-handoff.md` | MD | Architecture, workflow engine, API, DB, audit, notifications, integration, signature, security, localisation, reporting, testing, migration requirements | Engineering | Onboarding |
| 15 | `14-Test-Cases/` | `test-cases.md` | MD | UAT test cases: ID, scenario, preconditions, steps, expected, role, pass/fail | QA + UAT | Acceptance testing |
| 16 | `15-Sample-Data/` | `sample-seed-data.md` | MD | Seed-data catalogue per migration; instructions for fresh-DB bootstrap | Engineering + Demo | Demo + UAT data |
| 17 | `16-AI-Features/` | `ai-features.md` | MD | 15 LLM-assisted features (risk-description, BIA impact, RTO/RPO, supplier scoring, PIA + NCA gap, evidence quality, CAPA advisor, exec / board narratives, reg-change impact, RCA draft, lessons distiller, control mapping) — each with input / output / human review / approval / hallucination risk / guardrails / audit / privacy | Product + Backend + Security | AI roadmap + provider procurement |
| 18 | `17-Frontend-Requirements/` | `frontend-requirements.md` | MD | Spec-level FE breakdown: nav, pages, components, table columns, form fields, buttons, filters, modals, wizards, dashboard widgets, empty states, validation, AR/EN labels, RTL behaviour, RBAC visibility, status badges, evidence upload, signature, report export | Frontend + UX | Rebuild / extend the UI |
| 19 | `18-Excel-Workbook/` | `workbook-spec.md` + `tss-grc-implementation-package.xlsx` | MD + XLSX | 23-sheet workbook: Module Catalogue, Workflow Catalogue, Workflow Steps, Role Matrix, Approval Matrix, Data Objects, Entity Relationships, UI Screens, UI Fields, Evidence Library, Regulatory Mapping, Controls Library, Risk / BIA / Supplier / PDPL / NCA / Incident / Corrective-Action registers, Reports Catalogue, Automation Rules, Test Cases, Sample Data | BA / Config consultant / DBA / QA | Implementation config + seed import |

---

## Cross-references back into the repo

| Topic | Source of truth |
|---|---|
| Gap analysis (40/40 capabilities) | `docs/saudi-grc-gap-analysis.md` |
| Module status snapshot | `STATUS.md` |
| Sprint plans | `sprints/sprint-{1,2,3,4-convergence}.md` |
| Original session summary | `SESSION_SUMMARY.md` |
| Repo overview | `README.md` |
| Backend modules | `tss-grc-backend/src/main/java/com/tss/grc/module/` (17 modules) |
| Frontend pages | `tss-grc-ui-v2/src/pages/` (80+ pages) |
| Flyway migrations | `tss-grc-backend/src/main/resources/db/migration/sqlserver/` (V1..V54) |
| Translations | `tss-grc-ui-v2/src/translations/{en,ar}.ts` (5,000+ keys) |
| Live OpenAPI spec | `http://localhost:8084/v3/api-docs` (dev profile) |
| Mojibake repair | `fix_arabic_mojibake.py` |

---

## Status — what was checked vs. spec

| Spec requirement | Status | Evidence |
|---|---|---|
| OUTPUT 1 Product Blueprint | ✓ DONE | `01-Product-Blueprint/product-blueprint.md` |
| OUTPUT 2 Module Architecture (10 modules) | ✓ DONE | `02-Module-Architecture/module-architecture.md` |
| OUTPUT 3 Workflow Catalogue | ✓ DONE | `03-Workflow-Catalogue/workflow-catalogue.md` |
| OUTPUT 4 Data Model | ✓ DONE | `04-Data-Model/data-model.md` |
| OUTPUT 5 UI Screens | ✓ DONE | `05-UI-Screens/ui-screens.md` |
| OUTPUT 6 Evidence Library | ✓ DONE | `06-Evidence-Library/evidence-library.md` |
| OUTPUT 7 Regulatory Mapping | ✓ DONE | `07-Regulatory-Mapping/regulatory-mapping.md` |
| OUTPUT 8 Workflow Lifecycles | ✓ DONE | `03-Workflow-Catalogue/workflow-lifecycles.md` |
| OUTPUT 9 RBAC | ✓ DONE | `08-RBAC/rbac-matrix.md` |
| OUTPUT 10 Automation Rules | ✓ DONE | `09-Automation-Rules/automation-rules.md` |
| OUTPUT 11 Reports & Dashboards | ✓ DONE | `10-Reports-Dashboards/reports-dashboards.md` |
| OUTPUT 12 Implementation Backlog | ✓ DONE | `11-Implementation-Backlog/implementation-backlog.md` |
| OUTPUT 13 File Output Plan | ✓ DONE | this README |
| OUTPUT 14 Developer Handoff | ✓ DONE | `12-Developer-Handoff/developer-handoff.md` |
| OUTPUT 15 Test Cases | ✓ DONE | `14-Test-Cases/test-cases.md` |
| OUTPUT 16 Sample Seed Data | ✓ DONE | `15-Sample-Data/sample-seed-data.md` |
| OUTPUT 17 AI-Assisted Features | ✓ DONE | `16-AI-Features/ai-features.md` |
| OUTPUT 18 Frontend Implementation Requirements | ✓ DONE | `17-Frontend-Requirements/frontend-requirements.md` |
| OUTPUT 19 Excel Workbook (23 sheets + .xlsx) | ✓ DONE | `18-Excel-Workbook/workbook-spec.md` + `tss-grc-implementation-package.xlsx` |
| API spec | ✓ live + summary | `13-API-Specification/api-specification.md` + `/v3/api-docs` |

**All 16 outputs present.** Each is grounded in the actual platform
code, schemas, migrations, and existing internal documentation; no
content is inferred or invented. Placeholder IDs (where official
control numbers are awaited) are clearly tagged with the
`*-PLACEHOLDER-NNN` convention per the spec.

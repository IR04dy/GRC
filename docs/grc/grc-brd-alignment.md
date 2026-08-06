# BRD Alignment — GRC.html vs Current Platform
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> Mapping: HTML reference requirements ↔ Current codebase state  
> Date: 2026-04-08  
> Sprints: 2, 3, 4 (Sprint 1 = complete, Sprint 1.5 = complete)

---

## Status Legend

| Symbol | Meaning |
|---|---|
| ✅ | Exists and matches HTML |
| 🟡 | Partially exists, needs enhancement |
| ❌ | Missing — not implemented |

---

## A. Navigation & Shell

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| RTL sidebar (268px, dark green) | Sidebar CSS | 🟡 Present, different color scheme | P1 | 2 |
| All 30 module links in sidebar | `buildSidebar()` nav config | 🟡 Only 8 nav items | P1 | 2 |
| Sidebar section grouping (Main/Modules/Tools/Settings) | `sec:t('sec_main')` | 🟡 Partial (2 sections) | P1 | 2 |
| Dynamic badge counts on nav items | `badge:DB.get('risks')...` | ❌ Missing | P2 | 3 |
| Language toggle (AR/EN) in header | Header buttons | 🟡 Present but not persisted | P1 | 2 |
| Dark mode toggle | `body.dark` CSS | ❌ Missing toggle button | P2 | 3 |
| User avatar + name in sidebar | `sb-user` div | 🟡 Basic user display | P2 | 2 |
| Notification bell with unread count | Header `notif-btn` | 🟡 Basic bell, no count | P1 | 2 |
| Page breadcrumb in header | `.pg-bc` div | ❌ Missing | P2 | 3 |
| Tajawal font (Arabic-first) | CSS font-family | ❌ Using Bootstrap default | P1 | 2 |

---

## B. Dashboard

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Welcome banner (gradient, date, greeting) | `.welcome` div | 🟡 Basic hero strip exists | P1 | 2 |
| KPI: Total Risks with critical count | Dashboard KPIs | 🟡 Basic risk count exists | P1 | 2 |
| KPI: Compliance Rate % | Dashboard KPIs | ❌ Not shown | P1 | 2 |
| KPI: Open Incidents | Dashboard KPIs | ❌ Not shown | P1 | 2 |
| KPI: Critical Controls | Dashboard KPIs | ❌ Not shown | P2 | 2 |
| KPI: BCM Resilience | Dashboard KPIs | ❌ Not shown | P2 | 3 |
| KPI: PDPL Pending DSR | Dashboard KPIs | ❌ Not shown | P2 | 3 |
| Risk Heatmap widget (5×5) | `renderHeatmap()` | ❌ Missing on dashboard | P1 | 2 |
| Risk trend chart (line, 6 months) | Dashboard chart | 🟡 Chart exists, different data | P1 | 2 |
| Compliance scorecard radar chart | Dashboard radar | ❌ Missing | P2 | 3 |
| Recent alerts/notifications widget | Dashboard alerts | ❌ Missing | P1 | 2 |
| My tasks widget | Dashboard tasks | ❌ Missing | P2 | 3 |
| Clickable KPIs → module navigation | `onclick="showPage()"` | ❌ Missing | P2 | 3 |

---

## C. ERM Module

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Risk Register with full columns | `renderERM()` | 🟡 Basic CRUD exists | P1 | 2 |
| Risk Heatmap tab (5×5 interactive) | Heat Map tab | ❌ Missing | P1 | 2 |
| KRI Dashboard tab | KRI tab | ❌ Missing | P2 | 3 |
| Risk Appetite Statement tab | Appetite tab | ❌ Missing | P3 | 4 |
| Treatment strategy field (Mitigate/Accept/Transfer/Avoid) | Risk form | ❌ Not in current form | P1 | 2 |
| Risk score = likelihood × impact (auto-calc) | Risk form logic | ❌ Manual entry | P1 | 2 |
| Owner field on risk | Risk table | 🟡 Basic owner field | P1 | 2 |
| Status badges (Open/Mitigated/Accepted) | Risk table | 🟡 Basic status | P2 | 2 |
| Risk category (8 types) | Risk form | 🟡 Basic categories | P1 | 2 |
| Search/filter by category/status | Risk page | 🟡 Basic search only | P1 | 2 |

---

## D. Compliance Module (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Control Library (CRUD) | `renderCompliance()` | ❌ Not implemented | P1 | 2 |
| Control fields: code, name, framework, domain, maturity, status | Compliance table | ❌ Missing | P1 | 2 |
| Multi-framework support (ISO 27001/NCA ECC/PDPL/ISO 22301) | Framework filter | ❌ Missing | P1 | 2 |
| Compliance assessment records | Assessments tab | ❌ Missing | P1 | 2 |
| Findings management | Findings tab | ❌ Missing | P1 | 2 |
| Compliance scorecard (radar per framework) | Scorecard tab | ❌ Missing | P2 | 3 |
| Internal audit records | Audit tab | ❌ Missing | P2 | 3 |
| External audit records | External tab | ❌ Missing | P2 | 3 |
| CAPA management | CAPA sub-module | ❌ Missing | P2 | 3 |

---

## E. Cybersecurity Module (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Incident register (CRUD) | `renderCyber()` | ❌ Not implemented | P1 | 2 |
| Asset inventory | Assets tab | ❌ Missing | P2 | 3 |
| Controls (cyber-specific) | Controls tab | ❌ Missing | P2 | 3 |
| Cyber dashboard KPIs | Cyber dashboard | ❌ Missing | P2 | 3 |
| SIEM integration stubs | Integrations | ❌ Missing | P3 | 4 |

---

## F. Incident Management (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Incident register (title/severity/status/category) | `renderIncidentMgmt()` | ❌ Not implemented | P1 | 2 |
| Incident timeline per record | Timeline tab | ❌ Missing | P2 | 3 |
| Post-incident review | Lessons Learned | ❌ Missing | P3 | 4 |
| Severity distribution chart | Dashboard | ❌ Missing | P2 | 3 |
| Assignee + resolution date | Incident form | ❌ Missing | P1 | 2 |

---

## G. BCM Module (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| BIA register (process/RTO/RPO/MTPD/MBCO) | `renderBCM()` | ❌ Not implemented | P1 | 3 |
| BCP/DRP plans CRUD | Plans tab | ❌ Missing | P1 | 3 |
| Crisis management contacts | Crisis tab | ❌ Missing | P2 | 3 |
| Resilience metrics | Metrics tab | ❌ Missing | P2 | 4 |

---

## H. PDPL/Privacy Module (PARTIAL)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| RoPA records (processing activities) | `renderPDPL()` | ❌ Not implemented | P1 | 3 |
| DSR management (+30 day deadline) | DSR tab | ❌ Missing | P1 | 3 |
| PIA/DPIA records | PIA tab | ❌ Missing | P1 | 3 |
| Data breach registry | Breaches tab | ❌ Missing | P2 | 3 |
| SDAIA notification flag | Breach form | ❌ Missing | P2 | 3 |

---

## I. Data Governance (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Data Catalog (CRUD) | `renderDataGov()` | ❌ Not implemented | P2 | 3 |
| SDAIA classification levels | Classification tab | ❌ Missing | P2 | 3 |
| Data quality KPI dashboard | Quality tab | ❌ Missing | P2 | 4 |
| PII indicator on assets | Asset form | ❌ Missing | P2 | 3 |

---

## J. Evidence & Policies (NEW)

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Evidence register by domain | `renderEvidence()` | ❌ Not implemented | P1 | 3 |
| File upload for evidence | Evidence form | ❌ Missing | P2 | 3 |
| Policy CRUD with versioning | `renderPolicies()` | ❌ Not implemented | P1 | 3 |
| Policy lifecycle (Draft→Approved) | Policy status | ❌ Missing | P2 | 3 |

---

## K. Audit & Reporting

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Internal audit plan CRUD | `renderAudit()` | ❌ Not implemented | P2 | 3 |
| Audit Trail (system event log) | `renderAuditTrail()` | ❌ Not implemented | P2 | 3 |
| NCA Controls Mapping | `renderNCAMapping()` | ❌ Not implemented | P2 | 4 |
| Report generation (PDF) | Reports page | ❌ Not implemented | P1 | 4 |
| TPRM vendor register | `renderTPRM()` | ❌ Not implemented | P2 | 3 |

---

## L. Advanced Modules

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Risk Scenarios + Monte Carlo | `renderScenarios()` | ❌ Not implemented | P2 | 4 |
| Compliance Calendar | `renderCompCalendar()` | ❌ Not implemented | P2 | 4 |
| Training & Awareness | `renderTraining()` | ❌ Not implemented | P2 | 4 |
| Maturity Model | `renderMaturity()` | ❌ Not implemented | P2 | 4 |
| Board Report | `renderBoard()` | ❌ Not implemented | P2 | 4 |
| Change Management | `renderChangeMgmt()` | ❌ Not implemented | P3 | 4 |
| Supply Chain Risk | `renderSupplyChain()` | ❌ Not implemented | P3 | 4 |
| PbD / OWASP | `renderPbDOWASP()` | ❌ Not implemented | P3 | 4 |
| Risk Council | `renderRiskCouncil()` | ❌ Not implemented | P3 | 4 |
| Integrations page | `renderIntegrations()` | ❌ Not implemented | P3 | 4 |
| Workflow engine | `renderWorkflow()` | ❌ Not implemented | P2 | 4 |
| Notification matrix in Admin | Admin > Notifications tab | ❌ Missing | P2 | 3 |

---

## M. Design System Gaps

| Requirement | HTML Source | Current State | Priority | Sprint |
|---|---|---|---|---|
| Tajawal font (Google Fonts) | CSS `:root` | ❌ Bootstrap default | P1 | 2 |
| CSS design tokens (--primary, --accent, etc.) | `:root` | 🟡 Different tokens | P1 | 2 |
| Green color scheme (primary #0f4c35) | Design system | 🟡 Different primary | P1 | 2 |
| KPI card top-accent bar (3px gradient) | `.kpi::before` | 🟡 Different styling | P1 | 2 |
| Heatmap component (5×5 grid) | `.hm-cell` CSS | ❌ Missing | P1 | 2 |
| Toast notification system | `.toast` CSS | 🟡 Basic, different placement | P2 | 2 |
| Dark mode support | `body.dark` | ❌ Missing | P2 | 3 |
| Print CSS | `@media print` | ❌ Missing | P3 | 4 |

---

## Summary Stats

| Category | ✅ Exists | 🟡 Partial | ❌ Missing | Total |
|---|---|---|---|---|
| Navigation/Shell | 0 | 7 | 3 | 10 |
| Dashboard | 0 | 3 | 9 | 12 |
| ERM Module | 0 | 6 | 4 | 10 |
| Compliance (NEW) | 0 | 0 | 9 | 9 |
| Cybersecurity (NEW) | 0 | 0 | 5 | 5 |
| Incident Mgmt (NEW) | 0 | 0 | 5 | 5 |
| BCM (NEW) | 0 | 0 | 4 | 4 |
| PDPL (NEW) | 0 | 0 | 5 | 5 |
| Data Governance (NEW) | 0 | 0 | 4 | 4 |
| Evidence & Policies (NEW) | 0 | 0 | 4 | 4 |
| Audit & Reporting | 0 | 0 | 5 | 5 |
| Advanced Modules | 0 | 0 | 12 | 12 |
| Design System | 0 | 4 | 4 | 8 |
| **TOTAL** | **0** | **20** | **73** | **93** |

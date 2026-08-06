# GRC Platform — 3-Sprint Delivery Plan (Post Sprint 1.5)
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> Reference: GRC.html analysis + BRD alignment  
> Sprints are numbered 2, 3, 4 (Sprints 1 and 1.5 are complete)  
> Each sprint = ~2 weeks  

---

## Sprint 2 — Foundation Alignment + Core Modules
**Theme**: Close the visual design gap, implement Compliance and Incident Management, enhance Risk module  
**Goal**: Platform looks and feels like the HTML reference, has working Compliance and Incident modules  

### Backend (Sprint 2)
| Item | Priority |
|---|---|
| Compliance module: `Control` entity + CRUD API | P1 |
| Compliance module: `ComplianceAssessment` entity + API | P1 |
| Compliance module: `AuditFinding` entity + API | P1 |
| Incident module: `Incident` entity + CRUD API | P1 |
| Dashboard API: compliance rate, incident count, control stats | P1 |
| Risk module: treatment field + score auto-calc | P1 |
| Flyway migrations: controls, assessments, findings, incidents tables | P1 |
| Seed data: sample controls, assessments, incidents | P2 |

### Frontend (Sprint 2)
| Item | Priority |
|---|---|
| Style overhaul: Tajawal font, green design tokens, CSS alignment with HTML | P1 |
| Sidebar: all 30 modules, section grouping, badge counts | P1 |
| Dashboard rebuild: welcome banner, 6 KPIs, heatmap widget, recent alerts | P1 |
| Risk module: heatmap tab (5×5 interactive), KRI tab skeleton | P1 |
| Compliance module pages: controls library, assessments, findings | P1 |
| Incident Management page: register table, add/edit modal | P1 |
| Notification badge in header (unread count) | P2 |
| Language toggle (AR/EN) persistence | P2 |
| Module stub pages for: BCM, PDPL, DataGov, Evidence, Policies, Audit, TPRM, NCA | P2 |

### Database (Sprint 2)
```
V3__compliance_module.sql   — controls, assessments, findings tables
V4__incident_module.sql     — incidents, incident_timeline tables
V5__seed_sprint2.sql        — sample controls, incidents for dev
```

### Acceptance Criteria (Sprint 2)
- [ ] Dashboard shows 6 KPI cards populated from API
- [ ] Risk heatmap renders correctly on risk page
- [ ] Compliance module: add/edit/delete control works end-to-end
- [ ] Compliance module: assessment can be created and linked to framework
- [ ] Incident can be created with severity, category, assignee
- [ ] Sidebar shows all 30 module links grouped correctly
- [ ] Dashboard loads in <2 seconds
- [ ] Arabic mode: sidebar/tables/modals correctly RTL

---

## Sprint 3 — Secondary Modules
**Theme**: BCM, PDPL, Data Governance, Evidence, Policies, TPRM, Audits  
**Goal**: All regulatory compliance modules are functional  

### Backend (Sprint 3)
| Item | Priority |
|---|---|
| BCM module: `BIAProcess`, `BCPlan` entities + API | P1 |
| PDPL module: `RoPA`, `DSR`, `PIA`, `DataBreach` entities + API | P1 |
| Data Governance: `DataAsset` entity + API | P1 |
| Evidence: `Evidence` entity + file upload API | P1 |
| Policies: `Policy` entity + versioning API | P1 |
| TPRM: `Vendor`, `VendorAssessment` entities + API | P2 |
| Audit Trail: system event logging service | P2 |
| Internal Audit: `AuditPlan`, `AuditRecord` entities + API | P2 |
| Notification matrix: admin configuration API | P2 |
| Flyway migrations: BCM, PDPL, DataGov, Evidence, Policies, TPRM | P1 |

### Frontend (Sprint 3)
| Item | Priority |
|---|---|
| BCM page: BIA table, BCP/DRP plan CRUD | P1 |
| PDPL page: RoPA, DSR (with +30d deadline), PIA, Breaches | P1 |
| Data Governance page: catalog, SDAIA classification, quality KPIs | P1 |
| Evidence page: domain tabs, upload, control linking | P1 |
| Policies page: register, lifecycle status, review dates | P1 |
| TPRM page: vendor register, compliance matrix | P2 |
| Audit Trail page: event log, filter, chart | P2 |
| Internal Audit page: plan, findings, CAPA | P2 |
| Admin: notification matrix (event × channel toggle grid) | P2 |
| Dark mode toggle implementation | P3 |

### Database (Sprint 3)
```
V6__bcm_module.sql
V7__pdpl_module.sql  
V8__datagov_module.sql
V9__evidence_policies.sql
V10__tprm_module.sql
V11__audit_trail.sql
V12__seed_sprint3.sql
```

### Acceptance Criteria (Sprint 3)
- [ ] BCM: BIA process can be added with RTO/RPO values
- [ ] PDPL: DSR request auto-calculates deadline as +30 days
- [ ] Evidence: file can be uploaded and linked to a control
- [ ] Policy: version tracking works (1.0 → 1.1 on update)
- [ ] TPRM: vendor compliance score can be updated
- [ ] Audit trail: all CRUD operations are logged
- [ ] PDPL: breach can be marked as SDAIA-notified

---

## Sprint 4 — Advanced Modules + Reporting
**Theme**: Analytics, board reports, scenarios, training, maturity, NCA mapping, integrations  
**Goal**: Complete platform with reporting, advanced risk tools, and executive dashboards  

### Backend (Sprint 4)
| Item | Priority |
|---|---|
| Reports service: generate executive/risk/compliance/cyber reports | P1 |
| NCA controls mapping: framework control sets + compliance calc | P1 |
| Training: `TrainingProgram`, `Enrollment` entities + API | P2 |
| Maturity model: `MaturityAssessment` entity + domain scoring | P2 |
| Risk scenarios: `Scenario` entity + API | P2 |
| Workflow engine: `WorkflowTemplate`, `Task` entities + API | P2 |
| Compliance calendar: event management API | P2 |
| Board report: aggregated KPI endpoint | P2 |
| Integrations: placeholder connector registry | P3 |
| Change management: `ChangeRequest` entity + API | P3 |
| Supply chain: `Supplier` entity + API | P3 |

### Frontend (Sprint 4)
| Item | Priority |
|---|---|
| Reports page: all report types, PDF export | P1 |
| NCA Mapping page: ECC/CSCC/DCC frameworks with controls | P1 |
| Board/Executive report page (PDF-ready) | P1 |
| Training page: programs, enrollments, progress | P2 |
| Maturity model page: radar chart, gap analysis | P2 |
| Risk Scenarios page: register + Monte Carlo simulation UI | P2 |
| Compliance Calendar: month view + event management | P2 |
| Workflow page: templates, tasks, escalation rules | P2 |
| Risk Council page | P3 |
| PbD / OWASP page | P3 |
| Change Management page | P3 |
| Supply Chain page | P3 |
| Integrations page (status cards) | P3 |
| Templates page | P3 |

### Acceptance Criteria (Sprint 4)
- [ ] Executive report PDF exports correctly in Arabic
- [ ] NCA ECC framework shows all domains with compliance % 
- [ ] Training program completion rate calculated correctly
- [ ] Maturity radar chart shows current vs target per domain
- [ ] Risk scenario Monte Carlo simulation runs and returns results
- [ ] Compliance calendar shows events with color-coding
- [ ] Workflow task can be assigned, updated, and escalated

---

## Module Coverage Summary

| Module | Sprint 2 | Sprint 3 | Sprint 4 |
|---|---|---|---|
| Dashboard | ✅ Rebuild | | |
| ERM | ✅ Enhance | | |
| Compliance | ✅ New | | |
| Incident Management | ✅ New | | |
| Cybersecurity (incidents only) | ✅ via Incident | | |
| BCM | | ✅ New | |
| PDPL / Privacy | | ✅ New | |
| Data Governance | | ✅ New | |
| Evidence | | ✅ New | |
| Policies | | ✅ New | |
| TPRM | | ✅ New | |
| Audit Trail | | ✅ New | |
| Internal Audit | | ✅ New | |
| Reports | | | ✅ New |
| NCA Mapping | | | ✅ New |
| Board/Executive | | | ✅ New |
| Training | | | ✅ New |
| Maturity Model | | | ✅ New |
| Risk Scenarios | | | ✅ New |
| Compliance Calendar | | | ✅ New |
| Workflow Engine | | | ✅ New |
| Risk Council | | | 🟡 Stub |
| Change Management | | | 🟡 Stub |
| Supply Chain | | | 🟡 Stub |
| PbD / OWASP | | | 🟡 Stub |
| Integrations | | | 🟡 Stub |
| Templates | | | 🟡 Stub |
| Notifications | ✅ Badge | ✅ Matrix | ✅ Full |
| Admin | ✅ Enhance | ✅ Notif Matrix | |
| Licensing | 🟡 Stub | | ✅ Full |

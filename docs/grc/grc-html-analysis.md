# GRC.html — Comprehensive Platform Analysis

> Source: `C:\AYMAN\Gafaar-GRC-HTML\GRC.html` (20,233 lines)  
> Analyzed: 2026-04-08  
> Language: Arabic (RTL primary) / English bilingual  
> Framework: Vanilla JS SPA, Chart.js 4, Tajawal font, IBM Plex Mono  

---

## 1. Visual Design System

| Variable | Value |
|---|---|
| Primary | `#0f4c35` (dark green) |
| Primary light | `#1a6b3c` / `#2e8b57` |
| Accent | `#e8a020` (amber) |
| Danger | `#dc2626` |
| Warning | `#d97706` |
| Info | `#2563eb` |
| Success | `#16a34a` |
| Background | `#f0f4f1` (pale green-grey) |
| Surface | `#ffffff` |
| Font | Tajawal (Arabic-first) + IBM Plex Mono |
| Direction | RTL default, LTR via `body.ltr` |
| Sidebar width | 268px |
| Header height | 60px |
| Border radius | 14px cards, 9px small |

**Dark mode**: Full CSS variable override (`body.dark`).  
**Print CSS**: Sidebar/header hidden, charts preserved.  
**Animations**: Staggered card entrance, progress bar fill, count-up stats, float effects, shimmer loading.

---

## 2. Navigation Structure

### Sections & Pages

| Section | Page ID | Arabic Label | English Label | Icon |
|---|---|---|---|---|
| **رئيسي** (Main) | `dashboard` | لوحة التحكم | Dashboard | 📊 |
| | `executive` | التقرير التنفيذي | Executive Report | 👔 |
| | `about` | نبذة عن المنصة | About Platform | ℹ️ |
| **وحدات** (Modules) | `erm` | إدارة المخاطر المؤسسية | Enterprise Risk Mgmt | ⚠️ |
| | `cyber` | الأمن السيبراني | Cybersecurity | 🔒 |
| | `bcm` | استمرارية الأعمال | Business Continuity | 🔄 |
| | `datagov` | حوكمة البيانات | Data Governance | 🗄️ |
| | `pdpl` | حماية البيانات الشخصية | Personal Data Protection | 🔏 |
| | `compliance` | الامتثال والتدقيق | Compliance & Audit | ✅ |
| **أدوات** (Tools) | `evidence` | إدارة الأدلة | Evidence Management | 📎 |
| | `policies` | السياسات والوثائق | Policies & Documents | 📋 |
| | `workflow` | إدارة سير العمل | Workflow Management | ⚙️ |
| | `riskcouncil` | مجلس المخاطر | Risk Council | 👔 |
| | `scenarios` | سيناريوهات المخاطر | Risk Scenarios | 🎭 |
| | `audit` | التدقيق الداخلي | Internal Audit | 🔍 |
| | `tprm` | الأطراف الثالثة | Third-Party Risk | 🤝 |
| | `audittrail` | سجل التدقيق | Audit Trail | 📜 |
| | `ncamap` | خارطة NCA | NCA Mapping | 🗺️ |
| | `incidentmgmt` | إدارة الحوادث | Incident Management | 🚨 |
| | `compcal` | تقويم الامتثال | Compliance Calendar | 📅 |
| | `training` | التدريب | Training | 📚 |
| | `maturity` | نموذج النضج | Maturity Model | 🏆 |
| | `changemgmt` | إدارة التغيير | Change Management | 🔄 |
| | `supplychain` | سلسلة الإمداد | Supply Chain | 🔗 |
| | `pbdowasp` | PbD · OWASP | PbD · OWASP | 🛡️ |
| | `reports` | التقارير والتحليلات | Reports & Analytics | 📄 |
| | `integrations` | التكاملات | Integrations | 🔗 |
| | `templates` | القوالب والنماذج | Templates & Forms | 📁 |
| **إعدادات** (Settings) | `licensing` | التراخيص والاشتراكات | Licensing | 🏷️ |
| | `admin` | الإدارة والإعدادات | Administration | 🛠️ |
| | `notifications` | الإشعارات | Notifications | 🔔 |

Dynamic badges: ERM shows open critical risks count. Cyber shows open/investigating incidents count. PDPL shows pending DSR count. Evidence and Policies show record counts.

---

## 3. Module-by-Module Analysis

### 3.1 Dashboard (`page-dashboard`)

**Welcome Banner**: Gradient background, greeting text, current date display, shield emoji decoration.

**KPI Cards (6)**:
| KPI | Subtext |
|---|---|
| Total Risks | with critical count |
| Compliance Rate (%) | framework score |
| Open Incidents | with investigating count |
| Critical Controls | status indicator |
| BCM Resilience | process count |
| PDPL Pending DSR | request count |

**Widgets**:
- **Risk Heatmap** (5×5 matrix): Visual likelihood × impact grid, color-coded cells (green → red), click to drill into ERM module
- **Risk Trend Chart**: Line chart (last 6 months), stacked by severity
- **Recent Alerts/Notifications**: Latest 5 items with severity badges, time-ago, link to module
- **My Tasks**: Personal task queue with priority badges and due dates
- **Compliance Scorecard**: Radar chart (ISO 27001, NCA ECC, PDPL, ISO 22301, ISO 31000, SDAIA)

---

### 3.2 Executive Report (`page-executive`)

**Purpose**: Board/C-Level summary view.

**Content**:
- Org-level KPI summary (6 metrics)
- Top 5 critical risks table (ID, name, owner, score, treatment)
- Compliance scores per framework (bar chart + table)
- Open audit findings count per severity
- Incident status summary
- Upcoming deadlines (next 30 days)
- PDF export button (html2pdf.js)

---

### 3.3 ERM — Enterprise Risk Management (`page-erm`)

**Tabs**: Risk Register | Heat Map | KRI Dashboard | Risk Appetite

**Risk Register Table Columns**:
`Code | Name | Category | Likelihood (1-5) | Impact (1-5) | Score | Owner | Treatment | Status | Actions`

**Categories**: Cyber, Operational, Compliance, Technology, HR, Financial, Strategic, Regulatory

**Statuses**: Open, Mitigated, Accepted, Transferred, Closed

**Treatment Strategies**: Mitigate, Accept, Transfer, Avoid

**Heat Map**: 5×5 interactive grid, color cells by risk concentration (count), hover tooltip shows risk names

**KRI Dashboard**:
- Total Risks, Critical Risks Count, Average Risk Score, Treated %
- KRI line chart (trend over months)
- KRI table: Indicator, Value, Threshold, Trend, Status (breach/ok)

**Risk Appetite Statement**:
- Per-category appetite level: Zero Tolerance / Very Low / Low / Medium / High
- Visual bars showing current vs appetite

**Modal Form** (Add/Edit Risk):
`Code | Name | Category | Description | Likelihood | Impact | Owner | Treatment | Notes | Attachments`

**Search/Filter**: By name, category, status, owner, score range

---

### 3.4 Cybersecurity (`page-cyber`)

**Tabs**: Incidents | Assets | Controls | Cyber Dashboard

**Incidents Table**:
`Code | Title | Severity (Critical/High/Medium/Low) | Status (Open/Investigating/Resolved) | Date | Owner | Actions`

**Assets Inventory**:
`Asset Name | Type (Server/Workstation/Network/Application/Cloud/Mobile) | Criticality | Owner | IP Address | OS | Status`

**Controls Library** (Cyber):
`Code | Name | Framework (ISO 27001/NCA ECC/NIST CSF) | Domain | Maturity (1-5) | Status (Implemented/Partial/Planned) | Owner`

**Cyber Dashboard KPIs**:
- Open Incidents, Critical Incidents, Controls Implemented %, Registered Assets
- Charts: Incident severity (doughnut), Control maturity (radar), Asset criticality (bar)

**Frameworks Mapped**: ISO 27001, NCA ECC, NIST CSF, MITRE ATT&CK

---

### 3.5 BCM — Business Continuity (`page-bcm`)

**Tabs**: BIA | BCP/DRP Plans | Crisis Management | Resilience Metrics

**BIA Table**:
`Process Name | Department | Criticality (Critical/High/Medium/Low) | RTO (hrs) | RPO (hrs) | MTPD (hrs) | MBCO (%) | Status`

**BCP/DRP Plans**:
`Plan Name | Type (BCP/DRP) | Version | Owner | Approved Date | Status (Draft/Approved/Tested)`

**Crisis Management**:
- Crisis response team contacts
- Escalation procedures
- Exercise testing log

**Resilience Metrics**:
- Overall resilience score (circular gauge)
- RTO achieved vs target (bar)
- Recovery test results

---

### 3.6 Data Governance (`page-datagov`)

**Tabs**: Data Catalog | SDAIA Classification | Data Quality KPIs

**Data Catalog**:
`Asset Name | Type (DB/File/API/App) | Classification (TS/C/R/P) | Owner | Location | PII Flag | Status`

**SDAIA Classification Levels**:
- سري للغاية (Top Secret) — severe national interest harm
- سري (Confidential) — organizational harm
- محدود (Restricted) — internal use only
- عام (Public) — free distribution

**Data Quality Metrics** (6 indicators, all ≥78%):
- Completeness, Accuracy, Consistency, Timeliness, Uniqueness, Validity
- Each with trend % and color indicator

---

### 3.7 PDPL — Personal Data Protection (`page-pdpl`)

**Tabs**: PIA/DPIA | RoPA | DSR | Data Breaches

**PIA/DPIA Records**:
`Project | Type (PIA/DPIA) | Risk Level | Owner | Date | Status (Draft/In Progress/Completed/Approved)`

**RoPA** (Record of Processing Activities):
`Activity | Purpose | Data Category | Legal Basis (Consent/Contract/Legal Obligation/Legitimate Interest/Public Interest) | Retention Period | Cross-Border Transfer`

**DSR** (Data Subject Requests):
`Requester | Type (Access/Delete/Correct/Portability/Object) | Request Date | Deadline (+30 days) | Status (Pending/In Progress/Completed/Rejected) | Handler`

**Data Breaches**:
`ID | Description | Severity | Affected Count | Discovery Date | SDAIA Notified (Y/N) | Status`

**KPIs**: Pending DSRs, Overdue DSRs, Completed PIA/DPIAs, Data Breach Count

---

### 3.8 Compliance (`page-compliance`)

**Tabs**: Control Library | Internal Audit | External Audit | Findings | Scorecard

**Control Library**:
`Code | Name | Framework | Domain | Maturity (1-5) | Status | Owner | Evidence`

**Frameworks Supported**: ISO 27001, NCA ECC, PDPL, ISO 22301, ISO 31000, SDAIA, COSO ERM, DAMA-DMBOK

**Internal Audits**:
`Audit # | Title | Type | Department | Auditor | Period | Findings Count | Rating | Status`

**External Audits**:
Grouped by framework/regulatory body with similar columns + certification status

**Findings**:
`Finding # | Title | Audit | Severity | Owner | CAPA | Due Date | Status (Open/In-Progress/Remediated/Verified/Closed)`

**Scorecard**: Radar chart showing compliance % per framework, trend data

**KPIs**: Overall Compliance %, Total Controls, Implemented Controls %, Open Findings, Overdue Findings

---

### 3.9 Workflow (`page-workflow`)

**Tabs**: Templates | Pending Tasks | Escalation Rules | My Tasks

**Workflow Templates**:
`Name | Module | SLA (days) | Steps (visual arrows) | Status (Active/Inactive)`

**Task Queue**:
`Title | Assignee | Module | Priority | Due Date | Status (Pending/In Progress/Completed/Overdue)`

**Escalation Rules**:
`Trigger Condition | Action | SLA Timer | Module | Status`
Example: "New Critical Risk → Notify Risk Manager & CISO → 2 hrs SLA"

---

### 3.10 Reports (`page-reports`)

**Report Types**:
1. Executive Summary
2. Risk Report (full register + stats)
3. Cybersecurity Report (incidents + controls)
4. BCM Report
5. Compliance Scorecard (per framework)
6. PDPL Report
7. Evidence Report
8. Policies Report
9. Audit Trail Export
10. Custom Report (configurable)

**Filters**: Date range (7d / 30d / Quarter / Year / All), Framework, Module, Status

**Export Formats**: PDF (html2pdf.js), Excel, Word

---

### 3.11 Evidence Management (`page-evidence`)

**Domains** (color-coded tabs): Cyber | Infrastructure | Policies | Compliance | Data Governance

**Evidence Table**:
`Type | Title | Control Reference | Framework | Classification (TS/C/I/P) | Status (Approved/In Progress/Pending/Expired) | Review Due | Uploaded By`

**Actions**: Upload file, Link to control, Set expiry, Change status, Download

---

### 3.12 Policies & Documents (`page-policies`)

**Domains**: Security | Compliance | Risk | BCM | Data Protection

**Policy Records**:
`Code | Title | Domain | Version | Owner | Last Review | Status (Draft/Review/Approved/Effective/Superseded) | Next Review`

**Actions**: View, Edit, Approve, Archive, Set review reminder

---

### 3.13 Scenarios (`page-scenarios`)

**Tabs**: Register | Map | Simulation | Financial Impact | Comparison

**Scenario Register**:
`Name | Category (Cyber/Operational/Financial/Regulatory/Strategic/BCM) | Likelihood (1-5) | Impact (1-5) | Score | Status`

**Simulation** (Monte Carlo):
- Configure: Likelihood %, Impact (SAR), Control Effectiveness %, Iterations (100–10,000)
- Output: P50/P95 financial impact, Risk level, Timeline (Trigger→Detection→Containment→Response→Recovery)

**Financial Impact**: Distribution chart (bar), Cumulative curve

---

### 3.14 Internal Audit (`page-audit`)

**Tabs**: Audit Plan | External Audit | Findings | Remediation | Dashboard | CAPA | Timeline

**Audit Records**:
`# | Title | Type (Compliance/Operational/Financial/IT/Special) | Department | Auditor | Period | Findings | Rating (Satisfactory/Needs Improvement/Unsatisfactory) | Status`

**CAPA** (Corrective and Preventive Actions):
`# | Finding | Type (Corrective/Preventive) | Description | Owner | Due Date | Status`

**Dashboard KPIs**: Total Audits, Completion Rate, Open Findings, Overdue

---

### 3.15 Third-Party Risk (`page-tprm`)

**Tabs**: Vendor Register | Risk Assessment | Compliance Matrix | Reviews

**Vendor Register**:
`Name | Category (Tech/Consulting/Cloud/Outsourcing/Financial/Logistics) | Risk Level | Contract Value (SAR) | Data Access (Y/N) | Compliance Score % | Status`

**Compliance Matrix**: NCA/PDPL/ISO 27001/SOC 2 certification grid per vendor

**Review Schedule**: Overdue / Upcoming / Historical with compliance scores

---

### 3.16 Audit Trail (`page-audittrail`)

**KPIs**: Total Events, Critical Events, Active Users, Most Active Module

**Event Log**:
`Timestamp | User | Action (Login/Create/Update/Delete/View/Export/Approve/Reject) | Module | Details | IP | Severity`

**Chart**: 7-day event trend (line)

---

### 3.17 NCA Controls Mapping (`page-ncamap`)

**Frameworks**: ECC 2-2024 | CSCC (Cloud) | DCC (Data) | TCC (Telecom)

**Per Framework**: Domain breakdown → Control details (ID, Title AR/EN, Status, Maturity)

**Visualization**: Circular gauge, stacked bar per domain

---

### 3.18 Incident Management (`page-incidentmgmt`)

**Records**:
`Title | Severity | Status (Open/Investigating/Resolved) | Category (Cyber/Data/Infrastructure/Access) | Assignee | Discovery Date | Resolution Date`

**Timeline**: Chronological event log per incident (Reported → Investigation → Contained → Resolved)

**Post-Incident Review**: Lessons learned documentation

---

### 3.19 Compliance Calendar (`page-compcal`)

**Calendar view** with month navigation, event type color-coding (Audits/Reports/Trainings/Certifications/Policies/Assessments).

**Event properties**: Date, Type, Title (AR+EN), Description

**Upcoming list**: Next 6 events with countdown (days to deadline), urgency coloring

---

### 3.20 Training & Awareness (`page-training`)

**Programs**:
`Name | Category (Security/Compliance/Risk) | Duration (hrs) | Enrolled | Completed | Passed | Deadline | Status`

**Tabs**: Dashboard | Programs | Enrollments | Reports

**Charts**: Program progress bars, Category doughnut

---

### 3.21 GRC Maturity Model (`page-maturity`)

**5 CMMI Levels**: Initial → Repeatable → Defined → Managed → Optimizing

**8 Domains**: Risk Management, Compliance, Information Security, Business Continuity, Governance, Incident Management, Third-Party Mgmt, Training & Awareness

**Output**: Current vs Target radar chart, Gap analysis table, Domain progress circles

---

### 3.22 Board Report (`page-board`)

Executive-focused: Top risks, compliance by framework, open findings, incident summary. PDF export.

---

### 3.23 Risk Council (`page-riskcouncil`)

**Content**: Risk Council governance structure, Resilience indicators (ring gauges), KPI bar charts, RACI matrix for risk accountability.

---

### 3.24 PbD · OWASP (`page-pbdowasp`)

**Privacy by Design**: 7 principles with expandable implementation guides.

**OWASP Top 10**: Each item with ID badge, expandable technical detail.

**Feature Flags**: Toggle UI for enabling/disabling platform features.

---

### 3.25 Change Management (`page-changemgmt`)

**Change Requests**: Title, Requester, Type (Risk/Control/Policy/Process/Technical), Impact Assessment, Approval status, Due Date.

**Workflow**: Proposed → Reviewed → Approved → Implemented → Closed

---

### 3.26 Supply Chain (`page-supplychain`)

**Supplier Map**: Dependency visualization, Tier 1/2/3 suppliers, risk level per supplier.

**Risk Assessment**: Contract value, criticality, geo-risk, compliance score.

---

### 3.27 Integrations (`page-integrations`)

**Integration cards**: 
- SIEM (Splunk, QRadar, Sentinel)
- Vulnerability Scanners (Nessus, Qualys)
- ITSM (ServiceNow, Jira)
- Directory (LDAP, Azure AD)
- Cloud (AWS, Azure, GCP)
- Communication (Teams, Slack)

**Status per integration**: Connected / Disconnected / Error

---

### 3.28 Admin (`page-admin`)

**Tabs**: Users | Roles & Permissions | System Settings | Notification Matrix | AI Agents | Org Setup | Security Panel | Data Migration | System Health

**Users**: CRUD, role assignment, enable/disable, password reset

**Roles**: Dynamic role creation, granular permissions per module/action

**Notification Matrix**: Event × Channel (In-App/Email/SMS/WhatsApp) toggle grid

**System Health**: DB stats, memory, active sessions, uptime

---

### 3.29 Notifications (`page-notifications`)

**Inbox list**: Title, module source, severity dot, time-ago, read/unread indicator

**Filter**: All / Unread / Critical / Module-specific

---

### 3.30 Licensing (`page-licensing`)

**3 Tiers**: Starter / Professional (popular) / Enterprise

**Per tier**: Price (SAR), Feature checklist, CTA button

---

## 4. Shared UI Components

| Component | Description |
|---|---|
| KPI Card | Top accent bar, icon, value (large mono), label, trend badge (up/down/neutral) |
| Heatmap | 5×5 grid, 8 color steps (hm-c1..hm-c8), x/y axis labels |
| Modal | Animated, 600px, header/body/footer sections, overlay blur |
| Toast | Bottom-left, 4 types (success/error/warning/info), border accent |
| Badge | 8 color variants (red/orange/yellow/green/blue/purple/gray/primary) |
| Progress Bar | Animated fill with shimmer, color variants |
| Confirm Dialog | Icon + title + message + Yes/Cancel buttons |
| Empty State | Icon + title + subtitle for zero-data scenarios |
| Tabs | Pill-style tabs with active animation |
| Domain Tabs | Larger domain selector buttons with count badges |
| RACI Badge | R/A/C/I color-coded square badges |
| Integration Card | Logo, name, desc, status dot + label |
| Audit Log Item | Icon, action, meta (user/timestamp) |
| Stat Card | Center value (gradient text), label |

---

## 5. Entity/Data Model Extracted

### Core Entities

| Entity | Key Fields |
|---|---|
| Risk | code, name, category, description, likelihood (1-5), impact (1-5), score, owner, treatment, status |
| RiskCategory | name, description, appetiteLevel |
| KRI | name, currentValue, threshold, trend, status |
| Incident | code, title, severity, category, status, assignee, discoveryDate, resolutionDate, timeline[] |
| Control | code, name, framework, domain, maturity (1-5), status, owner, evidenceLinks[] |
| ComplianceAssessment | framework, period, score, auditor, status, findings[] |
| AuditFinding | title, auditId, severity, owner, capa, dueDate, status |
| CAPA | findingId, type, description, owner, dueDate, status |
| Policy | code, title, domain, version, owner, reviewDate, status |
| Evidence | type, title, controlRef, framework, classification, status, reviewDue, filePath |
| Vendor | name, category, riskLevel, contractValue, dataAccess, complianceScore, status |
| DSR | requester, type, requestDate, deadline, status, handler |
| PIA | project, type, riskLevel, owner, date, status |
| RoPA | activity, purpose, dataCategory, legalBasis, retentionPeriod |
| DataBreach | description, severity, affectedCount, discoveryDate, sdaiaNotified, status |
| BIAProcess | name, department, criticality, rto, rpo, mtpd, mbco, status |
| BCPlan | name, type, version, owner, status |
| DataAsset | name, type, classification, owner, location, piiFlag |
| TrainingProgram | name, category, duration, enrolledCount, completedCount, passedCount, deadline, status |
| Workflow | name, module, sladays, steps[], status |
| Task | title, assignee, module, priority, dueDate, status |
| Notification | title, module, severity, message, read, createdAt |
| User | username, email, roles[], enabled, tenantId |
| Organisation | name, sector, country, licenseType |

---

## 6. Roles & Permissions (Implied)

| Role | Key Access |
|---|---|
| PLATFORM_ADMIN | All modules, system settings, user management |
| ORG_ADMIN | All modules within org, user management |
| RISK_MANAGER | ERM, Scenarios, Risk Council, Dashboard |
| CISO | Cyber, BCM, Compliance, Dashboard |
| COMPLIANCE_OFFICER | Compliance, Audit, NCA Mapping, Findings |
| PRIVACY_OFFICER | PDPL, Data Governance, Evidence |
| AUDITOR | Compliance, Evidence, Audit Trail, Reports |
| BCM_MANAGER | BCM, Scenarios, Training |
| VIEWER | Dashboard read-only, Reports |

---

## 7. Reporting Requirements

- PDF export (html2pdf.js) for all major reports
- Excel/Word export (implied)
- Date range filtering (7d / 30d / Quarter / Year / All)
- Per-framework filtering
- Per-module filtering
- Scheduled reports (admin config)
- Board report package
- Executive summary PDF

---

## 8. Arabic/RTL Requirements

- Primary language: Arabic (`lang="ar" dir="rtl"`)
- Font: Tajawal (Google Fonts, Arabic-optimized)
- Language toggle: AR/EN runtime switch (`body.ltr` class)
- All labels bilingual throughout
- Toast positioned bottom-left (RTL), bottom-right (LTR)
- Sidebar right-side (RTL), left-side (LTR)
- Table headers right-aligned (RTL), left-aligned (LTR)
- RACI, notification matrix: direction-aware
- Mono font: IBM Plex Mono (codes, numbers, dates)

---

## 9. Compliance Frameworks Covered

| Framework | Module |
|---|---|
| ISO 27001:2022 | Cyber, Compliance, ERM |
| NCA ECC 2-2024 | Cyber, Compliance, NCA Mapping |
| NCA CSCC | NCA Mapping |
| NCA DCC | NCA Mapping |
| PDPL (Saudi Personal Data Protection Law) | PDPL, Compliance |
| ISO 22301:2019 (BCM) | BCM, Compliance |
| ISO 31000:2018 (Risk) | ERM, Compliance |
| SDAIA | Data Governance, Compliance |
| COSO ERM | ERM, Compliance |
| DAMA-DMBOK | Data Governance |
| NIST CSF | Cyber |
| MITRE ATT&CK | Cyber |
| ISO 27701 (Privacy) | PDPL |
| GDPR | PDPL (cross-reference) |
| SOC 2 | TPRM |

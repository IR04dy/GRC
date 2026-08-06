# GRC.html — Deep Analysis v2 (Exhaustive Re-read)
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> Source: `C:\AYMAN\Gafaar-GRC-HTML\GRC.html` — 20,233 lines  
> Re-analyzed: 2026-04-09 from scratch  
> Primary language: Arabic RTL, bilingual toggle  

---

## 1. Design System (exact tokens)

| Token | Value |
|---|---|
| `--primary` | `#0f4c35` |
| `--primary-d` | `#08301f` |
| `--primary-l` | `#1a6b3c` |
| `--primary-ll` | `#2e8b57` |
| `--primary-pale` | `#e8f5ee` |
| `--accent` | `#e8a020` |
| `--accent-l` | `#ffd166` |
| `--danger` | `#dc2626` |
| `--warning` | `#d97706` |
| `--info` | `#2563eb` |
| `--success` | `#16a34a` |
| `--bg` | `#f0f4f1` |
| `--surface` | `#ffffff` |
| `--surface2` | `#f6f9f7` |
| `--border` | `#dce8e0` |
| `--text1` | `#0a1f14` |
| `--text2` | `#1f4a30` |
| `--text3` | `#5a8a6e` |
| `--text4` | `#9ab8a6` |
| `--sw` | `268px` (sidebar width) |
| `--hh` | `60px` (header height) |
| `--r` | `14px` (card radius) |
| `--rs` | `9px` (small radius) |
| Font primary | Tajawal (Arabic-first) |
| Font mono | IBM Plex Mono |
| `--grad` | `linear-gradient(135deg,#0f4c35 0%,#1a6b3c 55%,#2e8b57 100%)` |

**Dark mode**: Full CSS variable override via `body.dark`  
**LTR toggle**: `body.ltr` flips all directional CSS  
**Print CSS**: sidebar/header hidden, pages all visible  

---

## 2. Complete Navigation Structure

### Sidebar Sections (exact JS structure from `buildSidebar()`)

#### Section: رئيسي / Main
| ID | Icon | Label AR | Label EN |
|---|---|---|---|
| `dashboard` | 📊 | لوحة التحكم | Dashboard |
| `executive` | 👔 | التقرير التنفيذي | Executive Report |
| `about` | ℹ️ | نبذة عن المنصة | About Platform |

#### Section: وحدات / Modules
| ID | Icon | Label AR | Label EN | Badge |
|---|---|---|---|---|
| `erm` | ⚠️ | إدارة المخاطر المؤسسية | Enterprise Risk Mgmt | Open critical risks count |
| `cyber` | 🔒 | الأمن السيبراني | Cybersecurity | Open+Investigating incidents |
| `bcm` | 🔄 | استمرارية الأعمال | Business Continuity | — |
| `datagov` | 🗄️ | حوكمة البيانات | Data Governance | — |
| `pdpl` | 🔏 | حماية البيانات الشخصية | Personal Data Protection | Pending DSR count |
| `compliance` | ✅ | الامتثال والتدقيق | Compliance & Audit | — |

#### Section: أدوات / Tools
| ID | Icon | Label AR | Label EN |
|---|---|---|---|
| `evidence` | 📎 | إدارة الأدلة | Evidence Management |
| `policies` | 📋 | السياسات والوثائق | Policies & Documents |
| `workflow` | ⚙️ | إدارة سير العمل | Workflow Management |
| `riskcouncil` | 👔 | مجلس المخاطر | Risk Council |
| `scenarios` | 🎭 | سيناريوهات المخاطر | Risk Scenarios |
| `audit` | 🔍 | التدقيق الداخلي | Internal Audit |
| `tprm` | 🤝 | الأطراف الثالثة | Third-Party Risk |
| `audittrail` | 📜 | سجل التدقيق | Audit Trail |
| `ncamap` | 🗺️ | خارطة NCA | NCA Mapping |
| `incidentmgmt` | 🚨 | إدارة الحوادث | Incident Mgmt |
| `compcal` | 📅 | تقويم الامتثال | Compliance Calendar |
| `training` | 📚 | التدريب | Training |
| `maturity` | 🏆 | نموذج النضج | Maturity Model |
| `changemgmt` | 🔄 | إدارة التغيير | Change Management |
| `supplychain` | 🔗 | سلسلة الإمداد | Supply Chain |
| `pbdowasp` | 🛡️ | PbD · OWASP | PbD · OWASP |
| `reports` | 📄 | التقارير والتحليلات | Reports & Analytics |
| `integrations` | 🔗 | التكاملات | Integrations |
| `templates` | 📁 | القوالب والنماذج | Templates & Forms |

#### Section: إعدادات / Settings
| ID | Icon | Label AR | Label EN |
|---|---|---|---|
| `licensing` | 🏷️ | التراخيص والاشتراكات | Licensing |
| `admin` | 🛠️ | الإدارة والإعدادات | Administration |

#### Notifications (separate)
| ID | Icon | Label |
|---|---|---|
| `notifications` | 🔔 | الإشعارات / Notifications |

---

## 3. Dashboard (`page-dashboard`)

### KPI Cards — 8 cards in g4 (4-column grid)
| # | Icon bg | Icon | Label AR | Label EN | Click action |
|---|---|---|---|---|---|
| 1 | `#fff3cd` | ⚠️ | إجمالي المخاطر | Total Risks | drillDownAllRisks() |
| 2 | `#fee2e2` | 🔴 | المخاطر الحرجة | Critical Risks | drillDownCriticalRisks() |
| 3 | `#dcfce7` | ✅ | مستوى الامتثال | Compliance Level % | showPage('compliance') |
| 4 | `#dbeafe` | 🔒 | الحوادث المفتوحة | Open Incidents | showPage('incidentmgmt') |
| 5 | `#ede9fe` | 🔄 | تغطية BCM | BCM Coverage % | showPage('bcm') |
| 6 | `#ffedd5` | 🔏 | طلبات DSR المعلقة | Pending DSR | showPage('pdpl') |
| 7 | `#f0fdf4` | 📈 | ERM | Enterprise Risk | showPage('erm') |
| 8 | `#f0fdf4` | 🏥 | BCM | Business Continuity | showPage('bcm') |

### Widgets — 2-column grid (g2)
**Row 1:**
- Risk Heatmap (5×5 interactive) — "View All" → erm
- Compliance bar chart (ISO 27001, NCA ECC, PDPL, ISO 22301, ISO 31000, SDAIA)

**Row 2:**
- Risk Trend line chart (Open Risks + Incidents, last 6 months)
- Recent Alerts (last 4 notifications, audit-item style)

**Row 3 (full width):**
- Module Status grid (6 modules: ERM, Cyber, BCM, PDPL, Compliance, Workflow) — status Active/Inactive badges

---

## 4. ERM — Enterprise Risk Management (`page-erm`)

### 4 Tabs
| Tab key | Label AR | Label EN |
|---|---|---|
| `register` | سجل المخاطر | Risk Register |
| `heatmap` | خريطة الحرارة | Heat Map |
| `kri` | مؤشرات KRI | KRI Dashboard |
| `appetite` | شهية المخاطر | Risk Appetite |

### Tab: Risk Register
**Search bar**: text search + category dropdown  
**Table columns**: Code | Name (AR+EN sub) | Category | Likelihood | Impact | Score | Owner | Treatment | Status | Actions  
**Categories**: cyber, operational, compliance, technology, hr  
**Treatment**: mitigate, accept, transfer, avoid  
**Status**: open (red), inprogress (yellow), closed (green)  
**Actions**: Edit (✏️), Delete (🗑️)  

**Risk form fields:**
- Risk Name (Arabic)
- Risk Name (English)
- Category (select: cyber/operational/compliance/technology/hr)
- Owner
- Likelihood 1-5 (select)
- Impact 1-5 (select)
- Treatment (mitigate/accept/transfer/avoid)
- Status (open/inprogress/closed)
- Department
- Notes (textarea)

### Tab: Heat Map
- 5×5 heatmap (same component as dashboard) centered max-width 600px
- Below: table of Critical & High risks only (score ≥ 9)

### Tab: KRI Dashboard
**4 KRI metric cards** (in g2 grid):
1. Critical Risks Count — threshold 3
2. Average Risk Score — threshold 10
3. Treated Risks % — threshold 70%
4. Risks Without Treatment — threshold 2

Each card: large number, threshold label, progress bar, breach/OK badge

**Risk Trend line chart**: Critical vs High per month (last 6 months)

### Tab: Risk Appetite
- Statement block (highlighted in primary-pale)
- 6 category cards in g3:
  - Cyber Risks → Zero Tolerance (danger)
  - Compliance Risks → Zero Tolerance (danger)
  - Operational Risks → Low (warning)
  - Financial Risks → Medium (amber)
  - HR Risks → Medium (amber)
  - Reputational Risks → Low (warning)

---

## 5. Cybersecurity (`page-cyber`)

### 4 Tabs
- Incidents | Assets | Controls | Cyber Dashboard

### Tab: Incidents
Table: Code | Title | Severity | Status | Date | Owner | Actions

### Tab: Assets
Table: Asset Name | Type | Criticality | Owner | IP | OS | Status

### Tab: Controls (Cyber)
Table: Code | Name | Framework (ISO 27001/NCA ECC/NIST CSF) | Domain | Maturity | Status | Owner

### Tab: Cyber Dashboard
- KPIs: Open Incidents, Critical Incidents, Controls Implemented %, Assets
- Charts: Severity doughnut, Control maturity radar, Asset criticality bar

---

## 6. Compliance (`page-compliance`)

### 5 Tabs
| Tab key | Label AR | Label EN |
|---|---|---|
| `controls` | مكتبة الضوابط | Control Library |
| `audit` | التدقيق الداخلي | Internal Audit |
| `external` | التدقيق الخارجي | External Audit 🏛️ |
| `findings` | النتائج | Findings |
| `scorecard` | بطاقة الأداء | Scorecard |

### Tab: Control Library
- Framework filter dropdown (ISO 27001, NCA ECC, ISO 22301, PDPL, SDAIA)
- Add Control button
- Table: Code | Name | Framework | Domain | Maturity (1-5) | Status | Owner | Evidence

### Tab: Internal Audit
- Table: Audit Title | Scope | Auditor | Date | Status | Actions  
- Status: completed (green), inprogress (yellow), planned (gray)

### Tab: External Audit 🏛️
**6 KPI cards**: Total Audits | Completed | In Progress | Planned | Total Findings | Critical Findings  
**Table**: Audit | Framework | Auditing Body | Department | Period (dates) | Findings | Rating | Status | Actions  
**Actions**: View 👁️ | Upload Report 📤 | Add Finding 📝  
**Below table**: External Audit Findings table:  
Columns: Finding | Audit | Severity | Recommendation | Owner | Due Date | Status

### Tab: Findings
- Table: Finding | Severity | Framework | CAPA | Target Date | Status | Actions
- Severity: critical (red), high (orange), medium (yellow), low (green)
- Status: closed (green), inprogress (yellow), open (red)

### Tab: Scorecard
- Radar chart: % compliance per framework
- Table: Framework | Score | Controls | Implemented | Trend

---

## 7. Incident Management (`page-incidentmgmt`)

### Header
- Title: إدارة الحوادث / Incident Management
- Subtitle: مراقبة وإدارة الحوادث الأمنية
- Buttons: Export PDF | New Incident (primary)

### 5 Stat Cards (5-column grid)
| # | Border | Icon | Label | Color |
|---|---|---|---|---|
| 1 | primary | 📊 | Total Incidents | primary |
| 2 | danger | 🔴 | Open | danger |
| 3 | #dc3545 | 🚨 | Critical | #dc3545 |
| 4 | success | ✅ | Resolved | success |
| 5 | info | ⏱️ | Avg Resolution (days) | info |

### 4 Tabs (bottom border style, NOT pill style)
Each tab has: icon above + label below

| Tab | Icon | Label AR | Label EN |
|---|---|---|---|
| `dashboard` | 📊 | لوحة المعلومات | Dashboard |
| `register` | 📋 | سجل الحوادث | Register |
| `timeline` | 📅 | الجدول الزمني | Timeline |
| `lessons` | 💡 | الدروس المستفادة | Lessons Learned |

### Tab: Dashboard
2×2 chart grid:
- Incidents by Severity → doughnut chart (Critical/High/Medium/Low)
- Incidents by Status → bar chart
- Incidents by Category → polar area chart
- Recent Incidents → card list (last 3, colored left-border by severity)

### Tab: Register (card list style, NOT table)
Each incident card:
- Left accent border (color by severity)
- Severity icon circle (colored background)
- Title (bold)
- Meta: date | assignee | category
- Right: severity badge + status badge
- Hover: translateY(-2px) + shadow

### Tab: Timeline
Per incident (filtered to those with timeline array):
- Incident header: icon + title + severity badge
- Vertical timeline line
- Timeline events: each with colored dot, action text, date

### Tab: Lessons Learned
- Grid of cards for resolved incidents with lessons
- Each card: severity color top border, title, resolved date
- Content box: lessons text

### Incident data fields
```
id, title (AR), title_en (EN), severity (critical/high/medium/low),
status (open/investigating/resolved/closed), category,
assignee, reported (date), resolved (date),
timeline: [{action (AR), action_en, date}],
lessons (AR), lessons_en
```

---

## 8. Business Continuity (`page-bcm`)

### 4 Tabs
- BIA | BCP/DRP | Crisis Management | Resilience

### Tab: BIA
Table: Process Name | Department | Criticality | RTO (hrs) | RPO (hrs) | MTPD | MBCO % | Status

### Tab: BCP/DRP Plans
Table: Plan Name | Type (BCP/DRP) | Version | Owner | Approved Date | Status (Draft/Approved/Tested)

### Tab: Crisis Management
- Crisis team contacts
- Escalation procedures
- Exercise log

### Tab: Resilience Metrics
- Resilience score ring gauge
- RTO achieved vs target bar chart
- Recovery test results table

---

## 9. PDPL (`page-pdpl`)

### 4 Tabs
- PIA/DPIA | RoPA | DSR | Data Breaches

### Tab: PIA/DPIA
Table: Project | Type | Risk Level | Owner | Date | Status

### Tab: RoPA
Table: Activity | Purpose | Data Category | Legal Basis | Retention | Cross-Border

### Tab: DSR
Table: Requester | Type | Request Date | Deadline (+30d) | Status | Handler
Types: Access/Delete/Correct/Portability/Object

### Tab: Data Breaches
Table: ID | Description | Severity | Affected Count | Discovery Date | SDAIA Notified | Status

---

## 10. Data Governance (`page-datagov`)

### 3 Tabs
- Data Catalog | SDAIA Classification | Data Quality KPIs

### Tab: Data Catalog
Table: Asset Name | Type (DB/File/API/App) | Classification (TS/C/R/P) | Owner | Location | PII Flag | Status

### Tab: SDAIA Classification
4 classification levels:
- سري للغاية (Top Secret)
- سري (Confidential)  
- محدود (Restricted)
- عام (Public)

### Tab: Data Quality KPIs
6 indicators (≥78%): Completeness, Accuracy, Consistency, Timeliness, Uniqueness, Validity

---

## 11. Gap Analysis — Current vs Required

### Critical Gaps (wrong or missing)

| Area | Current State | Required State |
|---|---|---|
| **Dashboard KPIs** | 6 KPIs, wrong labels | 8 KPIs (match GRC.html exactly) |
| **Dashboard Widgets** | Heatmap + compliance doughnut + incidents feed + coming-soon | Heatmap + compliance BAR + trend LINE + alerts feed + module status |
| **Risk Tabs** | Register only | Register + HeatMap + KRI + Appetite |
| **Compliance Tabs** | Controls + Assessments + Findings | Controls + Internal Audit + External Audit + Findings + Scorecard |
| **Incidents Page** | Simple list + filter bar | 5 stats + 4 tabs (Dashboard/Register/Timeline/Lessons) |
| **Incident UI Pattern** | Card list (close but wrong tabs) | Must have Dashboard tab with 3 charts + Register (card list) + Timeline + Lessons |
| **Incident Fields** | No timeline or lessons | Needs timeline[] and lessonsLearned fields |
| **Sidebar** | Custom nav structure | Must match GRC.html nav exactly |
| **Nav link** | `/incidents/index.html` | Correct (our file path), but label/section wrong |

### What Is Correct ✅
- Design tokens match (green palette, Tajawal, IBM Plex Mono)
- Welcome banner on dashboard
- Heatmap 5×5 grid component  
- Card/table/badge/button styling
- CRUD modals (compliance, incidents)
- API integration pattern (Api.get/post/put/delete)
- Multi-schema backend entities
- Auth flow (JWT, requireAuth)

---

## 12. Revised Sprint Plan

### Sprint 1 (NOW — this session)
**Foundation + Core UI Alignment**

Backend:
- Add `timeline` (JSON text) + `lessonsLearned` (text) to Incident entity
- Add `nameAr` field to Risk entity (currently only `name`)

Frontend (match GRC.html exactly):
- Dashboard: 8 KPIs + all 4 widgets
- Risk: 4 tabs (Register, HeatMap, KRI, Appetite)
- Compliance: 5 tabs (Controls, Internal Audit, External Audit, Findings, Scorecard)
- Incidents: 5 stats + 4 tabs (Dashboard, Register, Timeline, Lessons)
- Sidebar: exact GRC.html nav structure

### Sprint 2 (Next)
- BCM module (4 tabs)
- PDPL module (4 tabs)
- Data Governance module (3 tabs)
- Evidence Management
- Policies & Documents
- Workflow Management

### Sprint 3 (Future)
- Reports & Analytics
- NCA Mapping
- Maturity Model
- Training & Awareness
- Risk Scenarios (Monte Carlo)
- Audit Trail
- TPRM
- Admin enhancements
- Integrations

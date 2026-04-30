# Sprint 2 — Detailed Scope

> Sprint: 2 (first sprint of new HTML-aligned plan)  
> Theme: Foundation Alignment + Compliance Module + Incident Management  
> Reference: GRC.html visual design, renderCompliance(), renderIncidentMgmt()

---

## 1. Business Goal

Close the biggest visual and functional gap between the HTML reference and the current platform:

1. **Visual alignment** — switch to Tajawal font and green design tokens; rebuild dashboard to match HTML design intent
2. **Compliance module** — introduce the Control Library and Assessment capabilities; these underpin ISO 27001, NCA ECC, and PDPL compliance tracking
3. **Incident Management** — standalone incident register (feeding into Cyber module later)
4. **Risk enhancements** — heatmap tab + treatment strategy field

Outcome: a platform that looks and feels like the HTML reference and supports the most business-critical workflows.

---

## 2. UI Pages / Components

### 2.1 Style System Update (`style.css`, `rtl.css`)
- Switch to Tajawal font via Google Fonts CDN
- Override CSS tokens to match HTML reference:
  - `--primary: #0f4c35` (dark green)
  - `--primary-d: #08301f`
  - `--primary-l: #1a6b3c`
  - `--primary-ll: #2e8b57`
  - `--primary-pale: #e8f5ee`
  - `--accent: #e8a020`
  - `--bg: #f0f4f1`
- Add heatmap cell classes `.hm-c1` through `.hm-c8`
- Refine KPI card top-accent bar (3px gradient)
- Add `.welcome` banner component
- Add `.domain-tabs` domain selector component
- Add `.stat-card`, `.stat-number` styles (gradient text)
- Toast: align with HTML bottom-left placement

### 2.2 Dashboard (`dashboard.html`)
Rebuild to match `renderDashboard()` from HTML:

**Welcome Banner** (`.welcome`):
- Dynamic greeting: "مرحباً، {name}" / "Welcome, {name}"  
- Current date (AR/EN format)
- Shield emoji decoration
- Gradient green background

**KPI Cards** (6-column grid):
| # | Label (AR) | Label (EN) | API Source |
|---|---|---|---|
| 1 | إجمالي المخاطر | Total Risks | `/api/v1/dashboard/stats` |
| 2 | معدل الامتثال | Compliance Rate % | `/api/v1/dashboard/stats` |
| 3 | الحوادث المفتوحة | Open Incidents | `/api/v1/dashboard/stats` |
| 4 | الضوابط الحرجة | Critical Controls | `/api/v1/dashboard/stats` |
| 5 | صحة الاستمرارية | BCM Health | `/api/v1/dashboard/stats` |
| 6 | طلبات البيانات المعلقة | Pending DSR | `/api/v1/dashboard/stats` |

**Widgets** (2-column row):
- **Risk Heatmap** (left): 5×5 grid using `.hm-cell` classes, colored by risk count per cell
- **Recent Incidents** (right): Last 5 incidents with severity badge, time-ago, module link

**Charts** (2-column row):
- **Risk Trend**: Line chart (last 6 months, by severity) — already exists, keep
- **Compliance Score**: Radar chart (ISO 27001/NCA ECC/PDPL/ISO 22301) — new

### 2.3 Risk Module (`risk/index.html`)
Add two tabs: **Heatmap** and **KRI Dashboard**

**Heatmap tab**:
- 5×5 grid (likelihood × impact)
- Each cell shows count of risks at that position
- Color: 1-2=green, 3-6=yellow/orange, 8-25=red (based on HTML `.hm-c*` classes)
- Click cell → filter risk register to matching risks
- Y-axis: Impact 1–5 (top=5), X-axis: Likelihood 1–5

**KRI tab** (skeleton):
- KPI row: Total Risks, Critical Count, Avg Score, Treated %
- Simple table: KRI Name | Value | Threshold | Status
- Data from risk register (aggregated)

**Risk Register enhancements**:
- Add `treatment` field dropdown (Mitigate / Accept / Transfer / Avoid)
- Auto-calculate score = likelihood × impact (client-side + server-side)
- Status badge colors aligned with design system

### 2.4 Compliance Module (NEW — `compliance/`)
Three files:
- `compliance/index.html` — Control Library (default tab)
- `compliance/assessments.html` — Assessments & Findings
- Route in sidebar → `/compliance/index.html`

**Tab: Control Library** (`/compliance/index.html`):
- Search bar + Framework filter dropdown + Status filter
- Domain tabs: All | ISO 27001 | NCA ECC | PDPL | ISO 22301
- Table columns: `Code | Name | Framework | Domain | Maturity | Status | Owner | Actions`
- Add Control modal: `Code, Name, Framework (dropdown), Domain, Maturity (1-5 select), Status (dropdown), Owner, Description`
- Maturity display: colored badge 1–5

**Tab: Assessments** (`/compliance/assessments.html`):
- Table: `# | Framework | Period | Auditor | Score % | Status | Date | Actions`
- Add Assessment modal: `Framework, Period, Auditor, Score, Status, Notes`

**Tab: Findings** (on assessments page):
- Table: `# | Title | Assessment | Severity | Owner | Due Date | Status | Actions`
- Add Finding modal: `Title, Assessment (dropdown), Severity, Owner, CAPA, Due Date, Status`
- Severity badge: Critical (red) / High (orange) / Medium (yellow) / Low (green)

### 2.5 Incident Management (NEW — `incidents/`)
- `incidents/index.html` — Incident register

**Table columns**: `# | Title | Severity | Category | Status | Assignee | Discovery Date | Actions`

**Severity**: Critical / High / Medium / Low  
**Category**: Cyber / Data Breach / Infrastructure / Access Control / Other  
**Status**: Open / Investigating / Resolved / Closed

**Add/Edit modal fields**:
`Title, Severity (dropdown), Category (dropdown), Status, Assignee, Discovery Date, Description`

**Dashboard widget**: Count by status (Open/Investigating/Resolved) shown as colored stat cards

### 2.6 Sidebar / App Shell (`app.js`)
Replace current 8 nav items with all 30 modules, grouped:

**Section: رئيسي (Main)**
- Dashboard, Admin

**Section: وحدات (Modules)**
- ERM, Cybersecurity, BCM, Data Governance, PDPL, Compliance

**Section: أدوات (Tools)**
- Incidents, Evidence, Policies, Workflow, Risk Council, Scenarios, Audit, TPRM, Audit Trail, NCA Map, Compliance Calendar, Training, Maturity, Change Mgmt, Supply Chain, PbD/OWASP

**Section: التقارير (Reports)**
- Reports, Board Report, Executive

**Section: إعدادات (Settings)**
- Licensing, Notifications

Stub pages: all modules that don't have a page yet show a "Coming Soon" styled page with module description.

---

## 3. Backend Modules / APIs

### 3.1 Compliance Module

**Entity**: `Control`
```
id, code, name, framework, domain, maturityLevel (1-5), 
status (IMPLEMENTED/PARTIAL/PLANNED/NOT_APPLICABLE),
owner, description, evidenceLinks, organisationId, 
createdBy, createdAt, updatedAt, deleted
```

**Entity**: `ComplianceAssessment`
```
id, framework, period, auditor, score (0-100), 
status (PLANNED/IN_PROGRESS/COMPLETED), notes, 
organisationId, createdBy, createdAt, updatedAt
```

**Entity**: `AuditFinding`
```
id, title, assessmentId, severity (CRITICAL/HIGH/MEDIUM/LOW),
owner, capa, dueDate, status (OPEN/IN_PROGRESS/REMEDIATED/VERIFIED/CLOSED),
organisationId, createdBy, createdAt, updatedAt, deleted
```

**Endpoints**:
```
GET    /api/v1/compliance/controls           — list with filters
POST   /api/v1/compliance/controls           — create
PUT    /api/v1/compliance/controls/{id}      — update
DELETE /api/v1/compliance/controls/{id}      — soft delete
GET    /api/v1/compliance/assessments        — list
POST   /api/v1/compliance/assessments        — create
GET    /api/v1/compliance/findings           — list with filters
POST   /api/v1/compliance/findings           — create
PUT    /api/v1/compliance/findings/{id}      — update
GET    /api/v1/compliance/stats              — scorecard data
```

### 3.2 Incident Module

**Entity**: `Incident`
```
id, title, severity (CRITICAL/HIGH/MEDIUM/LOW), 
category (CYBER/DATA_BREACH/INFRASTRUCTURE/ACCESS_CONTROL/OTHER),
status (OPEN/INVESTIGATING/RESOLVED/CLOSED),
assignee, discoveryDate, resolutionDate, description,
organisationId, createdBy, createdAt, updatedAt, deleted
```

**Endpoints**:
```
GET    /api/v1/incidents          — list with filters (severity, status, category)
POST   /api/v1/incidents          — create
PUT    /api/v1/incidents/{id}     — update
DELETE /api/v1/incidents/{id}     — soft delete
GET    /api/v1/incidents/stats    — count by status/severity
```

### 3.3 Dashboard API Enhancements

Add to existing `DashboardStats`:
```java
int openIncidents;
int investigatingIncidents;
double complianceRate;          // avg score across assessments
int implementedControls;
int totalControls;
int pendingDsr;                 // zero until PDPL module
```

New endpoint: `GET /api/v1/dashboard/heatmap` → returns `List<HeatmapCell>` with `{likelihood, impact, count}`

---

## 4. Database Changes

### V3__compliance_module.sql
```sql
-- Controls table
CREATE TABLE grc.controls (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(50) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    framework NVARCHAR(100) NOT NULL,       -- ISO_27001, NCA_ECC, PDPL, ISO_22301
    domain NVARCHAR(100),
    maturity_level TINYINT DEFAULT 1,
    status NVARCHAR(50) DEFAULT 'PLANNED',  -- IMPLEMENTED, PARTIAL, PLANNED, NOT_APPLICABLE
    owner NVARCHAR(255),
    description NVARCHAR(MAX),
    organisation_id BIGINT NOT NULL,
    created_by NVARCHAR(100),
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE(),
    deleted BIT DEFAULT 0
);

-- Compliance assessments
CREATE TABLE grc.compliance_assessments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    framework NVARCHAR(100) NOT NULL,
    period NVARCHAR(50),
    auditor NVARCHAR(255),
    score DECIMAL(5,2),
    status NVARCHAR(50) DEFAULT 'PLANNED',  -- PLANNED, IN_PROGRESS, COMPLETED
    notes NVARCHAR(MAX),
    organisation_id BIGINT NOT NULL,
    created_by NVARCHAR(100),
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE()
);

-- Audit findings
CREATE TABLE grc.audit_findings (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    assessment_id BIGINT REFERENCES grc.compliance_assessments(id),
    severity NVARCHAR(20) NOT NULL,         -- CRITICAL, HIGH, MEDIUM, LOW
    owner NVARCHAR(255),
    capa NVARCHAR(MAX),
    due_date DATE,
    status NVARCHAR(50) DEFAULT 'OPEN',     -- OPEN, IN_PROGRESS, REMEDIATED, VERIFIED, CLOSED
    organisation_id BIGINT NOT NULL,
    created_by NVARCHAR(100),
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE(),
    deleted BIT DEFAULT 0
);
```

### V4__incident_module.sql
```sql
CREATE TABLE grc.incidents (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    severity NVARCHAR(20) NOT NULL,         -- CRITICAL, HIGH, MEDIUM, LOW
    category NVARCHAR(50),                  -- CYBER, DATA_BREACH, INFRASTRUCTURE, ACCESS_CONTROL, OTHER
    status NVARCHAR(50) DEFAULT 'OPEN',     -- OPEN, INVESTIGATING, RESOLVED, CLOSED
    assignee NVARCHAR(255),
    discovery_date DATE,
    resolution_date DATE,
    description NVARCHAR(MAX),
    organisation_id BIGINT NOT NULL,
    created_by NVARCHAR(100),
    created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE(),
    deleted BIT DEFAULT 0
);
```

---

## 5. Roles & Permissions

| Module | Required Role/Permission |
|---|---|
| Compliance — Read | COMPLIANCE:READ (AUDITOR, COMPLIANCE_OFFICER, RISK_MANAGER, PLATFORM_ADMIN) |
| Compliance — Write | COMPLIANCE:WRITE (COMPLIANCE_OFFICER, PLATFORM_ADMIN) |
| Incidents — Read | CYBER:READ (CISO, RISK_MANAGER, AUDITOR, PLATFORM_ADMIN) |
| Incidents — Write | CYBER:WRITE (CISO, PLATFORM_ADMIN) |
| Dashboard KPIs | Any authenticated user |

Add module codes `COMPLIANCE` and `INCIDENT` to `SecurityConfig` permit list and module permission checks.

---

## 6. Acceptance Criteria

### Dashboard
- [ ] Welcome banner shows current user's name and today's date
- [ ] All 6 KPI cards load from `/api/v1/dashboard/stats`
- [ ] Risk heatmap renders 5×5 grid with color-coded cells
- [ ] Recent incidents widget shows last 5 with severity badges
- [ ] Risk trend chart loads correctly

### ERM Module
- [ ] Heatmap tab shows 5×5 grid, cells colored by risk count
- [ ] KRI tab shows aggregated risk stats
- [ ] Treatment dropdown: Mitigate/Accept/Transfer/Avoid
- [ ] Score = likelihood × impact (auto-calculated)

### Compliance Module
- [ ] Control can be created with all fields
- [ ] Framework filter (ISO 27001 / NCA ECC / PDPL / ISO 22301) works
- [ ] Maturity badge (1–5) displayed in color
- [ ] Assessment can be linked to a framework with score
- [ ] Finding can be created with severity and due date

### Incident Management
- [ ] Incident can be created with severity/category/assignee
- [ ] Status can be updated (Open → Investigating → Resolved)
- [ ] Incident count visible on dashboard KPI

### Sidebar
- [ ] All 30 modules listed in correct sections
- [ ] Active module highlighted
- [ ] Stub pages show "Coming Soon" for unimplemented modules
- [ ] Arabic labels correct

### Non-Functional
- [ ] No regressions on login, dashboard, risk, admin pages
- [ ] Backend starts with `--spring.profiles.active=dev`
- [ ] Swagger accessible at `/swagger-ui/index.html`
- [ ] All new APIs documented in Swagger

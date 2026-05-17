# TSS GRC Platform — Version 2 Status
**Snapshot Date:** 22 April 2026  
**Branch Source:** `claude/quirky-borg` (commit `726cd1e`)  
**Stack:** Spring Boot 3.5 / Java 17 / SQL Server 2019 / Bootstrap 5 RTL / Chart.js 4

---
## Final Code Path
the final code in path : C:\AYMAN\Github\Claude-GRC\.claude\worktrees\quirky-borg

## Quick Start

```bash
# 1. Create DB schema (run once per module)
python setup_cyber_db.py
python setup_bcm_db.py

# 2. Start backend (port 8084)
java -jar tss-grc-backend/target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev

# 3. Start UI (port 8087)
cd tss-grc-ui && python server.py 8087

# 4. Open browser
http://localhost:8087/login.html
```

**Login credentials:**
| Username | Password | Role |
|----------|----------|------|
| admin | Admin@12345 | PLATFORM_ADMIN |
| riskmanager | Risk@12345 | RISK_MANAGER |
| auditor | Audit@12345 | AUDITOR |

**DB:** SQL Server 2019, database `TSS_GRC`, user `sa / tss.123`

---

## ✅ COMPLETED MODULES

### Sprint 1 — Core Platform (commit `4189d18`)
| Feature | Status |
|---------|--------|
| Spring Boot 3.5 project skeleton | ✅ |
| JWT authentication (login / refresh / logout) | ✅ |
| Role-based access control (PLATFORM_ADMIN, RISK_MANAGER, AUDITOR) | ✅ |
| Permission system per module (READ/WRITE) | ✅ |
| Multi-tenant architecture (organisation_id) | ✅ |
| SQL Server schema + seed data | ✅ |
| ERM — Risk Management module (full CRUD + heatmap) | ✅ |
| Incident Management module (basic) | ✅ |
| Dashboard (KPI cards, risk heatmap, compliance bar chart, trend chart) | ✅ |
| Login page (split-panel, RTL/LTR, Arabic/English) | ✅ |
| app.js global shell (sidebar + header + language switcher + notifications) | ✅ |
| translations.js (EN + AR for all modules) | ✅ |
| Swagger UI (dev profile, port 8084) | ✅ |
| User Management API + Admin UI | ✅ |

### Sprint 2 — GRC Modules (commits `0ab9401` → `2a70135`)
| Module | UI | Backend | DB |
|--------|----|---------|----|
| **Compliance** | ✅ | ✅ | ✅ |
| **Privacy (PDPL)** | ✅ full 9-tab module | ✅ | ✅ |
| **BCM (ISO 22301)** | ✅ full 9-tab module | ✅ | ✅ |
| **TPRM** | ✅ | ✅ | ✅ |

### Cyber Security Module (commit `726cd1e`) ← NEW IN V2
| Component | Status | Detail |
|-----------|--------|--------|
| **Sidebar menu entry** | ✅ | `nav.cyber` → `/cyber/index.html` |
| **EN + AR translations** | ✅ | All 10 tabs, KPIs, buttons, fields |
| **Backend entities (10)** | ✅ | CyberControl, CyberSubControl, CyberFramework, ControlAssessment, Vulnerability, VulnException, CyberIncident, CyberAsset, CyberEvidence, CyberActionPlan |
| **Repositories (10)** | ✅ | Full JPA repos with org-scoped queries |
| **CyberService** | ✅ | 541 lines — full business logic + 5 workflows |
| **CyberController** | ✅ | 427 lines — 50+ REST endpoints |
| **DB schema + seed** | ✅ | `setup_cyber_db.py` — 13 steps, 41 seed rows |
| **Tab 1: Overview** | ✅ | 8 KPI cards + recent tables + 2 Chart.js charts |
| **Tab 2: Controls Library** | ✅ | Table + Add/Edit modal + filter/search/pagination |
| **Tab 3: Sub-Controls** | ✅ | Table + filter by parent + Add/Edit modal |
| **Tab 4: Control Assessments** | ✅ | Table + Start Assessment + Approve + Convert Gap→CAPA |
| **Tab 5: Vulnerabilities** | ✅ | Table + 6-step workflow bar + Register/Edit + Escalate |
| **Tab 5: Exceptions** | ✅ | Table + 5-step workflow bar + Request/Edit + Approve |
| **Tab 6: Cyber Incidents** | ✅ | Table + 9-step response workflow + Register/Edit + Advance |
| **Tab 7: Assets & Systems** | ✅ | Full asset inventory + Add/Edit modal |
| **Tab 8: Evidence & Compliance** | ✅ | Evidence repo + Add/Edit + expiry indicators |
| **Tab 9: Action Plans (CAPA)** | ✅ | Table + progress bar + Add/Edit + Complete |
| **Tab 10: Reporting** | ✅ | 4 stat cards + 3 Chart.js charts |
| **RTL/LTR** | ✅ | `[dir=rtl]` CSS on all elements |
| **i18n** | ✅ | All `data-i18n` attrs wired |

#### Cyber Workflows Implemented
| Workflow | Steps |
|----------|-------|
| Control Assessment | DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED → CLOSED |
| Vulnerability Management | REGISTERED → CLASSIFICATION → OWNER_ASSIGNMENT → REMEDIATION → VERIFICATION → CLOSED |
| Exception Management | REQUESTED → IMPACT_ASSESSMENT → COMPENSATING_CONTROLS → APPROVED → EXPIRED |
| Cyber Incident Response | DETECTION → LOGGING → TRIAGE → CONTAINMENT → INVESTIGATION → ERADICATION → RECOVERY → CLOSURE → LESSONS_LEARNED |
| Action Plan (CAPA) | OPEN → IN_PROGRESS → COMPLETED |

---

### SDAIA NDI / نضيء — National Data Index Module (commits `2c79a6e` → `25e996a`) ← LANDED 2026-05-17
| Slice | Migration | Tab | Status | Notes |
|-------|-----------|-----|--------|-------|
| 1 — Assessment Cycles      | V66 | Cycles      | ✅ | 13-state lifecycle, "one active per year" |
| 2 — Framework Library      | V67 | Framework   | ✅ | 20 placeholder items, JSON import endpoint |
| 3 — Maturity Assessment    | V68 | Maturity    | ✅ | CMMI 0-5, Rule 1 + 2 + D.Validation |
| 4 — Compliance Assessment  | V69 | Compliance  | ✅ | Dual-axis (substantive + lifecycle), Rules 3-5 |
| 5 — Operational Excellence | V70 | OpEx        | ✅ | 10 KPIs, Rule 13 direction-aware status |
| 6 — Data Governance Domains| V71 | Domains     | ✅ | 8 placeholder domains, cross-slice rollup |
| 7 — Evidence Repository    | V72 | Evidence    | ✅ | 8 artefacts, Rule 6 expiry auto-flip |
| 8 — Gaps + CAPA            | V73 | Gaps        | ✅ | 5 gaps + 7 CAPAs, Rules 7-9, auto-resolve |
| 9 — Improvement Plan       | V74 | Improvement | ✅ | PLAN-2026-01 IN_FLIGHT, Rules 10-12 |
| 10 — Submission Package    | V75 | Submission  | ✅ | 6-state workflow, Rules 15-16, SHA-256 manifest |
| 11 — Reports               | V76 | Reports     | ✅ | 12 saved reports + ad-hoc runner |
| 12 — Dashboard             | (none) | Dashboard | ✅ | 14-widget aggregate, single round trip |
| 13 — Settings              | V77 | Settings    | ✅ | 12 settings, 5 categories, typed values |
| 14 — Audit Log             | V78 | Audit       | ✅ | 15 actions, 3 severity levels, append-only |
| 15 — Tests + QA checklist  | (none) | (n/a)     | ✅ | 11 unit tests · docs/ndi/QA_CHECKLIST.md |

**NDI follow-ups (commits `24b3409` → `25e996a`)**
- **A — Auto-audit interceptor** (`24b3409`): `NdiAuditPublisher` wired into 8 services (cycle, maturity, compliance, evidence, gap, plan, domain, submission). Every state change emits an audit row automatically.
- **B — PDF/XLSX/CSV exports** (`bf97588`): `NdiReportExporter` (OpenPDF + Apache POI). `GET /api/ndi/reports/:id/export?format=…` returns the binary; UI has XLSX/PDF download buttons on each saved report + on the ad-hoc result.
- **C — Schedule executor** (`25e996a`): `NdiScheduler` runs the evidence expiry sweep every 6h and the report cron loop every 60s. Verified by setting `0 * * * * *` on RPT-001 and watching it auto-fire.

**Guardrail (Rule 0)** — Every domain / framework item / submission package seeded by V66-V78 carries `is_placeholder = 1` and the bilingual banner: *"Placeholder — requires official SDAIA/NDI control library import / عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء"*. Flip `official_library_imported = true` in settings + each domain's `Mark official` action after the official library lands.

### Audit Management — `audit_lc` entities filled in (commit `0a36252`) ← LANDED 2026-05-17
V16 created 4 `audit_lc.*` tables but only `findings` was wired to Java. This commit added `AnnualPlan` / `WorkingPaper` / `CapaTask` entities + repositories + a combined `AuditLifecycleService` + `/api/audit-lc/{annual-plans, working-papers, capas}` REST endpoints. Frontend `lib/auditLifecycle.ts` swapped from localStorage mocks to real backend calls (commit `990e66a`) — `AuditLifecyclePage` consumes the same sync API but is now persistent.

### Policy Management — full module (commit `8098e43`) ← LANDED 2026-05-17
V79 migration: `policy.documents` + `policy.exceptions` + `policy.attestations` with 6/3/6 seeds. Three state machines (policy DRAFT → … → ARCHIVED, exception REQUESTED → … → REVOKED, attestation PENDING → ATTESTED|DECLINED). PUBLISHED-requires-effective-date guard, expiry sweeps, `/new-version` cloner, full UI at `/policy-management` with 4 tabs (Overview / Documents / Exceptions / Attestations).

### Cross-module integration (commit pending) ← LANDED 2026-05-17
NDI `gap.risk_id` and `compliance_assessment.risk_id` promoted from soft FK to validated ERM lookup. `NdiCrossLinkService` rejects non-existent risk ids on create/update with HTTP 400 + a clear message; `GET /api/ndi/gaps/{id}/linked-risk` and `GET /api/ndi/compliance/{id}/linked-risk` return a `LinkedRisk` enrichment record (id, externalId UUID, titleEn/Ar, status, sourceModule, inherentLikelihood/Impact, ownerId).

### Risk-Advanced (commit pending) ← LANDED 2026-05-17
The base ERM module already had `risk.risk_assessments` + `risk.risk_treatments` tables (V15) and Java entities + JPA repos, but no service/controller layer. This commit fills them in.
- **`RiskAssessmentService`** — record periodic re-assessments (1..5 likelihood / impact scale). On every save the latest values promote to the parent Risk's `residual_likelihood`/`residual_impact`, which trips the DB-side computed `residual_score`. Correction endpoint mirrors only when the corrected row is still the most-recent assessment.
- **`RiskTreatmentService`** — ISO 31000 treatment types (AVOID / MITIGATE / TRANSFER / ACCEPT) with a PLANNED → IN_PROGRESS → COMPLETED state machine (+ CANCELLED escape). Rule T1: a Risk in TREATMENT status refuses removal of its last live treatment with a clear message.
- **V80 migration**: adds the `completed_at` column the original V15 migration omitted, plus a backfill that stamps existing COMPLETED rows.
- `RiskAdvancedController` exposes the new endpoints under `/api/v1/risk/risks/{id}/assessments` and `/api/v1/risk/risks/{id}/treatments`.
- The existing `RiskAppetiteService` + `KriEngineService` already covered the "appetite" leg of the spec; this commit completes the assessment + treatment legs. Workflow on the parent Risk (submit-for-review, approve, accept, close, reopen, incident-bump) was also already wired by `RiskController`.

---

## ❌ REMAINING — Sprint 2 Completion

### Modules Not Yet Built
| Module | UI | Backend | Priority |
|--------|----|---------|----------|
| **Vendor Risk Enhancements** (TPRM scoring, questionnaires) | ⚠️ | ⚠️ | Medium |

### Gaps in Existing Modules
| Module | Missing |
|--------|---------|
| **Cyber** | File upload for evidence; real-time BCM handoff integration |
| **BCM** | Link BCM plans to cyber incidents; recovery testing results |
| **Privacy** | DPIA integration with risk module; consent analytics |
| **Compliance** | Gap analysis export (PDF/Excel); framework import |
| **Dashboard** | Cyber KPIs missing from main dashboard (links to `/cyber/`) |

---

## ❌ REMAINING — Sprint 3

| Feature | Priority |
|---------|----------|
| **MFA** (TOTP / SMS) | High |
| **Temporal tables** (full audit history on all entities) | High |
| **File upload service** (evidence attachments) | High |
| **Email notifications** (overdue alerts, workflow transitions) | High |
| **PDF/Excel export** (reports, risk register, audit) | High |
| **Licensing module** (org activation, module entitlements) | Medium |
| **Advanced analytics** (trend analysis, predictive risk) | Medium |
| **SSO / SAML integration** | Medium |
| **Production hardening** (rate limiting, WAF headers, secrets vault) | Medium |
| **Mobile-responsive polish** | Low |
| **Dark mode** | Low |

---

## Architecture Notes

```
tss-grc-backend/
  src/main/java/com/tss/grc/
    common/          — tenant context, permissions, base entities
    config/          — security, CORS, Swagger, cache
    module/
      risk/          ✅ Sprint 1
      incident/      ✅ Sprint 1
      compliance/    ✅ Sprint 2
      privacy/       ✅ Sprint 2
      bcm/           ✅ Sprint 2
      tprm/          ✅ Sprint 2
      cyber/         ✅ V2 NEW
      dashboard/     ✅
      admin/         ✅

tss-grc-ui/
  assets/css/        — style.css (CSS vars), rtl.css
  assets/js/         — app.js (shell), auth.js, api.js, i18n.js, translations.js, config.js
  dashboard.html     ✅
  risk/              ✅
  incidents/         ✅
  compliance/        ✅
  privacy/           ✅
  bcm/               ✅
  tprm/              ✅
  cyber/             ✅ V2 NEW
  admin/             ✅
```

### Key Design Patterns
- **Multi-tenant:** Every entity has `organisation_id`; every repo query filters by it via `TenantContext`
- **Soft delete:** `is_deleted` column; `@Where(clause="is_deleted=0")` on all entities
- **i18n:** `titleEn` / `titleAr` on all user-visible entities; frontend reads `I18n.current`
- **Auth:** Stateless JWT (access token in header) + HttpOnly refresh cookie; `@PreAuthorize("hasPermission('MODULE','READ')")`
- **Workflows:** Stored as `workflow_status` string; transitions enforced in Service layer
- **UI shell:** `app.js` injects sidebar + header into every page via `#app-shell` div

---

## How to Continue Development

### Adding a New Module (e.g., Data Governance)
1. Create `tss-grc-backend/src/main/java/com/tss/grc/module/datagovernance/`
   - `entity/` — JPA entities with `organisation_id`, `is_deleted`, bilingual fields
   - `repository/` — JPA repos with `findBy*OrganisationId` queries
   - `service/` — business logic + workflow transitions
   - `controller/` — `@RequestMapping("/api/v1/datagovernance")`, `@PreAuthorize`
2. Create `tss-grc-ui/datagovernance/index.html` — follow `cyber/index.html` pattern
3. Add to `app.js` `NAV_ITEMS` array
4. Add translations to `translations.js` (EN + AR blocks)
5. Create DB setup Python script following `setup_cyber_db.py` pattern
6. Run DB script, rebuild JAR with Maven

### Rebuilding the JAR (requires Maven)
```bash
cd tss-grc-backend
mvn clean package -DskipTests
# jar: target/tss-grc-platform-0.1.0-SNAPSHOT.jar
```

---


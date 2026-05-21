# TSS GRC Platform — Version 2 Status
**Last update:** 21 May 2026 (PRs #11-38 — wizard arc + reusable audit-timeline + reviewer-journey polish)
**Snapshot Date:** 22 April 2026 (initial), updated continuously
**Branch Source:** `claude/quirky-borg` (commit `726cd1e`)
**Stack:** Spring Boot 3.5 / Java 17 / SQL Server 2019 / React 18 + Vite / Bootstrap 5 RTL / Chart.js 4

---

## 🟢 Recent additions — twenty-eight-PR wizard arc + reviewer-journey polish (21 May 2026)

After PR #11 (41 commits) merged the §7 wizard catalog + Workflow Hub + bundle code-split, twenty follow-up PRs closed the persistence + UX + automation + AI + audit loop end-to-end, then extracted the forensic-timeline pattern into reusable cards that drop into any detail page. The most recent seven PRs (#32-38) polish the reviewer journey itself — server-side aggregation, Hub→register drill-down, sidebar inbox badge, bulk-review actions, stale-row signal, free-text search:

| PR | Merge | What |
|---|---|---|
| #11 | `9c141e1` | 24 wizards + Workflow Hub + WorkflowQuickLaunch chip strips + −53% gzip bundle code-split |
| #12 | `77b1b02` | 7 wizards wired to dedicated tables (BcmExercise, CyberIncident, VulnTriage, DSR, Breach, RoPA, SupplierOnboarding) |
| #13 | `a422f59` | V125 generic `workflow_submissions` ledger + new `module/workflow_submission/` backend + 12 remaining wizards wired (Risk Treatment/Acceptance, RCA, Mgmt Review, Signature, Control Test, Cloud CCC, NCA ECC, Consent, Cross-Border, Exit Plan, Board Pack) |
| #14 | `ca2f363` | `/workflows/submissions` register UI — filter by module + workflow id, status counts, JSON inspect, inline Approve/Reject/Close |
| #15 | `85a94f4` | 15-case `WorkflowSubmissionServiceTest` + V126 demo seed (25 representative rows) + sidebar entry "Workflow Submissions" |
| #16 | `cfdb564` | `ModuleSubmissionsPanel` — latest-5 panel on Privacy / BCM / SCM / Cyber / Risk / Compliance / Incidents pages |
| #17 | `62aeeec` | `DashboardSubmissionsCard` — status-counts strip + top-8 rows on `/dashboard` (first-login visibility) |
| #18 | `06eca6e` | `/workflows/submissions/:id` detail page — URL-shareable, full payload, review actions, copy-link, "open new" CTA |
| #19 | `7b7a0f4` | Filter-aware CSV export from the register (UTF-8 BOM, RFC-4180 quoting, filter-stamped filename) |
| #20 | `c3a01b7` | `AuditLogService` integration — every CREATE / APPROVE / REJECT / CLOSE / UNDER_REVIEW writes to `platform.audit_log` with cross-tenant defense |
| #21 | `f3af7f7` | F8 automation rules on submission events — V127 seeds AR-21..AR-25 (PDPL breach → NOTIFY DPO, NCA cyber-incident → NOTIFY CISO, risk acceptance → ESCALATE risk committee, cross-border → FLAG DPO review, board pack APPROVED → AUDIT milestone) |
| #22 | `afa5d98` | `/approvals` → `/workflows/submissions` cross-link in the page header + STATUS.md table refresh |
| #23 | `a4a8cb2` | AI review hints — 6th heuristic generator in `module/ai/`; per-wizard bilingual checklist + suggested decision; `AiReviewHintCard` on detail page |
| #24 | `e492cec` | Activity timeline on detail page — every audit-log row inline (CREATE / APPROVED / REJECTED / CLOSED / UNDER_REVIEW) with color-coded badges + actor (username · role) |
| #25 | `3036957` | `SubmissionAnalyticsCard` on the register — 3 KPIs (avg time-to-review, open count, active modules) + 3 lazy-loaded recharts (per-module bar, per-status donut, 30-day line); recomputes against current filter |
| #26 | `91feb05` | Per-wizard live submission counts on Hub cards — "3 submitted · 1 open" badges replace the static catalog |
| #27 | `5ffe91c` | F8 rule-run history on detail page — `AutomationRunsCard` shows which automation rules fired (paired with PR #24 timeline = humans + machines side-by-side) |
| #28 | `a529ab3` | STATUS.md refresh — captured PRs #22-27 + first end-to-end summary tables |
| #29 | `239d6d9` | Extracted timelines into reusable `components/audit/EntityActivityTimeline.tsx` + `EntityAutomationRuns.tsx`; dropped them onto `/bcm/aars/:id` (lights up the existing CrisisAarService audit-log calls automatically); WorkflowSubmissionDetailPage chunk shrunk 10.43→7.85 kB raw via the extraction |
| #30 | `a773a10` | Rolled the reusable cards into `RiskDetailPage` history tab — real audit_log rows + F8 rule fires + legacy mock history side-by-side; pattern now lives on 3 detail pages with 5+ more one-line-away once their services start audit-logging |
| #31 | `b623ca4` | STATUS.md refresh — captured PRs #28-30 + the reusable audit-timeline pattern subsection |
| #32 | `951e605` | FindingService audit-logging — every CREATE / TRANSITION / DELETE writes to `platform.audit_log`; FindingDto exposes numeric PK so the timeline card is one-line drop-in once an audit_lc Finding detail page lands |
| #33 | `e8e1fc3` | Server-side aggregation for Hub counts — new `GET /api/v1/workflow-submissions/counts/by-workflow`; single tenant-scoped JPQL `GROUP BY` replaces the client-side O(rows) aggregation; `WorkflowCount(workflowId,total,open,approved)` typed projection |
| #34 | `9f6ee26` | Hub count badges drill into pre-filtered Submissions register — `?module`, `?workflowId`, `?status` URL params (shareable, back-button-friendly); new status filter with `OPEN = SUBMITTED+UNDER_REVIEW`; Hub badge becomes a `role="link"` span that opens the register pre-scoped (+`&status=OPEN` when warning tone); "Clear all" pill |
| #35 | `848d09d` | Sidebar pending-reviews badge — accent-300 pill on "Workflow Submissions" entry shows `OPEN` count from the aggregate endpoint; refreshes on tab focus (`visibilitychange`); threaded through `NavRow` as optional `badge?` prop so adding CAPA/approvals badges later is one line |
| #36 | `dbd5b3c` | Bulk approve / reject / close — checkbox per OPEN row + sticky action bar; tri-state header checkbox (all/partial/none); `window.confirm` second-thought beat; `Promise.allSettled` so a single 409 doesn't abandon the rest; selection drops on reload + filter change |
| #37 | `279ea2f` | Stale-row highlighting — every OPEN row gains an age chip in the card header ("4d" info-blue → "12d · stale" danger-red past `STALE_DAYS = 7`); stats row gains a "Stale (7d+): N" pill; threshold centralised at top of file |
| #38 | `3d68bae` | Free-text search on the register — URL-backed `?q=...` substring across `titleEn/Ar`, `summaryEn/Ar`, `regulatoryAnchor`, `workflowId`, `workflowModule`, `reviewerNote`, **and** `payloadJson` so "breach" hits a payload field even when titleEn doesn't say it; plays with existing filters; selection drops on query change |

### Wizard persistence state

**20 of 24 catalog wizards POST to a real backend on submit, audit-log every transition, fan out to the F8 automation engine, and offer AI-generated reviewer guidance on demand.** The 4 remaining (`evidence-request`, `capa-workflow`, `evidence-connectors`, `incident-triage`) are catalog entries that route to existing register pages.

### Reusable forensic-timeline pattern (PRs #29-30)

`components/audit/EntityActivityTimeline.tsx` + `components/audit/EntityAutomationRuns.tsx` are now a two-line drop-in on any detail page. Both take `{entityType, entityId}` and hide silently when empty, so a fresh entity with no audit history renders no phantom cards.

| Detail page | Entity type | Status |
|---|---|---|
| `/workflows/submissions/:id` | `WorkflowSubmission` | ✅ lit up (#24, #27) |
| `/bcm/aars/:id` | `CrisisAar` | ✅ lit up (#29) — CrisisAarService already audit-logs |
| `/risk/:id` (History tab) | `Risk` | ✅ lit up (#30) |
| `/findings/:id` | `Finding` (string ext id, not numeric) | one-line away if DTO exposes numeric id |
| `/controls/:id`, `/policies/:id`, `/vendors/:id`, `/dpias/:id` | (services don't audit-log yet) | one-line away once they do |

The cards re-use the existing `GET /api/v1/audit-log/history` + `GET /api/v1/automation/runs/for-entity` endpoints — no schema or backend work needed per page. Adding the same pattern to a new module is two lines of JSX + (if missing) one constructor field + one `audit.log()` call in the service.

### Reviewer-journey polish (PRs #33-38)

Six PRs in three days collapsed the reviewer's path from "navigate around to find pending work" to "see the inbox, click, clear it":

| Before | After |
|---|---|
| Log in. No signal anywhere of pending work. Navigate to Submissions. Scroll. | Sidebar shows **"Workflow Submissions · 4"**. Click. |
| Submissions register loads every row to compute Hub badge counts (O(rows) per page-load). | Hub badges fed by a single tenant-scoped SQL `GROUP BY` aggregate. Register stays row-level for the list itself. |
| Hub badge says "1 open RoPA" — dead-end data; user must scroll the unfiltered register to find it. | Click the badge → register opens at `?workflowId=privacy-ropa&status=OPEN` already scoped to the row. URL is shareable + back-button-friendly. |
| 5 open submissions = 5 clicks, 5 modal beats. | Tick header checkbox → "Approve (5)" → confirm. `Promise.allSettled` reports per-row results. |
| Date strings ("Submitted 2026-05-09") — reviewer mentally subtracts to find oldest. | Age chip on every OPEN row turns danger-red after 7d (matches PDPL §18 / ECC SLA). Stats row counts the stale. |
| No way to find a remembered submission by keyword. | URL-backed `?q=...` substrings across title/summary/anchor/workflowId/payload. Pastes work. |

The wizard-submission surface is now production-feel for the day-to-day reviewer.

### End-to-end surface visible to a buyer

| Surface | What appears |
|---|---|
| `/dashboard` | `DashboardSubmissionsCard` — status counts + top-8 rows + "View all (N)" |
| `/workflows` (Hub) | 24 wizard cards with **live** "N submitted · M open" badges (server-side aggregate) — **click badge → drill into pre-filtered register** |
| `/<module>` (×7) | `WorkflowQuickLaunch` chip strip + `ModuleSubmissionsPanel` (latest 5) |
| `/workflows/submissions` | Register + analytics card (3 KPIs + 3 charts) + free-text search + module/wizard/status filters (URL-backed) + stale-row age chips + bulk approve/reject/close + CSV export |
| `/workflows/submissions/:id` | Hero envelope → AI review hint → Activity timeline (audit log) → Automation runs (F8) → Review actions → Full payload |
| `/approvals` | Existing inbox + "Workflow submissions" header CTA |
| `/ai/suggestions` | Filter by 6 features (added `WORKFLOW_SUBMISSION_REVIEW`); accept/reject DRAFT rows |
| Sidebar | "Workflow Hub" + **"Workflow Submissions · N"** (live pending-reviews badge) entries under My Work |

### Backend characteristics

- **22 service tests** on `WorkflowSubmissionServiceTest` — validation, state machine, cross-tenant 404 + cross-tenant audit defense + cross-tenant event-publisher defense, audit-log entry shape, F8 event payload shape, null-coercion guard, decision-mapping
- **Cross-tenant guarantees**: every read/write goes through `TenantContext.getCurrentTenant()`; a 404 from another tenant writes **no audit row AND publishes no F8 event** under the requesting tenant (both verified)
- **F8 integration**: 5 seed rules (AR-21..AR-25) routed by `workflowId` / `status` / `regulatoryAnchor`; engine evaluates equality + contains against the payload Map; payload values null-coerced to `""` to prevent NPEs
- **AI module**: 6 heuristic generators in `module/ai/`, all persisting DRAFT `AiSuggestion` rows for human review; LLM-gateway swap is a single private-method substitution per heuristic, no schema or API change

### Bundle performance

| Metric | Value |
|---|---|
| Main bundle | 1,537 kB raw / 470 kB gzip (−53% from pre-split baseline of 1,002 kB gzip) |
| Wizard chunks | 18 × ~5-9 kB raw each, fetched on demand |
| Route chunks | 95+ total |
| Detail-page chunk | 10.43 kB raw / 3.87 kB gzip (absorbs hero + AI hint + audit timeline + automation runs + review controls + payload) |
| Submissions register chunk | 11.32 kB raw / 4.51 kB gzip (includes analytics card; recharts lazy-loaded as shared 397 kB bundle) |

Recharts already split as own 397 kB / 116 kB gzip bundle — shared with other chart-bearing pages, not a new cost.

## 🟢 Recent additions (PR #11 — 39 commits)

### Saudi-GRC buyer-assessment engines
| Migration | Tables / columns |
|---|---|
| **V113** | Cross-border + special-category fields on `processing_activities`; `risk.risk_controls` bridge; index on `evidence_items.review_due_date` |
| **V115** | `risk.risk_attestations` (quarterly cadence); `evidence.evidence_connectors`; `crisis_events.pdpl_72h_*`; `dsr_cases.sla_breached_at`; `risks.attestation_cadence_days` |
| **V116** | `ai.ai_suggestions`; `bcm.crisis_aar`; full 28-role Saudi-GRC catalog in `iam.roles` |
| **V117–V123** | ~1,100 demo rows across 33 tables (Saudi-context vendors, KSA threat intel, bilingual data) |
| **V124** | UNIQUE INDEX on `bcm.crisis_aar(organisation_id, report_code)` |

### New backend modules
- `module/ai/` — 5 heuristic generators with persisted DRAFT ledger + human review workflow (LLM gateway is a private-method swap away). Endpoints under `/api/v1/ai/*`.
- `module/bcm/cycle/CrisisAar*` — NCEMA After-Action Reports with auto-stitched narrative + state machine `DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED → RESCINDED`. Endpoints under `/api/v1/bcm/aars/*`.
- `module/platform/completion/BuyerGap*` — wraps the V115 sweep endpoints (DSR SLA, PDPL 72h, risk attestation, evidence freshness) + connector registry.

### New frontend
- **Workflow Hub** at `/workflows` — 35-workflow catalog grouped by module, filterable by status / search
- **24 wizards** AVAILABLE out of the 35-entry §7 catalog (69%). Every PARTIAL/PLANNED entry that has a regulator-facing output is now a real wizard:
  - **ERM** (4/4 ✓) — Risk Identification, Treatment, Acceptance, Attestation
  - **BCM** (7/7 ✓) — BIA, BCP, DRP, Crisis, Exercise, AAR, Management Review
  - **SCM** (3/3 ✓) — Supplier Onboarding, Vendor Risk, Exit Plan
  - **NCA** (4/4 ✓) — Cyber Incident, ECC Assessment, Vuln Triage, Cloud CCC
  - **PDPL** (6/6 ✓) — DPIA, DSR, Breach, Cross-Border, Consent, RoPA
  - **Cross-module** (4/x) — Incident RCA, Management Review (generic), Digital Signature, Board Pack, Control Test
  - Remaining 11 entries are register-page-only (e.g. `/capa`, `/policy-management`, `/connectors`) — not wizard-shaped
- `/buyer-gap` — live demo of every V115/V116 endpoint
- `/bcm/aars/:id` — bilingual AAR editor with state-machine actions
- `/ai/suggestions` — accept/reject AI drafts with reviewer note
- **Design-system primitives**: `useLoadable<T>()`, `<ErrorState>`, `<LoadableSection>`, `<ErrorBoundary>`, `<WizardShell>` (used by every wizard above)

### Verification
| Check | Result |
|---|---|
| `mvn test` | **673 / 673 pass** (+31 from this branch) |
| `tsc --noEmit` | **0 errors** |
| `vite build` | clean, ~6.5s, 95+ route chunks |
| `/actuator/health` | UP |
| All 56 page-level endpoints | 200 |
| Backend startup warnings | All silenced |

### Bundle performance (cumulative across both code-split commits)
| Metric | Pre-split | After wizards | After full split | Total Δ |
|---|---|---|---|---|
| Main bundle raw | 3,588 kB | 3,430 kB | **1,530 kB** | **−2,058 kB (−57%)** |
| Main bundle gzip | 1,002 kB | 964 kB | **468 kB** | **−534 kB (−53%)** |
| Off-route chunks | 0 | 20 | 95+ | each 5-40 kB |

Initial JS now under 500 kB gzipped — meaningful TTI gain on low-bandwidth Saudi data networks. Every off-route page (wizards, lifecycle, detail, portal, intake forms, admin, KB, framework packs, dependency graph, etc.) fetched on demand via React.lazy + Suspense.

### Module-page wizard chips (PR #11 D-side)
Every module landing page (`/privacy`, `/bcm`, `/scm`, `/cyber`, `/risk`, `/compliance`, `/incidents`) now surfaces a horizontal chip strip of all AVAILABLE wizards for that module — auto-synced to the catalog so future wizards appear without per-page edits. See `WorkflowQuickLaunch.tsx`.

### Notable bug fixes this PR
- **🔴 Cross-tenant FK leak** in `linkRiskControl` (would let A persist a bridge row pointing at B's risk_id)
- **🔴 Backend startup crash** — ambiguous `/api/v1/evidence/connectors` mapping between EvidenceMatrixController and the new BuyerGapController; resolved by routing V115 endpoints under `/connector-registry/*`
- DSR sweep dedup bug (`Boolean.FALSE.equals(null)` returned false → first-time overdue cases never fired the event)
- AAR sequence race + missing year scoping + `.size()` doing O(n) row hydration
- 47 pre-existing TS errors across BCM / Licensing / CrisisRoom / Bcp pages
- `Cannot find cache named 'licenses'` WARN loop on every login
- Evidence freshness sweep loaded everything in one batch (OOM risk at scale)
- `NoResourceFoundException` and `HttpRequestMethodNotSupportedException` mapped to 500 instead of 404 / 405

### Vendor / product-decision blocked
- Real digital-signature provider adapter (Nafath / Adobe Sign / DocuSign)
- AI module LLM gateway (heuristic generators ship today)
- Tier-3 #14 threat-intel live feed
- Tier-3 #17 supplier portal
- `PageImpl → VIA_DTO` migration (would break frontend response shape)

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

### TPRM Questionnaire Engine — V81 (commit pending) ← LANDED 2026-05-17
TPRM had basic Vendor + VendorAssessment CRUD but `risk_score` was a free-form decimal with no rubric behind it. V81 adds a deterministic questionnaire engine: `tprm.questionnaire_templates` + `_questions` + `_responses`, plus 5 new columns on `vendor_assessments` (workflow_status, template_id, answered_count, total_questions, computed_score_pct). Seed: one ACTIVE *Critical Vendor Cyber Due Diligence v1.0* template with 8 bilingual questions across 3 sections (Information Security, Privacy, Operational Resilience). Service implements full template lifecycle (DRAFT → ACTIVE → ARCHIVED, frozen-once-ACTIVE rule), per-vendor response upsert with on-save weighted-sum recompute, NA-with-justification, and a NOT_STARTED → IN_PROGRESS → COMPLETED → REVIEWED workflow on the assessment. Scoring math: posture% = Σ(scored×weight) / Σ(max×weight) × 100; risk% = 100 − posture%; rating banded against the template's three thresholds. Verified live: a strong-vendor walkthrough (mostly YES + 4-out-of-5 scale answers) computed 17.31% risk → LOW band, matching hand-calc to two decimals.

### Risk-Advanced (commit `e906c0f`) ← LANDED 2026-05-17
The base ERM module already had `risk.risk_assessments` + `risk.risk_treatments` tables (V15) and Java entities + JPA repos, but no service/controller layer. This commit fills them in.
- **`RiskAssessmentService`** — record periodic re-assessments (1..5 likelihood / impact scale). On every save the latest values promote to the parent Risk's `residual_likelihood`/`residual_impact`, which trips the DB-side computed `residual_score`. Correction endpoint mirrors only when the corrected row is still the most-recent assessment.
- **`RiskTreatmentService`** — ISO 31000 treatment types (AVOID / MITIGATE / TRANSFER / ACCEPT) with a PLANNED → IN_PROGRESS → COMPLETED state machine (+ CANCELLED escape). Rule T1: a Risk in TREATMENT status refuses removal of its last live treatment with a clear message.
- **V80 migration**: adds the `completed_at` column the original V15 migration omitted, plus a backfill that stamps existing COMPLETED rows.
- `RiskAdvancedController` exposes the new endpoints under `/api/v1/risk/risks/{id}/assessments` and `/api/v1/risk/risks/{id}/treatments`.
- The existing `RiskAppetiteService` + `KriEngineService` already covered the "appetite" leg of the spec; this commit completes the assessment + treatment legs. Workflow on the parent Risk (submit-for-review, approve, accept, close, reopen, incident-bump) was also already wired by `RiskController`.

---

## ❌ REMAINING — Sprint 2 Completion

### Modules Not Yet Built
All Sprint 2 modules now landed. Remaining items are polish / UI work (frontend for Policy Management, Risk-Advanced, and TPRM questionnaire engine all consume the new endpoints but don't have dedicated tabs yet).

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


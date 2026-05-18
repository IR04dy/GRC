# BCM Module — Independent Audit & End-User Acceptance Test

**Auditor stance:** independent, expert. Acting as a GRC tools-leader (the way a ServiceNow IRM, MetricStream, OneTrust GRC, RSA Archer, Riskonnect, or LogicGate evaluator would walk a customer demo), an ISO 22301 lead implementer, a SAMA Cyber & Operational Resilience reviewer, and an NCEMA crisis-management specialist.

**Date:** 2026-05-18
**Audited against:** `docs/bcm/BCM_IMPROVEMENT_PLAN.md` (24 sections, 25 pages, 23 automation rules, 28 acceptance criteria)
**Audited code paths:**
- `tss-grc-backend/src/main/java/com/tss/grc/module/bcm/`
- `tss-grc-backend/src/main/java/com/tss/grc/module/bcm_lc/`
- `tss-grc-ui-v2/src/pages/bcm/`, `pages/bcm-lifecycle/`, `pages/bcm-dependency/`, `pages/crisis/`, `pages/dr-plans/`, `pages/dashboards/BcmDashboardPage.tsx`

---

## 1. Headline finding — the BCM module is THREE parallel implementations, not one

This is the single most important finding. The spec describes one cohesive 25-page workspace. What actually exists in code is three half-overlapping product tracks with their own state machines, data models, and URLs:

| Track | Route prefix | Backend package | Data model centre | What it does |
|---|---|---|---|---|
| **A. Legacy operational BCM** | `/bcm` | `module/bcm/{service,entity}` | `BcmPlan`, `BiaRecord`, `CriticalProcess`, `BcmExercise`, `CrisisCommsTemplate` | Day-to-day CRUD of plans / processes / BIA / exercises / comms templates. Status machine: DRAFT → UNDER_REVIEW → APPROVED → INVOKED → restore. No F7 audit, no F8 events. |
| **B. Cycle assessment** (BCM-1/2/3) | `/bcm/readiness`, `/bcm/reports` | `module/bcm/cycle/*` | `BcmAssessmentCycle`, `BcmGovernanceRecord`, `BcmRisk`, `ContinuityStrategy`, `BcmGap`, `BcmCorrectiveAction`, `BcmCommunicationContact` | Annual / periodic readiness cycle workflow. Heavy F7 audit + F8 events. Rich state machines. The "new" track from your recent sprints. |
| **C. Resilience lifecycle** | `/bcm-lifecycle`, `/crisis-management`, `/dr-plans` | `module/bcm_lc/*`, `module/bcm/drp/*` | `BcmInvocation` (STANDBY → ELEVATED → ACTIVATED → STAND_DOWN), `DisasterRecoveryPlan`, SAMA OPRES packet builder, NCEMA packet builder | Production-grade invocation lifecycle with realtime events, SAMA/NCEMA regulatory packets. Built earlier as a "production hardening" track. |

**Why this matters for an enterprise buyer:**
- A new BCM Manager logs in. The sidebar shows **BCM, BCM Dashboard, BCM Readiness, BCM Reports, BCM Lifecycle, BCM Dependency, Crisis Management, DR Plans** — eight entries, no obvious navigation hierarchy, three sets of overlapping terminology.
- A "plan" in `/bcm` (BcmPlan, status INVOKED) is not the same row as an "invocation" in `/bcm-lifecycle` (BcmInvocation, status ACTIVATED). They never refer to each other.
- "Run an exercise" exists in `/bcm` (BcmExercise CRUD with score + findings) **and** in `/bcm-lifecycle` as "drills" (separate `transitionDrill` API). They write to different tables.
- A "BIA" in `/bcm` (BiaRecord with RTO/RPO/MTPD hours on the process) is unrelated to a "BIA" in `/bcm-lifecycle` (separate lifecycle entity with a DRAFT → UNDER_REVIEW → APPROVED → ACTIVE state machine).
- The Crisis Management page (`/crisis-management`) uses an NCEMA-tier model (TIER-1-NATIONAL through TIER-4-LOCAL) and a STANDBY → ELEVATED → ACTIVATED → STAND_DOWN state machine. The spec calls for a Crisis Event entity with situation reports, decisions, regulatory notifications — **none of those columns exist on any entity in the codebase**. The crisis page rides on the lifecycle's invocation entity and the legacy `CrisisCommsDistribution` table, not on a real crisis event.

**Recommendation #1 (architecture):** before adding more features, run a consolidation sprint that picks ONE canonical model per concept (plan, BIA, exercise, crisis) and deprecates the others behind a redirect. Industry leaders ship one "BCM workspace" with sub-tabs, not three top-level applications.

---

## 2. Spec coverage matrix — what exists vs. what the plan promises

Scored against the 25-page navigation table in BCM_IMPROVEMENT_PLAN.md §2.

Legend: ✅ = fully shipped · 🟡 = partial / folded into another page · ❌ = missing.

| # | Spec page | Status | Where it lives (if anywhere) | Audit comment |
|---|---|---|---|---|
| 1 | BCM Dashboard | ✅ | `/bcm/dashboard` | KPIs present. Missing: ISO 22301 readiness score, DGA/SAMA readiness score, Management Review status, board approval status (4 of the 20 spec widgets). |
| 2 | BCM Governance | 🟡 | `/bcm/readiness` tab "Governance" | Table only — no editor for committee composition, no roles & responsibilities matrix, no link to evidence repository, no ISO 22301 clause picker. |
| 3 | Critical Services Register | ❌ | (folded into `processes`) | **There is no Critical Services entity.** Spec lists 5 criticality levels (Sovereign / Critical / Essential / Supporting / Low). What exists is `CriticalProcess` only. Service vs. process is a fundamental BCM distinction — ISO 22301 §8.2.2 separates them. |
| 4 | Critical Service Detail | ❌ | — | No detail page exists for any BCM entity. |
| 5 | Critical Processes Register | ✅ | `/bcm` tab "Processes" | Basic CRUD. Missing: manual workaround flag, minimum-resources structure, dependency linkage UI. |
| 6 | BIA Register | 🟡 | `/bcm` tab "BIA" | Lists BIA rows. Spec demands 10+ impact dimensions (regulatory / financial / operational / reputation / legal / public-service / national-sovereign / data / cyber / supplier); table shows one `overallImpact` enum only. |
| 7 | BIA Form / Wizard | ❌ | `/intake/bia_submission` (generic intake form) | The "create BIA" button on `/bcm` opens a 6-field modal, not the wizard the spec calls for. Impact time-bands (0-2h, 2-4h, 4-8h, 8-24h, 1-3d, 3-7d, >7d) are absent. MBCO (Minimum Business Continuity Objective) field is missing. Minimum staff / facility / supplier dependencies are not captured. |
| 8 | BCM Risk Assessment | ✅ | `/bcm/readiness` tab "Risks" | Strong. 18 threat categories, likelihood × impact, residual scoring, F8 event on TREATMENT_PLANNED. |
| 9 | Continuity Strategies | ✅ | `/bcm/readiness` tab "Strategies" | Strong. 15 strategy types, cost-flagging for executive approval, RTO/RPO enforcement on approval. |
| 10 | BCP Register | 🟡 | `/bcm` tab "Plans" | Status workflow (DRAFT → UNDER_REVIEW → APPROVED → INVOKED) exists but **no activation criteria, no comms tree linkage, no recovery steps, no manual workaround section, no supplier-escalation section.** A BCP is captured as 6 fields: name × type × version × status × review date. That's a policy register, not a BCP. |
| 11 | BCP Builder | ❌ | — | No multi-section builder. The "Sections" list in spec §H (plan overview · scope · roles · activation criteria · recovery priorities · recovery procedures · communication plan · manual workaround · resource requirements · supplier escalation · return to normal · appendices) has zero implementation. |
| 12 | DRP Register | ✅ | `/dr-plans` | Best-developed BCM page. Status workflow, failover/failback JSON, test outcome recording, next-test-due auto-schedule. |
| 13 | DRP Builder | ❌ | — | No multi-section builder. Inline form modal only. |
| 14 | Crisis Room | 🟡 | `/crisis-management` | Has tier selector + invocation state machine + activation banner. But the page operates on `BcmInvocation`, not on a Crisis Event entity. Situation reports, decisions log, regulatory notifications log — **none of these columns exist anywhere**. |
| 15 | Crisis Event Detail | ❌ | — | No detail view; clicking an active invocation does not navigate to a dedicated event page. |
| 16 | Communication & Call Tree | ✅ | `/bcm/readiness` tab "Call Tree" + `/bcm` tab "Comms" | Two implementations again. Call-tree contacts with test result recording (BCM-2) is solid. CrisisCommsTemplate / Audience / Distribution is the legacy track and is SIMULATED-only (no real channel integration). |
| 17 | Exercises & Testing | 🟡 | `/bcm` tab "Exercises" + `/bcm-lifecycle` tab "Drills" | Two implementations. Neither has the scenario builder, injects, role assignments, success-criteria checklist, observation timeline the spec describes. "Complete exercise" persists a score + findings string — useful, but not enough. |
| 18 | Lessons Learned | ❌ | (backend entity exists, no page) | `BcmLessonLearned` entity + repository + service all exist. **There is no frontend page.** Users cannot browse, create, or approve lessons. |
| 19 | Evidence Repository (BCM view) | ❌ | (generic `/evidence` only) | The spec calls for a dedicated BCM evidence view filtered by `bcm_area` + `related_record_type`. The platform has a generic Evidence module but no BCM-specific lens. The BCM entities themselves only carry `evidence_id` (soft FK with no enforced relationship). |
| 20 | Gaps & Corrective Actions | 🟡 | (backend exists, no register page) | `BcmGap` and `BcmCorrectiveAction` exist as entities + services + controllers + repos. **There is no register page.** Cycle workspace only shows aggregate counts in a KPI strip. Users cannot list, filter, assign, or close a gap from the UI. |
| 21 | Compliance Mapping | ❌ | — | The spec wants ISO 22301 / DGA / SAMA / NRC requirement-to-evidence mapping. Nothing exists. F1 Authority Documents catalog can host the requirements list but no BCM-specific mapping page renders it. |
| 22 | Reports | ✅ | `/bcm/reports` | 15 reports as catalogued, payload renderer. Missing: Generate Markdown / HTML / CSV export, Submit-for-review, Approve workflow on each report (spec §Q). Today reports are read-only one-shots. |
| 23 | Settings | ❌ | (generic `/settings`) | The 13 configurable settings in spec §R (risk matrix, RTO thresholds, exercise frequency, evidence expiry rules, SLA rules, approval matrix, notification rules, framework version, etc.) have no UI and no backend persistence. |
| 24 | Import Regulatory Framework | ❌ | — | No import-CSV / import-XLSX / import-XML page anywhere. F1 authority documents are DB seeds only. |
| 25 | Audit Trail | 🟡 | (generic `/audit` filter) | F7 audit log carries `sourceModule='bcm'`. No dedicated BCM-filtered audit page. The `/bcm-lifecycle` history tab is a different scope (only lifecycle invocation transitions). |

**Coverage score:** 6 ✅ · 9 🟡 · 10 ❌ → **45% functional** against the 25-page spec. Headline missing items: Critical Services, BIA wizard, BCP/DRP builders, Crisis Event entity, Lessons page, Gaps register page, Compliance mapping, Settings, Import framework.

---

## 3. End-user acceptance test — five critical journeys, walked click-by-click

I walked each journey from the perspective of a customer assigned to one of the 25 BCM roles in spec §9. Each test specifies the click sequence, where the workflow stops or breaks, and what an industry-leader product does at the same step.

### Journey A — BCM Manager runs the first annual readiness cycle

**Persona:** BCM Manager (Riyadh head office, year 1 of platform deployment).
**Goal:** stand up the 2026 BCM readiness cycle end-to-end as a pilot.

| Step | What the user expects | What actually happens | Verdict |
|---|---|---|---|
| 1 | Open BCM module. | `/bcm` loads. Eight sidebar entries are shown (BCM, BCM Dashboard, BCM Readiness, BCM Reports, BCM Lifecycle, BCM Dependency, Crisis Management, DR Plans). **User cannot tell which page to start on.** | 🔴 IA failure |
| 2 | Click "BCM Readiness" → Cycles tab → "Create cycle". | Form modal: name, year, dates, BCM manager, executive sponsor. Submits. New cycle row appears in DRAFT. | ✅ Works |
| 3 | Transition cycle DRAFT → IN_PROGRESS. | "Start cycle" button on row. Status updates. F7 audit row written. | ✅ Works |
| 4 | Add Governance Record (policy version, scope, committee). | Governance tab → "Add policy" form. Committee field is a free-text BIGINT (`committeeId`), with no committee picker. Roles & responsibilities matrix from spec §B step 7 has no UI. | 🟡 Partial — works for the metadata, missing the actual governance content |
| 5 | Define BCM Scope. | Spec §B step 2: "Define BCM Scope". **There is no Scope field on the governance record beyond a free-text textarea.** Industry leaders ship a multi-dimensional scope picker (entities × geographies × business lines × technology layers). | 🔴 Missing |
| 6 | Add Critical Services. | No Critical Services page. User goes to `/bcm` → Processes tab and adds processes instead. They cannot mark a service as "Sovereign / National Critical" — the criticality enum is HIGH/MEDIUM/LOW only, not the 5-level scale from spec §C. | 🔴 Missing |
| 7 | Run BIA on each critical process. | `/bcm` → BIA tab → "Create BIA". Modal asks for: process ID, assessment date, RTO/RPO/MTPD hours (3 numeric fields), overall impact (enum), linked risk ID. **User cannot capture per-time-band impact (0-2h, 2-4h, 4-8h, 8-24h…). User cannot capture the 10 impact dimensions (regulatory / financial / reputation / legal / public-service / national-sovereign / data / cyber / supplier). MBCO is missing. Minimum-resources are missing. Manual-workaround flag is missing.** | 🔴 BIA is unusable for ISO 22301 evidence |
| 8 | Define continuity strategies for each BIA. | `/bcm/readiness` → Strategies tab → "Add strategy". 15 strategy types available. **Strategy cannot be linked to a specific BIA in the UI** (the `relatedBiaId` field exists on the entity but the form modal doesn't ask for it). | 🟡 Backend yes, UI no |
| 9 | Create BCP from approved BIA. | Spec §H step 1: "Create BCP from approved BIA". **There is no "Generate BCP from BIA" button anywhere.** User goes to `/bcm` → Plans tab and creates a BcmPlan from scratch — 6 fields, no link to the BIA, no recovery steps. | 🔴 Missing |
| 10 | Create DRP. | `/dr-plans` → "New plan". The DRP form is the most complete in the system (failover/failback JSON, RTO/RPO, site type, backup strategy). Still no "Generate DRP from BIA" button. | 🟡 Strong form, no generation path |
| 11 | Schedule and run an exercise. | `/bcm` → Exercises tab → "Create exercise". 6-field modal: name, type, scheduled date, plan ID, facilitator, scenario, findings. **Cannot define injects, success criteria, observation timeline, role assignments.** | 🔴 Not exercise-management-grade |
| 12 | Mark exercise failed → expect auto-CAPA. | Spec §7 rule 10: "Exercise fails → create gap + corrective action". F8 rule BCM-T3 exists in the seed. **But the executor stub only writes an audit row** (until the F8 follow-up landed in PR #8 — and even then CREATE_RECORD is still audit-only). Result: no Gap row, no CAPA row appears. User must create them manually. | 🔴 Rule fires but does nothing visible |
| 13 | Browse the Gap register. | **No page exists.** User cannot list gaps. | 🔴 Missing |
| 14 | Approve cycle, generate readiness report. | `/bcm/reports` → BCM_READINESS → "Download JSON". Works. Spec calls for Markdown / HTML / CSV exports + Submit-for-review + Approve workflow on the report. Not present. | 🟡 |

**Score for Journey A:** 4 ✅ / 4 🟡 / 6 🔴. **The customer can stand up a readiness cycle as a tracking shell but cannot do a real BIA, cannot build a real BCP, cannot register gaps, cannot rely on automation rules to create work.**

---

### Journey B — IT Recovery Owner runs a DR test and tracks the gap

**Persona:** IT Recovery Owner (data-centre operations).
**Goal:** approve a DRP, run the quarterly DR test, log a failure, get a corrective action created automatically.

| Step | Expected | Actual | Verdict |
|---|---|---|---|
| 1 | `/dr-plans` → New plan → Fill 12 fields (code, title, system, site type, RTO/RPO/MTPD, failover steps JSON, failback steps JSON, backup strategy). | Works. DRAFT row appears. | ✅ |
| 2 | Approve plan. | "Approve" → status APPROVED, approvedAt + approvedBy stamped. | ✅ |
| 3 | Activate plan (production-ready). | "Activate" → status ACTIVATED. | ✅ |
| 4 | Click "Record test" → outcome=FAILED, notes "site failover took 9h vs. RTO 4h". | Test record persisted. `nextTestDue` auto-set to +6 months. | ✅ |
| 5 | Expect Gap + CAPA auto-created (spec §7 rule 9). | **Nothing happens.** No F8 event is published from `DrPlanService.recordTest`. The seeded BCM-T3 rule is wired to `BCM_EXERCISE_FAILED`, not `DR_TEST_FAILED`. | 🔴 |
| 6 | Manually create Gap. | No Gap register page in UI. Could POST to `/api/v1/bcm/gaps` but no UI route. | 🔴 |
| 7 | Manually create CAPA. | Same as gap — backend API exists, no UI. | 🔴 |
| 8 | See "DR test outcomes" report. | `/bcm/reports` → No DR-test-specific report in the 15-catalogue. CAPA_TRACKER report is the closest. | 🔴 Missing |

**Score:** 4 ✅ / 4 🔴. **The DRP flow is well-built up to the test recording; everything past the test is broken or requires API calls outside the UI.**

---

### Journey C — Crisis Manager activates a crisis after a ransomware incident

**Persona:** Crisis Manager.
**Goal:** open a crisis event, activate the relevant BCP + DRP, notify the executive team, log decisions, escalate to NCEMA, close the event with lessons learned.

| Step | Expected | Actual | Verdict |
|---|---|---|---|
| 1 | Navigate to Crisis Room. | Two routes available: `/crisis-management` and `/bcm` → Comms tab. **No path links them.** | 🔴 IA |
| 2 | Create new Crisis Event with severity = Crisis (level 4). | `/crisis-management` → "Start activation" button. Form asks for: SCC tier (TIER-1-NATIONAL / TIER-2-CRITICAL / TIER-3-IMPORTANT / TIER-4-LOCAL). Spec calls for severity 1-5 (Operational Event → National Emergency). **The tier model in the code is NCEMA-aligned and does not match the spec's severity model.** Pick TIER-2 as proxy. | 🟡 Different taxonomy |
| 3 | Affected services / processes / sites / suppliers / systems fields. | Form has none of these. Only tier + scenario picker + free-text "title". | 🔴 |
| 4 | Activate BCP. | Spec §J step 5: "Activate BCP / DRP if needed". **There is no "Activate BCP from crisis" button.** The crisis page does not list or reference any BcmPlan row. | 🔴 |
| 5 | Activate DRP. | Same — no link. | 🔴 |
| 6 | Notify stakeholders. | "Acknowledge contact" button + call-tree integration. Works for the contact list. **Mass notification (SMS / voice / email) is SIMULATED only.** Industry standard is Everbridge / OnSolve / AlertMedia integration; here it just writes a DB row called CrisisCommsDistribution with `delivery_status='SIMULATED'`. | 🟡 Wired but not connected |
| 7 | Add Situation Report. | Spec §J step 7: "Track situation reports". **No `situation_reports` table or field on any entity.** Users cannot log SitReps. Industry leaders ship a SitRep timeline (every 30/60 minutes during a major incident). | 🔴 Critical gap |
| 8 | Log decision (CEO declared work-from-home). | Spec §J step 8: "Track decisions". **No decisions log anywhere.** | 🔴 |
| 9 | Track regulatory notifications (SAMA, NCEMA, NCA). | "Log regulatory notification" button exists on `/crisis-management` and persists to an invocation row. Works. | ✅ Partial |
| 10 | De-escalate / close event. | "Stand down" button → status STAND_DOWN. Works. | ✅ |
| 11 | Lessons learned. | Spec §J step 12: "Conduct lessons learned". **No lessons-learned page or form. Backend entity exists with full lifecycle but no UI.** User cannot capture lessons. | 🔴 |
| 12 | Create corrective actions from the lessons. | Same — no register UI. | 🔴 |
| 13 | Export crisis report. | `/crisis-management` → "Download NCEMA packet" exists. SAMA OPRES packet on `/bcm-lifecycle`. **No general "Crisis Event Report" export from `/bcm/reports` catalogue.** | 🟡 |

**Score:** 2 ✅ / 3 🟡 / 8 🔴. **Crisis management is the weakest end-to-end journey. The platform looks like it supports crisis ops (because the NCEMA tier page is visible) but the Crisis Event entity called for by the spec doesn't exist. SitReps and decisions are entirely missing.**

This is the gap that would lose a real customer demo to ServiceNow IRM or MetricStream.

---

### Journey D — Compliance Officer maps BCM activities to ISO 22301 + SAMA + DGA

**Persona:** Compliance Reviewer.
**Goal:** show the regulator that every ISO 22301 clause has at least one evidence link.

| Step | Expected | Actual | Verdict |
|---|---|---|---|
| 1 | Open Compliance Mapping page. | **No such page.** Sidebar has no entry. | 🔴 |
| 2 | Import the ISO 22301 control library. | **No import UI.** F1 Authority Documents are DB seeds only. | 🔴 |
| 3 | Map BCM workflow → ISO 22301 clause. | **No mapping table.** The `/bcm` page has a "Regulatory Alignment" tab (F5) but it shows coverage at the framework level, not clause × evidence. | 🟡 Adjacent feature |
| 4 | Attach evidence to a clause. | Cannot do this from BCM module. The generic `/evidence` page lets you upload but doesn't show a "linked clauses" view. | 🔴 |
| 5 | Generate ISO 22301 Readiness Report. | Spec §Q report #11. **Not in the 15-report catalogue.** Closest is BCM_READINESS which has no clause-level breakdown. | 🔴 |
| 6 | Generate DGA / SAMA / Regulatory Readiness Report. | Spec §Q report #12. **Not in catalogue.** | 🔴 |

**Score:** 0 ✅ / 1 🟡 / 5 🔴. **This journey is a complete fail. A regulator-facing customer (any KSA bank or government entity) needs this to pass an external audit. As-is, the platform cannot produce ISO 22301 evidence packs.**

---

### Journey E — External Auditor downloads the audit evidence package

**Persona:** External Auditor (read-only).
**Goal:** download a single zipped package containing all approved BCM evidence for the 2026 cycle.

| Step | Expected | Actual | Verdict |
|---|---|---|---|
| 1 | Log in with External Auditor Read-Only role. | The role exists in spec §9 (#24). **Role does not exist in `iam.roles`.** Auditor would log in as PLATFORM_ADMIN or AUDITOR (generic). | 🟡 |
| 2 | Open BCM evidence package generator. | Spec §N + §Q report #15: "Audit Evidence Package". **No UI button, no API endpoint.** | 🔴 |
| 3 | Filter by cycle, approval status, date range. | n/a | 🔴 |
| 4 | Download ZIP of all approved evidence files. | n/a — no evidence packaging service exists anywhere in the platform. | 🔴 |

**Score:** 0 ✅ / 1 🟡 / 3 🔴. **No way to produce an auditor pack. This is a feature every named GRC tool ships out of the box.**

---

## 4. Industry-leader gap analysis — what the named competitors do that this doesn't

I picked features that ALL of ServiceNow IRM, MetricStream BCM, OneTrust GRC, Riskonnect BCM, RSA Archer BCM, and LogicGate ship by default. Each one represents a customer-demo "smell test" the platform fails today.

| # | Industry-standard feature | Why customers expect it | What this platform has | Effort to close |
|---|---|---|---|---|
| 1 | **Dependency graph visualiser** with services → processes → applications → infrastructure → suppliers, navigable + searchable + heatmap-overlaid | Required for ISO 22301 §8.2.3 (interdependencies). Customers literally demo this first. | `/bcm-dependency` shows a process picker + tree on the right — not interactive, no overlays, no search, uses mock data. The `/bcm` graph tab is similar (mock). | Medium — graph data exists in `ProcessDependency`; needs proper Cytoscape/react-flow component |
| 2 | **BIA wizard with impact-time-band matrix** (rows = time bands 0-2h…>7d, columns = impact dimensions × 1-5 score) | The default BIA artifact in every commercial tool. ISO 22301 §8.2.2 evidence. | 6-field modal with single `overallImpact` enum. | High — full new wizard + entity schema extension |
| 3 | **Plan editor with sections + version diff** (rich text with named sections, version-controlled, diff between v1 and v2) | Auditors compare plan versions. Required by SAMA BCM §6.4. | `BcmPlan.version` is a string field, no body, no sections, no diff. | High — content schema + Tiptap editor + diff view |
| 4 | **Recovery sequencer / Gantt** showing recovery activities on a timeline with dependencies + responsible roles | The "what happens when" view a crisis team uses on day-0. | None. Failover_steps_json is a flat array. | High |
| 5 | **Mass Notification / Emergency Mass Notification** integrated (Everbridge / OnSolve / AlertMedia / SMS gateway) | Mandatory for real crisis ops. Spec calls for it as "(Placeholder)" but real customers won't accept placeholder. | `CrisisCommsDistribution.delivery_status='SIMULATED'` only. | High — vendor contract + integration |
| 6 | **Plan distribution + acknowledgement tracking** (who has read v3 of the BCP, who hasn't acknowledged) | Standard control evidence ("plan was distributed to all key personnel"). | None. | Medium |
| 7 | **Mobile-ready offline plan view** (printable PDF + offline mobile app for crisis day) | A BCP that lives in a web app you can't reach when the data centre is down is useless. | No PDF export of plans. No offline mode. | Medium |
| 8 | **Threat intelligence feeds** wired to crisis activation (weather, cyber, political risk APIs auto-elevate severity) | The "smart" crisis room features in modern tools. | None. | High |
| 9 | **Quarterly attestation campaigns** ("BCP owner: confirm your plan is current") | The single most efficient way to keep plans fresh. | The platform has an Attestation module (`AttestationCampaignsPage`) but it's not wired to BCP/DRP review dates. | Low — wire-up only |
| 10 | **Continuity Readiness Score per service** (composite: BIA approved ✓ + BCP approved ✓ + DRP approved ✓ + last test passed ✓ + evidence current ✓) | The headline metric every CXO asks for. | Each entity has its own status but no composite score. The `BcmAssessmentCycle.overallScore` is a free integer with no formula. | Low — formula service |
| 11 | **Plan templates library** (pre-built BCPs for "data centre outage", "ransomware", "supplier failure", "key person", etc.) | Reduces time-to-first-plan from weeks to hours. | None. | Medium |
| 12 | **Exercise scenario library** with injects, timed events, role-based instructions | Out-of-box exercise content. | None. Exercise has a `scenario` free-text field. | Medium |
| 13 | **Supplier continuity portal** (vendors upload their own BCP, attest annually) | Closes the SCM ↔ BCM loop without manual chasing. | None. SCM module exists but no portal. | High — needs portal + auth |
| 14 | **Management Review meeting workflow** (agenda, minutes, decisions, action items per ISO 22301 §9.3) | Required ISO clause. | None. | Medium |
| 15 | **Training & awareness tracking** (who got BCP training, when, refresh due) | ISO 22301 §7.3 evidence. | None. | Medium |
| 16 | **Plan tester role-play simulation** (walk through the plan answering "now what?" prompts) | Tabletop facilitation aid. | None. | Medium |
| 17 | **Real-time crisis room dashboard** (open SitReps, action queue, comms log, decision log) | The single screen a Crisis Manager runs the event from. | `/crisis-management` page is close but missing SitReps + decisions + action queue. | Medium — UI on top of new entities |
| 18 | **Geographic / map view** of sites, suppliers, recovery sites, active incidents | Standard since ~2018 in the market. | None. | Medium |
| 19 | **Public status page** for customers during an outage (Statuspage-style) | Expected by anyone selling to consumer / enterprise. | None. | Medium |
| 20 | **SIEM / SOAR / monitoring API hooks** that auto-create incidents and elevate to crisis | Cyber-disruption automation. | F8 automation rules exist but no inbound webhook surface. | Medium |
| 21 | **Insurance / claim linkage** (link transferred BCM risks to insurance policies) | Risk-financing evidence. | None. | Low |
| 22 | **Risk heatmap with velocity dimension** (how fast a risk materialises, not just likelihood × impact) | Modern enterprise risk practice. | Likelihood × impact only. | Low — schema field + viz |
| 23 | **Resource catalogue** (staff / facilities / equipment / technology with capacity) that BIA picks from | The "minimum resources" requirement in BIA needs a catalogue to pick from. | None. | Medium |
| 24 | **Plan content imports** (Word/PDF → structured plan sections) | Migration tool customers love. | None. | High |
| 25 | **What-if simulation / dependency impact analysis** ("if the supplier X is unavailable for 48h, which services breach RTO?") | The killer feature in MetricStream's BCM module. | None. | High — graph + propagation engine |

---

## 5. Saudi / regional regulatory gaps

The plan name-checks ISO 22301, DGA, SAMA, NRC. Below is what each KSA regulator actually demands that is **not** present today, ordered by exam-day risk.

| Regulator / framework | What they demand | What this platform has | Status |
|---|---|---|---|
| **NCEMA (المركز الوطني لإدارة الكوارث والأزمات)** — Crisis & Disaster Management Authority | A formal Crisis Coordination Centre (CCC) interface with tier classification, NCEMA notification log, after-action report submission. | `/crisis-management` page has NCEMA tier picker (TIER-1 → TIER-4). NCEMA packet download exists. No after-action report builder. SitReps absent. | 🟡 Strongest of the four |
| **SAMA Operational Resilience Framework (OPRES, 2022)** + SAMA Business Continuity Framework | Quarterly OPRES packet covering critical operations, impact tolerance, mapping, scenario testing, lessons learned, board approval. | `/bcm-lifecycle` has SAMA OPRES packet builder (TXT + PDF download). Strong start. Missing: impact tolerance per critical operation, scenario testing log, formal lessons section. | 🟡 |
| **NCA (هيئة الأمن السيبراني) — ECC / CSCC** | BCM is implicit in ECC §3-5 (Cybersecurity Resilience). Critical & Sovereign sectors must demonstrate cyber-disruption continuity capability. | Cyber module exists separately. BCM ↔ Cyber bridge exists (`cyber-pdpl-bridge`). No "cyber disruption continuity capability" report. | 🔴 |
| **DGA (هيئة الحكومة الرقمية)** — IT Governance & Continuity controls for government entities | Digital service availability targets, DR drills, citizen-service continuity reporting. | None of this is BCM-specific in the code. | 🔴 |
| **CMA (هيئة السوق المالية)** — for listed entities | BCM disclosure in annual reports, board oversight evidence. | No CMA disclosure template anywhere. | 🔴 |
| **MoH / MoE / MEWA (sector regulators)** | Sector-specific continuity requirements (hospitals, schools, utilities). | None. | 🔴 |
| **Civil Defense (المديرية العامة للدفاع المدني)** | Evacuation drills, fire-life-safety records, building permits. | None. | 🔴 |
| **Vision 2030 critical sectors** | Sovereign / national critical service identification with explicit national-sovereignty impact dimension. | Process criticality enum is HIGH/MEDIUM/LOW only — no Sovereign/National tier. | 🔴 |
| **Hajj / Umrah / Ramadan stress windows** | Demand-period readiness reporting (Mecca, Madinah, holy-site critical services). | None. A KSA-native product is expected to have a "peak-season readiness" lens. | 🔴 |
| **Saudi PDPL Article 23** | Personal data breach impact on continuity must be captured. | The spec acknowledges this; the implementation has no PDPL-breach linkage on the crisis flow. | 🔴 |

---

## 6. Architectural recommendations (in priority order)

### Tier 1 — must-fix before any customer demo

1. **Consolidate the three BCM tracks** (see §1). Pick the cycle track as the canonical model. Migrate legacy `BcmPlan` rows into the cycle's BCP entity. Make `/bcm-lifecycle` and `/bcm` redirect to a single workspace.
2. **Build the Crisis Event entity** with proper columns: `severity` (1-5 per spec), `affected_services` (FK), `affected_processes` (FK), `affected_sites`, `affected_suppliers`, `affected_systems`, `crisis_manager_id`, `crisis_team_json`, `status`. Build the **SitRep timeline** and **Decisions log** sub-tables. Build the **Crisis Room page** as a dedicated detail view.
3. **Build the BIA wizard** with the impact-time-band × impact-dimension matrix. Add MBCO, minimum-staff, minimum-tech, minimum-facility, manual-workaround fields. Lock RTO ≤ MTPD validation. Lock "Critical service requires approved BIA" rule.
4. **Build the BCP + DRP plan editors** with named sections + version diff. Implement "Generate BCP from approved BIA" and "Generate DRP from approved BIA" buttons.
5. **Build the Lessons Learned page** + **Gaps & CAPA register page** + **Compliance Mapping page** + **Settings page**. All four have backend entities already; the deficit is purely UI.
6. **Wire DR test failure → auto-CAPA**. Today the F8 rule is stubbed; the new F8 follow-up handlers write an audit-only entry. Wire `CREATE_RECORD` to actually call `BcmRemediationService.createCapa(...)`.

### Tier 2 — customer-table-stakes

7. Mass notification integration (real SMS / voice / email channel).
8. Dependency graph viz with real data (replace the mock-data tree).
9. Continuity Readiness Score formula service.
10. Quarterly attestation campaign linkage to BCP review dates.
11. Plan distribution + acknowledgement tracking.
12. PDF export of approved BCPs + DRPs + Crisis Event reports.
13. Audit Evidence Package generator (ZIP all approved evidence for a cycle).

### Tier 3 — differentiation

14. Threat intelligence feed integration (weather + cyber).
15. Plan templates library (8-10 canonical scenarios).
16. Exercise scenario library with injects.
17. Supplier continuity portal.
18. Management Review meeting workflow.
19. Geographic map view.
20. Public status page.
21. SIEM / SOAR inbound webhook surface.
22. What-if simulation engine.

### Tier 4 — KSA-native moats

23. NCEMA after-action report builder with auto-submit.
24. SAMA OPRES packet with impact-tolerance + scenario-testing sections.
25. NCA cyber-disruption continuity capability report.
26. CMA annual BCM disclosure template.
27. Sovereign / National-critical service tier (per Vision 2030).
28. Hajj / Umrah / Ramadan peak-season readiness lens.
29. PDPL Article 23 breach-continuity linkage.

---

## 7. Test-coverage gaps

The platform has 48 Mockito tests across the BCM cycle services (good). What's missing:

- **No API integration tests** — every controller is untested against a real HTTP layer.
- **No end-to-end (Playwright / Cypress) tests** — none of the five journeys above is automated.
- **No "spec rule" tests** — the 23 automation rules in spec §7 should each have a test that proves the rule fires on the right event and the right action happens.
- **No Mockito test for `DrPlanService`** — the most-developed BCM service has no unit tests.
- **No regression test for the three-track consolidation** — if you fix #1, you need a test proving old `BcmPlan` rows are reachable.

---

## 8. Closing summary

| Dimension | Score | Comment |
|---|---|---|
| **Spec coverage** (25 pages) | 45% | 6 ✅ · 9 🟡 · 10 ❌ |
| **End-user journey completeness** (5 walked) | 28% | 10 ✅ / 12 🟡 / 26 🔴 across 48 steps |
| **Industry-leader feature parity** (25 features) | ~8% | 2 fully shipped (cycle + DRP test); rest absent or stub |
| **Saudi regulatory readiness** (10 regulators) | 15% | NCEMA + SAMA OPRES partially landed; rest absent |
| **Architectural coherence** | poor | Three parallel BCM implementations, fragmented IA |
| **Backend depth** | strong | Solid entity model, F7 audit, F8 events, state machines |
| **Frontend depth** | weak | Two workspaces compete with each other, missing 9 pages, no detail pages |
| **Test coverage** | shallow | 48 unit tests, zero integration, zero e2e, several services untested |

**One-line verdict.** The BCM module has best-in-class backend foundations (F7 + F8 + state machines + cycle model) sitting under a fragmented and under-built UI, with three of the four most-demanded enterprise flows (Crisis Event, BIA wizard, BCP builder, Auditor pack) entirely missing. To be credible against the named GRC competitors in a KSA enterprise sale, the platform needs ~6 focused sprints addressing the Tier-1 list above, in that order.

---

*Audit complete. Prepared independently against the spec, using static code analysis and step-by-step simulation of the five canonical end-user journeys. No code was modified during this audit.*

# Forms, Screens, Modals, Navigation, Upload & Workflow-Entry Remediation Plan

**Scope:** all GRC platform modules (ERM, BCM, Cyber, SOC Ops, NCA Compliance, PDPL/Privacy, SDAIA NDI, ESG, Supply Chain, Evidence, Policy/Document, Corrective Actions, Incident, Audit, Reporting).
**Mode:** review + design only. **No implementation** until this plan is approved (per LOOP 9).
**Reviewer lenses:** GRC product architect · enterprise UX · workflow orchestration · Saudi GRC consultant · internal/external auditor · business/department/evidence/policy/risk/control/BCM/cyber/SOC/privacy/ESG owners · QA · bilingual AR/EN.

**Method (honesty note):** grounded in structural discovery of `tss-grc-ui-v2/src` (route table `App.tsx`, `grep` of `window.open`, `Modal`/`FormModal`/`Drawer`, upload components, `*Wizard*`), the shared component layer, and the platform's backend workflow subsystems — plus the deep per-module remediation already completed (BCM B-A…B-I, ERM E-*, Compliance C-*, ESG S-*, Cyber/SOCOps CY/SOC-*, Workflow WF-1…WF-10 + 14-module engine adoption). It is **not** an exhaustive line-by-line pass of all ~200 page files; matrices call out the flagship forms per module and representative patterns, and flag where a design recommendation (vs. audited current state) is being made.

---

## 1. Executive Summary

**Headline verdict: Partially usable trending Market-ready on the forms/workflow-entry axis — NOT demo-only.** The premise that "forms are demo popups with no upload, no workflow, browser pop-ups" does **not** hold for this codebase. Three findings dominate:

1. **Browser pop-ups for workflows: none.** Only 4 `window.open` calls exist — 3 open an *internal* route (`/governance/pivot/{risk|control|finding}/:id`) in a new browser tab, and 1 is a print-preview window (`lib/packetPrint.ts`, legitimate). There is **no** `window.open`-driven data-entry form anywhere.
2. **A real wizard subsystem already exists.** A shared **`WizardShell`** (step indicator, per-step validation gating, Back/Next/**Save Draft**/**Submit**, auto-save on step change, RTL) backs ~22 routed process wizards under `/workflow/*` — BIA, RCA, DSR, ROPA, Breach, Cross-Border, Consent, NCA ECC, Cloud CCC, Vulnerability Triage, Cyber Incident, Risk Identification/Treatment/Acceptance, BCM Exercise, Supplier Onboarding/Risk/Exit, Board Pack, Management Review, Digital Signature. These persist to a `workflow_submissions` backend and are launched from module pages via `WorkflowQuickLaunch`.
3. **A real request engine already exists and is adopted platform-wide.** WF-1…WF-10 (`workflow.requests`) provides actor selection (`WorkflowActorSelector`: USER/ROLE/DEPARTMENT/COMMITTEE/EXTERNAL × ASSIGNEE/REVIEWER/APPROVER/OBSERVER/ESCALATION), routing (SEQUENTIAL/PARALLEL ALL·ANY·QUORUM·MAJORITY/BROADCAST_ACK), SLA + reminders + multi-tier escalation, a 16-state lifecycle, per-recipient tokenised external delivery, and an append-only audit trail. As of the current adoption program, **all 14 modules** carry a `RaiseRequestButton` and a **live** `ModuleEngineRequestsPanel` (module- and, on SOC/Risk detail, record-scoped).

**File upload is broadly present:** a shared `FileUploadField`, `AttachmentsField`, `EvidenceRequestsField`, `EntityDocsModal`, and `lib/fileUpload.ts`; `FormModal` natively supports `kind:'file'` and `kind:'attachments'`; 57 files use upload. Evidence has a full request→upload→review→reject→resubmit→approve→expiry lifecycle (BCM B-D / WF-4 bridge).

**So what is actually wrong?** The residual issues are consistency, reconciliation, and a small number of genuinely missing/thin forms — not a structural absence of workflow. The material gaps:

- **G1 — Two parallel entry systems, weakly cross-linked.** Wizards persist to `workflow_submissions`; the engine persists to `workflow.requests`. A user can complete a wizard *or* raise a request, but a wizard submission does not automatically spawn engine tasks/approvals, and the two do not render on a single unified timeline. This is the deepest gap.
- **G2 — Missing named shared primitives.** `WizardShell` (= GRCWizardFormLayout) exists, but there is **no** generic `GRCStructuredDrawer`, `GRCQuickActionModal`, or `GRCFullPageFormLayout`. Mid-weight actions (assign owner, quick review, add follow-up, status-with-reason) are done as ad-hoc `FormModal`/`Modal` instead of a context-preserving drawer.
- **G3 — Record anchoring is shallow on most create flows.** `RaiseRequestButton` is placed module-level in page headers; only SOC Ops and Risk *detail* pages anchor requests to a specific record and show a record-scoped panel. Most detail pages don't yet let you raise/see requests *about this record*.
- **G4 — A few flagship forms are still quick `FormModal`s where a wizard/page is warranted**, and a short list of forms the business needs **do not exist** (patch request, containment request, data-sharing request, processor review, exception/risk-acceptance as first-class, KRI breach response) — designed in §9.
- **G5 — The 3 internal `window.open('…','_blank')`** break SPA context (fresh tab: no preserved auth/RTL/nav state). Low severity; convert to in-app navigation or a drawer.

**Bottom line:** the platform is a real workflow-driven GRC system, not disconnected screens. Remediation is a **focused backlog** (reconcile the two entry systems, add 3 shared primitives, deepen record anchoring, build ~6 missing forms, tidy 3 popups) — not a rebuild.

---

## 2. Modules Reviewed

ERM/Risk · BCM · Cyber · SOC Ops · NCA Compliance · PDPL/Privacy · SDAIA NDI · ESG · Supply Chain (TPRM/SCM) · Evidence · Policy/Document · Corrective Actions (CAPA) · Incident · Audit (external + internal `audit-ia` + `audit-lifecycle`) · Reporting. Cross-cutting: Workflow (engine + submissions + approval chains), Committees, Ethics, Governance pivot, Intake/Connectors.

---

## 3. Screen Inventory (Table 7.1)

Representative flagship screens (not the full ~200-route table; register/list pages omitted where a detail/wizard is the workflow surface).

| Module | Screen / page | Route | Purpose | Current status | Linked workflow | Missing lifecycle | Sev | Recommendation |
|---|---|---|---|---|---|---|---|---|
| ERM | Risk detail | `/risk/:id` | Risk lifecycle | **Real full page** (DetailHeader + tabs + rail + status actions) | Risk status machine + engine (record-anchored) | — | — | KEEP |
| ERM | Risk Identification / Treatment / Acceptance wizards | `/workflow/erm/*` | Structured intake | **Real wizards** (WizardShell) | workflow_submissions | Cross-link to engine | Med | IMPROVE (G1) |
| BCM | BIA wizard | `/workflow/bcm/bia` (`BiaWizardPage`) | BIA | **Real wizard + upload** | submissions → BCP/DRP generator (B-A) | — | — | KEEP |
| BCM | BCP builder / DR plans | `BcpBuilderPage` / `DrPlansPage` | Plan authoring | **Real builders + upload** | BCM lifecycle | — | — | KEEP |
| BCM | Crisis room | `CrisisRoomPage` | Live crisis mgmt | **Real page** | Crisis events | — | — | KEEP |
| Cyber | Cyber incident wizard + register | `/workflow/nca/cyber-incident` + CyberPage | NIST 800-61 | **Real wizard + full detail** | cyber incident 9-state | — | — | KEEP |
| SOC | SOC case workspace | `SocOpsDetailPage` | Case handling | **Real** (timeline, playbook runs, forensics custody, threat-intel actions, record-scoped requests) | socops + engine | — | — | KEEP |
| PDPL | DSR / ROPA / Breach / Cross-Border / Consent wizards | `/workflow/pdpl/*` | PDPL processes | **Real wizards** | submissions; breach → notification | DSR SLA countdown surfaced? verify | Med | IMPROVE |
| NDI | NDI management | `NdiPage` (+ record-scoped requests now) | Cycles/framework/maturity | **Real, tabbed** | engine (adoption) | Per-item evidence request from data owners | Med | IMPROVE |
| ESG | ESG workspace + Climate report | `EsgPage` + `ClimateAnnualReportPage` | GHG/metrics/report | **Real, live data + PDF** | engine + vendor ESG survey | — | — | KEEP |
| SCM/TPRM | Supplier onboarding/risk/exit wizards | `/workflow/scm/*` | Vendor lifecycle | **Real wizards** | submissions; external recipients via engine | — | — | KEEP |
| Evidence | Evidence workspace + requests | `EvidencePage` | Evidence lifecycle | **Real, full lifecycle** | evidence_requests (WF-4 bridge) | — | — | KEEP |
| Policy | Policy mgmt + detail | `PolicyManagementPage` / `PolicyDetailPage` | Policy lifecycle | **Real + upload + attestation** | policy states | Publish/attest routing via engine | Low | IMPROVE |
| CAPA | CAPA register + detail | `CapaPage` / `CapaDetailPage` | Corrective actions | **Real** | CAPA states + engine | Closure-evidence gate consistency | Med | IMPROVE |
| Incident | Incident register + RCA wizard | `IncidentsPage` + `/workflow/incident/rca` | Incident + RCA | **Real** | incident + RCA→CAPA | Per-record request panel on a detail page | Med | IMPROVE |
| Audit | Engagement detail (ext + `audit-ia`) | `EngagementDetailPage` / `IaEngagementDetailPage` | Audit engagements | **Real + upload** | audit lifecycle | — | Low | KEEP |
| Reporting | Reports Centre / schedules | `ReportsCentrePage` / `ReportSchedulesPage` | Report distribution | **Real** | engine (REPORT_DISTRIBUTION) | — | — | KEEP |

---

## 4. Form Inventory (Table 7.2)

Flagship create/action forms and their correct pattern. "Pattern OK?" = does the current pattern match the process weight?

| Module | Form | Opened by | Current pattern | Recommended | Upload req / exists | Actor sel req | Routing req | Lifecycle req | Backend persist | Sev | Fix |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ERM | Create risk | `NewRiskModal` + `RiskIdentificationWizard` | Modal **and** wizard | Wizard primary; modal = quick-capture | N/N | N | N | Y | Y | Low | KEEP both; make modal "quick capture → open wizard" |
| ERM | Risk treatment / acceptance | Wizard | Wizard | Wizard | opt / Y | Y (approver) | Y | Y | Y | — | KEEP |
| ERM | KRI breach response | — | **missing** | Drawer + engine task | N | Y | Y | Y | partial | **High** | **ADD** (§9.1) |
| BCM | BIA / BCP / DRP / Exercise | Wizards/builders | Wizard/page | Wizard/page | Y/Y | Y | Y | Y | Y | — | KEEP |
| Cyber | Control assessment | FormModal + evidence field | FormModal | Drawer (has upload) | Y/Y | opt | opt | Y | Y | Low | IMPROVE→drawer |
| Cyber | Vulnerability triage / exception | `VulnerabilityTriageWizard` / exception form | Wizard / modal | Wizard / drawer | opt/Y | Y | Y | Y | Y | Med | IMPROVE (exception→drawer + acceptance route) |
| Cyber | **Patch request** | — | **missing** | Drawer + engine task | opt | Y (IT owner) | Y | Y | no | **High** | **ADD** (§9.2) |
| SOC | Case / escalation / **containment request** | Case workspace | page / (containment **missing** as form) | page / drawer | Y/Y | Y | Y | Y | Y/partial | **High** | **ADD containment request** (§9.3) |
| PDPL | PIA/DPIA, DSR, Breach, Cross-Border | Wizards + `DpiaDetailPage` | Wizard/page | Wizard/page | Y/Y | Y | Y | Y | Y | — | KEEP |
| PDPL | **Data-sharing request** / **Processor review** | `DataSharingPage`/`ProcessorAssessmentsPage` list only | list, thin action | Drawer/wizard + route | Y | Y | Y | Y | partial | Med | **ADD/IMPROVE** (§9.4) |
| NDI | Framework-item evidence request | inline | inline → engine | Drawer (record-anchored) | Y/Y | Y | Y | Y | Y | Med | IMPROVE (anchor per item) |
| ESG | Metric collection / Scope-3 vendor request | EsgPage + vendor survey | page + real survey | KEEP | Y/Y | Y | Y | Y | Y | — | KEEP |
| Evidence | Evidence request | `RaiseRequestButton`/EvidenceRequestsField | drawer/field + engine | KEEP | Y/Y | Y | Y | Y | Y | — | KEEP |
| Policy | Create/upload/review/approve/attest | PolicyMgmt + detail | page + upload | KEEP; route approval via engine | Y/Y | Y | Y | Y | Y | Low | IMPROVE |
| CAPA | Create / closure-with-evidence | CapaPage/Detail | page + upload | KEEP; enforce evidence gate on close | Y/Y | Y | Y | Y | Y | Med | IMPROVE |
| Reporting | Report distribution | `RaiseRequestButton` (REPORT_DISTRIBUTION) | engine | KEEP | N | Y | Y | Y | Y | — | KEEP |

---

## 5. Modal & Browser-Popup Inventory

- **`Modal`/`FormModal` usage:** ~81 files. Dominant create/edit pattern is `FormModal` (declarative `FieldDef[]`, incl. `kind:'file'`, `kind:'attachments'`, `select-searchable`, `date`, `number`). Appropriate for CRUD; the concern is only where a process needs multi-step/routing depth (handled by wizards) or context preservation (would be better as a drawer).
- **Drawers present:** `ActivityFeedDrawer`, `HelpDrawer` — so the drawer *pattern* exists, but there is **no generic structured form drawer** for mid-weight actions.
- **Browser pop-ups (`window.open`):** 4 total →
  - `RiskRegisterPage`, `ControlLibraryPage`, `AuditLifecyclePage` → `('/governance/pivot/…','_blank')` (internal route, new tab).
  - `lib/packetPrint.ts` → print-preview window (acceptable).
  - **No workflow data-entry pop-ups exist.**

---

## 6. Upload Capability Inventory

- **Shared:** `components/ui/FileUploadField.tsx`, `AttachmentsField.tsx`, `EvidenceRequestsField.tsx`, `EntityDocsModal.tsx`, `lib/fileUpload.ts`; `FormModal` `kind:'file'|'attachments'`.
- **Coverage:** 57 files reference upload — incl. Evidence, Policy, CAPA, BIA/BCP/DR, Exercise (AAR), audit engagements, DSR/ROPA/Breach/Consent detail, control tests (CCM), vendor BCP, RRP, RegReporting, OpRes, COI, counterparty, AI model, attack-surface assets.
- **Gap:** upload exists broadly, but is not uniformly wired to the **evidence repository** with versioning/expiry/classification everywhere it should be (evidence + BCM are; several module ad-hoc `AttachmentsField` uses are local attachments, not first-class evidence objects). See §14.

---

## 7. Button → Form Mapping (representative)

- Register "New/Create" buttons → `FormModal` (CRUD) or navigate to a wizard route.
- `WorkflowQuickLaunch` (on module pages) → routes to `/workflow/**` wizards.
- `RaiseRequestButton` (all 14 modules) → engine composer (create → send).
- Row action menus → status transitions (drawer/modal) or navigate to detail page.
- Detail-page action bars → lifecycle transitions (submit/approve/reject/close) calling module APIs.

---

## 8. Form → Workflow Mapping

Two real backends, weakly linked (**G1**):

1. **Wizards → `workflow_submissions`** (`WorkflowSubmissionsPage`/`…DetailPage`): structured multi-step intake, upload, draft/submit; a submission record with its own status.
2. **`RaiseRequestButton`/engine → `workflow.requests`** (WF-1…10): actor routing, SLA, escalation, 16-state lifecycle, audit, external tokens; visible via `ModuleEngineRequestsPanel` + `WorkflowRequestsPage`/`…DetailPage` + inbox.
3. **Module state machines** (risk, cyber incident, evidence, policy, CAPA, BCM) — persisted per module, with the engine now layered on top for cross-cutting requests.

**Gap:** a wizard submission cannot auto-spawn engine approval/assignment tasks, and the two do not share one timeline. Reconciliation is the primary architectural fix (§9.0, §25–26).

---

## 9. Ideal Screen & Form Design by Module — incl. **designs for missing/thin forms**

> Per the request: where a form does not exist (or is too thin for the business need), a from-scratch design is given from a GRC-expert POV — fields, routing, lifecycle, upload, SLA, audit, AR/EN.

### 9.0 Cross-cutting design — reconcile the two entry systems (G1)
- On wizard **Submit**, optionally spawn a `WorkflowRequest` (approval/assignment) via the engine, stamped with `sourceModule` + `sourceRecordType`/`Id` = the submission (or the domain record it created).
- Add a **unified activity timeline** component that merges `workflow_submissions` events + `workflow.requests` events + module audit-log for a given record, so "raise → route → respond → approve → close" reads as one story.
- `WorkflowHubPage` becomes the single "my work" surface across submissions + requests + approvals.

### 9.1 ERM — **KRI Breach Response** (missing → design)
Trigger: a KRI crosses threshold (KriDetailPage / dashboard). **Pattern: structured drawer** (context = the KRI) that raises an engine task.
Fields: KRI ref (linked), breach value/threshold, breach date, severity (auto from band), root-cause note, **owner** (actor select), reviewer, **routing** (SEQUENTIAL owner→risk-manager), **due/SLA**, reminder, escalation tier, evidence upload (optional), linked risk(s). Lifecycle: Open→In Progress→Under Review→Closed (+Overdue/Escalated). Audit + dashboard (KRI status, appetite breach) impact. AR/EN.

### 9.2 Cyber — **Patch Request** (missing → design)
Trigger: from a Vulnerability row. **Pattern: structured drawer** → engine task to IT/asset owner.
Fields: vulnerability ref (linked), affected asset(s), CVE/severity/CVSS, target patch date, **assignee = asset owner** (actor select), reviewer, **SLA by severity** (NCA ECC bands), evidence-on-closure required (patch proof), rollback note, change-request link. Lifecycle: Requested→Assigned→In Progress→Evidence Submitted→Verified→Closed (+Overdue/Escalated). Feeds vuln-remediation KPIs. AR/EN.

### 9.3 SOC — **Containment Request** (missing as a form → design)
Trigger: from a SOC case. **Pattern: structured drawer** → engine task to IT/security owner, with chain-of-custody-aware evidence.
Fields: case ref, affected system/account, containment action (isolate/disable/block), justification, **assignee** (IT/security), approver (for high-impact), **priority/SLA (1h CRITICAL…)**, evidence upload (before/after), reversibility flag. Lifecycle: Requested→Approved→Executing→Confirmed→Closed. Writes to the case timeline + forensics custody ledger. AR/EN.

### 9.4 PDPL — **Data-Sharing Request** & **Processor Review** (thin → design)
- **Data-sharing request:** **wizard** (legal weight). Steps: requester + recipient party (internal/external), data categories + lawful basis (PDPL Art.), purpose/retention, risk screen, **routing** privacy→legal→executive approval (SEQUENTIAL), DPA/attachment upload, decision + conditions. Lifecycle full; SLA; audit; register entry.
- **Processor review:** **wizard/drawer** anchored to a sub-processor. Steps: processor + service, DD questionnaire, evidence request to processor (**EXTERNAL recipient** via engine token), risk rating, approval, periodic re-review reminder. Upload + expiry; audit.

### 9.5 Other modules
- **NDI:** per-framework-item **evidence request drawer** anchored to the item, EXTERNAL/data-owner recipient, upload to evidence repo, submission-package approval chain (largely present; deepen anchoring).
- **BCM/Evidence/ESG/Policy/CAPA/Audit:** already real; changes are IMPROVE-level (routing via engine, evidence-repo wiring, record-scoped panels) — not new builds.

---

## 10. UX Pattern Classification

| Correct pattern | Where it applies | State |
|---|---|---|
| **Full page** | Risk/SOC/incident/engagement detail; Crisis room; Reports Centre | **Present** |
| **Wizard** (WizardShell) | BIA/BCP/DRP, RCA, DSR/ROPA/Breach/Cross-Border/Consent, NCA ECC/Cloud CCC/Vuln/Cyber-incident, Risk ID/Treat/Accept, Supplier onboard/risk/exit, Board pack, Mgmt review, Digital signature | **Present** |
| **Structured drawer** | assign owner, quick review, status-with-reason, follow-up, KRI/patch/containment requests | **Missing generic primitive (G2)** — done ad-hoc as modals |
| **Quick modal** | confirm delete/archive/escalate, tag, short comment, acknowledge | **Present** (FormModal/ConfirmDialog) |
| **Browser popup** | (never for workflow) | **Correctly absent** |

---

## 11. Forms That Must Become Full Pages / Wizards
Essentially **already done.** No flagship complex process is trapped in a modal. Only *new* builds (§9) should be wizards/drawers, not modals. `NewRiskModal` may stay as quick-capture that hands off to `RiskIdentificationWizard`.

## 12. Forms That Can Remain Drawers / Modals
CRUD registers (assets, obligations, controls, KRIs, suppliers, metrics), status transitions, tag/comment/acknowledge, confirmations. These are correctly modals; migrate mid-weight ones to the new `GRCStructuredDrawer` for context preservation (optional, Low sev).

## 13. Browser Popups to Remove (Table 7.3)

| Module | Action | Current | Why wrong | Replacement | Sev | Fix |
|---|---|---|---|---|---|---|
| ERM | Risk row "Pivot" | `window.open('/governance/pivot/risk/:id','_blank')` | New tab loses SPA auth/RTL/nav context | In-app `navigate()` or right-drawer preview | Low | IMPROVE |
| Compliance | Control "Open pivot" | same | same | same | Low | IMPROVE |
| Audit | Finding "Pivot" | same | same | same | Low | IMPROVE |
| — | Packet print | `packetPrint.ts` print window | N/A | **Keep** (print preview allowed) | — | KEEP |

## 14. Missing File-Upload Gaps (Table 7.4)

| Module | Form | Business reason | Upload exists | Evidence-repo integrated | Versioning | Approval | Sev | Fix |
|---|---|---|---|---|---|---|---|---|
| Cyber | Control assessment | control implementation proof | Y (field) | partial (local attach) | N | via review | Med | wire to evidence repo |
| NDI | Framework-item | maturity/compliance proof | Y | partial | N | submission pkg | Med | wire to evidence repo + expiry |
| CAPA | Closure | closure evidence gate | Y | partial | N | on close | Med | enforce evidence-required to close |
| SCM | Supplier evidence (external) | processor/vendor proof | Y | Y (engine token) | N | review | Low | add expiry/classification |
| New forms §9 | patch/containment/data-share/processor | proof/DPA | design-in | **must integrate** | Y | Y | — | build with evidence block |

## 15. Missing Actor-Selection Gaps
Engine `WorkflowActorSelector` covers requests platform-wide. Gaps: module **state transitions** (e.g., "assign treatment owner", "assign patch to IT") still use free-text/simple selects in places rather than the actor selector → the new drawer forms (§9) should reuse `WorkflowActorSelector`.

## 16. Missing Routing Gaps
Engine supports SEQUENTIAL/PARALLEL(ALL·ANY·QUORUM·MAJORITY)/BROADCAST_ACK + conditional branch rules (WF-6). Gaps: wizard **submissions** don't select a routing mode (they follow a fixed submission flow) — §9.0 reconciliation lets a submission spawn a routed engine request where approval is needed (data-sharing, cross-border, policy publish).

## 17. Missing Lifecycle Gaps
Engine (16 states) + module machines are comprehensive. Residual: **new §9 forms** need their lifecycles wired; **NDI submission package** and **policy publish/attest** should route their approval through the engine rather than a local status flip so transitions are audited uniformly.

## 18. Missing SLA / Countdown Gaps
Engine requests carry due date + countdown + reminders + multi-tier escalation. Verify surfaced on: **DSR** (PDPL 30-day) and **breach** (72-hour) wizard/detail — these are regulatory clocks that must show a visible countdown on the record, not only inside an engine request.

## 19. Missing Audit-Trail Gaps
Engine writes append-only events; module services write audit logs (S-H wired ESG; cyber/BCM/ERM have audit). Residual: **wizard submissions** audit is submission-scoped — merge into the unified timeline (§9.0) so a reader sees one chain.

## 20. Missing Backend / API Gaps
- **New forms §9** need: KRI-breach task, patch request, containment request, data-sharing request, processor-review — most can be modelled as `WorkflowRequest`s with a typed `requestType` + `sourceRecordType` (little/no new schema), plus module links.
- **Reconciliation (G1):** submission→request spawn endpoint + a unified-timeline read that unions submissions + requests + audit for a record.
- **Record-scoped requests** endpoint already exists (engine list filters by `sourceRecordType`+`sourceRecordId`).

## 21. Arabic / RTL Gaps
`WizardShell`, engine composer/selector/panels, FormModal, detail headers are RTL-aware. Spot-audit needed on: the 3 pivot new-tab pages (fresh tab must re-assert `dir`), any new §9 drawers (build RTL-first), and the DSR/breach countdown chips (AR numerals/labels).

## 22. Dashboard / Report Impact Gaps
Engine requests + module records feed dashboards; `ModuleEngineRequestsPanel` surfaces live per module. Ensure new §9 request types roll into the relevant KPIs (KRI status, vuln SLA, DSR/breach clocks, data-sharing approvals) and the board pack.

## 23. Shared-Component Recommendations (LOOP 7 — status)

| # | Component | Status | Action |
|---|---|---|---|
| 1 | GRCFullPageFormLayout | Partial (DetailHeader + DetailLayout cover detail pages) | Formalise a thin wrapper; Low |
| 2 | GRCWizardFormLayout | **Exists = `WizardShell`** | KEEP |
| 3 | GRCStructuredDrawer | **Missing** | **ADD** (backs §9 forms) |
| 4 | GRCQuickActionModal | Partial (`FormModal`/`ConfirmDialog`) | Standardise; Low |
| 5 | WorkflowActorSelector | **Exists** | KEEP/reuse in drawers |
| 6 | WorkflowRoutingSelector | **Exists** (in composer) | Extract standalone; Low |
| 7 | SLAAndCountdownSelector | Partial (in composer) | Extract; Med |
| 8 | EvidenceUploadBlock | **Exists** (`EvidenceRequestsField`/`FileUploadField`) | KEEP |
| 9 | AttachmentVersioningBlock | Partial | ADD versioning/expiry/classification |
| 10 | CommentsAndHistoryBlock | Partial (per module) | Consolidate |
| 11 | WorkflowTimeline | Partial (engine detail) | **Generalise → unified timeline (G1)** |
| 12 | ApprovalChainBlock | **Exists** (approval-chains subsystem) | KEEP |
| 13 | EscalationPathBlock | **Exists** (WF-10 tiers) | KEEP |
| 14 | RelatedRecordsBlock | Partial (anchors panels) | Standardise |
| 15 | AuditTrailBlock | Partial | Consolidate into timeline |
| 16 | FormValidationSummary | Partial (WizardShell gating) | Extract; Low |
| 17 | SaveDraftSubmitActionBar | **Exists** (in WizardShell) | Extract standalone; Low |

## 24. Decision Gate (Table 7.6)

| Proposed change | Problem solved | Modules | Global/module | Benefit | Complexity | Risk | Decision | Priority |
|---|---|---|---|---|---|---|---|---|
| Reconcile submissions ↔ engine + unified timeline (G1) | disconnected entry systems | all | Global | High | High | Med | **ADD** | **P1** |
| `GRCStructuredDrawer` primitive (G2) | mid-weight context-preserving forms | all | Global | High | Med | Low | **ADD** | **P1** |
| KRI-breach / patch / containment / data-sharing / processor forms (§9) | genuinely missing entry points | ERM/Cyber/SOC/PDPL | Module (on drawer) | High | Med | Low | **ADD** | **P1–P2** |
| Deepen record-anchored raise+panel on detail pages (G3) | can't raise/see per-record | all detail pages | Global pattern | Med | Low | Low | **IMPROVE** | **P2** |
| Extract SLAAndCountdownSelector + surface DSR/breach clocks | regulatory countdowns | PDPL | Global | Med | Low | Low | **IMPROVE** | **P2** |
| Evidence-repo wiring + versioning/expiry for ad-hoc uploads | evidence integrity | Cyber/NDI/CAPA/SCM | Global block | Med | Med | Low | **IMPROVE** | **P2** |
| Route policy-publish/NDI-package/data-share approvals via engine | uniform audited approval | Policy/NDI/PDPL | Module | Med | Low | Low | **IMPROVE** | **P3** |
| Convert 3 pivot `window.open` to in-app nav/drawer | SPA context loss | ERM/Compliance/Audit | Global | Low | Low | Low | **IMPROVE** | **P3** |
| Rebuild existing wizards/pages | — | — | — | none | — | high | **REJECT** | — |

## 25. Data-Model Changes
- Minimal. New §9 forms ride on `workflow.requests` (typed `requestType`, `sourceRecordType`/`Id`) — no new tables for KRI-breach/patch/containment/data-share/processor beyond optional link columns.
- Reconciliation: add a nullable `spawned_request_id` on `workflow_submissions` (submission→request link) — one column, one forward migration (next free **V277**).
- Attachment versioning/expiry/classification: extend the evidence/attachment tables if not present (verify before adding).

## 26. API Changes
- `POST /workflow-submissions/{id}/spawn-request` (or a service hook on submit) → creates a routed engine request.
- `GET …/timeline?sourceRecordType=&sourceRecordId=` → unified union (submissions + requests + audit).
- New request types are data, not new endpoints (engine `create` already generic).

## 27. UI Component Changes
Build `GRCStructuredDrawer`; extract `SLAAndCountdownSelector`, `WorkflowRoutingSelector`, `SaveDraftSubmitActionBar`, `FormValidationSummary` from existing code; generalise `WorkflowTimeline` → unified; add `AttachmentVersioningBlock`. Wire §9 forms on the drawer. Convert pivot popups.

## 28. Testing Plan
- **Backend:** unit tests for submission→request spawn + unified timeline union + each new request type's lifecycle; keep the controller-authz ratchet green.
- **FE:** `tsc` + Vite transform; per new drawer form — open/validate/upload/actor-select/route/submit → engine request created; countdown renders; RTL.
- **Live (harness):** create each new request type → appears in record-scoped panel → respond/approve/close → audit chain; `[REVIEW-TEST]` + CHARINDEX cleanup.
- **Regression:** existing wizards still submit; evidence lifecycle intact.

## 29. Implementation Backlog
1. (P1) `GRCStructuredDrawer` + extract `SLAAndCountdownSelector`/`WorkflowRoutingSelector`/`SaveDraftSubmitActionBar`.
2. (P1) Submission↔engine reconciliation + unified `WorkflowTimeline` (+ `spawned_request_id`, V277).
3. (P1–P2) §9 forms: KRI-breach → patch → containment → data-sharing → processor review.
4. (P2) Deepen record-anchored raise+panel across detail pages.
5. (P2) Evidence-repo wiring + versioning/expiry for ad-hoc uploads; surface DSR/breach countdowns.
6. (P3) Route policy-publish / NDI-package / data-share approvals through engine; convert 3 pivot popups.

## 30. Recommended Implementation Order
Global primitives (1) → reconciliation/timeline (2) → missing forms on the new drawer (3) → anchoring (4) → evidence/countdown polish (5) → approval-routing + popups (6). Ship Critical/High first; run tests + update this file's status per batch.

## 31. Risks If Not Fixed
- **G1 unresolved:** users see two disjoint "workflow" views; auditors can't follow one chain → weak traceability story in a Saudi regulatory audit.
- **Missing §9 forms:** KRI breaches, patches, containment, data-sharing happen off-platform (email) → no SLA/audit → the exact "disconnected" failure the platform is meant to eliminate.
- **Shallow anchoring:** requests aren't discoverable from the record they concern → users re-raise duplicates.
- **Ad-hoc uploads not evidence objects:** proof isn't versioned/expiring/classified → audit-evidence integrity gap.

## 32. Final Recommendation
**Partially usable, trending Market-ready.** The forms/screens layer is genuinely workflow-driven: real wizards (WizardShell), a real request engine adopted across all 14 modules, broad file upload, and no browser-popup workflows. It is **not** demo-only and does **not** need a rebuild. To reach unambiguously Market-ready on this axis, execute the focused P1–P2 backlog: (a) reconcile the wizard-submission and request-engine entry systems behind one timeline, (b) add the `GRCStructuredDrawer` primitive, (c) build the ~5–6 genuinely missing request forms (KRI breach, patch, containment, data-sharing, processor review) on that drawer + engine, and (d) deepen record-level anchoring. These are additive, low-risk, and mostly reuse existing engine capability.

---

## Implementation status (2026-07-07)

**Delivered + merged to master:**
- **P1 primitives** — `GRCStructuredDrawer` + `WorkflowRoutingSelector` + `SLAAndCountdownSelector` (RaiseRequestButton refactored onto them). `454fc725`.
- **P1 reconciliation (G1)** — submission detail page raises engine requests anchored to the submission (`sourceRecordType=WorkflowSubmission`) + a record-scoped linked-requests panel beside the activity timeline. `991c1bee`.
- **§9 forms** — `GRCRequestDrawer` + KRI-breach / patch / containment / data-sharing / processor-review, wired into KriDetail / Cyber / SocOpsDetail / DataSharing / ProcessorAssessments; distinct `request_type`, no backend change. `8a03921e`.
- **P2/P3-A** — 3 governance-pivot `window.open` popups → in-app `navigate()` (also fixes a per-tab-`sessionStorage` auth bug); **P2/P3-E** — anchored RaiseRequestButton on Finding + CAPA detail. `3a92c3ea`.
- **P2/P3-C** — verified already present (DSR 30-day + breach 72-hour header countdown badges).

**Deferred by decision (2026-07-07):**
- **B** (auto-spawn-on-submit + `spawned_request_id` + merged-timeline) — SKIPPED as largely redundant: the P1 anchor-based linkage already makes a submission spawn + list engine requests, and the submission page shows activity + engine timelines side by side. The column duplicates the anchor; auto-spawn is awkward (engine requires recipients).
- **D** (evidence versioning/expiry/classification across modules) and **F** (engine-routed policy-publish / NDI-package / data-share approvals) — DEFERRED as their own scoped programs (large cross-cutting refactors), not blitzed.

---

### Review stats (LOOP 9)
- Modules reviewed: **16** · Flagship screens catalogued: ~**60** (representative) of ~**200** routes.
- Forms assessed: **flagship set across 16 modules** (full CRUD long-tail summarised, not enumerated line-by-line).
- Modals/FormModals: ~**81 files**. Wizards/drawers: **26 files** (22 process wizards + `WizardShell` + 2 drawers + activity/help).
- Browser popups found: **4** (3 internal-new-tab → convert; 1 print → keep). **0** workflow data-entry popups.
- Upload usage: **57 files** (shared upload layer present).
- Lifecycle/workflow gaps: **1 architectural (G1)** + **~6 missing forms** + IMPROVE-level anchoring/evidence/countdown items.
- Plan file: `FORMS_SCREENS_AND_WORKFLOW_ENTRY_REMEDIATION_PLAN.md` (repo root).
- **Next command to start implementation** (only on approval): *"Implement the P1 items from FORMS_SCREENS_AND_WORKFLOW_ENTRY_REMEDIATION_PLAN.md — GRCStructuredDrawer + SLAAndCountdownSelector extraction, then the submission↔engine reconciliation + unified timeline. Verify and stop for review before the §9 forms."*

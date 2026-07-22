# Change Management & Policy Management — Reality Review, Workflow Audit & Remediation Plan
### إدارة التغيير + إدارة السياسات

**Review type:** read-and-test only — **no product code changed** (per command LOOP 8 / §20: do not implement until a separate implementation command is issued).
**Date:** 2026-07-21 · **Reviewer roles:** GRC/ITSM architect, ITIL change-enablement & COBIT specialist, policy/document-control specialist, internal & external auditor, security, workflow architect, UX, QA, bilingual reviewer.
**Method:** repository inspection (backend + FE + DB), live "violent" API testing (Change), code+DB verification (Policy — live API blocked, see note), two parallel deep-read agents for Policy.
**Evidence:** `review/change-mgmt/` (DB inspection + 4 self-cleaning probe scripts) and the DB/automation queries + agent reports summarised here.

> **Live-testing note.** Change Management was fully live-probed earlier this session (all Critical findings reproduced on the running stack). Midway, the backend's **authentication path began returning empty-body 401 for every account** (admin/compliance/so_officer/viewer) with the DB lockout counters at 0 — a backend/DevTools-reload health issue **unrelated to either module** (login worked earlier; the `PublicEndpointRateLimitFilter` explicitly excludes `/api/auth/login`). Rather than restart the user's backend, Policy Management findings below are **code- and DB-verified** and labelled as such; each has a ready live-probe in the remediation test plan for re-run once auth is healthy.

---

## 1. Executive Summary

Both modules present a convincing surface and a real data layer, but both are **management systems only on the happy path** — the governance controls that make change-enablement and document-control auditable are, to varying degrees, decorative, bypassable, duplicated, or unfinished.

**Change Management — Partially usable.** A clean ITIL-v4 14-state machine with a real transition guard, F8 event emission, cross-module links and analytics. But: **no segregation of duties** (the sole `WORKFLOW` gate is granted to 54 roles incl. read-only `VIEWER` — live-proven a viewer created a change); **CAB approval is decorative** (no quorum, no SoD, votes don't gate the decision, approver identity never captured — live-proven a change reached `CAB_APPROVED` with zero votes and despite a recorded REJECT); and the **state machine is fully bypassable** via a plain `PUT` that sets any status (live-proven DRAFT→CLOSED). No input validation / no DB CHECK constraints; audit covers only 2 of ~11 operations; the "Export" button downloads the **Risk** register.

**Policy Management — Partially usable, but sprawling.** The canonical `/policy-management` module is, in several respects, **better-founded than Change**: DB CHECK constraints, a guarded state machine, a real multi-tenant scheduler (expiry + review-due sweeps), real file upload with SHA-256, a complete exception/waiver lifecycle, principal-based actor capture, and no demo-mock masking. But it is undermined by: **a duplicated policy domain** (two backend systems — `policy.documents` vs `compliance.policies` — plus a third localStorage template surface, none integrated); **a parallel façade detail page** (`/policies/:id`) whose workflow buttons are local status flips with mock fallback; **create-time lifecycle bypass** (POST with `status:"PUBLISHED"`); **façade-grade SoD** (the approver self-approval guard never fires because `createdBy` is never populated); **no structured editor and no real document behind 41 of 51 policies**; **no version compare** (and a string-`max()` "latest version" bug); and **attestation with no campaign/audience/reminder/escalation**.

**Cross-cutting:** F8 automation is emitted in code but the consuming rules for Change (`CHANGE_*`) and most of Policy are **absent in the running dev DB** (only `POLICY_PUBLISHED`/AR-15 is wired) — the automation is dormant. Neither module enforces the platform's Arabic-mandatory rule.

Neither module is production-ready for a regulated Saudi deployment (NCA ECC change control, SAMA CSF segregation, ISO 20000/9001/27001 document & change control) until the Critical findings are closed. **None require large rework** — they are guard-, validate-, merge- and finish-the-UI changes on sound data models.

---

## 2. Verified Standards and Practices

> **Web verification was unavailable in this environment** (the WebSearch backend errored). Editions below are stated **from reviewer knowledge and MUST be verified against the official source before use**. No copyrighted standard text is reproduced; standards are used only to derive control objectives and configurable mappings. Where licensed content is required it is marked *Placeholder — requires official licensed framework content / عنصر مؤقت — يتطلب محتوى الإطار الرسمي المرخص*.

| Framework | Edition (verify) | Relevance |
|---|---|---|
| ISO/IEC 20000-1 | :2018 (SMS) | Change management & release/deployment practices |
| ITIL 4 | current | Change Enablement practice; standard/normal/emergency models, CAB |
| ISO 9001 | :2015 (cl. 7.5 documented information) — *revision reportedly in progress; verify current status* | Policy/document control, controlled copies, review |
| ISO/IEC 27001 | :2022 (A.5.37 documented procedures; 8.32 change management) | ISMS policy set + change control |
| ISO 22301 | :2019 | Controlled continuity documents (policy overlap with BCM) |
| COBIT | 2019 (BAI06 Managed IT Changes; APO01 documented governance) | Change authorisation matrix, policy framework |
| NIST | SP 800-128; SP 800-53 CM-3 (Configuration Change Control) | Security change control objectives |
| NCA ECC | ECC-1:2018 / **verify ECC-2:2024** | Change management & documented cybersecurity policies (KSA) |
| SAMA CSF | 1.0:2017 (3.x) | Change management + segregation of duties (KSA financial) |
| PDPL (KSA) | 2023 + implementing regs | Personal-data policy handling, attestation decline → DPO |

**Control objectives derived (used to score findings):** authorisation proportionate to risk (not one generic approver); segregation of duties (author ≠ approver; requester ≠ CAB approver); emergency path + mandatory retrospective review; controlled documents with version, review cadence, approval provenance and acknowledgement; complete audit trail; tenant isolation.

---

## 3. Repository and Architecture Reviewed

- **Backend:** Spring Boot 3.5 modular monolith, Java 17, SQL Server, `@PreAuthorize("hasPermission(MODULE,ACTION)")`, `TenantContext` thread-local, F8 `DomainEvent` automation engine, `AuditLogService`, generic `AttachmentService` (SHA-256), workflow engine (`WorkflowRequest`), OpenPDF/POI exports.
- **Change Management:** `module/change_mgmt/` — 13 files, 967 LoC. Controller (161) `/api/v1/changes`; `ChangeManagementService` (423); 5 entities; 5 repos. Schema `change_mgmt` (5 tables). Automation `V146` (AR-96..100). FE `tss-grc-ui-v2/src/pages/changes/ChangesPage.tsx` (710) + `mockChanges.ts`.
- **Policy Management:** **two backends** — `module/policy/` (`PolicyController` `/api/policy`, `PolicyManagementService` 685 LoC, `PolicyComplianceScheduler` 131, entities PolicyDocument/PolicyAttestation/PolicyException/PolicyDocumentControlLink, schema `policy`, CHECK-constrained) **and** `module/compliance` (`Policy`, `PolicyService` 176, `RegChangePolicyController` `/api/v1/compliance/policies`, schema `compliance.policies`, `PolicyFrameworkMapping`). Automation `V141` (AR-71..75). FE: `pages/policy/PolicyManagementPage.tsx` (canonical, `/api/policy/*`), `pages/detail/PolicyDetailPage.tsx` (compliance + mock), `pages/policy-templates/PolicyTemplatesPage.tsx` (localStorage), `lib/policy.ts`, `lib/policyLifecycle.ts`, `lib/policyTemplates.ts`, components `PolicyLifecycleStrip`, `PolicyVersionHistory`.

---

## 4. Current Module Inventory

### 4.1 Change Management
- **Pages/tabs:** Register, Kanban, Workflow (approvals/tasks/comments), Analytics, Integrations (links), Comms.
- **APIs (18):** list/get/create/update/delete; `POST /{id}/transition`; approvals GET+POST; tasks GET/POST + PUT/DELETE `/tasks/{id}`; links GET/POST + DELETE `/links/{id}` + GET `/links/by-entity`; comments GET/POST; GET `/analytics`. All `hasPermission('WORKFLOW',…)` (READ/WRITE/DELETE — **no APPROVE**).
- **Data:** `change_requests` (14-state), `change_approvals`, `change_tasks` (no `is_deleted`), `change_links`, `change_comments`. **0 CHECK constraints.**
- **Automation:** V146 AR-96..100 (defined; **absent in dev DB**). **Scheduler: none.**

### 4.2 Policy Management
- **Surfaces (3, disconnected):** `/policy-management` (canonical, real, `/api/policy/*`, 4 tabs: overview/documents/exceptions/attestations); `/policies/:id` (compliance detail, 8 tabs, mostly façade + mock); `/policy-templates` (localStorage only).
- **APIs — `/api/policy` (22):** documents CRUD + transition + new-version + expiry-sweep + aggregate + evidence-package + resolved-controls + PUT controls; exceptions CRUD + transition + expiry-sweep; attestations list/create/attest/decline. **APIs — `/api/v1/compliance/policies` (12):** CRUD + submit-for-legal-review/legal-approve/exec-approve/mark-review-due/retire/reject + stats.
- **Data:** `policy.documents` (51 rows; 6-state; **CHECK-constrained**; `body_url`; **41/51 have no body_url**), `policy.attestations` (18), `policy.exceptions` (9; CHECK-constrained), `policy.document_control_links`; `compliance.policies` (17; **0 CHECK constraints**; "legacy read-only, write lifecycle retired in C-4"), `compliance.policy_framework_mappings` (**no `organisation_id`**).
- **Automation:** V141 AR-71..75 (defined; **absent in dev DB**); only AR-15 `POLICY_PUBLISHED` wired. **Scheduler: `PolicyComplianceScheduler`** (daily 01:50, multi-tenant, clean TenantContext hygiene) — expiry sweeps + review-due events.

---

## 5. Complete Real-World Task Catalogue (initiation → archive)

**Change (target 33 tasks — ITIL Change Enablement):** create request; select requester/owner; select affected org/dept/service/product/system/app/infra/cloud/DB/data/site/supplier/project/control/CI; classify (authorisation model + domain + scope); flag security/privacy/regulatory/supplier/continuity relevance; business justification + success criteria; assess impact/urgency/likelihood/complexity/risk; identify affected users/dependencies; **detect schedule conflicts / freeze / blackout**; build implementation/test/validation/communication/rollback plans; upload technical evidence; request technical/security/privacy/BCM/supplier/architecture/business reviews; select reviewers/approvers by classification+risk; refer to CAB/ECAB; schedule meeting + agenda + pre-read; confirm quorum; record attendance/comments/COI/votes/conditions/decisions/minutes; create + track meeting actions; approve/approve-with-conditions/reject/defer/return/cancel/refer; schedule + publish to change calendar; notify stakeholders; start/pause/abort/resume/rollback; record implementation steps + evidence; validate technical+business success; record actual impact/outage; link incidents/problems/risks/CAPA; PIR; determine success/partial/failure/rollback; update standard-change model; close/reopen/archive; generate operational/risk/compliance/CAB/emergency/failed-change/KPI reports.

**Policy (target 34 tasks — ISO 9001/27001 document control):** create request; select document type + hierarchy level; assign owner + document controller; define scope/audience/applicability/authority/framework source; link laws/regs/standards/controls/risks/processes/services/depts/systems; select template; author in structured editor **or** upload controlled file; import existing docs; bilingual versions; metadata (classification/confidentiality/retention/review frequency); assign author + contributors; draft + collaborate; add/resolve comments; track + **compare** versions; request SME/legal/compliance/cyber/privacy/HR/BCM/business reviews; run parallel/sequential/conditional/hybrid review; return + resubmit; refer to policy committee; schedule committee meeting (agenda/pre-read/quorum/attendance/comments/decision/conditions/minutes/actions); final approval; publish controlled version; generate controlled PDF/Word; archive superseded; distribute to selected audience; require acknowledgement/attestation; track completion/reminders/escalation; link training/awareness; exception/waiver request + risk-assess + approve/reject; track exception expiry/renewal; trigger periodic + event-driven reviews; record no-change review or amendment; withdraw/retire/archive/supersede; generate reports + audit evidence packages.

**Coverage verdict:** Change implements ~15/33 meaningfully (missing: conflict/freeze/calendar, plan objects, reviewer selection, real committee/meeting, PIR, standard-model, most reports). Policy (canonical) implements ~18/34 (missing: structured editor, contributors/comments, version compare, real reviewer routing, committee/meeting, acknowledgement campaigns, training link, controlled PDF/Word generation). Full task-by-task matrix maintained in the remediation backlog (§31).

---

## 6. Ideal Change Workflows (target design)

**Objective:** authorise and execute changes with risk-proportionate control, full traceability, and CAB governance.
- **Trigger:** manual request or intake promotion.
- **Classification-driven routing:** `STANDARD` (pre-approved model → skip CAB → schedule → implement → record); `NORMAL` (assess → technical/security/business reviews → CAB → approve → schedule → implement → validate → PIR → close); `EMERGENCY` (emergency authority/ECAB → implement → validate → **mandatory retrospective review** → CAPA → close); `MAJOR` (adds board/exec gate + minimum lead time).
- **Gates:** impact+risk assessment computes a **risk tier** → drives required reviews, approval level, CAB/ECAB requirement, minimum lead time, evidence checklist, PIR requirement.
- **Conflict control:** freeze windows / blackout periods / change-calendar collision detection **before** scheduling.
- **CAB gate:** real committee object (below), quorum + voting tally drives the decision; approver ≠ requester.
- **Failure path:** IMPLEMENTING→ROLLED_BACK→(incident/problem/CAPA auto-link)→PIR→close.
- **RACI, evidence, SLA countdowns, reminders/escalation, audit, integrations, reports** attached to every transition.

## 7. Ideal Policy Workflows (target design)

**Objective:** produce controlled, acknowledged, framework-mapped documents with approval provenance.
- Draft → Contributor Review → SME Review → Compliance/Legal Review → Owner Review → **Committee Review (if required)** → Final Approval → Publication (controlled PDF/Word) → Distribution (audience-scoped) → Acknowledgement campaign → Periodic/event-driven Review → Amendment/Retirement.
- **Review modes:** sequential / parallel / conditional / hybrid; multiple approvers; committee decision with quorum; approve-with-conditions; return-for-changes; comment threads with resolution; **version compare** before approval.
- **Publication gate:** author ≠ approver; effective date required; supersede prior version; generate controlled output.
- **Acknowledgement:** audience by role/department/directory; campaign with completion tracking, reminders, escalation; link to training.
- **Exceptions:** request → **risk assessment (scored, not asserted)** → owner/compliance review (requester ≠ approver) → approve/reject → expiry reminder → renew/close.
- **Single canonical policy register** feeding framework coverage + board pack (resolves today's duplication).

---

## 8. Actor and RACI Maps (target)

**Change:** Requester (R create) · Change Owner (A) · Change Manager (A/C routing, CAB secretariat) · Technical/Security/Privacy/BCM/Supplier/Architecture Reviewers (C) · CAB Chair + Members (A approve, voting) · ECAB (A emergency) · Implementer (R implement) · Validator (C) · PIR Owner (R) · Audit Committee (I).
**Policy:** Requester/Author (R draft) · Document Controller (A control) · Owner (A) · Contributors (R) · SME/Legal/Compliance/Cyber/Privacy/HR/BCM Reviewers (C) · Policy Committee (A) · Approver/Executive (A publish) · Audience users (R acknowledge) · DPO (C on personal-data policies) · Audit Committee (I).
**Current gap:** neither module models distinct reviewer/approver actors with enforced separation — Change uses one `WORKFLOW/WRITE` role for everything; Policy captures an actor string but its SoD guard doesn't fire (§23).

---

## 9. Classification Models

**Change (configurable — must drive workflow):** Authorisation = {Standard, Normal, Emergency, Retrospective-emergency, Pre-authorized, Major/high-risk}; Domain = {Application, Infrastructure, Network, Cloud, Database, Data, Cybersecurity, IAM, Endpoint, Configuration, Service, Supplier, Facility, Business-process, Organizational, Regulatory, Policy, Privacy, BCM/DR, Architecture}; Scope = {Minor, Significant, Major, Enterprise-wide, Customer-facing, Critical-service, Regulatory-impacting}.
**Current:** `change_type` ∈ {STANDARD, NORMAL, EMERGENCY} only; `category` free-text; **classification does NOT change workflow** (single transition map; STANDARD not pre-approved — CM-L1). No scope dimension.
**Policy (hierarchy — each needs its own template/owner/reviewer/approver/cadence/audience/retention):** Charter, Policy, Standard, Procedure, Process, Guideline, Manual, Plan, Framework, Methodology, Work-instruction, Form, Template, Record.
**Current:** `category` free-text; `review_cadence` ∈ {AD_HOC, ANNUAL, BIENNIAL, TRIENNIAL} (CHECK-enforced ✔); no document-type hierarchy driving template/approver.

## 10. Authorization Matrices (target)

Authorisation must be **derived** from: business impact, service criticality, complexity, affected users, customer/security/privacy/regulatory/data/availability/financial impact, supplier dependency, rollback complexity, testing status, similar-change history, freeze conflict, collision, window, resources → outputs {risk tier, required reviews, approval level, CAB/ECAB requirement, minimum lead time, evidence checklist, PIR requirement}.
**Current Change:** none of this exists — no risk engine, no matrix; approval is a manual state flip by any `WORKFLOW/WRITE` holder. **Do not require CAB for every change; do not allow one generic approver** — today it's effectively one generic approver for all.
**Current Policy:** approval level is a fixed 1-stage (canonical) or 2-stage legal→exec (compliance) path, not risk-derived; exception approval not risk-derived (risk asserted).

## 11. CAB / ECAB and Committee Design (target)

A committee must be a **real workflow object**: master data, type, chair + secretary, permanent/optional members, SMEs/delegates, quorum, voting rule, COI declaration, meeting schedule + ad-hoc, agenda, pre-read distribution, attendance, comments, decisions, conditions, dissent, minutes, actions + owners + due dates + follow-up, evidence, approval records, digital-signature placeholder, audit trail. Decisions: Approved / Approved-with-conditions / Rejected / Deferred / Returned-for-info / Referred / Cancelled. Emergency → ECAB or configurable emergency authority + **mandatory retrospective review**.
**Current:** **No committee object exists in either module.** Change "CAB" is two status values (`CAB_PENDING`/`CAB_APPROVED`) + a free-text `change_approvals` log with no quorum/tally/COI/minutes/actions. Policy has **no committee referral at all**. The platform *does* have a generic committee module and a workflow-request engine (used by the policy publish-approval drawer) — the remediation should adopt these rather than build new.

## 12. Meeting Lifecycle Design (target)

Schedule → agenda + pre-read distribution → attendance + quorum check → per-item discussion/comments/COI → vote tally → decision + conditions + dissent → minutes → actions (owner/due) → follow-up to closure → evidence + audit. **Current: absent in both modules** (no agenda/quorum/attendance/voting/minutes/actions). Reuse the committee module.

---

## 13. Current-vs-Ideal Gap Analysis (classification)

| Capability | Change | Policy (canonical) | Policy (compliance) |
|---|---|---|---|
| Guarded state machine | ✔ (but bypassable via PUT) | ✔ (bypassable via create) | ✔ (bypassable via create) |
| Classification drives workflow | ✗ Missing | ✗ Missing | ✗ Missing |
| Risk/authorisation matrix | ✗ Missing | ✗ Missing | ✗ Missing |
| Real reviewer/approver routing | ✗ Missing | Partial (engine drawer only, on façade page) | Partial (2-stage roles, no identity) |
| Segregation of duties | ✗ Missing | Broken (façade guard) | ✗ Missing |
| Committee / meeting | ✗ Missing | ✗ Missing | ✗ Missing |
| Conflict/freeze/calendar | ✗ Missing | n/a | n/a |
| Version control + compare | n/a | Partial (rows yes, compare no; string-max bug) | ✗ Façade (in-place overwrite) |
| Structured editor / real document | n/a | ✗ (41/51 empty; no editor) | Inline text only |
| File upload + hash | Partial (attachments, no plan objects) | ✔ (AttachmentsField + SHA-256) | ✗ None |
| Acknowledgement campaign | n/a | ✗ (per-user only) | ✗ |
| Reminders / SLA / escalation | ✗ (task dueAt dead) | Partial (scheduler review-due; no attest reminders in-module) | ✗ |
| CHECK constraints / validation | ✗ None / no @Valid | ✔ CHECK / ✗ no @Valid | ✗ None / no @Valid |
| Audit coverage | Partial (2 ops) | Partial (creates/transitions; updates/deletes unaudited) | ✗ None |
| Tenant isolation | ✔ | ✔ | Partial (mapping table lacks org col) |
| Demo/mock masking | ✗ (fabricates for authed users) | ✔ none (canonical) | ✗ detail page mock, no badge |
| Correct export | ✗ (exports Risk register) | Partial (evidence pack real; no controlled PDF/Word) | n/a |
| F8 automation wired (dev DB) | ✗ (0 consumers) | Partial (POLICY_PUBLISHED only) | shares |

---

## 14. Critical Findings

### Change Management
- **CM-C1 — No segregation of duties; `WORKFLOW/WRITE` on 54 roles incl. `VIEWER`.** *Live:* `viewer` `POST /api/v1/changes` → **201**. One actor can author → CAB-approve → close. → CH-1.
- **CM-C2 — CAB approval decorative** (no quorum, no SoD, votes don't gate, `approver_id` NULL). *Live:* CAB_APPROVED with 0 votes → 200; despite REJECT → 200; self-approve → 201. → CH-1.
- **CM-C3 — `PUT` bypasses the state machine.** *Live:* `PUT {status:"CLOSED"}` on DRAFT → 200/CLOSED (guarded `transition` → 400). Skips guard, side-effects, F8, audit. → CH-2.

### Policy Management
- **PM-C1 — Duplicated policy domain, unintegrated.** `policy.documents` (canonical) vs `compliance.policies` (legacy, "write lifecycle retired in C-4") vs `/policy-templates` (localStorage). Bean collision was renamed away (`PolicyManagementService`) but the conceptual duplication remains; each links frameworks/controls separately, so a policy in one stack is invisible to the other's framework-coverage/board-pack. → CH-6 (MERGE).
- **PM-C2 — Create-time lifecycle bypass.** Both stacks let a WRITE caller `POST` with `status:"PUBLISHED"`/`APPROVED`, skipping the state machine, SoD and publish guards (which live only in `transition`); `compliance` create does **no** status validation at all. `PolicyManagementService:277-280`, `PolicyService.create:48-58`. → CH-7.
- **PM-C3 — SoD is façade-grade / absent.** Canonical guard blocks approver == createdBy/ownerName/ownerEmail, but **`createdBy` is never populated** and `actor` (username) is compared to owner **display-name/email** → the guard effectively never fires (`PolicyManagementService:354-360`). Compliance stack has **no SoD** and never captures `authorId`/`legalReviewerId`/`approverId` (unwritten façade columns). → CH-1/CH-7.
- **PM-C4 — `/policies/:id` detail page is a façade.** Submit/legal/exec/publish/mark-review/reaffirm/retire buttons are **local status flips with no API** (`PolicyDetailPage.tsx:61-65,98-106`); silent fallback to mock `POLICIES` with **no demo badge** (`:46-55`); versions/lifecycle/anchors tabs are hard-coded mock. Only publish-approval routing, attachments, and sign-off are real. → CH-9.

---

## 15. Forms and Screens Findings

- **Change:** forms are real modals, **but** approver name, task title, link entityType/id, and comment author are collected via **`window.prompt`** (`ChangesPage.tsx` castVote/addTask/addLink), and comment author is hardcoded `'You'`. The in-page create modal's create-branch is **dead** (the "New" button routes to `/intake/change_request`); Edit uses the bypassable `PUT`. → CH-2/CH-3/CH-8.
- **Policy (canonical):** forms are **real themed modals/drawers** (`lib/dialog.tsx`, not browser pop-ups) — create/exception/attestation assign are proper `FormModal`s; good. **Gaps:** no structured content editor anywhere (summary textareas only); policy `bodyUrl` has **no UI** to set it. → CH-8/CH-10.
- **Policy (detail/templates):** `/policies/:id` façade (PM-C4); `/policy-templates` is localStorage-only with a lingering native **`confirm()`** (`PolicyTemplatesPage.tsx:63`). → CH-9.

## 16. Upload and Evidence Findings

- **Policy (canonical) — strong.** `AttachmentsField` = drag-drop, multi-file, extension allow-list + 50 MB cap, staging (doc-type/title/expiry/classification), versioning/supersede, real `AttachmentService` with **SHA-256** (`AttachmentService.java:71`); evidence-package endpoint returns policy + hashed attachments + control links. **But** upload is **not exposed on `PolicyController`** (goes via the separate attachments controller), and **41/51 policies have no `body_url` and no attachment** → most "policies" are metadata shells (PM-H1). → CH-10.
- **Change — weak.** No first-class implementation/test/rollback-plan or CAB-pack/PIR-report objects; evidence only via generic attachments (`EntityDocsModal`). → CH-11.

## 17. Routing Findings

- **Change:** no reviewer/approver routing at all; "advance" is a single-actor state flip; Kanban lets one click move `CAB_PENDING`→`CAB_APPROVED`. → CH-1.
- **Policy:** real SEQUENTIAL/PARALLEL routing + actor selector + SLA/reminder/escalation exists **only** in the engine `GRCRequestDrawer`, exposed **only** via the "Route publish approval" button on the **façade** detail page (`ApprovalRequestForms.tsx:23-35`). The canonical page has **no reviewer/approver picker** — review/approve are bare enum transitions. **Adopt the engine drawer on the canonical page.** → CH-7.

## 18. Lifecycle Findings

- **Change:** 14-state machine real; **bypassable via PUT** (CM-C3); `STANDARD` has no pre-approved fast-path (CM-L1); invalid statuses persist (no CHECK — CM-H1) and then vanish from Kanban/analytics.
- **Policy:** canonical 6-state machine real + CHECK-enforced; **bypassable at create** (PM-C2); `newVersion` is a bare bump with a **string-`max()` latest-version bug** (`PolicyDocumentRepository:27-28` → "10.0" < "9.0"); no supersede flag/compare. Compliance stack overwrites version **in place** (destroys history). → CH-7/CH-12.

## 19. Notifications, SLA, and Escalation Findings

- **F8 automation emitted in code but dormant in dev:** `CHANGE_*` triggers have **0 consumers** in the dev DB; Policy has **only** `POLICY_PUBLISHED` (AR-15). V146 (AR-96..100) and V141 (AR-71..75) are **not present in dev** (dev carries a different 175-rule seed). Verify prod seeding + reconcile the two numbering schemes. → CH-13.
- **Change:** `change_tasks.due_at` exists but **no scheduler** and the FE never sets it → task SLA is cosmetic; "Overdue" KPI derives from the change's `scheduledStart`, not tasks. → CH-5.
- **Policy:** `PolicyComplianceScheduler` (daily, multi-tenant, clean TenantContext) runs expiry + review-due sweeps — **real**. But **attestation reminders/escalation are not implemented in-module** (delegated to F8, which lacks consumers in dev), and there is **no acknowledgement campaign** (per-user assignment only; `userRole`/`userDepartment` never populated). → CH-4.

## 20. Integration Findings

- **Change links (real):** 12 entity types × 7 link types + inverse `by-entity` panel (used by other modules). **But** `linksForEntity` query is org-unscoped (post-filtered — CM-L3), and there is **no auto-link of incidents/problems/CAPA on ROLLED_BACK/FAILED** (the F8 rules that would do it are absent in dev). → CH-13/CH-14.
- **Policy links:** canonical `PolicyDocumentControlLink`→`compliance.controls` is real, tenant-safe, audited; `PolicyDocument.frameworkId` is a **dead façade column** (never read/written — PM-M5). Compliance `PolicyFrameworkMapping`→`compliance.frameworks` is real **but lacks an `organisation_id` column**, and `coverage()`/`BoardPackService` count `findByFrameworkId` **with no org filter → cross-tenant count leakage** (PM-M4, elevate to High). The two stacks' framework/control linkage do not join. → CH-6/CH-15.

---

## 21. Data Model Gaps

- **Change:** no `ChangeType/Model`, `RiskAssessment`, `ImpactAssessment`, `AffectedObject`, `Implementation/Test/Rollback Plan`, `Schedule/CalendarEntry`, `Review`, `MeetingReferral`, `CABCommittee/Meeting/AgendaItem/Attendance/Decision/Action`, `ImplementationLog`, `Validation`, `PIR`. `change_approvals` has no `approver_id` population, no quorum fields. **No CHECK constraints** on any `change_mgmt` table. `change_tasks` has no `is_deleted`.
- **Policy:** no `PolicyType/Template` (DB), `Contributor`, `Review` (multi-actor), `CommitteeReferral`, `Distribution/Audience`, `Attestation campaign`, real `Version` snapshot. Canonical uses free-text `owner_name/approver_name` (no FK identity); compliance has FK identity columns that are **never written**. `policy_framework_mappings` **lacks `organisation_id`**. `PolicyDocument.frameworkId` façade.

## 22. API Gaps

- No `@Valid` and **raw JPA entities as request bodies** on every write in both modules (mass-assignment surface; ids/org are nulled/stamped server-side, but all other columns bind from client).
- Change: no APPROVE-scoped endpoint; transition not quorum-checked; `PUT` sets status.
- Policy: **write-behind-READ** — `POST /attestations` (assign), `/attest`, `/decline` guarded by COMPLIANCE **READ** not WRITE; `createAttestation` has no object check → any READ user can assign attestations to arbitrary usernames. Create sets status arbitrarily (PM-C2).

## 23. RBAC and Segregation-of-Duties Gaps

- **Change:** `WORKFLOW/WRITE` granted to **54 roles** incl. `VIEWER`, `REGULATOR_VIEWER`, `EXTERNAL_AUDITOR`, `AUDITOR`, `SUPPLIER_USER` (live-proven). No APPROVE separation. **Critical.**
- **Policy:** `COMPLIANCE/WRITE` is far better-scoped (**13 governance roles**, not `viewer`) — a genuine strength. **But** there is **no dedicated `POLICY` permission module**, so "author policy," "approve policy," and "manage compliance controls" all share `COMPLIANCE/WRITE`; the canonical SoD guard is façade-grade (PM-C3) and the compliance stack has none. Attestation create/attest/decline sit on READ (PM-M1).

## 24. Audit Trail Gaps

- **Change:** only `create` and `transition` audited. `update` (incl. silent status change), `delete`, `recordApproval`, all task/link/comment ops — **unaudited** (live-proven the `PUT` status jump leaves no audit row).
- **Policy (canonical):** creates + transitions + sweeps + attest/decline audited; **`update`, `newVersion`, `delete`, `updateException`, `deleteException` unaudited.**
- **Policy (compliance):** **entirely unaudited** — `PolicyService` imports no `AuditLogService`; create/update/delete + all 6 legal/exec transitions leave no trail. For an approval-provenance workflow this is severe.

## 25. Report and Export Gaps

- **Change:** the Changes "Export" button downloads the **Risk register** (`reportingApi.riskRegister*`) — wrong data; **no change-register PDF/DOCX/XLSX** exists (changes appear only inside the board pack). Analytics `successRatePct` computed only from records that carry an `outcome`. → CH-4/CH-16.
- **Policy:** evidence-package (JSON + hashed attachments) is real; **no controlled PDF/Word generation** of the policy itself; no acknowledgement-coverage report beyond a pending count; framework coverage counts risk cross-tenant leakage (PM-M4).

## 26. Button Test Results (representative)

**Change (live-tested):** Create ✔; Submit/Assess/CAB transitions ✔ (guarded); **Advance CAB_PENDING→CAB_APPROVED with no votes → PASS-but-wrong (200)**; Approve/Reject vote ✔ recorded but **non-binding**; Edit (PUT) **status-bypass (200)**; Delete change ✔ (soft, unaudited); Add task ✔ then **hard-delete (204)**; Add comment ✔ (author 'Unknown'/'You'); **Export → downloads Risk register (wrong)**; viewer Create → **201 (should be 403)**.
**Policy (code-verified; live re-run pending auth):** Create/transition/new-version/delete/attach/export-evidence/controls/exception CRUD+transition/attestation assign+attest+decline — **all real** on canonical page. Create with `status:PUBLISHED` → **expected to bypass** (PM-C2). PolicyDetailPage submit/publish/retire → **local flip, no API** (PM-C4). Template delete → **native `confirm()`**.

## 27. E2E Test Results (against the command's mandatory scenarios)

- **Normal Change:** partial — no real technical/security review actors, CAB is non-binding, no PIR. 
- **Emergency Change:** partial — `EMERGENCY_APPROVED` path exists (type-checked) but **no ECAB and no mandatory retrospective review**.
- **Failed Change:** partial — ROLLED_BACK sets outcome, but **no auto incident/problem/CAPA link** (F8 rule absent in dev) and no PIR.
- **Standard Change:** **not implemented as pre-approved** — routes through CAB states like Normal (CM-L1).
- **Policy Creation:** partial — draft/upload + single review + approve + publish works on canonical page; **no contributor/committee, no controlled-output generation**.
- **Policy Rejection:** works on compliance stack (reject with reason → resubmit) but that stack is unaudited and identity-less.
- **Policy Exception:** **works well** (request → under-review → approve/reject with reason → expiry sweep) — best-built E2E in either module; risk is asserted not assessed.
- **Policy Review (periodic):** **works** — scheduler emits `POLICY_REVIEW_DUE` in a 30-day window, deduped.

---

## 28. Decision Register (KEEP / IMPROVE / ADD / MERGE / REPLACE / REMOVE / GLOBALIZE / CONFIGURE / DEFER)

| # | Capability | Decision | Rationale (business · control · arch) |
|---|---|---|---|
| D1 | Change 14-state machine | **KEEP + IMPROVE** | Sound; close the PUT bypass + make classification-aware |
| D2 | Change `WORKFLOW` gate | **REPLACE** | Introduce `CHANGE` module perms + CAB-approver role; strip write from read-only personas |
| D3 | Change CAB (status-only) | **REPLACE** | Adopt the platform committee/meeting object; quorum-driven, SoD-enforced |
| D4 | Change task `due_at` | **IMPROVE** | Wire scheduler + FE capture; drive overdue KPI from tasks |
| D5 | Change Export (risk register) | **REPLACE** | Real change-register exporter (RTL-correct) |
| D6 | Change demo fallback | **IMPROVE** | Scope to unauthenticated; no fabricated analytics |
| D7 | Two policy backends | **MERGE** | One canonical register (`policy.documents`) absorbing compliance's ID-identity + inline content + legal/exec stages; compliance stack → read-only archive/redirect |
| D8 | `/policies/:id` façade page | **REPLACE/MERGE** | Point detail at the canonical policy; remove local-flip buttons + mock tabs |
| D9 | `/policy-templates` (localStorage) | **IMPROVE/CONFIGURE** | Back with a real template entity + type hierarchy |
| D10 | Policy SoD guard | **REPLACE** | Populate `createdBy`; compare like-for-like identifiers; add exception SoD |
| D11 | Policy attestation (per-user) | **ADD** | Audience selection + campaign + reminders + escalation (reuse compliance attestation-campaign machinery) |
| D12 | Policy version compare | **ADD** | Diff endpoint + UI; fix string-`max()` latest resolution |
| D13 | Structured policy editor | **ADD** | Controlled content editor + controlled PDF/Word generation |
| D14 | Committee/meeting object | **GLOBALIZE** | One committee engine serving CAB, policy committee, and others |
| D15 | `@Valid` + DTOs + CHECK | **ADD** | Both modules; enum + Arabic-mandatory validation |
| D16 | Audit coverage | **ADD** | Every mutating op in both modules |
| D17 | `policy_framework_mappings` org column | **ADD** | First-class tenant scoping; fix coverage/board-pack counts |
| D18 | `PolicyDocument.frameworkId` | **REMOVE or IMPLEMENT** | Dead façade column |
| D19 | F8 automation seeding (V141/V146) | **CONFIGURE** | Seed/verify rules in every environment; reconcile numbering |
| D20 | Change conflict/freeze/calendar | **ADD** | Collision detection before scheduling |
| D21 | Risk/authorisation matrix | **ADD** | Both modules; risk-tier drives routing/approval |

---

## 29. Immediate Fixes (low-effort, high-value)

1. **CM-C3 / PM-C2:** stop copying `status` from request bodies in `update`/`create`; force status changes through the guarded `transition` (both modules). 
2. **CM-C1:** remove `WORKFLOW/WRITE` from `VIEWER`, `REGULATOR_VIEWER`, `EXTERNAL_AUDITOR`, `AUDITOR`, `SUPPLIER_USER`. 
3. **PM-M1:** move attestation create/attest/decline from COMPLIANCE READ → WRITE; add object check on create. 
4. **CM-H3:** repoint the Changes Export menu away from the risk-register endpoints (hide until CH-16 lands). 
5. **PM-C3:** populate `createdBy` on policy documents so the SoD guard can fire. 
6. **CM-H1:** add DB CHECK constraints on `change_mgmt` status/type/risk/outcome (migration V300). 
7. **PM-C4:** hide the `/policies/:id` local-flip workflow buttons (they mislead) until CH-9. 
8. **PM-M4:** add an org filter to `coverage()`/board-pack mapping counts.

## 30. Structural Fixes

Segregation & CAB governance (CH-1); lifecycle integrity + validation (CH-2/CH-7); audit completeness + soft-delete (CH-3); real change/policy reporting & exports (CH-4/CH-16); task/attestation SLA + campaigns (CH-4/CH-5); **policy backend merge** (CH-6); real reviewer/committee routing via the engine + committee module (CH-7/CH-14); structured editor + controlled output (CH-10); tenant-scope the mapping table (CH-15); seed/verify F8 (CH-13); risk/authorisation matrix + classification-driven routing (CH-17).

## 31. Detailed Remediation Backlog (epics)

- **CH-1 — Change governance:** `CHANGE` permission module + CAB-approver role; approver ≠ requester (409); capture approver identity from principal; quorum-driven `CAB_APPROVED`/rejection-blocks; ECAB + mandatory retrospective review.
- **CH-2 — Change lifecycle integrity:** remove status from `update`/`apply`; `@Valid` DTOs; enum + Arabic-mandatory validation; DB CHECK (V300); type-aware paths incl. STANDARD pre-approved.
- **CH-3 — Change audit + soft-delete tasks:** audit every mutation; add `is_deleted` to `change_tasks`; capture comment/approver identity.
- **CH-4 — Reporting/exports:** change-register PDF/DOCX/XLSX (RTL) + repoint FE; policy controlled PDF/Word; acknowledgement-coverage report.
- **CH-5 — Change task SLA:** capture `due_at`/owner in FE; scheduler reminders/escalation; overdue KPI from tasks.
- **CH-6 — Policy backend MERGE:** one canonical register; migrate compliance policy identity/content/legal-exec stages into `policy.documents`; compliance stack → read-only/redirect; unify framework+control linkage.
- **CH-7 — Policy governance:** real reviewer/approver routing on the canonical page via the engine drawer (sequential/parallel/committee); fix SoD (populate `createdBy`, like-for-like compare); exception SoD + **scored** risk; close create-status bypass.
- **CH-8 — Real forms:** replace Change `window.prompt` flows (approver/task/link/comment) and the `/policy-templates` native `confirm()` with real modals.
- **CH-9 — Fix `/policies/:id`:** point at canonical policy; remove local-flip buttons + mock tabs; keep real attachments/sign-off/routing.
- **CH-10 — Structured policy content:** controlled editor + controlled-output generation; require a document (body_url or attachment) before PUBLISHED.
- **CH-11 — Change plan objects:** implementation/test/rollback plan + CAB-pack + PIR objects.
- **CH-12 — Version control:** fix latest-version resolution (numeric); supersede flag; diff/compare endpoint + UI.
- **CH-13 — F8 seeding:** apply/verify V141+V146 (and reconcile with the dev 175-rule seed) in all environments; add ROLLED_BACK→incident/problem/CAPA links.
- **CH-14 — Committee/meeting engine (GLOBALIZE):** one committee object (agenda/quorum/attendance/vote/minutes/actions/COI/e-sign) for CAB, ECAB and policy committee.
- **CH-15 — Tenant hardening:** add `organisation_id` to `policy_framework_mappings`; org-scope its repo queries + coverage/board-pack; org-scope `linksForEntity`.
- **CH-16 — Change register export** (folded into CH-4).
- **CH-17 — Risk/authorisation matrix + classification model** (both modules); conflict/freeze/change-calendar.

## 32. Acceptance Criteria (samples)

- CH-1: self-approval → 409; CAB_APPROVED with 0/any-REJECT votes → 409; `viewer`/`external_auditor` POST or approve → 403; `approver_id` populated; two-user happy path passes.
- CH-2/CH-7: `PUT`/`POST` cannot set an out-of-lifecycle status (ignored or 400); invalid enum → 400; missing `title_ar`/`justification_ar` → 400; STANDARD skips CAB.
- CH-3/CH-16(audit): every mutating call in both modules writes an audit row; deleted tasks recoverable.
- CH-4: Change export returns change data (RTL-correct); policy publish generates a controlled PDF/Word.
- CH-6: one policy register feeds framework coverage + board pack; no duplicate write path.
- CH-11(attestation): launch a campaign to a role/department; completion tracked; reminder + escalation fire.
- CH-15: framework-coverage counts are per-tenant; cross-tenant probe returns only own-tenant rows.

## 33. Implementation Sequence

**Phase 0 (Immediate, §29)** → **Phase 1: CH-1, CH-2, CH-7, CH-3** (governance + integrity + audit — release-blockers) → **Phase 2: CH-6 (policy merge), CH-9, CH-15** (de-duplicate + de-façade + tenant) → **Phase 3: CH-4, CH-5, CH-11, CH-12, CH-10** (reporting, SLA, campaigns, versioning, editor) → **Phase 4: CH-14 committee engine, CH-17 risk matrix + classification, CH-11 plan objects, CH-13 automation, CH-8 forms**. Migrations start at **V300** (V1→V299 packaged; CLAUDE.md's "V296" is stale). Branch `feat/change-policy-remediation`; commit per verified slice; **do not merge without explicit "merge."**

## 34. Risks if Not Fixed

- **Audit/regulatory failure:** non-binding CAB + no SoD + no committee record → NCA ECC / SAMA CSF change-authorisation and segregation findings; unaudited policy approvals → ISO 9001/27001 document-control nonconformities.
- **Integrity/repudiation:** status bypass + missing audit → unprovable change/policy history; approvals with no captured identity are repudiable.
- **Data trust:** Change fabricates demo data for authenticated users; wrong export; duplicated policy registers → conflicting "sources of truth."
- **Tenant exposure:** framework-coverage count leakage across tenants.
- **Operational:** dormant automation → no CAB/incident/attestation escalations actually fire.

## 35. Market-Readiness Rating

- **Change Management: Partially usable** (strong skeleton, hollow governance core; 3 Critical release-blockers).
- **Policy Management: Partially usable** (canonical register better-founded than Change on data integrity, uploads, exceptions, scheduler; crippled by duplication, create-bypass, façade SoD, façade detail page, missing editor/compare/campaigns). The compliance policy stack alone is **Demo-only/legacy**.
- **Neither acceptable for production** in a regulated deployment until Phase-1 (and PM merge) lands.

## 36. Next Implementation Command

> Implement Phase 0 + Phase 1 of `CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (CH-1, CH-2, CH-3, CH-7 + immediate fixes) on branch `feat/change-policy-remediation`, migrations from V300, live-probed with two users (maker + distinct approver) and a read-only persona; do not merge without explicit approval.

---

## Final Report (command §20)

- **Pages reviewed:** Change (6 tabs, 1 page + mock) · Policy (3 surfaces: `/policy-management` 4 tabs, `/policies/:id` 8 tabs, `/policy-templates`).
- **Forms reviewed:** ~20 (Change create/edit + prompt-flows; Policy create/exception/attestation modals, attachments, templates).
- **Buttons tested:** Change ~30 (live); Policy ~30 (code-verified, live re-run pending auth).
- **Workflows reviewed:** Change 14-state + 3 type paths; Policy 6-state (canonical) + 6-state (compliance) + exception 6-state + attestation.
- **Committee/meeting flows:** **none exist** in either module (gap).
- **APIs reviewed:** Change 18; Policy 34 (22 `/api/policy` + 12 `/api/v1/compliance/policies`).
- **Gaps by severity:** **Critical 7** (CM-C1/C2/C3, PM-C1/C2/C3/C4) · **High 7** (CM-H1/H2/H3, PM-H1/H2/H3/H4 incl. tenant-count leak PM-M4 elevated) · **Medium ~8** · **Low ~5**.
- **Proposed additions:** committee/meeting engine, risk/authorisation matrix, classification-driven routing, structured editor + controlled output, attestation campaigns, version compare, change plan/PIR objects, change-register export.
- **Proposed removals/merges:** MERGE the two policy backends; REPLACE the status-only CAB and the `/policies/:id` façade; REMOVE the dead `frameworkId` column; REPLACE the `WORKFLOW` gate for change.
- **Path to MD file:** `D:\AYMAN\GRC\CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (this file). Supersedes the Change-only `CHANGE_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md`.
- **Recommended implementation order:** §33 (Phase 0 → 4).
- **Status:** review-only; **no product code changed**; awaiting a separate implementation command.

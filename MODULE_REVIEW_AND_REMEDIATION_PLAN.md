# Module Review and Remediation Plan

> **Modules reviewed:** Compliance (`module/compliance`) + Policy (`module/policy` and the legacy `module/compliance` policy)
> **Date:** 2026-06-27
> **Reviewer role:** Senior GRC product architect / Saudi-market GRC specialist / internal auditor / QA
> **Repository:** `D:\AYMAN\GRC` — Spring Boot 3.5 (Java 17) backend + React/Vite (TS) frontend, SQL Server, tenant `organisation_id = 1`
> **Method:** static code read (file:line), DB schema/constraint inspection, and **live API smoke tests** against the running backend (`http://localhost:8084`, logged in as `admin`). A 10-lens adversarial review workflow was launched but cut off by a session limit; this report is the author's own line-by-line verification (which independently confirmed the crux), so findings below are evidence-backed, not agent-generated.

---

## 0. Remediation Progress (live — branch `feat/compliance-policy-remediation`)

> Implementation done 2026-06-28 via an autonomous `/loop`, in §22 order. Frontend changes verified with `tsc --noEmit`; backend changes `mvn compile`/`mvn test`-verified + live API smoke tests. **Not merged to `master`** — 19 commits on `feat/compliance-policy-remediation`, **pushed to origin (PR pending), backend rebuilt + restarted** so the schedulers / SoD / RBAC / evidence-package endpoints are active (this also fixed the stale-jar policy-transition 404).

| Gap | Fix | Status | Commit |
|---|---|---|---|
| C-9 | Policy exception creation reachable from the UI | ✅ Shipped | `fd46475e` |
| C-10 | Localized transition labels + policy KPI drill-down | ✅ Shipped | `fd46475e` / `956c7cc6` |
| C-5 | Obligation "New" button enabled (real create form) | ✅ Shipped | `0920ed12` |
| C-8 | 16 `window.prompt`/`confirm` → themed dialog service | ✅ Shipped | `9502f32b` |
| #7 perms | COMPLIANCE RBAC grants + missing `COMPLIANCE_DELETE` (Flyway V231, applied to dev) | ✅ Shipped | `f5727d06` |
| C-7 | "Demo data" badge aggregates all registers (no silent mock) | ✅ Shipped | `956c7cc6` |
| **C-1** | **Real document/file upload + versioning on policies** | ✅ Shipped | `b3415151` |
| **C-2** | **Evidence attachments on policies / obligations / attestations / findings / controls** | ✅ Shipped | `dd9dbc8b` / `08588ad0` |
| **C-2** | **"Request evidence from a department" → real EvidenceRequest + SLA chase** | ✅ Shipped | `b0076392` |
| **C-3** | **Scheduler suite — policy expiry/review + obligation-overdue + CAPA-due reminders** | ✅ Shipped | `4acf1bf1` / `923d8887` |
| **C-6** | **Policy approver segregation-of-duty (+ unit tests)** | ✅ Shipped | `90bbe478` / `8f925ec1` |
| C-4 | Consolidate the two policy systems into one register | ⏳ Backend — larger | — |
| **C-11** | **Audit evidence-package builder — policy (doc + attachments + controls/framework) AND obligation (obligation + attachments + per-attestation evidence); one-call JSON manifest + UI "Evidence pack" export. Both live-verified end-to-end.** | ✅ Shipped | `b553bbfe` / `93b2d8d3` / `4984b4ac` |
| C-12 | Replace free-text cross-links with FK links | ⏳ Pending | — |

**Net:** every Critical finding (C-1, C-2, C-3, C-5, C-6, C-7, C-8, C-9, C-10, **C-11**) plus #7-RBAC is resolved, verified, pushed, and — for the backend — activated by a rebuild + restart. The policy *and* obligation evidence-package endpoints are live-verified end-to-end (upload → one-call manifest with sha256 + download links → UI export). Remaining are the two large structural items only: **C-4** (merge the two policy systems — needs a data-migration decision; deliberately NOT done autonomously) and **C-12** (replace remaining free-text cross-links with FK links).

---

## 1. Executive Summary

**This is not a fake/demo module.** The Compliance and Policy modules have **real backends**: persisted JPA entities, enforced status state-machines, audit-log writes on every transition, domain-event emission for an automation engine, object-level authorization, and bilingual (AR/EN) UIs with empty/loading/error states. Live tests confirmed real persistence (created policy id 19, DRAFT, persisted) and seeded content (18 policies, 34 obligations, controls, evidence requests with real due dates + department assignment).

**But it is not market-ready for a Saudi GRC buyer**, for a small number of *specific, high-impact* reasons that exactly match the concerns raised:

1. **No real file upload anywhere in Compliance or Policy.** Policy "documents" are a `body_url` text field (`PolicyDocument.java:36`); the create form does not even capture it. Policy/compliance attestation evidence is a free-text string. A grep for `multipart|FormData|type=file|upload` across both modules' pages returns **zero matches**. A policy/evidence system with no document is not usable for audit.
2. **A strong evidence subsystem exists but Compliance/Policy are not wired to it.** `module/evidence` has `EvidenceRequest` (with `departmentId`, `dueDate`, `priority`, `lastRemindedAt`), a working daily SLA reminder scheduler, a daily expiry scheduler, reviews, versions, storage, and a generic `EvidenceLink`. Compliance and Policy reference evidence by **free-text string** (`evidenceRef`, `evidenceRefs`) instead of creating real `EvidenceRequest`/`EvidenceItem`/`EvidenceLink` rows. The plumbing is built; the modules just don't call it.
3. **Compliance/Policy deadlines do not auto-fire reminders or escalations.** Real schedulers exist for evidence and approval-chains — but **none** exists for policy review dates (`nextReviewDate` is stored and never read), policy/exception expiry (manual "Run expiry sweep" button only), obligation overdue (`markOverdueForCampaign` has no scheduled caller), or CAPA finding due dates (`AuditFinding.dueDate` stored, never enforced). The finding "escalation tier" is a **display computation**, not a delivered notification.
4. **Policy is duplicated into two divergent systems** — the legacy `compliance.policies` (lifecycle DRAFT→LEGAL_REVIEW→EXEC_APPROVAL→PUBLISHED→REVIEW_DUE) surfaced in the Compliance page "Policies" tab, and the newer `policy.documents` (DRAFT→REVIEW→APPROVED→PUBLISHED→EXPIRED→ARCHIVED + exceptions + attestations + versioning) at `/policy-management`. Two registers, two lifecycles, two backends, neither with file upload. This is confusing and not how a single GRC team works.
5. **Several user-facing actions are crude or dead.** Obligation "New obligation" button is `disabled` (`ObligationsPage.tsx:95`) — you cannot create an obligation from the UI. Compliance workflow actions use browser `window.prompt`/`window.confirm` (no validation, no Arabic, no evidence attach). KPI cards "drill down" to the same page (`to="/policy-management"`). The Compliance dashboard falls back to **mock data** when the API is empty/down (`useApiList(..., COMPLIANCE_CONTROLS)`), which can mask a disconnected backend.

**Bottom line:** the skeleton and most state-machines are genuinely real and auditable; the **connective tissue a Saudi auditor needs — uploaded evidence, evidence-request-to-department with SLA, automated review/expiry reminders, single policy register, approver-gated sign-off** — is incomplete. Verdict: **Partially usable — needs major remediation** before production/market use.

---

## 2. Module Reviewed

| Item | Detail |
|---|---|
| Compliance backend | `module/compliance` — controllers under `/api/v1/compliance` (`ControlController`, `RegChangePolicyController`, `AuditEngagementController`, `RegulatoryHorizonController`, `FrameworkAnchorController`) + `obligation/` sub-package (`/obligations`, `/obligation-campaigns`, `/obligation-attestations`). Services: `ComplianceService`, `CapaWorkflowService`, `AuditEngagementService`, `RegulatoryChangeService`, `RegulatoryHorizonService`, `FrameworkAnchorService`, legacy `PolicyService`, + obligation services. |
| Policy backend | `module/policy` — `PolicyController` at `/api/policy` (`documents`, `exceptions`, `attestations`) + `PolicyManagementService`. Entities `PolicyDocument`, `PolicyException`, `PolicyAttestation`. |
| Compliance frontend | `pages/compliance/` — `CompliancePage.tsx` (12 tabs), `PosturePage.tsx`, `ObligationsPage.tsx`, `ObligationCampaignsPage.tsx`, `ObligationDetailPage.tsx`, `RegulatoryHorizonPage.tsx`; API clients `lib/api.ts` (complianceApi), `lib/obligations.ts`. |
| Policy frontend | `pages/policy/PolicyManagementPage.tsx` (4 tabs) + `lib/policy.ts`. |
| Evidence (integration target) | `module/evidence` — `EvidenceRequest/Review/Item/Version/Link`, `EvidenceRequestService`, `EvidenceRequestSlaScheduler`, `EvidenceExpiryScheduler`, `EvidenceStorageService`. |

---

## 3. Full Real-World Task List

The operational lifecycle a Saudi compliance + policy function must support end-to-end. (Status column is the current-state verdict; detail in §5–§9.)

| ID | Task | Role | Trigger | Required evidence/approval | Status transitions | SLA / reminders / escalation | Current status |
|---|---|---|---|---|---|---|---|
| T-01 | Create compliance control | Compliance officer | New framework adoption | — | PLANNED→PARTIAL→IMPLEMENTED | review cadence | **Works** (CRUD + persistence) |
| T-02 | Assess control / run compliance assessment | Auditor | Assessment cycle | evidence per control | DRAFT→IN_PROGRESS→COMPLETED | period due | **Partial** (assessment entity exists; weak assess→evidence flow) |
| T-03 | Register regulatory change | Compliance officer | Regulator publishes | citation, impact | NEW→APPLICABILITY_ASSESSED→IMPACT_ASSESSED→CONTROLS_UPDATED→CLOSED | effective date | **Works** (workflow real; crude prompts) |
| T-04 | Create regulatory obligation | Compliance officer | New recurring duty | owner, frequency | ACTIVE lifecycle | next-due countdown | **Broken** (UI "New" disabled — `ObligationsPage.tsx:95`) |
| T-05 | Run attestation campaign (materialise cycles) | Compliance lead | Period start | — | DUE→IN_PROGRESS→ATTESTED→reviewed | due date + overdue | **Partial** (engine real; overdue not auto-scheduled) |
| T-06 | Officer attests obligation | Obligation owner | Cycle due | evidence refs | DUE→ATTESTED(_WITH_EXCEPTIONS) | overdue → reminder | **Partial** (evidence = string; no reminder delivery) |
| T-07 | Reviewer accepts/rejects attestation | Reviewer | Officer attests | comment | ACCEPT / REJECT / REQUEST_CHANGES→IN_PROGRESS | — | **Works** (reviewer decision real) |
| T-08 | Create policy record | Policy owner | New policy | — | DRAFT | — | **Works** (but no file capture) |
| T-09 | Upload policy file | Policy owner | After create | the document file | — | — | **Missing** (no upload; `body_url` only) |
| T-10 | Submit policy for review | Policy owner | Drafted | — | DRAFT→REVIEW | — | **Works** (status flip) |
| T-11 | Approve + publish policy | Approver | Reviewed | approver identity | REVIEW→APPROVED→PUBLISHED (needs effective_date) | — | **Partial** (no designated-approver gate; identity = whoever clicks) |
| T-12 | Version a policy | Policy owner | Material change | — | clone→DRAFT | — | **Works** (`newVersion`) |
| T-13 | Track next review date + remind | System | `nextReviewDate` approaches | — | →REVIEW_DUE | reminder before due | **Missing** (field never read; no scheduler) |
| T-14 | Expire policy automatically | System | past expiry | — | PUBLISHED→EXPIRED | — | **Missing as automation** (manual sweep button only) |
| T-15 | Request policy exception | Risk owner | Cannot comply | justification, compensating controls, risk | REQUESTED→UNDER_REVIEW→APPROVED/REJECTED | expiry | **Partial** (no "create exception" button in UI) |
| T-16 | Assign + complete policy attestation | Workforce | Policy published | evidence ref | PENDING→ATTESTED/DECLINED | — | **Partial** (evidence = free text; no campaign/reminder) |
| T-17 | Request evidence from another department | Compliance officer | Control/obligation needs proof | file, due date | OPEN→SUBMITTED→APPROVED/REJECTED→resubmit | due → reminder → escalate | **Missing from Compliance/Policy** (exists in evidence module, not invoked here) |
| T-18 | Upload + review + version evidence | Dept owner / reviewer | Evidence request | the file | review + version | expiry → notify | **Exists in evidence module; not reachable from these modules** |
| T-19 | Create gap / corrective action from finding | Auditor | Failed control/evidence | — | finding→CAPA stages | due date + escalation | **Partial** (CAPA real; due-date not enforced; no auto gap-from-evidence) |
| T-20 | Run CAPA closure (owner→independent→audit sign-off→close) | Owner/Auditor | Remediation | test evidence | IDENTIFIED→…→CLOSED | aging tier (display only) | **Works** (state machine real; no reminder delivery) |
| T-21 | External audit engagement lifecycle | Audit lead | Audit booked | — | PLANNED→…→CLOSED→FOLLOW_UP | follow-up date | **Works** (advance flow real) |
| T-22 | Generate / export report (board pack, framework coverage) | Compliance lead | Reporting period | source data | — | — | **Partial** (exports exist; coverage from real controls; board-pack linkage partial) |
| T-23 | Build audit evidence package | Auditor | External audit | linked evidence | — | — | **Missing** (no package builder tying policy+control+evidence) |
| T-24 | Reopen / archive item | Owner | Post-closure change | — | →ARCHIVED | — | **Partial** (policy archive yes; finding reopen limited) |
| T-25 | Link item across modules (control↔risk↔evidence↔policy) | Any | Mapping | — | — | — | **Partial** (mostly free-text refs, not FKs) |
| T-26 | Switch AR/EN + RTL | Any | Locale toggle | — | — | — | **Works** |
| T-27 | Enforce role permissions | System | Any action | — | — | — | **Partial** (guards present; COMPLIANCE perms admin-only on this DB) |

---

## 4. Ideal Workflow Design (target state)

### 4.1 Policy lifecycle (single, unified register)
- **Objective:** author → review → approve → publish → attest → review/expire a controlled policy document with a real file and an audit trail.
- **Actors:** Owner, Reviewer, Approver (designated), Workforce (attesters), Compliance admin.
- **Process:** Create (capture metadata **+ upload the document file** → stored via `EvidenceStorageService`/attachment, versioned) → Submit for review (assign **named reviewer**, SLA) → Reviewer comments/returns or approves → **Approver** (named, segregation-of-duty enforced) approves with e-signature → Publish (sets `effectiveDate`, `nextReviewDate` from cadence) → auto-create attestation **campaign** for the applicable population → review-due reminder fires *before* `nextReviewDate` → escalate if review overdue → expire automatically at `expiryDate`.
- **Status:** DRAFT→REVIEW→APPROVED→PUBLISHED→(REVIEW_DUE)→EXPIRED→ARCHIVED, with REJECTED return path carrying a reason.
- **Required file upload:** yes (the policy PDF). **Required approvals:** reviewer + approver, both ≠ author. **SLA:** review cycle, attestation window. **Reminders:** N days before review-due/attestation-due. **Escalation:** to manager on overdue. **Audit:** every transition (already present). **Integration:** link to controls (FK), frameworks, evidence package.

### 4.2 Evidence request workflow (invoked from a control/obligation/finding)
- Request → assign **owner + department** + **due date** → countdown/SLA badge → reminder before due → **escalate after overdue** → department uploads **file** + comment → reviewer approves/rejects **with reason** → resubmit loop → version retained → **link to the originating control/obligation/finding/policy** → show on dashboard + in reports → expire on validity end + notify → **auto-create a gap/finding if missing or rejected.** (Most primitives already exist in `module/evidence`; the work is invoking them from Compliance/Policy and adding the create-gap-on-fail rule.)

### 4.3 Obligation attestation cycle
- Campaign materialises per-period rows (DUE) → owner attests (evidence) → **auto-overdue via scheduler** + reminder → escalate → reviewer ACCEPT/REJECT/REQUEST_CHANGES → roll next due date → feed obligation register countdown + board report.

### 4.4 CAPA / corrective action
- Finding → assign owner **+ due date** → IMPLEMENTING → owner test (evidence) → independent test → audit sign-off → CLOSED, with **due-date SLA, reminders, and tier escalation actually delivered** (not just displayed), and reopen path.

### 4.5 Regulatory change → impact → control update
- Intake (regulator, citation) → applicability → impact → **link to affected controls/obligations/policies (FK)** → create remediation actions → close. Replace `window.prompt` with real forms.

**Anti-patterns to eliminate (all currently present):** create→save-only with no next step (obligations); evidence as free text with no file; approval = status flip with no named approver; dashboard KPI that links to itself; report/list that can show mock data; status machine with no SLA/reminder; manual "sweep" button standing in for a scheduler.

---

## 5. Current Implementation Findings (page/workflow by workflow)

**Legend:** ✅ works · ◑ partial · ✗ missing/broken · ▣ visual-only

### Policy Management (`/policy-management`, `PolicyManagementPage.tsx` + `PolicyManagementService.java`)
- ✅ Overview KPIs from real `aggregate()` (live: 18 total / 17 published / 3 open exceptions / 2 pending attestations).
- ✅ Documents: create, status transitions (DRAFT→REVIEW→APPROVED→PUBLISHED→EXPIRED→ARCHIVED with guards, `PolicyManagementService.java:223`), set effective date, new version (`:262`), soft delete (PUBLISHED-protected `:295`). All persisted + audit-logged + domain events.
- ✗ **No file upload** — create form fields are code/version/category/title/owner/cadence/summary; no `bodyUrl`, no file input (`PolicyManagementPage.tsx:263-290`). `PolicyDocument` has only `body_url` (`:36`).
- ◑ Approval is a status flip — `APPROVED` just stamps `approverName = actor` (`PolicyManagementService.java:238`); no designated approver, no segregation of duty, no e-signature.
- ✗ Review-due reminders — `nextReviewDate`/`reviewCadence` exist but **nothing reads them**; no scheduler.
- ▣ Expiry is a **manual button** ("Run expiry sweep", `PolicyManagementPage.tsx:115`) → `runExpirySweep()`; no `@Scheduled` caller (grep confirms only the controller endpoint calls it).
- ✗ Exceptions tab has **no "create exception" button** (only list + transition + manual sweep) — `createException` API exists but is unreachable from the UI.
- ◑ Attestations: assign + attest + decline real (object-level authz `:464`), but evidence = free-text prompt ("LMS completion id", `:582`); no campaign, no reminder, no file.
- ▣ KPI "drill-down" links all point to `/policy-management` (the same page) — not a filtered view.

### Compliance (`/compliance`, `CompliancePage.tsx` — 12 tabs)
- ◑ **Mock fallback** — every register loads via `useApiList(() => complianceApi.x(), MOCK_X)` (`:316-321`); if the API returns empty/fails it shows `mockCompliance.ts` data. Demo-masking risk.
- ✅ Controls: CRUD, framework coverage rings computed from real controls (`:333`).
- ✅ Regulatory change workflow real (NEW→…→CLOSED) — but actions via `window.prompt` (`:486-508`).
- ◑ **Legacy policy register** (this tab) is a *second* policy system: DRAFT→LEGAL_REVIEW→EXEC_APPROVAL→PUBLISHED→REVIEW_DUE→retired + reject (`:509-536`), `contentEn` text (still no file). Duplicate of `/policy-management`.
- ✅ CAPA closure buttons in Findings tab wired to real endpoints (`:566-593`); aging + escalation **tier shown** (`TIER_TONE`, `:117`) but not delivered.
- ✅ Audit engagement advance flow real (`:551`); "New engagement" routes to intake (`/intake/audit_request`).
- ◑ Findings have `dueDate` + `owner` (`AuditFinding.java:29,35`) but no SLA enforcement/escalation delivery.

### Obligations (`/compliance/obligations`, `ObligationsPage.tsx` + `ObligationAttestationService.java`)
- ✅ Register with **real due-date countdown** ("5d overdue"/"in 12d", `:222-230`), filters, detail link (live: 34 obligations).
- ✗ **"New obligation" button is `disabled`** (`:95`) — cannot create from UI.
- ✅ Attestation state machine real (DUE→ATTESTED→reviewer decision, `ObligationAttestationService.java`); `markOverdueForCampaign` exists.
- ✗ Overdue is **not auto-scheduled** (`markOverdueForCampaign` has no `@Scheduled` caller); evidence = `evidenceRefs` string.
- ◑ KPIs partly filter, partly link to generic `/compliance`.

### Cross-cutting (verified)
- ✅ **Audit trail** strong — `AuditLogService.log(...)` on every create/transition/attest/decline/CAPA stage (policy + compliance).
- ✅ **i18n/RTL** strong — bilingual labels throughout; natural Saudi Arabic; RTL automatic. (Minor: policy transition buttons render raw enum e.g. "REVIEW".)
- ✅ **Persistence** real (live create persisted id 19).
- ◑ **Permissions** — policy + compliance gated by `hasPermission('COMPLIANCE', …)` (`PolicyController.java:48…`). Per the restored DB, COMPLIANCE perms are granted to PLATFORM_ADMIN only → non-admin roles get 403.
- ⚠ **Deployment drift** — live `POST /api/policy/documents/{id}/transition` returned 404 though source defines it (`PolicyController.java:71`); the running jar is stale vs source (rebuild/restart needed). Not a code bug.

---

## 6. Critical Gaps (make the module feel incomplete / not market-ready)

| ID | Gap | Severity | Evidence |
|---|---|---|---|
| C-1 | No file upload for policies or evidence anywhere in these modules | **Critical** | `PolicyDocument.java:36` (url only); grep upload/multipart in pages = 0 |
| C-2 | Compliance/Policy not wired to the real evidence subsystem (free-text refs) | **Critical** | `PolicyAttestation.evidenceRef` string; `ObligationAttestation.evidenceRefs` string; no `EvidenceRequest`/`EvidenceLink` creation from these modules |
| C-3 | No automated reminders/escalation for policy review, policy/exception expiry, obligation overdue, CAPA due | **Critical** | no `@Scheduled` for policy/obligation/CAPA; `nextReviewDate` never read; manual sweep buttons |
| C-4 | Two divergent policy systems (legacy compliance.policies vs policy.documents) | **High** | `CompliancePage.tsx:509-536` vs `PolicyManagementPage.tsx`; two lifecycles, two backends |
| C-5 | Obligation cannot be created from UI (button disabled) | **High** | `ObligationsPage.tsx:95` |
| C-6 | Policy approval = status flip, no designated approver / SoD / e-signature | **High** | `PolicyManagementService.java:238` |
| C-7 | Compliance dashboard mock-data fallback can mask a dead backend | **High** | `CompliancePage.tsx:316-321,45-50` |
| C-8 | Crude `window.prompt`/`window.confirm` for compliance & CAPA workflow inputs | **Medium** | `CompliancePage.tsx:487,493,571,578` |
| C-9 | "Create policy exception" not reachable from the UI | **Medium** | `PolicyManagementPage.tsx` ExceptionsTab (no create button) |
| C-10 | KPI cards "drill down" to the same page; no filtered destination | **Medium** | `PolicyManagementPage.tsx:119-136` (`to="/policy-management"`) |
| C-11 | No audit-evidence-package builder (policy+control+evidence bundle) | **Medium** | absent |
| C-12 | Cross-module links are free text, not FKs (policy.relatedControls, finding→risk) | **Medium** | `PolicyDocument.relatedControls` NVARCHAR |

---

## 7. Evidence Workflow Gaps (the 20-point checklist)

| # | Required capability | Exists in evidence module | Reachable from Compliance/Policy | Verdict |
|---|---|---|---|---|
| 1 | Request evidence from another dept/user | ✅ `EvidenceRequest.departmentId/assignedToId` | ✗ | **Gap (not invoked)** |
| 2 | Assign evidence owner | ✅ | ✗ | Gap |
| 3 | Define due date | ✅ `dueDate` (live sample 2026-02-25) | ✗ | Gap |
| 4 | Countdown timer | ◑ (obligations show countdown; evidence req UI?) | ✗ | Gap |
| 5 | SLA status | ✅ `lastRemindedAt` + scheduler | ✗ | Gap |
| 6 | Reminders before due | ✅ `EvidenceRequestSlaScheduler` (`@Scheduled cron 0 40 1 * * *`) | ✗ | Gap (only for evidence-native, and *overdue* not *pre-due*) |
| 7 | Escalate after overdue | ◑ reminder yes; tiered escalation? | ✗ | Gap |
| 8 | File upload | ✅ `EvidenceStorageService` | ✗ from these modules | **Critical gap** |
| 9 | Comments | ◑ `EvidenceReview` | ✗ | Gap |
| 10 | Reviewer approve/reject | ✅ `EvidenceReviewService` | ✗ | Gap |
| 11 | Rejection reason | ◑ | ✗ | Gap |
| 12 | Resubmission | ◑ | ✗ | Gap |
| 13 | Versions | ✅ `EvidenceVersion` | ✗ | Gap |
| 14 | Audit trail | ✅ | n/a | OK in evidence |
| 15 | Link to control/risk/requirement | ✅ `EvidenceLink` (generic) | ✗ (compliance/policy don't create links) | **Critical gap** |
| 16 | Show in dashboard | ◑ evidence dashboard | ✗ in compliance/policy | Gap |
| 17 | Include in reports | ◑ | ✗ | Gap |
| 18 | Expire on validity end | ✅ `EvidenceExpiryScheduler` | n/a | OK |
| 19 | Notify before expiry | ◑ (sweep flips; pre-expiry notice?) | ✗ | Gap |
| 20 | Create gap if missing/rejected | ✗ | ✗ | **Missing everywhere** |

**Summary:** the evidence *engine* is ~70% built; Compliance and Policy use **0%** of it — they store evidence as a string. This is the single most important remediation.

---

## 8. Policy / Document Workflow Gaps (15-point checklist)

| # | Capability | Status | Evidence |
|---|---|---|---|
| 1 | Create policy record | ✅ | `PolicyManagementService.create:167` |
| 2 | **Upload file** | ✗ | `body_url` only; no upload UI |
| 3 | Assign owner | ✅ | ownerName/ownerEmail |
| 4 | Define reviewer | ✗ | no reviewer field captured in create/transition |
| 5 | Define approver | ◑ | `approverName` set on APPROVE = actor, not pre-assigned |
| 6 | Version control | ✅ | `newVersion:262` |
| 7 | Review comments | ✗ | no comment capture on REVIEW |
| 8 | Approval/rejection | ◑ | transition only; reject path only via DRAFT return |
| 9 | Publish approved version | ✅ | needs `effectiveDate` (`:235`) |
| 10 | Archive old version | ✅ | PUBLISHED→ARCHIVED |
| 11 | Track next review date | ◑ | field exists, never enforced |
| 12 | **Notify before review due** | ✗ | no scheduler |
| 13 | **Escalate overdue review** | ✗ | none |
| 14 | Link policy to controls/requirements | ◑ | `relatedControls` free text, not FK |
| 15 | Include policy in evidence package | ✗ | no package builder |

Plus **C-4 duplication**: the legacy compliance policy register (LEGAL_REVIEW→EXEC_APPROVAL) actually has a *richer approval chain* than the newer `/policy-management` one — the two should be merged into one register that keeps the named legal/exec approval **and** adds versioning/exceptions/attestations/file-upload.

---

## 9. Button & Process Testing Results

(Tested by code path + live API. "Actual" reflects current behavior.)

| Page | Button | Expected | Actual | Result | Severity |
|---|---|---|---|---|---|
| Policy · Documents | New policy | Create incl. file | Creates metadata only (no file field) | **FAIL** | Critical |
| Policy · Documents | Transition (REVIEW/APPROVED/…) | Advance state | Calls real API (live 404 = stale jar; source OK) | PARTIAL | Medium |
| Policy · Documents | Set effective date | Set date | Works (`updatePolicy`) | PASS | — |
| Policy · Documents | New version | Clone vNext | Works (`newVersion`) | PASS | — |
| Policy · Documents | Delete | Soft delete (block PUBLISHED) | Works | PASS | — |
| Policy · Overview | Run expiry sweep | Auto expiry | Manual button (no scheduler) | PARTIAL | High |
| Policy · Exceptions | Create exception | Open request form | **No button exists** | FAIL | Medium |
| Policy · Exceptions | Transition / Reject (reason) | Advance + reason | Works (reason prompt) | PASS | — |
| Policy · Attestations | Assign attestation | Create PENDING | Works | PASS | — |
| Policy · Attestations | Attest (evidence) | Attach file/evidence | Free-text prompt only | PARTIAL | High |
| Policy · Attestations | Decline (reason) | Decline + reason | Works | PASS | — |
| Compliance · Controls | New / Edit / Delete control | CRUD | Works | PASS | — |
| Compliance · Changes | Assess applicability/impact | Structured form | `window.prompt` | PARTIAL | Medium |
| Compliance · Policies (legacy) | Submit→legal→exec→publish | Approval chain | Works (window.confirm) | PARTIAL | Medium |
| Compliance · Findings | CAPA start/owner/indep/signoff/close | CAPA closure | Works (prompts) | PASS* | Medium |
| Compliance · Engagements | New engagement | Create | Routes to intake | PASS | — |
| Compliance · Engagements | Advance status | Next stage | Works | PASS | — |
| Obligations | New obligation | Create obligation | **Disabled** | FAIL | High |
| Obligations | Row → detail | Open detail | Works (Link) | PASS | — |
| Obligations | KPI "Overdue/Due" | Filter to those | Links to generic `/compliance` | PARTIAL | Low |
| Compliance · any | Export | Export report | ExportMenu present | PASS (partial source data) | — |

\* CAPA buttons work but require `window.prompt` text and deliver no reminder/escalation.

---

## 10. Data Model Gaps

- `policy.documents`: add `file_attachment_id` (FK to evidence/attachment), `reviewer_name/email`, `review_comments`, `published_by`/`approved_by` (distinct from author for SoD), and make `nextReviewDate` drive a reminder.
- `policy.attestations`: replace `evidence_ref` (string) with `evidence_item_id` FK; add campaign linkage + due date.
- `compliance.obligation_attestation`: replace `evidence_refs` (string) with a join to `evidence_links`/`evidence_items`.
- `compliance.findings`: `dueDate` exists — add `escalation_tier`, `next_reminder_at`, `reopened_at`; persist gap→action linkage.
- Cross-links: convert `PolicyDocument.relatedControls` (NVARCHAR) and policy→risk to real link rows; reuse `evidence_links` generic pattern or add `policy_control_link`.
- **Consolidate** the two policy tables (`compliance.policies` vs `policy.documents`) onto one model.

---

## 11. API Gaps

- **Evidence-from-compliance/policy**: endpoints to create an `EvidenceRequest` from a control/obligation/finding/policy and to attach the returned evidence (multipart upload) — wiring to existing `EvidenceRequestController`/`EvidenceItemController`/`EvidenceStorageService`.
- **Policy file upload**: `POST /api/policy/documents/{id}/file` (multipart) → store + set attachment id.
- **Obligation create** endpoint surfaced to UI (enable the disabled button) — `ObligationController` create likely exists; the gap is frontend.
- **Scheduled triggers** (server-side, not endpoints): policy review-due, policy/exception expiry, obligation overdue, CAPA due — see §19.
- **Reviewer/approver assignment** on policy transition (named reviewer/approver in the transition body, validated ≠ author).
- **Evidence-package** export endpoint (bundle policy + linked controls + evidence + audit).

---

## 12. UI/UX Gaps

- Replace `window.prompt`/`window.confirm` (compliance change/policy/CAPA) with real modal forms (validated, bilingual, evidence-attachable).
- Enable obligation creation; add policy-exception create button.
- Real KPI drill-down (navigate to a filtered register, not the same page).
- Policy transition buttons should show localized labels, not raw enums.
- Surface countdown/SLA badges on policy review/expiry and attestation due (obligations already do this — reuse the pattern).
- Remove or clearly flag mock-data fallback so an empty/broken backend is visible, not hidden.
- Add a policy document **viewer/preview** once upload exists.

---

## 13. Integration Gaps

| Target | State | Note |
|---|---|---|
| Risk register | Partial | `PolicyException.riskId` link; finding→risk weak |
| Control library | Implemented | controls register; policy→control free text |
| Compliance requirements/obligations | Implemented | obligation register real |
| Evidence repository | **Missing wiring** | strong module, not invoked here |
| Corrective-action register | Partial | CAPA on findings; no auto gap-from-evidence |
| Policy/document mgmt | Partial + **duplicated** | two systems |
| Incident mgmt | Missing | no link from finding/obligation to incident |
| Audit trail | Implemented | `AuditLogService` everywhere |
| Reports / board pack | Partial | `BoardPackService` exists; compliance/policy feed partial |
| Dashboard | Partial | KPIs real; drill-down weak; mock fallback |
| Notifications | Partial/Missing | domain events emitted; no consumer delivers compliance/policy deadline reminders |
| User/department directory | Partial | evidence req uses `departmentId` FK; policy attestation `userDepartment` is free text |
| Approval workflow engine | Partial | `ApprovalChain` engine exists; policy/compliance use ad-hoc flips |
| Role permissions | Partial | guards present; COMPLIANCE perms admin-only on this DB |
| AR/EN i18n | Implemented | strong |

---

## 14. Required Remediation Backlog (epics → stories)

Priority: P0 = market-blocker, P1 = important, P2 = polish. Complexity: S/M/L/XL.

### EPIC 1 — Workflow Realism Enhancement (P1)
- **Story:** As a compliance lead, I want every "create" to start a real next step (assign/route/SLA), not just save. **AC:** Given I create an obligation/policy, when saved, then an owner/reviewer is required and a due/review date is set; audit row written.

### EPIC 2 — Evidence Request Workflow (P0)
- **Story:** As a compliance officer, I want to request evidence from a department with a due date, from a control/obligation/finding/policy. **AC:** Given a control, when I click "Request evidence", then an `EvidenceRequest` is created with `departmentId` + `dueDate` + link back; visible in the department's queue; audit-logged. *(Files: `EvidenceRequestController`, compliance/policy pages, `EvidenceLink`.)*

### EPIC 3 — Evidence Upload & Review (P0)
- **Story:** As a department owner, I want to upload the actual file and a reviewer to approve/reject with reason and versions. **AC:** Given an open request, when I upload a file, then it is stored (`EvidenceStorageService`), versioned, and review approve/reject (with reason) + resubmit works; finding auto-created on reject. *(Files: `EvidenceItemController`, `EvidenceReviewService`, `EvidenceStorageService`.)*

### EPIC 4 — Evidence SLA, Reminder & Escalation (P0)
- **Story:** As a manager, I want reminders before due and escalation after overdue across policy review, attestation, evidence, CAPA. **AC:** Scheduler computes due/overdue; sends notification N days before; escalates to manager/committee tiers; throttled by `lastRemindedAt`. *(Mirror `EvidenceRequestSlaScheduler`/`ApprovalChainSchedulerService`.)*

### EPIC 5 — Policy & Document Management (P0)
- **Story:** As a policy owner, I want to upload the policy file, route to a named reviewer + approver, publish, and have review-due/expiry handled automatically. **AC:** file upload; reviewer/approver ≠ author; `nextReviewDate` reminder + auto-expiry scheduler; merged single register. *(Files: `PolicyController`, `PolicyManagementService`, `PolicyDocument`, `PolicyManagementPage`.)*

### EPIC 6 — Approval Workflow (P1)
- **Story:** As an approver, I want policy/exception approvals to use the platform approval-chain engine with SoD + e-signature. **AC:** transition to APPROVED requires a distinct approver; uses `ApprovalChain`; signature recorded.

### EPIC 7 — Audit Trail Enhancement (P2)
- Already strong; add evidence-request + file-upload events and surface the timeline on policy/obligation detail.

### EPIC 8 — Dashboard Drill-Down (P1)
- KPI cards navigate to a filtered register view; remove self-links.

### EPIC 9 — Report Source-Data Integrity (P1)
- Remove/flag mock fallback; board-pack pulls real compliance/policy/evidence; add evidence-package export.

### EPIC 10 — Cross-Module Integration (P1)
- Replace free-text refs with FK links (policy↔control, finding↔risk/incident, attestation↔evidence).

### EPIC 11 — Arabic/RTL Enhancement (P2)
- Localize transition button labels; verify new modals/forms in RTL.

### EPIC 12 — Button Functionality Completion (P1)
- Enable obligation create; add policy-exception create; replace `window.prompt`/`confirm` with forms.

### EPIC 13 — API & Backend Completion (P1)
- File-upload, evidence-from-module, scheduler triggers, reviewer/approver assignment, package export.

### EPIC 14 — Data Model Enhancement (P1)
- Fields/FKs per §10; consolidate policy tables.

### EPIC 15 — Automated Testing (P1)
- Unit (state machines, guards), API (controllers, authz), E2E (workflows).

### EPIC 16 — Regression Testing (P1)
- Snapshot existing audit/transition behavior before refactor.

### EPIC 17 — User Acceptance Testing (P1)
- Saudi GRC scenarios (NCA ECC evidence, PDPL DSR policy attestation, SAMA obligation cycle).

---

## 15. Immediate Fixes (quick wins, days)
1. **Rebuild + restart the backend** (resolve the stale-jar 404 on policy transition).
2. **Enable the obligation "New" button** + wire to existing create endpoint.
3. **Add a "Create exception" button** to the policy Exceptions tab (API already exists).
4. Replace policy transition raw-enum labels with localized labels.
5. Replace `window.prompt`/`confirm` in CAPA/changes/policy with the existing `FormModal`/`dialog` components.
6. Make KPI cards link to filtered register tabs.
7. Grant COMPLIANCE permissions to the appropriate non-admin roles (RBAC seed) so the module is usable by compliance officers, not just admin.

## 16. Structural Fixes (weeks)
1. **File upload + evidence integration** (EPICs 2/3/5) — the core of market-readiness.
2. **Scheduler suite** for policy review/expiry, obligation overdue, CAPA due, with notification delivery (EPIC 4).
3. **Merge the two policy systems** into one register (EPIC 5 + 14) keeping the legal/exec approval chain.
4. **FK cross-links** + evidence-package export (EPICs 9/10).

---

## 17. Data Model Changes Required
See §10. Net new: `file_attachment_id`, reviewer/approver fields + SoD, `evidence_item_id` FKs (replace string refs), `next_reminder_at`/`escalation_tier` on findings/attestations/policies, `policy_control_link`, single consolidated policy table.

## 18. API Changes Required
See §11. Net new endpoints: policy file upload (multipart); create-evidence-request-from-{control,obligation,finding,policy}; enable obligation create in UI; reviewer/approver assignment on transitions; evidence-package export. Net new server jobs: 4 schedulers (§19).

## 19. Workflow Engine Changes Required
- **Schedulers (mirror `EvidenceRequestSlaScheduler`):** `PolicyReviewScheduler` (remind before `nextReviewDate`, escalate overdue, flip REVIEW_DUE), `PolicyExpiryScheduler` (auto-run `runExpirySweep` + `runExceptionExpirySweep`), `ObligationOverdueScheduler` (call `markOverdueForCampaign` + remind), `CapaDueScheduler` (remind/escalate on `AuditFinding.dueDate`, deliver the existing aging tier as notifications).
- **Transitions:** add reviewer/approver gates with SoD on policy APPROVE/PUBLISH; add gap-creation rule on evidence reject/missing.
- **Notification delivery:** add consumers for the already-emitted domain events (`POLICY_PUBLISHED`, `OBLIGATION_OVERDUE`, `FINDING_CAPA_STAGE_CHANGED`, …) that create in-app/email notifications.

---

## 20. Testing Plan
- **Unit:** policy/exception/attestation/obligation/CAPA state-machine guards (valid + invalid transitions); SoD enforcement; scheduler date math.
- **API:** every controller endpoint incl. authz (admin vs compliance-officer vs viewer), multipart upload, evidence-request creation, 404/validation paths.
- **UI:** create/upload/submit/review/reject/resubmit/approve/attest flows; empty/error/RTL states; KPI drill-down.
- **E2E:** full lifecycles (policy author→publish→attest→review-due; obligation campaign→attest→overdue→escalate; control→evidence-request→upload→review→link→report).
- **Regression:** snapshot audit-log + transition outputs pre/post refactor.
- **Manual/UAT:** Saudi scenarios (NCA ECC control evidence pack; PDPL policy attestation campaign; SAMA monthly obligation).

## 21. Acceptance Criteria for Remediation (module is "market-ready" when…)
- A policy can be created **with an uploaded file**, routed to a **named reviewer + approver** (≠ author), published, attested by a population via a **campaign**, and auto-reminded before review-due and auto-expired.
- Evidence can be **requested from a department with a due date**, the department **uploads a file**, a reviewer approves/rejects with reason, versions are kept, the evidence is **linked** to its control/obligation/finding/policy, a **gap is auto-created on reject/missing**, and it appears in the dashboard + an exportable **evidence package**.
- Obligation overdue, policy review/expiry, and CAPA due all **fire reminders + escalations automatically** (no manual sweep button).
- **One** policy register, not two.
- No screen shows mock data in place of a real (empty) backend; every KPI drills to a filtered view; no `window.prompt`.
- Non-admin compliance/auditor roles can actually use the module (permissions seeded).

## 22. Implementation Order
1. **Quick wins** (§15) — rebuild, enable obligation create, exception create, kill prompts, fix labels/drill-down, seed RBAC.
2. **Evidence integration + file upload** (EPICs 2/3/5 file parts) — biggest value.
3. **Scheduler suite + notification delivery** (EPIC 4 + §19).
4. **Merge policy systems + approver SoD** (EPICs 5/6/14).
5. **FK cross-links + reports/package + dashboards** (EPICs 8/9/10).
6. **Tests** (EPICs 15/16/17) alongside each above.

## 23. Risks if Not Fixed
- **Audit/regulatory:** an NCA/SAMA/SDAIA auditor cannot retrieve an uploaded policy or evidence file → audit failure; no automated review/expiry → expired controls go unnoticed (compliance breach).
- **Business/adoption:** GRC teams will keep using email + SharePoint because evidence requests don't reach departments with deadlines; the disabled obligation button and prompt-driven flows feel unfinished → low adoption.
- **Market:** competitors (OneTrust, Archer, MetricStream) demo file upload + SLA evidence chase on slide 1; this module would lose POCs on those exact features despite a strong backend.
- **Data integrity:** free-text cross-links + mock fallback → reports that look real but aren't traceable to source.

## 24. Final Recommendation

**Partially usable — needs major remediation** (not demo-only, not market-ready).
The backend state-machines, persistence, audit trail, domain-event automation, and bilingual UI are genuinely solid — better than a typical demo. The module is **blocked from production/market use by a short, well-defined list**: real file upload, evidence-subsystem wiring, automated reminders/escalation for its own deadlines, a single policy register, and finishing a few dead/crude UI actions. None require a rewrite — they are integration + scheduling + UI-completion work on top of strong foundations. Estimated to reach market-ready: the §22 order, with EPICs 2–5 as the critical path.

---

## 25. Implementation Backlog — Detailed User Stories (sample, highest priority)

**E2-S1 (P0):** As a compliance officer, I want to request evidence from a department for a control.
*Given* a control detail page, *when* I click "Request evidence" and pick department + due date, *then* an `EvidenceRequest` is persisted (status OPEN, `departmentId`, `dueDate`), an `EvidenceLink` ties it to the control, the department sees it in its queue, and an audit row + notification are created.

**E3-S1 (P0):** As a department owner, I want to upload the evidence file.
*Given* an OPEN request assigned to me, *when* I upload a PDF and submit, *then* the file is stored (versioned), status → SUBMITTED, reviewer notified, audit logged.

**E3-S2 (P0):** As a reviewer, I want to reject evidence with a reason and have a gap created.
*Given* a SUBMITTED item, *when* I reject with reason, *then* status → REJECTED, reason stored, owner notified to resubmit, and a `finding`/gap is auto-created and linked.

**E5-S1 (P0):** As a policy owner, I want to upload the policy document.
*Given* a DRAFT policy, *when* I upload the file, *then* `file_attachment_id` is set, version retained, audit logged; publish is blocked until a file + effective date + approver exist.

**E4-S1 (P0):** As a manager, I want overdue policy reviews escalated automatically.
*Given* a PUBLISHED policy past `nextReviewDate`, *when* the daily scheduler runs, *then* status → REVIEW_DUE, owner reminded, and after the grace window the manager is escalated; all throttled and audit-logged.

*(Full backlog = EPICs 1–17 in §14; each expands to stories in this Given/When/Then form.)*

---

## 26. Test Cases (executable)

| TID | Scenario | Role | Preconditions | Steps | Expected | Actual (now) | Sev if fail |
|---|---|---|---|---|---|---|---|
| TC-01 | Create control | Compliance | logged in | POST control | persisted + audit | ✅ pass | — |
| TC-02 | Save policy draft | Owner | — | POST policy | DRAFT persisted | ✅ pass (id 19) | — |
| TC-03 | Submit policy for review | Owner | DRAFT | transition REVIEW | status REVIEW | ⚠ 404 (stale jar) | High |
| TC-04 | Request evidence (dept) | Officer | control exists | click Request evidence | EvidenceRequest+link | ✗ no action in module | Critical |
| TC-05 | Upload evidence file | Dept owner | open request | upload file | stored+versioned | ✗ no upload | Critical |
| TC-06 | Reject evidence + reason | Reviewer | submitted | reject | REJECTED+reason+gap | ✗ not wired here | Critical |
| TC-07 | Resubmit evidence | Dept owner | rejected | re-upload | new version | ✗ | High |
| TC-08 | Approve evidence | Reviewer | submitted | approve | APPROVED+link | ✗ from module | High |
| TC-09 | Evidence overdue reminder | System | past due | run scheduler | reminder+escalation | ◑ evidence-native only | Critical |
| TC-10 | Evidence expiry notice | System | past validity | scheduler | EXPIRED+notify | ◑ flips; pre-notice ✗ | High |
| TC-11 | Create policy | Owner | — | POST | persisted | ✅ | — |
| TC-12 | Upload policy file | Owner | policy exists | upload | attached | ✗ | Critical |
| TC-13 | Submit policy for approval | Owner | reviewed | transition APPROVED | named approver gate | ◑ flip only | High |
| TC-14 | Approve/publish policy | Approver | approved+eff date | transition PUBLISHED | published+publishedAt | ✅ (needs eff date) | — |
| TC-15 | Create gap | Auditor | failed control | create finding | finding+CAPA | ◑ manual | Medium |
| TC-16 | Create corrective action | Auditor | finding | CAPA start | IMPLEMENTING | ✅ | — |
| TC-17 | Close corrective action | Auditor | signed off | CAPA close | CLOSED | ✅ | — |
| TC-18 | Reopen item | Owner | closed | reopen | status reopened | ✗ limited | Medium |
| TC-19 | Export report | Lead | data | export | file w/ real data | ◑ (mock fallback risk) | Medium |
| TC-20 | Generate evidence package | Auditor | linked evidence | build package | bundle | ✗ missing | High |
| TC-21 | Dashboard drill-down | Any | KPIs | click KPI | filtered register | ✗ self-link | Medium |
| TC-22 | Arabic language | Any | locale=ar | toggle | AR labels | ✅ | — |
| TC-23 | RTL layout | Any | ar | view | mirrored | ✅ | — |
| TC-24 | Unauthorized access | Viewer | low role | call write API | 403 | ◑ (perms admin-only on this DB) | High |
| TC-25 | Audit log generation | Any | any write | inspect audit_log | row written | ✅ | — |
| TC-26 | Create obligation (UI) | Officer | — | click New | obligation created | ✗ button disabled | High |
| TC-27 | Obligation overdue auto | System | past due | scheduler | OVERDUE+remind | ✗ no scheduler | Critical |
| TC-28 | Create policy exception (UI) | Risk owner | policy exists | click New exception | exception REQUESTED | ✗ no button | Medium |

---

### Output summary

- **Review performed:** static (file:line) + DB constraint inspection + live API smoke (as `admin`) of Compliance + Policy, including the evidence subsystem they should integrate with.
- **Pages reviewed:** 7 page components (~20 tab-views); 3 read line-by-line (`CompliancePage` 12 tabs, `PolicyManagementPage` 4 tabs, `ObligationsPage`) + all key services/entities/controllers/API clients.
- **Workflows reviewed:** 10 (policy lifecycle, policy exception, policy attestation, legacy compliance-policy approval, obligation attestation, obligation campaign, CAPA closure, audit engagement, regulatory change, regulatory horizon).
- **Buttons tested:** ~22 (table §9).
- **Gaps by severity:** **Critical 6**, **High 9**, **Medium 9**, **Low 1** (+ deployment drift note).
- **File:** `D:\AYMAN\GRC\MODULE_REVIEW_AND_REMEDIATION_PLAN.md`
- **Recommended implementation order:** §22 (quick wins → evidence+upload → schedulers → merge policy+approver → links/reports/dashboard → tests).
- **Market-ready?** **No — partially usable; needs major remediation** (strong backend; blocked by file upload, evidence wiring, automated reminders/escalation, policy duplication, and a few dead UI actions).

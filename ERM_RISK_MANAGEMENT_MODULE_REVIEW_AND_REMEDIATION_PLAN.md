# ERM / Risk Management Module Review and Remediation Plan

> Enterprise Risk Management / إدارة المخاطر المؤسسية — deep reality review per the ERM Command-Loop brief. Original audit performed 2026-06-30 against `master` @ `050786e1` (repo `D:\AYMAN\GRC`), backend live on :8084, frontend on :5173. **Remediation in progress** on branch `feat/erm-remediation` — see §0.

---

## 0. Remediation Progress (live — branch `feat/erm-remediation`)

> Implementation started 2026-06-30 in §28 order. Frontend `tsc`-verified; backend `mvn`-tested; key paths live-smoked against :8084. **Not merged to `master`.** Dev DB has migrations applied by hand (Flyway off in dev).

| Epic | Item | Status |
|---|---|---|
| **E-A** | **Risk Acceptance workflow** — new `RiskAcceptance` entity (migration **V241**) + `RISK_APPROVE` permission/grants; `request` snapshots residual server-side + flags Board sign-off when above appetite; `approve` (gated `RISK_APPROVE`) flips the risk → **ACCEPTED**; `revoke` + `expireSweep` reopen the risk (UNDER_REVIEW); `RiskAcceptanceWizard` now calls the real API (was the generic submission inbox). **+5 unit tests; live-smoked** (board-gate → 400, approve → risk ACCEPTED, past-expiry → 400, expire-sweep OK). | ✅ Shipped |
| **E-B** | **Control-effectiveness → residual derivation** — new `RiskExposureService.recomputeResidual` aggregates linked-control effectiveness (EFFECTIVE 1.0 / NEEDS_IMPROVEMENT 0.5 / INEFFECTIVE 0.1 / NOT_TESTED 0.0) weighted by `contribution_pct` (or 1/N), capped at **75%** mitigation; derives `residualL/I = round(inherent·√retained)` so the score scales by exactly the retained fraction. **Auto-recomputes** on control link/effectiveness change (wired into `ErmGapService.linkRiskControl`); `POST /risks/{id}/recompute-residual` forces it; `GET /risk/exposure/uncontrolled` surfaces risks with **no** linked control. **Manual re-assessment stays the authoritative override** (not auto-clobbered). No schema change. **+7 unit tests** (caught a real ternary-unboxing NPE), **live-smoked** (link→residual 20→6, NOT_TESTED→no credit, uncontrolled list flips). | ✅ Shipped |
| **E-C** | **Evidence-request + file-upload for ERM** — wired the **generic** `EvidenceRequestsField` (request-from-department + SLA reminders/escalation + review/reject + comment thread, served by `/api/v1/evidence/requests` and the already-running `EvidenceRequestSlaScheduler`) into `RiskDetailPage` (`entityType="Risk"`) and `KriDetailPage` (`entityType="Kri"`, + `AttachmentsField`). **No backend change** — `EvidenceRequest.entityType` is free-form, so the ERM anchors work on the same infra as BCM/compliance. **Scope:** `Risk` + `Kri` are the entities with standalone detail surfaces today; treatment-plan evidence folds into **E-D** (treatment consolidation) and acceptance/control evidence follows as those surfaces land. `tsc` clean; **live-smoked** (Risk + Kri anchors: create → `by-entity` lookup → status → comments, all green). | ✅ Shipped (Risk + KRI) |
| **E-D** | **Consolidate treatment + mandatory-treatment gate** — designated `risk_lc.TreatmentPlan` (strategy + actions + approval + cost) the **canonical** model; rewired `RiskTreatmentWizard` to `POST /api/risk-lc/treatment-plans` (was the generic submission inbox); added the **mandatory-treatment gate** in `RiskService.close()` — a HIGH/VERY_HIGH/CRITICAL risk can't be CLOSED without a live (non-rejected) treatment plan (ACCEPTED risks exempt — formal acceptance is the documented decision); legacy `risk.RiskTreatment` documented as superseded (kept for the RiskDetailPage treatments-tab back-compat). **+4 unit tests** (28 RiskService total green), `tsc` clean, **live-smoked** (CRITICAL close → 409 → add plan → CLOSED; LOW closes freely). | ✅ Shipped (gate + wizard→canonical) |
| **E-H** | **Appetite ↔ register + breach escalation** — `RiskAppetiteService.recomputeExposureFromRegister()` pulls each APPROVED appetite statement's `currentExposure` from the live register (worst open residual per category, via `RiskRepository.findMaxResidualScoreByCategory`) instead of a manual entry; a breach now **emits an `APPETITE_BREACHED` F8 event** (was silently recorded), with **dedup** (no duplicate while an OPEN breach exists → safe to re-run/schedule). `POST /api/v1/risk/appetite/recompute-exposure` runs it. Extended the E-D close-gate: a risk **over its category's appetite ceiling** also requires a treatment plan (the deferred E-D piece). **+4 appetite tests + over-appetite gate test** (33 risk-module total), **live-smoked** (recompute → exposure 25, breach raised; re-run → 0 new). | ✅ Shipped |
| **E-G** | **Typed register linkage** — new `risk.risk_links` (migration **V242**) + `RiskLink` entity/repo/service/controller: a risk now carries typed links to other register objects (OBJECTIVE/PROCESS/SERVICE/ASSET/SUPPLIER/PROJECT/INCIDENT/CONTROL/OBLIGATION/OTHER), tenant-scoped, link-type validated against a fixed vocabulary. `GET/POST /api/v1/risk/risks/{id}/links` + `DELETE /api/v1/risk/links/{id}`. Frontend: the RiskDetailPage **links tab is now live** (new `RiskLinksPanel` — load/add/delete) instead of the `RISK_LINKS_BY_ID` mock. (The "first-class Bow-Tie link" alternative already exists via `BowTieAnalysis.linkedRiskId`.) **+4 unit tests**, `tsc` clean, **live-smoked** (add ASSET/SUPPLIER, lowercase normalised, unknown type → 400, delete). | ✅ Shipped |
| **E-F** | **Multi-dimensional impact + configurable methodology** — new `risk.methodology_config` (org-singleton, seeded) + `risk.risk_impact_ratings` (migration **V243**). A risk's impact is scored across the org's configured dimensions (default FINANCIAL/OPERATIONAL/REGULATORY/REPUTATIONAL/CYBER/PRIVACY/SAFETY/STRATEGIC); the **worst dimension drives** the inherent impact (→ L×I → level). The methodology (dimensions + likelihood/impact scale maxima) is **editable** (`GET/PUT /api/v1/risk/methodology`) and **consumed** — impact ratings are validated against it (declared dimension + 1..impactScaleMax). `GET/PUT /risks/{id}/impact-dimensions`. Frontend: `ImpactDimensionsPanel` on the risk overview (per-dimension scoring → worst). **+5 unit tests**, `tsc` clean, **live-smoked** (worst dim 5 → inherentImpact 2→5, score 6→15; unknown dim/over-scale → 400; methodology edit reflected). **Deferred:** config-driven **matrix bands** (`risk_level` is a DB computed column) + control-effectiveness-scale wiring into E-B. | ✅ Shipped (impact dims + methodology) |
| **E-E** | **KRI consolidation (part 1 — controller split + canonical decision)** — retired the mixed **`KriAppetiteController`** (which confusingly hosted both KRI *and* appetite endpoints): split into a dedicated canonical **`RiskAppetiteController`** (all `/api/v1/risk/appetite*`, incl. the E-H `recompute-exposure`) + a clearly-`@Deprecated` **`LegacyKriController`** (keeps the live frontend `/kris*` working). Marked **`KriEngineService` `@Deprecated`** with a Javadoc pointer to the canonical Sprint-13 `risk/kri/*` engine (UUID ids, full breach open/escalate/de-escalate/close lifecycle, F8 events, sustained-RED auto-CAPA, scheduler) at `/api/v1/risk/kri/*`. **No behaviour change**; compiles clean, risk-module tests green, **live-smoked** (relocated `/appetite*`+`recompute-exposure`, legacy `/kris*` 5 KRIs, canonical `/kri/dashboard` all 200; no ambiguous-mapping boot failure). **Deferred (honest, the larger slice):** legacy→canonical **data migration** (dev KRI data lives entirely in `kri_definitions`/`kri_readings`/`kri_breach_events`; canonical tables are empty), repointing the **frontend** (3 KRI pages — numeric id→UUID, `code`→`kriCode`, `band`→`status`) + the **5 backend consumers** (FrameworkAnchorService compliance↔KRI anchor, ExportService, BoardPackService, DocxExportService, BoardPackPptxService), then deleting `LegacyKriController`/`KriEngineService`/legacy entities. | 🟡 Partially shipped (controller split) |
| **E-J / E-I / E-K** | Review/approval gates, policy/committee, reporting | ⏳ Pending |

**Order (§28):** E-A → E-B → E-C → E-D → E-H → E-G → E-F → E-E → E-J → E-I → E-K.

---

## 1. Executive Summary

**The Risk Management / ERM module is NOT a demo — it is one of the most built-out modules in the platform**, second only to BCM. It has real persisted registers, DB-computed inherent/residual scoring, a status state-machine with F8 automation + audit, a working KRI subsystem, risk-appetite statements with breach severity, Bow-Tie analysis, a FAIR quantitative calculator, emerging-risk and scenario registers, and a periodic risk-owner attestation cadence.

Live evidence (this review, org 1): `risks` **51**, `appetite statements` **4** (1 in CRITICAL breach), `KRIs` **15** (1 amber), `risk_lc treatment_plans` **5**, `risk categories` **38** — all real rows. Creating a risk computes `inherent_score`/`residual_score`/`risk_level` server-side (verified: a 5×5 risk → `CRITICAL`, residual 25). `RISK_CREATED`/`RISK_TRANSITIONED` fan out to the F8 engine; transitions are audit-logged.

**However, it is not yet market-ready for an audited Saudi ERM buyer**, for a small number of *specific, high-impact* reasons:

1. **Risk Acceptance is demo-grade.** There is **no `RiskAcceptance` entity**. The `RiskAcceptanceWizard` collects a textbook flow (justification → compensating conditions → time-boxed expiry → CRO/Board sign-off) but its submit calls the **generic `submitWizard()`** — it writes a row to the generic *workflow-submission inbox*, **does not set the risk to ACCEPTED, does not persist an enforceable expiry, does not schedule revalidation/auto-reopen, and the "Board required above appetite" gate is client-side only.** The wizard's own javadoc claims it "creates `risk_lc.acceptance_request`" — that table/entity does not exist. `RiskService.accept()` is a bare status flip with none of the governance metadata.
2. **Control effectiveness does not drive residual risk.** `RiskControl` captures `effectiveness` + `contribution_pct` and its javadoc cites `RiskExposureService.recomputeResidual` — **that service does not exist anywhere in the codebase.** Residual is a *free manual re-score* (`RiskAssessmentService.record()` promotes the entered L×I straight to `residual_*`); it is never derived from inherent × control-effectiveness. Control effectiveness is decorative to the math.
3. **No evidence-request or file-upload workflow.** `module/risk` contains **zero** references to `EvidenceRequest`, `AttachmentService`, or `MultipartFile`. Risk has no "request evidence from a department," no upload on risks/treatments/controls/acceptance, no SLA/reminders/escalation, no review/reject→gap. (Same class of gap BCM had before its B-B/B-C/B-D remediation.)

Two structural smells compound the above: **two competing treatment subsystems** (`risk.risk_treatments` simple vs `risk_lc.treatment_plans` rich) and **two competing KRI subsystems** (`risk/entity/Kri*` + `KriEngineService` + `KriAppetiteController` vs `risk/kri/*`).

**Verdict (see §31): Partially usable — needs targeted remediation** (strong foundation; ~3 Critical + several High gaps block confident production use for an audited Saudi entity).

---

## 2. Module Reviewed

- **Module:** Risk Management / Enterprise Risk Management (`module/risk` + `module/risk_lc`), surfaced under the **"Risk Management"** pillar.
- **Date:** 2026-06-30 · **Reviewer role:** ERM/ISO 31000 consultant + GRC product architect + QA + auditor (per brief).
- **Backend:** `tss-grc-backend/.../module/risk` (core `Risk`/`RiskAssessment`/`RiskTreatment`/`RiskControl`/`RiskCategory`; `appetite`; `kri/`; `bowtie/`; `fair/`; `emerging/`; `scenario/`) + `module/risk_lc` (`TreatmentPlan`/`TreatmentAction`). ~10 services, ~9 controllers.
- **Frontend:** `pages/risk/` (RiskRegisterPage, RiskDetailPage, NewRiskModal, KriDashboard/Admin/Detail, RiskBowTie + Detail, FairCalculator), `pages/dashboards/ErmDashboardPage`, `pages/emerging-risk`, `pages/risk-scenarios`, `pages/risk-lifecycle/RiskLifecyclePage`, and **3 wizards** under `pages/workflow/erm/` (Identification, Treatment, Acceptance).
- **DB:** risk Flyway migrations — `V15` (lifecycle), `V29` (status lifecycle align), `V35` (scenarios), `V39` (emerging), `V80` (advanced: appetite/KRI/controls/assessments), `V132` (automation rules), `V164` (bow-tie), `V190` (risk-based vuln SLAs), `V212` (residual level computed column), `V217`/`V220` (bilingual AR).
- **Automation:** `RISK_CREATED` / `RISK_TRANSITIONED` F8 events; `KriSchedulerService` (@Scheduled); risk attestation cadence (`next_attestation_date`).

> Endpoints verified live (200): `/api/v1/risk/risks`, `/api/v1/risk/appetite[/dashboard]`, `/api/v1/risk/kri/dashboard`, `/api/risk-lc/treatment-plans`, `/api/v1/risk/categories`. Risk controllers are split across `RiskController`, `RiskAdvancedController`, `KriAppetiteController` (all `/api/v1/risk`), `kri/KriController` (`/api/v1/risk/kri`), `bowtie`, `fair`, plus `emerging` + `scenario` on the **legacy `/api/risk/...`** prefix (inconsistent versioning — minor finding).

---

## 3. Full Real-World ERM Task List

The 75 lifecycle tasks the module must support, with current status (✅ implemented · ◑ partial · ✗ missing/demo):

| # | Task | Status | Note |
|---|---|---|---|
| 1 | Create ERM cycle | ✗ | no ERM "cycle" concept (unlike BCM); risks are standalone |
| 2 | Define risk management policy | ✗ | no risk-policy record/workflow |
| 3 | Upload risk policy document | ✗ | no file upload in risk |
| 4 | Submit risk policy for review | ✗ | — |
| 5 | Approve risk policy | ✗ | — |
| 6 | Define risk methodology | ✗ | scales hard-coded (1..5); not configurable |
| 7 | Configure risk matrix | ✗ | matrix implied by computed columns; not configurable |
| 8 | Configure impact dimensions | ✗ | single `impact` integer; no multi-dimensional impact |
| 9 | Configure likelihood scale | ✗ | fixed 1..5 |
| 10 | Configure control-effectiveness scale | ◑ | enum EFFECTIVE/NEEDS_IMPROVEMENT/INEFFECTIVE/NOT_TESTED exists; not configurable, not scored into residual |
| 11 | Define risk appetite statement | ✅ | `RiskAppetiteService` (create/approve/retire) |
| 12 | Define risk tolerance thresholds | ✅ | toleranceLower/Upper + severity bands |
| 13 | Define risk taxonomy | ◑ | flat `RiskCategory` only; no hierarchical taxonomy |
| 14 | Define risk categories | ✅ | `RiskCategoryController` (38 live) |
| 15 | Add business objective | ✗ | no objective register / linkage |
| 16 | Add risk source | ◑ | `source_module`/`source_entity_id` generic only |
| 17 | Create risk record | ✅ | `RiskController` POST; computed scores |
| 18 | Assign risk owner | ✅ | `owner_id` |
| 19 | Link risk to objective | ✗ | no objective FK |
| 20 | Link risk to service/process/asset/supplier/project | ✗ | only generic `source_entity_id` + framework clause; no typed FKs |
| 21 | Define causes | ◑ | only via separate Bow-Tie threats |
| 22 | Define event | ◑ | risk title/description; no structured event field |
| 23 | Define consequences | ◑ | only via Bow-Tie consequences |
| 24 | Identify existing controls | ✅ | `RiskControl` bridge (risk↔control + effectiveness) |
| 25 | Assess inherent likelihood | ✅ | `inherent_likelihood` |
| 26 | Assess inherent impact | ✅ | `inherent_impact` |
| 27 | Calculate inherent risk | ✅ | DB computed `inherent_score` |
| 28 | Assess control effectiveness | ◑ | captured on `RiskControl`; **not aggregated** (no `RiskExposureService`) |
| 29 | Calculate residual risk | ◑ | computed `residual_score`, but residual is a **manual re-score**, not derived from controls |
| 30 | Compare residual to appetite | ◑ | appetite has its own exposure tracker; **not auto-compared** to a risk's residual |
| 31 | Create risk treatment plan | ◑ | **two** subsystems (`risk.RiskTreatment`, `risk_lc.TreatmentPlan`); wizard writes to generic inbox |
| 32 | Assign treatment owner | ✅ | both treatment models have owner |
| 33 | Add treatment actions | ◑ | only `risk_lc.TreatmentPlan` has child `TreatmentAction`s |
| 34 | Define treatment due date | ✅ | due_date present |
| 35 | Request evidence for treatment | ✗ | no evidence-request workflow |
| 36 | Upload treatment evidence | ✗ | no file upload |
| 37 | Review treatment evidence | ✗ | — |
| 38 | Reject treatment evidence | ✗ | — |
| 39 | Resubmit treatment evidence | ✗ | — |
| 40 | Approve treatment evidence | ✗ | — |
| 41 | Validate treatment effectiveness | ◑ | `risk_lc` plan has COMPLETED status; no effectiveness re-test gate |
| 42 | Close treatment | ✅ | status transition (both models) |
| 43 | Request risk acceptance | ◑ | wizard exists but writes to generic inbox |
| 44 | Add acceptance justification | ◑ | captured in wizard draft JSON only |
| 45 | Add compensating controls | ◑ | free text in wizard; not structured |
| 46 | Set acceptance expiry date | ◑ | captured in wizard; **not enforced** |
| 47 | Submit acceptance for approval | ◑ | generic submission status only |
| 48 | Approve risk acceptance | ◑ | no real approval matrix; `RiskService.accept()` is a bare status flip |
| 49 | Revalidate expired acceptance | ✗ | no expiry scheduler / auto-reopen |
| 50 | Create KRI | ✅ | `kri/KriController` (15 live) |
| 51 | Define KRI threshold | ✅ | green/amber/red bands |
| 52 | Update KRI value | ✅ | `KriMeasurementService` |
| 53 | Trigger KRI breach | ✅ | `KriBreachService` + scheduler |
| 54 | Escalate risk appetite breach | ◑ | `AppetiteBreachEvent` saved OPEN; **no F8/escalation** |
| 55 | Schedule risk review | ◑ | attestation cadence (`next_attestation_date`); no general review scheduler |
| 56 | Complete periodic risk review | ◑ | `RiskAttestation` records attestations |
| 57 | Reassess stale risk | ◑ | re-assessment exists; no stale-flagging gate |
| 58 | Create emerging risk | ✅ | `EmergingRiskService` |
| 59 | Convert emerging risk to active risk | ✅ | conversion path present |
| 60 | Run scenario analysis | ✅ | `RiskScenarioService` |
| 61 | Create issue/finding linked to risk | ◑ | risk↔control↔finding pivot exists; issue linkage indirect |
| 62 | Create corrective action linked to risk | ◑ | CAPA module exists; no direct risk→CAPA create from a risk |
| 63 | Link incident to realized risk | ✅ | `bumpLikelihoodFromIncident` |
| 64 | Link audit finding to risk | ◑ | governance pivot (risk/control/finding) read-side |
| 65 | Generate risk heatmap | ✅ | computed L×I + risk_level powers heatmap |
| 66 | Generate top-risk report | ◑ | dashboard surfaces; dedicated export unclear |
| 67 | Generate risk committee report | ✗ | no committee report |
| 68 | Generate board risk report | ◑ | board-pack exists platform-wide; ERM section depth unclear |
| 69 | Generate appetite breach report | ◑ | dashboard counts; no formal report/export |
| 70 | Export risk register | ◑ | verify export endpoint |
| 71 | Generate audit evidence package | ✗ | no risk evidence package |
| 72 | Switch Arabic/English | ✅ | bilingual columns (V217/V220) + RTL |
| 73 | Test role permissions | ◑ | `@PreAuthorize` present; coverage varies |
| 74 | Archive closed risk | ◑ | soft-delete; no explicit archive state |
| 75 | Reopen risk with approval | ◑ | `reopen()` exists (CLOSED→OPEN); **no approval gate** |

**Totals:** ~22 ✅ · ~32 ◑ · ~21 ✗. The ✗/◑ cluster heavily in **acceptance, evidence/upload, methodology configuration, register linkage, and policy/committee** — the same "looks built but not operationally wired" pattern the brief warns about.

---

## 4. Ideal ERM Workflow Design (target)

Each states the realistic target; §5–§14 compare to current. (Organizer: Trigger → Actors → Status lifecycle → Evidence → Approvals → SLA/Reminder/Escalation → Audit → Linked modules → Reports.)

1. **Risk Governance** — risk policy + methodology + appetite + taxonomy + committee → upload signed policy → review → approve → publish → annual review reminder. *Target adds: policy record + methodology/matrix config + committee.*
2. **Risk Methodology Config** — configurable likelihood scale, impact **dimensions** (financial/operational/regulatory/reputational/cyber/privacy/safety/strategic), control-effectiveness scale, and matrix → drives scoring + heatmap.
3. **Risk Appetite** — per-dimension appetite + tolerance → **aggregate live residual exposure from the register** → breach severity → F8 escalation to CRO/committee.
4. **Risk Taxonomy** — hierarchical categories + risk sources.
5. **Risk Identification** — capture cause-event-consequence + link to objective/process/service/asset/supplier/project → owner → inherent scoring.
6. **Risk Assessment** — inherent L×I (multi-dimensional) → control identification + effectiveness → **residual derived from inherent × aggregated control effectiveness** → appetite comparison → required-treatment logic.
7. **Control Effectiveness** — per-control rating + contribution% → `recomputeResidual` aggregation → "uncontrolled risk" flag.
8. **Risk Treatment** — ONE canonical plan (strategy + actions + owners + due + cost/benefit + approval) → evidence request → review → effectiveness validation → closure. **Mandatory when residual > appetite.**
9. **Risk Acceptance** — structured record: justification + compensating controls + residual snapshot + **time-boxed expiry** + approval matrix (CRO; Board if above appetite) → sets risk ACCEPTED → revalidation reminder → **auto-reopen on expiry**.
10. **KRI Monitoring** — ONE canonical KRI subsystem: definition + thresholds + readings + breach → escalation → dashboard/report.
11. **Risk Review** — scheduled periodic review → reassess → stale-flagging.
12. **Risk Escalation** — critical/appetite-breach → committee + F8.
13. **Emerging Risk** — horizon scan → convert to active risk.
14. **Scenario Analysis** — scenario → impact → linkage to risks.
15. **Issue & Corrective Action** — risk → issue/CAPA with closure loop.
16. **Evidence Request** — request from owner/dept → due + countdown → reminders → escalation → upload → review → approve/reject(reason) → resubmit → expiry → gap on missing/rejected.
17. **Policy/Document** — create → upload → owner/reviewer/approver → version → approve → publish → next-review reminder.
18. **Risk Committee** — agenda from top risks/breaches/overdue treatments → minutes → resolutions → actions.
19. **Board Risk Reporting** — board pack ERM section from live source data with drill-down.
20. **Audit Evidence Package** — one-call manifest: risk + assessment + controls + treatment + acceptance + KRI + evidence + approvals.

**Anti-patterns explicitly rejected (and where found):** acceptance without enforced expiry/approval → **found (wizard → generic inbox)**; residual not derived from control effectiveness → **found (no `RiskExposureService`)**; evidence request without owner/deadline → **found (absent)**; two parallel implementations of the same workflow → **found (treatment ×2, KRI ×2)**; button that records but doesn't trigger the next workflow → **found (treatment/acceptance wizards)**.

---

## 5. Current Implementation Findings (by area)

| Area | State | Findings |
|---|---|---|
| Navigation / pillar | ✅ | Risk Management pillar; register, dashboard, KRI, bow-tie, FAIR, emerging, scenarios, lifecycle, 3 wizards |
| ERM dashboard | ✅ | `ErmDashboardPage` (`/risk/dashboard`) |
| Risk policy / methodology | ✗ | no policy record, no configurable methodology/matrix/scales/impact-dimensions |
| Risk appetite | ◑ | statements + tolerance + breach severity + dashboard; **exposure is manual, not aggregated from the register; breach doesn't escalate (F8)** |
| Risk taxonomy / categories | ◑ | flat categories only |
| Risk register | ◑ | owner + inherent/residual + status + framework links; **no objective/process/asset/supplier FK, no cause-event-consequence (only via Bow-Tie)** |
| Risk assessment | ◑ | inherent + residual computed; **residual is a manual re-score, not derived from control effectiveness; single-dimension impact** |
| Control effectiveness | ◑ (data) / ✗ (math) | `RiskControl.effectiveness` + `contribution_pct` captured; **`RiskExposureService.recomputeResidual` referenced but does not exist** |
| Risk treatment | ◑ | **two subsystems** (`risk.RiskTreatment` simple, `risk_lc.TreatmentPlan` rich+actions+approval); `RiskTreatmentWizard` writes to generic inbox |
| Risk acceptance | ✗ | **no entity**; `RiskAcceptanceWizard` → generic submission; `accept()` bare status flip; no expiry/revalidation/auto-reopen |
| KRI | ◑ | working `risk/kri/*` subsystem (15 live, breach + scheduler); **duplicated by legacy `risk/entity/Kri*` + `KriEngineService`** |
| Bow-Tie | ✅ | threats/consequences/preventive+mitigating controls |
| FAIR (quantitative) | ✅ | `FairCalculatorService` |
| Emerging risk | ✅ | register + convert-to-active |
| Scenario analysis | ✅ | scenario register |
| Risk review / attestation | ◑ | periodic owner attestation cadence; no general review scheduler / stale-flag |
| Evidence | ✗ | no request workflow, no upload, no review/expiry — **headline gap** |
| Reporting / export | ◑ | heatmap + dashboards; committee report + evidence package missing |
| Audit trail | ✅ | `AuditLogService` on CRUD + transitions |
| Automation (F8) | ◑ | RISK_CREATED/TRANSITIONED emit; appetite/KRI breach not fully fanned to F8 |
| Arabic / RTL | ✅ | bilingual columns + RTL |

---

## 6. Critical Gaps

| ID | Gap | Severity | Evidence |
|---|---|---|---|
| **E-1** | **Risk Acceptance is demo-grade** — no `RiskAcceptance` entity; wizard → generic `submitWizard()` inbox; no risk-status link, no enforced expiry, no revalidation/auto-reopen, no real approval matrix | **Critical** | `RiskAcceptanceWizard.tsx:50-73` calls `submitWizard`; no acceptance entity in `module/risk`; `RiskService.accept()` is a bare status flip |
| **E-2** | **Control effectiveness does not drive residual** — `RiskExposureService.recomputeResidual` referenced by `RiskControl` but **does not exist**; residual is a manual re-score | **Critical** | grep `RiskExposureService` → only the javadoc mention; `RiskAssessmentService.record()` promotes entered L×I to residual |
| **E-3** | **No evidence-request / file-upload workflow** for risks/treatments/controls/acceptance | **Critical** | zero `EvidenceRequest`/`AttachmentService`/`MultipartFile` in `module/risk` |
| **E-4** | **Two competing treatment subsystems** + treatment wizard writes to generic inbox | **High** | `risk.risk_treatments` vs `risk_lc.treatment_plans`; `RiskTreatmentWizard.tsx:55` → `submitWizard` |
| **E-5** | **Two competing KRI subsystems** | **High** | `risk/entity/Kri*` + `KriEngineService` + `KriAppetiteController` vs `risk/kri/*` (the live one) |
| **E-6** | **No multi-dimensional impact; no configurable methodology/matrix/scales** | **High** | single `impact` int 1..5; no impact-dimension/likelihood-scale config entities |
| **E-7** | **Risk register lacks typed linkage** (objective/process/service/asset/supplier/project) + structured cause-event-consequence | **High** | `Risk` entity has only `source_entity_id` + framework clause; C-E-C only in Bow-Tie |
| **E-8** | **Appetite breach is a standalone manual tracker** — not aggregated from register residuals; no F8 escalation; no mandatory-treatment gate when residual > appetite | **High** | `RiskAppetiteService.currentExposure` hand-set; breach event saved OPEN with no escalation |
| **E-9** | **Reopen / acceptance / archive lack approval gates**; risk review scheduler partial | **Medium** | `reopen()` ungated; no archive state; attestation-only review |
| **E-10** | Inconsistent API versioning (`/api/v1/risk` vs `/api/risk/...` for emerging/scenario) | **Low** | controller mappings |

---

## 7. Risk Governance Gaps
- **Missing entirely:** risk **policy** record + document workflow; configurable **methodology** (likelihood scale, impact dimensions, control-effectiveness scale, matrix); **risk committee** (BCM links to `admin.committee`; ERM does not).
- **Present + good:** risk-appetite statements with approve/retire lifecycle + tolerance bands + breach severity; ISO 31000 / COSO framework tagging on risks.
- **Partial:** taxonomy is a flat category list (no hierarchy, no risk-source register).

## 8. Risk Register Gaps
- **Strong:** owner, inherent/residual L×I with DB-computed score + risk_level, identified/review dates, attestation cadence, F8 events, audit trail, bilingual.
- **Gaps:** no typed FK to **objective / process / service / asset / supplier / project** (only a generic `source_entity_id`); **cause-event-consequence** is not structured on the risk (only inside a separate Bow-Tie analysis); `applyRequest` cannot capture controls/linkages on create/update.

## 9. Risk Assessment Gaps
- **Strong:** inherent vs residual snapshots; 1..5 scale validation; assessor stamped server-side; incident→residual-likelihood bump.
- **Gaps:** **residual is not derived from inherent × control effectiveness** (manual re-score); **single-dimension impact** (no financial/operational/regulatory/reputational/cyber/privacy/safety/strategic); **no automatic appetite-breach evaluation** on assessment; matrix/scales hard-coded.

## 10. Risk Treatment Gaps
- **Two parallel models:** `risk.RiskTreatment` (type/owner/due/status — no actions/approval/cost/evidence) and `risk_lc.TreatmentPlan` (strategy + child `TreatmentAction`s + DRAFT→SUBMITTED→APPROVED→IN_PROGRESS→COMPLETED→REJECTED + cost/benefit + approver). A risk's "treatment" can live in either → fragmentation.
- `RiskTreatmentWizard` persists to the **generic submission inbox**, not to either treatment model.
- **No evidence on treatment**, no effectiveness-validation gate before closure, **no mandatory-treatment enforcement** for critical/above-appetite risks (verified: critical risk created with no treatment, no warning).

## 11. Risk Acceptance Gaps
- **No `RiskAcceptance` entity.** The 3-step wizard (justification → time-boxed expiry → CRO/Board sign-off) is well-designed UI but writes a generic workflow-submission row; the draft (expiry, compensating controls, sign-off) is an opaque JSON blob.
- **Not wired to the risk:** accepting does not set status ACCEPTED, does not store the expiry on the risk, does not require approval server-side, and the "Board required above appetite" rule is computed client-side only.
- **No revalidation / auto-reopen:** expiry is captured but nothing reopens the risk when it lapses. This is the single biggest audit exposure in the module.

## 12. KRI and Monitoring Gaps
- **Working subsystem** (`risk/kri/*`): definitions, thresholds (green/amber/red), measurements, breach detection, scheduler, dashboard (15 KRIs live, 1 amber).
- **Gap:** a **second, competing** KRI implementation (`risk/entity/KriDefinition`+`KriReading`+`KriBreachEvent`, `KriEngineService`, `KriAppetiteController`) coexists — duplicate tables/logic, ambiguous source of truth.
- KRI→risk linkage and KRI breach → F8 escalation should be confirmed end-to-end on the canonical subsystem.

## 13. Evidence Workflow Gaps (the 20-point checklist)
| # | Capability | ERM status |
|---|---|---|
| 1 | Request evidence from dept/user | ✗ |
| 2 | Assign evidence owner | ✗ |
| 3 | Due date | ✗ |
| 4 | Countdown timer | ✗ |
| 5 | SLA status | ✗ |
| 6 | Reminders before due | ✗ |
| 7 | Escalate after overdue | ✗ |
| 8 | File upload | ✗ |
| 9 | Comments | ✗ |
| 10 | Reviewer approve/reject | ✗ |
| 11 | Rejection reason | ✗ |
| 12 | Resubmission | ✗ |
| 13 | Versions | ✗ |
| 14 | Audit trail | ✅ (general CRUD/transition audit) |
| 15 | Link evidence to risk/control/treatment/KRI/acceptance | ✗ |
| 16 | Show in dashboard | ✗ |
| 17 | Include in reports | ✗ |
| 18 | Expire when validity ends | ✗ |
| 19 | Notify before expiry | ✗ |
| 20 | Create gap if missing/rejected | ✗ |

**This is the weakest area.** The generic evidence subsystem (`EvidenceRequest`/`EvidenceLink`/`AttachmentService` + SLA scheduler + `EvidenceRequestsField`) already exists platform-wide and is now wired into BCM — **ERM simply does not call it.** Reuse, don't rebuild.

## 14. Policy/Document Workflow Gaps
- No risk **policy/methodology document** record at all (no upload, version, reviewer/approver, next-review reminder). The platform policy-management + generic attachment patterns exist and should be reused.

---

## 15. Button & Process Testing Results (representative)

Live + code-grounded.

| Page / API | Action | Expected | Actual | Pass/Fail | Severity | Fix |
|---|---|---|---|---|---|---|
| `/api/v1/risk/risks` | List | rows | OK (51) | Pass | — | — |
| Create Risk | compute level/score | server-computed | OK (5×5 → CRITICAL/25) | Pass | — | — |
| Create CRITICAL risk | warn/require treatment | **201, nothing enforced** | **Fail** | High | E-8 |
| Risk transitions | OPEN→…→CLOSED/ACCEPTED | guarded + audited + F8 | OK | Pass | — | — |
| Accept Risk (wizard) | structured time-boxed acceptance | **generic inbox row; risk not ACCEPTED** | **Fail** | Critical | E-1 |
| Assess control effectiveness | feed residual | **residual unchanged (no aggregation)** | **Fail** | Critical | E-2 |
| Treatment wizard | create real treatment plan | **generic inbox row** | **Fail** | High | E-4 |
| `/api/v1/risk/appetite/dashboard` | breach counts | OK (1 critical) | Pass | — | — |
| Appetite breach | escalate | **event saved OPEN, no escalation** | **Fail** | High | E-8 |
| `/api/v1/risk/kri/dashboard` | KRI RAG | OK (15, 1 amber) | Pass | — | — |
| Request evidence (any risk) | open evidence workflow | **no such action** | **Fail** | Critical | E-3 |
| Identification wizard | create real risk | OK (`riskApi.create`) | Pass | — | — |

---

## 16. Data Model Gaps
- **Missing entities:** `RiskAcceptance` (justification, compensating controls, residual snapshot, expiry, approval matrix, status, audit); risk **policy/methodology** + **impact-dimension** + **likelihood/matrix config**; objective register; risk↔(process/service/asset/supplier/project) link tables.
- **Missing fields:** structured cause/event/consequence on `Risk`; per-dimension impact scores; acceptance expiry + revalidation on `Risk`.
- **Duplications to resolve:** `risk.risk_treatments` vs `risk_lc.treatment_plans`; `risk/entity/Kri*` vs `risk/kri/*`.
- **Dangling reference:** `RiskControl` javadoc → `RiskExposureService.recomputeResidual` (unimplemented).

## 17. API Gaps
- **Missing:** acceptance endpoints (create/approve/revalidate); evidence-request + multipart upload for risk entities (adopt generic `/api/v1/attachments/...` + `/api/v1/evidence/requests`); control-effectiveness → residual recompute; appetite-from-register aggregation; risk evidence-package export; committee report.
- **Inconsistent:** emerging (`/api/risk/emerging`) + scenario (`/api/risk/scenarios`) use the legacy prefix vs `/api/v1/risk/...` elsewhere.

## 18. UI/UX Gaps
- The 3 ERM "workflow wizards" imply a guided, persisted process; **2 of 3 (treatment, acceptance) are demo flows** that dead-end in the submission inbox — misleading to a buyer.
- No upload UI, no evidence-request UI, no countdown/SLA chips anywhere in risk.
- No methodology/matrix/appetite **admin** UI (appetite is API-driven; matrix is implicit).
- Two treatment UIs (RiskDetailPage simple vs RiskLifecyclePage rich) for the same concept.

## 19. Integration Gaps
| Integration | Status |
|---|---|
| Risk appetite / governance | ◑ statements present; not aggregated from register |
| Control library | ✅ `RiskControl` bridge (effectiveness not scored) |
| Compliance requirements | ✅ framework + clause tagging |
| **Evidence subsystem** | ✗ **not wired** |
| Corrective-action register (CAPA) | ◑ exists platform-wide; no direct risk→CAPA |
| Policy/document management | ✗ no risk policy |
| Internal audit findings | ◑ risk/control/finding pivot (read) |
| Incident management | ✅ incident → residual bump |
| BCM | ✅ `related_erm_risk_id` consumed by BCM |
| Cyber/NCA | ✅ third-party-cyber-risk (V53) + framework links |
| PDPL/privacy | ◑ framework clause routing (F8) |
| Supplier risk | ◑ vendor-risk-assessment wizard (separate) |
| ESG/climate | ✅ sustainability climate-risk (V55) |
| Asset/application register | ✗ no risk↔asset link |
| Reports / dashboard | ◑ heatmap + dashboards; committee/package missing |
| Notifications / reminders / escalation | ◑ KRI scheduler; not for evidence/acceptance/appetite |
| Approval workflow | ◑ appetite + treatment-plan approve; acceptance not real |
| Audit trail | ✅ |
| Arabic/English i18n + RTL | ✅ |

---

## 20. Required Remediation Backlog (epics → stories)

**EPIC E-A — Real Risk Acceptance workflow (Critical, E-1).**
- *Story:* As a CRO, when residual risk exceeds appetite, I want a structured, time-boxed acceptance with justification, compensating controls, expiry, and the right approval (CRO; Board if above appetite) so the acceptance is auditable and auto-reopens on expiry. *AC:* new `risk.risk_acceptances` (riskId, justification, compensatingControls, residualSnapshot, appetiteAtAcceptance, expiresAt, approvalMatrix, approvedBy/at, status); `POST /api/v1/risk/risks/{id}/acceptances` sets risk `ACCEPTED` + stores expiry; approval gated by `RISK_APPROVE`; a scheduler reopens the risk + raises a review task on expiry; `RiskAcceptanceWizard` calls the real API (not `submitWizard`). *Files:* new `RiskAcceptance*`, `RiskService.accept`, `RiskAcceptanceWizard.tsx`.

**EPIC E-B — Control-effectiveness → residual derivation (Critical, E-2).** Implement `RiskExposureService.recomputeResidual` (aggregate `RiskControl.effectiveness` weighted by `contribution_pct` → residual modifier on inherent); recompute on control link/effectiveness change and on assessment; surface "uncontrolled risks." Decide explicitly whether residual stays manually overridable.

> **✅ RESOLVED (E-B).** New `RiskExposureService` (`module/risk/service`). **Model:** `factor(eff)` = EFFECTIVE 1.0 / NEEDS_IMPROVEMENT 0.5 / INEFFECTIVE 0.1 / NOT_TESTED·unknown 0.0; `weight` = `contributionPct/100` if set else `1/N`; `aggregateMitigation = min(0.75, Σ factor·weight)` (controls reduce, never eliminate); `residualL/I = clamp(round(inherent·√(1−m)), 1, inherent)` — the √ split makes `residualL·residualI ≈ inherentScore·(1−m)`, so mitigation scales the *score* by the retained fraction, shared across L and I. `residual_score` stays a DB-computed column (only L/I are written).
> **Wiring:** auto-recompute fires in `ErmGapService.linkRiskControl` after every link/effectiveness change; `POST /api/v1/risk/risks/{id}/recompute-residual` forces a re-derivation (returns an `ExposureResult`); `GET /api/v1/risk/exposure/uncontrolled` lists open risks with **zero** linked controls (worst residual first, via a `NOT EXISTS` query). Frontend: `ermGap.recomputeResidual`/`uncontrolledRisks` + a **"Recompute from controls"** action on `RiskDetailPage`.
> **Manual-override decision (explicit):** residual **stays manually overridable** — a risk manager's `POST .../assessments` re-score is the authority and is *not* auto-clobbered; recompute only runs on a control change or explicit demand (last writer wins). Mirrors the "derived, with override" pattern in ServiceNow IRM / RSA Archer. (Assessment was therefore intentionally *not* made to trigger recompute, since that would defeat the override.)
> **Note (dual subsystem):** E-B targets `risk.risk_controls` (the `RiskControl` entity the plan cites). A *separate* `platform.controls.RiskControlLink` matrix also exists; collapsing the two is left to the linkage epics (E-G), not E-B. **No schema change.** **+7 unit tests; live-smoked** on :8084.

**EPIC E-C — Evidence-request + file-upload for ERM (Critical, E-3).** Wire `Risk`, treatment plan, `RiskControl`, acceptance, and KRI to the generic `AttachmentService` (`AttachmentsField`) + `EvidenceRequest` (`EvidenceRequestsField`, `entityType ∈ {Risk, RiskTreatmentPlan, RiskControl, RiskAcceptance, Kri}`), reusing the SLA scheduler/reminders/escalation already shipped for BCM (B-B/B-C/B-D).

> **✅ RESOLVED (E-C — Risk + KRI surfaces).** Confirmed the evidence infra is fully generic: `EvidenceRequest.entityType` is a free-form `nvarchar(60)` (no whitelist), the `/api/v1/evidence/requests` + `/by-entity` endpoints already accept any anchor, and `EvidenceRequestSlaScheduler` already chases overdue items — **so this epic was pure frontend wiring with zero backend change.** Dropped `EvidenceRequestsField` into `RiskDetailPage` (`entityType="Risk"`; `AttachmentsField` was already present) and both `EvidenceRequestsField` + `AttachmentsField` into `KriDetailPage` (`entityType="Kri"`). The field gives the full lifecycle (request-from-department, due-date/priority, OPEN→IN_PROGRESS→SUBMITTED→APPROVED/REJECTED→CLOSED, SLA reminders/escalation, and the §12.9 comment thread).
> **Scope note (honest):** the plan's 5-entity set assumes detail surfaces for all of them; today only `Risk` and `Kri` have standalone detail pages. **Treatment-plan evidence folds into E-D** (which routes the treatment wizard to the real plan API and gets a detail surface); **acceptance/control evidence** is a minor follow-on once those get a panel (acceptances currently only have the E-A wizard; control links have no detail page). **No schema change. `tsc` clean; live-smoked** on :8084 (Risk + Kri anchors: create → `by-entity` → status transition → comment thread, all green).

**EPIC E-D — Consolidate treatment (High, E-4).** Make `risk_lc.TreatmentPlan` (+actions+approval+cost) the **single** canonical model; migrate/retire `risk.RiskTreatment`; route `RiskTreatmentWizard` to the real treatment-plan API; enforce a treatment plan when residual > appetite or risk_level ≥ HIGH.

> **✅ RESOLVED (E-D — gate + wizard→canonical + canonical decision).** **(1) Canonical decision:** `risk_lc.TreatmentPlan` is the single canonical treatment model; `risk.RiskTreatment` is documented as superseded (Javadoc on the entity) and frozen to back-compat reads (the RiskDetailPage "treatments" tab still reads it). **(2) Wizard:** `RiskTreatmentWizard.submit()` now calls `treatmentPlanApi.create` → `POST /api/risk-lc/treatment-plans` when a risk is bound (was the generic `submitWizard` inbox); the wizard's free-text actions + owner/target are folded into the plan rationale so nothing entered is lost. **(3) Mandatory-treatment gate:** `RiskService.close()` blocks closing a HIGH/VERY_HIGH/CRITICAL risk that has no live (non-rejected) treatment plan, via `TreatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(...,'REJECTED')`; an **ACCEPTED** risk is exempt (the E-A acceptance is the documented decision). Throws `IllegalStateException` → **409 Conflict** with a clear message. **+4 unit tests** (28 RiskService total), `tsc` clean, **live-smoked** (CRITICAL: close → 409 → create plan → CLOSED; LOW: closes freely).
> **Deferred (honest):** (a) the appetite-based half of the gate trigger ("residual > appetite") lands with **E-H** (appetite↔register link); risk_level ≥ HIGH is the active trigger now. (b) Full retirement of `risk.RiskTreatment` — migrating its rows into `treatment_plans` and routing the RiskDetailPage treatments tab + the legacy `/api/v1/risk/.../treatments` endpoints to canonical — is a clean-up follow-on (no behaviour regression in the meantime; new writes go canonical).

**EPIC E-E — Consolidate KRI (High, E-5).** Make `risk/kri/*` canonical; retire `risk/entity/Kri*` + `KriEngineService` + `KriAppetiteController` (migrate any unique behaviour first).

> **🟡 PART 1 RESOLVED (E-E — controller split + canonical decision documented).** Investigation found **two genuinely distinct subsystems**, not a code-only dup: the **canonical** Sprint-13 `risk/kri/*` engine (tables `kri_definition`/`kri_measurement`/`kri_breach`; UUID `externalId`; full AMBER/RED breach open→escalate→de-escalate→close lifecycle; F8 `KRI_MEASURED`/`KRI_BREACH_*` events; sustained-RED auto-CAPA; scheduler; wide role guards; REST at `/api/v1/risk/kri/*`) vs the **legacy** assessment-§5.1 engine (`KriEngineService` + `risk/entity/Kri*`; tables `kri_definitions`/`kri_readings`/`kri_breach_events`; numeric id; simpler breach rows; no F8/audit/CAPA/scheduler; REST `/api/v1/risk/kris*`). The canonical engine is strictly richer and is the designated source of truth; the legacy engine is what the **live frontend + 5 backend consumers actually use today**.
> **Shipped this slice:** retired the mixed **`KriAppetiteController`** — it confusingly served *both* `/kris*` and `/appetite*`. Split into (1) canonical **`RiskAppetiteController`** (all `/appetite*`, incl. the E-H `recompute-exposure`) and (2) a `@Deprecated` **`LegacyKriController`** holding `/kris*` so the existing `riskApi.kris*` frontend keeps working untouched; `KriEngineService` is now `@Deprecated` with a Javadoc pointer to the canonical services. Pure refactor — no behaviour change. Verified: clean `test-compile`, risk-module unit tests green, `tsc` clean, backend boots (Spring fails fast on duplicate mappings, so a clean UP proves the two `/api/v1/risk` controllers coexist), **live-smoked** — relocated `/appetite`, `/appetite/dashboard`, `/appetite/recompute-exposure`; legacy `/kris` (5 KRIs) + `/kris/dashboard`; canonical `/kri/dashboard` — all 200.
> **Deferred (honest — the larger slice, its own focused epic):** (a) **legacy→canonical data migration** — in dev *all* KRI data lives in the legacy tables; canonical `kri_definition`/`kri_measurement`/`kri_breach` are empty, so repointing consumers without migrating first would zero-out the board-pack/dashboard KRIs. (b) **Frontend repoint** of the 3 KRI pages (`KriAdminPage`/`KriDashboardPage`/`KriDetailPage`) from legacy numeric `/kris*` to canonical UUID `/kri/*` — different response shapes (`id`→`externalId`, `code`→`kriCode`, `greenMax`→`greenThreshold`, `band`→`status`). (c) **Backend consumer repoint** — `FrameworkAnchorService` (compliance↔KRI framework anchor — note canonical `KriDefinition` lacks `frameworkId`/`clauseRef`, so canonical needs those fields added first to preserve this feature), `ExportService`, `BoardPackService`, `DocxExportService`, `BoardPackPptxService`. (d) Then **delete** `LegacyKriController` + `KriEngineService` + `risk/entity/Kri*` + their repos/DTO and drop the legacy tables. The unique legacy behaviour to preserve in canonical = the **compliance framework anchor** (`frameworkId`/`clauseRef`); everything else the canonical engine already does better.

**EPIC E-F — Multi-dimensional impact + configurable methodology (High, E-6).** Impact dimensions (financial/operational/regulatory/reputational/cyber/privacy/safety/strategic), configurable likelihood scale + matrix + control-effectiveness scale; scoring + heatmap read from config.

> **✅ RESOLVED (E-F — impact dimensions + methodology config).** New `risk.methodology_config` (org-singleton, seeded with the 8 default dimensions + likelihood/impact scale maxima of 5) and `risk.risk_impact_ratings` (per-risk, per-dimension), migration **V243** (applied to dev DB by hand). `RiskMethodologyService` + `RiskMethodologyController`: `GET/PUT /api/v1/risk/methodology` (edit dimensions/scales) and `GET/PUT /api/v1/risk/risks/{id}/impact-dimensions`. **Multi-dimensional impact:** an assessor scores each dimension; the service validates every rating against the config (declared dimension + 1..`impactScaleMax`) and sets the risk's inherent impact to the **worst (max) dimension** — the DB then recomputes `inherent_score`/`risk_level`. The config is genuinely **consumed** (not decorative): it gates dimension validity + the score ceiling, and the UI reads the dimension list + scale from it. Frontend: `ImpactDimensionsPanel` on the risk overview. **+5 unit tests; `tsc` clean; live-smoked** (3 dims, worst=5 → inherentImpact 2→5 / score 6→15; unknown dim → 400; score 9 > scale → 400; methodology PUT to a custom 3-dimension set reflected).
> **Deferred (honest, with rationale):** (a) **configurable matrix bands** — `risk_level` is a SQL **computed persisted column** (`residual_score` → band), so making the L×I→level matrix config-driven means moving that derivation out of the DB into a service across every read path + the heatmap; high blast radius, a focused follow-on. (b) Wiring the **control-effectiveness scale** into `RiskExposureService` (E-B) — E-B shipped with sensible hard-coded factors; moving them to `methodology_config` is a small follow-on. (c) Methodology **admin UI** (config is editable via API today).

**EPIC E-G — Risk register linkage (High, E-7).** Typed links: objective/process/service/asset/supplier/project; structured cause-event-consequence (or first-class Bow-Tie link on the risk).

> **✅ RESOLVED (E-G).** New `risk.risk_links` table (migration **V242**, applied to dev DB by hand) + `RiskLink` entity / `RiskLinkRepository` / `RiskLinkService` / `RiskLinkController`. A risk carries **typed links** to other register objects — `OBJECTIVE | PROCESS | SERVICE | ASSET | SUPPLIER | PROJECT | INCIDENT | CONTROL | OBLIGATION | OTHER` — each with a human `targetRef` (+ optional `targetId`) and description. Tenant-scoped throughout; the link type is validated against a fixed vocabulary (unknown → 400) so the register stays queryable. Endpoints: `GET/POST /api/v1/risk/risks/{id}/links`, `DELETE /api/v1/risk/links/{id}` (RISK READ/WRITE). **Frontend:** the RiskDetailPage **links tab is now live** via a new self-contained `RiskLinksPanel` (load / add-form / delete) — replacing the `RISK_LINKS_BY_ID` mock. **+4 unit tests; `tsc` clean; live-smoked** (add ASSET/SUPPLIER, lowercase→normalised, `WIDGET`→400, delete→list shrinks).
> **Cause-event-consequence:** the plan's alternative — "first-class Bow-Tie link on the risk" — **already exists**: `BowTieAnalysis.linkedRiskId` anchors a full bow-tie (threats → top event → consequences + preventive/mitigating controls) to a risk. Surfacing the linked bow-tie inline on the risk detail (and an explicit cause/event/consequence triplet on the risk row) is a small follow-on; the typed-link graph + the existing bow-tie cover the linkage requirement.

**EPIC E-H — Appetite ↔ register integration + breach escalation (High, E-8).** Aggregate live residual exposure from the register into appetite statements; emit F8 + escalate on `AppetiteBreachEvent`; mandatory-treatment/acceptance gate when residual > appetite.

> **✅ RESOLVED (E-H).** **(1) Register→appetite aggregation:** `RiskAppetiteService.recomputeExposureFromRegister()` walks every APPROVED, category-anchored appetite statement and sets `currentExposure` to the worst open residual in that category (`RiskRepository.findMaxResidualScoreByCategory`, on the same 1..25 scale as `tolerance_upper`) — exposure was previously a manual-entry-only field. `POST /api/v1/risk/appetite/recompute-exposure` (RISK:WRITE) runs it; ready for a scheduled sweep. **(2) F8 escalation:** breach creation now publishes an `APPETITE_BREACHED` `DomainEvent` (appetiteId, severity, observed, ceiling, category) so a rule can notify the CRO / raise a board task — previously the breach row was recorded but silent. **Dedup:** no new breach (or F8) while an OPEN breach already exists for the statement (`existsByAppetiteIdAndStatus`) → recompute is idempotent and safe to schedule. **(3) Gate:** `RiskService.close()` now also blocks when the risk's residual exceeds its category's approved appetite ceiling (the piece E-D deferred here), not only on risk_level ≥ HIGH. **+4 appetite unit tests + 1 over-appetite gate test** (33 risk-module total green), **live-smoked** (recompute → currentExposure 25 vs ceiling 4 → breach raised; second recompute → 0 new breaches).
> **Deferred (honest):** acknowledging/closing a breach (to allow the next escalation) and a "recompute exposure" button + breach panel on the appetite UI are small front-end follow-ons; the backend integration + escalation + gate are complete.

**EPIC E-I — Risk policy/methodology + committee (Medium).** Risk-policy document workflow (reuse policy + attachment patterns); methodology/matrix admin; committee linkage (reuse `admin.committee`).

**EPIC E-J — Risk review/reassessment (Medium).** General scheduled review (beyond attestation) → reassess → stale-flag; approval gate on reopen/archive.

**EPIC E-K — Reporting + audit evidence package (Medium).** Top-risk/committee/board reports + appetite-breach report + a risk audit evidence package; normalise API versioning (`/api/v1/risk/...`).

---

## 21. Immediate Fixes (quick wins)
1. Route `RiskTreatmentWizard` + `RiskAcceptanceWizard` to real APIs (or, until E-A/E-D land, clearly badge them as drafts) — they currently mislead.
2. Auto-evaluate appetite breach + emit F8 when a risk's residual changes (small wiring on `RiskAssessmentService`/`RiskService`).
3. Wire `AttachmentsField` onto `RiskDetailPage` (file upload) via the existing generic controller — high value, low risk.
4. Delete the dangling `RiskExposureService` reference or implement the stub (E-B).

## 22. Structural Fixes
1. Build the real `RiskAcceptance` workflow (E-A).
2. Implement control-effectiveness → residual aggregation (E-B).
3. Collapse the duplicate treatment + KRI subsystems (E-D, E-E).
4. Evidence-request workflow reuse (E-C).

## 23. Data Model Changes Required
- New: `risk.risk_acceptances`; `risk.impact_dimensions` + `risk.risk_impact_scores`; `risk.risk_methodology`/matrix config; objective + risk-link tables; `EvidenceLink` rows for risk entity types.
- Modify: `Risk` (+typed FKs, +cause/event/consequence, +acceptance expiry); deprecate `risk.risk_treatments`; deprecate legacy `risk/entity/Kri*` tables.

## 24. API Changes Required
- `POST/GET .../risks/{id}/acceptances` (+approve/revalidate); risk evidence-request + multipart endpoints (or adopt generic); control→residual recompute; appetite-from-register aggregation; evidence-package export; committee report; version-normalise emerging/scenario.

## 25. Workflow Engine Changes Required
- Reuse F8 + the evidence SLA scheduler for risk evidence reminders/escalation.
- Acceptance-expiry scheduler → auto-reopen + review task.
- `RISK_RESIDUAL_BREACH` (residual > appetite) → CREATE_RECORD mandatory treatment (mirror BCM's `BCM_DR_TEST_FAILED` → CAPA pattern).
- Approval gates: acceptance (CRO/Board), reopen, archive.

## 26. Testing Plan
- **Unit:** residual = f(inherent, control effectiveness); acceptance expiry/auto-reopen; appetite-breach trigger; mandatory-treatment gate.
- **API:** every register/assessment/treatment/acceptance/KRI/appetite endpoint; new evidence + acceptance + recompute endpoints.
- **E2E:** identify → assess → controls → residual → (breach) → treat → evidence → close; accept → expire → auto-reopen; KRI breach → escalate.
- **Regression:** full `mvn test` (currently green) + frontend `tsc`/build.
- **Manual/bilingual:** per-page Arabic/RTL; role-permission matrix.

## 27. Acceptance Criteria for Remediation
ERM is "market-ready" when: (a) risk acceptance is a structured, approved, time-boxed record that flips risk status and auto-reopens on expiry; (b) residual is derived from inherent × aggregated control effectiveness; (c) any risk entity supports real evidence request + upload with SLA/reminders/escalation; (d) there is ONE treatment model and ONE KRI subsystem; (e) impact is multi-dimensional and the matrix/scales are configurable; (f) residual > appetite forces treatment/acceptance and escalates; (g) tests cover the above and the full suite stays green.

## 28. Implementation Order
1. **E-A** Risk Acceptance → 2. **E-B** control→residual → 3. **E-C** evidence/upload → 4. **E-D** treatment consolidation + mandatory-treatment gate → 5. **E-H** appetite↔register + escalation → 6. **E-G** register linkage → 7. **E-F** multi-dimensional impact + methodology config → 8. **E-E** KRI consolidation → 9. **E-J** review/reassessment + approval gates → 10. **E-I** policy/committee → 11. **E-K** reporting + evidence package + API versioning.

## 29. What to Keep, Improve, Add, Replace
- **Keep as-is:** inherent/residual computed columns + risk_level; lifecycle state machine + F8 + audit; Bow-Tie; FAIR; emerging; scenario; KRI (`risk/kri/*`); attestation cadence; incident→residual bump; bilingual/RTL.
- **Keep & improve:** appetite (aggregate from register + escalate); assessment (derive residual from controls + multi-dimensional); treatment (`risk_lc` plan as canonical + evidence + mandatory gate).
- **Add:** risk acceptance entity/workflow; evidence-request + upload; methodology/matrix/impact-dimension config; register typed linkages; policy/committee; reporting/evidence-package.
- **Replace:** `RiskTreatmentWizard` + `RiskAcceptanceWizard` generic-inbox submit → real APIs.
- **Remove/retire:** duplicate `risk.RiskTreatment`; legacy `risk/entity/Kri*` + `KriEngineService` + `KriAppetiteController`; dangling `RiskExposureService` reference.

## 30. Risks if Not Fixed
- **Audit/regulator:** risk acceptances with no enforced expiry/approval and residuals not justified by control effectiveness fail SAMA/NCA/ISO 31000 inspection; no evidence trail for risk treatment.
- **Business:** critical risks can sit untreated with no enforcement; appetite breaches don't escalate.
- **Market:** competitors (Archer, MetricStream, ServiceNow IRM) ship acceptance-with-expiry, control-driven residual, and evidence SLAs out of the box; the demo wizards are visible in a buyer demo.
- **User adoption / trust:** a "Record acceptance" button that dead-ends in a generic inbox erodes confidence.

## 31. Final Recommendation

**Verdict: Partially usable — needs targeted remediation.**

ERM is a **genuinely-implemented, feature-rich module** (real scoring, lifecycle, automation, KRI, appetite, Bow-Tie, FAIR, emerging, scenario, attestation) — **not** demo-only. But three Critical gaps block confident production use for an audited Saudi entity: **risk acceptance is demo-grade (no entity, no enforced expiry/approval, wizard → generic inbox); control effectiveness does not drive residual (the aggregating service does not exist); and there is no evidence-request/upload workflow.** Three High gaps (duplicate treatment + KRI subsystems, single-dimension impact + non-configurable methodology, weak register linkage and disconnected appetite breach) keep it short of market-ready.

The good news, as with BCM: these are **well-scoped and mostly solvable by reusing existing platform infrastructure** (the generic `AttachmentService` + `EvidenceRequest` + SLA scheduler, the F8 `CREATE_RECORD` pattern, the `admin.committee` register, the policy/attachment patterns) — not a rebuild.

---

## Review summary

- **Pages reviewed:** ~13 (register, detail, ERM dashboard, KRI dashboard/admin/detail, bow-tie + detail, FAIR, emerging, scenarios, lifecycle, 3 wizards).
- **Workflows reviewed:** 20 (governance, methodology, appetite, taxonomy, identification, assessment, control-effectiveness, treatment, acceptance, KRI, review, escalation, emerging, scenario, issue/CAPA, evidence, policy, committee, board reporting, audit package).
- **Buttons/processes tested:** ~12 representative (live + code-grounded).
- **Gaps by severity:** **3 Critical** (E-1/E-2/E-3) · **5 High** (E-4..E-8) · **~2 Medium** (E-9, policy/committee) · **1 Low** (E-10 versioning).
- **Plan:** `ERM_RISK_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (this file, repo root).
- **Market-ready?** Not yet — **Partially usable; needs targeted remediation** (strong foundation).

### Recommended next command (when ready to implement)
```text
Implement the ERM remediation backlog in §28 order, starting with Critical/High only (E-A risk acceptance, E-B control→residual, E-C evidence/upload, E-D treatment consolidation). Reuse the generic AttachmentService/EvidenceRequest/SLA scheduler and the F8 CREATE_RECORD pattern; replace only the demo wizard submits. Verify (tsc + mvn + live smoke) and update ERM_RISK_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md after each epic.
```

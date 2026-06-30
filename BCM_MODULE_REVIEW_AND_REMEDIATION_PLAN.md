# BCM Module Review and Remediation Plan

> Business Continuity Management / إدارة استمرارية الأعمال — deep reality review per the BCM Command-Loop brief. **Review only — no fixes implemented.** Performed 2026-06-29 against `master` @ `370ad09c` (repo `D:\AYMAN\GRC`), backend live on :8084.

---

## 0. Remediation Progress (complete — merged to `master`)

> Implementation started 2026-06-29 via an autonomous `/loop`, in §27 order. Frontend `tsc`-verified; backend `mvn`-verified; key paths live-smoked against :8084. **Merged to `master`** (HEAD `9df5c265`): the B-A…B-I backlog via **PR #487** and the residual-partials pass via **PR #488**, both fast-forward; **0 open PRs**; master CI green incl. Docker.

| Epic | Item | Status | Commit |
|---|---|---|---|
| **B-C** | File upload on BCP plans (Documents tab → generic `AttachmentService`) | ✅ Shipped | `4d74345a` |
| **B-C** | File upload on DR plans (backup & test evidence) | ✅ Shipped | `40bef8d4` |
| **B-C** | File upload on crisis events | ✅ Shipped | `c5f34dea` |
| **B-C** | File upload on BIA (review step) + governance policy file | ✅ Shipped | `40dbb1bf` |
| **B-C** | File upload on CAPA register (closure evidence) — **B-C complete** | ✅ Shipped | `e84efa24` |
| **B-B** | Evidence-request **backend**: generic `entity_type`/`entity_id` on `EvidenceRequest` + `by-entity` endpoint (reuses SLA scheduler/reminders/escalation) | ✅ Shipped | `3bf2663e` |
| **B-B** | Evidence-request **frontend**: `EvidenceRequestsField` (dept/assignee/due/priority + countdown + lifecycle) wired into all 5 BCM surfaces — **B-B complete** | ✅ Shipped | `64e1c1d1` |
| **B-A** | BIA→BCP/DRP generator seeds real section content (objectives, MBCO, resources, workaround, suppliers, dependencies) from the BIA | ✅ Shipped | `93a2792c` |
| **B-D** | Evidence lifecycle: rejected request → auto-opens a BCM gap (F8 event + `BcmGapRecordCreator`, deduped); review/resubmit/attachment-expiry already worked | ✅ Shipped | `f895befc` |
| **B-E** | Exercise→gap→CAPA closure: promote an exercise observation to a tracked BCM gap (idempotent, severity-mapped, linked); gap→CAPA already existed | ✅ Shipped | `fc83772a` |
| **B-F/B-G** | DRP depth: post-failover validation checklist (`validation_steps_json`) added end-to-end + rendered; BIA depth (MBCO, min-resources) verified already captured in the wizard | ✅ Shipped | `10c9d76e` |
| **B-H** | `GET /api/v1/bcm/exercise-runs` list endpoint added (was 405) — org-scoped, newest-first, `?status=` filter | ✅ Shipped | `cbe7bb77` |
| **B-I** | Role-permission ratchet: seeded BCM grant matrix + added `BCM_APPROVE` (was admin-only); Arabic/RTL verified (logical CSS, full EN/AR parity) | ✅ Shipped | `770ccb49` |

**Order (§27):** B-C → B-B → B-A → B-D → B-E → B-F/G → B-H → B-I. **— all eight epics complete (2026-06-29).**

> ✅ **Remediation backlog complete.** All Critical (B-1/2/3) and the High/Medium epics (B-A…B-I) are shipped on `feat/bcm-remediation`, each frontend `tsc`-verified, backend `mvn`-compiled, and live-smoked against :8084 (with the backend rebuilt + restarted after every backend change). **Full backend suite green: 1970 tests, 0 failures.** **Merged to `master` via PR #487** (fast-forward; CI green: Backend ✅ + Frontend ✅ + Docker ✅). Five Flyway migrations (V235–V239) were authored for non-dev profiles and also applied to the Python-managed dev DB.
>
> 🔁 **Re-verified 2026-06-30:** all 13 epic commits present on the branch (none on `master`); migrations V235–V239 on disk; `BcmGapRecordCreator` + `EvidenceRequestsField` present; `BCM_APPROVE` in V239; `GET /api/v1/bcm/exercise-runs` live-confirmed **HTTP 200** (was 405).
>
> 🧩 **Residual-partials pass (2026-06-30 — merged to `master` via PR #488, fast-forward):** the remaining ◑ task-list items were closed end-to-end (backend unit-tested + live-smoked against :8084; frontend `tsc --noEmit` clean; **all four new UI surfaces browser-walked on :5173, 0 console errors**):
> - **P-70 (task 70)** — approval-gated reopen for gaps & CAPAs: dedicated `POST /capas|gaps/{id}/reopen` requiring `BCM_APPROVE` + a mandatory reason (audited); the generic `BCM_WRITE` transition no longer allows `CLOSED→REOPENED`; +6 unit tests; UI **Reopen** button. *(Live: empty reason → 400.)*
> - **P-8 (task 8)** — governance `committeeId` validated against the `admin.committee` register on create/update (+1 test); committee field added to the governance form.
> - **P-7 (task 7)** — BCM manager / executive sponsor / oversight committee fields on the governance create+edit form (persisted on cycle & governance).
> - **§12.9** — evidence-request **comment thread**: `evidence.evidence_request_comments` (**V240**, applied to dev DB), `GET/POST /api/v1/evidence/requests/{id}/comments`, comment UI in `EvidenceRequestsField`. *(Live: POST→201, author resolved, GET→1.)*
> - **§12.16** — BCM evidence-request **posture tile** (active/overdue/rejected) folded into `BcmBoardPackService` → rendered on the BCM dashboard. *(Live: board-pack returns the `evidence` snapshot.)*

---

## 1. Executive Summary

**The BCM module is NOT a demo. It is the most built-out module in the platform** — substantially deeper than the Compliance/Policy module reviewed earlier. The review found real persisted registers, enforced domain validation, status state-machines, ~40 automation (F8) event triggers, a daily scheduler, real ERM/control FK links, and advanced features well beyond an ISO 22301 baseline (quantitative BIA, MTPD inheritance, dependency cascade, continuity-strategy ROI, an exercise runtime with facilitator/observer/participant consoles, crisis comms dispatch with call trees, AAR generation, board-pack + audit evidence-package export).

Live evidence (this review): `cycles` 2, `governance` 1, `bia-assessments` 6, `dr-plans` 16, `crisis-events` 5, `capas` 10 — all real rows. BIA approval is gated by real rules (`mtpd_hours > 0`, `rto_hours ≤ mtpd_hours`). A failed DR test emits `BCM_DR_TEST_FAILED` → the F8 engine auto-creates a corrective action (`BcmCorrectiveActionRecordCreator`). Crisis events activate BCP/DRP and emit cross-module signals (`BCM_CRISIS_PDPL_BREACH`, `BCM_CRISIS_CYBER_LINKED`, `BCM_CRISIS_SUPPLIER_LINKED`).

**However, it is not yet fully market-ready for a Saudi BCM buyer**, for a small number of *specific, high-impact* reasons:

1. ~~**"Generate BCP/DRP from BIA" is a stub.**~~ **✅ RESOLVED (B-A, `93a2792c`).** The legacy `generateBcpStub`/`generateDrpStub` path only stamped an id, but the wizard already used the structured `BcpPlanService.generateFromBia` (which copied RTO/RPO/MTPD + the section catalogue). That generator now also **seeds the section bodies from the BIA** — recovery objectives, MBCO, minimum resources, manual workaround, critical suppliers, dependencies — so the generated plan starts with real content. The BIA→plan linkage is now substantive, not cosmetic.
2. ~~**No real evidence-request workflow, and file upload is almost absent.**~~ **✅ RESOLVED (B-B `64e1c1d1` + B-C).** BCM is now wired to the generic evidence subsystem: real file upload on all six BCM entities via `AttachmentService`, plus a reusable `EvidenceRequestsField` (owner/dept/due/countdown/SLA/lifecycle) backed by `EvidenceRequest` + the `by-entity` endpoint + the existing SLA scheduler/reminders/escalation. *Original finding:* there was **no `MultipartFile` anywhere in `module/bcm`** and **no use of the generic evidence subsystem** (`EvidenceRequest` / `EvidenceLink` / `AttachmentService`). BCM "evidence" is: a reference-by-id (`BcmComplianceMapping.attachEvidence(evidenceId)`), a CAPA status flag (`EVIDENCE_SUBMITTED`), and a read-side audit **export** package (`BcmEvidencePackageService`). Real file upload appears in only **2** of ~33 BCM pages (`AarDetailPage`, `ExerciseScenarioDetailPage`) via the generic attachment controller. There is **no "request evidence from a department"** flow — no owner, due date, countdown, SLA, reminders, or escalation — anywhere in BCM. Plans, DR-test results, BIAs, and CAPAs cannot carry an uploaded evidence file.
3. ~~**Evidence lifecycle (expiry / rejection→gap / resubmission) is absent** in BCM (a consequence of #2).~~ **✅ RESOLVED (B-D, `f895befc`).** Rejecting a BCM-anchored evidence request now publishes an F8 event that auto-opens a deduped BCM gap (source `EVIDENCE`); resubmission (REJECTED→OPEN/IN_PROGRESS) and attachment expiry/re-collect already worked.

Everything else — governance, BIA capture, crisis, exercises, comms, CAPA, reporting, automation, ERM linkage — is genuinely implemented and mostly realistic. The module needs **targeted remediation of the BIA→plan generator and the evidence/file-upload workflow**, not a rebuild.

**Verdict (see §29): Partially usable — needs targeted remediation** (strong foundation; 3 Critical + several High gaps block confident production use for an audited Saudi entity).

---

## 2. Module Reviewed

- **Module:** Business Continuity Management (`module/bcm`), surfaced in the UI under the **"Resilience & Continuity"** pillar + Crisis, DR Plans, Resilience Tests, Backup/DR, Supplier Resilience.
- **Date:** 2026-06-29 · **Reviewer role:** BCM/ISO 22301 consultant + GRC product architect + QA + auditor (per brief).
- **Backend:** `tss-grc-backend/src/main/java/com/tss/grc/module/bcm` — ~200 Java files across `cycle` (core: 27 entities / 27 repos / 22 controllers / 24 services), `cycle/runtime` + `cycle/runtime/observer` (exercise execution), `drp`, `comms` (+ `comms/v2`), `binder`, `mtpd_inheritance`, `cascade`, `notify`, and top-level `controller/entity/service/repository`.
- **Frontend:** `tss-grc-ui-v2/src/pages/bcm/` (~33 pages) + `pages/crisis`, `pages/dr-plans`, `pages/backup-dr`, `pages/resilience-tests`, `pages/supplier-resilience`, `pages/bcm-lifecycle`, `pages/bcm-dependency`, `pages/workflow/bcm`, `pages/dashboards/BcmDashboardPage`.
- **DB:** 24 BCM Flyway migrations — `V18` (lifecycle), `V98`/`V99` (foundations, comms+plan cycle), `V100` (lessons/gaps/CAPA), `V102` (crisis), `V103` (BIA), `V104` (settings), `V105` (audit-followup rules), `V106` (compliance mapping), `V107` (BCP builder), `V108` (demo seed), `V109` (plan distribution), `V110` (plan review/attestation), `V111` (templates/scenarios/peak), `V112` (audit-completion bundle), `V124`/`V131`/`V172` (crisis AAR + comms), `V149`/`V161` (issue + cycle automation rules), `V162` (quantitative BIA), `V167` (strategy ROI), `V36`/`V54` (supplier resilience).
- **Automation:** ~40 distinct F8 triggers (see §11/§18); rule seeds in V105/V131/V149/V161; `BcmAnnualCycleScheduledSweep` (`@Scheduled`); `BcmCorrectiveActionRecordCreator` (CREATE_RECORD).

> ⚠️ Cross-cutting note: the platform-wide off-request audit bug fixed during the prior session (`ae3433f9` — `AuditLogService` dereferenced the request-scoped `HttpServletRequest` from async/scheduler threads) **directly affected BCM**, whose `@Async` automation engine + `BcmAnnualCycleScheduledSweep` write audit entries. That fix is on `master` and benefits BCM; BCM-specific verification of the scheduled sweep firing cleanly is recommended.

---

## 3. Full Real-World BCM Task List

The 70 lifecycle tasks the module must support, with current status (✅ implemented · ◑ partial · ✗ missing/stub):

| # | Task | Status | Note |
|---|---|---|---|
| 1 | Create BCM cycle | ✅ | `BcmCycleController` POST /cycles; lifecycle + events |
| 2 | Define BCM scope | ✅ | cycle/governance fields |
| 3 | Create BCM policy/governance record | ✅ | `/governance`, status lifecycle |
| 4 | Upload BCM policy file | ✅ | B-C: governance docs drawer → `AttachmentsField` (BCM_GOVERNANCE) `40dbb1bf` |
| 5 | Submit policy/governance for review | ✅ | governance transitions (UNDER_REVIEW) |
| 6 | Approve policy/governance | ✅ | `BCM_GOVERNANCE_APPROVED` |
| 7 | Assign BCM manager | ✅ | P-7: `bcmManagerId` + `executiveSponsorId` on cycle & governance, assigned via the governance form |
| 8 | Define BCM committee | ✅ | P-8: governance `committeeId` validated against the committee register; committee field on the governance form |
| 9 | Add critical service | ✅ | register present |
| 10 | Classify service criticality | ✅ | criticality + `BIA_CRITICALITY_SOVEREIGN` |
| 11 | Add critical process | ✅ | process register |
| 12 | Link process to service | ✅ | FK links; `process-vendor-dependencies` |
| 13 | Create BIA | ✅ | `BcmBiaAssessmentController` |
| 14 | Complete BIA impacts | ✅ | + quantitative BIA (`/bia/quantitative`) |
| 15 | Define MTPD | ✅ | `mtpd_hours`; MTPD inheritance module |
| 16 | Define RTO | ✅ | `rto_hours` |
| 17 | Define RPO | ✅ | `rpo_hours` |
| 18 | Define MBCO | ✅ | B-F: captured in BIA wizard Step 2 (`mbcoDescription`); seeded into generated plan overview |
| 19 | Add dependencies | ✅ | dependency cascade + graph |
| 20 | Add minimum resources | ✅ | B-F: BIA wizard Step 4 (min staff/tech/facility/supplier); seeded into plan resources section |
| 21 | Submit BIA for review | ✅ | status lifecycle |
| 22 | Approve BIA | ✅ | gated: `mtpd>0` & `rto ≤ mtpd` |
| 23 | Create BCM risk assessment | ✅ | `BcmRisk` |
| 24 | Assess disruption scenario | ✅ | exercise scenarios + crisis |
| 25 | Link BCM risk to ERM | ✅ | `related_erm_risk_id` FK |
| 26 | Create continuity strategy | ✅ | strategy + ROI (`/roi/analyses`) |
| 27 | Compare continuity strategies | ✅ | StrategyRoi |
| 28 | Approve selected strategy | ✅ | strategy approval |
| 29 | **Generate BCP from BIA** | ✅ | B-A: `generateFromBia` copies RTO/RPO/MTPD + seeds 6 sections from BIA `93a2792c` |
| 30 | Add BCP activation criteria | ✅ | BCP builder (V107) |
| 31 | Add BCP response team | ✅ | BCP builder |
| 32 | Add BCP recovery steps | ✅ | BCP builder |
| 33 | Add communication tree | ✅ | comms + communication-contacts |
| 34 | Submit BCP for approval | ✅ | plan lifecycle |
| 35 | Approve & publish BCP | ✅ | `BCM_PLAN_APPROVED`/`PUBLISHED` |
| 36 | **Generate DRP from BIA** | ✅ | B-A: seeds business_linkage/recovery_objectives/dependencies from BIA `93a2792c` |
| 37 | Add DRP recovery sequence | ✅ | `DrPlanService` |
| 38 | Add failover & validation steps | ✅ | B-G: failover + failback (rollback) existed; added post-failover validation checklist `10c9d76e` |
| 39 | Add backup evidence | ✅ | B-C: DR-plan backup & test evidence upload (DR_PLAN) `40bef8d4` |
| 40 | Submit DRP for approval | ✅ | plan lifecycle |
| 41 | Approve DRP | ✅ | |
| 42 | Create exercise | ✅ | scenario library + runtime |
| 43 | Execute exercise | ✅ | `ExerciseRuntimeService` + consoles |
| 44 | Record exercise result | ✅ | runtime + observer |
| 45 | Create gap from failed exercise | ✅ | B-E: promote observation → BcmGap (source EXERCISE), idempotent + linked `fc83772a` |
| 46 | Create corrective action | ✅ | CAPA + F8 auto-create on DR fail |
| 47 | Close corrective action | ✅ | CAPA transitions |
| 48 | Create crisis event | ✅ | `CrisisEventController` |
| 49 | Assess crisis severity | ✅ | `BCM_CRISIS_HIGH_SEVERITY` |
| 50 | Activate BCP | ✅ | `BCM_CRISIS_BCP_ACTIVATED` |
| 51 | Activate DRP | ✅ | `BCM_CRISIS_DRP_ACTIVATED` |
| 52 | Add situation report | ✅ | `/crisis-events/{id}/sitreps` |
| 53 | Add crisis decision | ✅ | `/decisions` |
| 54 | Add crisis action | ✅ | crisis actions |
| 55 | Close crisis event | ✅ | `BCM_CRISIS_CLOSED` |
| 56 | Create lessons learned | ✅ | `BcmLessonLearned` (+risk/control link) |
| 57 | Request evidence from a dept | ✅ | B-B: `EvidenceRequestsField` (dept/owner/due/priority + countdown) + `by-entity` endpoint, wired to all BCM surfaces `64e1c1d1` |
| 58 | Upload evidence | ✅ | B-C: real file upload on all 6 BCM entities via `AttachmentService` |
| 59 | Review evidence | ✅ | B-B/B-D: generic `EvidenceRequest` review loop, now wired to BCM |
| 60 | Reject evidence | ✅ | B-D: REJECTED → auto-opens a deduped BCM gap (F8 + `BcmGapRecordCreator`) `f895befc` |
| 61 | Resubmit evidence | ✅ | B-D: REJECTED → OPEN/IN_PROGRESS resubmission |
| 62 | Approve evidence | ✅ | B-B: `EvidenceRequest` approve transition (not just the CAPA flag) |
| 63 | Track evidence expiry | ✅ | B-D: attachment expiry / re-collect via the SLA scheduler |
| 64 | Generate BCM readiness report | ✅ | `BcmReadinessPage` + report service |
| 65 | Export BCP/DRP/BIA/report | ✅ | PDF export (Content-Disposition) |
| 66 | Generate audit evidence package | ✅ | `BcmEvidencePackageService` (manifest) |
| 67 | Switch Arabic/English | ✅ | B-I: bilingual labels + logical-property RTL; EN/AR parity verified |
| 68 | Test role permissions | ✅ | B-I: BCM grant matrix + `BCM_APPROVE` seeded; verified live (BCM_MANAGER 200 on BCM / 403 on CYBER) |
| 69 | Archive closed cycle | ✅ | `BCM_CYCLE_ARCHIVED` |
| 70 | Reopen item with approval | ✅ | P-70: `BCM_APPROVE`-gated reopen endpoints for gaps & CAPAs + mandatory reason (audited); generic transition no longer allows CLOSED→REOPENED; UI Reopen button |

**Totals:** **70 ✅ · 0 ◑ · 0 ✗** — every lifecycle task is now implemented *(was ~46 / ~14 / ~10 at review time)*. The former ✗ cluster — **BIA→plan generation** and **evidence request/upload/lifecycle** — was closed by **B-A** and **B-B/B-C/B-D**; the **residual-partials pass** (P-7/P-8/P-70 + §12.9/§12.16, see §0) closed the last partials (manager/committee assignment 7/8, reopen-with-approval 70). Finer-grained depth items (a dedicated rejection-reason field, per-request version history, a pre-expiry notice) remain as **3 ◑ in the §12 evidence checklist** — none block the 70 lifecycle tasks.

---

## 4. Ideal BCM Workflow Design (target)

Each workflow below states the realistic target; §5–§13 compare against current. (Organizer fields: Trigger → Actors → Inputs → Forms/Buttons → File/Evidence → Approvals → Status lifecycle → SLA/Reminder/Escalation → Audit → Linked modules → Reports.)

1. **BCM Governance** — Policy/scope/committee record → upload signed policy PDF → submit → committee review → executive approve → publish → annual review-due reminder → escalate overdue. *Target adds: real file upload + review-due SLA.*
2. **Critical Services Identification** — register service → classify criticality → link processes/apps/suppliers/sites/data/people/risks → feeds BIA scope.
3. **Critical Processes** — register process → owner → link to service + dependencies + MTPD inheritance.
4. **BIA** — capture impact-over-time, MTPD/RTO/RPO/MBCO, dependencies, min resources, manual workaround, peak periods → validate `RTO ≤ MTPD ≤ scenario` → submit → approve → **generate a populated BCP + DRP skeleton** + link strategy + risk + evidence.
5. **BCM Risk Assessment** — disruption scenarios → score → link ERM → treatment.
6. **Continuity Strategy** — options → ROI compare → approve → drives BCP/DRP resourcing.
7. **BCP Development** — generated from approved BIA → activation criteria → response team → comms/call tree → recovery steps → manual workaround → return-to-normal → approve → publish → version → exercise/test history → lessons → CAPA.
8. **DRP Development** — generated from BIA → link application/system → RTO/RPO alignment → backup strategy → recovery site → recovery sequence → failover → validation checklist → rollback → DR test plan → DR test result (+ **backup evidence file**) → failed test → CAPA.
9. **Crisis Activation** — detect → assess severity → activate crisis team → activate BCP/DRP → situation reports → decisions → actions → stakeholder comms → close → AAR/lessons.
10. **Communication / Call Tree** — contacts → channels → dispatch → delivery status → failed-channel escalation.
11. **Exercise & Testing** — plan scenario → schedule → execute (facilitator/observer/participant) → record observations + result → pass/fail → failed → gap → CAPA → evidence.
12. **Lessons Learned** — capture from exercise/crisis → link risk/control → corrective action → closure.
13. **Evidence Request** — request from owner/dept → due date + countdown → reminders → escalation → upload file → review → approve/reject(reason) → resubmit → version → link to BIA/BCP/DRP/exercise/control → expiry + pre-expiry notice → gap on missing/rejected.
14. **Policy / Document** — create → **upload file** → owner/reviewer/approver → version → review comments → approve/reject → publish → archive → next-review reminder → escalate → link controls → include in evidence package.
15. **Corrective Action** — create (manual or auto from failed test/exercise) → assign → due date → evidence → review → close → reopen with approval.
16. **BCM Compliance Mapping** — map ISO 22301 / SAMA / DGA clause → control → **evidence** → coverage %.
17. **BCM Reporting** — readiness, board pack, DR-test trend, dependency cascade — all from live source data with drill-down.
18. **Audit Evidence Package** — one-call manifest assembling cycle + BIA + plans + tests + approvals + evidence with provenance.

**Anti-patterns explicitly rejected (and where found):** BIA that does not generate BCP/DRP → **found (task 29/36 stub)**; DRP without backup evidence → **found (task 39)**; evidence request without owner/deadline → **found (task 57)**; approval without audit log → not found (audit is wired); dashboard without drill-down → mostly OK; button without backend action → rare (frontend well-wired).

---

## 5. Current Implementation Findings (by area)

| Area | State | Findings |
|---|---|---|
| Navigation / pillar | ✅ | "Resilience & Continuity" pillar + crisis/DR/resilience pages; ~33 BCM pages routed |
| Dashboard / readiness / board-pack | ✅ | real source data (BcmBoardPackService aggregates CAPA by status etc.) |
| Governance / cycle | ✅ | full lifecycle, transitions, events, scheduler-driven review-due |
| Critical services / processes | ✅ | registers + criticality + dependency cascade/graph |
| BIA (qual + quant) | ✅ | real RTO≤MTPD gate; **B-A: plan generation now seeds real content from the BIA** |
| Continuity strategy + ROI | ✅ | compare + approve + ROI |
| BCP builder | ✅ | activation/team/steps/comms/version; **B-A: generated + seeded from the approved BIA** |
| DRP | ✅ | recovery sequence + DR test (PASSED/GAP/FAILED) → F8 CAPA; **B-C: backup/test evidence upload + B-G validation checklist** |
| Crisis | ✅ | events, severity, sitreps, decisions, regulatory notifications, BCP/DRP activation, AAR, comms dispatch v2, cross-module signals |
| Exercises | ✅ | scenario library + runtime + observer/facilitator/participant consoles + AAR draft |
| CAPA / corrective actions | ✅ | transitions; F8 auto-create on DR-test fail |
| Compliance mapping | ◑ | maps clause→control + attach-evidence-by-id; generic evidence requests now available (B-B), but mapping-level evidence depth stays light |
| Evidence | ✅ | **resolved (B-B/B-C/B-D + §12.9/§12.16)**: request workflow, upload on all 6 entities, review/reject→gap/resubmit/expiry, comment thread, dashboard tile (3 fine-grained ◑ in §12) |
| Reporting / export | ✅ | PDF exports + evidence-package manifest |
| Audit trail | ✅ | events + audit.log throughout (benefits from `ae3433f9` off-request fix) |
| Automation (F8) | ✅ | ~40 triggers + scheduler + RecordCreator |
| ERM / control linkage | ✅ | FK columns: `linked_risk_id`, `related_erm_risk_id`, `related_control_id` |
| Arabic / RTL | ✅ | B-I: bilingual labels + logical-property RTL; EN/AR parity verified (incl. browser walk) |

---

## 6. Critical Gaps

| ID | Gap | Severity | Evidence |
|---|---|---|---|
| **B-1** | **"Generate BCP/DRP from BIA" is a placeholder stub** — stamps `generatedBcpId`/`generatedDrpId` + audits; no populated plan created | **Critical** | `BcmBiaAssessmentService` javadoc "generateBcpStub/generateDrpStub stamp the BIA … placeholders"; `markBcpGenerated`/`markDrpGenerated` set an id only |
| **B-2** | **No evidence-request workflow** (request from dept, owner, due date, countdown, SLA, reminders, escalation) anywhere in BCM | **Critical** | no `EvidenceRequest`/`EvidenceLink` usage in `module/bcm`; no SLA/countdown in BCM services |
| **B-3** | **No real file upload** for BIA / BCP / DRP / DR-test / CAPA / policy; not wired to `AttachmentService` | **Critical** | zero `MultipartFile` in `module/bcm`; AttachmentsField only on `AarDetailPage` + `ExerciseScenarioDetailPage` |
| **B-4** | **No evidence lifecycle** — review, rejection→gap, resubmission, expiry, pre-expiry notice | **High** | absent in BCM (consequence of B-2/B-3); CAPA has only an `EVIDENCE_SUBMITTED` flag |
| **B-5** | BIA→strategy / min-resources / MBCO capture (was partial vs. ISO 22301 depth) — **resolved B-F/B-G** | Medium | tasks 18/20 → ✅ |
| **B-6** | `exercise-runs` root GET returns **405** (no list endpoint) — exercises only reachable via sub-paths | **Medium** | live smoke `GET /api/v1/bcm/exercise-runs → 405` |
| **B-7** | Arabic/RTL not exhaustively verified across ~33 pages | **Low** | needs a live bilingual walk |

> ✅ **Resolution (post-remediation — see §0):** every gap above is now fixed — **B-1 → B-A**, **B-2 → B-B**, **B-3 → B-C**, **B-4 → B-D**, **B-5 → B-G**, **B-6 → B-H** (live-confirmed HTTP 200), **B-7 → B-I**.
>
> Note (original): gaps B-2/B-3/B-4 are the **same class** the Compliance module had (C-1/C-2) and that were remediated there; BCM was never wired to the evidence subsystem, so it carried the full gap.

---

## 7. BIA Gaps
- **Validation is real and good:** approval blocked unless `mtpd_hours > 0` and `rto_hours ≤ mtpd_hours` (`BcmBiaAssessmentService.validateRtoMtpd`). Quantitative BIA (V162) adds financial impact + `BIA_QUANT_APPROVED`.
- **Downstream generation is the core defect (B-1):** approving a BIA does **not** produce a populated BCP/DRP. `markBcpGenerated/markDrpGenerated` only stamp an id; `generate*Stub` only audit. ISO 22301's central "BIA drives the plan" linkage is cosmetic.
- **Evidence:** BIA has no attached evidence/file (B-3).
- ~~**Partial:**~~ **✅ Resolved (B-F):** MBCO + minimum-resource + manual-workaround + peak-period capture are confirmed end-to-end in the BIA wizard (tasks 18/20) and seeded into the generated plan.
- **Good:** ERM link (`linked_risk_id`), criticality (incl. sovereign), versioning via cycle.

## 8. BCP Gaps
- **Strong:** activation criteria, response team, recovery steps, comms/call tree, approve→publish→attest lifecycle, plan-review-overdue automation (`BCM_PLAN_REVIEW_OVERDUE`), version/distribution (V109/V110).
- ~~**Gaps:**~~ **✅ Resolved (see §0):** auto-generated + seeded from the approved BIA (B-A) and signed-plan / evidence upload (B-C) are shipped. *Residual:* exercise/test-history linkage to the plan could be tightened.

## 9. DRP Gaps
- **Strong:** recovery sequence, DR test outcomes `PASSED/GAP/FAILED`, and **failed/GAP test → `BCM_DR_TEST_FAILED` → F8 → auto-CAPA** (`DrPlanService:173-181` + `BcmCorrectiveActionRecordCreator`). DR-test trend reporting.
- ~~**Gaps:**~~ **✅ Resolved (see §0):** backup/test-evidence upload (B-C), post-failover validation checklist (B-G), and BIA→DRP generation (B-A) are shipped. *Residual:* the application/system-register FK depth could be tightened.

## 10. Crisis & Emergency Gaps
- **Strong (near-complete):** crisis events, severity, situation reports, decisions, actions, regulatory notifications, **BCP/DRP activation**, closure, AAR (V124/V131), comms dispatch v2 + call-tree failure escalation (`BCM_CALL_TREE_FAILED`), and **cross-module signals** (`BCM_CRISIS_PDPL_BREACH`, `_CYBER_LINKED`, `_SUPPLIER_LINKED`). This is the strongest sub-area.
- **Gaps:** evidence attachment on crisis decisions/sitreps (B-3); verify mobile crisis room parity; AAR→lessons→CAPA chain fully closed.

## 11. Exercise & Testing Gaps
- **Strong:** scenario library, exercise runtime, **observer/facilitator/participant** consoles, AAR draft auto-created on completion.
- ~~**Gaps:**~~ **✅ Resolved (see §0):** exercise observation → tracked gap → CAPA (B-E), and `GET /api/v1/bcm/exercise-runs` list (B-H, was 405). Observation evidence upload uses the generic attachment field.

## 12. Evidence Workflow Gaps (the 20-point checklist)
| # | Capability | BCM status |
|---|---|---|
| 1 | Request evidence from dept/user | ✅ (B-B) |
| 2 | Assign evidence owner | ✅ (B-B: department + assignee) |
| 3 | Due date | ✅ (B-B) |
| 4 | Countdown timer | ✅ (B-B) |
| 5 | SLA status | ✅ (B-B) |
| 6 | Reminders before due | ✅ (B-B: `EvidenceRequestSlaScheduler`) |
| 7 | Escalate after overdue | ✅ (B-B: SLA scheduler escalation) |
| 8 | File upload | ✅ (B-C: `AttachmentService` on all 6 BCM entities) |
| 9 | Comments | ✅ (request comment thread — V240) |
| 10 | Reviewer approve/reject | ✅ (B-B/B-D: APPROVED/REJECTED transitions) |
| 11 | Rejection reason | ◑ (captured via the comment thread §12.9; no dedicated reason field on the reject action) |
| 12 | Resubmission | ✅ (B-D: REJECTED → OPEN/IN_PROGRESS) |
| 13 | Versions | ◑ (attachments carry sha256 + version, and evidence-items are versioned, platform-wide; no per-request version history) |
| 14 | Audit trail | ✅ (general) |
| 15 | Link evidence to BIA/BCP/DRP/exercise/control | ✅ (B-B: generic `entity_type`/`entity_id` anchor) |
| 16 | Show in dashboard | ✅ (BCM dashboard evidence tile) |
| 17 | Include in reports | ✅ (evidence-package export) |
| 18 | Expire when validity ends | ✅ (B-D: attachment expiry / re-collect) |
| 19 | Notify before expiry | ◑ (overdue-request reminders exist; a dedicated pre-expiry notice is not confirmed) |
| 20 | Create gap if missing/rejected | ✅ (B-D: rejected → deduped BCM gap via `BcmGapRecordCreator`) |

**This was the weakest area and the primary remediation target — now substantially resolved.** ✅ **Post-remediation (B-B/B-C/B-D + residual §12.9/§12.16, see §0): 17 of 20 points are ✅** — request/owner/due/countdown/SLA/reminders/escalation/upload (1–8), review-reject/resubmit (10, 12), comments (9), entity links (15), dashboard (16), reports (17), expiry (18), gap-on-reject (20). **3 residual ◑** are depth items: a dedicated **rejection-reason field** (11 — today via the comment thread), per-request **version history** (13 — attachments are already versioned), and a **pre-expiry notice** (19 — overdue-request reminders exist). The generic evidence subsystem (`EvidenceRequest`/`EvidenceLink`/`AttachmentService` + the SLA scheduler) already existed platform-wide — BCM is now wired to it.

## 13. Policy/Document Workflow Gaps
- Governance/policy records + lifecycle + review-due automation exist; **but no file upload** (B-3), no reviewer/approver field capture on the document itself, no version/archive of an uploaded file. Mirrors the Compliance policy gap before its remediation.

---

## 14. Button & Process Testing Results (representative)

Live + code-grounded. Full per-button sweep is part of the remediation test plan (§25).

| Page / API | Action | Expected | Actual | Pass/Fail | Severity | Fix |
|---|---|---|---|---|---|---|
| `/api/v1/bcm/cycles` | List cycles | rows | OK (2) | Pass | — | — |
| `/api/v1/bcm/bia-assessments` | List BIA | rows | OK (6) | Pass | — | — |
| BIA approve | Block if RTO>MTPD | reject | enforced | Pass | — | — |
| Generate BCP from BIA | Build populated plan | **stamps id only** | **Fail** | Critical | B-1 |
| `/api/bcm/dr-plans` | List DRP | rows | OK (16) | Pass | — | — |
| Record DR test = FAILED | Auto-create CAPA | F8 event → CAPA | Pass | — | — |
| Add backup evidence (DRP) | Upload file | **no upload** | **Fail** | Critical | B-3 |
| `/api/v1/bcm/crisis-events` | List crisis | rows | OK (5) | Pass | — | — |
| Crisis → activate BCP/DRP | Activate + event | events emitted | Pass | — | — |
| `/api/v1/bcm/capas` | List CAPA | rows | OK (10) | Pass | — | — |
| Request evidence from dept | Open request workflow | **no action** | **Fail** | Critical | B-2 |
| `/api/v1/bcm/exercise-runs` | List exercise runs | rows | **405** | Fail | Medium | B-6 |
| Generate evidence package | Manifest export | OK | Pass | — | — |

---

## 15. Data Model Gaps
- **Missing:** evidence-request linkage for BCM entities (reuse `EvidenceRequest`/`EvidenceLink` with `entityType=BcmBiaAssessment/BcpPlan/DrPlan/CrisisEvent/Exercise/Capa`); a real `attachment` linkage (reuse `platform.attachments` via `AttachmentService`) for plans/DR-tests/policy/CAPA; DR-test `backup_evidence` reference; evidence-validity/expiry columns.
- **Stub fields:** `generated_bcp_id` / `generated_drp_id` on BIA exist but are not populated by a real generator.
- **Present + good:** `linked_risk_id`, `related_erm_risk_id`, `related_control_id`, criticality, MTPD/RTO/RPO/MBCO, plan/cycle/crisis status columns.

## 16. API Gaps
- ~~**Missing:**~~ **✅ Resolved (B-A/B-B/B-C/B-H + §12.9; see §0):** evidence-request endpoints (incl. `/by-entity` and `/{id}/comments`), multipart upload via the generic `/api/v1/attachments/...`, the real `generateFromBia` plan generator, and `GET /api/v1/bcm/exercise-runs` (was 405 → 200).
- **Present:** comprehensive CRUD + transitions + exports across all registers.

## 17. UI/UX Gaps
- Well-wired (22 distinct API fns in BCM pages; minimal stub markers). **Former gaps now resolved (see §0):** upload UI on all 6 BCM entities (`AttachmentsField`), evidence-request UI with countdown/SLA chips + comment thread (`EvidenceRequestsField`), and a dashboard evidence tile; Arabic/RTL verified via a browser walk.

## 18. Integration Gaps
| Integration | Status |
|---|---|
| ERM risk register | ✅ FK (`related_erm_risk_id`, `linked_risk_id`) |
| Control library | ✅ FK (`related_control_id`) |
| Compliance requirements | ◑ mapping by clause/control + attach-evidence-by-id |
| **Evidence subsystem** (EvidenceRequest/Link/AttachmentService) | ✅ **wired (B-B/B-C/B-D + §12.9/§12.16)** |
| Corrective-action register | ✅ (BCM has its own CAPA + F8 auto-create) |
| Incident / cyber / PDPL / supplier | ✅ crisis cross-module signals |
| Audit log | ✅ (benefits from `ae3433f9`) |
| F8 automation engine | ✅ ~40 triggers + RecordCreator + rule seeds |
| Reports / dashboard | ✅ live source data |
| Notifications / reminders / escalation | ✅ cycle/plan/CAPA overdue · ✅ evidence (B-B: SLA scheduler) |
| Approval workflow | ✅ governance/plan/BIA/CAPA |
| Role permissions | ✅ B-I: BCM grant matrix + `BCM_APPROVE` seeded (verified live BCM_MANAGER 200 / CYBER 403) |
| Arabic/English i18n + RTL | ✅ B-I: full EN/AR parity + logical-property RTL (browser-walked) |

---

## 19. Required Remediation Backlog (epics → stories)

**EPIC B-A — BIA→Plan Generation (Critical, B-1)**
- *Story:* As a BCM manager, when I approve a BIA, I want a populated BCP **and** DRP skeleton generated (pre-filled with the BIA's critical service/process, RTO/RPO/MTPD/MBCO, dependencies, min resources, owner) so the plan starts from the analysis. *AC (Given/When/Then):* Given an APPROVED BIA, When I click "Generate BCP", Then a `BcpPlan` row is created with fields pre-populated from the BIA, linked back (`generated_bcp_id`), an audit `PLAN_GENERATED` entry is written, and it appears in the BCP register in DRAFT. Same for DRP (with application/system link). *Files:* `BcmBiaAssessmentService`, `BcpPlanService`, `DrPlanService`.

**EPIC B-B — Evidence Request Workflow for BCM (Critical, B-2)** — reuse the existing `EvidenceRequest`/SLA scheduler.
- *Story:* Request evidence from a department with owner + due date for any BCM entity (plan, DR test, exercise, BIA, CAPA). *AC:* creates a real `EvidenceRequest` (departmentId, dueDate, priority), shows countdown + SLA status, fires reminders pre-due and escalation overdue (the `EvidenceRequestSlaScheduler` already exists), and a missing/rejected request auto-creates a BCM gap/CAPA (reuse the `AuditFinding`/CAPA `CREATE_RECORD` pattern).

**EPIC B-C — File Upload + Evidence Linkage (Critical, B-3)** — adopt the generic `AttachmentService`.
- *Story:* Upload an evidence file (signed plan, DR-test log, backup screenshot, policy PDF) on BIA/BCP/DRP/DR-test/CAPA/policy. *AC:* multipart upload via `/api/v1/attachments/{entityType}/{entityId}` (entityTypes for the BCM entities), file shown with sha256 + version, included in the evidence-package export.

**EPIC B-D — Evidence Lifecycle (High, B-4):** review/approve/reject(reason)/resubmit/version + expiry + pre-expiry notice + gap-on-rejection.

**EPIC B-E — Exercise→Gap→CAPA closure (High):** make the failed-exercise path mirror the DR-test path (auto-gap + CAPA), close AAR→lessons→CAPA.

**EPIC B-F — DRP depth (Medium):** failover/validation-checklist/rollback structured capture + backup-evidence file.

**EPIC B-G — BIA depth (Medium):** MBCO, minimum-resources, manual-workaround, peak-period capture end-to-end.

**EPIC B-H — `exercise-runs` list endpoint (Medium, B-6).**

**EPIC B-I — Arabic/RTL walk + role-permission ratchet (Low/Medium).**

**EPIC B-J — Reporting/evidence-package: include uploaded evidence + request status (Medium).**

---

## 20. Immediate Fixes (quick wins)
1. Add `GET /api/v1/bcm/exercise-runs` list (B-6) — small.
2. Wire **file upload** on BIA/BCP/DRP/DR-test/policy via the existing generic `AttachmentService` + `AttachmentsField` (already used on AAR/exercise) — high value, low risk (B-3).
3. Verify + document the scheduled `BcmAnnualCycleScheduledSweep` fires cleanly post-`ae3433f9` (audit off-request fix).

## 21. Structural Fixes
1. Real BIA→BCP/DRP generator (B-1).
2. Evidence-request workflow reusing `EvidenceRequest` + SLA scheduler + `EvidenceLink` (B-2/B-4).
3. Evidence-package export to include uploaded evidence + request/approval status (currently a manifest of internal records only).

## 22. Data Model Changes Required
- New `EvidenceLink` rows (or reuse generic) for `entityType ∈ {BcmBiaAssessment, BcpPlan, DrPlan, CrisisEvent, ExerciseRun, BcmCapa, BcmGovernance}`.
- Populate `generated_bcp_id` / `generated_drp_id` from a real generator (B-1).
- DR-test `backup_evidence_attachment_id` (FK to `platform.attachments`).
- Evidence validity/expiry columns where evidence is attached.

## 23. API Changes Required
- `POST /api/v1/bcm/{entity}/{id}/request-evidence`, `POST .../controls`-style links.
- Multipart upload for BCM entities (or standardise on generic `/api/v1/attachments/...`).
- `POST .../bia/{id}/generate-bcp` + `generate-drp` (real generation).
- `GET /api/v1/bcm/exercise-runs`.

## 24. Workflow Engine Changes Required
- Reuse F8 + the evidence SLA scheduler for evidence reminders/escalation (don't build new).
- Add `EVIDENCE_REJECTED → CREATE_RECORD BcmGap/BcmCapa` rule (mirror the compliance `AR-EVD-GAP` pattern shipped on master).
- BIA `APPROVED → generate plans` as a service action (not just an event).

## 25. Testing Plan
- **Unit:** BIA validation (RTO≤MTPD) ✅ exists — extend to generator; DR-test→CAPA ✅; add evidence-request + link + expiry; exercise→gap.
- **API:** every BCM register list/create/transition/export; the new evidence + generate-plan + exercise-runs endpoints.
- **E2E:** BIA→generate BCP/DRP→approve→publish; crisis→activate→sitrep→decision→close→AAR; exercise→fail→CAPA; request evidence→upload→review→reject→resubmit→approve→expire.
- **Regression:** full `mvn test` (currently 1957 green) + frontend vitest/build after each major fix.
- **Manual/bilingual:** per-page Arabic/RTL walk; role-permission matrix.

## 26. Acceptance Criteria for Remediation
BCM is "market-ready" when: (a) approving a BIA generates linked, populated BCP+DRP; (b) any BCM entity supports real file upload + a request-evidence workflow with owner/due/countdown/reminders/escalation; (c) evidence has review/reject→gap/resubmit/expiry; (d) the audit evidence-package includes uploaded evidence + request status; (e) failed exercises auto-create gaps/CAPA like failed DR tests; (f) Arabic/RTL verified across all BCM pages; (g) tests cover the above and the full suite stays green.

## 27. Implementation Order
1. **B-C** file upload (quick, unblocks evidence) → 2. **B-B** evidence-request workflow + SLA → 3. **B-A** BIA→plan generator → 4. **B-D** evidence lifecycle + B-J package → 5. **B-E** exercise→CAPA → 6. **B-F/B-G** DRP/BIA depth → 7. **B-H** exercise-runs list → 8. **B-I** Arabic/RTL + permissions.

## 28. Risks if Not Fixed
- **Audit/regulator:** auditors cannot pull uploaded evidence for plans/DR tests; "BIA-driven plans" claim fails inspection (generation is cosmetic) — a finding for SAMA/DGA/ISO 22301 audits.
- **Business:** BCM team cannot collect evidence from departments with deadlines/escalation — the core operational loop is manual/offline.
- **Market:** competitors (Archer/MetricStream/Fusion) ship BIA-drives-plan + evidence SLA out of the box; absence is visible in a demo to a Saudi buyer.
- **User adoption:** "Generate BCP" that produces an empty stamp erodes trust.

## 29. Final Recommendation

**Original verdict: Partially usable — needs targeted remediation.**
**Post-remediation (2026-06-29): the targeted remediation is complete — see §0.**

BCM is a **strong, genuinely-implemented module** — the richest in the platform (real validation, status lifecycles, ~40 automation triggers, crisis/exercise depth, ERM/control linkage, exports). It is **not** demo-only. The three Critical gaps the review found — **BIA→plan generation is a stub**, **no evidence-request workflow**, and **near-absent file upload / no evidence subsystem wiring** — were, as predicted, **well-scoped and resolved by reusing existing infrastructure** (the generic `AttachmentService`, `EvidenceRequest` + SLA scheduler, and the F8 `CREATE_RECORD` pattern shipped for Compliance), not a rebuild.

**What shipped on `feat/bcm-remediation`** (all live-verified against :8084):
- **B-C** — file upload on all six BCM entities (BCP, DRP, crisis, BIA, governance, CAPA).
- **B-B** — generic entity-anchored `EvidenceRequest` + a reusable `EvidenceRequestsField` (owner/due/countdown/lifecycle), wired into all BCM surfaces, reusing the SLA scheduler/reminders/escalation.
- **B-A** — the BIA→BCP/DRP generator now seeds real section content from the BIA (objectives, MBCO, resources, workaround, suppliers, dependencies).
- **B-D** — rejected evidence request auto-opens a deduped BCM gap (F8 event + `BcmGapRecordCreator`).
- **B-E** — promote an exercise observation to a tracked gap (idempotent, severity-mapped) → CAPA.
- **B-F/B-G** — DRP post-failover validation checklist; BIA MBCO/min-resources confirmed captured.
- **B-H** — `GET /api/v1/bcm/exercise-runs` list endpoint (was 405).
- **B-I** — BCM role-permission grant matrix + `BCM_APPROVE` (was admin-only); Arabic/RTL verified.

**Revised verdict: Production-ready for an audited Saudi BCM buyer.** The full `mvn test` gate is satisfied (**1970 tests, 0 failures**); all work — the B-A…B-I backlog **and** the residual-partials pass — is **merged to `master`** (PR #487 + PR #488, fast-forward; master CI green incl. Docker; the new UI browser-walked). The only remaining gate before production is a stakeholder **UAT** pass.

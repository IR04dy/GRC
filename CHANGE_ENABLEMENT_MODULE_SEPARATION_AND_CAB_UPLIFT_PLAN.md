# Change Enablement — Module Separation & CAB / Committee Uplift Plan
### فصل وحدة إدارة التغيير وتطوير لجنة استشارات التغيير (CAB)

> **Plan type:** design & implementation plan — **no product code changed by this document**.
> **Date:** 2026-07-23 · **Baseline as written:** `master` @ `00ba890e` · backend suite 2316/0/0 · ratchet 62 · next free migration V313.
> **Re-baselined 2026-07-25:** suite **2,460/0/0** · ratchet **21** · next free migration **V318** (F2-28); the first CE number is **V322** · branch `feat/change-policy-remediation`.
> **Predecessor:** `CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (CH-1…CH-17 + Tracks A–D — **100 % delivered and merged**). This plan starts where that one ended: the change module is now *correct* (guarded, audited, SoD-gated, committee-routed). It is not yet *separate*, and its CAB is a thin gate rather than a governed body with its own lifecycle and records.
> **House rules** (`CLAUDE.md`): verified slices · feature branch · **no merge without explicit "merge"** · **ratchet ≤ 21** (was 62 when this plan was written) · idempotent migrations applied twice to dev via pyodbc (Flyway OFF in dev) · `[REVIEW-TEST]` + CHARINDEX cleanup · **destructive DB ops need explicit OK** · bilingual `*_en` + `*_ar`.

---

## §0 Implementation status

> Single source of truth for progress. Update the row **in the same commit** as the slice.

> **Status:** LIVE SPECIFICATION · **Sequenced by:**
> [`UNIFIED_IMPLEMENTATION_PLAN.md`](UNIFIED_IMPLEMENTATION_PLAN.md) §6c and Wave 3 · **Last reconciled:** 2026-07-25
>
> **Elevated 2026-07-25** at the owner's direction: Change Enablement becomes a first-class module with
> explicit CAB, committee-integration and workflow structure, and is lifted out of the Risk navigation
> group. §6c of the unified plan records the measured premise — **the backend was never under Risk**
> (`module/change_mgmt`, own `change_mgmt` schema, **0 imports either way with `risk`**); what placed it
> under Risk is `Sidebar.tsx:106`. New **CE-0** covers that nav split (XS, do first).
>
> ⚠ **Two corrections to this document, both material:**
> 1. **The migration numbers below are stale.** V313–V317 have been taken by other work (V313 permissions,
>    V314 gate map, V315 vote provenance, V316 CAB approval provenance, V317 committee/change-authority
>    grants). CE migrations are renumbered **V322–V331, assigned in delivery order** (V318 F2-28, V319 `ck_change_status`, V320 W2-0, V321 F2-04 all land first); the unified plan's §4 ledger is authoritative.
>    Re-check it immediately before creating any migration file.
> 2. **CE-3/CE-4 are gated on F2-28 (DF-5).** The committee quorum denominator is currently caller-authored,
>    and CE-4 asserts CAB decisions on quorate minutes. Building CAB governance on that would give every CAB
>    decision a forgeable validity precondition. Close F2-28 first (3–5 days).
>
> Also already shipped since this plan was written: CH-5 change-task SLA scheduler, CH-17 authorisation
> matrix + freeze windows, Track D committee re-route + e-signed minutes, and **DF-4** (CAB approval
> provenance — actor from `Authentication`, `CHANGE:APPROVE`, one vote per approver). **CE-15's RBAC half is
> partly done:** V317 created `CHANGE_MANAGER` and took `CHANGE:APPROVE` to 9 roles (ADR-015).

> ⚠ **Reconciled 2026-08-04 by S8c/CE-16, and the reconciliation was itself the finding.** Every one of
> the seventeen rows below read **NOT STARTED** while eleven epics had shipped and merged — in a table
> whose own header calls it the *"single source of truth for progress"* with the instruction to update
> it in the same commit as the slice. A reader planning work from it would have rebuilt CAB governance
> from scratch. Statuses and commits are taken from the delivery rows of
> [`UNIFIED_IMPLEMENTATION_PLAN.md`](UNIFIED_IMPLEMENTATION_PLAN.md) §6c (each verified, live-probed and
> merged at the time); this pass copied them, it did not re-verify the epics.

| Epic | Content | Size | Status | Commit | Migration |
|---|---|---|---|---|---|
| **CE-0** | **Lift Change out of the Risk nav group** — own `navChange` sidebar group with CAB / calendar-FSC / decision-register beneath it (`Sidebar.tsx:106`) | XS | ✅ **DONE** — only the routes that resolve are listed; the CAB queue and decision register join as they land | `29925f3e` | none |
| **CE-1** | Module separation: `change_mgmt` → `module/change` w/ sub-packages, ports/adapters, import-boundary ratchet test | L | ✅ **DONE (S5a + S5c)** — 7 peer imports → 0, ports in `spi/`, adapters in `module/change_adapters`, mutation-proven `ChangeModuleBoundaryTest`. ⚠ The **package rename** was closed as **superseded** by ADR-017 D-4, not performed (§15.3's own reasoning, ratified); the schema name stays `change_mgmt` and so does the package | `764a41a4`, `d19e6d86` | none (code-only) |
| **CE-2** | Change **model catalogue** (standard/pre-approved change templates) | M | ✅ **DONE (S8e)** — **V354**, two empty tables. The slice is one constraint: `ck_cmodel_preapproval` makes `pre_approved = 1` unrepresentable without an authorising name, a moment and a **bounded expiry**, and the migration proves the CHECK *rejects* rather than merely existing. Three acts (author `WRITE` → approve `APPROVE`, **not the author** → apply `WRITE`), and the request DTO carries none of the authorisation fields because the CHECK tests that they agree, not who set them. The `requires_model` flag is per cell, defaults off, and V354 THROWs if any row already reads 1 — 3 of the 5 STANDARD changes are in flight | *S8e* | **V354** |
| **CE-3** | **CAB as a governed body**: charter/ToR, membership matrix + roles, quorum policy, term review | M | ✅ **DONE (S6, S6a–S6c)** — closed **mostly by measurement**: `cab_profile`/`cab_member_role` are subsumed 12-of-15 and 6-of-7 by the generic committee engine and are pinned against being built; the one field that genuinely belonged needed a flag (`quorum_requires_chair`, opt-in) | `a06ea46b`, `78df5906`, `6f2c0b60`, `3219786f` | **V348** |
| **CE-4** | **CAB meeting cycle** + agenda auto-build from the CAB queue + papers pack | L | ✅ **DONE (S7a, S7b)** — the queue had to be *defined* first: its two definitions disagreed and the only referrals pointed at changes in no organisation | `453b6ea9`, `f7219422` | **V349**, **V350** |
| **CE-5** | **Decision write-back**: APPROVE/REJECT/DEFER/MORE_INFO/CONDITIONAL + conditions register | M | ✅ **DONE (S7c, S7e)** — 14 → **16** states, not 17: `PENDING_RATIFICATION` has no producer. **No `CONDITIONAL` literal** — conditionality is derived from carrying open conditions, and V352's self-check THROWs if one enters `ck_change_status` | `ebfbc5f1`, `5fad1646` | **V351**, **V352** |
| **CE-6** | **ECAB** emergency path + mandatory retrospective **ratification** with SLA | M | ✅ **DONE (ADR-017 D-3)** — ratification modelled **orthogonally** to `status`, and `ck_change_ratification_clock` makes the breach predicate total by constraint | `cf2f9ebf` | **V345** |
| **CE-7** | Real **PIR** object (findings, actions, lessons) + auto-CAPA link | M | ✅ **DONE (S7d)** — **no migration**: the CAPA store this plan named is ESG-SKU-gated, while the Base universal tracker has no CHECK on `origin_type`. Produced F2-105 — the PIR card had 400'd on every save since F2-30 | `d82e6a7f` | none |
| **CE-8** | **FSC** (forward schedule of change) + **PSO** + conflict/collision hardening | M | 🟡 **PARTIAL** — calendar, freeze windows and the conflict *report* shipped with CH-17; a **published** forward schedule and a **declared** service outage do not exist | — | V-next |
| **CE-9** | Change **records & evidence** — adopt the platform evidence/attachment subsystem + retention | M | ✅ **DONE (S8a)** — and **not** `change_links`, whose un-hashed, un-reviewed pointer *is* the deficiency G-9 describes. The satisfaction gate fails **closed**; every read fails open | `0440272c` | **V353** |
| **CE-10** | **Workflow-engine adoption** for change authorisations (15th module) | M | ⬜ NOT STARTED | — | none |
| **CE-11** | **Unauthorised-change detection** + reconciliation register | S | ✅ **DONE 2026-08-05** (**V355**) — and the row's own premise was wrong. **Drift detection has no input on this platform**: measured 0 CMDB tables, 0 configuration-item tables, 0 baseline tables, and the `integrations` schema holds **0 tables**; the only `%drift%` match is `ai_governance.ml_model_drift_observation`, a different subject. What exists is the reconciliation G-11 actually names — matrix (required) × `change_approvals` + committee resolution (recorded) × `status` (went ahead) — and on the seeded data it finds real failures, including an EMERGENCY/CRITICAL change at `IMPLEMENTED` on an ECAB cell demanding **2** approvals with **1** recorded. ⚠ **The first draft read one table and would have condemned the other route**: a referred change is authorised by the committee's carried resolution and writes **no** vote rows, so counting votes alone flags the platform's *preferred* path as the defect — fixed with `CommitteePort.decisionApproves`, the non-throwing sibling of the write path's assertion, delegating to the same engine answer. ⚠ **An authority gap and an unattributed authorisation are different facts**: 6 of dev's 11 findings satisfy `min_approvals` in full and lack only `cab_decision_at`, so folding them in would report 8 unauthorised changes where 3 is the truth. Findings **derived**, disposition **stored** — and a disposition annotates, never erases: no `CLOSED` state, and the KPI counts disposed gaps so the number cannot be cleared by signing off. `unauthorisedChangeCount` flips NOT_MEASURABLE → COMPUTED and `CE11_REGISTER_ABSENT` is **renamed**, because a blocker describing a gap the platform has closed is the stale-documentation defect one layer down | — | **V355** |
| **CE-12** | ITIL/ISO **KPI pack** + CAB throughput analytics | S | ✅ **DONE (S8b)** — **no migration**. 19 metrics carrying coverage **and** provenance as separate axes; 7 report `NOT_MEASURABLE` with a named blocker because nothing populates their source. Produced F2-107 and F2-108 | `04193b09` | none |
| **CE-13** | FE: split `ChangesPage` → Change workspace + **CAB workspace** (agenda builder, voting, minutes) | L | 🟡 **PARTIAL (S5c)** — 1,036 lines → nine files, proven token-identical. The **CAB workspace** and the retirement of `mockChanges.ts` remain | `d19e6d86` | none |
| **CE-14** | Exports/records pack: agenda pack, minutes, decision register (RTL + bilingual) | M | ✅ **DONE (S7f = CE-14a, S8d = CE-14b)** — agenda pack + minutes, each **stating the provenance** of every figure it prints (`quorum_source` is `ASSERTED_LEGACY` or NULL on 32 of 34 meetings), and the decision register, whose rows carry **coverage and evidence as separate axes** because 11 changes have passed a decision point and 1 names its author. Produced **F2-109** | `d6ce008b`, *S8d* | none |
| **CE-15** | RBAC: CAB roles + `CHANGE`/`CAB` permissions + SoD gates | S | 🟡 **PARTIAL** — V317 created `CHANGE_MANAGER` and took `CHANGE:APPROVE` to 9 roles (ADR-015); the SoD gates shipped across S6b/S7c/S7e/A17-D3. §10's *PIR approver ≠ implementer* and *condition `satisfied_by` ≠ owner* are **not** built | — | **V317** |
| **CE-16** | Docs: PROJECT_CHRONICLE §, `package-info`, CAB operating runbook | S | ✅ **DONE (S8c)** — `PROJECT_CHRONICLE` §3.10 (the handbook mentioned this module **zero** times in 1,145 lines), a rewritten module contract (it described a **ten**-state machine and called emergency a CAB bypass), the operating runbook, **and a freshness test** so all three are gated against the code rather than trusted | *this slice* | none |

⚠ **The `V322–V331` reservation this line used to publish is void, and the delivered numbers show why:**
the epics landed on **V345, V348–V353** — every reserved number had been taken by other work before the
slice that reserved it was written. **Reserving a version number in prose does not hold it.** Read the
directory (`ls tss-grc-backend/src/main/resources/db/migration/sqlserver | tail -3`) immediately before
creating a file; the unified plan's §4 ledger records intent, not ownership.
Every migration idempotent, `GO`-separated, applied to dev **twice**.
**Open decisions requiring the user: §19** — do not start CE-3/CE-4 before the **unified plan's** D-1 and D-2 are answered (not CD-1/CD-2),
**and not before F2-28 (quorum integrity) is closed.**

---

## §1 Executive summary

### 1.1 Why separate the module

Change Enablement today is **1,862 LoC across 18 files** in `module/change_mgmt`, but it is not a module in any enforceable sense:

- **One god-service.** `ChangeManagementService` is **853 LoC** and owns *everything*: CRUD, the state machine, approvals/votes, tasks, links, comments, analytics, the authorisation matrix, freeze windows, conflicts, committee referral and PIR capture. Every new CAB feature makes it worse.
- **Silent outbound coupling.** It reaches directly into two peer modules — `admin.committee` (`CommitteeReferralService`) and `incident` (`IncidentService` + its DTOs) — with no interface between them. A change to `IncidentService`'s signature breaks Change Enablement at compile time.
- **Nothing enforces the boundary.** There is no ArchUnit/import test (only `ControllerAuthorizationRatchetTest` for authorisation). Coupling can grow silently.
- **The FE mirrors the problem.** `ChangesPage.tsx` is **981 LoC** holding register + kanban + workflow + analytics + integrations + governance + comms in one file, plus a live `mockChanges.ts`.
- **Its records live elsewhere.** Exports sit in `module/reporting` (`ExportService.changeRegisterPdf/Xlsx/Docx`) — acceptable as a shared export service, but it means "the change module" cannot be reasoned about, tested, or shipped as a unit.

Separation is therefore **not cosmetic**: it is the precondition for adding a CAB lifecycle without turning an 853-line service into a 2,000-line one.

### 1.2 Why the CAB uplift

Track D made the committee engine the **system of record for referred changes** (quorum, votes, COI recusal, e-signed minutes). That closed the *governance* gap. What remains is the **operating model** gap — the difference between "a change can be gated by a committee" and "the organisation runs a CAB":

| ITIL 4 / ISO 20000-1 expectation | Today |
|---|---|
| A **change authority** per change class | ✅ `change_authorisation_matrix` (V306) — type × risk → AUTO/CAB/ECAB + `min_approvals` |
| Committee decision gates the change | ✅ Track D (`CommitteeReferralService.assertDecisionApproves`) |
| **Change models** for standard/pre-approved changes | ❌ `change_type='STANDARD'` is a free string — no catalogue, no template, no pre-authorisation record |
| A **constituted CAB**: charter, membership, roles, terms, quorum rule | ⚠️ Partially — the *generic* committee engine has `committee_member_terms`, `committee_member_skills`, attendance and votes, but there is no **CAB profile**: no ToR, no chair/secretary roles, no CAB-specific quorum policy |
| **CAB meeting cycle**: agenda from the change queue → papers → notice → quorum → deliberation → decisions → minutes → actions | ⚠️ The engine has meetings/papers/standing items/action items/calendar — but **nothing builds a CAB agenda from changes awaiting authorisation**. A secretary would assemble it by hand |
| Decision outcomes beyond approve/reject (**defer / more info / approve-with-conditions**) | ❌ Resolution outcomes are `APPROVED│REJECTED│DEFERRED│WITHDRAWN`; a change can only go `CAB_APPROVED│CAB_REJECTED`. Conditions are not recorded or enforced |
| **Emergency** path + mandatory retrospective review/ratification | ⚠️ `EMERGENCY_APPROVED` exists and Track D sets `mode=RATIFICATION`, but ratification is **not enforced** and has no SLA |
| **Post-implementation review** as a record | ⚠️ CH-11 captures a PIR *summary string* on the change and blocks closure without it — but there is no PIR entity, no findings, no actions, no lessons-learned register |
| **Change records** with evidence | ❌ **Zero** evidence/attachment integration in `module/change_mgmt` (verified: no `Evidence`/`Attachment`/`Workflow` reference anywhere in the package). ISO 20000-1 documented-information expectations are met only by free text |
| Detection of **unauthorised changes** | ❌ Not modelled |
| Schedule publication (**FSC**, **PSO**) | ⚠️ `/calendar` + `change_freeze_window` (V307) exist; no published forward schedule, no projected-outage concept |

### 1.3 Shape of the answer

1. **Extract** Change Enablement into `module/change` with real sub-packages and **ports** for its two outbound dependencies; lock it with an import-boundary ratchet test.
2. **Do not build a second meeting engine.** The committee engine is 4,638 LoC / 14 tables and already does quorum, votes, recusal, papers, action items, calendar and e-signed minutes. CAB becomes a **profile + workspace over it**, plus the CAB-specific pieces it genuinely lacks (agenda auto-build from the change queue, decision write-back, conditions, ratification SLA, CAB KPIs).
3. **Complete the lifecycle records** — change models, PIR, evidence, FSC/PSO, unauthorised-change register — so the module satisfies ISO 20000-1 documented-information and ITIL practice expectations end to end.

---

## §2 Standards basis

> **Editions below are stated from reviewer knowledge and MUST be verified against the official source before use.** No copyrighted standard text is reproduced anywhere in this plan or in the delivered product — standards are used only to derive control objectives and configurable mappings. Where licensed content is required, the product must show *Placeholder — requires official licensed framework content / عنصر مؤقت — يتطلب محتوى الإطار الرسمي المرخص*.

| Framework | Edition (verify) | What it drives here |
|---|---|---|
| **ITIL 4** | current | Change Enablement practice: change authority, change models (standard/normal/emergency), CAB & ECAB, remediation planning, PIR |
| **ISO/IEC 20000-1** | :2018 | §8.5.1 change management: change records, classification, authorisation, scheduling, review; documented information |
| **ISO/IEC 27001** | :2022 (A.8.32) | Change control as a security control; segregation of duties (A.5.3) |
| **ISO 9001** | :2015 (§7.5, §8.5.6) | Control of documented information; control of changes |
| **ISO 22301** | :2019 | Continuity impact of change; freeze windows around critical periods |
| **COBIT** | 2019 (BAI06, BAI07) | Managed IT changes; change acceptance & transitioning; authorisation matrix |
| **NIST** | SP 800-128; SP 800-53 **CM-3/CM-4/CM-5** | Configuration change control, impact analysis, access restrictions for change |
| **NCA ECC** | ECC-1:2018 / **verify ECC-2:2024** | KSA change-management control expectations |
| **SAMA CSF** | 1.0:2017 (3.3.x) | KSA financial-sector change control + segregation of duties |

**Market-leader (GRC/ITSM) capability patterns** used as a functional yardstick — capability shapes only, no content copied: ServiceNow Change Management (change models, CAB workbench, risk/collision detection), Jira Service Management (change types + approval rules), BMC Helix (change calendar/blackouts), and GRC platforms such as Archer / MetricStream / OneTrust for **committee governance** (charter, membership terms, agenda packs, minutes, resolution registers).

### 2.1 Derived control objectives (used to score every epic)

1. **Authorisation proportionate to risk** — no single generic approver; the change authority is a function of type × risk.
2. **Segregation of duties** — requester ≠ approver; implementer ≠ authoriser; server-enforced, not UI-enforced.
3. **Every change is a record** — classification, justification, impact, rollback, test evidence, decision provenance, schedule, outcome, review.
4. **Emergency is a path, not a bypass** — pre-authorised, time-boxed, and **retrospectively ratified**.
5. **Decisions are attributable and immutable** — named voters, quorum proven, minutes e-signed and hash-anchored.
6. **Schedule transparency** — a forward schedule and declared outages; freeze windows respected.
7. **Nothing unauthorised survives undetected** — implemented changes reconcile to authorised changes.
8. **Tenant isolation** — every query org-scoped.
9. **Bilingual** — Arabic mandatory on user-visible records; RTL-correct exports.

---

## §3 Current state (as-built, verified 2026-07-23)

### 3.1 Backend — `module/change_mgmt` (18 files, 1,862 LoC)

| Layer | Files | LoC |
|---|---|---|
| Controller | `ChangeManagementController` — `/api/v1/changes` | 242 |
| Service | `ChangeManagementService` (**god-service**) | 853 |
| Entities | `ChangeRequest` (148, **43 DB columns**), `ChangeFreezeWindow` (69), `ChangeAuthorisationMatrix` (58), `ChangeLink` (47), `ChangeTask` (42), `ChangeApproval` (36), `ChangeComment` (30) | 430 |
| Repositories | 7 (`ChangeRequestRepository` 83 = the only substantial one) | 211 |
| Scheduler | `ChangeTaskSlaScheduler` (CH-5) | 120 |
| Docs | `package-info.java` | 16 |

**Schema `change_mgmt` — 7 tables:** `change_requests` (43 cols), `change_approvals`, `change_authorisation_matrix`, `change_comments`, `change_freeze_window` (16 cols), `change_links`, `change_tasks`.

**Endpoints (~30)** all guarded `hasPermission('CHANGE', …)` after CH-1/V301: CRUD · `POST /{id}/transition` · approvals GET/POST · tasks GET/POST/PUT/DELETE · links GET/POST/DELETE + `/links/by-entity` · **committee**: `POST /{id}/refer-to-committee`, `GET /{id}/committee`, `POST /{id}/committee/resolution` · comments GET/POST · `/analytics` · `/authorisation-matrix` + `/{id}/authorisation` · freeze-windows GET/POST/DELETE · `/calendar` · `/{id}/conflicts`.

**Permissions (V301):** `CHANGE` × `READ │ WRITE │ APPROVE │ DELETE │ EXPORT`.

**Lifecycle** — `NORMAL_TRANSITIONS`, a 13-entry map over: `DRAFT → SUBMITTED → IMPACT_ASSESSED → CAB_PENDING → CAB_APPROVED│CAB_REJECTED → SCHEDULED → IMPLEMENTING → IMPLEMENTED → POST_REVIEW → CLOSED`, plus `EMERGENCY_APPROVED` fast-path, `ROLLED_BACK`, `CANCELLED`, and `CAB_REJECTED → DRAFT` rework. Guards in place: CAB rubber-stamp block (a recorded REJECT cannot become `CAB_APPROVED`), vote-window restriction (votes only in `IMPACT_ASSESSED│CAB_PENDING`), Track D committee gate, mandatory PIR summary before `CLOSED` (CH-11), freeze/conflict checks, F8 `CHANGE_*` events.

**Outbound coupling (exact, verified):**
```
com.tss.grc.common.tenant.TenantContext                        (platform — legitimate)
com.tss.grc.module.platform.auditlog.{service,entity,repository} (platform — legitimate)
com.tss.grc.module.platform.automation.event.DomainEvent        (platform — legitimate)
com.tss.grc.module.admin.committee.entity.CommitteeReferral      ← peer module, no port
com.tss.grc.module.admin.committee.service.CommitteeReferralService ← peer module, no port
com.tss.grc.module.incident.dto.{IncidentRequest,IncidentResponse}  ← peer module, no port
com.tss.grc.module.incident.service.IncidentService                 ← peer module, no port
```
**Absent entirely:** any reference to `Workflow*`, `Evidence*`, `Attachment*` or `Export*` inside the package.

### 3.2 Committee engine — `module/admin/committee` (47 files, 4,638 LoC)

Entities: `Committee`, `CommitteeMeeting`, `CommitteeMeetingAttendee`, `CommitteeMeetingPaper`, `CommitteeResolution`, `CommitteeResolutionVote`, `CommitteeActionItem`, `CommitteeStandingItem`, `CommitteeMemberTerm`, `CommitteeMemberSkill`, `CommitteeTemplate`, `CommitteeFormTemplate`, `CommitteeReferral`, `CommitteeGateMap`.
Services: `CommitteeService` (393), `CommitteeCalendarService` (286), `CommitteeFormExportService` (266), `CommitteeMemberRegistryService` (249), `CommitteeActionItemService` (236), `CommitteeResolutionService` (214), `CommitteeScheduledSweep` (175), `CommitteeReferralService` (166), `CommitteeMeetingPapersService` (160), `CommitteeCatalogService` (162), `CommitteeSqlGuards`.
**14 tables** in schema `admin`. Meeting states `SCHEDULED → IN_PROGRESS → COMPLETED → MINUTES_APPROVED`; resolutions carry `outcome ∈ {APPROVED, REJECTED, DEFERRED, WITHDRAWN}`; per-member votes `FOR│AGAINST│ABSTAIN│RECUSED`; deterministic quorum; COI-forced recusal; minutes e-signed via `SignatureService` (`platform.signatures`).

**Conclusion:** the meeting machinery is strong and generic. CAB should *consume* it — §5.3.

### 3.3 Frontend

| File | LoC | Note |
|---|---|---|
| `pages/changes/ChangesPage.tsx` | **981** | Register + Kanban + Workflow + Analytics + Integrations + Governance + Comms in one component |
| `pages/changes/GovernancePanel.tsx` | 196 | CH-17 matrix/freeze surfacing |
| `pages/changes/mockChanges.ts` | 132 | **live mock file still in the tree** |
| `components/ui/LinkedChangesPanel.tsx` | 75 | inverse links, reused by other modules |
| `components/intake/ChangeRiskPanel.tsx` + `lib/changeRisk.ts` | 236 | intake-side risk scoring |

### 3.4 Tests

`ChangeManagementServiceTest`, `ChangeTaskSlaSchedulerTest`, `reporting/ChangeRegisterExportTest`. **No architecture/boundary test exists** — the only ratchet is `ControllerAuthorizationRatchetTest` (`BASELINE_UNGUARDED_WRITE_ENDPOINTS = 21` (was 62 when this plan was written)).

---

## §4 Gap register

Severity: **C**ritical (blocks a regulated deployment claim) · **H**igh · **M**edium · **L**ow.

| ID | Gap | Sev | Epic |
|---|---|---|---|
| G-1 | 853-LoC god-service; no sub-packages; peer-module coupling with no ports; no boundary test | **H** | CE-1 |
| G-2 | No **change model** catalogue — `STANDARD` is an unbacked string, so "pre-approved" is a claim with no record | **H** | CE-2 |
| G-3 | No **CAB charter/ToR**, no chair/secretary roles, no CAB-specific quorum policy | **H** | CE-3 |
| G-4 | **Agenda is manual** — nothing assembles the CAB agenda from changes awaiting authorisation; no papers pack per change | **C** | CE-4 |
| G-5 | Decision vocabulary too narrow — no DEFER / MORE_INFO / **approve-with-conditions**; conditions not recorded or enforced before implementation | **H** | CE-5 |
| G-6 | **ECAB ratification not enforced** and has no SLA — emergency is effectively a bypass once implemented | **C** | CE-6 |
| G-7 | **PIR is a string**, not a record — no findings, no corrective actions, no lessons register, no link to CAPA | **H** | CE-7 |
| G-8 | No **FSC/PSO** — no published forward schedule, no declared outage, collision detection unpublished | **M** | CE-8 |
| G-9 | **No evidence/attachment integration at all** — test results, approvals, back-out proof are free text; ISO 20000/9001 documented-information not satisfiable | **C** | CE-9 |
| G-10 | Change authorisation does not use the **platform workflow engine** adopted by 14 other modules — inconsistent task/SLA/escalation behaviour | **M** | CE-10 |
| G-11 | ~~**Unauthorised changes undetectable** — no reconciliation of implemented vs authorised~~ ✅ **CLOSED** S8f/CE-11 (V355) | **H** | CE-11 |
| G-12 | No ITIL KPI pack (success rate, emergency ratio, failed-change rate, lead time, CAB throughput/quorum stats) | **M** | CE-12 |
| G-13 | 981-LoC FE page; no CAB workspace; live mock file in tree | **H** | CE-13 |
| G-14 | No agenda pack / minutes / decision-register exports for change governance | **M** | CE-14 |
| G-15 | No CAB roles in RBAC; `CHANGE_APPROVE` does not distinguish chair vs member vs observer | **M** | CE-15 |

---

## §5 Target architecture — module separation (CE-1)

### 5.1 Package layout

`module/change_mgmt` → **`module/change`** (schema name **stays** `change_mgmt` — renaming a schema is a destructive, high-risk operation with no functional benefit; §15.3).

```
module/change/
├── package-info.java              module contract + allowed dependencies (documented)
├── request/                       the change record itself
│   ├── controller/ ChangeController                 (/api/v1/changes)
│   ├── entity/     ChangeRequest, ChangeComment, ChangeLink
│   ├── repository/ …
│   └── service/    ChangeRequestService             CRUD + query + links + comments
├── lifecycle/                     the state machine, isolated and unit-testable
│   ├── ChangeStateMachine         transition table + guards (pure, no I/O)
│   ├── ChangeTransitionService    orchestration: guard → mutate → audit → F8
│   └── guard/                     FreezeWindowGuard, ConflictGuard, PirRequiredGuard,
│                                  CabDecisionGuard, ConditionsSatisfiedGuard
├── authority/                     who may authorise what
│   ├── entity/     ChangeAuthorisationMatrix
│   └── service/    ChangeAuthorityService           type × risk → gate + min_approvals
├── model/                         CE-2 change models / templates
├── cab/                           CE-3…CE-6 CAB profile + workspace over the committee engine
│   ├── controller/ CabController, CabMeetingController
│   ├── entity/     CabProfile, CabAgendaItem, ChangeDecision, ChangeDecisionCondition
│   └── service/    CabProfileService, CabAgendaService, CabDecisionService,
│                   EcabRatificationService
├── schedule/                      CE-8 FSC / PSO / freeze / conflicts
├── task/                          ChangeTask + ChangeTaskSlaScheduler
├── pir/                           CE-7 post-implementation review
├── assurance/                     CE-11 unauthorised-change reconciliation
├── analytics/                     CE-12 KPI service
└── spi/                           ⭐ OUTBOUND PORTS (interfaces only — no peer imports)
    ├── CommitteePort              refer / link resolution / read decision
    ├── IncidentPort               raise an incident on a failed change
    ├── EvidencePort               attach + verify evidence (CE-9)
    └── WorkflowPort               raise/complete an authorisation request (CE-10)
```

### 5.2 Ports & adapters — the mechanism that makes separation real

`module/change/**` may import **only**: itself, `common.*`, `security.*`, `platform.*` (audit, automation events, tenant, signatures), and its own `spi` interfaces. **It may not import any other `module.<peer>` package.**

Adapters live *outside* the module, wiring the port to the peer:

```
module/change_adapters/
├── CommitteeAdapter   implements CommitteePort  → admin.committee.CommitteeReferralService
├── IncidentAdapter    implements IncidentPort   → incident.IncidentService
├── EvidenceAdapter    implements EvidencePort   → evidence.*
└── WorkflowAdapter    implements WorkflowPort   → workflow.*
```

Registered as `@Component`; injected into change services as the **interface**. Ports are declared `@Autowired(required = false)`-friendly (nullable) so unit tests and `@InjectMocks` construction keep working — the established pattern from Track D.

**Benefits:** Change Enablement compiles against 4 small interfaces instead of 4 peer services; each port is trivially mockable; a peer refactor cannot break the module; the dependency direction is explicit and testable.

### 5.3 CAB = profile over the committee engine (decision, not a new engine)

```
Committee (admin.committees)                    ← generic body: members, terms, skills, quorum
   └── CabProfile (change_mgmt.cab_profile)     ← CAB-specific: charter, ToR, gate (CAB│ECAB),
                                                  scope, quorum policy, standing schedule,
                                                  chair/secretary, review cadence
CommitteeMeeting (admin.committee_meetings)     ← generic meeting: attendance, quorum, minutes, e-sign
   └── CabAgendaItem (change_mgmt.cab_agenda_item) ← binds a change_request to a meeting slot,
                                                     with order, presenter, time-box, paper ref
CommitteeResolution + votes                     ← generic decision + attributable votes
   └── ChangeDecision (change_mgmt.change_decision) ← the change-facing decision record:
                                                      outcome, conditions, effective window,
                                                      resolution_id provenance
```

Rationale: one meeting engine, one minutes format, one e-signature trail, one action-item register — platform-wide. The CAB gets its own **workspace and vocabulary** without forking the machinery. `CommitteeGateMap` (V310) already maps `CAB`/`ECAB` → committee per tenant; `CabProfile` enriches that mapping rather than replacing it.

### 5.4 Boundary enforcement test (ships with CE-1)

New `ChangeModuleBoundaryTest`, modelled on `ControllerAuthorizationRatchetTest`:

1. Scan `module/change/**/*.java` imports; **fail** on any `com.tss.grc.module.<peer>` that is not `platform`.
2. Assert every `spi` interface has ≥ 1 adapter implementation outside the module.
3. Assert `lifecycle.ChangeStateMachine` imports no Spring/JPA type (keeps it pure).
4. Ratchet the god-service: assert no class in `module/change` exceeds **400 LoC** (baseline captured at extraction; only lowerable).

---

## §6 Domain model additions

New tables all in schema `change_mgmt`, all with `organisation_id`, `is_deleted`, `created_at/by`, bilingual `*_en`/`*_ar` where user-visible.

| Table | Migration | Purpose | Key columns |
|---|---|---|---|
| `change_model` | *(see §0)* | Change model / template catalogue | `code`, `name_en/ar`, `change_type`, `risk_default`, `gate_override`, `pre_approved` (bit), `authorised_by`, `authorised_until`, `standard_steps` (JSON), `rollback_template`, `test_template`, `max_lead_time_hours`, `review_cadence`, `status` |
| `change_model_usage` | *(see §0)* | Which change used which model (audit + KPI) | `change_id`, `model_id`, `applied_at`, `deviation_notes` |
| `cab_profile` | *(see §0)* | CAB as a governed body | `committee_id` (→ `admin.committees`), `gate` (CAB│ECAB), `charter_en/ar`, `terms_of_reference`, `scope_json`, `quorum_min_members`, `quorum_requires_chair` (bit), `min_approvals`, `chair_member_id`, `secretary_member_id`, `standing_cadence`, `notice_period_hours`, `review_due_date`, `status` |
| `cab_member_role` | *(see §0)* | Role of a member **on this CAB** | `cab_profile_id`, `member_id`, `role` (CHAIR│VICE_CHAIR│SECRETARY│MEMBER│OBSERVER│DELEGATE), `delegate_for_member_id`, `voting` (bit), `from_date`, `to_date` |
| `cab_agenda_item` | *(see §0)* | Change ↔ meeting binding | `meeting_id`, `change_id`, `seq`, `presenter_name`, `time_box_minutes`, `item_type` (AUTHORISATION│RATIFICATION│REVIEW│INFORMATION), `paper_id`, `status` (PROPOSED│TABLED│DEFERRED│WITHDRAWN│DECIDED) |
| `change_decision` | *(see §0)* | Change-facing decision record | `change_id`, `meeting_id`, `resolution_id`, `outcome` (APPROVED│APPROVED_WITH_CONDITIONS│REJECTED│DEFERRED│MORE_INFO_REQUIRED), `decided_at`, `vote_for/against/abstain`, `quorum_met` (bit), `rationale_en/ar`, `effective_from`, `effective_to`, `superseded_by` |
| `change_decision_condition` | *(see §0)* | Enforceable conditions | `decision_id`, `seq`, `condition_en/ar`, `owner_name`, `due_at`, `satisfied_at`, `satisfied_by`, `evidence_id`, `status` (OPEN│SATISFIED│WAIVED) |
| `change_ratification` | *(see §0)* | ECAB retrospective ratification | `change_id`, `required_by` (datetime), `referred_at`, `meeting_id`, `resolution_id`, `outcome`, `ratified_at`, `breach` (bit), `breach_reason` |
| `change_pir` | *(see §0)* | Real PIR record | `change_id`, `review_date`, `reviewer_name`, `outcome` (SUCCESS│PARTIAL│FAILED│ROLLED_BACK), `objectives_met` (bit), `on_time` (bit), `within_scope` (bit), `incidents_caused`, `summary_en/ar`, `approved_by`, `approved_at` |
| `change_pir_finding` | *(see §0)* | PIR findings → actions | `pir_id`, `seq`, `finding_en/ar`, `severity`, `root_cause`, `capa_id` (→ `sust.corrective_action`), `action_owner`, `due_at`, `status` |
| `change_schedule_entry` | *(see §0)* | FSC + PSO | `change_id`, `window_start`, `window_end`, `entry_type` (PLANNED│PSO│MAINTENANCE), `services_affected`, `outage_expected` (bit), `outage_minutes`, `published` (bit), `published_at` |
| `change_evidence_link` | *(see §0)* | Change ↔ platform evidence | `change_id`, `evidence_id`, `purpose` (TEST_RESULT│APPROVAL│BACKOUT_PROOF│IMPLEMENTATION_LOG│PIR), `required` (bit), `verified_at`, `verified_by` |
| `change_unauthorised` | *(see §0)* | Reconciliation register | `detected_at`, `source` (MANUAL│SCAN│INTEGRATION), `asset_ref`, `description_en/ar`, `matched_change_id`, `status` (OPEN│MATCHED│FALSE_POSITIVE│ESCALATED), `incident_id`, `escalated_to` |

**`change_requests` additions** (*additive columns only — numbers per §0*): `change_model_id`, `current_decision_id`, `ratification_id`, `conditions_open_count` (denormalised for the guard), `unauthorised_flag`.

**CHECK constraints** on every new enum column (the CH-2/V300 convention) — and remember the `ck_ca_source` landmine: **any new CAPA `source_type` (e.g. `CHANGE_PIR`) must be added to the CHECK or inserts 500.**

---

## §7 Change lifecycle v2 (CE-5, CE-6, CE-9)

### 7.1 States

Existing 13-entry map is **kept** (no renames — the FE, F8 rules, analytics and audit history depend on the literals). Additions:

| New state | From | To | Why |
|---|---|---|---|
| `CAB_DEFERRED` | `CAB_PENDING` | `CAB_PENDING`, `DRAFT`, `CANCELLED` | Committee deferred the item — distinct from rejection; keeps it in the queue for the next meeting |
| `MORE_INFO_REQUIRED` | `CAB_PENDING` | `IMPACT_ASSESSED`, `CANCELLED` | Committee wants more information; returns to the requester without a rejection stigma |
| `PENDING_RATIFICATION` | `IMPLEMENTED` (emergency only) | `POST_REVIEW`, `CLOSED`‡ | Emergency changes are implemented, then must face ECAB |

‡ `CLOSED` only once `change_ratification.ratified_at` is set — otherwise 409 (CE-6).

### 7.2 New guards (all server-side 409/400, all live-probed)

| Guard | Rule |
|---|---|
| `ConditionsSatisfiedGuard` | `SCHEDULED → IMPLEMENTING` is **409** while any `change_decision_condition` is `OPEN` on the effective decision |
| `DecisionValidityGuard` | `CAB_APPROVED → SCHEDULED` is **409** if the decision's `effective_to` has passed (approval expiry — a re-approval is required) |
| `RatificationGuard` | Emergency change cannot reach `CLOSED` without a ratification decision; `required_by = implementation_ended + SLA` (default 5 business days, per-tenant) |
| `PirRecordGuard` | `POST_REVIEW → CLOSED` requires a `change_pir` row (upgrades CH-11's string check; the string stays as a fallback for legacy rows) |
| `EvidenceRequiredGuard` | Where the change model marks evidence mandatory, `IMPLEMENTED` requires ≥ 1 verified `change_evidence_link` of the required purpose |
| `ModelComplianceGuard` | A `STANDARD` change must reference a `change_model` with `pre_approved = 1` and a live `authorised_until`; otherwise it is not standard → routed as `NORMAL` |

### 7.3 Emergency (ECAB) flow — CE-6

```
DRAFT ──emergency──▶ EMERGENCY_APPROVED   (authority = ECAB per matrix; requires justification + named authoriser)
      └─▶ IMPLEMENTING ─▶ IMPLEMENTED ─▶ PENDING_RATIFICATION
                                             │  auto-referral to the ECAB committee (mode = RATIFICATION)
                                             │  SLA clock starts; scheduler flags breach
                                             ▼
                                    ECAB meeting ─▶ ChangeDecision(RATIFIED│REJECTED_RETROSPECTIVELY)
                                             ▼
                                        POST_REVIEW ─▶ CLOSED
```
A retrospective rejection does **not** un-implement the change — it records a governance breach, raises a PIR finding, and (per §14) can auto-raise a CAPA.

---

## §8 CAB management cycle (CE-3)

The **board** lifecycle, distinct from the meeting lifecycle:

```
PROPOSED ─▶ CHARTERED ─▶ ACTIVE ─▶ UNDER_REVIEW ─▶ ACTIVE
                                        └─▶ SUSPENDED ─▶ DISSOLVED
```

| Stage | What the product must do |
|---|---|
| **Charter** | Capture ToR (`charter_en/ar`), scope (`scope_json`: change types, risk bands, categories, business services in remit), gate (`CAB`/`ECAB`), standing cadence, notice period |
| **Constitute** | Assign members from the committee registry with **CAB roles** (chair, vice-chair, secretary, member, observer, delegate) and voting rights; enforce ≥ 1 chair and ≥ 1 secretary |
| **Quorum policy** | `quorum_min_members`, `quorum_requires_chair`, `min_approvals` — evaluated deterministically by the committee engine, surfaced as a CAB profile setting |
| **Terms** | Reuse `committee_member_terms`; surface expiring terms (≤ 60 days) on the CAB workspace and in the scheduled sweep |
| **Delegation** | A `DELEGATE` row names `delegate_for_member_id`; the delegate votes only when the principal is absent (enforced in vote recording) |
| **Periodic review** | `review_due_date` + cadence; overdue review raises a governance task (workflow engine) and shows on the CAB workspace |
| **Suspend/dissolve** | Blocked while the CAB has open agenda items or unratified emergency changes (409 with the count) |

**SoD carried forward from Track D:** the change requester and change owner are force-**RECUSED** on their own item (`CommitteeReferral.recusal_names`); a chair cannot be the sole approver of their own change.

---

## §9 CAB meeting management process (CE-4) — the centre of gravity

### 9.1 Meeting lifecycle

```
DRAFT_AGENDA ─▶ AGENDA_PUBLISHED ─▶ IN_PROGRESS ─▶ DECISIONS_RECORDED
                                                       ─▶ MINUTES_DRAFT ─▶ MINUTES_APPROVED (e-signed)
                                                                                 └─▶ PUBLISHED
```
Mapped onto the engine's `SCHEDULED → IN_PROGRESS → COMPLETED → MINUTES_APPROVED`; the extra CAB states are agenda/publication phases held on `cab_agenda_item` + `CabProfile`, so **no change to the generic engine's state literals**.

### 9.2 Creation & agenda auto-build (the feature that closes G-4)

1. **Create meeting** — from the CAB's standing cadence (next occurrence pre-filled) or ad hoc. Reuses `CommitteeService` meeting creation; `cab_profile_id` recorded.
2. **Auto-build the agenda** — `CabAgendaService.buildAgenda(meetingId)`:
   - **Candidate query**: changes in `CAB_PENDING` (+ `IMPACT_ASSESSED` opt-in) whose authority gate resolves to this CAB's gate and whose category/risk falls inside `scope_json`, org-scoped.
   - **Plus** `PENDING_RATIFICATION` emergency changes (item_type `RATIFICATION`) — ratification is never forgotten.
   - **Plus** overdue PIRs and open decision conditions (item_type `REVIEW`) as standing items.
   - **Ordering**: risk desc → scheduled_start asc → age desc; time-boxes from the change model (`max_lead_time_hours`/default).
   - **Notice check**: warns when publication would breach `notice_period_hours`.
   - Idempotent: re-running reconciles (adds new candidates, never duplicates, never silently drops a tabled item).
3. **Papers pack** — for each item, generate/attach a paper via `CommitteeMeetingPapersService`: change record, classification & risk, impact, implementation/test/rollback plan, linked risks/incidents/assets, prior decisions, evidence list.
4. **Publish agenda** — snapshot + notify attendees (notification service); after publication, late additions are marked `LATE_ADDITION` (visible in minutes — an audit expectation).
5. **Run the meeting** — attendance capture → deterministic quorum (engine) → per-item deliberation.
6. **Record decisions** — per item, a `CommitteeResolution` + per-member votes (engine, with COI recusal) → `ChangeDecision` written back to the change (CE-5), including conditions.
7. **Minutes** — draft → approve → **e-signed** with a SHA-256 of the minutes body (existing `SignatureService` path, Track D S4) → publish.
8. **Actions** — unresolved matters become `CommitteeActionItem`s with owners/due dates; PIR findings can spawn CAPAs.

### 9.3 Records produced (CE-14, and the ISO 20000/9001 answer)

| Record | Where | Export |
|---|---|---|
| Agenda (+ late additions) | `cab_agenda_item` snapshot | **Agenda pack** PDF/DOCX (RTL) |
| Papers per change | `committee_meeting_papers` | inside the agenda pack |
| Attendance & quorum proof | `committee_meeting_attendee` | in minutes |
| Per-member votes incl. recusals | `committee_resolution_vote` | in minutes + decision register |
| Decision + conditions | `change_decision`, `change_decision_condition` | **Decision register** XLSX/PDF |
| Minutes + e-signature + hash | `committee_meetings` + `platform.signatures` | **Minutes** PDF (RTL, signed) |
| Action items | `committee_action_items` | action register XLSX |
| Ratifications & breaches | `change_ratification` | governance-breach report |

All exports go through the existing RTL-correct generators (Amiri font, bilingual titles) — the platform-wide Arabic export conformance already established.

---

## §10 RBAC & SoD (CE-15)

New permissions (CE-15 — see §0 for the number), catalogued in `iam.permissions` and granted per the 55-role persona matrix:

| Permission | Holder intent |
|---|---|
| `CHANGE / CAB_MANAGE` | Charter/constitute a CAB, edit profile, manage membership & roles |
| `CHANGE / CAB_SCHEDULE` | Create meetings, build & publish agendas |
| `CHANGE / CAB_DECIDE` | Record decisions/votes on CAB items (distinct from the existing `CHANGE/APPROVE`) |
| `CHANGE / RATIFY` | Record ECAB retrospective ratification |
| `CHANGE / PIR` | Create/approve PIR records |

**SoD gates (server-enforced 409, each live-probed with two users):** requester ≠ decision recorder · chair cannot solely approve own change · delegate votes only in the principal's absence · PIR approver ≠ change implementer · condition `satisfied_by` ≠ the condition's own owner where the model demands independence.

**Ratchet:** every new write endpoint carries a real `hasPermission` guard — the count must stay **≤ 21** (`ControllerAuthorizationRatchetTest`).

---

## §11 Frontend architecture (CE-13)

Split the 981-LoC page into a feature folder; **delete `mockChanges.ts`** once the register is fully canonical.

```
pages/change/
├── ChangeRegisterPage.tsx        list + filters + export
├── ChangeDetailPage.tsx          record, plans, tasks, links, comments, evidence, decisions
├── ChangeBoardPage.tsx           kanban
├── ChangeSchedulePage.tsx        FSC + PSO + freeze windows calendar
├── ChangeAnalyticsPage.tsx       KPI pack
├── cab/
│   ├── CabWorkspacePage.tsx      boards, quorum health, expiring terms, overdue reviews
│   ├── CabMeetingPage.tsx        meeting detail: attendance, quorum badge, items, minutes
│   ├── CabAgendaBuilder.tsx      ⭐ auto-build → reorder → time-box → publish
│   ├── CabDecisionPanel.tsx      per-item outcome + conditions + votes (extends ResolutionVotesModal)
│   └── CabRatificationQueue.tsx  emergency changes awaiting ratification, SLA countdown
├── pir/ PirFormModal.tsx, PirRegisterPage.tsx
└── components/ ChangeAuthorityBadge, ConditionsChecklist, DecisionProvenanceCard
```

Every screen bilingual (Arabic primary, RTL), Hijri dual-dates on scheduling/meeting dates (shared `formatHijri`), `problemMessage()` for field-named validation toasts, and **no `window.prompt/confirm`** (CH-8 convention: real `FormModal`s).

---

## §12 Analytics & KPIs (CE-12)

Backed by SQL over the new records, surfaced on `ChangeAnalyticsPage` + the ESG/exec dashboard strip:

**Change:** success rate (`SUCCESS ÷ closed`) · failed-change rate · **emergency ratio** (emergency ÷ all — a headline ITIL/audit metric) · rollback rate · unauthorised-change count · median lead time (submit → CAB decision) and (decision → implemented) · schedule adherence (implemented inside the approved window) · PIR completion & on-time rate · open decision conditions overdue.
**CAB:** meetings held vs scheduled · **quorum-met rate** · items decided per meeting · deferral rate · average time-in-queue before decision · ratification SLA breaches · agenda-publication notice compliance · member attendance rate · expiring terms.

---

## §13 Integrations

| Peer | Direction | Mechanism |
|---|---|---|
| Committee engine | out | `CommitteePort` → `CommitteeAdapter` (Track D services) |
| Incident | out | `IncidentPort` → adapter (failed/rolled-back change raises an incident — replaces the direct `IncidentService` import) |
| Evidence | out | `EvidencePort` → adapter (CE-9); SHA-256 + four-eyes verification already in the subsystem |
| Workflow engine | out | `WorkflowPort` → adapter (CE-10) — change becomes the 15th adopting module |
| CAPA (`sust.corrective_action`) | out | PIR finding → CAPA; **must add `CHANGE_PIR` to the `ck_ca_source` CHECK** |
| Risk / Control / Asset / Policy / BCM / Audit finding | both | existing generic `change_links` (12 entity types × 7 relationship types) |
| F8 automation | out | existing `CHANGE_*` events + new `CHANGE_CAB_DEFERRED`, `CHANGE_CONDITION_OVERDUE`, `CHANGE_RATIFICATION_DUE/BREACHED`, `CHANGE_UNAUTHORISED_DETECTED` (seed rules per V305 pattern) |
| Reporting/export | out | keep in `module/reporting` (shared generators); add agenda/minutes/decision-register methods |
| Notifications | out | agenda published, papers ready, vote requested, ratification due, condition overdue |

---

## §14 Migration & data plan

1. **Numbering** — the values mapped in §0 (**V322–V331**, delivery order); re-check §4 of the unified plan immediately before creating a file. Each: idempotent guards (`IF OBJECT_ID … IS NULL`, `IF COL_LENGTH … IS NULL`, `NOT EXISTS` backfills), `GO`-separated, applied to dev **twice** via `apply_migration.py`, reverse recipe documented in the header.
2. **Additive only** — no column drops, no schema rename, no data deletion. Every new column NULLable or DEFAULTed. (A `change_mgmt` → `change` **schema** rename is explicitly **rejected**: destructive, requires an outage, breaks native SQL and Flyway history for zero functional gain.)
3. **Backfills** — (a) seed a `change_model` per distinct `(change_type, category)` in use, marking existing `STANDARD` rows' models `pre_approved = 0` until a human authorises them (fail-closed, never auto-bless); (b) create a `cab_profile` for each existing `committee_gate_map` row; (c) synthesise `change_pir` rows from the CH-11 PIR summary strings, flagged `source = LEGACY_SUMMARY`; (d) create `change_ratification` rows for closed emergency changes marked `breach = 1, breach_reason = 'PRE-CE6 LEGACY'` — the historical truth, visible rather than hidden.
4. **Package move (CE-1)** — a single mechanical commit (imports + `@ComponentScan` reach + test packages), no behaviour change, full suite green before and after. FE untouched by CE-1 (API paths unchanged: `/api/v1/changes` stays).
5. **Prod** — Flyway applies on the next jar (deployed baseline is current at **V312**). No manual DB step.

---

## §15 Delivery phases

Each slice: build → targeted unit tests → migration applied ×2 to dev → **live probe** (guard 403s, SoD 409s, two users `admin` + `so_officer`) → commit. Full suite before any merge. **No merge without the explicit word "merge".**

| Phase | Epics | Rationale |
|---|---|---|
| **P0 — Extract** | CE-1 | Do the mechanical separation *first*, while the module is small. Every later epic lands in a clean package. Zero functional change → lowest-risk starting point. |
| **P1 — Governed CAB** | CE-3, CE-15 | A constituted board with roles/quorum/charter is the prerequisite for meaningful meetings. |
| **P2 — Meetings & decisions** | CE-4, CE-5, CE-14(partial) | The heart: agenda auto-build, papers, decisions with conditions, agenda/minutes exports. |
| **P3 — Emergency & review** | CE-6, CE-7 | Ratification SLA + real PIR records close the two Critical governance gaps. |
| **P4 — Records & engine** | CE-9, CE-10, CE-2 | Evidence integration, workflow-engine adoption, change-model catalogue. |
| **P5 — Schedule & assurance** | CE-8, CE-11, CE-12, CE-14(rest) | FSC/PSO, unauthorised-change reconciliation, KPI pack, remaining exports. |
| **P6 — FE & docs** | CE-13, CE-16 | The CAB workspace + agenda builder land incrementally through P1–P5; P6 completes the split and the docs. |

> FE slices ship **alongside** their backend epic (the house pattern — no backend-only phases that leave dead endpoints), with CE-13's structural split done in P0/P1 so later panels have a home.

---

## §16 Test plan

**Unit** — `ChangeStateMachineTest` (pure transition table, every new state/guard, both allow and deny) · `CabAgendaServiceTest` (candidate selection, scope filter, ordering, idempotent rebuild, ratification inclusion) · `CabDecisionServiceTest` (outcome mapping, conditions, quorum/min-approval arithmetic) · `EcabRatificationServiceTest` (SLA computation, breach flag) · `ChangeModuleBoundaryTest` (§5.4) · port-adapter contract tests with mocked peers.

**Live probes** (Python urllib + pyodbc, through the Vite proxy for browser-equivalent paths, self-cleaning `[REVIEW-TEST]` rows with CHARINDEX deletes):

| # | Probe | Expected |
|---|---|---|
| 1 | Build an agenda with 3 `CAB_PENDING` changes | 3 items, risk-ordered, papers attached |
| 2 | Re-run the build | idempotent — no duplicates, tabled item preserved |
| 3 | Out-of-scope change (risk band outside `scope_json`) | **not** on the agenda |
| 4 | Requester votes on own item | **RECUSED**, excluded from the tally |
| 5 | Approve below quorum / below `min_approvals` | **409**, change stays `CAB_PENDING` |
| 6 | Approve with conditions, then `SCHEDULED → IMPLEMENTING` | **409** while a condition is OPEN; **200** after it is satisfied with evidence |
| 7 | Emergency change → `CLOSED` without ratification | **409** |
| 8 | Ratify late | `breach = 1`, breach visible in the governance report |
| 9 | `POST_REVIEW → CLOSED` with no PIR record | **409**; **200** after a PIR is filed |
| 10 | `STANDARD` change with no pre-approved model | routed as `NORMAL` (not silently auto-approved) |
| 11 | Non-CAB user hits `POST /cab/meetings/{id}/agenda` | **403** |
| 12 | Cross-tenant read of another org's CAB/meeting/decision | **404/403**, zero rows |
| 13 | Agenda pack / minutes / decision register exports | valid PDF/DOCX/XLSX magic bytes, Arabic shaped RTL, e-signature present in minutes |
| 14 | Legacy non-referred change | byte-identical legacy behaviour (non-regression) |

---

## §17 Risks & rollback

| Risk | Likelihood | Mitigation |
|---|---|---|
| Package move breaks Spring component scan / DevTools | Med | Mechanical commit, full suite before+after, `@ComponentScan` verified at boot; revert = one `git revert` (no schema change) |
| Agenda auto-build floods a meeting | Med | Scope filter + configurable max items + time-box budget warning; `LATE_ADDITION` marking |
| New guards block real work in production | Med | Every guard has a per-tenant enable flag defaulting **off** for existing tenants, **on** for new ones; enable after a dry-run report shows what *would* have blocked |
| Legacy emergency changes flagged as breaches alarms users | High | Flagged explicitly `PRE-CE6 LEGACY`, excluded from KPI trend lines, documented in §14.3(d) |
| Double meeting engine drift | Low | Rejected by design (§5.3) — CAB is a profile, not a fork |
| ROWVERSION double-flush on decision write-back | Med | Native stamp first, then mutate+save **once** (the established landmine rule) |
| Committee engine changes ripple into CAB | Low | `CommitteePort` isolates it; contract tests fail loudly |

**Rollback per slice:** each migration header carries its reverse recipe; all additive, so `git revert` of the code alone restores behaviour with the (unused) columns left in place — the expand-contract pattern already used by V233/V312.

---

## §18 Out of scope (explicit non-goals)

- Full **CMDB/asset-discovery** integration (only `change_links → ASSET` and manual `change_unauthorised` entry; automated scan sources are a later integration epic).
- **Release & deployment management** as a separate practice (ITIL treats it distinctly; changes here reference releases only by free text/link).
- Real-time **collision detection against external ITSM tools** (ServiceNow/Jira adapters exist as stubs at platform level; out of scope here).
- Renaming the `change_mgmt` **schema** (rejected — §14.2).
- Deleting `module/regchange` (**regulatory** change is a different domain; untouched).

---

## §19 Open decisions *(renumbered **CD-1…CD-7** 2026-07-25 — the previous D-1…D-7 collided with the unified plan's own D-1…D-9, which are different questions. CD-1 "CAB profile vs dedicated engine" is now **DECIDED** by §6c/ADR-013: a profile over the committee engine.)* (need the user before P1)

| # | Decision | Options | Recommendation |
|---|---|---|---|
| **CD-1** ✅ **DECIDED** | CAB modelling | (a) **Profile over the committee engine** · (b) dedicated CAB tables + own meeting engine | **(a)** — one meeting engine, one minutes/e-signature trail platform-wide; (b) duplicates 4,638 LoC and splits the audit story |
| **CD-2** | Agenda auto-build trigger | (a) **On demand** (secretary clicks "Build agenda") · (b) automatic on meeting creation · (c) scheduled nightly | **(a)** with a preview diff — auto-mutating an agenda a human is editing is a trust problem |
| **CD-3** | Ratification SLA default | 3 / **5** / 10 business days, per-tenant override | **5 business days**, per-tenant override; breach flags rather than blocks (blocking a closed emergency change helps nobody) |
| **CD-4** | New-guard rollout | (a) **Off for existing tenants + dry-run report** · (b) on immediately | **(a)** — see §17 |
| **CD-5** | Approval expiry | Do CAB approvals expire? **Yes**, default 30 days (`effective_to`) · No | **Yes, 30 days** — a 6-month-old approval for a since-changed system is a real audit finding |
| **CD-6** | Standard-change auto-approval | May a pre-approved model skip CAB entirely (`AUTO` gate)? | **Yes**, but only with `pre_approved = 1` **and** a live `authorised_until` **and** a named authoriser — fail-closed on expiry (§7.2 `ModelComplianceGuard`) |
| **CD-7** | Delegate voting | Allowed always · **only when principal absent** · never | **Only when principal absent** — recorded as a delegated vote in the minutes |

---

## §20 Definition of done (programme level)

- [ ] `module/change` self-contained; boundary test green; no class > 400 LoC; god-service dissolved.
- [ ] CAB constituted with charter, roles, quorum policy; expiring terms and overdue reviews visible.
- [ ] A CAB meeting can be created, its agenda **auto-built from the change queue**, papers attached, notice checked, published.
- [ ] Decisions recorded with attributable votes, quorum proof, conditions — and **written back** to the change.
- [ ] Conditions block implementation until satisfied with evidence; approvals expire.
- [ ] Emergency changes cannot close without ratification; breaches are visible, not hidden.
- [ ] PIR is a record with findings and CAPA links; closure requires it.
- [ ] Change evidence flows through the platform evidence subsystem (SHA-256, four-eyes).
- [ ] FSC/PSO published; unauthorised changes detectable and reconcilable.
- [ ] Agenda pack, minutes (e-signed), decision register export bilingually, RTL-correct.
- [ ] KPI pack live: success rate, emergency ratio, quorum-met rate, ratification breaches.
- [ ] Ratchet ≤ 21 · full suite green · every slice live-probed · Arabic mandatory throughout.
- [ ] `PROJECT_CHRONICLE.md` section + `package-info.java` contract + CAB operating runbook written.

---

**Path:** `D:\AYMAN\GRC\CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`
**Supersedes nothing** — it is the forward-looking successor to `CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (whose CH-1…CH-17 backlog is fully delivered).
**Recommended start:** answer **D-1** and **D-2**, then execute **P0 (CE-1)** — the mechanical extraction, on a feature branch, with no functional change.

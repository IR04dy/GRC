# TSS GRC Platform — Unified Implementation Plan
### خطة التنفيذ الموحّدة

> **This is the single execution plan.** It merges, validates and re-sequences two previously separate programmes into one dependency-ordered backlog:
> - `REMEDIATION_MASTER_PLAN.md` — 26 actions closing the independent assessment's 9 findings / 6 blocking conditions (**W0–W3**)
> - `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md` — 16 epics for Change Enablement separation + CAB operating model (**CE-1…CE-16**)
>
> Those two documents remain the **detail references** (domain models, table designs, guard specs, test matrices). This plan owns **sequencing, gates, migration numbering, and the corrections found while validating them.** Where they disagree with this plan, **this plan governs.**
>
> **Baseline verified 2026-07-25:** `master` @ `00ba890e` · branch @ `b9ffc20c` · worktree `fix/break-glass-sweep-tx` @ `ccc06799` · suite **2,322/0/0** · ratchet **62** · migrations V1–V312 · **next free V313**.
> **House rules** (`CLAUDE.md`): feature branch · commit per verified slice · **never merge to master without the explicit word "merge"** · migrations idempotent + applied twice to dev · `[REVIEW-TEST]` CHARINDEX cleanup · **destructive DB ops need explicit OK** · live-probe every slice · Arabic mandatory · ratchet only decreases.

---

## §1 Validation of the CE plan — judgment before merging

I re-tested the CE plan's load-bearing claims against the current code and database rather than trusting the document.

### 1.1 What held up (verified, unchanged since 2026-07-23)
| Claim | Status |
|---|---|
| `module/change_mgmt` = 18 files / **1,862 LoC**; `ChangeManagementService` = **853 LoC** | ✅ exact |
| `ChangesPage.tsx` = **981 LoC** | ✅ exact |
| No architecture/boundary test exists anywhere | ✅ still absent |
| Committee engine = 47 files / 4,638 LoC / 14 tables, with quorum, votes, recusal, e-signed minutes | ✅ |
| Outbound coupling to `admin.committee` + `incident` with no ports | ✅ |
| Zero evidence/attachment/workflow integration inside the change package | ✅ |
| **Architectural call — CAB as a *profile over* the committee engine, not a second engine** | ✅ **Correct and endorsed.** Forking would duplicate 4,638 LoC and split the minutes/e-signature audit trail |
| **Rejecting the `change_mgmt` schema rename** | ✅ **Correct.** Outage-grade, zero functional gain |

### 1.2 Eight defects found — all corrected in this plan

| # | Defect | Evidence | Correction |
|---|---|---|---|
| **X-1** | **Migration collision.** CE reserves V313–V322; the remediation plan now legitimately owns **V313** (permission catalogue) and **V314** (committee gate map) | both plans written independently | CE migrations **renumber to V315–V324** (§3) |
| **X-2** | ⛔ **RUNTIME-BREAKING: the status CHECK constraint blocks the 3 new states.** `ck_change_status` enumerates exactly the 13 existing statuses. CE §7.1 adds `CAB_DEFERRED`, `MORE_INFO_REQUIRED`, `PENDING_RATIFICATION` — but **no migration in the CE plan alters that constraint**. The first transition writing a new status would fail with a constraint violation (500), not a 409 | `SELECT definition FROM sys.check_constraints` → `ck_change_status` lists 14 values, none of them new | **New migration V315** drops + recreates `ck_change_status` with all 17 values **before** any state-machine code ships (§4, S5) |
| **X-3** | **Ratchet target stale.** CE §10 says "the count must stay **≤ 62**" | the remediation plan re-baselines to **17** after guarding 45 endpoints | CE inherits the new target: every CAB/PIR/schedule endpoint guarded; ratchet must remain **≤ 17** |
| **X-4** | **Foundation risk.** CE-4/CE-5 extend the committee engine, which has **zero automated tests** (no test anywhere matching *committee/meeting/resolution*) | independent assessment F-2 | **Hard gate:** committee test suite (S3) must be green before CE-4 starts |
| **X-5** | **Unconfigured dependency.** CE-4's agenda candidate query resolves a change's gate via `committee_gate_map` — which has **0 rows**, so the auto-built agenda would return **empty** and look like a bug | assessment F-4 | **Hard gate:** gate-map seed + config-health check (S4) precedes CE-4 |
| **X-6** | **Internal ordering contradiction.** CE §16 probe #6 requires a condition "satisfied **with evidence**" — but conditions ship in P2 (CE-5) while evidence integration is P4 (CE-9) | CE plan §15 vs §16 | Split: CE-5 ships conditions with an **optional** evidence reference; the *evidence-mandatory* variant of the guard activates in S8 with CE-9. Probe #6 splits into 6a (condition blocks) and 6b (evidence satisfies) |
| **X-7** | **`mockChanges.ts` deletion is not isolated.** CE-13 says delete it; it has **3 importers** — `ChangesPage.tsx`, **`lib/searchIndex.ts`** (global search) and **`pages/dashboard/widgets.tsx`** (executive dashboard) | grep | CE-13 depends on **W2-3**; deleting the file without those two breaks the build |
| **X-8** | **Stale baselines.** "suite 2316", "next free V313" | now 2,322 and V313 is taken | corrected throughout |

**Also validated as feasible:** CE-15's five new permission *actions* (`CAB_MANAGE`, `CAB_SCHEDULE`, `CAB_DECIDE`, `RATIFY`, `PIR`). `ModulePermissionEvaluator` upper-cases the action string and looks it up in the catalogue, so arbitrary action names work — **provided the catalogue rows and grants ship in the same migration as the guards**, exactly as `TRIAGE`/`BREAK_GLASS` already do. Without them the guards deny everyone.
**Minor correction:** the CE plan calls `change_type='STANDARD'` a "free string". The *value* is CHECK-constrained (`ck_change_type`); what is missing is the **model catalogue behind it**. The gap is real; the wording was imprecise.

### 1.3 Judgment
The CE plan is **technically sound and worth executing** — its architecture call is right, its domain model is complete, and its factual base is accurate to the line. But it was written as if it were the only programme in flight, and it **assumes a tested, configured committee engine that does not yet exist**. Executed as written, CE-5/CE-6 would fail at runtime on X-2, CE-4 would silently return empty agendas on X-5, and it would collide with the remediation migrations on X-1. Merged and re-sequenced as below, it is safe.

---

## §2 Unified stage model

Ten stages. **Each stage has an entry gate and an exit proof.** No stage starts until its gate is satisfied.

```
S0 Deploy the pending fixes ──► S1 Authorisation foundation ──► S2 Guard the platform
                                          │
                                          ▼
                             S3 Assure the governance core ──► S4 Configure the gate
                                          │                          │
                                          ▼                          ▼
                          S5 Extract the change module ──► S6 Constitute the CAB
                                                                     │
                                                                     ▼
                                              S7 Meetings, decisions, emergency, PIR
                                                                     │
                          S8 Records, evidence, schedule, assurance ◄┘
                                          │
                                          ▼
                          S9 Truthful UI + evidence-based scope + strategy
```

| Stage | Theme | Contains | Entry gate |
|---|---|---|---|
| **S0** | Deploy pending fixes | W0-1, W0-2, W0-3, W0-7 | user's "merge" + one elevated restart |
| **S1** | Authorisation foundation | W0-4 (**V313**), W0-5, W0-6 | — |
| **S2** | Guard the platform | W1-3 (45 endpoints, ratchet → 17), W1-6 | S1 complete (catalogue exists) |
| **S3** | Assure the governance core | W1-1, W1-2 | — (start in parallel with S0/S1) |
| **S4** | Configure the gate | W1-4 (**V314**), W1-5 | S3 green |
| **S5** | Extract the change module | CE-1, **V315** (status CHECK), CE-13a (FE skeleton) | S2 + S3 green |
| **S6** | Constitute the CAB | CE-3 (**V316**), CE-15 (**V317**) | S4 + S5 complete; **D-1, D-2 answered** |
| **S7** | Meetings → decisions → emergency → review | CE-4 (**V318**), CE-5 (**V319**), CE-6 (**V320**), CE-7 (**V321**), CE-14a | S6 complete |
| **S8** | Records, evidence, schedule, assurance | CE-9 (**V322**), CE-2 (**V323**), CE-10, CE-8 (**V324**), CE-11, CE-12, CE-14b | S7 complete |
| **S9** | Truthful UI, scope, strategy | W2-*, W3-*, CE-13b, CE-16, W4-1, W4-3…W4-6 | S8 for the CE-facing parts; W2/W3 may start after S2 |

---

## §3 Unified migration register

**One numbering line. No collisions.** Every migration idempotent, `GO`-separated, applied to dev **twice**, reverse recipe in the header.

| Version | Stage | Content | Source plan |
|---|---|---|---|
| **V313** | S1 | Permission catalogue: `SIGNATURE` (READ/WRITE/APPROVE), `AI` (READ/WRITE), `CAPA APPROVE` + role grants | Remediation W0-4 |
| **V314** | S4 | Seed `committee_gate_map` per tenant; config-health support | Remediation W1-4 |
| **V315** | S5 | ⛔ **`ck_change_status` extended** to 17 values (adds `CAB_DEFERRED`, `MORE_INFO_REQUIRED`, `PENDING_RATIFICATION`) — **X-2 fix, must precede any new-state code** | **NEW (this plan)** |
| **V316** | S6 | `cab_profile`, `cab_member_role` | CE-3 (was V314) |
| **V317** | S6 | CAB permission actions (`CAB_MANAGE`, `CAB_SCHEDULE`, `CAB_DECIDE`, `RATIFY`, `PIR`) + grants | CE-15 (was V322) |
| **V318** | S7 | `cab_agenda_item` | CE-4 (was V315) |
| **V319** | S7 | `change_decision`, `change_decision_condition` (+ `change_requests` additive cols) | CE-5 (was V316) |
| **V320** | S7 | `change_ratification` | CE-6 (was V317) |
| **V321** | S7 | `change_pir`, `change_pir_finding` (+ `CHANGE_PIR` added to `ck_ca_source`) | CE-7 (was V318) |
| **V322** | S8 | `change_evidence_link` | CE-9 (was V320) |
| **V323** | S8 | `change_model`, `change_model_usage` | CE-2 (was V313) |
| **V324** | S8 | `change_schedule_entry`; `change_unauthorised` | CE-8 + CE-11 (was V319/V321) |

**Rules.** Additive only — no column drops, no schema rename, no data deletion. `ck_change_status` is a **constraint** replacement (drop + recreate), not data loss, but it must be idempotent and verified by inserting each new status in dev. Prod picks all of these up via Flyway on the next jar (deployed baseline currently **V312**).

---

## §4 Stage detail

### S0 · Deploy the pending fixes *(days — do this first)*
**Why.** A security control is not running in production: the break-glass expiry sweep failed **33 times on 2026-07-25**; separately the stale-cookie 500 is proven live on `:80`. Both fixes exist and are verified — only delivery is missing.
**Actions.** W0-1 merge `fix/break-glass-sweep-tx` → W0-2 rebuild + activate the staged jar in **one** restart (closes both) → W0-3 verify → W0-7 expose the deployed build ID on `/actuator/info`.
**Exit proof.** `probe_df2.py` bogus-signature case = **401** (currently 500) · zero `break-glass expiry sweep failed` over 24 h · build ID matches master.
**Gate.** User's **"merge"** + one elevated PowerShell block (in `REMEDIATION_MASTER_PLAN.md` §2).

### S1 · Authorisation foundation *(V313)*
**Why.** The catalogue has 25 modules but **no `SIGNATURE`, no `AI`, and `CAPA` lacks `APPROVE`**. Any guard naming an uncatalogued module denies everyone — and the evaluator's unknown-module path is untested. This must land before any guard slice, CE included.
**Actions.** W0-4 V313 catalogue + grants (never to `viewer`) → W0-5 guard the 5 e-signature endpoints (`DigitalSignatureController` ×4 + `SignatureController#sign`) → W0-6 fix/delete `AGENTS.md`, stamp the V1 `docs/` corpus HISTORIC.
**Exit proof.** `viewer` → **403** on all five signature endpoints; `so_officer` → 200; committee minutes e-signing still works; ratchet 62 → **57**.

### S2 · Guard the platform *(ratchet 62 → 17)*
**Why.** ~45 of the 62 ratchet-exempt endpoints are real business writes reachable by any authenticated principal — RCA **approve**, plan **attest**, plan **distribute**, BCM compliance-mapping mutation, BCM settings **reset**, 8 AI endpoints, `revokeOne` (another user's session).
**Actions.** W1-3 (per-controller guard mapping in `REMEDIATION_MASTER_PLAN.md` §3) + `@PublicEndpoint`/`@SelfScoped` markers + re-baseline the ratchet to **17** and make the floor explicit; W1-6 tenant-isolation test pack.
**Exit proof.** `viewer` 403 across all 45; public endpoints still reachable unauthenticated; self-scoped endpoints reject a foreign target id; ratchet passes at 17; suite green.
**Gate.** S1 complete.

### S3 · Assure the governance core *(the keystone — start immediately, in parallel with S0/S1)*
**Why.** The committee engine is the system of record for change and policy approval, quorum, recusal and e-signed minutes — and has **zero tests**. `ModulePermissionEvaluator`, behind all 859 guards, has **zero tests**. Every later CE stage builds on both.
**Actions.** W1-1 six committee test classes (`CommitteeQuorumTest`, `CommitteeRecusalTest`, `CommitteeResolutionTallyTest`, `CommitteeMinutesSignatureTest`, `CommitteeReferralGateTest`, `CommitteeTenantIsolationTest` — ~25 cases) + W1-2 evaluator matrix (~9 cases incl. the unknown-module path).
**Exit proof.** ≥ 34 new tests green; temporarily mutating any one guard in `CommitteeReferralService`/`CommitteeResolutionService` **fails** a test (prove the net is real).
**This is the gate that makes CE safe (X-4).**

### S4 · Configure the gate *(V314)*
**Why.** `committee_gate_map` has **0 rows**, so no change is actually committee-gated today, and CE-4's agenda auto-build would return empty (X-5).
**Actions.** W1-4 seed a gate map for every tenant whose authorisation matrix demands CAB/ECAB (creating a default governance committee where none exists) + startup/dashboard **config-health check** that fails loudly on an unmapped gate; W1-5 run and **retain** one complete committee decision as standing evidence, tagged `[DEMO-EVIDENCE]` (never `[REVIEW-TEST]` — the cleanup scripts delete those).
**Exit proof.** Non-empty gate map per qualifying tenant; deleting a mapping turns the health check unhealthy; a CAB-routed change now hits the committee gate (409 before a quorate decision) instead of the legacy tally.

### S5 · Extract the change module *(CE-1 + V315)*
**Why.** Do the mechanical separation while the module is still 1,862 LoC — every later CAB epic then lands in a clean package instead of growing an 853-line service toward 2,000.
**Actions.**
1. **V315 first** — extend `ck_change_status` to 17 values (X-2). Verify by inserting each new status in dev and rolling back.
2. CE-1 package move `module/change_mgmt` → `module/change` with sub-packages (`request`, `lifecycle`, `authority`, `model`, `cab`, `schedule`, `task`, `pir`, `assurance`, `analytics`, `spi`) — schema name **stays** `change_mgmt`.
3. Four SPI ports (`CommitteePort`, `IncidentPort`, `EvidencePort`, `WorkflowPort`) + adapters outside the module; nullable `@Autowired(required=false)` so `@InjectMocks` tests keep working.
4. `ChangeModuleBoundaryTest`: no peer-module imports except `platform`; every port has ≥ 1 external adapter; `ChangeStateMachine` imports no Spring/JPA type; no class in `module/change` > 400 LoC.
5. CE-13a — FE folder skeleton `pages/change/` so later panels have a home (**do not** delete `mockChanges.ts` yet — X-7).
**Exit proof.** Full suite green before **and** after; boundary test green; API paths unchanged (`/api/v1/changes`); zero behaviour change (regression-probe the existing change flows); revert = one `git revert` (no schema dependency).
**Gate.** S2 + S3 green.

### S6 · Constitute the CAB *(CE-3 + CE-15; V316, V317)*
**Actions.** `cab_profile` + `cab_member_role` (charter/ToR, scope, gate, quorum policy, chair/secretary, cadence, notice period, review date, delegation); board lifecycle `PROPOSED → CHARTERED → ACTIVE → UNDER_REVIEW ⇄ ACTIVE / SUSPENDED → DISSOLVED`; CAB permission actions + grants (V317 — **catalogue and guards in the same migration**, per X-3/§1.2); expiring-term and overdue-review surfacing; CAB workspace FE.
**Exit proof.** A CAB can be chartered and constituted; ≥ 1 chair and ≥ 1 secretary enforced; suspend/dissolve blocked (409) while open agenda items or unratified emergency changes exist; `viewer` → 403 on every CAB write; ratchet still ≤ 17.
**Gate.** S4 + S5 complete **and D-1, D-2 answered** (§6).

### S7 · Meetings → decisions → emergency → review *(CE-4, CE-5, CE-6, CE-7; V318–V321)*
The centre of gravity — the four items that turn a gate into an operating model.
- **CE-4 agenda auto-build** (V318): candidates = `CAB_PENDING` (+ optional `IMPACT_ASSESSED`) whose gate resolves to this CAB and whose risk/category falls inside `scope_json`; **plus** `PENDING_RATIFICATION` emergencies; **plus** overdue PIRs and open conditions as standing items. Risk-ordered, time-boxed, notice-checked, **idempotent rebuild**, `LATE_ADDITION` marking after publication. Papers pack per item.
- **CE-5 decision write-back** (V319): `APPROVED │ APPROVED_WITH_CONDITIONS │ REJECTED │ DEFERRED │ MORE_INFO_REQUIRED` → `change_decision` + conditions register. New states `CAB_DEFERRED`/`MORE_INFO_REQUIRED` (enabled by V315). `ConditionsSatisfiedGuard` blocks `SCHEDULED → IMPLEMENTING` while a condition is OPEN. **Evidence reference on a condition is optional here** (X-6); the mandatory variant activates in S8.
- **CE-6 ECAB ratification** (V320): `PENDING_RATIFICATION`; `required_by = implementation_ended + SLA` (default **5 business days**, per-tenant); `CLOSED` blocked without a ratification decision; late ratification sets `breach = 1` — visible, not hidden.
- **CE-7 PIR record** (V321): real `change_pir` + `change_pir_finding` with CAPA linkage (**add `CHANGE_PIR` to `ck_ca_source` in the same migration or inserts 500**); `PirRecordGuard` upgrades CH-11's string check.
- **CE-14a**: agenda pack + minutes exports (RTL, bilingual).
**Exit proof.** CE plan §16 probes 1–5, 6a, 7, 8, 9, 11, 12, 14 all pass, including the non-regression probe that a legacy non-referred change behaves byte-identically.
**Gate.** S6 complete.

### S8 · Records, evidence, schedule, assurance *(CE-9, CE-2, CE-10, CE-8, CE-11, CE-12, CE-14b; V322–V324)*
**Actions.** CE-9 evidence integration via `EvidencePort` (+ `change_evidence_link`, V322) — **and now switch `ConditionsSatisfiedGuard` to require verified evidence where the model demands it** (closes X-6; probe 6b); CE-2 change-model catalogue (V323) with `ModelComplianceGuard` (a `STANDARD` change without a live pre-approved model routes as `NORMAL` — fail-closed); CE-10 workflow-engine adoption (15th module); CE-8 FSC/PSO (V324); CE-11 unauthorised-change register (V324); CE-12 KPI pack (success rate, **emergency ratio**, quorum-met rate, ratification breaches); CE-14b decision register export.
**Exit proof.** Probes 6b, 10, 13 pass; KPI figures reconcile to pyodbc counts; evidence-mandatory conditions block until verified.

### S9 · Truthful UI, evidence-based scope, strategy
**Actions.** W2-1…W2-5 eliminate all **53** mock import sites (start with `searchIndex.ts` ×8, `dashboard/widgets.tsx` ×3, `boardPack.ts` ×3 — global search, executive dashboard and board reporting) + CI rule; **CE-13b** completes the `ChangesPage` split and **only now deletes `mockChanges.ts`** (X-7); W3-1 the 84 user stories as a CI probe suite; W3-2 module scorecard with *Assurance* + *Operating-evidence* columns; W3-3 minimum test bar (start SAMA `outsourcing`); W3-4 reconcile the 287 audit findings; W3-5 label hollow features; CE-16 docs (chronicle §, `package-info`, CAB runbook); then W4-1 platform-wide boundary tests, W4-3 chronicle carry-overs (passwords, TLS, SMTP, `is_deleted`), W4-4 packaging/Saudi scorecards, W4-5 AI strategy, W4-6 structural debt.
**Exit proof.** No `mock*` import outside tests; with the backend stopped every register shows an error state and **no rows**; probe suite runs in CI; no zero-test/zero-data module in any commercial scope statement.

---

## §5 Unified tracker

| ID | Action | Stage | Size | Status | Migration |
|---|---|---|---|---|---|
| W0-1 | Merge `fix/break-glass-sweep-tx` | S0 | S | ✅ **DONE** — master `ccc06799` | — |
| W0-2 | Rebuild + activate jar (closes DF-1 + DF-2) | S0 | S | ⏳ **jar built + staged** (`...jar.new`, 104,934,012 B, suite 2322/0/0) — awaits the elevated restart | — |
| W0-3 | Post-deploy verification (24 h) | S0 | S | blocked on W0-2 restart | — |
| W0-7 | Deployed build ID on `/actuator/info` | S0 | S | not started | — |
| — | *(unplanned, found by the S0 build)* de-flake `ComplaintServiceTest` — Saturday-only failure | S0 | S | ✅ **DONE** — master `d859060b` | — |
| W0-4 | Permission catalogue: SIGNATURE, AI, CAPA APPROVE | S1 | S | ✅ **DONE** `9410b2f3` (applied dev ×2) | **V313** |
| W0-5 | Guard the e-signature endpoints | S1 | S | ✅ **DONE** `9410b2f3` — 4 endpoints (not 5: `#expire` was already admin-only); live 8/8 | rides V313 |
| W0-6 | Fix `AGENTS.md`; stamp V1 docs HISTORIC | S1 | S | ⬜ not started | — |
| W1-3 | Guard the business writes; ratchet 62 → **21** | S2 | M | ✅ **DONE** `d8d9b56b` + `782da646` — 41 endpoints guarded; floor now enumerated (8 public + 10 self-scoped + 3 participant) | rides V313 |
| W1-6 | Tenant-isolation test pack | S2 | M | 🔶 committee engine done (`ab28f348`); other module families pending | — |
| W1-1 | Committee-engine test suite | S3 | M | ✅ **DONE** — 5 classes / **93 tests**, all mutation- or live-verified (`00939e76`, `083c6119`, `033abaf5`, `ab28f348`) | — |
| W1-2 | `ModulePermissionEvaluator` test matrix | S3 | S | ✅ **DONE** — 14 cases `00939e76` | — |
| W1-4 | Seed gate map + config-health check | S4 | M | ✅ **DONE** `616dabc6` — org 1 mapped (CAB→IT Steering, ECAB→Exec); orgs 2/3 have no committees so are FLAGGED, not fabricated; startup WARN + `/governance-health` live-verified | **V314** |
| W1-5 | Retained worked committee decision | S4 | S | ⬜ next in S4 | — |
| **CE-1** | Module separation + ports + boundary test | S5 | L | not started | — |
| **X-2 fix** | **Extend `ck_change_status` to 17 values** | S5 | S | not started | **V315** |
| CE-13a | FE `pages/change/` skeleton | S5 | S | not started | — |
| **CE-3** | CAB as a governed body | S6 | M | not started | **V316** |
| **CE-15** | CAB permission actions + SoD gates | S6 | S | not started | **V317** |
| **CE-4** | CAB meeting cycle + agenda auto-build | S7 | L | not started | **V318** |
| **CE-5** | Decision write-back + conditions | S7 | M | not started | **V319** |
| **CE-6** | ECAB ratification + SLA | S7 | M | not started | **V320** |
| **CE-7** | Real PIR + findings + CAPA link | S7 | M | not started | **V321** |
| CE-14a | Agenda pack + minutes exports | S7 | M | not started | — |
| **CE-9** | Evidence integration (+ evidence-mandatory conditions) | S8 | M | not started | **V322** |
| **CE-2** | Change-model catalogue | S8 | M | not started | **V323** |
| CE-10 | Workflow-engine adoption | S8 | M | not started | — |
| CE-8 | FSC / PSO | S8 | M | not started | **V324** |
| CE-11 | Unauthorised-change register | S8 | S | not started | rides V324 |
| CE-12 | ITIL/CAB KPI pack | S8 | S | not started | — |
| CE-14b | Decision register export | S8 | S | not started | — |
| W2-1…W2-5 | Eliminate 53 mock sites + CI rule + product states | S9 | L | not started | — |
| CE-13b | Complete FE split; **delete `mockChanges.ts`** | S9 | L | not started | — |
| W3-1…W3-5 | Probe suite · scorecard · test bar · audit reconciliation · hollow-feature labels | S9 | L | not started | — |
| CE-16 | Docs (chronicle, package-info, CAB runbook) | S9 | S | not started | — |
| W4-1, W4-3…W4-6 | Boundary tests platform-wide · carry-overs · packaging · AI · structural debt | S9 | L | not started | — |

---

## §6 Decisions required from the user

**Blocking S6 — ✅ DECIDED 2026-07-25** (adopted as recommended; reversible by the user at any point before S6 starts):
| # | Decision | **Adopted** | Rationale |
|---|---|---|---|
| **D-1** | CAB modelling | ✅ **(a) Profile over the committee engine** — `CabProfile` + `CabAgendaItem` + `ChangeDecision` layered on `admin.committee*`; **no second meeting engine** | Independently validated (§1.1). A fork duplicates 4,638 LoC and splits the minutes/e-signature audit trail in two — the opposite of what a regulator wants. One engine also means the S3 committee tests protect the CAB too. |
| **D-2** | Agenda auto-build trigger | ✅ **(a) On demand, with a preview diff** — the secretary clicks "Build agenda" and sees *added / kept / dropped* before committing | Auto-mutating an agenda a human is mid-edit is a trust problem, and a nightly job would silently re-order a published agenda. On-demand keeps the secretary accountable for what the meeting considers — an audit expectation. The rebuild stays idempotent, so the button is safe to press twice. |

*Consequence for S6/S7:* `CabAgendaService.buildAgenda()` returns a **proposed** agenda diff; a separate `commitAgenda()` persists it. Both guarded by `CHANGE/CAB_SCHEDULE` (V317).

**Needed by S7:**
| # | Decision | Recommendation |
|---|---|---|
| **D-3** | Ratification SLA default | **5 business days**, per-tenant override; breach **flags**, does not block |
| **D-5** | Do CAB approvals expire? | **Yes, 30 days** (`effective_to`) — a stale approval for a since-changed system is a real audit finding |
| **D-7** | Delegate voting | **Only when the principal is absent**, recorded as delegated in the minutes |

**Needed by S8:**
| # | Decision | Recommendation |
|---|---|---|
| **D-6** | May a pre-approved model skip CAB (`AUTO` gate)? | **Yes** — but only with `pre_approved = 1`, a live `authorised_until` and a named authoriser; fail-closed on expiry |

**Cross-cutting:**
| # | Decision | Recommendation |
|---|---|---|
| **D-4** | New-guard rollout | **Off for existing tenants + dry-run report** first, on by default for new tenants (applies to S2 *and* the CE guards) |
| **D-8** *(new)* | Does S2's guarding of BCM participant endpoints (`PlanDistribution#acknowledge/decline`, `ExerciseRuntime#respond`) risk locking out non-GRC staff who legitimately acknowledge plans? | Treat as **`@SelfScoped` + `BCM READ`**: the recipient acts on their own assignment; verify against the seeded persona matrix before shipping |

---

## §7 Unified test & probe plan

**Automated (must be green to exit each stage).**
| Stage | New tests |
|---|---|
| S2 | tenant-isolation pack (per module family); ratchet at 17 |
| S3 | 6 committee classes (~25 cases) + evaluator matrix (~9) |
| S5 | `ChangeModuleBoundaryTest` (4 assertions); `ChangeStateMachineTest` (pure, every state/guard, allow **and** deny) |
| S6 | `CabProfileServiceTest` (charter/roles/quorum policy/suspend-blocked) |
| S7 | `CabAgendaServiceTest` (candidates, scope filter, ordering, idempotent rebuild, ratification inclusion) · `CabDecisionServiceTest` · `EcabRatificationServiceTest` · `PirServiceTest` |
| S8 | evidence-mandatory condition tests; `ChangeModelComplianceTest`; KPI arithmetic tests |
| S9 | the 84-story probe suite in CI |

**Live probes** (Python urllib + pyodbc, two users `admin` + `so_officer`, negative user `viewer`, `[REVIEW-TEST]` rows CHARINDEX-cleaned):
S0: DF-2 bogus-signature → 401 · S1: viewer 403 on 5 signature endpoints · S2: viewer 403 on 45 endpoints; public still open; self-scoped rejects foreign id · S4: CAB-routed change 409 before a quorate decision · S5: existing change flows unchanged (non-regression) · S7: CE §16 probes 1–5, **6a**, 7, 8, 9, 11, 12, 14 · S8: probes **6b**, 10, 13 · S9: backend-down shows error states, not rows.

---

## §8 Risks specific to the merge

| Risk | Mitigation |
|---|---|
| **X-2 missed in execution** → `CAB_DEFERRED` write raises a 500 constraint violation | V315 is a **named tracker row with its own stage gate**; verify by inserting all 17 statuses in dev before CE-5 code ships |
| Guarding 45 endpoints (S2) locks out legitimate users | D-4 dry-run report + per-tenant flag; D-8 for BCM participant endpoints; probe every persona before/after |
| CE-15's new actions deny everyone | Catalogue rows + grants ship **in the same migration** as the guards (V317) |
| Agenda auto-build looks broken (empty) | S4 gate-map seed is a hard gate before S7; config-health check surfaces the misconfiguration instead of silently returning nothing |
| Committee tests encode current (possibly wrong) behaviour | Derive expected values from ITIL/ISO intent and the plan's acceptance criteria, **not** from running the code |
| Extraction (S5) breaks component scan / DevTools | Mechanical commit, suite green before+after, `@ComponentScan` verified at boot; revert is one commit (no schema dependency) |
| Removing mock fallbacks (S9) exposes hidden API failures | **By design** — triage the newly-visible failures as real defects before shipping W2-2 |
| Two plans drift again | This document owns sequencing/numbering; the other two are detail references and must not restate stage order or migration numbers |
| ROWVERSION double-flush on decision write-back | Native stamp first, then mutate + save **once** (established landmine) |

---

## §9 Definition of done

**Security & assurance (S0–S4)**
- [ ] Zero live High defects; deployed build ID visible and matching master
- [ ] Ratchet at **17**; `viewer` proven 403 across all 45 guarded writes; public floor explicit
- [ ] Committee engine + permission evaluator under test; a deliberate mutation fails the suite
- [ ] Committee gate configured, health-checked, with one retained worked decision

**Change Enablement (S5–S8)**
- [ ] `module/change` self-contained; boundary test green; no class > 400 LoC; god-service dissolved
- [ ] `ck_change_status` carries all 17 values; every new state reachable without a constraint error
- [ ] CAB chartered and constituted; expiring terms and overdue reviews visible
- [ ] Agenda **auto-built from the change queue**, papers attached, notice checked, published, idempotent
- [ ] Decisions written back with attributable votes, quorum proof and conditions; approvals expire
- [ ] Conditions block implementation — and require verified evidence where the model demands it
- [ ] Emergency changes cannot close without ratification; breaches visible
- [ ] PIR is a record with findings and CAPA links; closure requires it
- [ ] FSC/PSO published; unauthorised changes reconcilable; KPI pack live (incl. emergency ratio)
- [ ] Agenda pack, e-signed minutes and decision register export bilingually, RTL-correct

**Product truth (S9)**
- [ ] No `mock*` import outside test files; backend-down shows error states, not data
- [ ] 84 stories running as CI probes; 287 audit findings reconciled with evidence
- [ ] Module scorecard published; no zero-test/zero-data module in commercial scope
- [ ] Docs current: chronicle §, `package-info` contract, CAB operating runbook

---

## §10 Recommended start

1. **Say "merge"** → S0 closes both live defects in one restart (hours).
2. **Start S3 in parallel today** — the committee and evaluator tests need no gate and unblock everything downstream.
3. **Then S1 → S2** (V313, guards, ratchet 17).
4. **Answer D-1 and D-2** while S1–S4 run, so S6 is not blocked when it arrives.
5. **S5 extraction** only once S2 + S3 are green — never refactor an untested governance core.

*Prepared 2026-07-25. Supersedes the sequencing and migration numbering in `REMEDIATION_MASTER_PLAN.md` and `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`; both remain authoritative for their design detail. Validation evidence: `INDEPENDENT_PLATFORM_ASSESSMENT.md` Appendix A plus the eight defect checks in §1.2.*

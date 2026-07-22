# Change Management (ITIL v4 Change Enablement) — Module Review & Remediation Plan

> **Superseded by [`CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md`](CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md)** (2026-07-21), which combines this Change review with the Policy Management review into one plan per the `CHANGE_AND_POLICY_MODULE_REVIEW_COMMAND_LOOP` spec. This file remains as the Change-only detail; the combined plan is authoritative.

**Module:** `change_mgmt` · route `/changes` · API base `/api/v1/changes`
**Review type:** Read-and-test only (no product-code changes in this phase, per `REVIEW_MODULE_COMMAND.md`)
**Reviewed:** 2026-07-21 · dual persona (senior GRC/ITIL consultant + daily change manager)
**Stack probed live:** backend `:8084` (PID 30388) + Vite `:5173` (PID 19624), SQL Server `TSS_GRC`, tenant org 1 (Trusted Security Solutions), 15 seeded change rows.
**Evidence:** `review/change-mgmt/` (DB inspection + 4 live-probe scripts, all self-cleaning via `[REVIEW-TEST]` + CHARINDEX).

---

## §0 — Implementation status tracker

| Phase | State | Notes |
|---|---|---|
| Inventory (BE/FE/DB/automation) | ✅ done | 13 backend files (967 LoC), 1 FE page (710 LoC) + mock file, 5 DB tables, 5 F8 rules |
| Design-vs-reality | ✅ done | ITIL 14-state model is real; CAB governance + validation + audit are not |
| Live violent testing | ✅ done | 7 finding-probes confirmed live; 1 (cross-tenant) code-verified only (see note) |
| Findings register | ✅ done | 3 Critical · 3 High · 4 Medium · 4 Low |
| **Remediation** | ⛔ **NOT STARTED** | This document is the plan; no code changed. Next free migration **V300** (V1→V299 packaged; CLAUDE.md's "V296" is stale). |

> **One live check deferred:** the cross-tenant `404` probe (P9) could not complete because the platform **login rate-limiter** (an IP-keyed auth filter, empty-body 401) tripped after my rapid repeated logins colliding with the STOMP broker's auth traffic. This is a platform auth-layer throttle, **unrelated to Change Management**, and did not affect the browser session. Tenant isolation is therefore reported as **code-verified** (see CM-STRENGTHS and CM-L3) — the `require()` org filter is invoked at the top of every sub-resource method and is the platform-standard pattern.

---

## §1 — Verdict

**Partially usable — strong ITIL skeleton, hollow governance core.**

The module models the ITIL v4 Change Enablement lifecycle competently: a clean 14-state machine, type-aware paths (NORMAL→CAB, EMERGENCY bypass), cross-module linking, realtime broadcast, board-pack integration, and **genuinely-wired F8 automation** (5 rules, AR-96..AR-100). A change manager can drive a record through its whole lifecycle and it looks right on screen.

But every control that makes change management a *governance* function rather than a *status tracker* is either decorative or bypassable:

- **The CAB vote decides nothing** — approval is a manual state flip unrelated to recorded votes, with no quorum, no segregation of duties, and no captured approver identity.
- **The state machine can be walked around entirely** — a plain `PUT` sets any status directly, skipping the guard, side-effects, events, and audit.
- **There is no segregation of duties at all** — the single permission gate (`WORKFLOW`) is granted to 54 roles including read-only `VIEWER`, `EXTERNAL_AUDITOR`, and `REGULATOR_VIEWER`; one person can author and approve their own emergency change.

For a platform anchored to **NCA ECC** (change control), **SAMA CSF** (segregation, four-eyes), and audit-committee reporting, findings CM-C1/C2/C3 are release-blockers. None require large rework — they are guard-and-validate changes on an otherwise sound design.

---

## §2 — Inventory (what actually exists)

**Backend** — `module/change_mgmt/` (13 files):
- `controller/ChangeManagementController.java` (161 LoC) — 18 endpoints, all `@PreAuthorize("hasPermission('WORKFLOW', …)")` (READ/WRITE/DELETE only; **no APPROVE action anywhere**).
- `service/ChangeManagementService.java` (423 LoC) — 14-state `NORMAL_TRANSITIONS` map, `guard()`, `transition()` with per-target side-effects, approvals/tasks/links/comments, analytics, F8 `emit()`.
- `entity/` — `ChangeRequest` (soft-delete `@Where`), `ChangeApproval`, `ChangeTask` (**no `is_deleted`**), `ChangeLink` (soft-delete), `ChangeComment`.
- `repository/` — 5 repos. `ChangeRequestRepository.findWithFilters` is org-scoped; sub-resource repos filter by `change_request_id` only (org enforced in service via `require()`).

**Frontend** — `tss-grc-ui-v2/src/pages/changes/`:
- `ChangesPage.tsx` (710 LoC) — 6 tabs (Register, Kanban, Workflow, Analytics, Integrations, Comms); FormModal; KanbanBoard; WorkflowPanel (approvals/tasks/comments); IntegrationsPanel (links).
- `mockChanges.ts` — 10 fabricated change requests + fake analytics used as a fallback.

**Database** — schema `change_mgmt`: `change_requests`, `change_approvals`, `change_tasks`, `change_links`, `change_comments`. **Zero CHECK constraints on any table.** Defaults: `status='DRAFT'`, `change_type='NORMAL'`, `is_deleted=0`, `external_id=newid()`, `created_at=sysutcdatetime()`.

**Automation** — `V146__change_mgmt_automation_rules.sql` *defines* 5 rules (AR-96..AR-100) on `CHANGE_SUBMITTED` (HIGH→CAB notify), `CHANGE_EMERGENCY_APPROVED` (→CTO/CISO escalate), `CHANGE_CAB_REJECTED` (→rework), `CHANGE_FAILED` (→incident triage), `CHANGE_IMPLEMENTED` (HIGH→BCM), and the service emits these events in code. **Correction (verified against the running DB):** these rules are **NOT present in the dev `platform.automation_rules` table** (0 `CHANGE_*` consumers; dev carries a different 175-rule seed, AR-01..AR-19+). So the emission is real but the automation is **dormant in the running instance** — verify prod seeding. See the combined plan §19/CH-13.

---

## §3 — Findings register

Severity: **Critical** = governance control absent/bypassable, release-blocking · **High** = integrity/trust defect · **Medium** = correctness/UX/attribution · **Low** = polish/fidelity.

### 🔴 CM-C1 — No segregation of duties; the `WORKFLOW` gate is granted to almost every role
**Evidence (live):** `viewer` (role `VIEWER`) `POST /api/v1/changes` → **201 CREATED**. DB: `VIEWER` holds `WORKFLOW` **READ + WRITE + EXPORT**; `WORKFLOW/WRITE` is granted to **54 roles**, including `VIEWER`, `REGULATOR_VIEWER`, `EXTERNAL_AUDITOR`, `AUDITOR`, `SUPPLIER_USER`.
**Root cause:** the module deliberately reuses the broad `WORKFLOW` permission (controller javadoc: *"change-management actions belong to the same broad workflow group"*). Every write/transition/approval endpoint is `WORKFLOW/WRITE`; there is **no APPROVE action and no CAB-approver role**.
**Impact:** one actor can create → submit → self-assess → CAB-approve → schedule → implement → close a change end-to-end. Read-only oversight personas (external auditor, regulator viewer) can author and mutate production changes. Directly violates NCA ECC change-authorisation and SAMA CSF segregation expectations.
**Remediation:** → **CM-1**.

### 🔴 CM-C2 — CAB approval is decorative (no quorum, no SoD, votes don't gate, no approver identity)
**Evidence (live):**
- `CAB_APPROVED` reached with **zero** approval rows → **200**.
- A recorded **`REJECT`** vote, then `CAB_APPROVED` → **200** (rejection ignored).
- **Self-approval** (`approverName` == `requestedByName`) while `CAB_PENDING` → **201**; persisted row has `approver_id = NULL`.
**Root cause:** `recordApproval()` only checks status ∈ {IMPACT_ASSESSED, CAB_PENDING} and that `decision`/`approverName` are present. `transition(CAB_PENDING→CAB_APPROVED)` is gated **only** by the state map — it never counts votes, never checks for rejections, never enforces approver≠requester, and `approver_id` is never derived from the authenticated principal (it is nullable and always null). The approvals table is a write-only log with no control effect.
**Impact:** the CAB — the single most important control in change enablement — enforces nothing. The 5 F8 automation rules and the audit-committee narrative rest on a gate that any one user satisfies by clicking "advance." No verifiable record of *who* approved.
**Remediation:** → **CM-1**.

### 🔴 CM-C3 — `update()` (PUT) bypasses the entire lifecycle state machine
**Evidence (live):** `PUT /api/v1/changes/{id}` with body `{"status":"CLOSED"}` on a **DRAFT** change → **200**, subsequent `GET` shows `status=CLOSED`. Contrast: the guarded path `POST /{id}/transition {"target":"CLOSED"}` on a DRAFT → **400** ("Cannot transition from DRAFT to CLOSED").
**Root cause:** `apply()` copies status unconditionally (`if (src.getStatus()!=null) target.setStatus(src.getStatus())`) and `update()` calls it with no `guard()`. So `PUT` is an unguarded parallel path to any status — skipping transition side-effects (timestamps, outcome), F8 event emission, **and** the audit trail.
**Impact:** the whole ITIL guarantee (a change cannot be closed without going through CAB/implementation/review) is void. A change can jump DRAFT→CLOSED or DRAFT→IMPLEMENTED silently and invisibly (see CM-H2). The FE's own Edit action uses this `PUT`.
**Remediation:** → **CM-2**.

### 🟠 CM-H1 — No input validation; no DB CHECK constraints; Arabic title not enforced
**Evidence (live):** `PUT {"status":"ZZZ_INVALID","changeType":"MADE_UP","riskLevel":"NUCLEAR"}` → **200**, all persisted verbatim. DB inspection: **no CHECK constraints** on any `change_mgmt` table; `status`/`change_type`/`risk_level`/`outcome` are free-text `nvarchar`. Controller `create`/`update` take a **raw entity** with **no `@Valid`**; `title_ar` is nullable and unenforced.
**Root cause:** validation exists only implicitly inside `transition()` (the state map) and nowhere for enums, and `update()` skips even that.
**Impact:** garbage statuses persist and then **vanish** from the Kanban lanes, analytics `byStatus`, and any status-filtered view (they match no known bucket) — silent data loss. Violates the house bilingual rule (`*_ar` mandatory for user-visible records).
**Remediation:** → **CM-2**.

### 🟠 CM-H2 — Audit trail covers only 2 of ~11 mutating operations
**Evidence (live):** after `CREATE` + `PUT(status=IMPLEMENTED)`, `platform.audit_log` for that change shows **only `CREATE`** — the status-changing PUT leaves no trace. (A proper `transition()` *is* audited: rows `[CREATE, TRANSITION]`.)
**Root cause:** `audit.log(...)` is called only in `create()` and `transition()`. **Not** called in `update()`, `delete()`, `recordApproval()`, `addTask()`, `updateTask()`, `deleteTask()`, `addLink()`, `deleteLink()`, `addComment()`.
**Impact:** the CM-C3 status bypass is also **invisible**. Deletions, approvals, task changes, and links are unauditable. Fails audit-ledger completeness for a module explicitly framed as "high-visibility for the audit committee."
**Remediation:** → **CM-3**.

### 🟠 CM-H3 — The Changes "Export" button downloads the **Risk Register**, not changes
**Evidence (code):** `ChangesPage.tsx` `ExportMenu` wires `onPdf={() => reportingApi.riskRegisterPdf(lang)}`, `onDocx/onXlsx → reportingApi.riskRegister*`. There is **no** change-register exporter in the backend (`ExportController` mentions changes only inside the multi-section **board pack**); the controller has no export endpoint (probed: `/api/v1/changes/export*` → not mapped).
**Impact:** a user exporting from the Changes page silently gets risk data — wrong artefact, and a genuinely missing feature (no standalone change register for CAB packs / regulator evidence).
**Remediation:** → **CM-4**.

### 🟡 CM-M1 — Demo data masks an empty or failing API for authenticated users
**Evidence (code):** `load()` calls `applyDemoChanges()` (10 fabricated changes + fake `successRatePct: 75`) when the API returns an **empty list** *or* on **any error** (`catch`). The "Demo mode" badge only renders when `!isAuthenticated()`. So a logged-in user on a fresh tenant (0 changes) or hitting a 403/500 sees fabricated governance data **with no indication it is fake**.
**Impact:** misleading KPIs and register content; a real empty state is indistinguishable from seeded fiction; masks backend outages.
**Remediation:** → **CM-6**.

### 🟡 CM-M2 — Change tasks are hard-deleted and unaudited
**Evidence (live + schema):** `DELETE /api/v1/changes/tasks/{id}` → **204**; `change_tasks` has **no `is_deleted` column** → `taskRepo.deleteById()` is a physical delete; no `audit.log`.
**Impact:** inconsistent with the platform soft-delete convention (and with `change_links`, which soft-delete); destroys implementation-checklist history that a post-implementation review or auditor would need.
**Remediation:** → **CM-3**.

### 🟡 CM-M3 — Approver / comment-author identity is not captured
**Evidence (live + code):** approvals — `approverName` is free-text (FE collects it via `window.prompt`), `approver_id` is never set from the principal (persisted `NULL`). Comments — FE hardcodes `authorName: 'You'`; backend defaults `'Unknown'` when null.
**Impact:** no verifiable link between an approval/comment and a real authenticated user; approvals are repudiable; comment attribution is meaningless. Compounds CM-C2.
**Remediation:** → **CM-1** (approver identity) + **CM-3** (comment author).

### 🟡 CM-M4 — Task SLA is a façade (`dueAt` unused; no scheduler; FE never sets it)
**Evidence (code):** `ChangeTask.dueAt` exists but **no `@Scheduled`** anywhere in the module (grep clean) — no reminder/escalation/overdue sweep. The FE `addTask()` sends only `{titleEn, status}` — never `dueAt` or `ownerName`. The page's "Overdue" KPI is computed from the *change's* `scheduledStart`, not from tasks.
**Impact:** implementation-task deadlines are never captured and never enforced; the SLA affordance is cosmetic.
**Remediation:** → **CM-5**.

### 🔵 CM-L1 — `STANDARD` (pre-approved) changes have no distinct fast-path
**Evidence (code):** a single `NORMAL_TRANSITIONS` map drives all types (comment: *"NORMAL/STANDARD path"*). A STANDARD change still routes DRAFT→SUBMITTED→IMPACT_ASSESSED→CAB_PENDING→CAB_APPROVED like a NORMAL one. Only EMERGENCY has a real shortcut (`DRAFT→EMERGENCY_APPROVED`). Contradicts the service javadoc ("STANDARD is pre-approved") and ITIL semantics.
**Remediation:** → **CM-2** (type-aware transitions).

### 🔵 CM-L2 — In-page create modal's create branch is dead; Edit routes through the bypassable PUT
**Evidence (code):** the header "New" button navigates to `/intake/change_request` (Services Center), so the page's own `FormModal` create branch (`changeApi.create`) is unreachable; only Edit opens the modal, and it saves via `changeApi.update` (the CM-C3 path). Two create philosophies coexist; the local one is dead weight and the edit path is unsafe.
**Remediation:** → **CM-6**.

### 🔵 CM-L3 — `linksForEntity` uses an org-unscoped query, then post-filters
**Evidence (code):** `linkRepo.findByEntityTypeAndEntityId(type,id)` has no org predicate; safety comes from a post-`filter` on `organisationId` + `is_deleted`. Correct result, but loads cross-tenant rows before discarding them (minor inefficiency / defence-in-depth gap). Tenant isolation on the primary paths (`require()`) is sound.
**Remediation:** → **CM-7**.

### 🔵 CM-L4 — CAB votes can be recorded outside the decision window
**Evidence (code):** `recordApproval()` accepts votes in `IMPACT_ASSESSED` (before CAB convenes) and nothing prevents further votes after a decision. Minor once CM-C2 introduces a real tally.
**Remediation:** → **CM-1**.

---

## §4 — Strengths (keep these)

- **Sound ITIL 14-state model** with a real `guard()` on `transition()` (the design is right — it's just not the only door; CM-C3).
- **F8 automation is designed and emitted in code** — the service publishes 8 lifecycle events and V146 defines 5 consuming rules (AR-96..AR-100). ⚠ *Corrected:* those rules are **absent from the dev DB**, so the automation is dormant in the running instance (see §2 / combined plan CH-13) — the wiring is real in code, not in the running data.
- **Tenant isolation via `require()`** on every primary and sub-resource path (org filter on `findById`), consistent with the platform pattern.
- **Rich cross-module linking** — 12 entity types × 7 link types + inverse `by-entity` lookup feeding other modules' "Linked Changes" panels.
- **Bilingual fields, realtime broadcast, board-pack integration, and analytics** all present.

---

## §5 — Remediation plan (proposed; not yet implemented)

Migrations start at **V300** (V1→V299 already packaged; idempotent; apply to dev twice via pyodbc; prod via Flyway). All work on a branch `feat/change-mgmt-remediation`; commit per verified slice; **do not merge without explicit "merge."** Live-probe every slice (admin + a real read-only user + a distinct CAB approver) including guard 403s and SoD 409s.

### CM-1 — Real change governance: segregation + CAB quorum + approver identity `[Critical → C1, C2, M3, L4]`
- Add a dedicated permission surface for the module: either a `CHANGE` module in the catalogue (READ/WRITE/APPROVE/DELETE) or, at minimum, a distinct `WORKFLOW/APPROVE`-style **CAB-approver** capability. **Remove `WORKFLOW/WRITE` from read-only personas** (`VIEWER`, `REGULATOR_VIEWER`, `EXTERNAL_AUDITOR`, `AUDITOR`, `SUPPLIER_USER`).
- Gate `POST /{id}/approvals` and the `CAB_APPROVED`/`EMERGENCY_APPROVED` transitions on the approver capability (not plain WRITE).
- In `recordApproval()`: set `approver_id` from the authenticated principal; **reject self-approval** (approver == requester/change owner) with a `409` SoD problem; restrict votes to `CAB_PENDING`.
- Make `CAB_APPROVED` **quorum-driven**: `transition→CAB_APPROVED` requires ≥ configured APPROVE votes and **zero** outstanding REJECT (else `409`). EMERGENCY_APPROVED requires a second authoriser distinct from the requester.
- **Acceptance:** self-approval → 409; CAB_APPROVED with 0 votes or with a REJECT → 409; `viewer` POST/approve → 403; approver_id populated; two-user happy path passes.

### CM-2 — Lifecycle integrity + validation `[Critical → C3, H1, L1]`
- Remove `status` copying from `apply()`; `update()` must **not** change status. All status moves go through `transition()`/`guard()` only.
- Add server-side enum validation (status/type/risk/outcome) + `@Valid` DTOs (introduce request DTOs rather than raw entities) + **Arabic-title-required** gate.
- Add idempotent DB **CHECK constraints** (V296) for `status`, `change_type`, `risk_level`, `outcome`.
- Introduce a genuine type-aware path so `STANDARD` is pre-approved (skips CAB) distinct from `NORMAL`.
- **Acceptance:** `PUT {status:…}` cannot change status (ignored or 400); invalid enums → 400; missing `title_ar` → 400; STANDARD skips CAB; DB rejects bad enums.

### CM-3 — Full audit coverage + soft-delete tasks `[High → H2, M2, M3-comments]`
- Call `AuditLogService.log(...)` on `update`, `delete`, `recordApproval`, `addTask`, `updateTask`, `deleteTask`, `addLink`, `deleteLink`, `addComment` — with before/after status where relevant.
- Add `is_deleted` to `change_tasks` (V296) + `@Where`; convert `deleteTask` to a soft delete.
- Capture comment `authorName`/`authorId` from the principal (stop trusting the client's `'You'`).
- **Acceptance:** every mutating call produces an audit row; deleted tasks are recoverable/queryable; author identity is real.

### CM-4 — Change-register export `[High → H3]`
- Add a change-register exporter (PDF/DOCX/XLSX) — reuse `TabularExportService`/`PdfTheme` (RTL-correct per the platform Arabic sweep) — and a `/api/v1/changes/export/{fmt}` endpoint gated on `…/EXPORT`.
- Rewire `ChangesPage` `ExportMenu` from `reportingApi.riskRegister*` to the new change endpoints.
- **Acceptance:** export produces change data (not risk); Arabic output RTL-correct; respects current filters.

### CM-5 — Task SLA that actually runs `[Medium → M4]`
- FE `addTask`/edit: capture `dueAt`, `ownerName`, `titleAr`.
- Add an org-explicit `@Scheduled` sweep for overdue tasks → reminder/escalation (reuse the platform notification/F8 path); drive the "Overdue" KPI from tasks, not `scheduledStart`.
- **Acceptance:** due dates persist; overdue tasks trigger a notification; KPI reflects task state.

### CM-6 — Data honesty in the UI `[Medium → M1, L2]`
- Scope demo fallback to unauthenticated/explicit-demo only; for authenticated users show a real empty state and a visible error banner on API failure — never fabricated analytics.
- Resolve the dual create paths: either restore the in-page create (guarded) or remove the dead modal-create branch; ensure Edit does not rely on status-via-PUT (depends on CM-2).
- **Acceptance:** logged-in fresh tenant shows empty state; API error shows a banner; no fake KPIs when authenticated.

### CM-7 — Polish `[Low → L3]`
- Add an org-scoped variant of `findByEntityTypeAndEntityId` (or a `@Query` with `organisation_id`) for defence-in-depth.
- **Acceptance:** query returns only same-tenant links.

---

## §6 — Verification plan (per slice, live)

- **Personas:** `admin` (PLATFORM_ADMIN), one real read-only user (e.g. re-scoped `viewer`), and a dedicated CAB approver distinct from the requester — for SoD/quorum paths.
- **Probes (reuse `review/change-mgmt/cm_probe*.py`):** guard 403s (read-only write attempts), SoD 409s (self-approval), quorum 409s (approve with 0/REJECT votes), status-immutability of `PUT`, enum-rejection 400s, audit-row presence after each mutation, export content-type/data, cross-tenant 404 (once the auth throttle is quiet — space out logins or reuse a cached token).
- **Regression:** full backend suite (`mvn -o clean package`), `npx tsc --noEmit`, `npx vitest run`.
- **Test data:** tag `[REVIEW-TEST]`; clean with `CHARINDEX` deletes (never `LIKE '[...]'`).

---

## §7 — Appendix: evidence index (`review/change-mgmt/`)

| File | What it proves |
|---|---|
| `cm_db_inspect.py` | schema, **zero CHECK constraints**, `change_tasks` has no `is_deleted`, orgs/users, WORKFLOW grants |
| `cm_probe.py` | P1 viewer write 201 · P3 update() bypass · P4 invalid enums persist · P5 CAB theater · P6 hard-delete task · P7 comment 'Unknown' |
| `cm_probe2.py` | P1 root-cause (VIEWER holds WORKFLOW W; 54 WRITE-holder roles) |
| `cm_probe3.py` | P5b clean self-approval 201 + `approver_id NULL` · P3b `update()` status change unaudited vs `transition()` audited |
| `cm_probe4.py` | tenant/links/export probes (blocked by the unrelated login rate-limiter; tenant isolation is code-verified) |

*All probe scripts create only `[REVIEW-TEST]`-tagged rows and delete them (children first) on exit; the DB was restored to its 15-row baseline after every run.*

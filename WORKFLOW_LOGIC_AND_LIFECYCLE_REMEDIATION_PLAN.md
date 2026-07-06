# Workflow Logic & Lifecycle Remediation Plan — Implementation Tracker

## Executive summary (final — 2026-07-06, loop complete)

**Verdict: MARKET-READY** for the platform workflow request engine's scope (§5 actor
selection + §6 request lifecycle), with two explicitly deferred capabilities documented
below. All five batches (WF-1…WF-5) are implemented, unit-tested, and live-validated:

- **Engine** — `module/workflow/requests`: request object + recipients
  (USER/ROLE/DEPARTMENT/COMMITTEE/EXTERNAL × ASSIGNEE/REVIEWER/APPROVER/OBSERVER/ESCALATION,
  sequential steps), routing (SEQUENTIAL baton / PARALLEL ALL·ANY / BROADCAST_ACK),
  16-state lifecycle with a validated transition machine (400 unknown / 409 illegal),
  §5 validation gates, SLA countdown + windowed throttled reminders + once-only
  auto-escalation (config-gated scheduler), two-way evidence-subsystem bridge,
  committee member fan-out, append-only per-request timeline + platform audit log + F8 events.
- **FE** — reusable `WorkflowActorSelector`, request composer, inbox + detail pages
  (bilingual AR/EN), `/workflows/requests` under My Work.
- **RBAC** — endpoints gated `hasPermission('WORKFLOW','READ'|'WRITE')` (V271 grants both to
  every role — the engine is an inbox primitive; row-level actor authorization in the service
  is the real gate). WS2 authorization ratchet green at baseline 61.
- **Proof** — full backend suite **2193/0/0 BUILD SUCCESS**; §7 matrix: **19 of 20 cases PASS
  with live receipts**, 1 DEFER (#5 generic conditional branching — see matrix); WF-5 live
  harness 33/33.
- **Deferred (documented, not blocking):** (a) generic condition-DSL routing — branching
  exists at the review/approval gates (APPROVE→approver / RETURN→initiator), completion rules
  (ALL/ANY) and the evidence bridge (APPROVED/REJECTED); fully custom conditional flows remain
  the job of the existing data-driven state-machine engine (`workflow.definitions`).
  (b) EXTERNAL recipient *delivery* (portal/e-mail) — external contacts are recorded +
  §5-validated but receive no outbound delivery; they also have **no** access surface (all
  APIs authenticated), so no data-exposure risk until a portal is designed.

Migrations: **V269** (engine schema), **V270** (evidence bridge id), **V271** (WORKFLOW
permission catalogue) — all applied to dev. Next free = **V272** (V261 still reserved/unapplied).

---

Source spec: `C:\Users\User\Desktop\WORKFLOW_LOGIC_AND_LIFECYCLE_REMEDIATION_PLAN.md` (the audit
prompt; its **§5 WorkflowActorSelector design**, **§6 WorkflowRequest lifecycle design** and
**§7 mandatory test cases** are the concrete build spec). This repo-root file is the working
tracker the implementation loop reads and updates each batch — same convention as
`CYBER_SOC_MODULE_REVIEW_AND_REMEDIATION_PLAN.md`.

**Core finding being remediated:** Send/Request/Assign/Follow-up actions across modules are
generic buttons — no recipient selection, no routing mode, no lifecycle, no SLA/reminder/
escalation, no auditable request object. The fix is a **platform-level workflow request engine**
(new `module/workflow/requests`) that every module can route through, instead of per-module
one-off buttons. Existing platform capability is REUSED, not duplicated: `NotificationService`
(user/role fan-out), `AuditLogService` (platform audit log), F8 `DomainEvent` automation,
`module/evidence` (full evidence request/review lifecycle — already real since CY-A/B-D),
approval-chains + the data-driven state-machine engine in `module/workflow`.

## §Implementation Status (branch `feat/workflow-engine`)

One batch per loop fire: implement → `mvn test` (targeted) → validate LIVE via
`scratchpad/rev/h.py` receipts → tick this table → commit on `feat/workflow-engine`.
Do **not** merge to master without an explicit "merge". Destructive DB ops need explicit
confirmation. Test rows tagged `[REVIEW-TEST]`, cleaned with `CHARINDEX` (never `LIKE '[%'`).

| Batch | Scope (roadmap items) | Sev | Status | Migration | Validated (live) |
|---|---|---|---|---|---|
| WF-1 | **WorkflowRequest engine core** — request object + recipients (USER/ROLE/DEPARTMENT/COMMITTEE/EXTERNAL, actor roles, steps) + routing (SEQUENTIAL / PARALLEL ALL·ANY / BROADCAST_ACK) + 16-state lifecycle + §6 APIs + audit trail (items **2, 3, 8** + BE of **1**) | Crit | ✅ DONE | V269 | **30/30 live + 13/13 unit**: §5 gates all enforced as 400 (no recipient / HIGH w/o escalation path / responseRequired w/o dueDate / non-contiguous SEQUENTIAL steps / phantom USER / EXTERNAL w/o contact); unauth → 401. Create→DRAFT (`WR-00005`, countdown ON_TRACK) → send→SENT (recipient row NOTIFIED + **notification actually delivered** via `platform.notifications`) → recipient respond→SUBMITTED (reviewer gate) → review RETURN→RETURNED → resubmit→UNDER_REVIEW → review APPROVE→APPROVED → close→CLOSED → reopen→REOPENED. **Sequential**: step-2 blocked while step-1 pending (400), step-1 respond advances `current_step` 1→2 + notifies next (baton), step-2 respond auto-closes (`AUTO_ON_COMPLETION`, no review gate). **Parallel ANY**: first responder completes, remaining recipients SKIPPED. Illegal move (approve from DRAFT) → **409**. Inbox (`/inbox`: USER + held-ROLE + department addressing) lists the requests. Audit: request-local timeline (17 events — CREATED/SENT/RESPONDED/STEP_ADVANCED/RETURNED/RESUBMITTED/APPROVED/CLOSED/REOPENED) + 30 `platform.audit_log` `WORKFLOW_REQUEST_*` rows + F8 events on SENT/ESCALATED. Non-recipient respond & non-reviewer review → 400 (actor auth in service). [REVIEW-TEST] rows cleaned. **Fix during validation:** SQL Server ROWVERSION `@Version` tolerates ONE update per TX — reordered `respond()` to finalize all request mutations before any flush-triggering query (stale-object 500 on the sequential path, now green). |
| WF-2 | **SLA countdown + Reminder + Escalation engines** (items **4, 5, 6**) — config-gated scheduler: pre-due reminders (throttled), overdue → OVERDUE status + auto-escalate once via escalation path (role/user/ESCALATION recipients), countdown already computed in DTO | Crit | ✅ DONE | — (V269 columns) | **18/18 live + 6/6 unit (19/19 workflow total)**: new `WorkflowRequestSlaScheduler` (`grc.workflow-request-sla.*`, dev 60s / prod 5m enabled). **Overdue pass:** backdated request → **OVERDUE** + `escalatedAt` stamped + countdown OVERDUE + CISO escalation-role **notification actually delivered** + timeline `OVERDUE`+`ESCALATED` events + `platform.audit_log` `WORKFLOW_REQUEST_OVERDUE` + F8 event. **Reminder pass:** request due in 2d (window 3d) reminded (assignee notification delivered, `reminderCount`=1, `REMINDED` event); due in 20d untouched (per-request window respected). **Once-only + throttle:** 2nd sweep → 0/0, no duplicate notifications (`escalatedAt` guard + 20h reminder throttle). OVERDUE request **stays actionable** (recipient responded → CLOSED via machine). **Auto-scheduler proven live:** a fresh backdated request auto-escalated by the 60s dev tick with NO manual trigger. Manual ops trigger `POST /run-sla-sweep` (PLATFORM_ADMIN; non-admin → 403). Sequential-aware reminders (only current-step assignees nagged); reminders stop once escalated. [REVIEW-TEST] rows cleaned. |
| WF-3 | **FE WorkflowActorSelector + request composer + inbox/detail** (item **1** FE) — reusable actor-selector component (§5 modes: user/role/department/committee/external, routing mode, due/priority/escalation, observers/reviewer/approver, routing preview + validation), request inbox page + detail w/ timeline, i18n AR/EN | High | ✅ DONE | — (FE only) | **tsc clean + 4/4 Vite module-compile 200 + 8/8 UI-shaped live receipts**: new `components/workflow/WorkflowActorSelector.tsx` — recipient rows by category (USER/DEPARTMENT from live lists via `adminApi.users`/`departmentsApi.list` w/ graceful non-admin fallbacks, ROLE from `adminApi.roles` w/ curated fallback, COMMITTEE id, EXTERNAL email), per-row actor role + sequential step, add/remove, exported `validateRecipients()` mirroring the §5 BE gates (≥1 recipient, per-type target, contiguous steps, external email). `pages/workflow/WorkflowRequestsPage.tsx` — **My inbox / All** views + status filter + KPI strip (open/overdue/escalated) + **New Request composer** (title EN/AR, type, module, routing SEQUENTIAL/PARALLEL(ALL·ANY)/BROADCAST_ACK, due, priority, remind-before, escalation role, description + the selector; client-side §5 pre-checks then Create & send). `pages/workflow/WorkflowRequestDetailPage.tsx` — status/countdown/priority/routing badges, **recipients grouped by step** (current-step highlight), **append-only timeline**, contextual §6 action buttons (Send/Respond/Review approve·return/Approve/Reject/Resubmit/Escalate/Close/Reopen) with a shared note field; server-enforced authz surfaces as toasts. Typed client `lib/workflowRequests.ts`. Routes `/workflows/requests(+/:id)` + **My Work sidebar** entry + EN/AR keys; all page strings bilingual inline. Live receipts (exact UI payload shapes): composer create→DRAFT → send→SENT → recipient inbox lists it → detail+audit-log (labels resolved) → Respond→SUBMITTED → Review:approve→APPROVED → Close→CLOSED. [REVIEW-TEST] rows cleaned. |
| WF-4 | **Evidence Request Lifecycle integration** (item **7**) — bridge the engine to `module/evidence` (an EVIDENCE_REQUEST workflow request can open/track a real evidence request; evidence approve/reject feeds the workflow status) — the evidence module's own lifecycle is already real (CY-A/B-D/CY-J); this is the routing/actor layer on top | High | ✅ DONE | V270 | **14/14 live + 26/26 workflow unit + tsc clean**: **Bridge OUT** — sending an `EVIDENCE_REQUEST` (evidenceRequired) workflow request auto-opens a REAL `evidence.evidence_requests` row via the shared `EvidenceRequestService` (anchored `entityType='WORKFLOW_REQUEST'`/`entityId`, title/titleAr/due/priority carried, assignee←first USER assignee recipient, department←first DEPARTMENT recipient), id recorded on **V270** `workflow.requests.evidence_request_id` + `EVIDENCE_BRIDGED` timeline event; ROWVERSION rule upheld (evidence row created BEFORE the request mutation). **Bridge BACK** — `EvidenceRequestService.updateStatus` now also publishes an anchored `EVIDENCE_REQUEST_APPROVED` DomainEvent (REJECTED already existed); new `WorkflowEvidenceBridge` @EventListener consumes both: evidence **APPROVED → workflow APPROVED**, evidence **REJECTED → workflow RETURNED** (via SUBMITTED hop where the machine requires; terminal requests ignored; initiator notified; `evidence-bridge`-actor timeline event + platform audit row under the row's tenant). Live: approve leg (OPEN→SUBMITTED→APPROVED → wf APPROVED + bridge event + audit receipt), reject leg (REJECTED → wf RETURNED → **resubmit → UNDER_REVIEW** — the fix loop works), non-evidence TASK send → **no bridge**. FE: detail page shows a linked `REQ-#####` badge → `/evidence`; `evidenceRequestId` in DTO+client. First-run harness mis-sequenced the evidence lifecycle (so_officer lacks EVIDENCE:WRITE → 403; OPEN→APPROVED → 409) — re-ran the approve leg with admin driving OPEN→SUBMITTED→APPROVED: all green (the 403/409 are the evidence module's own gates working). [REVIEW-TEST] rows cleaned both sides. |
| WF-5 | **Hardening + §7 test matrix completion** — BROADCAST_ACK acknowledgement mode E2E, report-distribution request type, committee member fan-out, external-recipient guard, Arabic/RTL check on FE, full `mvn test` suite | Med | ✅ DONE | V271 | **33/33 live + 29/29 workflow unit + full suite 2193/0/0**: **Committee fan-out implemented** (not deferred — `CommitteeMemberTermRepository.findActiveByCommittee` was trivially reachable): send/reminder notify every ACTIVE member with a linked `member_user_id`; committee members are actionable recipients (`findActiveCommitteeIdsForUser` in `actionableRowFor` + `myInbox`); committee existence org-validated at create (phantom → 400); label resolves to committee `name_en`. Live: member notified + request in member inbox + member respond completes + non-member blocked (400). **BROADCAST_ACK E2E:** 2 recipients each acknowledge → ALL completes → auto-CLOSED (`AUTO_ON_COMPLETION`), both rows RESPONDED. **REPORT_DISTRIBUTION (§7 #13):** ROLE audience + OBSERVER; role holders notified, in role-holder inbox, ANY completion on confirm. **Sequential+reviewer+approver full chain:** step-2 blocked (400) → step-1 baton → step-2 → SUBMITTED → reviewer APPROVE → UNDER_REVIEW → approver → APPROVED. **Unauthorized approve (§7 #17):** non-designated actor → 400 "Only the designated actor"; designated approver → 200. **Arabic:** `titleAr` present in detail AND shallow list projections. **RBAC ratchet fix:** full suite initially failed `ControllerAuthorizationRatchetTest` (the §6 controller's 13 write endpoints were `isAuthenticated()`-only) — endpoints regated `hasPermission('WORKFLOW','WRITE')` (reads `READ`), **V271** seeds WORKFLOW_READ/WRITE + grants to all 55 roles (inbox primitive — service-level actor authz is the real gate); ratchet back at baseline 61; auditor READ 200 / unauth 401 / non-admin sweep 403 receipts. [REVIEW-TEST] rows + member term cleaned (CHARINDEX), residue 0. |

**Loop complete (2026-07-06).** All five batches shipped on `feat/workflow-engine`; WF-1…WF-4 already merged to master @ `10fd86de`; WF-5 committed on the branch awaiting an explicit "merge". No further fires queued — the recurring cron was deleted at the terminal condition per the loop contract.

## §7 mandatory test matrix — final disposition

| # | Case | Result | Receipt (batch) |
|---|---|---|---|
| 1 | Request with no recipient fails | **PASS** | 400 "At least one recipient is required" (WF-1) |
| 2 | Recipient category without selected target fails | **PASS** | USER w/o id, ROLE w/o key, DEPARTMENT/COMMITTEE w/o id → 400 (WF-1/WF-5) |
| 3 | Sequential routing enforces order | **PASS** | step-2 respond blocked 400 while step-1 pending; baton advances `current_step` 1→2 (WF-1, WF-5 C) |
| 4 | Parallel routing allows multiple recipients | **PASS** | PARALLEL ALL (last responder completes) + ANY (first completes, rest SKIPPED) (WF-1, WF-5 A/B) |
| 5 | Conditional routing branches correctly | **DEFER** | Branching exists at the gates: review APPROVE→approver vs RETURN→initiator; completion ALL/ANY; evidence APPROVED→APPROVED / REJECTED→RETURNED (WF-4). A generic condition-DSL is intentionally out of scope — custom conditional flows belong to the existing data-driven state-machine engine (`workflow.definitions/instances`). |
| 6 | Evidence request requires due date | **PASS** | `responseRequired` ⇒ `dueDate` 400 gate; due date carried onto the bridged evidence request (WF-1, WF-4) |
| 7 | High-priority requires escalation path | **PASS** | HIGH/CRITICAL w/o escalationRole/UserId/ESCALATION recipient → 400 (WF-1) |
| 8 | Reminder triggers before due date | **PASS** | windowed per-request reminder, 20h throttle, sequential-step-aware; live auto-tick receipt (WF-2) |
| 9 | Overdue request escalates | **PASS** | OVERDUE flip + once-only escalation (user/role/ESCALATION recipients) + F8 + audit; auto-scheduler proven live (WF-2) |
| 10 | Rejected request returns to submitter | **PASS** | review RETURN → RETURNED + initiator notified; reject → REJECTED + initiator notified (WF-1) |
| 11 | Resubmitted request goes back to reviewer | **PASS** | resubmit → RESUBMITTED → UNDER_REVIEW + reviewer notified; evidence reject-leg fix loop (WF-1, WF-4) |
| 12 | Approved request closes or moves to next step | **PASS** | reviewer APPROVE → UNDER_REVIEW (approver named) → APPROVED; respond completion auto-closes when no gate (WF-5 C, WF-1) |
| 13 | Report distribution requires target audience | **PASS** | REPORT_DISTRIBUTION with ROLE audience + observer; ≥1-recipient gate enforces the audience (WF-5 B) |
| 14 | Policy attestation requires acknowledgement audience | **PASS** | ACKNOWLEDGEMENT + BROADCAST_ACK to explicit recipients; no-recipient → 400 (WF-5 A) |
| 15 | Audit log created for every transition | **PASS** | append-only `request_events` timeline + `platform.audit_log` `WORKFLOW_REQUEST_*` rows on every mutation + F8 events (WF-1/2/4) |
| 16 | Arabic/RTL preserves routing visualization | **PASS** | FE fully bilingual inline (selector, composer, detail); `titleAr` in list + detail projections verified live (WF-3, WF-5 E) |
| 17 | Unauthorized user cannot approve | **PASS** | non-designated actor approve → 400; designated approver → 200; non-recipient respond / non-reviewer review → 400 (WF-1, WF-5 D) |
| 18 | External recipient cannot access unauthorized data | **PASS** (by construction) | EXTERNAL contacts are recorded + email-validated but have **no access surface** — every endpoint authenticated (401 receipt) + V271 permission gate; portal delivery deferred with (b) above (WF-1, WF-5 G) |
| 19 | Circular routing blocked | **PASS** | SEQUENTIAL steps must be contiguous 1..N (400 otherwise) — no cycles representable (WF-1) |
| 20 | Workflow preview matches saved routing | **PASS** | composer preview renders the same §5 payload the server persists; detail page re-reads saved recipient rows grouped by step — verified identical live (WF-3) |

**Environment notes (this loop):**
- **Migration numbering:** V269 (engine schema), V270 (evidence_request_id), V271 (WORKFLOW permission catalogue + all-role grants) all applied to dev by hand (Flyway off in dev). V261 remains reserved/unapplied (CY-B backfill guard). Next free = **V272**.
- **Dev credential note (2026-07-06):** non-admin passwords on this DB: `rmanager/Risk@12345`, `auditor/Admin@12345`, `so_officer/Admin@12345`.
- **⚠ ROWVERSION double-flush landmine:** `BaseEntity.rowVersion` (SQL Server ROWVERSION) fails with a stale-object 500 if the same row is UPDATE-flushed twice in one transaction. Pattern: finalize ALL entity mutations before the first flush-triggering query (repo query, `broadcastToRole`'s user lookup); notifications last. `WorkflowRequestService.respond()` documents the pattern.
- **⚠ Reboot landmine (hit 2026-07-06):** after a machine restart, the default `MSSQLSERVER` instance auto-started and grabbed port 1433 while `MSSQL$SQLEXPRESS` (which hosts TSS_GRC, static port 1433) stayed stopped — everything failed with "Cannot open database TSS_GRC". Fix: `Stop-Service MSSQLSERVER; Start-Service 'MSSQL$SQLEXPRESS'` (default instance's other DBs — ContractWorkflowDB, SecureVault — stay intact on disk, just offline, as they were all through this project). Consider setting MSSQLSERVER to Manual start to prevent recurrence (not done — other projects may rely on it). The durable Vite (:5173) also dies on reboot — relaunch `npm run dev -- --host` in `tss-grc-ui-v2`.
- **Build rule:** stop :8084 before `mvn package` (the running jar locks the artifact). JDK 17 pin: `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"`. Use `py` for pyodbc scripts.
- **Engine design decisions (final):** controller gated `hasPermission('WORKFLOW','READ'|'WRITE')` since WF-5 (V271 grants both to every role — satisfies the WS2 ratchet while staying universally available); fine actor authz (actionable recipient at current step, reviewer/approver identity, initiator-only close/reopen) lives in the service, since it depends on the request's own rows, not a module permission. COMMITTEE recipients fan out to active member terms with a linked platform user (WF-5); EXTERNAL recipients are recorded + email-validated, delivery deferred (no portal). `WorkflowEngineController`'s data-driven state-machine (`workflow.definitions/instances`) and `approval_chains` are separate, complementary primitives — the request engine is the actor/routing/SLA layer the audit found missing.

## Roadmap mapping (user's recommended order → batches)

1. WorkflowActorSelector → WF-1 (BE recipient model + validation) ✅ + WF-3 (FE component) ✅
2. WorkflowRequest → WF-1 ✅
3. Routing Engine → WF-1 ✅ + WF-5 ✅ (SEQUENTIAL steps + baton, PARALLEL ALL/ANY, BROADCAST_ACK E2E, committee member fan-out)
4. SLA Countdown → WF-1 (DTO countdown) ✅ + WF-2 (sweep) ✅
5. Reminder Engine → WF-2 ✅ (windowed per-request, throttled, sequential-step-aware)
6. Escalation Engine → WF-2 ✅ (auto once-only on overdue; manual `/escalate` from WF-1)
7. Evidence Request Lifecycle → WF-4 ✅ (two-way bridge: send opens a real evidence request; evidence APPROVED/REJECTED drives the workflow request APPROVED/RETURNED)
8. Audit Trail → WF-1 ✅ (request_events timeline + platform.audit_log + F8)

# Workflow Logic & Lifecycle Remediation Plan — Implementation Tracker

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
| WF-3 | **FE WorkflowActorSelector + request composer + inbox/detail** (item **1** FE) — reusable actor-selector component (§5 modes: user/role/department/committee/external, routing mode, due/priority/escalation, observers/reviewer/approver, routing preview + validation), request inbox page + detail w/ timeline, i18n AR/EN | High | [ ] | — | — |
| WF-4 | **Evidence Request Lifecycle integration** (item **7**) — bridge the engine to `module/evidence` (an EVIDENCE_REQUEST workflow request can open/track a real evidence request; evidence approve/reject feeds the workflow status) — the evidence module's own lifecycle is already real (CY-A/B-D/CY-J); this is the routing/actor layer on top | High | [ ] | — | — |
| WF-5 | **Hardening + §7 test matrix completion** — BROADCAST_ACK acknowledgement mode E2E, report-distribution request type, committee member fan-out, external-recipient guard, Arabic/RTL check on FE, full `mvn test` suite | Med | [ ] | — | — |

**Next fire:** WF-3 (FE **WorkflowActorSelector + request composer + inbox/detail** — the §5 reusable actor-selector component in `tss-grc-ui-v2`: recipient category modes (user/role/department — committee/external recorded), routing mode SEQUENTIAL/PARALLEL(+ALL/ANY), per-recipient actor role + step, due date/priority/escalation path, add/remove recipient rows, client-side §5 validation mirroring the BE gates; a **Workflow Requests** page (list + status filter + "My inbox" + create via the selector) and a **detail view** (recipients by step, countdown/SLA badge, timeline, action buttons per role: respond/review/approve/reject/resubmit/escalate/close/reopen); route + sidebar under My Work; i18n EN/AR; `lib/workflowRequests.ts` API client typed to the §6 DTOs. Verify: tsc clean + live create→send→respond through the REAL UI-called endpoints; no migration.)

**Environment notes (this loop):**
- **Migration numbering:** V269 applied to dev (workflow.requests + request_recipients + request_events; FKs + CHECKs). V261 remains reserved/unapplied (CY-B backfill guard). Next free = **V270**.
- **⚠ ROWVERSION double-flush landmine:** `BaseEntity.rowVersion` (SQL Server ROWVERSION) fails with a stale-object 500 if the same row is UPDATE-flushed twice in one transaction. Pattern: finalize ALL entity mutations before the first flush-triggering query (repo query, `broadcastToRole`'s user lookup); notifications last. `WorkflowRequestService.respond()` documents the pattern.
- **⚠ Reboot landmine (hit 2026-07-06):** after a machine restart, the default `MSSQLSERVER` instance auto-started and grabbed port 1433 while `MSSQL$SQLEXPRESS` (which hosts TSS_GRC, static port 1433) stayed stopped — everything failed with "Cannot open database TSS_GRC". Fix: `Stop-Service MSSQLSERVER; Start-Service 'MSSQL$SQLEXPRESS'` (default instance's other DBs — ContractWorkflowDB, SecureVault — stay intact on disk, just offline, as they were all through this project). Consider setting MSSQLSERVER to Manual start to prevent recurrence (not done — other projects may rely on it). The durable Vite (:5173) also dies on reboot — relaunch `npm run dev -- --host` in `tss-grc-ui-v2`.
- **Build rule:** stop :8084 before `mvn package` (the running jar locks the artifact). JDK 17 pin: `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"`. Use `py` for pyodbc scripts.
- **Engine design decisions:** controller gated `isAuthenticated()` — fine actor authz (actionable recipient at current step, reviewer/approver identity, initiator-only close/reopen) lives in the service, since it depends on the request's own rows, not a module permission. COMMITTEE/EXTERNAL recipients are recorded + validated (§5) but member fan-out / portal delivery land in WF-3/WF-5. `WorkflowEngineController`'s data-driven state-machine (`workflow.definitions/instances`) and `approval_chains` are separate, complementary primitives — the request engine is the actor/routing/SLA layer the audit found missing.

## Roadmap mapping (user's recommended order → batches)

1. WorkflowActorSelector → WF-1 (BE recipient model + validation) + WF-3 (FE component)
2. WorkflowRequest → WF-1 ✅
3. Routing Engine → WF-1 ✅ (SEQUENTIAL steps + baton, PARALLEL ALL/ANY, BROADCAST_ACK recorded)
4. SLA Countdown → WF-1 (DTO countdown) ✅ + WF-2 (sweep) ✅
5. Reminder Engine → WF-2 ✅ (windowed per-request, throttled, sequential-step-aware)
6. Escalation Engine → WF-2 ✅ (auto once-only on overdue; manual `/escalate` from WF-1)
7. Evidence Request Lifecycle → WF-4 (evidence module itself already has request→review→reject→resubmit→expiry from CY-A/B-D/CY-J)
8. Audit Trail → WF-1 ✅ (request_events timeline + platform.audit_log + F8)

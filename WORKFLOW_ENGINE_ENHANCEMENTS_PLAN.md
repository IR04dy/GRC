# Workflow Engine — Enhancements & Gap-Closure Plan

Follow-on to `WORKFLOW_LOGIC_AND_LIFECYCLE_REMEDIATION_PLAN.md` (WF-1…WF-7, all merged to
master @ `fe2da28a`; §7 matrix 20/20 PASS, suite 2205/0/0). That work delivered the platform
workflow **request engine**. This plan closes the residual gaps the follow-up audit found
against the *maximal* wording of the Desktop spec (§2 concepts, §5 selector, §2.3 routing),
with the emphasis the user set: **recipient selection must be complete and realistic** — no
"send in the air", reusable distribution lists, owner suggestions, and committee quorum.

Branch `feat/workflow-recipients` (off master @ `fe2da28a`). Loop contract as before: implement
→ targeted `mvn test` → live receipts via `scratchpad/rev/h.py` → full suite → commit; no merge
without explicit "merge"; test rows `[REVIEW-TEST]`, cleaned with `CHARINDEX`.

## Decision gate (spec LOOP 4 — KEEP / IMPROVE / ADD / MERGE / REMOVE / DEFER / GLOBALIZE)

| # | Enhancement (gap from the audit) | Spec ref | Decision | Sev | This batch (WF-8/9)? |
|---|---|---|---|---|---|
| 1 | **Reusable recipient lists / distribution lists** — save a named recipient set, reuse it on any request | §2.2 "custom distribution list", §5 "Save Routing Template", "Select by distribution list" | **ADD (GLOBALIZE)** | High | ✅ WF-8 |
| 2 | **Recipient suggestions for the source record** — suggest owners/roles when composing (control owner, evidence owner, dept owner, compliance owner) | §2.1 example, §2.2 "by linked object owner", §5 "Select by linked owner / dynamic rule" | **ADD** | High | ✅ WF-8 |
| 3 | **Committee QUORUM / MAJORITY completion** — a committee (or panel) completes on quorum/majority, not just ALL/ANY | §2.3 #7 "Committee review", §5 "Parallel must define completion rule" | **ADD** | High | ✅ WF-9 |
| 4 | **Committee → member expansion** — expand a COMMITTEE recipient into per-member gates so quorum/majority is real | §2.3 #7 | **ADD** | High | ✅ WF-9 |
| 5 | **§5 validation hardening** — APPROVAL request requires an approver; approval gate can't be empty | §5 "Approver is required for approval workflows" | **IMPROVE** | Med | ✅ WF-9 |
| 6 | **`PUT /:id` edit (DRAFT only)** — the one missing §6 API; edit a request before it is sent | §6 API list | **ADD** | Med | ✅ WF-9 |
| 7 | **Multi-tier escalation** (L1 → L2 → L3 ladder) | §2.3 #6, §2.5 "Manager / Executive escalation" | **ADD** | Med | ✅ WF-10 |
| 8 | **Recurring attestation reminders** — periodic re-attest cadence | §2.5 "Recurring attestation reminder" | **DEFER** | Low | ⏳ — belongs with the policy-attestation engine |
| 9 | **In-platform outbound email** for EXTERNAL links | §2.3 #8 | **DEFER** | Med | ❌ no SMTP/outbox bean exists platform-wide; link is surfaced to the initiator to relay (documented) |
| 10 | **Migrate all 14 legacy modules' Send/Request/Assign buttons onto the engine** | §3 (all modules) | **DEFER (own program)** | High | ❌ each module was hardened in its own loop; wholesale re-plumbing is a multi-module migration, scoped separately — NOT a single batch |
| 11 | Reviewer-required-for-review-workflows | §5 | **REMOVE (as a hard gate)** | — | Intentionally NOT enforced: many valid review flows auto-complete/parallel-ack; forcing a reviewer breaks them. Reviewer stays optional; documented |
| 12 | Business-unit / module-owner selector modes | §2.2 | **MERGE** | Low | Covered by DEPARTMENT (business unit) + ROLE (module owner); no distinct primitive needed |

## WF-8 — Recipient selection completeness (the headline)

- **V275**: `workflow.recipient_lists` (id, org, name, description, is_active, audit) +
  `workflow.recipient_list_members` (list_id, recipient_type, recipient_id, recipient_key,
  recipient_label, actor_role, step_no). Also widens `ck_wfr_completion` → ALL/ANY/QUORUM/MAJORITY
  and adds `workflow.requests.completion_threshold` (for QUORUM).
- Entities + repo + `WorkflowRecipientListService` + `WorkflowRecipientListController`
  (`/api/v1/workflows/recipient-lists`, `hasPermission('WORKFLOW',…)`): CRUD + a save-from-set.
- `CreateRequest.recipientListId` — on create, the saved list's members are expanded into the
  request's recipients (merged with any explicit `recipients`). "Send to a distribution list."
- `GET /api/v1/workflows/requests/recipient-suggestions?sourceModule=&sourceRecordType=&sourceRecordId=`
  → suggested recipients: the initiator's department, the module's owner roles (from the RBAC
  catalogue), and the source record's owner where resolvable — so the composer proposes
  "control owner / evidence owner / dept owner / compliance owner" instead of a blank form.
- FE: `WorkflowActorSelector` gains a **Saved list** picker + **Save as list**, and a
  **Suggest recipients** action; `workflowRequests.ts` client + types.

## WF-9 — Routing & lifecycle completeness

- **Committee QUORUM / MAJORITY**: `advanceRouting` counts responded ASSIGNEE gates vs a
  threshold — MAJORITY = ⌊n/2⌋+1, QUORUM = `completionThreshold` (min responses). Remaining
  gates SKIPPED once met. Works for panels and (via expansion) committees.
- **Committee → member expansion**: `CreateRequest.expandCommittees` (or a per-recipient flag)
  materializes one USER gate per active committee member at create, so quorum/majority is real
  and each member is individually actionable/auditable.
- **§5 validation**: APPROVAL requestType requires an approver (`approverId` or an APPROVER-role
  recipient) → else 400.
- **`PUT /api/v1/workflows/requests/{id}`**: edit mutable fields (title/description/due/priority/
  reminder/escalation/recipients/branch rules) while status = DRAFT only → else 409.
- FE: composer completion-rule dropdown gains QUORUM/MAJORITY (+ threshold input) and an
  "expand committee to members" toggle; detail page shows quorum progress (k of n).

## WF-10 — multi-tier escalation (DONE)

- **V276**: `escalation_tiers` JSON ladder `[{level, role, userId, afterDays}]` + `escalation_level`
  on `workflow.requests`. Tiers auto-numbered 1..N; validated (each rung needs a role or userId;
  afterDays ≥ 0 and non-decreasing). A ladder satisfies the §5 HIGH/CRITICAL escalation-path gate.
- **Manual `/escalate`** steps up exactly one tier per call (no illegal `ESCALATED→ESCALATED`
  self-transition), notifying that rung (role broadcast + named user) + F8 with the level.
- **SLA sweep** climbs to the highest tier whose `afterDays` has elapsed past due, notifying each
  newly-reached rung once, and keeps climbing on later sweeps (the overdue pass now re-scans
  OVERDUE rows; a no-new-tier re-scan is a no-op). Requests without a ladder keep the original
  once-only single-path escalation unchanged.
- FE: `EscalationTier` type + `escalationTiers` in create/detail; detail page renders the ladder
  with the current tier highlighted. Full suite green, live 13/13, tsc + Vite clean.

## Deferred (documented, not silently dropped)

- **Outbound email** — no SMTP/dispatch bean exists anywhere in the platform; wiring it is a
  platform-infra task, not a workflow-engine task. External tracking links are surfaced to the
  initiator (WF-7).
- **14-module button migration** — a separate program: each module (ERM/BCM/PDPL/SCM/…) already
  hardened its own workflows in prior loops; routing them all through `module/workflow/requests`
  is large and module-coupled. Scoped as its own plan, not this batch.

## Verification (per batch)

Targeted `mvn test` → live receipts (new `w_wf8.py` / `w_wf9.py`: distribution-list expand,
suggestions, quorum completion, committee expansion, approval-gate 400, DRAFT-edit + 409 after
send) → full `mvn test` (expect 2205 + new) → tsc + Vite-200 for FE → commit. `[REVIEW-TEST]`
rows cleaned with `CHARINDEX`.

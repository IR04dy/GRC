# 12 — Workflow Engine Specification

The platform offers two complementary mechanisms for workflow:

1. **Service-local switch state machines** — fast, simple, used by
   the majority of modules. The state machine lives in the
   service's `transition()` method (see
   `12_Backend_Specification.md` for the canonical pattern).
2. **Generic configurable engine** — `module/workflow` for cases
   where the state machine is configured at runtime per-tenant
   (typical for approval chains and custom intake flows).

## Generic engine — `module/workflow`

### Entities

| Entity | Purpose |
|---|---|
| `WorkflowDefinition` | reusable template: name, version, owning entity_type |
| `WorkflowState` | per-definition, named state with metadata (final flag, role-gating) |
| `WorkflowTransition` | per-definition, `from_state_id → to_state_id`, optional `condition_expr`, required role |
| `WorkflowInstance` | one running flow, owns `(entity_type, entity_id)` + `current_state_id` |
| `WorkflowActionLog` | every transition taken, with actor/time/before/after |

### Transition logic

```
serviceCallsEngine.transition(instanceId, targetStateName, actor) →
  find allowed transitions from current state
  check role(actor) is in transition.required_role
  evaluate condition_expr (Spring SpEL) against the instance's payload
  if OK:
    update current_state_id
    write WorkflowActionLog
    publish DomainEvent("WORKFLOW_TRANSITIONED", entity_type, entity_id, payload)
    audit-log via F7
  else:
    throw IllegalTransitionException
```

### Why two engines

Service-local switches win on **clarity** and **performance** — the
state machine is right next to the business logic. The generic
engine wins when:

- The state graph is genuinely tenant-configurable (e.g.
  approval chains with different signatories per org).
- The transition matrix is too large to hand-edit per release.
- A no-code editor is needed (intake workflows).

For most domain rules (Risk lifecycle, BIA, BCP, NDI cycle), the
switch pattern is the right tool — and the tests lock the matrix
unambiguously.

## Audit + observability

Every transition — whether through a service switch or the generic
engine — emits an audit row via F7
(`actionType=TRANSITION`,
`workflowStatusBefore`/`workflowStatusAfter`, optional
`digitalSignatureRef` when an approval required a signature) and a
domain event for F8 to consume.

## Notifications + escalations

Transitions optionally:

- Notify the new owner via `WORKFLOW_TASK_ASSIGNED` template.
- Schedule an escalation if `due_date` is set
  (`approvals.scheduled_escalations`).
- Open an approval step (`approvals.approval_steps`).

## SLA tracking

Workflow tasks can carry `due_date`. The nightly watchdog
(`approvals.OverdueScheduler`) marks overdue tasks and writes
audit rows with severity WARN.

## State machine catalog (snapshot)

| Workflow | Engine | Source |
|---|---|---|
| Policy lifecycle | switch | `policy.PolicyService.transition` |
| Risk lifecycle | switch | `risk.RiskService.transition` |
| Treatment | switch | `risk.RiskTreatmentService.transition` |
| BCP / DRP | switch | `bcm.BcmService.transition` / `bcm.DisasterRecoveryPlanService.transition` |
| Cyber control assessment | switch | `cyber.CyberAssessmentService` |
| NDI cycle | switch | per-slice services in `ndi.*` |
| Submission package | switch | `ndi.NdiSubmissionPackageService` |
| Approval chain | generic engine | `approvals.ApprovalRequestService` |
| Intake → domain promotion | generic engine | `intake.IntakeService` |
| Authority Document lifecycle | switch | `regulatory.AuthorityDocumentService` (F1) |
| Control lifecycle | switch | `controls.ControlLibraryService` (F2) |

## Adding a workflow

1. Decide engine: switch (default) vs. generic.
2. For switch: add the matrix to the service + 4-6 unit tests
   covering happy / illegal / terminal / re-entry paths.
3. For generic: insert `WorkflowDefinition` + states + transitions
   via Flyway seed + emit `WorkflowInstance` rows from the owning
   service.
4. Publish a `DomainEvent` on every transition so F8 automation
   rules can react.
5. Add a row to `02_Module_Architecture.md`'s workflow list.

---

**See also:** `12_Backend_Specification.md`,
`module/workflow`, `module/approvals`, F8 automation engine.

# Workflow Engine Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the design of the TSS GRC Platform's built-in workflow engine — a lightweight, configurable approval and review engine that supports multi-step workflows, SLA enforcement, delegation, escalation, and full audit history without requiring an external BPMN engine.

---

## Design Philosophy

The workflow engine is a **custom, lightweight state machine** implemented within the Spring Boot application. This approach was chosen over external BPMN engines (Camunda, Activiti, Flowable) because:

- The platform's workflow patterns are well-defined and bounded (approval chains, reviews, escalations)
- External BPMN engines add significant operational complexity and licensing cost
- A custom engine gives full control over the workflow data model and SQL Server storage
- Simpler onboarding for developers maintaining the platform

The workflow engine is a **shared platform service** — any entity in any module can be workflow-enabled by specifying `entity_type` and `entity_id`.

---

## Core Concepts

| Concept | Description |
|---------|-------------|
| **Workflow Definition** | Template defining steps, roles, SLAs for a specific entity type in a module |
| **Workflow Step Definition** | Individual step within a workflow: type, assignee rule, SLA, escalation rule |
| **Workflow Instance** | Runtime execution of a workflow for a specific entity |
| **Workflow Instance Step** | Runtime state of a specific step in an active instance |
| **Task** | An assignee's view of a pending workflow instance step |

---

## Workflow States

```
[Created] → DRAFT → SUBMITTED → IN_PROGRESS → APPROVED
                                             → REJECTED → (back to DRAFT for revision)
                                             → ESCALATED
                              → CANCELLED
```

### State Definitions

| State | Description |
|-------|-------------|
| `DRAFT` | Entity created, workflow not yet started |
| `SUBMITTED` | Entity submitted for review; first workflow step activated |
| `IN_PROGRESS` | Workflow is progressing through steps |
| `APPROVED` | All required steps completed with positive decisions |
| `REJECTED` | A step was rejected; entity returned for correction |
| `ESCALATED` | Step SLA exceeded; escalation rule triggered |
| `CANCELLED` | Workflow cancelled by initiator or admin |
| `COMPLETED` | Final state after approved/rejected/cancelled |

---

## Step Types

| Type | Description |
|------|-------------|
| `APPROVAL` | Assignee must approve or reject |
| `REVIEW` | Assignee must review and optionally comment; cannot reject |
| `NOTIFICATION` | Automatic step; sends notification and advances immediately |
| `ASSIGNMENT` | Assigns entity to a responsible user; no decision required |
| `CONDITIONAL` | Evaluates a condition; routes to different next step |

---

## Assignee Resolution

For each step, the engine resolves the assignee using:

1. **ROLE-based**: All users with the specified role in the entity's organization receive the task; first to act completes the step
2. **USER-specific**: A named user is assigned the task
3. **BU_HEAD**: The head of the business unit owning the entity is assigned
4. **DYNAMIC**: Application code provides the assignee at runtime (e.g., the entity's owner)

---

## SLA and Escalation

```
Step created
    → SLA timer starts (due_at = started_at + sla_hours)
    → Scheduled job checks overdue steps every 15 minutes
    → If overdue:
        → Step status set to ESCALATED
        → Notification sent to escalation_role_id members
        → Original assignee also notified
```

---

## Parallel Approvals

Steps can be configured as parallel (`is_parallel = true`):
- Multiple users receive the task simultaneously
- Configurable completion rule: ANY (first approval completes) or ALL (all must approve)

---

## Database Tables

```
workflow.workflow_definitions
    └── workflow.workflow_steps (ordered steps)

workflow.workflow_instances (entity_type + entity_id + status)
    └── workflow.workflow_instance_steps (per-step runtime state)
```

---

## Spring Boot Integration

### WorkflowService Interface

```java
public interface WorkflowService {
    WorkflowInstance startWorkflow(String entityType, Long entityId, Long initiatedBy);
    WorkflowInstance submitForApproval(Long instanceId, Long submittedBy, String comments);
    WorkflowInstance approveStep(Long instanceStepId, Long approvedBy, String comments);
    WorkflowInstance rejectStep(Long instanceStepId, Long rejectedBy, String comments);
    WorkflowInstance delegateStep(Long instanceStepId, Long delegateTo, Long delegatedBy);
    WorkflowInstance cancelWorkflow(Long instanceId, Long cancelledBy, String reason);
    List<WorkflowTask> getPendingTasksForUser(Long userId);
    WorkflowInstance getInstanceForEntity(String entityType, Long entityId);
}
```

### Workflow Events (Spring ApplicationEvents)

- `WorkflowStartedEvent(instanceId, entityType, entityId)`
- `WorkflowStepCompletedEvent(instanceId, stepId, decision)`
- `WorkflowStepOverdueEvent(instanceId, stepId)`
- `WorkflowCompletedEvent(instanceId, finalStatus)`
- `WorkflowEscalatedEvent(instanceId, stepId)`

### Module Integration Pattern

Any module entity becomes workflow-enabled by:

1. Calling `WorkflowService.startWorkflow("risk", riskId, userId)` on entity creation
2. Listening to `WorkflowCompletedEvent` to update entity status
3. Displaying workflow history on entity detail page via shared Thymeleaf fragment

---

## Admin Configuration

Administrators configure workflows via the Admin UI:
- Create/edit workflow definitions per entity type
- Configure steps: order, type, assignee rule, SLA hours, escalation rule
- Activate/deactivate workflow definitions
- Preview workflow diagram

---

## Audit Trail

Every workflow action is logged:
- Instance creation, step transitions, decisions, comments, escalations, delegations
- Logged to `audit.audit_logs` with action type `APPROVE`, `REJECT`, etc.
- Workflow history is also stored in `workflow.workflow_instance_steps` for display

---

## Scheduled Jobs

Since SQL Server Express Edition does not include SQL Server Agent, all scheduled workflow tasks are handled by **Spring Scheduling** (`@Scheduled`):

```java
@Scheduled(fixedDelay = 900000) // Every 15 minutes
public void checkOverdueWorkflowSteps() {
    // Find steps where due_at < NOW() and status = IN_PROGRESS
    // Set status to ESCALATED
    // Publish WorkflowStepOverdueEvent
}
```

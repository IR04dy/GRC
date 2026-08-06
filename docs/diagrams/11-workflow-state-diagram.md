# Workflow State Diagram
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the state machine for the TSS GRC Platform workflow engine, covering all workflow states and valid transitions.

---

## Core Workflow State Machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Entity Created

    DRAFT --> SUBMITTED : User submits for approval
    DRAFT --> CANCELLED : Initiator cancels

    SUBMITTED --> IN_PROGRESS : First step activated
    SUBMITTED --> CANCELLED : Initiator cancels before processing

    IN_PROGRESS --> IN_PROGRESS : Step approved, next step activated
    IN_PROGRESS --> APPROVED : All steps approved
    IN_PROGRESS --> REJECTED : Any step rejected
    IN_PROGRESS --> ESCALATED : SLA exceeded on a step
    IN_PROGRESS --> CANCELLED : Admin cancels

    ESCALATED --> IN_PROGRESS : Escalation actioned, step reassigned
    ESCALATED --> APPROVED : Escalation approver approves
    ESCALATED --> REJECTED : Escalation approver rejects

    REJECTED --> DRAFT : Entity returned for revision
    REJECTED --> CANCELLED : Initiator gives up

    APPROVED --> [*] : Workflow completed
    CANCELLED --> [*] : Workflow terminated
```

---

## Workflow Step State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : Instance step created

    PENDING --> IN_PROGRESS : Assigned user opens task
    PENDING --> ESCALATED : SLA exceeded before start

    IN_PROGRESS --> COMPLETED_APPROVED : Assignee approves
    IN_PROGRESS --> COMPLETED_REJECTED : Assignee rejects
    IN_PROGRESS --> COMPLETED_RETURNED : Assignee returns for revision
    IN_PROGRESS --> DELEGATED : Assignee delegates to another user
    IN_PROGRESS --> ESCALATED : SLA exceeded during work

    DELEGATED --> IN_PROGRESS : Delegate accepts task

    ESCALATED --> IN_PROGRESS : Escalation role member picks up task

    COMPLETED_APPROVED --> [*]
    COMPLETED_REJECTED --> [*]
    COMPLETED_RETURNED --> [*]
    SKIPPED --> [*] : Conditional step skipped
```

---

## SLA and Escalation Flow

```mermaid
flowchart TD
    A[Step Created] --> B[SLA Timer Starts]
    B --> C{SLA Exceeded?}
    C -->|No| D[Normal Processing]
    C -->|Yes| E[Mark Step ESCALATED]
    E --> F[Notify Escalation Role]
    E --> G[Notify Original Assignee]
    F --> H[Escalation Actor Reviews]
    H --> I{Decision}
    I -->|Approve| J[Step COMPLETED_APPROVED]
    I -->|Reject| K[Step COMPLETED_REJECTED]
    I -->|Reassign| L[Step back to IN_PROGRESS]
```

---

## Module Entity Workflow Mapping

| Module | Entity | Default Workflow |
|--------|--------|-----------------|
| ERM | Risk | Risk Review & Approval |
| ERM | Risk Treatment | Treatment Plan Approval |
| Cyber | Cyber Incident | Incident Response |
| BCM | BCM Plan | Plan Approval |
| Privacy | Privacy Assessment | PIA/DPIA Approval |
| Privacy | Breach Record | Breach Management |
| Compliance | Assessment | Assessment Approval |
| Admin | User Creation | User Provisioning |
| Admin | License Request | License Approval |

---

## Task Inbox Flow

```mermaid
flowchart LR
    WF[Workflow Step Activated] --> ASSIGN[Resolve Assignee]
    ASSIGN --> NOTIFY[Notify Assignee: Email + In-App]
    NOTIFY --> INBOX[Task appears in My Tasks]
    INBOX --> ACTION{Assignee Action}
    ACTION -->|Approve| NEXT[Advance to Next Step]
    ACTION -->|Reject| RETURN[Return to Initiator]
    ACTION -->|Delegate| DELEGATE[Reassign to Delegate]
    ACTION -->|SLA Breach| ESCALATE[Escalation Triggered]
```

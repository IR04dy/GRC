# 09 — Escalation Rules
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Escalations ride the same engine as notifications (F8 actions of
`type: ESCALATE`). They differ in that they (1) transfer ownership
of a workflow task and (2) start a new SLA timer at the higher
level.

## Catalog

| Rule | Trigger | From | To | After |
|---|---|---|---|---|
| AR-06 | `RISK_APPETITE_BREACHED` | Risk Owner | Risk Committee | 0 (immediate) |
| AR-10 | `DSR_SLA_NEAR` | Privacy Manager | DPO | 0 |
| AR-12 | `CAPA_OVERDUE` | CAPA Owner | Owner's Manager | 0 |
| AR-13 | `VULN_SLA_BREACH` | Vuln Manager | Cybersecurity Manager | 0 |
| AR-04 | `INCIDENT_CREATED` (PDPL) | DPO | Legal Reviewer + Exec | 72h post-create (PDPL deadline) |
| (board) | `BOARD_APPROVAL_REQUIRED` overdue | Initiator | CEO | 7d after first request |

## Mechanics

1. Engine evaluates the rule's action `{ "type": "ESCALATE", "to_role": "X", "after_minutes": N }`.
2. If `after_minutes > 0`, the engine schedules the escalation via
   `approvals.scheduled_escalations` (a small queue table polled
   every 60s by `EscalationScheduler`).
3. When the scheduled time arrives:
   - The current owner's task is reassigned to a user in `to_role`.
   - A new audit-log row is written with
     `actionType='ESCALATION'`, `severity='WARN'`.
   - A notification fires using template
     `WORKFLOW_TASK_ASSIGNED` to the new owner.
   - If `also` is set in the action, those roles are notified in
     parallel (e.g. cc the originator's manager).

## Per-domain example: CAPA overdue (AR-12)

- CAPA `due_date` passes → `module/capa` publishes
  `DomainEvent(triggerEvent="CAPA_OVERDUE", entityType="CapaTask")`.
- AR-12 fires synchronously: SUCCESS run + audit row.
- Action `{ "type":"ESCALATE", "to_role":"OWNER_MANAGER", "after_minutes":0 }` runs.
- `approvals.scheduled_escalations` row inserted with `due_at = now`.
- Within 60s the scheduler picks it up:
  - resolves OWNER_MANAGER (uses `iam.users.manager_user_id`),
  - reassigns the CAPA task,
  - notifies the manager via `WORKFLOW_TASK_OVERDUE` template.

## SLA timers per workflow

| Workflow | SLA | Source |
|---|---|---|
| DSR — initial response | 30 days (PDPL) | `privacy_lc.DsrCaseService` |
| DSR — full response | 45 days (extendable to 90) | same |
| Breach notification | 72 hours (PDPL Article 27) | `privacy.DataBreachService` |
| CAPA closure | record-defined `due_date` | `module/capa` |
| Vulnerability remediation | severity-based (Critical 15d / High 30d / Medium 60d) | `cyber_lc.VulnerabilityService` |
| Audit finding closure | record-defined `target_close_date` | `module/audit_lc` |
| NCA evidence expiry warning | 60 days before expiry | `module/evidence` |
| Risk acceptance expiry warning | 30 days before expiry | `module/risk` |

## Stuck-task watchdog

A nightly job inspects `approvals.approval_steps` where
`status='PENDING' AND due_date < today() - 7`. Any row → audit
`severity='ERROR'`, notification to `GRC Administrator`, and an
incident-classified-as-OPERATIONAL row in `module/incident`.

## Repeated escalation

If a task is escalated and the new owner also misses the SLA, the
next firing pushes one level higher (manager's manager). The
escalation chain has a cap of 3 levels; beyond that the platform
opens an `Issue` in `module/capa` flagging the workflow as stuck.

---

**See also:** `09_Automation_Rules.md`, `module/approvals`,
`module/platform/Notification`.

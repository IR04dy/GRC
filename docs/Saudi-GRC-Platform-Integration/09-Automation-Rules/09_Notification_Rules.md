# 09 — Notification Rules
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Notifications fan out through `module/platform/NotificationService` +
the F8 automation rule action `type: NOTIFY`. Each notification lands
in `platform.notifications` as a row keyed to a recipient; the
frontend `<NotificationBell />` polls every 30s.

## Notification template catalog

| Template | Trigger | Recipient role | Channel | Priority |
|---|---|---|---|---|
| `NCA_EVIDENCE_EXPIRED` | AR-09 fires | EvidenceOwner + ControlOwner | in-app + email | High |
| `DSR_SLA_NEAR` | AR-10 fires | PrivacyManager + DPO | in-app + email | High |
| `RISK_APPETITE_BREACHED` | AR-06 fires | RiskCommittee + RiskManager | in-app + email | Critical |
| `CAPA_OVERDUE` | AR-12 fires | CAPA Owner + Owner's manager | in-app + email | High |
| `VULN_SLA_BREACH` | AR-13 fires | CybersecurityManager | in-app + email | Critical |
| `BIA_RTO_GAP` | AR-07 fires | BcmManager + ServiceOwner | in-app | Medium |
| `SUPPLIER_BCP_GAP` | AR-08 fires | SupplierManager | in-app + email | Medium |
| `INCIDENT_CRITICAL` | AR-18 fires | CrisisManager + Exec | SMS + in-app + email | Critical |
| `BREACH_72H_TIMER` | AR-04 fires | DPO + LegalReviewer | in-app + email | Critical |
| `POLICY_ATTESTATION_DUE` | AR-15 fires | every in-scope employee | in-app + email | Low |
| `CROSS_BORDER_REQUEST` | AR-19 fires | DPO | in-app + email | Medium |
| `CLOUD_REGION_CHANGED` | AR-20 fires | DPO + CyberManager | in-app + email | High |
| `BOARD_APPROVAL_REQUIRED` | AR-16 fires | BoardSecretary | email | High |
| `EVIDENCE_REJECTED` | AR-17 fires | EvidenceOwner | in-app | Medium |
| `BOARD_REPORT_READY` | Reporting service finalisation | BoardSecretary + Exec | email | Medium |
| `WORKFLOW_TASK_ASSIGNED` | Any approval step | Step assignee | in-app | Low |
| `WORKFLOW_TASK_OVERDUE` | Step `due_date` passes | Step assignee + manager | in-app + email | High |
| `LOGIN_FROM_NEW_IP` | Auth filter | User | email | Medium |
| `DIGITAL_SIGNATURE_REQUEST` | `signature.recordCreated` | Signer | email | Medium |
| `DIGITAL_SIGNATURE_COMPLETE` | `signature.recordSigned` | Initiator | in-app | Low |

## Per-template fields

```json
{
  "template": "NCA_EVIDENCE_EXPIRED",
  "title_en":   "NCA evidence expiring: {evidenceCode}",
  "title_ar":   "دليل NCA على وشك الانتهاء: {evidenceCode}",
  "message_en": "{evidenceCode} expires on {expiryDate}. Owner: {ownerName}.",
  "message_ar": "{evidenceCode} ينتهي في {expiryDate}. المسؤول: {ownerName}.",
  "channels":   ["IN_APP", "EMAIL"],
  "priority":   "HIGH",
  "cta_url":    "/evidence?id={evidenceId}"
}
```

Templates live in `module/platform/notification_templates` (table)
and are bilingual. Placeholders are replaced at send time from the
event's payload.

## Channels

- **IN_APP** — `platform.notifications` row → frontend bell.
- **EMAIL** — SMTP via Spring's `JavaMailSender`; per-tenant SMTP
  config in `platform.smtp_settings`.
- **SMS** — pluggable provider; today logs to a test sink and
  drops in `application-prod.yml` only when configured.
- **WEBHOOK** — for external integrations (Slack, MS Teams);
  per-tenant webhook URLs.

## Quiet hours

Per-user `iam.users.notification_window_start/end`. Non-critical
notifications outside the window queue until the next opening;
critical (Sev=`CRITICAL`) bypass quiet hours.

## Delivery audit

Every notification send writes to `platform.audit_log` with
`actionType=NOTIFICATION`, `sourceModule=notifications`, and the
template + recipient in the message. Failed deliveries flip
severity to WARN.

---

**See also:** `09_Automation_Rules.md`, `09_Escalation_Rules.md`,
`module/platform/Notification`.

# State Machine Catalogue

## State-machine contract

Each transition is defined by:

`Command + current state + authenticated actor + effective authority + tenant + object version + guards → new state + event + audit record`

Optimistic concurrency is mandatory for decisions. Invalid transitions return RFC 7807 with allowed next actions.

## SM-01 — Risk

```text
DRAFT → IDENTIFIED → ASSESSED → TREATMENT_PLANNED → MONITORED → CLOSED
                         └────→ ACCEPTANCE_PENDING → ACCEPTED ──┘
```

| Command | Guard |
|---|---|
| Submit identification | owner and bilingual material title |
| Complete assessment | methodology/version and required dimensions |
| Submit acceptance | residual risk and appetite comparison |
| Accept risk | correct authority, SoD, rationale and expiry |
| Close | treatments complete or valid acceptance; closure review |
| Reopen | changed exposure, KRI breach or expired acceptance |

## SM-02 — Control test

`PLANNED → IN_PROGRESS → SUBMITTED → REVIEWED_EFFECTIVE / REVIEWED_DEFICIENT → CLOSED`

Return from `SUBMITTED` to `IN_PROGRESS` preserves review comments. Tester cannot independently review own test where configured.

## SM-03 — Policy

`DRAFT → IN_REVIEW → APPROVAL_PENDING → APPROVED → PUBLISHED → SUPERSEDED → ARCHIVED`

Reject returns to draft with rationale. Published content is immutable; edits create a new version. Effective and review dates are independent.

## SM-04 — Audit engagement

`PLANNED → SCOPING → FIELDWORK → REPORTING → FOLLOW_UP → CLOSED`

Planning sign-off, working-paper review and final-report approval are distinct commands. Findings remain separately governed.

## SM-05 — Finding/CAPA

`DRAFT → ISSUED → RESPONSE_PENDING → AGREED / DISPUTED → REMEDIATION → VALIDATION → CLOSED`

The finding owner cannot self-validate remediation. Reopen follows failed validation or recurrence.

## SM-06 — Change

```text
DRAFT
  → SUBMITTED
  → IMPACT_ASSESSMENT
  → AUTHORIZATION_PENDING
      → AUTHORIZED
      → REJECTED
      → MORE_INFO_REQUIRED
      → DEFERRED
  → SCHEDULED
  → IMPLEMENTING
      → IMPLEMENTED
      → ROLLED_BACK
      → FAILED
  → PIR_PENDING
  → CLOSED

EMERGENCY: SUBMITTED → EMERGENCY_AUTHORIZED → IMPLEMENTING
           → PIR_PENDING → RATIFICATION_PENDING → CLOSED
```

No API accepts an arbitrary target status. `AuthorizeNormal`, `AuthorizeEmergency`, `Reject`, `Defer`, `Schedule`, `StartImplementation`, `RecordOutcome`, `CompletePir` and `RatifyEmergency` are separate commands.

## SM-07 — Committee meeting

`DRAFT → AGENDA_OPEN → PACK_ISSUED → IN_SESSION → MINUTES_DRAFT → MINUTES_REVIEW → APPROVED → SIGNED → CLOSED`

Pack issue requires cutoff checks. Session requires computed quorum. Signed minutes cannot be edited.

## SM-08 — Resolution

`DRAFT → OPEN_FOR_VOTE → DECIDED_APPROVED / DECIDED_REJECTED / DEFERRED → SIGNED`

`MORE_INFORMATION_REQUIRED` returns the originating item without losing vote history. A decision can be superseded, never overwritten.

## SM-09 — Vulnerability

`NEW → TRIAGED → ASSIGNED → REMEDIATION → VALIDATION → CLOSED`

Branches: `FALSE_POSITIVE_PENDING → FALSE_POSITIVE`, `EXCEPTION_PENDING → RISK_ACCEPTED`, `REOPENED`.

Exception approval requires risk authority and expiry.

## SM-10 — Security incident

`DETECTED → TRIAGED → CONTAINMENT → ERADICATION → RECOVERY → LESSONS → CLOSED`

Severity changes preserve history and may trigger privacy, BCM or regulator workflows.

## SM-11 — BIA

`DRAFT → OWNER_REVIEW → BCM_REVIEW → APPROVAL_PENDING → APPROVED → EXPIRED / SUPERSEDED`

Approval requires dependency, impact-period and recovery-objective completeness.

## SM-12 — BCM plan

`DRAFT → REVIEW → APPROVED → ACTIVE → INVOKED → RECOVERY → RESTORED → REVIEW_REQUIRED`

Plan invocation is not “status edit”; it is a crisis command with authority, timestamp, trigger and communication side effects.

## SM-13 — DPIA

`SCREENING → NOT_REQUIRED / ASSESSMENT → DPO_REVIEW → RISK_OWNER_DECISION → APPROVED_WITH_CONTROLS / REJECTED → REVIEW_DUE`

High residual risk routes to configured consultation/escalation.

## SM-14 — DSR

`RECEIVED → IDENTITY_PENDING → VALIDATED → FULFILMENT → REVIEW → DELIVERED → CLOSED`

Branches: `REJECTED_WITH_BASIS`, `EXTENDED_WITH_REASON`, `WITHDRAWN`. Clock pauses only where policy and law permit and remains visible.

## SM-15 — Privacy breach

`REPORTED → TRIAGE → INVESTIGATION → NOTIFICATION_DECISION → CONTAINMENT → REMEDIATION → CLOSED`

Notification decisions record regulator/data-subject applicability, deadline, rationale and actual dispatch.

## SM-16 — Third party

`PROSPECT → INHERENT_RISK → DUE_DILIGENCE → APPROVAL_PENDING → ACTIVE → MONITORING → OFFBOARDING → CLOSED`

High-risk acceptance and material outsourcing are separate related decisions.

## SM-17 — Outsourcing arrangement

`DRAFT → MATERIALITY_REVIEW → DUE_DILIGENCE → APPROVAL_OR_NOTIFICATION → CONTRACTED → ACTIVE_REVIEW → EXITING → TERMINATED`

Cloud/residency and exit-plan guards vary by regulatory content version.

## SM-18 — Access request

`DRAFT → MANAGER_REVIEW → APP_OWNER_REVIEW → SOD_REVIEW → APPROVED → FULFILLED → VERIFIED → CLOSED`

Reject/return branches retain rationale. Emergency access uses a separate time-bound state machine.

## SM-19 — Break-glass

`REQUESTED → AUTHORIZED → ACTIVE → EXPIRED / REVOKED → REVIEW_PENDING → REVIEWED`

Requester/beneficiary cannot review their own use. Expiry is enforced even if a scheduler fails by checking effective time on access.

## SM-20 — ESG disclosure

`DATA_COLLECTION → VALIDATION → CALCULATION → MANAGEMENT_REVIEW → ASSURANCE → APPROVED → PUBLISHED → RESTATED`

Restatement creates a new version with reason and impact.

## SM-21 — Regulatory filing

`DRAFT → EVIDENCE_COMPLETE → REVIEW → SIGNATURE_PENDING → READY → SUBMITTED → ACKNOWLEDGED`

`REJECTED_BY_AUTHORITY → CORRECTION → RESUBMITTED`. Retain submitted file hash and channel receipt.

## SM-22 — AI model

`PROPOSED → ASSESSED → VALIDATION → APPROVAL → DEPLOYED → MONITORED → RETRAINING / SUSPENDED → RETIRED`

Material model changes create a new version and validation cycle.

## SM-23 — Package

`AVAILABLE → DEPENDENCY_CHECK → ENABLED → DISABLE_PENDING → READ_ONLY → REMOVED`

Removal requires data export, retention/hold evaluation, downstream dependency resolution and signed tenant-admin confirmation.

## Verification requirements

For every state machine:

- transition table unit tests;
- forbidden-transition tests;
- permission and SoD matrix;
- concurrent decision test;
- idempotency test;
- tenant test;
- event/audit assertion;
- reopen/expiry/scheduler test;
- Arabic error and next-action test.


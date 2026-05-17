# 08 — Approval Matrix

Who can move a record from one workflow state to the next. Source
of truth = `module/approvals` + service-layer `transition()` guards.

## Conventions

- **Recommend** = can suggest the transition (drafts the request).
- **Approve** = can perform the transition.
- **Sign** = digital-signature required before approval commits.
- Multi-step approvals (e.g. policy publish: approver → board)
  cascade through `approvals.approval_steps`.

## Headline workflows

### Policy lifecycle

| Transition | Recommend | Approve | Sign | Audit action |
|---|---|---|---|---|
| DRAFT → REVIEW | Policy Author | Legal Reviewer | — | `TRANSITION` |
| REVIEW → APPROVED | Legal Reviewer | Policy Approver | ✅ when `policy_type=BOARD` | `TRANSITION` + `DIGITAL_SIGNATURE_COMPLETE` |
| APPROVED → PUBLISHED | Compliance Officer | Compliance Officer | — | requires effective_date |
| PUBLISHED → EXPIRED | (automatic via AR-XX expiry sweep) | system | — | |
| PUBLISHED → ARCHIVED | Compliance Officer | Compliance Officer | — | |

### Risk acceptance

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| Open risk → Risk acceptance requested | Risk Owner | — | — |
| Acceptance requested → APPROVED | — | Risk Committee | ✅ |
| APPROVED → EXPIRED | (automatic at expiry) | system | — |

### Evidence approval

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DRAFT → PENDING | Evidence Owner | — | — |
| PENDING → ACTIVE | — | Control Owner / Compliance Officer | ✅ if marked Sig-required |
| ACTIVE → REJECTED | — | Reviewer | — |
| ACTIVE → SUPERSEDED | Evidence Owner | Compliance Officer | — |

### BCP / DRP

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DRAFT → REVIEW | BCM Manager / DR Owner | — | — |
| REVIEW → APPROVED | BCM Manager | BCM Manager + Service Owner | ✅ |
| APPROVED → PUBLISHED | BCM Manager | BCM Manager | — |
| PUBLISHED → ARCHIVED | BCM Manager | BCM Manager | — |

### Cyber control assessment

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DRAFT → UNDER_REVIEW | Control Owner | — | — |
| UNDER_REVIEW → APPROVED | — | NCA Compliance Owner | ✅ when `framework='ECC' OR 'CSCC'` |
| UNDER_REVIEW → REJECTED | — | NCA Compliance Owner | — |
| REJECTED → DRAFT | Control Owner | — | — |

### NCA submission package (NDI / `nca-ecc-export`)

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DRAFT → READY | Compliance Officer | — | — |
| READY → SUBMITTED | — | Compliance Officer | ✅ |
| SUBMITTED → ACKNOWLEDGED | — | system (regulator response) | — |
| SUBMITTED → REJECTED | — | regulator response | — |
| REJECTED → DRAFT (reopen) | Compliance Officer | — | — |

### PDPL breach notification

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DETECTED → CONTAINED | DPO | — | — |
| CONTAINED → INVESTIGATED | DPO | — | — |
| INVESTIGATED → NOTIFIED (within 72h via AR-04) | — | DPO + Legal Reviewer | ✅ |
| NOTIFIED → CLOSED | — | DPO | — |

### Supplier exit plan

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| DRAFT → APPROVED | Supplier Manager | Procurement Owner + Legal Reviewer | ✅ |
| APPROVED → ENACTED | — | Supplier Manager | — |

### CAPA closure

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| EVIDENCE_SUBMITTED → CLOSED | CAPA Owner | Internal Auditor / Compliance Officer | — |

### Board approval (digital signature transactions)

| Transition | Recommend | Approve | Sign |
|---|---|---|---|
| Board pack DRAFT → APPROVED | Board Secretary | Board members (quorum) | ✅ all |
| Board pack APPROVED → ARCHIVED | Board Secretary | Board Secretary | — |

## Cascade vs. parallel

- **Cascade** = sequential approver chain (default). Step N+1
  only fires once step N is approved.
- **Parallel** = all approvers must approve; ordering doesn't
  matter. Used for board approvals (any-quorum) and SoD-required
  reviews (legal + compliance in parallel).

`approvals.approval_steps.parallel_group` controls which mode.

## Escalation

If an approver doesn't act within the `due_date` on
`approvals.approval_steps`, AR-12 (CAPA overdue rule pattern)
generalises into a "step overdue" notification + escalation to
the approver's manager. Configured via the F8 automation rules
table.

## Audit

Every approval transition emits a `TRANSITION` audit row through
F7 with `workflowStatusBefore` / `workflowStatusAfter`, plus a
`DIGITAL_SIGNATURE_COMPLETE` row when applicable.

---

**See also:** `module/approvals`, `module/signature`,
`08_RBAC_Matrix.md`.

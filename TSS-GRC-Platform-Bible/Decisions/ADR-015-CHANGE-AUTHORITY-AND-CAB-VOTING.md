# ADR-015 — Change Authority: a Dedicated CAB-Approver Capability and a Change Manager Role

**Status:** Accepted — closes **D-8**
**Date:** 2026-07-25
**Relates to:** `ADR-010` (permission-only authorisation), finding **F2-24 / DF-4** (CAB approval
provenance), `CM-C2` (CAB quorum + SoD)
**Decision owner:** GRC owner. Recorded as the leader-aligned default on the owner's instruction.

## Context

DF-4 moved the CAB-vote guard from `hasPermission('CHANGE','WRITE')` to
`hasPermission('CHANGE','APPROVE')` — because a requester could previously approve their own change by
submitting somebody else's name, and the guard was held by 41 of 56 roles. The correction was right and
the consequence needs a decision:

| Permission | Roles holding it |
|---|---|
| `CHANGE:READ` | 46 |
| `CHANGE:WRITE` | 41 |
| `CHANGE:EXPORT` | 41 |
| `CHANGE:APPROVE` | **3** — `GRC_ADMINISTRATOR`, `ORG_ADMIN`, `RISK_MANAGER` |
| `CHANGE:DELETE` | 1 — `ORG_ADMIN` |

41 → 3 is a large and correct tightening. It is also, as filed, wrong in a specific way: **the three roles
that can now approve a change are not a Change Advisory Board.** `SERVICE_OWNER` and `CISO` both exist in
the catalogue, both hold `CHANGE:WRITE`, and neither can approve. And the catalogue contains **no change
manager role at all** — the one persona ITIL makes the default change authority does not exist.

So the platform currently routes change approval to a GRC administrator and a risk manager, which is a
governance-function CAB rather than an operational one, and cannot record the security sign-off that
Saudi regulation expects.

The provenance controls DF-4 shipped are unchanged by this decision and are what make the grant safe to
widen: identity comes from the principal, requester and owner are blocked by SoD on the resolved user id,
`V316` enforces one vote per approver per change with a filtered unique index, and the decision is
whitelisted to `APPROVE|REJECT|DEFER|ABSTAIN`.

## Decision

**Keep the guard at `CHANGE:APPROVE` — do not widen it back to `WRITE` — and fix the grant instead.**

1. **Create the missing role `CHANGE_MANAGER`** ("مدير التغيير" / "Change Manager"), the ITIL change
   authority. Bilingual display names per the platform rule. Granted `CHANGE:READ`, `CHANGE:WRITE`,
   `CHANGE:APPROVE`, `CHANGE:EXPORT`.
2. **Grant `CHANGE:APPROVE` to the realistic CAB composition — 9 roles:**

   | Role | Why it is on the CAB |
   |---|---|
   | `CHANGE_MANAGER` *(new)* | ITIL default change authority; chairs the CAB |
   | `SERVICE_OWNER` | The service being changed. ITIL's mandatory participant |
   | `CISO` | Security sign-off — an NCA ECC / SAMA CSF expectation, not a preference |
   | `CYBER_MANAGER` | Operational security representation when the CISO delegates |
   | `COMPLIANCE_OFFICER` | Regulated-entity compliance representation |
   | `EXECUTIVE_REVIEWER` | Executive ratification for high-risk and emergency changes (ECAB) |
   | `GRC_ADMINISTRATOR` | *(existing)* |
   | `ORG_ADMIN` | *(existing)* |
   | `RISK_MANAGER` | *(existing)* risk representation |

   Deliberately **excluded**: `IR_LEAD`, `BCM_MANAGER`, `ASSET_OWNER`, `VULN_MANAGER` — plausible
   attendees, not standing voters; they hold `CHANGE:READ`/`WRITE` and can be added per tenant. Every
   read-only, external and machine persona is excluded, as is `EMERGENCY_RESPONDER`.

3. **The guard string does not change again.** `CHANGE:APPROVE` is the change-authority capability; the
   answer to "this person needs to approve changes" is a grant, never a code edit.

## Why this, with reference to the comparators

The pattern — a dedicated approver capability, distinct from write access, held by a small named set — is
uniform across the ITSM leaders and is codified in the standards.

| Vendor / standard | Mechanism | What it confirms |
|---|---|---|
| **ServiceNow** | `sn_change_write` lets you raise and edit a change; **`sn_change_cab_approver`** is a *separate* role for CAB voting, and `itil` alone is not enough | A dedicated CAB-approver capability distinct from change-write is the market-leading design — this ADR's exact shape |
| **Atlassian JSM** | Approvers configured per change request type; approver ≠ reporter enforced by the platform | Requester-cannot-approve is a product invariant, not a customisation (DF-4 now matches) |
| **ITIL 4 — change enablement** | The *change authority* is assigned per change type and risk; a standard change may be pre-authorised, a normal change goes to the CAB, an emergency change to the ECAB | The set of voters is a per-change-type routing decision — which is why the platform's Standard/Normal/Emergency split (CH-8/Phase 4) and this grant belong together |
| **ISO/IEC 20000-1 §8.5.1** | Changes are assessed and authorised by a defined authority before deployment | "Defined" is the operative word: an undefined or accidental authority fails the clause |
| **COBIT BAI06** | Change authorisation segregated from change construction | The SoD DF-4 enforces, stated as a control objective |
| **NCA ECC-1:2018 (change control within 2-x) / SAMA CSF 3.3** | Security review and approval of changes to information assets | `CISO` in the voting set is a regulatory expectation for the Saudi market |

The reason to fix the *grant* and not the *guard* is the one F2-23 taught at cost: **a guard nobody holds
is indistinguishable from a broken feature.** The committee subsystem is unreachable today for exactly
that reason (`ADMIN:*` held by zero roles, ADR-014). DF-4 tightened correctly and would have produced the
same failure one module over if the grant had not been reviewed alongside it. Guard and grant are one
decision; reviewing them separately is what produces both defects.

## Alternatives considered

- **Revert to `CHANGE:WRITE`.** Rejected. 41 roles including `VIEWER`-adjacent personas is not a CAB, and
  it re-opens the finding. Explicitly ruled out in the plan when D-8 was filed.
- **Introduce a `CAB_MEMBER` role and grant only that.** Rejected as the sole mechanism. It reads cleanly
  but forces every tenant to re-map its existing personas onto a new role before anyone can approve, and
  it hides *why* someone votes. Granting the capability to the roles that already mean "service owner"
  and "CISO" preserves the organisational meaning. A tenant that prefers a single `CAB_MEMBER` role can
  create one — the capability is grantable.
- **Model the CAB as a committee and route every change through `admin.committees`.** Already built
  (Track D) and this is not an either/or: a change routed to a committee uses the committee path with
  quorum, recusal and e-signed minutes, and is blocked from the direct CAB path so the two cannot both
  satisfy `CAB_APPROVED`. This ADR governs the **direct** path, which remains the right mechanism for
  normal changes that do not warrant a convened meeting. Same reasoning ServiceNow applies to CAB
  workbench versus a simple approval.
- **Per-change-type approver sets in data (standard / normal / emergency).** The correct end state and
  deferred, not rejected: it needs the authorisation matrix from CH-17 to become the routing source.
  Recorded as a follow-up: **F2-13c — per-change-type change authority**.

## Trade-offs accepted

- 9 roles is wider than 3. The defensible position is that the *provenance controls*, not the grant width,
  carry the load here: SoD on the resolved user id, one vote per approver enforced in the database, and
  audit lines stamped `user#<id>`. A grant of 9 with those controls is stronger than a grant of 3 without
  them, which is what existed before DF-4.
- `EXECUTIVE_REVIEWER` voting on ordinary normal changes is broader than ideal; it is in the set because
  ECAB ratification has no other home until F2-13c lands.
- Creating `CHANGE_MANAGER` adds a 57th role. The role catalogue grows for a real persona rather than a
  synthetic one, which is the right reason.

## Consequences

- Closes **D-8** and unblocks **F2-13b**.
- **Deployment note, unchanged in kind but much reduced in blast radius:** a user who was casting CAB votes
  via `CHANGE:WRITE` and holds none of the 9 roles now receives **403**. Before this ADR that was 38 roles
  losing the capability; after it, the realistic CAB composition retains it. Tenants should map their CAB
  members onto `CHANGE_MANAGER` or one of the 9 at upgrade.
- `CHANGE_MANAGER` must be seeded for every tenant, not just org 1 — the migration seeds it as a system
  role with `organisation_id` NULL, matching `ORG_ADMIN` and `RISK_MANAGER`.
- **Reversal cost:** one `DELETE` from `iam.role_permissions`. The role itself is additive and harmless.

## Proof obligations

1. Live probe: a `SERVICE_OWNER` who is not the requester can now record a CAB vote (**200**).
2. Live probe: the requester still cannot, whoever they claim to be (**409**, SoD) — the DF-4 invariant
   must survive the widened grant.
3. Live probe: the same approver voting twice is refused (**409**), and the `V316` unique index refuses it
   at the database level too.
4. Live probe: a role outside the 9 receives **403**, confirming the guard still binds.
5. The roles-per-permission report shows `CHANGE:APPROVE` at 9 of 57 roles, with no read-only, external or
   machine persona in the set.

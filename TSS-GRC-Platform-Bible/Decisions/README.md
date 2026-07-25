# Architecture Decision Records

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-MODULAR-MONOLITH.md) | Enforced modular monolith before service extraction | Accepted |
| [ADR-002](ADR-002-PLATFORM-KERNEL-AND-PACKAGES.md) | Platform kernel plus installable commercial packages | Accepted |
| [ADR-003](ADR-003-DATA-OWNERSHIP-AND-EVIDENCE-GRAPH.md) | One object owner and evidence graph projections | Accepted |
| [ADR-004](ADR-004-OUTBOX-AND-DOMAIN-EVENTS.md) | Transactional outbox for reliable cross-module events | Accepted |
| [ADR-005](ADR-005-TENANT-ISOLATION.md) | Layered tenant isolation | Accepted |
| [ADR-006](ADR-006-STATE-MACHINES-AND-APPROVALS.md) | Explicit command-driven state machines | Accepted |
| [ADR-007](ADR-007-ARABIC-FIRST-UX.md) | Arabic-first experience and content model | Accepted |
| [ADR-008](ADR-008-HUMAN-ACCOUNTABLE-AI.md) | Human-accountable AI with retained provenance | Accepted |

Existing repository ADRs remain historical evidence. This directory governs the transformation target.


## Independent review pass — 2026-07-25

| ADR | Title | Status | Makes enforceable |
|---|---|---|---|
| [ADR-009](ADR-009-MODULE-REGISTRY.md) | Module Registry as a Build Artefact | Proposed | — (new) |
| [ADR-010](ADR-010-PERMISSION-ONLY-AUTHORISATION.md) | Permission-Only Authorisation in Module Code | Proposed | ADR-006 |
| [ADR-011](ADR-011-SCHEMA-OWNERSHIP.md) | Schema Ownership Equals Module Ownership | Proposed | ADR-003 |
| [ADR-012](ADR-012-BOUNDARY-ENFORCEMENT-AND-PORTS.md) | Boundary Enforcement and Consumer-Owned Ports | Proposed | ADR-001 |


## Open decisions closed — 2026-07-25

Four decisions the plan had blocked on, answered with the leader-aligned best-practice default on the
owner's instruction. Each states its comparator evidence, its alternatives, the trade-offs accepted and
its **reversal cost** — none of them is expensive to unwind, which is the argument for taking them now
rather than leaving a third of the plan blocked.

| ADR | Title | Closes | Status |
|---|---|---|---|
| [ADR-013](ADR-013-PER-MODULE-SKU-AND-ENTITLEMENT.md) | Per-Module SKUs on One Deployment, Enforced by Entitlement | **D-3**, and dissolves **D-4** + **D-6** | Accepted (pricing out of scope) |
| [ADR-014](ADR-014-COMMITTEE-VOTING-RIGHTS.md) | Committee Voting Rights: Roster Confers the Vote, Grants Open the Door | **D-7** | Accepted — **implemented**, V317 |
| [ADR-015](ADR-015-CHANGE-AUTHORITY-AND-CAB-VOTING.md) | Change Authority: a Dedicated CAB-Approver Capability and a Change Manager Role | **D-8** | Accepted — **implemented**, V317 |
| [ADR-016](ADR-016-DOCUMENTATION-GOVERNANCE.md) | Documentation Governance: Status Headers, Supersession, and a CI-Enforced Register | the provisional doc-governance arrangement; makes **D-5** mechanical | Accepted |

Only **D-1** and **D-2** remain open, and deliberately: which committee constitutes each tenant's CAB and
ECAB, and the ECAB ratification SLA, are per-customer charter facts. Defaulting them would be fabricating
governance data — the defect `F2-02` exists to record.

**ADR-014 reverses a decision taken inside its own first draft.** `INTERNAL_AUDITOR` was in the
committee-vote grant set until the applied grants were checked against real users; IIA Standard 1110 has
the chief audit executive reporting *to* a committee, not voting in it. The reversal is recorded in the
ADR rather than quietly corrected, because the argument that justified the original grant ("the roster
check gates it anyway") would equally have justified granting the vote to everyone.

**ADR-001 through ADR-008 are retained unchanged.** Validation of each — including the finding that
`ADR-006` is contradicted *in implementation* by F2-21, and that `ADR-001`/`ADR-002`'s factual claims
reproduce exactly — is recorded in [`Governance/BIBLE_VALIDATION_LOG.md`](../Governance/BIBLE_VALIDATION_LOG.md).

# Evidence and Maintenance Standard

## 1. Evidence hierarchy

From strongest to weakest:

1. production-like live probe with two tenants and distinct principals;
2. automated security, state-machine or contract test;
3. executable source and database constraint;
4. migration or configuration;
5. user-interface implementation;
6. design or operating document;
7. assertion without artefact.

Lower evidence cannot override contradictory higher evidence. A user story marked complete is reopened if code or a live probe disproves it.

## 2. Definition of enterprise-ready

A capability is enterprise-ready only when all applicable gates pass:

- **Value:** a named persona can complete an end-to-end business outcome.
- **Integrity:** actor, tenant, time, authority and evidence are server-derived or independently verifiable.
- **Workflow:** states and transitions are explicit, guarded and tested.
- **Data:** ownership, retention, lineage, classification and deletion are defined.
- **UX:** Arabic and English tasks are usable, accessible and tested with target personas.
- **Security:** deny-by-default authorization, abuse cases and tenant isolation are proven.
- **Operations:** health, logs, backup, restore, upgrade and rollback are rehearsed.
- **Commercial:** entitlement, installation, upgrade, removal and dependency behaviour are proven.
- **Regulatory:** the product provides evidence-supporting capabilities; legal compliance is never claimed automatically.

## 3. Finding lifecycle

`OPEN → ACCEPTED → PLANNED → IN_PROGRESS → VERIFIED → CLOSED`

`RISK_ACCEPTED` requires an owner, expiry date, compensating control and approving authority. `WONT_FIX` requires an ADR.

## 4. Required finding schema

Every finding contains:

- ID, title, category and severity;
- current state, problem and root cause;
- business, technical, UX and sales impacts;
- recommendation, alternatives and trade-offs;
- effort, priority and expected ROI;
- references, owner, target release and verification evidence.

## 5. Severity

| Severity | Meaning |
|---|---|
| Critical | Enables cross-tenant access, fraudulent approval, material evidence falsification, systemic compromise or unsafe market claim |
| High | Breaks a core regulated workflow, least privilege, commercial isolation, auditability or a flagship journey |
| Medium | Significant operability, maintainability, accessibility or buyer-confidence weakness |
| Low | Localised quality issue with limited control or customer impact |

## 6. Decision discipline

- An ADR records one consequential decision.
- Decision status is `Proposed`, `Accepted`, `Superseded` or `Rejected`.
- ADRs explain context, decision, alternatives, consequences, reversibility and proof.
- A future-state diagram without an accepted ADR is a proposal, not architecture.

## 7. Release evidence pack

Each release must preserve:

- commit and artifact identifiers;
- database migration range;
- dependency and entitlement manifests;
- backend, frontend, security and accessibility results;
- live tenant and SoD probes;
- screenshots or recordings for critical journeys;
- regulatory-content versions;
- deployment and rollback evidence;
- closed findings with reproduction steps.


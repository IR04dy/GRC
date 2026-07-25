# ADR-014 — Committee Voting Rights: Roster Confers the Vote, Grants Open the Door

**Status:** Accepted — closes **D-7**
**Date:** 2026-07-25
**Relates to:** `ADR-006` (approvals use authenticated user IDs), `ADR-010` (permission-only
authorisation), finding **F2-21 / DF-3** (committee vote provenance)
**Decision owner:** GRC owner. Recorded as the leader-aligned default on the owner's instruction.

## Context

D-7 was filed as "which roles may cast a committee vote? Today the answer is *only* `PLATFORM_ADMIN`."
Measurement confirms it and makes it worse than filed.

Every endpoint in the committee subsystem — **7 controllers, 59 mapped methods** (32 `READ`, 27 `WRITE`) —
is guarded by `hasPermission('ADMIN','READ')` or `hasPermission('ADMIN','WRITE')`. Those two permissions
are used **nowhere else in the platform**: the `ADMIN` permission module gates the committee subsystem and
nothing besides — not tenant configuration, not user management. That bounds this decision's blast radius
exactly, and it is also the strongest argument for F2-23b below. Queried against the live catalogue:

| Permission | Roles holding it |
|---|---|
| `ADMIN:READ` | **0** |
| `ADMIN:WRITE` | **0** |
| `ADMIN:EXPORT` | **0** |
| `ADMIN:APPROVE` | **does not exist in the catalogue** |

So of 56 roles, **none can read a committee**, let alone vote in one. `BOARD_SECRETARY` exists as a
seeded system role and cannot open the board-governance module. Only `PLATFORM_ADMIN` works, and only
because `ModulePermissionEvaluator` bypasses in code. The entire committee and board-pack subsystem —
built out across CH-14 and Track D, with quorum, recusal, e-signed minutes and gate→committee routing —
is reachable by exactly one persona, and that persona is the vendor's administrator, not the customer's
board secretary.

Two further defects surfaced while grounding this decision, both mine:

- **DF-3's on-behalf check tests for a permission that cannot exist.** `mayRecordOnBehalf` matches the
  granted authority `ADMIN_APPROVE`, and `ADMIN:APPROVE` is not in the catalogue, so no tenant can ever
  grant it. The secretary-records-a-show-of-hands path I shipped is therefore `PLATFORM_ADMIN`-only in
  practice. The Javadoc asserts a distinction the data does not support.
- **One grant both appoints members and casts votes.** `ADMIN:WRITE` gates
  `CommitteeMemberRegistryController` (appoint a member term) *and* `CommitteeResolutionController`
  (`POST /{id}/votes`). Anyone who can vote can appoint themselves — or a confederate — to any
  committee's roster. That is a privilege-escalation path straight into quorum, and it undercuts the
  recusal and quorum work it sits next to.

DF-3 did establish the load-bearing control: the voter is resolved from the principal and matched against
`committee_member_terms` for that committee, non-roster actors are rejected, and the stored member name is
the canonical roster spelling. **That check, not the RBAC grant, is what makes a vote legitimate.**

## Decision

**Committee membership confers the vote. The permission grant only opens the door.** Concretely, decompose
the one-size `ADMIN:WRITE` into three distinct capabilities and grant each to the population that needs it:

| Capability | Meaning | Granted to |
|---|---|---|
| `ADMIN:READ` | See committees, meetings, agendas, resolutions, minutes, action items | **21 roles** — every executive, assurance and oversight persona, including read-only `AUDITOR`, `INTERNAL_AUDITOR`, `EXTERNAL_AUDITOR`, `REGULATOR_VIEWER` |
| `ADMIN:WRITE` | **Secretariat and administration** — constitute a committee, appoint or end a member term, edit the gate→committee map, manage agenda, papers, minutes, action items; **and record a vote on another member's behalf** | **3 roles** — `BOARD_SECRETARY`, `GRC_ADMINISTRATOR`, `ORG_ADMIN` |
| `ADMIN:APPROVE` *(new)* | **Cast or record a committee vote**, approve minutes | **15 roles** — the committee-eligible executive population (below) |
| `ADMIN:EXPORT` | Export committee packs, minutes, resolution registers | **7 roles** — the secretariat plus assurance (`INTERNAL_AUDITOR`, `EXECUTIVE_REVIEWER`, `COMPLIANCE_OFFICER`, `EXTERNAL_AUDITOR`) |

`ADMIN:APPROVE` grant set (15): `BOARD_SECRETARY`, `GRC_ADMINISTRATOR`, `ORG_ADMIN`, `CISO`, `CFO`,
`COMPLIANCE_OFFICER`, `DPO`, `RISK_MANAGER`, `BCM_MANAGER`, `CYBER_MANAGER`, `PRIVACY_MANAGER`,
`LEGAL_COUNSEL`, `EXECUTIVE_REVIEWER`, `SUSTAINABILITY_OFFICER`, `SUPPLIER_MANAGER`.

Deliberately **excluded** from `ADMIN:APPROVE`: `VIEWER`, `AUDITOR`, `EXTERNAL_AUDITOR`,
`REGULATOR_VIEWER` (read-only and external personas must never be able to vote), every `*_INGEST`
service account, and `EMERGENCY_RESPONDER` (break-glass must not silently acquire governance votes).

**`INTERNAL_AUDITOR` is also excluded, and this reverses a first draft of this ADR.** It was in the set
until the applied grants were checked against the dev users, which showed the `auditor` login acquiring a
committee vote through `INTERNAL_AUDITOR`. **IIA Standard 1110** (organisational independence) and the
standard Saudi audit-committee charter both have it the other way round: the chief audit executive
*reports to* a governance committee and attends it, and is not a voting member. `READ` + `EXPORT` give
full visibility with no vote, which is precisely what independence requires. A tenant whose charter does
seat its CAE can grant `APPROVE` itself. Recording the reversal rather than quietly shipping 15: the
grant was defensible only by the argument "the roster check gates it anyway", and that argument would
equally justify granting the vote to everyone — which this ADR rejects.

Three code consequences:

1. `POST /committees/resolutions/{id}/votes` moves from `ADMIN:WRITE` to **`ADMIN:APPROVE`**.
2. `mayRecordOnBehalf` tests for **`ADMIN_WRITE`** — the secretariat right — instead of the ungrantable
   `ADMIN_APPROVE`. A secretary minuting a show of hands holds both and the row is still stamped
   `on_behalf = 1` with the real actor; an ordinary member holds only `APPROVE` and can vote only as
   themselves.
3. Appointing member terms stays on `ADMIN:WRITE`, now held by 3 roles instead of by everyone who can
   vote. The escalation path closes.

**The roster check is unchanged and remains the real gate.** Holding `ADMIN:APPROVE` while not sitting on
a committee yields `400 — not a member of this committee`, exactly as before.

## Why this, with reference to the comparators

The two-layer model — coarse capability plus record-level membership — is what every serious governance
product does, because a job title is not a seat.

| Vendor / standard | Mechanism | What it confirms |
|---|---|---|
| **Diligent Boards / Nasdaq Boardvantage** (the actual board-portal leaders) | Voting is scoped to the *meeting's* member roster; the corporate secretary records votes with explicit attribution | Roster, not role, confers the vote — and delegated recording is a named, audited capability |
| **ServiceNow** | An approval is a *record* assigned to named approvers or groups; `approval_admin` (configure approval rules) is a different role from being an approver | Administering the approval process and participating in it are separate grants — exactly the split this ADR makes |
| **Salesforce** | "Manage approval processes" is a distinct permission from being an assigned approver; delegated approvers are configured per user and recorded | Same separation, independently arrived at |
| **Microsoft Purview / Entra** | Role *groups* for administration; reviewers assigned per case; access-review decisions attributable to the individual reviewer | Administration ≠ participation, and participation is per-record |
| **Saudi Companies Law + NCA ECC-1 2-1 / SAMA CSF 3.1** | A committee acts under a documented charter with named members and a quorum | The legal instrument is the charter and its roster. Minutes are evidentiary only if the vote is attributable to a charter member |

The decisive reason is evidentiary, not stylistic: **if one grant both appoints members and casts votes,
a single actor can manufacture a quorum, and every minute the system produces becomes worthless as
evidence.** That is the same defect class as F2-21/DF-3 — trusting the caller for identity — one layer up,
in the grant model instead of the request body. Separating the two is the only version of this that
survives an audit.

Granting `ADMIN:READ` to 21 roles including the regulator viewer is also deliberate: in Saudi practice a
regulator or external auditor asks to see the committee's decision trail, and a subsystem no customer
persona can open cannot answer that.

## Alternatives considered

- **Grant `ADMIN:WRITE` broadly and rely on the roster check alone.** Rejected — this is the tempting
  one-line fix. It works for voting and leaves the escalation path wide open: the same grant appoints
  members. The roster check cannot defend a roster the actor can edit.
- **Make committee membership itself the only gate; drop the permission check on voting.** Rejected. It
  inverts the platform's authorisation model (`ADR-010`), makes the endpoint unguarded for the ratchet,
  and leaves a tenant no way to say "this executive may sit on committees but not vote yet".
- **A dedicated `COMMITTEE` permission module instead of reusing `ADMIN`.** Genuinely attractive and
  deferred, not rejected: the committee subsystem is a Base Platform capability with 8 controllers and
  arguably deserves its own module code, which would also stop it sharing grants with unrelated admin
  screens. Deferred because it is a catalogue migration touching 55 mapped methods, and because D-7 is
  blocking F2-23 now. Recorded as a follow-up: **F2-23b — split `COMMITTEE` out of `ADMIN`**.
- **Grant `ADMIN:APPROVE` to all 41 roles that hold `CHANGE:WRITE`-style broad grants.** Rejected. The
  roles-per-permission report exists precisely to prevent a business-approve permission held by most of
  the catalogue; `WORKFLOW_WRITE` at 55 of 57 roles is the cautionary example.

## Trade-offs accepted

- 16 roles for `ADMIN:APPROVE` is a judgement call about which executives sit on governance committees in
  a Saudi enterprise. It is deliberately generous, because the roster check is the real restriction and an
  over-tight grant produces a 403 the customer cannot self-service. A tenant that wants it tighter removes
  grants; that is a data change, no deploy.
- `SUPPLIER_MANAGER` and `SUSTAINABILITY_OFFICER` are the two least certain inclusions — both sit on
  procurement and ESG committees respectively in the platform's own module design, so they are in.
- Reusing `ADMIN` as the permission module keeps committee grants coupled to other admin screens until
  F2-23b. Stated rather than hidden.

## Consequences

- Closes **D-7** and unblocks **F2-23**.
- The committee subsystem becomes usable by customer personas for the first time. This is a functional
  unlock, not a hardening: today it is inaccessible, and 22 of the 56 roles gain capability.
- `ADMIN:APPROVE` enters the permission catalogue, so the catalogue count rises. Not a ratchet regression:
  the ratchet counts *unguarded* write endpoints and no guard is removed.
- **Deployment note:** no user loses access, because nobody except `PLATFORM_ADMIN` had any. The only
  behavioural narrowing is that a `PLATFORM_ADMIN` remains the sole role able to do everything, and
  vote-recording now demands `ADMIN:APPROVE` — which `PLATFORM_ADMIN` bypasses anyway.
- **Reversal cost:** one `DELETE` from `iam.role_permissions` plus reverting two guard strings.

## Proof obligations

1. Live probe: `so_officer` (CISO) can cast a vote on a committee it sits on, and receives **400** for a
   committee it does not sit on.
2. Live probe: a role holding only `ADMIN:APPROVE` receives **403** on `POST /committees` and on
   `POST /committees/{id}/members` — the escalation path is closed.
3. Live probe: a `BOARD_SECRETARY` records a vote for another member and the row carries
   `on_behalf = 1`, `recorded_by_user_id = <secretary>`, `member_name = <canonical roster spelling>`.
4. Mutation proof: reverting the `recordVote` guard to `ADMIN:WRITE` must fail a named test.
5. The roles-per-permission report shows no read-only, external or machine persona holding
   `ADMIN:APPROVE`.

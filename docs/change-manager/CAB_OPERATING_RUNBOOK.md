# CAB Operating Runbook — Change Advisory Board / Emergency CAB
### دليل تشغيل لجنة استشارات التغيير (CAB) واللجنة الطارئة (ECAB)

> **Audience** — the change manager, the CAB secretary, and whoever brings a tenant live.
> **Scope** — how to constitute the board, map its authority, run the meeting cycle, and read
> the refusals. CE-16 of `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`.
> **Module reference** — `PROJECT_CHRONICLE.md` §3.10 · contract:
> `module/change_mgmt/package-info.java`.

---

## §0 Read this first — two things fail closed

The platform **refuses** to authorise a change through a `CAB` or `ECAB` gate until a real
committee has been named for that gate *and* that committee has voting members. That is
deliberate (ADR-017 D-2): there is no default CAB, because inventing an authority is worse than
refusing to act on one. It also means a tenant can look completely configured and still refuse
every CAB approval.

**Measured on this platform, 2026-08-04, in both the development and the deployment databases:**

| Gate | Mapped committee | Voting members with a current term |
|---|---|---|
| `CAB` | 13 — *IT Steering Committee* | **0** |
| `ECAB` | 12 — *Executive Committee* | **0** |

Committees 1–6 carry **31** member terms between them (plus six more on dev's two extra committees).
Committees **12 and 13 hold no member terms at all** — so both change-authority gates currently
point at unconstituted bodies.
This is finding **F2-96**, and only its *detection* half is fixed: the platform now says so loudly
instead of reporting healthy. **Appointing the members is a tenant act, not a migration** — nobody
but the customer can decide who sits on their CAB.

So before go-live, or after any tenant onboarding:

1. **Constitute the committee** (§1) — seats, with at least as many voting members as the
   authorisation matrix demands approvals.
2. **Map the gate** (§2) — `CAB` and `ECAB` each to a committee id.

Verify with **`GET /api/v1/changes/governance-health`**. It answers both questions in one call, tells
you *which* of the two is missing (the remedy text differs for "no committee mapped" and "committee
mapped but empty"), and is the same predicate the transition guard uses — so the health report and
the refusal cannot drift apart. The rows themselves are `admin.committee_gate_map` (one per gate per
organisation) and `admin.committee_member_terms` (`is_voting_member`, `term_ends_at`).

---

## §1 Constitute the board

The CAB is **not a separate engine**. It is a profile over the generic committee engine
(`module/admin/committee`, ADR-013) — the same machinery that runs the audit committee and the
Sharia board. A dedicated CAB table was measured as 12-of-15 subsumed by the generic one and is
pinned against being built.

| Step | Where | Notes |
|---|---|---|
| Create the committee | `/committees` · `ADMIN:WRITE` | `committee_type` `CAB` or a body you designate (`IT_STEERING`, `EXEC_COMMITTEE` … — the *type* is not what makes it the CAB; the gate map is) |
| Charter / ToR | committee record | `charter_en` + `charter_ar`. Bilingual is backend-mandatory for user-visible records |
| Quorum | `quorum_pct` | Percentage of voting members. `quorum_requires_chair` is **opt-in** — turning it on globally would have refused 17 of 18 quorate meetings in dev (S6c) |
| Appoint members | committee roster · `ADMIN:WRITE` | Set `is_voting_member` on everyone who may vote. Link each seat to a platform user where one exists — SoD is resolved on **user id**, and name-only matching is the legacy fallback a claimed name could once defeat |
| Chair + secretary | committee record | The chair presides; the secretary circulates the pack and files the minutes |

> ⚠ **Roster ≠ authority.** A seat with `is_voting_member = 0`, or whose `term_ends_at` has passed,
> does not count toward quorum or toward the minimum approvals. That is why the table in §0 counts
> *voting members with a current term* rather than rows.

---

## §2 Map the gates to committees

```
GET    /api/v1/changes/authority             → both gates, with configured / fault state
GET    /api/v1/changes/authority/eligible    → the committees that may serve
PUT    /api/v1/changes/authority/CAB         { "committeeId": 13 }
PUT    /api/v1/changes/authority/ECAB        { "committeeId": 12 }
DELETE /api/v1/changes/authority/CAB         → unmap (the gate then fails closed again)
```

Reads need `ADMIN:READ`; writes need **`ADMIN:WRITE`** — the same permission that charters a
committee. Naming which body holds an authority is a charter act, so a `CHANGE:APPROVE` holder
decides changes, not *who* decides them.

> ⚠ **SKU note.** Constituting a committee is licensed as `BOARD`; change governance is `CHANGE`. A
> tenant holding `CHANGE` alone can reach this screen with nothing to select. All three dev tenants
> hold both, so it is latent (ADR-017 D-2).

---

## §3 Which changes need which gate

`change_mgmt.change_authorisation_matrix` maps **change type × risk level** to a gate and a minimum
approval count — **12 rows per tenant** (`AUTO` ×1, `CAB` ×5, `ECAB` ×6), 36 rows across the three
seeded tenants. Read it per organisation: a platform-wide `GROUP BY gate` returns 3/15/18 and
describes nobody's configuration.

| Gate | Meaning |
|---|---|
| `AUTO` | Pre-authorised — no committee sitting. Low-risk `STANDARD` work |
| `CAB` | The change advisory board authorises **before** implementation |
| `ECAB` | Emergency: implement first, ratify after, on a clock (§6) |

`GET /api/v1/changes/{id}/authorisation` resolves one change's cell and tells you the gate, the
required approvals, and whether the authority behind it is configured.

> ⚠ **An unconstituted CAB does not block everything.** A change whose cell is `AUTO` — here, only
> `STANDARD`/`LOW` — walks `CAB_PENDING → CAB_APPROVED → … → CLOSED` with no committee involved at
> all, correctly. So "the CAB is not constituted" and "no change can be approved" are different
> statements, and `/{id}/authorisation` is how you tell which applies to the change in front of you.
> This is the distinction the CE-16 probe itself got wrong first time.

> ⚠ The matrix's own `gate` column is **advisory** on the change record until a gate map exists:
> 8 changes in each database were authorised carrying a `CAB`/`ECAB` gate with no committee behind
> it (F2-101). Reading a gate value is not proof a committee saw the change.

---

## §4 The meeting cycle

| Phase | What happens | Where |
|---|---|---|
| **Schedule** | Create the meeting on the committee's calendar | `/committees` → meetings |
| **Agenda** | Built from the CAB queue — changes awaiting authorisation, plus emergency changes awaiting ratification. Rebuilding is idempotent | agenda build (S7b) |
| **Papers** | The per-change pack: details, plans, votes, tasks, links, PIR | `GET /api/v1/reporting/change/{id}/cab-pack.pdf` |
| **Circulate** | Agenda pack PDF, bilingual | `GET /api/v1/reporting/committee/meeting/{id}/agenda.pdf` |
| **Sit** | Record attendance; the engine computes quorum against the voting roster | meeting screen |
| **Decide** | Raise a motion, vote, carry it. A motion is raised **undecided** — deciding one is a separate, recorded act (S7a) | meeting screen |
| **Write back** | The carried resolution becomes the change's decision — author and timestamp stamped on the change | automatic |
| **Minutes** | Attendance, quorum, tally, resolutions — e-signed | `GET /api/v1/reporting/committee/meeting/{id}/minutes.pdf` |

**Reading the minutes honestly.** The export states the **provenance** of every figure it prints,
per meeting and per resolution. `quorum_source = ASSERTED_LEGACY` means a human typed the quorum
conclusion and nothing computed it — true of 32 of 34 meetings in dev. A minutes document that
printed those numbers plainly would be inventing them, and an all-zero vote tally would report a
unanimous abstention that never happened. If you need computed quorum, record attendance.

---

## §5 Decisions, and what each one does to the change

| Outcome | Change goes to | Then |
|---|---|---|
| Approve | `CAB_APPROVED` | → `SCHEDULED` → `IMPLEMENTING` |
| Approve with conditions | `CAB_APPROVED` + condition rows | `→ IMPLEMENTING` **refuses** until every condition is closed (§5.1) |
| Reject | `CAB_REJECTED` | → `DRAFT` for rework |
| Defer | `CAB_DEFERRED` | → `CAB_PENDING` to re-table |
| More information | `MORE_INFO_REQUIRED` | → `CAB_PENDING` to re-table |

There is **no `CONDITIONAL` status**: conditionality is derived from carrying open conditions. A
stored literal can disagree with the rows it summarises, so the migration's self-check refuses to
let one into `ck_change_status`.

### §5.1 Conditions — who may do what

| Act | Permission | Extra rule |
|---|---|---|
| **Impose** a condition | `CHANGE:APPROVE` | Adding an obligation as the price of approval is an approval act |
| **Record satisfied** | `CHANGE:WRITE` | Must cite evidence the evidence subsystem has **APPROVED** (§5.2). Doing the work is operational |
| **Waive** | `CHANGE:APPROVE` | Needs a stated reason, and **may not be the requester or owner** — a condition the requester can forgive is not a condition |

In this deployment **9 roles hold `CHANGE:APPROVE`** — `CHANGE_MANAGER`, `CISO`,
`COMPLIANCE_OFFICER`, `CYBER_MANAGER`, `EXECUTIVE_REVIEWER`, `GRC_ADMINISTRATOR`, `ORG_ADMIN`,
`RISK_MANAGER`, `SERVICE_OWNER` — and only `ORG_ADMIN` holds `CHANGE:DELETE`. Read the live grants
from `iam.role_permissions`; do not infer them from a username.

### §5.2 What counts as evidence

`POST /api/v1/changes/{id}/evidence` and `POST /api/v1/changes/conditions/{id}/evidence` link an
item from the platform's evidence subsystem — SHA-256 hashed, versioned, independently reviewed.

**Only `APPROVED` counts.** `PENDING_REVIEW` would let something merely uploaded discharge a CAB
obligation; `EXPIRED` would let evidence that has aged out do it, and `EXPIRED` is 29 of the 78
live evidence items. If the evidence module is unavailable the satisfaction gate **refuses** rather
than passing — a guard that waves work through because it cannot see the proof is not a guard. The
escape hatch is the auditable one: an independent `CHANGE:APPROVE` holder waives, with a reason.

> ⚠ `change_links` offers an `EVIDENCE` entity type. It is a plain pointer — no hash, no review, no
> expiry. It is the deficiency ISO 20000 documented-information asks about, not the answer to it.

---

## §6 Emergency changes (ECAB)

An emergency change takes `DRAFT → EMERGENCY_APPROVED → IMPLEMENTING`, skipping the *pre*-approval
sitting — **not** the authorisation.

1. On implementation the ratification clock starts. The constraint
   `ck_change_ratification_clock` makes a pending ratification without a due date
   unrepresentable, so "no due date" cannot quietly mean "never breaches".
2. `GET /api/v1/changes/ratification-register` lists what is outstanding, due and breached.
3. The sweep runs **daily at 01:55** (`grc.change.ratification-sweep-cron`). On breach it notifies
   the `CISO` role, writes a durable audit-ledger event, and stamps the change. Dedup is keyed on
   the **due date**, so extending an SLA legitimately re-arms the escalation while a restart or a
   second run the same day does not re-notify.
4. `POST /api/v1/changes/{id}/ratify` — `CHANGE:APPROVE`, and **not** the approver
   (self-ratification would certify one's own decision) and **not** the raiser.
5. The SLA is per tenant (`GET`/`PUT /api/v1/changes/ratification-policy`, `CHANGE:APPROVE` to
   change it) and **bounded** — a tenant cannot answer "what stops you choosing 365 days?" by
   choosing 365 days.

> ⚠ Known residual: a raiser who holds `CHANGE:APPROVE` can take the `DRAFT → EMERGENCY_APPROVED`
> hop on their own change. Mandatory ratification by someone else is the control that catches it.

---

## §7 Schedule, freeze windows, conflicts

- `GET /api/v1/changes/calendar?from=&to=` — the change calendar.
- Freeze/blackout windows: `GET`/`POST /api/v1/changes/freeze-windows`
  (`CHANGE:READ`/`CHANGE:WRITE`; `DELETE` needs `CHANGE:DELETE`). Scope `ALL` or a single
  `CATEGORY`; `allow_emergency` decides whether an `EMERGENCY` change may pass.
- `→ SCHEDULED` **refuses** when the window overlaps an in-scope active freeze.
- `GET /api/v1/changes/{id}/conflicts` **reports** colliding changes. It does not refuse them —
  overlapping work is often legitimate, so the CAB decides, not the platform.

A *published* forward schedule of change (FSC) and a declared service outage (PSO) are **not built**
— that is CE-8. What exists is the calendar, the windows and the collision report.

---

## §8 What to hand an auditor

| Artefact | Endpoint | Guard |
|---|---|---|
| Change register | `/api/v1/reporting/change-register.{pdf,xlsx,docx}` | `CHANGE:EXPORT` **and** `CHANGE:READ` |
| CAB pack for one change | `/api/v1/reporting/change/{id}/cab-pack.pdf` | `CHANGE:EXPORT` **and** `CHANGE:READ` |
| **CAB decision register** | `/api/v1/reporting/change-decision-register.{pdf,xlsx}` | `CHANGE:EXPORT` **and** `CHANGE:READ` |
| Meeting agenda pack | `/api/v1/reporting/committee/meeting/{id}/agenda.pdf` | `ADMIN:EXPORT` **and** `ADMIN:READ` |
| Meeting minutes | `/api/v1/reporting/committee/meeting/{id}/minutes.pdf` | `ADMIN:EXPORT` **and** `ADMIN:READ` |

Add `?lang=ar` for Arabic — the generators shape and mirror RTL text properly. The committee
exports are guarded `ADMIN` because the engine is generic: minutes for the audit committee are not
a change-management artefact.

Every export is written to the audit ledger.

### §8.1 Reading the decision register

One row per change **past a decision point** — which includes deferrals and requests for more
information, because those are decisions the committee took. Each row answers two separate
questions, and you need both:

| Column | Values | What it means |
|---|---|---|
| **Basis** | `RECORDED` | The change carries a decision timestamp or author — somebody recorded the act |
| | `INFERRED_FROM_STATE` | The state proves an approval happened (a change cannot reach `IMPLEMENTING` otherwise) but **nothing recorded when or by whom** |
| **Evidence** | `RESOLUTION` | A carried committee resolution — the system of record. The register prints its code, outcome, tally **and the tally's source** |
| | `RECORDED_VOTES` | A legacy per-change tally with named approvers |
| | `NONE` | Nothing backs it — **correct** for an `AUTO`-gated change, since no committee was asked |

⚠ **`INFERRED_FROM_STATE` is not an error to fix in the register.** It is the historical record
being honest: those changes were approved before the platform stamped the author (F2-93/F2-103).
Decisions taken from now on are `RECORDED`. Where the register says *"not recorded"* it means
exactly that — never treat a blank or a dash as "no objection".

The header states how many rows are attributed. A register that said *"12 decisions"* without it
would invite you to read twelve recorded acts; measured 2026-08-04, one of eleven named its author.

Recusals from the committee vote are listed under the table in the PDF and in their own column in
the XLSX — a member who stood aside is a governance fact an auditor asks about by name.

---

## §9 KPIs — and how to read them

`GET /api/v1/changes/analytics` returns `kpis`: **19** ITIL/ISO metrics. Each one carries its
numerator, denominator and population, plus two independent axes:

- **`basis`** — how much of the population is covered: `COMPUTED`, `COMPUTED_PARTIAL`, `NO_DATA`,
  `NOT_MEASURABLE`.
- **`provenance`** — whether the covered rows were `MEASURED`, `ASSERTED`, or `MIXED`.

They are separate because a figure can be **complete and still unmeasured**: the CAB quorum-met
rate is 100% over every held meeting *and* every one of those quorum conclusions was typed by a
human. Collapsing the two axes ships "Quorum met: 100%" as a passed control.

Seven metrics report `NOT_MEASURABLE` with a named blocker rather than a number, because nothing in
either database populates their source — implementation timestamps, PIR timestamps, a submission
timestamp column that does not exist, CAB attendance rows, decided referrals, and the
unauthorised-change register. **A `—` is a gap; a `0%` is a measurement.** They stay on screen with
their reason: dropping them would leave a dashboard with no visible gaps, which reads as fully
instrumented.

---

## §10 When something is refused — what the 409 means

All of these are the platform working. Each message names what is missing.

| Refusal | Cause | Fix |
|---|---|---|
| *"Change authority is not configured: this change's authorisation matrix (TYPE / RISK) requires CAB authorisation, but the committee mapped to the CAB gate has no ACTIVE voting member, so it can never be quorate and any decision recorded in its name would be invalid."* | Mapped to an unconstituted body (§0) — the live wording, verified 2026-08-04 | Appoint voting members with current terms |
| The same message with *"no committee is mapped to the CAB gate for this organisation"* in place of the roster clause | The gate has no committee at all (§2) | Map it — and map `ECAB` too if the change is emergency |
| *"requires at least N recorded APPROVE vote(s)"* | Fewer votes than the matrix cell demands | Hold the sitting, or check the matrix cell via `/{id}/authorisation` |
| *"a REJECT vote is on record"* | Someone voted against | Route to `CAB_REJECTED`, do not re-approve |
| *"Segregation of duty: the change requester/owner cannot cast a CAB vote"* | Self-review | A different voting member votes |
| *"outstanding condition(s)"* on `→ IMPLEMENTING` | Open conditions (§5.1) | Satisfy with approved evidence, or waive with a reason |
| *"evidence … is not APPROVED"* | Evidence is `DRAFT`/`PENDING_REVIEW`/`EXPIRED` | Get it reviewed, or waive |
| *"a post-implementation review (PIR) summary is required"* on `→ CLOSED` | `NORMAL`/`EMERGENCY` change with no PIR | Record the PIR (`STANDARD` is exempt) |
| *"did not meet its objectives, so a follow-up action is required"* | PIR records failure with no follow-up | State the follow-up — it becomes a tracked CAPA |
| *"falls within an active freeze"* on `→ SCHEDULED` | In-scope blackout window (§7) | Reschedule, or set `allow_emergency` on the window if the policy allows |
| *"self-ratification"* / *"raiser"* on ratify | The approver or raiser is ratifying | Someone independent ratifies |

A `403` is different: that is entitlement (`@RequireModule("CHANGE")`) or permission
(`hasPermission('CHANGE', …)`). Check the SKU flag and the role grants, not the CAB.

---

## §11 Onboarding checklist

- [ ] Committee created, chartered bilingually, quorum set
- [ ] Voting members appointed, terms current, linked to platform users
- [ ] Chair and secretary named
- [ ] `CAB` gate mapped · `ECAB` gate mapped
- [ ] `GET /api/v1/changes/governance-health` reports both gates configured **and** constituted
- [ ] Authorisation matrix reviewed for this tenant's risk appetite
- [ ] Ratification SLA set within its bounds
- [ ] Freeze windows entered for the year's blackout periods
- [ ] One end-to-end rehearsal: raise → assess → refer → sit → decide → schedule → implement →
      review → close, and confirm the minutes and the register both show it
